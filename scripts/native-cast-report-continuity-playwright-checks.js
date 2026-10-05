#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Native-query value typing (Hibernate 7): Report by Template, "continuity" (Depression Continuity) report.
 * User path: Schedule > Administration > Reports > Report by Template (admin #myFrame iframe) > Add Template
 *   (upload a <type>continuity</type> template) > Template Library > template > fill the four dates and the
 *   Dx code list > Run Query.
 * Why: DepressionContinuityReporter.addAppt() reads OscarAppointmentDaoImpl.findAppointmentsByDemographicIds,
 *   a native query, and casts column 0 (a.appointment_date, a SQL DATE) to java.util.Date. Hibernate 7 returns a
 *   native DATE as java.time.LocalDate, the cast throws ClassCastException, and generateReport() swallows it
 *   (catch (Exception e) { log }), so the page shows a header with NO patient rows and no error.
 * Asserts: the upload stores the template with type "continuity"; Run Query renders the owned patient's Dx-code
 *   row (billing_on_item.dx / service_date) and, on the same report, the owned patient's visit row (the
 *   appointment date, the provider-seen name and the billing code SQL stores). The diagnosis and visit
 *   windows are deliberately different to verify that appointments use the requested visit dates.
 * Fixtures: the owned FAKE- patient (runWorkflow), one appointment, one OHIP claim header and one item (dx 311) tied
 *   to that appointment, and one template titled with the marker. Cleanup deletes only those rows and asserts
 *   they are gone.
 * Coverage plan: risk sweep "native-cast" (Hibernate 7 native result typing), Report by Template.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const ERROR_PAGE = /CARLOS has encountered an unexpected error|HTTP Status \d{3}|Exception Report/i;
const DX = '311';
const SERVICE_CODE = 'A007A';
const VISIT_DATE = '2026-03-04';
const DIAGNOSIS_DATE = '2026-01-03';

function templateXml(title) {
  const date = (id, text) => `<param id="${id}" type="date" description="${text}"></param>`;
  return `<report title="${title}" description="${title} continuity" active="1"><type>continuity</type><query>SELECT 1</query>`
    + date('diag_date_from', 'Dx from') + date('diag_date_to', 'Dx to')
    + date('visit_date_from', 'Visit from') + date('visit_date_to', 'Visit to')
    // A childless <param> is stored self-closed and UtilXML.escapeXML() then reads it as text unless some
    // <param> in the template keeps a closing tag, so the Dx list carries one (unused) choice.
    + '<param id="dxCodes" type="textlist" description="Dx codes"><choice id="311">311</choice></param></report>';
}

