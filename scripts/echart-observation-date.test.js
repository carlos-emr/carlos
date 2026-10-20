/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// validDate() in the eChart editor checks the encounter date is not in the future before a save.
// The field is rendered with English months whatever the interface language (issue #4711), and the
// localized calendar writes the interface language's months; both must be understood. A month it
// cannot read is left to the server, which refuses it with a message, instead of being turned into
// month -1 (December of the year before) and judged on that.
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const start = source.indexOf('    function getMonthIdx(');
const end = source.indexOf('    var pastObservationDateError;', start);
assert.ok(start > 0 && end > start, 'getMonthIdx() and validDate() were not found');

const FRENCH = ['Janv', 'Févr', 'Mars', 'Avr', 'Mai', 'Juin', 'Juil', 'Août', 'Sept', 'Oct', 'Nov', 'Déc'];
const ENGLISH = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function editor(labels, value, now = new Date(2026, 9, 16, 12, 0)) {
  const field = { value };
  // Date is the real one, but "now" is pinned so the verdicts do not depend on the run date.
  class FixedDate extends Date {
    constructor(...args) { if (args.length) super(...args); else super(now.getTime()); }
  }
  const context = { month: labels.slice(), $: id => (id === 'observationDate' ? field : null), Date: FixedDate };
  vm.createContext(context);
  vm.runInContext(source.slice(start, end), context);
  return context;
}

test('an English field value is a past date in a French chart', () => {
  // The Edit fragment renders "15-Feb-2026"; the French labels say "Févr".
  assert.equal(editor(FRENCH, '15-Feb-2026 08:30').validDate(), true);
  assert.equal(editor(FRENCH, '01-Sep-2026 08:30').validDate(), true);
  assert.equal(editor(FRENCH, '16-Oct-2026 09:00').validDate(), true);
});

test('a date written by the French calendar is read in a French chart', () => {
  assert.equal(editor(FRENCH, '30-sept-2026 08:30').validDate(), true);
  assert.equal(editor(FRENCH, '03-févr-2026 08:30').validDate(), true);
});

test('a future date is still refused, in English and in the interface language', () => {
  assert.equal(editor(ENGLISH, '15-Dec-2026 08:30').validDate(), false);
  assert.equal(editor(FRENCH, '15-Dec-2026 08:30').validDate(), false);
  assert.equal(editor(FRENCH, '15-déc-2026 08:30').validDate(), false);
  assert.equal(editor(ENGLISH, '17-Oct-2026 08:30').validDate(), false);
});

test('a month it cannot read is left to the server instead of being judged as December', () => {
  // Before: getMonthIdx() answered -1, setMonth(-1) moved to December, and a past date was
  // refused as "in the future" -- or a future one let through.
  assert.equal(editor(ENGLISH, '2026-10-15 19:14:08.0').validDate(), true);
  assert.equal(editor(FRENCH, '15-Foo-2026 08:30').validDate(), true);
});

test('getMonthIdx answers the interface labels first, then English', () => {
  const chart = editor(FRENCH, '');
  assert.equal(chart.getMonthIdx('févr'), 1);
  assert.equal(chart.getMonthIdx('feb'), 1);
  assert.equal(chart.getMonthIdx('dec'), 11);
  assert.equal(chart.getMonthIdx('foo'), -1);
});
