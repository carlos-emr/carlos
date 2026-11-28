/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
// Encounter note Save and Sign & Save once the sign-in has ended (#5868). After the
// inactivity limit the login filter redirects every request; the browser follows the
// redirect to the sign-in page as an ordinary HTTP 200. Save must not put that page in
// place of the editor (a redirect to facility selection is refused the same way), and
// Sign & Save (a full form submission) must not leave the chart at all: it asks the
// session heartbeat first. Both keep the typed text and say to sign in again. Runs the
// production functions from newCaseManagementView.js.jsp.
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { chartActionUrlSource } = require('./lib/chart-action-url-source');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const ORIGIN = 'https://emr.example.invalid';
const SIGNED_OUT = 'not saved; sign in again';
const FACILITY_PENDING = 'not saved; finish choosing a facility';
const TYPED = 'FAKE typed encounter note';

function slice(from, to) {
  const start = source.indexOf(from);
  const end = source.indexOf(to, start);
  assert.ok(start > 0 && end > start, `${from.trim()} is followed by ${to.trim()}`);
  return source.slice(start, end);
}

const valid = () => Promise.resolve({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: true }) });

/**
 * The Save and Sign & Save functions with the page objects they use, over a FAKE note.
 * Replacing #notCPP or submitting the form loses the editor's text, as in the browser.
 */
function harness(heartbeat = valid) {
  const requests = []; const alerts = []; const effects = []; const fetches = []; const replaced = []; const timers = [];
  let keydown;
  const hidden = (name, value) => ({ name, type: 'hidden', value, disabled: false });
  const editor = { name: 'caseNote_note', value: TYPED, disabled: false, focus() {} };
  const elements = [editor, hidden('sign', 'off'), hidden('verify', 'off'), hidden('toBill', 'false')];
  for (const element of elements) elements[element.name] = element;
  // The toolbar (Save, Sign & Save, Sign, Bill, Attach) is <input type="image">, which the
  // browser leaves out of form.elements.
  const toolbar = ['saveImg', 'signSaveImg', 'signVerifyImg', 'billImg', 'attachImg']
    .map(id => ({ id, type: 'image', disabled: false }));
  const entryForm = {
    method: {}, ajax: {}, chain: {}, includeIssue: {}, elements, attrs: {},
    querySelectorAll: selector => (selector === 'input[type="image"]' ? toolbar : []),
    get sign() { return elements.sign; }, get verify() { return elements.verify; }, get toBill() { return elements.toBill; },
    setAttribute(name, value) { this.attrs[name] = value; },
    removeAttribute(name) { delete this.attrs[name]; },
    submit() {
      effects.push({ submit: Object.fromEntries(elements.filter(e => !e.disabled).map(e => [e.name, e.value])) });
      editor.value = '';
    },
  };
  const fields = {
    editor1: editor, autosaveTime: { textContent: 'previous status', setAttribute(name, value) { this[name] = value; } },
    notCPP: { update: html => { replaced.push(html); editor.value = ''; } },
  };
  const context = {
    caseNote: 'editor1', ctx: '/carlos', chartContext: '', demographicNo: '123', fullChart: 'false',
    caisiEnabled: false, savingNoteError: 'save failed', invalidObservationDateError: 'invalid date',
    sessionEndedNotSavedError: SIGNED_OUT, facilitySelectionPendingNotSavedError: FACILITY_PENDING,
    lostNoteLock: false,
    draftSaveConflictMessage: 'Draft conflict',
    noteLockLostError: 'Your note lock has been lost; copy your text and reopen the chart.',
    // The notes-pane check a saved response passes (it carries #encMainDiv).
    sessionExpiredError: 'session expired',
    DOMParser: class { parseFromString(text) { return { getElementById: id => (text.includes(`id="${id}"`) ? {} : null) }; } },
    URL, AbortController, window: { location: { href: `${ORIGIN}/carlos/CaseManagementEntry?method=edit` } },
    setTimeout: (fn, ms) => { timers.push({ fn, ms }); return timers.length; },
    clearTimeout: id => { timers[id - 1].cleared = true; },
    document: {
      forms: { caseManagementEntryForm: entryForm, caseManagementViewForm: { method: {} } },
      getElementById: id => fields[id],
    },
    adjustCaseNote() {}, setCaretPosition() {},
    Form: { serialize: () => 'fixture=form' },
    CarlosAjax: { request: (url, options) => requests.push({ url, options }) },
    $: id => fields[id], $F: id => fields[id].value,
    alert: message => alerts.push(message),
    jQuery: () => ({ each() {}, on(event, handler) { assert.equal(event, 'keydown'); keydown = handler; } }),
    clearAutoSaveTimer: () => effects.push('pause autosave'), setTimer: () => effects.push('resume autosave'),
    destroyNoteIssueAutocomplete: () => effects.push('destroy autocomplete'),
    fetch: (url, options) => { fetches.push({ url, options }); return heartbeat(url, options); },
  };
  vm.createContext(context);
  vm.runInContext(chartActionUrlSource(source), context);
  vm.runInContext(slice('    var ajaxRequest;', '    function submitIssue('), context);
  vm.runInContext(slice('function showAutosaveStatus(', 'function autosaveAcknowledged('), context);
  vm.runInContext(slice('    // Text a template or a measurement window adds', '    var insertTemplateError;'), context);
  vm.runInContext(slice('    function saveNoteAjax(', '    var changeIssueMsg;'), context);
  const shortcut = source.match(/jQuery\(document\)\.on\('keydown', function\(e\) \{[\s\S]*?\n        \}\);/);
  assert.ok(shortcut, 'the production keyboard shortcut is present');
  vm.runInContext(shortcut[0], context);
  const pressBill = () => keydown({ ctrlKey: true, shiftKey: false, altKey: false, key: 'b', preventDefault() {} });
  const submits = () => effects.filter(effect => effect.submit).map(effect => effect.submit);
  context.sessionEndedNotSavedError = SIGNED_OUT;
  context.facilitySelectionPendingNotSavedError = FACILITY_PENDING;
  return { context, requests, alerts, effects, fetches, replaced, fields, editor, toolbar, entryForm, timers, submits, pressBill };
}

