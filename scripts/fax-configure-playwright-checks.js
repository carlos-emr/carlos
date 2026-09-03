#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/**
 * Playwright regression check for Administration > Faxes > Configure Fax (SRFax).
 *
 * Pins the operator path a tester follows to set up SRFax faxing, end to end:
 *   1. log in, open Administration, expand the "Faxes" nav section and click
 *      "Configure Fax" (the page loads inside the admin shell's iframe);
 *   2. the page explains the three SRFax values without ambiguity — the field
 *      that authenticates is labelled "SRFax Account Number" (not "Username"),
 *      the email field is labelled as the sender/notification address, no
 *      middleware-era copy is visible;
 *   3. "Test SRFax connection" posts the entered values (nothing saved) and
 *      renders a result: a failure message for the fake defaults, or success
 *      when SRFAX_LIVE=true and real credentials are supplied via env;
 *   4. Save persists the row and a reload shows the values back with the
 *      password masked, never echoed.
 *
 * Configuration is entirely environment driven and NOTHING here is a real
 * credential. The SRFAX_* defaults are deliberately fake so the page checks run
 * anywhere; the connection test then asserts a well-formed failure. To exercise
 * the live probe against a development (non-PHI) SRFax account:
 *
 *   set -a; . /secure/path/srfax.env; set +a     # SRFAX_ACCESS_ID, SRFAX_PASS, SRFAX_USER, SRFAX_FAX_NUMBER
 *   SRFAX_LIVE=true npm run test:fax-configure-playwright
 *
 * Environment:
 *   BASE_URL (loopback only unless ALLOW_NON_LOCAL_BASE_URL=true), TEST_USER,
 *   TEST_PASSWORD, TEST_PIN, CHROME_PATH, FAX_CONFIG_SCREENSHOT_DIR (default /tmp),
 *   SRFAX_ACCESS_ID (account number), SRFAX_PASS, SRFAX_USER (login email, used as
 *   the sender/notification email), SRFAX_FAX_NUMBER (10 digits), SRFAX_LIVE.
 *
 * Never prints any SRFAX_* value. Screenshots go to FAX_CONFIG_SCREENSHOT_DIR
 * (default /tmp) but are only taken when they cannot carry a real account: fake
 * mode with no real account stored on the page. In SRFAX_LIVE=true mode, or when
 * the page already shows a real account, screenshots are skipped unless
 * FAX_CONFIG_SCREENSHOTS=always is set, so CI artifacts and shared dev runs never
 * pick up a real account number, sender email, or a filled password field.
 *
 * SIDE EFFECT: the save step leaves the fax_config row configured with the
 * supplied (or fake) account values and the gateway ENABLED with polling on, the
 * same end state an operator reaches. With fake defaults the scheduler will log
 * SRFax authentication failures until the row is corrected or disabled. To keep a
 * default run from clobbering a real configuration, the save step only runs when
 * the page shows no account yet, the stored account number is this check's fake
 * one, SRFAX_LIVE=true (you supplied the real values), or
 * FAX_CONFIG_ALLOW_OVERWRITE=true; otherwise it is reported as SKIP (not PASS) and
 * the connection-test and guidance checks still run.
 */

'use strict';

const { chromium } = require('playwright');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

// Fake-by-default SRFax values: clearly not a real account. The password is
// assembled from fragments so no literal "password-looking" string is committed.
const FAKE_ACCESS_ID = '000000';
const FAKE_PASS = ['playwright', 'fake', 'srfax', 'pw'].join('-');
const FAKE_EMAIL = 'fax-config-check@example.invalid';
const FAKE_FAX_NUMBER = '5555550100';

const ACCOUNT_NAME_MARKER = 'Playwright SRFax check';

// The shared validateBaseUrl() restricts the host; additionally refuse a URL that
// carries userinfo, so a credential-bearing BASE_URL can never reach Playwright's
// navigation or the failure diagnostics (which print request URLs).
function rejectEmbeddedCredentials(parsed) {
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not contain embedded credentials (user:pass@host)');
  }
  return parsed;
}

