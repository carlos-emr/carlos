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
 * Issue #5272: a referral's Last Follow-up date, through its own date control.
 * User path: Schedule ▸ Consultations ▸ the owned referral (ViewRequest popup) ▸ Last Follow Up Date ▸ Update ▸
 * reopen from the list.
 * Asserted:
 *   1. a date typed into the control is stored exactly (consultationRequests.followUpDate), the update archives the
 *      previous version (consultationRequestsArchive, followUpDate NULL), and the reopened form shows it;
 *   2. changing it updates the same request, archives the previous date, and leaves the control referral alone;
 *   3. clearing the control stores NULL and the reopened form shows it empty (release/2026.08 kept the old date:
 *      the update branch only ever set a non-blank date);
 *   4. an impossible date (30 February) is refused: the browser's date control cannot read it, and Update and Print
 *      raise an alert naming the field instead of posting it as "no date"; posted past the control (the field's type switched
 *      to text, as a non-browser client would send it) the server answers 400 before archiving or saving, and the
 *      stored date is unchanged (release/2026.08 parsed it leniently and stored 2 March);
 *   5. closing the window over an unsaved edit changes nothing.
 * The control referral (a second request of the same patient, with its own follow-up date) is unchanged throughout.
 * Fixtures: lib/consultation-referral-fixture.js (FAKE patient, service, consultant, two referrals seeded by SQL). The
 * service and consultant are in the clinic-wide pickers while the run lasts, so the check must not run concurrently
 * with other checks (live wrapper: EXCLUSIVE=1). Cleanup removes every owned row and asserts it gone.
 */
const h = require('./lib/playwright-harness');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { UPDATED, openConsultationsList, openFromList, ownedReferrals, save } = require('./lib/consultation-referral-fixture');
const { runWorkflow } = require('./lib/workflow-session');

const STEP = {
  enter: 'a typed Last Follow-up date is stored, archived over the previous version and reopened',
  change: 'changing the date updates the same request and archives the previous date',
  clear: 'clearing Last Follow-up stores no date and reopens empty',
  impossible: 'an impossible date is refused by the form and by the server, and the stored date is kept',
  cancel: 'closing the window over an unsaved edit changes nothing',
};

/** ISO date a number of days from the server's today (MariaDB CURDATE). */
const serverDate = (sql, days) => sql.value(`SELECT DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL ${Number(days)} DAY),'%Y-%m-%d')`);

