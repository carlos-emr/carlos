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
async function settled(chart) {
  await chart.waitForFunction(() => !window.autoSaveXhr && window.issueUpdatesPending === 0);
  await chart.evaluate(() => clearAutoSaveTimer());
}

/** Submit the rendered editor form through its authenticated browser session. */
async function editorFormDelete(s, chart, issueId) {
  const submitted = await chart.evaluate(id => {
    const form = document.forms.caseManagementEntryForm;
    const issue = form.querySelector(`input[data-issue-id="${id}"]`);
    const index = issue && /^issueCheckList\[(\d+)\]\.checked$/.exec(issue.name);
    if (!index) throw new Error('The owned issue has no rendered editor-form index');
    const body = new URLSearchParams(new FormData(form));
    const token = globalThis.CarlosAjax?.getCsrfToken();
    if (!body.get('chartContext') || !token) throw new Error('The editor form has no authenticated chart context or CSRF token');
    body.set('method', 'issueDelete');
    body.set('deleteId', index[1]);
    body.set('ajax', 'true');
    body.set('CSRF-TOKEN', token);
    return { body: body.toString(), token };
  }, issueId);
  return s.context.request.post(h.appUrl(s.config.baseUrl, '/CaseManagementEntry'), {
    data: submitted.body,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': submitted.token },
    maxRedirects: 0,
  });
}

/** A refused editor-form request preserves clinical rows and the current typed text. */
async function refusedDelete(s, chart, issueId, status, text, label, applicationStatus) {
  const baseline = readPatientState(s.sql, s.patient);
  const response = await editorFormDelete(s, chart, issueId);
  try { await applicationStatus(response, status, label); }
  finally { await response.dispose(); }
  h.assert(await chart.locator(NOTE).inputValue() === text, `${label} discarded the current editor text`);
  assertPatientStateUnchanged(baseline, readPatientState(s.sql, s.patient), label);
}

/** Fail the exact issue DELETE after Hibernate has inserted this run's note revision. */
function rejectOwnedIssueDelete(s, patient, issueId, noteId, text) {
  h.assert(/^[1-9]\d*$/.test(patient) && /^[1-9]\d*$/.test(issueId) && /^[1-9]\d*$/.test(noteId)
    && /^FAKE-PW[0-9a-f]{16}$/.test(s.marker)
    && text.startsWith(s.marker), 'The issue-delete rollback probe needs an owned patient and draft');
  const name = `pw_issue_${s.marker.slice(7)}`;
  const message = `Owned issue-delete rollback probe ${s.marker.slice(7)}`;
  const predicate = `TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=${q(name)}`;
  h.assert(s.sql.value(`SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE ${predicate}`) === '0',
    'The issue-delete rollback trigger name is already in use');
  let definition;
  const drop = () => {
    const rows = s.sql.rows(`SELECT ACTION_STATEMENT,EVENT_OBJECT_TABLE,ACTION_TIMING,EVENT_MANIPULATION
      FROM information_schema.TRIGGERS WHERE ${predicate}`);
    if (rows.length) {
      h.assert(rows.length === 1 && rows[0][1] === 'casemgmt_issue' && rows[0][2] === 'AFTER'
        && rows[0][3] === 'DELETE' && (!definition || rows[0][0] === definition)
        && rows[0][0].includes(q(text)) && rows[0][0].includes(q(message)),
      'The issue-delete rollback trigger ownership changed');
      s.sql.execute(`DROP TRIGGER ${name}`);
    }
    h.assert(s.sql.value(`SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE ${predicate}`) === '0',
      'The owned issue-delete rollback trigger survived cleanup');
  };
  s.cleanup(drop);
  s.sql.execute(`DELIMITER //
CREATE TRIGGER ${name} AFTER DELETE ON casemgmt_issue FOR EACH ROW
BEGIN
  IF OLD.id=${issueId} AND OLD.demographic_no=${patient}
      AND EXISTS (SELECT 1 FROM casemgmt_note WHERE note_id<>${noteId} AND demographic_no=${patient}
        AND provider_no=${q(s.provider)} AND BINARY note=BINARY ${q(text)}) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=${q(message)};
  END IF;
END//
DELIMITER ;`);
  definition = s.sql.value(`SELECT ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE ${predicate}`);
  h.assert(definition && definition.includes(q(text)), 'The owned issue-delete rollback trigger was not installed');
  return { drop, name, message };
}

