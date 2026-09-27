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
 * Browser check for the consultation request's stored lab attachment rows (#3984).
 *
 * The shared attachment picker names a lab checkbox by its source and segment id
 * (labNoHL7123) so two lab sources that reuse a segment id stay distinct. The
 * consultation form's own row bookkeeping has to follow the same key on both
 * paths, otherwise a stored lab cannot be removed and a re-checked one is added
 * twice:
 *
 *   1. seeds a consultdocs row for the patient's first HL7 lab on an existing
 *      consultation request and opens ViewRequest for it;
 *   2. asserts the stored lab renders as #entry_labNoHL7<n> with a
 *      #delegate_labNoHL7<n> hidden input, and that no unqualified
 *      #entry_labNo<n> row is rendered;
 *   3. opens the picker, asserts the stored lab is pre-checked, unchecks it,
 *      saves and closes, and asserts the row is gone;
 *   4. reopens the picker, checks the lab again, saves and closes, and asserts
 *      exactly one row came back under the same id with the source-qualified identifier as
 *      the delegate value;
 *   5. opens and closes the picker once more and asserts the row is not duplicated.
 *
 * Nothing is submitted; the owned consultation and attachment fixtures are removed in a finally.
 *
 * The check SKIPS (exit 2) when the patient has no consultation request or no
 * HL7 lab, which is the case on a fresh install without the demo dataset.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:consultation-lab-attachment-rows-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=localhost MYSQL_USER=root MYSQL_PASSWORD=... MYSQL_DATABASE=carlos
 *   CONSULT_DEMO_NO=1
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const {
  EXIT_SKIP,
  SkipCheck,
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  createSqlRunner,
  gotoApp,
  launchBrowser,
  login,
  newContext,
  readConfig,
  sqlString,
  wirePage,
} = require('./lib/playwright-harness');

const config = readConfig({ require: ['MYSQL_PASSWORD'] });
const demographicNo = process.env.CONSULT_DEMO_NO || '1';
assert(/^\d+$/.test(demographicNo), 'CONSULT_DEMO_NO must be numeric');
const seedProvider = 'PWLROW';
const stamp = `PW_LAB_SOURCE_${Date.now()}_${process.pid}`;

const db = createSqlRunner(config.mysql);

function findRequestId() {
  return db.value(`SELECT requestId FROM consultationRequests WHERE demographicNo=${Number(demographicNo)} ORDER BY requestId DESC LIMIT 1`);
}

function findHl7LabNo() {
  return db.value(`SELECT lab_no FROM patientLabRouting WHERE demographic_no=${Number(demographicNo)} AND lab_type='HL7' ORDER BY lab_no LIMIT 1`);
}

function cleanupSeed(requestId) {
  assert(db.value(`SELECT reason FROM consultationRequests WHERE requestId=${Number(requestId)}`) === stamp, 'fixture parent ownership changed');
  db.execute(`DELETE FROM consultdocs WHERE requestId=${Number(requestId)}`);
  db.execute(`DELETE FROM consultationRequests WHERE requestId=${Number(requestId)} AND reason=${sqlString(stamp)}`);
}

async function openPicker(page) {
  await page.locator('#attachDocumentPanelBtn').click();
  await page.locator('#attachDocumentsForm').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('.ui-dialog .save-and-close-button').waitFor({ state: 'visible', timeout: 30000 });
}

async function saveAndClosePicker(page) {
  await page.locator('.ui-dialog .save-and-close-button').click();
  await page.locator('#attachDocumentsForm').waitFor({ state: 'hidden', timeout: 30000 });
}

