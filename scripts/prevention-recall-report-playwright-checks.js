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
 * screening type against a real as-of date — not merely that the page loads. It
 * reads only; it records no preventions and writes nothing, so it needs no cleanup.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:prevention-recall-report-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   PREVENTION_REPORT_TYPE=Flu   screening type to run (Mammogram, PAP, FOBT,
 *                                Flu, ChildImmunizations)
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

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
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};
// Flu is the default because every province's schedule carries it, so the option
// is present on any deployment without depending on local customisation.
const reportType = process.env.PREVENTION_REPORT_TYPE || 'Flu';
assert(['Mammogram', 'PAP', 'FOBT', 'Flu', 'ChildImmunizations'].includes(reportType),
  `PREVENTION_REPORT_TYPE must be one of the screening types the report offers, got ${reportType}`);

const recorder = createRecorder();
const passed = [];

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
  const preventionSelect = report.locator('select#prevention');
  await preventionSelect.waitFor({ state: 'visible', timeout: 30000 });
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
  const text = await assertNotErrorPage(report, `prevention recall report for ${reportType}`);

  // The submitted report must come back on the report page with the chosen type
  // still selected: a run that silently reset the form is a run whose numbers
  // belong to a different screening type than the one on screen.
  const selectedAfter = await report.locator('select#prevention').inputValue();
  assert(selectedAfter === reportType,
    `after running, the report showed ${selectedAfter} selected instead of ${reportType}`);
  return { text, asOfValue };
}

(async () => {
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  try {
    await login(context, config, recorder);

    const reportIndex = await openReportIndexFromSchedule(context);
    pass('the schedule Report link opens the report index');

    const report = await openPreventionReport(context, reportIndex);
    pass('the report index Preventions link opens the prevention recall report');

    const { asOfValue } = await runReport(report);
    pass(`the recall report runs for ${reportType} as of ${asOfValue} and keeps the chosen type selected`);

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
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
})();
