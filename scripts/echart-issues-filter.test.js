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
function block(from, to) {
  const start = source.indexOf(from);
  const end = source.indexOf(to, start);
  assert.ok(start >= 0 && end > start, `Missing or misordered source markers: ${from} -> ${to}`);
  return source.slice(start, end);
}

// A small DOM adapter: production functions create real node trees, never parsed markup.
function element(tag, props = {}) {
  return Object.assign({ tagName: tag.toUpperCase(), children: [], value: '', attributes: {}, style: {},
    appendChild(child) { this.children.push(child); return child; },
    setAttribute(name, value) { this.attributes[name] = value; },
    getAttribute(name) { return this.attributes[name] || null; },
    querySelectorAll(selector) {
      const children = this.children.flatMap(child => [child, ...child.querySelectorAll(selector)]);
      return children.filter(child => selector === 'input[name="issue_id"]' && child.name === 'issue_id');
    },
  }, props);
}

function harness({ searchData = [{ description: ' Safe & Sound ', code: 'A"B', id: 12 }], searchFails = false } = {}) {
  const list = element('ul');
  const cpp = element('form', { id: 'frmIssueNotes', elements: { issueChange: { value: '' } }, children: [list],
    querySelector: selector => selector === '#issueIdList' ? list : null });
  const note = element('form', { name: 'caseManagementEntryForm', elements: {
    newIssueId: { value: '' }, newIssueName: { value: '' }, change_diagnosis_id: { value: '' },
  } });
  note.change_diagnosis_id = note.elements.change_diagnosis_id;
  const input = element('input', { form: note });
  note.querySelector = selector => selector === '#issueAutocomplete' ? input : null;
  const cppInput = element('input', { form: cpp });
  const button = element('input');
  const signature = element('div', { id: 'sig31' });
  const fields = { asgnIssues: button, issueAutocomplete: input, noteIssues: { closest: () => signature },
    filter: element('div', { style: { display: 'none' } }), notesFilterToggle: element('button'),
    check_issue: { value: '73' } };
  const widgets = new Map(); const events = new Map(); const requests = []; const updates = []; const alerts = [];
  const searches = [input, cppInput];
  function jQuery(target) {
    return {
      find: () => ({ each: fn => searches.forEach(search => fn.call(search)) }),
      autocomplete(arg) {
        if (arg === 'instance') return widgets.get(target);
        if (arg === 'destroy') { widgets.delete(target); return this; }
        widgets.set(target, arg); return this;
      },
      on(name, fn) { events.set(target, fn); return this; },
      off() { events.delete(target); return this; },
    };
  }
  jQuery.ajax = options => { requests.push(options); return {
    abort() {}, done(fn) { if (!searchFails) fn(searchData); return this; },
    fail(fn) { if (searchFails) fn(); return this; },
  }; };
  jQuery.map = (items, fn) => items.map(fn).filter(item => item != null);
  const document = { forms: { caseManagementEntryForm: note, frmIssueNotes: cpp },
    getElementById: id => fields[id] || null,
    createElement: tag => element(tag), createTextNode: text => element('#text', { textContent: text }),
  };
  const context = { document, jQuery, ctx: '/carlos', fullChart: 'true', demographicNo: '100', providerNo: '200',
    submitIssues: false, issueUpdatesPending: 0, Event: { stop() {} }, alert: msg => alerts.push(msg),
    ajaxUpdateIssues: (method, div) => updates.push({ method, div }), $: id => fields[id],
    filter: (reset, clearIssues) => { requests.push({ reset, clearIssues }); return false; } };
  context.chartContext = '';
  vm.createContext(context);
  vm.runInContext(chartHelper, context);
  vm.runInContext(block('    var changeIssueMsg;', '    var closeWithoutSaveMsg;')
    + block('    var disposeNoteIssueSearch =', '    var ajaxRequest;')
    + block('    function showFilter(', '    function filterCheckBox(')
    + block('    function resetInputElements(', '    function filter('), context);
  context.pickIssueMsg = 'Pick a suggestion';
  return { context, document, note, cpp, list, input, cppInput, button, signature, fields, widgets, events, requests, updates, alerts };
}

