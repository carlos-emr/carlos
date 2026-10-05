/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/documentManager/editDocument.jsp'), 'utf8');
const start = jsp.indexOf('function reviewed(ths)');
const end = jsp.indexOf('var docSubClassList', start);
assert.ok(start >= 0 && end > start, 'The document Reviewed handler is missing');
const source = jsp.slice(start, end).replaceAll('<%=user_no%>', '999998');
function review(reviewer) {
  let submits = 0;
  const dialogs = [];
  const form = {reviewerId:{value:reviewer}, reviewDoc:{value:'false'}, extraReviewerId:{value:''},
    extraReviewDoc:{value:'false'}, submit() {submits++;}};
  const context = vm.createContext({alert:message => dialogs.push(message)});
  vm.runInContext(source, context);
  context.reviewed({form});
  assert.equal(submits, 1);
  assert.deepEqual(dialogs, [], 'Reviewed must not show a debug prompt');
  return form;
}
for (const value of ['', 'null']) {
  test(`first review asks the server to assign the provider when the rendered reviewer is ${JSON.stringify(value)}`, () => {
    const form = review(value);
    assert.equal(form.reviewerId.value, '');
    assert.equal(String(form.reviewDoc.value), 'true');
    assert.equal(form.extraReviewerId.value, '');
    assert.equal(form.extraReviewDoc.value, 'false');
  });
}
test('another review retains the primary reviewer and submits an extra reviewer', () => {
  const form = review('999997');
  assert.equal(form.reviewerId.value, '999997');
  assert.equal(form.reviewDoc.value, 'false');
  assert.equal(String(form.extraReviewerId.value), '999998');
  assert.equal(String(form.extraReviewDoc.value), 'true');
});
