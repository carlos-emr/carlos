/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const pharmacy = fs.readFileSync(path.join(__dirname, 'rx-preview-pharmacy-playwright-checks.js'), 'utf8');
const signature = fs.readFileSync(path.join(__dirname, 'prescription-signature-playwright-checks.js'), 'utf8');
function section(source, start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from);
  assert.ok(from >= 0 && to > from);
  return source.slice(from, to);
}
const fixtureConfig = section(pharmacy, 'let requestedScriptId =', 'const mysqlHost');
for (const value of ['abc', '0', '-1', '45 OR 1=1']) {
  test(`malformed explicit prescription input is rejected before any database or browser use: ${value}`, () => {
    assert.throws(() => vm.runInNewContext(fixtureConfig,
      {process: {env: {PRESCRIPTION_SCRIPT_ID: value}}, assert}), /positive integer prescription identifier/);
  });
}
for (const value of ['1000000000', '2147483647', '2147483648']) {
  test(`a numeric fixture outside the current Reprint route range fails before startup: ${value}`, () => {
    assert.throws(() => vm.runInNewContext(fixtureConfig,
      {process: {env: {PRESCRIPTION_SCRIPT_ID: value}}, assert}), /Reprint route limit of 999999999/);
  });
}
test('the highest supported Reprint fixture remains valid', () => {
  const context = {process: {env: {PRESCRIPTION_SCRIPT_ID: '999999999'}}, assert};
  vm.runInNewContext(fixtureConfig + '\nthis.selectedFixture = scriptId;', context);
  assert.equal(context.selectedFixture, '999999999');
});
test('explicit numeric fixture input is trimmed and normalized without changing its identity', () => {
  const context = {process: {env: {PRESCRIPTION_SCRIPT_ID: ' 00045 '}}, assert};
  vm.runInNewContext(fixtureConfig + '\nthis.selectedFixture = scriptId;', context);
  assert.equal(context.selectedFixture, '45');
});

const resolver = section(pharmacy, 'function resolvePrescriptionScriptId(', 'function sql(');
function resolve(requestedScriptId, headers, drugs) {
  const context = {
    requestedScriptId, demographicNo: '1', assert,
    sql(query) {
      const header = query.match(/^SELECT demographic_no FROM prescription WHERE script_no=(\d+)$/);
      if (header) return headers[header[1]] == null ? '' : String(headers[header[1]]);
      const count = query.match(/^SELECT COUNT\(\*\) FROM drugs WHERE script_no=(\d+) AND demographic_no=(\d+)$/);
      if (count) return String(drugs.filter(d => d.script === Number(count[1]) && d.patient === Number(count[2])).length);
      assert.match(query, /^SELECT MAX\(p.script_no\) FROM prescription p JOIN drugs d ON d.script_no=p.script_no WHERE /);
      const headerPatient = query.match(/p.demographic_no=(\d+)/);
      const drugPatient = query.match(/d.demographic_no=(\d+)/);
      const range = query.match(/p.script_no BETWEEN (\d+) AND (\d+)/);
      // Evaluate the ownership predicates actually present in the query; omitting either must
      // make the mixed-owner fixture win and fail the expected result below.
      const candidates = drugs.filter(d => Object.hasOwn(headers, d.script)
        && (!headerPatient || headers[d.script] === Number(headerPatient[1]))
        && (!drugPatient || d.patient === Number(drugPatient[1]))
        && (!range || (d.script >= Number(range[1]) && d.script <= Number(range[2])))).map(d => d.script);
      return candidates.length ? String(Math.max(...candidates)) : 'NULL';
    },
  };
  vm.runInNewContext(resolver, context);
  return context.resolvePrescriptionScriptId();
}
const headers = {45: 1, 63: 1, 200: 2, 300: 1};
const drugs = [{script: 45, patient: 1}, {script: 200, patient: 1}, {script: 300, patient: 2}];
test('explicit usable fixture is retained and automatic selection skips empty and mixed-owner scripts', () => {
  assert.equal(resolve('45', headers, drugs), '45');
  assert.equal(resolve('', headers, drugs), '45');
});
test('automatic fixtures skip unsupported high IDs and retain the highest supported ID', () => {
  assert.equal(resolve('', {...headers, 1000000000: 1, 2147483647: 1},
    [...drugs, {script: 1000000000, patient: 1}, {script: 2147483647, patient: 1}]), '45');
  assert.equal(resolve('', {999999999: 1, 1000000000: 1},
    [{script: 999999999, patient: 1}, {script: 1000000000, patient: 1}]), '999999999');
  assert.throws(() => resolve('', {1000000000: 1}, [{script: 1000000000, patient: 1}]), /supported by Reprint/);
});
for (const [id, reason] of [['63', /no drug rows owned/], ['999', /does not identify an existing prescription/],
  ['200', /does not belong/], ['300', /no drug rows owned/]]) {
  test(`unusable explicit fixture ${id} explains its actual cause and suggests an owned alternative`, () => {
    assert.throws(() => resolve(id, headers, drugs), error => reason.test(error.message)
      && /Use PRESCRIPTION_SCRIPT_ID=45/.test(error.message));
  });
}
test('missing automatic fixtures and unusable explicit fixtures without alternatives ask for an owned fixture', () => {
  assert.throws(() => resolve('', {63: 1}, []), /No prescription with owned drug rows/);
  assert.throws(() => resolve('63', {63: 1}, []), /No usable prescription exists/);
});

