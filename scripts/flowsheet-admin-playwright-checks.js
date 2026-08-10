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

/*
 * Browser regression checks for Administration -> System Management flowsheets.
 *
 * Creates one uniquely named flowsheet, verifies the editor opens, verifies
 * Edit for both the built-in Periodic Health Visit flowsheet and the created
 * flowsheet, then removes all database rows created by this run.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:flowsheet-admin-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=oscar
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *   ALLOW_NON_LOCAL_MYSQL_HOST=true only when intentionally targeting a non-local test database
 */

const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const { randomBytes } = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const LOCAL_HOSTS = new Set([
  'localhost',
  '127.0.0.1',
  '::1',
  '0:0:0:0:0:0:0:1',
  '0.0.0.0',
  'carlos',
]);
const LOCAL_MYSQL_HOSTS = new Set([
  'localhost',
  '127.0.0.1',
  '::1',
  '0:0:0:0:0:0:0:1',
  'db',
]);
const MYSQL_TIMEOUT_MS = 30000;

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || 'db');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'oscar';
const displayName = `PW Flowsheet 3400 ${Date.now()} ${randomBytes(4).toString('hex')}`;

const mysqlDefaults = createMysqlDefaultsFile();
const findings = [];
const checks = [];
const expectedErrorUrls = new Set();
const confirmedExpectedErrorUrls = new Set();
let csrfToken = '';

function normalizedHostname(url) {
  const host = url.hostname.toLowerCase();
  return host.startsWith('[') && host.endsWith(']') ? host.slice(1, -1) : host;
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not contain embedded credentials');
  }
  const host = normalizedHostname(parsed);
  const localHost = LOCAL_HOSTS.has(host);
  if (!localHost && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  if (!localHost && parsed.protocol !== 'https:') {
    throw new Error('Non-local BASE_URL targets must use https');
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  parsed.search = '';
  parsed.hash = '';
  return parsed;
}

function validateMysqlHost(rawMysqlHost) {
  const host = rawMysqlHost.trim().toLowerCase();
  if (!host) {
    throw new Error('MYSQL_HOST must not be empty');
  }
  if (!LOCAL_MYSQL_HOSTS.has(host) && process.env.ALLOW_NON_LOCAL_MYSQL_HOST !== 'true') {
    throw new Error(
      `Refusing non-local MYSQL_HOST ${host}; set ALLOW_NON_LOCAL_MYSQL_HOST=true for an intentional test database`
    );
  }
  return host;
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

function isWithinConfiguredApp(url) {
  const appRoot = baseUrl.pathname || '/';
  return url.origin === baseUrl.origin
    && (appRoot === '/' || url.pathname === appRoot || url.pathname.startsWith(`${appRoot}/`));
}

// In MySQL option files, an unquoted '#' starts a comment and backslashes are
// escape characters. Quote the password and escape the two characters that
// retain special meaning inside a double-quoted option value.
function encodeMysqlOptionFileValue(value) {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  let dir;
  try {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-flowsheet-mysql-'));
    const file = path.join(dir, 'client.cnf');
    fs.writeFileSync(file, `[client]\npassword=${encodeMysqlOptionFileValue(mysqlPassword)}\n`, { mode: 0o600 });
    return { dir, file };
  } catch (error) {
    if (dir) {
      fs.rmSync(dir, { recursive: true, force: true });
    }
    throw error;
  }
}

function sanitizedMysqlFailure(error) {
  const stderr = String(error && error.stderr ? error.stderr : '');
  const errorLine = stderr.split(/\r?\n/).find((line) => /^ERROR\b/i.test(line.trim()));
  if (!errorLine) {
    return error && (error.killed || error.code === 'ETIMEDOUT')
      ? 'mysql command timed out'
      : 'mysql command failed';
  }
  const detail = errorLine
    .replace(/'[^']*'/g, "'<redacted>'")
    .replace(/\d+/g, '#')
    .replace(/[^\x20-\x7e]/g, ' ')
    .slice(0, 300);
  return `mysql command failed: ${detail}`;
}

function sql(query) {
  try {
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
      timeout: MYSQL_TIMEOUT_MS,
    }).trim();
  } catch (error) {
    throw new Error(sanitizedMysqlFailure(error));
  }
}

function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function createdRows() {
  const output = sql(
    `SELECT id, name, displayName FROM FlowSheetUserCreated WHERE displayName='${escapeSql(displayName)}' ORDER BY id`
  );
  if (!output) {
    return [];
  }
  return output.split('\n').map((line) => {
    const [id, name, rowDisplayName] = line.split('\t');
    return { id, name, displayName: rowDisplayName };
  });
}

