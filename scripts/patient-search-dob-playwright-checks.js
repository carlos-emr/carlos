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
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression checks for DOB search in the patient search pop-up.
 *
 * Guards two regressions in the DOB auto-formatter
 * (share/javascript/dobSearchKeyword.js, used by zdemographicfulltitlesearch.jsp):
 *   - issue #3237: a typed separator after the year was dropped, so typing
 *     YYYY-MM-DD appeared to stop accepting input at 4 characters;
 *   - issue #3956: the % wildcard was stripped and anything shorter than 8
 *     digits was refused, so YYYY, YYYY-MM and 1975-%-05 searches were
 *     impossible.
 * The script logs in, opens the patient search page, selects DOB mode, verifies
 * keystroke-by-keystroke entry (full dates, separators, wildcards, a mid-field
 * edit), submits full, year, year-month and wildcard searches and checks each
 * reaches the results page with the typed keyword intact, and checks that a
 * malformed date is refused with an alert instead of being submitted. It also
 * exercises the appointment picker form and opens the name-only report picker
 * to catch stale validation/focus handlers.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/patient-search-dob-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   DOB_TEST_LOCALE=en-US browser locale used for the search and validation alert
 *   DOB_EXPECTED_MESSAGE=... exact localized validation alert, when checking a locale
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const net = require('node:net');
const { chromium } = require('playwright');

const EXACT_LOCAL_HOSTS = new Set([
  'localhost',
  '127.0.0.1',
  '::1',
  '0:0:0:0:0:0:0:1',
  '0.0.0.0',
  'host.docker.internal',
  'carlos',
]);

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const testLocale = process.env.DOB_TEST_LOCALE || 'en-US';
const expectedDobMessage = process.env.DOB_EXPECTED_MESSAGE;

const findings = [];
const checks = [];
let expectedDialogs = 0;
let seenExpectedDialogs = 0;

function normalizedHostname(url) {
  const host = url.hostname.toLowerCase();
  return host.startsWith('[') && host.endsWith(']') ? host.slice(1, -1) : host;
}

function isExactLocalHost(host) {
  return EXACT_LOCAL_HOSTS.has(host);
}

