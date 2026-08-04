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
 * Browser regression checks for the Report > Demographic Report Tool
 * navigation fix (issue #3275).
 *
 * Before the fix, the "Demographic Report Tool" link on the Report index
 * page opened in a brand-new browser tab (target="_blank") with no way back
 * other than closing the tab. This script asserts that:
 *   1. Following the link never opens a second browser tab/window.
 *   2. When reached through the schedule-shell (scheduleNav=1), the
 *      destination page keeps the app top-nav bar visible, including across
 *      a "Run Query" form submit.
 *   3. When reached outside the schedule-shell (the default popup-window
 *      navigation mode), the destination page offers a working "Back"
 *      control instead of leaving the user with only "close the tab".
 *
 * Defaults are for the local devcontainer:
 *   node scripts/report-demographic-navigation-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';

const findings = [];
const visited = [];

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);

// Matches a full dotted-quad IPv4 address only (anchored start-to-end), so a
// hostname like "10.attacker.example" cannot be mistaken for the private
// 10.0.0.0/8 range just because it starts with the same characters as one.
function isPrivateIpv4(host) {
  const match = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(host);
  if (!match) {
    return false;
  }
  const octets = match.slice(1).map(Number);
  if (octets.some((octet) => octet > 255)) {
    return false;
  }
  const [a, b] = octets;
  return a === 10 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31);
}

