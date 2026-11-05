/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {spawnSync} = require('node:child_process');
const h = require('./lib/playwright-harness');
const {requireAxeCore} = require('./accessibility-smoke-playwright-checks');
const {requireImportSource} = require('./demographic-cds-import-playwright-checks');
const {requireExclusiveFixture} = require('./session-heartbeat-timeout-playwright-checks');

test('missing axe-core is a named prerequisite, while other resolver errors fail', () => {
  assert.throws(() => requireAxeCore(() => {throw Object.assign(new Error(), {code:'MODULE_NOT_FOUND'});}),
    error => error instanceof h.SkipCheck && /axe-core@4\.14\.0/.test(error.message));
  const error = new Error('resolver failed');
  assert.throws(() => requireAxeCore(() => {throw error;}), error);
  assert.equal(requireAxeCore(name => `/modules/${name}`), '/modules/axe-core/axe.min.js');
});

function importFrame({disabled=false, options=[], error} = {}) {
  return {locator(selector) {
    if (error) throw error;
    if (selector === '#cdsOptions') return {isDisabled: async () => disabled};
    assert.equal(selector, '#cdsSource option[data-profile]');
    return {evaluateAll: async fn => fn(options)};
  }};
}

test('both CDS workflows share a named skip for disabled or unregistered sources', async () => {
  for (const frame of [importFrame({disabled:true}), importFrame()]) {
    await assert.rejects(requireImportSource(frame, h.createRecorder()),
      error => error instanceof h.SkipCheck && /approved.*source\/profile/.test(error.message));
  }
});

test('configured CDS sources retain profile selection and browser failures remain failures', async () => {
  const options = [{value:'other', dataset:{profile:'omd-5.2'}}, {value:'approved', dataset:{profile:'pcon-2024-02'}}];
  assert.equal(await requireImportSource(importFrame({options}), h.createRecorder()), 'approved');
  assert.equal(await requireImportSource(importFrame({options:options.slice(0,1)}), h.createRecorder()), 'other');
  const error = new Error('browser failed');
  await assert.rejects(requireImportSource(importFrame({error}), h.createRecorder()), error);
  const recorder = h.createRecorder();
  recorder.pageErrors.push({label:'import', text:'uncaught script error'});
  await assert.rejects(requireImportSource(importFrame(), recorder), error => error.name !== 'SkipCheck');
});

test('heartbeat prerequisites skip before SQL/browser setup and use the runner exit code 2', () => {
  for (const env of [{}, {CARLOS_DISPOSABLE_VM:'true'}]) {
    assert.throws(() => requireExclusiveFixture(env), error => error instanceof h.SkipCheck);
    const childEnv = {...process.env, MYSQL_USER:'', MYSQL_PASSWORD:'', CARLOS_DISPOSABLE_VM:'', EXCLUSIVE:'', ...env};
    delete childEnv.NODE_TEST_CONTEXT;
    const result = spawnSync(process.execPath, ['scripts/session-heartbeat-timeout-playwright-checks.js'], {
      cwd:require('node:path').join(__dirname, '..'), encoding:'utf8',
      env:childEnv,
    });
    assert.equal(result.status, 2, result.stdout + result.stderr);
    assert.match(result.stdout, /SKIP session-heartbeat-timeout.*requires/);
    assert.doesNotMatch(result.stdout + result.stderr, /mysql|MariaDB/i);
  }
  assert.doesNotThrow(() => requireExclusiveFixture({CARLOS_DISPOSABLE_VM:'true', EXCLUSIVE:'1'}));
});
