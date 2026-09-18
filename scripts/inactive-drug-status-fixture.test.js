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
    process.env.INACTIVE_DRUG_DIN = 'invalid';
    process.env.INACTIVE_DRUG_DATE = '2008-06-10';
    await assert.rejects(workflow({}), error => !(error instanceof SkipCheck)
      && /installed reference dataset/.test(error.message));
  } finally {
    keys.forEach((key, i) => {
      if (original[i] === undefined) delete process.env[key];
      else process.env[key] = original[i];
    });
  }
});
