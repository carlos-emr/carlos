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
 * Browser regression check for opening an appointment by appointment_no alone.
 *
 * The day sheet appends demographic_no, provider_no, date and time to the edit link, but a
 * link that carries only appointment_no — a bookmark, the receipt window, a support link —
 * does not. editappointment.jsp parsed those absent parameters anyway and answered HTTP 500
 * with a NumberFormatException before rendering (#3729).
 *
 * The strongest assertion available here is equivalence: the bare link and the full
 * day-sheet link address the same appointment, so they must render the same form. Checking
 * only "not 500" would pass on a page that rendered an empty or wrong-patient form.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:edit-appointment-direct-link-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   APPOINTMENT_NO=1 APPOINTMENT_DEMOGRAPHIC_NO=1
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');
const {
  assert,
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
const appointmentNo = process.env.APPOINTMENT_NO || '1';
const demographicNo = process.env.APPOINTMENT_DEMOGRAPHIC_NO || '1';

const failures = [];

async function fetchEdit(page, query, label, expectedStatus) {
  const search = new URLSearchParams(query).toString();
  const response = await gotoApp(page, config.baseUrl, `/appointment/editappointment?${search}`);
  const status = response ? response.status() : 0;
  if (status !== expectedStatus) {
    failures.push(`${label}: expected HTTP ${expectedStatus}, got ${status}`);
    return null;
  }
  return status === 200 ? page.content() : '';
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    const page = await context.newPage();
    wirePage(page, 'edit-appointment', recorder);

    // The #3729 case: only appointment_no, as a bookmark or receipt-window link carries it.
    const bare = await fetchEdit(page, { appointment_no: appointmentNo }, 'bare appointment_no link', 200);

    // The day sheet's own link to the same appointment.
    const daySheet = await fetchEdit(page, {
      appointment_no: appointmentNo,
      demographic_no: demographicNo,
      dboperation: 'search',
    }, 'day-sheet link', 200);

    if (bare && daySheet && bare !== daySheet) {
      failures.push('bare appointment_no link rendered a different form than the day-sheet link for the same appointment');
    }
    if (bare && !/appointment_no"\s+value="/.test(bare)) {
      failures.push('bare appointment_no link did not render the edit form (no appointment_no field)');
    }

    // A malformed or stale link is a client error, not a server fault.
    await fetchEdit(page, { dboperation: 'search' }, 'no appointment_no', 400);
    await fetchEdit(page, { appointment_no: 'abc' }, 'non-numeric appointment_no', 400);
    await fetchEdit(page, { appointment_no: '999999999' }, 'unknown appointment_no', 404);

    await page.close();
    await context.close();

    assert(failures.length === 0, `edit appointment link checks failed:\n  - ${failures.join('\n  - ')}`);
    console.log(`PASS appointment ${appointmentNo} opens from a bare link identically to the day-sheet link, and malformed links are 400/404`);
  } catch (error) {
    console.error('FAIL edit appointment direct link Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
