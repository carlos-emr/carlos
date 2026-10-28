/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function send(entries) {
  const form = { entries, addEventListener() {} };
  const request = { headers: {}, addEventListener() {}, open(method, url) { this.method = method; this.url = url; },
    setRequestHeader(name, value) { this.headers[name] = value; }, send(body) { this.body = body; } };
  class FormFixture extends FormData {
    constructor(element) {
      super();
      for (const entry of element.entries) this.append(...entry);
    }
  }
  const context = vm.createContext({ document: { getElementById: () => form }, setInterval() {},
    FormData: FormFixture, URLSearchParams, XMLHttpRequest: function () { return request; } });
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/forms/forms-timed-autosave.js'), 'utf8'), context);
  vm.runInContext("new FormsTimedAutosave('form', 10000, '/form/formname', () => true, '', '').sendRequest()", context);
  return request;
}

test('large text-only records autosave with ordinary form encoding and preserve duplicate fields and Unicode', () => {
  const entries = Array.from({ length: 1400 }, (_, index) => [`field${index}`, `value${index}`]);
  entries.push(['csrf', 'FAKE+&=token'], ['notes', 'Łódź ≥ 5 kg\n≤ 38°C & <follow-up>'], ['choice', 'a'], ['choice', 'b']);
  const request = send(entries);
  assert.equal(request.method, 'POST');
  assert.equal(request.url, '/form/formname');
  assert.equal(request.headers['Content-Type'], 'application/x-www-form-urlencoded;charset=UTF-8');
  assert.equal(typeof request.body, 'string');
  const decoded = new URLSearchParams(request.body);
  assert.deepEqual([...decoded], [...entries, ['submit', 'autosaveAjax']]);
});

test('forms with a file retain multipart upload behavior and leave the boundary to the browser', async () => {
  const file = new Blob(['FAKE attachment'], { type: 'text/plain' });
  const request = send([['csrf', 'FAKE-token'], ['attachment', file, 'fake.txt']]);
  assert.equal(request.headers['Content-Type'], undefined);
  assert.ok(request.body instanceof FormData);
  assert.equal(request.body.get('attachment').name, 'fake.txt');
  assert.equal(request.body.get('attachment').type, 'text/plain');
  assert.equal(await request.body.get('attachment').text(), 'FAKE attachment');
  assert.equal(request.body.get('submit'), 'autosaveAjax');
});

function autosaveSession() {
  const messages = [{ innerText: '' }];
  const requests = [];
  const listeners = {};
  const form = { addEventListener(name, callback) { listeners[name] = callback; } };
  class Request {
    constructor() { this.listeners = {}; requests.push(this); }
    addEventListener(name, callback) { this.listeners[name] = callback; }
    open() {}
    setRequestHeader() {}
    send() {}
    answer(status, body) { this.status = status; this.responseText = body; this.listeners.load({ target: this }); }
    fail(type) { this.listeners[type]({ target: this }); }
  }
  const history = [];
  const context = vm.createContext({
    document: { getElementById: () => form, getElementsByName: () => messages },
    window: { addEventListener() {}, history: { replaceState: (...args) => history.push(args) } },
    setInterval() {}, FormData: class extends FormData { constructor() { super(); this.append('note', 'FAKE clinical note'); } },
    URLSearchParams, XMLHttpRequest: Request,
  });
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/forms/forms-timed-autosave.js'), 'utf8'), context);
  const saver = vm.runInContext("new FormsTimedAutosave('form', 10000, '/form/formname', () => true, '', 'status', true)", context);
  const edit = () => listeners.change(new Event('change'));
  const input = () => listeners.input(new Event('input'));
  const warns = () => {
    const closing = new Event('beforeunload', { cancelable: true });
    saver.promptSaveBeforeClose(closing);
    return closing.defaultPrevented;
  };
  const success = JSON.stringify({ success: true, newFormId: 42, newNewUrl: '?formId=42', formAutosaveDate: '10:00 AM' });
  return { saver, requests, messages, history, edit, input, warns, success };
}

test('failed or non-JSON autosaves keep clinical edits unsaved and allow a retry', () => {
  for (const [status, body] of [[502, '<html>Bad gateway</html>'], [200, '<html>Login</html>'],
    [500, '{"success":true}'], [200, '{"success":false}'], [200, 'null'], [200, '{"success":true}']]) {
    const f = autosaveSession();
    f.edit();
    f.saver.saveForm();
    assert.doesNotThrow(() => f.requests[0].answer(status, body));
    assert.equal(f.warns(), true);
    assert.match(f.messages[0].innerText, /still unsaved/);
    assert.deepEqual(f.history, []);
    f.saver.saveForm();
    assert.equal(f.requests.length, 2);
    f.requests[1].answer(200, f.success);
    assert.equal(f.warns(), false);
    assert.equal(f.history.length, 1);
    assert.match(f.messages[0].innerText, /Autosaved at/);
  }
});

test('network errors, cancellation and timeouts preserve the unsaved warning', () => {
  for (const type of ['error', 'abort', 'timeout']) {
    const f = autosaveSession();
    f.edit();
    f.saver.saveForm();
    f.requests[0].fail(type);
    assert.equal(f.warns(), true, type);
    f.saver.saveForm();
    assert.equal(f.requests.length, 2, type);
  }
});

test('slow autosaves serialize snapshots and cannot acknowledge newer clinical edits', () => {
  const f = autosaveSession();
  f.edit();
  f.saver.timerIncrement();
  f.saver.timerIncrement();
  for (let tick = 0; tick < 5; tick++) f.saver.timerIncrement();
  assert.equal(f.requests.length, 1, 'one outstanding save despite multiple timer ticks');
  f.edit();
  f.requests[0].answer(200, f.success);
  assert.equal(f.warns(), true, 'the response saved the old snapshot only');
  assert.match(f.messages[0].innerText, /New changes are still unsaved/);
  f.saver.timerIncrement();
  assert.equal(f.requests.length, 2);
  f.requests[1].answer(200, f.success);
  assert.equal(f.warns(), false);
});

test('mouse paste stays unsaved when a response arrives before the field blurs', () => {
  const f = autosaveSession();
  // Paste/cut/drop emit input immediately; no keydown or change/blur is needed.
  f.input();
  assert.equal(f.warns(), true);
  f.saver.saveForm();
  f.input();
  f.requests[0].answer(200, f.success);
  assert.equal(f.warns(), true, 'the in-flight request did not contain the pasted edit');
  f.saver.saveForm();
  f.requests[1].answer(200, f.success);
  assert.equal(f.warns(), false);
});
