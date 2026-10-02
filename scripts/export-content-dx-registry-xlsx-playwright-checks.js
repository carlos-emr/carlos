#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Disease Registry report ▸ Download Excel: the workbook's CONTENT against the database.
 * User path: Schedule ▸ Administration ▸ Reports ▸ Disease Registry (iframe) ▸ code autocomplete ▸ Add ▸
 * provider ▸ status filter ▸ Search ▸ Download Excel (report/DxresearchReport?method=patientExcelReport).
 * dx-registry-status-update proves the on-screen list; nothing opened the .xlsx a clinic mails to a
 * registry, so a wrong column, a clipped value or another provider's patient would pass unseen.
 *
 * Asserts: the download is an .xlsx workbook; its header row names the eleven registry columns; the
 * owned patient's row (first name with an accent, quote and comma; last name; sex; DOB; phone; HIN;
 * code system; code; start date; status) equals the database for each status filter (All, Active,
 * Resolved, Deleted), row for row; the workbook has exactly the rows of the on-screen list (an owned patient
 * of ANOTHER provider is absent; the deleted diagnosis appears under All and Deleted only); the same click
 * does not change dxresearch.
 * Fixtures: the runWorkflow FAKE-PW patient (names set to non-ASCII text), a second FAKE- patient of
 * another provider holding the same code, three dxresearch rows with unused ICD-9 codes (the deleted one's code is added to the report filters too). Cleanup
 * deletes only those rows and asserts them gone. Needs no Poppler.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates, typeAutocomplete } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const x = require('./lib/export-content-helpers');

