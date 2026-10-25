/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const modernSource = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const legacySource = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/CaseManagementEntry.jsp'), 'utf8');
const resourceSource = fs.readFileSync(path.join(__dirname, '../src/main/resources/oscarResources_en.properties'), 'utf8');
const recoveryMessage = /^encounter\.draftSaveConflict\.msg=(.*)$/m.exec(resourceSource)[1];
const labels = { draftSavingLabel: 'Saving draft...', draftSaveFailedLabel: 'Draft failed; retrying.',
  draftSaveConflictLabel: recoveryMessage, draftSavedLabel: 'Draft saved' };
const renderLabels = source => source.replace(/\$\{carlos:forJavaScriptBlock\((\w+)\)\}/g,
  (_, key) => { assert.ok(labels[key]); return labels[key]; });

function statusElement() {
  return { textContent: 'Draft saved previously', attributes: {},
    setAttribute(name, value) { this.attributes[name] = value; } };
}

const ack = overrides => ({ status: 200, responseText: '',
  responseURL: 'https://clinic.example/CaseManagementEntry?chartContext=valid', ...overrides });
function unexpectedAcks(requestUrl) {
  const otherOrigin = new URL(requestUrl); otherOrigin.hostname = 'other.example';
  const changedQuery = new URL(requestUrl); changedQuery.searchParams.set('unexpected', 'changed');
  return [
    ['login HTML', { responseText: '<html><h1>Login</h1>clinician-secret</html>' }],
    ['an empty redirect to logout', { responseURL: 'https://clinic.example/logoutPage' }],
    ['another origin', { responseURL: otherOrigin.href }],
    ['a changed action query', { responseURL: changedQuery.href }],
    ['missing response bytes', { responseText: undefined }],
    ['an invalid final URL', { responseURL: 'http://[invalid' }],
  ];
}

function modern() {
  const status = statusElement();
  const editor = { value: 'Unsaved H&P + clinical text' };
  const requests = [];
  const timers = [];
  const diagnostics = [];
  const elements = { editor, autosaveTime: status, observationDate: { value: 'today' } };
  const context = {
    caseNote: 'editor', demographicNo: '101', case_program_id: '11',
    document: { getElementById: id => elements[id], forms: { caseManagementEntryForm: { noteId: { value: '0' } } } },
    $: id => elements[id], $F: id => elements[id].value,
    chartActionUrl: () => '/CaseManagementEntry?chartContext=valid',
    sanitizeElementByPattern() {}, CONTROL_CHAR_PATTERN_2: /x/,
    origCaseNote: 'saved', origObservationDate: 'today',
    URL, window: { location: { href: 'https://clinic.example/CaseManagementEntry' } },
    console: { error: (...args) => diagnostics.push(args) }, clearTimeout() {},
    setTimeout: fn => { timers.push(fn); return timers.length; },
    CarlosAjax: { request(url, options) {
      const request = { url, options, abort() { options.onFailure({ status: 0 }); options.onComplete(); } };
      requests.push(request); return request;
    } },
  };
  vm.createContext(context);
  vm.runInContext(renderLabels(modernSource.slice(modernSource.indexOf('var month=new Array(12);'),
    modernSource.indexOf('    var unsavedNoteMsg;', modernSource.indexOf('var month=new Array(12);')))), context);
  context.msgDraftSaved = 'Draft saved';
  return { context, status, editor, requests, timers, elements, diagnostics };
}

/** Execute the real CarlosAjax request path and dispatch native XHR callbacks. */
function modernNativeAjax() {
  const h = modern();
  const nativeRequests = [];
  class NativeXhr {
    constructor() { this.timeout = 0; this.headers = {}; nativeRequests.push(this); }
    open(method, url, asynchronous) { this.method = method; this.url = url; this.asynchronous = asynchronous; }
    setRequestHeader(name, value) { this.headers[name] = value; }
    send(body) { this.body = body; }
    abort() { this.aborted = true; }
    finish() { Object.assign(this, ack()); this.onload(); }
  }
  h.context.XMLHttpRequest = NativeXhr;
  h.context.document.querySelector = selector => selector === 'input[name="CSRF-TOKEN"]' ? { value: 'owned-csrf' } : null;
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/carlos-ajax.js'), 'utf8'), h.context);
  return { ...h, nativeRequests };
}

