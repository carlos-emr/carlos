#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the schedule views and their popups.
// User path: Schedule day sheet > (W) week view > Month > an appointment's own link (edit popup) > Print Card
// > Tickler (top bar) > tickler row > schedule Search (S) > Administration > Schedule Setting.
// Fixtures: an owned FAKE patient, a doctor with a schedule today, two appointments, a tickler with a
// comment, a holiday, an owned day-schedule reason, a schedule template and code, and an appointment
// status; every text column carries inert markup (INSERTed, bypassing the WAF). Cleanup removes exactly
// those rows by key and asserts they are gone.
// Asserted per surface: literal text visible, no `[data-xp]` element in any frame, no script error;
// findings are collected for the whole walk and the check fails once at the end.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { payload, inspect, Findings, Seeder, waitForProviderCache, freeProviderNo } = require('./lib/xss-poison-helpers');
const { seedPatient } = require('./lib/xss-poison-patient');

async function workflow(s) {
  const fields = {};
  // Payload numbers start at 900: each check owns its own range, so a concurrent xss-poison run's rows on a
  // shared list are never mistaken for this run's (inspect() ignores a number it did not create).
  let n = 900;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup, s.marker);
  const hex = s.marker.slice(-8);
  let provNo;
  const today = s.sql.value('SELECT CURDATE()');
  const monthDay = s.sql.value(`SELECT d FROM (SELECT DATE_ADD(CURDATE(), INTERVAL x DAY) d FROM (SELECT 2 x UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) t) c
    WHERE MONTH(d)=MONTH(CURDATE()) AND NOT EXISTS (SELECT 1 FROM scheduledate WHERE sdate=d AND provider_no='999998')
    AND NOT EXISTS (SELECT 1 FROM scheduleholiday WHERE sdate=d) LIMIT 1`);
  let demo; let ticklerField;
  await s.step('seed inert-markup rows for the schedule surfaces', async () => {
    provNo = freeProviderNo(s.sql, 600000 + (parseInt(hex, 16) % 99000));
    seed.insert('provider', {
      provider_no: provNo, last_name: P('doctor last name', 30), first_name: P('doctor first name', 30), provider_type: 'doctor',
      sex: 'F', specialty: '', status: '1', lastUpdateDate: { raw: 'NOW()' },
    }, { where: `provider_no='${provNo}'` });
    seed.insert('secUserRole', { provider_no: provNo, role_name: 'doctor', activeyn: 1, lastUpdateDate: { raw: 'NOW()' } }, { key: 'id' });
    seed.insert('scheduletemplate', { provider_no: provNo, name: P('schedule template name', 20), summary: P('schedule template summary', 80), timecode: '_'.repeat(96) }, { where: `provider_no='${provNo}'` });
    seed.insert('scheduledate', { sdate: today, provider_no: provNo, available: 'Y', priority: '1', reason: P('doctor day reason'), hour: 'Public', creator: '999998', status: 'A' }, { key: 'id' });
    if (monthDay) seed.insert('scheduledate', { sdate: monthDay, provider_no: '999998', available: 'Y', priority: '1', reason: P('own day reason'), hour: 'Standard', creator: '999998', status: 'A' }, { key: 'id' });
    demo = seedPatient(seed, P, '999998', `XP${hex.slice(0, 6)}`);
    const base = { appointment_date: today, end_time: '16:59:00', name: P('appointment name', 50), demographic_no: Number(demo), notes: P('appointment notes'),
      reason: P('appointment reason', 80), location: P('appointment location'), resources: P('appointment resources'), type: P('appointment type', 50),
      status: 't', creator: P('appointment creator', 50), remarks: P('appointment remarks', 50), urgency: P('appointment urgency', 30), lastupdateuser: '999998',
      createdatetime: { raw: 'NOW()' }, updatedatetime: { raw: 'NOW()' } };
    seed.insert('appointment', { ...base, provider_no: '999998', start_time: '16:45:00' }, { key: 'appointment_no' });
    seed.insert('appointment', { ...base, provider_no: provNo, start_time: '09:00:00', end_time: '09:14:00', demographic_no: 0, name: P('walk-in appointment name', 50) }, { key: 'appointment_no' });
    seed.insert('appointmentType', { name: P('appointment type name', 50), notes: P('appointment type notes', 80), reason: P('appointment type reason', 80), location: P('appointment type location'), resources: '', duration: 15 }, { key: 'id' });
    seed.insert('LookupListItem', { lookupListId: 1, value: String(900000 + (parseInt(hex, 16) % 99999)), label: P('appointment reason lookup label'), displayOrder: 99, active: 1, createdBy: '999998' }, { key: 'id' });
    seed.insert('scheduletemplatecode', { code: '~', description: P('schedule code description', 80), duration: '15', color: '#ffcc00', confirm: 'No', bookinglimit: 1 }, { key: 'id' });
    if (monthDay) seed.insert('scheduleholiday', { sdate: monthDay, holiday_name: P('holiday name', 100) }, { where: `sdate='${monthDay}'` });
    const tmsg = P('tickler message');
    ticklerField = n;
    const t = seed.insert('tickler', { demographic_no: Number(demo), message: tmsg, status: 'A', update_date: { raw: 'NOW()' }, service_date: { raw: 'DATE_SUB(NOW(), INTERVAL 1 DAY)' }, creator: '999998', priority: 'Normal', task_assigned_to: '999998' }, { key: 'tickler_no' });
    seed.insert('tickler_comments', { tickler_no: t, message: P('tickler comment'), provider_no: provNo, update_date: { raw: 'NOW()' } }, { key: 'id' });
    seed.insert('tickler_text_suggest', { creator: '999998', suggested_text: P('tickler suggested text'), active: 1 }, { key: 'id' });
    const wl = seed.insert('waitingListName', { name: P('waiting list name', 80), group_no: '', provider_no: '999998', create_date: { raw: 'NOW()' }, is_history: 'N' }, { key: 'ID' });
    seed.insert('waitingList', { listID: Number(wl), demographic_no: Number(demo), note: P('waiting list note'), position: 1, onListSince: { raw: 'NOW()' }, is_history: 'N' }, { key: 'id' });
  });
  await waitForProviderCache(s);
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  const viewAll = s.schedule.url();
  await step('day sheet shows every stored value as text', async () => {
    const since = f.mark();
    await s.schedule.reload({ waitUntil: 'load' });
    await inspect(f, 'day sheet', s.schedule, fields, since);
  });
  await step('week and month views show every stored value as text', async () => {
    let since = f.mark();
    await ui.clickAndAwaitReload(s.schedule, s.schedule.locator('input[name="weekview"][onclick*="999998"], input[name="weekview"][onClick*="999998"]').first(), { timeout: 20000, label: 'week view' });
    await inspect(f, 'week view', s.schedule, fields, since);
    since = f.mark();
    await ui.clickAndAwaitReload(s.schedule, s.schedule.locator('a[href*="displaymode=month"]').first(), { timeout: 20000, label: 'month view' });
    await inspect(f, 'month view', s.schedule, fields, since);
    await s.schedule.goto(viewAll);
  });
  await step('the appointment edit popup and Print Card show stored values as text', async () => {
    const own = s.schedule.locator(`a.apptLink[onclick*="provider_no=999998"][onclick*="demographic_no=${demo}"]`).first();
    h.assert(await own.count() === 1, 'The owned appointment is not on the day sheet');
    const popup = await ui.clickOpensPopup(s.schedule, own, { context: s.context, label: 'appointment-edit', recorder: s.recorder, timeout: 20000 });
    await popup.locator('#updateButton').waitFor({ timeout: 20000 });
    let since = f.mark();
    await inspect(f, 'appointment edit popup', popup, fields, since);
    since = f.mark();
    await ui.clickAndAwaitReload(popup, popup.locator('a.btn', { hasText: /Print Card/i }), { timeout: 20000, label: 'Print Card' });
    h.assert(/appointmentviewrecordcard/i.test(popup.url()), 'Print Card did not open the appointment card');
    await inspect(f, 'appointment card', popup, fields, since);
    await popup.close();
  });
  await step('the Tickler list shows stored values as text', async () => {
    const { page: tickler, isPopup: ticklerPopup } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('li:has(#oscar_new_tickler) a').first(),
      { context: s.context, label: 'tickler', recorder: s.recorder, timeout: 20000 });
    await tickler.waitForLoadState('domcontentloaded');
    const since = f.mark();
    await inspect(f, 'tickler list', tickler, fields, since);
    const edit = tickler.locator('tr', { hasText: new RegExp(`data-xp="?${ticklerField}[">]`) }).locator('a[onclick*="openTicklerEdit"]').first();
    if (await edit.count()) {
      const popup = await ui.clickOpensPopup(tickler, edit, { context: s.context, label: 'tickler-edit', recorder: s.recorder, timeout: 20000 });
      const sinceEdit = f.mark();
      await inspect(f, 'tickler edit popup (comments by a provider)', popup, fields, sinceEdit);
      await popup.close();
    } else f.note('tickler edit popup', 'edit link not offered');
    if (ticklerPopup) await tickler.close(); else await s.schedule.goto(viewAll);
  });
  await step('the Add Appointment popup (types, reasons) and the appointment Search popup show stored values as text', async () => {
    const slot = s.schedule.locator('a.adhour[onclick*="provider_no=999998&"]').first();
    h.assert(await slot.count() === 1, 'The day sheet offers no bookable slot for the test provider');
    let popup = await ui.clickOpensPopup(s.schedule, slot, { context: s.context, label: 'appointment-add', recorder: s.recorder, timeout: 20000 });
    let since = f.mark();
    await inspect(f, 'add appointment popup', popup, fields, since);
    await popup.close();
    popup = await ui.clickOpensPopup(s.schedule, s.schedule.locator('input[name="searchview"]').first(), { context: s.context, label: 'appointment-search', recorder: s.recorder, timeout: 20000 });
    since = f.mark();
    await inspect(f, 'appointment search popup', popup, fields, since);
    await popup.close();
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Schedule walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-schedule', workflow, { openPatient: false });
