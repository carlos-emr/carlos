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
 * Browser check for the CARLOS Assign Role dropdown.
 *
 * An alpha tester could not create a second administrator: Administration >
 * Assign Role withheld the `admin` role from its dropdown on a standalone
 * install, because the seeded `admin` role holds `_site_access_privacy` and the
 * multisite super-root narrowing was applied without checking that multisites
 * was actually enabled. Since the seeded carlosdoc account is meant to be
 * DEACTIVATED once real accounts exist (see debian/ initial-admin.txt), losing
 * `admin` from this dropdown is a lockout, not a cosmetic defect.
 *
 * The script logs in as an admin, asserts the role dropdown offers the
 * super-root role, seeds a uniquely stamped provider, assigns that role to it
 * through the browser, verifies the secUserRole row in MySQL, writes local
 * screenshots/results, and cleans up the rows it created.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:assign-role-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=127.0.0.1 MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   ASSIGN_ROLE_SCREENSHOT_DIR=/tmp/carlos-assign-role-playwright
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */
const assert = require('assert');
const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const { randomInt } = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const mysqlHost = process.env.MYSQL_HOST || '127.0.0.1';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
const screenshotDir = process.env.ASSIGN_ROLE_SCREENSHOT_DIR || '/tmp/carlos-assign-role-playwright';
const mysqlTimeoutMs = 30000;

// The role the multisite narrowing targets. Kept in sync with the
// multioffice.admin.role.name property that providerRole.jsp reads.
const SUPER_ROOT_ROLE = process.env.ASSIGN_ROLE_SUPER_ROOT || 'admin';

let mysqlDefaults = null;
const results = [];

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

function appUrl(suffix) {
  return `${baseUrl.origin}${baseUrl.pathname}${suffix}`;
}

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-assign-role-mysql-'));
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
    '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: mysqlTimeoutMs }).trim();
}

