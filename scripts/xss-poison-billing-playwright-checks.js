#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the Ontario billing screens.
// User path: Master Record > Billing History; Master Record > Create Invoice (billing form); Administration >
// Invoice Reports (Create Report) > an invoice > Billing Correction; Administration > Manage Billing Service
// Code, Add Billing Location, Manage Payment Type, Manage Billing Form.
// Fixtures: the owned billing provider and claim of billing-on-ohip-simulation-report (patient and provider
// names stay clean so the billing URLs are not refused by the WAF; the claim header's name and comment, the
// provider's first name, a service code, a diagnostic code, a billing location, a payment type, a billing form
// and a third-party payer carry inert markup, INSERTed bypassing the WAF). Cleanup removes exactly those rows.
// Asserted per surface: literal text visible, no `[data-xp]` element in any frame, no script error;
// findings are collected for the whole walk and the check fails once at the end.
// Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const ui = require('./lib/playwright-ui');
const { payload, inspect, Findings, Seeder, clickAdminItem } = require('./lib/xss-poison-helpers');
const { createBillingFixture, openAdministration, openAdminFrame } = require('./billing-on-ohip-simulation-report-playwright-checks');
const { billDate } = require('./billing-on-invoice-third-party-playwright-checks');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

const q = h.sqlString;

async function workflow(s) {
  const fields = {};
  let n = 0;
  const P = (name, max = 255) => { n += 1; fields[n] = name; return payload(n, max); };
  const seed = new Seeder(s.sql, s.cleanup);
  const hex = s.marker.slice(-8);
  const owned = createBillingFixture(s);
  const date = billDate();
  let claim; let serviceCode;
  await s.step('seed the poisoned billing rows', async () => {
    claim = owned.addClaim({ tag: 'XP', date, status: 'B', items: [{ code: 'A007A', fee: '30.00' }] });
    s.sql.execute(`UPDATE provider SET first_name=${q(P('billing provider first name', 30))} WHERE provider_no=${q(owned.providerNo)} AND last_name=${q(s.marker)};
      UPDATE billing_on_cheader1 SET demographic_name=${q(P('invoice patient name', 60))}, comment1=${q(`${s.marker} ${P('invoice comment', 200)}`)} WHERE id=${claim.id}`);
    serviceCode = `X${hex}`.slice(0, 8);
    seed.insert('billingservice', { service_code: serviceCode, description: P('billing service description'), value: '1.00', billingservice_date: { raw: 'CURDATE()' }, region: 'ON', specialty: '' }, { key: 'billingservice_no' });
    seed.insert('diagnosticcode', { diagnostic_code: `X${hex.slice(0, 4)}`, description: P('diagnostic code description'), status: 'A', region: 'ON' }, { key: 'diagnosticcode_no' });
    seed.insert('clinic_location', { clinic_location_no: hex.slice(0, 4), clinic_no: 1, clinic_location_name: P('billing location', 40) }, { key: 'id' });
    seed.insert('billing_payment_type', { payment_type: P('payment type', 25) }, { key: 'id' });
    seed.insert('ctl_billingservice', { servicetype_name: P('billing form name', 150), servicetype: `X${hex}`.slice(0, 8), service_code: serviceCode, service_group_name: P('billing group name', 30), service_group: `G${hex}`.slice(0, 8), status: 'A', service_order: 1 }, { key: 'id' });
    seed.insert('billing_on_3rdPartyAddress', { attention: P('payer attention', 100), company_name: P('payer company', 100), address: P('payer address', 200), city: P('payer city', 200), province: 'ON', postcode: 'K1A0B1', telephone: '555', fax: '555' }, { key: 'id' });
  });
  const f = new Findings(s.recorder);
  const step = f.stepper(s);
  await step('Master Record > Billing History and Create Invoice show stored values as text', async () => {
    const master = s.master;
    const history = master.locator('a').filter({ hasText: /^\s*Billing History\s*$/i }).first();
    if (await history.count()) {
      const since = f.mark();
      const popup = await ui.clickOpensPopupOrNavigates(master, history, { context: s.context, label: 'billing-history', recorder: s.recorder, timeout: 20000 });
      await inspect(f, 'billing history', popup.page, fields, since);
      if (popup.isPopup) await popup.page.close(); else await popup.page.goBack().catch(() => {});
    } else f.note('billing history', 'link not offered');
    const create = master.locator('a').filter({ hasText: /^\s*Create Invoice\s*$/i }).first();
    if (await create.count()) {
      const since = f.mark();
      const popup = await ui.clickOpensPopupOrNavigates(master, create, { context: s.context, label: 'create-invoice', recorder: s.recorder, timeout: 20000 });
      await inspect(f, 'create invoice form', popup.page, fields, since);
      if (popup.isPopup) await popup.page.close(); else await popup.page.goBack().catch(() => {});
    } else f.note('create invoice', 'link not offered');
    await releaseChartLocks(s.context, s.config.baseUrl, [master]).catch(() => {});
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
    await inspect(f, 'invoice report', admin, fields, since);
    const invoice = frame.locator(`#bListTable a`, { hasText: new RegExp(`^\\s*${claim.id}\\s*$`) }).first();
    if (await invoice.count()) {
      since = f.mark();
      const target = await ui.clickOpensPopupOrNavigates(admin, invoice, { context: s.context, label: 'billing-correction', recorder: s.recorder, timeout: 20000 });
      await inspect(f, 'billing correction', target.page, fields, since);
      if (target.isPopup) await target.page.close();
    } else f.note('billing correction', 'invoice link not found');
  });
  await step('billing administration lists show stored values as text', async () => {
    for (const [text, surface] of [['Manage Billing Service Code', 'manage service code'], ['Add Billing Location', 'billing locations'], ['Manage Payment Type', 'payment types'], ['Manage Billing Form', 'billing forms'], ['Manage Private Billing Code', 'private billing codes']]) {
      const since = f.mark();
      try {
        await clickAdminItem(admin, text);
      } catch (error) { f.note(surface, 'not reached'); continue; }
      await inspect(f, surface, admin, fields, since);
    }
  });
  await step('the walk found no output-encoding defect', async () => { f.assertNone('Billing walk'); });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-billing', workflow, { openPatient: true });
