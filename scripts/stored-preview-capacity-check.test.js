/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {EventEmitter} = require('node:events');
const {observeStoredPreviewCapacity} = require('./lib/stored-preview-capacity-check');
const {createRecorder, wireStrictPage, buildFailureDetails} = require('./lib/playwright-harness');
const endpoint = 'https://local/carlos/documentManager/ManageDocument';
const url = endpoint + '?method=viewDocPage&doc_no=42&curPage=2';
const label = 'stored-document-stale-session';
const busyHeaders = {'content-type': 'text/html;charset=UTF-8', 'retry-after': '1', 'cache-control': 'no-store'};
function fixture() {
  const page = new EventEmitter(), recorder = {badResponses: [], consoleIssues: []};
  page.url = () => 'https://local/carlos/oscarMDS/ViewSplit?document=42';
  const ledger = observeStoredPreviewCapacity({page, endpoint, documentId: '42', label, recorder});
  function send(status, options = {}) {
    const requestUrl = options.url || url;
    const request = {url: () => requestUrl, method: () => options.method || 'GET',
      resourceType: () => options.resourceType || 'fetch', postData: () => options.body || null};
    const headers = options.headers || (status === 503 ? busyHeaders : {'content-type': 'image/png'});
    if (status >= 400) {
      recorder.badResponses.push({label, url: requestUrl, status, method: request.method(),
        resourceType: request.resourceType(), contentType: headers['content-type']});
      recorder.consoleIssues.push({label, type: 'error', location: {url: requestUrl},
        text: `Failed to load resource: the server responded with a status of ${status} ()`});
    }
    page.emit('response', {request: () => request, status: () => status, headers: () => headers,
      finished: options.finished || (async () => null)});
    return request;
  }
  return {page, recorder, ledger, send};
}

test('five genuine preview refusals require same-URL success and decoded pixels before recorder reconciliation', async () => {
  const f = fixture();
  for (let i = 0; i < 5; i++) f.send(503);
  assert.equal(f.recorder.badResponses.length, 5);
  f.send(200); await f.ledger.finish([url]);
  assert.deepEqual(f.recorder, {badResponses: [], consoleIssues: []});
});

for (const [name, headers] of [
  ['generic proxy error', {'content-type': 'text/html'}],
  ['missing no-store', {'content-type': 'text/html', 'retry-after': '1'}],
  ['wrong retry delay', {...busyHeaders, 'retry-after': '60'}],
  ['JSON error', {...busyHeaders, 'content-type': 'application/json'}],
]) test(`${name} remains a failure even after an image succeeds`, async () => {
  const f = fixture(); f.send(503, {headers}); f.send(200);
  await assert.rejects(f.ledger.finish([url]), /admission-refusal contract/);
  assert.equal(f.recorder.badResponses.length, 1);
});

test('capacity refusal cannot be ignored without a successful retry and decoded same-page thumbnail', async () => {
  const noRetry = fixture(); noRetry.send(503);
  await assert.rejects(noRetry.ledger.finish([url]), /did not recover/);
  const noDecode = fixture(); noDecode.send(503); noDecode.send(200);
  await assert.rejects(noDecode.ledger.finish([]), /did not recover/);
  await assert.rejects(noDecode.ledger.finish([url.replace('curPage=2', 'curPage=1')]), /did not recover/);
  assert.equal(noDecode.recorder.badResponses.length, 1);
});

test('old successful image cannot justify a later unresolved refusal', async () => {
  const f = fixture(); f.send(200); f.send(503);
  await assert.rejects(f.ledger.finish([url]), /did not recover/);
});

test('wrong patient, endpoint, session label and unrelated failures remain recorded', async () => {
  const f = fixture();
  f.send(503, {url: url.replace('doc_no=42', 'doc_no=99')});
  f.send(503, {url: url.replace('/ManageDocument', '/other')});
  f.recorder.badResponses.push({label: 'other', url, status: 503, method: 'GET', resourceType: 'fetch', contentType: 'text/html'});
  f.recorder.consoleIssues.push({label, type: 'error', location: {url}, text: 'Application error 503'});
  f.send(503); f.send(200); await f.ledger.finish([url]);
  assert.equal(f.recorder.badResponses.length, 3);
  assert.equal(f.recorder.consoleIssues.length, 3);
});

for (const options of [{method: 'POST'}, {body: 'method=rotate90'}, {resourceType: 'document'},
  {url: url + '&curPage=3'}, {url: url + '&doc_no=99'}, {url: url.replace('curPage=2', 'curPage=0')}]) {
  test(`noncanonical preview request cannot qualify: ${JSON.stringify(options)}`, async () => {
    const f = fixture(); f.send(503, options); f.send(200);
    await assert.rejects(f.ledger.finish([url])); assert.equal(f.recorder.badResponses.length, 1);
  });
}

