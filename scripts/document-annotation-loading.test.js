/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/documentAnnotate.js'), 'utf8');

function viewer(randomValue = .5) {
  const math = Object.create(Math); math.random = () => randomValue;
  const frames = [], requests = [], images = [], words = [], saves = [], wraps = [], timers = [], revoked = [];
  let synchronousFailure = false;
  const classes = () => { const set = new Set(['pending']); return {
    add: value => set.add(value), remove: value => set.delete(value), contains: value => set.has(value),
  }; };
  for (let i = 1; i <= 12; i++) {
    const attributes = new Map();
    const image = {dataset: {page: String(i)}, classList: classes(), clientWidth: 100,
      top: (i - 1) * 1100, getBoundingClientRect() { return {top: this.top, bottom: this.top + 1000}; },
      getAttribute: name => attributes.get(name) ?? null,
      setAttribute(name, value) { attributes.set(name, value); },
      removeAttribute: name => attributes.delete(name)};
    const wrap = {dataset: image.dataset, classList: classes(), getBoundingClientRect: () => image.getBoundingClientRect(),
      querySelector: selector => selector === 'img' ? image : null};
    image.parentNode = wrap;
    images.push(image); wraps.push(wrap);
  }
  const status = {appendChild() {}};
  const pages = {style: {setProperty() {}}, querySelectorAll: selector => selector === '.page' ? wraps : images};
  const context = {Math: math, window: {CARLOS_ANNOTATE: {contextPath: '/carlos', docId: '42'}, innerHeight: 720,
    requestAnimationFrame: callback => frames.push(callback), setTimeout: (callback, delay) => timers.push({callback, delay}),
    clearTimeout: id => { if (timers[id - 1]) timers[id - 1].cancelled = true; }}, URL: {
    createObjectURL: () => `blob:test-${Math.random()}`, revokeObjectURL: url => revoked.push(url),
  }, document: {
    getElementById: id => id === 'pages' ? pages : status, addEventListener() {}, createElement: () => ({}),
  }, fetch(url, options) {
    if (synchronousFailure) { synchronousFailure = false; throw new Error('dispatch failure'); }
    return new Promise((resolve, reject) => {
      const task = {url, options, page: Number(new URL(url, 'http://example.test').searchParams.get('page')), resolve, reject};
      (url.includes('DocumentTextBoxes') ? words : url.includes('SaveAnnotatedDocument') ? saves : requests).push(task);
    });
  }};
  vm.createContext(context);
  vm.runInContext(source.replace(/\}\)\(\);\s*$/, `
    fitAllNotes = () => {}; csrfTokenReady = () => Promise.resolve(); csrfToken = () => 'test';
    updateCounts = () => {}; setSaving = value => {state.saving = value;};
    annotationFontReady = () => Promise.resolve();
    window.testViewer = {
    save, waitForCapacity, resumeDocumentRequests, suspendDocumentRequests, cancelUnwantedCapacityWaits, state, loadVisiblePages, prefetchVisibleWordBoxes, fetchWordBoxes, fetchDocumentResource, zoom, pageImageLoaded, pageImageFailed,
    active: () => documentRequestsInFlight
  };})();`), context);
  const api = context.window.testViewer;
  return {api, images, requests, words, saves, status, frames, timers, revoked,
    failDispatch() { synchronousFailure = true; },
    scrollTo(page) { images.forEach((image, index) => { image.top = (index + 1 - page) * 1100; }); },
    frame() { const pending = frames.splice(0); pending.forEach(callback => callback()); },
    async complete(page, error = false) {
      const task = requests.find(request => request.page === page && !request.settled);
      task.settled = true;
      task.resolve({ok: true, blob: async () => ({})});
      await flushPromises();
      if (images[page - 1].dataset.objectUrl) (error ? api.pageImageFailed : api.pageImageLoaded).call(images[page - 1]);
    }};
}
const flushPromises = () => new Promise(resolve => setImmediate(resolve));

test('rapid highlight scrolling bounds combined image and text requests and resumes at the current viewport', async () => {
  const v = viewer();
  v.api.state.tool = 'highlight';
  v.api.loadVisiblePages(); // images 1 and 2
  v.api.prefetchVisibleWordBoxes(); // text 1
  await flushPromises();
  v.scrollTo(5); v.api.loadVisiblePages(); v.api.prefetchVisibleWordBoxes();
  assert.equal(v.api.active(), 4);
  await flushPromises();
  v.scrollTo(12); v.api.loadVisiblePages(); v.api.prefetchVisibleWordBoxes();
  await flushPromises();
  assert.equal(v.requests.length + v.words.length, 4);
  await v.complete(1); v.frame(); await flushPromises();
  assert.ok(v.requests.at(-1).page >= 11, 'completion must schedule current pages, not stale intermediate pages');
  assert.equal(v.api.active(), 4);
  v.words[0].resolve({ok: true, json: async () => ({words: []})});
  await flushPromises(); v.frame(); await flushPromises();
  assert.equal(v.api.active(), 4);
  assert.ok(v.requests.some(request => request.page === 12));
});

