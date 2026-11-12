/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { EventEmitter } = require('node:events');
const { provenReloadAborts, observeOwnedReload, provenPrintNavigationAborts } = require('./lib/chart-reload-witness');
const h = require('./lib/playwright-harness');

function fixture() {
  const page = {}; const endpoint = 'https://127.0.0.1/carlos/CaseManagementEntry?chartContext=owned';
  const form = { endpoint, timeOrigin: 1, url: 'https://127.0.0.1/carlos/chart', patient: '11', program: '7', token: 'owned' };
  const reopened = { ...form, timeOrigin: 2 };
  const entry = { url: endpoint, resourceType: 'ping', errorText: 'net::ERR_ABORTED' };
  const recorder = h.createRecorder(); recorder.requestFailures.push(entry);
  const request = { frame: () => ({ page: () => page }), method: () => 'POST', resourceType: () => 'ping',
    url: () => endpoint, failure: () => ({ errorText: 'net::ERR_ABORTED' }) };
  const requests = [{ request, terminal: 'failed', rawFailure: entry }];
  const operation = { requestId: 'request1', frameId: 'frame', loaderId: 'old', endpoint, method: 'POST', type: 'Ping',
    bodyAvailable: false, bodyMatches: null, terminalCount: 1, ackCount: 1,
    terminal: { kind: 'failed', errorText: 'net::ERR_ABORTED', canceled: true, blockedReason: null },
    acknowledgment: { requestId: 'request1', status: 200, application: true } };
  return { recorder, input: { page, form, reopened, requests,
    witness: { oldFrame: 'frame', newFrame: 'frame', oldLoader: 'old', newLoader: 'new', operations: [operation] }, recovered: true }, entry, operation };
}

test('selects only the exact original abort, preserving raw evidence and unrelated active failures', () => {
  const f = fixture(); const other = { ...f.entry, url: 'https://127.0.0.1/carlos/unrelated' };
  f.recorder.requestFailures.push(other);
  const proof = provenReloadAborts(f.recorder, f.input);
  assert.deepEqual(proof.entries, [f.entry]); assert.equal(proof.audit.blobBytesUnavailable, true);
  assert.deepEqual(proof.audit.rawFailures, [f.entry]); assert.notEqual(proof.audit.rawFailures[0], f.entry);
  assert.deepEqual(f.recorder.requestFailures, [f.entry, other]); // Validator itself never erases evidence.
});

for (const [name, change] of [
  ['missing raw recovery', f => { f.input.recovered = false; }],
  ['same document timeOrigin', f => { f.input.reopened.timeOrigin = 1; }],
  ['foreign reopened patient', f => { f.input.reopened.patient = '12'; }],
  ['changed signed program', f => { f.input.reopened.program = '8'; }],
  ['changed context', f => { f.input.reopened.token = 'other'; }],
  ['same loader', f => { f.input.witness.newLoader = 'old'; }],
  ['foreign frame', f => { f.operation.frameId = 'foreign'; }],
  ['new-document operation', f => { f.operation.loaderId = 'new'; }],
  ['foreign signed endpoint', f => { f.operation.endpoint += '&force=true'; }],
  ['wrong HTTP method', f => { f.operation.method = 'GET'; }],
  ['non-Ping operation', f => { f.operation.type = 'XHR'; }],
  ['exposed foreign body', f => { f.operation.bodyAvailable = true; f.operation.bodyMatches = false; }],
  ['missing application acknowledgment', f => { delete f.operation.acknowledgment; }],
  ['different acknowledgment requestId', f => { f.operation.acknowledgment.requestId = 'unrelated'; }],
  ['actual server refusal', f => { f.operation.acknowledgment.status = 403; }],
  ['no application marker', f => { f.operation.acknowledgment.application = false; }],
  ['duplicate application acknowledgment', f => { f.operation.ackCount = 2; }],
  ['duplicate terminal event', f => { f.operation.terminalCount = 2; }],
  ['duplicate request admission', f => { f.operation.duplicateRequest = true; }],
  ['unterminated native Request', f => { delete f.input.requests[0].terminal; }],
  ['unknown native error', f => { f.operation.terminal.errorText = 'net::ERR_FAILED'; }],
  ['browser did not cancel', f => { f.operation.terminal.canceled = false; }],
  ['blocked request', f => { f.operation.terminal.blockedReason = 'csp'; }],
  ['missing exact recorder identity', f => { f.input.requests[0].rawFailure = { ...f.entry }; }],
  ['duplicate active entry identity', f => { f.recorder.requestFailures.push(f.entry); }],
  ['ambiguous duplicate Ping witnesses', f => { f.input.requests.push(f.input.requests[0]); f.input.witness.operations.push(f.operation); }],
  ['unexpected third native request', f => { f.input.requests.push(f.input.requests[0], f.input.requests[0]); }],
  ['different original page', f => { f.input.page = {}; }],
]) {
  test(`rejects ${name} without changing the recorder`, () => {
    const f = fixture(); change(f); const before = f.recorder.requestFailures.slice();
    assert.throws(() => provenReloadAborts(f.recorder, f.input));
    assert.deepEqual(f.recorder.requestFailures, before);
  });
}

