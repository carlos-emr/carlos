/**
 * Unit tests for the partialDates option of src/main/webapp/share/calendar/calendar-setup.js.
 *
 * The allergy dialogue's Start Date takes yyyy, yyyy-mm or yyyy-mm-dd. The shim binds flatpickr
 * to that box with allowInput, and flatpickr re-parses the box's text with dateFormat (on blur,
 * Enter and a click outside) and writes back formatDate(the parsed date). These tests load the
 * real flatpickr build for its parser and formatter, capture the options the shim hands to
 * flatpickr(), and run that same parse-then-format step. Run with:
 *   node --test scripts/calendar-setup-partial-dates.test.js
 */

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const WEBAPP = path.join(__dirname, '..', 'src', 'main', 'webapp');
const FLATPICKR = fs.readFileSync(path.join(WEBAPP, 'library', 'flatpickr', 'flatpickr.min.js'), 'utf8');
const SHIM = fs.readFileSync(path.join(WEBAPP, 'share', 'calendar', 'calendar-setup.js'), 'utf8');

/** Runs Calendar.setup on a Start Date box holding `value`; returns the options flatpickr got. */
function setUp(extraParams, value = '') {
  const input = { value };
  const context = { console, navigator: { userAgent: 'node' } };
  context.window = context;
  context.self = context;
  context.document = { getElementById: (id) => (id === 'startDate' ? input : null) };
  vm.createContext(context);
  vm.runInContext(FLATPICKR, context);
  const real = context.flatpickr;
  let options = null;
  const calendarListeners = {};
  const calendarContainer = { addEventListener: (type, fn) => { (calendarListeners[type] ||= []).push(fn); } };
  const stub = (target, opts) => { options = opts; return { toggle() {}, calendarContainer }; };
  stub.parseDate = real.parseDate;
  stub.formatDate = real.formatDate;
  context.flatpickr = stub;
  context.Calendar = { _flatpickrReady: true, _pendingSetups: [], _pendingLocaleUrl: null, _FD: null, _flatpickrLocale: null };
  vm.runInContext(SHIM, context);
  context.Calendar.setup({ inputField: 'startDate', ifFormat: '%Y-%m-%d', button: 'startDateCal', ...extraParams });
  assert.ok(options, 'Calendar.setup did not initialise flatpickr');
  /** A press inside the open calendar, the way a day is picked. */
  const press = (type) => (calendarListeners[type] || []).forEach((fn) => fn({ type }));
  return { options, real, input, press, calendarListeners };
}

/** What flatpickr writes back after re-parsing the box's text, with the shim's options. */
function reparse({ options, real }, text) {
  const parse = options.parseDate || ((s, f) => real.parseDate(s, f));
  const format = options.formatDate || ((d, f) => real.formatDate(d, f));
  const date = parse(text, options.dateFormat);
  return date === undefined ? undefined : format(date, options.dateFormat);
}

test('shouldFillInADay_withoutPartialDates', () => {
  const page = setUp({});
  assert.equal(page.options.parseDate, undefined, 'pages that do not opt in keep flatpickr\'s parser');
  assert.equal(page.options.formatDate, undefined, 'pages that do not opt in keep flatpickr\'s formatter');
  assert.equal(reparse(page, '2019'), '2019-01-01');
  assert.equal(reparse(page, '2019-05'), '2019-05-01');
});

test('shouldKeepTheTextBoxOnPhones_onlyWithPartialDates', () => {
  // flatpickr's mobile mode swaps in a native date input, which cannot hold 2019 or 2019-05.
  assert.equal(setUp({ partialDates: true }).options.disableMobile, true, 'a partial-date box keeps the text box on phones');
  assert.equal(setUp({}).options.disableMobile, undefined, 'other pages keep flatpickr\'s mobile default');
});

test('shouldKeepAYearAsTyped_withPartialDates', () => {
  assert.equal(reparse(setUp({ partialDates: true }), '2019'), '2019');
});

test('shouldKeepAYearAndMonthAsTyped_withPartialDates', () => {
  const page = setUp({ partialDates: true });
  assert.equal(reparse(page, '2019-05'), '2019-05');
  assert.equal(reparse(page, '2019-5'), '2019-5');
  assert.equal(reparse(page, ' 2019-05 '), '2019-05');
});

test('shouldKeepAFullDate_withPartialDates', () => {
  const page = setUp({ partialDates: true });
  assert.equal(reparse(page, '2019-05-14'), '2019-05-14');
  assert.equal(reparse(page, '2019-01-01'), '2019-01-01');
});

test('shouldOpenTheCalendarOnTheFirstDayOfThePeriod_withPartialDates', () => {
  const { options } = setUp({ partialDates: true });
  const year = options.parseDate('2019', options.dateFormat);
  const month = options.parseDate('2019-05', options.dateFormat);
  assert.deepEqual([year.getFullYear(), year.getMonth(), year.getDate()], [2019, 0, 1]);
  assert.deepEqual([month.getFullYear(), month.getMonth(), month.getDate()], [2019, 4, 1]);
});

test('shouldWriteAPickedDayInFull_whenTheCalendarIsPressed', () => {
  for (const type of ['mousedown', 'touchstart', 'keydown', 'click']) {
    const { options, press } = setUp({ partialDates: true });
    const typed = options.parseDate('2019', options.dateFormat);
    press(type);
    // 1 January 2019, the day the calendar opened on, picked: a known day, not a year.
    assert.equal(options.formatDate(new Date(typed.getTime()), options.dateFormat), '2019-01-01', `after a ${type}`);
  }
});

test('shouldListenToTheCalendarOnlyWithPartialDates', () => {
  assert.deepEqual(Object.keys(setUp({}).calendarListeners), []);
  assert.deepEqual(Object.keys(setUp({ partialDates: true }).calendarListeners).sort(), ['click', 'keydown', 'mousedown', 'touchstart']);
});

test('shouldNotKeepAnInvalidMonthAsTyped_withPartialDates', () => {
  const page = setUp({ partialDates: true });
  assert.notEqual(reparse(page, '2019-13'), '2019-13');
  assert.notEqual(reparse(page, '2019-00'), '2019-00');
});

test('shouldFormatOtherFormatsNormally_withPartialDates', () => {
  const { options } = setUp({ partialDates: true });
  const typed = options.parseDate('2019-05', options.dateFormat);
  // The calendar labels its days with ariaDateFormat; only the box's own format keeps the typed text.
  assert.equal(options.formatDate(typed, 'F j, Y'), 'May 1, 2019');
});
