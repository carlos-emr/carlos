#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const { preflight } = require('./pathnet-status-playwright-checks');
const { SkipCheck } = require('./lib/playwright-harness');

for (const value of [undefined, 'no', '']) {
  test(`disabled or unconfirmed PathNet (${String(value)}) skips before querying fixtures`, async () => {
    const sql = { value() { assert.fail('Disabled integration must not query or seed fixtures'); } };
    await assert.rejects(preflight({ sql, env: { PATHNET_LABS: value } }), SkipCheck);
  });
}

test('enabled PathNet still skips when a legacy table is missing', async () => {
  await assert.rejects(preflight({ sql: { value: () => '0' }, env: { PATHNET_LABS: 'yes' } }), SkipCheck);
});

test('enabled PathNet checks all six tables before running assertions', async () => {
  const queries = [];
  await preflight({ sql: { value(query) { queries.push(query); return '1'; } }, env: { PATHNET_LABS: ' yes ' } });
  assert.equal(queries.length, 6);
});
