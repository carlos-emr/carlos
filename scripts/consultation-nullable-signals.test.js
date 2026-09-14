/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { EventEmitter } = require('node:events');
const { createGracefulSignalCancellation, settleOperations } = require('./graceful-signal-cancellation');
const source = fs.readFileSync(path.join(__dirname, 'consultation-nullable-fields-playwright-checks.js'), 'utf8');

function validateDatabaseEnvironment(env) {
  const start = source.indexOf('const mysqlHost =');
  const end = source.indexOf('let mysqlDefaults =', start);
  assert(start >= 0 && end > start && end < source.indexOf('(async () =>'));
  return vm.runInNewContext(source.slice(start, end), {
    process: { env }, assert: (condition, message) => assert.ok(condition, message),
  });
}

test('nullable consultation rejects non-loopback database hosts before staging any state', () => {
  for (const host of ['shared.example.invalid', '192.0.2.1', 'db', 'localhost.example.invalid', '127.0.0.1.example.invalid']) {
    assert.throws(() => validateDatabaseEnvironment({ MYSQL_HOST: host, ALLOW_NON_LOCAL_MYSQL_HOST: 'true' }), /loopback MYSQL_HOST/);
  }
});

test('nullable consultation accepts only its documented loopback database targets', () => {
  for (const host of [undefined, 'localhost', '127.0.0.1', '::1']) {
    assert.doesNotThrow(() => validateDatabaseEnvironment({ MYSQL_HOST: host }));
  }
});

test('nullable consultation rejects credential-file option injection without exposing the password', () => {
  assert.throws(() => validateDatabaseEnvironment({ MYSQL_PASSWORD: 'fixture-secret\nhost=shared.example.invalid' }), error => {
    assert.match(error.message, /MYSQL_PASSWORD must not contain line breaks/);
    assert.doesNotMatch(error.message, /fixture-secret|shared.example/);
    return true;
  });
});

test('nullable consultation rejects non-numeric fixture IDs before any SQL or browser work', () => {
  const start = source.indexOf('const requestId =');
  const end = source.indexOf('const mysqlHost =', start);
  assert.ok(start >= 0 && end > start && end < source.indexOf('(async () =>'));
  const validation = source.slice(start, end);
  for (const value of ['2 OR 1=1', '2; DELETE FROM consultationRequests', '-2', '2.5', '2\nOR 1=1']) {
    assert.throws(() => vm.runInNewContext(validation, {
      process: { env: { CONSULT_NULLABLE_REQUEST_ID: value } },
      assert: (condition, message) => assert.ok(condition, message),
    }), /CONSULT_NULLABLE_REQUEST_ID must be numeric/);
  }
  for (const value of ['2', '0002', '2147483647']) {
    assert.doesNotThrow(() => vm.runInNewContext(validation, {
      process: { env: { CONSULT_NULLABLE_REQUEST_ID: value } },
      assert: (condition, message) => assert.ok(condition, message),
    }));
  }
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  test(`nullable consultation restores its fixture and disposes handlers after ${signal}`, async () => {
    const events = [];
    const signalProcess = Object.assign(new EventEmitter(), { env: {}, exitCode: 0 });
    let launchOptions;
    const listPage = {
      async waitForLoadState() {},
      locator() { return { async count() { return 1; } }; },
    };
    const context = { async waitForEvent() { return listPage; } };
    await vm.runInNewContext(source, {
      URL, Buffer, process: signalProcess, console: { log() {}, error() {} },
      require(name) {
        if (name === 'playwright') return { chromium: { async launch(options) {
          launchOptions = options;
          return { async newContext() { return context; }, async close() { events.push('close'); } };
        } } };
        if (name === './graceful-signal-cancellation') return {
          settleOperations,
          createGracefulSignalCancellation: () => createGracefulSignalCancellation({ signalProcess }),
        };
        if (name === 'child_process') return { execFileSync(command, args) {
          const sql = args.at(-1);
          if (sql.startsWith('SELECT')) return '999998\t1';
          if (sql.includes('providerNo=NULL')) events.push('stage');
          else {
            assert.match(sql, /providerNo='999998', urgency='1'/);
            events.push('restore');
          }
          return '';
        } };
        if (name === 'fs') return { mkdtempSync() { return '/tmp/simulated-consult-fixture'; }, writeFileSync() {}, rmSync() { events.push('credentials-cleanup'); } };
        if (name === 'os' || name === 'path') return require(name);
        if (name === './eform-local-playwright-utils') return {
          assert(value, message) { assert.ok(value, message); },
          assertNotErrorPage: async () => {}, screenshot: async () => {}, wirePage() {},
          validateBaseUrl: value => new URL(value), getLaunchOptions: () => ({}),
          createRecorder: () => ({}), buildFailureDetails: () => ({}),
          async login() {
            signalProcess.emit(signal);
            await Promise.resolve();
            events.push('operation-settled');
            return { locator() { return { first() { return { async click() {} }; } }; } };
          },
        };
        throw new Error(`Unexpected dependency: ${name}`);
      },
    });
    assert.equal(launchOptions.handleSIGINT, false);
    assert.equal(launchOptions.handleSIGTERM, false);
    assert.deepEqual(events, ['stage', 'operation-settled', 'close', 'restore', 'credentials-cleanup']);
    assert.equal(signalProcess.exitCode, signal === 'SIGINT' ? 130 : 143);
    assert.equal(signalProcess.listenerCount('SIGINT'), 0);
    assert.equal(signalProcess.listenerCount('SIGTERM'), 0);
  });
}
