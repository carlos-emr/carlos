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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression coverage for the Flu Billing Report demographic mapping.
 *
 * The check creates three synthetic patients and three valid flu claims, verifies
 * every displayed demographic field, the latest selected-year billing date, and
 * the blank billing-date cell of a patient with no claim that year, and exercises
 * both All Providers and individual-provider filters. Seeded rows are removed even
 * when an assertion fails; a delete that does not succeed fails the run rather
 * than leaving synthetic patients behind silently.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:flu-billing-report-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=oscar
 *   ALLOW_NON_LOCAL_BASE_URL=true only for an intentional non-local test target
 *   ALLOW_NON_LOCAL_MYSQL_HOST=true only for a disposable non-local test database
 *
 * BASE_URL and MYSQL_HOST are both restricted to local targets by default, but
 * MYSQL_HOST is held to a stricter definition. Browsing accepts any private
 * network address; seeding does not, because a shared clinic database usually
 * lives on one. Only loopback and the devcontainer/docker service names reach
 * the database without ALLOW_NON_LOCAL_MYSQL_HOST=true.
 */

const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');

// 'carlos' and 'db' are the devcontainer compose service names for the app and
// the MariaDB container; both resolve only inside that private network.
const LOCAL_HOSTS = new Set([
  'localhost',
  '127.0.0.1',
  '::1',
  '0.0.0.0',
  'host.docker.internal',
  'carlos',
  'db',
]);

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

function normalizeHost(rawHost) {
  return rawHost.toLowerCase().replace(/^\[|\]$/g, '');
}

function isLocalHost(rawHost) {
  const host = normalizeHost(rawHost);
  return LOCAL_HOSTS.has(host) || isPrivateIpv4(host);
}

/*
 * Deliberately stricter than isLocalHost: no private-IPv4 carve-out. Browsing a
 * private-network host is read-only and harmless, but a shared clinic database
 * typically sits on exactly such an address, and this check writes patients and
 * claims. Only loopback and the known devcontainer/docker service names pass
 * without an explicit opt-in.
 */
function isLocalDatabaseHost(rawHost) {
  return LOCAL_HOSTS.has(normalizeHost(rawHost));
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
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

/*
 * This check writes synthetic patients and OHIP claims, so the database target is
 * gated harder than the browsing target — see isLocalDatabaseHost. Without this an
 * exported MYSQL_HOST could seed fixtures straight into a shared or production
 * patient schema, which no amount of cleanup fully undoes.
 */
function validateMysqlHost(rawHost) {
  if (!isLocalDatabaseHost(rawHost) && process.env.ALLOW_NON_LOCAL_MYSQL_HOST !== 'true') {
    throw new Error(`Refusing to seed synthetic patients into non-local MYSQL_HOST ${rawHost}; set ALLOW_NON_LOCAL_MYSQL_HOST=true for a disposable test database`);
  }
  return rawHost;
}

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || 'db');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'oscar';

