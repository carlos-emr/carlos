#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Reference-data caches reach the booking form (issue #5304).
 *
 * The add-appointment form reads two cached reference lists on every load: the active
 * appointment statuses (AppointmentStatusDao.findActive, the `appointmentStatuses` cache)
 * and the reasonCode lookup list with its items (LookupListDao.findByName, the
 * `lookupLists` cache). Since #5304 those caches hold private copies and every read, hit or
 * miss, returns its own copies. This check guards that rework end to end: it drives the edits
 * an administrator makes and asserts each one reaches the form on its next load (the write
 * evicted the cache, and nothing stale is served from it), and that repeated loads before
 * and after each edit agree. No page changes a returned status or list in place, so the copy
 * isolation itself is not observable here; the DAO *CacheIntegrationTest classes prove it.
 *
 * User path: Schedule ▸ Administration ▸ Appointment Status Setting (admin iframe) ▸ Edit /
 * Disable / Enable; Schedule ▸ Administration ▸ Manage Lookup Lists (admin iframe) ▸ add to
 * the reasonCode list / X; Schedule ▸ an empty slot on the day sheet the login lands on ▸ the
 * add-appointment popup, read and closed each time, never booked.
 *
 * Asserted:
 *   - two loads of the form agree, and offer exactly the active statuses and reasonCode items,
 *     with their labels, that the database holds;
 *   - a label and colour saved in the status editor are the ones the form offers next, and
 *     the editor's own list (AppointmentStatusDao.findAll) shows the new label;
 *   - Disable takes the status off the form and Enable puts it back with the edited label;
 *   - editing the label back is reflected, and the row matches its snapshot again;
 *   - a reason added through Manage Lookup Lists is offered by its item id and label, and its
 *     removal takes it off the form.
 *
 * Fixtures: one clinic-wide appointment_status row (an editable active custom status a-f) is
 * snapshotted, changed through the editor and edited back, so the check must not run
 * concurrently with other checks (live wrapper: EXCLUSIVE=1). A failed run first edits the
 * status back through the editor (best effort, so the cache is evicted) and then restores the
 * snapshot by SQL. One marker-labelled LookupListItem on the reasonCode list is added and
 * removed through the UI; a failed run removes it through Manage Lookup Lists (best effort),
 * and cleanup deletes the owned rows and asserts they are gone. Nothing is booked: the check
 * asserts no appointment carries the marker.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {bundleMessage} = require('./lib/throwaway-login-fixture');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {openAdminSection, frameNavigation} = require('./schedule-admin-settings-playwright-checks');
const {settleOperations} = require('./graceful-signal-cancellation');

const TIMEOUT = 20000;
const STATUS_EDITOR = '/appointment/apptStatusSetting?dispatch=view';
const LOOKUP_MANAGER = '/lookupListManagerAction?method=manage';
const REASON_LIST = 'reasonCode';
const EDITED_COLOR = '#123456';
const LABEL = {
  disable: bundleMessage('admin.appt.status.mgr.label.disable', 'Disable'),
  enable: bundleMessage('admin.appt.status.mgr.label.enableAction', 'Enable'),
};