// The registry's own column order (on screen and in the workbook).
const HEADER = ['First Name', 'Last Name', 'Sex', 'DOB', 'Phone', 'HIN', 'Code System', 'Code', 'Start Date', 'Update Date', 'Status'];
const CODE = { code: '036', description: 'MENINGOCOCCAL INFECTION' };
const OTHER_CODE = '0369';
const DELETED_CODE = '0360'; // seeded with status D and selected in the report, so the status filters are exercised on it
const FIRST = 'Zoë "Zed", O\'Neil-Ünal';

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const scratch = x.scratchDir();
  const other = { id: null };
  s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));
  s.cleanup(() => {
    sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${patient}`);
    if (other.id) {
      sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${other.id};
        DELETE FROM demographic WHERE demographic_no=${other.id} AND last_name=${q(marker)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM demographic WHERE last_name=${q(marker)} AND demographic_no<>${patient})`) === '0',
    'Owned registry fixtures were not removed');
  });

  const otherProvider = sql.value(`SELECT provider_no FROM provider WHERE provider_no<>${q(provider)} AND status='1'
    AND provider_no NOT IN ('-1') ORDER BY provider_no LIMIT 1`);
  h.assert(otherProvider, 'No second provider exists for the cross-provider fixture');
  sql.execute(`UPDATE demographic SET first_name=${q(FIRST)}, year_of_birth='1962', month_of_birth='07', date_of_birth='09',
    sex='F', phone='416-555-0142', hin='9876543217', ver='AB' WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  other.id = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
    patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(marker)},'OtherProviderPatient','1970','01','01','M','AC',${q(otherProvider)},'ON','ON','NR',NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(other.id), 'The second synthetic patient was not created');
  const seed = (demo, code, status, start) => sql.execute(`INSERT INTO dxresearch
    (demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association,providerNo)
    VALUES (${demo},${q(start)},'2024-05-06 07:08:09',${q(status)},${q(code)},'icd9',0,${q(provider)})`);
  seed(patient, CODE.code, 'A', '2019-03-05');
  seed(patient, OTHER_CODE, 'C', '2020-11-30');
  seed(patient, DELETED_CODE, 'D', '2021-01-02');
  seed(other.id, CODE.code, 'A', '2018-04-03');
  const owned = status => sql.rows(`SELECT d.first_name, d.last_name, d.hin, d.year_of_birth, d.month_of_birth, d.date_of_birth,
      x.coding_system, x.dxresearch_code, d.sex, x.start_date, x.status, x.update_date, d.phone
    FROM dxresearch x JOIN demographic d ON d.demographic_no=x.demographic_no
    WHERE x.demographic_no=${patient} AND x.dxresearch_code IN (${q(CODE.code)},${q(OTHER_CODE)},${q(DELETED_CODE)})
      ${status ? `AND x.status=${q(status)}` : ''} ORDER BY x.dxresearch_code`);

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'dx-xlsx-administration', timeout: 20000 });
  const link = admin.getByRole('link', { name: 'Disease Registry', exact: true, includeHidden: true });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const report = await (await iframe.elementHandle()).contentFrame();
  h.assert(report, 'Disease Registry report iframe did not load');
  async function submit(control) {
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === report });
    await control.click();
    await navigated;
    await report.waitForLoadState('networkidle').catch(() => {});
  }

  await s.step('the report is filtered by the three owned codes (two live diagnoses and one deleted)', async () => {
    await report.locator('#codesearch').waitFor({ state: 'visible' });
    for (const [code, description] of [[CODE.code, CODE.description], [OTHER_CODE, 'MENINGOCOCCAL INFECT NOS'], [DELETED_CODE, 'MENINGOCOCCAL MENINGITIS']]) {
      await report.locator('#codingSystem').selectOption('icd9');
      await typeAutocomplete(report, '#codesearch', description, { option: new RegExp(`^${code}: `) });
      await submit(report.getByRole('button', { name: 'Add', exact: true }));
    }
    await report.locator('#provider_no').selectOption(provider);
  });

  const headers = [];
  const updates = [];
  const screenRows = async () => {
    await report.locator('#listview_filter input').fill(marker);
    return report.locator('#listview tbody tr').evaluateAll(trs => trs
      .map(tr => [...tr.querySelectorAll('td')].map(td => td.textContent.trim())).filter(cells => cells.length >= 11));
  };

  for (const [radio, status, label] of [['ALL', '', 'All'], ['Active', 'A', 'Active'], ['Resolved', 'C', 'Resolved'], ['Deleted', 'D', 'Deleted']]) {
    // eslint-disable-next-line no-await-in-loop
    await s.step(`${label}: the downloaded workbook equals the database and the on-screen list`, async () => {
      await report.locator('#provider_no').selectOption(provider);
      await report.getByLabel(radio, { exact: true }).check();
      await submit(report.getByRole('button', { name: 'Search', exact: true }));
      const onScreen = await screenRows();
      h.assert(onScreen.length === owned(status).length,
        `${label}: the on-screen list has ${onScreen.length} owned rows (${onScreen.map(c => `${c[7]}/${c[10]}`).join(',')}), the database ${owned(status).length}`);
      const before = sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${patient},${other.id})`);
      const button = report.getByRole('button', { name: 'Download Excel' });
      await button.waitFor({ state: 'visible' });
      const file = await x.saveDownload(admin, scratch, () => button.click(), { route: /\/report\/DxresearchReport$/ });
      h.assert(file.status === 200, `Download Excel answered HTTP ${file.status}`);
      h.assert(/\.xlsx$/i.test(file.name), 'The download is not named as an Excel workbook');
      const grid = x.xlsxRows(file.bytes);
      const headerAt = grid.findIndex(row => row.some(cell => /^Last Name/.test(cell)));
      h.assert(headerAt >= 0, 'The workbook has no header row with a Last Name column');
      const at = grid[headerAt].map((cell, i) => (cell === '' ? -1 : i)).filter(i => i >= 0);
      h.assert(at.length === HEADER.length, `The workbook header row has ${at.length} labelled columns, the registry list has ${HEADER.length}`);
      const col = Object.fromEntries(HEADER.map((title, i) => [title, at[i]]));
      headers.push(HEADER.map((title, i) => [title, grid[headerAt][at[i]]]));
      const body = grid.slice(headerAt + 1).filter(row => row.some(cell => cell !== ''));
      // The report does not promise an order for rows with the same update time, so compare by code.
      const mine = body.filter(row => row[col['Last Name']] === marker).sort((a, b) => (a[col.Code] < b[col.Code] ? -1 : a[col.Code] > b[col.Code] ? 1 : 0));
      const expected = owned(status);
      h.assert(mine.length === expected.length,
        `The workbook lists ${mine.length} owned rows for ${label}, the database has ${expected.length}`);
      expected.forEach((db, i) => {
        const row = mine[i];
        const want = {
          'First Name': db[0], 'Last Name': db[1], HIN: db[2], 'Code System': db[6], Code: db[7], Sex: db[8],
          'Start Date': db[9], Status: db[10], Phone: db[12],
        };
        for (const [title, value] of Object.entries(want)) {
          h.assert(row[col[title]] === value, `${label}: workbook column "${title}" of owned row ${i + 1} differs from the database`);
        }
        updates.push(`${row[col['Update Date']]}|${db[11]}`);
        h.assert(row[col.DOB] === `${db[3]}-${db[4]}-${db[5]}`, `${label}: workbook DOB of owned row ${i + 1} is not the stored date of birth`);
      });
      h.assert(!body.some(row => row[col['First Name']] === 'OtherProviderPatient'),
        `${label}: the workbook carries a patient of another provider`);
      // "ALL" and "Deleted" list status D by design (dx-registry-status-update); Active and Resolved must not.
      if (status === 'A' || status === 'C') h.assert(!body.some(row => row[col.Code] === DELETED_CODE), `${label}: the workbook carries a deleted diagnosis`);
      h.assert(body.length === onScreen.length,
        `${label}: the workbook has ${body.length} rows, the on-screen list ${onScreen.length}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${patient},${other.id})`) === before,
        'Download Excel changed the registry');
    });
  }

  await s.step('the workbook labels every column in full and prints the stored update time as stored', async () => {
    h.assert(headers.length === 4 && updates.length > 0, 'No workbook header or update date was captured');
    const problems = [];
    headers[0].forEach(([title, found]) => {
      if (found !== title) problems.push(`column headed "${found}" should be headed "${title}"`);
    });
    const odd = updates.filter(pair => pair.split('|')[0] !== pair.split('|')[1]);
    if (odd.length) problems.push(`${odd.length} of ${updates.length} Update Date cells differ from the stored timestamp (e.g. a fractional-second suffix)`);
    h.assert(!problems.length, `The workbook is mislabelled or misformatted: ${problems.join('; ')}`);
  });
}

if (require.main === module) runWorkflow('export-content-dx-registry-xlsx', workflow, { openMaster: false });
module.exports = { workflow };
