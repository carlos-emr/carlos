#!/usr/bin/env node
/*
 * Live browser smoke test for the CARLOS patient portal and local mail capture.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:patient-portal-playwright
 *
 * Optional environment:
 *   PORTAL_BASE_URL=http://127.0.0.1:8090
 *   PORTAL_TEST_USER=CarlosPatient
 *   PORTAL_TEST_PASSWORD=the seeded development password
 *   PORTAL_MAIL_COMMAND=/scripts/mail
 *   PORTAL_SCREENSHOT_DIR=/tmp
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   ALLOW_NON_LOCAL_BASE_URL=true only for an intentional non-production test target
 */

const { execFileSync } = require('node:child_process');
const path = require('node:path');
const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.PORTAL_BASE_URL || 'http://127.0.0.1:8090');
const testUser = process.env.PORTAL_TEST_USER || 'CarlosPatient';
const testPassword = process.env.PORTAL_TEST_PASSWORD || ['Carlos', '2026', '!!'].join('');
const mailCommand = process.env.PORTAL_MAIL_COMMAND || '/scripts/mail';
const screenshotDir = path.resolve(process.env.PORTAL_SCREENSHOT_DIR || '/tmp');
const chromePath = process.env.CHROME_PATH || '';

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`PORTAL_BASE_URL must use HTTP or HTTPS, got ${parsed.protocol}`);
  }
  const host = parsed.hostname.toLowerCase();
  const localHosts = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal']);
  const privateIpv4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  if (
    !localHosts.has(host)
    && !privateIpv4
    && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true'
  ) {
    throw new Error(
      `Refusing non-local PORTAL_BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true only for an intentional test target`
    );
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

function portalUrl(portalPath) {
  if (!portalPath.startsWith('/') || portalPath.startsWith('//')) {
    throw new Error(`Portal path must be root-relative, got ${portalPath}`);
  }
  return new URL(portalPath, baseUrl).toString();
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function runMailCommand(...args) {
  return execFileSync(mailCommand, args, {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
    timeout: 10000,
  });
}

function sleep(milliseconds) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, milliseconds);
}

function readCapturedMfaCode() {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    try {
      const message = runMailCommand('read', 'latest');
      const codeMatch = message.match(/(?:^|\r?\n)(\d{6})(?:\r?\n|$)/);
      const recipientMatch = message.match(/^Envelope-To:\s*(\S+)\s*$/m);
      const subjectMatch = message.match(/^Subject:\s*(.+)\s*$/m);
      if (codeMatch && recipientMatch && subjectMatch) {
        return {
          code: codeMatch[1],
          recipient: recipientMatch[1],
          subject: subjectMatch[1],
        };
      }
    } catch (error) {
      if (attempt === 19) {
        throw error;
      }
    }
    sleep(250);
  }
  throw new Error('Captured MFA email did not arrive within five seconds');
}

function screenshotPath(name) {
  const safeName = name.replace(/[^a-z0-9_-]/gi, '-');
  return path.join(screenshotDir, `${safeName}.png`);
}

(async () => {
  const badResponses = [];
  const browserIssues = [];
  const browser = await chromium.launch({
    headless: true,
    ...(chromePath ? { executablePath: chromePath } : {}),
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const page = await context.newPage();

  page.on('response', (response) => {
    if (response.status() >= 400) {
      badResponses.push({ status: response.status(), url: response.url() });
    }
  });
  page.on('console', (message) => {
    if (message.type() === 'error') {
      browserIssues.push(`console: ${message.text()}`);
    }
  });
  page.on('pageerror', (error) => {
    browserIssues.push(`pageerror: ${error.stack || error.message}`);
  });

  try {
    runMailCommand('clear');
    await page.goto(portalUrl('/'), { waitUntil: 'networkidle', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- portalUrl requires a root-relative path and validateBaseUrl restricts targets to local/private hosts by default

    await page.getByRole('heading', { name: 'Sign in' }).waitFor();
    const logoLoaded = await page.locator('.brand-logo').evaluate(
      (image) => image.complete && image.naturalWidth > 0 && image.naturalHeight > 0
    );
    assert(logoLoaded, 'CARLOS logo did not load');
    assert(await page.locator('.language-switch button').count() === 5, 'expected five languages');

    await page.locator('[data-language-code="fr"]').click();
    const modal = page.locator('#portal-message-modal');
    await modal.waitFor({ state: 'visible' });
    await modal.getByRole('heading', { name: 'Language not implemented' }).waitFor();
    await modal.locator('[data-modal-close]').click();
    await modal.waitFor({ state: 'hidden' });

    await page.locator('input[name="username"]').fill(testUser);
    await page.locator('input[name="password"]').fill(testPassword);
    await Promise.all([
      page.waitForURL(/\/auth\/login$/, { timeout: 30000 }),
      page.getByRole('button', { name: 'Sign in' }).click(),
    ]);
    await page.getByRole('heading', { name: 'Verification code' }).waitFor();

    const capturedMail = readCapturedMfaCode();
    assert(
      capturedMail.recipient === 'example.patient@example.com',
      `unexpected MFA recipient ${capturedMail.recipient}`
    );
    assert(
      capturedMail.subject === 'Your CARLOS Patient Portal verification code',
      `unexpected MFA subject ${capturedMail.subject}`
    );
    await page.locator('input[name="code"]').fill(capturedMail.code);
    await Promise.all([
      page.waitForURL(/\/portal$/, { timeout: 30000 }),
      page.getByRole('button', { name: 'Continue' }).click(),
    ]);

    await page.getByRole('heading', { name: 'Account' }).waitFor();
    await page.locator('.signed-in-user').filter({ hasText: 'carlospatient' }).waitFor();
    await page.screenshot({
      path: screenshotPath('patient-portal-live-desktop'),
      fullPage: true,
    });

    await page.getByRole('link', { name: 'Email passwords' }).click();
    await page.waitForURL(/\/portal\/email-passwords$/);
    await page.getByRole('heading', { name: 'Email passwords' }).waitFor();
    assert(
      await page.locator('.email-password-table tbody tr').count() >= 3,
      'expected seeded email-password records'
    );

    await page.setViewportSize({ width: 390, height: 844 });
    await page.reload({ waitUntil: 'networkidle' });
    const layout = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
    }));
    assert(
      layout.documentWidth <= layout.viewportWidth + 1,
      `mobile page overflows horizontally: ${layout.documentWidth}px > ${layout.viewportWidth}px`
    );
    const logoutBox = await page.getByRole('button', { name: 'Logout' }).boundingBox();
    assert(logoutBox !== null, 'mobile logout button is not visible');
    assert(
      logoutBox.x + logoutBox.width <= 390,
      'mobile logout button extends beyond the viewport'
    );
    await page.screenshot({
      path: screenshotPath('patient-portal-live-mobile'),
      fullPage: true,
    });

    await Promise.all([
      page.waitForURL((url) => url.pathname === '/', { timeout: 30000 }),
      page.getByRole('button', { name: 'Logout' }).click(),
    ]);
    await page.getByRole('heading', { name: 'Sign in' }).waitFor();

    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses)}`);
    assert(browserIssues.length === 0, `browser errors: ${JSON.stringify(browserIssues)}`);
    console.log('Patient portal Playwright smoke test passed');
    console.log(`Desktop screenshot: ${screenshotPath('patient-portal-live-desktop')}`);
    console.log(`Mobile screenshot: ${screenshotPath('patient-portal-live-mobile')}`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
