#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC day sheet: bill an appointment with B, then unbill it with -B. No check unbilled a BC
 * appointment: billing-on-correction-delete and csrf-runtime-form-day-sheet-unbill drive the
 * Ontario routes only, and the BC bill checks open the bill form but never undo a bill.
 * User path: Schedule (today's day sheet) ▸ B on the owned appointment (the BC bill form, in its own
 * window) ▸ billing physician, fee code, diagnosis ▸ Continue ▸ Save Bill (the window closes and the
 * day sheet refreshes) ▸ -B on that appointment ▸ OK on "unbill?" (billing/CA/BC/BillingDeleteWithoutNo,
 * posted into the "unbilled" window, which closes itself and refreshes the day sheet).
 * Asserted: B opens the bill form for the owned appointment; Save Bill writes one MSP claim for
 * that appointment (billing and billingmaster, status O) and the appointment becomes billed (B);
 * -B marks the bill and its line deleted (D), puts the appointment back to P and archives the
 * appointment row it changed; -B on a billed appointment whose claim was already submitted (status
 * B) says it cannot delete billed items and changes nothing; a GET carrying the unbill is refused and
 * changes nothing.
 * Known failure, recorded in the manifest notes: the last step fails on 2026.08 because
 * BillingDeleteWithoutNo has no method guard (#4439, D12).
 * Fixtures: one billing physician (bc.createBillingPhysician), the workflow's own FAKE patient made
 * a BC patient, and three appointments for it today on the test login's day sheet: one to bill,
 * one billed with a submitted claim and one billed with an open claim (both claims seeded). Cleanup
 * deletes every BC billing row of the patient, then its appointments and their archive rows, then
 * the physician, and asserts each is gone. BC installs only (billregion=BC).
 */
const h = require('./lib/playwright-harness');
const bc = require('./lib/bc-billing-fixture');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const UNBILL_ROUTE = '/billing/CA/BC/BillingDeleteWithoutNo';
const CODE = '00100';
const DX = '780';

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: "the day sheet's B on the owned appointment opens the BC bill form for it",
  save: 'Save Bill writes the appointment\'s MSP claim and the appointment becomes billed (B)',
  unbill: '-B unbills it: the bill and its line become D and the appointment goes back to P',
  submitted: '-B on an appointment whose claim was submitted says it cannot delete billed items and changes nothing',
  get: 'a GET carrying the unbill is refused and changes nothing',
};

async function settle(page, label) {
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(page, label);
}

/** The owned patient's appointments today and their archive rows; removed after the bills, before the patient. */
function ownAppointments(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no=${patient};
      DELETE FROM appointment WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient})`) === '0', 'Owned appointments were not removed');
  });
  const day = sql.value('SELECT CURDATE()');
  const make = (start, end, status) => h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date, start_time,
      end_time, name, demographic_no, notes, reason, status, createdatetime, updatedatetime, creator, lastupdateuser)
    VALUES (${q(provider)}, ${q(day)}, '${start}', '${end}', ${q(`${marker},Workflow`)}, ${patient}, ${q(marker)}, ${q(marker)},
      ${q(status)}, NOW(), NOW(), ${q(provider)}, ${q(provider)})`, 'appointment');
  return { day, toBill: make('09:00:00', '09:14:00', 't'), submitted: make('09:15:00', '09:29:00', 'B'), open: make('09:30:00', '09:44:00', 'B') };
}

/** A seeded claim on a billed appointment: its bill row carries the appointment and the given status. */
function seedClaimOn(s, physician, appointmentNo, status) {
  const { sql, patient } = s;
  const claim = bc.seedOwnedBcBill({ ...s, provider: physician.providerNo }, { status, code: CODE, dx: DX });
  sql.execute(`UPDATE billing SET appointment_no=${appointmentNo}, status=${h.sqlString(status)}
    WHERE billing_no=${claim.billingNo} AND demographic_no=${patient}`);
  return claim;
}

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
}

