/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/documentImageLoader.js'), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
const url = (page, doc = 42) => `https://example.test/carlos/documentManager/ManageDocument?method=viewDocPage&doc_no=${doc}&curPage=${page}`;

function viewer(count = 1) {
  const nodes = [], frames = [], requests = [], decoders = [], timers = [], revoked = [], listeners = new Map();
  let nextBlob = 0, random = .5, observer, throwFetch = false;
  function node(tag = 'IMG') {
    const attributes = new Map();
    const n = {tagName: tag, isConnected: true, top: 10, style: {}, textContent: '', children: [],
      setAttribute(k, v) {attributes.set(k, String(v));}, getAttribute: k => attributes.get(k) ?? null,
      removeAttribute: k => attributes.delete(k),
      getBoundingClientRect() {return {top: this.top, bottom: this.top + 100, left: 0, right: 100};},
      getClientRects() {return this.isConnected ? [{}] : [];},
      appendChild(child) {child.parentNode = this; this.children.push(child);},
      remove() {this.isConnected = false; this.parentNode = null;},
    };
    Object.defineProperty(n, 'src', {get: () => n.getAttribute('src'), set: value => n.setAttribute('src', value)});
    nodes.push(n); return n;
  }
  const images = Array.from({length: count}, (_, i) => {
    const image = node(); image.setAttribute('data-document-image-src', url(i + 1));
    node('DIV').appendChild(image); return image;
  });
  const math = Object.create(Math); math.random = () => random;
  const TestURL = class extends URL {};
  TestURL.createObjectURL = () => `blob:test-${++nextBlob}`;
  TestURL.revokeObjectURL = value => revoked.push(value);
  const context = {URL: TestURL, Math: math, Image: function () {
    const decoder = {removeAttribute() {}}; decoders.push(decoder); return decoder;
  }, MutationObserver: function (callback) {observer = callback; this.observe = () => {};},
  document: {documentElement: {}, addEventListener() {}, createElement: tag => node(tag.toUpperCase()),
    querySelectorAll(selector) {
      return nodes.filter(n => n.isConnected && (selector.startsWith('img') ? n.tagName === 'IMG' && n.getAttribute('data-document-image-src') : n.getAttribute('data-document-image-blob')));
    }},
  window: {location: {href: 'https://example.test/carlos/viewer', origin: 'https://example.test'}, innerHeight: 800, innerWidth: 1000,
    requestAnimationFrame: fn => frames.push(fn),
    setTimeout: (callback, delay) => {timers.push({callback, delay}); return timers.length;},
    clearTimeout: id => {timers[id - 1].cancelled = true;},
    addEventListener: (name, fn) => listeners.set(name, fn)},
  fetch(requestUrl, options) {
    if (throwFetch) {throwFetch = false; throw new Error('dispatch failed');}
    return new Promise((resolve, reject) => requests.push({url: requestUrl, options, resolve, reject}));
  }};
  vm.createContext(context); vm.runInContext(source, context);
  return {api: context.window.CarlosDocumentImages, images, frames, requests, decoders, timers, revoked, node,
    singleton() {vm.runInContext(source, context);}, random(value) {random = value;},
    failDispatch() {throwFetch = true;},
    frame() {frames.splice(0).forEach(fn => fn());}, mutate() {observer();},
    event(name, details = {}) {listeners.get(name)(details);},
    async success(index = 0) {
      requests[index].resolve({ok: true, status: 200, headers: {get: () => 'image/png'}, blob: async () => ({})});
      await flush(); decoders.at(-1).onload(); await flush();
    },
    busy(index) {requests[index].resolve({ok: false, status: 503, headers: {get: () => '1'}, text: async () => ''});},
    state(index = 0) {return images[index].getAttribute('data-document-image-state');},
  };
}

test('initial split pages use at most four GETs and retain admission through response body and image decoding', async () => {
  const v = viewer(12); v.frame();
  assert.equal(v.requests.length, 4);
  let body; v.requests[0].resolve({ok: true, status: 200, headers: {get: () => 'image/png'}, blob: () => new Promise(resolve => {body = resolve;})});
  await flush(); v.frame(); assert.equal(v.requests.length, 4);
  body({}); await flush(); v.frame(); assert.equal(v.requests.length, 4);
  v.decoders[0].onload(); await flush(); v.frame();
  assert.equal(v.requests.length, 5);
  assert.equal(v.images[0].src, 'blob:test-1');
  assert.equal(v.state(), 'loaded');
  assert.equal(v.requests[0].options.credentials, 'same-origin');
  assert.equal(v.requests[0].options.redirect, 'error');
  assert.equal(v.requests[0].options.method, undefined, 'only the default GET is ever issued');
});

