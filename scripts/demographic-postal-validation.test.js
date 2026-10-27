/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/demographic/demographiceditdemographic.js.jsp', 'utf8');
const section = (start, end) => {
  const from = jsp.indexOf(start);
  const to = jsp.indexOf(end, from);
  assert.ok(from >= 0 && to > from, `Missing ${start}`);
  return jsp.slice(from, to).replace(/<%[\s\S]*?%>/g, '');
};

function harness(original, value = original, province = 'ON', originalProvince = province) {
  const alerts = [];
  let focused = false;
  const context = {
    document: { updatedelete: {
      postal: { defaultValue: original, value, dataset: { originalProvince }, focus() { focused = true; } },
      province: { value: province, options: [{ value: province }], selectedIndex: 0 },
    } },
    alert(message) { alerts.push(message); },
    checkName: () => true, checkDob: () => true, checkHin: () => true,
    checkSex: () => true, checkRosterStatus: () => true, checkPatientStatus: () => true,
  };
  vm.runInNewContext(section('function checkTypeInEdit()', 'function formatPhoneNum(')
    + section('function isPostalCode(', 'function isPostalCode2(')
    + section('function isCanadian()', 'function updateProvinces('), context);
  return { context, alerts, focused: () => focused };
}

for (const original of ['', 'UNKNOWN', '12345']) {
  test(`an unrelated edit preserves the unchanged legacy postal value ${JSON.stringify(original)}`, () => {
    const h = harness(original);
    assert.equal(h.context.checkTypeInEdit(), true);
    assert.deepEqual(h.alerts, []);
    assert.equal(h.context.document.updatedelete.postal.value, original);
  });
}

for (const value of ['K1A0B1', 'K1A 0B1', 'k1a 0b1', ' K1A 0B1 ', 'K 1 A 0 B 1']) {
  test(`accepts a changed Canadian postal code ${JSON.stringify(value)}`, () => {
    const h = harness('UNKNOWN', value);
    assert.equal(h.context.checkTypeInEdit(), true);
    assert.deepEqual(h.alerts, []);
  });
}

for (const value of ['', '12345', 'K1A', 'K1A0B1junk', 'sK1A0B1', 'K1A-0B1']) {
  test(`a changed invalid postal code ${JSON.stringify(value)} still blocks saving`, () => {
    const h = harness('K1A0B1', value);
    assert.equal(h.context.checkTypeInEdit(), false);
    assert.deepEqual(h.alerts, ['The entered Postal Code is not valid']);
    assert.equal(h.focused(), true);
  });
}

test('field-change validation still announces an invalid legacy value', () => {
  const h = harness('UNKNOWN');
  assert.equal(h.context.isPostalCode(), false);
  assert.equal(h.alerts.length, 1);
});

for (const originalProvince of ['US-NY', 'BC']) {
  test(`changing province from ${originalProvince} to ON revalidates an unchanged invalid postal code`, () => {
    const h = harness('12345', '12345', 'ON', originalProvince);
    assert.equal(h.context.checkTypeInEdit(), false);
    assert.equal(h.alerts.length, 1);
  });
}

test('an ISO province change revalidates an unchanged invalid postal code', () => {
  const h = harness('UNKNOWN', 'UNKNOWN', 'CA-BC', 'CA-ON');
  assert.equal(h.context.checkTypeInEdit(), false);
  assert.equal(h.alerts.length, 1);
});

test('ISO initialization remembers the rendered province without resetting it on later choices', () => {
  const h = harness('UNKNOWN', 'UNKNOWN', 'CA-ON', 'ON');
  const edit = fs.readFileSync('src/main/webapp/WEB-INF/jsp/demographic/edit.jsp', 'utf8');
  const start = edit.indexOf('function updateProvinces(');
  const end = edit.indexOf('function updateResidentialProvinces(', start);
  assert.ok(start >= 0 && end > start);
  const fields = h.context.document.updatedelete;
  const select = {
    empty() { return this; },
    val(value) {
      if (value === undefined) return fields.province.value;
      fields.province.value = value;
      fields.province.options[0].value = value;
      return this;
    },
  };
  const jquery = selector => selector === '#country' ? { val: () => 'CA' } : select;
  jquery.ajax = options => options.success([]);
  jquery.each = () => {};
  Object.assign(h.context, { jQuery: jquery });
  vm.runInNewContext(edit.slice(start, end).replace(/<%[\s\S]*?%>/g, ''), h.context);
  h.context.updateProvinces('CA-ON', true);
  assert.equal(fields.postal.dataset.originalProvince, 'CA-ON');
  assert.equal(h.context.checkTypeInEdit(), true, 'normalizing legacy ON must not block unrelated edits');
  h.context.updateProvinces('CA-BC');
  assert.equal(fields.postal.dataset.originalProvince, 'CA-ON');
  assert.equal(h.context.checkTypeInEdit(), false, 'a later province choice must still be validated');
});

test('changing province still accepts an unchanged valid Canadian postal code', () => {
  const h = harness('K1A0B1', 'K1A0B1', 'ON', 'BC');
  assert.equal(h.context.checkTypeInEdit(), true);
  assert.deepEqual(h.alerts, []);
});

for (const province of ['US', 'US-NY', 'OT']) {
  test(`${province} addresses do not require Canadian postal codes`, () => {
    const h = harness('', '12345', province);
    assert.equal(h.context.checkTypeInEdit(), true);
    assert.deepEqual(h.alerts, []);
  });
}
