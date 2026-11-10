/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { chartActionUrlSource } = require('./lib/chart-action-url-source');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const chartHelper = chartActionUrlSource(source);
const start = source.indexOf('    var ajaxRequest;');
const end = source.indexOf('    function submitIssue(', start);
assert.ok(start > 0 && end > start);

function harness() {
  const updates = []; const panels = []; const alerts = [];
  const fields = { unresolvedIssues: {}, newIssueId: { value: '42' }, issueAutocomplete: { value: 'fixture' } };
  const context = {
    caseNote: 'editor1', demographicNo: '123', ctx: '/carlos', chartContext: '', savingNoteError: 'save failed',
    document: { forms: { caseManagementEntryForm: { method: {}, ajax: {} } } },
    Form: { serialize: () => 'fixture=form' },
    CarlosAjax: { updater: (target, url, options) => { updates.push(options); return {}; } },
    loadDiv: (target, url, limit, complete) => panels.push(complete),
    $: id => fields[id], alert: message => alerts.push(message),
  };
  vm.createContext(context); vm.runInContext(chartHelper + source.slice(start, end), context);
  context.updateIssueError = 'issue refresh failed';
  context.issueSidebarRefreshError = 'sidebar refresh failed; saving remains available';
  return { context, updates, panels, alerts, fields };
}

test('save waits for both issue fragments and repeated clicks submit once', () => {
  const h = harness(); let saves = 0;
  h.context.ajaxUpdateIssues('edit', 'sig1');
  assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), true);
  assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), true);
  assert.equal(saves, 0);
  h.updates[0].onComplete({ updateSucceeded: true, status: 200 });
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
  assert.equal(h.updates.length, 1, 'the second refresh must wait for the first pair');
  h.updates[0].onComplete({ updateSucceeded: true, status: 200 });
  assert.equal(h.updates.length, 1, 'wait for the sidebar too');
  h.panels[0](true);
  assert.equal(saves, 0);
  assert.equal(h.updates.length, 2);
  h.updates[1].onComplete({ updateSucceeded: true, status: 200 }); h.panels[1](true);
  assert.equal(saves, 1);
});

for (const phase of ['issue list']) {
  test(`failed ${phase} refresh prevents saving incomplete issue state and permits retry after recovery`, () => {
    const h = harness(); let saves = 0;
    h.context.ajaxUpdateIssues('edit', 'sig1'); h.context.deferNoteSaveUntilIssues(() => saves++);
    if (phase === 'issue list') {
      h.updates[0].onFailure({ status: 500 }); h.updates[0].onComplete({ updateSucceeded: true, status: 500 });
    } else {
      h.updates[0].onComplete({ updateSucceeded: true, status: 200 }); h.panels[0](false);
    }
    assert.equal(saves, 0); assert.equal(h.alerts.length, 1);
    assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), true);
    h.context.ajaxUpdateIssues('edit', 'sig1'); h.updates[1].onComplete({ updateSucceeded: true, status: 200 });
    h.panels.at(-1)(true);
    assert.equal(saves, 0, 'failed save must require a new user request');
    assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), false);
  });
}

test('a queued save never saves a different editor after navigation', () => {
  const h = harness(); let saves = 0;
  h.context.ajaxUpdateIssues('edit', 'sig1'); h.context.deferNoteSaveUntilIssues(() => saves++);
  h.context.caseNote = 'editor2';
  h.updates[0].onComplete({ updateSucceeded: true, status: 200 }); h.panels[0](true);
  assert.equal(saves, 0); assert.deepEqual(h.alerts, ['save failed']);
});

test('a replaced editor does not cause null field errors during issue completion', () => {
  const h = harness(); delete h.fields.newIssueId; delete h.fields.issueAutocomplete;
  h.context.ajaxUpdateIssues('edit', 'sig1');
  assert.doesNotThrow(() => h.updates[0].onComplete({ updateSucceeded: true, status: 200 }));
  h.panels[0](true);
});

