/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { assertOAuthResourceResult } = require('./authz-rest-sweep-playwright-checks');

for (const reason of ['restricted_endpoint', 'insufficient_scope', 'blocked_endpoint']) {
  test(`OAuth privilege sweep rejects outer policy denial: ${reason}`, () => {
    const result = { status: 403, fromApp: true, text: reason, json: null };
    assert.throws(() => assertOAuthResourceResult('receptionist', 'forms/allEForms', result, false), /service privilege refusal/);
    assert.throws(() => assertOAuthResourceResult('full', 'forms/allEForms', result, true), /control must reach the service/);
  });
}

test('OAuth privilege sweep accepts service responses and rejects missing mappers', () => {
  assertOAuthResourceResult('full', 'forms/allEForms', { status: 200, fromApp: true, json: {} }, true);
  assertOAuthResourceResult('receptionist', 'forms/allEForms', { status: 403, fromApp: true, text: '' }, false);
  assertOAuthResourceResult('receptionist', 'pharmacies/', { status: 403, fromApp: true, text: 'Access denied' }, false);
  for (const status of [200, 401, 500]) {
    assert.throws(() => assertOAuthResourceResult('receptionist', 'forms/allEForms', { status, fromApp: true, text: '' }, false));
  }
  assert.throws(() => assertOAuthResourceResult('full', 'forms/allEForms', { status: 200, fromApp: false, json: null }, true));
});
