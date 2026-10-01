#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Day sheet "-B" (Unbill): a POST sent through a form the page builds at click time.
 *
 * User path: Schedule (day sheet) ▸ owned billed appointment ▸ "-B" ▸ confirm ▸ the
 * `unbilled` popup. onUnbilled() in provider/schedulePage.js.jsp opens the popup and calls
 * postViaForm(), which creates a <form> with document.createElement and submits it at once.
 * CSRFGuard's client script never tokenises such a form (its MutationObserver runs after the
 * synchronous submit), so this path works only because postViaForm copies the page's
 * CSRF-TOKEN input into the new form. Pattern sweep `csrf-runtime-form` (issue #4130).
 *
 * Asserts: the day sheet offers "-B" for the owned appointment in status B; the click sends a
 * POST to billing/CA/ON/BillingDeleteWithoutNo carrying a non-empty CSRF-TOKEN; it is answered
 * 200 (not 403); billing_on_cheader1 and billing_on_item move to status D and the appointment
 * to P (unbilled), with an appointmentArchive snapshot of the billed appointment.
 * Fixtures: the session's owned FAKE- patient, one owned appointment today for the test login
 * (status B, name = marker) and one owned open bill (seedOwnedBill from
 * billing-on-invoice-third-party, comment1 = marker) linked to it by appointment_no. Cleanup
 * deletes the bill rows (shared helper), the appointment and its archive rows, and asserts
 * they are gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { seedOwnedBill, billDate } = require('./billing-on-invoice-third-party-playwright-checks');

const ROUTE = '/billing/CA/ON/BillingDeleteWithoutNo';

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const code = 'A007A';
  const date = billDate();
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)}
    AND billingservice_date<=${h.sqlString(date)} ORDER BY billingservice_date DESC LIMIT 1`);
  if (!/^\d+(\.\d+)?$/.test(fee) || Number(fee) <= 0) throw new h.SkipCheck(`service code ${code} has no positive fee`);

  const name = `${marker},Workflow`;
  let appointment = null;
  s.cleanup(() => {
    if (!appointment) return;
    sql.execute(`DELETE FROM appointmentArchive WHERE appointment_no=${appointment} AND demographic_no=${patient};
      DELETE FROM appointment WHERE appointment_no=${appointment} AND demographic_no=${patient} AND name=${h.sqlString(name)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointment})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${appointment})`) === '0',
    'The owned appointment or its archive rows were not removed');
  });
  appointment = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
      demographic_no, program_id, notes, reason, location, resources, type, style, billing, status, createdatetime,
      updatedatetime, creator, remarks, urgency)
    VALUES (${h.sqlString(provider)}, CURDATE(), '13:45:00', '13:59:00', ${h.sqlString(name)}, ${patient},
      0, '', 'Unbill workflow', '', '', '', '', '', 'B', NOW(), NOW(), ${h.sqlString(provider)}, '', '');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(appointment), 'The appointment fixture was not created');
  const bill = seedOwnedBill(s, { payProgram: 'HCP', status: 'O', code, fee, date });
  sql.execute(`UPDATE billing_on_cheader1 SET appointment_no=${appointment} WHERE id=${bill.headerId} AND demographic_no=${patient}`);
  h.assert(sql.value(`SELECT appointment_no FROM billing_on_cheader1 WHERE id=${bill.headerId}`) === appointment,
    'The owned bill was not linked to the owned appointment');

  const unbill = () => s.schedule.locator(`a[onclick*="${ROUTE}"][onclick*="appointment_no=${appointment}"]`);

  await s.step('the day sheet offers "-B" (Unbill) for the owned billed appointment', async () => {
    await s.schedule.reload({ waitUntil: 'domcontentloaded' }); // the appointment was seeded after login
    await s.schedule.waitForLoadState('networkidle').catch(() => {});
    h.assert(await unbill().count() === 1, 'The day sheet does not offer "-B" for the owned appointment');
    h.assert(sql.value(`SELECT CONCAT_WS('|', h.status, GROUP_CONCAT(i.status)) FROM billing_on_cheader1 h
      JOIN billing_on_item i ON i.ch1_id=h.id WHERE h.id=${bill.headerId} GROUP BY h.id`) === 'O|O',
    'The owned bill is not open before the unbill');
  });

  await s.step('"-B" posts a runtime-built form with a CSRF token; the bill is deleted and the appointment unbilled', async () => {
    const request = s.context.waitForEvent('request', { predicate: r => r.method() === 'POST'
      && new URL(r.url()).pathname.endsWith(ROUTE), timeout: 20000 });
    const response = s.context.waitForEvent('response', { predicate: r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith(ROUTE), timeout: 20000 });
    request.catch(() => {});
    response.catch(() => {});
    const dialogs = await h.withExpectedDialogs(s.schedule, async () => {
      await unbill().first().click();
      await response;
    });
    h.assert(dialogs.length === 1, 'Unbill did not ask for confirmation exactly once');
    const sent = new URLSearchParams((await request).postData() || '');
    h.assert(sent.get('appointment_no') === appointment, 'The unbill POST named another appointment');
    h.assert((sent.get('CSRF-TOKEN') || '').length > 0, 'The runtime-built unbill form was posted without a CSRF-TOKEN');
    const status = (await response).status();
    h.assert(status !== 403, 'The unbill POST was refused 403 (CSRF token missing from the runtime-built form)');
    h.assert(status === 200, `The unbill POST answered HTTP ${status}`);
    await expectValue(sql, `SELECT CONCAT_WS('|', h.status, GROUP_CONCAT(i.status)) FROM billing_on_cheader1 h
      JOIN billing_on_item i ON i.ch1_id=h.id WHERE h.id=${bill.headerId} GROUP BY h.id`, 'D|D',
    'Unbill did not mark the owned bill and its item deleted');
    await expectValue(sql, `SELECT status FROM appointment WHERE appointment_no=${appointment}`, 'P',
      'Unbill did not move the appointment to status P');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${appointment} AND status='B'`) === '1',
      'Unbill did not archive the billed appointment');
    // The popup closes itself; drop it if it is still around so the run ends cleanly.
    for (const page of s.context.pages()) {
      if (page !== s.schedule && /BillingDeleteWithoutNo/.test(page.url())) await page.close().catch(() => {});
    }
  });
}

if (require.main === module) runWorkflow('csrf-runtime-form-day-sheet-unbill', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
