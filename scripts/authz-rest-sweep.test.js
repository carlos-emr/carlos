/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readOAuthMode, checkOAuthAccess, assertOAuthResourceResult } = require('./authz-rest-sweep-playwright-checks');

test('OAuth sweep expects the restricted legacy default and rejects a misspelled mode', () => {
  assert.equal(readOAuthMode({}), 'legacy-restricted');
  for (const mode of ['legacy-restricted', 'scoped', 'legacy-full']) {
    assert.equal(readOAuthMode({ EXPECT_OAUTH_MODE: mode }), mode);
  }
  assert.throws(() => readOAuthMode({ EXPECT_OAUTH_MODE: 'scope' }), /EXPECT_OAUTH_MODE/);
});

// Responses from the two independent boundaries: OAuth policy and the service's provider guard.
// The legacy control reads the owned patient; the scoped tokens deliberately lack demographic.read.
function accessFixture(mode, override = () => undefined) {
  const calls = [];
  return {
    calls,
    async call(who, route) {
      calls.push([who, route]);
      const replaced = override(who, route);
      if (replaced) return replaced;
      if (route === 'jobs/all') return { status: 403, fromApp: true, text: 'blocked_endpoint' };
      if (route === 'demographics/42') return mode === 'scoped'
        ? { status: 403, fromApp: true, text: 'insufficient_scope' }
        : { status: 200, fromApp: true, json: { demographicNo: 42 } };
      if (mode === 'legacy-restricted') return { status: 403, fromApp: true, text: 'restricted_endpoint' };
      if (who === 'full') return { status: 200, fromApp: true, json: {} };
      return { status: 403, fromApp: true, text: route === 'pharmacies/' ? 'Access denied' : '' };
    },
  };
}

for (const mode of ['legacy-restricted', 'scoped', 'legacy-full']) {
  test(`OAuth sweep exercises both callers and all policy/service probes in ${mode} mode`, async () => {
    const fixture = accessFixture(mode);
    await checkOAuthAccess({ mode, patient: '42', call: fixture.call });
    for (const route of ['demographics/42', 'jobs/all', 'app/getApps/', 'recordUX/42/getAllergies', 'pharmacies/', 'forms/allEForms']) {
      for (const who of ['full', 'receptionist']) {
        assert.ok(fixture.calls.some(([caller, path]) => caller === who && path === route), `${who} never probed ${route}`);
      }
    }
  });
}

test('restricted legacy sweep requires a working patient control before accepting denials', async () => {
  for (const response of [
    { status: 403, fromApp: true, text: 'restricted_endpoint' },
    { status: 200, fromApp: true, json: { demographicNo: 99 } },
    { status: 200, fromApp: false, json: { demographicNo: 42 } },
  ]) {
    const fixture = accessFixture('legacy-restricted', (who, route) => route === 'demographics/42' && response);
    await assert.rejects(checkOAuthAccess({ mode: 'legacy-restricted', patient: '42', call: fixture.call }), /legacy control/);
  }
});

test('restricted legacy sweep fails if an app escapes the allowlist or the denial is from another layer', async () => {
  for (const response of [
    { status: 200, fromApp: true, json: [] },
    { status: 403, fromApp: true, text: 'insufficient_scope' },
    { status: 403, fromApp: true, text: '' },
    { status: 403, fromApp: false, text: 'restricted_endpoint' },
  ]) {
    const fixture = accessFixture('legacy-restricted', (who, route) => route === 'app/getApps/' && response);
    await assert.rejects(checkOAuthAccess({ mode: 'legacy-restricted', patient: '42', call: fixture.call }), /restricted_endpoint/);
  }
});

test('scoped sweep refuses a legacy-full server even when its service privilege guards work', async () => {
  const fixture = accessFixture('legacy-full');
  await assert.rejects(checkOAuthAccess({ mode: 'scoped', patient: '42', call: fixture.call }), /insufficient_scope/);
});

test('scoped service checks cannot pass on a restricted legacy server', async () => {
  const fixture = accessFixture('legacy-restricted');
  await assert.rejects(checkOAuthAccess({ mode: 'scoped', patient: '42', call: fixture.call }), /insufficient_scope/);
});

for (const mode of ['legacy-restricted', 'scoped', 'legacy-full']) {
  test(`OAuth sweep keeps always-blocked endpoints closed in ${mode} mode`, async () => {
    const fixture = accessFixture(mode, (who, route) => route === 'jobs/all'
      && { status: 200, fromApp: true, json: [] });
    await assert.rejects(checkOAuthAccess({ mode, patient: '42', call: fixture.call }), /blocked_endpoint/);
  });
}

for (const reason of ['restricted_endpoint', 'insufficient_scope', 'blocked_endpoint']) {
  test(`OAuth privilege sweep rejects outer policy denial: ${reason}`, () => {
    const result = { status: 403, fromApp: true, text: reason, json: null };
    assert.throws(() => assertOAuthResourceResult('receptionist', 'forms/allEForms', result, false), /service privilege refusal/);
    assert.throws(() => assertOAuthResourceResult('full', 'forms/allEForms', result, true), /control must reach the service/);
  });
}

test('OAuth privilege sweep accepts service responses and rejects missing mappers', () => {
  assertOAuthResourceResult('full', 'forms/allEForms', { status: 200, fromApp: true, json: {} }, true);
  assertOAuthResourceResult('receptionist', 'forms/allEForms', { status: 403, fromApp: true, text: '' }, false);
  assertOAuthResourceResult('receptionist', 'pharmacies/', { status: 403, fromApp: true, text: 'Access denied' }, false);
  for (const status of [200, 401, 500]) {
    assert.throws(() => assertOAuthResourceResult('receptionist', 'forms/allEForms', { status, fromApp: true, text: '' }, false));
  }
  assert.throws(() => assertOAuthResourceResult('full', 'forms/allEForms', { status: 200, fromApp: false, json: null }, true));
});
