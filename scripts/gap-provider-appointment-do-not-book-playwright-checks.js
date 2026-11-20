#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-appointment-do-not-book — blocking a schedule slot with a "Do Not Book" booking, the
 * front-desk way of closing time (lunch, meetings, a locum's absence).
 *
 * User path (throwaway login, its own day sheet): Schedule ▸ empty slot ▸ Add Appointment popup ▸
 * "(Do Not Book)" ▸ Add Appointment; Schedule ▸ the same slot again ▸ the popup warns and offers no
 * booking button; the neighbouring slot is unaffected; Schedule ▸ the blocking entry ▸ Delete ▸ the
 * slot is bookable again.
 * No other check drives the Do Not Book control or the add popup's blocked-slot state
 * (addappointment.jsp bDnb: "You CANNOT book an appointment on this time slot").
 *
 * Asserted: the (Do Not Book) link fills the keyword with Do_Not_Book and the saved appointment is a
 * patient-less row (demographic_no 0) on the clicked slot's provider/date/time; the day sheet shows
 * it; reopening the slot shows the double-booking alert plus the CANNOT-book sentence and no Add,
 * Group Appt or Repeat button; the next slot offers all three; deleting the block (archived, as every
 * delete) makes the slot bookable again.
 * Fixtures: the throwaway login (lib/throwaway-login-fixture.js); nothing else. Cleanup deletes the
 * owned appointment and archive rows, then the throwaway, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { createUnbookedThrowaway, registerAppointmentCleanup } = require('./lib/gap-provider-fixture');

async function workflow(s) {
  const { sql, config, recorder, marker } = s;
  // createUnbookedThrowaway refuses a provider number that already owns appointment rows, so the
  // by-provider-number cleanup below can only remove rows this run created.
  const fixture = createUnbookedThrowaway(s);
  registerAppointmentCleanup(s, fixture);
  const owner = h.sqlString(fixture.providerNo);
  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const slots = () => schedule.locator(`a.adhour[onclick*="provider_no=${fixture.providerNo}&"]`);
  // A patient-less booking's link is not an `.apptLink`; the edit popup route identifies appointment links.
  const apptLinks = () => schedule.locator('a[onclick*="/appointment/editappointment?appointment_no="]');
  const rows = () => sql.rows(`SELECT appointment_no,name,demographic_no,start_time,end_time FROM appointment WHERE provider_no=${owner} ORDER BY appointment_no`);
  const openSlot = async index => {
    const popup = await ui.clickOpensPopup(schedule, slots().nth(index), { context, recorder, label: 'add-appointment', timeout: 20000 });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    return popup;
  };
  const buttons = async popup => ({
    add: await popup.locator('#addButton').count(), group: await popup.locator('#groupButton').count(),
    repeat: await popup.locator('#apptRepeatButton').count() });
  const SLOT = 4; // 09:00 on the default 15-minute grid; its neighbour is slot 5
  const defects = [];
  let blockId;

  let slotStart;
  await s.step('(Do Not Book) fills the keyword and Add Appointment saves a patient-less blocking row on the slot', async () => {
    const popup = await openSlot(SLOT);
    slotStart = await popup.locator('form#addappt input[name="start_time"]').inputValue();
    h.assert(await popup.locator('.alert-danger', { hasText: /cannot book an appointment on this time slot/i }).count() === 0, 'A free slot already shows the CANNOT-book warning');
    await popup.locator('input[value*="Do Not Book"]').click();
    h.assert(await popup.locator('#keyword').inputValue() === 'Do_Not_Book', 'The Do Not Book link did not fill the keyword');
    await ui.clickAndAwaitReload(popup, popup.locator('#addButton'), { required: false });
    await expectValue(sql, `SELECT COUNT(*) FROM appointment WHERE provider_no=${owner}`, '1', 'The Do Not Book appointment was not saved');
    const [row] = rows();
    blockId = row[0];
    h.assert(row[1] === 'Do_Not_Book' && row[2] === '0' && row[3].startsWith(slotStart.slice(0, 5)),
      'The saved row is not a patient-less Do_Not_Book entry on the clicked slot');
    await popup.close().catch(() => {});
  });

  await s.step('the day sheet shows the blocking entry', async () => {
    await schedule.waitForFunction(() => /do_not_book/i.test(document.body ? document.body.innerText : ""), null, { timeout: 20000 });
    h.assert(await apptLinks().count() === 1, 'The day sheet does not show exactly the one blocking entry');
  });

  await s.step('reopening the slot warns, says it cannot be booked and offers no booking button', async () => {
    const popup = await openSlot(SLOT);
    const warning = popup.locator('.alert-danger', { hasText: /cannot book an appointment on this time slot/i });
    h.assert(await warning.count() === 1 && await warning.isVisible(), 'The blocked slot does not say it cannot be booked');
    h.assert(await popup.locator('.alert-danger:visible', { hasText: /double.?book/i }).count() === 1, 'The blocked slot shows no double-booking alert');
    const token = await popup.locator('form[name="ADDAPPT"] input[name="CSRF-TOKEN"]').first().inputValue();
    h.assert(token, 'The blocked appointment form has no CSRF token');
    const [date, start, end] = sql.rows(`SELECT appointment_date,start_time,end_time FROM appointment WHERE appointment_no=${blockId}`)[0];
    const [year, month, day] = date.split('-');
    const denied = await context.request.post(`${config.baseUrl}/appointment/appointmenteditrepeatbooking`, {
      headers: { 'CSRF-TOKEN': token }, form: { 'CSRF-TOKEN': token,
        groupappt: 'Add Group Appointment', provider_no: fixture.providerNo, appointment_date: date,
        start_time: start, end_time: end, demographic_no: '0', keyword: `${marker} blocked probe`,
        reason: marker, notes: marker, status: 't', reasonCode: '-1',
        everyNum: '1', everyUnit: 'day', endDate: `${day}/${month}/${year}` },
    });
    h.assert(denied.status() < 500, `Blocked-repeat POST answered HTTP ${denied.status()}`);
    if (Number(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${owner} AND appointment_no<>${blockId}`)) > 0) {
      defects.push('a direct repeat-booking POST created an appointment on the Do Not Book slot');
    } else {
      h.assert(/Do Not Book/.test(await denied.text()), 'The blocked-repeat POST did not explain its refusal');
    }
    const present = await buttons(popup);
    h.assert(present.add === 0 && present.group === 0, `The blocked slot still offers booking buttons: ${JSON.stringify(present)}`);
    if (present.repeat !== 0) {
      defects.push('the blocked slot still offers the Repeat button (addappointment.jsp renders it outside the !(bDnb || group) guard)');
      // Use it, as a front-desk user would: a repeat over the blocked slot creates a booking on the blocked time.
      await popup.locator('#reason').fill(`${marker} around the block`);
      await ui.clickAndAwaitReload(popup, popup.locator('#apptRepeatButton'), { required: false });
      await popup.locator('#endDate').waitFor({ state: 'attached' });
      const today = new Date();
      const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
      await ui.pickDate(popup, popup.locator('#endDate'), iso).catch(() => {});
      await popup.getByRole('button', { name: /create/i }).click().catch(() => {});
      await popup.locator('#recurrence-result').waitFor({ state: 'visible' }).catch(() => {});
      const created = Number(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${owner} AND appointment_no<>${blockId}`));
      if (created > 0) defects.push(`Repeat created ${created} booking(s) on the Do_Not_Book slot (RecurringAppointmentService has no block check)`);
    }
    await popup.close().catch(() => {});
  });

  await s.step('the neighbouring slot is unaffected and offers Add, Group Appt and Repeat', async () => {
    const popup = await openSlot(SLOT + 1);
    h.assert(await popup.locator('.alert-danger', { hasText: /cannot book an appointment on this time slot/i }).count() === 0, 'The neighbouring slot is blocked too');
    const present = await buttons(popup);
    h.assert(present.add === 1 && present.group === 1 && present.repeat === 1, `The free slot lacks booking buttons: ${JSON.stringify(present)}`);
    await popup.close();
  });

  await s.step('deleting the block archives it and makes the slot bookable again', async () => {
    const link = schedule.locator(`a[onclick*="/appointment/editappointment?appointment_no=${blockId}&"]`).first();
    const edit = await ui.clickOpensPopup(schedule, link, { context, recorder, label: 'edit-block', timeout: 20000 });
    await edit.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const dialogs = await h.withExpectedDialogs(edit, async () => {
      await ui.clickAndAwaitReload(edit, edit.locator('#deleteButton'), { required: false });
    }, { accept: true });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Deleting the block did not ask for confirmation');
    await expectValue(sql, `SELECT COUNT(*) FROM appointment WHERE appointment_no=${blockId}`, '0', 'The blocking appointment was not deleted');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${owner} AND name='Do_Not_Book'`) === '1', 'The deleted block was not archived');
    await edit.close().catch(() => {});
    await schedule.waitForFunction(() => !/do_not_book/i.test(document.body ? document.body.innerText : ""), null, { timeout: 20000 });
    const popup = await openSlot(SLOT);
    const present = await buttons(popup);
    h.assert(present.add === 1 && await popup.locator('.alert-danger', { hasText: /cannot book an appointment on this time slot/i }).count() === 0,
      'The slot is still blocked after the block was deleted');
    await popup.close();
  });

  await s.step('a blocked slot offers no way to book on it at all', async () => {
    h.assert(defects.length === 0, defects.join('; '));
  });
}

if (require.main === module) runWorkflow('gap-provider-appointment-do-not-book', workflow, { openPatient: false });
module.exports = { workflow };
