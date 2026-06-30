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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for adding a prescription signature.
 *
 * The script logs in, initializes the prescription session for an existing
 * prescription, clears any existing signature association for the duration of
 * the run, uploads a new signature using the production upload endpoint, saves
 * it through /rx/saveDigitalSignature, verifies the live preview reloads the
 * signature image, then rebuilds the prescription view and verifies the stored
 * signature is persisted on the preview.
 *
 * Required fixture:
 *   PRESCRIPTION_SCRIPT_ID=123 npm run test:prescription-signature-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   PRESCRIPTION_DEMOGRAPHIC_NO=1
 *   PRESCRIPTION_PHARMACY_ID=6
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const prescriptionScriptId = String(process.env.PRESCRIPTION_SCRIPT_ID || '').trim();
const prescriptionDemographicNo = String(process.env.PRESCRIPTION_DEMOGRAPHIC_NO || '1').trim();
const prescriptionPharmacyId = String(process.env.PRESCRIPTION_PHARMACY_ID || '').trim();

const findings = [];
const visited = [];

if (!/^\d+$/.test(prescriptionScriptId)) {
  throw new Error('PRESCRIPTION_SCRIPT_ID must be set to an existing numeric prescription script id');
}
if (!/^\d+$/.test(prescriptionDemographicNo)) {
  throw new Error('PRESCRIPTION_DEMOGRAPHIC_NO must be numeric');
}
if (prescriptionPharmacyId && !/^\d+$/.test(prescriptionPharmacyId)) {
  throw new Error('PRESCRIPTION_PHARMACY_ID must be numeric when provided');
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
  const parsedPath = new URL(appPath, 'http://app.local');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${parsedPath.pathname}`.replace(/\/{2,}/g, '/');
  url.search = parsedPath.search;
  url.hash = parsedPath.hash;
  return url.toString();
}

function gotoApp(page, appPath, options = { waitUntil: 'domcontentloaded', timeout: 30000 }) {
  return page.goto(appUrl(appPath), options); // nosemgrep // appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts.
}

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (/\/favicon\.ico$/.test(responseUrl)
    || /\/imageRenderingServlet\?source=signature_stored&digitalSignatureId=\d+/.test(responseUrl));
}

function isExpectedConsoleNoise(message) {
  const location = message.location ? message.location() : {};
  return /Failed to load resource: the server responded with a status of 404/.test(message.text())
    && /\/imageRenderingServlet\?source=signature_stored&digitalSignatureId=\d+/.test(location.url || '');
}

function isExpectedPageError(error) {
  const text = error.stack || error.message || '';
  return /Cannot set properties of null \(setting 'innerHTML'\)/.test(text) && /expandPreview/.test(text);
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
    const text = message.text();
    if (isExpectedConsoleNoise(message)) {
      return;
    }
    if (message.type() === 'error' || /(ReferenceError|TypeError|SyntaxError|Cannot read|Cannot set)/i.test(text)) {
      findings.push({ label, type: `console:${message.type()}`, text, location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    if (isExpectedPageError(error)) {
      return;
    }
    findings.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
  page.on('dialog', async (dialog) => {
    findings.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
}

async function assertNoErrorPage(page, label) {
  const bodyText = await page.locator('body').innerText().catch(() => '');
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report|Security Error/i.test(bodyText)) {
    findings.push({
      label,
      type: 'error-page',
      url: page.url(),
      body: bodyText.replace(/\s+/g, ' ').slice(0, 500),
    });
  }
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
  visited.push({ label: 'login', url: page.url() });
  await assertNoErrorPage(page, 'login');
  return page;
}

async function choosePrescriptionPatient(page) {
  const params = new URLSearchParams({ demographicNo: prescriptionDemographicNo });
  await gotoApp(page, `/rx/choosePatient?${params.toString()}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'choose-patient', url: page.url() });
  await assertNoErrorPage(page, 'choose-patient');
}

async function postReprintSession(page) {
  const result = await page.evaluate(async ({ scriptId }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection // scriptId is numeric-validated before this call; data is passed as a serialized argument, not string-interpolated into the function body.
    const csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
    const csrfToken = csrfEl ? csrfEl.value || '' : '';
    const body = new URLSearchParams({
      scriptNo: scriptId,
      rand: String(Math.floor(Math.random() * 10001)),
    });
    if (csrfToken) {
      body.append('CSRF-TOKEN', csrfToken);
    }
    const contextPath = window.location.pathname.split('/rx/')[0] || '';
    const response = await fetch(`${contextPath}/rx/rePrescribe2?method=reprint2`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'X-Requested-With': 'XMLHttpRequest',
        'CSRF-TOKEN': csrfToken,
      },
      body,
    });
    return {
      status: response.status,
      contentType: response.headers.get('content-type') || '',
      text: await response.text(),
    };
  }, { scriptId: prescriptionScriptId });
  if (result.status !== 200) {
    throw new Error(`Prescription reprint session setup returned HTTP ${result.status}: ${result.text.slice(0, 300)}`);
  }
}

