#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario Invoice Reports: bill type, service code, dx, visit type, visit location, RA error code
 * and claim number filters, the RA columns (paid, adjustment, messages), the footer totals, sorting
 * and the CSV export.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Invoice Reports (ViewBillStatus ▸
 * ViewBillingONStatus in the #dynamic-content iframe) ▸ filters ▸ Create Report; Export to CSV.
 * billing-on-payment-status drives the status, date, provider and patient filters; this check
 * drives the others against owned submitted (B) claims with owned Remittance Advice rows.
 *
 * Asserts: the report lists one row per claim item with its service code, billed amount, dx, pay
 * program and the RA-derived paid amount, adjustment and error code; the footer counts, billed,
 * paid and adjustment totals equal the rows; each filter (bill type, service code, dx, visit type,
 * visit location, RA code) narrows the list to exactly the matching owned claims;
 * Export to CSV downloads the listed invoices; Claim No narrows to its claim. The LAST step fails
 * today on two defects: emptying the Serv. Code box (it arrives holding "%") makes the report take
 * the legacy query that inner-joins billing_on_payment, so every claim without a payment record
 * disappears; and the LOCATION header sort answers HTTP 500 when a claim has no visit location.
 *
 * Fixtures: createBillingFixture (owned provider, HIN) with three submitted claims (two OHIP, one
 * WSIB; different codes, dx, visit type and location) and two owned radetail rows under an owned
 * raheader. Cleanup removes them by id and asserts they are gone.
 * Implements the gap-billing "invoice list filters" workflow.
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture, openAdministration, openAdminFrame } = require('./billing-on-ohip-simulation-report-playwright-checks');
const { billDate, money } = require('./billing-on-invoice-third-party-playwright-checks');

const q = h.sqlString;

