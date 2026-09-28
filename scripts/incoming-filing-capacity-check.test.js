/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {validateFilingRequest, cleanupFiling} = require('./lib/incoming-filing-capacity-check');
const owned = {name: 'FAKE-PW123.pdf', patient: '123', description: 'FAKE-PW123 incoming filing'};
function request(extra = {}, header = 'bounded-v1', method = 'POST') {
  const body = new URLSearchParams({method: 'addIncomingDocument', pdfName: owned.name, demog: owned.patient,
    documentDescription: owned.description, queueId: '1', pdfDir: 'File', 'CSRF-TOKEN': 'secret', ...extra});
  return {postData: () => body.toString(), method: () => method, headers: () => ({'x-carlos-incoming-filing': header})};
}
test('owned request guard preserves the exact encoded form body', () => {
  const req = request(); assert.equal(validateFilingRequest(req, owned), req.postData());
});
for (const [key, value] of Object.entries({method: 'addDocument', pdfName: 'other.pdf', demog: '124',
  documentDescription: 'other', queueId: '2', pdfDir: 'Fax', 'CSRF-TOKEN': ''})) {
  test(`filing guard refuses changed ${key} before transmission`, () => {
    assert.throws(() => validateFilingRequest(request({[key]: value}), owned));
  });
}
test('filing guard refuses duplicate targets and missing contract', () => {
  const req = request(); const duplicate = {...req, postData: () => req.postData() + '&demog=123'};
  assert.throws(() => validateFilingRequest(duplicate, owned));
  assert.throws(() => validateFilingRequest(request({}, '', 'POST'), owned));
  assert.throws(() => validateFilingRequest(request({}, 'bounded-v1', 'GET'), owned));
});
test('cleanup refuses changed patient ownership before reading or deleting child rows', () => {
  let touched = false;
  const session = {patient: '123', marker: 'FAKE-PW123', provider: '999998', sql: {
    value: () => '0', rows: () => {touched = true; return [];}, execute: () => {touched = true;},
  }};
  assert.throws(() => cleanupFiling(session, owned.description, '/tmp'));
  assert.equal(touched, false);
});
test('cleanup refuses a document linked to another patient before deleting any data', () => {
  let calls = 0, deleted = false;
  const session = {patient: '123', marker: 'FAKE-PW123', provider: '999998', sql: {
    value: () => ++calls === 1 ? '1' : '1', rows: () => [['42', owned.name]], execute: () => {deleted = true;},
  }};
  assert.throws(() => cleanupFiling(session, owned.description, '/tmp'));
  assert.equal(deleted, false);
});
