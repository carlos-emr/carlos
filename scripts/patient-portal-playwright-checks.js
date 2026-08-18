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
const { isIP } = require('node:net');
const path = require('node:path');
const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.PORTAL_BASE_URL || 'http://127.0.0.1:8090');
const testUser = process.env.PORTAL_TEST_USER || 'CarlosPatient';
const testPassword = process.env.PORTAL_TEST_PASSWORD || ['Carlos', '2026', '!!'].join('');
const expectedUser = process.env.PORTAL_EXPECTED_USER || testUser.toLowerCase();
const expectedEmail = process.env.PORTAL_EXPECTED_EMAIL || 'example.patient@example.com';
const changedPassword = ['Carlos', '2027', '!!'].join('');
const mailCommand = process.env.PORTAL_MAIL_COMMAND || '/scripts/mail';
const useDevelopmentMfaCode = process.env.PORTAL_USE_DEVELOPMENT_MFA_CODE === 'true';
const screenshotDir = path.resolve(process.env.PORTAL_SCREENSHOT_DIR || '/tmp');
const chromePath = process.env.CHROME_PATH || '';

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`PORTAL_BASE_URL must use HTTP or HTTPS, got ${parsed.protocol}`);
  }
  if (parsed.username || parsed.password) {
    throw new Error('PORTAL_BASE_URL must not contain embedded credentials');
  }
  const host = parsed.hostname.toLowerCase().replace(/^\[|\]$/g, '');
  const localHosts = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal']);
  const ipv4Parts = isIP(host) === 4 ? host.split('.').map(Number) : null;
  const privateIpv4 = ipv4Parts !== null && (
    ipv4Parts[0] === 10
    || (ipv4Parts[0] === 192 && ipv4Parts[1] === 168)
    || (ipv4Parts[0] === 172 && ipv4Parts[1] >= 16 && ipv4Parts[1] <= 31)
  );
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
  return new URL(portalPathname(portalPath), baseUrl.origin).toString();
}

