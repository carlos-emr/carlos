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
 * Browser-backed acceptance checks for the doctor-style eForm consultation workflow.
 *
 * The script logs into a running CARLOS app, imports a deterministic temporary
 * eForm fixture, completes and saves that eForm for a real demographic, reopens
 * the saved form directly and from the patient eForm list, then opens the
 * consultation workflow and proves the same saved eForm instance (fdid) is what
 * the consultation surface reuses.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/eform-consultation-acceptance-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   EFORM_CONSULT_DEMOGRAPHIC_NO=1
 *   EFORM_CONSULT_SCREENSHOT_DIR=/tmp
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium, request } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = process.env.EFORM_CONSULT_DEMOGRAPHIC_NO || '1';
const screenshotDir = process.env.EFORM_CONSULT_SCREENSHOT_DIR || '/tmp';

const bgImageName = 'playwright_consult_acceptance_bg.png';
const transparentPngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl5n2QAAAAASUVORK5CYII=';
const libraryEformName = 'Signature trick';
const libraryEformExpectedTemplateImages = [
  '2025_06_12_PCXX108060A_Regional_Community_Pain_Self_Management_Program_Referral__Form_NWM11.png',
  '2025_06_12_PCXX108060A_Regional_Community_Pain_Self_Management_Program_Referral__Form_NWM21.png',
];

const badResponses = [];
const consoleIssues = [];
const displayImageResponses = [];
const consultPreviewResponses = [];
const eformPreviewResponses = [];

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
  if (!localHosts.has(host) && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
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
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-consult-acceptance-'));
  const imagePath = path.join(tempDir, bgImageName);
  fs.writeFileSync(imagePath, Buffer.from(transparentPngBase64, 'base64'));

  const htmlPath = path.join(tempDir, 'playwright-consult-acceptance.html');
  const html = `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Playwright Consultation Acceptance</title>
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
<img id="BGImage1" src="\${oscar_image_path}${bgImageName}" style="position:relative;left:0;top:0;width:750px;height:220px;">
<input name="playwright_text_field" id="playwright_text_field" type="text" value="Template Text" class="noborder" style="position:absolute;left:40px;top:36px;width:220px;height:22px;">
<textarea name="playwright_textarea_field" id="playwright_textarea_field" class="noborder" style="position:absolute;left:40px;top:76px;width:240px;height:48px;">Template Textarea</textarea>
<select name="playwright_select_field" id="playwright_select_field" style="position:absolute;left:320px;top:36px;width:180px;height:24px;">
  <option value="">Choose</option>
  <option value="alpha">Alpha</option>
  <option value="bravo">Bravo</option>
  <option value="charlie">Charlie</option>
</select>
<div style="position:absolute;left:320px;top:76px;">
  <label><input type="radio" name="playwright_radio_field" id="playwright_radio_alpha" value="alpha"> Alpha</label>
  <label><input type="radio" name="playwright_radio_field" id="playwright_radio_bravo" value="bravo"> Bravo</label>
</div>
<div style="position:absolute;left:40px;top:142px;">
  <label><input type="checkbox" name="playwright_checkbox_checked" id="playwright_checkbox_checked" value="yes"> Checked box</label>
  <label><input type="checkbox" name="playwright_checkbox_unchecked" id="playwright_checkbox_unchecked" value="no"> Unchecked box</label>
</div>
<input name="subject" id="subject" type="text" value="Acceptance Subject" class="noborder" style="position:absolute;left:40px;top:180px;width:240px;height:22px;">
</div>
<div class="DoNotPrint" id="BottomButtons" style="position:absolute;top:240px;left:0;">
  <input value="Submit" name="SubmitButton" id="SubmitButton" type="submit" onclick="releaseDirtyFlag();">
</div>
</form>
</body>
</html>`;
  fs.writeFileSync(htmlPath, html, 'utf8'); // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- fixed local Playwright fixture written to a temp directory

  return { tempDir, imagePath, htmlPath };
}

