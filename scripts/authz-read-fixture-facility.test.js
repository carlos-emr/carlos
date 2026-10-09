/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { authzReadFixture } = require('./lib/authz-read-fixture');

const MARKER = 'FAKE-PW0123456789abcdef';

/**
 * A scripted sql client for the facility half of authzReadFixture: no database, every statement recorded.
 * `insertFails` makes the Facility INSERT throw (and, with `rowLanded`, leave the row behind, as a statement that
 * committed before its client lost the connection would); `members` is the provider_facility count a cleanup reads.
 */
function scripted({ insertFails = false, rowLanded = false, members = '0' } = {}) {
  const statements = [];
  const sql = {
    value(query) {
      statements.push(query);
      if (query.startsWith('INSERT INTO Facility')) {
        if (insertFails) throw new Error('INSERT failed');
        return '7';
      }
      if (query.startsWith('INSERT INTO facility_message')) return '70';
      if (query.includes('FROM Facility WHERE disabled=0')) return '1';
      if (query.includes("SELECT IFNULL(MAX(id),'') FROM Facility")) return rowLanded ? '9' : '';
      if (query.includes('FROM provider_facility WHERE facility_id=')) return members;
      if (/^SELECT COUNT\(\*\) FROM (?:Facility|facility_message) WHERE .*(?:name|message)<>/.test(query)) return '0';
      if (/^SELECT COUNT\(\*\) FROM (?:Facility|facility_message) WHERE (?:name=|id=)/.test(query)) {
        // After the DELETE the row is gone; before it, the INSERT-time uniqueness probe sees none.
        return '0';
      }
      throw new Error(`Unexpected scalar: ${query}`);
    },
    rows(query) {
      statements.push(query);
      if (query.includes('information_schema.COLUMNS')) return Array.from({ length: 12 }, (_, i) => [`column${i}`]);
      throw new Error(`Unexpected read: ${query}`);
    },
    execute(query) { statements.push(query); },
  };
  return { sql, statements };
}

const fixtureOver = sql => authzReadFixture({ sql, marker: MARKER, provider: '999998', testUser: 'carlosdoc' });
const deletes = statements => statements.filter(statement => /^\s*DELETE FROM/.test(statement));

test('shouldRemoveMessagesBeforeFacilities_whenCleanupRuns', () => {
  const { sql, statements } = scripted();
  const fixture = fixtureOver(sql);
  const facility = fixture.addFacility('A');
  fixture.addFacilityMessage(facility, `${MARKER}-MSG-A`);
  fixture.addFacilityMessage(facility, `${MARKER}-MSG-OLD`, { expired: true });
  fixture.cleanup();
  const removed = deletes(statements);
  assert.equal(removed.length, 3);
  assert.match(removed[0], /^DELETE FROM facility_message WHERE id='70'/);
  assert.match(removed[1], /^DELETE FROM facility_message WHERE id='70'/);
  assert.match(removed[2], new RegExp(`^DELETE FROM Facility WHERE id='7' AND name='${MARKER}-A' AND description='${MARKER}'`));
  assert.ok(statements.some(statement => statement.includes('DATE_SUB(NOW()')), 'the expired message is written with a past expiry');
  assert.ok(statements.some(statement => statement.includes('DATE_ADD(NOW()')), 'the live message is written with a future expiry');
});

test('shouldCloneConfigurationColumnsOnly_whenAddingAFacility', () => {
  const { sql, statements } = scripted();
  const fixture = fixtureOver(sql);
  fixture.addFacility('B');
  const insert = statements.find(statement => statement.startsWith('INSERT INTO Facility'));
  assert.match(insert, /SELECT 'FAKE-PW0123456789abcdef-B','FAKE-PW0123456789abcdef',NOW\(\),0,`column0`/);
  assert.match(insert, /FROM Facility WHERE id=1;/);
  assert.doesNotMatch(insert, /provider_facility|program/);
});

test('shouldRefuseToDeleteAFacility_whenAnotherProviderJoinedIt', () => {
  const { sql, statements } = scripted({ members: '1' });
  const fixture = fixtureOver(sql);
  fixture.addFacility('A');
  assert.throws(() => fixture.cleanup(), /still belongs to the run's facility; refusing to delete it/);
  assert.deepEqual(deletes(statements), []);
});

test('shouldDeleteAHalfBuiltFacilityByName_whenTheInsertThrewAfterLanding', () => {
  const { sql, statements } = scripted({ insertFails: true, rowLanded: true });
  const fixture = fixtureOver(sql);
  assert.throws(() => fixture.addFacility('A'), /INSERT failed/);
  fixture.cleanup();
  const removed = deletes(statements);
  assert.equal(removed.length, 1);
  assert.match(removed[0], new RegExp(`^DELETE FROM Facility WHERE id='9' AND name='${MARKER}-A' AND description='${MARKER}'`));
});

test('shouldDeleteNothing_whenTheInsertThrewBeforeAnyRowExisted', () => {
  const { sql, statements } = scripted({ insertFails: true, rowLanded: false });
  const fixture = fixtureOver(sql);
  assert.throws(() => fixture.addFacility('A'), /INSERT failed/);
  fixture.cleanup();
  assert.deepEqual(deletes(statements), []);
});

test('shouldRejectAMessage_whenItCarriesNoRunMarker', () => {
  const { sql } = scripted();
  const fixture = fixtureOver(sql);
  const facility = fixture.addFacility('A');
  assert.throws(() => fixture.addFacilityMessage(facility, 'a banner anyone could be showing'), /must carry the run marker/);
});

test('shouldRejectAFacilityOrLogin_whenAnotherFixtureOwnsIt', () => {
  const { sql } = scripted();
  const fixture = fixtureOver(sql);
  const other = { id: '99', name: `${MARKER}-Z` };
  assert.throws(() => fixture.addFacilityMessage(other, `${MARKER}-MSG`), /needs a facility created by this fixture/);
  assert.throws(() => fixture.joinFacility({ providerNo: '800001' }, other), /needs a login created by this fixture/);
  assert.throws(() => fixture.addFacility('way-too-long'), /short alphanumeric label/);
});
