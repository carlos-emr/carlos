/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const check = require('./tickler-validation-messages-playwright-checks');
const root = path.join(__dirname, '..');
const jspDir = path.join(root, 'src/main/webapp/WEB-INF/jsp/tickler');
const pages = ['ticklerAdd.jsp', 'ticklerEdit.jsp'].map(name => ({ name, source: fs.readFileSync(path.join(jspDir, name), 'utf8') }));
const shared = fs.readFileSync(path.join(root, 'src/main/webapp/share/javascript/tickler-validation.js'), 'utf8');
const bundle = fs.readFileSync(path.join(root, 'src/main/resources/oscarResources_en.properties'), 'utf8');
function functionSource(source, name, multisite) {
  // Function names are fixed test entry points, never regular-expression input.
  const patterns = {
    validate: /(^[ \t]*)function validate\([^)]*\) \{[\s\S]*?^\1\}/m,
    validateDate: /(^[ \t]*)function validateDate\([^)]*\) \{[\s\S]*?^\1\}/m,
    validateDemoNo: /(^[ \t]*)function validateDemoNo\([^)]*\) \{[\s\S]*?^\1\}/m,
  };
  assert.ok(Object.hasOwn(patterns, name), 'Unsupported test entry point');
  const match = source.match(patterns[name]);
  assert.ok(match, `Missing ${name}`);
  return match[0]
    .replace(/<%--[\s\S]*?--%>/g, '')
    .replace(/<% if \(io\.github\.carlos_emr\.carlos\.commn\.IsPropertiesOn\.isMultisitesEnable\(\)\) \{ %>([\s\S]*?)<% } %>/g, (_, body) => multisite ? body : '')
    .replace(/<carlos:encode value='<%= oscarBundle\.getString\("([^"]+)"\) %>' context="javaScriptBlock"\/>/g, (_, key) => key)
    .replace(/<carlos:encode\b[\s\S]*?\/>/g, 'fixture')
    .replace(/<%=[\s\S]*?%>/g, 'fixture');
}
function setup(page, multisite = false) {
  const error = { children: [], style: { display: 'none' }, appendChild(line) { this.children.push(line); } };
  Object.defineProperty(error, 'textContent', { get() { return this.children.map(c => c.textContent).join(''); }, set(value) { assert.equal(value, ''); this.children = []; } });
  const form = { demographic_no: { value: '1' }, xml_appointment_date: { value: '' }, task_assigned_to: { options: [{}], value: '999998' } };
  const context = vm.createContext({ document: { serviceform: form, getElementById: id => id === 'error' ? error : null,
    createElement: tag => { assert.equal(tag, 'div'); return { textContent: '', className: '' }; } },
  IsDate: value => value === '2026-03-04' });
  vm.runInContext(shared, context);
  const fieldValidator = page.name === 'ticklerAdd.jsp' ? 'validateDemoNo' : 'validateDate';
  vm.runInContext(functionSource(page.source, fieldValidator, multisite) + '\n' + functionSource(page.source, 'validate', multisite), context);
  return { context, error, form, messages: () => error.children.map(c => c.textContent) };
}
for (const page of pages) {
  test(`${page.name} resets duplicate messages on each actual validation attempt`, () => {
    const s = setup(page);
    for (let attempt = 0; attempt < 3; attempt += 1) {
      s.context.validate(s.form);
      assert.deepEqual(s.messages(), ['tickler.ticklerAdd.msgMissingDate']);
      assert.equal(s.error.style.display, 'block');
    }
  });
  test(`${page.name} loads the common alert renderer and has no obsolete program-control dependency`, () => {
    assert.ok(page.source.includes('/share/javascript/tickler-validation.js'));
    assert.doesNotMatch(page.source, /program_assigned_to|validateSelectedProgram|caisiEnabled/);
    assert.doesNotMatch(page.source, /function (resetValidationMessages|showValidationMessage)/);
    assert.match(page.source, /id="error"[^>]*role="alert"/);
  });
}
test('add validation reports all current failures and drops corrected failures on the next attempt', () => {
  const s = setup(pages[0], true);
  s.form.demographic_no.value = '';
  delete s.form.task_assigned_to;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    s.context.validate(s.form);
    assert.deepEqual(s.messages(), ['tickler.ticklerAdd.msgInvalidDemographic', 'tickler.ticklerAdd.msgMissingDate', 'tickler.ticklerAdd.msgMustAssignProvider']);
  }
  s.form.demographic_no.value = '1';
  s.context.validate(s.form);
  assert.deepEqual(s.messages(), ['tickler.ticklerAdd.msgMissingDate', 'tickler.ticklerAdd.msgMustAssignProvider']);
  s.form.xml_appointment_date.value = '2026-03-04';
  s.context.validate(s.form);
  assert.deepEqual(s.messages(), ['tickler.ticklerAdd.msgMustAssignProvider']);
});
for(const multisite of [false,true])test(`Add Tickler refuses an unselected assignee with guidance (multisite=${multisite})`,()=>{
  const s=setup(pages[0],multisite);
  s.form.xml_appointment_date.value='2026-03-04';
  s.form.task_assigned_to.value='';
  assert.equal(s.context.validateDemoNo(),false);
  assert.deepEqual(s.messages(),['tickler.ticklerAdd.msgMustAssignProvider']);
  s.context.CarlosTicklerValidation.reset();
  s.form.task_assigned_to.value='999998';
  assert.equal(s.context.validateDemoNo(),true);
  assert.deepEqual(s.messages(),[]);
});
test('native patient search carries the current Tickler draft through repeated attachment and patient changes',()=>{
  const searchMatch=pages[0].source.match(/<form name="ADDAPPT"[\s\S]*?<\/form>/);
  assert.ok(searchMatch,'Native Tickler patient search must contain the ADDAPPT form');
  const search=searchMatch[0];
  const submitMatch=search.match(/onsubmit="([^"]+)"/);
  assert.ok(submitMatch,'Native Tickler patient search must carry the draft in its submit handler');
  const submit=submitMatch[1];
  assert.match(search,/<input type="hidden" name="ticklerMessage"/);
  const carried={value:'old draft'},message={value:''};
  const form={elements:{namedItem(name){assert.equal(name,'ticklerMessage');return carried;}}};
  const submitDraft=vm.runInNewContext('(function(){'+submit+'})',{document:{getElementById(id){assert.equal(id,'ticklerMessage');return message;}}});
  for(const draft of ['Unsaved H&P + "quotes" <tag>\nsecond line','Changed patient: é & %26','']){
    message.value=draft;submitDraft.call(form);assert.equal(carried.value,draft);
  }
});
test('shared rendering keeps hostile message text inert and resetting hides and empties the alert', () => {
  const s = setup(pages[0]);
  const message = '<img src=x onerror="bad()"> & </script>';
  s.context.CarlosTicklerValidation.show(message);
  assert.deepEqual(s.messages(), [message]);
  assert.equal(s.error.children[0].className, 'tickler-validation-message');
  s.context.CarlosTicklerValidation.reset();
  assert.deepEqual(s.messages(), []);
  assert.equal(s.error.style.display, 'none');
});
test('all validation messages are encoded bundle strings and browser selectors match the rendered controls', () => {
  for (const page of pages) {
    const calls = [...page.source.matchAll(/CarlosTicklerValidation\.show\(([^\n]*)\);/g)];
    assert.ok(calls.length >= 1);
    for (const [, argument] of calls) {
      const match = argument.match(/^'<carlos:encode value='<%= oscarBundle\.getString\("(tickler\.tickler(?:Add\.[A-Za-z]+|Edit\.attachments\.error))"\) %>' context="javaScriptBlock"\/>'$/);
      assert.ok(match, 'Validation text must pass through the CARLOS JavaScript encoder');
      assert.ok(bundle.includes(match[1] + '='));
    }
    assert.ok(page.source.includes('name="xml_appointment_date"'));
  }
  assert.equal(check.SELECTORS.messageLine, '#error .tickler-validation-message');
  assert.equal(check.bundleMessage(check.MISSING_DATE_KEY, ''), 'Missing service date, please verify!');
  assert.ok(pages[0].source.includes('name="Button" class="btn btn-primary"'));
  assert.ok(pages[1].source.includes('name="updateTickler"'));
  assert.ok(fs.readFileSync(path.join(jspDir, 'dbTicklerAdd.jsp'), 'utf8').includes('id="tickler-save-ok"'));
  assert.ok(fs.readFileSync(path.join(jspDir, 'ticklerEditSuccess.jsp'), 'utf8').includes('tickler-edit-ok'));
});
