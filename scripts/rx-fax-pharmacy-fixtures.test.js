/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { createFaxPhoneFixtures } = require('./rx-fax-pharmacy-fixtures');

function fixture(options = {}) {
  const writes = [];
  let pharmacyRemoved = false;
  const db = {
    value(query) {
      if (query.startsWith('SELECT provider_no')) return '999998';
      if (query.startsWith('INSERT INTO demographic\n')) return '42';
      if (query.startsWith('INSERT INTO pharmacyInfo')) return '71';
      if (query.startsWith('INSERT INTO fax_config')) return '81';
      if (query.startsWith('SELECT id FROM fax_config')) return '';
      if (query.startsWith('SELECT id FROM faxes')) return '';
      if (query.startsWith('SELECT COUNT(*) FROM fax_config')) return '0';
      if (query.includes('FROM demographic WHERE')) return options.ownershipChanged ? '0' : '1';
      if (query.includes('FROM pharmacyInfo WHERE')) return pharmacyRemoved ? '0' : '1';
      if (query.includes('FROM demographicPharmacy WHERE')) return '0';
      if (query.startsWith('SELECT (SELECT COUNT(*)')) return '0';
      throw new Error('Unexpected SQL lookup');
    },
    rows() { return []; },
    execute(query) {
      writes.push(query);
      if (query.includes('DELETE FROM faxes') && options.failFaxes) throw new Error('private database diagnostic');
      if (query.includes('DELETE FROM pharmacyInfo')) pharmacyRemoved = true;
    },
  };
  const fixtures = createFaxPhoneFixtures({ db, config: { testUser: 'synthetic' }, marker: 'FAKE-PW-test',
    fromFaxNumber: '4161234560', drugNamePrefix: 'OWNED', artifactDirectories: [], expectedProvider: options.provider });
  return { fixtures, writes };
}

test('provider mismatch fails before creating patient or pharmacy rows', () => {
  const f = fixture({ provider: 'different' });
  assert.throws(() => f.fixtures.stage(), /does not match the login/);
  assert.equal(f.fixtures.patient, undefined);
  assert.deepEqual(f.writes, []);
});

test('changed patient ownership blocks every clinical cleanup write', () => {
  const f = fixture({ ownershipChanged: true });
  f.fixtures.stage();
  f.writes.length = 0;
  assert.throws(() => f.fixtures.cleanup(), /Patient fixture ownership changed/);
  assert.deepEqual(f.writes, []);
});

test('fax cleanup failure still attempts account and pharmacy cleanup and fails without leaking SQL errors', () => {
  const f = fixture({ failFaxes: true });
  f.fixtures.stage();
  assert.throws(() => f.fixtures.cleanup(), /^Error: fixture cleanup failed for: faxes$/);
  assert.ok(f.writes.some(query => query.includes('DELETE FROM fax_config')));
  assert.ok(f.writes.some(query => query.includes('DELETE FROM pharmacyInfo')));
});

test('contact edits target only the owned pharmacy and retain SQL escaping', () => {
  const f = fixture();
  f.fixtures.stage();
  f.fixtures.stagePharmacyPhones({ pharmacyFax: '5550100100', phone1: "x'1", phone2: null });
  const edit = f.writes.find(query => query.startsWith('UPDATE pharmacyInfo'));
  assert.ok(edit.includes("WHERE recordID=71 AND name='FAKE-PW-test'"));
  assert.ok(edit.includes('phone2=NULL'));
  f.fixtures.cleanup();
});

const { assertFaxCaseBrowser } = require('./rx-fax-pharmacy-browser');
const retryResponse = { label: 'A', status: 409, method: 'POST', url: 'https://localhost/carlos/rx/WriteToEncounter' };
function browserEvents() {
  return { badResponses: [retryResponse], consoleIssues: [{ label: 'A', location: { url: retryResponse.url },
    text: 'Failed to load resource: the server responded with a status of 409 (Conflict)' }],
  pageErrors: [], requestFailures: [], unexpectedDialogs: [] };
}
test('browser permits exactly the injected refusal and its matching console entry', () => {
  assert.doesNotThrow(() => assertFaxCaseBrowser(browserEvents(), 'A', true));
});
test('browser still rejects a server failure, unrelated console error and missing injected refusal', () => {
  const server = browserEvents();
  server.badResponses.push({ ...retryResponse, status: 500 });
  assert.throws(() => assertFaxCaseBrowser(server, 'A', true), /HTTP 500/);
  const consoleFailure = browserEvents();
  consoleFailure.consoleIssues[0].location.url = 'https://localhost/carlos/unrelated';
  assert.throws(() => assertFaxCaseBrowser(consoleFailure, 'A', true), /console/);
  const missing = browserEvents();
  missing.badResponses = [];
  assert.throws(() => assertFaxCaseBrowser(missing, 'A', true), /exactly once/);
});

const { stageRxFaxAccount, cleanupRxFaxAccount } = require('./rx-fax-account-fixture');
test('sender fixture rejects collisions before writing and refuses changed ownership at cleanup', () => {
  let mutations = 0;
  const collision = { value: () => '9', execute: () => { mutations += 1; } };
  assert.throws(() => stageRxFaxAccount(collision, '4165550100'), /collided/);
  assert.equal(mutations, 0);
  assert.throws(() => cleanupRxFaxAccount(collision, { id: '9', faxNumber: '4165550100' }), /ownership changed/);
  assert.equal(mutations, 1, 'cleanup attempts only the ownership-constrained delete');
  assert.throws(() => stageRxFaxAccount(collision, "1' OR 1=1"), /ten digits/);
});


test('sender fixture refuses an orphan job without inserting a new account', () => {
  const queries = [];
  assert.throws(() => stageRxFaxAccount({ value(query) {
    queries.push(query);
    return query.startsWith('SELECT id FROM faxes') ? '42' : '';
  } }, '4161234560'), /existing fax job/);
  assert.equal(queries.length, 2);
  assert(queries.every(query => query.startsWith('SELECT')));
});

test('sender fixture disables inbox polling and uses only synthetic credentials', () => {
  const queries = [];
  const account = stageRxFaxAccount({ value(query) {
    queries.push(query);
    return query.startsWith('INSERT') ? '23' : '';
  } }, '4161234560');
  assert.deepEqual(account, { id: '23', faxNumber: '4161234560' });
  assert.match(queries[2], /'Playwright Fax','fax@example.invalid','faxuser','siteuser','x','x','srfax','0','',0\)/);
});
