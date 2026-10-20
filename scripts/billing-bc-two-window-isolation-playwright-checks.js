#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Two patients' BC bills in two windows of one login keep their own patient (issue #4673).
 *
 * The BC bill being built lived in one login-wide session attribute, so a bill form opened in a
 * second window replaced the first window's bill: Continue and Save in the first window then billed
 * the second window's patient and appointment with the first window's codes. The receipt shown
 * after "Update Invoice" was likewise the invoice viewed last in any window. Each bill is now staged
 * under its own key (BcBillStaging) and the receipt is shown again by its own bill numbers.
 *
 * User paths (the first and third in two windows of one login):
 *
 *   1. Schedule ▸ B on A's appointment, then B on B's appointment (from a second schedule tab, as
 *      the day sheet opens every bill in one named window) ▸ billing physician, a service code
 *      and a diagnosis in each ▸ Continue in each ▸ Save Bill in A's window, then in B's: each
 *      review names its own patient, and each claim is saved for its own patient and appointment
 *      with its own codes.
 *   2. Schedule ▸ B on A's second appointment ▸ change the billing physician back and forth (each
 *      change reloads the form's patient bar and code table in place; the reloads used to stage a
 *      bill each, pushing the window's own bill out of the session) ▸ Continue with a service code
 *      that does not exist ▸ Continue ▸ Go Back ▸ Continue ▸ Another Bill ▸ Continue ▸ Save Bill: the
 *      refused Continue, Go Back and Another Bill each return to this window's own bill form, and
 *      both claims are saved for A on A's appointment.
 *   3. A private bill of two service lines for A ▸ Save & Print Receipt ▸ a private bill for B in
 *      the other window ▸ Save & Print Receipt ▸ Update Invoice on A's receipt: the receipt shown
 *      again is A's, with both lines.
 *   4. Schedule ▸ B on A's fifth appointment ▸ Bill WCB (the list of A's WCB forms) ▸ A's WCB form ▸
 *      B's bill form opened in the other window ▸ Continue in A's window with a service code that
 *      needs a complete WCB report: refused, back to A's own bill form ▸ Bill WCB, A's form, a code
 *      that needs no report ▸ Continue ▸ Save Bill: A's claim is saved on A's appointment with A's
 *      WCB form. (Before this the list answered 404, a submit reloaded it and cleared the chosen
 *      form, and the WCB check on Continue answered 500; see the bill form and create action.)
 *      First, a Continue for A's bill that names B's WCB form (a crafted post) is refused: the
 *      create action billed whichever form was named.
 *   5. Schedule ▸ B on A's fourth appointment ▸ another billing form ▸ Continue with a service code
 *      that does not exist: the bill form comes back with the chosen form's code table. Then eight
 *      more bills opened in the same login push this bill out of the session: the window's next
 *      Continue is told that its billing session expired, rather than a bare error.
 *
 * Fixtures: the workflow's FAKE patient (A, first name PatientAlpha) and a second FAKE patient (B,
 * PatientBravo, the same marker last name), both with a synthetic BC PHN; five appointments today
 * for A and three for B; one WCB form for each (the patient's details and the injury, as the WCB
 * form page saves them); an owned billing physician created through Administration ▸ Add Provider
 * (so the application's provider caches see it) and deactivated through Update Provider at the
 * end. Cleanup removes every claim, note, history and recipient row of the two patients, their
 * WCB forms, their appointments, B and the physician, and asserts them gone. BC installs only
 * (billregion=BC).
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { withProviderCleanup } = require('./billing-bc-simulation-encoding-playwright-checks');

const TIMEOUT = 30000;
const A_FIRST_NAME = 'PatientAlpha';
const B_FIRST_NAME = 'PatientBravo';
const q = h.sqlString;

function key(value, what) {
  h.assert(/^[1-9]\d*$/.test(String(value)), `The owned ${what} key is invalid`);
  return String(value);
}

function unusedValue(sql, table, column, prefix, digits = 5) {
  for (let i = 0; i < 20; i++) {
    const value = `${prefix}${randomInt(10 ** (digits - 1), 10 ** digits)}`;
    if (sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${column}=${q(value)}`) === '0') return value;
  }
  throw new Error(`Could not find an unused ${table}.${column}`);
}

/** A synthetic BC PHN for an owned patient: 9 and the zero-padded patient number, never a real one. */
function givePhn(sql, marker, patient) {
  sql.execute(`UPDATE demographic SET hc_type='BC', province='BC', hin=${q(`9${String(patient).padStart(9, '0').slice(-9)}`)},
      ver='' WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
}

/** The second patient, owned by this run; removed last, after every child row. */
function createSecondPatient(s) {
  const { sql, marker, provider } = s;
  const patient = h.insertId(sql, `INSERT INTO demographic (last_name, first_name, year_of_birth,
      month_of_birth, date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status,
      lastUpdateDate)
    VALUES (${q(marker)}, ${q(B_FIRST_NAME)}, '1975', '03', '04', 'M', 'AC', ${q(provider)}, 'BC', 'BC', 'NR',
      NOW())`, 'second patient');
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${q(marker)}`) === '1', 'The second patient fixture changed owner');
    sql.execute(['demographicExt', 'demographicArchive', 'casemgmt_note_lock']
      .map(table => `DELETE FROM ${table} WHERE demographic_no=${patient}`).join(';'));
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) === '0',
      'The second patient was not removed');
  });
  return patient;
}

/** Every claim the two windows save, with the rows a save, a receipt update and the archive write. */
function ownBills(s, patients) {
  const { sql } = s;
  const list = patients.join(',');
  s.cleanup(() => {
    const masters = sql.rows(`SELECT billingmaster_no FROM billingmaster WHERE demographic_no IN (${list})`)
      .map(([id]) => key(id, 'billing line'));
    const bills = sql.rows(`SELECT billing_no FROM billing WHERE demographic_no IN (${list})`)
      .map(([id]) => key(id, 'bill'));
    if (masters.length) {
      const ids = masters.join(',');
      sql.execute(`DELETE FROM billing_history WHERE billingmaster_no IN (${ids});
        DELETE FROM billingnote WHERE billingmaster_no IN (${ids});
        DELETE FROM billingmaster WHERE billingmaster_no IN (${ids}) AND demographic_no IN (${list})`);
    }
    if (bills.length) {
      sql.execute(`DELETE FROM bill_recipients WHERE billingNo IN (${bills.join(',')});
        DELETE FROM billing WHERE billing_no IN (${bills.join(',')}) AND demographic_no IN (${list})`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM billingmaster WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM billing_history WHERE billingmaster_no IN (${masters.join(',') || 0}))
      + (SELECT COUNT(*) FROM billingnote WHERE billingmaster_no IN (${masters.join(',') || 0}))
      + (SELECT COUNT(*) FROM bill_recipients WHERE billingNo IN (${bills.join(',') || 0}))`) === '0',
    'Owned BC claims, notes, history or recipients were not removed');
  });
}

