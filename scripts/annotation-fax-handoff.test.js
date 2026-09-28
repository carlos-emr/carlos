/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, 'e2e/fax/annotate-document-playwright-checks.js'), 'utf8');
const start = source.indexOf('async function waitForAnnotationFaxCover(');
const helper = source.slice(start, source.indexOf('\n}', start) + 2);
function handoff({pathname = '/carlos/documentManager/FaxDocument', message = '', cover = false, saveFailed = false} = {}) {
  let evaluations = 0;
  const context = vm.createContext({window: {location: {pathname}}, document: {
    getElementById: id => id === 'btnSend' && cover ? {} : null,
    querySelector: selector => selector === '[role="alert"]' && message ? {textContent: message}
      : selector === '#status.error' && saveFailed ? {} : null,
  }});
  vm.runInContext(helper, context);
  const page = {waitForFunction: async predicate => {
    evaluations++;
    const value = predicate();
    assert.notEqual(value, false, 'the completed handoff should resolve without waiting for the timeout');
    return {jsonValue: async () => value};
  }};
  return {run: () => context.waitForAnnotationFaxCover(page), evaluations: () => evaluations};
}
test('missing fax accounts fail the required handoff immediately with a fixture diagnostic', async () => {
  const check = handoff({message: 'No active fax accounts are configured. Contact your system administrator.'});
  await assert.rejects(check.run(), /requires an active fax account.*owned UI-only fax fixture/);
  assert.equal(check.evaluations(), 1);
});
test('a real fax cover completes the handoff without changing or skipping its checks', async () => {
  const check = handoff({pathname: '/carlos/fax/faxAction', cover: true});
  await assert.doesNotReject(check.run()); assert.equal(check.evaluations(), 1);
});
test('other server refusals and a failed save remain failures without logging clinical details', async () => {
  for (const state of [{message: 'Sensitive synthetic document detail'}, {pathname: '/carlos/documentManager/AnnotateDocument', saveFailed: true}]) {
    const check = handoff(state);
    await assert.rejects(check.run(), error => {
      assert.match(error.message, /was refused before reaching the cover page/);
      assert.doesNotMatch(error.message, /Sensitive/);
      return true;
    });
  }
});
