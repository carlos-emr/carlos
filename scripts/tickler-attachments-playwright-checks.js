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
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for tickler attachments through the shared attachment picker (#3984).
 *
 *   1. Add Tickler: opens the picker for the demo patient, checks every kind of item the
 *      picker offers (document, lab, eForm, HRM report, form), saves, and asserts the
 *      ticklerdocs rows: one live row per checked item, the lab row carrying its source, the
 *      provider column holding the LOGGED-IN provider.
 *   2. Edit Tickler: the stored attachments are listed by name and pre-checked in the
 *      picker; unchecking the document and saving soft-deletes exactly that row.
 *   3. Edit Tickler without opening the picker: a plain field update leaves the stored
 *      set untouched (the parallel fork detached everything on every save).
 *   4. Tickler list and patient tickler view render one attachment link per live row, and
 *      the list JSON carries the legacy viewer code per link.
 *   5. Crafted requests: another patient's document is refused by the edit action and the
 *      row count is unchanged; a GET to the edit action is 405; the picker endpoint is 400
 *      on a non-numeric demographic and 403/redirect-free otherwise.
 *
 * The check SKIPS (exit 2) when the patient has no document, lab, or eForm to attach,
 * which is the case on a fresh install without the demo dataset.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:tickler-attachments-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=localhost MYSQL_USER=root MYSQL_PASSWORD=... MYSQL_DATABASE=carlos
 *   TICKLER_DEMOGRAPHIC_NO=1
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const {
  EXIT_SKIP,
  SkipCheck,
  assert,
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
const demographicNo = process.env.TICKLER_DEMOGRAPHIC_NO || '1';
assert(/^\d+$/.test(demographicNo), 'TICKLER_DEMOGRAPHIC_NO must be numeric');
const stamp = `PW_TICKLER_ATTACH_${Date.now()}`;
const message = `${stamp} call patient about attached results`;

const db = createSqlRunner(config.mysql);

function ticklerRows() {
  return db.rows(`SELECT tickler_no, status FROM tickler WHERE message LIKE ${sqlString(`%${stamp}%`)} ORDER BY tickler_no`)
    .map(([id, status]) => ({ id, status }));
}

function attachmentRows(ticklerNo) {
  return db.rows(`SELECT doctype, document_no, IFNULL(lab_type, ''), provider_no, IFNULL(deleted, '') FROM ticklerdocs WHERE tickler_id=${Number(ticklerNo)} ORDER BY id`)
    .map(([doctype, documentNo, labType, providerNo, deleted]) => ({ doctype, documentNo, labType, providerNo, deleted }));
}

function liveRows(ticklerNo) {
  return attachmentRows(ticklerNo).filter((row) => row.deleted === '');
}

function cleanupRows() {
  for (const row of ticklerRows()) {
    db.execute(`DELETE FROM ticklerdocs WHERE tickler_id=${Number(row.id)}`);
    db.execute(`DELETE FROM tickler_comments WHERE tickler_no=${Number(row.id)}`);
    db.execute(`DELETE FROM tickler_update WHERE tickler_no=${Number(row.id)}`);
    db.execute(`DELETE FROM tickler WHERE tickler_no=${Number(row.id)}`);
  }
}

async function openPicker(page) {
  await page.locator('#manageAttachmentsBtn').click();
  await page.locator('#attachDocumentsForm').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('.ui-dialog .save-and-close-button').waitFor({ state: 'visible', timeout: 30000 });
}

async function saveAndClosePicker(page) {
  await page.locator('.ui-dialog .save-and-close-button').click();
  await page.locator('#attachDocumentsForm').waitFor({ state: 'hidden', timeout: 30000 });
}

async function checkFirstOfEachKind(page) {
  // The picker collapses long lists; the first entry of each kind is always rendered.
  const picked = {};
  for (const [cls, key] of [
    ['.document_check', 'D'], ['.lab_check', 'L'], ['.eForm_check', 'E'], ['.hrm_check', 'H'], ['.form_check', 'F'],
  ]) {
    const box = page.locator(`#attachDocumentsForm ${cls}:not([disabled])`).first();
    if (await box.count() === 0) {
      continue;
    }
    await box.check();
    picked[key] = await box.inputValue();
  }
  return picked;
}

async function submitAddForm(page) {
  await page.locator('.action-bar-bottom input.btn-primary[name="Button"]').first().click();
  await page.waitForFunction(() => {
    const frame = document.getElementById('ticklerSubmitFrame');
    return frame && frame.contentDocument && frame.contentDocument.getElementById('tickler-save-ok');
  }, null, { timeout: 30000 });
  const linkFailed = await page.evaluate(() => {
    const frame = document.getElementById('ticklerSubmitFrame');
    return Boolean(frame.contentDocument.getElementById('tickler-save-ok-link-failed'));
  });
  assert(!linkFailed, 'the add action reported that an attachment was refused');
}

async function submitEditForm(page) {
  await page.locator('input[name="updateTickler"]').click();
  await page.waitForFunction(() => {
    const frame = document.getElementById('ticklerEditFrame');
    return frame && frame.contentDocument && frame.contentDocument.getElementById('tickler-edit-ok');
  }, null, { timeout: 30000 });
}

async function openEdit(context, recorder, ticklerNo, label) {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, `/tickler/ViewTicklerEdit?tickler_no=${encodeURIComponent(ticklerNo)}`, 'load');
  await page.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 30000 });
  return page;
}

