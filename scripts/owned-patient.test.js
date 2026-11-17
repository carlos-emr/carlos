/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const {
  ALLERGY_ROWS, CONSULTATION_ROWS, PATIENT_KEYED_TABLES, combineRows, createOwnedPatient, eformRows, newOwnedMarker, removeOwnedPatient,
} = require('./lib/owned-patient');

/*
 * lib/owned-patient.js creates and removes the FAKE patient a check that is not built on runWorkflow() runs on, so that it
 * stops writing to a demo patient's chart. These tests give it a recording stub of the harness client: they pin WHICH rows it
 * may touch (the owned patient's, by its key, while it still carries the marker), not that MariaDB accepts the statements, which
 * the checks that use it prove.
 */

const MARKER = 'FAKE-PW0123456789abcdef';

function stubSql({ owner = '1', left = '0', gone = '0', inserted = '3891' } = {}) {
  const executed = [];
  const queries = [];
  return {
    executed, queries,
    value(query) {
      queries.push(query);
      if (/^INSERT INTO demographic/.test(query.trim())) return inserted;
      if (/AND last_name=/.test(query) && /SELECT COUNT\(\*\) FROM demographic WHERE/.test(query)) return owner;
      if (/SELECT COUNT\(\*\) FROM demographic WHERE demographic_no=\d+$/.test(query.trim())) return gone;
      return left;
    },
    execute(query) { executed.push(query); },
  };
}

test('shouldMakeAMarkerTheResidueAuditRecognises', () => {
  assert.match(newOwnedMarker(), /^FAKE-PW[0-9a-f]{16}$/);
  assert.notEqual(newOwnedMarker(), newOwnedMarker());
});

