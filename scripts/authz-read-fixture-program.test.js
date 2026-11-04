/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { authzReadFixture } = require('./lib/authz-read-fixture');

function programFixture(sourceDefaults) {
  const statements = [];
  let copied = [];
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
      if (query.startsWith('SELECT (SELECT COUNT(*)')) return '0';
      assert.fail(`Unexpected program fixture scalar: ${query}`);
    },
    rows(query) {
      statements.push(query);
      assert.match(query, /^SELECT program_id,signnote FROM provider_default_program/);
      return structuredClone(query.includes("provider_no='999998'") ? sourceDefaults : copied);
    },
    execute(query) {
      statements.push(query);
      if (query.startsWith('INSERT INTO provider_default_program')) copied = structuredClone(sourceDefaults);
      if (query.includes('DELETE FROM provider_default_program')) copied = [];
    },
  };
  const fixture = authzReadFixture({ sql, marker: 'FAKE-PW0123456789abcdef', provider: '999998', testUser: 'carlosdoc' });
  const login = fixture.addLogin('doctor');
  return { fixture, login, statements, copied: () => copied };
}

test('default inheritance preserves a nonfirst program and its signing setting, then removes owned defaults', () => {
  const defaults = [['10034', '1']];
  const f = programFixture(defaults);
  f.fixture.joinTestLoginPrograms(f.login, { copyDefaults: true });
  assert.deepEqual(f.copied(), defaults);
  assert.ok(f.statements.some(q => q.includes('FROM provider_default_program') && q.startsWith('SELECT (SELECT COUNT(*)')),
    'allocation must exclude provider numbers with preexisting default rows');
  f.fixture.cleanup();
  assert.deepEqual(f.copied(), []);
  assert.deepEqual(defaults, [['10034', '1']]);
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
