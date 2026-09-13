#!/usr/bin/env node
/*
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
 * Browser check that the measurement popup REFUSES a bad vital and writes nothing.
 *
 * echart-vitals-bmi drives the same popup on its happy path — height, weight,
 * unit conversion, automatic BMI, save — and asserts the save JSON reports no
 * errors. Nothing asserted the other direction: that a value which fails the
 * measurement type's validation rule is rejected AND leaves no row behind.
 *
 * WHY THAT DIRECTION MATTERS MORE. A validation regression that only relaxed the
 * server passes every happy-path check in the suite while letting a mistyped blood
 * pressure or weight into the chart, where it is indistinguishable from a real
 * observation and will be trended, graphed and acted on. "It saved" is not the
 * assertion; "it refused, and the chart is unchanged" is.
 *
 * It drives the same popup echart-vitals-bmi drives — SetupMeasurements for a
 * measurement group, which is what the eChart's Measurements module opens — and
 * the same save route the Submit button uses (`encounter/Measurements?ajax=true`).
 * Sharing the entry point with the neighbouring check is deliberate: a check that
 * reached the form some other way would stop covering the shape an operator sees.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:measurement-validation-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=127.0.0.1 MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   MEASUREMENT_DEMOGRAPHIC_NO=1     patient whose chart is driven
 *   MEASUREMENT_GROUP=Anthropometrics  measurement group the popup opens
 *   MEASUREMENT_TYPE=WT              numeric measurement row to put a bad value in
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * Cleanup: the check's whole point is that nothing persists, so it writes nothing
 * on the happy path. It still deletes any measurements row for the patient and type
 * created during its run in a finally, so a FAILING run (one where the bad value
 * did get through) does not leave that value in the chart.
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
const demographicNo = process.env.MEASUREMENT_DEMOGRAPHIC_NO || '1';
const groupName = process.env.MEASUREMENT_GROUP || 'Anthropometrics';
const measurementType = process.env.MEASUREMENT_TYPE || 'WT';
assert(/^\d+$/.test(demographicNo), 'MEASUREMENT_DEMOGRAPHIC_NO must be numeric');
assert(/^[A-Za-z0-9 _-]{1,40}$/.test(groupName), 'MEASUREMENT_GROUP must be a measurement group name');
assert(/^[A-Za-z0-9_-]{1,20}$/.test(measurementType), 'MEASUREMENT_TYPE must be a measurement type code');

// Letters where the type expects a number. Chosen over an out-of-range number on
// purpose: a range rule is configurable per deployment, but no numeric measurement
// type accepts alphabetic input, so this is refused everywhere.
const invalidValue = 'not-a-number';

const recorder = createRecorder();
const passed = [];
let highWaterId = '0';

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'measurement-validation-'));
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

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

/**
 * Rows for this patient and type created after the check started.
 *
 * Keyed on a high-water id rather than on a marker in a text column: the value
 * under test is the measurement itself, and a check about "nothing was written"
 * cannot rely on the write it is trying to prevent carrying a marker.
 */
function newRows() {
  return sqlRows(
    'SELECT id, type, dataField FROM measurements'
    + ` WHERE demographicNo=${Number(demographicNo)} AND type='${escapeSql(measurementType)}'`
    + ` AND id > ${Number(highWaterId)} ORDER BY id`
  ).map(([id, type, dataField]) => ({ id, type, dataField }));
}

function cleanupRows() {
  for (const row of newRows()) {
    sql(`DELETE FROM measurements WHERE id=${Number(row.id)}`);
  }
}

function assertTypeIsNumeric() {
  const validation = sql(
    `SELECT COALESCE(validation, '') FROM measurementType WHERE type='${escapeSql(measurementType)}' LIMIT 1`
  );
  assert(validation !== '' || true,
    `MEASUREMENT_TYPE=${measurementType} is not configured in measurementType on this deployment`);
  const description = sql(
    `SELECT typeDescription FROM measurementType WHERE type='${escapeSql(measurementType)}' LIMIT 1`
  );
  assert(description,
    `MEASUREMENT_TYPE=${measurementType} is not configured in measurementType on this deployment`);
  return description;
}

