/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function fixture() {
  const events = [], sql = {value(query) {
    if (query.includes('FROM security')) {events.push('provider'); return '999998';}
    if (query.includes('INSERT INTO demographic')) {events.push('patient INSERT'); return '42';}
    throw new Error('Unexpected SQL');
  }};
  const harness = {assert, readConfig: () => ({mysql: {}, testUser: 'owned'}), createSqlRunner: () => sql,
    sqlString: value => "'" + value + "'", createRecorder: () => ({}),
    runCheck: ({run}) => run({cancellation: {}}), launchBrowser: async () => {events.push('browser'); throw new Error('stop after insertion');}};
  const context = {module: {exports: {}}, console, require(name) {
    if (name === 'node:crypto') return require(name);
    if (name === './playwright-harness') return harness;
    return {};
  }};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'lib/workflow-session.js'), 'utf8'), context);
  return {run: context.module.exports.runWorkflow, events, sql};
}
test('failed metadata engine preflight runs before any patient INSERT or browser', async () => {
  const env = fixture();
  await assert.rejects(env.run('owned', () => {}, {preflight({sql, provider}) {
    assert.equal(sql, env.sql); assert.equal(provider, '999998'); env.events.push('preflight'); throw new Error('engine blocked');
  }}), /engine blocked/);
  assert.deepEqual(env.events, ['provider', 'preflight']);
});
test('a successful awaited preflight precedes patient creation without changing ordinary workflow order', async () => {
  const env = fixture();
  await assert.rejects(env.run('owned', () => {}, {preflight: async () => {
    await Promise.resolve(); env.events.push('preflight');
  }}), /stop after insertion/);
  assert.deepEqual(env.events, ['provider', 'preflight', 'patient INSERT', 'browser']);
});
test('existing workflows without a preflight keep their patient creation order', async () => {
  const env = fixture(); await assert.rejects(env.run('owned', () => {}), /stop after insertion/);
  assert.deepEqual(env.events, ['provider', 'patient INSERT', 'browser']);
});

test('custom patient creation is registered and used instead of the generic INSERT', async () => {
  const env = fixture();
  await assert.rejects(env.run('owned', () => {}, {patientFixtureFactory({sql, marker, provider}) {
    assert.equal(sql, env.sql); assert.equal(provider, '999998'); assert.match(marker, /^FAKE-PW[0-9a-f]{16}$/);
    env.events.push('registered');
    return {create() {env.events.push('custom INSERT'); return '42';}, verifyOwner() {}, cleanup() {}};
  }}), /stop after insertion/);
  assert.deepEqual(env.events, ['provider', 'registered', 'custom INSERT', 'browser']);
});
test('failed custom insertion never falls through to generic insertion or browser launch', async () => {
  const env = fixture();
  await assert.rejects(env.run('owned', () => {}, {patientFixtureFactory() {
    env.events.push('registered');
    return {create() {throw new Error('private insertion unconfirmed');}, verifyOwner() {}, cleanup() {}};
  }}), /private insertion unconfirmed/);
  assert.deepEqual(env.events, ['provider', 'registered']);
});
