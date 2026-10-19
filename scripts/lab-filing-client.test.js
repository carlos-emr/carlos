/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/labFiling.js'), 'utf8');
const index = fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/oscarMDSIndex.js'), 'utf8');

function dialogFixture() {
  let ready, submitHandler, closed = 0, reloaded = 0, sent = 0;
  const peer = {value: '100002', checked: true, disabled: false};
  const comment = {value: ''};
  const error = {textContent: ''};
  const submit = {disabled: false};
  const commentBox = {hidden: false};
  const details = {open: false};
  const form = {elements: {comment}, addEventListener: (name, callback) => { submitHandler = callback; }};
  const ackForm = {id: 'acknowledgeForm_30', elements: {providerNo: {value: '100001'}, comment: {value: ''}}};
  const existing = {textContent: "Existing O'Brien < 3.5 & Hb > 100"};
  const dialog = {
    dataset: {labFiling: '30', endpoint: '/FileLabsOnBehalf', errorMessage: 'Refresh before retrying', selectMessage: 'Select a provider'},
    showModal() {}, close() { closed++; },
    querySelector(selector) {
      return {'form': form, '[data-filing-comment]': commentBox, 'details': details,
        '[data-filing-error]': error, '[data-filing-cancel]': {addEventListener() {}},
        '[type="submit"]': submit, 'textarea[name="comment"]': comment}[selector];
    },
    querySelectorAll(selector) { return selector.includes(':checked') ? (peer.checked ? [peer] : []) : [peer]; },
  };
  const window = {location: {reload() { reloaded++; }}};
  const document = {
    forms: {acknowledgeForm_30: ackForm},
    getElementById(id) { return id === 'labFiling_30' ? dialog : id === '100001_30commentText' ? existing : null; },
    addEventListener(name, callback) { ready = callback; },
    querySelectorAll(selector) { return selector.startsWith('dialog') ? [dialog] : [{value: 'csrf'}]; },
  };
  vm.runInNewContext(source, {window, document, URLSearchParams,
    fetch: async () => { sent++; return {ok: true, json: async () => ({success: true})}; }});
  ready();
  return {window, dialog, peer, comment, error, submit, ackForm, existing,
    open: () => window.openLabFilingDialog('30', true),
    submitForm: () => submitHandler({preventDefault() {}}),
    counts: () => ({closed, reloaded, sent})};
}

test('combined acknowledgement shows the current decoded clinical comment', () => {
  const fixture = dialogFixture();
  fixture.open();
  assert.equal(fixture.comment.value, fixture.existing.textContent);
});

test('a failed acknowledgement leaves the dialog open after confirmed colleague filing and allows an ack-only retry', async () => {
  const f = dialogFixture();
  f.open();
  f.window.handleLab = async (form, lab, action, before) => {
    await before();
    assert.equal(f.counts().closed, 0, 'dialog closed before acknowledgement');
    throw new Error('synthetic acknowledgement failure');
  };
  await f.submitForm();
  assert.deepEqual(f.counts(), {closed: 0, reloaded: 0, sent: 1});
  assert.equal(f.error.textContent, 'Refresh before retrying');
  assert.equal(f.peer.disabled, true);
  assert.equal(f.peer.checked, false);
  assert.equal(f.submit.disabled, false);
  f.window.handleLab = async (form, lab, action, before) => { await before(); return true; };
  await f.submitForm();
  assert.deepEqual(f.counts(), {closed: 1, reloaded: 0, sent: 1}, 'retry must not file the colleague twice');
});

test('cancelling acknowledgement confirmation keeps the combined dialog and selections', async () => {
  const f = dialogFixture();
  f.open();
  f.window.handleLab = async () => undefined;
  await f.submitForm();
  assert.deepEqual(f.counts(), {closed: 0, reloaded: 0, sent: 0});
  assert.equal(f.peer.checked, true);
});

function acknowledgementFixture(response, failure) {
  let notified = 0;
  const jQuery = () => ({length: 0});
  jQuery.post = () => failure ? Promise.reject(failure) : Promise.resolve(response);
  const context = vm.createContext({jQuery, contextpath: '', alert() {},
    document: {getElementById: () => ({})}, serializeFormToObject: () => ({labType: 'HL7'}),
    window: {}, _in_window: false, labDocumentRows: () => ({slideUp() { notified++; }}),
    updateGlobalDataAndSideNav() {}});
  vm.runInContext(index.slice(index.indexOf('function serverClearedCount('), index.indexOf('function fileDoc(')), context);
  return {run: () => context.updateStatus('acknowledgeForm_30', true), notified: () => notified};
}

test('acknowledgement network errors reject without removing the inbox item', async () => {
  const f = acknowledgementFixture(null, new Error('synthetic network failure'));
  await assert.rejects(f.run(), /network failure/);
  assert.equal(f.notified(), 0);
});

test('HTTP-200 failure pages and unconfirmed responses do not count as acknowledgements', async () => {
  for (const response of ['failure', '<html>failure</html>', {}, {clearedCount: -1}, {clearedCount: '1oops'}]) {
    const f = acknowledgementFixture(response);
    await assert.rejects(f.run(), /not confirmed/);
    assert.equal(f.notified(), 0);
  }
});

test('a confirmed idempotent acknowledgement with zero cleared rows succeeds', async () => {
  const f = acknowledgementFixture('{"clearedCount":0}');
  assert.equal(await f.run(), true);
  assert.equal(f.notified(), 1);
});
