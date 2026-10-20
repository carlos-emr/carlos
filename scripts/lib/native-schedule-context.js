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
  const name = `${marker},Workflow`;
  const browser = s.context.browser();
  s.cleanup(() => {
    h.assert(browser.isConnected() === false, 'Appointment cleanup retained: browser close is unconfirmed');
    sql.execute(`DELETE FROM appointment WHERE demographic_no=${patient} AND name=${h.sqlString(name)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient}`) === '0',
      'The owned appointment was not removed');
  });
  const end = sql.value(`SELECT ADDTIME(${h.sqlString(time)}, '00:14:00')`);
  const id = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
      demographic_no, program_id, notes, reason, location, resources, type, style, billing, status, createdatetime,
      updatedatetime, creator, remarks, urgency)
    VALUES (${h.sqlString(provider)}, ${h.sqlString(context.date)}, ${h.sqlString(time)}, ${h.sqlString(end)}, ${h.sqlString(name)}, ${patient},
      0, '', ${h.sqlString(`${tag} workflow`)}, ${h.sqlString(context.location)}, '', '', '', '', 't', NOW(), NOW(), ${h.sqlString(provider)}, '', '');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), 'The native appointment fixture was not created');
  return id;
}

module.exports = {captureScheduleContext, assertScheduleContext, seedScheduleAppointment};
