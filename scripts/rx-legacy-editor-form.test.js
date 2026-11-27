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
    for (const qty of ['30', '7.50', '0.5', '.5']) {
      const actionField = {value: ''};
      let submitted;
      const frm = {
        action: '/carlos/rx/writeScript', // Built-in form.action is not the action control.
        elements: {action: actionField}, quantity: {value: qty}, repeat: {value: ''},
        submit() { submitted = {action: actionField.value, repeat: this.repeat.value}; },
      };
      const context = {frm, oscarLog() {}};
      vm.runInNewContext(functionSource('submitForm', 'function changeDuration('), context);
      context.submitForm(action);
      assert.deepEqual(submitted, {action, repeat: 0}, `expected quantity ${qty} to submit`);
    }
    for (const invalidQty of ['', '   ', 'abc', '.', '1.2.3', '-5']) {
      const actionField = {value: ''};
      let submitted = false;
      const frm = {
        action: '/carlos/rx/writeScript',
        elements: {action: actionField}, quantity: {value: invalidQty}, repeat: {value: '0'},
        submit() { submitted = true; },
      };
      const context = {frm, oscarLog() {}};
      vm.runInNewContext(functionSource('submitForm', 'function changeDuration('), context);
      context.submitForm(action);
      assert.equal(submitted, false, `expected invalid quantity ${JSON.stringify(invalidQty)} to be refused`);
    }
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
// A javascript: href is percent-decoded before it runs, which undoes any encoding inside it, so
// the link carries the drug-info URL and its onclick opens the popup.
test('drug-info link encodes stored generic names and DINs for its URL and its JavaScript attribute', () => {
  const link = jsp.split('\n').find(line => line.includes('<a href=') && line.includes('/rx/drugInfo?GN='));
  assert.ok(link, 'drug-info link');
  assert.ok(link.includes('<a href="${pageContext.request.contextPath}/rx/drugInfo?GN=${carlos:forUriComponent(rx2.genericName)}'));
  assert.ok(link.includes('&amp;DIN=${carlos:forUriComponent(rx2.regionalIdentifier)}'));
  assert.ok(link.includes(`onclick="ShowDrugInfo('\${carlos:forJavaScriptAttribute(rx2.genericName)}', '\${carlos:forJavaScriptAttribute(rx2.regionalIdentifier)}'); return false;"`));
  assert.ok(!jsp.includes('href="javascript:ShowDrugInfo('));
  assert.ok(!jsp.includes(`ShowDrugInfo('\${rx2.genericName}'`));
});

test('every persisted editor input is initialized from its model with an encoded value', () => {
  for (const field of ['GCN_SEQNO', 'atcCode', 'regionalIdentifier', 'dosage', 'genericName',
    'brandName', 'rxDate', 'takeMin', 'takeMax', 'duration', 'quantity', 'unitName', 'repeat',
    'lastRefillDate', 'outsideProviderName', 'outsideProviderOhip', 'writtenDate']) {
    const start = jsp.indexOf(`name="${field}"`);
    const value = jsp.indexOf('value="<carlos:encode', start);
    assert.ok(start > 0 && value > start, `missing encoded value for ${field}`);
    const binding = jsp.slice(start, jsp.indexOf('context="htmlAttribute"', value));
    assert.match(binding, new RegExp('thisForm.get' + field[0].toUpperCase() + field.slice(1) + '\\(\\)'));
    assert.ok(!binding.includes('<input', 1), `value belongs to another field: ${field}`);
  }
  for (const field of ['customName', 'special']) {
    assert.match(jsp, new RegExp('name="' + field + '"[^>]*><carlos:encode value=\'<%= thisForm.get'
      + field[0].toUpperCase() + field.slice(1) + '\\(\\) %>\' context="html"/>'));
  }
  for (const field of ['prn', 'nosubs', 'customInstr']) {
    assert.ok(jsp.includes(`name="${field}" value="true" <%= thisForm.get${field[0].toUpperCase() + field.slice(1)}() ? "checked" : "" %>`));
  }
  assert.match(jsp, /thisForm.setShortTerm\(rx.getShortTerm\(\)\)/);
  assert.match(jsp, /name="shortTerm" value="<%= thisForm.getShortTerm\(\) %>"/);
  assert.match(jsp, /name="dispenseInternal" value="<%= thisForm.getDispenseInternal\(\) %>"/);
  assert.ok(!jsp.includes('customQty(<%=quan%>)'), 'quantity was emitted as executable JavaScript');
});

