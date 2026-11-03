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
 * Browser regression checks for controlled client-input refusals through the
 * packaged front door. Missing or malformed satellite-clinic addresses return
 * HTTP 400 without reflected input or exception details (#5533). Malformed
 * schedule dates preserve the manager's HTTP 400 and public message (#5545).
 *
 * Both routes previously supplied accidental server-error fixtures. Actual
 * uncaught exceptions, /ws 5xx bodies, partial serialization and committed
 * responses remain covered by ResponseSanitizationFilterUnitTest. The schedule
 * endpoint's unexpected-failure response is covered by ScheduleServiceEndpointTest.
 *
 * RUN THIS THROUGH :443 to verify that the packaged front door preserves the
 * application's client-error response and message.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN
 * Optional: CHROME_PATH, ERROR_SANITIZATION_SCREENSHOT_DIR (default /tmp).
 *
 * FIXTURE SAFETY: creates and deletes nothing. Both routes are GETs that fail
 * before any write -- assertion 1 refuses input before touching a file, DB
 * or audit trail. Assertion 2's resource records one OscarLog audit row, which
 * is append-only.
 *
 * Both controlled refusals must produce no server-error or uncaught-exception
 * signatures. Their former log-audit allowances have been removed.
 */


const legacyHarness=require('./lib/legacy-check');
const { chromium } = require('playwright');
const { shouldIgnoreHttpsErrors, assertStrictPage, wireStrictPage } = require('./lib/playwright-harness');
const {
  assert,
  assertNoPageErrors,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  login,
  screenshot,
  validateBaseUrl,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.ERROR_SANITIZATION_SCREENSHOT_DIR || '/tmp',
};

// ResponseSanitizationFilter.STACK_TRACE_MARKERS. Kept verbatim so this check
// fails if a sanitized page ever starts carrying one of them.
const STACK_TRACE_MARKERS = [
  'Caused by:',
  'java.lang.',
  'io.github.carlos_emr.',
  'jakarta.servlet.',
  'org.apache.',
  'org.springframework.',
  'org.hibernate.',
];

const REFERENCE_ID_PATTERN =
  /Reference ID:\s+[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;

legacyHarness.runLegacyCheck('error-sanitization',async({cancellation})=>{
  if (config.baseUrl.protocol !== 'https:') {
    console.warn(
      'WARN: BASE_URL is not HTTPS. Run against :443 to verify that the packaged front door '
      + 'preserves the application response.',
    );
  }
  const browser = await chromium.launch({...(getLaunchOptions(config.chromePath)),...legacyHarness.NO_PLAYWRIGHT_SIGNAL_HANDLING});
  const recorder = createRecorder();
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: shouldIgnoreHttpsErrors(), viewport: { width: 1400, height: 900 } });
    cancellation?.throwIfCancelled();
    context.on('page', page => wireStrictPage(page, 'error-sanitization-page', recorder, { baseline: [] }));
    const loginPage = await login(context, config, recorder);
    cancellation?.throwIfCancelled();
    await loginPage.close();
    cancellation?.throwIfCancelled();

    const page = await context.newPage();
    cancellation?.throwIfCancelled();
    wireStrictPage(page, 'error-sanitization', recorder, { baseline: [] });

    // page.request shares the context's session cookies, so these are authenticated
    // requests. It is also NOT a browser navigation, so these deliberate 400s never
    // reach the recorder's badResponses -- which is why the cleanliness guard other
    // checks apply would not fight this one.

    // --- 1: satellite-clinic input errors are deliberate 400 responses -----------
    const satelliteCases = [
      { useSC: 'true' },
      { useSC: 'true', scAddress: '' },
      { useSC: 'TrUe', scAddress: 'PRIVATE_INVALID_SATELLITE_ADDRESS' },
      { useSC: 'true', scAddress: '<b>Dr A</b><br>Clinic<br>Street' },
    ];
    for (const parameters of satelliteCases) {
      const target = `${config.baseUrl.href}/form/createcustomedpdf?${new URLSearchParams(parameters)}`;
      const response = await page.request.get(target, { failOnStatusCode: false, maxRedirects: 0 });
      cancellation?.throwIfCancelled();
      try {
        const body = await response.text();
        cancellation?.throwIfCancelled();
        assert(response.status() === 400,
          `Invalid satellite clinic address: expected HTTP 400, got ${response.status()}`);
        assert(!response.headers()['content-disposition'], 'Invalid input started a PDF download');
        assert(!/application\/pdf/i.test(response.headers()['content-type'] || ''), 'Invalid input returned PDF headers');
        assert(!body.startsWith('%PDF'), 'Invalid input returned PDF bytes');
        assert(!REFERENCE_ID_PATTERN.test(body), 'A controlled 400 reached the server-error sanitizer');
        assert(!body.includes('PRIVATE_INVALID_SATELLITE_ADDRESS'), 'The error page reflected the address');
        for (const marker of STACK_TRACE_MARKERS) {
          assert(!body.includes(marker), `Satellite-address refusal leaked a stack-trace marker: ${marker}`);
        }
      } finally {
        await response.dispose();
      }
    }

    // --- 2: schedule date validation preserves its controlled 400 and message ---
    //
    // /ws/rs is session-authenticated, and WebServiceSessionInvalidatingFilter
    // deliberately skips /ws/rs/, so this does not destroy the browser session.
    const wsPath = '/ws/rs/schedule/fetchProvidersApptsCount/not-a-date/not-a-date';
    const ws = await page.request.get(`${config.baseUrl.href}${wsPath}`, {
      failOnStatusCode: false,
      maxRedirects: 0,
    });
    cancellation?.throwIfCancelled();
    try {
      const wsBody = await ws.text();
      cancellation?.throwIfCancelled();
      assert(ws.status() === 400, `GET ${wsPath}: expected HTTP 400, got ${ws.status()}`);
      assert(wsBody === 'Path parameter has the wrong format',
        `GET ${wsPath}: the public validation message was replaced`);
    } finally {
      await ws.dispose();
    }

    await screenshot(page, config.screenshotDir, 'error-sanitization');
    cancellation?.throwIfCancelled();
    await page.close();
    cancellation?.throwIfCancelled();

    // The intentional failures above use APIRequestContext, which emits no page
    // response/console events. Every observed browser failure remains fatal.
    assertNoPageErrors(recorder);
    assertStrictPage(recorder);

    await context.close();
    cancellation?.throwIfCancelled();

    console.log(
      'PASS error sanitization: invalid satellite addresses and schedule dates receive HTTP 400 '
      + 'through the packaged front door, with no stack-trace markers or reflected input; '
      + 'the schedule validation message is preserved',
    );
  } catch (error) {
    console.error('FAIL error sanitization Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
});
