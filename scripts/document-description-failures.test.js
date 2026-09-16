/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/admin/displayDocumentDescriptionTemplate.jsp'), 'utf8');
const source = jsp.slice(jsp.indexOf('function reportTemplateError('), jsp.indexOf('function adddocDescription('));
function fixture({token = 'test-token', response = {ok: true, redirected: false, text: async () => '{"ok":true}'}} = {}) {
  const status = {textContent: 'old error'};
  const calls = [];
  const context = {window: {}, document: {querySelector: () => ({value: token}), getElementById: () => status},
    fetch: async (...args) => { calls.push(args); return response; }};
  vm.runInNewContext(source, context);
  return {context, calls, status};
}
test('document description request waits for CSRF initialization', async () => {
  const f = fixture();
  let ready;
  f.context.window.csrfTokenReady = new Promise(resolve => { ready = resolve; });
  const result = f.context.templateRequest('/carlos/DocumentDescriptionTemplate', 'method=list');
  assert.equal(f.calls.length, 0);
  ready();
  assert.equal(await result, '{"ok":true}');
  assert.equal(f.calls[0][1].headers['CSRF-TOKEN'], 'test-token');
  assert.equal(f.status.textContent, '');
});
test('missing CSRF token prevents an invalid request', async () => {
  const f = fixture({token: ''});
  await assert.rejects(f.context.templateRequest('/route', 'body'), /CSRF token/);
  assert.equal(f.calls.length, 0);
});
for (const response of [{ok: false, redirected: false}, {ok: true, redirected: true}]) {
  test(`HTTP failure or login redirect is visible (${JSON.stringify(response)})`, async () => {
    const f = fixture({response});
    await f.context.templateRequest('/route', 'body').catch(f.context.reportTemplateError);
    assert.match(f.status.textContent, /failed.*may not have been saved/);
  });
}
