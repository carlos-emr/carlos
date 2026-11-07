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
 * Issue #5277: one referral from request to reviewed completion, through the clinician's own controls.
 * User path: E-Chart ▸ Consultations "+" ▸ service and consultant pickers ▸ reason, urgency ▸ Submit; Schedule ▸
 * Consultations ▸ the referral ▸ appointment date, time and notes, Last Follow-up ▸ Update; the specialist's reply,
 * received into the patient's chart as a document ▸ the referral's Manage Attachments ▸ that document ▸ Save and
 * Close ▸ Update; E-Chart ▸ the Consultations module's entry for the referral ▸ review ▸ Completed ▸ Update; the
 * Consultations list with and without Include completed.
 * Asserted:
 *   1. Submit stores one referral for the owned patient with the chosen service, consultant, reason and urgency;
 *   2. the appointment and follow-up are stored on the same referral, listed and reopened;
 *   3. the reply document is offered in the referral's attachment window (and the second patient's document is not),
 *      and Update attaches it to this referral only (consultdocs), the patient's own document;
 *   4. the referral opened from the patient's E-Chart shows the reply attached;
 *   5. a completion that fails part-way (an unreadable Last Follow-up date, refused before saving) and a completion
 *      abandoned by closing the window leave the referral open;
 *   6. Completed is stored, archived over the open version, leaves the default list and shows with Include completed;
 * and the second patient's referral and document are unchanged throughout.
 * RESPONSE BLOCKER (reported, not worked around): this release has no clinician page that authors or reviews a
 * consultation response. consultationResponse rows are reached only through the REST endpoints /ws/rs/consults/
 * searchResponses, getResponse, getResponseAttachments and saveResponse (consultation-response-attachments covers
 * those adversarially); the "Consultation Responses" menu state they fed belonged to the retired AngularJS front end.
 * The supported user-facing path for a specialist's reply is therefore the one driven here: the reply arrives in the
 * chart as a document and is attached to the referral it answers. The check prints a NOTE saying so.
 * Fixtures: lib/consultation-referral-fixture.js (FAKE patients, service, consultant, a seeded referral for the second
 * patient, and a FAKE reply document in each patient's chart). The service and consultant are in the clinic-wide
 * pickers while the run lasts, so the check must not run concurrently with other checks (live wrapper: EXCLUSIVE=1).
 * Cleanup removes every owned row and asserts it gone.
 */
const h = require('./lib/playwright-harness');
const {
  CREATED, UPDATED, listRow, newFromChart, openConsultationsList, openFromChart, openFromList, ownedReferrals, pickOwnedServiceAndConsultant,
  refreshList, save,
} = require('./lib/consultation-referral-fixture');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const serverDate = (sql, days) => sql.value(`SELECT DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL ${Number(days)} DAY),'%Y-%m-%d')`);

const STEP = {
  create: 'Submit creates one referral for the patient with the chosen service, consultant, reason and urgency',
  track: 'the appointment and follow-up are stored on the referral, listed and reopened',
  reply: 'the specialist\'s reply document is attached to this referral through its attachment window',
  review: 'the referral opened from the patient\'s chart shows the reply attached',
  notCompleted: 'a refused or abandoned completion leaves the referral open',
  complete: 'Completed is stored, archived, and moves the referral to the completed list',
};

async function workflow(s) {
  const { sql, marker, patient } = s;
  const referrals = ownedReferrals(s);
  const second = referrals.extraPatient('JourneySecond');
  const reason = `${marker} journey referral`;
  const secondReason = `${marker} second patient referral`;
  const secondId = referrals.seed({ reason: secondReason, demographicNo: second.demographicNo, status: '2' });
  const reply = referrals.document(`${marker}-specialist-reply`);
  const secondReply = referrals.document(`${marker}-second-reply`, second.demographicNo);
  referrals.attach(secondId, secondReply);
  const secondUnchanged = () => h.assert(referrals.field(secondId, 'status') === '2' && referrals.field(secondId, 'reason') === secondReason
    && referrals.archived(secondId).length === 0 && JSON.stringify(referrals.attachments(secondId)) === JSON.stringify([`D:${secondReply}`]),
  'The second patient\'s referral changed');
  const apptDate = serverDate(sql, 10);
  const followUp = serverDate(sql, 40);
  const notes = `${marker} booked with the specialist`;
  let requestId;

  await s.step(STEP.create, async () => {
    const form = await newFromChart(s, 'journey-new');
    await pickOwnedServiceAndConsultant(form, referrals);
    await form.locator('textarea[name="reasonForConsultation"]').fill(reason);
    await form.locator('#urgency').selectOption('1');
    await save(form, 'submitSaveOnly', CREATED);
    await form.close();
    const created = referrals.requestsFor();
    h.assert(created.length === 1, `Submit stored ${created.length} referrals for the patient, not one`);
    [requestId] = created;
    h.assert(referrals.field(requestId, 'serviceId') === referrals.serviceId && referrals.field(requestId, 'specId') === referrals.specId
      && referrals.field(requestId, 'reason') === reason && referrals.field(requestId, 'urgency') === '1'
      && referrals.field(requestId, 'status') === '1' && referrals.field(requestId, 'demographicNo') === String(patient),
    'The new referral does not carry the chosen service, consultant, reason, urgency and patient');
    secondUnchanged();
  });

  const list = await openConsultationsList(s);
  const open = label => openFromList(s, list, requestId, reason, label);

  await s.step(STEP.track, async () => {
    await refreshList(list);
    const form = await open('journey-track');
    await form.locator('#appointmentDate').fill(apptDate);
    await form.locator('#appointmentTimeDisplay').fill('14:30');
    await form.locator('textarea[name="appointmentNotes"]').fill(notes);
    await form.locator('#followUpDate').fill(followUp);
    await save(form, 'update', UPDATED);
    await form.close();
    h.assert(referrals.field(requestId, 'appointmentDate') === apptDate && referrals.field(requestId, 'appointmentTime') === '14:30:00'
      && referrals.field(requestId, 'statusText') === notes && referrals.field(requestId, 'followUpDate') === followUp,
    'The appointment, notes and follow-up were not stored on the referral');
    h.assert(referrals.requestsFor().length === 1, 'Tracking the appointment created another referral');
    await refreshList(list);
    h.assert((await listRow(list, requestId).innerText()).includes(apptDate), 'The list does not show the booked appointment date');
    const reopened = await open('journey-track-reopen');
    h.assert(await reopened.locator('#appointmentDate').inputValue() === apptDate
      && await reopened.locator('#appointmentTimeDisplay').inputValue() === '14:30'
      && await reopened.locator('#followUpDate').inputValue() === followUp, 'The reopened referral does not show the appointment and follow-up');
    await reopened.close();
    secondUnchanged();
  });

  await s.step(STEP.reply, async () => {
    const form = await open('journey-reply');
    await form.locator('#attachDocumentPanelBtn').click();
    await form.locator('#attachDocumentsForm').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await form.locator(`#attachDocumentsForm #docNo${secondReply}`).count() === 0,
      'The attachment window offers the second patient\'s document');
    await form.locator(`#attachDocumentsForm #docNo${reply}`).check();
    await form.locator('.ui-dialog .save-and-close-button').click();
    await form.locator('#attachDocumentsForm').waitFor({ state: 'hidden', timeout: TIMEOUT });
    h.assert(await form.locator(`#attachedDocumentsTable input[name="docNo"][value="${reply}"]`).count() === 1,
      'Save and Close did not list the reply among the referral\'s documents');
    await save(form, 'update', UPDATED);
    await form.close();
    h.assert(JSON.stringify(referrals.attachments(requestId)) === JSON.stringify([`D:${reply}`]),
      `The referral's attachments are ${JSON.stringify(referrals.attachments(requestId))}, not the reply`);
    h.assert(sql.value(`SELECT module_id FROM ctl_document WHERE document_no=${reply} AND module='demographic'`) === String(patient),
      'The attached reply is not the owned patient\'s document');
    h.assert(referrals.field(requestId, 'appointmentDate') === apptDate && referrals.field(requestId, 'statusText') === notes,
      'Attaching the reply changed the tracked appointment');
    console.log('  NOTE consultation-referral-journey: no clinician page authors or reviews a consultation response in this release; '
      + 'consultationResponse is reachable only through /ws/rs/consults/*Response (REST), so the reply is received as a chart '
      + 'document attached to the referral.');
    secondUnchanged();
  });

  const fromChart = label => openFromChart(s, requestId, reason, label);

  await s.step(STEP.review, async () => {
    const form = await fromChart('journey-review');
    const attached = form.locator('#attachedDocumentsTable tr', { has: form.locator(`input[name="docNo"][value="${reply}"]`) });
    h.assert(await attached.count() === 1 && (await attached.innerText()).includes(`${marker}-specialist-reply`),
      'The referral opened from the chart does not show the reply attached');
    h.assert(await form.locator('#status1').isChecked(), 'The referral is not open before it is completed');
    await form.close();
  });

  await s.step(STEP.notCompleted, async () => {
    const archived = referrals.archived(requestId).length;
    const form = await fromChart('journey-refused');
    await form.locator('#status4').check();
    const control = form.locator('#followUpDate');
    await control.fill('');
    await control.click();
    await control.pressSequentially('02302026');
    const refused = await h.withExpectedDialogs(form, () => form.locator('input[name="update"]').click());
    h.assert(refused.length === 1 && /not a real calendar date/i.test(refused[0].text),
      `A completion with an unreadable follow-up date raised ${JSON.stringify(refused)}, not a refusal`);
    await form.close();
    h.assert(referrals.field(requestId, 'status') === '1' && referrals.field(requestId, 'followUpDate') === followUp
      && referrals.archived(requestId).length === archived, 'The refused completion changed the referral');
    const abandoned = await fromChart('journey-abandoned');
    await abandoned.locator('#status4').check();
    await abandoned.close();
    h.assert(referrals.field(requestId, 'status') === '1' && referrals.archived(requestId).length === archived,
      'Closing the window over an unsaved completion completed the referral');
    secondUnchanged();
  });

  await s.step(STEP.complete, async () => {
    const archived = referrals.archived(requestId, ['status']);
    const form = await fromChart('journey-complete');
    await form.locator('#status4').check();
    await save(form, 'update', UPDATED);
    await form.close();
    h.assert(referrals.field(requestId, 'status') === '4', 'Completed was not stored');
    h.assert(JSON.stringify(referrals.archived(requestId, ['status'])) === JSON.stringify([...archived, ['1']]),
      'Completing did not archive the open version');
    h.assert(JSON.stringify(referrals.attachments(requestId)) === JSON.stringify([`D:${reply}`])
      && referrals.field(requestId, 'appointmentDate') === apptDate && referrals.field(requestId, 'followUpDate') === followUp,
    'Completing changed the attachments, appointment or follow-up');
    const include = list.locator('#includeCompleted');
    if (await include.isChecked()) await include.uncheck();
    await refreshList(list);
    h.assert(await listRow(list, requestId).count() === 0, 'The completed referral is still in the open list');
    await include.check();
    await refreshList(list);
    const cell = listRow(list, requestId).locator('td').first();
    h.assert(await cell.getAttribute('class') === 'consult-status-4' && (await cell.innerText()).trim() === 'DONE',
      'With Include completed the referral is not listed as DONE');
    await include.uncheck();
    secondUnchanged();
  });
}

if (require.main === module) runWorkflow('consultation-referral-journey', workflow, { openPatient: true });
module.exports = { workflow, STEP };
