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
const useDevelopmentMfaCode = process.env.PORTAL_USE_DEVELOPMENT_MFA_CODE === 'true';
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
    permissions: ['clipboard-read', 'clipboard-write'],
  });
  const page = await context.newPage();

  page.on('response', (response) => {
    const isExpectedMfaCooldown = response.status() === 429
      && new URL(response.url()).pathname === '/auth/mfa/resend';
    if (response.status() >= 400 && !isExpectedMfaCooldown) {
      badResponses.push({ status: response.status(), url: response.url() });
    }
  });
  page.on('console', (message) => {
    const isExpectedMfaCooldownConsoleError = message.text().includes(
      'Failed to load resource: the server responded with a status of 429'
    );
    if (message.type() === 'error' && !isExpectedMfaCooldownConsoleError) {
      browserIssues.push(`console: ${message.text()}`);
    }
  });
  page.on('pageerror', (error) => {
    browserIssues.push(`pageerror: ${error.stack || error.message}`);
  });

  try {
    if (!useDevelopmentMfaCode) {
      runMailCommand('clear');
    }
    await page.goto(portalUrl('/'), { waitUntil: 'networkidle', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- portalUrl requires a root-relative path and validateBaseUrl restricts targets to local/private hosts by default

    await page.getByRole('heading', { name: 'Sign in' }).waitFor();
    const logoLoaded = await page.locator('.brand-logo').evaluate(
      (image) => image.complete && image.naturalWidth > 0 && image.naturalHeight > 0
    );
    assert(logoLoaded, 'CARLOS logo did not load');
    assert(await page.locator('.language-switch button').count() === 5, 'expected five languages');

    const languageTrigger = page.locator('[data-language-code="fr"]');
    await languageTrigger.click();
    const modal = page.locator('#portal-message-modal');
    await modal.waitFor({ state: 'visible' });
    await modal.getByRole('heading', { name: 'Language not implemented' }).waitFor();
    await page.keyboard.press('Tab');
    assert(
      await page.evaluate(() => Boolean(document.activeElement?.closest('#portal-message-modal'))),
      'keyboard focus escaped the open modal'
    );
    await page.keyboard.press('Escape');
    await modal.waitFor({ state: 'hidden' });
    assert(await languageTrigger.evaluate((element) => element === document.activeElement),
      'closing the modal did not restore focus to its trigger');

    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({
      path: screenshotPath('patient-portal-sign-in-mobile'),
      fullPage: true,
    });
    await page.getByRole('link', { name: 'Activate account' }).click();
    await page.getByRole('heading', { name: 'Activate your account' }).waitFor();
    assert(
      await page.locator('input[name="date_of_birth"][type="date"]').count() === 1,
      'activation date-of-birth control is missing'
    );
    await page.setViewportSize({ width: 390, height: 844 });
    const activationMobileLayout = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
    }));
    assert(
      activationMobileLayout.documentWidth <= activationMobileLayout.viewportWidth + 1,
      `mobile activation page overflows horizontally: ${activationMobileLayout.documentWidth}px > ${activationMobileLayout.viewportWidth}px`
    );
    await page.screenshot({
      path: screenshotPath('patient-portal-activation-mobile'),
      fullPage: true,
    });
    await page.getByRole('link', { name: 'Back to sign in' }).click();

    await page.getByRole('link', { name: 'Forgot username or password?' }).click();
    await page.getByRole('heading', { name: 'Reset your password' }).waitFor();
    const resetMobileLayout = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
    }));
    assert(
      resetMobileLayout.documentWidth <= resetMobileLayout.viewportWidth + 1,
      `mobile password-reset page overflows horizontally: ${resetMobileLayout.documentWidth}px > ${resetMobileLayout.viewportWidth}px`
    );
    await page.screenshot({
      path: screenshotPath('patient-portal-password-reset-mobile'),
      fullPage: true,
    });
    await page.getByRole('link', { name: 'Back to sign in' }).click();
    await page.setViewportSize({ width: 1440, height: 1000 });

    await page.locator('input[name="username"]').fill(testUser);
    await page.locator('input[name="password"]').fill(testPassword);
    await Promise.all([
      page.waitForURL(/\/auth\/login$/, { timeout: 30000 }),
      page.getByRole('button', { name: 'Sign in' }).click(),
    ]);
    await page.getByRole('heading', { name: 'Verification code' }).waitFor();

    await page.setViewportSize({ width: 390, height: 844 });
    const mfaMobileLayout = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
    }));
    assert(
      mfaMobileLayout.documentWidth <= mfaMobileLayout.viewportWidth + 1,
      `mobile MFA page overflows horizontally: ${mfaMobileLayout.documentWidth}px > ${mfaMobileLayout.viewportWidth}px`
    );
    assert(
      await page.locator('.mfa-method-switch input[type="radio"]').count() === 2,
      'expected email and SMS MFA delivery options'
    );
    assert(
      await page.locator('.mfa-method-switch input[value="sms"]:disabled').count() === 1,
      'SMS MFA must stay disabled until a sender is configured'
    );
    await page.getByRole('button', { name: 'Help' }).click();
    await modal.waitFor({ state: 'visible' });
    await modal.getByRole('heading', { name: 'Clinic help' }).waitFor();
    await modal.locator('[data-modal-close]').click();
    await modal.waitFor({ state: 'hidden' });
    await page.screenshot({
      path: screenshotPath('patient-portal-mfa-mobile'),
      fullPage: true,
    });

    const capturedMail = useDevelopmentMfaCode
      ? {
          code: await page.locator('[data-development-mfa-code]').getAttribute(
            'data-development-mfa-code'
          ),
        }
      : readCapturedMfaCode();
    assert(capturedMail.code, 'MFA code was not available');
    if (!useDevelopmentMfaCode) {
      assert(
        capturedMail.recipient === 'example.patient@example.com',
        `unexpected MFA recipient ${capturedMail.recipient}`
      );
      assert(
        capturedMail.subject === 'Your CARLOS Patient Portal verification code',
        `unexpected MFA subject ${capturedMail.subject}`
      );
    }
    const resendResponsePromise = page.waitForResponse(
      (response) => new URL(response.url()).pathname === '/auth/mfa/resend'
    );
    await page.getByRole('button', { name: 'Resend code' }).click();
    const resendResponse = await resendResponsePromise;
    assert(resendResponse.status() === 429, `expected resend cooldown, got ${resendResponse.status()}`);
    await page.getByRole('alert').filter({ hasText: 'A code was sent recently.' }).waitFor();
    await page.getByRole('heading', { name: 'Verification code' }).waitFor();

    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.locator('input[name="code"]').fill(capturedMail.code);
    await Promise.all([
      page.waitForURL(/\/portal$/, { timeout: 30000 }),
      page.getByRole('button', { name: 'Verify' }).click(),
    ]);

    await page.getByRole('heading', { name: 'Patient portal' }).waitFor();
    await page.locator('.signed-in-user').filter({ hasText: 'carlospatient' }).waitFor();
    await page.screenshot({
      path: screenshotPath('patient-portal-live-desktop'),
      fullPage: true,
    });

    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({
      path: screenshotPath('patient-portal-dashboard-mobile'),
      fullPage: true,
    });
    await page.getByRole('link', { name: 'Account', exact: true }).click();
    await page.waitForURL(/\/portal\/account$/);
    await page.getByRole('heading', { name: 'Account' }).waitFor();
    assert(
      await page.locator('input[name="new_password_confirmation"][required]').count() === 1,
      'account password confirmation is missing'
    );
    assert(
      await page.locator('select[name="preferred_mfa_method"] option[value="sms"]:disabled').count()
        === 1,
      'account SMS option must reflect the unavailable test sender'
    );
    const mfaSettingsForm = page.locator('form', {
      has: page.locator('select[name="preferred_mfa_method"]'),
    });
    await mfaSettingsForm.locator('select[name="preferred_mfa_method"]').selectOption('email');
    await mfaSettingsForm.locator('input[name="current_password"]').fill(testPassword);
    await Promise.all([
      page.waitForURL((url) => (
        url.pathname === '/portal/account'
        && url.searchParams.get('status') === 'mfa-updated'
      )),
      mfaSettingsForm.getByRole('button', { name: 'Update MFA' }).click(),
    ]);
    await page.getByRole('status').filter({ hasText: 'MFA settings updated.' }).waitFor();
    await page.screenshot({
      path: screenshotPath('patient-portal-account-mobile'),
      fullPage: true,
    });
    await page.getByRole('link', { name: 'Help', exact: true }).click();
    await page.waitForURL(/\/portal\/help$/);
    await page.getByRole('heading', { name: 'Help' }).waitFor();
    await page.screenshot({
      path: screenshotPath('patient-portal-help-mobile'),
      fullPage: true,
    });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.getByRole('link', { name: 'Email passwords', exact: true }).click();
    await page.waitForURL(/\/portal\/email-passwords$/);
    await page.getByRole('heading', { name: 'Email passwords' }).waitFor();
    assert(
      await page.locator('select[name="provider"]').count() === 1
        && await page.locator('input[name="date_from"][type="date"]').count() === 1
        && await page.locator('input[name="date_to"][type="date"]').count() === 1,
      'email-password filters are incomplete'
    );
    assert(
      await page.locator('.email-password-table tbody tr').count() >= 3,
      'expected seeded email-password records'
    );
    assert(
      await page.getByRole('button', { name: 'Reveal' }).count() >= 3,
      'expected Reveal controls for seeded email-password records'
    );
    assert(
      await page.locator('.copy-action:visible').count() === 0,
      'Copy controls must stay hidden before an explicit reveal'
    );
    await page.getByRole('button', { name: 'Reveal' }).first().click();
    await page.getByRole('button', { name: 'Copy' }).first().waitFor();
    const firstPassphrase = (await page.locator('.copyable-password').first().textContent() || '').trim();
    await page.getByRole('button', { name: 'Copy' }).first().click();
    await page.getByRole('button', { name: 'Copied' }).waitFor();
    const clipboardPassphrase = await page.evaluate(() => navigator.clipboard.readText());
    assert(
      clipboardPassphrase === firstPassphrase,
      'Copy control did not write the displayed passphrase'
    );
    await page.evaluate(() => {
      Object.defineProperty(navigator, 'clipboard', {
        configurable: true,
        value: {
          writeText: () => Promise.reject(new Error('clipboard denied by test')),
        },
      });
    });
    const secondPasswordRow = page.locator('.email-password-table tbody tr').nth(1);
    await secondPasswordRow.getByRole('button', { name: 'Reveal' }).click();
    await secondPasswordRow.getByRole('button', { name: 'Copy' }).click();
    await secondPasswordRow.getByRole('button', { name: 'Select and copy manually' }).waitFor();
    assert(
      await secondPasswordRow.locator('.copyable-password').evaluate(
        (element) => window.getSelection().toString() === element.textContent.trim()
      ),
      'clipboard denial fallback did not select the displayed passphrase'
    );

    await page.locator('input[name="q"]').fill('Care');
    await Promise.all([
      page.waitForURL((url) => (
        url.pathname === '/portal/email-passwords'
        && url.searchParams.get('q') === 'Care'
      )),
      page.getByRole('button', { name: 'Search' }).click(),
    ]);
    assert(
      await page.locator('.email-password-table tbody tr').count() === 1,
      'Care search should return exactly one seeded email-password record'
    );
    await page.getByText('Care plan password', { exact: true }).waitFor();
    assert(
      await page.getByText('Referral package password', { exact: true }).count() === 0,
      'Care search returned a non-matching email-password record'
    );
    await Promise.all([
      page.waitForURL(/\/portal\/email-passwords$/),
      page.getByRole('link', { name: 'Clear filters' }).click(),
    ]);
    await Promise.all([
      page.waitForURL((url) => url.searchParams.get('page') === '2'),
      page.getByRole('link', { name: 'Next' }).click(),
    ]);
    await page.getByText('Page 2 of 2', { exact: true }).waitFor();
    assert(
      await page.locator('.email-password-table tbody tr').count() === 2,
      'second page should contain the final two seeded records'
    );
    await Promise.all([
      page.waitForURL(/\/portal\/email-passwords$/),
      page.getByRole('link', { name: 'Previous' }).click(),
    ]);
    await page.locator('input[name="q"]').fill('No such message');
    await Promise.all([
      page.waitForURL((url) => url.searchParams.get('q') === 'No such message'),
      page.getByRole('button', { name: 'Search' }).click(),
    ]);
    await page.getByText('No matching email passwords', { exact: true }).waitFor();
    await Promise.all([
      page.waitForURL(/\/portal\/email-passwords$/),
      page.getByRole('link', { name: 'Clear filters' }).click(),
    ]);

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
    console.log(`Sign-in mobile screenshot: ${screenshotPath('patient-portal-sign-in-mobile')}`);
    console.log(`Activation mobile screenshot: ${screenshotPath('patient-portal-activation-mobile')}`);
    console.log(`Password-reset mobile screenshot: ${screenshotPath('patient-portal-password-reset-mobile')}`);
    console.log(`MFA mobile screenshot: ${screenshotPath('patient-portal-mfa-mobile')}`);
    console.log(`Desktop screenshot: ${screenshotPath('patient-portal-live-desktop')}`);
    console.log(`Dashboard mobile screenshot: ${screenshotPath('patient-portal-dashboard-mobile')}`);
    console.log(`Account mobile screenshot: ${screenshotPath('patient-portal-account-mobile')}`);
    console.log(`Help mobile screenshot: ${screenshotPath('patient-portal-help-mobile')}`);
    console.log(`Mobile screenshot: ${screenshotPath('patient-portal-live-mobile')}`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
