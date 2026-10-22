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
 * first program so the booking typeahead can find it. Before opening a popup, capture its exact native
 * slot and session-hashed monitor baseline. After confirmed browser closure, remove only new monitors
 * for that slot/session/login provider and live marker-owned patient; verify them before removing
 * marker/provider appointment rows and the captured admission. Any uncertainty retains the parent.
 */
const h = require('./lib/playwright-harness');
const { markOpener, typeAutocomplete } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { documentChain, openerState } = require('./lib/popup-opener-helpers');
const { appointmentMonitorCleanup } = require('./lib/appointment-page-monitor-cleanup');

const TIMEOUT = 30000;

function positiveId(value, label) {
  const id = String(value);
  h.assert(/^[1-9]\d*$/.test(id) && id.length <= 10 && BigInt(id) <= 2147483647n,
    `The owned ${label} identity is invalid`);
  return id;
}

// Read the actual native slot before opening it: the first script in that popup already writes a lock.
function nativeSlot(onclick, baseUrl) {
  const match = String(onclick || '').match(/['"]([^'"]*\/appointment\/addappointment\?[^'"]+)['"]/);
  h.assert(match, 'The native slot has no Add Appointment URL');
  const text = match[1].replace(/\\(?:u([0-9a-f]{4})|x([0-9a-f]{2})|([\\/]))/gi,
    (_, unicode, hex, escaped) => escaped || String.fromCharCode(parseInt(unicode || hex, 16)));
  h.assert(!text.includes('\\'), 'The native slot URL has unsupported escaping');
  const base = new URL(baseUrl);
  const url = new URL(text, base);
  h.assert(url.origin === base.origin && url.pathname === `${base.pathname.replace(/\/$/, '')}/appointment/addappointment`,
    'The native slot is outside the configured application');
  const get = key => {
    h.assert(url.searchParams.getAll(key).length === 1, `The native slot has an ambiguous ${key}`);
    return url.searchParams.get(key);
  };
  const provider = positiveId(get('provider_no'), 'slot provider');
  const year = get('year'), month = get('month'), day = get('day');
  h.assert(/^\d{4}$/.test(year) && /^\d{1,2}$/.test(month) && /^\d{1,2}$/.test(day), 'The native slot date is invalid');
  const date = `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
  const parsed = new Date(`${date}T12:00:00Z`);
  h.assert(Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date, 'The native slot date is invalid');
  const start = get('start_time');
  let duration = get('duration');
  h.assert(/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(start), 'The native slot time is invalid');
  if (duration === '') {
    // A slot with no template code carries no duration, and Add Appointment then uses the provider's slot length
    // (everyMin). The same URL's end_time is start + that length - 1, with minutes not carried past 59.
    const end = /^(\d{1,2}):(\d{1,3})$/.exec(get('end_time'));
    h.assert(end, 'The native slot end time is invalid');
    duration = String(Number(end[1]) * 60 + Number(end[2]) - (Number(start.slice(0, 2)) * 60 + Number(start.slice(3))) + 1);
  }
  h.assert(/^[1-9]\d{0,3}$/.test(duration), 'The native slot time is invalid');
  const endMinutes = Number(start.slice(0, 2)) * 60 + Number(start.slice(3)) + Number(duration) - 1;
  h.assert(endMinutes < 1440, 'The native slot duration crosses midnight');
  const end = `${String(Math.floor(endMinutes / 60)).padStart(2, '0')}:${String(endMinutes % 60).padStart(2, '0')}`;
  return { provider, date, start, end, pageId: `${provider}|${date}|${start}|${end}` };
}

function installOwnedDaySheetCleanup(s) {
  const { sql, marker, context } = s;
  const patient = positiveId(s.patient, 'patient');
  const provider = positiveId(s.provider, 'login provider');
  h.assert(/^FAKE-PW[0-9a-f]{16}$/.test(marker), 'The fixture marker is invalid');
  const ownerSql = `SELECT 1 FROM demographic WHERE demographic_no=${patient}
    AND last_name=${h.sqlString(marker)} AND provider_no=${h.sqlString(provider)}`;
  const owner = () => h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
    AND last_name=${h.sqlString(marker)} AND provider_no=${h.sqlString(provider)}`) === '1', 'Patient fixture ownership changed');
  const children = () => sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient})
    + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient})
    + (SELECT COUNT(*) FROM admission WHERE client_id=${patient})`);
  let ready = false, slot, monitors, admission;
  const observedMonitors = new Set();
  s.cleanup(() => {
    const browser = context.browser();
    h.assert(browser && !browser.isConnected(), 'Day-sheet fixtures retained: browser closure is unconfirmed');
    owner();
    h.assert(ready, 'Day-sheet fixtures retained: the initial child baseline is unconfirmed');
    // Keep the live marker parent and all appointment evidence until monitor cleanup succeeds.
    if (monitors) monitors.cleanup();
    if (observedMonitors.size) {
      h.assert(sql.value(`SELECT COUNT(*) FROM PageMonitor WHERE id IN (${[...observedMonitors].join(',')})`) === '0',
        'A captured appointment monitor remains or changed ownership');
    }
    owner();
    if (slot) {
      for (const table of ['appointmentArchive', 'appointment']) {
        sql.execute(`DELETE FROM ${table} WHERE demographic_no=${patient}
          AND provider_no=${h.sqlString(slot.provider)} AND reason=${h.sqlString(marker)} AND EXISTS (${ownerSql})`);
      }
    }
    if (admission) sql.execute(`DELETE FROM admission WHERE id=${admission} AND client_id=${patient}
      AND provider_no=${h.sqlString(provider)} AND EXISTS (${ownerSql})`);
    owner();
    h.assert(children() === '0', 'Owned day-sheet rows remain; foreign or uncertain rows were retained');
  });
  owner();
  h.assert(children() === '0', 'A new day-sheet fixture already has appointment or admission rows');
  ready = true;
  return {
    admissionCreated(id) { admission = positiveId(id, 'admission'); },
    async rememberSlot(onclick) {
      h.assert(!slot, 'The fixture already selected a booking slot');
      slot = nativeSlot(onclick, s.config.baseUrl);
      let snapshot = true;
      monitors = appointmentMonitorCleanup({ provider: slot.provider, date: slot.date, baseUrl: s.config.baseUrl,
        sql(statement) {
          const ids = /^SELECT id FROM PageMonitor WHERE /.test(statement);
          const count = /^SELECT COUNT\(\*\) FROM PageMonitor WHERE /.test(statement);
          const deletion = /^DELETE FROM PageMonitor WHERE /.test(statement);
          h.assert(ids || count || deletion, 'Unexpected appointment monitor cleanup query');
          const scoped = `${statement} AND pageId=${h.sqlString(slot.pageId)}
            AND providerNo=${h.sqlString(provider)} AND EXISTS (${ownerSql})`;
          if (deletion) { sql.execute(scoped); return ''; }
          const rows = sql.rows(scoped);
          h.assert(rows.every(row => Array.isArray(row) && row.length === 1), 'Appointment monitor evidence is malformed');
          if (count) {
            h.assert(rows.length === 1 && /^\d+$/.test(String(rows[0][0])), 'Appointment monitor count is malformed');
          } else {
            const values = rows.map(row => positiveId(row[0], 'appointment monitor'));
            h.assert(new Set(values).size === values.length, 'Appointment monitor identities are duplicated');
            if (snapshot) {
              h.assert(!values.length, 'The native slot already has an owned-session monitor; it was retained');
              const session = statement.match(/SHA2\(session,256\)='([0-9a-f]{64})'/);
              h.assert(session, 'The appointment session hash evidence is unavailable');
              console.log(`FIXTURE popup-opener-day-sheet monitor-scope ${JSON.stringify({patient, marker,
                slotProvider:slot.provider, loginProvider:provider, pageId:slot.pageId, sessionSha256:session[1], baselineIds:values})}`);
            } else {
              values.forEach(id => observedMonitors.add(id));
              if (values.length) console.log(`FIXTURE popup-opener-day-sheet monitor-ids ${JSON.stringify({patient, marker, ids:values})}`);
            }
          }
          return rows.map(row => row.join('\t')).join('\n');
        },
      });
      try { await monitors.remember(context); } finally { snapshot = false; }
      return slot;
    },
  };
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const chain = documentChain(s.context);
  const day = s.schedule;
  const owned = installOwnedDaySheetCleanup(s);
  // The booking typeahead searches only patients admitted to one of the login's programs.
  const program = sql.value(`SELECT program_id FROM program_provider WHERE provider_no=${h.sqlString(s.provider)}
    ORDER BY program_id LIMIT 1`);
  if (!/^[1-9]\d*$/.test(program || '')) throw new h.SkipCheck('The test login belongs to no program, so no patient is bookable');
  owned.admissionCreated(sql.value(`INSERT INTO admission (client_id,program_id,provider_no,admission_date,
      admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)
    VALUES (${patient},${program},${h.sqlString(s.provider)},NOW(),0,0,'current',NOW()); SELECT LAST_INSERT_ID()`));
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
    const slot = await owned.rememberSlot(await slots.last().getAttribute('onclick'));
    booking = await s.popup(day, slots.last(), 'add-appointment');
    await booking.locator('form#addappt #keyword').waitFor({ state: 'visible' });
    const actual = await booking.locator('form#addappt').evaluate(form => Object.fromEntries(
      ['provider_no', 'appointment_date', 'start_time', 'end_time'].map(name => [name, form.elements.namedItem(name).value])));
    h.assert(actual.provider_no === slot.provider && actual.appointment_date === slot.date
      && actual.start_time === slot.start && actual.end_time === slot.end, 'The opened native booking slot differs from its captured cleanup scope');
    const linked = await typeAutocomplete(booking, '#keyword', marker, { option: marker.slice(0, 12), hidden: '#demographic_no' });
    h.assert(linked === patient, 'The typeahead linked another patient');
    await booking.locator('#reason').fill(marker);
  });

  await s.step('Add books the appointment, closes the popup and the day sheet refreshes itself to show it', async () => {
    await closesAndRefreshes(booking, () => booking.locator('#addButton').click({ noWaitAfter: true }), 'Add Appointment');
    const booked = sql.rows(`SELECT appointment_no FROM appointment WHERE demographic_no=${patient} AND reason=${h.sqlString(marker)}`);
    h.assert(booked.length === 1 && /^[1-9]\d*$/.test(booked[0][0] || ''), 'The booking did not write exactly one appointment for the owned patient');
    appointmentNo = booked[0][0];
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
module.exports = { workflow, installOwnedDaySheetCleanup, nativeSlot };
