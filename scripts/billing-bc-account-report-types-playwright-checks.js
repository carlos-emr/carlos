#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Administration ▸ Billing ▸ Accounting Reports: the Invoice, Accounts Receivable and Rejection reports and the
 * payer boxes (MSP, WCB, Private, ICBC). billing-bc-account-reports drives the Write-Off and Payments and Refunds
 * reports; nothing read these three or checked that the boxes filter.
 * User path: Schedule ▸ Administration ▸ Billing ▸ Accounting Reports (billing/CA/BC/ViewBillingAccountReports, in
 * the #dynamic-content iframe) ▸ Report type (Payee and Provider are disabled for these three), Account (the FAKE
 * provider), PDF, today to today, the payer boxes ▸ Create Report (POST billing/CA/BC/createBillingReportAction) ▸ the PDF downloads.
 * Asserted, from each PDF's text (MSPReconcile.createCriteriaString decides which claims a report takes):
 *   - Invoice lists every claim but the deleted one;
 *   - Accounts Receivable lists the open claims: not the deleted one, not the written-off one;
 *   - Rejection lists the rejected claim only;
 *   - with only MSP ticked, Invoice lists the two MSP claims; with only Private, the private one; with only ICBC,
 *     the ICBC one.
 * Fixtures: the owned FAKE- patient made a BC patient, one FAKE provider (provider, OHIP and billing number all the
 * same free 5-digit number, last name = the run marker) with its billing-report row, and five claims for it today,
 * each with its own amount so a report's text shows which it took: MSP open (O), private (P), ICBC rejected (R),
 * MSP deleted (D) and MSP written off (X). Cleanup deletes every BC billing row of the patient, then the
 * billing-report row and the provider, and asserts each is gone.
 * Also checks payment and internal-adjustment balances, negative history totals and a later payment
 * balancing the private claim: the next Invoice refreshes its total and A/R omits it (#3967).
 * PDF text is read with Poppler pdftotext (lib/export-content-helpers.js); without it the check SKIPs.
 */
const fs = require('node:fs');
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const bc = require('./lib/bc-billing-fixture');
const { pdfTextBuffer, requirePoppler } = require('./lib/export-content-helpers');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 60000;
const MENU_ROUTE = '/billing/CA/BC/ViewBillingAccountReports';
// reportprovider.team is 10 characters wide; a longer name is cut without an error under the empty sql_mode.
const TEAM = 'FAKE-types';
// Five claims with binary-spaced amounts: no sum of two or more equals any one, so a report's total or subtotal
// can never stand in for a claim it left out (asserted in amountsAreSumFree).
const CLAIMS = {
  open: { billingtype: 'MSP', status: 'O', code: '00100', amount: '10.01' },
  private: { billingtype: 'Pri', status: 'P', code: '00110', amount: '20.02' },
  rejected: { billingtype: 'ICBC', status: 'R', code: '00120', amount: '40.04' },
  deleted: { billingtype: 'MSP', status: 'D', code: '00130', amount: '80.08' },
  writtenOff: { billingtype: 'MSP', status: 'X', code: '00140', amount: '160.16' },
};

/** Every sum of two or more claim amounts differs from every single amount (in cents). */
function amountsAreSumFree() {
  const cents = Object.values(CLAIMS).map(claim => Math.round(Number(claim.amount) * 100));
  for (let mask = 1; mask < 2 ** cents.length; mask++) {
    const picked = cents.filter((_, i) => mask & (2 ** i));
    if (picked.length < 2) continue;
    const sum = picked.reduce((a, b) => a + b, 0);
    h.assert(!cents.includes(sum), `The claim amounts are not sum-free: ${picked.join('+')} = ${sum}`);
  }
}
const ALL_PAYERS = { showMSP: true, showWCB: true, showPRIV: true, showICBC: true };

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Administration ▸ Billing ▸ Accounting Reports opens and offers the FAKE provider as an account',
  invoice: 'Invoice lists every claim but the deleted one',
  receivable: 'Accounts Receivable lists the open claims, not the deleted or written-off ones',
  rejection: 'Rejection lists the rejected claim only',
  payers: 'the payer boxes filter Invoice to MSP, Private or ICBC claims',
  payments: 'Invoice and Accounts Receivable deduct payments and internal adjustments from the correct claims',
  refreshed: 'a later payment updates Invoice and removes the balanced claim from Accounts Receivable',
};

async function preflight({ sql }) {
  amountsAreSumFree();
  bc.requireBcBillingSchema(sql);
  requirePoppler('pdftotext');
}

