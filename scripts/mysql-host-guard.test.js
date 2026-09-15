/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');

const { validateMysqlHost } = require('./eform-local-playwright-utils');

// The fixture-writing Playwright checks seed and delete database rows, so the
// MySQL host they are pointed at must be loopback unless the caller opts in for
// a disposable non-local test database.
test('validateMysqlHost accepts loopback hosts without an opt-in', () => {
  for (const host of ['localhost', '127.0.0.1', '::1', '[::1]', 'LOCALHOST', '0:0:0:0:0:0:0:1']) {
    assert.equal(validateMysqlHost(host, {}), host);
  }
});

test('validateMysqlHost refuses non-loopback hosts without ALLOW_NON_LOCAL_MYSQL_HOST=true', () => {
  for (const host of ['db.example.com', '10.0.0.5', '192.168.1.20', '', undefined]) {
    assert.throws(() => validateMysqlHost(host, {}), /non-loopback MYSQL_HOST/);
    assert.throws(() => validateMysqlHost(host, { ALLOW_NON_LOCAL_MYSQL_HOST: 'yes' }), /non-loopback MYSQL_HOST/);
  }
});

test('validateMysqlHost allows a non-loopback host only with the explicit opt-in', () => {
  assert.equal(validateMysqlHost('db.example.com', { ALLOW_NON_LOCAL_MYSQL_HOST: 'true' }), 'db.example.com');
});
