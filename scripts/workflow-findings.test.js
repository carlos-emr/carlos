/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {throwFindings} = require('./lib/workflow-findings');
const {classifyResult} = require('./run-playwright-suite');
const finding = label => Object.assign(new Error(label), {failedStep: label});
const check = {expectedFailure: {finding: 1, step: 'first'}};
test('no findings returns normally', () => assert.doesNotThrow(() => throwFindings([])));
for (const labels of [['first'], ['second'], ['first','second'], ['second','first']]) {
 test(`runner retains independent failures: ${labels}`, () => {
  let error; try {throwFindings(labels.map(finding));} catch (caught) {error=caught;}
  assert.equal(error.errors.length, labels.length);
  const outcome=classifyResult(check,{outcome:'FAIL',failedStep:error.failedStep,cleanupFailed:false}).outcome;
  assert.equal(outcome,labels.length===1&&labels[0]==='first'?'known-fail':'failed-elsewhere');
  assert.equal(classifyResult(check,{outcome:'FAIL',failedStep:error.failedStep,cleanupFailed:true}).outcome,'failed-elsewhere');
 });
}
