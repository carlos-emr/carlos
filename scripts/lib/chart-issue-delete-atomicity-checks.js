/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
'use strict';
const h = require('./playwright-harness');
const { expectValue } = require('./workflow-session');
const { waitForNavbars } = require('../echart-navbar-modules-playwright-checks');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const { releaseChartLocks } = require('./chart-lock-cleanup');

const q = h.sqlString;
const NOTE = '#caseManagementEntryForm textarea[name="caseNote_note"]';
const isDelete = response => response.request().method() === 'POST'
  && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(response.request().postData() || '').get('method') === 'issueDelete';

async function settled(chart) {
  await chart.waitForFunction(() => !window.autoSaveXhr && window.issueUpdatesPending === 0);
  await chart.evaluate(() => clearAutoSaveTimer());
}

/** A rejected native Delete must not report success, replace the editor or touch clinical rows. */
async function refusedDelete(s, chart, link, status, text, label, applicationStatus) {
  const baseline = readPatientState(s.sql, s.patient);
  const issueMarkup = await chart.locator('#noteIssues').innerHTML();
  const failureMessage = await chart.evaluate(() => updateIssueError);
  const before = { responses: new Set(s.recorder.badResponses), console: new Set(s.recorder.consoleIssues) };
  let response;
  const dialogs = await h.withExpectedDialogs(chart, async () => {
    const answer = chart.waitForResponse(isDelete);
    answer.catch(() => {});
    await link.click();
    response = await answer;
    await response.finished();
    await settled(chart);
  });
  await applicationStatus(response, status, label);
  h.assert(dialogs.length === 1 && dialogs[0].type === 'alert'
    && dialogs[0].text === `${status} ${failureMessage}`, `${label} did not show its exact refusal alert`);
  h.assert(await chart.locator(NOTE).inputValue() === text, `${label} discarded the current editor text`);
  h.assert(await chart.locator('#noteIssues').innerHTML() === issueMarkup,
    `${label} replaced or removed the current issue controls`);
  assertPatientStateUnchanged(baseline, readPatientState(s.sql, s.patient), label);
  // Excuse only the controlled browser POST and its resource diagnostic after proving
  // its response, UI and database effects. Unrelated failures remain strict findings.
  const responses = s.recorder.badResponses.filter(entry => !before.responses.has(entry)
    && entry.url === response.url() && entry.method === 'POST' && entry.status === status);
  h.assert(responses.length === 1, `${label} did not record exactly one refused POST`);
  const resources = s.recorder.consoleIssues.filter(entry => !before.console.has(entry)
    && entry.location?.url === response.url()
    && entry.type === 'error'
    && new RegExp(`^Failed to load resource: the server responded with a status of ${status}(?: \\([^)]*\\))?$`).test(entry.text));
  h.assert(resources.length <= 1, `${label} recorded duplicate resource errors`);
  s.recorder.badResponses = s.recorder.badResponses.filter(entry => !responses.includes(entry));
  s.recorder.consoleIssues = s.recorder.consoleIssues.filter(entry => !resources.includes(entry));
}

