#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// report-by-template: coverage plan §3.6 `report-by-template` (template lifecycle and output).
// User path: Schedule ▸ Administration ▸ Reports ▸ Report by Template (the admin #myFrame iframe)
//   ▸ Add Template (upload) ▸ Template Library ▸ template ▸ Run Query ▸ Export to CSV / XLSX
//   ▸ Back ▸ Edit Template ▸ Done ▸ Delete Template.
// Asserts: the uploaded template row (reportTemplates), the parameterised result table equals the
// same SELECT run in SQL for each parameter choice (literal apostrophe/comma/quote/ampersand
// rendered as text), the result page carries only a token for the CSV kept on the server (never the CSV
// itself), the CSV bytes parse back to the SQL rows, the XLSX is an OOXML workbook carrying the values, a
// stale token and a pre-upgrade page that posts its CSV get the HTTP 410 "run the report again" page (no
// download, no error page) whose button runs it again, leaving the result page fires the ViewClearSession
// beacon, the textarea edit persists,
// an edit whose SQL is a write statement is refused when saved (the stored template and the
// database are unchanged and the typed XML stays in the textarea), a write statement already stored
// in a template is refused at run time and writes nothing, and the confirm()-gated delete removes
// the row. Limits (#5849): a report over the row, cell or page-size limit returns to the configuration
// with a message naming that limit, saying which carlos.properties setting an administrator can raise, and no export; a report whose CSV is over 5 MB is still shown, with a
// note (naming report_by_template.max_csv_mb, which can now be raised) and no CSV/XLSX download; in a sequence only the large query's downloads are held back, the note
// names that query and the remaining download names its own.
// Fixtures: the owned synthetic patient (runWorkflow) plus a second owned FAKE patient with the
// same marker surname; one template titled with the marker. Cleanup deletes only marker rows and
// asserts they are gone. Group membership and upload validation live in
// report-by-template-groups-playwright-checks.js.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');
const {xlsxRows} = require('./lib/export-content-helpers');

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

function templateXml(title, description, query, params = '', sequence = false) {
  return `<report title="${title}" description="${description}" active="1"${sequence ? ' sequence="true"' : ''}>`
    + `<query>${query}</query>${params}</report>`;
}