const settled = () => new Promise(resolve => setImmediate(resolve));

for (const [name, landing] of [['the sign-in page', '/carlos/logoutPage'], ['facility selection', '/carlos/select_facility']]) {
  test(`Save redirected to ${name} keeps the editor and its text`, () => {
    const h = harness();
    assert.equal(h.context.saveNoteAjax('save', 'list'), false);
    assert.equal(h.requests.length, 1);
    assert.equal(h.editor.disabled, true);
    const response = { status: 200, responseText: '<form id="loginForm">sign in</form>', responseURL: `${ORIGIN}${landing}` };
    h.requests[0].options.onSuccess(response);
    h.requests[0].options.onComplete(response);
    assert.deepEqual(h.replaced, [], 'the editor is not replaced by the sign-in page');
    assert.equal(h.editor.value, TYPED);
    assert.equal(h.editor.disabled, false, 'the editor controls come back');
    assert.deepEqual(h.alerts, [SIGNED_OUT]);
    assert.ok(h.effects.includes('resume autosave'));
    assert.ok(!h.effects.includes('destroy autocomplete'));
  });
}

test('Save refused with 401 keeps the editor and says to sign in again', () => {
  const h = harness();
  h.context.saveNoteAjax('save', 'list');
  h.requests[0].options.onFailure({ status: 401 });
  h.requests[0].options.onComplete({ status: 401 });
  assert.deepEqual(h.replaced, []);
  assert.equal(h.editor.value, TYPED);
  assert.deepEqual(h.alerts, [SIGNED_OUT]);
  assert.equal(h.editor.disabled, false);
});

