/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { authzReadFixture } = require('./lib/authz-read-fixture');

/** Model bound default copies and an occupied provider number; reject malformed copy SQL. */
function programFixture(sourceDefaults, { blockExisting = false } = {}) {
  const statements = [];
  let copied = [];
  let occupiedProvider;
  const sql = {
    value(query) {
      statements.push(query);
      if (query.startsWith('INSERT INTO security')) return '91';
      if (query.includes('SELECT GROUP_CONCAT(role_name)')) return 'doctor';
      if (query.startsWith('SELECT COUNT(*) FROM program_provider')) return '2';
      if (query.startsWith('SELECT COUNT(*) FROM secRole')) return '1';
      if (query.startsWith('SELECT COUNT(*) FROM security')) {
        return query.includes("user_name='carlosdoc'") ? '1' : '0';
      }
      if (query.startsWith('SELECT COUNT(*) FROM provider') && query.includes(' AND last_name=')) return '1';
      if (query.startsWith('SELECT COUNT(*) FROM provider') && query.includes(' AND last_name<>')) return '0';
      if (query.startsWith('SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=')) {
        const candidate = query.match(/provider_no='(\d+)'/)[1];
        assert.ok(query.includes('FROM provider_default_program'), 'allocation must check existing default rows');
        if (blockExisting && !occupiedProvider) occupiedProvider = candidate;
        return candidate === occupiedProvider ? '1' : '0';
      }
      if (query.startsWith('SELECT (SELECT COUNT(*)')) return '0';
      assert.fail(`Unexpected program fixture scalar: ${query}`);
    },
    rows(query, parameters) {
      statements.push(query);
      if (query.startsWith('SELECT privilege FROM secObjPrivilege')) {
        assert.match(query, /roleUserGroup='doctor' AND objectName='_appointment'/);
        return [['w']];
      }
      assert.match(query, /^SELECT program_id,signnote FROM provider_default_program\s+WHERE provider_no=\? ORDER BY program_id,signnote$/);
      assert.equal(parameters.length, 1);
      if (parameters[0] === '999998') return structuredClone(sourceDefaults);
      assert.equal(parameters[0], login.providerNo);
      return structuredClone(copied);
    },
    execute(query, parameters) {
      statements.push(query);
      if (query.startsWith('INSERT INTO provider_default_program')) {
        assert.match(query, /^INSERT INTO provider_default_program \(provider_no, program_id, signnote\)\s+SELECT \?, program_id, signnote FROM provider_default_program WHERE provider_no=\?$/);
        assert.deepEqual(parameters, [login.providerNo, '999998']);
        copied = structuredClone(sourceDefaults);
      }
      if (query.includes('DELETE FROM provider_default_program')) {
        assert.ok(query.includes(`DELETE FROM provider_default_program WHERE provider_no='${login.providerNo}'`));
        copied = [];
      }
    },
  };
  const fixture = authzReadFixture({ sql, marker: 'FAKE-PW0123456789abcdef', provider: '999998', testUser: 'carlosdoc' });
  const login = fixture.addLogin('doctor');
  return { fixture, login, statements, copied: () => copied, occupiedProvider: () => occupiedProvider };
}

test('default inheritance preserves every program and signing setting, then removes owned defaults', () => {
  const defaults = [['10001', '0'], ['10034', '1']];
  const f = programFixture(defaults, { blockExisting: true });
  f.fixture.joinTestLoginPrograms(f.login, { copyDefaults: true });
  assert.deepEqual(f.copied(), defaults);
  assert.ok(f.occupiedProvider());
  assert.notEqual(f.login.providerNo, f.occupiedProvider(), 'allocation must reject a provider number with existing defaults');
  f.fixture.cleanup();
  assert.deepEqual(f.copied(), []);
  assert.deepEqual(defaults, [['10001', '0'], ['10034', '1']]);
});

test('requested default inheritance rejects an empty source before inserting defaults', () => {
  const f = programFixture([]);
  assert.throws(() => f.fixture.joinTestLoginPrograms(f.login, { copyDefaults: true }), /no default program to inherit/);
  assert.ok(!f.statements.some(q => q.startsWith('INSERT INTO provider_default_program')));
  f.fixture.cleanup();
});

test('membership-only callers do not require or copy a default program', () => {
  const f = programFixture([]);
  assert.equal(f.fixture.joinTestLoginPrograms(f.login), 2);
  assert.ok(!f.statements.some(q => q.startsWith('SELECT program_id,signnote')
    || q.startsWith('INSERT INTO provider_default_program')));
  f.fixture.cleanup();
});
