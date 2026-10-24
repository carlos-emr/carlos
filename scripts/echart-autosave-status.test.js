/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const modernSource = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const legacySource = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/CaseManagementEntry.jsp'), 'utf8');
const labels = { draftSavingLabel: 'Saving draft...', draftSaveFailedLabel: 'Draft failed; retrying.',
  draftSaveConflictLabel: 'Reopen this chart.', draftSavedLabel: 'Draft saved' };
const renderLabels = source => source.replace(/\$\{carlos:forJavaScriptBlock\((\w+)\)\}/g,
  (_, key) => { assert.ok(labels[key]); return labels[key]; });

function statusElement() {
  return { textContent: 'Draft saved previously', attributes: {},
    setAttribute(name, value) { this.attributes[name] = value; } };
}

function modern() {
  const status = statusElement();
  const editor = { value: 'Unsaved H&P + clinical text' };
  const requests = [];
  const timers = [];
  const elements = { editor, autosaveTime: status, observationDate: { value: 'today' } };
  const context = {
    caseNote: 'editor', demographicNo: '101', case_program_id: '11',
    document: { getElementById: id => elements[id], forms: { caseManagementEntryForm: { noteId: { value: '0' } } } },
    $: id => elements[id], $F: id => elements[id].value,
    chartActionUrl: () => '/CaseManagementEntry?chartContext=valid',
    sanitizeElementByPattern() {}, CONTROL_CHAR_PATTERN_2: /x/,
    origCaseNote: 'saved', origObservationDate: 'today',
    console: { error() {} }, clearTimeout() {}, setTimeout: fn => { timers.push(fn); return timers.length; },
    CarlosAjax: { request(url, options) {
      const request = { url, options, abort() { options.onFailure({ status: 0 }); options.onComplete(); } };
      requests.push(request); return request;
    } },
  };
  vm.createContext(context);
  vm.runInContext(renderLabels(modernSource.slice(modernSource.indexOf('var month=new Array(12);'),
    modernSource.indexOf('    var unsavedNoteMsg;', modernSource.indexOf('var month=new Array(12);')))), context);
  context.msgDraftSaved = 'Draft saved';
  return { context, status, editor, requests, timers, elements };
}

for (const code of [0, 400, 403, 500]) {
  test(`modern HTTP ${code} failure replaces the prior saved claim and retains clinical text, then retry succeeds`, () => {
    const h = modern(); h.context.autoSave();
    assert.equal(h.status.attributes['data-autosave-state'], 'pending');
    h.requests[0].options.onFailure({ status: code }); h.requests[0].options.onComplete();
    assert.equal(h.status.attributes['data-autosave-state'], 'failed');
    assert.equal(h.status.textContent, labels.draftSaveFailedLabel);
    assert.equal(h.editor.value, 'Unsaved H&P + clinical text');
    h.context.autoSave();
    assert.equal(h.status.attributes['data-autosave-state'], 'pending');
    h.requests[1].options.onSuccess({ status: 200 }); h.requests[1].options.onComplete();
    assert.equal(h.status.attributes['data-autosave-state'], 'saved');
    assert.match(h.status.textContent, /^Draft saved /);
    assert.equal(new URLSearchParams(h.requests[1].options.postBody).get('note'), h.editor.value);
  });
}

test('modern explicit cancellation does not announce an autosave failure and stale callbacks cannot replace a new status', () => {
  const h = modern(); h.context.autoSave();
  const cancelled = h.requests[0];
  h.context.clearAutoSaveTimer();
  assert.equal(h.context.autoSaveXhr, null);
  assert.notEqual(h.status.attributes['data-autosave-state'], 'failed');
  h.context.autoSave();
  cancelled.options.onFailure({ status: 500 }); cancelled.options.onComplete();
  assert.equal(h.status.attributes['data-autosave-state'], 'pending');
  assert.equal(h.context.autoSaveXhr, h.requests[1]);
});

test('modern lock conflict stops periodic retries and keeps the text available for recovery', () => {
  const h = modern(); h.context.autoSave();
  h.requests[0].options.onFailure({ status: 409 }); h.requests[0].options.onComplete();
  h.context.backup(); h.context.autoSave();
  assert.equal(h.requests.length, 1);
  assert.equal(h.status.attributes['data-autosave-state'], 'conflict');
  assert.equal(h.status.textContent, labels.draftSaveConflictLabel);
  assert.equal(h.timers.length, 0);
  assert.equal(h.editor.value, 'Unsaved H&P + clinical text');
});

