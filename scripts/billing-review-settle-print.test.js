/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingONReview.jsp'), 'utf8');
const button = source.match(/<input\s+type="([^"]+)"\s+id="settlePrintBtn"[\s\S]*?onclick="([^"]*)"/);
assert.ok(button, 'The rendered review template must expose the settle/print control');

test('Settle & Print uses native submission through the form validation hook', () => {
  assert.equal(button[1], 'submit');
  assert.match(source, /<form\b[^>]*name="titlesearch"[\s\S]*?onsubmit="return onSave\(\);"/);
});

test('clicking Settle & Print works when a request field shadows form.submit', () => {
  const billingAction = { value: '' };
  const form = { submit: { name: 'submit', value: 'Next' } };
  vm.runInNewContext(button[2], {
    document: { forms: { titlesearch: form }, getElementById: id => {
      assert.equal(id, 'billingAction');
      return billingAction;
    } },
    popupPage: () => assert.fail('The click must not open an invoice before the save succeeds'),
  });
  assert.equal(billingAction.value, 'SETTLE_PRINT');
  assert.equal(form.submit.value, 'Next');
});
