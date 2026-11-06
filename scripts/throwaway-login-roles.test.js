/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {throwawayLoginFixture} = require('./lib/throwaway-login-fixture');

function fixture() {
  const statements = [];
  const sql = {
    execute(query) { statements.push(query); },
    value(query) {
      statements.push(query);
      if (query.startsWith('SELECT (SELECT COUNT')) return '0';
      if (query.startsWith('SELECT COUNT(*) FROM security WHERE user_name=')) return query.includes("'source-login'") ? '1' : '0';
      if (query.startsWith('SELECT COUNT(*) FROM security WHERE provider_no=')) return '0';
      if (query.startsWith('SELECT COUNT(*) FROM provider')) return '1';
      if (query.startsWith('INSERT INTO security')) return '501';
      if (query.startsWith('SELECT password FROM security')) return 'fixture-hash';
      if (query.startsWith('SELECT COUNT(*) FROM secUserRole')) return '1';
      throw new Error('Unexpected fixture query');
    },
  };
  return {statements, login: throwawayLoginFixture({sql, marker: 'FAKE-PW0123456789abcdef', provider: '999998', testUser: 'source-login'})};
}

test('default login creation preserves inherited roles and expiry behavior', () => {
  const {login, statements} = fixture();
  login.create();
  const credentials = statements.find(query => query.startsWith('INSERT INTO security'));
  assert.match(credentials, /pin,b_ExpireSet,date_ExpireDate,b_LocalLockSet/);
  const roles = statements.filter(query => query.startsWith('INSERT INTO secUserRole'));
  assert.equal(roles.length, 1);
  assert.match(roles[0], /SELECT .*role_name,orgcd,activeyn,NOW\(\) FROM secUserRole/s);
});

test('restricted login starts with only explicit roles and enforced expiry', () => {
  const {login, statements} = fixture();
  login.create({roleNames: ['owned-no-report', 'owned-no-report'], expiresTomorrow: true});
  const credentials = statements.find(query => query.startsWith('INSERT INTO security'));
  assert.match(credentials, /pin,1,DATE_ADD\(CURDATE\(\), INTERVAL 1 DAY\),b_LocalLockSet/);
  const roles = statements.filter(query => query.startsWith('INSERT INTO secUserRole'));
  assert.equal(roles.length, 1);
  assert.doesNotMatch(roles[0], /FROM secUserRole/);
  assert.equal(roles[0].match(/'owned-no-report'/g).length, 1);
});

for (const roleNames of [[], ["bad'role"]]) {
  test(`invalid explicit roles are rejected before any database access: ${JSON.stringify(roleNames)}`, () => {
    const {login, statements} = fixture();
    assert.throws(() => login.create({roleNames}), /Explicit fixture roles/);
    assert.equal(statements.length, 0);
  });
}

test('the add-appointment page locks under the throwaway number are part of its rows', () => {
  const {login, statements} = fixture();
  login.create();
  const lock = `FROM PageMonitor WHERE providerNo='${login.providerNo}'`;
  assert.ok(statements.find(query => query.startsWith('SELECT (SELECT COUNT')).includes(lock), 'the free-number check skips page locks');
  statements.length = 0;
  login.cleanup();
  const deletes = statements.filter(query => query.includes('DELETE FROM'));
  assert.equal(deletes.length, 1);
  assert.ok(deletes[0].includes(`DELETE ${lock}`), 'cleanup does not delete the page locks');
  assert.ok(deletes[0].indexOf(`DELETE ${lock}`) < deletes[0].indexOf('DELETE FROM provider '), 'the page locks are not deleted before the provider row');
  assert.ok(statements.filter(query => query.startsWith('SELECT (SELECT COUNT')).every(query => query.includes(lock)),
    'the leftover count skips page locks');
});