// Execute the real entry point through its failure paths: invalid fixture validation must run
// before Chromium launch or pharmacy mutation, and defaults/owned cleanup must still run.
// The module-scope state, cleanupRun() and the entry point are one unit since issue #3600
// moved cleanup out of the finally block so a SIGINT/SIGTERM handler can share it.
const pharmacyEntry = pharmacy.slice(pharmacy.indexOf('let browser = null;'));
for (const invalid of [true, false]) {
  test(`pharmacy fixture startup cleans up after ${invalid ? 'fixture validation' : 'browser launch'} failure`, async () => {
    const events = [];
    const context = {
      createRecorder: () => ({}), config: {chromePath: ''},
      randomBytes: () => ({toString: () => 'fixture'}),
      initMysqlDefaults() { events.push('defaults'); },
      resolvePrescriptionScriptId() { events.push('resolve'); if (invalid) throw new Error('unusable fixture'); return '45'; },
      chromium: {launch(options) {
        assert.equal(options.handleSIGINT, false); assert.equal(options.handleSIGTERM, false);
        events.push('launch'); throw new Error('browser unavailable');
      }},
      NO_PLAYWRIGHT_SIGNAL_HANDLING: {handleSIGINT: false, handleSIGTERM: false},
      getLaunchOptions: () => ({}),
      stageNoPharmacy() {assert.fail('startup failure must not change pharmacy links');},
      restorePharmacy(ids) {assert.equal(ids, null); events.push('restore');},
      cleanupOwnedWorkflow: async value => {assert.equal(value.browser, null); events.push('owned cleanup');},
      cleanupMysqlDefaults() {events.push('defaults cleanup');}, sql() {},
      buildFailureDetails: () => ({}), process: {exitCode: 0}, console: {error() {}, warn() {}},
      installCleanupSignalHandlers: () => ({dispose() {}}),
    };
    await vm.runInNewContext(pharmacyEntry, context);
    assert.equal(context.process.exitCode, 1);
    assert.deepEqual(events, ['defaults', 'resolve', ...(invalid ? [] : ['launch']), 'restore', 'owned cleanup', 'defaults cleanup']);
  });
}

// Run the production finally block with an otherwise successful status. Restoration failures
// must change the process result while browser/owned fixture/defaults cleanup still proceeds.
const cleanupStart = pharmacy.indexOf('async function cleanupRun() {');
const cleanupEnd = pharmacy.indexOf('\n}\n', cleanupStart) + '\n}'.length;
assert.ok(cleanupStart > 0 && cleanupEnd > cleanupStart);
const teardown = `(${pharmacy.slice(cleanupStart, cleanupEnd)})()`;
for (const failRestore of [false, true]) {
  test(`pharmacy restoration ${failRestore ? 'failure fails' : 'success preserves'} an otherwise successful run`, async () => {
    const events = [];
    const errors = [];
    const context = {
      stagedLinkIds: '12,13', browser: {}, foreignPatient: null, foreignMarker: 'fixture',
      restorePharmacy(ids) {
        assert.equal(ids, '12,13'); events.push('restore');
        if (failRestore) throw new Error('fixture restoration unavailable');
      },
      cleanupOwnedWorkflow: async () => {events.push('owned cleanup');},
      cleanupMysqlDefaults() {events.push('defaults cleanup');}, sql() {},
      process: {exitCode: 0}, console: {error(message) {errors.push(message);}},
    };
    await vm.runInNewContext(teardown, context);
    assert.equal(context.process.exitCode, failRestore ? 1 : 0);
    assert.deepEqual(events, ['restore', 'owned cleanup', 'defaults cleanup']);
    assert.equal(errors.length, failRestore ? 1 : 0);
    if (failRestore) assert.match(errors[0], /^FAIL .*restore demographicPharmacy links/);
  });
}

