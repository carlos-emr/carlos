/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Encounter note Save and Sign & Save once the sign-in has ended (#5868). After the
// inactivity limit, or with a facility still to be chosen, the login filter redirects every
// request; the browser follows the redirect to the sign-in page as an ordinary HTTP 200.
// Save must not put that page in place of the editor, and Sign & Save (a full form
// submission) must not leave the chart at all. Both keep the typed text and say to sign in
// again. Runs the production functions from newCaseManagementView.js.jsp.
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

/** The Save and Sign & Save functions with the page objects they use, over a FAKE note. */
function harness(heartbeat = () => Promise.resolve({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: true }) })) {
  const requests = []; const alerts = []; const effects = []; const fetches = []; const replaced = [];
  const control = { disabled: false };
  const entryForm = {
    method: {}, ajax: {}, chain: {}, includeIssue: {}, elements: [control], attrs: {},
    setAttribute(name, value) { this.attrs[name] = value; },
    removeAttribute(name) { delete this.attrs[name]; },
    submit() { effects.push('submit'); },
  };
  const fields = { editor1: { value: TYPED }, notCPP: { update: html => replaced.push(html) } };
  const context = {
    caseNote: 'editor1', ctx: '/carlos', chartContext: '', demographicNo: '123', fullChart: 'false',
    caisiEnabled: false, savingNoteError: 'save failed', invalidObservationDateError: 'invalid date',
    sessionEndedNotSavedError: SIGNED_OUT,
    URL, window: { location: { href: `${ORIGIN}/carlos/CaseManagementEntry?method=edit` } },
    document: { forms: { caseManagementEntryForm: entryForm, caseManagementViewForm: { method: {} } } },
    Form: { serialize: () => 'fixture=form' },
    CarlosAjax: { request: (url, options) => requests.push({ url, options }) },
    $: id => fields[id], $F: id => fields[id].value,
    alert: message => alerts.push(message),
    jQuery: () => ({ each() {} }),
    clearAutoSaveTimer: () => effects.push('pause autosave'), setTimer: () => effects.push('resume autosave'),
    destroyNoteIssueAutocomplete: () => effects.push('destroy autocomplete'),
    fetch: (url, options) => { fetches.push({ url, options }); return heartbeat(); },
  };
  vm.createContext(context);
  vm.runInContext(chartActionUrlSource(source), context);
  vm.runInContext(slice('    var ajaxRequest;', '    function submitIssue('), context);
  vm.runInContext(slice('    function saveNoteAjax(', '    var changeIssueMsg;'), context);
  return { context, requests, alerts, effects, fetches, replaced, fields, control, entryForm };
}

const settled = () => new Promise(resolve => setImmediate(resolve));

for (const [name, landing] of [['the sign-in page', '/carlos/logoutPage'], ['facility selection', '/carlos/select_facility']]) {
  test(`Save redirected to ${name} keeps the editor and its text`, () => {
    const h = harness();
    assert.equal(h.context.saveNoteAjax('save', 'list'), false);
    assert.equal(h.requests.length, 1);
    assert.equal(h.control.disabled, true);
    const response = { status: 200, responseText: '<form id="loginForm">sign in</form>', responseURL: `${ORIGIN}${landing}` };
    h.requests[0].options.onSuccess(response);
    h.requests[0].options.onComplete(response);
    assert.deepEqual(h.replaced, [], 'the editor is not replaced by the sign-in page');
    assert.equal(h.fields.editor1.value, TYPED);
    assert.equal(h.control.disabled, false, 'the editor controls come back');
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
  assert.deepEqual(h.alerts, [SIGNED_OUT]);
  assert.equal(h.control.disabled, false);
});

test('Save refused for another reason still names the status', () => {
  const h = harness();
  h.context.saveNoteAjax('save', 'list');
  h.requests[0].options.onFailure({ status: 500 });
  assert.deepEqual(h.alerts, ['save failed 500']);
});

test('Save answered by the save action itself replaces the chart as before', () => {
  const h = harness();
  h.context.saveNoteAjax('save', 'list');
  const action = new URL(h.requests[0].url, h.context.window.location.href);
  const response = { status: 200, responseText: '<div id="encMainDiv">saved chart</div>', responseURL: action.href };
  h.requests[0].options.onSuccess(response);
  h.requests[0].options.onComplete(response);
  assert.deepEqual(h.replaced, ['<div id="encMainDiv">saved chart</div>']);
  assert.deepEqual(h.alerts, []);
});

test('Sign & Save asks the session heartbeat without following a redirect, then submits once', async () => {
  const h = harness();
  assert.equal(h.context.savePage('saveAndExit', ''), false);
  assert.ok(!h.effects.includes('submit'), 'nothing is submitted before the answer');
  await settled();
  assert.equal(h.fetches.length, 1);
  assert.equal(h.fetches[0].url, '/carlos/status/SessionHeartbeat?autoRefresh=true');
  assert.equal(h.fetches[0].options.redirect, 'manual');
  assert.equal(h.fetches[0].options.credentials, 'same-origin');
  assert.deepEqual(h.effects.filter(effect => effect === 'submit'), ['submit']);
  assert.equal(h.entryForm.method.value, 'saveAndExit');
  assert.deepEqual(h.alerts, []);
});

for (const [name, heartbeat] of [
  ['redirected to the sign-in page', () => Promise.resolve({ type: 'opaqueredirect', status: 0, ok: false })],
  ['refused with 401', () => Promise.resolve({ type: 'basic', status: 401, ok: false })],
  ['answered as no longer valid', () => Promise.resolve({ type: 'basic', status: 200, ok: true, json: async () => ({ valid: false }) })],
]) {
  test(`Sign & Save with the heartbeat ${name} submits nothing and keeps the note`, async () => {
    const h = harness(heartbeat);
    h.context.savePage('saveAndExit', '');
    await settled();
    assert.ok(!h.effects.includes('submit'));
    assert.equal(h.fields.editor1.value, TYPED);
    assert.deepEqual(h.alerts, [SIGNED_OUT]);
    h.context.savePage('saveAndExit', '');
    await settled();
    assert.equal(h.fetches.length, 2, 'a later click checks again');
  });
}

for (const [name, heartbeat] of [
  ['cannot be reached', () => Promise.reject(new Error('offline'))],
  ['answers with a server error', () => Promise.resolve({ type: 'basic', status: 503, ok: false })],
]) {
  test(`Sign & Save still submits when the heartbeat ${name}`, async () => {
    const h = harness(heartbeat);
    h.context.savePage('saveAndExit', '');
    await settled();
    assert.deepEqual(h.effects.filter(effect => effect === 'submit'), ['submit']);
    assert.deepEqual(h.alerts, []);
  });
}

test('a second Sign & Save click while the check runs submits once', async () => {
  const h = harness();
  h.context.savePage('saveAndExit', '');
  h.context.savePage('saveAndExit', '');
  await settled();
  assert.equal(h.fetches.length, 1);
  assert.deepEqual(h.effects.filter(effect => effect === 'submit'), ['submit']);
});