/** Every appointment of the two patients; an orphan appointment breaks the whole day sheet. */
function ownAppointments(s, patients) {
  const { sql } = s;
  const list = patients.join(',');
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no IN (${list});
      DELETE FROM appointment WHERE demographic_no IN (${list})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no IN (${list}))`) === '0',
    'Owned appointments were not removed');
  });
}

/** A service code WorkSafeBC pays only with a complete WCB report (WCBCodes' default list). */
const WCB_FORM_CODE = '19937';

/**
 * One WCB form for each patient, as the WCB form page would have saved it (that page cannot be
 * opened, #4864): the patient's details and the injury, enough to bill a code that needs no report
 * but not one that does. Removed by key; the claims that name a form keep only its id, so the order
 * of the two cleanups does not matter.
 */
function ownWcbForms(s, patients, day) {
  const { sql, marker, provider } = s;
  const forms = {};
  for (const [label, patient] of Object.entries(patients)) {
    const first = label === 'A' ? A_FIRST_NAME : B_FIRST_NAME;
    const phn = sql.value(`SELECT hin FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
    forms[label] = key(h.insertId(sql, `INSERT INTO wcb (demographic_no, provider_no, formCreated, formEdited,
        w_reporttype, w_fname, w_lname, w_gender, w_dob, w_city, w_phn, w_servicedate, w_doi, w_noi, w_icd9,
        w_diagnosis, w_duration, status, formNeeded)
      VALUES (${patient}, ${q(provider)}, NOW(), NOW(), 'F', ${q(first)}, 'Fake', 'F', '1980-01-02', 'Testville',
        ${q(phn)}, ${q(day)}, ${q(day)}, '03500', '780', ${q(marker)}, 1, 'W', 0)`, `${label}'s WCB form`),
    `${label}'s WCB form`);
  }
  const ids = Object.values(forms).join(',');
  const list = Object.values(patients).join(',');
  s.cleanup(() => {
    sql.execute(`DELETE FROM wcb WHERE ID IN (${ids}) AND demographic_no IN (${list})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM wcb WHERE ID IN (${ids})`) === '0', 'The owned WCB forms were not removed');
  });
  return forms;
}

/**
 * The owned billing physician, created and deactivated through Administration, never behind the
 * application's back: the active-provider list is cached, and only a provider save evicts it.
 */
