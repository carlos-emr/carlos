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
 * Every other check drives success paths, so a filter that had stopped
 * sanitizing entirely would have kept the suite green. This one deliberately
 * provokes two real 500s and asserts the client receives the generic
 * "Reference ID" page instead of whatever the failing component produced.
 *
 * What it pins:
 *
 *   1. An exception that escapes the WHOLE filter chain is answered with the
 *      sanitized page, not a container error page and not a stack trace.
 *      /form/createcustomedpdf is a plain servlet, excluded from Struts via
 *      struts.action.excludePattern, so nothing downstream converts its
 *      NullPointerException into sendError() first -- which is what makes it
 *      one of the few routes that reaches the filter's uncaught-exception
 *      branch at all.
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
 * before any write -- assertion 1 throws before the servlet touches a file, DB
 * or audit trail. Assertion 2's resource records one OscarLog audit row, which
 * is append-only.
 *
 * EXPECTED LOG NOISE: this check makes the server log real errors on purpose.
 * "Uncaught exception escaped filter chain" and "Sanitizing ... error response
 * body" ERROR lines in `journalctl -u carlos-emr` during this check are the
 * check working, not a failure.
 */

const { chromium } = require('playwright');
const {
  assert,
  assertNoPageErrors,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
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

(async () => {
  if (config.baseUrl.protocol !== 'https:') {
    console.warn(
      'WARN: BASE_URL is not HTTPS. This check also establishes that the packaged front door '
      + 'passes the sanitized error body through unmodified, so run it against :443.',
    );
  }
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const recorder = createRecorder();
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1400, height: 900 } });
    const loginPage = await login(context, config, recorder);
    await loginPage.close();

    const page = await context.newPage();
    wirePage(page, 'error-sanitization', recorder);

    // page.request shares the context's session cookies, so these are authenticated
    // requests. It is also NOT a browser navigation, so these deliberate 500s never
    // reach the recorder's badResponses -- which is why the cleanliness guard other
    // checks apply would not fight this one.

    // --- 1: an exception escaping the entire chain -------------------------------
    //
    // useSC=true with no scAddress makes FrmCustomedPDFServlet.parseSCAddress call
    // split() on null. It is thrown from the try-with-resources resource expression,
    // whose catches cover only DocumentException/FileNotFoundException, so it unwinds
    // past every filter to ResponseSanitizationFilter's own catch.
    const escapedPath = '/form/createcustomedpdf?useSC=true';
    const escaped = await page.request.get(`${config.baseUrl.href}${escapedPath}`, {
      failOnStatusCode: false,
      maxRedirects: 0,
    });
    const escapedBody = await escaped.text();

    // Negative control, and the most valuable assertion here: errorpage.jsp means the
    // exception was caught and converted to sendError() somewhere downstream, so the
    // filter's uncaught-exception branch never ran and a green result would be hollow.
    assert(
      !escapedBody.includes('CARLOS Error:'),
      `GET ${escapedPath} returned errorpage.jsp rather than the sanitized page, so the `
        + 'exception did not escape the chain and this check is no longer exercising the '
        + "filter's uncaught-exception branch. Re-point it at a route that still throws "
        + '(see the header comment for the fallback trigger).',
    );
    assertSanitizedErrorPage(
      `GET ${escapedPath}`,
      escaped.status(),
      escapedBody,
      escaped.headers()['content-type'],
    );

    // --- 2: a /ws 5xx body is replaced even with no stack trace -------------------
    //
    // /ws/rs is session-authenticated, and WebServiceSessionInvalidatingFilter
    // deliberately skips /ws/rs/, so this does not destroy the browser session.
    const wsPath = '/ws/rs/schedule/fetchProvidersApptsCount/not-a-date/not-a-date';
    const ws = await page.request.get(`${config.baseUrl.href}${wsPath}`, {
      failOnStatusCode: false,
      maxRedirects: 0,
    });
    const wsBody = await ws.text();

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
    await page.close();

    // Safe to keep: this inspects uncaught JavaScript only, never HTTP status.
    assertNoPageErrors(recorder);

    await context.close();

    console.log(
      'PASS error sanitization: an exception escaping the filter chain and a /ws 5xx both come '
      + 'back as the generic Reference ID page, through the packaged front door, with no '
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
})();