function isPrivateIpv4(host) {
  if (!net.isIPv4(host)) {
    return false;
  }
  const [first, second] = host.split('.').map(Number);
  return first === 10
    || (first === 192 && second === 168)
    || (first === 172 && second >= 16 && second <= 31);
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not contain embedded credentials');
  }

  const host = normalizedHostname(parsed);
  const exactLocalHost = isExactLocalHost(host);
  const privateIpv4 = isPrivateIpv4(host);
  if (!exactLocalHost && !privateIpv4 && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  if (!exactLocalHost && parsed.protocol !== 'https:') {
    throw new Error('Non-local BASE_URL targets must use https');
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  parsed.search = '';
  parsed.hash = '';
  return parsed;
}

function appUrl(appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${appPath}`.replace(/\/{2,}/g, '/');
  url.search = '';
  return url.toString();
}

function isWithinConfiguredApp(url) {
  const appRoot = baseUrl.pathname || '/';
  return url.origin === baseUrl.origin
    && (appRoot === '/' || url.pathname === appRoot || url.pathname.startsWith(`${appRoot}/`));
}

async function safeGoto(page, appPath, options, parameters = {}) {
  const target = new URL(appUrl(appPath));
  target.search = new URLSearchParams(parameters).toString();
  const response = await page.goto(target.toString(), options); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to loopback by default // NOSONAR - same rationale
  if (!isWithinConfiguredApp(new URL(page.url()))) {
    throw new Error('Navigation left the configured CARLOS EMR application');
  }
  return response;
}

function waitForAppPath(page, pathPattern, options) {
  return page.waitForURL((url) => isWithinConfiguredApp(url) && pathPattern.test(url.pathname), options);
}

async function installNavigationGuard(context) {
  await context.route('**/*', async (route) => {
    const request = route.request();
    if (request.isNavigationRequest() && !isWithinConfiguredApp(new URL(request.url()))) {
      findings.push({ label: 'navigation', type: 'outside-app-navigation-blocked' });
      await route.abort('blockedbyclient');
      return;
    }
    await route.continue();
  });
}

function isExpectedConsoleNoise(message) {
  const text = message.text();
  return /Content Security Policy.*report-only/i.test(text)
    || /Master token \[CSRF-TOKEN\]/.test(text)
    || /Hidden token fields .* were updated with new token value/.test(text);
}

function isSevereConsoleMessage(message) {
  if (isExpectedConsoleNoise(message)) {
    return false;
  }
  const text = message.text();
  if (message.type() === 'error') {
    return !/imageRenderingServlet\?|favicon\.ico/i.test(text);
  }
  return /(ReferenceError|TypeError|SyntaxError|redeclaration|Cannot read|Cannot set)/i.test(text);
}

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (/\/imageRenderingServlet\?/.test(responseUrl) || /\/favicon\.ico$/.test(responseUrl));
}

function wirePage(page, label) {
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      findings.push({ label, type: 'http', status });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      findings.push({ label, type: `console:${message.type()}` });
    }
  });
  page.on('pageerror', () => {
    findings.push({ label, type: 'pageerror' });
  });
  page.on('dialog', async (dialog) => {
    // Unlike sibling scripts, dialogs are blocking findings here: the DOB
    // format alert firing on a valid date is the regression this script guards.
    // expectDialog() is the one sanctioned exception, for the malformed-date step.
    if (expectedDialogs > 0) {
      expectedDialogs -= 1;
      seenExpectedDialogs += 1;
      if (expectedDobMessage !== undefined) {
        expectValue('dob-localized-validation-alert', dialog.message(), expectedDobMessage);
      }
    } else {
      findings.push({ label, type: 'dialog' });
    }
    await dialog.accept();
  });
}

async function assertNoErrorPage(page, label) {
  const bodyText = await page.locator('body').innerText().catch(() => '');
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(bodyText)) {
    findings.push({
      label,
      type: 'error-page',
    });
  }
}

function expectValue(label, actual, expected) {
  const pass = actual === expected;
  checks.push({ label, pass });
  if (!pass) {
    findings.push({ label, type: 'value-mismatch' });
  }
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await safeGoto(page, '/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  const pin = page.locator('#pin');
  if (await pin.count()) {
    await pin.fill(testPin);
  }
  await Promise.all([
    waitForAppPath(page, /providercontrol|appointment/i, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNoErrorPage(page, 'login');
  return page;
}

async function selectDobMode(page) {
  await page.locator('#search_mode').selectOption('search_dob');
}

async function clearKeyword(page) {
  await page.locator('#keyword').fill('');
}

async function typeDob(page, text) {
  await page.locator('#keyword').click();
  // Pin the caret to the end: a center click can land inside existing text.
  await page.locator('#keyword').press('End');
  await page.locator('#keyword').pressSequentially(text, { delay: 25 });
  return page.locator('#keyword').inputValue();
}

async function openDobSearch(page) {
  await safeGoto(page, '/demographic/ViewSearch', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await assertNoErrorPage(page, 'patient-search');
  await selectDobMode(page);
  await clearKeyword(page);
}

/**
 * Types a DOB, submits, and asserts the keyword the server received. The form is
 * a GET, so the query string is the server's view of the field. Result rows are
 * deliberately not asserted: they would pin the local seed data, not the fix
 * (the SQL side of partial matching is pinned by DemographicDaoIntegrationTest
 * and patient-search-modes-playwright-checks.js).
 */
async function submitDob(page, label, typed, expectedKeyword) {
  await openDobSearch(page);
  await typeDob(page, typed);
  await Promise.all([
    waitForAppPath(page, /DemographicSearch/, { timeout: 30000 }),
    page.locator('form[name="titlesearch"] input[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNoErrorPage(page, label);
  expectValue(`${label}-submitted-keyword`, new URL(page.url()).searchParams.get('keyword'), expectedKeyword);
  expectValue(`${label}-results-table`, await page.locator('#patientResults').count() > 0, true);
}

async function fillDob(page, text) {
  await page.locator('#keyword').fill(text);
  return page.locator('#keyword').inputValue();
}

(async () => {
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const browser = await chromium.launch(launchOptions);
  try {
    const context = await browser.newContext({
      ignoreHTTPSErrors: isExactLocalHost(normalizedHostname(baseUrl)),
      viewport: { width: 1024, height: 700 },
      locale: testLocale,
    });
    await installNavigationGuard(context);
    const landingPage = await login(context);
    // Close the landing page once the session cookie is established. It stays
    // wired to the finding collectors, so leaving it open lets a late console
    // error or 404 there fail the run under the 'login' label — a failure with
    // nothing to do with the DOB field this script exists to check.
    await landingPage.close();

    const page = await context.newPage();
    wirePage(page, 'patient-search');
    await safeGoto(page, '/demographic/ViewSearch', { waitUntil: 'domcontentloaded', timeout: 30000 });
    await assertNoErrorPage(page, 'patient-search');
    await selectDobMode(page);

    // Regression #3237: the separator typed after the year must survive
    // instead of the field appearing stuck at 4 characters.
    await clearKeyword(page);
    expectValue('dob-year-plus-separator', await typeDob(page, '1980-'), '1980-');
    expectValue('dob-full-with-separators', await typeDob(page, '01-01'), '1980-01-01');

    // Digits-only entry keeps auto-formatting to YYYY-MM-DD.
    await clearKeyword(page);
    expectValue('dob-digits-only', await typeDob(page, '19800101'), '1980-01-01');

    // Alternate separators are normalized to hyphens while digits and whole-part
    // wildcards survive the same keystroke-by-keystroke path.
    await clearKeyword(page);
    expectValue('dob-slash-separators', await typeDob(page, '1980/01/01'), '1980-01-01');

    await clearKeyword(page);
    expectValue('dob-dot-separators', await typeDob(page, '1980.01.01'), '1980-01-01');

    await clearKeyword(page);
    expectValue('dob-space-separators', await typeDob(page, '1980 01 01'), '1980-01-01');

    // Single-event entry covers paste/programmatic input, while the remaining
    // cases pin the edit paths the formatter promises to preserve.
    expectValue('dob-paste-digits-only', await fillDob(page, '19800101'), '1980-01-01');
    expectValue('dob-paste-with-separators', await fillDob(page, '1980-01-01'), '1980-01-01');

    await clearKeyword(page);
    await typeDob(page, '1980-');
    await page.locator('#keyword').press('Backspace');
    expectValue('dob-backspace-separator', await page.locator('#keyword').inputValue(), '1980');

    await clearKeyword(page);
    expectValue('dob-eight-digit-cap', await typeDob(page, '1980010199'), '1980-01-01');

    await clearKeyword(page);
    expectValue('dob-non-digits-ignored', await typeDob(page, '1980a01b01'), '1980-01-01');

    await clearKeyword(page);
    expectValue('dob-double-separators', await typeDob(page, '1980--01--01'), '1980-01-01');

    // Issue #3956: % survives typing as a whole-segment wildcard, and the
    // partial shapes are no longer truncated or rejected while typing.
    await clearKeyword(page);
    expectValue('dob-wildcard-month', await typeDob(page, '1980-%-01'), '1980-%-01');
    await clearKeyword(page);
    expectValue('dob-wildcard-year', await typeDob(page, '%-01-01'), '%-01-01');
    await clearKeyword(page);
    expectValue('dob-year-month', await typeDob(page, '1980-01'), '1980-01');
    await clearKeyword(page);
    expectValue('dob-one-digit-month-day', await typeDob(page, '1980-1-1'), '1980-1-1');
    expectValue('dob-paste-wildcard', await fillDob(page, '1980-%-01'), '1980-%-01');

    // A mid-field edit must not throw the caret to the end: deleting the
    // month/day separator makes the formatter re-insert it, and the caret has to
    // stay after the month (7), not jump to the end of the field (10).
    await fillDob(page, '1980-01-01');
    await page.locator('#keyword').evaluate((input) => input.setSelectionRange(7, 7));
    await page.locator('#keyword').press('Delete');
    expectValue('dob-mid-field-edit', await page.locator('#keyword').inputValue(), '1980-01-01');
    expectValue('dob-mid-field-caret',
      await page.locator('#keyword').evaluate((input) => input.selectionStart), 7);

    // Each accepted shape submits without the format alert and reaches the
    // results page with the whole keyword intact.
    await submitDob(page, 'dob-search-full', '1980-01-01', '1980-01-01');
    await submitDob(page, 'dob-search-year', '1980', '1980');
    await submitDob(page, 'dob-search-year-month', '1980-01', '1980-01');
    await submitDob(page, 'dob-search-wildcard', '1980-%-01', '1980-%-01');

    // A malformed date is still refused in the browser: exactly one alert, and
    // the page does not navigate.
    await openDobSearch(page);
    await typeDob(page, '198');
    const beforeUrl = page.url();
    expectedDialogs = 1;
    await page.locator('form[name="titlesearch"] input[type="submit"]').first().click();
    await page.waitForTimeout(750);
    expectValue('dob-malformed-alerted', seenExpectedDialogs, 1);
    expectValue('dob-malformed-not-submitted', page.url(), beforeUrl);
    expectedDialogs = 0;

    // Appointment/contact pickers have their own search form and POST routing.
    // Exercise the actual dropdown and submission, including its localized alert.
    await safeGoto(page, '/demographic/DemographicSearch',
      { waitUntil: 'domcontentloaded', timeout: 30000 },
      { displaymode: 'Search ', search_mode: 'search_dob', keyword: '1980', ptstatus: 'active' });
    await assertNoErrorPage(page, 'appointment-search');
    for (const [typed, expected] of [
      ['1980', '1980'], ['1980-01', '1980-01'],
      ['1980-%-01', '1980-%-01'], ['19800101', '1980-01-01'],
    ]) {
      await page.locator('select[name="search_mode"]').selectOption('search_dob');
      await page.locator('form[name="titlesearch"] input[name="keyword"]').fill(typed);
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
        page.locator('form[name="titlesearch"] input[type="submit"]').first().click(),
      ]);
      await assertNoErrorPage(page, 'appointment-dob-results');
      expectValue('appointment-dob-keyword', await page.locator('form[name="titlesearch"] input[name="keyword"]').inputValue(), expected);
      expectValue('appointment-search-context', await page.locator('input[type="hidden"][name="displaymode"]').inputValue(), 'Search ');
    }
    for (const invalid of ['198', '1980-13', '%']) {
      await page.locator('form[name="titlesearch"] input[name="keyword"]').fill(invalid);
      const beforeDialogs = seenExpectedDialogs;
      expectedDialogs = 1;
      await page.locator('form[name="titlesearch"] input[type="submit"]').first().click();
      await page.waitForTimeout(750);
      expectValue('appointment-malformed-alerted', seenExpectedDialogs, beforeDialogs + 1);
      expectValue('appointment-malformed-retained', await page.locator('form[name="titlesearch"] input[name="keyword"]').inputValue(), invalid);
      expectedDialogs = 0;
    }

    // The report picker is a name-only result page: it has no titlesearch form.
    // Opening it must not run stale DOB/focus code against a nonexistent form.
    await safeGoto(page, '/demographic/ViewDemographicSearch2ReportResults',
      { waitUntil: 'load', timeout: 30000 }, { keyword: 'FAKE-PW-NO-MATCH' });
    await assertNoErrorPage(page, 'report-picker');
    expectValue('report-picker-results-form', await page.locator('form[name="addform"]').count(), 1);
    expectValue('report-picker-has-no-search-form', await page.locator('form[name="titlesearch"]').count(), 0);

    if (findings.length) {
      throw new Error(`patient search DOB browser check found ${findings.length} issue(s)`);
    }

    console.log('PASS CARLOS EMR patient search DOB entry accepts YYYY, YYYY-MM, YYYY-MM-DD and % wildcards');
  } finally {
    // Dump collected evidence on success and failure alike so a mid-flow
    // timeout (e.g. the DOB alert blocking submit) still reports the checks.
    console.log(JSON.stringify({ checks, findings }, null, 2));
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL CARLOS EMR patient search DOB Playwright check');
  console.error(`Failure type: ${error && error.name ? error.name : 'Error'}`);
  process.exit(1);
});
