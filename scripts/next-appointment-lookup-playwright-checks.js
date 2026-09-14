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
 * Browser check for the patient search's "next appointment" column (issue #2651).
 *
 * AppointmentUtil.getNextAppointment() guarded its input backwards: a real
 * demographic number returned the "(none)" sentinel before the database was ever
 * asked, and the formatted value came from a variable that was still null. The
 * only caller is SearchDemographicAutoComplete2Action, which publishes the value
 * as `nextAppointment` (and the `nextAppt` alias) on /demographic/SearchDemographic,
 * so a unit test alone cannot show the wired path answering through the packaged
 * front door. This check drives it end to end:
 *
 *   1. logs in and types the patient's name into the schedule's quick-search widget,
 *      reading the JSON the widget itself receives;
 *   2. asserts the value matches the patient's real next appointment as the DAO
 *      defines it (earliest uncancelled appointment from now on), which on a
 *      patient who has none is the "(none)" sentinel;
 *   3. seeds one appointment for tomorrow and asserts the column now reports that
 *      date rather than the sentinel -- the assertion that fails on the bug;
 *   4. deletes the seeded appointment and asserts the column returns to its
 *      original value.
 *
 * PREREQUISITE: `workflow_enhance` must be true in the install's carlos.properties
 * (it ships false), then `carlos-ctl restart`. The field is rendered only under that
 * property, and this check fails with that instruction rather than passing vacuously.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: NEXT_APPT_DEMOGRAPHIC_NO (1), NEXT_APPT_PROVIDER_NO (999998).
 *
 * No patient identifier or name is printed: the search keyword is read from the
 * database and used only inside the request.
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  assert,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  login,
  validateBaseUrl,
  validateMysqlHost,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  resetPassword: process.env.RESET_PASSWORD || '',
};
const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || '127.0.0.1');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
const demographicNo = process.env.NEXT_APPT_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.NEXT_APPT_PROVIDER_NO || '999998';
assert(/^\d+$/.test(demographicNo), 'NEXT_APPT_DEMOGRAPHIC_NO must be numeric');
assert(/^\d+$/.test(providerNo), 'NEXT_APPT_PROVIDER_NO must be numeric');

// The sentinel AppointmentUtil renders when there is no next appointment to show.
const NONE = '(none)';
const stamp = `PW_NEXT_APPT_${Date.now()}`;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'next-appointment-'));
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

// The same selection OscarAppointmentDaoImpl.findNextAppointment() makes: the
// earliest uncancelled appointment that has not started yet. Asserting against it
// rather than against the seeded date alone keeps the check honest on a dataset
// where the patient already has appointments.
function expectedNextAppointment() {
  const date = sql(
    "SELECT IFNULL(DATE_FORMAT(MIN(appointment_date), '%Y-%m-%d'), '') FROM appointment"
    + ` WHERE demographic_no=${Number(demographicNo)} AND status NOT LIKE '%C%'`
    + ' AND (appointment_date > CURDATE() OR (appointment_date = CURDATE() AND start_time >= CURTIME()))');
  return date || NONE;
}

function seedAppointment() {
  sql('INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,'
    + ' program_id, notes, reason, location, resources, type, style, billing, status, createdatetime,'
    + ' updatedatetime, creator, remarks, urgency)'
    + ` SELECT '${escapeSql(providerNo)}', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00:00', '09:15:00',`
    + ` CONCAT(last_name, ',', first_name), ${Number(demographicNo)}, 0, '${escapeSql(stamp)}',`
    + ` '${escapeSql(stamp)}', '', '', '', '', '', 't', NOW(), NOW(), '${escapeSql(providerNo)}', '', ''`
    + ` FROM demographic WHERE demographic_no=${Number(demographicNo)}`);
  const appointmentNo = sql(`SELECT appointment_no FROM appointment WHERE notes='${escapeSql(stamp)}' ORDER BY appointment_no DESC LIMIT 1`);
  assert(/^\d+$/.test(appointmentNo),
    'could not seed the fixture appointment -- does the configured test patient exist?');
  return appointmentNo;
}

