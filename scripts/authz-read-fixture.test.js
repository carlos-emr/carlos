/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {cleanupAll} = require('./lib/authz-read-fixture');

test('authorization fixture cleanup runs all actions while preserving primary and later failures', () => {
  const calls = [], primary = new Error('FAKE primary');
  assert.throws(() => cleanupAll(
    () => { calls.push('first'); throw primary; },
    () => { calls.push('second'); throw new Error('FAKE secondary'); },
    () => calls.push('last')),
  error => error === primary && /secondary/.test(error.message));
  assert.deepEqual(calls, ['first', 'second', 'last']);
});

test('empty and successful authorization teardown remain safe', () => {
  cleanupAll();
  const calls = [];
  cleanupAll(() => calls.push(1), () => calls.push(2));
  assert.deepEqual(calls, [1, 2]);
});
