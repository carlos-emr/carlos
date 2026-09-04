#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Local-only browser regression check for the Rich Text Letter's Print and PDF workflows, driven the
 * way a clinician drives them, against a running CARLOS (devcontainer Tomcat or a packaged install
 * through nginx). It covers the defects that shipped green through every other check:
 *
 *   1. the form's own "PDF" / "Submit & PDF" buttons (printControl.js) produce a real PDF download
 *      (they used to be a plain Save: the print flag was never posted, and the server had no mapped
 *      result for it anyway); "PDF" leaves the window open, "Submit & PDF" auto-closes it after
 *      the download like a plain Submit;
 *   2. the toolbar's Download produces a real PDF for the letter just typed;
 *   3. the toolbar's Print ("Save and then print") prints the EDITOR IFRAME and then saves a new
 *      letter (typing never set the dirty flag before, so nothing was saved);
 *   4. the form's "Submit & Print" prints the iframe and submits through the CSP timer shim;
 *   5. the Preventions sidebar button loads through eform/rtlPreventions.do (unmapped before);
 *   6. optionally, a clinic .rtl template (RTL_TEMPLATE_NAME) loads into the editor unsandboxed and
 *      stays editable (any template other than blank.rtl used to be served with a sandbox CSP).
 *
 * Every page is checked for uncaught JS errors and severe console errors; the only tolerated one is
 * the documented stamps.js 404 on stock installs.
 *
 * Environment: BASE_URL (default http://127.0.0.1:8080/carlos), TEST_USER/TEST_PASSWORD/TEST_PIN,
 * RTL_DEMOGRAPHIC_NO (default 1), RTL_FORM_NAME (default "Rich Text Letter"), RTL_TEMPLATE_NAME
 * (optional, e.g. MissedAppointment.rtl), RTL_SCREENSHOT_DIR (default /tmp), CHROME_PATH (optional).
 */

const fs = require('fs');
const { chromium } = require('playwright');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  buildArtifactPath,
  buildFailureDetails,
  createRecorder,
  findLibraryEform,
  getLaunchOptions,
  gotoApp,
  login,
  openManager,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  demographicNo: process.env.RTL_DEMOGRAPHIC_NO || '1',
  screenshotDir: process.env.RTL_SCREENSHOT_DIR || '/tmp',
  formName: process.env.RTL_FORM_NAME || 'Rich Text Letter',
  templateName: process.env.RTL_TEMPLATE_NAME || '',
};

const LETTER_TEXT = 'Dear Dr. Smith, the patient reports "chest pain" & <cough>. Playwright RTL check.';

function escapeLikeSaveRTL(s) {
  return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/'/g, '&#39;');
}

// stamps.js is never auto-deployed (clinic signature stamps), so a stock install logs a 404 + MIME
// refusal for it on every letter. Documented in docs/ui-tests/deb-install-validation.md.
function isKnownConsoleIssue(issue) {
  // Chromium's resource-load errors carry the URL in the message location, not in the text.
  const where = (issue.location && issue.location.url) || '';
  const text = issue.text || '';
  // Only the two messages a MISSING stamps.js produces are exempt: the 404 resource-load failure
  // (located at the asset URL) and the MIME refusal for the HTML 404 page (located at the letter
  // page, naming the asset). A JavaScript error thrown by an installed stamps.js is not.
  const missingStamps = (/imagefile=stamps\.js/.test(where) && /Failed to load resource/.test(text))
    || (/Refused to execute script from .*imagefile=stamps\.js/.test(text) && /MIME type/.test(text));
  // The site root's favicon is nginx's concern, not the letter's.
  const missingFavicon = /\/favicon\.ico$/.test(where) && /Failed to load resource/.test(text);
  return missingStamps || missingFavicon;
}

async function waitForEditor(page) {
  await page.locator('#remotePrintButton').waitFor({ state: 'attached', timeout: 30000 });
  await page.locator('iframe#edit').waitFor({ state: 'attached', timeout: 30000 });
  await page.waitForFunction(() => {
    const sel = document.getElementById('template');
    return sel && !Array.from(sel.options).some((o) => o.textContent.trim() === 'loading...');
  }, null, { timeout: 30000 });
  await page.waitForFunction(() => {
    const f = document.getElementById('edit');
    try { return f && f.contentWindow.document.readyState === 'complete' && f.contentWindow.document.body != null; } catch (e) { return false; }
  }, null, { timeout: 30000 });
  await page.waitForTimeout(500);
}

