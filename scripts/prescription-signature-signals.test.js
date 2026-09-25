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
        if (name === './browser-error-class') return require(name);
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

const reprintHelper = source.slice(source.indexOf('async function postReprintSession('),
  source.indexOf('async function checkEmptyPrescriptionPrint('));
for (const status of [200, 409]) {
  test(`signature reprint setup binds its configured patient and preserves CSRF for HTTP ${status}`, async () => {
    const requests = [];
    const context = {
      prescriptionScriptId: '45', prescriptionDemographicNo: '42', URLSearchParams,
      document: { querySelector() { return { value: 'fixture-csrf-token' }; } },
      window: { location: { pathname: '/carlos/rx/choosePatient', search: '?demographicNo=99' } },
      async fetch(url, options) { requests.push({ url, options }); return { status }; },
    };
    vm.runInNewContext(reprintHelper, context);
    const page = { async evaluate(body, parameters) { return body(parameters); } };
    if (status === 200) await context.postReprintSession(page);
    else await assert.rejects(context.postReprintSession(page), /HTTP 409/);
    assert.equal(requests.length, 1);
    const { url, options } = requests[0];
    assert.equal(url, '/carlos/rx/rePrescribe2?method=reprint2');
    assert.equal(options.method, 'POST');
    assert.equal(options.credentials, 'same-origin');
    assert.equal(options.body.get('scriptNo'), '45');
    assert.equal(options.body.get('demographicNo'), '42');
    assert.equal(options.body.get('CSRF-TOKEN'), 'fixture-csrf-token');
    assert.equal(options.headers['CSRF-TOKEN'], 'fixture-csrf-token');
  });
}