for (const signed of [false, true]) {
  test(`Save with ${signed ? 'a signing choice' : 'no signing choice'} explains a lost lock and stops autosave`, () => {
    const h = harness();
    if (signed) h.entryForm.elements.sign.value = 'on';
    h.context.saveNoteAjax('save', 'list');
    const conflict = { status: 409, getResponseHeader: name => name === 'X-Carlos-Note-Lock-Lost' ? 'true' : null };
    h.requests[0].options.onFailure(conflict);
    h.requests[0].options.onComplete({ status: 409 });
    assert.equal(h.context.lostNoteLock, true);
    assert.deepEqual(h.alerts, [h.context.noteLockLostError]);
    assert.equal(h.editor.value, TYPED);
    assert.equal(h.editor.disabled, false);
    assert.deepEqual(h.replaced, []);
    assert.equal(h.effects.includes('resume autosave'), false);
    assert.equal(h.fields.autosaveTime['data-autosave-state'], 'conflict');
    assert.equal(h.fields.autosaveTime.textContent, h.context.draftSaveConflictMessage);
    assert.equal(h.context.saveNoteAjax('save', 'list'), false);
    assert.equal(h.requests.length, 1, 'Another toolbar click must keep the draft without a second POST');
  });
}

test('Save refused for another reason still names the status', () => {
  for (const status of [409, 500]) {
    const h = harness();
    h.context.saveNoteAjax('save', 'list');
    h.requests[0].options.onFailure({ status, getResponseHeader: () => null });
    assert.deepEqual(h.alerts, ['save failed ' + status]);
    assert.equal(h.context.lostNoteLock, false);
    assert.ok(h.effects.includes('resume autosave'));
  }
});

for (const rejected of [
  { status: 200, responseURL: `${ORIGIN}/carlos/logoutPage` },
  { status: 401 },
]) {
  test(`Ctrl+B during a plain Save does not leave signing choices after a ${rejected.status} rejection`, () => {
    const h = harness();
    h.context.saveNoteAjax('save', 'list');
    h.pressBill();
    assert.equal(h.requests.length, 1);
    assert.equal(h.fetches.length, 0);
    const request = h.requests[0];
    request.options[rejected.status === 200 ? 'onSuccess' : 'onFailure'](rejected);
    request.options.onComplete(rejected);
    assert.equal(h.editor.value, TYPED);
    assert.deepEqual(['sign', 'verify', 'toBill'].map(name => h.entryForm.elements[name].value), ['off', 'off', 'false']);
    h.context.Form.serialize = form => new URLSearchParams(Object.fromEntries(
      form.elements.map(element => [element.name, element.value]))).toString();
    h.context.saveNoteAjax('save', 'list');
    const retry = new URLSearchParams(h.requests[1].options.postBody);
    assert.equal(retry.get('sign'), 'off');
    assert.equal(retry.get('toBill'), 'false');
  });
}

for (const [name, responseURL] of [
  ['the save action itself (a server-side forward to the chart)', 'action'],
  ['a browser that reports no final URL', ''],
]) {
  test(`Save answered by ${name} replaces the chart as before`, () => {
    const h = harness();
    h.context.saveNoteAjax('save', 'list');
    const action = new URL(h.requests[0].url, h.context.window.location.href).href;
    const response = { status: 200, responseText: '<div id="encMainDiv">saved chart</div>',
      responseURL: responseURL === 'action' ? action : responseURL };
    h.requests[0].options.onSuccess(response);
    h.requests[0].options.onComplete(response);
    assert.deepEqual(h.replaced, ['<div id="encMainDiv">saved chart</div>']);
    assert.deepEqual(h.alerts, []);
  });
}

/** Sign & Save as the Sign button does it: mark the note signed, then save the page. */
function signAndSave(h) {
  h.entryForm.elements.sign.value = 'on';
  return h.context.savePage('saveAndExit', '');
}

