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
 * Browser regression checks for the saved eForm render path.
 *
 * The script logs into a running local CARLOS app, uploads a tiny background
 * image and a temporary eForm fixture, saves a real eForm instance to produce
 * an fdid, then verifies that reopened saved forms still render both the
 * background image and persisted field data.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/eform-saved-render-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   SAVED_RENDER_DEMOGRAPHIC_NO=1
 *   SAVED_RENDER_SCREENSHOT_DIR=/tmp
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = process.env.SAVED_RENDER_DEMOGRAPHIC_NO || '1';
const screenshotDir = process.env.SAVED_RENDER_SCREENSHOT_DIR || '/tmp';

const bgImageName = 'playwright_saved_render_bg.png';
const transparentPngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl5n2QAAAAASUVORK5CYII=';

const badResponses = [];
const consoleIssues = [];
const displayImageResponses = [];

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
  const allowNonLocal = process.env.ALLOW_NON_LOCAL_BASE_URL === 'true';
  if (!localHosts.has(host) && !allowNonLocal) {
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

function createFixtureFiles() {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-saved-render-'));
  const imagePath = path.join(tempDir, bgImageName);
  fs.writeFileSync(imagePath, Buffer.from(transparentPngBase64, 'base64'));

  const htmlPath = path.join(tempDir, 'playwright-saved-render.html');
  const html = `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Playwright Saved Render</title>
<style type="text/css" media="print">
.DoNotPrint { display: none; }
.noborder {
  border: 0;
  background: transparent;
  overflow: hidden;
}
</style>
<script language="javascript">
var needToConfirm = false;
document.onkeyup = function setDirtyFlag() {
  needToConfirm = true;
};
function releaseDirtyFlag() {
  needToConfirm = false;
}
</script>
</head>
<body>
<form method="post" action="" name="FormName" id="FormName">
<div id="page1" style="page-break-after:always;position:relative;">
<img id="BGImage1" src="\${oscar_image_path}${bgImageName}" style="position:relative;left:0;top:0;width:750px;height:140px;">
<input name="patient_nameL" id="patient_nameL" type="text" value="TemplateSeed" class="noborder" style="position:absolute;left:40px;top:32px;width:220px;height:22px;" oscarDB="patient_nameL">
<input name="subject" id="subject" type="text" value="Saved Render Subject" class="noborder" style="position:absolute;left:40px;top:72px;width:220px;height:22px;">
</div>
<div class="DoNotPrint" id="BottomButtons" style="position:absolute;top:180px;left:0;">
  <input value="Submit" name="SubmitButton" id="SubmitButton" type="submit" onclick="releaseDirtyFlag();">
</div>
</form>
</body>
</html>`;
  fs.writeFileSync(htmlPath, html, 'utf8'); // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- fixed local test fixture written under a temp directory for Playwright only

  return { tempDir, imagePath, htmlPath };
}

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (/\/favicon\.ico$/.test(responseUrl) || /\/imageRenderingServlet\?/.test(responseUrl));
}

function isIgnorableConsoleMessage(message) {
  const text = message.text();
  return /Content Security Policy.*report-only/i.test(text)
    || /Master token \[CSRF-TOKEN\]/.test(text)
    || /Hidden token fields .* were updated with new token value/.test(text)
    || /window\.print/i.test(text);
}

