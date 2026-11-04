/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Regression for finding 240 (#4598): ADFv2's Save and Save and Exit must post into the window they
// were pressed in. reset() retargeted the form at "apptProviderSearch", the legacy schedule window's
// name; CARLOS opens no window of that name, so the browser answered the POST in a second window and
// the first kept showing the unsaved form, where another Save filed a second row. This runs the
// page's own handlers; the live round trips are form-catalog-smoke-adfv2-window (Save) and
// form-adfv2-save-exit (Save and Exit).
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/form/formadfv2.jsp'), 'utf8');
const script = jsp.match(/<script type="text\/javascript" language="Javascript">([\s\S]*?)<\/script>/)[1];
const formTag = jsp.match(/<form\s[^>]*\baction=[^>]*>/)[0];
const SAME_WINDOW = new Set(['', '_self']);

function render(source, contextPath) {
  return source
    .replaceAll('<%= request.getContextPath() %>', contextPath)
    .replaceAll('${pageContext.request.contextPath}', contextPath)
    .replace(/<fmt:message key='[^']*'\/>/g, 'message');
}

/** The page script in a sandbox holding one form, as the browser builds it from the page's <form> tag. */
function load(contextPath) {
  const rendered = render(script, contextPath);
  // A server expression left unrendered would be posted as literal text; the test must render each one.
  assert.doesNotMatch(rendered, /<%/, 'the page script carries a server expression this test does not render');
  const dateBox = name => ({ name, value: '', focus() {} });
  const form = {
    target: '', action: render(formTag.match(/\baction="([^"]*)"/)[1], contextPath),
    submit: { value: 'exit' },
    sigDate: dateBox('sigDate'), influDate: dateBox('influDate'), pneuDate: dateBox('pneuDate'),
  };
  const confirms = [];
  const context = vm.createContext({
    document: { forms: [form] },
    confirm: text => { confirms.push(text); return true; },
    alert: text => assert.fail(`unexpected alert: ${text}`),
  });
  vm.runInContext(rendered, context);
  return { form, confirms, run: call => vm.runInContext(call, context) };
}

test('shouldWireSaveButtons_toTheHandlersThisTestRuns', () => {
  assert.match(jsp, /value="<fmt:message key='global\.save'\/>"\s+onclick="javascript:return onSave\(\);"/);
  assert.match(jsp, /value="<fmt:message key='global\.saveExit'\/>"\s+onclick="javascript:return onSaveExit\(\);"/);
});

const CONTEXTS = { '': 'Root', '/carlos': 'Carlos', '/nested/carlos': 'NestedCarlos' };
for (const [contextPath, contextName] of Object.entries(CONTEXTS)) {
  for (const [handler, submit] of [['onSave', 'save'], ['onSaveExit', 'exit']]) {
    test(`shouldPostIntoTheWindowItWasPressedIn_for${handler[0].toUpperCase()}${handler.slice(1)}UnderContext${contextName}`, () => {
      const { form, confirms, run } = load(contextPath);
      // A stale target or action left by anything that ran before the press must not survive it.
      form.target = 'apptProviderSearch';
      form.action = `${contextPath}/form/createpdf`;
      assert.equal(run(`${handler}()`), true);
      assert.equal(confirms.length, 1, 'the save is confirmed once');
      assert.equal(form.submit.value, submit);
      assert.ok(SAME_WINDOW.has(form.target), `the form posts into window "${form.target}", not the one Save was pressed in`);
      assert.equal(form.action, `${contextPath}/form/formname`);
    });
  }
}

test('shouldDeclareTheSameSaveRoute_inTheFormTagAndInReset', () => {
  assert.doesNotMatch(formTag, /\btarget=/, 'the <form> tag must not name a window');
  for (const contextPath of ['', '/carlos']) {
    const { form, run } = load(contextPath);
    const declared = form.action;
    run('reset()');
    assert.equal(form.action, declared);
  }
});

test('shouldNeverRetargetTheForm_atANamedWindow', () => {
  const targets = [...script.matchAll(/\.target\s*=\s*(["'])(.*?)\1/g)].map(match => match[2]);
  assert.ok(targets.length > 0, 'reset() must set the target, or a target left by an earlier script survives');
  for (const target of targets) {
    assert.ok(SAME_WINDOW.has(target), `the page retargets its form at the named window "${target}"`);
  }
});
