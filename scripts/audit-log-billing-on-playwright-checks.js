#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of an Ontario invoice created from the Master Record (wave 7 sweep `audit-log`).
 *
 * User path: Schedule > Search > Master Record > Create Invoice (the Ontario bill form for appointment 0) > service
 * date, billing physician, service code and diagnostic code > Next (review) > Save (BillingONSave) > Master
 * Record > Billing History.
 *
 * Asserts the saved claim (billing_on_cheader1 for the owned patient and billing physician, one item with the
 * typed code) and, scoped to the owned patient: saving the claim wrote at least one audit row that is not a read,
 * carrying the provider, the client address and demographic_no = the patient (a claim holds the patient's HIN,
 * diagnosis and the fee), and no audit row carries the HIN or the diagnostic code; opening the Billing History
 * popup (the patient's whole billing record) wrote a read row naming the patient. Every expectation is
 * evaluated and the violated ones are reported together in the last step.
 *
 * Fixtures: createBillingFixture (owned billing provider and HIN on the owned patient); cleanup removes the claim
 * rows the browser wrote and every fixture, and the audit rows scoped to the patient, and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture } = require('./billing-on-ohip-simulation-report-playwright-checks');
const { openHistory } = require('./billing-on-invoice-third-party-playwright-checks');
const g = require('./lib/gap-billing-support');
const { auditProbe, phiLeaks, incomplete, label } = require('./lib/audit-log-helpers');

async function workflow(s) {
  const { sql, patient, provider } = s;
  const owned = createBillingFixture(s);
  g.registerOwnedBillCleanup(s);
  const probe = auditProbe({ sql, patient });
  s.cleanup(() => probe.cleanup());
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  const month = sql.value("SELECT DATE_FORMAT(CURDATE() - INTERVAL 1 MONTH, '%Y-%m')");
  const target = `${month}-01`;
  let form;

  await s.step('Create Invoice opens the bill form for the owned patient', async () => {
    const link = s.master.locator('a[onclick*="/billing?billRegion=ON"]').first();
    h.assert(await link.count() === 1, 'The Master Record does not offer Create Invoice');
    form = await s.popup(s.master, link, 'audit-bill-form');
    await form.locator('select[name="xml_billtype"]').waitFor({ state: 'visible', timeout: 30000 });
    await g.chooseBillingPhysician(form, owned.providerNo);
    await ui.pickDate(form, '#service_date', target);
  });

  let headerId;
  await s.step('Next and Save write the claim (audit rows observed)', async () => {
    await form.locator('input[name="serviceCode0"]').fill('A007A');
    await form.locator('input[name="dxCode"]').fill('250');
    await g.nextToReview(form);
    await probe.settle(1500);
    const before = probe.mark();
    const [response] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingONSave'), { timeout: 30000 }),
      form.locator('form[name="titlesearch"] input[type="submit"][value="Save"]').click(),
    ]);
    h.assert(response.status() === 200, `The bill save answered HTTP ${response.status()}`);
    const headers = g.headersOf(sql, patient, ['pay_program', 'status', 'provider_no', 'billing_date', 'hin']);
    h.assert(headers.length === 1, 'The save did not write exactly one claim for the owned patient');
    headerId = headers[0].id;
    h.assert(g.itemsOf(sql, headerId).length === 1, 'The claim does not carry exactly one item');
    await probe.settle(2500);
    const rows = probe.since(before, `action NOT LIKE 'read%' AND action NOT LIKE '%Manager.get%' AND action NOT LIKE '%Manager.find%'
      AND action NOT LIKE 'DemographicManager.%' AND action NOT LIKE 'PatientConsentManager.%'`);
    expect(rows.length >= 1, 'Saving an Ontario OHIP claim for a patient wrote no audit row');
    for (const r of rows) {
      const problems = incomplete([r], { provider, patient });
      expect(!problems.length, `Saving the claim: the audit row ${label(r)} is incomplete (${problems.join(', ')})`);
    }
    expect(!phiLeaks(probe.since(before), [owned.hin, '250']).length, 'Saving the claim: an audit row carries the HIN or the diagnostic code');
  });

  await s.step('the Master Record Billing History opens (audit rows observed)', async () => {
    await s.master.waitForLoadState('load').catch(() => {});
    await s.master.locator('a[onclick*="/billing/CA/ON/ViewBillingONHistory"]').first().waitFor({ state: 'attached', timeout: 20000 });
    await probe.settle(1500);
    const before = probe.mark();
    const history = await openHistory(s);
    await history.close();
    await probe.settle(2500);
    const reads = probe.since(before, `action LIKE 'read%' OR content LIKE '%illing%' OR action LIKE '%illing%'`);
    expect(reads.length >= 1, 'Opening the patient\'s Billing History wrote no audit row naming the patient');
  });

  await s.step('every expectation of the billing audit trail held', async () => {
    h.assert(!defects.length, `Audit-trail defects on the Ontario billing path:\n  - ${[...new Set(defects)].join('\n  - ')}`);
  });
}

if (require.main === module) runWorkflow('audit-log-billing-on', workflow, { openPatient: true, openMaster: true });
module.exports = { workflow };