for (const choices of [['save', 'saveAndExit'], ['saveAndExit', 'save']]) {
  test(`latest choice ${choices.join(' then ')} replaces the pending action without duplicate saves`, () => {
    const h = harness(); const saved = [];
    h.context.ajaxUpdateIssues('edit', 'sig1');
    for (const choice of choices) h.context.deferNoteSaveUntilIssues(() => saved.push(choice));
    h.updates[0].onComplete({ updateSucceeded: true, status: 200 });
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
      h.updates[0].onComplete({ updateSucceeded: succeeded, status: succeeded ? 200 : 503 });
      assert.ok(!events.includes('teardown'));
      if (succeeded) h.panels[0](true);
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
  const durationNames = ['hourOfEncounterTime', 'minuteOfEncounterTime',
    'hourOfEncTransportationTime', 'minuteOfEncTransportationTime'];
  for (const name of durationNames) h.fields[name] = { value: '17', disabled: false };
  form.elements = [editor, h.fields.observationDate, h.fields.encTypeSelect01,
    ...durationNames.map(name => h.fields[name])];
  form.setAttribute = (key, value) => { form[key] = value; };
  form.removeAttribute = key => { delete form[key]; };
  Object.assign(h.context, {
    providerNo: '1', origCaseNote: '', origObservationDate: 'date',
    confirm: () => true, unsavedNoteWarning: 'save changes?', lostNoteLock: false, caisiEnabled: false,
    $F: id => h.fields[id]?.value, validDate: () => true,
    clearAutoSaveTimer: () => effects.push('pause autosave'), setTimer: () => effects.push('resume autosave'),
    finishChangeToView: () => effects.push('teardown'),
    noteLockLostError: 'lock lost', sessionExpiredError: 'session expired', invalidObservationDateError: 'invalid encounter date; draft retained',
  });
  h.context.CarlosAjax.request = (url, options) => h.updates.push(options);
  for (const [name, next] of [['changeToView', 'finishChangeToView'], ['ajaxSaveNote', 'saveNoteAjax']]) {
    vm.runInContext(source.slice(source.indexOf(`    function ${name}(`), source.indexOf(`    function ${next}(`)), h.context);
  }
  return { ...h, editor, form, effects };
}

for (const status of [0, 400, 403, 409, 500, 200]) {
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
    for (const name of ['hourOfEncounterTime', 'minuteOfEncounterTime',
      'hourOfEncTransportationTime', 'minuteOfEncTransportationTime']) {
      assert.equal(h.fields[name].value, '17', 'failed saves retain each exact duration');
      assert.equal(h.fields[name].disabled, false, 'failed saves retain editable duration controls');
    }
    assert.equal(h.fields.autosaveTime.textContent, 'previous status');
    assert.equal(h.form['aria-busy'], undefined);
    assert.ok(!h.effects.includes('teardown'));
    assert.equal(navigations, 0);
    assert.equal(h.alerts.length, 1);
    if (status === 400) assert.equal(h.alerts[0], 'invalid encounter date; draft retained');
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
  h.updates[0].onComplete({ updateSucceeded: true, status: 200 });
  assert.equal(h.effects.filter(effect => effect === 'teardown').length, 1);
  assert.equal(navigations, 1);
  assert.equal(h.editor.disabled, false);
});

// Execute the actual successful teardown. Optional CAISI time inputs precede
// noteIssues in the signature, so deleting that issue subtree alone left their
// names and IDs active in the next note's full-form Save.
function durationTeardown(values) {
  const durationNames = ['hourOfEncounterTime', 'minuteOfEncounterTime',
    'hourOfEncTransportationTime', 'minuteOfEncTransportationTime'];
  const signatures = [];
  function signature(id, times) {
    const node = { id, children: [], querySelectorAll(selector) {
      return this.children.filter(child => child.tagName === 'INPUT'
        && selector.includes(`input[name="${child.name}"]`));
    }, replaceChild(replacement, original) {
      const index = this.children.indexOf(original);
      assert.ok(index >= 0, 'replace only a child of the retiring signature');
      this.children[index] = replacement; replacement.parentNode = this; original.parentNode = null;
    } };
    durationNames.forEach((name, index) => node.children.push({ tagName: 'INPUT', id: name,
      name, value: times[index], disabled: false, parentNode: node }));
    node.children.push({tagName: 'INPUT', id: `${id}-unrelated`, name: 'unrelated',
      value: 'keep', parentNode: node});
    signatures.push(node);
    return node;
  }
  const retiring = signature('sig101', values);
  const current = signature('sig202', ['3', '21', '4', '32']);
  const parent = {id: 'n101', insertAdjacentHTML() {}, style: {}};
  const editor = {id: 'editor101', parentNode: parent};
  const fields = {editor101: editor, n101: parent, edit101: {insertAdjacentHTML() {}}, editWarn101: {value: 'false'}};
  const get = id => fields[id] || signatures.find(node => node.id === id)
    || signatures.flatMap(node => node.children).find(node => node.id === id) || null;
  const events = [];
  const context = {
    document: {getElementById: get, createTextNode: value => ({nodeType: 3, textContent: value})},
    $: get, $F: id => get(id).value, caseNote: editor.id, ctx: '/carlos', editLabel: 'Edit', largeNote: () => false, noteIsQeued: () => -1,
    noteIssuesChanged: true, autoSaveTimer: 41, clearTimeout: id => events.push(['clear timer', id]),
    removeLock: id => events.push(['release lock', String(id).match(/\d+/)[0]]), deleteAutoSave: () => events.push(['delete draft']),
    jQuery: () => ({off() {}}), destroyNoteIssueAutocomplete() {}, monitorCaseNote() {}, getActiveText() {},
    Element: {remove(id) { delete fields[id]; }, stopObserving() {}},
  };
  const from = source.indexOf('    function notePrintButton(');
  const to = source.indexOf('    function escapeNoteText(', from);
  assert.ok(from >= 0 && to > from, 'successful note teardown source exists');
  vm.createContext(context); vm.runInContext(source.slice(from, to), context);
  return {context, retiring, current, durationNames, get, events};
}

for (const values of [['1', '12', '2', '34'], ['', '', '', '']]) {
  for (const saving of [true, false]) {
    test(`retiring ${saving ? 'a saved' : 'an unchanged'} note removes optional duration fields before the next note saves ${JSON.stringify(values)}`, () => {
      const h = durationTeardown(values);
      const nextControls = [...h.current.children];
      h.context.finishChangeToView('editor101', saving, 'clinical note');
      assert.deepEqual(h.retiring.children.slice(0, 4).map(node => node.textContent), values,
        'the previous note retains its exact displayed duration as text');
      assert.ok(h.retiring.children.slice(0, 4).every(node => node.nodeType === 3));
      assert.equal(h.retiring.children[4].name, 'unrelated', 'other signature fields remain untouched');
      assert.deepEqual(h.current.children, nextControls, 'the next editor retains its exact controls');
      const serialized = new URLSearchParams(h.retiring.children.concat(h.current.children)
        .filter(node => node.tagName === 'INPUT' && !node.disabled).map(node => [node.name, node.value]));
      h.durationNames.forEach((name, index) => {
        assert.equal(h.get(name), nextControls[index], 'global validation resolves the current editor only');
        assert.deepEqual(serialized.getAll(name), [nextControls[index].value],
          'native full-form serialization submits exactly one current duration');
      });
      assert.equal(h.context.noteIssuesChanged, false);
      assert.deepEqual(h.events, [['release lock', '101'], ['clear timer', 41], ['delete draft']]);
    });
  }
}

for (const failure of ['503', 'unacknowledged 200', 'different patient', 'setup']) {
  test(`full-chart ${failure} retains the editor and permits retry`, () => {
    const h = switchHarness();
    const errors = [];
    const replacements = [];
    h.fields.notCPP = { update: html => replacements.push(html) };
    h.context.caseNote = 'editor1';
    h.context.noteIssuesChanged = false;
    h.context.origCaseNote = h.editor.value;
    h.form.noteId = { value: '48' };
    Object.assign(h.context, {
      assembleMainChartParams: () => 'demographicNo=123',
      showChartLoadError: (id, status) => errors.push(status),
      destroyNoteIssueAutocomplete: () => h.effects.push('destroy autocomplete'),
    });
    vm.runInContext(source.slice(source.indexOf('    var fullChart ='), source.indexOf('    var socHistoryLabel;')), h.context);
    if (failure === 'setup') h.context.CarlosAjax.request = () => { throw new Error('setup'); };
    h.context.viewFullChart(true);
    if (failure !== 'setup') assert.equal(h.updates[0].postBody, 'demographicNo=123&previousNoteId=48');
    assert.equal(h.editor.value, 'typed clinical text');
    assert.deepEqual(replacements, []);
    if (failure !== 'setup') {
      if (failure === '503') h.updates[0].onFailure({status: 503});
      else {
        h.context.DOMParser = class {
          parseFromString() { return {getElementById: () => failure === 'different patient' ? {getAttribute: () => '999'} : null}; }
        };
        h.updates[0].onSuccess({status: 200, responseText: '<html>login or wrong chart</html>'});
      }
      h.updates[0].onComplete();
    }
    assert.equal(h.context.fullChart, 'false');
    assert.equal(h.editor.disabled, false);
    assert.equal(h.context.noteSwitchSavePending, false);
    assert.deepEqual(replacements, []);
    assert.equal(h.effects.includes('destroy autocomplete'), false);
    assert.equal(errors.length, 1);
    h.context.CarlosAjax.request = (url, options) => h.updates.push(options);
    h.context.viewFullChart(true);
    assert.equal(h.context.noteSwitchSavePending, true);
  });
}

test('full-chart navigation waits for acknowledgement of an encounter-type-only edit', () => {
  const h = switchHarness();
  h.context.caseNote = 'editor1';
  h.context.origCaseNote = h.editor.value;
  const markup = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/noteIssueList.jsp'), 'utf8');
  const select = markup.match(/<select[^>]+class="encTypeCombo"[^>]+>/)[0];
  vm.runInContext(select.match(/onchange="([^"]+)"/)[1], h.context);
  let loads = 0;
  vm.runInContext(source.slice(source.indexOf('    var fullChart ='), source.indexOf('    var socHistoryLabel;')), h.context);
  h.context.loadFullChart = () => loads++;
  h.context.viewFullChart(true);
  assert.equal(loads, 0);
  assert.equal(new URLSearchParams(h.updates[0].postBody).get('preserveEditor'), 'true',
    'the acknowledged save must retain editable controls until the chart replaces them');
  h.updates[0].onFailure({status: 503});
  h.updates[0].onComplete({status: 503});
  assert.equal(loads, 0);
  assert.equal(h.editor.value, 'typed clinical text');
  assert.equal(h.context.noteIssuesChanged, true);
});

for (const [index, kind] of ['resolved', 'unresolved'].entries()) {
  test(`a ${kind} checklist-only edit enters save-on-switch and retains the selection on failure`, () => {
    const h = switchHarness();
    h.context.origCaseNote = h.editor.value;
    const markup = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/noteIssueList.jsp'), 'utf8');
    const inputs = markup.match(/<input type="checkbox"[^\n]+name="issueCheckList[^\n]+/g);
    assert.equal(inputs.length, 2);
    const handler = inputs[index].match(/onchange="([^"]+)"/);
    assert.ok(handler, 'AJAX-rendered checkboxes must retain their change handler');
    h.context.changedCheckbox = {form: h.form};
    vm.runInContext('(function () {' + handler[1] + '}).call(changedCheckbox)', h.context);
    assert.equal(h.context.noteIssuesChanged, true);
    h.context.changeToView('editor1');
    assert.equal(h.updates.length, 1, 'an issue-only change must save before the UI is removed');
    h.updates[0].onFailure({status: 503});
    h.updates[0].onComplete({status: 503});
    assert.equal(h.context.noteIssuesChanged, true);
    assert.equal(h.effects.includes('teardown'), false);
  });
}

test('the first chart load succeeds before a note form or editor exists', () => {
  const h = switchHarness();
  h.context.document.forms = {};
  h.context.caseNote = undefined;
  const replacements = [];
  h.fields.notCPP = {update: html => replacements.push(html)};
  Object.assign(h.context, {
    assembleMainChartParams: () => 'demographicNo=123',
    destroyNoteIssueAutocomplete() {}, scrollDownInnerBar() {},
    DOMParser: class {parseFromString() {return {getElementById: () => ({getAttribute: () => '123'})};}},
  });
  vm.runInContext(source.slice(source.indexOf('    var fullChart ='), source.indexOf('    var socHistoryLabel;')), h.context);
  h.context.viewFullChart(false);
  assert.equal(h.context.noteSwitchSavePending, true);
  h.updates[0].onSuccess({status: 200, responseText: 'initial chart'});
  h.updates[0].onComplete();
  assert.deepEqual(replacements, ['initial chart']);
  assert.equal(h.context.noteSwitchSavePending, false);
});

test('a fresh checklist button opens its AJAX-replaced controls even when the previous editor was expanded', () => {
  const fields = {'noteIssues-resolved': {style: {display: 'none'}}, 'noteIssues-unresolved': {style: {display: 'none'}}};
  const context = {$: id => fields[id]};
  vm.createContext(context);
  vm.runInContext(source.slice(source.indexOf('    var showIssue ='), source.indexOf('    function showIssues(')), context);
  const button = () => ({expanded: 'false', getAttribute() {return this.expanded;}, setAttribute(key, value) {this.expanded = value;}});
  context.toggleIssueChecklist(button());
  assert.equal(context.showIssue, true);
  for (const field of Object.values(fields)) field.style.display = 'none';
  const replacement = button();
  context.toggleIssueChecklist(replacement);
  assert.equal(fields['noteIssues-unresolved'].style.display, 'block');
  assert.equal(fields['noteIssues-resolved'].style.display, 'block');
  assert.equal(replacement.expanded, 'true');
  context.toggleIssueChecklist(replacement);
  assert.equal(fields['noteIssues-unresolved'].style.display, 'none');
});

test('the editable note Print control follows its committed revision after an acknowledgement renames its id', () => {
  const calls = [];
  const context = {nId: '101', ctx: '/carlos', event: {}, control: {id: 'print202'},
    togglePrint: (id, event) => calls.push(String(id)), noteIsQeued: () => -1};
  vm.createContext(context);
  vm.runInContext(source.slice(source.indexOf('    function notePrintButton('), source.indexOf('    function finishChangeToView(')), context);
  const start = source.indexOf('        var printimg =', source.indexOf('    function editNote('));
  vm.runInContext(source.slice(start, source.indexOf('\n', start)), context);
  const handler = context.printimg.match(/onclick='([^']+)'/)[1];
  vm.runInContext(`(function(event) { ${handler}; }).call(control, event);`, context);
  assert.deepEqual(calls, ['202']);
  assert.ok(context.printimg.includes("<button type='button'"));
  assert.ok(context.printimg.includes("aria-label='Toggle Print Note'"));
  assert.doesNotMatch(context.printimg, /role='button'|onkeydown=/, 'native buttons provide keyboard activation');
});

