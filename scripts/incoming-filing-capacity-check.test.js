/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {validateFilingRequest, createFilingForwarder, createFilingBusyRecorder, cleanupFiling} = require('./lib/incoming-filing-capacity-check');
const owned = {name: 'FAKE-PW123.pdf', patient: '123', description: 'FAKE-PW123 incoming filing', revision: 'a'.repeat(64)};
function request(extra = {}, header = 'bounded-v1', method = 'POST') {
  const body = new URLSearchParams({method: 'addIncomingDocument', pdfName: owned.name, demog: owned.patient,
    documentDescription: owned.description, queueId: '1', pdfDir: 'File', 'CSRF-TOKEN': 'secret', sourceRevision: owned.revision, ...extra});
  return {postData: () => body.toString(), method: () => method, headers: () => ({'x-carlos-incoming-filing': header})};
}
test('owned request guard preserves the exact encoded form body', () => {
  const req = request(); assert.equal(validateFilingRequest(req, owned), req.postData());
});
for (const [key, value] of Object.entries({method: 'addDocument', pdfName: 'other.pdf', demog: '124',
  documentDescription: 'other', queueId: '2', pdfDir: 'Fax', 'CSRF-TOKEN': '', sourceRevision: 'b'.repeat(64)})) {
  test(`filing guard refuses changed ${key} before transmission`, () => {
    assert.throws(() => validateFilingRequest(request({[key]: value}), owned));
  });
}
test('filing guard refuses duplicate targets and missing contract', () => {
  const req = request(); const duplicate = {...req, postData: () => req.postData() + '&demog=123'};
  assert.throws(() => validateFilingRequest(duplicate, owned));
  assert.throws(() => validateFilingRequest(request({}, '', 'POST'), owned));
  assert.throws(() => validateFilingRequest(request({}, 'bounded-v1', 'GET'), owned));
  assert.throws(() => validateFilingRequest({...req, postData: () => req.postData() + '&sourceRevision=' + owned.revision}, owned));
  assert.throws(() => validateFilingRequest(request({sourceRevision: ''}), owned));
});
test('cleanup refuses changed patient ownership before reading or deleting child rows', () => {
  let touched = false;
  const session = {patient: '123', marker: 'FAKE-PW123', provider: '999998', sql: {
    value: () => '0', rows: () => {touched = true; return [];}, execute: () => {touched = true;},
  }};
  assert.throws(() => cleanupFiling(session, owned.description, '/tmp'));
  assert.equal(touched, false);
});
test('cleanup refuses a document linked to another patient before deleting any data', () => {
  let calls = 0, deleted = false;
  const session = {patient: '123', marker: 'FAKE-PW123', provider: '999998', sql: {
    value: () => ++calls === 1 ? '1' : '1', rows: () => [['42', owned.name]], execute: () => {deleted = true;},
  }};
  assert.throws(() => cleanupFiling(session, owned.description, '/tmp'));
  assert.equal(deleted, false);
});

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => {resolve = yes; reject = no;});
  return {promise, resolve, reject};
}
function captureFixture(overrides = {}) {
  const events = [];
  let state = 'idle', uncertain = false;
  const guard = {
    forward() {assert.equal(state, 'idle'); state = 'pending'; events.push('forward');},
    completed() {state = 'complete'; events.push('complete');},
    pending: () => state === 'pending',
    unknown() {uncertain = true; events.push('unknown');},
    assertCleanup() {assert.ok(state !== 'pending' && !uncertain);},
  };
  const bytes = Buffer.from(JSON.stringify({success: true, accepted: true, documentNo: 42, nextUrl: '/next'}));
  const response = {
    status: () => 200, headers: () => ({'content-type': 'application/json;charset=UTF-8'}),
    async body() {events.push('body'); return bytes;},
    async dispose() {events.push('dispose');}, ...overrides.response,
  };
  const route = {
    request: () => request(),
    async fetch(options) {events.push('fetch'); assert.deepEqual(options, {maxRedirects: 0, maxRetries: 0, timeout: 90000}); return response;},
    async fulfill(actual) {events.push('fulfill'); assert.equal(actual.response, response); assert.equal(actual.body, bytes);},
    async abort() {events.push('abort');}, ...overrides.route,
  };
  const forwarder = createFilingForwarder(owned, () => request().postData(), guard);
  return {events, guard, bytes, response, route, forwarder};
}
test('real filing body is captured before UI navigation and exact response is fulfilled once', async () => {
  const f = captureFixture();
  f.route.fulfill = async actual => {
    assert.deepEqual(f.events, ['forward', 'fetch', 'body']);
    assert.equal(actual.response, f.response); assert.equal(actual.body, f.bytes);
    // Navigation invalidates page/CDP response access immediately. No browser
    // response body is consulted: only the already captured APIResponse exists.
    f.response.body = () => {throw new Error('Page navigated');};
    f.events.push('navigate');
  };
  await f.forwarder.forward(f.route);
  assert.equal((await f.forwarder.result).documentNo, 42);
  await f.forwarder.drain(); f.guard.assertCleanup();
  assert.deepEqual(f.events, ['forward', 'fetch', 'body', 'navigate', 'dispose', 'complete']);
});
test('drain and fixture cleanup wait for full response, fulfillment and disposal', async () => {
  const body = deferred(), fulfill = deferred(), dispose = deferred();
  const f = captureFixture();
  f.response.body = () => body.promise;
  f.route.fulfill = () => fulfill.promise;
  f.response.dispose = () => dispose.promise;
  const work = f.forwarder.forward(f.route);
  let drained = false; const draining = f.forwarder.drain().then(() => {drained = true;});
  await Promise.resolve(); assert.throws(() => f.guard.assertCleanup()); assert.equal(drained, false);
  body.resolve(f.bytes); await Promise.resolve(); await Promise.resolve();
  assert.equal(drained, false); assert.throws(() => f.guard.assertCleanup());
  fulfill.resolve(); await Promise.resolve(); await Promise.resolve();
  assert.equal(drained, false); assert.throws(() => f.guard.assertCleanup());
  dispose.resolve(); await work; await draining;
  assert.equal(drained, true); f.guard.assertCleanup();
});
test('concurrent duplicate real filing is refused while first fetch is pending', async () => {
  const fetched = deferred(); const f = captureFixture();
  let forwards = 0; f.route.fetch = () => {forwards++; return fetched.promise;};
  const work = f.forwarder.forward(f.route);
  await assert.rejects(f.forwarder.forward(f.route), /concurrent or second accepted real filing/);
  assert.equal(forwards, 1);
  fetched.resolve(f.response); await work;
  await assert.rejects(f.forwarder.forward(f.route), /concurrent or second accepted real filing/);
  assert.equal(forwards, 1);
});
test('changed frozen request is refused before forwarding or claiming acceptance', async () => {
  const f = captureFixture(); f.route.request = () => request({docType: 'changed'});
  await assert.rejects(f.forwarder.forward(f.route), /frozen form/);
  await assert.rejects(f.forwarder.result, /frozen form/);
  assert.deepEqual(f.events, ['abort']); f.guard.assertCleanup();
});
for (const outcome of ['network', 'HTML', 'invalid-json', 'rejected', 'redirect', 'fulfill-failure']) {
  test(`real filing ${outcome} never fabricates acceptance or permits uncertain cleanup`, async () => {
    const f = captureFixture();
    if (outcome === 'network') f.route.fetch = async () => {throw new Error('Network lost');};
    if (outcome === 'HTML') f.response.headers = () => ({'content-type': 'text/html'});
    if (outcome === 'invalid-json') f.response.body = async () => Buffer.from('invalid');
    if (outcome === 'rejected') f.response.body = async () => Buffer.from('{"success":false,"accepted":false}');
    if (outcome === 'redirect') f.response.status = () => 302;
    if (outcome === 'fulfill-failure') f.route.fulfill = async () => {throw new Error('Navigation destroyed route');};
    await assert.rejects(f.forwarder.forward(f.route));
    await assert.rejects(f.forwarder.result);
    await assert.rejects(f.forwarder.drain());
    assert.throws(() => f.guard.assertCleanup());
    assert.ok(f.events.includes('unknown'));
    assert.ok(!f.events.includes('complete'));
    if (outcome !== 'fulfill-failure') assert.ok(!f.events.includes('fulfill'));
  });
}

