/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const {readiness, execute} = require('./run-node-release-tests');

test('Node readiness refuses TODO, unexpected TODO pass, cancellation, skips, empty and missing summaries', () => {
  const clean = {success: true, counts: {tests: 2, passed: 2, failed: 0, cancelled: 0, skipped: 0, todo: 0}};
  assert.equal(readiness(clean), true);
  for (const kind of ['failed', 'cancelled', 'skipped', 'todo']) {
    assert.equal(readiness({...clean, counts: {...clean.counts, [kind]: 1}}), false, kind);
  }
  for (const value of [null, {}, {...clean, counts: {...clean.counts, passed: 1}}, {...clean, success: false}, {...clean, counts: {...clean.counts, tests: 0, passed: 0}}]) {
    assert.equal(readiness(value), false);
  }
});

test('real Node subprocess outcomes cannot hide behind exit zero or TODO annotations', async t => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'node-release-gate-'));
  t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
  const driver = path.join(directory, 'driver.cjs');
  fs.writeFileSync(driver, 'require(process.argv[2]).execute([process.argv[3]], process.argv[4]).then(c => process.exitCode = c)');
  for (const [body, expected, outcome] of [
    ["test('clean', () => {});", 0, 'PASS'],
    ["for(let i=0;i<2;i++) test('same display name', () => {});", 0, 'PASS'],
    ["test('known', {todo: true}, () => {throw new Error('synthetic');});", 1, 'TODO'],
    ["test('unexpected pass', {todo: true}, () => {});", 1, 'TODO'],
    ["test('skipped', {skip: true}, () => {});", 1, 'SKIP'],
    ["test('failed', () => {throw new Error('synthetic');});", 1, 'FAIL'],
  ]) {
    const file = path.join(directory, 'controlled.test.cjs');
    const output = path.join(directory, 'result.json');
    fs.writeFileSync(file, "const test = require('node:test');\n" + body);
    const environment = {...process.env};
    delete environment.NODE_TEST_CONTEXT;
    const child = spawnSync(process.execPath, [driver,
      require.resolve('./run-node-release-tests'), file, output], {encoding: 'utf8', env: environment});
    assert.equal(child.status, expected, body + child.stderr);
    const report = JSON.parse(fs.readFileSync(output));
    assert.equal(report.cases.length, body.includes('same display name') ? 2 : 1, body);
    assert.ok(report.cases.every(c => c.outcome === outcome));
    const ids = report.cases.map(c => JSON.stringify([c.file,c.parents,c.testNumber]));
    assert.equal(new Set(ids).size, ids.length, 'Parameterized executions retain distinct engine identities');
    assert.equal(JSON.stringify(report).includes('Error: synthetic'), false, 'No arbitrary child output in evidence');
  }
  for (const body of ['', 'process.exit(0);']) {
    const file = path.join(directory, 'empty.test.cjs');
    fs.writeFileSync(file, body);
    const environment = {...process.env};
    delete environment.NODE_TEST_CONTEXT;
    const child = spawnSync(process.execPath, [driver,
      require.resolve('./run-node-release-tests'), file, path.join(directory, 'empty-result.json')], {env: environment});
    assert.equal(child.status, 1, 'Synthetic file PASS must not count as a test');
  }
  await assert.rejects(execute([], path.join(directory, 'empty.json')), /Empty/);
});
