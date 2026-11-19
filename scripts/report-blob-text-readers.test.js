/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {createRequire} = require('node:module');
const {spawnSync} = require('node:child_process');
const script = path.join(__dirname, 'report-blob-text-readers-playwright-checks.js');
const fixtureDatabase = '6361726C6F73:6C6F63616C686F7374:3306:1:2F64617461';

function loadWorkflow(fakeSpawnSync) {
  const localRequire = createRequire(script);
  const module = {exports:{}};
  const context = vm.createContext({module, exports:module.exports, __dirname, process,
    require: id => id === 'node:child_process' ? {spawnSync: fakeSpawnSync} : localRequire(id)});
  vm.runInContext(fs.readFileSync(script, 'utf8'), context, {filename:script});
  return module.exports.workflow;
}

test('shouldSkipBeforeSqlSetup_whenThePackagedHostIsNotAuthorized', () => {
  const env = {...process.env, MYSQL_USER:'', MYSQL_PASSWORD:'', CARLOS_DISPOSABLE_HOST:'', EXCLUSIVE:''};
  delete env.NODE_TEST_CONTEXT;
  const result = spawnSync(process.execPath, [script], {env, encoding:'utf8', timeout:10000, killSignal:'SIGKILL'});
  assert.equal(result.status, 2, result.stdout + result.stderr);
  assert.match(result.stdout, /SKIP report-blob-text-readers.*requires CARLOS_DISPOSABLE_HOST/);
  assert.doesNotMatch(result.stdout + result.stderr, /MYSQL_PASSWORD|MariaDB|ECONNREFUSED/);
});

test('shouldReturnToOwnedFixtureCleanup_whenTheJavaChildStalls', async () => {
  let child;
  let targetVerified = false;
  const workflow = loadWorkflow((command, args, options) => {
    assert.equal(command, 'java');
    assert.ok(Number.isFinite(options.timeout) && options.timeout > 0 && options.timeout < 180000);
    assert.equal(options.killSignal, 'SIGKILL');
    assert.equal(options.env.PROBE_BLOB_DB_ID, fixtureDatabase);
    if (args.at(-1) === '--verify-target') {
      targetVerified = true;
      return {status: 0};
    }
    assert.equal(options.env.PROBE_BLOB_FORM_ID, '73');
    assert.equal(options.stdio, 'inherit');
    // Exercise OS termination, shortening only the test's waiting time.
    child = spawnSync(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], {...options, timeout:50});
    return child;
  });
  let present = false;
  const cleanups = [];
  const session = {marker:'FAKE-probe-timeout', cleanup:fn=>cleanups.push(fn), step:async (_name, fn)=>fn(),
    sql:{rows:()=>present?[['73']]:[], insertId:()=>{assert.ok(targetVerified);present=true;return '73';},
      value: query => query.includes('DATABASE()') ? fixtureDatabase : '0',
      execute(query, parameters) {
        assert.match(query, /^DELETE FROM eform WHERE fid=\?/);
        assert.equal(parameters[0], '73');present=false;
      }}};
  try {
    await assert.rejects(workflow(session), error => error.code === 'ETIMEDOUT');
  } finally {
    for (const cleanup of cleanups.reverse()) await cleanup();
  }
  assert.equal(child.signal, 'SIGKILL');
  assert.equal(cleanups.length, 1);
  assert.equal(present, false);
});

test('shouldExplainTheDatabaseSetupFailureBeforeInserting_whenTheInstalledProbeUsesAnotherSchema', async () => {
  const workflow = loadWorkflow((_command, args, options) => {
    assert.equal(args.at(-1), '--verify-target');
    assert.equal(options.env.PROBE_BLOB_DB_ID, fixtureDatabase);
    return {status: 2};
  });
  await assert.rejects(workflow({marker: 'FAKE-mismatch', sql: {
    value(query) {
      assert.match(query, /HEX\(DATABASE\(\)\)/);
      return fixtureDatabase;
    },
    insertId() { assert.fail('A mismatched database must never receive the fixture'); },
  }}), /probe setup refused the fixture database before insertion/);
});
