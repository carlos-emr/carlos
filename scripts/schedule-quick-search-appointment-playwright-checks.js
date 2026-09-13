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
 * Browser check for the schedule's quick-search widget and booking an
 * appointment from it, which alpha-11 testers reported working ("search
 * widget works including setting appts"). patient-search-dob covers the
 * separate Search popup; echart-new-patient-notes books from a day-sheet
 * slot. Nothing drove #quickSearch before this check.
 *
 *   1. types the patient's last name into #quickSearch on the schedule; the
 *      dropdown must list the patient with the M / E / Rx / Appt badges;
 *   2. clicks the Appt badge: the schedule asks FindNextAvailableSlot for
 *      the patient's MRP, navigates the day sheet to that day and opens the
 *      add-appointment popup with the patient and the slot pre-filled;
 *   3. enters a reason and books; the popup closes and the appointment row
 *      exists for the patient, provider, date and time the slot returned;
 *   4. the day sheet shows the appointment link for it.
 *
 * The appointment is deleted in a finally.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: QUICKSEARCH_DEMOGRAPHIC_NO (1; the patient must be active with
 *   an MRP who has a schedule template applied in the next 90 days).
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
const demographicNo = process.env.QUICKSEARCH_DEMOGRAPHIC_NO || '1';
assert(/^\d+$/.test(demographicNo), 'QUICKSEARCH_DEMOGRAPHIC_NO must be numeric');
const reason = `PW_QS_APPT_${Date.now()}`;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'quick-search-appt-'));
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
function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function appointmentRow() {
  const out = sql(`SELECT appointment_no, provider_no, appointment_date, start_time, end_time, demographic_no, name, creator FROM appointment WHERE reason='${escapeSql(reason)}' ORDER BY appointment_no DESC LIMIT 1`);
  if (!out) {
    return null;
  }
  const [id, providerNo, date, startTime, endTime, demoNo, name, creator] = out.split('\t');
  return { id, providerNo, date, startTime, endTime, demoNo, name, creator };
}
function cleanupRows() {
  sql(`DELETE FROM appointment WHERE reason='${escapeSql(reason)}'`);
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
    const [lastName, firstName, mrp, status] = sql(`SELECT last_name, first_name, IFNULL(provider_no,''), IFNULL(patient_status,'') FROM demographic WHERE demographic_no=${Number(demographicNo)}`).split('\t');
    assert(lastName, `demographic ${demographicNo} not found`);
    assert(mrp, `demographic ${demographicNo} has no MRP, so the widget will not offer Appt`);
    assert(!status || status === 'AC', `demographic ${demographicNo} is not active (${status})`);
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1600, height: 1100 } });
    const schedule = await login(context, config, recorder);
    await assertNotErrorPage(schedule, 'schedule');

    // 1. Quick search.
    await schedule.locator('#quickSearch').click();
    const [searchResponse] = await Promise.all([
      schedule.waitForResponse((response) => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/demographic/SearchDemographic'), { timeout: 30000 }),
      schedule.keyboard.type(lastName, { delay: 40 }),
    ]);
    assert(searchResponse.status() === 200, `quick search returned HTTP ${searchResponse.status()}`);
    const dropdown = schedule.locator('#quickSearchDropdown');
    await dropdown.waitFor({ state: 'visible', timeout: 15000 });
    const row = dropdown.locator('.qs-result-row').filter({ hasText: firstName }).filter({ hasText: lastName }).first();
    await row.waitFor({ state: 'visible', timeout: 15000 });
    for (const badge of ['.qs-badge-m', '.qs-badge-e', '.qs-badge-rx', '.qs-badge-appt']) {
      assert(await row.locator(badge).count(), `quick search row did not offer the ${badge} badge`);
    }

    // 2. Appt badge -> next available slot -> popup. The page navigates as
    // soon as the slot arrives, so read the same answer the badge will get
    // through the API first, then require the badge's own call to succeed.
    const slotProbe = await context.request.get(`${config.baseUrl.href}/demographic/FindNextAvailableSlot?providerNos=${encodeURIComponent(mrp)}`);
    assert(slotProbe.ok(), `FindNextAvailableSlot returned HTTP ${slotProbe.status()}`);
    const slot = await slotProbe.json();
    assert(slot && slot.found, `no available slot was found for the MRP: ${JSON.stringify(slot)}`);
    const popupPromise = context.waitForEvent('page', { timeout: 60000 });
    const [slotResponse] = await Promise.all([
      schedule.waitForResponse((response) => /\/demographic\/FindNextAvailableSlot\?/.test(response.url()), { timeout: 30000 }),
      row.locator('.qs-badge-appt').click(),
    ]);
    assert(slotResponse.status() === 200, `the Appt badge's FindNextAvailableSlot call returned HTTP ${slotResponse.status()}`);
    assert(String(slot.providerNo) === mrp, `slot provider ${slot.providerNo} is not the patient's MRP ${mrp}`);
    const popup = await popupPromise;
    wirePage(popup, 'add-appointment', recorder);
    await popup.waitForURL(/\/appointment\/addappointment\?/, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await assertNotErrorPage(popup, 'add-appointment popup');
    await schedule.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
    const scheduleUrl = new URL(schedule.url());
    assert(scheduleUrl.searchParams.get('year') === String(slot.year) && scheduleUrl.searchParams.get('day') === String(slot.day),
      `schedule did not navigate to the slot's day (${schedule.url()})`);
    assert((await popup.locator('#demographic_no').inputValue()) === demographicNo, 'popup did not pre-select the patient');
    const expectedDate = `${slot.year}-${String(slot.month).padStart(2, '0')}-${String(slot.day).padStart(2, '0')}`;
    assert((await popup.locator('input[name="appointment_date"]').inputValue()) === expectedDate, `popup date was ${await popup.locator('input[name="appointment_date"]').inputValue()}, expected ${expectedDate}`);
    assert((await popup.locator('input[name="start_time"]').inputValue()) === slot.startTime, `popup start time was ${await popup.locator('input[name="start_time"]').inputValue()}, expected ${slot.startTime}`);
    assert((await popup.locator('input[name="provider_no"]').inputValue()) === String(slot.providerNo), 'popup provider did not match the slot');

    // 3. Book.
    await popup.locator('#reason').fill(reason);
    const closed = popup.waitForEvent('close', { timeout: 30000 });
    await popup.locator('#addButton').click();
    await closed;
    const appointment = appointmentRow();
    assert(appointment, 'appointment row was not created');
    assert(appointment.demoNo === demographicNo, `appointment patient was ${appointment.demoNo}`);
    assert(appointment.providerNo === String(slot.providerNo), `appointment provider was ${appointment.providerNo}`);
    assert(appointment.date === expectedDate, `appointment date was ${appointment.date}`);
    assert(appointment.startTime === `${slot.startTime}:00`, `appointment start was ${appointment.startTime}`);
    assert(appointment.name === `${lastName},${firstName}`, `appointment name was ${appointment.name}`);

    // 4. Day sheet shows it. The booking popup refreshes its opener on the way
    // out; let that navigation settle before looking for the link.
    await schedule.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
    await schedule.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await schedule.reload({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await schedule.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const link = schedule.locator(`a.apptLink[onclick*="appointment_no=${appointment.id}&"]`).first();
    await link.waitFor({ state: 'visible', timeout: 30000 });

    // A provider-filtered day must retain encounter/bill links, and entering
    // week view explicitly must survive the previous/next navigation controls.
    assert(await schedule.locator(`a.encounterBtn[onclick*=",${appointment.id});"]`).count(), 'provider-filtered day lost the encounter link');
    assert(await schedule.locator(`a[onclick*="/billing?"][onclick*="appointment_no=${appointment.id}&"]`).count(), 'provider-filtered day lost the billing link');
    await schedule.locator(`[onclick*="goWeekView"][onclick*="'${appointment.providerNo}'"]`).first().click();
    await schedule.waitForURL(/weekView=true/);
    assert(await schedule.locator('select[name="provider_select"]').inputValue() === appointment.providerNo,
      'week view changed the selected provider');
    for (const icon of ['fa-forward-step', 'fa-backward-step']) {
      await schedule.locator(`a.redArrow:has(.${icon})`).click();
      await schedule.waitForLoadState('domcontentloaded');
      const weekUrl = new URL(schedule.url());
      assert(weekUrl.searchParams.get('weekView') === 'true'
        && weekUrl.searchParams.get('provider_no') === appointment.providerNo, 'week navigation lost view/provider context');
    }

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`PASS quick search found demographic ${demographicNo} and booked appointment ${appointment.id} on ${appointment.date} ${appointment.startTime} with provider ${appointment.providerNo}`);
  } catch (error) {
    console.error(`FAIL schedule quick-search appointment check: ${error.stack || error.message}`);
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
