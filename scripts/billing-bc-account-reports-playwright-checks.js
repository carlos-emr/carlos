#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * BC Administration ▸ Billing ▸ Accounting Reports: the Write-Off, Payments and Refunds, and Payments and Refunds
 * summary reports, as PDFs, and the summary as a spreadsheet, for one provider and one payee. Nothing else drives
 * this page or reads what its reports print.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Accounting Reports (billing/CA/BC/ViewBillingAccountReports, in
 * the #dynamic-content iframe) ▸ Report type, Provider, Payee, Account (ALL), Format, From/To dates ▸ Create Report
 * (POST billing/CA/BC/createBillingReportAction) ▸ the report downloads.
 * Asserted: the menu item opens the report form; the Write-Off PDF names the chosen provider and payee, each on its
 * own label; the Payments and Refunds PDF and its summary PDF print the chosen provider's and payee's initials and
 * total the seeded $33.70 payment; the summary as a spreadsheet downloads.
 *
 * Fixtures: the owned FAKE- patient (lib/workflow-session.js), two FAKE providers inserted by SQL (provider "Quinn"
 * and payee "Yara", last name = the run marker, each with its own free 5-digit provider/OHIP number and a
 * billing-report row), and one MSP bill on the patient for them: a written-off (bad debt) billingmaster line and the
 * teleplanS00 payment line for it, both dated today. Cleanup deletes exactly those rows by id and marker and asserts
 * they are gone. The PDFs are read in memory; each download's temporary file is deleted once read.
 *
 * PDF text is read with Poppler pdftotext (lib/export-content-helpers.js); without it the check SKIPs.
 * Expected (see the manifest notes): on release and develop the first report step fails with HTTP 404 until #4805
 * (#4801's release fix) merges.
 */
const { randomInt } = require('node:crypto');
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { requirePoppler, pdfTextBuffer } = require('./lib/export-content-helpers');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 60000;
const MENU_ROUTE = '/billing/CA/BC/ViewBillingAccountReports';

/** The step labels the manifest's notes name; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Administration ▸ Billing ▸ Accounting Reports opens the report form',
  writeOff: 'the Write-Off PDF names the chosen provider and payee',
  payRef: "the Payments and Refunds PDF prints the chosen provider's and payee's initials",
  payRefSummary: "the Payments and Refunds summary PDF prints the chosen provider's and payee's initials",
  spreadsheet: 'the Payments and Refunds summary downloads as a spreadsheet',
};

async function preflight({ sql }) {
  if (sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='teleplanS00'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required');
  }
  requirePoppler('pdftotext');
}

/** One provider and one payee, the patient's bill for them, a write-off line and its payment line. */
function seedReportRows(s) {
  const { sql, marker, patient } = s;
  const q = h.sqlString;
  const team = marker.slice(-10);
  // 5 digits: the MSP payee and practitioner numbers (billingmaster.payee_no, teleplanS00) are varchar(5).
  const taken = [];
  const free = () => {
    for (let i = 0; i < 50; i++) {
      const n = String(randomInt(10000, 99999));
      if (!taken.includes(n) && sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(n)} OR ohip_no=${q(n)}`) === '0') {
        taken.push(n);
        return n;
      }
    }
    throw new Error('No free FAKE provider number');
  };
  const rows = { provider: free(), payee: free(), billing: null, master: null, s00: null };
  const today = sql.value("SELECT DATE_FORMAT(CURDATE(), '%Y%m%d')");
  rows.reportDate = sql.value("SELECT DATE_FORMAT(CURDATE(), '%Y-%m-%d')");

  // Registered before any insert, so a partial fixture still has an id- and marker-scoped cleanup.
  s.cleanup(() => {
    if (rows.s00) sql.execute(`DELETE FROM teleplanS00 WHERE s00_id=${rows.s00} AND filename=${q(marker)}`);
    if (rows.master) sql.execute(`DELETE FROM billingmaster WHERE billingmaster_no=${rows.master} AND demographic_no=${patient}`);
    if (rows.billing) sql.execute(`DELETE FROM billing WHERE billing_no=${rows.billing} AND demographic_no=${patient}`);
    sql.execute(`DELETE FROM reportprovider WHERE provider_no IN (${q(rows.provider)}, ${q(rows.payee)}) AND team=${q(team)};
      DELETE FROM provider WHERE provider_no IN (${q(rows.provider)}, ${q(rows.payee)}) AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no IN (${q(rows.provider)}, ${q(rows.payee)}))
      + (SELECT COUNT(*) FROM reportprovider WHERE provider_no IN (${q(rows.provider)}, ${q(rows.payee)}))
      + (SELECT COUNT(*) FROM billing WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billingmaster WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM teleplanS00 WHERE filename=${q(marker)})`) === '0', 'Owned Account Reports fixtures were not removed');
  });

  // Each one's OHIP number is its provider number: the payee lookup goes through the OHIP number.
  for (const [no, first] of [[rows.provider, 'Quinn'], [rows.payee, 'Yara']]) {
    sql.execute(`INSERT INTO provider (provider_no, last_name, first_name, provider_type, specialty, sex, ohip_no, billing_no,
        status, lastUpdateUser, lastUpdateDate)
      VALUES (${q(no)}, ${q(marker)}, ${q(first)}, 'doctor', '', 'F', ${q(no)}, ${q(no)}, '1', ${q(s.provider)}, NOW());
      INSERT INTO reportprovider (provider_no, team, action, status) VALUES (${q(no)}, ${q(team)}, 'billingreport', '1')`);
  }
  rows.billing = h.insertId(sql, `INSERT INTO billing (demographic_no, provider_no, apptProvider_no, billingtype, demographic_name,
      dob, update_date, billing_date, status, creator)
    VALUES (${patient}, ${q(rows.provider)}, ${q(rows.provider)}, 'MSP', ${q(`${marker},Workflow`)}, '19800102', CURDATE(), CURDATE(),
      'O', ${q(s.provider)})`, 'bill');
  // billingstatus X: written off (bad debt), what the Write-Off report lists.
  rows.master = h.insertId(sql, `INSERT INTO billingmaster (billing_no, createdate, billingstatus, demographic_no, appointment_no,
      claimcode, datacenter, payee_no, practitioner_no, phn, name_verify, dependent_num, billing_unit, clarification_code,
      anatomical_area, after_hour, new_program, billing_code, bill_amount, payment_mode, service_date, service_to_day,
      submission_code, extended_submission_code, dx_code1, dx_code2, dx_code3, dx_expansion, service_location, referral_flag1,
      referral_no1, referral_flag2, referral_no2, time_call, service_start_time, service_end_time, birth_date, office_number,
      correspondence_code, claim_comment, mva_claim_code, icbc_claim_no, original_claim, facility_no, facility_sub_no,
      oin_insurer_code, oin_registration_no, oin_birthdate, oin_first_name, oin_second_name, oin_surname, oin_sex_code,
      oin_address, oin_address2, oin_address3, oin_address4, oin_postalcode, paymentMethod, wcb_id)
    VALUES (${rows.billing}, NOW(), 'X', ${patient}, 0, 'C02', '00000', ${q(rows.payee)}, ${q(rows.provider)}, '9000000000', 'WOR',
      '00', '1', '00', '00', '0', '00', '00100', '33.70', '0', ${q(today)}, '00', '0', '', '780', '', '', '', 'A', '0', '', '0', '',
      '', '', '', '19800102', '', '0', '', 'N', '', '', '', '', '', '', '', '', '', '', 'F', '', '', '', '', '', '1', 0)`, 'bill line');
  // MSP amounts are 7 digits in cents (t_billamt and t_paidamt are varchar(7)); a longer value is cut without an error
  // under the empty sql_mode, so the width is asserted before the payment line is written.
  h.assert(sql.value("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='teleplanS00' AND COLUMN_NAME='t_paidamt'") === '7',
    'teleplanS00.t_paidamt is not the 7-character MSP amount this fixture writes');
  rows.s00 = h.insertId(sql, `INSERT INTO teleplanS00 (s21_id, filename, t_s00type, t_datacenter, t_dataseq, t_payment, t_linecode,
      t_payeeno, t_mspctlno, t_practitionerno, t_msprcddate, t_initial, t_surname, t_phn, t_phndepno, t_servicedate, t_today,
      t_billnoservices, t_billclafcode, t_billfeeschedule, t_billamt, t_paidnoservices, t_paidclafcode, t_paidfeeschedule,
      t_paidamt, t_officeno, t_exp1, t_exp2, t_exp3, t_exp4, t_exp5, t_exp6, t_exp7, t_ajc1, t_aja1, t_ajc2, t_aja2, t_ajc3,
      t_aja3, t_ajc4, t_aja4, t_ajc5, t_aja5, t_ajc6, t_aja6, t_ajc7, t_aja7, t_paidrate, t_planrefno, t_claimsource,
      t_previouspaiddate, t_insurercode, t_icbcwcb, t_filler)
    VALUES (0, ${q(marker)}, 'S01', '00000', '0000001', ${q(today)}, '1', ${q(rows.payee)}, '000000', ${q(rows.provider)}, ${q(today)},
      'W', ${q(marker.slice(0, 18))}, '9000000000', '00', ${q(today)}, '00', '001', '00', '00100', '0003370', '001', '00',
      '00100', '0003370', ${q(rows.master)}, '', '', '', '', '', '', '', '', '0000000', '', '0000000', '', '0000000', '',
      '0000000', '', '0000000', '', '0000000', '', '0000000', '', '', '', '', '', '', '')`, 'payment line');
  return rows;
}

async function workflow(s) {
  const rows = seedReportRows(s);
  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'bc-account-reports-admin' });
  let frame;

  /** Fills the form for one report and returns the HTTP answer and, for a download, its bytes. */
  const createReport = async (repType, format) => {
    // The report type first: the page enables Provider and Payee only for the types that use them.
    await frame.locator('select[name="repType"]').selectOption(repType);
    await frame.locator('select[name="selProv"]').selectOption(rows.provider);
    await frame.locator('select[name="selPayee"]').selectOption(rows.payee);
    await frame.locator('select[name="selAccount"]').selectOption('ALL');
    await frame.locator('select[name="docFormat"]').selectOption(format);
    await frame.locator('[name="xml_vdate"]').evaluate((input, date) => { input.value = date; }, rows.reportDate);
    await frame.locator('[name="xml_appointment_date"]').evaluate((input, date) => { input.value = date; }, rows.reportDate);
    const downloaded = admin.waitForEvent('download', { timeout: TIMEOUT }).catch(() => null);
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && /createBillingReportAction$/.test(new URL(r.url()).pathname),
        { timeout: TIMEOUT }),
      frame.locator('input[type="submit"][name="Submit"]').click(),
    ]);
    h.assert(response.status() === 200, `Create Report answered HTTP ${response.status()}`);
    const download = await downloaded;
    h.assert(download, 'Create Report did not download the report');
    try {
      return { type: response.headers()['content-type'] || '', bytes: fs.readFileSync(await download.path()) };
    } finally {
      await download.delete();
    }
  };
  const pdfReport = async (repType) => {
    const report = await createReport(repType, 'pdf');
    // Keep totals beside their labels; default Poppler reading order can interleave adjacent columns.
    return pdfTextBuffer(report.bytes, { layout: true }).replace(/\s+/g, ' ');
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
    for (const [name, value] of [['selProv', rows.provider], ['selPayee', rows.payee]]) {
      h.assert(await frame.locator(`select[name="${name}"] option[value="${value}"]`).count() === 1,
        `The ${name} list does not offer the FAKE ${name === 'selProv' ? 'provider' : 'payee'}`);
    }
  });

  await s.step(STEP.writeOff, async () => {
    const text = await pdfReport('REP_WO');
    // The write-off names are "first last".
    h.assert(/Provider:\s*Quinn\b/.test(text), 'The Write-Off PDF does not name the chosen provider on its Provider label');
    h.assert(/Payee:\s*Yara\b/.test(text), 'The Write-Off PDF does not name the chosen payee on its Payee label');
  });

  await s.step(STEP.payRef, async () => {
    const text = await pdfReport('REP_PAYREF');
    // The payments reports print initials: Q(uinn) F(AKE-…), Y(ara) F(AKE-…).
    h.assert(/Provider:\s*QF\b/.test(text), "The Payments and Refunds PDF does not print the provider's initials");
    h.assert(/Payee:\s*YF\b/.test(text), "The Payments and Refunds PDF does not print the payee's initials");
    // Check the total field, including on a C.UTF-8 JVM whose currency symbol is the generic ¤.
    h.assert(/TOTAL:\s*(?:CA)?[$¤]33\.70\b/.test(text), 'The Payments and Refunds PDF does not total the 33.70 payment');
  });

  await s.step(STEP.payRefSummary, async () => {
    const text = await pdfReport('REP_PAYREF_SUM');
    h.assert(/Provider:\s*QF\b/.test(text), "The Payments and Refunds summary PDF does not print the provider's initials");
    h.assert(/Payee:\s*YF\b/.test(text), "The Payments and Refunds summary PDF does not print the payee's initials");
    h.assert(/TOTAL:\s*(?:CA)?[$¤]33\.70\b/.test(text), 'The Payments and Refunds summary PDF does not total the 33.70 payment');
  });

  await s.step(STEP.spreadsheet, async () => {
    const report = await createReport('REP_PAYREF_SUM', 'csv');
    // CreateBillingReport2Action.cfgHeader sends csv as application/csv. Its template prints no provider or payee,
    // so the type and the absence of a page are what tell a spreadsheet from an error page that downloaded.
    h.assert(/^application\/csv\b/i.test(report.type), `The spreadsheet summary came back as ${JSON.stringify(report.type)}`);
    h.assert(report.bytes.length > 0, 'The spreadsheet summary is empty');
    const head = report.bytes.subarray(0, 512).toString('latin1');
    h.assert(!head.startsWith('%PDF-') && !/<html|<!doctype/i.test(head), 'The spreadsheet summary is a PDF or an HTML page');
    const text = report.bytes.toString('utf8');
    const printedAmounts = (text.match(/\$33\.70/g) || []).length;
    h.assert(printedAmounts === 2,
      `The spreadsheet summary prints ${printedAmounts} CAD payment/total amounts, not the expected two $33.70 values`);
    h.assert(!/[\u00a4\u20ac]/.test(text), 'The spreadsheet summary changed CAD into a host-locale currency');
  });
  await admin.close();
}

if (require.main === module) runWorkflow('billing-bc-account-reports', workflow, { openMaster: false, preflight });
module.exports = { workflow, preflight, STEP };
