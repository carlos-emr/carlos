#!/usr/bin/env node
/*
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
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
/*
 * Issue #5276: every referral status the form offers, through its radio controls, and back.
 * User path: Schedule ▸ Consultations ▸ the owned referral (ViewRequest popup) ▸ Status ▸ Update ▸ the list (with and
 * without "Include completed") ▸ reopen.
 * Transitions driven: Nothing (1) ▸ Pending Specialist Callback (2) ▸ Pending Patient Callback (3) ▸ Completed (4) ▸
 * Pending Specialist Callback (2, reopening a completed referral) ▸ Nothing (1). For each one:
 *   - consultationRequests.status is the chosen value and the update archived exactly the previous version with the
 *     previous status (consultationRequestsArchive);
 *   - the reopened form has that radio checked;
 *   - the list row shows the status code (ND, SR, PR, DONE) in its status class; a completed referral leaves the
 *     default list and shows only with Include completed, and returns to the default list when reopened;
 *   - the appointment date and time, the appointment notes, the attached document and the patient are unchanged.
 * Also: pressing Update again with the same status keeps it (one more archived version, as every Update makes);
 * closing the window over an unsaved status changes nothing; the control referral (another referral of the same
 * patient) is unchanged throughout.
 * Booking Confirmed (status 5) is offered only on an Ocean eReferral: a referral carrying the ereferral_ref extension
 * the Ocean integration writes (EctViewRequest2Action). Such a referral is managed in Ocean, so the form renders it
 * without any save control. With a synthetic eReferral fixture (the three ereferral_* extension rows) the check asserts
 * that Booking Confirmed is offered there, with its stored status checked, and that the form offers no Update or Submit
 * control and changes nothing when closed; the status itself is set by Ocean and cannot be driven from CARLOS (a NOTE
 * says so). A native referral does not offer it.
 * Fixtures: lib/consultation-referral-fixture.js (FAKE patient, service, consultant, three referrals, the third with
 * synthetic ereferral_* extension rows, and a FAKE chart document attached to the first, seeded by SQL). The service and consultant are in the clinic-wide pickers while the
 * run lasts, so the check must not run concurrently with other checks (live wrapper: EXCLUSIVE=1). Cleanup removes
 * every owned row and asserts it gone.
 */
const h = require('./lib/playwright-harness');
const {
  UPDATED, listRow, openConsultationsList, openFromList, ownedReferrals, refreshList, save,
} = require('./lib/consultation-referral-fixture');
const { runWorkflow } = require('./lib/workflow-session');

const CODES = { 1: 'ND', 2: 'SR', 3: 'PR', 4: 'DONE' };
const PATH = ['2', '3', '4', '2', '1'];

const STEP = {
  native: 'a native referral offers Nothing, the two callbacks and Completed, and not Booking Confirmed',
  ereferral: 'an Ocean eReferral offers Booking Confirmed but no save control',
  transition: (from, to) => `status ${from} to ${to} is stored, archived, reopened and listed`,
  repeat: 'pressing Update again with the same status keeps it',
  cancel: 'closing the window over an unsaved status changes nothing',
};

