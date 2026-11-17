/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const { randomUUID } = require('node:crypto');
const h = require('./playwright-harness');
const { expectValue } = require('./workflow-session');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');

const q = h.sqlString;
const NOTE = '#caseManagementEntryForm textarea[name="caseNote_note"]';
const isSave = response => response.request().method() === 'POST'
  && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(response.request().postData() || '').get('method') === 'save';

/** Select actual visible legacy controls, leaving the rendered note identity and CSRF fields intact. */
async function prepareEditor(editor, { noteId, text, chartContext, description }) {
  await editor.waitForLoadState('load');
  await editor.locator(NOTE).waitFor({ state: 'visible' });
  h.assert(await editor.locator(NOTE).inputValue() === text, 'Legacy full-form editor selected another note');
  h.assert(await editor.locator('input[name="noteId"]').inputValue() === noteId
    && await editor.locator('input[name="chartContext"]').inputValue() === chartContext,
  'Legacy full-form editor did not render its own note and chart identity');
  await editor.waitForFunction(() => !window.legacyAutosavePending);
  await editor.evaluate(() => clearTimeout(legacyAutosaveTimer));
  const fields = await editor.locator('input[type="checkbox"][name^="issueCheckList["]').evaluateAll((inputs, caption) =>
    inputs.filter(input => input.closest('tr')?.cells[1]?.textContent.trim() === caption.trim())
      .map(input => ({ name: input.name, disabled: input.disabled })), description);
  h.assert(fields.length === 1 && !fields[0].disabled
    && /^issueCheckList\[\d+\]\.checked$/.test(fields[0].name),
  'Legacy full-form editor has no unique enabled checkbox for its owned issue');
  await editor.locator(`input[name="${fields[0].name}"]`).check();
  await editor.locator('select[name="caseNote.encounter_type"]').selectOption('face to face encounter with client');
  await editor.locator('input[name="sign"]').uncheck();
  await editor.locator('input[name="includeIssue"]').uncheck();
}

async function nativeSave(editor) {
  let response;
  const dialogs = await h.withExpectedDialogs(editor, async () => {
    const answer = editor.waitForResponse(isSave);
    answer.catch(() => {});
    await editor.locator('input[type="submit"][value="Save"]').click();
    response = await answer;
    await response.finished();
    await editor.waitForLoadState('load');
  });
  h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm'
    && dialogs[0].text === 'Are you sure that you want to save without signing?',
  'Native legacy Save did not pass its normal unsigned-note confirmation');
  h.assert(Object.hasOwn(response.headers(), h.APPLICATION_HEADER)
    && !/ModSecurity|<center>nginx<\/center>/i.test(await response.text()),
  'Native legacy Save did not reach the application');
  return response;
}

/** Legacy init reloads its opener; wait for that next load rather than the current document's state. */
async function withOpenerReload(opener, action) {
  const reloaded = opener.waitForEvent('load');
  reloaded.catch(() => {});
  const result = await action();
  await reloaded;
  return result;
}

