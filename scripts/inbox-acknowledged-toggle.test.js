/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The toolbar's Acknowledged toggle must not ask for a provider's whole acknowledged history.
 *
 * List mode chains loadMoreListData() until the server returns an empty page, so whatever the
 * toggle asks for is loaded in full, a page at a time, with the DataTable redrawn after every
 * page. When the toggle only flipped query.status to A, that was every result the provider had
 * ever acknowledged: an alpha tester's inbox sat "loading" with a progress bar frozen against
 * the New totals until they pressed Stop. The toggle asks for what was acknowledged TODAY
 * instead (the server bounds it by acknowledgement time, so a lab acknowledged today keeps
 * showing however old its own date is), and every page of that list has to keep asking for it.
 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/web/inboxhub/InboxhubForm.jsp'), 'utf8');

/** One named function from the page, delimited by brace matching. */
function extractFunction(name) {
  const start = jsp.indexOf('    function ' + name + '(');
  assert.ok(start >= 0, name + ' not found in InboxhubForm.jsp');
  let depth = 0;
  for (let at = jsp.indexOf('{', start); at < jsp.length; at++) {
    if (jsp[at] === '{') depth++;
    if (jsp[at] === '}' && --depth === 0) return jsp.slice(start, at + 1);
  }
  throw new Error('unbalanced braces reading ' + name);
}

// The request path exactly as the page ships it. The list URL carries a JSP encoder tag,
// which is not JavaScript; the fixture checks only the request's data, not its address.
const source = ['toggleAcknowledged', 'fetchInboxhubData', 'syncAcknowledgedTodayField', 'fetchInboxhubListData',
  'loadMoreListData', 'ownsInboxhubPageFetch', 'settleInboxhubPageFetch']
  .map(extractFunction).join('\n').replace(/<carlos:encode[^>]*\/>/g, '/carlos');

/** The onclick handler the search panel's review-status radio carries, exactly as rendered. */
function statusRadioOnclick(id) {
  // Plain string search, not a RegExp built from the id: the tag is the <input ...> around it.
  const at = jsp.indexOf('id="' + id + '"');
  const start = jsp.lastIndexOf('<input', at);
  const end = jsp.indexOf('>', at);
  assert.ok(at >= 0 && start >= 0 && end > at, 'no #' + id + ' radio in InboxhubForm.jsp');
  const handler = jsp.slice(start, end + 1).match(/onclick="([^"]*)"/);
  assert.ok(handler, '#' + id + ' has no onclick handler');
  return handler[1];
}

/**
 * An inbox in list mode whose search form holds the page-load search (New) and whose server
 * answers each page by hand. resetDataPageCount is the page's own reset reduced to the state the
 * request path reads; its DOM work is the subject of other checks.
 */
