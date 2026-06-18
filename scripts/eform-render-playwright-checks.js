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
 * App-backed browser regression checks for the eForm PDF/render pipeline.
 *
 * This script logs into a running CARLOS app, uploads a tiny background image,
 * imports a temporary eForm whose HTML intentionally contains malformed comment
 * syntax plus a ${oscar_image_path} background image reference, opens the eForm
 * from the admin library, and verifies that:
 *   1. the eForm still renders in the popup without an error page
 *   2. the background image resolves through displayImage
 *   3. /previewDocs?method=renderEFormPDF returns a real PDF for the imported form
 *
 * Defaults are for the local devcontainer:
 *   node scripts/eform-render-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   EFORM_SCREENSHOT_DIR=/tmp
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
const screenshotDir = process.env.EFORM_SCREENSHOT_DIR || '/tmp';

const bgImageName = 'playwright_pdf_render_bg.png';
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

function createFixtureFiles() {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-eform-render-'));
  const imagePath = path.join(tempDir, bgImageName);
  fs.writeFileSync(imagePath, Buffer.from(transparentPngBase64, 'base64'));

  const htmlPath = path.join(tempDir, 'playwright-render-pipeline.html');
  const html = `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Playwright Render Pipeline</title>
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
window.onbeforeunload = function confirmExit() {
  if (needToConfirm) {
    return "Unsaved changes";
  }
};
function formPrint() {
  window.print();
}
</script>
</head>
<body onload="">
<form method="post" action="" name="FormName" id="FormName">
<!-- malformed -- comment-->
<div id="page1" style="page-break-after:always;position:relative;">
<img id="BGImage1" src="\${oscar_image_path}${bgImageName}" style="position:relative;left:0;top:0;width:750px;height:140px;">
<input name="patient_nameL" id="patient_nameL" type="text" value="TemplateSeed" class="noborder" style="position:absolute;left:40px;top:32px;width:220px;height:22px;" oscarDB="patient_nameL">
<input name="subject" id="subject" type="text" class="noborder" style="position:absolute;left:40px;top:72px;width:220px;height:22px;">
</div>
<div class="DoNotPrint" id="BottomButtons" style="position:absolute;top:180px;left:0;">
  <input value="Submit" name="SubmitButton" id="SubmitButton" type="submit" onclick="releaseDirtyFlag();">
  <input value="Print" name="PrintButton" id="PrintButton" type="button" onclick="formPrint();">
</div>
</form>
</body>
</html>`;
  fs.writeFileSync(htmlPath, html, 'utf8'); // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- html is a fixed local test fixture, not external input

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
  return /(ReferenceError|TypeError|SyntaxError|\$ is not defined|jQuery is not defined|Cannot read|Cannot set|is not defined)/i.test(text);
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    consoleIssues.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.dismiss().catch(() => {});
  });
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    if (/\/eform\/displayImage(?:\.do)?\?imagefile=/.test(responseUrl)) {
      displayImageResponses.push({ label, status, url: responseUrl, contentType: response.headers()['content-type'] || '' });
    }
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      badResponses.push({ label, status, method: response.request().method(), url: responseUrl, contentType: response.headers()['content-type'] || '' });
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
    await frame.locator('#patientIndependent').check();
    await frame.locator('#formHtml').setInputFiles(htmlPath);
    await frame.locator('input.upload[type="submit"]').click();

    await page.waitForURL(/administration\?show=Forms/, { timeout: 10000 }).catch(() => {});
    await gotoApp(page, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const row = page.locator('#eformTbl tbody tr', { hasText: formName }).first();
    await row.waitFor({ state: 'visible', timeout: 15000 });

    const editHref = await row.locator('a[href*="efmformmanageredit?fid="]').first().getAttribute('href');
    assert(editHref, `Could not find edit link for imported eForm ${formName}`);
    const parsed = new URL(editHref, baseUrl.href);
    const fid = parsed.searchParams.get('fid');
    assert(fid, `Could not extract fid from edit link ${editHref}`);
    return { page, row, fid };
  } catch (error) {
    await page.close().catch(() => {});
    throw error;
  }
}

async function openManagerPreview(context, row) {
  const popupPromise = context.waitForEvent('page');
  await row.locator('td a').first().click();
  const popup = await popupPromise;
  wirePage(popup, 'manager-preview');
  await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return popup;
}

