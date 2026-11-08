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

test('passing Node cases cannot qualify test source modified during execution', t => {
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'node-release-source-'));
  t.after(()=>fs.rmSync(directory,{recursive:true,force:true}));
  const source=path.join(directory,'controlled.test.cjs');
  const output=path.join(directory,'result.json');
  const driver=path.join(directory,'driver.cjs');
  fs.writeFileSync(source,"const test=require('node:test');test('passing but changed',()=>require('node:fs').appendFileSync(__filename,'\\n// FAKE changed assertions'));\n");
  fs.writeFileSync(driver,'require(process.argv[2]).execute([process.argv[3]],process.argv[4]).then(code=>process.exitCode=code)');
  const environment={...process.env};delete environment.NODE_TEST_CONTEXT;
  const child=spawnSync(process.execPath,[driver,require.resolve('./run-node-release-tests'),source,output],
    {env:environment,encoding:'utf8',timeout:15000});
  assert.equal(child.status,1,child.stderr);
  const report=JSON.parse(fs.readFileSync(output));
  assert.equal(report.cases.length,1);assert.equal(report.cases[0].outcome,'PASS');
  assert.equal(report.sourceChanged,true);
  assert.match(Object.values(report.sourceFiles)[0],/^[0-9a-f]{64}$/);
});

for(const [label,added] of [['stable selection',null],['new failing test',"require('node:test')('late',()=>{throw Error('must run next time');});"],['new empty test','']])
test('actual release CLI checks its final discovery set: '+label,t=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'node-release-discovery-'));
  t.after(()=>fs.rmSync(directory,{recursive:true,force:true}));
  const runner=path.join(directory,'runner.cjs'),source=path.join(directory,'initial.test.js'),output=path.join(directory,'result.json');
  fs.copyFileSync(require.resolve('./run-node-release-tests'),runner);
  const action=added===null?'':"require('node:fs').writeFileSync(require('node:path').join(__dirname,'late.test.js'),"+JSON.stringify(added)+");";
  fs.writeFileSync(source,"require('node:test')('initial pass',()=>{"+action+"});\n");
  const environment={...process.env};delete environment.NODE_TEST_CONTEXT;
  const child=spawnSync(process.execPath,[runner,'--output',output],{env:environment,encoding:'utf8',timeout:15000});
  assert.equal(child.status,added===null?0:1,child.stderr+' '+child.stdout);
  const report=JSON.parse(fs.readFileSync(output));
  assert.equal(report.summary.success,true);assert.equal(report.cases.length,1);assert.equal(report.cases[0].outcome,'PASS');
  assert.equal(report.sourceChanged,added!==null,'Newly discovered files were silently omitted from qualification');
});
