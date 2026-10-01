#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-preferences-save-all — coverage plan §2.2 (provider preferences): the main
 * Provider Preferences form saved with "Save All Preferences" (provider/ViewProviderUpdatePreference),
 * as a THROWAWAY login (lib/throwaway-login-fixture.js) so the shared test provider's schedule hours,
 * contact details and display settings never change under a parallel check.
 *
 * User path: Schedule ▸ Preferences icon ▸ Schedule & Appointments / Contact Information /
 * Prescriptions / Clinical Settings / Display & UI / Appointment Card sections ▸ Save All Preferences;
 * the window closes and reloads the day sheet. Quick links, signature, CPP and the default dx code
 * have their own checks (provider-quick-links, provider-signature-contact,
 * provider-preferences-cpp-dx); this one owns the rest of the form.
 *
 * Asserted: a start hour at or after the end hour is refused by the form's own alert with no request;
 * a server-side validation failure (negative link-name length) shows the failure page with the reason
 * and saves NOTHING (no ProviderPreference change and none of the property rows the same form carried);
 * the valid save writes ProviderPreference (start/end hour, period, link-name length) and the
 * property rows for every other section; the day sheet then renders 09:00..17:30 slots in 30-minute
 * steps, truncates the owned patient's appointment name to the configured length, and the week view
 * drops the weekend; reopening the
 * form shows every stored value; clearing the contact fields stores empty values; GET against the
 * save route is refused and writes nothing.
 * Fixtures: the throwaway login, one owned FAKE- patient and one appointment for the throwaway today.
 * Cleanup removes the appointment, the throwaway's property/ProviderPreference rows, then the throwaway.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');

// Closing save pages: Chromium reports the CSRFGuard script request of a self-closing page as ERR_ABORTED.
function consumeSelfCloseAbort(recorder, since) {
  const added = recorder.requestFailures.slice(since);
  const index = added.findIndex(entry => entry.resourceType === 'script' && entry.errorText === 'net::ERR_ABORTED'
    && /\/csrfguard$/.test(new URL(entry.url).pathname));
  if (index >= 0) recorder.requestFailures.splice(since + index, 1);
}

async function submitAndClose(recorder, page, locator) {
  const closed = page.waitForEvent('close', { timeout: 20000 });
  const since = recorder.requestFailures.length;
  await locator.click({ noWaitAfter: true });
  await closed;
  consumeSelfCloseAbort(recorder, since);
}

