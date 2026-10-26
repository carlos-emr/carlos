/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { EventEmitter } = require('node:events');
const test = require('node:test');
const h = require('./lib/playwright-harness');
const { observeLegacyExit } = require('./legacy-encounter-editor-isolation-playwright-checks');
const base = new URL('http://127.0.0.1:8080/carlos');
const save = 'http://127.0.0.1:8080/carlos/encounter/SaveEncounter2';
const script = 'http://127.0.0.1:8080/carlos/csrfguard';
const context = 'FAKE-own-signed-context';

function setup({ strict = true } = {}) {
  const recorder = h.createRecorder();
  const page = new EventEmitter();
  const frame = { url: () => page.address, isDetached: () => false };
  page.address = 'http://127.0.0.1:8080/carlos/encounter/IncomingEncounter?demographicNo=101';
  page.mainFrame = () => frame;
  page.isClosed = () => Boolean(page.closed);
  if (strict) h.wireStrictPage(page, 'closing-editor', recorder, { baseline: [] });
  const observer = observeLegacyExit(page, recorder, { base, patient: '101', chartContext: context, timeout: 100 });
  const request = (options = {}) => ({
    url: () => options.url || script,
    method: () => options.method || 'GET',
    isNavigationRequest: () => Boolean(options.navigation),
    resourceType: () => options.type || 'script',
    frame: () => options.frame || frame,
    headers: () => ({ referer: options.referer || save }),
    postData: () => options.body || '',
    failure: () => ({ errorText: options.error || 'net::ERR_ABORTED' }),
  });
  const exit = (options = {}) => {
    const own = request({ url: save, method: 'POST', navigation: true, type: 'document',
      body: new URLSearchParams({ btnPressed: 'Exit', demographicNo: '101', chartContext: context }).toString(), ...options });
    page.emit('request', own);
    page.address = save;
    return { request: own, response: { request: () => own, url: () => save,
      status: () => options.status || 200, headers: () => options.noApp ? {} : { [h.APPLICATION_HEADER]: 'none' } } };
  };
  const close = () => { page.closed = true; page.emit('close'); };
  return { page, frame, recorder, observer, request, exit, close };
}
function dispose(s) { s.observer.dispose(); }

for (const delayed of [false, true]) test(`exact native Exit acknowledges only its own terminal abort (delayed=${delayed})`, async () => {
  const s = setup();
  let artifact;
  try {
    const own = s.exit();
    s.observer.acknowledge(own.response);
    const req = s.request();
    s.page.emit('request', req);
    const unrelated = { label: 'another-editor', url: script, resourceType: 'script', errorText: 'net::ERR_ABORTED' };
    s.recorder.requestFailures.push(unrelated);
    s.close();
    if (delayed) setTimeout(() => s.page.emit('requestfailed', req), 10);
    else s.page.emit('requestfailed', req);
    artifact = await s.observer.settle();
    assert.deepEqual(s.recorder.requestFailures, [unrelated]);
    const proof = JSON.parse(fs.readFileSync(artifact, 'utf8'));
    assert.equal(proof.nativeExitAcknowledged, true);
    assert.equal(proof.rawFailure.url, script);
    assert.equal(proof.rawFailure.label, 'closing-editor');
    assert.equal(JSON.stringify(proof).includes(context), false, 'No signed context/clinical POST data in the audit');
    assert.throws(() => h.assertStrictPage(s.recorder), /request failed/, 'Other editor remains a strict finding');
  } finally { dispose(s); if (artifact) fs.rmSync(path.dirname(artifact), { recursive: true }); }
});

for (const duplicate of ['different Request', 'same Request twice']) test(`duplicate ${duplicate} aborts remain strict`, async () => {
  const s = setup();
  try {
    s.observer.acknowledge(s.exit().response);
    const first = s.request();
    s.page.emit('request', first);
    const second = duplicate === 'different Request' ? s.request() : first;
    if (second !== first) s.page.emit('request', second);
    s.close();
    s.page.emit('requestfailed', first);
    s.page.emit('requestfailed', second);
    const before = s.recorder.requestFailures.slice();
    await assert.rejects(s.observer.settle(), /ambiguous or duplicated/);
    assert.deepEqual(s.recorder.requestFailures, before);
    assert.equal(before.length, 2);
  } finally { dispose(s); }
});

