#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check that Ontario bills are actually SAVED through the bill-entry
 * form, for the three bill types alpha-11 testers reported working:
 *
 *   - OHIP ("ODP"): a favourite-grid service code plus a typed diagnostic code
 *   - WSIB ("WCB | Worker's Compensation Board"): the same form, so the header
 *     must land with pay_program WCB and status W
 *   - Bonus Codes ("BON"): the bill-type switch navigates the form into the
 *     bonus view and a Q-code typed into the free service-code column must save
 *     as an HCP bonus claim (status I) with no patient HIN on the header
 *
 * billing-on-third-party-playwright-checks.js is deliberately read-only (it
 * only proves bill-type navigation keeps billRegion). This check completes the
 * journey: Next -> review page -> Save, and verifies billing_on_cheader1 /
 * billing_on_item rows plus the appointment's billed status, then deletes
 * every row it created (appointments, headers, items, ext, transactions).
 *
 * Fixture: it inserts its own appointments for BILLING_DEMOGRAPHIC_NO with
 * BILLING_PROVIDER_NO on a fixed past date, so no demo appointment is billed.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: BILLING_DEMOGRAPHIC_NO (1), BILLING_PROVIDER_NO (999998),
 *   BILLING_SUBMIT_DATE (2024-05-06), BILLING_OHIP_CODE (A007A),
 *   BILLING_BONUS_CODE (Q040A), BILLING_DX_CODE (250),
 *   BILLING_FORM_NAME (General Practice) -- the entry in the "Billing form"
 *   chooser whose favourite grid carries BILLING_OHIP_CODE. The grid stays
 *   hidden until a form is chosen when the install's default_view does not
 *   name a real service type, which is what an operator sees on a stock dev
 *   install, so the check always picks the form the way an operator does.
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  validateBaseUrl,
  validateMysqlHost,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};
const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || '127.0.0.1');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

function digits(name, raw, fallback) {
  const value = raw === undefined || raw === '' ? fallback : raw;
  assert(/^\d+$/.test(value), `${name} must be numeric, got ${value}`);
  return value;
}
function code(name, raw, fallback) {
  const value = (raw === undefined || raw === '' ? fallback : raw).toUpperCase();
  assert(/^[A-Z]\d{3}[A-Z]$/.test(value), `${name} must be an Ontario service code like A007A, got ${value}`);
  return value;
}
const demographicNo = digits('BILLING_DEMOGRAPHIC_NO', process.env.BILLING_DEMOGRAPHIC_NO, '1');
const providerNo = digits('BILLING_PROVIDER_NO', process.env.BILLING_PROVIDER_NO, '999998');
const billingDate = process.env.BILLING_SUBMIT_DATE || '2024-05-06';
assert(/^\d{4}-\d{2}-\d{2}$/.test(billingDate), 'BILLING_SUBMIT_DATE must be YYYY-MM-DD');
const ohipCode = code('BILLING_OHIP_CODE', process.env.BILLING_OHIP_CODE, 'A007A');
const bonusCode = code('BILLING_BONUS_CODE', process.env.BILLING_BONUS_CODE, 'Q040A');
const dxCode = process.env.BILLING_DX_CODE || '250';
assert(/^\d{3,4}$/.test(dxCode), 'BILLING_DX_CODE must be a 3-4 digit diagnostic code');

const billingFormName = process.env.BILLING_FORM_NAME || 'General Practice';
const stamp = `PW_BILL_${Date.now()}`;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'billing-on-submit-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  mysqlDefaults = { dir, file };
}
function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}
function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
}
function sqlRows(query) {
  const out = sql(query);
  return out ? out.split('\n').map((line) => line.split('\t')) : [];
}
function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

const createdAppointments = [];

