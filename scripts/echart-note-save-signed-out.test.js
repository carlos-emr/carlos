/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
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
  const hidden = (name, value) => ({ name, type: 'hidden', value, disabled: false });
  const editor = { name: 'caseNote_note', value: TYPED, disabled: false };
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
    editor1: editor, autosaveTime: { textContent: 'previous status' },
    notCPP: { update: html => { replaced.push(html); editor.value = ''; } },
  };
  const context = {
    caseNote: 'editor1', ctx: '/carlos', chartContext: '', demographicNo: '123', fullChart: 'false',
    caisiEnabled: false, savingNoteError: 'save failed', invalidObservationDateError: 'invalid date',
    sessionEndedNotSavedError: SIGNED_OUT,
    URL, AbortController, window: { location: { href: `${ORIGIN}/carlos/CaseManagementEntry?method=edit` } },
    setTimeout: (fn, ms) => { timers.push({ fn, ms }); return timers.length; },
    clearTimeout: id => { timers[id - 1].cleared = true; },
    document: { forms: { caseManagementEntryForm: entryForm, caseManagementViewForm: { method: {} } } },
    Form: { serialize: () => 'fixture=form' },
    CarlosAjax: { request: (url, options) => requests.push({ url, options }) },
    $: id => fields[id], $F: id => fields[id].value,
    alert: message => alerts.push(message),
    jQuery: () => ({ each() {} }),
    clearAutoSaveTimer: () => effects.push('pause autosave'), setTimer: () => effects.push('resume autosave'),
    destroyNoteIssueAutocomplete: () => effects.push('destroy autocomplete'),
    fetch: (url, options) => { fetches.push({ url, options }); return heartbeat(url, options); },
  };
  vm.createContext(context);
  vm.runInContext(chartActionUrlSource(source), context);
  vm.runInContext(slice('    var ajaxRequest;', '    function submitIssue('), context);
  vm.runInContext(slice('    function saveNoteAjax(', '    var changeIssueMsg;'), context);
  const submits = () => effects.filter(effect => effect.submit).map(effect => effect.submit);
  return { context, requests, alerts, effects, fetches, replaced, fields, editor, toolbar, entryForm, timers, submits };
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

test('Save refused for another reason still names the status', () => {
  const h = harness();
  h.context.saveNoteAjax('save', 'list');
  h.requests[0].options.onFailure({ status: 500 });
  assert.deepEqual(h.alerts, ['save failed 500']);
});

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
  // Ctrl+B sets Sign and Bill, then asks for Sign & Save.
  h.entryForm.elements.sign.value = 'on';
  h.entryForm.elements.toBill.value = 'true';
  assert.equal(h.context.savePage('saveAndExit', ''), false);
  assert.equal(h.fetches.length, 1, 'no second check');
  assert.deepEqual(h.alerts, [h.context.noteSavingMessage]);
  answer({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: true }) });
  await settled();
  assert.deepEqual(h.submits().map(fields => [fields.sign, fields.verify, fields.toBill]), [['on', 'off', 'false']],
    'the submission carries the choices made when Sign & Save was chosen');
});

for (const [name, heartbeat] of [
  ['redirected to the sign-in page', () => Promise.resolve({ type: 'opaqueredirect', status: 0, ok: false })],
  ['refused with 401', () => Promise.resolve({ type: 'basic', status: 401, ok: false })],
  ['refused with 403', () => Promise.resolve({ type: 'basic', status: 403, ok: false })],
  ['answered as no longer valid', () => Promise.resolve({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: false }) })],
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
    assert.deepEqual(h.alerts, [SIGNED_OUT]);
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
