/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { withProviderCleanup } = require('./billing-bc-simulation-encoding-playwright-checks');

test('provider deactivation and page close run after a successful simulation', async () => {
  const events = [];
  await withProviderCleanup(async () => events.push('simulation'),
    async () => events.push('deactivate'), async () => events.push('close'));
  assert.deepEqual(events, ['simulation', 'deactivate', 'close']);
});

test('a setup failure still deactivates the saved provider and preserves its original error', async () => {
  const failure = new Error('setup failed after saving provider');
  const events = [];
  await assert.rejects(withProviderCleanup(async () => { throw failure; },
    async () => events.push('deactivate'), async () => events.push('close')), error => error === failure);
  assert.deepEqual(events, ['deactivate', 'close']);
});

test('a cache eviction failure cannot report a successful simulation', async () => {
  const failure = new Error('cache eviction failed');
  let closed = false;
  await assert.rejects(withProviderCleanup(async () => {}, async () => { throw failure; },
    async () => { closed = true; }), error => error === failure);
  assert.equal(closed, true);
});

test('scenario, deactivation and page-close failures remain visible together', async () => {
  const failures = ['simulation failed', 'cache eviction failed', 'page close failed'].map(message => new Error(message));
  await assert.rejects(withProviderCleanup(...failures.map(error => async () => { throw error; })), error => {
    assert(error instanceof AggregateError);
    assert.deepEqual(error.errors, failures);
    for (const failure of failures) assert(error.message.includes(failure.message));
    return true;
  });
});
