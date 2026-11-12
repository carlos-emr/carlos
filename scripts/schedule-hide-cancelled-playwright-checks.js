#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The day sheet's Hide/Show Cancelled button (toggleCancelled in provider/schedulePage.js.jsp): it hides the
 * cancelled appointments of the day sheet and keeps the choice in the browser (localStorage hideCancelled), so the
 * next day sheet opens the same way. appointment-cancel-no-show checks that a cancelled appointment is greyed (class
 * Cancelled); nothing pressed the button.
 *
 * User path (throwaway login, its own day sheet): Schedule ▸ the eye button next to the date ▸ reload ▸ the button
 * again ▸ reload.
 * Asserted, on two owned appointments for today (one cancelled, one booked): the day sheet shows both, the cancelled
 * one greyed; Hide hides exactly the cancelled one and the button then offers Show; the choice holds after a reload;
 * LAST, Show brings the cancelled one back and that also holds after a reload. The booked one is shown throughout.
 * Fixtures: the throwaway login (lib/gap-provider-fixture.js, a number owning no appointment rows), the owned FAKE-
 * patient and the two appointments seeded by SQL. The check's browser context, and the hideCancelled flag in it,
 * end with the run. Cleanup deletes the appointment rows by the throwaway's number, then the throwaway, and
 * asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createUnbookedThrowaway, registerAppointmentCleanup } = require('./lib/gap-provider-fixture');

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  shown: 'the day sheet shows the cancelled and the booked appointment, the cancelled one greyed',
  hide: 'Hide Cancelled hides exactly the cancelled appointment and the button then offers Show',
  kept: 'the hidden state holds when the day sheet reloads',
  show: 'Show Cancelled brings the cancelled appointment back, and that holds after a reload too',
};

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  const fixture = createUnbookedThrowaway(s);
  registerAppointmentCleanup(s, fixture);
  const owner = h.sqlString(fixture.providerNo);
  // The schedule opens on the server's today (CURDATE()), which can differ from the runner's clock around midnight.
  const dateKey = sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y-%m-%d')");
  const seeded = {};
  for (const [label, start, status] of [['cancelled', '09:00:00', 'C'], ['booked', '10:00:00', 't']]) {
    seeded[label] = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,reason,
        notes,status,createdatetime,updatedatetime,creator,lastupdateuser)
      VALUES (${owner},${h.sqlString(dateKey)},${h.sqlString(start)},${h.sqlString(start.replace(':00:00', ':14:00'))},
        ${h.sqlString(`${marker},Workflow`)},${patient},${h.sqlString(`${marker} ${label}`)},'',${h.sqlString(status)},NOW(),NOW(),
        ${h.sqlString(s.provider)},${h.sqlString(s.provider)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(seeded[label]), `The ${label} appointment was not seeded`);
  }

  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const link = id => schedule.locator(`a[onclick*="/appointment/editappointment?appointment_no=${id}&"]`).first();
  const cell = id => schedule.locator('td.appt').filter({ has: link(id) });
  const button = schedule.locator('#toggleCancelledBtn');
  const shown = async id => (await cell(id).count()) === 1 && cell(id).isVisible();
  const reload = async () => {
    await schedule.reload({ waitUntil: 'domcontentloaded' });
    await link(seeded.booked).waitFor({ state: 'attached' });
  };

  try {
    await s.step(STEP.shown, async () => {
      for (const id of Object.values(seeded)) await link(id).waitFor({ state: 'attached' });
      h.assert(/\bCancelled\b/.test(await cell(seeded.cancelled).getAttribute('class')), 'The cancelled appointment is not greyed');
      h.assert(!/\bCancelled\b/.test(await cell(seeded.booked).getAttribute('class')), 'The booked appointment is greyed');
      h.assert(await shown(seeded.cancelled) && await shown(seeded.booked), 'The day sheet does not show both appointments');
      h.assert(await button.getAttribute('title') === await button.getAttribute('data-title-hide'),
        `The button offers "${await button.getAttribute('title')}", not Hide, before any press`);
    });

    await s.step(STEP.hide, async () => {
      await button.click();
      h.assert(!await shown(seeded.cancelled), 'Hide Cancelled left the cancelled appointment shown');
      h.assert(await shown(seeded.booked), 'Hide Cancelled hid the booked appointment');
      h.assert(await button.getAttribute('title') === await button.getAttribute('data-title-show'),
        `After Hide the button offers "${await button.getAttribute('title')}", not Show`);
    });

    await s.step(STEP.kept, async () => {
      await reload();
      h.assert(!await shown(seeded.cancelled), 'After a reload the cancelled appointment is shown again');
      h.assert(await shown(seeded.booked), 'After a reload the booked appointment is hidden');
      h.assert(await button.getAttribute('title') === await button.getAttribute('data-title-show'),
        'After a reload the button no longer offers Show');
    });

    await s.step(STEP.show, async () => {
      await button.click();
      h.assert(await shown(seeded.cancelled), 'Show Cancelled did not bring the cancelled appointment back');
      h.assert(await shown(seeded.booked), 'Show Cancelled hid the booked appointment');
      await reload();
      h.assert(await shown(seeded.cancelled) && await shown(seeded.booked), 'After Show and a reload an appointment is hidden');
      h.assert(await button.getAttribute('title') === await button.getAttribute('data-title-hide'),
        'After Show and a reload the button does not offer Hide');
    });
  } finally {
    // The flag lives in this check's own context, whose storage ends when it closes below; it is removed first all
    // the same.
    await schedule.evaluate(() => localStorage.removeItem('hideCancelled')).catch(() => {});
    await context.close().catch(() => {});
  }
}

if (require.main === module) runWorkflow('schedule-hide-cancelled', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, STEP };
