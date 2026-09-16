/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { EventEmitter } = require('node:events');
const { createGracefulSignalCancellation, settleOperations } = require('./graceful-signal-cancellation');
const source = fs.readFileSync(path.join(__dirname, 'email-recovery-playwright-checks.js'), 'utf8');

function validateTarget(baseUrl) {
  const start = source.indexOf('const config =');
  const end = source.indexOf('const mysqlHost =', start);
  assert.ok(start >= 0 && end > start);
  return vm.runInNewContext(source.slice(start, end), {
    process: { env: { BASE_URL: baseUrl, ALLOW_NON_LOCAL_BASE_URL: 'true' } },
    // Isolate this fixture's stricter guard from the shared helper's remote opt-in.
    validateBaseUrl: value => new URL(value),
    assert: (condition, message) => assert.ok(condition, message),
  });
}

test('email recovery rejects remote HTTP and HTTPS even with the shared opt-in', () => {
  for (const host of ['example.invalid', '192.0.2.1', '10.0.0.1', 'localhost.example.invalid']) {
    for (const protocol of ['http', 'https']) {
      assert.throws(() => validateTarget(`${protocol}://${host}/carlos`), /loopback BASE_URL/);
    }
  }
});

test('email recovery accepts loopback HTTP and self-signed HTTPS test deployments', () => {
  for (const host of ['localhost', '127.0.0.1', '[::1]']) {
    for (const protocol of ['http', 'https']) {
      assert.doesNotThrow(() => validateTarget(`${protocol}://${host}/carlos`));
    }
  }
});

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
