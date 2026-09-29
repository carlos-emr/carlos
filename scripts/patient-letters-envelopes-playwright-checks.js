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
 * Browser regression checks for patient letters and envelopes (issue #3963).
 *
 * Before the fix:
 *   - Uploading a letter template stored the Struts temp name (upload_*.tmp), and under
 *     Struts 7 the upload never reached the action at all.
 *   - GET /report/GenerateEnvelopes with no patient selected threw a NullPointerException (500).
 *   - The printer-name preference was written unescaped into the PDF's JavaScript.
 *
 * This script asserts that:
 *   1. Envelopes with no (or no usable) demos redisplay Generate Letters with a notice, not a 500.
 *   2. Envelopes for a real patient stream a PDF (%PDF) with no error page appended.
 *   3. Uploading a template through Manage Letters stores and lists the user's file name, and the
 *      Download link returns that name in Content-Disposition.
 *   4. Letter template upload and letter generation reject GET with 405.
 *   5. Generate Letters blocks an empty selection in the browser, the server answers a direct
 *      empty POST with the notice, and a POST with a patient returns a PDF.
 *   6. The Generate Envelopes button no longer puts the CSRF token into the URL.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/patient-letters-envelopes-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   DEMOGRAPHIC_NO=<id of a test patient>   (otherwise looked up with SQL)
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   MYSQL_DOCKER_CONTAINER=<name>   run the mysql client inside this container instead of locally
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * SQL and LETTER_DOCUMENT_DIR (the local application document directory) are required so
 * every owned template, document, follow-up and PDF can be removed and verified after the run.
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { sqlString } = require('./lib/playwright-harness');

const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);

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
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not embed a username or password');
  }
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
const mysqlContainer = process.env.MYSQL_DOCKER_CONTAINER || '';
const stamp = `${Date.now()}_${process.pid}`;
const documentDirectory = process.env.LETTER_DOCUMENT_DIR;
const reportName = `PW_LETTER_${stamp}`;
// Spaces and a path prefix exercise the sanitizer; the stored name must be the basename with
// spaces turned into underscores, never the Struts upload_*.tmp name.
const uploadName = `PW Letter ${stamp}.jrxml`;
const expectedStoredName = `PW_Letter_${stamp}.jrxml`;

/** Small JasperReports 7 template: a title band with static text, no parameters. */
const MINIMAL_JRXML = `<?xml version="1.0" encoding="UTF-8"?>
<jasperReport name="PwLetter${stamp}" pageWidth="595" pageHeight="842" columnWidth="555"
  leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20" whenNoDataType="AllSectionsNoDetail">
  <title height="40">
    <element kind="staticText" x="0" y="0" width="555" height="20">
      <text><![CDATA[CARLOS Playwright letter]]></text>
    </element>
  </title>
</jasperReport>
`;

const findings = [];
const visited = [];
let reportLetterId = '';

function encodeOptionFileValue(value) {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

let mysqlDefaults = null;

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-letters-mysql-'));
  const file = path.join(dir, 'client.cnf');
  fs.writeFileSync(file, `[client]\npassword=${encodeOptionFileValue(mysqlPassword)}\n`, { mode: 0o600 });
  return { dir, file };
}

function sql(query) {
  if (mysqlContainer) {
    // The password goes in through the environment, never argv, so it stays out of `ps` output.
    return execFileSync('docker', [
      'exec', '-i', '-e', 'MYSQL_PWD', mysqlContainer,
      'mysql', '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
    ], {
      encoding: 'utf8',
      env: { ...process.env, MYSQL_PWD: mysqlPassword },
      stdio: ['ignore', 'pipe', 'pipe'],
    }).trim();
  }
  if (!mysqlDefaults) {
    mysqlDefaults = createMysqlDefaultsFile();
  }
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
}

function trySql(query) {
  try {
    return { ok: true, out: sql(query) };
  } catch (error) {
    return { ok: false, error: error.message.split('\n')[0] };
  }
}

