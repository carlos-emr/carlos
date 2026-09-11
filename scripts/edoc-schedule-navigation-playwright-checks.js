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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the eDoc navigation header, driven the way an
 * operator hits it: open eDoc from the schedule menu (which lands on
 * /documentManager/ViewDocumentReport?...&scheduleNav=1, so the page renders the
 * shared navigation header), add a document, and look at the header again.
 *
 * The reported defect: adding a document lost the header tabs. documentReport.jsp
 * renders /WEB-INF/jsp/provider/mainMenu.jsp only while the REQUEST carries
 * scheduleNav=1. The add form did not post the flag and AddEditDocument2Action
 * answers success with a REDIRECT -- a brand-new request -- so the flag was gone
 * by the time the document list re-rendered and the provider was stranded on a
 * bare page with no way back but the browser's Back button.
 *
 * Nothing covered this: the unit tests assert the redirect's other query
 * parameters, and eform-admin-schedule-navigation covers the same class of bug on
 * a different surface entirely.
 *
 * Five scenarios:
 *   1. Entering eDoc with scheduleNav=1 renders the header AND puts the flag in
 *      the add form (the missing hidden input is the root cause; assert it
 *      directly so a regression is diagnosed, not just detected).
 *   2. Adding a document keeps the header and keeps scheduleNav=1 on the URL.
 *   3. Deleting that document -- the same page's other mutation redirect -- keeps
 *      them too.
 *   4. Restoring it through the deleted list preserves the header and makes it active again.
 *   5. Adding a link preserves the header and persists the generated link document.
 * Plus the negative: entering eDoc WITHOUT the flag must still render no header,
 * so the fix cannot be "always show the header".
 *
 * Defaults are for the local devcontainer:
 *   node scripts/edoc-schedule-navigation-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc  TEST_PASSWORD=carlos2026  TEST_PIN=2026
 *   EDOC_NAV_SCREENSHOT_DIR=/tmp   ALLOW_NON_LOCAL_BASE_URL=true
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (fixture teardown; see below)
 *   ALLOW_NON_LOCAL_MYSQL_HOST=true only for a disposable non-local test database
 *
 * FIXTURE SAFETY: creates one PDF and one link under unique descriptions and
 * only mutates those fixtures. Note that the
 * UI delete in scenario 3 is the application's SOFT delete -- status='D', row and
 * file both still there -- so it is an assertion, NOT teardown. The row is removed
 * for real in the `finally` block on ordinary success or failure. Cleanup errors
 * fail validation. Signal handling is best effort; kills, a pending server write,
 * or host panic can leave strays. Identify those by their carlos-nav-probe- prefix
 * on a disposable demo database and remove their related rows by document_no.
 */

const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const { randomUUID } = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createGracefulSignalCancellation } = require('./graceful-signal-cancellation');

/*
 * Hosts that are unambiguously this machine or its compose network. The teardown below
 * DELETEs document rows, so the database target is held to this narrower set rather than
 * BASE_URL's (which also admits private IPv4). Same sets as
 * assign-role-playwright-checks.js, which seeds rows for the same reason.
 */
const EXACT_LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', 'db', 'carlos']);

/*
 * Strictly this machine's own loopback interface. Deliberately NARROWER than EXACT_LOCAL_HOSTS,
 * which also admits the compose service names: those resolve over a real network, so a
 * certificate served on one has to be verified. Only this set may switch TLS checking off.
 */
const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '::1']);

function normalizeHost(rawHost) {
  return String(rawHost).toLowerCase().replace(/^\[|\]$/g, '');
}

function isExactLocalHost(rawHost) {
  return EXACT_LOCAL_HOSTS.has(normalizeHost(rawHost));
}

function isLoopbackHost(rawHost) {
  return LOOPBACK_HOSTS.has(normalizeHost(rawHost));
}

/** Refuses to point the fixture teardown's DELETEs at a database that is not plainly local. */
function validateMysqlHost(rawHost) {
  if (!isExactLocalHost(rawHost) && process.env.ALLOW_NON_LOCAL_MYSQL_HOST !== 'true') {
    throw new Error(`Refusing to delete probe document rows from non-local MYSQL_HOST ${rawHost};`
      + ' set ALLOW_NON_LOCAL_MYSQL_HOST=true for a disposable test database');
  }
  return rawHost;
}

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.EDOC_NAV_SCREENSHOT_DIR || '/tmp',
  mysqlHost: validateMysqlHost(process.env.MYSQL_HOST || '127.0.0.1'),
  mysqlUser: process.env.MYSQL_USER || 'root',
  mysqlPassword: process.env.MYSQL_PASSWORD || 'password',
  mysqlDatabase: process.env.MYSQL_DATABASE || 'carlos',
};