async function workflow(s) {
  const {sql, marker, context, recorder} = s;
  const q = h.sqlString;
  const hex = marker.slice(-16);

  // ---- fixtures ---------------------------------------------------------------------------------
  const statusRow = sql.rows(`SELECT id,status,description,color,active FROM appointment_status
    WHERE editable=1 AND active=1 AND status IN ('f','e','d','b','a') ORDER BY status DESC LIMIT 1`)[0];
  if (!statusRow) throw new h.SkipCheck('No editable active custom appointment status is available');
  const [statusId, statusCode, statusDesc, statusColor] = statusRow;
  h.assert(/^[1-9]\d*$/.test(statusId), 'The chosen appointment status has no id');
  const statusSnapshot = () => JSON.stringify(sql.rows(`SELECT description,color,active FROM appointment_status WHERE id=${Number(statusId)}`));
  const originalStatus = statusSnapshot();
  const editedLabel = `PW${hex}`;

  const listId = sql.value(`SELECT id FROM LookupList WHERE name=${q(REASON_LIST)} AND active=1 ORDER BY id LIMIT 1`);
  if (!/^[1-9]\d*$/.test(listId)) throw new h.SkipCheck('This install has no active reasonCode lookup list');
  const reasonLabel = `${marker} reason`;
  const owned = `lookupListId=${Number(listId)} AND label LIKE ${q(`${marker}%`)}`;

  s.cleanup(() => {
    sql.execute(`UPDATE appointment_status SET description=${q(statusDesc)},color=${q(statusColor)},active=${Number(statusRow[4])}
      WHERE id=${Number(statusId)};
      DELETE FROM LookupListItem WHERE ${owned}`);
    h.assert(statusSnapshot() === originalStatus, 'The appointment status was not restored to its snapshot');
    h.assert(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE ${owned}`) === '0', 'The owned reason code was not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE reason LIKE ${q(`${marker}%`)} OR notes LIKE ${q(`${marker}%`)}`) === '0',
      'An appointment carrying the marker was booked');
  });

  // ---- surfaces ---------------------------------------------------------------------------------
  // The administration panel opens as a popup or in the schedule's own tab, a deployment
  // preference. In the second case the day sheet is reopened in a tab of its own from the exact
  // URL the login landed on (providercontrol renders nothing without those parameters).
  const landing = s.schedule.url();
  const {page: admin, isPopup} = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder, label: 'administration', timeout: TIMEOUT});
  let daySheet = s.schedule;
  if (!isPopup) {
    daySheet = await context.newPage();
    await daySheet.goto(landing, {waitUntil: 'domcontentloaded'});
  }
  await daySheet.locator('a.adhour').first().waitFor({timeout: TIMEOUT});
  await h.assertNotErrorPage(daySheet, 'day sheet');

  /** Opens the add-appointment popup from an empty slot, reads its status and reason lists, closes it. */
  async function bookingForm() {
    // A slot whose template code asks for no booking confirmation (empty confirm argument).
    const slot = daySheet.locator(`a.adhour[onclick*="','',"]`).first();
    h.assert(await slot.count() === 1, 'The day sheet offers no slot that opens the add-appointment form without a prompt');
    const popup = await ui.clickOpensPopup(daySheet, slot, {context, recorder, label: 'add-appointment', timeout: TIMEOUT});
    try {
      await popup.locator('form#addappt').waitFor({timeout: TIMEOUT});
      const status = popup.locator('form#addappt select[name="status"]');
      if (await status.count() !== 1) return {statuses: null, reasons: null};
      const statuses = await status.locator('option').evaluateAll(nodes => nodes.map(o => ({
        value: o.value, text: o.textContent.trim(), style: (o.getAttribute('style') || '').replace(/\s+/g, ''),
      })));
      const reasons = await popup.locator('form#addappt select#reasonCode option').evaluateAll(nodes => nodes.map(o => ({
        value: o.value, text: o.textContent.trim(),
      })));
      return {statuses, reasons};
    } finally {
      await popup.close().catch(() => {});
    }
  }
  const offered = (form, code) => form.statuses.filter(option => option.value === code);

  async function editStatus(desc, color) {
    const frame = await openAdminSection(admin, STATUS_EDITOR, 'table.borderAll');
    await frameNavigation(admin, frame, () => frame.locator(`a[href*="dispatch=modify&id=${statusId}"]`).first().click());
    h.assert(await frame.locator('#apptStatus').inputValue() === statusCode, 'The status edit form opened another status');
    await frame.locator('#apptDesc').fill(desc);
    await frame.locator('#apptColor').fill(color);
    await frameNavigation(admin, frame, () => frame.locator('form input[type="submit"]').click());
    await frame.locator('table.borderAll').waitFor({timeout: TIMEOUT});
    return frame;
  }
  async function toggleStatus(label) {
    const frame = await openAdminSection(admin, STATUS_EDITOR, 'table.borderAll');
    const button = frame.locator(`form:has(input[name="id"][value="${statusId}"]) button`, {hasText: label});
    h.assert(await button.count() === 1, `The status editor offers no ${label} button for the status`);
    await frameNavigation(admin, frame, () => button.click());
  }
  async function lookupManager() {
    return openAdminSection(admin, LOOKUP_MANAGER, `#lookupListItems_${listId}`);
  }
  async function managerPost(frame, action) {
    const response = admin.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/lookupListManagerAction') && r.request().frame() === frame, {timeout: TIMEOUT});
    // Settle both so a click that fails before dispatch cannot leave the waiter to reject unhandled.
    const [, answered] = await settleOperations([Promise.resolve().then(action), response]);
    h.assert(answered.status() === 200, `The lookup list manager answered HTTP ${answered.status()}`);
    await frame.waitForFunction(() => window.jQuery && window.jQuery.active === 0, null, {timeout: TIMEOUT});
  }

  // Best-effort application-layer repairs for a failed run, so the caches are evicted by the
  // same writes the steps use rather than left serving what the SQL teardown overwrote.
  async function repairThroughUi() {
    if (statusSnapshot() !== originalStatus) {
      if (sql.value(`SELECT active FROM appointment_status WHERE id=${Number(statusId)}`) === '0') await toggleStatus(LABEL.enable);
      await editStatus(statusDesc, statusColor);
    }
    const active = sql.rows(`SELECT id FROM LookupListItem WHERE ${owned} AND active=1`).map(([id]) => id);
    if (active.length) {
      const frame = await lookupManager();
      for (const id of active) {
        h.assert(/^[1-9]\d*$/.test(id), 'Owned lookup list item id is invalid');
        await managerPost(frame, () => frame.locator(`#removeLookupListItem_${id}_${listId}`).click());
      }
    }
  }

  // ---- steps ------------------------------------------------------------------------------------
  let baseline;
  await s.step('two loads of the booking form agree and offer the active statuses and reasons the database holds', async () => {
    const first = await bookingForm();
    if (first.statuses === null) {
      throw new h.SkipCheck('The add-appointment form offers no status pull-down (ENABLE_EDIT_APPT_STATUS is off)');
    }
    const second = await bookingForm();
    h.assert(JSON.stringify(second) === JSON.stringify(first), 'A second load of the booking form offered different statuses or reasons');
    const chosen = offered(first, statusCode);
    h.assert(chosen.length === 1 && chosen[0].text === statusDesc.trim(),
      `The booking form does not offer status ${statusCode} once with its stored label`);
    // Compared as sorted sets: ties in the stored order are not part of the contract.
    const sorted = rows => rows.map(row => row.join('\u0000')).sort();
    const storedStatuses = sql.rows('SELECT status,description FROM appointment_status WHERE active=1')
      .map(([code, desc]) => [code, (desc || '').trim()]);
    h.assert(JSON.stringify(sorted(first.statuses.map(o => [o.value, o.text]))) === JSON.stringify(sorted(storedStatuses)),
      'The booking form does not offer exactly the active statuses, with their labels, that the database holds');
    const storedReasons = sql.rows(`SELECT id,COALESCE(label,'') FROM LookupListItem WHERE lookupListId=${Number(listId)} AND active=1`)
      .map(([id, label]) => [id, label.trim()]);
    h.assert(JSON.stringify(sorted(first.reasons.map(o => [o.value, o.text]))) === JSON.stringify(sorted(storedReasons)),
      'The booking form does not offer exactly the active reasonCode items, with their labels, that the database holds');
    h.assert(!first.reasons.some(option => option.text.includes(marker)), 'The booking form already offers the owned reason');
    baseline = first;
  });

  let reasonId;
  try {
    await s.step('a label and colour saved in the status editor are what the booking form offers next', async () => {
      const frame = await editStatus(editedLabel, EDITED_COLOR);
      h.assert(JSON.stringify(sql.rows(`SELECT description,color FROM appointment_status WHERE id=${Number(statusId)}`))
        === JSON.stringify([[editedLabel, EDITED_COLOR]]), 'The status label and colour were not saved');
      h.assert(await frame.locator('tr', {hasText: editedLabel}).count() === 1, 'The status editor list does not show the new label');
      for (let load = 1; load <= 2; load++) {
        const form = await bookingForm();
        const chosen = offered(form, statusCode);
        h.assert(chosen.length === 1 && chosen[0].text === editedLabel,
          `Load ${load} of the booking form offers status ${statusCode} as ${JSON.stringify(chosen.map(o => o.text))}, not the saved label`);
        h.assert(chosen[0].style.toLowerCase().includes(`background-color:${EDITED_COLOR}`),
          `Load ${load} of the booking form does not colour status ${statusCode} with the saved colour`);
        h.assert(form.statuses.length === baseline.statuses.length, 'Editing one status changed how many statuses the form offers');
      }
    });

    await s.step('Disable takes the status off the booking form and Enable puts it back with its edited label', async () => {
      await toggleStatus(LABEL.disable);
      await expectValue(sql, `SELECT active FROM appointment_status WHERE id=${Number(statusId)}`, '0', 'Disable did not deactivate the status');
      const disabled = await bookingForm();
      h.assert(offered(disabled, statusCode).length === 0, `The booking form still offers the disabled status ${statusCode}`);
      h.assert(disabled.statuses.length === baseline.statuses.length - 1, 'Disabling one status removed more or less than that status');
      await toggleStatus(LABEL.enable);
      await expectValue(sql, `SELECT active FROM appointment_status WHERE id=${Number(statusId)}`, '1', 'Enable did not reactivate the status');
      const enabled = await bookingForm();
      const chosen = offered(enabled, statusCode);
      h.assert(chosen.length === 1 && chosen[0].text === editedLabel, `The booking form does not offer the re-enabled status ${statusCode} with its edited label`);
    });

    await s.step('editing the label back is what the booking form offers next', async () => {
      await editStatus(statusDesc, statusColor);
      h.assert(statusSnapshot() === originalStatus, 'Editing back did not restore the status label and colour');
      const form = await bookingForm();
      h.assert(JSON.stringify(form.statuses) === JSON.stringify(baseline.statuses),
        'After editing the status back the booking form does not offer the statuses it offered before');
    });

    await s.step('a reason added through Manage Lookup Lists is offered by the booking form', async () => {
      const frame = await lookupManager();
      await frame.locator(`#lookupListItemLabel_${listId}`).fill(reasonLabel);
      await managerPost(frame, () => frame.locator(`#addLookupListItemButton_${listId}`).click());
      await frame.locator(`#lookupListItems_${listId} li.lookupListItem .label`).filter({hasText: marker}).first().waitFor({timeout: TIMEOUT});
      await expectValue(sql, `SELECT COUNT(*) FROM LookupListItem WHERE ${owned} AND active=1`, '1', 'Exactly one owned reason was not stored');
      reasonId = sql.value(`SELECT id FROM LookupListItem WHERE ${owned}`);
      h.assert(/^[1-9]\d*$/.test(reasonId), 'The stored reason has no id');
      for (let load = 1; load <= 2; load++) {
        const form = await bookingForm();
        const ours = form.reasons.filter(option => option.value === reasonId);
        h.assert(ours.length === 1 && ours[0].text === reasonLabel,
          `Load ${load} of the booking form does not offer the added reason once by its id and label`);
        h.assert(form.reasons.length === baseline.reasons.length + 1, 'Adding one reason changed the other reasons the form offers');
      }
    });

    await s.step('removing the reason through Manage Lookup Lists takes it off the booking form', async () => {
      const frame = await lookupManager();
      const remove = frame.locator(`#removeLookupListItem_${reasonId}_${listId}`);
      h.assert(await remove.count() === 1, 'Manage Lookup Lists does not offer the owned reason for removal');
      await managerPost(frame, () => remove.click());
      await expectValue(sql, `SELECT active FROM LookupListItem WHERE id=${Number(reasonId)}`, '0', 'Removal did not deactivate the owned reason');
      const form = await bookingForm();
      h.assert(!form.reasons.some(option => option.value === reasonId || option.text.includes(marker)),
        'The booking form still offers the removed reason');
      h.assert(JSON.stringify(form) === JSON.stringify(baseline), 'After the removal the booking form does not offer what it offered at the start');
    });
  } catch (error) {
    await repairThroughUi().catch(repair => console.log(`  Best-effort repair through the UI failed: ${repair.message}`));
    throw error;
  } finally {
    if (isPopup && !admin.isClosed()) await admin.close().catch(() => {});
    if (daySheet !== s.schedule && !daySheet.isClosed()) await daySheet.close().catch(() => {});
  }
}

if (require.main === module) runWorkflow('reference-data-cache', workflow, {openPatient: false});
module.exports = {workflow};
