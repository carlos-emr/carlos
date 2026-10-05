#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check: a stale tickler list completes a tickler another session already deleted.
 *
 * User path (both sessions of the shared test login): Schedule > Search > Master Record > Tickler >
 * tick the tickler's row > Delete (session B) / Complete (session A). Both lists were loaded while
 * the tickler was Active. B deletes it. A, whose list still shows it as Active, ticks it and clicks
 * Complete.
 *
 * Asserted: B's delete lands (control) and A's batch POST is answered normally; the deleted tickler is
 * NOT silently brought back as Completed. DbTicklerMain2Action calls TicklerManager.updateStatus for
 * every ticked row and updateStatus applies the requested status whatever the row currently holds
 * (it only skips an identical status), so D -> C resurrects the tickler into the Completed list.
 * The check fails at that last step.
 *
 * Fixtures: one owned tickler seeded by SQL for the owned FAKE- patient; cleanup removes it and its
 * history and asserts it gone. Wave-7 sweep "concurrency".
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openSecondSession } = require('./lib/concurrency-support');
const { seedTickler, openPatientTicklerList, listRow } = require('./lib/concurrency-tickler');

async function batch(list, message, buttonValue) {
  const row = await listRow(list, message);
  await row.locator('input[name="checkbox"]').check();
  const button = list.locator(`input[type="button"][value="${buttonValue}"]`).first();
  h.assert(await button.count() > 0, `The tickler list offers no ${buttonValue} button`);
  await ui.clickAndAwaitReload(list, button, { timeout: 30000, label: buttonValue });
  // DbTicklerMain2Action swallows a per-row exception and redirects with failCount=N; a failed update must not be read as the
  // application protecting the row (the status assertions below could not tell the two apart).
  h.assert(!new URL(list.url()).searchParams.has('failCount'),
    `The ${buttonValue} batch reported failed rows (failCount in the redirect URL), so the update itself did not run`);
}

async function workflow(s) {
  const { sql } = s;
  const tickler = seedTickler(s, 'list race');
  const statusQuery = `SELECT status FROM tickler WHERE tickler_no=${tickler.id}`;
  const b = await openSecondSession(s, { label: 'second-session' });
  const aList = await openPatientTicklerList(s.context, s.master, s.recorder, 'tickler-list-a');
  const bList = await openPatientTicklerList(b.context, b.master, s.recorder, 'tickler-list-b');
  await listRow(aList, tickler.message);
  await listRow(bList, tickler.message);
  const completeLabel = await aList.locator('input.btn-secondary[onclick*="Complete"]').first().getAttribute('value');
  const deleteLabel = await aList.locator('input.btn-danger[onclick*="Delete"]').first().getAttribute('value');

  await s.step('session B deletes the tickler from its list', async () => {
    await batch(bList, tickler.message, deleteLabel);
    await expectValue(sql, statusQuery, 'D', 'Session B\'s delete did not reach the database');
  });
  await s.step('session A completes the same tickler from its stale list', async () => {
    await batch(aList, tickler.message, completeLabel);
  });
  await s.step('the tickler session B deleted stays deleted', async () => {
    const status = sql.value(statusQuery);
    h.assert(status === 'D',
      `Session A's stale Complete turned the deleted tickler into status '${status}': DbTicklerMain2Action -> TicklerManagerImpl.updateStatus applies `
      + 'the requested status to whatever the row currently holds, so a tickler deleted in one window is silently resurrected into the Completed list by another.');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-tickler-list-status', workflow, { openPatient: true });
