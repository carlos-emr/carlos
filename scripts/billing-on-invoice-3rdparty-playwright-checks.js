#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario 3rd-party (private) invoice, bill-to address and payment workflow check.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Billing History (popup) ▸ Edit
 * (billingONCorrection) ▸ bill-to ▸ Save; Correction ▸ Payments List (billingON3rdPayments)
 * ▸ partial then full payment; Billing History ▸ Print (ViewBillingON3rdInv) ▸ Print PDF
 * (BillingInvoicePrint); Schedule ▸ Administration ▸ Billing ▸ Invoice Reports ▸ Bill
 * Patient ▸ tick ▸ Print (BillingInvoiceListPrint); Correction ▸ Payer
 * (ViewOnSearch3rdBillAddr) ▸ Add/Edit Address (OnAddEdit3rdAddr) ▸ Edit; Payer ▸ pick.
 *
 * Asserts against MariaDB: the bill-to lands in billing_on_ext; each payment writes
 * billing_on_payment / billing_on_item_payment / billing_on_transaction rows and moves
 * billing_on_cheader1.paid and the ext payment key, with the balance the popup and the
 * invoice show; both PDFs are real PDFs whose text carries the invoice number and total,
 * and printing appends the "Printed" note to comment1; a GET against the payment save is
 * refused and writes nothing; Add/Edit Address loads the owned address; picking it in the
 * Payer search fills and saves the bill-to (last: fails on a known defect, see report).
 *
 * Fixtures: one owned PAT bill (header + one private-code item, comment1 = marker) for
 * the owned FAKE- patient, one owned bill-to address (company = marker), the billing
 * provider's missing site memberships (as billing-on-correction-delete). Cleanup deletes
 * owned rows by id/marker, the server-side list-print PDFs of the owned invoice, and
 * asserts they are gone. Implements coverage-plan §2.7 billing-on-invoice-3rdparty.
 */

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const CORRECTION_FORM = 'form[action$="/billing/CA/ON/UpdateBillingONCorrection"]';
const PAYMENTS = '/billing/CA/ON/billingON3rdPayments';

function privateCode() {
  const code = (process.env.BILLING_PRIVATE_CODE || '_OMA_A007').toUpperCase();
  h.assert(/^_[A-Z0-9_]{1,9}$/.test(code), 'BILLING_PRIVATE_CODE must be a private code like _OMA_A007');
  return code;
}

/** Two days ago, so "not in the future" rules hold in any zone. */
function billDate() {
  return new Date(Date.now() - 2 * 86400000).toISOString().slice(0, 10);
}

const money = value => Number(value).toFixed(2);

/**
 * True when `text` shows `label` followed by `amount`. The currency sign is not
 * asserted: the payments popup formats with the JVM default locale and shows the
 * generic sign on a server without a country locale (reported separately).
 */
function amountShown(text, label, amount) {
  return text.split(label).slice(1).some(after => after.replace(/^\s*[^\d\s-]?\s*/, '').startsWith(amount));
}

/** Wait for the request the browser sends to `route`, from whichever page sends it. */
function awaitResponse(context, route, method = 'POST', timeout = 20000) {
  return context.waitForEvent('response', {
    timeout,
    predicate: r => r.request().method() === method && new URL(r.url()).pathname.endsWith(route),
  });
}

/**
 * Seed one owned Ontario bill for the owned patient and register its cleanup first.
 * Shape mirrors BillingOnHeaderCreationService (see billing-on-correction-delete).
 * Returns { headerId, itemId, provider, ohip }.
 */
