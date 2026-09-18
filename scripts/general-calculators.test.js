/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function calculator() {
  const source = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/encounter/calculators/GeneralCalculators.jsp'), 'utf8');
  const forms = [6, 7, 5].map(count => {
    const form = { elements: [{}] };
    for (let i = 1; i <= count; i++) {
      let value = '';
      const field = { get value() { return value; }, set value(next) { value = String(next); },
        message: '', setCustomValidity(message) { this.message = message; },
        reportValidity() { return !this.message; } };
      form.elements.push(field);
      form[`val${i}`] = field;
    }
    return form;
  });
  const context = vm.createContext({ document: { forms } });
  for (const match of source.matchAll(/<script\b([^>]*?)>([\s\S]*?)<\/script>/gi)) {
    if (/\bsrc\s*=/i.test(match[1])) continue; // External library, not an inline calculator block.
    vm.runInContext(match[2], context);
  }
  return { forms, context };
}

// NIST SP 811 unit identities, independent of the implementation's SI-to-unit factors.
for (const [group, field, value, expected, tolerance] of [
  [0, 'val2', '12', 0.3048, 1e-7],
  [0, 'val6', '1', 1852, 1e-3],
  [1, 'val3', '1', 0.45359237, 1e-6],
  [1, 'val4', '1', 0.3732417216, 1e-6],
  [1, 'val5', '1', 6.35029318, 1e-5],
  [1, 'val6', '1', 907.18474, 1e-3],
  [1, 'val7', '1', 1016.0469088, 1e-2],
  [2, 'val2', '1', 0.0295735295625, 1e-7],
  [2, 'val3', '1', 0.946352946, 1e-6],
  [2, 'val4', '1', 3.785411784, 1e-5],
  [2, 'val5', '1', 4.54609, 1e-5],
]) {
  test(`calculator initializes and converts ${group}/${field} to its SI base unit`, () => {
    const { forms, context } = calculator();
    forms[group][field].value = value;
    assert.equal(context.convertform(forms[group]), true);
    assert.ok(Math.abs(Number(forms[group].val1.value) - expected) <= tolerance);
  });
}

test('zero, tiny and large finite values remain meaningful', () => {
  for (const input of ['0', '1e-20', '1e20']) {
    const { forms, context } = calculator();
    forms[0].val1.value = input;
    assert.equal(context.convertform(forms[0]), true);
    assert.equal(Number(forms[0].val1.value), Number(input));
    assert.notEqual(forms[0].val2.value, '');
    assert.ok(Math.abs(Number(forms[0].val2.value) * 0.0254 - Number(input)) <= Math.abs(Number(input)) * 1e-6);
  }
});

test('invalid and overflowing input is rejected without publishing partial results', () => {
  for (const input of ['invalid', 'Infinity', '1e309', '1e308']) {
    const { forms, context } = calculator();
    forms[0].val1.value = input;
    assert.equal(context.convertform(forms[0]), false);
    assert.notEqual(forms[0].val1.message, '');
    assert.equal(forms[0].val2.value, '');
    context.resetform(forms[0]);
    assert.equal(forms[0].val1.message, '');
    assert.equal(forms[0].val1.value, '1');
    assert.ok(Number(forms[0].val2.value) > 0);
  }
});
