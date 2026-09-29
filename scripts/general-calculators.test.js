/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

for (const fragment of ['', '#weight', '#temps']) {
  test(`legacy calculator redirect and fallback preserve fragment ${fragment || '(empty)'}`, () => {
    const source = fs.readFileSync(path.join(__dirname,
      '../src/main/webapp/encounter/calculators/GeneralCalculators.htm'), 'utf8');
    const start = source.indexOf('<script>');
    const end = source.indexOf('</script>', start);
    assert.ok(start >= 0 && end > start);
    const link = { href: 'ViewGeneralCalculators' };
    let destination;
    // Executes only the checked-in fixture above in a mock VM; no external input or HTML output.
    // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag
    vm.runInNewContext(source.slice(start + '<script>'.length, end), {
      document: { getElementById: () => link },
      window: { location: { hash: fragment, replace: value => { destination = value; } } },
    });
    assert.equal(destination, `ViewGeneralCalculators${fragment}`);
    assert.equal(link.href, destination);
  });
}

function calculator() {
  const source = fs.readFileSync(path.join(__dirname,
    '../src/main/webapp/WEB-INF/jsp/encounter/calculators/GeneralCalculators.jsp'), 'utf8');
  const forms = [6, 7, 5].map(count => {
    const form = { elements: [{}] };
    for (let i = 1; i <= count; i++) {
      let value = '';
      const field = { get value() { return value; }, set value(next) { value = String(next); },
        form,
        message: '', setCustomValidity(message) { this.message = message; },
        reportValidity() { return !this.message; } };
      form.elements.push(field);
      form[`val${i}`] = field;
    }
    return form;
  });
  const context = vm.createContext({ document: { forms } });
  // Extract trusted repository fixtures, not HTML sanitization. Browsers also
  // accept whitespace and stray attributes on an end tag.
  for (const match of source.matchAll(/<script\b([^>]*?)>([\s\S]*?)<\/script\b[^>]*>/gi)) {
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

test('editing a converted field selects that unit and clears stale outputs and errors', () => {
  const { forms, context } = calculator();
  const form = forms[0];
  context.resetform(form);
  form.val1.setCustomValidity('Previous error');
  form.val2.value = '12';
  context.conversionInputChanged(form.val2);
  assert.equal(form.val1.value, '');
  assert.equal(form.val1.message, '');
  assert.equal(form.val2.value, '12');
  assert.equal(context.convertform(form), true);
  assert.equal(Number(form.val3.value), 1);
});

test('temperature conversions preserve freezing, boiling and the shared negative point', () => {
  const { forms, context } = calculator();
  const input = forms[0].val1;
  const output = forms[0].val2;
  forms[0].elements.temperatureOutput = output;
  for (const [value, toCelsius, expected] of [
    ['32', true, 0], ['212', true, 100], ['-40', true, -40],
    ['0', false, 32], ['100', false, 212], ['-40', false, -40],
  ]) {
    input.value = value;
    context.convertTemperature(input, 'temperatureOutput', toCelsius);
    assert.equal(Number(output.value), expected);
    assert.equal(input.message, '');
  }
});

test('invalid temperatures clear output and an empty field clears the error without calculating', () => {
  const { forms, context } = calculator();
  const input = forms[0].val1;
  const output = forms[0].val2;
  forms[0].elements.temperatureOutput = output;
  for (const value of ['invalid', 'Infinity', '1e309']) {
    input.value = value;
    output.value = '100';
    context.convertTemperature(input, 'temperatureOutput', true);
    assert.notEqual(input.message, '');
    assert.equal(output.value, '');
  }
  input.value = '  ';
  context.convertTemperature(input, 'temperatureOutput', true);
  assert.equal(input.message, '');
  assert.equal(output.value, '');
});

test('correcting temperature through the opposite field clears its stale validation error', () => {
  const { forms, context } = calculator();
  const fahrenheit = forms[0].val1;
  const celsius = forms[0].val2;
  forms[0].elements.F = fahrenheit;
  forms[0].elements.C = celsius;
  fahrenheit.value = 'invalid';
  context.convertTemperature(fahrenheit, 'C', true);
  assert.notEqual(fahrenheit.message, '');
  celsius.value = '20';
  context.convertTemperature(celsius, 'F', false);
  assert.equal(fahrenheit.value, '68');
  assert.equal(fahrenheit.message, '');
  fahrenheit.value = 'invalid';
  context.convertTemperature(fahrenheit, 'C', true);
  celsius.value = '';
  context.convertTemperature(celsius, 'F', false);
  assert.equal(fahrenheit.value, '');
  assert.equal(fahrenheit.message, '');
});
