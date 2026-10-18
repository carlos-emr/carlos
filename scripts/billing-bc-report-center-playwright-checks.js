#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC billing report centre, Billed mode: the report lists exactly the provider's bills in the range that SQL selects,
 * and each row's account number opens that bill. billing-unbilled-report covers the Unbilled mode by typed URL; this
 * check drives the Billed mode from the user's path.
 *
 * User path: Schedule ▸ Report (report/ViewReportindex popup) ▸ "Generate a billing report" (new page,
 * billing/CA/BC/ViewBillingReportCenter) ▸ Billed, Select provider, Service Date-Range ▸ Create Report (GET
 * billing/CA/BC/ViewBillingReportControl) ▸ a row's account number (popup billing/CA/BC/billingView).
 * Asserted:
 *   1. the Report index link opens the BC report centre, which offers the Billed mode and the test provider;
 *   2. Billed lists exactly the bills SQL selects (BillingDao.search_bill_history_daterange: the provider's bills in
 *      the range, not deleted, submitted or settled), each under its account number with the patient and the status
 *      described (Bill MSP, Bill WCB, Bill Patient, Do Not Bill, Capitated Bill);
 *   3. each listed bill's account number opens that bill: its patient, PHN and total;
 *   4. LAST: a login without report read is refused by both the report centre and the report, with the app's
 *      refusal page and none of the bills.
 *
 * Fixtures: the owned FAKE- patient made a BC patient (lib/bc-billing-fixture.js) and nine bills on it billed to the
 * test provider: on one service day picked for this run in 1990-1999, one each in billing status O, W, P, N and H
 * (listed, each with its own total) and B, S and D (not listed); and one O bill on the next day, outside the range.
 * The report's provider list holds only report-provider rows ('billingreport'); when the test provider has none, one
 * is added for the run. It shows in every user's report-provider lists, so the check runs EXCLUSIVE. The throwaway
 * login holds one owned role. Cleanup deletes the bills (registerOwnedBcBillCleanup), the report-provider row it
 * added, the role and the login, and asserts they are gone.
 * Expected (see the manifest notes): on release/2026.08 the refusal step fails until #4940 is fixed (the billing
 * package maps no SecurityException, so the refusal is a 500 error page).
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { requireBcBillingSchema, makeBcPatient, registerOwnedBcBillCleanup, seedOwnedBcBill } = require('./lib/bc-billing-fixture');

const TIMEOUT = 30000;
const CENTRE_ROUTE = '/billing/CA/BC/ViewBillingReportCenter';
const REPORT_ROUTE = '/billing/CA/BC/ViewBillingReportControl';
const BILL_ROUTE = '/billing/CA/BC/billingView';
// What the refusal step's login needs to log in to the schedule: no report read.
const GRANTS = [['_appointment', 'r'], ['_msg', 'r'], ['_demographic', 'r'], ['_search', 'r']];
// One bill per billing status. The report lists every status but D (deleted), S (settled) and B (submitted), and
// describes the listed ones by status; the out-of-range bill is billed the day after the report's range.
const BILLS = [
  { status: 'O', billingtype: 'MSP', amount: '21.00', describes: 'Bill MSP' },
  { status: 'W', billingtype: 'WCB', amount: '22.00', describes: 'Bill WCB' },
  { status: 'P', billingtype: 'Pri', amount: '23.00', describes: 'Bill Patient' },
  { status: 'N', billingtype: 'MSP', amount: '24.00', describes: 'Do Not Bill' },
  { status: 'H', billingtype: 'MSP', amount: '25.00', describes: 'Capitated Bill' },
  { status: 'B', billingtype: 'MSP', amount: '26.00' },
  { status: 'S', billingtype: 'MSP', amount: '27.00' },
  { status: 'D', billingtype: 'MSP', amount: '28.00' },
  { status: 'O', billingtype: 'MSP', amount: '29.00', nextDay: true },
];
const STEP = {
  open: 'Schedule ▸ Report ▸ Generate a billing report opens the BC report centre with the Billed mode and the provider',
  billed: 'Billed lists exactly the provider\'s bills in the range that are not deleted, submitted or settled',
  links: 'each listed bill\'s account number opens that bill',
  refusal: 'a login without report read is refused by the report centre and the report, and sees none of the bills',
};

async function preflight({ sql }) {
  requireBcBillingSchema(sql);
}