test('zoom retains in-flight permits and replaces only after completion with the newest resolution', async () => {
  const v = viewer();
  v.api.loadVisiblePages(); await flushPromises();
  v.api.zoom(1); v.api.zoom(1);
  await flushPromises();
  assert.equal(v.requests.length, 2, 'zoom must not cancel server work and dispatch replacements immediately');
  assert.equal(v.api.active(), 2);
  await v.complete(1); v.frame(); await flushPromises();
  assert.equal(v.requests.length, 3);
  assert.match(v.requests.at(-1).url, /dpi=192$/);
  assert.equal(v.images[0].classList.contains('pending'), true, 'stale render must not become annotatable');
  await v.complete(1); v.frame(); await flushPromises();
  assert.equal(v.images[0].classList.contains('pending'), false);
  assert.equal(v.api.active(), 1);
});

test('failed old-zoom render releases exactly once without showing a stale error', async () => {
  const v = viewer();
  v.api.loadVisiblePages(); await flushPromises(); v.api.zoom(1);
  await v.complete(1, true);
  v.api.pageImageFailed.call(v.images[0]); // duplicate event must not return another permit
  assert.equal(v.api.active(), 1);
  assert.equal(v.status.textContent, undefined);
  v.frame(); await flushPromises();
  assert.match(v.requests.at(-1).url, /dpi=144$/);
});

test('current image failure is visible and is not automatically retried forever', async () => {
  const v = viewer();
  v.api.loadVisiblePages();
  await flushPromises(); await v.complete(1, true); v.frame(); await flushPromises();
  assert.match(v.status.textContent, /Page 1 could not be loaded/);
  assert.equal(v.status.className, 'status error');
  assert.equal(v.images[0].parentNode.classList.contains('load-failed'), true);
  assert.equal(v.requests.length, 2);
});

test('failed text extraction frees capacity without a retry loop; a later drag can retry', async () => {
  const v = viewer();
  v.api.state.tool = 'highlight';
  v.api.state.tool = 'highlight'; v.api.fetchWordBoxes(1); await flushPromises();
  v.words[0].reject(new Error('network failed'));
  await flushPromises(); v.frame();
  assert.equal(v.words.length, 1);
  assert.equal(v.api.state.wordBoxes[1], undefined);
  v.api.state.tool = 'highlight'; v.api.fetchWordBoxes(1); await flushPromises();
  assert.equal(v.words.length, 2);
  v.words[1].resolve({ok: true, json: async () => ({words: []})});
  await flushPromises();
  v.api.zoom(1); v.api.prefetchVisibleWordBoxes();
  assert.equal(v.words.length, 2, 'empty text cache survives zoom');
  assert.equal(v.api.state.wordBoxes[1].length, 0);
});

test('synchronous fetch failure releases its slot and leaves extraction retryable', async () => {
  const v = viewer(); v.failDispatch();
  v.api.state.tool = 'highlight'; v.api.fetchWordBoxes(1); await flushPromises();
  assert.equal(v.api.active(), 0);
  assert.equal(v.api.state.wordBoxes[1], undefined);
  v.api.state.tool = 'highlight'; v.api.fetchWordBoxes(1); await flushPromises();
  assert.equal(v.words.length, 1);
});

test('capacity waits beyond three responses while keeping its slot, then continues without losing state', async () => {
  const v = viewer(); v.api.loadVisiblePages(); await flushPromises();
  const busy = {ok: false, status: 503, headers: {get: () => '999999'}, text: async () => 'busy'};
  for (let attempt = 0; attempt < 6; attempt++) {
    v.requests.filter(request => request.page === 1).at(-1).resolve(busy);
    await flushPromises();
    assert.equal(v.api.active(), 2);
    assert.ok(v.timers.at(-1).delay <= 10000);
    assert.match(v.status.textContent, /Waiting for document capacity/);
    assert.equal(v.status.className, 'status busy');
    v.timers.at(-1).callback(); await flushPromises();
  }
  v.requests.filter(request => request.page === 1).at(-1).resolve({ok: true, blob: async () => ({})});
  await flushPromises(); v.api.pageImageLoaded.call(v.images[0]);
  assert.equal(v.requests.filter(request => request.page === 1).length, 7);
  assert.equal(v.api.active(), 1);
  assert.equal(v.images[0].classList.contains('pending'), false);
});