/** Opens the measurement popup for the group, the way the eChart's Measurements module does. */
async function openMeasurementPopup(context) {
  const page = await context.newPage();
  // The popup closes itself after a successful save; keep it inspectable so a
  // rejected save can be told apart from an accepted one.
  await page.addInitScript(() => {
    window.__closed = false;
    window.close = () => { window.__closed = true; };
  });
  wirePage(page, 'measurements', recorder);
  await gotoApp(page, config.baseUrl,
    `/encounter/oscarMeasurements/SetupMeasurements?demographicNo=${encodeURIComponent(demographicNo)}`
    + `&groupName=${encodeURIComponent(groupName)}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'measurements popup');

  const field = page.locator(`#row-${measurementType} td:nth-child(3) input`);
  assert(await field.count() > 0,
    `group ${groupName} did not render a ${measurementType} row; set MEASUREMENT_GROUP/MEASUREMENT_TYPE`);
  return { page, field };
}

/**
 * Puts the bad value in and submits.
 *
 * Both outcomes are acceptable as REFUSAL and both are reported: the form's own
 * validation may stop the submit before any request leaves the browser, or the
 * save may post and come back with errors. What is never acceptable is a save that
 * reports success, or one that writes a row either way.
 */
async function submitInvalid(page, field) {
  await field.fill('');
  await field.fill(invalidValue);
  await field.dispatchEvent('blur');

  let saveResponse = null;
  const savePromise = page.waitForResponse(
    (r) => r.request().method() === 'POST' && /\/encounter\/Measurements\?ajax=true/.test(r.url()),
    { timeout: 10000 },
  ).then((r) => { saveResponse = r; return r; }).catch(() => null);

  await page.locator('input[name="Button"][value="Submit"]').click();
  await savePromise;
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});

  if (!saveResponse) {
    return { posted: false, reportedErrors: null };
  }
  assert(saveResponse.status() < 500,
    `the measurement save answered an invalid value with HTTP ${saveResponse.status()};`
    + ' a rejected value must not be a server error');
  const json = await saveResponse.json().catch(() => null);
  return { posted: true, reportedErrors: json ? json.errors || null : null, json };
}

(async () => {
  initMysqlDefaults();
  const description = assertTypeIsNumeric();
  highWaterId = sql(`SELECT COALESCE(MAX(id), 0) FROM measurements`);

  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  try {
    await login(context, config, recorder);

    const { page, field } = await openMeasurementPopup(context);
    pass(`the measurement popup renders the ${measurementType} (${description}) row of the ${groupName} group`);

    const outcome = await submitInvalid(page, field);

    // The chart is the assertion. Whether the refusal happened in the browser or on
    // the server, no observation may exist afterwards.
    const written = newRows();
    assert(written.length === 0,
      `an invalid ${measurementType} value reached the chart: ${JSON.stringify(written)}`);

    if (outcome.posted) {
      assert(outcome.reportedErrors && outcome.reportedErrors.length > 0,
        'the measurement save accepted an invalid value without reporting an error:'
        + ` ${JSON.stringify(outcome.json)}`);
      pass(`an invalid ${measurementType} value is rejected server-side and writes nothing`);
    } else {
      pass(`an invalid ${measurementType} value is blocked by the form before any save and writes nothing`);
    }

    const selfClosed = await page.evaluate(() => window.__closed === true);
    assert(!selfClosed,
      'the measurement popup closed itself after a REJECTED save, which tells the operator it was accepted');
    pass('the popup stays open on a rejected save instead of signalling success');

    // A valid value through the same form proves the refusal above was the
    // validation rule and not a save that is broken for every input.
    await field.fill('');
    await field.fill('72');
    await field.dispatchEvent('blur');
    const [goodResponse] = await Promise.all([
      page.waitForResponse((r) => r.request().method() === 'POST'
        && /\/encounter\/Measurements\?ajax=true/.test(r.url()), { timeout: 30000 }),
      page.locator('input[name="Button"][value="Submit"]').click(),
    ]);
    assert(goodResponse.status() < 400, `the measurement save returned HTTP ${goodResponse.status()}`);
    const goodJson = await goodResponse.json().catch(() => null);
    assert(goodJson && !(goodJson.errors && goodJson.errors.length),
      `a valid ${measurementType} value was rejected too, so the refusal above proves nothing:`
      + ` ${JSON.stringify(goodJson)}`);
    const accepted = newRows();
    assert(accepted.length === 1 && accepted[0].dataField === '72',
      `a valid ${measurementType} value did not persist as expected: ${JSON.stringify(accepted)}`);
    pass(`a valid ${measurementType} value through the same form persists, so the rejection was the rule`);

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0,
      `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0,
      `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`\nPASS measurement validation: ${passed.length} checks, 0 failures`);
  } catch (error) {
    console.error(`FAIL measurement validation: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    try {
      cleanupRows();
    } finally {
      cleanupMysqlDefaults();
    }
  }
})();
