#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario bill status, settlement and payment-report workflow check.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Invoice Reports (ViewBillingONStatus
 * in #dynamic-content iframe) ▸ status/date searches ▸ invoice # ▸ Correction ▸ Payments
 * List ▸ Save & settle; Invoice Reports ▸ Rejected ▸ Status checkbox
 * (BillingONStatusERUpdateStatus); Administration ▸ Payment Received (BillingONPayment);
 * Master Record ▸ Billing History ▸ invoice # (ViewBillingONDisplay); Schedule ▸ owned
 * appointment "B" (billing router ▸ billingView) ▸ billing history Go
 * (ViewBillingONHistorySpec).
 *
 * Asserts: each status filter lists exactly the owned bills carrying that status and a
 * date window before the bill date lists neither; Save & settle moves the owned PAT bill
 * to status S with paid = total in billing_on_cheader1 and the report follows; the
 * rejected-claim checkbox flips billing_on_eareport.status Y/N and its cell text; a GET
 * against that mutator is refused; Payment Received, the bill display and the bill
 * form's history popup show the owned bills with their status and amounts.
 *
 * Fixtures: an owned PAT bill (status P) and an owned HCP bill (status O) seeded with
 * seedOwnedBill from billing-on-invoice-third-party, one owned error-report row for the HCP
 * bill, one owned appointment today for the test provider. Cleanup deletes them by
 * id/marker and asserts they are gone. Implements coverage-plan §2.7
 * billing-on-payment-status.
 */

const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const {
  seedOwnedBill, openHistory, historyRow, runInvoiceReport, fillDate, awaitResponse, billDate, money,
} = require('./billing-on-invoice-third-party-playwright-checks');

const ER_ROUTE = '/billing/CA/ON/BillingONStatusERUpdateStatus';

/** Schedule ▸ Administration, then one Billing left-nav item loaded into its iframe. */
async function openAdminFrame(s, route, ready, admin) {
  if (!admin || admin.isClosed()) {
    ({ page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'billing-administration', timeout: 20000 }));
  }
  const link = admin.locator(`a[rel$="${route}"]`).first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor({ timeout: 20000 });
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, `${route} did not load in the administration frame`);
  await frame.locator(ready).waitFor({ state: 'visible', timeout: 20000 });
  return { admin, frame };
}

/** The bill-list rows of the report that link to one owned invoice. */
function reportRows(frame, headerId) {
  return frame.locator('#bListTable tbody tr').filter({
    has: frame.locator(`a[onclick*="BillingONCorrection?billing_no=${headerId}'"]`),
  });
}

async function cellsOf(row) {
  return (await row.first().locator('td').allInnerTexts()).map(text => text.trim());
}

