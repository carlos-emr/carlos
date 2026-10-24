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
const { csrfTokenPresent } = require('./playwright-ui');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');

const q = h.sqlString;
const key = value => {
  h.assert(/^[1-9]\d*$/.test(String(value)), 'The owned issue-deletion fixture key is invalid');
  return String(value);
};

/** Prove both writes are authorized before an issue linked to an existing note can be removed. */
async function checkIssueDeleteAuthorization(s, { fixture, patient, program, applicationStatus }) {
  const { sql, marker, config } = s;
  patient = key(patient);
  h.assert(fixture && typeof fixture.addRole === 'function', 'Issue deletion needs the workflow\'s shared role fixture');
  h.assert(/^(?:0|[1-9]\d*)$/.test(String(program)), 'The issue-deletion chart program is invalid');
  await s.step('an issue-only writer cannot partly delete an existing note\'s issue before note-write refusal', async () => {
    const role = fixture.addRole({ _demographic: 'r', _eChart: 'r', '_casemgmt.notes': 'r',
      '_casemgmt.issues': 'w', _appointment: 'r' });
    const actor = fixture.addLogin(role);
    fixture.joinTestLoginPrograms(actor);
    const noteText = `${marker}-issue-delete-owned-note`;
    let noteId; let issueId; let cppId;
    s.cleanup(() => {
      if (noteId) {
        h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${noteId}
          AND (demographic_no<>${patient} OR provider_no<>${q(actor.providerNo)})`) === '0',
        'Issue-deletion note ownership changed; refusing cleanup');
        sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id=${noteId};
          DELETE FROM casemgmt_note_ext WHERE note_id=${noteId};
          DELETE FROM casemgmt_note_link WHERE note_id=${noteId};
          DELETE FROM casemgmt_note WHERE note_id=${noteId} AND demographic_no=${patient}
            AND provider_no=${q(actor.providerNo)}`);
      }
      if (issueId) {
        h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_issue WHERE id=${issueId}
          AND demographic_no<>${patient}`) === '0', 'Issue-deletion issue ownership changed; refusing cleanup');
        sql.execute(`DELETE FROM casemgmt_issue_notes WHERE id=${issueId};
          DELETE FROM casemgmt_issue WHERE id=${issueId} AND demographic_no=${patient}`);
      }
      if (cppId) {
        h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_cpp WHERE id=${cppId}
          AND demographic_no=${patient} AND provider_no=${q(actor.providerNo)}
          AND reminders=${q(`${marker}-issue-delete-cpp`)}`) === '1',
        'Issue-deletion CPP ownership changed; refusing cleanup');
        sql.execute(`DELETE FROM casemgmt_cpp WHERE id=${cppId} AND demographic_no=${patient}
          AND provider_no=${q(actor.providerNo)}`);
      }
      sql.execute(`DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no=${q(actor.providerNo)};
        DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient} AND provider_no=${q(actor.providerNo)}`);
      const owned = [
        noteId && `(SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${noteId})`,
        noteId && `(SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id=${noteId})`,
        noteId && `(SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id=${noteId})`,
        noteId && `(SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id=${noteId})`,
        issueId && `(SELECT COUNT(*) FROM casemgmt_issue WHERE id=${issueId})`,
        issueId && `(SELECT COUNT(*) FROM casemgmt_issue_notes WHERE id=${issueId})`,
        cppId && `(SELECT COUNT(*) FROM casemgmt_cpp WHERE id=${cppId})`,
        `(SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no=${q(actor.providerNo)})`,
        `(SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient} AND provider_no=${q(actor.providerNo)})`,
      ].filter(Boolean);
      h.assert(sql.value(`SELECT ${owned.join(' + ')}`) === '0', 'Owned issue-deletion fixture rows survived cleanup');
    });
    const catalog = sql.rows("SELECT issue_id,role,description FROM issue WHERE code='CTCMM1000'");
    h.assert(catalog.length === 1, 'The issue-deletion fixture requires one catalog issue');
    const [catalogId, issueType, description] = catalog[0];
    key(catalogId);
    const reporterRole = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(actor.providerNo)}
      AND program_id=${Number(program)} ORDER BY id LIMIT 1`) || '0';
    h.assert(/^\d+$/.test(reporterRole), 'The issue-deletion note reporter role is invalid');
    noteId = key(h.insertId(sql, `INSERT INTO casemgmt_note
      (update_date,observation_date,demographic_no,provider_no,note,signed,history,uuid,locked,archived,
       program_no,reporter_caisi_role)
      VALUES (NOW(),'2026-10-14 10:00:00',${patient},${q(actor.providerNo)},${q(noteText)},0,${q(noteText)},
        UUID(),'0',0,${q(String(program))},${q(reporterRole)})`, 'issue-deletion note'));
    issueId = key(h.insertId(sql, `INSERT INTO casemgmt_issue
      (demographic_no,issue_id,acute,certain,major,resolved,program_id,type,update_date)
      VALUES (${patient},${catalogId},0,0,0,0,${Number(program)},${q(issueType)},NOW())`, 'issue-deletion issue'));
    sql.execute(`INSERT INTO casemgmt_issue_notes (id,note_id) VALUES (${issueId},${noteId})`);
    if (sql.value(`SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no=${patient}`) === '0') {
      cppId = key(h.insertId(sql, `INSERT INTO casemgmt_cpp (demographic_no,provider_no,reminders,ongoingConcerns)
        VALUES (${patient},${q(actor.providerNo)},${q(`${marker}-issue-delete-cpp`)},${q(description)})`, 'issue-deletion CPP'));
    }
    const context = await h.newContext(s.context.browser(), config);
    context.on('page', page => h.wireStrictPage(page, 'issue-delete-restricted-login', s.recorder));
    try {
      const page = await h.login(context, { ...config, testUser: actor.username }, s.recorder);
      const csrf = await csrfTokenPresent(page);
      // These authenticated API reads set up a negative permission probe without loading
      // unrelated chart modules that this deliberately restricted actor cannot access.
      const entry = await context.request.get(h.appUrl(config.baseUrl, `/encounter/IncomingEncounter?${new URLSearchParams({
        demographicNo: patient, providerNo: actor.providerNo, programId: String(program),
      })}`), { failOnStatusCode: false });
      let token;
      try {
        h.assert(entry.status() === 200, `Restricted chart entry returned HTTP ${entry.status()}`);
        const destination = new URL(entry.url());
        token = destination.searchParams.get('chartContext');
        h.assert(destination.searchParams.get('demographicNo') === patient && token,
          'Restricted chart entry did not issue its own patient context');
      } finally { await entry.dispose(); }
      const endpoint = h.appUrl(config.baseUrl, '/CaseManagementEntry');
      const editor = await context.request.post(endpoint, {
        form: { method: 'edit', noteId, forceNote: 'true', ajax: 'true', from: 'casemgmt',
          demographicNo: patient, chartContext: token, 'CSRF-TOKEN': csrf },
        headers: { 'CSRF-TOKEN': csrf }, failOnStatusCode: false, maxRedirects: 0,
      });
      let deletionIndex;
      try {
        h.assert(editor.status() === 200, `Restricted note editor returned HTTP ${editor.status()}`);
        const body = await editor.text();
        const ownedInputs = [...body.matchAll(/<input\b[^>]*>/g)]
          .map(match => match[0]).filter(tag => tag.includes(`data-issue-id="${issueId}"`));
        h.assert(body.includes(`id="summary${noteId}"`) && ownedInputs.length === 1,
          'The authorized read did not bind the owned note and linked issue');
        const field = /\bname="issueCheckList\[(\d+)\]\.checked"/.exec(ownedInputs[0]);
        h.assert(field, 'The owned issue has no explicit editor index');
        deletionIndex = field[1];
      } finally { await editor.dispose(); }
      const baseline = readPatientState(sql, patient);
      const locks = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`));
      const refused = await context.request.post(endpoint, {
        form: { method: 'issueDelete', deleteId: deletionIndex, demographicNo: patient, chartContext: token,
          observation_date: '14-Oct-2026 10:00', 'CSRF-TOKEN': csrf },
        headers: { 'CSRF-TOKEN': csrf }, failOnStatusCode: false, maxRedirects: 0,
      });
      try {
        if (applicationStatus) await applicationStatus(refused, 403, 'Issue deletion without note-write access');
        h.assert(refused.status() === 403, `Issue deletion without note-write access returned HTTP ${refused.status()}`);
        await h.assertRefused(s, { response: refused, table: 'casemgmt_issue',
          where: `id=${issueId} AND demographic_no=${patient}`, before: '1', label: 'Issue deletion without note-write access' });
      } finally { await refused.dispose(); }
      assertPatientStateUnchanged(baseline, readPatientState(sql, patient), 'Refused issue deletion');
      h.assert(JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`)) === locks,
        'Refused issue deletion changed an existing note lock');
    } finally { await context.close(); }
  });
}

module.exports = { checkIssueDeleteAuthorization };
