/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (C) 2026 CARLOS Contributors */
'use strict';
// Master Record custom-field multi-selects (js/demographic/custom-selects.js, #6336): a multiple
// select with every option cleared submits nothing, so the script adds an empty value on formdata.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(path.join(__dirname, '..', 'src/main/webapp/js/demographic/custom-selects.js'), 'utf8');

function fakeForm() {
  const listeners = {};
  return {
    listeners,
    addEventListener(type, fn) { (listeners[type] ||= []).push(fn); },
  };
}

/** A select fake: selected count, enabled flag, wrapper name, multiple flag and owning form. */
function fakeSelect(form, { name = 'cust_a', wrapper = name, multiple = true, enabled = true, selected = 1 } = {}) {
  return {
    name,
    form,
    multiple,
    enabled,
    selectedOptions: { length: selected },
    matches(selector) {
      if (selector === 'select[multiple]:enabled') return this.multiple && this.enabled;
      if (selector === ':enabled') return this.enabled;
      throw new Error(`unexpected selector ${selector}`);
    },
    closest(selector) {
      assert.equal(selector, '[data-custom-select-name]');
      return wrapper === null ? null : { dataset: { customSelectName: wrapper } };
    },
  };
}

function load(form) {
  const document = { getElementById: (id) => (id === 'updatedelete' ? form : null) };
  vm.runInNewContext(SOURCE, { document });
  return form;
}
const change = (form, select) => form.listeners.change.forEach((fn) => fn({ target: select }));
function submit(form, existing = []) {
  const present = new Set(existing);
  const appended = [];
  const formData = { has: (n) => present.has(n), append: (n, v) => { appended.push([n, v]); present.add(n); } };
  form.listeners.formdata.forEach((fn) => fn({ formData }));
  return appended;
}

test('clearing a multiple custom select submits one empty value for its name', () => {
  const form = load(fakeForm());
  const select = fakeSelect(form);
  select.selectedOptions.length = 0;
  change(form, select);
  assert.deepEqual(submit(form), [['cust_a', '']]);
});

test('selecting an option again after clearing submits nothing extra', () => {
  const form = load(fakeForm());
  const select = fakeSelect(form);
  select.selectedOptions.length = 0;
  change(form, select);
  select.selectedOptions.length = 1;
  change(form, select);
  assert.deepEqual(submit(form), []);
});

test('selects that are not this form\'s named multiple custom select are ignored', () => {
  const form = load(fakeForm());
  const cases = [
    fakeSelect(form, { wrapper: 'other_name' }),
    fakeSelect(form, { wrapper: null }),
    fakeSelect(form, { multiple: false }),
    fakeSelect(fakeForm()),
    fakeSelect(form, { name: '' , wrapper: ''}),
  ];
  for (const select of cases) { select.selectedOptions.length = 0; change(form, select); }
  assert.deepEqual(submit(form), []);
});

test('a disabled select is never changed into a cleared one, nor appended if disabled later', () => {
  const form = load(fakeForm());
  const disabledAtChange = fakeSelect(form, { name: 'cust_b', enabled: false, selected: 0 });
  change(form, disabledAtChange);
  const disabledLater = fakeSelect(form, { name: 'cust_c', selected: 0 });
  change(form, disabledLater);
  disabledLater.enabled = false;
  assert.deepEqual(submit(form), []);
});

test('a name the form already carries is not duplicated, and a reselected-before-submit select is skipped', () => {
  const form = load(fakeForm());
  const present = fakeSelect(form, { name: 'cust_d', selected: 0 });
  change(form, present);
  const reselected = fakeSelect(form, { name: 'cust_e', selected: 0 });
  change(form, reselected);
  reselected.selectedOptions.length = 1; // changed without a change event reaching the script
  assert.deepEqual(submit(form, ['cust_d']), []);
});

test('the script does nothing when the Master Record form is absent', () => {
  const calls = [];
  const document = { getElementById: (id) => { calls.push(id); return null; } };
  assert.doesNotThrow(() => vm.runInNewContext(SOURCE, { document }));
  assert.deepEqual(calls, ['updatedelete']);
});
