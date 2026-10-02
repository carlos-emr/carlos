#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the Ontario billing screens.
// User path: Master Record > Billing History; Master Record > Create Invoice (billing form); Administration >
// Invoice Reports (Create Report) > an invoice > Billing Correction > Payer (third-party payer search) and the
// diagnostic-code search; Administration > Manage Billing Service Code (search the seeded code), Add Billing
// Location, Manage Payment Type, Manage Billing Form.
// Fixtures: the owned billing provider and claim of billing-on-ohip-simulation-report (patient and provider
// names stay clean so the billing URLs are not refused by the WAF; the claim header's name and comment, the
// provider's first name, a service code, two diagnostic codes, a billing location, a payment type, a billing form
// and a third-party payer carry inert markup, INSERTed bypassing the WAF). Cleanup removes exactly those rows,
// and every row of the borrowed fixture and the session patient is in the Seeder ledger before the first
// write, so a killed run's rows are removed by key when the next run starts.
// Asserted per surface: literal text visible, no `[data-xp]` element in any frame, no script error, and the
// seeded values that surface is known to show are shown; findings are collected for the whole walk and the
// check fails once at the end.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { payload, inspect, Findings, Seeder, clickAdminItem, fieldIds } = require('./lib/xss-poison-helpers');
const { createBillingFixture, openAdministration, openAdminFrame } = require('./billing-on-ohip-simulation-report-playwright-checks');
const { billDate } = require('./billing-on-invoice-third-party-playwright-checks');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { SUPPORT } = require('./lib/xss-poison-patient');

const q = h.sqlString;

