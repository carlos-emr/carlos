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
 * Browser regression checks for stored XSS in the demographic and Rourke
 * export pages (PR #2452).
 *
 * Before the fix, demographicExport.jsp and rourkeExport.jsp rendered
 * admin/DB-sourced values straight into <option> elements, a download link URL
 * and history table cells:
 *
 *     <option value="<%=setName%>"><%=setName%>
 *     <a href='...&zipFile=<%=file%>'><%=file %></a>
 *     <td><%=dataExport.getUser()%>
 *
 * so a patient-set name or a persisted export filename containing markup broke
 * out of the attribute and executed in the admin's browser. This script seeds
 * those exact DB fields with breakout payloads and then asserts, on the live
 * pages, that:
 *
 *   1. No element from the payload is ever parsed into the DOM (the payload's
 *      <svg> never appears inside the select or the history table).
 *   2. The payload's inline handler never runs (no sentinel on window, and no
 *      page error from the undefined function the handler calls).
 *   3. The raw server response never contains the payload unescaped.
 *   4. The encoding is LOSSLESS, so the fix did not break the feature: the
 *      option's value and visible text still equal the seeded name exactly,
 *      and the Rourke download link's zipFile query parameter still decodes
 *      back to the seeded filename.
 *
 * Coverage note: rourkeExport.jsp only lists dataExport rows whose `type` is
 * exactly 'Rourke' (RourkeExport2Action -> DataExportDao.findAllByType), so the
 * `type` cell cannot carry an injected value through this page. Its encoding is
 * defence-in-depth, and this script asserts that cell renders the literal type
 * rather than pretending to inject into it.
 *
 * The demographicSets.set_name column is varchar(20), so the patient-set
 * payload has to fit in 20 characters -- hence the terse `"><svg onload=x()>`
 * rather than a longer sentinel-assigning handler. It still proves attribute
 * breakout, and check (2) covers execution via the page-error path.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/export-jsp-xss-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

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
const mysqlHost = process.env.MYSQL_HOST || 'db';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

const ROURKE_TYPE = 'Rourke';
const stamp = String(Date.now()).slice(-8);

// demographicSets.set_name is varchar(20); this payload is 17 characters and
// still breaks out of both the value attribute and the element text if either
// is rendered raw. x() is deliberately undefined so that execution surfaces as
// a page error even though the payload has no room for a sentinel assignment.
const SET_NAME_PAYLOAD = '"><svg onload=x()>';
// dataExport.file / dataExport.user are varchar(255), so these carry an
// explicit window sentinel as well as the breakout.
const EXPORT_FILE_PAYLOAD = `PWXSS${stamp}"><svg onload="window.__xssFile=1">.zip`;
const EXPORT_USER_PAYLOAD = `PWXSS${stamp}<svg onload="window.__xssUser=1">`;

const findings = [];
const visited = [];
const notes = [];

// A MariaDB option file is NOT a raw key=value format: in a value, '\' starts an
// escape sequence and an unquoted '#' starts a comment that truncates the rest
// of the line, so writing the password verbatim can silently corrupt it (see
// the fix for the same issue in login-playwright-checks.js, #3308). Double-quoting
// neutralizes '#' and surrounding whitespace; '\' and '"' still need escaping.
function encodeOptionFileValue(value) {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-exportxss-mysql-'));
  const file = path.join(dir, 'client.cnf');
  fs.writeFileSync(file, `[client]\npassword=${encodeOptionFileValue(mysqlPassword)}\n`, { mode: 0o600 });
  return { dir, file };
}

let mysqlDefaults = null;

