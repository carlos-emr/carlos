#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario Invoice Reports: the CONTENT of Export to CSV and Export to Excel, row by row.
 * User path: Schedule ▸ Administration ▸ Billing ▸ Invoice Reports (ViewBillStatus ▸ ViewBillingONStatus in the
 * #dynamic-content iframe) ▸ Create Report ▸ Export to CSV / Export to Excel (js/table-export.js builds both
 * files in the browser from the report table).
 * gap-billing-invoice-report-filters only proves the invoice numbers occur somewhere in the CSV.
 *
 * Asserts: the CSV parses (RFC 4180) to the report's own header row and one row per listed claim; each owned
 * row's SERVICE DATE, PATIENT, CODE, BILLED, PAID, ADJ, TYPE and INVOICE # equal what the claim was billed and
 * the RA paid (money to the cent); a patient name carrying an accent, a quote and a comma survives the file
 * round trip; the Excel file is a table of the same rows; and (LAST, fails today) a negative adjustment is
 * exported as a number, not as a tab-prefixed text cell, and the CSV declares its UTF-8 encoding (BOM) so a
 * spreadsheet shows the accent instead of mojibake.
 * Fixtures: createBillingFixture (owned provider and HIN), three submitted claims, two owned raheader/radetail
 * rows (one paying MORE than billed so the adjustment is negative); cleanup removes them and asserts it.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const x = require('./lib/export-content-helpers');
const { createBillingFixture, openAdministration, openAdminFrame } = require('./billing-on-ohip-simulation-report-playwright-checks');
const { billDate, money } = require('./billing-on-invoice-third-party-playwright-checks');

const q = h.sqlString;
const FIRST = 'Zoë "Zed", O\'Neil';