async function workflow(s) {
  const { sql, marker } = s;
  const title = `${marker} CONT`;
  const medication = `${marker}<img src=x onerror=window.cx=1>`;
  const owned = { appointment: null, header: null, item: null, drug: null };
  s.cleanup(() => {
    if (owned.drug) sql.execute(`DELETE FROM drugs WHERE drugid=${owned.drug} AND demographic_no=${s.patient}`);
    sql.execute(`DELETE FROM reportTemplates WHERE templatetitle=${h.sqlString(title)}`);
    if (owned.item) sql.execute(`DELETE FROM billing_on_item WHERE id=${owned.item} AND ch1_id=${owned.header}`);
    if (owned.header) sql.execute(`DELETE FROM billing_on_cheader1 WHERE id=${owned.header} AND demographic_name=${h.sqlString(`${marker},Workflow`)}`);
    if (owned.appointment) sql.execute(`DELETE FROM appointment WHERE appointment_no=${owned.appointment} AND name=${h.sqlString(`${marker},Workflow`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE drugid=${owned.drug || 0})
      + (SELECT COUNT(*) FROM reportTemplates WHERE templatetitle=${h.sqlString(title)})
      + (SELECT COUNT(*) FROM billing_on_item WHERE id=${owned.item || 0})
      + (SELECT COUNT(*) FROM billing_on_cheader1 WHERE id=${owned.header || 0})
      + (SELECT COUNT(*) FROM appointment WHERE appointment_no=${owned.appointment || 0})`) === '0', 'Owned fixture rows remain');
  });
  owned.appointment = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,
      program_id,reason,status,createdatetime,updatedatetime,creator,lastupdateuser)
    VALUES (${h.sqlString(s.provider)},${h.sqlString(VISIT_DATE)},'09:00:00','09:15:00',${h.sqlString(`${marker},Workflow`)},${s.patient},
      0,${h.sqlString(marker)},'t',NOW(),NOW(),'playwright',${h.sqlString(s.provider)}); SELECT LAST_INSERT_ID()`);
  owned.header = sql.value(`INSERT INTO billing_on_cheader1 (header_id,demographic_no,demographic_name,provider_no,appointment_no,
      billing_date,billing_time,status,total,paid,apptProvider_no,creator,clinic)
    VALUES (1,${s.patient},${h.sqlString(`${marker},Workflow`)},${h.sqlString(s.provider)},${owned.appointment},
      ${h.sqlString(VISIT_DATE)},'09:00:00','O',0,0,${h.sqlString(s.provider)},'playwright','NATIVECAST'); SELECT LAST_INSERT_ID()`);
  owned.item = sql.value(`INSERT INTO billing_on_item (ch1_id,service_code,fee,ser_num,service_date,dx,status)
    VALUES (${owned.header},${h.sqlString(SERVICE_CODE)},'0.00','1',${h.sqlString(DIAGNOSIS_DATE)},${h.sqlString(DX)},'O'); SELECT LAST_INSERT_ID()`);
  owned.drug = sql.value(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,BN,GN,customName,special,
      archived,script_no,create_date,lastUpdateDate)
    VALUES (${h.sqlString(s.provider)},${s.patient},${h.sqlString(VISIT_DATE)},${h.sqlString(VISIT_DATE)},${h.sqlString(VISIT_DATE)},
      NULL,NULL,${h.sqlString(medication)},${h.sqlString(marker)},0,0,NOW(),NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(sql.value(`SELECT customName FROM drugs WHERE drugid=${owned.drug} AND demographic_no=${s.patient}`) === medication,
    'The custom medication fixture was truncated or changed on storage');
  h.assert([owned.appointment, owned.header, owned.item, owned.drug].every(id => /^[1-9]\d*$/.test(id)), 'Fixture rows were not inserted');

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'continuity-administration', timeout: 20000 });
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
  await s.step('Report by Template opens; Add Template stores the owned continuity template', async () => {
    const link = admin.getByRole('link', { name: 'Report by Template', exact: true, includeHidden: true });
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor();
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Report by Template frame did not load');
    await frame.waitForURL(/\/oscarReport\/reportByTemplate\/ViewHomePage/);
    await settle('Template Library');
    await frameClick(frame.getByRole('link', { name: 'Add Template', exact: true }), 'Add Template');
    await frame.locator('#uploadReportXml').setInputFiles({ name: 'continuity.xml', mimeType: 'text/xml', buffer: Buffer.from(templateXml(title)) });
    await frameClick(frame.locator('input[type="submit"][value^="Upload"]'), 'Upload & Add');
    const outcome = await frame.locator('.alert').first().innerText({ timeout: 20000 }).catch(() => '(no alert)');
    h.assert(/Saved Successfully/.test(outcome), `Upload answered: ${outcome.replace(/\s+/g, ' ').slice(0, 200)}`);
    templateId = sql.value(`SELECT GROUP_CONCAT(templateid) FROM reportTemplates WHERE templatetitle=${h.sqlString(title)}`);
    h.assert(/^[1-9]\d*$/.test(templateId), 'Upload did not create exactly one owned template');
    h.assert(sql.value(`SELECT type FROM reportTemplates WHERE templateid=${templateId}`) === 'continuity', 'The stored template type is not continuity');
  });

  // The result page of a report whose rows were dropped also trips DataTables (a table with a header row
  // and no body rows). That is the same defect, so it is recorded here and asserted in the last step instead
  // of failing the earlier steps, so every provable step is proven first.
  const deferred = [];
  const deferJsProblems = since => {
    for (const key of ['pageErrors', 'consoleIssues', 'dialogs', 'unexpectedDialogs']) {
      for (const entry of s.recorder[key].splice(since[key])) deferred.push(`${key}: ${String(entry.text || entry.message || JSON.stringify(entry)).split('\n')[0].slice(0, 160)}`);
    }
  };
  let rows;
  await s.step('Run Query opens the continuity result page with its nine report columns', async () => {
    await frameClick(frame.getByRole('link', { name: 'Template Library', exact: true }), 'Template Library');
    await frame.locator('#userSearch').pressSequentially(title);
    const visible = frame.locator('#tableData tr:visible');
    h.assert(await visible.count() === 1, 'Library search did not narrow to the one owned template');
    await frameClick(visible.getByRole('link', { name: title, exact: true }), 'Report configuration');
    h.assert(new URL(frame.url()).searchParams.get('templateid') === templateId, 'Configuration opened another template');
    for (const [id, date] of Object.entries({ diag_date_from: '2026-01-01', diag_date_to: '2026-01-31',
      visit_date_from: '2026-03-01', visit_date_to: '2026-03-31' })) {
      await frame.locator(`input[name="${id}"]`).fill(date);
    }
    await frame.locator('input[name="dxCodes:list"]').fill(DX);
    const since = {};
    for (const key of ['pageErrors', 'consoleIssues', 'dialogs', 'unexpectedDialogs']) since[key] = s.recorder[key].length;
    await frameClick(frame.locator('input[type="submit"][value="Run Query"]'), 'Continuity result');
    await frame.locator('table.reportTable').waitFor();
    await admin.waitForTimeout(1000);
    deferJsProblems(since);
    const headers = (await frame.locator('table.reportTable th').allInnerTexts()).map(t => t.trim());
    h.assert(headers.join('|') === 'ID|Date of Code|Dx Code|Date of Visit|Provider Seen|MRP|Billing Code|Rx Name|Prescriber',
      'The continuity result header is not the nine documented columns');
    rows = [];
    for (const tr of await frame.locator('table.reportTable tr').all()) rows.push((await tr.locator('td').allInnerTexts()).map(t => t.trim()));
  });

  await s.step('the report lists the owned patient\'s Dx code row and the visit with the billing code', async () => {
    const dxRows = rows.filter(r => r[0] === s.patient && r[2] === DX);
    const visit = rows.filter(r => r[0] === s.patient && r[3] === VISIT_DATE);
    // Provider Seen is the appointment provider's "first last" name as the report's SQL concatenates it.
    const seen = sql.value(`SELECT CONCAT(first_name,' ',last_name) FROM provider WHERE provider_no=${h.sqlString(s.provider)}`);
    h.assert(dxRows.length === 1 && dxRows[0][1] === DIAGNOSIS_DATE && visit.length === 1 && visit[0][6] === SERVICE_CODE
      && visit[0][4] === seen,
      `The report lists ${dxRows.length} Dx row(s) and ${visit.length} visit row(s) for the owned patient, expected 1 and 1: `
      + 'native DATE values must render correctly, using the independently requested diagnosis and visit windows');
    h.assert(visit[0][7] === medication, `Custom medication name was not rendered as literal text: ${JSON.stringify(visit[0][7])}`);
    h.assert(await frame.locator('table.reportTable img, table.reportTable script').count() === 0,
      'A stored medication name became report markup');
    h.assert(await frame.evaluate(() => window.cx === undefined), 'Stored medication markup executed');
    h.assert(deferred.length === 0, `The result page raised JavaScript problems: ${deferred.join(' | ')}`);
  });
}

if (require.main === module) runWorkflow('native-cast-report-continuity', workflow, { openMaster: false });
module.exports = { workflow };
