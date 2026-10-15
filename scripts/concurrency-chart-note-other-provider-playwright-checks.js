#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check: a second PROVIDER tries to edit a chart note the first provider is editing.
 *
 * User path: Schedule > Search > Master Record > E-Chart > the saved note's Edit link. Provider A
 * (the shared test login) opens the edit of a saved note (the chart takes a per-note lock in
 * casemgmt_note_lock). Provider B (a throwaway login of this run, with the same program membership)
 * opens the same patient's chart and clicks the same note's Edit link.
 *
 * Asserted: A's edit takes the lock (control); B is told "This note is being edited by another user"
 * (alert), B's chart does not open an editor on the note, no lock row is created for B on that note,
 * A keeps the lock and A's editor is untouched. Expected to PASS (pins CaseManagementEntry.isNoteEdited
 * and the editNote() "other" branch).
 *
 * Fixtures: the owned FAKE- patient, one saved note seeded by SQL for the test provider, a throwaway
 * login (lib/throwaway-login-fixture.js) given the test provider's program memberships; all removed by
 * cleanup and asserted gone. Wave-7 sweep "concurrency".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { openSecondSession } = require('./lib/concurrency-support');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const q = h.sqlString;
  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  s.cleanup(() => {
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(notes.every(id => /^[1-9]\d*$/.test(id)), 'Owned note id is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned notes were not removed');
  });
  s.cleanup(() => {
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(fixture.providerNo)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(fixture.providerNo)}`) === '0', 'The throwaway default program was not removed');
  });
  // The throwaway holds the same program memberships as the test provider, so the same chart is open to it.
  sql.execute(`INSERT INTO program_provider (program_id,provider_no,role_id,team_id)
    SELECT program_id,${q(fixture.providerNo)},role_id,team_id FROM program_provider WHERE provider_no=${q(provider)}`);
  sql.execute(`INSERT INTO provider_default_program (provider_no,program_id,signnote)
    SELECT ${q(fixture.providerNo)},program_id,signnote FROM provider_default_program WHERE provider_no=${q(provider)}`);
  const program = sql.value(`SELECT id FROM program WHERE name='OSCAR' ORDER BY id LIMIT 1`);
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)} AND program_id=${program || 0} LIMIT 1`);
  h.assert(program && role, 'The default OSCAR program or the test provider\'s role in it is missing');
  const text = `${marker} saved note under edit`;
  const noteId = sql.value(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,
    encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived) VALUES (NOW(),NOW(),${patient},${q(provider)},${q(text)},1,${q(provider)},
    '',${q(program)},${q(role)},'x',UUID(),'0',0); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(noteId), 'The note fixture was not created');
  const locks = who => sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND note_id=${noteId}${who ? ` AND provider_no=${q(who)}` : ''}`);

  const aChart = await s.chart();
  await s.step('provider A opens the note for editing and takes its lock', async () => {
    await aChart.locator(`#edit${noteId}`).click();
    await aChart.locator(`#caseNote_note${noteId}`).waitFor({ state: 'visible', timeout: 20000 });
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND note_id=${noteId} AND provider_no=${q(provider)}`,
      '1', 'Provider A\'s edit did not take the note lock');
  });
  await s.step('provider B is told the note is being edited by another user and gets no editor and no lock', async () => {
    const b = await openSecondSession(s, { label: 'second-provider', config: { ...s.config, testUser: fixture.username } });
    const bChart = await openChart(b.context, b.master, s.recorder, 20000);
    await waitForNavbars(bChart, 20000);
    const edit = bChart.locator(`#edit${noteId}`);
    await edit.waitFor({ state: 'visible', timeout: 20000 });
    const dialogs = await h.withExpectedDialogs(bChart, async () => {
      await edit.click();
      await bChart.waitForTimeout(1500); // the lock probe is a synchronous ajax call; the alert follows it
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && /being edited by another user/i.test(dialogs[0].text),
      `Provider B was not told the note is being edited by another user (${dialogs.length} dialog(s))`);
    h.assert(await bChart.locator(`#caseNote_note${noteId}`).count() === 0, 'Provider B got an editor on the note provider A is editing');
    h.assert(locks(fixture.providerNo) === '0', 'Provider B\'s refused edit left a lock row for the note');
    h.assert(locks(provider) === '1', 'Provider A lost the note lock to provider B');
    h.assert(await aChart.locator(`#caseNote_note${noteId}`).isVisible(), 'Provider A\'s editor was closed by provider B\'s attempt');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-chart-note-other-provider', workflow, { openPatient: true });
