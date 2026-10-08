/** Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

for (const filename of ['rx-fax-reprint-represcribe-playwright-checks.js']) {
  for (const [signal, exitCode] of [['SIGINT', 130], ['SIGTERM', 143]]) {
    test(`${filename}: ${signal} cleans fixtures and credentials before exiting ${exitCode}`, () => {
      const source = fs.readFileSync(path.join(__dirname, filename), 'utf8');
      const registration = source.match(/for \(const signal of \['SIGINT', 'SIGTERM'\]\) \{[\s\S]*?\n\}/);
      assert.ok(registration, 'actual signal registration must be present');
      const handlers = new Map();
      const events = [];
      vm.runInNewContext(registration[0], {
        cleanupFixtures() { events.push('fixtures'); },
        removeSecretsDir() { events.push('credentials'); },
        process: {
          on(name, handler) { handlers.set(name, handler); },
          exit(code) { events.push(code); },
        },
      });
      handlers.get(signal)();
      assert.deepEqual(events, ['fixtures', 'credentials', exitCode]);
    });
  }
}

const { EventEmitter } = require('node:events');
const { createGracefulSignalCancellation } = require('./graceful-signal-cancellation');
for (const [signal, exitCode] of [['SIGINT', 130], ['SIGTERM', 143]]) {
  test(`stamp ${signal} waits for submitted work before closing browser and credentials`, async () => {
    const source = fs.readFileSync(path.join(__dirname, 'rx-fax-signature-stamp-playwright-checks.js'), 'utf8');
    const main = source.slice(source.lastIndexOf('(async () => {'));
    const signalProcess = Object.assign(new EventEmitter(), { exitCode: 0, exit() { throw new Error('Immediate process exit'); } });
    const events = [];
    let release, options;
    const submitted = new Promise(resolve => { release = resolve; });
    const operation = vm.runInNewContext(main, {
      createGracefulSignalCancellation: () => createGracefulSignalCancellation({ signalProcess }),
      shouldIgnoreHttpsErrors: () => true,
      process: signalProcess, chromePath: '', baseUrl: new URL('https://localhost/carlos'),
      console: { log() {}, error() {} }, visited: [], findings: [], browserErrorClass: () => 'Error',
      chromium: { async launch(value) { options = value; return {
        async newContext() { return {}; }, async close() { events.push('browser'); },
      }; } },
      async login() { return { async close() {} }; }, async checkBuildStampOnAboutPage() {},
      async runChecks() {
        events.push('submitted');
        signalProcess.emit(signal);
        await submitted;
        events.push('fixtures');
      },
      removeSecretsDir() { events.push('credentials'); },
    });
    await new Promise(resolve => setImmediate(resolve));
    assert.deepEqual(events, ['submitted']);
    assert.equal(options.handleSIGINT, false);
    assert.equal(options.handleSIGTERM, false);
    release();
    await operation;
    assert.deepEqual(events, ['submitted', 'fixtures', 'browser', 'credentials']);
    assert.equal(signalProcess.exitCode, exitCode);
    assert.equal(signalProcess.listenerCount(signal), 0);
  });
}
