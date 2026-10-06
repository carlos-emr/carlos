/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const start = source.indexOf('    function saveNoteAjax(method, chain) {');
const end = source.indexOf('    function savePage(method, chain) {', start);
assert.ok(start >= 0 && end > start);

function harness() {
  const requests = [], alerts = [], updates = [];
  let restored = 0, timers = 0;
  const fields = {observationDate: {value: ''}, notCPP: {update: text => updates.push(text)}};
  const form = {method: {}, ajax: {}, chain: {}, includeIssue: {}};
  const context = {
    lostNoteLock: false, noteLockLostError: 'note lock lost', savingNoteError: 'save failed', sessionExpiredError: 'session expired',
    caseNote: 'editor', caisiEnabled: false, ctx: '/carlos', fullChart: 'true',
    document: {forms: {caseManagementEntryForm: form}},
    $F: () => 'recoverable clinical text', $: id => fields[id],
    alert: text => alerts.push(text), deferNoteSaveUntilIssues: () => false,
    clearAutoSaveTimer() {}, setTimer: () => timers++,
    beginNoteSwitchSave: () => () => restored++,
    Form: {serialize: () => 'method=save'},
    CarlosAjax: {request: (url, options) => requests.push(options)},
    DOMParser: class { parseFromString(text) { return {getElementById: id => text === 'valid note pane' && id === 'encMainDiv' ? {} : null}; } },
  };
  vm.createContext(context); vm.runInContext(source.slice(start, end), context);
  return {context, requests, alerts, updates, get restored() {return restored;}, get timers() {return timers;}};
}

test('a known lost lock warns without submitting or replacing the editor', () => {
  const h = harness(); h.context.lostNoteLock = true;
  h.context.saveNoteAjax('save', '');
  assert.deepEqual(h.alerts, ['note lock lost']); assert.equal(h.requests.length, 0); assert.equal(h.updates.length, 0);
});

for (const status of [409, 403, 500]) {
  test(`HTTP ${status} preserves the editor and restores controls`, () => {
    const h = harness(); h.context.saveNoteAjax('save', '');
    assert.equal(h.updates.length, 0, 'pending save must keep typed text');
    h.requests[0].onFailure({status, responseText: 'refusal page'});
    h.requests[0].onComplete();
    assert.equal(h.updates.length, 0); assert.equal(h.restored, 1); assert.equal(h.alerts.length, 1);
    assert.equal(h.context.lostNoteLock, status === 409); assert.equal(h.timers, status === 409 ? 0 : 1);
  });
}

test('a login or error page returned with HTTP 200 cannot replace the editor', () => {
  const h = harness(); h.context.saveNoteAjax('save', '');
  h.requests[0].onSuccess({responseText: 'login page'}); h.requests[0].onComplete();
  assert.equal(h.updates.length, 0); assert.deepEqual(h.alerts, ['save failed']); assert.equal(h.restored, 1);
});

test('a successful save installs the returned notes pane', () => {
  const h = harness(); h.context.saveNoteAjax('save', '');
  h.requests[0].onSuccess({responseText: 'valid note pane'}); h.requests[0].onComplete();
  assert.deepEqual(h.updates, ['valid note pane']); assert.deepEqual(h.alerts, []); assert.equal(h.restored, 1); assert.equal(h.timers, 0);
});