test('same-URL real 500 or HTML success remains a failure beside legitimate capacity recovery', async () => {
  for (const [status, headers] of [[500, {'content-type': 'text/html'}], [200, {'content-type': 'text/html'}]]) {
    const f = fixture(); f.send(503); f.send(status, {headers}); f.send(200);
    await assert.rejects(f.ledger.finish([url]), /PNG image/);
    assert.ok(f.recorder.badResponses.length > 0);
  }
});

test('duplicate recorder failures are not broadly discarded', async () => {
  const f = fixture(); f.send(503); f.send(200);
  f.recorder.badResponses.push({...f.recorder.badResponses[0]});
  await assert.rejects(f.ledger.finish([url]), /differ from validated/);
  assert.equal(f.recorder.badResponses.length, 2);
});

test('all image bodies must drain and transport failures must remain failures', async () => {
  const f = fixture(); f.send(503);
  let complete; const body = new Promise(resolve => {complete = resolve;});
  f.send(200, {finished: () => body});
  let done = false; const finishing = f.ledger.finish([url]).then(() => {done = true;});
  await new Promise(resolve => setImmediate(resolve)); assert.equal(done, false);
  complete(null); await finishing; assert.equal(done, true);
  const bad = fixture(); bad.send(503); bad.send(200, {finished: async () => new Error('connection lost')});
  await assert.rejects(bad.ledger.finish([url]), /body failed/);
  const failed = fixture(); failed.send(503); const request = failed.send(200);
  failed.page.emit('requestfailed', request);
  await assert.rejects(failed.ledger.finish([url]), /transport failed/);
});

test('the showPage endpoint uses its actual page parameter and observations stop on close', async () => {
  const f = fixture(), other = url.replace('viewDocPage', 'showPage').replace('curPage=2', 'page=2');
  f.send(503, {url: other}); f.send(200, {url: other}); await f.ledger.finish([other]);
  assert.equal(f.recorder.badResponses.length, 0);
  f.ledger.close(); assert.equal(f.page.listenerCount('response'), 0); assert.equal(f.page.listenerCount('requestfailed'), 0);
});

test('later checkpoints and other thumbnail failures cannot erase earlier recorder evidence', async () => {
  const f = fixture();
  f.send(503); f.send(200); await f.ledger.finish([url]);
  f.send(503); f.send(200);
  const other = url.replace('curPage=2', 'curPage=3'); f.send(503, {url: other});
  await assert.rejects(f.ledger.finish([url]), /did not recover/);
  assert.equal(f.recorder.badResponses.length, 2, 'failed reconciliation must be atomic');
});

for (const observerFirst of [false, true]) test(`actual recorder preserves exact request identity before diagnostic sanitization (observer first: ${observerFirst})`, async () => {
  const page = new EventEmitter(), recorder = createRecorder();
  page.url = () => 'https://local/carlos/oscarMDS/ViewSplit?document=42';
  let ledger;
  if (observerFirst) ledger = observeStoredPreviewCapacity({page, endpoint, documentId: '42', label, recorder});
  wireStrictPage(page, label, recorder, {baseline: []});
  if (!observerFirst) ledger = observeStoredPreviewCapacity({page, endpoint, documentId: '42', label, recorder});
  const request = {url: () => url, method: () => 'GET', resourceType: () => 'fetch', postData: () => null};
  function emit(status) {
    page.emit('response', {url: () => url, request: () => request, status: () => status,
      headers: () => status === 503 ? busyHeaders : {'content-type': 'image/png'}, finished: async () => null});
    if (status === 503) page.emit('console', {type: () => 'error', location: () => ({url}),
      text: () => 'Failed to load resource: the server responded with a status of 503 ()'});
  }
  emit(503);
  const diagnostic = buildFailureDetails(recorder);
  assert.equal(diagnostic.badResponses[0].url, endpoint, 'published diagnostics intentionally remove the query');
  assert.equal(recorder.badResponses[0].url, url, 'live recorder must preserve the exact owned page request');
  emit(200);
  await ledger.finish(['/carlos/documentManager/ManageDocument?method=viewDocPage&doc_no=42&curPage=2']);
  assert.equal(recorder.badResponses.length, 0); assert.equal(recorder.consoleIssues.length, 0);
  assert.equal(diagnostic.badResponses.length, 1, 'diagnostic snapshots remain immutable evidence');
});

test('relative DOM image URLs resolve against the actual page, while another origin cannot justify recovery', async () => {
  const f = fixture(); f.send(503); f.send(200);
  await assert.rejects(f.ledger.finish(['https://other/carlos/documentManager/ManageDocument?method=viewDocPage&doc_no=42&curPage=2']), /did not recover/);
  await f.ledger.finish(['../documentManager/ManageDocument?method=viewDocPage&doc_no=42&curPage=2']);
  assert.equal(f.recorder.badResponses.length, 0);
});
