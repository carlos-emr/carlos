/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createHash } = require('node:crypto');
const h = require('./playwright-harness');
const { waitForNavbars } = require('../echart-navbar-modules-playwright-checks');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const { observeOwnedReload, provenReloadAborts } = require('./chart-reload-witness');
const { retireOwnedChartPages } = require('./owned-chart-page-cleanup');
const q = h.sqlString;
const NOTE = '#caseManagementEntryForm textarea[name="caseNote_note"]';
const LOCK_COLUMNS = ['id', 'session_id', 'ip_address', 'provider_no', 'note_id', 'demographic_no', 'lock_acquired'];
const LOCKS = `SELECT ${LOCK_COLUMNS.join(',')} FROM casemgmt_note_lock ORDER BY id`;
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const digest = value => createHash('sha256').update(JSON.stringify(value)).digest('hex');

/** Revoke one proven synthetic editor lock; never adopt or restore another lock. */
function revokeOwnedModernLock(sql, { patient, provider, noteId, sessionId, marker }) {
  h.assert(/^[1-9]\d*$/.test(patient) && /^[1-9]\d*$/.test(provider) && /^\d+$/.test(noteId)
    && typeof sessionId === 'string' && /^[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_.-]+)?$/.test(sessionId)
    && /^FAKE-PW[0-9a-f]{16}$/.test(marker), 'Invalid owned modern-conflict fixture identity');
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`) === '1',
    'The modern-conflict patient is not owned');
  const before = sql.rows(LOCKS);
  const sessions = new Set([sessionId, sessionId.split('.')[0]]);
  const owned = before.filter(row => sessions.has(row[1]) && row[3] === provider
    && row[4] === noteId && row[5] === patient);
  h.assert(owned.length === 1 && /^[1-9]\d*$/.test(owned[0][0]), 'The modern editor has no unique owned session lock');
  const row = owned[0];
  const predicate = LOCK_COLUMNS.map((column, index) => `${column}<=>${row[index] === null ? 'NULL' : q(row[index])}`).join(' AND ');
  h.assert(sql.value(`DELETE FROM casemgmt_note_lock WHERE ${predicate}; SELECT ROW_COUNT()`) === '1',
    'The modern-conflict lock changed before its conditional revocation');
  const otherLocks = before.filter(value => value[0] !== row[0]);
  h.assert(same(sql.rows(LOCKS), otherLocks), 'Revoking the owned lock changed another editor lock');
  return { revoked: row, otherLocks };
}

function acknowledgeModernConflict(recorder, response, before) {
  h.assert(response.status() === 409, 'Only the actual modern409 may be acknowledged');
  const http = recorder.badResponses.filter(entry => !before.http.has(entry)
    && entry.url === response.url() && entry.method === 'POST' && entry.status === 409);
  const resource = recorder.consoleIssues.filter(entry => !before.console.has(entry)
    && entry.location?.url === response.url() && /^Failed to load resource:.*409/.test(entry.text));
  h.assert(http.length === 1 && resource.length <= 1, 'Modern conflict diagnostics are missing or ambiguous');
  const audit = { http: [...http], console: [...resource] };
  recorder.badResponses = recorder.badResponses.filter(entry => !http.includes(entry));
  recorder.consoleIssues = recorder.consoleIssues.filter(entry => !resource.includes(entry));
  return audit;
}

const formIdentity = chart => chart.evaluate(() => {
  const form = document.forms.caseManagementEntryForm;
  return { patient: String(demographicNo), provider: String(providerNo), program: String(case_program_id),
    noteId: String(form.elements.noteId.value), token: form.elements.chartContext.value,
    csrf: CarlosAjax.getCsrfToken(), endpoint: new URL(chartActionUrl('/CaseManagementEntry'), location.href).href,
    message: draftSaveConflictMessage, timeOrigin: performance.timeOrigin, url: location.href };
});

async function bounded(promise, label) {
  let timer;
  try {
    return await Promise.race([promise, new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error(`${label} did not settle`)), 15000);
    })]);
  } finally { clearTimeout(timer); }
}

/** Actual autosave409, copy-first Cancel, genuine reopen, and raw-text retry. */
async function checkModernAutosaveConflict(s, { chart, otherChart, patient, otherPatient, program,
  autosave, pause, applicationStatus, cleanupPages = [] }) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-modern-conflict-'));
  fs.chmodSync(directory, 0o700);
  const evidence = { phase: 'modern autosave409/copy-first recovery', clipboardTested: false,
    rawDraft: `${s.marker}-modern409 latest H&P café 中文\nA+B > 1 & follow-up`, nativeReleases: [], auxiliaryCleanup: [] };
  let form; let locks; let beforeA; let beforeB; let otherDrafts; let primaryError;
  const readOtherDrafts = () => s.sql.rows(`SELECT * FROM casemgmt_tmpsave WHERE demographic_no=${patient}
    AND NOT(provider_no=${q(s.provider)} AND program_id=${Number(program)}) ORDER BY id`);
  const unchanged = label => {
    assertPatientStateUnchanged(beforeA, readPatientState(s.sql, patient), label);
    assertPatientStateUnchanged(beforeB, readPatientState(s.sql, otherPatient), label);
    h.assert(same(s.sql.rows(LOCKS), locks.otherLocks), `${label}: unrelated or revoked locks changed`);
  };
  try {
    await s.step('a real modern autosave409 retains raw text and the last acknowledged draft', async () => {
      await pause(chart); await pause(otherChart);
      await autosave(chart, `${s.marker}-modern409 acknowledged baseline`);
      await pause(chart);
      form = await formIdentity(chart);
      h.assert(form.patient === patient && form.provider === s.provider && form.program === String(program)
        && /^\d+$/.test(form.noteId) && form.token && form.csrf && /copy/i.test(form.message),
      'Modern conflict setup lost its actual chart identity or copy-first message');
      const cookie = (await s.context.cookies(s.config.baseUrl.href)).find(value => value.name === 'JSESSIONID');
      h.assert(cookie?.value, 'Modern conflict setup has no authenticated session');
      beforeA = readPatientState(s.sql, patient); beforeB = readPatientState(s.sql, otherPatient);
      otherDrafts = readOtherDrafts();
      locks = revokeOwnedModernLock(s.sql, { patient, provider: s.provider, noteId: form.noteId,
        sessionId: cookie.value, marker: s.marker });
      const before = { http: new Set(s.recorder.badResponses), console: new Set(s.recorder.consoleIssues) };
      const response = await autosave(chart, evidence.rawDraft, 409);
      await applicationStatus(response, 409, 'Real modern lost-lock autosave');
      const request = response.request(); const fields = new URLSearchParams(request.postData() || '');
      h.assert(request.frame().page() === chart && request.url() === form.endpoint
        && fields.get('demographicNo') === patient && fields.get('programId') === String(program)
        && fields.get('note_id') === form.noteId && fields.get('note') === evidence.rawDraft,
      'The real409 was not the exact owned modern autosave');
      await chart.locator('#autosaveTime[data-autosave-state="conflict"]').waitFor();
      h.assert(await chart.locator('#autosaveTime').innerText() === form.message
        && await chart.locator(NOTE).inputValue() === evidence.rawDraft
        && await chart.evaluate(() => lostNoteLock === true), 'Modern409 lost raw text or did not show copy-first conflict');
      unchanged('Modern409 refusal');
      evidence.conflict = acknowledgeModernConflict(s.recorder, response, before);
      evidence.originalLockId = locks.revoked[0]; evidence.lastAcknowledgedStateSha256 = digest(beforeA);
    });
    await s.step('cancelling the real copy-first reopen prompt leaves the document and raw draft intact', async () => {
      const dialogs = await h.withExpectedDialogs(chart, () => chart.locator(NOTE).press('ArrowLeft'), { accept: false });
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm'
        && dialogs[0].text === `${form.message}\n Do you wish to reopen the chart now?`,
      'Modern conflict Cancel did not handle exactly its copy-first reopen confirmation');
      const current = await formIdentity(chart);
      h.assert(current.timeOrigin === form.timeOrigin && current.url === form.url
        && current.patient === patient && current.program === form.program && current.noteId === form.noteId
        && current.token === form.token && await chart.locator(NOTE).inputValue() === evidence.rawDraft,
      'Cancelling the reopen prompt changed or reloaded the clinical editor');
      unchanged('Cancelled modern reopen'); evidence.cancel = { dialogs, documentUnchanged: true, rawTextUnchanged: true };
    });
    await s.step('accepting reopen reacquires the owned lock and saves the copied raw draft unchanged', async () => {
      // This is a reference copy, not an assertion about OS clipboard permissions.
      const referenceCopy = await chart.locator(NOTE).inputValue();
      const requests = []; const releaseErrors = [];
      const passive = await observeOwnedReload(chart, form);
      const previousFailures = new Set(s.recorder.requestFailures);
      const claimedFailures = new Set();
      let seenFailureCount = s.recorder.requestFailures.length;
      const observe = request => {
        try {
          if (request.resourceType() !== 'ping' || new URL(request.url()).pathname !== new URL(form.endpoint).pathname) return;
          const item = { request, startedUTC: new Date().toISOString(),
            requestTargetSha256: createHash('sha256').update(new URL(request.url()).pathname + new URL(request.url()).search).digest('hex'),
            bodyAvailable: request.postData() !== null };
          let terminal;
          Object.defineProperty(item, 'terminalPromise', { value: new Promise(resolve => { terminal = resolve; }) });
          Object.defineProperty(item, 'settleTerminal', { value: terminal });
          const fields = new URLSearchParams(request.postData() || '');
          if (request.frame().page() !== chart || request.method() !== 'POST' || request.url() !== form.endpoint) {
            releaseErrors.push('Native release left its owned page or signed endpoint');
          } else if (item.bodyAvailable && !(same([...fields.keys()].sort(),
            ['CSRF-TOKEN', 'demographicNo', 'method', 'noteId', 'providerNo']) && fields.get('method') === 'releaseNoteLock'
            && fields.get('demographicNo') === patient && fields.get('providerNo') === s.provider
            && fields.get('noteId') === form.noteId && fields.get('CSRF-TOKEN') === form.csrf)) {
            releaseErrors.push('Native release payload differs from the owned form');
          }
          requests.push(item);
        } catch (error) {
          releaseErrors.push(`Native release ownership could not be read (${error.name})`);
        }
      };
      const failed = request => {
        const countBefore = seenFailureCount;
        seenFailureCount = s.recorder.requestFailures.length;
        const item = requests.find(value => value.request === request);
        if (!item) return;
        item.terminal = 'failed';
        item.settleTerminal();
        const entry = s.recorder.requestFailures.at(-1);
        if (seenFailureCount !== countBefore + 1 || !entry || previousFailures.has(entry) || claimedFailures.has(entry) || entry.url !== request.url()
          || entry.resourceType !== 'ping' || entry.errorText !== request.failure()?.errorText) {
          releaseErrors.push('The native failed Request has no unique synchronous recorder entry'); return;
        }
        claimedFailures.add(entry);
        item.failureUTC = new Date().toISOString(); item.rawFailure = entry;
        item.failureEntryIndex = s.recorder.requestFailures.length - 1;
      };
      const finished = request => {
        const item = requests.find(value => value.request === request);
        if (item) { item.terminal = 'finished'; item.settleTerminal(); }
      };
      chart.on('request', observe);
      chart.on('requestfailed', failed);
      chart.on('requestfinished', finished);
      let reopenError;
      try {
        const loaded = chart.waitForEvent('load', { timeout: 30000 }); loaded.catch(() => {});
        const dialogs = await h.withExpectedDialogs(chart, async () => {
          await chart.locator(NOTE).press('ArrowLeft'); await loaded;
        });
        h.assert(dialogs.length === 1 && dialogs[0].text === `${form.message}\n Do you wish to reopen the chart now?`,
          'Accepted modern reopen did not use exactly its actual copy-first confirmation');
        await waitForNavbars(chart, 30000); await pause(chart);
        const reopened = await formIdentity(chart);
        h.assert(reopened.timeOrigin !== form.timeOrigin && reopened.url === form.url
          && reopened.patient === patient && reopened.program === form.program && reopened.token === form.token,
        'The accepted reopen did not create a new document for the same patient/program');
        h.assert(releaseErrors.length === 0 && requests.length >= 1 && requests.length <= 2,
          'Native reopen did not emit the bounded owned release requests');
        for (const item of requests) {
          const response = await bounded(item.request.response(), 'Native release response');
          if (response) {
            h.assert(response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
              'An observed native release was not acknowledged by the application');
            const error = await bounded(response.finished(), 'Native release completion');
            item.applicationStatus = 200; item.responseFinished = !error; item.terminalError = error?.message || null;
          } else item.applicationStatus = null;
        }
        assertPatientStateUnchanged(beforeA, readPatientState(s.sql, patient), 'Modern reopen before re-entry');
        assertPatientStateUnchanged(beforeB, readPatientState(s.sql, otherPatient), 'Modern reopen bystander');
        const acquired = s.sql.rows(LOCKS).filter(row => row[1] === locks.revoked[1] && row[3] === s.provider
          && row[4] === reopened.noteId && row[5] === patient);
        h.assert(acquired.length === 1 && acquired[0][0] !== locks.revoked[0]
          && same(s.sql.rows(LOCKS).filter(row => row[0] !== acquired[0][0]), locks.otherLocks),
        'Reopen failed to acquire a fresh owned lock or changed another lock');
        const retry = await autosave(chart, referenceCopy);
        await applicationStatus(retry, 200, 'Retained raw draft retry');
        const rows = s.sql.rows(`SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient}
          AND provider_no=${q(s.provider)} AND program_id=${Number(program)} ORDER BY id`);
        h.assert(rows.length === 1 && rows[0][0] === referenceCopy && referenceCopy === evidence.rawDraft
          && await chart.locator(NOTE).inputValue() === referenceCopy
          && await chart.locator('#autosaveTime').getAttribute('data-autosave-state') === 'saved',
        'Successful modern retry changed, lost or duplicated the copied raw text');
        const afterA = readPatientState(s.sql, patient);
        assertPatientStateUnchanged({ ...beforeA, drafts: afterA.drafts }, afterA, 'Modern retry clinical state');
        assertPatientStateUnchanged(beforeB, readPatientState(s.sql, otherPatient), 'Modern retry bystander');
        h.assert(same(readOtherDrafts(), otherDrafts) && same(s.sql.rows(LOCKS), [...locks.otherLocks, acquired[0]]
          .sort((a, b) => Number(a[0]) - Number(b[0]))), 'Modern retry changed other drafts or lost its fresh lock');
        evidence.reopen = { dialogs, newDocument: true, samePatientProgram: true, freshLockId: acquired[0][0], rawTextPersisted: true };
        await bounded(Promise.all(requests.map(item => item.terminalPromise)), 'Original native Request terminal events');
        const witness = await passive.finish(requests.length);
        h.assert(releaseErrors.length === 0, 'Native release diagnostic association failed');
        const proof = provenReloadAborts(s.recorder, { page: chart, form, reopened, requests, witness, recovered: true });
        evidence.reloadAudit = proof.audit;
        evidence.nativeReleases = requests.map(({ request, ...item }) => item);
        // Persist the original raw failure and CDP proof BEFORE acknowledging its exact entry.
        fs.writeFileSync(path.join(directory, 'recovery.json'), JSON.stringify(evidence, null, 2) + '\n', { mode: 0o600 });
        s.recorder.requestFailures = s.recorder.requestFailures.filter(entry => !proof.entries.includes(entry));
      } catch (error) {
        reopenError = error; throw error;
      } finally {
        chart.off('request', observe);
        chart.off('requestfailed', failed);
        chart.off('requestfinished', finished);
        evidence.nativeReleases = requests.map(({ request, ...item }) => item);
        evidence.nativeReleaseErrors = releaseErrors;
        try { await passive.close(); }
        catch (error) {
          if (reopenError) reopenError.message += `; Passive observer cleanup: ${error.message}`;
          else throw error;
        }
      }
    });
  } catch (error) {
    primaryError = error; throw error;
  } finally {
    let cleanupError;
    try { await retireOwnedChartPages(s, cleanupPages, evidence.auxiliaryCleanup); }
    catch (error) {
      evidence.cleanupFailure = error.message;
      if (primaryError) primaryError.message += `; Auxiliary cleanup: ${error.message}`;
      else cleanupError = error;
    }
    const file = path.join(directory, 'recovery.json');
    try {
      fs.writeFileSync(file, JSON.stringify(evidence, null, 2) + '\n', { mode: 0o600 });
      console.log('CHECK modern409 recovery evidence ' + JSON.stringify({ file, sha256: digest(evidence) }));
    } catch (error) {
      console.error('Modern409 evidence capture failed: ' + error.message);
      if (!primaryError && !cleanupError) throw error;
    }
    if (cleanupError) throw cleanupError;
  }
}

module.exports = { checkModernAutosaveConflict, revokeOwnedModernLock, acknowledgeModernConflict };