function isLocalHost(rawHost) {
  // URL.hostname keeps the brackets on IPv6 literals (e.g. "[::1]"); strip
  // them so bracketed loopback addresses match the same as their bare form.
  const host = rawHost.toLowerCase().replace(/^\[|\]$/g, '');
  return LOCAL_HOSTS.has(host) || isPrivateIpv4(host);
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }

  const host = parsed.hostname.toLowerCase();
  if (!isLocalHost(host) && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

function appUrl(appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  // Split off the query string before assigning to url.pathname: the pathname
  // setter percent-encodes "?" instead of treating it as a query separator,
  // which silently mangles any appPath that carries query parameters.
  const [pathPart, queryPart] = appPath.split('?');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${pathPart}`.replace(/\/{2,}/g, '/');
  url.search = queryPart ? `?${queryPart}` : '';
  return url.toString();
}

function safeGoto(page, appPath, options) {
  return page.goto(appUrl(appPath), options); // nosemgrep // NOSONAR - appUrl validates local-only BASE_URL and root-relative paths.
}

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (/\/imageRenderingServlet\?/.test(responseUrl) || /\/favicon\.ico$/.test(responseUrl));
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

function wirePage(page, label) {
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      findings.push({ label, type: 'http', status, url: responseUrl });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      findings.push({ label, type: `console:${message.type()}`, text: message.text(), location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    findings.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
  page.on('dialog', async (dialog) => {
    findings.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
}

const ERROR_PAGE_PATTERN = /CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i;

// Only the matched phrase plus a little surrounding context is captured (never
// the full page body), so CI logs never risk surfacing patient-like content
// that could be rendered on an error page.
function extractErrorSignature(bodyText) {
  const match = ERROR_PAGE_PATTERN.exec(bodyText);
  if (!match) {
    return null;
  }
  const start = Math.max(0, match.index - 40);
  const end = Math.min(bodyText.length, match.index + match[0].length + 80);
  return bodyText.slice(start, end).replace(/\s+/g, ' ').trim();
}

async function assertNoErrorPage(page, label) {
  const bodyText = await page.locator('body').innerText().catch(() => '');
  const signature = extractErrorSignature(bodyText);
  if (signature) {
    findings.push({
      label,
      type: 'error-page',
      url: page.url(),
      signature,
    });
  }
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await safeGoto(page, '/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'login', url: page.url() });
  await assertNoErrorPage(page, 'login');
  return page;
}

// Follows the Demographic Report Tool link and asserts it never opens a
// second tab/window (the #3275 "opens in a separate tab" regression).
async function clickDemographicReportLink(context, reportIndexPage, label) {
  const locator = reportIndexPage.locator("a[href*='ViewReportDemographicReport']").first();
  if (!await locator.count()) {
    findings.push({ label, type: 'missing-user-link', selector: "a[href*='ViewReportDemographicReport']" });
    return null;
  }

  const targetAttr = await locator.getAttribute('target');
  if (targetAttr === '_blank') {
    findings.push({ label, type: 'opens-new-tab', detail: 'link still carries target="_blank"' });
  }

  const existingPages = context.pages();
  const popupPromise = context.waitForEvent('page', { timeout: 4000 }).catch(() => null);
  const navigatedPromise = reportIndexPage.waitForURL(/ViewReportDemographicReport/, { timeout: 15000 }).catch(() => null);
  await locator.click();
  const [popupPage, navigated] = await Promise.all([popupPromise, navigatedPromise]);

  if (popupPage) {
    findings.push({ label, type: 'opens-new-tab', detail: 'click opened a second browser page/tab' });
    wirePage(popupPage, `${label}-popup`);
    await popupPage.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  }

  const currentPages = context.pages();
  if (currentPages.length !== existingPages.length) {
    findings.push({ label, type: 'unexpected-page-count', before: existingPages.length, after: currentPages.length });
  }

  await reportIndexPage.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  await reportIndexPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label, url: reportIndexPage.url() });
  await assertNoErrorPage(reportIndexPage, label);

  // Without this, a click that silently fails to navigate would leave later
  // scheduleNav/nav-bar assertions running against the unchanged index page,
  // which could pass and mask a real regression.
  if (!navigated && !/ViewReportDemographicReport/.test(reportIndexPage.url())) {
    findings.push({ label, type: 'navigation-failed', detail: 'click did not navigate to the Demographic Report Tool', url: reportIndexPage.url() });
    return null;
  }

  return reportIndexPage;
}

async function checkScheduleShellMode(context) {
  const label = 'demographic-report-schedule-nav';
  const reportIndexPage = await context.newPage();
  wirePage(reportIndexPage, `${label}-index`);
  await safeGoto(reportIndexPage, '/report/ViewReportindex?scheduleNav=1', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await reportIndexPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const reportPage = await clickDemographicReportLink(context, reportIndexPage, label);
  if (!reportPage) {
    return;
  }

  if (!/scheduleNav=1/.test(reportPage.url())) {
    findings.push({ label, type: 'schedule-nav-lost', url: reportPage.url() });
  }

  const navBarVisible = await reportPage.locator('#logoutButton').isVisible().catch(() => false);
  if (!navBarVisible) {
    findings.push({ label, type: 'nav-bar-missing', url: reportPage.url() });
  }

  // Run a query and confirm the top-nav bar survives the form POST/forward.
  const demographicNoCheckbox = reportPage.locator('#select_demographic_no');
  if (!await demographicNoCheckbox.count()) {
    findings.push({ label, type: 'missing-run-query-precondition', selector: '#select_demographic_no' });
  } else {
    await demographicNoCheckbox.check();
    await Promise.all([
      reportPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {}),
      reportPage.getByRole('button', { name: 'Run Query', exact: true }).click(),
    ]);
    await assertNoErrorPage(reportPage, `${label}-run-query`);
    const navBarAfterQuery = await reportPage.locator('#logoutButton').isVisible().catch(() => false);
    if (!navBarAfterQuery) {
      findings.push({ label: `${label}-run-query`, type: 'nav-bar-missing-after-submit', url: reportPage.url() });
    }
  }

  await reportPage.close();
}

async function checkPopupWindowMode(context) {
  const label = 'demographic-report-popup-mode';
  const reportIndexPage = await context.newPage();
  wirePage(reportIndexPage, `${label}-index`);
  await safeGoto(reportIndexPage, '/report/ViewReportindex', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await reportIndexPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const reportPage = await clickDemographicReportLink(context, reportIndexPage, label);
  if (!reportPage) {
    return;
  }

  // Selects by id rather than the localized "Back" label (fmt:message key
  // global.btnBack) so this check doesn't depend on locale/label content.
  const backButton = reportPage.locator('#demographicReportBackButton');
  if (!await backButton.count()) {
    findings.push({ label, type: 'back-control-missing', url: reportPage.url() });
  } else {
    await Promise.all([
      reportPage.waitForURL(/ViewReportindex/, { timeout: 10000 }).catch(() => {}),
      backButton.click(),
    ]);
    if (!/ViewReportindex/.test(reportPage.url())) {
      findings.push({ label, type: 'back-control-did-not-navigate', url: reportPage.url() });
    }
  }

  await reportPage.close();
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
    // Certificate validation is only relaxed for local targets (self-signed dev
    // certs are common there). A non-local target reached via ALLOW_NON_LOCAL_BASE_URL
    // still gets full TLS validation, so a spoofed/invalid cert can't silently
    // intercept the credentialed login this script performs.
    const context = await browser.newContext({
      ignoreHTTPSErrors: isLocalHost(baseUrl.hostname),
      viewport: { width: 1440, height: 1000 },
    });
    await login(context);

    await checkScheduleShellMode(context);
    await checkPopupWindowMode(context);

    console.log(JSON.stringify({ visited, findings }, null, 2));

    const blockingFindings = findings.filter((finding) => finding.type !== 'dialog');
    if (blockingFindings.length) {
      throw new Error(`demographic report navigation check found ${blockingFindings.length} issue(s)`);
    }

    console.log('PASS demographic report navigation stays in-tab and offers a way back');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL demographic report navigation Playwright check');
  console.error(error.stack || error.message);
  process.exit(1);
});
