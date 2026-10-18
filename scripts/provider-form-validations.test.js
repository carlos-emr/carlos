/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/**
 * Unit tests for src/main/webapp/share/javascript/provider_form_validations.js, the whole-number check on the
 * preference pages (consultation cut-off date, quick chart size, patient name length, Rx default quantity). The script
 * runs in a vm context with just enough of window/document. Run with:
 *   node --test scripts/provider-form-validations.test.js
 *
 * The page that confirms a saved preference loads the script too but has no form; its onload must not throw there
 * (finding 472: "Cannot set properties of null (setting 'onsubmit')" after every cut-off date save).
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SCRIPT = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/share/javascript/provider_form_validations.js'), 'utf8');

function load(elements) {
  const window = {};
  vm.runInNewContext(SCRIPT, { window, document: { getElementById: id => elements[id] || null } });
  return window;
}

function preferencePage(value) {
  const box = { value, style: {}, focus() { this.focused = true; } };
  const elements = { providerForm: {}, numericFormField: box, errorMessage: { style: {} } };
  load(elements).onload();
  return { submit: () => elements.providerForm.onsubmit(), box, error: elements.errorMessage };
}

test('the saved-preference page, which has no form, loads without a script error', () => {
  const window = load({});
  assert.doesNotThrow(() => window.onload());
});

test('a whole number of months is sent', () => {
  const page = preferencePage('6');
  assert.equal(page.submit(), undefined);
  assert.equal(page.error.style.display, undefined);
});

for (const value of ['abc', '6a', '0']) {
  test(`"${value}" is refused with the page's message`, () => {
    const page = preferencePage(value);
    assert.equal(page.submit(), false);
    assert.equal(page.error.style.display, 'block');
    assert.equal(page.box.style.borderColor, 'red');
  });
}
