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
 * Browser check for the CARLOS admin add-login flow affected by PR 2522.
 *
 * The script logs in as an admin user, seeds a uniquely stamped active provider
 * with no security login, verifies that provider is offered in the add-login
 * Provider No dropdown, creates the login through the browser, verifies the
 * security row in MySQL, verifies the provider disappears from the dropdown,
 * writes local screenshots/results, and cleans up rows it created.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:add-login-account-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   MYSQL_HOST=127.0.0.1 MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=oscar
 *   ADD_LOGIN_SITE_ID=<site id to assign to the seeded provider>
 *   ADD_LOGIN_SCREENSHOT_DIR=/tmp/carlos-add-login-account-playwright
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const { randomInt } = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { buildArtifactPath } = require('./eform-local-playwright-utils');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const mysqlHost = process.env.MYSQL_HOST || '127.0.0.1';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'oscar';
const screenshotDir = process.env.ADD_LOGIN_SCREENSHOT_DIR || '/tmp/carlos-add-login-account-playwright';
const fixturePassword = process.env.ADD_LOGIN_NEW_PASSWORD || 'E2eAccount1!';
const fixturePin = process.env.ADD_LOGIN_NEW_PIN || '1234';
const mysqlTimeoutMs = 30000;

let mysqlDefaults = null;
const badResponses = [];
const consoleIssues = [];


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

function playwrightBaseUrl() {
  const pathname = baseUrl.pathname.replace(/\/$/, '');
  return `${baseUrl.origin}${pathname}/`;
}

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-add-login-mysql-'));
  const file = path.join(dir, 'client.cnf');
  try {
    fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
    return { dir, file };
  } catch (error) {
    fs.rmSync(dir, { recursive: true, force: true });
    throw error;
  }
}

function cleanupMysqlDefaultsFile() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}

function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
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
    timeout: mysqlTimeoutMs,
  }).trim();
}

function rows(query) {
  const output = sql(query);
  return output ? output.split('\n').map((line) => line.split('\t')) : [];
}

function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function chooseFixture() {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const providerNo = String(randomInt(700000, 790000));
    const username = `pw${providerNo.slice(1)}`;
    const count = Number(sql(
      `SELECT`
        + ` (SELECT COUNT(*) FROM provider WHERE provider_no='${escapeSql(providerNo)}') +`
        + ` (SELECT COUNT(*) FROM security WHERE user_name='${escapeSql(username)}' OR provider_no='${escapeSql(providerNo)}')`
    ));

    if (count === 0) {
      return { providerNo, username };
    }
  }

  throw new Error('Could not find an unused provider/user fixture id');
}

function adminProviderNo() {
  const out = sql(`SELECT provider_no FROM security WHERE user_name='${escapeSql(testUser)}'`);
  assert(out, `No security row found for TEST_USER=${testUser}`);
  return out.split('\n')[0];
}

function activeRoleNames(providerNo) {
  const roleRows = rows(
    `SELECT role_name FROM secUserRole`
      + ` WHERE provider_no='${escapeSql(providerNo)}'`
      + ` AND COALESCE(activeyn, 1)=1`
      + ` ORDER BY role_name`
  );
  const roleNames = roleRows.map((row) => row[0]).filter(Boolean);
  assert(roleNames.length > 0, `No active roles found for TEST_USER=${testUser} provider_no=${providerNo}`);
  return roleNames;
}

function assertSiteAccessPrivacyPrivilege(roleNames) {
  const escapedRoles = roleNames.map((roleName) => `'${escapeSql(roleName)}'`).join(',');
  const privilegeCount = Number(sql(
    `SELECT COUNT(*) FROM secObjPrivilege`
      + ` WHERE objectName='_site_access_privacy'`
      + ` AND privilege IN ('x', 'r')`
      + ` AND roleUserGroup IN (${escapedRoles})`
  ));
  assert(
    privilegeCount > 0,
    `TEST_USER=${testUser} must have _site_access_privacy through one of these active roles: ${roleNames.join(', ')}`
  );
}