async function workflow(s) {
  const { sql, patient, context } = s;
  const physician = bc.createBillingPhysician(s);
  bc.makeBcPatient(s);
  // Registered after the appointments' cleanup, so the bills go first.
  const appointments = ownAppointments(s);
  bc.registerOwnedBcBillCleanup(s);
  seedClaimOn(s, physician, appointments.submitted, 'B');
  seedClaimOn(s, physician, appointments.open, 'O');
  const state = (appointmentNo) => sql.value(`SELECT CONCAT_WS('|',
      (SELECT status FROM appointment WHERE appointment_no=${appointmentNo}),
      (SELECT GROUP_CONCAT(status ORDER BY billing_no) FROM billing WHERE appointment_no=${appointmentNo} AND demographic_no=${patient}),
      (SELECT GROUP_CONCAT(bm.billingstatus ORDER BY bm.billingmaster_no) FROM billingmaster bm JOIN billing b ON b.billing_no=bm.billing_no
        WHERE b.appointment_no=${appointmentNo} AND b.demographic_no=${patient}))`);
  h.assert(state(appointments.submitted) === 'B|B|B' && state(appointments.open) === 'B|O|O',
    `The seeded billed appointments are not as expected: ${state(appointments.submitted)}, ${state(appointments.open)}`);

  const [year, month, day] = appointments.day.split('-');
  const daySheetPath = `/provider/providercontrol?${new URLSearchParams({ year, month: String(Number(month)),
    day: String(Number(day)), view: '0', displaymode: 'day', dboperation: 'searchappointmentday', viewall: '1' })}`;
  const sheet = s.schedule;
  const openDaySheet = async () => {
    await h.gotoApp(sheet, s.config.baseUrl, daySheetPath);
    await settle(sheet, 'day sheet');
  };
  const unbillLink = (appointmentNo) => sheet.locator(`a[onclick*="${UNBILL_ROUTE}?"][onclick*="appointment_no=${appointmentNo}"]`);
  /** The day sheet's own refresh, which a save or a successful unbill sends it; a timeout means none came. */
  const sheetRefreshed = () => sheet.waitForEvent('load', { timeout: TIMEOUT }).catch(() => null);
  /** -B on one appointment: OK on the confirm; returns the "unbilled" window it posts into. */
  const unbill = async (appointmentNo) => {
    const link = unbillLink(appointmentNo);
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    const posted = context.waitForEvent('request', { predicate: r => r.method() === 'POST'
      && new URL(r.url()).pathname.endsWith(UNBILL_ROUTE), timeout: TIMEOUT });
    const popup = context.waitForEvent('page', { timeout: TIMEOUT });
    const dialogs = await h.withExpectedDialogs(sheet, () => link.click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', '-B did not ask to confirm the unbill');
    const request = await posted;
    const response = await request.response();
    h.assert(response && response.status() === 200, `The unbill answered HTTP ${response ? response.status() : 'nothing'}`);
    return await popup;
  };

  let form;
  await s.step(STEP.open, async () => {
    await openDaySheet();
    const link = sheet.locator(`a[onclick*="/billing?billRegion="][onclick*="appointment_no=${appointments.toBill}&"]`).first();
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    [form] = await Promise.all([context.waitForEvent('page', { timeout: TIMEOUT }), link.click()]);
    await settle(form, 'the bill form');
    h.assert(await form.locator('#bcBillingForm').count() === 1, 'B did not open the BC bill form');
  });

  await s.step(STEP.save, async () => {
    await bc.changeBillFormSelect(form, '#xml_provider', physician.providerNo, 2, 'choosing the billing physician');
    await form.locator('input[name="xml_other1"]').fill(CODE);
    await form.locator('input[name="xml_other1_unit"]').fill('1');
    await form.locator('input[name="xml_diagnostic_detail1"]').fill(DX);
    await form.locator('#ignoreWarn').setChecked(true);
    const [continued] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/BC/CreateBilling'),
        { timeout: TIMEOUT }),
      form.locator('#buttonRow input[type="submit"][value="Continue"]').click(),
    ]);
    h.assert(continued.status() === 200, `Continue answered HTTP ${continued.status()}`);
    await settle(form, 'the bill review');
    h.assert(await form.locator('form[action$="/billing/CA/BC/SaveBilling"]').count() === 1, 'Continue did not reach the bill review');
    const closed = form.waitForEvent('close', { timeout: TIMEOUT });
    const refreshed = sheetRefreshed();
    const [saved] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/billing/CA/BC/SaveBilling'),
        { timeout: TIMEOUT }),
      form.locator('input[type="submit"][name="submit"][value="Save Bill"]').click(),
    ]);
    h.assert(saved.status() < 400, `Save Bill answered HTTP ${saved.status()}`);
    await closed;
    await refreshed;
    h.assert(state(appointments.toBill) === 'B|O|O', `After Save Bill the appointment, bill and line are ${state(appointments.toBill)}, not B|O|O`);
    h.assert(sql.value(`SELECT CONCAT_WS('|',bm.billing_code,bm.dx_code1,b.billingtype) FROM billingmaster bm JOIN billing b
        ON b.billing_no=bm.billing_no WHERE b.appointment_no=${appointments.toBill} AND b.demographic_no=${patient}`)
      === `${CODE}|${DX}|MSP`, 'The saved claim does not carry the fee code, diagnosis and MSP');
  });

  await s.step(STEP.unbill, async () => {
    await openDaySheet();
    const archived = () => sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${appointments.toBill}`);
    const before = archived();
    const refreshed = sheetRefreshed();
    const result = await unbill(appointments.toBill);
    // The result page closes itself and refreshes the day sheet.
    if (!result.isClosed()) await result.waitForEvent('close', { timeout: TIMEOUT });
    await refreshed;
    h.assert(state(appointments.toBill) === 'P|D|D', `After -B the appointment, bill and line are ${state(appointments.toBill)}, not P|D|D`);
    h.assert(Number(archived()) === Number(before) + 1, 'The unbill did not archive the appointment row it changed');
  });

  await s.step(STEP.submitted, async () => {
    await openDaySheet();
    const result = await unbill(appointments.submitted);
    await settle(result, 'the unbill refusal');
    h.assert(/Sorry, cannot delete billed items\./.test(await result.locator('body').innerText()),
      'The unbill of a submitted claim did not say it cannot delete billed items');
    await result.close();
    h.assert(state(appointments.submitted) === 'B|B|B', `The refused unbill changed the submitted claim: ${state(appointments.submitted)}`);
  });

  await s.step(STEP.get, async () => {
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `${UNBILL_ROUTE}?status=B&appointment_no=${appointments.open}`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET unbill answered ${response.status()}, not 405`);
    h.assert(state(appointments.open) === 'B|O|O', `A refused GET unbill changed the open claim: ${state(appointments.open)}`);
  });
}

if (require.main === module) runWorkflow('billing-bc-day-sheet-unbill', workflow, { openMaster: false, preflight });
module.exports = { workflow, preflight, STEP };