/** Two legacy windows for one patient/program must not save through the later window's session form. */
async function checkLegacyFullFormIsolation(s, { program }) {
  const { sql, provider, marker, context, config } = s;
  h.assert(/^(?:0|[1-9]\d*)$/.test(String(program)), 'Legacy full-form fixture program is invalid');
  const stamp = `${marker}-lff`;
  h.assert(stamp.length <= 30, 'Legacy full-form fixture name exceeds the patient schema');
  const uuids = [randomUUID(), randomUUID()];
  let patient; let issueId;
  s.cleanup(() => {
    if (!patient) return;
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${q(stamp)}`) === '1', 'Legacy full-form patient ownership changed');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND (provider_no IS NULL OR provider_no<>${q(provider)} OR uuid IS NULL
        OR uuid NOT IN (${uuids.map(q).join(',')}))`) === '0',
    'Legacy full-form note ownership changed; refusing cleanup');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient}
      ${issueId ? `AND id<>${issueId}` : ''}`) === '0',
    'Legacy full-form issue ownership changed; refusing cleanup');
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}
      AND uuid IN (${uuids.map(q).join(',')})`;
    if (issueId) h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_issue_notes WHERE id=${issueId}
      AND note_id NOT IN (${notes})`) === '0', 'Legacy full-form issue acquired a foreign note association');
    sql.execute(`DELETE FROM hash_audit WHERE type='enc' AND id IN (${notes});
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND uuid IN (${uuids.map(q).join(',')});
      ${issueId ? `DELETE FROM casemgmt_issue WHERE id=${issueId} AND demographic_no=${patient};` : ''}
      DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient};
      DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};
      DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
      DELETE FROM demographicExt WHERE demographic_no=${patient};
      DELETE FROM demographicArchive WHERE demographic_no=${patient};
      DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(stamp)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})`) === '0',
    'Legacy full-form fixture rows survived cleanup');
  });
  patient = h.insertId(sql, `INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
     provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(stamp)},'LegacyForms','1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW())`,
  'legacy full-form patient');
  h.assert(/^[1-9]\d*$/.test(patient), 'Legacy full-form patient was not created');
  const catalog = sql.rows("SELECT issue_id,role,description FROM issue WHERE code='CTCMM1000'");
  h.assert(catalog.length === 1 && /^[1-9]\d*$/.test(catalog[0][0]), 'Legacy full-form issue catalog is ambiguous');
  const [catalogId, issueType, description] = catalog[0];
  issueId = h.insertId(sql, `INSERT INTO casemgmt_issue
    (demographic_no,issue_id,acute,certain,major,resolved,program_id,type,update_date)
    VALUES (${patient},${catalogId},0,0,0,0,${program},${q(issueType)},NOW())`, 'legacy full-form issue');
  h.assert(/^[1-9]\d*$/.test(issueId), 'Legacy full-form issue was not created');
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)}
    AND program_id=${program} ORDER BY id LIMIT 1`) || '0';
  h.assert(/^\d+$/.test(role), 'Legacy full-form reporter role is invalid');
  const original = [`${stamp}-note-A`, `${stamp}-note-B`];
  const ids = original.map((text, index) => h.insertId(sql, `INSERT INTO casemgmt_note
    (update_date,observation_date,demographic_no,provider_no,note,signed,history,uuid,locked,archived,
     program_no,reporter_caisi_role,encounter_type)
    VALUES (NOW(),NOW(),${patient},${q(provider)},${q(text)},0,${q(text)},${q(uuids[index])},'0',0,
      ${q(String(program))},${q(role)},'face to face encounter with client')`, 'legacy full-form note'));
  h.assert(ids.every(id => /^[1-9]\d*$/.test(id)) && ids[0] !== ids[1], 'Legacy full-form note identities are invalid');
  sql.execute(ids.map(id => `INSERT INTO casemgmt_issue_notes (id,note_id) VALUES (${issueId},${id})`).join(';'));
  const entry = await context.request.get(h.appUrl(config.baseUrl,
    `/encounter/IncomingEncounter?${new URLSearchParams({ demographicNo: patient, providerNo: provider, programId: String(program) })}`));
  let chartContext;
  try {
    const destination = new URL(entry.url());
    chartContext = destination.searchParams.get('chartContext');
    h.assert(entry.status() === 200 && destination.searchParams.get('demographicNo') === patient && chartContext,
      'Legacy full-form patient did not receive an authenticated chart context');
  } finally { await entry.dispose(); }
  // Legacy init reloads its opener. A dedicated static document provides that
  // real same-origin relationship without the schedule's periodic navigation.
  const opener = await context.newPage();
  const openerResponse = await h.gotoApp(opener, config.baseUrl, '/images/favicon.ico', 'load');
  h.assert(openerResponse?.status() === 200, 'Legacy full-form opener did not load its static document');
  const editors = [];
  for (let index = 0; index < ids.length; index++) {
    const url = h.appUrl(config.baseUrl, `/CaseManagementEntry?${new URLSearchParams({
      method: 'edit', noteId: ids[index], forceNote: 'true', from: 'casemgmt',
      demographicNo: patient, providerNo: provider, chartContext,
    })}`);
    const editor = await withOpenerReload(opener, async () => {
      const opened = context.waitForEvent('page');
      opened.catch(() => {});
      await opener.evaluate(target => { window.open(target, '_blank'); }, url);
      const page = await opened;
      await prepareEditor(page, { noteId: ids[index], text: original[index], chartContext, description });
      return page;
    });
    editors.push(editor);
  }
  const [first, second] = editors;
  const draftA = `${stamp}-A-retained H&P + follow-up`;
  const draftB = `${stamp}-B-authorized H&P + follow-up`;
  await s.step('a stale legacy full-form Save refuses window A without changing either note or window B\'s lock', async () => {
    await first.locator(NOTE).fill(draftA);
    const baseline = readPatientState(sql, patient);
    const lockQuery = `SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient}
      AND provider_no=${q(provider)} AND note_id=${ids[1]} ORDER BY id`;
    const locks = sql.rows(lockQuery);
    h.assert(locks.length === 1, 'Legacy window B does not own exactly one note lock');
    const before = { responses: new Set(s.recorder.badResponses), console: new Set(s.recorder.consoleIssues) };
    const response = await nativeSave(first);
    h.assert(response.status() === 409, `Stale legacy full-form Save returned HTTP ${response.status()}`);
    const submitted = new URLSearchParams(response.request().postData() || '');
    h.assert(submitted.get('noteId') === ids[0] && submitted.get('caseNote_note') === draftA
      && submitted.get('demographicNo') === patient && submitted.get('chartContext') === chartContext,
    'Native legacy Save did not submit window A\'s rendered note identity and draft');
    await first.locator('#unsaved-note-text[readonly]').waitFor({ state: 'visible' });
    h.assert(await first.locator('#unsaved-note-text').inputValue() === draftA,
      'The stale legacy Save recovery page lost window A\'s exact draft');
    assertPatientStateUnchanged(baseline, readPatientState(sql, patient), 'Stale legacy full-form Save');
    h.assert(JSON.stringify(sql.rows(lockQuery)) === JSON.stringify(locks)
      && await second.locator('input[name="noteId"]').inputValue() === ids[1]
      && await second.locator(NOTE).inputValue() === original[1],
    'Stale legacy Save changed window B\'s current editor or lock');
    const refused = s.recorder.badResponses.filter(item => !before.responses.has(item)
      && item.url === response.url() && item.method === 'POST' && item.status === 409);
    h.assert(refused.length === 1, 'Stale legacy Save was not recorded exactly once');
    const diagnostics = s.recorder.consoleIssues.filter(item => !before.console.has(item)
      && item.location?.url === response.url() && /^Failed to load resource:.*409/.test(item.text));
    h.assert(diagnostics.length <= 1, 'Stale legacy Save emitted duplicate resource diagnostics');
    s.recorder.badResponses = s.recorder.badResponses.filter(item => !refused.includes(item));
    s.recorder.consoleIssues = s.recorder.consoleIssues.filter(item => !diagnostics.includes(item));
  });
  await s.step('window B\'s native legacy Save persists only its own new revision after the refused window A save', async () => {
    const previousRows = sql.rows(`SELECT * FROM casemgmt_note WHERE note_id IN (${ids.join(',')}) ORDER BY note_id`);
    await second.locator(NOTE).fill(draftB);
    const response = await withOpenerReload(opener, () => nativeSave(second));
    h.assert(response.status() === 200, `Authorized legacy full-form Save returned HTTP ${response.status()}`);
    h.assert(new URLSearchParams(response.request().postData() || '').get('noteId') === ids[1],
      'Authorized legacy Save submitted another window\'s note identity');
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND uuid=${q(uuids[1])} AND note=${q(draftB)}`, '1', 'Window B\'s native Save lost or duplicated its draft');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND uuid=${q(uuids[0])}`) === '1'
      && sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND uuid=${q(uuids[1])}`) === '2'
      && JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id IN (${ids.join(',')}) ORDER BY note_id`))
        === JSON.stringify(previousRows), 'Authorized legacy Save changed an original note or another revision chain');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND uuid=${q(uuids[1])}
      AND note=${q(draftB)} AND LOCATE(${q(original[1])},history)>0 AND program_no=${q(String(program))}
      AND provider_no=${q(provider)}`) === '1', 'Window B\'s saved revision lost its own history, program or author');
  });
  await Promise.all(editors.map(editor => editor.close()));
  await opener.close();
}

module.exports = { checkLegacyFullFormIsolation };