test('real CarlosAjax autosave XHR times out after 45 seconds, reports failure and releases the next backup retry', () => {
  const h = modernNativeAjax(); h.context.backup();
  const first = h.nativeRequests[0];
  assert.equal(first.timeout, 45000);
  assert.equal(first.method, 'POST'); assert.equal(first.asynchronous, true);
  assert.equal(new URLSearchParams(first.body).get('CSRF-TOKEN'), 'owned-csrf');
  assert.equal(new URLSearchParams(first.body).get('note'), h.editor.value);
  first.ontimeout();
  assert.equal(h.status.attributes['data-autosave-state'], 'failed');
  assert.equal(h.context.autoSaveOperation, null); assert.equal(h.context.autoSaveXhr, null);
  assert.deepEqual(h.diagnostics, [['CaseManagement autoSave failed', 0, 'Request timed out']]);
  assert.equal(h.editor.value, 'Unsaved H&P + clinical text');
  h.context.backup(); assert.equal(h.nativeRequests.length, 2);
  assert.equal(h.nativeRequests[1].timeout, 45000);
  h.nativeRequests[1].finish();
  assert.equal(h.status.attributes['data-autosave-state'], 'saved');
});

test('late native timeout and completion callbacks cannot clear or overwrite another active autosave', () => {
  const h = modernNativeAjax(); h.context.autoSave();
  const first = h.nativeRequests[0]; first.ontimeout();
  h.context.autoSave(); const retry = h.nativeRequests[1];
  first.ontimeout(); first.finish();
  assert.equal(h.context.autoSaveXhr, retry);
  assert.equal(h.status.attributes['data-autosave-state'], 'pending');
  assert.equal(h.diagnostics.length, 1);
  retry.finish(); assert.equal(h.status.attributes['data-autosave-state'], 'saved');
});

test('a native timeout releases serialization even if updating the failure status throws', () => {
  const h = modernNativeAjax(); h.context.backup();
  const updateStatus = h.status.setAttribute;
  h.status.setAttribute = function (name, value) {
    if (value === 'failed') throw new Error('synthetic status update failure');
    updateStatus.call(this, name, value);
  };
  assert.throws(() => h.nativeRequests[0].ontimeout(), /synthetic status update failure/);
  assert.equal(h.context.autoSaveOperation, null); assert.equal(h.context.autoSaveXhr, null);
  h.status.setAttribute = updateStatus;
  h.context.backup(); assert.equal(h.nativeRequests.length, 2);
  h.nativeRequests[1].finish();
  assert.equal(h.status.attributes['data-autosave-state'], 'saved');
});

test('explicit native XHR cancellation stays silent and a stale timeout does not establish a retry baseline', () => {
  const h = modernNativeAjax(); h.context.origCaseNote = '';
  h.context.backup(); const first = h.nativeRequests[0]; h.context.clearAutoSaveTimer();
  assert.equal(first.aborted, true); first.ontimeout();
  assert.notEqual(h.status.attributes['data-autosave-state'], 'failed');
  assert.equal(h.context.acknowledgedDrafts.get(h.editor), undefined);
  h.editor.value = ''; h.context.backup();
  assert.equal(h.nativeRequests.length, 1); assert.equal(h.diagnostics.length, 0);
});

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
    h.requests[1].options.onSuccess(ack()); h.requests[1].options.onComplete();
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

