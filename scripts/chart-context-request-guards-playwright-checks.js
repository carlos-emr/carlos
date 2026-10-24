#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
/*
 * Issue #4811: actual chart autosaves must retain their patient, reject invalid window
 * contexts, and report failed persistence without discarding the editor or prior draft.
 * A second chart and a separate login session make the negative controls distinguishable.
 * Only this run's two patients, drafts and lock rows are created; cleanup verifies removal.
 */
'use strict';
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { csrfTokenPresent } = require('./lib/playwright-ui');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { readPatientState, assertPatientStateUnchanged } = require('./echart-two-patient-isolation-playwright-checks');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { checkLegacyAutosave, checkLegacyExistingNoteAutosave, rejectOwnedDraftInsert } = require('./lib/chart-autosave-checks');
const { checkAutosaveTimeout } = require('./lib/chart-autosave-timeout-checks');
const { checkIssueDeleteAuthorization } = require('./lib/chart-issue-delete-checks');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');

const NOTE = '#caseManagementEntryForm textarea[name="caseNote_note"]';
const ENDPOINT = '/CaseManagementEntry';
const q = h.sqlString;
const isAutosave = request => request.method() === 'POST'
  && new URL(request.url()).pathname.endsWith(ENDPOINT)
  && new URLSearchParams(request.postData() || '').get('method') === 'autosave';

async function applicationStatus(response, expected, label) {
  h.assert(response.status() === expected, `${label} returned HTTP ${response.status()}, expected ${expected}`);
  h.assert(Object.hasOwn(response.headers(), h.APPLICATION_HEADER), `${label} did not reach the application`);
  h.assert(!/ModSecurity|<center>nginx<\/center>/i.test(await response.text()), `${label} was blocked by the WAF`);
}

function drafts(sql, patients) {
  return JSON.stringify(sql.rows(`SELECT * FROM casemgmt_tmpsave
    WHERE demographic_no IN (${patients.join(',')}) ORDER BY id`));
}

async function pause(chart) {
  await chart.waitForFunction(() => !window.autoSaveXhr);
  await chart.evaluate(() => clearAutoSaveTimer());
}

async function autosave(chart, text, status = 200, periodic = false) {
  await pause(chart);
  await chart.locator(NOTE).fill(text);
  const answer = chart.waitForResponse(response => isAutosave(response.request()));
  answer.catch(() => {}); // Await the original below; setup errors must still reach cleanup.
  await chart.evaluate(timerPath => timerPath ? backup() : autoSave(), periodic);
  const response = await answer;
  await response.finished();
  h.assert(response.status() === status, `Autosave answered HTTP ${response.status()}, expected ${status}`);
  await chart.waitForFunction(() => !window.autoSaveXhr);
  await chart.evaluate(() => clearAutoSaveTimer());
  return response;
}

// A deliberate failed request is still recorded. Excuse only this exact response and
// Chromium's corresponding resource diagnostic after checking its status and UI/DB effects.
function acknowledgeResponse(recorder, response, before) {
  const responses = recorder.badResponses.filter(entry => !before.responses.has(entry)
    && entry.url === response.url() && entry.method === 'POST' && entry.status === response.status());
  h.assert(responses.length === 1, 'The controlled autosave failure was not recorded exactly once');
  const console = recorder.consoleIssues.filter(entry => !before.console.has(entry)
    && entry.location?.url === response.url()
    && /^Failed to load resource:.*500/.test(entry.text));
  h.assert(console.length <= 1, 'The controlled failure recorded duplicate resource diagnostics');
  const reported = recorder.consoleIssues.filter(entry => !before.console.has(entry)
    && /\/js\/newCaseManagementView\.js\.jsp(?:\?|$)/.test(entry.location?.url || '')
    && /^CaseManagement autoSave failed 500(?: Internal Server Error)?$/.test(entry.text.trim()));
  h.assert(reported.length === 1, 'The failed autosave did not log its exact server failure');
  recorder.badResponses = recorder.badResponses.filter(entry => !responses.includes(entry));
  recorder.consoleIssues = recorder.consoleIssues.filter(entry => !console.includes(entry) && !reported.includes(entry));
}

