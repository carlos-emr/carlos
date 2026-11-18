/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const h = require('./lib/playwright-harness');
const { isVerifiedAttackRefusal } = require('./boundary-demographic-search-playwright-checks');

function response(status, body, headers = {}) {
  return { status: () => status, text: async () => body, headers: () => headers };
}

test('only an identifiable front-door 403 may bypass the attack-result assertions', async () => {
  for (const body of ['<center>nginx</center>', 'ModSecurity: Access denied']) {
    assert.equal(await isVerifiedAttackRefusal(response(403, body)), true);
  }
});

test('application success keeps the browser result and encoding assertions required', async () => {
  assert.equal(await isVerifiedAttackRefusal(response(200, 'results', { [h.APPLICATION_HEADER]: 'none' })), false);
});

test('CSRF, privilege, unknown refusal, redirects and server failures cannot pass as WAF blocks', async () => {
  for (const [status, body, headers] of [
    [403, 'CSRF token invalid', { [h.APPLICATION_HEADER]: 'none' }],
    [403, 'Security Exception: insufficient privileges', { [h.APPLICATION_HEADER]: 'none' }],
    [403, 'Forbidden', {}],
    [403, '<center>nginx</center>', { [h.APPLICATION_HEADER]: 'none' }],
    [403, 'ModSecurity', { [h.APPLICATION_HEADER]: 'none' }],
    [302, 'login', {}], [500, 'ModSecurity', {}], [200, '<center>nginx</center>', {}],
  ]) {
    await assert.rejects(isVerifiedAttackRefusal(response(status, body, headers)), /verified WAF refusal/);
  }
});