test('capacity waits through more than three busy responses with capped jitter and eventually displays the image', async () => {
  const v = viewer(); v.frame();
  const lateDelays = [];
  for (let i = 0; i < 8; i++) {
    v.random(i % 2 ? 1 : 0); v.busy(i); await flush();
    assert.equal(v.state(), 'waiting');
    assert.match(v.images[0].parentNode.children.at(-1).textContent, /Waiting for document capacity/);
    const timer = v.timers.at(-1);
    assert.ok(timer.delay >= 1000 && timer.delay <= 10000);
    if (i >= 5) lateDelays.push(timer.delay);
    timer.callback(); await flush();
  }
  assert.ok(new Set(lateDelays).size > 1, 'steady-state clients must not synchronize at the cap');
  await v.success(8);
  assert.equal(v.state(), 'loaded'); assert.equal(v.requests.length, 9);
  assert.deepEqual(v.revoked, [], 'decoded image remains available to split zoom/rotation');
});

test('pagination keeps accepted work counted and suppresses stale pixels and old decode events', async () => {
  const v = viewer(5); v.frame();
  v.api.load(v.images[0], url(9)); v.frame(); assert.equal(v.requests.length, 4);
  v.requests[0].resolve({ok: true, status: 200, headers: {get: () => 'image/png'}, blob: async () => ({})});
  await flush(); v.frame();
  assert.equal(v.images[0].src, null);
  assert.equal(v.decoders.length, 0, 'obsolete body must never decode into the current image');
  assert.equal(v.requests[4].url, url(9));
  await v.success(4);
  const oldEvent = v.decoders.at(-1).onload;
  assert.equal(oldEvent, null, 'completed decoder has no late event handlers');
  assert.equal(v.api.source(v.images[0]), url(9));
});

test('scrolling cancels obsolete capacity waits and gives visible pages the released slots', async () => {
  const v = viewer(5); v.frame();
  for (let i = 0; i < 4; i++) v.busy(i);
  await flush(); v.images.slice(0, 4).forEach(image => {image.top = -1000;});
  v.event('scroll'); v.frame(); await flush(); v.frame();
  assert.equal(v.requests.length, 5);
  assert.equal(v.requests[4].url, url(5));
  assert.ok(v.timers.every(timer => timer.cancelled));
  assert.equal(v.state(), 'waiting', 'offscreen cancellation must not poison a future visit');
});

test('removing a patient fragment does not free an accepted server slot early or display its returned pixels', async () => {
  const v = viewer(5); v.frame(); v.images[0].remove(); v.mutate(); v.frame();
  assert.equal(v.requests.length, 4);
  v.requests[0].resolve({ok: true, status: 200, headers: {get: () => 'image/png'}, blob: async () => ({})});
  await flush(); v.frame(); assert.equal(v.requests.length, 5);
  assert.equal(v.decoders.length, 0);
});

for (const status of [403, 404, 500]) test(`genuine ${status} is visible and never retried automatically`, async () => {
  const v = viewer(); v.frame();
  v.requests[0].resolve({ok: false, status, headers: {get: () => 'text/html'}, text: async () => 'unavailable'});
  await flush(); v.frame();
  assert.equal(v.state(), 'failed'); assert.equal(v.requests.length, 1); assert.equal(v.timers.length, 0);
  assert.match(v.images[0].parentNode.children.at(-1).textContent, /could not be loaded/);
});

test('a login HTML response or synchronous transport exception fails visibly without leaking a slot', async () => {
  const v = viewer(6); v.failDispatch(); v.frame(); await flush(); v.frame();
  assert.equal(v.state(), 'failed');
  assert.equal(v.requests.length, 4, 'failed synchronous dispatch must allow the next queued image');
  v.requests[0].resolve({ok: true, status: 200, headers: {get: () => 'text/html'}, text: async () => '<form>login</form>'});
  await flush(); v.frame(); assert.equal(v.state(1), 'failed'); assert.equal(v.requests.length, 5);
});

test('decoded blobs survive cloning and rotation, and revoke only after their last connected owner disappears', async () => {
  const v = viewer(); v.frame(); await v.success();
  const original = v.images[0]; const copy = v.node();
  copy.src = original.src; copy.setAttribute('data-document-image-src', url(1));
  v.api.retain(original, copy);
  assert.equal(copy.getAttribute('data-document-image-src'), null);
  const canvas = v.node('CANVAS'); v.api.retain(original, canvas);
  v.api.load(original, url(2)); v.frame(); assert.equal(v.revoked.length, 0);
  copy.remove(); v.mutate(); v.frame(); assert.equal(v.revoked.length, 0);
  canvas.remove(); v.mutate(); v.frame(); assert.deepEqual(v.revoked, ['blob:test-1']);
  assert.equal(v.requests.length, 2, 'clones and rotated canvases must never trigger another GET');
});

