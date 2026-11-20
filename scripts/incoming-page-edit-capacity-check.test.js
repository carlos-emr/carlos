/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {EventEmitter} = require('node:events');
const {createHash} = require('node:crypto');
const {validatePageEdit, waitingPage, createPageEditCleanupGuard, removeInjectedFailures,
  checkIncomingPageEditCapacity} = require('./lib/incoming-page-edit-capacity-check');
const revision = 'a'.repeat(64), name = 'FAKE-PW-owned.pdf';
const body = new URLSearchParams({pdfAction: 'Rotate90', pdfName: name, pdfDir: 'File', pdfPageNumber: '1',
  defaultQueue: '1', queueList: '1', pdfNo: '2', sourceRevision: revision, 'CSRF-TOKEN': 'owned-token'}).toString();
const request = (value = body, method = 'POST') => ({postData: () => value, method: () => method});
test('native admission probe permits only its exact owned document revision and operation', () => {
  assert.equal(validatePageEdit(request(), name, revision).toString(), body);
});
for (const [key, value] of Object.entries({pdfAction: 'DeletePDF', pdfName: 'other.pdf', pdfDir: 'Fax',
  pdfPageNumber: '2', defaultQueue: '2', queueList: '2', queueId: '2', sourceRevision: 'b'.repeat(64), 'CSRF-TOKEN': ''})) {
  test(`native admission guard refuses changed ${key}`, () => {
    const fields = new URLSearchParams(body); fields.set(key, value);
    assert.throws(() => validatePageEdit(request(fields.toString()), name, revision));
  });
}
test('native admission guard rejects duplicate identity and GET', () => {
  assert.throws(() => validatePageEdit(request(body + '&pdfName=' + name), name, revision));
  assert.throws(() => validatePageEdit(request(body, 'GET'), name, revision));
});
test('wait fixture encodes frozen fields and loads the installed script from its context', () => {
  const fields = new URLSearchParams(body); fields.append('provider', 'one'); fields.append('provider', 'two');
  fields.set('note', '"><script>untrusted</script>');
  const html = waitingPage(fields, 'https://local/carlos/documentManager/ViewIncomingDocs');
  assert.match(html, /src="\/carlos\/js\/incomingDocumentPageEditWait.js"/);
  assert.equal((html.match(/name="provider"/g) || []).length, 2);
  assert.ok(html.includes('&quot;&gt;&lt;script&gt;untrusted&lt;/script&gt;'));
  assert.match(html, /id="incoming-page-edit-cancel" href="\/carlos\/documentManager\/ViewIncomingDocs\?queueId=1&amp;pdfDir=File&amp;pdfNo=2&amp;pdfPageNumber=1"/);
  assert.throws(() => waitingPage(fields, 'https://local/carlos/documentManager/ManageDocument'));
});

for (const [key, value] of [['pdfNo', '0'], ['pdfNo', '1&pdfAction=DeletePDF'], ['queueList', '2'], ['pdfDir', 'Fax']]) {
  test(`native cancellation rejects invalid ${key}=${value}`, () => {
    const fields = new URLSearchParams(body); fields.set(key, value);
    assert.throws(() => waitingPage(fields, 'https://local/carlos/documentManager/ViewIncomingDocs'));
  });
}

function fixture(t) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'incoming-native-guard-test-'));
  t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
  const source = path.join(directory, name); fs.writeFileSync(source, 'owned PDF bytes');
  const guard = createPageEditCleanupGuard({marker: 'owned-test', source, owned: [source], evidenceRoot: directory});
  const revision = createHash('sha256').update(fs.readFileSync(source)).digest('hex');
  const fields = new URLSearchParams(body); fields.set('sourceRevision', revision);
  const endpoint = 'https://local/carlos/documentManager/ViewIncomingDocs';
  return {directory, source, guard, fields, endpoint,
    request: () => ({...request(fields.toString()), url: () => endpoint})};
}

test('cleanup requires a completed forwarded response and evidence excludes raw CSRF/body', t => {
  const f = fixture(t);
  f.guard.assertCleanup();
  f.guard.forward(f.request());
  assert.throws(() => f.guard.assertCleanup(), /retain all fixtures/);
  const text = fs.readFileSync(f.guard.journal, 'utf8'), journal = JSON.parse(text);
  assert.equal(journal.state, 'forwarded');
  assert.equal(journal.files[0].sha256, f.fields.get('sourceRevision'));
  assert.equal(journal.files[0].inode, fs.statSync(f.source).ino);
  assert.equal(fs.statSync(f.guard.journal).mode & 0o777, 0o600);
  assert.ok(!text.includes('owned-token'));
  f.guard.completed(); f.guard.assertCleanup();
  assert.equal(JSON.parse(fs.readFileSync(f.guard.journal, 'utf8')).state, 'response-completed');
  assert.throws(() => f.guard.forward(f.request()), /another forwarded/);
});

test('failed transport remains uncertain even if completion arrives later', t => {
  const f = fixture(t); f.guard.forward(f.request());
  f.guard.unknown('transport failed'); f.guard.completed();
  assert.throws(() => f.guard.assertCleanup(), /retain all fixtures/);
  const journal = JSON.parse(fs.readFileSync(f.guard.journal, 'utf8'));
  assert.equal(journal.uncertain, true); assert.equal(journal.reason, 'transport failed');
  assert.equal(fs.readFileSync(f.source, 'utf8'), 'owned PDF bytes');
});