function inbox() {
  const requests = [];
  const form = { status: 'N' };
  const radios = { statusNew: { checked: true }, statusAcknowledged: { checked: false },
    ackToggle: { checked: false },
    // The form's hidden query.acknowledgedToday field, rendered disabled for a New search.
    acknowledgedTodayId: { disabled: true } };
  const viewMode = { value: 'false', disabled: false };
  function jQuery(selector) {
    if (selector === '#inboxSearchForm') {
      // Like jQuery's serialize, a disabled field is left out.
      return { serialize: () => 'query.viewMode=false&query.searchProviderNo=999998&query.status=' + form.status
        + (radios.acknowledgedTodayId.disabled ? '' : '&query.acknowledgedToday=true') };
    }
    return { prop() { return this; }, hide() { return this; } };
  }
  jQuery.ajax = options => {
    const xhr = { abort() { options.error(xhr, 'abort'); } };
    requests.push({ options, xhr });
    return xhr;
  };
  const context = vm.createContext({
    jQuery, requests,
    page: 1, pageSize: 20, hasMoreData: true, isFetchingData: false, currentFetchRequest: null,
    inboxhubResultSetGeneration: 0, inboxSearchFormData: '', filter: '',
    ackToggleState: false,
    changeValueElementByName(name, value) { assert.equal(name, 'query.status'); form.status = value; },
    document: { getElementById: id => (id === 'btnViewMode' ? viewMode : radios[id] || null) },
    ShowSpin() {}, HideSpin() {}, toastErrorMessage() { throw new Error('no page should fail here'); },
    addDataInInboxhubListTable() {},
    fetchInboxhubViewData() { throw new Error('the toggle is a list-mode control'); },
  });
  context.resetDataPageCount = () => {
    if (context.currentFetchRequest) { context.currentFetchRequest.abort(); context.currentFetchRequest = null; }
    context.inboxhubResultSetGeneration++;
    context.page = 1;
    context.hasMoreData = true;
    context.isFetchingData = false;
  };
  vm.runInContext(source, context);
  // Any helper a status radio's handler calls has to come from the page as well.
  for (const id of ['statusAll', 'statusNew', 'statusAcknowledged', 'statusFiled']) {
    for (const [, name] of statusRadioOnclick(id).matchAll(/([A-Za-z_$][\w$]*)\(/g)) {
      if (typeof context[name] !== 'function') vm.runInContext(extractFunction(name), context);
    }
  }
  /** Clicks a search-panel status radio by running the handler the page renders for it. */
  const clickStatusRadio = id => vm.runInContext(statusRadioOnclick(id), context);
  /** Answers the latest request with a page of rows; the page then asks for the next one. */
  const answerLatestPage = () => {
    const { options, xhr } = requests[requests.length - 1];
    options.success('<tr></tr>', 'success', xhr);
  };
  const params = index => new URLSearchParams(requests[index].options.data);
  return { context, requests, radios, answerLatestPage, params, clickStatusRadio };
}

test('the toggled list asks for today\'s acknowledgements, not the whole acknowledged history', () => {
  const page = inbox();

  page.context.toggleAcknowledged(true);

  assert.equal(page.requests.length, 1);
  assert.equal(page.params(0).get('query.status'), 'A');
  assert.equal(page.params(0).get('query.acknowledgedToday'), 'true',
    'an unbounded status=A request loads every result the provider ever acknowledged');
  assert.equal(page.params(0).get('page'), '1');
  assert.equal(page.radios.statusAcknowledged.checked, true, 'the search panel still shows the status listed');
});

test('every later page of the toggled list keeps the same bound', () => {
  const page = inbox();
  page.context.toggleAcknowledged(true);

  page.answerLatestPage();
  page.answerLatestPage();

  assert.equal(page.requests.length, 3, 'list mode chains the next page after each one lands');
  for (const index of [1, 2]) {
    assert.equal(page.params(index).get('page'), String(index + 1));
    assert.equal(page.params(index).get('query.status'), 'A');
    assert.equal(page.params(index).get('query.acknowledgedToday'), 'true',
      'a page without the bound pages through the whole history from that offset on');
  }
});

test('turning the toggle off asks for the New list without the acknowledgement bound', () => {
  const page = inbox();
  page.context.toggleAcknowledged(true);

  page.context.toggleAcknowledged(false);

  const last = page.params(page.requests.length - 1);
  assert.equal(last.get('query.status'), 'N');
  assert.equal(last.get('query.acknowledgedToday'), null, 'the New list is not limited to today');
  assert.equal(page.radios.statusNew.checked, true);
});

test('a status picked in the search panel replaces the toggle, so later refreshes are not bounded', () => {
  // Toggle on, then pick a status in the search panel. A category link, a type badge or the
  // preview switch re-runs the search without the Search button; none of them may keep
  // narrowing the panel's own Acknowledged status to today.
  const page = inbox();
  page.radios.ackToggle.checked = true; // the clinician ticks the toolbar box
  page.context.toggleAcknowledged(true);

  page.clickStatusRadio('statusNew');
  page.clickStatusRadio('statusAcknowledged');
  page.context.fetchInboxhubData();

  const last = page.params(page.requests.length - 1);
  assert.equal(last.get('query.status'), 'A');
  assert.equal(last.get('query.acknowledgedToday'), null,
    'the panel\'s Acknowledged status reaches the whole history');
  assert.equal(page.radios.ackToggle.checked, false, 'the toolbar no longer claims to list today only');
});

test('a search that never used the toggle is not bounded by it', () => {
  // The search panel's own Acknowledged status is the way to reach older acknowledgements,
  // so only the toolbar toggle may narrow what an Acknowledged search returns.
  const page = inbox();

  page.context.fetchInboxhubData();

  assert.equal(page.params(0).get('query.acknowledgedToday'), null);
});

test('the Search button keeps the toggle, because the form itself carries it', () => {
  // The Search button submits the form as a full page, not through fetchInboxhubData. Without the
  // field the next page would come back with a bare status=A and page in the whole history.
  const page = inbox();

  page.context.toggleAcknowledged(true);
  assert.equal(page.radios.acknowledgedTodayId.disabled, false, 'a Search submit now asks for today only');

  page.context.toggleAcknowledged(false);
  assert.equal(page.radios.acknowledgedTodayId.disabled, true, 'the New list submits no bound');

  page.context.toggleAcknowledged(true);
  page.clickStatusRadio('statusAcknowledged');
  assert.equal(page.radios.acknowledgedTodayId.disabled, true,
    'a status picked in the panel replaces the toggle for the Search button too');
});

test('the page rendered for a submitted search starts with the toggle it asked for', () => {
  // The returned page re-ticks the box (restoreToolbarState) and keeps sending the bound only when
  // the submitted search was the toggle's own: Acknowledged status plus the flag.
  const field = jsp.slice(jsp.lastIndexOf('<input', jsp.indexOf('id="acknowledgedTodayId"')),
    jsp.indexOf('/>', jsp.indexOf('id="acknowledgedTodayId"')) + 2);
  assert.match(field, /name="query\.acknowledgedToday"/);
  assert.match(field, /value="true"/);
  assert.ok(field.includes("${query.acknowledgedToday and query.status eq 'A' ? '' : 'disabled'}"),
    'the field is sent only for the toggle\'s own search');
  assert.ok(jsp.includes("var ackToggleState = ${query.acknowledgedToday and query.status eq 'A' ? 'true' : 'false'};"),
    'the toggle state starts from the submitted search');
});
