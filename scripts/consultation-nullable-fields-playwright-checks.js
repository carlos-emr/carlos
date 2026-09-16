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
 * Also stages an owned specialist whose ID differs from the request, with no
 * demographic contact. Checks the displayed and printed contact details, the
 * REST detail/404 contract, and refreshes after removing the optional specialist.
 * Restores every staged column and removes only its owned specialist, including
 * on failure/cancellation. Requires pdftotext to verify PDF content, not just magic.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (to stage and restore the NULLs)
 * MYSQL_HOST must be localhost, 127.0.0.1 or ::1; this mutating fixture has no remote override.
 * Optional: CONSULT_NULLABLE_REQUEST_ID (default 2), CHROME_PATH,
 *   CONSULT_NULLABLE_SCREENSHOT_DIR (default /tmp).
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { createGracefulSignalCancellation, settleOperations } = require('./graceful-signal-cancellation');
const {
  appUrl,
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
assert(['localhost', '127.0.0.1', '::1'].includes(mysqlHost),
  'Nullable consultation fixture requires a loopback MYSQL_HOST');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
assert(!/[\r\n]/.test(mysqlPassword), 'MYSQL_PASSWORD must not contain line breaks');
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
  const cancellation = createGracefulSignalCancellation();
  const recorder = createRecorder();
  let browser;
  let original = null;
  let specialistId = null;
  const specialistMarker = `PWDETAIL${Date.now()}`;
  const specialistPhone = '416-555-0145';
  const specialistFax = '416-555-0245';
  try {
    browser = await chromium.launch({ ...getLaunchOptions(config.chromePath), handleSIGINT: false, handleSIGTERM: false });
    cancellation.throwIfCancelled();
    initMysqlDefaults();
    await cancellation.run(async () => {
      // Stage the nullable state, remembering what to restore.
      const row = sql(`SELECT IFNULL(providerNo,'NULL'), IFNULL(urgency,'NULL'), IFNULL(specId,'NULL'), IFNULL(demographicContactId,'NULL') FROM consultationRequests WHERE requestId=${requestId}`);
      assert(row, `consultationRequests row ${requestId} not found`);
      const [origProvider, origUrgency, origSpecialist, origContact] = row.split('\t');
      original = { providerNo: origProvider, urgency: origUrgency, specId: origSpecialist, contactId: origContact };
      specialistId = sql(`INSERT INTO professionalSpecialists
        (fName,lName,address,phone,fax,email,lastUpdated,institutionId,departmentId,hideFromView,deleted)
        VALUES ('Synthetic','${specialistMarker}','2545 Synthetic Street','${specialistPhone}',
          '${specialistFax}','consult@example.invalid',NOW(),0,0,0,0); SELECT LAST_INSERT_ID()`);
      assert(/^[1-9]\d*$/.test(specialistId), 'Specialist fixture did not return a positive identifier');
      assert(Number(specialistId) !== Number(requestId), 'Fixture needs distinct request and specialist identifiers');
      sql(`UPDATE consultationRequests SET providerNo=NULL, urgency=NULL, specId=${specialistId}, demographicContactId=NULL WHERE requestId=${requestId}`);

      const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
      const schedulePage = await login(context, config, recorder);

      // User path: the schedule banner's Consultations link opens the list.
      let [listPage] = await settleOperations([
        context.waitForEvent('page', { timeout: 15000 }).catch(() => null),
        schedulePage.locator("a[onclick*='/carlos/encounter/IncomingConsultation']").first().click(),
      ]);
      if (!listPage) {
        listPage = context.pages()[context.pages().length - 1];
      }
      wirePage(listPage, 'consultation-list', recorder);
      await listPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      await assertNotErrorPage(listPage, 'consultation list');
      await screenshot(listPage, config.screenshotDir, 'consultation-nullable-list');
      // The demo dataset ships hundreds of consults, so an empty first page is a
      // regression, not a filter: the packaged install once rendered zero rows
      // because the seeded _site_access_privacy grant was applied without
      // multisite mode. Paging to the staged request must not mask that.
      const listRows = await listPage.locator('table.consult-table tbody tr').count();
      assert(listRows > 0, 'consultation list rendered no rows for the demo dataset');

      // The request id lives in the row's onclick handler (not in an anchor).
      // Require the exact staged row to render so direct form navigation cannot
      // hide a consultation-list failure such as a NULL urgency dereference.
      const requestRows = listPage.locator("table.consult-table tbody tr[onclick*='requestId=']");
      let requestRow;
      // Results are newest first, and seeded request 2 is older than the first
      // 100 results. Follow the list's own pagination to exercise its real row.
      const maxPages = 100;
      for (let pageNumber = 1; pageNumber <= maxPages; pageNumber += 1) {
        cancellation.throwIfCancelled();
        const matchingRowIndexes = await requestRows.evaluateAll((rows, targetRequestId) => rows.flatMap((row, index) => {
          const onclick = row.getAttribute('onclick') || '';
          const targetMatch = /['"]([^'"]*\/encounter\/ViewRequest\?[^'"]*)['"]/.exec(onclick);
          if (!targetMatch) return [];
          try {
            const targetUrl = new URL(targetMatch[1], window.location.href);
            return targetUrl.searchParams.get('requestId') === targetRequestId ? [index] : [];
          } catch {
            return [];
          }
        }), requestId);
        assert(matchingRowIndexes.length <= 1,
          `consultation list rendered ${matchingRowIndexes.length} exact rows for staged request ${requestId}`);
        if (matchingRowIndexes.length === 1) {
          requestRow = requestRows.nth(matchingRowIndexes[0]);
          break;
        }

        const nextButton = listPage.locator('button[onclick="gotoPage(true);"]');
        assert(await nextButton.count() === 1,
          `consultation list pagination ended without staged request ${requestId}`);
        const previousOffset = Number(await listPage.locator('input[name="offset"]').inputValue());
        assert(Number.isSafeInteger(previousOffset) && previousOffset >= 0,
          `consultation list has invalid offset ${previousOffset}`);
        const [nextResponse] = await settleOperations([
          listPage.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
          nextButton.click(),
        ]);
        assert(nextResponse && nextResponse.ok(),
          `consultation list next page failed with HTTP ${nextResponse?.status()}`);
        await assertNotErrorPage(listPage, 'consultation list next page');
        const nextUrl = new URL(listPage.url());
        assert(nextUrl.origin === config.baseUrl.origin
          && nextUrl.pathname === new URL(appUrl(config.baseUrl, '/encounter/ViewConsultation')).pathname,
        `consultation list pagination opened an unexpected destination (${nextUrl.pathname})`);
        const nextOffset = Number(await listPage.locator('input[name="offset"]').inputValue());
        assert(Number.isSafeInteger(nextOffset) && nextOffset > previousOffset,
          `consultation list pagination did not advance (${previousOffset} to ${nextOffset})`);
      }
      assert(requestRow, `consultation list did not contain staged request ${requestId} within ${maxPages} pages`);
      cancellation.throwIfCancelled();
      const [consultPage] = await settleOperations([
        context.waitForEvent('page', { timeout: 15000 }).catch(() => null),
        requestRow.click(),
      ]);
      assert(consultPage, `consultation row ${requestId} did not open its request form`);
      wirePage(consultPage, 'consultation-form', recorder);
      await consultPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      const consultUrl = new URL(consultPage.url());
      const expectedConsultPath = new URL(appUrl(config.baseUrl, '/encounter/ViewRequest')).pathname;
      assert(consultUrl.origin === config.baseUrl.origin
        && consultUrl.pathname === expectedConsultPath
        && consultUrl.searchParams.get('requestId') === requestId,
      `consultation row ${requestId} opened an unexpected destination (${consultUrl.pathname})`);

      // Regression 1: the form must render despite providerNo/urgency NULL.
      await assertNotErrorPage(consultPage, `consultation ${requestId} with NULL providerNo/urgency`);
      await consultPage.locator('form[name="EctConsultationFormRequest2Form"]').waitFor({ state: 'attached', timeout: 15000 });
      const fatal500s = recorder.badResponses.filter((r) => r.status >= 500);
      assert(fatal500s.length === 0, `consultation form load produced 5xx responses: ${JSON.stringify(fatal500s)}`);
      assert(await consultPage.locator('input[name="phone"]').inputValue() === specialistPhone,
        'Consultation form did not use its actual specialist phone');
      assert(await consultPage.locator('input[name="fax"]').inputValue() === specialistFax,
        'Consultation form did not use its actual specialist fax');
      assert((await consultPage.locator('textarea[name="address"]').inputValue()).includes('2545 Synthetic Street'),
        'Consultation form did not use its actual specialist address');
      await screenshot(consultPage, config.screenshotDir, 'consultation-nullable-form');

      // Regression 2: Print Preview must return a real PDF. The button posts
      // submission="And Print Preview" to /encounter/RequestConsultation and
      // receives JSON with a base64 consultPDF.
      cancellation.throwIfCancelled();
      const [pdfResponse] = await settleOperations([consultPage.waitForResponse(
        (response) => /\/encounter\/RequestConsultation/.test(response.url()) && response.request().method() === 'POST',
        { timeout: 30000 },
      ),
      // Exactly the plain "And Print Preview" button: checkForm('And Print
      // Preview',…) takes the AJAX/JSON path, while the sibling buttons
      // ("Update … And Print Preview", "Submit … And Print Preview") do a full
      // form submit that answers with HTML, not the consultPDF JSON.
      consultPage.locator('input[type="button"][onclick*="checkForm(\'And Print Preview\'"]').first().click()]);
      assert(pdfResponse.ok(), `Print Preview POST failed with HTTP ${pdfResponse.status()}`);
      const payload = await pdfResponse.json().catch(() => null);
      assert(payload, 'Print Preview did not return JSON');
      assert(!payload.errorMessage, `Print Preview returned an error: ${payload.errorMessage}`);
      assert(payload.consultPDF, 'Print Preview JSON carried no consultPDF payload');
      const pdfBytes = Buffer.from(payload.consultPDF, 'base64');
      assert(pdfBytes.subarray(0, 5).toString('utf8') === '%PDF-',
        `consultPDF payload is not a PDF (starts with ${pdfBytes.subarray(0, 8).toString('hex')})`);
      await screenshot(consultPage, config.screenshotDir, 'consultation-nullable-preview');
      const pdfText = execFileSync('pdftotext', ['-layout', '-', '-'],
        { input: pdfBytes, encoding: 'utf8', timeout: 15000 });
      for (const expected of [specialistMarker, specialistPhone, specialistFax, '2545 Synthetic Street']) {
        assert(pdfText.includes(expected), `Printed consultation omitted specialist detail: ${expected}`);
      }

      const detailUrl = appUrl(config.baseUrl, `/ws/rs/consults/getRequest?requestId=${requestId}`);
      const detail = await context.request.get(detailUrl);
      assert(detail.status() === 200, `REST detail failed with HTTP ${detail.status()}`);
      const stored = await detail.json();
      assert(stored.id === Number(requestId), 'REST detail returned the wrong request');
      assert(stored.professionalSpecialist?.id === Number(specialistId), 'REST detail returned the wrong specialist');
      assert(stored.professionalSpecialist?.phoneNumber === specialistPhone,
        'REST detail lost the detached specialist phone');

      assert(sql('SELECT COUNT(*) FROM consultationRequests WHERE requestId=2147483647') === '0',
        'Missing-request fixture identifier is already in use');
      const missing = await context.request.get(appUrl(config.baseUrl, '/ws/rs/consults/getRequest?requestId=2147483647'));
      assert(missing.status() === 404, `Missing REST request must return 404, got ${missing.status()}`);

      // A refresh is an operator-visible detached read; optional associations must
      // not leave stale contact details from the previous request state.
      sql(`UPDATE consultationRequests SET specId=NULL WHERE requestId=${requestId}`);
      const refreshed = await consultPage.reload({ waitUntil: 'networkidle', timeout: 30000 });
      assert(refreshed && refreshed.ok(), `Request without specialist failed with HTTP ${refreshed?.status()}`);
      await assertNotErrorPage(consultPage, 'request without specialist or demographic contact');
      for (const selector of ['input[name="phone"]', 'input[name="fax"]', 'textarea[name="address"]']) {
        assert(await consultPage.locator(selector).inputValue() === '', 'Missing specialist retained stale contact details');
      }
      const withoutSpecialist = await context.request.get(detailUrl);
      assert(withoutSpecialist.status() === 200, 'REST detail hid the request with absent optional associations');
      const optional = await withoutSpecialist.json();
      assert(optional.id === Number(requestId) && optional.professionalSpecialist == null,
        'REST detail retained the absent specialist');
      assert(recorder.badResponses.filter(response => response.status >= 500).length === 0,
        'Consultation workflow produced a 5xx response');


      await context.close();
      cancellation.throwIfCancelled();
      console.log(`PASS consultation ${requestId}: nullable fields, distinct specialist details in form/PDF/REST, missing-request 404, and absent associations (${pdfBytes.length}-byte PDF)`);
    });
  } catch (error) {
    console.error('FAIL consultation nullable-fields Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = cancellation.exitCode || 1;
  } finally {
    try {
      // Close and drain browser activity before restoring the staged clinical fields.
      if (browser) await browser.close();
    } finally {
      try {
        if (original) {
          sql(`UPDATE consultationRequests SET providerNo=${sqlValue(original.providerNo)}, urgency=${sqlValue(original.urgency)}, specId=${sqlValue(original.specId)}, demographicContactId=${sqlValue(original.contactId)} WHERE requestId=${requestId}`);
        }
      } catch (restoreError) {
        console.error(`WARN failed to restore consultationRequests ${requestId}: ${restoreError.message}`);
        process.exitCode = cancellation.exitCode || 1;
      }
      try {
        if (specialistId && /^[1-9]\d*$/.test(specialistId)) {
          sql(`DELETE FROM professionalSpecialists WHERE specId=${specialistId} AND lName='${specialistMarker}'
            AND NOT EXISTS (SELECT 1 FROM consultationRequests WHERE specId=${specialistId})`);
          assert(sql(`SELECT COUNT(*) FROM professionalSpecialists WHERE specId=${specialistId} AND lName='${specialistMarker}'`) === '0',
            'Owned specialist fixture could not be removed');
        }
      } catch (cleanupError) {
        console.error(`FAIL specialist fixture cleanup: ${cleanupError.message}`);
        process.exitCode = cancellation.exitCode || 1;
      }
      try { cleanupMysqlDefaults(); } finally { cancellation.dispose(); }
    }
  }
})().catch(() => {
  console.error('Consultation nullable-fields cleanup failed');
  process.exitCode = process.exitCode || 1;
});