function cleanupFixtureFiles(fixture) {
  if (!fixture.tempDir) {
    return;
  }
  fs.rmSync(fixture.tempDir, { recursive: true, force: true });
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

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    consoleIssues.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.dismiss().catch(() => {});
  });
  page.on('response', async (response) => {
    const responseUrl = response.url();
    const status = response.status();
    const contentType = response.headers()['content-type'] || '';
    if (/\/eform\/displayImage(?:\.do)?\?imagefile=/.test(responseUrl)) {
      displayImageResponses.push({ label, status, url: responseUrl, contentType });
    }
    if (responseUrl.includes('/previewDocs?method=renderEFormPDF')) {
      eformPreviewResponses.push({ label, status, url: responseUrl, contentType });
    }
    if (/\/encounter\/RequestConsultation$/.test(responseUrl) && response.request().method() === 'POST') {
      let json = null;
      try {
        json = await response.json();
      } catch (error) {
        json = { parseError: error.message };
      }
      consultPreviewResponses.push({ label, status, url: responseUrl, contentType, json });
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

async function findExistingLibraryEform(context, formName) {
  const page = await context.newPage();
  wirePage(page, 'library-manager');
  try {
    await gotoApp(page, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const row = page.locator('#eformTbl tbody tr', { hasText: formName }).first();
    await row.waitFor({ state: 'visible', timeout: 15000 });
    const editHref = await row.locator('a[href*="efmformmanageredit?fid="]').first().getAttribute('href');
    const match = editHref && editHref.match(/fid=([^&'"]+)/);
    assert(match && match[1], `Could not extract existing library fid for ${formName}`);
    return decodeURIComponent(match[1]);
  } finally {
    await page.close();
  }
}

async function readManagerTemplateHtml(context, fid) {
  const requestContext = await request.newContext({
    storageState: await context.storageState(),
    ignoreHTTPSErrors: true,
  });
  try {
    const response = await requestContext.get(appUrl(`/eform/efmformmanageredit?fid=${encodeURIComponent(fid)}`));
    assert(response.ok(), `Could not fetch manager template HTML for fid ${fid}: ${response.status()}`);
    return await response.text();
  } finally {
    await requestContext.dispose();
  }
}

async function probeExistingLibraryEform(context, fid) {
  const page = await openAddEform(context, fid);
  try {
    await assertNotErrorPage(page, 'existing library eForm add page');
    const bodyHtml = await page.locator('body').innerHTML();
    const bgImageCount = await page.locator('#BGImage1, #BGImage2').count();
    const fieldCount = await page.locator('#FormName input, #FormName textarea, #FormName select').count();
    assert(bgImageCount >= 2, `Existing library eForm fid=${fid} did not render both background images`);
    await assertImageLoaded(page, '#BGImage1', `existing library eForm fid=${fid} background image 1`);
    await assertImageLoaded(page, '#BGImage2', `existing library eForm fid=${fid} background image 2`);
    await screenshot(page, 'consult-acceptance-library-eform-runtime');
    return {
      bgImageCount,
      fieldCount,
      bodyHasPainBackground: /Regional_Community_Pain/i.test(bodyHtml),
      bodyHasDisplayImage: bodyHtml.includes('displayImage'),
      renderSurfaceUsable: bgImageCount >= 2 && bodyHtml.includes('displayImage'),
    };
  } finally {
    await page.close();
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

async function openSavedEformFromPatientList(context, fdid) {
  const page = await context.newPage();
  wirePage(page, 'patient-list');
  await gotoApp(page, `/eform/efmpatientformlist?demographic_no=${encodeURIComponent(demographicNo)}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const link = page.locator(`#efmTable a[onclick*="fdid=${fdid}"]`).first();
  await link.waitFor({ state: 'visible', timeout: 15000 });
  const popupPromise = context.waitForEvent('page');
  await link.click();
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

function collectDisplayImageFetches(imageName) {
  return displayImageResponses.filter((response) => response.url.includes(`imagefile=${encodeURIComponent(imageName)}`) || response.url.includes(`imagefile=${imageName}`));
}

async function screenshot(page, name) {
  fs.mkdirSync(screenshotDir, { recursive: true });
  await page.screenshot({ path: path.join(screenshotDir, `${name}.png`), fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- local Playwright helper writes screenshots under caller-selected local artifact dir
}

async function capturePersistenceState(page) {
  return page.evaluate(() => {
    const selectedRadio = document.querySelector('input[name="playwright_radio_field"]:checked');
    return {
      fdid: document.getElementById('fdid') ? document.getElementById('fdid').value : '',
      textValue: document.getElementById('playwright_text_field') ? document.getElementById('playwright_text_field').value : '',
      textareaValue: document.getElementById('playwright_textarea_field') ? document.getElementById('playwright_textarea_field').value : '',
      selectValue: document.getElementById('playwright_select_field') ? document.getElementById('playwright_select_field').value : '',
      radioValue: selectedRadio ? selectedRadio.value : '',
      checkedBox: document.getElementById('playwright_checkbox_checked') ? document.getElementById('playwright_checkbox_checked').checked : false,
      uncheckedBox: document.getElementById('playwright_checkbox_unchecked') ? document.getElementById('playwright_checkbox_unchecked').checked : false,
      autoclose: document.getElementById('isSuccess_Autoclose') ? document.getElementById('isSuccess_Autoclose').value : '',
      closeIntercepted: Boolean(window.__playwrightCloseIntercepted),
    };
  });
}

async function saveEformAndCaptureFdid(page, expectedState) {
  await assertNotErrorPage(page, 'add eForm page');
  await assertImageLoaded(page, '#BGImage1', 'add eForm fixture background image');
  await page.locator('#playwright_text_field').fill(expectedState.textValue);
  await page.locator('#playwright_textarea_field').fill(expectedState.textareaValue);
  await page.locator('#playwright_select_field').selectOption(expectedState.selectValue);
  await page.locator(`input[name="playwright_radio_field"][value="${expectedState.radioValue}"]`).check({ force: true });
  await page.locator('#playwright_checkbox_checked').evaluate((element) => {
    element.checked = true;
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await page.locator('#playwright_checkbox_unchecked').evaluate((element) => {
    element.checked = false;
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await page.locator('#remote_eform_subject').fill(expectedState.subjectValue);

  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('#remoteSubmitButton').click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.locator('#fdid').waitFor({ state: 'attached', timeout: 15000 });

  const state = await capturePersistenceState(page);
  assert(/^\d+$/.test(state.fdid), `Expected numeric fdid after save, got ${JSON.stringify(state)}`);
  assert(state.textValue === expectedState.textValue, `Saved page did not preserve text value after submit: ${JSON.stringify(state)}`);
  assert(state.textareaValue === expectedState.textareaValue, `Saved page did not preserve textarea value after submit: ${JSON.stringify(state)}`);
  assert(state.selectValue === expectedState.selectValue, `Saved page did not preserve select value after submit: ${JSON.stringify(state)}`);
  assert(state.radioValue === expectedState.radioValue, `Saved page did not preserve radio value after submit: ${JSON.stringify(state)}`);
  assert(state.checkedBox === true, `Saved page did not preserve checked checkbox state after submit: ${JSON.stringify(state)}`);
  assert(state.uncheckedBox === false, `Saved page did not preserve unchecked checkbox state after submit: ${JSON.stringify(state)}`);
  assert(state.autoclose === 'true', `Expected auto-close success flag after save, got ${JSON.stringify(state)}`);
  return state.fdid;
}

async function assertSavedFormState(page, expectedState, expectedFdid, screenshotName) {
  await assertNotErrorPage(page, screenshotName);
  await assertImageLoaded(page, '#BGImage1', `${screenshotName} background image`);
  await page.locator('#playwright_text_field').waitFor({ state: 'visible', timeout: 15000 });
  const state = await capturePersistenceState(page);
  assert(state.fdid === expectedFdid, `${screenshotName} did not render the expected fdid: ${JSON.stringify(state)}`);
  assert(state.textValue === expectedState.textValue, `${screenshotName} did not render persisted text value: ${JSON.stringify(state)}`);
  assert(state.textareaValue === expectedState.textareaValue, `${screenshotName} did not render persisted textarea value: ${JSON.stringify(state)}`);
  assert(state.selectValue === expectedState.selectValue, `${screenshotName} did not render persisted select value: ${JSON.stringify(state)}`);
  assert(state.radioValue === expectedState.radioValue, `${screenshotName} did not render persisted radio value: ${JSON.stringify(state)}`);
  assert(state.checkedBox === true, `${screenshotName} did not render the checked checkbox as checked: ${JSON.stringify(state)}`);
  assert(state.uncheckedBox === false, `${screenshotName} incorrectly rendered the unchecked checkbox as checked: ${JSON.stringify(state)}`);
  await screenshot(page, screenshotName);
}

async function openNewConsultation(context) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.close = () => {
      window.__playwrightCloseIntercepted = true;
    };
  });
  wirePage(page, 'consult-new');
  await gotoApp(page, `/encounter/oscarConsultationRequest/ViewConsultationFormRequest?de=${encodeURIComponent(demographicNo)}&teamVar=&appNo=`);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return page;
}

async function prepareConsultationForm(page) {
  await assertNotErrorPage(page, 'consultation form page');
  const specialistSelect = page.locator('#specialist');
  assert(await specialistSelect.count(), 'Consultation page did not render the specialist selector');
  const options = await specialistSelect.locator('option').evaluateAll((nodes) => nodes
    .map((node) => ({ value: node.value, text: (node.textContent || '').trim() }))
    .filter((option) => option.value && option.value !== '-1'));

  let submitMode = 'button';
  if (options.length > 0) {
    await specialistSelect.selectOption(options[0].value);
    await page.waitForFunction(() => {
      const serviceField = document.forms.EctConsultationFormRequest2Form && document.forms.EctConsultationFormRequest2Form.service;
      return serviceField && serviceField.value && serviceField.value !== '0';
    }, { timeout: 15000 });
  } else {
    const serviceWasSet = await page.evaluate(() => {
      const form = document.forms.EctConsultationFormRequest2Form;
      if (!form || !form.service) {
        return false;
      }
      const services = Array.isArray(window.consultationServices) ? window.consultationServices : [];
      const usable = services.find((service) => service && String(service.id || '') !== '' && String(service.id) !== '-1');
      form.service.value = usable ? String(usable.id) : '57';
      return form.service.value && form.service.value !== '0';
    });
    assert(serviceWasSet, 'Consultation page did not expose a usable service id for fallback submission');
    submitMode = 'programmatic';
  }

  await page.locator('textarea[name="appointmentNotes"]').fill(`Playwright consultation note ${Date.now()}`);
  return submitMode;
}

async function openConsultAttachmentPanelAndAttachEform(page, fdid) {
  await page.locator('#attachDocumentPanelBtn').click();
  await page.locator(`#eFormNo${fdid}`).waitFor({ state: 'visible', timeout: 15000 });
  const eformEntry = page.locator(`#eFormNo${fdid}`).locator('xpath=ancestor::li[1]');
  await Promise.all([
    page.waitForResponse((response) => response.url().includes('/previewDocs?method=renderEFormPDF') && response.request().method() === 'GET'),
    eformEntry.locator('button.preview-button').click(),
  ]);
  await eformEntry.locator(`input[type="checkbox"][value="${fdid}"]`).check();
  const checked = await eformEntry.locator(`input[type="checkbox"][value="${fdid}"]`).isChecked();
  assert(checked, `Consultation attachment picker did not keep fdid ${fdid} selected`);
  await screenshot(page, 'consultation-linked-open');
}

(async () => {
  const fixture = createFixtureFiles();
  const timestamp = Date.now();
  const formName = `Playwright Consultation Acceptance ${timestamp}`;
  const formSubject = `Consult Acceptance ${timestamp}`;
  const expectedState = {
    textValue: `Playwright text ${timestamp}`,
    textareaValue: `Playwright textarea ${timestamp}`,
    selectValue: 'charlie',
    radioValue: 'bravo',
    subjectValue: `Playwright subject ${timestamp}`,
  };

  let importedFid = null;
  let managerPage = null;
  let libraryFid = null;
  let libraryRuntimeProbe = null;
  let browser = null;

  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  try {
    browser = await chromium.launch(launchOptions);
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1600 } });
    const landingPage = await login(context);
    await landingPage.close();

    libraryFid = await findExistingLibraryEform(context, libraryEformName);
    const libraryTemplateHtml = await readManagerTemplateHtml(context, libraryFid);
    assert(libraryTemplateHtml.includes('${oscar_image_path}'), `Existing library eForm ${libraryEformName} did not retain oscar_image_path image references`);
    for (const imageName of libraryEformExpectedTemplateImages) {
      assert(libraryTemplateHtml.includes(imageName), `Existing library eForm ${libraryEformName} template did not retain expected background image ${imageName}`);
    }
    libraryRuntimeProbe = await probeExistingLibraryEform(context, libraryFid);

    await ensureImageUploaded(context, fixture.imagePath, bgImageName);
    const uploadResult = await uploadEform(context, formName, formSubject, fixture.htmlPath);
    managerPage = uploadResult.page;
    importedFid = uploadResult.fid;

    const addPage = await openAddEform(context, importedFid);
    const fdid = await saveEformAndCaptureFdid(addPage, expectedState);
    await screenshot(addPage, 'consult-acceptance-after-save');
    await addPage.close();

    const directPage = await openSavedEformDirect(context, fdid);
    await assertSavedFormState(directPage, expectedState, fdid, 'consult-acceptance-direct-reopen');
    await directPage.close();

    const patientListPopup = await openSavedEformFromPatientList(context, fdid);
    assert(patientListPopup.url().includes(`fdid=${fdid}`), `Patient list popup did not open the expected saved-form route: ${patientListPopup.url()}`);
    await assertSavedFormState(patientListPopup, expectedState, fdid, 'consult-acceptance-patient-list-reopen');
    await patientListPopup.close();

    const newConsultationPage = await openNewConsultation(context);
    await prepareConsultationForm(newConsultationPage);
    await openConsultAttachmentPanelAndAttachEform(newConsultationPage, fdid);
    await newConsultationPage.close();

    const syntheticBackgroundResponses = collectDisplayImageFetches(bgImageName);
    assert(syntheticBackgroundResponses.length > 0, `displayImage was never requested for ${bgImageName}`);
    assert(syntheticBackgroundResponses.some((response) => response.status === 200), `displayImage never returned 200 for ${bgImageName}: ${JSON.stringify(syntheticBackgroundResponses, null, 2)}`);
    assert(libraryRuntimeProbe.renderSurfaceUsable, `Existing library eForm ${libraryEformName} did not render a usable background-backed surface: ${JSON.stringify(libraryRuntimeProbe)}`);
    assert(eformPreviewResponses.some((response) => response.status === 200), `Consultation attachment preview never produced a 200 renderEFormPDF response: ${JSON.stringify(eformPreviewResponses, null, 2)}`);
    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
    const renderConsoleIssues = consoleIssues.filter((issue) => ['add-eform', 'saved-direct', 'patient-list-popup', 'consult-new'].includes(issue.label));
    assert(renderConsoleIssues.length === 0, `unexpected render-surface browser console failures: ${JSON.stringify(renderConsoleIssues, null, 2)}`);

    console.log('PASS eForm consultation acceptance workflow preserved saved values, reopened the saved fdid, reused that same saved eForm in the consultation attachment workflow, and probed the existing Signature trick library form for its stored image-layer template');
    console.log(`Imported fid=${importedFid}, saved fdid=${fdid}, consultation request id=n/a (consultation attachment fallback), library fid=${libraryFid}, library runtime probe=${JSON.stringify(libraryRuntimeProbe)}, synthetic background responses=${JSON.stringify(syntheticBackgroundResponses)}`);
    console.log(`Screenshots written under ${screenshotDir}`);
  } finally {
    if (managerPage && !managerPage.isClosed()) {
      await managerPage.close().catch(() => {});
    }
    if (browser) {
      await browser.close();
    }
    cleanupFixtureFiles(fixture);
  }
})().catch((error) => {
  console.error('FAIL eForm consultation acceptance Playwright check');
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
  if (eformPreviewResponses.length) {
    console.error(`eForm preview responses: ${JSON.stringify(eformPreviewResponses, null, 2)}`);
  }
  if (consultPreviewResponses.length) {
    console.error(`consult preview responses: ${JSON.stringify(consultPreviewResponses, null, 2)}`);
  }
  process.exit(1);
});
