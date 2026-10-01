#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Date-range inclusivity of the Ontario billing reports (wave 6, boundary values, Part 2).
 * User path: Schedule > Administration > Billing > Invoice Reports (ViewBillStatus) > start / end date >
 * Create Report; Administration > Billing > Simulation OHIP Diskette (ViewBillingOHIPsimulation) > provider +
 * start / end date > Create Report.
 * Asserts, with open claims dated on 2004-02-28, the leap day 2004-02-29, 2004-03-01 and the edges of a
 * 2004-03-08..2004-03-12 window: the Invoice Reports list a claim dated exactly on the start date and exactly
 * on the end date and a single-day window on the leap day lists only that day; the OHIP simulation does the
 * same (a claim dated on the Service Date Start must be previewed, as the Service Date End one is).
 * Fixtures: lib-style billing fixture of billing-on-ohip-simulation-report (throwaway billing provider with own
 * OHIP and group numbers, the owned patient with a synthetic HIN, seven open claims); cleanup removes them and
 * asserts they are gone. The simulation writes nothing; no claim file is generated or sent.
 * Implements the wave-6 "boundary values" pattern, Part 2 (date range inclusivity, leap day).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture, openAdministration } = require('./billing-on-ohip-simulation-report-playwright-checks');
const { runInvoiceReport } = require('./billing-on-invoice-third-party-playwright-checks');

const DAYS = { feb28: '2004-02-28', leap: '2004-02-29', mar01: '2004-03-01', before: '2004-03-07', start: '2004-03-08', end: '2004-03-12', after: '2004-03-13' };

function scheduleFee(sql, code) {
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)} ORDER BY billingservice_date DESC LIMIT 1`);
  if (!/^\d+(\.\d+)?$/.test(fee) || Number(fee) <= 0) throw new h.SkipCheck(`service code ${code} has no positive fee`);
  return Number(fee).toFixed(2);
}

async function fillDate(scope, page, selector, value) {
  await scope.locator(selector).fill(value);
  await scope.locator('xpath=ancestor-or-self::*[.//h3][1]').locator('h3').first().click();
  await page.locator('.flatpickr-calendar.open').first().waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
  h.assert(await scope.locator(selector).inputValue() === value, `${selector} did not keep ${value}`);
}

/** Administration > Billing > <menu>: the page the menu hosts in the #dynamic-content iframe. */
async function openAdminFrame(admin, route, ready) {
  const link = admin.locator(`a[rel$="${route}"]`).first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor({ timeout: 20000 });
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, `${route} did not load in the administration frame`);
  await frame.locator(ready).first().waitFor({ state: 'visible', timeout: 20000 });
  return frame;
}

async function workflow(s) {
  const { sql } = s;
  const owned = createBillingFixture(s);
  const fee = scheduleFee(sql, 'A007A');
  const claim = {};
  for (const [name, date] of Object.entries(DAYS)) {
    claim[name] = owned.addClaim({ tag: name.toUpperCase(), date, status: 'O', items: [{ code: 'A007A', fee }] });
  }
  const link = (scope, id) => scope.locator(`a[onclick*="BillingONCorrection?billing_no=${id}'"], a[onclick*="BillingONCorrection?billing_no=${id}\\""]`);
  const listed = async (scope) => {
    const names = [];
    for (const [name, c] of Object.entries(claim)) if (await link(scope, c.id).count() > 0) names.push(name);
    return names.sort().join(',');
  };
  const admin = await openAdministration(s);

  async function invoiceWindow(frame, start, end) {
    await runInvoiceReport(admin, frame, { date: start, endDate: end, statusId: 'statusTypeAll', billingProvider: owned.providerNo, demographic: s.patient });
    await h.assertNotErrorPage(frame, 'invoice report');
    return listed(frame);
  }
  let frame;
  await s.step('Invoice Reports list a claim dated exactly on the start date and exactly on the end date', async () => {
    frame = await openAdminFrame(admin, '/billing/CA/ON/ViewBillStatus', 'form[name="serviceform"]');
    const got = await invoiceWindow(frame, DAYS.start, DAYS.end);
    h.assert(got === 'end,start', `Window ${DAYS.start}..${DAYS.end} listed [${got}], expected [end,start] (a claim on the end or the start date is missing, or one outside it was listed)`);
  });
  await s.step('Invoice Reports: a single-day window on the leap day lists only that day, and 31 Dec to 1 Jan style windows keep both edges', async () => {
    const leap = await invoiceWindow(frame, DAYS.leap, DAYS.leap);
    h.assert(leap === 'leap', `The single-day window ${DAYS.leap} listed [${leap}], expected [leap]`);
    const span = await invoiceWindow(frame, DAYS.feb28, DAYS.mar01);
    h.assert(span === 'feb28,leap,mar01', `Window ${DAYS.feb28}..${DAYS.mar01} listed [${span}], expected [feb28,leap,mar01]`);
  });

  async function simulate(start, end) {
    const menu = admin.locator('a.contentLink[href*="/billing/CA/ON/ViewBillingOHIPsimulation"]').first();
    await menu.waitFor({ state: 'attached', timeout: 20000 });
    await revealAuditLink(admin, menu, 20000);
    const panel = await ui.clickInjectsPanel(admin, menu, { marker: '#dynamic-content form#serviceform' });
    const form = panel.locator('form#serviceform');
    await form.locator('select[name="providers"]').selectOption(owned.providerNo);
    await fillDate(form, admin, '#xml_vdate', start);
    await fillDate(form, admin, '#xml_appointment_date', end);
    await Promise.all([
      admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewBillingOHIPsimulation')
        && new URL(r.url()).searchParams.get('submit') === 'Create Report', { timeout: 60000 }),
      form.locator('button[type="submit"][value="Create Report"]').click(),
    ]);
    await admin.locator('#dynamic-content td', { hasText: /RECORDS PROCESSED|No data|0 RECORDS/i }).first().waitFor({ timeout: 30000 }).catch(() => {});
    await admin.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    return listed(admin.locator('#dynamic-content'));
  }

  await s.step('OHIP simulation previews the claim dated on the Service Date End (and nothing past it)', async () => {
    const got = await simulate(DAYS.before, DAYS.end);
    h.assert(got.split(',').includes('end') && !got.split(',').includes('after'),
      `Window ${DAYS.before}..${DAYS.end} previewed [${got}]; the claim on the end date must be included and the day after excluded`);
  });
  await s.step('OHIP simulation previews a claim dated exactly on the Service Date Start, and a single-day window finds its day', async () => {
    const got = await simulate(DAYS.start, DAYS.end);
    h.assert(got === 'end,start', `Window ${DAYS.start}..${DAYS.end} previewed [${got}], expected [end,start]: the claim dated on the Service Date Start is left out `
      + '(BillingONCHeader1DaoImpl.findByProviderStatusAndDateRange uses billingDate > from)');
    const leap = await simulate(DAYS.leap, DAYS.leap);
    h.assert(leap === 'leap', `The single-day window ${DAYS.leap} previewed [${leap}], expected [leap]`);
  });
}

if (require.main === module) runWorkflow('boundary-date-billing-claims', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
