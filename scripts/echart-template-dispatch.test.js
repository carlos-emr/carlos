/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/webapp/js/newCaseManagementView.js.jsp', 'utf8');
function section(from, to) { return source.slice(source.indexOf(from), source.indexOf(to, source.indexOf(from))); }
test('template closures preserve hostile names and legacy module commands still dispatch', () => {
  const name = "Patient's A&B + ü; <script>";
  const requests = [];
  const field = {value: name};
  const context = {ctx: '/carlos', document: {getElementById: () => field}, autoCompleted: {},
    CarlosAjax: {request: (url, opts) => requests.push({url, opts})},
    writeToEncounterNote() {}, initTemplateFeatures() {}, insertTemplateError: 'failed', alert() {}};
  vm.createContext(context);
  vm.runInContext(section('    function ajaxInsertTemplate(', '    /**'), context);
  vm.runInContext(section('    function menuAction()', '    function grabEnterGetTemplate('), context);
  context.autoCompleted[name] = () => context.ajaxInsertTemplate(name);
  context.menuAction();
  assert.equal(new URLSearchParams(requests[0].opts.postBody).get('templateName'), name);
  context.autoCompleted.Forms = 'legacyOpened = true;'; field.value = 'Forms';
  context.menuAction(); assert.equal(context.legacyOpened, true);
});

test('CPP issue components preserve delimiters, Unicode and literal percent escapes', () => {
  const description = "Patient's; Other Meds %20 <b>ü</b>";
  const payload = ['17', 'A;B', description].map(encodeURIComponent).join(';');
  const start = source.indexOf('        var noteIssueUl =');
  const end = source.indexOf('        var noteInfo =', start);
  const context = {noteIssues: payload, getCPP: () => ''};
  vm.createContext(context);
  vm.runInContext(section('    function escapeNoteText(', '    function completeChangeToView('), context);
  vm.runInContext(source.slice(start, end), context);
  assert.match(context.noteIssueUl, /value='17'/);
  assert.ok(context.noteIssueUl.includes("Patient&#39;s; Other Meds %20 &lt;b&gt;ü&lt;/b&gt;"));
  assert.equal(context.noteIssueUl.includes('<b>'), false);
});
