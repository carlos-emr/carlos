/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/eform-render-capacity.js'), 'utf8');
function page() {
  const timers = [], events = {}, listeners = {}, posts = [];
  const status = {textContent: 'waiting'};
  const proceed = {disabled: false}, cancel = {disabled: false, addEventListener(name, cb) {listeners.cancel = cb;}};
  const fields = [['fdid', '42'], ['demographicNo', '123'], ['renderApproval', 'scoped-token'], ['csrf', 'csrf-token']];
  const form = {dataset: {cancelledMessage: 'cancelled', continuingMessage: 'continuing', uncertainMessage: 'uncertain'},
    addEventListener(name, cb) {listeners[name] = cb;},
    requestSubmit() {let prevented = false; listeners.submit({preventDefault() {prevented = true;}}); if (!prevented) posts.push(fields.map(pair => [...pair]));}};
  const nodes = {'eform-render-capacity': form, 'eform-render-capacity-status': status,
    'eform-render-capacity-cancel': cancel, 'eform-render-capacity-continue': proceed};
  vm.runInNewContext(source, {document: {getElementById: id => nodes[id]}, window: {
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
