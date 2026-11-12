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
vm.runInNewContext(fs.readFileSync(helperFile, 'utf8'), {module: helperModule, require: name => {assert.equal(name, './playwright-harness'); return h;}}, {filename: helperFile});
const {selectBillingReportContext} = helperModule.exports;
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
test('native report selects exact owned clinic before physician while both dates are empty', async () => {
  const frame = nativeReport(); await selectBillingReportContext(frame, owned);
  assert.deepEqual(frame.state.changed, [['start', ''], ['end', ''], ['site', owned.clinic], ['provider', owned.providerNo]]);
  assert.equal(frame.state.autoSubmits, 0); assert.equal(frame.state.provider, '972775');
});
test('nonmultisite report preserves NULL clinic and the exact owned physician', async () => {
  const context = {...owned, multisites: false, clinic: null}; const frame = nativeReport({context});
  await selectBillingReportContext(frame, context); assert.equal(frame.state.provider, owned.providerNo); assert(!frame.state.changed.some(c => c[0] === 'site'));
});
test('missing, duplicate or disabled owned clinic refuses instead of selecting another site', async () => {
  for (const sites of [['none', 'Foreign'], ['none', owned.clinic, owned.clinic], ['none', {value: owned.clinic, disabled: true}]]) {
    const frame = nativeReport({sites}); await assert.rejects(selectBillingReportContext(frame, owned), /exact owned clinic/); assert(!frame.state.changed.some(c => c[0] === 'provider'));
  }
});
test('missing, duplicate, disabled or group-disabled physician never falls back to All or operator', async () => {
  for (const physicians of [['none', 'all', '999998'], [owned.providerNo, owned.providerNo], [{value: owned.providerNo, disabled: true}], [{value: owned.providerNo, groupDisabled: true}]]) {
    const frame = nativeReport({physicians}); await assert.rejects(selectBillingReportContext(frame, owned), /exact owned physician/); assert(!frame.state.changed.some(c => c[0] === 'provider'));
  }
});
test('clinic mode disagreement refuses before changing native controls', async () => {
  const frame = nativeReport({siteCount: 0}); await assert.rejects(selectBillingReportContext(frame, owned), /clinic mode differs/); assert.deepEqual(frame.state.changed, []);
});
test('date controls that do not clear refuse before clinic or physician selection', async () => {
  const frame = nativeReport({clearable: false}); await assert.rejects(selectBillingReportContext(frame, owned), /date did not clear/); assert(!frame.state.changed.some(c => c[0] === 'site'));
});
test('a physician selection that does not persist fails closed', async () => {
  await assert.rejects(selectBillingReportContext(nativeReport({sticky: false}), owned), /physician selection did not persist/);
});
test('actual correction caller submits the complete owned report context and preserves its row assertion', async () => {
  const caller = process.env.BILLING_REPORT_CALLER || path.join(__dirname, 'billing-on-correction-delete-playwright-checks.js');
  const frame = nativeReport(); const done = Error('report completed'); const module = {exports: {}};
  const link = {first() {return this;}, waitFor: async () => {}, click: async () => {}};
  const iframe = {first() {return this;}, waitFor: async () => {}, elementHandle: async () => ({contentFrame: async () => frame})};
  const admin = {locator: selector => selector === '#dynamic-content iframe' ? iframe : link, waitForEvent: async () => frame, isClosed: () => false, close: async () => {}};
  let billId = 40;
  let dependentSession;
  const lifecycle = {created: 0, clinics: 0, contexts: 0, claimCleanups: 0};
  const requireStub = name => {
    if (name === './lib/playwright-harness') return h;
    if (name === './lib/playwright-ui') return {clickOpensPopupOrNavigates: async () => ({page: admin})};
    if (name === './lib/playwright-link-audit') return {revealAuditLink: async () => {}};
    if (name === './lib/workflow-session') return {};
    if (name === './lib/owned-billing-physician') return {createOwnedBillingPhysician(session) {
      lifecycle.created++;
      dependentSession = Object.create(session);
      dependentSession.cleanup = callback => {assert.equal(typeof callback, 'function'); lifecycle.claimCleanups++;};
      return {providerNo: owned.providerNo, dependentSession,
        async selectClinic() {lifecycle.clinics++;},
        assertContext(context) {assert.equal(context, owned); lifecycle.contexts++;}};
    }};
    if (name === './lib/billing-site-context') return {
      selectBillingSiteAndProvider: async (page, preferred) => {assert.equal(lifecycle.created, 1); assert.equal(lifecycle.clinics, 1); assert.equal(preferred, owned.providerNo); return owned;},
      validateBillingContext: (sql, operator, context) => {assert.equal(operator, '999998'); assert.equal(context, owned); return owned;}};
    if (name === './lib/billing-report-context') return helperModule.exports;
    if (name === './billing-on-invoice-third-party-playwright-checks') return {seedOwnedBill: (session, options) => {assert.equal(session, dependentSession); assert.equal(options.billingContext, owned); assert.equal(lifecycle.contexts, 1); session.cleanup(() => {}); return {headerId: String(++billId), itemId: '51', provider: owned.providerNo};}, openHistory: async () => ({}), billDate: () => '2026-10-20', money: value => Number(value).toFixed(2), fillDate: async (page, selector, value) => page.locator(selector).fill(value)};
    throw Error('Unexpected import ' + name);
  };
  requireStub.main = null;
  vm.runInNewContext(fs.readFileSync(caller, 'utf8'), {require: requireStub, module, process: {env: {}}, console}, {filename: caller});
  const session = {provider: '999998', patient: '777', marker: 'FAKE-PWowned', master: {getByRole: () => link}, schedule: {locator: () => link}, popup: async () => ({isClosed: () => false, close: async () => {}}), context: {}, recorder: {},
    sql: {value(query) {if (/FROM billingservice/.test(query)) return '10.00'; if (/CONCAT_WS/.test(query)) return 'W|HCP|20.00|777|972775|2026-10-20|'; return /WHERE id=/.test(query) ? '1' : '0';}},
    async step(name, callback) {if (name.startsWith('the native invoice')) return callback(); if (name.includes('Invoice Reports lists')) {await callback(); throw done;}}
  };
  await assert.rejects(module.exports.workflow(session), error => error === done);
  assert.equal(frame.state.autoSubmits, 0); assert.equal(frame.state.reports.length, 1);
  assert.equal(frame.state.reports[0].site, owned.clinic); assert.equal(frame.state.reports[0].provider, owned.providerNo);
  assert.deepEqual(lifecycle, {created: 1, clinics: 1, contexts: 1, claimCleanups: 3});
});
test('actual shipped report template binds physician options to clinic and auto-submits only when both dates are populated', () => {
  const root = process.env.BILLING_REPORT_APP_ROOT || path.resolve(__dirname, '..');
  const jsp = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingONStatus.jsp'), 'utf8');
  assert(jsp.includes('providerSelect.innerHTML = ""'));
  assert(jsp.includes('(_providers[sel.value] || []).forEach'));
  assert(jsp.includes('onchange="changeProvider(true);"'));
  assert(/xml_vdate"\).value.length > 0[\s\S]*xml_appointment_date"\).value.length > 0[\s\S]*document.serviceform.submit\(\)/.test(jsp));
});