test('real typed busy responses stay pending until exactly one real acceptance is forwarded', async () => {
  const f = captureFixture();
  let attempts = 0, validations = 0, resolved = false;
  const busyBytes = Buffer.from('{"success":false,"accepted":false,"retryable":true}');
  const busyResponse = {status: () => 503, headers: () => ({'content-type': 'application/json', 'retry-after': '2'}),
    body: async () => busyBytes, dispose: async () => {f.events.push('dispose-busy');}};
  f.forwarder = createFilingForwarder(owned, () => request().postData(), f.guard, () => {validations++;});
  f.forwarder.result.then(() => {resolved = true;});
  f.route.fetch = async () => {attempts++; return attempts <= 2 ? busyResponse : f.response;};
  f.route.fulfill = async actual => {
    assert.equal(actual.response, attempts <= 2 ? busyResponse : f.response);
    assert.equal(actual.body, attempts <= 2 ? busyBytes : f.bytes);
  };
  for (let index = 0; index < 2; index++) {
    const data = await f.forwarder.forward(f.route); await f.forwarder.drain();
    assert.equal(data.accepted, false); assert.equal(resolved, false);
    assert.throws(() => f.guard.assertCleanup());
  }
  await f.forwarder.forward(f.route);
  assert.equal((await f.forwarder.result).documentNo, 42);
  assert.equal(attempts, 3); assert.equal(validations, 2); assert.equal(f.forwarder.busyCount(), 2);
  assert.equal(f.events.filter(event => event === 'forward').length, 1);
  assert.equal(f.events.filter(event => event === 'complete').length, 1);
  f.guard.assertCleanup();
  await assert.rejects(f.forwarder.forward(f.route), /second accepted/);
  assert.equal(attempts, 3);
});
for (const invalid of [
  {data: {success: false, accepted: true, retryable: true}},
  {data: {success: true, accepted: false, retryable: true}},
  {data: {success: false, accepted: false, retryable: false}},
  {data: {success: false, retryable: true}},
  {retryAfter: ''}, {retryAfter: '0'}, {retryAfter: '61'}, {retryAfter: '1.5'}, {retryAfter: 'tomorrow'},
]) {
  test(`invalid real busy response blocks all replay: ${JSON.stringify(invalid)}`, async () => {
    const f = captureFixture(); let forwards = 0;
    f.response.status = () => 503;
    f.response.headers = () => ({'content-type': 'application/json', 'retry-after': invalid.retryAfter ?? '1'});
    f.response.body = async () => Buffer.from(JSON.stringify(invalid.data || {success: false, accepted: false, retryable: true}));
    f.route.fetch = async () => {forwards++; return f.response;};
    await assert.rejects(f.forwarder.forward(f.route), /invalid pre-acceptance/);
    await assert.rejects(f.forwarder.result);
    await assert.rejects(f.forwarder.forward(f.route), /second accepted/);
    assert.equal(forwards, 1); assert.equal(f.forwarder.busyCount(), 0);
    assert.throws(() => f.guard.assertCleanup());
  });
}
test('a changed request after a proven busy response never reaches the server', async () => {
  const f = captureFixture();
  f.response.status = () => 503;
  f.response.headers = () => ({'content-type': 'application/json', 'retry-after': '1'});
  f.response.body = async () => Buffer.from('{"success":false,"accepted":false,"retryable":true}');
  f.route.fulfill = async () => {};
  await f.forwarder.forward(f.route);
  f.route.request = () => request({'CSRF-TOKEN': 'replacement'});
  await assert.rejects(f.forwarder.forward(f.route), /frozen form/);
  assert.equal(f.events.filter(event => event === 'fetch').length, 1);
  assert.throws(() => f.guard.assertCleanup());
});
test('strict recorder removes only individually observed validated busy entries', () => {
  const endpoint = 'https://fixture.invalid/documentManager/ManageDocument';
  const entry = () => ({label: 'owned-page', status: 503, method: 'POST', url: endpoint, contentType: 'application/json'});
  const old = entry(), unrelated = entry();
  const oldConsole = {label: 'owned-page', type: 'error', location: {url: endpoint}, text: 'Failed to load resource: 503'};
  const recorder = {badResponses: [old], consoleIssues: [oldConsole]};
  const ledger = createFilingBusyRecorder(recorder, endpoint);
  recorder.badResponses.push(unrelated);
  const req = request(), proven = entry(); ledger.expect(req); recorder.badResponses.push(proven);
  ledger.observe({request: () => req, status: () => 503, url: () => endpoint, headers: () => ({'content-type': 'application/json'})});
  const provenConsole = {...oldConsole}; recorder.consoleIssues.push(provenConsole);
  ledger.finish(1);
  assert.deepEqual(recorder.badResponses, [old, unrelated]);
  assert.deepEqual(recorder.consoleIssues, [oldConsole]);
});
test('missing, duplicate and additional console busy evidence fails visibly', () => {
  const endpoint = 'https://fixture.invalid/documentManager/ManageDocument';
  const recorder = {badResponses: [], consoleIssues: []};
  const ledger = createFilingBusyRecorder(recorder, endpoint), req = request();
  ledger.expect(req); assert.throws(() => ledger.expect(req)); assert.throws(() => ledger.finish(1));
  recorder.badResponses.push({label: 'owned-page', status: 503, method: 'POST', url: endpoint, contentType: 'application/json'});
  const response = {request: () => req, status: () => 503, url: () => endpoint, headers: () => ({'content-type': 'application/json'})};
  ledger.observe(response); assert.throws(() => ledger.observe(response));
  recorder.consoleIssues.push(...[1, 2].map(() => ({label: 'owned-page', type: 'error', location: {url: endpoint}, text: 'Failed to load resource: 503'})));
  assert.throws(() => ledger.finish(1), /additional filing capacity console/);
  assert.equal(recorder.badResponses.length, 1);
});
