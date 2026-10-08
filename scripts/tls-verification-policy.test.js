/* SPDX-License-Identifier: GPL-2.0-or-later */
// Issue #3598: certificate verification may only be waived for a local target, or
// by the dedicated ALLOW_UNVERIFIED_TLS variable -- never as a side effect of
// ALLOW_NON_LOCAL_BASE_URL, and never as a literal `true` in a check.
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  explainTlsFailure, readConfig, shouldIgnoreHttpsErrors,
} = require('./lib/playwright-harness');

const REMOTE = 'https://staging.example.org/carlos';

test('a local target waives certificate checks without any opt-in', () => {
  for (const url of ['http://127.0.0.1:8080/carlos', 'https://localhost/carlos', 'https://[::1]/carlos', 'https://192.168.1.5/carlos']) {
    assert.equal(shouldIgnoreHttpsErrors(url, {}), true, url);
    assert.equal(shouldIgnoreHttpsErrors(new URL(url), {}), true, url);
  }
});

test('ALLOW_NON_LOCAL_BASE_URL alone does not waive certificate checks', () => {
  assert.equal(shouldIgnoreHttpsErrors(REMOTE, { ALLOW_NON_LOCAL_BASE_URL: 'true' }), false);
  assert.equal(shouldIgnoreHttpsErrors(new URL(REMOTE), {}), false);
});

test('ALLOW_UNVERIFIED_TLS=true is the only way to waive checks for a remote target', () => {
  assert.equal(shouldIgnoreHttpsErrors(REMOTE, { ALLOW_UNVERIFIED_TLS: 'true' }), true);
  for (const value of ['1', 'yes', 'TRUE', '', 'false']) {
    assert.equal(shouldIgnoreHttpsErrors(REMOTE, { ALLOW_UNVERIFIED_TLS: value }), false, value);
  }
});

test('lookalike hosts and unparseable values fail closed', () => {
  assert.equal(shouldIgnoreHttpsErrors('https://10.example.com/carlos', {}), false);
  assert.equal(shouldIgnoreHttpsErrors('https://localhost.evil.example/carlos', {}), false);
  assert.equal(shouldIgnoreHttpsErrors('not a url', {}), false);
});

test('with no argument the target comes from BASE_URL, defaulting to loopback', () => {
  assert.equal(shouldIgnoreHttpsErrors(undefined, {}), true);
  assert.equal(shouldIgnoreHttpsErrors(undefined, { BASE_URL: REMOTE, ALLOW_NON_LOCAL_BASE_URL: 'true' }), false);
  assert.equal(shouldIgnoreHttpsErrors(undefined, { BASE_URL: REMOTE, ALLOW_UNVERIFIED_TLS: 'true' }), true);
});

test('readConfig applies the same policy', () => {
  const env = { BASE_URL: REMOTE, ALLOW_NON_LOCAL_BASE_URL: 'true' };
  assert.equal(readConfig({ env }).ignoreHTTPSErrors, false);
  assert.equal(readConfig({ env: { ...env, ALLOW_UNVERIFIED_TLS: 'true' } }).ignoreHTTPSErrors, true);
});

test('a certificate failure against a remote target names ALLOW_UNVERIFIED_TLS', () => {
  const raw = new Error('page.goto: net::ERR_CERT_AUTHORITY_INVALID at https://staging.example.org/carlos/');
  const explained = explainTlsFailure(raw, new URL(REMOTE), {});
  assert.notEqual(explained, raw);
  assert.match(explained.message, /ALLOW_UNVERIFIED_TLS=true/);
  assert.match(explained.message, /staging\.example\.org/);
  assert.match(explained.message, /ERR_CERT_AUTHORITY_INVALID/);
});

test('other failures, and failures where the waiver is already on, pass through unchanged', () => {
  const timeout = new Error('page.goto: Timeout 30000ms exceeded');
  assert.equal(explainTlsFailure(timeout, new URL(REMOTE), {}), timeout);
  const cert = new Error('net::ERR_CERT_COMMON_NAME_INVALID');
  assert.equal(explainTlsFailure(cert, new URL(REMOTE), { ALLOW_UNVERIFIED_TLS: 'true' }), cert);
  assert.equal(explainTlsFailure(cert, new URL('https://127.0.0.1/carlos'), {}), cert);
});

// login-failure-host-header drives rejectUnauthorized and ignoreHTTPSErrors from the same
// predicate and pins that pairing in its own test (login-failure-host-header-tls-scope.test.js).
const OWN_POLICY = new Set(['login-failure-host-header-playwright-checks.js']);
const SHARED_POLICY = /^(?:shouldIgnoreHttpsErrors\(|(?:s\.)?config\.ignoreHTTPSErrors\b)/;

test('every ignoreHTTPSErrors value comes from the shared policy (issue #3598)', () => {
  const offenders = [];
  const scan = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (entry.name !== 'node_modules') scan(full);
        continue;
      }
      if (!entry.name.endsWith('.js') || entry.name.endsWith('.test.js')
          || full === path.join(__dirname, 'lib', 'playwright-harness.js') || OWN_POLICY.has(entry.name)) continue;
      const source = fs.readFileSync(full, 'utf8').replace(/\/\*[\s\S]*?\*\/|\/\/.*$/gm, '');
      for (const match of source.matchAll(/ignoreHTTPSErrors\s*:\s*([^,}\n]+)/g)) {
        if (!SHARED_POLICY.test(match[1].trim())) {
          offenders.push(`${path.relative(__dirname, full)}: ${match[0].trim()}`);
        }
      }
    }
  };
  scan(__dirname);
  assert.deepEqual(offenders, [], 'route TLS verification through shouldIgnoreHttpsErrors() so ALLOW_UNVERIFIED_TLS is honoured everywhere');
});
