#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Issue #5278: the referral's Fax Account selector, and which sender the fax goes out from.
 * User paths: E-Chart ▸ Consultations "+" ▸ service and consultant pickers ▸ reason ▸ Letterhead (expand) ▸ Fax Account
 * ▸ Submit And Fax ▸ the fax cover page ▸ Send; Schedule ▸ Consultations ▸ a referral ▸ Fax Account ▸ Update And Fax ▸
 * the cover page ▸ Send / Cancel.
 * Asserted, with two owned active sender accounts (A, B) and one inactive one (C):
 *   1. the selector offers A and B by fax number and account name, and not C;
 *   2. Submit And Fax with the non-default sender chosen saves exactly one referral and opens the cover page with that
 *      sender selected (release/2026.08 compared the posted fax number with the account id, never matched, and fell
 *      back to the letterhead's fax or the first account);
 *   3. Send queues the referral for the consultant from exactly that sender: each job (faxes) carries the chosen
 *      account's fax line and user, the consultant's fax number, the owned patient, a FaxClientLog row naming the
 *      referral, and a PDF in the document store that is this referral (its reason);
 *   4. Update And Fax on the saved referral with the other sender chosen carries that sender to the cover page and the
 *      job, archives the referral once and creates no second referral;
 *   5. Cancel on the cover page queues nothing;
 *   6. a sender chosen on the form but deactivated before the cover page opens is not replaced by another one: the
 *      cover page offers no account selected, and Send is refused with an alert and queues nothing;
 *   7. two referral windows open at once, for two patients (the first from its patient's E-Chart, the second from the
 *      clinic list), keep their own senders on their own cover pages.
 * The accounts are MIDDLEWARE accounts whose relay URL is an unroutable loopback port with no inbound polling, so
 * nothing ever leaves the host: if the fax scheduler is running, a queued job fails at the provider boundary
 * (status ERROR) instead of being sent. The transport call itself is driven by consultation-fax-account-transport.
 * Skips when consultation fax is disabled or the login lacks _fax, and when any fax sender account other than the
 * owned ones is active (a fallback could then send from a real account).
 * Fixtures: lib/consultation-referral-fixture.js (FAKE patients, service, consultant with a fax number, a referral for
 * the second patient); three owned fax_config rows. The accounts are clinic-wide while the run lasts, so the check
 * must not run concurrently with other checks (live wrapper: EXCLUSIVE=1). Cleanup removes the owned fax jobs, their
 * FaxClientLog rows and PDF files, the accounts and every owned referral row, and asserts them gone.
 * Environment: CONSULT_FAX_DOCUMENT_STORE, the server's document directory the queued PDFs are written to (default
 * /var/lib/carlos-emr/CarlosDocument/carlos/document, the packaged path); readable by the check, which reads and removes
 * only the files its own jobs name.
 */
const h = require('./lib/playwright-harness');
const { requirePoppler, pdfTextBuffer } = require('./lib/export-content-helpers');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const {
  expand, newFromChart, openConsultationsList, openFromChart, openFromList, ownedReferrals, pickOwnedServiceAndConsultant, refreshList,
} = require('./lib/consultation-referral-fixture');
const { UNROUTABLE_RELAY, ownedJobs, ownedSenders } = require('./lib/consultation-fax-fixture');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;
const TIMEOUT = 30000;
const CONSULTANT_FAX = '905-555-0161';

const STEP = {
  offered: 'the Fax Account selector offers the active owned senders and not the inactive one',
  submitAndFax: 'Submit And Fax opens the cover page with the chosen sender, saving one referral',
  send: 'Send queues the referral to the consultant from exactly the chosen sender',
  updateAndFax: 'Update And Fax carries the other chosen sender to the cover page and the job',
  cancel: 'Cancel on the cover page queues nothing',
  unavailable: 'a chosen sender deactivated before the cover page is not replaced, and Send is refused',
  windows: 'two referral windows keep their own senders on their own cover pages',
};

