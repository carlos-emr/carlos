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
 * Three scenarios:
 *   1. Entering eDoc with scheduleNav=1 renders the header AND puts the flag in
 *      the add form (the missing hidden input is the root cause; assert it
 *      directly so a regression is diagnosed, not just detected).
 *   2. Adding a document keeps the header and keeps scheduleNav=1 on the URL.
 *   3. Deleting that document -- the same page's other mutation redirect -- keeps
 *      them too.
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
 *
 * FIXTURE SAFETY: uploads one PDF this script generates under a unique,
 * timestamped description and only ever asserts on that document. Note that the
 * UI delete in scenario 3 is the application's SOFT delete -- status='D', row and
 * file both still there -- so it is an assertion, NOT teardown. The row is removed
 * for real in the `finally` block, pass or fail, so an interrupted run leaves
 * nothing behind either. Cleanup never fails the run; if it could not connect it
 * warns, and strays are then removable with:
 *   DELETE FROM document WHERE docdesc LIKE 'carlos-nav-probe-%';
 */

const { chromium } = require('playwright');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.EDOC_NAV_SCREENSHOT_DIR || '/tmp',
  mysqlHost: process.env.MYSQL_HOST || '127.0.0.1',
  mysqlUser: process.env.MYSQL_USER || 'root',
  mysqlPassword: process.env.MYSQL_PASSWORD || 'password',
  mysqlDatabase: process.env.MYSQL_DATABASE || 'carlos',
};

const NAV_SELECTOR = '#firstTable #navlist';
const docDescription = `carlos-nav-probe-${Date.now()}`;
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
  const host = config.baseUrl.hostname.toLowerCase();
  return host === 'localhost' || host === '127.0.0.1' || host === '::1' || host === '[::1]';
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

let mysqlDefaults = null;
function initMysqlDefaults() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'edoc-nav-sql-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  fs.writeFileSync(file, `[client]\npassword=${config.mysqlPassword}\n`, { mode: 0o600 });
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
 * unconditionally, pass or fail, so an interrupted run leaves nothing behind either.
 */
function cleanupProbeDocuments() {
  try {
    const ids = sql(
      `SELECT document_no FROM document WHERE docdesc LIKE '${docDescription}%'`,
    ).split(/\s+/).filter(Boolean);
    if (!ids.length) return;
    const list = ids.join(',');
    console.log(`cleanup: removing probe document row(s) ${list} for ${docDescription}`);
    sql(`DELETE FROM ctl_document WHERE document_no IN (${list})`);
    sql(`DELETE FROM document WHERE document_no IN (${list})`);
  } catch (e) {
    // Housekeeping, never an assertion: a cleanup problem must not turn a passing run red.
    console.warn(`WARN: could not clean up probe documents for ${docDescription}: ${e.message}`);
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
  return `${url.pathname.replace(config.baseUrl.pathname, '')}${url.search}`;
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

  await Promise.all([
    page.waitForLoadState('networkidle', { timeout: 60000 }).catch(() => {}),
    page.locator('#addDocDiv input[name="Submit"]').click(),
  ]);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
}

async function deleteDocument(page) {
  const row = page.locator('tr', { has: page.locator(`a[title="${docDescription}"]`) }).first();
  await row.waitFor({ state: 'visible', timeout: 15000 });
  page.once('dialog', (dialog) => dialog.accept());
  await Promise.all([
    page.waitForLoadState('networkidle', { timeout: 60000 }).catch(() => {}),
    row.locator('a[onclick^="checkDelete("]').first().click(),
  ]);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
}

async function run() {
  const { tempDir, pdfPath } = createPdfFixture();
  initMysqlDefaults();
  const launchOptions = { headless: true };
  if (config.chromePath) {
    launchOptions.executablePath = config.chromePath;
  }
  const browser = await chromium.launch(launchOptions);
  // The packaged install serves a self-signed certificate, so loopback runs must accept it --
  // but a run pointed at a real host with ALLOW_NON_LOCAL_BASE_URL must still verify TLS.
  const context = await browser.newContext({ ignoreHTTPSErrors: isLoopbackTarget() });
  let page;

  try {
    const schedulePage = await login(context);
    const edocPath = await readEdocPath(schedulePage);

    // Negative control first: without the flag there must be no header, so a
    // green run cannot mean "the header is now unconditional".
    page = await context.newPage();
    await gotoApp(page, edocPath, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await assertNavHeader(page, false, 'eDoc without scheduleNav');
    await page.close();

    page = await context.newPage();
    const shellPath = `${edocPath}${edocPath.includes('?') ? '&' : '?'}scheduleNav=1`;
    await gotoApp(page, shellPath, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await assertNavHeader(page, true, 'eDoc entry');
    console.log(`eDoc opened in the schedule shell: ${page.url()}`);

    await addDocument(page, pdfPath);
    assertScheduleNavRetained(page, 'after adding a document');
    await assertNavHeader(page, true, 'after adding a document');
    assert(
      await page.locator(`a[title="${docDescription}"]`).count(),
      `The added document "${docDescription}" is not listed after the redirect`,
    );
    await screenshot(page, 'after-add');
    console.log(`document "${docDescription}" added; navigation header intact`);

    await deleteDocument(page);
    assertScheduleNavRetained(page, 'after deleting a document');
    await assertNavHeader(page, true, 'after deleting a document');
    await screenshot(page, 'after-delete');
    console.log(`document "${docDescription}" deleted; navigation header intact`);

    console.log('PASS: eDoc keeps its navigation header tabs across add and delete');
  } catch (error) {
    if (page) {
      console.error(`failure screenshot: ${await screenshot(page, 'failure')}`);
      console.error(`failure url: ${page.url()}`);
    }
    console.error(`FAIL: ${error.message}`);
    console.error(`probe document description: ${docDescription}`);
    process.exitCode = 1;
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    cleanupProbeDocuments();
    cleanupMysqlDefaults();
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}

run();
