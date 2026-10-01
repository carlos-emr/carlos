#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * HRM report viewer actions that no other check drives (gap-records, HRM).
 * detached-delete-hrm covers Add Comment / Delete comment / (remove) provider and hrm-report-print-download
 * covers list, print and download; this one drives the rest of hospitalReportManager/Modify.
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ HRM Documents heading (ViewDocList popup) ▸
 * report (Display) ▸ Sign-Off / Revoke Sign-Off ▸ Set Description ▸ Categorization (edit) ▸ Assigned
 * Providers autocomplete ▸ patient (remove) and the patient autocomplete.
 * Asserts, against HRMDocumentToProvider / HRMDocument / HRMDocumentToDemographic: Sign-Off stores
 * signedOff=1 with a timestamp for the viewer only and flips the button to Revoke Sign-Off, which stores 0;
 * Set Description stores the typed text and the document list shows it; choosing a category stores
 * hrmCategoryId and the read-only label shows its name; picking a provider in the autocomplete adds exactly
 * that provider's unsigned routing row; (remove) beside the patient deletes the patient link and disables the
 * patient buttons, and picking the patient again in the autocomplete restores the link.
 * Fixtures: the runWorkflow FAKE- patient, one owned schema-valid text HRM XML file in DOCUMENT_DIR with its
 * HRMDocument / HRMDocumentToDemographic / HRMDocumentToProvider rows, and a second active provider only read.
 * Cleanup deletes the owned rows and file and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { createOwnedHrmReport } = require('./lib/gap-records-hrm-fixture');

