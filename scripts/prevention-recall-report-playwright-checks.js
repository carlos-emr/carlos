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
 * Browser check for the prevention RECALL REPORT — the screening tool a clinic
 * uses to find the patients who are due.
 *
 * prevention-brand-picker covers recording an immunization on one patient's chart.
 * This covers the other direction: asking the whole roster who is overdue.
 * `prevention/PreventionReport` was never driven, and its failure mode is quiet —
 * an empty or erroring report looks like "nobody is due", which is the one answer a
 * recall tool must never give wrongly.
 *
 * REACHED THE WAY A CLINIC REACHES IT: the Report link in the schedule's top nav,
 * then the Preventions report link on the report index, then the report's own "Run
 * Report" form. The report index link is the only UI entry to this route, so a
 * check that navigated to PreventionReport directly would not notice the link
 * disappearing from the menu.
 *
 * The check asserts the report is reachable, renders its form, and RUNS for a
 * screening type against a real as-of date — and that a patient who IS due comes
 * back in the results.
 *
 * WHY A FIXTURE IS NEEDED AT ALL: PreventionReport2Action returns the empty form
 * unchanged unless patientSet parses to a positive id (hasValidPatientSet), so a
 * check that only picks a screening type and a date never runs the query it is
 * meant to cover — the page comes back looking identical whether the report works
 * or not. The fixture is one saved demographic query (demographicQueryFavourites)
 * naming a single patient the report must classify, which is also what makes the
 * expected result deterministic instead of "whatever the demo roster happens to
 * hold". It is the only row this check writes, and the finally block removes it.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:prevention-recall-report-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=127.0.0.1 MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * PREVENTION_REPORT_TYPE is deliberately NOT configurable: the fixture below and the
 * expected classification are specific to the Flu recall rule.
 */

const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');
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
// Flu is used because every province's schedule carries it, so the option is present
// on any deployment without depending on local customisation.
const reportType = 'Flu';
// FluReport.isOfAge treats a patient as eligible when their DOB is on or before
// December 31 of the as-of year minus 65 years; with no Flu prevention recorded,
// such a patient is classified "No Info" -- the report's way of saying "due".
const expectedState = 'No Info';

const stamp = `PW_RECALL_${Date.now()}`;
const recorder = createRecorder();
const passed = [];
let fixtureQueryId = null;
let fixtureDemographicNo = null;

let mysqlDefaults = null;
// A MySQL option file interprets backslash escapes, so a password containing \ or "
// reaches the client mangled unless it is quoted and escaped here.
function encodeOptionFileValue(value) {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'prevention-recall-'));
  // A throw between mkdtemp and the assignment below would leave the directory --
  // and possibly a written password file -- with nothing tracking it for cleanup.
  try {
    const file = path.join(dir, 'mysql-defaults.cnf');
    fs.writeFileSync(file, `[client]\npassword=${encodeOptionFileValue(mysqlPassword)}\n`, { mode: 0o600 });
    mysqlDefaults = { dir, file };
  } catch (error) {
    fs.rmSync(dir, { recursive: true, force: true });
    throw error;
  }
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

/**
 * Picks a patient the Flu recall must report as due and wraps them in a saved query.
 *
 * The patient has to be 65 or over as of December 31 of this year and carry no live
 * Flu prevention, which is exactly the "No Info" branch of FluReport. Selecting the
 * patient from the deployment's own roster rather than inventing one keeps the check
 * free of demographic writes.
 */
