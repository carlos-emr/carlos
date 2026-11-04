#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario INR Batch Billing: the service date chosen through the calendar popup.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ INR Batch Billing (ViewInrReportINR in the
 * #dynamic-content iframe) ▸ "Date:" link (ViewBillingCalendarPopup) ▸ previous month / a day ▸
 * tick the INR row ▸ Generate INR Batch Billing (ViewInrOnGenINRbilling).
 * billing-on-reports-inr-eoy types the date and covers the rest of the INR generation; this check
 * covers the calendar popup it does not touch.
 *
 * Asserts: the popup opens on the current month; Last month steps back one month; clicking a day
 * closes the popup and fills the page's service-date field with that ISO date; generating bills
 * the ticked owned INR row with that date (claim header billing_date, item service_date, the INR
 * row's last bill date), not today's.
 *
 * Fixtures: createBillingFixture (owned billing provider and patient) and one owned billinginr row.
 * Cleanup removes the claim and INR rows and asserts they are gone.
 * Implements the gap-billing "INR calendar popup" workflow.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { createBillingFixture, openAdministration, openAdminFrame } = require('./billing-on-ohip-simulation-report-playwright-checks');
const g = require('./lib/gap-billing-support');

const q = h.sqlString;
const INR_CODE = 'G271A';
const INR_DX = '286';

async function workflow(s) {
  const { sql, patient, marker, provider, context } = s;
  const owned = createBillingFixture(s);
  g.registerOwnedBillCleanup(s);
  const fee = g.money(g.scheduleFee(sql, INR_CODE));
  let inrNo;
  s.cleanup(() => {
    sql.execute(`DELETE FROM billinginr WHERE demographic_no=${patient} AND demographic_name=${q(`${marker},Workflow`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM billinginr WHERE demographic_no=${patient}`) === '0', 'The owned INR row was not removed');
  });
  inrNo = sql.value(`INSERT INTO billinginr (demographic_no, demographic_name, hin, dob, provider_no, provider_ohip_no, provider_rma_no,
      creator, diagnostic_code, service_code, service_desc, billing_amount, billing_unit, createdatetime, status)
    VALUES (${patient}, ${q(`${marker},Workflow`)}, ${q(owned.hin)}, '19800102', ${q(owned.providerNo)}, ${q(owned.ohipNo)}, '',
      ${q(provider)}, ${q(INR_DX)}, ${q(INR_CODE)}, 'INR management', ${q(fee)}, '1', NOW(), 'N'); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(inrNo), 'The INR fixture was not created');

  const admin = await openAdministration(s);
  const inr = await openAdminFrame(admin, '/billing/CA/ON/ViewInrReportINR?provider_no=all', 'select[name="provider"]');
  const row = inr.locator('tr').filter({ has: inr.locator(`input[name="inrbilling${inrNo}"]`) });
  const dateField = inr.locator('input[name="xml_appointment_date"]');
  let target;

  await s.step('the Date link opens the calendar popup on the current month and Last month steps back', async () => {
    h.assert(await row.count() === 1, 'INR Batch Billing does not list the owned INR row');
    const popup = await s.popup(inr.page(), inr.locator('a', { hasText: 'Date:' }), 'billing-calendar');
    await popup.locator('span.title').first().waitFor({ state: 'visible', timeout: 20000 });
    h.assert((await popup.locator('span.title').first().innerText()).trim() === sql.value("SELECT DATE_FORMAT(CURDATE(), '%Y-%c')"),
      'The calendar popup does not open on the current month');
    await Promise.all([
      popup.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 20000 }),
      popup.locator('a', { hasText: 'Last' }).filter({ has: popup.locator('img[alt="View Last Month"]') }).first().click(),
    ]);
    const title = (await popup.locator('span.title').first().innerText()).trim();
    const previous = sql.value("SELECT DATE_FORMAT(CURDATE() - INTERVAL 1 MONTH, '%Y-%c')");
    h.assert(title === previous, 'Last month did not step the calendar back one month');
    // The first of the previous month is never in the future.
    const [year, month] = previous.split('-');
    target = `${year}-${month.padStart(2, '0')}-01`;
    await popup.locator('td a', { hasText: /^1$/ }).first().click();
    await popup.waitForEvent('close', { timeout: 10000 }).catch(() => {});
    h.assert(popup.isClosed(), 'The calendar popup stayed open after a day was picked');
  });

  await s.step('the chosen day fills the service date field of the INR page', async () => {
    h.assert(await dateField.inputValue() === target, 'The calendar popup did not fill the service date with the chosen day');
  });

  await s.step('Generate INR Batch Billing bills the ticked row on the chosen date', async () => {
    await row.locator(`input[name="inrbilling${inrNo}"]`).check();
    const [response] = await Promise.all([
      context.waitForEvent('response', { timeout: 30000, predicate: r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewInrOnGenINRbilling') }),
      inr.locator('input[type="submit"][value="Generate INR Batch Billing"]').click(),
    ]);
    h.assert(response.status() === 302, `INR generation answered HTTP ${response.status()}`);
    await inr.waitForURL(/\/billing\/CA\/ON\/ViewInrReportINR\?provider_no=all/, { timeout: 30000 });
    await expectValue(sql, `SELECT CONCAT_WS('|', status, DATE(createdatetime)) FROM billinginr WHERE billinginr_no=${inrNo}`,
      `A|${target}`, 'The INR row was not stamped billed with the chosen date');
    const claims = g.headersOf(sql, patient, ['billing_date', 'status', 'pay_program']);
    h.assert(claims.length === 1 && claims[0].billing_date === target && claims[0].pay_program === 'HCP',
      'The INR claim is not dated with the chosen day');
    h.assert(g.itemsOf(sql, claims[0].id).length === 1
      && sql.value(`SELECT service_date FROM billing_on_item WHERE ch1_id=${claims[0].id}`) === target,
    'The INR claim item is not dated with the chosen day');
  });
}

if (require.main === module) runWorkflow('gap-billing-inr-calendar-popup', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
