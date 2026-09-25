/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'), 'utf8');
const start = jsp.indexOf('function popForm2(');
const end = jsp.indexOf('function callAdditionWebService(', start);
assert.ok(start >= 0 && end > start);
const source = jsp.slice(start, end).replace(/\$\{[^}]*\}/g, 'Edit Rx');

function launch(token, saveAndPrint = false) {
  const created = [];
  const errors = [];
  const submissions = [];
  const modal = { appendChild() {}, style: {} };
  function Modal() { this.show = () => {}; }
  Modal.getInstance = () => null;
  vm.runInNewContext(`${source}\npopForm2(789, ${saveAndPrint});`, {
    ctx: '/carlos',
    // Per-patient Rx state (#3875): the preview POST names the window's patient.
    RxPatientContext: require('../src/main/webapp/share/javascript/rx-patient-context.js').create('1001'),
    jQuery: () => ({ val: () => JSON.stringify({ id: '5&other=value' }) }),
    bootstrap: { Modal },
    updateDeleteOnCloseRxBox() {},
    oscarLog: error => errors.push(error.message),
    document: {
      getElementById: () => modal,
      querySelector: selector => selector.includes('CSRF-TOKEN') ? token : modal,
      createElement(tag) {
        const element = { tag, style: {}, children: [],
          appendChild(child) { this.children.push(child); },
          remove() { this.removed = true; },
          submit() { submissions.push(this); },
        };
        created.push(element);
        return element;
      },
    },
  });
  return { created, errors, submissions };
}

test('save/print preview uses a token-bearing POST into the modal iframe, never a mutating GET', () => {
  const { created, errors, submissions } = launch({ value: 'session-owned-token' });
  assert.deepEqual(errors, []);
  assert.equal(submissions.length, 1);
  const form = submissions[0];
  const iframe = created.find(element => element.tag === 'iframe');
  assert.equal(form.method, 'post');
  assert.equal(form.target, iframe.name);
  assert.equal(form.action, '/carlos/rx/viewScript?scriptId=789&pharmacyId=5%26other%3Dvalue&demographicNo=1001');
  assert.equal(form.children[0].name, 'CSRF-TOKEN');
  assert.equal(form.children[0].value, 'session-owned-token');
  assert.equal(form.removed, true);
  assert.equal(iframe.src, undefined);
});

test('save/print preview fails closed without the current session CSRF token', () => {
  for (const token of [null, { value: '' }]) {
    const { created, errors, submissions } = launch(token);
    assert.equal(submissions.length, 0);
    assert.equal(created.length, 0);
    assert.equal(errors.length, 1);
    assert.match(errors[0], /valid CSRF token/);
  }
});


test('only the successful save handoff requests signing its explicit saved script', () => {
  const { errors, submissions } = launch({ value: 'session-owned-token' }, true);
  assert.deepEqual(errors, []);
  const params = new URL(submissions[0].action, 'https://example.invalid').searchParams;
  assert.equal(params.get('scriptId'), '789');
  assert.equal(params.get('demographicNo'), '1001');
  assert.equal(params.get('saveAndPrint'), 'true');
  assert.equal(new URL(launch({ value: 'token' }).submissions[0].action,
    'https://example.invalid').searchParams.has('saveAndPrint'), false);
});

const savedPreviewSource = jsp.slice(jsp.indexOf('function openSavedPrescriptionPreview('), start);
for (const responseText of ['{"scriptId":"789"}', '{"scriptId":789}']) {
  test(`successful save binds the print handoff to its returned identity: ${responseText}`, () => {
    const previews = [];
    vm.runInNewContext(`${savedPreviewSource}\nopenSavedPrescriptionPreview({ responseText });`, {
      responseText, popForm2: (...args) => previews.push(args),
      alert: () => assert.fail('valid saved identity was refused'),
    });
    assert.equal(String(previews[0][0]), '789');
    assert.equal(previews[0][1], true);
  });
}
for (const responseText of ['<html>legacy result</html>', '{}', 'null', '{"scriptId":0}', '{"scriptId":"bad"}']) {
  test(`unidentified saved preview fails closed without retry: ${responseText}`, () => {
    const alerts = [];
    vm.runInNewContext(`${savedPreviewSource}\nopenSavedPrescriptionPreview({ responseText });`, {
      responseText, popForm2: () => assert.fail('missing saved identity must not select session state'),
      jsMsg: { previewUnavailable: 'review the saved prescription' }, alert: message => alerts.push(message),
    });
    assert.deepEqual(alerts, ['review the saved prescription']);
  });
}
