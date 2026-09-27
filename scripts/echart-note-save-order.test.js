/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const start = source.indexOf('    var ajaxRequest;');
const end = source.indexOf('    function submitIssue(', start);
assert.ok(start > 0 && end > start);

function harness() {
  const updates = []; const panels = []; const alerts = [];
  const fields = { newIssueId: { value: '42' }, issueAutocomplete: { value: 'fixture' } };
  const context = {
    caseNote: 'editor1', demographicNo: '123', ctx: '/carlos', savingNoteError: 'save failed',
    document: { forms: { caseManagementEntryForm: { method: {}, ajax: {} } } },
    Form: { serialize: () => 'fixture=form' },
    CarlosAjax: { updater: (target, url, options) => { updates.push(options); return {}; } },
    loadDiv: (target, url, limit, complete) => panels.push(complete),
    $: id => fields[id], alert: message => alerts.push(message),
  };
  vm.createContext(context); vm.runInContext(source.slice(start, end), context);
  context.updateIssueError = 'issue refresh failed';
  return { context, updates, panels, alerts, fields };
}

test('save waits for both issue fragments and repeated clicks submit once', () => {
  const h = harness(); let saves = 0;
  h.context.ajaxUpdateIssues('edit', 'sig1');
  assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), true);
  assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), true);
  assert.equal(saves, 0);
  h.updates[0].onComplete({ status: 200 });
  assert.equal(saves, 0);
  assert.equal(h.fields.newIssueId.value, '');
  h.panels[0](true);
  assert.equal(saves, 1);
  assert.deepEqual(h.alerts, []);
  assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), false);
});

test('all outstanding issue updates complete before a save resumes', () => {
  const h = harness(); let saves = 0;
  h.context.ajaxUpdateIssues('edit', 'sig1'); h.context.ajaxUpdateIssues('edit', 'sig1');
  h.context.deferNoteSaveUntilIssues(() => saves++);
  h.updates[1].onComplete({ status: 200 }); h.panels[0](true);
  assert.equal(saves, 0);
  h.updates[0].onComplete({ status: 200 }); h.panels[1](true);
  assert.equal(saves, 1);
});

for (const phase of ['issue list', 'panel']) {
  test(`failed ${phase} refresh prevents saving incomplete issue state and permits retry after recovery`, () => {
    const h = harness(); let saves = 0;
    h.context.ajaxUpdateIssues('edit', 'sig1'); h.context.deferNoteSaveUntilIssues(() => saves++);
    if (phase === 'issue list') {
      h.updates[0].onFailure({ status: 500 }); h.updates[0].onComplete({ status: 500 });
    } else {
      h.updates[0].onComplete({ status: 200 }); h.panels[0](false);
    }
    assert.equal(saves, 0); assert.equal(h.alerts.length, 1);
    assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), true);
    h.context.ajaxUpdateIssues('edit', 'sig1'); h.updates[1].onComplete({ status: 200 });
    h.panels.at(-1)(true);
    assert.equal(saves, 0, 'failed save must require a new user request');
    assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), false);
  });
}

test('a queued save never saves a different editor after navigation', () => {
  const h = harness(); let saves = 0;
  h.context.ajaxUpdateIssues('edit', 'sig1'); h.context.deferNoteSaveUntilIssues(() => saves++);
  h.context.caseNote = 'editor2';
  h.updates[0].onComplete({ status: 200 }); h.panels[0](true);
  assert.equal(saves, 0); assert.deepEqual(h.alerts, ['save failed']);
});

test('a replaced editor does not cause null field errors during issue completion', () => {
  const h = harness(); delete h.fields.newIssueId; delete h.fields.issueAutocomplete;
  h.context.ajaxUpdateIssues('edit', 'sig1');
  assert.doesNotThrow(() => h.updates[0].onComplete({ status: 200 }));
  h.panels[0](true);
});

for (const choices of [['save', 'saveAndExit'], ['saveAndExit', 'save']]) {
  test(`latest choice ${choices.join(' then ')} replaces the pending action without duplicate saves`, () => {
    const h = harness(); const saved = [];
    h.context.ajaxUpdateIssues('edit', 'sig1');
    for (const choice of choices) h.context.deferNoteSaveUntilIssues(() => saved.push(choice));
    h.updates[0].onComplete({ status: 200 });
    assert.deepEqual(saved, []);
    h.panels[0](true);
    assert.deepEqual(saved, [choices[1]]);
  });
}