async function openPrescriptionView(page, label) {
  await choosePrescriptionPatient(page);
  await postReprintSession(page);
  const viewParams = new URLSearchParams({ scriptId: prescriptionScriptId });
  if (prescriptionPharmacyId) {
    viewParams.set('pharmacyId', prescriptionPharmacyId);
  }
  await gotoApp(page, `/rx/viewScript?${viewParams.toString()}`);
  await page.locator('#preview').waitFor({ state: 'attached', timeout: 30000 });
  await previewFrame(page);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label, url: page.url() });
  await assertNoErrorPage(page, label);
}

async function previewFrame(page) {
  const iframe = await page.locator('#preview').elementHandle({ timeout: 30000 });
  const frame = await iframe.contentFrame();
  if (!frame) {
    throw new Error('Prescription preview iframe did not expose a frame');
  }
  try {
    await frame.locator('#signature').waitFor({ state: 'attached', timeout: 30000 });
  } catch (error) {
    const bodyText = await frame.locator('body').innerText().catch(() => '');
    throw new Error(`Prescription preview did not render #signature at ${frame.url()}: ${bodyText.replace(/\s+/g, ' ').slice(0, 500)}`);
  }
  return frame;
}

async function readPreviewSignature(page, options = {}) {
  const frame = await previewFrame(page);
  if (options.requireLoaded !== false) {
    await frame.waitForFunction(() => {
      const img = document.getElementById('signature');
      return img && img.complete && img.naturalWidth > 0 && img.naturalHeight > 0;
    }, { timeout: 15000 });
  }
  return frame.evaluate(() => {
    const img = document.getElementById('signature');
    const storedMatch = img && img.src.match(/[?&]digitalSignatureId=(\d+)/);
    return {
      src: img ? img.src : '',
      width: img ? img.naturalWidth : 0,
      height: img ? img.naturalHeight : 0,
      storedSignatureId: storedMatch ? storedMatch[1] : '',
    };
  });
}

async function waitForSignatureFrame(page) {
  await page.locator('#signatureFrame').waitFor({ state: 'attached', timeout: 30000 });
  const signatureFrame = await page.locator('#signatureFrame').elementHandle();
  const frame = await signatureFrame.contentFrame();
  if (!frame) {
    throw new Error('Prescription signature iframe did not expose a frame');
  }
  await frame.locator('#signatureForm').waitFor({ state: 'attached', timeout: 30000 });
}

async function readCsrfToken(page) {
  const csrfInput = page.locator('input[name="CSRF-TOKEN"]').first();
  if (await csrfInput.count() === 0) {
    return '';
  }
  return csrfInput.inputValue({ timeout: 5000 }).catch(() => '');
}

async function savePrescriptionSignatureAssociation(page, digitalSignatureId) {
  const signatureId = String(digitalSignatureId || '').trim();
  if (signatureId && !/^\d+$/.test(signatureId)) {
    throw new Error(`Prescription digitalSignatureId must be numeric when provided, got ${signatureId}`);
  }

  const csrfToken = await readCsrfToken(page);
  const form = {
    method: 'saveDigitalSignature',
    scriptId: prescriptionScriptId,
  };
  if (signatureId) {
    form.digitalSignatureId = signatureId;
  }
  if (csrfToken) {
    form['CSRF-TOKEN'] = csrfToken;
  }

  const headers = { 'X-Requested-With': 'XMLHttpRequest' };
  if (csrfToken) {
    headers['CSRF-TOKEN'] = csrfToken;
  }

  const response = await page.context().request.post(appUrl('/rx/saveDigitalSignature'), {
    form,
    headers,
  });
  const text = await response.text();
  if (response.status() !== 200) {
    throw new Error(`Saving prescription signature association returned HTTP ${response.status()}: ${text.slice(0, 300)}`);
  }
}