test('CPP picks create checked issue_id controls, preserve literal labels, and leave the note form untouched', () => {
  const h = harness();
  h.note.elements.newIssueId.value = '99';
  h.note.elements.newIssueName.value = 'note selection';
  const label = 'A & B <img src=x onerror=alert(1)> "quote"';
  h.context.addIssue2CPP(h.cppInput, { id: 12, textContent: label });
  const controls = h.cpp.querySelectorAll('input[name="issue_id"]');
  assert.equal(controls.length, 1);
  assert.equal(controls[0].value, '12');
  assert.equal(controls[0].checked, true);
  assert.equal(h.list.children[0].children[0].children[1].textContent, label);
  assert.equal(h.cpp.elements.issueChange.value, 'true');
  assert.equal(h.note.elements.newIssueId.value, '99');
  assert.equal(h.note.elements.newIssueName.value, 'note selection');
  assert.equal(h.context.submitIssues, false);
});

test('duplicate CPP selections recheck the existing issue without duplicate fields', () => {
  const h = harness();
  h.context.addIssue2CPP(h.cppInput, { id: 12, textContent: 'Safety' });
  h.cpp.querySelectorAll('input[name="issue_id"]')[0].checked = false;
  h.context.addIssue2CPP(h.cppInput, { id: '12', textContent: 'Safety' });
  const controls = h.cpp.querySelectorAll('input[name="issue_id"]');
  assert.equal(controls.length, 1);
  assert.equal(controls[0].checked, true);
});

test('invalid issue ids and selections belonging to the other form are ignored', () => {
  const h = harness();
  for (const id of ['', '0', '-1', '12\" onclick=alert(1)', '12&method=save']) {
    h.context.addIssue2CPP(h.cppInput, { id, textContent: 'invalid' });
    h.context.saveIssueId(h.input, { id, textContent: 'invalid' });
  }
  h.context.addIssue2CPP(h.input, { id: 12, textContent: 'wrong form' });
  h.context.saveIssueId(h.cppInput, { id: 12, textContent: 'wrong form' });
  assert.equal(h.list.children.length, 0);
  assert.equal(h.note.elements.newIssueId.value, '');
});

test('autocomplete sends search as request data and routes each pick to its owning form', () => {
  const h = harness(); h.context.initializeIssueAutocomplete(h.document);
  const term = 'A&B + # ? " <'; let results;
  h.widgets.get(h.input).source({ term }, data => { results = data; });
  assert.equal(h.requests[0].data.term, term);
  assert.equal(h.requests[0].url, '/carlos/CaseManagementEntry');
  assert.equal(h.requests[0].method, 'GET');
  assert.equal(h.requests[0].dataType, 'json');
  assert.equal(results[0].value, 'Safe & Sound');
  const selected = { item: { id: 12, value: 'Safe & Sound' } };
  assert.equal(h.widgets.get(h.input).select({}, selected), false);
  assert.equal(h.note.elements.newIssueId.value, '12');
  assert.equal(h.input.value, 'Safe & Sound');
  assert.equal(h.list.children.length, 0);
  assert.equal(h.widgets.get(h.cppInput).select({}, selected), false);
  assert.equal(h.list.children.length, 1);
  assert.equal(h.note.elements.newIssueId.value, '12');
  assert.equal(h.cppInput.value, '');
});

test('autocomplete responds once with no suggestions for non-array payloads or failed requests', () => {
  for (const options of [{searchData: null}, {searchData: {}}, {searchData: 'not an array'},
    {searchData: 42}, {searchData: []}, {searchFails: true}]) {
    const h = harness(options); h.context.initializeIssueAutocomplete(h.document);
    for (const input of [h.input, h.cppInput]) {
      const responses = [];
      h.widgets.get(input).source({term: 'Safety'}, data => responses.push(Array.from(data)));
      assert.deepEqual(responses, [[]]);
    }
  }
});