// Execute the actual navigation entry points through their deferral boundary. Any subsequent
// lock, save, or DOM work is represented by the sentinel; browser tests exercise that work too.
for (const [name, argument, boundary] of [
  ['newNote', null, '        ++newNoteCounter;'],
  ['editNote', { target: 'edit42' }, '        var el = Event.element(e);'],
  ['changeToView', 'editor1', '        var parent = $(id).parentNode.id;'],
]) {
  for (const succeeded of [true, false]) {
    test(`${name} preserves the editor while issue refresh is pending and ${succeeded ? 'resumes' : 'stays open on failure'}`, () => {
      const h = harness(); const events = [];
      h.fields.editor1 = { value: 'unsaved' };
      h.context.Event = { stop: () => events.push('stop') };
      h.context.teardown = () => events.push('teardown');
      const from = source.indexOf(`    function ${name}(`);
      const to = source.indexOf(boundary, from);
      assert.ok(from > 0 && to > from);
      vm.runInContext(`${source.slice(from, to)} teardown(); }`, h.context);
      h.context.ajaxUpdateIssues('edit', 'sig1');
      assert.equal(h.context[name](argument), false);
      assert.ok(!events.includes('teardown'));
      h.updates[0].onComplete({ status: 200 });
      assert.ok(!events.includes('teardown'));
      h.panels[0](succeeded);
      assert.equal(events.filter(event => event === 'teardown').length, succeeded ? 1 : 0);
      assert.equal(h.context.caseNote, 'editor1');
    });
  }
}

function switchHarness() {
  const h = harness(); const effects = [];
  const editor = { value: 'typed clinical text', parentNode: { id: 'n01' }, disabled: false };
  h.fields.editor1 = editor;
  h.fields.observationDate = { value: 'date', disabled: false };
  h.fields.encTypeSelect01 = { value: 'face-to-face', disabled: true };
  h.fields.autosaveTime = { textContent: 'previous status' };
  const form = h.context.document.forms.caseManagementEntryForm;
  form.elements = [editor, h.fields.observationDate, h.fields.encTypeSelect01];
  form.setAttribute = (key, value) => { form[key] = value; };
  form.removeAttribute = key => { delete form[key]; };
  Object.assign(h.context, {
    providerNo: '1', origCaseNote: '', origObservationDate: 'date',
    confirm: () => true, unsavedNoteWarning: 'save changes?', lostNoteLock: false, caisiEnabled: false,
    $F: id => h.fields[id]?.value, validDate: () => true,
    clearAutoSaveTimer: () => effects.push('pause autosave'), setTimer: () => effects.push('resume autosave'),
    finishChangeToView: () => effects.push('teardown'),
    noteLockLostError: 'lock lost', sessionExpiredError: 'session expired',
  });
  h.context.CarlosAjax.request = (url, options) => h.updates.push(options);
  for (const [name, next] of [['changeToView', 'finishChangeToView'], ['ajaxSaveNote', 'saveNoteAjax']]) {
    vm.runInContext(source.slice(source.indexOf(`    function ${name}(`), source.indexOf(`    function ${next}(`)), h.context);
  }
  return { ...h, editor, form, effects };
}

for (const status of [0, 403, 409, 500, 200]) {
  test(`save-on-switch retains text and controls on ${status === 200 ? 'unacknowledged HTTP 200' : `HTTP ${status}`} and does not navigate`, () => {
    const h = switchHarness(); let navigations = 0;
    assert.equal(h.context.changeToView('editor1', () => navigations++), false);
    assert.equal(h.editor.disabled, true);
    assert.equal(h.form['aria-busy'], 'true');
    assert.ok(!h.effects.includes('teardown'));
    const response = { status };
    if (status !== 200) h.updates[0].onFailure(response);
    else {
      h.context.DOMParser = class {
        parseFromString() { return { getElementById: () => null }; }
      };
      h.updates[0].onSuccess({ ...response, responseText: '<p>missing acknowledgement</p>' });
    }
    h.updates[0].onComplete(response);
    assert.equal(h.editor.value, 'typed clinical text');
    assert.equal(h.editor.disabled, false);
    assert.equal(h.fields.encTypeSelect01.disabled, true, 'previously disabled controls remain disabled');
    assert.equal(h.fields.autosaveTime.textContent, 'previous status');
    assert.equal(h.form['aria-busy'], undefined);
    assert.ok(!h.effects.includes('teardown'));
    assert.equal(navigations, 0);
    assert.equal(h.alerts.length, 1);
    assert.equal(h.context.noteSwitchSavePending, false);
  });
}

test('save-on-switch serializes before disabling controls and tears down only after confirmed save', () => {
  const h = switchHarness(); let navigations = 0;
  h.context.Form.serialize = () => {
    assert.equal(h.editor.disabled, false);
    return 'fixture=form';
  };
  h.context.DOMParser = class {
    parseFromString() { return { getElementById: () => ({ getAttribute: () => '42' }) }; }
  };
  h.fields.sig01 = { update: () => { h.context.updatedNoteId = '42'; } };
  h.context.changeToView('editor1', () => navigations++);
  assert.ok(h.updates[0].postBody.includes('noteTxt=typed%20clinical%20text'));
  assert.equal(h.context.deferNoteSaveUntilIssues(() => navigations++), true);
  assert.equal(navigations, 0);
  h.updates[0].onSuccess({ status: 200, responseText: 'confirmed fixture fragment' });
  assert.ok(!h.effects.includes('teardown'));
  h.updates[0].onComplete({ status: 200 });
  assert.equal(h.effects.filter(effect => effect === 'teardown').length, 1);
  assert.equal(navigations, 1);
  assert.equal(h.editor.disabled, false);
});
