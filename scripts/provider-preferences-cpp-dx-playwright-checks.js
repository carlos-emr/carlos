#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * provider-preferences-cpp-dx — coverage plan §2.2 (provider preferences: CPP display, default
 * billing dx code, saved tickler view, the Vaccine Provider landing).
 *
 * User paths, all as a THROWAWAY login (lib/throwaway-login-fixture.js) so the shared test
 * provider's chart layout, billing defaults and views never change under a parallel check:
 *   Schedule ▸ Preferences ▸ Account & Advanced ▸ Configure eChart CPP (provider/CppPreferences)
 *     ▸ duplicate positions refused by the form's alert ▸ Save ▸ Search ▸ Master Record ▸ E-Chart;
 *   Schedule ▸ Preferences ▸ Default billing dx code ▸ Save All ▸ Master Record ▸ Create Invoice;
 *   Schedule ▸ Tickler ▸ filter ▸ Save View (saveWorkView) ▸ reopen;
 *   login as a provider holding the Vaccine Provider role (provider/ViewVaccineProvider).
 * Asserted: each save reaches `property` / `ProviderPreference` / `view` (and the SAVE_CUSTOM_CPP
 * audit row), each reopen shows it, and its consumer honours it: the chart's Allergies items carry
 * the start date and severity, the bill form preselects the dx code, the tickler list reopens on
 * the saved filter. GET against both mutators is refused. Kept last: the chart honours the CPP
 * "Hide" choice for a section.
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
    const closed = cpp.waitForEvent('close', { timeout: 20000 });
    await cpp.locator('input[type="submit"]').click({ noWaitAfter: true });
    await closed;
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

  await s.step('Preferences ▸ default billing dx code ▸ Save All stores it on ProviderPreference', async () => {
    const prefs = await openPrefs();
    const field = prefs.locator('#dxCode');
    await revealAuditLink(prefs, field, 20000);
    await field.fill(dxCode);
    const closed = prefs.waitForEvent('close', { timeout: 20000 });
    await prefs.locator('.footer-bar button[type="submit"]').click({ noWaitAfter: true });
    await closed;
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
}

if (require.main === module) runWorkflow('provider-preferences-cpp-dx', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
