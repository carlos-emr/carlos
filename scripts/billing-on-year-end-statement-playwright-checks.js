#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Administration > Billing > End Year Statement: select an owned patient, create
 * an HTML statement and download its PDF. Both outputs must contain active and
 * settled PAT invoices in the inclusive date range, formatted dates/amounts, and
 * matching totals. Deleted, OHIP and out-of-range invoices must be excluded.
 * A second owned patient and statement window must not change the shown first
 * statement, even after its editable dates change. An empty replacement clears
 * the second window's rows and disables printing instead of reusing its result.
 * Every fixture belongs to an owned patient and is removed on completion.
 */
const fs = require('node:fs');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { statementInvoiceRows } = require('./lib/end-year-statement-pdf');
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
  const otherMarker = `${s.marker}B`;
  let otherPatient;
  s.cleanup(() => {
    if (!otherPatient) return;
    h.assert(s.sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${otherPatient}
      AND last_name=${h.sqlString(otherMarker)}`) === '1', 'Second statement patient ownership changed');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${otherPatient}`) === '0',
      'Second statement patient retained: its billing rows were not removed');
    s.sql.execute(`DELETE FROM demographic WHERE demographic_no=${otherPatient} AND last_name=${h.sqlString(otherMarker)}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${otherPatient}`) === '0',
      'The second statement patient was not removed');
  });
  otherPatient = s.sql.value(`INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
      provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(otherMarker)},'Other statement','1980','01','02','F','AC',
      ${h.sqlString(s.provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(otherPatient), 'The second statement patient fixture was not created');
  const otherBill = seedOwnedBill({ ...s, patient: otherPatient, marker: otherMarker },
    { payProgram: 'PAT', status: 'O', date: '2025-07-15', code: 'K005A', fee: '61.35' });
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
  const invoiceRows = list => list.map(b => ({ id: b.headerId, date: b.date, invoiced: b.fee, paid: b.paid }));
  const billingState = () => JSON.stringify(s.sql.rows(`SELECT * FROM billing_on_cheader1
    WHERE demographic_no IN (${s.patient},${otherPatient}) ORDER BY id`))
    + JSON.stringify(s.sql.rows(`SELECT i.* FROM billing_on_item i JOIN billing_on_cheader1 h ON h.id=i.ch1_id
      WHERE h.demographic_no IN (${s.patient},${otherPatient}) ORDER BY i.id`));
  const beforeBilling = billingState();
  const invoiced = '79.95';
  const paid = '70.25';
  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'year-end-administration', timeout: 20000 });
  const frame = await openAdminFrame(admin, '/billing/CA/ON/endYearStatement', '#nameForlooksOnly');

  async function printStatement(owner, statement, marker, expectedRows) {
    const downloaded = owner.waitForEvent('download', { timeout: 30000 });
    downloaded.catch(() => {});
    const [response] = await Promise.all([
      s.context.waitForEvent('response', { timeout: 30000, predicate: r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/ON/endYearStatement/pdf') }),
      statement.locator('input[type="submit"][value="Print PDF"]').click(),
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
    h.assert(text.includes('End Year Statement') && text.includes(marker), 'The PDF has the wrong patient or title');
    h.assert(JSON.stringify(statementInvoiceRows(text)) === JSON.stringify(expectedRows),
      'The PDF invoice rows do not match the PAT/patient/date selection');
    return text;
  }

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
    const text = await printStatement(admin, frame, s.marker, invoiceRows(selected));
    h.assert(selected.every(b => text.includes(b.code)), 'The PDF omitted an eligible service code');
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

  let otherWindow;
  await s.step('a second patient statement contains only its own invoice in HTML and PDF', async () => {
    otherWindow = await s.context.newPage();
    await h.gotoApp(otherWindow, s.config.baseUrl, '/billing/CA/ON/endYearStatement');
    await otherWindow.locator('#nameForlooksOnly').fill(otherMarker);
    await navigate(otherWindow, otherWindow.mainFrame(),
      () => otherWindow.locator('button[onclick*="demographicSearch"]').click());
    const pick = otherWindow.locator(`input[name="pick_demographic"][value="${otherPatient}"]`);
    h.assert(await pick.count() === 1, 'The second owned patient was not found exactly once');
    await navigate(otherWindow, otherWindow.mainFrame(), () => pick.click());
    h.assert(await otherWindow.locator('#demographicNoParam').inputValue() === otherPatient,
      'The second statement inherited the first patient');
    for (const [id, value] of [['fromDateParam', FROM], ['toDateParam', TO]]) {
      await otherWindow.locator(`#${id}`).fill(value);
      await otherWindow.locator('h3').first().click();
    }
    await navigate(otherWindow, otherWindow.mainFrame(),
      () => otherWindow.locator('input[type="submit"][value="Create Statement"]').click());
    const rows = await otherWindow.locator('tr[bgcolor="#CEF6CE"]').evaluateAll(elements => elements.map(row =>
      [...row.cells].map(cell => cell.innerText.trim())));
    h.assert(JSON.stringify(rows.map(([id, date, , invoiced, paidAmount]) => ({ id, date, invoiced, paid: paidAmount })))
      === JSON.stringify([{ id: otherBill.headerId, date: '2025-07-15', invoiced: '61.35', paid: '0.00' }]),
    'The second HTML statement contains another patient\'s invoice or incorrect values');
    await printStatement(otherWindow, otherWindow, otherMarker,
      [{ id: otherBill.headerId, date: '2025-07-15', invoiced: '61.35', paid: '0.00' }]);
  });

  await s.step('the first shown statement prints its own snapshot after another patient opens and its date fields change', async () => {
    await frame.locator('#fromDateParam').fill('2024-01-01');
    await frame.locator('h3').first().click();
    await frame.locator('#toDateParam').fill('2026-12-31');
    await frame.locator('h3').first().click();
    h.assert(await frame.locator('input[name="statementDemographicNo"]').inputValue() === s.patient,
      'Opening another statement replaced the first statement patient');
    h.assert(await frame.locator('input[name="statementFromDate"]').inputValue() === FROM
      && await frame.locator('input[name="statementToDate"]').inputValue() === TO,
    'Editing search dates changed the shown statement snapshot before Create Statement');
    await printStatement(admin, frame, s.marker, invoiceRows(selected));
  });

  await s.step('an empty replacement clears old results and disables Print PDF without changing either patient billing', async () => {
    for (const id of ['fromDateParam', 'toDateParam']) {
      await otherWindow.locator(`#${id}`).fill('2023-01-01');
      await otherWindow.locator('h3').first().click();
    }
    await navigate(otherWindow, otherWindow.mainFrame(),
      () => otherWindow.locator('input[type="submit"][value="Create Statement"]').click());
    h.assert(await otherWindow.locator('tr[bgcolor="#CEF6CE"]').count() === 0,
      'An empty date range retained a previous statement invoice');
    h.assert(await otherWindow.locator('input[type="submit"][value="Print PDF"]').isDisabled(),
      'An empty replacement still offers the stale statement PDF');
    h.assert(await otherWindow.locator('#demographicNoParam').inputValue() === otherPatient,
      'The empty replacement changed the selected patient');
    h.assert(await frame.locator('tr[bgcolor="#CEF6CE"]').count() === selected.length,
      'The empty second statement cleared the first window');
    h.assert(billingState() === beforeBilling, 'Reading or printing year-end statements changed billing data');
  });
}

if (require.main === module) runWorkflow('billing-on-year-end-statement', workflow, {
  openPatient: true,
  openMaster: false,
  preflight() { execFileSync('pdftotext', ['-v'], { stdio: 'pipe', timeout: 5000 }); },
});
module.exports = { workflow };
