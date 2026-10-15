/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { generatedPassword } = require('./lib/deb-login-password');

test('generated installer reset credentials satisfy the password policy', () => {
  for (let index = 0; index < 32; index += 1) {
    const password = generatedPassword();
    assert.equal(password.length, 20);
    assert.match(password, /^[A-HJ-NP-Za-km-z2-9]{16}!Aa1$/);
    assert.match(password, /[A-Z]/);
    assert.match(password, /[a-z]/);
    assert.match(password, /[0-9]/);
    assert.match(password, /[^A-Za-z0-9]/);
  }
});
