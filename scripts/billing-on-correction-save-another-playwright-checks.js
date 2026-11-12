#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario invoice correction ▸ "Save&Correct Another": the correction is saved and the page comes back ready for the
 * next invoice, with nothing of the first one carried into it.
 *
 * WHY THIS CHECK EXISTS. The correction page opened from Billing History has two save buttons. billing-on-correction-delete
 * presses Save (units, dx, status, unbill) and gap-billing-correction-admin-actions covers the Administration page's Save,
 * Rebill, Settle All and Reprint. Nothing pressed Save&Correct Another, which posts the same update
 * (billing/CA/ON/UpdateBillingONCorrection) and then renders the correction page again (the closeReload result) for the
 * clinician to look up the next invoice. A save that silently fails there, or a page that comes back still holding the
 * first invoice, sends a wrong claim to OHIP.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Billing History (popup) ▸ the first owned invoice's Edit (the correction
 * popup) ▸ units and dx ▸ Save&Correct Another ▸ (same popup) the invoice number lookup ▸ the second owned invoice ▸ Search
 * ▸ dx ▸ Save.
 * Asserted, against MariaDB after every round trip:
 *   1. Save&Correct Another posts the update for exactly the first invoice and saves it: its item has the new units, fee
 *      (schedule fee x units) and dx, the header total follows, and an audit snapshot of the item's earlier state is kept.
 *      The page that comes back is the correction page with an empty invoice lookup and no invoice loaded.
 *   2. Looking up the second invoice there loads it as stored (its own code, units, dx and status), with nothing from the
 *      first invoice.
 *   3. Saving a dx change on the second invoice changes only the second invoice; the first keeps its correction.
 * Fixtures: two owned open OHIP (HCP) bills for the owned FAKE- patient through seedOwnedBill
 * (billing-on-invoice-third-party), whose cleanup removes every row the bills and their corrections write, by id and
 * marker, and asserts them gone. Optional BILLING_CORRECTION_CODE (default A007A): an OHIP code with a schedule fee on or
 * before the bill date.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const {
  seedOwnedBill, openHistory, openCorrection, awaitResponse, billDate, money,
} = require('./billing-on-invoice-third-party-playwright-checks');

const NAME = 'billing-on-correction-save-another';
const CORRECTION_FORM = 'form[action$="/billing/CA/ON/UpdateBillingONCorrection"]';

function serviceCode() {
  const code = (process.env.BILLING_CORRECTION_CODE || 'A007A').toUpperCase();
  h.assert(/^[A-Z]\d{3}[A-Z]$/.test(code), 'BILLING_CORRECTION_CODE must be an Ontario service code like A007A');
  return code;
}

