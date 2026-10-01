#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario invoice created from the Master Record, without an appointment.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Create Invoice (the Ontario bill form for
 * appointment 0) ▸ service date from the calendar icon ▸ billing physician ▸ typed code and
 * diagnostic code ▸ Next (ViewBillingONReview) ▸ Save (BillingONSave) ▸ Master Record ▸
 * Billing History.
 * billing-on-admin-config only opens this form to inspect its form chooser; billing-on-submit
 * bills from an appointment. This check saves the appointment-less claim.
 *
 * Asserts against MariaDB: the calendar fills the editable service date; the saved claim carries
 * the chosen date, no appointment, the owned patient and HIN, the chosen physician and the code
 * at its schedule fee with the dx; no appointment exists to be flagged billed; the Billing History
 * popup of the Master Record then lists the new invoice with its code, dx and fee.
 *
 * Fixtures: createBillingFixture (owned billing provider and HIN on the owned patient). Cleanup
 * removes the claim rows the browser wrote and every fixture, and asserts they are gone.
 * Implements the gap-billing "create invoice without appointment" workflow.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture } = require('./billing-on-ohip-simulation-report-playwright-checks');
const { openHistory, historyRow } = require('./billing-on-invoice-third-party-playwright-checks');
const g = require('./lib/gap-billing-support');

async function workflow(s) {
  const { sql, patient } = s;
  const owned = createBillingFixture(s);
  g.registerOwnedBillCleanup(s);
  const fee = g.money(g.scheduleFee(sql, 'A007A'));
  // The 1st of the previous month: never in the future, never today.
  const month = sql.value("SELECT DATE_FORMAT(CURDATE() - INTERVAL 1 MONTH, '%Y-%m')");
  const target = `${month}-01`;
  let form;

  await s.step('Create Invoice opens the bill form for appointment 0 with an editable service date', async () => {
    const link = s.master.locator('a[onclick*="/billing?billRegion=ON"]').first();
    h.assert(await link.count() === 1, 'The Master Record does not offer Create Invoice');
    form = await s.popup(s.master, link, 'bill-form');
    await form.locator('select[name="xml_billtype"]').waitFor({ state: 'visible', timeout: 30000 });
    const url = new URL(form.url());
    h.assert(url.searchParams.get('demographic_no') === patient && url.searchParams.get('appointment_no') === '0',
      'The bill form did not open for the owned patient without an appointment');
    h.assert(await form.locator('#service_date_cal').count() === 1, 'The bill form offers no calendar for the service date');
    await g.chooseBillingPhysician(form, owned.providerNo);
  });

  await s.step('the service date calendar fills the chosen day', async () => {
    await ui.pickDate(form, '#service_date', target);
    h.assert(await form.locator('#service_date').inputValue() === target, 'The calendar did not fill the service date with the chosen day');
  });

  let headerId;
  await s.step('Next and Save write the appointment-less claim on the chosen date', async () => {
    await form.locator('input[name="serviceCode0"]').fill('A007A');
    await form.locator('input[name="dxCode"]').fill('250');
    await g.nextToReview(form);
    const [response] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingONSave'),
        { timeout: 30000 }),
      form.locator('form[name="titlesearch"] input[type="submit"][value="Save"]').click(),
    ]);
    h.assert(response.status() === 200, `The bill save answered HTTP ${response.status()}`);
    const headers = g.headersOf(sql, patient, ['pay_program', 'status', 'provider_no', 'billing_date', 'appointment_no', 'hin', 'total']);
    h.assert(headers.length === 1, 'The save did not write exactly one claim for the owned patient');
    const header = headers[0];
    headerId = header.id;
    h.assert(header.billing_date === target && header.provider_no === owned.providerNo && header.hin === owned.hin
      && header.pay_program === 'HCP' && header.status === 'O', 'The claim does not carry the chosen date, physician, HIN, program and status');
    h.assert(!header.appointment_no || header.appointment_no === '0', 'The claim is linked to an appointment');
    const items = g.itemsOf(sql, headerId);
    h.assert(items.length === 1 && items[0].service_code === 'A007A' && Number(items[0].fee) === Number(fee) && items[0].dx === '250'
      && Number(header.total) === Number(fee), 'The claim item does not carry the code, fee and dx');
    h.assert(sql.value(`SELECT service_date FROM billing_on_item WHERE ch1_id=${headerId}`) === target, 'The item is not dated with the chosen day');
  });

  await s.step('the Master Record Billing History lists the new invoice with its code, dx and fee', async () => {
    // The save popup refreshes the Master Record that opened it: let it finish reloading first.
    await s.master.waitForLoadState('load').catch(() => {});
    await s.master.locator('a[onclick*="/billing/CA/ON/ViewBillingONHistory"]').first().waitFor({ state: 'attached', timeout: 20000 });
    const history = await openHistory(s);
    const row = historyRow(history, headerId);
    h.assert(await row.count() === 1, 'Billing History does not list the new invoice exactly once');
    const text = (await row.innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes('A007A') && text.includes('250') && text.includes(fee) && text.includes(target),
      'The history row does not show the date, code, dx and fee');
    await history.close();
  });
}

if (require.main === module) runWorkflow('gap-billing-create-invoice-no-appointment', workflow, { openPatient: true, openMaster: true });
module.exports = { workflow };