test('pagehide cancels decode without waiting for an error event and BFCache resume safely requeues', async () => {
  const v = viewer(); v.frame();
  v.requests[0].resolve({ok: true, status: 200, headers: {get: () => 'image/png'}, blob: async () => ({})});
  await flush(); const decoder = v.decoders[0]; const lateLoad = decoder.onload;
  v.event('pagehide', {persisted: true}); await flush(); v.frame();
  assert.equal(v.requests.length, 1); assert.equal(v.images[0].src, null);
  assert.deepEqual(v.revoked, ['blob:test-1']);
  v.event('pageshow'); v.frame(); assert.equal(v.requests.length, 2);
  lateLoad(); await flush(); assert.equal(v.images[0].src, null, 'old decode completion cannot draw into the restored page');
  await v.success(1); assert.equal(v.state(), 'loaded');
});

test('pagehide stops all capacity retry timers; duplicate fragment script installation adds no second loader', async () => {
  const v = viewer(); v.frame(); v.busy(0); await flush();
  v.singleton(); v.frame(); assert.equal(v.requests.length, 1);
  v.event('pagehide', {persisted: true}); await flush(); v.frame();
  assert.equal(v.timers[0].cancelled, true); assert.equal(v.requests.length, 1);
  v.event('pageshow'); v.frame(); assert.equal(v.requests.length, 2);
});

test('cross-origin and mutation URLs cannot be used as document image requests', () => {
  const v = viewer();
  v.api.load(v.images[0], 'https://elsewhere.test/documentManager/ManageDocument?method=showPage&doc_no=42');
  v.frame(); assert.equal(v.requests.length, 0); assert.equal(v.state(), 'failed');
  v.api.load(v.images[0], 'https://example.test/carlos/documentManager/ManageDocument?method=rotate90&doc_no=42');
  v.frame(); assert.equal(v.requests.length, 0);
});


test('a removed and reinserted element cannot receive pixels from its discarded record', async () => {
  const v = viewer(); v.frame();
  const image = v.images[0]; const parent = image.parentNode;
  image.remove(); v.mutate(); v.frame();
  image.isConnected = true; parent.appendChild(image);
  image.setAttribute('data-document-image-src', url(1, 99));
  v.mutate(); v.frame();
  assert.equal(v.requests.length, 2);
  v.requests[0].resolve({ok: true, status: 200, headers: {get: () => 'image/png'}, blob: async () => ({})});
  await flush(); assert.equal(v.decoders.length, 0);
  await v.success(1);
  assert.equal(v.api.source(image), url(1, 99));
  assert.equal(v.state(), 'loaded');
});

test('corrupt image decoding is visible, releases its blob and admits the next page without retrying', async () => {
  const v = viewer(5); v.frame();
  v.requests[0].resolve({ok: true, status: 200, headers: {get: () => 'image/png'}, blob: async () => ({})});
  await flush(); v.decoders[0].onerror(); await flush(); v.frame();
  assert.equal(v.state(), 'failed'); assert.deepEqual(v.revoked, ['blob:test-1']);
  assert.equal(v.requests.length, 5); assert.equal(v.timers.length, 0);
});

test('a page change during decode keeps its permit and never displays stale pixels', async () => {
  const v = viewer(5); v.frame();
  v.requests[0].resolve({ok: true, status: 200, headers: {get: () => 'image/png'}, blob: async () => ({})});
  await flush(); v.api.load(v.images[0], url(7)); v.frame();
  assert.equal(v.requests.length, 4);
  v.decoders[0].onload(); await flush(); v.frame();
  assert.equal(v.images[0].src, null); assert.deepEqual(v.revoked, ['blob:test-1']);
  assert.equal(v.requests[4].url, url(7));
  await v.success(4); assert.equal(v.state(), 'loaded');
});

test('BFCache keeps already decoded split resources alive and restores without refetching', async () => {
  const v = viewer(); v.frame(); await v.success();
  v.event('pagehide', {persisted: true}); v.frame();
  assert.deepEqual(v.revoked, []); assert.equal(v.images[0].src, 'blob:test-1');
  v.event('pageshow'); v.frame(); assert.equal(v.requests.length, 1);
  v.event('pagehide', {persisted: false}); assert.deepEqual(v.revoked, ['blob:test-1']);
});
