/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const { createHash } = require('node:crypto');

/** Remove only new booking locks for this fixture's own browser session/provider/date. */
function appointmentMonitorCleanup({ sql, provider, date, baseUrl }) {
  if (!/^\d+$/.test(provider) || !/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error('Invalid appointment lock cleanup scope');
  const sessions = new Map();
  const scope = hash => `pageName='addappointment' AND pageId LIKE '${provider}|${date}|%' AND SHA2(session,256)='${hash}'`;
  const ids = text => text ? text.split(/\s+/).map(value => {
    if (!/^\d+$/.test(value)) throw new Error('Invalid appointment lock identity');
    return value;
  }) : [];
  return {
    async remember(context) {
      const cookie = (await context.cookies(String(baseUrl))).filter(item => item.name === 'JSESSIONID')
        .sort((left, right) => (right.path || '').length - (left.path || '').length)[0];
      if (!cookie?.value) throw new Error('Cannot establish the appointment fixture session for lock cleanup');
      // Keep only the hash: neither subprocess arguments nor diagnostic errors expose a session cookie.
      const hash = createHash('sha256').update(cookie.value).digest('hex');
      if (!sessions.has(hash)) sessions.set(hash, new Set(ids(sql(`SELECT id FROM PageMonitor WHERE ${scope(hash)}`))));
    },
    cleanup() {
      let removed = 0;
      for (const [hash, baseline] of sessions) {
        const added = ids(sql(`SELECT id FROM PageMonitor WHERE ${scope(hash)}`)).filter(id => !baseline.has(id));
        if (!added.length) continue;
        sql(`DELETE FROM PageMonitor WHERE id IN (${added.join(',')}) AND ${scope(hash)}`);
        if (sql(`SELECT COUNT(*) FROM PageMonitor WHERE id IN (${added.join(',')}) AND ${scope(hash)}`) !== '0') {
          throw new Error('The fixture could not remove its own appointment booking locks');
        }
        removed += added.length;
      }
      sessions.clear();
      return removed;
    },
  };
}
/** Always attempt lock cleanup and retain both errors if row and lock cleanup fail. */
function finishAppointmentCleanup(deleteRows, deleteMonitors) {
  const errors = [];
  try { deleteRows(); } catch (error) { errors.push(error); }
  try { deleteMonitors(); } catch (error) { errors.push(error); }
  if (errors.length === 1) throw errors[0];
  if (errors.length > 1) throw new AggregateError(errors, 'Appointment fixture row and booking-lock cleanup failed');
}
module.exports = { appointmentMonitorCleanup, finishAppointmentCleanup };