test('actual passive event observer retains failed requestId through later successful ExtraInfo without routing', async () => {
  const cdp = new EventEmitter(); let frame = { id: 'frame', loaderId: 'old' }; const commands = [];
  cdp.send = async (name, options) => { commands.push({ name, options }); return name === 'Page.getFrameTree' ? { frameTree: { frame } } : {}; };
  let detached = false; cdp.detach = async () => { detached = true; };
  const page = { context: () => ({ newCDPSession: async () => cdp }) };
  const form = { endpoint: 'https://127.0.0.1/carlos/CaseManagementEntry?chartContext=owned' };
  const observer = await observeOwnedReload(page, form);
  cdp.emit('Network.requestWillBeSent', { type: 'Ping', requestId: 'r1', frameId: 'frame', loaderId: 'old',
    request: { url: form.endpoint, method: 'POST', hasPostData: true } });
  cdp.emit('Network.loadingFailed', { requestId: 'r1', errorText: 'net::ERR_ABORTED', canceled: true });
  // A different request's200 cannot prove r1.
  cdp.emit('Network.responseReceivedExtraInfo', { requestId: 'unrelated', statusCode: 200, headers: { [h.APPLICATION_HEADER]: 'none' } });
  setImmediate(() => cdp.emit('Network.responseReceivedExtraInfo', { requestId: 'r1', statusCode: 200,
    headers: { [h.APPLICATION_HEADER]: 'none' } }));
  frame = { id: 'frame', loaderId: 'new' };
  const witness = await observer.finish(1);
  assert.equal(witness.operations[0].acknowledgment.requestId, 'r1');
  assert.equal(witness.operations[0].terminal.kind, 'failed');
  assert.equal(witness.operations[0].bodyAvailable, false);
  assert.ok(commands.every(({ name }) => ['Page.getFrameTree', 'Network.enable'].includes(name)));
  await observer.close(); assert.equal(detached, true); assert.equal(cdp.listenerCount('Network.loadingFailed'), 0);
});

function printFixture() {
  const f = fixture();
  f.input.form.provider = '99'; f.input.form.noteId = '0'; f.input.form.session = 'owned.route';
  const destination = 'https://127.0.0.1/carlos/demographic/DemographicEdit?demographic_no=11&displaymode=edit&dboperation=search_detail';
  f.input.arrived = { timeOrigin: 2, url: destination, status: 200, application: true };
  f.input.state = { before: { notes: [['1', 'FAKE clinical body']], drafts: [] },
    after: { notes: [['1', 'FAKE clinical body']], drafts: [] },
    beforeLocks: [['1', 'owned', '127.0.0.1', '99', '0', '11', 'date'], ['2', 'other', 'ip', '98', '0', '12', 'date']],
    afterLocks: [['2', 'other', 'ip', '98', '0', '12', 'date']] };
  return f;
}

