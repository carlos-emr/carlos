#!/usr/bin/env node
/*
 * Browser regression checks for multiple active sessions for the same CARLOS user.
 *
 * The script uses two isolated Playwright browser contexts to model two separate
 * browsers. A login from browser B must not invalidate browser A. It also verifies
 * the unauthenticated logoutPage -> logout flow lands on a login page without
 * redirecting back to logoutPage.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/multiple-session-playwright-checks.js
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

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function assertNotBlank(page, label, minHtml = 100) {
  const text = (await page.locator('body').innerText({ timeout: 10000 })).trim();
  const html = (await page.locator('body').innerHTML({ timeout: 10000 })).trim();
  assert(text.length > 0 || html.length >= minHtml, `${label} rendered blank content`);
  return { text, html };
}

async function login(context, label) {
  const page = await context.newPage();
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded' });
  await page.locator('#username').waitFor({ timeout: 15000 });
  await assertNotBlank(page, `${label} login page`);
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForURL(/provider\/(providercontrol|ViewAppointmentAdminDay)/, { timeout: 30000 });
  await assertAuthenticated(page, `${label} post-login`);
  return page;
}

async function assertAuthenticated(page, label) {
  await page.waitForLoadState('domcontentloaded').catch(() => {});
  const url = page.url();
  const { text, html } = await assertNotBlank(page, label, 1000);
  assert(!/logoutPage|\/logout\b|\/index\b|login=failed/i.test(url), `${label} was redirected out of the app: ${url}`);
  assert(/Search|Schedule|Preference|appointment|provider/i.test(text + html), `${label} did not look authenticated`);
  assert(html.includes('window.__carlosLogoutActive=true;'), `${label} did not include logout broadcast listener`);
}

async function assertStillAuthenticated(page, label) {
  await page.goto(appUrl('/provider/providercontrol'), { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  await assertAuthenticated(page, label);
}

async function assertLogoutPageNoLoop(browser) {
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  const page = await context.newPage();
  const paths = [];
  page.on('framenavigated', (frame) => {
    if (frame === page.mainFrame()) {
      paths.push(new URL(frame.url()).pathname);
    }
  });

  await page.goto(appUrl('/logoutPage'), { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);

  const current = new URL(page.url()).pathname;
  const logoutPageVisits = paths.filter((path) => path.endsWith('/logoutPage')).length;
  assert(logoutPageVisits <= 1, `unauthenticated /logoutPage looped: ${paths.join(' -> ')}`);
  assert(
    /\/index$|\/logout$|\/login/i.test(current) || current === `${baseUrl.pathname}/`,
    `unexpected unauthenticated logout destination: ${page.url()}; paths=${paths.join(' -> ')}`
  );
  await assertNotBlank(page, 'unauthenticated logout destination');
  await context.close();
  console.log(`PASS unauthenticated logoutPage completed without redirect loop (${paths.join(' -> ') || current})`);
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
  const contextA = await browser.newContext({ ignoreHTTPSErrors: true });
  const contextB = await browser.newContext({ ignoreHTTPSErrors: true });

  try {
    const pageA = await login(contextA, 'browser A');
    const pageB = await login(contextB, 'browser B');

    await assertStillAuthenticated(pageA, 'browser A after browser B login');
    await assertStillAuthenticated(pageB, 'browser B after browser A recheck');
    console.log('PASS same user can keep two independent browser sessions authenticated');

    await assertLogoutPageNoLoop(browser);
  } finally {
    await contextA.close().catch(() => {});
    await contextB.close().catch(() => {});
    await browser.close().catch(() => {});
  }
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
