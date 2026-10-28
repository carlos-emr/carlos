#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Two sessions edit the same tickler. A stale priority/comment save must not undo B's completion,
 * append history, or discard the draft. Reviewing the current record permits the intended change.
 * Direct replay and deletion are refused explicitly; all SQL fixtures and history are owned. */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openSecondSession, failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { seedTickler, openPatientTicklerList, openTicklerEdit, saveTicklerEdit } = require('./lib/concurrency-tickler');

async function refusedSave(s, edit, status) {
  const mark = failureMark(s.recorder);
  const [response] = await Promise.all([
    edit.waitForResponse(r => r.request().method() === 'POST' && /\/tickler\/EditTickler$/.test(new URL(r.url()).pathname)),
    edit.locator('input[name="updateTickler"]').click(),
  ]);
  h.assert(response.status() === status, `Expected HTTP ${status}, received ${response.status()}`);
  await edit.locator('#error').waitFor({ state: 'visible' });
  consumeExpectedFailure(s.recorder, mark, { status, path: /\/tickler\/EditTickler$/ });
  h.assert(await edit.locator('input[name="updateTickler"]').isEnabled(), 'Refusal left the draft disabled');
}

async function workflow(s) {
  const { sql } = s;
  const tickler = seedTickler(s, 'edit race');
  const deleted = seedTickler(s, 'deleted edit');
  const stateQuery = `SELECT CONCAT(status,'|',priority) FROM tickler WHERE tickler_no=${tickler.id}`;
  const counts = () => sql.value(`SELECT CONCAT((SELECT COUNT(*) FROM tickler_comments WHERE tickler_no=${tickler.id}),
    '|',(SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${tickler.id}))`);
  const b = await openSecondSession(s, { label: 'second-session' });
  const aList = await openPatientTicklerList(s.context, s.master, s.recorder, 'tickler-list-a');
  const bList = await openPatientTicklerList(b.context, b.master, s.recorder, 'tickler-list-b');
  const aEdit = await openTicklerEdit(s.context, s.recorder, aList, tickler.message, 'tickler-edit-a');
  const original = await aEdit.locator('input[name="ticklerEditVersion"]').inputValue();
  const draft = `${s.marker} retained draft comment`;
  let committedCounts;

  await s.step('session B completes the tickler and records one comment', async () => {
    const bEdit = await openTicklerEdit(b.context, s.recorder, bList, tickler.message, 'tickler-edit-b');
    await bEdit.locator('select[name="status"]').selectOption('C');
    await bEdit.locator('[name="newMessage"]').fill(`${s.marker} completion comment`);
    await saveTicklerEdit(bEdit);
    await expectValue(sql, stateQuery, 'C|Normal', 'Session B\'s completion did not reach the database');
    committedCounts = counts();
    h.assert(committedCounts === '1|2', `Completion must write one comment and original/change history: ${committedCounts}`);
    await bEdit.close().catch(() => {});
  });
  await s.step('the stale save explicitly refuses all writes and retains the draft', async () => {
    await aEdit.locator('select[name="priority"]').selectOption('High');
    await aEdit.locator('[name="newMessage"]').fill(draft);
    await refusedSave(s, aEdit, 409);
    h.assert((await aEdit.locator('#error').innerText()).includes('Review the current tickler'), 'Missing recovery guidance');
    h.assert(await aEdit.locator('[name="newMessage"]').inputValue() === draft, 'Conflict discarded the draft comment');
    h.assert(await aEdit.locator('select[name="priority"]').inputValue() === 'High', 'Conflict discarded the draft priority');
    h.assert(await aEdit.locator('[name="ticklerEditVersion"]').inputValue() === original, 'Conflict silently refreshed the stale version');
    h.assert(sql.value(stateQuery) === 'C|Normal' && counts() === committedCounts, 'Conflict changed the tickler or its history');
  });
  await s.step('replaying the same stale draft remains a no-write conflict', async () => {
    await refusedSave(s, aEdit, 409);
    h.assert(sql.value(stateQuery) === 'C|Normal' && counts() === committedCounts, 'Replay changed the tickler or its history');
  });
  await s.step('reviewing the current tickler permits the priority change without undoing completion', async () => {
    const pagePromise = s.context.waitForEvent('page');
    await aEdit.locator('#reviewCurrentTickler').click();
    const current = await pagePromise;
    h.wireStrictPage(current, 'tickler-current', s.recorder);
    await current.waitForLoadState('domcontentloaded');
    h.assert(await current.locator('select[name="status"]').inputValue() === 'C', 'Recovery did not display the current completion');
    h.assert(await current.locator('[name="ticklerEditVersion"]').inputValue() !== original, 'Recovery did not load the current state');
    h.assert(await aEdit.locator('[name="newMessage"]').inputValue() === draft, 'Opening recovery discarded the original draft');
    await current.locator('select[name="priority"]').selectOption('High');
    await current.locator('[name="newMessage"]').fill(draft);
    await saveTicklerEdit(current);
    await expectValue(sql, stateQuery, 'C|High', 'Reviewed priority change lost the completion or failed to save');
    h.assert(counts() === '2|3', 'Reviewed save must append exactly one comment and one update');
    await current.close().catch(() => {});
  });
  await s.step('a deleted tickler refuses the edit and leaves its draft visible', async () => {
    // The list reuses the named edit_tickler window. B's editor is closed; using B's
    // context keeps A's refused draft open instead of navigating that existing window.
    const edit = await openTicklerEdit(b.context, s.recorder, bList, deleted.message, 'tickler-deleted');
    sql.execute(`DELETE FROM tickler WHERE tickler_no=${deleted.id} AND demographic_no=${s.patient}`);
    await edit.locator('[name="newMessage"]').fill(draft);
    await refusedSave(s, edit, 404);
    h.assert(await edit.locator('[name="newMessage"]').inputValue() === draft, 'Deletion discarded the draft');
    h.assert(await edit.locator('#tickler-edit-recovery').isHidden(), 'A deleted tickler offers a misleading current-record link');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE tickler_no=${deleted.id}`) === '0', 'The deleted tickler was recreated');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler_comments WHERE tickler_no=${deleted.id}`) === '0', 'The deleted tickler gained a comment');
    h.assert(await aEdit.locator('[name="newMessage"]').inputValue() === draft, 'The original refused draft was replaced');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-tickler-edit', workflow, { openPatient: true });
