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
 * Browser + direct-HTTP regression check for the failed-login page's resource
 * origin (WEB-INF/jsp/login/loginfailed.jsp).
 *
 * THE DEFECT THIS PINS. loginfailed.jsp used to emit
 *
 *     <base href="<%= request.getScheme() + "://" + request.getServerName()
 *                   + ":" + request.getServerPort()
 *                   + request.getContextPath() + "/" %>">
 *
 * request.getServerName()/getServerPort() are read from the Host header, which
 * is attacker-controlled. A single <base> element re-points EVERY relative URL
 * on the document, so a spoofed Host turned an app-local page into one that
 * resolved its relative references against a host of the attacker's choosing.
 * That matters more here than on a typical page for two reasons: this page is
 * reachable PRE-AUTHENTICATION (/loginfailed is a public gate action), and it
 * is the page a user is looking at when they are already about to retype a
 * username and password.
 *
 * WHY A BROWSER CHECK AND NOT ONLY THE JSP TEXT ASSERTIONS.
 * LoginJspMigrationRegressionTest greps the JSP source, which catches a
 * re-introduced literal but proves nothing about what is actually served. The
 * page is assembled by the container and passes through nginx and ModSecurity
 * in a packaged install; a <base> re-introduced from an include, a tag file, a
 * template, or a front-door rewrite would keep that source-level test green.
 * This check reads what a browser really receives.
 *
 * WHAT IT ASSERTS.
 *
 *   1. BYTE-IDENTICAL UNDER A SPOOFED HOST. The page is fetched twice over a
 *      raw socket -- once with the real Host, once with an attacker-controlled
 *      one -- and the two bodies must match exactly. This is the strongest
 *      available statement of the property under test: the response is a pure
 *      function of the request line, not of the Host header. With the old code
 *      present the two bodies differ in the <base href> and this fails. It also
 *      catches any FUTURE Host reflection anywhere in the document, not just a
 *      <base> element, which a substring assertion would miss.
 *   2. NO <base> ELEMENT AT ALL, in the parsed DOM (not the source text), and
 *      document.baseURI still equal to the page's own URL.
 *   3. ASSETS ANCHORED TO THE SERVLET CONTEXT. The favicon link and global.js
 *      script resolve to absolute URLs on the real origin under the app's
 *      context path. This is what the deleted <base> was (incorrectly) there to
 *      do, so it is the regression that a careless revert would cause.
 *   4. THE PAGE STILL WORKS. The errormsg is rendered and HTML-encoded, so a
 *      future "just delete the whole head" fix cannot pass by serving a blank
 *      or broken page. loginfailed.jsp's only dynamic output is errormsg, and
 *      it is reachable unauthenticated, so its encoding is worth pinning here.
 *
 * WHY RAW http/https AND NOT page.request FOR ASSERTION 1. Host is a forbidden
 * header for the browser's own fetch stack, and Playwright's APIRequestContext
 * gives no guarantee it is transmitted verbatim rather than rebuilt from the
 * URL. A raw socket is the only way to be sure the server actually saw the
 * spoofed value -- and if it were silently dropped, the check would be
 * comparing two identical requests and would pass vacuously.
 *
 * A FRONT DOOR THAT REJECTS THE SPOOFED HOST IS A PASS, and is reported as
 * such -- but narrowly. Only an nginx-served 400 or 421 counts: those are the
 * statuses nginx answers a refused Host with, and the nginx Server header is
 * what proves the refusal came from the front door rather than the application.
 * Any OTHER status difference, an application-generated 404 or 500 included, is
 * the application answering differently because of the Host header, which is
 * the defect class under test, so it is asserted on rather than excused. The
 * check does not fail the run when assertion 1's mechanism is legitimately
 * short-circuited, because assertions 2-4 still execute against the legitimate
 * Host and independently establish that no <base> is emitted.
 *
 * RUN THIS THROUGH :443 in a packaged install, per
 * docs/ui-tests/deb-install-validation.md. Going straight to Tomcat on
 * 127.0.0.1:18080 skips nginx and ModSecurity and so cannot see a front-door
 * rewrite re-introducing a <base>. The default BASE_URL is bare Tomcat, so the
 * run reports which layer it actually covered: no nginx Server header on the
 * response prints a WARNING, and EXPECT_FRONT_DOOR=true makes that a failure
 * instead. A standalone run must never be mistaken for front-door coverage.
 *
 * Env: BASE_URL (default http://127.0.0.1:8080/carlos). Optional CHROME_PATH,
 * LOGIN_FAILURE_SCREENSHOT_DIR (default /tmp), EXPECT_FRONT_DOOR (1/true/yes to
 * require the packaged front door).
 *
 * FIXTURE SAFETY: reads only. No login, no credentials, no DB access, no
 * writes -- /loginfailed is a public page and every request here is a GET.
 */

const http = require('node:http');
const https = require('node:https');
const { chromium } = require('playwright');
const {
  appUrl,
  assert,
  assertNoPageErrors,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  screenshotDir: process.env.LOGIN_FAILURE_SCREENSHOT_DIR || '/tmp',
};

// .invalid is reserved by RFC 2606 and can never resolve, so if this string ever
// appears in a served page it can only have come from the request's Host header.
const SPOOFED_HOST = 'carlos-host-header-probe.invalid';

// Distinctive enough that finding it in the body proves the page rendered the
// message we supplied, and carries markup so the encoding assertion is real.
const ERROR_MESSAGE = 'Probe <b>message</b> for host header check';

// This check's subject is what the packaged front door actually SERVES: nginx is in
// scope precisely because a rewrite there could re-introduce a <base href> the
// application never emitted. The default BASE_URL is bare Tomcat, where nothing sits
// in front of the container, so a green run there covers only what the application
// emits and says nothing about that layer. Report the front door explicitly rather
// than letting a standalone run read as full coverage; EXPECT_FRONT_DOOR=true turns
// the warning into a failure. Same signal and same spelling as echart-print and
// clinical-freetext: the Server header is the only cheap evidence available.
const FRONT_DOOR_SERVER = /nginx/i;
const sawFrontDoor = (headers) => FRONT_DOOR_SERVER.test(headers.server || '');
const expectFrontDoor = /^(1|true|yes)$/i.test(process.env.EXPECT_FRONT_DOOR || '');

// A front door that REFUSES the spoofed Host satisfies the property under test by a
// different mechanism, so it is a pass -- but only if the refusal actually came from
// the front door. 400 (malformed/rejected Host) and 421 (Misdirected Request) are the
// two statuses nginx answers with, and they count only when an nginx-served response
// was observed. Every other status, an application-generated 404 or 500 included, is
// the application answering DIFFERENTLY because of the Host header, which is the exact
// defect class this check exists to catch; treating it as a rejection would turn the
// defect into a green run.
const FRONT_DOOR_REJECTION_STATUSES = new Set([400, 421]);
const isFrontDoorRejection = (frontDoorObserved, status) => frontDoorObserved
  && FRONT_DOOR_REJECTION_STATUSES.has(status);

// Certificate verification is relaxed ONLY for loopback, where the packaged install
// serves its own self-signed cert. A non-local target opted in through
// ALLOW_NON_LOCAL_BASE_URL must still prove its certificate: this check reads a page
// back and compares it byte for byte, so a silently accepted man-in-the-middle would
// make every assertion below meaningless. This is the same rule allergy-rx-alert
// and billing-on-third-party apply to their browser contexts.
const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1']);
const isLoopbackTarget = () => LOOPBACK_HOSTS.has(
  config.baseUrl.hostname.replace(/^\[|\]$/g, '').toLowerCase(),
);

/**
 * GET a path over a raw socket with a caller-chosen Host header.
 *
 * Bypasses both the browser and Playwright's request stack so the Host value is
 * transmitted exactly as given; see the header comment.
 */
function rawGet(pathname, search, hostHeader) {
  const transport = config.baseUrl.protocol === 'https:' ? https : http;
  const defaultPort = config.baseUrl.protocol === 'https:' ? 443 : 80;
  const port = config.baseUrl.port || String(defaultPort);
  return new Promise((resolve, reject) => {
    const request = transport.request(
      {
        host: config.baseUrl.hostname,
        port,
        path: `${pathname}${search}`,
        method: 'GET',
        headers: { Host: hostHeader, Connection: 'close' },
        // The packaged install terminates TLS with a self-signed certificate by
        // default; the other checks in this suite relax verification for the same
        // reason. Host-header behaviour is what is under test, not the cert -- but
        // only loopback gets the exemption; see LOOPBACK_HOSTS.
        rejectUnauthorized: !isLoopbackTarget(),
        // RFC 6066 forbids an IP literal as an SNI server name, and Node warns and will
        // eventually ignore one. BASE_URL is loopback by default, so send SNI only when the
        // host is a real name.
        ...(/^[\d.]+$/.test(config.baseUrl.hostname) || config.baseUrl.hostname.includes(':')
          ? {}
          : { servername: config.baseUrl.hostname }),
        timeout: 30000,
      },
      (response) => {
        const chunks = [];
        response.on('data', (chunk) => chunks.push(chunk));
        response.on('end', () => resolve({
          status: response.statusCode,
          headers: response.headers,
          body: Buffer.concat(chunks).toString('utf8'),
        }));
      },
    );
    request.on('timeout', () => request.destroy(new Error(`Timed out fetching ${pathname}`)));
    request.on('error', reject);
    request.end();
  });
}

/**
 * Locate the first byte at which two response bodies diverge, for failure output.
 *
 * A whole-body diff would bury the signal; the offset plus a short window either side is
 * enough to see WHICH construct became Host-dependent.
 */
function describeFirstDifference(left, right) {
  const limit = Math.min(left.length, right.length);
  let index = 0;
  while (index < limit && left[index] === right[index]) {
    index += 1;
  }
  const window = 120;
  const from = Math.max(0, index - 40);
  return `First difference at byte ${index}.\n`
    + `  legitimate Host: ${JSON.stringify(left.slice(from, from + window))}\n`
    + `  spoofed Host:    ${JSON.stringify(right.slice(from, from + window))}`;
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));

  try {
    const target = new URL(appUrl(config.baseUrl, '/loginfailed'));
    target.searchParams.set('errormsg', ERROR_MESSAGE);
    const pathname = target.pathname;
    const search = target.search;
    const honestHost = config.baseUrl.port
      ? `${config.baseUrl.hostname}:${config.baseUrl.port}`
      : config.baseUrl.hostname;

    // --- 1: the response body does not depend on the Host header -----------------
    const honest = await rawGet(pathname, search, honestHost);
    const spoofed = await rawGet(pathname, search, SPOOFED_HOST);

    assert(
      honest.status === 200,
      `GET ${pathname} with a legitimate Host returned ${honest.status}, not 200. The failed-login `
        + 'page did not render, so nothing below was actually exercised. Check that /loginfailed is '
        + 'still mapped to the public gate action in struts-login.xml.',
    );

    // Establish the layer this run actually covered before asserting anything about it.
    const frontDoorObserved = sawFrontDoor(honest.headers);
    if (expectFrontDoor && !frontDoorObserved) {
      throw new Error(
        'EXPECT_FRONT_DOOR is set but no response carried an nginx Server header; the run did not go '
        + 'through the packaged front door, so a front-door rewrite re-introducing a <base href> was '
        + 'never in the path of this check.',
      );
    }
    if (!frontDoorObserved) {
      console.log(
        'WARNING: no response carried an nginx Server header, so this run did NOT go through the '
        + 'packaged front door. Everything below covers only what the application emits; an nginx '
        + 'rewrite re-introducing a <base href> is unverified. Point BASE_URL at the packaged :443 '
        + 'target (docs/ui-tests/deb-install-validation.md) for the coverage this check is written '
        + 'for, and set EXPECT_FRONT_DOOR=true to make its absence a failure.',
      );
    }

    const spoofRejectedAtFrontDoor = isFrontDoorRejection(frontDoorObserved, spoofed.status);
    if (spoofRejectedAtFrontDoor) {
      // Defence in depth rather than a failure: the bad Host never reached the app.
      console.log(
        `NOTE the front door answered ${spoofed.status} for Host: ${SPOOFED_HOST}, so the spoofed `
        + 'value never reached the application. Assertions 2-4 below still run against the real '
        + 'Host and independently establish that no <base> element is emitted.',
      );
    } else {
      // Status first: a differing status is already a Host-dependent response, and
      // saying so plainly beats a byte diff between an error page and the real one.
      assert(
        spoofed.status === honest.status,
        `The failed-login page's STATUS changed when the Host header changed: Host: ${honestHost} `
          + `returned ${honest.status} but Host: ${SPOOFED_HOST} returned ${spoofed.status}. That is `
          + 'the response depending on a value an attacker controls. Only an nginx-served 400 or 421 '
          + 'counts as the front door refusing the Host; this was not one, so it is the application '
          + 'answering differently.',
      );
      assert(
        spoofed.body === honest.body,
        `The failed-login page changed when the Host header changed: a request with `
          + `Host: ${SPOOFED_HOST} produced a different body than Host: ${honestHost}. Something on `
          + 'this page is derived from the Host header, which an attacker controls. This is the '
          + 'defect removed in PR #2436 (a <base href> built from request.getServerName()). '
          + describeFirstDifference(honest.body, spoofed.body),
      );
      assert(
        !spoofed.body.includes(SPOOFED_HOST),
        `The failed-login page reflected the spoofed Host ${SPOOFED_HOST} into its body.`,
      );
    }

    // --- 2-4: what a real browser parses -----------------------------------------
    const context = await browser.newContext({ ignoreHTTPSErrors: isLoopbackTarget() });
    const page = await context.newPage();
    wirePage(page, 'loginfailed', recorder);
    // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to loopback by default
    await page.goto(target.toString(), { waitUntil: 'domcontentloaded', timeout: 30000 });

    const baseElementCount = await page.locator('base').count();
    assert(
      baseElementCount === 0,
      `The failed-login page carries ${baseElementCount} <base> element(s). A <base> re-points every `
        + 'relative URL on the document; on this pre-authentication page it must not exist at all.',
    );

    const pageUrl = page.url();
    const baseUri = await page.evaluate(() => document.baseURI);
    assert(
      baseUri === pageUrl,
      `document.baseURI is ${baseUri}, expected the page's own URL ${pageUrl}. Relative references `
        + 'on the failed-login page resolve against something other than the page itself.',
    );

    // Assets must be anchored to the real origin under the servlet context path --
    // this is the job the removed <base> was doing badly, so it is what a careless
    // revert would break.
    const expectedPrefix = `${config.baseUrl.origin}${config.baseUrl.pathname}/`;
    const assets = await page.evaluate(() => ({
      favicon: document.querySelector('link[rel="icon"]')?.href ?? null,
      globalJs: document.querySelector('script[src$="/js/global.js"]')?.src ?? null,
    }));

    for (const [name, href] of Object.entries(assets)) {
      assert(
        href !== null,
        `The failed-login page no longer references ${name}. The page's assets were dropped rather `
          + 'than re-anchored to the context path.',
      );
      assert(
        href.startsWith(expectedPrefix),
        `${name} resolves to ${href}, which is not under the application's own context path `
          + `${expectedPrefix}. Relative resource resolution has left the app's origin.`,
      );
    }

    // --- 4: the page still renders its message, encoded --------------------------
    const bodyText = await page.locator('body').innerText();
    assert(
      bodyText.includes(ERROR_MESSAGE),
      `The failed-login page did not render the errormsg it was given. Body text was: ${bodyText}`,
    );
    assert(
      (await page.locator('body b').count()) === 0,
      'The errormsg rendered as live markup on the failed-login page: the <b> in the probe message '
        + 'became a real element. carlos:encode is no longer encoding this value, which is a stored '
        + 'reflected-XSS sink on a pre-authentication page.',
    );

    await screenshot(page, config.screenshotDir, 'login-failure-host-header');
    await page.close();

    assertNoPageErrors(recorder);
    await context.close();

    console.log(
      'PASS login failure host header: /loginfailed serves a byte-identical body under a spoofed '
      + 'Host'
      + (spoofRejectedAtFrontDoor ? ' (front door rejected the spoofed Host)' : '')
      + ', emits no <base> element, keeps favicon and global.js anchored to the servlet context '
      + 'path, and still renders its error message HTML-encoded'
      + (frontDoorObserved
        ? ' -- through the packaged nginx front door, so a front-door rewrite was in scope'
        : ' -- against bare Tomcat, so the front-door layer was NOT covered (see the warning above)'),
    );
  } catch (error) {
    console.error('FAIL login failure host header Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
