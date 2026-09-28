#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Reference browser check for the floating toolbar's Print -> chart-save decision (issue #3901).
 *
 * scripts/eform-print-save-decision.test.js pins printSaveDecision()/saveAfterPrint() against stubs.
 * This check drives the real toolbar in Chromium against a running CARLOS and the dev database:
 *
 * Each branch of saveAfterPrint(needToConfirm) is driven on a real eForm page for a demo (FAKE-)
 * patient:
 *
 *   1. no dirty detection (flag undefined): Print prints, then saves with no prompt;
 *   2. edited form (flag true): Print prints, then saves with no prompt;
 *   3. clean form (flag false): Print prints, then asks; OK posts the save;
 *   4. clean form (flag false): Cancel keeps the printout and posts nothing;
 *   5. the eForm manager preview (demographic -1): Print prints only, with no prompt and no save;
 *      the Save button is hidden once the toolbar lands and its handler refuses.
 *
 * NOTHING IS WRITTEN. Every POST navigation after login is answered by a stub page from
 * context.route(), so the "save" is observed but never reaches AddEForm2Action. window.print is
 * replaced by a counter in every frame, so no print dialog opens.
 *
 * The toolbar reads only the page's global `needToConfirm` (`typeof needToConfirm === 'undefined'`
 * means no dirty detection). The check records what the freshly opened form declares natively,
 * then sets the global to the value each case needs; a declared flag assigned `undefined` is the
 * same to the toolbar as no declaration, which scripts/eform-print-save-decision.test.js also pins.
 * The output says whether each case ran on the form's own declaration or on a forced value.
 *
 * Manual reference check (tier core); it is not run by CI:
 *   npm run test:eform-print-save-playwright
 *
 * Optional environment (common ones: see lib/playwright-harness.js readConfig()):
 *   EFORM_PRINT_SAVE_DEMOGRAPHIC_NO  demo patient to open the form for (default 1)
 *   EFORM_PRINT_SAVE_FID             eForm to use (default: the first one in the eForm manager)
 */
const h = require('./lib/playwright-harness');

const demographicNo = process.env.EFORM_PRINT_SAVE_DEMOGRAPHIC_NO || '1';
const requestedFid = process.env.EFORM_PRINT_SAVE_FID || '';
const SETTLE_MS = 3000;

async function firstLibraryFid(context, config, recorder) {
  const page = await context.newPage();
  h.wirePage(page, 'eform-manager', recorder);
  await h.gotoApp(page, config.baseUrl, '/eform/efmformmanager');
  // Other checks leave "Playwright ..." probe eForms in the library; those are minimal fixtures
  // without the floating toolbar, so pick a real library form.
  const link = page.locator('#eformTbl tr')
    .filter({ hasNot: page.getByText(/^Playwright /) })
    .locator('a[onclick*="efmshowform_data?fid="]').first();
  if (await link.count() === 0) {
    await page.close();
    throw new h.SkipCheck('the eForm manager lists no eForms; set EFORM_PRINT_SAVE_FID');
  }
  const match = (await link.getAttribute('onclick')).match(/fid=(\d+)/);
  await page.close();
  h.assert(match, 'could not read an fid from the eForm manager');
  return match[1];
}

/**
 * Opens an eForm page with print stubbed and every dialog answered by `answer`, and returns a
 * handle whose `events` list interleaves prints, dialogs and intercepted saves in arrival order.
 */
async function openEform(context, config, recorder, appPath, label, answer) {
  const page = await context.newPage();
  const events = [];
  await page.addInitScript(() => {
    window.print = () => { console.log('__carlos_print_stub__'); };
  });
  h.wirePage(page, label, recorder, async (dialog) => {
    events.push({ kind: dialog.type() === 'beforeunload' ? 'beforeunload' : 'dialog', type: dialog.type(), text: dialog.message() });
    if (dialog.type() === 'beforeunload' || answer === 'accept') {
      await dialog.accept().catch(() => {});
    } else {
      await dialog.dismiss().catch(() => {});
    }
  });
  page.on('console', (message) => {
    if (message.text() === '__carlos_print_stub__') events.push({ kind: 'print' });
  });
  await h.gotoApp(page, config.baseUrl, appPath);
  await h.assertNotErrorPage(page, label);
  // The toolbar is fetched by XHR after DOMContentLoaded (includeHTML in eform_floating_toolbar.js).
  await page.locator('#remotePrintButton').waitFor({ state: 'visible', timeout: 20000 });
  // A letter eForm (the Rich Text Letter) loads measurement history after the toolbar appears, and
  // the toolbar refuses to print or save until it settles ("Measurements are still loading"). Wait
  // for the same condition the toolbar checks, so Print is clicked on a ready form.
  await page.waitForFunction(() => typeof window.measurementHistoryStillLoading !== 'function'
    || !window.measurementHistoryStillLoading(), null, { timeout: 20000 });
  return { page, events, kinds: () => events.map((event) => event.kind) };
}