function seedOwnedBill(s, { payProgram, status, code, fee, date, dx = '250' }) {
  const { sql, patient, marker, provider } = s;
  const owned = { headerIds: new Set() };
  s.cleanup(() => {
    const ids = new Set(sql.rows(`SELECT id FROM billing_on_cheader1 WHERE demographic_no=${patient}
      AND comment1 LIKE ${h.sqlString(`${marker}%`)}`).map(row => row[0]));
    for (const id of owned.headerIds) ids.add(id);
    for (const id of ids) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned billing header id is invalid');
      const items = sql.rows(`SELECT id FROM billing_on_item WHERE ch1_id=${id}`).map(row => row[0]);
      const itemIds = items.length ? items.map(Number).join(',') : '0';
      sql.execute(`DELETE FROM billing_on_repo WHERE (category='billing_on_item' AND h_id IN (${itemIds}))
          OR (category='billing_on_cheader1' AND h_id=${id});
        DELETE FROM billing_on_proc WHERE object=${h.sqlString(id)};
        DELETE FROM billing_on_eareport WHERE billing_no=${id};
        DELETE FROM billing_on_transaction WHERE ch1_id=${id};
        DELETE FROM billing_on_item_payment WHERE ch1_id=${id};
        DELETE FROM billing_on_payment WHERE billing_no=${id};
        DELETE FROM billing_on_ext WHERE billing_no=${id};
        DELETE FROM billing_on_item WHERE ch1_id=${id};
        DELETE FROM billing_on_cheader1 WHERE id=${id} AND demographic_no=${patient}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_cheader1 WHERE id=${id})
        + (SELECT COUNT(*) FROM billing_on_item WHERE ch1_id=${id})
        + (SELECT COUNT(*) FROM billing_on_ext WHERE billing_no=${id})
        + (SELECT COUNT(*) FROM billing_on_payment WHERE billing_no=${id})
        + (SELECT COUNT(*) FROM billing_on_item_payment WHERE ch1_id=${id})
        + (SELECT COUNT(*) FROM billing_on_transaction WHERE ch1_id=${id})
        + (SELECT COUNT(*) FROM billing_on_eareport WHERE billing_no=${id})
        + (SELECT COUNT(*) FROM billing_on_repo WHERE category='billing_on_cheader1' AND h_id=${id})`) === '0',
      'Owned billing fixture rows were not removed');
    }
  });

  // The correction page lists only active providers with an OHIP number.
  const billingProvider = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND ohip_no<>''
    ORDER BY (provider_no=${h.sqlString(provider)}) DESC, provider_no LIMIT 1`);
  if (!/^-?\d+$/.test(billingProvider)) throw new h.SkipCheck('no active provider with an OHIP number to bill under');
  const ohip = sql.value(`SELECT ohip_no FROM provider WHERE provider_no=${h.sqlString(billingProvider)}`);
  // With _site_access_privacy the correction page edits bills only of providers
  // sharing a site with the operator: add exactly the missing memberships.
  const addedSites = sql.rows(`SELECT s.site_id FROM providersite s WHERE s.provider_no=${h.sqlString(provider)}
    AND NOT EXISTS (SELECT 1 FROM providersite p WHERE p.provider_no=${h.sqlString(billingProvider)}
    AND p.site_id=s.site_id)`).map(row => row[0]);
  if (addedSites.length && billingProvider !== provider) {
    s.cleanup(() => {
      for (const siteId of addedSites) {
        h.assert(/^\d+$/.test(siteId), 'Owned site id is invalid');
        sql.execute(`DELETE FROM providersite WHERE provider_no=${h.sqlString(billingProvider)} AND site_id=${siteId}`);
      }
      h.assert(sql.value(`SELECT COUNT(*) FROM providersite WHERE provider_no=${h.sqlString(billingProvider)}
        AND site_id IN (${addedSites.map(Number).join(',')})`) === '0', 'Owned provider-site memberships were not removed');
    });
    sql.execute(addedSites.map(siteId => `INSERT IGNORE INTO providersite (provider_no, site_id)
      VALUES (${h.sqlString(billingProvider)}, ${Number(siteId)})`).join(';'));
  }

  const headerId = sql.value(`INSERT INTO billing_on_cheader1 (header_id, transc_id, rec_id, hin, ver, dob, pay_program,
      payee, ref_num, facilty_num, admission_date, ref_lab_num, man_review, location, demographic_no, provider_no,
      appointment_no, demographic_name, sex, province, billing_date, billing_time, total, paid, status, comment1,
      visittype, provider_ohip_no, provider_rma_no, apptProvider_no, asstProvider_no, creator, clinic)
    VALUES (0, 'HE', 'H', '', '', '19800102', ${h.sqlString(payProgram)}, 'P', '', '', '', '', '', '', ${patient},
      ${h.sqlString(billingProvider)}, 0, ${h.sqlString(`${marker},Workflow`)}, '2', 'ON', ${h.sqlString(date)},
      '09:00:00', ${money(fee)}, 0.00, ${h.sqlString(status)}, ${h.sqlString(marker)}, '00', ${h.sqlString(ohip)}, '',
      '', '', ${h.sqlString(provider)}, NULL); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(headerId), 'The billing header fixture was not created');
  owned.headerIds.add(headerId);
  const itemId = sql.value(`INSERT INTO billing_on_item (ch1_id, transc_id, rec_id, service_code, fee, ser_num,
      service_date, dx, dx1, dx2, status)
    VALUES (${headerId}, 'HE', 'T', ${h.sqlString(code)}, ${h.sqlString(money(fee))}, '1', ${h.sqlString(date)},
      ${h.sqlString(dx)}, '', '', ${h.sqlString(status)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(itemId), 'The billing item fixture was not created');
  return { headerId, itemId, provider: billingProvider, ohip };
}

/** Master Record ▸ Billing History: the patient's bill list popup. */
async function openHistory(s) {
  const link = s.master.locator('a[onclick*="/billing/CA/ON/ViewBillingONHistory"]').first();
  h.assert(await link.count() === 1, 'The Master Record does not offer the Ontario Billing History link');
  const history = await s.popup(s.master, link, 'billing-history');
  await history.locator('#billingHistoryTable').waitFor({ state: 'visible', timeout: 20000 });
  return history;
}

/** The history row of one owned bill (its invoice-number link names the bill). */
function historyRow(history, headerId) {
  return history.locator('#billingHistoryTable tbody tr').filter({
    has: history.locator(`a[onclick*="ViewBillingONDisplay?billing_no=${headerId}'"]`),
  });
}

/** History row ▸ Edit: the correction popup for the owned bill. */
async function openCorrection(s, history, headerId) {
  const row = historyRow(history, headerId);
  await row.waitFor({ state: 'visible', timeout: 20000 });
  const popup = await s.popup(history, row.locator('a', { hasText: 'Edit' }).first(), 'billing-correction');
  await popup.locator(CORRECTION_FORM).waitFor({ state: 'visible', timeout: 20000 });
  h.assert(await popup.locator(`${CORRECTION_FORM} input[name="xml_billing_no"]`).inputValue() === headerId,
    'The correction popup opened a bill other than the owned fixture');
  return popup;
}

/** Type an ISO date into a flatpickr (allowInput) field and close its calendar. */
async function fillDate(frame, selector, value) {
  await frame.locator(selector).fill(value);
  await frame.locator('body').click({ position: { x: 4, y: 4 } });
  await frame.locator('.flatpickr-calendar.open').waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
  h.assert(await frame.locator(selector).inputValue() === value, `${selector} did not keep ${value}`);
}

/**
 * Schedule ▸ Administration ▸ Billing ▸ Invoice Reports, then run the report for
 * the owned patient, the bill date and one status filter. Returns { admin, frame }.
 */
async function openInvoiceReport(s, { date, statusId, billingProvider, admin }) {
  if (!admin || admin.isClosed()) {
    ({ page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'billing-administration', timeout: 20000 }));
  }
  const link = admin.locator('a[rel$="/billing/CA/ON/ViewBillStatus"]').first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor({ timeout: 20000 });
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'The bill status page did not load in the administration frame');
  await frame.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
  await runInvoiceReport(admin, frame, { date, statusId, billingProvider, demographic: s.patient });
  return { admin, frame };
}

/** Fill the Invoice Reports search and press Create Report inside its frame. */
async function runInvoiceReport(admin, frame, { date, endDate = date, statusId, billingProvider, demographic = '' }) {
  await fillDate(frame, '#xml_vdate', date);
  await fillDate(frame, '#xml_appointment_date', endDate);
  await frame.locator('input[name="demographicNo"]').fill(demographic);
  await frame.locator(`#${statusId}`).check();
  const providers = frame.locator('select[name="providerview"]').last();
  const offered = await providers.locator('option').evaluateAll(options => options.map(option => option.value));
  if (offered.includes(billingProvider)) await providers.selectOption(billingProvider);
  else if (offered.includes('all')) await providers.selectOption('all');
  const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
  navigated.catch(() => {});
  await frame.locator('input[type="submit"][value="Create Report"]').click();
  await navigated;
  await frame.waitForLoadState('networkidle', { timeout: 30000 });
}

