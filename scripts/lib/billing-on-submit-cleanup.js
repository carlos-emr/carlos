/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const assert = require('node:assert/strict');
const quote = value => `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;

/** Delete only the run's appointment versions, including an INSERT whose ID reply was lost. */
function cleanupAppointmentArchives(rows, execute, fixture) {
  const { stamp, providerNo, demographicNo, billingDate, labels, appointmentIds } = fixture;
  assert.match(stamp, /^PW_BILL_\d+$/);
  assert.match(String(providerNo), /^\d+$/);
  assert.match(String(demographicNo), /^\d+$/);
  assert.match(billingDate, /^\d{4}-\d{2}-\d{2}$/);
  const notes = [...new Set(labels)].map(label => {
    assert.ok(['ohip', 'wsib', 'bonus', 'private'].includes(label), 'Unexpected billing fixture label');
    return `${stamp} ${label}`;
  });
  const captured = [...new Set(appointmentIds.map(String))];
  captured.forEach(id => assert.match(id, /^[1-9]\d*$/));
  if (!notes.length) {
    assert.equal(captured.length, 0, 'Captured appointment has no owned label');
    return [];
  }
  const owner = `provider_no=${quote(providerNo)} AND demographic_no=${Number(demographicNo)}`
    + ` AND appointment_date=${quote(billingDate)} AND notes IN (${notes.map(quote).join(',')}) AND reason=notes`;
  const candidates = (ids) => `(${owner})${ids.length ? ` OR appointment_no IN (${ids.join(',')})` : ''}`;
  const check = ([id, appointment, provider, patient, date, note, reason]) => {
    assert.match(String(id), /^[1-9]\d*$/);
    assert.match(String(appointment), /^[1-9]\d*$/);
    assert.ok(String(provider) === String(providerNo) && Number(patient) === Number(demographicNo)
      && date === billingDate && notes.includes(note) && reason === note,
    'Unowned appointment/archive candidate; preserve all fixture rows for review');
  };
  // Validate BOTH tables before the first mutation, including foreign versions of a captured ID.
  const live = rows(`SELECT appointment_no,appointment_no,provider_no,demographic_no,appointment_date,notes,reason FROM appointment WHERE ${candidates(captured)}`);
  live.forEach(check);
  const ownedAppointments = [...new Set([...captured, ...live.map(row => String(row[1]))])];
  const archived = rows(`SELECT id,appointment_no,provider_no,demographic_no,appointment_date,notes,reason FROM appointmentArchive WHERE ${candidates(ownedAppointments)}`);
  archived.forEach(check);
  if (archived.length) {
    const ids = archived.map(row => Number(row[0]));
    execute(`DELETE FROM appointmentArchive WHERE id IN (${ids.join(',')}) AND (${owner})`);
  }
  assert.equal(rows(`SELECT id FROM appointmentArchive WHERE ${candidates(ownedAppointments)}`).length, 0,
    'Owned billing appointment archives remain after cleanup');
  return ownedAppointments;
}

module.exports = { cleanupAppointmentArchives };