const chartNotes = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotes.jsp'), 'utf8');
const fullFormHandlers = [...chartNotes.matchAll(/onclick="([^"]*savePage\('saveAndExit', ''\)[^"]*)"/g)]
  .map(match => match[1]);
assert.equal(fullFormHandlers.length, 3, 'the three JSP toolbar handlers are present');

for (const [index, label, expected] of [
  [0, 'Sign & Save', ['on', 'off', 'false']],
  [1, 'Verify', ['on', 'on', 'false']],
  [2, 'Bill', ['on', 'off', 'true']],
]) {
  test(`the rendered ${label} handler refuses a lost lock and clears its flags`, async () => {
    const flags = ['sign', 'verify', 'toBill'];
    const clickToolbar = h => vm.runInContext(`(function (event) { ${fullFormHandlers[index]} })`, h.context)({
      preventDefault() {}, stopPropagation() {}
    });
    const blocked = harness();
    blocked.context.lostNoteLock = true;
    assert.equal(clickToolbar(blocked), false);
    assert.equal(blocked.fetches.length, 0);
    assert.deepEqual(blocked.submits(), []);
    assert.equal(blocked.editor.value, TYPED);
    assert.deepEqual(blocked.alerts, [blocked.context.noteLockLostError]);
    assert.deepEqual(flags.map(flag => blocked.entryForm.elements[flag].value), ['off', 'off', 'false']);

    const allowed = harness();
    assert.equal(clickToolbar(allowed), false);
    await settled();
    assert.deepEqual(allowed.submits().map(fields => flags.map(flag => fields[flag])), [expected]);
  });
}

function useFormSerializer(h) {
  h.context.Form.serialize = form => new URLSearchParams(Object.fromEntries(
    form.elements.map(element => [element.name, element.value]))).toString();
}

for (const [index, label] of ['Sign & Save', 'Verify', 'Bill'].entries()) {
  test(label + ' rejected during date validation leaves a later plain Save unsigned', () => {
    const h = harness();
    useFormSerializer(h);
    h.fields.observationDate = {value: '2099-01-01'};
    h.context.validDate = () => false;
    h.context.pastObservationDateError = 'Observation date is in the future';
    vm.runInContext(`(function (event) { ${fullFormHandlers[index]} })`, h.context)({
      preventDefault() {}, stopPropagation() {}
    });
    assert.deepEqual(h.alerts, [h.context.pastObservationDateError]);
    assert.equal(h.fetches.length, 0);
    h.context.validDate = () => true;
    h.context.saveNoteAjax('save', 'list');
    const posted = new URLSearchParams(h.requests[0].options.postBody);
    assert.deepEqual(['sign', 'verify', 'toBill'].map(name => posted.get(name)), ['off', 'off', 'false']);
  });
}

test('an issue refresh keeps the signing choice only for the queued save that made it', async () => {
  for (const replaceWithPlainSave of [true, false]) {
    const h = harness();
    useFormSerializer(h);
    const finish = h.context.beginIssueUpdate();
    h.pressBill();
    if (replaceWithPlainSave) h.context.saveNoteAjax('save', 'list');
    finish(true);
    await settled();
    if (replaceWithPlainSave) {
      const posted = new URLSearchParams(h.requests[0].options.postBody);
      assert.deepEqual(['sign', 'toBill'].map(name => posted.get(name)), ['off', 'false']);
      assert.deepEqual(h.submits(), []);
    } else {
      assert.deepEqual(h.submits().map(fields => [fields.sign, fields.toBill]), [['on', 'true']]);
    }
  }
});

