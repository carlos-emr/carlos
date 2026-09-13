/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const { ROUND_TRIP_FIELDS, UNTOUCHED_COLUMN } = require('./demographic-edit-update-playwright-checks');

const SOURCE = fs.readFileSync(path.join(__dirname, 'demographic-edit-update-playwright-checks.js'), 'utf8');

test('the check never writes an identifier it has no business writing', () => {
  // A browser check editing a health card number or SIN, even on a FAKE- demo
  // patient, is not something that should be possible to do by accident.
  const columns = ROUND_TRIP_FIELDS.map((field) => field.column);
  for (const forbidden of ['hin', 'ver', 'sin', 'last_name', 'first_name', 'date_of_birth', 'year_of_birth', 'month_of_birth']) {
    assert.ok(!columns.includes(forbidden), `${forbidden} must not be written by this check`);
  }
});

test('every value written is obviously synthetic', () => {
  const marker = 'EDIT1700000000000';
  for (const field of ROUND_TRIP_FIELDS) {
    const value = field.value(marker);
    assert.ok(value && value.length > 0, `${field.input} must produce a value`);
  }
  const byInput = Object.fromEntries(ROUND_TRIP_FIELDS.map((field) => [field.input, field.value(marker)]));
  // 555-01xx is the reserved fictional range: this can never be someone's number.
  assert.match(byInput.phone, /^555-01\d\d$/);
  // .invalid is reserved by RFC 2606 and can never be delivered to.
  assert.match(byInput.email, /@example\.invalid$/);
  // The marker makes a leftover row traceable to the run that wrote it.
  assert.ok(byInput.city.includes(marker));
  // chart_no is varchar(10), so it carries only the tail of the marker -- but it
  // must still carry enough of it to identify the run, and must still fit.
  assert.ok(byInput.chart_no.length <= 10,
    `chart_no is varchar(10); ${JSON.stringify(byInput.chart_no)} would be truncated or rejected`);
  assert.ok(marker.endsWith(byInput.chart_no.replace(/^PW/, '')),
    'the chart number must be the tail of this run\'s marker, so a leftover row is traceable');
});

test('every written value fits the column it is written to', () => {
  // A value longer than its column is not a test failure that reads as one: on a
  // lenient MariaDB it is silently truncated, and the round-trip comparison then
  // fails with "the field did not reach the database" pointing at the app.
  const schema = fs.readFileSync(path.join(
    __dirname, '..', 'database', 'mysql', 'migration', 'common', 'V1__baseline_schema.sql',
  ), 'utf8');
  const start = schema.indexOf('CREATE TABLE `demographic` (');
  assert.ok(start > 0, 'the baseline schema must declare the demographic table');
  const table = schema.slice(start, schema.indexOf('ENGINE=', start));
  // One literal pattern over the whole table, rather than one built per column:
  // the repo's Semgrep rules flag new RegExp(...) and nothing here needs one.
  const widths = Object.fromEntries(
    [...table.matchAll(/`(\w+)` varchar\((\d+)\)/g)].map((found) => [found[1], Number(found[2])]),
  );
  assert.ok(Object.keys(widths).length > 10, 'the width map must not be empty, or this test proves nothing');
  const marker = 'EDIT1700000000000';
  for (const field of ROUND_TRIP_FIELDS) {
    const width = widths[field.column];
    if (!width) {
      continue;
    }
    const value = field.value(marker);
    assert.ok(value.length <= width,
      `${field.column} is varchar(${width}) but the check writes ${value.length} characters `
      + '-- a lenient MariaDB truncates it, and the round-trip then blames the app for a lost field');
  }
});

test('the untouched column is genuinely untouched', () => {
  const columns = ROUND_TRIP_FIELDS.map((field) => field.column);
  assert.ok(!columns.includes(UNTOUCHED_COLUMN),
    'the clobber check is meaningless if the check also writes that column');
});

test('the originals are captured from the database, not from the form', () => {
  // A form that renders a populated column blank is itself a defect; restoring
  // from the form would then silently erase the real value.
  assert.match(SOURCE, /SELECT \$\{columns/, 'originals must be read with a SELECT');
  const captureIndex = SOURCE.indexOf('const [before]');
  const fillIndex = SOURCE.indexOf('await input.fill(');
  assert.ok(captureIndex > 0 && fillIndex > captureIndex,
    'the originals must be captured before the first field is filled');
});

test('the restore runs in a finally and is scoped to one patient and one column set', () => {
  // The OUTER finally of main(), which holds the restore. The nested one after
  // it only disposes the sql runner and the browser, so slicing from the last
  // occurrence would miss the restore entirely.
  const finallyBlock = SOURCE.slice(SOURCE.indexOf('} finally {', SOURCE.indexOf('async function main')));
  assert.match(finallyBlock, /UPDATE demographic SET/, 'the restore must be in the finally');
  assert.match(finallyBlock, /WHERE demographic_no = \$\{Number\(demographicNo\)\}/,
    'the restore must be scoped to the one patient, with the id coerced to a number');
  assert.ok(!/DELETE FROM demographic/.test(SOURCE),
    'this check edits an existing patient; it must never delete one');
});

test('no field value is ever logged', () => {
  // The repo rule: diagnostics name the field, never its content.
  const logs = [...SOURCE.matchAll(/console\.(log|error)\(([^\n]*)/g)].map((match) => match[2]);
  for (const line of logs) {
    assert.ok(!/field\.value|stored\[|original\[|shown\b/.test(line),
      `a log line may not carry a field value: ${line.slice(0, 60)}`);
  }
});

test('the patient the check asserts on is the one the UI actually opened', () => {
  // Taking the id from the environment instead would let the check pass while
  // asserting against a different patient than the one it edited.
  assert.match(SOURCE, /masterPage\.url\(\)\.match\(\/demographic_no=\(\\d\+\)\//);
  const landedIndex = SOURCE.indexOf('const landed =');
  const selectIndex = SOURCE.indexOf('SELECT ${columns');
  assert.ok(landedIndex > 0 && selectIndex > landedIndex,
    'the id must be resolved from the landed page before any assertion uses it');
});
