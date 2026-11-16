/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. */
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const test = require('node:test');
const { assertStrictPage, buildFailureDetails, createRecorder, wireStrictPage } = require('./lib/playwright-harness');

function fixture() {
  const page = new EventEmitter();
  const frame = { parentFrame: () => null, url: () => 'https://host/carlos/chart', isDetached: () => false };
  page.mainFrame = () => frame;
  const recorder = createRecorder();
  const request = (errorText = 'net::ERR_ABORTED', origin = frame, resourceType = 'xhr') => ({
    frame: () => origin, failure: () => ({ errorText }), resourceType: () => resourceType,
    method: () => 'GET',
    url: () => 'https://host/carlos/poll?demographic_no=123', isNavigationRequest: () => false,
  });
  const start = (load = request()) => { page.emit('request', load); return load; };
  const fail = load => page.emit('requestfailed', load);
  const navigate = (beforeCommit = () => {}) => {
    const navigation = { ...request('net::ERR_ABORTED', frame, 'document'), isNavigationRequest: () => true };
    page.emit('request', navigation);
    page.emit('response', { url: () => navigation.url(), status: () => 200,
      request: () => navigation, headers: () => ({}) });
    beforeCommit();
    page.emit('framenavigated', frame);
  };
  const wire = () => wireStrictPage(page, 'chart', recorder, { baseline: [] });
  return { page, frame, recorder, request, start, fail, navigate, wire };
}

for (const method of ['goto', 'reload', 'goBack', 'goForward', 'close']) {
  test(`a pending request cancelled by ${method} remains in diagnostics without failing the check`, async () => {
    const f = fixture();
    let load;
    f.page[method] = async () => {
      // The URL need not change: a reload abandons the previous document too.
      if (method === 'close') { f.fail(load); f.page.emit('close'); }
      else f.navigate(() => f.fail(load));
    };
    f.wire();
    load = f.start();
    await f.page[method]();
    assert.doesNotThrow(() => assertStrictPage(f.recorder));
    assert.equal(f.recorder.requestFailures.length, 1);
    const [detail] = buildFailureDetails(f.recorder).requestFailures;
    assert.equal(detail.navigationCancellation.classification, 'BROWSER_NAVIGATION_CANCELLATION');
    assert.equal(detail.navigationCancellation.operation, method);
    assert.equal(detail.url, 'https://host/carlos/poll');
    assert.equal(detail.errorText, 'net::ERR_ABORTED');
  });
}

test('a committed document navigation qualifies only requests already pending in the replaced frame', () => {
  const f = fixture();
  f.wire();
  const unrelated = { parentFrame: () => null, isDetached: () => false, url: () => 'https://host/carlos/other' };
  const child = { parentFrame: () => f.frame, isDetached: () => false, url: () => 'https://host/carlos/child' };
  const mainLoad = f.start();
  const childLoad = f.start(f.request('net::ERR_ABORTED', child));
  const otherLoad = f.start(f.request('net::ERR_ABORTED', unrelated));
  const navigation = { ...f.request('net::ERR_ABORTED', f.frame, 'document'), isNavigationRequest: () => true };
  f.page.emit('request', navigation);
  f.page.emit('response', { url: () => navigation.url(), status: () => 200,
    request: () => navigation, headers: () => ({}) });
  f.fail(mainLoad);
  f.fail(childLoad);
  f.fail(otherLoad);
  // Cancellation can precede the navigation's commit event.
  assert.throws(() => assertStrictPage(f.recorder), /3 JavaScript-layer/);
  f.page.emit('framenavigated', f.frame);
  assert.throws(() => assertStrictPage(f.recorder), /1 JavaScript-layer/);
  assert.equal(buildFailureDetails(f.recorder).requestFailures.filter(entry => entry.navigationCancellation).length, 2);
});

test('an independently aborted request stays a failure after a later navigation', async () => {
  const f = fixture();
  f.page.reload = async () => f.navigate();
  f.wire();
  f.fail(f.start());
  await f.page.reload();
  assert.throws(() => assertStrictPage(f.recorder), /xhr request failed \(net::ERR_ABORTED\)/);
  assert.equal(buildFailureDetails(f.recorder).requestFailures[0].navigationCancellation, undefined);
});

test('a failed navigation without a committed replacement cannot excuse an abort', async () => {
  const f = fixture();
  let load;
  f.page.goto = async () => { f.fail(load); throw new Error('navigation refused'); };
  f.wire();
  load = f.start();
  await assert.rejects(f.page.goto(), /navigation refused/);
  assert.throws(() => assertStrictPage(f.recorder), /request failed/);
});

test('new loads and non-abort failures during a navigation remain failures', async () => {
  const f = fixture();
  let oldLoad;
  f.page.reload = async () => {
    f.fail(f.start()); // Issued after the snapshot, not abandoned with the previous document.
    f.fail(oldLoad);
    f.navigate();
  };
  f.wire();
  oldLoad = f.start(f.request('net::ERR_CONTENT_LENGTH_MISMATCH'));
  await f.page.reload();
  assert.throws(() => assertStrictPage(f.recorder), /2 JavaScript-layer/);
  assert.equal(buildFailureDetails(f.recorder).requestFailures.filter(entry => entry.navigationCancellation).length, 0);
});