function seedFixture() {
  const asOfYear = new Date().getFullYear();
  const demo = sql(
    'SELECT demographic_no FROM demographic'
    + ` WHERE year_of_birth REGEXP '^[0-9]{4}$' AND CAST(year_of_birth AS UNSIGNED) <= ${asOfYear - 65}`
    + " AND patient_status = 'AC'"
    + ' AND demographic_no NOT IN ('
    + "   SELECT demographic_no FROM preventions WHERE prevention_type = 'Flu' AND IFNULL(deleted, '0') <> '1'"
    + ' ) ORDER BY demographic_no LIMIT 1'
  );
  assert(/^\d+$/.test(demo),
    'this deployment has no active patient aged 65+ without a Flu prevention, so the recall'
    + ' report has nothing it must report as due; seed one before running this check');
  fixtureDemographicNo = demo;

  // `selects` is not optional decoration: RptDemographicQueryBuilder returns an empty
  // result when the saved query names no columns, so the report would run and list
  // nobody. The shape is the one RptDemographicQuery2Saver writes, with no whitespace
  // between the elements because RptDemographicQueryLoader casts every child node to
  // an Element.
  const selectsXml = '<root><item value="demographic_no"/></root>';
  sql(
    'INSERT INTO demographicQueryFavourites (queryName, archived, demoIds, selects)'
    + ` VALUES ('${escapeSql(stamp)}', '1', '${escapeSql(demo)}', '${escapeSql(selectsXml)}')`
  );
  fixtureQueryId = sql(`SELECT favId FROM demographicQueryFavourites WHERE queryName='${escapeSql(stamp)}'`);
  assert(/^\d+$/.test(fixtureQueryId), 'the saved-query fixture did not reach demographicQueryFavourites');
}

function cleanupFixture() {
  if (fixtureQueryId === null) {
    return;
  }
  try {
    sql(`DELETE FROM demographicQueryFavourites WHERE favId=${Number(fixtureQueryId)}`);
  } catch (error) {
    console.error(`WARN could not remove the saved-query fixture ${fixtureQueryId}: ${error.message}`);
  }
  fixtureQueryId = null;
}

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

/** Opens the report index from the Report link in the schedule's top nav. */
async function openReportIndexFromSchedule(context) {
  const page = await context.newPage();
  wirePage(page, 'schedule', recorder);
  await gotoApp(page, config.baseUrl,
    '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1&scheduleNav=1');
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'schedule day sheet');

  const reportLink = page.locator("a[onclick*='/report/ViewReportindex'], a[href*='/report/ViewReportindex']").first();
  assert(await reportLink.count() > 0, 'the schedule top nav rendered no Report link');
  await Promise.all([
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    reportLink.click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'report index');
  return page;
}

/**
 * Follows the Preventions report link off the report index.
 *
 * The link carries target="_blank", so the report opens in a new page; both the
 * popup and a same-page navigation are accepted because which one happens depends
 * on how the index was opened.
 */
async function openPreventionReport(context, reportIndex) {
  const link = reportIndex.locator("a[href*='/prevention/PreventionReport']").first();
  assert(await link.count() > 0,
    'the report index rendered no Preventions report link; this route has no other UI entry');

  const popupPromise = context.waitForEvent('page', { timeout: 15000 }).catch(() => null);
  await link.click();
  const popup = await popupPromise;
  const report = popup || reportIndex;
  if (popup) {
    wirePage(popup, 'prevention-report', recorder);
  }
  await report.waitForLoadState('domcontentloaded', { timeout: 45000 });
  await report.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(report, 'prevention recall report');
  assert(/\/prevention\/PreventionReport/.test(report.url()),
    `the Preventions report link landed on ${report.url()}`);
  return report;
}

/**
 * Runs the report for the screening type through its own form.
 *
 * Running it is the point: rendering the form proves the route resolves, but only
 * a submitted report exercises the query that decides who is overdue.
 */
