#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Receive Payment (#4551).
 *
 * receivePayment.jsp read a `receivePaymentActionForm` request attribute that no action set, so
 * the popup's hidden billingmasterNo and billNo inputs were empty and its Method list had no
 * options: a submit archived nothing and showed the empty form again.
 *
 * User path: Master record > Billing > Invoice List (Bill Status) > Edit (adjust bill page) >
 * View Invoice > Receive Payment. Asserted: the popup carries the bill's line and invoice
 * numbers and offers the manual payment methods (never ELECTRONIC); a GET to the save route is
 * refused with 405; a blank amount re-renders the form with an error and writes nothing; a valid
 * amount writes exactly one billing_history row for that amount, shows the confirmation and
 * leaves a part-paid bill open; a second payment of the balance marks the bill paid-private.
 * Direct requests are negative probes only.
 *
 * Expected to FAIL while app-findings-log.md finding 164 stands.
 *
 * Fixtures: one private (Pri) billing + billingmaster row for the workflow's own FAKE patient,
 * stamped with the run marker; cleanup deletes exactly those rows and the history the payments
 * wrote, and asserts they are gone. BC installs only (billregion=BC).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const TIMEOUT = 30000;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  let billingNo;
  let billingmasterNo;

  s.cleanup(() => {
    // Keyed by the owned patient, not by the ids captured above: the cleanup must still recover the rows when the
    // INSERT succeeded and the id read that followed it was lost.
    const owned = sql.rows(`SELECT billingmaster_no FROM billingmaster WHERE demographic_no=${patient}`)
      .map(([id]) => id).filter((id) => /^\d+$/.test(id));
    if (owned.length) {
      const list = owned.join(',');
      sql.execute([
        `DELETE FROM billing_history WHERE billingmaster_no IN (${list})`,
        `DELETE FROM billingnote WHERE billingmaster_no IN (${list})`,
        `DELETE FROM billingmaster WHERE billingmaster_no IN (${list}) AND demographic_no=${patient}`,
      ].join(';'));
    }
    sql.execute(`DELETE FROM billing WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billingmaster WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billingnote WHERE billingmaster_no IN (${owned.length ? owned.join(',') : 0}))
      + (SELECT COUNT(*) FROM billing_history WHERE billingmaster_no IN (${owned.length ? owned.join(',') : 0}))`) === '0',
    'Owned BC bill rows were not removed');
  });

  // A BC claim for the owned patient, shaped like one the BC billing form writes.
  sql.execute(`UPDATE demographic SET hc_type='BC', hin='9${String(patient).padStart(9, '0').slice(-9)}', ver=''
    WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const ohip = sql.value(`SELECT IFNULL(NULLIF(ohip_no,''),'00000') FROM provider WHERE provider_no=${q(provider)}`);
  billingNo = sql.value(`INSERT INTO billing (clinic_no,demographic_no,provider_no,appointment_no,demographic_name,
      billing_date,billing_time,update_date,update_time,status,provider_ohip_no,billingtype,creator,total)
    VALUES (0,${patient},${q(provider)},0,${q(`${marker},Workflow`)},CURDATE(),CURTIME(),CURDATE(),CURTIME(),
      'P',${q(ohip)},'Pri',${q(provider)},'23.00'); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingNo), 'The BC billing fixture was not created');
  billingmasterNo = sql.value(`INSERT INTO billingmaster (billing_no,createdate,billingstatus,demographic_no,appointment_no,
      claimcode,datacenter,payee_no,practitioner_no,phn,name_verify,dependent_num,billing_unit,clarification_code,
      anatomical_area,after_hour,new_program,billing_code,bill_amount,payment_mode,service_date,service_to_day,
      submission_code,extended_submission_code,dx_code1,service_location,referral_flag1,referral_flag2,birth_date,
      correspondence_code,claim_comment,mva_claim_code,paymentMethod)
    VALUES (${billingNo},NOW(),'P',${patient},0,'C02','00000',${q(ohip.slice(0, 5))},${q(ohip.slice(0, 5))},
      (SELECT hin FROM demographic WHERE demographic_no=${patient}),'WOR','00','1','00','00','0','00','00100','23.00',
      '0',DATE_FORMAT(CURDATE(),'%Y%m%d'),'00','0',' ','250','A','0','0','19800102','0','','N',6);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingmasterNo), 'The BC billingmaster fixture was not created');
  const historyRows = () => sql.value(`SELECT COUNT(*) FROM billing_history WHERE billingmaster_no=${billingmasterNo}`);

  const status = () => sql.value(`SELECT billingstatus FROM billingmaster WHERE billingmaster_no=${billingmasterNo}`);

  let billStatus;
  await s.step('Invoice List on the master record lists the owned bill', async () => {
    const link = masterRecordLink(s.master, '/billing/CA/BC/reprocessBill?').first();
    h.assert(await link.count() === 1, 'The BC master record has no Invoice List link');
    billStatus = await ui.clickOpensPopup(s.master, link, { context: s.context, recorder: s.recorder,
      label: 'bc-bill-status', timeout: 20000, position: { x: 8, y: 8 } });
    await billStatus.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(billStatus, 'BC Bill Status');
    h.assert(await billStatus.locator(`a[href*="billingmaster_no=${billingmasterNo}&"]`).count() === 1,
      'Bill Status did not list the owned bill with its Edit link');
  });

  let adjust;
  await s.step('Edit opens the adjust bill page', async () => {
    adjust = await s.popup(billStatus, billStatus.locator(`a[href*="billingmaster_no=${billingmasterNo}&"]`), 'bc-adjust-bill');
    await adjust.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(adjust, 'BC adjust bill page');
  });

  let receipt;
  await s.step('View Invoice shows the invoice with a Receive Payment link', async () => {
    const view = adjust.locator('a', { hasText: 'View Invoice' });
    h.assert(await view.count() === 1, 'The adjust page has no View Invoice link for the part-billed private bill');
    // popupPage() opens every popup in the one window named "attachment", so from the adjust page
    // (itself that window) View Invoice navigates it in place rather than opening another.
    await view.click({ timeout: TIMEOUT });
    receipt = adjust;
    await receipt.waitForURL(/\/billing\/CA\/BC\/billingView\?/, { timeout: TIMEOUT });
    await receipt.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(receipt, 'BC invoice');
    h.assert(await receipt.locator('.rcvPayment a', { hasText: 'Receive Payment' }).count() >= 1,
      'The invoice has no Receive Payment link');
  });

  let pay;
  await s.step('Receive Payment opens a form that carries the bill and offers the manual methods', async () => {
    pay = await s.popup(receipt, receipt.locator('.rcvPayment a', { hasText: 'Receive Payment' }).first(), 'bc-receive-payment');
    await pay.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(pay, 'BC Receive Payment');
    // The finding: both hidden inputs were empty because the page read an attribute nothing set.
    h.assert(await pay.locator('input#billingmasterNo').inputValue() === billingmasterNo,
      'Receive Payment does not carry the billing line number');
    h.assert(await pay.locator('input#billNo').inputValue() === billingNo,
      'Receive Payment does not carry the invoice number');
    const methods = await pay.locator('select#paymentMethod option').allTextContents();
    h.assert(methods.length > 0, 'The Method list has no options');
    h.assert(!methods.some((m) => m.trim().toUpperCase() === 'ELECTRONIC'), 'The Method list offers ELECTRONIC');
  });

  await s.step('a GET to the save route is refused and records nothing', async () => {
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/billing/CA/BC/receivePaymentAction?billingmasterNo=${billingmasterNo}&billNo=${billingNo}&amountReceived=5&paymentMethod=1`),
    { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET payment answered ${response.status()}, not 405`);
    h.assert(historyRows() === '0', 'A refused GET wrote billing history');
  });

  const submit = async (amount, label) => {
    await pay.locator('input[name="amountReceived"]').fill(amount);
    const posted = pay.waitForRequest((request) => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/billing/CA/BC/receivePaymentAction'), { timeout: TIMEOUT });
    await ui.clickAndAwaitReload(pay, pay.locator('input[type="submit"]'), { label, timeout: TIMEOUT });
    await posted;
    await h.assertNotErrorPage(pay, label);
  };

  await s.step('a blank amount re-renders the form with an error and writes nothing', async () => {
    await submit('', 'blank amount');
    h.assert(await pay.locator('#receivePaymentError').count() === 1, 'A blank amount showed no error');
    h.assert(await pay.locator('input#billingmasterNo').inputValue() === billingmasterNo,
      'The re-rendered form lost the billing line number');
    h.assert(await pay.locator('select#paymentMethod option').count() > 0, 'The re-rendered form lost its payment methods');
    h.assert(historyRows() === '0', 'A blank amount wrote billing history');
  });

  await s.step('a part payment is archived and the bill stays open', async () => {
    await submit('10.00', 'part payment');
    h.assert(await pay.locator('input[name="amountReceived"]').count() === 0, 'The form did not give way to the confirmation');
    h.assert((await pay.locator('.msgDisplay').first().innerText()).includes(billingNo), 'The confirmation does not name the invoice');
    h.assert(historyRows() === '1', 'The payment did not write exactly one billing_history row');
    const received = parseFloat(sql.value(`SELECT amount_received FROM billing_history WHERE billingmaster_no=${billingmasterNo}`));
    h.assert(received === 10, `The archived payment was ${received}, not 10`);
    h.assert(status() === 'P', `A part payment moved the bill to ${status()}`);
  });

  await s.step('paying the balance marks the bill paid', async () => {
    // The invoice window is still open behind the confirmation, and the popup is still open in front
    // of it; popupPage() reuses the one "attachment" window, so Receive Payment again turns the
    // confirmation back into the form instead of opening a new window.
    await receipt.locator('.rcvPayment a', { hasText: 'Receive Payment' }).first().click({ timeout: TIMEOUT });
    await pay.waitForURL(/\/billing\/CA\/BC\/viewReceivePaymentAction\?/, { timeout: TIMEOUT });
    await pay.waitForLoadState('domcontentloaded');
    await submit('13.00', 'balance payment');
    h.assert(historyRows() === '2', 'The balance payment did not write a second billing_history row');
    h.assert(status() === 'A', `Paying the balance left the bill at ${status()}, not paid-private (A)`);
  });
}

async function preflight({ sql }) {
  if (sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='billingmaster'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required (billregion=BC)');
  }
}

if (require.main === module) runWorkflow('billing-bc-receive-payment', workflow, { openPatient: true, preflight });
module.exports = { workflow };
