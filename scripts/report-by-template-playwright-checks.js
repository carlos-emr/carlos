#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// report-by-template: coverage plan §3.6 `report-by-template` (template lifecycle and output).
// User path: Schedule ▸ Administration ▸ Reports ▸ Report by Template (the admin #myFrame iframe)
//   ▸ Add Template (upload) ▸ Template Library ▸ template ▸ Run Query ▸ Export to CSV / XLS
//   ▸ Back ▸ Edit Template ▸ Done ▸ Delete Template.
// Asserts: the uploaded template row (reportTemplates), the parameterised result table equals the
// same SELECT run in SQL for each parameter choice (literal apostrophe/comma/quote/ampersand
// rendered as text), the CSV bytes parse back to the SQL rows, the XLS is an OLE2 workbook carrying
// the values, leaving the result page fires the ViewClearSession beacon, the textarea edit persists,
// a template whose SQL is a write statement is refused at run time and writes nothing, and the
// confirm()-gated delete removes the row.
// Fixtures: the owned synthetic patient (runWorkflow) plus a second owned FAKE patient with the
// same marker surname; one template titled with the marker. Cleanup deletes only marker rows and
// asserts they are gone. Group membership and upload validation live in
// report-by-template-groups-playwright-checks.js.
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const ERROR_PAGE = /CARLOS has encountered an unexpected error|HTTP Status \d{3}|Exception Report/i;

// Minimal RFC 4180 reader: enough to compare Commons CSV DEFAULT output with SQL rows.
function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; } else if (c === '"') quoted = false;
      else field += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { row.push(field); field = ''; } else if (c === '\r' || c === '\n') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field); rows.push(row); row = []; field = '';
    } else field += c;
  }
  if (field || row.length) { row.push(field); rows.push(row); }
  return rows;
}

function templateXml(title, description, query, params = '') {
  return `<report title="${title}" description="${description}" active="1">`
    + `<query>${query}</query>${params}</report>`;
}