test('successful retry decodes and releases the object URL; ordinary500 is not retried', async () => {
  const v = viewer(); v.api.loadVisiblePages(); await flushPromises();
  v.requests[0].resolve({ok: false, status: 503, headers: {get: () => '1'}, text: async () => ''});
  await flushPromises(); v.timers[0].callback(); await flushPromises();
  const retry = v.requests.at(-1);
  retry.resolve({ok: true, blob: async () => ({})}); await flushPromises();
  const url = v.images[0].dataset.objectUrl;
  assert.equal(v.api.active(), 2, 'slot remains owned through image decode');
  v.api.pageImageLoaded.call(v.images[0]);
  assert.ok(v.revoked.includes(url));
  v.requests[1].resolve({ok: false, status: 500, text: async () => ''}); await flushPromises();
  assert.equal(v.timers.length, 1);
  assert.equal(v.api.active(), 0);
});

test('explicit text-layer read failure is retryable rather than cached as an empty OCR layer', async () => {
  const v = viewer(); v.api.state.tool = 'highlight'; v.api.fetchWordBoxes(1); await flushPromises();
  v.words[0].resolve({ok: true, json: async () => ({textLayerRead: false, words: []})});
  await flushPromises();
  assert.equal(v.api.state.wordBoxes[1], undefined);
  v.api.state.tool = 'highlight'; v.api.fetchWordBoxes(1); await flushPromises();
  assert.equal(v.words.length, 2);
});

test('leaving the viewer cancels capacity timers and prevents further requests without discarding marks', async () => {
  const v = viewer();
  v.api.state.annotations.push({type: 'highlight', page: 1, x: .1, y: .1, w: .2, h: .1});
  v.api.loadVisiblePages(); await flushPromises();
  v.requests[0].resolve({status: 503, headers: {get: () => '1'}, text: async () => ''});
  await flushPromises();
  const timer = v.timers[0];
  v.api.suspendDocumentRequests(); await flushPromises();
  assert.equal(timer.cancelled, true);
  timer.callback(); await flushPromises(); v.frame(); await flushPromises();
  assert.equal(v.requests.length, 2);
  assert.equal(v.api.state.annotations.length, 1);
  assert.equal(v.images[0].dataset.loadingUrl, undefined);
});

test('zoom cancels only stale capacity waits and next admission targets the newest resolution', async () => {
  const v = viewer(); v.api.loadVisiblePages(); await flushPromises();
  v.requests[0].resolve({status: 503, headers: {get: () => '1'}, text: async () => ''});
  await flushPromises();
  v.api.zoom(1); v.api.zoom(1); await flushPromises(); v.frame(); await flushPromises();
  assert.equal(v.timers[0].cancelled, true);
  assert.match(v.requests.at(-1).url, /dpi=192$/);
  assert.equal(v.api.active(), 2); // original page2 render still owns its slot
  assert.equal(v.status.className, 'status');
});

test('save waits through repeated explicit pre-acceptance503 and submits the same snapshot exactly until accepted', async () => {
  const v = viewer();
  v.api.state.annotations.push({type: 'highlight', page: 1, x: .1, y: .1, w: .2, h: .1});
  v.api.save(false); await flushPromises();
  const body = v.saves[0].options.body;
  for (let attempt = 0; attempt < 5; attempt++) {
    v.saves.at(-1).resolve({ok: false, status: 503, headers: {get: () => '1'},
      json: async () => ({success: false, retryable: true})});
    await flushPromises();
    assert.equal(v.api.state.saving, true);
    assert.equal(v.api.state.uncertain, false);
    assert.equal(v.api.state.annotations.length, 1);
    v.timers.at(-1).callback(); await flushPromises();
    assert.equal(v.saves.at(-1).options.body, body);
  }
  v.saves.at(-1).resolve({ok: true, status: 200, json: async () => ({success: true, documentNo: 99})});
  await flushPromises();
  assert.equal(v.saves.length, 6);
  assert.equal(v.api.state.saved, true);
  assert.equal(v.api.state.saving, false);
});

