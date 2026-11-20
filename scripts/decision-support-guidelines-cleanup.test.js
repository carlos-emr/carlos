/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const MARKER = 'FAKE-PW0123456789abcdef';
const PROVIDER = '812346';

function setup(t, options = {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-ds-cleanup-'));
  t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
  const state = {provider: null, quickLists: [{id: 7, providerNo: '999998', name: 'foreign'}], events: [], cleanups: [], browserConnected: false};
  if (options.preexisting) state.quickLists.push({id: 8, providerNo: PROVIDER, name: 'orphan'});
  const sql = {
    value(query) {
      if (query.startsWith('SELECT COUNT(*) FROM provider WHERE')) {
        return state.provider && state.provider.marker === MARKER ? '1' : '0';
      }
      if (query.startsWith('SELECT COUNT(*) FROM quickListUser WHERE')) {
        return String(state.quickLists.filter(row => row.providerNo === PROVIDER).length);
      }
      throw new Error('Unexpected database read');
    },
    execute(query) {
      state.events.push('quick-list-delete-attempt');
      assert.match(query, /DELETE FROM quickListUser WHERE providerNo='812346'/);
      assert.match(query, /EXISTS \(SELECT 1 FROM provider WHERE provider_no='812346' AND last_name='FAKE-PW0123456789abcdef'\)/);
      if (options.deleteFailure) throw new Error('Synthetic child delete refused');
      if (state.provider && state.provider.marker === MARKER) {
        state.quickLists = state.quickLists.filter(row => row.providerNo !== PROVIDER);
      }
    },
  };
  const login = {
    providerNo: PROVIDER,
    create() {state.provider = {marker: MARKER}; state.events.push('provider-created'); if (options.createFailure) throw new Error('Synthetic setup failure');},
    cleanup() {state.events.push('shared-parent-cleanup'); state.provider = null;},
  };
  const h = {
    assert(condition, message) {if (!condition) throw new Error(message);},
    sqlString(value) {return `'${value}'`;},
    buildArtifactPath(dir, base, extension) {assert.equal(dir, directory); return path.join(dir, base + extension);},
  };
  const module = {exports: {}};
  const filename = path.join(__dirname, 'decision-support-guidelines-playwright-checks.js');
  const actualSource = fs.readFileSync(filename, 'utf8');
  const start = actualSource.indexOf('async function workflow(s) {') + 'async function workflow(s) {'.length;
  const end = actualSource.indexOf('  s.cleanup(() => {\n    sql.execute(`DELETE FROM provider_default_program', start);
  assert.ok(start > 0 && end > start, 'The actual workflow setup boundary must exist');
  const composedSource = actualSource + '\nmodule.exports.setupUnderTest = function(s) {'
    + actualSource.slice(start, end) + '\nreturn fixture;};';
  vm.runInNewContext(composedSource, {
    module, exports: module.exports, __dirname, process: {env: {ARTIFACT_DIR: directory}},
    require(name) {
    if (name === './lib/authenticated-probe') return require('./lib/authenticated-probe');
      if (name === './lib/playwright-harness') return h;
      if (name === './lib/throwaway-login-fixture') return {throwawayLoginFixture: () => login};
      if (name.startsWith('node:')) {
        if (name === 'node:fs' && options.fsyncFailure) return new Proxy(fs, {get(target, key) {return key === 'fsyncSync' ? () => {throw new Error('Synthetic proof fsync failure');} : target[key];}});
        return require(name);
      }
      return {};
    },
  }, {filename});
  const session = {sql, marker: MARKER, provider: '999998', config: {testUser: 'source-login'}, context: {browser() {return {isConnected() {return state.browserConnected;}};}}, cleanup(fn) {state.cleanups.push(fn);}};
  const cleanup = () => {
    const errors = [];
    for (const fn of [...state.cleanups].reverse()) {try {fn();} catch (error) {errors.push(error);}}
    return errors;
  };
  return {state, directory, start() {return module.exports.setupUnderTest(session);}, cleanup};
}

test('durable private empty-baseline receipt precedes UI and owned quick-list cleanup precedes shared parent cleanup', t => {
  const f = setup(t); f.start();
  const receipts = fs.readdirSync(f.directory); assert.equal(receipts.length, 1);
  const file = path.join(f.directory, receipts[0]); const receipt = JSON.parse(fs.readFileSync(file));
  assert.deepEqual(receipt, {check: 'decision-support-guidelines', marker: MARKER, providerNo: PROVIDER,
    quickListUserBaseline: {count: 0, rows: []}, verifiedBeforeThrowawayUi: true});
  assert.equal(fs.statSync(file).mode & 0o777, 0o600);
  f.state.quickLists.push({id: 9, providerNo: PROVIDER, name: 'List1'});
  assert.deepEqual(f.cleanup(), []);
  assert.deepEqual(f.state.events, ['provider-created', 'quick-list-delete-attempt', 'shared-parent-cleanup']);
  assert.deepEqual(f.state.quickLists, [{id: 7, providerNo: '999998', name: 'foreign'}]);
});

test('pre-existing orphan collision refuses UI/receipt and is never adopted or deleted', t => {
  const f = setup(t, {preexisting: true});
  assert.throws(() => f.start(), /pre-existing quick-list/);
  assert.equal(fs.readdirSync(f.directory).length, 0);
  assert.deepEqual(f.cleanup(), []);
  assert.deepEqual(f.state.events, ['provider-created', 'shared-parent-cleanup']);
  assert.equal(f.state.quickLists.find(row => row.id === 8).name, 'orphan');
});

test('an unrelated workflow-body failure still removes its application-created preference before parent', t => {
  const f = setup(t); f.start();
  f.state.quickLists.push({id: 9, providerNo: PROVIDER, name: 'List1'});
  const failure = new Error('Synthetic later browser step failure');
  assert.throws(() => {throw failure;}, /later browser/);
  assert.deepEqual(f.cleanup(), []); assert.equal(f.state.provider, null);
  assert.equal(f.state.quickLists.length, 1);
});

test('failed child delete retains the marker-bearing parent even when workflow attempts every callback', t => {
  const f = setup(t, {deleteFailure: true}); f.start();
  f.state.quickLists.push({id: 9, providerNo: PROVIDER, name: 'List1'});
  const errors = f.cleanup(); assert.equal(errors.length, 2);
  assert.match(errors[0].message, /child delete refused/); assert.match(errors[1].message, /retaining/);
  assert.equal(f.state.provider.marker, MARKER); assert.equal(f.state.quickLists.length, 2);
  assert.ok(!f.state.events.includes('shared-parent-cleanup'));
});

test('changed ownership refuses quick-list deletion and blocks shared parent cleanup', t => {
  const f = setup(t); f.start();
  f.state.quickLists.push({id: 9, providerNo: PROVIDER, name: 'List1'});
  f.state.provider.marker = 'FOREIGN';
  const errors = f.cleanup(); assert.equal(errors.length, 2);
  assert.match(errors[0].message, /ownership changed/);
  assert.deepEqual(f.state.events, ['provider-created']); assert.equal(f.state.quickLists.length, 2);
});

test('receipt collision cannot overwrite evidence and still cleans only the new provider', t => {
  const f = setup(t); const name = path.join(f.directory, `${MARKER}-guideline-provider.json`);
  fs.writeFileSync(name, 'prior evidence', {mode: 0o600});
  assert.throws(() => f.start(), /EEXIST/); assert.deepEqual(f.cleanup(), []);
  assert.equal(fs.readFileSync(name, 'utf8'), 'prior evidence'); assert.equal(f.state.provider, null);
});

test('proof fsync failure never enters UI and registered cleanup still completes', t => {
  const f = setup(t, {fsyncFailure: true}); assert.throws(() => f.start(), /proof fsync failure/);
  assert.deepEqual(f.cleanup(), []); assert.equal(f.state.provider, null);
  assert.equal(f.state.quickLists.length, 1);
});

test('partially failed login setup still invokes its previously registered shared cleanup', t => {
  const f = setup(t, {createFailure: true}); assert.throws(() => f.start(), /setup failure/);
  assert.deepEqual(f.cleanup(), []); assert.deepEqual(f.state.events, ['provider-created', 'shared-parent-cleanup']);
  assert.equal(f.state.provider, null);
});


test('unconfirmed browser drain retains preferences and parent without deletion attempts', t => {
  const f = setup(t); f.start();
  f.state.quickLists.push({id: 9, providerNo: PROVIDER, name: 'List1'});
  f.state.browserConnected = true;
  const errors = f.cleanup(); assert.equal(errors.length, 2);
  assert.ok(errors.every(error => /browser drain is unconfirmed/.test(error.message)));
  assert.deepEqual(f.state.events, ['provider-created']);
  assert.equal(f.state.provider.marker, MARKER); assert.equal(f.state.quickLists.length, 2);
});
