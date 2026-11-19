/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');

const root = path.join(__dirname, '..');
const jsp = fs.readFileSync(path.join(root,
  'src/main/webapp/WEB-INF/jsp/admin/securityupdatesecurity.jsp'), 'utf8');
const validator = fs.readFileSync(path.join(root,
  'src/main/java/io/github/carlos_emr/carlos/www/admin/SecurityUpdatePasswordValidator.java'), 'utf8');
const limits = Object.fromEntries([...validator.matchAll(/public static final int (MAX_PASSWORD_\w+) = (\d+);/g)]
  .map(match => [match[1], Number(match[2])]));

test('the complete no-PIN MFA lifecycle remains an active regression without a failure pin', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, 'playwright-suite.json'), 'utf8'));
  const check = manifest.checks.find(row => row.name === 'login-mfa-no-pin');
  assert.equal(check.script, 'scripts/login-mfa-playwright-checks.js');
  assert.equal(check.profile, 'no-pin');
  assert.equal(check.expectedFailure, undefined);
  const ledger = JSON.parse(fs.readFileSync(path.join(root,
    'docs/testing/release-2026.08-known-outcomes.json'), 'utf8'));
  assert.equal(ledger.browserExpectedFailures.some(row => row.check === check.name), false);
  assert.equal(ledger.retiredBrowserExpectations.filter(row => row.check === check.name && row.finding === 347).length, 1);
});

function fixture(legacyPin, values = {}, pinValid = true) {
  const match = /function onsub\(\) \{[\s\S]*?\n            \}/.exec(jsp);
  assert.ok(match, 'Missing production security editor validator');
  // Render only this function's actual JSP condition, limits and message tags;
  // the validator's JavaScript is executed unchanged against the form below.
  const script = match[0]
    .replace(/<%\s*if \(MfaManager\.isOscarLegacyPinEnabled\(\)\) \{\s*%>([\s\S]*?)<%\s*}\s*%>/g,
      (_, body) => legacyPin ? body : '')
    .replace(/<%=SecurityUpdatePasswordValidator\.(MAX_PASSWORD_\w+)%>/g, (_, name) => {
      assert.ok(Number.isInteger(limits[name]), 'Unknown production password limit ' + name);
      return String(limits[name]);
    })
    .replace(/<\/?fmt:[^>]+>/g, 'message');
  assert.doesNotMatch(script, /<%|<\/?fmt:/, 'Unrendered JSP in validator fixture');
  const form = Object.fromEntries(Object.entries({user_name: 'synthetic-editor', password: '*********',
    conPassword: '*********', provider_no: '999999', date_ExpireDate: '',
    ...(legacyPin ? {pin: '****', conPin: '****'} : {}), ...values})
    .map(([key, value]) => [key, {value}]));
  form.b_ExpireSet = {checked: false};
  form.enableMfa = {checked: false};
  if (legacyPin) {
    form.b_RemoteLockSet = {checked: false};
    form.b_LocalLockSet = {checked: false};
  }
  const alerts = [], focused = [], pinCalls = [];
  const context = vm.createContext({document: {updatearecord: form, forms: [form]}, TextEncoder,
    alert: message => alerts.push(message), setfocus: name => focused.push(name),
    validatePassword: () => true, validatePin: pin => { pinCalls.push(pin); return pinValid; }});
  vm.runInContext(script, context);
  return {form, alerts, focused, pinCalls, submit: () => context.onsub()};
}

for (const enabled of [false, true]) {
  test(`no-PIN security edit validates with MFA ${enabled ? 'enabled' : 'disabled'} and no PIN controls`, () => {
    const f = fixture(false);
    f.form.enableMfa.checked = enabled;
    assert.equal(f.submit(), true);
    assert.deepEqual(f.alerts, []);
    assert.deepEqual(f.pinCalls, []);
  });
}

for (const [label, values, focus] of [
  ['missing username', {user_name: ''}, 'user_name'],
  ['overlong username', {user_name: 'u'.repeat(31)}, 'user_name'],
  ['missing password', {password: ''}, 'password'],
  ['overlong password', {password: 'a'.repeat(limits.MAX_PASSWORD_LENGTH + 1)}, 'password'],
  ['overlong UTF-8 password', {password: '\u754c'.repeat(25)}, 'password'],
  ['password confirmation mismatch', {password: 'Synthetic123!', conPassword: 'different'}, 'conPassword'],
]) {
  test(`no-PIN security edit still refuses ${label}`, () => {
    const f = fixture(false, values);
    assert.equal(f.submit(), false);
    assert.deepEqual(f.focused, [focus]);
    assert.equal(f.alerts.length, 1);
  });
}

test('no-PIN security edit still refuses an absent provider and an incomplete expiry date', () => {
  assert.equal(fixture(false, {provider_no: ''}).submit(), false);
  const f = fixture(false, {date_ExpireDate: '2026'});
  f.form.b_ExpireSet.checked = true;
  assert.equal(f.submit(), false);
  assert.deepEqual(f.focused, ['date_ExpireDate']);
});

test('legacy PIN sentinels remain valid without checking them as new PINs', () => {
  const f = fixture(true);
  assert.equal(f.submit(), true);
  assert.deepEqual(f.pinCalls, []);
});

for (const name of ['b_RemoteLockSet', 'b_LocalLockSet']) {
  test(`legacy ${name} still requires a PIN`, () => {
    const f = fixture(true, {pin: '', conPin: ''});
    f.form[name].checked = true;
    assert.equal(f.submit(), false);
    assert.deepEqual(f.focused, ['pin']);
  });
}

test('legacy PIN validation still refuses a PIN rejected by the password policy', () => {
  const f = fixture(true, {pin: 'invalid', conPin: 'invalid'}, false);
  assert.equal(f.submit(), false);
  assert.deepEqual(f.pinCalls, ['invalid']);
  assert.deepEqual(f.focused, ['pin']);
});

test('legacy PIN confirmation still rejects a mismatch', () => {
  const f = fixture(true, {pin: '123456', conPin: '654321'});
  assert.equal(f.submit(), false);
  assert.deepEqual(f.focused, ['conPin']);
});

test('legacy PIN updates still accept matching valid values', () => {
  const f = fixture(true, {pin: '123456', conPin: '123456'});
  assert.equal(f.submit(), true);
  assert.deepEqual(f.pinCalls, ['123456']);
  assert.deepEqual(f.alerts, []);
});
