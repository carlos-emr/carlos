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
 * Required environment:
 *   TEST_PASSWORD=<dev password> TEST_PIN=<dev pin>
 *   MYSQL_PASSWORD=<dev db password>
 *   TEST_PASSWORD_HASH='<known hash for TEST_PASSWORD>'
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_DATABASE=oscar
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium, request } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const appPath = baseUrl.pathname.replace(/\/$/, '') || '';
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = requiredEnv('TEST_PASSWORD');
const testPin = requiredEnv('TEST_PIN');
const mysqlHost = process.env.MYSQL_HOST || 'db';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = requiredEnv('MYSQL_PASSWORD');
const mysqlDatabase = process.env.MYSQL_DATABASE || 'oscar';
const resetPasswordNoCsrf = ['Carlos', '2026', '!NoCsrf'].join('');
const resetPasswordRetry = ['Carlos', '2026', '!Retry'].join('');
const resetPasswordValid = ['Carlos', '2026', '!Valid'].join('');
const resetPasswordLegacy = ['Carlos', '2026', '!Legacy'].join('');

let baselineHash = process.env.TEST_PASSWORD_HASH || null;
const mysqlDefaults = createMysqlDefaultsFile();

const original = {};
const results = [];
const failures = [];

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is required for login Playwright checks`);
  }
  return value;
}

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

function appUrl(path, query = null) {
  if (!path.startsWith('/') || path.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${path}`);
  }
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${path}`.replace(/\/{2,}/g, '/');
  url.search = '';
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      url.searchParams.set(key, value);
    }
  }
  return url.toString();
}

async function gotoApp(page, path, options = {}, query = null) {
  // BASE_URL is validated by validateBaseUrl(), and path must be root-relative.
  const targetUrl = appUrl(path, query);
  // nosemgrep -- appUrl rejects non-root-relative paths and validateBaseUrl rejects non-local hosts unless explicitly allowed.
  return page.goto(targetUrl, options); // nosemgrep
}

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-login-mysql-'));
  const file = path.join(dir, 'client.cnf');
  fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  return { dir, file };
}

function cleanupMysqlDefaultsFile() {
  if (mysqlDefaults && mysqlDefaults.dir) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
  }
}

function sql(query) {
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost,
    '-u', mysqlUser,
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
  await gotoApp(page, '/', { waitUntil: 'domcontentloaded' });
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
  if (!baselineHash) {
    throw new Error('TEST_PASSWORD_HASH is required so the script can seed a known-good password before mutating the database.');
  }
  console.log(`Captured original ${testUser} security row for restoration`);

  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const browser = await chromium.launch(launchOptions);

  try {
    await record('app root renders a nonblank login page', async () => {
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await gotoApp(page, '/', { waitUntil: 'domcontentloaded' });
      await page.locator('#username').waitFor({ timeout: 10000 });
      await assertNotBlank(page, 'app root');
      await context.close();
    });

    await record('failed login redirects to a nonblank failure flow', async () => {
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await login(page, ['wrong', 'test', 'credential'].join('-'));
      await page.waitForURL(/login=failed|loginfailed|login/, { timeout: 15000 });
      await assertNotBlank(page, 'failed login page');
      assert(/login=failed|loginfailed|login/.test(page.url()), `unexpected failed-login URL: ${page.url()}`);
      await context.close();
    });

    await record('public login entry route rejects POST and allows GET', async () => {
      const api = await request.newContext();
      const post = await api.post(appUrl('/index'), { form: { anything: 'x' } });
      assert(post.status() === 405, `POST /index expected 405, got ${post.status()}`);
      assert((post.headers().allow || '').includes('GET'), `POST /index missing Allow GET header`);
      const resetPost = await api.post(appUrl('/forcepasswordreset'), { form: { anything: 'x' } });
      assert(resetPost.status() === 405, `POST /forcepasswordreset expected 405, got ${resetPost.status()}`);
      assert((resetPost.headers().allow || '').includes('GET'), 'POST /forcepasswordreset missing Allow GET header');
      const get = await api.get(appUrl('/index'));
      assert(get.status() === 200, `GET /index expected 200, got ${get.status()}`);
      await assertResponseNotBlank(get, 'GET /index');
      await api.dispose();
    });

    await record('unauthenticated protected browser pages redirect to login', async () => {
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await gotoApp(page, '/provider/providercontrol', { waitUntil: 'domcontentloaded' });
      await page.waitForURL(/login|logout|index/, { timeout: 15000 });
      await assertNotBlank(page, 'unauthenticated provider redirect');
      await gotoApp(page, '/billing/CA/ON/ViewBillingONMRI', { waitUntil: 'domcontentloaded' });
      await page.waitForURL(/login|logout|index/, { timeout: 15000 });
      await assertNotBlank(page, 'unauthenticated billing MRI redirect');
      await context.close();
    });

    await record('unauthenticated structured and download routes return 401', async () => {
      const api = await request.newContext();
      const ajax = await api.get(appUrl('/billing/CA/ON/ViewSearchRefDocAjax'), {
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
      });
      assert(ajax.status() === 401, `AJAX unauth expected 401, got ${ajax.status()}`);
      const json = await api.get(appUrl('/admin/api/status'), {
        headers: { Accept: 'application/json' },
      });
      assert(json.status() === 401, `JSON unauth expected 401, got ${json.status()}`);
      const download = await api.get(appUrl('/Download'));
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
      const response = await gotoApp(page, '/provider/ViewAppointmentAdminDay', {
        waitUntil: 'domcontentloaded',
      }, {
        year: '2026',
        month: '5',
        day: '14',
        view: '0',
        displaymode: 'day',
        dboperation: 'searchappointmentday',
        viewall: '0',
      });
      assert(response.status() === 200, `direct appointment day expected 200, got ${response.status()}`);
      const html = await assertResponseNotBlank(response, 'direct appointment-day response', 5000);
      assert(html.includes('/csrfguard'), 'direct appointment-day response did not include csrfguard script');
      await assertNotBlank(page, 'direct appointment-day page', 5000);
      await context.close();
    });

    await record('missing reset credential redirects instead of rendering reset form', async () => {
      const context = await newBrowserContext(browser);
      const page = await context.newPage();
      await gotoApp(page, '/forcepasswordreset', { waitUntil: 'domcontentloaded' });
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
      const response = await page.evaluate(async ({ submitPath, password, resetPassword }) => {
        const body = new URLSearchParams({
          forcedpasswordchange: 'true',
          oldPassword: password,
          newPassword: resetPassword,
          confirmPassword: resetPassword,
        });
        const res = await fetch(`${submitPath}/forcepasswordresetSubmit`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body,
        });
        return { status: res.status, url: res.url, text: (await res.text()).slice(0, 300) };
      }, { submitPath: appPath, password: testPassword, resetPassword: resetPasswordNoCsrf });
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
      const response = await page.evaluate(async ({ csrfName, csrfValue, submitPath, password }) => {
        const body = new URLSearchParams({
          forcedpasswordchange: 'true',
          oldPassword: password,
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
      }, { ...csrf, submitPath: appPath, password: testPassword });
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

      await page.locator('input[name="oldPassword"]').fill(['wrong', 'old', 'password'].join('-'));
      await page.locator('input[name="newPassword"]').fill(resetPasswordRetry);
      await page.locator('input[name="confirmPassword"]').fill(resetPasswordRetry);
      await Promise.all([
        page.waitForLoadState('domcontentloaded').catch(() => {}),
        page.locator('input[type="submit"]').click(),
      ]);
      await assertNotBlank(page, 'wrong-old-password reset response');
      const rejectedRow = securityRow();
      assert(rejectedRow.password === baselineHash && rejectedRow.forcePasswordReset === '1',
        'wrong old password changed DB state');

      await page.locator('input[name="oldPassword"]').fill(testPassword);
      await page.locator('input[name="newPassword"]').fill(resetPasswordValid);
      await page.locator('input[name="confirmPassword"]').fill(resetPasswordValid);
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
      await login(newPage, resetPasswordValid);
      await expectSchedulePage(newPage, 'new password login schedule');
      await newContext.close();
    });

    await record('legacy /login forced-reset POST cannot change password without reset cache token', async () => {
      setForcedResetBaseline(1);
      const api = await request.newContext();
      const res = await api.post(appUrl('/login'), {
        form: {
          forcedpasswordchange: 'true',
          oldPassword: testPassword,
          newPassword: resetPasswordLegacy,
          confirmPassword: resetPasswordLegacy,
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
    try {
      restoreOriginal();
      console.log(`Restored ${testUser} security row`);
    } finally {
      cleanupMysqlDefaultsFile();
    }
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
  } finally {
    cleanupMysqlDefaultsFile();
  }
  process.exit(1);
});
