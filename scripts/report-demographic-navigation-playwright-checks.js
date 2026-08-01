#!/usr/bin/env node
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

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }

  const host = parsed.hostname.toLowerCase();
  const localHosts = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);
  const privateIpv4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  if (!localHosts.has(host) && !privateIpv4 && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
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
  await locator.click();
  const popupPage = await popupPromise;

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

  const navBarVisible = await reportPage.locator('#logoutButton').count();
  if (!navBarVisible) {
    findings.push({ label, type: 'nav-bar-missing', url: reportPage.url() });
  }

  // Run a query and confirm the top-nav bar survives the form POST/forward.
  const demographicNoCheckbox = reportPage.locator('#select_demographic_no');
  if (await demographicNoCheckbox.count()) {
    await demographicNoCheckbox.check();
    await Promise.all([
      reportPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {}),
      reportPage.getByRole('button', { name: 'Run Query', exact: true }).click(),
    ]);
    await assertNoErrorPage(reportPage, `${label}-run-query`);
    const navBarAfterQuery = await reportPage.locator('#logoutButton').count();
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

  const backButton = reportPage.getByRole('button', { name: 'Back' });
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
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
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
