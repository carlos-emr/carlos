/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { clearOwnedPrescriptionRows } = require('./rx-stash-patient-isolation-playwright-checks');

const marker = 'FAKE-PW0123456789abcdef';

test('isolation cleanup removes detached prescription signatures after their owned prescription rows', () => {
  const mutations = [];
  const sql = {
    value(query) {
      assert.match(query, /demographic_no=42/);
      assert.ok(query.includes(marker));
      return '1';
    },
    execute(query) { mutations.push(query); },
  };
  clearOwnedPrescriptionRows(sql, '42', marker);
  assert.equal(mutations.length, 2);
  assert.match(mutations[0], /DELETE FROM prescription/);
  assert.match(mutations[0], /DELETE FROM drugs/);
  assert.ok(mutations[0].includes(marker));
  assert.match(mutations[1], /DELETE FROM DigitalSignature WHERE demographicId=42/);
  assert.match(mutations[1], /moduleType='PRESCRIPTION'/);
  assert.match(mutations[1], /NOT EXISTS[\s\S]*digital_signature_id=DigitalSignature.id/);
});

test('isolation cleanup refuses every child mutation when the synthetic patient was replaced', () => {
  let mutations = 0;
  assert.throws(() => clearOwnedPrescriptionRows({value: () => '0', execute: () => { mutations++; }}, '42', marker),
    /ownership changed/);
  assert.equal(mutations, 0);
});

test('isolation cleanup preserves signatures when prescription cleanup has not succeeded', () => {
  const mutations = [];
  const sql = {
    value: () => '1',
    execute(query) { mutations.push(query); throw new Error('database unavailable'); },
  };
  assert.throws(() => clearOwnedPrescriptionRows(sql, '42', marker), /database unavailable/);
  assert.equal(mutations.length, 1);
  assert.doesNotMatch(mutations[0], /DELETE FROM DigitalSignature/);
});

test('isolation cleanup rejects an unowned marker before querying the database', () => {
  assert.throws(() => clearOwnedPrescriptionRows({value: () => { throw new Error('must not query'); }}, '42', 'other'),
    /Invalid prescription fixture identity/);
});
