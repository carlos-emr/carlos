/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {EventEmitter} = require('node:events');
const {validateCapacityResponse, createCapacityLedger, withStoredCapacityCheck} = require('./lib/stored-document-capacity-check');
const endpoint = 'https://local/carlos/documentManager/SplitDocument';
const label = 'stored-document-mutations';
const headers = {'content-type': 'application/json;charset=UTF-8', 'retry-after': '1'};
const busy = {success: false, accepted: false, retryable: true};
const body = 'method=rotate90&document=42&sourceRevision=' + 'a'.repeat(64);
function request(overrides = {}) {
  return {url: () => endpoint, method: () => 'POST', postData: () => body, headers: () => ({'csrf-token': 'original'}), ...overrides};
}
function response(req, status = 503, data = busy, extra = {}) {
  return {request: () => req, status: () => status, headers: () => headers, json: async () => data, ...extra};
}
function recorder() {return {badResponses: [], consoleIssues: []};}
function recorded(extra = {}) {return {label, url: endpoint, method: 'POST', status: 503, contentType: headers['content-type'], ...extra};}
function consoleEntry(extra = {}) {return {label, type: 'error', location: {url: endpoint}, text: 'Failed to load resource: the server responded with a status of 503 ()', ...extra};}
function ledger(rec = recorder(), extra = {}) {
  return createCapacityLedger({endpoint, label, recorder: rec, validateRequest: value => {
    const fields = new URLSearchParams(value.postData());
    assert.equal(fields.get('document'), '42'); assert.equal(fields.get('method'), 'rotate90');
  }, ...extra});
}

test('typed capacity validation accepts only a bounded explicit pre-acceptance contract', () => {
  validateCapacityResponse(headers, busy);
  for (const data of [{...busy, accepted: true}, {...busy, success: true}, {...busy, retryable: false}, {success: false, retryable: true}, null]) {
    assert.throws(() => validateCapacityResponse(headers, data));
  }
  for (const bad of [undefined, '', '0', '-1', '61', '999', 'tomorrow']) {
    assert.throws(() => validateCapacityResponse({...headers, 'retry-after': bad}, busy));
  }
  assert.throws(() => validateCapacityResponse({...headers, 'content-type': 'text/html'}, busy));
});

for (const [name, changed] of [
  ['body', {postData: () => body + '&page=2'}],
  ['revision', {postData: () => body.replace('a'.repeat(64), 'b'.repeat(64))}],
  ['CSRF header', {headers: () => ({'csrf-token': 'changed'})}],
  ['document', {postData: () => body.replace('42', '43')}],
  ['method', {method: () => 'GET'}],
  ['endpoint', {url: () => endpoint + '?different=1'}],
]) test(`ledger rejects changed frozen ${name}`, () => {
  const v = ledger(); v.request(request()); assert.throws(() => v.request(request(changed)));
});

test('ledger removes only new individually validated capacity errors after exactly one real completion', async () => {
  const rec = recorder(); const old = recorded(); rec.badResponses.push(old);
  const oldConsole = consoleEntry(); rec.consoleIssues.push(oldConsole);
  const v = ledger(rec);
  const first = request(), second = request(); v.request(first, true); v.request(second);
  const expected = recorded(), expectedConsole = consoleEntry();
  const unrelated = [recorded({method: 'GET'}), recorded({status: 500}), recorded({label: 'other'}), recorded({url: endpoint + '?other=1'})];
  const otherConsole = [consoleEntry({type: 'warning'}), consoleEntry({text: 'Application error 503'}), consoleEntry({label: 'other'})];
  rec.badResponses.push(expected, ...unrelated); rec.consoleIssues.push(expectedConsole, ...otherConsole);
  await v.response(response(first)); await v.response(response(second, 200, {success: true, accepted: true}));
  v.finish(1);
  assert.deepEqual(rec.badResponses, [old, ...unrelated]); assert.deepEqual(rec.consoleIssues, [oldConsole, ...otherConsole]);
});

test('a malformed same-endpoint response cannot be hidden beside a validated busy response', async () => {
  const rec = recorder(), v = ledger(rec), first = request(), bad = request(), final = request();
  for (const req of [first, bad, final]) v.request(req);
  rec.badResponses.push(recorded(), recorded());
  await v.response(response(first));
  await assert.rejects(v.response(response(bad, 503, {...busy, accepted: true})));
  await v.response(response(final, 200, {success: true, accepted: true}));
  assert.throws(() => v.finish(0)); assert.equal(rec.badResponses.length, 2);
});