async function workflow(s) {
  const { sql, marker, patient } = s;
  const referrals = ownedReferrals(s);
  const reason = `${marker} status referral`;
  const controlReason = `${marker} control referral`;
  const appointmentDate = sql.value("SELECT DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 21 DAY),'%Y-%m-%d')");
  const notes = `${marker} booked for the morning`;
  const requestId = referrals.seed({ reason, appointmentDate, appointmentTime: '09:45:00', statusText: notes });
  const controlId = referrals.seed({ reason: controlReason, status: '3' });
  const documentNo = referrals.document(`${marker}-attached-letter`);
  referrals.attach(requestId, documentNo);

  const preserved = () => JSON.stringify([referrals.field(requestId, 'appointmentDate'), referrals.field(requestId, 'appointmentTime'),
    referrals.field(requestId, 'statusText'), referrals.field(requestId, 'reason'), referrals.field(requestId, 'demographicNo'),
    referrals.attachments(requestId)]);
  const expectedPreserved = JSON.stringify([appointmentDate, '09:45:00', notes, reason, String(patient), [`D:${documentNo}`]]);
  const controlUnchanged = () => h.assert(referrals.field(controlId, 'status') === '3' && referrals.field(controlId, 'reason') === controlReason
    && referrals.archived(controlId).length === 0, 'The control referral changed');
  const archivedStatuses = () => referrals.archived(requestId, ['status']).map(([status]) => status);

  const list = await openConsultationsList(s);
  const includeCompleted = list.locator('#includeCompleted');
  const open = label => openFromList(s, list, requestId, reason, label);

  /** The status the list shows for the referral, or null when the row is not listed. */
  async function listed() {
    await refreshList(list);
    const row = listRow(list, requestId);
    if (await row.count() === 0) return null;
    const cell = row.locator('td').first();
    return { cls: await cell.getAttribute('class'), code: (await cell.innerText()).trim(), patient: await row.innerText() };
  }

  await s.step(STEP.native, async () => {
    h.assert(preserved() === expectedPreserved, `The fixture referral is not as seeded: ${preserved()}`);
    const form = await open('consult-status-native');
    const offered = await form.locator('input[type="radio"][name="status"]').evaluateAll(radios => radios.map(r => r.value));
    h.assert(JSON.stringify(offered) === JSON.stringify(['1', '2', '3', '4']),
      `A native referral offers the statuses ${JSON.stringify(offered)}, not Nothing, the two callbacks and Completed`);
    h.assert(await form.locator('#status1').isChecked(), 'The referral did not open on its saved status');
    h.assert(await form.locator('#status5').count() === 0, 'A native referral offers Booking Confirmed');
    await form.close();
  });

  await s.step(STEP.ereferral, async () => {
    const oceanReason = `${marker} ocean eReferral`;
    const oceanId = referrals.seed({ reason: oceanReason, status: '5' });
    sql.execute(['ereferral_ref', 'ereferral_service', 'ereferral_doctor'].map((name, index) => `INSERT INTO consultationRequestExt
      (requestId,name,value,dateCreated) VALUES (${oceanId},${h.sqlString(name)},${h.sqlString([`${marker}-ref`, 'Synthetic Ocean service',
      'Synthetic Ocean consultant'][index])},CURDATE())`).join(';'));
    await refreshList(list);
    const row = listRow(list, oceanId);
    await row.waitFor({ state: 'visible' });
    const cell = row.locator('td').first();
    h.assert(await cell.getAttribute('class') === 'consult-status-5' && (await cell.innerText()).trim() === 'BC',
      'The list does not show the eReferral as Booking Confirmed (BC)');
    const form = await openFromList(s, list, oceanId, oceanReason, 'consult-status-ereferral');
    h.assert(await form.locator('#status5').count() === 1 && await form.locator('#status5').isChecked(),
      'The eReferral does not offer Booking Confirmed with its stored status checked');
    h.assert(await form.locator('input[name="update"], input[name="submitSaveOnly"], input[name="updateAndPrint"], #fax_button').count() === 0,
      'The eReferral, which Ocean manages, offers a save control');
    await form.close();
    h.assert(referrals.field(oceanId, 'status') === '5' && referrals.archived(oceanId).length === 0, 'Opening the eReferral changed it');
    console.log('  NOTE consultation-status-transitions: Booking Confirmed (status 5) belongs to Ocean eReferrals, which CARLOS renders '
      + 'without save controls; the status is set in Ocean and was not driven from CARLOS.');
  });

  let from = '1';
  for (const to of PATH) {
    await s.step(STEP.transition(from, to), async () => {
      const before = archivedStatuses();
      const form = await open(`consult-status-${from}-${to}`);
      h.assert(await form.locator(`#status${from}`).isChecked(), `The referral did not reopen on status ${from}`);
      await form.locator(`#status${to}`).check();
      await save(form, 'update', UPDATED);
      await form.close();
      h.assert(referrals.field(requestId, 'status') === to, `The update stored status ${referrals.field(requestId, 'status')}, not ${to}`);
      h.assert(JSON.stringify(archivedStatuses()) === JSON.stringify([...before, from]),
        `The update did not archive exactly the previous version (status ${from}): ${JSON.stringify(archivedStatuses())}`);
      h.assert(preserved() === expectedPreserved, `Changing the status changed the appointment, notes, attachment or patient: ${preserved()}`);
      const reopened = await open(`consult-status-reopen-${to}`);
      h.assert(await reopened.locator(`#status${to}`).isChecked(), `The reopened referral does not have status ${to} checked`);
      h.assert(await reopened.locator(`#attachedDocumentsTable input[name="docNo"][value="${documentNo}"]`).count() === 1,
        'The reopened referral no longer lists its attached document');
      await reopened.close();

      if (await includeCompleted.isChecked()) await includeCompleted.uncheck();
      const plain = await listed();
      if (to === '4') {
        h.assert(plain === null, 'A completed referral is still in the list without Include completed');
        await includeCompleted.check();
        const all = await listed();
        h.assert(all && all.cls === 'consult-status-4' && all.code === CODES[4],
          `With Include completed the referral is listed as ${JSON.stringify(all)}, not DONE`);
        await includeCompleted.uncheck();
      } else {
        h.assert(plain && plain.cls === `consult-status-${to}` && plain.code === CODES[to],
          `The list shows the referral as ${JSON.stringify(plain)}, not ${CODES[to]}`);
        h.assert(plain.patient.includes(marker), 'The listed row does not name the owned patient');
      }
      controlUnchanged();
    });
    from = to;
  }

  await s.step(STEP.repeat, async () => {
    const before = archivedStatuses();
    const form = await open('consult-status-repeat');
    await form.locator('#status1').check();
    await save(form, 'update', UPDATED);
    await form.close();
    h.assert(referrals.field(requestId, 'status') === '1' && JSON.stringify(archivedStatuses()) === JSON.stringify([...before, '1']),
      'Pressing Update again with the same status did not keep it as one more version');
    h.assert(referrals.requestsFor().length === 3, 'Pressing Update again created another referral');
    h.assert(preserved() === expectedPreserved, 'The repeated update changed the appointment, notes, attachment or patient');
    controlUnchanged();
  });

  await s.step(STEP.cancel, async () => {
    const before = archivedStatuses();
    const form = await open('consult-status-cancel');
    await form.locator('#status4').check();
    await form.close();
    h.assert(referrals.field(requestId, 'status') === '1' && JSON.stringify(archivedStatuses()) === JSON.stringify(before),
      'Closing the window over an unsaved status changed the referral');
    const shown = await listed();
    h.assert(shown && shown.code === CODES[1], 'The list does not show the referral as ND after the cancelled change');
    controlUnchanged();
  });
}

if (require.main === module) runWorkflow('consultation-status-transitions', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, STEP };
