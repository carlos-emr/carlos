/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// RxInputValidation (share/javascript/rx-input-validation.js): the prescribing card's checks for
// optional pick-up fields (#5002), impossible written dates and inline Min/Max doses (#5003).
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const v = require('../src/main/webapp/share/javascript/rx-input-validation');

test('a cleared pick-up date or time is valid and stays blank', () => {
  for (const blank of ['', '   ', null, undefined]) {
    assert.deepEqual(v.pickupDate(blank), { ok: true, value: '' });
    assert.deepEqual(v.pickupTime(blank), { ok: true, value: '' });
  }
});

test('a real pick-up date and time are accepted, trimmed', () => {
  assert.deepEqual(v.pickupDate(' 2026-10-18 '), { ok: true, value: '2026-10-18' });
  assert.deepEqual(v.pickupDate('2028-02-29'), { ok: true, value: '2028-02-29' });
  assert.deepEqual(v.pickupTime('9:05'), { ok: true, value: '9:05' });
  assert.deepEqual(v.pickupTime('23:59'), { ok: true, value: '23:59' });
  assert.deepEqual(v.pickupTime('00:00'), { ok: true, value: '00:00' });
});

test('an invalid non-empty pick-up value is refused and returned for the message', () => {
  for (const bad of ['2026-02-30', '2027-02-29', '2026-13-01', '18/10/2026', 'tomorrow', '2026-10']) {
    assert.deepEqual(v.pickupDate(bad), { ok: false, value: bad }, bad);
  }
  for (const bad of ['24:00', '12:60', '5pm', '1700', '12:5']) {
    assert.deepEqual(v.pickupTime(bad), { ok: false, value: bad }, bad);
  }
});

test('a full written date must exist on the calendar', () => {
  assert.equal(v.fullDateIsReal('2026-02-31'), false);
  assert.equal(v.fullDateIsReal('2026/04/31'), false);
  assert.equal(v.fullDateIsReal('2027-02-29'), false);
  assert.equal(v.fullDateIsReal('2028-02-29'), true);
  assert.equal(v.fullDateIsReal('2026-10-18'), true);
  assert.equal(v.fullDateIsReal('0026-01-01'), true, 'a two-digit year is not read as 19xx');
});

test('blank and partial written dates are left to the existing checks', () => {
  for (const value of ['', null, '2026', '2026-02', 'not a date']) assert.equal(v.fullDateIsReal(value), true, String(value));
});

test('calendar dates use 1-based months and reject impossible days', () => {
  assert.equal(v.isCalendarDate(2026, 2, 28), true);
  assert.equal(v.isCalendarDate(2026, 2, 29), false);
  assert.equal(v.isCalendarDate(2026, 0, 1), false);
  assert.equal(v.isCalendarDate(2026, 12, 32), false);
});

test('inline doses accept plain decimals and the card display of a computed dose', () => {
  for (const dose of ['1', '0.5', '.25', '2.', '0.33333334', ' 1.5 ', '']) assert.equal(v.isDoseAmount(dose), true, dose);
});

test('inline doses refuse text, signs, exponents and non-finite values', () => {
  for (const dose of ['abc', 'one', '1/2', '-1', '+1', '1e3', 'NaN', 'Infinity', '1,5', '1 tab']) {
    assert.equal(v.isDoseAmount(dose), false, dose);
  }
});

test('messages quote the prescriber input, every placeholder', () => {
  assert.equal(v.format('The date "{0}" ({0}) is wrong', ' 2026-02-31 '), 'The date "2026-02-31" (2026-02-31) is wrong');
  assert.equal(v.format('No input "{0}"', null), 'No input ""');
});
