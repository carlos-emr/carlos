#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Who may read an E-Chart note's revision history, and for which patient (coverage-plan area:
 * authorization, "authz-read" sweep).
 *
 * User path: Schedule > Search > Master Record > E-Chart > type a note > Save > the note's "rev"
 * link (Note Revision History popup, CaseManagementEntry method=notehistory). The older
 * Note Search and Case Management view pages reach the same data through method=history.
 *
 * Asserted: the note saves and the rev link opens its history (UI positive path); a login with no
 * sec object is refused on both history methods (pinned, with a positive control); then, in the
 * LAST step, the history must belong to the patient the request names and to a patient the login
 * may read: (a) naming a DIFFERENT owned patient with this note's id must not return the text,
 * (b) a doctor login locked out of the patient (|o| on _demographic$N and _eChart$N) must be
 * refused. Both methods read the note by id alone (CaseManagementEntry2Action.notehistory/history
 * call caseManagementMgr.getNote/getHistory(noteId) and use demographicNo only for the heading).
 *
 * Fixtures: the workflow's owned patient, one more owned patient, the saved note, one throwaway
 * er_clerk login and one throwaway doctor login with the lock rows; all removed and verified.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { probe, classify, forbiddenByApp, urlFor, signIn, ledger } = require('./lib/authz-read-probe');

const isEntry = method => r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

async function workflow(s) {
  const { sql, marker, patient, provider, config } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  const otherName = `${marker}-OTHER`;
  const text = `${marker} chart note for the revision history check`;
  let other;
  s.cleanup(() => {
    fixture.cleanup();
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned note rows were not removed');
    // Saving the note and the history reads write audit rows (log.data carries the note text); remove the
    // ones about the two owned patients or carrying this run's marker.
    const demos = [patient, other].filter(Boolean).join(',');
    const ownedLog = `demographic_no IN (${demos}) OR data LIKE ${h.sqlString(`%${marker}%`)} OR content LIKE ${h.sqlString(`%${marker}%`)}`;
    sql.execute(`DELETE FROM log WHERE ${ownedLog}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE ${ownedLog}`) === '0', 'Owned audit rows were not removed');
    if (other) {
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${other} AND last_name=${h.sqlString(otherName)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${other}`) === '0', 'The second patient was not removed');
    }
  });

  let chart; let noteId;
  await s.step('E-Chart: a note is saved and its rev link opens the Note Revision History', async () => {
    chart = await s.chart();
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    await editor.click();
    await editor.fill(text);
    const [saved] = await Promise.all([chart.waitForResponse(isEntry('save')), chart.locator('#saveImg').first().click()]);
    h.assert(saved.status() === 200, `Saving the note answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id DESC LIMIT 1`, text,
      'The note text did not reach casemgmt_note');
    noteId = sql.value(`SELECT MAX(note_id) FROM casemgmt_note WHERE demographic_no=${patient}`);
    const rev = chart.locator('#encMainDiv a[onclick^="return showHistory("]').first();
    const history = await s.popup(chart, rev, 'note-history');
    h.assert(new URL(history.url()).searchParams.get('method') === 'notehistory'
      && new URL(history.url()).searchParams.get('noteId') === noteId, 'The rev link opened the history of another note');
    await history.locator('h3', { hasText: 'Note Revision History' }).waitFor();
    h.assert((await history.locator('body').innerText()).includes(text), 'The history popup does not show the note');
    await history.close();
  });

  const noteUrl = (method, demo) => `CaseManagementEntry?method=${method}&noteId=${noteId}&demographicNo=${demo}&demographic_no=${demo}`;
  const get = (context, route) => probe(context, urlFor(config, route), { needles: [text] });
  let clerk; let doctor;

  await s.step('a login holding no sec object is refused on both history methods (control: the owner is served)', async () => {
    other = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
        provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(otherName)},'Other','1980','01','02','F','AC',${h.sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(other), 'The second patient fixture was not created');
    for (const method of ['notehistory', 'history']) {
      const own = await get(s.context, noteUrl(method, patient));
      h.assert(own.status === 200 && own.found.includes(text), `The owner was not served ${method} for the right patient (HTTP ${own.status})`);
    }
    h.assert(fixture.roleHoldsNothing('er_clerk'), 'er_clerk is expected to hold no sec object');
    clerk = await signIn(s, fixture.addLogin('er_clerk'));
    for (const method of ['notehistory', 'history']) {
      const result = await get(clerk.context, noteUrl(method, patient));
      h.assert(forbiddenByApp(result) && !result.found.length, `${method} answered HTTP ${result.status} to a login without sec objects`);
    }
  });

  await s.step('a doctor login is locked out of the owned patient and signs in', async () => {
    const login = fixture.addLogin('doctor');
    h.assert(fixture.lockPatient(login, patient).length === 2, 'The patient lock rows were not written');
    doctor = await signIn(s, login);
    // The lock is a real gate for this patient: the master record refuses it while the other patient is served.
    const refused = await get(doctor.context, `demographic/DemographicEdit?demographic_no=${patient}`);
    const served = await get(doctor.context, `demographic/DemographicEdit?demographic_no=${other}`);
    h.assert(forbiddenByApp(refused) && classify(served) === 'served', `The lock fixture does not discriminate (${refused.status}/${served.status})`);
  });

  await s.step('a note history is only served for the patient the note belongs to, and not to a login locked out of that patient', async () => {
    const open = ledger();
    for (const method of ['notehistory', 'history']) {
      const mismatched = await get(s.context, noteUrl(method, other));
      if (mismatched.status === 200 && mismatched.found.includes(text)) open.add(`${method} naming another patient`, 'the note id alone decides');
      const lockedOut = await get(doctor.context, noteUrl(method, patient));
      if (lockedOut.status === 200 && lockedOut.found.includes(text)) open.add(`${method} for a patient the login is locked out of`, `HTTP ${lockedOut.status}`);
    }
    open.assertEmpty('Note history was served outside its patient');
  });
}

if (require.main === module) runWorkflow('authz-read-chart-note-history', workflow);
module.exports = { workflow };
