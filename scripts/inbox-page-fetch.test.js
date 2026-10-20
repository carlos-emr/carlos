/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/web/inboxhub/InboxhubForm.jsp'), 'utf8');
const fetchStart = jsp.indexOf('    function fetchInboxhubListData()');
const fetchEnd = jsp.indexOf('    function addDataInInboxhubListTable(data)', fetchStart);
assert(fetchStart >= 0 && fetchEnd > fetchStart);
const source = jsp.slice(fetchStart, fetchEnd).replace(/<carlos:encode[^>]*\/>/g, '/carlos');
const loadStart = jsp.indexOf('    function loadMoreListData()');
const loadEnd = jsp.indexOf('    function resetDataPageCount()', loadStart);

function fixture(mode) {
  const requests = [];
  const added = [];
  const controls = new Map();
  let errors = 0;
  function jQuery(selector) {
    return {
      prop(name, value) { controls.set(selector + ':' + name, value); return this; },
      hide() { controls.set(selector + ':hidden', true); },
    };
  }
  jQuery.ajax = options => {
    const xhr = { abort() { options.error(xhr, 'abort'); } };
    requests.push({ options, xhr });
    return xhr;
  };
  const context = vm.createContext({
    jQuery, hasMoreData: true, isFetchingData: false, currentFetchRequest: null,
    inboxhubResultSetGeneration: 1, page: 2, pageSize: 20,
    inboxSearchFormData: 'status=N', filter: '',
    ShowSpin() {}, HideSpin() {}, toastErrorMessage() { errors++; },
    addDataInInboxhubViewTable(data) { added.push(data); },
    addDataInInboxhubListTable(data) { added.push(data); },
    document: { getElementById() { return { value: mode === 'preview' ? 'true' : 'false' }; } },
    bootstrap: { Toast: { getOrCreateInstance() { return { hide() {} }; } } },
  });
  vm.runInContext(source + jsp.slice(loadStart, loadEnd), context);
  const fetch = () => context[mode === 'preview' ? 'fetchInboxhubViewData' : 'fetchInboxhubListData']();
  const success = (index, data) => requests[index].options.success(data, 'success', requests[index].xhr);
  const fail = (index, status = 'error') => requests[index].options.error(requests[index].xhr, status);
  return { context, requests, controls, added, fetch, success, fail, errors: () => errors };
}

for (const mode of ['list', 'preview']) {
  test(mode + ' retries the failed page and blocks overlapping requests', () => {
    const inbox = fixture(mode);
    inbox.fetch();
    inbox.fetch();
    assert.equal(inbox.requests.length, 1);
    inbox.fail(0);
    assert.equal(inbox.context.isFetchingData, false);
    assert.equal(inbox.context.currentFetchRequest, null);
    assert.equal(inbox.context.page, 2);
    assert.equal(inbox.errors(), 1);
    assert.equal(inbox.controls.get('#inboxhubFormSearchBtn:disabled'), false);
    inbox.context.retryInboxhubPage();
    inbox.context.retryInboxhubPage();
    assert.equal(inbox.requests.length, 2);
    assert.equal(inbox.requests[1].options.data, inbox.requests[0].options.data);
    inbox.success(1, 'retried-page');
    assert.deepEqual(inbox.added, ['retried-page']);
    assert.equal(inbox.context.page, 3);
  });

  test(mode + ' ignores callbacks from an abandoned result set', () => {
    const inbox = fixture(mode);
    inbox.fetch();
    inbox.context.inboxhubResultSetGeneration++;
    inbox.context.isFetchingData = false;
    inbox.context.currentFetchRequest = null;
    inbox.context.page = 1;
    inbox.fetch();
    const current = inbox.context.currentFetchRequest;
    inbox.success(0, 'stale-results');
    inbox.fail(0);
    assert.deepEqual(inbox.added, []);
    assert.equal(inbox.context.isFetchingData, true);
    assert.equal(inbox.context.currentFetchRequest, current);
    assert.equal(inbox.context.page, 1);
    assert.equal(inbox.errors(), 0);
  });

  test(mode + ' ignores late callbacks from a replaced request in the same result set', () => {
    const inbox = fixture(mode);
    inbox.fetch();
    inbox.context.currentFetchRequest.abort();
    assert.equal(inbox.errors(), 0);
    inbox.fetch();
    const current = inbox.context.currentFetchRequest;
    inbox.success(0, 'aborted-results');
    inbox.fail(0);
    assert.deepEqual(inbox.added, []);
    assert.equal(inbox.context.currentFetchRequest, current);
    assert.equal(inbox.context.isFetchingData, true);
    assert.equal(inbox.context.page, 2);
    assert.equal(inbox.errors(), 0);
  });
}
