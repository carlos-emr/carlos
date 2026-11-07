/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {renalInputFixture} = require('./lib/renal-input-fixture');
test('renal fixture registers cleanup first and preserves clinical measurement values as bound parameters', () => {
  const calls = []; let cleanup;
  const sql = {rows: () => [], execute: (...args) => calls.push(args)};
  const fixture = renalInputFixture({sql, patient: '17', provider: '900001', marker: 'FAKE-owned',
    cleanup: action => { cleanup = action; }});
  assert.equal(typeof cleanup, 'function');
  assert.equal(calls.length, 0);
  fixture.apply({weight: '80', cr: '100'});
  const inserts = calls.filter(([query]) => query.startsWith('INSERT INTO measurements('));
  assert.deepEqual(inserts.map(([, values]) => values[3]), ['80', '100']);
  assert.ok(inserts.every(([query, values]) => query.includes('?') && values[1] === '17' && values[6].startsWith('2025-')));
  calls.length = 0;
  cleanup();
  assert.deepEqual(calls.map(([query]) => query), ['DELETE FROM measurements WHERE demographicNo=17']);
});