const VALUES = {
  rxAddress: '12 Pw Test Road', rxCity: 'Faketown', rxProvince: 'ON', rxPostal: 'K1A 0B1',
  rxPhone: '613-555-0142', faxnumber: '613-555-0143',
  rx_default_quantity: '28', patient_name_length: '10', encounterWindowWidth: '900', encounterWindowHeight: '700',
  quickChartSize: '120', appointmentCardName: 'FAKE Pw Clinic', appointmentCardPhone: '613-555-0144',
  appointmentCardFax: '613-555-0145', consultation_time_period_warning: '45',
};

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const today = new Date();
  const dateKey = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  fixture.create();
  const owner = h.sqlString(fixture.providerNo);
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointment WHERE provider_no=${owner} AND reason=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${owner}`) === '0', 'The owned appointment was not removed');
  });
  sql.execute(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,reason,status,createdatetime,creator)
    VALUES (${owner},${h.sqlString(dateKey)},'10:00:00','10:29:00',${h.sqlString(marker)},${patient},${h.sqlString(marker)},'t',NOW(),${h.sqlString(s.provider)})`);

  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const openPrefs = () => ui.clickOpensPopup(schedule, schedule.getByTitle(/Edit your personal setting/i).first(),
    { context, recorder, label: 'preferences', timeout: 20000 });
  const property = name => sql.value(`SELECT COALESCE(value,'') FROM property WHERE provider_no=${owner} AND name=${h.sqlString(name)}`);
  const hasProperty = name => sql.value(`SELECT COUNT(*) FROM property WHERE provider_no=${owner} AND name=${h.sqlString(name)}`) !== '0';
  const prefRow = () => sql.rows(`SELECT startHour,endHour,everyMin,appointmentScreenLinkNameDisplayLength FROM ProviderPreference WHERE providerNo=${owner}`)[0] || [];
  async function field(page, name) {
    const locator = page.locator(`form[name="UPDATEPRE"] [name="${name}"]`);
    await revealAuditLink(page, locator, 20000);
    return locator;
  }
  const save = page => page.locator('.footer-bar button[type="submit"]');
  const slotTimes = () => schedule.$$eval('a.adhour', as => as.map(a => a.textContent.trim()));

  // Week view (the "W" button in the provider's column header): the day names it lists.
  async function weekViewDays() {
    await Promise.all([schedule.waitForNavigation({ waitUntil: 'domcontentloaded' }), schedule.locator('input[name="weekview"]').first().click()]);
    await schedule.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const days = await schedule.$$eval('td.infirmaryView a, td.infirmaryView b', list =>
      [...new Set(list.map(el => el.textContent.trim().slice(0, 3)).filter(text => /^(Sun|Mon|Tue|Wed|Thu|Fri|Sat)$/.test(text)))]);
    await Promise.all([schedule.waitForNavigation({ waitUntil: 'domcontentloaded' }), schedule.goBack()]);
    await schedule.locator('a.adhour').first().waitFor({ state: 'attached' });
    return days;
  }

  let baseline;
  await s.step('the day sheet starts from the default 08:00-18:00 grid in 15-minute steps and week view lists all seven days', async () => {
    h.assert((await weekViewDays()).length === 7, 'The default week view does not list seven days');
    const slots = await slotTimes();
    h.assert(slots[0] === '08:00' && slots[1] === '08:15', `The default grid starts ${slots.slice(0, 2).join(',')}`);
    baseline = prefRow();
    h.assert(baseline[0] === '8' && baseline[1] === '18' && baseline[2] === '15', 'The login did not create the default schedule preference');
  });

  await s.step('a start hour after the end hour is refused by the form alert and nothing is posted', async () => {
    const prefs = await openPrefs();
    await (await field(prefs, 'start_hour')).fill('17');
    await (await field(prefs, 'end_hour')).fill('9');
    const posts = [];
    prefs.on('request', request => { if (request.method() === 'POST') posts.push(request.url()); });
    const dialogs = await h.withExpectedDialogs(prefs, () => save(prefs).click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', `Expected one validation alert, saw ${dialogs.length} dialog(s)`);
    h.assert(posts.length === 0 && JSON.stringify(prefRow()) === JSON.stringify(baseline), 'A refused schedule range still posted or saved');
    await prefs.close();
  });

  await s.step('a server-side validation failure shows the reason and saves nothing from the whole form', async () => {
    const prefs = await openPrefs();
    await (await field(prefs, 'rxCity')).fill('Atomictown');
    await (await field(prefs, 'patient_name_length')).fill('7');
    await (await field(prefs, 'appointmentScreenFormsNameDisplayLength')).fill('-1');
    await save(prefs).click();
    await prefs.waitForLoadState('domcontentloaded');
    const text = (await prefs.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(/Appointment screen link name display length must be zero or greater/.test(text),
      'The failure page does not say why the preferences were refused');
    h.assert(!prefs.isClosed(), 'The failure page closed itself, hiding the reason');
    h.assert(JSON.stringify(prefRow()) === JSON.stringify(baseline) && !hasProperty('rxCity') && !hasProperty('patient_name_length'),
      'A refused preferences save still wrote part of the form');
    await prefs.close();
  });

  await s.step('Save All writes the schedule range and every other section, and the window closes', async () => {
    const prefs = await openPrefs();
    await (await field(prefs, 'start_hour')).fill('9');
    await (await field(prefs, 'end_hour')).fill('17');
    await (await field(prefs, 'every_min')).fill('30');
    await (await field(prefs, 'appointmentScreenFormsNameDisplayLength')).fill('12');
    await (await field(prefs, 'schedule.week_view_weekends')).uncheck();
    for (const [name, value] of Object.entries(VALUES)) await (await field(prefs, name)).fill(value);
    await (await field(prefs, 'rx_page_size')).selectOption('PageSize.A4');
    await (await field(prefs, 'default_sex')).selectOption('F');
    await (await field(prefs, 'HC_Type')).selectOption('ON');
    await (await field(prefs, 'cpp_single_line')).check();
    await (await field(prefs, 'rx_show_patient_dob')).check();
    await (await field(prefs, 'lab_ack_comment')).check();
    await (await field(prefs, 'prescriptionQrCodes')).check();
    await submitAndClose(recorder, prefs, save(prefs));
    await expectValue(sql, `SELECT startHour FROM ProviderPreference WHERE providerNo=${owner}`, '9', 'The start hour did not reach ProviderPreference');
    h.assert(JSON.stringify(prefRow()) === JSON.stringify(['9', '17', '30', '12']), 'ProviderPreference does not hold the saved range, period and link length');
    for (const [name, value] of Object.entries(VALUES)) h.assert(property(name) === value, `Preference ${name} was not stored as entered`);
    h.assert(property('default_sex') === 'F' && property('HC_Type') === 'ON', 'The clinical defaults were not stored');
    h.assert(property('rx_page_size') === 'PageSize.A4', 'The Rx page size preference was not stored');
    h.assert(property('cpp_single_line') === 'yes' && property('rx_show_patient_dob') === 'yes' && property('lab_ack_comment') === 'yes',
      'The checkbox preferences were not stored');
    h.assert(sql.value(`SELECT printQrCodeOnPrescriptions FROM ProviderPreference WHERE providerNo=${owner}`) === '1', 'The Rx QR code preference was not stored');
  });

  await s.step('the day sheet reloads with 09:00-17:30 in 30-minute steps and the configured name length', async () => {
    await schedule.waitForFunction(() => document.querySelector('a.adhour')?.textContent.trim() === '09:00', null, { timeout: 20000 });
    const slots = await slotTimes();
    h.assert(slots[1] === '09:30' && slots[slots.length - 1] === '17:30', `The reloaded grid is ${slots[0]}..${slots[slots.length - 1]}`);
    const link = schedule.locator('a.apptLink').first();
    await link.waitFor({ state: 'attached' });
    const shown = (await link.innerText()).replace(/\s+/g, ' ').trim();
    // The day sheet title-cases the name, so compare case-insensitively.
    h.assert(shown.toLowerCase() === marker.slice(0, 10).toLowerCase(),
      `The appointment name shows as "${shown}", not the first 10 characters of the patient name`);
  });

  await s.step('week view now lists Monday to Friday only (the weekends switch is honoured)', async () => {
    const days = await weekViewDays();
    h.assert(days.length === 5 && !days.includes('Sat') && !days.includes('Sun'), `The week view still lists ${days.join(',')} with weekends switched off`);
  });

  await s.step('reopening the form shows every stored value', async () => {
    const prefs = await openPrefs();
    const value = async name => (await field(prefs, name)).inputValue();
    h.assert(await value('start_hour') === '9' && await value('end_hour') === '17' && await value('every_min') === '30'
      && await value('appointmentScreenFormsNameDisplayLength') === '12', 'The reopened form lost the schedule settings');
    for (const [name, expected] of Object.entries(VALUES)) h.assert(await value(name) === expected, `The reopened form lost ${name}`);
    h.assert(await value('default_sex') === 'F' && await value('HC_Type') === 'ON', 'The reopened form lost the clinical defaults');
    h.assert(await value('rx_page_size') === 'PageSize.A4', 'The reopened form lost the Rx page size');
    h.assert(!await (await field(prefs, 'schedule.week_view_weekends')).isChecked(), 'The weekends switch came back on');
    h.assert(await (await field(prefs, 'cpp_single_line')).isChecked() && await (await field(prefs, 'prescriptionQrCodes')).isChecked(),
      'The reopened form lost the checkbox settings');
    await prefs.close();
  });

  await s.step('clearing the contact fields and saving stores empty values', async () => {
    const prefs = await openPrefs();
    for (const name of ['rxAddress', 'rxCity', 'rxProvince', 'rxPostal', 'rxPhone', 'faxnumber']) await (await field(prefs, name)).fill('');
    await submitAndClose(recorder, prefs, save(prefs));
    await expectValue(sql, `SELECT COALESCE(value,'x') FROM property WHERE provider_no=${owner} AND name='rxAddress'`, '', 'Clearing rxAddress did not persist');
    h.assert(['rxCity', 'rxProvince', 'rxPostal', 'rxPhone', 'faxnumber'].every(name => property(name) === ''),
      'A cleared contact field kept its old value');
    h.assert(property('appointmentCardName') === 'FAKE Pw Clinic', 'Clearing the contact section changed the appointment card');
  });

  await s.step('GET against the save route is refused and writes nothing', async () => {
    const before = JSON.stringify([prefRow(), property('rxCity'), property('patient_name_length')]);
    const response = await context.request.get(`${config.baseUrl}/provider/ViewProviderUpdatePreference?start_hour=5&end_hour=6&every_min=5&rxCity=GetWrote&patient_name_length=3`);
    h.assert(response.status() === 405, `GET answered HTTP ${response.status()}, not 405`);
    h.assert(JSON.stringify([prefRow(), property('rxCity'), property('patient_name_length')]) === before, 'A GET changed the preferences');
  });
}

if (require.main === module) runWorkflow('gap-provider-preferences-save-all', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
