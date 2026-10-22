/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// login-failure-host-header-playwright-checks.js reads /loginfailed back over a raw
// socket and compares two responses byte for byte. If certificate verification were
// disabled against an arbitrary target, a man-in-the-middle could serve both halves
// of that comparison and every assertion in the check would pass vacuously. So the
// exemption is bounded to loopback, where the packaged install legitimately presents
// its own self-signed cert -- the same rule allergy-rx-alert and billing-on-third-party
// apply to their browser contexts.
//
// The predicate is evaluated here out of the real source rather than re-declared, so
// this pins the shipped code and not a copy of it.
const SOURCE = fs.readFileSync(
  path.join(__dirname, 'login-failure-host-header-playwright-checks.js'),
  'utf8',
);

function isLoopbackTargetFor(baseUrl) {
  const start = SOURCE.indexOf('const LOOPBACK_HOSTS =');
  assert.ok(start >= 0, 'LOOPBACK_HOSTS is no longer declared in the check script');
  const end = SOURCE.indexOf(');', SOURCE.indexOf('const isLoopbackTarget =', start)) + 2;
  const context = vm.createContext({ config: { baseUrl: new URL(baseUrl) } });
  // `const` declarations do not become properties of a vm context's global, so ask
  // for the binding back as the completion value rather than reading it off `context`.
  const isLoopbackTarget = vm.runInContext(`${SOURCE.slice(start, end)}\nisLoopbackTarget;`, context);
  return isLoopbackTarget();
}

test('login-failure host header check treats every loopback spelling as local', () => {
  for (const baseUrl of [
    'https://127.0.0.1:443/carlos',
    'http://127.0.0.1:8080/carlos',
    'https://localhost/carlos',
    'https://LOCALHOST/carlos',
    'https://[::1]/carlos',
    'https://[0:0:0:0:0:0:0:1]/carlos',
  ]) {
    assert.equal(isLoopbackTargetFor(baseUrl), true, baseUrl);
  }
});

test('login-failure host header check keeps certificate verification on for non-loopback targets', () => {
  for (const baseUrl of [
    'https://emr.example.org/carlos',
    'https://10.0.0.5/carlos',
    'https://192.168.1.20:443/carlos',
    'https://host.docker.internal/carlos',
  ]) {
    assert.equal(isLoopbackTargetFor(baseUrl), false, baseUrl);
  }
});

test('login-failure host header check never hard-codes a disabled TLS check', () => {
  // rejectUnauthorized and ignoreHTTPSErrors must both be driven by the predicate;
  // a bare `false` / `true` literal here is the regression this pins.
  assert.match(SOURCE, /rejectUnauthorized: !isLoopbackTarget\(\),/);
  assert.match(SOURCE, /ignoreHTTPSErrors: isLoopbackTarget\(\)/);
  assert.doesNotMatch(SOURCE, /rejectUnauthorized:\s*false/);
  assert.doesNotMatch(SOURCE, /ignoreHTTPSErrors:\s*true/);
});
