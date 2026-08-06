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
 * The check creates two synthetic patients and three valid flu claims, verifies
 * every displayed demographic field and the latest selected-year billing date,
 * and exercises both All Providers and individual-provider filters. All seeded
 * rows are removed even when an assertion fails.
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
 */

const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');

const LOCAL_HOSTS = new Set([
  'localhost',
  '127.0.0.1',
  '::1',
  '0.0.0.0',
  'host.docker.internal',
  'carlos',
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

function isLocalHost(rawHost) {
  const host = rawHost.toLowerCase().replace(/^\[|\]$/g, '');
  return LOCAL_HOSTS.has(host) || isPrivateIpv4(host);
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  if (!isLocalHost(parsed.hostname) && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${parsed.hostname}`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const mysqlHost = process.env.MYSQL_HOST || 'db';
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

function cleanupSeedData() {
  for (const headerId of [...seededHeaderIds].reverse()) {
    try {
      sql(`DELETE FROM billing_on_item WHERE ch1_id=${headerId}`);
      sql(`DELETE FROM billing_on_cheader1 WHERE id=${headerId}`);
    } catch (error) {
      console.error(`WARN failed to clean synthetic billing header ${headerId}: ${error.message}`);
    }
  }
  for (const demographicId of [...seededDemographicIds].reverse()) {
    try {
      sql(`DELETE FROM demographic WHERE demographic_no=${demographicId}`);
    } catch (error) {
      console.error(`WARN failed to clean synthetic demographic ${demographicId}: ${error.message}`);
    }
  }
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
      browserFindings.push({ type: 'http', status: response.status(), url: responseUrl });
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

async function reportRows(page, reportYear, providerNo) {
  const query = new URLSearchParams({ numMonth: reportYear, proNo: providerNo });
  await page.goto(appUrl(`/oscarReport/FluBilling?${query.toString()}`), { // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- reportYear and providerNo are database-derived validated values, and appUrl restricts the target host/path
    waitUntil: 'domcontentloaded',
    timeout: 30000,
  });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const body = await page.locator('body').innerText();
  assert(!/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(body), 'Flu Billing Report rendered an error page');
  assert(await page.locator('table tbody').count(), 'Flu Billing Report result table was missing');
  const selectedProvider = await page.locator('select[name="proNo"]').inputValue();
  assert(selectedProvider === providerNo,
    `Flu Billing Report selected provider ${selectedProvider} instead of ${providerNo}`);
  return page.locator('table tbody tr').evaluateAll((rows) => rows.map((row) =>
    [...row.querySelectorAll('td')].map((cell) => cell.textContent.replace(/\u00a0/g, ' ').trim())
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
    primary.patientName = `${primary.lastName},${primary.firstName}`;
    secondary.patientName = `${secondary.lastName},${secondary.firstName}`;

    const primaryId = seedDemographic(primary);
    const secondaryId = seedDemographic(secondary);
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
    assert(!individualProviderRows.some((row) => row[0] === secondary.patientName),
      'Individual-provider filtering included the synthetic patient assigned to another provider');
    assert(browserFindings.length === 0, `Browser findings: ${JSON.stringify(browserFindings)}`);

    console.log(JSON.stringify({
      reportYear,
      providerNo,
      assertions: [
        'all seven patient and billing columns map correctly',
        'latest valid selected-year flu claim is displayed',
        'All Providers includes both synthetic patients',
        'individual provider includes only its assigned synthetic patient',
      ],
    }, null, 2));
    console.log('PASS Flu Billing Report demographic mapping and provider filters');
  } finally {
    if (browser) {
      await browser.close();
    }
    try {
      cleanupSeedData();
    } finally {
      cleanupMysqlDefaultsFile();
    }
  }
}

run().catch((error) => {
  console.error('FAIL Flu Billing Report Playwright check');
  console.error(error.stack || error.message);
  process.exit(1);
});
