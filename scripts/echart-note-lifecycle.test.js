/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const { waitVisible } = require('./echart-note-lifecycle-playwright-checks');

test('note lifecycle visibility failure names the control and retains the Playwright cause', async () => {
  const cause = new Error('locator.waitFor: Timeout 30000ms exceeded.');
  await assert.rejects(waitVisible({ async waitFor() { throw cause; } },
    'Edit signed note: caseNote_note textarea'), error => {
    assert.match(error.message, /^Edit signed note: caseNote_note textarea: locator\.waitFor:/);
    assert.equal(error.cause, cause);
    return true;
  });
});

test('note lifecycle visibility checks wait for a visible control', async () => {
  let options;
  await waitVisible({ async waitFor(value) { options = value; } }, 'Edit link', 123);
  assert.deepEqual(options, { state: 'visible', timeout: 123 });
});