/**
 * Exercise real editor-form HTTP requests against the packaged server and database.
 * Modern charts retain the legacy Delete link in a hidden reference fragment without
 * an opening control. Do not force that hidden link or claim native alert coverage.
 */
async function checkIssueDeleteAtomicity(session, { applicationStatus }) {
  const { sql, provider, marker, context, config } = session;
  let patient;
  // demographic.last_name is VARCHAR(30); retain the complete unique run marker.
  const stamp = `${marker}-ida`;
  h.assert(stamp.length <= 30, 'Atomic issue-delete fixture name exceeds the patient schema');
  session.cleanup(() => {
    if (!patient) return;
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${q(stamp)}`) === '1', 'Atomic issue-delete patient ownership changed');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND (provider_no<>${q(provider)} OR LOCATE(${q(stamp)},note)=0)`) === '0',
    'Atomic issue-delete note ownership changed');
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    const noteIds = sql.rows(notes).map(([id]) => id);
    h.assert(noteIds.every(id => /^[1-9]\d*$/.test(id)), 'Atomic issue-delete cleanup has an invalid owned note ID');
    const signatures = `type='enc' AND id IN (${noteIds.join(',') || '0'})`;
    const issues = `SELECT id FROM casemgmt_issue WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM hash_audit WHERE ${signatures};
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes}) OR id IN (${issues});
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
      + (SELECT COUNT(*) FROM hash_audit WHERE ${signatures})
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
  // Seed this isolated patient before opening its chart. Reuse the originating
  // chart's authorized program, without navigating an editor away during setup.
  const origin = await session.chart();
  const program = await origin.evaluate(() => String(case_program_id));
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
     program_no,reporter_caisi_role)
    VALUES (NOW(),'2026-10-14 10:00:00',${patient},${q(provider)},${q(original)},0,${q(original)},
      UUID(),'0',0,${q(program)},${q(role)})`, 'atomic issue-delete note');
  // This issue is intentionally unlinked: the native UI offers Delete only for unused issues.
  // Deletion while any saved note is edited still saves that note, so these failures must be atomic.
  const issueId = h.insertId(sql, `INSERT INTO casemgmt_issue
    (demographic_no,issue_id,acute,certain,major,resolved,program_id,type,update_date)
    VALUES (${patient},${catalogId},0,0,0,0,${program},${q(issueType)},NOW())`, 'atomic issue-delete issue');
  const cppId = h.insertId(sql, `INSERT INTO casemgmt_cpp (demographic_no,provider_no,reminders,ongoingConcerns)
    VALUES (${patient},${q(provider)},${q(stamp)},${q(description)})`, 'atomic issue-delete CPP');
  const chart = await context.newPage();
  await h.gotoApp(chart, config.baseUrl,
    `/encounter/IncomingEncounter?demographicNo=${patient}&providerNo=${provider}&programId=${program}`);
  await waitForNavbars(chart, 90000);
  await settled(chart);
  const savedEditor = chart.locator(`#caseNote_note${noteId}`);
  // Reopening an owned unsigned note selects its editor automatically. A note
  // shown as a card has an Edit link; drive it when the editor is absent.
  if (!(await savedEditor.isVisible())) {
    const [response] = await Promise.all([
      chart.waitForResponse(answer => answer.request().method() === 'POST'
        && new URL(answer.url()).pathname.endsWith('/CaseManagementEntry')
        && new URLSearchParams(answer.request().postData() || '').get('method') === 'edit'),
      chart.locator(`#edit${noteId}`).click(),
    ]);
    await applicationStatus(response, 200, 'Opening the saved note editor');
  }
  await savedEditor.waitFor({ state: 'visible' });
  await settled(chart);
  h.assert((await savedEditor.inputValue()).trimEnd() === original,
    'Atomic issue-delete did not load the exact saved note text');
  h.assert(await chart.locator('#caseManagementEntryForm input[name="noteId"]').inputValue() === noteId,
    'Atomic issue-delete did not open its saved note');
  const issue = chart.locator(`#noteIssues input[data-issue-id="${issueId}"]`);
  h.assert(await issue.count() === 1, 'Atomic issue-delete editor did not list its own issue');
  await session.step('blank saved-note text refuses an authenticated editor-form delete without changing clinical rows', async () => {
    await chart.locator(NOTE).fill('');
    await settled(chart);
    await refusedDelete(s, chart, issueId, 400, '', 'Blank-text issue deletion', applicationStatus);
  });
  const draft = `${stamp}-retained-clinical-draft H&P + follow-up`;
  await session.step('a disappeared editor lock refuses an authenticated editor-form delete and retains all clinical data and typed text', async () => {
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
      await refusedDelete(s, chart, issueId, 409, draft, 'Expired-lock issue deletion', applicationStatus);
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
  await session.step('a real database failure after issue deletion rolls back the issue, note, CPP, draft and editor lock together', async () => {
    const saved = chart.waitForResponse(answer => answer.request().method() === 'POST'
      && new URL(answer.url()).pathname.endsWith('/CaseManagementEntry')
      && new URLSearchParams(answer.request().postData() || '').get('method') === 'autosave');
    saved.catch(() => {});
    await chart.evaluate(() => autoSave());
    await applicationStatus(await saved, 200, 'Owned issue-delete draft autosave');
    await settled(chart);
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${q(provider)} AND program_id=${program} ORDER BY id DESC LIMIT 1`, draft,
    'The rollback probe did not first persist its exact recoverable draft');
    const locks = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`));
    // A leaked signature for a rolled-back new note would no longer join to the
    // patient's surviving notes. Compare the exclusive fixture's full digest list.
    const signatureState = () => JSON.stringify(sql.rows(`SELECT
      SHA2(JSON_ARRAY(pkid,signature,id,type,algorithm),256) FROM hash_audit ORDER BY pkid`));
    const signaturesBefore = signatureState();
    const failure = rejectOwnedIssueDelete(s, patient, issueId, noteId, draft);
    try {
      const started = new Date().toISOString();
      await refusedDelete(s, chart, issueId, 500, draft, 'Transactional issue-delete failure', applicationStatus);
      h.assert(JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`)) === locks,
        'The failed issue-delete transaction changed the editor lock');
      h.assert(signatureState() === signaturesBefore, 'The failed issue-delete transaction leaked or changed a note signature');
      h.assert(await issue.count() === 1 && await chart.locator('#caseManagementEntryForm input[name="noteId"]').inputValue() === noteId,
        'The failed issue-delete transaction changed the browser issue or selected note');
      console.log('  CHECK chart-context-request-guards: owned issue-delete rollback ' + JSON.stringify({
        trigger: failure.name, message: failure.message, status: 500, startUTC: started, endUTC: new Date().toISOString(),
      }));
    } finally { failure.drop(); }
  });
  await session.step('an authorized editor-form retry deletes the issue and stores the retained note text together', async () => {
    const response = await editorFormDelete(s, chart, issueId);
    try { await applicationStatus(response, 200, 'Authorized editor-form issue-delete retry'); }
    finally { await response.dispose(); }
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_issue WHERE id=${issueId}`, '0',
      'Authorized retry did not remove its issue');
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND provider_no=${q(provider)} AND note=${q(draft)}`, '1', 'Authorized retry lost or duplicated the retained clinical text');
    h.assert(sql.value(`SELECT note FROM casemgmt_note WHERE note_id=${noteId}`) === original,
      'Authorized retry overwrote the previous saved note revision');
    h.assert(await chart.locator(NOTE).inputValue() === draft,
      'The editor-form request changed the current typed text');
    h.assert(sql.value(`SELECT reminders FROM casemgmt_cpp WHERE id=${cppId}`) === stamp,
      'Authorized issue deletion changed unrelated CPP text');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${q(provider)} AND program_id=${program}`) === '0',
    'The successful retry retained the superseded autosaved draft');
  });
  await releaseChartLocks(context, config.baseUrl, [chart]);
  // The workflow owns the browser lifetime; keep this paused page until its
  // normal teardown instead of racing an unload beacon with the next scenario.
  return { chart, patient, owner: stamp };
}

module.exports = { checkIssueDeleteAtomicity };
