#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Report by Template: result table, CSV and XLS for a query whose columns are NOT plain table columns.
 * User path: Schedule ▸ Administration ▸ Reports ▸ Report by Template (the admin #myFrame iframe) ▸ Add Template
 * (upload) ▸ Template Library ▸ template ▸ Run Query ▸ Export to CSV / Export to XLS.
 * report-by-template proves the happy path with four plain columns. Real templates alias columns
 * (SELECT COUNT(*) AS visits), join two tables that share a column name, and return NULLs, dates, decimals and
 * multi-line text, so this is what a clinic's report actually has to survive.
 *
 * Asserts, against the same SELECT run in SQL: the result table's header is the aliases the SELECT chose; two
 * columns with the same name (provider last_name, patient last_name) show their own values; NULL, DATE, DATETIME,
 * DECIMAL, multi-line, accented and quoted values read as SQL returns them; the CSV parses to the same header and
 * row; the XLS workbook, decoded cell by cell, holds the same values at the same column indexes. The checks are collected in order, so the first
 * wrong construct fails the step that names it.
 * Fixtures: the owned FAKE-PW patient (names set to accented text) and one marker-titled template; cleanup deletes
 * only the marker template and asserts it gone. Nothing else is written.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const x = require('./lib/export-content-helpers');

const ERROR_PAGE = /CARLOS has encountered an unexpected error|HTTP Status \d{3}|Exception Report/i;
const q = h.sqlString;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const title = `${marker} RBT columns`;
  const scratch = x.scratchDir();
  s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));
  s.cleanup(() => {
    sql.execute(`DELETE FROM reportTemplates WHERE templatetitle LIKE ${q(`${marker}%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM reportTemplates WHERE templatetitle LIKE ${q(`${marker}%`)}`) === '0',
      'Owned report templates were not removed');
  });
  const first = 'Zoë "Zed", O\'Neil-Ünal';
  sql.execute(`UPDATE demographic SET first_name=${q(first)}, year_of_birth='1962', month_of_birth='07', date_of_birth='09',
    address=${q('12 Rue de l’Église\nUnit 4')} WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const select = `SELECT d.demographic_no AS patient_id, d.last_name AS surname, p.last_name, d.last_name, d.first_name AS given,
    d.address AS address, CAST('1234.50' AS DECIMAL(10,2)) AS fee, NULL AS missing, DATE('2024-05-06') AS seen_on,
    CAST('2024-05-06 07:08:09' AS DATETIME) AS seen_at, '00123' AS chart_no, '12345678901234567' AS reference_no, COUNT(*) AS visits
    FROM demographic d JOIN provider p ON p.provider_no = d.provider_no WHERE d.demographic_no = ${patient}
    GROUP BY d.demographic_no, p.last_name, d.last_name, d.first_name, d.address`.replace(/\s+/g, ' ');
  const xmlSelect = select.replace(/&/g, '&amp;').replace(/</g, '&lt;');
  const xml = `<report title="${title}" description="${marker} aliases and joins" active="1"><query>${xmlSelect}</query></report>`;
  // What MariaDB itself returns for the same SELECT: the labels and the values as text.
  const header = ['patient_id', 'surname', 'last_name', 'last_name', 'given', 'address', 'fee', 'missing', 'seen_on', 'seen_at', 'chart_no', 'reference_no', 'visits'];
  const expectedRow = () => {
    return sql.rows(select)[0].map(cell => (cell === null || cell === 'NULL' ? '' : cell));
  };

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'rbt-columns-administration', timeout: 20000 });
  let frame;
  async function settle(label) {
    await frame.waitForLoadState('domcontentloaded');
    await frame.waitForLoadState('networkidle').catch(() => {});
    const text = await frame.locator('body').innerText().catch(() => '');
    h.assert(text.trim() && !ERROR_PAGE.test(text), `${label} rendered an error or blank page`);
  }
  async function frameClick(locator, label) {
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 20000 });
    navigated.catch(() => {});
    await locator.click();
    await navigated;
    await settle(label);
  }

  let templateId;
  await s.step('the owned template (aliases, a name collision, NULL, date, decimal, multi-line text) uploads and opens', async () => {
    const link = admin.getByRole('link', { name: 'Report by Template', exact: true, includeHidden: true });
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor();
    frame = await (await iframe.elementHandle()).contentFrame();
    await frame.waitForURL(/\/oscarReport\/reportByTemplate\/ViewHomePage/);
    await settle('Template Library');
    await frameClick(frame.getByRole('link', { name: 'Add Template', exact: true }), 'Add Template');
    await frame.locator('#uploadReportXml').setInputFiles({ name: 'rbt-columns.xml', mimeType: 'text/xml', buffer: Buffer.from(xml) });
    await frameClick(frame.locator('input[type="submit"][value^="Upload"]'), 'Upload & Add');
    await frame.locator('.alert-success', { hasText: 'Saved Successfully' }).waitFor();
    templateId = sql.value(`SELECT GROUP_CONCAT(templateid) FROM reportTemplates WHERE templatetitle=${q(title)}`);
    h.assert(/^[1-9]\d*$/.test(templateId), 'Upload did not create exactly one owned template');
    await frameClick(frame.getByRole('link', { name: 'Template Library', exact: true }), 'Template Library');
    await frame.locator('#userSearch').pressSequentially(title);
    await frameClick(frame.locator('#tableData tr:visible').getByRole('link', { name: title, exact: true }), 'Report configuration');
  });

  let table;
  await s.step('Run Query shows one row with the SQL values and the SELECT\'s aliases as column headings', async () => {
    await frameClick(frame.locator('input[type="submit"][value="Run Query"]'), 'Result report');
    await frame.locator('table#report2').waitFor();
    table = {
      header: (await frame.locator('table#report2 thead th').allInnerTexts()).map(t => t.trim()),
      rows: await frame.locator('table#report2 tbody tr').evaluateAll(trs => trs.map(tr => [...tr.querySelectorAll('td')].map(td => td.textContent))),
    };
    h.assert(table.rows.length === 1, `The result table has ${table.rows.length} rows, SQL returns 1`);
    h.assert(table.header.length === header.length, `The result table has ${table.header.length} columns, the SELECT names ${header.length}`);
  });

  const duplicated = i => header.indexOf(header[i]) !== header.lastIndexOf(header[i]);
  await s.step('the result table values equal the SQL values', async () => {
    const want = expectedRow();
    const cells = table.rows[0].map(c => c.trim());
    const wrong = [];
    want.forEach((value, i) => { if (!duplicated(i) && cells[i] !== value.trim()) wrong.push(`column ${i + 1} (${header[i]})`); });
    h.assert(!wrong.length, `The result table differs from the SQL row in ${wrong.join(', ')}`);
  });

  let csv;
  await s.step('Export to CSV parses to the same header and row as SQL', async () => {
    const file = await x.saveDownload(admin, scratch, () => frame.locator('input[name="getCSV"]').click());
    csv = x.parseCsv(file.bytes.toString('utf8')).filter(r => r.some(c => c !== ''));
    h.assert(csv.length === 2, `The CSV has ${csv.length} lines, expected a header and one row`);
    const want = expectedRow();
    const wrong = [];
    want.forEach((value, i) => { if (csv[1][i].replace(/\r\n/g, '\n') !== value.trim()) wrong.push(`column ${i + 1} (${header[i]}: CSV ${JSON.stringify(csv[1][i])} vs SQL ${JSON.stringify(value.trim())})`); });
    h.assert(!wrong.length, `The CSV row differs from the SQL row in ${wrong.join(', ')}`);
  });

  let xls;
  await s.step('the XLS workbook holds the same values in the same order', async () => {
    const file = await x.saveDownload(admin, scratch, () => frame.locator('input[name="getXLS"]').click());
    // Decode the .xls sheet (BIFF8) and compare each expected cell by its column index.
    const sheet = x.xlsCells(file.bytes);
    h.assert(sheet.length === 2, `The XLS sheet has ${sheet.length} rows, expected a header and one row`);
    xls = { header: sheet[0], row: sheet[1] };
    const want = expectedRow();
    h.assert(xls.row.length === want.length, `The XLS row has ${xls.row.length} cells, the SELECT returns ${want.length}`);
    const wrong = [];
    want.forEach((value, i) => {
      const cell = xls.row[i];
      // A numeric cell is the same value when it reads as that number; whether text was WRONGLY numeric is the last step.
      const same = cell.type === 'number' ? value.trim() !== '' && Number(value) === cell.value : cell.value.replace(/\r\n/g, '\n') === value.trim();
      if (!same) wrong.push(`column ${i + 1} (${header[i]})`);
    });
    h.assert(!wrong.length, `The XLS row differs from the SQL row in ${wrong.join(', ')}`);
  });

  await s.step('the table shows each of the two columns named last_name with its own value, the headings are the aliases, and the XLS keeps text cells as text', async () => {
    const problems = [];
    const want = expectedRow();
    const cells = table.rows[0].map(c => c.trim());
    const same = header.map((_, i) => i).filter(duplicated).filter(i => cells[i] !== want[i].trim());
    if (same.length) problems.push(`the result table shows the wrong value for ${same.length} of the two columns named last_name (it reads cells by column NAME, so the second shows the first's value)`);
    if (JSON.stringify(table.header) !== JSON.stringify(header)) {
      problems.push(`the result table is headed ${JSON.stringify(table.header)} instead of ${JSON.stringify(header)}`);
    }
    if (JSON.stringify(csv[0]) !== JSON.stringify(header)) {
      problems.push(`the CSV is headed ${JSON.stringify(csv[0])} instead of ${JSON.stringify(header)}`);
    }
    const sheetHeader = xls.header.map(cell => String(cell.value));
    if (JSON.stringify(sheetHeader) !== JSON.stringify(header)) {
      problems.push(`the XLS is headed ${JSON.stringify(sheetHeader)} instead of ${JSON.stringify(header)}`);
    }
    for (const [name, value] of [['chart_no', '00123'], ['reference_no', '12345678901234567']]) {
      const cell = xls.row[header.indexOf(name)];
      if (cell.type !== 'text' || cell.value !== value) problems.push(`the XLS stores ${name} ${value} as a number, not as the text the query returned (GenerateOutFiles2Action parses every cell with Double.parseDouble: leading zeros and digits beyond 15 are lost)`);
    }
    h.assert(!problems.length, `Report by Template exports wrong content: ${problems.join('; ')}`);
  });
}

if (require.main === module) runWorkflow('export-content-rbt-columns', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