test('autocomplete skips malformed items before trimming descriptions and preserves valid suggestions', () => {
  const h = harness({searchData: [null, false, 42, 'invalid', {}, {description: null},
    {description: 17}, {description: {}}, {description: []},
    {id: 12, description: ' Safe & Sound ', code: 'A"B'}]});
  h.context.initializeIssueAutocomplete(h.document);
  for (const input of [h.input, h.cppInput]) {
    const responses = [];
    h.widgets.get(input).source({term: 'Safety'}, data => responses.push(data));
    assert.equal(responses.length, 1);
    assert.equal(responses[0].length, 1);
    assert.deepEqual({...responses[0][0]}, {id: 12, label: 'Safe & Sound (A"B)', value: 'Safe & Sound'});
  }
});

test('initialization is idempotent and typing invalidates only the note selection', () => {
  const h = harness(); h.context.initializeIssueAutocomplete(h.document);
  const widget = h.widgets.get(h.input);
  h.context.saveIssueId(h.input, { id: 12, textContent: 'Safety' });
  h.context.initializeIssueAutocomplete(h.document);
  assert.equal(h.widgets.get(h.input), widget);
  assert.equal(h.note.elements.newIssueId.value, '12');
  h.events.get(h.cppInput)();
  assert.equal(h.note.elements.newIssueId.value, '12');
  h.events.get(h.input)();
  assert.equal(h.note.elements.newIssueId.value, '');
  assert.equal(h.note.elements.newIssueName.value, '');
  assert.equal(h.context.submitIssues, false);
});

test('Assign targets the active fragment including the latest saved revision id', () => {
  const h = harness();
  for (const id of ['sig0', 'sig31', 'sig92']) {
    h.signature.id = id;
    h.context.saveIssueId(h.input, { id: 12, textContent: 'Safety' });
    h.context.assignNoteIssue({});
    assert.equal(h.updates.at(-1).div, id);
    assert.equal(h.updates.at(-1).method, 'makeIssue');
  }
  assert.equal(h.updates.length, 3);
});

test('Assign rejects edited suggestions and ignores repeat clicks while an issue update is pending', () => {
  const h = harness();
  h.context.saveIssueId(h.input, { id: 12, textContent: 'Safety' });
  h.input.value = 'something else'; h.context.assignNoteIssue({});
  assert.equal(h.alerts.length, 1); assert.equal(h.updates.length, 0);
  h.input.value = 'Safety'; h.context.issueUpdatesPending = 1; h.context.assignNoteIssue({});
  assert.equal(h.updates.length, 0);
});

test('Change uses the same current fragment for both resolved and unresolved issues', () => {
  const h = harness();
  h.input.focus = () => {};
  for (const name of ['changeDiagnosisResolved', 'changeDiagnosisUnresolved']) {
    h.context[name]('3');
    h.context.saveIssueId(h.input, { id: 12, textContent: 'Safety' });
    h.context.assignNoteIssue({});
    assert.equal(h.updates.at(-1).div, 'sig31');
    assert.equal(h.updates.at(-1).method, 'ajaxChangeDiagnosis');
    assert.equal(h.note.change_diagnosis_id.value, '3');
  }
});

test('filter show and hide follow the rendered state after AJAX replaces the panel', () => {
  const h = harness();
  for (let cycle = 0; cycle < 2; cycle++) {
    assert.equal(h.context.showFilter(), false);
    assert.equal(h.fields.filter.style.display, 'block');
    assert.equal(h.fields.notesFilterToggle.getAttribute('aria-expanded'), 'true');
    h.context.showFilter();
    assert.equal(h.fields.filter.style.display, 'none');
    assert.equal(h.fields.notesFilterToggle.getAttribute('aria-expanded'), 'false');
    h.fields.filter = element('div', { style: { display: 'none' } });
  }
});

test('both fixed issue heading handlers request only the issue constraint to clear', () => {
  const h = harness();
  const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/encounter/LeftNavBarDisplay.jsp'), 'utf8');
  const branch = jsp.match(/else if \("unresolvedIssues"\.equals\(navbarName\) \|\| "resolvedIssues"\.equals\(navbarName\)\)[\s\S]*?onclick="([^"]+)"/);
  assert.ok(branch, 'Both issue module names must dispatch to the fixed handler');
  assert.match(branch[0], /<button type="button"/, 'The filter action must use a native button');
  for (const name of ['unresolvedIssues', 'resolvedIssues']) {
    h.fields.check_issue.value = name;
    vm.runInContext(`(function () { ${branch[1]} })()`, h.context);
    assert.equal(h.fields.check_issue.value, name, 'Heading must not mutate filters before the save guard');
    assert.equal(h.requests.at(-1).reset, false);
    assert.equal(h.requests.at(-1).clearIssues, true);
  }
});