async function postEditForm(page, fields) {
  // A classic form POST through the browser session, with the CSRF token CSRFGuard injected
  // into the page's real form, so what is exercised is the action's own gate, not CSRF.
  return page.evaluate(async ({ ctx, fields }) => {
    const token = document.querySelector('input[name="CSRF-TOKEN"]');
    const body = new URLSearchParams(fields);
    if (token && token.value) {
      body.set('CSRF-TOKEN', token.value);
    }
    const response = await fetch(`${ctx}/tickler/EditTickler`, {
      method: 'POST',
      body,
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });
    return { status: response.status, text: await response.text() };
  }, { ctx: config.baseUrl.pathname, fields });
}

(async () => {
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  const providerNo = db.value(`SELECT provider_no FROM security WHERE user_name=${sqlString(config.testUser)} LIMIT 1`);
  assert(providerNo, `no security row for ${config.testUser}`);
  const otherPatientDocument = db.value(`SELECT document_no FROM ctl_document WHERE module='demographic' AND module_id<>${Number(demographicNo)} AND status='A' ORDER BY document_no LIMIT 1`);
  cleanupRows();
  try {
    const context = await newContext(browser, config);
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    // 1. Add with attachments -----------------------------------------------------------
    const addPage = await context.newPage();
    wirePage(addPage, 'tickler-add', recorder);
    await gotoApp(addPage, config.baseUrl, `/tickler/ViewAddTickler?updateParent=true&bFirstDisp=false&demographic_no=${encodeURIComponent(demographicNo)}`, 'load');
    await addPage.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 30000 });
    await addPage.locator('textarea[name="ticklerMessage"]').fill(message);
    await openPicker(addPage);
    const picked = await checkFirstOfEachKind(addPage);
    if (!picked.D || !picked.L) {
      throw new SkipCheck(`patient ${demographicNo} has no document and lab to attach; load the demo dataset`);
    }
    await saveAndClosePicker(addPage);
    const expectedCount = Object.keys(picked).length;
    assert((await addPage.locator('#attachmentCount').innerText()).trim() === String(expectedCount),
      `add form badge did not show ${expectedCount} attachments`);
    assert(await addPage.locator('#attachmentsSubmitted').inputValue() === '1', 'picker did not set the submitted marker');
    await submitAddForm(addPage);
    await addPage.close();

    const rows = ticklerRows();
    assert(rows.length === 1, `expected one created tickler, found ${rows.length}`);
    const ticklerNo = rows[0].id;
    let live = liveRows(ticklerNo);
    assert(live.length === expectedCount, `expected ${expectedCount} ticklerdocs rows, got ${JSON.stringify(live)}`);
    for (const [doctype, documentNo] of Object.entries(picked)) {
      const row = live.find((item) => item.doctype === doctype && item.documentNo === documentNo);
      assert(row, `no live ${doctype} row for ${documentNo}: ${JSON.stringify(live)}`);
      assert(row.providerNo === providerNo, `${doctype} row provider was ${row.providerNo}, expected the session provider ${providerNo}`);
      if (doctype === 'L') {
        assert(row.labType !== '', 'lab row did not record its lab source');
      }
    }

    // 2. Edit: names listed, pre-checked, detach the document ----------------------------
    let editPage = await openEdit(context, recorder, ticklerNo, 'tickler-edit');
    assert((await editPage.locator('#attachmentCount').innerText()).trim() === String(expectedCount),
      'edit form badge did not show the stored attachment count');
    assert(await editPage.locator('#attachmentNames li').count() === expectedCount, 'edit form did not list every attachment by name');
    assert(await editPage.locator(`#delegate_docNo${picked.D}`).count() === 1, 'document delegate input missing on edit form');
    const storedLabType = live.find((row) => row.doctype === 'L').labType;
    assert(await editPage.locator(`#delegate_labNo${picked.L}`).inputValue() === `${storedLabType}:${picked.L}`,
      'lab delegate input does not carry the lab source');
    await openPicker(editPage);
    const docBox = editPage.locator(`#attachDocumentsForm #docNo${picked.D}`);
    assert(await docBox.isChecked(), 'stored document was not pre-checked in the picker');
    assert(await editPage.locator(`#attachDocumentsForm #labNo${picked.L}`).isChecked(), 'stored lab was not pre-checked in the picker');
    await docBox.uncheck();
    await saveAndClosePicker(editPage);
    assert((await editPage.locator('#attachmentCount').innerText()).trim() === String(expectedCount - 1), 'badge did not drop after detaching');
    await submitEditForm(editPage);
    await editPage.close();

    live = liveRows(ticklerNo);
    assert(live.length === expectedCount - 1 && !live.some((row) => row.doctype === 'D'),
      `document was not detached: ${JSON.stringify(attachmentRows(ticklerNo))}`);
    assert(attachmentRows(ticklerNo).some((row) => row.doctype === 'D' && row.deleted === 'Y'), 'detached document row was not soft-deleted');

    // 3. Edit without the picker leaves attachments alone ---------------------------------
    editPage = await openEdit(context, recorder, ticklerNo, 'tickler-edit-plain');
    await editPage.locator('#priority').selectOption('High');
    await submitEditForm(editPage);
    await editPage.close();
    assert(liveRows(ticklerNo).length === expectedCount - 1, 'a plain edit changed the stored attachments');

    // 4. Lists ----------------------------------------------------------------------------
    const listPage = await context.newPage();
    wirePage(listPage, 'tickler-list', recorder);
    const listJson = await listPage.request.get(
      `${config.baseUrl.href}/tickler/ListTicklers?demographicNo=${encodeURIComponent(demographicNo)}&status=A&start=0&length=500`,
    );
    assert(listJson.status() === 200, `ListTicklers returned ${listJson.status()}`);
    const listRow = (await listJson.json()).data.find((row) => row.id === Number(ticklerNo));
    assert(listRow, 'created tickler missing from ListTicklers JSON');
    assert(listRow.links.length === expectedCount - 1, `ListTicklers carried ${listRow.links.length} links, expected ${expectedCount - 1}`);
    const labLink = listRow.links.find((link) => link.tableId === Number(picked.L));
    assert(labLink && labLink.tableName === live.find((row) => row.doctype === 'L').labType,
      `lab link did not carry its viewer code: ${JSON.stringify(listRow.links)}`);
    await gotoApp(listPage, config.baseUrl, `/tickler/ViewTicklerMain?ticklerview=A&demoview=${encodeURIComponent(demographicNo)}`, 'networkidle');
    await listPage.locator('#ticklerResults tbody tr', { hasText: stamp }).first().waitFor({ state: 'visible', timeout: 30000 });
    // An encounter form whose id is claimed by more than one form table renders as an unlinked
    // paperclip rather than a link to the wrong form; count both shapes, and require that the
    // lab (always addressable) is a real link.
    const listTableRow = listPage.locator('#ticklerResults tbody tr', { hasText: stamp }).first();
    const listLinks = await listTableRow.locator('a[href^="javascript:reportWindow"], i.fa-paperclip[title]').count();
    assert(listLinks === expectedCount - 1, `tickler list rendered ${listLinks} attachment markers, expected ${expectedCount - 1} (picked ${JSON.stringify(picked)})`);
    assert(await listTableRow.locator('a[href*="segmentID="]').count() >= 1, 'tickler list did not link the lab attachment');
    await gotoApp(listPage, config.baseUrl, `/tickler/ViewTicklerDemoMain?demoview=${encodeURIComponent(demographicNo)}&ticklerview=A`, 'networkidle');
    const demoRow = listPage.locator('tr', { hasText: stamp }).first();
    const demoLinks = await demoRow.locator('a, span').filter({ hasText: /^ATT$/ }).count();
    assert(demoLinks === expectedCount - 1, `patient tickler view rendered ${demoLinks} ATT markers, expected ${expectedCount - 1}`);
    assert(await demoRow.locator('a[href*="segmentID="]').count() >= 1, 'patient tickler view did not link the lab attachment');
    await listPage.close();

    // 5. Crafted requests -----------------------------------------------------------------
    const guardPage = await openEdit(context, recorder, ticklerNo, 'tickler-guard');
    const serviceDate = db.value(`SELECT DATE(service_date) FROM tickler WHERE tickler_no=${Number(ticklerNo)}`);
    if (otherPatientDocument) {
      const refused = await postEditForm(guardPage, {
        method: 'editTickler', ticklerNo, status: 'A', priority: 'High', assignedToProviders: providerNo,
        xml_appointment_date: serviceDate, attachmentsSubmitted: '1', docNo: otherPatientDocument,
      });
      assert(!/tickler-edit-ok/.test(refused.text), 'edit action accepted another patient\'s document');
      assert(!liveRows(ticklerNo).some((row) => row.doctype === 'D'), 'another patient\'s document was attached');
    } else {
      console.log('SKIP crafted foreign-document POST: no other patient document in this database');
    }
    // A lab id is only meaningful within its source: the same id under a source that does not
    // route it to this patient must be refused, and the stored lab must survive the attempt.
    const storedLab = live.find((row) => row.doctype === 'L');
    const wrongSource = storedLab.labType === 'HL7' ? 'MDS' : 'HL7';
    const wrongSourceRefused = await postEditForm(guardPage, {
      method: 'editTickler', ticklerNo, status: 'A', priority: 'High', assignedToProviders: providerNo,
      xml_appointment_date: serviceDate, attachmentsSubmitted: '1', labNo: `${wrongSource}:${storedLab.documentNo}`,
    });
    assert(!/tickler-edit-ok/.test(wrongSourceRefused.text), 'edit action accepted a lab id under the wrong source');
    assert(liveRows(ticklerNo).some((row) => row.doctype === 'L' && row.labType === storedLab.labType && row.documentNo === storedLab.documentNo),
      `stored lab was disturbed by the refused wrong-source POST: ${JSON.stringify(attachmentRows(ticklerNo))}`);
    const getStatus = await guardPage.evaluate(async (ctx) => {
      const response = await fetch(`${ctx}/tickler/EditTickler?method=editTickler&ticklerNo=1`, { credentials: 'same-origin' });
      return response.status;
    }, config.baseUrl.pathname);
    assert(getStatus === 405, `GET to the edit action returned ${getStatus}, expected 405`);
    const badPicker = await guardPage.request.get(`${config.baseUrl.href}/previewDocs?method=fetchTicklerDocuments&demographicNo=abc`);
    assert(badPicker.status() === 400, `picker endpoint returned ${badPicker.status()} for a non-numeric demographic, expected 400`);
    const picker = await guardPage.request.get(`${config.baseUrl.href}/previewDocs?method=fetchTicklerDocuments&demographicNo=${encodeURIComponent(demographicNo)}`);
    assert(picker.status() === 200 && /attachDocumentsForm/.test(await picker.text()), 'picker endpoint did not render the picker for the patient');
    await guardPage.close();

    const pageErrors = recorder.pageErrors || [];
    assert(pageErrors.length === 0, `pages reported uncaught errors: ${JSON.stringify(pageErrors)}`);

    await context.close();
    console.log(`PASS tickler attachments: ${expectedCount} attached through the picker (${Object.keys(picked).join('')}), one detached, plain edit untouched, lists rendered, crafted requests (foreign document, wrong lab source, GET, bad picker id) refused`);
  } catch (error) {
    if (error instanceof SkipCheck) {
      console.log(`SKIP ${error.message}`);
      process.exitCode = EXIT_SKIP;
    } else {
      console.error('FAIL tickler attachments Playwright check');
      console.error(error.stack || error.message);
      console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
      process.exitCode = 1;
    }
  } finally {
    cleanupRows();
    db.dispose();
    await browser.close();
  }
})();