async function workflow(s) {
  const { sql, marker, patient } = s;
  const others = sql.value(`SELECT COUNT(*) FROM fax_config WHERE active=1`);
  if (others !== '0') throw new h.SkipCheck('A fax sender account is active on this install; the check runs only where its own are the only ones');
  const referrals = ownedReferrals(s, { consultantFax: CONSULTANT_FAX });
  const second = referrals.extraPatient('FaxSecond');
  const senders = ownedSenders(s, { url: UNROUTABLE_RELAY, labels: [['A', 1], ['B', 1], ['C', 0]] });
  const jobs = ownedJobs(s, [patient, second.demographicNo]);
  const reason = `${marker} fax account referral`;
  const secondReason = `${marker} second patient fax referral`;
  const secondId = referrals.seed({ reason: secondReason, demographicNo: second.demographicNo });
  let requestId;

  /** The Fax Account selector, in the expanded Letterhead section. */
  async function senderSelect(form) {
    await expand(form, 'collapseLetterhead');
    return form.locator('select#faxAccount');
  }
  /** Presses Submit And Fax or Update And Fax and returns the cover page's selected sender. */
  async function coverPage(form) {
    await form.waitForFunction(() => !document.getElementById('fax_button').disabled, null, { timeout: 20000 });
    await clickAndAwaitReload(form, form.locator('#fax_button'), { label: 'the fax button' });
    await form.locator('#coverPageForm').waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert((await form.locator('#coverPageForm').getAttribute('action')).endsWith('/encounter/oscarConsultationRequest/ConsultationFormFax'),
      'The cover page does not post to the consultation fax action');
    return {
      selected: await form.locator('#senderFaxAccount').inputValue(),
      requestId: await form.locator('#coverPageForm input[name="requestId"]').inputValue(),
      demographicNo: await form.locator('#coverPageForm input[name="demographicNo"]').inputValue(),
    };
  }
  /** Send on the cover page; returns the jobs it queued. */
  async function send(form) {
    const before = jobs.rows().length;
    await clickAndAwaitReload(form, form.locator('#btnSend'), { label: 'Send' });
    await form.locator('#closeButton').waitFor({ state: 'attached', timeout: TIMEOUT });
    const queued = jobs.rows().slice(before);
    await form.close();
    return queued;
  }
  /** Each queued job: the chosen sender, the consultant, the patient, the referral and its PDF. */
  function assertQueued(queued, sender, expectedRequest, expectedPatient, why) {
    h.assert(queued.length === 1, `Send queued ${queued.length} jobs, not one to the consultant`);
    const [job] = queued;
    h.assert(job.faxline === sender.number && job.user === sender.faxUser,
      `The job goes out from ${job.faxline}/${job.user}, not the chosen sender ${sender.number}/${sender.faxUser}`);
    h.assert(job.destination === CONSULTANT_FAX.replace(/\D/g, ''), `The job is addressed to ${job.destination}, not the consultant`);
    h.assert(job.demographicNo === String(expectedPatient) && job.requestId === String(expectedRequest),
      'The job is not filed for the referral\'s patient and request');
    h.assert(['WAITING', 'ERROR'].includes(job.status), `The job is ${job.status}: nothing may be sent from an owned account`);
    const text = pdfTextBuffer(jobs.pdf(job)).replace(/\s+/g, ' ');
    h.assert(text.includes(why), 'The queued PDF is not this referral');
  }

  let form;
  await s.step(STEP.offered, async () => {
    form = await newFromChart(s, 'fax-new');
    if (await form.locator('#fax_button').count() !== 1 || await form.locator('select#faxAccount').count() !== 1) {
      throw new h.SkipCheck('This install does not offer consultation fax (consultation_fax_enabled or the _fax right)');
    }
    const options = await (await senderSelect(form)).locator('option')
      .evaluateAll(nodes => nodes.map(o => ({ value: o.value, text: o.textContent.trim() })));
    for (const label of ['A', 'B']) {
      h.assert(options.some(o => o.value === senders[label].number && o.text === senders[label].name),
        `The Fax Account selector does not offer sender ${label} by its number and name`);
    }
    h.assert(!options.some(o => o.value === senders.C.number), 'The Fax Account selector offers an inactive sender');
  });

  let chosen;
  await s.step(STEP.submitAndFax, async () => {
    await pickOwnedServiceAndConsultant(form, referrals);
    await form.locator('textarea[name="reasonForConsultation"]').fill(reason);
    const select = await senderSelect(form);
    // The sender the form would use unchosen is the letterhead's (or the first); choose the other one.
    chosen = await select.inputValue() === senders.B.number ? senders.A : senders.B;
    await select.selectOption(chosen.number);
    const cover = await coverPage(form);
    const created = referrals.requestsFor();
    h.assert(created.length === 1, `Submit And Fax stored ${created.length} referrals, not one`);
    [requestId] = created;
    h.assert(cover.requestId === requestId && cover.demographicNo === String(patient), 'The cover page is for another referral or patient');
    h.assert(cover.selected === chosen.number,
      `The cover page selected the sender ${JSON.stringify(cover.selected)}, not the chosen ${chosen.number}`);
    h.assert(jobs.rows().length === 0, 'Submit And Fax queued a job before Send');
  });

  await s.step(STEP.send, async () => {
    const queued = await send(form);
    assertQueued(queued, chosen, requestId, patient, reason);
  });

  const list = await openConsultationsList(s);
  await refreshList(list);
  const other = () => (chosen === senders.A ? senders.B : senders.A);

  await s.step(STEP.updateAndFax, async () => {
    const archived = referrals.archived(requestId).length;
    const edit = await openFromList(s, list, requestId, reason, 'fax-update');
    await (await senderSelect(edit)).selectOption(other().number);
    const cover = await coverPage(edit);
    h.assert(cover.selected === other().number, `Update And Fax opened the cover page on ${JSON.stringify(cover.selected)}, not ${other().number}`);
    h.assert(referrals.requestsFor().length === 1 && referrals.archived(requestId).length === archived + 1,
      'Update And Fax did not update the one referral exactly once');
    assertQueued(await send(edit), other(), requestId, patient, reason);
  });

  await s.step(STEP.cancel, async () => {
    const before = jobs.rows().length;
    const edit = await openFromList(s, list, requestId, reason, 'fax-cancel');
    await coverPage(edit);
    await clickAndAwaitReload(edit, edit.locator('#btnCancel'), { label: 'Cancel' });
    await edit.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: TIMEOUT });
    await edit.close();
    h.assert(jobs.rows().length === before, 'Cancel on the cover page queued a fax');
  });

  await s.step(STEP.unavailable, async () => {
    const before = jobs.rows().length;
    const edit = await openFromList(s, list, requestId, reason, 'fax-unavailable');
    await (await senderSelect(edit)).selectOption(senders.B.number);
    sql.execute(`UPDATE fax_config SET active=0 WHERE id=${senders.B.id} AND accountName=${q(senders.B.name)}`);
    try {
      const cover = await coverPage(edit);
      h.assert(cover.selected !== senders.A.number,
        'The cover page replaced the deactivated sender with another account');
      h.assert(cover.selected === '', `The cover page selected ${JSON.stringify(cover.selected)} for a sender that is no longer active`);
      const refused = await h.withExpectedDialogs(edit, () => clickAndAwaitReload(edit, edit.locator('#btnSend'), { label: 'Send' }));
      h.assert(refused.length === 1 && /could not be sent/i.test(refused[0].text) && /not configured/i.test(refused[0].text),
        `Send with no available sender raised ${JSON.stringify(refused)}, not the refusal`);
      h.assert(jobs.rows().length === before, 'Send queued a fax from another sender');
      await edit.close();
    } finally {
      sql.execute(`UPDATE fax_config SET active=1 WHERE id=${senders.B.id} AND accountName=${q(senders.B.name)}`);
    }
  });

  await s.step(STEP.windows, async () => {
    const before = jobs.rows().length;
    // The first from the patient's chart, the second from the clinic list (one shared window): two windows at once.
    const first = await openFromChart(s, requestId, reason, 'fax-window-first');
    const secondWindow = await openFromList(s, list, secondId, secondReason, 'fax-window-second');
    await (await senderSelect(first)).selectOption(senders.A.number);
    await (await senderSelect(secondWindow)).selectOption(senders.B.number);
    const secondCover = await coverPage(secondWindow);
    const firstCover = await coverPage(first);
    h.assert(secondCover.selected === senders.B.number && secondCover.requestId === secondId
      && secondCover.demographicNo === second.demographicNo, 'The second window\'s cover page lost its sender or patient');
    h.assert(firstCover.selected === senders.A.number && firstCover.requestId === requestId
      && firstCover.demographicNo === String(patient), 'The first window\'s cover page took the second window\'s sender or patient');
    await first.close();
    await secondWindow.close();
    h.assert(jobs.rows().length === before, 'Opening the cover pages queued a fax');
    h.assert(referrals.field(secondId, 'demographicNo') === second.demographicNo, 'The second referral changed patient');
  });
}

if (require.main === module) {
  runWorkflow('consultation-fax-account', workflow, { openPatient: true, preflight: () => requirePoppler('pdftotext') });
}
module.exports = { workflow, STEP };
