/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const { validateBaseUrl } = require('./lib/playwright-harness');
const source = fs.readFileSync(path.join(__dirname, 'deb-login-playwright-checks.js'), 'utf8');
const setup = source.slice(source.indexOf('const baseUrl ='), source.indexOf('function credentials()'));

function loginUrl(env) {
  return vm.runInNewContext(`${setup}\nappUrl('/');`, {
    process: { env }, assert, validateBaseUrl: raw => validateBaseUrl(raw, env),
  });
}

for (const base of ['https://127.0.0.1', 'https://127.0.0.1/', 'https://127.0.0.1/carlos', 'https://127.0.0.1/carlos/']) {
  test(`packaged login uses one application context for ${base}`, () => {
    assert.equal(loginUrl({ BASE_URL: base }), 'https://127.0.0.1/carlos/');
  });
}

test('packaged login refuses plain HTTP and embedded credentials before browser launch', () => {
  assert.throws(() => loginUrl({ BASE_URL: 'http://127.0.0.1' }), /requires HTTPS/);
  assert.throws(() => loginUrl({ BASE_URL: 'https://user:password@127.0.0.1' }), /must not embed/);
});

test('packaged login requires explicit opt-in for a remote disposable target', () => {
  assert.throws(() => loginUrl({ BASE_URL: 'https://fixture.invalid' }), /non-loopback/);
  assert.equal(loginUrl({ BASE_URL: 'https://fixture.invalid/carlos', ALLOW_NON_LOCAL_BASE_URL: 'true' }),
    'https://fixture.invalid/carlos/');
});