async function workflow(s) {
  const { sql, patient, marker } = s;
  const scratch = x.scratchDir();
  s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));
  const owned = createBillingFixture(s);
  const date = billDate();
  const feeOf = code => {
    const value = sql.value(`SELECT value FROM billingservice WHERE service_code=${q(code)} AND billingservice_date<=${q(date)}
      ORDER BY billingservice_date DESC LIMIT 1`);
    if (!(Number(value) > 0)) throw new h.SkipCheck(`service code ${code} has no fee`);
    return money(value);
  };
  const feeA = feeOf('A007A');
  const feeB = feeOf('A001A');
  const feeK = feeOf('K030A');
  const one = owned.addClaim({ tag: 'ONE', date, status: 'B', items: [{ code: 'A007A', fee: feeA }] });
  const two = owned.addClaim({ tag: 'TWO', date, status: 'B', items: [{ code: 'A001A', fee: feeB }] });
  const three = owned.addClaim({ tag: 'THREE', date, status: 'B', items: [{ code: 'K030A', fee: feeK }] });
  // The report prints the name stored on the claim header when it was billed.
  sql.execute(`UPDATE billing_on_cheader1 SET demographic_name=${q(`${marker},${FIRST}`)} WHERE id IN (${one.id},${two.id},${three.id})`);
  const payOne = '30.00';
  const payTwo = '150.00'; // more than billed: the adjustment is negative
  const ownedRa = `SELECT raheader_no FROM raheader WHERE payable=${q(marker)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM radetail WHERE raheader_no IN (${ownedRa}); DELETE FROM raheader WHERE payable=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM radetail WHERE raheader_no IN (${ownedRa}))
      + (SELECT COUNT(*) FROM raheader WHERE payable=${q(marker)})`) === '0', 'Owned RA rows were not removed');
  });
  const compact = date.replace(/-/g, '');
  const raNo = sql.value(`INSERT INTO raheader (filename, paymentdate, payable, totalamount, records, claims, status, readdate)
    VALUES (${q(`${marker.slice(0, 20)}.xml`)}, ${q(compact)}, ${q(marker)}, '180.00', '2', '2', 'N', ${q(compact)});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(raNo), 'The RA header fixture was not created');
  const claimNo = () => `9${String(Math.floor(Math.random() * 1e10)).padStart(10, '0')}`;
  sql.execute(`INSERT INTO radetail (raheader_no, providerohip_no, billing_no, service_code, service_count, hin, amountclaim,
      amountpay, service_date, error_code, billtype, claim_no) VALUES
      (${raNo}, ${q(owned.ohipNo)}, ${one.id}, 'A007A', '01', '', ${q(feeA)}, ${q(payOne)}, ${q(compact)}, '', 'HCP', ${q(claimNo())}),
      (${raNo}, ${q(owned.ohipNo)}, ${two.id}, 'A001A', '01', '', ${q(feeB)}, ${q(payTwo)}, ${q(compact)}, '', 'HCP', ${q(claimNo())})`);
  const ids = [one.id, two.id, three.id];

  const admin = await openAdministration(s);
  const frame = await openAdminFrame(admin, '/billing/CA/ON/ViewBillStatus', 'form[name="serviceform"]');
  const form = 'form[name="serviceform"]';
  let table;

  await s.step('Create Report lists the three owned claims with the billed and RA-paid amounts', async () => {
    await frame.locator(`${form} input[name="demographicNo"]`).fill(String(patient));
    for (const selector of ['#xml_vdate', '#xml_appointment_date']) {
      await frame.locator(selector).fill(date);
      await frame.locator('body').click({ position: { x: 4, y: 4 } });
    }
    await frame.locator('#statusTypeSubmittedOHIP').check();
    await frame.locator('select[name="providerview"]').last().selectOption(owned.providerNo);
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
    navigated.catch(() => {});
    await frame.locator(`${form} input[type="submit"][value="Create Report"]`).click();
    await navigated;
    await frame.waitForLoadState('networkidle', { timeout: 30000 });
    await frame.locator('#bListTable').waitFor({ state: 'attached', timeout: 20000 });
    table = await frame.locator('#bListTable').evaluate(t => ({
      head: [...t.tHead.rows[0].cells].map(c => c.textContent.trim()),
      rows: [...t.tBodies[0].rows].map(r => [...r.cells].map(c => c.textContent.trim())),
    }));
    const col = table.head.findIndex(title => title === 'INVOICE #');
    h.assert(col >= 0, 'The report has no INVOICE # column');
    table.mine = table.rows.filter(r => ids.includes(r[col]));
    h.assert(table.mine.length === 3, `The report lists ${table.mine.length} of the three owned claims`);
    const adj = table.head.indexOf('ADJ');
    const paid = table.head.indexOf('PAID');
    const byId = Object.fromEntries(table.mine.map(r => [r[col], r]));
    h.assert(Number(byId[two.id][paid].replace(/[^\d.-]/g, '')) === Number(payTwo)
      && Number(byId[two.id][adj]) === Number(money(Number(feeB) - Number(payTwo))),
    'The overpaid claim does not show its RA payment and negative adjustment on screen');
  });

  const flat = value => value.replace(/\s+/g, ' ');
  const col = name => table.head.findIndex(title => flat(title) === name);
  const csvDownload = async () => x.saveDownload(admin, scratch, () => frame.locator('a[download="carlos_invoices.csv"]').click());
  let csv;
  let csvBytes;

  await s.step('the CSV carries the report header and exactly the listed rows, cell for cell', async () => {
    const file = await csvDownload();
    csvBytes = file.bytes;
    h.assert(file.name === 'carlos_invoices.csv', 'The CSV download is not named carlos_invoices.csv');
    csv = x.parseCsv(file.bytes.toString('utf8').replace(/^﻿/, ''));
    h.assert(JSON.stringify(csv[0]) === JSON.stringify(table.head), 'The CSV header row is not the report header row');
    const body = csv.slice(1).filter(r => r.some(c => c !== ''));
    h.assert(body.length === table.rows.length, `The CSV has ${body.length} data rows, the report lists ${table.rows.length}`);
    const mine = body.filter(r => ids.includes(r[col('INVOICE #')]));
    h.assert(mine.length === 3, 'The CSV does not carry the three owned claims');
    for (const row of mine) {
      const shown = table.mine.find(r => r[col('INVOICE #')] === row[col('INVOICE #')]);
      table.head.forEach((title, i) => {
        // Negative numbers are checked in the last step; here every other cell must equal the report cell.
        if (/^[\t]?-\d/.test(row[i]) && shown[i] === row[i].replace(/^\t/, '')) return;
        h.assert(row[i] === shown[i], `The CSV cell for ${title} of invoice ${row[col('INVOICE #')]} differs from the report cell`);
      });
    }
  });

  await s.step('the CSV cells equal the billed fee, RA payment, code, date and the patient name as stored', async () => {
    const body = csv.slice(1).filter(r => ids.includes(r[col('INVOICE #')]));
    const expected = { [one.id]: ['A007A', feeA, payOne], [two.id]: ['A001A', feeB, payTwo], [three.id]: ['K030A', feeK, null] };
    const dbName = `${marker},${FIRST}`;
    for (const row of body) {
      const [code, fee, pay] = expected[row[col('INVOICE #')]];
      h.assert(row[col('CODE')] === code, 'A CSV row has the wrong service code');
      h.assert(row[col('SERVICE DATE')] === date, `A CSV row has service date "${row[col('SERVICE DATE')]}" instead of "${date}"`);
      h.assert(row[col('PATIENT')] === String(patient), 'A CSV row has the wrong patient number');
      h.assert(Number(row[col('BILLED')].replace(/[^\d.-]/g, '')) === Number(fee), 'A CSV row has the wrong billed amount');
      if (pay !== null) h.assert(Number(row[col('PAID')].replace(/[^\d.-]/g, '')) === Number(pay), 'A CSV row has the wrong paid amount');
      h.assert(x.squash(row[col('PATIENT NAME')]) === x.squash(dbName),
        'A CSV row does not carry the patient name as stored (accent, quote, comma)');
    }
  });

  await s.step('Export to Excel is a table of the same rows', async () => {
    const file = await x.saveDownload(admin, scratch, () => frame.locator('a[download="carlos_invoices.xls"]').click());
    const html = file.bytes.toString('utf8');
    h.assert(/charset=UTF-8/i.test(html), 'The Excel export does not declare its UTF-8 encoding');
    for (const id of ids) h.assert(html.includes(`>${id}<`) || new RegExp(`>\\s*${id}\\s*<`).test(html), 'The Excel export lacks an owned invoice');
    h.assert((html.match(/<tr\b/g) || []).length >= table.rows.length + 1, 'The Excel export has fewer rows than the report');
    h.assert(html.includes('Zoë') || html.includes('Zo&euml;') || html.includes('Zo&#235;'), 'The Excel export lost the accent in the patient name');
  });

  await s.step('a negative adjustment is exported as a number and the CSV declares UTF-8', async () => {
    const problems = [];
    const messy = csv[0].filter(title => /\s{2,}|[\r\n]/.test(title));
    if (messy.length) problems.push(`${messy.length} CSV header cell(s) carry a line break and indentation from the page source ("${flat(messy[0])}")`);
    const negative = csv.slice(1).find(r => r[col('INVOICE #')] === two.id)[col('ADJ')];
    if (negative !== money(Number(feeB) - Number(payTwo))) {
      problems.push('a negative adjustment is exported as a tab-prefixed text cell (the formula-injection guard also hits legitimate numbers)');
    }
    if (!(csvBytes[0] === 0xEF && csvBytes[1] === 0xBB && csvBytes[2] === 0xBF)) {
      problems.push('the CSV has no UTF-8 byte-order mark, so Excel shows the accent in the patient name as mojibake');
    }
    h.assert(!problems.length, `The invoice CSV is not spreadsheet-safe: ${problems.join('; ')}`);
  });
}

if (require.main === module) runWorkflow('export-content-invoice-csv', workflow, { openMaster: false });
module.exports = { workflow };