async function workflow(s) {
  const { sql } = s;
  const code = serviceCode();
  const date = billDate();
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)}
    AND billingservice_date<=${h.sqlString(date)} ORDER BY billingservice_date DESC LIMIT 1`);
  if (!/^\d+(\.\d+)?$/.test(fee) || Number(fee) <= 0) {
    throw new h.SkipCheck(`service code ${code} has no positive schedule fee on ${date}; set BILLING_CORRECTION_CODE`);
  }
  const feeTimesTwo = money(Number(fee) * 2);
  const first = seedOwnedBill(s, { payProgram: 'HCP', status: 'O', code, fee, date });
  const second = seedOwnedBill(s, { payProgram: 'HCP', status: 'O', code, fee, date });
  const item = (bill) => sql.value(`SELECT CONCAT_WS('|', ser_num, fee, dx, status, service_code) FROM billing_on_item
    WHERE id=${bill.itemId}`);
  const header = (bill) => sql.value(`SELECT CONCAT_WS('|', total, status, demographic_no, provider_no) FROM billing_on_cheader1
    WHERE id=${bill.headerId}`);
  const secondBefore = { item: item(second), header: header(second) };

  const updates = [];
  const watchUpdates = (page) => page.on('request', (request) => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/billing/CA/ON/UpdateBillingONCorrection')) {
      updates.push(new URLSearchParams(request.postData() || ''));
    }
  });

  let popup;
  let firstAfter;
  await s.step('Save&Correct Another saves the first invoice\'s correction and comes back with an empty lookup', async () => {
    const history = await openHistory(s);
    popup = await openCorrection(s, history, first.headerId);
    watchUpdates(popup);
    h.assert(await popup.locator('input[name="billingunit0"]').inputValue() === '1'
      && await popup.locator('input[name="xml_diagnostic_detail"]').inputValue() === '250',
    'The correction form did not load the first invoice as stored');
    await popup.locator('input[name="billingunit0"]').fill('2');
    await popup.locator('input[name="xml_diagnostic_detail"]').fill('401');
    const [response] = await Promise.all([
      awaitResponse(s.context, '/billing/CA/ON/UpdateBillingONCorrection'),
      popup.locator(`${CORRECTION_FORM} input[type="submit"][value="Save&Correct Another"]`).click(),
    ]);
    h.assert(response.status() === 200, `Save&Correct Another answered HTTP ${response.status()}`);
    h.assert(updates.length === 1 && updates[0].get('xml_billing_no') === first.headerId
      && updates[0].get('submit') === 'Save&Correct Another',
    'Save&Correct Another did not post one update for exactly the first invoice');
    await expectValue(sql, `SELECT CONCAT_WS('|', ser_num, fee, dx, status) FROM billing_on_item WHERE id=${first.itemId}`,
      `2|${feeTimesTwo}|401|O`, 'Save&Correct Another did not save the first invoice\'s units, fee and dx');
    h.assert(header(first).startsWith(`${feeTimesTwo}|O|`), 'The first invoice\'s total was not recomputed from the correction');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_repo WHERE category='billing_on_item' AND h_id=${first.itemId}`) === '1',
      'Save&Correct Another did not keep one audit snapshot of the corrected item');
    firstAfter = { item: item(first), header: header(first) };
    await popup.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(popup, 'correction page after Save&Correct Another');
    h.assert(!popup.isClosed(), 'Save&Correct Another closed the correction window');
    await popup.locator('#billing_no').waitFor({ state: 'visible', timeout: 20000 });
    const lookup = await popup.locator('#billing_no').inputValue();
    const loaded = await popup.locator(`${CORRECTION_FORM} input[name="xml_billing_no"]`).count()
      ? await popup.locator(`${CORRECTION_FORM} input[name="xml_billing_no"]`).inputValue() : '';
    h.assert(lookup === '' && loaded === '',
      `The page after Save&Correct Another still holds invoice "${lookup || loaded}", not an empty lookup`);
    h.assert(!(await popup.locator('body').innerText()).includes('401'),
      'The page after Save&Correct Another still shows the first invoice\'s corrected dx');
  });

  await s.step('looking up the second invoice there loads it as stored, with nothing from the first', async () => {
    await popup.locator('#billing_no').fill(second.headerId);
    await Promise.all([
      popup.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
      popup.locator('form[name="form1"] input[type="submit"][value="Search"]').click(),
    ]);
    await h.assertNotErrorPage(popup, 'correction page for the second invoice');
    await popup.locator(CORRECTION_FORM).waitFor({ state: 'visible', timeout: 20000 });
    h.assert(await popup.locator(`${CORRECTION_FORM} input[name="xml_billing_no"]`).inputValue() === second.headerId,
      'Search did not load the second invoice');
    h.assert(await popup.locator('input[name="servicecode0"]').inputValue() === code
      && await popup.locator('input[name="billingunit0"]').inputValue() === '1'
      && await popup.locator('input[name="xml_diagnostic_detail"]').inputValue() === '250'
      && await popup.locator('#status').inputValue() === 'O',
    'The second invoice did not load as stored (code, 1 unit, dx 250, status O): something carried over from the first');
    h.assert(item(second) === secondBefore.item && header(second) === secondBefore.header,
      'Looking up the second invoice changed it');
  });

  await s.step('saving the second invoice changes it alone; the first keeps its correction', async () => {
    await popup.locator('input[name="xml_diagnostic_detail"]').fill('493');
    const [response] = await Promise.all([
      awaitResponse(s.context, '/billing/CA/ON/UpdateBillingONCorrection'),
      popup.locator(`${CORRECTION_FORM} input[type="submit"][value="Save"]`).click(),
    ]);
    h.assert(response.status() === 200, `The second invoice's Save answered HTTP ${response.status()}`);
    h.assert(updates.length === 2 && updates[1].get('xml_billing_no') === second.headerId,
      'Save did not post one update for exactly the second invoice');
    await expectValue(sql, `SELECT CONCAT_WS('|', ser_num, dx, status) FROM billing_on_item WHERE id=${second.itemId}`,
      '1|493|O', 'Save did not store the second invoice\'s dx');
    h.assert(item(first) === firstAfter.item && header(first) === firstAfter.header,
      'Saving the second invoice changed the first one');
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