let cleanupDone = false;
// Runs from the finally block or a signal handler: a fixture left behind is a
// failure of this check even when every assertion passed.
function runCleanup() {
  if (cleanupDone || !mysqlDefaults) {
    return;
  }
  cleanupDone = true;
  try {
    sql(`DELETE FROM appointment WHERE notes='${escapeSql(stamp)}'`);
  } catch (cleanupError) {
    console.error(`FAIL cleanup failed to remove the seeded appointment: ${cleanupError.message}`);
    process.exitCode = 1;
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

// Drives the schedule's quick-search widget the way a user does and reads the JSON
// it receives. The next-appointment value is not rendered in the dropdown -- no page
// renders it today -- so the widget's own response is the surface to assert on, and
// typing into the widget is what makes the application produce it.
async function searchRow(page, lastName) {
  // A trailing comma explicitly selects surname search. Without it, the widget
  // can interpret a synthetic surname such as Pr3668 as a health-card number.
  const searchTerm = `${lastName},`;
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 });
  await assertNotErrorPage(page, 'schedule');
  const quickSearch = page.locator('#quickSearch');
  await quickSearch.waitFor({ state: 'visible', timeout: 30000 });
  await quickSearch.click();
  // The widget fires a request per keystroke and aborts the previous one, so wait for
  // the response to the request carrying the WHOLE name rather than to a prefix of it.
  const [response] = await Promise.all([
    page.waitForResponse((candidate) => candidate.request().method() === 'POST'
      && new URL(candidate.url()).pathname.endsWith('/demographic/SearchDemographic')
      && new URLSearchParams(candidate.request().postData() || '').get('term') === searchTerm, { timeout: 30000 }),
    page.keyboard.type(searchTerm, { delay: 40 }),
  ]);
  assert(response.status() === 200, `quick search answered HTTP ${response.status()}`);
  const contentType = (response.headers()['content-type'] || '');
  assert(/application\/json/i.test(contentType),
    `quick search answered ${contentType || 'no content type'} instead of JSON`);
  let results;
  try {
    results = await response.json();
  } catch (parseError) {
    throw new Error(`quick search did not return JSON: ${parseError.message}`);
  }
  assert(Array.isArray(results), 'quick search did not return a result array');
  // The dropdown rendering from the same payload is what proves the widget consumed it.
  await page.locator('#quickSearchDropdown .qs-result-row').first().waitFor({ state: 'visible', timeout: 15000 });
  const row = results.find((entry) => String(entry.demographicNo) === String(demographicNo));
  assert(row, `quick search returned ${results.length} rows without the configured test patient`);
  return row;
}

(async () => {
  const recorder = createRecorder();
  let browser = null;
  try {
    initMysqlDefaults();
    const keyword = sql(`SELECT last_name FROM demographic WHERE demographic_no=${Number(demographicNo)}`);
    assert(keyword, 'the configured test patient does not exist in the configured database');

    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const page = await login(context, config, recorder);

    const before = await searchRow(page, keyword);
    assert(Object.prototype.hasOwnProperty.call(before, 'nextAppointment'),
      'the search result carries no nextAppointment field: set workflow_enhance=true in the'
      + " install's carlos.properties and restart (carlos-ctl restart) before running this check");
    const expectedBefore = expectedNextAppointment();
    assert(before.nextAppointment === expectedBefore,
      `next appointment before seeding was ${before.nextAppointment}, expected ${expectedBefore}`);
    assert(before.nextAppt === before.nextAppointment,
      `the nextAppt alias (${before.nextAppt}) disagrees with nextAppointment (${before.nextAppointment})`);

    seedAppointment();
    const after = await searchRow(page, keyword);
    const expectedAfter = expectedNextAppointment();
    assert(expectedAfter !== NONE, 'the seeded appointment is not the next one the DAO would select');
    assert(after.nextAppointment !== NONE,
      `next appointment reported ${NONE} for a patient with an appointment tomorrow -- the issue #2651 guard is inverted again`);
    assert(after.nextAppointment === expectedAfter,
      `next appointment reported ${after.nextAppointment}, expected ${expectedAfter}`);
    assert(after.nextAppt === after.nextAppointment,
      `the nextAppt alias (${after.nextAppt}) disagrees with nextAppointment (${after.nextAppointment})`);

    runCleanup();
    const restored = await searchRow(page, keyword);
    assert(restored.nextAppointment === expectedBefore,
      `next appointment after removing the fixture was ${restored.nextAppointment}, expected ${expectedBefore}`);

    await page.close();
    await context.close();
    console.log(`PASS next appointment lookup reported ${expectedAfter} for the seeded appointment and ${expectedBefore} without it`);
  } catch (error) {
    console.error('FAIL next appointment lookup Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    runCleanup();
    cleanupMysqlDefaults();
    if (browser) {
      await browser.close();
    }
  }
})();
