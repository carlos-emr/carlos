/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
'use strict';
// Runs the HTML document editor's Reviewed handler (documentManager/addedithtmldocument.jsp) as the
// page ships it. 4f6f848e7ca3 (#6135) made it validate and confirm before submitting and drop the
// review intent when either step declines, as editDocument.jsp's handler does
// (document-reviewer.test.js). Before #6336 only its source text was read (#6336 Task 10).
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/documentManager/addedithtmldocument.jsp'), 'utf8');
const start = jsp.indexOf('function reviewed(ths)');
const end = jsp.indexOf('var docSubClassList', start);
assert.ok(start >= 0 && end > start, 'The HTML document Reviewed handler is missing');
const source = jsp.slice(start, end);

/** Clicks Reviewed on a fake form; submitUpload stands for the page's own validate-and-confirm step. */
function clickReviewed({uploadAccepts, formValid}) {
  const calls = [];
  const form = {
    reviewDoc: {value: 'false'},
    reportValidity() { calls.push('reportValidity'); return formValid; },
    submit() { calls.push('submit:' + this.reviewDoc.value); }
  };
  const context = vm.createContext({
    submitUpload(target) { calls.push('submitUpload'); assert.equal(target, form); return uploadAccepts; }
  });
  vm.runInContext(source, context);
  context.reviewed({form});
  return {form, calls};
}

test('Reviewed submits the review once the form validates and the clinician confirms', () => {
  const {form, calls} = clickReviewed({uploadAccepts: true, formValid: true});
  assert.deepEqual(calls, ['submitUpload', 'reportValidity', 'submit:true']);
  assert.equal(String(form.reviewDoc.value), 'true');
});

test('Reviewed sends nothing and clears the review intent when the confirm or a field check declines', () => {
  const {form, calls} = clickReviewed({uploadAccepts: false, formValid: true});
  assert.deepEqual(calls, ['submitUpload']);
  assert.equal(form.reviewDoc.value, false);
});

test('Reviewed sends nothing and clears the review intent when the browser finds an invalid field', () => {
  const {form, calls} = clickReviewed({uploadAccepts: true, formValid: false});
  assert.deepEqual(calls, ['submitUpload', 'reportValidity']);
  assert.equal(form.reviewDoc.value, false);
});