for (const reopen of [false, true]) {
  test(`a lost-lock prompt warns that edits may be unsaved and says to copy before reopening (${reopen})`, () => {
    const h = modern(); const prompts = []; let reloads = 0;
    h.context.lostNoteLock = true;
    h.context.confirm = message => { prompts.push(message); return reopen; };
    h.context.window.location.reload = () => { reloads++; };
    h.context.jQuery = () => ({val: () => h.editor.value});
    h.context.numChars = h.editor.value.length;
    h.context.adjustCaseNote = () => { throw new Error('unexpected resize'); };
    const start = modernSource.indexOf('    function monitorCaseNote(');
    const end = modernSource.indexOf('\n    function ', start + 1);
    assert.ok(start >= 0 && end > start, 'actual note lock monitor exists');
    vm.runInContext(modernSource.slice(start, end), h.context);

    h.context.monitorCaseNote({keyCode: 65});

    assert.equal(prompts.length, 1);
    assert.ok(prompts[0].startsWith(recoveryMessage));
    assert.match(prompts[0], /latest changes may not be saved/);
    assert.match(prompts[0], /Copy your changes before reopening the chart/);
    assert.equal(reloads, reopen ? 1 : 0);
    assert.equal(h.editor.value, 'Unsaved H&P + clinical text');
  });
}

test('modern overlapping autosaves are serialized and old editor responses cannot label another editor saved', () => {
  const h = modern(); h.context.autoSave(); h.context.autoSave();
  assert.equal(h.requests.length, 1);
  h.elements.editor = { value: 'Different note editor' };
  h.requests[0].options.onSuccess(ack()); h.requests[0].options.onComplete();
  assert.notEqual(h.status.attributes['data-autosave-state'], 'saved');
  assert.equal(h.elements.editor.value, 'Different note editor');
});

test('modern cleared text is submitted and saved is announced only after acknowledgment', () => {
  const h = modern(); h.editor.value = ''; h.context.autoSave();
  assert.equal(new URLSearchParams(h.requests[0].options.postBody).get('note'), '');
  assert.equal(h.status.attributes['data-autosave-state'], 'pending');
  h.requests[0].options.onSuccess(ack()); h.requests[0].options.onComplete();
  assert.equal(h.status.attributes['data-autosave-state'], 'saved');
});

test('periodic backup replaces an acknowledged nonempty draft when the clinician reverts a new note to empty', () => {
  const h = modern(); h.context.origCaseNote = ''; h.editor.value = '';
  h.context.backup();
  assert.equal(h.requests.length, 0, 'a pristine new editor needs no draft write');
  h.editor.value = 'Typed clinical text'; h.context.backup();
  h.requests[0].options.onSuccess(ack()); h.requests[0].options.onComplete();
  h.editor.value = ''; h.context.backup();
  assert.equal(h.requests.length, 2, 'the periodic path must write the cleared draft');
  assert.equal(new URLSearchParams(h.requests[1].options.postBody).get('note'), '');
  assert.equal(h.context.origCaseNote, '', 'draft acknowledgments must not alter explicit-save tracking');
});

test('periodic backup remembers the submitted text when the editor changes before the response arrives', () => {
  const h = modern(); h.context.origCaseNote = '';
  h.editor.value = 'In-flight text'; h.context.backup();
  h.editor.value = '';
  h.requests[0].options.onSuccess(ack()); h.requests[0].options.onComplete();
  h.context.backup();
  assert.equal(h.requests.length, 2, 'acknowledging the old request must not claim the current empty editor was saved');
  assert.equal(new URLSearchParams(h.requests[1].options.postBody).get('note'), '');
});

test('periodic backup retries a failed clear even though it matches the original empty note', () => {
  const h = modern(); h.context.origCaseNote = '';
  h.context.backup(); h.requests[0].options.onSuccess(ack()); h.requests[0].options.onComplete();
  h.editor.value = ''; h.context.backup();
  h.requests[1].options.onFailure({ status: 500 }); h.requests[1].options.onComplete();
  h.context.backup();
  assert.equal(h.requests.length, 3);
  assert.equal(new URLSearchParams(h.requests[2].options.postBody).get('note'), '');
  h.requests[2].options.onSuccess(ack()); h.requests[2].options.onComplete();
  h.context.backup();
  assert.equal(h.requests.length, 3, 'an acknowledged empty draft needs no further write');
});

test('a failed initial request remains retryable after the clinician reverts its possibly committed text', () => {
  const h = modern(); h.context.origCaseNote = '';
  h.editor.value = 'Unacknowledged text'; h.context.backup();
  h.editor.value = ''; h.requests[0].options.onFailure({ status: 0 }); h.requests[0].options.onComplete();
  h.context.backup();
  assert.equal(h.requests.length, 2);
  assert.equal(new URLSearchParams(h.requests[1].options.postBody).get('note'), '');
});

