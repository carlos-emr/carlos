#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Billing Preferences, a provider's own billing defaults, as a THROWAWAY login (lib/throwaway-login-fixture.js):
 * everything the page saves is keyed to the signed-in provider, so the shared test provider's preferences are never
 * touched and the check need not run alone. The billing part of #4439 task 9 (billing-bc-preferences).
 *
 * User path: Schedule ▸ Preferences icon ▸ BC Billing Preferences (billing/CA/BC/viewBillingPreferencesAction, a new
 * tab) ▸ default billing provider, referral type, billing form, service location, payee, payee information, the two
 * boxes ▸ Save (POST saveBillingPreferencesAction, which shows the page again) ▸ reopen from Preferences ▸ change only
 * the referral ▸ Save ▸ then the master record's Create Invoice (the BC bill form) for an owned BC patient.
 * Asserted: the page opens for the throwaway; with nothing saved it offers Clinic Default and no payee (not the first
 * provider listed), ticks "Display Clinic Information Under Payee" and posts no provider number; Save stores every
 * choice in billing_preferences and the throwaway's property rows and writes no property row without a provider (a
 * clinic-wide setting); reopening shows every saved value; a Save that changes only the referral keeps every other
 * preference; the next bill form shows the saved billing form and service location.
 *
 * Fixtures: the throwaway login and the workflow's own FAKE patient, made a BC patient. Cleanup deletes the
 * throwaway's billing_preferences row (the page creates one on first open), then the fixture removes its property
 * rows and the login. A property row without a provider written during the run fails it and is left for a look.
 *
 * Expected (see the manifest notes): on 2026.08 the first step fails until #4938 (issue #4936) merges: the page's
 * script throws on every open (it looks for a warning element by the wrong id). Behind it, the form posts an empty
 * provider number, so Save stores every preference as a clinic-wide setting; the step with nothing saved checks that
 * before any Save, so on 2026.08 nothing is saved.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { masterRecordLink } = require('./lib/master-record-link');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const bc = require('./lib/bc-billing-fixture');

const TIMEOUT = 30000;

/** The property rows the page saves, one each per provider. */
const NAMES = ['default_billing_form', 'default_billing_provider', 'bc_default_service_location', 'invoice_payee_info',
  'invoice_payee_display_clinic', 'auto_populate_refer'];