function check(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` -- ${detail}` : ''}`);
}

function shot(name) {
  assert(/^[0-9a-z-]+$/.test(name), `Invalid screenshot name: ${name}`);
  return path.join(screenshotDir, `${name}.png`);
}

async function login(page) {
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await page.locator('input[type="submit"], button[type="submit"]').first().click();
  await page.waitForLoadState('domcontentloaded', { timeout: 60000 });
}

async function roleOptions(page, keyword) {
  const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : '';
  await page.goto(appUrl(`/admin/ProviderRole${query}`), { waitUntil: 'domcontentloaded', timeout: 60000 });
  const select = page.locator('select[name="roleNew"]').first();
  await select.waitFor({ state: 'attached', timeout: 60000 });
  return select.locator('option').evaluateAll((options) => options.map((option) => option.value));
}

function seedProvider(providerNo, lastName) {
  // lastUpdateDate/lastUpdateUser are NOT NULL without defaults (the audit-trail
  // columns every CARLOS table carries), so a bare column list fails on insert.
  sql(`INSERT INTO provider (provider_no, last_name, first_name, provider_type, status,
                             lastUpdateUser, lastUpdateDate)
       VALUES ('${providerNo}', '${lastName}', 'RoleCheck', 'doctor', '1', '-1', NOW())`);
}

function removeProvider(providerNo) {
  // Ordered by the foreign keys that reference provider.provider_no. Errors are
  // NOT swallowed: a cleanup that half-fails leaves a seeded provider and an
  // `admin` grant behind in the target database, which must never be reported
  // as a clean run.
  for (const table of ['secUserRole', 'program_provider', 'provider_facility', 'security']) {
    sql(`DELETE FROM ${table} WHERE provider_no='${providerNo}'`);
  }
  sql(`DELETE FROM provider WHERE provider_no='${providerNo}'`);
}

async function run() {
  const providerNo = String(900000 + randomInt(1000, 9999));
  const lastName = `Rolecheck${providerNo}`;
  let seeded = false;
  let browser = null;

  try {
    fs.mkdirSync(screenshotDir, { recursive: true });
    mysqlDefaults = createMysqlDefaultsFile();

    // The narrowing only misfires when the acting admin actually holds
    // _site_access_privacy; without it the check would pass vacuously.
    const privilege = sql(
      `SELECT privilege FROM secObjPrivilege WHERE objectName='_site_access_privacy'
       AND roleUserGroup IN (SELECT role_name FROM secUserRole WHERE provider_no=
         (SELECT provider_no FROM security WHERE user_name='${testUser}' LIMIT 1))`);
    check(`${testUser} holds _site_access_privacy (the condition that triggered the bug)`,
      privilege.length > 0, privilege || '(none)');

    browser = await chromium.launch({
      ...(chromePath ? { executablePath: chromePath } : {}),
      args: ['--no-sandbox', '--disable-dev-shm-usage'],
    });
    const page = await browser.newPage({ viewport: { width: 1400, height: 1000 } });

    await login(page);
    check('logged in', /providercontrol/i.test(page.url()), page.url());

    const options = await roleOptions(page);
    await page.screenshot({ path: shot('assign-role-dropdown'), fullPage: true });
    check(`Assign Role dropdown offers the "${SUPER_ROOT_ROLE}" role`,
      options.includes(SUPER_ROOT_ROLE), `${options.length} options`);

    seedProvider(providerNo, lastName);
    seeded = true;

    await roleOptions(page, lastName);
    // The JSP wraps each <tr> in a <form> INSIDE the <table>; HTML parsing
    // hoists the form out and leaves the row behind, so address the row.
    const row = page.locator('tr', { hasText: providerNo }).first();
    await row.waitFor({ state: 'attached', timeout: 30000 });
    const select = row.locator('select[name="roleNew"]').first();
    await select.selectOption(SUPER_ROOT_ROLE);
    const addButton = row.locator('input[name="submit"][value="Add"]').first();
    // The Add button ships disabled; the select's onchange (enableAddRoleButton)
    // is what enables it for a role the provider does not already hold. Wait for
    // that to happen rather than clearing `disabled` here: forcing it would let
    // this check pass even when a real user cannot submit the assignment at all.
    // Playwright's click() waits for the element to be enabled as part of its
    // actionability checks, so a handler that never enables the button surfaces
    // here as a timeout instead of a silently forced click.
    let addButtonEnabled = true;
    let addButtonDetail = '';
    try {
      await addButton.click({ timeout: 30000 });
    } catch (error) {
      addButtonEnabled = false;
      addButtonDetail = `Add stayed disabled after selecting "${SUPER_ROOT_ROLE}"`;
    }
    check('the role change handler enables Add, and the click submits',
      addButtonEnabled, addButtonDetail);
    await page.waitForLoadState('domcontentloaded', { timeout: 60000 });
    await page.screenshot({ path: shot('assign-role-assigned'), fullPage: true });

    const assigned = sql(`SELECT role_name FROM secUserRole WHERE provider_no='${providerNo}'`);
    check(`"${SUPER_ROOT_ROLE}" role assigned through the browser and persisted`,
      assigned === SUPER_ROOT_ROLE, assigned || '(none)');
  } finally {
    if (browser) await browser.close();
    if (seeded) {
      let cleanupError = '';
      try {
        removeProvider(providerNo);
      } catch (error) {
        cleanupError = error.message;
      }
      // Verify, rather than trust, that nothing was left behind — then fail the
      // run if it was, so a stale fixture can never hide behind a green result.
      let leftover = 'unknown';
      try {
        leftover = sql(`SELECT COUNT(*) FROM provider WHERE provider_no='${providerNo}'`);
      } catch (error) {
        leftover = `unreadable (${error.message})`;
      }
      check(`fixture provider ${providerNo} removed`,
        leftover === '0' && cleanupError === '',
        cleanupError ? `${cleanupError} (provider rows: ${leftover})` : `provider rows: ${leftover}`);
    }
    cleanupMysqlDefaultsFile();
  }
}

run()
  .catch((error) => check('script completed without error', false, error.message))
  .finally(() => {
    fs.mkdirSync(screenshotDir, { recursive: true });
    fs.writeFileSync(path.join(screenshotDir, 'results.json'), JSON.stringify(results, null, 2));
    const failed = results.filter((result) => !result.ok);
    console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
    process.exit(failed.length ? 1 : 0);
  });
