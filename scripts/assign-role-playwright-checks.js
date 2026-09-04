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
 *   ALLOW_NON_LOCAL_MYSQL_HOST=true only for a disposable non-local test database
 */
const assert = require('assert');
const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const { randomInt } = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { buildArtifactPath } = require('./eform-local-playwright-utils');

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'db', 'carlos']);

/*
 * Hosts that are unambiguously this machine or its private compose network.
 * Deliberately narrower than isLocalHost: the database target keys off this,
 * because this check WRITES a provider row and an `admin` role grant.
 */
const EXACT_LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', 'db', 'carlos']);

function normalizeHost(rawHost) {
  return rawHost.toLowerCase().replace(/^\[|\]$/g, '');
}

/*
 * Match four numeric octets, not a `10.`-style prefix: a bare prefix test also
 * accepts DNS names such as `10.attacker.example`, which would slip past the
 * non-local opt-in and receive a real login.
 */
function isPrivateIpv4(host) {
  const match = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(host);
  if (!match) {
    return false;
  }
  const octets = match.slice(1).map(Number);
  if (octets.some((octet) => octet > 255)) {
    return false;
  }
  const [a, b] = octets;
  return a === 10 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31);
}

function isLocalHost(rawHost) {
  const host = normalizeHost(rawHost);
  return LOCAL_HOSTS.has(host) || isPrivateIpv4(host);
}

