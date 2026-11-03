/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { authzReadFixture } = require('./lib/authz-read-fixture');

for (const [privilege, expected] of [[null, false], ['r', true], ['|u|', true], ['w', true], ['x', true],
  ['|or|', true], ['|ow|', true], ['d', false], ['|o|', false]]) {
  test(`fixture records schedule access from its role's _appointment privilege: ${privilege}`, () => {
    const sql = {
      value(query) {
        if (query.startsWith('SELECT COUNT(*) FROM security WHERE user_name=\'carlosdoc\'')) return '1';
        if (query.startsWith('SELECT COUNT(*) FROM secRole')) return '1';
        if (query.startsWith('INSERT INTO security')) return '42';
        if (query.includes('SELECT GROUP_CONCAT(role_name)')) return 'restricted';
        if (query.includes('FROM provider WHERE provider_no=') && query.includes('AND last_name=')) return '1';
        return '0';
      },
      rows(query) {
        assert.match(query, /FROM secObjPrivilege/);
        assert.match(query, /roleUserGroup='restricted' AND objectName='_appointment'/);
        return privilege === null ? [] : [[privilege]];
      },
      execute() {},
    };
    const fixture = authzReadFixture({ sql, marker: 'FAKE-PW0123456789abcdef', provider: '999998', testUser: 'carlosdoc' });
    const login = fixture.addLogin('restricted');
    assert.equal(login.scheduleAccess, expected);
    assert.equal(login.role, 'restricted');
  });
}
