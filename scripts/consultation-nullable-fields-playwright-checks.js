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
 * Browser regression check for consultations whose nullable columns are
 * actually NULL, driven the way a user works: login, the schedule banner's
 * Consultations link, the consultation list, open the request, Print Preview.
 *
 * consultationRequests.providerNo and .urgency are nullable in the schema
 * but always populated in the demo snapshot, so this check creates the
 * condition itself: it NULLs both columns on one seeded request (restoring
 * the original values afterwards), then asserts that
 *   1. the consultation form still renders (EctViewRequest2Action used to
 *      NPE in provDao.getProvider(null) and 500 the page), and
 *   2. Print Preview still returns a real PDF (ConsultationPDFCreator used
 *      to NPE on urgency.equals(...) and on the null provider's OHIP), by
 *      decoding the JSON consultPDF payload and checking the %PDF magic.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (to stage and restore the NULLs)
 * Optional: CONSULT_NULLABLE_REQUEST_ID (default 2), CHROME_PATH,
 *   CONSULT_NULLABLE_SCREENSHOT_DIR (default /tmp).
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  assert,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  login,
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
  screenshotDir: process.env.CONSULT_NULLABLE_SCREENSHOT_DIR || '/tmp',
};
const requestId = process.env.CONSULT_NULLABLE_REQUEST_ID || '2';
assert(/^\d+$/.test(requestId), `CONSULT_NULLABLE_REQUEST_ID must be numeric, got ${requestId}`);

const mysqlHost = process.env.MYSQL_HOST || '127.0.0.1';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

let mysqlDefaults = null;
function initMysqlDefaults() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-nullable-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  mysqlDefaults = { dir, file };
}
function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}
function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
}
function sqlValue(value) {
  return value === '' || value === 'NULL' ? 'NULL' : `'${value.replace(/'/g, "''")}'`;
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  initMysqlDefaults();
  let original = null;
  try {
    // Stage the nullable state, remembering what to restore.
    const row = sql(`SELECT IFNULL(providerNo,'NULL'), IFNULL(urgency,'NULL') FROM consultationRequests WHERE requestId=${requestId}`);
    assert(row, `consultationRequests row ${requestId} not found`);
    const [origProvider, origUrgency] = row.split('\t');
    original = { providerNo: origProvider, urgency: origUrgency };
    sql(`UPDATE consultationRequests SET providerNo=NULL, urgency=NULL WHERE requestId=${requestId}`);

    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const schedulePage = await login(context, config, recorder);

    // User path: the schedule banner's Consultations link opens the list.
    const listPopup = context.waitForEvent('page', { timeout: 15000 }).catch(() => null);
    await schedulePage.locator("a[onclick*='/carlos/encounter/IncomingConsultation']").first().click();
    let listPage = await listPopup;
    if (!listPage) {
      listPage = context.pages()[context.pages().length - 1];
    }
    wirePage(listPage, 'consultation-list', recorder);
    await listPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(listPage, 'consultation list');
    await screenshot(listPage, config.screenshotDir, 'consultation-nullable-list');

    // Open the staged request from the list the way a user would; the list
    // links carry requestId=<id>. Fall back to direct navigation only if the
    // list pagination/filters hide this request.
    const rowLink = listPage.locator(`a[href*='requestId=${requestId}'], a[onclick*='requestId=${requestId}']`).first();
    let consultPage = listPage;
    if (await rowLink.count()) {
      const consultPopup = context.waitForEvent('page', { timeout: 15000 }).catch(() => null);
      await rowLink.click();
      const popped = await consultPopup;
      if (popped) {
        consultPage = popped;
        wirePage(consultPage, 'consultation-form', recorder);
      }
    } else {
      await listPage.goto(`${config.baseUrl.href.replace(/\/$/, '')}/encounter/ViewRequest?requestId=${requestId}`, { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep // validateBaseUrl restricts hosts to loopback by default; requestId is digits-only.
    }
    await consultPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    // Regression 1: the form must render despite providerNo/urgency NULL.
    await assertNotErrorPage(consultPage, `consultation ${requestId} with NULL providerNo/urgency`);
    await consultPage.locator('form[name="EctConsultationFormRequest2Form"]').waitFor({ state: 'attached', timeout: 15000 });
    const fatal500s = recorder.badResponses.filter((r) => r.status >= 500);
    assert(fatal500s.length === 0, `consultation form load produced 5xx responses: ${JSON.stringify(fatal500s)}`);
    await screenshot(consultPage, config.screenshotDir, 'consultation-nullable-form');

    // Regression 2: Print Preview must return a real PDF. The button posts
    // submission="And Print Preview" to /encounter/RequestConsultation and
    // receives JSON with a base64 consultPDF.
    const pdfResponsePromise = consultPage.waitForResponse(
      (response) => /\/encounter\/RequestConsultation/.test(response.url()) && response.request().method() === 'POST',
      { timeout: 30000 },
    );
    // Exactly the plain "And Print Preview" button: checkForm('And Print
    // Preview',…) takes the AJAX/JSON path, while the sibling buttons
    // ("Update … And Print Preview", "Submit … And Print Preview") do a full
    // form submit that answers with HTML, not the consultPDF JSON.
    await consultPage.locator('input[type="button"][onclick*="checkForm(\'And Print Preview\'"]').first().click();
    const pdfResponse = await pdfResponsePromise;
    assert(pdfResponse.ok(), `Print Preview POST failed with HTTP ${pdfResponse.status()}`);
    const payload = await pdfResponse.json().catch(() => null);
    assert(payload, 'Print Preview did not return JSON');
    assert(!payload.errorMessage, `Print Preview returned an error: ${payload.errorMessage}`);
    assert(payload.consultPDF, 'Print Preview JSON carried no consultPDF payload');
    const pdfBytes = Buffer.from(payload.consultPDF, 'base64');
    assert(pdfBytes.subarray(0, 5).toString('utf8') === '%PDF-',
      `consultPDF payload is not a PDF (starts with ${pdfBytes.subarray(0, 8).toString('hex')})`);
    await screenshot(consultPage, config.screenshotDir, 'consultation-nullable-preview');

    await context.close();
    console.log(`PASS consultation ${requestId} renders and print-previews a ${pdfBytes.length}-byte PDF with NULL providerNo/urgency`);
  } catch (error) {
    console.error('FAIL consultation nullable-fields Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    try {
      if (original) {
        sql(`UPDATE consultationRequests SET providerNo=${sqlValue(original.providerNo)}, urgency=${sqlValue(original.urgency)} WHERE requestId=${requestId}`);
      }
    } catch (restoreError) {
      console.error(`WARN failed to restore consultationRequests ${requestId}: ${restoreError.message}`);
    }
    cleanupMysqlDefaults();
    await browser.close();
  }
})();