const TIMEOUT = 30000;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const other = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no REGEXP '^[1-9][0-9]*$'
    AND provider_no<>${h.sqlString(provider)} AND COALESCE(last_name,'')<>'' AND last_name NOT LIKE 'FAKE-PW%'
    AND last_name REGEXP '^[A-Za-z-]+$' AND (SELECT COUNT(*) FROM provider p2 WHERE p2.last_name=provider.last_name)=1
    ORDER BY provider_no LIMIT 1`);
  if (!other) throw new h.SkipCheck('The database has no second active provider to assign the report to');
  const otherName = sql.rows(`SELECT last_name, first_name FROM provider WHERE provider_no=${h.sqlString(other)}`)[0];
  const reportText = `${marker} HRM report for the viewer actions`;
  // A report matched to a patient whose HIN is NULL makes the Inbox list answer 500 for every provider
  // (HRMResultsData.java:180); the owned patient therefore gets the empty HIN the demographic form stores.
  // gap-records-hrm-inbox-null-hin asserts that defect on its own, exclusively.
  sql.execute(`UPDATE demographic SET hin='' WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  // The patient autocomplete only offers patients admitted to a program the viewer belongs to (the program-domain
  // restriction in DemographicDaoImpl), so the owned patient is admitted to one of the viewer's own programs.
  const program = sql.value(`SELECT program_id FROM program_provider WHERE provider_no=${h.sqlString(provider)} ORDER BY program_id LIMIT 1`);
  if (!/^[1-9]\d*$/.test(program)) throw new h.SkipCheck('The test provider belongs to no program, so the patient autocomplete cannot offer a patient');
  sql.execute(`INSERT INTO admission (client_id,program_id,provider_no,admission_date,admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)
    VALUES (${patient},${program},${h.sqlString(provider)},NOW(),0,0,'current',NOW())`);
  s.cleanup(() => sql.execute(`DELETE FROM admission WHERE client_id=${patient}`));
  const report = createOwnedHrmReport({ sql, marker, suffix: 'actions', description: `${marker} before`, reportText });
  s.cleanup(() => report.remove());
  const reportId = report.create();
  const key = h.sqlString(reportId);
  sql.execute(`INSERT INTO HRMDocumentToDemographic (demographicNo,hrmDocumentId,timeAssigned) VALUES (${patient},${key},NOW());
    INSERT INTO HRMDocumentToProvider (providerNo,hrmDocumentId,signedOff,viewed) VALUES (${h.sqlString(provider)},${key},0,0)`);
  const own = `hrmDocumentId=${key} AND providerNo=${h.sqlString(provider)}`;

  let list;
  let viewer;
  await s.step('E-Chart HRM Documents opens the owned report', async () => {
    const chart = await s.chart();
    list = await s.popup(chart, chart.locator('#leftNavBar a[onclick*="/hospitalReportManager/ViewDocList"]').first(), 'hrm-actions-list');
    viewer = await s.popup(list, list.locator(`#tblHRM a[onclick*="Display?id=${reportId}'"]`), 'hrm-actions-report');
    await viewer.locator(`#hrmdoc_${reportId}`).waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert((await viewer.locator('#hrmReportContent').innerText()).includes(reportText), 'The owned report body is not displayed');
  });

  await s.step('Sign-Off stores the viewer\'s sign-off and Revoke Sign-Off clears it', async () => {
    const button = viewer.locator(`#signoff${reportId}`);
    h.assert((await button.getAttribute('value')) === 'Sign-Off', 'A fresh report does not offer Sign-Off');
    await button.click();
    await expectValue(sql, `SELECT signedOff FROM HRMDocumentToProvider WHERE ${own}`, '1', 'Sign-Off did not store signedOff=1');
    h.assert(sql.value(`SELECT signedOffTimestamp IS NOT NULL FROM HRMDocumentToProvider WHERE ${own}`) === '1', 'Sign-Off stored no timestamp');
    await viewer.locator(`#signoff${reportId}[value="Revoke Sign-Off"]`).waitFor({ timeout: TIMEOUT });
    await viewer.locator(`#signoff${reportId}`).click();
    await expectValue(sql, `SELECT signedOff FROM HRMDocumentToProvider WHERE ${own}`, '0', 'Revoke Sign-Off did not store signedOff=0');
    await viewer.locator(`#signoff${reportId}[value="Sign-Off"]`).waitFor({ timeout: TIMEOUT });
  });

  await s.step('Set Description stores the typed text and the document list shows it', async () => {
    const description = `${marker} described`;
    await viewer.locator(`[id="descriptionField_${reportId}_hrm"]`).fill(description);
    await viewer.locator('#descriptionBox input[value="Set Description"]').click();
    await viewer.locator(`#descriptionstatus${reportId}`).filter({ hasText: /Success/ }).waitFor({ timeout: TIMEOUT });
    await expectValue(sql, `SELECT description FROM HRMDocument WHERE id=${reportId}`, description, 'Set Description did not store the text');
    await list.reload();
    await list.locator('#tblHRM').waitFor({ timeout: TIMEOUT });
    h.assert((await list.locator('#tblHRM').innerText()).includes(description), 'The document list does not show the new description');
  });

  await s.step('choosing a category stores it and the read-only label shows its name', async () => {
    await viewer.locator(`#showCategory_${reportId} a`, { hasText: '(edit)' }).click();
    const name = sql.value(`SELECT categoryName FROM HRMCategory WHERE id=2`);
    await viewer.locator(`#selectedCategory_${reportId}`).selectOption('2');
    await expectValue(sql, `SELECT hrmCategoryId FROM HRMDocument WHERE id=${reportId}`, '2', 'The chosen category was not stored');
    await viewer.locator(`#hrmCategory_${reportId}`).filter({ hasText: name }).waitFor({ timeout: TIMEOUT });
  });

  await s.step('picking a provider in Assigned Providers adds exactly that provider\'s unsigned routing row', async () => {
    const input = viewer.locator(`#autocompleteprov${reportId}hrm`);
    await input.click();
    await input.pressSequentially(otherName[0], { delay: 60 });
    const choice = viewer.locator('ul.ui-autocomplete:visible li').filter({ hasText: otherName[0] }).first();
    await choice.waitFor({ timeout: TIMEOUT }).catch(async error => {
      throw new Error(`${error.message.split('\n')[0]} -- menu: ${JSON.stringify(await viewer.locator('ul.ui-autocomplete li').allInnerTexts())}`);
    });
    await choice.click();
    await expectValue(sql, `SELECT COUNT(*) FROM HRMDocumentToProvider WHERE hrmDocumentId=${key} AND providerNo=${h.sqlString(other)}`, '1',
      'Picking the provider did not add exactly one routing row');
    h.assert(sql.value(`SELECT signedOff FROM HRMDocumentToProvider WHERE hrmDocumentId=${key} AND providerNo=${h.sqlString(other)}`) !== '1',
      'The new routing row arrived already signed off');
    h.assert(sql.value(`SELECT COUNT(*) FROM HRMDocumentToProvider WHERE ${own}`) === '1', 'Assigning a provider changed the viewer\'s own routing');
  });

  await s.step('(remove) unlinks the patient and the patient autocomplete links them again', async () => {
    await viewer.locator(`#demostatus${reportId} a`, { hasText: '(remove)' }).click();
    await expectValue(sql, `SELECT COUNT(*) FROM HRMDocumentToDemographic WHERE hrmDocumentId=${key}`, '0', '(remove) did not delete the patient link');
    h.assert(await viewer.locator(`#mainMaster_${reportId}`).isDisabled(), 'The patient buttons stay enabled after the patient was unlinked');
    const input = viewer.locator(`#autocompletedemo${reportId}hrm`);
    await input.waitFor({ state: 'visible', timeout: TIMEOUT });
    await input.click();
    const searches = [];
    viewer.on('response', async response => {
      if (/SearchDemographic/.test(response.url())) searches.push(`${response.status()} ${(await response.text().catch(() => '')).slice(0, 160)}`);
    });
    await input.pressSequentially(marker, { delay: 40 });
    const choice = viewer.locator('ul.ui-autocomplete:visible li').filter({ hasText: marker }).first();
    await choice.waitFor({ timeout: TIMEOUT }).catch(async error => {
      throw new Error(`${error.message.split('\n')[0]} -- patient search responses: ${JSON.stringify(searches.slice(-2))}`);
    });
    await choice.click();
    await expectValue(sql, `SELECT demographicNo FROM HRMDocumentToDemographic WHERE hrmDocumentId=${key}`, patient, 'Picking the patient did not restore the link');
    await viewer.locator(`#demostatus${reportId} a`, { hasText: '(remove)' }).waitFor({ timeout: TIMEOUT });
  });

  await s.step('the provider autocomplete menu shows names, not markup', async () => {
    const input = viewer.locator(`#autocompleteprov${reportId}hrm`);
    await input.click();
    await input.pressSequentially(otherName[0].slice(0, 4), { delay: 60 });
    await viewer.locator('ul.ui-autocomplete:visible li').first().waitFor({ timeout: TIMEOUT });
    const shown = await viewer.locator('ul.ui-autocomplete:visible li').allInnerTexts();
    const literal = shown.filter(text => /<\/?span/.test(text));
    h.assert(literal.length === 0, `The provider menu prints the match-highlighting markup as text on ${literal.length} of ${shown.length} entries `
      + '(carlosAutocomplete.js renders item.label with .text(), but the label is already HTML)');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-hrm-report-actions', workflow, { openPatient: true });