function sql(query) {
  if (!mysqlDefaults) {
    mysqlDefaults = createMysqlDefaultsFile();
  }
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

// The seeded rows are the whole point of the check, so seeding runs before the
// browser starts and is torn down in the finally block of the runner.
function seedPayloadRows() {
  removePayloadRows();
  // demographic_no 1 only has to exist as a set member; the export pages list
  // distinct set names and never dereference the demographic for this render.
  sql(`INSERT INTO demographicSets (demographic_no, set_name, eligibility, archive)
       VALUES (1, '${escapeSql(SET_NAME_PAYLOAD)}', '1', '0')`);
  sql(`INSERT INTO dataExport (file, daterun, user, type)
       VALUES ('${escapeSql(EXPORT_FILE_PAYLOAD)}', NOW(), '${escapeSql(EXPORT_USER_PAYLOAD)}', '${escapeSql(ROURKE_TYPE)}')`);
}

function removePayloadRows() {
  sql(`DELETE FROM demographicSets WHERE set_name = '${escapeSql(SET_NAME_PAYLOAD)}'`);
  sql(`DELETE FROM dataExport WHERE file = '${escapeSql(EXPORT_FILE_PAYLOAD)}'`);
}

// Best-effort: a cleanup failure is logged, not thrown, so it never masks a
// real XSS finding from the checks that ran before it.
function cleanupPayloadRows() {
  try {
    removePayloadRows();
  } catch (error) {
    console.error(`WARN: failed to clean up seeded export XSS rows: ${error.message}`);
  } finally {
    if (mysqlDefaults) {
      fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
      mysqlDefaults = null;
    }
  }
}

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

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (/\/imageRenderingServlet\?/.test(responseUrl) || /\/favicon\.ico$/.test(responseUrl));
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

function wirePage(page, label) {
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      findings.push({ label, type: 'http', status, url: responseUrl });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      findings.push({ label, type: `console:${message.type()}`, text: message.text(), location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    findings.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
  page.on('dialog', async (dialog) => {
    findings.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
}

const ERROR_PAGE_PATTERN = /CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i;

// Only the matched phrase plus a little surrounding context is captured (never
// the full page body), so CI logs never risk surfacing patient-like content
// that could be rendered on an error page.
function extractErrorSignature(bodyText) {
  const match = ERROR_PAGE_PATTERN.exec(bodyText);
  if (!match) {
    return null;
  }
  const start = Math.max(0, match.index - 40);
  const end = Math.min(bodyText.length, match.index + match[0].length + 80);
  return bodyText.slice(start, end).replace(/\s+/g, ' ').trim();
}

async function assertNoErrorPage(page, label) {
  const bodyText = await page.locator('body').innerText().catch(() => '');
  const signature = extractErrorSignature(bodyText);
  if (signature) {
    findings.push({ label, type: 'error-page', url: page.url(), signature });
  }
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
  visited.push({ label: 'login', url: page.url() });
  await assertNoErrorPage(page, 'login');
  return page;
}

// The sentinels the seeded inline handlers would set if the payload ever
// executed. Checked on every export page render.
async function assertNoSentinelFired(page, label) {
  const fired = await page.evaluate(() => ({
    file: typeof window.__xssFile !== 'undefined',
    user: typeof window.__xssUser !== 'undefined',
  }));
  if (fired.file) {
    findings.push({ label, type: 'xss-executed', detail: 'window.__xssFile was set by the seeded export filename' });
  }
  if (fired.user) {
    findings.push({ label, type: 'xss-executed', detail: 'window.__xssUser was set by the seeded export user' });
  }
}

// The decisive structural check: if any payload survived as markup, the
// container has a parsed <svg> child that the page never renders on its own.
async function assertNoInjectedMarkup(page, containerSelector, label) {
  const injected = await page.locator(`${containerSelector} svg`).count();
  if (injected > 0) {
    findings.push({ label, type: 'xss-markup', selector: `${containerSelector} svg`, count: injected });
  }
}

// A page can look clean in the DOM and still have shipped the payload raw (for
// example inside a comment or a script block), so the served bytes are checked
// too. The payload's distinctive breakout prefix must never appear verbatim.
function assertResponseBodyEscaped(html, label) {
  for (const payload of [SET_NAME_PAYLOAD, EXPORT_FILE_PAYLOAD, EXPORT_USER_PAYLOAD]) {
    if (html.includes(payload)) {
      findings.push({ label, type: 'raw-payload-in-response', payloadPrefix: payload.slice(0, 24) });
    }
  }
}

// Locates the seeded patient-set <option> by its exact text and asserts the
// name round-trips through both the value attribute and the visible text.
// Lossless encoding is what keeps the export itself working: the value posted
// back has to be the real set name.
async function assertPatientSetOptionEncoded(page, selectSelector, label) {
  const options = page.locator(`${selectSelector} option`);
  const count = await options.count();
  let matched = 0;
  for (let index = 0; index < count; index += 1) {
    const option = options.nth(index);
    const text = ((await option.textContent()) || '').trim();
    if (text !== SET_NAME_PAYLOAD) {
      continue;
    }
    matched += 1;
    const value = await option.getAttribute('value');
    if (value !== SET_NAME_PAYLOAD) {
      findings.push({ label, type: 'lossy-attribute-encoding', expected: SET_NAME_PAYLOAD, actual: value });
    }
  }
  if (matched !== 1) {
    findings.push({
      label,
      type: 'seeded-set-not-rendered',
      detail: `expected exactly one <option> whose text is the seeded set name, found ${matched}`,
      selector: selectSelector,
    });
  }
}

// demographicExport.jsp: patient-set and provider <option> lists.
async function checkDemographicExportPage(context) {
  const label = 'demographic-export';
  const page = await context.newPage();
  wirePage(page, label);
  const response = await safeGoto(page, '/demographic/DemographicExport', { waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label, url: page.url() });
  await assertNoErrorPage(page, label);

  assertResponseBodyEscaped(await response.text(), label);
  await assertNoInjectedMarkup(page, '#patientSet', label);
  await assertNoSentinelFired(page, label);
  await assertPatientSetOptionEncoded(page, '#patientSet', label);

  // The provider <option> list is the other value the fix re-encoded on this
  // page. ProviderDaoImpl.getActiveProviders() is @Cacheable, so a provider
  // seeded by SQL would not reliably appear; instead this asserts that the
  // provider options the app really serves are attribute-safe -- no option
  // value or label may contain a raw angle bracket or quote.
  const providerOptions = await page.locator('#providerNo option').evaluateAll((nodes) => nodes.map((node) => ({
    value: node.getAttribute('value'),
    text: (node.textContent || '').trim(),
  })));
  if (!providerOptions.length) {
    notes.push({ label, note: 'no provider options rendered; provider-name encoding not exercised' });
  }
  for (const option of providerOptions) {
    if (/[<>"']/.test(option.value || '') || /[<>]/.test(option.text)) {
      findings.push({ label, type: 'unsafe-provider-option', value: option.value });
    }
  }

  await page.close();
}

// rourkeExport.jsp: patient-set list plus the previous-exports history table.
async function checkRourkeExportPage(context) {
  const label = 'rourke-export';
  const page = await context.newPage();
  wirePage(page, label);
  const response = await safeGoto(page, '/demographic/eRourkeExport', { waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label, url: page.url() });
  await assertNoErrorPage(page, label);

  assertResponseBodyEscaped(await response.text(), label);
  await assertNoInjectedMarkup(page, '#patientSet', label);
  await assertPatientSetOptionEncoded(page, '#patientSet', label);

  // The seeded history row: filename cell (URI component in the href plus HTML
  // text in the link label), user cell, and the fixed type cell.
  const downloadLink = page.locator(`a[href*="zipFile="]`).filter({ hasText: EXPORT_FILE_PAYLOAD });
  if (await downloadLink.count() !== 1) {
    findings.push({
      label,
      type: 'seeded-export-not-rendered',
      detail: `expected exactly one download link labelled with the seeded filename, found ${await downloadLink.count()}`,
    });
  } else {
    const href = await downloadLink.getAttribute('href');
    // Resolved against the page so a relative href parses; searchParams then
    // performs the percent-decode, which is exactly the round-trip the real
    // download depends on.
    const parsed = new URL(href, page.url());
    if (parsed.searchParams.get('zipFile') !== EXPORT_FILE_PAYLOAD) {
      findings.push({
        label,
        type: 'lossy-uri-encoding',
        detail: 'zipFile query parameter does not decode back to the seeded filename',
        actual: parsed.searchParams.get('zipFile'),
      });
    }
    if (parsed.searchParams.get('method') !== 'getFile') {
      findings.push({
        label,
        type: 'broken-download-link',
        detail: 'method=getFile did not survive as a separate query parameter',
      });
    }
  }

  const historyRow = page.locator('tr').filter({ hasText: EXPORT_FILE_PAYLOAD }).first();
  if (await historyRow.count()) {
    await assertNoInjectedMarkup(page, 'tr:has(a[href*="zipFile="])', label);
    const cells = await historyRow.locator('td').evaluateAll((nodes) => nodes.map((node) => (node.textContent || '').trim()));
    // Columns are run date, file, user, type.
    if (cells.length !== 4) {
      findings.push({ label, type: 'unbalanced-history-row', detail: `expected 4 <td> cells, found ${cells.length}` });
    }
    if (cells[2] !== EXPORT_USER_PAYLOAD) {
      findings.push({ label, type: 'lossy-user-encoding', expected: EXPORT_USER_PAYLOAD, actual: cells[2] });
    }
    // Not an injection vector (findAllByType pins this column to 'Rourke');
    // asserted so the defence-in-depth encoding stays lossless.
    if (cells[3] !== ROURKE_TYPE) {
      findings.push({ label, type: 'lossy-type-encoding', expected: ROURKE_TYPE, actual: cells[3] });
    }
  }

  await assertNoSentinelFired(page, label);
  await page.close();
}

(async () => {
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  seedPayloadRows();

  const browser = await chromium.launch(launchOptions);
  try {
    // Certificate validation is only relaxed for local targets (self-signed dev
    // certs are common there). A non-local target reached via ALLOW_NON_LOCAL_BASE_URL
    // still gets full TLS validation, so a spoofed/invalid cert can't silently
    // intercept the credentialed login this script performs.
    const context = await browser.newContext({
      ignoreHTTPSErrors: isLocalHost(baseUrl.hostname),
      viewport: { width: 1440, height: 1000 },
    });
    await login(context);

    await checkDemographicExportPage(context);
    await checkRourkeExportPage(context);

    console.log(JSON.stringify({ visited, notes, findings }, null, 2));

    if (findings.length) {
      throw new Error(`export JSP XSS check found ${findings.length} issue(s)`);
    }

    console.log('PASS export JSP output stays encoded and lossless for seeded XSS payloads');
  } finally {
    cleanupPayloadRows();
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL export JSP XSS Playwright check');
  console.error(error.stack || error.message);
  process.exit(1);
});
