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
  const state = {checked, attribute, confirmations: 0, posts: [], removed: false};
  const checkbox = {
    is(selector) { assert.equal(selector, ':checked'); return state.checked; },
    prop(name, value) { assert.equal(name, 'checked'); state.checked = value; },
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
    state.posts.push({data, callback});
  };
  const context = {ctx: '/carlos', jQuery, window: {confirm() {
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
