/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Owned BC bills for the BC billing browser checks: one billing header plus one billingmaster
 * line, shaped like the rows the BC bill form saves (see billing-bc-reprocess-bill, which seeds the
 * same shape inline). Every row belongs to the workflow's own FAKE patient, so the cleanup is keyed
 * by that patient and still finds rows whose id read was lost after the INSERT.
 */
const { randomInt } = require('node:crypto');
const h = require('./playwright-harness');
const { clickOpensPopupOrNavigates } = require('./playwright-ui');
const { revealAuditLink } = require('./playwright-link-audit');
const { masterRecordLink } = require('./master-record-link');

/** Skip unless the database has the BC billing schema (billregion=BC installs). */
function requireBcBillingSchema(sql) {
  if (sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='billingmaster'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required (billregion=BC)');
  }
}

/** Make the owned patient a BC patient with a FAKE PHN (9 then the zero-padded patient number). */
function makeBcPatient(s) {
  const { sql, patient, marker } = s;
  sql.execute(`UPDATE demographic SET hc_type='BC', province='BC', hin='9${String(patient).padStart(9, '0').slice(-9)}', ver=''
    WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  return sql.value(`SELECT hin FROM demographic WHERE demographic_no=${patient}`);
}

/** A free number: `prefix` then random digits, `digits` long in all, for which `taken` is false. */
function unusedNumber(prefix, digits, taken) {
  for (let i = 0; i < 50; i++) {
    const candidate = prefix + String(randomInt(0, 10 ** (digits - prefix.length))).padStart(digits - prefix.length, '0');
    if (!taken(candidate)) return candidate;
  }
  throw new Error(`No free ${digits}-character number with prefix ${prefix}`);
}

/**
 * A billing physician the BC billing pages list: the test login's provider row with FAKE BC numbers
 * (practitioner 9xxxx, payee 8xxxx) and last name = the run marker. Removed, and asserted gone, in cleanup.
 * With `status: '0'` it is a physician who has left: inactive from the start, so the application's
 * cached active-provider list never holds it.
 *
 * @param {Object} s the workflow session
 * @param {{status?: '0'|'1'}} [options]
 * @returns {{providerNo: string, ohipNo: string, payeeNo: string}}
 */
function createBillingPhysician(s, { status = '1' } = {}) {
  h.assert(status === '0' || status === '1', 'A billing physician is active (1) or inactive (0)');
  const { sql, marker, provider } = s;
  const q = h.sqlString;
  const owned = {};
  s.cleanup(() => {
    if (!owned.providerNo) return;
    sql.execute(`DELETE FROM provider WHERE provider_no=${q(owned.providerNo)} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(owned.providerNo)}`) === '0', 'The owned billing physician was not removed');
  });
  owned.providerNo = unusedNumber('97', 6, c => sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(c)}`) !== '0');
  owned.ohipNo = unusedNumber('9', 5, c => sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE ohip_no=${q(c)})
    + (SELECT COUNT(*) FROM billingmaster WHERE practitioner_no=${q(c)})`) !== '0');
  owned.payeeNo = unusedNumber('8', 5, c => sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE billing_no=${q(c)})
    + (SELECT COUNT(*) FROM billingmaster WHERE payee_no=${q(c)})`) !== '0');
  const columns = sql.value(`SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='provider'`).split(',');
  const overrides = { provider_no: q(owned.providerNo), ohip_no: q(owned.ohipNo), billing_no: q(owned.payeeNo),
    last_name: q(marker), first_name: "'Billing'", status: q(status) };
  sql.execute(`INSERT INTO provider (${columns.map(c => `\`${c}\``).join(',')})
    SELECT ${columns.map(c => overrides[c] || `\`${c}\``).join(',')} FROM provider WHERE provider_no=${q(provider)}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(owned.providerNo)} AND ohip_no=${q(owned.ohipNo)}`) === '1',
    'The owned billing physician was not created');
  return owned;
}

/**
 * Remove every billing, billingmaster, billing_history, billingnote and bill_recipients row of the
 * owned patient, then assert none is left. Register once, before the first seed.
 */
function registerOwnedBcBillCleanup(s) {
  const { sql, patient } = s;
  s.cleanup(() => {
    const masters = sql.rows(`SELECT billingmaster_no FROM billingmaster WHERE demographic_no=${patient}`)
      .map(([id]) => id).filter((id) => /^\d+$/.test(id));
    const bills = sql.rows(`SELECT billing_no FROM billing WHERE demographic_no=${patient}`)
      .map(([id]) => id).filter((id) => /^\d+$/.test(id));
    const masterList = masters.length ? masters.join(',') : '0';
    const billList = bills.length ? bills.join(',') : '0';
    sql.execute([
      `DELETE FROM billing_history WHERE billingmaster_no IN (${masterList})`,
      `DELETE FROM billingnote WHERE billingmaster_no IN (${masterList})`,
      `DELETE FROM bill_recipients WHERE billingNo IN (${billList})`,
      `DELETE FROM billingmaster WHERE demographic_no=${patient}`,
      `DELETE FROM billing WHERE demographic_no=${patient}`,
    ].join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billingmaster WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billing_history WHERE billingmaster_no IN (${masterList}))
      + (SELECT COUNT(*) FROM billingnote WHERE billingmaster_no IN (${masterList}))
      + (SELECT COUNT(*) FROM bill_recipients WHERE billingNo IN (${billList}))`) === '0',
    'Owned BC bill rows were not removed');
  });
}

