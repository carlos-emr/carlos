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
 * Browser regression check for the Rich Text Letter's provider signature stamp, against a running
 * CARLOS (devcontainer Tomcat or a packaged install through nginx).
 *
 * The letter's Stamp and Closing Salutation buttons pick consult_sig_<provider_no>.png from three
 * hidden inputs on the form (user_id, user_ohip_no, doctor_provider_no). Those inputs are added to
 * the stored form_html by update-2026-09-20-rtl-provider-stamp-fields.sql on a fresh seed and by the
 * Flyway migration common/V1.0.41 on an upgraded install. An alpha tester's upgraded deb install
 * signed letters with no signature because the upgrade never ran the updates/ script; this check
 * drives the buttons the way a clinician does and proves:
 *
 *   1. the stored letter carries the three hidden inputs and the server populated user_id;
 *   2. Stamp inserts consult_sig_<signer>.png, and the image actually loads (not a broken image or
 *      the stamps.js / stamp.png fallback);
 *   3. Closing Salutation inserts the same signature, and neither button raises APCache's
 *      "could not be filled in" banner (the dead stamp_name key used to, on every Stamp click);
 *   4. no page errors, severe console errors (beyond the documented stamps.js 404) or alert dialogs.
 *
 * Nothing is saved: the letter is opened, stamped and discarded.
 *
 * Fixture: consult_sig_<signer>.png must exist in the eForm image directory (runbook fixture b in
 * docs/ui-tests/deb-install-validation.md). The signer is the logged-in provider when their
 * ohip_no is above the RTL billing threshold (1000), otherwise the patient's MRP.
 *
 * Environment: BASE_URL (default http://127.0.0.1:8080/carlos), TEST_USER/TEST_PASSWORD/TEST_PIN,
 * RTL_DEMOGRAPHIC_NO (default 1), RTL_FORM_NAME (default "Rich Text Letter"),
 * RTL_EXPECT_SIGNER (optional provider_no the stamp must name; derived from the inputs otherwise),
 * RTL_STAMP_ALLOW_AP_FALLBACK=true (skip check 1, to exercise editControl2.js's APCache fallback on
 * a form_html that predates the inputs), RTL_SCREENSHOT_DIR (default /tmp), CHROME_PATH (optional).
 */

const { chromium } = require('playwright');
const { shouldIgnoreHttpsErrors } = require('./lib/playwright-harness');
const {
  assertNoPageErrors,
  assertNotErrorPage,
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
  expectSigner: process.env.RTL_EXPECT_SIGNER || '',
  allowApFallback: process.env.RTL_STAMP_ALLOW_AP_FALLBACK === 'true',
};

// Mirrors MIN_BILLING_PROVIDER_OHIP_NO in editControl2.js (see docs/rich-text-letter-eform.md).
const RTL_BILLING_THRESHOLD = 1000;

// stamps.js is never auto-deployed (clinic signature stamps), so a stock install logs a 404 + MIME
// refusal for it on every letter; the same exemption as eform-rtl-print-pdf-playwright-checks.js.
function isKnownConsoleIssue(issue) {
  const where = (issue.location && issue.location.url) || '';
  const text = issue.text || '';
  const missingStamps = (/imagefile=stamps\.js/.test(where) && /Failed to load resource/.test(text))
    || (/Refused to execute script from .*imagefile=stamps\.js/.test(text) && /MIME type/.test(text));
  const missingFavicon = /\/favicon\.ico$/.test(where) && /Failed to load resource/.test(text);
  return missingStamps || missingFavicon;
}

async function waitForEditor(page) {
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
  if (!frame) { throw new Error('editor iframe not found'); }
  return frame;
}

/** Clicks an AP-backed toolbar button and returns the signature <img> it added to the editor. */
async function clickAndFindStamp(page, buttonName, label) {
  const frame = editorFrame(page);
  const before = await frame.evaluate(() => document.querySelectorAll('img').length);
  await frame.locator('body').click();
  const lookup = page.waitForResponse((r) => r.url().includes('efmformapconfig_lookup'), { timeout: 30000 });
  await page.locator(`input[name="${buttonName}"]`).click();
  const response = await lookup;
  if (response.status() >= 400) { throw new Error(`${label}: AP lookup answered HTTP ${response.status()}`); }
  await frame.waitForFunction((n) => document.querySelectorAll('img').length > n, before, { timeout: 15000 });
  // Let the image finish (or fail, and let bindStampFallbacks() swap it) before reading it.
  await frame.waitForFunction(() => Array.from(document.images).every((i) => i.complete), null, { timeout: 15000 });
  await page.waitForTimeout(300);
  await frame.waitForFunction(() => Array.from(document.images).every((i) => i.complete), null, { timeout: 15000 });
  return frame.evaluate((n) => {
    const img = document.querySelectorAll('img')[document.querySelectorAll('img').length - 1];
    return { count: document.querySelectorAll('img').length - n, src: img.getAttribute('src'), loaded: img.complete && img.naturalWidth > 0, width: img.naturalWidth };
  }, before);
}

(async () => {
  const recorder = createRecorder();
  const results = [];
  const step = (name, ok, detail) => { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ' — ' + detail : ''}`); };
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    // A self-signed front door is only acceptable on the loopback install the runbook describes.
    const loopbackTarget = /^https?:\/\/(127\.0\.0\.1|localhost|\[::1\])(:\d+)?(\/|$)/i.test(config.baseUrl);
    const context = await browser.newContext({ ignoreHTTPSErrors: shouldIgnoreHttpsErrors(), viewport: { width: 1440, height: 1100 } });
    const landing = await login(context, config, recorder);
    await landing.close();
    const manager = await openManager(context, config, recorder, 'rtl-stamp-manager');
    const { fid } = await findLibraryEform(manager, config.formName);
    await manager.close();

    const page = await context.newPage();
    wirePage(page, 'rtl-stamp', recorder);
    await gotoApp(page, config.baseUrl, `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(config.demographicNo)}`);
    await assertNotErrorPage(page, 'rtl-stamp');
    await waitForEditor(page);

    // ---------- 1. The stored letter carries the identity inputs ----------
    const inputs = await page.evaluate(() => {
      const read = (id) => { const el = document.getElementById(id); return el ? { type: el.type, value: el.value } : null; };
      return { user_id: read('user_id'), user_ohip_no: read('user_ohip_no'), doctor_provider_no: read('doctor_provider_no') };
    });
    if (config.allowApFallback) {
      console.log(`[info] RTL_STAMP_ALLOW_AP_FALLBACK=true: inputs on the page: ${JSON.stringify(inputs)}`);
    } else {
      const present = ['user_id', 'user_ohip_no', 'doctor_provider_no'].every((k) => inputs[k] && inputs[k].type === 'hidden');
      step('stored Rich Text Letter carries the user_id / user_ohip_no / doctor_provider_no hidden inputs', present,
        present ? '' : `missing on the page: ${JSON.stringify(inputs)} (form_html predates the stamp fields: did Flyway V1.0.41 run?)`);
      step('the server populated user_id for the logged-in provider', present && /^\d+$/.test(inputs.user_id.value.trim()),
        present ? `user_id="${inputs.user_id.value}"` : '');
    }

    // ---------- 2. Stamp inserts the provider's stored signature ----------
    const stamp = await clickAndFindStamp(page, 'stamp', 'Stamp');
    const m = /imagefile=(consult_sig_(\d+)\.png)$/.exec(stamp.src || '');
    let expected = config.expectSigner;
    if (!expected && inputs.user_id && inputs.user_ohip_no && inputs.doctor_provider_no) {
      const ohip = parseInt(inputs.user_ohip_no.value, 10);
      expected = (!Number.isNaN(ohip) && ohip > RTL_BILLING_THRESHOLD) ? inputs.user_id.value.trim() : inputs.doctor_provider_no.value.trim();
    }
    step('Stamp inserts a per-provider consult_sig_<provider_no>.png', !!m, `src="${stamp.src}"`);
    if (expected) {
      step(`Stamp signs as provider ${expected}`, !!m && m[2] === expected, m ? `got ${m[2]}` : '');
    }
    step('the stamped signature image loads (the consult_sig fixture exists and is served)', stamp.loaded,
      `naturalWidth=${stamp.width}`);
    await screenshot(page, config.screenshotDir, 'rtl-signature-stamp');

    // ---------- 3. Closing Salutation carries the same signature ----------
    const closing = await clickAndFindStamp(page, 'Closing', 'Closing Salutation');
    step('Closing Salutation inserts the same signature as Stamp', !!m && closing.src === stamp.src && closing.loaded,
      `src="${closing.src}" loaded=${closing.loaded}`);

    // A requested AP key apconfig.xml does not define raises APCache's "could not be filled in"
    // banner; the dead legacy stamp_name key put it on every Stamp click.
    const lookupNotice = await page.evaluate(() => {
      const el = document.getElementById('carlos-apcache-lookup-failure');
      return el ? el.textContent : '';
    });
    step('Stamp and Closing Salutation raise no "could not be filled in" AP lookup banner', lookupNotice === '', lookupNotice);

    // ---------- 4. No JS failures ----------
    assertNoPageErrors(recorder);
    const severe = recorder.consoleIssues.filter((i) => !isKnownConsoleIssue(i));
    step('no severe console errors on the letter', severe.length === 0,
      severe.map((i) => `[${i.label}] ${i.text.slice(0, 120)} @ ${(i.location && i.location.url) || ''}`).join(' | '));
    step('no unexpected dialogs', recorder.dialogs.length === 0, JSON.stringify(recorder.dialogs));
    // Discard the unsaved letter without the unload prompt.
    await page.evaluate(() => { window.needToConfirm = false; window.onbeforeunload = null; });
    await page.close();
  } catch (error) {
    console.error('RTL signature stamp check failed:', error && error.stack || error);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    results.push({ name: 'harness', ok: false });
  } finally {
    await browser.close();
  }
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  process.exit(failed.length ? 1 : 0);
})();
