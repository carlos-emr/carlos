/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const {assertTicklerFailureDialog, ticklerFailureDiagnostics} = require('./get-reject-chart-notes-playwright-checks');

const english = fs.readFileSync(path.join(__dirname, '../src/main/resources/oscarResources_en.properties'), 'utf8');
const saveFailure = english.match(/^tickler\.ticklerMain\.errorNoteSaveFailed=(.+)$/m)?.[1];
assert.ok(saveFailure, 'The shipping tickler note failure translation is missing');

function dialog(file, status) {
  const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/tickler', file), 'utf8');
  const start = source.indexOf('function saveNoteDialog()');
  assert.ok(start > 0, `${file} has no save handler`);
  const end = source.indexOf('\n            }', start);
  assert.ok(end > start, `${file} has no complete save handler`);
  const script = source.slice(start, end + '\n            }'.length)
    .replace(/<%=\s*request.getContextPath\(\)\s*%>/g, '/carlos')
    .replace(/alert\('<carlos:encode\b[\s\S]*?\/>'\)/g, `alert(${JSON.stringify(saveFailure)})`)
    .replace(/alert\('\$\{carlos:forJavaScriptBlock\(ticklerNoteSaveFailed\)\}'\)/g,
      `alert(${JSON.stringify(saveFailure)})`);
  const inputs = {
    tickler_note: {value: 'Unsaved H&P + retained clinical text'},
    tickler_note_noteId: {value: '17'}, tickler_note_demographicNo: {value: '101'},
    tickler_note_ticklerNo: {value: '44'},
  };
  const state = {closed: 0, alerts: [], requests: []};
  const jquery = selector => ({dialog(operation) {
    assert.equal(selector, '#note-form');
    assert.equal(operation, 'close');
    state.closed++;
  }});
  jquery.ajax = options => {
    state.requests.push(options);
    if (status === 200) options.success('');
    else options.error({status}, 'error', '');
  };
  const context = {jQuery: jquery, ctx: '/carlos', console: {error() {}},
    alert: value => state.alerts.push(value), document: {getElementById: id => inputs[id]}};
  vm.createContext(context);
  vm.runInContext(script, context);
  context.saveNoteDialog();
  return {state, inputs};
}

for (const file of ['ticklerMain.jsp', 'ticklerDemoMain.jsp']) {
  test(`${file} closes only after its POST is acknowledged`, () => {
    const {state, inputs} = dialog(file, 200);
    assert.equal(state.closed, 1);
    assert.deepEqual(state.alerts, []);
    assert.equal(state.requests.length, 1);
    const request = state.requests[0];
    assert.equal(request.type || request.method, 'POST');
    assert.equal(request.url, '/carlos/CaseManagementEntry');
    assert.equal(request.data.method, 'ticklerSaveNote');
    assert.equal(request.data.value, inputs.tickler_note.value);
    assert.equal(request.data.demographicNo, '101');
    assert.equal(request.data.ticklerNo, '44');
    assert.equal(request.data.noteId, '17');
  });

  for (const status of [0, 403, 500]) {
    test(`${file} retains its open editor and exact text after HTTP ${status}`, () => {
      const {state, inputs} = dialog(file, status);
      assert.equal(state.closed, 0);
      assert.equal(inputs.tickler_note.value, 'Unsaved H&P + retained clinical text');
      assert.equal(state.alerts.length, 1);
      assert.equal(state.alerts[0], saveFailure);
      assertTicklerFailureDialog([{type: 'alert', text: state.alerts[0]}], file);
    });
  }
}

for (const [label, dialogs] of [
  ['missing', []],
  ['duplicate', [{type: 'alert', text: saveFailure}, {type: 'alert', text: saveFailure}]],
  ['wrong type', [{type: 'confirm', text: saveFailure}]],
  ['wrong message', [{type: 'alert', text: 'A different error'}]],
]) {
  test(`the failure probe refuses a ${label} alert`, () => {
    assert.throws(() => assertTicklerFailureDialog(dialogs, 'main'), /exactly one explanatory alert/);
  });
}

const requestUrl = 'https://example.test/carlos/CaseManagementEntry';
const pageUrl = 'https://example.test/carlos/tickler/ViewTicklerMain?demoview=101';
const csrfGuardUrl = 'https://example.test/carlos/csrfguard';
const resource = url => ({type: 'error', text: 'Failed to load resource: the server responded with a status of 403 ()',
  location: {url}});
const application = {type: 'error', text: '[ticklerMain] Failed to save note (HTTP 403): ', location: {url: pageUrl}};

for (const variant of ['main', 'legacy']) {
  for (const origin of [requestUrl, csrfGuardUrl]) {
    test(`${variant} attributes its unique controlled 403 at ${new URL(origin).pathname}`, () => {
      const own = resource(origin);
      const unrelated = {type: 'error', text: 'Unrelated application failure', location: {url: pageUrl}};
      const entries = [own, ...(variant === 'main' ? [application] : []), unrelated];
      const acknowledged = ticklerFailureDiagnostics(entries, {variant, pageUrl, requestUrl, csrfGuardUrl});
      assert.deepEqual(acknowledged, [own, ...(variant === 'main' ? [application] : [])]);
      assert.deepEqual(entries.filter(entry => !acknowledged.includes(entry)), [unrelated]);
    });
  }
}

for (const [label, entries] of [
  ['missing resource', [application]],
  ['duplicate resources', [resource(requestUrl), resource(csrfGuardUrl), application]],
  ['foreign resource', [resource('https://example.test/carlos/other-resource'), application]],
  ['missing application callback', [resource(csrfGuardUrl)]],
]) {
  test(`the failure probe refuses ${label} diagnostics`, () => {
    assert.throws(() => ticklerFailureDiagnostics(entries,
      {variant: 'main', pageUrl, requestUrl, csrfGuardUrl}), /missing or ambiguous diagnostics/);
  });
}
