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
const labels = { noteLockLostLabel: 'Your note lock has been lost.', draftSavingLabel: 'Saving draft...', draftSaveFailedLabel: 'Draft failed; retrying.',
  draftSaveConflictLabel: recoveryMessage, draftSavedLabel: 'Draft saved',
  noteLockReleaseFailedLabel: 'Your note lock could not be released.' };
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

function legacy(ownsLock = true) {
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
  context.legacyNoteLockOwned = ownsLock;
  return { context, status, editor, requests, timers, activeTimers, listeners };
}

test('opening the classic form for reading neither autosaves nor starts an autosave timer', () => {
  const h = legacy(false);
  h.context.setTimer();
  h.context.autoSave();
  assert.equal(h.requests.length, 0);
  assert.equal(h.timers.length, 0);
});

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

// The classic editor's Edit and Cancel buttons (#6132). The page script is loaded as the JSP ships it,
// with its server-rendered values filled in.
function classicEditor({ ownedAtRender = false, attemptAtRender = '' } = {}) {
  const requests = [];
  const closed = [];
  const alerts = [];
  class FakeXhr {
    constructor() { this.readyState = 0; this.status = 0; this.responseText = ''; requests.push(this); }
    open(method, url) { this.method = method; this.url = url; }
    setRequestHeader() {}
    send(body) { this.body = body; }
    answer(status, json) { this.status = status; this.responseText = typeof json === 'string' ? json : JSON.stringify(json);
      this.readyState = 4; this.onreadystatechange(); }
  }
  const fields = [{ disabled: true }, { disabled: true }];
  const edit = { disabled: false, hidden: false };
  const note = { readOnly: true, value: 'text', focused: false, focus() { this.focused = true; } };
  const attemptField = { value: '' };
  const timers = [];
  let draws = 0;
  // A distinct, repeatable "random" draw per attempt.
  const crypto = { getRandomValues(bytes) { draws += 1; for (let i = 0; i < bytes.length; i += 1) bytes[i] = (draws * 37 + i * 11) & 255; return bytes; } };
  const context = {
    alert: message => alerts.push(message), URL,
    XMLHttpRequest: FakeXhr, clearTimeout() {}, setTimeout: fn => { timers.push(fn); return timers.length; },
    document: {
      getElementById: id => ({ editLegacyNote: edit, caseNote_note: note, editAttemptField: attemptField }[id] || { setAttribute() {} }),
      querySelectorAll: () => fields,
    },
    window: { XMLHttpRequest: FakeXhr, close: () => closed.push('closed'), opener: null, crypto,
      location: { href: 'https://clinic.example/carlos/CaseManagementEntry' }, addEventListener() {} },
  };
  const fill = text => renderLabels(text)
    .replace(/<%=request\.getContextPath\(\) %>/g, '/carlos')
    .replace(/var demographicNo = '\$\{carlos:forJavaScript\(param\.demographicNo\)\}';/, "var demographicNo = '101';")
    .replace(/var noteId = '<carlos:encode[\s\S]+?<%--[\s\S]+?--%>/, "var noteId = '5009';")
    .replace(/var chartContext = '<%=[\s\S]+?%>';/, "var chartContext = 'valid';")
    .replace(/var legacyEditAttempt = '<%=[\s\S]+?%>';/, `var legacyEditAttempt = '${attemptAtRender}';`);
  const first = legacySource.indexOf('        var XMLHttpRequestObject = false;');
  const second = legacySource.indexOf('        function init() {', first);
  const third = legacySource.indexOf('        function restore() {', second);
  vm.createContext(context);
  vm.runInContext(fill(legacySource.slice(first, second)) + fill(legacySource.slice(second, third)), context);
  context.noteLockOwnedAtRender = ownedAtRender;
  const submitted = [];
  const form = { elements: { method: { value: 'save' } }, submit() { submitted.push(this.elements.method.value); } };
  return { context, requests, closed, alerts, fields, edit, note, timers, form, submitted, attemptField };
}

const fields = request => new URLSearchParams(request.body);
const GRANTED = { isNoteEdited: 'unlocked', ownedBySession: true, lockTaken: true };

test('Edit takes the lock and starts editing only once this session owns it', () => {
  const h = classicEditor();
  h.context.takeNoteLock();
  assert.equal(h.requests.length, 1);
  assert.equal(fields(h.requests[0]).get('method'), 'isNoteEdited');
  // The page says it is the one asking, so the server can refuse a reload when its form is gone.
  assert.equal(fields(h.requests[0]).get('classicEdit'), 'true');
  // The attempt's identity, which Cancel names later.
  assert.match(fields(h.requests[0]).get('editAttempt'), /^[0-9a-f]{32}$/);
  assert.equal(h.note.readOnly, true, 'nothing is editable while the request is in flight');
  h.requests[0].answer(200, GRANTED);
  assert.equal(h.note.readOnly, false);
  assert.ok(h.fields.every(field => field.disabled === false));
  assert.equal(h.edit.hidden, true);
  assert.equal(h.timers.length, 1, 'autosave starts');
  assert.equal(h.attemptField.value, fields(h.requests[0]).get('editAttempt'), 'the form carries the attempt into a Save');
});

test('Edit refused as stale or while another note is open keeps the page read-only and says so', () => {
  for (const state of ['stale', 'editorOpen', 'other', 'reload']) {
    const h = classicEditor();
    h.context.takeNoteLock();
    h.requests[0].answer(200, { isNoteEdited: state, ownedBySession: false, lockTaken: false });
    assert.equal(h.note.readOnly, true, state);
    assert.deepEqual(h.alerts, [labels.noteLockLostLabel], state);
    assert.equal(h.edit.disabled, false, 'Edit can be tried again');
    assert.equal(h.timers.length, 0);
  }
});

test('a retried Edit repeats an attempt whose answer was lost, and a refused Edit starts a new one', () => {
  const h = classicEditor();
  h.context.takeNoteLock();
  const first = fields(h.requests[0]).get('editAttempt');
  h.requests[0].answer(0, '');
  assert.equal(h.note.readOnly, true);
  assert.equal(h.edit.disabled, false);
  h.context.takeNoteLock();
  assert.equal(fields(h.requests[1]).get('editAttempt'), first, 'the server may have granted it: the same attempt asks again');
  h.requests[1].answer(200, { isNoteEdited: 'other', ownedBySession: false, lockTaken: false });
  h.context.takeNoteLock();
  const third = fields(h.requests[2]).get('editAttempt');
  assert.match(third, /^[0-9a-f]{32}$/);
  assert.notEqual(third, first, 'a refused attempt took nothing');
});

test('Cancel while Edit is in flight waits, releases the lock Edit took, and closes only afterwards', () => {
  const h = classicEditor();
  h.context.takeNoteLock();
  assert.equal(h.context.cancelLegacyNote(h.form), false, 'the form is not submitted');
  assert.deepEqual(h.closed, [], 'the window stays open until the answer is in');
  assert.equal(h.requests.length, 1, 'no release before the lock is known');
  h.requests[0].answer(200, GRANTED);
  // A cancelled reader never starts editing.
  assert.equal(h.note.readOnly, true);
  assert.equal(h.timers.length, 0);
  assert.equal(h.requests.length, 2);
  const release = fields(h.requests[1]);
  assert.equal(release.get('method'), 'releaseNoteLock');
  assert.equal(release.get('noteId'), '5009');
  assert.equal(release.get('demographicNo'), '101');
  assert.equal(release.get('editAttempt'), fields(h.requests[0]).get('editAttempt'), 'Cancel names the attempt Edit sent');
  assert.equal(release.get('classicEdit'), null, 'only the Edit request carries it');
  assert.equal(h.requests[1].method, 'POST');
  assert.deepEqual(h.closed, [], 'closed only after the release answers');
  h.requests[1].answer(200, { releaseNoteLock: 'released' });
  assert.deepEqual(h.closed, ['closed']);
  assert.deepEqual(h.alerts, []);
});

test('Cancel while Edit is in flight releases nothing when Edit was refused or took no lock', () => {
  for (const answer of [
    [200, { isNoteEdited: 'user', ownedBySession: true, lockTaken: false }],
    [200, { isNoteEdited: 'other', ownedBySession: false, lockTaken: false }],
    [200, { isNoteEdited: 'editorOpen', ownedBySession: false, lockTaken: false }],
  ]) {
    const h = classicEditor();
    h.context.takeNoteLock();
    h.context.cancelLegacyNote(h.form);
    h.requests[0].answer(...answer);
    assert.equal(h.requests.length, 1, JSON.stringify(answer));
    assert.deepEqual(h.closed, ['closed'], JSON.stringify(answer));
    assert.equal(h.note.readOnly, true);
  }
});

test('Cancel while Edit is in flight asks the server to settle the attempt when the Edit answer is lost', () => {
  for (const [lost, settled] of [
    [[0, ''], 'released'], [[403, 'Forbidden'], 'notHeld'], [[500, 'Server error'], 'cancelled'], [[200, 'not json'], 'notHeld'],
  ]) {
    const h = classicEditor();
    h.context.takeNoteLock();
    h.context.cancelLegacyNote(h.form);
    h.requests[0].answer(...lost);
    assert.equal(h.requests.length, 2, `${JSON.stringify(lost)}: the server knows whether this attempt took the lock`);
    const release = fields(h.requests[1]);
    assert.equal(release.get('method'), 'releaseNoteLock');
    assert.equal(release.get('editAttempt'), fields(h.requests[0]).get('editAttempt'));
    assert.deepEqual(h.closed, []);
    h.requests[1].answer(200, { releaseNoteLock: settled });
    assert.deepEqual(h.closed, ['closed'], JSON.stringify(lost));
    assert.equal(h.note.readOnly, true);
  }
});

test('a failed or uncertain release keeps the window open, says so, and Cancel asks again for the same attempt', () => {
  for (const failure of [
    [500, { releaseNoteLock: 'failed' }], [0, ''], [200, ''], [200, '<html>Login</html>'], [200, { releaseNoteLock: 'surprise' }],
  ]) {
    const h = classicEditor();
    h.context.takeNoteLock();
    h.context.cancelLegacyNote(h.form);
    h.requests[0].answer(200, GRANTED);
    const attempt = fields(h.requests[1]).get('editAttempt');
    assert.equal(h.context.cancelLegacyNote(h.form), false);
    assert.equal(h.requests.length, 2, 'a second Cancel waits for the release on its way');
    h.requests[1].answer(...failure);
    assert.deepEqual(h.closed, [], JSON.stringify(failure));
    assert.deepEqual(h.alerts, [labels.noteLockReleaseFailedLabel], JSON.stringify(failure));
    assert.equal(h.context.cancelLegacyNote(h.form), false);
    assert.equal(h.requests.length, 3, 'Cancel asks again');
    assert.equal(fields(h.requests[2]).get('method'), 'releaseNoteLock');
    assert.equal(fields(h.requests[2]).get('editAttempt'), attempt);
    h.requests[2].answer(200, { releaseNoteLock: 'released' });
    assert.deepEqual(h.closed, ['closed'], JSON.stringify(failure));
  }
});

test('Cancel with no Edit closes at once; Cancel after Edit gives the lock back first, then submits the cancel', () => {
  const idle = classicEditor();
  assert.equal(idle.context.cancelLegacyNote(idle.form), false);
  assert.deepEqual(idle.closed, ['closed']);
  assert.equal(idle.requests.length, 0);

  const editing = classicEditor();
  editing.context.takeNoteLock();
  editing.requests[0].answer(200, GRANTED);
  assert.equal(editing.timers.length, 1);
  assert.equal(editing.context.cancelLegacyNote(editing.form), false, 'nothing is submitted before the lock is given back');
  assert.equal(editing.requests.length, 2);
  assert.equal(fields(editing.requests[1]).get('method'), 'releaseNoteLock');
  assert.equal(fields(editing.requests[1]).get('editAttempt'), fields(editing.requests[0]).get('editAttempt'));
  // No draft is sent while the lock is being given back.
  editing.timers[0]();
  assert.equal(editing.requests.length, 2);
  assert.deepEqual(editing.submitted, []);
  editing.requests[1].answer(200, { releaseNoteLock: 'released' });
  assert.deepEqual(editing.submitted, ['cancel'], 'the cancel request discards the draft and closes the window');
  assert.deepEqual(editing.closed, []);

  // A failed release leaves the clinician editing, autosave included.
  const kept = classicEditor();
  kept.context.takeNoteLock();
  kept.requests[0].answer(200, GRANTED);
  kept.context.cancelLegacyNote(kept.form);
  kept.requests[1].answer(500, { releaseNoteLock: 'failed' });
  assert.deepEqual(kept.submitted, []);
  assert.equal(kept.note.readOnly, false);
  assert.equal(kept.timers.length, 2, 'autosave is scheduled again');
});

test('a page rendered after a Save that kept the lock goes straight to editing; a fresh GET does not', () => {
  const attempt = '0123456789abcdef0123456789abcdef';
  const saved = classicEditor({ ownedAtRender: true, attemptAtRender: attempt });
  saved.context.init();
  assert.equal(saved.note.readOnly, false);
  assert.ok(saved.fields.every(field => field.disabled === false));
  assert.equal(saved.edit.hidden, true);
  assert.equal(saved.timers.length, 1);
  assert.equal(saved.note.focused, false, 'the cursor is not moved');
  assert.equal(saved.requests.length, 0, 'no second lock request');
  assert.equal(saved.attemptField.value, attempt, 'the next Save carries the attempt on');
  // Its Cancel gives back what the attempt that opened this editor took, then submits the cancel.
  assert.equal(saved.context.cancelLegacyNote(saved.form), false);
  assert.equal(fields(saved.requests[0]).get('method'), 'releaseNoteLock');
  assert.equal(fields(saved.requests[0]).get('editAttempt'), attempt);
  saved.requests[0].answer(200, { releaseNoteLock: 'notHeld' });
  assert.deepEqual(saved.submitted, ['cancel']);

  // An editor that no Edit attempt opened submits its cancel as before.
  const unnamed = classicEditor({ ownedAtRender: true });
  unnamed.context.init();
  assert.equal(unnamed.context.cancelLegacyNote(unnamed.form), true);
  assert.equal(unnamed.form.elements.method.value, 'cancel');
  assert.equal(unnamed.requests.length, 0);

  const fresh = classicEditor();
  fresh.context.init();
  assert.equal(fresh.note.readOnly, true);
  assert.equal(fresh.timers.length, 0);
  assert.equal(fresh.requests.length, 0);
});
