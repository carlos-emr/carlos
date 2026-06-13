#!/usr/bin/env node
/*
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
 * Browser smoke checks for PR #2722 logout-redirect surfaces.
 *
 * This script verifies the affected CARLOS routes behave safely from the
 * browser: authenticated users can still render/use the DMS surfaces, and fresh
 * unauthenticated browser contexts land on logout/login handling without
 * protected content, blank pages, or server error pages.
 *
 * Authenticated MeasurementGraph image-byte validation is intentionally out of
 * scope for this PR-specific check and is tracked separately in issue #2859.
 *
 * The exact internal invariant from PR #2722, "return immediately after
 * sendRedirect when userrole is missing", is not directly observable from a
 * pure black-box browser request because LoginFilter and top-level action
 * privilege checks may intercept earlier. Keep the focused action unit tests
 * as the authoritative proof for that no-further-work behavior.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/logout-redirect-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   GRAPH_DEMOGRAPHIC_NO=1
 *   GRAPH_MEASUREMENT_TYPE=ALT to override the default seeded measurement graph
 *   GRAPH_PATH=/encounter/GraphMeasurements?method=ChartMeds&demographic_no=1&drug=...
 *   CARLOS_LOG_FILE=/path/to/catalina.out
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const fs = require('fs');
const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const graphDemographicNo = process.env.GRAPH_DEMOGRAPHIC_NO || '1';
const graphMeasurementType = process.env.GRAPH_MEASUREMENT_TYPE || 'ALT';
const graphPath = process.env.GRAPH_PATH
  || `/encounter/GraphMeasurements?demographic_no=${encodeURIComponent(graphDemographicNo)}&type=${encodeURIComponent(graphMeasurementType)}`;
const carlosLogFile = process.env.CARLOS_LOG_FILE || '';
const timeout = parseTimeout(process.env.PLAYWRIGHT_TIMEOUT, 30000);

const findings = [];
const visited = [];

const pageRoutes = [
  { label: 'dms-index', path: '/documentManager/inboxManage?method=prepareForIndexPage' },
  { label: 'dms-content', path: '/documentManager/inboxManage?method=prepareForContentPage&page=1&pageSize=20&view=all&status=N' },
  { label: 'dms-queues', path: '/documentManager/inboxManage?method=getDocumentsInQueues' },
];

const affectedRoutes = [
  ...pageRoutes,
  { label: 'measurement-graph', path: graphPath, graph: true },
];

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

function parseTimeout(rawTimeout, fallback) {
  if (rawTimeout === undefined || rawTimeout === '') {
    return fallback;
  }
  const parsed = Number(rawTimeout);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error(`PLAYWRIGHT_TIMEOUT must be a positive number, got ${rawTimeout}`);
  }
  return parsed;
}

function safeUrl(rawUrl) {
  try {
    const parsed = new URL(rawUrl);
    if (!['http:', 'https:'].includes(parsed.protocol)) {
      return rawUrl;
    }
    const queryKeys = [...parsed.searchParams.keys()];
    parsed.search = '';
    if (!queryKeys.length) {
      return parsed.toString();
    }
    return `${parsed.toString()}?${queryKeys.map((key) => `${encodeURIComponent(key)}=<redacted>`).join('&')}`;
  } catch {
    return '<unparseable-url>';
  }
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

function safeGoto(page, label, appPath, options = {}) {
  const targetUrl = appUrl(appPath);
  // nosemgrep -- appUrl rejects non-root-relative paths and validateBaseUrl rejects non-local hosts unless explicitly allowed.
  return page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout, ...options }).then((response) => {
    visited.push({ label, status: response ? response.status() : null, url: safeUrl(page.url()), targetUrl: safeUrl(targetUrl) });
    return response;
  });
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
  return /(ReferenceError|TypeError|SyntaxError|DataTable is not a function|Cannot read|Cannot set|is not defined)/i.test(text);
}

function wirePage(page, label) {
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      findings.push({ label, type: 'http', status, url: safeUrl(responseUrl) });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      findings.push({
        label,
        type: `console:${message.type()}`,
        ...summarizeText(message.text()),
        location: safeLocation(message.location()),
      });
    }
  });
  page.on('pageerror', (error) => {
    findings.push({ label, type: 'pageerror', errorName: error.name, ...summarizeText(error.stack || error.message) });
  });
  page.on('dialog', async (dialog) => {
    findings.push({ label, type: 'dialog', ...summarizeText(dialog.message()) });
    await dialog.accept();
  });
}

async function bodyText(page) {
  return page.locator('body').innerText({ timeout: 10000 }).catch(() => '');
}

function isErrorPageText(text) {
  return /CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report|Stacktrace|There is no Action mapped|InvocationTargetException/i.test(text);
}

function isLoginOrLogoutPage(text, url) {
  return /login|logout|username|password|session/i.test(text)
    || /\/(logoutPage|index|loginfailed|login)(?:$|[?#])/.test(url);
}

function summarizeText(text = '') {
  const raw = String(text);
  return {
    textLength: raw.length,
    normalizedTextLength: raw.replace(/\s+/g, ' ').trim().length,
  };
}

function safeLocation(location = {}) {
  return {
    url: location.url ? safeUrl(location.url) : '',
    lineNumber: location.lineNumber,
    columnNumber: location.columnNumber,
  };
}

async function assertNoErrorPage(page, label, options = {}) {
  const text = await bodyText(page);
  if (isErrorPageText(text)) {
    findings.push({ label, type: 'error-page', url: safeUrl(page.url()), ...summarizeText(text) });
  }
  if (!options.allowBlank && !text.trim()) {
    findings.push({ label, type: 'blank-page', url: safeUrl(page.url()) });
  }
  return text;
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await safeGoto(page, 'login', '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  if (await page.locator('#pin').count()) {
    await page.locator('#pin').fill(testPin);
  }
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout }).catch(() => {});
  visited.push({ label: 'post-login', url: safeUrl(page.url()) });
  const text = await assertNoErrorPage(page, 'post-login');
  if (/login=failed|Login failed|form name="loginForm"/i.test(page.url() + text)) {
    findings.push({ label: 'post-login', type: 'login-failed', url: safeUrl(page.url()), ...summarizeText(text) });
  }
  return page;
}

async function checkAuthenticatedPageRoute(context, route) {
  const page = await context.newPage();
  wirePage(page, `authenticated:${route.label}`);
  const response = await safeGoto(page, `authenticated:${route.label}`, route.path);
  await page.waitForLoadState('networkidle', { timeout }).catch(() => {});
  if (!response || !response.ok()) {
    findings.push({
      label: `authenticated:${route.label}`,
      type: 'bad-navigation-status',
      status: response ? response.status() : null,
      url: safeUrl(page.url()),
    });
  }
  const text = await assertNoErrorPage(page, `authenticated:${route.label}`);
  if (isLoginOrLogoutPage(text, page.url())) {
    findings.push({ label: `authenticated:${route.label}`, type: 'unexpected-auth-redirect', url: safeUrl(page.url()), ...summarizeText(text) });
  }
  await page.close();
}

async function checkUnauthenticatedRoute(browser, route) {
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  const page = await context.newPage();
  const label = `unauthenticated:${route.label}`;
  wirePage(page, label);
  const response = await safeGoto(page, label, route.path);
  await page.waitForLoadState('networkidle', { timeout }).catch(() => {});
  const contentType = response ? response.headers()['content-type'] || '' : '';

  if (route.graph && /^image\//i.test(contentType)) {
    findings.push({ label, type: 'protected-image-rendered', contentType, url: safeUrl(page.url()) });
    await context.close();
    return;
  }

  const text = await assertNoErrorPage(page, label, { allowBlank: isLoginOrLogoutPage('', page.url()) });
  if (!isLoginOrLogoutPage(text, page.url())) {
    findings.push({ label, type: 'missing-auth-redirect', url: safeUrl(page.url()), ...summarizeText(text) });
  }
  await context.close();
}

function captureLogSnapshot() {
  if (!carlosLogFile) {
    return null;
  }
  try {
    const stats = fs.statSync(carlosLogFile);
    return { file: carlosLogFile, size: stats.size };
  } catch (error) {
    findings.push({ label: 'log-scan', type: 'log-file-unavailable', file: carlosLogFile, error: error.message });
    return null;
  }
}

function readLogDelta(snapshot) {
  if (!snapshot) {
    return '';
  }
  try {
    const stats = fs.statSync(snapshot.file);
    if (stats.size <= snapshot.size) {
      return '';
    }
    const maxBytes = 1024 * 1024;
    const start = Math.max(snapshot.size, stats.size - maxBytes);
    const length = stats.size - start;
    const buffer = Buffer.alloc(length);
    const fd = fs.openSync(snapshot.file, 'r');
    try {
      fs.readSync(fd, buffer, 0, length, start);
    } finally {
      fs.closeSync(fd);
    }
    return buffer.toString('utf8');
  } catch (error) {
    findings.push({ label: 'log-scan', type: 'log-read-failed', file: snapshot.file, error: error.message });
    return '';
  }
}

function checkLogDelta(snapshot) {
  const delta = readLogDelta(snapshot);
  if (!delta) {
    return;
  }

  const responseCommitFailure = /(Cannot call sendRedirect|Cannot redirect after response has been committed|response[^.\n]{0,80}committed|IllegalStateException:[^\n]*committed)/i;
  const affectedActionStacktrace = /(?:Exception|Error)[\s\S]{0,1200}(DmsInboxManage2Action|MeasurementGraphAction22Action)|(DmsInboxManage2Action|MeasurementGraphAction22Action)[\s\S]{0,1200}(?:Exception|Error)/i;
  const signals = [];
  if (responseCommitFailure.test(delta)) {
    signals.push('response-commit-failure');
  }
  if (affectedActionStacktrace.test(delta)) {
    signals.push('affected-action-stacktrace');
  }
  if (signals.length) {
    findings.push({
      label: 'log-scan',
      type: 'affected-route-log-failure',
      file: snapshot.file,
      signals,
      ...summarizeText(delta),
    });
  }
}

(async () => {
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const logSnapshot = captureLogSnapshot();
  const browser = await chromium.launch(launchOptions);
  try {
    for (const route of affectedRoutes) {
      await checkUnauthenticatedRoute(browser, route);
    }

    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
    const landingPage = await login(context);
    if (!findings.some((finding) => finding.label === 'post-login' && finding.type === 'login-failed')) {
      for (const route of pageRoutes) {
        await checkAuthenticatedPageRoute(context, route);
      }
    }
    await landingPage.close();
    await context.close();
  } finally {
    await browser.close();
  }

  checkLogDelta(logSnapshot);

  console.log(JSON.stringify({ visited, findings }, null, 2));
  const blockingFindings = findings.filter((finding) => finding.type !== 'dialog');
  if (blockingFindings.length) {
    throw new Error(`logout redirect Playwright check found ${blockingFindings.length} issue(s)`);
  }

  console.log('PASS logout redirect surfaces rendered without auth, HTTP, browser, or response-commit failures');
})().catch((error) => {
  console.error('FAIL logout redirect Playwright check');
  console.error(error.stack || error.message);
  if (findings.length) {
    console.error(`Findings: ${JSON.stringify(findings, null, 2)}`);
  }
  process.exit(1);
});