test('initialization retains known and historical select values without adding HTML', () => {
  const context = {Option: function(text, value) { return {text, value}; }};
  vm.runInNewContext(functionSource('restoreEditorSelect', 'function pageLoad('), context);
  for (const expected of ['BID', '', `historic <dose> '&`]) {
    const options = [{value: 'BID'}];
    let selected = 'BID';
    const select = {add(option) { options.push(option); },
      get value() { return selected; },
      set value(value) { selected = options.some(option => option.value === value) ? value : ''; }};
    context.restoreEditorSelect(select, expected);
    assert.equal(select.value, expected);
    assert.equal(options.length, expected === 'BID' || expected === '' ? 1 : 2);
    if (options.length === 2) assert.equal(options[1].text, expected);
  }
});

test('page load never recalculates or rewrites clinical fields and preserves an OHIP-only provider', () => {
  const frm = {quantity: {value: '018'}, special: {value: 'Keep exact instructions <&>'},
    outsideProviderName: {value: ''}, outsideProviderOhip: {value: '123456'}};
  const controls = {ocheck: {checked: false}, otext: {style: {}}};
  const context = {document: {forms: {frm}, getElementById: id => controls[id]}, frm: null,
    refreshSuggestedQuantity() {},
    calcQty() { throw new Error('render recalculated quantity'); },
    writeScriptDisplay() { throw new Error('render rewrote instructions'); }};
  vm.runInNewContext(functionSource('pageLoad', 'function showHideOutsideProvider('), context);
  context.pageLoad();
  assert.equal(frm.quantity.value, '018');
  assert.equal(frm.special.value, 'Keep exact instructions <&>');
  assert.equal(frm.outsideProviderOhip.value, '123456');
  assert.equal(controls.ocheck.checked, true);
  assert.equal(controls.otext.style.display, '');
  const initialization = jsp.slice(jsp.indexOf('// Preserve stored values'), jsp.indexOf('function getRenalDosingInformation('));
  assert.ok(!initialization.includes('writeScriptDisplay()'));
  assert.ok(!initialization.includes('calcQty()'));
});

test('compliance controls submit true, false and unknown distinctly', () => {
  const field = {value: ''};
  const frm = {patientComplianceY: {checked: true}, patientComplianceN: {checked: true},
    elements: {patientCompliance: field}};
  const context = {frm, writeScriptDisplay() {}};
  vm.runInNewContext(functionSource('checkPatientCompliance', 'function writeScriptDisplay('), context);
  context.checkPatientCompliance('Y');
  assert.equal(field.value, 'true');
  assert.equal(frm.patientComplianceN.checked, false);
  frm.patientComplianceN.checked = true;
  context.checkPatientCompliance('N');
  assert.equal(field.value, 'false');
  assert.equal(frm.patientComplianceY.checked, false);
  frm.patientComplianceN.checked = false;
  context.checkPatientCompliance('N');
  assert.equal(field.value, '');
});

