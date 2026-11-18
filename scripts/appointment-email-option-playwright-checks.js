#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

// The installed appointment-options profile enables emailApptReminder.
async function assertReminderUnavailable(page) {
  h.assert(await page.locator('input[name="emailPt"]').count() === 0,
    'The form offered an appointment email reminder without delivery support');
}

async function workflow(s) {
  process.env.APPOINTMENT_DEMOGRAPHIC_NO = s.patient;
  process.env.APPOINTMENT_PROVIDER_NO = s.provider;
  const fixture = require('./appointment-lifecycle-playwright-checks');
  fixture.initMysqlDefaults();
  s.cleanup(() => {
    try {
      fixture.cleanupRows();
    } finally {
      fixture.cleanupMysqlDefaults();
    }
  });
  s.sql.execute(`UPDATE demographic SET email=${h.sqlString(s.marker + '@carlos.invalid')} WHERE demographic_no=${s.patient}`);
  let booking;

  await s.step('Add omits the unsupported reminder and books the appointment', async () => {
    booking = await fixture.bookFromSlot(s.context, s.schedule, {
      slotIndex: 2,
      configureAdd: assertReminderUnavailable,
    });
  });

  await s.step('closing Edit leaves the appointment unchanged', async () => {
    const before = fixture.stampedAppointments();
    const editor = await fixture.openEditPopup(s.context, s.schedule, booking.id);
    await assertReminderUnavailable(editor);
    await editor.locator('textarea[name="notes"]').fill(fixture.stamp + ' unsaved notes');
    await editor.close();
    h.assert(JSON.stringify(fixture.stampedAppointments()) === JSON.stringify(before),
      'Closing Edit changed the appointment');
  });

  await s.step('Edit omits the unsupported reminder and saves the existing appointment', async () => {
    const editor = await fixture.openEditPopup(s.context, s.schedule, booking.id);
    await assertReminderUnavailable(editor);
    const notes = fixture.stamp + ' saved notes';
    await editor.locator('textarea[name="notes"]').fill(notes);
    const [response] = await Promise.all([
      editor.waitForResponse(reply => reply.request().method() === 'POST'
        && new URL(reply.url()).pathname.endsWith('/appointment/UpdateRecord')),
      editor.locator('#updateButton').click(),
    ]);
    h.assert(response.ok(), `Appointment edit returned HTTP ${response.status()}`);
    const posted = new URLSearchParams(response.request().postData() || '');
    h.assert(!posted.has('emailPt'), 'Edit submitted a reminder choice');
    const [stored, ...other] = fixture.stampedAppointments();
    h.assert(other.length === 0 && stored?.id === booking.id && stored.notes === notes,
      'Editing did not update the one owned appointment');
    await editor.close();
  });
}

if (require.main === module) {
  runWorkflow('appointment-email-option', workflow, { openPatient: true, openMaster: false });
}
module.exports = { workflow };
