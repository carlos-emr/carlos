/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function entryPoint(env) {
  const events = [];
  const harness = {
    assert,
    readConfig: () => ({mysql: {}, testUser: 'owned'}),
    createRecorder: () => ({}),
    runCheck: ({run}) => run({cancellation: {}}),
    createSqlRunner() {
      events.push('SQL connection');
      throw new Error('stop at authorized SQL setup');
    },
    launchBrowser() { events.push('browser'); },
  };
  const lifecycle = {module: {exports: {}}, console, require(name) {
    if (name === 'node:crypto') return require(name);
    if (name === './playwright-harness') return harness;
    return {};
  }};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'lib/workflow-session.js'), 'utf8'), lifecycle);
  const entry = {exports: {}};
  let outcome;
  const requireStub = name => {
    if (name === './lib/playwright-harness') return harness;
    if (name === './lib/workflow-session') return {runWorkflow(...args) {
      outcome = lifecycle.module.exports.runWorkflow(...args);
      return outcome;
    }};
    return {};
  };
  requireStub.main = entry;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'appointment-settings-playwright-checks.js'), 'utf8'),
    {module: entry, require: requireStub, process: {env}});
  return {outcome, events, guard: entry.exports.requireDisposableEnvironment};
}

for (const env of [{}, {EXCLUSIVE: '1'}, {CARLOS_DISPOSABLE_VM: 'true'},
  {EXCLUSIVE: '0', CARLOS_DISPOSABLE_VM: 'true'}, {EXCLUSIVE: '1', CARLOS_DISPOSABLE_VM: 'false'}]) {
  test(`appointment settings refuses an unconfirmed target before SQL or browser: ${JSON.stringify(env)}`, async () => {
    const execution = entryPoint(env);
    await assert.rejects(execution.outcome, /EXCLUSIVE=1 and CARLOS_DISPOSABLE_VM=true/);
    assert.deepEqual(execution.events, []);
  });
}

test('appointment settings reaches SQL only after both disposable and exclusive confirmation', async () => {
  const execution = entryPoint({EXCLUSIVE: '1', CARLOS_DISPOSABLE_VM: 'true'});
  await assert.rejects(execution.outcome, /stop at authorized SQL setup/);
  assert.deepEqual(execution.events, ['SQL connection']);
});