function portalPathname(portalPath) {
  const basePath = baseUrl.pathname === '/' ? '' : baseUrl.pathname.replace(/\/+$/, '');
  return `${basePath}${portalPath}`;
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
  let expectedRevealFailures = 0;
  const browser = await chromium.launch({
    headless: true,
    ...(chromePath ? { executablePath: chromePath } : {}),
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    permissions: ['clipboard-read', 'clipboard-write'],
  });
  const page = await context.newPage();
  let passwordChanged = false;
  let passwordRestoration = null;

  async function restoreSeededPassword() {
    if (!passwordChanged) {
      return;
    }
    if (passwordRestoration !== null) {
      await passwordRestoration;
      return;
    }
    passwordRestoration = (async () => {
      try {
        await page.goto(portalUrl('/portal/account'), {
          waitUntil: 'networkidle',
          timeout: 30000,
        });
        const cleanupPasswordForm = page.locator('form', {
          has: page.locator('input[name="new_password"]'),
        });
        await cleanupPasswordForm.locator('input[name="current_password"]').fill(changedPassword);
        await cleanupPasswordForm.locator('input[name="new_password"]').fill(testPassword);
        await cleanupPasswordForm.locator('input[name="new_password_confirmation"]').fill(testPassword);
        await Promise.all([
          page.waitForURL((url) => (
            url.pathname === portalPathname('/portal/account')
            && url.searchParams.get('status') === 'password-updated'
          )),
          cleanupPasswordForm.getByRole('button', { name: 'Update password' }).click(),
        ]);
        passwordChanged = false;
      } catch (cleanupError) {
        console.error(`Failed to restore seeded portal password: ${cleanupError.message}`);
      }
    })();
    await passwordRestoration;
  }

  let signalHandled = false;
  for (const [signal, exitCode] of [['SIGINT', 130], ['SIGTERM', 143]]) {
    process.once(signal, () => {
      if (signalHandled) {
        return;
      }
      signalHandled = true;
      void (async () => {
        await restoreSeededPassword();
        await context.close().catch(() => {});
        await browser.close().catch(() => {});
        process.exit(exitCode);
      })();
    });
  }

  page.on('response', (response) => {
    const isExpectedMfaCooldown = response.status() === 429
      && new URL(response.url()).pathname === portalPathname('/auth/mfa/resend');
    const responsePath = new URL(response.url()).pathname;
    const isExpectedRevealFailure = response.status() === 503
      && expectedRevealFailures > 0
      && responsePath.startsWith(portalPathname('/portal/email-passwords/'))
      && responsePath.endsWith('/reveal');
    if (isExpectedRevealFailure) {
      expectedRevealFailures -= 1;
    }
    if (response.status() >= 400 && !isExpectedMfaCooldown && !isExpectedRevealFailure) {
      badResponses.push({ status: response.status(), url: response.url() });
    }
  });
  page.on('console', (message) => {
    const isExpectedMfaCooldownConsoleError = message.text().includes(
      'Failed to load resource: the server responded with a status of 429'
    );
    const isExpectedRevealFailureConsoleError = message.text().includes(
      'Failed to load resource: the server responded with a status of 503'
    );
    if (
      message.type() === 'error'
      && !isExpectedMfaCooldownConsoleError
      && !isExpectedRevealFailureConsoleError
    ) {
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
    assert(
      await page.locator('link[rel="icon"][href$="/static/carlos-logo.png"]').count() === 1,
      'CARLOS favicon is not declared'
    );
    // Four links plus a span for the active locale — the switcher navigates now rather than
    // opening a "language unavailable" modal, so these are no longer buttons.
    assert(await page.locator('.language-switch .text-tab').count() === 5, 'expected five languages');
    assert(
      await page.locator(
        `.language-switch a[href^="${portalPathname('/locale/')}"]`
      ).count() === 4,
      'expected four selectable languages'
    );
    await page.locator(
      `.language-switch a[href^="${portalPathname('/locale/fr')}"]`
    ).click();
    await page.getByRole('heading', { name: 'Sign in' }).waitFor();
    assert(
      await page.locator('.language-switch .text-tab.selected[lang="fr"]').count() === 1,
      'selecting a language did not persist'
    );
    // Back to English so the rest of the run reads the locale it asserts against.
    await page.locator(
      `.language-switch a[href^="${portalPathname('/locale/en')}"]`
    ).click();
    await page.getByRole('heading', { name: 'Sign in' }).waitFor();
    assert(
      await page.locator('.language-switch .text-tab.selected[lang="en"]').count() === 1,
      'could not switch back to English'
    );

    const signInPassword = page.locator('input[name="password"]');
    assert(await signInPassword.getAttribute('type') === 'password', 'password starts visible');
    await page.getByRole('button', { name: 'Show password' }).click();
    assert(await signInPassword.getAttribute('type') === 'text', 'show-password control failed');
    await page.getByRole('button', { name: 'Hide password' }).click();
    assert(await signInPassword.getAttribute('type') === 'password', 'hide-password control failed');

    // The language tabs used to be this page's modal trigger. They navigate now, so the modal's
    // focus trap and focus restoration are exercised through Clinic help instead -- the assertions
    // are about the modal, not about which control opened it.
    const modalTrigger = page.getByRole('button', { name: 'Clinic help' });
    const modal = page.locator('#portal-message-modal');
    await modalTrigger.click();
    await modal.waitFor({ state: 'visible' });
    await modal.getByRole('heading', { name: 'Clinic help' }).waitFor();
    await page.keyboard.press('Tab');
    assert(
      await page.evaluate(() => Boolean(document.activeElement?.closest('#portal-message-modal'))),
      'keyboard focus escaped the open modal'
    );
    await page.keyboard.press('Escape');
    await modal.waitFor({ state: 'hidden' });
    assert(await modalTrigger.evaluate((element) => element === document.activeElement),
      'closing the modal did not restore focus to its trigger');
    await modalTrigger.click();
    await modal.waitFor({ state: 'visible' });
    await modal.locator('[data-modal-close]').click();
    await modal.waitFor({ state: 'hidden' });

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
      page.waitForURL((url) => url.pathname === portalPathname('/auth/login'), { timeout: 30000 }),
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
        capturedMail.recipient === expectedEmail,
        `unexpected MFA recipient ${capturedMail.recipient}`
      );
      assert(
        capturedMail.subject === 'Your CARLOS Patient Portal verification code',
        `unexpected MFA subject ${capturedMail.subject}`
      );
    }
    const resendResponsePromise = page.waitForResponse(
      (response) => new URL(response.url()).pathname === portalPathname('/auth/mfa/resend')
    );
    await page.getByRole('button', { name: 'Resend code' }).click();
    const resendResponse = await resendResponsePromise;
    assert(resendResponse.status() === 429, `expected resend cooldown, got ${resendResponse.status()}`);
    await page.getByRole('alert').filter({ hasText: 'A code was sent recently.' }).waitFor();
    await page.getByRole('heading', { name: 'Verification code' }).waitFor();

    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.locator('input[name="code"]').fill(capturedMail.code);
    await Promise.all([
      page.waitForURL((url) => url.pathname === portalPathname('/portal'), { timeout: 30000 }),
      page.getByRole('button', { name: 'Verify' }).click(),
    ]);

    await page.getByRole('heading', { name: 'Patient portal' }).waitFor();
    await page.locator('.signed-in-user').filter({ hasText: expectedUser }).waitFor();
    await page.screenshot({
      path: screenshotPath('patient-portal-live-desktop'),
      fullPage: true,
    });

    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({
      path: screenshotPath('patient-portal-dashboard-mobile'),
      fullPage: true,
    });

    // The sidebar remains visible at tablet widths. Three minimum-width cards previously made the
    // dashboard 813px wide in a 768px viewport, clipping the final card off-screen.
    await page.setViewportSize({ width: 768, height: 1024 });
    const dashboardTabletLayout = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
    }));
    assert(
      dashboardTabletLayout.documentWidth <= dashboardTabletLayout.viewportWidth + 1,
      `tablet dashboard overflows horizontally: ${dashboardTabletLayout.documentWidth}px > ${dashboardTabletLayout.viewportWidth}px`
    );

    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole('link', { name: 'Account', exact: true }).click();
    await page.waitForURL((url) => url.pathname === portalPathname('/portal/account'));
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
        url.pathname === portalPathname('/portal/account')
        && url.searchParams.get('status') === 'mfa-updated'
      )),
      mfaSettingsForm.getByRole('button', { name: 'Update MFA' }).click(),
    ]);
    await page.getByRole('status').filter({ hasText: 'MFA settings updated.' }).waitFor();

    // A contact change now has two branches, and the browser check covers both.
    //
    // An email change is held until the new mailbox confirms it, so the account keeps its current
    // address -- and therefore its MFA and password-reset destination -- until then. Confirming is
    // not driven here: this run has no SMTP host, so there is no confirmation link to follow.
    // test_portal_pages.py covers redemption; what matters in a browser is that submitting the
    // form does not move the address.
    const contactForm = page.locator('form', {
      has: page.locator('input[name="email"]'),
    });
    const originalEmail = await contactForm.locator('input[name="email"]').inputValue();
    await contactForm.locator('input[name="email"]').fill('playwright.patient@example.com');
    await contactForm.locator('input[name="current_password"]').fill(testPassword);
    await Promise.all([
      page.waitForURL((url) => (
        url.pathname === portalPathname('/portal/account')
        && url.searchParams.get('status') === 'email-confirmation-required'
      )),
      contactForm.getByRole('button', { name: 'Update contact' }).click(),
    ]);
    await page.getByRole('status').filter({
      hasText: 'Check the new email address for a confirmation link.',
    }).waitFor();
    assert(
      await contactForm.locator('input[name="email"]').inputValue() === originalEmail,
      'an unconfirmed email change must not move the account address'
    );

    // A phone-only change also waits for ownership proof. This smoke environment deliberately has
    // no SMS sender, so delivery fails closed and the account keeps the original phone number.
    const originalPhone = await contactForm.locator('input[name="phone_number"]').inputValue();
    await contactForm.locator('input[name="phone_number"]').fill('+1 555 010 9090');
    await contactForm.locator('input[name="current_password"]').fill(testPassword);
    await Promise.all([
      page.waitForURL((url) => (
        url.pathname === portalPathname('/portal/account')
        && url.searchParams.get('status') === 'phone-confirmation-notice-failed'
      )),
      contactForm.getByRole('button', { name: 'Update contact' }).click(),
    ]);
    await page.getByRole('status').filter({
      hasText: 'A confirmation code could not be sent to the new phone number.',
    }).waitFor();
    assert(
      await contactForm.locator('input[name="phone_number"]').inputValue() === originalPhone,
      'an unconfirmed phone change must not move the account phone number'
    );

    const passwordForm = page.locator('form', {
      has: page.locator('input[name="new_password"]'),
    });
    await passwordForm.locator('input[name="current_password"]').fill(testPassword);
    await passwordForm.locator('input[name="new_password"]').fill(changedPassword);
    await passwordForm.locator('input[name="new_password_confirmation"]').fill(changedPassword);
    passwordChanged = true;
    await Promise.all([
      page.waitForURL((url) => (
        url.pathname === portalPathname('/portal/account')
        && url.searchParams.get('status') === 'password-updated'
      )),
      passwordForm.getByRole('button', { name: 'Update password' }).click(),
    ]);
    await page.getByRole('status').filter({ hasText: 'Password updated.' }).waitFor();
    await page.screenshot({
      path: screenshotPath('patient-portal-account-mobile'),
      fullPage: true,
    });
    await page.getByRole('link', { name: 'Help', exact: true }).click();
    await page.waitForURL((url) => url.pathname === portalPathname('/portal/help'));
    await page.getByRole('heading', { name: 'Help' }).waitFor();
    await page.screenshot({
      path: screenshotPath('patient-portal-help-mobile'),
      fullPage: true,
    });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.getByRole('link', { name: 'Email passwords', exact: true }).click();
    await page.waitForURL(
      (url) => url.pathname === portalPathname('/portal/email-passwords')
    );
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
    await page.setViewportSize({ width: 390, height: 844 });
    expectedRevealFailures = 1;
    await page.route('**/portal/email-passwords/*/reveal', (route) => {
      void route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'email password unavailable' }),
      });
    }, { times: 1 });
    await page.getByRole('button', { name: 'Reveal' }).first().click();
    await page.getByRole('button', { name: 'Password could not be revealed.' }).waitFor();
    assert(expectedRevealFailures === 0, 'expected reveal failure response was not observed');
    await page.waitForTimeout(2600);
    await page.getByRole('button', { name: 'Reveal' }).first().waitFor();
    await page.setViewportSize({ width: 1440, height: 1000 });

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
        url.pathname === portalPathname('/portal/email-passwords')
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
      page.waitForURL((url) => url.pathname === portalPathname('/portal/email-passwords')),
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
      page.waitForURL((url) => url.pathname === portalPathname('/portal/email-passwords')),
      page.getByRole('link', { name: 'Previous' }).click(),
    ]);
    await page.locator('input[name="q"]').fill('No such message');
    await Promise.all([
      page.waitForURL((url) => url.searchParams.get('q') === 'No such message'),
      page.getByRole('button', { name: 'Search' }).click(),
    ]);
    await page.getByText('No matching email passwords', { exact: true }).waitFor();
    await Promise.all([
      page.waitForURL((url) => url.pathname === portalPathname('/portal/email-passwords')),
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

    // Leave the documented seeded account reusable for the next smoke-test run.
    await restoreSeededPassword();

    await Promise.all([
      page.waitForURL((url) => url.pathname === portalPathname('/'), { timeout: 30000 }),
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
    await restoreSeededPassword();
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