test('modern overlapping autosaves are serialized and old editor responses cannot label another editor saved', () => {
  const h = modern(); h.context.autoSave(); h.context.autoSave();
  assert.equal(h.requests.length, 1);
  h.elements.editor = { value: 'Different note editor' };
  h.requests[0].options.onSuccess({ status: 200 }); h.requests[0].options.onComplete();
  assert.notEqual(h.status.attributes['data-autosave-state'], 'saved');
  assert.equal(h.elements.editor.value, 'Different note editor');
});

test('modern cleared text is submitted and saved is announced only after acknowledgment', () => {
  const h = modern(); h.editor.value = ''; h.context.autoSave();
  assert.equal(new URLSearchParams(h.requests[0].options.postBody).get('note'), '');
  assert.equal(h.status.attributes['data-autosave-state'], 'pending');
  h.requests[0].options.onSuccess({ status: 200 }); h.requests[0].options.onComplete();
  assert.equal(h.status.attributes['data-autosave-state'], 'saved');
});

function legacy() {
  const status = statusElement();
  const editor = { value: 'Legacy H&P + clinical text' };
  const requests = [];
  const timers = [];
  const listeners = {};
  class FakeXhr {
    constructor() { this.readyState = 0; this.status = 0; requests.push(this); }
    open() {}
    setRequestHeader() {}
    send(body) { this.body = body; }
    finish(code) { this.status = code; this.readyState = 4; this.onreadystatechange(); }
    abort() { this.onabort(); }
  }
  const context = {
    document: { getElementById: id => id === 'legacyAutosaveStatus' ? status : editor },
    XMLHttpRequest: FakeXhr, clearTimeout() {}, setTimeout: fn => { timers.push(fn); return timers.length; },
    window: { XMLHttpRequest: FakeXhr, addEventListener: (name, callback) => { listeners[name] = callback; } },
  };
  const from = legacySource.indexOf('        var XMLHttpRequestObject = false;');
  const to = legacySource.indexOf('        function init()', from);
  let source = legacySource.slice(from, to)
    .replace('<%=request.getContextPath() %>', '/carlos')
    .replace(/var demographicNo = [\s\S]+?XMLHttpRequestObject.send\(/,
      "var demographicNo = '101'; var noteId = '0'; var programId = '11'; var chartContext = 'valid'; XMLHttpRequestObject.send(");
  vm.createContext(context); vm.runInContext(renderLabels(source), context);
  return { context, status, editor, requests, timers, listeners };
}

for (const code of [0, 403, 500]) {
  test(`legacy HTTP ${code} failure is visible and retries once without losing text or scheduling duplicate timers`, () => {
    const h = legacy(); h.context.autoSave(); h.context.autoSave();
    assert.equal(h.requests.length, 1);
    assert.equal(h.status.attributes['data-autosave-state'], 'pending');
    h.requests[0].finish(code); h.requests[0].onerror();
    assert.equal(h.timers.length, 1);
    assert.equal(h.status.attributes['data-autosave-state'], 'failed');
    assert.equal(h.status.textContent, labels.draftSaveFailedLabel);
    assert.equal(h.editor.value, 'Legacy H&P + clinical text');
    h.context.autoSave(); h.requests[1].finish(200);
    assert.notEqual(h.requests[0], h.requests[1]);
    assert.equal(h.status.attributes['data-autosave-state'], 'saved');
    assert.equal(h.status.textContent, labels.draftSavedLabel);
    assert.equal(new URLSearchParams(h.requests[1].body).get('note'), h.editor.value);
  });
}

test('legacy lock conflict stops further autosaves and page teardown cancellation stays silent', () => {
  const conflict = legacy(); conflict.context.autoSave(); conflict.requests[0].finish(409);
  conflict.context.autoSave();
  assert.equal(conflict.requests.length, 1); assert.equal(conflict.timers.length, 0);
  assert.equal(conflict.status.attributes['data-autosave-state'], 'conflict');
  const teardown = legacy(); teardown.context.autoSave(); teardown.listeners.pagehide();
  assert.equal(teardown.timers.length, 0);
  assert.notEqual(teardown.status.attributes['data-autosave-state'], 'failed');
});

test('both editors expose an atomic polite status region', () => {
  for (const [source, id] of [[legacySource, 'legacyAutosaveStatus'],
    [fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/noteIssueList.jsp'), 'utf8'), 'autosaveTime']]) {
    const tag = source.match(new RegExp(`<div[^>]+id=['"]${id}['"][^>]*>`))[0];
    assert.match(tag, /role=['"]status['"]/); assert.match(tag, /aria-live=['"]polite['"]/);
    assert.match(tag, /aria-atomic=['"]true['"]/);
  }
});
