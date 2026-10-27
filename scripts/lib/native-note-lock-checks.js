/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./playwright-harness');
const { csrfToken } = require('./front-door-checks');
const { assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;

function sessionsForCookie(value) {
  h.assert(typeof value === 'string' && value.length > 0, 'Native lock check requires one actual session cookie');
  return new Set([value, value.split('.')[0]]);
}

/** Check the complete persisted ownership tuple before using it as recovery evidence. */
function assertOwnedLock(rows, { patient, note, provider, sessions }) {
  h.assert(rows.length === 1 && rows[0].length === 5, 'Native lock operation did not leave exactly one owned row');
  const row = rows[0];
  h.assert(/^[1-9]\d*$/.test(row[0]) && row[1] === String(patient) && row[2] === String(note)
    && row[3] === String(provider) && sessions.has(row[4]), 'Native lock persisted ownership tuple changed');
  return row;
}

/** Only the selected owned row's browser session may change during recovery. */
function assertRecoveryRows(before, after, { columns, id, sessions }) {
  h.assert(new Set(columns).size === columns.length && ['id', 'session_id'].every(name => columns.includes(name)),
    'Native lock recovery column identity is incomplete');
  const key = columns.indexOf('id');
  const session = columns.indexOf('session_id');
  const map = values => {
    h.assert(values.every(row => row.length === columns.length && /^[1-9]\d*$/.test(row[key])),
      'Native lock recovery snapshot shape changed');
    const result = new Map(values.map(row => [row[key], row]));
    h.assert(result.size === values.length, 'Native lock recovery snapshot has duplicate identities');
    return result;
  };
  const original = map(before); const current = map(after);
  h.assert(original.has(String(id)) && current.size === original.size
    && [...original.keys()].every(value => current.has(value)), 'Native lock recovery added or removed a lock row');
  for (const [value, row] of original) {
    const wanted = row.slice();
    if (value === String(id)) {
      const actualSession = current.get(value)[session];
      h.assert(sessions.has(actualSession) && actualSession !== row[session], 'Native lock recovery kept the wrong browser session');
      wanted[session] = actualSession;
    }
    h.assert(JSON.stringify(current.get(value)) === JSON.stringify(wanted),
      'Native lock recovery changed a foreign row or unexpected lock metadata');
  }
}

/** Retain a failed step's original error while preserving every cleanup failure as its cause. */
function recordCleanupFailures(primary, failures) {
  if (!failures.length) return;
  const cause = new AggregateError(primary?.cause ? [primary.cause, ...failures] : failures,
    'Native lock check cleanup/diagnostics failed; owned parents retained');
  if (primary) primary.cause = cause;
  else throw cause;
}

/** Use the parent print fixture's exact patients/notes; every lock is created/released by real endpoints. */
async function checkNativeNoteLocks(s, { fixture, patients, notes, readLogin, snapshot }) {
  const { sql, config, provider } = s;
  h.assert(patients.length === 2 && notes.length === 2 && [...patients, ...notes].every(id => /^[1-9]\d*$/.test(String(id))),
    'Native lock checks require the two already-owned print patients and notes');
  const [lockedPatient, openPatient] = patients;
  const [lockedNote, openNote] = notes;
  const doctor = fixture.addLogin('doctor');
  fixture.joinTestLoginPrograms(doctor);
  h.assert(fixture.lockPatient(doctor, lockedPatient, ['_demographic']).length === 1,
    'Native lock record exclusion was not installed');
  const contexts = [];
  const endpoint = h.appUrl(config.baseUrl, '/CaseManagementEntry');
  const state = () => patients.map(patient => snapshot(sql, patient));
  const unchanged = (before, label) => patients.forEach((patient, index) =>
    assertPatientStateUnchanged(before[index], snapshot(sql, patient), label));
  const beforeAll = state();
  const lockColumns = sql.rows('SHOW COLUMNS FROM casemgmt_note_lock').map(row => row[0]);
  h.assert(new Set(lockColumns).size === lockColumns.length
    && ['id', 'note_id', 'demographic_no', 'provider_no', 'session_id', 'ip_address', 'lock_acquired']
      .every(name => lockColumns.includes(name)), 'Native lock fixture lacks the actual complete lock schema');
  h.assert(beforeAll.every(patient => patient.locks.length === 0), 'Native lock fixture already has locks');
  const rows = (patient, note, actor) => sql.rows(`SELECT id,demographic_no,note_id,provider_no,session_id
    FROM casemgmt_note_lock WHERE demographic_no=${patient} AND note_id=${note}
      AND provider_no=${q(actor)} ORDER BY id`);
  const login = async (account, label) => {
    const context = await h.newContext(s.context.browser(), config);
    const recorder = h.createRecorder();
    const actor = { context, recorder, account, releaseTargets: new Map(), csrf: null, sessions: null };
    contexts.push(actor); // Register before browser login/CSRF setup can fail.
    context.on('page', page => h.wireStrictPage(page, label, recorder, { baseline: [] }));
    actor.page = await h.login(context, { ...config, testUser: account.username }, recorder, { baseline: [], label });
    actor.csrf = await csrfToken(actor.page, config.baseUrl);
    const cookies = (await context.cookies(endpoint)).filter(cookie => cookie.name === 'JSESSIONID');
    h.assert(cookies.length === 1, 'Native lock login does not have exactly one session cookie');
    actor.sessions = sessionsForCookie(cookies[0].value);
    return actor;
  };
  const request = async (actor, method, patient, note, expected, verb = 'POST') => {
    const parameters = { method, demographicNo: String(patient) };
    if (verb === 'POST') parameters['CSRF-TOKEN'] = actor.csrf;
    if (verb === 'POST' && method !== 'releaseNoteLock' && note !== null) {
      actor.releaseTargets.set(`${patient}:${note}`, { patient, note });
    }
    if (note !== null) parameters.noteId = String(note);
    const response = verb === 'POST'
      ? await actor.context.request.post(endpoint, { form: parameters, headers: { 'CSRF-TOKEN': actor.csrf },
        maxRedirects: 0, failOnStatusCode: false, timeout: 40000 })
      : await actor.context.request.get(`${endpoint}?${new URLSearchParams(parameters)}`, {
        headers: { 'CSRF-TOKEN': actor.csrf }, maxRedirects: 0, failOnStatusCode: false, timeout: 40000 });
    try {
      const body = await response.text();
      h.assert(response.status() === expected && Object.hasOwn(response.headers(), h.APPLICATION_HEADER)
        && !h.isWafPage(response.status(), body), `${method} did not return application HTTP ${expected}`);
      if (expected !== 200) h.assert(!body.includes(s.marker), `${method} refusal disclosed owned clinical text`);
      return body;
    } finally { await response.dispose(); }
  };
  let primaryFailure;
  try {
    const first = await login(doctor, 'native-lock-first');
    const second = await login(doctor, 'native-lock-second');
    h.assert(![...first.sessions].some(value => second.sessions.has(value)), 'Recovery needs two distinct browser sessions');
    const owner = await login({ username: config.testUser, providerNo: provider }, 'native-lock-other-provider');
    const reader = await login(readLogin, 'native-lock-read-only');
    await s.step('native lock acquisition and recovery require patient record and note-write access', async () => {
      for (const method of ['isNoteEdited', 'updateNoteLock']) {
        const before = state();
        await request(first, method, lockedPatient, lockedNote, 403);
        unchanged(before, `${method} excluded record`);
        await request(reader, method, openPatient, openNote, 403);
        unchanged(before, `${method} read-only module grants`);
      }
    });
    await s.step('native lock takeover preserves another provider and recovers the same provider across browser sessions', async () => {
      const body = await request(first, 'isNoteEdited', openPatient, 0, 200);
      h.assert(JSON.parse(body).isNoteEdited === 'unlocked', 'The first browser did not acquire its new editor lock');
      const own = assertOwnedLock(rows(openPatient, 0, doctor.providerNo), {
        patient: openPatient, note: 0, provider: doctor.providerNo, sessions: first.sessions });
      const ownerBody = await request(owner, 'isNoteEdited', openPatient, openNote, 200);
      h.assert(JSON.parse(ownerBody).isNoteEdited === 'unlocked', 'The separate provider did not acquire its own note lock');
      const foreign = assertOwnedLock(rows(openPatient, openNote, provider), {
        patient: openPatient, note: openNote, provider, sessions: owner.sessions });
      const beforeRefusal = state();
      await request(first, 'updateNoteLock', openPatient, openNote, 403);
      unchanged(beforeRefusal, 'foreign-provider takeover refusal');
      await request(first, 'updateNoteLock', openPatient, null, 200);
      unchanged(beforeRefusal, 'classic no-noteId recovery preserves the current owned lock/cache');
      const beforeTakeover = state();
      await request(second, 'updateNoteLock', openPatient, 0, 200);
      const recovered = assertOwnedLock(rows(openPatient, 0, doctor.providerNo), {
        patient: openPatient, note: 0, provider: doctor.providerNo, sessions: second.sessions });
      h.assert(recovered[0] === own[0] && recovered[4] !== own[4], 'Recovery replaced the lock row or kept the old browser session');
      const foreignAfter = assertOwnedLock(rows(openPatient, openNote, provider), {
        patient: openPatient, note: openNote, provider, sessions: owner.sessions });
      h.assert(JSON.stringify(foreignAfter) === JSON.stringify(foreign), 'Same-provider recovery changed the foreign lock');
      for (const [index, patient] of patients.entries()) {
        const after = snapshot(sql, patient);
        assertPatientStateUnchanged({ ...beforeTakeover[index], locks: [] }, { ...after, locks: [] },
          'Same-provider recovery clinical/draft preservation');
        if (patient === openPatient) assertRecoveryRows(beforeTakeover[index].locks, after.locks,
          { columns: lockColumns, id: own[0], sessions: second.sessions });
        else h.assert(JSON.stringify(beforeTakeover[index].locks) === JSON.stringify(after.locks),
          'Same-provider recovery changed another patient lock');
      }
      const beforeGet = state();
      for (const method of ['isNoteEdited', 'updateNoteLock']) {
        await request(second, method, openPatient, 0, 405, 'GET');
        unchanged(beforeGet, `${method} GET refusal`);
      }
    });
  } catch (error) {
    primaryFailure = error;
    throw error;
  } finally {
    const failures = [];
    // Browser contexts must remain live until their own authenticated release completes.
    for (const actor of [...contexts].reverse()) {
      try {
        if (actor.csrf) for (const target of [...actor.releaseTargets.values()].reverse()) {
          const held = rows(target.patient, target.note, actor.account.providerNo);
          h.assert(held.every(row => actor.sessions && contexts.some(candidate =>
            candidate.account.providerNo === actor.account.providerNo && candidate.sessions?.has(row[4]))),
          'Native lock fixture ownership changed; refusing unowned recovery cleanup');
          await request(actor, 'releaseNoteLock', target.patient, target.note, 200);
        }
      } catch (error) { failures.push(error); }
      try { await actor.context.close(); h.assertStrictPage(actor.recorder); }
      catch (error) { failures.push(error); }
    }
    try { unchanged(beforeAll, 'Native lock endpoint cleanup'); } catch (error) { failures.push(error); }
    recordCleanupFailures(primaryFailure, failures);
  }
}

module.exports = { checkNativeNoteLocks, assertOwnedLock, sessionsForCookie, assertRecoveryRows, recordCleanupFailures };