/** Drive actual issue deletion against a saved note, including lock loss and blank text. */
async function checkIssueDeleteAtomicity(session, { applicationStatus }) {
  const { sql, provider, marker, context, config } = session;
  let patient;
  const stamp = `${marker}-issue-delete-atomicity`;
  session.cleanup(() => {
    if (!patient) return;
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${q(stamp)}`) === '1', 'Atomic issue-delete patient ownership changed');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND (provider_no<>${q(provider)} OR LOCATE(${q(stamp)},note)=0)`) === '0',
    'Atomic issue-delete note ownership changed');
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    const issues = `SELECT id FROM casemgmt_issue WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes}) OR id IN (${issues});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient};
      DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};
      DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
      DELETE FROM demographicExt WHERE demographic_no=${patient};
      DELETE FROM demographicArchive WHERE demographic_no=${patient};
      DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(stamp)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})`) === '0',
    'Atomic issue-delete fixture rows survived cleanup');
  });
  patient = h.insertId(sql, `INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
     provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(stamp)},'IssueDelete','1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW())`,
  'atomic issue-delete patient');
  const s = { ...session, patient };
  const chart = await context.newPage();
  await h.gotoApp(chart, config.baseUrl,
    `/encounter/IncomingEncounter?demographicNo=${patient}&providerNo=${provider}`);
  await waitForNavbars(chart, 90000);
  await settled(chart);
  const program = await chart.evaluate(() => String(case_program_id));
  h.assert(/^(?:0|[1-9]\d*)$/.test(program), 'Atomic issue-delete chart program is invalid');
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)}
    AND program_id=${program} ORDER BY id LIMIT 1`) || '0';
  h.assert(/^\d+$/.test(role), 'Atomic issue-delete note role is invalid');
  const catalog = sql.rows("SELECT issue_id,role,description FROM issue WHERE code='CTCMM1000'");
  h.assert(catalog.length === 1 && /^[1-9]\d*$/.test(catalog[0][0]), 'Atomic issue-delete catalog fixture is ambiguous');
  const [catalogId, issueType, description] = catalog[0];
  const original = `${stamp}-saved-note`;
  const noteId = h.insertId(sql, `INSERT INTO casemgmt_note
    (update_date,observation_date,demographic_no,provider_no,note,signed,history,uuid,locked,archived,
     program_no,reporter_caisi_role,revision)
    VALUES (NOW(),'2026-10-14 10:00:00',${patient},${q(provider)},${q(original)},0,${q(original)},
      UUID(),'0',0,${q(program)},${q(role)},'1')`, 'atomic issue-delete note');
  // This issue is intentionally unlinked: the native UI offers Delete only for unused issues.
  // Deletion while any saved note is edited still saves that note, so these failures must be atomic.
  const issueId = h.insertId(sql, `INSERT INTO casemgmt_issue
    (demographic_no,issue_id,acute,certain,major,resolved,program_id,type,update_date)
    VALUES (${patient},${catalogId},0,0,0,0,${program},${q(issueType)},NOW())`, 'atomic issue-delete issue');
  const cppId = h.insertId(sql, `INSERT INTO casemgmt_cpp (demographic_no,provider_no,reminders,ongoingConcerns)
    VALUES (${patient},${q(provider)},${q(stamp)},${q(description)})`, 'atomic issue-delete CPP');
  await chart.reload();
  await waitForNavbars(chart, 90000);
  await settled(chart);
  await chart.locator(`#edit${noteId}`).click();
  await chart.locator(`#caseNote_note${noteId}`).waitFor({ state: 'visible' });
  await settled(chart);
  h.assert(await chart.locator('#caseManagementEntryForm input[name="noteId"]').inputValue() === noteId,
    'Atomic issue-delete did not open its saved note');
  const issue = chart.locator(`#noteIssues input[data-issue-id="${issueId}"]`);
  h.assert(await issue.count() === 1, 'Atomic issue-delete editor did not list its own issue');
  const link = issue.locator('..').getByRole('link', { name: 'Delete', exact: true });
  h.assert(await link.count() === 1, 'Atomic issue-delete has no native unused-issue Delete control');
  await session.step('blank saved-note text refuses native issue deletion without changing the issue, CPP or note', async () => {
    await chart.locator(NOTE).fill('');
    await settled(chart);
    await refusedDelete(s, chart, link, 400, '', 'Blank-text issue deletion', applicationStatus);
  });
  const draft = `${stamp}-retained-clinical-draft H&P + follow-up`;
  await session.step('a disappeared editor lock refuses native issue deletion and retains all clinical data and typed text', async () => {
    await chart.locator(NOTE).fill(draft);
    await settled(chart);
    const lockWhere = `demographic_no=${patient} AND provider_no=${q(provider)} AND note_id=${noteId}`;
    const rows = sql.rows(`SELECT id,ip_address,note_id,provider_no,demographic_no,session_id,lock_acquired
      FROM casemgmt_note_lock WHERE ${lockWhere}`);
    h.assert(rows.length === 1 && /^[1-9]\d*$/.test(rows[0][0]), 'The saved-note editor does not own one exact lock');
    const lock = rows[0];
    // Read SQL's own literals before deletion: batch output otherwise cannot distinguish
    // SQL NULL from a literal four-character 'NULL' field when reconstructing the row.
    const [restore] = sql.rows(`SELECT QUOTE(id),QUOTE(ip_address),QUOTE(note_id),QUOTE(provider_no),
      QUOTE(demographic_no),QUOTE(session_id),QUOTE(lock_acquired)
      FROM casemgmt_note_lock WHERE id=${lock[0]} AND ${lockWhere}`);
    h.assert(restore && restore.length === 7, 'The owned lock restoration values are missing');
    sql.execute(`DELETE FROM casemgmt_note_lock WHERE id=${lock[0]} AND ${lockWhere}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE id=${lock[0]}`) === '0',
      'The exact owned lock did not disappear');
    try {
      await refusedDelete(s, chart, link, 409, draft, 'Expired-lock issue deletion', applicationStatus);
    } finally {
      // Restore only the exact owned lock to prove a normal retry, without opening a new
      // form that would mask an in-memory checklist mutation by the refused operation.
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE id=${lock[0]} OR (${lockWhere})`) === '0',
        'Another lock appeared during the controlled lock-loss probe');
      sql.execute(`INSERT INTO casemgmt_note_lock
        (id,ip_address,note_id,provider_no,demographic_no,session_id,lock_acquired)
        VALUES (${restore.map(value => value === null ? 'NULL' : value).join(',')})`);
    }
  });
  await session.step('an authorized retry deletes the issue and stores the retained note text together', async () => {
    const answer = chart.waitForResponse(isDelete);
    answer.catch(() => {});
    await link.click();
    const response = await answer;
    await response.finished();
    await applicationStatus(response, 200, 'Authorized issue-delete retry');
    await settled(chart);
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_issue WHERE id=${issueId}`, '0',
      'Authorized retry did not remove its issue');
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND provider_no=${q(provider)} AND note=${q(draft)}`, '1', 'Authorized retry lost or duplicated the retained clinical text');
    h.assert(sql.value(`SELECT note FROM casemgmt_note WHERE note_id=${noteId}`) === original,
      'Authorized retry overwrote the previous saved note revision');
    h.assert(await chart.locator(`#noteIssues input[data-issue-id="${issueId}"]`).count() === 0,
      'Authorized deletion did not refresh the issue checklist');
    h.assert(sql.value(`SELECT reminders FROM casemgmt_cpp WHERE id=${cppId}`) === stamp,
      'Authorized issue deletion changed unrelated CPP text');
  });
  await releaseChartLocks(context, config.baseUrl, [chart]);
  await chart.close();
}

module.exports = { checkIssueDeleteAtomicity };
