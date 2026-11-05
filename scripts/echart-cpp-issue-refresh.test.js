/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { chartActionUrlSource } = require('./lib/chart-action-url-source');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const ajaxSource = fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/carlos-ajax.js'), 'utf8');
const chartHelper = chartActionUrlSource(source);

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
      h.updates[0].options.onComplete({ updateSucceeded: true, status: 200 });
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
  h.updates[0].options.onComplete({ updateSucceeded: true, status: 200 });
  assert.deepEqual(checkboxes.map(box => box.checked), [false, false, true]);
  assert.equal(saved, undefined);
  h.requests[1].options.onSuccess({ status: 200, responseText: '' });
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
  h.updates[0].options.onComplete({ updateSucceeded: true, status: 200 });
  assert.equal(h.requests.length, 1);
  h.requests[0].options.onSuccess({ status: 200, responseText: '' });
  h.requests[0].options.onComplete({ status: 200 });
  assert.deepEqual(h.errors, []);
});

test('an encounter save requested before the CPP response waits for both refreshes and restored selections', () => {
  const h = harness();
  const checkbox = (id, checked) => ({ checked, getAttribute: () => id });
  let boxes = [checkbox('41', true), checkbox('42', false)];
  h.context.document.forms.caseManagementEntryForm.querySelectorAll = () => boxes;
  h.context.updateCPPNote();
  assert.equal(h.context.issueUpdatesPending, 1, 'register before sending the CPP request');
  const saves = [];
  assert.equal(h.context.deferNoteSaveUntilIssues(() => saves.push('old choice')), true);
  assert.equal(h.context.deferNoteSaveUntilIssues(() => saves.push(boxes.map(box => box.checked))), true);
  h.requests[0].options.onSuccess({ status: 200, responseText: '' });
  assert.deepEqual(saves, []);
  assert.equal(h.context.issueUpdatesPending, 1, 'transfer the existing slot to the refresh');
  h.updates[0].options.onSuccess({ status: 200 });
  boxes = [checkbox('42', true), checkbox('43', false), checkbox('41', false)];
  h.updates[0].options.onComplete({ updateSucceeded: true, status: 200 });
  assert.deepEqual(saves, []);
  h.requests[1].options.onSuccess({ status: 200, responseText: '' });
  h.requests[1].options.onComplete({ status: 200 });
  assert.deepEqual(saves, [[false, false, true]]);
  assert.equal(h.context.issueUpdatesPending, 0);
  h.requests[1].options.onComplete({ status: 200 });
  assert.deepEqual(saves, [[false, false, true]], 'completion cannot release or save twice');
  assert.equal(h.context.issueUpdatesPending, 0);
});

for (const phase of ['CPP POST', 'note refresh', 'Unresolved Issues', 'panel rendering', 'request setup', 'refresh setup', 'selection restore']) {
  test(`a failed ${phase} releases the CPP barrier, cancels the queued save, and permits explicit recovery`, () => {
    const h = harness();
    let saves = 0;
    if (phase === 'request setup') {
      h.context.CarlosAjax.request = () => {
        assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), true);
        throw new Error('request setup failed');
      };
    }
    h.context.updateCPPNote();
    if (phase !== 'request setup') {
      h.context.deferNoteSaveUntilIssues(() => saves++);
      if (phase === 'CPP POST') h.requests[0].options.onFailure({ status: 500 });
      else {
        if (phase === 'refresh setup') h.context.CarlosAjax.updater = () => { throw new Error('refresh setup failed'); };
        h.requests[0].options.onSuccess({ status: 200, responseText: '' });
        if (phase !== 'refresh setup') {
          h.updates[0].options.onSuccess({ status: 200 });
          if (phase === 'selection restore') {
            h.context.document.forms.caseManagementEntryForm.querySelectorAll = () => { throw new Error('restore failed'); };
          }
          h.updates[0].options.onComplete({ updateSucceeded: true, status: phase === 'note refresh' ? 500 : 200 });
          if (['Unresolved Issues', 'panel rendering'].includes(phase)) {
            if (phase === 'panel rendering') {
              h.fields.unresolvedIssueslist.update = () => { throw new Error('render failed'); };
              assert.throws(() => h.requests[1].options.onSuccess({ status: 200, responseText: '' }), /render failed/);
            }
            h.requests[1].options.onComplete({ status: phase === 'Unresolved Issues' ? 500 : 200 });
          }
        }
      }
    }
    assert.equal(h.context.issueUpdatesPending, 0);
    assert.equal(h.context.issueUpdateFailed, true);
    assert.equal(h.context.queuedNoteSave, null);
    assert.equal(saves, 0);
    assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), true, 'incomplete issue state blocks a new save');
    // A fresh, successful refresh recovers the barrier, but never resurrects the failed save.
    h.context.CarlosAjax.updater = (target, url, options) => h.updates.push({ target, url, options });
    h.context.loadDiv = (div, url, limit, complete) => complete(true);
    h.context.ajaxUpdateIssues('edit', 'sig01');
    h.updates.at(-1).options.onComplete({ updateSucceeded: true, status: 200 });
    assert.equal(h.context.issueUpdatesPending, 0);
    assert.equal(h.context.issueUpdateFailed, false);
    assert.equal(saves, 0);
    assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), false);
  });
}

