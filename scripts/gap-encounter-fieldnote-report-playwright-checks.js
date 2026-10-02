#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration ▸ Forms/eForms ▸ Field Note Report & Management: choose which eForms count as field
 * notes, read the supervisor and resident reports, view and download one resident's report.
 *
 * User path: Schedule ▸ Administration ▸ Forms/eForms ▸ "Field Note Report & Management" (listed only for a
 * login holding _admin.fieldnote, which no role holds on the packaged install and which is not even in
 * secObjectName, so the check grants it to a throwaway login by provider number; the report
 * opens in the administration iframe, eform/fieldNoteReport/fieldnotereport) ▸ Select eForms
 * (fieldnoteselect: tick the owned template, Submit; later Unselect) ▸ Get Field Notes for a date
 * range ▸ the Observer/Supervisor count table ▸ the resident's View (a new window with the report,
 * fieldnotereportdetail) and Download (a .doc).
 * Asserts: the report starts with no field-note eForm assigned; the select page offers the owned
 * "<marker> Field Note" template and storing the choice writes property fieldNoteEform exactly; the
 * report then lists the owned resident with the owned supervisor, each count equal to the two seeded
 * notes; the detail window shows the totals, purposes, role/skills, impressions, clinical domains and
 * the topic / done-well / work-on / follow-up text of both notes (HTML-encoded, a literal <b> stays
 * text); the download is an attachment named after the resident with the same counts; a date range
 * with no notes lists no resident; Unselect empties the property and brings the "no eForm assigned"
 * notice back. The final step is the one that fails today: the report, its detail and the select page
 * are gated only by _eform read (EFormViewRoutes) although the menu entry needs _admin.fieldnote, and
 * the select page changes the clinic-wide property from a GET, so a clinician without administration
 * rights can open the supervisor report (read-only probe, with a throwaway doctor-only login).
 * Fixtures: EXCLUSIVE (clinic-wide property fieldNoteEform is snapshotted and restored). An owned
 * template, two owned eform_data instances (supervisor = the throwaway login, resident = the first demo
 * provider) with their eform_values on the owned patient, and a throwaway login with an owned
 * _admin.fieldnote grant. Cleanup removes them and restores the property, asserting each. Implements gap-encounter "field note report".
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates, clickDownloadsOrOpens } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;

const TEMPLATE_HTML = '<html><head><title>field note</title></head><body><form method="post" action="" name="FormName" id="FormName">'
  + '<input type="text" name="subject" id="subject"><input type="submit" value="Submit" id="SubmitButton"></form></body></html>';

const ymd = date => date.toISOString().slice(0, 10);

