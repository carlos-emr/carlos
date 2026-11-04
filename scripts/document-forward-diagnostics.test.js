/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const h = require('./lib/playwright-harness');
const { acknowledgeOwnedForwardFailure } = require('./lib/document-forward-diagnostics');

function fixture() {
  const page = {};
  const expectedUrl = 'https://127.0.0.1/carlos/oscarMDS/ViewSelectProvider';
  const details = { method: 'POST', url: expectedUrl, body: 'forwardList=17%3ADOC&isListView=false', status: 503,
    responseBody: 'test outage' };
  const request = { method: () => details.method, url: () => details.url,
    postData: () => details.body, frame: () => ({ page: () => page }) };
  const response = { request: () => request, status: () => details.status,
    text: async () => details.responseBody, finished: async () => null };
  const recorder = h.createRecorder();
  const before = { http: new Set(), console: new Set() };
  const http = { url: expectedUrl, method: 'POST', status: 503, label: 'document-forward' };
  const resource = { location: { url: expectedUrl }, text: 'Failed to load resource: the server responded with a status of 503 ()' };
  const reported = { location: { url: 'https://127.0.0.1/carlos/share/javascript/oscarMDSIndex.js?v=1' },
    text: 'Forward dialog request failed: error ' };
  recorder.badResponses.push(http); recorder.consoleIssues.push(resource, reported);
  const options = { page, expectedUrl, documentNo: '17', request, response, intercepted: 1, before };
  return { recorder, options, details, http, resource, reported };
}

test('acknowledges exactly the observed owned injected503 and retains its raw diagnostic audit', async () => {
  const f = fixture(); const audit = await acknowledgeOwnedForwardFailure(f.recorder, f.options);
  assert.deepEqual(f.recorder.badResponses, []); assert.deepEqual(f.recorder.consoleIssues, []);
  assert.deepEqual(audit.http, [f.http]); assert.deepEqual(audit.console, [f.resource, f.reported]);
  h.assertStrictPage(f.recorder);
});

test('never removes old same-URL findings or unrelated new browser failures', async () => {
  const f = fixture(); const old = { ...f.http }; const unrelated = { url: 'https://127.0.0.1/carlos/other', method: 'GET', status: 500 };
  const unknownConsole = { text: 'Unrelated failure', location: {} };
  f.recorder.badResponses.unshift(old); f.options.before.http.add(old);
  f.recorder.badResponses.push(unrelated); f.recorder.consoleIssues.push(unknownConsole);
  await acknowledgeOwnedForwardFailure(f.recorder, f.options);
  assert.deepEqual(f.recorder.badResponses, [old, unrelated]);
  assert.deepEqual(f.recorder.consoleIssues, [unknownConsole]);
  assert.throws(() => h.assertStrictPage(f.recorder), /HTTP 503|HTTP 500/);
});

for (const [label, change] of [
  ['foreign page', f => { f.options.page = {}; }],
  ['different request object', f => { f.options.response = { ...f.options.response, request: () => ({}) }; }],
  ['wrong method', f => { f.details.method = 'GET'; }],
  ['foreign endpoint', f => { f.details.url = 'https://127.0.0.1/carlos/other'; }],
  ['foreign document', f => { f.details.body = 'forwardList=18%3ADOC'; }],
  ['different document category', f => { f.details.body = 'forwardList=17%3AHL7'; }],
  ['repeated document selection', f => { f.details.body = 'forwardList=17%3ADOC&forwardList=18%3ADOC'; }],
  ['duplicate interception', f => { f.options.intercepted = 2; }],
  ['wrong response status', f => { f.details.status = 500; }],
  ['unexpected response body', f => { f.details.responseBody = 'real outage'; }],
  ['duplicate HTTP finding', f => { f.recorder.badResponses.push({ ...f.http }); }],
  ['missing native error diagnostic', f => { f.recorder.consoleIssues.pop(); }],
  ['duplicate native error diagnostic', f => { f.recorder.consoleIssues.push({ ...f.reported }); }],
]) {
  test(`refuses acknowledgement after ${label} without removing any finding`, async () => {
    const f = fixture(); change(f);
    const beforeHttp = [...f.recorder.badResponses]; const beforeConsole = [...f.recorder.consoleIssues];
    await assert.rejects(acknowledgeOwnedForwardFailure(f.recorder, f.options));
    assert.deepEqual(f.recorder.badResponses, beforeHttp); assert.deepEqual(f.recorder.consoleIssues, beforeConsole);
  });
}
