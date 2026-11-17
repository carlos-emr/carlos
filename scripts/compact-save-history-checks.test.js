/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');
const { assertCompactIdentity, assertSelectedLockAdvance, assertIssueHistory,
  assertNativeRelease, assertProtectedEditorState } = require('./lib/compact-save-history-checks');

const identity = { baseUrl: new URL('https://127.0.0.1/carlos'), patient: '101', provider: '800001', note: '52', token: 'owned-program-A' };
const native = (change = {}) => ({
  method: () => change.method || 'POST',
  url: () => change.url || `https://127.0.0.1/carlos/CaseManagementEntry?chartContext=${identity.token}`,
  postData: () => change.body || new URLSearchParams({ method: 'ajaxsave', nId: '52', demographicNo: '101',
    providerNo: '800001', noteTxt: 'H&P + exact 中文', ...change.fields }).toString(),
});
test('the real native compact payload keeps its selected note and exact text', () => {
  assert.equal(assertCompactIdentity(native(), identity).get('noteTxt'), 'H&P + exact 中文');
});
for (const [label, change] of [
  ['other verb', { method: 'GET' }],
  ['other origin', { url: 'https://other.invalid/carlos/CaseManagementEntry?chartContext=owned-program-A' }],
  ['other route', { url: 'https://127.0.0.1/carlos/Other?chartContext=owned-program-A' }],
  ['other selected note', { fields: { nId: '53' } }],
  ['other patient', { fields: { demographicNo: '202' } }],
  ['other provider', { fields: { providerNo: '800002' } }],
  ['missing text', { fields: { noteTxt: '' } }],
  ['mixed program context', { fields: { chartContext: 'other-program' } }],
]) test(`compact witness refuses ${label}`, () => assert.throws(() => assertCompactIdentity(native(change), identity)));

test('native retirement must release the committed revision in its original patient/program', () => {
  const request = native({ fields: { method: 'releaseNoteLock', noteId: '54' } });
  assert.doesNotThrow(() => assertNativeRelease(request, { ...identity, note: '54' }));
  for (const fields of [{ noteId: '52' }, { demographicNo: '202' }, { providerNo: '800002' }, { chartContext: 'other-program' }]) {
    assert.throws(() => assertNativeRelease(native({ fields: { method: 'releaseNoteLock', noteId: '54', ...fields } }),
      { ...identity, note: '54' }));
  }
});

const locks = [
  ['1', '800001', '101', '52', 'same-session', '2026-10-25 12:00:00', '127.0.0.1'],
  ['2', '800001', '101', '53', 'same-session', '2026-10-25 12:01:00', '127.0.0.1'],
];
const copied = () => locks.map(row => [...row]);
const advance = { selectedId: '1', otherId: '2', revision: '54' };
test('only A advances to its saved revision while every B lock field remains exact', () => {
  const after = copied(); after[0][3] = '54';
  assert.doesNotThrow(() => assertSelectedLockAdvance(locks, after, advance));
});
for (const [label, mutate] of [
  ['the original wrong-cache corruption', rows => { rows[1][3] = '54'; }],
  ['both locks advancing', rows => { rows[0][3] = '54'; rows[1][3] = '54'; }],
  ['another session', rows => { rows[0][3] = '54'; rows[1][4] = 'another-session'; }],
  ['lost lock', rows => { rows.splice(1, 1); }],
  ['rewritten acquisition time', rows => { rows[0][3] = '54'; rows[1][5] = '2026-10-25 12:02:00'; }],
]) test(`lock proof rejects ${label}`, () => {
  const after = copied(); mutate(after);
  assert.throws(() => assertSelectedLockAdvance(locks, after, advance));
});

const history = { status: 200, headers: { [h.APPLICATION_HEADER]: 'none', 'content-type': 'text/html;charset=UTF-8' },
  html: '<p>READABLE H&amp;P + 中文</p>', text: 'READABLE H&P + 中文' };
test('history requires readable cross-program text and excludes the hidden reporter role', () => {
  assert.doesNotThrow(() => assertIssueHistory(history, { status: 200, allowed: ['READABLE H&P + 中文'], forbidden: ['HIDDEN'] }));
});
for (const [label, result] of [
  ['hidden clinical body', { ...history, html: `${history.html} HIDDEN`, text: `${history.text} HIDDEN` }],
  ['missing cross-program note', { ...history, html: '<p>Empty</p>', text: 'Empty' }],
  ['unmarked proxy response', { ...history, headers: { 'content-type': 'text/html' } }],
  ['unexpected redirect', { ...history, status: 302 }],
  ['non-HTML success', { ...history, headers: { [h.APPLICATION_HEADER]: 'none', 'content-type': 'application/json' } }],
]) test(`history proof rejects ${label}`, () => assert.throws(() => assertIssueHistory(result,
  { status: 200, allowed: ['READABLE H&P + 中文'], forbidden: ['HIDDEN'] })));
