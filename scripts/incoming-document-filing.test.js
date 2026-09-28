/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/incomingDocumentFiling.js'), 'utf8');
const waitSource = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/incomingDocumentPageCountWait.js'), 'utf8');
const next = '/carlos/documentManager/ViewIncomingDocs?queueId=1&pdfDir=File&pdfNo=1&pdfPageNumber=1';
const flush = () => new Promise(resolve => setImmediate(resolve));
function response(status, data) { return { status, ok: status === 200, headers: {get: () => '1'}, json: async () => data }; }
const busy = () => response(503, {success: false, retryable: true, accepted: false});
const accepted = () => response(200, {success: true, accepted: true, documentNo: 42, nextUrl: next});
function viewer() {
  const handlers = {}, events = {}, timers = [], requests = [], navigations = [], attributes = {};
  const inputs = [{name: 'description', value: 'owned value', disabled: false}, {name: 'unchanged', value: 'x', disabled: true}];
  const fields = [['method', 'addIncomingDocument'], ['documentDescription', 'owned value'], ['flagproviders', '1'], ['flagproviders', '2'], ['CSRF-TOKEN', 'token']];
  const form = {action: 'https://local/carlos/documentManager/ManageDocument', dataset: {waitMessage: 'waiting', unconfirmedMessage: 'check chart', savingMessage: 'saving'}, elements: inputs,
    addEventListener(name, cb) {handlers[name] = cb;}, setAttribute(name, value) {attributes[name] = value;}};
  const status = {textContent: ''};
  const window = { location: {href: 'https://local/carlos/documentManager/ViewIncomingDocs', origin: 'https://local', pathname: '/carlos/documentManager/ViewIncomingDocs', assign(url) {navigations.push(url);}},
    addEventListener(name, cb) {events[name] = cb;}, setTimeout(cb, delay) {timers.push({cb, delay});return timers.length;}, clearTimeout(id) {timers[id - 1].cancelled = true;}};
  class FixtureFormData { constructor() {this.fields = fields.map(pair => [...pair]);} append(k, v) {this.fields.push([k, v]);} forEach(fn) {this.fields.forEach(([k, v]) => fn(v, k));} }
  const context = {URL, URLSearchParams, FormData: FixtureFormData, window,
    document: {querySelector: () => form, getElementById: () => status},
    fetch(url, options) {return new Promise((resolve, reject) => requests.push({url, options, resolve, reject}));}};
  vm.runInNewContext(source, context);
  return {form, fields, inputs, status, requests, timers, navigations, events, attributes, context,
    submit() {return handlers.submit({preventDefault() {}, defaultPrevented: false});}};
}

test('filing waits past multiple explicit pre-acceptance refusals with exactly frozen repeated fields', async () => {
  const v = viewer(); const done = v.submit();
  assert(v.inputs.every(input => input.disabled));
  assert.equal(v.status.textContent, 'saving');
  const body = v.requests[0].options.body;
  assert.deepEqual(new URLSearchParams(body).getAll('flagproviders'), ['1', '2']);
  v.fields[1][1] = 'later edit must not change snapshot';
  for (let i = 0; i < 6; i++) {
    v.requests[i].resolve(busy()); await flush();
    assert.equal(v.status.textContent, 'waiting');
    assert.equal(v.timers.length, i + 1);
    assert(v.timers[i].delay >= 1000 && v.timers[i].delay <= 10000);
    assert.equal(v.requests.length, i + 1);
    v.timers[i].cb(); await flush();
    assert.equal(v.requests[i + 1].options.body, body);
    assert.equal(v.requests[i + 1].options.headers['X-Carlos-Incoming-Filing'], 'bounded-v1');
  }
  v.requests.at(-1).resolve(accepted()); await done;
  assert.deepEqual(v.navigations, ['https://local' + next]);
});

for (const [label, value] of [
  ['missing acceptance guarantee', response(503, {success: false, retryable: true})],
  ['explicit accepted work', response(503, {success: false, retryable: true, accepted: true})],
  ['unretryable result', response(503, {success: false, retryable: false, accepted: false})],
  ['server error', response(500, {success: false})],
  ['malformed response', {status: 200, ok: true, json: async () => {throw new Error('not JSON');}}],
  ['malformed success ID', response(200, {success: true, accepted: true, documentNo: '42', nextUrl: next})],
  ['external success URL', response(200, {success: true, accepted: true, documentNo: 42, nextUrl: 'https://other/steal'})],
  ['mutation success URL', response(200, {success: true, accepted: true, documentNo: 42, nextUrl: next + '&pdfAction=DeletePage'})],
]) {
  test(`filing preserves and locks input after ${label}, without replay`, async () => {
    const v = viewer(); const done = v.submit(); v.requests[0].resolve(value); await done;
    assert.equal(v.status.textContent, 'check chart');
    assert.equal(v.inputs[0].value, 'owned value');
    assert(v.inputs.every(input => input.disabled));
    assert.equal(v.timers.length, 0);
    await v.submit();
    assert.equal(v.requests.length, 1);
    assert.equal(v.navigations.length, 0);
  });
}

