#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup check of the note revision popup.
// User path: E-Chart > a note's "rev" link. showHistory() (js/newCaseManagementView.js.jsp) opens
// CaseManagementEntry?method=notehistory&noteId=N&demographicNo=D; since #4307 that demographicNo makes the
// popup print the patient's name above the revisions. The check opens that popup URL as the link does: the
// link renders only for a note that has revisions, and the popup is what is under test.
// Fixtures: an owned FAKE patient whose text columns carry inert markup (xss-poison-patient.js) and one signed
// note whose text carries it too, INSERTed (bypassing the WAF, like imported data). Cleanup removes exactly
// these rows by key and asserts they are gone.
// Asserted: the patient's name is shown, as text; no `[data-xp]` element in the popup; no script error.
// Expected to FAIL while app-findings-log.md finding 148 stands (showHistory.jsp prints ${demoName} raw).
// Implements: wave-6 xss-poison (stored markup / output-encoding walk), for a surface #4307 made reachable.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { payload, inspect, Findings, Seeder, fieldIds } = require('./lib/xss-poison-helpers');
const { seedPatient } = require('./lib/xss-poison-patient');

async function workflow(s) {
  const fields = {};
  // Payload numbers start at 1000: each check owns its own range, so a concurrent xss-poison run's rows on a
  // shared list are never mistaken for this run's (inspect() ignores a number it did not create).
  let n = 1000;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup, s.marker);
  const chartNo = `XN${s.marker.slice(-8)}`;
  const me = s.provider;
  let demo;
  let note;
  await s.step('seed the poisoned patient and a signed note', async () => {
    demo = seedPatient(seed, P, me, chartNo);
    const program = s.sql.value("SELECT id FROM program WHERE name='OSCAR' ORDER BY id LIMIT 1");
    const role = s.sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${h.sqlString(me)} AND program_id=${Number(program) || 0} LIMIT 1`);
    h.assert(program && role, "The default OSCAR program or the test provider's role in it is missing");
    note = seed.insert('casemgmt_note', {
      update_date: { raw: 'NOW()' }, observation_date: { raw: 'NOW()' }, demographic_no: Number(demo), provider_no: me,
      note: P('note text', 2000), signed: 1, signing_provider_no: me, encounter_type: '', program_no: program,
      reporter_caisi_role: role, history: P('note history', 2000), uuid: { raw: 'UUID()' }, locked: '0', archived: 0,
    }, { key: 'note_id' });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  await step('the note revision popup shows the patient name and note as text', async () => {
    const page = await s.context.newPage();
    await page.goto(h.appUrl(s.config.baseUrl, `/CaseManagementEntry?method=notehistory&noteId=${note}&demographicNo=${demo}`),
      { waitUntil: 'load', timeout: 30000 });
    await page.waitForLoadState('networkidle').catch(() => {});
    const since = f.mark();
    await inspect(f, 'note revision popup', page, fields, since,
      { expect: fieldIds(fields, 'patient last name', 'patient first name') });
    await page.close();
  });
  await step('the popup found no output-encoding defect', async () => { f.assertNone('Note revision popup'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-note-history', workflow, { openPatient: false });