const waitHelper = section(signature, 'async function waitForPreviewOrExplain(', 'async function previewFrame(');
async function probe({waits, states}) {
  const timeouts = [];
  const context = {prescriptionScriptId: '45', prescriptionDemographicNo: '1'};
  vm.runInNewContext(waitHelper, context);
  const page = {
    locator(selector) {
      assert.equal(selector, '#preview', 'diagnostics must not read clinical page text');
      return {async waitFor(options) {timeouts.push(options.timeout); const result = waits.shift(); if (result) throw result;}};
    },
    async evaluate() {return states.shift();},
  };
  let error;
  try {await context.waitForPreviewOrExplain(page);} catch (failure) {error = failure;}
  return {timeouts, error};
}
test('immediate and slow successful previews retain the original total render budget', async () => {
  assert.deepEqual(await probe({waits: [null], states: []}), {timeouts: [5000], error: undefined});
  assert.deepEqual(await probe({waits: [new Error('early'), null],
    states: [{complete: false, frameAttached: false, serverFoundDrugs: false}]}),
  {timeouts: [5000, 25000], error: undefined});
});
test('a completed page explicitly reporting no preview fails promptly without clinical text', async () => {
  const result = await probe({waits: [new Error('early')],
    states: [{complete: true, frameAttached: false, serverFoundDrugs: false}]});
  assert.deepEqual(result.timeouts, [5000]);
  assert.match(result.error.message, /hasPreview=false/);
  assert.match(result.error.message, /p.demographic_no=1 AND d.demographic_no=1/);
  assert.match(result.error.message, /digital_signature_id IS NULL/);
});
for (const state of [
  {complete: true, frameAttached: false, serverFoundDrugs: null},
  {complete: true, frameAttached: false, serverFoundDrugs: true},
  {complete: false, frameAttached: false, serverFoundDrugs: false},
]) {
  test(`ambiguous or unfinished page retains its true timeout: ${JSON.stringify(state)}`, async () => {
    const timeout = new Error('actual locator timeout');
    const result = await probe({waits: [new Error('early'), timeout], states: [state, state]});
    assert.equal(result.error, timeout);
    assert.deepEqual(result.timeouts, [5000, 25000]);
  });
}
test('an initially loading page only receives an empty diagnosis once it settles', async () => {
  const result = await probe({waits: [new Error('early'), new Error('late')], states: [
    {complete: false, frameAttached: false, serverFoundDrugs: false},
    {complete: true, frameAttached: false, serverFoundDrugs: false},
  ]});
  assert.deepEqual(result.timeouts, [5000, 25000]);
  assert.match(result.error.message, /hasPreview=false/);
});

test('pharmacy render failure never reads or reports prescription body text', async () => {
  const code = section(pharmacy, 'async function assertPreviewRenders(', '// Module scope so the SIGINT');
  const context = {assert};
  vm.runInNewContext(code, context);
  const frame = {locator(selector) {assert.equal(selector, '#signature'); return {async waitFor() {throw new Error('timeout');}};}};
  const host = {locator(selector) {assert.equal(selector, '#preview'); return {async elementHandle() {return {contentFrame: async () => frame};}};}};
  await assert.rejects(context.assertPreviewRenders(host, 'owned fixture'),
    /^Error: owned fixture: preview did not render \(#signature missing\)$/);
});

const reprint = section(signature, 'async function postReprintSession(', 'async function checkEmptyPrescriptionPrint(');
for (const [result, message] of [
  [{status: 404}, /may be missing, belong to another patient, or have no owned drug rows/],
  [{status: 403}, /HTTP 403/],
  [{status: 500}, /HTTP 500/],
  [{status: 200, redirected: true}, /redirected.*check the login and permissions/],
]) {
  test(`reprint setup explains its real refusal without assuming every failure is an empty fixture: ${JSON.stringify(result)}`, async () => {
    const context = {prescriptionScriptId: '45', prescriptionDemographicNo: '1'};
    vm.runInNewContext(reprint, context);
    await assert.rejects(context.postReprintSession({evaluate: async () => result}), message);
  });
}
