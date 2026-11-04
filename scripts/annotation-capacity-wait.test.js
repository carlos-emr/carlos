/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/annotationCapacityWait.js'), 'utf8');
function page(url, attempt = 0, random = .5) {
  const math = Object.create(Math); math.random = () => random;
  const timers = [], events = {}, storage = new Map();
  let reloads = 0;
  const window = {location: {href: url, reload() {reloads++;}}, sessionStorage: {
    getItem: () => String(attempt), setItem: (key, value) => storage.set(key, value),
  }, setTimeout(callback, delay) {timers.push({callback, delay});return timers.length;},
  clearTimeout(id) {timers[id - 1].cancelled = true;}, addEventListener: (name, fn) => {events[name] = fn;}};
  vm.runInNewContext(source, {window, URL, Math: math});
  return {timers, events, storage, reloads: () => reloads};
}
test('authorized capacity view schedules only safe GET reloads with capped jittered backoff', () => {
  for (const attempt of [0, 4, 100]) {
    const value = page('https://local/carlos/documentManager/AnnotateDocument?docId=42', attempt);
    assert.equal(value.timers.length, 1);
    assert.ok(value.timers[0].delay >= 1000 && value.timers[0].delay <= 10000);
    value.timers[0].callback();
    assert.equal(value.reloads(), 1);
  }
});
test('leaving cancels automatic reload and returning from history restarts waiting', () => {
  const value = page('https://local/carlos/documentManager/AnnotateDocument?docId=42');
  value.events.pagehide();
  assert.equal(value.timers[0].cancelled, true);
  value.events.pageshow();
  assert.equal(value.timers.length, 2);
});
test('script cannot turn another action or malformed document into automatic reloads', () => {
  assert.equal(page('https://local/carlos/documentManager/SaveAnnotatedDocument?docId=42').timers.length, 0);
  assert.equal(page('https://local/carlos/documentManager/AnnotateDocument?docId=-1').timers.length, 0);
});


test('long-lived busy viewer reloads retain jitter at the maximum backoff', () => {
  const url = 'https://local/carlos/documentManager/AnnotateDocument?docId=42';
  const first = page(url, 100, 0), second = page(url, 100, 1);
  assert.ok(first.timers[0].delay < second.timers[0].delay);
  assert.ok(second.timers[0].delay <= 10000);
});
