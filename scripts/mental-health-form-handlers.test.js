/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const directory = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/form');
const forms = [
  {file: 'formMentalHealthForm14.jsp', dates: ['signatureDate']},
  {file: 'formMentalHealthForm42.jsp', dates: ['dateOfExamination', 'dateOfSign', 'dateOfOrder', 'dateOfSign2']},
];

function editor(form, values = {}, accept = true) {
  const jsp = fs.readFileSync(path.join(directory, form.file), 'utf8');
  const script = [...jsp.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\b[^>]*>/gi)]
    .map(match => match[1]).find(source => source.includes('function valDate('))
    .replace(/<%=[\s\S]*?%>/g, 'carlos')
    .replace(/<fmt:message\b[^>]*\/>/g, 'Localized message')
    .replace(/\$\{carlos:forJavaScript\(mentalHealthInvalidDateMessage\)\}/g, 'Invalid date in field ');
  const alerts = [], confirmations = [], focused = [];
  const page = {submit: {value: 'exit'}, action: '/carlos/form/formname', target: 'original'};
  for (const name of form.dates) {
    assert.match(jsp, new RegExp(`name="${name}"`), 'The validator must refer to a real rendered control');
    page[name] = {name, value: values[name] ?? '', focus() {focused.push(name);}};
  }
  const context = {document: {forms: [page]}, alert(message) {alerts.push(message);},
    confirm(message) {confirmations.push(message); return accept;}};
  vm.createContext(context); vm.runInContext(script, context);
  return {context, page, alerts, confirmations, focused};
}

for (const form of forms) {
  for (const value of ['', '2026/09/30', '2026-09-30', '30/09/2026', '2024/02/29', '29/02/2024']) {
    test(`${form.file} saves actual date controls unchanged: ${value || '(blank)'}`, () => {
      const values = Object.fromEntries(form.dates.map(name => [name, value]));
      const e = editor(form, values);
      assert.equal(e.context.onSave(), true);
      assert.equal(e.page.submit.value, 'save'); assert.equal(e.page.target, '_self');
      assert.deepEqual(e.alerts, []); assert.deepEqual(e.confirmations, []);
      for (const name of form.dates) assert.equal(e.page[name].value, value);
    });
  }

  for (const name of form.dates) {
    for (const value of ['2026/02/29', '31/04/2026', '2026//30', '30/09/2026/extra', 'not a date']) {
      test(`${form.file} rejects invalid ${name} without discarding it: ${value}`, () => {
        const e = editor(form, {[name]: value});
        assert.equal(e.context.onSave(), false);
        assert.equal(e.alerts.length, 1); assert(e.alerts[0].includes(name));
        assert.deepEqual(e.focused, [name]); assert.deepEqual(e.confirmations, []);
        assert.equal(e.page[name].value, value);
      });
    }
    test(`${form.file} handles a missing ${name} control without an uncaught error`, () => {
      const e = editor(form); delete e.page[name];
      assert.equal(e.context.onSave(), false);
      assert.equal(e.alerts.length, 1); assert.deepEqual(e.confirmations, []);
    });
  }

  for (const accepted of [true, false]) test(`${form.file} Save and Exit honours its one confirmation: ${accepted}`, () => {
    const e = editor(form, Object.fromEntries(form.dates.map(name => [name, '30/09/2026'])), accepted);
    assert.equal(e.context.onSaveExit(), accepted);
    assert.equal(e.page.submit.value, 'exit'); assert.equal(e.confirmations.length, 1);
    assert.deepEqual(e.alerts, []);
  });

  test(`${form.file} invalid date prevents Save and Exit confirmation`, () => {
    const name = form.dates.at(-1), e = editor(form, {[name]: '31/02/2026'});
    assert.equal(e.context.onSaveExit(), false);
    assert.deepEqual(e.confirmations, []); assert.deepEqual(e.focused, [name]);
  });

  test(`${form.file} restores its saved form action after PDF preparation`, () => {
    const e = editor(form);
    assert.equal(e.context.onPrintPDF(), true); assert.notEqual(e.page.action, '/carlos/form/formname');
    assert.equal(e.context.onSave(), true);
    assert.equal(e.page.action, '/carlos/form/formname'); assert.equal(e.page.submit.value, 'save');
  });
}