// The English limit messages (oscarResources_en, #5849). The page's locale may group the digits with a comma,
// a period, a space, a no-break space or a narrow no-break space.
const LIMIT_TEXT = {
  rows: /This report has more than 10[,.\s\u00a0\u202f]?000 rows, the most a report may return\./,
  cell: /A value in this report is longer than 65[,.\s\u00a0\u202f]?536 characters, the most a report cell may hold\./,
  html: /This report is larger than 10 MB, the most a report page may show\./,
};
const CSV_HELD_BACK = 'too large to download as CSV or XLSX (more than 5 MB)';
// Follows a refusal while the limit is below its maximum, and names the carlos.properties setting to change.
const canRaise = (setting) => `An administrator can raise this limit with the setting ${setting} in carlos.properties.`;

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
  const templateRow = (id = templateId) => s.sql.rows(`SELECT templatetitle,templatedescription,templatesql,active,uuid
    FROM reportTemplates WHERE templateid=${Number(id)}`);

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

  async function uploadTemplate(xml, ownedTitle) {
    await frameClick(frame.getByRole('link', {name: 'Add Template', exact: true}), 'Add Template');
    await frame.locator('#uploadReportXml').setInputFiles({name: 'rbt-template.xml', mimeType: 'text/xml',
      buffer: Buffer.from(xml)});
    await frameClick(frame.locator('input[type="submit"][value^="Upload"]'), 'Upload & Add');
    await frame.locator('.alert-success', {hasText: 'Saved Successfully'}).waitFor();
    const id = s.sql.value(`SELECT GROUP_CONCAT(templateid) FROM reportTemplates WHERE templatetitle=${h.sqlString(ownedTitle)}`);
    h.assert(/^[1-9]\d*$/.test(id), 'Upload did not create exactly one owned template');
    return id;
  }
  async function openFromLibrary(ownedTitle, id) {
    await frameClick(frame.getByRole('link', {name: 'Template Library', exact: true}), 'Template Library');
    await frame.locator('#userSearch').pressSequentially(ownedTitle);
    // The search is a substring filter, so other owned templates whose titles extend this one
    // (`<marker> RBT write`) stay visible too; the owned row is the one whose link is the title.
    const visible = frame.locator('#tableData tr:visible');
    const count = await visible.count();
    h.assert(count >= 1 && count <= 3, 'Library search did not narrow to the owned templates');
    const owned = visible.getByRole('link', {name: ownedTitle, exact: true});
    h.assert(await owned.count() === 1, 'Library search did not show exactly one row for the owned template');
    await frameClick(owned, 'Report configuration');
    h.assert(new URL(frame.url()).searchParams.get('templateid') === id, 'Configuration opened another template');
    await frame.locator('h3', {hasText: ownedTitle}).waitFor();
  }
  async function deleteFromConfiguration(id, ownedTitle) {
    const posted = admin.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/oscarReport/reportByTemplate/addEditTemplatesAction'), {timeout: 20000});
    posted.catch(() => {});
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      const navigated = admin.waitForEvent('framenavigated', {predicate: f => f === frame, timeout: 20000});
      navigated.catch(() => {});
      await frame.getByRole('link', {name: 'Delete Template', exact: true}).click();
      await navigated;
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete did not ask exactly one confirmation');
    const status = (await posted).status();
    h.assert(status === 200, `Confirmed Delete Template POST answered HTTP ${status}; the form deleteTemplate() `
      + 'builds after confirm() carries no CSRF-TOKEN');
    await settle('Delete Template');
    await frame.locator('h3', {hasText: 'Template Library'}).waitFor();
    h.assert(s.sql.value(`SELECT COUNT(*) FROM reportTemplates WHERE templateid=${id}`) === '0',
      'Confirmed delete left the template row');
    h.assert(await frame.getByRole('link', {name: ownedTitle, exact: true}).count() === 0, 'Deleted template is still listed');
  }

  // resultReport.jsp's body onunload sends navigator.sendBeacon(ViewClearSession). Chromium
  // abandons a ping from an unloading frame before its response reaches the page (requestfailed
  // ERR_ABORTED, and page/context routes never see it), so the server's answer is not observable
  // here: each exit asserts the beacon was sent and consumes exactly that ping failure.
  const beacons = [];
  const isBeacon = url => new URL(url).pathname.endsWith('/oscarReport/reportByTemplate/ViewClearSession');
  admin.on('request', request => { if (isBeacon(request.url())) beacons.push(request.method()); });
  async function leaveResult(locator, label) {
    const seen = beacons.length;
    await frameClick(locator, label);
    const deadline = Date.now() + 15000;
    while (beacons.length === seen && Date.now() < deadline) await new Promise(r => setTimeout(r, 100));
    h.assert(beacons.length === seen + 1 && beacons[seen] === 'POST',
      `${label}: leaving the result page sent no ViewClearSession beacon`);
    await new Promise(r => setTimeout(r, 300));
    for (let i = s.recorder.requestFailures.length - 1; i >= 0; i--) {
      const entry = s.recorder.requestFailures[i];
      if (entry.resourceType === 'ping' && isBeacon(entry.url)) s.recorder.requestFailures.splice(i, 1);
    }
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
    templateId = await uploadTemplate(templateXml(title, `${s.marker} synthetic roster`, query, params), title);
    const [row] = templateRow();
    h.assert(row[1] === `${s.marker} synthetic roster` && row[2] === query && row[3] === '1'
      && /^[0-9a-f-]{36}$/.test(row[4]), 'Stored template does not match the uploaded XML');
  });

  await s.step('the library search narrows to the owned template and opens its configuration', async () => {
    await openFromLibrary(title, templateId);
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

  await s.step('Run Query for Female shows exactly the SQL rows for the owned patient', async () => {
    const result = await runReport('F', 'Result report (F)');
    const expected = expectedRows('F');
    h.assert(expected.length === 1 && expected[0][0] === s.patient, 'SQL fixture for the owned patient is missing');
    h.assert(result.header.join('|') === 'demographic_no|last_name|first_name|sex', 'Result header differs from the SELECT');
    h.assert(JSON.stringify(result.rows) === JSON.stringify(expected), 'Result table differs from the SQL rows');
    h.assert(await frame.locator('input[name="resultToken"]').count() === 1, 'Result page did not carry one CSV export form');
    // The downloads are built on the server: the page carries a token, never the CSV (no hidden csv field and
    // no CSV header line anywhere in the page).
    h.assert(await frame.locator('input[name="csv"]').count() === 0
      && !(await frame.content()).includes('demographic_no,last_name,first_name,sex'),
      'Result page still carries the CSV instead of a token for the result kept on the server');
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

  await s.step('Export to XLSX downloads an OOXML workbook carrying the result values', async () => {
    const file = await download('input[name="getXLS"]');
    h.assert(file.name === 'oscarReport.xlsx', 'XLSX export used an unexpected file name');
    h.assert(file.bytes.subarray(0, 4).equals(Buffer.from('504b0304', 'hex')), 'XLSX export is not a ZIP package');
    const cells = xlsxRows(file.bytes).flat();
    h.assert(cells.includes(s.marker) && cells.includes('Workflow') && cells.includes('last_name'),
      'XLSX workbook does not carry the result values');
  });

  await s.step('Back fires the ViewClearSession beacon and returns to the configuration', async () => {
    await leaveResult(frame.locator('input[type="button"][value="Back"]'), 'Back to configuration');
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

  // The 410 page for a result this session no longer holds. Its status is the expected answer here, so exactly
  // that response (and Chromium's console line for it) is consumed; any other signal stays strict.
  const RESULT_GONE = 'This report result is no longer available to download.';
  async function exportExpectingRunAgain(label) {
    const since = {responses: s.recorder.badResponses.length, console: s.recorder.consoleIssues.length};
    const isExport = url => new URL(url).pathname.endsWith('/oscarReport/reportByTemplate/generateOutFilesAction');
    const answered = admin.waitForResponse(r => r.request().method() === 'POST' && isExport(r.url()), {timeout: 20000});
    answered.catch(() => {});
    let downloads = 0;
    const onDownload = () => { downloads++; };
    admin.on('download', onDownload);
    try {
      await leaveResult(frame.locator('input[name="getCSV"]'), label);
    } finally {
      admin.off('download', onDownload);
    }
    const status = (await answered).status();
    h.assert(status === 410, `${label}: the export answered HTTP ${status}, not 410`);
    h.assert(downloads === 0, `${label}: the export still downloaded a file`);
    await frame.locator('#reportResultUnavailable', {hasText: RESULT_GONE}).waitFor();
    for (let i = s.recorder.badResponses.length - 1; i >= since.responses; i--) {
      const entry = s.recorder.badResponses[i];
      if (entry.status === 410 && isExport(entry.url)) s.recorder.badResponses.splice(i, 1);
    }
    for (let i = s.recorder.consoleIssues.length - 1; i >= since.console; i--) {
      if (/status of 410 \(/.test(s.recorder.consoleIssues[i].text)) s.recorder.consoleIssues.splice(i, 1);
    }
  }

  await s.step('a stale result token and an old page\'s posted CSV ask to run the report again, with no download', async () => {
    // A token this session does not hold (expired, replaced by newer reports, or another session's).
    await frame.locator('input[name="resultToken"]').evaluate(input => { input.value = '0'.repeat(64); });
    await exportExpectingRunAgain('Stale result token');
    await frameClick(frame.getByRole('link', {name: 'Run the report again', exact: true}), 'Run the report again');
    h.assert(new URL(frame.url()).searchParams.get('templateid') === templateId, 'Run the report again opened another template');
    await runReport('M', 'Result report (M, after a stale token)');
    // A result page opened before the downloads moved to the server posts its whole CSV and no token: the posted
    // CSV is ignored, never sent back as a file.
    await frame.locator('form:has(input[name="getCSV"])').evaluate(form => {
      for (const name of ['resultToken', 'resultIndex', 'templateid']) form.querySelector(`input[name="${name}"]`)?.remove();
      const csv = document.createElement('input');
      csv.type = 'hidden';
      csv.name = 'csv';
      csv.value = 'FAKE-posted,value\r\n';
      form.appendChild(csv);
    });
    await exportExpectingRunAgain('Pre-upgrade CSV post');
    await frameClick(frame.getByRole('link', {name: 'Run the report again', exact: true}), 'Run the report again (no template)');
    await frame.locator('h3', {hasText: 'Template Library'}).waitFor();
    await openFromLibrary(title, templateId);
    await runReport('M', 'Result report (M, after an old page)');
    const file = await download('input[name="getCSV"]');
    h.assert(JSON.stringify(parseCsv(file.bytes.toString('utf8')).slice(1)) === JSON.stringify(expectedRows('M')),
      'The rerun report did not download its own CSV');
  });

  const writeTitle = `${s.marker} RBT write`;
  let writeId;
  let write;
  await s.step('Edit Template refuses a write statement when saved and keeps the typed XML', async () => {
    await leaveResult(frame.getByRole('link', {name: 'Template Library', exact: true}), 'Template Library (write)');
    const select = 'SELECT templateid FROM reportTemplates WHERE templateid = 0';
    write = `UPDATE reportTemplates SET templatedescription = 'FAKE-PW-written' WHERE templateid = ${templateId}`;
    writeId = await uploadTemplate(templateXml(writeTitle, `${s.marker} write probe`, select), writeTitle);
    await openFromLibrary(writeTitle, writeId);
    await frameClick(frame.getByRole('link', {name: 'Edit Template', exact: true}), 'Edit Template (write)');
    const textarea = frame.locator('textarea#xmltext');
    h.assert((await textarea.inputValue()).includes(select), 'Edit page did not load the stored XML');
    // Both rows: the edited template, and the one the UPDATE statement targets.
    const before = JSON.stringify([templateRow(writeId), templateRow()]);
    await textarea.fill(templateXml(writeTitle, `${s.marker} write probe`, write));
    await frameClick(frame.locator('input[type="submit"][name="done"]'), 'Edit Done (write)');
    // Refused at save (issue #4133): the editor answers with the reason and the author's XML.
    await frame.locator('.alert-danger', {hasText: 'was refused'}).waitFor();
    h.assert((await frame.locator('textarea#xmltext').inputValue()).includes(write),
      'The refused edit did not keep the typed XML in the textarea');
    h.assert(s.sql.value(`SELECT templatesql FROM reportTemplates WHERE templateid=${writeId}`) === select,
      'A refused edit replaced the stored template SQL');
    h.assert(JSON.stringify([templateRow(writeId), templateRow()]) === before,
      'Saving the write-statement edit changed the database');
  });

  await s.step('a write statement already stored in a template is refused at run time and writes nothing', async () => {
    // Templates saved before the save-time check (or written to the table directly) never passed
    // it, so Run Query keeps its own refusal. Seed one such row and run it from the configuration.
    s.sql.execute(`UPDATE reportTemplates SET templatesql=${h.sqlString(write)} WHERE templateid=${Number(writeId)}`);
    h.assert(s.sql.value(`SELECT templatesql FROM reportTemplates WHERE templateid=${writeId}`) === write,
      'The stored write statement was not seeded');
    await openFromLibrary(writeTitle, writeId);
    const before = JSON.stringify([templateRow(writeId), templateRow()]);
    await frameClick(frame.locator('input[type="submit"][value="Run Query"]'), 'Result report (write)');
    await frame.locator('.alert-danger', {hasText: 'Only SELECT statements are allowed'}).waitFor();
    h.assert(await frame.locator('table#report2').count() === 0, 'A write statement produced a result table');
    h.assert(await frame.locator('input[name="csv"],input[name="getCSV"],input[name="getXLS"]').count() === 0,
      'A refused query offered an empty export');
    h.assert(JSON.stringify([templateRow(writeId), templateRow()]) === before,
      'Running the write-statement template changed the database');
    await leaveResult(frame.locator('input[type="button"][value="Back"]'), 'Back (write)');
  });

  await s.step('Delete Template asks for confirmation and removes the template row', async () => {
    const deleteTitle = `${s.marker} RBT delete`;
    const deleteId = await uploadTemplate(templateXml(deleteTitle, `${s.marker} delete probe`, query, params), deleteTitle);
    await openFromLibrary(deleteTitle, deleteId);
    await deleteFromConfiguration(deleteId, deleteTitle);
    h.assert(templateRow().length === 1, 'Deleting one template removed another');
  });

  await s.step('a report over the page-size limit names that limit and offers no empty export', async () => {
    const limitTitle = `${s.marker} RBT limit`;
    // About 12 MB of page: its CSV passes 5 MB first and is held back, then the 10 MB page limit refuses it.
    const select = "SELECT REPEAT('x', 60000) AS synthetic_limit FROM seq_1_to_200";
    const id = await uploadTemplate(templateXml(limitTitle, `${s.marker} limit probe`, select), limitTitle);
    await openFromLibrary(limitTitle, id);
    await frameClick(frame.locator('input[type="submit"][value="Run Query"]'), 'Result report (limit)');
    await frame.locator('.alert-danger', {hasText: LIMIT_TEXT.html}).waitFor();
    h.assert((await frame.locator('.alert-danger', {hasText: LIMIT_TEXT.html}).innerText())
      .includes(canRaise('report_by_template.max_html_mb')), 'The page-size refusal did not name the setting that raises it');
    h.assert(await frame.locator('input[name="getCSV"],input[name="getXLS"]').count() === 0,
      'An oversized result offered an empty export');
    h.assert(await frame.locator('table#report2').count() === 0, 'An oversized result rendered partial rows');
    await frame.locator('input[type="submit"][value="Run Query"]').waitFor();
    await deleteFromConfiguration(id, limitTitle);
  });

  // Last: saving XML that carries <param> through the front door was refused by the WAF before
  // issue #4133 (rule 1402 now excludes xmltext), so everything else is proven above it.
  await s.step('Edit Template saves the parameterised textarea XML and Done returns to the configuration', async () => {
    await openFromLibrary(title, templateId);
    await frameClick(frame.getByRole('link', {name: 'Edit Template', exact: true}), 'Edit Template');
    const textarea = frame.locator('textarea#xmltext');
    h.assert((await textarea.inputValue()).includes(`title="${title}"`), 'Edit page did not load the stored XML');
    await textarea.fill(templateXml(title, `${s.marker} edited roster`, query, params));
    await frameClick(frame.locator('input[type="submit"][name="done"]'), 'Edit Done');
    await frame.locator('h3 small', {hasText: `${s.marker} edited roster`}).waitFor();
    h.assert(await frame.locator('select#sexpick').count() === 1, 'Edited template lost its parameters');
    const [row] = templateRow();
    h.assert(row[0] === title && row[1] === `${s.marker} edited roster` && row[2] === query, 'Edited template was not persisted');
  });

  await s.step('CSV download guards formula headers and text while preserving negative amounts and CSV syntax', async () => {
    const guardedTitle = `${s.marker} RBT spreadsheet`;
    // MariaDB trims leading spaces from column aliases; exercise those spaces in data below.
    const headers = ['=header', '+header', '-header', '@header', '=spacedHeader',
      '＝header', '＋header', '－header', '＠header', 'control', 'quoted', 'multiline', 'amount', 'scientific', 'spacedAmount'];
    const values = ['=1+1', '+1+1', '-cmd', '@SUM(A1)', '  =1+1', '＝1+1', '＋1+1', '－cmd', '＠SUM(A1)', '\t\r\n=1+1',
      '=HYPERLINK("synthetic","quoted,\r\nvalue")', 'Zoë,"text"\r\nnext', '-12.50', '-1.2E-7', '  -12.50'];
    // Hex literals preserve the synthetic control characters through XML and SQL parsing.
    const select = 'SELECT ' + values.map((value, i) =>
      `CONVERT(0x${Buffer.from(value).toString('hex')} USING utf8mb4) AS \`${headers[i]}\``).join(', ');
    const id = await uploadTemplate(templateXml(guardedTitle, `${s.marker} spreadsheet probe`, select), guardedTitle);
    await openFromLibrary(guardedTitle, id);
    await frameClick(frame.locator('input[type="submit"][value="Run Query"]'), 'Spreadsheet result');
    await frame.locator('table#report2').waitFor();
    h.assert(await frame.locator('table#report2 tbody tr').first().locator('td').first().innerText() === values[0],
      'The CSV guard changed the visible HTML result');
    const file = await download('input[name="getCSV"]');
    const expected = [headers.map((v, i) => i < 9 ? `\t${v}` : v), values.map((v, i) => i < 11 ? `\t${v}` : v)];
    h.assert(file.bytes.toString('utf8').startsWith('"\t=header"'), 'Formula guard tab was not CSV quoted');
    assert.deepEqual(parseCsv(file.bytes.toString('utf8')), expected,
      'Downloaded spreadsheet CSV lost its guards, numeric text, quotes, line breaks or Unicode');
    await leaveResult(frame.locator('input[type="button"][value="Back"]'), 'Back (spreadsheet)');
    await deleteFromConfiguration(id, guardedTitle);
  });

  for (const [budget, sql, text, setting] of [
    ['row', 'SELECT seq FROM seq_1_to_10001', LIMIT_TEXT.rows, 'report_by_template.max_rows'],
    ['cell', "SELECT REPEAT('x', 65537) AS sample", LIMIT_TEXT.cell, 'report_by_template.max_cell_chars'],
  ]) {
    await s.step(`${budget} limit refuses oversized output with a message that names the limit`, async () => {
      const budgetTitle = `${s.marker} RBT ${budget} budget`;
      const id = await uploadTemplate(templateXml(budgetTitle, `${s.marker} bounded report probe`, sql), budgetTitle);
      await openFromLibrary(budgetTitle, id);
      await frameClick(frame.locator('input[type="submit"][value="Run Query"]'), `${budget} limit refusal`);
      await frame.locator('.alert-danger', {hasText: text}).waitFor();
      h.assert((await frame.locator('.alert-danger', {hasText: text}).innerText()).includes(canRaise(setting)),
        `The ${budget} refusal did not name the setting that raises it`);
      h.assert(await frame.locator('table#report2').count() === 0, 'An oversized report exposed partial result rows');
      h.assert(await frame.locator('input[name="csv"], input[name="getCSV"], input[name="getXLS"]').count() === 0,
        'An oversized report exposed a CSV download');
      await deleteFromConfiguration(id, budgetTitle);
    });
  }

  // About 5.06 MB of CSV: one query's downloads are over the 5 MB limit, while its page (10 MB limit) is not.
  const largeCsv = "SELECT REPEAT('é', 32768) AS sample FROM seq_1_to_81";

  await s.step('a report whose CSV is over 5 MB is shown with a note and without its downloads', async () => {
    const csvTitle = `${s.marker} RBT CSV byte budget`;
    const id = await uploadTemplate(templateXml(csvTitle, `${s.marker} bounded report probe`, largeCsv), csvTitle);
    await openFromLibrary(csvTitle, id);
    await frameClick(frame.locator('input[type="submit"][value="Run Query"]'), 'CSV limit result');
    await frame.locator('table#report2').waitFor();
    // DataTables may page the rows, so check the first value rather than counting rows.
    h.assert((await frame.locator('table#report2 tbody tr').first().locator('td').first().innerText()) === 'é'.repeat(32768),
      'The report with a held-back CSV did not show its values');
    const note = frame.locator('.alert-info[role="status"]');
    h.assert(await note.count() === 1 && (await note.innerText()).includes(CSV_HELD_BACK),
      'The report did not say its CSV and XLSX downloads are held back');
    // The downloads are built on the server, so the 5 MB default is below the setting's maximum and can be raised.
    h.assert((await note.innerText()).includes(canRaise('report_by_template.max_csv_mb')),
      'The held-back note did not name the setting that raises the CSV limit');
    h.assert(await frame.locator('.alert-danger, .alert-warning').count() === 0, 'A held-back CSV was shown as an error');
    h.assert(await frame.locator('input[name="csv"], input[name="getCSV"], input[name="getXLS"]').count() === 0,
      'A report over the CSV limit still offered a download');
    await leaveResult(frame.locator('input[type="button"][value="Back"]'), 'Back (CSV limit)');
    await deleteFromConfiguration(id, csvTitle);
  });

  await s.step('in a sequence only the large query is held back, and the note and remaining download name their query', async () => {
    const sequenceTitle = `${s.marker} RBT CSV sequence`;
    const xml = templateXml(sequenceTitle, `${s.marker} sequence probe`, `${largeCsv}; SELECT 'small' AS sample`, '', true);
    const id = await uploadTemplate(xml, sequenceTitle);
    await openFromLibrary(sequenceTitle, id);
    await frameClick(frame.locator('input[type="submit"][value="Run Query"]'), 'CSV limit sequence result');
    await frame.locator('table#report2').nth(1).waitFor();
    h.assert(await frame.locator('table#report2').count() === 2, 'The sequence did not show both results');
    const note = frame.locator('.alert-info[role="status"]');
    h.assert(await note.count() === 1, 'The sequence did not show exactly one held-back note');
    const noteText = await note.innerText();
    h.assert(noteText.startsWith('Query 1:') && noteText.includes(CSV_HELD_BACK),
      'The held-back note did not name the first query');
    const exports = frame.locator('form:has(input[name="getCSV"])');
    h.assert(await exports.count() === 1 && (await exports.innerText()).includes('Query 2'),
      'The sequence did not offer exactly the second query\'s download, named as such');
    const file = await download('input[name="getCSV"]');
    assert.deepEqual(parseCsv(file.bytes.toString('utf8')), [['sample'], ['small']],
      'The remaining download is not the second query\'s CSV');
    await leaveResult(frame.locator('input[type="button"][value="Back"]'), 'Back (CSV limit sequence)');
    await deleteFromConfiguration(id, sequenceTitle);
  });
}

if (require.main === module) runWorkflow('report-by-template', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
