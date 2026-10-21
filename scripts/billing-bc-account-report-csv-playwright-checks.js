#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Administration ▸ Billing ▸ Accounting Reports as a spreadsheet: the Invoice, A/R and Rejection reports' CSV rows
 * are the claims SQL selects for the same range. billing-bc-account-report-types reads these three reports' PDFs and
 * their payer boxes, and billing-bc-account-reports the Write-Off and Payments and Refunds PDFs; this check reads the
 * three reports' spreadsheets against SQL.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Accounting Reports (billing/CA/BC/ViewBillingAccountReports, in the
 * #dynamic-content iframe) ▸ Report type, Account ALL, Format Spread Sheet, From/To dates ▸ Create Report (POST
 * billing/CA/BC/createBillingReportAction) ▸ the CSV downloads.
 * Asserted:
 *   1. the menu item opens the report form, which offers the Spread Sheet format;
 *   2. the Invoice spreadsheet lists exactly the range's claims that are not deleted;
 *   3. the A/R spreadsheet lists exactly the range's claims that are neither deleted nor written off;
 *   4. a login with report read but not billing read opens the form, and Create Report gets the app's refusal and
 *      no file;
 *   5. LAST: the Rejection spreadsheet lists the range's rejected claims, each under its full claim number and with
 *      the patient's birth date.
 * Each CSV row names its claim as "<billing_no>-<billingmaster_no>"; the expected set is SQL over billing and
 * billingmaster for the same day, statuses and payers (all four payer boxes ticked).
 *
 * Fixtures: the owned FAKE- patient made a BC patient (lib/bc-billing-fixture.js), and six MSP claims on it billed to
 * the test provider: one each in status O (not submitted), B (submitted), R (rejected), X (written off) and D
 * (deleted) on one service day picked for this run in 1990-1999, and one O claim on the next day, outside the range;
 * the rejected claim's remittance and refusal line (teleplanS21 + teleplanC12, filename = the run marker).
 * Account ALL with a day no other check bills keeps the expected rows to the run's own, without adding a provider to
 * the clinic's lists. The throwaway login holds one owned role. Cleanup deletes the claims (registerOwnedBcBillCleanup),
 * the remittance rows, the role and the login, and asserts they are gone.
 * Expected (see the manifest notes): on release/2026.08 every Create Report answers HTTP 404 until #4805 (#4801) merges.
 */
const { randomInt } = require('node:crypto');
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { requireBcBillingSchema, makeBcPatient, registerOwnedBcBillCleanup, seedOwnedBcBill } = require('./lib/bc-billing-fixture');

const TIMEOUT = 30000;
const MENU_ROUTE = '/billing/CA/BC/ViewBillingAccountReports';
const REPORT_ROUTE = '/billing/CA/BC/createBillingReportAction';
// What the role step's login needs to log in to the schedule and open the form: report read, not billing read.
const GRANTS = [['_report', 'r'], ['_appointment', 'r'], ['_msg', 'r'], ['_demographic', 'r'], ['_search', 'r']];
// The claims, one per billingmaster status; the out-of-range one is billed the day after the report's range.
const CLAIMS = [
  { label: 'not submitted', status: 'O' },
  { label: 'submitted', status: 'B' },
  { label: 'rejected', status: 'R' },
  { label: 'written off', status: 'X' },
  { label: 'deleted', status: 'D' },
  { label: 'next day', status: 'O', nextDay: true },
];
// The billingmaster statuses each report type selects (MSPReconcile: D deleted, X bad debt, R rejected).
const REPORTS = {
  REP_INVOICE: { what: 'the Invoice spreadsheet', where: "bm.billingstatus <> 'D'" },
  REP_ACCOUNT_REC: { what: 'the A/R spreadsheet', where: "bm.billingstatus NOT IN ('D','X')" },
  REP_REJ: { what: 'the Rejection spreadsheet', where: "bm.billingstatus = 'R'" },
};
const STEP = {
  open: 'Accounting Reports opens from Administration ▸ Billing and offers the Spread Sheet format',
  invoice: 'the Invoice spreadsheet lists exactly the range\'s claims that are not deleted',
  accountsReceivable: 'the A/R spreadsheet lists exactly the range\'s claims that are neither deleted nor written off',
  role: 'a login with report read but not billing read gets the app\'s refusal and no report',
  rejection: 'the Rejection spreadsheet lists each rejected claim under its claim number, with the patient\'s birth date',
};

