/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { formsAfterSelection } = require('./billing-bc-wcb-links-playwright-checks');

test('WCB form expectations preserve every field while normalizing the displayed menu', () => {
  const seed = [
    ['A', 'viewA', 'tableA', '1'], ['B', 'viewB', 'tableB', '1'],
    ['BC-WCB', 'viewWcb', 'wcb', '0'], ['Retired', 'viewOld', 'old', '4'],
  ];
  const original = structuredClone(seed);
  assert.deepEqual(formsAfterSelection(seed, ['A', 'B', 'BC-WCB']), [
    ['A', 'viewA', 'tableA', '1'], ['B', 'viewB', 'tableB', '2'],
    ['BC-WCB', 'viewWcb', 'wcb', '3'], ['Retired', 'viewOld', 'old', '0'],
  ]);
  assert.deepEqual(formsAfterSelection(seed, ['A', 'B']), [
    ['A', 'viewA', 'tableA', '1'], ['B', 'viewB', 'tableB', '2'],
    ['BC-WCB', 'viewWcb', 'wcb', '0'], ['Retired', 'viewOld', 'old', '0'],
  ]);
  assert.deepEqual(seed, original);
});

test('WCB form expectations reject missing or ambiguous menu entries', () => {
  const seed = [['A', 'viewA', 'tableA', '1']];
  assert.throws(() => formsAfterSelection(seed, ['Unknown']), /unknown or ambiguous/);
  assert.throws(() => formsAfterSelection(seed, ['A', 'A']), /repeated/);
  assert.throws(() => formsAfterSelection([...seed, ...seed], ['A']), /unknown or ambiguous/);
});