function ownBillingPhysician(s, admin) {
  const { sql, marker } = s;
  // Five characters, like an MSP practitioner number: the claim's payee and practitioner columns hold five.
  const ohipNo = unusedValue(sql, 'provider', 'ohip_no', '8', 4);
  const firstName = `Bill${ohipNo}`;
  const owned = `last_name=${q(marker)} AND first_name=${q(firstName)}`;
  s.cleanup(() => {
    const ownedNo = `provider_no IN (SELECT provider_no FROM provider WHERE ${owned})`;
    const memberships = ['program_provider', 'provider_facility', 'secUserRole', 'providersite'];
    sql.execute(memberships.map(table => `DELETE FROM ${table} WHERE ${ownedNo}`).join(';'));
    h.assert(sql.value(`SELECT ${memberships.map(table => `(SELECT COUNT(*) FROM ${table} WHERE ${ownedNo})`).join(' + ')}`)
      === '0', 'The owned billing physician\'s program, facility, role or site rows were not removed');
    sql.execute(`DELETE FROM provider WHERE ${owned}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE ${owned}`) === '0', 'The owned billing physician was not removed');
  });
  /** Deactivates every saved owned physician, one whose creation failed half way included. */
  async function deactivate() {
    for (const [providerNo] of sql.rows(`SELECT provider_no FROM provider WHERE ${owned} AND status='1'`)) {
      await h.gotoApp(admin, s.config.baseUrl, `/admin/ViewProviderUpdateProvider?keyword=${encodeURIComponent(providerNo)}`);
      const form = admin.locator('form[name="updatearecord"]');
      await form.locator('#statusInactive').check();
      await Promise.all([
        admin.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }),
        form.locator('input[type="submit"][name="subbutton"]').click(),
      ]);
      h.assert(sql.value(`SELECT status FROM provider WHERE provider_no=${q(providerNo)}`) === '0',
        'Update Provider did not deactivate the owned billing physician');
    }
  }
  async function create() {
    await h.gotoApp(admin, s.config.baseUrl, '/admin/ViewProviderAddARecordHtm');
    const form = admin.locator('form[name="searchprovider"]');
    const providerNoInput = form.locator('input[name="provider_no"]').first();
    await providerNoInput.waitFor({ state: 'visible' });
    if ((await providerNoInput.getAttribute('readonly')) === null) {
      await providerNoInput.fill(unusedValue(sql, 'provider', 'provider_no', '9'));
    }
    await form.locator('input[name="last_name"]').fill(marker);
    await form.locator('input[name="first_name"]').fill(firstName);
    await form.locator('select[name="provider_type"]').selectOption('doctor');
    await form.locator('select[name="sex"]').selectOption('F');
    await form.locator('input[name="ohip_no"]').fill(ohipNo);
    await form.locator('input[name="billing_no"]').fill(ohipNo);
    await Promise.all([
      admin.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }),
      form.locator('input[type="submit"]').first().click(),
    ]);
    const providerNo = sql.value(`SELECT provider_no FROM provider WHERE ${owned} AND ohip_no=${q(ohipNo)} AND status='1'`);
    h.assert(providerNo, 'Add Provider did not create the owned billing physician');
    return providerNo;
  }
  return { create, deactivate };
}

async function settle(page, label) {
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(page, label);
}

/** A bill form's in-place reload: the same page fetched again for its patient bar or code table. */
function isFormReload(response) {
  const url = new URL(response.url());
  return response.request().method() === 'GET' && url.pathname.endsWith('/billing') && url.searchParams.has('demographic_no');
}

/**
 * Changes one of the bill form's selects and waits for the in-place reloads that change makes (the
 * physician reloads the patient bar and then the code table; the bill type, the code table).
 */
