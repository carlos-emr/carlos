/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const { SkipCheck } = require('./lib/playwright-harness');
const { workflow } = require('./inactive-drug-status-playwright-checks');

test('missing inactive-drug fixtures skip while malformed configured fixtures fail', async () => {
  const keys = ['INACTIVE_DRUG_DIN', 'INACTIVE_DRUG_DATE'];
  const original = keys.map(key => process.env[key]);
  try {
    for (const fixture of [[undefined, '2008-06-10'], ['00000019', undefined], ['', '']]) {
      keys.forEach((key, i) => {
        if (fixture[i] === undefined) delete process.env[key];
        else process.env[key] = fixture[i];
      });
      await assert.rejects(workflow({}), SkipCheck);
    }
    for (const [din, date] of [['invalid', '2008-06-10'], ['00000019', '2026-02-31'],
      ['00000019', '2026-13-01'], ['00000019', '0000-01-01']]) {
      process.env.INACTIVE_DRUG_DIN = din;
      process.env.INACTIVE_DRUG_DATE = date;
      await assert.rejects(workflow({}), error => !(error instanceof SkipCheck)
        && /installed reference dataset/.test(error.message));
    }
    const reachedBrowser = new Error('valid fixture reached browser setup');
    for (const date of ['2008-06-10', '2024-02-29']) {
      process.env.INACTIVE_DRUG_DIN = '00000019';
      process.env.INACTIVE_DRUG_DATE = date;
      await assert.rejects(workflow({ context: { newPage() { throw reachedBrowser; } } }),
        error => error === reachedBrowser);
    }
  } finally {
    keys.forEach((key, i) => {
      if (original[i] === undefined) delete process.env[key];
      else process.env[key] = original[i];
    });
  }
});
