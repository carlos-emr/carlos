/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {throwawayLoginFixture} = require('./lib/throwaway-login-fixture');
test('login fixture validates ownership before SQL and does not mutate during construction', () => {
  let calls = 0;
  const sql = {value: () => { calls++; throw new Error('unexpected SQL'); }};
  for (const marker of ['not-owned', 'FAKE-PW1', 'FAKE-PW0123456789abcdeg']) {
    assert.throws(() => throwawayLoginFixture({sql, marker}), /run marker/);
  }
  const fixture = throwawayLoginFixture({sql, marker: 'FAKE-PW0123456789abcdef', provider: '1', testUser: 'FAKE'});
  assert.equal(fixture.username, 'FAKEPW0123456789abcdef');
  assert.equal(fixture.providerNo, null);
  assert.equal(calls, 0);
});
test('missing source login refuses before creating a throwaway identity', () => {
  const statements = [];
  const fixture = throwawayLoginFixture({sql: {value: query => { statements.push(query); return '0'; }},
    marker: 'FAKE-PW0123456789abcdef', provider: '1', testUser: 'FAKE'});
  assert.throws(() => fixture.create(), /no single security row/);
  assert.equal(statements.length, 1);
  assert.equal(fixture.providerNo, null);
});