const NAV_SELECTOR = '#firstTable #navlist';

/*
 * Uncaught script errors, collected across every page. This matters more than it looks: the
 * delete and undelete handlers live in one <script> block on documentReport.jsp, so a single
 * syntax error (an unescaped apostrophe in a translated string, say) wipes out submitDocAction
 * entirely. Clicking a dead handler then does nothing -- the page does not navigate, so the URL
 * and the header still look right and the delete leg passes without deleting anything.
 */
const pageErrors = [];

function watchForPageErrors(page, label) {
  page.on('pageerror', (error) => pageErrors.push(`${label}: ${error.message}`));
}
const docDescription = `carlos-nav-probe-${randomUUID()}`;
const linkInputDescription = `${docDescription}-link`;
const linkDescription = `${linkInputDescription} (link)`;
const docTypeName = 'CARLOS Nav Probe';

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  // Credentials in the URL would ride along on every navigation and surface in Playwright's
  // own error messages, which this script prints on failure.
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not embed a username or password');
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

/** True when BASE_URL points at this machine, the only case where a bad cert is expected. */
function isLoopbackTarget() {
  return isLoopbackHost(config.baseUrl.hostname);
}

function appUrl(appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const [rawPath, search = ''] = appPath.split('?');
  const url = new URL(config.baseUrl.href);
  url.pathname = `${config.baseUrl.pathname}${rawPath}`.replace(/\/{2,}/g, '/');
  url.search = search;
  return url.toString();
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function gotoApp(page, appPath, options) {
  return page.goto(appUrl(appPath), options); // nosemgrep // NOSONAR - appUrl validates local-only BASE_URL and root-relative paths.
}

/*
 * MySQL option files treat an unquoted '#' as a comment and a backslash as an escape, so a
 * perfectly valid password containing either is silently corrupted and the teardown then fails
 * "Access denied" -- leaving the probe rows it was meant to remove. Quote and escape, as
 * assign-role-playwright-checks.js and login-playwright-checks.js do.
 */
function encodeOptionFileValue(value) {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(config.mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'edoc-nav-sql-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  fs.writeFileSync(
    file,
    `[client]\npassword=${encodeOptionFileValue(config.mysqlPassword)}\n`,
    { mode: 0o600 },
  );
  mysqlDefaults = { dir, file };
}

function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}

function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', config.mysqlHost, '-u', config.mysqlUser, config.mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
}

/**
 * Removes the rows this run created, keyed on its unique description only.
 *
 * The UI delete in scenario 3 is the application's SOFT delete -- it sets status='D' and leaves
 * both the row and the uploaded PDF in place -- so it is an assertion, not teardown. This runs
 * on ordinary success or failure; interrupted writes can still leave strays.
 */
let cleanupDone = false;
function cleanupProbeDocuments() {
  if (cleanupDone) {
    return;
  }
  cleanupDone = true;
  try {
    const ids = sql(
      `SELECT document_no FROM document WHERE docdesc IN ('${docDescription}', '${linkDescription}')`,
    ).split(/\s+/).filter(Boolean);
    if (!ids.length) return;
    assert(ids.every((id) => /^\d+$/.test(id)), 'Fixture cleanup returned a nonnumeric document id');
    const list = ids.join(',');
    console.log(`cleanup: removing probe document row(s) ${list} for ${docDescription}`);
    // document_storage is empty in the default file-backed mode, but carries the file's bytes
    // when database-backed storage is on (AddEditDocument2Action writes it), so the blob has to
    // go before the row that names it. The uploaded file itself lives in the server's document
    // store, which a browser-driven check cannot reach -- same limitation as
    // document-upload-playwright-checks.js.
    sql(`DELETE FROM document_storage WHERE documentNo IN (${list})`);
    sql(`DELETE FROM ctl_document WHERE document_no IN (${list})`);
    sql(`DELETE FROM document WHERE document_no IN (${list})`);
  } catch (e) {
    console.error(`FAIL: could not clean up probe documents for ${docDescription}: ${e.message}`);
    process.exitCode = 1;
  }
}

/** Minimal one-page PDF; the report list only needs a real %PDF file, not real content. */
function createPdfFixture() {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-edoc-nav-'));
  const pdfPath = path.join(tempDir, `${docDescription}.pdf`);
  const body = [
    '%PDF-1.4',
    '1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj',
    '2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj',
    '3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >> endobj',
    'trailer << /Root 1 0 R >>',
    '%%EOF',
    '',
  ].join('\n');
  fs.writeFileSync(pdfPath, body, 'latin1');
  return { tempDir, pdfPath };
}