function sharedSiteId(providerNo) {
  if (process.env.ADD_LOGIN_SITE_ID) {
    return process.env.ADD_LOGIN_SITE_ID;
  }
  const out = sql(
    `SELECT site_id FROM providersite`
      + ` WHERE provider_no='${escapeSql(providerNo)}'`
      + ` ORDER BY site_id LIMIT 1`
  );
  return out || '1';
}

function seedProvider(providerNo, siteId) {
  const numericSiteId = Number(siteId);
  assert(Number.isInteger(numericSiteId), `ADD_LOGIN_SITE_ID must be an integer, got ${siteId}`);
  sql(
    `INSERT INTO provider`
      + ` (provider_no, last_name, first_name, provider_type, specialty, sex, status, lastUpdateDate)`
      + ` VALUES`
      + ` ('${escapeSql(providerNo)}', 'Playwright', 'Account', 'doctor', 'GP', 'M', '1', NOW())`
  );
  sql(
    `INSERT INTO providersite(provider_no, site_id)`
      + ` VALUES ('${escapeSql(providerNo)}', ${numericSiteId})`
  );
}

function cleanupRows(providerNo, username) {
  const statements = [
    `DELETE FROM security WHERE user_name='${escapeSql(username)}' OR provider_no='${escapeSql(providerNo)}'`,
    `DELETE FROM providersite WHERE provider_no='${escapeSql(providerNo)}'`,
    `DELETE FROM provider WHERE provider_no='${escapeSql(providerNo)}'`,
  ];

  const errors = [];
  for (const statement of statements) {
    try {
      sql(statement);
    } catch (error) {
      errors.push(`${statement}: ${error.message}`);
    }
  }

  if (errors.length > 0) {
    throw new Error(`Cleanup failed:\n${errors.join('\n')}`);
  }
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    consoleIssues.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
  page.on('response', (response) => {
    const status = response.status();
    const responseUrl = response.url();
    if (status >= 400 && !(status === 404 && /\/imageRenderingServlet\?/.test(responseUrl))) {
      badResponses.push({ label, status, url: responseUrl });
    }
  });
  page.on('console', (message) => {
    const text = message.text();
    if (/(ReferenceError|SyntaxError|TypeError|DataTables AJAX error|Server did not return expected success response|Form submission timed out|Failed to save)/i.test(text)) {
      consoleIssues.push({ label, type: message.type(), text, location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    consoleIssues.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
}

async function login(page) {
  await page.goto('./', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
}

async function providerOptions(page) {
  const select = page.locator('select[name="provider_no"]').first();
  await select.waitFor({ state: 'visible', timeout: 30000 });
  return select.locator('option').evaluateAll((options) => options.map((option) => ({
    value: option.value,
    text: option.textContent.trim(),
  })));
}

async function run() {
  let providerNo = null;
  let username = null;
  let browser = null;

  try {
    mysqlDefaults = createMysqlDefaultsFile();

    const adminNo = adminProviderNo();
    const roleNames = activeRoleNames(adminNo);
    assertSiteAccessPrivacyPrivilege(roleNames);
    const siteId = sharedSiteId(adminNo);
    const fixture = chooseFixture();
    providerNo = fixture.providerNo;
    username = fixture.username;
    const result = {
      baseUrl: baseUrl.toString(),
      adminProviderNo: adminNo,
      adminRoles: roleNames,
      siteId,
      providerNo,
      username,
      screenshots: {},
      steps: [],
    };

    cleanupRows(providerNo, username);
    seedProvider(providerNo, siteId);

    const launchOptions = { headless: true };
    if (chromePath) {
      launchOptions.executablePath = chromePath;
    }

    browser = await chromium.launch(launchOptions);
    const context = await browser.newContext({
      baseURL: playwrightBaseUrl(),
      viewport: { width: 1280, height: 900 },
      ignoreHTTPSErrors: true,
    });
    const page = await context.newPage();
    page.setDefaultTimeout(20000);
    wirePage(page, 'add-login-account');

    await login(page);
    result.steps.push('logged in as admin');

    await page.goto('admin/ViewSecurityAddARecord', { waitUntil: 'networkidle', timeout: 30000 });
    const initialOptions = await providerOptions(page);
    result.initialOptions = initialOptions;

    assert(
      initialOptions.some((option) => option.value === providerNo),
      `Seeded provider ${providerNo} was not offered before account creation: ${JSON.stringify(initialOptions)}`
    );

    result.screenshots.before = buildArtifactPath(screenshotDir, 'before-create-account');
    await page.screenshot({ path: result.screenshots.before, fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename

    await page.locator('input[name="user_name"]').fill(username);
    await page.locator('input[name="password"]').fill(fixturePassword);
    await page.locator('input[name="conPassword"]').fill(fixturePassword);
    await page.locator('select[name="provider_no"]').selectOption(providerNo);

    const expire = page.locator('input[name="b_ExpireSet"]').first();
    if ((await expire.count()) && (await expire.isChecked())) {
      await expire.uncheck();
    }

    const forcePasswordReset = page.locator('select[name="forcePasswordReset"]').first();
    if (await forcePasswordReset.count()) {
      await forcePasswordReset.selectOption('0');
    }

    const pin = page.locator('input[name="pin"]').first();
    if ((await pin.count()) && (await pin.isEnabled())) {
      await pin.fill(fixturePin);
    }

    const conPin = page.locator('input[name="conPin"]').first();
    if ((await conPin.count()) && (await conPin.isEnabled())) {
      await conPin.fill(fixturePin);
    }

    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => null),
      page.locator('input[type="submit"][name="subbutton"]').click(),
    ]);

    assert(consoleIssues.filter((issue) => issue.type === 'dialog').length === 0, `Unexpected browser validation dialog(s): ${JSON.stringify(consoleIssues)}`);

    const heading = await page.locator('h1').first().textContent().catch(() => '');
    result.submitHeading = heading ? heading.trim() : '';
    result.screenshots.afterSubmit = buildArtifactPath(screenshotDir, 'after-submit');
    await page.screenshot({ path: result.screenshots.afterSubmit, fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename

    const securityRows = rows(
      `SELECT user_name, provider_no, b_ExpireSet, forcePasswordReset, usingMfa`
        + ` FROM security`
        + ` WHERE user_name='${escapeSql(username)}' AND provider_no='${escapeSql(providerNo)}'`
    );
    result.securityRows = securityRows;
    assert(securityRows.length === 1, `Expected one security row for ${username}/${providerNo}, found ${securityRows.length}`);

    await page.goto('admin/ViewSecurityAddARecord', { waitUntil: 'networkidle', timeout: 30000 });
    const afterOptions = await providerOptions(page);
    result.afterOptions = afterOptions;
    assert(
      !afterOptions.some((option) => option.value === providerNo),
      `Provider ${providerNo} was still offered after account creation: ${JSON.stringify(afterOptions)}`
    );

    result.screenshots.afterProviderRemoved = buildArtifactPath(screenshotDir, 'after-provider-removed');
    await page.screenshot({ path: result.screenshots.afterProviderRemoved, fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename
    result.steps.push('created security row and verified provider disappeared from dropdown');

    assert(badResponses.length === 0, `Unexpected HTTP error responses: ${JSON.stringify(badResponses)}`);
    assert(consoleIssues.length === 0, `Unexpected browser console/page issues: ${JSON.stringify(consoleIssues)}`);

    fs.writeFileSync(buildArtifactPath(screenshotDir, 'result', '.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result, null, 2));
  } finally {
    try {
      if (browser) {
        await browser.close();
      }
    } finally {
      try {
        if (providerNo && username) {
          cleanupRows(providerNo, username);
        }
      } finally {
        cleanupMysqlDefaultsFile();
      }
    }
  }
}

run().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});
