#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * provider-preferences-cpp-dx — coverage plan §2.2 (provider preferences: CPP display, default
 * billing dx code, saved tickler view).
 *
 * User paths, all as a THROWAWAY login (lib/throwaway-login-fixture.js) so the shared test
 * provider's chart layout, billing defaults and views never change under a parallel check
 * (nothing on the test provider is touched, so there is nothing of theirs to snapshot):
 *   Schedule ▸ Preferences ▸ Account & Advanced ▸ Configure eChart CPP (provider/CppPreferences)
 *     ▸ duplicate positions refused by the form's alert ▸ Save ▸ Search ▸ Master Record ▸ E-Chart;
 *   Schedule ▸ Preferences ▸ Default billing dx code ▸ Save All ▸ Master Record ▸ Create Invoice;
 *   Schedule ▸ Tickler ▸ filter ▸ Save View (saveWorkView) ▸ reopen.
 * Asserted: each save reaches `property` / `ProviderPreference` / `view` (and the SAVE_CUSTOM_CPP
 * audit row), each reopen shows it, and its consumer honours it: the chart's Allergies items carry
 * the start date and severity, the bill form preselects the dx code, the tickler list reopens on
 * the saved filter, and the chart drops the section set to Hide. GET against both mutators is refused.
 * Fixtures: the throwaway login, one FAKE- allergy on the owned patient. Cleanup deletes the
 * allergy, the throwaway's ProviderPreference/view rows and the throwaway (property, roles, log).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const PREF_TABLES = ['ProviderPreferenceAppointmentScreenForm', 'ProviderPreferenceAppointmentScreenEForm',
  'ProviderPreferenceAppointmentScreenQuickLink', 'ProviderPreference'];

// A save page that carries self.close() in its <head> closes while the CSRFGuard script tag
// injected after it is still loading; Chromium reports that one request as net::ERR_ABORTED.
// Consume exactly that entry for the closing page, after the close was observed.
function consumeSelfCloseAbort(recorder, since) {
  const added = recorder.requestFailures.slice(since);
  const index = added.findIndex(entry => entry.resourceType === 'script' && entry.errorText === 'net::ERR_ABORTED'
    && /\/csrfguard$/.test(new URL(entry.url).pathname));
  if (index >= 0) recorder.requestFailures.splice(since + index, 1);
}

/** Click a submit whose result page closes the window, and wait for the close. */
async function submitAndClose(recorder, page, locator) {
  const closed = page.waitForEvent('close', { timeout: 20000 });
  const since = recorder.requestFailures.length;
  await locator.click({ noWaitAfter: true });
  await closed;
  consumeSelfCloseAbort(recorder, since);
}

