/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Execute only the fixed, checked-in JSP's handlers; this is not an HTML sanitizer.
const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm1.jsp'), 'utf8');
const source = [...jsp.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\b[^>]*>/gi)]
  .map(match => match[1]).find(script => script.includes('function valDate('))
  .replace(/<%=[\s\S]*?%>/g, 'carlos').replace(/<fmt:message\b[^>]*\/>/g, 'Localized message');

function editor(values = {}, accept = true) {
  const alerts = [];
  const confirmations = [];
  const focused = [];
  const form = { submit: { value: 'exit' }, action: '/carlos/form/formname' };
  for (const name of ['onDate', 'todayDate']) {
    form[name] = { name, value: values[name] || '', focus() { focused.push(name); } };
  }
  const context = { document: { forms: [form] },
    alert(message) { alerts.push(message); },
    confirm(message) { confirmations.push(message); return accept; },
  };
  vm.createContext(context);
  vm.runInContext(source, context);
  return { context, form, alerts, confirmations, focused };
}

for (const values of [{}, {onDate:'2026/09/30', todayDate:'2026-10-01'},
  {onDate:'2024-02-29', todayDate:'2024/02/29'}]) {
  test(`Save validates the actual date fields without a confirmation: ${JSON.stringify(values)}`, () => {
    const e = editor(values);
    assert.equal(e.context.onSave(), true);
    assert.equal(e.form.submit.value, 'save');
    assert.equal(e.form.target, '_self');
    assert.deepEqual(e.confirmations, []);
    assert.deepEqual(e.alerts, []);
    for (const name of ['onDate', 'todayDate']) assert.equal(e.form[name].value, values[name] || '');
  });
}

for (const name of ['onDate', 'todayDate']) {
  for (const value of ['2026/02/29', '2026-13-01', '2026/04/31', 'not a date',
    '2026//01', '2026/01/', '2026//', '2026--01', '2026-01-', '2026--',
    '2026/01/01/extra', '2026/01/01/']) {
    test(`Save refuses invalid ${name}: ${value}`, () => {
      const e = editor({onDate:'2026/09/30', todayDate:'2026/10/01', [name]:value});
      assert.equal(e.context.onSave(), false);
      assert.equal(e.alerts.length, 1);
      assert.deepEqual(e.focused, [name]);
      assert.deepEqual(e.confirmations, []);
      assert.equal(e.form[name].value, value);
    });
  }
  test(`a missing ${name} control fails validation without an uncaught exception`, () => {
    const e = editor();
    delete e.form[name];
    assert.equal(e.context.onSave(), false);
    assert.equal(e.alerts.length, 1);
    assert.deepEqual(e.confirmations, []);
  });
}

for (const accepted of [true, false]) {
  test(`Save and Exit honours its single confirmation: ${accepted}`, () => {
    const e = editor({onDate:'2026/09/30', todayDate:'2026-10-01'}, accepted);
    assert.equal(e.context.onSaveExit(), accepted);
    assert.equal(e.form.submit.value, 'exit');
    assert.equal(e.confirmations.length, 1);
    assert.deepEqual(e.alerts, []);
    assert.equal(e.form.onDate.value, '2026/09/30');
    assert.equal(e.form.todayDate.value, '2026-10-01');
  });
}