/**
 * Insert one owned BC bill.
 *
 * @param {Object} s the workflow session
 * @param {Object} bill
 * @param {string} [bill.billingtype] MSP, WCB, ICBC or Pri
 * @param {string} [bill.status] billingmaster.billingstatus (O not submitted, S settled, P bill patient, ...)
 * @param {string} [bill.code] fee code
 * @param {string} [bill.amount] bill amount
 * @param {string} [bill.serviceDate] YYYY-MM-DD; defaults to today
 * @param {string} [bill.dx] diagnostic code
 * @returns {{billingNo: string, billingmasterNo: string}}
 */
function seedOwnedBcBill(s, { billingtype = 'MSP', status = 'O', code = '00100', amount = '23.00', serviceDate, dx = '250' } = {}) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  h.assert(/^\d+\.\d\d$/.test(amount) && /^[A-Z0-9]{1,10}$/.test(code) && /^[A-Z]$/.test(status), 'Invalid BC bill fixture');
  const date = serviceDate ? q(serviceDate) : 'CURDATE()';
  const ohip = sql.value(`SELECT IFNULL(NULLIF(ohip_no,''),'00000') FROM provider WHERE provider_no=${q(provider)}`);
  const billingNo = sql.value(`INSERT INTO billing (clinic_no,demographic_no,provider_no,appointment_no,demographic_name,
      billing_date,billing_time,update_date,update_time,status,provider_ohip_no,billingtype,creator,total)
    VALUES (0,${patient},${q(provider)},0,${q(`${marker},Workflow`)},${date},CURTIME(),CURDATE(),CURTIME(),
      ${q(billingtype === 'Pri' ? 'P' : 'O')},${q(ohip)},${q(billingtype)},${q(provider)},${q(amount)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingNo), 'The BC billing fixture was not created');
  const billingmasterNo = sql.value(`INSERT INTO billingmaster (billing_no,createdate,billingstatus,demographic_no,appointment_no,
      claimcode,datacenter,payee_no,practitioner_no,phn,name_verify,dependent_num,billing_unit,clarification_code,
      anatomical_area,after_hour,new_program,billing_code,bill_amount,payment_mode,service_date,service_to_day,
      submission_code,extended_submission_code,dx_code1,service_location,referral_flag1,referral_flag2,birth_date,
      correspondence_code,claim_comment,mva_claim_code,paymentMethod)
    VALUES (${billingNo},NOW(),${q(status)},${patient},0,'C02','00000',${q(ohip.slice(0, 5))},${q(ohip.slice(0, 5))},
      (SELECT hin FROM demographic WHERE demographic_no=${patient}),'WOR','00','1','00','00','0','00',${q(code)},${q(amount)},
      '0',DATE_FORMAT(${date},'%Y%m%d'),'00','0',' ',${q(dx)},'A','0','0','19800102','0','','N',${billingtype === 'Pri' ? 9 : 6});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingmasterNo), 'The BC billingmaster fixture was not created');
  return { billingNo, billingmasterNo };
}

