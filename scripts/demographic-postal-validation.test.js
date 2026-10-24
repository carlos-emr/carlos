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

function harness(original, value = original, province = 'ON') {
  const alerts = [];
  let focused = false;
  const context = {
    document: { updatedelete: {
      postal: { defaultValue: original, value, focus() { focused = true; } },
      province: { options: [{ value: province }], selectedIndex: 0 },
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

for (const province of ['US', 'US-NY', 'OT']) {
  test(`${province} addresses do not require Canadian postal codes`, () => {
    const h = harness('', '12345', province);
    assert.equal(h.context.checkTypeInEdit(), true);
    assert.deepEqual(h.alerts, []);
  });
}
