#!/usr/bin/env node
/*
 * Browser regression check for opening Pending Docs when queue 1's Refile
 * directory is absent. This check is intended only for a local/private CARLOS
 * deployment; it temporarily renames the directory and always restores it.
 *
 * Example local usage:
 *   TEST_PASSWORD=carlos2026 TEST_PIN=2026 MYSQL_PASSWORD=password \
 *     node scripts/pending-docs-missing-refile-playwright-checks.js
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = requiredEnv('TEST_PASSWORD');
const testPin = requiredEnv('TEST_PIN');
const mysqlHost = process.env.MYSQL_HOST || 'db';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = requiredEnv('MYSQL_PASSWORD');
const mysqlDatabase = process.env.MYSQL_DATABASE || 'oscar';
const incomingDocumentDir = process.env.INCOMING_DOCUMENT_DIR || '/var/lib/OscarDocument/oscar/incomingdocs';
const refileDirectory = path.join(incomingDocumentDir, '1', 'Refile');

const findings = [];
let browser;
let context;
let originalDirectoryState = 'unknown';
let backupDirectory;
let documentNo;
let targetUrl;

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is required for the Pending Docs Playwright check`);
  }
  return value;
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

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

function appUrl(appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const relative = new URL(appPath, 'http://localhost');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  return url.toString();
}

function selectActiveDocumentNo() {
  const output = execFileSync('mysql', [
    '-h', mysqlHost,
    '-u', mysqlUser,
    mysqlDatabase,
    '-N',
    '-B',
    '-e',
    'SELECT document_no\nFROM document\nWHERE status = \'A\'\nORDER BY document_no DESC\nLIMIT 1',
  ], {
    encoding: 'utf8',
    env: { ...process.env, MYSQL_PWD: mysqlPassword },
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
  assert(output, 'No active document row exists for the Pending Docs Playwright check');
  assert(/^\d+$/.test(output), `Active document query returned an invalid document number: ${output}`);
  return output;
}

function hideRefileDirectory() {
  if (!fs.existsSync(refileDirectory)) {
    originalDirectoryState = 'absent';
    return;
  }

  originalDirectoryState = 'present';
  backupDirectory = `${refileDirectory}.pending-docs-playwright-${process.pid}-${Date.now()}`;
  if (fs.existsSync(backupDirectory)) {
    throw new Error(`Refusing to overwrite existing Refile backup directory ${backupDirectory}`);
  }
  fs.renameSync(refileDirectory, backupDirectory);
}

function restoreRefileDirectory() {
  if (originalDirectoryState !== 'present') {
    return;
  }
  if (!backupDirectory || !fs.existsSync(backupDirectory)) {
    throw new Error(`Refile backup directory is missing; cannot restore ${refileDirectory}`);
  }
  if (fs.existsSync(refileDirectory)) {
    throw new Error(`Refile directory unexpectedly exists; refusing to overwrite it while restoring ${backupDirectory}`);
  }
  fs.renameSync(backupDirectory, refileDirectory);
}

function isIgnorableMissingAsset(status, responseUrl) {
  return status === 404 && (/\/favicon\.ico(?:$|\?)/.test(responseUrl) || /\/imageRenderingServlet(?:\?|$)/.test(responseUrl));
}

function isIgnorableConsoleMessage(message) {
  const text = message.text();
  const location = message.location();
  return /Content Security Policy.*report-only/i.test(text)
    || /favicon\.ico|imageRenderingServlet/i.test(`${text} ${location.url || ''}`);
}

function wirePage(page, label) {
  page.on('response', (response) => {
    const status = response.status();
    const responseUrl = response.url();
    if (status >= 400 && !isIgnorableMissingAsset(status, responseUrl)) {
      findings.push({ label, type: 'http', status, method: response.request().method(), url: responseUrl });
    }
  });
  page.on('pageerror', (error) => {
    findings.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
  page.on('console', (message) => {
    if (message.type() === 'error' && !isIgnorableConsoleMessage(message)) {
      findings.push({ label, type: 'console:error', text: message.text(), location: message.location() });
    }
  });
}

async function login(page) {
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const body = await page.locator('body').innerText();
  assert(!/login=failed|Login failed|name="loginForm"/i.test(`${page.url()}\n${body}`), 'Login did not establish an authenticated session');
}

function printDiagnostics(error) {
  console.log(JSON.stringify({
    result: error ? 'FAIL' : 'PASS',
    documentNo: documentNo || null,
    targetUrl: targetUrl || null,
    refileDirectory,
    originalDirectoryState,
    restoredDirectoryState: fs.existsSync(refileDirectory) ? 'present' : 'absent',
    findings,
    error: error ? (error.stack || error.message) : null,
  }, null, 2));
}

(async () => {
  let failure;
  try {
    documentNo = selectActiveDocumentNo();
    browser = await chromium.launch({ headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
    context = await browser.newContext({ ignoreHTTPSErrors: true });
    const page = await context.newPage();
    wirePage(page, 'pending-docs');
    await login(page);

    hideRefileDirectory();
    targetUrl = appUrl('/documentManager/ViewShowDocument?'
      + new URLSearchParams({ segmentID: String(documentNo), inWindow: 'true', inQueue: 'true' }));
    const response = await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });

    assert(response && response.ok(), `Pending Docs view returned ${response ? response.status() : 'no response'}`);
    const body = await page.locator('body').innerText();
    assert(!/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(body),
      'Pending Docs view rendered an application error page');
    assert(await page.locator(`#refileDoc_${documentNo}`).count() === 1,
      'Pending Docs view did not render the document refile control');
    assert(findings.length === 0, `Pending Docs browser check recorded ${findings.length} finding(s)`);
  } catch (error) {
    failure = error;
  } finally {
    try {
      if (context) {
        await context.close();
      }
      if (browser) {
        await browser.close();
      }
    } catch (closeError) {
      failure = failure || closeError;
    }
    try {
      restoreRefileDirectory();
    } catch (restoreError) {
      failure = failure || restoreError;
    }
    printDiagnostics(failure);
  }
  if (failure) {
    throw failure;
  }
})().catch((error) => {
  console.error('FAIL pending-docs missing-Refile Playwright check');
  console.error(error.stack || error.message);
  process.exit(1);
});