function isSevereConsoleMessage(message) {
  if (isIgnorableConsoleMessage(message)) {
    return false;
  }
  const text = message.text();
  if (message.type() === 'error') {
    return true;
  }
  return /(ReferenceError|TypeError|SyntaxError|\$ is not defined|jQuery is not defined|Cannot read|Cannot set)/i.test(text);
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    consoleIssues.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.dismiss().catch(() => {});
  });
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    const contentType = response.headers()['content-type'] || '';
    if (/\/eform\/displayImage(?:\.do)?\?imagefile=/.test(responseUrl)) {
      displayImageResponses.push({ label, status, url: responseUrl, contentType });
    }
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      badResponses.push({ label, status, method: response.request().method(), url: responseUrl, contentType });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      consoleIssues.push({ label, type: message.type(), text: message.text(), location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    consoleIssues.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
}

async function gotoApp(page, appPath, waitUntil = 'domcontentloaded') {
  return page.goto(appUrl(appPath), { waitUntil, timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to local/private by default
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await gotoApp(page, '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  if (await page.locator('#pin').count()) {
    await page.locator('#pin').fill(testPin);
  }
  await Promise.all([
    page.waitForURL(/providercontrol|appointment/i, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function ensureImageUploaded(context, imagePath, imageName) {
  const page = await context.newPage();
  wirePage(page, `image:${imageName}`);
  try {
    await gotoApp(page, '/eform/efmimagemanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const imageLink = page.locator('#tblImage a.viewImage', { hasText: imageName }).first();
    if (await imageLink.count()) {
      return;
    }

    const frame = page.frameLocator('#uploadFrame');
    await frame.locator('#image').setInputFiles(imagePath);
    await frame.locator('input.upload[type="submit"]').click();
    await page.waitForURL(/administration\?show=ImageUpload/, { timeout: 10000 }).catch(() => {});
    await gotoApp(page, '/eform/efmimagemanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await page.locator('#tblImage a.viewImage', { hasText: imageName }).first().waitFor({ state: 'visible', timeout: 15000 });
  } finally {
    await page.close();
  }
}

async function uploadEform(context, formName, formSubject, htmlPath) {
  const page = await context.newPage();
  wirePage(page, 'eform-upload');
  try {
    await gotoApp(page, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const frame = page.frameLocator('#uploadFrame');
    await frame.locator('input[name="formName"]').fill(formName);
    await frame.locator('input[name="formSubject"]').fill(formSubject);
    await frame.locator('#formHtml').setInputFiles(htmlPath);
    await frame.locator('input.upload[type="submit"]').click();

    await page.waitForURL(/administration\?show=Forms/, { timeout: 10000 }).catch(() => {});
    await gotoApp(page, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const row = page.locator('#eformTbl tbody tr', { hasText: formName }).first();
    await row.waitFor({ state: 'visible', timeout: 15000 });

    const previewHref = await row.locator('a[onclick*="efmshowform_data?fid="]').first().getAttribute('onclick');
    const match = previewHref && previewHref.match(/fid=([^&'"]+)/);
    assert(match && match[1], `Could not extract fid from manager preview link for ${formName}`);
    return { page, row, fid: decodeURIComponent(match[1]) };
  } catch (error) {
    await page.close().catch(() => {});
    throw error;
  }
}

async function openAddEform(context, fid) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.close = () => {
      window.__playwrightCloseIntercepted = true;
    };
  });
  wirePage(page, 'add-eform');
  await gotoApp(page, `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(demographicNo)}`);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function openSavedEformDirect(context, fdid) {
  const page = await context.newPage();
  wirePage(page, 'saved-direct');
  await gotoApp(page, `/eform/efmshowform_data?fdid=${encodeURIComponent(fdid)}`);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function openSavedEformFromPatientList(context, formName) {
  const page = await context.newPage();
  wirePage(page, 'patient-list');
  await gotoApp(page, `/eform/efmpatientformlist?demographic_no=${encodeURIComponent(demographicNo)}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const row = page.locator('#efmTable tbody tr', { hasText: formName }).first();
  await row.waitFor({ state: 'visible', timeout: 15000 });
  const popupPromise = context.waitForEvent('page');
  await row.locator('a').first().click();
  const popup = await popupPromise;
  wirePage(popup, 'patient-list-popup');
  await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.close();
  return popup;
}

async function assertNotErrorPage(page, label) {
  const text = await page.locator('body').innerText({ timeout: 10000 }).catch(() => '');
  assert(!/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(text), `${label} rendered an error page`);
  assert(text.trim().length > 0, `${label} rendered a blank page`);
}

async function assertImageLoaded(page, selector, label) {
  const loaded = await page.locator(selector).evaluate((img) => ({
    complete: img.complete,
    width: img.naturalWidth,
    height: img.naturalHeight,
    src: img.currentSrc || img.src,
  }));
  assert(loaded.complete, `${label} did not finish loading`);
  assert(loaded.width > 0 && loaded.height > 0, `${label} failed to decode: ${loaded.src}`);
}

function assertDisplayImageFetchesSucceeded(imageName) {
  const matches = displayImageResponses.filter((response) => response.url.includes(`imagefile=${encodeURIComponent(imageName)}`) || response.url.includes(`imagefile=${imageName}`));
  assert(matches.length > 0, `No displayImage response captured for ${imageName}`);
  assert(matches.some((response) => response.status === 200), `displayImage never returned 200 for ${imageName}: ${JSON.stringify(matches, null, 2)}`);
}

async function screenshot(page, name) {
  fs.mkdirSync(screenshotDir, { recursive: true });
  await page.screenshot({ path: path.join(screenshotDir, `${name}.png`), fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- local Playwright helper writes screenshots under caller-selected local artifact dir
}

async function saveEformAndCaptureFdid(page, savedValue) {
  await assertNotErrorPage(page, 'add eForm page');
  await page.locator('#patient_nameL').fill(savedValue);
  await page.locator('#remote_eform_subject').fill('Playwright Saved Render Subject');

  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('#remoteSubmitButton').click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.locator('#fdid').waitFor({ state: 'attached', timeout: 15000 });

  const state = await page.evaluate(() => ({
    fdid: document.getElementById('fdid') ? document.getElementById('fdid').value : '',
    patientValue: document.getElementById('patient_nameL') ? document.getElementById('patient_nameL').value : '',
    autoclose: document.getElementById('isSuccess_Autoclose') ? document.getElementById('isSuccess_Autoclose').value : '',
    closeIntercepted: Boolean(window.__playwrightCloseIntercepted),
  }));

  assert(/^\d+$/.test(state.fdid), `Expected numeric fdid after save, got ${JSON.stringify(state)}`);
  assert(state.patientValue === savedValue, `Saved page did not preserve patient value after submit: ${JSON.stringify(state)}`);
  assert(state.autoclose === 'true', `Expected auto-close success flag after save, got ${JSON.stringify(state)}`);
  return state.fdid;
}

async function assertSavedFormState(page, expectedValue, expectedFdid, screenshotName) {
  await assertNotErrorPage(page, screenshotName);
  await page.locator('#patient_nameL').waitFor({ state: 'visible', timeout: 15000 });
  await assertImageLoaded(page, '#BGImage1', `${screenshotName} background image`);
  const state = await page.evaluate(() => ({
    fdid: document.getElementById('fdid') ? document.getElementById('fdid').value : '',
    patientValue: document.getElementById('patient_nameL') ? document.getElementById('patient_nameL').value : '',
    bgSrc: document.getElementById('BGImage1') ? document.getElementById('BGImage1').src : '',
  }));
  assert(state.fdid === expectedFdid, `${screenshotName} did not render the expected fdid: ${JSON.stringify(state)}`);
  assert(state.patientValue === expectedValue, `${screenshotName} did not render persisted patient value: ${JSON.stringify(state)}`);
  await screenshot(page, screenshotName);
}

(async () => {
  const fixture = createFixtureFiles();
  const timestamp = Date.now();
  const formName = `Playwright Saved Render ${timestamp}`;
  const formSubject = `Saved Render ${timestamp}`;
  const savedValue = `Playwright Saved ${timestamp}`;
  let importedFid = null;
  let managerPage = null;

  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const browser = await chromium.launch(launchOptions);
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1280, height: 1400 } });
    const landingPage = await login(context);
    await landingPage.close();

    await ensureImageUploaded(context, fixture.imagePath, bgImageName);
    const uploadResult = await uploadEform(context, formName, formSubject, fixture.htmlPath);
    managerPage = uploadResult.page;
    importedFid = uploadResult.fid;

    const addPage = await openAddEform(context, importedFid);
    const fdid = await saveEformAndCaptureFdid(addPage, savedValue);
    await screenshot(addPage, 'saved-render-after-save');
    await addPage.close();

    const directPage = await openSavedEformDirect(context, fdid);
    await assertSavedFormState(directPage, savedValue, fdid, 'saved-render-direct-route');
    await directPage.close();

    const patientListPopup = await openSavedEformFromPatientList(context, formName);
    assert(patientListPopup.url().includes(`fdid=${fdid}`), `Patient list popup did not open the expected saved-form route: ${patientListPopup.url()}`);
    await assertSavedFormState(patientListPopup, savedValue, fdid, 'saved-render-patient-list');
    await patientListPopup.close();

    assertDisplayImageFetchesSucceeded(bgImageName);
    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
    const renderConsoleIssues = consoleIssues.filter((issue) => ['add-eform', 'saved-direct', 'patient-list-popup'].includes(issue.label));
    assert(renderConsoleIssues.length === 0, `unexpected render-surface browser console failures: ${JSON.stringify(renderConsoleIssues, null, 2)}`);

    console.log('PASS saved eForm render path preserves background assets and persisted field values');
    console.log(`Imported fid=${importedFid}, saved fdid=${fdid}`);
    console.log(`Screenshots written under ${screenshotDir}`);
  } finally {
    if (managerPage && !managerPage.isClosed()) {
      await managerPage.close().catch(() => {});
    }
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL saved eForm render Playwright check');
  console.error(error.stack || error.message);
  if (badResponses.length) {
    console.error(`HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
  }
  if (consoleIssues.length) {
    console.error(`Console issues: ${JSON.stringify(consoleIssues, null, 2)}`);
  }
  if (displayImageResponses.length) {
    console.error(`displayImage responses: ${JSON.stringify(displayImageResponses, null, 2)}`);
  }
  process.exit(1);
});
