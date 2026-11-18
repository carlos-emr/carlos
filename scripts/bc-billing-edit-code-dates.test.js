/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * BC billing code Edit page (billingEditCode.jsp): after a fee row's save, shownDate() turns the dates the save answers
 * with into the row's text. The server answers each date as the epoch milliseconds of its midnight, so a browser west of
 * a UTC server must not show the day before; a code with no end date answers null. Runs the page's own function.
 */

// Before any Date is made: the clinic browser in Toronto, the server's dates at UTC midnight.
process.env.TZ = 'America/Toronto';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const JSP = path.join(__dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'billing', 'CA', 'BC', 'billingEditCode.jsp');

function shownDate() {
  const source = fs.readFileSync(JSP, 'utf8');
  const start = source.indexOf('function shownDate(value) {');
  assert.ok(start >= 0, 'billingEditCode.jsp defines shownDate');
  // The function ends at the first line that closes it at its own indentation.
  const end = source.indexOf('\n                    }', start);
  assert.ok(end > start, 'shownDate has a closing brace');
  const context = {};
  vm.runInNewContext(`${source.slice(start, end + '\n                    }'.length)}\nthis.shownDate = shownDate;`, context);
  return context.shownDate;
}

test('the browser runs west of UTC in this test', () => {
  assert.equal(new Date(Date.UTC(2026, 9, 17)).getDate(), 16);
});

test('a date at UTC midnight shows as that day in a Toronto browser', () => {
  const show = shownDate();
  assert.equal(show(Date.UTC(2026, 9, 17)), '2026-10-17');
  assert.equal(show(Date.UTC(9999, 11, 31)), '9999-12-31');
});

test('the older object shape and a plain yyyy-mm-dd string show the same day', () => {
  const show = shownDate();
  assert.equal(show({ time: Date.UTC(2026, 0, 1) }), '2026-01-01');
  assert.equal(show('2026-03-03'), '2026-03-03');
});

test('a missing or unreadable date shows as empty', () => {
  const show = shownDate();
  for (const value of [null, undefined, '', 'null', {}]) {
    assert.equal(show(value), '', `for ${JSON.stringify(value)}`);
  }
});