async function workflow(s) {
  const { sql, config, recorder, patient, marker } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const allergy = `${marker} peanut`;
  s.cleanup(() => {
    const owner = h.sqlString(fixture.providerNo || '');
    sql.execute(`DELETE FROM allergies WHERE demographic_no=${patient} AND DESCRIPTION=${h.sqlString(allergy)};
      DELETE FROM view WHERE providerNo=${owner};
      ${PREF_TABLES.map(table => `DELETE FROM ${table} WHERE providerNo=${owner}`).join(';')}`);
    const left = [`(SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})`,
      `(SELECT COUNT(*) FROM view WHERE providerNo=${owner})`,
      ...PREF_TABLES.map(table => `(SELECT COUNT(*) FROM ${table} WHERE providerNo=${owner})`)];
    h.assert(sql.value(`SELECT ${left.join('+')}`) === '0', 'Owned allergy/preference/view rows were not removed');
  });
  fixture.create();
  const owner = h.sqlString(fixture.providerNo);
  const dxCode = '401';
  h.assert(sql.value(`SELECT COUNT(*) FROM diagnosticcode WHERE diagnostic_code=${h.sqlString(dxCode)}`) !== '0',
    'The Ontario diagnostic code table has no 401 to make the default');
  sql.execute(`INSERT INTO allergies (demographic_no,entry_date,DESCRIPTION,TYPECODE,archived,start_date,severity_of_reaction,
      position,lastUpdateDate,providerNo,nonDrug)
    VALUES (${patient},CURDATE(),${h.sqlString(allergy)},0,0,'2020-03-04','3',0,NOW(),${owner},1)`);

  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const popup = (page, locator, label) => ui.clickOpensPopup(page, locator, { context, recorder, label, timeout: 20000 });
  const openPrefs = () => popup(schedule, schedule.getByTitle(/Edit your personal setting/i).first(), 'preferences');
  const property = name => sql.value(`SELECT COALESCE(value,'') FROM property WHERE provider_no=${owner} AND name=${h.sqlString(name)}`);
  const cppRows = () => sql.value(`SELECT COUNT(*) FROM property WHERE provider_no=${owner} AND name LIKE 'cpp.%'`);
  async function openCpp(label) {
    const prefs = await openPrefs();
    const link = prefs.locator('a[href$="/provider/CppPreferences"]');
    await revealAuditLink(prefs, link, 20000);
    const cpp = await popup(prefs, link, label);
    await cpp.locator('input[name="cpp.pref.enable"]').waitFor({ state: 'visible' });
    return { prefs, cpp };
  }

  await s.step('CPP preferences refuse two sections in one position with the form alert and write nothing', async () => {
    const { prefs, cpp } = await openCpp('cpp-duplicate');
    await cpp.locator('select[name="cpp.medical_hx.position"]').selectOption('R1I1');
    const dialogs = await h.withExpectedDialogs(cpp, () => cpp.locator('input[type="submit"]').click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && /duplicate for Row 1, Column 1/.test(dialogs[0].text),
      `Expected one duplicate-position alert, saw ${dialogs.length} dialog(s)`);
    await cpp.waitForLoadState('networkidle').catch(() => {});
    h.assert(!cpp.isClosed() && cppRows() === '0', 'A refused CPP form still saved preferences');
    await cpp.close();
    await prefs.close();
  });

  await s.step('CPP preferences save the custom chart, Preventions hidden, allergy start date and severity', async () => {
    const { prefs, cpp } = await openCpp('cpp-save');
    await cpp.locator('input[name="cpp.pref.enable"]').check();
    await cpp.locator('select[name="cpp.preventions.display"]').selectOption('');
    await cpp.locator('input[name="cpp.allergy.start_date"]').check();
    await cpp.locator('input[name="cpp.allergy.severity"]').check();
    await submitAndClose(recorder, cpp, cpp.locator('input[type="submit"]'));
    await expectValue(sql, `SELECT COALESCE(value,'') FROM property WHERE provider_no=${owner} AND name='cpp.pref.enable'`, 'on',
      'The custom-chart switch was not saved');
    h.assert(property('cpp.preventions.display') === '' && property('cpp.allergies.display') === 'SHOW'
      && property('cpp.allergy.start_date') === 'on' && property('cpp.allergy.severity') === 'on'
      && property('cpp.social_hx.position') === 'R1I1', 'The CPP choices were not stored as selected');
    h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE provider_no=${owner} AND action='SAVE_CUSTOM_CPP'`) === '1',
      'The CPP save wrote no SAVE_CUSTOM_CPP audit row');
    await prefs.close();
  });

  await s.step('reopening CPP preferences shows the stored choices', async () => {
    const { prefs, cpp } = await openCpp('cpp-reopen');
    h.assert(await cpp.locator('input[name="cpp.pref.enable"]').isChecked()
      && await cpp.locator('input[name="cpp.allergy.start_date"]').isChecked()
      && await cpp.locator('input[name="cpp.allergy.severity"]').isChecked()
      && await cpp.locator('select[name="cpp.preventions.display"]').inputValue() === '',
    'The reopened CPP form did not show the stored choices');
    await cpp.close();
    await prefs.close();
  });

  const { masterPage: master } = await openMasterRecord(context, schedule, recorder,
    { searchTerm: marker, preferredDemographicNo: patient, timeout: 20000 });
  let chart;
  await s.step('the chart Allergies box shows the allergy with its start date and severity', async () => {
    chart = await openChart(context, master, recorder, 20000);
    await waitForNavbars(chart, 20000);
    const item = chart.locator('#leftNavBar a, #rightNavBar a').filter({ hasText: allergy }).first();
    await item.waitFor({ state: 'attached' });
    const text = (await item.innerText()).replace(/\s+/g, ' ');
    h.assert(/Start Date:\s*\S*2020/.test(text) && /Severity:\s*Severe/.test(text),
      'The Allergies box ignored the start-date/severity preference');
  });

  await s.step('the chart honours the CPP "Hide" choice: Preventions is gone while Allergies stays', async () => {
    const heading = chart.locator('#leftNavBar, #rightNavBar').locator('h3, a, div').filter({ hasText: /^\s*Preventions\s*$/ });
    h.assert(await chart.locator('#allergies').count() === 1, 'The shown Allergies section is missing: the hide probe would be vacuous');
    h.assert(await chart.locator('#preventions').count() === 0 && await heading.count() === 0,
      'Configure eChart CPP saved Preventions = Hide with the custom chart enabled, but the chart still renders Preventions');
  });

  const viewValue = name => sql.value(`SELECT COALESCE(value,'') FROM view WHERE providerNo=${owner}
    AND view_name='tickler' AND name=${h.sqlString(name)} ORDER BY id LIMIT 1`);
  let creator;
  // The Tickler link opens a window or, in the focused navigation mode, replaces the schedule.
  async function withTicklers(label, body) {
    const opened = await ui.clickOpensPopupOrNavigates(schedule, schedule.locator('a:has(#oscar_new_tickler)').first(),
      { context, recorder, label, timeout: 20000 });
    h.assert(h.pathOnly(opened.page.url()).endsWith('/tickler/ViewTicklerMain'), 'The schedule Tickler link did not open the tickler list');
    await body(opened.page);
    if (opened.isPopup) await opened.page.close();
    else {
      await schedule.goBack({ waitUntil: 'domcontentloaded' });
      await schedule.getByTitle(/Edit your personal setting/i).first().waitFor({ state: 'attached' });
    }
  }
  await s.step('Tickler ▸ Save View stores the chosen status and creator filters for this provider', async () => {
    await withTicklers('tickler-main', async ticklers => {
      await ticklers.locator('#ticklerview').selectOption('C');
      creator = await ticklers.locator('#providerview option:not([value="all"])').first().getAttribute('value');
      h.assert(creator, 'The tickler creator filter offers no provider to choose');
      await ticklers.locator('#providerview').selectOption(creator);
      const [saved] = await Promise.all([
        ticklers.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/saveWorkView')),
        ticklers.locator('#saveViewButton').click(),
      ]);
      h.assert(saved.status() === 200, `Save View answered HTTP ${saved.status()}`);
      await ticklers.locator('#saveViewButton.btn-success').waitFor({ state: 'attached' });
      await expectValue(sql, `SELECT COALESCE(value,'') FROM view WHERE providerNo=${owner} AND view_name='tickler'
        AND name='ticklerview' ORDER BY id LIMIT 1`, 'C', 'The tickler status filter was not saved');
      h.assert(viewValue('providerview') === creator, 'The tickler creator filter was not saved');
    });
  });

  await s.step('reopening the tickler list starts from the saved view', async () => {
    await withTicklers('tickler-main-reopen', async ticklers => {
      h.assert(await ticklers.locator('#ticklerview').inputValue() === 'C'
        && await ticklers.locator('#providerview').inputValue() === creator,
      'The reopened tickler list ignored the saved view');
    });
  });

  await s.step('Preferences ▸ default billing dx code ▸ Save All stores it on ProviderPreference', async () => {
    const prefs = await openPrefs();
    const field = prefs.locator('#dxCode');
    await revealAuditLink(prefs, field, 20000);
    await field.fill(dxCode);
    await submitAndClose(recorder, prefs, prefs.locator('.footer-bar button[type="submit"]'));
    await expectValue(sql, `SELECT COALESCE(defaultDxCode,'') FROM ProviderPreference WHERE providerNo=${owner}`, dxCode,
      'The default dx code did not reach ProviderPreference');
    await schedule.waitForLoadState('networkidle').catch(() => {});
  });

  await s.step('Master Record ▸ Create Invoice preselects the default dx code on the bill form', async () => {
    const bill = await popup(master, master.locator('a[onclick*="/billing?"][onclick*="apptProvider_no=none"]').first(), 'bill-form');
    await bill.locator('select[name="xml_billtype"]').waitFor({ state: 'visible' });
    h.assert(await bill.locator('input[name="dxCode"]').first().inputValue() === dxCode,
      'The bill form did not preselect the provider\'s default dx code');
    await bill.close();
  });

  await s.step('negative probes: GET on saveWorkView and on the CPP save is refused and changes nothing', async () => {
    const before = `${viewValue('ticklerview')}|${property('cpp.pref.enable')}|${property('cpp.allergy.severity')}`;
    const view = await context.request.get(h.appUrl(config.baseUrl,
      '/saveWorkView?method=save&view_name=tickler&ticklerview=D'), { maxRedirects: 0 });
    h.assert(view.status() === 405, `GET saveWorkView answered HTTP ${view.status()}, expected 405`);
    const cpp = await context.request.get(h.appUrl(config.baseUrl,
      '/provider/CppPreferences?method=save&cpp.pref.enable=off&cpp.allergy.severity=off'), { maxRedirects: 0 });
    h.assert(`${viewValue('ticklerview')}|${property('cpp.pref.enable')}|${property('cpp.allergy.severity')}` === before,
      'A GET request changed a saved preference');
    h.assert(cpp.status() === 405, `GET provider/CppPreferences?method=save answered HTTP ${cpp.status()}, expected 405`);
  });

}

if (require.main === module) runWorkflow('provider-preferences-cpp-dx', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