/** A BC bill form's in-place reload: the same page fetched again for its patient bar or code table. */
function isBillFormReload(response) {
  const url = new URL(response.url());
  return response.request().method() === 'GET' && url.pathname.endsWith('/billing') && url.searchParams.has('demographic_no');
}

/**
 * Change one of the BC bill form's selects and wait for the in-place reloads that change makes (the
 * physician reloads the patient bar and then the code table; the bill type, the code table). A
 * Continue pressed before they finish abandons the code table's reload, which the strict page check
 * reports as a failed request.
 *
 * @param {import('playwright').Page} form the bill form
 * @param {string} select the select's locator
 * @param {string} value the option to choose
 * @param {number} count how many reloads the change makes
 * @param {string} what the change, for messages
 */
async function changeBillFormSelect(form, select, value, count, what, timeout = 30000) {
  const reloads = [];
  const onResponse = (response) => { if (isBillFormReload(response)) reloads.push(response); };
  form.on('response', onResponse);
  try {
    await form.locator(select).selectOption(value);
    const deadline = Date.now() + timeout;
    while (reloads.length < count && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 100));
    h.assert(reloads.length >= count, `${what} reloaded the bill form ${reloads.length} times, not ${count}`);
    await form.waitForFunction(() => !window.jQuery || window.jQuery.active === 0, null, { timeout });
  } finally {
    form.off('response', onResponse);
  }
  for (const response of reloads) {
    h.assert(response.status() === 200, `A bill form reload after ${what} answered HTTP ${response.status()}`);
  }
  await h.assertNotErrorPage(form, `bill form after ${what}`);
}

/** Open Bill Status from the master record's Invoice List link (Billing ▸ Invoice List), filtered to the owned patient. */
async function openBillStatus(s, ui) {
  const link = masterRecordLink(s.master, '/billing/CA/BC/reprocessBill?').first();
  h.assert(await link.count() === 1, 'The BC master record has no Invoice List link');
  // Click the visible words: on BC the inline eligibility menu widens the navigation cell under the
  // content pane, so the link's centre is covered (app-findings-log.md finding 146).
  const page = await ui.clickOpensPopup(s.master, link, { context: s.context, recorder: s.recorder,
    label: 'bc-bill-status', timeout: 20000, position: { x: 8, y: 8 } });
  await page.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(page, 'BC Bill Status');
  h.assert(new URL(page.url()).pathname.endsWith('/billing/CA/BC/reprocessBill'), 'Invoice List did not open Bill Status');
  return page;
}

/**
 * Set Bill Status's filter form, press Create Report and wait for the GET to load the new list.
 *
 * @param {Object} opts
 * @param {string} opts.status the billTypes radio value (O, S, P, %, ...)
 * @param {boolean} [opts.showPrivate] whether the Private box stays ticked (MSP, WCB and ICBC always are)
 * @param {string} [opts.from] first service date, YYYY-MM-DD ('' for none)
 * @param {string} [opts.to] last service date, YYYY-MM-DD ('' for none)
 */
