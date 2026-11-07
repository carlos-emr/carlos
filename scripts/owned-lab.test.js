/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { createOwnedLab } = require('./lib/owned-lab');

/*
 * lib/owned-lab.js makes and removes the COPY of a demo HL7 lab that lab-acknowledge acknowledges, so the demo's own lab keeps its
 * provider-0 routing row (the application deletes it on acknowledge). These tests give it recording stubs of the check's mysql
 * helpers and pin the SQL shapes: which keys the copy is read and removed by (the template's number on the way in; the owned lab's
 * number and accession on the way out), the `id > floor` taken before the first write, and the `<lab_no>N</lab_no>` match that finds
 * the table_modification row the application files. They do not prove MariaDB accepts the statements; the live check does.
 */

const LOCK_NOT_SHARED = 'NOT EXISTS (SELECT 1 FROM providerLabRouting other WHERE other.lab_no=providerLabRoutingLock.lab_no)';
const ACCESSION = 'ACKDEADBEEF01';

function stubs({ floor = '700', copy = '5001', verify = '3', accessionRows = [['5001']], left = '0' } = {}) {
  const calls = [];
  const sql = (query) => {
    calls.push(query);
    if (/FROM table_modification$/.test(query.trim())) return floor;
    if (/^START TRANSACTION/.test(query.trim())) return copy;
    if (/^SELECT \(SELECT COUNT\(\*\) FROM hl7TextInfo WHERE lab_no=\d+ AND accessionNum=/.test(query.trim())) return verify;
    if (/^SELECT \(SELECT COUNT\(\*\) FROM providerLabRouting WHERE lab_type='HL7'/.test(query.trim())) return left;
    return '';
  };
  const lookups = [];
  const sqlRows = (query) => { lookups.push(query); return accessionRows; };
  return { calls, lookups, sql, sqlRows };
}

function owned(options) {
  const s = stubs(options);
  const lab = createOwnedLab({ sql: s.sql, sqlRows: s.sqlRows, lockNotShared: LOCK_NOT_SHARED, accessionFor: () => ACCESSION });
  return { ...s, ...lab };
}

test('shouldTakeTheModificationFloor_beforeTheFirstWrite', () => {
  const lab = owned();
  lab.cloneLab('22');
  assert.match(lab.calls[0], /^SELECT IFNULL\(MAX\(id\), 0\) FROM table_modification$/, 'the floor is read first');
  assert.match(lab.calls[1].trim(), /^START TRANSACTION;/, 'and the first write comes after it');
});

test('shouldCopyTheTemplateInOneTransaction_underAnAccessionOfItsOwn', () => {
  const lab = owned();
  assert.equal(lab.cloneLab('22'), '5001');
  const copy = lab.calls[1];
  assert.match(copy.trim(), /^START TRANSACTION;[\s\S]*COMMIT;\s+SELECT @lab$/, 'one transaction, ending in the new lab number');
  assert.match(copy, /FROM hl7TextMessage WHERE lab_id=22 LIMIT 1/, 'the message is read by the template\'s lab id');
  assert.match(copy, /FROM hl7TextInfo WHERE lab_no=22 LIMIT 1/);
  assert.match(copy, /report_status, 'ACKDEADBEEF01', filler_order_num/, 'the copy\'s info row carries the owned accession, not the template\'s');
  assert.match(copy, /FROM patientLabRouting WHERE lab_no=22 AND lab_type='HL7' LIMIT 1/);
  assert.match(copy, /FROM providerLabRouting\s+WHERE lab_no=22 AND lab_type='HL7' AND provider_no='0'/,
    'only the provider-0 routing row is copied, so the copy reads like the demo lab');
  assert.doesNotMatch(copy, /INSERT INTO fileUploadCheck/, 'the checksum row belongs to the demo; the copy shares it');
  assert.doesNotMatch(copy, /UPDATE |DELETE /, 'a copy writes new rows and changes none');
});

test('shouldCheckTheCopyHasItsInfoMessageAndPatientLink', () => {
  const lab = owned();
  lab.cloneLab('22');
  const check = lab.calls[2];
  assert.match(check, /FROM hl7TextInfo WHERE lab_no=5001 AND accessionNum='ACKDEADBEEF01'/);
  assert.match(check, /FROM hl7TextMessage WHERE lab_id=5001/);
  assert.match(check, /FROM patientLabRouting WHERE lab_no=5001 AND lab_type='HL7'/);
});

test('shouldFail_whenTheCopyHasNoNumberOfItsOwn_orIsMissingARow', () => {
  assert.throws(() => owned({ copy: '' }).cloneLab('22'), /has no lab number of its own/);
  assert.throws(() => owned({ copy: '22' }).cloneLab('22'), /has no lab number of its own/, 'a number that is not above the template\'s is the template');
  assert.throws(() => owned({ verify: '2' }).cloneLab('22'), /missing its info, message or patient link/);
});

test('shouldRemoveByTheOwnedLabNumber_andTheModificationFloor', () => {
  const lab = owned();
  lab.cloneLab('22');
  lab.calls.length = 0;
  lab.removeOwnedLab();
  assert.match(lab.lookups[0], /SELECT lab_no FROM hl7TextInfo WHERE accessionNum='ACKDEADBEEF01'/, 'the number is also read from the accession');
  const removal = lab.calls[0];
  assert.match(removal, /DELETE FROM table_modification WHERE id > 700 AND table_name='providerLabRouting'\s+AND modification_type='delete' AND \(resultSet LIKE '%<lab_no>5001<\/lab_no>%'\)/,
    'the row the application files for the provider-0 routing row it deletes: above the floor, and naming the owned lab in its XML');
  assert.match(removal, /DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN \(5001\)/);
  assert.match(removal, new RegExp(`DELETE FROM providerLabRoutingLock WHERE lab_no IN \\(5001\\) AND ${LOCK_NOT_SHARED.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`),
    'the lock is left alone while another lab type routes that number');
  assert.match(removal, /DELETE FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN \(5001\)/);
  assert.match(removal, /DELETE FROM hl7TextInfo WHERE lab_no IN \(5001\) AND accessionNum='ACKDEADBEEF01'/,
    'the info row needs the accession as well as the number');
  assert.match(removal, /DELETE FROM hl7TextMessage WHERE lab_id IN \(5001\)/);
  assert.doesNotMatch(removal, /fileUploadCheck/, 'the demo\'s checksum row is never deleted');
});

test('shouldFindACopyWhoseNumberWasNeverReturned_throughItsAccession', () => {
  const lab = owned({ accessionRows: [['5001'], ['5002'], ['x'], ['0']] });
  lab.cloneLab('22');
  lab.calls.length = 0;
  lab.removeOwnedLab();
  const removal = lab.calls[0];
  assert.match(removal, /lab_no IN \(5001,5002\)/, 'both numbers the accession names; a value that is not an id is dropped');
  assert.match(removal, /resultSet LIKE '%<lab_no>5001<\/lab_no>%' OR resultSet LIKE '%<lab_no>5002<\/lab_no>%'/);
});

test('shouldRemoveNothing_whenNoLabWasCopied', () => {
  const never = owned();
  never.removeOwnedLab();
  assert.deepEqual(never.calls, []);
  assert.deepEqual(never.lookups, []);
});

test('shouldRemoveNothing_whenTheCopyFailedBeforeItHadANumber_andNoRowCarriesItsAccession', () => {
  const failed = owned({ copy: '', accessionRows: [] });
  assert.throws(() => failed.cloneLab('22'), /has no lab number of its own/);
  failed.calls.length = 0;
  failed.removeOwnedLab();
  assert.equal(failed.lookups.length, 1, 'it still looks the accession up: a copy can exist without its number having been returned');
  assert.deepEqual(failed.calls, [], 'but with no lab found there is nothing to delete');
});

test('shouldAssertEverythingGone_withTheSameKeys', () => {
  const lab = owned();
  lab.cloneLab('22');
  lab.calls.length = 0;
  lab.removeOwnedLab();
  const check = lab.calls[1];
  assert.match(check, /FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN \(5001\)/);
  assert.match(check, /FROM table_modification WHERE id > 700 AND table_name='providerLabRouting'\s+AND modification_type='delete' AND \(resultSet LIKE '%<lab_no>5001<\/lab_no>%'\)/,
    'the filed modification row is counted by the same floor and match');
  assert.throws(() => { const failing = owned({ left: '1' }); failing.cloneLab('22'); failing.removeOwnedLab(); },
    /was not removed/);
});
