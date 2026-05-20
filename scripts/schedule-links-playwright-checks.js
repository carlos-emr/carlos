#!/usr/bin/env node
/*
 * Browser regression checks for high-value schedule-page links.
 *
 * The script follows the same links a user sees after login, opening the
 * schedule top-nav links for Tickler, Consultations, Scratch Pad, Reports,
 * Inbox, and eDoc. It fails on unexpected HTTP errors, CARLOS error pages, and
 * severe browser JavaScript failures.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/schedule-links-playwright-checks.js
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

const scheduleLinks = [
  { label: 'schedule-tickler-link', selector: "a[onclick*='/carlos/tickler/ViewTicklerMain']" },
  { label: 'schedule-consultations-link', selector: "a[onclick*='/carlos/encounter/IncomingConsultation']" },
  { label: 'schedule-scratch-link', selector: "a[onclick*='/carlos/Scratch']" },
  { label: 'schedule-report-link', selector: "a[onclick*='/carlos/report/ViewReportindex']" },
  { label: 'schedule-inbox-link', selector: "a[onclick*='/carlos/web/inboxhub/Inboxhub']" },
  { label: 'schedule-edoc-link', selector: "a[onclick*='/carlos/documentManager/ViewDocumentReport']" },
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
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${appPath}`.replace(/\/{2,}/g, '/');
  url.search = '';
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
  return /(ReferenceError|TypeError|SyntaxError|DataTable is not a function|redeclaration|Cannot read|Cannot set)/i.test(text);
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

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'schedule');
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'schedule', url: page.url() });
  await assertNoErrorPage(page, 'schedule');
  return page;
}

async function clickScheduleLink(context, schedulePage, linkSpec) {
  const locator = schedulePage.locator(linkSpec.selector).first();
  if (!await locator.count()) {
    findings.push({ label: linkSpec.label, type: 'missing-user-link', selector: linkSpec.selector });
    return;
  }

  const existingPages = context.pages();
  const popupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);
  await locator.click();
  let targetPage = await popupPromise;

  if (!targetPage) {
    const currentPages = context.pages();
    targetPage = currentPages.find((page) => !existingPages.includes(page)) || currentPages[currentPages.length - 1];
  }

  wirePage(targetPage, linkSpec.label);
  await targetPage.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  await targetPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: linkSpec.label, url: targetPage.url() });
  await assertNoErrorPage(targetPage, linkSpec.label);
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
    const schedulePage = await login(context);

    for (const linkSpec of scheduleLinks) {
      await clickScheduleLink(context, schedulePage, linkSpec);
    }

    console.log(JSON.stringify({ visited, findings }, null, 2));

    const blockingFindings = findings.filter((finding) => finding.type !== 'dialog');
    if (blockingFindings.length) {
      throw new Error(`schedule link browser check found ${blockingFindings.length} issue(s)`);
    }

    console.log('PASS schedule links rendered without HTTP or browser console failures');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL schedule link Playwright check');
  console.error(error.stack || error.message);
  process.exit(1);
});
