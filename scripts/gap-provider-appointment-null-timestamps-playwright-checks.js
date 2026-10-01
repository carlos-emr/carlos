#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-appointment-null-timestamps — opening an appointment that has no "last updated" (or
 * no "created") timestamp. appointment.createdatetime/updatedatetime are nullable, and appointments
 * that came from an import, a migration or any writer that does not stamp them have NULLs; the demo
 * dataset itself carries three such appointments.
 *
 * User path (throwaway login, its own day sheet): Schedule ▸ the appointment's own link ▸ edit popup
 * (appointment/editappointment). The popup shows Created and Last Updated dates, and
 * editappointment.jsp parses both with LocalDateTime.parse, which throws on the empty string a NULL
 * becomes.
 * Asserted, for each of two owned appointments (NULL updatedatetime; NULL createdatetime AND
 * updatedatetime): the link opens the edit popup without an error page, it shows the saved reason, and
 * it offers Update and Delete. The failures are collected and asserted together as the last step so
 * both shapes are reported.
 * Fixtures: the throwaway login (lib/gap-provider-fixture.js, a number owning no appointment rows), one owned FAKE- patient and the two
 * appointments, seeded by SQL (the shape other writers leave). Cleanup deletes the appointment and
 * archive rows, then the throwaway, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createUnbookedThrowaway, registerAppointmentCleanup } = require('./lib/gap-provider-fixture');

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  // createUnbookedThrowaway refuses a provider number that already owns appointment rows, so the
  // by-provider-number cleanup can only remove rows this run created.
  const fixture = createUnbookedThrowaway(s);
  registerAppointmentCleanup(s, fixture);
  const owner = h.sqlString(fixture.providerNo);
  const today = new Date();
  const dateKey = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  const shapes = [
    { label: 'NULL updatedatetime', start: '10:00:00', end: '10:14:00', created: 'NOW()', updated: 'NULL' },
    { label: 'NULL createdatetime and updatedatetime', start: '11:00:00', end: '11:14:00', created: 'NULL', updated: 'NULL' },
  ];
  for (const shape of shapes) {
    shape.reason = `${marker} ${shape.label}`;
    sql.execute(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,reason,status,createdatetime,updatedatetime,creator)
      VALUES (${owner},${h.sqlString(dateKey)},${h.sqlString(shape.start)},${h.sqlString(shape.end)},${h.sqlString(marker)},${patient},
        ${h.sqlString(shape.reason)},'t',${shape.created},${shape.updated},${h.sqlString(s.provider)})`);
  }
  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const defects = [];
  // The edit popup's HTTP status, seen from the moment the popup is created (a 500 is the defect reported below,
  // so it is consumed from the strict recorder after being asserted by this check, not ignored globally).
  const statuses = [];
  context.on('page', page => page.on('response', response => {
    if (/\/appointment\/editappointment$/.test(h.pathOnly(response.url()))) statuses.push(response.status());
  }));
  for (const shape of shapes) {
    await s.step(`the appointment with ${shape.label} is opened from its day-sheet link and the popup status recorded`, async () => {
      const id = sql.value(`SELECT appointment_no FROM appointment WHERE provider_no=${owner} AND reason=${h.sqlString(shape.reason)}`);
      const link = schedule.locator(`a[onclick*="/appointment/editappointment?appointment_no=${id}&"]`).first();
      await link.waitFor({ state: 'attached' });
      const since = { responses: recorder.badResponses.length, console: recorder.consoleIssues.length };
      statuses.length = 0;
      const popupPromise = context.waitForEvent('page', { timeout: 20000 });
      await link.click();
      const edit = await popupPromise;
      await edit.waitForLoadState('load', { timeout: 20000 }).catch(() => {});
      const deadline = Date.now() + 10000;
      while (!statuses.length && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
      if (statuses.includes(500)) {
        defects.push(`${shape.label}: editappointment answered HTTP 500 (editappointment.jsp:1183-1184 LocalDateTime.parse of the empty timestamp)`);
        const failed = recorder.badResponses.slice(since.responses);
        h.assert(failed.length === 1 && failed[0].status === 500 && /\/appointment\/editappointment$/.test(h.pathOnly(failed[0].url)),
          'The edit popup produced something other than its one HTTP 500');
        recorder.badResponses.splice(since.responses, 1);
        for (let i = recorder.consoleIssues.length - 1; i >= since.console; i--) {
          if (/status of 500/.test(recorder.consoleIssues[i].text || '')) recorder.consoleIssues.splice(i, 1);
        }
      } else {
        h.assert(await edit.locator('#reason').inputValue() === shape.reason, `The edit popup does not show the reason for ${shape.label}`);
        h.assert(await edit.locator('#updateButton').count() === 1 && await edit.locator('#deleteButton').count() === 1,
          `The edit popup for ${shape.label} offers no Update/Delete`);
      }
      await edit.close().catch(() => {});
    });
  }

  await s.step('every appointment opens, whatever timestamps its writer left', async () => {
    h.assert(defects.length === 0, defects.join('; '));
  });
}

if (require.main === module) runWorkflow('gap-provider-appointment-null-timestamps', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
