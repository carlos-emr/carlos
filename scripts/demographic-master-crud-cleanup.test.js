/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');

const source = fs.readFileSync(path.join(__dirname, 'demographic-master-crud-smoke.js'), 'utf8');
const definitions = source.slice(0, source.indexOf('\nmain().catch('));

for (const cleanupFails of [false, true]) {
  test(`shouldAttemptPatientCleanup_whenBrowserCloseFails${cleanupFails ? 'AndSqlFails' : ''}`, async () => {
    const events = [];
    const closeError = new Error('browser close failed');
    let ownedPatientExists = false;
    const sql = {
      rows(query) {
        assert.match(query, /WHERE last_name='FAKE-PW0123456789abcdef'/);
        return ownedPatientExists ? [['42']] : [];
      },
      value: () => ownedPatientExists ? '1' : '0',
      dispose() { events.push('SQL disposed'); },
    };
    const context = {
      URL,
      process: { env: {} },
      console: { log() {} },
      require(name) {
    if (name === './lib/authenticated-probe') return require('./lib/authenticated-probe');
        if (name === 'playwright') return { chromium: { async launch() { return {
          async newContext() { return { setDefaultTimeout() {}, on() {}, async newPage() { return {}; } }; },
          async close() { events.push('browser close attempted'); throw closeError; },
        }; } } };
        if (name === './lib/playwright-harness') return { ...h, readConfig: () => ({ mysql: {} }), createSqlRunner: () => sql };
        if (name === './lib/owned-patient') return {
          newOwnedMarker: () => 'FAKE-PW0123456789abcdef',
          removeOwnedPatient(_sql, id, marker) {
            assert.equal(id, '42');
            assert.equal(marker, 'FAKE-PW0123456789abcdef');
            events.push('patient cleanup attempted');
            if (cleanupFails) throw new Error('patient cleanup failed');
            ownedPatientExists = false;
          },
        };
        throw new Error(`Unexpected module ${name}`);
      },
      savePatient() { ownedPatientExists = true; throw new Error('redirect failed after saving'); },
    };
    vm.createContext(context);
    vm.runInContext(definitions, context);
    // Reach the real teardown after a save whose response never reached the browser.
    vm.runInContext('login = async () => {}; openSearchPopup = async () => ({}); searchByName = async () => {}; createDemographic = savePatient;', context);
    await assert.rejects(context.main(), error => error === closeError, 'retain the browser close failure');
    assert.deepEqual(events, ['browser close attempted', 'patient cleanup attempted', 'SQL disposed']);
    assert.equal(ownedPatientExists, cleanupFails);
    if (cleanupFails) assert.equal(vm.runInContext('findings.at(-1).detail', context), 'patient cleanup failed');
  });
}
