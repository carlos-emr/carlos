#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario private (Bill Patient) bill: the review page's Back to Edit, Save, Save & Add Another,
 * Save & Print Invoice and Settle & Print Invoice buttons.
 *
 * User path: Schedule ▸ owned appointment "B" (bill form) ▸ Billing type "Bill Patient" ▸ typed
 * private code (_OMA_A007) and diagnostic code ▸ Next (ViewBillingONReview) ▸ Back to Edit /
 * Save & Add Another / Save & Print Invoice (BillingONSave ▸ billingONSavePrintInvoice ▸ the
 * ViewBillingON3rdInv popup) / Settle & Print Invoice.
 *
 * Asserts against MariaDB: Back to Edit returns the bill form with the typed code and diagnostic
 * code and writes nothing; the bill-to, remit-to and note typed on the review page land in
 * billing_on_ext and comment1 of the saved claim (status P, paid 0, one transaction); Save & Print
 * opens exactly one invoice popup, for the saved bill, listing the bill-to and the total; Save &
 * Add Another saves a second claim; Settle & Print settles the claim (status S, paid = total, a
 * billing_on_payment row) -- the last step fails while it only saves it unpaid.
 *
 * Fixtures: createBillingFixture (owned billing provider and HIN on the owned patient), one owned
 * appointment today per bill. Cleanup removes the claim rows the browser wrote and every fixture,
 * and asserts they are gone.
 * Implements the gap-billing "private bill save actions" workflow.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture } = require('./billing-on-ohip-simulation-report-playwright-checks');
const g = require('./lib/gap-billing-support');

const CODE = '_OMA_A007';
const SAVE = r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingONSave');