async function workflow(s) {
  const fields = {};
  // Payload numbers start at 300: each check owns its own range, so a concurrent xss-poison run's rows on a
  // shared list are never mistaken for this run's (inspect() ignores a number it did not create).
  let n = 300;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup, s.marker);
  const hex = s.marker.slice(-8);
  // The session's patient and the borrowed billing fixture (provider, provider sites, claim header, items and
  // the rows the billing pages hang off them) are created outside the Seeder, so a SIGKILLed run would leave
  // them for good: the payload sweep only neutralises the provider's and claim's text. Record every one of
  // them in the durable ledger first, so the next run's dead-run recovery deletes them by key. Recording
  // also opens the Seeder, and with it the one-time payload sweep, BEFORE anything is poisoned below:
  // opened later, that sweep would neutralise this run's own provider name and claim header.
  seed.track('demographic', `demographic_no=${s.patient} AND last_name=${q(s.marker)}`);
  for (const [table, column] of SUPPORT) seed.track(table, `${column}=${s.patient}`);
  const owned = createBillingFixture(s, { record: (table, where) => seed.track(table, where) });
  const date = billDate();
  let claim; let serviceCode;
  await s.step('seed the poisoned billing rows', async () => {
    claim = owned.addClaim({ tag: 'XP', date, status: 'B', items: [{ code: 'A007A', fee: '30.00' }] });
    s.sql.execute(`UPDATE provider SET first_name=${q(P('billing provider first name', 30))} WHERE provider_no=${q(owned.providerNo)} AND last_name=${q(s.marker)};
      UPDATE billing_on_cheader1 SET demographic_name=${q(P('invoice patient name', 60))}, comment1=${q(`${s.marker} ${P('invoice comment', 200)}`)} WHERE id=${claim.id}`);
    // Manage Billing Service Code searches only a well-formed OHIP code: a letter, three digits, a letter.
    // billingservice.service_code is not unique and the admin search shows the first match, so the code must be
    // one no row holds yet (a pre-existing match would hide the seeded description behind its own).
    serviceCode = '';
    for (let i = 0; i < 1000 && !serviceCode; i += 1) {
      const code = `X${String((parseInt(hex, 16) + i) % 1000).padStart(3, '0')}X`;
      if (s.sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${q(code)}`) === '0') serviceCode = code;
    }
    if (!serviceCode) throw new h.SkipCheck('every X###X billing service code is already in use, so the service-code fixture cannot be placed');
    seed.insert('billingservice', { service_code: serviceCode, description: P('billing service description'), value: '1.00', billingservice_date: { raw: 'CURDATE()' }, region: 'ON', specialty: '' }, { key: 'billingservice_no' });
    // Two codes: a description search with exactly one match picks it and closes the popup before anyone can
    // read the list (billingDigSearch.jsp autoSelect), so the list needs a second match to stay on screen.
    seed.insert('diagnosticcode', { diagnostic_code: `X${hex.slice(0, 4)}`, description: P('diagnostic code description'), status: 'A', region: 'ON' }, { key: 'diagnosticcode_no' });
    seed.insert('diagnosticcode', { diagnostic_code: `Y${hex.slice(0, 4)}`, description: P('second diagnostic code description'), status: 'A', region: 'ON' }, { key: 'diagnosticcode_no' });
    seed.insert('clinic_location', { clinic_location_no: hex.slice(0, 4), clinic_no: 1, clinic_location_name: P('billing location', 40) }, { key: 'id' });
    seed.insert('billing_payment_type', { payment_type: P('payment type', 25) }, { key: 'id' });
    seed.insert('ctl_billingservice', { servicetype_name: P('billing form name', 150), servicetype: `X${hex}`.slice(0, 8), service_code: serviceCode, service_group_name: P('billing group name', 30), service_group: `G${hex}`.slice(0, 8), status: 'A', service_order: 1 }, { key: 'id' });
    seed.insert('billing_on_3rdPartyAddress', { attention: P('payer attention', 100), company_name: P('payer company', 100), address: P('payer address', 200), city: P('payer city', 200), province: 'ON', postcode: 'K1A0B1', telephone: '555', fax: '555' }, { key: 'id' });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  // What each surface is known to show (live runs on the packaged install); a surface that stops showing them is
  // a MISSING finding, so an empty or misrouted page cannot pass as an encoded one.
  const [provider, invoiceName, invoiceComment, location, form] = fieldIds(fields, 'billing provider first name', 'invoice patient name',
    'invoice comment', 'billing location', 'billing form name');
  await step('Master Record > Billing History and Create Invoice show stored values as text', async () => {
    const master = s.master;
    const history = master.locator('a').filter({ hasText: /^\s*Billing History\s*$/i }).first();
    if (await history.count()) {
      const since = f.mark();
      const popup = await ui.clickOpensPopupOrNavigates(master, history, { context: s.context, label: 'billing-history', recorder: s.recorder, timeout: 20000 });
      await inspect(f, 'billing history', popup.page, fields, since, { expect: [provider] });
      if (popup.isPopup) await popup.page.close(); else await popup.page.goBack().catch(() => {});
    } else f.missing('billing history', 'the Master Record offers no Billing History link');
    const create = master.locator('a').filter({ hasText: /^\s*Create Invoice\s*$/i }).first();
    if (await create.count()) {
      const since = f.mark();
      const popup = await ui.clickOpensPopupOrNavigates(master, create, { context: s.context, label: 'create-invoice', recorder: s.recorder, timeout: 20000 });
      await inspect(f, 'create invoice form', popup.page, fields, since, { expect: [provider, location, form] });
      if (popup.isPopup) await popup.page.close(); else await popup.page.goBack().catch(() => {});
    } else f.missing('create invoice', 'the Master Record offers no Create Invoice link');
    await releaseChartLocks(s.context, s.config.baseUrl, [master]);
  });
  let admin;
  await step('Invoice Reports lists the claim and Billing Correction shows stored values as text', async () => {
    admin = await openAdministration(s);
    const frame = await openAdminFrame(admin, '/billing/CA/ON/ViewBillStatus', 'form[name="serviceform"]');
    const form = 'form[name="serviceform"]';
    await frame.locator(`${form} input[name="demographicNo"]`).fill(String(s.patient));
    for (const sel of ['#xml_vdate', '#xml_appointment_date']) {
      await frame.locator(sel).fill(date);
      await frame.locator('body').click({ position: { x: 4, y: 4 } });
    }
    await frame.locator('#statusTypeSubmittedOHIP').check();
    await frame.locator('select[name="providerview"]').last().selectOption(owned.providerNo);
    const navigated = admin.waitForEvent('framenavigated', { predicate: x => x === frame, timeout: 30000 });
    navigated.catch(() => {});
    await frame.locator(`${form} input[type="submit"][value="Create Report"]`).click();
    await navigated;
    await frame.waitForLoadState('networkidle', { timeout: 30000 });
    let since = f.mark();
    await inspect(f, 'invoice report', admin, fields, since, { expect: [provider, invoiceName] });
    const invoice = frame.locator(`#bListTable a`, { hasText: new RegExp(`^\\s*${claim.id}\\s*$`) }).first();
    if (await invoice.count()) {
      since = f.mark();
      const target = await ui.clickOpensPopupOrNavigates(admin, invoice, { context: s.context, label: 'billing-correction', recorder: s.recorder, timeout: 20000 });
      const correction = target.page;
      await inspect(f, 'billing correction', correction, fields, since, { expect: [provider, invoiceName, invoiceComment, location] });
      // The diagnostic-code search, searched by description with the payload's clean prefix (markup in a keyword
      // would be refused by the WAF, and is not the point).
      since = f.mark();
      const dx = await ui.clickOpensPopup(correction, correction.locator('a[href="javascript:ScriptAttach()"]').first(), { context: s.context, label: 'dx-search', recorder: s.recorder, timeout: 20000 });
      await dx.locator('input[name="codedesc"]').fill('FAKE-XP');
      await ui.clickAndAwaitReload(dx, dx.locator('form[name="codesearch"] input[type="submit"]'), { timeout: 20000, label: 'diagnostic code search' });
      await inspect(f, 'diagnostic code search', dx, fields, since, { expect: fieldIds(fields, 'diagnostic code description', 'second diagnostic code description') });
      await dx.close();
      // Payer: the third-party payer search, searched the same way. The page shows the Payer link only for a
      // third-party pay program, so the program is switched on the page the way a user does (nothing is saved);
      // the search opens into the correction window itself (window name "billcorrection"), so it goes last.
      await correction.locator('#payProgram').selectOption('PAT');
      since = f.mark();
      const payer = await ui.clickOpensPopupOrNavigates(correction, correction.locator('a[onclick*="search3rdParty"]').first(), { context: s.context, label: 'payer-search', recorder: s.recorder, timeout: 20000 });
      await payer.page.locator('input[name="keyword"]').fill('FAKE-XP');
      await ui.clickAndAwaitReload(payer.page, payer.page.locator('form[name="titlesearch"] input[type="submit"]'), { timeout: 20000, label: 'payer search' });
      await inspect(f, 'third-party payer search', payer.page, fields, since, { expect: fieldIds(fields, 'payer company') });
      if (payer.isPopup) await payer.page.close();
      if (target.isPopup) await correction.close();
    } else f.missing('billing correction', 'the seeded claim is not listed in the invoice report');
  });
  await step('billing administration lists show stored values as text', async () => {
    const surfaces = [['Manage Billing Service Code', 'manage service code', fieldIds(fields, 'billing service description')], ['Add Billing Location', 'billing locations', [location]],
      ['Manage Payment Type', 'payment types', fieldIds(fields, 'payment type')], ['Manage Billing Form', 'billing forms', [form]], ['Manage Private Billing Code', 'private billing codes', []]];
    for (const [text, surface, expect] of surfaces) {
      const since = f.mark();
      try {
        await clickAdminItem(admin, text);
        if (surface === 'manage service code') {
          // The page lists nothing until a code is searched: search the seeded one.
          const frame = admin.frameLocator('#dynamic-content iframe').first();
          await frame.locator('input[name="service_code"]').fill(serviceCode);
          await frame.locator('button[name="submitFrm"][value="Search"]').click();
          await admin.waitForLoadState('networkidle').catch(() => {});
          await admin.waitForTimeout(800);
        }
      } catch (error) { f.missing(surface, `not reached: ${String(error.message).split('\n')[0].slice(0, 100)}`); continue; }
      await inspect(f, surface, admin, fields, since, { expect });
    }
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Billing walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-billing', workflow, { openPatient: true });
