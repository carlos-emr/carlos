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
