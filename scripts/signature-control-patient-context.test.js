/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const controlPath = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/library/eforms/signatureControl.jsp');

function initialize(patient, options) {
  const alerts = [];
  const frames = [];
  function node(value = '') {
    return {
      length: 1, value, children: [], content: '', attributes: {},
      val(next) { if (arguments.length) { this.value = next; return this; } return this.value; },
      empty() { this.children = []; this.content = ''; return this; },
      text(next) { this.content = next; return this; },
      html(next) { this.content = next; return this; },
      append(...children) { this.children.push(...children); return this; },
      attr(attributes) { Object.assign(this.attributes, attributes); return this; },
      css() { return this; },
    };
  }
  const elements = new Map([
    ['#demographicNo', node(patient)], ['#signatureInput', node()],
    ['#signatureDisplay', node()], ['#signatureValue', node()],
  ]);
  const jQuery = selector => {
    if (selector === '<iframe title="Signature pad">') {
      const frame = node();
      frames.push(frame);
      return frame;
    }
    if (selector === '<br>') return node();
    assert.ok(elements.has(selector), `Unexpected selector ${selector}`);
    return elements.get(selector);
  };
  const source = fs.readFileSync(controlPath, 'utf8')
    .replace(/<%=\s*request\.getContextPath\(\)\s*%>/g, '/carlos')
    .replace(/<%--[\s\S]*?--%>/g, '')
    .replace(/<%[\s\S]*?%>/g, '');
  const context = vm.createContext({jQuery, alert: message => alerts.push(message), URL,
    document: {baseURI: 'https://clinic.example/carlos/eform/efmshowform'}, window: {}, options});
  vm.runInContext(source, context);
  vm.runInContext('signatureControl.initialize(options)', context);
  return {alerts, frames, input: elements.get('#signatureInput')};
}

for (const patient of [-1, '-1']) {
  test(`patientless eForm ${typeof patient} context shows availability without opening a pad`, () => {
    const page = initialize(patient);
    assert.deepEqual(page.alerts, []);
    assert.equal(page.frames.length, 0);
    assert.equal(page.input.content, 'Signing is available once this form is saved for a patient.');
    assert.deepEqual(page.input.children, []);
  });
}

for (const patient of [undefined, '', '0', '-2', 'invalid', '2147483648']) {
  test(`invalid eForm patient ${String(patient)} retains the refusal alert`, () => {
    const page = initialize(patient);
    assert.equal(page.alerts.length, 1);
    assert.match(page.alerts[0], /could not determine this form's patient/);
    assert.equal(page.frames.length, 0);
    assert.equal(page.input.content, '');
  });
}

test('a valid eForm patient opens the pad with its own patient and module', () => {
  const page = initialize('421');
  assert.deepEqual(page.alerts, []);
  assert.equal(page.frames.length, 1);
  assert.ok(page.input.children.includes(page.frames[0]));
  const url = new URL(page.frames[0].attributes.src);
  assert.equal(url.origin, 'https://clinic.example');
  assert.equal(url.pathname, '/carlos/signature_pad/tabletSignature');
  assert.equal(url.searchParams.get('demographicNo'), '421');
  assert.equal(url.searchParams.get('ModuleType'), 'E_FORM');
});

test('a patientless custom pad retains the invalid-context alert', () => {
  const page = initialize('421', {sigHTML: '/carlos/signature_pad/tabletSignature?ModuleType=RTL',
    demographicNo: '-1', signatureInput: '#signatureInput', signatureDisplay: '#signatureDisplay'});
  assert.equal(page.alerts.length, 1);
  assert.match(page.alerts[0], /could not determine this form's patient/);
  assert.equal(page.frames.length, 0);
  assert.equal(page.input.content, '');
});
