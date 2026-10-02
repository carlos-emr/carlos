#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Ontario bill types: Do Not Bill, Interim Federal Health, CPP, STD/LTD, OCF, ODS and Bill Patient (private code).
 *
 * User path: Schedule ▸ owned appointment "B" (bill form) ▸ Billing type ▸ a typed service code and
 * diagnostic code ▸ Next (ViewBillingONReview) ▸ Save (BillingONSave), once per bill type.
 * billing-on-submit covers OHIP, WSIB and Bonus Codes; this check covers the remaining bill types
 * the form offers.
 *
 * Asserts against MariaDB, per bill type: the claim's pay_program, status (N for Do Not Bill, P for
 * Bill Patient, O for the others), payee and total; the item's code and fee; for the third-party
 * programs (IFH, CPP, STD, OCF, ODS, PAT) a billing_on_ext bill-to row set, and for the
 * others (Do Not Bill) a billing_on_transaction row; and that the appointment turns "billed".
 *
 * Fixtures: createBillingFixture (owned billing provider and HIN on the owned patient), one owned
 * appointment today per bill type. Cleanup removes the claim rows the browser wrote and every
 * fixture, and asserts they are gone.
 * Implements the gap-billing "bill types" workflow.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture } = require('./billing-on-ohip-simulation-report-playwright-checks');
const g = require('./lib/gap-billing-support');

// [bill type prefix, expected pay_program, expected status, third-party ext rows expected]
const TYPES = [
  ['NOT', 'NOT', 'N', false],
  ['IFH', 'IFH', 'O', true],
  ['CPP', 'CPP', 'O', true],
  ['STD', 'STD', 'O', true],
  ['OCF', 'OCF', 'O', true],
  ['ODS', 'ODS', 'O', true],
  // Bill Patient: only a private (underscore) code is billed, so it carries the OMA uninsured fee.
  ['PAT', 'PAT', 'P', true, '_OMA_A007'],
];

async function selectBillType(form, prefix) {
  const select = form.locator('select[name="xml_billtype"]');
  const value = await select.locator('option').evaluateAll((options, wanted) => {
    const match = options.find(option => option.value.startsWith(wanted));
    return match ? match.value : null;
  }, prefix);
  h.assert(value, `The bill form does not offer bill type ${prefix}`);
  const navigates = ['PAT', 'OCF', 'ODS', 'CPP', 'STD', 'BON'].includes(prefix);
  if (navigates) {
    await Promise.all([form.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }), select.selectOption(value)]);
    await form.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(form, `bill form after switching to ${prefix}`);
  } else {
    await select.selectOption(value);
  }
  h.assert((await form.locator('select[name="xml_billtype"]').inputValue()).startsWith(prefix),
    `Bill type ${prefix} was not selected`);
}

async function workflow(s) {
  const { sql, patient } = s;
  const owned = createBillingFixture(s);
  g.registerOwnedBillCleanup(s);
  const fee = g.scheduleFee(sql, 'A007A');
  const privateFee = g.scheduleFee(sql, '_OMA_A007');
  let minute = 0;
  const paymentIds = sql.rows('SELECT id FROM billing_payment_type').map(row => row[0]);

  const appointments = TYPES.map(() => {
    const clock = 15 * 60 + minute;
    const appointment = g.seedAppointment(s, {
      time: `${String(Math.floor(clock / 60)).padStart(2, '0')}:${String(clock % 60).padStart(2, '0')}:00`,
    });
    minute += 15;
    return appointment;
  });
  await g.showSeededAppointments(s);

  for (const [index, [prefix, payProgram, status, thirdParty, privateCode]] of TYPES.entries()) {
    const appointment = appointments[index];
    await s.step(`bill type ${prefix} saves a claim with pay program ${payProgram} and status ${status}`, async () => {
      const form = await g.openBillForm(s, appointment);
      await g.chooseBillingPhysician(form, owned.providerNo);
      await selectBillType(form, prefix);
      await g.chooseBillingPhysician(form, owned.providerNo);
      const code = privateCode || 'A007A';
      const expectedFee = privateCode ? privateFee : fee;
      await form.locator('input[name="serviceCode0"]').fill(code);
      await form.locator('input[name="dxCode"]').fill('250');
      await g.nextToReview(form);
      // A third-party bill carries the bill-to typed on the review page: type a known one to read it back.
      const billTo = `${s.marker} ${prefix} Payer`;
      if (thirdParty) {
        h.assert(await form.locator('#billTo').count() === 1, `The review page of bill type ${prefix} offers no bill-to`);
        await form.locator('#billTo').fill(billTo);
      }
      await Promise.all([
        form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/ON/BillingONSave'),
          { timeout: 30000 }),
        form.locator('form[name="titlesearch"] input[type="submit"][value="Save"]').click(),
      ]);
      const rows = sql.rows(`SELECT id, pay_program, status, payee, total FROM billing_on_cheader1
        WHERE demographic_no=${patient} AND appointment_no=${appointment}`);
      h.assert(rows.length === 1, `Bill type ${prefix} did not save exactly one claim`);
      const [id, program, headerStatus, payee, total] = rows[0];
      // Third-party bills keep the chosen payment-method id in the one-character payee column.
      const payeeOk = thirdParty ? paymentIds.includes(payee) : payee === 'P';
      h.assert(program === payProgram && headerStatus === status && payeeOk,
        `Bill type ${prefix} saved pay program ${program}, status ${headerStatus} and payee ${payee}`);
      const items = g.itemsOf(sql, id);
      h.assert(items.length === 1 && items[0].service_code === code && Number(items[0].fee) === Number(expectedFee)
        && Number(total) === Number(expectedFee) && items[0].dx === '250', `Bill type ${prefix} saved the wrong item or total`);
      const trans = sql.value(`SELECT COUNT(*) FROM billing_on_transaction WHERE ch1_id=${id}`);
      if (thirdParty) {
        const ext = sql.rows(`SELECT key_val, value FROM billing_on_ext WHERE billing_no=${id}`);
        h.assert(ext.some(([key, value]) => key === 'billTo' && value === billTo),
          `Bill type ${prefix} did not write its billTo row with the typed bill-to`);
      } else {
        h.assert(Number(trans) > 0, `Bill type ${prefix} did not write its transaction row`);
      }
      h.assert(/B/.test(sql.value(`SELECT status FROM appointment WHERE appointment_no=${appointment}`)),
        `Bill type ${prefix} did not mark the appointment billed`);
    });
  }
}

if (require.main === module) runWorkflow('gap-billing-bill-types', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