async function workflow(s) {
  const { sql, marker } = s;
  const referrals = ownedReferrals(s);
  const reason = `${marker} follow-up referral`;
  const controlReason = `${marker} control referral`;
  const controlDate = serverDate(sql, 3);
  const requestId = referrals.seed({ reason });
  const controlId = referrals.seed({ reason: controlReason, followUpDate: controlDate, status: '2' });
  const followUp = () => referrals.field(requestId, 'followUpDate');
  const archivedFollowUps = () => referrals.archived(requestId, ['followUpDate']).map(([date]) => date);
  const controlUnchanged = () => {
    h.assert(referrals.field(controlId, 'followUpDate') === controlDate && referrals.field(controlId, 'status') === '2'
      && referrals.field(controlId, 'reason') === controlReason && referrals.archived(controlId).length === 0,
    'The control referral changed');
  };
  const first = serverDate(sql, 14);
  const second = serverDate(sql, 28);

  const list = await openConsultationsList(s);
  const open = label => openFromList(s, list, requestId, reason, label);

  /** Opens the referral, types a date into Last Follow-up (empty clears it) and presses Update. */
  async function updateFollowUp(value, label) {
    const form = await open(label);
    await form.locator('#followUpDate').fill(value);
    await save(form, 'update', UPDATED);
    await form.close();
  }
  /** The date the reopened referral shows. */
  async function reopenedValue(label) {
    const form = await open(label);
    const value = await form.locator('#followUpDate').inputValue();
    await form.close();
    return value;
  }

  await s.step(STEP.enter, async () => {
    h.assert(followUp() === '', 'The fixture referral already has a follow-up date');
    await updateFollowUp(first, 'consult-followup-enter');
    h.assert(followUp() === first, `Last Follow-up was stored as ${JSON.stringify(followUp())}, not ${first}`);
    h.assert(JSON.stringify(archivedFollowUps()) === JSON.stringify(['']),
      `The update did not archive exactly the previous version (no date): ${JSON.stringify(archivedFollowUps())}`);
    h.assert(referrals.field(requestId, 'reason') === reason && referrals.field(requestId, 'status') === '1',
      'Saving the follow-up date changed the referral\'s reason or status');
    h.assert(await reopenedValue('consult-followup-reopen') === first, 'The reopened referral does not show the saved date');
    controlUnchanged();
  });

  await s.step(STEP.change, async () => {
    await updateFollowUp(second, 'consult-followup-change');
    h.assert(followUp() === second, `The changed date was stored as ${JSON.stringify(followUp())}, not ${second}`);
    h.assert(JSON.stringify(archivedFollowUps()) === JSON.stringify(['', first]),
      `The change did not archive the previous date: ${JSON.stringify(archivedFollowUps())}`);
    h.assert(referrals.requestsFor().length === 2, 'Changing the date created another request');
    h.assert(await reopenedValue('consult-followup-reopen') === second, 'The reopened referral does not show the changed date');
    controlUnchanged();
  });

  await s.step(STEP.clear, async () => {
    await updateFollowUp('', 'consult-followup-clear');
    h.assert(referrals.isNull(requestId, 'followUpDate'),
      `Clearing Last Follow-up left ${JSON.stringify(followUp())} stored (the update kept the old date)`);
    h.assert(JSON.stringify(archivedFollowUps()) === JSON.stringify(['', first, second]),
      `Clearing did not archive the previous date: ${JSON.stringify(archivedFollowUps())}`);
    h.assert(await reopenedValue('consult-followup-reopen') === '', 'The reopened referral still shows a follow-up date');
    controlUnchanged();
  });

  await s.step(STEP.impossible, async () => {
    await updateFollowUp(first, 'consult-followup-restore');
    h.assert(followUp() === first, 'The date the impossible input must not lose was not saved');
    const archivedBefore = archivedFollowUps().length;
    const form = await open('consult-followup-impossible');
    const control = form.locator('#followUpDate');
    // The way a clinician types one: month, day, year into the control's segments.
    await control.fill('');
    await control.click();
    await control.pressSequentially('02302026');
    h.assert(await control.evaluate(input => input.validity.badInput && input.value === ''),
      'The date control accepted 30 February as a date');
    const refused = await h.withExpectedDialogs(form, () => form.locator('input[name="update"]').click());
    h.assert(refused.length === 1 && refused[0].type === 'alert' && /not a real calendar date/i.test(refused[0].text)
      && refused[0].text.includes('Follow'),
    `Update with an unreadable follow-up date raised ${JSON.stringify(refused)}, not an alert naming the field`);
    h.assert(/ViewRequest|ViewConsultationFormRequest/.test(form.url()) && await form.locator('#followUpDate').count() === 1,
      'Update left the form although the follow-up date was refused');
    h.assert(followUp() === first && archivedFollowUps().length === archivedBefore,
      'The refused update changed the stored follow-up date or archived a version');
    // Print (an unsaved preview) would otherwise print the referral without the date the clinician typed.
    const previewRefused = await h.withExpectedDialogs(form, () => form.locator('input[name="printPreview"]').click());
    h.assert(previewRefused.length === 1 && /not a real calendar date/i.test(previewRefused[0].text),
      `Print with an unreadable follow-up date raised ${JSON.stringify(previewRefused)}, not the refusal`);

    // Past the control: what a client that is not this form posts. The server must refuse it, not store 2 March.
    await control.evaluate(input => { input.type = 'text'; input.value = '2026-02-30'; });
    const mark = failureMark(s.recorder);
    const answered = form.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/encounter/RequestConsultation'), { timeout: 30000 });
    await clickAndAwaitReload(form, form.locator('input[name="update"]'), { label: 'Update with 2026-02-30' });
    const response = await answered;
    h.assert(followUp() === first,
      `The server stored the impossible date 2026-02-30 as ${JSON.stringify(followUp())}`);
    h.assert(response.status() === 400, `The server answered ${response.status()} to an impossible follow-up date, not 400`);
    consumeExpectedFailure(s.recorder, mark, { status: 400, path: /\/encounter\/RequestConsultation$/ });
    h.assert(archivedFollowUps().length === archivedBefore, 'The refused update still archived a version of the referral');
    await form.close();
    h.assert(await reopenedValue('consult-followup-reopen') === first, 'After the refusals the referral does not reopen with its date');
    controlUnchanged();
  });

  await s.step(STEP.cancel, async () => {
    const archivedBefore = archivedFollowUps().length;
    const form = await open('consult-followup-cancel');
    await form.locator('#followUpDate').fill(second);
    await form.close();
    h.assert(followUp() === first && archivedFollowUps().length === archivedBefore,
      'Closing the window over an unsaved follow-up date changed the referral');
    h.assert(await reopenedValue('consult-followup-reopen') === first, 'The referral does not reopen with its saved date');
    controlUnchanged();
  });
}

if (require.main === module) runWorkflow('consultation-followup-date', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, STEP };
