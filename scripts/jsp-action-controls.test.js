/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { decodeHTML } = require('entities');

const jsp = file => fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp', file), 'utf8');
const fixture = { Dcode: '250.1', DcodeDesc: 'Diabetes', Dcode2: '401', DcodeDesc2: 'Hypertension',
  ctx: '/carlos', popupPatientSex: 'F', popupPatientAge: '55', curYear: '2026', curMonth: '10' };

// Render just the controls under test with synthetic values. Their event handlers
// remain the repository code, so the tests check the destination and callback contracts.
function render(control) {
  const result = control
    .replace(/<carlos:encode value='<%= curimage %>' context="javaScriptAttribute"\/>/g, 'fixture.png')
    .replace(/<fmt:message key="([^"]+)"\/>/g, '$1')
    .replace(/<%=([\s\S]*?)%>/g, (_, expression) => {
      if (expression.includes('request.getContextPath()')) return '/carlos';
      const name = expression.trim().replace(/^io\.github\.carlos_emr\.carlos\.utility\.SafeEncode\.for\w+\((\w+)\)$/, '$1');
      assert.ok(Object.hasOwn(fixture, name), `Unexpected JSP value: ${expression}`);
      return fixture[name];
    })
    .replace(/\$\{carlos:for\w+\((\w+)\)\}/g, (_, name) => {
      assert.ok(Object.hasOwn(fixture, name), `Unexpected EL value: ${name}`);
      return fixture[name];
    });
  assert.doesNotMatch(result, /<%|\$\{|<carlos:/);
  return result;
}

function attribute(control, name) {
  const match = [...control.matchAll(/\b([\w-]+)=(['"])([\s\S]*?)\2/g)]
    .find(candidate => candidate[1].toLowerCase() === name.toLowerCase());
  assert.ok(match, `Missing ${name}: ${control}`);
  return decodeHTML(match[3]);
}

function click(control, context) {
  return vm.runInNewContext('(function () {' + attribute(control, 'onclick') + '})()', context);
}

test('diagnostic result buttons transfer each selected code without submitting the update form', () => {
  const source = jsp('billing/CA/BC/billingDigSearch.jsp');
  const controls = source.match(/<button\b[\s\S]*?<\/button>/g) || [];
  assert.equal(controls.length, 3);
  const transfer = source.match(/function CodeAttach\(File2\)\s*\{[\s\S]*?\}/)[0];
  for (const [index, raw] of controls.entries()) {
    const control = render(raw);
    assert.equal(attribute(control, 'type'), 'button');
    const detail = {value: ''};
    let closed = 0;
    const context = vm.createContext({self: {close() { closed++; }, opener: {document: {serviceform: {xml_diagnostic_detail: detail}}}}});
    vm.runInContext(transfer, context);
    assert.equal(click(control, context), false);
    assert.equal(detail.value, index === 2 ? '401|Hypertension' : '250.1|Diabetes');
    assert.equal(closed, 1);
  }
});

test('tickler date buttons retain separate begin and end popups without submitting the report', () => {
  const controls = jsp('tickler/ticklerDemoMain.jsp').match(/<button type="button" class="tickler-date-picker"[\s\S]*?<\/button>/g) || [];
  assert.equal(controls.length, 2);
  for (const [index, raw] of controls.entries()) {
    const control = render(raw);
    assert.equal(attribute(control, 'type'), 'button');
    const calls = [];
    click(control, {openBrWindow: (...args) => calls.push(args)});
    assert.equal(calls.length, 1);
    const url = new URL(calls[0][0], 'https://example.test');
    assert.equal(url.pathname, '/carlos/billing/CA/ON/ViewBillingCalendarPopup');
    assert.equal(url.searchParams.get('type'), index === 0 ? 'admission' : 'end');
    assert.equal(url.searchParams.get('year'), '2026');
    assert.equal(url.searchParams.get('month'), '10');
    assert.deepEqual(calls[0].slice(1), ['', 'width=300,height=300']);
  }
});

test('image delete remains a single encoded action and bypasses anchor-only AJAX navigation', () => {
  const source = jsp('eform/efmimagemanager.jsp');
  const raw = source.match(/<button type="button" onclick="deleteImg\([\s\S]*?<\/button>/)?.[0];
  assert.ok(raw);
  assert.match(raw, /context="javaScriptAttribute"/);
  const control = render(raw);
  assert.equal(attribute(control, 'type'), 'button');
  assert.match(attribute(control, 'class'), /\bcontentLink\b/);
  const calls = [];
  click(control, {deleteImg: value => calls.push(value)});
  assert.deepEqual(calls, ['fixture.png']);
});

test('the OMD popup action does not expose patient attributes in a navigation link', () => {
  const source = jsp('casemgmt/newEncounterHeader.jsp');
  const raw = (source.match(/<button\b[\s\S]*?<\/button>/g) || [])
    .find(control => control.includes('key="encounter.Header.OntMD"'));
  assert.ok(raw, 'OMD must remain a native button');
  const control = render(raw);
  assert.equal(attribute(control, 'type'), 'button');
  assert.doesNotMatch(control, /\b(?:href|formaction)\s*=/i);
  for (const anchor of source.match(/<a\b[\s\S]*?<\/a>/g) || []) {
    assert.doesNotMatch(anchor, /omdDiseaseList|popupPatientSex|popupPatientAge/,
      'Patient attributes must not be copied into a navigation link');
  }
  const calls = [];
  assert.equal(click(control, {popupPage: (...args) => calls.push(args)}), false);
  assert.deepEqual(calls, [[600, 175, 'Calculators', '/carlos/common/omdDiseaseList?sex=F&age=55']]);
});

test('messenger navigation has a real fallback route matching its popup', () => {
  const raw = (jsp('admin/admin.jsp').match(/<a\b[\s\S]*?<\/a>/g) || [])
    .find(control => control.includes('key="admin.admin.btnMessengerAdmin"'));
  assert.ok(raw);
  const control = render(raw);
  const calls = [];
  assert.equal(click(control, {popupOscarRx: (...args) => calls.push(args)}), false);
  assert.equal(calls.length, 1);
  const fallback = attribute(control, 'href');
  assert.equal(fallback, calls[0][2]);
  assert.equal(new URL(fallback, 'https://example.test').pathname, '/carlos/messenger');
});
