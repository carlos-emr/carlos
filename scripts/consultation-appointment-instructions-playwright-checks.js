#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Issue #5273: choosing an Appointment Instruction on a referral and saving it.
 * User paths: E-Chart ▸ Consultations "+" (a new request) ▸ the owned service and consultant through their pickers ▸
 * reason ▸ Appointment Instructions ▸ Submit Consultation Request; then Schedule ▸ Consultations ▸ that referral ▸
 * Appointment Instructions ▸ Update / Update And Print Preview.
 * Asserted:
 *   1. the form offers the run's two active instructions by value and label, and not its inactive one;
 *   2. Submit stores the chosen instruction's value (consultationRequests.appointmentInstructions, which the
 *      consultApptInst lookup list resolves to the label) and the reopened referral has it selected;
 *   3. changing it on Update stores the other value, archives the previous one and leaves the reason and clinical
 *      information exactly as they were;
 *   4. Update And Print Preview prints the chosen instruction's label as the letter's reply heading;
 *   5. the blank option clears the choice: Update stores an empty value and the referral reopens on the blank option;
 *   6. closing the window over an unsaved selection changes nothing.
 * The control referral (another request of the same patient, with the first instruction) is unchanged throughout.
 * Skips when the form shows no Appointment Instructions list (CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP off) or the
 * install has no active consultApptInst lookup list.
 * Fixtures: lib/consultation-referral-fixture.js, plus three FAKE items on the clinic-wide consultApptInst lookup list,
 * added through Administration ▸ Customize Consult Appointment Instructions (the form reads the list through a
 * 30-minute cache that only the lookup list manager's own writes evict), one of them removed again through Manage
 * Lookup Lists to make it inactive. Every login's consultation form offers them while the run lasts, so the check must
 * not run concurrently with other checks (live wrapper: EXCLUSIVE=1). At the end, or on a failure, the active ones are
 * removed through Manage Lookup Lists; cleanup then deletes the owned rows and asserts the list's other items are
 * exactly as they were before the run.
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { requirePoppler, pdfTextBuffer } = require('./lib/export-content-helpers');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { settleOperations } = require('./graceful-signal-cancellation');
const {
  CREATED, UPDATED, newFromChart, openConsultationsList, openFromList, ownedReferrals, pickOwnedServiceAndConsultant, save,
} = require('./lib/consultation-referral-fixture');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;
const LIST_NAME = 'consultApptInst';
const TIMEOUT = 30000;
const squash = text => String(text).replace(/\s+/g, ' ').trim();

const STEP = {
  offered: 'the form offers the active owned instructions by value and label, and not the inactive one',
  submit: 'Submit stores the chosen instruction and the reopened referral has it selected',
  change: 'Update stores the other instruction, archives the previous one and keeps the clinical text',
  print: 'Update And Print Preview prints the chosen instruction as the reply heading',
  clear: 'the blank option clears the instruction and the referral reopens on it',
  cancel: 'closing the window over an unsaved selection changes nothing',
};

async function workflow(s) {
  const { sql, marker } = s;
  const listId = sql.value(`SELECT id FROM LookupList WHERE name=${q(LIST_NAME)} AND active=1 ORDER BY id LIMIT 1`);
  if (!/^[1-9]\d*$/.test(listId || '')) throw new h.SkipCheck('This install has no active consultApptInst lookup list');
  const items = {
    first: { label: `${marker} Call the patient & book` },
    second: { label: `${marker} Patient to call the office` },
    inactive: { label: `${marker} Retired instruction` },
  };
  const owned = `lookupListId=${listId} AND label LIKE ${q(`${marker}%`)}`;
  const others = () => JSON.stringify(sql.rows(`SELECT id,value,IFNULL(label,''),displayOrder,active FROM LookupListItem
    WHERE lookupListId=${listId} AND NOT (${owned}) ORDER BY id`));
  const othersBefore = others();
  s.cleanup(() => {
    sql.execute(`DELETE FROM LookupListItem WHERE ${owned}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE ${owned}`) === '0', 'Owned lookup list items were not removed');
    h.assert(others() === othersBefore, 'The consultApptInst lookup list\'s other items changed');
  });

  // The consultation form reads the list through LookupListDao's 30-minute LOOKUP_LISTS cache, which only the lookup
  // list manager's own writes evict: the items are added and removed through Administration, as a clinic does it.
  let admin;
  async function adminFrame(name, ready) {
    if (!admin || admin.isClosed()) {
      ({ page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
        { context: s.context, recorder: s.recorder, label: 'instructions-administration', timeout: TIMEOUT }));
    }
    const link = admin.getByRole('link', { name, exact: true, includeHidden: true }).first();
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor({ timeout: TIMEOUT });
    const frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, `${name} did not load in the administration frame`);
    await frame.locator(ready).waitFor({ timeout: TIMEOUT });
    return frame;
  }
  async function managerPost(frame, action) {
    const response = admin.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/lookupListManagerAction') && r.request().frame() === frame, { timeout: TIMEOUT });
    const [, answered] = await settleOperations([Promise.resolve().then(action), response]);
    h.assert(answered.status() === 200, `The lookup list manager answered HTTP ${answered.status()}`);
    await frame.waitForFunction(() => window.jQuery && window.jQuery.active === 0, null, { timeout: TIMEOUT });
  }
  /** Customize Consult Appointment Instructions ▸ add; returns the generated value. */
  async function addInstruction(item) {
    const frame = await adminFrame('Customize Consult Appointment Instructions', `#lookupListItemLabel_${listId}`);
    await frame.locator(`#lookupListItemLabel_${listId}`).fill(item.label);
    await managerPost(frame, () => frame.locator(`#addLookupListItemButton_${listId}`).click());
    await expectValue(sql, `SELECT COUNT(*) FROM LookupListItem WHERE lookupListId=${listId} AND BINARY label=BINARY ${q(item.label)} AND active=1`,
      '1', `The instruction ${JSON.stringify(item.label)} was not stored once`);
    const [id, value] = sql.rows(`SELECT id, value FROM LookupListItem WHERE lookupListId=${listId} AND BINARY label=BINARY ${q(item.label)}`)[0];
    Object.assign(item, { id, value });
  }
  /** Manage Lookup Lists ▸ X: deactivates the item (the manager keeps the row). */
  async function removeInstruction(item) {
    const frame = await adminFrame('Manage Lookup Lists', `#lookupListItems_${listId}`);
    await managerPost(frame, () => frame.locator(`#removeLookupListItem_${item.id}_${listId}`).click());
    await expectValue(sql, `SELECT active FROM LookupListItem WHERE id=${item.id}`, '0', `The instruction ${JSON.stringify(item.label)} was not deactivated`);
  }
  async function removeActiveOwned() {
    // A fresh Administration window: the one used to add the items may have moved on since.
    if (admin && admin !== s.schedule && !admin.isClosed()) await admin.close();
    admin = undefined;
    for (const [id] of sql.rows(`SELECT id FROM LookupListItem WHERE ${owned} AND active=1`)) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned lookup list item id is invalid');
      await removeInstruction({ id, label: id });
    }
  }
  try {
    await workflowWithInstructions(s, { items, addInstruction, removeInstruction });
    await removeActiveOwned();
  } catch (error) {
    // Leave no active owned item in the cached list for the next check (best effort; the original failure is reported).
    await removeActiveOwned().catch(removal => console.log(`  Best-effort removal through Manage Lookup Lists failed: ${removal.message}`));
    throw error;
  } finally {
    if (admin && admin !== s.schedule && !admin.isClosed()) await admin.close();
  }
}

