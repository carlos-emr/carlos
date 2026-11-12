#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The shared date-box calendar (share/calendar/calendar-setup.js, the Calendar.setup shim over Flatpickr) used from
 * the keyboard, on four pages that use it (#5058). Before, most calendar buttons (an <img>, or an <a> with no href)
 * could not be reached with Tab, and the arrow keys stayed in the date box.
 *
 * User paths, all without saving anything:
 *   - Chart ▸ Allergies ▸ Custom Allergy ▸ Start Date (rx/AddReaction2.jsp, an <img> button with no name);
 *   - Schedule ▸ Report ▸ Prevention Report ▸ as-of date (prevention/PreventionReporting.jsp, an <a> around a named <img>);
 *   - Schedule ▸ an empty slot ▸ Add Appointment ▸ Repeat ▸ End date (appointment/appointmenteditrepeatbooking.jsp, an
 *     icon-only <button>), as a throwaway login on its own day sheet, as gap-provider-appointment-repeat-booking does;
 *   - Master Record ▸ Create Invoice ▸ Service Date: on a BC install billing/CA/BC/billingBC.jsp (an <a href> link before
 *     the box), elsewhere billing/CA/ON/billingON.jsp (a <span> around a named image, after the box).
 * Asserted on each: the calendar button is a named button or link that Tab reaches; Enter opens the calendar with
 * focus on a day; ArrowRight moves to the next day and Enter picks it into the box, closes the calendar and returns
 * focus to the button; Enter again and Escape close it without a change, focus back on the button. On the allergy
 * Start Date also: Tab from the box reaches the button; ArrowDown in the empty box moves into the open calendar and
 * Escape returns to the box; and a mouse click on the button and a day still fills the box, with no focus moved into
 * the calendar.
 * Fixtures: the workflow's owned FAKE patient (made a BC patient on a BC install, for its bill form) and a throwaway
 * login (lib/gap-provider-fixture.js) for the appointment window. Nothing is saved: no allergy, no appointment, no bill,
 * and the check asserts none was written for the patient. The Add Appointment window's slot lock (PageMonitor, keyed to
 * the throwaway) is removed with the throwaway's appointment rows, then the throwaway.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const bc = require('./lib/bc-billing-fixture');
const { runWorkflow } = require('./lib/workflow-session');
const { createUnbookedThrowaway, registerAppointmentCleanup } = require('./lib/gap-provider-fixture');
const { masterRecordLink } = require('./lib/master-record-link');

const TIMEOUT = 30000;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  allergy: 'Allergies ▸ Custom Allergy ▸ Start Date: the calendar works from the keyboard, and from the mouse as before',
  prevention: 'Report ▸ Prevention Report ▸ as-of date: the calendar works from the keyboard',
  repeat: 'Add Appointment ▸ Repeat ▸ End date: the calendar works from the keyboard',
  bill: 'Create Invoice ▸ Service Date: the calendar works from the keyboard',
};

const pad = n => String(n).padStart(2, '0');
const FORMATS = {
  'Y-m-d': date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
  'd/m/Y': date => `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`,
};

