/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const directory = path.join(__dirname, '../src/main/webapp/web/dashboard/display');
const helper = fs.readFileSync(path.join(directory, 'dashboard-provider-context.js'), 'utf8');
const {providerUrl} = require(path.join(directory, 'dashboard-provider-context.js'));

function windowFor(provider) {
  const submissions = [], ajax = [];
  const field = {value: provider};
  const document = {
    getElementById: id => id === 'dashboardProviderNo' ? field : null,
    querySelector: () => ({value: 'FAKE csrf + &= token'}),
    createElement: tag => tag === 'form' ? {
      fields: [], appendChild(input) {this.fields.push(input);},
      submit() {submissions.push({url: this.action, method: this.method, fields: this.fields});},
    } : {},
    body: {appendChild() {}},
  };
  const $ = () => ({ready() {}});
  $.ajax = options => ajax.push(options);
  $.fn = {dataTableExt: {afnFiltering: []}, dataTable: {moment() {}, ext: {order: {}}}};
  const window = {document, location: {href: 'https://clinic.invalid/carlos/web/dashboard/display/DashboardDisplay'}};
  const context = vm.createContext({window, document, URL, $, ctx: '/carlos', console});
  vm.runInContext(helper, context);
  context.dashboardProviderUrl = window.dashboardProviderUrl;
  return {context, window, submissions, ajax, field};
}

for (const controller of ['dashboardDisplayController.js', 'drilldownDisplayController.js']) {
  test(`${controller} keeps each window's provider during native POST navigation and preserves CSRF fields`, () => {
    const a = windowFor('FAKE-provider-A'), b = windowFor('FAKE-provider-B');
    const source = fs.readFileSync(path.join(directory, controller), 'utf8');
    vm.runInContext(source, a.context); vm.runInContext(source, b.context);
    b.context.sendData('/web/dashboard/display/DrilldownDisplay', {indicatorTemplateId: 42}, 'reload');
    a.context.sendData('/web/dashboard/display/DrilldownDisplay', {indicatorTemplateId: 42}, 'reload');
    for (const [window, provider] of [[a, 'FAKE-provider-A'], [b, 'FAKE-provider-B']]) {
      const submission = window.submissions[0];
      assert.equal(new URL(submission.url).searchParams.get('dashboardProviderNo'), provider);
      assert.equal(submission.method, 'POST');
      assert.equal(submission.fields.find(f => f.name === 'CSRF-TOKEN').value, 'FAKE csrf + &= token');
      assert.equal(submission.fields.find(f => f.name === 'indicatorTemplateId').value, 42);
    }
  });
}

test('indicator AJAX and tickler modal requests carry the current window provider without changing their bodies', () => {
  for (const [controller, target] of [['dashboardDisplayController.js', 'indicatorId'], ['drilldownDisplayController.js', 'modal']]) {
    const window = windowFor('FAKE-provider-A');
    vm.runInContext(fs.readFileSync(path.join(directory, controller), 'utf8'), window.context);
    const data = {indicatorId: 42, 'CSRF-TOKEN': 'FAKE token'};
    window.context.sendData('/web/dashboard/display/DisplayIndicator', data, target);
    assert.equal(new URL(window.ajax[0].url).searchParams.get('dashboardProviderNo'), 'FAKE-provider-A');
    assert.equal(window.ajax[0].data, data);
  }
});

test('provider values cannot alter the route, host, operation or fragment', () => {
  const result = new URL(providerUrl('/carlos/export?method=get&dashboardProviderNo=old#section', 'FAKE +&?=/#', 'https://clinic.invalid'));
  assert.equal(result.origin, 'https://clinic.invalid');assert.equal(result.pathname, '/carlos/export');
  assert.equal(result.searchParams.get('method'), 'get');assert.equal(result.hash, '#section');
  assert.deepEqual(result.searchParams.getAll('dashboardProviderNo'), ['FAKE +&?=/#']);
});

test('a missing window selection leaves the original request URL intact', () => {
  assert.equal(providerUrl('/carlos/export?method=get', '', 'https://clinic.invalid'), '/carlos/export?method=get');
  const window = windowFor('');assert.equal(window.window.dashboardProviderUrl('/carlos/export'), '/carlos/export');
});
