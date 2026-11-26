/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function checklist() {
  const listeners = {}, elements = {}, requests = [], navigation = [], cells = [];
  function element(id) {
    return elements[id] ||= {
      dataset: {}, isConnected: true, matches: () => false,
      addEventListener(type, handler) { listeners[id + type] = handler; },
      requestSubmit(submitter) { navigation.push(submitter); },
    };
  }
  element('checked_items').dataset = {empty: 'None', printUrl: 'print-labels'};
  element('referralSearch').action = '/admin/ManageBillingReferral';
  const box = {dataset: {referralId: '7'}, matches: () => true};
  const document = {
    addEventListener(type, handler) { listeners[type] = handler; },
    getElementById: element,
    querySelectorAll() { return [box]; },
    querySelector() { return {
      replaceChildren() { cells.length = 0; },
      insertRow() { return {insertCell() { const cell = {}; cells.push(cell); return cell; }}; },
    }; },
  };
  const $ = {ajax(options) {
    const request = {options, fail(fn) { this.reject = fn; return this; }, done(fn) { this.resolve = fn; return this; }};
    requests.push(request);
    return request;
  }};
  const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/referral-label-checklist.js'), 'utf8');
  vm.runInNewContext(source, {document, $, window: {location: {assign(url) { navigation.push(url); }}}});
  function change(checked) { box.checked = checked; listeners.change({target: box}); }
  function submit(form, submitter) {
    let stopped = false;
    listeners.submit({target: form, submitter, preventDefault() {}, stopImmediatePropagation() { stopped = true; }});
    return stopped;
  }
  return {element, requests, navigation, cells, box, change, submit, click: id => listeners[id + 'click']()};
}

test('check, clear and check are acknowledged in click order before batch printing', () => {
  const f = checklist();
  const rows = [{id: 7, formattedName: 'A < B & C'}];
  f.change(true);
  f.click('clearReferralLabels');
  f.change(true);
  f.click('printReferralLabels');
  assert.equal(f.requests.length, 1);
  assert.equal(f.requests[0].options.timeout, 15000);
  f.requests[0].resolve(rows);
  assert.equal(f.requests[1].options.data.clear, true);
  f.requests[1].resolve([]);
  assert.equal(f.requests[2].options.data.checked, true);
  assert.equal(f.navigation.length, 0);
  f.requests[2].resolve(rows);
  assert.deepEqual(f.navigation, ['print-labels']);
  assert.equal(f.cells[0].textContent, rows[0].formattedName);
  assert.equal(f.box.checked, true);
});

for (const failure of ['timeout', 'unconfirmed selection', 'invalid response']) {
  test(failure + ' cancels held search and paging until the user confirms a retry', () => {
    const f = checklist();
    const pager = {matches: () => true, isConnected: true, requestSubmit: s => f.navigation.push(s)};
    f.change(true);
    assert.equal(f.submit(f.element('referralSearch'), 'Search'), true);
    assert.equal(f.submit(pager, 'Next'), true);
    const first = f.requests[0];
    if (failure === 'timeout') first.reject();
    else first.resolve(failure === 'invalid response' ? {} : []);
    assert.equal(f.element('labelUpdateError').hidden, false);
    f.click('printReferralLabels');
    assert.equal(f.submit(f.element('referralSearch'), 'Search'), true);
    f.click('retryLabelUpdate');
    f.requests[1].resolve([{id: 7, formattedName: 'Fake specialist'}]);
    assert.equal(f.element('labelUpdateError').hidden, true);
    assert.deepEqual(f.navigation, []);
    f.change(false);
    assert.equal(f.submit(pager, 'Next'), true);
    f.requests[2].resolve([]);
    assert.deepEqual(f.navigation, ['Next']);
    assert.equal(f.box.checked, false);
    assert.equal(f.cells[0].textContent, 'None');
  });
}