function isExactLocalHost(rawHost) {
  return EXACT_LOCAL_HOSTS.has(normalizeHost(rawHost));
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  // Credentials in the URL would travel into Playwright navigations and can
  // surface in request or failure logging, so reject them outright.
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not contain embedded credentials');
  }
  if (!isLocalHost(parsed.hostname) && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${parsed.hostname}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  // This script logs in with real credentials, so anything that is not plainly
  // loopback has to prove its certificate.
  if (!isExactLocalHost(parsed.hostname) && parsed.protocol !== 'https:') {
    throw new Error(`Non-loopback BASE_URL host ${parsed.hostname} must use https`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

/*
 * The database target is gated harder than the browsing target: this check
 * seeds a provider and grants it the installation-wide administrator role, so
 * an exported MYSQL_HOST must never reach a shared or production schema.
 */
function validateMysqlHost(rawHost) {
  if (!isExactLocalHost(rawHost) && process.env.ALLOW_NON_LOCAL_MYSQL_HOST !== 'true') {
    throw new Error(`Refusing to seed a provider and an admin role grant into non-local MYSQL_HOST ${rawHost}; set ALLOW_NON_LOCAL_MYSQL_HOST=true for a disposable test database`);
  }
  return rawHost;
}

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || '127.0.0.1');
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


/*
 * Playwright resolves these against the context baseURL set in run(). Passing
 * the composed absolute URL into goto() instead would route an environment
 * value straight into the navigation sink.
 */
function playwrightBaseUrl() {
  return `${baseUrl.origin}${baseUrl.pathname.replace(/\/$/, '')}/`;
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

/*
 * execFileSync's own message embeds the whole command line and the failing
 * statement, so never surface it raw. Take only the ERROR line, drop the
 * fragment mysql quotes back in "near '...'", mask digit runs (fixture provider
 * numbers), and cap the length.
 */
function describeMysqlFailure(error) {
  const errorLine = String(error.stderr || '')
    .split('\n')
    .find((line) => line.startsWith('ERROR '));
  if (!errorLine) {
    return `mysql exited with status ${error.status}`;
  }
  return errorLine
    .replace(/ near '.*$/, ' near <redacted>')
    .replace(/\d{3,}/g, '###')
    .slice(0, 200);
}

function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  try {
    return execFileSync('mysql', [
      `--defaults-extra-file=${mysqlDefaults.file}`,
      '-h', mysqlHost,
      '-u', mysqlUser,
      mysqlDatabase,
      '-N', '-B', '-e', query,
      // Without a timeout an unreachable host blocks forever, and the signal
      // handler calls straight back into here — an interrupted run would hang
      // while trying to remove its own seeded rows.
    ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: mysqlTimeoutMs }).trim();
  } catch (error) {
    throw new Error(`mysql command failed: ${describeMysqlFailure(error)}`);
  }
}

function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function check(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` -- ${detail}` : ''}`);
}

function shot(name) {
  return buildArtifactPath(screenshotDir, name);
}

async function login(page) {
  await page.goto('./', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await page.locator('input[type="submit"], button[type="submit"]').first().click();
  await page.waitForLoadState('domcontentloaded', { timeout: 60000 });
}

async function roleOptions(page, keyword) {
  const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : '';
  await page.goto(`./admin/ProviderRole${query}`, { waitUntil: 'domcontentloaded', timeout: 60000 });
  const select = page.locator('select[name="roleNew"]').first();
  await select.waitFor({ state: 'attached', timeout: 60000 });
  return select.locator('option').evaluateAll((options) => options.map((option) => option.value));
}

function seedProvider(providerNo, lastName) {
  // lastUpdateDate/lastUpdateUser are NOT NULL without defaults (the audit-trail
  // columns every CARLOS table carries), so a bare column list fails on insert.
  sql(`INSERT INTO provider (provider_no, last_name, first_name, provider_type, status,
                             lastUpdateUser, lastUpdateDate)
       VALUES ('${escapeSql(providerNo)}', '${escapeSql(lastName)}', 'RoleCheck', 'doctor', '1', '-1', NOW())`);
}

/*
 * Ordered by the foreign keys that reference provider.provider_no.
 *
 * Every delete is attempted even after one fails, and every failure is
 * reported: letting the first error propagate would skip the remaining tables
 * AND the provider row itself, which is the same stranded fixture that
 * swallowing the errors produced — just arrived at from the other direction.
 *
 * @return the failures encountered, empty when the fixture is fully removed
 */
function removeProvider(providerNo) {
  const failures = [];
  const tables = ['secUserRole', 'program_provider', 'provider_facility', 'security', 'provider'];
  for (const table of tables) {
    try {
      sql(`DELETE FROM ${table} WHERE provider_no='${escapeSql(providerNo)}'`);
    } catch (error) {
      failures.push(`${table}: ${error.message}`);
    }
  }
  return failures;
}

/*
 * Cleanup has to be reachable from both run()'s finally and the signal
 * handlers, so the fixture identity lives at module scope: Node does not unwind
 * a finally block on SIGINT, and an interrupted run would otherwise strand a
 * seeded provider holding a live `admin` grant plus the cleartext password file.
 */
let seededProviderNo = null;

function runCleanupOnce() {
  const failures = [];
  if (seededProviderNo !== null) {
    const providerNo = seededProviderNo;
    // Cleared first: a second entry (finally after a signal) must not re-run
    // the deletes or double-report them.
    seededProviderNo = null;
    failures.push(...removeProvider(providerNo));
    // Verify, rather than trust, that nothing was left behind.
    try {
      const leftover = sql(`SELECT COUNT(*) FROM provider WHERE provider_no='${escapeSql(providerNo)}'`);
      if (leftover !== '0') {
        failures.push(`fixture provider rows still present: ${leftover}`);
      }
    } catch (error) {
      failures.push(`could not confirm fixture removal: ${error.message}`);
    }
  }
  cleanupMysqlDefaultsFile();
  return failures;
}

async function run() {
  const providerNo = String(900000 + randomInt(1000, 9999));
  const lastName = `Rolecheck${providerNo}`;
  let browser = null;

  try {
    fs.mkdirSync(screenshotDir, { recursive: true });
    mysqlDefaults = createMysqlDefaultsFile();

    // The narrowing only misfires when the acting admin actually holds
    // _site_access_privacy; without it the check would pass vacuously.
    const privilege = sql(
      `SELECT privilege FROM secObjPrivilege WHERE objectName='_site_access_privacy'
       AND roleUserGroup IN (SELECT role_name FROM secUserRole WHERE provider_no=
         (SELECT provider_no FROM security WHERE user_name='${escapeSql(testUser)}' LIMIT 1))`);
    check(`${testUser} holds _site_access_privacy (the condition that triggered the bug)`,
      privilege.length > 0, privilege || '(none)');

    browser = await chromium.launch({
      ...(chromePath ? { executablePath: chromePath } : {}),
      args: ['--no-sandbox', '--disable-dev-shm-usage'],
    });
    const page = await browser.newPage({
      baseURL: playwrightBaseUrl(),
      viewport: { width: 1400, height: 1000 },
    });

    await login(page);
    check('logged in', /providercontrol/i.test(page.url()), page.url());

    const options = await roleOptions(page);
    await page.screenshot({ path: shot('assign-role-dropdown'), fullPage: true });
    check(`Assign Role dropdown offers the "${SUPER_ROOT_ROLE}" role`,
      options.includes(SUPER_ROOT_ROLE), `${options.length} options`);

    seedProvider(providerNo, lastName);
    seededProviderNo = providerNo;

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
      // click() also fails when the element is detached, hidden or obstructed,
      // so read the real state before blaming the change handler — otherwise a
      // failing check points at the wrong thing.
      const stillDisabled = await addButton.isDisabled().catch(() => null);
      addButtonDetail = stillDisabled === true
        ? `Add stayed disabled after selecting "${SUPER_ROOT_ROLE}"`
        : `Add could not be clicked (disabled=${stillDisabled}): ${String(error.message).split('\n')[0].slice(0, 200)}`;
    }
    check('the role change handler enables Add, and the click submits',
      addButtonEnabled, addButtonDetail);
    await page.waitForLoadState('domcontentloaded', { timeout: 60000 });
    await page.screenshot({ path: shot('assign-role-assigned'), fullPage: true });

    const assigned = sql(`SELECT role_name FROM secUserRole WHERE provider_no='${escapeSql(providerNo)}'`);
    check(`"${SUPER_ROOT_ROLE}" role assigned through the browser and persisted`,
      assigned === SUPER_ROOT_ROLE, assigned || '(none)');
  } finally {
    if (browser) await browser.close();
    const wasSeeded = seededProviderNo !== null;
    const failures = runCleanupOnce();
    // A dirty run must never exit green: leaving a provider that holds the
    // installation-wide administrator role behind is exactly what this check
    // must not do quietly.
    if (wasSeeded) {
      check(`fixture provider ${providerNo} removed`,
        failures.length === 0, failures.join('; '));
    }
  }
}

/*
 * Node does not run finally blocks on SIGINT/SIGTERM, so remove the seeded rows
 * and the cleartext password file here too. The browser is left to the OS; only
 * the database rows and that file matter.
 */
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    console.error(`\n${signal} received; removing seeded rows before exiting.`);
    let failures = [];
    try {
      failures = runCleanupOnce();
    } catch (error) {
      console.error(`WARN cleanup after ${signal} failed: ${error.message}`);
    }
    failures.forEach((failure) => console.error(`WARN ${failure}`));
    process.exit(130);
  });
}

run()
  .catch((error) => check('script completed without error', false, error.message))
  .finally(() => {
    fs.mkdirSync(screenshotDir, { recursive: true });
    fs.writeFileSync(buildArtifactPath(screenshotDir, 'results', '.json'), JSON.stringify(results, null, 2));
    const failed = results.filter((result) => !result.ok);
    console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
    process.exit(failed.length ? 1 : 0);
  });
