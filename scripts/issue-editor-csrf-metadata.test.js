/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

test('the release ledger retires the fixed CSRF failure and pins only the remaining duplicate-message step', () => {
  const read = file => JSON.parse(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'));
  const checks = read('scripts/playwright-suite.json').checks;
  const ledger = read('docs/testing/release-2026.08-known-outcomes.json');
  const broader = checks.find(check => check.name === 'admin-issue-editor');
  const active = ledger.browserExpectedFailures.filter(row => row.check === broader.name);
  assert.equal(active.length, 1);
  assert.equal(active[0].finding, 362);
  assert.equal(active[0].step, broader.expectedFailure.step);
  assert.equal(ledger.browserExpectedFailures.some(row => row.finding === 331), false);
  assert.equal(ledger.retiredBrowserExpectations.filter(row =>
    row.check === broader.name && row.finding === 331).length, 1);
  assert.equal(checks.find(check => check.name === 'admin-issue-editor-save').expectedFailure, undefined);
});
