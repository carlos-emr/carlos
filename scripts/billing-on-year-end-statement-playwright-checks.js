#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Administration > Billing > End Year Statement: select an owned patient, create
 * an HTML statement and download its PDF. Both outputs must contain active and
 * settled PAT invoices in the inclusive date range, formatted dates/amounts, and
 * matching totals. Deleted, OHIP and out-of-range invoices must be excluded.
 * Every fixture belongs to the workflow patient and is removed on completion.
 */
const fs = require('node:fs');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { openAdminFrame } = require('./billing-on-ohip-simulation-report-playwright-checks');
const { seedOwnedBill } = require('./billing-on-invoice-third-party-playwright-checks');

const FROM = '2025-01-01';
const TO = '2025-12-31';

async function navigate(admin, frame, action) {
  const loaded = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
  loaded.catch(() => {});
  await action();
  await loaded;
  await frame.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(frame, 'year-end statement');
}

async function workflow(s) {
  const definitions = [
    ['PAT', 'O', FROM, 'A007A', '34.70', '25.00'],
    ['PAT', 'S', TO, 'K030A', '45.25', '45.25'],
    ['PAT', 'D', '2025-06-15', 'K005A', '999.99', '888.88'],
    ['HCP', 'O', '2025-06-15', 'A007A', '31.00', '0.00'],
    ['PAT', 'O', '2024-12-31', 'A007A', '32.00', '0.00'],
    ['PAT', 'O', '2026-01-01', 'A007A', '33.00', '0.00'],
  ];
  const bills = definitions.map(([payProgram, status, date, code, fee, paid]) => {
    const bill = seedOwnedBill(s, { payProgram, status, date, code, fee });
    s.sql.execute(`UPDATE billing_on_cheader1 SET paid=${h.sqlString(paid)}
      WHERE id=${bill.headerId} AND demographic_no=${s.patient}`);
    return { ...bill, payProgram, status, date, code, fee, paid };
  });
  // The deleted-header regression must not be hidden by deleted service items.
  s.sql.execute(`UPDATE billing_on_item SET status='O' WHERE id=${bills[2].itemId} AND ch1_id=${bills[2].headerId}`);
  const selected = bills.slice(0, 2);
  const invoiced = '79.95';
  const paid = '70.25';
  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'year-end-administration', timeout: 20000 });
  const frame = await openAdminFrame(admin, '/billing/CA/ON/endYearStatement', '#nameForlooksOnly');

  await s.step('patient search and Create Statement open the owned year-end statement', async () => {
    await frame.locator('#nameForlooksOnly').fill(s.marker);
    await navigate(admin, frame, () => frame.locator('button[onclick*="demographicSearch"]').click());
    const pick = frame.locator(`input[name="pick_demographic"][value="${s.patient}"]`);
    h.assert(await pick.count() === 1, 'The owned patient was not found exactly once');
    await navigate(admin, frame, () => pick.click());
    h.assert(await frame.locator('#demographicNoParam').inputValue() === s.patient, 'Another patient was selected');
    await frame.locator('#fromDateParam').fill(FROM);
    await frame.locator('h3').first().click();
    await frame.locator('#toDateParam').fill(TO);
    await frame.locator('h3').first().click();
    await navigate(admin, frame, () => frame.locator('input[type="submit"][value="Create Statement"]').click());
  });

  await s.step('Print PDF downloads the complete statement with only eligible invoices and matching totals', async () => {
    const downloaded = admin.waitForEvent('download', { timeout: 30000 });
    downloaded.catch(() => {});
    const [response] = await Promise.all([
      s.context.waitForEvent('response', { timeout: 30000, predicate: r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/ON/endYearStatement/pdf') }),
      frame.locator('input[type="submit"][value="Print PDF"]').click(),
    ]);
    h.assert(response.status() === 200 && /^application\/pdf/.test(response.headers()['content-type'] || ''),
      `Year-end PDF answered HTTP ${response.status()} instead of application/pdf`);
    const download = await downloaded;
    const file = await download.path();
    h.assert(file, 'The year-end PDF was not saved');
    const bytes = fs.readFileSync(file);
    h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The download is not a PDF');
    h.assert(/%%EOF\s*$/.test(bytes.subarray(-64).toString('latin1')), 'The PDF contains trailing response bytes');
    const text = execFileSync('pdftotext', ['-layout', file, '-'], { encoding: 'utf8', timeout: 15000 });
    h.assert(text.includes('End Year Statement') && text.includes(s.marker), 'The PDF has the wrong patient or title');
    const words = text.split(/\s+/);
    h.assert(selected.every(b => words.includes(b.headerId) && text.includes(b.code)), 'The PDF omitted an eligible invoice or service');
    h.assert(bills.slice(2).every(b => !words.includes(b.headerId)), 'The PDF contains a deleted, OHIP or out-of-range invoice');
    h.assert(text.includes(invoiced) && text.includes(paid), 'The PDF totals do not match the eligible invoices');
  });

  await s.step('HTML rows show ISO dates and two-decimal money, including settled services and excluding deleted invoices', async () => {
    const rows = await frame.locator('tr[bgcolor="#CEF6CE"]').evaluateAll(elements => elements.map(row =>
      [...row.cells].map(cell => cell.innerText.trim())));
    h.assert(rows.length === selected.length, 'The HTML statement has the wrong number of invoices');
    selected.forEach((bill, index) => {
      const [id, date, , billed, paidAmount] = rows[index];
      h.assert(id === bill.headerId && date === bill.date && billed === bill.fee && paidAmount === bill.paid,
        `HTML statement invoice ${index + 1} has incorrect values or formatting`);
    });
    const services = await frame.locator('tr[bgcolor="ivory"], tr[bgcolor="#EEEEFF"]').allInnerTexts();
    h.assert(selected.every(b => services.some(text => text.includes(b.code) && text.includes(b.fee))),
      'The HTML statement omitted active or settled invoice services');
    const totals = (await frame.locator('tr[bgcolor="#99FF66"]').innerText()).replace(/\s+/g, ' ');
    h.assert(/Count: 2\b/.test(totals) && totals.includes(invoiced) && totals.includes(paid),
      'The HTML statement totals do not match the PDF');
  });
}

if (require.main === module) runWorkflow('billing-on-year-end-statement', workflow, {
  openPatient: true,
  openMaster: false,
  preflight() { execFileSync('pdftotext', ['-v'], { stdio: 'pipe', timeout: 5000 }); },
});
module.exports = { workflow };
