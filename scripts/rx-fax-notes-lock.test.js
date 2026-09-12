/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Execute the actual JSP's browser functions. No browser, server or duplicate
// implementation is needed to exercise the delayed-save/click/event ordering.
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp'), 'utf8');
test('Print and Paste initially disables reprints and missing previews without fax JavaScript', () => {
  const marker = jsp.indexOf('id="printPasteButton"');
  const start = jsp.lastIndexOf('<input ', marker);
  const button = jsp.slice(start, jsp.indexOf('/>', marker) + 2);
  assert.ok(marker > start && start >= 0);
  assert.equal((button.match(/<input\b/g) || []).length, 1);
  assert.ok(button);
  assert.match(button, /reprint\.equals\("true"\) \|\| !previewAvailable \? "disabled='true'"/);
});
function browserFunction(name, nextName) {
  const start = jsp.indexOf(`function ${name}(`);
  const end = jsp.indexOf(`function ${nextName}(`, start + 1);
  assert.ok(start >= 0 && end > start, `missing JSP function ${name}`);
  return jsp.slice(start, end)
    .replace(/<%--[\s\S]*?--%>/g, '')
    .replace(/<%[\s\S]*?%>/g, '1')
    .replace(/<carlos:encode\b[^>]*\/>/g, '1');
}

function setup(priorReadOnly = false, priorSaveDisabled = false) {
  const notes = { value: 'note at fax click', readOnly: priorReadOnly, disabled: false };
  const save = { disabled: priorSaveDisabled };
  const previewForm = { elements: { rx_no_newlines: { value: 'prescription' } } };
  const elements = {
    additionalNotes: notes,
    saveAdditionalNotes: save,
    faxButton: { disabled: false },
    faxPasteButton: { disabled: false },
    printPasteButton: { disabled: false },
    faxPasteRetryRow: { style: { display: 'none' } },
    faxPasteRetryButton: { disabled: false },
    faxPasteUncertain: { hidden: true },
    faxPasteRecoveryText: { value: '' },
    faxSubmissionUncertain: { hidden: true },
    faxSubmissionRecoveryText: { hidden: true, value: '' },
    faxPreviewChanged: { hidden: true },
    faxNumber: { selectedIndex: 0, options: [{ value: '5555555555' }] },
    preview: { contentWindow: { document: { getElementById: () => previewForm } } },
  };
  const previewElements = { finalFax: { value: '' }, additNotes: { style: {} } };
  const savedBodies = [];
  const deferredTimeouts = [];
  const clearedTimeouts = [];
  const closedWindows = [];
  const modalHandlers = new Map();
  const unloadHandlers = [];
  const modal = {
    addEventListener: (event, handler) => modalHandlers.set(event, handler),
    removeEventListener: (event) => modalHandlers.delete(event),
  };
  let releaseSave;
  const saveGate = new Promise((resolve) => { releaseSave = resolve; });
  const context = vm.createContext({
    console,
    document: { getElementById: (id) => elements[id] || null },
    frames: { preview: { document: {
      getElementById: (id) => previewElements[id],
      getElementsByName: () => [{ value: '' }],
    } } },
    window: { onbeforeunload: () => 'unsaved', top: { close: () => { closedWindows.push(true); } },
      addEventListener: (_event, handler) => unloadHandlers.push(handler) },
    parent: { document: { getElementById: () => modal } },
    setTimeout: (fn) => { deferredTimeouts.push(fn); return deferredTimeouts.length; },
    clearTimeout: (id) => { clearedTimeouts.push(id); },
    faxScriptNo: '12345',
    faxSubmissionPending: false,
    faxSubmissionUncertain: false,
    faxPreviewReloading: false,
    faxNotesState: null,
    pendingNotesSave: Promise.resolve(),
    pendingFaxCancellation: null,
    faxQueued: false,
    faxPasteRetryText: null,
    lastFaxPasteText: null,
    faxPasteCanRetry: false,
    faxPasteRetryPending: false,
    hasPreview: true,
    isReprint: false,
    hasFaxNumber: true,
    hasFaxSenderAccount: true,
    canFaxScript: true,
    isSignatureSaved: true,
    hasStoredSignature: false,
    getCsrfToken: () => 'fixture-token',
    fetch: async (_url, options) => {
      savedBodies.push(options.body);
      await saveGate;
      return { ok: true };
    },
  });
  for (const [name, next] of [
    ['cancelPendingFax', 'bindFaxModalCancellation'],
    ['bindFaxModalCancellation', 'onPrint2'],
    ['addNotes', 'printIframe'],
    ['lockFaxNotes', 'unlockFaxNotes'],
    ['unlockFaxNotes', 'setFaxControlsDisabled'],
    ['setFaxControlsDisabled', 'shouldDisableFaxControls'],
    ['shouldDisableFaxControls', 'resetFailedFaxSubmission'],
    ['resetFailedFaxSubmission', 'enterFaxPasteRecovery'],
    ['enterFaxPasteRecovery', 'retryFaxPaste'],
    ['retryFaxPaste', 'refreshImage'],
    ['sendFax', 'unloadMess'],
  ]) vm.runInContext(browserFunction(name, next), context);
  return { context, notes, save, elements, savedBodies, releaseSave, deferredTimeouts, clearedTimeouts, closedWindows,
    modalHandlers, unloadHandlers };
}