test('expected failures remove only five proven objects and at most five matching console messages', () => {
  const expected = Array.from({length: 5}, () => ({status: 503, method: 'POST', url: 'owned', label: 'editor'}));
  const old = {status: 503, method: 'POST', url: 'owned', label: 'editor'};
  const get = {...old, method: 'GET'}, extraPost = {...old}, other = {...old, label: 'other-page'};
  const message = () => ({label: 'editor', location: {url: 'owned'}, text: 'Failed to load resource: server returned 503'});
  const before = message(), extraMessages = Array.from({length: 6}, message);
  const otherMessage = {...message(), label: 'other-page'};
  const recorder = {badResponses: [old, ...expected, get, extraPost, other], consoleIssues: [before, ...extraMessages, otherMessage]};
  removeInjectedFailures(recorder, new Set(expected), 1);
  assert.deepEqual(recorder.badResponses, [old, get, extraPost, other]);
  assert.deepEqual(recorder.consoleIssues, [before, extraMessages[5], otherMessage]);
});

test('incomplete expected-response evidence cannot suppress any errors', () => {
  const entry = {status: 503, method: 'POST', url: 'owned', label: 'editor'};
  const recorder = {badResponses: [entry], consoleIssues: []};
  assert.throws(() => removeInjectedFailures(recorder, new Set([entry]), 0), /exactly five/);
  assert.deepEqual(recorder.badResponses, [entry]);
});

function fakePage(f, recorder, forwardFailure = false) {
  const page = new EventEmitter(), events = [];
  let handler, resolveResponse, predicate;
  page.locator = () => ({evaluate: async () => f.endpoint, waitFor: async () => {}});
  page.route = async (pattern, callback) => {handler = callback;};
  page.unroute = async () => {events.push('unroute'); handler = null;};
  page.close = async () => {events.push('close');};
  page.waitForLoadState = async () => {};
  page.waitForResponse = callback => {predicate = callback; return new Promise(resolve => {resolveResponse = resolve;});};
  async function dispatch(fields = f.fields) {
    const req = {...request(fields.toString()), url: () => f.endpoint};
    function respond(status) {
      if (status === 503) recorder.badResponses.push({status, method: 'POST', url: f.endpoint, label: 'editor'});
      const response = {request: () => req, status: () => status, url: () => f.endpoint, finished: async () => null};
      page.emit('response', response);
      if (predicate(response)) resolveResponse(response);
    }
    await handler({request: () => req,
      abort: async () => {events.push('abort');},
      fulfill: async () => {events.push('fulfill'); respond(503);},
      continue: async () => {
        events.push('continue');
        if (forwardFailure) throw new Error('transport failed');
        respond(200);
      }});
  }
  return {page, events, dispatch};
}

test('failure before forwarding aborts the route and closes the retry page before removing interception', async t => {
  const f = fixture(t), recorder = {badResponses: [], consoleIssues: []};
  const p = fakePage(f, recorder);
  await assert.rejects(checkIncomingPageEditCapacity({recorder}, p.page, name, f.source, async () => {
    fs.writeFileSync(f.source, 'unexpected local replacement');
    await p.dispatch(); throw new Error('stop probe');
  }, f.guard), /stop probe/);
  assert.deepEqual(p.events, ['abort', 'close', 'unroute']); f.guard.assertCleanup();
});

test('a changed CSRF token is aborted before transport rather than silently refreshing the frozen intent', async t => {
  const f = fixture(t), recorder = {badResponses: [], consoleIssues: []};
  const p = fakePage(f, recorder);
  await assert.rejects(checkIncomingPageEditCapacity({recorder}, p.page, name, f.source, async () => {
    await p.dispatch();
    const changed = new URLSearchParams(f.fields); changed.set('CSRF-TOKEN', 'other-session-token');
    await p.dispatch(changed); throw new Error('stop probe');
  }, f.guard), /stop probe/);
  assert.deepEqual(p.events, ['fulfill', 'abort', 'close', 'unroute']); f.guard.assertCleanup();
});

test('failed forwarding preserves pending evidence and prevents the caller from deleting owned files', async t => {
  const f = fixture(t), recorder = {badResponses: [], consoleIssues: []};
  const p = fakePage(f, recorder, true);
  await assert.rejects(checkIncomingPageEditCapacity({recorder}, p.page, name, f.source, async () => {
    for (let index = 0; index < 6; index++) await p.dispatch();
    throw new Error('stop probe');
  }, f.guard), /stop probe/);
  assert.equal(p.events.filter(event => event === 'continue').length, 1);
  assert.deepEqual(p.events.slice(-3), ['abort', 'close', 'unroute']);
  assert.throws(() => f.guard.assertCleanup(), /retain all fixtures/);
  assert.equal(JSON.parse(fs.readFileSync(f.guard.journal, 'utf8')).uncertain, true);
  assert.ok(fs.existsSync(f.source));
});

test('five proven refusals and one completely received real response allow later fixture cleanup', async t => {
  const f = fixture(t), recorder = {badResponses: [], consoleIssues: []};
  const p = fakePage(f, recorder);
  await checkIncomingPageEditCapacity({recorder}, p.page, name, f.source, async () => {
    for (let index = 0; index < 6; index++) await p.dispatch();
  }, f.guard);
  assert.deepEqual(p.events, ['fulfill', 'fulfill', 'fulfill', 'fulfill', 'fulfill', 'continue', 'unroute']);
  assert.deepEqual(recorder.badResponses, []); f.guard.assertCleanup();
});