async function workflow(s) {
  const { sql, patient: A, marker, provider, context } = s;
  const B = h.insertId(sql, `INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
     provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(marker)},'ContextBravo','1985','03','04','M','AC',${q(provider)},'ON','ON','NR',NOW())`,
  'second chart patient');
  h.assert(/^[1-9]\d*$/.test(B), 'The second chart patient was not created');
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B}
      AND last_name=${q(marker)}`) === '1', 'Second chart patient ownership changed');
    sql.execute(`DELETE FROM casemgmt_note WHERE demographic_no=${B}
        AND note=${q(`${marker}-B-private-note`)};
      DELETE FROM casemgmt_note_lock WHERE demographic_no=${B};
      DELETE FROM casemgmt_tmpsave WHERE demographic_no=${B};
      DELETE FROM demographicExt WHERE demographic_no=${B};
      DELETE FROM demographicArchive WHERE demographic_no=${B};
      DELETE FROM demographic WHERE demographic_no=${B} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${B})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${B})
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${B})
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${B})`) === '0',
    'Second chart fixture or drafts were not removed');
  });
  const chartA = await s.chart();
  await pause(chartA);
  const chartB = await context.newPage();
  await h.gotoApp(chartB, s.config.baseUrl,
    `/encounter/IncomingEncounter?demographicNo=${B}&providerNo=${provider}`);
  await waitForNavbars(chartB, 30000);
  await pause(chartB);
  const foreignNote = h.insertId(sql, `INSERT INTO casemgmt_note
    (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,
     history,uuid,locked,archived,program_no)
    VALUES (NOW(),NOW(),${B},${q(provider)},${q(`${marker}-B-private-note`)},1,${q(provider)},
      ${q(`${marker}-B-private-history`)},UUID(),'0',0,'0')`, 'foreign-patient draft reference');
  h.assert(/^[1-9]\d*$/.test(foreignNote), 'The foreign-patient note fixture was not created');
  const token = await chartA.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue();
  const otherToken = await chartB.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue();
  h.assert(token && otherToken && token !== otherToken, 'The chart fixtures have no distinct authenticated contexts');
  const program = await chartA.evaluate(() => String(case_program_id));
  const endpoint = h.appUrl(s.config.baseUrl, ENDPOINT);
  const baseFields = [['method', 'autosave'], ['demographicNo', A], ['chartContext', token],
    ['programId', program], ['note_id', '0'], ['note', `${marker}-refused`]];
  const beforeB = readPatientState(sql, B);
  await s.step('both charts autosave only their own patient and show confirmed draft status', async () => {
    for (const [chart, patient, text] of [[chartA, A, `${marker}-A-original`], [chartB, B, `${marker}-B-original`]]) {
      await autosave(chart, text);
      await chart.locator('#autosaveTime[data-autosave-state="saved"][role="status"]').waitFor();
      await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient}
        AND provider_no=${q(provider)} ORDER BY id DESC LIMIT 1`, text, 'Autosave persisted another draft');
    }
  });
  const stable = drafts(sql, [A, B]);
  const stableA = readPatientState(sql, A);
  const stableB = readPatientState(sql, B);
  await s.step('invalid contexts and non-POST autosaves refuse without changing either patient', async () => {
    const csrf = await csrfTokenPresent(chartA);
    const forged = token.slice(0, token.indexOf('.') + 1) + (token.split('.')[1][0] === 'A' ? 'B' : 'A')
      + token.split('.')[1].slice(1);
    const cases = [
      ['forged', baseFields.map(([name, value]) => [name, name === 'chartContext' ? forged : value]), 403],
      ['another patient', baseFields.map(([name, value]) => [name, name === 'demographicNo' ? B : value]), 403],
      ['conflicting contexts', [...baseFields, ['chartContext', otherToken]], 403],
      ['conflicting patient aliases', [...baseFields, ['demographic_no', B]], 403],
      ['missing patient', baseFields.filter(([name]) => name !== 'demographicNo'), 400],
      ['malformed note reference', baseFields.map(([name, value]) => [name,
        name === 'note_id' ? 'invalid-note-id' : value]), 400],
      ['another patient\'s note reference', baseFields.map(([name, value]) => [name,
        name === 'note_id' ? foreignNote : value]), 403],
    ];
    for (const [label, fields, expected] of cases) {
      const response = await context.request.post(endpoint, {
        data: new URLSearchParams([...fields, ['CSRF-TOKEN', csrf]]).toString(),
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': csrf },
        maxRedirects: 0, failOnStatusCode: false,
      });
      try {
        await applicationStatus(response, expected, `${label} autosave`);
      } finally { await response.dispose(); }
      h.assert(drafts(sql, [A, B]) === stable, `${label} autosave changed a draft`);
    }
    const get = await context.request.get(`${endpoint}?${new URLSearchParams(baseFields)}`, { maxRedirects: 0 });
    try { await applicationStatus(get, 405, 'GET autosave'); }
    finally { await get.dispose(); }
    for (const method of ['edit', 'notehistory', 'history']) {
      const response = await context.request.get(`${endpoint}?${new URLSearchParams({
        method, demographicNo: A, chartContext: token, noteId: foreignNote, from: 'casemgmt', forceNote: 'true',
      })}`, { maxRedirects: 0 });
      try {
        await applicationStatus(response, 403, `Foreign-note ${method}`);
        h.assert(!(await response.text()).includes(`${marker}-B-private`), `${method} exposed the other patient's note`);
      } finally { await response.dispose(); }
    }
    assertPatientStateUnchanged(stableA, readPatientState(sql, A), 'Refused A autosaves');
    assertPatientStateUnchanged(stableB, readPatientState(sql, B), 'Refused B autosaves');
  });
  await s.step('a chart context cannot be replayed in another authenticated session', async () => {
    const separate = await h.newContext(context.browser(), s.config);
    separate.on('page', page => h.wireStrictPage(page, 'separate-chart-login', s.recorder));
    const page = await h.login(separate, s.config, s.recorder);
    const csrf = await csrfTokenPresent(page);
    const response = await separate.request.post(endpoint, {
      form: Object.fromEntries([...baseFields, ['CSRF-TOKEN', csrf]]),
      headers: { 'CSRF-TOKEN': csrf }, maxRedirects: 0,
    });
    try { await applicationStatus(response, 403, 'Cross-login chart context'); }
    finally { await response.dispose(); }
    h.assert(drafts(sql, [A, B]) === stable, 'Cross-login replay changed a draft');
    await separate.close();
  });
  const fixture = authzReadFixture({ sql, marker, provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  await s.step('registry and draft service access boundaries refuse reads and writes without clinical changes', async () => {
    const limited = fixture.addLogin(fixture.addRole({ _demographic: 'r', _appointment: 'r', _eChart: 'r' }));
    const locked = fixture.addLogin('doctor');
    fixture.lockPatient(locked, A);
    const read = h.appUrl(s.config.baseUrl,
      `/oscarResearch/oscarDxResearch/setupDxResearch?demographicNo=${A}`);
    const write = h.appUrl(s.config.baseUrl, '/oscarResearch/oscarDxResearch/dxResearch');
    for (const [login, label] of [[limited, 'Missing registry module'], [locked, 'Locked patient']]) {
      const signed = await signIn(s, login);
      try {
        const csrf = await csrfTokenPresent(signed.page);
        const get = await signed.context.request.get(read, { maxRedirects: 0 });
        try { await applicationStatus(get, 403, `${label} read`); }
        finally { await get.dispose(); }
        const post = await signed.context.request.post(write, {
          form: { demographicNo: A, selectedCodingSystem: 'icd9', forward: '250',
            providerNo: login.providerNo, 'CSRF-TOKEN': csrf },
          headers: { 'CSRF-TOKEN': csrf }, maxRedirects: 0,
        });
        try { await applicationStatus(post, 403, `${label} write`); }
        finally { await post.dispose(); }
        const draft = await signed.context.request.post(
          h.appUrl(s.config.baseUrl, `/ws/rs/notes/${A}/tmpSave`), {
            data: { noteId: 0, note: `${marker}-forbidden-rest-draft` },
            headers: { 'CSRF-TOKEN': csrf }, maxRedirects: 0,
          });
        try { await applicationStatus(draft, 403, `${label} draft write`); }
        finally { await draft.dispose(); }
      } finally { await signed.context.close(); }
    }
    const control = await context.request.get(read, { maxRedirects: 0 });
    try { await applicationStatus(control, 200, 'Authorized registry read'); }
    finally { await control.dispose(); }
    assertPatientStateUnchanged(stableA, readPatientState(sql, A), 'Registry access probes');
    assertPatientStateUnchanged(stableB, readPatientState(sql, B), 'Registry access probes');
  });
  await checkIssueDeleteAuthorization(s, { fixture, patient: A, program, applicationStatus });
  await s.step('a real server autosave failure preserves the previous draft and visibly recovers on retry', async () => {
    // A scoped INSERT trigger reaches the actual transaction and fails after its delete.
    // The browser submits the valid note identity and its unmodified clinical text.
    const dropFailure = rejectOwnedDraftInsert(s, A, `${marker}-A-retry`);
    const before = { responses: new Set(s.recorder.badResponses), console: new Set(s.recorder.consoleIssues) };
    try {
      const response = await autosave(chartA, `${marker}-A-retry`, 500);
      await chartA.locator('#autosaveTime[data-autosave-state="failed"]').waitFor();
      h.assert(/could not be saved/i.test(await chartA.locator('#autosaveTime').innerText()), 'Failed autosave still claims success');
      h.assert(await chartA.locator(NOTE).inputValue() === `${marker}-A-retry`, 'Failure discarded the editor');
      h.assert(drafts(sql, [A, B]) === stable, 'Failed replacement deleted or changed an existing draft');
      acknowledgeResponse(s.recorder, response, before);
    } finally { dropFailure(); }
    await autosave(chartA, `${marker}-A-retry`);
    await chartA.locator('#autosaveTime[data-autosave-state="saved"]').waitFor();
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${A}
      AND provider_no=${q(provider)} ORDER BY id DESC LIMIT 1`, `${marker}-A-retry`, 'Retry did not persist the retained editor');
    assertPatientStateUnchanged(stableB, readPatientState(sql, B), 'A retry');
  });
  await s.step('deleting all draft text clears the old persisted draft', async () => {
    // Returning to the original text used to skip backup() and revive the older draft.
    const original = await chartA.evaluate(() => origCaseNote);
    await autosave(chartA, original, 200, true);
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${A}
      AND provider_no=${q(provider)} ORDER BY id DESC LIMIT 1`, original,
    'Returning to the original editor text retained a stale autosaved draft');
    if (original !== '') await autosave(chartA, '', 200, true);
    await chartA.locator('#autosaveTime[data-autosave-state="saved"]').waitFor();
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${A}
      AND provider_no=${q(provider)} ORDER BY id DESC LIMIT 1`, '', 'Clearing the editor retained the previous draft');
    assertPatientStateUnchanged(stableB, readPatientState(sql, B), 'Clearing A draft');
  });
  await s.step('a login page returned with HTTP 200 cannot acknowledge an autosave', async () => {
    const baseline = drafts(sql, [A, B]);
    const before = new Set(s.recorder.consoleIssues);
    const match = url => url.pathname.endsWith(ENDPOINT);
    let injected = 0;
    const expired = route => {
      if (!isAutosave(route.request())) return route.continue();
      injected++;
      return route.fulfill({ status: 200, contentType: 'text/html',
        body: '<!doctype html><title>Session expired</title><p>Please sign in again.</p>' });
    };
    await chartA.route(match, expired);
    try {
      await autosave(chartA, `${marker}-timeout-retained`, 200);
      await chartA.locator('#autosaveTime[data-autosave-state="failed"]').waitFor();
      h.assert(injected === 1 && await chartA.locator(NOTE).inputValue() === `${marker}-timeout-retained`
        && drafts(sql, [A, B]) === baseline, 'A login-page response lost text or claimed a persistent save');
      const diagnostics = s.recorder.consoleIssues.filter(entry => !before.has(entry)
        && /\/js\/newCaseManagementView\.js\.jsp(?:\?|$)/.test(entry.location?.url || '')
        && entry.text.trim() === 'CaseManagement autoSave failed 200 Unexpected autosave response');
      h.assert(diagnostics.length === 1, 'The false-success response did not record its exact diagnostic');
      s.recorder.consoleIssues = s.recorder.consoleIssues.filter(entry => !diagnostics.includes(entry));
    } finally { await chartA.unroute(match, expired); }
    await autosave(chartA, `${marker}-timeout-retained`);
    await chartA.locator('#autosaveTime[data-autosave-state="saved"]').waitFor();
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${A}
      AND provider_no=${q(provider)} ORDER BY id DESC LIMIT 1`, `${marker}-timeout-retained`, 'Timeout recovery did not persist retained text');
    assertPatientStateUnchanged(stableB, readPatientState(sql, B), 'Timeout recovery');
  });
  await s.step('a network failure is visible and retains text until a successful retry', async () => {
    const baseline = drafts(sql, [A, B]);
    const before = { failures: new Set(s.recorder.requestFailures), console: new Set(s.recorder.consoleIssues) };
    const match = url => url.pathname.endsWith(ENDPOINT);
    let rejected = 0;
    const disconnect = route => {
      if (!isAutosave(route.request())) return route.continue();
      rejected++;
      return route.abort('failed');
    };
    await pause(chartA);
    await chartA.locator(NOTE).fill(`${marker}-network-retry`);
    await chartA.route(match, disconnect);
    try {
      const failed = chartA.waitForEvent('requestfailed', { predicate: isAutosave });
      failed.catch(() => {});
      await chartA.evaluate(() => autoSave());
      const request = await failed;
      await chartA.locator('#autosaveTime[data-autosave-state="failed"]').waitFor();
      await chartA.waitForFunction(() => !window.autoSaveXhr);
      h.assert(await chartA.locator(NOTE).inputValue() === `${marker}-network-retry`, 'Network failure discarded text');
      h.assert(drafts(sql, [A, B]) === baseline, 'An undelivered request changed a draft');
      const failures = s.recorder.requestFailures.filter(entry => !before.failures.has(entry)
        && entry.url === request.url() && /ERR_FAILED/.test(entry.errorText || ''));
      h.assert(rejected === 1 && failures.length === 1, 'Network probe did not record exactly its rejected autosave');
      const diagnostics = s.recorder.consoleIssues.filter(entry => !before.console.has(entry)
        && ((entry.location?.url === request.url() && /^Failed to load resource:.*ERR_FAILED/.test(entry.text))
          || (/\/js\/newCaseManagementView\.js\.jsp(?:\?|$)/.test(entry.location?.url || '')
            && /^CaseManagement autoSave failed 0(?: Network Error)?$/.test(entry.text.trim()))));
      h.assert(diagnostics.length >= 1 && diagnostics.length <= 2, 'Network failure did not produce the expected diagnostics');
      s.recorder.requestFailures = s.recorder.requestFailures.filter(entry => !failures.includes(entry));
      s.recorder.consoleIssues = s.recorder.consoleIssues.filter(entry => !diagnostics.includes(entry));
    } finally { await chartA.unroute(match, disconnect); }
    await autosave(chartA, `${marker}-network-retry`);
    await chartA.locator('#autosaveTime[data-autosave-state="saved"]').waitFor();
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${A}
      AND provider_no=${q(provider)} ORDER BY id DESC LIMIT 1`, `${marker}-network-retry`, 'Network retry did not save retained text');
    assertPatientStateUnchanged(stableB, readPatientState(sql, B), 'A network retry');
  });
  await checkAutosaveTimeout(s, { chart: chartA, patient: A, otherPatient: B, isAutosave, drafts, autosave, pause });
  await checkLegacyAutosave(s, { patient: A, chartContext: token });
  await s.step('REST draft references refuse foreign notes and preserve intentional empty text', async () => {
    const baseline = readPatientState(sql, A);
    const csrf = await csrfTokenPresent(chartA);
    const rest = h.appUrl(s.config.baseUrl, `/ws/rs/notes/${A}`);
    const cases = [
      ['tmpSave', undefined, 400],
      ['tmpSave', { noteId: Number(foreignNote), note: `${marker}-foreign-rest-write` }, 403],
      ['getCurrentNote', undefined, 400],
      ['getCurrentNote', { noteId: foreignNote, forceNote: 'true' }, 403],
    ];
    for (const [method, data, expected] of cases) {
      const response = await context.request.post(`${rest}/${method}`, {
        data: data === undefined ? Buffer.alloc(0) : data,
        headers: { 'Content-Type': 'application/json', 'CSRF-TOKEN': csrf }, maxRedirects: 0,
      });
      try {
        await applicationStatus(response, expected, `REST ${method}`);
        h.assert(!(await response.text()).includes(`${marker}-B-private`), 'REST exposed the foreign note');
      } finally { await response.dispose(); }
      assertPatientStateUnchanged(baseline, readPatientState(sql, A), 'Refused REST draft request');
      assertPatientStateUnchanged(stableB, readPatientState(sql, B), 'Refused REST foreign-note request');
    }
    const read = await context.request.post(`${rest}/getCurrentNote`, {
      data: { note_edit: 'new' }, headers: { 'CSRF-TOKEN': csrf }, maxRedirects: 0,
    });
    try {
      await applicationStatus(read, 200, 'Authorized REST new note');
      h.assert((await read.json()).noteId == null, 'A new REST note selected a saved foreign note');
    } finally { await read.dispose(); }
    assertPatientStateUnchanged(baseline, readPatientState(sql, A), 'REST new-note read');
    const cleared = await context.request.post(`${rest}/tmpSave`, {
      data: { noteId: 0, note: '' }, headers: { 'CSRF-TOKEN': csrf }, maxRedirects: 0,
    });
    try { await applicationStatus(cleared, 200, 'Authorized REST empty draft'); }
    finally { await cleared.dispose(); }
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${A}
      AND provider_no=${q(provider)} ORDER BY id DESC LIMIT 1`, '', 'REST revived a cleared draft');
    assertPatientStateUnchanged(stableB, readPatientState(sql, B), 'REST empty draft save');
  });
  await checkLegacyExistingNoteAutosave(s, { patient: A, chartContext: token, program });
  // Apart from its explicit owned draft, B gained no clinical data during the test.
  const finalB = readPatientState(sql, B);
  finalB.drafts = beforeB.drafts;
  assertPatientStateUnchanged(beforeB, finalB, 'B chart autosave');
  await releaseChartLocks(context, s.config.baseUrl);
}

if (require.main === module) runWorkflow('chart-context-request-guards', workflow);
module.exports = { workflow, isAutosave, acknowledgeResponse };
