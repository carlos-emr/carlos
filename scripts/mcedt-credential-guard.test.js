/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {createRequire} = require('node:module');
const vm = require('node:vm');
const test = require('node:test');

function invokeEntry(exclusive) {
  const filename = path.join(__dirname, 'mcedt-mailbox-outbox-playwright-checks.js');
  const actualRequire = createRequire(filename);
  const entry = {exports: {}};
  let reachedPreflight = false;
  const requireEntry = name => name === './lib/workflow-session' ? {
    runWorkflow(check, workflow, options) {
      assert.equal(check, 'mcedt-mailbox-outbox');
      assert.equal(options.openPatient, false);
      assert.equal(typeof workflow, 'function');
      assert.equal(typeof options.preflightConfig, 'function');
      reachedPreflight = true;
      // Run the actual entry point's environment guard before creating any session or SQL runner.
      options.preflightConfig({config: {}});
    },
  } : actualRequire(name);
  requireEntry.main = entry;
  vm.runInNewContext(fs.readFileSync(filename, 'utf8'), {
    require: requireEntry, module: entry, exports: entry.exports,
    process: {env: {...process.env, EXCLUSIVE: exclusive}},
    __dirname, __filename: filename,
  }, {filename});
  assert.equal(reachedPreflight, true);
}

test('the direct MCEDT script refuses shared execution before opening database or browser state', () => {
  for (const exclusive of [undefined, '', '0', 'true']) {
    assert.throws(() => invokeEntry(exclusive), /MCEDT credential restoration requires EXCLUSIVE=1/);
  }
});

test('the direct MCEDT script accepts the explicit exclusive-host declaration', () => {
  invokeEntry('1');
});
