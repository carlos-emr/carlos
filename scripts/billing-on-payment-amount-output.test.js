/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {paymentRowAmounts,printedInvoiceTotals}=require('./billing-on-multi-item-payments-playwright-checks');
test('literal payment-row reader preserves exact cents and both locale currency variants',()=>{
  for(const symbol of ['$','¤','CA$'])assert.deepEqual(paymentRowAmounts('Service Code: _ONE $11.23 Paid: '+symbol+'3.21 Balance: '+symbol+'8.02'),['3.21','8.02']);
});
for(const text of ['Paid: $3.210 Balance: $8.02','Paid: $3.21 Balance: $8.020','Paid: pending Balance: $8.02','Paid: $-3.21 Balance: $8.02','Paid: $3.21 Balance: $8.02 Paid: $4.00 Balance: $7.23'])
  test('payment-row reader rejects malformed or ambiguous amounts: '+text,()=>assert.throws(()=>paymentRowAmounts(text),/paid\/balance fields/));
test('literal printed-total reader returns distinct complete amount values',()=>assert.deepEqual(printedInvoiceTotals('Total: 73.89 Payments: $8.64 Balance Due: 65.25'),['73.89','8.64','65.25']));
for(const text of ['Total: 73.890 Payments: 8.64 Balance Due: 65.25','Total: 73.89 Payments: 8.640 Balance Due: 65.25','Total: 73.89 Payments: 8.64 Balance: 65.25','Total: 73.89 Payments: 8.64 Balance Due: 65.25 Total: 99.99'])
  test('printed-total reader rejects omitted, imprecise or duplicate fields: '+text,()=>assert.throws(()=>printedInvoiceTotals(text),/malformed totals/));
