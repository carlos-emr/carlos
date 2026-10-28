#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Schedule > Report > Waiting List > patient row Make Appointment > Flip View
// > free slot > Add. Navigation variants use Next Month or the provider picker.
// MANUALLY_CLEANUP_WL=yes must mirror that optional server property, as in
// appointment-waiting-list-prompt; the default asks a removal confirmation.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { prepareWaitingLists } = require('./lib/waiting-list-ui-workflow');

async function workflow(s) {
  const mode = process.env.WAITING_LIST_BOOKING || 'book';
  h.assert(['book', 'navigate', 'provider'].includes(mode), 'WAITING_LIST_BOOKING must be navigate or provider when set');
  const f = await prepareWaitingLists(s, { members: true, providerChoices: mode === 'provider' });
  const reason = `${s.marker} waiting-list booking`;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no=${s.patient} AND reason=${h.sqlString(reason)};
      DELETE FROM appointment WHERE demographic_no=${s.patient} AND reason=${h.sqlString(reason)}`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${s.patient})
      +(SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${s.patient})`) === '0', 'Owned appointment rows remain');
  });
  const beforeMembers = JSON.stringify(s.sql.rows(`SELECT * FROM waitingList WHERE listID=${f.list.id} ORDER BY id`));
  let flip;
  let popup;
  let chosen;
  let selectedProvider;
  async function openSlot() {
    const today = s.sql.value('SELECT CURDATE()');
    const links = flip.locator('[onclick^="t("]');
    // Parse a real rendered slot, then check availability without creating or
    // changing a schedule. Choose a future free slot so no warning is ignored.
    const handlers = await links.evaluateAll(nodes => nodes.map(node => node.getAttribute('onclick')));
    for (let index = handlers.length - 1; index >= 0; index--) {
      const m = handlers[index].match(/^t\((\d+),(\d+),(\d+),'([^']+)'/);
      if (!m) continue;
      const date = `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}`;
      const time = m[4];
      if (date <= today || !/^\d{2}:\d{2}$/.test(time)) continue;
      if (s.sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${h.sqlString(selectedProvider)}
        AND appointment_date=${h.sqlString(date)} AND start_time<=${h.sqlString(time)} AND end_time>${h.sqlString(time)}`) !== '0') continue;
      chosen = { date, time };
      const dialogs = await h.withExpectedDialogs(flip, async () => {
        popup = await s.popup(flip, links.nth(index), 'waiting-list-booking-form');
      });
      h.assert(dialogs.every(dialog => dialog.type === 'confirm'), 'Opening a free slot raised an unexpected alert');
      await popup.locator('#demographic_no').waitFor({ state: 'attached' });
      await popup.waitForLoadState('networkidle');
      return;
    }
    throw new h.SkipCheck('The selected provider has no future free rendered Flip View slot');
  }
  async function assertPatientContext() {
    h.assert(await popup.locator('#demographic_no').inputValue() === s.patient,
      'The waiting-list booking form lost the selected patient');
    h.assert(await popup.locator('[name="provider_no"]').inputValue() === selectedProvider, 'Booking changed the selected provider');
    h.assert(await popup.locator('[name="appointment_date"]').inputValue() === chosen.date, 'Booking changed the selected date');
    h.assert(await popup.locator('[name="start_time"]').inputValue() === chosen.time, 'Booking changed the selected time');
  }
  await s.step('Make Appointment opens Flip View for the selected row patient and provider', async () => {
    const row = f.patientRow(s.patient);
    const providers = await row.locator('[name="selectedProvider"] option').evaluateAll(nodes => nodes.map(node => node.value));
    selectedProvider = providers.includes(s.provider) ? s.provider : providers[0];
    if (!selectedProvider) throw new h.SkipCheck('The waiting-list group offers no appointment provider');
    await row.locator('[name="selectedProvider"]').selectOption(selectedProvider);
    flip = await s.popup(f.manager, row.locator('a[onclick*="popupPage("]'), 'waiting-list-flip-view');
    h.assert(new URL(flip.url()).searchParams.get('demographic_no') === s.patient, 'Flip View opened another patient');
    h.assert(new URL(flip.url()).searchParams.get('provider_no') === selectedProvider, 'Flip View opened another provider');
    await flip.locator('[onclick^="t("]').first().waitFor();
  });
  if (mode === 'navigate') {
    await s.step('Next Month retains the waiting-list patient when a free slot is selected', async () => {
      await ui.clickAndAwaitReload(flip, flip.locator('a[href*="/schedule/FlipView"][href*="startDate="]').last(),
        { timeout: 20000, label: 'Flip View Next Month' });
      await openSlot();
      await assertPatientContext();
    });
    return;
  }
  if (mode === 'provider') {
    await s.step('changing provider retains the waiting-list patient when a free slot is selected', async () => {
      const alternatives = await flip.locator('select[name="provider_no"] option').evaluateAll(nodes => nodes.map(node => node.value));
      const alternative = alternatives.find(value => value && value !== selectedProvider);
      if (!alternative) throw new h.SkipCheck('Flip View needs two offered providers to exercise a provider change');
      selectedProvider = alternative;
      await Promise.all([
        flip.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        flip.locator('select[name="provider_no"]').selectOption(selectedProvider),
      ]);
      await h.assertNotErrorPage(flip, 'Flip View after provider change');
      await openSlot();
      await assertPatientContext();
    });
    return;
  }
  await s.step('a free Flip View slot prefills the selected patient, provider, date and time', async () => {
    await openSlot();
    await assertPatientContext();
  });
  await s.step('Add books the waiting-list patient once while Cancel retains both list members', async () => {
    await popup.locator('#reason').fill(reason);
    const dialogs = await h.withExpectedDialogs(popup, async () => {
      const closed = popup.waitForEvent('close');
      await popup.locator('#addButton').click();
      await closed;
    }, { accept: false });
    const expectedDialogs = (process.env.MANUALLY_CLEANUP_WL || '').toLowerCase() === 'yes' ? 0 : 1;
    h.assert(dialogs.length === expectedDialogs && dialogs.every(d => d.type === 'confirm'
      && d.text === `Click OK to remove patient from the waiting list: ${f.list.name}`), 'Booking raised an unrelated dialog');
    await expectValue(s.sql, `SELECT COUNT(*) FROM appointment WHERE demographic_no=${s.patient}
      AND reason=${h.sqlString(reason)}`, '1', 'The waiting-list booking was not saved exactly once');
    const saved = s.sql.rows(`SELECT provider_no,DATE(appointment_date),TIME(start_time),demographic_no FROM appointment
      WHERE demographic_no=${s.patient} AND reason=${h.sqlString(reason)}`);
    h.assert(JSON.stringify(saved) === JSON.stringify([[selectedProvider, chosen.date, `${chosen.time}:00`, s.patient]]),
      'The saved appointment differs from the selected patient/provider/slot');
    h.assert(JSON.stringify(s.sql.rows(`SELECT * FROM waitingList WHERE listID=${f.list.id} ORDER BY id`)) === beforeMembers,
      'Booking with Cancel changed waiting-list membership');
    await f.manager.reload({ waitUntil: 'domcontentloaded' });
    h.assert(await f.patientRow(s.patient).count() === 1 && await f.patientRow(f.control).count() === 1,
      'Booking lost a waiting-list patient');
  });
}
if (require.main === module) runWorkflow(process.env.WAITING_LIST_BOOKING === 'navigate' ? 'waiting-list-booking-navigation'
  : process.env.WAITING_LIST_BOOKING === 'provider' ? 'waiting-list-booking-provider' : 'waiting-list-booking',
  workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
