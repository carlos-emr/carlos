/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const h = require('./lib/playwright-harness');
const { probeAttackKeyword } = require('./boundary-demographic-search-playwright-checks');

const fields = { 'CSRF-TOKEN': 'session-token', search_mode: 'search_name', keyword: 'control',
  dboperation: 'search_titlename', ptstatus: 'active', limit1: '0', limit2: '18' };
const baseUrl = new URL('https://example.test/carlos');
const attack = "' OR 1=1 --";
function context(status, body, headers = {}) {
  return { request: { async post(url, options) {
    assert.equal(url, 'https://example.test/carlos/demographic/DemographicSearch');
    assert.deepEqual(options, { form: { ...fields, search_mode: 'search_address', keyword: attack }, maxRedirects: 0 });
    return { status: () => status, text: async () => body, headers: () => headers };
  } } };
}

test('a verified nginx WAF block is a valid outcome for a deliberate attack', async () => {
  assert.equal(await probeAttackKeyword(context(403, '<center>nginx</center>'),
    baseUrl, fields, attack), true);
  assert.equal(fields.keyword, 'control');
});

test('application success still requires the browser result and encoding assertions', async () => {
  assert.equal(await probeAttackKeyword(context(200, 'results', { [h.APPLICATION_HEADER]: 'none' }),
    baseUrl, fields, attack), false);
});

test('CSRF, privilege, unknown refusal, redirects and server failures cannot pass as WAF blocks', async () => {
  for (const [status, body, headers] of [
    [403, 'CSRF token invalid', { [h.APPLICATION_HEADER]: 'none' }],
    [403, 'Security Exception: insufficient privileges', { [h.APPLICATION_HEADER]: 'none' }],
    [403, 'Forbidden', {}],
    [403, '<center>nginx</center>', { [h.APPLICATION_HEADER]: 'none' }],
    [302, 'login', {}], [500, 'server error', {}], [200, 'login', {}],
  ]) {
    await assert.rejects(probeAttackKeyword(context(status, body, headers),
      baseUrl, fields, attack), /verified WAF refusal/);
  }
});

test('the probe refuses to run without a real search form CSRF token', async () => {
  await assert.rejects(probeAttackKeyword({}, baseUrl, {}, attack), /CSRF token/);
});
