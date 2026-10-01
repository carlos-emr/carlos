#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check: two sessions edit the same tickler (stale edit un-completes it).
 *
 * User path (both sessions of the shared test login): Schedule > Search > Master Record > Tickler >
 * the tickler row's edit link > Edit Tickler popup > change field > Save. Session A opens the edit
 * popup first (status Active, priority Normal). Session B opens its own edit popup, sets the status
 * to Complete and saves. Session A, still on its stale form, raises the priority to High and saves.
 *
 * Asserted: B's completion lands (control); A's save reaches the server and stores its priority or is
 * refused; and the tickler is NOT silently put back to Active. EditTickler2Action compares the posted
 * status / priority / assignee / date with the row it just loaded, not with what the form was
 * rendered from, so every posted field wins; the stale Active status reverts the completion. The
 * check fails at that last step.
 *
 * Fixtures: one owned tickler seeded by SQL for the owned FAKE- patient (removed by cleanup with its
 * comments and history rows, asserted gone). Wave-7 sweep "concurrency".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openSecondSession } = require('./lib/concurrency-support');
const { seedTickler, openPatientTicklerList, openTicklerEdit, saveTicklerEdit } = require('./lib/concurrency-tickler');

async function workflow(s) {
  const { sql } = s;
  const tickler = seedTickler(s, 'edit race');
  const stateQuery = `SELECT CONCAT(status,'|',priority) FROM tickler WHERE tickler_no=${tickler.id}`;
  const state = () => sql.value(stateQuery);
  const b = await openSecondSession(s, { label: 'second-session' });
  const aList = await openPatientTicklerList(s.context, s.master, s.recorder, 'tickler-list-a');
  const bList = await openPatientTicklerList(b.context, b.master, s.recorder, 'tickler-list-b');
  let aEdit;

  await s.step('session A opens the edit popup and holds it', async () => {
    aEdit = await openTicklerEdit(s.context, s.recorder, aList, tickler.message, 'tickler-edit-a');
    h.assert(await aEdit.locator('select[name="status"]').inputValue() === 'A', 'The edit popup does not start Active');
    h.assert(new URL(aEdit.url()).searchParams.get('tickler_no') === tickler.id, 'The edit popup opened another tickler');
  });
  await s.step('session B completes the same tickler from its own edit popup', async () => {
    const bEdit = await openTicklerEdit(b.context, s.recorder, bList, tickler.message, 'tickler-edit-b');
    await bEdit.locator('select[name="status"]').selectOption('C');
    await saveTicklerEdit(bEdit);
    await expectValue(sql, stateQuery, 'C|Normal', 'Session B\'s completion did not reach the database');
    await bEdit.close().catch(() => {});
  });
  await s.step('session A saves a different field (priority) from its stale popup', async () => {
    await aEdit.locator('select[name="priority"]').selectOption('High');
    await saveTicklerEdit(aEdit);
  });
  await s.step('the tickler session B completed is still completed', async () => {
    const [status, priority] = state().split('|');
    h.assert(status === 'C',
      `Session A's stale Edit Tickler save silently put the completed tickler back to status '${status}' (priority now ${priority}). `
      + 'EditTickler2Action applies every posted field with no comparison against the state the form was rendered from, so the later save wins '
      + 'and the completion is lost without a warning to either user.');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-tickler-edit', workflow, { openPatient: true });
