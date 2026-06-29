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

const fs = require('fs');
const path = require('path');

const SAFE_ARTIFACT_BASENAME_RE = /^[A-Za-z0-9._-]+$/;
const SAFE_ARTIFACT_EXTENSION_RE = /^\.[A-Za-z0-9]+$/;

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function resolveArtifactDir(rawDir) {
  assert(typeof rawDir === 'string' && rawDir.trim() !== '', 'Artifact directory must be a non-empty string');
  const resolvedDir = path.resolve(rawDir); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- artifact dirs are restricted to /tmp or the current workspace before use
  const allowedRoots = [path.resolve('/tmp'), path.resolve(process.cwd())];
  assert(allowedRoots.some((root) => resolvedDir === root || resolvedDir.startsWith(`${root}${path.sep}`)), `Artifact directory must be under ${allowedRoots.join(' or ')}, got ${resolvedDir}`);
  fs.mkdirSync(resolvedDir, { recursive: true });
  return resolvedDir;
}

function buildArtifactPath(artifactDir, baseName, extension = '.png') {
  assert(SAFE_ARTIFACT_BASENAME_RE.test(baseName), `Invalid artifact name: ${baseName}`);
  assert(SAFE_ARTIFACT_EXTENSION_RE.test(extension), `Invalid artifact extension: ${extension}`);
  return path.join(resolveArtifactDir(artifactDir), `${baseName}${extension}`); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- output path stays under a validated artifact directory and uses a sanitized basename
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

function appUrl(baseUrl, appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const relative = new URL(appPath, 'http://localhost');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  return url.toString();
}

function createRecorder() {
  return {
    badResponses: [],
    consoleIssues: [],
    pageErrors: [],
    requestLog: [],
    dialogs: [],
  };
}

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (
    /\/favicon\.ico$/.test(responseUrl)
    || /\/imageRenderingServlet\?/.test(responseUrl)
    || /\/eform\/displayImage\?imagefile=signature_pad\.min\.js(?:$|&)/.test(responseUrl)
    || /\/eform\/displayImage\?imagefile=BNK\.png(?:$|&)/.test(responseUrl)
  );
}

function isIgnorableConsoleMessage(message) {
  const text = message.text();
  return /Content Security Policy.*report-only/i.test(text)
    || /Master token \[CSRF-TOKEN\]/.test(text)
    || /Hidden token fields .* were updated with new token value/.test(text)
    || /window\.print/i.test(text);
}

function isExpectedLegacyConsoleIssue(text, location = {}) {
  const source = `${location.url || ''} ${text}`;
  return /signature_pad\.min\.js|BNK\.png/.test(source);
}

function isSevereConsoleMessage(message) {
  if (isIgnorableConsoleMessage(message)) {
    return false;
  }
  const text = message.text();
  if (isExpectedLegacyConsoleIssue(text, message.location())) {
    return false;
  }
  if (message.type() === 'error') {
    return true;
  }
  return /(ReferenceError|TypeError|SyntaxError|\$ is not defined|jQuery is not defined|Cannot read|Cannot set|is not defined)/i.test(text);
}

function wirePage(page, label, recorder) {
  page.on('dialog', async (dialog) => {
    recorder.dialogs.push({ label, type: dialog.type(), text: dialog.message() });
    await dialog.dismiss().catch(() => {});
  });
  page.on('response', async (response) => {
    const responseUrl = response.url();
    const status = response.status();
    const contentType = response.headers()['content-type'] || '';
    recorder.requestLog.push({
      label,
      status,
      method: response.request().method(),
      url: responseUrl,
      contentType,
    });
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      recorder.badResponses.push({
        label,
        status,
        method: response.request().method(),
        url: responseUrl,
        contentType,
      });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      recorder.consoleIssues.push({
        label,
        type: message.type(),
        text: message.text(),
        location: message.location(),
      });
    }
  });
  page.on('pageerror', (error) => {
    recorder.pageErrors.push({ label, text: error.stack || error.message });
  });
}