function appUrl(appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
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

const dialogs = [];

function wirePage(page, label) {
  page.on('response', (response) => {
    const status = response.status();
    if (status >= 400 && !isExpectedMissingAsset(status, response.url())) {
      findings.push({ label, type: 'http', status, url: response.url() });
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
    dialogs.push({ label, type: dialog.type(), text: dialog.message() });
    await dialog.accept();
  });
}

const ERROR_PAGE_PATTERN = /CARLOS has encountered an unexpected error|CARLOS Error|HTTP Status 5\d\d|Exception Report|NullPointerException/i;

// Only the matched phrase plus a little context is captured, never the whole page, so a failing
// run cannot copy patient details from an error page into CI logs.
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

function expect(condition, label, detail) {
  if (condition) {
    visited.push({ label, result: 'ok' });
  } else {
    findings.push({ label, type: 'assertion', detail });
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
  await assertNoErrorPage(page, 'login');
  await page.close();
}

function resolveDemographicNo() {
  if (process.env.DEMOGRAPHIC_NO) {
    if (!/^\d+$/.test(process.env.DEMOGRAPHIC_NO)) {
      throw new Error('DEMOGRAPHIC_NO must be numeric');
    }
    return process.env.DEMOGRAPHIC_NO;
  }
  const result = trySql("SELECT demographic_no FROM demographic WHERE patient_status = 'AC' ORDER BY demographic_no LIMIT 1");
  if (!result.ok || !/^\d+$/.test(result.out)) {
    throw new Error(`Set DEMOGRAPHIC_NO; SQL lookup failed (${result.ok ? 'no active patient' : result.error})`);
  }
  return result.out;
}

function isPdf(buffer) {
  return buffer.length > 4 && buffer.subarray(0, 4).toString('latin1') === '%PDF';
}

function pdfHasTrailingHtml(buffer) {
  // A named Struts result after a streamed PDF shows up as HTML appended after %%EOF.
  const text = buffer.toString('latin1');
  const eof = text.lastIndexOf('%%EOF');
  return eof >= 0 && /<html|<!DOCTYPE/i.test(text.slice(eof));
}

async function checkEnvelopesWithoutSelection(context) {
  const page = await context.newPage();
  wirePage(page, 'envelopes-empty');
  for (const [label, query] of [
    ['envelopes-no-demos', ''],
    ['envelopes-unusable-demos', '?demos=abc&demos=&demos=999999999'],
  ]) {
    const response = await safeGoto(page, `/report/GenerateEnvelopes${query}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
    expect(response && response.status() === 200, `${label}: HTTP 200`, { status: response && response.status() });
    await assertNoErrorPage(page, label);
    const notice = page.locator('#noPatientsSelected');
    expect(await notice.count() === 1, `${label}: no-patients notice shown`, { url: page.url() });
  }
  await page.close();
}

async function withPreservedCleanup(body, cleanups) {
  let failed = false;
  let primaryError;
  let result;
  try {
    result = await body();
  } catch (error) {
    failed = true;
    primaryError = error;
  }
  const cleanupErrors = [];
  for (const cleanup of cleanups) {
    try { await cleanup(); } catch (error) { cleanupErrors.push(error); }
  }
  if (failed) {
    for (const error of cleanupErrors) console.error(`Additional cleanup failure: ${error.message}`);
    throw primaryError;
  }
  if (cleanupErrors.length) throw new AggregateError(cleanupErrors, 'Owned fixture cleanup failed');
  return result;
}

async function checkUnicodeEnvelope(context) {
  // demographic.last_name is varchar(30); an oversized marker is silently
  // truncated by older installations before the PDF renderer sees it.
  const marker = `FAKE_LETTER_${Date.now().toString(36)}_${process.pid}`;
  const provider = sql(`SELECT provider_no FROM security WHERE user_name=${sqlString(testUser)}`);
  if (!provider) throw new Error('Test provider was not found');
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'letter-envelope-pdf-'));
  let patient;
  await withPreservedCleanup(async () => {
    patient = sql(`INSERT INTO demographic
      (first_name,last_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES ('Łukasz Жуков',${sqlString(marker)},'1980','01','02','F','AC',${sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    if (!/^[1-9]\d*$/.test(patient)) throw new Error('Synthetic envelope patient was not created');
    expect(sql(`SELECT last_name FROM demographic WHERE demographic_no=${patient}`) === marker,
      'unicode-envelope: synthetic patient name survived the database round trip', {marker});
    const response = await context.request.get(appUrl(`/report/GenerateEnvelopes?demos=${patient}`));
    const bytes = await response.body();
    expect(response.status() === 200 && isPdf(bytes), 'unicode-envelope: complete PDF returned', {status:response.status()});
    const file = path.join(temporary, 'envelope.pdf');
    fs.writeFileSync(file, bytes);
    const text = execFileSync('pdftotext', [file, '-'], {encoding:'utf8'});
    // The envelope column may wrap between the two names; whitespace is not
    // part of the clinical value that this extraction assertion protects.
    const normalizedText = text.replace(/\s+/g, ' ');
    const hasName = normalizedText.includes('Łukasz Жуков');
    const hasMarker = normalizedText.includes(marker);
    expect(hasName && hasMarker, 'unicode-envelope: original patient name preserved in installed PDF',
      {hasName, hasMarker, marker, text: normalizedText.slice(0, 400)});
  }, [
    () => fs.rmSync(temporary, {recursive:true, force:true}),
    () => {
      if (!patient) return;
      sql(`DELETE FROM demographic WHERE demographic_no=${patient} AND first_name='Łukasz Жуков' AND last_name=${sqlString(marker)}`);
      if (sql(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) !== '0')
        throw new Error('Owned envelope patient remains');
    },
  ]);
}

async function checkEnvelopePdf(context, demographicNo) {
  const response = await context.request.get(appUrl(`/report/GenerateEnvelopes?demos=${demographicNo}`));
  const body = await response.body();
  expect(response.status() === 200, 'envelopes-pdf: HTTP 200', { status: response.status() });
  expect(/application\/pdf/.test(response.headers()['content-type'] || ''), 'envelopes-pdf: content type',
    { contentType: response.headers()['content-type'] });
  expect(isPdf(body), 'envelopes-pdf: body starts with %PDF', { head: body.subarray(0, 16).toString('latin1') });
  expect(!pdfHasTrailingHtml(body), 'envelopes-pdf: no HTML appended after the PDF', {});
}

async function checkMutatorsRejectGet(context) {
  for (const route of ['/report/ManageLetters', '/report/GenerateLetters']) {
    const response = await context.request.get(appUrl(route), { maxRedirects: 0 });
    expect(response.status() === 405, `GET ${route} rejected with 405`, { status: response.status() });
  }
}

async function uploadTemplate(context) {
  const page = await context.newPage();
  wirePage(page, 'manage-letters');
  await safeGoto(page, '/report/ViewManageLetters', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.locator('input[name="reportFile"]').setInputFiles({
    name: uploadName,
    mimeType: 'text/xml',
    buffer: Buffer.from(MINIMAL_JRXML, 'utf8'),
  });
  await page.locator('input[name="reportName"]').fill(reportName);
  await clickAndAwaitReload(page, page.locator('form[action$="/report/ManageLetters"] input[type="submit"]'));
  await assertNoErrorPage(page, 'manage-letters-upload');

  await safeGoto(page, '/report/ViewManageLetters', { waitUntil: 'domcontentloaded', timeout: 30000 });
  const row = page.locator('tr', { hasText: reportName });
  expect(await row.count() === 1, 'manage-letters: uploaded template is listed', { reportName });
  if (await row.count() === 1) {
    const cells = await row.locator('td').allInnerTexts();
    reportLetterId = (cells[0] || '').trim();
    const storedName = (cells[3] || '').trim();
    expect(storedName === expectedStoredName, 'manage-letters: original file name stored', { storedName, expectedStoredName });
    expect(!/^upload_/i.test(storedName) && !/\.tmp$/i.test(storedName), 'manage-letters: not the Struts temp name', { storedName });

    const download = await context.request.get(appUrl(`/report/DownloadLetter?reportID=${encodeURIComponent(reportLetterId)}`));
    const disposition = download.headers()['content-disposition'] || '';
    expect(disposition.includes(`filename="${expectedStoredName}"`), 'download-letter: Content-Disposition uses stored name', { disposition });
  }
  await page.close();
}

async function checkInvalidUpload(context) {
  const page = await context.newPage();
  await safeGoto(page, '/report/ViewManageLetters', {waitUntil:'domcontentloaded'});
  const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
  for (const [name, content] of [[reportName, 'not XML'], ['', MINIMAL_JRXML]]) {
    const response = await context.request.post(appUrl('/report/ManageLetters'), {multipart:{
      reportName:name, 'CSRF-TOKEN':token,
      reportFile:{name:uploadName, mimeType:'text/xml', buffer:Buffer.from(content)},
    }});
    const html = await response.text();
    expect(response.status() === 400 && /id="letterUploadFailed"/.test(html), 'invalid-upload: visible failure', {status:response.status()});
    expect(sql(`SELECT COUNT(*) FROM report_letters WHERE report_name='${reportName}'`) === '0', 'invalid-upload: nothing saved', {});
  }
  await page.close();
}

async function checkGenerateLetters(context, demographicNo) {
  const page = await context.newPage();
  wirePage(page, 'generate-letters');
  await safeGoto(page, `/report/ViewGenerateLetters?demo=${demographicNo}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNoErrorPage(page, 'generate-letters');
  expect(await page.locator('#noPatientsSelected').count() === 0, 'generate-letters: no notice on first render', {});

  const select = page.locator('select[name="reportLetter"]');
  if (reportLetterId) {
    await select.selectOption(reportLetterId);
  }
  const letterId = await select.inputValue();
  const csrfToken = await page.evaluate(() => {
    const el = document.querySelector('input[name="CSRF-TOKEN"]');
    return el ? el.value || '' : '';
  });
  expect(csrfToken.length > 0, 'generate-letters: CSRF token bootstrapped on the form', {});

  // Envelope button: request URL must carry demos but not the CSRF token.
  await page.route('**/report/GenerateEnvelopes**', route => route.fulfill({ status: 204, body: '' }));
  const [envelopeResponse] = await Promise.all([
    page.waitForResponse('**/report/GenerateEnvelopes**', { timeout: 10000 }),
    page.locator('input[type="button"][onclick*="genEnvelopes"]').click(),
  ]);
  const envelopeUrl = envelopeResponse.url();
  expect(envelopeUrl.includes(`demos=${demographicNo}`), 'generate-envelopes-button: demos in URL', { envelopeUrl: envelopeUrl.replace(/CSRF-TOKEN=[^&]*/, 'CSRF-TOKEN=<redacted>') });
  expect(!/CSRF-TOKEN=/i.test(envelopeUrl), 'generate-envelopes-button: CSRF token kept out of URL', {});
  await page.unroute('**/report/GenerateEnvelopes**');

  // Client-side guard: nothing checked means an alert and no navigation.
  await safeGoto(page, `/report/ViewGenerateLetters?demo=${demographicNo}`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.locator('input[name="demos"]').uncheck();
  const dialogCountBefore = dialogs.length;
  const urlBefore = page.url();
  await Promise.all([
    page.waitForEvent('dialog', { timeout: 10000 }),
    page.locator('#listDemographic input[type="submit"]').click(),
  ]);
  expect(dialogs.length === dialogCountBefore + 1 && /No patients selected/i.test(dialogs[dialogs.length - 1].text),
    'generate-letters: empty selection blocked in the browser', { dialogs: dialogs.slice(dialogCountBefore) });
  expect(page.url() === urlBefore, 'generate-letters: no navigation on empty selection', { url: page.url() });

  // Server-side guard: a direct POST with no demos gets the page back with the notice.
  const empty = await context.request.post(appUrl('/report/GenerateLetters'), {
    form: { reportLetter: letterId, 'CSRF-TOKEN': csrfToken },
  });
  const emptyHtml = await empty.text();
  expect(empty.status() === 200, 'generate-letters-empty-post: HTTP 200', { status: empty.status() });
  expect(/id="noPatientsSelected"/.test(emptyHtml), 'generate-letters-empty-post: notice rendered', {});
  expect(!ERROR_PAGE_PATTERN.test(emptyHtml), 'generate-letters-empty-post: no error page', { signature: extractErrorSignature(emptyHtml) });

  // Happy path: a patient selected returns the concatenated letters PDF.
  if (reportLetterId) {
    const letters = await context.request.post(appUrl('/report/GenerateLetters'), {
      form: { reportLetter: reportLetterId, demos: demographicNo, addFollowUp:'ON', followupType:'FLUF', followupValue:'L1', message:reportName, 'CSRF-TOKEN': csrfToken },
    });
    const body = await letters.body();
    expect(letters.status() === 200, 'generate-letters-pdf: HTTP 200', { status: letters.status() });
    expect(/application\/pdf/.test(letters.headers()['content-type'] || ''), 'generate-letters-pdf: content type',
      { contentType: letters.headers()['content-type'] });
    expect(isPdf(body), 'generate-letters-pdf: body starts with %PDF', { head: body.subarray(0, 16).toString('latin1') });
    expect(!pdfHasTrailingHtml(body), 'generate-letters-pdf: no HTML appended after the PDF', {});
    expect(sql(`SELECT COUNT(*) FROM document WHERE docdesc='${reportLetterId}-${reportName}'`) === '1', 'letters: document saved once', {});
    expect(sql(`SELECT COUNT(*) FROM measurements WHERE comments='${reportName}' AND type='FLUF' AND demographicNo=${Number(demographicNo)}`) === '1', 'letters: follow-up saved once', {});
    // A malformed ID reaches the application through the WAF; integer overflow is covered in Java tests.
    const invalid = await context.request.post(appUrl('/report/GenerateLetters'), {
      headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      data: new URLSearchParams([['reportLetter', reportLetterId], ['demos', demographicNo], ['demos', 'abc'],
        ['addFollowUp', 'ON'], ['followupType', 'FLUF'], ['followupValue', 'L1'], ['message', reportName], ['CSRF-TOKEN', csrfToken]]).toString(),
    });
    const invalidHtml = await invalid.text();
    expect(invalid.status() === 400 && /id="letterGenerationFailed"/.test(invalidHtml), 'letters: invalid selection visibly refused', {status:invalid.status()});
    // The argument is an application response body, parsed into a detached document to read inputs.
    // No code interpolation, navigation or request target is derived from it.
    // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection
    const selectedPatients = html => page.evaluate(markup => Array.from(
      new DOMParser().parseFromString(markup, 'text/html').querySelectorAll('input[name="demos"]'), input => input.value), html);
    expect(JSON.stringify(await selectedPatients(invalidHtml)) === JSON.stringify([String(demographicNo)]),
      'letters: valid selected patient retained on error page; invalid ID omitted', {});
    expect(sql(`SELECT COUNT(*) FROM measurements WHERE comments='${reportName}' AND type='FLUF' AND demographicNo=${Number(demographicNo)}`) === '1', 'letters: failed request did not record follow-up', {});
    const partial = await context.request.get(appUrl(`/report/GenerateEnvelopes?demos=${demographicNo}&demos=abc`));
    const partialHtml = await partial.text();
    expect(partial.status() === 400 && /id="letterSelectionIncomplete"/.test(partialHtml), 'envelopes: incomplete selection refused', {status:partial.status()});
    expect(JSON.stringify(await selectedPatients(partialHtml)) === JSON.stringify([String(demographicNo)]),
      'envelopes: valid selected patient retained on error page; invalid ID omitted', {});
  }
  await page.close();
}

async function cleanup(demographicNo) {
  if (!/^[1-9]\d*$/.test(String(demographicNo)) || !Number.isSafeInteger(Number(demographicNo))) {
    throw new Error('Invalid owned follow-up patient');
  }
  const owned = sql(`SELECT ID FROM report_letters WHERE report_name = '${reportName}'`);
  const ids = owned ? owned.split('\n').map(Number) : [];
  if (!ids.length) return;
  if (ids.some(id => !Number.isSafeInteger(id) || id <= 0)) throw new Error('Invalid owned template ID');
  for (const id of ids) {
    const rows = sql(`SELECT document_no,docfilename FROM document WHERE docdesc='${id}-${reportName}'`);
    const documents = rows ? rows.split('\n').map(row => row.split('\t')) : [];
    for (const [number, filename] of documents) {
      if (!/^\d+$/.test(number) || path.basename(filename) !== filename || !filename.startsWith(`letter-${id}-`) || !filename.endsWith('.pdf')) {
        throw new Error('Refusing to remove a file not owned by this letter test');
      }
      fs.rmSync(path.join(documentDirectory, filename), {force:true});
    }
    sql(`START TRANSACTION;
      DELETE d,c FROM document d LEFT JOIN ctl_document c ON c.document_no=d.document_no WHERE d.docdesc='${id}-${reportName}';
      DELETE FROM log_letters WHERE report_id=${id};
      DELETE FROM report_letters WHERE ID=${id} AND report_name='${reportName}';
      DELETE FROM measurements WHERE comments='${reportName}' AND type='FLUF' AND demographicNo=${Number(demographicNo)};
      COMMIT`);
    if (sql(`SELECT COUNT(*) FROM document WHERE docdesc='${id}-${reportName}'`) !== '0') throw new Error('Owned letter documents remain');
  }
  if (sql(`SELECT COUNT(*) FROM report_letters WHERE report_name='${reportName}'`) !== '0') throw new Error('Owned template remains');
  if (sql(`SELECT COUNT(*) FROM measurements WHERE comments='${reportName}' AND type='FLUF' AND demographicNo=${Number(demographicNo)}`) !== '0') throw new Error('Owned follow-up remains');
}

(async () => {
  const launchOptions = { headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }
  if (!documentDirectory || !path.isAbsolute(documentDirectory) || !fs.statSync(documentDirectory).isDirectory()) {
    throw new Error('LETTER_DOCUMENT_DIR must name the local application document directory for verified cleanup');
  }
  execFileSync('pdftotext', ['-v'], {stdio:'ignore'});
  sql('SELECT 1');
  const demographicNo = resolveDemographicNo();
  const browser = await chromium.launch(launchOptions);
  let context;
  await withPreservedCleanup(async () => {
    context = await browser.newContext({
      ignoreHTTPSErrors: isLocalHost(baseUrl.hostname),
      viewport: { width: 1280, height: 900 },
    });
    await login(context);
    await checkEnvelopesWithoutSelection(context);
    await checkEnvelopePdf(context, demographicNo);
    await checkUnicodeEnvelope(context);
    await checkMutatorsRejectGet(context);
    await checkInvalidUpload(context);
    await uploadTemplate(context);
    await checkGenerateLetters(context, demographicNo);

    console.log(JSON.stringify({ visited, dialogs, findings }, null, 2));
    if (findings.length) {
      throw new Error(`patient letters/envelopes check found ${findings.length} issue(s)`);
    }
    console.log('PASS patient letters keep the uploaded name; envelopes and letters handle empty selections');
  }, [
    () => cleanup(demographicNo),
    () => { if (mysqlDefaults) fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true }); },
    () => browser.close(),
  ]);
})().catch((error) => {
  try {
    if (mysqlDefaults) fs.rmSync(mysqlDefaults.dir, {recursive:true, force:true});
  } catch (cleanupError) {
    console.error(`Additional cleanup failure: ${cleanupError.message}`);
  }
  console.error('FAIL patient letters/envelopes Playwright check');
  console.error(error.stack || error.message);
  process.exit(1);
});
