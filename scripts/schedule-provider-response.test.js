/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const { EventEmitter } = require('node:events');

/** Load the real selection workflow without starting its standalone browser entry point. */
function subject() {
  const source = fs.readFileSync(path.join(__dirname, 'schedule-links-playwright-checks.js'), 'utf8');
  const sandbox = {
    process: { env: {} }, URL, URLSearchParams, setTimeout, clearTimeout, console,
    require: name => {
      if (name === './lib/legacy-check') return { runLegacyCheck() {} };
      if (name === 'playwright') return { chromium: {} };
      if (name === './lib/playwright-ui') return {};
      assert.equal(name, './lib/playwright-harness');
      return { validateBaseUrl: value => new URL(value), createRecorder: () => ({}), wireStrictPage() {} };
    },
  };
  vm.runInNewContext(source + '\nglobalThis.selection = selectProviderFromLastNameSearch;'
    + '\nglobalThis.observations = { findings, visited };', sandbox, { filename: 'schedule-links-playwright-checks.js' });
  return sandbox;
}

/** Model a selection response followed immediately by the popup's normal self.close(). */
function fixture({ status = 200, responds = true, csrf = true, mismatchedRequest = false } = {}) {
  const events = new EventEmitter();
  const waits = new Set();
  const context = {
    waitForEvent: (name, { predicate = () => true } = {}) => new Promise((resolve, reject) => {
      const waiter = { name, reject: error => { events.off(name, listener); waits.delete(waiter); reject(error); } };
      const listener = value => {
        if (!predicate(value)) return;
        events.off(name, listener); waits.delete(waiter); resolve(value);
      };
      waits.add(waiter); events.on(name, listener);
    }),
  };
  let lateResponseCalls = 0;
  let selected = false;
  let closed = false;
  let openerSettled = false;
  const url = 'http://127.0.0.1:8080/carlos/provider/providercontrol';
  const request = {
    method: () => 'POST', url: () => url,
    postData: () => csrf ? 'dboperation=updatepreference&SCHEDULE-TOKEN=synthetic-token' : 'dboperation=updatepreference',
    response: async () => { lateResponseCalls++; throw new Error('Target page, context or browser has been closed'); },
  };
  const popup = {
    on() {}, url: () => url, isClosed: () => closed,
    waitForLoadState: async () => { if (closed) throw new Error('page closed'); },
    close: async () => { closed = true; },
    locator: selector => {
      if (selector === 'a[onclick*="selectProvider("]') return { first: () => ({ click: async () => {
        selected = true;
        events.emit('request', request);
        if (responds) events.emit('response', {
          request: () => mismatchedRequest ? { ...request } : request,
          status: () => status, url: () => url,
        });
        closed = true;
        // Expire only an unfulfilled response wait, without slowing a missing-response test.
        for (const waiter of [...waits]) if (waiter.name === 'response') waiter.reject(new Error('timeout'));
      } }) };
      if (selector === '#providerSelectionCsrfToken') return { getAttribute: async () => 'SCHEDULE-TOKEN' };
      assert.equal(selector, 'body');
      return { innerText: async () => { if (closed) throw new Error('page closed'); return ''; } };
    },
  };
  const page = {
    locator: selector => {
      assert.equal(selector, 'form[name="findprovider"] input[name="providername"]');
      return { count: async () => 1, fill: async () => {}, press: async () => {
        events.emit('page', popup);
        events.emit('response', {
          request: () => ({ method: () => 'GET', resourceType: () => 'document' }),
          status: () => 200,
          url: () => 'http://127.0.0.1:8080/carlos/provider/ViewReceptionistFindProvider',
          text: async () => '<input id="providerSelectionCsrfToken" name="SCHEDULE-TOKEN">',
        });
      } };
    },
    waitForLoadState: async state => { assert.equal(state, 'networkidle'); openerSettled = true; },
  };
  return { context, page, state: () => ({ selected, closed, openerSettled, lateResponseCalls, pendingWaits: waits.size }) };
}

for (const scenario of [
  { name: 'captures the response before the provider popup closes', options: {}, finding: null },
  { name: 'rejects an HTTP error even when the provider popup closes', options: { status: 500 }, finding: 'http' },
  { name: 'rejects a missing response after the provider popup closes', options: { responds: false }, finding: 'provider-post-no-response' },
  { name: 'rejects a missing CSRF token after the provider popup closes', options: { csrf: false }, finding: 'missing-csrf-token' },
]) {
  test(`schedule selection ${scenario.name}`, async () => {
    const s = subject(); const f = fixture(scenario.options);
    await s.selection(f.context, f.page);
    assert.deepEqual(f.state(), { selected: true, closed: true, openerSettled: true, lateResponseCalls: 0, pendingWaits: 0 });
    const findings = JSON.parse(JSON.stringify(s.observations.findings));
    assert.deepEqual(findings.map(row => row.type), scenario.finding ? [scenario.finding] : []);
    const selection = s.observations.visited.find(row => row.label === 'schedule-provider-search');
    assert.equal(selection.status, scenario.options.responds === false ? null : scenario.options.status || 200);
  });
}

test('schedule selection rejects a response from a different request', async () => {
  const s = subject(); const f = fixture({ mismatchedRequest: true });
  await assert.rejects(s.selection(f.context, f.page), /response belongs to a different request/);
  assert.equal(f.state().lateResponseCalls, 0);
  assert.equal(f.state().pendingWaits, 0);
});
