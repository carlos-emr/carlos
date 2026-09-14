/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// login-failure-host-header-playwright-checks.js is documented as requiring the
// packaged :443 front door, because an nginx rewrite re-introducing a <base href> is
// exactly the case its source-level sibling cannot see. Its default BASE_URL is bare
// Tomcat, though, so without a signal a standalone run reports PASS while covering
// only what the application emits -- the reassuring half of the claim. The check
// therefore reports the layer it actually exercised: a WARNING when no response
// carried an nginx Server header, escalated to a failure by EXPECT_FRONT_DOOR=true.
//
// These read the real source rather than re-declaring the predicate, so they pin the
// shipped code and not a copy of it.
const SOURCE = fs.readFileSync(
  path.join(__dirname, 'login-failure-host-header-playwright-checks.js'),
  'utf8',
);

// `const` declarations do not become properties of a vm context's global, so each
// slice asks for the binding back as the completion value.
function evaluateBinding(startMarker, endMarker, name, sandbox = {}) {
  const start = SOURCE.indexOf(startMarker);
  assert.ok(start >= 0, `${startMarker} is no longer declared in the check script`);
  const end = SOURCE.indexOf(';', SOURCE.indexOf(endMarker, start)) + 1;
  return vm.runInContext(`${SOURCE.slice(start, end)}\n${name};`, vm.createContext(sandbox));
}

function sawFrontDoorFor(headers) {
  const sawFrontDoor = evaluateBinding(
    'const FRONT_DOOR_SERVER =',
    'const sawFrontDoor =',
    'sawFrontDoor',
  );
  return sawFrontDoor(headers);
}

function isFrontDoorRejectionFor(frontDoorObserved, status) {
  const isFrontDoorRejection = evaluateBinding(
    'const FRONT_DOOR_REJECTION_STATUSES =',
    'const isFrontDoorRejection =',
    'isFrontDoorRejection',
  );
  return isFrontDoorRejection(frontDoorObserved, status);
}

function expectFrontDoorFor(value) {
  return evaluateBinding(
    'const expectFrontDoor =',
    'const expectFrontDoor =',
    'expectFrontDoor',
    { process: { env: value === undefined ? {} : { EXPECT_FRONT_DOOR: value } } },
  );
}

test('login-failure host header check recognises an nginx-served response as the front door', () => {
  for (const server of ['nginx', 'nginx/1.24.0', 'NGINX/1.26.2 (Ubuntu)']) {
    assert.equal(sawFrontDoorFor({ server }), true, server);
  }
});

test('login-failure host header check does not mistake bare Tomcat for the front door', () => {
  // Node lowercases response header names, so `server` is the only spelling that can
  // arrive; a capitalised key must not count, or the guard would pass on a header the
  // runtime never produces.
  assert.equal(sawFrontDoorFor({}), false, 'no Server header at all');
  assert.equal(sawFrontDoorFor({ server: '' }), false, 'empty Server header');
  assert.equal(sawFrontDoorFor({ server: 'Apache-Coyote/1.1' }), false, 'Tomcat');
  assert.equal(sawFrontDoorFor({ Server: 'nginx/1.24.0' }), false, 'capitalised key');
});

test('login-failure host header check escalates to a failure only when EXPECT_FRONT_DOOR is set', () => {
  for (const value of ['1', 'true', 'TRUE', 'yes', 'Yes']) {
    assert.equal(expectFrontDoorFor(value), true, value);
  }
  for (const value of [undefined, '', '0', 'false', 'no', 'maybe']) {
    assert.equal(expectFrontDoorFor(value), false, String(value));
  }
});

test('login-failure host header check still reports the layer it covered when the front door is absent', () => {
  // The warning is what keeps a standalone run from reading as full coverage, and the
  // throw is what EXPECT_FRONT_DOOR buys. Both are the regression this pins.
  assert.match(SOURCE, /const frontDoorObserved = sawFrontDoor\(honest\.headers\);/);
  assert.match(SOURCE, /if \(expectFrontDoor && !frontDoorObserved\) \{\s*\n\s*throw new Error\(/);
  assert.match(SOURCE, /WARNING: no response carried an nginx Server header/);
  // The PASS line must say which layer was covered, so a green log is not ambiguous.
  assert.match(SOURCE, /frontDoorObserved\s*\n?\s*\?\s*' -- through the packaged nginx front door/);
});

test('login-failure host header check excuses the spoofed Host only on an nginx-served 400 or 421', () => {
  assert.equal(isFrontDoorRejectionFor(true, 400), true, 'nginx 400');
  assert.equal(isFrontDoorRejectionFor(true, 421), true, 'nginx 421');
});

test('login-failure host header check asserts on any other Host-dependent response', () => {
  // The hole this closes: `spoofed.status >= 400` treated an application-generated
  // 404/500 as the front door refusing the Host, so a page that answered DIFFERENTLY
  // because of the Host header -- the defect under test -- skipped the comparison and
  // the run went green. Only nginx-served 400/421 may short-circuit it.
  for (const status of [403, 404, 429, 500, 502, 503]) {
    assert.equal(isFrontDoorRejectionFor(true, status), false, `nginx ${status}`);
  }
  for (const status of [400, 421, 404, 500]) {
    assert.equal(isFrontDoorRejectionFor(false, status), false, `no front door, ${status}`);
  }
  assert.equal(isFrontDoorRejectionFor(true, 200), false, 'nginx 200');
});

test('login-failure host header check compares the status as well as the body', () => {
  // A differing status is already a Host-dependent response; without this assertion the
  // narrowed rejection rule would fall through to a byte diff between an error page and
  // the real one, which reads as a confusing failure rather than the real finding.
  assert.match(SOURCE, /spoofed\.status === honest\.status,/);
  assert.match(SOURCE, /const spoofRejectedAtFrontDoor = isFrontDoorRejection\(frontDoorObserved, spoofed\.status\);/);
  assert.doesNotMatch(SOURCE, /if \(spoofed\.status >= 400\)/);
});