for (const resourceType of ['xhr', 'font', 'image']) {
  test(`a late old-document ${resourceType} load qualifies only at replacement`, () => {
    const f = fixture();
    f.wire();
    f.navigate(() => {
      f.fail(f.start(f.request('net::ERR_ABORTED', f.frame, resourceType)));
      assert.throws(() => assertStrictPage(f.recorder), /request failed/);
    });
    assert.doesNotThrow(() => assertStrictPage(f.recorder));
    const [detail] = buildFailureDetails(f.recorder).requestFailures;
    assert.equal(detail.resourceType, resourceType);
    assert.equal(detail.navigationCancellation.operation, 'document-navigation');
  });
}

test('loads with no issuing frame and public cancellation claims never qualify', async () => {
  const f = fixture();
  let serviceWorker;
  f.page.reload = async () => { f.fail(serviceWorker); f.navigate(); };
  f.wire();
  serviceWorker = f.start({ ...f.request(), frame: () => { throw new Error('service worker'); } });
  await f.page.reload();
  f.recorder.requestFailures.push({ label: 'chart', resourceType: 'fetch', errorText: 'net::ERR_ABORTED',
    navigationCancellation: { classification: 'BROWSER_NAVIGATION_CANCELLATION', operation: 'reload' } });
  assert.throws(() => assertStrictPage(f.recorder), /2 JavaScript-layer/);
  assert.equal(buildFailureDetails(f.recorder).requestFailures.filter(entry => entry.navigationCancellation).length, 0);
});

test('a finished navigation request cannot retroactively qualify failed loads', () => {
  const f = fixture();
  f.wire();
  const load = f.start();
  const navigation = { ...f.request('net::ERR_ABORTED', f.frame, 'document'), isNavigationRequest: () => true };
  f.page.emit('request', navigation);
  f.page.emit('requestfailed', navigation);
  f.fail(load);
  f.page.emit('framenavigated', f.frame);
  assert.throws(() => assertStrictPage(f.recorder), /1 JavaScript-layer/);
});

test('a same-document hash navigation cannot excuse an unrelated abort', async () => {
  const f = fixture();
  let load;
  f.page.goto = async () => { f.fail(load); f.page.emit('framenavigated', f.frame); };
  f.wire();
  load = f.start();
  await f.page.goto('https://host/carlos/chart#tab');
  assert.throws(() => assertStrictPage(f.recorder), /xhr request failed/);
});

test('an application abort while the replacement response is pending stays fatal after commit', async () => {
  const f = fixture();
  let load;
  f.page.goto = async () => {
    const navigation = { ...f.request('net::ERR_ABORTED', f.frame, 'document'), isNavigationRequest: () => true };
    f.page.emit('request', navigation);
    f.fail(load); // The old document's AbortController can run while the server is still thinking.
    f.page.emit('response', { url: () => navigation.url(), status: () => 200,
      request: () => navigation, headers: () => ({}) });
    f.page.emit('framenavigated', f.frame);
  };
  f.wire();
  load = f.start();
  await f.page.goto('https://host/carlos/slow-navigation');
  assert.throws(() => assertStrictPage(f.recorder), /xhr request failed/);
  assert.equal(buildFailureDetails(f.recorder).requestFailures[0].navigationCancellation, undefined);
});

test('history-state navigation while awaiting a replacement response cannot qualify an application abort', async () => {
  const f = fixture();
  let load;
  f.page.goto = async () => {
    const navigation = { ...f.request('net::ERR_ABORTED', f.frame, 'document'), isNavigationRequest: () => true };
    f.page.emit('request', navigation);
    // The old document can change its history state while /b is pending. This
    // event must not turn the unfinished replacement into a committed one.
    f.page.emit('framenavigated', f.frame);
    f.fail(load);
    f.page.emit('response', { url: () => navigation.url(), status: () => 200,
      request: () => navigation, headers: () => ({}) });
    f.page.emit('framenavigated', f.frame);
  };
  f.wire();
  load = f.start();
  await f.page.goto('https://host/carlos/slow-navigation');
  assert.throws(() => assertStrictPage(f.recorder), /xhr request failed/);
  assert.equal(buildFailureDetails(f.recorder).requestFailures[0].navigationCancellation, undefined);
});

test('a redirect response cannot excuse an application abort before the final document arrives', async () => {
  const f = fixture();
  let load;
  f.page.goto = async () => {
    const navigation = { ...f.request('net::ERR_ABORTED', f.frame, 'document'), isNavigationRequest: () => true };
    f.page.emit('request', navigation);
    f.page.emit('response', { url: () => navigation.url(), status: () => 302,
      request: () => navigation, headers: () => ({}) });
    f.fail(load);
    f.navigate();
  };
  f.wire();
  load = f.start();
  await f.page.goto('https://host/carlos/redirect');
  assert.throws(() => assertStrictPage(f.recorder), /xhr request failed/);
});
