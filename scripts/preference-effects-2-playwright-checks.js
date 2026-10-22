#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * preference-effects-2: what three more Preferences settings change on the pages that read them.
 * gap-provider-preferences-save-all checks that Save All stores them; provider-preference-effects
 * follows the stale date and the navigation mode. This check follows Patient name length (the day
 * sheet), Default sex and HC type (the Create Demographic form).
 *
 * User path, as a THROWAWAY login (lib/throwaway-login-fixture.js) so that no seeded account's
 * preferences change:
 *   Schedule (the throwaway's own appointment today) and Schedule ▸ Search ▸ Create Demographic,
 *     first with none of the three set (the controls);
 *   Schedule ▸ Preferences ▸ Patient name length, Default sex, HC type ▸ Save All;
 *   the same two pages again.
 *
 * Asserted, in this order:
 * - controls: the day sheet does not show the appointment's name cut to 18 characters (whatever its
 *   default length is: 11, 20, 25 or 30), and the add form preselects neither the sex nor the HC type
 *   this check is about to save;
 * - Save All stores patient_name_length=18 and the chosen default_sex and HC_Type;
 * - the day sheet shows exactly the first 18 characters of the appointment's 23-character name;
 * - Create Demographic preselects the saved sex and HC type.
 *
 * Fixtures: a throwaway login, the session's owned FAKE patient and one appointment today on the
 * throwaway's own schedule, named with the run marker. Cleanup removes the appointment, then the
 * throwaway (its property rows go with it); both are asserted gone. The add form is never submitted.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { submitAndClose } = require('./gap-provider-preferences-save-all-playwright-checks');

const TIMEOUT = 30000;
const NAME_LENGTH = 18;

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
  h.assert(marker.length === 23, 'The run marker is not the 23-character FAKE-PW<hex> this check measures');
  const appointmentNo = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,
      reason,status,createdatetime,creator)
    VALUES (${owner},CURDATE(),'10:00:00','10:29:00',${h.sqlString(marker)},${patient},${h.sqlString(marker)},'t',NOW(),
      ${h.sqlString(s.provider)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(appointmentNo), 'The owned appointment was not created');
  const property = name => sql.value(`SELECT COALESCE(MAX(value),'') FROM property WHERE provider_no=${owner} AND name=${h.sqlString(name)}`);

  const context = await h.newContext(s.context.browser(), config);
  s.cleanup(() => context.close().catch(() => {}));
  context.setDefaultTimeout(TIMEOUT);
  context.on('page', page => h.wireStrictPage(page, 'preference-effects-2', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });

  /** The day sheet's text for the owned appointment's link. */
  async function shownName() {
    const link = schedule.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}&"], a.apptLink[onclick*="appointment_no=${appointmentNo}'"]`).first();
    await link.waitFor({ timeout: TIMEOUT });
    // The day sheet re-cases names (FAKE-PW… shows as Fake-pw…), so compare without case.
    return (await link.innerText()).replace(/\s+/g, ' ').trim().toLowerCase();
  }
  const cut = length => marker.slice(0, length).toLowerCase();
  /** Whether the shown name is exactly the first `length` characters of the marker. */
  const cutTo = (name, length) => name.includes(cut(length)) && !name.includes(cut(length + 1));
  /** Schedule ▸ Search ▸ a no-match search ▸ Create Demographic: the sex and HC type it preselects. */
  async function addFormDefaults() {
    const search = await ui.clickOpensPopup(schedule, schedule.locator('#search a, a:has-text("Search")').first(),
      { context, recorder, label: 'add-search', timeout: TIMEOUT });
    await search.locator('#keyword, input[name="keyword"]').first().fill(marker);
    await search.locator("input[type='submit']").first().click();
    await search.waitForLoadState('networkidle').catch(() => {});
    await search.locator("form[action$='/demographic/ViewDemographicAddARecordHtm'] button[type='submit']").first().click();
    await search.locator('form[name="adddemographic"]').waitFor({ timeout: TIMEOUT });
    await h.assertNotErrorPage(search, 'create demographic');
    const defaults = { sex: await search.locator('#sex').inputValue(), hcType: await search.locator('#hc_type').inputValue() };
    await search.close();
    return defaults;
  }

  let control;
  let sex;
  let hcType;
  await s.step('controls: before any of the three is set, the day sheet and the add form show their defaults', async () => {
    const name = await shownName();
    h.assert(name.includes(cut(7)), `The day sheet does not show the owned appointment's name: ${JSON.stringify(name)}`);
    h.assert(!cutTo(name, NAME_LENGTH),
      `The day sheet already cuts the name to ${NAME_LENGTH} characters, so the setting could not be told apart`);
    control = await addFormDefaults();
    sex = control.sex === 'F' ? 'M' : 'F';
    // An untouched BC install legitimately defaults to BC. Pick a different
    // supported type so the saved preference is observable in every province.
    hcType = control.hcType === 'BC' ? 'ON' : 'BC';
  });

  await s.step('Preferences ▸ Save All stores the patient name length, default sex and HC type', async () => {
    const prefs = await ui.clickOpensPopup(schedule, schedule.getByTitle(/Edit your personal setting/i).first(),
      { context, recorder, label: 'preferences', timeout: TIMEOUT });
    const reloadsSchedule = await prefs.evaluate(() => window.opener !== null);
    const field = async name => {
      const locator = prefs.locator(`form[name="UPDATEPRE"] [name="${name}"]`);
      await revealAuditLink(prefs, locator, TIMEOUT);
      return locator;
    };
    await (await field('patient_name_length')).fill(String(NAME_LENGTH));
    await (await field('default_sex')).selectOption(sex);
    await (await field('HC_Type')).selectOption(hcType);
    await schedule.evaluate(() => { window.__beforePreferenceSave = true; });
    await submitAndClose(recorder, prefs, prefs.locator('.footer-bar button[type="submit"]'));
    if (reloadsSchedule) {
      await schedule.waitForFunction(() => !window.__beforePreferenceSave && document.readyState === 'complete', null, { timeout: TIMEOUT });
    }
    const stored = { patient_name_length: property('patient_name_length'), default_sex: property('default_sex'), HC_Type: property('HC_Type') };
    h.assert(stored.patient_name_length === String(NAME_LENGTH) && stored.default_sex === sex && stored.HC_Type === hcType,
      `Save All stored ${JSON.stringify(stored)}`);
  });

  await s.step(`the day sheet shows the first ${NAME_LENGTH} characters of the appointment's name`, async () => {
    await schedule.reload({ waitUntil: 'domcontentloaded' });
    const name = await shownName();
    h.assert(cutTo(name, NAME_LENGTH),
      `The day sheet shows ${JSON.stringify(name)} for the ${marker.length}-character name, not its first ${NAME_LENGTH} characters`);
  });

  await s.step('Create Demographic preselects the saved default sex and HC type', async () => {
    const after = await addFormDefaults();
    h.assert(after.sex === sex && after.hcType === hcType,
      `The add form preselects sex ${JSON.stringify(after.sex)} and HC type ${JSON.stringify(after.hcType)}, not ${sex} and ${hcType} `
        + `(before: ${JSON.stringify(control)})`);
  });
}

// The owned FAKE patient is the appointment's; both pages are reached from the schedule.
if (require.main === module) runWorkflow('preference-effects-2', workflow, { openMaster: false });
module.exports = { workflow };
