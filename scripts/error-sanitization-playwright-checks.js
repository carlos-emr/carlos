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
 * Browser regression check for ResponseSanitizationFilter's ERROR REPLACEMENT --
 * the behaviour the filter exists for, and the one thing the rest of the suite
 * never exercised.
 *
 * This checks both a controlled client-input refusal and a real server error
 * through the packaged front door.
 *
 * What it pins:
 *
 *   1. Missing or malformed satellite-clinic addresses receive HTTP 400 with no
 *      exception details or reflected input. This route formerly supplied an
 *      uncaught NullPointerException fixture; issue #5533 fixes that defect.
 *      The filter's uncaught-exception branch remains covered by
 *      ResponseSanitizationFilterUnitTest, without depending on a live bug.
 *   2. A /ws 5xx has its body REPLACED even though the body carries no stack
 *      trace. That rule exists because a serialization failure mid-response
 *      leaves a clean, well-formed JSON prefix -- potentially patient
 *      demographics -- already written, which no stack-trace heuristic would
 *      ever catch. Here the discarded entity is the harmless string
 *      "Internal server error"; the point is that the substitution happened.
 *
 * WHAT THIS CHECK DELIBERATELY DOES NOT COVER, so nobody "fixes" the omission:
 *
 *   - The stack-trace-marker branch. On a stock install no component can put a
 *     Java stack trace in a response body: errorpage.jsp prints one only when
 *     DISPLAY_ERROR=true AND response.sanitization.enabled=false, so "trace in
 *     the body while the filter is enabled" is unconfigurable, and disabling
 *     the filter to produce a trace would prove nothing about the filter. That
 *     branch is unit-tested (ResponseSanitizationFilterUnitTest) and cannot be
 *     driven end-to-end without deploying a probe page into the served tree.
 *     Do not add one.
 *   - The committed-response guards (dropTaintedBodyIfCommitted,
 *     appendAfterCommit). Those need a premature commit from outside the
 *     wrapper, which pinning suspendWrappedResponseAfterForward="false"
 *     prevents. Also unit-only, also by design.
 *
 * RUN THIS THROUGH :443. Part of what is being established is that the
 * packaged front door delivers the sanitized body untouched. nginx sets no
 * proxy_intercept_errors/error_page today; an operator adding one would
 * silently replace this page -- and the correlation ID support depends on --
 * with nginx's own, and only a run through the front door would notice.
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
 * EXPECTED LOG NOISE: this check makes the server log real errors on purpose.
 * "Sanitizing ... error response body" ERROR lines for the /ws 5xx in
 * `journalctl -u carlos-emr` are expected. Satellite-address refusals must
 * produce no uncaught exceptions.
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

/**
 * Asserts a response carries the filter's generic replacement page.
 *
 * Deliberately checks the SHAPE of the sanitized page rather than which
 * exception produced it: the subject under test is the filter, so the check
 * stays valid if the underlying route starts failing for a different reason.
 */
function assertSanitizedErrorPage(label, status, body, contentType) {
  assert(
    status === 500,
    `${label}: expected HTTP 500, got ${status}. This route must reach the filter's error `
      + 'path for the rest of the assertions to mean anything.',
  );
  assert(
    REFERENCE_ID_PATTERN.test(body),
    `${label}: response carries no "Reference ID: <uuid>", so ResponseSanitizationFilter did `
      + `not replace the body. Body began: ${JSON.stringify(body.slice(0, 200))}`,
  );
  assert(
    body.includes('An error occurred'),
    `${label}: missing the sanitized page's heading. Got: ${JSON.stringify(body.slice(0, 200))}`,
  );
  assert(
    /text\/html/i.test(contentType || ''),
    `${label}: sanitized page should be text/html, got "${contentType}".`,
  );
  for (const marker of STACK_TRACE_MARKERS) {
    assert(
      !body.includes(marker),
      `${label}: the response leaked the stack-trace marker "${marker}" to the client. This is `
        + 'the exact disclosure ResponseSanitizationFilter exists to prevent.',
    );
  }
}

legacyHarness.runLegacyCheck('error-sanitization',async({cancellation})=>{
  if (config.baseUrl.protocol !== 'https:') {
    console.warn(
      'WARN: BASE_URL is not HTTPS. This check also establishes that the packaged front door '
      + 'passes the sanitized error body through unmodified, so run it against :443.',
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
    // requests. It is also NOT a browser navigation, so these deliberate 500s never
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
      const body = await response.text();
      cancellation?.throwIfCancelled();
      assert(response.status() === 400,
        `Invalid satellite clinic address: expected HTTP 400, got ${response.status()}`);
      assert(!response.headers()['content-disposition'], 'Invalid input started a PDF download');
      assert(!REFERENCE_ID_PATTERN.test(body), 'A controlled 400 reached the server-error sanitizer');
      assert(!body.includes('PRIVATE_INVALID_SATELLITE_ADDRESS'), 'The error page reflected the address');
      for (const marker of STACK_TRACE_MARKERS) {
        assert(!body.includes(marker), `Satellite-address refusal leaked a stack-trace marker: ${marker}`);
      }
      await response.dispose();
    }

    // --- 2: a /ws 5xx body is replaced even with no stack trace -------------------
    //
    // /ws/rs is session-authenticated, and WebServiceSessionInvalidatingFilter
    // deliberately skips /ws/rs/, so this does not destroy the browser session.
    const wsPath = '/ws/rs/schedule/fetchProvidersApptsCount/not-a-date/not-a-date';
    const ws = await page.request.get(`${config.baseUrl.href}${wsPath}`, {
      failOnStatusCode: false,
      maxRedirects: 0,
    });
    cancellation?.throwIfCancelled();
    const wsBody = await ws.text();
    cancellation?.throwIfCancelled();

    assert(
      ws.status() !== 401,
      `GET ${wsPath} returned 401, so the session did not reach the web-service surface and `
        + 'the 5xx replacement rule was never exercised.',
    );
    assertSanitizedErrorPage(
      `GET ${wsPath}`,
      ws.status(),
      wsBody,
      ws.headers()['content-type'],
    );
    assert(
      !wsBody.includes('Internal server error'),
      `GET ${wsPath}: the resource's own entity survived to the client, so the /ws 5xx rule `
        + 'did not replace the body. That rule is what stops a half-serialized response '
        + 'shipping the PHI prefix it had already written.',
    );

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
      'PASS error sanitization: invalid satellite addresses receive HTTP 400 and a /ws 5xx '
      + 'receives the generic Reference ID page, through the packaged front door, with no '
      + 'stack-trace markers and no underlying entity leaked',
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