test('duplicate accepted responses and excess recorded capacity errors remain failures', async () => {
  const rec = recorder(), v = ledger(rec);
  for (let i = 0; i < 2; i++) { const req = request(); v.request(req); await v.response(response(req, 200, {success: true, accepted: true})); }
  assert.throws(() => v.finish(0));
  const rec2 = recorder(), v2 = ledger(rec2), first = request(), final = request();
  v2.request(first); v2.request(final); rec2.badResponses.push(recorded(), recorded());
  await v2.response(response(first)); await v2.response(response(final, 200, {success: true, accepted: true}));
  assert.throws(() => v2.finish(0)); assert.equal(rec2.badResponses.length, 2);
});

function fixture({naturalBusy = 0} = {}) {
  const page = new EventEmitter(), rec = recorder();
  let handler, unroutes = 0, forwards = 0, injected = 0, failures = 0, unchanged = 0, cancelled = 0;
  page.route = async (url, callback) => {assert.equal(url, endpoint); handler = callback;};
  page.unroute = async () => {unroutes++; handler = null;};
  page.evaluate = async () => {cancelled++;};
  const options = {page, endpoint, label, recorder: rec, inject: 5, validateRequest: () => {},
    assertUnchanged() {unchanged++;}, onFailure() {failures++;}};
  async function send({deferredJson} = {}) {
    const req = request(); page.emit('request', req);
    const emit = async (status, data, synthetic) => {
      if (synthetic) injected++;
      if (status === 503) {rec.badResponses.push(recorded()); rec.consoleIssues.push(consoleEntry());}
      page.emit('response', response(req, status, data, deferredJson ? {json: () => deferredJson} : {}));
      page.emit('requestfinished', req);
    };
    await handler({request: () => req,
      async fulfill(value) {assert.equal(value.status, 503); await emit(503, JSON.parse(value.body), true);},
      async fallback() {
        forwards++;
        if (naturalBusy-- > 0) await emit(503, busy, false);
        else await emit(200, {success: true, accepted: true}, false);
      },
      async abort() {page.emit('requestfailed', req);}});
  }
  return {options, send, counts: () => ({unroutes, forwards, injected, failures, unchanged, cancelled}), rec};
}

test('five injected refusals fall through once to the existing real-server ownership guard', async () => {
  const f = fixture();
  await withStoredCapacityCheck(f.options, async () => {for (let i = 0; i < 6; i++) await f.send();});
  assert.deepEqual(f.counts(), {unroutes: 1, forwards: 1, injected: 5, failures: 0, unchanged: 5, cancelled: 0});
  assert.deepEqual(f.rec, recorder());
});

test('response validation must finish before routes are removed or successful cleanup is allowed', async () => {
  const f = fixture(); f.options.inject = 0;
  let complete; const deferredJson = new Promise(resolve => {complete = resolve;});
  const running = withStoredCapacityCheck(f.options, async () => {await f.send({deferredJson});});
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(f.counts().unroutes, 0);
  complete({success: true, accepted: true}); await running;
  assert.equal(f.counts().unroutes, 1);
});

test('failed operations mark the fixture uncertain, stop page retries and drain before teardown', async () => {
  const f = fixture(); f.options.inject = 0;
  let complete; const deferredJson = new Promise(resolve => {complete = resolve;});
  const running = withStoredCapacityCheck(f.options, async () => {await f.send({deferredJson}); throw new Error('failed assertion');});
  running.catch(() => {});
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(f.counts().failures, 1); assert.equal(f.counts().cancelled, 1); assert.equal(f.counts().unroutes, 0);
  complete({success: true, accepted: true}); await assert.rejects(running, /failed assertion/);
  assert.equal(f.counts().unroutes, 1);
});


test('additional real typed capacity refusals recover without hiding a duplicate accepted mutation', async () => {
  const f = fixture({naturalBusy: 1});
  await withStoredCapacityCheck(f.options, async () => {for (let i = 0; i < 7; i++) await f.send();});
  assert.equal(f.counts().injected, 5); assert.equal(f.counts().forwards, 2);
  assert.equal(f.counts().failures, 0); assert.deepEqual(f.rec, recorder());
});

test('a stale-source final conflict leaves its separate recorder entry for explicit caller verification', async () => {
  const rec = recorder(), v = ledger(rec, {finalStatus: 409}), first = request(), final = request();
  v.request(first); v.request(final); const conflict = recorded({status: 409});
  rec.badResponses.push(recorded(), conflict);
  await v.response(response(first));
  await v.response(response(final, 409, {...busy, retryable: false, sourceChanged: true}));
  v.finish(0); assert.deepEqual(rec.badResponses, [conflict]);
});

test('invalid JSON never qualifies as a removable busy response', async () => {
  const rec = recorder(), v = ledger(rec), req = request(); v.request(req); rec.badResponses.push(recorded());
  await assert.rejects(v.response(response(req, 503, null, {json: async () => {throw new Error('HTML body');}})), /HTML body/);
  assert.throws(() => v.finish(0)); assert.equal(rec.badResponses.length, 1);
});
