#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: Appointment add (day sheet slot > Add Appointment).
 *
 * User path: Schedule day sheet > empty slot link > patient keyword search > pick the owned
 * FAKE- patient > Add Appointment. Each rapid activation (dblclick(), slow re-click, two back-to-back
 * click({noWaitAfter}), double Enter on the focused Add Appointment button; Enter in a text field would hit the earlier Search button and #reason is a textarea) books ONE appointment (own marker reason,
 * own slot, 400 days ahead) and the check asserts EXACTLY ONE appointment row for the owned patient
 * and marker. addappointment.jsp has no disable-on-submit and no token; a repeat relies on the page
 * navigating away fast enough.
 *
 * Fixtures: the owned FAKE- patient; every appointment/appointmentArchive row with the run marker
 * for that patient is deleted and asserted gone. Wave-6 pattern sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer } = require('./lib/double-submit-helpers');

const DAYS_AHEAD = Number(process.env.APPOINTMENT_DAYS_AHEAD || '400');

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const like = h.sqlString(`${marker}-%`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no=${patient} AND reason LIKE ${like}`);
    sql.execute(`DELETE FROM appointment WHERE demographic_no=${patient} AND reason LIKE ${like}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient} AND reason LIKE ${like}`) === '0'
      && sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient} AND reason LIKE ${like}`) === '0',
    'Appointment cleanup left rows behind');
  });

  const target = new Date(Date.now() + DAYS_AHEAD * 86400000);
  const query = new URLSearchParams({
    year: String(target.getUTCFullYear()), month: String(target.getUTCMonth() + 1), day: String(target.getUTCDate()),
    view: '0', displaymode: 'day', dboperation: 'searchappointmentday', viewall: '1',
  });
  const v = verdicts('appointment-add');

  for (const [index, mode] of MODES.entries()) {
    await s.step(`Add Appointment via ${mode.label} books exactly one appointment`, async () => {
      await h.gotoApp(s.schedule, s.config.baseUrl, `/provider/providercontrol?${query}`);
      await s.schedule.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
      const slots = s.schedule.locator(`a.adhour[onclick*="provider_no=${provider}&"]`);
      h.assert(await slots.count() > index * 3, 'the day sheet rendered too few bookable slots');
      const popup = await s.popup(s.schedule, slots.nth(index * 3), 'add-appointment');
      await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
      await popup.locator('#keyword').fill(marker);
      await clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
      const pick = popup.locator(`table tr input[type="button"][name="pick_demographic"][value="${patient}"]`).first();
      await pick.waitFor({ state: 'visible', timeout: 30000 });
      await clickAndAwaitReload(popup, pick, { required: false });
      await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
      h.assert(await popup.locator('#demographic_no').inputValue() === patient, 'the booking form has another patient');
      await popup.locator('#reason').fill(`${marker}-${mode.tag} double submit`);
      const posts = watchPosts(popup.context(), /\/appointment\/AddRecord$/);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, /\/appointment\/AddRecord$/) : null;
      await rapid(mode.key, popup.locator('#addButton'), { textField: popup.locator('#addButton') });
      const count = await settledCount(sql,
        `SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient} AND reason LIKE ${h.sqlString(`${marker}-${mode.tag}%`)}`,
        { min: 1 });
      if (disarm) await disarm();
      posts.stop();
      console.log(`    (${posts.seen.length} AddRecord POST(s) sent)`);
      v.record(mode.label, count, { exactly: 1 });
      if (!popup.isClosed()) await popup.close().catch(() => {});
    });
  }
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-appointment', workflow, { openPatient: true });
