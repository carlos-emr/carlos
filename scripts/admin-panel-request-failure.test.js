/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// #5842: the Administration panel hijacks the submit of the forms it loads (registerFormSubmit in
// administration/index.jsp). A failed request (403 refusal, 500 error page, WAF block) must be shown
// above the form, keep the admin's entries, never paint the failed response body, and make usable again
// only the submit controls that were usable when it was sent. The functions run here are the page's own,
// cut out of the JSP.
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.join(__dirname, '..');
const PAGE = fs.readFileSync(path.join(ROOT, 'src/main/webapp/WEB-INF/jsp/administration/index.jsp'), 'utf8');
const GROUPS_PAGE = fs.readFileSync(path.join(ROOT, 'src/main/webapp/WEB-INF/jsp/eform/efmmanageformgroups.jsp'), 'utf8');
const MESSAGE = 'FIXTURE request failed message';
const FAILED_BODY = '<html><body>FIXTURE error page body</body></html>';

// Cuts one named function out of the page, from its declaration to its matching closing brace.
// Comments and quoted strings are skipped, so an apostrophe in a comment or a brace inside a
// string literal cannot end the function early.
function functionSource(name) {
  const start = PAGE.indexOf(`function ${name}(`);
  assert.ok(start >= 0, `administration/index.jsp no longer defines ${name}`);
  let depth = 0;
  let quote = null;
  for (let i = PAGE.indexOf('{', start); i < PAGE.length; i++) {
    const ch = PAGE[i];
    if (quote) {
      if (ch === '\\') i++;
      else if (ch === quote) quote = null;
    } else if (ch === '/' && PAGE[i + 1] === '/') {
      i = PAGE.indexOf('\n', i);
    } else if (ch === '/' && PAGE[i + 1] === '*') {
      i = PAGE.indexOf('*/', i) + 1;
    } else if (ch === '\'' || ch === '"') {
      quote = ch;
    } else if (ch === '{') {
      depth++;
    } else if (ch === '}' && --depth === 0) {
      return PAGE.slice(start, i + 1);
    }
  }
  throw new Error(`Unbalanced braces in ${name}`);
}

// A stand-in for the few jQuery calls the two functions make, recording what they do.
function panelShell() {
  const state = {ajax: [], panelHtml: [], notices: [], buttons: [{disabled: false}], events: [], handler: null};
  const formElement = {id: 'ForwardRulesForm'};
  const form = {
    attr: (name) => ({action: '/carlos/admin/ForwardingRules', method: 'post'})[name],
    submit: (handler) => { state.handler = handler; return form; },
    prevAll: (selector) => ({
      remove: () => {
        assert.equal(selector, '.carlos-admin-request-failed');
        state.notices = state.notices.filter((notice) => !notice.markup.includes('carlos-admin-request-failed'));
      },
    }),
    before: (notice) => { state.notices.push(notice); return form; },
    find: (selector) => {
      assert.equal(selector, ':submit');
      return {filter: (keep) => ({get: () => state.buttons.filter((button) => keep.call(button))})};
    },
    trigger: (name) => { state.events.push(name); return form; },
  };
  function $(arg) {
    if (arg === '#ForwardRulesForm' || arg === form) return form;
    if (Array.isArray(arg)) return {prop: (name, value) => { arg.forEach((button) => { button[name] = value; }); }};
    if (arg === formElement) return {serialize: () => 'operation=update'};
    if (arg === '#dynamic-content') return {html: (value) => state.panelHtml.push(value)};
    if (typeof arg === 'string' && arg.startsWith('<div ')) {
      const notice = {markup: arg, textValue: null, scrolled: []};
      notice.text = (value) => { notice.textValue = value; return notice; };
      notice[0] = {scrollIntoView: (options) => notice.scrolled.push(options)};
      return notice;
    }
    throw new Error(`Unexpected jQuery call: ${String(arg)}`);
  }
  $.ajax = (options) => { state.ajax.push(options); };
  const context = {$, carlosAdminRequestFailedMessage: MESSAGE};
  vm.runInNewContext(`${functionSource('showAdminRequestFailure')}\n${functionSource('registerFormSubmit')}\n`
      + "registerFormSubmit('ForwardRulesForm', 'dynamic-content');", context);
  state.send = () => state.handler.call(formElement);
  return state;
}

