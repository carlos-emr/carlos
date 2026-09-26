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
 *      exactly one row came back under the same id with the bare segment id as
 *      the delegate value;
 *   5. opens and closes the picker once more and asserts the row is not duplicated.
 *
 * Nothing is submitted; the seeded consultdocs row is removed in a finally.
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
  wirePage,
} = require('./lib/playwright-harness');

const config = readConfig({ require: ['MYSQL_PASSWORD'] });
const demographicNo = process.env.CONSULT_DEMO_NO || '1';
assert(/^\d+$/.test(demographicNo), 'CONSULT_DEMO_NO must be numeric');
// The seed is tagged by provider so the finally only removes what this run added.
const seedProvider = 'PWLROW';

const db = createSqlRunner(config.mysql);

function findRequestId() {
  return db.value(`SELECT requestId FROM consultationRequests WHERE demographicNo=${Number(demographicNo)} ORDER BY requestId DESC LIMIT 1`);
}

function findHl7LabNo() {
  return db.value(`SELECT lab_no FROM patientLabRouting WHERE demographic_no=${Number(demographicNo)} AND lab_type='HL7' ORDER BY lab_no LIMIT 1`);
}

function cleanupSeed(requestId) {
  db.execute(`DELETE FROM consultdocs WHERE requestId=${Number(requestId)} AND provider_no='${seedProvider}'`);
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
  const requestId = findRequestId();
  const labNo = findHl7LabNo();
  const browser = await launchBrowser(config);
  try {
    if (!requestId || !labNo) {
      throw new SkipCheck(`demographic ${demographicNo} has no consultation request or no HL7 lab to seed; load the demo dataset`);
    }
    const checkboxId = `labNoHL7${labNo}`;
    const rowId = `#entry_${checkboxId}`;
    const delegateId = `#delegate_${checkboxId}`;

    // 1. Seed the stored lab attachment and open the request.
    cleanupSeed(requestId);
    db.execute(`INSERT INTO consultdocs (requestId, document_no, doctype, deleted, attach_date, provider_no)`
      + ` VALUES (${Number(requestId)}, ${Number(labNo)}, 'L', NULL, CURDATE(), '${seedProvider}')`);

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

    // 3. Unchecking the stored lab removes its row.
    await openPicker(page);
    const checkbox = page.locator(`#attachDocumentsForm #${checkboxId}`);
    await checkbox.waitFor({ state: 'visible', timeout: 30000 });
    assert(await checkbox.isChecked(), 'picker did not pre-check the stored lab');
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
    assert((await page.locator(delegateId).getAttribute('value')) === String(labNo),
      're-added delegate value is not the bare segment id');

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
