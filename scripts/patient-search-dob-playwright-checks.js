#!/usr/bin/env node
/*
 * Browser regression checks for DOB search in the patient search pop-up.
 *
 * Guards against the issue #3237 regression where the DOB auto-formatter in
 * zdemographicfulltitlesearch.jsp silently dropped a typed separator after the
 * year, so typing the required YYYY-MM-DD format appeared to stop accepting
 * input at 4 characters. The script logs in, opens the patient search page,
 * selects DOB mode, and verifies keystroke-by-keystroke entry of a full date
 * (with and without separators), then submits the search and checks the
 * results page renders without errors.
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
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';

const findings = [];
const checks = [];

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
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${appPath}`.replace(/\/{2,}/g, '/');
  url.search = '';
  return url.toString();
}

function safeGoto(page, appPath, options) {
  return page.goto(appUrl(appPath), options); // nosemgrep // NOSONAR - appUrl validates local-only BASE_URL and root-relative paths.
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

async function assertNoErrorPage(page, label) {
  const bodyText = await page.locator('body').innerText().catch(() => '');
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(bodyText)) {
    findings.push({
      label,
      type: 'error-page',
      url: page.url(),
      body: bodyText.replace(/\s+/g, ' ').slice(0, 500),
    });
  }
}

function expectValue(label, actual, expected) {
  const pass = actual === expected;
  checks.push({ label, actual, expected, pass });
  if (!pass) {
    findings.push({ label, type: 'value-mismatch', actual, expected });
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
  await page.locator('#keyword').pressSequentially(text, { delay: 25 });
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
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1024, height: 700 } });
    await login(context);

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

    // Alternate separators are normalized to hyphens.
    await clearKeyword(page);
    expectValue('dob-slash-separators', await typeDob(page, '1980/01/01'), '1980-01-01');

    // A full DOB submits without the format alert and renders results.
    await Promise.all([
      page.waitForURL(/DemographicSearch/, { timeout: 30000 }),
      page.locator('form[name="titlesearch"] input[type="SUBMIT"], form[name="titlesearch"] input[type="submit"]').first().click(),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNoErrorPage(page, 'dob-search-results');

    console.log(JSON.stringify({ checks, findings }, null, 2));

    if (findings.length) {
      throw new Error(`patient search DOB browser check found ${findings.length} issue(s)`);
    }

    console.log('PASS patient search DOB entry accepts full YYYY-MM-DD input');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL patient search DOB Playwright check');
  console.error(error.stack || error.message);
  process.exit(1);
});