for (const previewState of ['absent-frame', 'absent-document', 'absent-elements', 'inaccessible']) {
  test(`notes still save when preview is ${previewState}`, async () => {
    const fixture = setup();
    const { context, notes, savedBodies, releaseSave } = fixture;
    notes.value = 'exact note\nwith an apostrophe and &';
    if (previewState === 'absent-frame') delete context.frames.preview;
    if (previewState === 'absent-document') context.frames.preview.document = null;
    if (previewState === 'absent-elements') context.frames.preview.document = {
      getElementById: () => null, getElementsByName: () => [],
    };
    if (previewState === 'inaccessible') Object.defineProperty(context.frames.preview, 'document', {
      get() { throw new Error('cross-origin preview'); },
    });
    try {
      assert.doesNotThrow(() => context.addNotes());
    } finally {
      releaseSave();
      await context.pendingNotesSave;
    }
    assert.equal(savedBodies.length, 1);
    assert.equal(new URLSearchParams(savedBodies[0]).get('comment'), notes.value);
  });
}

for (const pasteAfterSuccess of [false, true]) {
  test(`pending ${pasteAfterSuccess ? 'Fax & Paste' : 'Fax'} blocks later note writes`, async () => {
    const fixture = setup();
    const { context, notes, save, savedBodies, releaseSave } = fixture;
    let submitted;
    context.onPrint2 = (_method, _script, _pdf, paste, capturedText) => {
      submitted = context.pendingNotesSave.then(() => ({ paste, capturedText, note: notes.value }));
    };
    context.addNotes(); // textarea blur happens before the Fax click
    assert.equal(context.sendFax(pasteAfterSuccess), true);
    assert.equal(notes.readOnly, true);
    assert.equal(save.disabled, true);
    const pendingSave = context.pendingNotesSave;
    notes.value = 'late edit that must not reach the prescription';
    assert.equal(context.addNotes(), false); // queued event/direct handler call
    assert.equal(context.pendingNotesSave, pendingSave);
    assert.equal(notes.value, 'note at fax click');
    assert.equal(context.sendFax(pasteAfterSuccess), false);
    releaseSave();
    const result = await submitted;
    assert.equal(savedBodies.length, 1);
    assert.equal(new URLSearchParams(savedBodies[0]).get('comment'), 'note at fax click');
    assert.equal(result.note, 'note at fax click');
    assert.equal(result.paste, pasteAfterSuccess);
    assert.equal(result.capturedText, pasteAfterSuccess ? 'prescription\nnote at fax click\n' : null);
    assert.equal(notes.readOnly, true); // retain the queued document state on success
  });
}