test('Reset clears single fields and RadioNodeList collections and tolerates absent filters', () => {
  const h = harness();
  const single = element('input', { checked: true });
  const fields = [element('input', { checked: true }), element('input', { checked: true })];
  Object.defineProperty(fields, Symbol.toStringTag, { value: 'RadioNodeList' });
  h.context.resetInputElements(single); h.context.resetInputElements(fields); h.context.resetInputElements(undefined);
  assert.equal(single.checked, false); assert.ok(fields.every(field => !field.checked));
});

for (const outcome of ['pending issues', 'cancel', 'save failure']) {
  test(`filter preserves the original form/editor on ${outcome}`, () => {
    const h = harness(); let attemptedSwitch = false;
    vm.runInContext(block('    function filter(', '// find index of month'), h.context);
    h.context.caseNote = 'editor';
    h.context.deferNoteSaveUntilIssues = () => outcome === 'pending issues';
    h.context.changeToView = () => { attemptedSwitch = true; return false; };
    const before = JSON.stringify(h.note.elements);
    assert.equal(h.context.filter(true), false);
    assert.equal(JSON.stringify(h.note.elements), before);
    assert.equal(h.fields.check_issue.value, '73');
    assert.equal(h.requests.length, 0);
    assert.equal(attemptedSwitch, outcome !== 'pending issues');
  });
}

test('filter resumes after pending issues and successful save, before mutating its request fields', () => {
  const h = harness(); let afterIssues; let afterSave; const actions = [];
  vm.runInContext(block('    function filter(', '// find index of month'), h.context);
  h.context.caseNote = 'editor';
  h.context.deferNoteSaveUntilIssues = callback => { afterIssues = callback; return true; };
  h.context.changeToView = (id, callback) => { actions.push('save'); afterSave = callback; return false; };
  h.context.filter(true);
  assert.deepEqual(actions, []);
  h.context.deferNoteSaveUntilIssues = () => false; afterIssues();
  assert.deepEqual(actions, ['save']);
  assert.equal(h.fields.check_issue.value, '73');
  // A sentinel marks the first request-building statement, reached only on a successful save.
  Object.defineProperty(h.note, 'method', { get() { actions.push('build request'); throw new Error('request sentinel'); } });
  h.context.changeToView = () => true;
  assert.throws(() => afterSave(), /request sentinel/);
  assert.deepEqual(actions, ['save', 'build request']);
});

test('an assignment without text changes is saved before changing the editor', () => {
  const h = harness(); const saves = []; const finished = [];
  vm.runInContext(block('    function changeToView(', '    function finishChangeToView('), h.context);
  h.fields.editor = { value: 'unchanged', parentNode: { id: 'n31' } };
  h.fields.observationDate = { value: 'same date' };
  h.context.origCaseNote = 'unchanged'; h.context.origObservationDate = 'same date';
  h.context.$F = id => h.fields[id].value;
  h.context.deferNoteSaveUntilIssues = () => false;
  h.context.unsavedNoteWarning = 'Save note?'; h.context.confirm = () => true;
  h.context.ajaxSaveNote = (sig, id, text, done) => saves.push(done);
  h.context.finishChangeToView = () => finished.push(true);
  h.context.saveIssueId(h.input, { id: 12, textContent: 'Safety' });
  h.context.assignNoteIssue({});
  assert.equal(h.context.changeToView('editor', () => finished.push('navigate')), false);
  assert.equal(saves.length, 1); assert.equal(finished.length, 0);
  saves[0](false); assert.equal(finished.length, 0);
  saves[0](true); assert.deepEqual(finished, [true, 'navigate']);
});

