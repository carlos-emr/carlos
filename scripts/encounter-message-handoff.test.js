/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotesAjax.jsp'), 'utf8');
const start = source.indexOf('    var encounterMessage =');
const end = source.indexOf('<% if (noteBody != null)', start);
assert.ok(start > 0 && end > start);
const script = source.slice(start, end).replace(
  '"<carlos:encode value=\'<%= oscarMsg %>\' context="javaScriptBlock"/>"', JSON.stringify('legacy message'));

function harness(pending, legacyLayout = false) {
  const editor = { value: 'draft' };
  const context = { caseNote: 'editor', document: { getElementById: () => editor } };
  if (!legacyLayout) context.pendingEncounterMessage = pending;
  vm.createContext(context);
  return { editor, context, render: () => vm.runInContext(script, context) };
}

test('the chart-local message is consumed once despite a stale shared bean', () => {
  const h = harness('current patient message');
  h.render(); h.render();
  assert.equal(h.editor.value, 'draft\n\ncurrent patient message');
  assert.equal(h.context.pendingEncounterMessage, '');
});

test('a new chart without a requested message never inherits the shared bean message', () => {
  const h = harness(''); h.render();
  assert.equal(h.editor.value, 'draft');
});

test('legacy layouts without a chart-local handoff retain the legacy fallback', () => {
  const h = harness(undefined, true); h.render();
  assert.equal(h.editor.value, 'draft\n\nlegacy message');
});

test('a temporarily absent editor does not consume the pending message', () => {
  const h = harness('pending');
  h.context.document.getElementById = () => null; h.render();
  assert.equal(h.context.pendingEncounterMessage, 'pending');
  h.context.document.getElementById = () => h.editor; h.render();
  assert.equal(h.editor.value, 'draft\n\npending');
});
