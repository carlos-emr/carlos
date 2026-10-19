/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');
const MARKER = 'FAKE-PW0123456789abcdef';
const INTENTS = ['Save Query', 'Run Query And Save to Patient Set'];
const filename = path.join(__dirname, 'demographic-report-favourites-playwright-checks.js');

function load({source, tool} = {}) {
  const module = {exports: {}};
  vm.runInNewContext(source || fs.readFileSync(filename, 'utf8'), {
    module, URL, URLSearchParams, console,
    require(name) {
      if (name === './lib/playwright-harness') return h;
      if (name === './lib/playwright-ui') return {clickOpensPopupOrNavigates: async () => ({page: tool}), clickAndAwaitReload: async () => {}};
      if (name === './lib/workflow-session') return {};
      throw new Error('Unexpected caller dependency');
    },
  }, {filename});
  return module.exports;
}

function probeSession(options = {}) {
  const calls = []; const rows = new Map(); const cleanup = [];
  const sql = {
    value(query) {
      if (query.startsWith('INSERT INTO demographic')) return '42';
      const own = query.match(/(?:queryName|set_name)='([^']+)'/);
      if (query.startsWith('SELECT COUNT(*)')) return own ? String(rows.get(query.includes('demographicSets') ? 'set:' + own[1] : 'fav:' + own[1]) || 0) : '0';
      throw new Error('Unexpected controlled SQL');
    },
    execute() {throw new Error('This probe must never issue fixture deletion');},
  };
  async function fetch(url, request) {
    calls.push({url, request});
    const fields = request.form || request.params;
    if (options.writeFavourite) rows.set('fav:' + fields.queryName, 1);
    if (options.writeSet) rows.set('set:' + fields.setName, 1);
    const status = options.status ?? (request.method === 'POST' ? 403 : 405);
    return {status: () => status, headers: () => ({...(options.application === false ? {} : {'x-permitted-cross-domain-policies': 'none'}),
      ...(options.allow === false ? {} : {allow: options.allow || 'POST'})}), text: async () => ''};
  }
  return {calls, rows, session: {sql, patient: '41', marker: MARKER, provider: '999998', config: {baseUrl: new URL('https://localhost/carlos')},
    context: {request: {fetch}, browser() {return {isConnected: () => false};}}, cleanup(fn) {cleanup.push(fn);}}, cleanup};
}

for (const method of ['GET', 'HEAD']) for (const [index, intent] of INTENTS.entries()) {
  test(`${method} ${intent} requires exact405/AllowPOST and no favourite or patient-set row`, async () => {
    const api = load(); const p = probeSession();
    await api.assertReportMutationRefused(p.session, {method, intent, ...api.reportMutationNames(MARKER, `${method[0]}${index}`)});
    assert.equal(p.calls.length, 1); const call = p.calls[0];
    assert.equal(call.request.method, method); assert.equal(call.request.maxRedirects, 0);
    assert.equal(call.request.params.query, intent); assert.equal(call.request.params.select, 'demographic_no');
    assert.equal(call.request.params.lastName, MARKER); assert.equal(call.request.params.sex, '1');
    assert.equal(call.request.form, undefined);
  });
}
for (const intent of INTENTS) test(`tokenless POST ${intent} requires application403 and no rows`, async () => {
  const api = load(); const p = probeSession();
  await api.assertReportMutationRefused(p.session, {method: 'POST', intent, ...api.reportMutationNames(MARKER, 'P0')});
  assert.equal(p.calls[0].request.form.query, intent);
  assert.ok(!Object.keys(p.calls[0].request.form).some(name => /csrf/i.test(name)));
  assert.equal(p.calls[0].request.headers, undefined);
});

for (const options of [{status: 200}, {status: 302}, {status: 403}, {status: 500}, {allow: false}, {allow: 'GET, POST'}, {application: false}, {writeFavourite: true}, {writeSet: true}]) {
  test(`unsafe false-green is rejected: ${JSON.stringify(options)}`, async () => {
    const api = load(); const p = probeSession(options);
    await assert.rejects(api.assertReportMutationRefused(p.session, {method: 'HEAD', intent: INTENTS[1], ...api.reportMutationNames(MARKER, 'H1')}));
  });
}

test('owned set names are unique, use all marker entropy and fit the real VARCHAR20 column', () => {
  const api = load(); const names = ['G0', 'G1', 'H0', 'H1', 'P0', 'P1', 'S0'].map(suffix => api.reportMutationNames(MARKER, suffix).setName);
  assert.equal(new Set(names).size, 7);
  assert.ok(names.every(name => name.length === 20 && name.includes(MARKER.slice(-16))));
  assert.throws(() => api.reportMutationNames('foreign', 'G0'));
});

