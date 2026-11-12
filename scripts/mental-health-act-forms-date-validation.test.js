/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Mental Health Act Forms 14 and 42 validate their own date boxes on Save (#4597). They carried the lab
// requisition's checkAllDates(), over b_dateSigned and o_specimenCollectionDate, which these forms do not have:
// every Save threw a TypeError, the browser posted anyway, and none of the forms' own dates was checked. The
// forms ask for day / month / year, so dd/mm/yyyy is accepted as well as CARLOS's yyyy/mm/dd.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const FORMS = {
  14: ['signatureDate'],
  42: ['dateOfExamination', 'dateOfSign', 'dateOfOrder', 'dateOfSign2'],
};

// Execute only the checked-in JSP's handler block; this is not an HTML sanitizer.
function handlerSource(number) {
  const jsp = fs.readFileSync(path.join(__dirname,
    `../src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm${number}.jsp`), 'utf8');
  return [...jsp.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\b[^>]*>/gi)]
    .map(match => match[1]).find(script => script.includes('function valDate('))
    .replace(/<%=[\s\S]*?%>/g, 'carlos').replace(/<fmt:message\b[^>]*\/>/g, 'Localized message');
}

function editor(number, values = {}, accept = true) {
  const alerts = [];
  const confirmations = [];
  const focused = [];
  const form = { submit: { value: 'exit' }, action: '/carlos/form/formname' };
  for (const name of FORMS[number]) {
    form[name] = { name, value: values[name] || '', focus() { focused.push(name); } };
  }
  const context = { document: { forms: [form] },
    alert(message) { alerts.push(message); },
    confirm(message) { confirmations.push(message); return accept; },
  };
  vm.createContext(context);
  vm.runInContext(handlerSource(number), context);
  return { context, form, alerts, confirmations, focused };
}

const VALID = ['2026/09/30', '2026-09-30', '30/09/2026', '30-09-2026', '29/02/2024', '2024/2/29', '1/1/2026'];
const INVALID = ['2026/02/29', '29/02/2026', '2026-13-01', '31/04/2026', '30/09/26', 'not a date', '2026//01', '2026/01/',
  '//2026', '2026/09-30', '2026/01/01/extra', '30.09.2026'];

for (const [number, dates] of Object.entries(FORMS)) {
  test(`Form ${number}: Save posts with every date box empty and raises nothing`, () => {
    const e = editor(number);
    assert.equal(e.context.onSave(), true);
    assert.equal(e.form.submit.value, 'save');
    assert.deepEqual(e.alerts, []);
    assert.deepEqual(e.confirmations, []);
  });

  for (const name of dates) {
    for (const value of VALID) {
      test(`Form ${number}: Save accepts ${name} ${value}`, () => {
        const e = editor(number, { [name]: value });
        assert.equal(e.context.onSave(), true);
        assert.deepEqual(e.alerts, []);
        assert.equal(e.form[name].value, value);
      });
    }
    for (const value of INVALID) {
      test(`Form ${number}: Save refuses ${name} ${value} with one alert and focus on it`, () => {
        const e = editor(number, { [name]: value });
        assert.equal(e.context.onSave(), false);
        assert.equal(e.alerts.length, 1);
        assert.deepEqual(e.focused, [name]);
        assert.equal(e.form[name].value, value);
      });
    }
    test(`Form ${number}: a missing ${name} control fails validation without an uncaught exception`, () => {
      const e = editor(number);
      delete e.form[name];
      assert.equal(e.context.onSave(), false);
      assert.equal(e.alerts.length, 1);
    });
  }

  for (const accepted of [true, false]) {
    test(`Form ${number}: Save and Exit validates, then honours its single confirmation (${accepted})`, () => {
      const e = editor(number, { [dates[0]]: '30/09/2026' }, accepted);
      assert.equal(e.context.onSaveExit(), accepted);
      assert.equal(e.form.submit.value, 'exit');
      assert.equal(e.confirmations.length, 1);
      assert.deepEqual(e.alerts, []);
    });
  }

  test(`Form ${number}: Save and Exit with an invalid date asks nothing`, () => {
    const e = editor(number, { [dates[0]]: '2026/02/30' });
    assert.equal(e.context.onSaveExit(), false);
    assert.deepEqual(e.confirmations, []);
    assert.equal(e.alerts.length, 1);
  });
}
