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

test('a truncated reset input refuses submission without exposing either credential', async () => {
  const {fillResetPasswords} = require('./lib/deb-login-password');
  let submitted = false;
  const fields = new Map();
  const page = {locator(name) {
    return {async fill(value) {fields.set(name, value.slice(0, 32));},
      async inputValue() {return fields.get(name);}};
  }};
  const oldPassword = 'FAKE-CurrentCredential!7';
  const nextPassword = 'FAKE-NewCredential!7'.repeat(3);
  await assert.rejects(async () => {
    await fillResetPasswords(page, oldPassword, nextPassword);
    submitted = true;
  }, error => /no credential change was submitted/.test(error.message)
    && !error.message.includes(oldPassword) && !error.message.includes(nextPassword));
  assert.equal(submitted, false);
});

test('supported reset inputs are retained exactly before submission', async () => {
  const {fillResetPasswords} = require('./lib/deb-login-password');
  const values = new Map();
  const page = {locator(name) {return {
    async fill(value) {values.set(name,value);}, async inputValue() {return values.get(name);},
  };}};
  await fillResetPasswords(page, 'FAKE-old-Ω!7', 'FAKE-new-é!7');
  assert.equal(values.get('input[name="oldPassword"]'), 'FAKE-old-Ω!7');
  assert.equal(values.get('input[name="newPassword"]'), 'FAKE-new-é!7');
  assert.equal(values.get('input[name="confirmPassword"]'), 'FAKE-new-é!7');
});
