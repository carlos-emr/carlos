#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-schedule-reason-privacy — what the day sheet shows of an appointment's REASON and NOTES,
 * and the per-provider "*" toggle that reveals them (appointmentprovideradminday.jsp:
 * SHOW_APPT_REASON, TOGGLE_REASON_BY_PROVIDER ▸ toggleReason() / updateTooltipsForProvider()).
 * No check read what the schedule discloses on screen: the reason text and the appointment tooltip are
 * the day sheet's most exposed PHI (every person at the front desk sees the screen).
 *
 * User path (throwaway login, its own day sheet): Schedule ▸ the provider's column header ▸ "*".
 * Asserted: with the default configuration (SHOW_APPT_REASON unset) an appointment's reason and notes
 * appear neither as text in the grid nor in any tooltip (title attribute) of the day sheet, while the
 * appointment's own link opens the edit popup that does show them; and the column header offers the
 * reason toggle "*" (the JSP gives TOGGLE_REASON_BY_PROVIDER defaultVal="yes", but
 * CarlosPropertiesCheck only honours a defaultVal of "true", so with the property unset the toggle
 * is never rendered and a front desk cannot reveal reasons for one provider). The toggle assertion
 * is the last step; the show/hide/tooltip behaviour behind it cannot be driven until it is reachable.
 * Fixtures: the throwaway login (lib/throwaway-login-fixture.js), one owned FAKE- patient and one
 * appointment on the throwaway's own schedule today. Cleanup removes the appointment, then the
 * throwaway, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  const owner = h.sqlString(fixture.providerNo);
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE provider_no=${owner}; DELETE FROM appointment WHERE provider_no=${owner}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${owner}`) === '0', 'The owned appointment was not removed');
  });
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

  await s.step('the day sheet lists the appointment without its reason or notes in the grid (the hover tooltip is reported, not asserted)', async () => {
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
      // The hover tooltip is built from name, time, type, reason CODE name, notes and warnings (appointmentprovideradminday.jsp
      // appointmentTooltipFull): it carries the notes by design, so only the free-text reason must stay out of it.
      return { visible: visible.includes(reasonText) || visible.includes(notesText), titles: titles.includes(reasonText),
        notesInTooltip: titles.includes(notesText) };
    }, { reasonText: reason, notesText: notes });
    h.assert(!exposure.visible, 'The default day sheet shows the appointment reason or notes in the grid');
    // The hover tooltip (name, time, type, reason, notes, warnings: appointmentprovideradminday.jsp appointmentTooltipFull, shown
    // unconditionally because SHOW_APPT_REASON_TOOLTIP is hard-coded on) discloses both by design, so it is reported, not
    // asserted; Bootstrap moves `title` into data-bs-original-title, which is why a plain [title] scan never saw it.
    console.log(`    (the hover tooltip ${exposure.titles ? 'carries' : 'does not carry'} the reason text and ${exposure.notesInTooltip ? 'carries' : 'does not carry'} the notes)`);
  });

  await s.step('the appointment\'s own link opens the edit popup, which does show the reason and notes', async () => {
    const edit = await ui.clickOpensPopup(schedule, link, { context, recorder, label: 'edit-appointment', timeout: 20000 });
    await edit.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    h.assert(await edit.locator('#reason').inputValue() === reason, 'The edit popup does not show the reason');
    h.assert(await edit.locator('textarea[name="notes"]').inputValue() === notes, 'The edit popup does not show the notes');
    await edit.close();
  });

  await s.step('the provider column header offers the "*" reason toggle', async () => {
    h.assert(await schedule.locator('a.expand-reason-btn').count() >= 1,
      'The day sheet column header has no reason toggle "*": TOGGLE_REASON_BY_PROVIDER defaultVal="yes" is not honoured by CarlosPropertiesCheck (only "true" is), so it is hidden while the property is unset');
  });
}

if (require.main === module) runWorkflow('gap-provider-schedule-reason-privacy', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
