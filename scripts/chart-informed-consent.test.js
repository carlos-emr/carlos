/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/newCaseManagementView.js.jsp'), 'utf8');
const start = source.indexOf('    function doInformedConsent(');
const end = source.indexOf('    window.addEventListener("message"', start);
assert.ok(start > 0 && end > start, 'The deployed consent handler must be exercised');

function chart({checked = true, attribute, confirm = true} = {}) {
  const state = {checked, attribute, confirmations: 0, posts: [], removed: false, disabled: false, alerts: []};
  const checkbox = {
    is(selector) { assert.equal(selector, ':checked'); return state.checked; },
    prop(name, value) {
      assert.ok(['checked', 'disabled'].includes(name));
      if (arguments.length === 1) return state[name];
      state[name] = value; return checkbox;
    },
    // Attribute and live state intentionally differ after a native checkbox click.
    attr(name, value) {
      assert.equal(name, 'checked');
      if (arguments.length === 1) return state.attribute;
      state.attribute = value ? 'checked' : undefined;
    },
  };
  function jQuery(selector) {
    if (selector === '#informedConsentCheck') return checkbox;
    assert.equal(selector, '#informedConsentDiv');
    return {remove() { state.removed = true; }};
  }
  jQuery.post = (url, data, callback, format) => {
    assert.equal(url, '/carlos/DemographicExtService');
    assert.equal(format, 'json');
    const post = {data, callback};
    state.posts.push(post);
    return {fail(callback) { post.failure = callback; return this; }};
  };
  const context = {ctx: '/carlos', jQuery, window: {alert(message) { state.alerts.push(message); }, confirm() {
    state.confirmations++;
    return typeof confirm === 'function' ? confirm() : confirm;
  }}};
  vm.createContext(context);
  vm.runInContext(source.slice(start, end), context);
  return {state, record: () => context.doInformedConsent('123')};
}

test('a checked box without a checked attribute confirms and posts the originating patient once', () => {
  const c = chart(); c.record();
  assert.equal(c.state.confirmations, 1);
  assert.equal(c.state.posts.length, 1);
  assert.deepEqual(JSON.parse(JSON.stringify(c.state.posts[0].data)), {
    method: 'saveNewValue', demographicNo: '123', key: 'informedConsent', value: 'yes',
  });
  assert.equal(c.state.removed, false);
  c.state.posts[0].callback({value: '1'}, 'success');
  assert.equal(c.state.removed, true);
});

test('an unchecked live box does not post even when its original attribute was checked', () => {
  const c = chart({checked: false, attribute: 'checked'}); c.record();
  assert.equal(c.state.confirmations, 0);
  assert.equal(c.state.posts.length, 0);
  assert.equal(c.state.removed, false);
});

test('cancelling resets the live selection and allows a later confirmed click', () => {
  let confirmed = false;
  const c = chart({confirm: () => confirmed}); c.record();
  assert.equal(c.state.confirmations, 1);
  assert.equal(c.state.checked, false);
  assert.equal(c.state.posts.length, 0);
  assert.equal(c.state.removed, false);
  confirmed = true; c.state.checked = true; c.record();
  assert.equal(c.state.confirmations, 2);
  assert.equal(c.state.posts.length, 1);
});

test('an absent or nonpositive acknowledgement keeps the consent banner', () => {
  for (const data of [undefined, {value: '0'}, {value: '-1'}]) {
    const c = chart(); c.record();
    assert.equal(c.state.posts.length, 1);
    c.state.posts[0].callback(data, 'success');
    assert.equal(c.state.removed, false);
  }
});

test('the release ledger retires the repaired consent failure as an active regression', () => {
  const ledger = JSON.parse(fs.readFileSync(path.join(__dirname,
    '../docs/testing/release-2026.08-known-outcomes.json'), 'utf8'));
  assert.equal(ledger.browserExpectedFailures.some(row => row.check === 'chart-informed-consent'), false);
  assert.equal(ledger.retiredBrowserExpectations.filter(row =>
    row.check === 'chart-informed-consent' && row.finding === 332).length, 1);
});


test('a lost response after a consent write blocks another save until the chart is reopened', () => {
  const c = chart(); c.record();
  const committed = c.state.posts[0];
  assert.equal(committed.data.value, 'yes');
  committed.failure({status: 0});
  assert.equal(c.state.checked, false);
  assert.equal(c.state.disabled, true);
  assert.equal(c.state.removed, false);
  assert.equal(c.state.alerts.length, 1);
  assert.match(c.state.alerts[0], /could not be confirmed.*reopen the chart.*only if consent is still absent/i);
  c.state.checked = true; c.record();
  assert.equal(c.state.posts.length, 1);
});

test('an unconfirmed acknowledgement clears and disables the checkbox until reopen', () => {
  for (const data of [undefined, {}, {value: '0'}, {value: '-1'}, {value: '1garbage'}, {value: [1]}]) {
    const c = chart(); c.record(); c.state.posts[0].callback(data, 'success');
    assert.equal(c.state.checked, false);
    assert.equal(c.state.disabled, true);
    assert.equal(c.state.removed, false);
    assert.equal(c.state.alerts.length, 1);
  }
});

test('a pending consent write stays unchecked and cannot submit a second attempt', () => {
  const c = chart(); c.record();
  assert.equal(c.state.checked, false);
  assert.equal(c.state.disabled, true);
  c.state.checked = true; c.record();
  assert.equal(c.state.posts.length, 1);
});
