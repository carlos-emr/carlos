/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const h = require('./playwright-harness');

/** Read the actual day view before a fixture INSERT; never select another site or change preferences. */
async function captureScheduleContext(s, {preferredSite} = {}) {
  const page = s.schedule;
  const providerNo = await page.locator('#scheduleLoggedInProviderNo').inputValue();
  const date = await page.locator('#scheduleCurrentDate').inputValue();
  const visible = (await page.locator('#scheduleVisibleProviderNos').inputValue()).split(',');
  h.assert(/^[1-9]\d{0,5}$/.test(providerNo) && providerNo === String(s.provider)
    && visible.filter(value => value === providerNo).length === 1,
  'The native day sheet must display the exact logged-in fixture provider');
  h.assert(/^\d{4}-\d{2}-\d{2}$/.test(date)
    && !Number.isNaN(Date.parse(`${date}T00:00:00Z`))
    && new Date(`${date}T00:00:00Z`).toISOString().slice(0, 10) === date,
  'The native day sheet must expose a valid displayed date');
  h.assert(new URL(page.url()).searchParams.get('weekView') !== 'true',
    'This fixture requires a native day view, not a week view');
  const sites = page.locator('select#site[name="site"]');
  const count = await sites.count();
  h.assert(count === 0 || count === 1, 'The native day sheet has ambiguous site controls');
  let location = '';
  if (count) {
    const options = (await sites.locator('option').evaluateAll(rows => rows.map(option => ({
      value: option.value, disabled: option.disabled || Boolean(option.closest('optgroup')?.disabled),
    })))).filter(option => !option.disabled && option.value && option.value !== 'none');
    h.assert(options.length > 0 && new Set(options.map(option => option.value)).size === options.length,
      'The native day sheet must offer unambiguous named sites');
    const current = await sites.inputValue();
    // All Clinics is only read if it is already the native view. A real offered
    // named site is still mandatory; no preference or dropdown is changed.
    location = current === 'none' ? (preferredSite === undefined ? options[0].value : preferredSite) : current;
    h.assert(typeof location === 'string' && location === location.trim()
      && options.some(option => option.value === location),
      'The fixture site must be offered by the current native day sheet');
    h.assert(s.sql.value(`SELECT COUNT(DISTINCT site.site_id) FROM site
      JOIN providersite membership ON membership.site_id=site.site_id
      WHERE site.status<>0 AND site.name=${h.sqlString(location)}
      AND membership.provider_no=${h.sqlString(providerNo)}`) === '1',
    'The native fixture site must be one active site owned by the displayed provider');
  }
  return Object.freeze({providerNo, date, location, multisites: count === 1});
}

/** Refuse a stale date/provider/site after the schedule reload rather than hiding its missing link. */
async function assertScheduleContext(s, expected) {
  const actual = await captureScheduleContext(s, {preferredSite: expected.location});
  h.assert(['providerNo', 'date', 'location', 'multisites'].every(key => actual[key] === expected[key]),
    'The native schedule context changed after fixture creation');
}