test('Sign & Save asks the session heartbeat without following a redirect, then submits once', async () => {
  const h = harness();
  assert.equal(signAndSave(h), false);
  assert.deepEqual(h.submits(), [], 'nothing is submitted before the answer');
  await settled();
  assert.equal(h.fetches.length, 1);
  assert.equal(h.fetches[0].url, '/carlos/status/SessionHeartbeat?autoRefresh=true');
  assert.equal(h.fetches[0].options.redirect, 'manual');
  assert.equal(h.fetches[0].options.credentials, 'same-origin');
  assert.ok(h.fetches[0].options.signal, 'the check can be cut short');
  assert.deepEqual(h.submits().map(fields => [fields.caseNote_note, fields.sign]), [[TYPED, 'on']],
    'the submission carries every control, re-enabled, with the typed note');
  assert.equal(h.entryForm.method.value, 'saveAndExit');
  assert.equal(h.timers[0].cleared, true);
  assert.deepEqual(h.alerts, []);
});

test('while the sign-in check runs, autosave is paused and the note controls and toolbar wait', async () => {
  let answer;
  const h = harness(() => new Promise(resolve => { answer = resolve; }));
  signAndSave(h);
  assert.equal(h.effects[0], 'pause autosave');
  assert.equal(h.editor.disabled, true);
  assert.deepEqual(h.toolbar.filter(button => !button.disabled).map(button => button.id), [],
    'every toolbar image button is held');
  assert.equal(h.fields.autosaveTime.textContent, h.context.noteSavingMessage);
  assert.equal(h.context.saveNoteAjax('save', 'list'), false);
  assert.equal(h.requests.length, 0, 'no Save request during the check');
  assert.deepEqual(h.alerts, [h.context.noteSavingMessage], 'Save during the check says the note is saving');
  answer({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: true }) });
  await settled();
  assert.equal(h.submits().length, 1);
  assert.equal(h.fields.autosaveTime.textContent, 'previous status');
  assert.deepEqual(h.toolbar.filter(button => button.disabled).map(button => button.id), [], 'the toolbar comes back');
});

test('Bill pressed during the check (Ctrl+B) starts nothing and does not change the pending submission', async () => {
  let answer;
  const h = harness(() => new Promise(resolve => { answer = resolve; }));
  signAndSave(h);
  h.pressBill();
  assert.equal(h.fetches.length, 1, 'no second check');
  assert.deepEqual(h.alerts, [h.context.noteSavingMessage]);
  answer({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: true }) });
  await settled();
  assert.deepEqual(h.submits().map(fields => [fields.sign, fields.verify, fields.toBill]), [['on', 'off', 'false']],
    'the submission carries the choices made when Sign & Save was chosen');
});

// A live sign-in still choosing its facility is told to finish that, not that it expired.
for (const [name, heartbeat, message] of [
  ['redirected to the sign-in page', () => Promise.resolve({ type: 'opaqueredirect', status: 0, ok: false }), SIGNED_OUT],
  ['refused with 401', () => Promise.resolve({ type: 'basic', status: 401, ok: false }), SIGNED_OUT],
  ['refused with 403', () => Promise.resolve({ type: 'basic', status: 403, ok: false }), SIGNED_OUT],
  ['answered as no longer valid', () => Promise.resolve({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: false }) }), SIGNED_OUT],
  ['answered as no longer valid even with the facility flag', () => Promise.resolve({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: false, facilitySelectionPending: true }) }), SIGNED_OUT],
  ['answered as signed in with a facility still to choose', () => Promise.resolve({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: true, facilitySelectionPending: true }) }), FACILITY_PENDING],
]) {
  test(`Sign & Save with the heartbeat ${name} submits nothing and keeps the note`, async () => {
    const h = harness(heartbeat);
    h.entryForm.elements.verify.value = 'on';
    h.entryForm.elements.toBill.value = 'true';
    signAndSave(h);
    await settled();
    assert.deepEqual(h.submits(), []);
    assert.equal(h.editor.value, TYPED);
    assert.equal(h.editor.disabled, false, 'the note controls come back');
    assert.deepEqual(h.alerts, [message]);
    assert.deepEqual(['sign', 'verify', 'toBill'].map(name => h.entryForm.elements[name].value), ['off', 'off', 'false'],
      'a later plain Save does not sign, verify or bill');
    assert.deepEqual(h.effects, ['pause autosave', 'resume autosave']);
    signAndSave(h);
    await settled();
    assert.equal(h.fetches.length, 2, 'a later click checks again');
  });
}

