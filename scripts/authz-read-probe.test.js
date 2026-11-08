/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const p = require('./lib/authz-read-probe');
test('authorization verdict requires application evidence and never substitutes WAF or login errors', () => {
  for (const status of [401, 403, 405]) {
    assert.equal(p.classify({status, fromApp: true}), 'refused');
    assert.equal(p.classify({status, fromApp: false}), 'error');
  }
  assert.equal(p.classify({status: 302, location: '/carlos/securityError'}), 'refused');
  assert.equal(p.classify({status: 302, location: '/carlos/login.jsp'}), 'error');
  assert.equal(p.classify({status: 500, length: 100}), 'error');
  assert.equal(p.classify({status: 200, lead: 'Error Page'}), 'error');
  assert.equal(p.classify({status: 200, length: 0}), 'empty');
  assert.equal(p.classify({status: 200, length: 100}), 'served');
  assert.equal(p.refusedHead({status: 405, fromApp: true}), true);
  assert.equal(p.refusedHead({status: 405, fromApp: false}), false);
  assert.equal(p.refusedHead({status: 405}), false);
  assert.equal(p.refusedByApp({status: 403, fromApp: false}), false);
});

test('a live session does not make an edge HEAD rejection an application refusal', async () => {
  const baseUrl = new URL('http://127.0.0.1:8080/carlos');
  const calls = [];
  const context = {
    cookies: async () => [{name: 'JSESSIONID', value: 'FAKE-session', domain: '127.0.0.1', path: '/carlos', expires: -1}],
    request: {
      async fetch(url, options) {
        calls.push({url, method: options.method});
        if (url.endsWith('/status/SessionHeartbeat')) {
          return {status: () => 200, json: async () => ({valid: true}), dispose: async () => {}};
        }
        return {status: () => 405, headers: () => ({server: 'nginx'}), body: async () => Buffer.alloc(0)};
      },
    },
  };
  const response = await p.probe(context, `${baseUrl.href}/locked-record`, {method: 'HEAD', baseUrl});
  assert.equal(calls.length, 2);
  assert.equal(new URL(calls[0].url).pathname, '/carlos/status/SessionHeartbeat');
  assert.equal(calls[1].method, 'HEAD');
  assert.equal(response.fromApp, false);
  assert.equal(p.classify(response), 'error');
  assert.equal(p.refusedHead(response), false);
});
