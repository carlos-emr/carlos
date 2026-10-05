/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

// Execute the shipped handlers; only server-rendered strings and browser I/O are substituted.
const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/form/formDischargeSummary.jsp'), 'utf8');
const source = [...jsp.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)]
  .map(match => match[1]).find(script => script.includes('function valDate('))
  .replace(/<%=[\s\S]*?%>/g, 'carlos').replace(/<fmt:message\b[^>]*\/>/g, 'Localized message');

function editor(value, accept = true) {
  const alerts = [];
  const confirmations = [];
  let focused = 0;
  const date = { name: 'dischargeDate', value, focus() { focused++; } };
  const form = { dischargeDate: date, submit: { value: 'exit' } };
  const context = { document: { forms: [form] },
    alert(message) { alerts.push(message); },
    confirm(message) { confirmations.push(message); return accept; },
  };
  vm.createContext(context);
  vm.runInContext(source, context);
  return { context, form, alerts, confirmations, get focused() { return focused; } };
}

for (const value of ['', '2026/09/30', '2024/02/29']) {
  test(`Save validates only the rendered date field and confirms once: ${value || '(blank)'}`, () => {
    const e = editor(value);
    assert.equal(e.context.onSave(), true);
    assert.equal(e.form.submit.value, 'save');
    assert.equal(e.confirmations.length, 1);
    assert.deepEqual(e.alerts, []);
    assert.equal(e.form.dischargeDate.value, value);
  });
}

for (const value of ['2026/02/29', '2026/13/01', '2026/04/31', 'not a date']) {
  test(`Save refuses an invalid date before confirmation: ${value}`, () => {
    const e = editor(value);
    assert.equal(e.context.onSave(), false);
    assert.equal(e.alerts.length, 1);
    assert.equal(e.confirmations.length, 0);
    assert.equal(e.focused, 1);
    assert.equal(e.form.dischargeDate.value, value);
  });
}

for (const handler of ['onSave', 'onSaveExit']) {
  test(`${handler} honours a cancelled confirmation without changing the typed date`, () => {
    const e = editor('2026/09/30', false);
    assert.equal(e.context[handler](), false);
    assert.equal(e.confirmations.length, 1);
    assert.deepEqual(e.alerts, []);
    assert.equal(e.form.dischargeDate.value, '2026/09/30');
  });
}