function editorFrame(page) {
  const frame = page.frames().find((fr) => fr.parentFrame() === page.mainFrame());
  assert(frame, 'editor iframe not found');
  return frame;
}

async function typeLetter(page, text = LETTER_TEXT) {
  const frame = editorFrame(page);
  await frame.locator('body').click();
  await page.keyboard.press('End');
  await page.keyboard.type(text);
  const html = await frame.evaluate(() => document.body.innerHTML);
  assert(html.includes('Playwright RTL check'), `typed text did not land in the editor: ${html.slice(0, 200)}`);
  return html;
}

async function openNewLetter(context, recorder, fid, label) {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(config.demographicNo)}`);
  await assertNotErrorPage(page, label);
  await waitForEditor(page);
  return page;
}

/** Clicks a control that ends in a browser download and returns the saved PDF's bytes. */
async function clickAndDownloadPdf(page, locator, label) {
  const downloadPromise = page.waitForEvent('download', { timeout: 120000 });
  await locator.click();
  const download = await downloadPromise;
  // buildArtifactPath keeps the file under the validated artifact directory and creates it.
  const target = buildArtifactPath(config.screenshotDir, `rtl-${label}-${Date.now()}`, '.pdf');
  await download.saveAs(target);
  const bytes = fs.readFileSync(target);
  assert(download.suggestedFilename().toLowerCase().endsWith('.pdf'), `${label}: download is not a .pdf (${download.suggestedFilename()})`);
  assert(bytes.length > 1024, `${label}: PDF is implausibly small (${bytes.length} bytes)`);
  assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', `${label}: downloaded bytes are not a PDF`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, label);
  return { bytes, file: target, name: download.suggestedFilename() };
}

/** Clicks a control whose handler ends in a save, and waits for that save's response and result page. */
async function clickAndAwaitSave(page, locator) {
  const saveResponse = page.waitForResponse(
    (r) => r.url().includes('/eform/addEForm') && r.request().method() === 'POST', { timeout: 60000 });
  await locator.click();
  const response = await saveResponse;
  assert(response.status() < 400, `save POST to addEForm answered HTTP ${response.status()} (a WAF 403 means the letter was lost)`);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return response;
}

async function savedFdid(page) {
  await page.locator('#fdid').waitFor({ state: 'attached', timeout: 30000 });
  const fdid = await page.locator('#fdid').inputValue();
  assert(/^\d+$/.test(fdid), `expected a saved fdid on the page, got "${fdid}"`);
  return fdid;
}

(async () => {
  const recorder = createRecorder();
  const results = [];
  const step = (name, ok, detail) => { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ' — ' + detail : ''}`); };
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const printLog = [];
  try {
    // A self-signed front door is only acceptable on the loopback install the runbook describes;
    // a remote HTTPS target must present a certificate the test user actually trusts.
    const loopbackTarget = /^https?:\/\/(127\.0\.0\.1|localhost|\[::1\])(:\d+)?(\/|$)/i.test(config.baseUrl);
    const context = await browser.newContext({ acceptDownloads: true, ignoreHTTPSErrors: loopbackTarget, viewport: { width: 1440, height: 1100 } });
    // Record window.print() from EVERY frame on the Node side (the pages navigate right after printing),
    // and neutralize window.close() so the post-save auto-close does not tear the page down under us.
    await context.exposeBinding('__carlosRecordPrint', (source, info) => { printLog.push(info); });
    await context.addInitScript(() => {
      window.print = function () {
        window.__carlosRecordPrint({ href: location.href, isTop: window === window.top, body: (document.body || {}).innerText || '' });
      };
      window.close = () => { window.__playwrightCloseIntercepted = true; };
    });

    const landing = await login(context, config, recorder);
    await landing.close();
    const manager = await openManager(context, config, recorder, 'rtl-manager');
    const { fid } = await findLibraryEform(manager, config.formName);
    await manager.close();

    // ---------- 1. New letter: Preventions button (eform/rtlPreventions.do alias) ----------
    let page = await openNewLetter(context, recorder, fid, 'rtl-preventions');
    const preventionsResponse = page.waitForResponse((r) => /\/eform\/rtlPreventions/.test(r.url()), { timeout: 30000 });
    await page.locator('input[name="Preventions"]').click();
    const pr = await preventionsResponse;
    const preventionsBody = await editorFrame(page).evaluate(() => document.body.innerText);
    step('Preventions button loads through the rtlPreventions route with HTTP 200', pr.status() === 200, `${pr.status()} ${pr.url()}`);
    // Detail carries shape only, never the editor text: on a real patient that is clinical data,
    // and this log ends up in consoles and CI output.
    step('Preventions content lands in the editor (table or "No preventions on file.")',
      /No preventions on file|Prevention|Date/i.test(preventionsBody) && !/Error loading preventions/i.test(preventionsBody),
      `${preventionsBody.length} chars, ${/No preventions on file/i.test(preventionsBody) ? 'empty-list message' : 'prevention rows'}`);
    await page.close();

    // ---------- 2. New letter: typing marks it dirty; toolbar Download yields a real PDF ----------
    page = await openNewLetter(context, recorder, fid, 'rtl-download');
    step('new letter opens clean (needToConfirm=false)', (await page.evaluate(() => window.needToConfirm)) === false, '');
    const typed = await typeLetter(page);
    step('typing sets the dirty flag (needToConfirm=true)', (await page.evaluate(() => window.needToConfirm)) === true, '');
    await page.locator('#remote_eform_subject').fill(`RTL print/pdf check ${Date.now()}`);
    const dl1 = await clickAndDownloadPdf(page, page.locator('#remoteDownloadButton'), 'toolbar-download');
    const fdidAfterDownload = await savedFdid(page);
    step('toolbar Download saves the letter and downloads a real PDF', dl1.bytes.length > 1024, `${dl1.name}, ${dl1.bytes.length} bytes, fdid ${fdidAfterDownload}`);
    await screenshot(page, config.screenshotDir, 'rtl-print-pdf-after-download');

    // ---------- 3. Saved letter page: the form's own PDF button ----------
    const storedLetter = await page.locator('#Letter').inputValue();
    step('saved letter stores the typed text with saveRTL() escaping', storedLetter === escapeLikeSaveRTL(typed) || storedLetter.includes(escapeLikeSaveRTL('"chest pain" & <cough>')), storedLetter.slice(0, 120));
    const reopened = await editorFrame(page).evaluate(() => document.body.innerHTML);
    step('saved letter reopens in the editor as markup', reopened.includes('Playwright RTL check') && reopened.includes('&amp; &lt;cough&gt;'), reopened.slice(0, 120));
    const dl2 = await clickAndDownloadPdf(page, page.locator('input[name="pdfButton"]'), 'form-pdf-button');
    const fdidAfterPdfButton = await savedFdid(page);
    step('form "PDF" button downloads a real PDF', dl2.bytes.length > 1024, `${dl2.name}, ${dl2.bytes.length} bytes, fdid ${fdidAfterPdfButton}`);
    // "PDF" is a preview: the saved alert (5 s countdown) never shows and the window stays open.
    // Wait past that countdown before asserting, or the check would pass vacuously.
    await page.waitForTimeout(6500);
    step('form "PDF" button leaves the window open (no auto-close)', !(await page.evaluate(() => window.__playwrightCloseIntercepted === true)), '');

    // ---------- 3b. Saved letter page: the form's own "Submit & PDF" downloads, then auto-closes ----------
    const dl3 = await clickAndDownloadPdf(page, page.locator('input[name="pdfSaveButton"]'), 'form-submit-pdf-button');
    const fdidAfterSubmitPdf = await savedFdid(page);
    step('form "Submit & PDF" button downloads a real PDF', dl3.bytes.length > 1024, `${dl3.name}, ${dl3.bytes.length} bytes, fdid ${fdidAfterSubmitPdf}`);
    // A submission: the result page sets isSuccess_Autoclose, so the toolbar closes the window
    // once the saved alert's countdown ends (window.close is intercepted by the init script above).
    const autoClosed = await page.waitForFunction(() => window.__playwrightCloseIntercepted === true, null, { timeout: 15000 }).then(() => true).catch(() => false);
    step('form "Submit & PDF" then auto-closes the window after the download', autoClosed, '');
    await page.close();

    // ---------- 4. New letter: toolbar Print prints the iframe, then saves ----------
    printLog.length = 0;
    page = await openNewLetter(context, recorder, fid, 'rtl-toolbar-print');
    await typeLetter(page);
    await page.locator('#remote_eform_subject').fill(`RTL toolbar print ${Date.now()}`);
    await clickAndAwaitSave(page, page.locator('#remotePrintButton'));
    step('toolbar Print invokes print() exactly once, on the EDITOR IFRAME', printLog.length === 1 && !printLog[0].isTop, JSON.stringify(printLog.map((c) => ({ href: c.href, isTop: c.isTop }))));
    step('toolbar Print printed document contains the typed letter', printLog.length === 1 && printLog[0].body.includes('Playwright RTL check'), '');
    await assertNotErrorPage(page, 'rtl-toolbar-print');
    const fdidAfterPrint = await savedFdid(page);
    step('toolbar Print then saves the letter (new fdid on the result page)', /^\d+$/.test(fdidAfterPrint) && fdidAfterPrint !== fdidAfterSubmitPdf, `fdid ${fdidAfterPrint}`);
    await page.close();

    // ---------- 5. New letter: the form's own "Submit & Print" ----------
    printLog.length = 0;
    page = await openNewLetter(context, recorder, fid, 'rtl-form-print');
    await typeLetter(page);
    await page.locator('#remote_eform_subject').fill(`RTL submit and print ${Date.now()}`);
    const printSave = page.locator('input[name="PrintSaveButton"]');
    // The form's own button submits through a 1s string timer (the CSP shim), so wait for the save
    // itself rather than for whatever load state the still-current page happens to report.
    await clickAndAwaitSave(page, printSave);
    step('"Submit & Print" prints the editor iframe', printLog.length === 1 && !printLog[0].isTop, '');
    await assertNotErrorPage(page, 'rtl-form-print');
    const fdidAfterFormPrint = await savedFdid(page);
    step('"Submit & Print" submits through the string-timer shim and saves', /^\d+$/.test(fdidAfterFormPrint) && fdidAfterFormPrint !== fdidAfterPrint, `fdid ${fdidAfterFormPrint}`);
    await page.close();

    // ---------- 6. Optional: a clinic .rtl template loads unsandboxed and stays editable ----------
    if (config.templateName) {
      page = await openNewLetter(context, recorder, fid, 'rtl-template');
      const option = page.locator(`#template option[value="${config.templateName}"]`);
      step(`template dropdown offers ${config.templateName}`, (await option.count()) === 1, '');
      const templateResponse = page.waitForResponse((r) => r.url().includes(`imagefile=${encodeURIComponent(config.templateName)}`) || r.url().includes(`imagefile=${config.templateName}`), { timeout: 30000 });
      await page.locator('#template').selectOption(config.templateName);
      const tr = await templateResponse;
      const csp = tr.headers()['content-security-policy'] || '';
      step('clinic template is served without the sandbox CSP', tr.status() === 200 && !/sandbox/.test(csp), `${tr.status()} csp="${csp}"`);
      await page.waitForTimeout(1500);
      const editable = await page.evaluate(() => {
        try {
          const d = document.getElementById('edit').contentWindow.document;
          return { ok: true, designMode: d.designMode, length: d.body.innerHTML.length };
        } catch (e) { return { ok: false, error: String(e) }; }
      });
      step('editor can read the clinic template frame (same-origin) with designMode on', editable.ok && editable.designMode === 'on' && editable.length > 0, JSON.stringify(editable));
      await page.close();
    } else {
      console.log('[skip] RTL_TEMPLATE_NAME not set: clinic template check skipped');
    }

    // ---------- 7. No JS failures anywhere ----------
    assertNoPageErrors(recorder);
    const severe = recorder.consoleIssues.filter((i) => !isKnownConsoleIssue(i));
    step('no severe console errors on any RTL page', severe.length === 0,
      severe.map((i) => `[${i.label}] ${i.text.slice(0, 120)} @ ${(i.location && i.location.url) || ''}`).join(' | '));
    step('no unexpected dialogs', recorder.dialogs.length === 0, JSON.stringify(recorder.dialogs));
  } catch (error) {
    console.error('RTL print/PDF check failed:', error && error.stack || error);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    results.push({ name: 'harness', ok: false });
  } finally {
    await browser.close();
  }
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  process.exit(failed.length ? 1 : 0);
})();
