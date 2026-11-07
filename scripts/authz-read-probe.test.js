/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const p = require('./lib/authz-read-probe');
test('authorization verdict requires application evidence and never substitutes WAF or login errors', () => {
  for (const status of [401, 403]) {
    assert.equal(p.classify({status, fromApp: true}), 'refused');
    assert.equal(p.classify({status, fromApp: false}), 'error');
  }
  assert.equal(p.classify({status: 302, location: '/carlos/securityError'}), 'refused');
  assert.equal(p.classify({status: 302, location: '/carlos/login.jsp'}), 'error');
  assert.equal(p.classify({status: 500, length: 100}), 'error');
  assert.equal(p.classify({status: 200, lead: 'Error Page'}), 'error');
  assert.equal(p.classify({status: 200, length: 0}), 'empty');
  assert.equal(p.classify({status: 200, length: 100}), 'served');
  assert.equal(p.refusedHead({status: 405}), true);
  assert.equal(p.refusedByApp({status: 403, fromApp: false}), false);
});