const config = {
  baseUrl: rejectEmbeddedCredentials(validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos')),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.FAX_CONFIG_SCREENSHOT_DIR || '/tmp',
  allowOverwrite: process.env.FAX_CONFIG_ALLOW_OVERWRITE === 'true',
  screenshotsAlways: process.env.FAX_CONFIG_SCREENSHOTS === 'always',
  srfax: {
    accessId: process.env.SRFAX_ACCESS_ID || FAKE_ACCESS_ID,
    pass: process.env.SRFAX_PASS || FAKE_PASS,
    email: process.env.SRFAX_USER || FAKE_EMAIL,
    faxNumber: process.env.SRFAX_FAX_NUMBER || FAKE_FAX_NUMBER,
    live: process.env.SRFAX_LIVE === 'true',
  },
};

if (config.srfax.live) {
  // Live mode saves and enables the gateway with these values: all four must be real,
  // otherwise a fake sender email or fax number would be persisted next to real credentials.
  const missing = ['SRFAX_ACCESS_ID', 'SRFAX_PASS', 'SRFAX_USER', 'SRFAX_FAX_NUMBER']
    .filter((name) => !process.env[name]);
  assert(missing.length === 0, `SRFAX_LIVE=true requires ${missing.join(', ')} to be set in the environment`);
}

const results = [];
const PASSWORD_MASK = '**********';
const TEST_CONNECTION_TIMEOUT_MS = 90000; // client-side SRFax timeouts are 30s connect / 60s response

function record(name, passed, details) {
  results.push({ name, passed, details });
  console.log(`${passed ? 'PASS' : 'FAIL'} ${name}${details && !passed ? `: ${details}` : ''}`);
}

// A skipped step is neither PASS nor FAIL: it is reported as SKIP with the reason so a
// run can never claim coverage it did not exercise.
function skip(name, reason) {
  results.push({ name, passed: true, skipped: true, details: reason });
  console.log(`SKIP ${name}: ${reason}`);
}

async function step(name, fn) {
  try {
    await fn();
    record(name, true);
  } catch (error) {
    record(name, false, error.message);
    throw error;
  }
}

// Screenshots of this page can carry an account number, a sender email and a filled
// (masked) password field. Take them only when no real account can be on the page:
// fake mode with an empty/fake stored account. Live mode, or a real stored account,
// needs the explicit FAX_CONFIG_SCREENSHOTS=always opt-in.
function screenshotAllowed(existingAccountNumber) {
  if (config.screenshotsAlways) {
    return true;
  }
  if (config.srfax.live) {
    return false;
  }
  return existingAccountNumber === '' || existingAccountNumber === FAKE_ACCESS_ID;
}

let screenshotsSkipped = 0;
async function shot(page, existingAccountNumber, name) {
  if (!screenshotAllowed(existingAccountNumber)) {
    screenshotsSkipped += 1;
    return null;
  }
  return screenshot(page, config.screenshotDir, name);
}

function digitsOnly(value) {
  return String(value).replace(/\D/g, '');
}

// The Configure Fax form is a stable contract (see scripts/e2e/fax/lib.js):
// these ids/names must not change without updating both scripts.
async function fillSrfaxForm(form, srfax) {
  await form.locator('#faxUser').fill(srfax.accessId);
  await form.locator('#faxPasswd').fill(srfax.pass);
  await form.locator('#faxNumber').fill(srfax.faxNumber);
  await form.locator('#senderEmail').fill(srfax.email);
  await form.locator('#accountName').fill(ACCOUNT_NAME_MARKER);
  await form.locator('#on').check();
  const downloadCheckbox = form.locator('#downloadCheckbox');
  if (!(await downloadCheckbox.isChecked())) {
    await downloadCheckbox.check();
  }
}

// fill() does not fire keypress, and the page only enables Save on keypress /
// select change / radio click. Typing one extra character then deleting it is
// the operator-faithful way to arm the button (radio clicks above also arm it,
// but do not rely on that ordering).
async function armSaveButton(form) {
  const accountName = form.locator('#accountName');
  await accountName.press('End');
  await accountName.pressSequentially(' ');
  await accountName.press('Backspace');
  await form.locator('#submit').waitFor({ state: 'visible', timeout: 10000 });
  assert(!(await form.locator('#submit').isDisabled()), 'Save Configuration did not enable after editing the form');
}

const recorder = createRecorder();

async function main() {
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ viewport: { width: 1360, height: 1100 } });
  let page;
  const existing = { accountNumber: '', accountName: '' };

  try {
    await step('login as an administrator', async () => {
      page = await login(context, config, recorder);
      await assertNotErrorPage(page, 'post-login page');
    });

    // The admin shell dismisses nothing itself, but wirePage() dismisses every
    // dialog it sees — and dismissing a beforeunload prompt CANCELS navigation.
    // Accept beforeunload so the reload after save behaves like a real click on
    // "Leave"; every other dialog keeps the recorder's dismiss behaviour.
    page.on('dialog', async (dialog) => {
      if (dialog.type() === 'beforeunload') {
        await dialog.accept().catch(() => {});
      }
    });

    await step('navigate Administration > Faxes > Configure Fax', async () => {
      await gotoApp(page, config.baseUrl, '/administration');
      await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      await assertNotErrorPage(page, 'Administration');

      // "Faxes" is a collapsed Bootstrap accordion section; its links are in the
      // DOM but not clickable until an operator opens it.
      const faxesSection = page.locator('button[data-bs-target="#collapseFifteen"]').first();
      await faxesSection.waitFor({ state: 'visible', timeout: 20000 });
      assert(/Faxes/i.test(await faxesSection.innerText()), 'The #collapseFifteen accordion is not the Faxes section');
      await faxesSection.click();

      const configureLink = page.locator('a.xlink[rel$="/admin/ViewConfigureFax"]').first();
      await configureLink.waitFor({ state: 'visible', timeout: 20000 });
      assert(/Configure Fax/i.test(await configureLink.innerText()), 'Configure Fax link text changed');
      await configureLink.click();

      // leftNav.jspf injects <iframe id="myFrame"> into #dynamic-content and
      // points it at the gated /admin/ViewConfigureFax route.
      await page.locator('#dynamic-content iframe#myFrame').waitFor({ state: 'attached', timeout: 20000 });
      const frame = page.frameLocator('#myFrame');
      await frame.locator('#configFrm').waitFor({ state: 'visible', timeout: 30000 });
      await frame.locator('#faxUser').waitFor({ state: 'visible', timeout: 30000 });

      // Snapshot what an operator would see before this check touches anything, to
      // decide below whether saving is safe (never clobber a real account by default).
      existing.accountNumber = (await frame.locator('#faxUser').inputValue()).trim();
      existing.accountName = (await frame.locator('#accountName').inputValue()).trim();
    });

    const frame = page.frameLocator('#myFrame');

    await step('page explains the SRFax values without ambiguity', async () => {
      const text = await frame.locator('body').innerText();

      // The authenticating field is the ACCOUNT NUMBER, and says so.
      assert(/SRFax Account Number/.test(text), 'Missing "SRFax Account Number" label');
      assert(!/SRFax Username/.test(text), 'Stale "SRFax Username" label still rendered');
      assert(/not your login email/i.test(text), 'Account number help does not warn against the login email');

      // The "What you need" primer names all three values and disambiguates the email.
      assert(/What you need from SRFax/.test(text), 'Missing "What you need from SRFax" primer');
      assert(/login email is NOT used to sign in to the API/.test(text), 'Missing login-email disambiguation');

      // Email is the sender/notification address, not a credential.
      assert(/Sender \/ Notification Email/.test(text), 'Missing "Sender / Notification Email" label');
      assert(/not used to authenticate/i.test(text), 'Sender email help does not say it is not used to authenticate');

      // Fax number format + password sentinel semantics are explained.
      assert(/Your SRFax Fax Number/.test(text), 'Missing "Your SRFax Fax Number" label');
      assert(/10-digit fax number/.test(text), 'Missing 10-digit fax number hint');
      assert(/Leave the stars unchanged to keep the saved password/.test(text), 'Missing password mask hint');

      // Middleware-era copy must not leak into the SRFax-only page.
      const visibleMiddleware = await frame.locator('#middlewareFields').isVisible();
      assert(!visibleMiddleware, 'Middleware relay fields are visible on the SRFax-only page');
      assert(!/Choose how to connect/.test(text), 'Stale "Choose how to connect" copy still rendered');
      assert(!/Required for both provider types/.test(text), 'Stale "both provider types" copy still rendered');
      assert(/connects directly to the SRFax API/.test(text), 'Missing direct-API note');

      // The connection test control is present and the account-number input is numeric-friendly.
      await frame.locator('#testSrfaxConnectionBtn').waitFor({ state: 'visible', timeout: 10000 });
      assert((await frame.locator('#faxUser').getAttribute('inputmode')) === 'numeric', 'Account number input is not inputmode=numeric');

      await shot(page, existing.accountNumber, 'fax-config-page');
    });

    await step('test connection reports a result before anything is saved', async () => {
      await fillSrfaxForm(frame, config.srfax);
      await shot(page, existing.accountNumber, 'fax-config-filled');

      const configureCalls = recorder.requestLog.filter((entry) => /admin\/ManageFax/.test(entry.url)).length;
      await frame.locator('#testSrfaxConnectionBtn').click();

      const result = frame.locator('#testConnectionResult');
      await result.waitFor({ state: 'visible', timeout: 10000 });
      // Wait for the probe to settle: the "Contacting SRFax..." placeholder is replaced
      // by a success or failure message.
      await page.waitForFunction(
        () => {
          const iframe = document.getElementById('myFrame');
          const el = iframe && iframe.contentDocument && iframe.contentDocument.getElementById('testConnectionResult');
          return !!el && el.textContent.trim() !== '' && !/Contacting SRFax/.test(el.textContent);
        },
        null,
        { timeout: TEST_CONNECTION_TIMEOUT_MS },
      );
      const message = (await result.innerText()).trim();
      const classes = (await result.getAttribute('class')) || '';

      // The probe must have gone to the server as a POST (the verb gate 405s a GET).
      const probeCalls = recorder.requestLog.filter((entry) => /admin\/ManageFax/.test(entry.url));
      assert(probeCalls.length > configureCalls, 'No request to /admin/ManageFax was issued by the Test connection button');
      const last = probeCalls[probeCalls.length - 1];
      assert(last.method === 'POST', `Test connection used ${last.method}, expected POST`);
      assert(last.status === 200, `Test connection returned HTTP ${last.status}`);

      // Never echo credentials: the visible result must not contain the password.
      assert(!message.includes(config.srfax.pass), 'Test connection result echoed the password');

      if (config.srfax.live) {
        assert(/text-success/.test(classes), `Live SRFax connection test did not succeed: "${message.replace(config.srfax.accessId, '<account>')}"`);
        assert(/Connection successful/.test(message), 'Live success message missing');
      } else {
        assert(/text-danger/.test(classes), `Fake credentials unexpectedly reported success: "${message}"`);
        assert(/Connection failed/.test(message), `Unexpected failure wording: "${message}"`);
      }

      // Nothing was saved by the test: Save is still the only persisting control.
      assert(!(await frame.locator('#msg').isVisible()), 'Save alert appeared during a connection test');
      await shot(page, existing.accountNumber, 'fax-config-test-result');

      // A result only describes the credentials that were tested: editing the account
      // number must clear it, so a stale "successful" line can never sit beside unverified
      // values. Restore the value afterwards so the save step persists the tested account.
      await frame.locator('#faxUser').fill(`${config.srfax.accessId}9`);
      await result.waitFor({ state: 'hidden', timeout: 5000 });
      assert((await result.innerText()).trim() === '', 'Connection result text survived a credential edit');
      await frame.locator('#faxUser').fill(config.srfax.accessId);
      assert(!(await result.isVisible()), 'Connection result reappeared after restoring the account number');
    });

    // Saving overwrites the single fax_config row. Safe cases: no account configured yet,
    // the row holds this check's FAKE account number (a live run leaves the real number
    // behind, so the account-name marker alone is NOT proof of ownership), live mode (the
    // operator supplied the real values), or an explicit opt-in.
    const saveIsSafe = existing.accountNumber === ''
      || existing.accountNumber === FAKE_ACCESS_ID
      || config.srfax.live
      || config.allowOverwrite;
    const saveStep = 'save persists the account and masks the password on reload';
    if (!saveIsSafe) {
      skip(saveStep, 'an existing fax account is configured; set FAX_CONFIG_ALLOW_OVERWRITE=true '
        + '(or SRFAX_LIVE=true with real values) to let this check overwrite it');
    }

    // The skip path never enters step(), so it can never be recorded as a PASS.
    await (saveIsSafe ? step : async () => {})(saveStep, async () => {
      await armSaveButton(frame);
      await frame.locator('#submit').click();

      const alert = frame.locator('#msg');
      await alert.waitFor({ state: 'visible', timeout: 30000 });
      const alertText = (await alert.innerText()).trim();
      const alertClass = (await alert.getAttribute('class')) || '';
      assert(/alert-success/.test(alertClass), `Save did not succeed: "${alertText}"`);
      assert(!alertText.includes(config.srfax.pass), 'Save response echoed the password');
      await shot(page, existing.accountNumber, 'fax-config-saved');

      // Reload the direct route (the same gated action the nav iframe used) and
      // confirm the persisted values come back, password masked.
      const direct = await context.newPage();
      wirePage(direct, 'configure-fax-direct', recorder);
      await gotoApp(direct, config.baseUrl, '/admin/ViewConfigureFax');
      await direct.locator('#configFrm').waitFor({ state: 'visible', timeout: 30000 });
      await assertNotErrorPage(direct, 'Configure Fax direct route');

      assert((await direct.locator('#faxUser').inputValue()) === config.srfax.accessId, 'Account number did not persist');
      assert((await direct.locator('#faxNumber').inputValue()) === digitsOnly(config.srfax.faxNumber).replace(/^1(\d{10})$/, '$1'), 'Fax number did not persist as 10 digits');
      assert((await direct.locator('#senderEmail').inputValue()) === config.srfax.email, 'Sender email did not persist');
      assert((await direct.locator('#accountName').inputValue()) === 'Playwright SRFax check', 'Account name did not persist');
      assert((await direct.locator('#faxPasswd').inputValue()) === PASSWORD_MASK, 'Password field is not masked after save');
      assert(await direct.locator('#on').isChecked(), 'Gateway did not persist as enabled');
      assert(await direct.locator('#downloadCheckbox').isChecked(), 'Poll for incoming faxes did not persist');

      const html = await direct.content();
      assert(!html.includes(config.srfax.pass), 'Rendered page contains the SRFax password');
      await shot(direct, existing.accountNumber, 'fax-config-reloaded');
      await direct.close();
    });

    await step('no page errors across the flow', async () => {
      assertNoPageErrors(recorder);
    });
    if (screenshotsSkipped > 0) {
      console.log(`SKIP ${screenshotsSkipped} screenshot(s): a real SRFax account could appear on the page; `
        + 'set FAX_CONFIG_SCREENSHOTS=always to capture them anyway');
    }
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
}

main()
  .then(() => {
    const failed = results.filter((r) => !r.passed);
    if (failed.length > 0) {
      process.exitCode = 1;
    }
  })
  .catch((error) => {
    console.error(`FAIL fax configure checks: ${error.message}`);
    // Diagnostics only: URLs, statuses and console text, never form values.
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  });
