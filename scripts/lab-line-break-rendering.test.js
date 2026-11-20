/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Pins the fixture that lab-line-break-rendering-playwright-checks.js seeds (issue #3953).
 *
 * The browser check can only prove the lab views render HL7 \.br\ as a line break if the
 * message it seeds actually carries \.br\ in every place the views print handler text, and
 * the hostile result it uses to prove encoding survives is real markup. A fixture edit that
 * dropped either would leave the check passing while it proves nothing, so this runs in the
 * browserless test:scripts job.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const { fixtureMessage, cleanup } = require('./lab-line-break-rendering-playwright-checks');

const segments = fixtureMessage().split('\r');
const field = (segment, index) => segment.split('|')[index];
const find = (prefix) => segments.filter((segment) => segment.startsWith(`${prefix}|`));

test('the fixture is an Excelleris ORU^R01 the PATHL7 handler parses', () => {
  const [msh] = find('MSH');
  assert.equal(field(msh, 2), 'PATHL7');
  assert.equal(field(msh, 8), 'ORU^R01');
  assert.ok(segments.every((segment) => /^[A-Z][A-Z0-9]{2}\|/.test(segment)), 'segments are CR-separated');
});

test('every text field the lab views print carries the HL7 line-break escape', () => {
  const [ftResult, nmResult] = find('OBX');
  assert.match(field(ftResult, 5), /\S\\\.br\\\S/, 'OBX-5 must carry \\.br\\ between two lines');
  assert.match(field(nmResult, 7), /\S\\\.br\\\S/, 'OBX-7 reference range must carry \\.br\\');
  assert.match(field(find('NTE')[0], 3), /\S\\\.br\\\S/, 'NTE-3 must carry \\.br\\');
});

test('the hostile result is live markup that only encoding can neutralise', () => {
  const hostile = field(find('OBX')[2], 5);
  // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- Assertion over a repository-owned HL7 attack fixture, never an HTML rendering sink.
  assert.equal(hostile.includes('<script>window.__carlos3953=1</script>'), true);
  assert.ok(hostile.includes('<img src=carlos3953 onerror='));
  assert.match(hostile, /\\\.br\\/, 'the hostile value must also be split by a break marker');
});

test('the fixture patient is clearly synthetic', () => {
  assert.match(field(find('PID')[0], 5), /^FAKE-/);
});

test('the check is registered in the suite and exposed through npm', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, 'playwright-suite.json'), 'utf8'));
  const entry = manifest.checks.find((check) => check.name === 'lab-line-break-rendering');
  assert.ok(entry, 'playwright-suite.json has no lab-line-break-rendering entry');
  assert.equal(entry.assertsDatabase, true, 'the check seeds and removes lab rows');
  const pkg = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'package.json'), 'utf8'));
  assert.equal(pkg.scripts['test:lab-line-break-rendering-playwright'],
    'node scripts/lab-line-break-rendering-playwright-checks.js');
});

// A populated OBX-4 is required to exercise the composite Ontario result branch.
test('Ontario fixture uses its HAPI version and nonempty result sub-IDs', () => {
  const ontario = fixtureMessage('ExcellerisON').split('\r');
  assert.equal(field(ontario[0], 11), '2.3.1');
  const results = ontario.filter(segment => segment.startsWith('OBX|'));
  assert.equal(field(results[0], 4), 'A');
  assert.equal(field(results[2], 4), 'A');
  assert.ok(field(results[0], 5).includes('\\.br\\'));
});

function cleanupDatabase({ changedLab = false, failedLab = false, changedPatient = false } = {}) {
  const deleted = [];
  return {
    deleted,
    value(query) {
      if (query.includes('SELECT (SELECT')) return '0';
      if (query.includes('FROM hl7TextMessage')) return changedLab && query.includes('lab_id=2') ? '0' : '1';
      if (query.includes('AND last_name=')) return changedPatient ? '0' : '1';
      return '0';
    },
    execute(query) {
      if (query.includes('lab_no=2') && failedLab) throw new Error('Database deletion failed');
      deleted.push(query);
    },
  };
}

test('cleanup removes all owned labs before the synthetic patient', () => {
  const state = { labNos: ['1', '2'], patientNo: '3' };
  const sql = cleanupDatabase();
  cleanup(sql, state);
  assert.deepEqual(state, { labNos: [], patientNo: null });
  assert.equal(sql.deleted.length, 3);
  assert.ok(sql.deleted.at(-1).startsWith('DELETE FROM demographic'));
});

test('cleanup reports a deletion failure, continues other labs and preserves their patient', () => {
  const state = { labNos: ['1', '2'], patientNo: '3' };
  const sql = cleanupDatabase({ failedLab: true });
  assert.throws(() => cleanup(sql, state), error => error instanceof AggregateError
    && error.errors.some(cause => cause.message === 'Database deletion failed'));
  assert.deepEqual(state, { labNos: ['2'], patientNo: '3' });
  assert.equal(sql.deleted.length, 1);
  assert.ok(sql.deleted[0].includes('lab_no=1'));
});

test('cleanup refuses to delete a lab with a changed ownership marker', () => {
  const state = { labNos: ['1', '2'], patientNo: '3' };
  const sql = cleanupDatabase({ changedLab: true });
  assert.throws(() => cleanup(sql, state), AggregateError);
  assert.deepEqual(state, { labNos: ['2'], patientNo: '3' });
  assert.ok(sql.deleted.every(query => !query.includes('lab_no=2') && !query.includes('DELETE FROM demographic')));
});

test('cleanup refuses to delete a patient with a changed ownership marker', () => {
  const state = { labNos: [], patientNo: '3' };
  const sql = cleanupDatabase({ changedPatient: true });
  assert.throws(() => cleanup(sql, state), AggregateError);
  assert.deepEqual(state, { labNos: [], patientNo: '3' });
  assert.deepEqual(sql.deleted, []);
});


test('TRUENORTH fixture represents one FT observation exposed as an OBR comment', () => {
  const message = fixtureMessage('TRUENORTH');
  assert.equal(message.split('\r').filter(segment => segment.startsWith('OBX|')).length, 1);
  assert.ok(message.includes('Final        11Sep2026'));
});
