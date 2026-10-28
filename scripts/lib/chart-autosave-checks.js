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
'use strict';
const h = require('./playwright-harness');
const { expectValue } = require('./workflow-session');
const { checkLegacyFullFormIsolation } = require('./legacy-full-form-save-checks');

const NOTE = '#caseManagementEntryForm textarea[name="caseNote_note"]';
const isAutosave = request => request.method() === 'POST'
  && new URL(request.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(request.postData() || '').get('method') === 'autosave';

let failureSequence = 0;
/** A real database failure restricted to one owned patient's exact draft and provider. */
function rejectOwnedDraftInsert(s, patient, text) {
  h.assert(/^[1-9]\d*$/.test(patient) && /^FAKE-PW[0-9a-f]{16}$/.test(s.marker)
    && text.startsWith(s.marker), 'A rollback probe must name an owned patient and stamped draft');
  const name = `pw_draft_${s.marker.slice(7)}_${++failureSequence}`;
  const predicate = `TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=${h.sqlString(name)}`;
  h.assert(s.sql.value(`SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE ${predicate}`) === '0',
    'The rollback probe trigger name is already in use');
  let definition;
  const drop = () => {
    const rows = s.sql.rows(`SELECT ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE ${predicate}`);
    if (rows.length) {
      h.assert(rows.length === 1 && (!definition || rows[0][0] === definition)
        && rows[0][0].includes(h.sqlString(text)), 'Rollback trigger ownership changed');
      s.sql.execute(`DROP TRIGGER ${name}`);
    }
    h.assert(s.sql.value(`SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE ${predicate}`) === '0',
      'The owned rollback trigger survived cleanup');
  };
  s.cleanup(drop);
  s.sql.execute(`DELIMITER //
CREATE TRIGGER ${name} BEFORE INSERT ON casemgmt_tmpsave FOR EACH ROW
BEGIN
  IF NEW.demographic_no=${patient} AND NEW.provider_no=${h.sqlString(s.provider)}
      AND NEW.note=${h.sqlString(text)} THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Owned autosave rollback probe';
  END IF;
END//
DELIMITER ;`);
  definition = s.sql.value(`SELECT ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE ${predicate}`);
  h.assert(definition && definition.includes(h.sqlString(text)), 'The owned rollback probe was not installed');
  return drop;
}

/** Drive the standalone legacy note editor through a genuine failed replacement and retry. */
async function checkLegacyAutosave(s, { patient, chartContext, opener = s.schedule }) {
  const { context, config, sql, provider, marker, recorder } = s;
  h.assert(/^[1-9]\d*$/.test(patient) && chartContext, 'Legacy autosave needs an owned chart context');
  const url = h.appUrl(config.baseUrl, `/CaseManagementEntry?${new URLSearchParams({
    method: 'edit', note_edit: 'new', from: 'casemgmt', demographicNo: patient,
    providerNo: provider, chartContext, forceNote: 'true',
  })}`);
  let editor;
  await s.step('the legacy note editor reports an actual autosave failure and saves retained text on retry', async () => {
    const opened = context.waitForEvent('page');
    opened.catch(() => {}); // Preserve the original rejection while allowing fixture cleanup.
    await opener.evaluate(target => { window.open(target, '_blank'); }, url);
    editor = await opened;
    await editor.locator(NOTE).waitFor({ state: 'visible' });
    h.assert(await editor.locator('input[name="chartContext"]').inputValue() === chartContext,
      'Legacy editor lost the originating chart context');
    const status = editor.locator('#legacyAutosaveStatus[role="status"][aria-live="polite"][aria-atomic="true"]');
    await status.waitFor({ state: 'attached' });
    const draftQuery = `SELECT * FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${h.sqlString(provider)} ORDER BY id`;
    const savedText = `${marker}-legacy-original`;
    const retryText = `${marker}-legacy-retry H&P + retained`;
    const send = async (text, expectedStatus) => {
      await editor.locator(NOTE).fill(text);
      const responsePromise = editor.waitForResponse(response => isAutosave(response.request()));
      responsePromise.catch(() => {});
      await editor.evaluate(() => autoSave());
      const response = await responsePromise;
      await response.finished();
      h.assert(response.status() === expectedStatus, `Legacy autosave returned HTTP ${response.status()}`);
      await editor.waitForFunction(() => !window.legacyAutosavePending);
      return response;
    };
    await send(savedText, 200);
    h.assert(await status.getAttribute('data-autosave-state') === 'saved', 'Legacy autosave did not confirm success');
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${h.sqlString(provider)} ORDER BY id DESC LIMIT 1`, savedText,
    'Legacy autosave did not persist the exact text');
    const baseline = JSON.stringify(sql.rows(draftQuery));
    const beforeResponses = new Set(recorder.badResponses);
    const beforeConsole = new Set(recorder.consoleIssues);
    const dropFailure = rejectOwnedDraftInsert(s, patient, retryText);
    let response;
    try {
      response = await send(retryText, 500);
      h.assert(await status.getAttribute('data-autosave-state') === 'failed'
        && /could not be saved/i.test(await status.innerText()),
      'Legacy failed autosave still reports a saved draft');
      h.assert(await editor.locator(NOTE).inputValue() === retryText, 'Legacy failed autosave discarded the editor text');
      h.assert(JSON.stringify(sql.rows(draftQuery)) === baseline, 'Legacy failed replacement changed the prior draft');
    } finally { dropFailure(); }
    // Retain all unrelated errors. Only the response just asserted and its matching
    // Chromium resource diagnostic are intentional evidence for this negative control.
    const responses = recorder.badResponses.filter(entry => !beforeResponses.has(entry)
      && entry.url === response.url() && entry.method === 'POST' && entry.status === 500);
    h.assert(responses.length === 1, 'The controlled legacy autosave failure was not recorded exactly once');
    const diagnostics = recorder.consoleIssues.filter(entry => !beforeConsole.has(entry)
      && entry.location?.url === response.url() && /^Failed to load resource:.*500/.test(entry.text));
    h.assert(diagnostics.length <= 1, 'The controlled legacy failure emitted duplicate resource diagnostics');
    recorder.badResponses = recorder.badResponses.filter(entry => !responses.includes(entry));
    recorder.consoleIssues = recorder.consoleIssues.filter(entry => !diagnostics.includes(entry));
    await send(retryText, 200);
    h.assert(await status.getAttribute('data-autosave-state') === 'saved', 'Legacy retry did not replace the failure status');
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${h.sqlString(provider)} ORDER BY id DESC LIMIT 1`, retryText,
    'Legacy retry did not save the retained clinical text');
    const acknowledged = JSON.stringify(sql.rows(draftQuery));
    const match = address => address.pathname.endsWith('/CaseManagementEntry');
    let loginPages = 0;
    const expired = route => {
      if (!isAutosave(route.request())) return route.continue();
      loginPages++;
      return route.fulfill({ status: 200, contentType: 'text/html',
        body: '<!doctype html><title>Session expired</title><p>Please sign in again.</p>' });
    };
    await editor.route(match, expired);
    try {
      await send(`${retryText}-after-timeout`, 200);
      h.assert(await status.getAttribute('data-autosave-state') === 'failed'
        && await editor.locator(NOTE).inputValue() === `${retryText}-after-timeout`,
      'A legacy login page was acknowledged as a saved draft or discarded text');
      h.assert(loginPages === 1 && JSON.stringify(sql.rows(draftQuery)) === acknowledged,
        'The legacy login-page probe changed the acknowledged draft');
    } finally { await editor.unroute(match, expired); }
    await send(`${retryText}-after-timeout`, 200);
    h.assert(await status.getAttribute('data-autosave-state') === 'saved', 'Legacy timeout recovery did not confirm the real save');
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${h.sqlString(provider)} ORDER BY id DESC LIMIT 1`, `${retryText}-after-timeout`,
    'Legacy timeout recovery lost the retained clinical text');
    await editor.close();
  });
}

/** An implicit existing-note selection must post its actual authorized ID. */
async function checkLegacyExistingNoteAutosave(s, { patient, chartContext, program }) {
  const { sql, provider, marker, context, config } = s;
  h.assert(/^[1-9]\d*$/.test(patient) && /^\d+$/.test(program), 'Existing-note probe needs its owned patient/program');
  await s.step('the legacy editor autosaves the actual implicitly selected unsigned note', async () => {
    const text = `${marker}-legacy-selected-note`;
    const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${h.sqlString(provider)}
      AND program_id=${program} ORDER BY id LIMIT 1`) || '0';
    h.assert(/^\d+$/.test(role), 'The owned legacy note role is invalid');
    let id;
    s.cleanup(() => {
      if (!id) return;
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${id}
        AND demographic_no=${patient} AND note=${h.sqlString(text)}`) === '1', 'Legacy selected-note ownership changed');
      sql.execute(`DELETE FROM casemgmt_note WHERE note_id=${id} AND demographic_no=${patient}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${id}`) === '0', 'The owned selected note survived cleanup');
    });
    id = h.insertId(sql, `INSERT INTO casemgmt_note
      (update_date,observation_date,demographic_no,provider_no,note,signed,history,uuid,locked,archived,
       program_no,reporter_caisi_role)
      VALUES (NOW(),NOW(),${patient},${h.sqlString(provider)},${h.sqlString(text)},0,${h.sqlString(text)},
        UUID(),'0',0,${h.sqlString(program)},${h.sqlString(role)})`, 'implicitly selected unsigned note');
    h.assert(/^[1-9]\d*$/.test(id), 'The selected note fixture was not created');
    const url = h.appUrl(config.baseUrl, `/CaseManagementEntry?${new URLSearchParams({
      method: 'edit', from: 'casemgmt', demographicNo: patient, providerNo: provider,
      chartContext, forceNote: 'true',
    })}`);
    const opened = context.waitForEvent('page');
    opened.catch(() => {});
    await s.schedule.evaluate(target => { window.open(target, '_blank'); }, url);
    const editor = await opened;
    await editor.locator(NOTE).waitFor({ state: 'visible' });
    h.assert(await editor.locator(NOTE).inputValue() === text, 'Legacy fallback selected another note');
    await editor.locator(NOTE).fill(`${text}-draft`);
    const sent = editor.waitForResponse(response => isAutosave(response.request()));
    sent.catch(() => {});
    await editor.evaluate(() => autoSave());
    const response = await sent;
    await response.finished();
    h.assert(response.status() === 200
      && new URLSearchParams(response.request().postData() || '').get('note_id') === id,
    'The implicit existing-note autosave lost its actual selected note ID');
    await editor.locator('#legacyAutosaveStatus[data-autosave-state="saved"]').waitFor();
    await expectValue(sql, `SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND provider_no=${h.sqlString(provider)} AND program_id=${program} ORDER BY id DESC LIMIT 1`, `${text}-draft`,
    'The implicit existing-note draft was not persisted');
    h.assert(sql.value(`SELECT note FROM casemgmt_note WHERE note_id=${id}`) === text,
      'Autosaving the existing note mutated its saved clinical text');
    await editor.close();
  });
  await checkLegacyFullFormIsolation(s, { program });
}

module.exports = { checkLegacyAutosave, checkLegacyExistingNoteAutosave, rejectOwnedDraftInsert };
