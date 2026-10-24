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

function dialog(file, status) {
  const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/tickler', file), 'utf8');
  const start = source.indexOf('function saveNoteDialog()');
  assert.ok(start > 0, `${file} has no save handler`);
  const end = source.indexOf('\n            }', start);
  assert.ok(end > start, `${file} has no complete save handler`);
  const script = source.slice(start, end + '\n            }'.length)
    .replace(/<%=\s*request.getContextPath\(\)\s*%>/g, '/carlos')
    .replace(/<carlos:encode\b[\s\S]*?\/>/g, 'The note could not be saved. Please try again.')
    .replace(/\$\{carlos:forJavaScriptBlock\(ticklerNoteSaveFailed\)\}/g,
      'The note could not be saved. Please try again.');
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
      assert.match(state.alerts[0], /note could not be saved.*try again/i);
    });
  }
}
