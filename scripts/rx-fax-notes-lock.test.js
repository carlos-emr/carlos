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
function browserFunction(name, nextName) {
  const start = jsp.indexOf(`function ${name}(`);
  const end = jsp.indexOf(`function ${nextName}(`, start + 1);
  assert.ok(start >= 0 && end > start, `missing JSP function ${name}`);
  return jsp.slice(start, end)
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
    faxPasteRetryRow: { style: { display: 'none' } },
    faxPasteRetryButton: { disabled: false },
    faxNumber: { selectedIndex: 0, options: [{ value: '5555555555' }] },
    preview: { contentWindow: { document: { getElementById: () => previewForm } } },
  };
  const previewElements = { finalFax: { value: '' }, additNotes: { style: {} } };
  const savedBodies = [];
  const deferredTimeouts = [];
  const closedWindows = [];
  let releaseSave;
  const saveGate = new Promise((resolve) => { releaseSave = resolve; });
  const context = vm.createContext({
    console,
    document: { getElementById: (id) => elements[id] || null },
    frames: { preview: { document: {
      getElementById: (id) => previewElements[id],
      getElementsByName: () => [{ value: '' }],
    } } },
    window: { onbeforeunload: () => 'unsaved', top: { close: () => { closedWindows.push(true); } } },
    setTimeout: (fn) => { deferredTimeouts.push(fn); },
    faxSubmissionPending: false,
    faxNotesState: null,
    pendingNotesSave: Promise.resolve(),
    faxQueued: false,
    faxPasteRetryText: null,
    hasPreview: true,
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
  return { context, notes, save, elements, savedBodies, releaseSave, deferredTimeouts, closedWindows };
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
  context.enterFaxPasteRecovery('prescription\nnote at fax click\n');

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
  context.printPaste2Parent = (print, fax, pasteRx, text) => {
    pasteAttempts.push({ print, fax, pasteRx, text });
    return Promise.resolve(pasteAttempts.length > 1);
  };
  assert.equal(context.retryFaxPaste(), true);
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(pasteAttempts, [
    { print: false, fax: true, pasteRx: true, text: 'prescription\nnote at fax click\n' },
  ]);
  assert.equal(elements.faxPasteRetryButton.disabled, false);
  assert.equal(elements.faxPasteRetryRow.style.display, '');
  assert.equal(closedWindows.length, 0);

  // A successful retry pastes the faxed text, hides the retry and closes the window.
  assert.equal(context.retryFaxPaste(), true);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(pasteAttempts.length, 2);
  assert.equal(pasteAttempts[1].text, 'prescription\nnote at fax click\n');
  assert.equal(context.faxPasteRetryText, null);
  assert.equal(elements.faxPasteRetryRow.style.display, 'none');
  assert.equal(context.retryFaxPaste(), false); // nothing left to retry
  assert.equal(deferredTimeouts.length, 1);
  deferredTimeouts[0]();
  assert.equal(closedWindows.length, 1);
});