test('cancelling Exit after an assignment-only edit keeps the chart open', () => {
  const h = harness(); let closed = false; let stopped = false;
  vm.runInContext(block('    var closeWithoutSaveMsg;', '    // Bind each newly rendered editor'), h.context);
  h.context.lostNoteLock = false; h.context.origCaseNote = 'same'; h.context.$F = () => 'same';
  h.context.caseNote = 'editor'; h.context.origObservationDate = 'same date';
  h.fields.observationDate = { value: 'same date' }; h.context.noteIssuesChanged = true;
  h.context.confirm = () => false; h.context.clearAutoSaveTimer = () => { stopped = true; };
  h.context.window = { close() { closed = true; } };
  assert.equal(h.context.closeEnc({}), false);
  assert.equal(closed, false); assert.equal(stopped, false);
});

test('toolbar Save retains the dirty editor and selected issues on a stale-revision 409', () => {
  const h = harness(); const requests = []; let restored = 0; let timers = 0; let replaced = 0;
  vm.runInContext(block('    function saveNoteAjax(', '    function savePage('), h.context);
  Object.assign(h.context, { lostNoteLock: false, noteLockLostError: 'note lock lost', sessionExpiredError: 'session expired', DOMParser: class {}, chartActionUrl: path => '/carlos'+path, destroyNoteIssueAutocomplete() {}, caseNote: 'editor', caisiEnabled: false, deferNoteSaveUntilIssues: () => false,
    $F: () => 'dirty draft', clearAutoSaveTimer() {}, setTimer() { timers++; }, savingNoteError: 'Save failed',
    beginNoteSwitchSave: () => () => { restored++; }, Form: { serialize: () => 'note=dirty&issue=12' },
    CarlosAjax: { request: (url, options) => requests.push(options) } });
  h.fields.notCPP = { update() { replaced++; } };
  Object.assign(h.note, { method: {}, ajax: {}, chain: {}, includeIssue: {} });
  h.context.saveNoteAjax('save', 'list');
  assert.equal(replaced, 0, 'Editor must survive while Save is pending');
  requests[0].onFailure({ status: 409, responseText: '<h1>Conflict</h1>' });
  requests[0].onComplete();
  assert.equal(replaced, 0); assert.equal(restored, 1); assert.equal(timers, 0);
  assert.deepEqual(h.alerts, ['note lock lost']);
});

test('CPP save conflict reopens the filled dialog without replacing the saved row', () => {
  const h = harness(); const requests = []; let rowReplaced = false;
  vm.runInContext(block('function updateCPPNote()', '    function clickLoadDiv('), h.context);
  Object.assign(h.context, { sanitizeElementByPattern() {}, CONTROL_CHAR_PATTERN_2: '', caseNote: 'caseNote_note31',
    savingNoteError: 'Save failed', CarlosAjax: { request: (url, options) => requests.push(options) } });
  h.cpp.elements.noteEditTxt = { value: 'dirty CPP draft' };
  Object.assign(h.cpp, { action: '/CaseManagementEntry?method=issueNoteSave', serialize: () => 'noteEditTxt=dirty&issue_id=12' });
  Object.assign(h.fields, { frmIssueNotes: h.cpp, reloadUrl: { value: '/reload' }, containerDiv: { value: 'cppList' },
    channel: { style: {} }, showEditNote: { style: { display: 'block' } }, cppList: { update() { rowReplaced = true; } } });
  h.context.updateCPPNote();
  requests[0].onFailure({ status: 409 });
  assert.equal(h.fields.showEditNote.style.display, 'block');
  assert.equal(h.cpp.elements.noteEditTxt.value, 'dirty CPP draft');
  assert.equal(rowReplaced, false); assert.deepEqual(h.alerts, ['Save failed 409']);
});

test('assignment controls stay disabled through the sidebar refresh and reenable on completion', () => {
  const h = harness(); h.context.issueUpdatesPending = 1;
  h.context.initializeIssueAutocomplete(h.document);
  assert.equal(h.input.disabled, true); assert.equal(h.button.disabled, true);
  vm.runInContext(block('    function setNoteIssueControlsBusy(', '    function ajaxUpdateIssues('), h.context);
  h.context.queuedNoteSave = null; h.context.issueUpdateFailed = false;
  h.context.finishIssueUpdate(true);
  assert.equal(h.input.disabled, false); assert.equal(h.button.disabled, false);
});

