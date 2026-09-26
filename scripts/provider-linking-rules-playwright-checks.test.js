/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
'use strict';
// Pins the pure parts of the provider-linking-rules browser check (issue #3971): the synthetic
// CML message the lab upload parses, and the cleanup statements that put a shared database back.
// A cleanup that silently skips a row leaves a clinic-wide routing switch on, or a synthetic lab
// in a provider's inbox, for whoever uses the database next.
const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const {
  buildCmlMessage,
  deleteLabStatements,
  restoreHrmStatements,
  restorePropertyStatements,
} = require(path.join(__dirname, 'provider-linking-rules-playwright-checks.js'));

const FIELDS = {
  controlId: 'ABC123', accession: 'PLRABC123', hin: '1234567890', lastName: 'FAKE-DOE',
  firstName: 'JANE', dob: '19800131', sex: 'F', ohipNo: '123456',
};

test('buildCmlMessage puts each identifier where CMLHandler reads it', () => {
  const segments = buildCmlMessage(FIELDS).split('\r');
  const field = (name, index) => segments.find((s) => s.startsWith(`${name}|`)).split('|')[index];
  assert.equal(field('PID', 4), '1234567890^^ON', 'PID-4 carries the HIN');
  assert.equal(field('PID', 5), 'FAKE-DOE^JANE');
  assert.equal(field('PID', 7), '19800131');
  assert.equal(field('PID', 8), 'F');
  assert.equal(field('ORC', 2), 'PLRABC123', 'ORC-2 is the accession number');
  assert.equal(field('OBR', 16).split('^')[0], '123456', 'OBR-16 is the ordering provider');
  assert.ok(segments[0].startsWith('MSH|^~\\&|'), 'MSH declares the standard encoding characters');
  assert.ok(!buildCmlMessage(FIELDS).includes('\n'), 'segments are separated by carriage returns only');
});

test('buildCmlMessage refuses identifiers that would break the HL7 structure', () => {
  assert.throws(() => buildCmlMessage({ ...FIELDS, hin: '123|456' }), /hin must be alphanumeric/);
  assert.throws(() => buildCmlMessage({ ...FIELDS, accession: 'A^B' }), /accession must be alphanumeric/);
  const segments = buildCmlMessage({ ...FIELDS, lastName: 'O|Brien^X' }).split('\r');
  assert.equal(segments[1].split('|')[5], 'O Brien X^JANE', 'name delimiters are neutralised, not passed through');
});

test('restorePropertyStatements puts back exactly the rows that existed', () => {
  assert.deepEqual(restorePropertyStatements([]), ["DELETE FROM property WHERE name='provider_linking_rules'"],
    'no row before means no row after');
  const statements = restorePropertyStatements([['7', 'true', '<NULL>'], ['9', null, '']]);
  assert.equal(statements.length, 3);
  assert.match(statements[1], /VALUES \(7, 'provider_linking_rules', 'true', NULL\)$/);
  assert.match(statements[2], /VALUES \(9, 'provider_linking_rules', NULL, ''\)$/,
    'an empty provider stays empty, which the schema treats as global too');
  assert.throws(() => restorePropertyStatements([['7 OR 1=1', 'true', '<NULL>']]), /numeric/);
});

test('deleteLabStatements covers every table an upload writes, for numeric ids only', () => {
  assert.deepEqual(deleteLabStatements([]), []);
  const statements = deleteLabStatements(['101', 'x; DROP TABLE demographic', 102]).join('\n');
  for (const table of ['measurements', 'measurementsExt', 'providerLabRouting', 'patientLabRouting',
    'providerLabRoutingLock', 'fileUploadCheck', 'hl7TextInfo', 'hl7TextMessage']) {
    assert.match(statements, new RegExp(`\\b${table}\\b`), `${table} is cleaned`);
  }
  assert.ok(statements.includes('IN (101,102)'));
  assert.ok(!statements.includes('DROP'), 'a non-numeric id never reaches SQL');
});

test('restoreHrmStatements rebuilds the report rows from the snapshot', () => {
  const statements = restoreHrmStatements('5',
    [['1', '42', '2026-01-01 00:00:00']],
    [['3', '101', '1', '2026-01-02 00:00:00', '1', null]]);
  assert.equal(statements.length, 4);
  assert.match(statements[0], /DELETE FROM HRMDocumentToDemographic WHERE hrmDocumentId='5'/);
  assert.match(statements[1], /DELETE FROM HRMDocumentToProvider WHERE hrmDocumentId='5'/);
  assert.match(statements[2], /\('1', '42', '5', '2026-01-01 00:00:00'\)/);
  assert.match(statements[3], /\('3', '101', '5', '1', '2026-01-02 00:00:00', '1', NULL\)/);
  assert.throws(() => restoreHrmStatements('5 OR 1', [], []), /numeric/);
});
