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
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (the owned patient and its cleanup)
 *   SURFACE_SCREENSHOT_DIR=/tmp
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * FIXTURE. The surfaces are opened for a FAKE patient this check creates (lib/owned-patient.js: last name = a FAKE-PW run marker)
 * and removes. It used to open DEMO patient 1 and save its phone comment twice; a Master Record save is not a round trip (it also
 * rewrites the record's province and newsletter codes, and files an archive row for the previous state), so putting the comment
 * back could never put the demo record back. The patient, its archive rows and its chart rows are deleted by the patient's key.
 */

const { chromium } = require('playwright');
const { buildArtifactPath } = require('./eform-local-playwright-utils');
const h = require('./lib/playwright-harness');
const { createOwnedPatient, newOwnedMarker, removeOwnedPatient } = require('./lib/owned-patient');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
// The owned patient the surfaces are opened for, created in main (never a demo patient): its marker is the search term.
const ownedMarker = newOwnedMarker();
const searchTerm = ownedMarker;
let demographicNo = null;
const screenshotDir = process.env.SURFACE_SCREENSHOT_DIR || '/tmp';

const badResponses = [];
const consoleIssues = [];

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not embed a username or password');
  }
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }

  const host = parsed.hostname.toLowerCase();
  const localHosts = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);
  const octets = host.split('.');
  const isIpv4 = octets.length === 4 && octets.every(part => /^\d{1,3}$/.test(part) && Number(part) <= 255);
  const privateIpv4 = isIpv4 && (Number(octets[0]) === 10
    || (Number(octets[0]) === 192 && Number(octets[1]) === 168)
    || (Number(octets[0]) === 172 && Number(octets[1]) >= 16 && Number(octets[1]) <= 31));
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

  const sql = h.createSqlRunner(h.readConfig().mysql);
  let browser = null;
  try {
    const provider = sql.value(`SELECT provider_no FROM security WHERE user_name=${h.sqlString(testUser)}`);
    assert(provider, 'The configured test login has no provider');
    demographicNo = createOwnedPatient(sql, { marker: ownedMarker, provider });
    // The Master Record form refuses to save a blank Canadian postal code ("The entered Postal Code is not valid"), and the surface
    // step saves it; the demo patient this check used to open had one. A reserved real-format code, on the owned patient only.
    sql.execute(`UPDATE demographic SET postal='K1A0B1' WHERE demographic_no=${demographicNo} AND last_name=${h.sqlString(ownedMarker)}`);
    browser = await chromium.launch(launchOptions);
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
    try {
      if (browser) await browser.close();
      // After the browser is gone: the patient's archive and chart rows, then the patient, by its key.
      if (demographicNo !== null) removeOwnedPatient(sql, demographicNo, ownedMarker);
    } finally {
      sql.dispose();
    }
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
