/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const {test} = require('node:test');
const repo = process.env.NATIVE_SCHEDULE_REPO_ROOT || path.resolve(__dirname, '..');
const callerRoot = process.env.NATIVE_SCHEDULE_CALLER_ROOT || __dirname;
const q = value => `'${String(value).replaceAll("'", "''")}'`;
const h = {assert: (ok, message) => assert(ok, message), sqlString: q,
  SkipCheck: class SkipCheck extends Error {}, insertId: (sql, query) => sql.value(query),
  gotoApp: async () => {}, assertNotErrorPage: async () => {}};
function load(file, imports = {}, env = {}, expose = '') {
  const m = {exports: {}};
  const request = name => {
    if (Object.hasOwn(imports, name)) return imports[name];
    if (name === './lib/playwright-harness' || name === './playwright-harness') return h;
    if (name.startsWith('node:')) return require(name);
    return {};
  };
  request.main = null;
  vm.runInNewContext(fs.readFileSync(file, 'utf8') + expose,
    {module: m, exports: m.exports, require: request, process: {env}, Date, URL, URLSearchParams, console, Buffer,
      __dirname: path.dirname(file)}, {filename: file});
  return m.exports;
}
const helper = load(path.join(__dirname, 'lib/native-schedule-context.js'));
const rawSite = "North's & East";
function nativeSession({current = rawSite, sites = [rawSite, 'Other'], multisites = true,
  provider = '999998', actor = provider, visible = provider, date = '2004-02-29', scope = '1', week = false} = {}) {
  const calls = [];
  const state = {current, sites, multisites, actor, visible, date};
  const option = x => typeof x === 'string' ? {value: x, disabled: false, closest: () => null}
    : {...x, closest: () => x.groupDisabled ? {disabled: true} : null};
  const schedule = {url: () => `https://127.0.0.1/carlos/provider/providercontrol?displaymode=day${week ? '&weekView=true' : ''}`,
    locator(selector) {
      const values = {'#scheduleLoggedInProviderNo': 'actor', '#scheduleVisibleProviderNos': 'visible', '#scheduleCurrentDate': 'date'};
      if (values[selector]) return {inputValue: async () => state[values[selector]]};
      if (selector === 'select#site[name="site"]') return {count: async () => Number(state.multisites),
        inputValue: async () => state.current, locator: () => ({evaluateAll: async fn => fn(state.sites.map(option))}),
        selectOption: () => {throw Error('Native preferences must never be changed');}};
      throw Error('Unexpected native locator ' + selector);
    }};
  const sql = {value(query) {calls.push(query); return scope;}, execute() {throw Error('Capture must be read-only');}};
  return {schedule, sql, provider, calls, state};
}
test('named site, raw spelling, date and exact visible logged-in provider are read without changing controls', async () => {
  const s = nativeSession();
  const got = await helper.captureScheduleContext(s, {preferredSite: 'Other'});
  assert.deepEqual({...got}, {providerNo: '999998', date: '2004-02-29', location: rawSite, multisites: true});
  assert(Object.isFrozen(got)); assert.equal(s.calls.length, 1);
  assert(s.calls[0].includes(`site.name=${q(rawSite)}`)); assert(s.calls[0].includes("membership.provider_no='999998'"));
});
test('an already displayed All Clinics view still binds a real offered authorized site without selecting All', async () => {
  for (const preferredSite of [undefined, 'Other']) {
    const s = nativeSession({current: 'none'});
    const got = await helper.captureScheduleContext(s, {preferredSite});
    assert.equal(got.location, preferredSite || rawSite); assert.equal(s.state.current, 'none');
    await helper.assertScheduleContext(s, got);
  }
});
test('native nonmultisite view preserves its legacy blank location without inventing a clinic', async () => {
  const s = nativeSession({multisites: false}); const got = await helper.captureScheduleContext(s);
  assert.equal(got.location, ''); assert.equal(got.multisites, false); assert.equal(s.calls.length, 0);
});
for (const [label, options] of [
  ['foreign logged-in provider', {actor: '2'}], ['provider column missing', {visible: '2'}],
  ['provider column ambiguous', {visible: '999998,999998'}], ['invalid date', {date: '2004-02-30'}],
  ['week view', {week: true}], ['trimmed location cannot match a padded native site', {current: ' Padded ', sites: [' Padded ']}], ['no named site', {sites: ['none']}], ['duplicate sites', {sites: [rawSite, rawSite]}],
  ['selected foreign site', {current: 'Foreign'}], ['disabled site', {sites: [{value: rawSite, disabled: true}]}],
  ['disabled optgroup', {sites: [{value: rawSite, disabled: false, groupDisabled: true}]}],
  ['inactive or foreign membership', {scope: '0'}], ['ambiguous database site name', {scope: '2'}],
]) test(`${label} refuses before any fixture write`, async () => {
  await assert.rejects(helper.captureScheduleContext(nativeSession(options)));
});
test('All Clinics cannot bind a preferred site absent from native options', async () => {
  await assert.rejects(helper.captureScheduleContext(nativeSession({current: 'none'}), {preferredSite: 'Foreign'}));
});
test('reload context refuses changed date, named site, actor, visibility or multisite mode', async () => {
  for (const change of [{date: '2004-03-01'}, {current: 'Other'}, {actor: '2'}, {visible: '2'}, {multisites: false}]) {
    const s = nativeSession(); const got = await helper.captureScheduleContext(s);
    Object.assign(s.state, change); await assert.rejects(helper.assertScheduleContext(s, got));
  }
});

