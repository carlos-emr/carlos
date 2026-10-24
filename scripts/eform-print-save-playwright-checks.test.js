/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
'use strict';
// Pins that the eform-print-save browser check drives every branch of saveAfterPrint() on the real
// page: no dirty detection (undefined), edited (truthy) and clean (falsy). Its flag helpers must
// reach a top-level `var` or `let` needToConfirm as well as a form that declares none, so the
// "no dirty detection" case is never quietly turned into the "clean" prompt case.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const checkPath = path.join(__dirname, 'eform-print-save-playwright-checks.js');
const { readFlag, setFlag } = require(checkPath);

/** A stand-in for a Playwright page whose evaluate() runs in a context seeded like an eForm. */
function fakePage(declaration) {
  const context = vm.createContext({});
  if (declaration) vm.runInContext(declaration, context);
  return {
    evaluate: async (fn, arg) => vm.runInContext(`(${fn.toString()})`, context)(arg),
    toolbarSees: () => vm.runInContext("typeof needToConfirm === 'undefined' ? 'undefined' : (needToConfirm ? 'dirty' : 'clean')", context),
  };
}

test('readFlag reports the flag exactly as the toolbar classifies it', async () => {
  assert.equal(await readFlag(fakePage('')), 'undefined');
  assert.equal(await readFlag(fakePage('var needToConfirm;')), 'undefined');
  assert.equal(await readFlag(fakePage('var needToConfirm = false;')), 'clean');
  assert.equal(await readFlag(fakePage('let needToConfirm = 0;')), 'clean');
  assert.equal(await readFlag(fakePage('var needToConfirm = true;')), 'dirty');
});

test('setFlag reaches var, let and undeclared flags for each of the three branches', async () => {
  for (const declaration of ['', 'var needToConfirm = false;', 'let needToConfirm = false;', 'var needToConfirm = true;']) {
    for (const wanted of ['undefined', 'dirty', 'clean']) {
      const page = fakePage(declaration);
      const label = await setFlag(page, wanted);
      assert.equal(page.toolbarSees(), wanted, `${JSON.stringify(declaration)} -> ${wanted}`);
      assert.match(label, /^(form-declared|forced) /, label);
    }
  }
});

test('setFlag says when the form already declared the wanted state and when it was forced', async () => {
  assert.equal(await setFlag(fakePage(''), 'undefined'), 'form-declared undefined');
  assert.equal(await setFlag(fakePage('var needToConfirm = false;'), 'undefined'), 'forced undefined (form declared clean)');
  assert.equal(await setFlag(fakePage(''), 'clean'), 'forced clean (form declared undefined)');
});

test('the browser workflow drives the no-detection and edited save branches without a prompt', () => {
  const source = fs.readFileSync(checkPath, 'utf8');
  const workflow = source.slice(source.indexOf('async function workflow('), source.indexOf('async function main('));
  assert.match(workflow, /for \(const wanted of \['undefined', 'dirty'\]\)/);
  assert.match(workflow, /must save without prompting/);
  assert.match(workflow, /must post exactly one save/);
  assert.match(workflow, /setFlag\(f\.page, 'clean'\)/);
  assert.doesNotMatch(source, /synthetic needToConfirm=false/);
});
