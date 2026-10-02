#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the schedule views and their popups.
// User path: Schedule day sheet > (W) week view > Month > an appointment's own link (edit popup) > Print Card
// > Tickler (top bar) > tickler row > schedule Search (S) > Administration > Schedule Setting > Template
// Setting for Public (and Holiday / Template Code Setting where this login is offered them).
// Fixtures: an owned FAKE patient, a doctor with a schedule, a Public schedule template, the logged-in provider's
// own schedule day with a reason and a holiday on it later this month, two appointments, an appointment type,
// an appointment-reason lookup item, a schedule template code, a tickler with a comment, a tickler suggestion
// and a waiting list; every text column carries inert markup (INSERTed, bypassing the WAF). Cleanup removes
// exactly those rows by key and asserts they are gone.
// Asserted per surface: literal text visible, no `[data-xp]` element in any frame, no script error, and the
// fields that surface is known to show are shown; findings are collected for the whole walk and the check
// fails once at the end.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { payload, inspect, Findings, Seeder, waitForProviderCache, freeProviderNo, clickAdminItem, fieldIds } = require('./lib/xss-poison-helpers');
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
  // Every row the schedule shows as the user's own belongs to the provider the session logged in as.
  const me = s.provider;
  const today = s.sql.value('SELECT CURDATE()');
  // Any other day of this month on which the logged-in provider has no schedule and there is no holiday: the
  // month view shows that day's reason and holiday name. Without one the month-view fixtures cannot exist, and
  // the check says so instead of passing without them.
  const monthDay = s.sql.value(`SELECT d FROM (SELECT DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL x DAY) d FROM (SELECT seq x FROM seq_0_to_30) t) c
    WHERE MONTH(d)=MONTH(CURDATE()) AND d<>CURDATE() AND NOT EXISTS (SELECT 1 FROM scheduledate WHERE sdate=d AND provider_no=${h.sqlString(me)})
    AND NOT EXISTS (SELECT 1 FROM scheduleholiday WHERE sdate=d) ORDER BY ABS(DATEDIFF(d, CURDATE())) LIMIT 1`);
  if (!monthDay) throw new h.SkipCheck('every other day of this month already has a schedule or a holiday for the test provider, so the month-view fixtures cannot be placed');
  let demo; let ticklerField;
  await s.step('seed inert-markup rows for the schedule surfaces', async () => {
    provNo = freeProviderNo(s.sql, 600000 + (parseInt(hex, 16) % 99000));
    seed.insert('provider', {
      provider_no: provNo, last_name: P('doctor last name', 30), first_name: P('doctor first name', 30), provider_type: 'doctor',
      sex: 'F', specialty: '', status: '1', lastUpdateDate: { raw: 'NOW()' },
    }, { where: `provider_no='${provNo}'` });
    seed.insert('secUserRole', { provider_no: provNo, role_name: 'doctor', activeyn: 1, lastUpdateDate: { raw: 'NOW()' } }, { key: 'id' });
    // A Public template: Template Setting puts the chosen provider's NAME into its URL, and the front door's WAF
    // refuses the seeded doctor's (markup-carrying) name there (403, CRS) before the application renders anything,
    // so the template list is reached through "Public", which is how shared templates are kept anyway.
    const templateName = P('schedule template name', 20);
    seed.insert('scheduletemplate', { provider_no: 'Public', name: templateName, summary: P('schedule template summary', 80), timecode: '_'.repeat(96) },
      { where: `provider_no='Public' AND name=${h.sqlString(templateName)}` });
    seed.insert('scheduledate', { sdate: today, provider_no: provNo, available: 'Y', priority: '1', reason: P('doctor day reason'), hour: 'Public', creator: me, status: 'A' }, { key: 'id' });
    seed.insert('scheduledate', { sdate: monthDay, provider_no: me, available: 'Y', priority: '1', reason: P('own day reason'), hour: 'Standard', creator: me, status: 'A' }, { key: 'id' });
    demo = seedPatient(seed, P, me, `XS${hex}`);
    const base = { appointment_date: today, end_time: '16:59:00', name: P('appointment name', 50), demographic_no: Number(demo), notes: P('appointment notes'),
      reason: P('appointment reason', 80), location: P('appointment location'), resources: P('appointment resources'), type: P('appointment type', 50),
      status: 't', creator: P('appointment creator', 50), remarks: P('appointment remarks', 50), urgency: P('appointment urgency', 30), lastupdateuser: me,
      createdatetime: { raw: 'NOW()' }, updatedatetime: { raw: 'NOW()' } };
    seed.insert('appointment', { ...base, provider_no: me, start_time: '16:45:00' }, { key: 'appointment_no' });
    seed.insert('appointment', { ...base, provider_no: provNo, start_time: '09:00:00', end_time: '09:14:00', demographic_no: 0, name: P('walk-in appointment name', 50) }, { key: 'appointment_no' });
    seed.insert('appointmentType', { name: P('appointment type name', 50), notes: P('appointment type notes', 80), reason: P('appointment type reason', 80), location: P('appointment type location'), resources: '', duration: 15 }, { key: 'id' });
    seed.insert('LookupListItem', { lookupListId: 1, value: String(900000 + (parseInt(hex, 16) % 99999)), label: P('appointment reason lookup label'), displayOrder: 99, active: 1, createdBy: me }, { key: 'id' });
    seed.insert('scheduletemplatecode', { code: '~', description: P('schedule code description', 80), duration: '15', color: '#ffcc00', confirm: 'No', bookinglimit: 1 }, { key: 'id' });
    seed.insert('scheduleholiday', { sdate: monthDay, holiday_name: P('holiday name', 100) }, { where: `sdate='${monthDay}'` });
    const tmsg = P('tickler message');
    ticklerField = n;
    const t = seed.insert('tickler', { demographic_no: Number(demo), message: tmsg, status: 'A', update_date: { raw: 'NOW()' }, service_date: { raw: 'DATE_SUB(NOW(), INTERVAL 1 DAY)' }, creator: me, priority: 'Normal', task_assigned_to: me }, { key: 'tickler_no' });
    seed.insert('tickler_comments', { tickler_no: t, message: P('tickler comment'), provider_no: provNo, update_date: { raw: 'NOW()' } }, { key: 'id' });
    seed.insert('tickler_text_suggest', { creator: me, suggested_text: P('tickler suggested text'), active: 1 }, { key: 'id' });
    const wl = seed.insert('waitingListName', { name: P('waiting list name', 80), group_no: '', provider_no: me, create_date: { raw: 'NOW()' }, is_history: 'N' }, { key: 'ID' });
    seed.insert('waitingList', { listID: Number(wl), demographic_no: Number(demo), note: P('waiting list note'), position: 1, onListSince: { raw: 'NOW()' }, is_history: 'N' }, { key: 'id' });
  });
  await waitForProviderCache(s);
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  // What each surface is known to show (live runs on the packaged install). A surface that stops showing them
  // is a MISSING finding, so a changed or empty view cannot pass as an encoded one.
  const doctor = fieldIds(fields, 'doctor last name', 'doctor first name');
  const patient = fieldIds(fields, 'patient last name', 'patient first name');
  const viewAll = s.schedule.url();
  await step('day sheet shows every stored value as text', async () => {
    const since = f.mark();
    await s.schedule.reload({ waitUntil: 'load' });
    await inspect(f, 'day sheet', s.schedule, fields, since, { expect: [...doctor, ...fieldIds(fields, 'tickler message')] });
  });
  await step('week and month views show every stored value as text', async () => {
    let since = f.mark();
    await ui.clickAndAwaitReload(s.schedule, s.schedule.locator(`input[name="weekview"][onclick*="${me}"], input[name="weekview"][onClick*="${me}"]`).first(), { timeout: 20000, label: 'week view' });
    await inspect(f, 'week view', s.schedule, fields, since, { expect: fieldIds(fields, 'tickler message') });
    since = f.mark();
    await ui.clickAndAwaitReload(s.schedule, s.schedule.locator('a[href*="displaymode=month"]').first(), { timeout: 20000, label: 'month view' });
    // The week view's Month link opens the month the WEEK starts in, which early in a month is the previous one;
    // step with the month view's own next/previous links to the month that holds the seeded day.
    const [wantYear, wantMonth] = monthDay.split('-').map(Number);
    for (let i = 0; i < 2; i += 1) {
      const shown = new URL(s.schedule.url()).searchParams;
      const at = Number(shown.get('year')) * 12 + Number(shown.get('month'));
      if (at === wantYear * 12 + wantMonth) break;
      const step = at < wantYear * 12 + wantMonth ? Number(shown.get('month')) + 1 : Number(shown.get('month')) - 1;
      await ui.clickAndAwaitReload(s.schedule, s.schedule.locator(`a[href*="displaymode=month"][href*="&month=${step}&"][href*="providerview="]`).first(), { timeout: 20000, label: 'month view (adjacent month)' });
    }
    // The day's reason is printed only on installs without multisites (with them the page shows the site's short
    // name instead), so only the holiday name is required here; the reason is inspected wherever it does show.
    await inspect(f, 'month view', s.schedule, fields, since, { expect: fieldIds(fields, 'holiday name') });
    await s.schedule.goto(viewAll);
  });
  await step('the appointment edit popup and Print Card show stored values as text', async () => {
    const own = s.schedule.locator(`a.apptLink[onclick*="provider_no=${me}"][onclick*="demographic_no=${demo}"]`).first();
    h.assert(await own.count() === 1, 'The owned appointment is not on the day sheet');
    const popup = await ui.clickOpensPopup(s.schedule, own, { context: s.context, label: 'appointment-edit', recorder: s.recorder, timeout: 20000 });
    await popup.locator('#updateButton').waitFor({ timeout: 20000 });
    let since = f.mark();
    await inspect(f, 'appointment edit popup', popup, fields, since,
      { expect: [...patient, ...fieldIds(fields, 'appointment notes', 'appointment reason', 'appointment location', 'appointment resources', 'appointment remarks')] });
    since = f.mark();
    await ui.clickAndAwaitReload(popup, popup.locator('a.btn', { hasText: /Print Card/i }), { timeout: 20000, label: 'Print Card' });
    h.assert(/appointmentviewrecordcard/i.test(popup.url()), 'Print Card did not open the appointment card');
    await inspect(f, 'appointment card', popup, fields, since, { expect: fieldIds(fields, 'appointment name') });
    await popup.close();
  });
  await step('the Tickler list shows stored values as text', async () => {
    const { page: tickler, isPopup: ticklerPopup } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('li:has(#oscar_new_tickler) a').first(),
      { context: s.context, label: 'tickler', recorder: s.recorder, timeout: 20000 });
    await tickler.waitForLoadState('domcontentloaded');
    const since = f.mark();
    await inspect(f, 'tickler list', tickler, fields, since, { expect: [...patient, ...fieldIds(fields, 'tickler message', 'tickler comment')] });
    const edit = tickler.locator('tr', { hasText: new RegExp(`data-xp="?${ticklerField}[">]`) }).locator('a[onclick*="openTicklerEdit"]').first();
    if (await edit.count()) {
      const popup = await ui.clickOpensPopup(tickler, edit, { context: s.context, label: 'tickler-edit', recorder: s.recorder, timeout: 20000 });
      const sinceEdit = f.mark();
      await inspect(f, 'tickler edit popup (comments by a provider)', popup, fields, sinceEdit,
        { expect: [...patient, ...fieldIds(fields, 'tickler message', 'tickler comment', 'tickler suggested text')] });
      await popup.close();
    } else f.missing('tickler edit popup', 'the seeded tickler offers no edit link');
    if (ticklerPopup) await tickler.close(); else await s.schedule.goto(viewAll);
  });
  await step('the Add Appointment popup (types, reasons) and the appointment Search popup show stored values as text', async () => {
    // The logged-in provider's column (and its a.adhour slots) is drawn whether or not it has a schedule that
    // day (appointmentprovideradminday.jsp skips only OTHER providers without one), while the seeded doctor is
    // in no group of the test login and so has no column on this day sheet at all: the login's own slot is the
    // one to use.
    const slot = s.schedule.locator(`a.adhour[onclick*="provider_no=${me}&"]`).first();
    h.assert(await slot.count() === 1, 'The day sheet offers no bookable slot for the test provider');
    let popup = await ui.clickOpensPopup(s.schedule, slot, { context: s.context, label: 'appointment-add', recorder: s.recorder, timeout: 20000 });
    let since = f.mark();
    await inspect(f, 'add appointment popup', popup, fields, since, { expect: fieldIds(fields, 'appointment type name') });
    await popup.close();
    popup = await ui.clickOpensPopup(s.schedule, s.schedule.locator('input[name="searchview"]').first(), { context: s.context, label: 'appointment-search', recorder: s.recorder, timeout: 20000 });
    since = f.mark();
    await inspect(f, 'appointment search popup', popup, fields, since, { expect: doctor });
    await popup.close();
  });
  await step('Administration > Schedule Setting and its template pages show stored values as text', async () => {
    const opened = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, label: 'administration', recorder: s.recorder, timeout: 20000 });
    const admin = opened.page;
    let since = f.mark();
    await clickAdminItem(admin, 'Schedule Setting');
    const frame = admin.frameLocator('#dynamic-content iframe').first();
    await frame.locator('select[name="providerid"]').waitFor({ timeout: 20000 });
    await inspect(f, 'schedule setting', admin, fields, since, { expect: doctor });
    // Template Setting for Public: the popup lists the shared templates, the seeded one among them.
    await frame.locator('select[name="providerid"]').selectOption('Public');
    since = f.mark();
    const templates = await ui.clickOpensPopup(admin, frame.locator('a[onclick="go()"]'), { context: s.context, label: 'schedule-template', recorder: s.recorder, timeout: 20000 });
    await templates.waitForLoadState('networkidle').catch(() => {});
    await inspect(f, 'schedule template setting (Public)', templates, fields, since, { expect: fieldIds(fields, 'schedule template name') });
    await templates.close();
    // Holiday and Template Code Setting are drawn only without site/team access privacy (scheduletemplatesetting.jsp);
    // when this login is not offered them they are noted, not counted.
    for (const [route, surface, expect] of [['HolidaySetting', 'holiday setting', ['holiday name']], ['TemplateCodeSetting', 'schedule template code setting', ['schedule code description']]]) {
      const link = frame.locator(`a[onclick*="/schedule/${route}"]`).first();
      if (!(await link.count())) { f.note(surface, 'not offered to this login (site or team access privacy)'); continue; }
      since = f.mark();
      const popup = await ui.clickOpensPopup(admin, link, { context: s.context, label: surface, recorder: s.recorder, timeout: 20000 });
      await popup.waitForLoadState('networkidle').catch(() => {});
      await inspect(f, surface, popup, fields, since, { expect: fieldIds(fields, ...expect) });
      await popup.close();
    }
    if (opened.isPopup) await admin.close(); else await s.schedule.goto(viewAll);
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Schedule walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-schedule', workflow, { openPatient: false });