for (const priorReadOnly of [false, true]) {
  test(`failed fax restores prior note controls (readOnly=${priorReadOnly})`, () => {
    const { context, notes, save } = setup(priorReadOnly, priorReadOnly);
    const unload = context.window.onbeforeunload;
    context.onPrint2 = () => { throw new Error('fixture submission failure'); };
    assert.throws(() => context.sendFax(false), /fixture submission failure/);
    assert.equal(context.faxSubmissionPending, false);
    assert.equal(context.faxNotesState, null);
    assert.equal(notes.readOnly, priorReadOnly);
    assert.equal(notes.disabled, false);
    assert.equal(save.disabled, priorReadOnly);
    assert.equal(context.window.onbeforeunload, unload);
  });
}

test('queued fax with a failed encounter paste offers a retry and refuses a second fax', async () => {
  const { context, notes, elements, deferredTimeouts, closedWindows } = setup();
  context.onPrint2 = () => {};
  assert.equal(context.sendFax(true), true);

  // Fax accepted by the server, encounter write rejected.
  const queuedFaxText = '[Rx faxed to Example Pharmacy Fax#: 5555555555 prescribed by Dr Example, 11-Sep-2026 01:23 PM]\nprescription\nnote at fax click\n';
  context.faxPasteCanRetry = true; // explicit rejection before any encounter write
  context.enterFaxPasteRecovery(queuedFaxText);

  assert.equal(context.faxSubmissionPending, false); // no longer stuck mid-submission
  assert.equal(context.faxQueued, true);
  assert.equal(elements.faxPasteRetryRow.style.display, '');
  assert.equal(elements.faxPasteRetryButton.disabled, false);
  assert.equal(elements.faxButton.disabled, true); // the pharmacy already has this script
  assert.equal(elements.faxPasteButton.disabled, true);
  assert.equal(context.sendFax(true), false);
  assert.equal(notes.readOnly, true); // still holds exactly what was faxed, selectable to copy
  notes.value = 'edit after the fax went out';
  assert.equal(context.addNotes(), false);
  assert.equal(notes.value, 'note at fax click');

  // A retry that fails again leaves the recovery path available.
  const pasteAttempts = [];
  context.printPaste2Parent = (print, fax, pasteRx, text, useAsIs) => {
    pasteAttempts.push({ print, fax, pasteRx, text, useAsIs });
    return Promise.resolve(pasteAttempts.length > 1);
  };
  assert.equal(context.retryFaxPaste(), true);
  assert.equal(context.retryFaxPaste(), false); // do not dispatch concurrent appends
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(pasteAttempts, [
    { print: false, fax: true, pasteRx: true, text: queuedFaxText, useAsIs: true },
  ]);
  assert.equal(elements.faxPasteRetryButton.disabled, false);
  assert.equal(elements.faxPasteRetryRow.style.display, '');
  assert.equal(closedWindows.length, 0);

  // A successful retry pastes the faxed text, hides the retry and closes the window.
  assert.equal(context.retryFaxPaste(), true);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(pasteAttempts.length, 2);
  assert.equal(pasteAttempts[1].text, queuedFaxText);
  assert.equal(pasteAttempts[1].useAsIs, true);
  assert.equal(context.faxPasteRetryText, null);
  assert.equal(elements.faxPasteRetryRow.style.display, 'none');
  assert.equal(context.retryFaxPaste(), false); // nothing left to retry
  assert.equal(deferredTimeouts.length, 1);
  deferredTimeouts[0]();
  assert.equal(closedWindows.length, 1);
});

test('uncertain encounter outcome preserves text but cannot retry or resend', () => {
  const { context, elements } = setup();
  context.faxPasteCanRetry = false;
  context.enterFaxPasteRecovery('exact queued fax text');
  assert.equal(elements.faxPasteRetryButton.disabled, true);
  assert.equal(elements.faxPasteUncertain.hidden, false);
  assert.equal(elements.faxPasteRecoveryText.value, 'exact queued fax text');
  assert.equal(context.retryFaxPaste(), false);
  assert.equal(context.sendFax(true), false);
});