// Execute the exact native JSP condition. The only Java-to-JS adjustment is
// String.equals; no independent approximation of appointment visibility is used.
const jsp = fs.readFileSync(path.join(repo, 'src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp'), 'utf8');
const expression = jsp.match(/if \((!bMultisites \|\| \(selectedSite == null && CurrentSiteMap\.get\(sitename\) != null\) \|\| sitename\.equals\(selectedSite\))\)/)?.[1];
assert(expression, 'Actual native schedule site predicate must be found');
assert(jsp.includes('String sitename = appointment.getLocation() != null ? appointment.getLocation().trim() : "";'),
  'Actual native schedule must trim the appointment location before its predicate');
const predicate = new Function('bMultisites', 'selectedSite', 'CurrentSiteMap', 'sitename',
  `return ${expression.replace('sitename.equals(selectedSite)', '(sitename === selectedSite)')};`);
const nativeVisible = (multisites, selected, offered, location) => predicate(multisites, selected, offered, location == null ? '' : location.trim());
test('actual native predicate hides legacy blank appointment rows and admits the exact offered named location', () => {
  for (const selected of [null, rawSite]) {
    assert.equal(nativeVisible(true, selected, new Map([[rawSite, 'Y']]), ''), false);
    assert.equal(nativeVisible(true, selected, new Map([[rawSite, 'Y']]), rawSite), true);
  }
  assert.equal(nativeVisible(true, 'Other', new Map([[rawSite, 'Y']]), rawSite), false);
});
test('scoped seeder registers cleanup before its INSERT, preserves native fields and refuses DML before browser drain', async () => {
  const s = nativeSession(); const context = await helper.captureScheduleContext(s);
  let connected = true, cleanup; const events = [];
  Object.assign(s, {patient: '777', marker: 'FAKE-PW0123456789abcdef',
    context: {browser: () => ({isConnected: () => connected})},
    cleanup(fn) {events.push('registered'); cleanup = fn;}});
  s.sql = {value(query) {events.push(query); if (/SELECT ADDTIME/.test(query)) return '14:29:00';
    if (/INSERT INTO appointment/.test(query)) return '77'; return '0';}, execute(query) {events.push(query);}};
  assert.equal(helper.seedScheduleAppointment(s, context), '77');
  const insert = events.findIndex(value => /INSERT INTO appointment/.test(value));
  assert(events.indexOf('registered') < insert);
  const row = appointmentRow(events[insert]);
  assert.equal(row.provider_no, s.provider); assert.equal(row.appointment_date, context.date); assert.equal(row.location, rawSite);
  assert.throws(cleanup, /browser close is unconfirmed/); assert(!events.some(value => /^DELETE/.test(value)));
  connected = false; cleanup(); assert(events.some(value => /^DELETE/.test(value)));
});
test('scoped seeder refuses mismatched provider or blank/placeholder multisite location before registering cleanup', () => {
  const context = {providerNo: '999998', date: '2004-02-29', location: rawSite, multisites: true};
  const s = {provider: '999998', cleanup() {throw Error('no registration permitted');}};
  for (const delta of [{providerNo: '2'}, {location: ''}, {location: 'none'}])
    assert.throws(() => helper.seedScheduleAppointment(s, {...context, ...delta}), /captured native/);
});
function splitValues(text) {
  const values = []; let start = 0, quote = false, depth = 0;
  for (let i = 0; i < text.length; i++) {
    if (text[i] === "'") {if (quote && text[i + 1] === "'") {i++; continue;} quote = !quote;}
    if (!quote) {if (text[i] === '(') depth++; if (text[i] === ')') depth--; if (text[i] === ',' && depth === 0) {values.push(text.slice(start, i).trim()); start = i + 1;}}
  }
  values.push(text.slice(start).trim()); return values;
}
function appointmentRow(query) {
  const match = query.match(/INSERT INTO appointment\s*\((.*?)\)\s*VALUES\s*\(([\s\S]*?)\)(?:;|$)/s);
  assert(match, 'Actual fixture INSERT must be parsed');
  const columns = match[1].split(',').map(s => s.trim());
  const values = splitValues(match[2]); assert.equal(columns.length, values.length);
  return Object.fromEntries(columns.map((key, i) => [key, values[i].startsWith("'")
    ? values[i].slice(1, -1).replaceAll("''", "'") : values[i]]));
}

