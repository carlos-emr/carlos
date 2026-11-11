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
const source = jsp.slice(start, end);
test('the Reviewed handler does not embed the signed-in provider number', () => {
  // The action records the session provider; the page must not supply a reviewer of its own.
  assert.doesNotMatch(source, /user_no|extraReviewerId|extraReviewDoc/);
});
function review(reviewer) {
  let submits = 0;
  const dialogs = [];
  const form = {reviewerId:{value:reviewer}, reviewDoc:{value:'false'}, reportValidity() {return true;}, submit() {submits++;}};
  const context = vm.createContext({alert:message => dialogs.push(message), submitUpload: () => true});
  vm.runInContext(source, context);
  context.reviewed({form});
  assert.equal(submits, 1);
  assert.deepEqual(dialogs, [], 'Reviewed must not show a debug prompt');
  return form;
}
// Issue #6081: the reviewer rendered with the page can be out of date (another user's Update
// clears it, another provider reviews first), so the page never decides first versus additional
// review. It asks for a review and the action decides from the stored document.
for (const value of ['', 'null', '999997']) {
  test(`Reviewed asks the server for a review whatever reviewer was rendered (${JSON.stringify(value)})`, () => {
    const form = review(value);
    assert.equal(String(form.reviewDoc.value), 'true');
    assert.equal(form.reviewerId.value, value, 'the rendered reviewer is left as loaded and is not trusted');
    assert.deepEqual(Object.keys(form).sort(), ['reportValidity', 'reviewDoc', 'reviewerId', 'submit']);
  });
}
test('the edit form no longer posts client-chosen extra-review fields', () => {
  assert.doesNotMatch(jsp, /name="extraReviewerId"|name="extraReviewDoc"/);
});

test('canceled validation clears review intent and submits nothing', () => {
  const form = {reviewDoc:{value:false}, reportValidity() {return true;}, submit() {assert.fail('must not submit');}};
  const context = vm.createContext({submitUpload: () => false});
  vm.runInContext(source, context); context.reviewed({form});
  assert.equal(form.reviewDoc.value, false);
});
