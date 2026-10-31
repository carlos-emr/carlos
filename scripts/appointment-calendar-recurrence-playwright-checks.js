#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {runWorkflow} = require('./lib/workflow-session');
// Explicit dates encode the original-date rule, not an implementation of it.
const CASES = [
  {start: '2032-01-29', unit: 'Month', every: '1', dates: ['2032-01-29','2032-02-29','2032-03-29']},
  {start: '2032-01-30', unit: 'Month', every: '1', dates: ['2032-01-30','2032-02-29','2032-03-30']},
  {start: '2032-01-31', unit: 'Month', every: '1', dates: ['2032-01-31','2032-02-29','2032-03-31','2032-04-30']},
  {start: '2033-01-31', unit: 'Month', every: '1', dates: ['2033-01-31','2033-02-28','2033-03-31']},
  {start: '2028-02-29', unit: 'Year', every: '1', dates: ['2028-02-29','2029-02-28','2030-02-28','2031-02-28','2032-02-29']},
  {start: '2028-02-29', unit: 'Year', every: '2', dates: ['2028-02-29','2030-02-28','2032-02-29']},
];
async function workflow(s) {
  process.env.APPOINTMENT_DEMOGRAPHIC_NO = s.patient;
  process.env.APPOINTMENT_PROVIDER_NO = s.provider;
  for (const testCase of CASES) for (const mode of ['new', 'saved']) {
    const now = new Date(), today = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
    const distance = Math.round((Date.parse(testCase.start+'T00:00:00Z') - today) / 86400000);
    h.assert(distance > 0 && distance < 3650, 'Calendar recurrence fixtures must be future dates within ten years');
    process.env.APPOINTMENT_DAYS_AHEAD = String(distance);
    delete require.cache[require.resolve('./appointment-lifecycle-playwright-checks')];
    const f = require('./appointment-lifecycle-playwright-checks'); f.initMysqlDefaults();
    s.cleanup(() => {try {f.cleanupRows();} finally {f.cleanupMysqlDefaults();}});
    await s.step(`${mode} ${testCase.unit} interval ${testCase.every} from ${testCase.start} retains calendar boundaries`, async () => {
      await f.openDaySheet(s.schedule);
      let source, page;
      if (mode === 'saved') {
        source = await f.bookFromSlot(s.context, s.schedule);
        page = await f.openEditPopup(s.context, s.schedule, source.id);
        await Promise.all([page.waitForEvent('domcontentloaded'), page.locator('#repeatButton').click()]);
      } else {
        page = await f.openBookingForm(s.context, s.schedule);
        await page.locator('#reason').fill(f.bookedReason); await page.locator('textarea[name="notes"]').fill(f.bookedNotes);
        await Promise.all([page.waitForEvent('domcontentloaded'), page.locator('#apptRepeatButton').click()]);
      }
      await page.locator('#endDate').waitFor({state: 'visible'});
      const priorDate = new Date(Date.parse(testCase.start+'T12:00:00Z') - 86400000).toISOString().slice(0, 10);
      await ui.pickDate(page, page.locator('#endDate'), priorDate);
      await page.locator(`#dateUnit${testCase.unit}`).check();
      await page.locator('select[name="everyNum"]').selectOption(testCase.every);
      async function submit() {
        const [response] = await Promise.all([
          page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/appointment/appointmenteditrepeatbooking')),
          page.getByRole('button', {name: 'Create repeats', exact: true}).click(),
        ]);
        h.assert(response.status() === 200, `Recurrence returned HTTP ${response.status()}`);
        await page.locator('#recurrence-result').waitFor({state: 'visible'});
        return page.locator('#recurrence-result').innerText();
      }
      h.assert(/end date on or after/i.test(await submit()), 'Invalid end date was not explained');
      h.assert(f.stampedAppointments().length === (mode === 'saved' ? 1 : 0), 'Rejected recurrence wrote rows');
      await ui.pickDate(page, page.locator('#endDate'), testCase.dates.at(-1));
      await submit();
      const rows = f.stampedAppointments();
      h.assert(JSON.stringify(rows.map(row => row.date).sort()) === JSON.stringify(testCase.dates), 'Calendar recurrence dates/count differ from the original-date rule');
      h.assert(rows.every(row => row.provider === s.provider && row.demographic === s.patient
        && row.reason === f.bookedReason && row.notes === f.bookedNotes
        && row.startTime === rows[0].startTime && row.endTime === rows[0].endTime), 'Repeats lost patient, provider, times or details');
      if (source) h.assert(rows.some(row => row.id === source.id && row.date === source.date), 'Recurring creation replaced its source booking');
      await submit();
      h.assert(f.stampedAppointments().length === rows.length, 'Repeated submission created duplicates');
      await page.close(); await f.openDaySheet(s.schedule);
      h.assert(await s.schedule.locator(`a.apptLink[onclick*="appointment_no=${rows[0].id}"]`).count() > 0, 'The original day sheet lost its booking');
      h.assertStrictPage(f.recorder);
      f.cleanupRows(); f.cleanupMysqlDefaults();
    });
  }
}
if (require.main === module) runWorkflow('appointment-calendar-recurrence', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow, CASES};
