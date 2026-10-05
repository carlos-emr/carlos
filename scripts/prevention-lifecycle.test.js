/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const { activePrevention } = require('./prevention-lifecycle-playwright-checks');

function sqlWith(rows) {
  return { rows(query) {
    assert.match(query, /demographic_no=42/);
    assert.match(query, /prevention_type='Inf' AND deleted=0/);
    return rows;
  } };
}

test('prevention lookup requires one active row for the owned patient', () => {
  assert.equal(activePrevention(sqlWith([['81']]), '42'), '81');
});

for (const rows of [[], [['81'], ['82']], [['0']], [['invalid']]]) {
  test(`prevention lookup rejects missing, duplicate or invalid rows: ${JSON.stringify(rows)}`, () => {
    assert.throws(() => activePrevention(sqlWith(rows), '42'), /exactly one active prevention/);
  });
}