function composition(file, pin = '') {
  const s = nativeSession(); const rows = []; const events = []; let id = 70;
  const stop = Error('native owned appointment link reached');
  const oldG = load(path.join(repo, 'scripts/lib/gap-billing-support.js'));
  const nativeLookup = selector => {
    const match = selector.match(/appointment(?:_no)?=(\d+)/); const found = rows.find(r => r.id === match?.[1]);
    const visible = found && found.provider_no === s.provider && found.appointment_date === s.state.date
      && nativeVisible(true, s.state.current === 'none' ? null : s.state.current,
        new Map(s.state.sites.map(value => [typeof value === 'string' ? value : value.value, 'Y'])), found.location || '');
    return {first() {return this;}, count: async () => Number(Boolean(visible)),
      waitFor: async () => {if (!visible) throw Error('native appointment row not rendered'); if (/apptStatus|masterBtn/.test(selector)) throw stop;}};
  };
  const locate = s.schedule.locator.bind(s.schedule);
  s.schedule.locator = selector => /^a\[onclick|^a\.apptStatus|^a\.masterBtn/.test(selector) ? nativeLookup(selector)
    : selector === 'span.dateAppointment' ? {waitFor: async () => {}} : locate(selector);
  s.schedule.reload = async () => {}; s.schedule.waitForLoadState = async () => {};
  s.marker = 'FAKE-PW0123456789abcdef'; s.patient = '777'; s.config = {testUser: 'synthetic'};
  const cleanups = []; s.state.connected = false;
  s.recorder = {requestFailures: []}; s.context = {browser: () => ({isConnected: () => s.state.connected})};
  s.cleanup = callback => {events.push('cleanup'); cleanups.push(callback);};
  s.step = async (_label, body) => {if (file.startsWith('csrf-') && events.includes('first-step')) throw stop;
    const value = await body(); events.push('first-step'); return value;};
  s.popup = async () => {throw stop;};
  s.sql = {value(query) {
    events.push(query);
    if (/^INSERT INTO appointment\b/.test(query.trim())) {const row = appointmentRow(query.trim());
      if (row.appointment_date === 'CURDATE()') row.appointment_date = s.state.date;
      row.id = String(++id); rows.push(row); return row.id;}
    if (/SELECT COUNT\(DISTINCT site.site_id\)/.test(query)) return '1';
    if (/^SELECT ADDTIME/.test(query)) {
      const clock = query.match(/'([0-9]{2}):([0-9]{2}):([0-9]{2})'/); assert(clock);
      const minutes = Number(clock[1]) * 60 + Number(clock[2]) + 14;
      return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}:00`;
    }
    if (/SELECT COUNT.*FROM billingservice/.test(query)) return '0';
    if (/FROM billingservice/.test(query)) return '50.00';
    if (/^SELECT appointment_no FROM billing_on_cheader1/.test(query)) return rows[0].id;
    if (query === 'SELECT CURDATE()') return s.state.date;
    if (/^SELECT status FROM appointment WHERE appointment_no=/.test(query))
      return rows.find(row => row.id === query.match(/appointment_no=(\d+)/)[1]).status;
    if (/^SELECT CONCAT_WS.*h.status/.test(query)) return 'O|O';
    if (/^SELECT COALESCE\(NULLIF\(TRIM\(e.signature\)/.test(query)) return 'Synthetic Signature';
    if (/^INSERT INTO billing_on_eareport/.test(query)) return '31';
    if (/SELECT COUNT/.test(query)) return '0';
    throw Error('Unexpected setup query');
  }, rows(query) {
    if (/FROM ProviderPreference/.test(query)) return query.includes('SELECT CURDATE(),')
      ? [[s.state.date, '8', '18', '15']] : [['8', '18', '15']];
    if (/FROM appointment_status/.test(query)) return [['t', 'star.gif', 'To Do'], ['H', 'here.gif', 'Here']];
    throw Error('Unexpected setup rows');
  }, execute(query) {events.push(query);}, dispose() {events.push('disposed');}};
  const own = {providerNo: '970001', ohipNo: '123456'};
  const g = {...oldG, scheduleFee: () => '50.00', registerOwnedBillCleanup: () => {}};
  const invoice = {captureNativeInvoiceContext: async () => ({...own, clinic: rawSite, multisites: true}),
    seedOwnedBill: () => ({...own, provider: own.providerNo, ohip: own.ohipNo, headerId: '100', itemId: '101'}),
    billDate: () => '2030-01-01', money: value => Number(value).toFixed(2)};
  const imports = {'./lib/native-schedule-context': helper, './lib/gap-billing-support': g,
    './lib/owned-billing-physician': {createOwnedBillingPhysician: () => ({...own, dependentSession: s})},
    './billing-on-invoice-third-party-playwright-checks': invoice,
    './billing-on-ohip-simulation-report-playwright-checks': {createBillingFixture: () => own},
    'node:fs': {...fs, readFileSync: filename => /oscarResources_en.properties$/.test(filename)
      ? ['encounter.class.EctSaveEncounterAction.msgSigned', 'encounter.class.EctSaveEncounterAction.msgSigBy',
        'encounter.class.EctSaveEncounterAction.msgVerAndSig', 'encounter.pastObservationDateError.msg',
        'encounter.observationDateInvalid.msg'].map(key => key + '=Synthetic').join('\n') : fs.readFileSync(filename, 'utf8')}};
  const expose = file.startsWith('session-state') ? '\nmodule.exports.healthTrackerVisits = healthTrackerVisits;' : '';
  const caller = load(path.join(callerRoot, file), imports, {ECHART_VERIFY_PIN: pin}, expose);
  const run = () => file.startsWith('session-state') ? caller.healthTrackerVisits(s, '777', '778') : caller.workflow(s);
  return {s, rows, events, stop, run, cleanups};
}
const callers = [
  ['billing-on-payment-status-playwright-checks.js', '', 1],
  ['csrf-runtime-form-day-sheet-unbill-playwright-checks.js', '', 1],
  ['gap-billing-bill-form-options-playwright-checks.js', '', 2],
  ['gap-billing-bill-types-playwright-checks.js', '', 7],
  ['billing-review-failed-calculation-playwright-checks.js', '', 1],
  ['echart-note-verify-appointment-status-playwright-checks.js', '', 2],
  ['echart-note-verify-appointment-status-playwright-checks.js', 'archive', 2],
  ['echart-note-verify-appointment-status-playwright-checks.js', 'billing', 2],
  ['session-state-two-window-isolation-playwright-checks.js', '', 2],
];
for (const [file, pin, count] of callers) test(`actual ${file} ${pin} binds native date/provider/site and reaches its real owned link`, async () => {
  const c = composition(file, pin);
  await assert.rejects(c.run(), error => error === c.stop);
  assert.equal(c.rows.length, count);
  for (const row of c.rows) {
    assert.equal(row.provider_no, c.s.provider); assert.equal(row.appointment_date, c.s.state.date);
    assert.equal(row.location, rawSite);
  }
  const insert = c.events.findIndex(e => typeof e === 'string' && /INSERT INTO appointment/.test(e));
  assert(c.events.slice(0, insert).some(e => typeof e === 'string' && /SELECT COUNT\(DISTINCT site.site_id\)/.test(e)));
});

// Execute the unchanged default finalizer: it deliberately continues callbacks
// after close/child failures, so each changed fixture must protect its own DML.
const finalizer = load(path.join(repo, 'scripts/lib/workflow-session.js')).cleanupOwnedWorkflow;
for (const [file, pin] of callers.filter(([file]) => !file.startsWith('session-state'))) {
  test(`actual finalizer close rejection retains ${file} ${pin} appointment rows`, async () => {
    const c = composition(file, pin); await assert.rejects(c.run());
    assert(c.rows.length > 0, 'Actual fixture callbacks must exist before teardown');
    let laterCallback = false;
    c.cleanups.unshift(() => {laterCallback = true;});
    c.s.state.connected = true;
    const before = c.events.length;
    await assert.rejects(finalizer({browser: {close: async () => {throw Error('synthetic close refusal');}},
      sql: c.s.sql, cleanups: c.cleanups}));
    assert.equal(laterCallback, true, 'The real finalizer must continue after a child refusal');
    assert(!c.events.slice(before).some(query => typeof query === 'string'
      && /DELETE FROM appointment(?:Archive)?\b/.test(query)));
  });
}
for (const mode of ['connected', 'child-failure', 'success']) test(`actual session parent/appointment finalizer composition ${mode}`, async () => {
  const callbacks = [], writes = []; let disposed = false;
  const connected = mode === 'connected';
  const s = {provider: '999998', marker: 'FAKE-PW0123456789abcdef',
    context: {browser: () => ({isConnected: () => connected})}, cleanup: callback => callbacks.push(callback),
    sql: {value(query) {if (/INSERT INTO demographic/.test(query)) return '778';
      if (/AND last_name=/.test(query)) return '1'; return '0';},
    execute(query) {if (mode === 'child-failure' && /DELETE FROM appointmentArchive/.test(query)) throw Error('synthetic appointment child refusal'); writes.push(query);},
    dispose() {disposed = true;}}};
  const source = load(path.join(callerRoot, 'session-state-two-window-isolation-playwright-checks.js'), {}, {},
    '\nmodule.exports.fixtureCleanup = {createSecondPatient, ownAppointments};').fixtureCleanup;
  let assertAppointmentsCleaned;
  const B = source.createSecondPatient(s, () => {if (assertAppointmentsCleaned) assertAppointmentsCleaned();});
  assert.equal(B, '778');
  assertAppointmentsCleaned = source.ownAppointments(s, ['777', B]);
  let continued = false; callbacks.unshift(() => {continued = true;});
  const run = () => finalizer({browser: {close: async () => {if (connected) throw Error('synthetic close refusal');}},
    sql: s.sql, cleanups: callbacks});
  if (mode === 'success') {await run(); assert(writes.some(query => /DELETE FROM demographic WHERE demographic_no=778/.test(query)));}
  else {await assert.rejects(run); assert(!writes.some(query => /DELETE FROM demographic/.test(query)));}
  if (connected) assert.equal(writes.length, 0);
  assert.equal(continued, true); assert.equal(disposed, true);
});
