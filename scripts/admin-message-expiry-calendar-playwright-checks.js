#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * admin-message-expiry-calendar: the Expiry Day calendar of the System Message and Facility Message editors.
 *
 * User path: Schedule ▸ Administration ▸ CAISI ▸ System Messages (or Facility Messages), hosted in the
 * administration iframe ▸ Create New Message ▸ the calendar icon beside Expiry Day. The icon opens
 * calendar/oscarCalendarPopup?type=caisi with the editor's form and field names (openerForm, openerElement); its
 * Last and Next links move a month, and picking a day writes year-month-day into the editor's Expiry Day box.
 * admin-messages saves a message with the date typed into the box, and admin-facility-messages seeds its rows;
 * neither opens the calendar.
 *
 *   1. System Messages ▸ Create New Message: the calendar opens on the current month, with one link per day,
 *      for the editor's form and Expiry Day field;
 *   2. Next and Last move one month each way;
 *   3. Facility Messages ▸ Create New Message: its calendar opens the same popup for its own form and field;
 *   4. LAST: picking a day closes the popup and writes the date into the editor's Expiry Day box, in both editors.
 *
 * Nothing is saved, so no message rows are written. SKIPs on a deployment with the CAISI module off
 * (carlos.properties caisi=off): both editors' menu entries live in the CAISI administration group.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const TIMEOUT = 20000;
// A day every month has, so the pick never depends on the month's length.
const PICK_DAY = 15;
const EDITORS = {
  system: {menu: 'System Messages', route: '/SystemMessage', form: 'systemMessageForm', field: 'system_message.expiry_day'},
  facility: {menu: 'Facility Messages', route: '/FacilityMessage', form: 'facilityMessageForm', field: 'facility_message.expiry_day'},
};

