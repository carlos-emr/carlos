#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Bill Status: the status, bill-type and service-date filters of the invoice list.
 *
 * Bill Status (billing/CA/BC/reprocessBill, billStatus.jsp) is where BC staff find a bill to
 * adjust, resubmit or settle. billing-bc-reprocess-bill opens it only to reach one bill; nothing
 * checked that its filters return the right bills. The filter form is a GET back to the same
 * route; the list comes from MSPReconcile.getBills (BillingDaoImpl.findByManyThings).
 *
 * User path: Master record ▸ Billing ▸ Invoice List (Bill Status filtered to the patient) ▸ status
 * radio / bill-type boxes / service-date window ▸ Create Report. Asserted, by each owned bill's
 * Edit link: the patient filter is fixed to the owned patient; each status lists exactly the owned
 * bill in that status (Not Submitted, Settled, Bill Patient); "All" with Private unticked lists both
 * MSP bills and not the private one; a service-date window that ends before the bills lists none.
 * The page writes nothing; a direct request is never used.
 *
 * Fixtures: three bills (billing + billingmaster) for the workflow's own FAKE patient, made a BC
 * patient: MSP not submitted (O), MSP settled (S) and private bill-patient (P), all dated today.
 * Cleanup deletes every BC billing row of that patient and asserts none is left. BC installs only
 * (billregion=BC); skipped elsewhere.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const bc = require('./lib/bc-billing-fixture');


async function workflow(s) {
  bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  const bills = {
    notSubmitted: bc.seedOwnedBcBill(s, { billingtype: 'MSP', status: 'O', amount: '23.00' }),
    settled: bc.seedOwnedBcBill(s, { billingtype: 'MSP', status: 'S', amount: '31.62' }),
    billPatient: bc.seedOwnedBcBill(s, { billingtype: 'Pri', status: 'P', amount: '45.00' }),
  };

  // Which owned bills the current list shows, by their Edit links (one per billingmaster line).
  async function listed(page) {
    const shown = [];
    for (const [name, { billingmasterNo }] of Object.entries(bills)) {
      if (await page.locator(`a[href*="billingmaster_no=${billingmasterNo}&"]`).count() > 0) shown.push(name);
    }
    return shown.sort().join(',');
  }

  const report = (page, options) => bc.reportBillStatus(page, ui, options);

  let status;
  await s.step('Invoice List opens Bill Status fixed to the owned patient', async () => {
    status = await bc.openBillStatus(s, ui);
    const patientBox = status.locator('form[name="serviceform"] input[name="demographicNo"]');
    h.assert(await patientBox.inputValue() === String(s.patient), 'Bill Status is not filtered to the owned patient');
    h.assert(await patientBox.getAttribute('readonly') !== null, 'The patient filter of an Invoice List can be changed');
  });

  for (const [label, statusCode, expected] of [
    ['Not Submitted', 'O', 'notSubmitted'],
    ['Settled', 'S', 'settled'],
    ['Bill Patient', 'P', 'billPatient'],
  ]) {
    await s.step(`status ${label} lists exactly the owned bill in that status`, async () => {
      await report(status, { status: statusCode });
      const shown = await listed(status);
      h.assert(shown === expected, `Status ${label} listed [${shown}] instead of [${expected}]`);
    });
  }

  await s.step('All with Private unticked lists both MSP bills and not the private one', async () => {
    await report(status, { status: '%', showPrivate: false });
    const shown = await listed(status);
    h.assert(shown === 'notSubmitted,settled', `All without Private listed [${shown}] instead of [notSubmitted,settled]`);
  });

  await s.step('All with every type lists the three owned bills', async () => {
    await report(status, { status: '%' });
    const shown = await listed(status);
    h.assert(shown === 'billPatient,notSubmitted,settled', `All listed [${shown}] instead of all three owned bills`);
  });

  await s.step('a service-date window that ends before the bills lists none of them', async () => {
    await report(status, { status: '%', from: '2000-01-01', to: '2000-12-31' });
    const shown = await listed(status);
    h.assert(shown === '', `A window in 2000 listed [${shown}]`);
  });
}

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
}

if (require.main === module) runWorkflow('billing-bc-bill-status-filters', workflow, { openPatient: true, preflight });
module.exports = { workflow };
