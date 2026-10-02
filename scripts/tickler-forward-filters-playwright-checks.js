#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * tickler-forward-filters — coverage plan §3.4 (`tickler-forward-filters`: forward a tickler to
 * another provider and the tickler list's Assigned To filter). The preference half of that row
 * lives in tickler-preferences-playwright-checks.js.
 *
 * User path: Schedule ▸ Tickler (tickler/ViewTicklerMain) ▸ search box + Assigned To filter +
 * Create Report (tickler/ListTicklers) ▸ row pencil ▸ edit popup ▸ Assigned To ▸ Update
 * (tickler/EditTickler, the reassignment control).
 *
 * Asserted: forwarding an owned tickler to a second active provider changes
 * tickler.task_assigned_to and writes tickler_update rows (the original-state backfill plus the
 * change, each by the forwarding provider) and no comment; the Assigned To filter then hides the
 * row under the test provider and shows it under the other provider (ListTicklers JSON and the
 * rendered row); forwarding back restores the assignee, adds one more history row and returns the
 * row to the test provider's list.
 *
 * Fixtures: one tickler seeded by SQL for the owned synthetic patient (message carries the
 * marker). Cleanup deletes the owned tickler with its comments, updates and attachments, and
 * asserts it is gone. No shared setting is changed.
 *
 * tickler/ForwardDemographicTickler is deliberately NOT driven here: it only prefills the add
 * form from a document, lab or HRM viewer and has no entry in the tickler list.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

/** The server-side DataTable is initialised and idle. */
async function waitForTicklerTable(page) {
  await page.locator('#ticklerResults').waitFor({ state: 'visible', timeout: 30000 });
  await page.waitForFunction(() => {
    const table = window.jQuery && window.jQuery.fn && window.jQuery.fn.DataTable
      && window.jQuery.fn.DataTable.isDataTable('#ticklerResults') && window.jQuery('#ticklerResults').DataTable();
    if (!table) return false;
    const settings = table.settings()[0];
    return settings.bInitialised && !settings.bDrawing && (!settings.jqXHR || settings.jqXHR.readyState === 4);
  }, null, { timeout: 30000 });
}

const isListRequest = response => response.request().method() === 'GET'
  && h.pathOnly(response.url()).endsWith('/tickler/ListTicklers');

/** Narrow the list to this run's rows through the DataTables search box (sent as search[value]). */
async function searchList(page, needle) {
  const [response] = await Promise.all([
    page.waitForResponse(r => isListRequest(r) && new URL(r.url()).searchParams.get('search[value]') === needle, { timeout: 30000 }),
    page.locator('#ticklerResults_filter input[type="search"]').fill(needle),
  ]);
  h.assert(response.status() === 200, `Searching the tickler list answered HTTP ${response.status()}`);
  await waitForTicklerTable(page);
}

/** Choose an Assigned To value, click Create Report, and return the rows ListTicklers answered with. */
async function listAssignedTo(page, assignee, needle) {
  await page.locator('#assignedTo').selectOption(assignee);
  const expected = assignee === 'all' ? '' : assignee;
  const [response] = await Promise.all([
    page.waitForResponse(r => isListRequest(r) && new URL(r.url()).searchParams.get('assignee') === expected
      && new URL(r.url()).searchParams.get('search[value]') === needle, { timeout: 30000 }),
    page.locator('#formSubmitBtn').click(),
  ]);
  h.assert(response.status() === 200, `Filtering the tickler list answered HTTP ${response.status()}`);
  const json = await response.json();
  h.assert(Array.isArray(json.data), 'ListTicklers did not answer a DataTables payload');
  await waitForTicklerTable(page);
  return json.data;
}

/** The hidden submit iframe rendered the server's success sentinel. */
async function waitForSaveSentinel(page, frameId, sentinelId) {
  await page.waitForFunction(({ frame, sentinel }) => {
    const iframe = document.getElementById(frame);
    return Boolean(iframe && iframe.contentDocument && iframe.contentDocument.getElementById(sentinel));
  }, { frame: frameId, sentinel: sentinelId }, { timeout: 30000 });
}

/** Reassign the owned tickler through the list row's edit popup; returns after the save sentinel. */
async function forwardThroughEdit(s, list, marker, ticklerNo, from, to) {
  const row = list.locator('#ticklerResults tbody tr').filter({ hasText: marker }).first();
  await row.waitFor({ state: 'visible', timeout: 20000 });
  const edit = await s.popup(list, row.locator('a[onclick*="openTicklerEdit"]'), 'tickler-edit');
  await edit.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
  h.assert(new URL(edit.url()).searchParams.get('tickler_no') === ticklerNo, 'The edit popup opened another tickler');
  h.assert(await edit.locator('#assignedToProviders').inputValue() === from, 'The edit popup did not preselect the current assignee');
  await edit.locator('#assignedToProviders').selectOption(to);
  const [save] = await Promise.all([
    edit.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/tickler/EditTickler'), { timeout: 30000 }),
    edit.locator('input[name="updateTickler"]').click(),
  ]);
  h.assert(save.status() === 200, `Forwarding the tickler answered HTTP ${save.status()}`);
  await waitForSaveSentinel(edit, 'ticklerEditFrame', 'tickler-edit-ok');
  if (!edit.isClosed()) await edit.close();
}