async function changeAndAwaitReloads(form, select, value, count, what) {
  const reloads = [];
  const onResponse = (response) => { if (isFormReload(response)) reloads.push(response); };
  form.on('response', onResponse);
  try {
    await form.locator(select).selectOption(value);
    const deadline = Date.now() + TIMEOUT;
    while (reloads.length < count && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
    h.assert(reloads.length >= count, `${what} reloaded the bill form ${reloads.length} times, not ${count}`);
    await form.waitForFunction(() => !window.jQuery || window.jQuery.active === 0, null, { timeout: TIMEOUT });
  } finally {
    form.off('response', onResponse);
  }
  for (const response of reloads) {
    h.assert(response.status() === 200, `A bill form reload after ${what} answered HTTP ${response.status()}`);
  }
  await h.assertNotErrorPage(form, `bill form after ${what}`);
}

function chooseBillingPhysician(form, providerNo) {
  return changeAndAwaitReloads(form, '#xml_provider', providerNo, 2, 'choosing the billing physician');
}

/** The billing physician, service lines and diagnosis of a bill form, then Continue. */
async function fillAndContinue(form, { physician, codes, dx, ignoreWarnings }) {
  if (await form.locator('#xml_provider').inputValue() !== physician) await chooseBillingPhysician(form, physician);
  for (const [index, code] of codes.entries()) {
    await form.locator(`input[name="xml_other${index + 1}"]`).fill(code);
    await form.locator(`input[name="xml_other${index + 1}_unit"]`).fill('1');
  }
  await form.locator('input[name="xml_diagnostic_detail1"]').fill(dx);
  await form.locator('#ignoreWarn').setChecked(ignoreWarnings);
  const [posted] = await Promise.all([
    form.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/billing/CA/BC/CreateBilling'), { timeout: TIMEOUT }),
    form.locator('#buttonRow input[type="submit"][value="Continue"]').click(),
  ]);
  return posted;
}

/** Continue, warnings ignored, to the bill review. */
async function continueToReview(form, label, { physician, codes, dx }) {
  const posted = await fillAndContinue(form, { physician, codes, dx, ignoreWarnings: true });
  h.assert(posted.status() === 200, `${label}'s Continue answered HTTP ${posted.status()}`);
  await settle(form, `${label}'s bill review`);
  h.assert(await form.locator('form[action$="/billing/CA/BC/SaveBilling"]').count() === 1,
    `${label}'s Continue did not reach the bill review`);
}

/** A page's text, whitespace folded and lower-cased: the pages show names in their own capitalisation. */
async function textOf(locator) {
  return (await locator.innerText()).replace(/\s+/g, ' ').toLowerCase();
}

function reviewText(page) {
  return textOf(page.locator('form[action$="/billing/CA/BC/SaveBilling"]'));
}

function names(text, own, other) {
  return text.includes(own.toLowerCase()) && !text.includes(other.toLowerCase());
}

/**
 * Save Bill (or Save & Print Receipt) on a bill review. Save Bill's answer closes the bill window and
 * refreshes the day sheet that opened it; Save & Print Receipt turns the window into the receipt.
 */
async function save(page, label, button, daySheet = null) {
  const closed = button === 'Save Bill' ? page.waitForEvent('close', { timeout: TIMEOUT }) : null;
  const refreshed = closed && daySheet ? daySheet.waitForEvent('load', { timeout: TIMEOUT }) : null;
  const [posted] = await Promise.all([
    page.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/billing/CA/BC/SaveBilling'), { timeout: TIMEOUT }),
    page.locator(`input[type="submit"][name="submit"][value="${button}"]`).click(),
  ]);
  h.assert(posted.status() < 400, `${label}'s ${button} answered HTTP ${posted.status()}`);
  if (closed) {
    await closed;
    if (refreshed) {
      await refreshed;
      await settle(daySheet, `the day sheet ${label}'s save refreshed`);
    }
    return;
  }
  await settle(page, `${label} after ${button}`);
}

async function workflow(s) {
  const { sql, marker, provider, context } = s;
  const A = key(s.patient, 'patient');
  sql.execute(`UPDATE demographic SET first_name=${q(A_FIRST_NAME)} WHERE demographic_no=${A} AND last_name=${q(marker)}`);
  // Registered first, so it runs after every child cleanup below.
  const B = key(createSecondPatient(s), 'second patient');
  givePhn(sql, marker, A);
  givePhn(sql, marker, B);
  ownAppointments(s, [A, B]);
  ownBills(s, [A, B]);
  const admin = await context.newPage();
  h.wireStrictPage(admin, 'billing-bc-two-window-admin', s.recorder);
  const owner = ownBillingPhysician(s, admin);
  // The physician is deactivated through Administration whatever happens below, before the SQL
  // cleanup removes it; every failure, the scenario's and the teardown's, is reported.
  await withProviderCleanup(async () => {
    const physician = { providerNo: await owner.create() };
    await billInTwoWindows(s, A, B, physician);
  }, owner.deactivate, () => admin.close());
}

async function billInTwoWindows(s, A, B, physician) {
  const { sql, marker, provider, context } = s;

  const day = sql.value('SELECT CURDATE()');
  const [year, month, dayOfMonth] = day.split('-');
  const appointment = {};
  for (const [label, patient, first, start, end] of [
    ['A1', A, A_FIRST_NAME, '09:00:00', '09:14:00'], ['B1', B, B_FIRST_NAME, '09:15:00', '09:29:00'],
    ['A2', A, A_FIRST_NAME, '09:30:00', '09:44:00'], ['A3', A, A_FIRST_NAME, '09:45:00', '09:59:00'],
    ['B3', B, B_FIRST_NAME, '10:00:00', '10:14:00'], ['A4', A, A_FIRST_NAME, '10:15:00', '10:29:00'],
    ['A5', A, A_FIRST_NAME, '10:30:00', '10:44:00'], ['B5', B, B_FIRST_NAME, '10:45:00', '10:59:00']]) {
    appointment[label] = h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date, start_time,
        end_time, name, demographic_no, notes, reason, status, createdatetime, updatedatetime, creator,
        lastupdateuser)
      VALUES (${q(provider)}, ${q(day)}, '${start}', '${end}', ${q(`${marker},${first}`)}, ${patient},
        ${q(marker)}, ${q(marker)}, 't', NOW(), NOW(), ${q(provider)}, ${q(provider)})`, 'appointment');
  }
  const daySheetPath = `/provider/providercontrol?${new URLSearchParams({ year, month: String(Number(month)),
    day: String(Number(dayOfMonth)), view: '0', displaymode: 'day', dboperation: 'searchappointmentday', viewall: '1' })}`;

  // The day sheet opens every bill in one named window, so a second bill comes from a second
  // schedule tab, as a clinician keeping two bills open would have it.
  const sheets = [s.schedule, await context.newPage()];
  for (const sheet of sheets) {
    await h.gotoApp(sheet, s.config.baseUrl, daySheetPath);
    await settle(sheet, 'day sheet');
  }

  /** Schedule ▸ B on one appointment: that appointment's bill form, in a window of its own. */
  async function openBillForm(sheet, label) {
    const link = sheet.locator(`a[onclick*="/billing?billRegion="][onclick*="appointment_no=${appointment[label]}&"]`).first();
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    const [form] = await Promise.all([context.waitForEvent('page', { timeout: TIMEOUT }), link.click()]);
    await settle(form, `${label}'s bill form`);
    h.assert(await form.locator('#bcBillingForm').count() === 1, `B on ${label}'s appointment did not open the bill form`);
    h.assert(/^[A-Za-z0-9_-]{22}$/.test(await form.locator('#bcBillingForm input[name="billKey"]').inputValue()),
      `${label}'s bill form carries no bill key`);
    return form;
  }

  function claimOf(label) {
    return sql.rows(`SELECT b.demographic_no, b.appointment_no, bm.billing_code, bm.dx_code1
      FROM billing b JOIN billingmaster bm ON bm.billing_no=b.billing_no
      WHERE b.appointment_no=${appointment[label]} ORDER BY bm.billingmaster_no`);
  }

  await s.step('two bills open in two windows each review and save their own patient, appointment and codes', async () => {
    const formA = await openBillForm(sheets[0], 'A1');
    const formB = await openBillForm(sheets[1], 'B1');
    await chooseBillingPhysician(formA, physician.providerNo);
    await chooseBillingPhysician(formB, physician.providerNo);
    // B's bill was opened last, and B's window continued last: A's Continue must still be A's bill.
    await continueToReview(formB, 'B', { physician: physician.providerNo, codes: ['00120'], dx: '250' });
    await continueToReview(formA, 'A', { physician: physician.providerNo, codes: ['00100'], dx: '780' });
    const reviewA = await reviewText(formA);
    h.assert(names(reviewA, A_FIRST_NAME, B_FIRST_NAME), `A's bill review does not name A alone: ${reviewA.slice(0, 300)}`);
    const reviewB = await reviewText(formB);
    h.assert(names(reviewB, B_FIRST_NAME, A_FIRST_NAME), `B's bill review does not name B alone: ${reviewB.slice(0, 300)}`);

    await save(formA, 'A', 'Save Bill', sheets[0]);
    await save(formB, 'B', 'Save Bill', sheets[1]);
    await expectValue(sql, `SELECT COUNT(*) FROM billing WHERE demographic_no IN (${A},${B})`, '2',
      'The two windows did not save one claim each');
    const claimA = claimOf('A1');
    const claimB = claimOf('B1');
    h.assert(JSON.stringify(claimA) === JSON.stringify([[A, appointment.A1, '00100', '780']]),
      `A's claim was saved as ${JSON.stringify(claimA)}, not for A on A's appointment with A's codes`);
    h.assert(JSON.stringify(claimB) === JSON.stringify([[B, appointment.B1, '00120', '250']]),
      `B's claim was saved as ${JSON.stringify(claimB)}, not for B on B's appointment with B's codes`);
    for (const label of ['A1', 'B1']) {
      h.assert(sql.value(`SELECT status FROM appointment WHERE appointment_no=${appointment[label]}`).includes('B'),
        `${label}'s appointment was not marked billed`);
    }
  });

  /** The bill form in this window is still the one opened: its own key, its own patient. */
  async function expectOwnBillForm(form, label, billKey) {
    await settle(form, label);
    h.assert(await form.locator('#bcBillingForm').count() === 1, `${label} did not show the bill form`);
    h.assert(await form.locator('#bcBillingForm input[name="billKey"]').inputValue() === billKey,
      `${label} shows another bill than the one this window opened`);
    h.assert(names(await textOf(form.locator('body')), A_FIRST_NAME, B_FIRST_NAME), `${label} does not show A's bill alone`);
  }

  await s.step('one bill window keeps its own bill through in-place reloads, a refused Continue, Go Back and Another Bill', async () => {
    const form = await openBillForm(sheets[0], 'A2');
    const billKey = await form.locator('#bcBillingForm input[name="billKey"]').inputValue();
    // Each physician change reloads the patient bar and the code table in place. The list holds the
    // owned physician and nobody else on a bare install, so "Select Provider" is the other choice.
    const otherPhysician = (await form.locator('#xml_provider option').evaluateAll(options => options.map(o => o.value)))
      .find(value => value && value !== physician.providerNo) || '';
    for (const choice of [physician.providerNo, otherPhysician, physician.providerNo, otherPhysician, physician.providerNo]) {
      await changeAndAwaitReloads(form, '#xml_provider', choice, 2, 'changing the billing physician');
    }

    // Ten reloads by now, more than the session keeps bills. A Continue the validation refuses (a
    // service code that does not exist, warnings not ignored) returns to this window's own bill form.
    const unknownCode = unusedValue(sql, 'billingservice', 'service_code', '9', 4);
    const refused = await fillAndContinue(form, { physician: physician.providerNo, codes: [unknownCode], dx: '780',
      ignoreWarnings: false });
    h.assert(refused.status() === 302, `A Continue with an unknown service code answered HTTP ${refused.status()}`);
    await form.waitForURL(url => url.pathname.endsWith('/billing') && url.searchParams.get('billKey') === billKey,
      { timeout: TIMEOUT });
    await expectOwnBillForm(form, 'The bill form after a refused Continue', billKey);

    await continueToReview(form, 'A after the reloads', { physician: physician.providerNo, codes: ['00100'], dx: '780' });
    h.assert(names(await reviewText(form), A_FIRST_NAME, B_FIRST_NAME), 'The reloaded bill form\'s review does not name A alone');
    await Promise.all([
      form.waitForURL(url => url.pathname.endsWith('/billing') && url.searchParams.get('billKey') === billKey,
        { timeout: TIMEOUT }),
      form.locator('input[type="button"][value="Go Back"]').click(),
    ]);
    await expectOwnBillForm(form, 'The bill form after Go Back', billKey);

    await continueToReview(form, 'A after Go Back', { physician: physician.providerNo, codes: ['00100'], dx: '780' });
    const [another] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/BC/SaveBilling'), { timeout: TIMEOUT }),
      form.locator('input[type="submit"][name="submit"][value="Another Bill"]').click(),
    ]);
    h.assert(another.status() === 200, `Another Bill answered HTTP ${another.status()}`);
    await expectOwnBillForm(form, 'The bill form after Another Bill', billKey);
    await expectValue(sql, `SELECT COUNT(*) FROM billing WHERE appointment_no=${appointment.A2}`, '1',
      'Another Bill did not save the first claim');

    await continueToReview(form, 'A\'s other bill', { physician: physician.providerNo, codes: ['00120'], dx: '780' });
    await save(form, 'A\'s other bill', 'Save Bill', sheets[0]);
    const claims = claimOf('A2');
    h.assert(JSON.stringify(claims) === JSON.stringify([[A, appointment.A2, '00100', '780'], [A, appointment.A2, '00120', '780']]),
      `The window's two claims were saved as ${JSON.stringify(claims)}, not both for A on A's appointment`);
  });

  await s.step('Update Invoice on a two-line private receipt shows that receipt again, not another window\'s invoice', async () => {
    async function privateReceipt(sheet, label, codes) {
      const form = await openBillForm(sheet, label);
      await chooseBillingPhysician(form, physician.providerNo);
      // Private turns the billing form to the private one, reloading the code table in place.
      await changeAndAwaitReloads(form, '#xml_billtype', 'Pri', 1, 'choosing a private bill');
      await continueToReview(form, label, { physician: physician.providerNo, codes, dx: '780' });
      await save(form, label, 'Save & Print Receipt');
      await form.waitForURL(url => url.pathname.endsWith('/billing/CA/BC/billingView'), { timeout: TIMEOUT });
      await settle(form, `${label}'s receipt`);
      return form;
    }
    const receiptA = await privateReceipt(sheets[0], 'A3', ['00100', '00120']);
    const billsA = sql.rows(`SELECT billing_no FROM billing WHERE appointment_no=${appointment.A3} ORDER BY billing_no`).map(([id]) => id);
    h.assert(billsA.length === 2, `A's private bill of two lines was saved as ${billsA.length} claims`);
    h.assert(new URL(receiptA.url()).searchParams.getAll('billing_no').join(',') === billsA.join(','),
      'Save & Print Receipt did not open the receipt of both lines');
    const receiptB = await privateReceipt(sheets[1], 'B3', ['00100']);
    await receiptB.close();

    const [updated] = await Promise.all([
      receiptA.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/BC/UpdateBilling'), { timeout: TIMEOUT }),
      receiptA.locator('input[type="submit"][value="Update Invoice"]').click(),
    ]);
    h.assert(updated.status() < 400, `Update Invoice answered HTTP ${updated.status()}`);
    if (updated.status() === 302) {
      await receiptA.waitForURL(url => url.pathname.endsWith('/billing/CA/BC/billingView'), { timeout: TIMEOUT });
    }
    await settle(receiptA, 'A\'s receipt after Update Invoice');
    const text = await textOf(receiptA.locator('body'));
    h.assert(names(text, A_FIRST_NAME, B_FIRST_NAME), 'The receipt after Update Invoice is not A\'s alone');
    for (const code of ['00100', '00120']) {
      h.assert(text.includes(code), `The receipt after Update Invoice lost A's ${code} line`);
    }
    // Shown by the view of the receipt's own bills, loaded again, not rendered from the session.
    h.assert(updated.status() === 302 && new URL(receiptA.url()).pathname.endsWith('/billing/CA/BC/billingView')
      && new URL(receiptA.url()).searchParams.getAll('billing_no').join(',') === billsA.join(','),
    `Update Invoice did not show the view of A's bills ${billsA} (HTTP ${updated.status()}, ${receiptA.url()})`);
    await receiptA.close();
  });

  /** The service codes of the bill form's code table, as its checkboxes list them. */
  function serviceCodes(form) {
    return form.locator('#billingFormTable input[name="service"]').evaluateAll(boxes => boxes.map(box => box.value));
  }

  /** Bill WCB: the bill form lists its own patient's WCB forms (the list is fetched for the bill's patient). */
  async function chooseWcbBill(form, label, ownForm, otherForm) {
    const [listed] = await Promise.all([
      form.waitForResponse(r => new URL(r.url()).pathname.endsWith('/billing/CA/BC/ViewWcbForms'), { timeout: TIMEOUT }),
      form.locator('#xml_billtype').selectOption('WCB'),
    ]);
    h.assert(listed.status() === 200, `${label}'s WCB form list answered HTTP ${listed.status()}`);
    const choice = form.locator(`#wcbForms input[name="WCBid"][value="${ownForm}"]`);
    await choice.waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await form.locator(`#wcbForms input[name="WCBid"][value="${otherForm}"]`).count() === 0,
      `${label}'s bill form lists the other patient's WCB form`);
    await choice.check();
  }

  await s.step('a WCB bill keeps its own patient\'s WCB form through a refused Continue while another window bills B', async () => {
    const wcb = ownWcbForms(s, { A, B }, day);
    // A Continue that names B's WCB form for A's bill (a crafted post: the form lists A's forms
    // only) is refused before anything is billed, rather than billing B's report for A.
    const crafted = await openBillForm(sheets[0], 'A5');
    await chooseBillingPhysician(crafted, physician.providerNo);
    await chooseWcbBill(crafted, 'A', wcb.A, wcb.B);
    await crafted.locator(`#wcbForms input[name="WCBid"][value="${wcb.A}"]`)
      .evaluate((input, other) => { input.value = other; }, wcb.B);
    const craftedBad = s.recorder.badResponses.length;
    const craftedConsole = s.recorder.consoleIssues.length;
    const foreign = await fillAndContinue(crafted, { physician: physician.providerNo, codes: ['00100'], dx: '780',
      ignoreWarnings: true });
    h.assert(foreign.status() === 400, `A WCB Continue naming B's WCB form for A's bill answered HTTP ${foreign.status()}`);
    await crafted.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
    await crafted.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    h.assert((await crafted.locator('body').innerText()).includes('This WCB form is not this patient\'s.'),
      'The refused WCB Continue did not say why');
    // The refusal is the expected outcome: excuse its response, Chromium's console line for that
    // document load and the site icon a page without one asks the server root for.
    const refusals = s.recorder.badResponses.slice(craftedBad);
    h.assert(refusals.length === 1 && refusals[0].status === 400
      && new URL(refusals[0].url).pathname.endsWith('/billing/CA/BC/CreateBilling'),
    'The refused WCB Continue was not the one bad response');
    s.recorder.badResponses.splice(craftedBad, 1);
    for (let i = s.recorder.consoleIssues.length - 1; i >= craftedConsole; i--) {
      const entry = s.recorder.consoleIssues[i];
      const where = entry.location && entry.location.url ? new URL(entry.location.url).pathname : '';
      if (entry.type === 'error' && ((/status of 400\b/.test(entry.text) && where.endsWith('/billing/CA/BC/CreateBilling'))
          || (/status of 404\b/.test(entry.text) && where === '/favicon.ico'))) {
        s.recorder.consoleIssues.splice(i, 1);
      }
    }
    await crafted.close();
    const form = await openBillForm(sheets[0], 'A5');
    const billKey = await form.locator('#bcBillingForm input[name="billKey"]').inputValue();
    await chooseBillingPhysician(form, physician.providerNo);
    await chooseWcbBill(form, 'A', wcb.A, wcb.B);
    // B's bill is opened in the other window after A's.
    const formB = await openBillForm(sheets[1], 'B5');

    // A service code that needs a complete WCB report, with a form that has only the injury on
    // it: Continue is refused, warnings ignored or not, and comes back to this window's own bill.
    const refused = await fillAndContinue(form, { physician: physician.providerNo, codes: [WCB_FORM_CODE], dx: '780',
      ignoreWarnings: true });
    h.assert(refused.status() === 302, `A WCB Continue with an incomplete WCB form answered HTTP ${refused.status()}`);
    await form.waitForURL(url => url.pathname.endsWith('/billing') && url.searchParams.get('billKey') === billKey,
      { timeout: TIMEOUT });
    await expectOwnBillForm(form, 'The WCB bill form after a refused Continue', billKey);

    // The form comes back as a new MSP bill would; the prescriber chooses WCB and the form again.
    await chooseWcbBill(form, 'A, after the refused Continue,', wcb.A, wcb.B);
    await continueToReview(form, 'A\'s WCB bill', { physician: physician.providerNo, codes: ['00100'], dx: '780' });
    h.assert(names(await reviewText(form), A_FIRST_NAME, B_FIRST_NAME), 'A\'s WCB bill review does not name A alone');
    await save(form, 'A\'s WCB bill', 'Save Bill', sheets[0]);
    const claims = claimOf('A5');
    h.assert(JSON.stringify(claims) === JSON.stringify([[A, appointment.A5, '00100', '780']]),
      `A's WCB claim was saved as ${JSON.stringify(claims)}, not for A on A's appointment with A's codes`);
    h.assert(sql.value(`SELECT CONCAT(b.billingtype, ':', bm.wcb_id) FROM billing b
        JOIN billingmaster bm ON bm.billing_no=b.billing_no WHERE b.appointment_no=${appointment.A5}`) === `WCB:${wcb.A}`,
    'A\'s WCB claim does not carry A\'s WCB form');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing WHERE appointment_no=${appointment.B5}`) === '0',
      'Saving A\'s WCB bill saved a claim for B\'s appointment');
    await formB.close();
  });

  // Last: the eight bills it opens push every earlier bill of this login out of the session.
  await s.step('a bill window comes back to the billing form it shows, and one whose bill was dropped is told so', async () => {
    const form = await openBillForm(sheets[0], 'A4');
    const billKey = await form.locator('#bcBillingForm input[name="billKey"]').inputValue();
    await chooseBillingPhysician(form, physician.providerNo);
    const openedWith = await form.locator('#selectBillingForm').inputValue();
    const openedCodes = await serviceCodes(form);
    const chosen = (await form.locator('#selectBillingForm option').evaluateAll(options => options.map(o => o.value)))
      .find(value => value && value !== openedWith && value !== 'PRI');
    h.assert(chosen, 'The bill form offers no other billing form to choose');
    await changeAndAwaitReloads(form, '#selectBillingForm', chosen, 1, 'choosing another billing form');
    const chosenCodes = await serviceCodes(form);
    h.assert(chosenCodes.length && JSON.stringify(chosenCodes) !== JSON.stringify(openedCodes),
      `The ${chosen} billing form shows the same code table as ${openedWith}`);

    // A Continue the validation refuses comes back to this window's bill, with the form it showed.
    const unknownCode = unusedValue(sql, 'billingservice', 'service_code', '9', 4);
    const refused = await fillAndContinue(form, { physician: physician.providerNo, codes: [unknownCode], dx: '780',
      ignoreWarnings: false });
    h.assert(refused.status() === 302, `A Continue with an unknown service code answered HTTP ${refused.status()}`);
    await form.waitForURL(url => url.pathname.endsWith('/billing') && url.searchParams.get('billKey') === billKey,
      { timeout: TIMEOUT });
    await expectOwnBillForm(form, 'The bill form after a refused Continue', billKey);
    h.assert(await form.locator('#selectBillingForm').inputValue() === chosen,
      `The refused Continue came back to the ${openedWith} billing form the bill was opened with, not ${chosen}`);
    h.assert(JSON.stringify(await serviceCodes(form)) === JSON.stringify(chosenCodes),
      `The refused Continue came back with another code table than the ${chosen} form's`);

    // Eight bills opened since, from the day sheet's own link, are more than the session keeps.
    const onclick = await sheets[1].locator(`a[onclick*="/billing?billRegion="][onclick*="appointment_no=${appointment.A4}&"]`)
      .first().getAttribute('onclick');
    const link = (onclick.match(/["']([^"']*\/billing\?billRegion=[^"']*)["']/) || [])[1];
    h.assert(link, 'The day sheet\'s bill link names no bill form address');
    for (let i = 0; i < 8; i++) {
      const opened = await context.request.get(new URL(link, sheets[1].url()).toString());
      h.assert(opened.status() === 200, `Opening another bill answered HTTP ${opened.status()}`);
    }
    const badMark = s.recorder.badResponses.length;
    const consoleMark = s.recorder.consoleIssues.length;
    const expired = await fillAndContinue(form, { physician: physician.providerNo, codes: ['00100'], dx: '780',
      ignoreWarnings: true });
    h.assert(expired.status() === 400, `The dropped bill's Continue answered HTTP ${expired.status()}, not 400`);
    await form.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
    // Settled, so the browser's own requests for this plain-text document are in before they are judged.
    await form.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    h.assert((await form.locator('body').innerText()).includes('Billing session expired.'),
      'The dropped bill\'s Continue did not say that its billing session expired');
    // The refusal is this step's expected outcome, not a page failure: excuse its response and
    // Chromium's console line for that same document load, and the site icon Chromium asks the
    // server root for on a page that names none (an install without a root application answers
    // 404), and nothing else.
    const bad = s.recorder.badResponses.slice(badMark);
    h.assert(bad.length === 1 && bad[0].status === 400 && new URL(bad[0].url).pathname.endsWith('/billing/CA/BC/CreateBilling'),
      'The dropped bill\'s refusal was not the one bad response');
    s.recorder.badResponses.splice(badMark, 1);
    for (let i = s.recorder.consoleIssues.length - 1; i >= consoleMark; i--) {
      const entry = s.recorder.consoleIssues[i];
      const where = entry.location && entry.location.url ? new URL(entry.location.url).pathname : '';
      if (entry.type === 'error' && ((/status of 400\b/.test(entry.text) && where.endsWith('/billing/CA/BC/CreateBilling'))
          || (/status of 404\b/.test(entry.text) && where === '/favicon.ico'))) {
        s.recorder.consoleIssues.splice(i, 1);
      }
    }
    await form.close();
  });
}

async function preflight({ sql }) {
  if (sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='billingmaster'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required (billregion=BC)');
  }
}

if (require.main === module) runWorkflow('billing-bc-two-window-isolation', workflow, { openPatient: true, preflight });
module.exports = { workflow };