async function screenshot(page, name) {
  const target = path.join(config.screenshotDir, `edoc-nav-${name}.png`);
  await page.screenshot({ path: target, fullPage: true }).catch(() => {});
  return target;
}

async function login(context) {
  const page = await context.newPage();
  watchForPageErrors(page, 'schedule');
  await gotoApp(page, '/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#username').fill(config.testUser);
  await page.locator('#password').fill(config.testPassword);
  await page.locator('#pin').fill(config.testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

/** Decodes the \xNN and \uNNNN escapes SafeEncode.forJavaScriptAttribute emits. */
function decodeJsEscapes(value) {
  return value
    .replace(/\\x([0-9a-fA-F]{2})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
    .replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
}

/**
 * Reads the eDoc destination out of the schedule menu's own link rather than
 * guessing the provider number, so the check follows whatever URL the deployment
 * actually serves.
 */
async function readEdocPath(schedulePage) {
  const link = schedulePage.locator("a[onclick*='/documentManager/ViewDocumentReport']").first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  const onclick = await link.getAttribute('onclick');
  const match = /['"](\S*?\/documentManager\/ViewDocumentReport\?[^'"]*)['"]/.exec(onclick || '');
  assert(match, `Could not read the eDoc menu link target from: ${onclick}`);
  // The href is built with SafeEncode.forJavaScriptAttribute, so '&' arrives as the JS escape
  // \x26 (and non-ASCII as \uXXXX). Decode before parsing or the whole query string collapses
  // into one parameter named "function".
  const url = new URL(decodeJsEscapes(match[1]), schedulePage.url());
  const expectedPath = new URL(appUrl('/documentManager/ViewDocumentReport')).pathname;
  assert(url.origin === config.baseUrl.origin,
    `The eDoc menu link points at a different origin (${url.origin})`);
  assert(url.pathname === expectedPath,
    `The eDoc menu link points outside the configured application context (${url.pathname})`);
  return `/documentManager/ViewDocumentReport${url.search}`;
}

async function assertNavHeader(page, present, label) {
  const count = await page.locator(NAV_SELECTOR).count();
  if (present) {
    assert(count > 0, `${label}: the navigation header tabs are gone (${page.url()})`);
  } else {
    assert(count === 0, `${label}: the navigation header rendered without scheduleNav=1 (${page.url()})`);
  }
}

function assertScheduleNavRetained(page, label) {
  const flag = new URL(page.url()).searchParams.get('scheduleNav');
  assert(flag === '1', `${label}: scheduleNav was dropped from the URL (${page.url()})`);
}

/** Picks an existing document type, or creates one through the page's own prompt(). */
async function chooseDocType(page) {
  const values = await page.locator('#docType option').evaluateAll(
    (options) => options.map((option) => option.value).filter((value) => value !== ''),
  );
  if (values.length > 0) {
    await page.selectOption('#docType', values[0]);
    return;
  }
  page.once('dialog', (dialog) => dialog.accept(docTypeName));
  await page.locator('#docTypeinput').click();
  await page.locator(`#docType option[value="${docTypeName}"]`).waitFor({ state: 'attached', timeout: 10000 });
  await page.selectOption('#docType', docTypeName);
}

async function addDocument(page, pdfPath) {
  await page.locator('button[data-bs-target="#addDocDiv"]').click();
  await page.locator('#addDocDiv #docDesc').waitFor({ state: 'visible', timeout: 15000 });

  // Scenario 1: the hidden input is the root cause. Assert it on the rendered page,
  // before submitting, so a regression names the cause instead of the symptom.
  assert(
    await page.locator('#addDocDiv input[name="scheduleNav"][value="1"]').count(),
    'The Add Document form did not carry scheduleNav=1',
  );

  await chooseDocType(page);
  await page.locator('#docDesc').fill(docDescription);
  await page.locator('#docFile').setInputFiles(pdfPath);
  const observationDate = await page.locator('#observationDate').inputValue();
  assert(/^\d{4}-\d{2}-\d{2}$/.test(observationDate),
    `The Add Document form has an invalid observation date: ${observationDate}`);
  const validObservationDate = await page.evaluate(() => (
    typeof validDate === 'function' && validDate('observationDate')
  ));
  assert(validObservationDate, `validDate rejected observationDate=${observationDate}`);

  const expectedFunction = await page.locator('#addDocDiv input[name="function"]').inputValue();
  const expectedFunctionId = await page.locator('#addDocDiv input[name="functionId"]').inputValue();

  const [postResponse] = await Promise.all([
    page.waitForResponse(
      (response) => response.url().includes('/documentManager/addEditDocument')
        && response.request().method() === 'POST',
      { timeout: 60000 },
    ),
    page.locator('#addDocDiv input[name="Submit"]').click(),
  ]);
  assert(postResponse.status() < 400,
    `Add Document POST returned HTTP ${postResponse.status()} (${postResponse.url()})`);
  const expectedReportPath = new URL(appUrl('/documentManager/ViewDocumentReport')).pathname;
  await page.waitForURL((url) => (
    url.pathname === expectedReportPath
      && url.searchParams.get('docerrors') === 'docerrors'
      && url.searchParams.get('function') === expectedFunction
      && url.searchParams.get('functionid') === expectedFunctionId
      && url.searchParams.get('scheduleNav') === '1'
  ), { waitUntil: 'domcontentloaded', timeout: 60000 });
}

async function deleteDocument(page) {
  const row = page.locator('tr', { has: page.locator(`a[title="${docDescription}"]`) }).first();
  await row.waitFor({ state: 'visible', timeout: 15000 });
  page.once('dialog', (dialog) => dialog.accept());
  const [response] = await Promise.all([
    page.waitForResponse((result) => new URL(result.url()).pathname.endsWith('/documentManager/DocumentDelete')
      && result.request().method() === 'POST', { timeout: 30000 }),
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
    row.locator('a[onclick^="checkDelete("]').first().click(),
  ]);
  assert(response.status() < 400, `Delete Document POST returned HTTP ${response.status()}`);
}

async function selectDocumentStatus(page, status) {
  const [response] = await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
    page.locator('#viewstatus').selectOption(status),
  ]);
  assert(response && response.ok(), `Document ${status} filter failed`);
  assertScheduleNavRetained(page, `after selecting ${status} documents`);
  await assertNavHeader(page, true, `after selecting ${status} documents`);
  assert(await page.locator('#viewstatus').inputValue() === status, 'Document status filter was not retained');
}

async function restoreDocument(page) {
  await selectDocumentStatus(page, 'deleted');
  const row = page.locator('tr', { has: page.locator(`a[title="${docDescription}"]`) });
  assert(await row.count() === 1, 'Deleted list did not contain exactly the uploaded fixture');
  const [response] = await Promise.all([
    page.waitForResponse((result) => new URL(result.url()).pathname.endsWith('/documentManager/DocumentUndelete')
      && result.request().method() === 'POST', { timeout: 30000 }),
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
    row.locator('a[onclick^="submitDocAction(\'undelDocumentNo\'"]').click(),
  ]);
  assert(response.status() < 400, `Restore Document POST returned HTTP ${response.status()}`);
  assertScheduleNavRetained(page, 'after restoring a document');
  await assertNavHeader(page, true, 'after restoring a document');
  assert(await page.locator(`a[title="${docDescription}"]`).count() === 0,
    'Restored fixture remained in the deleted list');
  await selectDocumentStatus(page, 'active');
  assert(await page.locator(`a[title="${docDescription}"]`).count() === 1,
    'Restored fixture did not reappear in the active list');
}

async function addLink(page) {
  await page.locator('button[data-bs-target="#addLinkDiv"]').click();
  const form = page.locator('#addLinkDiv form');
  await form.locator('#docDesc2').waitFor({ state: 'visible', timeout: 15000 });
  assert(await form.locator('input[name="scheduleNav"][value="1"]').count() === 1,
    'The Add Link form did not carry scheduleNav=1');
  const types = await form.locator('#docType1 option').evaluateAll(
    (options) => options.map((option) => option.value).filter(Boolean),
  );
  assert(types.length > 0, 'Add Link has no document type after the upload created or selected one');
  await form.locator('#docType1').selectOption(types[0]);
  await form.locator('#docDesc2').fill(linkInputDescription);
  // The link is persisted but never followed; no external service is involved.
  await form.locator('#html').fill('http://example.invalid/carlos-nav-probe');
  const expectedFunction = await form.locator('input[name="function"]').inputValue();
  const expectedFunctionId = await form.locator('input[name="functionid"]').inputValue();
  const [response] = await Promise.all([
    page.waitForResponse((result) => new URL(result.url()).pathname.endsWith('/documentManager/addLink')
      && result.request().method() === 'POST', { timeout: 30000 }),
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
    form.locator('input[type="submit"]').click(),
  ]);
  assert(response.status() < 400, `Add Link POST returned HTTP ${response.status()}`);
  const destination = new URL(page.url());
  assert(destination.origin === config.baseUrl.origin
    && destination.pathname === new URL(appUrl('/documentManager/ViewDocumentReport')).pathname
    && destination.searchParams.get('function') === expectedFunction
    && destination.searchParams.get('functionid') === expectedFunctionId,
  'Add Link did not return to the originating document report');
  assertScheduleNavRetained(page, 'after adding a link');
  await assertNavHeader(page, true, 'after adding a link');
  assert(await page.locator(`a[title="${linkDescription}"]`).count() === 1,
    'Created link did not appear exactly once in the document list');
}

async function run() {
  const cancellation = createGracefulSignalCancellation();
  const { tempDir, pdfPath } = createPdfFixture();
  const launchOptions = { headless: true, handleSIGINT: false, handleSIGTERM: false };
  if (config.chromePath) {
    launchOptions.executablePath = config.chromePath;
  }
  let browser;
  // The packaged install serves a self-signed certificate, so loopback runs must accept it --
  // but a run pointed at a real host with ALLOW_NON_LOCAL_BASE_URL must still verify TLS.
  let context;
  let page;

  try {
    initMysqlDefaults();
    browser = await chromium.launch(launchOptions);
    cancellation.throwIfCancelled();
    context = await browser.newContext({ ignoreHTTPSErrors: isLoopbackTarget() });
    const schedulePage = await cancellation.run(() => login(context));
    const edocPath = await readEdocPath(schedulePage);

    // Negative control first: without the flag there must be no header, so a
    // green run cannot mean "the header is now unconditional".
    page = await context.newPage();
    watchForPageErrors(page, 'eDoc without scheduleNav');
    await gotoApp(page, edocPath, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await assertNavHeader(page, false, 'eDoc without scheduleNav');
    await page.close();

    page = await context.newPage();
    watchForPageErrors(page, 'eDoc');
    const shellPath = `${edocPath}${edocPath.includes('?') ? '&' : '?'}scheduleNav=1`;
    await gotoApp(page, shellPath, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await assertNavHeader(page, true, 'eDoc entry');
    console.log(`eDoc opened in the schedule shell: ${page.url()}`);

    await cancellation.run(() => addDocument(page, pdfPath));
    assertScheduleNavRetained(page, 'after adding a document');
    await assertNavHeader(page, true, 'after adding a document');
    await page.locator(`a[title="${docDescription}"]`).waitFor({ state: 'attached', timeout: 30000 });
    await screenshot(page, 'after-add');
    console.log(`document "${docDescription}" added; navigation header intact`);

    await cancellation.run(() => deleteDocument(page));
    assertScheduleNavRetained(page, 'after deleting a document');
    await assertNavHeader(page, true, 'after deleting a document');
    // The document must be GONE from the active list. Without this the leg passes when the click
    // did nothing at all -- which is exactly what a broken script block looks like from outside.
    assert(
      (await page.locator(`a[title="${docDescription}"]`).count()) === 0,
      `The document "${docDescription}" is still listed after the delete; the delete did not happen`,
    );
    await screenshot(page, 'after-delete');
    console.log(`document "${docDescription}" deleted; navigation header intact`);

    await cancellation.run(() => restoreDocument(page));
    await screenshot(page, 'after-restore');
    console.log('document restored to the active list; navigation header intact');
    await cancellation.run(() => addLink(page));
    await screenshot(page, 'after-add-link');
    console.log('link document added; navigation header intact');

    assert(!pageErrors.length, `Uncaught script errors on the page: ${pageErrors.join(' | ')}`);

    cancellation.throwIfCancelled();
    console.log('PASS: eDoc keeps its navigation header tabs across upload, delete, restore, and Add Link');
  } catch (error) {
    if (cancellation.isCancellation(error)) {
      process.exitCode = cancellation.exitCode;
      return;
    }
    if (page) {
      console.error(`failure screenshot: ${await screenshot(page, 'failure')}`);
      console.error(`failure url: ${page.url()}`);
    }
    console.error(`FAIL: ${error.message}`);
    console.error(`probe document description: ${docDescription}`);
    process.exitCode = 1;
  } finally {
    try {
      if (context) await context.close().catch(() => {});
      if (browser) await browser.close().catch(() => {});
      cleanupProbeDocuments();
    } finally {
      cleanupMysqlDefaults();
      fs.rmSync(tempDir, { recursive: true, force: true });
      if (cancellation.exitCode) process.exitCode = cancellation.exitCode;
      cancellation.dispose();
    }
  }
}

run().catch(() => {
  console.error('FAIL: eDoc validation or fixture cleanup failed');
  process.exitCode = process.exitCode || 1;
});
