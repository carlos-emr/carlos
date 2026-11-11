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
const assert = require('node:assert/strict');
const { removeOwnedPatient } = require('./owned-patient');

// One dedicated UI-created FAKE patient. Share the same completion between the
// normal finally and a signal; a refused/uncertain cleanup is never retried here.
function createOwnedFixtureCleanup({sql, marker, isSqlReady, getPatientId, closeBrowser}) {
  assert.equal(typeof sql, 'function');
  assert.equal(typeof isSqlReady, 'function');
  assert.equal(typeof getPatientId, 'function');
  assert.equal(typeof closeBrowser, 'function');
  let completion;
  return function cleanup() {
    if (!completion) completion = (async () => {
      await closeBrowser();
      if (!isSqlReady()) return;
      const patient = String(getPatientId() || '');
      if (!patient) return;
      // The existing helper verifies the live parent marker before touching any
      // child, removes known chart rows before their parent, and checks zero.
      removeOwnedPatient({value: sql, execute: sql}, patient, marker,
        {extra: [['appointment', 'demographic_no'], ['admission', 'client_id']]});
    })();
    return completion;
  };
}
module.exports = {createOwnedFixtureCleanup};