test('shouldShowTheStatusAboveTheForm_whenAHijackedSaveFails', () => {
  const shell = panelShell();
  assert.equal(shell.send(), false, 'The hijacked form must not also submit natively');
  shell.buttons[0].disabled = true; // as a form that disables its own button while sending
  assert.equal(shell.ajax.length, 1);
  assert.equal(typeof shell.ajax[0].error, 'function', 'A failed panel request needs an error handler (#5842)');

  shell.ajax[0].error({status: 403, statusText: 'Forbidden', responseText: FAILED_BODY});

  assert.deepEqual(shell.panelHtml, [], 'A failure must keep the panel and the admin\'s entries');
  assert.equal(shell.notices.length, 1, 'One notice goes above the form');
  const notice = shell.notices[0];
  assert.match(notice.markup, /class="alert alert-danger carlos-admin-request-failed"/);
  assert.match(notice.markup, /role="alert"/, 'Screen readers must announce the failure');
  assert.equal(notice.textValue, `HTTP 403: ${MESSAGE}`);
  assert.ok(!String(notice.textValue).includes('FIXTURE error page body'), 'The failed response body is never shown');
  assert.deepEqual(shell.buttons, [{disabled: false}], 'The submit control must be usable again');
  assert.deepEqual(shell.events, ['carlos:admin-request-failed'], 'The page is told, so it can check its fields again');
  assert.deepEqual(notice.scrolled.map((options) => options.block), ['nearest'], 'The notice is scrolled into view');
});

test('shouldLeaveOutTheStatus_whenTheRequestNeverReachedTheServer', () => {
  const shell = panelShell();
  shell.send();
  shell.ajax[0].error({status: 0, statusText: 'error'});
  assert.equal(shell.notices.length, 1);
  assert.equal(shell.notices[0].textValue, MESSAGE);
});

test('shouldClearTheEarlierNotice_whenTheFormIsSentAgain', () => {
  const shell = panelShell();
  shell.send();
  shell.ajax[0].error({status: 500, responseText: FAILED_BODY});
  shell.ajax[0].error({status: 500, responseText: FAILED_BODY});
  assert.equal(shell.notices.length, 1, 'A repeated failure replaces its notice instead of stacking another');

  shell.send();
  assert.equal(shell.notices.length, 0, 'Sending again clears the stale notice');
  shell.ajax[1].success('<p>FIXTURE saved panel</p>');
  assert.deepEqual(shell.panelHtml, ['<p>FIXTURE saved panel</p>']);
  assert.equal(shell.notices.length, 0);
});

test('shouldTakeTheMessageFromTheBundle_inEveryLocale', () => {
  assert.ok(PAGE.includes('<fmt:message key="admin.admin.panelRequestFailed" var="adminPanelRequestFailed"/>'),
    'The notice text must come from the resource bundle');
  assert.ok(PAGE.includes("var carlosAdminRequestFailedMessage = '${carlos:forJavaScript(adminPanelRequestFailed)}';"),
    'The bundle text must be encoded for a JavaScript string');
  for (const locale of ['en', 'fr', 'es', 'pl', 'pt_BR']) {
    const bundle = fs.readFileSync(path.join(ROOT, `src/main/resources/oscarResources_${locale}.properties`), 'latin1');
    const line = bundle.split('\n').find((row) => row.startsWith('admin.admin.panelRequestFailed='));
    assert.ok(line && line.length > 'admin.admin.panelRequestFailed='.length + 20, `Missing or empty text in ${locale}`);
    assert.doesNotMatch(line, /&#/, `${locale} must use \\u escapes, not HTML entities, for a text node`);
  }
});

test('shouldKeepAButtonDisabled_whenThePageHadDisabledItBeforeSending', () => {
  const shell = panelShell();
  // eForm groups keeps "Add group" disabled for an empty or duplicate name; a failure must not undo that.
  shell.buttons.push({disabled: true});
  shell.send();
  shell.ajax[0].error({status: 500, responseText: FAILED_BODY});
  assert.deepEqual(shell.buttons, [{disabled: false}, {disabled: true}]);
});

test('shouldCheckTheGroupNameAgain_whenAnAddGroupRequestFails', () => {
  // The name may have changed while the request was out: the groups page re-runs its own check.
  assert.match(GROUPS_PAGE, /\$\("#addGroupForm"\)\.on\("carlos:admin-request-failed", function \(\) \{\s*\$\(this\)\.find\("\.check"\)\.each\(validate\);/);
});
