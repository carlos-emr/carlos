/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/WriteScript.jsp'), 'utf8');

function functionSource(name, next) {
  const start = jsp.indexOf(`function ${name}(`);
  const end = jsp.indexOf(next, start);
  assert.ok(start >= 0 && end > start);
  return jsp.slice(start, end);
}

// Browsers discard a nested <form> start tag, assigning its action/patient controls to the
// editor instead. RadioNodeList.value cannot select hidden action controls, so Print then
// refreshes with HTTP200 without saving anything. Check the actual JSP form boundaries.
test('legacy editor and stash forms are balanced siblings with distinct action/patient fields', () => {
  const tags = [...jsp.matchAll(/<\/?form\b[^>]*>/gi)];
  let current = null;
  const forms = new Map();
  for (const tag of tags) {
    if (/^<\/form/i.test(tag[0])) {
      assert.ok(current, 'orphan form closing tag');
      forms.set(current.name, jsp.slice(current.start, tag.index));
      current = null;
    } else {
      assert.equal(current, null, 'nested form: browser will merge unrelated action fields');
      const name = tag[0].match(/\bid="([^"]+)"/);
      assert.ok(name, 'form has no stable id');
      current = {name: name[1], start: tag.index};
    }
  }
  assert.equal(current, null, 'unclosed editor form');
  assert.deepEqual([...forms.keys()], ['addFavoriteWriteScriptForm', 'RxStashForm', 'frm']);
  for (const name of ['RxStashForm', 'frm']) {
    assert.equal((forms.get(name).match(/name="action"/g) || []).length, 1);
    assert.equal((forms.get(name).match(/name="demographicNo"/g) || []).length, 1);
  }
  assert.match(forms.get('frm'), /name="randomId"/);
  assert.match(forms.get('frm'), /name="draftRevision"/);
  assert.match(forms.get('frm'), /name="draftRevision_<%= draftCard.getRandomId\(\) %>"/);
  assert.match(forms.get('frm'), /name="quantity"/);
  assert.match(forms.get('frm'), /name="special"/);
  assert.match(forms.get('RxStashForm'), /name="RxStashForm"/);
  assert.match(forms.get('RxStashForm'), /name="randomId"/);
  assert.match(forms.get('RxStashForm'), /name="draftRevision"/);
  assert.doesNotMatch(forms.get('RxStashForm'), /name="stashId"/);
  const links = jsp.match(/href="javascript:submitPending[^"]*"/g);
  assert.equal(links.length, 3);
  links.forEach(link => {
    assert.match(link, /\$\{rx.randomId\}/);
    assert.match(link, /\$\{carlos:forJavaScript\(rx.draftRevision\)\}/);
    assert.doesNotMatch(link, /loopStatus.index/);
  });
});

for (const action of ['update', 'updateAddAnother', 'updateAndPrint']) {
  test(`legacy ${action} sets its own action control before native form submission`, () => {
    const actionField = {value: ''};
    let submitted;
    const frm = {
      action: '/carlos/rx/writeScript', // Built-in form.action is not the action control.
      elements: {action: actionField}, quantity: {value: '30'}, repeat: {value: ''},
      submit() { submitted = {action: actionField.value, repeat: this.repeat.value}; },
    };
    const context = {frm, oscarLog() {}};
    vm.runInNewContext(functionSource('submitForm', 'function changeDuration('), context);
    context.submitForm(action);
    assert.deepEqual(submitted, {action, repeat: 0});
  });
}

test('stash edit/delete uses its separate named form and preserves the editor action', () => {
  const editorAction = {value: 'updateAndPrint'};
  const submitted = [];
  const form = {elements: {action: {value: ''}, randomId: {value: ''}, draftRevision: {value: ''}},
    submit() { submitted.push({action: this.elements.action.value, randomId: this.elements.randomId.value, revision: this.elements.draftRevision.value}); }};
  const context = {document: {forms: {RxStashForm: form, frm: {elements: {action: editorAction}}}}, oscarLog() {}};
  vm.runInNewContext(functionSource('submitPending', '</script>'), context);
  context.submitPending('2345', 'draft-b', 'edit');
  context.submitPending('7890', 'draft-c', 'delete');
  assert.deepEqual(submitted, [{action: 'edit', randomId: '2345', revision: 'draft-b'}, {action: 'delete', randomId: '7890', revision: 'draft-c'}]);
  assert.equal(editorAction.value, 'updateAndPrint');
});

// The encoder's quote/attribute behavior is covered by CarlosEncodeTagUnitTest; guard the
// actual legacy link against reverting to raw EL or using a JavaScript-only context here.
test('drug-info link encodes stored generic names for the surrounding JavaScript attribute', () => {
  assert.ok(jsp.includes(`href="javascript:ShowDrugInfo('<carlos:encode value='\${rx2.genericName}' context="javaScriptAttribute"/>');"`));
  assert.ok(!jsp.includes(`href="javascript:ShowDrugInfo('\${rx2.genericName}');"`));
});
