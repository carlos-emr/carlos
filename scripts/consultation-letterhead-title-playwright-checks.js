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
 * Issue #5275: the Letterhead section's "Include Dr. with name" checkbox (ext_letterheadTitle).
 * User path: Schedule ▸ Consultations ▸ the owned referral (ViewRequest popup) ▸ Letterhead (expand) ▸ a provider
 * letterhead ▸ Include Dr. with name ▸ Update / Update And Print Preview.
 * Asserted:
 *   1. the checkbox is rendered (it is only offered where consultations can be faxed) and starts unchecked;
 *   2. checking it and pressing Update stores the extension (consultationRequestExt letterheadTitle=Dr) and the
 *      referral reopens with it checked;
 *   3. Update And Print Preview prints "Dr." once in front of the provider's name in the letterhead (the letter reads
 *      the provider through RxProviderData, which puts "Dr. " in front of every first name that lacks it, and the
 *      unchecked box takes it off again);
 *   4. an Update that changes only another field keeps the choice;
 *   5. with the clinic letterhead chosen the letter prints the clinic's name unchanged (the option is a provider's
 *      title), the stored choice is kept, and choosing the provider again applies it again;
 *   6. unchecking it and pressing Update removes the extension, reopens unchecked, and the letter prints the plain name;
 *   7. closing the window over an unsaved toggle changes nothing.
 * Skips when the checkbox is not rendered (consultation fax disabled or no _fax right).
 * Fixtures: lib/consultation-referral-fixture.js (FAKE patient, service, consultant and a referral seeded with an
 * existing active doctor, other than the signed-in one, as its letterhead). The service and consultant are in the
 * clinic-wide pickers while the run lasts, so the check must not run concurrently with other checks (live wrapper:
 * EXCLUSIVE=1). Cleanup removes every owned row and asserts it gone. No provider record is changed.
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { requirePoppler, pdfTextBuffer } = require('./lib/export-content-helpers');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { UPDATED, expand, openConsultationsList, openFromList, ownedReferrals, save } = require('./lib/consultation-referral-fixture');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;
const squash = text => String(text).replace(/\s+/g, ' ').trim();
/** The provider's first name without a leading title, as the unchecked letterhead prints it. */
const withoutTitle = first => String(first).replace(/^\s*dr(?:\.\s*|\s+)/i, '').trim();

const STEP = {
  rendered: 'the Include Dr. with name checkbox is rendered and starts unchecked',
  check: 'checking it stores the extension and the referral reopens checked',
  print: 'the printed letterhead puts Dr. once in front of the provider\'s name',
  unrelated: 'an update of another field keeps the choice',
  clinic: 'the clinic letterhead prints the clinic name unchanged and the provider letterhead applies the title again',
  uncheck: 'unchecking it removes the extension and the letter prints the plain name',
  cancel: 'closing the window over an unsaved toggle changes nothing',
};