test('print completion selects only the delivered native abort after actual owned navigation and exact lock removal', () => {
  const f = printFixture(); const unknown = { ...f.entry, errorText: 'net::ERR_FAILED' };
  f.recorder.requestFailures.push(unknown);
  const proof = provenPrintNavigationAborts(f.recorder, f.input);
  assert.deepEqual(proof.entries, [f.entry]);
  assert.equal(proof.audit.blobBytesUnavailable, true);
  assert.equal(proof.audit.phase, 'owned print completion navigation');
  assert.deepEqual(f.recorder.requestFailures, [f.entry, unknown]);
});

for (const [name, change] of [
  ['same document', f => { f.input.arrived.timeOrigin = 1; }],
  ['foreign patient destination', f => { f.input.arrived.url = f.input.arrived.url.replace('no=11', 'no=12'); }],
  ['foreign origin', f => { f.input.arrived.url = f.input.arrived.url.replace('127.0.0.1', 'foreign.invalid'); }],
  ['unrelated destination', f => { f.input.arrived.url = f.input.arrived.url.replace('DemographicEdit', 'Other'); }],
  ['extra destination alias', f => { f.input.arrived.url += '&demographicNo=12'; }],
  ['repeated destination patient', f => { f.input.arrived.url += '&demographic_no=11'; }],
  ['navigation refused', f => { f.input.arrived.status = 403; }],
  ['navigation lacks application marker', f => { f.input.arrived.application = false; }],
  ['clinical change', f => { f.input.state.after.notes[0][1] = 'changed'; }],
  ['draft change', f => { f.input.state.after.drafts.push(['3', 'changed']); }],
  ['own lock retained', f => { f.input.state.afterLocks.unshift(f.input.state.beforeLocks[0]); }],
  ['another lock removed', f => { f.input.state.afterLocks = []; }],
  ['another lock changed', f => { f.input.state.afterLocks[0][1] = 'changed'; }],
  ['foreign session lock', f => { f.input.state.beforeLocks[0][1] = 'owned.foreign'; }],
  ['foreign provider lock', f => { f.input.state.beforeLocks[0][3] = '98'; }],
  ['foreign note lock', f => { f.input.state.beforeLocks[0][4] = '1'; }],
  ['ambiguous owned locks', f => { f.input.state.beforeLocks.push([...f.input.state.beforeLocks[0]]); }],
  ['unacknowledged canceled request', f => { f.operation.acknowledgment.status = 403; }],
  ['different acknowledgment requestId', f => { f.operation.acknowledgment.requestId = 'another'; }],
  ['new-loader ping', f => { f.operation.loaderId = 'new'; }],
  ['unknown failure', f => { f.operation.terminal.errorText = 'net::ERR_FAILED'; }],
  ['duplicate acknowledgment', f => { f.operation.ackCount = 2; }],
  ['duplicate original Request', f => { f.input.requests.push(f.input.requests[0]); }],
  ['coherent unknown endpoint query', f => {
    f.input.form.endpoint += '&force=true'; f.operation.endpoint = f.input.form.endpoint;
    f.input.requests[0].request.url = () => f.input.form.endpoint; f.entry.url = f.input.form.endpoint;
  }],
  ['coherent repeated context query', f => {
    f.input.form.endpoint += '&chartContext=owned'; f.operation.endpoint = f.input.form.endpoint;
    f.input.requests[0].request.url = () => f.input.form.endpoint; f.entry.url = f.input.form.endpoint;
  }],
  ['foreign original page', f => { f.input.page = {}; }],
  ['stale recorder clone', f => { f.input.requests[0].rawFailure = { ...f.entry }; }],
]) test(`print completion rejects ${name} without consuming any diagnostic`, () => {
  const f = printFixture(); change(f); const before = f.recorder.requestFailures.slice();
  assert.throws(() => provenPrintNavigationAborts(f.recorder, f.input));
  assert.deepEqual(f.recorder.requestFailures, before);
});