/** The step labels the manifest's notes name; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Preferences ▸ BC Billing Preferences opens the throwaway\'s own billing preferences',
  fresh: 'with nothing saved the page offers Clinic Default and no payee, ticks the clinic information box and posts no provider number',
  save: 'Save stores every choice for the throwaway and writes no clinic-wide setting',
  reopen: 'reopening BC Billing Preferences shows every saved value',
  one: 'a Save that changes only the referral keeps every other preference',
  bill: 'the next bill form shows the saved billing form and service location',
};

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  const q = h.sqlString;
  bc.requireBcBillingSchema(sql);
  bc.makeBcPatient(s);
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  const owner = fixture.providerNo;
  h.assert(/^\d+$/.test(owner), 'The throwaway has no numeric provider number');
  const propMark = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM property'));
  s.cleanup(() => {
    sql.execute(`DELETE FROM billing_preferences WHERE providerNo=${q(owner)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_preferences WHERE providerNo=${q(owner)}`) === '0',
      'The throwaway\'s billing preferences were not removed');
    // Not deleted: a clinic-wide row is not keyed to anything this run owns. Report it for a look.
    const clinicWide = sql.rows(`SELECT id, name FROM property WHERE id>${propMark} AND provider_no IS NULL
      AND name IN (${NAMES.map(q).join(',')})`);
    h.assert(clinicWide.length === 0, `Clinic-wide property rows were written during the run: ${JSON.stringify(clinicWide)}`);
  });

  const stored = () => ({
    pref: sql.rows(`SELECT referral, defaultPayeeNo FROM billing_preferences WHERE providerNo=${q(owner)}`),
    props: Object.fromEntries(sql.rows(`SELECT name, COALESCE(value,'') FROM property WHERE provider_no=${q(owner)}
      AND name IN (${NAMES.map(q).join(',')})`)),
  });
  const clinicWideCount = () => sql.value(`SELECT COUNT(*) FROM property WHERE id>${propMark} AND provider_no IS NULL
    AND name IN (${NAMES.map(q).join(',')})`);

  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const openPrefs = () => ui.clickOpensPopup(schedule, schedule.getByTitle(/Edit your personal setting/i).first(),
    { context, recorder, label: 'preferences', timeout: TIMEOUT });

  let page;
  const openBillingPrefs = async (prefs) => {
    const link = prefs.locator('a[href*="/billing/CA/BC/viewBillingPreferencesAction"]');
    h.assert(await link.count() === 1, 'Preferences has no BC Billing Preferences link');
    await revealAuditLink(prefs, link, TIMEOUT);
    const opened = await ui.clickOpensPopup(prefs, link, { context, recorder, label: 'bc-billing-preferences', timeout: TIMEOUT });
    await opened.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(opened, 'BC Billing Preferences');
    await opened.locator('h2', { hasText: 'Billing Preferences' }).waitFor({ timeout: TIMEOUT });
    return opened;
  };
  const shown = () => page.evaluate(() => {
    const value = id => (document.getElementById(id) || {}).value;
    const box = id => { const e = document.getElementById(id); return e ? { tag: e.tagName, type: e.type, checked: e.checked, disabled: e.disabled } : null; };
    return {
      billingProvider: value('defaultBillingProvider'), referral: value('referral'), form: value('defaultBillingForm'),
      location: value('defaultServiceLocation'), payee: value('payeeProviderNo'), payeeInfo: value('invoicePayeeInfo'),
      clinicInfo: box('invoicePayeeDisplayClinicInfo'), autoPopulate: box('autoPopulateRefer'),
      postsProviderNo: !!document.querySelector('form [name="providerNo"]'),
    };
  });
  const save = async () => {
    const [response] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/BC/saveBillingPreferencesAction'),
        { timeout: TIMEOUT }),
      page.locator('form input[type="submit"][value="Save"]').click(),
    ]);
    h.assert(response.status() === 200, `Save answered HTTP ${response.status()}`);
    await page.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(page, 'BC Billing Preferences after Save');
    await page.locator('h2', { hasText: 'Billing Preferences' }).waitFor({ timeout: TIMEOUT });
  };
  /** A choice other than the select's Clinic Default option, so a stored first option cannot pass for it. */
  const otherOption = async (select) => {
    const values = await page.locator(`${select} option`).evaluateAll(options => options.map(o => o.value));
    const choice = values.find(v => v && v !== 'clinicdefault');
    h.assert(choice, `${select} offers nothing besides Clinic Default`);
    return choice;
  };

  let prefs;
  await s.step(STEP.open, async () => {
    prefs = await openPrefs();
    page = await openBillingPrefs(prefs);
    h.assert(await page.locator('form[action$="/billing/CA/BC/saveBillingPreferencesAction"]').count() === 1,
      'BC Billing Preferences has no form that saves');
  });

  let globalAutoPopulate;
  await s.step(STEP.fresh, async () => {
    const view = await shown();
    console.log(`    nothing saved: ${JSON.stringify(view)}`);
    // Checked first: on a build whose form posts a provider number, the check stops here, before any Save.
    h.assert(!view.postsProviderNo, 'The form posts a provider number (issue #4936: it is always empty, and Save stores the preferences as clinic-wide settings)');
    h.assert(view.billingProvider === 'clinicdefault', `The default billing provider shows ${view.billingProvider}, not Clinic Default`);
    h.assert(view.payee === 'NONE', `The default payee shows ${view.payee}, not none`);
    h.assert(view.form === 'clinicdefault' && view.location === 'clinicdefault', 'The billing form or service location is not Clinic Default');
    h.assert(view.referral === '1', `The referral type shows ${view.referral}, not Refer To`);
    h.assert(view.clinicInfo && view.clinicInfo.tag === 'INPUT' && view.clinicInfo.type === 'checkbox' && view.clinicInfo.checked,
      '"Display Clinic Information Under Payee" is not a ticked checkbox');
    h.assert(view.autoPopulate && view.autoPopulate.type === 'checkbox' && !view.autoPopulate.checked, 'The auto-populate box is missing or ticked');
    // The box is disabled only while the clinic-wide setting is on; the page then says so.
    globalAutoPopulate = view.autoPopulate.disabled;
    if (globalAutoPopulate) {
      h.assert(await page.getByText('cannot be changed per provider').count() === 1, 'The auto-populate box is disabled without the clinic-wide note');
    }
  });

  const choice = {};
  await s.step(STEP.save, async () => {
    choice.form = await otherOption('#defaultBillingForm');
    choice.location = await otherOption('#defaultServiceLocation');
    choice.payeeInfo = `FAKE payee ${marker.slice(-6)}`;
    await page.locator('#referral').selectOption('2');
    await page.locator('#defaultBillingForm').selectOption(choice.form);
    await page.locator('#defaultServiceLocation').selectOption(choice.location);
    await page.locator('#payeeProviderNo').selectOption('CUSTOM');
    await page.locator('#invoicePayeeInfo').fill(choice.payeeInfo);
    await page.locator('#invoicePayeeDisplayClinicInfo').uncheck();
    if (!globalAutoPopulate) await page.locator('#autoPopulateRefer').check();
    await save();
    const rows = stored();
    console.log(`    stored: ${JSON.stringify(rows)}`);
    h.assert(rows.pref.length === 1 && rows.pref[0][0] === '2' && rows.pref[0][1] === 'CUSTOM',
      `billing_preferences holds ${JSON.stringify(rows.pref)}, not referral 2 and the custom payee`);
    const expected = {
      default_billing_form: choice.form, default_billing_provider: 'clinicdefault', bc_default_service_location: choice.location,
      invoice_payee_info: choice.payeeInfo, invoice_payee_display_clinic: 'false',
    };
    // While the clinic-wide setting is on, the box is disabled and Save keeps the provider's own row, which a new
    // throwaway does not have; otherwise the ticked box is stored.
    if (globalAutoPopulate) {
      h.assert(!('auto_populate_refer' in rows.props), `auto_populate_refer was stored as ${JSON.stringify(rows.props.auto_populate_refer)} while the clinic-wide setting is on`);
    } else {
      expected.auto_populate_refer = 'true';
    }
    for (const [name, value] of Object.entries(expected)) {
      h.assert(rows.props[name] === value, `${name} is stored as ${JSON.stringify(rows.props[name])}, not ${JSON.stringify(value)}`);
    }
    h.assert(clinicWideCount() === '0', 'Save wrote a property row without a provider (a clinic-wide setting)');
  });

  await s.step(STEP.reopen, async () => {
    await page.close();
    page = await openBillingPrefs(prefs);
    const view = await shown();
    h.assert(view.billingProvider === 'clinicdefault' && view.referral === '2' && view.form === choice.form
      && view.location === choice.location && view.payee === 'CUSTOM' && view.payeeInfo === choice.payeeInfo,
    `The reopened page shows ${JSON.stringify(view)}`);
    h.assert(!view.clinicInfo.checked, 'The reopened page ticks the clinic information box that was saved off');
    h.assert(view.autoPopulate.checked === !globalAutoPopulate, 'The reopened page does not show the saved auto-populate choice');
  });

  await s.step(STEP.one, async () => {
    const before = stored();
    await page.locator('#referral').selectOption('3');
    await save();
    const after = stored();
    h.assert(after.pref.length === 1 && after.pref[0][0] === '3' && after.pref[0][1] === 'CUSTOM',
      `billing_preferences holds ${JSON.stringify(after.pref)}, not referral 3 and the custom payee`);
    h.assert(JSON.stringify(after.props) === JSON.stringify(before.props),
      `The other preferences changed: ${JSON.stringify(before.props)} became ${JSON.stringify(after.props)}`);
  });

  await s.step(STEP.bill, async () => {
    const { masterPage: master } = await openMasterRecord(context, schedule, recorder,
      { searchTerm: marker, preferredDemographicNo: patient, timeout: TIMEOUT });
    const link = masterRecordLink(master, '/billing?billRegion=', 'appointment_no=0&').first();
    h.assert(await link.count() === 1, 'The master record has no Create Invoice link');
    const form = await ui.clickOpensPopup(master, link, { context, recorder, label: 'bc-bill-form', timeout: TIMEOUT });
    await form.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(form, 'BC bill form');
    h.assert(await form.locator('form[action$="/billing/CA/BC/CreateBilling"]').count() === 1, 'Create Invoice did not open the BC bill form');
    const billForm = await form.locator('#selectBillingForm').inputValue();
    const location = await form.locator('#xml_visittype').inputValue();
    h.assert(billForm === choice.form, `The bill form shows billing form ${billForm}, not the saved ${choice.form}`);
    h.assert(location === choice.location, `The bill form shows service location ${location}, not the saved ${choice.location}`);
    h.assert(clinicWideCount() === '0', 'A clinic-wide property row appeared during the run');
  });
}

if (require.main === module) runWorkflow('billing-bc-preferences', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, STEP };