async function openImportedEform(context, fid, demographicNo) {
  const popup = await context.newPage();
  wirePage(popup, 'eform-popup');
  await gotoApp(popup, `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(demographicNo)}`);
  await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
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

 async function cleanupImportedEform(page, fid) {
  if (!fid) {
    return;
  }
  await page.evaluate((submittedFid) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- submittedFid is passed as a Playwright argument, not interpolated into code
    const form = document.createElement('form');
    form.method = 'post';
    form.action = `${window.location.origin}${window.location.pathname.replace(/\/efmformmanager.*$/, '')}/delEForm`;
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = 'fid';
    input.value = submittedFid;
    form.appendChild(input);
    document.body.appendChild(form);
    form.submit();
  }, fid);
  await page.waitForLoadState('domcontentloaded', { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
}

(async () => {
  const fixture = createFixtureFiles();
  const timestamp = Date.now();
  const importedFormName = `Playwright Render Pipeline ${timestamp}`;
  const importedFormSubject = `Render pipeline ${timestamp}`;
  const renderedPdfPath = path.join(screenshotDir, `eform-render-pipeline-${timestamp}.pdf`);
  const screenshotPath = path.join(screenshotDir, `eform-render-pipeline-${timestamp}.png`);
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
    const context = await browser.newContext({ acceptDownloads: true, ignoreHTTPSErrors: true, viewport: { width: 1100, height: 1600 } });
    const landingPage = await login(context);
    await landingPage.close();

    await ensureImageUploaded(context, fixture.imagePath, bgImageName);
    const uploadResult = await uploadEform(context, importedFormName, importedFormSubject, fixture.htmlPath);
    managerPage = uploadResult.page;
    importedFid = uploadResult.fid;

    const managerPreview = await openManagerPreview(context, uploadResult.row);
    await assertNotErrorPage(managerPreview, 'manager preview popup');
    await assertImageLoaded(managerPreview, '#BGImage1', 'manager preview background image');
    const managerPreviewState = await managerPreview.evaluate(() => ({
      patientLast: document.getElementById('patient_nameL') ? document.getElementById('patient_nameL').value : '',
      bgSrc: document.getElementById('BGImage1') ? document.getElementById('BGImage1').src : '',
    }));
    assert(managerPreviewState.patientLast === 'TemplateSeed', `Manager preview did not render template-stored field data: ${managerPreviewState.patientLast}`);
    await managerPreview.close();

    const popup = await openImportedEform(context, importedFid, '1');
    await assertNotErrorPage(popup, 'render-pipeline eForm popup');

    await popup.fill('#patient_nameL', 'Playwright');
    const popupState = await popup.evaluate(() => ({
      patientLast: document.getElementById('patient_nameL').value,
      title: document.title,
      warningMessage: document.getElementById('warningMessage') ? document.getElementById('warningMessage').value : '',
      bgSrc: document.getElementById('BGImage1') ? document.getElementById('BGImage1').src : '',
    }));
    assert(popupState.patientLast === 'Playwright', 'Imported render-pipeline eForm field was not editable');
    assert(!popupState.warningMessage, `Unexpected warning message on initial popup render: ${popupState.warningMessage}`);
    await assertImageLoaded(popup, '#BGImage1', 'render-pipeline background image');
    assertDisplayImageFetchesSucceeded(bgImageName);

    const downloadPromise = popup.waitForEvent('download', { timeout: 60000 });
    await popup.locator('#remoteDownloadButton').click();
    const download = await downloadPromise;

    fs.mkdirSync(screenshotDir, { recursive: true });
    await download.saveAs(renderedPdfPath);
    const pdfBytes = fs.readFileSync(renderedPdfPath);
    assert(pdfBytes.subarray(0, 5).toString('utf8') === '%PDF-', 'Downloaded payload was not a PDF');
    assert(pdfBytes.length > 500, `Downloaded PDF payload was unexpectedly small (${pdfBytes.length} bytes)`);

    await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const postDownloadState = await popup.evaluate(() => ({
      warningMessage: document.getElementById('warningMessage') ? document.getElementById('warningMessage').value : '',
      errorMessage: document.getElementById('errorMessage') ? document.getElementById('errorMessage').value : '',
      isDownloadEForm: document.getElementById('isDownloadEForm') ? document.getElementById('isDownloadEForm').value : '',
    }));
    assert(!postDownloadState.warningMessage, `Unexpected warning after remote download: ${postDownloadState.warningMessage}`);
    assert(!postDownloadState.errorMessage, `Unexpected error after remote download: ${postDownloadState.errorMessage}`);

    await popup.screenshot({ path: screenshotPath, fullPage: true });

    const fatalBadResponses = badResponses.filter((response) => !(response.label === 'eform-popup' && response.url.includes('/oscar/eform/displayImage?imagefile=')));
    const fatalConsoleIssues = consoleIssues.filter((issue) => issue.type !== 'dialog' &&
      !(issue.label && issue.label.startsWith('image:') && /\$ is not defined/.test(issue.text)) &&
      !(issue.label === 'eform-upload' && /checkFormAndDisable is not defined/.test(issue.text)) &&
      !(issue.label === 'eform-popup' && /Failed to load resource/.test(issue.text) && /\/oscar\/eform\/displayImage\?imagefile=/.test(issue.location && issue.location.url ? issue.location.url : '')));
    assert(fatalBadResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(fatalBadResponses, null, 2)}`);
    assert(fatalConsoleIssues.length === 0, `unexpected browser console failures: ${JSON.stringify(fatalConsoleIssues, null, 2)}`);

    console.log(JSON.stringify({
      importedFormName,
      importedFormSubject,
      importedFid,
      screenshotPath,
      renderedPdfPath,
      pdfBytes: pdfBytes.length,
      managerPreviewBgSrc: managerPreviewState.bgSrc,
      popupBgSrc: popupState.bgSrc,
      isDownloadEFormAfterSave: postDownloadState.isDownloadEForm,
      ignoredPopupDisplayImage404s: badResponses.filter((response) => response.label === 'eform-popup' && response.url.includes('/oscar/eform/displayImage?imagefile=')).length,
    }, null, 2));
    console.log('PASS app-backed eForm render pipeline Playwright check');

    await popup.close();
  } finally {
    if (managerPage && !managerPage.isClosed()) {
      await cleanupImportedEform(managerPage, importedFid).catch(() => {});
      await managerPage.close().catch(() => {});
    }
    fs.rmSync(fixture.tempDir, { recursive: true, force: true });
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL app-backed eForm render pipeline Playwright check');
  console.error(error.stack || error.message);
  if (badResponses.length) {
    console.error(`HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
  }
  if (consoleIssues.length) {
    console.error(`Console issues: ${JSON.stringify(consoleIssues, null, 2)}`);
  }
  process.exit(1);
});