for (const [tokenIn, status] of [['body', 200], ['header', 200], ['missing', 200], ['body', 403]]) {
  test(`native POST proof: token=${tokenIn} response=${status}`, async () => {
    const api = load(); let posted = false;
    const fields = new URLSearchParams({query: INTENTS[0], queryName: MARKER});
    if (tokenIn === 'body') fields.set('CSRF-TOKEN', 'synthetic-noncredential-token');
    const request = {method: () => 'POST', url: () => 'https://localhost/carlos/report/DemographicReport', postData: () => fields.toString(),
      headers: () => tokenIn === 'header' ? {'csrf-token': 'synthetic-noncredential-token'} : {}, response: async () => ({status: () => status})};
    const page = {waitForRequest(predicate) {assert.equal(predicate(request), true); return Promise.resolve(request);}};
    const run = api.submitReportWrite(page, INTENTS[0], async () => {posted = true;}, request.url());
    if (tokenIn === 'missing' || status !== 200) await assert.rejects(run); else await run;
    assert.equal(posted, true);
  });
}

for (const scenario of ['owned', 'preexisting', 'foreign-patient', 'null-patient']) test(`patient-set fixture cleanup: ${scenario}`, () => {
  const api = load(); let removed = false; let callback; let reads = 0;
  const session = {marker: MARKER, patient: '41', context: {browser() {return {isConnected: () => false};}}, cleanup(fn) {callback = fn;}, sql: {
    value(query) {
      reads++;
      if (reads === 1) return scenario === 'preexisting' ? '1' : '0';
      if (query.includes('IS NULL')) return ['foreign-patient', 'null-patient'].includes(scenario) ? '1' : '0';
      return removed ? '0' : '1';
    }, execute(query) {assert.match(query, /demographic_no IN \(41,42\)/); removed = true;},
  }};
  if (scenario === 'preexisting') {assert.throws(() => api.registerOwnedPatientSets(session, '42')); assert.equal(callback, undefined);}
  else {api.registerOwnedPatientSets(session, '42'); if (scenario === 'owned') callback(); else assert.throws(callback);}
  assert.equal(removed, scenario === 'owned');
});

async function actualMatrixStep(source) {
  const p = probeSession();
  const locator = {first() {return this;}, waitFor: async () => {}};
  const tool = {locator() {return locator;}};
  p.session.schedule = tool; p.session.recorder = {};
  p.session.step = async (label, body) => {
    if (label.startsWith('both report write intents refuse')) await body();
  };
  await load({source, tool}).workflow(p.session);
  return p.calls.map(({request}) => [request.method, (request.params || request.form).query]);
}

test('actual installed-case composition executes all four method probes and both tokenless probes', async () => {
  const calls = await actualMatrixStep();
  assert.deepEqual(calls, [['GET', INTENTS[0]], ['GET', INTENTS[1]], ['HEAD', INTENTS[0]], ['HEAD', INTENTS[1]], ['POST', INTENTS[0]], ['POST', INTENTS[1]]]);
});

for (const missing of ['HEAD', 'Run Query And Save to Patient Set']) test(`actual composition refuses a missing required matrix dimension: ${missing}`, async () => {
  let source = fs.readFileSync(filename, 'utf8');
  source = missing === 'HEAD' ? source.replace("for (const method of ['GET', 'HEAD'])", "for (const method of ['GET'])")
    : source.replace("const WRITE_INTENTS = ['Save Query', 'Run Query And Save to Patient Set'];", "const WRITE_INTENTS = ['Save Query'];");
  const calls = await actualMatrixStep(source);
  assert.notEqual(JSON.stringify(calls), JSON.stringify([['GET', INTENTS[0]], ['GET', INTENTS[1]], ['HEAD', INTENTS[0]], ['HEAD', INTENTS[1]], ['POST', INTENTS[0]], ['POST', INTENTS[1]]]));
});

for (const scenario of ['owned', 'foreign-result', 'silent-write']) test(`actual read-only GET composition: ${scenario}`, async () => {
  const p = probeSession(); const calls = []; let changed = false;
  const originalValue = p.session.sql.value;
  p.session.sql.value = query => query.startsWith('SELECT COUNT(*)') && changed ? '1' : originalValue(query);
  p.session.context.request.get = async (_url, options) => {
    calls.push(options);
    const run = Boolean(options.params);
    if (run && scenario === 'silent-write') changed = true;
    const patient = scenario === 'foreign-result' ? '42' : '41';
    return {status: () => 200, text: async () => run
      ? `<div class="section-header">Search Returned: 1 Results</div><table><tr><th>ID</th></tr><tr><td>${patient}</td></tr></table>`
      : '<input id="select_demographic_no">'};
  };
  const locator = {first() {return this;}, waitFor: async () => {}};
  const tool = {locator() {return locator;}};
  p.session.schedule = tool; p.session.recorder = {};
  p.session.step = async (label, body) => {if (label.startsWith('read-only GET')) await body();};
  const run = load({tool}).workflow(p.session);
  if (scenario === 'owned') await run; else await assert.rejects(run);
  assert.equal(calls.length, 2); assert.equal(calls[1].params.query, 'Run Query');
});

