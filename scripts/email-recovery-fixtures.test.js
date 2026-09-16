/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { EventEmitter } = require('node:events');
const { createGracefulSignalCancellation, settleOperations } = require('./graceful-signal-cancellation');
const source = fs.readFileSync(path.join(__dirname, 'email-recovery-playwright-checks.js'), 'utf8');

for (const failure of ['launch', 'SIGINT', 'SIGTERM']) {
  test(`email recovery removes only its owned logs after ${failure}`, async () => {
    const events = [];
    const signalProcess = Object.assign(new EventEmitter(), { env: {}, exitCode: 0 });
    let nextId = 9001;
    await vm.runInNewContext(source, {
      process: signalProcess, URL, console: { log() {}, error() {} },
      require(name) {
        if (name === 'playwright') return { chromium: { async launch() {
          if (failure === 'launch') throw new Error('Launch failed');
          signalProcess.emit(failure);
          return { async close() { events.push('close'); }, async newContext() { assert.fail('Cancelled work resumed'); } };
        } } };
        if (name === 'node:child_process') return { execFileSync(command, args) {
          const query = args.at(-1);
          if (query.startsWith('SELECT COUNT(*) FROM demographic')) return '1';
          if (query.startsWith('INSERT')) { const id = String(nextId++); events.push(`create${id}`); return id; }
          if (query.startsWith('DELETE')) {
            assert.match(query, /WHERE id=900[12] AND subject='PWRECOVERY[0-9a-f]+'/);
            events.push(`delete${/id=(\d+)/.exec(query)[1]}`); return '';
          }
          if (query.startsWith('SELECT COUNT(*) FROM emailLog')) return '0';
          assert.fail(`Unexpected SQL: ${query}`);
        } };
        if (name === 'node:crypto') return require(name);
        if (name === './graceful-signal-cancellation') return { settleOperations,
          createGracefulSignalCancellation: () => createGracefulSignalCancellation({ signalProcess }) };
        if (name === './eform-local-playwright-utils') return {
          assert: (condition, message) => assert.ok(condition, message),
          validateBaseUrl: value => new URL(value), createRecorder: () => ({}),
          buildFailureDetails: () => ({}), getLaunchOptions: () => ({}),
        };
        assert.fail(`Unexpected dependency: ${name}`);
      },
    });
    assert.deepEqual(events, ['create9001', 'create9002', ...(failure === 'launch' ? [] : ['close']), 'delete9001', 'delete9002']);
    assert.equal(signalProcess.exitCode, failure === 'launch' ? 1 : failure === 'SIGINT' ? 130 : 143);
    assert.equal(signalProcess.listenerCount('SIGINT'), 0);
    assert.equal(signalProcess.listenerCount('SIGTERM'), 0);
  });
}