for (const options of [{ frame: { url: () => save, isDetached: () => false } },
  { referer: 'http://127.0.0.1:8080/carlos/other' }, { url: script + '?unexpected=1' }, { method: 'POST' }]) {
  test(`same-path failures without exact Exit ownership stay strict: ${JSON.stringify(options)}`, async () => {
    const s = setup();
    try {
      s.observer.acknowledge(s.exit().response);
      const req = s.request(options);
      s.page.emit('request', req);
      s.close();
      s.page.emit('requestfailed', req);
      assert.equal(await s.observer.settle(), undefined);
      assert.equal(s.recorder.requestFailures.length, 1);
      assert.throws(() => h.assertStrictPage(s.recorder), /request failed/);
    } finally { dispose(s); }
  });
}

for (const options of [{ status: 403 }, { noApp: true },
  { body: 'btnPressed=Exit&demographicNo=202&chartContext=' + context },
  { body: 'btnPressed=Save&demographicNo=101&chartContext=' + context }]) {
  test(`a nonacknowledged native Exit cannot classify failures: ${JSON.stringify(options)}`, async () => {
    const s = setup();
    try {
      assert.throws(() => s.observer.acknowledge(s.exit(options).response), /exact owned native form POST/);
      s.close();
      await assert.rejects(s.observer.settle(), /no acknowledged native closing phase/);
    } finally { dispose(s); }
  });
}

test('a matching stale recorder entry is never adopted when the common listener appended nothing', async () => {
  const s = setup({ strict: false });
  try {
    s.observer.acknowledge(s.exit().response);
    const req = s.request();
    const stale = { label: 'closing-editor', url: script, resourceType: 'script', errorText: 'net::ERR_ABORTED' };
    s.recorder.requestFailures.push(stale);
    s.page.emit('request', req);
    s.close();
    s.page.emit('requestfailed', req);
    await assert.rejects(s.observer.settle(), /ambiguous or duplicated/);
    assert.deepEqual(s.recorder.requestFailures, [stale]);
  } finally { dispose(s); }
});

test('unknown errors and missing terminal events remain failures', async () => {
  for (const terminal of [false, true]) {
    const s = setup();
    try {
      s.observer.acknowledge(s.exit().response);
      const req = s.request({ error: 'net::ERR_FAILED' });
      s.page.emit('request', req);
      s.close();
      if (terminal) s.page.emit('requestfailed', req);
      await assert.rejects(s.observer.settle(), terminal ? /not one uniquely recorded navigation abort/ : /no terminal browser event/);
      assert.equal(s.recorder.requestFailures.length, terminal ? 1 : 0);
    } finally { dispose(s); }
  }
});

test('a successful guard has no allowance and observer retirement retains common diagnostics', async () => {
  const s = setup();
  const commonFailures = s.page.listenerCount('requestfailed') - 2;
  try {
    s.observer.acknowledge(s.exit().response);
    const req = s.request();
    s.page.emit('request', req);
    s.page.emit('requestfinished', req);
    s.close();
    assert.equal(await s.observer.settle(), undefined);
    h.assertStrictPage(s.recorder);
  } finally { dispose(s); }
  assert.equal(s.page.listenerCount('requestfailed'), commonFailures);
});


test('a failed trigger retires private observers while unknown frameless requests remain strict', () => {
  const s = setup();
  const before = s.page.listenerCount('requestfailed') - 2;
  const req = s.request();
  req.frame = () => { throw new Error('No frame for this service-worker request'); };
  assert.doesNotThrow(() => s.page.emit('request', req));
  s.observer.dispose(); // The native click failed before a POST/close acknowledgment.
  assert.equal(s.page.listenerCount('requestfailed'), before);
  s.page.emit('requestfailed', req);
  assert.equal(s.recorder.requestFailures.length, 1);
  assert.throws(() => h.assertStrictPage(s.recorder), /request failed/);
});