async function workflow(s) {
  const { sql, patient, marker, context } = s;
  const date = billDate();
  const fee = code => sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)}
    AND billingservice_date<=${h.sqlString(date)} ORDER BY billingservice_date DESC LIMIT 1`);
  const privateCode = (process.env.BILLING_PRIVATE_CODE || '_OMA_A007').toUpperCase();
  const ohipCode = (process.env.BILLING_CORRECTION_CODE || 'A007A').toUpperCase();
  h.assert(/^_[A-Z0-9_]{1,9}$/.test(privateCode) && /^[A-Z]\d{3}[A-Z]$/.test(ohipCode), 'Billing code overrides are malformed');
  const privateFee = fee(privateCode);
  const ohipFee = fee(ohipCode);
  if (!(Number(privateFee) > 0) || !(Number(ohipFee) > 0)) {
    throw new h.SkipCheck(`${privateCode} or ${ohipCode} has no fee on ${date}`);
  }

  const pat = seedOwnedBill(s, { payProgram: 'PAT', status: 'P', code: privateCode, fee: privateFee, date });
  const hcp = seedOwnedBill(s, { payProgram: 'HCP', status: 'O', code: ohipCode, fee: ohipFee, date, dx: '401' });
  const patTotal = money(privateFee);
  const header = id => sql.value(`SELECT CONCAT_WS('|', status, pay_program, total, paid) FROM billing_on_cheader1 WHERE id=${id}`);

  // One rejected-claim row for the HCP bill, under the billing provider's OHIP
  // number and default group/specialty (BillingOnLookupService defaults).
  const errorId = sql.value(`INSERT INTO billing_on_eareport (providerohip_no, group_no, specialty, process_date, hin,
      ver, dob, billing_no, ref_no, facility, admitted_date, claim_error, code, fee, unit, code_date, dx, exp,
      code_error, report_name, status, comment)
    VALUES (${h.sqlString(hcp.ohip)}, '0000', '00', ${h.sqlString(date)}, '', '', NULL, ${hcp.headerId}, '', '', NULL,
      'EH1', ${h.sqlString(ohipCode)}, ${h.sqlString(money(ohipFee))}, '1', ${h.sqlString(date)}, '401', '',
      'EH1', 'FAKE-PW-ER', 'N', ${h.sqlString(marker)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(errorId), 'The error-report fixture was not created');
  const erStatus = () => sql.value(`SELECT status FROM billing_on_eareport WHERE id=${errorId}`);

  // An appointment today for the test login, so its day sheet offers the "B" link.
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointment WHERE demographic_no=${patient} AND name=${h.sqlString(`${marker},Workflow`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient}`) === '0',
      'The owned appointment was not removed');
  });
  const appointment = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
      demographic_no, program_id, notes, reason, location, resources, type, style, billing, status, createdatetime,
      updatedatetime, creator, remarks, urgency)
    VALUES (${h.sqlString(s.provider)}, CURDATE(), '13:15:00', '13:29:00', ${h.sqlString(`${marker},Workflow`)}, ${patient},
      0, '', 'Billing workflow', '', '', '', '', '', 't', NOW(), NOW(), ${h.sqlString(s.provider)}, '', '');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(appointment), 'The appointment fixture was not created');

  await s.step('day sheet "B" opens the Ontario bill form whose history popup lists both owned bills', async () => {
    await s.schedule.reload({ waitUntil: 'domcontentloaded' }); // shows the appointment seeded after login
    const link = s.schedule.locator(`a[onclick*="appointment_no=${appointment}&"][onclick*="/billing?"]`).first();
    h.assert(await link.count() === 1, 'The day sheet does not offer the B link for the owned appointment');
    const form = await s.popup(s.schedule, link, 'bill-form');
    await form.locator('select[name="xml_billtype"]').waitFor({ state: 'visible', timeout: 30000 });
    h.assert(new URL(form.url()).searchParams.get('appointment_no') === appointment, 'The bill form opened for another appointment');
    await form.locator('input[name="day"]').fill('30');
    const spec = await s.popup(form, form.locator('input[name="buttonDay"]'), 'billing-history-spec');
    await spec.locator('form[name="titlesearch"]').waitFor({ state: 'visible' });
    // seedOwnedBill validates both fixture header IDs as digits before returning them.
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
    const specRow = id => spec.locator('tbody tr').filter({ has: spec.locator('td', { hasText: new RegExp(`^\\s*${id}\\s*$`) }) });
    h.assert(await specRow(pat.headerId).count() === 1 && await specRow(hcp.headerId).count() === 1,
      'The bill form history did not list both owned bills');
    const patCells = await cellsOf(specRow(pat.headerId));
    const hcpCells = await cellsOf(specRow(hcp.headerId));
    h.assert(patCells[1] === date && patCells[2] === 'Bill Patient' && patCells[3].startsWith(`${privateCode} x 1`) && patCells[5] === patTotal,
      'The history row of the PAT bill does not show Bill Patient, its code and date');
    h.assert(hcpCells[2] === 'Bill OHIP' && hcpCells[3].startsWith(`${ohipCode} x 1`) && hcpCells[4] === '401',
      'The history row of the OHIP bill does not show Bill OHIP, its code and dx');
    await spec.locator('input[name="serviceCode"]').fill(ohipCode);
    await Promise.all([
      spec.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      spec.locator('input[type="submit"][value="Search"]').click(),
    ]);
    h.assert(await specRow(hcp.headerId).count() === 1 && await specRow(pat.headerId).count() === 0,
      'The service-code filter did not narrow the history to the OHIP bill');
    await spec.close();
    await form.close();
    h.assert(sql.value(`SELECT status FROM appointment WHERE appointment_no=${appointment}`) === 't',
      'Opening the bill form without saving changed the appointment status');
  });
  const { admin, frame: report } = await openAdminFrame(s, '/billing/CA/ON/ViewBillStatus', 'form[name="serviceform"]');
  const search = opts => runInvoiceReport(admin, report, { date, billingProvider: pat.provider, demographic: patient, ...opts });

  await s.step('Invoice Reports status filters list only the owned bills carrying that status', async () => {
    await search({ statusId: 'statusTypeBillPatient' });
    h.assert(await reportRows(report, pat.headerId).count() === 1, 'Bill Patient did not list the owned PAT bill');
    h.assert(await reportRows(report, hcp.headerId).count() === 0, 'Bill Patient listed the owned OHIP bill');
    const patCells = await cellsOf(reportRows(report, pat.headerId));
    h.assert(patCells.includes('P') && patCells.includes('PAT') && patCells.includes(privateCode),
      'The PAT row does not show status P, PAT and its code');
    await search({ statusId: 'statusTypeInvoiced' });
    h.assert(await reportRows(report, hcp.headerId).count() === 1, 'Invoiced did not list the owned OHIP bill');
    h.assert(await reportRows(report, pat.headerId).count() === 0, 'Invoiced listed the owned PAT bill');
    const before = new Date(Date.parse(`${date}T00:00:00Z`) - 3 * 86400000).toISOString().slice(0, 10);
    const dayBefore = new Date(Date.parse(`${date}T00:00:00Z`) - 86400000).toISOString().slice(0, 10);
    await search({ statusId: 'statusTypeAll', date: before, endDate: dayBefore });
    h.assert(await reportRows(report, hcp.headerId).count() + await reportRows(report, pat.headerId).count() === 0,
      'A date window ending before the bill date still listed an owned bill');
  });

  await s.step('Save & settle from the report moves the PAT bill to status S with paid equal to the total', async () => {
    await search({ statusId: 'statusTypeBillPatient' });
    const link = reportRows(report, pat.headerId).locator(`a[onclick*="BillingONCorrection?billing_no=${pat.headerId}'"]`).first();
    const correction = await s.popup(admin, link, 'billing-correction');
    await correction.locator('form[action$="/billing/CA/ON/UpdateBillingONCorrection"]').waitFor({ state: 'visible' });
    // The report opens the correction in window "billcorrection" and the correction
    // opens Payments List under the same name, so the list replaces the correction.
    const { page: payments } = await ui.clickOpensPopupOrNavigates(correction,
      correction.locator('#thirdPartyPymnt a', { hasText: 'Payments List' }),
      { context, recorder: s.recorder, label: 'third-party-payments', timeout: 20000 });
    await payments.locator('#editPayment').waitFor({ state: 'visible' });
    h.assert(await payments.locator('input[name="itemId0"]').inputValue() === pat.itemId, 'The payment form is not for the owned item');
    await payments.locator('#payment0').fill(patTotal);
    await payments.locator('#payment0').dispatchEvent('change');
    let response;
    const dialogs = await h.withExpectedDialogs(payments, async () => {
      const reloaded = payments.waitForEvent('load', { timeout: 20000 });
      [response] = await Promise.all([
        awaitResponse(context, '/billing/CA/ON/billingON3rdPayments'),
        payments.locator('#saveAndSettleBtn').click(),
      ]);
      await reloaded;
    });
    h.assert(response.status() === 200 && new URLSearchParams(response.request().postData() || '').get('status') === 'S',
      'Save & settle did not post status S');
    h.assert(dialogs.length === 1 && dialogs[0].text === 'Save payments successfully!', 'Save & settle did not report success once');
    await expectValue(sql, `SELECT CONCAT_WS('|', status, pay_program, total, paid) FROM billing_on_cheader1
      WHERE id=${pat.headerId}`, `S|PAT|${patTotal}|${patTotal}`, 'Save & settle did not settle the bill with paid = total');
    h.assert(sql.value(`SELECT CONCAT_WS('|', COUNT(*), SUM(total_payment)) FROM billing_on_payment
      WHERE billing_no=${pat.headerId}`) === `1|${patTotal}`, 'Save & settle did not write one full billing_on_payment row');
    h.assert(header(hcp.headerId) === `O|HCP|${money(ohipFee)}|0.00`, 'Settling the PAT bill changed the OHIP bill');
    if (!payments.isClosed()) await payments.close();
    if (!correction.isClosed()) await correction.close();
  });

  await s.step('the report follows the settlement: Settled/Paid lists it with its paid amount, Bill Patient no longer does', async () => {
    await search({ statusId: 'statusTypeSettled' });
    h.assert(await reportRows(report, pat.headerId).count() === 1, 'Settled/Paid did not list the settled bill');
    const cells = await cellsOf(reportRows(report, pat.headerId));
    h.assert(cells[4] === 'S' && cells.filter(cell => cell.replace(/[^\d.]/g, '') === patTotal).length >= 2,
      'The settled row does not show status S with billed and paid amounts');
    await search({ statusId: 'statusTypeBillPatient' });
    h.assert(await reportRows(report, pat.headerId).count() === 0, 'Bill Patient still lists the settled bill');
  });

  await s.step('Rejected lists the owned error row and its Status checkbox flips billing_on_eareport.status', async () => {
    await search({ statusId: 'statusTypeRejected', billingProvider: hcp.provider });
    const row = report.locator(`#BillingErrorRow_${errorId}`);
    h.assert(await row.count() === 1, 'Rejected did not list the owned error-report row');
    h.assert((await row.innerText()).includes(hcp.headerId), 'The rejected row does not name the owned invoice');
    const box = report.locator(`#status${errorId}`);
    h.assert(!await box.isChecked() && erStatus() === 'N', 'The owned error row did not start unchecked');
    const toggle = async (expected, text) => {
      const [response] = await Promise.all([awaitResponse(context, ER_ROUTE), box.click()]);
      h.assert(response.status() === 200, `${ER_ROUTE} answered HTTP ${response.status()}`);
      await expectValue(sql, `SELECT status FROM billing_on_eareport WHERE id=${errorId}`, expected,
        `The checkbox did not store status ${expected}`);
      await report.locator(`td[id="${errorId}"]`).filter({ hasText: text }).waitFor({ timeout: 10000 });
    };
    await toggle('Y', 'checked');
    await toggle('N', 'uncheck');
    const probe = await context.request.get(h.appUrl(s.config.baseUrl, ER_ROUTE),
      { params: { id: errorId, val: 'Y' }, maxRedirects: 0 });
    h.assert(probe.status() === 405 && probe.headers().allow === 'POST', `${ER_ROUTE} must reject GET with Allow: POST`);
    h.assert(erStatus() === 'N', 'A refused GET changed the error-report status');
    h.assert(header(hcp.headerId) === `O|HCP|${money(ohipFee)}|0.00`, 'Acknowledging the error row changed the OHIP bill');
  });

  await s.step('Billing History ▸ invoice # displays the settled bill, and the invoice field loads the other bill', async () => {
    const history = await openHistory(s);
    const row = historyRow(history, pat.headerId);
    await row.waitFor({ state: 'visible' });
    const display = await s.popup(history,
      row.locator(`a[onclick*="ViewBillingONDisplay?billing_no=${pat.headerId}'"]`), 'billing-display');
    await display.locator('input[name="billing_no"]').waitFor({ state: 'visible' });
    h.assert(await display.locator('input[name="billing_no"]').inputValue() === pat.headerId, 'The display opened another bill');
    h.assert(await display.locator('select[name="status"]').inputValue() === 'S', 'The display does not show status S');
    h.assert(await display.locator('input[name="servicecode0"]').inputValue() === privateCode, 'The display does not list the item code');
    await display.locator('input[name="billing_no"]').fill(hcp.headerId);
    await Promise.all([
      display.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      display.locator('input[name="billing_no"]').press('Enter'),
    ]);
    h.assert(await display.locator('input[name="billing_no"]').inputValue() === hcp.headerId
      && await display.locator('select[name="status"]').inputValue() === 'O'
      && await display.locator('input[name="servicecode0"]').inputValue() === ohipCode, 'The invoice field did not load the OHIP bill');
    await display.close();
    await history.close();
  });

  // A report end date includes the whole calendar day, and excludes the next midnight.
  await s.step('Administration ▸ Payment Received lists the settled 3rd-party bill with its payment', async () => {
    const { frame } = await openAdminFrame(s, '/billing/CA/ON/BillingONPayment', 'form[name="billingPaymentForm"]', admin);
    const today = sql.value('SELECT CURDATE()');
    const tomorrow = sql.value('SELECT CURDATE() + INTERVAL 1 DAY');
    const paymentRow = () => frame.locator('tr').filter({ has: frame.locator(`a[onclick*="billing_no=${pat.headerId}'"]`) });
    const generate = async (start, end) => {
      await frame.locator('select[name="providerList"]').selectOption(pat.provider);
      await fillDate(frame, '#startDateText', start);
      await fillDate(frame, '#endDateText', end);
      const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
      navigated.catch(() => {});
      await frame.locator('form[name="billingPaymentForm"] input[type="submit"]').click();
      await navigated;
      await frame.waitForLoadState('networkidle', { timeout: 30000 });
    };
    await generate(today, tomorrow);
    h.assert(await paymentRow().count() === 1, 'Payment Received (today to tomorrow) did not list the settled bill exactly once');
    const cells = (await cellsOf(paymentRow())).map(cell => cell.replace(/[^\d.-]/g, ''));
    h.assert(cells.filter(cell => cell === patTotal).length >= 2, 'The 3rd-party row does not show the billed and paid total');
    h.assert(await frame.locator('tr').filter({ has: frame.locator(`a[onclick*="billing_no=${hcp.headerId}'"]`) }).count() === 0,
      'Payment Received listed the unpaid OHIP bill');
    // The report's default window ends today; a payment received today must be in it.
    await generate(today, today);
    h.assert(await paymentRow().count() === 1,
      'Payment Received with End Date = the payment date omitted the payment');
    const sameDayCells = (await cellsOf(paymentRow())).map(cell => cell.replace(/[^\d.-]/g, ''));
    h.assert(sameDayCells.filter(cell => cell === patTotal).length >= 2,
      'The single-day report did not include both the billed and paid amount');

    const paymentId = sql.value(`SELECT payment_id FROM billing_on_payment WHERE billing_no=${pat.headerId}`);
    h.assert(/^[1-9]\d*$/.test(paymentId), 'The owned bill must have exactly one payment');
    const reportPaidTotal = async () => {
      const footer = frame.locator('table.table-striped').last().locator('tbody > tr').last();
      const value = Number((await footer.locator('td').nth(4).innerText()).trim());
      h.assert(Number.isFinite(value), 'The third-party paid total is not numeric');
      return value;
    };
    for (const time of ['00:00:00', '12:34:56', '23:59:59']) {
      sql.execute(`UPDATE billing_on_payment SET pay_date=${h.sqlString(`${today} ${time}`)}
        WHERE payment_id=${paymentId} AND billing_no=${pat.headerId}`);
      await generate(today, today);
      h.assert(await paymentRow().count() === 1, `Payment Received omitted the End Date payment at ${time}`);
    }
    const includedTotal = await reportPaidTotal();
    sql.execute(`UPDATE billing_on_payment SET pay_date=${h.sqlString(`${tomorrow} 00:00:00`)}
      WHERE payment_id=${paymentId} AND billing_no=${pat.headerId}`);
    await generate(today, today);
    h.assert(await paymentRow().count() === 0, 'Payment Received included midnight after the End Date');
    h.assert(Math.round((includedTotal - await reportPaidTotal()) * 100) === Math.round(Number(patTotal) * 100),
      'The report total did not change by the owned payment amount at the end-date boundary');
    await generate(today, tomorrow);
    h.assert(await paymentRow().count() === 1, 'Advancing End Date did not include the next-day payment');
    const yesterday = sql.value('SELECT CURDATE() - INTERVAL 1 DAY');
    sql.execute(`UPDATE billing_on_payment SET pay_date=${h.sqlString(`${yesterday} 23:59:59`)}
      WHERE payment_id=${paymentId} AND billing_no=${pat.headerId}`);
    await generate(today, today);
    h.assert(await paymentRow().count() === 0, 'Payment Received included a payment before the Start Date');
  });
}

if (require.main === module) runWorkflow('billing-on-payment-status', workflow, { openPatient: true });
module.exports = { workflow };