for (const [name, heartbeat] of [
  ['cannot be reached', () => Promise.reject(new Error('offline'))],
  ['answers with a server error', () => Promise.resolve({ type: 'basic', status: 503, ok: false })],
  ['answers with a page that is not JSON', () => Promise.resolve({ type: 'basic', status: 200, ok: true, json: async () => { throw new SyntaxError('not JSON'); } })],
]) {
  test(`Sign & Save still submits when the heartbeat ${name}`, async () => {
    const h = harness(heartbeat);
    signAndSave(h);
    await settled();
    assert.equal(h.submits().length, 1);
    assert.deepEqual(h.alerts, []);
  });
}

for (const [name, change] of [
  ['the browser has no AbortController', h => { delete h.context.AbortController; }],
  ['fetch throws before sending', h => { h.context.fetch = () => { throw new TypeError('blocked'); }; }],
]) {
  test(`Sign & Save submits as before when ${name}`, async () => {
    const h = harness(() => new Promise(() => {}));
    change(h);
    signAndSave(h);
    await settled();
    if (!h.submits().length) {
      assert.equal(h.timers.length, 1, 'the time limit still applies');
      h.timers[0].fn();
      await settled();
    }
    assert.equal(h.submits().length, 1);
    assert.equal(h.editor.disabled, false);
    assert.deepEqual(h.alerts, []);
  });
}

test('Sign & Save submits as before when the heartbeat gives no answer within the time limit', async () => {
  const h = harness((url, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new Error('aborted')));
  }));
  signAndSave(h);
  await settled();
  assert.deepEqual(h.submits(), []);
  assert.equal(h.timers.length, 1);
  assert.ok(h.timers[0].ms >= 5000 && h.timers[0].ms <= 10000, `time limit ${h.timers[0].ms} ms`);
  h.timers[0].fn();
  await settled();
  assert.equal(h.submits().length, 1);
  assert.deepEqual(h.alerts, []);
});


for (const changed of ['text', 'date', 'issues']) {
  for (const discard of [false, true]) {
    test(`Close after lock loss prompts before discarding ${changed} changes (${discard ? 'discard' : 'cancel'})`, () => {
      const h = harness(); let prompts = 0; let closed = 0;
      vm.runInContext(slice('    var closeWithoutSaveMsg;', '    // Bind each newly rendered editor'), h.context);
      Object.assign(h.context, {lostNoteLock: true, origCaseNote: changed === 'text' ? 'previous text' : TYPED,
        origObservationDate: changed === 'date' ? 'previous date' : 'today', noteIssuesChanged: changed === 'issues',
        closeWithoutSaveMsg: 'Discard unsaved changes?', Event: {stop() {}},
        confirm: message => {prompts++; assert.equal(message, 'Discard unsaved changes?'); return discard;}});
      h.fields.observationDate = {value: 'today'};
      h.context.window.close = () => {closed++;};
      h.context.closeEnc({});
      assert.equal(prompts, 1);
      assert.equal(closed, discard ? 1 : 0);
      assert.equal(h.editor.value, TYPED);
      assert.equal(h.context.noteIssuesChanged, changed === 'issues');
      assert.equal(h.context.lostNoteLock, true);
      assert.equal(h.effects.includes('resume autosave'), false);
      assert.equal(h.effects.includes('pause autosave'), discard);
      assert.equal(h.requests.length, 0, 'Close must never try to write against a lost lock');
    });
  }
}