(async () => {
  const recorder = createRecorder();
  const originalRequestId = findRequestId();
  let requestId = null;
  const labNo = findHl7LabNo();
  const browser = await launchBrowser(config);
  try {
    if (!originalRequestId || !labNo) {
      throw new SkipCheck(`demographic ${demographicNo} has no consultation request or no HL7 lab to seed; load the demo dataset`);
    }
    const checkboxId = `labNoHL7${labNo}`;
    const rowId = `#entry_${checkboxId}`;
    const delegateId = `#delegate_${checkboxId}`;

    // Clone the parent so this check never changes a clinician's saved attachment set.
    const columns = db.rows("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='consultationRequests' AND column_name<>'requestId' ORDER BY ordinal_position")
      .map(([column]) => { assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(column), 'unexpected schema identifier'); return column; });
    const projection = columns.map(column => column === 'reason' ? sqlString(stamp) : `\`${column}\``).join(',');
    requestId = db.value(`INSERT INTO consultationRequests (${columns.map(column => `\`${column}\``).join(',')}) SELECT ${projection} FROM consultationRequests WHERE requestId=${Number(originalRequestId)}; SELECT LAST_INSERT_ID()`);
    assert(Number(requestId) > 0, 'owned consultation was not created');
    for (const source of ["'HL7'", "'MDS'", 'NULL']) {
      db.execute(`INSERT INTO consultdocs (requestId, document_no, doctype, lab_type, deleted, attach_date, provider_no)`
        + ` VALUES (${Number(requestId)}, ${Number(labNo)}, 'L', ${source}, NULL, CURDATE(), '${seedProvider}')`);
    }

    const context = await newContext(browser, config);
    const landingPage = await login(context, config, recorder);
    await landingPage.close();
    const page = await context.newPage();
    wirePage(page, 'consult-lab-rows', recorder);
    await gotoApp(page, config.baseUrl, `/encounter/ViewRequest?requestId=${encodeURIComponent(requestId)}`, 'load');
    await assertNotErrorPage(page, 'consultation request');
    await page.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: 30000 });

    // 2. Stored row identity follows the picker checkbox id.
    assert(await page.locator(rowId).count() === 1, `stored lab row ${rowId} was not rendered`);
    assert(await page.locator(delegateId).count() === 1, `stored lab delegate ${delegateId} was not rendered`);
    assert(await page.locator(`#entry_labNo${labNo}`).count() === 0,
      `stored lab row still uses the unqualified id entry_labNo${labNo}`);

    // Colliding and unresolved stored IDs must stay separate and visibly removable.
    for (const source of ['MDS', 'UNRESOLVED']) {
      const missing = page.locator(`#entry_labNo${source}${labNo}`);
      assert(await missing.count() === 1, `${source} attachment was lost or replaced by HL7`);
      assert(await missing.locator('input').inputValue() === `${source}:${labNo}`, 'stored source changed');
      await missing.locator('.removeUnavailableLab').click();
      assert(await page.locator(rowId).count() === 1, 'removing another source removed HL7');
    }

    // 3. Unchecking the stored lab removes its row.
    await openPicker(page);
    const checkbox = page.locator(`#attachDocumentsForm #${checkboxId}`);
    await checkbox.waitFor({ state: 'visible', timeout: 30000 });
    assert(await checkbox.isChecked(), 'picker did not pre-check the stored lab');
    const preview = await checkbox.locator('..').locator('.preview-button').first().getAttribute('onclick');
    assert(preview.includes(`HL7:${labNo}`) && preview.includes('labType=HL7'), 'preview lost source identity');
    const cacheIsolated = await page.evaluate(id => {
      addPdfAttachment('LAB', `HL7:${id}`, 'hl7-test', []);
      addPdfAttachment('LAB', `MDS:${id}`, 'mds-test', []);
      const isolated = getPdfAttachment('LAB', `HL7:${id}`).base64Data === 'hl7-test'
        && getPdfAttachment('LAB', `MDS:${id}`).base64Data === 'mds-test';
      pdfCache = pdfCache.filter(item => item.base64Data !== 'hl7-test' && item.base64Data !== 'mds-test');
      return isolated;
    }, labNo);
    assert(cacheIsolated, 'PDF cache confused lab sources');
    const rejectedPreview = await page.evaluate(async ({id, patient}) => {
      const token = document.querySelector('input[name="CSRF-TOKEN"]');
      const params = new URLSearchParams({method:'renderLabPDF', segmentId:id, demographicNo:patient, labType:'MDS'});
      if (token) params.set('CSRF-TOKEN', token.value);
      const response = await fetch(`${window.location.pathname.substring(0, window.location.pathname.indexOf('/encounter/'))}/previewDocs?${params}`, {credentials:'same-origin'});
      return {status:response.status, text:await response.text()};
    }, {id:labNo, patient:demographicNo});
    assert(rejectedPreview.status === 400 && rejectedPreview.text.includes('lab_source_unsupported'), 'MDS preview reached the HL7 renderer');
    assert((await checkbox.getAttribute('class')) === 'lab_pre_check', 'stored lab was not marked as pre-checked');
    await checkbox.uncheck();
    await saveAndClosePicker(page);
    assert(await page.locator(rowId).count() === 0, 'unchecking the stored lab did not remove its row');

    // 4. Re-checking adds one row back under the same id.
    await openPicker(page);
    const again = page.locator(`#attachDocumentsForm #${checkboxId}`);
    await again.waitFor({ state: 'visible', timeout: 30000 });
    await again.check();
    await saveAndClosePicker(page);
    assert(await page.locator(rowId).count() === 1, `re-checking the lab did not add ${rowId} back`);
    assert(await page.locator(delegateId).count() === 1, `re-added row has no ${delegateId} input`);
    assert((await page.locator(delegateId).getAttribute('name')) === 'labNo', 're-added delegate is not named labNo');
    assert((await page.locator(delegateId).getAttribute('value')) === `HL7:${labNo}`,
      're-added delegate value lost its source');

    // 5. Another open/close does not duplicate the row.
    await openPicker(page);
    await page.locator(`#attachDocumentsForm #${checkboxId}`).waitFor({ state: 'visible', timeout: 30000 });
    await saveAndClosePicker(page);
    assert(await page.locator(rowId).count() === 1, 'reopening the picker duplicated the lab row');

    assertNoPageErrors(recorder);
    await context.close();
    console.log(`PASS consultation lab attachment rows keyed by source (request ${requestId}, lab HL7 ${labNo}: remove, re-add, no duplicate)`);
  } catch (error) {
    if (error instanceof SkipCheck) {
      console.log(`SKIP ${error.message}`);
      process.exitCode = EXIT_SKIP;
    } else {
      console.error('FAIL consultation lab attachment rows Playwright check');
      console.error(error.stack || error.message);
      console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
      process.exitCode = 1;
    }
  } finally {
    if (requestId) {
      cleanupSeed(requestId);
    }
    db.dispose();
    await browser.close();
  }
})();