for (const file of ['formmhreferral.jsp', 'formmhassessment.jsp']) {
  for (const target of ['', 'named-form-window']) test(`${file} captures the actual form target on load: ${target || '(self)'}`, () => {
    const jsp = fs.readFileSync(path.join(directory, file), 'utf8');
    const source = /function getMainAction\(\)\s*\{[^}]*\}/.exec(jsp)?.[0];
    assert(source, 'The real onload handler must remain present');
    const context = {mainAction: '', mainTarget: '', document: {forms: [{action: '/carlos/form/formname', target}]}};
    vm.createContext(context); vm.runInContext(source, context); context.getMainAction();
    assert.equal(context.mainAction, '/carlos/form/formname'); assert.equal(context.mainTarget, target);
  });
}

function chainEditor(file, {valid, accepted = true, failSubmit = false} = {}) {
  const jsp = fs.readFileSync(path.join(directory, file), 'utf8')
    .replace(/<%=[\s\S]*?%>/g, '/carlos').replace(/<fmt:message\b[^>]*\/>/g, 'Localized confirmation');
  const functions = ['getMainAction', 'onPrint', 'onSave', 'onSaveExit'].map(name => {
    const start = jsp.indexOf(`function ${name}()`);
    const end = jsp.indexOf('\n            function ', start + 1);
    assert(start >= 0 && end > start, `The real ${name} handler must be present`);
    return jsp.slice(start, end);
  }).join('\n');
  const page = {action: '/carlos/form/formname', target: 'owned-form-window', submit: {value: 'save'},
    prose: {value: 'unsaved clinical details'}};
  const submitted = [], popups = [], confirmations = [];
  let validations = 0;
  const context = {mainAction: '', mainTarget: '', document: {forms: [page]},
    numvalidate() {validations++; return valid;},
    confirm(message) {confirmations.push(message); return accepted;},
    popupFixedPage(...args) {popups.push(args);},
    window: {opener: {location: {reload() {}}}},
    HTMLFormElement: {prototype: {submit() {
      submitted.push({action: this.action, target: this.target, intent: this.submit.value, prose: this.prose.value});
      if (failSubmit) throw new Error('native submit failed');
    }}},
  };
  vm.createContext(context); vm.runInContext(functions, context); context.getMainAction();
  return {context, page, submitted, popups, confirmations, validations: () => validations};
}

for (const [file, route] of [['formmhreferral.jsp', '/form/formmhreferralprint'],
  ['formmhassessment.jsp', '/form/formmhassessmentprint']]) {
  for (const valid of [undefined, true]) test(`${file} Print submits natively despite a hidden submit input: ${valid}`, () => {
    const e = chainEditor(file, {valid});
    const before = JSON.stringify(e.page);
    assert.equal(e.context.onPrint(), false);
    assert.deepEqual(e.submitted, [{action: '/carlos' + route, target: 'planner', intent: 'print', prose: 'unsaved clinical details'}]);
    assert.equal(e.popups.length, 1); assert.equal(e.validations(), 1);
    assert.deepEqual(e.confirmations, []); assert.equal(JSON.stringify(e.page), before);
  });

  test(`${file} invalid numeric input prevents every print side effect`, () => {
    const e = chainEditor(file, {valid: false}), before = JSON.stringify(e.page);
    assert.equal(e.context.onPrint(), false);
    assert.deepEqual(e.submitted, []); assert.deepEqual(e.popups, []);
    assert.deepEqual(e.confirmations, []); assert.equal(JSON.stringify(e.page), before);
  });

  test(`${file} restores exact form state when native print submission throws`, () => {
    const e = chainEditor(file, {failSubmit: true}), before = JSON.stringify(e.page);
    assert.throws(() => e.context.onPrint(), /native submit failed/);
    assert.equal(JSON.stringify(e.page), before); assert.equal(e.submitted.length, 1);
  });

  test(`${file} Save after Print uses its original save route and target`, () => {
    const e = chainEditor(file);
    e.context.onPrint(); assert.equal(e.context.onSave(), true);
    assert.equal(e.page.action, '/carlos/form/formname'); assert.equal(e.page.target, 'owned-form-window');
    assert.equal(e.page.submit.value, 'save'); assert.equal(e.confirmations.length, 1);
  });
}

