#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Reference browser check for the floating toolbar's Print -> chart-save decision (issue #3901).
 *
 * scripts/eform-print-save-decision.test.js pins printSaveDecision()/saveAfterPrint() against stubs.
 * This check drives the real toolbar in Chromium against a running CARLOS and the dev database:
 *
 *   1. a clean eForm for a demo (FAKE-) patient: Print prints, then asks; OK posts the save;
 *   2. the same form: Cancel keeps the printout and posts nothing;
 *   3. the eForm manager preview (demographic -1): Print prints only, with no prompt and no save.
 *
 * NOTHING IS WRITTEN. Every POST navigation after login is answered by a stub page from
 * context.route(), so the "save" is observed but never reaches AddEForm2Action. window.print is
 * replaced by a counter in every frame, so no print dialog opens.
 *
 * "Clean" means the dirty flag reports no edit. A freshly opened eForm-generator form declares
 * `var needToConfirm = false`; when the chosen form declares no flag at all the check defines
 * `needToConfirm = false` on the page, which is exactly that declaration, and says so in its output.
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
  const link = page.locator('#eformTbl a[onclick*="efmshowform_data?fid="]').first();
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
  return { page, events, kinds: () => events.map((event) => event.kind) };
}

/** Makes the page's dirty flag report "unedited", returning how the flag was obtained. */
async function ensureCleanFlag(page) {
  return page.evaluate(() => {
    // eslint-disable-next-line no-undef
    if (typeof needToConfirm === 'undefined') {
      window.needToConfirm = false;
      return 'synthetic needToConfirm=false (form declares no dirty detection)';
    }
    // eslint-disable-next-line no-undef
    if (needToConfirm) return 'dirty';
    return 'form-declared needToConfirm=false';
  });
}

async function workflow({ config, context, recorder, saves, fid }) {
  const expectedPrompt = async (page) => page.locator('#eform_floating_toolbar')
    .getAttribute('data-print-save-unedited-confirm');
  const patientForm = `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(demographicNo)}`;

  // 1. Clean form, OK: print, then one confirm, then the save POST.
  {
    const f = await openEform(context, config, recorder, patientForm, 'print-save-accept', 'accept');
    const flag = await ensureCleanFlag(f.page);
    h.assert(flag !== 'dirty', 'the freshly opened eForm already reports an edit; choose another EFORM_PRINT_SAVE_FID');
    console.log(`  dirty flag: ${flag}`);
    const prompt = await expectedPrompt(f.page);
    h.assert(prompt && prompt.trim(), 'toolbar fragment did not publish data-print-save-unedited-confirm');
    const before = saves.length;
    const saved = f.page.waitForRequest((request) => request.method() === 'POST' && request.isNavigationRequest(),
      { timeout: 20000 });
    await f.page.locator('#remotePrintButton').click();
    await saved;
    // The route handler records the POST asynchronously; give it a moment before counting.
    for (let tries = 0; saves.length === before && tries < 20; tries += 1) await f.page.waitForTimeout(100);
    await f.page.waitForTimeout(500);
    const dialogs = f.events.filter((event) => event.kind === 'dialog');
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', `expected exactly one confirm, saw ${JSON.stringify(dialogs)}`);
    h.assert(dialogs[0].text === prompt, 'the confirm did not show the server-localized prompt');
    const order = f.kinds().filter((kind) => kind === 'print' || kind === 'dialog');
    h.assert(order[0] === 'print' && order.includes('dialog'), `the prompt must follow the print, saw ${order.join(' -> ')}`);
    h.assert(saves.length === before + 1, `OK must post exactly one save, saw ${saves.length - before}`);
    await f.page.close();
  }

  // 2. Clean form, Cancel: print, one confirm, no POST.
  {
    const f = await openEform(context, config, recorder, patientForm, 'print-save-cancel', 'dismiss');
    await ensureCleanFlag(f.page);
    const before = saves.length;
    await f.page.locator('#remotePrintButton').click();
    await f.page.waitForTimeout(SETTLE_MS);
    h.assert(f.kinds().includes('print'), 'Cancel path did not print');
    h.assert(f.events.filter((event) => event.kind === 'dialog').length === 1, 'Cancel path must prompt exactly once');
    h.assert(saves.length === before, 'Cancel must not post a save');
    h.assert(await f.page.locator('#remotePrintButton').isVisible(), 'Cancel navigated away from the eForm');
    await f.page.close();
  }

  // 3. Manager preview (no patient): print only, no prompt, no POST, whatever the dirty flag says.
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
      await context.route('**/*', async (route) => {
        const request = route.request();
        if (request.method() === 'POST' && request.isNavigationRequest()) {
          saves.push(h.pathOnly(request.url()));
          await route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><title>save intercepted</title>' });
          return;
        }
        await route.continue();
      });
      await workflow({ config, context, recorder, saves, fid });
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
module.exports = { ensureCleanFlag, workflow };
