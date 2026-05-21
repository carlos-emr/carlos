#!/usr/bin/env node
/*
 * Browser regression check for cross-window logout broadcast.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/logout-broadcast-playwright-checks.js
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

function safeGoto(page, appPath, options = {}) {
  return page.goto(appUrl(appPath), options); // nosemgrep // appUrl restricts paths and validateBaseUrl restricts hosts.
}

async function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function assertNotBlank(page, label, minHtml = 100) {
  const bodyText = (await page.locator('body').innerText({ timeout: 10000 })).trim();
  const bodyHtml = (await page.locator('body').innerHTML({ timeout: 10000 })).trim();
  await assert(bodyText.length > 0 || bodyHtml.length >= minHtml, `${label} rendered blank content`);
}

async function login(page) {
  await safeGoto(page, '/', { waitUntil: 'domcontentloaded' });
  await page.locator('#username').waitFor({ timeout: 10000 });
  await assertNotBlank(page, 'login page');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForURL(/provider\/(providercontrol|ViewAppointmentAdminDay)/, { timeout: 20000 });
  await assertAuthenticatedPage(page, 'post-login page');
}

async function assertAuthenticatedPage(page, label) {
  await assertNotBlank(page, label, 1000);
  const html = await page.content();
  await assert(html.includes('window.__carlosLogoutActive=true;'), `${label} did not include logout broadcast listener`);
  await assert(html.includes("BroadcastChannel('carlos_logout')"), `${label} did not include BroadcastChannel listener`);
  await assert(!html.includes('var ready='), `${label} still includes stale logout grace-period code`);
}

async function openScriptOwnedAppWindow(page, label) {
  const popupPromise = page.waitForEvent('popup', { timeout: 10000 });
  await page.evaluate((target) => {
    window.open(target, '_blank', 'width=900,height=700');
  }, appUrl('/provider/providercontrol'));
  const popup = await popupPromise;
  popup.on('console', (message) => {
    if (message.type() === 'error') {
      console.log(`${label} console error: ${message.text()}`);
    }
  });
  await popup.waitForLoadState('domcontentloaded');
  await popup.waitForURL(/provider\/(providercontrol|ViewAppointmentAdminDay)/, { timeout: 20000 });
  await assertAuthenticatedPage(popup, label);
  return popup;
}

async function waitForClosed(page, label, timeoutMs = 5000) {
  if (page.isClosed()) {
    return;
  }
  await Promise.race([
    page.waitForEvent('close'),
    new Promise((_, reject) => setTimeout(() => reject(new Error(`${label} did not close within ${timeoutMs}ms`)), timeoutMs)),
  ]);
}

async function waitForClosedOrLoginRedirect(page, label, timeoutMs = 5000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (page.isClosed()) {
      return 'closed';
    }
    if (/\/index|\/logout|login/i.test(page.url())) {
      await assertNotBlank(page, `${label} login redirect`);
      return 'redirected';
    }
    const bodyText = await page.locator('body').innerText({ timeout: 500 }).catch(() => '');
    if (/Logged out|logged out/i.test(bodyText)) {
      return 'logged-out-overlay';
    }
    await page.waitForTimeout(100);
  }
  throw new Error(`${label} did not close or redirect after logout; final URL: ${page.url()}`);
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
  const context = await browser.newContext({ ignoreHTTPSErrors: true });

  try {
    const primary = await context.newPage();
    await login(primary);

    const popup = await openScriptOwnedAppWindow(primary, 'script-owned popup');
    const secondPopup = await openScriptOwnedAppWindow(primary, 'second script-owned popup');

    const tab = await context.newPage();
    await safeGoto(tab, '/provider/providercontrol', { waitUntil: 'domcontentloaded' });
    await tab.waitForURL(/provider\/(providercontrol|ViewAppointmentAdminDay)/, { timeout: 20000 });
    await assertAuthenticatedPage(tab, 'same-session tab');

    await safeGoto(primary, '/logoutPage', { waitUntil: 'domcontentloaded' });
    await Promise.all([
      waitForClosed(popup, 'script-owned popup'),
      waitForClosed(secondPopup, 'second script-owned popup'),
    ]);
    const tabResult = await waitForClosedOrLoginRedirect(tab, 'same-session tab');
    await primary.waitForURL(/\/index|\/logout|login/i, { timeout: 10000 }).catch(() => {});

    console.log(`PASS logout broadcast closed script-owned windows and ${tabResult} same-session tab`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