const seededDemographicIds = [];
const seededHeaderIds = [];
const browserFindings = [];
let mysqlDefaults = null;

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function encodeOptionFileValue(value) {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-flu-report-mysql-'));
  const file = path.join(dir, 'client.cnf');
  fs.writeFileSync(file, `[client]\npassword=${encodeOptionFileValue(mysqlPassword)}\n`, { mode: 0o600 });
  return { dir, file };
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

function numericId(value, label) {
  assert(/^\d+$/.test(value), `${label} was not a numeric database id`);
  return Number(value);
}

function appUrl(appPath) {
  assert(appPath.startsWith('/') && !appPath.startsWith('//'), `Invalid application path ${appPath}`);
  const [pathPart, queryPart] = appPath.split('?');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${pathPart}`.replace(/\/{2,}/g, '/');
  url.search = queryPart ? `?${queryPart}` : '';
  return url.toString();
}

function providerNoForTestUser() {
  const providerNo = sql(
    `SELECT provider_no FROM security WHERE user_name='${escapeSql(testUser)}' ORDER BY security_no LIMIT 1`
  );
  assert(providerNo && /^[A-Za-z0-9_-]+$/.test(providerNo), `No valid provider number found for TEST_USER=${testUser}`);
  return providerNo;
}

function seedDemographic(fixture) {
  const demographicId = numericId(sql(
    `INSERT INTO demographic`
      + ` (last_name, first_name, phone, year_of_birth, month_of_birth, date_of_birth,`
      + ` roster_status, patient_status, provider_no, sex, lastUpdateDate, pref_name)`
      + ` VALUES ('${escapeSql(fixture.lastName)}', '${escapeSql(fixture.firstName)}',`
      + ` '${escapeSql(fixture.phone)}', '${fixture.birthYear}', '${fixture.birthMonth}',`
      + ` '${fixture.birthDay}', '${fixture.rosterStatus}', '${fixture.patientStatus}',`
      + ` '${escapeSql(fixture.providerNo)}', 'F', NOW(), '');`
      + ` SELECT LAST_INSERT_ID();`
  ), 'demographic id');
  seededDemographicIds.push(demographicId);
  return demographicId;
}

function seedFluClaim(demographicId, providerNo, patientName, billingDate, serviceCode) {
  assert(['G590A', 'G591A'].includes(serviceCode), `Unsupported flu service code ${serviceCode}`);
  assert(/^\d{4}-\d{2}-\d{2}$/.test(billingDate), `Invalid billing date ${billingDate}`);
  const headerId = numericId(sql(
    `INSERT INTO billing_on_cheader1`
      + ` (header_id, demographic_no, provider_no, demographic_name, billing_date, status)`
      + ` VALUES (0, ${demographicId}, '${escapeSql(providerNo)}',`
      + ` '${escapeSql(patientName)}', '${billingDate}', 'O');`
      + ` SELECT LAST_INSERT_ID();`
  ), 'billing header id');
  seededHeaderIds.push(headerId);
  sql(
    `INSERT INTO billing_on_item (ch1_id, service_code, service_date, status)`
      + ` VALUES (${headerId}, '${serviceCode}', '${billingDate}', 'O')`
  );
}

/*
 * Deletes every seeded row, continuing past individual failures so one blocked
 * delete cannot orphan the rest. Failures are returned rather than only logged:
 * leftover synthetic 65+ patients would show up in real Flu Billing Reports and
 * patient searches, so the caller must fail the run instead of printing PASS.
 */
function cleanupSeedData() {
  const failures = [];
  for (const headerId of [...seededHeaderIds].reverse()) {
    try {
      sql(`DELETE FROM billing_on_item WHERE ch1_id=${headerId}`);
      sql(`DELETE FROM billing_on_cheader1 WHERE id=${headerId}`);
    } catch (error) {
      failures.push(`billing_on_cheader1 id=${headerId}: ${error.message}`);
    }
  }
  for (const demographicId of [...seededDemographicIds].reverse()) {
    try {
      sql(`DELETE FROM demographic WHERE demographic_no=${demographicId}`);
    } catch (error) {
      failures.push(`demographic demographic_no=${demographicId}: ${error.message}`);
    }
  }
  return failures;
}

function cleanupMysqlDefaultsFile() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}

function wirePage(page) {
  page.on('response', (response) => {
    const responseUrl = response.url();
    if (response.status() >= 400 && !/\/favicon\.ico$|\/imageRenderingServlet\?/.test(responseUrl)) {
      // Path only: report URLs carry proNo, a real provider number, and these
      // findings are printed on every failure.
      browserFindings.push({ type: 'http', status: response.status(), path: new URL(responseUrl).pathname });
    }
  });
  page.on('console', (message) => {
    const text = message.text();
    const expected = /Content Security Policy.*report-only|Master token \[CSRF-TOKEN\]|Hidden token fields .* updated/.test(text);
    if (!expected && (message.type() === 'error' || /(ReferenceError|TypeError|SyntaxError)/.test(text))) {
      browserFindings.push({ type: `console:${message.type()}`, text });
    }
  });
  page.on('pageerror', (error) => {
    browserFindings.push({ type: 'pageerror', text: error.message });
  });
  page.on('dialog', async (dialog) => {
    browserFindings.push({ type: 'dialog', text: dialog.message() });
    await dialog.dismiss();
  });
}

async function login(page) {
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl restricts paths and validateBaseUrl restricts hosts by default
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
}

/*
 * Loads the report and returns its rows as arrays of cell text.
 *
 * Passing reportYear/providerNo as null omits the parameter entirely, which is
 * how the real entry points link to this report (leftNav.jspf and admin.jsp both
 * send only "?orderby="). Exercising that shape matters: an absent proNo used to
 * filter on provider_no = '' and render an empty worklist under a dropdown that
 * still read "All Providers".
 */
async function reportRows(page, reportYear, providerNo, expectedSelectedProvider) {
  const query = new URLSearchParams();
  if (reportYear !== null) {
    query.set('numMonth', reportYear);
  }
  if (providerNo !== null) {
    query.set('proNo', providerNo);
  }
  const suffix = query.toString() ? `?${query.toString()}` : '?orderby=';
  await page.goto(appUrl(`/oscarReport/FluBilling${suffix}`), { // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- reportYear and providerNo are database-derived validated values, and appUrl restricts the target host/path
    waitUntil: 'domcontentloaded',
    timeout: 30000,
  });
  // Only a networkidle timeout is expected here (the report page keeps some
  // long-lived connections open). Anything else — a closed target, a failed
  // navigation — must surface rather than turn into a confusing failure on the
  // innerText() call below.
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch((error) => {
    if (!/Timeout .* exceeded/.test(error.message)) {
      throw error;
    }
  });
  const body = await page.locator('body').innerText();
  assert(!/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(body), 'Flu Billing Report rendered an error page');
  assert(await page.locator('table tbody').count(), 'Flu Billing Report result table was missing');
  // The dropdown must agree with the filter that actually ran, so a mismatch
  // between "All Providers" on screen and the applied filter cannot recur.
  const expectedSelection = expectedSelectedProvider === undefined ? providerNo : expectedSelectedProvider;
  const selectedProvider = await page.locator('select[name="proNo"]').inputValue();
  assert(selectedProvider === expectedSelection,
    `Flu Billing Report selected provider ${selectedProvider} instead of ${expectedSelection}`);
  return page.locator('table tbody tr').evaluateAll((rows) => rows.map((row) =>
    [...row.querySelectorAll('td')].map((cell) => cell.textContent.trim())
  ));
}

function fixtureRow(rows, expectedName) {
  const matches = rows.filter((row) => row[0] === expectedName);
  assert(matches.length === 1, `Expected exactly one synthetic row for ${expectedName}, found ${matches.length}`);
  return matches[0];
}

function assertPatientRow(row, expected) {
  assert(row.length === 7, `Expected seven Flu Billing Report columns, found ${row.length}`);
  const actual = {
    name: row[0],
    dateOfBirth: row[1],
    age: row[2],
    rosterStatus: row[3],
    patientStatus: row[4],
    phone: row[5],
    billingDate: row[6],
  };
  assert(JSON.stringify(actual) === JSON.stringify(expected),
    `Synthetic Flu Billing Report row did not match expected values: ${JSON.stringify({ actual, expected })}`);
}

async function run() {
  let browser = null;
  let cleanupFailures = [];
  mysqlDefaults = createMysqlDefaultsFile();
  try {
    // Use a completed year so both seeded claims are valid historical data no
    // matter when the check runs. The report offers the selected year plus or
    // minus two, so the previous year remains a normal UI-supported choice.
    const reportYear = sql('SELECT YEAR(CURRENT_DATE)-1');
    assert(/^\d{4}$/.test(reportYear), `Database returned invalid current year ${reportYear}`);
    const expectedAge = sql("SELECT YEAR(CURRENT_DATE)-1940-(DATE_FORMAT(CURRENT_DATE,'%m%d')<'0615')");
    assert(/^\d{1,3}$/.test(expectedAge), `Database returned invalid fixture age ${expectedAge}`);
    const expectedSecondaryAge = sql("SELECT YEAR(CURRENT_DATE)-1941-(DATE_FORMAT(CURRENT_DATE,'%m%d')<'0102')");
    assert(/^\d{1,3}$/.test(expectedSecondaryAge), `Database returned invalid secondary fixture age ${expectedSecondaryAge}`);
    const expectedUnvaccinatedAge = sql("SELECT YEAR(CURRENT_DATE)-1942-(DATE_FORMAT(CURRENT_DATE,'%m%d')<'0309')");
    assert(/^\d{1,3}$/.test(expectedUnvaccinatedAge), `Database returned invalid unvaccinated fixture age ${expectedUnvaccinatedAge}`);
    const providerNo = providerNoForTestUser();
    const suffix = Date.now().toString(36).slice(-8);
    const primary = {
      lastName: `Flu${suffix}A`,
      firstName: 'Primary',
      phone: '416-555-0714',
      birthYear: '1940',
      birthMonth: '06',
      birthDay: '15',
      rosterStatus: 'RO',
      patientStatus: 'AC',
      providerNo,
    };
    const secondary = {
      lastName: `Flu${suffix}B`,
      firstName: 'Secondary',
      phone: '416-555-0715',
      birthYear: '1941',
      birthMonth: '01',
      birthDay: '02',
      rosterStatus: 'NR',
      patientStatus: 'UHIP',
      providerNo: 'PWNONE',
    };
    // Seeded with no flu claim at all: the unvaccinated 65+ patient this recall
    // report exists to surface. Its Billing Date cell must be blank, not the
    // literal text "&nbsp;".
    const unvaccinated = {
      lastName: `Flu${suffix}C`,
      firstName: 'Unvaccinated',
      phone: '416-555-0716',
      birthYear: '1942',
      birthMonth: '03',
      birthDay: '09',
      rosterStatus: 'FS',
      patientStatus: 'AC',
      providerNo,
    };
    primary.patientName = `${primary.lastName},${primary.firstName}`;
    secondary.patientName = `${secondary.lastName},${secondary.firstName}`;
    unvaccinated.patientName = `${unvaccinated.lastName},${unvaccinated.firstName}`;

    const primaryId = seedDemographic(primary);
    const secondaryId = seedDemographic(secondary);
    seedDemographic(unvaccinated);
    seedFluClaim(primaryId, primary.providerNo, primary.patientName, `${reportYear}-01-15`, 'G590A');
    seedFluClaim(primaryId, primary.providerNo, primary.patientName, `${reportYear}-10-20`, 'G591A');
    seedFluClaim(secondaryId, secondary.providerNo, secondary.patientName, `${reportYear}-05-05`, 'G590A');

    const launchOptions = {
      headless: true,
      args: ['--no-sandbox', '--disable-dev-shm-usage'],
    };
    if (chromePath) {
      launchOptions.executablePath = chromePath;
    }
    browser = await chromium.launch(launchOptions);
    const context = await browser.newContext({
      ignoreHTTPSErrors: isLocalHost(baseUrl.hostname),
      viewport: { width: 1440, height: 1000 },
    });
    const page = await context.newPage();
    wirePage(page);
    await login(page);

    const allProviderRows = await reportRows(page, reportYear, '-1');
    assertPatientRow(fixtureRow(allProviderRows, primary.patientName), {
      name: primary.patientName,
      dateOfBirth: '1940-06-15',
      age: expectedAge,
      rosterStatus: primary.rosterStatus,
      patientStatus: primary.patientStatus,
      phone: primary.phone,
      billingDate: `${reportYear}-10-20`,
    });
    assertPatientRow(fixtureRow(allProviderRows, secondary.patientName), {
      name: secondary.patientName,
      dateOfBirth: '1941-01-02',
      age: expectedSecondaryAge,
      rosterStatus: secondary.rosterStatus,
      patientStatus: secondary.patientStatus,
      phone: secondary.phone,
      billingDate: `${reportYear}-05-05`,
    });
    assertPatientRow(fixtureRow(allProviderRows, unvaccinated.patientName), {
      name: unvaccinated.patientName,
      dateOfBirth: '1942-03-09',
      age: expectedUnvaccinatedAge,
      rosterStatus: unvaccinated.rosterStatus,
      patientStatus: unvaccinated.patientStatus,
      phone: unvaccinated.phone,
      billingDate: '',
    });

    const individualProviderRows = await reportRows(page, reportYear, providerNo);
    assertPatientRow(fixtureRow(individualProviderRows, primary.patientName), {
      name: primary.patientName,
      dateOfBirth: '1940-06-15',
      age: expectedAge,
      rosterStatus: primary.rosterStatus,
      patientStatus: primary.patientStatus,
      phone: primary.phone,
      billingDate: `${reportYear}-10-20`,
    });
    // The no-claim fixture is assigned to this provider, so filtering must keep it
    // and must keep its billing-date cell blank rather than dropping the row.
    assertPatientRow(fixtureRow(individualProviderRows, unvaccinated.patientName), {
      name: unvaccinated.patientName,
      dateOfBirth: '1942-03-09',
      age: expectedUnvaccinatedAge,
      rosterStatus: unvaccinated.rosterStatus,
      patientStatus: unvaccinated.patientStatus,
      phone: unvaccinated.phone,
      billingDate: '',
    });
    assert(!individualProviderRows.some((row) => row[0] === secondary.patientName),
      'Individual-provider filtering included the synthetic patient assigned to another provider');

    // The shape both real entry points use: no numMonth, no proNo. This must list
    // every eligible patient rather than filtering on an empty provider number.
    // The year defaults to the current one, so the fixtures' previous-year claims
    // correctly show as blank billing dates here.
    const defaultEntryRows = await reportRows(page, null, null, '-1');
    for (const fixture of [primary, secondary, unvaccinated]) {
      const row = fixtureRow(defaultEntryRows, fixture.patientName);
      assert(row[6] === '',
        `Default entry point showed a billing date for ${fixture.patientName} whose only claims predate the current year`);
    }

    // A malformed year must fall back to the current year, not error and not
    // drive the year-select loop past Integer.MAX_VALUE.
    const currentYear = sql('SELECT YEAR(CURRENT_DATE)');
    for (const badYear of ['abc', '+2023', '2147483645', '-5', '0025']) {
      const rows = await reportRows(page, badYear, '-1');
      assert(rows.length > 0, `Malformed numMonth=${badYear} produced no rows at all`);
      fixtureRow(rows, primary.patientName);
      // Assert what it fell back TO, not merely that it did not error.
      const shownYear = await page.locator('select[name="numMonth"]').inputValue();
      assert(shownYear === currentYear,
        `Malformed numMonth=${badYear} fell back to ${shownYear} instead of the current year ${currentYear}`);
    }

    // Whitespace around the provider must not turn into a literal filter. This is
    // the same "empty worklist under an All Providers label" failure as an absent
    // proNo, reachable from a hand-edited or copied URL.
    const paddedAllRows = await reportRows(page, null, ' -1 ', '-1');
    fixtureRow(paddedAllRows, primary.patientName);
    const paddedProviderRows = await reportRows(page, null, ` ${providerNo} `, providerNo);
    fixtureRow(paddedProviderRows, primary.patientName);
    assert(!paddedProviderRows.some((row) => row[0] === secondary.patientName),
      'Padded provider filter did not actually filter by provider');

    assert(browserFindings.length === 0, `Browser findings: ${JSON.stringify(browserFindings)}`);

    // The provider number is deliberately not reported: it is a real
    // security.provider_no, a PHI-correlating identifier, and nothing about the
    // result needs it. The seeded ids in cleanup diagnostics are different — those
    // rows are synthetic and an operator needs the ids to remove them by hand.
    console.log(JSON.stringify({
      reportYear,
      assertions: [
        'all seven patient and billing columns map correctly',
        'latest valid selected-year flu claim is displayed',
        'a patient with no claim that year renders a blank billing date',
        'All Providers includes every synthetic patient',
        'individual provider keeps both of its assigned synthetic patients and excludes the other',
        'the parameterless entry-point URL lists every patient and selects All Providers',
        'a malformed report year falls back to the current year instead of erroring',
      ],
    }, null, 2));
  } finally {
    // browser.close() can reject (crashed target, close timeout). Swallow it here
    // so the database and the cleartext MySQL password file are always cleaned up
    // rather than the rest of this block being skipped.
    if (browser) {
      await browser.close().catch((error) => {
        console.error(`WARN failed to close browser: ${error.message}`);
      });
    }
    try {
      cleanupFailures = cleanupSeedData();
    } finally {
      cleanupMysqlDefaultsFile();
    }
    cleanupFailures.forEach((failure) => console.error(`WARN ${failure}`));
  }

  // Reached only when every assertion passed. Left-behind synthetic 65+ patients
  // would pollute real Flu Billing Reports, so a dirty database is a failed run.
  if (cleanupFailures.length) {
    throw new Error(
      `Synthetic rows were left in the database and must be removed by hand:\n  ${cleanupFailures.join('\n  ')}`
    );
  }
  console.log('PASS CARLOS EMR Flu Billing Report demographic mapping and provider filters');
}

run().catch((error) => {
  console.error('FAIL CARLOS EMR Flu Billing Report Playwright check');
  console.error(error.stack || error.message);
  // A row mismatch is usually caused by something the page already reported —
  // an HTTP 500 on the report action, a JS error that broke rendering. Print the
  // collected findings on every failure, not only when they are the assertion.
  if (browserFindings.length) {
    console.error(`Browser findings during the run: ${JSON.stringify(browserFindings, null, 2)}`);
  }
  process.exit(1);
});
