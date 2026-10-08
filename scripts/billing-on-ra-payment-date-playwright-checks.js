#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Issue #4430: Payment Received lists every RA paid in the End Date's calendar month,
 * including an RA paid on the LAST day of that month (the report bound was exclusive
 * and dropped it). Exercises all/one provider, the days either side of the month, a
 * leap-year February 29, the RA paid subtotal and the final paid total.
 * Standard browser/SQL harness; disposable Ontario database, no extra settings.
 * Owns two providers, a patient, one claim per RA row and the RA header/detail pairs;
 * removes every fixture.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture } = require('./billing-on-ohip-simulation-report-playwright-checks');
const { fillDate } = require('./billing-on-invoice-third-party-playwright-checks');

async function workflow(s) {
  const { sql, marker, context } = s;
  const selected = createBillingFixture(s);
  const other = createBillingFixture(s);
  // Each RA row pays its own owned claim, so every row renders as the first row of its bill.
  const fixtures = [
    { tag: 'pre', paid: '20040531', amount: '100.00', provider: selected },
    { tag: 'start', paid: '20040601', amount: '11.11', provider: selected },
    { tag: 'end', paid: '20040630', amount: '22.22', provider: selected },
    { tag: 'post', paid: '20040701', amount: '200.00', provider: selected },
    { tag: 'other', paid: '20040630', amount: '33.33', provider: other },
    { tag: 'leap', paid: '20040229', amount: '44.44', provider: selected },
    { tag: 'march', paid: '20040301', amount: '300.00', provider: selected },
  ];
  const filenames = fixtures.map(row => `${marker}-${row.tag}`);
  // raheader.filename is varchar(30); a truncated name would escape the cleanup's exact match.
  h.assert(filenames.every(name => name.length <= 30), 'An RA fixture filename exceeds raheader.filename (30)');
  const ownsRaAs = alias => `${alias}filename IN (${filenames.map(h.sqlString).join(',')}) AND ${alias}payable=${h.sqlString(marker)}`;
  const ownsRa = ownsRaAs('');
  // Registered after the billing fixtures, so it runs before their claim/provider cleanups.
  s.cleanup(() => {
    const ids = sql.rows(`SELECT raheader_no FROM raheader WHERE ${ownsRa}`).map(row => Number(row[0]));
    for (const id of ids) {
      sql.execute(`DELETE FROM radetail WHERE raheader_no=${id};
        DELETE FROM raheader WHERE raheader_no=${id} AND ${ownsRa}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM radetail WHERE raheader_no=${id}`) === '0',
        'Owned RA detail rows remain after cleanup');
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
  const raTable = () => page.locator('table').filter({ has: page.locator('th', { hasText: 'CLAIM No.' }) });
  const money = value => {
    const number = Number(value.replace(/[$,\s]/g, ''));
    h.assert(Number.isFinite(number), `Report amount is not numeric: ${value}`);
    return Math.round(number * 100);
  };
  const totals = async () => {
    // Footer cells: item-count label, count, total label, fee, claimed, PAID, adjustments, padding.
    const footer = raTable().locator('tbody > tr').last();
    const ra = money(await footer.locator('td').nth(5).innerText());
    const final = money((await page.locator('h3').filter({ hasText: 'Total Paid' }).innerText()).split(':').pop());
    return { ra, final };
  };
  // The RA window is the End Date's whole calendar month; the Start Date does not narrow it.
  const cases = [
    { label: 'selected provider, June (30-day month)', provider: selected.providerNo, start: '2004-06-15', end: '2004-06-15',
      amounts: ['11.11', '22.22'], cents: 3333 },
    { label: 'all providers, June (30-day month)', provider: '', start: '2004-06-15', end: '2004-06-15',
      amounts: ['11.11', '22.22', '33.33'], cents: 6666 },
    { label: 'selected provider, February 2004 (leap year)', provider: selected.providerNo, start: '2004-02-01', end: '2004-02-10',
      amounts: ['44.44'], cents: 4444 },
  ];
  // Existing unrelated RAs can contribute to the All Providers totals.
  for (const scenario of cases) {
    await generate(scenario.provider, scenario.start, scenario.end);
    scenario.before = await totals();
  }

  for (const [index, row] of fixtures.entries()) {
    const claim = row.provider.addClaim({ tag: `ra-${row.tag}`, date: '2004-01-15', status: 'S',
      items: [{ code: 'A007A', fee: row.amount }] });
    const id = sql.value(`INSERT INTO raheader (filename, paymentdate, payable, totalamount, records, claims, status, readdate)
      VALUES (${h.sqlString(filenames[index])}, ${h.sqlString(row.paid)}, ${h.sqlString(marker)}, ${h.sqlString(row.amount)},
        '1', '1', 'N', ${h.sqlString(row.paid)});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'RA header fixture insert failed');
    sql.execute(`INSERT INTO radetail (raheader_no, providerohip_no, billing_no, service_code, service_count, hin,
        amountclaim, amountpay, service_date, error_code, billtype, claim_no)
      VALUES (${id}, ${h.sqlString(row.provider.ohipNo)}, ${claim.id}, 'A007A', '01', ${h.sqlString(row.provider.hin)},
        ${h.sqlString(row.amount)}, ${h.sqlString(row.amount)}, '20040115', '', 'HCP', ${h.sqlString(`C${index}`)})`);
  }
  const raState = () => JSON.stringify(sql.rows(`SELECT d.* FROM radetail d JOIN raheader r ON r.raheader_no=d.raheader_no
    WHERE ${ownsRaAs('r.')} ORDER BY d.radetail_no`));
  const before = raState();

  for (const scenario of cases) {
    await s.step(`Payment Received lists the month-end RA: ${scenario.label}`, async () => {
      await generate(scenario.provider, scenario.start, scenario.end);
      // Owned rows name the owned FAKE patient (marker last name); the paid column is the 10th cell.
      const rows = raTable().locator('tbody > tr').filter({ hasText: marker });
      const amounts = (await rows.locator('td:nth-child(10)').allTextContents()).map(value => value.trim()).sort();
      h.assert(JSON.stringify(amounts) === JSON.stringify([...scenario.amounts].sort()),
        `Payment Received RA rows were ${JSON.stringify(amounts)}, expected ${JSON.stringify(scenario.amounts)}: `
        + 'a last-day-of-month RA was omitted, or an adjacent-month or unselected-provider RA was included');
      const after = await totals();
      h.assert(after.ra - scenario.before.ra === scenario.cents, 'RA paid subtotal omitted the month-end amount');
      h.assert(after.final - scenario.before.final === scenario.cents, 'Final paid total omitted the month-end amount');
      h.assert(raState() === before, 'Reading Payment Received changed RA records');
    });
  }
  await page.close();
}

if (require.main === module) runWorkflow('billing-on-ra-payment-date', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