test('a CPP save cannot start while an encounter save owns the editor', () => {
  const h = harness();
  h.context.noteSwitchSavePending = true;
  h.context.updateCPPNote();
  assert.equal(h.requests.length, 0);
  assert.equal(h.context.issueUpdatesPending, 0);
  assert.equal(h.alerts.length, 1);
});

test('a CPP refresh waits behind an older editor refresh so its new issues cannot be overwritten', () => {
  const h = harness();
  const checkbox = (id, checked) => ({ checked, getAttribute: () => id });
  let boxes = [checkbox('41', true), checkbox('42', false)];
  h.context.document.forms.caseManagementEntryForm.querySelectorAll = () => boxes;
  h.context.ajaxUpdateIssues('edit', 'sig01');
  h.context.updateCPPNote();
  let saved;
  h.context.deferNoteSaveUntilIssues(() => { saved = boxes.map(box => [box.getAttribute(), box.checked]); });
  h.requests[0].options.onSuccess({ status: 200, responseText: '' });
  assert.equal(h.updates.length, 1, 'do not send the CPP refresh while the old editor request is pending');
  h.updates[0].options.onSuccess({ status: 200 });
  h.updates[0].options.onComplete({ updateSucceeded: true, status: 200 });
  assert.equal(h.updates.length, 1, 'also wait for the old Unresolved Issues request');
  h.requests[1].options.onSuccess({ status: 200, responseText: 'old issues' });
  h.requests[1].options.onComplete({ status: 200 });
  assert.equal(h.updates.length, 2);
  assert.equal(saved, undefined);
  h.updates[1].options.onSuccess({ status: 200 });
  boxes = [checkbox('43', false), checkbox('42', true), checkbox('41', false)];
  h.updates[1].options.onComplete({ updateSucceeded: true, status: 200 });
  assert.equal(saved, undefined);
  h.requests[2].options.onSuccess({ status: 200, responseText: 'CPP-created issues' });
  h.requests[2].options.onComplete({ status: 200 });
  assert.equal(h.fields.issuesText, 'CPP-created issues');
  assert.deepEqual(saved, [['43', false], ['42', false], ['41', true]]);
  assert.equal(h.context.issueUpdatesPending, 0);
  assert.equal(h.context.issueRefreshPending, false);
});

for (const rendering of ['success', 'throws', 'missing target']) {
  test(`the real AJAX adapter ${rendering === 'success' ? 'releases a save after rendering both fragments' : `blocks a save when note rendering ${rendering}`}`, () => {
    const h = harness(); const xhrs = [];
    h.context.document.querySelector = () => null;
    h.context.document.getElementById = id => h.fields[id] || null;
    if (rendering !== 'missing target') h.fields.sig01 = {
      set innerHTML(value) {
        if (rendering === 'throws') throw new Error('synthetic DOM installation failure');
        h.fields.noteFragment = value;
      },
    };
    h.context.XMLHttpRequest = class {
      constructor() { xhrs.push(this); }
      open(method, url) { this.responseURL = url; }
      setRequestHeader() {}
      send() {}
      respond(responseText) { this.status = 200; this.responseText = responseText; this.onload(); }
    };
    vm.runInContext(ajaxSource, h.context);
    let saves = 0;
    h.context.updateCPPNote();
    h.context.deferNoteSaveUntilIssues(() => saves++);
    xhrs[0].respond('');
    xhrs[1].respond('<p>current note issues</p>');
    assert.equal(saves, 0);
    if (rendering === 'success') {
      assert.equal(h.fields.noteFragment, '<p>current note issues</p>');
      assert.equal(h.context.issueUpdatesPending, 1);
      xhrs[2].respond('<p>current unresolved issues</p>');
      assert.equal(saves, 1);
      assert.equal(h.context.issueUpdateFailed, false);
    } else {
      assert.equal(xhrs.length, 2, 'failed note rendering cannot advance to a successful sidebar completion');
      assert.equal(h.context.issueUpdateFailed, true);
      assert.equal(h.context.deferNoteSaveUntilIssues(() => saves++), true);
      assert.deepEqual(h.alerts, ['issue refresh failed', 'issue refresh failed']);
    }
    assert.equal(h.context.issueUpdatesPending, 0);
    assert.equal(h.context.issueRefreshPending, false);
    assert.equal(h.context.queuedNoteSave, null);
  });
}