async function workflow(s) {
  const title = `${s.marker} RBT`;
  const second = {first: 'O\'Neil, "A&B"', id: null};
  s.cleanup(() => {
    const like = h.sqlString(`${s.marker}%`);
    s.sql.execute(`DELETE FROM reportTemplates WHERE templatetitle LIKE ${like}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM reportTemplates WHERE templatetitle LIKE ${like}`) === '0',
      'Owned report templates were not removed');
    if (second.id) {
      s.sql.execute(`DELETE FROM demographic WHERE demographic_no=${second.id} AND last_name=${h.sqlString(s.marker)}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${second.id}`) === '0',
        'The second owned patient was not removed');
    }
  });
  second.id = s.sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,
    sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(s.marker)},${h.sqlString(second.first)},'1975','03','04','M','AC',${h.sqlString(s.provider)},
    'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(second.id), 'The second synthetic patient was not created');

  const query = 'SELECT demographic_no, last_name, first_name, sex FROM demographic'
    + ' WHERE last_name = \'{who}\' AND sex = \'{sexpick}\' ORDER BY demographic_no';
  const params = '<param id="who" type="text" description="Surname"></param>'
    + '<param id="sexpick" type="list" description="Sex"><choice id="F">Female</choice><choice id="M">Male</choice></param>';
  const expectedRows = sex => s.sql.rows(`SELECT demographic_no, last_name, first_name, sex FROM demographic
    WHERE last_name=${h.sqlString(s.marker)} AND sex=${h.sqlString(sex)} ORDER BY demographic_no`);
  let templateId;
  const templateRow = () => s.sql.rows(`SELECT templatetitle,templatedescription,templatesql,active,uuid
    FROM reportTemplates WHERE templateid=${Number(templateId)}`);

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'rbt-administration', timeout: 20000});
  let frame;
  async function settle(label) {
    await frame.waitForLoadState('domcontentloaded');
    await frame.waitForLoadState('networkidle').catch(() => {});
    const text = await frame.locator('body').innerText().catch(() => '');
    h.assert(text.trim() && !ERROR_PAGE.test(text), `${label} rendered an error or blank page`);
  }
  // Every in-frame action is a real click; the wait is armed before it.
  async function frameClick(locator, label) {
    const navigated = admin.waitForEvent('framenavigated', {predicate: f => f === frame, timeout: 20000});
    navigated.catch(() => {});
    await locator.click();
    await navigated;
    await settle(label);
  }

  await s.step('Administration ▸ Reports ▸ Report by Template opens the Template Library', async () => {
    const link = admin.getByRole('link', {name: 'Report by Template', exact: true, includeHidden: true});
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor();
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Report by Template frame did not load');
    await frame.waitForURL(/\/oscarReport\/reportByTemplate\/ViewHomePage/);
    await settle('Template Library');
    await frame.locator('h3', {hasText: 'Template Library'}).waitFor();
    await frame.locator('#rbtTable').waitFor();
  });

  await s.step('Add Template uploads the owned template XML and stores its SQL', async () => {
    await frameClick(frame.getByRole('link', {name: 'Add Template', exact: true}), 'Add Template');
    await frame.locator('#uploadReportXml').setInputFiles({name: 'rbt-template.xml', mimeType: 'text/xml',
      buffer: Buffer.from(templateXml(title, `${s.marker} synthetic roster`, query, params))});
    await frameClick(frame.locator('input[type="submit"][value^="Upload"]'), 'Upload & Add');
    await frame.locator('.alert-success', {hasText: 'Saved Successfully'}).waitFor();
    templateId = s.sql.value(`SELECT templateid FROM reportTemplates WHERE templatetitle=${h.sqlString(title)}`);
    h.assert(/^[1-9]\d*$/.test(templateId), 'Upload did not create exactly one owned template');
    const [row] = templateRow();
    h.assert(row[1] === `${s.marker} synthetic roster` && row[2] === query && row[3] === '1'
      && /^[0-9a-f-]{36}$/.test(row[4]), 'Stored template does not match the uploaded XML');
  });

  await s.step('the library search narrows to the owned template and opens its configuration', async () => {
    await frameClick(frame.getByRole('link', {name: 'Template Library', exact: true}), 'Template Library');
    await frame.locator('#userSearch').pressSequentially(s.marker);
    const visible = frame.locator('#tableData tr:visible');
    h.assert(await visible.count() === 1, 'Library search did not narrow to the one owned template');
    await frameClick(visible.getByRole('link', {name: title, exact: true}), 'Report configuration');
    h.assert(new URL(frame.url()).searchParams.get('templateid') === templateId, 'Configuration opened another template');
    await frame.locator('h3', {hasText: title}).waitFor();
    h.assert(await frame.locator('select#sexpick option').allInnerTexts().then(t => t.map(x => x.trim()).join('|'))
      === 'Female|Male', 'List parameter choices did not render from the XML');
  });

  async function runReport(sex, label) {
    await frame.locator('input#who').fill(s.marker);
    await frame.locator('select#sexpick').selectOption(sex);
    await frameClick(frame.locator('input[type="submit"][value="Run Query"]'), label);
    await frame.locator('table#report2').waitFor();
    const header = (await frame.locator('table#report2 thead th').allInnerTexts()).map(t => t.trim());
    const rows = [];
    for (const tr of await frame.locator('table#report2 tbody tr').all()) {
      rows.push((await tr.locator('td').allInnerTexts()).map(t => t.trim()));
    }
    return {header, rows};
  }

  let csvFields;
  await s.step('Run Query for Female shows exactly the SQL rows for the owned patient', async () => {
    const result = await runReport('F', 'Result report (F)');
    const expected = expectedRows('F');
    h.assert(expected.length === 1 && expected[0][0] === s.patient, 'SQL fixture for the owned patient is missing');
    h.assert(result.header.join('|') === 'demographic_no|last_name|first_name|sex', 'Result header differs from the SELECT');
    h.assert(JSON.stringify(result.rows) === JSON.stringify(expected), 'Result table differs from the SQL rows');
    csvFields = await frame.locator('input[name="csv"]').count();
    h.assert(csvFields === 1, 'Result page did not carry one CSV export form');
  });

  async function download(button) {
    const [file] = await Promise.all([admin.waitForEvent('download', {timeout: 20000}), frame.locator(button).click()]);
    const failure = await file.failure();
    h.assert(!failure, `Export download failed: ${failure}`);
    return {name: file.suggestedFilename(), bytes: fs.readFileSync(await file.path())};
  }

  await s.step('Export to CSV downloads the header and rows the SQL returns', async () => {
    const file = await download('input[name="getCSV"]');
    h.assert(file.name === 'oscarReport.csv', 'CSV export used an unexpected file name');
    const parsed = parseCsv(file.bytes.toString('utf8'));
    const expected = [['demographic_no', 'last_name', 'first_name', 'sex'], ...expectedRows('F')];
    h.assert(JSON.stringify(parsed) === JSON.stringify(expected), 'CSV export differs from the SQL rows');
  });

  await s.step('Export to XLS downloads an OLE2 workbook carrying the result values', async () => {
    const file = await download('input[name="getXLS"]');
    h.assert(file.name === 'oscarReport.xls', 'XLS export used an unexpected file name');
    h.assert(file.bytes.subarray(0, 8).equals(Buffer.from('d0cf11e0a1b11ae1', 'hex')), 'XLS export is not an OLE2 workbook');
    const text = file.bytes.toString('latin1');
    h.assert(text.includes(s.marker) && text.includes('Workflow') && text.includes('last_name'),
      'XLS workbook does not carry the result values');
  });

  await s.step('Back fires the ViewClearSession beacon and returns to the configuration', async () => {
    const beacon = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/oscarReport/reportByTemplate/ViewClearSession'),
      {timeout: 20000});
    await frameClick(frame.locator('input[type="button"][value="Back"]'), 'Back to configuration');
    const response = await beacon;
    h.assert(response.status() === 200, `ViewClearSession beacon answered HTTP ${response.status()}`);
    await frame.locator('input[type="submit"][value="Run Query"]').waitFor();
  });

  await s.step('Run Query for Male shows the punctuation-bearing name literally, as SQL returns it', async () => {
    const result = await runReport('M', 'Result report (M)');
    const expected = expectedRows('M');
    h.assert(expected.length === 1 && expected[0][0] === second.id && expected[0][2] === second.first,
      'SQL fixture for the second patient is missing');
    h.assert(JSON.stringify(result.rows) === JSON.stringify(expected), 'Result table differs from the SQL rows');
    const file = await download('input[name="getCSV"]');
    h.assert(JSON.stringify(parseCsv(file.bytes.toString('utf8')).slice(1)) === JSON.stringify(expected),
      'CSV export did not quote the comma/quote-bearing value back to the SQL row');
  });

  await s.step('Edit Template saves the textarea XML and Done returns to the configuration', async () => {
    await frameClick(frame.locator('a.edit', {hasText: 'Edit Template'}), 'Edit Template');
    const textarea = frame.locator('textarea#xmltext');
    h.assert((await textarea.inputValue()).includes(`title="${title}"`), 'Edit page did not load the stored XML');
    await textarea.fill(templateXml(title, `${s.marker} edited roster`, query, params));
    await frameClick(frame.locator('input[type="submit"][name="done"]'), 'Edit Done');
    await frame.locator('h3 small', {hasText: `${s.marker} edited roster`}).waitFor();
    const [row] = templateRow();
    h.assert(row[0] === title && row[1] === `${s.marker} edited roster` && row[2] === query, 'Edited template was not persisted');
  });

  await s.step('a template whose SQL is a write statement is refused at run time and writes nothing', async () => {
    const write = `UPDATE reportTemplates SET templatedescription = 'FAKE-PW-written' WHERE templateid = ${templateId}`;
    await frameClick(frame.getByRole('link', {name: 'Edit Template', exact: true}), 'Edit Template (write)');
    await frame.locator('textarea#xmltext').fill(templateXml(title, `${s.marker} edited roster`, write));
    await frameClick(frame.locator('input[type="submit"][name="done"]'), 'Edit Done (write)');
    h.assert(s.sql.value(`SELECT templatesql FROM reportTemplates WHERE templateid=${templateId}`) === write,
      'The write-statement template was not stored for the run-time probe');
    const before = JSON.stringify(templateRow());
    await frameClick(frame.locator('input[type="submit"][value="Run Query"]'), 'Result report (write)');
    await frame.locator('.alert-danger', {hasText: 'Only SELECT statements are allowed'}).waitFor();
    h.assert(await frame.locator('table#report2').count() === 0, 'A write statement produced a result table');
    h.assert(JSON.stringify(templateRow()) === before, 'Running the write-statement template changed the database');
  });

  await s.step('Delete Template asks for confirmation and removes the template row', async () => {
    await frameClick(frame.locator('input[type="button"][value="Back"]'), 'Back (write)');
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      await frameClick(frame.getByRole('link', {name: 'Delete Template', exact: true}), 'Delete Template');
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete did not ask exactly one confirmation');
    await frame.locator('h3', {hasText: 'Template Library'}).waitFor();
    h.assert(s.sql.value(`SELECT COUNT(*) FROM reportTemplates WHERE templateid=${templateId}`) === '0',
      'Confirmed delete left the template row');
    h.assert(await frame.getByRole('link', {name: title, exact: true}).count() === 0, 'Deleted template is still listed');
  });
}

if (require.main === module) runWorkflow('report-by-template', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
