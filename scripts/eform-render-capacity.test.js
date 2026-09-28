/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/eform-render-capacity.js'), 'utf8');
function page({ crypto = { getRandomValues(sample) { sample[0] = 1777; return sample; } }, now = 1499 } = {}) {
  const timers = [], events = {}, listeners = {}, posts = [];
  const status = {textContent: 'waiting'};
  const proceed = {disabled: false}, cancel = {disabled: false, addEventListener(name, cb) {listeners.cancel = cb;}};
  const fields = [['fdid', '42'], ['demographicNo', '123'], ['renderApproval', 'scoped-token'], ['csrf', 'csrf-token']];
  const form = {dataset: {cancelledMessage: 'cancelled', continuingMessage: 'continuing', uncertainMessage: 'uncertain'},
    addEventListener(name, cb) {listeners[name] = cb;},
    requestSubmit() {let prevented = false; listeners.submit({preventDefault() {prevented = true;}}); if (!prevented) posts.push(fields.map(pair => [...pair]));}};
  const nodes = {'eform-render-capacity': form, 'eform-render-capacity-status': status,
    'eform-render-capacity-cancel': cancel, 'eform-render-capacity-continue': proceed};
  vm.runInNewContext(source, {Date: {now: () => now}, document: {getElementById: id => nodes[id]}, window: {
    crypto,
    setTimeout(fn, delay) {timers.push({fn, delay});return timers.length;},
    clearTimeout(id) {timers[id - 1].cancelled = true;},
    addEventListener(name, cb) {events[name] = cb;}}});
  return {form, status, cancel, proceed, timers, events, listeners, posts, fields};
}
test('a confirmed busy page waits with jitter then posts its exact saved-form and CSRF fields once', () => {
  const p = page(); assert.equal(p.posts.length, 0);
  assert(p.timers[0].delay >= 2000 && p.timers[0].delay < 5000);
  p.timers[0].fn(); assert.deepEqual(p.posts, [p.fields]);
  assert.equal(p.status.textContent, 'continuing'); assert(p.proceed.disabled && p.cancel.disabled);
  p.form.requestSubmit(); p.timers[0].fn(); assert.equal(p.posts.length, 1);
});
test('cancelling waits preserves a manual continuation with the same saved-form fields', () => {
  const p = page(); p.listeners.cancel(); assert(p.timers[0].cancelled);
  assert.equal(p.status.textContent, 'cancelled'); assert.equal(p.posts.length, 0);
  assert(!p.proceed.disabled); p.form.requestSubmit(); assert.deepEqual(p.posts, [p.fields]);
});
test('manual continue cancels automatic submission and prevents double submit', () => {
  const p = page(); p.form.requestSubmit(); assert(p.timers[0].cancelled);
  p.timers[0].fn(); assert.equal(p.posts.length, 1);
});
test('leaving before submission cancels waiting; history restore does not submit automatically', () => {
  const p = page(); p.events.pagehide(); assert(p.timers[0].cancelled);
  p.events.pageshow({persisted: true}); assert.equal(p.status.textContent, 'cancelled');
  assert.equal(p.posts.length, 0); assert.equal(p.timers.length, 1);
});
test('history after submission retains uncertain outcome and permanently prevents duplicate acceptance', () => {
  const p = page(); p.form.requestSubmit(); p.events.pagehide(); p.events.pageshow({persisted: true});
  assert.equal(p.status.textContent, 'uncertain'); assert(p.proceed.disabled && p.cancel.disabled);
  p.form.requestSubmit(); assert.equal(p.posts.length, 1); assert.equal(p.timers.length, 1);
});
test('a fresh busy response can wait repeatedly without a global or cross-tab retry cap', () => {
  for (let n = 0; n < 8; n++) {const p = page(); p.timers[0].fn(); assert.equal(p.posts.length, 1);}
});
test('browser random bytes spread simultaneous pages while keeping the bounded retry interval', () => {
  const delays = [];
  for (const [word, expected] of [[0, 2000], [2999, 4999], [0xffffffff, 4295]]) {
    let calls = 0;
    const p = page({ now: 1000, crypto: {getRandomValues(sample) {
      calls++;
      assert(ArrayBuffer.isView(sample));
      assert.equal(sample.BYTES_PER_ELEMENT, 4);
      assert.equal(sample.length, 1);
      sample[0] = word;
      return sample;
    }}});
    assert.equal(calls, 1);
    assert.equal(p.timers[0].delay, expected);
    delays.push(p.timers[0].delay);
    p.timers[0].fn(); p.form.requestSubmit();
    assert.equal(p.posts.length, 1);
  }
  assert.equal(new Set(delays).size, 3);
});
for (const [label, crypto] of [
  ['missing crypto object', null],
  ['missing random-byte method', {}],
  ['random-byte failure', {getRandomValues() {throw new Error('browser entropy unavailable');}}]
]) {
  test(`${label} retains bounded waiting and duplicate-submit protection`, () => {
    const p = page({crypto, now: 4599});
    assert.equal(p.timers[0].delay, 3599);
    p.timers[0].fn(); p.form.requestSubmit();
    assert.deepEqual(p.posts, [p.fields]);
    assert(p.proceed.disabled && p.cancel.disabled);
  });
}
test('clock-phase fallback remains cancellable before any continuation is submitted', () => {
  const p = page({crypto: null, now: 6000});
  assert.equal(p.timers[0].delay, 2000);
  p.listeners.cancel();
  assert(p.timers[0].cancelled);
  assert.equal(p.posts.length, 0);
  assert.equal(p.status.textContent, 'cancelled');
  assert(!p.proceed.disabled);
});