for (const editorPresent of [true, false]) {
  test(`Close after lock loss accepts a clean chart ${editorPresent ? 'with' : 'without'} an editor`, () => {
    const h = harness(); let prompts = 0; let closed = 0;
    vm.runInContext(slice('    var closeWithoutSaveMsg;', '    // Bind each newly rendered editor'), h.context);
    Object.assign(h.context, {lostNoteLock: true, origCaseNote: TYPED,
      origObservationDate: 'today', noteIssuesChanged: false, Event: {stop() {}},
      confirm: () => {prompts++; return true;}});
    h.fields.observationDate = {value: 'today'};
    if (!editorPresent) delete h.fields.editor1;
    h.context.window.close = () => {closed++;};
    h.context.closeEnc({});
    assert.equal(prompts, 0); assert.equal(closed, 1);
    assert.equal(h.context.lostNoteLock, true);
    assert.equal(h.requests.length, 0);
  });
}
const INSERTED = 'FAKE measurement text';

test('text inserted while the sign-in check runs is not signed: the note keeps it and asks for another look', async () => {
  let answer;
  const h = harness(() => new Promise(resolve => { answer = resolve; }));
  signAndSave(h);
  h.context.writeToEncounterNote({ responseText: INSERTED });
  assert.equal(h.context.pasteToEncounterNote('FAKE template text'), true, 'a paste during the check is accepted');
  assert.equal(h.editor.value, TYPED, 'nothing reaches the note while the check runs');
  answer({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: true }) });
  await settled();
  assert.deepEqual(h.submits(), [], 'nothing is signed');
  assert.ok(h.editor.value.startsWith(TYPED) && h.editor.value.includes(INSERTED)
    && h.editor.value.includes('FAKE template text'), 'the held text is in the note now');
  assert.deepEqual(h.alerts, [h.context.noteChangedDuringSignMessage]);
  assert.deepEqual(h.effects, ['pause autosave', 'resume autosave'], 'autosave resumes for the review');
  assert.deepEqual(['sign', 'verify', 'toBill'].map(name => h.entryForm.elements[name].value), ['off', 'off', 'false'],
    'a later plain Save does not sign, verify or bill');
  assert.equal(h.editor.disabled, false, 'the note controls come back');
  h.context.writeToEncounterNote({ responseText: ' later' });
  assert.ok(h.editor.value.endsWith(' later'), 'insertions apply at once after the check');
  signAndSave(h);
  answer({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: true }) });
  await settled();
  assert.equal(h.submits().length, 1, 'choosing Sign & Save again signs the reviewed note');
  assert.ok(h.submits()[0].caseNote_note.includes(INSERTED));
});

test('CPP text copied into the note while the sign-in check runs is held for review, then added', async () => {
  let answer;
  const h = harness(() => new Promise(resolve => { answer = resolve; }));
  vm.runInContext(slice('    function copyCppToCurrentNote(', '    function selectGroup('), h.context);
  const cppEditor = { value: 'FAKE CPP text' };
  const pageJQuery = h.context.jQuery;
  h.context.jQuery = selector => {
    if (selector === '#noteEditTxt') return { val: () => cppEditor.value };
    return pageJQuery(selector);
  };
  signAndSave(h);
  h.context.copyCppToCurrentNote();
  assert.equal(h.editor.value, TYPED, 'nothing reaches the note while the check runs');
  cppEditor.value = 'FAKE later CPP edit';
  answer({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: true }) });
  await settled();
  assert.deepEqual(h.submits(), [], 'nothing is signed');
  assert.equal(h.editor.value, `${TYPED}\nFAKE CPP text`, 'the text copied at the click is added');
  assert.deepEqual(h.alerts, [h.context.noteChangedDuringSignMessage]);
  assert.deepEqual(h.effects, ['pause autosave', 'resume autosave'], 'autosave resumes for the review');
  h.context.copyCppToCurrentNote();
  assert.ok(h.editor.value.endsWith('\nFAKE later CPP edit'), 'a copy after the check applies at once');
});

