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
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.join(__dirname, 'demographic-add-playwright-checks.js'), 'utf8');
const cleanup = script.slice(script.indexOf('function cleanupSyntheticPatient()'), script.indexOf('// The password file'));

function fixture({ capturedId = '3009', parentOwned = true } = {}) {
  const tables = new Set(['admission', 'demographicArchive', 'demographiccust', 'demographic']);
  const queries = [];
  const context = { assert, mysqlDefaults: {}, createdDemographicNo: capturedId,
    fixtureLastName: 'PLAYWRIGHT-ADD-123456', fixtureFirstName: 'Check', sql(query) {
      queries.push(query);
      if (/^SELECT demographic_no/.test(query)) return parentOwned ? '3009' : '';
      if (/^SELECT COUNT\(\*\) FROM demographic/.test(query)) {
        return query.includes('last_name=') ? String(Number(parentOwned)) : String(Number(tables.has('demographic')));
      }
      const match = /^DELETE FROM (\w+) WHERE (demographic_no|client_id)=3009\b/.exec(query);
      assert(match, 'A cleanup delete must target only this owned patient');
      if (match[1] === 'demographic') {
        assert.equal(tables.has('demographiccust'), false, 'The owned custom-data child must be removed before its parent');
        assert(query.includes("last_name='PLAYWRIGHT-ADD-123456'"));
      }
      tables.delete(match[1]);
      return '';
    } };
  vm.runInNewContext(cleanup + '\ncleanupSyntheticPatient();', context);
  return { tables, queries };
}

test('saved patient cleanup removes custom-data child before the owned parent', () => {
  assert.equal(fixture().tables.size, 0);
});
test('partial save before the patient id is captured still removes its owned custom data', () => {
  assert.equal(fixture({ capturedId: null }).tables.size, 0);
});
test('changed patient identity refuses every deletion', () => {
  assert.throws(() => fixture({ parentOwned: false }), /fixture identity changed/);
});
test('a failure before any save deletes no unrelated data', () => {
  const result = fixture({ capturedId: null, parentOwned: false });
  assert.equal(result.tables.size, 4);
  assert.equal(result.queries.filter((query) => query.startsWith('DELETE')).length, 0);
});
