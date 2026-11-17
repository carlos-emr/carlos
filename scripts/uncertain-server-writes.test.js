/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const writes = require('./lib/uncertain-server-writes');

test('fixture retention follows the browser owner across contexts without affecting another browser', () => {
  const browser = {}, other = {};
  const first = {browser: () => browser}, second = {browser: () => browser};
  assert.equal(writes.hasUncertainWrites(first), false);
  writes.retainFixtures(first);
  assert.equal(writes.hasUncertainWrites(second), true);
  assert.equal(writes.hasUncertainWrites({browser: () => other}), false);
});

test('drain waits for writes added while an earlier dispatch is settling', async () => {
  const owner = {};
  let completeFirst, completeSecond, drained = false;
  const first = new Promise(resolve => { completeFirst = resolve; });
  const second = new Promise(resolve => { completeSecond = resolve; });
  writes.trackWrite(owner, first);
  const drain = writes.drainWrites(owner).then(() => { drained = true; });
  writes.trackWrite(owner, second);
  completeFirst();
  await Promise.resolve();
  assert.equal(drained, false);
  completeSecond();
  await drain;
  assert.equal(drained, true);
});

test('rejected writes settle without discarding uncertain fixture retention', async () => {
  const owner = {};
  writes.retainFixtures(owner);
  writes.trackWrite(owner, Promise.reject(new Error('FAKE lost acknowledgement')));
  await writes.drainWrites(owner);
  assert.equal(writes.hasUncertainWrites(owner), true);
  await writes.drainWrites(null);
});