test('individual and bulk Print selection keep the queue, icon and accessible state consistent', () => {
  for (const interactive of [false, true]) {
    const attrs = {};
    const icon = {};
    const control = interactive ? {tagName: 'BUTTON', querySelector: () => icon,
      setAttribute: (name, value) => { attrs[name] = value; }} : icon;
    const fields = {print101: control, notes2print: {value: ''}};
    const context = {ctx: '/carlos', $: id => fields[id], $F: id => fields[id].value,
      Event: {stop() {}}, Image: class {}};
    vm.createContext(context);
    vm.runInContext(source.slice(source.indexOf('    function noteIsQeued('), source.indexOf('    var printDateMsg;')), context);
    context.imgPrintgreen.src = '/carlos/encounter/graphics/printerGreen.png';
    const event = {preventDefault() {}};
    for (const select of [() => context.togglePrint('101', event), () => context.addPrintQueue('101')]) {
      select();
      assert.equal(fields.notes2print.value, '101');
      assert.equal(icon.src, context.imgPrintgreen.src);
      assert.equal(attrs['aria-pressed'], interactive ? 'true' : undefined);
      context.removePrintQueue('101', 0);
      assert.equal(fields.notes2print.value, '');
      assert.equal(icon.src, '/carlos/encounter/graphics/printer.png');
      assert.equal(attrs['aria-pressed'], interactive ? 'false' : undefined);
    }
  }
});

