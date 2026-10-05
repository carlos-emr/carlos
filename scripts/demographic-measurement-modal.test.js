/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/form/demographicMeasurementModal.jsp'), 'utf8');
// nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- Extracts executable test code only from the fixed, checked-in JSP path above; no external data or HTML response is accepted.
const source = jsp.slice(jsp.indexOf('<script>') + '<script>'.length, jsp.lastIndexOf('</script>'))
  .replace(/<fmt:message key=["']([^"']+)["']\s*\/>/g, '$1')
  .replaceAll('<%=request.getContextPath()%>', '/carlos');

function setup(history = null, saveResult = {success:true}) {
  class Element {
    constructor(tag) { this.tag = tag; this.children = []; this.listeners = {}; this.value = ''; this.classList = { add() {}, remove() {} }; }
    appendChild(child) { this.children.push(child); return child; }
    removeChild(child) { const i = this.children.indexOf(child); assert.ok(i >= 0); this.children.splice(i, 1); }
    addEventListener(name, callback) { this.listeners[name] = callback; }
    removeEventListener(name) { delete this.listeners[name]; }
    setAttribute() {}
    focus() {}
    find(predicate) { return predicate(this) ? this : this.children.map(child => child.find(predicate)).find(Boolean); }
  }
  const body = new Element('body');
  const document = new Element('document');
  Object.assign(document, { body, createElement: tag => new Element(tag),
    createTextNode: text => Object.assign(new Element('text'), { textContent: text }),
    getElementById: id => body.find(element => element.id === id) || null });
  const target = body.appendChild(Object.assign(new Element('input'), { id: 'weight', value: '3.4' }));
  const requests = [];
  const jQuery = { each(data, callback) { for (const value of Object.values(data)) callback.call(value); }, ajax(options) {
    requests.push(options);
    if (options.url.includes('saveMeasurement') && saveResult === 'network-error') {
      if (options.error) options.error();
    } else {
      options.success(options.url.includes('saveMeasurement') ? saveResult : (history || {'-1':'No Results Found'}));
    }
  }};
  const context = vm.createContext({ document, jQuery, setTimeout() {}, requestAnimationFrame: callback => callback() });
  vm.runInContext(source, context);
  context.displayDemographicMeasurements('weight', 'WT', '17', '2026-01-01', '42');
  document.getElementById('currentMeasurementValue').value = '3.75';
  document.getElementById('currentMeasurementObservationDate').value = '2026-01-08';
  const click = className => body.find(element => element.className === className).listeners.click();
  return { context, document, target, requests, click, body };
}

function closed(fixture) {
  assert.equal(fixture.document.getElementById('currentMeasurementValue'), null, 'dialog inputs must be detached');
  assert.equal(fixture.document.listeners.keydown, undefined, 'dialog key handler must be removed');
}

test('Save posts exact measurement values and updates the form after dialog removal', () => {
  const f = setup();
  f.click('meas-btn-save');
  assert.equal(f.requests.length, 2);
  const saved = f.requests[1];
  assert.equal(saved.type, 'POST');
  assert.equal(saved.url, '/carlos/encounter/MeasurementData?action=saveMeasurement&demographicNo=17&appointmentNo=42&type=WT');
  assert.deepEqual(JSON.parse(JSON.stringify(saved.data)), {value:'3.75', instruction:'in kg', dateObserved:'2026-01-08'});
  assert.equal(f.target.value, '3.75');
  closed(f);
});

test('Okay imports the value without creating a measurement', () => {
  const f = setup();
  f.click('meas-btn-cancel');
  assert.equal(f.target.value, '3.75');
  assert.equal(f.requests.length, 1);
  closed(f);
});

test('Save imports a selected existing measurement without duplicating it', () => {
  const f = setup();
  f.context.setDemographicMeasurementModalValues('4.1', 'in kg', '2026-02-01');
  f.click('meas-btn-save');
  assert.equal(f.target.value, '4.1');
  assert.equal(f.requests.length, 1);
  closed(f);
});

test('editing an imported value enables a new measurement save', () => {
  const f = setup();
  f.context.setDemographicMeasurementModalValues('4.1', 'old instructions', '2026-02-01');
  f.document.getElementById('currentMeasurementValue').listeners.keydown();
  f.document.getElementById('currentMeasurementValue').value = '4.2';
  f.click('meas-btn-save');
  assert.equal(f.requests.length, 2);
  assert.equal(f.requests[1].data.value, '4.2');
  assert.equal(f.requests[1].data.instruction, 'in kg');
  closed(f);
});

for (const date of [Date.UTC(2026,0,8), {time:Date.UTC(2026,0,8)}, '2026-01-08T00:00:00Z', null]) {
  test(`measurement history accepts its observation-date representation: ${JSON.stringify(date)}`, () => {
    const f = setup({12:{dateObserved:date, dataField:'3.6', measuringInstruction:'in kg'}});
    const link = f.body.find(element => element.tag === 'a');
    assert.ok(link, 'Existing measurement is not displayed');
    assert.ok(!link.children[0].textContent.includes('NaN'), 'Unknown observation dates must not invent an age');
    link.listeners.click({preventDefault() {}});
    assert.equal(f.document.getElementById('currentMeasurementObservationDate').value, date === null ? '' : '2026-01-08');
    f.click('meas-btn-save');
    assert.equal(f.target.value, '3.6');
    assert.equal(f.requests.length, 1, 'Importing history must not save a duplicate');
    closed(f);
  });
}

for (const dismissal of ['Escape', 'overlay']) {
  test(`${dismissal} discards edits without saving or importing`, () => {
    const f = setup();
    if (dismissal === 'Escape') f.document.listeners.keydown({key:'Escape'});
    else {
      const overlay = f.body.find(element => element.className === 'meas-dialog-overlay');
      overlay.listeners.click({target:overlay});
    }
    assert.equal(f.target.value, '3.4');
    assert.equal(f.requests.length, 1);
    closed(f);
  });
}
for (const failure of [{success:false}, null, 'network-error']) {
  test(`failed Save preserves the form value: ${JSON.stringify(failure)}`, () => {
    const f = setup(null, failure);
    f.click('meas-btn-save');
    assert.equal(f.requests.length, 2);
    assert.equal(f.target.value, '3.4');
    closed(f);
  });
}
