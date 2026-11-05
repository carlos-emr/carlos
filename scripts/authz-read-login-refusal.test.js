/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');
const source = fs.readFileSync(path.join(__dirname, 'lib/authz-read-probe.js'), 'utf8');

function fixture({ status, recorded, scheduleAccess, headers = { [h.APPLICATION_HEADER]: 'none' },
  body = status === 403 ? 'Schedule unavailable' : 'CARLOS schedule', landing } = {}) {
  const recorder = h.createRecorder();
  const baseUrl = new URL('https://127.0.0.1/carlos');
  const url = 'https://127.0.0.1/carlos/provider/providercontrol';
  let disposed = false, closed = false;
  const context = { setDefaultTimeout() {}, async close() { closed = true; } };
  const module = { exports: {} };
  vm.runInNewContext(source, { URL, Buffer, module, require(name) {
    if (name === './playwright-harness') return { ...h, newContext: async () => context, login: async () => {
      if (recorded) recorder.badResponses.push({ status: 403, method: 'GET', url });
      return { url: () => landing || url };
    } };
    if (name === './authenticated-probe') return { authenticatedProbe: async () => ({ status: () => status,
      headers: () => headers, text: async () => body, dispose: async () => { disposed = true; } }) };
    if (name === './concurrency-support') return require('./lib/concurrency-support');
    return require(name);
  } });
  return { recorder, operation: () => module.exports.signIn({ context: { browser: () => ({}) }, config: { baseUrl }, recorder },
    { username: 'restricted', scheduleAccess }), disposed: () => disposed, closed: () => closed };
}

for (const status of [200, 403]) for (const recorded of [false, true]) {
  test(`fixture without schedule access requires application 403 plus recorded refusal: HTTP ${status}, recorded=${recorded}`, async () => {
    const f = fixture({ status, recorded, scheduleAccess: false });
    if (status === 403 && recorded) {
      await f.operation();
      assert.equal(f.recorder.badResponses.length, 0);
      assert.equal(f.closed(), false);
    } else {
      await assert.rejects(f.operation(), /authenticated application refusal|exactly one HTTP 403/);
      assert.equal(f.closed(), true, 'A failed sign-in must close its new context');
    }
    assert(f.disposed(), 'Every schedule proof response must be disposed');
  });
}

test('fixture with schedule access accepts authenticated 200 without excusing any browser failure', async () => {
  const f = fixture({ status: 200, recorded: true, scheduleAccess: true });
  await f.operation();
  assert.equal(f.recorder.badResponses.length, 1, 'An earlier 403 must remain visible for a role allowed to see the schedule');
  assert(f.disposed());
});

test('fixture with schedule access rejects an unexpected schedule 403', async () => {
  const f = fixture({ status: 403, recorded: true, scheduleAccess: true });
  await assert.rejects(f.operation(), /expected HTTP 200, got 403/);
  assert.equal(f.recorder.badResponses.length, 1);
  assert(f.closed());
});

test('a WAF or proxy 403 cannot qualify as the expected restricted schedule landing', async () => {
  const f = fixture({ status: 403, recorded: true, scheduleAccess: false, headers: {} });
  await assert.rejects(f.operation(), /authenticated application refusal/);
  assert.equal(f.recorder.badResponses.length, 1);
  assert(f.disposed());
});

test('a login or facility redirect cannot qualify as a schedule landing', async () => {
  const f = fixture({ status: 403, recorded: true, scheduleAccess: false, landing: 'https://127.0.0.1/carlos/login' });
  await assert.rejects(f.operation(), /outside the schedule landing/);
  assert(f.closed());
});