function faxResultFixture({ submitThrows = false, notesReject = false, notesGate, script = '12345', patient = '1', missing = false } = {}) {
  const fixture = setup();
  const { context, elements } = fixture;
  const frame = elements.preview;
  const form = frame.contentWindow.document.getElementById('preview2Form');
  let handler;
  let submissions = 0;
  let result = missing ? 'markerless' : 'preview';
  elements.printPageSize = { value: 'A4' };
  elements.addressSel = { value: 'none' };
  frame.addEventListener = (_event, callback) => { handler = callback; };
  frame.removeEventListener = () => {};
  frame.getAttribute = () => '/rx/ViewPreview2?scriptId=12345';
  form.querySelector = () => ({ value: '' });
  form.getAttribute = () => script;
  form.elements.demographic_no = { value: patient };
  form.submit = () => {
    submissions += 1;
    if (submitThrows) throw new Error('submit outcome unknown');
  };
  context.frames.preview.document.getElementById = (id) => {
    if (result === 'inaccessible') throw new Error('response inaccessible');
    if (id === 'preview2Form') return result === 'preview' ? form : null;
    if (id === 'fax-success') return result === 'success' ? {} : null;
    if (id === 'fax-failure') return result === 'failure' ? {} : null;
    return null;
  };
  frame.contentWindow.document = context.frames.preview.document;
  context.console = { log() {}, error() {} };
  context.alert = () => {};
  context.lockFaxNotes();
  context.faxSubmissionPending = true;
  context.setFaxControlsDisabled(true);
  if (notesReject) context.pendingNotesSave = Promise.reject(new Error('notes not saved'));
  if (notesGate) context.pendingNotesSave = notesGate;
  vm.runInContext(browserFunction('onPrint2', 'setComment'), context);
  context.onPrint2('oscarRxFax', '12345', 'attempt-id', false, 'captured prescription text', () => 'unsaved');
  return { ...fixture, submissions: () => submissions, load: (state) => { result = state; handler(); } };
}

for (const cancellation of ['modal hide', 'reset', 'unload']) {
  test(`${cancellation} cancels a deferred fax without claiming an in-flight fax was cancelled`, async () => {
    let finishNotes;
    const notesGate = new Promise((resolve) => { finishNotes = resolve; });
    const fixture = faxResultFixture({ notesGate });
    if (cancellation === 'modal hide') fixture.modalHandlers.get('hide.bs.modal')();
    else if (cancellation === 'unload') fixture.unloadHandlers[0]();
    else fixture.context.cancelPendingFax();
    finishNotes();
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(fixture.submissions(), 0);
    assert.equal(fixture.context.faxSubmissionPending, false);
    assert.equal(fixture.notes.readOnly, false);
    assert.equal(fixture.context.pendingFaxCancellation, null);
    if (cancellation === 'unload') assert.equal(fixture.modalHandlers.size, 0);
  });
}

test('dismissal after the fax POST starts never resets an uncertain in-flight operation', async () => {
  const fixture = faxResultFixture();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(fixture.submissions(), 1);
  fixture.modalHandlers.get('hide.bs.modal')();
  assert.equal(fixture.context.faxSubmissionPending, true);
  assert.equal(fixture.notes.readOnly, true);
  fixture.load('markerless');
  assert.equal(fixture.context.faxSubmissionUncertain, true);
});

