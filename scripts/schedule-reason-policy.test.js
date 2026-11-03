/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

function run({reasonInTooltip = true, notesInTooltip = true, exposedInGrid = false} = {}) {
  const marker = 'FAKE-PW0123456789abcdef';
  const reason = `${marker} SECRETREASON`, notes = `${marker} SECRETNOTES`;
  const steps = [];
  let shown = false, popupClosed = false;
  const link = {first() {return this;}, waitFor: async () => {}};
  const schedule = {locator(selector) {
    if (selector === 'a.apptLink') return link;
    if (selector.startsWith('a.expand-reason-btn')) return {count: async () => 1, click: async () => {shown = !shown;}};
    if (selector.startsWith('.reason_')) return {filter() {return this;}, first() {return this;}, isVisible: async () => shown};
    throw new Error(`Unexpected schedule locator ${selector}`);
  }, evaluate: async (fn, input) => fn(input)};
  const popup = {waitForLoadState: async () => {}, close: async () => {popupClosed = true;},
    locator: selector => ({inputValue: async () => selector === '#reason' ? reason : notes})};
  const document = {querySelectorAll(selector) {
    if (selector === 'body *') return [{children: [], getClientRects: () => [1], textContent: exposedInGrid ? reason : 'Owned appointment'}];
    assert(selector.includes('[data-bs-original-title]'));
    return [{getAttribute: name => name === 'data-bs-original-title'
      ? [reasonInTooltip && reason, notesInTooltip && notes].filter(Boolean).join(' ') : null,
    classList: {contains: () => false}}];
  }};
  const module = {exports: {}};
  const h = {assert: (ok, message) => assert(ok, message), sqlString: JSON.stringify,
    newContext: async () => ({setDefaultTimeout() {}, on() {}}), login: async () => schedule};
  const imports = {'./lib/playwright-harness': h,
    './lib/playwright-ui': {clickOpensPopup: async () => popup}, './lib/workflow-session': {},
    './lib/gap-provider-fixture': {createUnbookedThrowaway: () => ({providerNo: '999998', username: 'fixture'}),
      registerAppointmentCleanup() {}}};
  const requireFixture = name => {assert(Object.hasOwn(imports, name)); return imports[name];};
  requireFixture.main = null;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'gap-provider-schedule-reason-privacy-playwright-checks.js'), 'utf8'),
    {module, require: requireFixture, document, getComputedStyle: () => ({visibility: 'visible'}), Date});
  const s = {sql: {execute() {}}, config: {}, recorder: {}, marker, patient: '123', provider: '999998',
    context: {browser() {}}, step: async (label, action) => {steps.push(label); await action();}};
  return {result: module.exports.workflow(s), steps, popupClosed: () => popupClosed};
}

test('the owner-approved tooltip is required while grid, popup and toggle coverage remains', async () => {
  const check = run();
  await check.result;
  assert.equal(check.steps.length, 3);
  assert(check.popupClosed(), 'The retained edit-popup proof must run');
});
test('removing either reason or notes from the tooltip fails the owner-approved contract', async () => {
  await assert.rejects(run({reasonInTooltip: false}).result, /tooltip lost.*free-text reason/);
  await assert.rejects(run({notesInTooltip: false}).result, /tooltip lost.*notes/);
});
test('showing the reason in the default grid still fails', async () => {
  await assert.rejects(run({exposedInGrid: true}).result, /day sheet shows.*grid/);
});