function cleanupCreatedRows() {
  const rows = createdRows();
  for (const row of rows) {
    sql(`DELETE FROM flowsheet_customization WHERE flowsheet='${escapeSql(row.name)}'`);
  }
  sql(`DELETE FROM FlowSheetUserCreated WHERE displayName='${escapeSql(displayName)}'`);
}

async function cleanupCreatedRowsThroughApplication() {
  const rows = createdRows();
  if (!rows.length) {
    return;
  }
  if (!browserContext) {
    throw new Error('Cannot reload flowsheets during cleanup without an authenticated browser context');
  }
  if (!csrfToken) {
    throw new Error('Cannot reload flowsheets during cleanup without a CSRF token');
  }
  for (const row of rows) {
    sql(`DELETE FROM flowsheet_customization WHERE flowsheet='${escapeSql(row.name)}'`);
    const response = await browserContext.request.post(appUrl('/admin/Flowsheet'), {
      form: { method: 'deleteFlowsheet', id: row.id, 'CSRF-TOKEN': csrfToken },
      timeout: 30000,
    });
    if (!response.ok()) {
      throw new Error(`Application flowsheet cleanup returned HTTP ${response.status()}`);
    }
    const result = await response.json();
    if (result.success !== true || String(result.id) !== row.id) {
      throw new Error('Application flowsheet cleanup returned an unexpected response');
    }
  }
}

async function installNavigationGuard(context) {
  await context.route('**/*', async (route) => {
    const request = route.request();
    if (request.isNavigationRequest() && !isWithinConfiguredApp(new URL(request.url()))) {
      findings.push({ type: 'outside-app-navigation-blocked' });
      await route.abort('blockedbyclient');
      return;
    }
    await route.continue();
  });
}