async function gotoApp(page, baseUrl, appPath, waitUntil = 'domcontentloaded') {
  return page.goto(appUrl(baseUrl, appPath), { waitUntil, timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restrict hosts to local/private by default
}

async function login(context, config, recorder) {
  const page = await context.newPage();
  wirePage(page, 'login', recorder);
  await gotoApp(page, config.baseUrl, '/');
  await page.locator('#username').fill(config.testUser);
  await page.locator('#password').fill(config.testPassword);
  if (await page.locator('#pin').count()) {
    await page.locator('#pin').fill(config.testPin);
  }
  await Promise.all([
    page.waitForURL(/providercontrol|appointment/i, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function openManager(context, config, recorder, label = 'manager') {
  const page = await context.newPage();
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, '/eform/efmformmanager');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function findLibraryEform(page, formName) {
  const row = page.locator('#eformTbl tbody tr', { hasText: formName }).first();
  await row.waitFor({ state: 'visible', timeout: 15000 });
  const previewOnclick = await row.locator('a[onclick*="efmshowform_data?fid="]').first().getAttribute('onclick');
  const editHref = await row.locator('a[href*="efmformmanageredit?fid="]').first().getAttribute('href');
  const previewMatch = previewOnclick && previewOnclick.match(/fid=([^&'"]+)/);
  const editMatch = editHref && editHref.match(/fid=([^&'"]+)/);
  assert(previewMatch?.[1] || editMatch?.[1], `Could not extract fid for ${formName}`);
  return {
    row,
    fid: decodeURIComponent((previewMatch && previewMatch[1]) || editMatch[1]),
  };
}

async function openAddEform(context, config, recorder, fid, demographicNo, label = 'add-eform') {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.close = () => {
      window.__playwrightCloseIntercepted = true;
    };
  });
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(demographicNo)}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function assertNotErrorPage(page, label) {
  const text = await page.locator('body').innerText({ timeout: 10000 }).catch(() => '');
  assert(!/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report|Whitelabel Error Page/i.test(text), `${label} rendered an error page`);
  assert(text.trim().length > 0, `${label} rendered a blank page`);
}

async function saveCurrentEform(page, subjectValue) {
  await assertNotErrorPage(page, 'save candidate');
  await page.locator('#remote_eform_subject').fill(subjectValue);
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('#remoteSubmitButton').click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.locator('#fdid').waitFor({ state: 'attached', timeout: 15000 });
  const fdid = await page.locator('#fdid').inputValue();
  assert(/^\d+$/.test(fdid), `Expected saved eForm fdid after submit, got ${fdid}`);
  return fdid;
}

async function openAttachPopup(page, context) {
  const popupPromise = context.waitForEvent('page');
  await page.locator('.editControlButton[title="Attach"]').click();
  const popup = await popupPromise;
  return popup;
}

async function waitForPopupReady(popup, recorder, label) {
  wirePage(popup, label, recorder);
  await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(popup, label);
}

async function invokeFetchAttached(page) {
  return page.evaluate(async () => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code executed without interpolating user-controlled input
    if (typeof fetchAttached !== 'function') {
      return { hasFunction: false, text: '', html: '' };
    }
    try {
      fetchAttached();
    } catch (error) {
      return { hasFunction: true, error: String(error), text: '', html: '' };
    }
    await new Promise((resolve) => window.setTimeout(resolve, 1500));
    const target = document.getElementById('tdAttachedDocs');
    return {
      hasFunction: true,
      text: target ? target.textContent.trim() : '',
      html: target ? target.innerHTML : '',
    };
  });
}

function getLatestRequest(recorder, predicate) {
  const matches = recorder.requestLog.filter(predicate);
  return matches.length ? matches[matches.length - 1] : null;
}

function getLaunchOptions(chromePath) {
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }
  return launchOptions;
}

async function screenshot(page, screenshotDir, name) {
  const outputPath = buildArtifactPath(screenshotDir, name);
  await page.screenshot({ path: outputPath, fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename
  return outputPath;
}

function buildFailureDetails(recorder) {
  return {
    badResponses: recorder.badResponses,
    consoleIssues: recorder.consoleIssues,
    pageErrors: recorder.pageErrors,
    dialogs: recorder.dialogs,
  };
}

module.exports = {
  appUrl,
  assert,
  assertNotErrorPage,
  buildArtifactPath,
  buildFailureDetails,
  createRecorder,
  findLibraryEform,
  getLaunchOptions,
  getLatestRequest,
  gotoApp,
  invokeFetchAttached,
  login,
  openAddEform,
  openAttachPopup,
  openManager,
  saveCurrentEform,
  screenshot,
  validateBaseUrl,
  waitForPopupReady,
  wirePage,
};