/**
 * Reads the dirty flag as the toolbar sees it on the freshly opened form:
 * 'undefined' (no dirty detection), 'dirty' (truthy) or 'clean' (declared and falsy).
 */
async function readFlag(page) {
  return page.evaluate(() => {
    // eslint-disable-next-line no-undef
    if (typeof needToConfirm === 'undefined') return 'undefined';
    // eslint-disable-next-line no-undef
    return needToConfirm ? 'dirty' : 'clean';
  });
}

/**
 * Puts the page's dirty flag into the state a case needs ('undefined', 'dirty' or 'clean') and
 * returns a label saying whether the form already declared it that way or it was forced.
 *
 * Assigning the bare identifier reaches a top-level `var` or `let` declaration alike, and creates
 * the global when the form declares none; `needToConfirm = undefined` on a declared flag is what
 * the toolbar treats as "no dirty detection", so that branch is reachable on any library form.
 */
async function setFlag(page, wanted) {
  const native = await readFlag(page);
  if (native === wanted) return `form-declared ${wanted}`;
  await page.evaluate((state) => {
    // eslint-disable-next-line no-undef
    needToConfirm = state === 'undefined' ? undefined : state === 'dirty';
  }, wanted);
  h.assert(await readFlag(page) === wanted, `could not set needToConfirm to ${wanted} on this eForm`);
  return `forced ${wanted} (form declared ${native})`;
}

/** Waits for the toolbar's form-submit save to be recorded by the route handler, or times out. */
async function awaitSave(page, saves, before) {
  const saved = page.waitForRequest((request) => request.method() === 'POST' && request.isNavigationRequest(),
    { timeout: 20000 });
  await page.locator('#remotePrintButton').click();
  await saved;
  // The route handler records the POST asynchronously; give it a moment before counting.
  for (let tries = 0; saves.length === before && tries < 20; tries += 1) await page.waitForTimeout(100);
  await page.waitForTimeout(500);
}

async function workflow({ config, context, recorder, saves, fid }) {
  const expectedPrompt = async (page) => page.locator('#eform_floating_toolbar')
    .getAttribute('data-print-save-unedited-confirm');
  const patientForm = `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(demographicNo)}`;

  // 1 and 2. No dirty detection, and an edited form: print, then the save POST, never a prompt.
  // These are the two "save" branches of saveAfterPrint(); the documented unconditional save for
  // forms without dirty detection is what keeps an edit from being dropped by a Cancel there.
  for (const wanted of ['undefined', 'dirty']) {
    const label = wanted === 'undefined' ? 'no-detection' : 'edited';
    const f = await openEform(context, config, recorder, patientForm, `print-save-${label}`, 'dismiss');
    console.log(`  ${label}: ${await setFlag(f.page, wanted)}`);
    const before = saves.length;
    await awaitSave(f.page, saves, before);
    h.assert(f.kinds().includes('print'), `${label} form did not print`);
    h.assert(!f.events.some((event) => event.kind === 'dialog'),
      `${label} form must save without prompting, saw ${JSON.stringify(f.events.filter((event) => event.kind === 'dialog'))}`);
    h.assert(saves.length === before + 1, `${label} form must post exactly one save, saw ${saves.length - before}`);
    await f.page.close();
  }

  // 3. Clean form, OK: print, then one confirm, then the save POST.
  {
    const f = await openEform(context, config, recorder, patientForm, 'print-save-accept', 'accept');
    const native = await readFlag(f.page);
    h.assert(native !== 'dirty', 'the freshly opened eForm already reports an edit; choose another EFORM_PRINT_SAVE_FID');
    console.log(`  clean: ${await setFlag(f.page, 'clean')}`);
    const prompt = await expectedPrompt(f.page);
    h.assert(prompt && prompt.trim(), 'toolbar fragment did not publish data-print-save-unedited-confirm');
    const before = saves.length;
    await awaitSave(f.page, saves, before);
    const dialogs = f.events.filter((event) => event.kind === 'dialog');
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', `expected exactly one confirm, saw ${JSON.stringify(dialogs)}`);
    h.assert(dialogs[0].text === prompt, 'the confirm did not show the server-localized prompt');
    const order = f.kinds().filter((kind) => kind === 'print' || kind === 'dialog');
    h.assert(order[0] === 'print' && order.includes('dialog'), `the prompt must follow the print, saw ${order.join(' -> ')}`);
    h.assert(saves.length === before + 1, `OK must post exactly one save, saw ${saves.length - before}`);
    await f.page.close();
  }

  // 4. Clean form, Cancel: print, one confirm, no POST.
  {
    const f = await openEform(context, config, recorder, patientForm, 'print-save-cancel', 'dismiss');
    await setFlag(f.page, 'clean');
    const before = saves.length;
    await f.page.locator('#remotePrintButton').click();
    await f.page.waitForTimeout(SETTLE_MS);
    h.assert(f.kinds().includes('print'), 'Cancel path did not print');
    h.assert(f.events.filter((event) => event.kind === 'dialog').length === 1, 'Cancel path must prompt exactly once');
    h.assert(saves.length === before, 'Cancel must not post a save');
    h.assert(await f.page.locator('#remotePrintButton').isVisible(), 'Cancel navigated away from the eForm');
    await f.page.close();
  }

  // 5. Manager preview (no patient): print only, no prompt, no POST, whatever the dirty flag says.
  for (const dirty of [false, true]) {
    const f = await openEform(context, config, recorder, `/eform/efmshowform_data?fid=${encodeURIComponent(fid)}`,
      `print-save-preview-${dirty ? 'dirty' : 'clean'}`, 'accept');
    h.assert(await f.page.locator('#demographicNo').inputValue() === '-1', 'preview did not render with demographic -1');
    await f.page.evaluate((value) => { window.needToConfirm = value; }, dirty);
    const before = saves.length;
    await f.page.locator('#remotePrintButton').click();
    await f.page.waitForTimeout(SETTLE_MS);
    h.assert(f.kinds().includes('print'), 'preview Print did not print');
    h.assert(!f.events.some((event) => event.kind === 'dialog'), 'preview Print must not prompt about the chart');
    h.assert(saves.length === before, 'preview Print must not post a save');
    // The toolbar is fetched after DOMContentLoaded, so the Save button must be hidden once it
    // lands, and its handler must refuse even when called directly (#3904).
    h.assert(!(await f.page.locator('#remoteSubmitButton').isVisible()), 'preview still shows the Save button');
    h.assert((await f.page.evaluate(() => remoteSaveOnly())) === false, 'preview remoteSaveOnly did not refuse');
    await f.page.waitForTimeout(SETTLE_MS);
    h.assert(saves.length === before, 'preview Save must not post a save');
    await f.page.close();
  }
}