async function uploadPrescriptionSignature(page) {
  return page.evaluate(async ({ demographicNo }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection // demographicNo is numeric-validated before this call; data is passed as a serialized argument, not string-interpolated into the function body.
    const getCsrfToken = () => {
      const csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
      return csrfEl ? csrfEl.value || '' : '';
    };
    const iframe = document.getElementById('signatureFrame');
    if (!iframe) {
      throw new Error('signatureFrame is missing; prescription may already have a stored signature or Rx signatures are disabled');
    }
    const iframeUrl = new URL(iframe.src, window.location.href);
    const signatureKey = iframeUrl.searchParams.get('signatureRequestId')
      || (document.querySelector('input[name="signatureRequestId"]') || {}).value
      || '';
    if (!/^[a-zA-Z0-9]+$/.test(signatureKey)) {
      throw new Error(`Missing or invalid prescription signatureRequestId: ${signatureKey}`);
    }

    const canvas = document.createElement('canvas');
    canvas.width = 300;
    canvas.height = 60;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.strokeStyle = '#111';
    ctx.lineWidth = 5;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(20, 40);
    ctx.bezierCurveTo(70, 5, 115, 58, 165, 25);
    ctx.bezierCurveTo(200, 4, 235, 44, 280, 18);
    ctx.stroke();

    const csrfToken = getCsrfToken();
    const body = new URLSearchParams({
      source: 'IPAD',
      signatureImage: canvas.toDataURL('image/png'),
      signatureRequestId: signatureKey,
      demographicNo,
      ModuleType: 'PRESCRIPTION',
      saveToDB: 'true',
    });
    if (csrfToken) {
      body.append('CSRF-TOKEN', csrfToken);
    }

    const uploadUrl = `${iframeUrl.origin}${iframeUrl.pathname.replace('/signature_pad/tabletSignature', '/signature_pad/SaveSignatureUpload')}`;
    const response = await fetch(uploadUrl, {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'X-Requested-With': 'XMLHttpRequest',
        'CSRF-TOKEN': csrfToken,
      },
      body,
    });
    const text = await response.text();
    if (response.status !== 200) {
      throw new Error(`SaveSignatureUpload returned HTTP ${response.status}: ${text.slice(0, 300)}`);
    }
    const doc = new DOMParser().parseFromString(text, 'text/html');
    const signatureId = (doc.querySelector('input[name="signatureId"]') || {}).value || '';
    if (!/^\d+$/.test(signatureId)) {
      throw new Error(`SaveSignatureUpload did not return a numeric signatureId: ${text.slice(0, 300)}`);
    }
    return { signatureId, signatureKey };
  }, { demographicNo: prescriptionDemographicNo });
}

async function refreshLivePreview(page) {
  await page.evaluate(() => {
    if (typeof window.refreshImage !== 'function') {
      throw new Error('ViewScript2 refreshImage() is missing');
    }
    window.refreshImage();
  });
  const state = await readPreviewSignature(page);
  if (!/source=signature_preview/.test(state.src)) {
    throw new Error(`Expected live preview image to use signature_preview after add, got ${state.src}`);
  }
  return state;
}

async function runPrescriptionSignatureCheck(context) {
  const page = await context.newPage();
  wirePage(page, 'prescription-signature');
  let originalSignatureId = '';
  let uploadedSignatureId = '';

  try {
    await openPrescriptionView(page, 'initial-prescription-view');
    const initialPreview = await readPreviewSignature(page, { requireLoaded: false });
    originalSignatureId = initialPreview.storedSignatureId;

    await savePrescriptionSignatureAssociation(page, null);
    await openPrescriptionView(page, 'unsigned-prescription-view');
    await waitForSignatureFrame(page);

    const upload = await uploadPrescriptionSignature(page);
    uploadedSignatureId = upload.signatureId;
    await savePrescriptionSignatureAssociation(page, uploadedSignatureId);
    const livePreview = await refreshLivePreview(page);

    await openPrescriptionView(page, 'stored-prescription-view');
    const storedPreview = await readPreviewSignature(page);
    if (storedPreview.storedSignatureId !== uploadedSignatureId) {
      throw new Error(`Expected stored preview signature id ${uploadedSignatureId}, got ${storedPreview.storedSignatureId || 'none'} from ${storedPreview.src}`);
    }
    if (!/source=signature_stored/.test(storedPreview.src)) {
      throw new Error(`Expected stored preview image after reload, got ${storedPreview.src}`);
    }

    return {
      scriptId: prescriptionScriptId,
      demographicNo: prescriptionDemographicNo,
      pharmacyId: prescriptionPharmacyId,
      originalSignatureId,
      uploadedSignatureId,
      livePreview,
      storedPreview,
    };
  } finally {
    if (uploadedSignatureId) {
      try {
        await savePrescriptionSignatureAssociation(page, originalSignatureId || null);
      } catch (error) {
        findings.push({
          label: 'prescription-signature:restore',
          type: 'restore-error',
          text: error.stack || error.message,
        });
      }
    }
    await page.close();
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

  const browser = await chromium.launch(launchOptions);
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
    const loginPage = await login(context);
    await loginPage.close();

    const result = await runPrescriptionSignatureCheck(context);
    console.log(JSON.stringify({ visited, result, findings }, null, 2));
    const blockingFindings = findings.filter((finding) => finding.type !== 'dialog');
    if (blockingFindings.length) {
      process.exitCode = 1;
    }
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
