/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {failureMark, consumeExpectedFailure, consumeAbortedFetch} = require('./lib/concurrency-support');
test('expected concurrency refusal consumes only its scoped response and keeps earlier failures', () => {
  const old = {status: 500, method: 'GET', url: 'https://example.test/old'};
  const recorder = {badResponses: [old], consoleIssues: [], requestFailures: []};
  const mark = failureMark(recorder);
  recorder.badResponses.push({status: 409, method: 'POST', url: 'https://example.test/save?id=1'});
  consumeExpectedFailure(recorder, mark, {status: 409, path: /^\/save$/});
  assert.deepEqual(recorder.badResponses, [old]);
  recorder.badResponses.push({status: 500, method: 'POST', url: 'https://example.test/save'});
  assert.throws(() => consumeExpectedFailure(recorder, mark, {status: 409, path: /^\/save$/}), /Expected/);
  assert.equal(recorder.badResponses.length, 2);
});
test('aborted fetch cleanup preserves unrelated network failures', () => {
  const recorder = {badResponses: [], consoleIssues: [], requestFailures: []};
  const mark = failureMark(recorder);
  const unrelated = {resourceType: 'fetch', errorText: 'ERR_CONNECTION_RESET', url: 'https://example.test/save'};
  recorder.requestFailures.push(unrelated, {resourceType: 'fetch', errorText: 'ERR_ABORTED', url: 'https://example.test/save'});
  consumeAbortedFetch(recorder, mark, /^\/save$/);
  assert.deepEqual(recorder.requestFailures, [unrelated]);
});