/** Scoped counterpart of gap-billing's seeder; existing shared callers keep their original helper. */
function seedScheduleAppointment(s, context, {time = '14:15:00', tag = 'Billing'} = {}) {
  const {sql, patient, marker, provider} = s;
  h.assert(context && context.providerNo === String(provider) && /^\d{4}-\d{2}-\d{2}$/.test(context.date)
    && typeof context.multisites === 'boolean' && typeof context.location === 'string'
    && (context.multisites ? context.location.length > 0 && context.location !== 'none' : context.location === ''),
  'A captured native appointment context is required');
  h.assert(/^[1-9]\d*$/.test(String(patient)) && /^[1-9]\d*$/.test(String(provider))
    && /^FAKE-PW[0-9a-f]{16}$/.test(marker)
    && /^(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d$/.test(time),
  'The native appointment fixture requires exact owned identities and time');
  const name = `${marker},Workflow`;
  const reason = `${tag} workflow`;
  const browser = s.context.browser();
  const end = sql.value(`SELECT ADDTIME(${h.sqlString(time)}, '00:14:00')`);
  h.assert(/^(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d$/.test(end), 'The native appointment end time is invalid');
  const namespace = `name=${h.sqlString(name)} AND start_time=${h.sqlString(time)}`;
  // A private marker is not permission to remove a row that preceded this fixture.
  h.assert(sql.rows(`SELECT appointment_no FROM appointment WHERE ${namespace}`).length === 0
    && sql.rows(`SELECT id FROM appointmentArchive WHERE ${namespace}`).length === 0,
  'The native appointment fixture namespace already exists');
  let id, attempted = false;
  s.cleanup(() => {
    h.assert(browser.isConnected() === false, 'Appointment cleanup retained: browser close is unconfirmed');
    if (!attempted) return;
    const owner = `provider_no=${h.sqlString(provider)} AND demographic_no=${patient}`
      + ` AND appointment_date=${h.sqlString(context.date)} AND start_time=${h.sqlString(time)}`
      + ` AND end_time=${h.sqlString(end)} AND name=${h.sqlString(name)} AND notes=''`
      + ` AND reason=${h.sqlString(reason)} AND location=${h.sqlString(context.location)}`
      + ` AND creator=${h.sqlString(provider)}`;
    const candidates = ids => `(${namespace})${ids.length ? ` OR appointment_no IN (${ids.join(',')})` : ''}`;
    const captured = id && /^[1-9]\d*$/.test(id) ? [id] : [];
    const fields = 'provider_no,demographic_no,appointment_date,start_time,end_time,name,notes,reason,location,creator';
    const check = row => {
      const [key, appointment, actor, demo, date, startTime, endTime, rowName, notes, rowReason, location, creator] = row;
      h.assert(/^[1-9]\d*$/.test(String(key)) && /^[1-9]\d*$/.test(String(appointment))
        && String(actor) === String(provider) && String(demo) === String(patient) && date === context.date
        && startTime === time && endTime === end && rowName === name && notes === ''
        && rowReason === reason && location === context.location && String(creator) === String(provider),
      'Unowned native appointment/archive candidate; preserve fixture rows for review');
    };
    // Read and validate BOTH tables before the first DELETE. Captured IDs also
    // expose a foreign archive version; marker lookup recovers a lost INSERT reply.
    const live = sql.rows(`SELECT appointment_no,appointment_no,${fields} FROM appointment WHERE ${candidates(captured)}`);
    live.forEach(check);
    const appointments = [...new Set([...captured, ...live.map(row => String(row[1]))])];
    const archived = sql.rows(`SELECT id,appointment_no,${fields} FROM appointmentArchive WHERE ${candidates(appointments)}`);
    archived.forEach(check);
    if (archived.length) {
      sql.execute(`DELETE FROM appointmentArchive WHERE id IN (${archived.map(row => row[0]).join(',')}) AND (${owner})`);
    }
    h.assert(sql.rows(`SELECT id FROM appointmentArchive WHERE ${candidates(appointments)}`).length === 0,
      'Owned native appointment archives remain after cleanup');
    if (live.length) {
      sql.execute(`DELETE FROM appointment WHERE appointment_no IN (${live.map(row => row[0]).join(',')}) AND (${owner})`);
    }
    h.assert(sql.rows(`SELECT appointment_no FROM appointment WHERE ${candidates(appointments)}`).length === 0,
      'The owned native appointment remains after cleanup');
  });
  attempted = true;
  id = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
      demographic_no, program_id, notes, reason, location, resources, type, style, billing, status, createdatetime,
      updatedatetime, creator, remarks, urgency)
    VALUES (${h.sqlString(provider)}, ${h.sqlString(context.date)}, ${h.sqlString(time)}, ${h.sqlString(end)}, ${h.sqlString(name)}, ${patient},
      0, '', ${h.sqlString(reason)}, ${h.sqlString(context.location)}, '', '', '', '', 't', NOW(), NOW(), ${h.sqlString(provider)}, '', '');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), 'The native appointment fixture was not created');
  return id;
}

module.exports = {captureScheduleContext, assertScheduleContext, seedScheduleAppointment};
