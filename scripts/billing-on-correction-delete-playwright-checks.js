#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario bill correction, status change and unbill (delete) workflow check.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Billing History (popup) ▸ Edit
 * (billingONCorrection.jsp popup) ▸ Save; Schedule ▸ Administration ▸ Billing ▸
 * Invoice Reports (ViewBillStatus → ViewBillingONStatus in the #myFrame iframe)
 * ▸ Create Report; Master Record ▸ Billing History ▸ Unbill (confirm).
 *
 * Asserts, against MariaDB after every round trip: a units + diagnostic-code
 * correction rewrites the billing_on_item row (units, fee = schedule fee x
 * units, dx) and the header total, and leaves a billing_on_repo audit snapshot
 * of the item's BEFORE state; a status change through the correction form's
 * status control lands on billing_on_cheader1 with a header audit snapshot;
 * the bill-status report lists the owned bill with its new status; GET against
 * the two mutators answers 405 with Allow: POST and writes nothing; Billing
 * History offers no Unbill for an owned bill in status B and the server refuses
 * the unbill post its opener would send for it (billCode B) without changing it;
 * Unbill flips header and items to status D and writes the two billing_on_proc
 * audit rows.
 *
 * Fixtures: two owned bills (header + one item each, comment1 = the run marker,
 * one open and one already billed, status B)
 * seeded for the owned FAKE- patient under an active provider that carries an
 * OHIP number (the correction page only offers such providers), seeded by the
 * shared seedOwnedBill helper (billing-on-invoice-3rdparty). Its cleanup
 * deletes the owned repo/proc/eareport/transaction/item-payment/payment/ext/
 * item/header rows by id and marker, and any provider-site memberships it
 * added, and asserts they are gone.
 *
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md §2.7
 * billing-on-correction-delete.
 *
 * Environment: the common contract in lib/playwright-harness.js readConfig().
 * Optional BILLING_CORRECTION_CODE (default A007A): an OHIP code with a
 * billingservice fee on or before the bill date.
 */

const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
// Shared Ontario bill fixture and page helpers: one seeding shape and one complete
// cleanup (repo/proc/eareport/transaction/item_payment/payment/ext/item/header).
const {
  seedOwnedBill, openHistory, fillDate, awaitResponse, billDate, money,
} = require('./billing-on-invoice-3rdparty-playwright-checks');

const UNBILL_CONFIRM = 'You are about to delete the previous billing, are you sure?';
const CORRECTION_FORM = 'form[action$="/billing/CA/ON/UpdateBillingONCorrection"]';

function serviceCode() {
  const code = (process.env.BILLING_CORRECTION_CODE || 'A007A').toUpperCase();
  h.assert(/^[A-Z]\d{3}[A-Z]$/.test(code), 'BILLING_CORRECTION_CODE must be an Ontario service code like A007A');
  return code;
}

/** The history row of one owned bill (its Edit link names the bill). */
function historyRow(history, headerId) {
  return history.locator('#billingHistoryTable tbody tr').filter({
    has: history.locator(`a[onclick*="BillingONCorrection?billing_no=${headerId}'"]`),
  });
}

/** History row ▸ Edit: the correction popup, loaded for the owned bill. */
async function openCorrection(s, history, headerId, provider) {
  const row = historyRow(history, headerId);
  await row.waitFor({ state: 'visible', timeout: 20000 });
  const popup = await s.popup(history, row.locator('a[title]', { hasText: 'Edit' }).first(), 'billing-correction');
  await popup.locator(CORRECTION_FORM).waitFor({ state: 'visible', timeout: 20000 });
  h.assert(await popup.locator(`${CORRECTION_FORM} input[name="xml_billing_no"]`).inputValue() === headerId,
    'The correction popup opened a bill other than the owned fixture');
  h.assert(await popup.locator('#provider_no').inputValue() === provider,
    'The correction page does not offer the bill\'s provider (only active providers with an OHIP number are listed)');
  return popup;
}

/** Click Save on the correction form and wait for the update POST to answer. */
async function saveCorrection(s, popup) {
  const [response] = await Promise.all([
    awaitResponse(s.context, '/billing/CA/ON/UpdateBillingONCorrection'),
    popup.locator(`${CORRECTION_FORM} input[type="submit"][value="Save"]`).click(),
  ]);
  h.assert(response.status() === 200, `The correction save answered HTTP ${response.status()}`);
}

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const code = serviceCode();
  const date = billDate();
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)}
    AND billingservice_date<=${h.sqlString(date)} ORDER BY billingservice_date DESC LIMIT 1`);
  if (!/^\d+(\.\d+)?$/.test(fee) || Number(fee) <= 0) {
    throw new h.SkipCheck(`service code ${code} has no positive schedule fee on ${date}; set BILLING_CORRECTION_CODE`);
  }
  const unitFee = money(fee);
  const feeTimesTwo = money(Number(fee) * 2);

  // One open OHIP (HCP) bill, dx 250, comment1 = marker. seedOwnedBill registers
  // the complete cleanup before its first insert, bills under an active provider
  // with an OHIP number (the correction page lists only those) and adds that
  // provider to the operator's sites it is missing from (_site_access_privacy).
  const bill = seedOwnedBill(s, { payProgram: 'HCP', status: 'O', code, fee, date });
  const { headerId, itemId, provider: billingProvider } = bill;
  // A second owned bill already submitted to OHIP (status B): the one Unbill must refuse.
  const billedBill = seedOwnedBill(s, { payProgram: 'HCP', status: 'B', code, fee, date });
  const billedSnapshot = () => sql.value(`SELECT CONCAT_WS('|', h.status, h.total, GROUP_CONCAT(i.status))
    FROM billing_on_cheader1 h JOIN billing_on_item i ON i.ch1_id=h.id WHERE h.id=${billedBill.headerId} GROUP BY h.id`);
  const headerSnapshot = () => sql.value(`SELECT CONCAT_WS('|', status, pay_program, total, demographic_no, provider_no,
    billing_date, hin) FROM billing_on_cheader1 WHERE id=${headerId}`);
  const itemSnapshot = () => sql.value(`SELECT CONCAT_WS('|', ser_num, fee, dx, status, service_code)
    FROM billing_on_item WHERE id=${itemId}`);
  const headerAudits = () => Number(sql.value(`SELECT COUNT(*) FROM billing_on_repo
    WHERE category='billing_on_cheader1' AND h_id=${headerId}`));
  const itemAudits = () => Number(sql.value(`SELECT COUNT(*) FROM billing_on_repo
    WHERE category='billing_on_item' AND h_id=${itemId}`));

  let history = await openHistory(s);

  await s.step('billing history lists the owned bill with its code, dx, Edit and Unbill controls', async () => {
    const row = historyRow(history, headerId);
    h.assert(await row.count() === 1, 'Billing History did not list exactly one row for the owned bill');
    const text = (await row.innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(code) && text.includes('250'), 'The history row does not show the service code and dx');
    h.assert(await row.locator('a', { hasText: 'Edit' }).count() === 1, 'The history row has no Edit link');
    h.assert(await row.locator('a', { hasText: 'Unbill' }).count() === 1, 'The history row has no Unbill link');
  });

  await s.step('correcting units and dx rewrites the item, the header total and leaves an item audit snapshot', async () => {
    const popup = await openCorrection(s, history, headerId, billingProvider);
    h.assert(await popup.locator('input[name="servicecode0"]').inputValue() === code, 'The correction form did not load the item code');
    h.assert(await popup.locator('input[name="billingunit0"]').inputValue() === '1', 'The correction form did not load the item units');
    h.assert(await popup.locator('input[name="xml_diagnostic_detail"]').inputValue() === '250', 'The correction form did not load the dx');
    h.assert(await popup.locator('#status').inputValue() === 'O', 'The correction form did not load the bill status');
    await popup.locator('input[name="billingunit0"]').fill('2');
    await popup.locator('input[name="xml_diagnostic_detail"]').fill('401');
    const auditsBefore = headerAudits();
    await saveCorrection(s, popup);
    await expectValue(sql, `SELECT CONCAT_WS('|', ser_num, fee, dx, status) FROM billing_on_item WHERE id=${itemId}`,
      `2|${feeTimesTwo}|401|O`, 'The correction did not rewrite the item units, fee and dx');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_item WHERE ch1_id=${headerId}`) === '1',
      'The correction added or removed item rows instead of updating the existing one');
    h.assert(sql.value(`SELECT CONCAT_WS('|', total, status, demographic_no, provider_no) FROM billing_on_cheader1
      WHERE id=${headerId}`) === `${feeTimesTwo}|O|${patient}|${billingProvider}`,
    'The header total was not recomputed from the corrected item, or its identity changed');
    h.assert(itemAudits() === 1, 'Exactly one billing_on_repo snapshot of the corrected item was expected');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_repo WHERE category='billing_on_item' AND h_id=${itemId}
      AND content LIKE ${h.sqlString(`HE|T|${code}|${unitFee}|1|%|250|%`)}`) === '1',
    'The item audit snapshot does not carry the pre-correction fee, units and dx');
    h.assert(headerAudits() - auditsBefore <= 1, 'A single correction wrote more than one header audit snapshot');
    if (!popup.isClosed()) await popup.close();
  });

  await s.step('the status control changes the bill status and leaves a header audit snapshot', async () => {
    // close.html reloads the history page when the popup closes; find the row afresh.
    const popup = await openCorrection(s, history, headerId, billingProvider);
    const auditsBefore = headerAudits();
    const itemAuditsBefore = itemAudits();
    await popup.locator('#status').selectOption('W');
    await saveCorrection(s, popup);
    await expectValue(sql, `SELECT CONCAT_WS('|', status, pay_program, total) FROM billing_on_cheader1 WHERE id=${headerId}`,
      `W|HCP|${feeTimesTwo}`, 'The status change did not reach billing_on_cheader1');
    h.assert(headerAudits() === auditsBefore + 1, 'The status change did not write exactly one header audit snapshot');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_repo WHERE category='billing_on_cheader1' AND h_id=${headerId}
      AND content LIKE ${h.sqlString(`%|O|${marker}|%`)}`) !== '0',
    'No header audit snapshot carries the pre-change status O');
    h.assert(itemAudits() === itemAuditsBefore, 'A status-only change wrote an item audit snapshot');
    h.assert(itemSnapshot() === `2|${feeTimesTwo}|401|O|${code}`, 'A status-only change altered the item');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_payment WHERE billing_no=${headerId}`) === '0',
      'A status change to W created a payment record');
    if (!popup.isClosed()) await popup.close();
  });

  await s.step('Administration ▸ Billing ▸ Invoice Reports lists the owned bill with its new status', async () => {
    const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'billing-administration', timeout: 20000 });
    const link = admin.locator('a[rel$="/billing/CA/ON/ViewBillStatus"]').first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    await revealAuditLink(admin, link, 20000);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe').first();
    await iframe.waitFor({ timeout: 20000 });
    const status = await (await iframe.elementHandle()).contentFrame();
    h.assert(status, 'The bill status page did not load in the administration frame');
    await status.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
    await fillDate(status, '#xml_vdate', date);
    await fillDate(status, '#xml_appointment_date', date);
    await status.locator('input[name="demographicNo"]').fill(patient);
    // The report defaults to "O | Invoiced"; the bill now carries status W.
    await status.locator('#statusTypeAll').check();
    const providers = status.locator('select[name="providerview"]').last();
    const offered = await providers.locator('option').evaluateAll(options => options.map(option => option.value));
    if (offered.includes(billingProvider)) await providers.selectOption(billingProvider);
    else if (offered.includes('all')) await providers.selectOption('all');
    const navigated = admin.waitForEvent('framenavigated', { predicate: frame => frame === status, timeout: 30000 });
    navigated.catch(() => {});
    await status.locator('input[type="submit"][value="Create Report"]').click();
    await navigated;
    await status.waitForLoadState('networkidle', { timeout: 30000 });
    const rows = status.locator('tr').filter({ has: status.locator(`a[onclick*="BillingONCorrection?billing_no=${headerId}'"]`) });
    h.assert(await rows.count() === 1, 'The bill status report did not list exactly one row for the owned bill');
    const cells = (await rows.first().locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells.includes('W') && cells.includes(code) && cells.includes(patient),
      'The bill status row does not show the new status, the service code and the patient number');
    h.assert(headerSnapshot() === `W|HCP|${feeTimesTwo}|${patient}|${billingProvider}|${date}|`,
      'Rendering the bill status report changed the bill');
    if (admin !== s.schedule && !admin.isClosed()) await admin.close();
  });

  await s.step('GET against the correction and unbill mutators is refused and writes nothing', async () => {
    const before = { header: headerSnapshot(), item: itemSnapshot(), audits: headerAudits() + itemAudits() };
    const correction = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/UpdateBillingONCorrection'), {
      params: { xml_billing_no: headerId, status: 'S', payProgram: 'HCP', submit: 'Save', oldStatus: '' },
      maxRedirects: 0,
    });
    h.assert(correction.status() === 405 && correction.headers().allow === 'POST',
      'UpdateBillingONCorrection must reject GET with Allow: POST');
    const unbill = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/BillingDeleteNoAppt'), {
      params: { billing_no: headerId, billCode: 'W', dboperation: 'delete_bill', hotclick: '0' },
      maxRedirects: 0,
    });
    h.assert(unbill.status() === 405 && unbill.headers().allow === 'POST',
      'BillingDeleteNoAppt must reject GET with Allow: POST');
    // The documented refusal: a bill already billed (status B) cannot be unbilled. The history
    // hides its Unbill link; the server must refuse the post that link would send (billCode =
    // the bill's stored status).
    const billedRow = history.locator('#billingHistoryTable tbody tr').filter({
      has: history.locator(`a[onclick*="ViewBillingONDisplay?billing_no=${billedBill.headerId}'"]`),
    });
    await billedRow.waitFor({ state: 'visible', timeout: 20000 });
    h.assert(await billedRow.locator('a', { hasText: 'Unbill' }).count() === 0,
      'Billing History offers Unbill for a bill already in status B');
    const billedBefore = billedSnapshot();
    h.assert(billedBefore === `B|${money(fee)}|B`, 'The billed bill fixture is not in status B');
    const token = await ui.csrfTokenPresent(s.master);
    const billed = await s.context.request.post(h.appUrl(s.config.baseUrl, '/billing/CA/ON/BillingDeleteNoAppt'), {
      form: { 'CSRF-TOKEN': token, billing_no: billedBill.headerId, billCode: 'B', dboperation: 'delete_bill', hotclick: '0' },
      headers: { 'CSRF-TOKEN': token },
      maxRedirects: 0,
    });
    h.assert(billed.status() === 200 && /cannot delete billed items/i.test(await billed.text()),
      'Unbilling a bill flagged as billed was not refused with the cannot-delete page');
    h.assert(headerSnapshot() === before.header && itemSnapshot() === before.item
      && headerAudits() + itemAudits() === before.audits, 'A refused request changed the bill');
    h.assert(billedSnapshot() === billedBefore && sql.value(`SELECT COUNT(*) FROM billing_on_proc
      WHERE object=${h.sqlString(billedBill.headerId)}`) === '0', 'The refused unbill changed the billed bill');
  });

  await s.step('Unbill asks for confirmation, marks header and items deleted and writes the audit rows', async () => {
    if (!history.isClosed()) await history.close();
    history = await openHistory(s);
    const row = historyRow(history, headerId);
    await row.waitFor({ state: 'visible', timeout: 20000 });
    const procBefore = sql.value(`SELECT COUNT(*) FROM billing_on_proc WHERE object=${h.sqlString(headerId)}`);
    let response;
    const dialogs = await h.withExpectedDialogs(history, async () => {
      [response] = await Promise.all([
        awaitResponse(s.context, '/billing/CA/ON/BillingDeleteNoAppt'),
        row.locator('a', { hasText: 'Unbill' }).first().click(),
      ]);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text === UNBILL_CONFIRM,
      'Unbill must ask the bundle\'s confirmation question exactly once');
    h.assert(response.status() === 200, `Unbill answered HTTP ${response.status()}`);
    await expectValue(sql, `SELECT CONCAT_WS('|', status, pay_program, total) FROM billing_on_cheader1 WHERE id=${headerId}`,
      `D|HCP|${feeTimesTwo}`, 'Unbill did not mark the header deleted');
    h.assert(sql.value(`SELECT GROUP_CONCAT(status) FROM billing_on_item WHERE ch1_id=${headerId}`) === 'D',
      'Unbill did not mark the item deleted');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE id=${headerId}`) === '1',
      'Unbill removed the header row instead of flagging it');
    await expectValue(sql, `SELECT COUNT(*) FROM billing_on_proc WHERE object=${h.sqlString(headerId)}
      AND creator=${h.sqlString(provider)} AND action IN ('updateBillingStatus','updateBillingStatus-items')`,
    String(Number(procBefore) + 2), 'Unbill did not write its two billing_on_proc audit rows');
  });
}

if (require.main === module) runWorkflow('billing-on-correction-delete', workflow, { openPatient: true });
module.exports = { workflow, UNBILL_CONFIRM };
