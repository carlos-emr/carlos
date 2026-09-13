/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  DATE_SELECTOR, NEXT_DAY, PREVIOUS_DAY, addDays, iso, lastDayOfMonth, parseIso,
} = require('./schedule-date-navigation-playwright-checks');

const SOURCE = fs.readFileSync(
  path.join(__dirname, 'schedule-date-navigation-playwright-checks.js'), 'utf8',
);
const DAY_JSP = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'provider', 'appointmentprovideradminday.jsp',
), 'utf8');
const MONTH_JSP = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'provider', 'appointmentprovideradminmonth.jsp',
), 'utf8');
const CALENDAR_JSP = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'share', 'CalendarPopup.jsp',
), 'utf8');
const BUNDLE = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'resources', 'oscarResources_en.properties',
), 'utf8');

/*
 * The check's expectations are ordinary calendar arithmetic, and the page's are
 * not: it adds and subtracts from the day number with no idea how long a month
 * is. These tests pin that the page really does that -- otherwise the check is
 * standing on month boundaries for no reason -- and that the check's own
 * arithmetic is right, including the cases that only occur in some years.
 */

test('the page really does build its day links by raw arithmetic', () => {
  // This is the whole premise. If the JSP ever computes a real date, the check
  // still passes but stops being interesting, and this says so out loud.
  assert.match(DAY_JSP, /day=<%=isWeekView\?\(day-7\):\(day-1\)%>/);
  assert.match(DAY_JSP, /day=<%=isWeekView\?\(day\+7\):\(day\+1\)%>/);
  // And the month view does the same with the month number.
  assert.match(MONTH_JSP, /month=<%=\(month-1\)%>/);
  assert.match(MONTH_JSP, /month=<%=\(month\+1\)%>/);
});

test('stepping back from the 1st asks the server for day 0', () => {
  // Spelled out because it is the reason the check jumps to the 1st at all.
  const first = parseIso('2026-03-01');
  assert.equal(first.getDate(), 1);
  assert.equal(iso(addDays(first, -1)), '2026-02-28', 'the honest answer is the last day of February');
});

test('stepping forward from the last day asks for a day the month does not have', () => {
  const last = lastDayOfMonth(parseIso('2026-04-10'));
  assert.equal(iso(last), '2026-04-30');
  assert.equal(iso(addDays(last, 1)), '2026-05-01');
});

test('February is right in a leap year and in a common one', () => {
  // The one boundary whose length is not a constant.
  assert.equal(iso(lastDayOfMonth(parseIso('2028-02-10'))), '2028-02-29');
  assert.equal(iso(lastDayOfMonth(parseIso('2026-02-10'))), '2026-02-28');
  assert.equal(iso(addDays(parseIso('2028-03-01'), -1)), '2028-02-29');
});

test('the year boundary is handled too', () => {
  assert.equal(iso(addDays(parseIso('2027-01-01'), -1)), '2026-12-31');
  assert.equal(iso(addDays(lastDayOfMonth(parseIso('2026-12-05')), 1)), '2027-01-01');
});

test('iso() zero-pads, so its output compares equal to what the header renders', () => {
  assert.equal(iso(new Date(2026, 0, 5)), '2026-01-05');
  assert.equal(iso(new Date(2026, 11, 31)), '2026-12-31');
});

test('the header date really is rendered in the format the check parses', () => {
  assert.match(DAY_JSP, /<span class="dateAppointment">/);
  assert.equal(DATE_SELECTOR, 'span.dateAppointment');
  assert.ok(BUNDLE.includes('date.EEEyyyyMMdd=EEE, yyyy-MM-dd'),
    'the check reads a yyyy-MM-dd out of the header; if the bundle format changes this must too');
});

test('the two arrows are told apart by their icons, not by position', () => {
  // Both carry class="redArrow". Taking .first() and .last() would silently
  // swap if anything else on the header ever used that class.
  assert.match(DAY_JSP, /class="redArrow"/);
  assert.match(DAY_JSP, /fa-solid fa-backward-step/);
  assert.match(DAY_JSP, /fa-solid fa-forward-step/);
  assert.match(PREVIOUS_DAY, /fa-backward-step/);
  assert.match(NEXT_DAY, /fa-forward-step/);
});

test('the calendar popup navigates the opener, which is why the check waits there', () => {
  assert.match(CALENDAR_JSP, /opener\.location\.href =/);
  assert.match(CALENDAR_JSP, /self\.close\(\)/);
  // The wait is on the OPENER, not on the popup that was clicked.
  assert.match(SOURCE, /clickAndAwaitReload\(schedulePage, cell, \{ timeout, label: 'the calendar day cell' \}\)/);
});

test('a day cell is matched exactly, so "1" does not also select 11, 21 and 31', () => {
  assert.match(SOURCE, /days\.indexOf\(String\(Number\(day\)\)\)/);
  assert.ok(!/new RegExp\(/.test(SOURCE.replace(/\/\/.*$/gm, '')),
    'no RegExp may be built from the day number');
});

test('every boundary is asserted in both directions', () => {
  // Landing back where you started is the property a clinician relies on, and a
  // server that normalises day 0 may still not normalise day 32.
  assert.match(SOURCE, /Stepping back from the 1st and forward again/);
  assert.match(SOURCE, /Crossing into the next month and stepping back/);
});

test('the check navigates only, and by clicking', () => {
  assert.ok(!/page\.goto\(/.test(SOURCE), 'the thing under test IS the URL the page builds');
  assert.ok(!/providercontrol\?/.test(SOURCE), 'the check must not build a schedule URL of its own');
  for (const statement of [/createSqlRunner/, /\bINSERT\b/i, /\bDELETE\b/i]) {
    assert.ok(!statement.test(SOURCE), `moving around the schedule is read-only; found ${statement}`);
  }
});

test('the Today link landing is asserted against today, not against the page it came from', () => {
  // `after` was read and returned and never compared, so a Today link that
  // landed on some other day passed as long as the page was not blank.
  //
  // The oracle is TODAY, not `before`: checkMonthBoundaries runs first and walks
  // the schedule across month ends, so `before` is wherever that walk finished
  // and Today quite correctly does not return to it. A test asserting
  // after === before would have pinned the wrong contract.
  const source = require('node:fs').readFileSync(
    require.resolve('./schedule-date-navigation-playwright-checks'), 'utf8',
  );
  const monthView = source.slice(source.indexOf('async function checkMonthView'));
  const body = monthView.slice(0, monthView.indexOf('\n}\n'));
  assert.match(body, /const driftDays = /);
  assert.match(body, /driftDays <= 1/);
  assert.ok(!/assert\(after === before/.test(body),
    'before is the boundary walk’s landing, not today; asserting equality would pin the wrong contract');
  // The invalid-date guard must test the parsed value, not its truthiness:
  // parseIso returns an Invalid Date object, which is truthy.
  assert.match(body, /Number\.isNaN\(landed\.getTime\(\)\)/);
  // And the comparison is midnight-to-midnight, or the tolerance shrinks as the
  // day wears on and the same deployment passes in the morning, fails at night.
  assert.match(body, /setHours\(0, 0, 0, 0\)/);
});
