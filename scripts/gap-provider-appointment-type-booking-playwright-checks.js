#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-appointment-type-booking — booking an appointment by choosing an Appointment TYPE in
 * the Add Appointment popup (the Type menu fills reason, notes, resources, location and duration
 * from the clinic's appointmentType list), then reopening the booking.
 * schedule-admin-settings-playwright-checks creates/edits/deletes appointment types and only proves
 * the Type menu OFFERS the owned type; nothing chose one while booking.
 *
 * User path (throwaway login, its own day sheet): Schedule ▸ empty slot ▸ Add Appointment popup ▸
 * keyword search ▸ pick the owned patient ▸ Type menu ▸ a type ▸ Add Appointment ▸ the day sheet ▸
 * the appointment's own link (edit popup).
 * Asserted: choosing a type fills the reason, notes, resources and duration from the type; choosing a
 * second type REPLACES the first type's autofill (the user's own typed reason is kept after
 * " -- ") instead of stacking the first type's text; the saved appointment carries the type name,
 * reason, notes, resources and an end time of start + duration - 1 minute (inclusive final minute);
 * the edit popup shows the same type, reason and duration. The stacking assertion comes last.
 * Fixtures: two SEEDED appointmentType rows chosen at run time and never modified (the Type menu reads
 * a 30-minute AppointmentTypeDao cache that SQL-inserted types would miss), the throwaway login
 * (lib/gap-provider-fixture.js, a number owning no appointment rows) and the owned FAKE- patient. Cleanup deletes the owned appointment
 * and archive rows, then the throwaway, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { createUnbookedThrowaway, registerAppointmentCleanup } = require('./lib/gap-provider-fixture');

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  // createUnbookedThrowaway refuses a provider number that already owns appointment rows, so the
  // by-provider-number cleanup can only remove rows this run created.
  const fixture = createUnbookedThrowaway(s);
  registerAppointmentCleanup(s, fixture);
  const owner = h.sqlString(fixture.providerNo);
  // The popup's Type menu reads AppointmentTypeDao's 30-minute cache, which only the app's own writes evict,
  // so types seeded by SQL would not be offered. Use two seeded (demo) types and never modify them.
  const seeded = sql.rows(`SELECT name,duration,reason,notes,location,resources FROM appointmentType
    WHERE name NOT LIKE 'FAKE-PW%' AND duration>0 AND reason<>'' AND notes<>'' AND resources<>'' AND location<>'' ORDER BY id`)
    .map(([name, duration, reason, notes, location, resources]) => ({ name, duration: Number(duration), reason, notes, location, resources }));
  // Two types with different durations and reasons, so a swap is visible in every field.
  const types = [];
  for (const candidate of seeded) {
    if (types.every(t => t.duration !== candidate.duration && t.reason !== candidate.reason)) types.push(candidate);
    if (types.length === 2) break;
  }
  if (types.length < 2) throw new h.SkipCheck('Fewer than two seeded appointment types with distinct durations');
  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const defects = [];
  const USER_TEXT = `${marker} user typed`;
  const field = (popup, name) => popup.locator(`form#addappt [name="${name}"]`);
  const chooseType = async (popup, type) => {
    await popup.locator('#type-button').click();
    await popup.locator('.ui-selectmenu-menu .ui-menu-item', { hasText: type.name }).first().click();
  };
  const minutes = value => Number(value.slice(0, 2)) * 60 + Number(value.slice(3, 5));
  let popup;
  let slotStart;

  await s.step('Add Appointment popup: pick the owned patient, then the Type menu offers the owned types', async () => {
    const slots = schedule.locator(`a.adhour[onclick*="provider_no=${fixture.providerNo}&"]`);
    popup = await ui.clickOpensPopup(schedule, slots.nth(6), { context, recorder, label: 'add-appointment', timeout: 20000 });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    slotStart = await field(popup, 'start_time').inputValue();
    await popup.locator('#keyword').fill(marker);
    await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const pick = popup.locator(`table tr input[type="button"][name="pick_demographic"][value="${patient}"]`).first();
    await pick.waitFor({ state: 'visible' });
    await ui.clickAndAwaitReload(popup, pick, { required: false });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    h.assert(await popup.locator('#demographic_no').inputValue() === patient, 'The booking form did not take the owned patient');
    await popup.locator('#type-button').click();
    for (const t of types) h.assert(await popup.locator('.ui-selectmenu-menu .ui-menu-item', { hasText: t.name }).count() === 1, `The Type menu does not offer ${t.name}`);
    await popup.keyboard.press('Escape');
  });

  await s.step('choosing type A fills the reason, notes, resources and duration from the type', async () => {
    await chooseType(popup, types[0]);
    h.assert(await field(popup, 'reason').inputValue() === types[0].reason, 'The reason was not filled from the type');
    h.assert(await field(popup, 'notes').inputValue() === types[0].notes, 'The notes were not filled from the type');
    h.assert(await field(popup, 'resources').inputValue() === types[0].resources, 'The resources were not filled from the type');
    h.assert(await field(popup, 'duration').inputValue() === String(types[0].duration), 'The duration was not filled from the type');
    h.assert(await field(popup, 'type').inputValue() === types[0].name, 'The type field does not hold the chosen type name');
  });

  await s.step('the user\'s own reason text is kept after the type\'s reason when a type is chosen over it', async () => {
    await field(popup, 'reason').fill(USER_TEXT);
    await chooseType(popup, types[1]);
    const reason = await field(popup, 'reason').inputValue();
    h.assert(reason === `${types[1].reason} -- ${USER_TEXT}`, `Choosing type B over typed text gave reason "${reason}"`);
    h.assert(await field(popup, 'duration').inputValue() === String(types[1].duration)
      && await field(popup, 'notes').inputValue() === types[1].notes, 'Type B did not replace the duration and notes');
  });

  await s.step('the type\'s duration, reason and notes are saved with the booking and shown again by the edit popup', async () => {
    // Settle on type A with a clean reason so the saved text is the type's own.
    await field(popup, 'reason').fill('');
    await chooseType(popup, types[0]);
    await ui.clickAndAwaitReload(popup, popup.locator('#addButton'), { required: false });
    await expectValue(sql, `SELECT COUNT(*) FROM appointment WHERE provider_no=${owner}`, '1', 'The typed appointment was not saved');
    const [row] = sql.rows(`SELECT type,reason,notes,resources,location,start_time,end_time,demographic_no FROM appointment WHERE provider_no=${owner}`);
    h.assert(row[0] === types[0].name && row[1] === types[0].reason && row[2] === types[0].notes && row[3] === types[0].resources && row[7] === patient,
      'The saved booking does not carry the type, reason, notes and resources of type A for the owned patient');
    h.assert(String(row[5]).startsWith(slotStart.slice(0, 5)), `The saved start time ${row[5]} does not match the clicked slot ${slotStart}`);
    h.assert(minutes(row[6]) - minutes(row[5]) === types[0].duration - 1, `The saved end time ${row[6]} is not start ${row[5]} + the type's ${types[0].duration} minutes - 1`);
    h.assert(row[4] === types[0].location, `The saved location "${row[4]}" is not the type's location`);
    await popup.close().catch(() => {});
    const link = schedule.locator('a[onclick*="/appointment/editappointment?appointment_no="]').first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    const edit = await ui.clickOpensPopup(schedule, link, { context, recorder, label: 'edit-appointment', timeout: 20000 });
    await edit.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    h.assert(await edit.locator('#reason').inputValue() === types[0].reason, 'The edit popup does not show the saved reason');
    h.assert(await edit.locator('[name="type"]').first().inputValue() === types[0].name, 'The edit popup does not show the saved type');
    h.assert(await edit.locator('#duration').inputValue() === String(types[0].duration), 'The edit popup does not show the saved duration');
    const copied = edit.waitForEvent('close', { timeout: 20000 });
    await edit.locator('a[onclick*="appointmentcopyrecord"]').click();
    await copied;
  });

  await s.step('changing type after a pasted booking replaces the copied autofill', async () => {
    const pasted = await ui.clickOpensPopup(schedule, schedule.locator(`a.adhour[onclick*="provider_no=${fixture.providerNo}&"]`).nth(12),
      { context, recorder, label: 'paste-type-appointment', timeout: 20000 });
    await pasted.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    h.assert(await field(pasted, 'type').inputValue() === types[0].name
      && await field(pasted, 'reason').inputValue() === types[0].reason, 'The copied type/reason were not pasted');
    await chooseType(pasted, types[1]);
    h.assert(await field(pasted, 'reason').inputValue() === types[1].reason, 'The pasted autofill was retained after changing types');
    await pasted.close();
  });

  await s.step('switching type A to type B replaces the first type\'s autofill instead of stacking it', async () => {
    const p2 = await ui.clickOpensPopup(schedule, schedule.locator(`a.adhour[onclick*="provider_no=${fixture.providerNo}&"]`).nth(12),
      { context, recorder, label: 'add-appointment-2', timeout: 20000 });
    await p2.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await chooseType(p2, types[0]);
    await chooseType(p2, types[1]);
    const reason = await field(p2, 'reason').inputValue();
    if (reason !== types[1].reason) defects.push(`choosing type A then type B leaves the reason "${reason}" instead of type B's "${types[1].reason}" (addappointment.jsp appends the first type's autofill to the second)`);
    await p2.close();
    h.assert(defects.length === 0, defects.join('; '));
  });
}

if (require.main === module) runWorkflow('gap-provider-appointment-type-booking', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
