/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { removeOwnedHl7Labs, LOCK_NOT_SHARED } = require('./lab-forwarding-rules-playwright-checks');

/*
 * removeOwnedHl7Labs is the one place that removes the rows an HL7 lab leaves behind, for five checks. The routing lock
 * (providerLabRoutingLock) is keyed by the lab number alone, and a document is routed in the same number space
 * (providerLabRouting.lab_type = 'DOC'), so the lock is deleted only when no other lab type routes the same number.
 * These tests give it a recording stub of the harness client; the statements are proved against MariaDB by the checks.
 */

function recordingSql({ locksLeft = '0' } = {}) {
  const executed = [];
  const values = [];
  return {
    executed, values,
    rows() { return []; },
    value(query) { values.push(query); return query.includes('providerLabRoutingLock') ? locksLeft : '0'; },
    execute(query) { executed.push(query); },
  };
}

test('shouldKeepALockRow_thatAnotherLabTypeAlsoRoutes', () => {
  const sql = recordingSql();
  removeOwnedHl7Labs(sql, ['501', '502']);
  const deletion = sql.executed.find((query) => query.includes('DELETE FROM providerLabRoutingLock'));
  assert.ok(deletion, 'the lab\'s lock row is deleted');
  assert.match(deletion, /DELETE FROM providerLabRoutingLock WHERE lab_no IN \(501,502\) AND NOT EXISTS \(SELECT 1 FROM providerLabRouting other\s+WHERE other\.lab_no=providerLabRoutingLock\.lab_no AND other\.lab_type<>'HL7'\)/,
    'a lock row that a document (or any other lab type) is also routed under is not this lab\'s to delete');
  assert.ok(deletion.includes(LOCK_NOT_SHARED));
  assert.ok(deletion.indexOf("DELETE FROM providerLabRouting WHERE lab_type='HL7'") < deletion.indexOf('DELETE FROM providerLabRoutingLock'),
    'the lab\'s own HL7 routing goes first, so only another lab type\'s routing can keep the lock');
});

test('shouldAssertOnlyTheLockRowsItOwns_afterDeleting', () => {
  const sql = recordingSql();
  removeOwnedHl7Labs(sql, ['501']);
  const count = sql.values.find((query) => query.includes('FROM providerLabRoutingLock'));
  assert.ok(count, 'the removal is asserted');
  assert.ok(count.includes(`(SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no IN (501) AND ${LOCK_NOT_SHARED})`),
    'the assertion counts the same rows the delete is allowed to remove, so a shared lock does not fail the cleanup');
});

test('shouldFailTheCleanup_whenAnOwnedLockRowRemains', () => {
  const sql = recordingSql({ locksLeft: '1' });
  sql.value = (query) => (query.includes('providerLabRoutingLock') ? '1' : '0');
  assert.throws(() => removeOwnedHl7Labs(sql, ['501']), /The synthetic lab rows were not all removed/);
});

test('shouldIgnoreANumberThatIsNotALabNumber_andTouchNothingForNone', () => {
  const sql = recordingSql();
  removeOwnedHl7Labs(sql, []);
  removeOwnedHl7Labs(sql, ['0', 'x', '', '-1', "1; DROP TABLE hl7TextInfo"]);
  assert.deepEqual(sql.executed, [], 'only positive integers are keyed on');
});
