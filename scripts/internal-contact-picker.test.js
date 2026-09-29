/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/demographic/demographicsearch2apptresults.jsp'), 'utf8');
const names = {formName: 'contactForm', elementName: 'contactName', elementId: 'contactId'};
const source = jsp.match(/function addNameCaisi\([\s\S]*?self\.close\(\);\s*\}/)[0]
  .replace(/<carlos:encode value='<%= StringUtils.noNull\(request.getParameter\("(formName|elementName|elementId)"\)\) %>' context="javaScriptBlock"\/>/g,
    (_, key) => names[key]);
for (const [last, first, expected] of [
  ['O%27Neil', 'Anne%20Marie', "O'Neil,Anne Marie"],
  ['A%26B', 'Fran%C3%A7ois', 'A&B,François'],
  ['Percent%2520', 'Plain', 'Percent%20,Plain'],
]) {
  test(`patient picker returns decoded display text for ${last}`, () => {
    const elements = {contactName: {value: ''}, contactId: {value: ''}};
    let closed = false;
    const context = {opener: {document: {contactForm: {elements}}}, self: {close() {closed = true;}}};
    vm.runInNewContext(source, context);
    context.addNameCaisi('123', last, first, '', '');
    assert.equal(elements.contactName.value, expected);
    assert.equal(elements.contactId.value, '123');
    assert.equal(closed, true);
  });
}

test('clicking a patient row works without a demographic field on the search form', () => {
  const line = jsp.split('\n').find(value => value.includes('onClick=') && value.includes('__enc_1'));
  const handler = line.trim().slice('onClick="'.length, -2)
    .replace(/<% if\(caisi\)[\s\S]*?%>/g, 'addNameCaisi')
    .replace(/<%=demo.getDemographicNo\(\)%>/g, '123')
    .replace(/<carlos:encode [\s\S]*?\/>/g, tag => tag.includes('__enc_1') ? 'O%27Neil' : 'Anne');
  assert(!handler.includes('<%') && !handler.includes('<carlos:'), 'JSP fixture contains an unresolved expression');
  let selected;
  vm.runInNewContext(handler, {document: {forms: [{}]}, addNameCaisi: (...args) => {selected = args;}});
  assert.equal(selected[0], '123');
  assert.equal(selected[1], 'O%27Neil');
});
