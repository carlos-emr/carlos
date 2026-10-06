#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Waiting list workflow, entered the way a clinician enters it:
 *   Schedule ▸ Search ▸ Master Record ▸ Edit ▸ Waiting List section ▸ Update Record
 *   (adds the patient; the update page auto-posts waitinglist/Add2WaitingList)
 *   Master Record ▸ Waiting List (waitinglist/SetupDisplayPatientWaitingList) ▸ remove
 *   (confirm() ▸ POST waitinglist/RemoveFromWaitingList in a self-closing popup that
 *   reloads the opener), and the note edited again from the Master Record
 *   (DemographicUpdate ▸ WLWaitingListUtil.updateWaitingListRecord).
 * Asserts, after every step, the `waitingList` rows that reached MariaDB (one
 * current row per edit, the previous one marked history, the removal marking all
 * of them history), the patient waiting-list page rendering the current row, a
 * cancelled confirm() changing nothing, and the mutators refusing GET and a
 * tokenless POST without writing.
 * Fixtures: the synthetic patient from lib/workflow-session.js and one
 * `waitingListName` row for the login's schedule group, seeded by SQL because the
 * list-name editor (waitinglist/WLEditWaitingListNameAction, reached only from
 * waitinglist/SetupDisplayWaitingList) has no navigation entry in this release.
 * Cleanup deletes the owned waitingList and waitingListName rows and asserts.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md §2.3 waiting-list.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const MASTER_LINK = '#appt_table a[href*="SetupDisplayPatientWaitingList"]';
const LIST_SELECT = 'select[name="list_id"]';
const NOTE_INPUT = 'input[name="waiting_list_note"]';
const DATE_INPUT = 'input[name="waiting_list_referral_date"]';

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

/**
 * Verify waiting-list availability, exact text, history and protected removal
 * through the real Master Record workflow using only owned fixture rows.
 * @param {object} s Authenticated workflow session with SQL and cleanup helpers.
 * @returns {Promise<void>} Resolves after every UI and database assertion passes.
 */
