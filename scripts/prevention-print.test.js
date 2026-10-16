/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const { assertSelectionOnly } = require('./prevention-print-playwright-checks');

test('a print POST of the selection alone is accepted', () => {
  const params = assertSelectionOnly('CSRF-TOKEN=abc&immunizationOnly=false&demographic_no=42&demographicNo=42&printHP=Inf&printHP=HPV-CERVIX');
  assert.deepEqual(params.getAll('printHP'), ['Inf', 'HPV-CERVIX']);
  assert.equal(params.get('immunizationOnly'), 'false');
});

for (const field of ['preventProcedureComments34-0', 'preventionHeader34', 'hin', 'mrp', 'lotNumberToAdd2', 'module']) {
  test(`a print POST carrying ${field} is refused`, () => {
    assert.throws(() => assertSelectionOnly(`demographicNo=42&printHP=Inf&${field}=x`), /still carries chart fields/);
  });
}

test('a print POST carrying a clinical comment under any name is refused', () => {
  assert.throws(() => assertSelectionOnly('demographicNo=42&printHP=Reviewed+labs%3B+find+attached'), /carries a clinical comment/);
});