for (const count of [1, 2, 3]) {
  test(`existing CPP markup labels ${count} checkboxes without duplicate ids or unmatched rows`, () => {
    const context = { noteIssues: Array.from({length: count}, (_, i) => `${i + 1};TEST;Issue ${i + 1} & <text>`).join(';'), getCPP: () => '' };
    vm.createContext(context);
    vm.runInContext(block('    function escapeNoteText(', '    function completeChangeToView(')
      + block('        var noteIssueUl =', '        var noteInfo ='), context);
    const html = context.noteIssueUl;
    assert.equal((html.match(/<label><input type='checkbox' name='issue_id'/g) || []).length, count);
    assert.equal((html.match(/<\/label>/g) || []).length, count);
    assert.equal((html.match(/<li>/g) || []).length, Math.ceil(count / 2));
    assert.equal((html.match(/<\/li>/g) || []).length, Math.ceil(count / 2));
    assert.doesNotMatch(html, /id='issueId'/);
    assert.match(html, /&amp; &lt;text&gt;/);
  });
}

test('new and existing note textareas preserve raw values, names and natural keyboard order', () => {
  const label = `Encounter "note's" <literal>`;
  const context = { noteEditorLabel: label, document: { createElement: tag => {
    assert.equal(tag, 'textarea');
    return { style: {}, attributes: {}, setAttribute(name, value) { this.attributes[name] = value; } };
  } } };
  vm.createContext(context);
  vm.runInContext(block('    function createNoteTextarea(', '    function editNote('), context);
  for (const editing of [false, true]) {
    for (const value of ['', '\nleading newline', '\n\n<&> &amp; "quoted" </textarea><img src=x> 🩺\n']) {
      const input = context.createNoteTextarea('caseNote_note02', value, editing);
      assert.equal(input.value, value);
      assert.equal(input.attributes['aria-label'], label);
      assert.equal(input.id, 'caseNote_note02');
      assert.equal(input.name, 'caseNote_note');
      assert.equal(input.wrap, 'soft');
      assert.equal(input.cols, 84);
      assert.equal(input.rows, editing ? 10 : 1);
      assert.equal(input.className, 'txtArea boxsizingBorder' + (editing ? ' edit-textarea' : ''));
      assert.equal(input.style.lineHeight, editing ? '1.1em' : '1.0em');
      assert.equal(input.attributes.tabindex, undefined);
      assert.equal(input.innerHTML, undefined);
      assert.equal(input.textContent, undefined);
    }
  }
  assert.match(source, /var noteEditorLabel = "\$\{carlos:forJavaScriptBlock\(noteEditorLabel\)\}"/);
});

function headingFilterHarness() {
  const h = harness(); const saves = [];
  const view = { method: {value: ''}, resetFilter: {value: 'false'},
    filter_providers: [element('input', {checked: true, value: '200'}), element('input', {checked: false, value: '201'})],
    filter_roles: [element('input', {checked: true, value: '7'}), element('input', {checked: false, value: '8'})],
    note_sort: [element('input', {checked: false, value: 'date'}), element('input', {checked: true, value: 'name'})],
    issues: [element('input', {checked: true, value: '73'}), element('input', {checked: true, value: '74'})] };
  view.elements = ['filter_providers', 'filter_roles', 'note_sort', 'issues'].flatMap(name => view[name].map(field => Object.assign(field, {name})));
  h.document.forms.caseManagementViewForm = view;
  for (const name of ['method', 'note_edit', 'noteId', 'ajax', 'chain']) h.note[name] = { value: 'original' };
  h.note.cloneNode = () => h.note;
  h.fields.editor = {value: 'dirty draft', parentNode: {id: 'n31'}};
  h.fields.observationDate = {value: 'same date'};
  Object.assign(h.context, {caseNote: 'editor', origCaseNote: '', origObservationDate: 'same date', noteIssuesChanged: true,
    noteSwitchSavePending: false, issueUpdateFailed: false, queuedNoteSave: null,
    updateIssueError: 'Issue update failed', noteSavingMessage: 'Saving', unsavedNoteWarning: 'Save draft?',
    $F: id => h.fields[id].value, confirm: () => true,
    ajaxSaveNote: (sig, id, text, done) => saves.push(done),
    finishChangeToView: () => { delete h.fields.editor; h.context.noteIssuesChanged = false; },
    Form: {serialize: form => JSON.stringify(form === view ? view : h.note.elements)},
    CarlosAjax: {request: (url, options) => h.requests.push(options)} });
  vm.runInContext(block('    function filter(', '// find index of month')
    + block('    function changeToView(', '    function finishChangeToView(')
    + block('    function deferNoteSaveUntilIssues(', '    function ajaxUpdateIssues('), h.context);
  return {...h, view, saves, snapshot: () => JSON.stringify([view, h.fields.check_issue, h.note.elements])};
}

