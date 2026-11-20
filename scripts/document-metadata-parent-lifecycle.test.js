/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {cleanupOwnedWorkflow} = require('./lib/workflow-session');
function fixture() {
  const events = [];
  const state = {events, browser: {close: async () => events.push('browser')}, sql: {
    value() {assert.fail('Custom lifecycle must not use generic patient queries');},
    execute() {assert.fail('Custom lifecycle must not run generic support/parent deletes');},
    dispose() {events.push('dispose');}}, patient: '42', marker: 'owned', cleanups: [() => events.push('child')],
  patientFixture: {verifyOwner() {events.push('owner');}, cleanup({browserClosed}) {assert.equal(browserClosed, true); events.push('exact parent');}}};
  return state;
}
test('custom parent cleanup follows confirmed browser close and child cleanup with no generic deletion', async () => {
  const env = fixture(); await cleanupOwnedWorkflow(env);
  assert.deepEqual(env.events, ['browser', 'owner', 'child', 'exact parent', 'dispose']);
});
test('custom parent ownership failure preserves all children and skips generic fallback', async () => {
  const env = fixture(); env.patientFixture.verifyOwner = () => {throw new Error('parent changed');};
  await assert.rejects(cleanupOwnedWorkflow(env), /parent changed/);
  assert.deepEqual(env.events, ['browser', 'dispose']);
});
test('failed child cleanup retains exact parent without fallback', async () => {
  const env = fixture(); env.cleanups = [() => {throw new Error('child retained');}];
  await assert.rejects(cleanupOwnedWorkflow(env), /child retained/);
  assert.deepEqual(env.events, ['browser', 'owner', 'dispose']);
});
test('failed browser close retains every strict child before any ownership or cleanup callback', async () => {
  const env = fixture(); env.browser.close = async () => {throw new Error('browser uncertain');};
  env.patientFixture.verifyOwner = () => assert.fail('Strict ownership callback must not run before browser closure');
  env.cleanups = [() => assert.fail('Child cleanup must not run before browser closure')];
  env.patientFixture.cleanup = () => assert.fail('Parent cleanup must not run before browser closure');
  await assert.rejects(cleanupOwnedWorkflow(env), /browser uncertain; Strict patient fixture and children retained/);
  assert.deepEqual(env.events, ['dispose']);
});
test('registered but unconfirmed parent insertion cannot be deleted even without a returned patient id', async () => {
  const env = fixture(); env.patient = undefined;
  env.patientFixture.verifyOwner = () => {throw new Error('insertion unconfirmed');};
  await assert.rejects(cleanupOwnedWorkflow(env), /insertion unconfirmed/);
  assert.deepEqual(env.events, ['browser', 'dispose']);
});
test('cleanup acknowledgement loss is reported without generic fallback or second parent attempt', async () => {
  const env = fixture(); let attempts = 0;
  env.patientFixture.cleanup = () => {attempts++; throw new Error('SQL acknowledgement lost');};
  await assert.rejects(cleanupOwnedWorkflow(env), /acknowledgement lost/);
  assert.equal(attempts, 1); assert.deepEqual(env.events, ['browser', 'owner', 'child', 'dispose']);
});
