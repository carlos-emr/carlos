/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function openResolve() {
  const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/admin/manageEmails.jsp'), 'utf8');
  const start = source.indexOf('        function resolve(emailLogId) {');
  const end = source.indexOf('        function resend(emailLogId)', start);
  assert.ok(start >= 0 && end > start, 'The production resolve function is unavailable');
  const state = {status: 'FAILED', removed: false, alerts: [], busy: false, confirmed: true};
  let request;
  const $ = selector => ({
    attr: () => '/carlos/admin/ManageEmails',
    removeClass() { return this; },
    addClass() { return this; },
    remove() { state.removed = true; },
    text(value) { assert.equal(selector, '#emailStatus7003'); state.status = value; },
  });
  $.ajax = options => { request = options; };
  const context = vm.createContext({$, confirm: () => state.confirmed,
    ShowSpin: () => { state.busy = true; }, HideSpin: () => { state.busy = false; },
    alert: message => state.alerts.push(message), manageEmailsResolveConfirm: 'Confirm',
    manageEmailsResolved: 'RESOLVED', manageEmailsServerError: 'Resolution failed'});
  vm.runInContext(source.slice(start, end), context);
  return {state, click: () => vm.runInContext('resolve(7003)', context),
    request: () => request,
    receive(body) {
      if (request.dataType === 'json' && typeof body === 'string') {
        try { body = JSON.parse(body); }
        catch { request.error({}, 'parsererror', 'Invalid JSON'); return; }
      }
      request.success(body);
    },
  };
}

for (const [label, body] of [
  ['a followed sign-in page', '<html><form id="loginForm"></form></html>'],
  ['an empty response', ''],
  ['an unrelated JSON response', {}],
  ['an acknowledgement for another row', {resolvedLogId: '7004'}],
]) test(`Resolve preserves FAILED and its button after ${label}`, () => {
  const page = openResolve();
  page.click();
  page.receive(body);
  assert.equal(page.state.status, 'FAILED');
  assert.equal(page.state.removed, false);
  assert.equal(page.state.busy, false);
  assert.deepEqual(page.state.alerts, ['Resolution failed']);
});

test('Resolve updates the card only after the requested row is acknowledged', () => {
  const page = openResolve();
  page.click();
  assert.equal(page.request().method, 'POST');
  assert.equal(page.request().data, 'method=setResolved&logId=7003');
  page.receive({resolvedLogId: '7003'});
  assert.equal(page.state.status, 'RESOLVED');
  assert.equal(page.state.removed, true);
  assert.equal(page.state.busy, false);
  assert.deepEqual(page.state.alerts, []);
});

test('Resolve keeps the unresolved card after a transport refusal', () => {
  const page = openResolve();
  page.click();
  page.request().error({status: 403}, 'error', 'Forbidden');
  assert.equal(page.state.status, 'FAILED');
  assert.equal(page.state.removed, false);
  assert.equal(page.state.busy, false);
  assert.deepEqual(page.state.alerts, ['Resolution failed']);
});

test('cancelling Resolve sends no request and leaves the card intact', () => {
  const page = openResolve();
  page.state.confirmed = false;
  page.click();
  assert.equal(page.request(), undefined);
  assert.equal(page.state.status, 'FAILED');
  assert.equal(page.state.removed, false);
  assert.equal(page.state.busy, false);
});
