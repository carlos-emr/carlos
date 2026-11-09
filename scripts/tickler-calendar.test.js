/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {offset} = require('../src/main/webapp/share/javascript/tickler-calendar');
for (const [base, months, days, expected] of [
 ['2027-01-31',1,0,'2027-02-28'], ['2028-01-31',1,0,'2028-02-29'],
 ['2028-02-29',12,0,'2029-02-28'], ['2028-02-29',48,0,'2032-02-29'],
 ['2028-02-29',13,0,'2029-03-29'], ['2026-08-31',1,0,'2026-09-30'],
 ['2026-10-31',4,0,'2027-02-28'], ['2027-03-31',-1,0,'2027-02-28'],
 ['2026-12-31',2,1,'2027-03-01'], ['2027-03-13',0,2,'2027-03-15']]) {
 test(`${base} offset ${months} months ${days} days`, () => {
  const [y,m,d] = base.split('-').map(Number), original = new Date(y,m-1,d,12);
  const result = offset(original,months,days);
  const format = date => [date.getFullYear(),String(date.getMonth()+1).padStart(2,'0'),String(date.getDate()).padStart(2,'0')].join('-');
  assert.equal(format(result),expected); assert.equal(format(original),base);
 });
}