async function workflow(s) {
  // The current month as the server sees it: the editor passes it to the calendar (year, month in the popup's
  // URL), read in step 1. Not the database's clock, which can be in another time zone.
  let year;
  let month;
  let days;
  let current;
  let nextMonth;

  let admin;
  /** Opens an editor's Create New Message form in the administration iframe; returns the frame. */
  async function openEditor(editor) {
    if (!admin || admin.isClosed()) {
      ({page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
        {context: s.context, recorder: s.recorder, label: 'messages-administration', timeout: TIMEOUT}));
      await admin.locator('#dynamic-content, .adminBox').first().waitFor({timeout: TIMEOUT});
    }
    const link = admin.getByRole('link', {name: editor.menu, exact: true, includeHidden: true}).first();
    if (await link.count() === 0) throw new h.SkipCheck(`${editor.menu} is not offered by the administration menu on this deployment (caisi module off)`);
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const iframe = admin.locator(`#dynamic-content iframe[src*="${editor.route}"]`).first();
    await iframe.waitFor({timeout: TIMEOUT});
    const frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, `${editor.menu} did not load in the administration frame`);
    await frame.locator('input[value="Create New Message"]').waitFor({timeout: TIMEOUT});
    const navigated = admin.waitForEvent('framenavigated', {predicate: candidate => candidate === frame, timeout: TIMEOUT});
    navigated.catch(() => {});
    await frame.locator('input[value="Create New Message"]').click();
    await navigated;
    await frame.locator(`input[name="${editor.field}"]`).waitFor({timeout: TIMEOUT});
    return frame;
  }
  /** Clicks the calendar icon beside Expiry Day; returns the popup and the names it was given. */
  async function openCalendar(frame, editor) {
    const popup = await s.popup(frame, frame.locator('a:has(img[src*="calendar.jpg"])').first(), `${editor.menu} calendar`);
    await popup.waitForLoadState('domcontentloaded');
    const url = new URL(popup.url());
    h.assert(h.pathOnly(popup.url()).endsWith('/calendar/oscarCalendarPopup'), `The calendar opened ${popup.url()}`);
    h.assert(url.searchParams.get('type') === 'caisi', `The calendar was opened as type ${url.searchParams.get('type')}`);
    h.assert(url.searchParams.get('openerForm') === editor.form && url.searchParams.get('openerElement') === editor.field,
      `The calendar was given form ${url.searchParams.get('openerForm')} and field ${url.searchParams.get('openerElement')}`);
    return popup;
  }
  const shownMonth = popup => popup.locator('span.title').first().innerText().then(text => text.trim());
  const dayLinks = popup => popup.locator('table[border="1"] a');

  let systemFrame;
  let systemPopup;
  await s.step('System Messages ▸ Create New Message: the Expiry Day calendar opens on the current month', async () => {
    systemFrame = await openEditor(EDITORS.system);
    systemPopup = await openCalendar(systemFrame, EDITORS.system);
    const opened = new URL(systemPopup.url()).searchParams;
    [year, month] = [Number(opened.get('year')), Number(opened.get('month'))];
    h.assert(Number.isInteger(year) && month >= 1 && month <= 12, `The editor passed the calendar year ${opened.get('year')} and month ${opened.get('month')}`);
    // And it is this month: a time-zone difference between the runner and the server is under a day, so within one
    // month of the runner's own date.
    const today = new Date();
    const apart = Math.abs(year * 12 + month - 1 - (today.getFullYear() * 12 + today.getMonth()));
    h.assert(apart <= 1, `The editor passed ${year}-${month}, not this month (${today.getFullYear()}-${today.getMonth() + 1})`);
    days = new Date(year, month, 0).getDate();
    current = `${year}-${month}`;
    nextMonth = month === 12 ? `${year + 1}-1` : `${year}-${month + 1}`;
    h.assert(await shownMonth(systemPopup) === current, `The calendar shows ${await shownMonth(systemPopup)}, not ${current}`);
    h.assert(await dayLinks(systemPopup).count() === days, `The calendar lists ${await dayLinks(systemPopup).count()} days, not ${days}`);
  });

  await s.step('Next and Last move one month each way', async () => {
    await Promise.all([systemPopup.waitForURL(/delta=1(&|$)/, {timeout: TIMEOUT}),
      systemPopup.locator('a[href*="delta=1&"]').first().click()]);
    h.assert(await shownMonth(systemPopup) === nextMonth, `Next shows ${await shownMonth(systemPopup)}, not ${nextMonth}`);
    await Promise.all([systemPopup.waitForURL(/delta=-1(&|$)/, {timeout: TIMEOUT}),
      systemPopup.locator('a[href*="delta=-1&"]').first().click()]);
    h.assert(await shownMonth(systemPopup) === current, `Last shows ${await shownMonth(systemPopup)}, not ${current}`);
    const url = new URL(systemPopup.url());
    h.assert(url.searchParams.get('openerForm') === EDITORS.system.form && url.searchParams.get('openerElement') === EDITORS.system.field,
      'Moving a month lost the editor\'s form or field');
    await systemPopup.close();
  });

  await s.step('Facility Messages ▸ Create New Message: its calendar opens the same popup for its own form and field', async () => {
    const facilityFrame = await openEditor(EDITORS.facility);
    const popup = await openCalendar(facilityFrame, EDITORS.facility);
    h.assert(await shownMonth(popup) === current, `The Facility Messages calendar shows ${await shownMonth(popup)}, not ${current}`);
    await popup.close();
  });

  // Last: the pick itself (pending #4923, the fix for #4916). Each editor is opened just before its own pick: opening one loads
  // it into the administration iframe in place of the other, so a frame kept from earlier is detached by then.
  await s.step('picking a day closes the popup and writes the date into the editor\'s Expiry Day box, in both editors', async () => {
    const expected = `${year}-${month}-${PICK_DAY}`;
    for (const key of ['facility', 'system']) {
      const editor = EDITORS[key];
      const frame = await openEditor(editor);
      const box = frame.locator(`input[name="${editor.field}"]`);
      await box.fill('');
      const popup = await openCalendar(frame, editor);
      const closed = popup.waitForEvent('close', {timeout: TIMEOUT}).then(() => true, () => false);
      const day = dayLinks(popup).filter({hasText: new RegExp(`^\\s*${PICK_DAY}\\s*$`)});
      h.assert(await day.count() === 1, `${editor.menu}: the calendar has ${await day.count()} links for day ${PICK_DAY}, not one`);
      // The popup closes as the pick lands, which can end the click with an error.
      await day.click({noWaitAfter: true}).catch(() => {});
      h.assert(await closed, `${editor.menu}: picking a day left the calendar open`);
      h.assert(await box.inputValue() === expected, `${editor.menu}: the Expiry Day box reads "${await box.inputValue()}", not ${expected}`);
    }
  });
}

if (require.main === module) runWorkflow('admin-message-expiry-calendar', workflow, {openPatient: false});
module.exports = {workflow};
