/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar.js'), 'utf8');
const start = source.indexOf('function eformAttachmentSubmissionValue(');
const end = source.indexOf('\n}\n', start) + 3;
assert.ok(start >= 0 && end > start);
const context = vm.createContext({});
vm.runInContext(source.slice(start, end), context);
function value(name, id, labType, unlisted = false) {
  return context.eformAttachmentSubmissionValue({
    val: () => id,
    attr: key => ({ name, 'data-lab-type': labType }[key]),
    hasClass: key => key === 'unlisted_attachment_check' && unlisted,
  });
}
test('toolbar preserves source identity when two lab systems share the same ID', () => {
  assert.equal(value('labNo', '77', 'HL7'), 'HL7:77');
  assert.equal(value('labNo', '77', 'MDS'), 'MDS:77');
  assert.equal(value('docNo', '77'), '77');
});
test('toolbar rejects missing or invalid source metadata instead of submitting an ambiguous lab', () => {
  for (const source of [undefined, '', 'DOC', 'HL7:7']) {
    assert.throws(() => value('labNo', '77', source), /Invalid lab/);
  }
  assert.throws(() => value('labNo', '0', 'HL7'), /Invalid lab/);
});
test('toolbar preserves unresolved stored selections until the user explicitly removes them', () => {
  assert.equal(value('labNo', 'UNRESOLVED:77', undefined, true), 'UNRESOLVED:77');
  assert.equal(value('labNo', 'MDS:77', undefined, true), 'MDS:77');
});

// Execute the real dialog callback with observable list/badge operations. A rejected
// second selection must preserve the original delegates, not just report an error.
function closePicker(selections, loaded = true) {
  const original = {name: 'labNo', value: 'HL7:9', id: 'delegate_existing'};
  const state = {inputs: [original], badge: 1, disabled: true, alerts: []};
  const list = {
    length: 1,
    empty() { state.inputs = []; return this; },
    append(inputs) { state.inputs.push(...inputs); return this; },
  };
  const badge = {
    empty() { state.badge = 0; return this; },
    append(count) { state.badge = count; return this; },
  };
  const jquery = (selector, attributes) => {
    if (typeof selector === 'object') return {
      attr: key => selector[key], val: () => selector.value, hasClass: () => false,
    };
    if (selector === '<input />') return [attributes];
    if (selector === '#attachDocumentList') return list;
    if (selector === '#remoteTotalAttachments') return badge;
    if (selector === '.delegateAttachment') return {length: state.inputs.length};
    if (selector === '#attachDocumentsForm') return {
      find: () => ({map: callback => ({get: () => selections.map(item => callback.call(item))})}),
    };
    assert.fail('Unexpected picker selector');
  };
  const callbackStart = source.indexOf('beforeClose: function (event, ui) {');
  const callbackEnd = source.indexOf('\n        }\n    });', callbackStart);
  assert.ok(callbackStart >= 0 && callbackEnd > callbackStart);
  const callback = source.slice(callbackStart, callbackEnd + '\n        }'.length)
    .replace('beforeClose: function', 'function closeSelection');
  const sandbox = vm.createContext({jQuery: jquery, pickerLoaded: loaded,
    alert: text => state.alerts.push(text),
    document: {getElementById: () => ({classList: {remove: () => {state.disabled = false;}}})},
  });
  vm.runInContext(source.slice(start, end) + '\n' + callback, sandbox);
  state.result = sandbox.closeSelection();
  return state;
}
test('invalid later picker selection preserves existing delegates, badge and open dialog', () => {
  const state = closePicker([
    {name: 'labNo', value: '77', id: 'first', 'data-lab-type': 'MDS'},
    {name: 'labNo', value: '88', id: 'invalid'},
  ]);
  assert.equal(state.result, false);
  assert.deepEqual(state.inputs, [{name: 'labNo', value: 'HL7:9', id: 'delegate_existing'}]);
  assert.equal(state.badge, 1);
  assert.equal(state.disabled, true);
  assert.deepEqual(state.alerts, ['An attachment selection is invalid. Please reselect it.']);
});
test('valid picker selection replaces delegates together and enables the toolbar', () => {
  const state = closePicker([
    {name: 'labNo', value: '77', id: 'first', 'data-lab-type': 'MDS'},
    {name: 'labNo', value: '77', id: 'second', 'data-lab-type': 'HL7'},
  ]);
  assert.deepEqual(state.inputs.map(input => input.value), ['MDS:77', 'HL7:77']);
  assert.equal(state.badge, 2);
  assert.equal(state.disabled, false);
  assert.deepEqual(state.alerts, []);
});
test('an unloaded picker leaves existing delegates unchanged', () => {
  const state = closePicker([], false);
  assert.equal(state.inputs[0].value, 'HL7:9');
  assert.equal(state.badge, 1);
  assert.equal(state.disabled, true);
});