test('network rejection locks uncertain filing and never replays', async () => {
  const v = viewer(); const done = v.submit(); v.requests[0].reject(new Error('connection lost')); await done;
  await v.submit(); assert.equal(v.requests.length, 1); assert.equal(v.status.textContent, 'check chart');
});

test('leaving during a confirmed capacity wait cancels timer and permits safe later submission', async () => {
  const v = viewer(); const done = v.submit(); v.requests[0].resolve(busy()); await flush();
  v.events.pagehide(); await done;
  assert(v.timers[0].cancelled);
  assert.equal(v.inputs[0].disabled, false);
  assert.equal(v.inputs[1].disabled, true);
  assert.equal(v.status.textContent, '');
  assert.equal(v.requests.length, 1);
  v.events.pageshow();
  const retry = v.submit(); v.requests[1].resolve(accepted()); await retry;
  assert.equal(v.navigations.length, 1);
});

test('leaving during an in-flight POST does not free inputs or launch a second POST', async () => {
  const v = viewer(); const done = v.submit(); v.events.pagehide();
  assert(v.inputs.every(input => input.disabled));
  await v.submit(); assert.equal(v.requests.length, 1);
  v.requests[0].resolve(accepted()); await done;
  assert.equal(v.navigations.length, 0);
  v.events.pageshow(); assert.equal(v.navigations.length, 1);
});

test('double submit and duplicate script installation do not issue another request', async () => {
  const v = viewer(); const done = v.submit();
  vm.runInNewContext(source, v.context);
  await v.submit(); assert.equal(v.requests.length, 1);
  v.requests[0].resolve(accepted()); await done;
});

function capacityPage(target = next, auto = 'true') {
  const timers = [], events = {}, navigations = [];
  const location = {href: 'https://local/carlos/documentManager/ManageDocument', pathname: '/carlos/documentManager/ManageDocument', origin: 'https://local',
    reload() {throw new Error('POST must never reload');}, replace(url) {navigations.push(url);}};
  vm.runInNewContext(waitSource, {URL, window: {location, sessionStorage: {getItem: () => '9', setItem() {}},
    addEventListener(name, cb) {events[name] = cb;}, setTimeout(cb, delay) {timers.push({cb, delay});return timers.length;}, clearTimeout(id) {timers[id - 1].cancelled = true;}},
    document: {getElementById: () => ({getAttribute(name) {return name === 'data-auto-retry' ? auto : target;}})}});
  return {timers, events, navigations};
}
test('page-count wait after POST explicitly navigates to GET and cancels across history', () => {
  const v = capacityPage(); assert.equal(v.timers.length, 1); assert(v.timers[0].delay <= 10000);
  v.timers[0].cb(); assert.deepEqual(v.navigations, ['https://local' + next]);
  v.events.pagehide(); assert(v.timers[0].cancelled);
  v.events.pageshow(); assert.equal(v.timers.length, 2);
  v.events.pageshow(); assert.equal(v.timers.length, 2);
});
test('page-count wait does not auto-navigate past mutation errors or to unsafe targets', () => {
  assert.equal(capacityPage(next, 'false').timers.length, 0);
  for (const target of ['https://other' + next, next + '&pdfAction=DeletePage', next.replace('ViewIncomingDocs', 'ManageDocument'), next.replace('pdfNo=1', 'pdfNo=-1')]) {
    assert.equal(capacityPage(target).timers.length, 0);
  }
});

test('explicit pre-mutation parse refusal shows its error and permits only an operator retry', async () => {
  const v = viewer(); const done = v.submit();
  v.requests[0].resolve(response(422, {success: false, retryable: false, accepted: false, error: 'Review document'}));
  await done;
  assert.equal(v.status.textContent, 'Review document');
  assert.equal(v.inputs[0].disabled, false);
  assert.equal(v.inputs[1].disabled, true);
  assert.equal(v.timers.length, 0);
  const retry = v.submit(); v.requests[1].resolve(accepted()); await retry;
  assert.equal(v.navigations.length, 1);
});

test('page-count wait rejects duplicate query parameters', () => {
  assert.equal(capacityPage(next + '&queueId=2').timers.length, 0);
});

test('successful navigation preserves validated recent patient and entry mode', async () => {
  const v = viewer(); const done = v.submit();
  v.requests[0].resolve(response(200, {success: true, accepted: true, documentNo: 42,
    nextUrl: next + '&lastdemographic_no=123&entryMode=Fast'}));
  await done;
  assert.deepEqual(v.navigations, ['https://local' + next + '&lastdemographic_no=123&entryMode=Fast']);
});

test('navigation rejects invalid optional patient/mode and nonnumeric queues', async () => {
  for (const url of [next + '&lastdemographic_no=0', next + '&lastdemographic_no=2147483648',
    next + '&entryMode=Other', next + '&entryMode=Fast&entryMode=Normal', next.replace('queueId=1', 'queueId=letters')]) {
    const v = viewer(); const done = v.submit();
    v.requests[0].resolve(response(200, {success: true, accepted: true, documentNo: 42, nextUrl: url}));
    await done;
    assert.equal(v.navigations.length, 0);
    assert.equal(v.status.textContent, 'check chart');
  }
});