async function preflight({ sql }) {
  requireBcBillingSchema(sql);
}

/** Splits CSV text into rows of fields (RFC 4180 quoting). */
function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') quoted = false;
      else field += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field); rows.push(row); row = []; field = '';
    } else field += c;
  }
  if (field || row.length) { row.push(field); rows.push(row); }
  return rows;
}

async function workflow(s) {
  const { sql, marker, provider, config, recorder } = s;
  const q = h.sqlString;
  makeBcPatient(s);
  registerOwnedBcBillCleanup(s);

  // A service day in the 1990s for this run, and the day after it.
  const day = sql.value(`SELECT DATE_FORMAT(DATE_ADD('1990-01-01', INTERVAL ${randomInt(0, 3650)} DAY), '%Y-%m-%d')`);
  const nextDay = sql.value(`SELECT DATE_FORMAT(DATE_ADD(${q(day)}, INTERVAL 1 DAY), '%Y-%m-%d')`);
  const claims = CLAIMS.map(claim => ({
    ...claim,
    ...seedOwnedBcBill(s, { status: claim.status, serviceDate: claim.nextDay ? nextDay : day, amount: '23.00' }),
  }));
  // The reports print the patient's birth date and the appointment provider's initials from the billing row.
  sql.execute(`UPDATE billing SET dob='19800102', apptProvider_no=${q(provider)}
    WHERE billing_no IN (${claims.map(c => Number(c.billingNo)).join(',')}) AND demographic_no=${s.patient}`);
  const ownedKeys = new Set(claims.map(c => `${c.billingNo}-${c.billingmasterNo}`));

  // A rejected claim arrives with its refusal: a remittance (teleplanS21) and its refusal line (teleplanC12), which the
  // Rejection report reads for the explanation codes and the rejection date. The line names the claim by its
  // billingmaster number, zero-padded to the 7-character office folio claim number.
  const rejected = claims.find(c => c.status === 'R');
  const remit = { s21: null, c12: null };
  s.cleanup(() => {
    if (remit.c12) sql.execute(`DELETE FROM teleplanC12 WHERE c12_id=${remit.c12} AND filename=${q(marker)}`);
    if (remit.s21) sql.execute(`DELETE FROM teleplanS21 WHERE s21_id=${remit.s21} AND filename=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM teleplanC12 WHERE filename=${q(marker)})
      + (SELECT COUNT(*) FROM teleplanS21 WHERE filename=${q(marker)})`) === '0', 'The owned remittance rows were not removed');
  });
  const payDay = day.replace(/-/g, '');
  remit.s21 = h.insertId(sql, `INSERT INTO teleplanS21 (filename, t_datacenter, t_dataseq, t_payment, t_linecode, t_payeeno,
      t_mspctlno, t_payeename, t_amtbilled, t_amtpaid, t_balancefwd, t_cheque, t_newbalance, t_filler, status)
    VALUES (${q(marker)}, '00000', '0000001', ${q(payDay)}, '1', '00000', '000000', ${q(marker.slice(0, 25))}, '000002300',
      '000000000', '000000000', '000000000', '000000000', '', 'O')`, 'remittance');
  remit.c12 = h.insertId(sql, `INSERT INTO teleplanC12 (s21_id, filename, t_datacenter, t_dataseq, t_payeeno, t_practitioner_no,
      t_exp1, t_officefolioclaimno, status)
    VALUES (${remit.s21}, ${q(marker)}, '00000', '0000001', '00000', '00000', 'AA',
      ${q(String(rejected.billingmasterNo).padStart(7, '0'))}, 'O')`, 'refusal line');

  /** The claims SQL selects for one report type on the run's day: every patient's, with all four payer types. */
  const expectedKeys = repType => new Set(sql.rows(`SELECT CONCAT(b.billing_no, '-', bm.billingmaster_no)
      FROM billing b JOIN billingmaster bm ON bm.billing_no = b.billing_no
      JOIN provider p ON p.provider_no = b.provider_no JOIN demographic d ON d.demographic_no = b.demographic_no
    WHERE TO_DAYS(bm.service_date) = TO_DAYS(${q(day)}) AND ${REPORTS[repType].where}`).map(([key]) => key));

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder, label: 'bc-account-report-csv-admin' });
  let frame;

  /** Fills the form for one report as a spreadsheet and returns the HTTP answer and the downloaded bytes. */
  const createReport = async (target, page, repType) => {
    // The report type first: the page enables Provider and Payee only for the types that use them.
    await target.locator('select[name="repType"]').selectOption(repType);
    await target.locator('select[name="selAccount"]').selectOption('ALL');
    await target.locator('select[name="docFormat"]').selectOption('csv');
    for (const box of ['showMSP', 'showWCB', 'showPRIV', 'showICBC']) await target.locator(`input[name="${box}"]`).check();
    await target.locator('[name="xml_vdate"]').evaluate((input, date) => { input.value = date; }, day);
    await target.locator('[name="xml_appointment_date"]').evaluate((input, date) => { input.value = date; }, day);
    const downloaded = page.waitForEvent('download', { timeout: TIMEOUT }).catch(() => null);
    const [response] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'POST' && /createBillingReportAction$/.test(h.pathOnly(r.url())),
        { timeout: TIMEOUT }),
      target.locator('input[type="submit"][name="Submit"]').click(),
    ]);
    return { response, downloaded };
  };

  /** One report as a spreadsheet: it is a CSV, and its claim rows are exactly the ones SQL selects. */
  const spreadsheet = async repType => {
    const { response, downloaded } = await createReport(frame, admin, repType);
    h.assert(response.status() === 200, `${REPORTS[repType].what}: Create Report answered HTTP ${response.status()} `
      + `(${h.pathOnly(response.url())})`);
    const download = await downloaded;
    h.assert(download, `${REPORTS[repType].what} did not download`);
    let bytes;
    try {
      bytes = fs.readFileSync(await download.path());
    } finally {
      await download.delete();
    }
    const type = response.headers()['content-type'] || '';
    // CreateBillingReport2Action.cfgHeader sends csv as application/csv.
    h.assert(/^application\/csv\b/i.test(type), `${REPORTS[repType].what} came back as ${JSON.stringify(type)}`);
    const text = bytes.toString('utf8');
    h.assert(!text.startsWith('%PDF-') && !/<html|<!doctype/i.test(text.slice(0, 512)), `${REPORTS[repType].what} is a PDF or an HTML page`);
    // A claim's row carries "<billing_no>-<billingmaster_no>"; other fields of that shape (a phone number) are not claims.
    const listed = new Set();
    for (const field of parseCsv(text).flat().map(f => f.trim())) {
      const m = /^(\d+)-(\d+)$/.exec(field);
      if (m && sql.value(`SELECT COUNT(*) FROM billingmaster WHERE billing_no=${m[1]} AND billingmaster_no=${m[2]}`) === '1') {
        listed.add(field);
      }
    }
    const expected = expectedKeys(repType);
    const label = key => {
      const claim = claims.find(c => `${c.billingNo}-${c.billingmasterNo}` === key);
      return claim ? `${key} (${claim.label})` : key;
    };
    const missing = [...expected].filter(key => !listed.has(key)).map(label);
    const extra = [...listed].filter(key => !expected.has(key)).map(label);
    h.assert(!missing.length && !extra.length, `${REPORTS[repType].what} does not list the claims SQL selects: `
      + `missing ${JSON.stringify(missing)}, extra ${JSON.stringify(extra)}`);
    // The fixture's own claims are in that set as the statuses say, so a broken seed can't make the comparison empty.
    h.assert([...ownedKeys].some(key => expected.has(key)), `${REPORTS[repType].what}: SQL selects none of the run's claims`);
    return parseCsv(text);
  };

  await s.step(STEP.open, async () => {
    const link = admin.locator(`a.xlink[rel$="${MENU_ROUTE}"]`).first();
    await link.waitFor({ state: 'attached', timeout: TIMEOUT });
    await revealAuditLink(admin, link, 20000);
    const loaded = admin.waitForResponse(r => h.pathOnly(r.url()).endsWith(MENU_ROUTE), { timeout: TIMEOUT });
    await link.click();
    const response = await loaded;
    h.assert(response.status() === 200, `Accounting Reports answered HTTP ${response.status()}`);
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor({ timeout: TIMEOUT });
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'Accounting Reports did not load into the Administration panel');
    await frame.locator('select[name="repType"]').waitFor({ timeout: TIMEOUT });
    h.assert(await frame.locator('select[name="docFormat"] option[value="csv"]').count() === 1,
      'The report form does not offer the Spread Sheet format');
  });

  await s.step(STEP.invoice, () => spreadsheet('REP_INVOICE'));
  await s.step(STEP.accountsReceivable, () => spreadsheet('REP_ACCOUNT_REC'));

  await s.step(STEP.role, async () => {
    // secObjPrivilege.roleUserGroup holds 30 characters: the 23-character marker plus "-report" fits.
    const role = `${marker}-report`;
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
    sql.execute(`INSERT INTO secRole(role_name,description) VALUES(${q(role)},'Owned report reader');
      INSERT INTO secObjPrivilege(roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES ${GRANTS.map(([object, privilege]) => `(${q(role)},${q(object)},${q(privilege)},0,${q(provider)})`).join(',')}`);
    fixture.create({ roleNames: [role] });

    const context = await h.newContext(s.context.browser(), config);
    s.cleanup(() => context.close().catch(() => {}));
    context.setDefaultTimeout(TIMEOUT);
    context.on('page', page => h.wireStrictPage(page, 'report-reader', recorder));
    await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'report-reader-login' });
    const page = await context.newPage();
    await h.gotoApp(page, config.baseUrl, MENU_ROUTE);
    await page.locator('select[name="repType"]').waitFor({ timeout: TIMEOUT });
    const { response, downloaded } = await createReport(page, page, 'REP_INVOICE');
    const body = (await response.text()).replace(/\s+/g, ' ');
    h.assert(response.status() === 403 && /Security Exception|insufficient privileges/i.test(body),
      `Create Report without billing read answered HTTP ${response.status()}: ${body.slice(0, 160)}`);
    h.assert(!(await downloaded), 'Create Report without billing read downloaded a report');
    // The refusal is the point of this step: forget the strict recorder's note of the 403 page.
    recorder.badResponses.splice(0, recorder.badResponses.length,
      ...recorder.badResponses.filter(e => !(e.status === 403 && h.pathOnly(e.url || '').endsWith(REPORT_ROUTE))));
    recorder.consoleIssues.splice(0, recorder.consoleIssues.length,
      ...recorder.consoleIssues.filter(e => !/status of 403/.test(e.text)));
    await page.close();
  });

  // Last: the Rejection report's claim cells (a failure here leaves the other reports' steps standing).
  await s.step(STEP.rejection, async () => {
    const rows = await spreadsheet('REP_REJ');
    // Each claim's block is followed by a row that labels the patient's birth date "DOB:"; the date is the next field.
    const dobRow = rows.find(row => row.some(f => f.trim() === 'DOB:'));
    h.assert(dobRow, 'The Rejection spreadsheet has no DOB row for the rejected claim');
    const dob = (dobRow.slice(dobRow.findIndex(f => f.trim() === 'DOB:') + 1).find(f => f.trim()) || '').trim();
    // The billing row's birth date is 1980-01-02, whatever the date format.
    h.assert(/^(1\/2\/80|1980-01-02|02-Jan-(19)?80|Jan 2, 1980)\b/.test(dob),
      `The Rejection spreadsheet prints the patient's birth date (1980-01-02) as ${JSON.stringify(dob)}`);
  });
  await admin.close();
}

if (require.main === module) runWorkflow('billing-bc-account-report-csv', workflow, { openMaster: false, preflight });
module.exports = { workflow, preflight, parseCsv, STEP };
