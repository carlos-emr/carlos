#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Schedule > Tickler > search > Check All / Clear All > select two > Complete;
// Completed > row pencil > Active > Update; Completed > select > Delete > Deleted.
// Owns four tasks: three visible matches and one hidden control. No shared rows change.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { waitForTicklerTable } = require('./tickler-forward-filters-playwright-checks');

async function workflow(s) {
  const manualRefresh = process.env.TICKLER_BULK_ONLY === 'state-actions';
  h.assert(!process.env.TICKLER_BULK_ONLY || manualRefresh, 'TICKLER_BULK_ONLY must be state-actions when set');
  const ids = [];
  const needle = `${s.marker} batch`;
  const owned = `demographic_no=${s.patient} AND message LIKE ${h.sqlString(`${s.marker}%`)}`;
  s.cleanup(() => {
    const select = `SELECT tickler_no FROM tickler WHERE ${owned}`;
    s.sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${select});
      DELETE FROM tickler_comments WHERE tickler_no IN (${select});
      DELETE FROM tickler_update WHERE tickler_no IN (${select});
      DELETE FROM tickler WHERE ${owned}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM tickler WHERE ${owned}`) === '0', 'Bulk task fixtures remain');
  });
  for (const label of ['batch first', 'batch second', 'batch third', 'hidden control']) {
    const id = s.sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
      VALUES(${s.patient},${h.sqlString(`${s.marker} ${label}`)},'A',NOW(),DATE_SUB(CURDATE(),INTERVAL 1 DAY),
        ${h.sqlString(s.provider)},'Normal',${h.sqlString(s.provider)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'Task fixture creation failed'); ids.push(id);
  }
  const statuses = () => s.sql.rows(`SELECT status FROM tickler WHERE tickler_no IN (${ids.join(',')}) ORDER BY tickler_no`)
    .map(row => row[0]).join('');
  const opened = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_tickler)').first(),
    { context: s.context, recorder: s.recorder, label: 'bulk-tickler-list', timeout: 20000 });
  const list = opened.page;
  const box = id => list.locator(`#ticklerResults input[name="checkbox"][value="${id}"]`);
  const button = action => list.locator(`form[name="ticklerform"] input[type="button"][onclick*="submit_form.value='${action}'"]`);
  const isList = response => response.request().method() === 'GET' && h.pathOnly(response.url()).endsWith('/tickler/ListTicklers');
  async function filter(status) {
    await waitForTicklerTable(list);
    if (await list.locator('#ticklerview').inputValue() !== status) {
      const [response] = await Promise.all([
        list.waitForResponse(r => isList(r) && new URL(r.url()).searchParams.get('status') === status),
        list.locator('#ticklerview').selectOption(status),
      ]);
      h.assert(response.status() === 200, `Status filter answered HTTP ${response.status()}`);
    }
    const search = list.locator('#ticklerResults_filter input[type="search"]');
    if (await search.inputValue() !== needle) {
      const [response] = await Promise.all([
        list.waitForResponse(r => isList(r) && new URL(r.url()).searchParams.get('search[value]') === needle),
        search.fill(needle),
      ]);
      h.assert(response.status() === 200, `Task search answered HTTP ${response.status()}`);
    }
    await waitForTicklerTable(list);
  }
  async function submit(action, selected) {
    const [response] = await Promise.all([
      list.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/tickler/DbTicklerMain')),
      list.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      button(action).click(),
    ]);
    h.assert(response.status() < 400, `${action} answered HTTP ${response.status()}`);
    const form = new URLSearchParams(response.request().postData());
    h.assert(JSON.stringify(form.getAll('checkbox').sort()) === JSON.stringify([...selected].sort()),
      'Bulk submission did not contain exactly the checked task IDs');
    h.assert(Boolean(form.get('CSRF-TOKEN')), 'Bulk submission omitted CSRF protection');
    await list.waitForLoadState('domcontentloaded');
    await waitForTicklerTable(list);
  }
  await s.step('Check All and Clear All affect only the three visible matching tasks', async () => {
    await filter('A');
    h.assert(await list.locator('#ticklerResults input[name="checkbox"]').count() === 3, 'Search did not isolate the three batch tasks');
    await list.locator('#checkAllLink').click();
    // javascript: links schedule their evaluation after the click's default
    // action. Observe the result rather than racing that browser task.
    await list.waitForFunction(() => document.querySelectorAll('#ticklerResults input[name="checkbox"]:checked').length === 3,
      null, { timeout: 5000 }).catch(() => {});
    h.assert(await list.locator('#ticklerResults input[name="checkbox"]:checked').count() === 3, 'Check All missed visible tasks');
    await list.locator('a[href="javascript:ClearAll();"]').click();
    await list.waitForFunction(() => document.querySelectorAll('#ticklerResults input[name="checkbox"]:checked').length === 0,
      null, { timeout: 5000 }).catch(() => {});
    h.assert(await list.locator('#ticklerResults input[name="checkbox"]:checked').count() === 0, 'Clear All left a task selected');
    h.assert(statuses() === 'AAAA', 'Selecting tasks changed a stored status');
  });
  await s.step('Complete moves exactly the selected two tasks out of Active and into Completed', async () => {
    await box(ids[0]).check(); await box(ids[1]).check();
    await submit('Complete', ids.slice(0, 2));
    h.assert(statuses() === 'CCAA', 'Complete changed an unchecked or hidden task, or missed a checked task');
    await filter('A');
    h.assert(await box(ids[2]).count() === 1 && await box(ids[0]).count() === 0 && await box(ids[1]).count() === 0,
      'The active list did not reflect the completed tasks');
    await filter('C');
    h.assert(await box(ids[0]).count() === 1 && await box(ids[1]).count() === 1 && await box(ids[2]).count() === 0,
      'The completed list contains the wrong tasks');
  });
  async function reactivateTask() {
    const row = list.locator('#ticklerResults tbody tr')
      .filter({has: list.locator(`input[name="checkbox"][value="${ids[0]}"]`)});
    const edit = await s.popup(list, row.locator('a[onclick*="openTicklerEdit"]'), 'reactivate-task');
    await edit.locator('#status').selectOption('A');
    const refreshed = manualRefresh ? null : list.waitForResponse(isList, { timeout: 5000 }).catch(() => null);
    const [save] = await Promise.all([
      edit.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/tickler/EditTickler')),
      edit.locator('input[name="updateTickler"]').click(),
    ]);
    h.assert(save.status() === 200, `Reactivating answered HTTP ${save.status()}`);
    await expectValue(s.sql, `SELECT status FROM tickler WHERE tickler_no=${ids[0]}`, 'A', 'Reactivation was not stored');
    if (manualRefresh) {
      // A clinician can explicitly rerun the report. This independent variant
      // reaches Delete/Deleted even while the automatic refresh is broken.
      const [response] = await Promise.all([list.waitForResponse(isList), list.locator('#formSubmitBtn').click()]);
      h.assert(response.status() === 200, 'Create Report did not refresh the edited task');
    } else {
      const response = await refreshed;
      h.assert(response && response.status() === 200, 'The task was reactivated in the database but its Completed opener did not refresh');
    }
    await waitForTicklerTable(list);
    h.assert(statuses() === 'ACAA', 'Reactivation changed another task');
    // A page reload may clear the search. Reapply it through the same user control.
    await filter('C');
    h.assert(await box(ids[0]).count() === 0 && await box(ids[1]).count() === 1, 'The completed opener did not remove the reactivated task');
    if (!edit.isClosed()) await edit.close();
  }
  if (manualRefresh) await s.step('the Completed row pencil reactivates one task and Create Report refreshes the list', reactivateTask);
  else await s.step('the Completed row pencil reactivates one task and refreshes the opener', reactivateTask);
  await s.step('Delete archives the selected completed task and preserves all unchecked tasks', async () => {
    await box(ids[1]).check(); await submit('Delete', [ids[1]]);
    h.assert(statuses() === 'ADAA', 'Delete changed a different task');
    await filter('D');
    h.assert(await box(ids[1]).count() === 1 && await list.locator('#ticklerResults input[name="checkbox"]').count() === 1,
      'Deleted does not show exactly the archived batch task');
  });
  await s.step('the Deleted filter offers Erase Completely and hides Complete and Delete', async () => {
    h.assert(await button('Erase Completely').isVisible(), 'Deleted filter has no Erase Completely action');
    h.assert(!await button('Complete').isVisible() && !await button('Delete').isVisible(),
      'Deleted filter still offers active-task actions');
  });
}
if (require.main === module) runWorkflow(process.env.TICKLER_BULK_ONLY === 'state-actions' ? 'tickler-bulk-state-actions' : 'tickler-bulk-actions',
  workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