async function workflow(s) {
  const { sql, marker, provider } = s;
  const letterheadNo = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no NOT LIKE '-%'
    AND provider_no<>${q(provider)} AND provider_type='doctor' AND first_name<>'' AND last_name<>''
    AND lastUpdateDate < NOW() - INTERVAL 10 MINUTE ORDER BY provider_no+0 LIMIT 1`);
  h.assert(/^\d+$/.test(letterheadNo || ''), 'No existing active doctor (other than the signed-in one) to use as the letterhead');
  const [firstName, surname] = sql.rows(`SELECT first_name, last_name FROM provider WHERE provider_no=${q(letterheadNo)}`)[0];
  const plainName = squash(`${withoutTitle(firstName)} ${surname}`);
  const titledName = `Dr. ${plainName}`;
  const clinicName = squash(sql.value('SELECT clinic_name FROM clinic LIMIT 1') || '');
  h.assert(clinicName, 'The clinic has no name to print on its letterhead');

  const referrals = ownedReferrals(s);
  const reason = `${marker} letterhead title referral`;
  const requestId = referrals.seed({ reason, letterheadName: letterheadNo, letterheadAddress: '1 Synthetic Letterhead Road',
    letterheadPhone: '555-0181', letterheadFax: '555-0182' });
  const title = () => referrals.ext(requestId).letterheadTitle;

  const list = await openConsultationsList(s);
  const open = async label => {
    const form = await openFromList(s, list, requestId, reason, label);
    await expand(form, 'collapseLetterhead');
    return { form, box: form.locator('#ext_letterheadTitle') };
  };
  /** Presses Update And Print Preview and returns the letter's text up to the referral reason (the letterhead). */
  async function printedLetterhead(form) {
    const download = form.waitForEvent('download', { timeout: 30000 });
    await clickAndAwaitReload(form, form.locator('input[name="updateAndPrint"]'));
    const text = squash(pdfTextBuffer(fs.readFileSync(await (await download).path())));
    const at = text.indexOf(reason);
    h.assert(at > 0, 'The PDF is not the owned referral');
    return text.slice(0, at);
  }

  await s.step(STEP.rendered, async () => {
    const { form, box } = await open('consult-title-render');
    if (await box.count() === 0) {
      throw new h.SkipCheck('The Letterhead section has no Include Dr. with name checkbox (consultation fax is disabled or the _fax right is missing)');
    }
    h.assert(await box.isVisible() && !(await box.isChecked()), 'The checkbox is hidden or starts checked on a referral without the extension');
    h.assert(await form.locator('#letterheadName').inputValue() === letterheadNo, 'The referral did not open on its provider letterhead');
    await form.close();
    h.assert(title() === undefined, 'The fixture referral already carries a letterhead title');
  });

  await s.step(STEP.check, async () => {
    const { form, box } = await open('consult-title-check');
    await box.check();
    await save(form, 'update', UPDATED);
    await form.close();
    h.assert(title() === 'Dr', `The checked box stored the letterhead title ${JSON.stringify(title())}, not Dr`);
    const reopened = await open('consult-title-reopen');
    h.assert(await reopened.box.isChecked(), 'The referral did not reopen with Include Dr. with name checked');
    await reopened.form.close();
  });

  await s.step(STEP.print, async () => {
    const { form } = await open('consult-title-print');
    const letterhead = await printedLetterhead(form);
    await form.close();
    h.assert(letterhead.includes(titledName), `The letterhead does not print ${JSON.stringify(titledName)}: ${JSON.stringify(letterhead.slice(0, 300))}`);
    h.assert(!/Dr\.?\s+Dr\b/i.test(letterhead), 'The letterhead prints the title twice');
    h.assert(title() === 'Dr', 'Update And Print Preview dropped the stored title');
  });

  await s.step(STEP.unrelated, async () => {
    const notes = `${marker} notes changed alone`;
    const { form } = await open('consult-title-unrelated');
    await form.locator('textarea[name="appointmentNotes"]').fill(notes);
    await save(form, 'update', UPDATED);
    await form.close();
    h.assert(referrals.field(requestId, 'statusText') === notes, 'The other field was not saved');
    h.assert(title() === 'Dr', 'Updating another field dropped the letterhead title');
    const reopened = await open('consult-title-reopen');
    h.assert(await reopened.box.isChecked(), 'The referral did not reopen checked after an unrelated update');
    await reopened.form.close();
  });

  await s.step(STEP.clinic, async () => {
    const { form, box } = await open('consult-title-clinic');
    const select = form.locator('#letterheadName');
    await select.selectOption({ index: 0 });
    h.assert(squash(await select.locator('option:checked').innerText()) === clinicName, 'The first letterhead is not the clinic');
    h.assert(await box.isChecked(), 'Choosing the clinic letterhead unchecked the title');
    const clinicLetterhead = await printedLetterhead(form);
    h.assert(clinicLetterhead.includes(clinicName) && !clinicLetterhead.includes(`Dr. ${clinicName}`),
      `The clinic letterhead does not print the clinic's name unchanged: ${JSON.stringify(clinicLetterhead.slice(0, 300))}`);
    h.assert(!clinicLetterhead.includes(plainName), 'The clinic letterhead still prints the provider\'s name');
    h.assert(title() === 'Dr' && referrals.field(requestId, 'letterheadName') === clinicName,
      'Saving the clinic letterhead did not keep the title choice or did not store the clinic');
    await form.close();
    const back = await open('consult-title-provider-again');
    await back.form.locator('#letterheadName').selectOption({ value: letterheadNo });
    const providerLetterhead = await printedLetterhead(back.form);
    await back.form.close();
    h.assert(providerLetterhead.includes(titledName), 'Choosing the provider letterhead again did not apply the title');
    h.assert(referrals.field(requestId, 'letterheadName') === letterheadNo, 'The provider letterhead was not stored again');
  });

  await s.step(STEP.uncheck, async () => {
    const { form, box } = await open('consult-title-uncheck');
    await box.uncheck();
    await save(form, 'update', UPDATED);
    await form.close();
    h.assert(title() === undefined, `Unchecking left the letterhead title ${JSON.stringify(title())} stored`);
    const reopened = await open('consult-title-reopen');
    h.assert(!(await reopened.box.isChecked()), 'The referral reopened checked after unchecking');
    const letterhead = await printedLetterhead(reopened.form);
    await reopened.form.close();
    h.assert(letterhead.includes(plainName) && !letterhead.includes(titledName),
      `The unchecked letterhead does not print the plain name: ${JSON.stringify(letterhead.slice(0, 300))}`);
  });

  await s.step(STEP.cancel, async () => {
    const archived = referrals.archived(requestId).length;
    const { form, box } = await open('consult-title-cancel');
    await box.check();
    await form.close();
    h.assert(title() === undefined && referrals.archived(requestId).length === archived,
      'Closing the window over an unsaved toggle changed the referral');
  });
}

if (require.main === module) {
  runWorkflow('consultation-letterhead-title', workflow, { openPatient: true, openMaster: false, preflight: () => requirePoppler('pdftotext') });
}
module.exports = { workflow, STEP };