async function main() {
  let browser;
  const recorder = h.createRecorder();
  await h.runCheck({
    name: 'eform-print-save',
    async run() {
      const config = h.readConfig();
      h.assert(/^[1-9]\d*$/.test(demographicNo), 'EFORM_PRINT_SAVE_DEMOGRAPHIC_NO must be a positive integer');
      h.assert(!requestedFid || /^\d+$/.test(requestedFid), 'EFORM_PRINT_SAVE_FID must be numeric');
      browser = await h.launchBrowser(config);
      const context = await h.newContext(browser, config);
      context.setDefaultTimeout(20000);
      await h.login(context, config, recorder);
      const fid = requestedFid || await firstLibraryFid(context, config, recorder);
      // Installed after login so the login POST is real. From here on no form POST reaches the app.
      const saves = [];
      // Kept apart from `saves`, which counts the toolbar's own form-submit save exactly.
      const blockedAsync = [];
      await context.route('**/*', async (route) => {
        const request = route.request();
        if (request.method() === 'POST') {
          const target = h.pathOnly(request.url());
          if (request.isNavigationRequest()) {
            saves.push(target);
            await route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><title>save intercepted</title>' });
            return;
          }
          // A library eForm can save over fetch/XHR (for example to /eform/addEForm), which is not a
          // navigation. Abort every such POST so the no-write guarantee holds for any form; only
          // CSRFGuard's token exchange, which writes nothing, is let through.
          if (!/\/csrfguard$/i.test(target)) {
            blockedAsync.push(target);
            await route.abort('blockedbyclient');
            return;
          }
        }
        await route.continue();
      });
      await workflow({ config, context, recorder, saves, fid });
      if (blockedAsync.length) {
        console.log(`  NOTE blocked ${blockedAsync.length} background POST(s) the eForm sent: ${[...new Set(blockedAsync)].join(', ')}`);
      }
      // Library eForms carry their own legacy script errors; only the toolbar's own are findings here.
      const toolbarErrors = recorder.pageErrors.filter((entry) => /eform_floating_toolbar/.test(entry.text));
      h.assert(toolbarErrors.length === 0,
        `the toolbar raised uncaught errors: ${toolbarErrors.map((entry) => entry.text.split('\n')[0]).join(' | ')}`);
    },
    async cleanup() {
      if (browser) await browser.close();
    },
  });
}

if (require.main === module) main();
module.exports = { readFlag, setFlag, workflow };