test('cancelled and replaced editor requests cannot create a draft baseline for a new pristine editor', () => {
  const h = modern(); h.context.origCaseNote = '';
  h.context.backup(); const cancelled = h.requests[0]; h.context.clearAutoSaveTimer();
  h.editor.value = ''; cancelled.options.onSuccess(ack()); cancelled.options.onComplete();
  h.context.backup();
  assert.equal(h.requests.length, 1, 'an explicitly cancelled response is not an acknowledgment');
  h.editor.value = 'Previous editor text'; h.context.backup();
  const previous = h.requests[1];
  h.elements.editor = { value: '' };
  previous.options.onSuccess(ack()); previous.options.onComplete(); h.context.backup();
  assert.equal(h.requests.length, 2, 'a stale editor completion must not dirty its replacement');
});

test('a successful draft for an earlier note ID cannot dirty a newly selected note in a reused textarea', () => {
  const h = modern(); h.context.origCaseNote = '';
  h.context.backup(); h.requests[0].options.onSuccess(ack()); h.requests[0].options.onComplete();
  h.editor.value = '';
  h.context.document.forms.caseManagementEntryForm.noteId.value = '502';
  h.context.backup();
  assert.equal(h.requests.length, 1);
});

test('a note ID switched during a request cannot receive the previous note acknowledgment or failure', () => {
  const h = modern(); h.context.origCaseNote = '';
  h.context.backup();
  h.editor.value = '';
  h.context.document.forms.caseManagementEntryForm.noteId.value = '502';
  h.requests[0].options.onSuccess(ack());
  h.requests[0].options.onFailure({ status: 500 }); h.requests[0].options.onComplete();
  assert.notEqual(h.status.attributes['data-autosave-state'], 'saved');
  assert.notEqual(h.status.attributes['data-autosave-state'], 'failed');
  h.context.backup(); assert.equal(h.requests.length, 1);
});

for (const outcome of ['success', 'lock conflict', 'network failure']) {
  test(`a replaced form with the same note and textarea ignores its predecessor's ${outcome}`, () => {
    const h = modern(); h.context.autoSave();
    const previous = h.requests[0];
    h.context.document.forms.caseManagementEntryForm = { noteId: { value: '0' } };
    if (outcome === 'success') previous.options.onSuccess(ack());
    else previous.options.onFailure({ status: outcome === 'lock conflict' ? 409 : 0 });
    previous.options.onComplete();
    assert.equal(h.context.lostNoteLock, false, 'the replacement editor still owns its own lock');
    assert.equal(h.context.acknowledgedDrafts.get(h.editor), undefined);
    assert.deepEqual(h.diagnostics, [], 'stale requests must not label the replacement failed');
    h.context.backup();
    assert.equal(h.requests.length, 2, 'the replacement form can immediately back up its text');
    h.requests[1].options.onSuccess(ack()); h.requests[1].options.onComplete();
    assert.equal(h.status.attributes['data-autosave-state'], 'saved');
  });
}

for (const [description, response] of unexpectedAcks('https://clinic.example/CaseManagementEntry?chartContext=valid')) {
  test(`modern HTTP200 ${description} is a visible failure, never a saved draft or a false retry baseline`, () => {
    const h = modern(); h.context.origCaseNote = '';
    h.context.backup();
    const originalText = h.editor.value;
    h.requests[0].options.onSuccess(ack(response)); h.requests[0].options.onComplete();
    assert.equal(h.status.attributes['data-autosave-state'], 'failed');
    assert.equal(h.editor.value, originalText);
    assert.equal(h.context.acknowledgedDrafts.get(h.editor).text, undefined);
    assert.deepEqual(h.diagnostics, [['CaseManagement autoSave failed', 200, 'Unexpected autosave response']]);
    assert.ok(!JSON.stringify(h.diagnostics).includes('clinician-secret'));
    h.editor.value = ''; h.context.backup();
    assert.equal(h.requests.length, 2, 'even a reverted note must retry after an unacknowledged response');
    h.requests[1].options.onSuccess(ack()); h.requests[1].options.onComplete();
    assert.equal(h.status.attributes['data-autosave-state'], 'saved');
    h.context.backup(); assert.equal(h.requests.length, 2);
  });
}

