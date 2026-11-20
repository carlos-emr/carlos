/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/demographic/edit.jsp', 'utf8');
const start = jsp.indexOf('function updateEnrolledTo()');
const end = jsp.indexOf('function validateHC()', start);
assert.ok(start >= 0 && end > start);

function update(status, enrolled, confirmResult) {
  const fields = { roster_status: { value: status }, enrolledTo: { value: enrolled }, mrp: { value: '42' } };
  const prompts = [];
  const context = { document: { getElementById: id => fields[id] },
    i18n: { msgConfirmEnrolledToMRP: 'Use MRP?', msgConfirmClearEnrolledTo: 'Clear for {0}?' },
    confirm(message) { prompts.push(message); return confirmResult; } };
  vm.runInNewContext(jsp.slice(start, end) + '\nupdateEnrolledTo();', context);
  return { value: fields.enrolledTo.value, prompts };
}

test('rostering can enroll to the MRP using a native select value', () => {
  assert.deepEqual(update('RO', '', true), { value: '42', prompts: ['Use MRP?'] });
});
test('declining the roster confirmation preserves the enrolled provider', () => {
  assert.equal(update('RO', '17', false).value, '17');
});
test('leaving the roster clears the provider only after confirmation', () => {
  assert.deepEqual(update('NR', '17', true), { value: '', prompts: ['Clear for NR?'] });
  assert.equal(update('NR', '17', false).value, '17');
});
test('unchanged enrollment does not prompt', () => {
  assert.deepEqual(update('RO', '42', true).prompts, []);
  assert.deepEqual(update('NR', '', true).prompts, []);
});
