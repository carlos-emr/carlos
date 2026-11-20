/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Unit tests for the cleanup of scripts/cml-version-chain-playwright-checks.js.
//
// The check seeds lab rows by SQL into the live database and removes them by key afterwards, so what it will and will
// not delete is worth asserting without a database: a fake `sql` answers the count queries and records the statements
// it is asked to run.
const assert = require('node:assert/strict');
const test = require('node:test');

const { removeReport } = require('./cml-version-chain-playwright-checks');

const MARKER = 'FAKE-PW0123456789abcdef';
const PATIENT = 7;
const PROVIDER = '999998';

const report = (overrides = {}) => ({
  label: 'first unmatched', id: 1000000001, infoId: 1000000002, marker: MARKER, printDate: '20260105', printTime: '08:00',
  ...overrides,
});

/**
 * The three counts the cleanup asks for. `owned` is the marker proof on the report row, `header` the orphaned-header
 * probe, and `remaining` everything still keyed by the report's numbers: it reads `before` until a statement has been
 * executed and `after` from then on.
 */
function fakeSql({ owned, header = '0', before, after = '0' }) {
  const executed = [];
  return {
    executed,
    value(query) {
      if (/FROM labPatientPhysicianInfo WHERE id=\d+ AND patient_last_name=/.test(query)) return owned;
      if (/^SELECT COUNT\(\*\) FROM labReportInformation WHERE id=/.test(query.trim())) return header;
      return executed.length === 0 ? before : after;
    },
    execute(statement) { executed.push(statement); },
  };
}

const run = (sql, overrides) => removeReport({ sql, report: report(overrides), patient: PATIENT, provider: PROVIDER });

test('should remove every routing row keyed by an unmatched report\'s number, whoever the match linked it to', () => {
  // The number was proved unoccupied when it was allocated, so a routing row under it is this run's even if a regression
  // linked the report to another patient; a delete filtered by the owned patient would leave that row behind.
  const sql = fakeSql({ owned: '1', before: '3' });
  run(sql, { matched: false });

  assert.equal(sql.executed.length, 1);
  assert.match(sql.executed[0], /DELETE FROM patientLabRouting WHERE lab_type='CML' AND lab_no=1000000001(;|$)/);
  assert.doesNotMatch(sql.executed[0], /patientLabRouting[^;]*demographic_no/);
});

test('should remove the routing rows of a seeded report by its number as well', () => {
  const sql = fakeSql({ owned: '1', before: '4' });
  run(sql);

  assert.match(sql.executed[0], /DELETE FROM patientLabRouting WHERE lab_type='CML' AND lab_no=1000000001(;|$)/);
});

test('should keep the audit-row deletes scoped to the owned provider and patient', () => {
  const sql = fakeSql({ owned: '1', before: '2' });
  run(sql);

  for (const statement of sql.executed[0].split(';').filter((s) => /^\s*DELETE FROM log\b/.test(s))) {
    assert.match(statement, /provider_no='999998'/);
  }
});

test('should refuse to delete anything when the report row does not carry this run\'s marker', () => {
  const sql = fakeSql({ owned: '0', header: '0', before: '2' });

  assert.throws(() => run(sql), /header changed; refusing to delete rows keyed by its number/);
  assert.deepEqual(sql.executed, []);
});

test('should remove a header left behind by a seed that failed before the report was written', () => {
  const sql = fakeSql({ owned: '0', header: '1', before: '1' });
  run(sql);

  assert.deepEqual(sql.executed, ['DELETE FROM labReportInformation WHERE id=1000000002']);
});

test('should refuse a header that looks orphaned when anything else is keyed by the report\'s numbers', () => {
  const sql = fakeSql({ owned: '0', header: '1', before: '2' });

  assert.throws(() => run(sql), /header changed; refusing to delete rows keyed by its number/);
  assert.deepEqual(sql.executed, []);
});

test('should do nothing for a report that was never written', () => {
  const sql = fakeSql({ owned: '0', header: '0', before: '0' });
  run(sql);

  assert.deepEqual(sql.executed, []);
});

test('should fail when rows are still keyed by the report after the delete', () => {
  const sql = fakeSql({ owned: '1', before: '1', after: '1' });

  assert.throws(() => run(sql), /fixture was not removed/);
});
