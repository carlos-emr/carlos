#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * appointment-card-preferences: what Preferences ▸ Appointment Card ("Info printed on appointment
 * reminder cards for patients") changes on the card itself. gap-provider-preferences-save-all
 * checks that Save All stores the three values; nothing followed them to the card.
 *
 * User path, as a THROWAWAY login (lib/throwaway-login-fixture.js) so that the shared test
 * provider's preferences never change:
 *   Schedule ▸ Preferences ▸ Appointment Card ▸ Clinic Name, Phone, Fax ▸ Save All;
 *   Schedule ▸ the throwaway's own appointment ▸ Edit popup ▸ Print Card
 *     (appointment/appointmentviewrecordcard).
 *
 * Asserted, in this order:
 * - Save All stores the three values as the throwaway's appointmentCardName/Phone/Fax properties;
 * - Print Card opens the card for the owned appointment and prints itself, and the clinic's own
 *   phone and fax differ from the saved ones (so the last step can tell them apart);
 * - last, because it fails today: the card shows the saved clinic name, phone and fax. The card
 *   (appointmentviewrecordcard.jsp) reads APPT_CARD_NAME/PHONE/FAX, which nothing writes, so it
 *   shows the provider's own name and the clinic's phone and fax. Expected to FAIL at that step
 *   while app-findings-log.md finding 315 stands.
 *
 * Fixtures: a throwaway login, the session's owned FAKE patient and one appointment today on the
 * throwaway's own schedule. Cleanup removes the appointment, then the throwaway (its property
 * rows go with it); both are asserted gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { submitAndClose } = require('./gap-provider-preferences-save-all-playwright-checks');

const TIMEOUT = 20000;

/** The step the manifest's expectedFailure names; one place, so the script and the manifest agree. */
const STEP = {
  card: 'the appointment card shows the clinic name, phone and fax saved in Preferences',
};

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  const owner = h.sqlString(fixture.providerNo);
  // Runs before the throwaway's own cleanup (cleanups run in reverse order).
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointment WHERE provider_no=${owner} AND demographic_no=${patient} AND reason=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${owner}`) === '0', 'The owned appointment was not removed');
  });
  const today = sql.value('SELECT CURDATE()');
  const appointmentNo = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,
      reason,status,createdatetime,creator)
    VALUES (${owner},${h.sqlString(today)},'10:00:00','10:29:00',${h.sqlString(marker)},${patient},${h.sqlString(marker)},'t',NOW(),
      ${h.sqlString(s.provider)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(appointmentNo), 'The owned appointment was not created');

  // FAKE card values, unlike anything the clinic row holds (checked before the card is read).
  const saved = {
    appointmentCardName: `FAKE Card Clinic ${marker.slice(-6)}`,
    appointmentCardPhone: '613-555-0171',
    appointmentCardFax: '613-555-0172',
  };
  const property = name => sql.value(`SELECT COALESCE(MAX(value),'') FROM property WHERE provider_no=${owner} AND name=${h.sqlString(name)}`);

  const context = await h.newContext(s.context.browser(), config);
  s.cleanup(() => context.close().catch(() => {}));
  context.setDefaultTimeout(TIMEOUT);
  context.on('page', page => h.wireStrictPage(page, 'appointment-card', recorder));
  // The card prints itself on load; record the call instead of opening a print dialog.
  await context.addInitScript(() => {
    window.print = () => { window.__carlosPrintCalls = (window.__carlosPrintCalls || 0) + 1; };
  });
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });

  await s.step('Preferences ▸ Appointment Card ▸ Save All stores the clinic name, phone and fax', async () => {
    const prefs = await ui.clickOpensPopup(schedule, schedule.getByTitle(/Edit your personal setting/i).first(),
      { context, recorder, label: 'preferences', timeout: TIMEOUT });
    const reloadsSchedule = await prefs.evaluate(() => window.opener !== null);
    for (const [name, value] of Object.entries(saved)) {
      const input = prefs.locator(`form[name="UPDATEPRE"] input[name="${name}"]`);
      await revealAuditLink(prefs, input, TIMEOUT);
      await input.fill(value);
    }
    await schedule.evaluate(() => { window.__beforePreferenceSave = true; });
    await submitAndClose(recorder, prefs, prefs.locator('.footer-bar button[type="submit"]'));
    if (reloadsSchedule) {
      await schedule.waitForFunction(() => !window.__beforePreferenceSave && document.readyState === 'complete', null, { timeout: TIMEOUT });
    }
    for (const [name, value] of Object.entries(saved)) {
      h.assert(property(name) === value, `Save All stored ${JSON.stringify(property(name))} as ${name}, not ${JSON.stringify(value)}`);
    }
  });

  let card = '';
  await s.step('Edit Appointment ▸ Print Card opens the card of the owned appointment and prints it', async () => {
    const [[clinicPhone, clinicFax]] = sql.rows('SELECT COALESCE(clinic_phone,\'\'),COALESCE(clinic_fax,\'\') FROM clinic ORDER BY clinic_no LIMIT 1');
    h.assert(clinicPhone !== saved.appointmentCardPhone && clinicFax !== saved.appointmentCardFax,
      'The clinic row already holds the FAKE card phone or fax, so the card could not tell them apart');
    const link = schedule.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}&"], a.apptLink[onclick*="appointment_no=${appointmentNo}'"]`).first();
    await link.waitFor({ timeout: TIMEOUT });
    const popup = await ui.clickOpensPopup(schedule, link, { context, recorder, label: 'edit-appointment', timeout: TIMEOUT });
    await popup.waitForLoadState('domcontentloaded');
    await popup.locator('#updateButton').waitFor({ timeout: TIMEOUT });
    h.assert(await popup.locator('input[name="appointment_no"]').inputValue() === String(appointmentNo),
      'The edit popup opened a different appointment');
    await Promise.all([popup.waitForURL(/\/appointment\/appointmentviewrecordcard/), popup.locator('a.btn:has(i.fa-print)').first().click()]);
    await popup.waitForLoadState('load');
    await h.assertNotErrorPage(popup, 'appointment card');
    card = (await popup.locator('body').innerText()).replace(/\s+/g, ' ').trim();
    h.assert(card.includes(`Throwaway ${marker}`) || card.includes(saved.appointmentCardName),
      'The card names neither the appointment provider nor the saved clinic name');
    h.assert(await popup.evaluate(() => window.__carlosPrintCalls || 0) >= 1, 'The card did not open the print dialog on load');
    await popup.close();
  });

  await s.step(STEP.card, async () => {
    const missing = Object.entries(saved).filter(([, value]) => !card.includes(value)).map(([name]) => name);
    h.assert(missing.length === 0, `The card leaves out the saved ${missing.join(', ')}: it reads ${JSON.stringify(card.slice(0, 400))}`);
  });
}

// The owned FAKE patient is the appointment's; the card is reached from the schedule, not the record.
if (require.main === module) runWorkflow('appointment-card-preferences', workflow, { openMaster: false });
module.exports = { workflow, STEP };
