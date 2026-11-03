#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-schedule-reason-privacy — what the day sheet shows of an appointment's REASON and NOTES,
 * and the per-provider "*" toggle that reveals them (appointmentprovideradminday.jsp:
 * SHOW_APPT_REASON, TOGGLE_REASON_BY_PROVIDER ▸ toggleReason() / updateTooltipsForProvider()).
 * User path (throwaway login, its own day sheet): Schedule ▸ the provider's column header ▸ "*".
 * Asserted: with the default configuration (SHOW_APPT_REASON unset) an appointment's reason and notes
 * stay out of the grid, but the appointment's hover tooltip and edit popup show both. The tooltip
 * behavior is intentional: the owner confirmed it in #5214 (closed not_planned), so #5576 retires the
 * former disclosure assertion. The column-header reason toggle must still reveal and hide the
 * reason without changing the tooltip or the stored appointment details.
 * Fixtures: the throwaway login (lib/gap-provider-fixture.js, a number owning no appointment rows), one owned FAKE- patient and one
 * appointment on the throwaway's own schedule today. Cleanup removes the appointment, then the
 * throwaway, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { createUnbookedThrowaway, registerAppointmentCleanup } = require('./lib/gap-provider-fixture');

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  // createUnbookedThrowaway refuses a provider number that already owns appointment rows, so the
  // by-provider-number cleanup can only remove rows this run created.
  const fixture = createUnbookedThrowaway(s);
  registerAppointmentCleanup(s, fixture);
  const owner = h.sqlString(fixture.providerNo);
  const reason = `${marker} SECRETREASON`;
  const notes = `${marker} SECRETNOTES`;
  const today = new Date();
  const dateKey = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  // updatedatetime is set: an appointment without one makes the edit popup answer HTTP 500 (see
  // gap-provider-appointment-null-timestamps), which is not what this check is about.
  sql.execute(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,reason,notes,status,createdatetime,updatedatetime,creator,lastupdateuser)
    VALUES (${owner},${h.sqlString(dateKey)},'10:00:00','10:14:00',${h.sqlString(marker)},${patient},${h.sqlString(reason)},${h.sqlString(notes)},'t',NOW(),NOW(),${h.sqlString(s.provider)},${h.sqlString(s.provider)})`);
  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const link = schedule.locator('a.apptLink').first();
  await s.step('the day sheet hides reason and notes in the grid and shows both in the hover tooltip', async () => {
    await link.waitFor({ state: 'attached' });
    const exposure = await schedule.evaluate(({ reasonText, notesText }) => {
      const visible = [...document.querySelectorAll('body *')].filter(el => el.children.length === 0
        && el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden')
        .map(el => el.textContent || '').join(' ');
      // Bootstrap tooltips move `title` into data-bs-original-title (and drop the attribute), so scan both, plus any tooltip text
      // already rendered into the page.
      const titles = [...document.querySelectorAll('[title], [data-bs-original-title], [data-bs-title], .tooltip')]
        .map(el => [el.getAttribute('title'), el.getAttribute('data-bs-original-title'), el.getAttribute('data-bs-title'),
          el.classList.contains('tooltip') ? el.textContent : ''].filter(Boolean).join(' ')).join(' ');
      // #5214: the appointment's free-text reason and notes belong in the tooltip.
      return { visible: visible.includes(reasonText) || visible.includes(notesText), titles: titles.includes(reasonText),
        notesInTooltip: titles.includes(notesText) };
    }, { reasonText: reason, notesText: notes });
    h.assert(!exposure.visible, 'The default day sheet shows the appointment reason or notes in the grid');
    h.assert(exposure.titles, 'The hover tooltip lost the appointment\'s free-text reason');
    h.assert(exposure.notesInTooltip, 'The hover tooltip lost the appointment\'s notes');
  });

  await s.step('the appointment\'s own link opens the edit popup, which does show the reason and notes', async () => {
    const edit = await ui.clickOpensPopup(schedule, link, { context, recorder, label: 'edit-appointment', timeout: 20000 });
    await edit.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    h.assert(await edit.locator('#reason').inputValue() === reason, 'The edit popup does not show the reason');
    h.assert(await edit.locator('textarea[name="notes"]').inputValue() === notes, 'The edit popup does not show the notes');
    await edit.close();
  });

  await s.step('the default provider toggle reveals and hides its appointment reason', async () => {
    const toggle = schedule.locator(`a.expand-reason-btn[data-provider="${fixture.providerNo}"]`);
    h.assert(await toggle.count() === 1, 'The default provider reason toggle is missing');
    const reasonText = schedule.locator(`.reason_${fixture.providerNo}`).filter({ hasText: reason }).first();
    await toggle.click();
    h.assert(await reasonText.isVisible(), 'The toggle did not reveal the reason');
    await toggle.click();
    h.assert(!await reasonText.isVisible(), 'The toggle did not hide the reason again');
  });

}

if (require.main === module) runWorkflow('gap-provider-schedule-reason-privacy', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
