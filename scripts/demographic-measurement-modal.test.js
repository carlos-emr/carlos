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

function setup() {
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
  const jQuery = { ajax(options) {
    requests.push(options);
    options.success(options.url.includes('saveMeasurement') ? {success:true} : {'-1':'No Results Found'});
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
