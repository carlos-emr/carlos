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