test('prescription reset and clear actions cancel the deferred fax before changing the stash', () => {
  assert.match(browserFunction('resetStash', 'resetReRxDrugList'), /function resetStash\(\)\s*\{\s*cancelPendingFax\(\);/);
  assert.match(browserFunction('clearPending', 'clearPendingFax'), /function clearPending\(actionValue\)\s*\{\s*cancelPendingFax\(\);/);
});

for (const result of ['markerless', 'inaccessible']) {
  test(`inconclusive ${result} fax result never permits another transmission`, async () => {
    const fixture = faxResultFixture();
    await new Promise((resolve) => setImmediate(resolve));
    fixture.load('preview'); // late initial iframe load is not a fax result
    assert.equal(fixture.context.faxSubmissionPending, true);
    fixture.load(result);
    assert.equal(fixture.context.faxSubmissionUncertain, true);
    assert.equal(fixture.context.faxQueued, false); // do not claim success
    assert.equal(fixture.elements.faxSubmissionUncertain.hidden, false);
    assert.equal(fixture.context.sendFax(true), false);
    assert.equal(fixture.context.onPrint2('oscarRxFax'), false);
    assert.equal(fixture.context.addNotes(), false);
    assert.equal(fixture.notes.readOnly, true);
    assert.equal(fixture.elements.printPasteButton.disabled, true);
    assert.deepEqual(fixture.clearedTimeouts, [1]);
    assert.equal(fixture.submissions(), 1);
  });
}

test('explicit fax-failure restores controls but fax-success retains the queue lock', async () => {
  for (const result of ['failure', 'success']) {
    const fixture = faxResultFixture();
    await new Promise((resolve) => setImmediate(resolve));
    fixture.load(result);
    assert.equal(fixture.context.faxSubmissionUncertain, false);
    if (result === 'failure') {
      assert.equal(fixture.context.faxPreviewReloading, true);
      assert.equal(fixture.elements.faxButton.disabled, true);
      fixture.load('preview');
      assert.equal(fixture.context.faxPreviewReloading, false);
    }
    assert.equal(fixture.context.faxQueued, result === 'success');
    assert.equal(fixture.elements.faxButton.disabled, result === 'success');
    assert.equal(fixture.notes.readOnly, result === 'success');
  }
});

test('an exception after submit begins is uncertain, but a notes rejection never submits', async () => {
  const uncertain = faxResultFixture({ submitThrows: true });
  const rejected = faxResultFixture({ notesReject: true });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(uncertain.context.faxSubmissionUncertain, true);
  assert.equal(uncertain.submissions(), 1);
  assert.equal(rejected.context.faxSubmissionUncertain, false);
  assert.equal(rejected.context.faxSubmissionPending, false);
  assert.equal(rejected.notes.readOnly, false);
  assert.equal(rejected.submissions(), 0);
});

test('a failed preview reload keeps fax and note writes disabled', async () => {
  const fixture = faxResultFixture();
  await new Promise((resolve) => setImmediate(resolve));
  fixture.load('failure');
  fixture.load('markerless');
  assert.equal(fixture.context.faxSubmissionUncertain, false); // explicit no-queue result is known
  assert.equal(fixture.context.faxPreviewReloading, true);
  assert.equal(fixture.notes.readOnly, true);
  assert.equal(fixture.context.sendFax(false), false);
  assert.equal(fixture.context.addNotes(), false);
});

test('preview identity must match both the originating script and patient', () => {
  const { context } = setup();
  const form = { getAttribute: () => '12345', elements: { demographic_no: { value: '1' } } };
  assert.equal(context.matchesFaxPreview(form), true);
  form.getAttribute = () => '54321';
  assert.equal(context.matchesFaxPreview(form), false);
  form.getAttribute = () => '12345';
  form.elements.demographic_no.value = '2';
  assert.equal(context.matchesFaxPreview(form), false);
  assert.equal(context.matchesFaxPreview(null), false);
});

for (const options of [{ script: '54321' }, { patient: '2' }, { missing: true }]) {
  test(`missing or mismatched preview cannot submit: ${JSON.stringify(options)}`, async () => {
    const fixture = faxResultFixture(options);
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(fixture.submissions(), 0);
    assert.equal(fixture.context.hasPreview, false);
    assert.equal(fixture.elements.faxPreviewChanged.hidden, false);
    assert.equal(fixture.elements.faxButton.disabled, true);
  });
}

test('missing fax load event times out to uncertainty, never to safe retry', async () => {
  const fixture = faxResultFixture();
  await new Promise((resolve) => setImmediate(resolve));
  fixture.deferredTimeouts[0]();
  assert.equal(fixture.context.faxSubmissionPending, false);
  assert.equal(fixture.context.faxSubmissionUncertain, true);
  assert.equal(fixture.context.faxQueued, false);
  assert.equal(fixture.elements.faxSubmissionRecoveryText.value, 'captured prescription text');
  assert.equal(fixture.elements.faxSubmissionRecoveryText.hidden, false);
  assert.equal(fixture.context.sendFax(true), false);
  assert.equal(fixture.elements.printPasteButton.disabled, true);
  assert.deepEqual(fixture.clearedTimeouts, [1]);
});

test('missing fax prerequisites alone do not disable Print and Paste', () => {
  const fixture = setup();
  fixture.context.hasFaxSenderAccount = false;
  fixture.context.setFaxControlsDisabled(true);
  assert.equal(fixture.elements.faxButton.disabled, true);
  assert.equal(fixture.elements.printPasteButton.disabled, false);
});

test('recalculating controls preserves the historical-reprint restriction', () => {
  const fixture = setup();
  fixture.context.isReprint = true;
  fixture.context.setFaxControlsDisabled(false);
  assert.equal(fixture.elements.printPasteButton.disabled, true);
});

test('Print and Paste works without fax-only variables and rejects historical reprints', async () => {
  const { context } = setup();
  delete context.hasFaxNumber;
  delete context.hasFaxSenderAccount;
  delete context.canFaxScript;
  delete context.hasStoredSignature;
  context.window.parent = { opener: null };
  const writes = [];
  context.writeToEncounter = (print, text) => { writes.push({ print, text }); return Promise.resolve(true); };
  context.alert = (message) => { throw new Error(message); };
  vm.runInContext(browserFunction('printPaste2Parent', 'writeToEncounter'), context);
  assert.equal(await context.printPaste2Parent(true, false, true, 'captured prescription'), true);
  assert.equal(writes.length, 1);
  assert.equal(writes[0].print, true);
  assert.ok(writes[0].text.includes('captured prescription'));
  context.isReprint = true;
  assert.equal(await context.printPaste2Parent(true, false, true, 'old prescription'), false);
  assert.equal(writes.length, 1);
  // The declarations must remain in the unconditional script, not under the
  // later isRxFaxEnabled JSP branch that supplies the fax-only variables.
  const declarations = jsp.slice(jsp.indexOf('var POLL_TIME'), jsp.indexOf('function lockFaxNotes'));
  assert.match(declarations, /var hasPreview =/);
  assert.match(declarations, /var isReprint =/);
  assert.ok(!declarations.includes('if (CarlosProperties'));
});

function encounterFixture(fetchResult) {
  const { context } = setup();
  let requests = 0;
  context.alert = () => {};
  context.fetch = async () => {
    requests += 1;
    if (fetchResult instanceof Error) throw fetchResult;
    return fetchResult;
  };
  context.openEncounter = () => { throw new Error('window failed after commit'); };
  vm.runInContext(browserFunction('writeToEncounter', 'openEncounter'), context);
  return { context, requests: () => requests };
}

test('acknowledged append stays successful when opening encounter fails', async () => {
  const { context, requests } = encounterFixture({ ok: true, headers: { get: () => 'written' } });
  assert.equal(await context.writeToEncounter(false, 'rx text'), true);
  assert.equal(context.faxPasteCanRetry, false);
  assert.equal(requests(), 1);
});

for (const [label, response, retryable] of [
  ['lost response after commit', new Error('connection lost'), false],
  ['server error after save', { ok: false, status: 500, headers: { get: () => null } }, false],
  ['login redirect', { ok: true, redirected: true, status: 200, headers: { get: () => null } }, false],
  ['explicit pre-write rejection', { ok: false, status: 409, headers: { get: () => 'not-written' } }, true],
]) test(label, async () => {
  const { context } = encounterFixture(response);
  assert.equal(await context.writeToEncounter(false, 'rx text'), false);
  assert.equal(context.faxPasteCanRetry, retryable);
});

test('local insertion acknowledges success even if layout fails; missing editor returns no-write', () => {
  const chart = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
  const source = chart.slice(chart.indexOf('function pasteToEncounterNote('), chart.indexOf('function writeToEncounterNote('));
  const editor = { value: '' };
  const context = vm.createContext({
    console, caseNote: 'caseNote', document: { getElementById: () => editor },
    adjustCaseNote: () => { throw new Error('layout'); }, setCaretPosition: () => {},
  });
  vm.runInContext(source, context);
  assert.equal(context.pasteToEncounterNote('rx text'), true);
  assert.equal(editor.value, '\nrx text');
  context.document.getElementById = () => null;
  assert.equal(context.pasteToEncounterNote('rx text'), false);
  assert.equal(editor.value, '\nrx text');
});
