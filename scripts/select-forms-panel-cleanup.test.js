/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');
const forms = require('./lib/encounter-form-list');

const source = fs.readFileSync(path.join(__dirname, 'select-forms-panel-playwright-checks.js'), 'utf8');

for (const failure of ['snapshot', 'launch', 'context', 'page', 'cleanup']) {
  test(`shouldRemoveSqlCredentials_whenFailingAt${failure[0].toUpperCase() + failure.slice(1)}`, async t => {
    const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-select-forms-test-'));
    t.after(() => fs.rmSync(scratch, { recursive: true, force: true }));
    const optionFile = path.join(scratch, 'client.cnf');
    const events = [];
    const errors = [];
    let queryCount = 0;
    const context = {
      async newPage() { throw new Error('page failed'); },
      async close() { events.push('context closed'); },
    };
    const browser = {
      async newContext() {
        if (failure === 'context') throw new Error('context failed');
        return context;
      },
      async close() { events.push('browser closed'); },
    };
    await vm.runInNewContext(source, {
      URL,
      process: { env: {}, exit(code) { events.push(`exit ${code}`); } },
      console: { log() {}, error(message) { errors.push(message); } },
      require(name) {
        if (name === 'playwright') return { chromium: { async launch() {
          assert.ok(fs.existsSync(optionFile), 'startup has already created the SQL credential file');
          if (failure === 'launch') throw new Error('launch failed');
          return browser;
        } } };
        if (name === './lib/encounter-form-list') return forms;
        if (name === './lib/playwright-harness') return {
          ...h,
          readConfig: () => ({ mysql: { host: '127.0.0.1', password: 'synthetic-test-password' } }),
          createSqlRunner(config) {
            return h.createSqlRunner(config, {
              mkdtemp: () => scratch,
              exec() {
                queryCount++;
                if (failure === 'snapshot' || (failure === 'cleanup' && queryCount > 1)) throw new Error('query failed');
                return 'Annual\tformAnnual.jsp\tformAnnual\t3\n';
              },
              rm(directory, options) { events.push('SQL disposed'); fs.rmSync(directory, options); },
            });
          },
        };
        throw new Error(`Unexpected module ${name}`);
      },
    }, { filename: 'select-forms-panel-playwright-checks.js' });

    assert.equal(fs.existsSync(optionFile), false, 'the SQL password file must be removed before exit');
    assert.equal(events.filter(event => event === 'SQL disposed').length, 1);
    assert.deepEqual(events.slice(-2), ['SQL disposed', 'exit 1']);
    assert.equal(events.includes('browser closed'), ['context', 'page', 'cleanup'].includes(failure));
    assert.equal(events.includes('context closed'), ['page', 'cleanup'].includes(failure));
    assert.match(errors.at(-1), /FAIL Select Forms panel checks:/);
    if (failure === 'cleanup') {
      assert.match(errors[0], /cleanup: the database query failed/);
      assert.match(errors.at(-1), /page failed/, 'cleanup must retain the original startup error');
    }
  });
}
