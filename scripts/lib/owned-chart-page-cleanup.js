/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const h = require('./playwright-harness');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/** Test fixture cleanup only, never a test of the application's native unload transport. */
async function retireOwnedChartPages(s, pages, evidence) {
  const cookies = (await s.context.cookies(s.config.baseUrl.href)).filter(cookie => cookie.name === 'JSESSIONID');
  h.assert(cookies.length === 1 && cookies[0].value, 'Owned chart cleanup has no unique authenticated session');
  const cookie = cookies[0].value;
  const core = cookie.split('.')[0];
  const sessions = new Set([cookie, core]);
  for (const { chart, patient, owner } of pages) {
    if (chart.isClosed()) continue;
    h.assert(/^[1-9]\d*$/.test(patient) && [s.marker, `${s.marker}-ida`].includes(owner)
      && s.sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND last_name=${q(owner)}`) === '1',
    'Auxiliary chart cleanup requires its exact owned patient');
    const form = await chart.evaluate(() => {
      const editor = document.forms.caseManagementEntryForm;
      return editor && { patient: String(demographicNo), provider: String(providerNo),
        note: String(editor.elements.noteId.value), token: editor.elements.chartContext.value,
        csrf: CarlosAjax.getCsrfToken() };
    });
    h.assert(form && form.patient === patient && form.provider === s.provider && /^\d+$/.test(form.note)
      && form.csrf && Buffer.from(form.token.split('.')[0], 'base64url').toString('utf8').split(':')[0] === patient,
    'Auxiliary cleanup form lost its actual patient/provider/note/context/CSRF');
    const before = readPatientState(s.sql, patient);
    const policy = () => Object.fromEntries(Object.entries({ provider_default_program: 'id', program_provider: 'id',
      admission: 'am_id', default_role_access: 'id' }).map(([table, key]) => [table, s.sql.rows(`SELECT * FROM ${table} ORDER BY ${key}`)]));
    const beforePolicy = policy();
    const locks = s.sql.rows('SELECT id,session_id,ip_address,provider_no,note_id,demographic_no,lock_acquired FROM casemgmt_note_lock ORDER BY id');
    const matchingNote = row => row[3] === s.provider && row[4] === form.note && row[5] === patient;
    h.assert(!locks.some(row => matchingNote(row) && row[1].startsWith(`${core}.`) && !sessions.has(row[1])),
      'Auxiliary cleanup found a different route suffix; retaining all locks');
    const owned = locks.filter(row => sessions.has(row[1]) && matchingNote(row));
    h.assert(owned.length <= 1, 'Auxiliary cleanup found ambiguous full/core session locks');
    const response = await s.context.request.post(h.appUrl(s.config.baseUrl, '/CaseManagementEntry'), {
      form: { method: 'releaseNoteLock', demographicNo: patient, providerNo: s.provider, noteId: form.note,
        chartContext: form.token, 'CSRF-TOKEN': form.csrf }, maxRedirects: 0, timeout: 15000,
    });
    try {
      h.assert(response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
        'Auxiliary owned lock release did not return application200');
    } finally { await response.dispose(); }
    assertPatientStateUnchanged(before, readPatientState(s.sql, patient), 'Auxiliary test-page lock cleanup');
    h.assert(same(beforePolicy, policy()), 'Auxiliary test-page cleanup changed program, admission or role policy');
    h.assert(same(s.sql.rows('SELECT id,session_id,ip_address,provider_no,note_id,demographic_no,lock_acquired FROM casemgmt_note_lock ORDER BY id'),
      locks.filter(row => !owned.includes(row))), 'Auxiliary cleanup changed other editor locks');
    const acknowledged = await chart.evaluate(expected => {
      const editor = document.forms.caseManagementEntryForm;
      if (!editor || String(demographicNo) !== expected.patient || String(providerNo) !== expected.provider
        || String(editor.elements.noteId.value) !== expected.note || editor.elements.chartContext.value !== expected.token) return false;
      needToReleaseLock = false;
      return true;
    }, form);
    h.assert(acknowledged, 'Auxiliary chart identity changed after its acknowledged cleanup');
    evidence.push({ phase: 'authenticated owned test-page cleanup; native transport not tested', patient,
      provider: s.provider, note: form.note, applicationStatus: 200, removedLockIds: owned.map(row => row[0]),
      clinicalDraftsOtherLocksUnchanged: true });
    await chart.close();
  }
}
module.exports = { retireOwnedChartPages };