test('shouldInsertThePatientWithTheMarkerAsItsLastName_andReturnItsKey', () => {
  const sql = stubSql();
  const patient = createOwnedPatient(sql, { marker: MARKER, provider: '999998' });
  assert.equal(patient, '3891');
  assert.match(sql.queries[0], /VALUES \('FAKE-PW0123456789abcdef','Workflow'/, 'the marker is the last name');
  assert.match(sql.queries[0], /'999998','ON','ON','NR'/, 'the test provider is the patient\'s provider');
  assert.match(sql.queries[0], /SELECT LAST_INSERT_ID\(\)$/);
});

test('shouldRefuse_toCreateAPatientWithoutAMarkerOrAProvider_orWhenTheKeyIsNotAnId', () => {
  assert.throws(() => createOwnedPatient(stubSql(), { marker: 'FAKE-Jacky', provider: '999998' }), /FAKE-PW<16 hex> marker/);
  assert.throws(() => createOwnedPatient(stubSql(), { marker: MARKER, provider: '' }), /provider's number/);
  assert.throws(() => createOwnedPatient(stubSql({ inserted: '' }), { marker: MARKER, provider: '999998' }), /was not created/);
  assert.throws(() => createOwnedPatient(stubSql({ inserted: '0' }), { marker: MARKER, provider: '999998' }), /was not created/);
});

test('shouldDeleteTheChartRowsByThePatientsKey_beforeThePatient', () => {
  const sql = stubSql();
  removeOwnedPatient(sql, '3891', MARKER);
  const statements = sql.executed.join('\n');
  for (const [table, column] of PATIENT_KEYED_TABLES) {
    assert.match(statements, new RegExp(`DELETE FROM ${table} WHERE ${column}=3891(;|$)`, 'm'), `${table} is deleted by the patient's key`);
  }
  assert.match(statements, /DELETE FROM casemgmt_issue_notes WHERE note_id IN \(SELECT note_id FROM casemgmt_note WHERE demographic_no=3891\)/,
    'the note children are found through the patient\'s own notes');
  assert.match(statements, /DELETE FROM casemgmt_note_ext WHERE note_id IN \(SELECT note_id FROM casemgmt_note WHERE demographic_no=3891\)/);
  assert.match(statements, /DELETE FROM casemgmt_note_link WHERE note_id IN \(SELECT note_id FROM casemgmt_note WHERE demographic_no=3891\)/);
  assert.match(statements, /DELETE FROM casemgmt_note WHERE demographic_no=3891;/);
  assert.ok(statements.indexOf('DELETE FROM casemgmt_note_ext') < statements.indexOf('DELETE FROM casemgmt_note WHERE'),
    'children go while the notes that name them still exist');
  assert.equal(sql.executed.length, 2, 'the chart rows in one statement, then the patient');
  assert.match(sql.executed[1], /^DELETE FROM demographic WHERE demographic_no=3891 AND last_name='FAKE-PW0123456789abcdef'$/);
  assert.doesNotMatch(statements, /DELETE FROM (log|hash_audit)\b/, 'the audit trail is not the check\'s to delete');
});

test('shouldDeleteTheExtraTablesACheckNames_byThePatientsKey', () => {
  const sql = stubSql();
  removeOwnedPatient(sql, '3891', MARKER, { extra: [['allergies', 'demographic_no'], ['consultationRequests', 'demographicNo']] });
  const statements = sql.executed.join('\n');
  assert.match(statements, /DELETE FROM allergies WHERE demographic_no=3891/);
  assert.match(statements, /DELETE FROM consultationRequests WHERE demographicNo=3891/);
  assert.match(sql.queries.find((query) => /SELECT \(SELECT COUNT/.test(query)), /COUNT\(\*\) FROM allergies WHERE demographic_no=3891/,
    'an extra table is asserted empty too');
});

test('shouldRefuse_toDeleteAPatientThatDoesNotCarryTheMarker', () => {
  const sql = stubSql({ owner: '0' });
  assert.throws(() => removeOwnedPatient(sql, '1', MARKER), /ownership changed/);
  assert.deepEqual(sql.executed, [], 'nothing is deleted for a patient that is not this run\'s');
});

test('shouldRefuse_aKeyOrTableNameThatIsNotWhatItClaimsToBe', () => {
  assert.throws(() => removeOwnedPatient(stubSql(), '1; DROP TABLE demographic', MARKER), /demographic_no/);
  assert.throws(() => removeOwnedPatient(stubSql(), '3891', 'FAKE-Jacky'), /FAKE-PW<16 hex> marker/);
  assert.throws(() => removeOwnedPatient(stubSql(), '3891', MARKER, { extra: [['allergies; DROP TABLE x', 'demographic_no']] }), /not a table and column name/);
});

test('shouldFail_whenAChartRowIsStillThereAfterTheDeletes_andKeepTheParent', () => {
  const sql = stubSql({ left: '2' });
  assert.throws(() => removeOwnedPatient(sql, '3891', MARKER), /chart rows were not removed/);
  assert.equal(sql.executed.length, 1, 'the patient is kept so the rows that remain can still be found by its key');
});

test('shouldFail_whenThePatientIsStillThereAfterItsDelete', () => {
  assert.throws(() => removeOwnedPatient(stubSql({ gone: '1' }), '3891', MARKER), /patient was not removed/);
});

test('shouldDeleteTheRowsThatHangOffAParentRowOfThePatients_beforeTheParentsAreDeleted', () => {
  const sql = stubSql();
  removeOwnedPatient(sql, '3891', MARKER, CONSULTATION_ROWS);
  assert.equal(sql.executed.length, 3, 'the hanging rows, the direct rows, then the patient');
  const [hanging, direct] = sql.executed;
  assert.match(hanging, /DELETE FROM consultationRequestExt WHERE requestId IN \(SELECT requestId FROM consultationRequests WHERE demographicNo=3891\)/);
  assert.match(hanging, /DELETE FROM consultationRequestExtArchive WHERE requestId IN \(SELECT requestId FROM consultationRequests WHERE demographicNo=3891\)/);
  assert.match(hanging, /DELETE FROM consultdocs WHERE requestId IN \(SELECT requestId FROM consultationRequests WHERE demographicNo=3891\)/);
  assert.doesNotMatch(hanging, /DELETE FROM consultationRequests WHERE/, 'the parent rows are still there for the count');
  assert.match(direct, /DELETE FROM DigitalSignature WHERE demographicId=3891/, 'the signature is keyed by the patient');
  assert.match(direct, /DELETE FROM consultationRequests WHERE demographicNo=3891/);
  const count = sql.queries.findIndex((query) => /FROM consultationRequestExt WHERE requestId IN/.test(query) && /SELECT COUNT/.test(query));
  assert.ok(count >= 0, 'the hanging rows are counted');
});

test('shouldFail_whenARowHangingOffAParentRowIsStillThere', () => {
  const sql = stubSql();
  const value = sql.value;
  sql.value = (query) => (/FROM consultdocs WHERE requestId IN/.test(query) && /SELECT COUNT/.test(query) ? '1' : value(query));
  assert.throws(() => removeOwnedPatient(sql, '3891', MARKER, CONSULTATION_ROWS), /hanging off the owned patient's records were not removed/);
  assert.equal(sql.executed.length, 1, 'nothing else is deleted, so the parent rows can still be found');
});

test('shouldRefuse_aHangingRowDescription_whoseNamesAreNotIdentifiers', () => {
  const bad = { via: [{ table: 'consultdocs', column: 'requestId', parent: 'consultationRequests; DROP TABLE x', key: 'requestId', patient: 'demographicNo' }] };
  assert.throws(() => removeOwnedPatient(stubSql(), '3891', MARKER, bad), /not a table or column name/);
});

test('shouldNameTheAllergyTable_forTheAllergyBundle', () => {
  const sql = stubSql();
  removeOwnedPatient(sql, '3891', MARKER, ALLERGY_ROWS);
  assert.match(sql.executed.join('\n'), /DELETE FROM allergies WHERE demographic_no=3891/);
});

test('shouldDeleteTheEformValuesAndAttachments_throughTheInstancesOfThePatient_beforeTheInstances', () => {
  const sql = stubSql();
  sql.value = ((value) => (query) => (/MAX\(id\), 0\) FROM EFormDocs/.test(query) ? '4676' : value(query)))(sql.value);
  removeOwnedPatient(sql, '3891', MARKER, eformRows(sql));
  const [hanging, direct] = sql.executed;
  assert.match(hanging, /DELETE FROM eform_values WHERE fdid IN \(SELECT fdid FROM eform_data WHERE demographic_no=3891\)(;|$)/m);
  assert.match(hanging, /DELETE FROM EFormDocs WHERE fdid IN \(SELECT fdid FROM eform_data WHERE demographic_no=3891\) AND id > 4676/,
    'the demo\'s stale attachment rows are older than the mark and are not the run\'s to delete');
  assert.match(direct, /DELETE FROM eform_data WHERE demographic_no=3891/);
  assert.doesNotMatch(sql.executed.join('\n'), /DELETE FROM eform WHERE/, 'the template is not the patient\'s; a check deletes it by its own name');
});

test('shouldRefuse_anUnreadableEformDocsMark_andAMarkThatIsNotAnInteger', () => {
  const unreadable = stubSql();
  unreadable.value = () => 'x';
  assert.throws(() => eformRows(unreadable), /mark was not readable/);
  const bad = { via: [{ table: 'EFormDocs', column: 'fdid', parent: 'eform_data', key: 'fdid', patient: 'demographic_no', above: { column: 'id', value: '1; DROP' } }] };
  assert.throws(() => removeOwnedPatient(stubSql(), '3891', MARKER, bad), /must be an integer/);
});

test('shouldCombineBundles_withoutRepeatingOrDroppingAnyEntry', () => {
  const eforms = (() => { const sql = stubSql(); sql.value = () => '12'; return eformRows(sql); })();
  const both = combineRows(CONSULTATION_ROWS, eforms, { extra: [['allergies', 'demographic_no']] });
  assert.equal(both.via.length, CONSULTATION_ROWS.via.length + eforms.via.length);
  assert.equal(both.extra.length, CONSULTATION_ROWS.extra.length + eforms.extra.length + 1);
  assert.deepEqual(combineRows(), { extra: [], via: [] });
  const sql = stubSql();
  removeOwnedPatient(sql, '3891', MARKER, both);
  assert.match(sql.executed.join('\n'), /DELETE FROM allergies WHERE demographic_no=3891/);
  assert.match(sql.executed.join('\n'), /DELETE FROM eform_data WHERE demographic_no=3891/);
  assert.match(sql.executed.join('\n'), /DELETE FROM DigitalSignature WHERE demographicId=3891/);
});
