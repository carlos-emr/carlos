/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { assertOwnedLock, sessionsForCookie, assertRecoveryRows, recordCleanupFailures } = require('./lib/native-note-lock-checks');
const owned = { patient: '1001', note: 0, provider: '1002', sessions: sessionsForCookie('session.routeA') };
const row = ['19', '1001', '0', '1002', 'session.routeA'];

test('native lock ownership accepts exactly the observed full session or its core', () => {
  assert.strictEqual(assertOwnedLock([row], owned), row);
  assert.deepEqual(assertOwnedLock([[...row.slice(0, 4), 'session']], owned), [...row.slice(0, 4), 'session']);
  assert.throws(() => assertOwnedLock([[...row.slice(0, 4), 'session.routeB']], owned), /ownership tuple/);
});
for (const [label, index, value] of [['patient', 1, '1003'], ['note', 2, '1'], ['provider', 3, '1003'], ['session', 4, 'foreign']]) {
  test(`native lock ownership refuses a foreign ${label} before cleanup or recovery`, () => {
    const altered = row.slice(); altered[index] = value;
    assert.throws(() => assertOwnedLock([altered], owned), /ownership tuple/);
  });
}
test('native lock ownership refuses missing, duplicate and malformed stored identities', () => {
  assert.throws(() => assertOwnedLock([], owned), /exactly one/);
  assert.throws(() => assertOwnedLock([row, row], owned), /exactly one/);
  assert.throws(() => assertOwnedLock([row.slice(1)], owned), /exactly one/);
  assert.throws(() => assertOwnedLock([['0', ...row.slice(1)]], owned), /ownership tuple/);
});
test('native lock ownership preserves the explicit legacy minus-one note sentinel', () => {
  assert.deepEqual(assertOwnedLock([['20', '1001', '-1', '1002', 'session']], { ...owned, note: -1 }),
    ['20', '1001', '-1', '1002', 'session']);
});
test('native lock session evidence requires an actual nonempty cookie', () => {
  for (const missing of [null, undefined, '']) assert.throws(() => sessionsForCookie(missing), /actual session cookie/);
});

const columns = ['id', 'demographic_no', 'note_id', 'provider_no', 'session_id', 'ip_address', 'lock_acquired'];
const held = [row.concat(['127.0.0.1', '2026-10-25 10:00:00']),
  ['20', '1001', '55', '1003', 'foreign-session', '127.0.0.2', '2026-10-25 10:01:00']];
const recovery = () => held.map((value, index) => index === 0 ? value.map((field, at) => at === 4 ? 'next.route' : field) : value.slice());
const expected = { columns, id: '19', sessions: sessionsForCookie('next.route') };
test('native recovery permits only the selected row browser-session change', () => {
  assertRecoveryRows(held, recovery(), expected);
});
for (const [label, rowIndex, fieldIndex] of [['foreign IP', 1, 5], ['foreign acquisition time', 1, 6],
  ['owned IP', 0, 5], ['owned acquisition time', 0, 6], ['foreign session', 1, 4]]) {
  test(`native recovery rejects changed ${label}`, () => {
    const changed = recovery(); changed[rowIndex][fieldIndex] = 'unexpected';
    assert.throws(() => assertRecoveryRows(held, changed, expected), /foreign row|metadata/);
  });
}
test('native recovery rejects extra, missing and duplicate lock rows', () => {
  assert.throws(() => assertRecoveryRows(held, [...recovery(), ['21', ...held[1].slice(1)]], expected), /added or removed/);
  assert.throws(() => assertRecoveryRows(held, recovery().slice(0, 1), expected), /added or removed/);
  assert.throws(() => assertRecoveryRows(held, [recovery()[0], recovery()[0]], expected), /duplicate/);
});
test('native cleanup retains the original failed step and cause alongside cleanup failures', () => {
  const original = new Error('original assertion', { cause: new Error('original cause') });
  original.failedStep = 'original step';
  const priorCause = original.cause; const cleanup = new Error('cleanup failure');
  recordCleanupFailures(original, [cleanup]);
  assert.equal(original.failedStep, 'original step');
  assert.equal(original.message, 'original assertion');
  assert.deepEqual(original.cause.errors, [priorCause, cleanup]);
});
test('native cleanup errors still fail an otherwise successful workflow', () => {
  assert.throws(() => recordCleanupFailures(null, [new Error('cleanup-only')]), AggregateError);
  recordCleanupFailures(null, []);
});
