/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {clinicalFieldDifferences} = require('./rx-favorites-choose-drug-playwright-checks');

test('native textarea CRLF and DOM LF compare equally without changing the clinical wording', () => {
  assert.deepEqual(clinicalFieldDifferences(['quantity', 'special'],
    ['37', 'Line one <&>\r\nLine two\r\n'], ['37', 'Line one <&>\nLine two\n']), []);
});
test('changed quantities, instructions and whitespace still fail with field names only', () => {
  assert.deepEqual(clinicalFieldDifferences(['quantity', 'special'],
    ['38', 'Take two\r\n'], ['37', 'Take one\n']), ['quantity', 'special']);
  assert.deepEqual(clinicalFieldDifferences(['special'], ['Keep spacing \r\n'], ['Keep spacing\n']), ['special']);
});
test('normalization is confined to textarea CRLF and does not hide null or malformed rows', () => {
  assert.deepEqual(clinicalFieldDifferences(['outside_provider_name'], ['A\r\nB'], ['A\nB']), ['outside_provider_name']);
  assert.deepEqual(clinicalFieldDifferences(['special'], ['A\rB'], ['A\nB']), ['special']);
  assert.deepEqual(clinicalFieldDifferences(['special'], [null], ['']), ['special']);
  assert.deepEqual(clinicalFieldDifferences(['special'], [], ['']), ['column_count']);
});
