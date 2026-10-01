#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Appointment popups -> window.opener.refresh() of the day sheet (risk sweep "lost
 * popup openers"; coverage plan §2.2 scheduling, opener contracts).
 *
 * User path: Schedule (day sheet) ▸ an open time slot (a.adhour) ▸ Add Appointment
 * popup (appointment/addappointment) ▸ patient typeahead ▸ Add (appointmentaddarecord:
 * self.opener.refresh(); self.close()); then the new appointment link ▸ Edit
 * Appointment popup ▸ Delete Appointment (appointmentdeletearecord: same callback).
 * The existing booking checks reload the day sheet themselves after the popup closes,
 * which proves the row and never the callback; here the day sheet must reload BY
 * ITSELF (markOpener sentinel gone, no manual reload) and show / drop the appointment.
 * Asserts the appointment row is written for the owned patient and then deleted (an
 * appointmentArchive copy kept), each popup closes, and the day sheet follows both.
 * Fixtures: the owned FAKE- patient, admitted (one owned admission row) to the login's
 * first program so the booking typeahead can find it; cleanup deletes its appointment,
 * appointmentArchive and admission rows and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { markOpener, typeAutocomplete } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { documentChain, openerState } = require('./lib/popup-opener-helpers');

const TIMEOUT = 30000;

async function workflow(s) {
  const { sql, patient, marker } = s;
  const chain = documentChain(s.context);
  const day = s.schedule;
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no=${patient};
      DELETE FROM appointment WHERE demographic_no=${patient};
      DELETE FROM admission WHERE client_id=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM admission WHERE client_id=${patient})`) === '0',
    'Owned appointment or admission rows were not removed');
  });
  // The booking typeahead searches only patients admitted to one of the login's programs.
  const program = sql.value(`SELECT program_id FROM program_provider WHERE provider_no=${h.sqlString(s.provider)}
    ORDER BY program_id LIMIT 1`);
  if (!/^[1-9]\d*$/.test(program || '')) throw new h.SkipCheck('The test login belongs to no program, so no patient is bookable');
  sql.execute(`INSERT INTO admission (client_id,program_id,provider_no,admission_date,
      admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)
    VALUES (${patient},${program},${h.sqlString(s.provider)},NOW(),0,0,'current',NOW())`);
  /** Run `act`; require the popup to close and the day sheet to reload itself. */
  const closesAndRefreshes = async (popup, act, what) => {
    const sentinel = await markOpener(day);
    const state = await openerState(popup);
    const closed = popup.waitForEvent('close', { timeout: TIMEOUT }).then(() => true, () => false);
    const reloaded = day.waitForEvent('load', { timeout: TIMEOUT }).then(() => true, () => false);
    await act();
    h.assert(await closed, `${what}: the popup did not close (documents: ${chain.describe(popup)})`);
    const refreshed = await reloaded
      && await day.evaluate(name => window[name], sentinel.marker).catch(() => undefined) !== sentinel.token;
    h.assert(refreshed, `${what}: the day sheet was not refreshed by the popup (window.opener ${state}; `
      + `popup documents: ${chain.describe(popup)})`);
  };
  let booking;
  let appointmentNo;

  await s.step('an open slot opens Add Appointment and the typeahead links the owned patient', async () => {
    const slots = day.locator('a.adhour');
    h.assert(await slots.count() > 0, 'The day sheet offers no bookable slot');
    booking = await s.popup(day, slots.last(), 'add-appointment');
    await booking.locator('form#addappt #keyword').waitFor({ state: 'visible' });
    const linked = await typeAutocomplete(booking, '#keyword', marker, { option: marker.slice(0, 12), hidden: '#demographic_no' });
    h.assert(linked === patient, 'The typeahead linked another patient');
    await booking.locator('#reason').fill(marker);
  });

  await s.step('Add books the appointment, closes the popup and the day sheet refreshes itself to show it', async () => {
    await closesAndRefreshes(booking, () => booking.locator('#addButton').click({ noWaitAfter: true }), 'Add Appointment');
    appointmentNo = sql.value(`SELECT appointment_no FROM appointment WHERE demographic_no=${patient} AND reason=${h.sqlString(marker)}`);
    h.assert(/^[1-9]\d*$/.test(appointmentNo || ''), 'The booking did not write exactly one appointment for the owned patient');
    await day.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}&"]`).first().waitFor({ state: 'visible', timeout: TIMEOUT });
  });

  await s.step('Delete Appointment removes it, closes the popup and the day sheet refreshes itself without it', async () => {
    const edit = await s.popup(day, day.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}&"]`).first(), 'edit-appointment');
    await edit.locator('#deleteButton').waitFor({ state: 'visible' });
    await h.withExpectedDialogs(edit, () => closesAndRefreshes(edit,
      () => edit.locator('#deleteButton').click({ noWaitAfter: true }), 'Delete Appointment'), { accept: true });
    await expectValue(sql, `SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointmentNo}`, '0',
      'Delete Appointment left the appointment row');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${appointmentNo}`) !== '0',
      'Delete Appointment kept no appointmentArchive copy');
    h.assert(await day.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}&"]`).count() === 0,
      'The refreshed day sheet still shows the deleted appointment');
  });
}

if (require.main === module) runWorkflow('popup-opener-day-sheet', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
