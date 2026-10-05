#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Issue #4279: Payment Received includes active RA premiums on both selected
 * calendar dates, including a single-day range. Exercises all/one provider,
 * inactive premiums, adjacent dates, premium subtotals and the final paid total.
 * Standard browser/SQL harness; disposable Ontario database, no extra settings.
 * Owns two providers, a patient and six RA/premium pairs; removes every fixture.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture } = require('./billing-on-ohip-simulation-report-playwright-checks');
const { fillDate } = require('./billing-on-invoice-third-party-playwright-checks');

async function workflow(s) {
  const { sql, marker, context } = s;
  const selected = createBillingFixture(s);
  const other = createBillingFixture(s);
  const fixtures = [
    { tag: 'before', day: '2004-06-14', amount: '100.00', active: 1, provider: selected },
    { tag: 'start', day: '2004-06-15', amount: '11.11', active: 1, provider: selected },
    { tag: 'end', day: '2004-06-16', amount: '22.22', active: 1, provider: selected },
    { tag: 'after', day: '2004-06-17', amount: '200.00', active: 1, provider: selected },
    { tag: 'inactive', day: '2004-06-16', amount: '999.99', active: 0, provider: selected },
    { tag: 'other', day: '2004-06-16', amount: '33.33', active: 1, provider: other },
  ];
  const filenames = fixtures.map(row => `${marker}-${row.tag}`);
  const ownsRa = `filename IN (${filenames.map(h.sqlString).join(',')}) AND payable=${h.sqlString(marker)}`;
  // Register before any RA INSERT; recover even when an INSERT acknowledgement is lost.
  s.cleanup(() => {
    const ids = sql.rows(`SELECT raheader_no FROM raheader WHERE ${ownsRa}`).map(row => Number(row[0]));
    for (const id of ids) {
      sql.execute(`DELETE FROM billing_on_premium WHERE raheader_no=${id};
        DELETE FROM raheader WHERE raheader_no=${id} AND ${ownsRa}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_premium WHERE raheader_no=${id}`) === '0',
        'Owned premium rows remain after cleanup');
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM raheader WHERE ${ownsRa}`) === '0', 'Owned RA rows remain after cleanup');
  });
  const page = await context.newPage();
  await h.gotoApp(page, s.config.baseUrl, '/billing/CA/ON/BillingONPayment');
  const generate = async (provider, start, end) => {
    await page.locator('select[name="providerList"]').selectOption(provider);
    await fillDate(page, '#startDateText', start);
    await fillDate(page, '#endDateText', end);
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      page.locator('form[name="billingPaymentForm"] input[type="submit"]').click(),
    ]);
    await h.assertNotErrorPage(page, 'Payment Received report');
  };
  const premiumTable = () => page.locator('table').filter({ has: page.locator('th', { hasText: 'Pay Date' }) });
  const totals = async () => {
    const footer = premiumTable().locator('tbody > tr').last();
    const money = value => {
      const number = Number(value.replace(/[$,\s]/g, ''));
      h.assert(Number.isFinite(number), 'Report amount is not numeric');
      return Math.round(number * 100);
    };
    const premium = money(await footer.locator('td').nth(3).innerText());
    const final = money((await page.locator('h3').filter({ hasText: 'Total Paid' }).innerText()).split(':').pop());
    return { premium, final };
  };
  const cases = [
    { provider: selected.providerNo, start: '2004-06-15', end: '2004-06-16', amounts: ['11.11', '22.22'], cents: 3333 },
    { provider: '', start: '2004-06-15', end: '2004-06-16', amounts: ['11.11', '22.22', '33.33'], cents: 6666 },
    { provider: selected.providerNo, start: '2004-06-16', end: '2004-06-16', amounts: ['22.22'], cents: 2222 },
    { provider: '', start: '2004-06-16', end: '2004-06-16', amounts: ['22.22', '33.33'], cents: 5555 },
  ];
  // Existing unrelated payments can contribute to All Providers totals.
  for (const scenario of cases) {
    await generate(scenario.provider, scenario.start, scenario.end);
    scenario.before = await totals();
  }
  for (const [index, row] of fixtures.entries()) {
    const id = sql.value(`INSERT INTO raheader (filename, paymentdate, payable, totalamount, records, claims, status, readdate)
      VALUES (${h.sqlString(filenames[index])}, '20040616', ${h.sqlString(marker)}, '0.00', '0', '0', 'N', '20040616');
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'RA fixture insert failed');
    sql.execute(`INSERT INTO billing_on_premium
      (raheader_no, provider_no, providerohip_no, pay_date, amount_pay, create_date, creator, status)
      VALUES (${id}, ${h.sqlString(row.provider.providerNo)}, ${h.sqlString(row.provider.ohipNo)},
        ${h.sqlString(row.day)}, ${h.sqlString(row.amount)}, NOW(), ${h.sqlString(s.provider)}, ${row.active})`);
  }
  const premiumState = () => JSON.stringify(sql.rows(`SELECT * FROM billing_on_premium WHERE raheader_no IN
    (SELECT raheader_no FROM raheader WHERE ${ownsRa}) ORDER BY premium_id`));
  const before = premiumState();
  for (const scenario of cases) {
    await s.step(`Payment Received ${scenario.provider ? 'selected provider' : 'all providers'}: ${scenario.start} through ${scenario.end}`, async () => {
      await generate(scenario.provider, scenario.start, scenario.end);
      const rows = premiumTable().locator('tbody > tr').filter({ hasText: marker });
      const amounts = await rows.locator('td:last-child').allTextContents();
      h.assert(JSON.stringify(amounts.map(value => value.trim()).sort()) === JSON.stringify([...scenario.amounts].sort()),
        'Payment Received omitted an End Date premium or included an inactive/adjacent-date/unselected premium');
      const after = await totals();
      h.assert(after.premium - scenario.before.premium === scenario.cents, 'Premium subtotal omitted the boundary amount');
      h.assert(after.final - scenario.before.final === scenario.cents, 'Final paid total omitted the boundary amount');
      h.assert(premiumState() === before, 'Reading Payment Received changed premium records');
    });
  }
  await page.close();
}

if (require.main === module) runWorkflow('billing-on-premium-payment-date', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
