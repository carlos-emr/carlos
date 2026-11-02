/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require.resolve('./prescription-signature-playwright-checks'), 'utf8');
const start = source.indexOf('async function checkEmptyPrescriptionPrint(');
const end = source.indexOf('\nasync function openPrescriptionView', start);
function fixture(status = 200, state = {hasPreview: false, framePresent: false, printDisabled: true, helperPresent: true}) {
  const routes = [], visited = [];
  let evaluated = false;
  const context = vm.createContext({URLSearchParams, process: {env: {}}, prescriptionDemographicNo: '42', visited,
    choosePrescriptionPatient: async () => {},
    gotoApp: async (_page, route) => {routes.push(route); return {status: () => status};},
    assertNoErrorPage: async () => {},
  });
  vm.runInContext(source.slice(start, end), context);
  return {routes, visited, evaluated: () => evaluated,
    run: () => context.checkEmptyPrescriptionPrint({evaluate: async () => {evaluated = true; return state;}})};
}
test('empty print probe names its patient and records success', async () => {
  const f = fixture(); await f.run();
  assert.equal(new URL(f.routes[0], 'https://localhost').searchParams.get('demographicNo'), '42');
  assert.equal(f.visited[0].label, 'empty-prescription-print-guard');
});
test('a conflict fails on HTTP status before evaluating print controls', async () => {
  const f = fixture(409);
  await assert.rejects(f.run(), /returned HTTP 409/);
  assert.equal(f.evaluated(), false);
});
test('failure diagnostics identify the missing control without rendered clinical text', async () => {
  const f = fixture(200, {hasPreview: false, framePresent: false, printDisabled: false, helperPresent: true});
  await assert.rejects(f.run(), /"printDisabled":false/);
});