test('text inserted while a refused sign-in check runs is kept in the note', async () => {
  let answer;
  const h = harness(() => new Promise(resolve => { answer = resolve; }));
  signAndSave(h);
  h.context.writeToEncounterNote({ responseText: INSERTED });
  answer({ type: 'basic', status: 401, ok: false });
  await settled();
  assert.deepEqual(h.submits(), []);
  assert.ok(h.editor.value.includes(INSERTED));
  assert.deepEqual(h.alerts, [SIGNED_OUT]);
  assert.deepEqual(h.effects, ['pause autosave', 'resume autosave'], 'autosave resumes after the refusal');
});

test('text inserted while the sign-in check times out is held for review, and autosave resumes', async () => {
  const h = harness(() => new Promise(() => {}));
  signAndSave(h);
  h.context.writeToEncounterNote({ responseText: INSERTED });
  await settled();
  assert.equal(h.timers.length, 1, 'the time limit is running');
  h.timers[0].fn();
  await settled();
  assert.deepEqual(h.submits(), [], 'a timed-out check with held text signs nothing');
  assert.ok(h.editor.value.includes(INSERTED), 'the held text is in the note now');
  assert.deepEqual(h.alerts, [h.context.noteChangedDuringSignMessage]);
  assert.deepEqual(h.effects, ['pause autosave', 'resume autosave'], 'autosave resumes for the review');
  assert.equal(h.editor.disabled, false, 'the note controls come back');
});

test('both encounter layouts use the shared localized facility-selection message', () => {
  const layout = source;
  assert.match(layout, /key="encounter\.facilitySelectionPendingNotSaved\.msg"/);
  assert.match(layout, /facilitySelectionPendingNotSavedError = "\$\{carlos:forJavaScriptBlock\(facilitySelectionPendingNotSavedText\)\}"/);
  for (const locale of ['en', 'es', 'fr', 'pl', 'pt_BR']) {
    const bundle = fs.readFileSync(path.join(__dirname, `../src/main/resources/oscarResources_${locale}.properties`), 'latin1');
    assert.match(bundle, /^encounter\.facilitySelectionPendingNotSaved\.msg=\S/m, `${locale} has the message`);
  }
});

test('changing the note while issue updates are pending requires a fresh signing choice', () => {
  const h = harness();
  h.context.issueUpdatesPending = 1;
  signAndSave(h);
  h.editor.value += ' New text before heartbeat';
  h.context.issueUpdatesPending = 0;
  h.context.queuedNoteSave.save();
  assert.deepEqual(h.submits(), []);
  assert.equal(h.fetches.length, 0);
  assert.equal(h.entryForm.sign.value, 'off');
  assert.deepEqual(h.alerts, [h.context.noteChangedDuringSignMessage]);
});

test('direct changes to the editor during a successful heartbeat cannot be signed', async () => {
  const h = harness();
  signAndSave(h);
  h.editor.value += ' New text during heartbeat';
  await settled();
  assert.deepEqual(h.submits(), []);
  assert.equal(h.editor.disabled, false);
  assert.deepEqual(h.alerts, [h.context.noteChangedDuringSignMessage]);
});

test('Close keeps held clinical text until the heartbeat releases it', async () => {
  const h = harness();
  let closed = false;
  vm.runInContext(slice('    var closeWithoutSaveMsg;', '    // Bind each newly rendered editor'), h.context);
  h.context.Event = {stop() {}};
  h.context.window.close = () => { closed = true; };
  signAndSave(h);
  h.context.pasteToEncounterNote('Held clinical text');
  h.context.closeEnc({});
  assert.equal(closed, false);
  assert.deepEqual(h.alerts, [h.context.noteSavingMessage]);
  await settled();
  assert.ok(h.editor.value.endsWith('Held clinical text'));
  assert.deepEqual(h.submits(), []);
});
