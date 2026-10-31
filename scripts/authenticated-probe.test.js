/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { authenticatedProbe } = require('./lib/authenticated-probe');
const cookie = { name: 'JSESSIONID', value: 'synthetic', domain: '127.0.0.1', path: '/carlos', secure: true, expires: -1 };
function fixture(cookies = [cookie], status = 405) {
  const calls = [];
  const session = { config: { baseUrl: new URL('http://127.0.0.1/carlos') }, context: {
    cookies: async () => cookies,
    request: { fetch: async (...args) => { calls.push(args); return { status: () => status }; } },
  } };
  return { session, calls };
}
for (const method of ['GET', 'HEAD']) {
  test(`${method} explicitly carries the Secure browser session over HTTP loopback`, async () => {
    const { session, calls } = fixture();
    await authenticatedProbe(session, '/carlos/save', { method, maxRedirects: 5 });
    assert.equal(calls[0][1].headers.Cookie, 'JSESSIONID=synthetic');
    assert.equal(calls[0][1].maxRedirects, 0);
  });
}
test('does not forward unrelated, expired or wrong-path cookies', async () => {
  const { session, calls } = fixture([cookie,
    { ...cookie, name: 'other', domain: 'other.invalid' },
    { ...cookie, name: 'wrongpath', path: '/carlosx' },
    { ...cookie, name: 'expired', expires: 1 },
  ]);
  await authenticatedProbe(session, '/carlos/save');
  assert.equal(calls[0][1].headers.Cookie, 'JSESSIONID=synthetic');
});
test('rejects missing session, foreign origins and mutating methods before sending', async () => {
  const { session, calls } = fixture([]);
  await assert.rejects(authenticatedProbe(session, '/carlos/save'), /session cookie/);
  await assert.rejects(authenticatedProbe(session, 'https://other.invalid/carlos/save'), /origin/);
  await assert.rejects(authenticatedProbe(session, '/carlos/save', { method: 'POST' }), /GET and HEAD/);
  assert.equal(calls.length, 0);
});
test('rejects login redirects even for callers checking only unchanged rows', async () => {
  const { session } = fixture([cookie], 302);
  await assert.rejects(authenticatedProbe(session, '/carlos/save'), /redirected/);
});

test('real Playwright HTTP transport sends the Secure cookie to a local refusal endpoint', async t => {
  const http = require('node:http');
  const { request } = require('playwright');
  const seen = [];
  const server = http.createServer((req, res) => {
    seen.push({method: req.method, cookie: req.headers.cookie});
    res.writeHead(req.headers.cookie === 'JSESSIONID=synthetic' ? 405 : 302,
      {'x-permitted-cross-domain-policies': 'none', location: '/login'});
    res.end();
  });
  await new Promise((resolve, reject) => {server.once('error', reject); server.listen(0, '127.0.0.1', resolve);});
  t.after(() => new Promise(resolve => server.close(resolve)));
  const baseUrl = new URL(`http://127.0.0.1:${server.address().port}/carlos`);
  const context = await request.newContext({storageState: {cookies: [{...cookie, httpOnly: true, sameSite: 'Lax'}], origins: []}});
  t.after(() => context.dispose());
  const session = {config: {baseUrl}, context: {
    cookies: async () => (await context.storageState()).cookies,
    request: context,
  }};
  for (const method of ['GET', 'HEAD']) {
    const response = await authenticatedProbe(session, '/carlos/save', {method});
    assert.equal(response.status(), 405);
    await response.dispose();
  }
  assert.deepEqual(seen, ['GET', 'HEAD'].map(method => ({method, cookie: 'JSESSIONID=synthetic'})));
});
