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
 * Browser checks for the corrected client-input refusals through the packaged
 * front door. Both former error probes must now return controlled HTTP 400s,
 * with no stack trace, private input, download, or server-error replacement.
 *
 * Additional unit-level 5xx replacement is exercised by
 * ResponseSanitizationFilterTomcatIntegrationTest: test-only servlets under real
 * Tomcat cover HTML errors and /ws bodies (including clean partial JSON), with
 * successful/400 controls and a disabled-filter negative control. Those fixtures
 * never ship in the WAR. Do not depend on an unfixed application bug for a 500.
 *
 * Run this browser check through HTTPS :443 to observe the packaged front door.
 * Requires BASE_URL, TEST_USER, TEST_PASSWORD and TEST_PIN as in the runbook.
 * Optional CHROME_PATH and ERROR_SANITIZATION_SCREENSHOT_DIR. The disposable
 * installed check temporarily adds fault routes and restores them before exiting.
 */


const legacyHarness=require('./lib/legacy-check');
const { chromium } = require('playwright');
const { SkipCheck, shouldIgnoreHttpsErrors, assertStrictPage, wireStrictPage } = require('./lib/playwright-harness');
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

/** A validation refusal must neither reflect input nor masquerade as a 500. */
function assertControlledRefusal(label, status, body, headers, forbiddenInput) {
  assert(status === 400, `${label}: expected HTTP 400, got ${status}`);
  assert(!headers['content-disposition'], `${label}: invalid input started a download`);
  assert(!REFERENCE_ID_PATTERN.test(body), `${label}: a controlled 400 reached the server-error sanitizer`);
  if (typeof forbiddenInput === 'string' && forbiddenInput.length) {
    const encoded = forbiddenInput.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    assert(!body.includes(forbiddenInput) && !body.includes(encoded), `${label}: the refusal reflected invalid input`);
  }
  for (const marker of STACK_TRACE_MARKERS) {
    assert(!body.includes(marker), `${label}: the refusal leaked a stack-trace marker`);
  }
  assert(!/\bat\s+(?:[\w.$@-]+\/)?[\w.$]+\.[\w$<>]+\((?:[^()\r\n]*\.java:\d+|Native Method|Unknown Source)\)/.test(body)
    && !/\b(?:java|javax|jakarta)\.[\w.$]+(?:Exception|Error)\b/.test(body),
  `${label}: the refusal leaked Java exception or stack-frame details`);
}

function assertScheduleDateRefusal(status,body,headers) {
  assertControlledRefusal('Invalid schedule-count dates',status,body,headers,'not-a-date');
  assert(body.includes('Path parameter has the wrong format'),
    'Invalid schedule-count dates: the 400 did not come from the schedule route');
}

function assertSanitizedFault(status, body, headers, errorDispatch = false) {
  if (errorDispatch) assert(headers['x-carlos-test-error-dispatch'] === 'observed', 'Fault fixture did not execute the real ERROR dispatcher');
  assert(status === 500, `Fault fixture: expected HTTP 500, got ${status}`);
  assert(REFERENCE_ID_PATTERN.test(body), 'Fault fixture: missing sanitized Reference ID');
  assert(!headers['content-disposition'], 'Fault fixture started a download');
  assert((headers['content-type'] || '').toLowerCase().includes('text/html'),
    'Fault fixture was not replaced by the HTML sanitizer');
  assert(!body.includes('FAKE-private-error-5794'), 'Fault fixture leaked its original body');
  for (const marker of STACK_TRACE_MARKERS) assert(!body.includes(marker), 'Fault fixture leaked a stack trace');
}

