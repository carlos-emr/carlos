#!/usr/bin/env node
/*
 * Browser regression checks for high-risk CARLOS UI surfaces.
 *
 * The script follows the same links a user follows for the demographic patient
 * page, then verifies the patient edit, Tickler, Consultation, and admin pages
 * that are sensitive to shared JavaScript/CSS and filter response handling.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/browser-surface-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   SURFACE_SEARCH_TERM=FAKE-J
 *   SURFACE_DEMOGRAPHIC_NO=1
 *   SURFACE_SCREENSHOT_DIR=/tmp
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');
const { buildArtifactPath } = require('./eform-local-playwright-utils');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const searchTerm = process.env.SURFACE_SEARCH_TERM || 'FAKE-J';
const demographicNo = process.env.SURFACE_DEMOGRAPHIC_NO || '1';
const screenshotDir = process.env.SURFACE_SCREENSHOT_DIR || '/tmp';

const badResponses = [];
const consoleIssues = [];

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
  const relative = new URL(appPath, 'http://localhost');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  return url.toString();
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function isExpectedMissingFixtureImage(status, responseUrl) {
  return status === 404 && /\/imageRenderingServlet\?/.test(responseUrl);
}

function isSevereConsoleMessage(message) {
  const text = message.text();
  if (message.type() === 'pageerror') {
    return true;
  }
  return /(ReferenceError|SyntaxError|TypeError|DataTable is not a function|forceWindowPaths|Cannot reset buffer)/i.test(text);
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    consoleIssues.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    const contentType = response.headers()['content-type'] || '';
    if (status >= 400 && !isExpectedMissingFixtureImage(status, responseUrl)) {
      badResponses.push({ label, status, url: responseUrl, contentType });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      consoleIssues.push({ label, type: message.type(), text: message.text(), location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    consoleIssues.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
}

async function gotoApp(page, appPath, waitUntil = 'domcontentloaded') {
  // appUrl validates that this remains inside the configured CARLOS base URL.
  return page.goto(appUrl(appPath), { waitUntil, timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to local/private by default
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'schedule');
  await gotoApp(page, '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function openPatientFromSearch(context, schedulePage) {
  const searchPopup = context.waitForEvent('page');
  await schedulePage.locator('a').filter({ hasText: /^Search$/ }).click();
  const searchPage = await searchPopup;
  wirePage(searchPage, 'search');
  await searchPage.waitForLoadState('domcontentloaded', { timeout: 30000 });

  await searchPage.locator('#keyword, input[name="keyword"]').first().fill(searchTerm);
  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
    searchPage.locator('input[type="submit"][value="Search"]').first().click(),
  ]);
  await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const patientPopup = context.waitForEvent('page');
  await searchPage.locator(`a[onclick*='DemographicEdit?demographic_no=${demographicNo}']`).first().click();
  const patientPage = await patientPopup;
  wirePage(patientPage, 'patient');
  await patientPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return patientPage;
}

async function openPatientEditPage(context) {
  const page = await context.newPage();
  wirePage(page, 'patient-edit');
  await gotoApp(page, `/demographic/DemographicEdit?demographic_no=${encodeURIComponent(demographicNo)}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function savePhoneComment(page, value) {
  await enterDemographicEditMode(page);
  await page.locator('textarea[name="phoneComment"]').fill(value);
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('input.btn-toolbar-update[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
}

async function enterDemographicEditMode(page) {
  try {
    await page.locator('#editBtn').waitFor({ state: 'attached', timeout: 15000 });
  } catch (error) {
    const bodyText = await page.locator('body').innerText().catch(() => '');
    throw new Error(`demographic edit button was unavailable at ${page.url()}: ${bodyText.slice(0, 1000)}`, { cause: error });
  }
  const phoneComment = page.locator('textarea[name="phoneComment"]').first();
  if (await phoneComment.isVisible().catch(() => false)) {
    return;
  }
  await page.locator('#editBtn').click();
  await phoneComment.waitFor({ state: 'visible', timeout: 15000 });
}

async function checkDemographicCrud(context) {
  let page = await openPatientEditPage(context);
  await enterDemographicEditMode(page);
  const original = await page.locator('textarea[name="phoneComment"]').inputValue();
  const updated = `Playwright browser surface ${Date.now()}`;
  try {
    await savePhoneComment(page, updated);
    await page.close();

    page = await openPatientEditPage(context);
    const saved = await page.locator('textarea[name="phoneComment"]').inputValue();
    assert(saved === updated, `demographic phone comment did not save. Expected ${updated}, got ${saved}`);
  } finally {
    if (!page.isClosed()) {
      await savePhoneComment(page, original);
      await page.close();
    }
  }
}

async function clickPopupLink(context, page, selector, label) {
  const popupPromise = context.waitForEvent('page');
  await page.locator(selector).first().click();
  const popup = await popupPromise;
  wirePage(popup, label);
  await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return popup;
}

async function assertDataTablesAvailable(page, label) {
  const dataTablesState = await page.evaluate(() => ({
    hasJQuery: typeof window.jQuery !== 'undefined',
    hasDataTable: typeof window.jQuery !== 'undefined'
      && window.jQuery.fn
      && (typeof window.jQuery.fn.DataTable === 'function' || typeof window.jQuery.fn.dataTable === 'function'),
  }));
  assert(dataTablesState.hasJQuery, `${label} did not load jQuery`);
  assert(dataTablesState.hasDataTable, `${label} did not load DataTables`);
}

async function checkPatientPopups(context, patientPage) {
  const tickler = await clickPopupLink(
    context,
    patientPage,
    "a[onclick*='/tickler/ViewTicklerMain']",
    'tickler'
  );
  await tickler.locator('body').waitFor({ state: 'visible', timeout: 15000 });
  await assertDataTablesAvailable(tickler, 'tickler');
  await tickler.screenshot({ path: buildArtifactPath(screenshotDir, 'surface-tickler'), fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename
  await tickler.close();
  await patientPage.bringToFront();

  const consultation = await clickPopupLink(
    context,
    patientPage,
    "a[onclick*='ViewDisplayDemographicConsultationRequests']",
    'consultation'
  );
  await consultation.locator('body').waitFor({ state: 'visible', timeout: 15000 });
  await assertDataTablesAvailable(consultation, 'consultation');
  await consultation.screenshot({ path: buildArtifactPath(screenshotDir, 'surface-consultation'), fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename
}

async function checkAdminPage(context, appPath, label, requiredText) {
  const page = await context.newPage();
  wirePage(page, label);
  const response = await gotoApp(page, appPath);
  assert(response && response.ok(), `${label} returned ${response ? response.status() : 'no response'}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  if (requiredText) {
    await page.locator('body').filter({ hasText: requiredText }).waitFor({ state: 'visible', timeout: 15000 });
  }
  await page.screenshot({ path: buildArtifactPath(screenshotDir, `surface-${label}`), fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename
  await page.close();
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
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const schedulePage = await login(context);
    const patientPage = await openPatientFromSearch(context, schedulePage);

    await checkDemographicCrud(context);
    await checkPatientPopups(context, patientPage);
    await checkAdminPage(context, '/admin/ViewAdminDisplayMyGroup', 'admin-my-group', 'Group');
    await checkAdminPage(context, '/admin/labForwardingRules', 'admin-lab-forwarding', 'Forwarding');
    await checkAdminPage(context, '/admin/ProviderPrivilege', 'admin-provider-privilege', 'Provider');

    const fatalConsoleIssues = consoleIssues.filter((issue) => issue.type !== 'dialog');
    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
    assert(fatalConsoleIssues.length === 0,
      `unexpected browser console failures: ${JSON.stringify(fatalConsoleIssues, null, 2)}`);

    console.log('PASS demographic, tickler, consultation, and admin browser surfaces rendered correctly');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL browser surface Playwright check');
  console.error(error.stack || error.message);
  if (badResponses.length) {
    console.error(`HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
  }
  if (consoleIssues.length) {
    console.error(`Console issues: ${JSON.stringify(consoleIssues, null, 2)}`);
  }
  process.exit(1);
});
