/** Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, 'prescription-signature-playwright-checks.js'), 'utf8');

for (const launchFails of [false, true]) {
  test(`signature check owns signal handling and releases it when ${launchFails ? 'launch' : 'context setup'} fails`, async () => {
    const events = [];
    let options;
    await vm.runInNewContext(source, {
      URL,
      process: { env: { PRESCRIPTION_SCRIPT_ID: '45' }, exitCode: 0 },
      console: { error() {}, log() {}, warn() {} },
      require(name) {
        if (name === 'playwright') return { chromium: { async launch(value) {
          events.push('launch');
          options = value;
          if (launchFails) throw new Error('simulated launch failure');
          return {
            async newContext() { throw new Error('simulated context failure'); },
            async close() { events.push('close'); },
          };
        } } };
        if (name === './graceful-signal-cancellation') return { createGracefulSignalCancellation() {
          events.push('register');
          return { throwIfCancelled() { events.push('check'); }, dispose() { events.push('dispose'); } };
        } };
        if (name === './local-fixture-cleanup') return {};
        throw new Error('unexpected dependency');
      },
    });
    assert.equal(options.handleSIGINT, false);
    assert.equal(options.handleSIGTERM, false);
    assert.equal(events[0], 'register');
    assert.equal(events[1], 'launch');
    assert.equal(events.at(-1), 'dispose');
    assert.equal(events.includes('close'), !launchFails);
  });
}
