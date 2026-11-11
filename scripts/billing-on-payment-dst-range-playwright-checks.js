#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Issue #6047: Payment Received refuses an End Date one day before the Start Date when that day
 * is a daylight saving spring-forward date. The range check counted days by dividing elapsed time
 * by 24 hours; the spring-forward day is 23 hours long, so March 9 back to March 8 counted as
 * zero days and the reversed range ran the report instead of showing the date error.
 *
 * A reversed range must be refused in every zone, so the check needs no knowledge of the
 * server's zone: it tries the 2026 spring-forward date of each common rule (North America,
 * Europe, New Zealand, south-eastern Australia), and whichever one the server's zone observes
 * reproduces the defect on a build without the fix. The forward one-day range over the same date
 * must still be accepted.
 * Standard browser/SQL harness; Ontario install, no extra settings. Read-only: no fixtures.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { fillDate } = require('./billing-on-invoice-third-party-playwright-checks');

/** 2026 spring-forward dates; the day after is the first day with the summer offset. */
const SPRING_FORWARD = [
  { rule: 'North America', day: '2026-03-08', next: '2026-03-09' },
  { rule: 'Europe', day: '2026-03-29', next: '2026-03-30' },
  { rule: 'New Zealand', day: '2026-09-27', next: '2026-09-28' },
  { rule: 'south-eastern Australia', day: '2026-10-04', next: '2026-10-05' },
];

const DATE_ERROR = /End date is greater than start date/i;

async function workflow(s) {
  const page = await s.context.newPage();
  await h.gotoApp(page, s.config.baseUrl, '/billing/CA/ON/BillingONPayment');
  const errorPanel = page.locator('.card.card-body').filter({ has: page.locator('form[name="billingPaymentForm"]') });
  const generate = async (start, end) => {
    await fillDate(page, '#startDateText', start);
    await fillDate(page, '#endDateText', end);
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      page.locator('form[name="billingPaymentForm"] input[type="submit"]').click(),
    ]);
    await h.assertNotErrorPage(page, 'Payment Received report');
    const text = await errorPanel.first().innerText();
    // The form re-renders the parsed dates; a value that changed means the request was not the one intended.
    h.assert(await page.locator('#startDateText').inputValue() === start, `Start Date did not round-trip ${start}`);
    h.assert(await page.locator('#endDateText').inputValue() === end, `End Date did not round-trip ${end}`);
    return DATE_ERROR.test(text);
  };

  await s.step('Payment Received refuses a reversed range on an ordinary day (control)', async () => {
    h.assert(await generate('2026-06-16', '2026-06-15'),
      'Payment Received accepted an End Date one day before the Start Date in June');
  });

  for (const { rule, day, next } of SPRING_FORWARD) {
    await s.step(`Payment Received refuses a reversed range over the ${rule} spring-forward day`, async () => {
      h.assert(await generate(next, day),
        `Payment Received accepted End Date ${day} before Start Date ${next}: the ${rule} spring-forward day `
        + 'was counted as zero days (#6047)');
    });
    await s.step(`Payment Received accepts the forward range over the ${rule} spring-forward day`, async () => {
      h.assert(!(await generate(day, next)),
        `Payment Received refused the ordered range ${day} to ${next} as an End Date before the Start Date`);
    });
  }
}

if (require.main === module) runWorkflow('billing-on-payment-dst-range', workflow, { openPatient: false });

module.exports = { SPRING_FORWARD };
