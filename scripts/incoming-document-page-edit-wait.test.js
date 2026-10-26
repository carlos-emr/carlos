/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../src/main/webapp/js/incomingDocumentPageEditWait.js'), 'utf8');
function page(options = {}) {
  const fields = options.fields || [['sourceRevision', 'a'.repeat(64)], ['CSRF-TOKEN', 'original-token'], ['pdfAction', 'ExtractPagePDF'], ['pdfExtractPageNumber', '2,4'], ['pdfName', 'owned.pdf']];
  const original = fields.map(([name, value]) => ({name, value, disabled: false}));
  const form = {dataset: {accepted: options.accepted || 'false'}, method: 'post', action: options.action || 'https://local/carlos/documentManager/ViewIncomingDocs', elements: original,
    appendChild(element) { this.elements.push(element); }};
  const events = {}, cancelEvents = {}, timers = [], posts = [], destinations = [];
  const refresh = {addEventListener(name, callback) {cancelEvents[name] = callback;}};
  class FormData { constructor() {this.fields = fields.map(pair => [...pair]);} getAll(name) {return this.fields.filter(pair => pair[0] === name).map(pair => pair[1]);} forEach(fn) {this.fields.forEach(([name, value]) => fn(value, name));} }
  function HTMLFormElement() {}
  HTMLFormElement.prototype.submit = function () { destinations.push({action: this.action, method: this.method, target: this.target}); posts.push(this.elements.filter(element => !element.disabled).map(({name, value}) => [name, value])); };
  const context = {URL, FormData, HTMLFormElement, document: {getElementById: id => id === 'incoming-page-edit-wait' ? form : id === 'incoming-page-edit-cancel' ? refresh : null, createElement: () => ({})},
    window: {location: {href: form.action, origin: 'https://local', pathname: '/carlos/documentManager/ViewIncomingDocs'},
      setTimeout(callback, delay) {timers.push({callback, delay}); return timers.length;}, clearTimeout(id) {timers[id - 1].cancelled = true;}, addEventListener(name, callback) {events[name] = callback;}}};
  vm.runInNewContext(source, context);
  return {fields, form, events, cancelEvents, timers, posts, destinations, context};
}

test('one confirmed admission refusal posts exactly the frozen page action, revision and CSRF once', () => {
  const p = page(); const expected = p.fields.map(pair => [...pair]);
  assert.equal(p.timers.length, 1); assert(p.timers[0].delay >= 1000 && p.timers[0].delay <= 1250);
  p.form.elements[0].value = 'b'.repeat(64);
  p.fields[1][1] = 'new-token';
  p.timers[0].callback(); p.timers[0].callback();
  assert.deepEqual(p.posts, [expected]);
  vm.runInNewContext(source, p.context);
  assert.equal(p.timers.length, 1);
});

test('pagehide cancels pending POST and restored history cannot replay it', () => {
  const p = page(); p.events.pagehide(); p.timers[0].callback();
  assert(p.timers[0].cancelled); assert.equal(p.posts.length, 0);
  vm.runInNewContext(source, p.context); assert.equal(p.timers.length, 1);
});

for (const [name, options] of [
  ['unknown acceptance', {accepted: 'true'}],
  ['external endpoint', {action: 'https://external/carlos/documentManager/ViewIncomingDocs'}],
  ['wrong endpoint', {action: 'https://local/carlos/documentManager/ManageDocument'}],
  ['query endpoint', {action: 'https://local/carlos/documentManager/ViewIncomingDocs?pdfAction=DeletePDF'}],
  ['missing revision', {fields: [['CSRF-TOKEN', 'token'], ['pdfAction', 'DeletePDF']]}],
  ['duplicate revision', {fields: [['sourceRevision', 'a'.repeat(64)], ['sourceRevision', 'b'.repeat(64)], ['CSRF-TOKEN', 'token'], ['pdfAction', 'DeletePDF']]}],
  ['missing CSRF', {fields: [['sourceRevision', 'a'.repeat(64)], ['pdfAction', 'DeletePDF']]}],
  ['unknown action', {fields: [['sourceRevision', 'a'.repeat(64)], ['CSRF-TOKEN', 'token'], ['pdfAction', 'file']]}],
]) test(`${name} never schedules a POST`, () => { const p = page(options); assert.equal(p.timers.length, 0); assert.equal(p.posts.length, 0); });


test('live endpoint changes cannot redirect frozen clinical fields', () => {
  const p = page();
  p.form.action = 'https://external/stolen'; p.form.method = 'get'; p.form.target = 'another-window';
  p.timers[0].callback();
  assert.deepEqual(p.destinations, [{action: 'https://local/carlos/documentManager/ViewIncomingDocs', method: 'post', target: '_self'}]);
});

test('crypto jitter stays within its fixed bound', () => {
  const p = page();
  p.form.dataset.bound = '';
  p.context.window.crypto = {getRandomValues(values) {values[0] = 0xffffffff; return values;}};
  vm.runInNewContext(source, p.context);
  assert.equal(p.timers[1].delay, 1000 + 0xffffffff % 251);
});


test('clicking manual refresh cancels immediately without suppressing its read-only navigation', () => {
  const p = page();
  let prevented = false;
  p.cancelEvents.click({preventDefault() {prevented = true;}});
  // A slow GET has not triggered pagehide yet, and an already-queued timer may still run.
  p.timers[0].callback();
  assert(p.timers[0].cancelled);
  assert.equal(p.posts.length, 0);
  assert.equal(prevented, false);
  vm.runInNewContext(source, p.context);
  assert.equal(p.timers.length, 1);
});