async function workflow(s) {
  const { sql, patient, marker, context } = s;
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
  const location = sql.value('SELECT clinic_location_no FROM clinic_location ORDER BY id LIMIT 1');
  if (!/^\d{4}$/.test(location)) throw new h.SkipCheck('no 4-digit visit location is configured');

  const one = owned.addClaim({ tag: 'ONE', date, status: 'B', items: [{ code: 'A007A', fee: feeA }] });
  const two = owned.addClaim({ tag: 'TWO', date, status: 'B', items: [{ code: 'A001A', fee: feeB }] });
  const three = owned.addClaim({ tag: 'THREE', date, status: 'B', items: [{ code: 'K030A', fee: feeK }] });
  // Differentiate the owned claims: dx, visit type, visit location, pay program.
  sql.execute(`UPDATE billing_on_item SET dx='401' WHERE ch1_id=${two.id};
    UPDATE billing_on_item SET dx='493' WHERE ch1_id=${three.id};
    UPDATE billing_on_cheader1 SET visittype='02', facilty_num=${q(location)} WHERE id=${two.id};
    UPDATE billing_on_cheader1 SET pay_program='WCB' WHERE id=${three.id}`);

  // Owned RA rows: the first claim paid in part, the second rejected.
  const claimOne = `9${String(Math.floor(Math.random() * 1e10)).padStart(10, '0')}`;
  const claimTwo = `8${String(Math.floor(Math.random() * 1e10)).padStart(10, '0')}`;
  // Scope both the delete and its verification to this fixture's RA header: a generated claim number could
  // collide with an unowned remittance row, which must never be removed.
  const ownedRa = `SELECT raheader_no FROM raheader WHERE payable=${q(marker)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM radetail WHERE raheader_no IN (${ownedRa});
      DELETE FROM raheader WHERE payable=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM radetail WHERE raheader_no IN (${ownedRa}))
      + (SELECT COUNT(*) FROM raheader WHERE payable=${q(marker)})`) === '0', 'Owned RA rows were not removed');
  });
  const raNo = sql.value(`INSERT INTO raheader (filename, paymentdate, payable, totalamount, records, claims, status, readdate)
    VALUES (${q(`${marker.slice(0, 20)}.xml`)}, ${q(date.replace(/-/g, ''))}, ${q(marker)}, '30.00', '2', '2', 'N',
      ${q(date.replace(/-/g, ''))}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(raNo), 'The RA header fixture was not created');
  const compact = date.replace(/-/g, '');
  sql.execute(`INSERT INTO radetail (raheader_no, providerohip_no, billing_no, service_code, service_count, hin, amountclaim,
      amountpay, service_date, error_code, billtype, claim_no) VALUES
      (${raNo}, ${q(owned.ohipNo)}, ${one.id}, 'A007A', '01', '', ${q(feeA)}, '30.00', ${q(compact)}, '', 'HCP', ${q(claimOne)}),
      (${raNo}, ${q(owned.ohipNo)}, ${two.id}, 'A001A', '01', '', ${q(feeB)}, '0.00', ${q(compact)}, 'AD', 'HCP', ${q(claimTwo)})`);

  const admin = await openAdministration(s);
  const frame = await openAdminFrame(admin, '/billing/CA/ON/ViewBillStatus', 'form[name="serviceform"]');
  const form = 'form[name="serviceform"]';
  const ids = [one.id, two.id, three.id];

  /** Fill the filters and press Create Report; returns the invoice numbers of the owned rows listed. */
  async function report({ billTypes = null, dx = '', serviceCode = null, raCode = '', claimNo = '', visitType = '-', visit = null } = {}) {
    await frame.locator(`${form} input[name="demographicNo"]`).fill(String(patient));
    for (const [selector, value] of [['#xml_vdate', date], ['#xml_appointment_date', date]]) {
      await frame.locator(selector).fill(value);
      await frame.locator('body').click({ position: { x: 4, y: 4 } });
    }
    await frame.locator('#statusTypeSubmittedOHIP').check();
    const providers = frame.locator('select[name="providerview"]').last();
    const offered = await providers.locator('option').evaluateAll(options => options.map(option => option.value));
    h.assert(offered.includes(owned.providerNo), 'Invoice Reports does not offer the owned billing provider');
    await providers.selectOption(owned.providerNo);
    // ALL ticks or unticks every bill type: clear them all, then tick ALL again or only the wanted types.
    const all = frame.locator('#ALL');
    if (await all.isChecked()) await all.click();
    if (billTypes === null) await all.click();
    else for (const type of billTypes) await frame.locator(`#billType_${type}`).check();
    await frame.locator(`${form} input[name="dx"]`).fill(dx);
    // The box arrives holding "%" (any code) and keeps the last search: put "%" back unless a code is wanted.
    await frame.locator(`${form} input[name="serviceCode"]`).fill(serviceCode === null ? '%' : serviceCode);
    await frame.locator(`${form} input[name="raCode"]`).fill(raCode);
    await frame.locator(`${form} input[name="claimNo"]`).fill(claimNo);
    await frame.locator(`${form} select[name="visitType"]`).selectOption(visitType);
    await frame.locator('#xml_location').selectOption(visit === null ? { index: 0 } : visit);
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
    navigated.catch(() => {});
    await frame.locator(`${form} input[type="submit"][value="Create Report"]`).click();
    await navigated;
    await frame.waitForLoadState('networkidle', { timeout: 30000 });
    return ownedRows();
  }
  /** The owned rows of the report on screen, as cell texts. */
  async function ownedRows() {
    await frame.locator('#bListTable').waitFor({ state: 'attached', timeout: 20000 });
    const listed = await frame.locator('#bListTable tbody tr').evaluateAll(rows => rows.map(row =>
      Array.from(row.querySelectorAll('td')).map(cell => cell.innerText.trim())));
    return listed.filter(cells => ids.includes(cells[12]));
  }
  /** Click a sortable column header (it resubmits the report) and read the rows. */
  async function sortBy(label) {
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
    navigated.catch(() => {});
    await frame.locator('#bListTable thead a', { hasText: label }).click();
    await navigated;
    await frame.waitForLoadState('networkidle', { timeout: 30000 });
    return ownedRows();
  }
  const invoices = rows => rows.map(cells => cells[12]).sort().join(',');
  const wanted = list => list.map(c => c.id).sort().join(',');
  const names = rows => rows.map(cells => ['one', 'two', 'three'][ids.indexOf(cells[12])]).join('+') || 'none';

  let all;
  await s.step('the report lists each owned claim with its code, billed amount, dx, pay program and RA paid, adjustment and message', async () => {
    all = await report();
    h.assert(invoices(all) === wanted([one, two, three]), 'The report does not list exactly the three owned claims');
    const row = id => all.find(cells => cells[12] === id);
    const a = row(one.id);
    h.assert(a[6] === 'A007A' && Number(a[7].replace(/[^\d.]/g, '')) === Number(feeA) && a[10] === '250' && a[11] === 'HCP',
      'The first claim row does not show its code, billed amount, dx and pay program');
    h.assert(Number(a[8].replace(/[^\d.-]/g, '')) === 30 && Number(a[9]) === Number(money(Number(feeA) - 30)),
      'The first claim row does not show the RA paid amount and adjustment');
    const b = row(two.id);
    h.assert(b[6] === 'A001A' && b[10] === '401' && Number(b[8].replace(/[^\d.-]/g, '')) === 0 && b[13].includes('AD'),
      'The rejected claim row does not show its dx, nothing paid and the AD message');
    h.assert(row(three.id)[11] === 'WCB' && row(three.id)[10] === '493', 'The WSIB claim row does not show its pay program and dx');
  });

  await s.step('the footer counts the rows and totals billed, paid and adjustments', async () => {
    const text = (await frame.locator('table.table-warning, tr.table-warning').last().innerText()).replace(/\s+/g, ' ');
    h.assert(new RegExp(`Count:\\s*${all.length}(?!\\d)`).test(text), 'The footer count is not the number of listed claims');
    const billed = Number(feeA) + Number(feeB) + Number(feeK);
    h.assert(text.includes(`Total: $${money(billed)}`), 'The footer billed total is not the sum of the rows');
    h.assert(text.includes('Paid: $30.00'), 'The footer paid total is not the sum of the RA payments');
    h.assert(text.includes(`Adj: $${money(billed - 30)}`), 'The footer adjustment total is not billed minus paid');
  });

  await s.step('bill type, service code, dx, visit type, visit location and RA code each narrow the list', async () => {
    const expectOnly = async (label, filters, list) => {
      const rows = await report(filters);
      h.assert(invoices(rows) === wanted(list), `${label} listed ${names(rows)} instead of ${list.map(c => ids.indexOf(c.id) === 0 ? 'one' : ids.indexOf(c.id) === 1 ? 'two' : 'three').join('+')}`);
    };
    await expectOnly('Bill type WCB', { billTypes: ['WCB'] }, [three]);
    await expectOnly('Bill types HCP and NOT', { billTypes: ['HCP', 'NOT'] }, [one, two]);
    await expectOnly('Service code K030A', { serviceCode: 'K030A' }, [three]);
    await expectOnly('Dx 401', { dx: '401' }, [two]);
    await expectOnly('Visit type 02', { visitType: '02' }, [two]);
    await expectOnly('The visit location', { visit: location }, [two]);
    await expectOnly('RA code AD', { raCode: 'AD' }, [two]);
  });

  await s.step('Export to CSV downloads the listed invoices', async () => {
    await report();
    const [download] = await Promise.all([
      admin.waitForEvent('download', { timeout: 20000 }),
      frame.locator('a[download="carlos_invoices.csv"]').click(),
    ]);
    const file = await download.path();
    h.assert(file, 'The CSV export was not saved');
    const csv = fs.readFileSync(file, 'utf8');
    for (const id of ids) h.assert(csv.includes(id), 'The CSV export does not list an owned invoice');
  });

  await s.step('the Claim No filter lists only the claim of that RA claim number', async () => {
    const rows = await report({ claimNo: claimOne });
    h.assert(invoices(rows) === wanted([one]), 'Claim No did not narrow the list to the claim of that RA claim number');
  });

  await s.step('LOCATION sorts claims with missing visit locations in both directions', async () => {
    await report();
    const asc = await sortBy('LOCATION');
    const desc = await sortBy('LOCATION');
    h.assert(asc.length === 3 && asc.map(c => c[12]).join(',') === desc.map(c => c[12]).reverse().join(','),
      'The LOCATION sort did not preserve all rows and reverse on the second click');
  });

  // Separate regression L270 (#4153): keep the empty-filter path independently observable.
  await s.step('emptying the Serv. Code box keeps the unpaid claims', async () => {
    const rows = await report({ serviceCode: '' });
    h.assert(invoices(rows) === wanted([one, two, three]),
      'Clearing the Serv. Code box hides claims without a payment record');
  });
}

if (require.main === module) runWorkflow('gap-billing-invoice-report-filters', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