for (const kind of ['network loss', 'invalid JSON', 'explicit ambiguous filing']) {
  test(`save never automatically retries ${kind}`, async () => {
    const v = viewer();
    v.api.state.annotations.push({type: 'highlight', page: 1, x: .1, y: .1, w: .2, h: .1});
    v.api.save(false); await flushPromises();
    if (kind === 'network loss') v.saves[0].reject(new Error('lost response'));
    else if (kind === 'invalid JSON') v.saves[0].resolve({status: 503, json: async () => {throw new Error('invalid JSON');}});
    else v.saves[0].resolve({ok: false, status: 503, json: async () => ({success: false, retryable: false})});
    await flushPromises();
    assert.equal(v.saves.length, 1);
    assert.equal(v.timers.length, 0);
    assert.equal(v.api.state.uncertain, true);
    assert.equal(v.api.state.annotations.length, 1);
  });
}

test('cancelling an unaccepted save wait leaves marks editable without retrying the POST', async () => {
  const v = viewer();
  v.api.state.annotations.push({type: 'highlight', page: 1, x: .1, y: .1, w: .2, h: .1});
  v.api.save(false); await flushPromises();
  v.saves[0].resolve({ok: false, status: 503, headers: {get: () => '1'},
    json: async () => ({success: false, retryable: true})});
  await flushPromises(); v.api.suspendDocumentRequests(); await flushPromises();
  assert.equal(v.api.state.saving, false);
  assert.equal(v.api.state.uncertain, false);
  assert.equal(v.api.state.annotations.length, 1);
  assert.equal(v.saves.length, 1);
});

test('steady-state waiting retains jitter instead of synchronizing every client at the delay cap', async () => {
  const low = viewer(0), high = viewer(1);
  const response = {headers: {get: () => '1'}};
  const waits = [low.api.waitForCapacity(response, 100, null, false), high.api.waitForCapacity(response, 100, null, false)];
  assert.ok(low.timers[0].delay < high.timers[0].delay);
  assert.ok(low.timers[0].delay >= 8000 && high.timers[0].delay <= 10000);
  low.timers[0].callback(); high.timers[0].callback();
  await Promise.all(waits);
});

test('scrolling away frees four obsolete OCR waits for current page images and does not poison their cache', async () => {
  const v = viewer(); v.api.state.tool = 'highlight';
  for (let page = 1; page <= 4; page++) {
    v.images[page - 1].top = 0;
    v.api.fetchWordBoxes(page);
  }
  await flushPromises();
  assert.equal(v.words.length, 4);
  for (const word of v.words) word.resolve({status: 503, headers: {get: () => '1'}, text: async () => ''});
  await flushPromises();
  assert.equal(v.api.active(), 4);
  v.scrollTo(12); v.api.cancelUnwantedCapacityWaits(); await flushPromises();
  v.frame(); await flushPromises();
  assert.ok(v.timers.every(timer => timer.cancelled));
  assert.ok(v.requests.some(request => request.page === 12));
  assert.equal(v.api.state.wordBoxes[1], undefined);
  assert.ok(v.words.some(request => /page=12$/.test(request.url)), 'current-page OCR remains eligible after old waits leave');
  const beforeReturn = v.words.length;
  v.scrollTo(1); v.api.fetchWordBoxes(1); await flushPromises();
  assert.equal(v.words.length, beforeReturn + 1);
  assert.match(v.words.at(-1).url, /page=1$/);
});

test('a blob decoding across pagehide is replaced so its late error cannot poison the resumed image', async () => {
  const v = viewer(); v.api.loadVisiblePages(); await flushPromises();
  const original = v.images[0];
  const replacement = {...original, dataset: {}, addEventListener() {}};
  original.cloneNode = () => {
    replacement.dataset = {...original.dataset};
    return replacement;
  };
  original.parentNode.replaceChild = (next, previous) => {
    assert.equal(previous, original);
    v.images[0] = next;
  };
  v.requests[0].resolve({ok: true, blob: async () => ({})}); await flushPromises();
  const objectUrl = original.dataset.objectUrl;
  v.api.suspendDocumentRequests();
  assert.ok(v.revoked.includes(objectUrl));
  assert.equal(v.api.active(), 1);
  assert.equal(v.images[0], replacement);
  // The old node's delayed event is now inert, even if a fresh request owns the replacement.
  v.api.resumeDocumentRequests(); v.frame(); await flushPromises();
  assert.equal(v.requests.length, 3, 'resumption starts a new image request without waiting for an old error event');
  v.api.pageImageFailed.call(original, new Error('revoked old blob'));
  assert.equal(v.api.active(), 2);
  assert.ok(replacement.dataset.loadingUrl);
  assert.notEqual(v.status.className, 'status error');
});