/** One FAKE provider with its billing-report row (the form's Payee, Provider and Account lists come from those rows). */
function createReportProvider(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const owned = {};
  s.cleanup(() => {
    if (!owned.no) return;
    sql.execute(`DELETE FROM reportprovider WHERE provider_no=${q(owned.no)} AND team=${q(TEAM)};
      DELETE FROM provider WHERE provider_no=${q(owned.no)} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${q(owned.no)})
      + (SELECT COUNT(*) FROM reportprovider WHERE provider_no=${q(owned.no)})`) === '0', 'The FAKE report provider was not removed');
  });
  h.assert(Number(sql.value("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reportprovider' AND COLUMN_NAME='team'")) >= TEAM.length,
    'reportprovider.team is narrower than the team name this fixture writes');
  for (let i = 0; i < 50 && !owned.no; i++) {
    const n = String(randomInt(10000, 99999));
    if (sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${q(n)} OR ohip_no=${q(n)} OR billing_no=${q(n)})
      + (SELECT COUNT(*) FROM billingmaster WHERE payee_no=${q(n)})`) === '0') owned.no = n;
  }
  h.assert(owned.no, 'No free FAKE provider number');
  // Its OHIP number is its provider number: the Payee list's values are OHIP numbers, and a claim's payee is that number.
  sql.execute(`INSERT INTO provider (provider_no, last_name, first_name, provider_type, specialty, sex, ohip_no, billing_no,
      status, lastUpdateUser, lastUpdateDate)
    VALUES (${q(owned.no)}, ${q(marker)}, 'Reports', 'doctor', '', 'F', ${q(owned.no)}, ${q(owned.no)}, '1', ${q(s.provider)}, NOW());
    INSERT INTO reportprovider (provider_no, team, action, status) VALUES (${q(owned.no)}, ${q(TEAM)}, 'billingreport', '1')`);
  return owned.no;
}

async function workflow(s) {
  const { sql, patient } = s;
  const q = h.sqlString;
  const provider = createReportProvider(s);
  bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  const seededClaims = {};
  for (const [name, claim] of Object.entries(CLAIMS)) {
    const seeded = bc.seedOwnedBcBill({ ...s, provider }, claim);
    seededClaims[name] = seeded;
    // Each report row names its appointment provider (MSPReconcile.getBillsByType), so the bill carries one.
    sql.execute(`UPDATE billing SET apptProvider_no=${q(provider)}, billingtype=${q(claim.billingtype)}
      WHERE billing_no=${seeded.billingNo} AND demographic_no=${patient}`);
  }
  const reportDate = sql.value("SELECT DATE_FORMAT(CURDATE(), '%Y-%m-%d')");

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'bc-account-report-types-admin' });
  let frame;
  /** Fills the form for one report, as a PDF, and returns its text with whitespace collapsed. */
  const report = async (repType, payers) => {
    // The report type first: for these three the page disables Payee and Provider (setUIState), and Account (the
    // bill's provider) is the one filter, so it names the FAKE provider.
    await frame.locator('select[name="repType"]').selectOption(repType);
    h.assert(await frame.locator('select[name="selPayee"]').isDisabled() && await frame.locator('select[name="selProv"]').isDisabled(),
      `The ${repType} form does not disable Payee and Provider`);
    await frame.locator('select[name="selAccount"]').selectOption(provider);
    await frame.locator('select[name="docFormat"]').selectOption('pdf');
    await frame.locator('[name="xml_vdate"]').evaluate((input, date) => { input.value = date; }, reportDate);
    await frame.locator('[name="xml_appointment_date"]').evaluate((input, date) => { input.value = date; }, reportDate);
    for (const [name, on] of Object.entries(payers)) await frame.locator(`input[type="checkbox"][name="${name}"]`).setChecked(on);
    const downloaded = admin.waitForEvent('download', { timeout: TIMEOUT }).catch(() => null);
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && /createBillingReportAction$/.test(new URL(r.url()).pathname),
        { timeout: TIMEOUT }),
      frame.locator('input[type="submit"][name="Submit"]').click(),
    ]);
    h.assert(response.status() === 200, `Create Report (${repType}) answered HTTP ${response.status()}`);
    const download = await downloaded;
    h.assert(download, `Create Report (${repType}) did not download a report`);
    try {
      const bytes = fs.readFileSync(await download.path());
      h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', `The ${repType} report is not a PDF`);
      return pdfTextBuffer(bytes).replace(/\s+/g, ' ');
    } finally {
      await download.delete();
    }
  };
  /** Asserts which of the claims a report's text lists, by their amounts. */
  const lists = (text, label, expected) => {
    for (const [name, claim] of Object.entries(CLAIMS)) {
      const shown = new RegExp(`(^|[^0-9.])${claim.amount.replace('.', '\\.')}([^0-9]|$)`).test(text);
      h.assert(shown === expected.includes(name),
        `${label} ${shown ? 'lists' : 'does not list'} the ${name} claim (${claim.amount})`);
    }
  };

  await s.step(STEP.open, async () => {
    const link = admin.locator(`a.xlink[rel$="${MENU_ROUTE}"]`).first();
    await link.waitFor({ state: 'attached', timeout: TIMEOUT });
    await revealAuditLink(admin, link, 20000);
    const loaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith(MENU_ROUTE), { timeout: TIMEOUT });
    await link.click();
    const response = await loaded;
    h.assert(response.status() === 200, `Accounting Reports answered HTTP ${response.status()}`);
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor({ timeout: TIMEOUT });
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Accounting Reports did not load into the Administration panel');
    await frame.locator('select[name="repType"]').waitFor({ timeout: TIMEOUT });
    h.assert(await frame.locator(`select[name="selAccount"] option[value="${provider}"]`).count() === 1,
      'The Account list does not offer the FAKE provider');
  });

  await s.step(STEP.invoice, async () => {
    lists(await report('REP_INVOICE', ALL_PAYERS), 'Invoice', ['open', 'private', 'rejected', 'writtenOff']);
  });

  await s.step(STEP.receivable, async () => {
    lists(await report('REP_ACCOUNT_REC', ALL_PAYERS), 'Accounts Receivable', ['open', 'private', 'rejected']);
  });

  await s.step(STEP.rejection, async () => {
    lists(await report('REP_REJ', ALL_PAYERS), 'Rejection', ['rejected']);
  });

  await s.step(STEP.payers, async () => {
    const only = box => ({ showMSP: box === 'showMSP', showWCB: false, showPRIV: box === 'showPRIV', showICBC: box === 'showICBC' });
    lists(await report('REP_INVOICE', only('showMSP')), 'Invoice with only MSP', ['open', 'writtenOff']);
    lists(await report('REP_INVOICE', only('showPRIV')), 'Invoice with only Private', ['private']);
    lists(await report('REP_INVOICE', only('showICBC')), 'Invoice with only ICBC', ['rejected']);
  });
  // #3967: the grouped history lookup must include internal adjustments, preserve negative
  // totals, keep bills without history and reload after another payment. The Java tests pin
  // query count; these assertions pin the balances rendered by the installed application.
  const history = (name, amount, paymentType) => sql.execute(`INSERT INTO billing_history
      (billingmaster_no, amount_received, payment_type_id, creation_date)
    VALUES (${seededClaims[name].billingmasterNo},${q(amount)},${paymentType},NOW())`);
  const owes = (text, amount) => h.assert(text.split(/[^0-9.,-]+/)
    .some(token => token.replaceAll(',', '') === amount),
    `The report does not show the expected balance ${amount}`);
  await s.step(STEP.payments, async () => {
    history('private', '7.11', 1);
    history('private', '2.22', 10);
    history('open', '-1.23', 10);
    const invoice = await report('REP_INVOICE', ALL_PAYERS);
    lists(invoice, 'Invoice after payments', ['open', 'private', 'rejected', 'writtenOff']);
    owes(invoice, '10.69');
    owes(invoice, '8.78');
    const receivable = await report('REP_ACCOUNT_REC', ALL_PAYERS);
    owes(receivable, '10.69');
    owes(receivable, '8.78');
    owes(receivable, '40.04');
  });
  await s.step(STEP.refreshed, async () => {
    history('private', '10.69', 2);
    const invoice = await report('REP_INVOICE', ALL_PAYERS);
    lists(invoice, 'Invoice after final payment', ['open', 'private', 'rejected', 'writtenOff']);
    h.assert(!/(^|[^0-9.])10\.69([^0-9]|$)/.test(invoice), 'Invoice retained the old private balance');
    const receivable = await report('REP_ACCOUNT_REC', ALL_PAYERS);
    h.assert(!/(^|[^0-9.])(20\.02|10\.69)([^0-9]|$)/.test(receivable), 'Accounts Receivable retained the balanced private claim');
    owes(receivable, '8.78');
    owes(receivable, '40.04');
  });
  await admin.close();
}

if (require.main === module) runWorkflow('billing-bc-account-report-types', workflow, { openMaster: false, preflight });
module.exports = { workflow, preflight, STEP };