async function workflowWithInstructions(s, { items, addInstruction, removeInstruction }) {
  const { sql, marker } = s;
  for (const item of Object.values(items)) await addInstruction(item);
  await removeInstruction(items.inactive);

  const referrals = ownedReferrals(s);
  const reason = `${marker} instruction referral`;
  const clinicalInfo = `${marker} clinical information that must not change`;
  const controlReason = `${marker} control referral`;
  const controlId = referrals.seed({ reason: controlReason, appointmentInstructions: items.first.value });
  const controlUnchanged = () => h.assert(referrals.field(controlId, 'appointmentInstructions') === items.first.value
    && referrals.field(controlId, 'reason') === controlReason && referrals.archived(controlId).length === 0,
  'The control referral changed');
  let requestId;
  const instruction = () => referrals.field(requestId, 'appointmentInstructions');

  const list = await openConsultationsList(s);
  const open = async label => {
    const form = await openFromList(s, list, requestId, reason, label);
    return { form, select: form.locator('select#appointmentInstructions') };
  };

  let form;
  await s.step(STEP.offered, async () => {
    form = await newFromChart(s);
    const select = form.locator('select#appointmentInstructions');
    if (await select.count() === 0) {
      throw new h.SkipCheck('The consultation form shows no Appointment Instructions list (CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP is off)');
    }
    const options = await select.locator('option').evaluateAll(nodes => nodes.map(o => ({ value: o.value, text: o.textContent.trim() })));
    for (const item of [items.first, items.second]) {
      h.assert(options.filter(o => o.value === item.value && o.text === item.label).length === 1,
        `The form does not offer ${JSON.stringify(item.label)} once by its value and label`);
    }
    h.assert(!options.some(o => o.value === items.inactive.value), 'The form offers an inactive instruction');
    h.assert(options[0].value === '', 'The instruction list does not start with the blank option');
  });

  await s.step(STEP.submit, async () => {
    await pickOwnedServiceAndConsultant(form, referrals);
    await form.locator('textarea[name="reasonForConsultation"]').fill(reason);
    await form.locator('textarea[name="clinicalInformation"]').fill(clinicalInfo);
    await form.locator('select#appointmentInstructions').selectOption({ label: items.first.label });
    await save(form, 'submitSaveOnly', CREATED);
    await form.close();
    const created = referrals.requestsFor().filter(id => id !== controlId);
    h.assert(created.length === 1, `Submit stored ${created.length} new referrals, not one`);
    [requestId] = created;
    h.assert(instruction() === items.first.value, `Submit stored the instruction ${JSON.stringify(instruction())}, not the chosen value`);
    await clickAndAwaitReload(list, list.locator('form[action$="/encounter/ViewConsultation"] input[type="submit"]'));
    const reopened = await open('consult-instructions-reopen');
    h.assert(await reopened.select.inputValue() === items.first.value
      && squash(await reopened.select.locator('option:checked').innerText()) === items.first.label,
    'The reopened referral does not have the chosen instruction selected');
    await reopened.form.close();
    controlUnchanged();
  });

  await s.step(STEP.change, async () => {
    const { form: edit, select } = await open('consult-instructions-change');
    await select.selectOption({ label: items.second.label });
    await save(edit, 'update', UPDATED);
    await edit.close();
    h.assert(instruction() === items.second.value, `Update stored ${JSON.stringify(instruction())}, not the second instruction`);
    h.assert(JSON.stringify(referrals.archived(requestId, ['appointmentInstructions'])) === JSON.stringify([[items.first.value]]),
      'The update did not archive the previous instruction');
    h.assert(referrals.field(requestId, 'reason') === reason && referrals.field(requestId, 'clinicalInfo') === clinicalInfo,
      'Changing the instruction changed the referral\'s reason or clinical information');
    controlUnchanged();
  });

  await s.step(STEP.print, async () => {
    const { form: edit, select } = await open('consult-instructions-print');
    h.assert(await select.inputValue() === items.second.value, 'The referral did not reopen with the second instruction');
    const download = edit.waitForEvent('download', { timeout: 30000 });
    await clickAndAwaitReload(edit, edit.locator('input[name="updateAndPrint"]'));
    const text = squash(pdfTextBuffer(fs.readFileSync(await (await download).path())));
    h.assert(text.includes(reason), 'The PDF is not the owned referral');
    h.assert(text.includes(squash(items.second.label)), 'The PDF does not print the chosen instruction');
    h.assert(!text.includes(squash(items.first.label)), 'The PDF prints the previous instruction');
    await edit.close();
    h.assert(instruction() === items.second.value, 'Update And Print Preview changed the stored instruction');
  });

  await s.step(STEP.clear, async () => {
    const archived = referrals.archived(requestId).length;
    const { form: edit, select } = await open('consult-instructions-clear');
    await select.selectOption('');
    await save(edit, 'update', UPDATED);
    await edit.close();
    h.assert(instruction() === '', `The blank option stored ${JSON.stringify(instruction())}, not an empty instruction`);
    h.assert(referrals.archived(requestId).length === archived + 1, 'Clearing the instruction did not archive the previous version');
    const reopened = await open('consult-instructions-reopen');
    h.assert(await reopened.select.inputValue() === '', 'The referral does not reopen on the blank instruction');
    await reopened.form.close();
    controlUnchanged();
  });

  await s.step(STEP.cancel, async () => {
    const archived = referrals.archived(requestId).length;
    const { form: edit, select } = await open('consult-instructions-cancel');
    await select.selectOption({ label: items.first.label });
    await edit.close();
    h.assert(instruction() === '' && referrals.archived(requestId).length === archived,
      'Closing the window over an unsaved instruction changed the referral');
    controlUnchanged();
  });
}

if (require.main === module) {
  runWorkflow('consultation-appointment-instructions', workflow, { openPatient: true, preflight: () => requirePoppler('pdftotext') });
}
module.exports = { workflow, STEP };
