/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const {cleanupUnusedHctSignatures} = require('./consultation-health-care-team-playwright-checks');
const marker = 'FAKE-PW0123456789abcdef';
function database(owner = '1', left = '0') {
  const reads = [], writes = [];
  return {reads, writes, value(query, parameters) {reads.push({query, parameters}); return reads.length === 1 ? owner : left;},
    execute(query, parameters) {writes.push({query, parameters});}};
}
test('removes only unused consultation signatures of the verified owned patient', () => {
  const sql = database();cleanupUnusedHctSignatures(sql, '42', marker);
  assert.deepEqual(sql.reads[0].parameters, ['42', marker]);
  assert.match(sql.reads[0].query, /demographic_no=\? AND last_name=\?/);
  assert.equal(sql.writes.length, 1);assert.deepEqual(sql.writes[0].parameters, ['42']);
  assert.match(sql.writes[0].query, /demographicId=\? AND ModuleType='CONSULTATION'/);
  for (const table of ['consultationRequests', 'consultationRequestsArchive']) {
    assert.ok(sql.writes[0].query.includes('NOT EXISTS (SELECT 1 FROM ' + table + ' WHERE signature_img=CAST(DigitalSignature.id AS CHAR))'));
  }
  assert.deepEqual(sql.reads[1].parameters, ['42']);
});
test('refuses changed ownership without deleting signatures', () => {
  const sql = database('0');assert.throws(() => cleanupUnusedHctSignatures(sql, '42', marker), /ownership changed/);
  assert.equal(sql.writes.length, 0);
});
test('reports a silently ignored unused-signature deletion', () => {
  assert.throws(() => cleanupUnusedHctSignatures(database('1', '1'), '42', marker), /signatures remain/);
});
test('refuses invalid fixture keys before any database access', () => {
  for (const [patient, name] of [['0', marker], ['42 OR 1=1', marker], ['42', 'another patient']]) {
    const sql = database();assert.throws(() => cleanupUnusedHctSignatures(sql, patient, name), /Invalid HCT/);
    assert.equal(sql.reads.length, 0);assert.equal(sql.writes.length, 0);
  }
});
