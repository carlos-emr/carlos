/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { EventEmitter } = require('node:events');
const { SkipCheck } = require('./lib/playwright-harness');
const { createGracefulSignalCancellation } = require('./graceful-signal-cancellation');

for (const [error, expectedCode, expectedStatus] of [
  [new SkipCheck('FAKE-PATIENT diagnostic must not be logged'), 2, 'SKIP:'],
  [new Error('preflight failure'), 1, 'FAIL:'],
]) {
  test(`Rx PDF preflight reports ${expectedStatus} and cleans up without starting a browser`, async () => {
    const source = fs.readFileSync(path.join(__dirname, 'rx-fax-record-binding-playwright-checks.js'), 'utf8');
    const main = source.slice(source.lastIndexOf('(async () => {'));
    const output = [];
    let code, cleaned = false, summary;
    const signalProcess = Object.assign(new EventEmitter(), { env: {}, exit(value) { code = value; } });
    await vm.runInNewContext(main, {
      SkipCheck, process: signalProcess, faxRoundTripTimeoutMs: 1000,
      createGracefulSignalCancellation: () => createGracefulSignalCancellation({ signalProcess }),
      pdf: { requirePoppler() { throw error; } },
      chromium: { launch() { assert.fail('Preflight failure must prevent browser launch'); } },
      removeSecretsDir() { cleaned = true; },
      baseUrl: new URL('http://localhost/carlos'), visited: [], findings: [], artifactDir: '/unused',
      browserErrorClass: e => e.name, errorSourceLocation: () => '', fixtureErrorTag: () => '',
      buildArtifactPath: () => '/unused/result.json',
      fs: { writeFileSync(_file, body) { summary = JSON.parse(body); } },
      console: { log: value => output.push(value), error: value => output.push(value) },
    });
    assert.equal(code, expectedCode);
    assert.equal(cleaned, true);
    assert.ok(output.some(line => line.startsWith(expectedStatus)));
    assert.ok(output.every(line => !line.startsWith('PASS:')));
    assert.ok(output.every(line => !line.includes('FAKE-PATIENT')));
    assert.equal(summary.findings.length, expectedCode === 2 ? 0 : 1);
    assert.equal(summary.skipped, expectedCode === 2 ? 'Poppler pdftotext is unavailable' : undefined);
    assert.equal(signalProcess.listenerCount('SIGTERM'), 0);
  });
}

test('clinical forms registers a PDF preflight before the workflow can create fixtures', () => {
  const source = fs.readFileSync(path.join(__dirname, 'clinical-forms-save-reopen-playwright-checks.js'), 'utf8');
  const registration = source.slice(source.lastIndexOf('if (require.main === module)'));
  const module = { exports: {} };
  const calls = [];
  const require = Object.assign(name => {
    assert.equal(name, './lib/export-content-helpers');
    return { requirePoppler(tool) { calls.push(tool); throw new SkipCheck('missing tool'); } };
  }, { main: module });
  vm.runInNewContext(registration, {
    require, module, NAME: 'clinical-forms-save-reopen', FORMS: [],
    workflow() { assert.fail('Missing PDF tool must prevent fixture creation'); },
    runWorkflow(name, workflow, options) {
      assert.equal(name, 'clinical-forms-save-reopen');
      assert.throws(() => options.preflight(), SkipCheck);
    },
  });
  assert.deepEqual(calls, ['pdftotext']);
});
