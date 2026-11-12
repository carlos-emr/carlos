/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');

// Execute the real local witness without starting the login script, creating
// its SQL credential file, or importing browser fixtures.
const source = fs.readFileSync(path.join(__dirname, 'login-playwright-checks.js'), 'utf8');
const start = source.indexOf('async function expectMissingCsrfRefusal(');
assert(start >= 0, 'Login must define its bounded missing-CSRF witness');
const expectRefusal = vm.runInNewContext(
  `${source.slice(start, source.indexOf('\n}', start) + 2)}\nexpectMissingCsrfRefusal;`,
  { assert: h.assert, h, URLSearchParams },
);

const endpoint = 'https://127.0.0.1/carlos/forcepasswordresetSubmit';
const form = { forcedpasswordchange: 'true', oldPassword: 'synthetic-old', newPassword: 'synthetic-new' };
const formBody = new URLSearchParams(form).toString();

function fixture() {
  const recorder = h.createRecorder();
  const page = new EventEmitter();
  h.wireStrictPage(page, 'missing-CSRF', recorder, { baseline: [] });
  function respond(options = {}) {
    const body = options.body ?? '<html><p>CARLOS Error: 403</p></html>';
    const status = options.status ?? 403;
    const request = {
      method: () => options.method ?? 'POST', url: () => options.url ?? endpoint,
      postData: () => options.postData ?? formBody, resourceType: () => 'fetch',
    };
    const response = {
      request: () => request, url: request.url, status: () => status,
      headers: () => options.headers ?? { 'content-type': 'text/html', [h.APPLICATION_HEADER]: 'none' },
      text: async () => body, finished: async () => null,
    };
    page.emit('response', response);
    if (options.console !== false) {
      page.emit('console', {
        type: () => 'error', text: () => `Failed to load resource: the server responded with a status of ${status} (Forbidden)`,
        location: () => ({ url: request.url() }),
      });
    }
    return { response, result: { status, url: response.url(), text: body.slice(0, 300) } };
  }
  return { page, recorder, respond };
}

test('only the exact proven CSRF response and its resource diagnostic are acknowledged', async () => {
  const f = fixture();
  let verified = false;
  await expectRefusal(f.page, f.recorder, endpoint, form,
    async () => f.respond().result, async () => { verified = true; });
  assert.equal(verified, true);
  h.assertStrictPage(f.recorder);
  assert.equal(f.page.listenerCount('response'), 1, 'the local witness must unregister itself');
});

test('a different request to the same endpoint stays fatal alongside the owned refusal', async () => {
  const f = fixture();
  let unrelated;
  await expectRefusal(f.page, f.recorder, endpoint, form, async () => {
    unrelated = f.respond({ postData: 'unrelated=1', console: false });
    return f.respond().result;
  }, () => {});
  assert.equal(f.recorder.badResponses.length, 1);
  assert.equal(f.recorder.badResponses[0].url, unrelated.response.url());
  assert.throws(() => h.assertStrictPage(f.recorder), /HTTP 403/);
});

test('preexisting findings and unrelated same-URL JavaScript errors remain fatal', async () => {
  const f = fixture();
  const prior = { label: 'earlier', method: 'GET', status: 500, url: endpoint };
  f.recorder.badResponses.push(prior);
  await expectRefusal(f.page, f.recorder, endpoint, form, async () => {
    f.page.emit('console', { type: () => 'error', text: () => 'TypeError: reset handler failed', location: () => ({ url: endpoint }) });
    return f.respond().result;
  }, () => {});
  assert.deepEqual(f.recorder.badResponses, [prior]);
  assert.equal(f.recorder.consoleIssues.length, 1);
  assert.throws(() => h.assertStrictPage(f.recorder), /reset handler failed/);
});

test('duplicate owned responses fail without removing either HTTP finding', async () => {
  const f = fixture();
  await assert.rejects(expectRefusal(f.page, f.recorder, endpoint, form, async () => {
    f.respond(); return f.respond().result;
  }, () => {}), /exactly one/);
  assert.equal(f.recorder.badResponses.length, 2);
  assert.equal(f.page.listenerCount('response'), 1);
});

for (const [name, options] of [
  ['a server error', { status: 500 }],
  ['a frontend WAF page', { body: '<html>Forbidden ModSecurity</html>' }],
  ['a response without application evidence', { headers: { 'content-type': 'text/html' } }],
  ['the wrong generic error status', { body: '<html><p>CARLOS Error: 500</p></html>' }],
  ['a container-style Forbidden page', { body: '<html>HTTP Status 403 – Forbidden</html>' }],
  ['an unrelated body', { body: '<html>Different response</html>' }],
]) {
  test(`${name} is refused without acknowledging its diagnostic`, async () => {
    const f = fixture();
    await assert.rejects(expectRefusal(f.page, f.recorder, endpoint, form,
      async () => f.respond(options).result, () => {}), /HTTP 403|application error evidence/);
    assert.equal(f.recorder.badResponses.length, 1);
    assert.equal(f.page.listenerCount('response'), 1);
  });
}

test('a mismatching browser body cannot acknowledge a captured 403', async () => {
  const f = fixture();
  await assert.rejects(expectRefusal(f.page, f.recorder, endpoint, form, async () => {
    const { result } = f.respond(); return { ...result, text: 'a different response' };
  }, () => {}), /exact HTTP 403/);
  assert.equal(f.recorder.badResponses.length, 1);
});

test('failed database preservation proof leaves every refusal finding in place', async () => {
  const f = fixture();
  await assert.rejects(expectRefusal(f.page, f.recorder, endpoint, form,
    async () => f.respond().result, () => { throw new Error('security row changed'); }), /security row changed/);
  assert.equal(f.recorder.badResponses.length, 1);
  assert.equal(f.recorder.consoleIssues.length, 1);
});

test('a send failure always unregisters the temporary witness', async () => {
  const f = fixture();
  await assert.rejects(expectRefusal(f.page, f.recorder, endpoint, form,
    async () => { throw new Error('request failed'); }, () => {}), /request failed/);
  assert.equal(f.page.listenerCount('response'), 1);
});