async function workflow(s) {
  const { sql, marker, provider, config, recorder } = s;
  const q = h.sqlString;
  const phn = makeBcPatient(s);
  registerOwnedBcBillCleanup(s);

  // A service day in the 1990s for this run, and the day after it.
  const day = sql.value(`SELECT DATE_FORMAT(DATE_ADD('1990-01-01', INTERVAL ${randomInt(0, 3650)} DAY), '%Y-%m-%d')`);
  const nextDay = sql.value(`SELECT DATE_FORMAT(DATE_ADD(${q(day)}, INTERVAL 1 DAY), '%Y-%m-%d')`);
  const bills = BILLS.map(bill => {
    const seeded = seedOwnedBcBill(s, { billingtype: bill.billingtype, serviceDate: bill.nextDay ? nextDay : day, amount: bill.amount });
    sql.execute(`UPDATE billing SET status=${q(bill.status)} WHERE billing_no=${Number(seeded.billingNo)} AND demographic_no=${s.patient}`);
    return { ...bill, ...seeded };
  });
  const patientName = sql.value(`SELECT CONCAT(last_name, ', ', first_name) FROM demographic WHERE demographic_no=${s.patient}`);

  // The report's provider list is the clinic's report-provider rows for 'billingreport'.
  let reportProviderId = null;
  s.cleanup(() => {
    if (!reportProviderId) return;
    sql.execute(`DELETE FROM reportprovider WHERE id=${reportProviderId} AND provider_no=${q(provider)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM reportprovider WHERE id=${reportProviderId}`) === '0', 'The report-provider row was not removed');
  });
  if (sql.value(`SELECT COUNT(*) FROM reportprovider WHERE provider_no=${q(provider)} AND action='billingreport' AND status<>'D'`) === '0') {
    reportProviderId = h.insertId(sql, `INSERT INTO reportprovider (provider_no, team, action, status)
      VALUES (${q(provider)}, '', 'billingreport', 'A')`, 'report-provider row');
  }

  /** The bills SQL selects for the report: the provider's, in the range, not deleted, settled or submitted. */
  const expected = new Set(sql.rows(`SELECT billing_no FROM billing WHERE provider_no=${q(provider)}
      AND billing_date BETWEEN ${q(day)} AND ${q(day)} AND status NOT IN ('D','S','B') AND demographic_no<>0`).map(([id]) => id));

  let centre;
  let report;
  let rows = [];
  await s.step(STEP.open, async () => {
    const { page: index } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a[onclick*="ViewReportindex"]').first(),
      { context: s.context, recorder, label: 'bc-report-centre-index' });
    const link = index.locator(`a[href*="${CENTRE_ROUTE}"]`).first();
    await link.waitFor({ timeout: TIMEOUT });
    const opened = s.context.waitForEvent('page', { timeout: TIMEOUT });
    await link.click();
    centre = await opened;
    await centre.waitForLoadState('domcontentloaded');
    h.assert(h.pathOnly(centre.url()).endsWith(CENTRE_ROUTE), `The link opened ${h.pathOnly(centre.url())}`);
    await centre.locator('input[name="reportAction"][value="billed"]').waitFor({ timeout: TIMEOUT });
    h.assert(await centre.locator(`select[name="providerview"] option[value="${provider}"]`).count() === 1,
      'The report centre does not offer the test provider');
    await index.close();
  });

  await s.step(STEP.billed, async () => {
    await centre.locator('input[name="reportAction"][value="billed"]').check();
    await centre.locator('select[name="providerview"]').selectOption(provider);
    await centre.locator('input[name="xml_vdate"]').fill(day);
    await centre.locator('input[name="xml_appointment_date"]').fill(day);
    const [response] = await Promise.all([
      centre.waitForResponse(r => h.pathOnly(r.url()).endsWith(REPORT_ROUTE) && r.request().resourceType() === 'document', { timeout: TIMEOUT }),
      centre.locator('input[type="submit"][name="Submit"]').click(),
    ]);
    h.assert(response.status() === 200, `Create Report answered HTTP ${response.status()}`);
    await centre.waitForLoadState('domcontentloaded');
    report = centre;
    // Each listed bill is a row of five cells: service date, time, patient, description, and the account number link.
    rows = await report.locator('table[border="2"] tr').evaluateAll(trs => trs
      .map(tr => [...tr.querySelectorAll('td')].map(td => td.textContent.replace(/\s+/g, ' ').trim()))
      .filter(cells => cells.length === 5));
    const listed = new Set(rows.map(cells => cells[4]));
    const missing = [...expected].filter(id => !listed.has(id));
    const extra = [...listed].filter(id => !expected.has(id));
    h.assert(!missing.length && !extra.length && listed.size === rows.length,
      `Billed does not list the bills SQL selects: missing ${JSON.stringify(missing)}, extra ${JSON.stringify(extra)}`);
    for (const bill of bills) {
      const row = rows.find(cells => cells[4] === bill.billingNo);
      if (!bill.describes) {
        h.assert(!row, `Billed lists the bill in status ${bill.status}${bill.nextDay ? ' on the next day' : ''}`);
        continue;
      }
      h.assert(row, `Billed does not list the bill in status ${bill.status}`);
      h.assert(row[0] === day && row[2] === `${marker},Workflow` && row[3].startsWith(bill.describes),
        `Billed shows the status ${bill.status} bill as ${JSON.stringify(row.slice(0, 4))}`);
    }
  });

  await s.step(STEP.links, async () => {
    for (const bill of bills.filter(b => b.describes)) {
      const link = report.locator('table[border="2"] tr td a', { hasText: new RegExp(`^\\s*${bill.billingNo}\\s*$`) });
      const { page: view } = await ui.clickOpensPopupOrNavigates(report, link, { context: s.context, recorder, label: `bc-report-centre-bill-${bill.status}` });
      await view.waitForLoadState('domcontentloaded');
      const url = new URL(view.url());
      h.assert(h.pathOnly(view.url()).endsWith(BILL_ROUTE) && url.searchParams.get('billing_no') === bill.billingNo,
        `The account number ${bill.billingNo} opened ${h.pathOnly(view.url())}?billing_no=${url.searchParams.get('billing_no')}`);
      const text = (await view.locator('body').innerText()).replace(/\s+/g, ' ');
      h.assert(text.includes(patientName) && text.includes(phn) && text.includes(bill.amount),
        `The bill ${bill.billingNo} does not show its patient, PHN and total ${bill.amount}`);
      await view.close();
    }
  });

  // Last: the refusal (a failure here leaves the report's steps standing).
  await s.step(STEP.refusal, async () => {
    // secObjPrivilege.roleUserGroup holds 30 characters: the 23-character marker plus "-norpt" fits.
    const role = `${marker}-norpt`;
    const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
    let roleOwned = false;
    s.cleanup(() => {
      fixture.cleanup();
      if (roleOwned) sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(role)}; DELETE FROM secRole WHERE role_name=${q(role)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)})
        + (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)})`) === '0', 'The owned role was not removed');
    });
    h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}`) === '0', 'The owned role already exists');
    roleOwned = true;
    sql.execute(`INSERT INTO secRole(role_name,description) VALUES(${q(role)},'Owned schedule reader');
      INSERT INTO secObjPrivilege(roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES ${GRANTS.map(([object, privilege]) => `(${q(role)},${q(object)},${q(privilege)},0,${q(provider)})`).join(',')}`);
    fixture.create({ roleNames: [role] });

    const context = await h.newContext(s.context.browser(), config);
    s.cleanup(() => context.close().catch(() => {}));
    context.setDefaultTimeout(TIMEOUT);
    context.on('page', page => h.wireStrictPage(page, 'no-report-reader', recorder));
    await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'no-report-reader-login' });
    const page = await context.newPage();
    const query = `reportAction=billed&providerview=${encodeURIComponent(provider)}&xml_vdate=${day}&xml_appointment_date=${day}`;
    for (const [route, params] of [[CENTRE_ROUTE, 'displaymode=billreport'], [REPORT_ROUTE, query]]) {
      const response = await h.gotoApp(page, config.baseUrl, `${route}?${params}`);
      const body = (await page.locator('body').innerText()).replace(/\s+/g, ' ');
      h.assert(response && response.status() === 403 && /Security Exception|insufficient privileges/i.test(body),
        `${route} without report read answered HTTP ${response && response.status()}: ${body.slice(0, 160)}`);
      h.assert(!bills.some(bill => body.includes(bill.billingNo) && body.includes(marker)), `${route} without report read shows the bills`);
    }
    // The refusals are the point of this step: forget the strict recorder's notes of the 403 pages.
    recorder.badResponses.splice(0, recorder.badResponses.length,
      ...recorder.badResponses.filter(e => !(e.status === 403 && [CENTRE_ROUTE, REPORT_ROUTE].some(r => h.pathOnly(e.url || '').endsWith(r)))));
    recorder.consoleIssues.splice(0, recorder.consoleIssues.length,
      ...recorder.consoleIssues.filter(e => !/status of 403/.test(e.text)));
    await page.close();
  });
  await centre.close();
}

if (require.main === module) runWorkflow('billing-bc-report-center', workflow, { openMaster: false, preflight });
module.exports = { workflow, preflight, STEP };
