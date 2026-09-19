/* SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const { assertPatientText } = require('./demographic-label-content-playwright-checks');
test('label content assertion tolerates PDF line wrapping but rejects wrong or missing patient data', () => {
  assert.doesNotThrow(() => assertPatientText('FAKE-\nFixture\n42 O\'Neil & Test Lane', 'FAKE-Fixture', "42 O'Neil & Test Lane"));
  assert.throws(() => assertPatientText('OTHER Person 42 Test Lane', 'FAKE-Fixture', null), /patient name/);
  assert.throws(() => assertPatientText('FAKE-Fixture wrong address', 'FAKE-Fixture', '42 Test Lane'), /address/);
  assert.throws(() => assertPatientText('', 'FAKE-Fixture', null), /patient name/);
});
