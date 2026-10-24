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
 * Asserted: B's delete lands; A receives explicit conflict guidance, with no status or history
 * change. A fresh list can still perform a deliberate transition, and replay of the stale POST
 * cannot create another history row.
 *
 * Fixtures: one owned tickler seeded by SQL for the owned FAKE- patient; cleanup removes it and its
 * history and asserts it gone. Wave-7 sweep "concurrency".
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openSecondSession } = require('./lib/concurrency-support');
const { seedTickler, openPatientTicklerList, listRow } = require('./lib/concurrency-tickler');

async function batch(list, message, buttonValue, conflicts = 0) {
  const row = await listRow(list, message);
  await row.locator('input[name="checkbox"]').check();
  const button = list.locator(`input[type="button"][value="${buttonValue}"]`).first();
  h.assert(await button.count() > 0, `The tickler list offers no ${buttonValue} button`);
  await ui.clickAndAwaitReload(list, button, { timeout: 30000, label: buttonValue });
  // DbTicklerMain2Action swallows a per-row exception and redirects with failCount=N; a failed update must not be read as the
  // application protecting the row (the status assertions below could not tell the two apart).
  h.assert(!new URL(list.url()).searchParams.has('failCount'),
    `The ${buttonValue} batch reported failed rows (failCount in the redirect URL), so the update itself did not run`);
  h.assert(Number(new URL(list.url()).searchParams.get('conflictCount') || 0) === conflicts,
    `The ${buttonValue} batch did not report exactly ${conflicts} stale rows`);
  if (conflicts) {
    const guidance = list.locator('#tickler-status-conflict');
    await guidance.waitFor({ state: 'visible' });
    h.assert((await guidance.innerText()).includes('Review the refreshed list'), 'Missing conflict recovery guidance');
  }
}

async function workflow(s) {
  const { sql } = s;
  const tickler = seedTickler(s, 'list race');
  const statusQuery = `SELECT status FROM tickler WHERE tickler_no=${tickler.id}`;
  const historyQuery = `SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${tickler.id}`;
  const b = await openSecondSession(s, { label: 'second-session' });
  const aList = await openPatientTicklerList(s.context, s.master, s.recorder, 'tickler-list-a');
  const bList = await openPatientTicklerList(b.context, b.master, s.recorder, 'tickler-list-b');
  await listRow(aList, tickler.message);
  await listRow(bList, tickler.message);
  const completeLabel = await aList.locator('input.btn-secondary[onclick*="Complete"]').first().getAttribute('value');
  const deleteLabel = await aList.locator('input.btn-danger[onclick*="Delete"]').first().getAttribute('value');
  let staleRequest;

  await s.step('session B deletes the tickler from its list', async () => {
    await batch(bList, tickler.message, deleteLabel);
    await expectValue(sql, statusQuery, 'D', 'Session B\'s delete did not reach the database');
    h.assert(sql.value(historyQuery) === '1', 'The successful delete must record exactly one history row');
  });
  await s.step('session A completes the same tickler from its stale list', async () => {
    const submitted = aList.waitForRequest(request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/tickler/DbTicklerMain'));
    submitted.catch(() => {});
    await batch(aList, tickler.message, completeLabel, 1);
    staleRequest = await submitted;
    const fields = new URLSearchParams(staleRequest.postData());
    h.assert(fields.get(`expectedStatus_${tickler.id}`) === 'A' && fields.get('checkbox') === tickler.id,
      'The captured request must contain the owned stale selection');
  });
  await s.step('the tickler session B deleted stays deleted', async () => {
    h.assert(sql.value(statusQuery) === 'D', 'A stale Complete resurrected the deleted tickler');
    h.assert(sql.value(historyQuery) === '1', 'A rejected stale save changed tickler history');
  });
  await s.step('a replay of the stale request reports a conflict without another history row', async () => {
    // Replay the real form, including its CSRF token, so this reaches the status guard.
    const response = await s.context.request.post(staleRequest.url(), {
      data: staleRequest.postData(),
      headers: { 'content-type': staleRequest.headers()['content-type'] },
      maxRedirects: 0
    });
    h.assert(response.status() === 302, `The replay returned HTTP ${response.status()} instead of the normal list redirect`);
    const redirect = new URL(response.headers().location, aList.url());
    h.assert(redirect.searchParams.get('conflictCount') === '1' && !redirect.searchParams.has('failCount'),
      'The replay did not report a specific stale-status conflict');
    h.assert(sql.value(statusQuery) === 'D' && sql.value(historyQuery) === '1', 'The replay changed the tickler or history');
  });
  await s.step('a fresh deleted list permits an intentional completion', async () => {
    await aList.locator('#ticklerview').selectOption('D');
    const row = await listRow(aList, tickler.message);
    h.assert(await row.locator(`input[name="expectedStatus_${tickler.id}"]`).inputValue() === 'D',
      'The fresh list did not render the deleted status');
    await batch(aList, tickler.message, completeLabel);
    await expectValue(sql, statusQuery, 'C', 'An intentional transition from the refreshed list failed');
    h.assert(sql.value(historyQuery) === '2', 'The intentional completion must add exactly one history row');
  });

}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-tickler-list-status', workflow, { openPatient: true });
