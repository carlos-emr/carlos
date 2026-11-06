/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {ownedAppointment} = require('./lib/mutation-families');
test('mutation fixture uses the inserted appointment identity and never accepts a missing key', () => {
  const session = {provider: '900001', patient: '17', marker: 'FAKE-owned', sql: {value: () => '42'}};
  assert.equal(ownedAppointment(session, {date: '2026-11-06', start: '10:00', text: 'FAKE-owned'}), '42');
  for (const id of ['0', '', '42;DELETE', '-1']) {
    session.sql.value = () => id;
    assert.throws(() => ownedAppointment(session, {date: '2026-11-06', start: '10:00', text: 'FAKE-owned'}), /owned numeric id/);
  }
});