/** Bill form ▸ Bill Patient (navigates to the PRIVATE form) ▸ typed code and dx ▸ Next. */
async function privateReview(s, owned, appointment) {
  const form = await g.openBillForm(s, appointment);
  await g.chooseBillingPhysician(form, owned.providerNo);
  const select = form.locator('select[name="xml_billtype"]');
  const value = await select.locator('option').evaluateAll(options => options.find(o => o.value.startsWith('PAT')).value);
  await Promise.all([form.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }), select.selectOption(value)]);
  await form.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await g.chooseBillingPhysician(form, owned.providerNo);
  await form.locator('input[name="serviceCode0"]').fill(CODE);
  await form.locator('input[name="dxCode"]').fill('250');
  await g.nextToReview(form);
  return form;
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const owned = createBillingFixture(s);
  g.registerOwnedBillCleanup(s);
  const fee = g.scheduleFee(sql, CODE);
  const total = g.money(fee);
  const appointments = ['14:00:00', '14:15:00', '14:30:00', '14:45:00'].map(time => g.seedAppointment(s, { time }));
  await g.showSeededAppointments(s);
  const claim = appointment => sql.rows(`SELECT id, status, paid, total, comment1 FROM billing_on_cheader1
    WHERE demographic_no=${patient} AND appointment_no=${appointment}`);
  const billTo = `${marker} Payer\n1 Fake Street\nHamilton ON`;
  const remitTo = `${marker} Remit\n2 Fake Street\nHamilton ON`;

  await s.step('Back to Edit returns the bill form with the typed code and writes nothing', async () => {
    const form = await privateReview(s, owned, appointments[0]);
    await Promise.all([
      form.waitForResponse(SAVE, { timeout: 30000 }),
      form.locator('form[name="titlesearch"] input[value="Back to Edit"]').click(),
    ]);
    await form.locator('input[name="serviceCode0"]').waitFor({ state: 'visible', timeout: 30000 });
    h.assert(await form.locator('input[name="serviceCode0"]').inputValue() === CODE
      && await form.locator('input[name="dxCode"]').inputValue() === '250', 'Back to Edit lost the typed code or diagnostic code');
    h.assert(claim(appointments[0]).length === 0, 'Back to Edit saved a claim');
    await form.close();
  });

  let printedId;
  await s.step('Save & Print Invoice saves the unpaid claim with its bill-to and opens the invoice for it', async () => {
    const form = await privateReview(s, owned, appointments[1]);
    await form.locator('#billTo').fill(billTo);
    await form.locator('#remitTo').fill(remitTo);
    await form.locator('textarea[name="comment"]').fill(`${marker} private note`);
    // Count every page the context opens during the save: a duplicated popup must not go unnoticed.
    const opened = [];
    const onPage = page => opened.push(page);
    s.context.on('page', onPage);
    const invoice = s.context.waitForEvent('page', { timeout: 30000 });
    const [response] = await Promise.all([
      form.waitForResponse(SAVE, { timeout: 30000 }),
      form.locator('input[value="Save & Print Invoice"]').click(),
    ]);
    h.assert(response.status() === 200, `Save & Print answered HTTP ${response.status()}`);
    const popup = await invoice;
    await popup.waitForURL(/ViewBillingON3rdInv/, { timeout: 20000 });
    await popup.waitForLoadState('domcontentloaded');
    // Give a second (duplicate) invoice popup time to appear before counting.
    await s.schedule.waitForTimeout(1500);
    s.context.off('page', onPage);
    h.assert(opened.length === 1, `Save & Print opened ${opened.length} pages instead of exactly one invoice popup`);
    const rows = claim(appointments[1]);
    h.assert(rows.length === 1, 'Save & Print did not save exactly one claim');
    const [id, status, paid, rowTotal, comment] = rows[0];
    printedId = id;
    h.assert(status === 'P' && Number(paid) === 0 && Number(rowTotal) === Number(total) && comment.includes(`${marker} private note`),
      'Save & Print did not save an unpaid private claim with its note');
    const ext = sql.rows(`SELECT key_val, value FROM billing_on_ext WHERE billing_no=${id}`);
    h.assert(ext.some(([key, value]) => key === 'billTo' && value.includes(`${marker} Payer`)), 'The bill-to was not saved');
    h.assert(ext.some(([key, value]) => key === 'remitTo' && value.includes(`${marker} Remit`)), 'The remit-to was not saved');
    h.assert(popup.url().includes(`billingNo=${id}`), `The invoice popup is not for the saved bill (${new URL(popup.url()).search})`);
    const text = (await popup.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(`${marker} Payer`) && text.includes(total), 'The invoice does not show the bill-to and the total');
    // The invoice's Remit To block shows the clinic address instead when useDemoClinicInfoOnInvoice is on, so the
    // typed remit-to is asserted in billing_on_ext above, not on the invoice.
    await popup.close();
  });

  await s.step('Save & Add Another saves a second claim', async () => {
    const form = await privateReview(s, owned, appointments[2]);
    const [response] = await Promise.all([
      form.waitForResponse(SAVE, { timeout: 30000 }),
      form.locator('input[value="Save & Add Another Bill"]').click(),
    ]);
    h.assert(response.status() === 200, `Save & Add Another answered HTTP ${response.status()}`);
    const rows = claim(appointments[2]);
    h.assert(rows.length === 1 && rows[0][1] === 'P' && Number(rows[0][3]) === Number(total),
      'Save & Add Another did not save one unpaid private claim');
    h.assert(printedId && claim(appointments[1]).length === 1, 'The first claim disappeared');
    // "Add another" keeps the popup open on a fresh bill form for the same patient and appointment.
    await form.locator('select[name="xml_billtype"]').waitFor({ state: 'visible', timeout: 30000 });
    await form.close();
  });

  await s.step('Settle & Print Invoice settles the claim: status S, paid equal to the total, one payment row', async () => {
    const form = await privateReview(s, owned, appointments[3]);
    const invoice = s.context.waitForEvent('page', { timeout: 30000 }).catch(() => null);
    await Promise.all([
      form.waitForResponse(SAVE, { timeout: 10000 }).catch(() => null),
      form.locator('#settlePrintBtn').click(),
    ]);
    const popup = await invoice;
    if (popup) await popup.close().catch(() => {});
    const rows = claim(appointments[3]);
    h.assert(rows.length === 1, 'Settle & Print Invoice saved no claim (its button script throws before the form is submitted)');
    const [id, status, paid] = rows[0];
    h.assert(status === 'S' && Number(paid) === Number(total)
      && sql.value(`SELECT COUNT(*) FROM billing_on_payment WHERE billing_no=${id}`) === '1',
    'Settle & Print Invoice did not settle the claim (it should be status S, paid = total, with one payment row)');
  });
}

if (require.main === module) runWorkflow('gap-billing-private-bill-save-actions', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
