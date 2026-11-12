#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC private (pay patient) bill: the receipt, Update Invoice and Receive Payment.
 *
 * A BC private bill (billing.billingtype 'Pri', billingmaster.billingstatus 'P') is billed to the
 * patient, not MSP. Its invoice number in Bill Status opens the receipt (billing/CA/BC/billingView
 * with receipt=yes, billReceipt.jsp). There staff set who the invoice is addressed to and add a
 * note (Update Invoice, billing/CA/BC/UpdateBilling), and record what the patient paid (Receive
 * Payment, billing/CA/BC/viewReceivePaymentAction, a popup posting to receivePaymentAction). No
 * browser check covered any of it.
 *
 * User path: Master record ▸ Billing ▸ Invoice List (Bill Status filtered to the patient) ▸ status
 * Bill Patient ▸ Create Report ▸ invoice number (the receipt popup) ▸ bill-to and note ▸ Update
 * Invoice; receipt ▸ Receive Payment (popup) ▸ amount and method ▸ Submit.
 *
 * Asserted: Bill Status lists the owned private bill with its invoice link; the receipt is for that
 * bill and shows its amount; a GET to Update Invoice is refused with 405 and writes nothing; Update
 * Invoice saves the bill-to as the bill's bill_recipients row and the note as a billingnote of its
 * line, and leaves the line's payee number as it was; Receive Payment offers payment methods, and a
 * full payment writes one billing_history row with the amount received and marks the bill paid
 * private (billingstatus A).
 *
 * Finding 282 (#4717): the receipt's Update Invoice form posted an empty hidden billingNo and
 * UpdateBilling answered HTTP 500 (confirmed live on develop 2026-10-15). Behind it, finding 164
 * (#4551): receivePayment.jsp read an attribute no action sets, so its hidden bill and line inputs
 * were empty and its method list had none, and a payment saved nothing. release/2026.08 fixes the
 * first in #4723 and the second in #4659, so no step is expected to fail there.
 *
 * Fixtures: one private bill (billing + billingmaster, 45.00) for the workflow's own FAKE patient,
 * made a BC patient. Cleanup deletes every BC billing row of that patient, its billing_history,
 * billingnote and bill_recipients rows, and asserts none is left. BC installs only (billregion=BC).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { expectValue, runWorkflow } = require('./lib/workflow-session');
const bc = require('./lib/bc-billing-fixture');

const TIMEOUT = 30000;
const AMOUNT = '45.00';

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  const { billingNo, billingmasterNo } = bc.seedOwnedBcBill(s, { billingtype: 'Pri', status: 'P', amount: AMOUNT });
  const payee = sql.value(`SELECT payee_no FROM billingmaster WHERE billingmaster_no=${billingmasterNo}`);
  const recipientRows = () => sql.value(`SELECT COUNT(*) FROM bill_recipients WHERE billingNo=${billingNo}`);
  const noteRows = () => sql.value(`SELECT COUNT(*) FROM billingnote WHERE billingmaster_no=${billingmasterNo}`);
  const recipient = { name: `${marker.slice(0, 20)} Payer`, address: '1 FAKE Billing Road', city: 'Testville', province: 'BC', postal: 'V0V0V0' };
  const note = `${marker} receipt note`;

  let statusPage;
  await s.step('Bill Status lists the owned private bill under Bill Patient', async () => {
    statusPage = await bc.openBillStatus(s, ui);
    await bc.reportBillStatus(statusPage, ui, { status: 'P' });
    h.assert(await statusPage.locator(`a[href*="billingView?billing_no=${billingNo}&receipt=yes"]`).count() === 1,
      'Bill Status did not list the owned private bill with its invoice link');
  });

  let receipt;
  await s.step('the invoice number opens the receipt of the owned bill with its amount', async () => {
    receipt = await s.popup(statusPage, statusPage.locator(`a[href*="billingView?billing_no=${billingNo}&receipt=yes"]`), 'bc-receipt');
    await receipt.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(receipt, 'BC private bill receipt');
    const text = await receipt.locator('body').innerText();
    h.assert(text.includes(String(billingNo)), 'The receipt does not show the owned invoice number');
    h.assert(text.includes(AMOUNT), `The receipt does not show the bill amount ${AMOUNT}`);
    h.assert(await receipt.locator('form[action$="/billing/CA/BC/UpdateBilling"]').count() === 1, 'The receipt has no Update Invoice form');
  });

  await s.step('a GET to Update Invoice is refused and writes nothing', async () => {
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/billing/CA/BC/UpdateBilling?billingNo=${billingNo}&recipientName=${encodeURIComponent(recipient.name)}`
      + `&messageNotes=${encodeURIComponent(note)}&submit=Update%20Invoice`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET Update Invoice answered ${response.status()}, not 405`);
    h.assert(recipientRows() === '0' && noteRows() === '0', 'A refused GET wrote a bill-to or a note');
  });

  await s.step('Update Invoice saves the bill-to and the note against the owned bill and keeps its payee', async () => {
    const form = receipt.locator('form[action$="/billing/CA/BC/UpdateBilling"]');
    await form.locator('input[name="recipientName"]').fill(recipient.name);
    await form.locator('input[name="recipientAddress"]').fill(recipient.address);
    await form.locator('input[name="recipientCity"]').fill(recipient.city);
    await form.locator('input[name="recipientProvince"]').fill(recipient.province);
    await form.locator('input[name="recipientPostal"]').fill(recipient.postal);
    await form.locator('textarea[name="messageNotes"]').fill(note);
    const posted = receipt.waitForRequest(request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/billing/CA/BC/UpdateBilling'), { timeout: TIMEOUT });
    await ui.clickAndAwaitReload(receipt, form.locator('input[type="submit"][value="Update Invoice"]'), { label: 'Update Invoice', timeout: TIMEOUT });
    const request = await posted;
    await h.assertNotErrorPage(receipt, 'BC Update Invoice result');
    h.assert(new URLSearchParams(request.postData() || '').get('billingNo') === String(billingNo),
      'Update Invoice did not post the owned bill number');
    await expectValue(sql, `SELECT CONCAT_WS('|',name,address,city,province,postal) FROM bill_recipients WHERE billingNo=${billingNo}`,
      [recipient.name, recipient.address, recipient.city, recipient.province, recipient.postal].join('|'),
      'Update Invoice did not save the bill-to against the owned bill');
    h.assert(sql.value(`SELECT COUNT(*) FROM billingnote WHERE billingmaster_no=${billingmasterNo} AND note=${q(note)}`) === '1',
      'Update Invoice did not save the note against the owned bill line');
    h.assert(sql.value(`SELECT IFNULL(payee_no,'<null>') FROM billingmaster WHERE billingmaster_no=${billingmasterNo}`) === payee,
      'Update Invoice changed the bill line\'s payee number');
  });

  await s.step('Receive Payment records a full payment and marks the private bill paid', async () => {
    const link = receipt.locator('a[onclick*="viewReceivePaymentAction?lineNo="]').first();
    h.assert(await link.count() === 1, 'The receipt has no Receive Payment link');
    const payment = await s.popup(receipt, link, 'bc-receive-payment');
    await payment.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(payment, 'BC Receive Payment');
    const form = payment.locator('form[action$="/billing/CA/BC/receivePaymentAction"]');
    h.assert(await form.locator('#paymentMethod option').count() > 0, 'Receive Payment offers no payment method');
    await form.locator('input[name="amountReceived"]').fill(AMOUNT);
    await form.locator('#paymentMethod').selectOption({ index: 0 });
    const posted = payment.waitForRequest(request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/billing/CA/BC/receivePaymentAction'), { timeout: TIMEOUT });
    await ui.clickAndAwaitReload(payment, form.locator('input[type="submit"]'), { label: 'Receive Payment Submit', timeout: TIMEOUT });
    const request = await posted;
    await h.assertNotErrorPage(payment, 'BC Receive Payment result');
    h.assert(new URLSearchParams(request.postData() || '').get('billingmasterNo') === String(billingmasterNo),
      'Receive Payment did not post the owned bill line');
    await expectValue(sql, `SELECT COUNT(*) FROM billing_history WHERE billingmaster_no=${billingmasterNo} AND amount_received=${AMOUNT}`, '1',
      'Receive Payment did not write one billing_history row with the amount received');
    await expectValue(sql, `SELECT billingstatus FROM billingmaster WHERE billingmaster_no=${billingmasterNo}`, 'A',
      'A full payment did not mark the private bill paid (A)');
  });
}

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
}

if (require.main === module) runWorkflow('billing-bc-private-bill-receipt', workflow, { openPatient: true, preflight });
module.exports = { workflow };
