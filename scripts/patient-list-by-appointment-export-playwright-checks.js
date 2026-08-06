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
 * Browser regression checks for Administration > Reports > Patient List by
 * Appointment Time (issue #3346).
 *
 * Before the fix, clicking Export issued
 * GET /carlos/patientlistbyappt?... and Struts answered 404 ("There is no
 * Action mapped for namespace [/] and action name [patientlistbyappt]"),
 * because the global struts.action.excludePattern did not list this
 * extensionless, web.xml-mapped servlet route. This script asserts that:
 *   1. Export triggers a real browser download whose suggested filename is
 *      patientlist.txt, served with HTTP 200.
 *   2. All Doctors over the seeded range returns one row per appointment.
 *   3. Provider filtering narrows the rows to that provider's patients only.
 *   4. A null appointment type exports as an empty field (not "null", and not
 *      a 500 from a NullPointerException).
 *   5. An empty date range still downloads a valid, empty attachment.
 *   6. An unauthenticated request is still rejected with 401 - excluding the
 *      route from Struts must not open a hole in the auth policy.
 *
 * This script only reads; it seeds nothing and mutates nothing. It expects the
 * three LOCAL_SEED_OBEC_REPORT_* appointments (2026-08-07..2026-08-10) to be
 * present; missing seed data is reported as a test failure so the provider and
 * representative-content assertions cannot silently pass without coverage.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/patient-list-by-appointment-export-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');
const fs = require('fs');

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);

// Matches a full dotted-quad IPv4 address only (anchored start-to-end), so a
// hostname like "10.attacker.example" cannot be mistaken for the private
// 10.0.0.0/8 range just because it starts with the same characters as one.
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
  // URL.hostname keeps the brackets on IPv6 literals (e.g. "[::1]"); strip
  // them so bracketed loopback addresses match the same as their bare form.
  const host = rawHost.toLowerCase().replace(/^\[|\]$/g, '');
  return LOCAL_HOSTS.has(host) || isPrivateIpv4(host);
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }

  const host = parsed.hostname.toLowerCase();
  if (!isLocalHost(host) && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';

// The locally seeded LOCAL_SEED_OBEC_REPORT_* appointments, all with a NULL
// appointment type on purpose. Names are FAKE-* synthetic dev data, not PHI.
const SEED_DATE_FROM = '2026-08-07';
const SEED_DATE_TO = '2026-08-10';
const PROVIDER_ALL = 'all';
const PROVIDER_WELCH = '9';
const PROVIDER_CARLOSDOC = '999998';
const EXPECTED_ROWS = {
  [PROVIDER_WELCH]: ['FAKE-Abbott,FAKE-Jerilyn,555-555-5555,555-555-5555,09:00:00,2026-08-07,,Kristen Welch,'],
  [PROVIDER_CARLOSDOC]: [
    'FAKE-Altenwerth,FAKE-Izola,555-555-5555,555-555-5555,10:00:00,2026-08-08,,doctor carlosdoc,',
    'FAKE-Altenwerth,FAKE-Josh,555-555-5555,555-555-5555,11:00:00,2026-08-10,,doctor carlosdoc,',
  ],
};

const findings = [];
const observed = [];

function appUrl(appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  // Split off the query string before assigning to url.pathname: the pathname
  // setter percent-encodes "?" instead of treating it as a query separator,
  // which silently mangles any appPath that carries query parameters.
  const [pathPart, queryPart] = appPath.split('?');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${pathPart}`.replace(/\/{2,}/g, '/');
  url.search = queryPart ? `?${queryPart}` : '';
  return url.toString();
}

function safeGoto(page, appPath, options) {
  return page.goto(appUrl(appPath), options); // nosemgrep // NOSONAR - appUrl validates local-only BASE_URL and root-relative paths.
}

function isExpectedConsoleNoise(message) {
  const text = message.text();
  return /Content Security Policy.*report-only/i.test(text)
    || /Master token \[CSRF-TOKEN\]/.test(text)
    || /Hidden token fields .* were updated with new token value/.test(text);
}

function isSevereConsoleMessage(message) {
  if (isExpectedConsoleNoise(message)) {
    return false;
  }
  const text = message.text();
  if (message.type() === 'error') {
    return !/imageRenderingServlet\?|favicon\.ico/i.test(text);
  }
  return /(ReferenceError|TypeError|SyntaxError|redeclaration|Cannot read|Cannot set)/i.test(text);
}

// patientlist.jsp calls $(document).ready(...) to wire jQuery Validate, but the
// page never loads jQuery, so the block throws "$ is not defined" and the
// required/oscarDate date validation silently never runs. That is a separate,
// pre-existing defect - the same shape affects several other report JSPs, which
// reference the custom "oscarDate" validator that only administration/index.jsp
// ever registers - and it is NOT what issue #3346 fixed. It is allowed through
// here (recorded, not blocking) so this script keeps failing on any NEW page
// error; remove this allowance once the validation stack on these pages is fixed.
function isKnownUnrelatedPageError(error) {
  return /\$ is not defined/.test(error.message);
}

function wirePage(page, label) {
  page.on('response', (response) => {
    const status = response.status();
    const responseUrl = response.url();
    if (status >= 400 && !/\/favicon\.ico$/.test(responseUrl)) {
      findings.push({ label, type: 'http', status, url: responseUrl });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      findings.push({ label, type: `console:${message.type()}`, text: message.text() });
    }
  });
  page.on('pageerror', (error) => {
    if (isKnownUnrelatedPageError(error)) {
      // Recorded, not blocking: see isKnownUnrelatedPageError.
      observed.push({ check: `${label}:known-page-error`, text: error.message });
      return;
    }
    findings.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
  page.on('dialog', async (dialog) => {
    findings.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await safeGoto(page, '/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

/**
 * Drives the real Patient List form: selects the provider, sets both dates,
 * clicks Export, and returns the captured download plus the export response.
 */
async function exportViaForm(context, label, providerNo, dateFrom, dateTo) {
  const page = await context.newPage();
  wirePage(page, label);
  await safeGoto(page, '/oscarReport/ViewPatientlist', { waitUntil: 'domcontentloaded', timeout: 30000 });

  await page.locator('select[name="provider_no"]').selectOption(providerNo);

  // flatpickr opens its calendar overlay on focus and keeps it open, so a
  // focus-driven fill() leaves an overlay that swallows the next click - both
  // the second date input and the Export button end up unreachable. Setting
  // the value directly and dispatching input/change gives jQuery Validate the
  // same signal without ever opening the calendar.
  await page.evaluate(({ from, to }) => {
    for (const [id, value] of [['date_from', from], ['date_to', to]]) {
      const input = document.getElementById(id);
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }, { from: dateFrom, to: dateTo });

  const exportResponsePromise = page.waitForResponse(
    (response) => /\/patientlistbyappt(\?|$)/.test(response.url()),
    { timeout: 30000 },
  );
  const downloadPromise = page.waitForEvent('download', { timeout: 30000 });

  await page.locator('button[type="submit"]').first().click();

  const exportResponse = await exportResponsePromise;
  const download = await downloadPromise.catch(() => null);

  // Chromium hands an attachment response to the download manager, so its body
  // is no longer readable through response.text() ("No resource with given
  // identifier"). Read the bytes the user actually receives - the saved
  // download - instead.
  const headers = await exportResponse.allHeaders();
  const downloadPath = download ? await download.path() : null;
  const result = {
    status: exportResponse.status(),
    url: exportResponse.url(),
    contentDisposition: headers['content-disposition'] || null,
    contentType: headers['content-type'] || null,
    suggestedFilename: download ? download.suggestedFilename() : null,
    body: downloadPath ? fs.readFileSync(downloadPath, 'utf8') : await exportResponse.text().catch(() => ''),
  };
  if (download) {
    await download.delete();
  }
  await page.close();
  return result;
}

function dataRows(body) {
  return body.split('\n').map((line) => line.trimEnd()).filter((line) => line.length > 0);
}

function checkDownloadEnvelope(label, result) {
  if (result.status !== 200) {
    findings.push({ label, type: 'export-status', status: result.status, url: result.url });
  }
  if (result.suggestedFilename !== 'patientlist.txt') {
    findings.push({ label, type: 'suggested-filename', actual: result.suggestedFilename });
  }
  if (!/attachment;\s*filename=patientlist\.txt/i.test(result.contentDisposition || '')) {
    findings.push({ label, type: 'content-disposition', actual: result.contentDisposition });
  }
  if (!/charset=UTF-8/i.test(result.contentType || '')) {
    findings.push({ label, type: 'content-type', actual: result.contentType });
  }
  if (/no Action mapped for namespace/i.test(result.body) || /HTTP Status 404/i.test(result.body)) {
    findings.push({ label, type: 'struts-404-body' });
  }
}

function checkRows(label, rows, expected) {
  if (rows.length !== expected.length) {
    findings.push({ label, type: 'row-count', expected: expected.length, actual: rows.length, rows });
    return;
  }
  const sortedRows = [...rows].sort();
  const sortedExpected = [...expected].sort();
  sortedExpected.forEach((expectedRow, index) => {
    if (sortedRows[index] !== expectedRow) {
      findings.push({ label, type: 'row-mismatch', expected: expectedRow, actual: sortedRows[index] });
    }
  });
  // The seeded appointments all carry a NULL type; column 7 (0-indexed 6) must
  // therefore be empty rather than the literal string "null".
  rows.forEach((row) => {
    const typeField = row.split(',')[6];
    if (typeField !== '') {
      findings.push({ label, type: 'null-type-field', actual: typeField, row });
    }
  });
}

async function checkUnauthenticatedRejection(browser) {
  const anonymous = await browser.newContext({ ignoreHTTPSErrors: isLocalHost(baseUrl.hostname) });
  try {
    const response = await anonymous.request.get(
      appUrl(`/patientlistbyappt?provider_no=all&date_from=${SEED_DATE_FROM}&date_to=${SEED_DATE_TO}`),
      { maxRedirects: 0 },
    );
    observed.push({ check: 'unauthenticated', status: response.status() });
    if (response.status() !== 401) {
      findings.push({ label: 'unauthenticated', type: 'auth-policy', status: response.status() });
    }
  } finally {
    await anonymous.close();
  }
}

(async () => {
  const launchOptions = { args: ['--no-sandbox', '--disable-dev-shm-usage'] };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }
  const browser = await chromium.launch(launchOptions);
  try {
    // Certificate validation is only relaxed for local targets (self-signed dev
    // certs are common there). A non-local target reached via ALLOW_NON_LOCAL_BASE_URL
    // still gets full TLS validation, so a spoofed/invalid cert can't silently
    // intercept the credentialed login this script performs.
    const context = await browser.newContext({
      ignoreHTTPSErrors: isLocalHost(baseUrl.hostname),
      viewport: { width: 1440, height: 1000 },
      acceptDownloads: true,
    });
    const loginPage = await login(context);
    await loginPage.close();

    const allDoctors = await exportViaForm(context, 'all-doctors', PROVIDER_ALL, SEED_DATE_FROM, SEED_DATE_TO);
    checkDownloadEnvelope('all-doctors', allDoctors);
    const allRows = dataRows(allDoctors.body);
    observed.push({ check: 'all-doctors', status: allDoctors.status, rows: allRows });

    const expectedAll = [...EXPECTED_ROWS[PROVIDER_WELCH], ...EXPECTED_ROWS[PROVIDER_CARLOSDOC]];
    if (allRows.length === 0) {
      findings.push({ label: 'all-doctors', type: 'seed-data-missing', detail: 'no rows for the seeded 2026-08-07..2026-08-10 range' });
    } else {
      checkRows('all-doctors', allRows, expectedAll);
    }

    for (const providerNo of [PROVIDER_WELCH, PROVIDER_CARLOSDOC]) {
      const label = `provider-${providerNo}`;
      const result = await exportViaForm(context, label, providerNo, SEED_DATE_FROM, SEED_DATE_TO);
      checkDownloadEnvelope(label, result);
      const rows = dataRows(result.body);
      observed.push({ check: label, status: result.status, rows });
      checkRows(label, rows, EXPECTED_ROWS[providerNo]);
    }

    const emptyRange = await exportViaForm(context, 'empty-range', PROVIDER_ALL, '2026-09-01', '2026-09-02');
    checkDownloadEnvelope('empty-range', emptyRange);
    const emptyRows = dataRows(emptyRange.body);
    observed.push({ check: 'empty-range', status: emptyRange.status, rows: emptyRows });
    if (emptyRows.length !== 0) {
      findings.push({ label: 'empty-range', type: 'unexpected-rows', rows: emptyRows });
    }

    await checkUnauthenticatedRejection(browser);

    console.log(JSON.stringify({ observed, findings }, null, 2));

    const blockingFindings = findings.filter((finding) => finding.type !== 'dialog');
    if (blockingFindings.length) {
      throw new Error(`patient list export check found ${blockingFindings.length} issue(s)`);
    }

    console.log('PASS patient list by appointment time exports patientlist.txt with correct provider filtering');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL patient list by appointment time export Playwright check');
  console.error(error.stack || error.message);
  process.exit(1);
});