for (const outcome of ['pending issues', 'issue failure', 'save pending', 'cancel', 'save failure']) {
  test(`issue heading preserves every filter and the dirty editor on ${outcome}`, () => {
    const h = headingFilterHarness(); const before = h.snapshot();
    h.context.issueUpdatesPending = outcome === 'pending issues' ? 1 : 0;
    h.context.issueUpdateFailed = outcome === 'issue failure';
    h.context.noteSwitchSavePending = outcome === 'save pending';
    h.context.confirm = () => outcome !== 'cancel';
    assert.equal(h.context.clearIssueFilter(), false);
    if (outcome === 'pending issues') {
      assert.equal(h.snapshot(), before);
      h.context.finishIssueUpdate(false);
    }
    if (outcome === 'save failure') h.saves[0](false);
    assert.equal(h.snapshot(), before);
    assert.equal(h.fields.editor.value, 'dirty draft');
    assert.equal(h.requests.length, 0);
  });
}

test('issue heading resumes after pending assignments and successful save, clearing only issue controls', () => {
  const h = headingFilterHarness(); const before = h.snapshot();
  h.context.issueUpdatesPending = 1;
  h.context.clearIssueFilter();
  assert.equal(h.saves.length, 0); assert.equal(h.snapshot(), before);
  h.context.finishIssueUpdate(true);
  assert.equal(h.saves.length, 1); assert.equal(h.snapshot(), before);
  h.saves[0](true);
  assert.equal(h.fields.check_issue.value, '');
  assert.ok(h.view.issues.every(field => !field.checked));
  assert.deepEqual(h.view.filter_providers.map(field => field.checked), [true, false]);
  assert.deepEqual(h.view.filter_roles.map(field => field.checked), [true, false]);
  assert.deepEqual(h.view.note_sort.map(field => field.checked), [false, true]);
  assert.equal(h.view.resetFilter.value, false);
  assert.equal(h.requests.length, 1);
});

test('explicit Reset still clears provider, role, sort and both issue controls', () => {
  const h = headingFilterHarness();
  delete h.fields.editor;
  h.context.filter(true);
  for (const name of ['filter_providers', 'filter_roles', 'note_sort', 'issues']) {
    assert.ok(h.view[name].every(field => !field.checked));
  }
  assert.equal(h.fields.check_issue.value, '');
  assert.equal(h.view.resetFilter.value, true);
  assert.equal(h.requests.length, 1);
});

for (const mode of ['apply', 'heading', 'reset']) {
  test(`${mode} restores its resulting selections and legacy issue value after replacing the form`, () => {
    const h = headingFilterHarness();
    delete h.fields.editor;
    h.context.filter(mode === 'reset', mode === 'heading');
    const selectedLegacyIssue = h.fields.check_issue.value;
    const replacement = headingFilterHarness().view;
    replacement.elements.forEach(field => { field.checked = false; });
    h.fields.notCPP = { update() {
      h.document.forms.caseManagementViewForm = replacement;
      h.fields.check_issue = {value: ''};
    } };
    h.requests[0].onSuccess({responseText: 'replacement filter form'});
    for (const name of ['filter_providers', 'filter_roles', 'note_sort', 'issues']) {
      assert.deepEqual(replacement[name].map(field => field.checked), h.view[name].map(field => field.checked));
    }
    assert.equal(h.fields.check_issue.value, selectedLegacyIssue);
  });
}