/** Save a PDF download, assert its bytes and return its text. */
async function pdfText(download, label) {
  const file = await download.path();
  h.assert(file, `${label}: the download was not saved`);
  const bytes = fs.readFileSync(file);
  h.assert(bytes.subarray(0, 4).toString('latin1') === '%PDF', `${label}: the download is not a PDF`);
  return execFileSync('pdftotext', ['-layout', file, '-'], { encoding: 'utf8', timeout: 20000 });
}

async function workflow(s) {
  const { sql, marker, context } = s;
  const code = privateCode();
  const date = billDate();
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)}
    AND billingservice_date<=${h.sqlString(date)} ORDER BY billingservice_date DESC LIMIT 1`);
  if (!/^\d+(\.\d+)?$/.test(fee) || Number(fee) <= 1) {
    throw new h.SkipCheck(`private code ${code} has no fee on ${date}; set BILLING_PRIVATE_CODE`);
  }
  const total = money(fee);
  const partial = '40.00';
  const rest = money(Number(fee) - 40);

  // Owned bill-to address; the UI-added one (last step) is owned by name too.
  const company = `${marker} Insurer`;
  const added = `${marker} Added`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM billing_on_3rdPartyAddress WHERE company_name IN (${h.sqlString(company)},${h.sqlString(added)})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_3rdPartyAddress
      WHERE company_name IN (${h.sqlString(company)},${h.sqlString(added)})`) === '0', 'Owned bill-to addresses were not removed');
  });
  const addressId = sql.value(`INSERT INTO billing_on_3rdPartyAddress (attention, company_name, address, city, province,
      postcode, telephone, fax) VALUES ('Claims Desk', ${h.sqlString(company)}, '1 Fake Street', 'Hamilton', 'ON',
      'L0R 4K3', '555-555-0100', ''); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(addressId), 'The bill-to address fixture was not created');

  const bill = seedOwnedBill(s, { payProgram: 'PAT', status: 'P', code, fee, date });
  const { headerId, itemId } = bill;
  // Server-side copies BillingInvoiceListPrint leaves in INVOICE_DIR for this invoice.
  const invoiceDir = process.env.INVOICE_DIR || '/var/lib/carlos-emr/CarlosDocument/carlos/billing/invoices';
  const listPrintCopies = () => (fs.existsSync(invoiceDir) ? fs.readdirSync(invoiceDir) : [])
    .filter(name => name.startsWith(`BillingInvoice${headerId}_`) && name.endsWith('.pdf'));
  s.cleanup(() => {
    for (const name of listPrintCopies()) fs.rmSync(path.join(invoiceDir, name));
    h.assert(listPrintCopies().length === 0, 'Owned list-print PDF copies were not removed');
  });
  const headerState = () => sql.value(`SELECT CONCAT_WS('|', status, pay_program, total, paid) FROM billing_on_cheader1
    WHERE id=${headerId}`);
  const extValue = key => sql.value(`SELECT value FROM billing_on_ext WHERE billing_no=${headerId}
    AND key_val=${h.sqlString(key)} AND status='1' ORDER BY id DESC LIMIT 1`);

  let history = await openHistory(s);
  const typedBillTo = `Accounts Payable\n${company}\n1 Fake Street\nHamilton ON L0R 4K3`;

  /** Correction form ▸ Save, waiting for the update POST. */
  async function saveCorrection(correction) {
    const [response] = await Promise.all([
      awaitResponse(context, '/billing/CA/ON/UpdateBillingONCorrection'),
      correction.locator(`${CORRECTION_FORM} input[type="submit"][value="Save"]`).click(),
    ]);
    h.assert(response.status() === 200, `The correction save answered HTTP ${response.status()}`);
  }

  await s.step('a bill-to typed on the correction form of the private bill lands in billing_on_ext', async () => {
    const row = historyRow(history, headerId);
    await row.waitFor({ state: 'visible', timeout: 20000 });
    h.assert((await row.innerText()).includes(code), 'The history row does not show the private service code');
    const correction = await openCorrection(s, history, headerId);
    await correction.locator('#thirdParty').waitFor({ state: 'visible', timeout: 10000 });
    h.assert(await correction.locator('#billTo').inputValue() === '', 'The fixture bill already carried a bill-to');
    await correction.locator('#billTo').fill(typedBillTo);
    await saveCorrection(correction);
    await expectValue(sql, `SELECT COUNT(*) FROM billing_on_ext WHERE billing_no=${headerId} AND key_val='billTo'
      AND status='1' AND REPLACE(value, CHAR(13), '')=${h.sqlString(typedBillTo)}`, '1', 'The typed bill-to did not reach billing_on_ext');
    h.assert(headerState() === `P|PAT|${total}|0.00`, 'Saving the bill-to changed the bill status or amounts');
    if (!correction.isClosed()) await correction.close();
  });


  /** Correction ▸ Payments List popup for the owned bill. */
  async function openPayments() {
    if (history.isClosed()) history = await openHistory(s);
    const correction = await openCorrection(s, history, headerId);
    const link = correction.locator('#thirdPartyPymnt a', { hasText: 'Payments List' });
    h.assert(await link.count() === 1, 'The correction page of a 3rd-party bill offers no Payments List link');
    const payments = await s.popup(correction, link, 'third-party-payments');
    await payments.locator('#editPayment').waitFor({ state: 'visible' });
    return { correction, payments };
  }

  /** Enter one payment row and press Save, accepting the success alert and reload. */
  async function pay(payments, amount) {
    h.assert(await payments.locator('input[name="itemId0"]').inputValue() === itemId, 'The payment form is not for the owned item');
    await payments.locator('#sel0').selectOption('payment');
    await payments.locator('#payment0').fill(amount);
    await payments.locator('#payment0').dispatchEvent('change');
    const cash = payments.locator('#editPayment input[name="paymentType"]').first();
    await cash.check();
    let response;
    const dialogs = await h.withExpectedDialogs(payments, async () => {
      const reloaded = payments.waitForEvent('load', { timeout: 20000 });
      [response] = await Promise.all([
        awaitResponse(context, PAYMENTS),
        payments.locator('#saveBtn').click(),
      ]);
      await reloaded;
    });
    h.assert(response.status() === 200, `The payment save answered HTTP ${response.status()}`);
    h.assert(dialogs.length === 1 && dialogs[0].text === 'Save payments successfully!',
      'The payment save did not report success exactly once');
    await payments.locator('#editPayment').waitFor({ state: 'visible' });
  }

  await s.step('a partial payment writes the payment rows, raises paid and leaves the remaining balance', async () => {
    const { correction, payments } = await openPayments();
    await pay(payments, partial);
    await expectValue(sql, `SELECT CONCAT_WS('|', COUNT(*), SUM(total_payment)) FROM billing_on_payment
      WHERE billing_no=${headerId}`, `1|${partial}`, 'The partial payment did not write one billing_on_payment row');
    h.assert(sql.value(`SELECT CONCAT_WS('|', COUNT(*), SUM(paid)) FROM billing_on_item_payment WHERE ch1_id=${headerId}
      AND billing_on_item_id=${itemId}`) === `1|${partial}`, 'The partial payment did not write its item payment row');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_transaction WHERE ch1_id=${headerId}
      AND service_code_paid=${partial}`) === '1', 'The partial payment wrote no billing_on_transaction row');
    h.assert(headerState() === `P|PAT|${total}|${partial}`, 'The partial payment did not raise billing_on_cheader1.paid only');
    h.assert(Number(extValue('payment')) === Number(partial), 'The ext payment key does not carry the partial payment');
    const listed = (await payments.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(amountShown(listed, 'Balance:', rest), 'The payments popup does not show the remaining item balance');
    await payments.close();
    if (!correction.isClosed()) await correction.close();
  });

  await s.step('GET against the payment save is refused and writes nothing', async () => {
    const before = sql.value(`SELECT COUNT(*) FROM billing_on_payment WHERE billing_no=${headerId}`);
    const probe = await context.request.get(h.appUrl(s.config.baseUrl, PAYMENTS), {
      params: { method: 'savePayment', billingNo: headerId, size: '1', itemId0: itemId, sel0: 'payment',
        payment0: '1.00', discount0: '0.00', paymentType: '1', paymentDate: date },
      maxRedirects: 0,
    });
    h.assert(probe.status() === 405, `A GET payment save answered HTTP ${probe.status()}, not 405`);
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_payment WHERE billing_no=${headerId}`) === before
      && headerState() === `P|PAT|${total}|${partial}`, 'A refused GET payment save changed the bill');
  });

  await s.step('paying the rest brings paid to the total, balance to 0.00 and lists both payments', async () => {
    const { correction, payments } = await openPayments();
    await pay(payments, rest);
    await expectValue(sql, `SELECT CONCAT_WS('|', COUNT(*), SUM(total_payment)) FROM billing_on_payment
      WHERE billing_no=${headerId}`, `2|${total}`, 'The second payment did not write its billing_on_payment row');
    h.assert(headerState() === `P|PAT|${total}|${total}`, 'billing_on_cheader1.paid does not equal the total after full payment');
    h.assert(Number(extValue('payment')) === Number(total), 'The ext payment key does not carry the full payment');
    const text = (await payments.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(amountShown(text, 'Balance:', '0.00'), 'The payments popup does not show a zero item balance');
    h.assert(await payments.locator('a', { hasText: 'view' }).count() === 2, 'The payments list does not show both payments');
    await payments.close();
    if (!correction.isClosed()) await correction.close();
  });

  await s.step('Billing History ▸ Print shows the bill-to, the amounts and a zero balance', async () => {
    if (history.isClosed()) history = await openHistory(s);
    else await history.reload();
    const row = historyRow(history, headerId);
    await row.waitFor({ state: 'visible' });
    const invoice = await s.popup(history, row.locator('a', { hasText: 'Print' }).first(), 'third-party-invoice');
    await invoice.locator('#invoiceNo').waitFor({ state: 'attached' });
    const text = (await invoice.locator('body').innerText()).replace(/[ \t]+/g, ' ');
    h.assert(text.includes(`Invoice - ${headerId}`), 'The invoice does not name the owned invoice number');
    h.assert(text.includes(company) && text.includes('1 Fake Street'), 'The invoice does not show the picked bill-to address');
    h.assert(text.includes(code) && text.includes(total), 'The invoice does not list the service code and fee');
    const amount = label => invoice.locator('tr', { has: invoice.locator('td', { hasText: label }) }).last().locator('td').last();
    h.assert((await amount('Payments:').innerText()).trim() === total, 'The invoice payments line is not the paid total');
    h.assert((await amount('Balance:').innerText()).trim() === '0.00', 'The invoice balance is not 0.00');

    const commentBefore = sql.value(`SELECT comment1 FROM billing_on_cheader1 WHERE id=${headerId}`);
    const [download] = await Promise.all([
      invoice.waitForEvent('download', { timeout: 30000 }),
      invoice.locator('input[name="printInvoice"]').click(),
    ]);
    const pdf = await pdfText(download, 'BillingInvoicePrint');
    h.assert(pdf.includes(headerId) && pdf.includes(total), 'The invoice PDF does not carry the invoice number and total');
    await expectValue(sql, `SELECT comment1 LIKE ${h.sqlString(`${commentBefore}\nPrinted%`)} FROM billing_on_cheader1
      WHERE id=${headerId}`, '1', 'Printing the invoice did not append the Printed note to the bill comment');
    await invoice.close();
  });

  await s.step('Invoice Reports ▸ Bill Patient lists the bill and its Print action downloads a list PDF', async () => {
    const { admin, frame } = await openInvoiceReport(s, { date, statusId: 'statusTypeBillPatient', billingProvider: bill.provider });
    const box = frame.locator(`#invoiceAction${headerId}`);
    h.assert(await box.count() === 1, 'The Bill Patient report offers no print checkbox for the owned 3rd-party bill');
    const row = frame.locator('#bListTable tbody tr').filter({ has: box });
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells.includes('P') && cells.includes('PAT') && cells.includes(code), 'The report row does not show status P, PAT and the code');
    await box.check();
    const copiesBefore = listPrintCopies().length;
    const [download] = await Promise.all([
      admin.waitForEvent('download', { timeout: 30000 }),
      frame.locator('a[onclick*="submitForm(\'print\')"]').click(),
    ]);
    const pdf = await pdfText(download, 'BillingInvoiceListPrint');
    h.assert(pdf.includes(headerId) && pdf.includes(total), 'The invoice list PDF does not carry the owned invoice');
    h.assert(listPrintCopies().length === copiesBefore + 1, 'The list print did not render the owned invoice exactly once');
    if (admin !== s.schedule && !admin.isClosed()) await admin.close();
  });


  await s.step('Payer search ▸ Add/Edit Address lists the owned address and Edit loads its fields', async () => {
    if (history.isClosed()) history = await openHistory(s);
    const correction = await openCorrection(s, history, headerId);
    const search = await s.popup(correction, correction.locator('#thirdParty a[onclick*="search3rdParty"]').first(), 'bill-to-search');
    await search.locator('form[name="titlesearch"]').waitFor({ state: 'visible' });
    await Promise.all([
      search.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      search.locator('form[action$="/billing/CA/ON/OnAddEdit3rdAddr"] button[type="submit"]').click(),
    ]);
    // The legacy forms sit directly inside <table>, so the parser leaves their
    // controls outside the <form> element: address the controls themselves.
    const chooser = search.locator('select#company_name');
    await chooser.waitFor({ state: 'visible' });
    h.assert(await chooser.locator('option', { hasText: company }).count() === 1,
      'Add/Edit Address does not offer the owned address in its chooser');
    await chooser.selectOption(company);
    await Promise.all([
      search.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      search.locator('input[type="submit"][name="action"]').click(),
    ]);
    const form = search.locator('body');
    h.assert((await search.locator('th').first().innerText()).includes('You can edit the name'),
      'Choosing the owned address did not open it for editing');
    h.assert(await form.locator('input[type="text"][name="company_name"]').inputValue() === company
      && await form.locator('input[name="attention"]').inputValue() === 'Claims Desk'
      && await form.locator('input[name="postcode"]').inputValue() === 'L0R 4K3'
      && await form.locator('input[name="id"]').inputValue() === addressId,
    'The address editor did not load the owned address fields');
    await search.close();
    if (!correction.isClosed()) await correction.close();
  });

  // Last: picking an address in the Payer search (a known defect, see the report).
  await s.step('Payer search lists the owned address and picking it fills and saves the bill-to', async () => {
    if (history.isClosed()) history = await openHistory(s);
    const correction = await openCorrection(s, history, headerId);
    await correction.locator('#thirdParty').waitFor({ state: 'visible', timeout: 10000 });
    const search = await s.popup(correction, correction.locator('#thirdParty a[onclick*="search3rdParty"]').first(), 'bill-to-search');
    const billTo = `Claims Desk\n${company}\n`;
    await search.locator('form[name="titlesearch"] input[name="keyword"]').waitFor({ state: 'visible' });
    await search.locator('form[name="titlesearch"] input[name="keyword"]').fill(marker);
    await Promise.all([
      search.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      search.locator('form[name="titlesearch"] input[type="submit"][value="Search"]').click(),
    ]);
    const hit = search.locator('tr[onclick*="typeInData1"]').filter({ hasText: 'Claims Desk' });
    h.assert(await hit.count() === 1, 'The bill-to search did not list exactly the owned address');
    const closed = search.waitForEvent('close', { timeout: 10000 });
    await hit.click();
    await closed.catch(() => {});
    h.assert(search.isClosed(), 'Clicking the owned address did not hand it back to the correction form (the search popup stayed open)');
    const filled = await correction.locator('#billTo').inputValue();
    h.assert(filled.startsWith(billTo) && filled.includes('1 Fake Street') && filled.includes('L0R 4K3'),
      'Picking the address did not write it into the correction form\'s bill-to box');
    await saveCorrection(correction);
    await expectValue(sql, `SELECT COUNT(*) FROM billing_on_ext WHERE billing_no=${headerId} AND key_val='billTo'
      AND status='1' AND REPLACE(value, CHAR(13), '') LIKE ${h.sqlString(`${billTo}%1 Fake Street%`)}`, '1',
    'The picked bill-to address did not reach billing_on_ext');
    h.assert(headerState() === `P|PAT|${total}|${total}`, 'Saving the bill-to changed the bill status or amounts');
    if (!correction.isClosed()) await correction.close();
  });
}

if (require.main === module) runWorkflow('billing-on-invoice-3rdparty', workflow, { openPatient: true });
module.exports = {
  workflow, seedOwnedBill, openHistory, historyRow, openCorrection, openInvoiceReport, runInvoiceReport,
  fillDate, awaitResponse, billDate, money,
};
