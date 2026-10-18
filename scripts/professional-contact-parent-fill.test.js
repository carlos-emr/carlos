/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/demographic/addEditProfessionalContact.jsp'), 'utf8');

// Run the page's own fillParentFields, replacing only its three server-rendered request values (#4987).
function fillParentFields(rendered, opener, contactId, contactName) {
  const match = jsp.match(/function fillParentFields\(contactId, contactName\) \{([\s\S]*?)^ {12}\}/m);
  assert.ok(match, 'fillParentFields was not found');
  const values = [rendered.form, rendered.id, rendered.name];
  const body = match[1].replace(/'\$\{carlos:forJavaScript\(param\.\w+\)\}'/g, () => JSON.stringify(values.shift()));
  assert.equal(values.length, 0, 'the three parent field names must be supplied');
  return vm.runInNewContext(`(function (contactId, contactName) {${body}})(contactId, contactName)`,
    { opener, contactId, contactName }, { timeout: 1000 });
}

const openerWith = () => {
  const elements = { 'procontact_1.contactId': {}, 'procontact_1.contactName': {} };
  return { elements, opener: { document: { forms: { contactForm: { elements } } } } };
};

test('a saved contact is put in the id and name fields of the row the search was opened for', () => {
  const { elements, opener } = openerWith();
  const filled = fillParentFields({ form: 'contactForm', id: 'procontact_1.contactId', name: 'procontact_1.contactName' },
    opener, '42', 'O\'Neil,External');
  assert.equal(filled, true);
  assert.equal(elements['procontact_1.contactId'].value, '42');
  assert.equal(elements['procontact_1.contactName'].value, 'O\'Neil,External');
});

test('nothing is filled when the opener named no row', () => {
  const { elements, opener } = openerWith();
  for (const missing of ['form', 'id', 'name']) {
    const rendered = { form: 'contactForm', id: 'procontact_1.contactId', name: 'procontact_1.contactName', [missing]: '' };
    assert.equal(fillParentFields(rendered, opener, '42', 'x'), false);
  }
  assert.equal(elements['procontact_1.contactId'].value, undefined);
});

test('the form prefers the Health Care Team hand-back when the opener offers one', () => {
  const forward = jsp.match(/function forwardToParent\(\) \{[\s\S]*?^ {12}\}/m)[0];
  assert.ok(forward.indexOf("typeof opener.popUpData === 'function'") < forward.indexOf('fillParentFields('));
});
