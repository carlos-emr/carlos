#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario Billing Correction opened from Administration: invoice / OHIP claim number lookup, Rebill
 * OHIP, Settle All and Reprint.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Billing Correction (ViewBillingCorrection?admin in
 * the #dynamic-content iframe) ▸ invoice number ▸ Search ▸ Rebill OHIP / Settle All / Reprint
 * (the correction links under Save). billing-on-correction-delete drives the same page from Billing
 * History (units, dx, status dropdown); this check covers the Administration entry and the three
 * shortcut links it does not touch.
 *
 * Asserts against MariaDB: an unknown invoice number reports "Invoice number does not exist!"; an
 * owned invoice loads its code, dx, status and the RA claim number of its radetail row; Rebill OHIP
 * turns a submitted bill (B) back to O with a header audit snapshot; Settle All
 * turns an open bill to S; Reprint shows the invoice of that
 * bill. The LAST step asserts three behaviours the page lacks today: Settle All writes the payment
 * record its service means to (billing_on_payment), a Save from the Administration entry keeps the
 * frame on the correction page with the saved banner (the search form drops ?admin), and an OHIP
 * claim number typed alone finds its bill (the form's invoice-number field is required).
 *
 * Fixtures: two owned bills (seedOwnedBill, billed under an active OHIP provider the correction page
 * lists) and an owned raheader/radetail pair giving the first bill its claim number. Cleanup removes
 * them by id and asserts they are gone.
 * Implements the gap-billing "billing correction from Administration" workflow.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { seedOwnedBill, money, billDate } = require('./billing-on-invoice-third-party-playwright-checks');
const { openAdministration } = require('./billing-on-ohip-simulation-report-playwright-checks');

const UPDATE = '/billing/CA/ON/UpdateBillingONCorrection';

async function workflow(s) {
  const { sql, marker, context } = s;
  const date = billDate();
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code='A007A'
    AND billingservice_date<=${h.sqlString(date)} ORDER BY billingservice_date DESC LIMIT 1`);
  if (!(Number(fee) > 0)) throw new h.SkipCheck('A007A has no schedule fee');
  const rebill = seedOwnedBill(s, { payProgram: 'HCP', status: 'B', code: 'A007A', fee, date });
  const open = seedOwnedBill(s, { payProgram: 'HCP', status: 'O', code: 'A007A', fee, date });

  // The RA claim number the correction page shows for the first bill (radetail.claim_no).
  const claimNo = `9${String(Math.floor(Math.random() * 1e10)).padStart(10, '0')}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM radetail WHERE claim_no=${h.sqlString(claimNo)};
      DELETE FROM raheader WHERE payable=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM radetail WHERE claim_no=${h.sqlString(claimNo)})
      + (SELECT COUNT(*) FROM raheader WHERE payable=${h.sqlString(marker)})`) === '0', 'Owned RA rows were not removed');
  });
  const raNo = sql.value(`INSERT INTO raheader (filename, paymentdate, payable, totalamount, records, claims, status, readdate)
    VALUES (${h.sqlString(`${marker.slice(0, 20)}.xml`)}, '20250101', ${h.sqlString(marker)}, '0.00', '1', '1', 'N', '20250101');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(raNo), 'The RA header fixture was not created');
  sql.execute(`INSERT INTO radetail (raheader_no, providerohip_no, billing_no, service_code, service_count, hin, amountclaim,
      amountpay, service_date, error_code, billtype, claim_no)
    VALUES (${raNo}, ${h.sqlString(rebill.ohip)}, ${rebill.headerId}, 'A007A', '01', '', '0.00', '0.00', '20250101', '', 'HCP',
      ${h.sqlString(claimNo)})`);

  const admin = await openAdministration(s);
  let frame;
  const reopen = async () => {
    const link = admin.locator('a.xlink[rel*="/billing/CA/ON/ViewBillingCorrection"]').first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe').first();
    await iframe.waitFor({ timeout: 20000 });
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Billing Correction did not load in the administration frame');
    await frame.locator('form[name="form1"]').waitFor({ state: 'visible', timeout: 20000 });
  };
  const search = async (invoice, claim = '') => {
    await frame.locator('#billing_no').fill(invoice);
    await frame.locator('input[name="claim_no"]').fill(claim);
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
    navigated.catch(() => {});
    await frame.locator('form[name="form1"] input[type="submit"]').click();
    await navigated;
    await frame.waitForLoadState('load');
  };
  let afterSave;
  let settlePayments;
  const state = id => sql.value(`SELECT CONCAT_WS('|', status, total, paid) FROM billing_on_cheader1 WHERE id=${id}`);

  await s.step('Administration ▸ Billing Correction opens a blank lookup and an unknown invoice number is reported', async () => {
    await reopen();
    h.assert(await frame.locator('#billing_no').inputValue() === '', 'The Administration entry did not open a blank invoice lookup');
    const unknown = String(Number(sql.value('SELECT MAX(id) FROM billing_on_cheader1')) + 1000000);
    await search(unknown);
    h.assert(await frame.locator('#alert_message', { hasText: 'Invoice number does not exist' }).count() === 1,
      'An unknown invoice number was not reported');
  });

  await s.step('Search loads the owned invoice with its code, dx, status and RA claim number', async () => {
    await search(rebill.headerId);
    const form = frame.locator('form[action$="/billing/CA/ON/UpdateBillingONCorrection"]');
    await form.waitFor({ state: 'visible', timeout: 20000 });
    h.assert(await form.locator('input[name="xml_billing_no"]').inputValue() === rebill.headerId, 'Another invoice was loaded');
    h.assert(await form.locator('input[name="servicecode0"]').inputValue() === 'A007A'
      && await form.locator('input[name="xml_diagnostic_detail"]').inputValue() === '250'
      && await frame.locator('#status').inputValue() === 'B', 'The loaded invoice does not show its code, dx and status');
    h.assert(await frame.locator('input[name="claim_no"]').inputValue() === claimNo, 'The page does not show the RA claim number of the invoice');
  });

  await s.step('Rebill OHIP turns the submitted bill back to O, snapshots the header and reports the save', async () => {
    const audits = () => Number(sql.value(`SELECT COUNT(*) FROM billing_on_repo WHERE category='billing_on_cheader1' AND h_id=${rebill.headerId}`));
    const before = audits();
    const [response] = await Promise.all([
      context.waitForEvent('response', { timeout: 20000, predicate: r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(UPDATE) }),
      frame.locator('#rebillLink').click(),
    ]);
    h.assert(response.status() === 200, `Rebill OHIP answered HTTP ${response.status()}`);
    await expectValue(sql, `SELECT status FROM billing_on_cheader1 WHERE id=${rebill.headerId}`, 'O', 'Rebill OHIP did not set the status to O');
    h.assert(audits() === before + 1, 'Rebill OHIP did not leave exactly one header audit snapshot');
    h.assert(sql.value(`SELECT total FROM billing_on_cheader1 WHERE id=${rebill.headerId}`) === money(fee), 'Rebill OHIP changed the total');
    await frame.waitForLoadState('load');
    // Judged by the last step: where the Administration frame lands after a Save.
    afterSave = {
      banner: await frame.locator('#alert_message', { hasText: 'saved' }).count() === 1,
      closePage: /Click here to close this window/.test(await frame.locator('body').innerText()),
    };
  });

  await s.step('Settle All turns the open bill to settled (S) and leaves its total alone', async () => {
    await reopen();
    await search(open.headerId);
    await frame.locator('form[action$="/billing/CA/ON/UpdateBillingONCorrection"]').waitFor({ state: 'visible', timeout: 20000 });
    const [response] = await Promise.all([
      context.waitForEvent('response', { timeout: 20000, predicate: r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(UPDATE) }),
      frame.locator('#settleLink').click(),
    ]);
    h.assert(response.status() === 200, `Settle All answered HTTP ${response.status()}`);
    await expectValue(sql, `SELECT status FROM billing_on_cheader1 WHERE id=${open.headerId}`, 'S', 'Settle All did not set the status to S');
    h.assert(state(open.headerId).startsWith(`S|${money(fee)}|`), 'Settle All changed the total');
    // Judged by the last step: the payment record the service means to write when a bill is settled.
    settlePayments = sql.value(`SELECT COUNT(*) FROM billing_on_payment WHERE billing_no=${open.headerId}`);
  });

  await s.step('Reprint shows the invoice of the loaded bill', async () => {
    await reopen();
    await search(open.headerId);
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
    navigated.catch(() => {});
    await frame.locator('#reprintLink').click();
    await navigated;
    await frame.waitForLoadState('load');
    h.assert(frame.url().includes(`ViewBillingON3rdInv?billingNo=${open.headerId}`), 'Reprint did not open the invoice of the loaded bill');
    h.assert((await frame.locator('body').innerText()).replace(/\s+/g, ' ').includes(money(fee)), 'The reprinted invoice does not show the bill total');
  });

  await s.step('a Save keeps the Administration page and its saved banner', async () => {
    h.assert(!afterSave.closePage && afterSave.banner,
      'A Save from Administration left the frame on the close page without the saved banner');
  });

  await s.step('an OHIP claim number alone finds its bill', async () => {
    await reopen();
    await search('', claimNo);
    h.assert(await frame.locator(`input[name="xml_billing_no"][value="${rebill.headerId}"]`).count() === 1,
      'Searching by OHIP claim number alone did not load its bill');
  });

  // Separate regression L267 (#4152): keep this assertion independent of lookup/navigation.
  await s.step('Settle All records a payment', async () => {
    h.assert(settlePayments === '1', 'Settle All settled the bill without writing its payment record');
  });
}

if (require.main === module) runWorkflow('gap-billing-correction-admin-actions', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