test('history403 still fails if either raw HTML or decoded text exposes clinical content', () => {
  const refused = { status: 403, headers: { [h.APPLICATION_HEADER]: 'none' }, html: 'Refused', text: 'Refused' };
  assert.doesNotThrow(() => assertIssueHistory(refused, { status: 403, forbidden: ['HIDDEN'] }));
  assert.throws(() => assertIssueHistory({ ...refused, html: 'HIDDEN' }, { status: 403, forbidden: ['HIDDEN'] }));
  assert.throws(() => assertIssueHistory({ ...refused, text: 'HIDDEN' }, { status: 403, forbidden: ['HIDDEN'] }));
});

const patient = () => ({ notes: [], drafts: [], echart: [], measurements: [], noteIssues: [], demographic: [['101', 'FAKE']] });
const clinical = () => ({ patients: [patient(), patient()], locks: [], programs: [['7']], memberships: [['7', '800001']], policy: {} });
test('own revision and backup consumption do not authorize changing the other clinical domains', () => {
  const before = clinical(); const after = clinical();
  after.patients[0].notes.push(['54', 'owned revision']); after.patients[0].echart.push(['owned legacy text']);
  after.locks.push(['owned advanced lock']);
  assert.doesNotThrow(() => assertProtectedEditorState(before, after));
});
for (const [label, mutate] of [
  ['other patient note', state => state.patients[1].notes.push(['55', 'foreign mutation'])],
  ['other patient backup', state => state.patients[1].drafts.push(['other backup'])],
  ['unrelated measurement', state => state.patients[0].measurements.push(['77'])],
  ['historical issue link', state => state.patients[0].noteIssues.push(['3', '52'])],
  ['program membership', state => state.memberships.push(['8', 'other-role'])],
]) test(`protected snapshot rejects ${label}`, () => {
  const after = clinical(); mutate(after);
  assert.throws(() => assertProtectedEditorState(clinical(), after));
});

// Exercise both actual browser functions. The observer stops execution exactly
// after CarlosAjax receives the release, before unrelated layout teardown.
const chartJs = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const functionBody = (begin, end) => {
  const first = chartJs.indexOf(begin); const last = chartJs.indexOf(end, first + begin.length);
  assert(first >= 0 && last > first);
  return chartJs.slice(first, last).replace(/<%[\s\S]*?%>/g, '');
};
const retireSource = functionBody('function removeLock(', 'var noteIssuesChanged') + '\n'
  + functionBody('function finishChangeToView(', 'function completeChangeToView(');
for (const [label, textarea, parent, expected, saving] of [
  ['saved existing', 'caseNote_note52', 'n54', '54', true],
  ['saved new counter', 'caseNote_note01', 'n54', '54', true],
  ['unchanged persisted', 'caseNote_note52', 'n52', '52', false],
  ['new zero', 'caseNote_note0', 'n0', '0', false],
  ['new counter', 'caseNote_note01', 'n01', '0', false],
  ['legacy new decimal', 'caseNote_note0.1', 'n0.1', '0', false],
]) test(`actual chart teardown retires the canonical current lock for ${label}`, () => {
  const stop = {}; let sent;
  const context = vm.createContext({
    $: () => ({ parentNode: { id: parent } }), document: { getElementById: () => ({ querySelectorAll: () => [] }) },
    providerNo: '800001', demographicNo: '101', chartActionUrl: route => `/carlos${route}?chartContext=owned-program-A`,
    CarlosAjax: { request(url, options) { sent = { url, options }; throw stop; } },
  });
  vm.runInContext(retireSource, context);
  assert.throws(() => context.finishChangeToView(textarea, saving, 'text'), error => error === stop);
  assert.equal(sent.options.method, 'post');
  assert.equal(new URL(sent.url, 'https://127.0.0.1').searchParams.get('chartContext'), 'owned-program-A');
  const body = new URLSearchParams(sent.options.postBody);
  assert.equal(body.get('noteId'), expected);
  assert.equal(body.get('providerNo'), '800001');
  assert.equal(body.get('demographicNo'), '101');
  assert.equal(body.get('method'), 'releaseNoteLock');
});
test('an unacknowledged compact save never tears down or releases its recoverable editor', () => {
  const editor = { value: 'unsaved text', parentNode: { id: 'n52' } }; let finished = 0;
  const context = vm.createContext({
    $: () => editor, $F: () => editor.value, origCaseNote: 'before', origObservationDate: '', noteIssuesChanged: false,
    unsavedNoteWarning: 'Save first?', confirm: () => true, deferNoteSaveUntilIssues: () => false,
    ajaxSaveNote: (_div, _note, _text, complete) => complete(false), finishChangeToView: () => { finished++; },
  });
  vm.runInContext(functionBody('function changeToView(', 'function finishChangeToView('), context);
  assert.equal(context.changeToView('caseNote_note52', () => { finished++; }), false);
  assert.equal(finished, 0); assert.equal(editor.value, 'unsaved text');
});