// The fault descriptor must exist only for this check, never for other suite
// clients. Keep this parent alive on cancellation until Python restores it.
function superviseInstalledFault({ environment = process.env, signalProcess = process,
  spawn = require('node:child_process').spawn } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn('python3', ['scripts/ci/run-installed-error-sanitization.py', '--',
      'node', 'scripts/error-sanitization-playwright-checks.js'], {
      cwd: require('node:path').resolve(__dirname, '..'), env: environment, stdio: 'inherit',
    });
    const interrupt = () => child.kill('SIGINT');
    const terminate = () => child.kill('SIGTERM');
    const cleanup = () => {
      signalProcess.removeListener('SIGINT', interrupt);
      signalProcess.removeListener('SIGTERM', terminate);
    };
    signalProcess.on('SIGINT', interrupt);
    signalProcess.on('SIGTERM', terminate);
    child.once('error', error => { cleanup(); reject(error); });
    child.once('close', (code, signal) => {
      cleanup();
      if (signal) console.error(`FAIL installed error fixture supervisor terminated by ${signal}; web.xml restoration is unconfirmed`);
      resolve(signal || code === null ? 1 : code);
    });
  });
}

if (require.main === module && process.env.CARLOS_DISPOSABLE_HOST === 'true'
    && !process.env.ERROR_SANITIZATION_FAULT_TOKEN) {
  superviseInstalledFault().then(code => { process.exitCode = code; }, () => {
    console.error('FAIL installed error fixture could not start');
    process.exitCode = 1;
  });
} else if (require.main === module) legacyHarness.runLegacyCheck('error-sanitization',async({cancellation})=>{
  const faultToken = process.env.ERROR_SANITIZATION_FAULT_TOKEN || '';
  if (!/^[a-f0-9]{32}$/.test(faultToken)) {
    throw new SkipCheck('Run scripts/ci/run-installed-error-sanitization.py inside the disposable packaged container to provision temporary fault routes');
  }
  assert(config.baseUrl.protocol === 'https:' && (!config.baseUrl.port || config.baseUrl.port === '443'),
    'Error sanitization requires the packaged HTTPS front door on port 443');
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

    // Authenticated API requests bypass browser-error collection, so assert each
    // intentional refusal explicitly; ordinary browser failures remain fatal.
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
      assertControlledRefusal('Invalid satellite clinic address', response.status(), body,
        response.headers(), parameters.scAddress);
      await response.dispose();
    }

    const wsPath = '/ws/rs/schedule/fetchProvidersApptsCount/not-a-date/not-a-date';
    const ws = await page.request.get(`${config.baseUrl.href}${wsPath}`, {
      failOnStatusCode: false,
      maxRedirects: 0,
    });
    cancellation?.throwIfCancelled();
    try {
      const wsBody = await ws.text();
      cancellation?.throwIfCancelled();
      assertScheduleDateRefusal(ws.status(), wsBody, ws.headers());
    } finally { await ws.dispose(); }

    // Exercise a JSON 5xx under /ws and an exception dispatched through ERROR,
    // using the installed descriptor and packaged nginx rather than a mock filter.
    for (const suffix of [`/ws/rs/__test-error-${faultToken}`, `/__test-error-${faultToken}.jsp?mode=throw`, `/__test-error-${faultToken}.jsp?mode=error`]) {
      const fault = await page.request.get(`${config.baseUrl.href}${suffix}`, { failOnStatusCode: false, maxRedirects: 0 });
      try {
        assertSanitizedFault(fault.status(), await fault.text(), fault.headers(), suffix.endsWith('mode=error'));
      } finally { await fault.dispose(); }
      cancellation?.throwIfCancelled();
    }

    await screenshot(page, config.screenshotDir, 'error-sanitization');
    cancellation?.throwIfCancelled();
    await page.close();
    cancellation?.throwIfCancelled();

    // The intentional refusals above use APIRequestContext, which emits no page
    // response/console events. Every observed browser failure remains fatal.
    assertNoPageErrors(recorder);
    assertStrictPage(recorder);

    await context.close();
    cancellation?.throwIfCancelled();

    console.log('PASS error sanitization: former server-error probes return controlled HTTP 400 refusals '
      + 'and deterministic /ws and ERROR-dispatch 500 bodies are sanitized through the packaged front door');
  } catch (error) {
    console.error('FAIL error sanitization Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
});

module.exports = { assertControlledRefusal, assertScheduleDateRefusal, assertSanitizedFault, superviseInstalledFault };
