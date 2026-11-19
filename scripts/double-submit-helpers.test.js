/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {EventEmitter} = require('node:events');
const {watchPosts, verdicts} = require('./lib/double-submit-helpers');

test('double-submit recorder scopes requests and removes its listener on teardown', () => {
  const emitter = new EventEmitter();
  const watcher = watchPosts(emitter, /^\/carlos\/save$/);
  for (const [method, url] of [['GET', 'https://example.test/carlos/save'], ['POST', 'invalid'],
    ['POST', 'https://example.test/carlos/other'], ['POST', 'https://example.test/carlos/save?token=FAKE']]) {
    emitter.emit('request', {method: () => method, url: () => url});
  }
  assert.equal(watcher.seen.length, 1);
  watcher.stop();
  assert.equal(emitter.listenerCount('request'), 0);
});

test('no mode executed and duplicate or lost writes cannot produce a successful verdict', () => {
  assert.throws(() => verdicts('FAKE').finish(), /no double-submit mode ran/);
  const result = verdicts('FAKE');
  result.record('duplicate', 2);
  result.record('missing', 0);
  assert.throws(() => result.finish(), /duplicate=2; missing=0/);
  const passing = verdicts('FAKE');
  passing.record('single', 1);
  passing.finish();
});
