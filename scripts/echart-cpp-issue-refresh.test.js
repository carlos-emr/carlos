/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const chartHelper = source.slice(source.indexOf('    function chartActionUrl('), source.indexOf('    var caisiEnabled'));

// Run the production CPP save and its two-stage issue refresh, including the
// barrier that keeps an encounter save from racing stale issue fields (#4699).
function harness(checked = [true], issueChange = '') {
  const requests = []; const updates = []; const alerts = []; const errors = [];
  let changeValue = issueChange;
  const fields = {
    reloadUrl: { value: '/reload' }, containerDiv: { value: 'cpp.socialHistory' },
    issueChange: { get value() { return changeValue; }, set value(value) { changeValue = String(value); } },
    channel: { style: {} }, showEditNote: { style: {} },
    'cpp.socialHistory': { id: 'cpp.socialHistory', update: text => { fields.cppText = text; } },
    unresolvedIssueslist: { id: 'unresolvedIssueslist', update: text => { fields.issuesText = text; } },
  };
  const cppForm = {
    action: '/carlos/CaseManagementEntry?method=issueNoteSave',
    elements: { noteEditTxt: { value: 'synthetic CPP item' } },
    querySelectorAll: selector => {
      assert.equal(selector, 'input[name="issue_id"]');
      return checked.map(value => ({ checked: value }));
    },
    serialize: () => `issueChange=${fields.issueChange.value}`,
  };
  fields.frmIssueNotes = cppForm;
  const context = {
    caseNote: 'caseNote_note01', demographicNo: '123', ctx: '/carlos', chartContext: '',
    savingNoteError: 'save failed', CONTROL_CHAR_PATTERN_2: /unused/,
    sanitizeElementByPattern: () => {},
    document: { forms: { frmIssueNotes: cppForm, caseManagementEntryForm: { method: {}, ajax: {}, querySelectorAll: () => [] } } },
    Form: { serialize: () => 'fixture=form' },
    CarlosAjax: {
      request: (url, options) => requests.push({ url, options }),
      updater: (target, url, options) => updates.push({ target, url, options }),
    },
    $: id => fields[id], alert: message => alerts.push(message),
    console: { error: (...args) => errors.push(args) },
  };
  vm.createContext(context);
  vm.runInContext(chartHelper, context);
  for (const [from, to] of [
    ['function updateCPPNote()', '    function clickLoadDiv('],
    ['    function loadDiv(', '    var expandedIssues'],
    ['    var ajaxRequest;', '    function submitIssue('],
  ]) {
    const start = source.indexOf(from); const end = source.indexOf(to, start);
    assert.ok(start > 0 && end > start);
    vm.runInContext(source.slice(start, end), context);
  }
  context.updateIssueError = 'issue refresh failed';
  return { context, requests, updates, fields, alerts, errors };
}

for (const [name, checked, changed] of [
  ['one checked issue', [true], ''],
  ['several checked issues', [true, true], ''],
  ['an unchecked issue', [true, false], ''],
  ['no issue checkboxes', [], ''],
  ['a newly assigned issue', [true, true], 'true'],
]) {
  for (const responseText of ['', '<p>saved CPP item</p>']) {
    test(`CPP save with ${name} refreshes both issue fragments after ${responseText ? 'a nonempty' : 'an empty'} response`, () => {
      const h = harness(checked, changed);
      assert.equal(h.context.updateCPPNote(), false);
      assert.equal(h.requests.length, 1);
      assert.equal(h.updates.length, 0, 'do not refresh before the CPP save succeeds');
      assert.equal(new URLSearchParams(h.requests[0].options.postBody).get('issueChange'),
        checked.includes(false) ? 'true' : changed, 'preserve actual issue-change detection');

      h.requests[0].options.onSuccess({ status: 200, responseText });
      assert.equal(h.updates.length, 1);
      assert.equal(h.updates[0].target.success, 'sig01');
      assert.equal(h.updates[0].url, '/carlos/CaseManagementEntry');
      assert.equal(h.context.document.forms.caseManagementEntryForm.method.value, 'edit');
      let saves = 0;
      assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), true);
      h.updates[0].options.onSuccess({ status: 200 });
      h.updates[0].options.onComplete({ status: 200 });
      assert.equal(saves, 0, 'wait for Unresolved Issues too');
      assert.equal(h.requests.length, 2);
      const refresh = h.requests[1];
      const url = new URL(refresh.url, 'https://example.invalid');
      assert.equal(url.pathname, '/carlos/encounter/displayIssues');
      assert.equal(url.searchParams.get('cmd'), 'unresolvedIssues');
      assert.equal(url.searchParams.get('demographicNo'), '123');
      refresh.options.onSuccess({ status: 200, responseText: '<p>current issues</p>' });
      refresh.options.onComplete({ status: 200 });
      assert.equal(h.fields.issuesText, '<p>current issues</p>');
      assert.equal(saves, 1);
      assert.deepEqual(h.alerts, []);
      assert.deepEqual(h.errors, []);
    });
  }
}

test('a failed CPP save restores the editor without requesting issue refreshes', () => {
  const h = harness();
  h.context.updateCPPNote();
  h.requests[0].options.onFailure({ status: 500 });
  assert.equal(h.fields.showEditNote.style.display, 'block');
  assert.equal(h.fields.channel.style.visibility, 'hidden');
  assert.equal(h.requests.length, 1);
  assert.equal(h.updates.length, 0);
  assert.deepEqual(h.alerts, ['save failed 500']);
  assert.deepEqual(h.errors, []);
});

test('CPP refresh preserves the latest unsaved selections by issue id before a queued save resumes', () => {
  const h = harness();
  const checkbox = (id, checked) => ({ checked, getAttribute: () => id });
  let checkboxes = [checkbox('41', false), checkbox('42', true)];
  h.context.document.forms.caseManagementEntryForm.querySelectorAll = () => checkboxes;
  h.context.updateCPPNote();
  h.requests[0].options.onSuccess({ status: 200, responseText: '' });
  // A clinician can change a checkbox while the refresh request is in flight.
  checkboxes[0].checked = true;
  checkboxes[1].checked = false;
  h.updates[0].options.onSuccess({ status: 200 });
  // edit() rebuilds persisted selections, and a new CPP issue can reorder them.
  checkboxes = [checkbox('43', false), checkbox('42', true), checkbox('41', false)];
  let saved;
  h.context.deferNoteSaveUntilIssues(() => { saved = checkboxes.map(box => box.checked); });
  h.updates[0].options.onComplete({ status: 200 });
  assert.deepEqual(checkboxes.map(box => box.checked), [false, false, true]);
  assert.equal(saved, undefined);
  h.requests[1].options.onComplete({ status: 200 });
  assert.deepEqual(saved, [false, false, true]);
});

test('ordinary editor loads use persisted selections without preserving another note\'s checklist', () => {
  const h = harness();
  h.context.document.forms.caseManagementEntryForm.querySelectorAll = () => {
    throw new Error('initial note load must not inspect the previous editor selections');
  };
  h.context.ajaxUpdateIssues('edit', 'sig01');
  h.updates[0].options.onSuccess({ status: 200 });
  h.updates[0].options.onComplete({ status: 200 });
  assert.equal(h.requests.length, 1);
  h.requests[0].options.onComplete({ status: 200 });
  assert.deepEqual(h.errors, []);
});
