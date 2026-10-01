#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * tickler-forward-preferences — coverage plan §3.4 (`tickler-forward-filters`: forward to another
 * provider, the Assigned To filter, and Preferences ▸ tickler settings).
 *
 * User path: Schedule ▸ preferences icon ▸ "Set Tickler Preferences" (setProviderStaleDate?
 * method=viewTicklerTaskAssignee ▸ form posts to setTicklerPreferences); Schedule ▸ Tickler
 * (tickler/ViewTicklerMain) ▸ Assigned To filter + Create Report (tickler/ListTicklers) ▸ row
 * pencil ▸ edit popup ▸ Assigned To ▸ Update (tickler/EditTickler, the reassignment control);
 * Master Record ▸ Tickler ▸ New Tickler (the preference's effect: the defaulted assignee).
 *
 * Asserted: forwarding an owned tickler to a second active provider changes
 * tickler.task_assigned_to and writes tickler_update rows (the original-state backfill plus the
 * change, each by the forwarding provider); the Assigned To filter then hides the row under the
 * test provider and shows it under the other provider (ListTicklers JSON and the rendered row);
 * forwarding back restores it; saving the "default assignee = <other provider>" preference writes
 * the property row `tickler_task_assignee`, a new tickler form preselects that provider and a
 * save carries it; choosing "Default" again deletes the row. Finally a GET against
 * setTicklerPreferences must not change the stored preference.
 *
 * Fixtures: one tickler seeded by SQL for the owned patient (message carries the marker) plus the
 * one the preference step saves; the preference row is snapshotted before any change. Cleanup
 * deletes the owned ticklers with their comments, updates and attachments, restores the
 * preference snapshot, and asserts both.
 *
 * tickler/ForwardDemographicTickler is deliberately NOT driven here: it only prefills the add
 * form from a document, lab or HRM viewer and has no entry in the tickler list (see the report).
 * Env: the common contract (lib/playwright-harness.js readConfig()); TICKLER_PREFS_DIRECT=true
 * opens the preference form by its own route while the Preferences link points at the wrong action.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const PREFERENCE = 'tickler_task_assignee';

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
  const defaultedMessage = `${marker} saved with the preferred default assignee`;
  const other = sql.rows(`SELECT provider_no, last_name, first_name FROM provider WHERE status='1'
    AND provider_no NOT LIKE '-%' AND provider_no<>${h.sqlString(provider)} ORDER BY last_name, first_name, provider_no LIMIT 1`)[0];
  if (!other) throw new h.SkipCheck('No second active provider exists to forward a tickler to');
  const [otherNo, otherLast, otherFirst] = other;
  const providerName = sql.value(`SELECT CONCAT(last_name, ', ', first_name) FROM provider WHERE provider_no=${h.sqlString(provider)}`);
  const namesOther = name => String(name || '').includes(otherLast) && String(name || '').includes(otherFirst);
  const ownedTicklers = `demographic_no=${patient} AND message LIKE ${h.sqlString(`${marker}%`)}`;
  const preferencePredicate = `provider_no=${h.sqlString(provider)} AND name=${h.sqlString(PREFERENCE)}`;
  const snapshotQuery = `SELECT id, COALESCE(value,''), value IS NULL FROM property WHERE ${preferencePredicate} ORDER BY id`;
  const preferenceSnapshot = sql.rows(snapshotQuery);
  const storedPreference = () => sql.value(`SELECT COALESCE(value,'') FROM property WHERE ${preferencePredicate} ORDER BY id LIMIT 1`);
  const preferenceRows = () => sql.value(`SELECT COUNT(*) FROM property WHERE ${preferencePredicate}`);
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
    const statements = [`DELETE FROM property WHERE ${preferencePredicate}`];
    for (const [id, value, isNull] of preferenceSnapshot) {
      h.assert(/^\d+$/.test(id), 'Invalid preference restore snapshot');
      statements.push(`INSERT INTO property(id,provider_no,name,value) VALUES (${id},${h.sqlString(provider)},${h.sqlString(PREFERENCE)},${isNull === '1' ? 'NULL' : h.sqlString(value)})`);
    }
    sql.execute(`START TRANSACTION;${statements.join(';')};COMMIT`);
    h.assert(JSON.stringify(sql.rows(snapshotQuery)) === JSON.stringify(preferenceSnapshot), 'Tickler preference restore did not match its snapshot');
  });
  const ticklerNo = sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
    VALUES(${patient},${h.sqlString(message)},'A',NOW(),DATE_SUB(CURDATE(),INTERVAL 1 DAY),${h.sqlString(provider)},'Normal',${h.sqlString(provider)});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(ticklerNo), 'Owned tickler was not created');
  const updates = () => sql.value(`SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${ticklerNo}`);

  // The preferences popup is opened from the schedule before the Tickler link, which in the
  // focused schedule mode navigates the schedule tab itself.
  const prefs = await s.popup(s.schedule, s.schedule.getByTitle(/Edit your personal setting/i).first(), 'preferences');
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

  // The preference form, entered through the Preferences popup link. providerpreference.jsp links
  // setProviderStaleDate?method=viewTicklerTaskAssignee, whose "success" result is
  // setNoteStaleDate.jsp, so on the current build the link never renders the tickler form; that is
  // asserted (and fails) unless TICKLER_PREFS_DIRECT=true opens the form by its own route.
  async function openPreferenceForm(label) {
    let settings;
    if (process.env.TICKLER_PREFS_DIRECT === 'true') {
      settings = await s.context.newPage();
      await h.gotoApp(settings, s.config.baseUrl, '/setTicklerPreferences?method=viewTicklerTaskAssignee');
      await h.assertNotErrorPage(settings, label);
    } else {
      const link = prefs.locator('a[href*="method=viewTicklerTaskAssignee"]').first();
      await revealAuditLink(prefs, link, 20000);
      settings = await s.popup(prefs, link, label);
    }
    h.assert(await settings.locator('#taskAssigneeProvider').count() === 1,
      'The "Set Tickler Preferences" link did not open the tickler preference form (it opens '
      + 'setProviderStaleDate?method=viewTicklerTaskAssignee, mapped to setNoteStaleDate.jsp); '
      + 'TICKLER_PREFS_DIRECT=true drives the form by its own route until the link is fixed');
    return settings;
  }

  await s.step('Preferences ▸ Set Tickler Preferences saves the other provider as the default assignee', async () => {
    const settings = await openPreferenceForm('tickler-preferences');
    await settings.locator('#taskAssigneeProvider').check();
    const select = settings.locator('#assigneeSelect');
    await select.waitFor({ state: 'visible' });
    await select.selectOption(otherNo);
    h.assert(await settings.locator('#taskAssignee').inputValue() === otherNo, 'Choosing a provider did not stage it for submission');
    await ui.clickAndAwaitReload(settings, settings.locator('form input[type="submit"]'), { label: 'tickler preference Submit' });
    h.assert(h.pathOnly(settings.url()).endsWith('/setTicklerPreferences'), 'The preference form did not post to setTicklerPreferences');
    await settings.locator('#AlertBanner').waitFor({ state: 'visible', timeout: 20000 });
    h.assert(preferenceRows() === '1' && storedPreference() === otherNo, 'The default assignee preference was not stored');
    await settings.close();
  });

  await s.step('a new tickler form preselects the preferred assignee and the save carries it', async () => {
    const patientList = await s.popup(s.master, s.master.locator('a[onclick*="/tickler/ViewTicklerMain"]').first(), 'patient-tickler-list');
    const add = await s.popup(patientList, patientList.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(), 'tickler-add');
    await add.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
    h.assert(await add.locator('form[name="serviceform"] input[name="demographic_no"]').last().inputValue() === patient, 'The add form did not open for the owned patient');
    h.assert(await add.locator('select[name="task_assigned_to"]').first().inputValue() === otherNo, 'The add form did not default its assignee to the preferred provider');
    await add.locator('textarea[name="ticklerMessage"]').fill(defaultedMessage);
    await add.locator('input[name="xml_appointment_date"]').fill(sql.value('SELECT CURDATE()'));
    await add.locator('input.btn-primary[name="Button"]').first().click();
    await waitForSaveSentinel(add, 'ticklerSubmitFrame', 'tickler-save-ok');
    await expectValue(sql, `SELECT task_assigned_to FROM tickler WHERE demographic_no=${patient} AND message=${h.sqlString(defaultedMessage)}`,
      otherNo, 'The defaulted tickler was not saved to the preferred assignee');
    h.assert(sql.value(`SELECT creator FROM tickler WHERE demographic_no=${patient} AND message=${h.sqlString(defaultedMessage)}`) === provider,
      'The defaulted tickler was not created by the test provider');
    if (!add.isClosed()) await add.close();
  });

  await s.step('choosing Default again removes the preference row', async () => {
    const settings = await openPreferenceForm('tickler-preferences-reset');
    h.assert(await settings.locator('#taskAssigneeProvider').isChecked() && await settings.locator('#assigneeSelect').inputValue() === otherNo,
      'Reopening the preference did not show the stored provider');
    await settings.locator('#taskAssigneeDefault').check();
    await ui.clickAndAwaitReload(settings, settings.locator('form input[type="submit"]'), { label: 'tickler preference Submit' });
    await settings.locator('#AlertBanner').waitFor({ state: 'visible', timeout: 20000 });
    h.assert(preferenceRows() === '0', 'Choosing Default did not delete the preference row');
    await settings.close();
  });

  await s.step('a GET against setTicklerPreferences does not change the stored preference', async () => {
    const before = `${preferenceRows()}:${storedPreference()}`;
    const rejected = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/setTicklerPreferences?method=saveTicklerTaskAssignee&taskAssigneeMRP.value=provider&taskAssigneeSelection.value=${encodeURIComponent(otherNo)}`),
    { maxRedirects: 0 });
    h.assert(rejected.status() === 405, `A GET preference save answered HTTP ${rejected.status()} instead of 405`);
    h.assert(`${preferenceRows()}:${storedPreference()}` === before, 'A GET request changed the stored tickler preference');
  });
}

if (require.main === module) runWorkflow('tickler-forward-preferences', workflow, { openPatient: true });
module.exports = { workflow };