async function runReport(report) {
  // patientSet is what decides whether the report RUNS at all: without a positive id
  // PreventionReport2Action returns the untouched form, so the fixture query has to be
  // selected here or nothing below is exercised.
  const patientSetSelect = report.locator('select#patientSet');
  await patientSetSelect.waitFor({ state: 'visible', timeout: 30000 });
  const patientSets = await patientSetSelect.locator('option').evaluateAll((nodes) => nodes.map((n) => n.value));
  assert(patientSets.includes(String(fixtureQueryId)),
    `the report does not offer the saved query fixture ${fixtureQueryId}; it offers ${JSON.stringify(patientSets)}`);
  await patientSetSelect.selectOption(String(fixtureQueryId));

  const preventionSelect = report.locator('select#prevention');
  const options = await preventionSelect.locator('option').evaluateAll((nodes) => nodes.map((n) => n.value));
  assert(options.includes(reportType),
    `the report does not offer ${reportType}; it offers ${JSON.stringify(options)}`);
  await preventionSelect.selectOption(reportType);

  const asOf = report.locator('#asofDate');
  assert(await asOf.count() > 0, 'the report rendered no as-of date field');
  const today = new Date();
  const asOfValue = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  await asOf.fill(asOfValue);

  const [response] = await Promise.all([
    report.waitForResponse((r) => /\/prevention\/PreventionReport/.test(r.url())
      && r.request().method() === 'GET', { timeout: 45000 }),
    report.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    report.locator('input[type="submit"]').first().click(),
  ]);
  assert(response.status() < 400, `prevention/PreventionReport returned HTTP ${response.status()}`);
  await report.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(report, `prevention recall report for ${reportType}`);

  // The submitted report must come back on the report page with the chosen type
  // still selected: a run that silently reset the form is a run whose numbers
  // belong to a different screening type than the one on screen.
  const selectedAfter = await report.locator('select#prevention').inputValue();
  assert(selectedAfter === reportType,
    `after running, the report showed ${selectedAfter} selected instead of ${reportType}`);
  assert(await report.locator('#preventionTable').count() > 0,
    'the report answered without a result table, so the query never ran');
  return { asOfValue };
}

/**
 * Asserts the fixture patient is listed, and listed as due.
 *
 * "The report rendered" is not the assertion that matters. A recall tool that comes
 * back empty, or that reports a 65-year-old with no flu shot on file as anything
 * other than due, is broken in exactly the way that looks like working software.
 */
async function assertFixtureIsReportedDue(report) {
  const row = report.locator('#preventionTable tbody tr')
    .filter({ has: report.locator(`a[onclick*="demographic_no=${fixtureDemographicNo}"]`) })
    .first();
  assert(await row.count() > 0,
    `the ${reportType} recall report did not list patient ${fixtureDemographicNo},`
    + ' who is over 65 with no flu shot on file and must be reported as due');
  const state = (await row.locator('span.badge').first().innerText()).trim();
  assert(state === expectedState,
    `the ${reportType} recall report classified patient ${fixtureDemographicNo} as "${state}",`
    + ` expected "${expectedState}"`);
}

(async () => {
  initMysqlDefaults();
  let browser = null;
  let context = null;
  // Setup runs inside the try so a failure before the browser opens still reaches the
  // finally: initMysqlDefaults has already written a 0600 file holding the database
  // password, and the fixture row must not outlive a failed run either.
  try {
    seedFixture();

    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    context = await browser.newContext({ ignoreHTTPSErrors: true });

    await login(context, config, recorder);

    const reportIndex = await openReportIndexFromSchedule(context);
    pass('the schedule Report link opens the report index');

    const report = await openPreventionReport(context, reportIndex);
    pass('the report index Preventions link opens the prevention recall report');

    const { asOfValue } = await runReport(report);
    pass(`the recall report runs for ${reportType} as of ${asOfValue} and keeps the chosen type selected`);

    await assertFixtureIsReportedDue(report);
    pass(`patient ${fixtureDemographicNo} is reported "${expectedState}" for ${reportType}, so the recall query answers`);

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0,
      `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0,
      `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`\nPASS prevention recall report: ${passed.length} checks, 0 failures`);
  } catch (error) {
    console.error(`FAIL prevention recall report: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    if (context) await context.close().catch(() => {});
    if (browser) await browser.close().catch(() => {});
    try {
      cleanupFixture();
    } finally {
      cleanupMysqlDefaults();
    }
  }
})();