function createAppointment(startTime, label) {
  sql(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no, program_id, notes, reason, location, resources, type, style, billing, status, createdatetime, updatedatetime, creator, remarks, urgency)`
    + ` SELECT '${escapeSql(providerNo)}', '${escapeSql(billingDate)}', '${startTime}', ADDTIME('${startTime}', '00:14:00'), CONCAT(last_name, ',', first_name), ${Number(demographicNo)}, 0, '${escapeSql(`${stamp} ${label}`)}', '${escapeSql(`${stamp} ${label}`)}', '', '', '', '', '', 't', NOW(), NOW(), '${escapeSql(providerNo)}', '', ''`
    + ` FROM demographic WHERE demographic_no=${Number(demographicNo)}`);
  const id = sql(`SELECT appointment_no FROM appointment WHERE notes='${escapeSql(`${stamp} ${label}`)}' ORDER BY appointment_no DESC LIMIT 1`);
  assert(/^\d+$/.test(id), `could not create fixture appointment for ${label}`);
  createdAppointments.push(id);
  return id;
}

function cleanupRows() {
  const headerIds = sqlRows(`SELECT id FROM billing_on_cheader1 WHERE appointment_no IN (SELECT appointment_no FROM appointment WHERE notes LIKE '${escapeSql(`${stamp}%`)}')`
    + ` OR (demographic_no=${Number(demographicNo)} AND billing_date='${escapeSql(billingDate)}' AND comment1 LIKE '${escapeSql(`${stamp}%`)}')`).map((row) => row[0]);
  // Bonus headers carry no appointment number, so also sweep by the item date
  // + provider + our fixture window; anything we created is deleted by id.
  for (const id of [...new Set([...headerIds, ...createdHeaderIds])]) {
    sql(`DELETE FROM billing_on_transaction WHERE ch1_id=${Number(id)}`);
    sql(`DELETE FROM billing_on_ext WHERE billing_no=${Number(id)}`);
    sql(`DELETE FROM billing_on_item WHERE ch1_id=${Number(id)}`);
    sql(`DELETE FROM billing_on_cheader1 WHERE id=${Number(id)}`);
  }
  sql(`DELETE FROM appointment WHERE notes LIKE '${escapeSql(`${stamp}%`)}'`);
}
const createdHeaderIds = [];

function billEntryPath(appointmentNo, startTime) {
  const params = new URLSearchParams({
    billRegion: 'ON',
    hotclick: '',
    appointment_no: appointmentNo,
    demographic_no: demographicNo,
    apptProvider_no: providerNo,
    providerview: providerNo,
    appointment_date: billingDate,
    status: 't',
    start_time: startTime,
    bNewForm: '1',
  });
  return `/billing?${params.toString()}`;
}

async function openBillForm(context, recorder, label, appointmentNo, startTime) {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, billEntryPath(appointmentNo, startTime));
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, `${label} bill form`);
  await page.locator('select[name="xml_billtype"]').waitFor({ state: 'visible', timeout: 30000 });
  return page;
}

async function selectBillType(page, prefix) {
  const select = page.locator('select[name="xml_billtype"]');
  const value = await select.locator('option').evaluateAll((options, wanted) => {
    const match = options.find((option) => option.value.startsWith(wanted));
    return match ? match.value : null;
  }, prefix);
  assert(value, `bill form did not offer bill type ${prefix}`);
  const navigates = ['PAT', 'OCF', 'ODS', 'CPP', 'STD', 'BON'].includes(prefix);
  if (navigates) {
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
      select.selectOption(value),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, `bill form after switching to ${prefix}`);
    const url = new URL(page.url());
    assert(url.searchParams.get('billRegion') === 'ON', `bill-type switch to ${prefix} dropped billRegion=ON`);
    assert((url.searchParams.get('xml_billtype') || '').startsWith(prefix), `bill-type switch did not carry xml_billtype=${prefix}`);
  } else {
    await select.selectOption(value);
  }
  const selected = await page.locator('select[name="xml_billtype"]').inputValue();
  assert(selected.startsWith(prefix), `bill type ${prefix} was not selected after the switch (got ${selected})`);
}

async function chooseBillingForm(page, label) {
  // Operator path: "Billing form" link -> chooser layer -> form name. This
  // reveals that form's favourite-code grid (toggleDiv) for the checkboxes.
  await page.locator('a[onclick*="showHideLayers(\'Layer1\',\'\',\'show\')"]').first().click();
  const layer = page.locator('#Layer1');
  await layer.waitFor({ state: 'visible', timeout: 15000 });
  const entry = layer.locator('a', { hasText: billingFormName }).first();
  assert(await entry.count(), `${label}: billing form chooser did not list "${billingFormName}"`);
  await entry.click();
  await page.waitForFunction(() => document.getElementById('Layer1').style.visibility !== 'visible', null, { timeout: 15000 });
  const formName = await page.locator('#billFormName').inputValue();
  assert(formName.startsWith(billingFormName.slice(0, 20)), `${label}: choosing the billing form did not set the form name (got "${formName}")`);
  const formCode = await page.locator('#billForm').inputValue();
  assert(formCode, `${label}: choosing the billing form did not set the billForm code`);
  return formCode;
}

async function selectedBillingPhysician(page) {
  // xml_provider option values are "<provider_no>|<ohip_no>".
  const value = await page.locator('select[name="xml_provider"]').inputValue();
  const providerPart = value.split('|')[0];
  assert(/^-?\d+$/.test(providerPart), `billing physician selector held an unexpected value "${value}"`);
  return providerPart;
}

async function submitToReview(page, label) {
  await Promise.all([
    page.waitForURL(/ViewBillingONReview/, { timeout: 30000 }),
    page.locator('#titlesearch input[type="submit"][name="submit"]').click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, `${label} review page`);
  const reviewText = await page.locator('body').innerText();
  const saveOffered = (await page.locator('input[type="submit"][value="Save"]').count()) > 0;
  assert(!/duplicate/i.test(reviewText) || saveOffered,
    `${label} review page reported a problem: ${reviewText.slice(0, 300)}`);
}

async function saveFromReview(page, label) {
  const saveButton = page.locator('form[name="titlesearch"] input[type="submit"][value="Save"]');
  assert(await saveButton.count(), `${label} review page did not offer the Save button (code invalid or duplicate?)`);
  const [saveResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/billing/CA/ON/BillingONSave'), { timeout: 30000 }),
    saveButton.click(),
  ]);
  assert(saveResponse.status() < 400, `${label} save returned HTTP ${saveResponse.status()}`);
  const savePostData = new URLSearchParams(saveResponse.request().postData() || '');
  assert(savePostData.get('billingAction') === 'SAVE', `${label} save posted billingAction=${savePostData.get('billingAction')}`);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  const body = await page.locator('body').innerText().catch(() => '');
  assert(!/Save Failed|billingFailed|CARLOS Error/i.test(body), `${label} save page reported a failure: ${body.slice(0, 300)}`);
  return savePostData;
}

function headerRows(appointmentNo) {
  return sqlRows(`SELECT id, pay_program, status, demographic_no, provider_no, billing_date, hin, total, visittype`
    + ` FROM billing_on_cheader1 WHERE appointment_no=${Number(appointmentNo)} ORDER BY id`)
    .map(([id, payProgram, status, demoNo, provider, date, hin, total, visitType]) => ({ id, payProgram, status, demoNo, provider, date, hin, total, visitType }));
}
function itemRows(headerId) {
  return sqlRows(`SELECT service_code, fee, ser_num, dx, status, service_date FROM billing_on_item WHERE ch1_id=${Number(headerId)} ORDER BY id`)
    .map(([serviceCode, fee, units, dx, status, serviceDate]) => ({ serviceCode, fee, units, dx, status, serviceDate }));
}
function appointmentStatus(appointmentNo) {
  return sql(`SELECT status FROM appointment WHERE appointment_no=${Number(appointmentNo)}`);
}
function expectedFee(serviceCode) {
  return sql(`SELECT value FROM billingservice WHERE service_code='${escapeSql(serviceCode)}' ORDER BY billingservice_date DESC LIMIT 1`);
}

async function billOhipOrWsib(context, recorder, { label, billType, expectPayProgram, expectStatus, startTime }) {
  const appointmentNo = createAppointment(startTime, label);
  const page = await openBillForm(context, recorder, label, appointmentNo, startTime);
  await selectBillType(page, billType);
  await chooseBillingForm(page, label);
  // The favourite grid is rendered once per service type and only the active
  // form's copy is shown, so pick the visible checkbox for the code.
  const codeBox = page.locator(`input[name="xml_${ohipCode}"]:visible`).first();
  assert(await codeBox.count(), `bill form favourite grid did not offer ${ohipCode}; set BILLING_OHIP_CODE to a code on the default form`);
  await codeBox.check();
  const billingPhysician = await selectedBillingPhysician(page);
  await page.locator('input[name="dxCode"]').fill(dxCode);
  await page.locator('input[name="dxCode"]').dispatchEvent('change');
  await submitToReview(page, label);
  const reviewText = await page.locator('body').innerText();
  assert(reviewText.includes(ohipCode), `${label} review page did not list ${ohipCode}`);
  await saveFromReview(page, label);
  await page.close().catch(() => {});

  const headers = headerRows(appointmentNo);
  assert(headers.length === 1, `${label}: expected one billing header for appointment ${appointmentNo}, found ${headers.length}`);
  const header = headers[0];
  createdHeaderIds.push(header.id);
  assert(header.payProgram === expectPayProgram, `${label}: pay_program was ${header.payProgram}, expected ${expectPayProgram}`);
  assert(header.status === expectStatus, `${label}: header status was ${header.status}, expected ${expectStatus}`);
  assert(header.demoNo === demographicNo, `${label}: header demographic ${header.demoNo}, expected ${demographicNo}`);
  assert(header.provider === billingPhysician, `${label}: header provider ${header.provider} did not match the billing physician ${billingPhysician}`);
  assert(header.date === billingDate, `${label}: billing_date ${header.date}, expected ${billingDate}`);
  assert(header.hin && header.hin.trim() !== '', `${label}: header did not carry the patient HIN`);
  const items = itemRows(header.id);
  assert(items.length === 1 && items[0].serviceCode === ohipCode, `${label}: expected one ${ohipCode} item, got ${JSON.stringify(items)}`);
  assert(items[0].dx === dxCode, `${label}: item dx was ${items[0].dx}, expected ${dxCode}`);
  assert(Number(items[0].fee) === Number(expectedFee(ohipCode)), `${label}: item fee ${items[0].fee} did not match the schedule fee ${expectedFee(ohipCode)}`);
  assert(Number(header.total) === Number(items[0].fee), `${label}: header total ${header.total} did not match the item fee`);
  const apptStatus = appointmentStatus(appointmentNo);
  assert(/B/.test(apptStatus), `${label}: appointment status ${apptStatus} was not flagged as billed`);
  return header.id;
}

async function billBonus(context, recorder, { label, startTime }) {
  const appointmentNo = createAppointment(startTime, label);
  const page = await openBillForm(context, recorder, label, appointmentNo, startTime);
  await selectBillType(page, 'BON');
  // Bonus codes are typed into the free service-code column; no favourite
  // grid is needed, so no billing form is chosen here.
  const billingPhysician = await selectedBillingPhysician(page);
  await page.locator('input[name="serviceCode0"]').fill(bonusCode);
  await page.locator('input[name="serviceCode0"]').dispatchEvent('blur');
  await page.locator('input[name="dxCode"]').fill(dxCode);
  await submitToReview(page, label);
  const reviewText = await page.locator('body').innerText();
  assert(reviewText.includes(bonusCode), `${label} review page did not list ${bonusCode}`);
  await saveFromReview(page, label);
  await page.close().catch(() => {});

  // Bonus claims are saved without patient identity (no HIN / appointment on
  // the header), so find the row through its item.
  const rows = sqlRows(`SELECT h.id, h.pay_program, h.status, h.hin, h.total, i.service_code, i.fee, h.provider_no`
    + ` FROM billing_on_cheader1 h JOIN billing_on_item i ON i.ch1_id=h.id`
    + ` WHERE i.service_code='${escapeSql(bonusCode)}' AND h.timestamp1 >= (NOW() - INTERVAL 10 MINUTE)`
    + ` AND h.id NOT IN (${createdHeaderIds.length ? createdHeaderIds.map(Number).join(',') : '0'}) ORDER BY h.id DESC`);
  assert(rows.length >= 1, `${label}: no bonus billing header with ${bonusCode} was saved`);
  const [id, payProgram, status, hin, total, serviceCode, fee, headerProvider] = rows[0];
  createdHeaderIds.push(id);
  assert(headerProvider === billingPhysician, `${label}: bonus header provider ${headerProvider} did not match the billing physician ${billingPhysician}`);
  assert(payProgram === 'HCP', `${label}: bonus pay_program was ${payProgram}, expected HCP`);
  assert(status === 'I', `${label}: bonus header status was ${status}, expected I`);
  assert(!hin || hin.trim() === '', `${label}: bonus header unexpectedly carried a HIN (${hin})`);
  assert(serviceCode === bonusCode && Number(fee) === Number(expectedFee(bonusCode)), `${label}: bonus item ${serviceCode}/${fee} did not match ${bonusCode}/${expectedFee(bonusCode)}`);
  assert(Number(total) === Number(fee), `${label}: bonus header total ${total} did not match the item fee ${fee}`);
  return id;
}

let browser = null;
let cleanupDone = false;
// Runs once from the finally block or the signal handler: every step is attempted
// and a step that fails marks the run as failed, because a fixture left behind is
// a failure of this check even when every assertion passed.
function runCleanup() {
  if (cleanupDone || !mysqlDefaults) {
    return;
  }
  cleanupDone = true;
  for (const step of [cleanupRows]) {
    try {
      step();
    } catch (cleanupError) {
      console.error(`FAIL cleanup step ${step.name} failed: ${cleanupError.message}`);
      process.exitCode = 1;
    }
  }
}
// Node does not run finally blocks on SIGINT/SIGTERM (the suite loop's `timeout`
// sends TERM), so restore the fixtures here too before exiting.
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    console.error(`${signal} received; restoring fixtures before exiting.`);
    runCleanup();
    cleanupMysqlDefaults();
    process.exit(130);
  });
}

(async () => {
  const recorder = createRecorder();
  initMysqlDefaults();
  // Staging and the browser launch sit inside the protected scope so a failure in
  // either still reaches the fixture cleanup below.
  try {
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    await login(context, config, recorder);

    const ohipHeader = await billOhipOrWsib(context, recorder, {
      label: 'ohip', billType: 'ODP', expectPayProgram: 'HCP', expectStatus: 'O', startTime: '09:00:00',
    });
    const wsibHeader = await billOhipOrWsib(context, recorder, {
      label: 'wsib', billType: 'WCB', expectPayProgram: 'WCB', expectStatus: 'W', startTime: '09:15:00',
    });
    const bonusHeader = await billBonus(context, recorder, { label: 'bonus', startTime: '09:30:00' });

    // Known defect, tracked for review: billingONReview.jsp's onSave() reads
    // #payee, which only renders for 3rd-party bills, so OHIP/WSIB/bonus saves
    // throw a TypeError in the onsubmit handler (the browser still submits
    // the form, so the bill saves, but checkTotal() never runs). Everything
    // else the browser raises fails the check.
    assertNoPageErrors(recorder, null, [/Cannot read properties of null \(reading 'value'\)[\s\S]*at onSave/]);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`PASS Ontario bills saved through the UI: OHIP header ${ohipHeader}, WSIB header ${wsibHeader}, bonus header ${bonusHeader}`);
  } catch (error) {
    console.error(`FAIL Ontario bill submit check: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
    runCleanup();
    cleanupMysqlDefaults();
  }
})();
