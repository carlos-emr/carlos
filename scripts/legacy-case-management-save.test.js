/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/casemgmt/CaseManagementEntry.jsp'), 'utf8');
const start = source.indexOf('        function validateIssuecheck(');
const end = source.indexOf('        function toggleGroupNote(', start);
assert.ok(start >= 0 && end > start, 'legacy issue and Save validation functions exist');

function legacy(fields) {
  const elements = [...fields];
  elements['caseNote.encounter_type'] = { value: 'face to face encounter with client' };
  const form = { elements, sign: { checked: false }, method: { value: 'edit' } };
  const alerts = []; const confirmations = [];
  const context = {
    document: { caseManagementEntryForm: form },
    alert: message => alerts.push(message),
    confirm: message => { confirmations.push(message); return true; },
  };
  vm.createContext(context);
  vm.runInContext(source.slice(start, end), context);
  return { context, form, alerts, confirmations };
}

for (const [label, fields, expected] of [
  ['a checked issue', [{ type: 'checkbox', name: 'issueCheckList[0].checked', checked: true }], true],
  ['no checked issue', [{ type: 'checkbox', name: 'issueCheckList[0].checked', checked: false }], false],
  ['an unrelated Sign checkbox', [{ type: 'checkbox', name: 'sign', checked: true }], false],
  ['a noncontiguous rendered issue', [{ type: 'checkbox', name: 'issueCheckList[17].checked', checked: true }], true],
  ['a disabled selected association', [{ type: 'checkbox', name: 'issueCheckList[9].checked', checked: true, disabled: true }], true],
  ['a different field with an issue-like name', [{ type: 'text', name: 'issueCheckList[0].checked', checked: true },
    { type: 'checkbox', name: 'prefixissueCheckList[0].checked', checked: true }], false],
]) {
  test(`legacy issue validation reads actual controls with ${label}`, () => {
    const h = legacy(fields);
    assert.equal(h.context.validateIssuecheck(), expected);
  });
}

for (const [buttonLabel, method] of [['Save', 'save'], ['Save and Exit', 'saveAndExit']]) {
  const button = new RegExp(`value="${buttonLabel}"[\\s\\S]*?onclick="([^"]+)"`).exec(source);
  assert.ok(button, `native ${buttonLabel} handler exists`);
  // Render the old broken Java counter as its actual zero value if reintroduced:
  // the native handlers must still validate the live checked controls.
  const handler = button[1].replace(/<%=count_issues_display%>/g, '0');
  for (const checked of [true, false]) {
    test(`native legacy ${buttonLabel} ${checked ? 'accepts' : 'refuses'} its actual issue selection`, () => {
      const h = legacy([{ type: 'checkbox', name: 'issueCheckList[17].checked', checked }]);
      const click = vm.runInContext(`(function () { ${handler} })`, h.context);
      assert.equal(click.call({ form: h.form }), checked);
      assert.equal(h.form.method.value, method);
      if (checked) {
        assert.deepEqual(h.alerts, []);
        assert.deepEqual(h.confirmations, ['Are you sure that you want to save without signing?']);
      } else {
        assert.deepEqual(h.alerts,
          ['You cannot save a note when there is no issue checked, please add an issue or check a currently available issue before save.']);
        assert.deepEqual(h.confirmations, []);
      }
    });
  }
}
