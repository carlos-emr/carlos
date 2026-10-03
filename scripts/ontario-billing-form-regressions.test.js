/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const base = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/billing/CA/ON');
const form = fs.readFileSync(path.join(base, 'billingON.jsp'), 'utf8');
const review = fs.readFileSync(path.join(base, 'billingONReview.jsp'), 'utf8');

// Execute the actual JSP functions; replace localized text with inert test labels.
function functionSource(source, name) {
  const start = source.indexOf(`function ${name}(`);
  assert.ok(start >= 0, `Missing function ${name}`);
  const end = source.indexOf('\n        function ', start + 1);
  const endAtFourSpaces = source.indexOf('\n    function ', start + 1);
  const bounds = [end, endAtFourSpaces].filter(value => value > start);
  assert.ok(bounds.length, `No boundary after ${name}`);
  return source.slice(start, Math.min(...bounds)).trim()
    .replace(/\$\{carlos:forJavaScript\([^}]+\)\}/g, 'Validation message');
}
function load(source, names, context) {
  vm.createContext(context);
  for (const name of names) vm.runInContext(functionSource(source, name), context);
  return context;
}

test('service percent validates live named inputs, including previously empty boxes', () => {
  const fields = [{ value: '' }, { value: '' }];
  const context = load(form, ['checkServicePercent'], {
    jQuery(selector) {
      // The rendered inputs have names and no serviceAt IDs.
      assert.equal(selector, "input[name^='serviceAt']");
      return { each(fn) { for (const field of fields) if (fn.call(field) === false) break; } };
    },
  });
  for (const value of ['', '1', '0.5', '-0.5', '2.25']) {
    fields[1].value = value;
    assert.equal(context.checkServicePercent(), true, value);
  }
  for (const value of ['abc', 'NaN', '1.2.3', '0.5oops', '12345', '1E999999999']) {
    fields[1].value = value;
    assert.equal(context.checkServicePercent(), false, value);
  }
});

test('visit changes preserve hospital and nursing-home admission dates independent of option order', () => {
  const admission = { value: '2026-01-15' };
  const visit = { value: '02| Hospital' };
  const context = load(form.replaceAll('${formModel.multisite.rmaEnabled}', 'false'), ['updateDate'], {
    document: { forms: [{ xml_visittype: visit }], getElementById: () => admission },
  });
  for (const value of ['02| Hospital', '04| Nursing home']) {
    visit.value = value;
    context.updateDate();
    assert.equal(admission.value, '2026-01-15');
  }
  visit.value = '01| Outpatient';
  context.updateDate();
  assert.equal(admission.value, '');
});

test('RMA clinic numbers never retain a hospital or nursing-home admission date', () => {
  const admission = { value: '' };
  const clinic = { value: '' };
  const context = load(form.replaceAll('${formModel.multisite.rmaEnabled}', 'true'), ['updateDate'], {
    document: { forms: [{ xml_visittype: clinic }], getElementById: () => admission },
  });
  for (const value of ['02| Clinic A', '04| Clinic B', '01| Clinic C']) {
    admission.value = '2026-01-15';
    clinic.value = value;
    context.updateDate();
    assert.equal(admission.value, '', value);
  }
});

test('invalid reviews load and allow Back to Edit without a total field', () => {
  const context = load(review, ['showtotal', 'checkTotal'], {
    bClick: false, document: { getElementById: () => null }, alert: () => assert.fail('Unexpected alert'),
  });
  assert.doesNotThrow(() => context.showtotal());
  assert.equal(context.checkTotal(), true);
  context.bClick = true;
  assert.equal(context.checkTotal(), false, 'Missing totals must not authorize Save');
});

test('OHIP line edits preserve cents without private-payer total fields', () => {
  const fields = { total: { value: '0.00' } };
  const context = load(review, ['updateElement', 'calculateTotal', 'onTotalChanged'], {
    document: {
      getElementById: id => fields[id] || null,
      querySelectorAll: () => [{ value: '12.34' }, { value: '1.25' }],
    },
    alert: () => assert.fail('Unexpected alert'),
  });
  context.calculateTotal();
  assert.equal(fields.total.value, '13.59');
  fields.total.value = '12.34';
  assert.doesNotThrow(() => context.onTotalChanged());
  assert.equal(fields.total.value, '12.34');
});

test('private totals remain synchronized when their fields are present', () => {
  const fields = { total: { value: '0.00' }, gstBilledTotal: { value: '' }, stotal: { value: '' } };
  const context = load(review, ['updateElement', 'calculateTotal'], {
    document: { getElementById: id => fields[id], querySelectorAll: () => [{ value: '12.34' }] },
  });
  context.calculateTotal();
  for (const field of Object.values(fields)) assert.equal(field.value, '12.34');
});