for (const valid of [undefined, true]) {
  for (const accepted of [false, true]) test(`Assessment Save and Exit honours validation and one confirmation: ${valid}/${accepted}`, () => {
    const e = chainEditor('formmhassessment.jsp', {valid, accepted});
    e.page.action = '/previous-print'; e.page.target = 'previous-target';
    const before = JSON.stringify(e.page);
    assert.equal(e.context.onSaveExit(), accepted);
    assert.equal(e.confirmations.length, 1); assert.equal(e.validations(), 1);
    if (accepted) {
      assert.equal(e.page.action, '/carlos/form/formname'); assert.equal(e.page.target, 'owned-form-window');
      assert.equal(e.page.submit.value, 'exit'); assert.equal(e.page.prose.value, 'unsaved clinical details');
    } else assert.equal(JSON.stringify(e.page), before);
    assert.deepEqual(e.submitted, []);
  });
}

test('Assessment invalid numeric input prevents Save and Exit confirmation or state changes', () => {
  const e = chainEditor('formmhassessment.jsp', {valid: false}), before = JSON.stringify(e.page);
  assert.equal(e.context.onSaveExit(), false);
  assert.deepEqual(e.confirmations, []); assert.deepEqual(e.submitted, []);
  assert.equal(JSON.stringify(e.page), before);
});

for (const selected of [false, true]) test(`native button regression is asserted by the selected script concern: ${selected}`, async () => {
  const source = fs.readFileSync(path.join(__dirname, 'clinical-forms-save-reopen-playwright-checks.js'), 'utf8');
  const start = source.indexOf('  async function mentalHealthChain(entry) {');
  const end = source.indexOf('  async function mentalHealthFormState(page) {', start);
  assert(start >= 0 && end > start);
  const claims = require('./lib/form-claims');
  const failure = new Error('native print did not reach its real handler');
  let prints = 0;
  const context = {claims, wanted: new Set(selected ? ['mhc.problems'] : []),
    s: {recorder: {}}, chart: {on() {}, off() {}}, markProblems() {return {};},
    async printMentalHealthPage() {prints++; throw failure;},
    async attempt(entry, concern, _label, body) {
      if (concern === 'chain') {
        try {await body();} catch (error) {entry.chainFailure = error;}
      }
    },
  };
  vm.createContext(context); vm.runInContext(source.slice(start, end), context);
  const entry = {form: {claim: 'mhc', prose: 'r_refComments', chain: []}, page: {}, before: 0, text: 'owned prose'};
  await context.mentalHealthChain(entry);
  assert.equal(prints, selected ? 1 : 0);
  if (selected) {
    assert.equal(entry.nativeHandlerError, failure);
    const statement = /    const outcome = entry\.nativeHandlerError[\s\S]*?;\n/.exec(source)?.[0];
    assert(statement, 'The final script concern must retain native handler failures');
    context.entry = entry; context.problems = [];
    vm.runInContext(statement.replace('const outcome =', 'globalThis.actualOutcome ='), context);
    assert.equal(context.actualOutcome.ok, false);
    assert(context.actualOutcome.message.includes(failure.message));
  } else assert.equal(entry.nativeHandlerError, undefined);
});