async function reportBillStatus(page, ui, { status, showPrivate = true, from = '', to = '' }) {
  const timeout = 30000;
  const form = page.locator('form[name="serviceform"]');
  await form.locator(`input[name="billTypes"][value="${status}"]`).check();
  const privateBox = form.locator('input[name="showPRIV"]');
  if (await privateBox.isChecked() !== showPrivate) await privateBox.setChecked(showPrivate);
  for (const name of ['showMSP', 'showWCB', 'showICBC']) {
    const box = form.locator(`input[name="${name}"]`);
    if (!await box.isChecked()) await box.check();
  }
  await form.locator('#xml_vdate').fill(from);
  await form.locator('#xml_appointment_date').fill(to);
  const loaded = page.waitForRequest(request => request.method() === 'GET'
    && new URL(request.url()).pathname.endsWith('/billing/CA/BC/reprocessBill')
    && new URL(request.url()).searchParams.get('billTypes') === status, { timeout });
  await ui.clickAndAwaitReload(page, form.locator('input[type="submit"][value="Create Report"]'),
    { label: `Create Report (${status})`, timeout });
  await loaded;
  await h.assertNotErrorPage(page, `BC Bill Status report (${status})`);
}

/**
 * Administration ▸ Billing ▸ Edit Invoices: the clinic-wide Bill Status, loaded into the
 * Administration frame. The Master Record's Invoice List is the same page fixed to one patient, and
 * there billStatus.jsp lists every status whatever the radio says; the status filter is the
 * clinic-wide page's. Returns { admin, frame }: the page that holds the frame, and the frame.
 */
async function openClinicBillStatus(s) {
  const timeout = 30000;
  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'bc-administration', timeout });
  const link = admin.getByRole('link', { name: 'Edit Invoices', exact: true, includeHidden: true });
  await revealAuditLink(admin, link, timeout);
  await link.click();
  const deadline = Date.now() + timeout;
  let frame;
  while (!(frame = admin.frames().find(f => f.parentFrame() && new URL(f.url(), 'http://x').pathname.endsWith('/billing/CA/BC/reprocessBill')))) {
    h.assert(Date.now() < deadline, 'Edit Invoices did not load Bill Status into the administration frame');
    await admin.waitForTimeout(100);
  }
  await frame.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(frame, 'Clinic-wide Bill Status');
  return { admin, frame };
}

/**
 * Fill the clinic-wide Bill Status form for one patient and press Create Report. The form is a GET
 * back to the same route inside the frame; the answer is awaited on the page that holds the frame
 * (a frame has no request events of its own).
 */
async function reportClinicBillStatus({ admin, frame }, { demographicNo, status, showPrivate = true, from = '', to = '' }) {
  const timeout = 30000;
  const form = frame.locator('form[name="serviceform"]');
  await form.locator('input[name="demographicNo"]').fill(String(demographicNo));
  await form.locator(`input[name="billTypes"][value="${status}"]`).check();
  const privateBox = form.locator('input[name="showPRIV"]');
  if (await privateBox.isChecked() !== showPrivate) await privateBox.setChecked(showPrivate);
  for (const name of ['showMSP', 'showWCB', 'showICBC']) {
    const box = form.locator(`input[name="${name}"]`);
    if (!await box.isChecked()) await box.check();
  }
  await form.locator('#xml_vdate').fill(from);
  await form.locator('#xml_appointment_date').fill(to);
  await Promise.all([
    admin.waitForResponse(response => response.request().method() === 'GET'
      && new URL(response.url()).pathname.endsWith('/billing/CA/BC/reprocessBill')
      && new URL(response.url()).searchParams.get('billTypes') === status
      && new URL(response.url()).searchParams.get('demographicNo') === String(demographicNo), { timeout }),
    // Response headers can arrive before the frame commits its new document.
    // Wait for that commit before asking for the new document's load state.
    admin.waitForEvent('framenavigated', { predicate: navigated => navigated === frame, timeout }),
    form.locator('input[type="submit"][value="Create Report"]').click(),
  ]);
  await frame.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(frame, `Clinic-wide Bill Status report (${status})`);
}

module.exports = { requireBcBillingSchema, makeBcPatient, unusedNumber, createBillingPhysician, changeBillFormSelect, registerOwnedBcBillCleanup, seedOwnedBcBill, openBillStatus, reportBillStatus,
  openClinicBillStatus, reportClinicBillStatus };
