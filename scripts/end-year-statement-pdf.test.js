/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { statementInvoiceRows } = require('./lib/end-year-statement-pdf');

test('invoice IDs must occur in invoice rows, not dates, amounts or patient details (#5191)', () => {
  const text = `End Year Statement
    1234567890    FAKE-PW5191, Workflow
    15 Main Street, Unit 31
    From: 2025-01-01    To: 2025-12-31
    Invoice No    Date          Service Code    Invoiced    Paid
    21            2025-03-10                    34.70       25.00
                                A007A 34.70
    22            2025-06-20                    45.25       45.25
                                K030A 45.25
    Count: 2    Invoiced: 79.95    Paid: 70.25
    Page 1 of 1`;
  assert.deepEqual(statementInvoiceRows(text), [
    { id: '21', date: '2025-03-10', invoiced: '34.70', paid: '25.00' },
    { id: '22', date: '2025-06-20', invoiced: '45.25', paid: '45.25' },
  ]);
  // All of these excluded IDs trigger the old whole-PDF word-boundary assertion.
  for (const id of ['1', '2', '15', '25', '31', '70', '2025', '1234567890']) {
    assert.match(text, new RegExp(`\\b${id}\\b`));
    assert.ok(!statementInvoiceRows(text).some(row => row.id === id));
  }
});

test('retains unexpected and repeated invoice rows across page breaks', () => {
  const row = ' 5191   2024-12-31     1234567.89    -10.00';
  assert.deepEqual(statementInvoiceRows(`${row}\r\n\fInvoice No   Date   Invoiced   Paid\n${row}`), [
    { id: '5191', date: '2024-12-31', invoiced: '1234567.89', paid: '-10.00' },
    { id: '5191', date: '2024-12-31', invoiced: '1234567.89', paid: '-10.00' },
  ]);
});

test('empty or incomplete statement data cannot produce an invoice row', () => {
  assert.deepEqual(statementInvoiceRows('End Year Statement\nCount: 0 Invoiced: 0.00 Paid: 0.00'), []);
  assert.deepEqual(statementInvoiceRows('21 2025-03-10\nA007A 34.70\n34.70 25.00'), []);
});