async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const message = `${marker} forward through the tickler list`;
  const other = sql.rows(`SELECT provider_no, last_name, first_name FROM provider WHERE status='1'
    AND provider_no NOT LIKE '-%' AND provider_no<>${h.sqlString(provider)} ORDER BY last_name, first_name, provider_no LIMIT 1`)[0];
  if (!other) throw new h.SkipCheck('No second active provider exists to forward a tickler to');
  const [otherNo, otherLast, otherFirst] = other;
  const providerName = sql.value(`SELECT CONCAT(last_name, ', ', first_name) FROM provider WHERE provider_no=${h.sqlString(provider)}`);
  const namesOther = name => String(name || '').includes(otherLast) && String(name || '').includes(otherFirst);
  const ownedTicklers = `demographic_no=${patient} AND message LIKE ${h.sqlString(`${marker}%`)}`;
  s.cleanup(() => {
    const ids = sql.rows(`SELECT tickler_no FROM tickler WHERE ${ownedTicklers}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Owned tickler ID is invalid');
    if (ids.length) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler WHERE ${ownedTicklers} AND tickler_no IN (${ids.join(',')})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${ownedTicklers}`) === '0', 'Owned ticklers were not removed');
  });
  const ticklerNo = sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
    VALUES(${patient},${h.sqlString(message)},'A',NOW(),DATE_SUB(CURDATE(),INTERVAL 1 DAY),${h.sqlString(provider)},'Normal',${h.sqlString(provider)});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(ticklerNo), 'Owned tickler was not created');
  const updates = () => sql.value(`SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${ticklerNo}`);

  // Schedule ▸ Tickler: a popup, or the schedule tab itself in the focused schedule mode.
  const opened = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_tickler)').first(),
    { context: s.context, recorder: s.recorder, label: 'tickler-list', timeout: 20000 });
  const list = opened.page;
  h.assert(h.pathOnly(list.url()).endsWith('/tickler/ViewTicklerMain'), 'The schedule Tickler link did not open the tickler list');
  await waitForTicklerTable(list);
  await searchList(list, marker);

  await s.step('the owned tickler is listed under the test provider before forwarding', async () => {
    const rows = await listAssignedTo(list, provider, marker);
    const row = rows.find(item => String(item.id) === ticklerNo);
    h.assert(row && row.assigneeName === providerName, 'The seeded tickler is not listed under its assignee');
    await list.locator('#ticklerResults tbody tr').filter({ hasText: marker }).first().waitFor({ state: 'visible', timeout: 20000 });
  });

  await s.step('forwarding through the edit popup reassigns the tickler and records its history', async () => {
    h.assert(updates() === '0', 'The seeded tickler already had history');
    await forwardThroughEdit(s, list, marker, ticklerNo, provider, otherNo);
    await expectValue(sql, `SELECT task_assigned_to FROM tickler WHERE tickler_no=${ticklerNo}`, otherNo, 'Forwarding did not change task_assigned_to');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${ticklerNo}
      AND assignedTo=${h.sqlString(otherNo)} AND provider_no=${h.sqlString(provider)}`) === '1', 'Forwarding did not write a tickler_update row naming the new assignee');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${ticklerNo}
      AND assignedTo=${h.sqlString(provider)} AND provider_no=${h.sqlString(provider)}`) === '1', 'The first forward did not backfill the original assignee');
    h.assert(updates() === '2', `expected the backfill plus one update row, found ${updates()}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler_comments WHERE tickler_no=${ticklerNo}`) === '0', 'Forwarding without a note wrote a comment');
  });

  await s.step('the Assigned To filter hides the forwarded tickler under the test provider and shows it under the other provider', async () => {
    let rows = await listAssignedTo(list, provider, marker);
    h.assert(!rows.some(item => String(item.id) === ticklerNo), 'The forwarded tickler is still listed under the test provider');
    h.assert(await list.locator('#ticklerResults tbody tr').filter({ hasText: message }).count() === 0, 'The forwarded row is still rendered under the test provider');
    rows = await listAssignedTo(list, otherNo, marker);
    const row = rows.find(item => String(item.id) === ticklerNo);
    h.assert(row && namesOther(row.assigneeName), 'The forwarded tickler is not listed under the other provider with that assignee');
    await list.locator('#ticklerResults tbody tr').filter({ hasText: message }).filter({ hasText: otherLast }).first()
      .waitFor({ state: 'visible', timeout: 20000 });
  });

  await s.step('forwarding back restores the assignee and the list', async () => {
    await forwardThroughEdit(s, list, marker, ticklerNo, otherNo, provider);
    await expectValue(sql, `SELECT task_assigned_to FROM tickler WHERE tickler_no=${ticklerNo}`, provider, 'Forwarding back did not restore task_assigned_to');
    h.assert(updates() === '3', `expected one more history row after forwarding back, found ${updates()}`);
    const rows = await listAssignedTo(list, provider, marker);
    h.assert(rows.some(item => String(item.id) === ticklerNo && item.assigneeName === providerName), 'The tickler did not return to the test provider\'s list');
  });
}

// The patient fixture owns the tickler; the Master Record is not needed on this path.
if (require.main === module) runWorkflow('tickler-forward-filters', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, waitForSaveSentinel, waitForTicklerTable };