async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  let listId;
  const listName = `${marker} <b>LIST</b> "&`;
  const currentRows = () => sql.rows(`SELECT note, DATE(onListSince), position FROM waitingList
    WHERE listID=${listId} AND demographic_no=${patient} AND is_history='N' ORDER BY id`);
  const rowCount = (history) => sql.value(`SELECT COUNT(*) FROM waitingList WHERE listID=${listId}
    AND demographic_no=${patient} AND is_history=${h.sqlString(history)}`);

  s.cleanup(() => {
    if (!listId) return;
    sql.execute(`DELETE FROM waitingList WHERE listID=${listId} AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM waitingList WHERE listID=${listId}`) === '0',
      'Owned waiting-list rows were not removed');
    sql.execute(`DELETE FROM waitingListName WHERE ID=${listId} AND name=${h.sqlString(listName)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM waitingListName WHERE ID=${listId}`) === '0',
      'The owned waiting-list name was not removed');
  });

  await s.step('seed an owned list name for the login group and the Master Record offers it', async () => {
    // edit-form-clinical.jsp lists names with findCurrentByGroup(myGroupNo): a
    // login without a group preference can never see a list, seeded or not.
    const group = sql.value(`SELECT COALESCE(myGroupNo, '') FROM ProviderPreference WHERE providerNo=${h.sqlString(provider)}`);
    if (group === '') throw new h.SkipCheck('The test login has no schedule group preference (ProviderPreference.myGroupNo), so the Master Record lists no waiting lists');
    listId = sql.value(`INSERT INTO waitingListName (name, group_no, provider_no, create_date, is_history)
      VALUES (${h.sqlString(listName)}, ${h.sqlString(group)}, ${h.sqlString(provider)}, NOW(), 'N'); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(listId), 'The waiting-list name fixture was not created');
    // The Master Record was rendered before the name existed; it decides both the
    // Waiting List link and the editable note at render time.
    await s.master.reload({ waitUntil: 'domcontentloaded', timeout: 20000 });
    await s.master.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await h.assertNotErrorPage(s.master, 'the reloaded Master Record');
    await openEditForm(s.master);
    const option = s.master.locator(`${LIST_SELECT} option[value="${listId}"]`);
    h.assert(await option.count() === 1, 'The Master Record waiting-list select does not offer the seeded list');
    h.assert((await option.textContent()).trim() === listName, 'The seeded list is offered under another name');
    h.assert(await option.locator('b').count() === 0, 'List name markup created an option child element');
    // Both are gated by the same wLReadonly flag; report both at once.
    const problems = [];
    if (await s.master.locator(MASTER_LINK).count() !== 1) problems.push('shows no Waiting List link');
    if (!(await s.master.locator(NOTE_INPUT).first().isEditable())) problems.push('renders the waiting-list note read-only');
    if (!(await s.master.locator(DATE_INPUT).first().isEditable())) problems.push('renders the date of request read-only');
    h.assert(!problems.length, `The Master Record ${problems.join(', ')} although an active list name exists `
      + `(first list option reads ${JSON.stringify((await s.master.locator(`${LIST_SELECT} option`).first().textContent()).trim())}; `
      + 'edit.jsp and its fragments set wLReadonly whenever DEMOGRAPHIC_WAITING_LIST=true)');
  });

  await s.step('stale archived, moved and out-of-group lists are refused before either write action changes data', async () => {
    const form = s.master.locator('form[name="updatedelete"]');
    const fields = await form.evaluate(element => Object.fromEntries(new FormData(element)));
    h.assert(fields['CSRF-TOKEN'], 'The stale-submit probe needs the real form CSRF token');
    const originalGroup = sql.value(`SELECT myGroupNo FROM ProviderPreference WHERE providerNo=${h.sqlString(provider)}`);
    const otherGroup = `PW${marker.slice(-6)}`;
    const beforePatient = sql.value(`SELECT first_name FROM demographic WHERE demographic_no=${patient}`);
    const beforeRows = sql.rows(`SELECT id, note, is_history FROM waitingList WHERE listID=${listId} AND demographic_no=${patient} ORDER BY id`);
    const cases = [
      {name: 'archived list', change: `UPDATE waitingListName SET is_history='Y' WHERE ID=${listId}`},
      {name: 'moved list', change: `UPDATE waitingListName SET group_no=${h.sqlString(otherGroup)} WHERE ID=${listId}`},
      {name: 'changed provider group', change: `UPDATE ProviderPreference SET myGroupNo=${h.sqlString(otherGroup)} WHERE providerNo=${h.sqlString(provider)}`},
    ];
    for (const scenario of cases) {
      try {
        sql.execute(scenario.change);
        const update = await s.context.request.post(h.appUrl(s.config.baseUrl, '/demographic/DemographicUpdate'), {
          form: {...fields, first_name: 'Stale submission', postal: 'K1A 0B1', list_id: listId,
            waiting_list_note: `${marker} stale`, waiting_list_referral_date: '2026-03-04'}, maxRedirects: 0,
        });
        h.assert(update.status() === 409, `${scenario.name}: DemographicUpdate answered ${update.status()} instead of 409`);
        h.assert(sql.value(`SELECT first_name FROM demographic WHERE demographic_no=${patient}`) === beforePatient,
          `${scenario.name}: rejected update changed the patient record`);
        const confirmation = await s.context.request.post(h.appUrl(s.config.baseUrl, '/waitinglist/Add2WaitingList'), {
          form: {'CSRF-TOKEN': fields['CSRF-TOKEN'], listId, demographicNo: patient,
            waitingListNote: `${marker} stale confirmation`, onListSince: '2026-03-04'}, maxRedirects: 0,
        });
        h.assert(confirmation.status() === 409, `${scenario.name}: confirmation answered ${confirmation.status()} instead of 409`);
        h.assert(JSON.stringify(sql.rows(`SELECT id, note, is_history FROM waitingList WHERE listID=${listId}
          AND demographic_no=${patient} ORDER BY id`)) === JSON.stringify(beforeRows),
        `${scenario.name}: a stale submission changed waiting-list rows`);
      } finally {
        sql.execute(`UPDATE waitingListName SET is_history='N', group_no=${h.sqlString(originalGroup)} WHERE ID=${listId};
          UPDATE ProviderPreference SET myGroupNo=${h.sqlString(originalGroup)} WHERE providerNo=${h.sqlString(provider)}`);
        h.assert(sql.value(`SELECT myGroupNo FROM ProviderPreference WHERE providerNo=${h.sqlString(provider)}`) === originalGroup,
          'Provider group was not restored after the stale-submit probe');
      }
    }
  });

  const firstNote = `${marker} first "quoted" note & detail`;
  const secondNote = `${marker} note edited again`;
  await s.step('Update Record with a list, note and date adds the patient to the list', async () => {
    // The generic synthetic patient has no postal code; satisfy the real form's
    // Canadian address validation before submitting this owned patient's update.
    await s.master.locator('form[name="updatedelete"] input[name="postal"]').fill('K1A 0B1');
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
    h.assert(await s.master.locator(NOTE_INPUT).first().inputValue() === firstNote, 'The reopened edit form lost the note');
    h.assert((await s.master.locator(DATE_INPUT).first().inputValue()).startsWith('2026-03-04'), 'The reopened edit form lost the date');
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