for (const queue of ['', '101', '202,101']) {
  test(`opening the saved editor reflects its existing Print queue: ${queue || 'empty'}`, () => {
    const attrs = {'aria-pressed': 'false'};
    const icon = {};
    let insertions = 0;
    const fields = {notes2print: {value: queue}, n101: {insertAdjacentHTML() { insertions++; }},
      print101: {tagName: 'BUTTON', querySelector: () => icon, setAttribute: (name, value) => {attrs[name] = value;}}};
    const context = {nId: '101', ctx: '/carlos', txt: 'n101', printimg: 'production markup',
      $: id => fields[id], $F: id => fields[id].value};
    vm.createContext(context);
    vm.runInContext(source.slice(source.indexOf('    function noteIsQeued('), source.indexOf('    function togglePrint(')), context);
    const start = source.indexOf('        var strNid =', source.indexOf('    function editNote('));
    vm.runInContext(source.slice(start, source.indexOf('        if ($F(isFull)', start)), context);
    const selected = queue.split(',').includes('101');
    assert.equal(insertions, 1);
    assert.equal(attrs['aria-pressed'], String(selected));
    assert.equal(icon.src, `/carlos/encounter/graphics/${selected ? 'printerGreen' : 'printer'}.png`);
    assert.equal(fields.notes2print.value, queue);
  });
}
