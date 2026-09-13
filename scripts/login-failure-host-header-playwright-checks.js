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
 * such. nginx answering 400/421 means the bad Host never reached the
 * application, which satisfies the same property by a different mechanism.
 * The check fails only if the app SERVES a page whose content depends on the
 * Host header. It does not fail the run when only assertion 1's mechanism is
 * short-circuited, because assertions 2-4 still execute against the legitimate
 * Host and independently establish that no <base> is emitted.
 *
 * RUN THIS THROUGH :443 in a packaged install, per
 * docs/ui-tests/deb-install-validation.md. Going straight to Tomcat on
 * 127.0.0.1:18080 skips nginx and ModSecurity and so cannot see a front-door
 * rewrite re-introducing a <base>.
 *
 * Env: BASE_URL (default http://127.0.0.1:8080/carlos). Optional CHROME_PATH,
 * LOGIN_FAILURE_SCREENSHOT_DIR (default /tmp).
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
        // default; the other checks in this suite use ignoreHTTPSErrors for the
        // same reason. Host-header behaviour is what is under test, not the cert.
        rejectUnauthorized: false,
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

    let spoofRejectedAtFrontDoor = false;
    if (spoofed.status >= 400) {
      // Defence in depth rather than a failure: the bad Host never reached the app.
      spoofRejectedAtFrontDoor = true;
      console.log(
        `NOTE the front door answered ${spoofed.status} for Host: ${SPOOFED_HOST}, so the spoofed `
        + 'value never reached the application. Assertions 2-4 below still run against the real '
        + 'Host and independently establish that no <base> element is emitted.',
      );
    } else {
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
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
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
      bodyText.includes('Probe <b>message</b> for host header check'),
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
      + 'path, and still renders its error message HTML-encoded',
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