async function workflow(s) {
  const { sql, marker, patient, provider, config } = s;
  const templateName = `${marker} Field Note`;
  const roleText = '<b data-role-test>role & text</b>';
  const resident = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no NOT IN (${q(provider)},'-1')
    AND last_name LIKE 'FAKE-%' AND provider_no NOT LIKE '8%' ORDER BY provider_no LIMIT 1`);
  h.assert(resident, 'No demo provider is available to act as the resident');
  const residentName = `${sql.value(`SELECT last_name FROM provider WHERE provider_no=${q(resident)}`)}, ${sql.value(`SELECT first_name FROM provider WHERE provider_no=${q(resident)}`)}`;
  const today = new Date();
  const longAgo = '2000-01-01';
  const propertySnapshot = sql.rows(`SELECT id, value, (value IS NULL) FROM property WHERE name='fieldNoteEform' AND provider_no IS NULL`);
  const propertyCount = () => sql.value(`SELECT COUNT(*) FROM property WHERE name='fieldNoteEform'`);
  const propertyValue = () => sql.value(`SELECT COALESCE(value,'') FROM property WHERE name='fieldNoteEform'`);
  h.assert(propertySnapshot.length <= 1, 'The fieldNoteEform property is ambiguous; refusing to change it');
  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  let supervisorName;
  let fid;
  const fdids = [];
  const noteTexts = {
    doneWell: `Listened well, <b>kept</b> eye contact & "calm" ${marker}`,
    workOn: `Time management 50% ${marker}`,
    followUp: `Review next week ${marker}`,
  };

  s.cleanup(() => {
    for (const fdid of fdids) {
      sql.execute(`DELETE FROM eform_values WHERE fdid=${fdid}; DELETE FROM eform_data WHERE fdid=${fdid} AND demographic_no=${patient}`);
    }
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(templateName)}`);
    // Restore the clinic-wide property exactly as found.
    if (propertySnapshot.length) {
      // The harness reads a SQL NULL and the text 'NULL' alike as null, so the companion flag (column 3, a
      // literal 1/0 from the query) decides whether SQL NULL is restored rather than the text 'null'.
      const [id, value, wasNull] = propertySnapshot[0];
      sql.execute(`UPDATE property SET value=${wasNull === '1' ? 'NULL' : q(value === null ? 'NULL' : value)} WHERE id=${id} AND name='fieldNoteEform'`);
    } else {
      sql.execute(`DELETE FROM property WHERE name='fieldNoteEform' AND (value IS NULL OR value='' OR value=${q(String(fid || 0))})`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(templateName)})
      + (SELECT COUNT(*) FROM eform_data WHERE form_name=${q(templateName)})`) === '0', 'Owned field-note eForms were not removed');
    h.assert(propertySnapshot.length ? propertyCount() === '1' : propertyCount() === '0', 'The fieldNoteEform property was not restored');
  });
  s.cleanup(() => {
    sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(fixture.providerNo || '')} AND objectName='_admin.fieldnote'`);
    fixture.cleanup();
  });
  fixture.create();
  const supervisor = fixture.providerNo;
  supervisorName = `${marker}, Throwaway`;
  sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
    VALUES (${q(supervisor)},'_admin.fieldnote','x',0,${q(provider)})`);
  const ctx = await h.newContext(s.context.browser(), config);
  ctx.on('page', page => h.wireStrictPage(page, 'fieldnote-user', s.recorder));
  s.cleanup(() => ctx.close().catch(() => {}));
  const schedule = await h.login(ctx, { ...config, testUser: fixture.username }, s.recorder, { label: 'fieldnote-schedule' });

  // The property starts empty for this run (the report's "no eForm assigned" state).
  if (propertySnapshot.length) sql.execute(`UPDATE property SET value=NULL WHERE id=${propertySnapshot[0][0]}`);
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(templateName)},'','field note fixture',CURDATE(),CURTIME(),${q(provider)},1,${q(TEMPLATE_HTML)},0,0,${q(roleText)},0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'The field-note template was not created');
  const notes = [
    { values: { 'direct.observation': 'on', 'minimal.supervision': 'on', clinical_domain: 'adults', communicator: 'on',
      'clinical.topic': `Hypertension ${marker}`, 'done.well': noteTexts.doneWell, 'work.on': noteTexts.workOn,
      'follow-up': noteTexts.followUp, dateField: ymd(today) } },
    { values: { 'chart.review': 'on', 'close.supervision': 'on', clinical_domain: 'obstetrics', knowledge: 'on',
      'clinical.topic': `Prenatal ${marker}`, dateField: ymd(today) } },
  ];
  for (const note of notes) {
    const fdid = sql.value(`INSERT INTO eform_data(fid,form_name,subject,demographic_no,status,form_date,form_time,form_provider,
      form_data,showLatestFormOnly,patient_independent,roleType)
      VALUES(${fid},${q(templateName)},'field note',${patient},1,CURDATE(),CURTIME(),${q(supervisor)},${q(TEMPLATE_HTML)},0,0,''); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fdid), 'A field-note instance was not created');
    fdids.push(fdid);
    const rows = { residentId: resident, ...note.values };
    sql.execute(Object.entries(rows).map(([name, value]) => `INSERT INTO eform_values(fdid,fid,demographic_no,var_name,var_value)
      VALUES(${fdid},${fid},${patient},${q(name)},${q(value)})`).join(';'));
  }

  const { page: admin } = await clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel,#admin2').first(),
    { context: ctx, recorder: s.recorder, label: 'fieldnote-admin', timeout: 20000 });
  const frame = async () => {
    const element = admin.locator('#dynamic-content iframe').first();
    await element.waitFor();
    const inner = await (await element.elementHandle()).contentFrame();
    h.assert(inner, 'The field note iframe did not load');
    return inner;
  };
  const settle = async inner => { await inner.waitForLoadState('load'); await inner.waitForLoadState('networkidle').catch(() => {}); };
  let report;

  await s.step('Administration ▸ Forms/eForms ▸ Field Note Report & Management opens the report with no eForm assigned', async () => {
    await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
    const link = admin.locator('a.xlink[rel$="/eform/fieldNoteReport/fieldnotereport"]').first();
    await revealAuditLink(admin, link, 20000);
    await link.click();
    report = await frame();
    await report.locator('form[name="fieldNoteReportForm"]').waitFor();
    h.assert(await report.locator('input[type="button"][onclick*="fieldnoteselect"]').count() === 1, 'The report has no Select eForms button');
    h.assert(await report.locator('tr[style*="255, 255, 0"], tr[style*="FFFF00" i]').count() === 1,
      'The report did not warn that no eForm is assigned as a field note');
    h.assert(propertyValue() === '' || propertyCount() === '0', 'The fieldNoteEform property was not empty at the start');
  });

  await s.step('Select eForms offers the owned template; Submit stores it as the field-note eForm', async () => {
    await report.locator('input[type="button"][onclick*="fieldnoteselect"]').click();
    await report.waitForURL(/fieldnoteselect/);
    await settle(report);
    const box = report.locator(`input[name="selected_eform"][value="${fid}"]`);
    await box.waitFor({ state: 'attached', timeout: 20000 });
    h.assert((await box.locator('xpath=ancestor::tr').innerText()).includes(roleText)
      && await report.locator('[data-role-test]').count() === 0,
      'The available eForm role was rendered as markup');
    await box.check();
    await Promise.all([report.waitForNavigation({ waitUntil: 'load' }), report.locator('input[type="submit"]').click()]);
    await settle(report);
    await expectValue(sql, `SELECT COUNT(*) FROM property WHERE name='fieldNoteEform' AND FIND_IN_SET(${fid}, value)>0`, '1',
      'Submitting the select page did not store the owned eForm in property fieldNoteEform');
    h.assert(await report.locator(`a[onclick*="remove_select(${fid})"]`).count() === 1,
      'The select page does not list the chosen eForm with an Unselect link');
    h.assert((await report.locator(`a[onclick*="remove_select(${fid})"]`).locator('xpath=ancestor::tr').innerText()).includes(roleText)
      && await report.locator('[data-role-test]').count() === 0,
      'The selected eForm role was rendered as markup');
    h.assert(await report.locator(`input[name="selected_eform"][value="${fid}"]`).count() === 0,
      'The chosen eForm is still offered for selection');
  });

  await s.step('Get Field Notes lists the owned resident and the supervisor count table', async () => {
    await report.locator('input[type="button"][onclick*="fieldnotereport"]').click();
    await report.waitForURL(/fieldnotereport(\?|$)/);
    await settle(report);
    await report.locator('#startDate').fill(ymd(new Date(today.getTime() - 86400000)));
    await report.locator('#endDate').fill(ymd(today));
    await Promise.all([report.waitForURL(/date_start=/), report.locator('input[type="submit"][onclick*="getFieldNotes"]').click()]);
    await settle(report);
    const residentRow = report.locator('tr').filter({ hasText: residentName }).filter({ has: report.locator('input[value="View"]') });
    h.assert(await residentRow.count() === 1, 'The report does not list the owned resident');
    await report.locator('#supervisorReportButton').click();
    const table = report.locator('#supervisorReport');
    await table.waitFor({ state: 'visible' });
    const text = (await table.innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(supervisorName) && text.includes(residentName), 'The supervisor table lacks the owned supervisor or resident');
    h.assert(/Total:?\s*2\b/.test(text), 'The supervisor table total is not the two seeded notes');
    h.assert((await report.locator('#supervisorReportButton').getAttribute('value')).includes('(2)'),
      'The observer note count button does not show the two seeded notes');
  });

  await s.step('View opens the resident report with the counts and both notes', async () => {
    const residentRow = report.locator('tr').filter({ hasText: residentName }).filter({ has: report.locator('input[value="View"]') });
    const popup = await (async () => {
      const [page] = await Promise.all([ctx.waitForEvent('page', { timeout: 20000 }), residentRow.locator('input[value="View"]').click()]);
      await page.waitForLoadState('domcontentloaded');
      return page;
    })();
    await popup.waitForURL(/fieldnotereportdetail/);
    const text = (await popup.locator('body').innerText()).replace(/\s+/g, ' ');
    const has = (needle, message) => h.assert(text.includes(needle), message);
    has(residentName, 'The detail report does not name the resident');
    h.assert(/Total field notes\s*:\s*2\b/.test(text), 'The detail report total is not 2');
    h.assert(/Direct Observation\s*:\s*1\b/.test(text) && /Chart Review\s*:\s*1\b/.test(text) && /Case Discussion\s*:\s*0\b/.test(text),
      'The detail report purposes are not 1 / 1 / 0');
    h.assert(/Knowledge\s*:\s*1\b/.test(text), 'The detail report role/skill count is wrong');
    h.assert(/Minimal Supervision \(1\)/.test(text) && /Close Supervision \(1\)/.test(text), 'The impression sections are not 1 / 1');
    h.assert(/Clinical Domain : Adults \(1\)/.test(text) && /Clinical Domain : Obstetrics \(1\)/.test(text), 'The clinical domain sections are wrong');
    has(`Hypertension ${marker}`, 'The first note topic is missing');
    has(`Prenatal ${marker}`, 'The second note topic is missing');
    has(noteTexts.doneWell, 'Done well text lost its characters or was not shown as plain text');
    has(noteTexts.workOn, 'Work on text is missing');
    has(noteTexts.followUp, 'Follow-up text is missing');
    h.assert(await popup.locator('b').count() === 0, 'Stored markup in a note was rendered instead of shown as text');
    await popup.close();
  });

  await s.step('Download delivers the resident report as a .doc attachment with the same counts', async () => {
    const residentRow = report.locator('tr').filter({ hasText: residentName }).filter({ has: report.locator('input[value="Download"]') });
    const outcome = await clickDownloadsOrOpens(report.page(), residentRow.locator('input[value="Download"]'),
      { context: ctx, recorder: s.recorder, label: 'fieldnote-download', timeout: 30000 });
    h.assert(outcome.kind === 'download', 'Download did not deliver a file');
    h.assert(/\.doc$/.test(outcome.download.suggestedFilename()) && !/[,\s"\\]/.test(outcome.download.suggestedFilename()),
      'The downloaded report is not a plain .doc file name');
    const path = await outcome.download.path();
    const body = fs.readFileSync(path, 'utf8').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ');
    h.assert(body.includes(residentName) && /Total field notes\s*:\s*2\b/.test(body), 'The downloaded report does not carry the counts');
  });

  await s.step('invalid calendar dates and reversed ranges are rejected consistently', async () => {
    for (const [start, end] of [['2026-02-30', '2026-03-02'], ['2026-03-02', '2026-03-01']]) {
      // Send raw dates: the date-picker normalizes invalid typed days on blur.
      const summaryUrl = new URL(report.url());
      summaryUrl.searchParams.set('date_start', start);
      summaryUrl.searchParams.set('date_end', end);
      const dialogs = await h.withExpectedDialogs(admin, async () => {
        await report.goto(summaryUrl.toString(), { waitUntil: 'load' });
        await settle(report);
      });
      h.assert(dialogs.some(dialog => /Invalid Start\/End dates/.test(dialog.text)),
        'The report did not reject invalid dates');
      h.assert(await report.locator('input[value="View"]').count() === 0,
        'Invalid dates offered a resident report');
      const url = new URL(report.url());
      url.pathname = url.pathname.replace(/fieldnotereport$/, 'fieldnotereportdetail');
      url.searchParams.set('residentId', resident);
      url.searchParams.set('method', 'view');
      const response = await ctx.request.get(url.toString());
      h.assert(response.status() === 400, 'The detail report accepted invalid dates');
      await response.dispose();
    }
  });

  await s.step('a date range with no notes lists no resident', async () => {
    await report.locator('#startDate').fill(longAgo);
    await report.locator('#endDate').fill(longAgo);
    await Promise.all([report.waitForURL(/date_start=2000-01-01/), report.locator('input[type="submit"][onclick*="getFieldNotes"]').click()]);
    await settle(report);
    h.assert(await report.locator('input[value="View"]').count() === 0, 'A date range with no notes still lists a resident');
  });

  await s.step('Unselect empties the property and the report warns again', async () => {
    await report.locator('input[type="button"][onclick*="fieldnoteselect"]').click();
    await report.waitForURL(/fieldnoteselect/);
    await settle(report);
    await Promise.all([report.waitForNavigation({ waitUntil: 'load' }), report.locator(`a[onclick*="remove_select(${fid})"]`).click()]);
    await settle(report);
    await expectValue(sql, `SELECT COUNT(*) FROM property WHERE name='fieldNoteEform' AND FIND_IN_SET(${fid}, COALESCE(value,''))>0`, '0',
      'Unselect left the eForm in property fieldNoteEform');
    h.assert(await report.locator(`input[name="selected_eform"][value="${fid}"]`).count() === 1,
      'The unselected eForm is not offered for selection again');
  });

  await s.step('a read-only report user has no selection control and GET cannot change the selected forms', async () => {
    sql.execute(`DELETE FROM secUserRole WHERE provider_no=${q(supervisor)} AND role_name='admin';
      UPDATE secObjPrivilege SET privilege='r' WHERE roleUserGroup=${q(supervisor)} AND objectName='_admin.fieldnote'`);
    const context = await h.newContext(s.context.browser(), config);
    context.on('page', page => h.wireStrictPage(page, 'fieldnote-reader', s.recorder));
    try {
      const page = await h.login(context, { ...config, testUser: fixture.username }, s.recorder, { label: 'fieldnote-reader' });
      const base = `${String(config.baseUrl).replace(/\/$/, '')}/eform/fieldNoteReport`;
      await page.goto(`${base}/fieldnotereport`);
      await page.locator('form[name="fieldNoteReportForm"]').waitFor();
      h.assert(await page.locator('input[onclick*="fieldnoteselect"]').count() === 0,
        'A read-only report user was offered the selection control');
      const response = await context.request.get(`${base}/fieldnoteselect?selected_eform=${fid}`);
      h.assert(response.status() === 405, 'GET was not refused for a field-note selection change');
      h.assert(response.headers().allow === 'POST', 'The mutation response advertises an unsafe method');
      await response.dispose();
      h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE name='fieldNoteEform' AND FIND_IN_SET(${fid}, COALESCE(value,''))>0`) === '0',
        'GET changed the field-note selection');
    } finally {
      await context.close();
    }
  });

  await s.step('a clinician without administration rights is refused the field-note report', async () => {
    // The same throwaway login, now a doctor only: no administration role and no _admin.fieldnote grant.
    sql.execute(`DELETE FROM secUserRole WHERE provider_no=${q(supervisor)} AND role_name='admin';
      DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(supervisor)} AND objectName='_admin.fieldnote'`);
    h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(supervisor)} AND role_name='doctor' AND activeyn=1`) === '1',
      'The throwaway login does not hold the doctor role');
    h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup IN ('doctor',${q(supervisor)}) AND objectName IN ('_admin','_admin.fieldnote')`) === '0',
      'The doctor-only login unexpectedly holds an administration right');
    const context = await h.newContext(s.context.browser(), config);
    context.on('page', page => h.wireStrictPage(page, 'fieldnote-doctor', s.recorder));
    try {
      await h.login(context, { ...config, testUser: fixture.username }, s.recorder, { label: 'fieldnote-doctor' });
      const response = await context.request.get(`${String(config.baseUrl).replace(/\/$/, '')}/eform/fieldNoteReport/fieldnotereport`,
        { maxRedirects: 0 });
      const body = await response.text();
      h.assert(response.status() >= 400 || /securityError/.test(response.headers().location || ''),
        `A doctor without _admin.fieldnote was served the field-note report (HTTP ${response.status()})`);
      h.assert(!body.includes('fieldNoteReportForm'), 'The field-note report markup reached a doctor without _admin.fieldnote');
    } finally {
      await context.close();
    }
  });
}

if (require.main === module) runWorkflow('gap-encounter-fieldnote-report', workflow, { openPatient: true });
module.exports = { workflow };
