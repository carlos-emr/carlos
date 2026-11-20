/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');
const h = {assert(ok, message) { if (!ok) throw Error(message); }, sqlString: value => `'${String(value).replaceAll("'", "''")}'`};
const helperFile = path.join(__dirname, 'lib/billing-report-context.js');
const helperModule = {exports: {}};
vm.runInNewContext(fs.readFileSync(helperFile, 'utf8'), {module: helperModule, require: name => {
    if (name === './lib/authenticated-probe') return require('./lib/authenticated-probe');assert.equal(name, './playwright-harness'); return h;}}, {filename: helperFile});
const scheduleHelperFile = path.join(__dirname, 'lib/native-schedule-context.js');
const scheduleHelperModule = {exports: {}};
vm.runInNewContext(fs.readFileSync(scheduleHelperFile, 'utf8'), {module: scheduleHelperModule, URL,
  require: name => {
    if (name === './lib/authenticated-probe') return require('./lib/authenticated-probe');assert.equal(name, './playwright-harness'); return h;}}, {filename: scheduleHelperFile});
const owned = {multisites: true, clinic: 'FAKE Owned Clinic', providerNo: '972775', ohipNo: '123456'};
function nativeReport({context = owned, sites = ['none', owned.clinic], physicians = ['none', owned.providerNo], sticky = true, clearable = true, siteCount} = {}) {
  const state = {site: 'none', provider: '', options: context.multisites ? [] : physicians, start: '2026-04-23', end: '2026-10-20', patient: '', status: 'O', changed: [], autoSubmits: 0, reports: []};
  const option = value => typeof value === 'string' ? {value, disabled: false, closest: () => null} : {...value, closest: () => value.groupDisabled ? {disabled: true} : null};
  const options = values => ({evaluateAll: async fn => fn(values.map(option))});
  const providers = {waitFor: async () => {}, count: async () => 1, last() {return this;}, locator: () => options(state.options), inputValue: async () => state.provider,
    selectOption: async value => {assert(state.options.map(option).some(o => o.value === value && !o.disabled)); state.changed.push(['provider', value]); if (sticky) state.provider = value; if (context.multisites && state.start && state.end) state.autoSubmits++;}};
  const clinics = {count: async () => siteCount ?? (context.multisites ? 1 : 0), locator: () => options(sites), inputValue: async () => state.site,
    selectOption: async value => {state.site = value; state.options = physicians; state.changed.push(['site', value]);}};
  const date = key => ({fill: async value => {if (value || clearable) state[key] = value; state.changed.push([key, value]);}, inputValue: async () => state[key]});
  const frame = {state, waitForLoadState: async () => {}, locator(selector) {
    if (selector === 'select[name="providerview"]') return providers;
    if (selector === 'select[name="site"]') return clinics;
    if (selector === '#xml_vdate') return date('start');
    if (selector === '#xml_appointment_date') return date('end');
    if (selector === 'form[name="serviceform"]') return {waitFor: async () => {}};
    if (selector === 'input[name="demographicNo"]') return {fill: async value => {state.patient = value;}};
    if (selector === '#statusTypeAll') return {check: async () => {state.status = '%';}};
    if (selector === 'input[type="submit"][value="Create Report"]') return {click: async () => {state.reports.push({...state});}};
    if (selector === 'tr') return {filter: () => ({count: async () => Number(state.site === owned.clinic && state.provider === owned.providerNo && state.start === '2026-10-20' && state.end === '2026-10-20' && state.patient === '777' && state.status === '%'), first: () => ({locator: () => ({allInnerTexts: async () => ['W', 'A007A', '777']})})})};
    if (selector.startsWith('a[onclick')) return {};
    throw Error('Unexpected locator ' + selector);
  }};
  return frame;
}
const candidate = name => path.join(__dirname, name);
function load(filename, overrides = {}) {
  const module = {exports: {}};
  function requireStub(name) {
    if (name in overrides) return overrides[name];
    if (name === './lib/playwright-harness') return h;
    if (name === './lib/billing-report-context') return helperModule.exports;
    if (name === './lib/native-schedule-context') return scheduleHelperModule.exports;
    if (name === './lib/billing-site-context') return {selectBillingSiteAndProvider: async () => owned, validateBillingContext: (_sql, _operator, c) => c};
    if (name.startsWith('node:')) return require(name);
    return {};
  }
  requireStub.main = null;
  vm.runInNewContext(fs.readFileSync(filename, 'utf8'), {require: requireStub, module, exports: module.exports, process: {env: {}}, Date, console}, {filename});
  return module.exports;
}
const shared = () => load(process.env.ADJACENT_REPORT_SHARED || candidate('billing-on-invoice-third-party-playwright-checks.js'));
function reportFrame(options = {}) {
  const frame = nativeReport(options);
  const locate = frame.locator;
  frame.locator = selector => {
    if (selector === 'body') return {click: async () => {}};
    if (selector === '.flatpickr-calendar.open') return {waitFor: async () => {}};
    if (selector === '#statusTypeBillPatient') return {check: async () => {frame.state.status = 'P';}};
    return locate(selector);
  };
  return frame;
}
const reportAdmin = {waitForEvent: async () => {}, isClosed: () => false};
test('actual shared report restores exact owned filters after native context selection and submits once', async () => {
  const frame = reportFrame();
  await shared().runInvoiceReport(reportAdmin, frame, {date: '2004-02-28', endDate: '2004-03-01', statusId: 'statusTypeAll', billingProvider: owned.providerNo, billingContext: owned, demographic: '777'});
  assert.equal(frame.state.autoSubmits, 0); assert.equal(frame.state.reports.length, 1);
  const r = frame.state.reports[0];
  assert.deepEqual([r.site, r.provider, r.start, r.end, r.patient, r.status], [owned.clinic, owned.providerNo, '2004-02-28', '2004-03-01', '777', '%']);
});
test('actual report facade forwards native context and exact patient into the administration frame', async () => {
  const frame = reportFrame(); const link = {first() {return this;}, waitFor: async () => {}, click: async () => {}};
  const iframe = {...link, elementHandle: async () => ({contentFrame: async () => frame})};
  const admin = {locator: selector => selector === '#dynamic-content iframe' ? iframe : link, isClosed: () => false, waitForEvent: async () => {}};
  const module = load(candidate('billing-on-invoice-third-party-playwright-checks.js'), {'./lib/playwright-link-audit': {revealAuditLink: async () => {}}});
  const result = await module.openInvoiceReport({patient: '777'}, {admin, date: '2004-02-29', statusId: 'statusTypeBillPatient', billingProvider: owned.providerNo, billingContext: owned});
  assert.equal(result.frame, frame); assert.equal(frame.state.reports.length, 1); assert.equal(frame.state.reports[0].patient, '777'); assert.equal(frame.state.reports[0].status, 'P'); assert.equal(frame.state.reports[0].site, owned.clinic);
});
test('repeated date-window searches preserve the exact physician and never trigger a native onchange submission', async () => {
  const frame = reportFrame();
  for (const [date, endDate] of [['2004-03-08', '2004-03-12'], ['2004-02-29', '2004-02-29']]) {
    await shared().runInvoiceReport(reportAdmin, frame, {date, endDate, statusId: 'statusTypeAll', billingProvider: owned.providerNo, billingContext: owned, demographic: '777'});
  }
  assert.equal(frame.state.autoSubmits, 0); assert.equal(frame.state.reports.length, 2);
  assert.deepEqual(frame.state.reports.map(r => [r.start, r.end, r.site, r.provider]), [['2004-03-08', '2004-03-12', owned.clinic, owned.providerNo], ['2004-02-29', '2004-02-29', owned.clinic, owned.providerNo]]);
});
test('actual shared report rejects missing or foreign context before changing controls', async () => {
  for (const context of [undefined, {...owned, providerNo: '999998'}]) {
    const frame = reportFrame(); await assert.rejects(shared().runInvoiceReport(reportAdmin, frame, {date: '2004-02-29', statusId: 'statusTypeAll', billingProvider: owned.providerNo, billingContext: context}), /provider must match/);
    assert.deepEqual(frame.state.changed, []); assert.equal(frame.state.reports.length, 0);
  }
});
test('shared report never falls back to All, foreign physician or another clinic', async () => {
  for (const options of [{sites: ['none', 'Foreign']}, {physicians: ['all', '999998']}]) {
    const frame = reportFrame(options); await assert.rejects(shared().runInvoiceReport(reportAdmin, frame, {date: '2004-02-29', statusId: 'statusTypeAll', billingProvider: owned.providerNo, billingContext: owned}), /exact owned/);
    assert.equal(frame.state.reports.length, 0);
  }
});
function nativeSession({context = owned, countAfter = '0', selectionError = false} = {}) {
  let count = 0; const calls = []; let closed = false;
  const link = {first() {return this;}, count: async () => 1};
  const invoice = {isClosed: () => closed, close: async () => {closed = true; calls.push('closed');}};
  const s = {provider: '999998', patient: '777', marker: 'FAKE-PW0123456789abcdef', master: {getByRole: () => link},
    sql: {value: () => ++count === 1 ? '0' : countAfter}, popup: async () => {calls.push('popup'); return invoice;},
    step: async (_name, callback) => callback()};
  const physician = {...owned, selectClinic: async () => {calls.push('clinic'); if (selectionError) throw Error('native selection refused');}, assertContext: c => assert.equal(c, context)};
  const module = load(candidate('billing-on-invoice-third-party-playwright-checks.js'), {'./lib/billing-site-context': {selectBillingSiteAndProvider: async () => {calls.push('provider'); return context;}, validateBillingContext: (_sql, _operator, c) => {calls.push('validated'); return c;}}});
  return {s, physician, calls, closed: () => closed, module};
}
test('native context is read before headers and closes popup without saving a claim', async () => {
  const c = nativeSession(); assert.equal(await c.module.captureNativeInvoiceContext(c.s, c.physician), owned);
  assert.deepEqual(c.calls, ['popup', 'clinic', 'provider', 'validated', 'closed']); assert(c.closed());
});
test('native context refuses foreign provider/OHIP and unexpected header writes, always closing popup', async () => {
  for (const options of [{context: {...owned, providerNo: '999998'}}, {context: {...owned, ohipNo: '654321'}}, {countAfter: '1'}, {selectionError: true}]) {
    const c = nativeSession(options); await assert.rejects(c.module.captureNativeInvoiceContext(c.s, c.physician)); assert(c.closed());
  }
});
function billingStart() {
  const calls = []; const stop = Error('captured header'); const cleanups = [];
  let header;
  const s = {provider: '999998', patient: '777', marker: 'FAKE-PW0123456789abcdef', context: {}, master: {getByRole: () => ({first() {return this;}, count: async () => 1})},
    popup: async () => ({isClosed: () => false, close: async () => {calls.push('closed');}}),
    cleanup: fn => {cleanups.push(fn); calls.push('ordinary cleanup');}, step: async (_name, callback) => callback(),
    sql: {value(q) {if (/SELECT COUNT\(DISTINCT site.site_id\)/.test(q)) {
      assert(q.includes("site.name='FAKE Owned Clinic'") && q.includes("membership.provider_no='999998'"));
      calls.push('native schedule membership'); return '1';
    } if (/FROM billingservice/.test(q)) return '50.00'; if (/^SELECT COUNT/.test(q)) return '0'; if (/INSERT INTO billing_on_3rdPartyAddress/.test(q)) return '20'; if (/INSERT INTO billing_on_cheader1/.test(q)) {calls.push('header'); header = q; throw stop;} throw Error('Unexpected SQL ' + q);}, rows: () => {throw Error('Unexpected provider/site membership lookup');}}};
  s.schedule = {url: () => 'https://127.0.0.1/carlos/provider/providercontrol?displaymode=day', locator(selector) {
    const fields = {'#scheduleLoggedInProviderNo': s.provider, '#scheduleCurrentDate': '2026-10-20', '#scheduleVisibleProviderNos': s.provider};
    if (Object.hasOwn(fields, selector)) return {inputValue: async () => {
      if (selector === '#scheduleLoggedInProviderNo') calls.push('native schedule');
      return fields[selector];
    }};
    assert.equal(selector, 'select#site[name="site"]');
    return {count: async () => 1, inputValue: async () => owned.clinic, locator: () => ({evaluateAll: async fn =>
      fn(['none', owned.clinic].map(value => ({value, disabled: false, closest: () => null})))})};
  }};
  const dependentSession = Object.create(s); dependentSession.cleanup = fn => {calls.push('dependent cleanup'); cleanups.push(fn);};
  const factory = session => {assert.equal(session, s); assert.equal(calls.length, 0); calls.push('physician'); return {...owned, dependentSession, selectClinic: async () => {calls.push('clinic');}, assertContext: c => {assert.equal(c, owned); calls.push('context');}};};
  const overrides = {'./lib/owned-billing-physician': {createOwnedBillingPhysician: factory}, './lib/billing-site-context': {selectBillingSiteAndProvider: async () => {calls.push('native'); return owned;}, validateBillingContext: (_sql, _operator, c) => c}};
  const invoice = load(candidate('billing-on-invoice-third-party-playwright-checks.js'), overrides);
  return {s, calls, stop, header: () => header, overrides, invoice};
}
test('actual third-party workflow owns its physician first and binds the native clinic before its private header INSERT', async () => {
  const c = billingStart(); await assert.rejects(c.invoice.workflow(c.s), e => e === c.stop);
  assert.equal(c.calls[0], 'physician'); assert(c.calls.indexOf('native') < c.calls.indexOf('header')); assert(c.calls.includes('dependent cleanup'));
  assert(c.header().endsWith("'999998', 'FAKE Owned Clinic'); SELECT LAST_INSERT_ID()")); assert(c.header().includes("'972775'")); assert(c.header().includes("'PAT'")); assert(c.header().includes('50.00, 0.00'));
});
test('actual payment-status workflow also binds a per-case physician before its first private header', async () => {
  const c = billingStart(); const payment = load(candidate('billing-on-payment-status-playwright-checks.js'), {...c.overrides, './billing-on-invoice-third-party-playwright-checks': c.invoice});
  await assert.rejects(payment.workflow(c.s), e => e === c.stop);
  assert.equal(c.calls[0], 'physician'); assert(c.calls.indexOf('native') < c.calls.indexOf('header')); assert(c.calls.includes('dependent cleanup'));
  assert(c.calls.indexOf('native') < c.calls.indexOf('native schedule'));
  assert(c.calls.indexOf('native schedule') < c.calls.indexOf('native schedule membership'));
  assert(c.calls.indexOf('native schedule membership') < c.calls.indexOf('header'));
  assert(c.header().endsWith("'999998', 'FAKE Owned Clinic'); SELECT LAST_INSERT_ID()"));
});
function actualOhipFixture() {
  const queries = []; const cleanups = []; let id = 40;
  const sql = {rows(q) {assert(q.includes('information_schema.columns')); return ['provider_no', 'ohip_no', 'last_name', 'first_name', 'status', 'comments'].map(x => [x]);},
    execute(q) {queries.push(q);}, value(q) {queries.push(q); if (/^SELECT COUNT\(\*\) FROM provider WHERE provider_no='970001'/.test(q)) return '1'; if (/SELECT hin FROM demographic/.test(q)) return '9000000001'; if (/^INSERT INTO billing_on_(cheader1|item)/.test(q)) return String(++id); return '0';}};
  const module = load(candidate('billing-on-ohip-simulation-report-playwright-checks.js'), {'node:crypto': {randomInt: () => 1}, './lib/billing-site-context': {validateBillingContext: (_sql, _operator, c) => {if (c.stale) throw Error('stale clinic'); return c;}}});
  const fixture = module.createBillingFixture({sql, provider: '999998', patient: '777', marker: 'FAKE-PW0123456789abcdef', cleanup: fn => cleanups.push(fn)});
  return {fixture, queries, cleanups};
}
const claimInput = {tag: 'LEAP', date: '2004-02-29', status: 'O', items: [{code: 'A007A', fee: '10.25'}, {code: 'K030A', fee: '20.50'}]};
test('actual OHIP fixture binds native clinic while retaining its own group/HIN/provider and exact date/total/items', () => {
  const c = actualOhipFixture(); const context = {...owned, providerNo: c.fixture.providerNo, ohipNo: c.fixture.ohipNo};
  const row = c.fixture.addClaim({...claimInput, billingContext: context}); const q = c.queries.find(q => /^INSERT INTO billing_on_cheader1/.test(q));
  assert(q.endsWith("'999998', 'FAKE Owned Clinic'); SELECT LAST_INSERT_ID()")); assert(q.includes("'970001'")); assert(q.includes("'9000000001'")); assert(q.includes("'2004-02-29'")); assert(q.includes('30.75, 0.00')); assert.equal(row.total, '30.75'); assert.equal(c.fixture.groupNo, '8001'); assert.equal(c.queries.join('\n').match(/INSERT INTO billing_on_item/g).length, 2);
});
test('existing OHIP-fixture callers and explicit nonmultisite contexts retain NULL clinic', () => {
  for (const explicit of [false, true]) {
    const c = actualOhipFixture(); const billingContext = explicit ? {...owned, providerNo: c.fixture.providerNo, ohipNo: c.fixture.ohipNo, multisites: false, clinic: null} : undefined;
    c.fixture.addClaim({...claimInput, billingContext}); assert(c.queries.find(q => /^INSERT INTO billing_on_cheader1/.test(q)).endsWith("'999998', NULL); SELECT LAST_INSERT_ID()"));
  }
});
test('OHIP claim refuses a foreign provider, OHIP number or stale clinic before inserting any header', () => {
  for (const delta of [{providerNo: '123456'}, {ohipNo: '654321'}, {stale: true}]) {
    const c = actualOhipFixture(); const context = {...owned, providerNo: c.fixture.providerNo, ohipNo: c.fixture.ohipNo, ...delta};
    assert.throws(() => c.fixture.addClaim({...claimInput, billingContext: context})); assert(!c.queries.some(q => /^INSERT INTO billing_on_cheader1/.test(q)));
  }
});
test('actual date-boundary workflow captures its existing OHIP context before all seven headers and passes it to the report', async () => {
  const calls = []; const claims = []; const stop = Error('report inspected'); const fixture = {...owned, addClaim: input => {assert(calls.includes('native')); assert.equal(input.billingContext, owned); claims.push(input); return {id: String(claims.length), total: '10.00'};}};
  const link = {first() {return this;}, waitFor: async () => {}, click: async () => {}}; const frame = {locator: () => link}; const iframe = {...link, elementHandle: async () => ({contentFrame: async () => frame})};
  // Let the actual first report step use the native administration frame; stop in its report helper before assertions.
  const admin = {locator: s => s === '#dynamic-content iframe' ? iframe : link};
  const m2 = load(candidate('boundary-date-billing-claims-playwright-checks.js'), {'./lib/playwright-link-audit': {revealAuditLink: async () => {}}, './lib/gap-billing-support': {scheduleFee: () => '10.00'}, './billing-on-ohip-simulation-report-playwright-checks': {createBillingFixture: () => fixture, openAdministration: async () => admin}, './billing-on-invoice-third-party-playwright-checks': {captureNativeInvoiceContext: async (_s, p) => {assert.equal(p, fixture); calls.push('native'); return owned;}, runInvoiceReport: async (_a, _f, input) => {assert.equal(input.billingContext, owned); assert.equal(input.billingProvider, owned.providerNo); assert.equal(claims.length, 7); assert.deepEqual([input.date, input.endDate], ['2004-03-08', '2004-03-12']); throw stop;}}});
  await assert.rejects(m2.workflow({sql: {}, patient: '777', step: async (_n, callback) => callback()}), e => e === stop);
  assert.deepEqual(claims.map(c => c.date), ['2004-02-28', '2004-02-29', '2004-03-01', '2004-03-07', '2004-03-08', '2004-03-12', '2004-03-13']); assert(claims.every(c => c.status === 'O' && c.items[0].code === 'A007A'));
});
