/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const {test} = require('node:test');
const {setImmediate: nextTurn} = require('node:timers/promises');
const {waitForPatientInbox} = require('./patient-messenger-context-playwright-checks');

test('a completed placeholder document does not qualify an inbox whose heading is pending', async () => {
  let release, finished = false;
  const heading = new Promise(resolve => { release = resolve; });
  const page = {
    async waitForLoadState() {},
    locator() { return {waitFor() { return heading; }}; },
  };
  const pending = waitForPatientInbox(page).then(() => { finished = true; });
  try {
    await nextTurn();
    assert.equal(finished, false, 'Initial about:blank readiness was accepted as the inbox');
  } finally { release(); await pending; }
  assert.equal(finished, true);
});

test('a permanently missing inbox heading remains a failure', async () => {
  const failure = new Error('FAKE heading readiness timeout');
  const page = {async waitForLoadState() {}, locator() {return {async waitFor() {throw failure;}};}};
  await assert.rejects(waitForPatientInbox(page, 321), error => error === failure);
});

test('an already rendered destination qualifies with its visible heading', async () => {
  const events = [];
  const page = {
    async waitForLoadState(state) { events.push(['document', state]); },
    locator(selector) { return {async waitFor(options) {events.push(['heading', selector, options]);}}; },
  };
  await waitForPatientInbox(page, 1234);
  assert.deepEqual(events, [['document', 'domcontentloaded'],
    ['heading', 'h2', {state: 'visible', timeout: 1234}]]);
});

test('a navigation failure is propagated before inspecting the destination', async () => {
  const failure = new Error('FAKE navigation failure');
  const page = {async waitForLoadState() {throw failure;}, locator() {assert.fail('Destination must not be inspected');}};
  await assert.rejects(waitForPatientInbox(page), error => error === failure);
});
