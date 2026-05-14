#!/usr/bin/env node
/*
 * Comprehensive browser checks for the CARLOS login and forced password-reset
 * flow. This script expects the devcontainer database and Tomcat app to be up.
 *
 * The checks intentionally cover both browser behavior and direct POST attempts:
 * failed login rendering, public method guards, unauthenticated provider redirects,
 * CSRF enforcement, weak-password rejection, retryable old-password failures, valid
 * password persistence, and old-vs-new password login behavior.
 *
 * The script mutates TEST_USER's security row while it runs and restores the original
 * password, forcePasswordReset flag, and passwordUpdateDate in a finally block. Use a
 * disposable development database, not a production or PHI-bearing environment.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/login-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/root/.cache/ms-playwright/chromium-1224/chrome-linux64/chrome
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 */

const { chromium, request } = require('../node_modules/playwright');
const { execFileSync } = require('child_process');

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8080/carlos';
const appPath = new URL(baseUrl).pathname.replace(/\/$/, '') || '';
const chromePath = process.env.CHROME_PATH || '/root/.cache/ms-playwright/chromium-1224/chrome-linux64/chrome';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const mysqlHost = process.env.MYSQL_HOST || 'db';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'oscar';

// Bcrypt hash for TEST_PASSWORD=carlos2026 in the dev fixture.
const baselineHash = process.env.TEST_PASSWORD_HASH
  || '{bcrypt}$2a$10$RcoNeqhcLzkfBzAoTQ5C5.nnsOs15iOasQCp0/smjDAuTtkMQ.Uju';

const original = {};
const results = [];
const failures = [];