test('native POST capture excludes another route, intent and HTTP method', async () => {
  const api = load();
  const base = {method: () => 'POST', url: () => 'https://localhost/carlos/report/DemographicReport',
    postData: () => new URLSearchParams({query: INTENTS[0], 'CSRF-TOKEN': 'synthetic'}).toString(),
    headers: () => ({}), response: async () => ({status: () => 200})};
  const page = {waitForRequest(predicate) {
    assert.equal(predicate({...base, method: () => 'GET'}), false);
    assert.equal(predicate({...base, url: () => 'https://localhost/carlos/other'}), false);
    assert.equal(predicate({...base, url: () => 'https://foreign.example/carlos/report/DemographicReport'}), false);
    assert.equal(predicate({...base, postData: () => 'query=Run+Query'}), false);
    assert.equal(predicate(base), true);
    return Promise.resolve(base);
  }};
  await api.submitReportWrite(page, INTENTS[0], async () => {}, base.url());
});

async function reverseCleanupComposition(scenario) {
  const callbacks = []; const events = []; const sets = [];
  let secondExists = false; let connected = scenario === 'browser-connected';
  if (scenario === 'preexisting') sets.push({patient: '900'});
  const sql = {
    value(query) {
      if (query.startsWith('INSERT INTO demographic')) {secondExists = true; events.push('second-created'); return '42';}
      if (query.startsWith('SELECT COUNT(*) FROM demographicSets')) {
        return String(query.includes('IS NULL') ? sets.filter(row => !['41', '42'].includes(row.patient)).length : sets.length);
      }
      if (query.startsWith('SELECT COUNT(*) FROM demographic WHERE demographic_no=42')) return secondExists ? '1' : '0';
      if (query.startsWith('SELECT COUNT(*) FROM demographicQueryFavourites')) return '0';
      throw new Error('Unexpected composed cleanup read');
    },
    execute(query) {
      if (query.startsWith('DELETE FROM demographicSets')) {
        events.push('set-delete');
        assert.match(query, /demographic_no IN \(41,42\)/);
        if (scenario === 'delete-refused') throw new Error('Synthetic set delete refused');
        if (scenario !== 'delete-noop') sets.splice(0);
      } else if (query.startsWith('DELETE FROM demographic WHERE')) {
        events.push('second-delete'); assert.match(query, /demographic_no=42 AND last_name='FAKE-PW0123456789abcdef'/);
        secondExists = false;
      } else assert.match(query, /^DELETE FROM demographicQueryFavourites/);
    },
  };
  const locator = {first() {return this;}, waitFor: async () => {}};
  const tool = {locator() {return locator;}};
  const session = {sql, patient: '41', marker: MARKER, provider: '999998', config: {baseUrl: new URL('https://localhost/carlos')},
    context: {browser() {return {isConnected: () => connected};}}, schedule: tool, recorder: {},
    cleanup(fn) {callbacks.push(fn);}, step: async () => {}};
  let setupError;
  try {await load({tool}).workflow(session);} catch (error) {setupError = error;}
  if (scenario !== 'preexisting') sets.push({patient: '42'});
  if (scenario === 'foreign-patient') sets.push({patient: '900'});
  if (scenario === 'null-patient') sets.push({patient: null});
  const errors = [];
  // This is the shared workflow's real scheduling contract: one refusal does
  // not stop later callbacks. The case must protect its own parent explicitly.
  for (const callback of [...callbacks].reverse()) {try {callback();} catch (error) {errors.push(error);}}
  return {setupError, errors, events, sets, secondExists};
}

for (const scenario of ['owned', 'foreign-patient', 'null-patient', 'browser-connected', 'preexisting', 'delete-refused', 'delete-noop']) {
  test(`actual reverse cleanup preserves ownership through continuing callbacks: ${scenario}`, async () => {
    const state = await reverseCleanupComposition(scenario);
    if (scenario === 'owned') {
      assert.equal(state.setupError, undefined); assert.equal(state.errors.length, 0);
      assert.deepEqual(state.events, ['second-created', 'set-delete', 'second-delete']);
      assert.equal(state.secondExists, false); assert.equal(state.sets.length, 0);
    } else if (scenario === 'preexisting') {
      assert.match(state.setupError.message, /already uses/); assert.equal(state.errors.length, 0);
      assert.deepEqual(state.events, ['second-created', 'second-delete']);
      assert.deepEqual(state.sets, [{patient: '900'}]);
    } else {
      assert.equal(state.setupError, undefined); assert.equal(state.errors.length, 2);
      assert.equal(state.secondExists, true); assert.ok(state.sets.some(row => row.patient === '42'));
      assert.ok(!state.events.includes('second-delete'));
      if (['foreign-patient', 'null-patient', 'browser-connected'].includes(scenario)) assert.ok(!state.events.includes('set-delete'));
    }
  });
}