/** The focused element, when it is a calendar day: its date as Y-m-d; else what it is. */
async function focused(page) {
  return page.evaluate(() => {
    const el = document.activeElement;
    if (el && el.classList.contains('flatpickr-day') && el.dateObj) {
      const d = el.dateObj;
      return { day: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` };
    }
    return { other: el ? (el.id || el.tagName) : null };
  });
}
const openCalendars = page => page.locator('.flatpickr-calendar.open').count();
const isFocused = locator => locator.evaluate(el => el === document.activeElement);

/**
 * Drive one date box's calendar from the keyboard. The button is reached by Tab from the box when `tabFromBox`, else
 * focused directly (it comes before the box on that page). Returns what the box holds afterwards.
 */
async function keyboardPick(page, { label, box, button, format, tabFromBox }) {
  const snapshot = await button.ariaSnapshot();
  h.assert(/^- (button|link) "[^"]+"/.test(snapshot), `${label}: the calendar button is not a named button or link (${snapshot})`);
  h.assert(await button.evaluate(el => el.tabIndex >= 0), `${label}: Tab cannot reach the calendar button`);
  if (tabFromBox) {
    await box.focus();
    if (await openCalendars(page)) await page.keyboard.press('Escape');
    await page.keyboard.press('Tab');
    h.assert(await isFocused(button), `${label}: Tab from the date box did not reach the calendar button (${JSON.stringify(await focused(page))})`);
  } else {
    await button.focus();
  }
  await page.keyboard.press('Enter');
  await page.locator('.flatpickr-calendar.open').waitFor({ state: 'visible', timeout: TIMEOUT });
  const first = await focused(page);
  h.assert(first.day, `${label}: Enter on the calendar button opened it without focus on a day (${JSON.stringify(first)})`);
  await page.keyboard.press('ArrowRight');
  const next = await focused(page);
  const expected = new Date(`${first.day}T12:00:00`);
  expected.setDate(expected.getDate() + 1);
  h.assert(next.day === FORMATS['Y-m-d'](expected), `${label}: ArrowRight moved from ${first.day} to ${JSON.stringify(next)}`);
  await page.keyboard.press('Enter');
  h.assert(await openCalendars(page) === 0, `${label}: Enter on a day did not close the calendar`);
  const value = await box.inputValue();
  h.assert(value === FORMATS[format](expected), `${label}: Enter on ${next.day} filled the box with "${value}"`);
  h.assert(await isFocused(button), `${label}: after a day was picked focus is on ${JSON.stringify(await focused(page))}, not the button`);
  await page.keyboard.press('Enter');
  await page.locator('.flatpickr-calendar.open').waitFor({ state: 'visible', timeout: TIMEOUT });
  h.assert((await focused(page)).day, `${label}: Enter did not open the calendar on a day a second time`);
  await page.keyboard.press('Escape');
  h.assert(await openCalendars(page) === 0, `${label}: Escape did not close the calendar`);
  h.assert(await box.inputValue() === value, `${label}: Escape changed the box to "${await box.inputValue()}"`);
  h.assert(await isFocused(button), `${label}: after Escape focus is on ${JSON.stringify(await focused(page))}, not the button`);
  return value;
}

async function workflow(s) {
  const { sql, patient } = s;
  const hasTable = name => sql.value(`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
    AND table_name=${h.sqlString(name)}`) === '1';
  // The bill form of the install: BC's needs a BC patient; Ontario's bills are stored in billing_on_cheader1.
  const bcInstall = hasTable('billingmaster');
  if (bcInstall) bc.makeBcPatient(s);
  const onBills = hasTable('billing_on_cheader1') ? `(SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient})` : '0';
  const writes = () => sql.value(`SELECT CONCAT_WS('|', (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}),
    (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient}),
    (SELECT COUNT(*) FROM billing WHERE demographic_no=${patient}) + ${onBills})`);
  h.assert(writes() === '0|0|0', 'The owned patient already has an allergy, an appointment or a bill');

  await s.step(STEP.allergy, async () => {
    const chart = await s.chart();
    const list = await s.popup(chart, chart.locator('a[onclick*="showAllergy"]').first(), 'calendar-allergy');
    await list.locator('#searchString').fill('FAKE CALENDAR');
    const dialogs = await h.withExpectedDialogs(list, () => list.locator('input[value="Custom Allergy"]').click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Custom Allergy did not ask for confirmation');
    const form = list.locator('#RxAddAllergyForm');
    await form.waitFor({ state: 'visible', timeout: TIMEOUT });
    const box = form.locator('#startDate');
    const button = form.locator('#startDateCal');
    await keyboardPick(list, { label: 'allergy Start Date', box, button, format: 'Y-m-d', tabFromBox: true });

    await box.fill('');
    await box.focus();
    await list.locator('.flatpickr-calendar.open').waitFor({ state: 'visible', timeout: TIMEOUT });
    await list.keyboard.press('ArrowDown');
    h.assert((await focused(list)).day, `allergy Start Date: ArrowDown in the empty box did not move into the calendar (${JSON.stringify(await focused(list))})`);
    await list.keyboard.press('Escape');
    h.assert(await openCalendars(list) === 0 && await isFocused(box) && await box.inputValue() === '',
      'allergy Start Date: Escape in the calendar did not close it and give focus back to the empty box');

    // The mouse, as before: a click on the button opens the calendar without taking focus into it, a click on a day fills the box.
    await list.locator('#reactionDescription').click();
    await button.click();
    await list.locator('.flatpickr-calendar.open').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(!(await focused(list)).day, 'allergy Start Date: a mouse click on the button moved focus into the calendar');
    const today = list.locator('.flatpickr-calendar.open .flatpickr-day.today');
    const todayIso = await today.evaluate(el => `${el.dateObj.getFullYear()}-${String(el.dateObj.getMonth() + 1).padStart(2, '0')}-${String(el.dateObj.getDate()).padStart(2, '0')}`);
    await today.click();
    h.assert(await openCalendars(list) === 0 && await box.inputValue() === todayIso,
      `allergy Start Date: a mouse click on today filled the box with "${await box.inputValue()}", not ${todayIso}`);
    await list.close();
  });

  await s.step(STEP.prevention, async () => {
    const { page: index } = await ui.clickOpensPopupOrNavigates(s.schedule,
      s.schedule.locator("a[onclick*='/report/ViewReportindex'], a[href*='/report/ViewReportindex']").first(),
      { context: s.context, recorder: s.recorder, label: 'calendar-report-index', timeout: TIMEOUT });
    const { page: report } = await ui.clickOpensPopupOrNavigates(index, index.locator("a[href*='/prevention/PreventionReport']").first(),
      { context: s.context, recorder: s.recorder, label: 'calendar-prevention-report', timeout: TIMEOUT });
    await report.locator('#asofDate').waitFor({ state: 'visible', timeout: TIMEOUT });
    await keyboardPick(report, { label: 'prevention report as-of date', box: report.locator('#asofDate'), button: report.locator('#date'),
      format: 'Y-m-d', tabFromBox: true });
    if (report !== s.schedule) await report.close();
    if (index !== s.schedule && !index.isClosed()) await index.close();
  });

  // The throwaway's own day sheet has its column whatever the shared login's schedule view shows.
  const throwaway = createUnbookedThrowaway(s);
  registerAppointmentCleanup(s, throwaway);
  s.cleanup(() => {
    // The Add Appointment window locks its slot (PageMonitor) for the throwaway; nothing else holds that number.
    sql.execute(`DELETE FROM PageMonitor WHERE providerNo=${h.sqlString(throwaway.providerNo)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM PageMonitor WHERE providerNo=${h.sqlString(throwaway.providerNo)}`) === '0',
      'The throwaway\'s slot lock was not removed');
  });

  await s.step(STEP.repeat, async () => {
    const context = await h.newContext(s.context.browser(), s.config);
    context.setDefaultTimeout(TIMEOUT);
    context.on('page', page => h.wireStrictPage(page, 'calendar-throwaway', s.recorder));
    try {
      const schedule = await h.login(context, { ...s.config, testUser: throwaway.username }, s.recorder, { label: 'calendar-throwaway-login' });
      const slots = schedule.locator(`a.adhour[onclick*="provider_no=${throwaway.providerNo}&"]`);
      h.assert(await slots.count() > 0, 'The throwaway\'s day sheet offers no slot in its column');
      const popup = await ui.clickOpensPopup(schedule, slots.first(), { context, recorder: s.recorder,
        label: 'calendar-add-appointment', timeout: TIMEOUT });
      await popup.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      await popup.locator('#keyword').fill(s.marker);
      await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
      await popup.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      const pick = popup.locator(`table tr input[type="button"][name="pick_demographic"][value="${patient}"]`).first();
      await pick.waitFor({ state: 'visible', timeout: TIMEOUT });
      await ui.clickAndAwaitReload(popup, pick, { required: false });
      await popup.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      await ui.clickAndAwaitReload(popup, popup.locator('#apptRepeatButton'), { required: false });
      await popup.locator('#endDate').waitFor({ state: 'attached', timeout: TIMEOUT });
      await h.assertNotErrorPage(popup, 'repeat booking form');
      await keyboardPick(popup, { label: 'repeat booking End date', box: popup.locator('#endDate'), button: popup.locator('#f_trigger_b'),
        format: 'd/m/Y', tabFromBox: true });
      await popup.close();
    } finally {
      await context.close().catch(() => {});
    }
  });

  await s.step(STEP.bill, async () => {
    const link = (bcInstall ? masterRecordLink(s.master, '/billing?billRegion=', 'appointment_no=0&')
      : masterRecordLink(s.master, '/billing?billRegion=ON')).first();
    h.assert(await link.count() === 1, 'The master record has no Create Invoice link');
    const form = await s.popup(s.master, link, 'calendar-bill-form');
    await form.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(form, 'bill form');
    if (bcInstall) {
      // The Service Date link comes before its box, so it is focused directly rather than tabbed to from the box.
      await keyboardPick(form, { label: 'BC bill form Service Date', box: form.locator('#xml_appointment_date'),
        button: form.locator('#hlSDate'), format: 'Y-m-d', tabFromBox: false });
    } else {
      await keyboardPick(form, { label: 'Ontario bill form Service Date', box: form.locator('#service_date:visible'),
        button: form.locator('#service_date_cal'), format: 'Y-m-d', tabFromBox: true });
    }
    await form.close();
    h.assert(writes() === '0|0|0', `Using the calendars wrote rows for the owned patient (allergies|appointments|bills = ${writes()})`);
  });
}

if (require.main === module) runWorkflow('calendar-keyboard', workflow);
module.exports = { workflow, STEP };
