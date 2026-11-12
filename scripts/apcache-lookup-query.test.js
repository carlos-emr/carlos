/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// APCache.js's interactive lookup query: the patient it sends, run in a VM with jQuery.ajax captured.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

const SOURCE = fs.readFileSync(path.join(__dirname, '../src/main/webapp/library/eforms/APCache.js'), 'utf8');

/** One lookup of `key` on a page with this query string and this `demographicNo` global; returns the data sent. */
function lookupData({ search, demographicNo }) {
  const sent = [];
  const context = {
    window: { location: { search } },
    document: { getElementById: () => null },
    jQuery: { ajax: (options) => { sent.push(options); } },
  };
  if (demographicNo !== undefined) context.demographicNo = demographicNo;
  vm.createContext(context);
  vm.runInContext(SOURCE, context);
  vm.runInContext("createCache({}).lookup('today');", context);
  assert.equal(sent.length, 1, 'the lookup sent no request');
  return sent[0].data;
}

test('an eForm view with a demographicNo element sends the element\'s value, not the element', () => {
  // The eForm views add <input type="hidden" id="demographicNo">, which the window's named access returns as the
  // global demographicNo when no variable is declared.
  const data = lookupData({ search: '?fdid=12', demographicNo: { value: '123', nodeName: 'INPUT' } });
  assert.match(data, /&demographic_no=123(&|$)/);
  assert.doesNotMatch(data, /object|HTMLInputElement/);
});

test('a page variable demographicNo is sent as it is', () => {
  assert.match(lookupData({ search: '?fid=7', demographicNo: '456' }), /&demographic_no=456(&|$)/);
});

test('a page whose query string carries demographic_no sends no second one', () => {
  const data = lookupData({ search: '?efmfid=7&efmdemographic_no=123', demographicNo: { value: '123' } });
  assert.equal((data.match(/demographic_no=/g) || []).length, 1, data);
});

test('a page with no demographicNo at all sends none, and does not fail', () => {
  assert.doesNotMatch(lookupData({ search: '?fid=7' }), /&demographic_no=/);
});