function sql(query) {
  return execFileSync('mysql', [
    '-h', mysqlHost,
    '-u', mysqlUser,
    `-p${mysqlPassword}`,
    mysqlDatabase,
    '-N',
    '-B',
    '-e',
    query,
  ], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
}

function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function securityRow() {
  const out = sql(
    `SELECT password, forcePasswordReset, IFNULL(passwordUpdateDate, 'NULL')`
      + ` FROM security WHERE user_name='${escapeSql(testUser)}'`
  );
  if (!out) {
    throw new Error(`No security row found for ${testUser}`);
  }
  const [password, forcePasswordReset, passwordUpdateDate] = out.split('\t');
  return { password, forcePasswordReset, passwordUpdateDate };
}

function restoreOriginal() {
  if (!original.password) {
    return;
  }
  const dateSql = original.passwordUpdateDate === 'NULL'
    ? 'NULL'
    : `'${escapeSql(original.passwordUpdateDate)}'`;
  sql(
    `UPDATE security SET password='${escapeSql(original.password)}',`
      + ` forcePasswordReset=${Number(original.forcePasswordReset)},`
      + ` passwordUpdateDate=${dateSql}`
      + ` WHERE user_name='${escapeSql(testUser)}'`
  );
}

function setForcedResetBaseline(forcePasswordReset = 1) {
  sql(
    `UPDATE security SET password='${escapeSql(baselineHash)}',`
      + ` forcePasswordReset=${Number(forcePasswordReset)}`
      + ` WHERE user_name='${escapeSql(testUser)}'`
  );
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function record(name, fn) {
  const start = Date.now();
  try {
    await fn();
    results.push({ name, ms: Date.now() - start });
    console.log(`PASS ${name}`);
  } catch (error) {
    failures.push({ name, error });
    console.log(`FAIL ${name}: ${error.message}`);
  }
}

async function assertNotBlank(page, label, minHtml = 100) {
  const bodyText = (await page.locator('body').innerText({ timeout: 10000 })).trim();
  const bodyHtml = (await page.locator('body').innerHTML({ timeout: 10000 })).trim();
  assert(bodyText.length > 0 || bodyHtml.length >= minHtml, `${label} rendered blank content`);
}

async function assertResponseNotBlank(response, label, minBytes = 100) {
  const text = await response.text();
  assert(text.trim().length >= minBytes, `${label} HTTP response was blank or too small`);
  return text;
}

async function newBrowserContext(browser) {
  return browser.newContext({ ignoreHTTPSErrors: true });
}

async function login(page, password = testPassword) {
  await page.goto(`${baseUrl}/`, { waitUntil: 'domcontentloaded' });
  await page.locator('#username').waitFor({ timeout: 10000 });
  await assertNotBlank(page, 'login page');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(password);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
}

async function loginToForcedReset(page) {
  await login(page);
  await page.waitForURL(/forcepasswordreset/, { timeout: 15000 });
  await assertNotBlank(page, 'forced reset page');

  const action = await page.locator('form').first().getAttribute('action');
  assert(action && action.endsWith('/forcepasswordresetSubmit'), `reset form action was ${action}`);

  await page.waitForSelector('input[type="hidden"]', { state: 'attached', timeout: 10000 });
  const csrf = await page.locator('input[type="hidden"]').evaluateAll((inputs) => {
    const token = inputs
      .map((input) => ({ name: input.getAttribute('name') || '', value: input.getAttribute('value') || '' }))
      .find((input) => input.name && input.name !== 'forcedpasswordchange' && input.value);
    return token || null;
  });
  assert(csrf && csrf.name && csrf.value, 'forced reset page did not render a CSRF hidden input');
  assert(csrf.value.length > 10, 'CSRF token is unexpectedly short');
  return { csrfName: csrf.name, csrfValue: csrf.value };
}

async function expectSchedulePage(page, label) {
  await page.waitForURL(/provider\/(providercontrol|ViewAppointmentAdminDay)/, { timeout: 20000 });
  await assertNotBlank(page, label, 5000);
  const html = await page.content();
  assert(html.includes('/csrfguard'), `${label} did not include csrfguard script`);
  assert(/Schedule|appointment|provider/i.test(html), `${label} did not look like a schedule page`);
}

(async () => {
  Object.assign(original, securityRow());
  console.log(
    `Original ${testUser} row: forcePasswordReset=${original.forcePasswordReset},`
      + ` passwordUpdateDate=${original.passwordUpdateDate}`
  );

  const browser = await chromium.launch({
    executablePath: chromePath,
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });

  try {
    await record('app root renders a nonblank login page', async () => {
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await page.goto(`${baseUrl}/`, { waitUntil: 'domcontentloaded' });
      await page.locator('#username').waitFor({ timeout: 10000 });
      await assertNotBlank(page, 'app root');
      await context.close();
    });

    await record('failed login redirects to a nonblank failure flow', async () => {
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await login(page, 'wrong-password');
      await page.waitForURL(/login=failed|loginfailed|login/, { timeout: 15000 });
      await assertNotBlank(page, 'failed login page');
      assert(/login=failed|loginfailed|login/.test(page.url()), `unexpected failed-login URL: ${page.url()}`);
      await context.close();
    });

    await record('public login entry route rejects POST and allows GET', async () => {
      const api = await request.newContext();
      const post = await api.post(`${baseUrl}/index`, { form: { anything: 'x' } });
      assert(post.status() === 405, `POST /index expected 405, got ${post.status()}`);
      assert((post.headers().allow || '').includes('GET'), `POST /index missing Allow GET header`);
      const resetPost = await api.post(`${baseUrl}/forcepasswordreset`, { form: { anything: 'x' } });
      assert(resetPost.status() === 405, `POST /forcepasswordreset expected 405, got ${resetPost.status()}`);
      assert((resetPost.headers().allow || '').includes('GET'), 'POST /forcepasswordreset missing Allow GET header');
      const get = await api.get(`${baseUrl}/index`);
      assert(get.status() === 200, `GET /index expected 200, got ${get.status()}`);
      await assertResponseNotBlank(get, 'GET /index');
      await api.dispose();
    });

    await record('unauthenticated protected browser pages redirect to login', async () => {
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await page.goto(`${baseUrl}/provider/providercontrol`, { waitUntil: 'domcontentloaded' });
      await page.waitForURL(/login|logout|index/, { timeout: 15000 });
      await assertNotBlank(page, 'unauthenticated provider redirect');
      await page.goto(`${baseUrl}/billing/CA/ON/ViewBillingONMRI`, { waitUntil: 'domcontentloaded' });
      await page.waitForURL(/login|logout|index/, { timeout: 15000 });
      await assertNotBlank(page, 'unauthenticated billing MRI redirect');
      await context.close();
    });

    await record('unauthenticated structured and download routes return 401', async () => {
      const api = await request.newContext();
      const ajax = await api.get(`${baseUrl}/billing/CA/ON/ViewSearchRefDocAjax`, {
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
      });
      assert(ajax.status() === 401, `AJAX unauth expected 401, got ${ajax.status()}`);
      const json = await api.get(`${baseUrl}/admin/api/status`, {
        headers: { Accept: 'application/json' },
      });
      assert(json.status() === 401, `JSON unauth expected 401, got ${json.status()}`);
      const download = await api.get(`${baseUrl}/Download`);
      assert(download.status() === 401, `download unauth expected 401, got ${download.status()}`);
      await api.dispose();
    });

    await record('normal login reaches a nonblank authenticated schedule page', async () => {
      setForcedResetBaseline(0);
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await login(page);
      await expectSchedulePage(page, 'normal login schedule');
      await context.close();
    });

    await record('direct authenticated appointment-day action renders nonblank HTML', async () => {
      setForcedResetBaseline(0);
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await login(page);
      await expectSchedulePage(page, 'initial authenticated schedule');
      const direct = `${baseUrl}/provider/ViewAppointmentAdminDay`
        + '?year=2026&month=5&day=14&view=0&displaymode=day&dboperation=searchappointmentday&viewall=0';
      const response = await page.goto(direct, { waitUntil: 'domcontentloaded' });
      assert(response.status() === 200, `direct appointment day expected 200, got ${response.status()}`);
      const html = await assertResponseNotBlank(response, 'direct appointment-day response', 5000);
      assert(html.includes('/csrfguard'), 'direct appointment-day response did not include csrfguard script');
      await assertNotBlank(page, 'direct appointment-day page', 5000);
      await context.close();
    });

    await record('missing reset credential redirects instead of rendering reset form', async () => {
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await page.goto(`${baseUrl}/forcepasswordreset`, { waitUntil: 'domcontentloaded' });
      await page.waitForURL(/loginfailed|login|index/, { timeout: 15000 });
      await assertNotBlank(page, 'missing-token reset redirect');
      await context.close();
    });

    await record('forced reset page posts to protected endpoint and includes CSRF token', async () => {
      setForcedResetBaseline(1);
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await loginToForcedReset(page);
      await context.close();
    });

    await record('missing-CSRF forced reset POST is rejected and leaves DB unchanged', async () => {
      setForcedResetBaseline(1);
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await loginToForcedReset(page);
      const response = await page.evaluate(async (submitPath) => {
        const body = new URLSearchParams({
          forcedpasswordchange: 'true',
          oldPassword: 'carlos2026',
          newPassword: 'Carlos2026!NoCsrf',
          confirmPassword: 'Carlos2026!NoCsrf',
        });
        const res = await fetch(`${submitPath}/forcepasswordresetSubmit`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body,
        });
        return { status: res.status, url: res.url, text: (await res.text()).slice(0, 300) };
      }, appPath);
      assert(
        response.status === 403 || /csrf/i.test(response.text) || response.url.includes('/errorpage'),
        `missing-CSRF POST was not rejected: ${JSON.stringify(response)}`
      );
      const row = securityRow();
      assert(row.password === baselineHash && row.forcePasswordReset === '1', 'missing-CSRF POST changed DB state');
      await context.close();
    });

    await record('weak direct forced reset POST is rejected server-side with token retained', async () => {
      setForcedResetBaseline(1);
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      const csrf = await loginToForcedReset(page);
      const response = await page.evaluate(async ({ csrfName, csrfValue, submitPath }) => {
        const body = new URLSearchParams({
          forcedpasswordchange: 'true',
          oldPassword: 'carlos2026',
          newPassword: 'short',
          confirmPassword: 'short',
          [csrfName]: csrfValue,
        });
        const res = await fetch(`${submitPath}/forcepasswordresetSubmit`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body,
        });
        return { status: res.status, url: res.url, text: (await res.text()).slice(0, 1000) };
      }, { ...csrf, submitPath: appPath });
      assert(response.status === 200, `weak password POST expected 200 reset response, got ${response.status}`);
      assert(/forcepasswordreset|Password/i.test(response.text), 'weak password POST did not return reset/error content');
      const row = securityRow();
      assert(row.password === baselineHash && row.forcePasswordReset === '1', 'weak password POST changed DB state');
      await context.close();
    });

    await record('wrong old password keeps reset token reusable, then valid reset persists', async () => {
      setForcedResetBaseline(1);
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await loginToForcedReset(page);

      await page.locator('input[name="oldPassword"]').fill('wrong-old-password');
      await page.locator('input[name="newPassword"]').fill('Carlos2026!Retry');
      await page.locator('input[name="confirmPassword"]').fill('Carlos2026!Retry');
      await Promise.all([
        page.waitForLoadState('domcontentloaded').catch(() => {}),
        page.locator('input[type="submit"]').click(),
      ]);
      await assertNotBlank(page, 'wrong-old-password reset response');
      const rejectedRow = securityRow();
      assert(rejectedRow.password === baselineHash && rejectedRow.forcePasswordReset === '1',
        'wrong old password changed DB state');

      await page.locator('input[name="oldPassword"]').fill(testPassword);
      await page.locator('input[name="newPassword"]').fill('Carlos2026!Valid');
      await page.locator('input[name="confirmPassword"]').fill('Carlos2026!Valid');
      await Promise.all([
        page.waitForLoadState('domcontentloaded').catch(() => {}),
        page.locator('input[type="submit"]').click(),
      ]);
      await expectSchedulePage(page, 'successful reset schedule destination');
      const row = securityRow();
      assert(row.password !== baselineHash, 'valid reset did not change stored password');
      assert(row.forcePasswordReset === '0', `valid reset did not clear forcePasswordReset: ${row.forcePasswordReset}`);
      await context.close();
    });

    await record('after valid reset, old password fails and new password reaches schedule', async () => {
      const row = securityRow();
      assert(row.forcePasswordReset === '0', 'positive reset precondition was not met');

      const oldContext = await newBrowserContext(browser);
      const oldPage = await oldContext.newPage();
      await login(oldPage, testPassword);
      await oldPage.waitForURL(/login=failed|loginfailed|login/, { timeout: 15000 });
      await assertNotBlank(oldPage, 'old password failure after reset');
      await oldContext.close();

      const newContext = await newBrowserContext(browser);
      const newPage = await newContext.newPage();
      await login(newPage, 'Carlos2026!Valid');
      await expectSchedulePage(newPage, 'new password login schedule');
      await newContext.close();
    });

    await record('legacy /login forced-reset POST cannot change password without reset cache token', async () => {
      setForcedResetBaseline(1);
      const api = await request.newContext();
      const res = await api.post(`${baseUrl}/login`, {
        form: {
          forcedpasswordchange: 'true',
          oldPassword: testPassword,
          newPassword: 'Carlos2026!Legacy',
          confirmPassword: 'Carlos2026!Legacy',
        },
      });
      assert([200, 302, 403].includes(res.status()), `unexpected /login forced reset POST status ${res.status()}`);
      const row = securityRow();
      assert(row.password === baselineHash && row.forcePasswordReset === '1',
        'legacy /login forced-reset POST changed DB state');
      await api.dispose();
    });
  } finally {
    await browser.close().catch(() => {});
    restoreOriginal();
    const after = securityRow();
    console.log(
      `Restored ${testUser} row: forcePasswordReset=${after.forcePasswordReset},`
        + ` passwordUpdateDate=${after.passwordUpdateDate}`
    );
    console.log(`Completed ${results.length} Playwright checks, ${failures.length} failures`);
    if (failures.length) {
      for (const failure of failures) {
        console.log(`FAILED ${failure.name}: ${failure.error.stack || failure.error.message}`);
      }
      process.exitCode = 1;
    }
  }
})().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  try {
    restoreOriginal();
  } catch (restoreError) {
    console.error(`Restore failed: ${restoreError.stack || restoreError}`);
  }
  process.exit(1);
});