test('modern acknowledgments accept empty response bytes when the transport has no final URL', () => {
  const h = modern(); h.context.autoSave();
  h.requests[0].options.onSuccess(ack({ responseURL: '' })); h.requests[0].options.onComplete();
  assert.equal(h.status.attributes['data-autosave-state'], 'saved');
});

function legacy() {
  const status = statusElement();
  const editor = { value: 'Legacy H&P + clinical text' };
  const requests = [];
  const timers = [];
  const activeTimers = new Map();
  const listeners = {};
  class FakeXhr {
    constructor() { this.readyState = 0; this.status = 0; this.responseText = '';
      this.responseURL = 'https://clinic.example/carlos/CaseManagementEntry'; requests.push(this); }
    open() {}
    setRequestHeader() {}
    send(body) { this.body = body; }
    finish(code) { this.status = code; this.readyState = 4; this.onreadystatechange(); }
    abort() { this.aborted = true; this.onabort(); }
  }
  const context = {
    document: { getElementById: id => id === 'legacyAutosaveStatus' ? status : editor },
    XMLHttpRequest: FakeXhr, clearTimeout: id => activeTimers.delete(id),
    setTimeout: fn => { timers.push(fn); activeTimers.set(timers.length, fn); return timers.length; },
    URL, window: { XMLHttpRequest: FakeXhr, location: { href: 'https://clinic.example/carlos/CaseManagementEntry' },
      addEventListener: (name, callback) => { listeners[name] = callback; } },
  };
  const from = legacySource.indexOf('        var XMLHttpRequestObject = false;');
  const to = legacySource.indexOf('        function init()', from);
  let source = legacySource.slice(from, to)
    .replace('<%=request.getContextPath() %>', '/carlos')
    .replace(/var demographicNo = [\s\S]+?XMLHttpRequestObject.send\(/,
      "var demographicNo = '101'; var noteId = '0'; var programId = '11'; var chartContext = 'valid'; XMLHttpRequestObject.send(");
  vm.createContext(context); vm.runInContext(renderLabels(source), context);
  return { context, status, editor, requests, timers, activeTimers, listeners };
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

test('legacy persisted page restoration resumes one timer and saves the latest text after aborting the old transport', () => {
  const h = legacy(); h.context.autoSave();
  const abandoned = h.requests[0];
  h.listeners.pagehide();
  assert.equal(abandoned.aborted, true);
  assert.equal(h.context.legacyAutosavePending, false);
  assert.equal(h.activeTimers.size, 0);
  h.editor.value = 'Restored exact H&P + café 中文\n  50% & note_id=999';
  h.listeners.pageshow({ persisted: true });
  assert.equal(h.context.legacyAutosaveStopped, false);
  assert.equal(h.activeTimers.size, 1);
  h.listeners.pageshow({ persisted: true });
  assert.equal(h.timers.length, 1, 'the same restore cannot schedule a duplicate timer');
  h.timers.at(-1)();
  assert.equal(h.requests.length, 2);
  assert.equal(new URLSearchParams(h.requests[1].body).get('note'), h.editor.value);
  assert.deepEqual(new URLSearchParams(h.requests[1].body).getAll('note_id'), ['0']);
  abandoned.finish(409); abandoned.onerror(); abandoned.ontimeout();
  assert.equal(h.context.legacyAutosavePending, true);
  assert.equal(h.context.legacyAutosaveStopped, false);
  h.requests[1].finish(200);
  assert.equal(h.status.attributes['data-autosave-state'], 'saved');
  assert.equal(h.activeTimers.size, 1);
});

test('legacy ordinary pageshow never restarts a hidden editor or replaces an existing timer', () => {
  const h = legacy(); h.context.setTimer();
  h.listeners.pageshow({ persisted: false });
  h.listeners.pageshow({ persisted: true });
  assert.equal(h.timers.length, 1, 'pageshow without pagehide does not replace the active timer');
  h.listeners.pagehide();
  h.listeners.pageshow({ persisted: false });
  assert.equal(h.context.legacyAutosaveStopped, true);
  assert.equal(h.activeTimers.size, 0);
  assert.equal(h.requests.length, 0);
});

for (const hide of [false, true]) {
  test(`legacy conflict remains stopped on persisted pageshow ${hide ? 'after pagehide' : 'without pagehide'}`, () => {
    const h = legacy(); h.context.autoSave(); h.requests[0].finish(409);
    if (hide) h.listeners.pagehide();
    h.listeners.pageshow({ persisted: true });
    h.context.autoSave();
    assert.equal(h.context.legacyAutosaveStopped, true);
    assert.equal(h.activeTimers.size, 0);
    assert.equal(h.requests.length, 1);
    assert.equal(h.status.attributes['data-autosave-state'], 'conflict');
    assert.equal(h.status.textContent, labels.draftSaveConflictLabel);
    assert.equal(h.editor.value, 'Legacy H&P + clinical text');
  });
}

test('legacy timeout retries its latest complete text once and ignores late callbacks from the failed request', () => {
  const h = legacy(); h.context.autoSave();
  const timedOut = h.requests[0];
  assert.equal(timedOut.timeout, 45000);
  timedOut.ontimeout();
  assert.equal(h.status.attributes['data-autosave-state'], 'failed');
  assert.equal(h.timers.length, 1);
  h.editor.value = 'Latest H&P + 50% = 1\n\u00e9\u4e2d\u6587 & note_id=999';
  h.timers[0]();
  assert.equal(h.requests.length, 2);
  const retry = h.requests[1];
  const body = new URLSearchParams(retry.body);
  assert.equal(body.get('note'), h.editor.value, 'clinical punctuation and Unicode remain one exact field');
  assert.deepEqual(body.getAll('note_id'), ['0'], 'note text must never inject another draft identity');
  timedOut.finish(409); timedOut.onerror(); timedOut.ontimeout();
  assert.equal(h.status.attributes['data-autosave-state'], 'pending');
  assert.equal(h.context.legacyAutosavePending, true);
  assert.equal(h.context.legacyAutosaveStopped, false);
  assert.equal(h.timers.length, 1, 'old transport callbacks cannot schedule another retry');
  retry.finish(200);
  assert.equal(h.status.attributes['data-autosave-state'], 'saved');
  assert.equal(h.context.legacyAutosavePending, false);
  assert.equal(h.timers.length, 2, 'the acknowledged retry schedules one next backup');
});

for (const [description, response] of unexpectedAcks('https://clinic.example/carlos/CaseManagementEntry')) {
  test(`legacy HTTP200 ${description} leaves the editor intact and reports failure until a genuine retry succeeds`, () => {
    const h = legacy(); h.context.autoSave();
    Object.assign(h.requests[0], response); h.requests[0].finish(200);
    assert.equal(h.status.attributes['data-autosave-state'], 'failed');
    assert.equal(h.status.textContent, labels.draftSaveFailedLabel);
    assert.equal(h.editor.value, 'Legacy H&P + clinical text');
    assert.equal(h.timers.length, 1);
    h.context.autoSave(); h.requests[1].finish(200);
    assert.equal(h.status.attributes['data-autosave-state'], 'saved');
    assert.equal(h.status.textContent, labels.draftSavedLabel);
  });
}

test('both editors expose an atomic polite status region', () => {
  for (const [source, id] of [[legacySource, 'legacyAutosaveStatus'],
    [fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/noteIssueList.jsp'), 'utf8'), 'autosaveTime']]) {
    const tag = source.match(new RegExp(`<div[^>]+id=['"]${id}['"][^>]*>`))[0];
    assert.match(tag, /role=['"]status['"]/); assert.match(tag, /aria-live=['"]polite['"]/);
    assert.match(tag, /aria-atomic=['"]true['"]/);
  }
});
