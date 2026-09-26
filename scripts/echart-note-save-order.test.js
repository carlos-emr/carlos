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
