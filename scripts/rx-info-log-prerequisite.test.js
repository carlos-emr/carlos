/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const { SkipCheck } = require('./lib/playwright-harness');
const { workflow } = require('./gap-encounter-rx-drug-info-on-host-playwright-checks');

test('Rx Info cannot pass or start its UI work without the required server-log source', async () => {
  const keys = ['LAB_UPLOAD_JOURNAL_UNIT', 'LAB_UPLOAD_SERVER_LOG'];
  const before = keys.map(key => [key, process.env[key]]);
  for (const key of keys) delete process.env[key];
  try {
    const untouched = new Proxy({}, { get() { throw new Error('UI or fixture work started before checking its prerequisite'); } });
    await assert.rejects(workflow(untouched), error => error instanceof SkipCheck && /server log/.test(error.message));
  } finally {
    for (const [key, value] of before) {
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
  }
});
