#!/usr/bin/env node
/*
 * Browser regression check that logout invalidates the server-side session for
 * future same-context windows, not only already-open broadcast listeners.
 *
 * Example local usage:
 *   TEST_USER=... TEST_PASSWORD=... TEST_PIN=... node scripts/logout-session-invalidation-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

function requireEnv(name) {
  const value = process.env[name];
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(`${name} must be provided via environment variable`);
  }
  return value;
}

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = requireEnv('TEST_USER');
const testPassword = requireEnv('TEST_PASSWORD');
const testPin = requireEnv('TEST_PIN');

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

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function assertNotBlank(page, label, minHtml = 100) {
  const bodyText = (await page.locator('body').innerText({ timeout: 10000 })).trim();
  const bodyHtml = (await page.locator('body').innerHTML({ timeout: 10000 })).trim();
  assert(bodyText.length > 0 || bodyHtml.length >= minHtml, `${label} rendered blank content`);
  return bodyText;
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
  const bodyText = await assertNotBlank(page, label, 1000);
  const html = await page.content();
  assert(/Search|Schedule|Preference|appointment/i.test(bodyText + html), `${label} did not look authenticated`);
  assert(html.includes('window.__carlosLogoutActive=true;'), `${label} did not include logout broadcast listener`);
}

async function assertLoggedOutPage(page, label) {
  const bodyText = await assertNotBlank(page, label);
  const url = page.url();
  const html = await page.content();
  const looksLoggedOut = /\/index|\/logout|login/i.test(url)
    || /username|password|logged out|session expired|you have been logged out/i.test(bodyText + html);
  assert(looksLoggedOut, `${label} did not look logged out; url=${url}; path=${new URL(url).pathname}; bodyLength=${bodyText.length}`);
  assert(!/provider\/(providercontrol|ViewAppointmentAdminDay)/i.test(url), `${label} stayed on authenticated URL ${url}`);
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

    await safeGoto(primary, '/logoutPage', { waitUntil: 'domcontentloaded' });
    await primary.waitForURL((url) => url.pathname === baseUrl.pathname + '/logout', { timeout: 10000 });
    await assertLoggedOutPage(primary, 'logout action destination');

    const postLogoutPage = await context.newPage();
    await safeGoto(postLogoutPage, '/provider/providercontrol', { waitUntil: 'domcontentloaded' });
    await postLogoutPage.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
    await assertLoggedOutPage(postLogoutPage, 'post-logout protected navigation');


    console.log('PASS logout invalidated same-context session for future protected navigation');
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