function wirePage(page) {
  page.on('response', (response) => {
    if (expectedErrorUrls.has(response.url()) && response.status() === 500) {
      confirmedExpectedErrorUrls.add(response.url());
      return;
    }
    if (response.status() >= 400
        && !expectedErrorUrls.has(response.url())
        && !/\/favicon\.ico$/.test(response.url())) {
      findings.push({ type: 'http', status: response.status(), url: response.url() });
    }
  });
  page.on('pageerror', (error) => {
    findings.push({ type: 'pageerror', text: error.stack || error.message });
  });
  page.on('console', (message) => {
    const text = message.text();
    const expectedErrorResponse = confirmedExpectedErrorUrls.has(message.location().url);
    if (message.type() === 'error'
        && !expectedErrorResponse
        && !/Content Security Policy.*report-only/i.test(text)) {
      findings.push({ type: 'console', text, location: message.location() });
    }
  });
  page.on('dialog', async (dialog) => {
    findings.push({ type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
}

async function safeGoto(page, appPath) {
  const response = await page.goto(appUrl(appPath), { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl restricts paths and validateBaseUrl restricts hosts // NOSONAR - same rationale
  if (!isWithinConfiguredApp(new URL(page.url()))) {
    throw new Error('Navigation left the configured CARLOS EMR application');
  }
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return response;
}

async function assertEditor(page, label, response) {
  const body = await page.locator('body').innerText();
  if (!response || response.status() !== 200) {
    throw new Error(`${label} returned HTTP ${response ? response.status() : 'unknown'}`);
  }
  if (!/Edit Flowsheet/.test(body) || /CARLOS Error:|unexpected error/i.test(body)) {
    throw new Error(`${label} did not render the flowsheet editor`);
  }
  checks.push({ label, status: response.status(), url: page.url() });
}

async function captureCsrfToken(page) {
  await page.waitForFunction(() => {
    const input = document.querySelector('input[name="CSRF-TOKEN"]');
    return Boolean(input && input.value);
  }, null, { timeout: 30000 });
  csrfToken = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
}

async function login(page) {
  await safeGoto(page, '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  if (await page.locator('#pin').count()) {
    await page.locator('#pin').fill(testPin);
  }
  await Promise.all([
    page.waitForURL((url) => isWithinConfiguredApp(url) && /providercontrol/.test(url.pathname), { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  if (!/providercontrol/.test(page.url())) {
    throw new Error(`Login did not reach providercontrol: ${page.url()}`);
  }
}

async function createFlowsheet(page) {
  await safeGoto(page, '/encounter/oscarMeasurements/adminFlowsheet/ViewNewFlowsheet');
  await page.locator('#displayName').fill(displayName);
  await page.locator('#dxcodeTriggers').fill('icd9:250');
  await page.locator('#warningColour').fill('red');
  await page.locator('#recommendationColour').fill('yellow');
  const [response] = await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
    page.locator('input[type="submit"][value="Create"]').click(),
  ]);
  await assertEditor(page, 'create', response);
  await captureCsrfToken(page);

  const rows = createdRows();
  if (rows.length !== 1) {
    throw new Error(`One create submission produced ${rows.length} rows`);
  }
  checks.push({ label: 'single-row', id: rows[0].id, flowsheet: rows[0].name });
  return rows[0];
}

async function editFromManager(page, rowText, label) {
  await safeGoto(page, '/admin/ManageFlowsheets');
  const row = page.locator('tbody tr').filter({ hasText: rowText }).first();
  await row.waitFor({ state: 'visible', timeout: 30000 });
  const href = await row.getByRole('link', { name: /^Edit$/ }).getAttribute('href');
  if (!href) {
    throw new Error(`${label} row did not contain an Edit link`);
  }
  const target = new URL(href, baseUrl);
  if (!isWithinConfiguredApp(target)) {
    throw new Error(`${label} Edit link left the configured application`);
  }
  const response = await page.goto(target.toString(), { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- href is application-generated and origin/path checked above // NOSONAR - same rationale
  await assertEditor(page, label, response);
}

async function verifyEditorFailureStatus(page) {
  const target = new URL(appUrl('/encounter/oscarMeasurements/adminFlowsheet/ViewEditFlowsheet'));
  target.searchParams.set('flowsheet', `missing-${Date.now()}`);
  target.searchParams.set('displayName', 'Missing test flowsheet');
  expectedErrorUrls.add(target.toString());
  let response;
  try {
    response = await page.goto(target.toString(), { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- target is built from the validated application URL above // NOSONAR - same rationale
  } finally {
    expectedErrorUrls.delete(target.toString());
  }
  const body = await page.locator('body').innerText();
  if (!response || response.status() !== 500) {
    throw new Error(`Invalid editor render returned HTTP ${response ? response.status() : 'unknown'}, expected 500`);
  }
  if (!/CARLOS Error:\s*500/.test(body) || /CARLOS Error:\s*0/.test(body)) {
    throw new Error('Invalid editor render did not display CARLOS Error: 500');
  }
  checks.push({ label: 'editor-error-status', status: response.status(), url: page.url() });
}

let browser;
let browserContext;
let cleanupPromise;

function cleanupResources() {
  if (cleanupPromise) {
    return cleanupPromise;
  }
  cleanupPromise = (async () => {
    const errors = [];
    try {
      try {
        await cleanupCreatedRowsThroughApplication();
      } catch (error) {
        errors.push(error);
      }
      try {
        cleanupCreatedRows();
      } catch (error) {
        errors.push(error);
      }
      if (browser) {
        try {
          await browser.close();
        } catch (error) {
          errors.push(error);
        } finally {
          browser = undefined;
          browserContext = undefined;
        }
      }
    } finally {
      try {
        fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
      } catch (error) {
        errors.push(error);
      }
    }
    if (errors.length) {
      throw new AggregateError(errors, 'One or more flowsheet test cleanup operations failed');
    }
  })();
  return cleanupPromise;
}

function installSignalHandler(signal, exitCode) {
  process.once(signal, () => {
    cleanupResources()
      .catch((error) => console.error(`Cleanup after ${signal} failed: ${error.message}`))
      .finally(() => process.exit(exitCode));
  });
}

installSignalHandler('SIGINT', 130);
installSignalHandler('SIGTERM', 143);

(async () => {
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }
  try {
    cleanupCreatedRows();
    browser = await chromium.launch(launchOptions);
    browserContext = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
    await installNavigationGuard(browserContext);
    const page = await browserContext.newPage();
    wirePage(page);

    await login(page);
    await createFlowsheet(page);
    await editFromManager(page, 'Periodic Health Visit', 'system-edit');
    await editFromManager(page, displayName, 'user-edit');
    await verifyEditorFailureStatus(page);

    const blockingFindings = findings;
    if (blockingFindings.length) {
      throw new Error(
        `Browser checks found ${blockingFindings.length} HTTP or browser issue(s): ${JSON.stringify(blockingFindings)}`
      );
    }
    console.log(JSON.stringify({ displayName, checks, findings }, null, 2));
    console.log('PASS flowsheet create and system/user edit workflows rendered successfully');
  } finally {
    await cleanupResources();
  }
})().catch((error) => {
  console.error('FAIL flowsheet admin Playwright checks');
  console.error(error.stack || error.message);
  process.exit(1);
});