test('initial suggestions are calculated without changing stored quantities or instructions', () => {
  const frm = {frequencyCode: {selectedIndex: 0}, durationUnit: {value: 'W'}, duration: {value: '2'},
    takeMin: {value: '1'}, takeMax: {value: '2'}, sugQtyMin: {value: ''}, sugQtyMax: {value: ''},
    quantity: {value: '018'}, special: {value: 'Keep the written directions'}};
  let labels = 0;
  const context = {frm, freqMin: [1], freqMax: [2], calculateDuration: () => 14,
    setQuantity() { labels += 1; }};
  vm.runInNewContext(functionSource('ceilDispenseQuantity', 'function calcQuantity('), context);
  vm.runInNewContext(functionSource('refreshSuggestedQuantity', 'function pageLoad('), context);
  context.refreshSuggestedQuantity();
  assert.equal(frm.sugQtyMin.value, 14);
  assert.equal(frm.sugQtyMax.value, 56);
  assert.equal(frm.quantity.value, '018');
  assert.equal(frm.special.value, 'Keep the written directions');
  assert.equal(labels, 1);
  frm.frequencyCode.selectedIndex = 9;
  context.refreshSuggestedQuantity();
  assert.equal(labels, 1, 'unknown historical frequency must not calculate a dose');
  assert.equal(frm.quantity.value, '018');
});

test('legacy dispense calculation rounds an exact decimal course once', () => {
  const context = {};
  vm.runInNewContext(functionSource('ceilDispenseQuantity', 'function calcQuantity('), context);
  assert.equal(context.ceilDispenseQuantity('0.2', 3, 10, 'TID'), 6);
  assert.equal(context.ceilDispenseQuantity('0.20000000001', 3, 10, 'TID'), 7);
  assert.equal(context.ceilDispenseQuantity('.5', 1 / 7, 28, 'Q1Week'), 2);
  assert.equal(context.ceilDispenseQuantity('1/2', 3, 10, 'TID'), undefined);
});

test('legacy redraw keeps the package description and reads repeats from its own line or single-line trailer', () => {
  const frm = {quantity: {value: '1'}, unitName: {value: 'bottle (120 mL)'}, txtRepeat: {value: '2'},
    dataset: {qtyLabel: 'Qté/Mitte', repeatsLabel: 'Répétitions'}};
  const context = {frm, getNoSubs: () => ''};
  vm.runInNewContext(functionSource('findInlineRepeatClause', 'function removePRNifNeeded('), context);
  const multiLine = ' Days\nQté/Mitte:1 bottle (120 mL)\nRépétitions:0\nSupplementary text';
  assert.equal(context.replaceQuantityAndRepeats(multiLine),
    ' Days\nQté/Mitte:1 bottle (120 mL)\nRépétitions:2\nSupplementary text');

  frm.quantity.value = '2';
  frm.txtRepeat.value = '3';
  const singleLine = ' Days\nQty:1 bottle (120 mL) Repeats:0';
  assert.equal(context.replaceQuantityAndRepeats(singleLine),
    ' Days\nQty:2 bottle (120 mL) Repeats:3');

  const localizedSingleLine = ' Days\nQté/Mitte:1 bottle (120 mL) Répétitions:0\nSupplementary text';
  assert.equal(context.replaceQuantityAndRepeats(localizedSingleLine),
    ' Days\nQté/Mitte:2 bottle (120 mL) Répétitions:3\nSupplementary text');
});

test('submitForm preserves unchanged stored non-decimal quantities and alerts on invalid edits', () => {
  const actionField = {value: ''};
  let submitted = null;
  const alerts = [];
  const frm = {
    action: '/carlos/rx/writeScript',
    elements: {action: actionField},
    quantity: {value: '1/2 bottle', defaultValue: '1/2 bottle'},
    repeat: {value: '1'},
    submit() { submitted = actionField.value; },
  };
  const context = {frm, oscarLog() {}, alert(msg) { alerts.push(msg); }};
  vm.runInNewContext(functionSource('submitForm', 'function changeDuration('), context);

  context.submitForm('update');
  assert.equal(submitted, 'update');
  assert.deepEqual(alerts, []);

  submitted = null;
  frm.quantity.value = 'invalid edit';
  context.submitForm('update');
  assert.equal(submitted, null);
  assert.equal(alerts.length, 1);

  submitted = null;
  frm.quantity = {value: '   ', defaultValue: '   '};
  context.submitForm('update');
  assert.equal(submitted, null);
  assert.equal(alerts.length, 2);
});
