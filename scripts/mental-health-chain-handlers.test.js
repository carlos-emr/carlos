/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// The Print, Save and Save and Exit handlers of the three Mental Health pages (Referral, Assessment, Outcome),
// run in node against a stand-in for the page's form. Like the real form, it has a hidden input named
// `submit`, which shadows the form's own submit() method: calling document.forms[0].submit() on it throws,
// which is what kept Print from requesting any print page (#4596). The Assessment Save also reloaded the
// window that opened it, the E-Chart (#4600), and its Save and Exit read an undeclared variable.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const PAGES = {
  referral: { catalogue: 'r_rps1' },
  assessment: { catalogue: 'a_aps1' },
  outcome: { catalogue: 'o_sp1' },
};

// Execute only the checked-in JSP's handler block; this is not an HTML sanitizer.
function handlerSource(page) {
  const jsp = fs.readFileSync(path.join(__dirname, `../src/main/webapp/WEB-INF/jsp/form/formmh${page}.jsp`), 'utf8');
  return [...jsp.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\b[^>]*>/gi)]
    .map(match => match[1]).find(script => script.includes('function onPrint('))
    .replace(/<%--[\s\S]*?--%>/g, '')
    .replace(/<%=[\s\S]*?%>/g, '/carlos')
    .replace(/<%[\s\S]*?%>/g, '')
    .replace(/<fmt:message\b[^>]*\/>/g, 'Localized message');
}

function pageOf(page, { accept = true, values = {} } = {}) {
  const submitted = [];
  const opened = [];
  const confirmations = [];
  const alerts = [];
  const openerTouched = [];
  // Every input the handlers read exists, empty unless given; the hidden `submit` input is the one that shadows.
  const fields = { submit: { name: 'submit', value: 'exit' }, action: '/carlos/form/formname', target: '' };
  const form = new Proxy(fields, {
    get(target, name) {
      if (!(name in target) && typeof name === 'string') {
        target[name] = { name, value: values[name] || '', focus() {} };
      }
      return target[name];
    },
  });
  function HTMLFormElement() {}
  HTMLFormElement.prototype.submit = function submit() {
    submitted.push({ action: this.action, target: this.target, submit: this.submit.value });
  };
  const context = {
    HTMLFormElement,
    document: { forms: [form] },
    window: {
      open(url, name) { opened.push({ url, name }); return {}; },
      get opener() { openerTouched.push('opener'); return { location: { reload() { openerTouched.push('reload'); } } }; },
    },
    alert(message) { alerts.push(message); },
    confirm(message) { confirmations.push(message); return accept; },
  };
  context.window.document = context.document;
  vm.createContext(context);
  vm.runInContext(handlerSource(page), context);
  context.getMainAction();
  return { context, form, submitted, opened, confirmations, alerts, openerTouched };
}

for (const page of Object.keys(PAGES)) {
  test(`${page}: Print posts the page to its print view in the notice window, though the hidden submit input shadows submit()`, () => {
    const p = pageOf(page);
    assert.throws(() => p.form.submit(), TypeError, 'the stand-in must shadow submit() as the real form does');

    assert.equal(p.context.onPrint(), false);

    assert.deepEqual(p.submitted, [{ action: `/carlos/form/formmh${page}print`, target: 'planner', submit: 'print' }]);
    assert.equal(p.opened.length, 1);
    assert.equal(p.opened[0].name, 'planner');
    assert.match(p.opened[0].url, /\/provider\/notice\.htm$/);
    // The form is left as Save expects to find it, not aimed at a window named apptProviderSearch.
    assert.equal(p.form.action, '/carlos/form/formname');
    assert.equal(p.form.target, '');
  });

  test(`${page}: Print refuses a catalogue number that is not a number and requests nothing`, () => {
    const p = pageOf(page, { values: { [PAGES[page].catalogue]: 'abc' } });

    assert.equal(p.context.onPrint(), false);

    assert.deepEqual(p.submitted, []);
    assert.deepEqual(p.opened, []);
    assert.equal(p.alerts.length, 1);
  });

  for (const accept of [true, false]) {
    test(`${page}: Save asks once, answers with the confirmation (${accept}) and leaves the window that opened it alone`, () => {
      const p = pageOf(page, { accept, values: { o_formDate: '2026/09/30' } });

      assert.equal(p.context.onSave(), accept);

      assert.equal(p.form.submit.value, 'save');
      assert.equal(p.confirmations.length, 1);
      assert.deepEqual(p.openerTouched, []);
      assert.deepEqual(p.submitted, []);
    });

    test(`${page}: Save and Exit posts submit=exit after one confirmation (${accept}), even after Print`, () => {
      const p = pageOf(page, { accept, values: { o_formDate: '2026/09/30' } });
      p.context.onPrint();

      assert.equal(p.context.onSaveExit(), accept);

      assert.equal(p.form.submit.value, 'exit');
      assert.equal(p.form.action, '/carlos/form/formname');
      assert.equal(p.confirmations.length, 1);
      assert.deepEqual(p.openerTouched, []);
    });
  }
}
