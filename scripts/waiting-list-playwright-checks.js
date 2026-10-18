#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Waiting list workflow, entered the way clinic staff enter it:
 *   Report ▸ Waiting List (waitinglist/SetupDisplayWaitingList) ▸ Create List
 *   (waitinglist/WLEditWaitingListNameAction) creates the list for the login's group;
 *   Schedule ▸ Search ▸ Master Record ▸ Edit ▸ Waiting List section ▸ Update Record
 *   (adds the patient; the update page auto-posts waitinglist/Add2WaitingList)
 *   Master Record ▸ Waiting List (waitinglist/SetupDisplayPatientWaitingList) ▸ remove
 *   (confirm() ▸ POST waitinglist/RemoveFromWaitingList in a self-closing popup that
 *   reloads the opener), and the note edited again from the Master Record
 *   (DemographicUpdate ▸ WLWaitingListUtil.updateWaitingListRecord).
 * Asserts, after every step, the `waitingListName` / `waitingList` rows that reached
 * MariaDB (one current row per edit, the previous one marked history, the removal
 * marking all of them history), the Master Record offering the list with an editable
 * note and date while DEMOGRAPHIC_WAITING_LIST=true (issue #4229), list names
 * printed as text, a list archived after the form was drawn refused (HTTP 409) by
 * both write actions before anything is written, the manager and the patient
 * waiting-list page rendering the current row, a cancelled confirm() changing
 * nothing, and the mutators refusing GET and a tokenless POST without writing.
 * Fixtures: the synthetic patient from lib/workflow-session.js, the list the check
 * creates through Create List, and a second list for the login's group seeded by SQL
 * whose name carries HTML markup (the packaged WAF rightly refuses markup in a form
 * post, so that name cannot be typed in). Cleanup deletes the owned waitingList and
 * waitingListName rows and asserts they are gone.
 * SCREENSHOT_DIR, when set, receives one full-page screenshot per screen.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md §2.3 waiting-list.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const MASTER_LINK = '#appt_table a[href*="SetupDisplayPatientWaitingList"]';
const LIST_SELECT = 'select[name="list_id"]';
const NOTE_INPUT = 'input[name="waiting_list_note"]';
const DATE_INPUT = 'input[name="waiting_list_referral_date"]';
const REPORT_ENTRY = '#waitingListReport';

/** The Master Record's Edit toggle reveals the form in place (no request). */
async function openEditForm(master) {
  const editButton = master.locator('#editBtn');
  h.assert(await editButton.count() > 0, 'The Master Record offers no Edit control (needs _demographic write rights)');
  await editButton.scrollIntoViewIfNeeded().catch(() => {});
  await editButton.click();
  await master.locator('#editDemographic').waitFor({ state: 'visible' });
  await master.locator('#updateButton').waitFor({ state: 'visible' });
}

/**
 * Update Record posts DemographicUpdate; when the patient joins a list the result
 * page auto-submits Add2WaitingList, and every path ends on DemographicEdit.
 */
async function saveMasterRecord(master) {
  const save = master.locator('#updateButton input[type="submit"]').first();
  h.assert(await save.count() > 0, 'The edit form offers no "Update Record" control');
  await ui.clickAndAwaitReload(master, save, { timeout: 20000, label: 'Update Record' });
  await master.waitForURL(/\/demographic\/DemographicEdit/, { timeout: 20000 });
  await master.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  await h.assertNotErrorPage(master, 'the Master Record after Update Record');
}

/** Report ▸ Waiting List: the Report index entry opens the manager in a popup. */
async function openWaitingListManager(s) {
  const report = await s.context.newPage();
  await report.goto(h.appUrl(s.config.baseUrl, '/report/ViewReportindex'), { waitUntil: 'domcontentloaded', timeout: 20000 });
  await h.assertNotErrorPage(report, 'the Report index');
  const entry = report.locator(REPORT_ENTRY);
  h.assert(await entry.count() === 1, 'The Report index offers no Waiting List entry while DEMOGRAPHIC_WAITING_LIST=true');
  const manager = await s.popup(report, entry, 'waiting-list-manager');
  await manager.waitForURL(/\/waitinglist\/SetupDisplayWaitingList/, { timeout: 20000 });
  await h.assertNotErrorPage(manager, 'the waiting-list manager');
  return { report, manager };
}

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const shot = (page, name) => h.screenshot(page, s.config.screenshotDir, `waiting-list-${name}`);
  const listName = `${marker} list`;
  const markupName = `${marker} <b>markup</b> "&`;
  let group;
  let listId;
  let markupId;
  const currentRows = () => sql.rows(`SELECT note, DATE(onListSince), position FROM waitingList
    WHERE listID=${listId} AND demographic_no=${patient} AND is_history='N' ORDER BY id`);
  const allRows = () => JSON.stringify(sql.rows(`SELECT id, note, is_history FROM waitingList
    WHERE listID=${listId} AND demographic_no=${patient} ORDER BY id`));
  const rowCount = (history) => sql.value(`SELECT COUNT(*) FROM waitingList WHERE listID=${listId}
    AND demographic_no=${patient} AND is_history=${h.sqlString(history)}`);
  const ownedNames = `${h.sqlString(listName)}, ${h.sqlString(markupName)}`;

  s.cleanup(() => {
    // The names carry the run's unique marker, so this matches only this run's lists,
    // including one Create List stored before a later assertion failed.
    const ids = sql.rows(`SELECT ID FROM waitingListName WHERE name IN (${ownedNames})`).map(row => row[0]);
    for (const id of ids) {
      h.assert(/^[1-9]\d*$/.test(id), 'An owned waiting-list name has an unexpected ID');
      sql.execute(`DELETE FROM waitingList WHERE listID=${id} AND demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM waitingList WHERE listID=${id}`) === '0',
        'Owned waiting-list rows were not removed');
    }
    sql.execute(`DELETE FROM waitingListName WHERE name IN (${ownedNames})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM waitingListName WHERE name IN (${ownedNames})`) === '0',
      'The owned waiting-list names were not removed');
    // Master Record Update also writes these support rows. runWorkflow removes
    // demographicExt/demographicArchive, but these two need their own cleanup.
    sql.execute(`DELETE FROM demographicExtArchive WHERE demographic_no=${patient};
      DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${patient})
      +(SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient})`) === '0',
      'The owned Master Record extension archive or custom fields remain');
  });

  await s.step('Report ▸ Waiting List ▸ Create List creates a current list for the login group', async () => {
    // Every waiting-list screen scopes names to the login's schedule group
    // (ProviderPreference.myGroupNo): without one there is nothing to offer.
    group = sql.value(`SELECT COALESCE(myGroupNo, '') FROM ProviderPreference WHERE providerNo=${h.sqlString(provider)}`);
    if (group === '') throw new h.SkipCheck('The test login has no schedule group preference (ProviderPreference.myGroupNo), so no waiting list can be offered');
    const { report, manager } = await openWaitingListManager(s);
    await shot(manager, 'manager');
    const create = manager.locator('a', { hasText: /create list/i });
    h.assert(await create.count() === 1, 'The waiting-list manager offers no Create List link (it is shown to admin roles)');
    const editor = await s.popup(manager, create, 'waiting-list-name-editor');
    await editor.waitForURL(/\/waitinglist\/WLEditWaitingListNameAction/, { timeout: 20000 });
    await h.assertNotErrorPage(editor, 'the waiting-list name editor');
    await editor.locator('#wlNewName').fill(listName);
    await ui.clickAndAwaitReload(editor, editor.locator('#wlNewName ~ input[type="submit"]'),
      { timeout: 20000, label: 'Create List Save' });
    await h.assertNotErrorPage(editor, 'the waiting-list name editor after Save');
    h.assert(/created successfully/i.test(await editor.locator('.alert').innerText()),
      'Create List did not report that the list was created');
    listId = sql.value(`SELECT ID FROM waitingListName WHERE name=${h.sqlString(listName)}
      AND group_no=${h.sqlString(group)} AND provider_no=${h.sqlString(provider)} AND is_history='N'`);
    h.assert(/^[1-9]\d*$/.test(listId), 'Create List did not store one current list name for the login group');
    h.assert((await editor.locator(`#selectedWL option[value="${listId}"]`).textContent()).trim() === listName,
      'The name editor does not offer the created list for renaming');
    await shot(editor, 'create-list');
    await editor.close();
    await manager.close();
    await report.close();
  });

  await s.step('the Master Record shows the Waiting List link and an editable list, note and date (#4229)', async () => {
    markupId = sql.value(`INSERT INTO waitingListName (name, group_no, provider_no, create_date, is_history)
      VALUES (${h.sqlString(markupName)}, ${h.sqlString(group)}, ${h.sqlString(provider)}, NOW(), 'N'); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(markupId), 'The markup-named list fixture was not created');
    // The Master Record was drawn before either list existed, and it decides the link,
    // the options and whether the note and date are editable at render time.
    await s.master.reload({ waitUntil: 'domcontentloaded', timeout: 20000 });
    await s.master.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await h.assertNotErrorPage(s.master, 'the reloaded Master Record');
    await openEditForm(s.master);
    const problems = [];
    if (await s.master.locator(MASTER_LINK).count() !== 1) problems.push('shows no Waiting List link');
    if (!(await s.master.locator(NOTE_INPUT).first().isEditable())) problems.push('renders the waiting-list note read-only');
    if (!(await s.master.locator(DATE_INPUT).first().isEditable())) problems.push('renders the date of request read-only');
    const firstOption = (await s.master.locator(`${LIST_SELECT} option`).first().textContent()).trim();
    if (/create waiting list name first/i.test(firstOption)) problems.push(`asks to create a list first (${JSON.stringify(firstOption)})`);
    h.assert(!problems.length, `With DEMOGRAPHIC_WAITING_LIST=true and current lists for the login group, the Master Record ${problems.join(', ')}`);
    const option = s.master.locator(`${LIST_SELECT} option[value="${listId}"]`);
    h.assert(await option.count() === 1, 'The Master Record waiting-list select does not offer the created list');
    h.assert((await option.textContent()).trim() === listName, 'The created list is offered under another name');
    const markupOption = s.master.locator(`${LIST_SELECT} option[value="${markupId}"]`);
    h.assert(await markupOption.count() === 1, 'The Master Record does not offer the second current list');
    h.assert((await markupOption.textContent()).trim() === markupName, 'A list name with markup is not printed as literal text');
    h.assert(await markupOption.locator('b').count() === 0, 'A list name\'s markup created an element in the page');
    await s.master.locator('#waitingListTable').scrollIntoViewIfNeeded();
    await shot(s.master, 'master-record-edit');
  });

  const firstNote = `${marker} first "quoted" note & detail`;
  const secondNote = `${marker} note edited again`;
  await s.step('Update Record with a list, note and date adds the patient to the list', async () => {
    // The generic synthetic patient has no postal code; give this owned patient one so
    // the form's address validation lets Update Record through.
    const postal = s.master.locator('form[name="updatedelete"] input[name="postal"]');
    if (await postal.count() > 0 && (await postal.inputValue()).trim() === '') await postal.fill('K1A 0B1');
    await s.master.locator(LIST_SELECT).selectOption(listId);
    await s.master.locator(NOTE_INPUT).first().fill(firstNote);
    await s.master.locator(DATE_INPUT).first().fill('2026-03-04');
    await saveMasterRecord(s.master);
    await expectValue(sql, `SELECT COUNT(*) FROM waitingList WHERE listID=${listId} AND demographic_no=${patient}`, '1',
      'Update Record did not write the waiting-list row');
    h.assert(JSON.stringify(currentRows()) === JSON.stringify([[firstNote, '2026-03-04', '1']]),
      'The waiting-list row did not carry the note, the date of request and position 1');
    await openEditForm(s.master);
    h.assert(await s.master.locator(LIST_SELECT).inputValue() === listId, 'The reopened edit form lost the selected list');
    h.assert(await s.master.locator(NOTE_INPUT).first().inputValue() === firstNote, 'The reopened edit form lost the note (or did not print its quotes and ampersand as text)');
    h.assert((await s.master.locator(DATE_INPUT).first().inputValue()).startsWith('2026-03-04'), 'The reopened edit form lost the date');
  });

  await s.step('a list archived after the form was drawn is refused by both write actions before any write', async () => {
    const fields = await s.master.locator('form[name="updatedelete"]')
      .evaluate(element => Object.fromEntries(new FormData(element)));
    h.assert(fields['CSRF-TOKEN'], 'The stale-submit probe needs the real form CSRF token');
    h.assert(fields.list_id === listId, 'The drawn form does not carry the selected list');
    const patientName = () => sql.value(`SELECT first_name FROM demographic WHERE demographic_no=${patient}`);
    const beforePatient = patientName();
    const beforeRows = allRows();
    const post = (route, form) => s.context.request.post(h.appUrl(s.config.baseUrl, route), { form, maxRedirects: 0 });
    try {
      sql.execute(`UPDATE waitingListName SET is_history='Y' WHERE ID=${listId}`);
      const update = await post('/demographic/DemographicUpdate',
        { ...fields, first_name: 'Stale submission', waiting_list_note: `${marker} stale note` });
      h.assert(update.status() === 409, `DemographicUpdate answered ${update.status()} instead of 409 for an archived list`);
      h.assert(patientName() === beforePatient, 'The refused update still changed the patient record');
      const confirmation = await post('/waitinglist/Add2WaitingList', {
        'CSRF-TOKEN': fields['CSRF-TOKEN'], listId, demographicNo: patient,
        waitingListNote: `${marker} stale confirmation`, onListSince: '2026-03-04',
      });
      h.assert(confirmation.status() === 409, `Add2WaitingList answered ${confirmation.status()} instead of 409 for an archived list`);
      h.assert(allRows() === beforeRows, 'A refused submission changed the waiting-list rows');
      // "--Select Waiting List--" (list_id=0) still saves the demographics alone.
      const demographicsOnly = await post('/demographic/DemographicUpdate', { ...fields, list_id: '0' });
      h.assert(demographicsOnly.status() === 302, `A demographics-only save answered ${demographicsOnly.status()}`);
      h.assert(allRows() === beforeRows, 'A demographics-only save changed the waiting-list rows');
    } finally {
      sql.execute(`UPDATE waitingListName SET is_history='N' WHERE ID=${listId}`);
    }
    await s.master.reload({ waitUntil: 'domcontentloaded', timeout: 20000 });
    await s.master.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await openEditForm(s.master);
  });

  await s.step('editing the note through Update Record keeps one current row and the old one as history', async () => {
    await s.master.locator(NOTE_INPUT).first().fill(secondNote);
    await saveMasterRecord(s.master);
    await expectValue(sql, `SELECT COUNT(*) FROM waitingList WHERE listID=${listId} AND demographic_no=${patient}`, '2',
      'Editing the note did not record a new row');
    h.assert(JSON.stringify(currentRows()) === JSON.stringify([[secondNote, '2026-03-04', '1']]),
      'The edited note is not the single current row');
    h.assert(rowCount('Y') === '1' && sql.value(`SELECT note FROM waitingList WHERE listID=${listId}
      AND demographic_no=${patient} AND is_history='Y'`) === firstNote, 'The previous note was not kept as history');
  });

  await s.step('Report ▸ Waiting List lists the patient with the current note on the chosen list', async () => {
    const { report, manager } = await openWaitingListManager(s);
    await manager.locator('#selectedWL').selectOption(listId);
    await ui.clickAndAwaitReload(manager, manager.locator('input[type="button"][onclick*="goToPage"]'),
      { timeout: 20000, label: 'Generate Report' });
    await manager.waitForURL(url => url.searchParams.get('waitingListId') === listId, { timeout: 20000 });
    await h.assertNotErrorPage(manager, 'the waiting-list report');
    h.assert(await manager.locator('a', { hasText: marker }).count() === 1, 'The waiting-list report does not list the patient once');
    const notes = await manager.locator('textarea[name$="].note"]').evaluateAll(areas => areas.map(area => area.value));
    h.assert(JSON.stringify(notes) === JSON.stringify([secondNote]), 'The waiting-list report does not show the current note');
    await shot(manager, 'manager-report');
    await manager.close();
    await report.close();
  });

  let listPage;
  async function openPatientList() {
    const outcome = await ui.clickOpensPopupOrNavigates(s.master, s.master.locator(MASTER_LINK),
      { context: s.context, recorder: s.recorder, label: 'patient-waiting-list', timeout: 20000 });
    h.assert(!outcome.isPopup, 'The Waiting List link opened a popup instead of navigating the Master Record');
    listPage = outcome.page;
    await listPage.waitForURL(/\/waitinglist\/SetupDisplayPatientWaitingList/, { timeout: 20000 });
  }
  const ownedRow = () => listPage.locator('tr.data').filter({ hasText: marker });

  await s.step('Master Record ▸ Waiting List renders the current row', async () => {
    await openPatientList();
    const row = ownedRow();
    h.assert(await row.count() === 1, 'The patient waiting-list page does not list the owned entry exactly once');
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[0] === listName && cells[1] === '1' && cells[2] === secondNote && cells[3].startsWith('2026-03-04'),
      'The patient waiting-list row does not show the list, position 1, the edited note and the date of request');
    await shot(listPage, 'patient-waiting-list');
  });

  await s.step('a cancelled remove confirm leaves the patient on the list', async () => {
    let opened = false;
    const onPage = () => { opened = true; };
    s.context.on('page', onPage);
    const sentinel = await ui.markOpener(listPage);
    try {
      const dialogs = await h.withExpectedDialogs(listPage, () => ownedRow().locator('a', { hasText: /remove/i }).click(),
        { accept: false });
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && /remove this patient/i.test(dialogs[0].text),
        'remove did not ask for confirmation');
      // Bounded wait for a removal popup: a timeout is the expected no-popup outcome.
      const popup = await s.context.waitForEvent('page', { timeout: 1500 }).catch(error => {
        if (error.name === 'TimeoutError') return null;
        throw error;
      });
      if (popup) opened = true;
    } finally { s.context.off('page', onPage); }
    h.assert(!opened, 'A cancelled confirm still opened the removal popup');
    h.assert(await listPage.evaluate(name => window[name], sentinel.marker) === sentinel.token, // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the sentinel name is a module constant
      'A cancelled confirm reloaded the page');
    h.assert(rowCount('N') === '1', 'A cancelled confirm removed the patient');
    h.assert(await ownedRow().count() === 1, 'A cancelled confirm hid the row');
  });

  await s.step('GET and a tokenless POST to the mutators are refused without writing', async () => {
    const probe = async (route, method) => s.context.request[method](h.appUrl(s.config.baseUrl, route), {
      [method === 'get' ? 'params' : 'form']: { listId, demographicNo: patient, waitingListNote: 'must not save' },
      maxRedirects: 0,
    });
    for (const route of ['/waitinglist/RemoveFromWaitingList', '/waitinglist/Add2WaitingList']) {
      h.assert((await probe(route, 'get')).status() === 405, `${route} accepted GET`);
      h.assert((await probe(route, 'post')).status() === 403, `${route} accepted a POST without a CSRF token`);
    }
    h.assert(rowCount('N') === '1' && rowCount('Y') === '1', 'A refused request changed the waiting list');
  });

  await s.step('confirmed remove posts through the popup, reloads the page and marks every row history', async () => {
    const popupPromise = s.context.waitForEvent('page', { timeout: 20000 });
    // The remove link is href="#", so a frame navigation fires on the click itself;
    // only a new document loses the sentinel, which is what proves the reload.
    const sentinel = await ui.markOpener(listPage);
    const dialogs = await h.withExpectedDialogs(listPage, () => ownedRow().locator('a', { hasText: /remove/i }).click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'remove did not ask for confirmation');
    // removeFromWaitingListResult.jsp reloads the opener and closes itself.
    const popup = await popupPromise;
    if (!popup.isClosed()) await popup.waitForEvent('close', { timeout: 20000 }).catch(() => {});
    h.assert(popup.isClosed(), 'The removal popup did not close itself, so the removal never completed');
    await listPage.waitForFunction(name => window[name] === undefined, sentinel.marker, { timeout: 20000 }); // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the sentinel name is a module constant
    await listPage.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await h.assertNotErrorPage(listPage, 'the reloaded patient waiting-list page');
    h.assert(await ownedRow().count() === 0, 'The removed entry is still listed after the opener reload');
    await expectValue(sql, `SELECT COUNT(*) FROM waitingList WHERE listID=${listId} AND demographic_no=${patient} AND is_history='N'`,
      '0', 'The removal left a current waiting-list row');
    h.assert(rowCount('Y') === '2', 'The removal deleted rows instead of marking them history');
    await ui.clickAndAwaitReload(listPage, listPage.locator('a[href*="DemographicEdit"]').first(), { timeout: 20000, label: 'Back' });
    await listPage.waitForURL(/\/demographic\/DemographicEdit/, { timeout: 20000 });
  });
}

if (require.main === module) runWorkflow('waiting-list', workflow, { openPatient: true });
module.exports = { workflow };
