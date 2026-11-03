/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/documentManager/attachDocument.jsp'), 'utf8');
const start = jsp.indexOf('        function toggleSelectAll(');
const end = jsp.indexOf('        // advisoryIssues', start);
assert.ok(start >= 0 && end > start);
const toggleSelectAll = jsp.slice(start, end);

function picker() {
  const options = [
    { prefix: 'hrm_', checked: false },
    { prefix: 'hrm_', version: true, checked: false },
    { prefix: 'hrm_', version: true, checked: true },
    { prefix: 'hrm_', disabled: true, checked: false },
    { prefix: 'lab_', checked: false },
    { prefix: 'lab_', version: true, checked: false },
  ];
  const context = { jQuery(element) {
    if (typeof element !== 'string') return { prop() { return element.checked; } };
    const prefix = element.match(/class\^='([^']+)'/)[1];
    const selected = options.filter(option => option.prefix === prefix && !option.disabled
      && (!element.includes(':not([data-version])') || !option.version));
    return { prop(name, value) { assert.equal(name, 'checked'); selected.forEach(option => { option.checked = value; }); } };
  } };
  vm.createContext(context);
  vm.runInContext(toggleSelectAll, context);
  return { options, toggle: context.toggleSelectAll };
}

test('selecting all HRM reports keeps previous attachments without adding older versions', () => {
  const { options, toggle } = picker();
  toggle({ checked: true }, 'hrm_');
  assert.deepEqual(options.map(option => option.checked), [true, false, true, false, false, false]);
});

test('clearing all HRM reports removes current and previously attached versions', () => {
  const { options, toggle } = picker();
  toggle({ checked: false }, 'hrm_');
  assert.deepEqual(options.map(option => option.checked), [false, false, false, false, false, false]);
});

test('selecting all labs still selects lab options', () => {
  const { options, toggle } = picker();
  toggle({ checked: true }, 'lab_');
  assert.deepEqual(options.map(option => option.checked), [false, false, true, false, true, true]);
});
