#!/usr/bin/env node
/*
 * Read-only browser health checks for a running CARLOS application.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/application-health-playwright-checks.js
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

const authenticatedRoutes = [
  { label: 'schedule', path: '/provider/providercontrol', requiredText: /Search|Schedule|Preference/i },
  { label: 'tickler', path: '/tickler/ViewTicklerMain', requiredText: /Tickler/i },
  { label: 'consultations', path: '/encounter/IncomingConsultation', requiredText: /Consult/i },
  { label: 'reports', path: '/report/ViewReportindex', requiredText: /Report/i },
  { label: 'inbox', path: '/web/inboxhub/Inboxhub', requiredText: /Incoming Docs|Pending Docs|Doc Upload/i },
];

const protectedRoutes = [
  '/provider/providercontrol',
  '/tickler/ViewTicklerMain',
  '/report/ViewReportindex',
];

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
  return /(ReferenceError|TypeError|SyntaxError|DataTable is not a function|Cannot read|Cannot set|is not defined)/i.test(text);
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

async function gotoApp(page, label, appPath) {
  const response = await page.goto(appUrl(appPath), { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label, status: response ? response.status() : null, url: page.url() });
  return response;
}

async function bodyText(page) {
  return page.locator('body').innerText({ timeout: 10000 }).catch(() => '');
}

async function assertNoErrorPage(page, label) {
  const text = await bodyText(page);
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(text)) {
    findings.push({ label, type: 'error-page', url: page.url(), body: text.replace(/\s+/g, ' ').slice(0, 500) });
  }
  if (!text.trim()) {
    findings.push({ label, type: 'blank-page', url: page.url() });
  }
  return text;
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await gotoApp(page, 'login', '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  if (await page.locator('#pin').count()) {
    await page.locator('#pin').fill(testPin);
  }
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'post-login', url: page.url() });
  const text = await assertNoErrorPage(page, 'post-login');
  if (/login=failed|Login failed|form name="loginForm"/i.test(page.url() + text)) {
    findings.push({ label: 'post-login', type: 'login-failed', url: page.url(), body: text.slice(0, 500) });
  }
  return page;
}

async function checkAuthenticatedRoute(context, route) {
  const page = await context.newPage();
  wirePage(page, route.label);
  const response = await gotoApp(page, route.label, route.path);
  if (!response || !response.ok()) {
    findings.push({ label: route.label, type: 'bad-navigation-status', status: response ? response.status() : null, url: page.url() });
  }
  const text = await assertNoErrorPage(page, route.label);
  if (route.requiredText && !route.requiredText.test(text)) {
    findings.push({ label: route.label, type: 'missing-expected-text', expected: String(route.requiredText), url: page.url(), body: text.slice(0, 500) });
  }
  await page.close();
}

async function checkProtectedRedirect(browser, appPath) {
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  const page = await context.newPage();
  const label = `unauthenticated:${appPath}`;
  wirePage(page, label);
  await gotoApp(page, label, appPath);
  const text = await bodyText(page);
  const atLogin = /login|username|password/i.test(text) || /login|logout/i.test(page.url());
  if (!atLogin) {
    findings.push({ label, type: 'missing-auth-redirect', url: page.url(), body: text.slice(0, 500) });
  }
  await context.close();
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
    for (const appPath of protectedRoutes) {
      await checkProtectedRedirect(browser, appPath);
    }

    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
    const schedulePage = await login(context);
    if (!findings.some((finding) => finding.label === 'post-login' && finding.type === 'login-failed')) {
      for (const route of authenticatedRoutes) {
        await checkAuthenticatedRoute(context, route);
      }
    }
    await schedulePage.close();

    console.log(JSON.stringify({ visited, findings }, null, 2));
    const blockingFindings = findings.filter((finding) => finding.type !== 'dialog');
    if (blockingFindings.length) {
      throw new Error(`application health Playwright check found ${blockingFindings.length} issue(s)`);
    }

    console.log('PASS application health routes rendered without HTTP, auth, or browser failures');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL application health Playwright check');
  console.error(error.stack || error.message);
  process.exit(1);
});
