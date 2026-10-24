/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {EventEmitter} = require('node:events');
const {createRecorder, wireStrictPage, assertStrictPage} = require('./lib/playwright-harness');
const {createInjectedResponseEvidence} = require('./lib/injected-response-evidence');
const {observeStoredPreviewCapacity} = require('./lib/stored-preview-capacity-check');

const label = 'legacy-document-capacity';
const endpoint = 'https://local/carlos/documentManager/ManageDocument';
const url = endpoint + '?method=viewDocPage&doc_no=42&curPage=2';
const nativeBusy = {'content-type': 'text/html;charset=UTF-8', 'retry-after': '1', 'cache-control': 'no-store'};
function fixture() {
  const page = new EventEmitter(), recorder = createRecorder(), injectedRequests = new WeakSet();
  page.url = () => 'https://local/carlos/oscarMDS/ViewSplit?document=42';
  wireStrictPage(page, label, recorder, {baseline: []});
  const injected = createInjectedResponseEvidence(recorder, label);
  page.on('response', response => injected.observe(response));
  const native = observeStoredPreviewCapacity({page, endpoint, documentId: '42', label, recorder, injectedRequests});
  function request() {
    return {url: () => url, method: () => 'GET', resourceType: () => 'fetch', postData: () => null,
      failure: () => ({errorText: 'net::ERR_CONNECTION_RESET'})};
  }
  function emit(request, status, headers = status === 503 ? nativeBusy : {'content-type': 'image/png'}) {
    page.emit('response', {url: () => url, request: () => request, status: () => status,
      headers: () => headers, finished: async () => null});
    if (status >= 400) page.emit('console', {type: () => 'error', location: () => ({url}),
      text: () => `Failed to load resource: the server responded with a status of ${status} ()`});
  }
  function synthetic(status = 503) {
    const value = request();
    injected.expect(value, status); injectedRequests.add(value);
    emit(value, status, {'content-type': 'text/plain', 'retry-after': '1'});
    return value;
  }
  async function finish(decoded = [url]) {
    injected.removeExpectedDiagnostics();
    await native.finish(decoded);
    assertStrictPage(recorder);
  }
  return {page, recorder, native, injected, request, emit, synthetic, finish};
}

test('native and injected 503s at the same URL require their separate exact evidence', async () => {
  const f = fixture();
  f.emit(f.request(), 503);
  f.synthetic();
  f.emit(f.request(), 503);
  f.synthetic();
  f.emit(f.request(), 200);
  await assert.rejects(f.finish([]), /decoded image/);
  assert.equal(f.recorder.badResponses.length, 2, 'only exact synthetic responses were reconciled');
  await f.finish();
  assert.equal(f.recorder.badResponses.length, 0);
  assert.equal(f.recorder.consoleIssues.length, 0);
});

test('the synthetic exclusion cannot excuse an unregistered same-URL generic 503', async () => {
  const f = fixture();
  f.synthetic();
  f.emit(f.request(), 503, {'content-type': 'text/plain', 'retry-after': '1'});
  f.emit(f.request(), 200);
  await assert.rejects(f.finish(), /admission-refusal contract/);
  assert.equal(f.recorder.badResponses.length, 1);
});

test('a real same-URL 500 remains a failure beside fully evidenced synthetic and native refusals', async () => {
  const f = fixture();
  f.synthetic(); f.emit(f.request(), 503); f.emit(f.request(), 500, {'content-type': 'text/html'});
  f.emit(f.request(), 200);
  await assert.rejects(f.finish(), /PNG image/);
  assert.equal(f.recorder.badResponses.length, 2);
});

test('an old decoded thumbnail cannot excuse a later unresolved native refusal at cleanup', async () => {
  const f = fixture();
  f.synthetic(); f.emit(f.request(), 503); f.emit(f.request(), 200); await f.finish();
  f.emit(f.request(), 503);
  await assert.rejects(f.finish(), /did not recover/);
  assert.equal(f.recorder.badResponses.length, 1);
});

test('a native transport failure is not hidden by a synthetic request at the same URL', async () => {
  const f = fixture();
  f.synthetic(); f.emit(f.request(), 503); f.emit(f.request(), 200);
  f.page.emit('requestfailed', f.request());
  await assert.rejects(f.finish(), /transport failed/);
});

test('synthetic exclusions must be exact request identities rather than a URL allowlist', () => {
  const page = new EventEmitter();
  assert.throws(() => observeStoredPreviewCapacity({page, endpoint, documentId: '42', label,
    recorder: createRecorder(), injectedRequests: new Set([url])}), /exact request identities/);
});

test('resource-like console text carrying additional application diagnostics remains a strict failure', async () => {
  const f = fixture();
  f.synthetic(); f.emit(f.request(), 503); f.emit(f.request(), 200);
  f.recorder.consoleIssues.push({label, type: 'error', location: {url},
    text: 'Failed to load resource: the server responded with a status of 503 () ReferenceError: application failed'});
  await assert.rejects(f.finish(), /ReferenceError: application failed/);
  assert.equal(f.recorder.consoleIssues.length, 1);
});
