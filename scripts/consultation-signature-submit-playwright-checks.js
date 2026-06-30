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
 * Browser smoke test for consultation request signature submission (PR 3019).
 *
 * Tests the create/update submit paths for the three signature modes introduced or
 * extended by the signatures PR, specifically:
 *
 *   1. stamp-create-happy    — POST new consultation with stamp provider; assert no warning
 *   2. stamp-create-warning  — POST with provider that has no stamp file; assert warning
 *   3. stamp-update          — POST update to an unsigned consultation; assert stamp applied
 *   4. stamp-print-preview   — POST "And Print Preview" with stamp override; assert PDF has image
 *
 * The existing consultation-signature-playwright-checks.js covers the complementary path:
 * viewing already-persisted STORED signatures and their PDF rendering.
 *
 * Required environment variables:
 *   CONSULT_DEMO_NO=<id>        Demographic number for the test patient
 *   CONSULT_SERVICE_ID=<id>     Service ID to use when creating consultation requests
 *
 * Optional environment variables:
 *   CONSULT_STAMP_PROVIDER_NO=<providerNo>   Provider whose stamp PNG exists in the
 *                                             eForm image dir (default: 999998)
 *   CONSULT_UNSIGNED_REQUEST_ID=<id>          Existing consultation with no signature;
 *                                             required for stamp-update and stamp-print-preview
 *                                             scenarios (those scenarios are skipped when absent)
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   CHROME_PATH=/path/to/chrome
 *   ALLOW_NON_LOCAL_BASE_URL=true   Only when intentionally targeting a non-local test server
 *
 * Example invocation:
 *   CONSULT_DEMO_NO=1 CONSULT_SERVICE_ID=1 node scripts/consultation-signature-submit-playwright-checks.js
 *
 * With all scenarios:
 *   CONSULT_DEMO_NO=1 CONSULT_SERVICE_ID=1 CONSULT_UNSIGNED_REQUEST_ID=42 \
 *     node scripts/consultation-signature-submit-playwright-checks.js
 */

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const consultDemoNo = process.env.CONSULT_DEMO_NO || '';
const consultServiceId = process.env.CONSULT_SERVICE_ID || '';
const stampProviderNo = process.env.CONSULT_STAMP_PROVIDER_NO || '999998';
const unsignedRequestId = process.env.CONSULT_UNSIGNED_REQUEST_ID || '';

if (!/^\d+$/.test(consultDemoNo)) {
  throw new Error('CONSULT_DEMO_NO must be set to a numeric demographic number');
}
if (!/^\d+$/.test(consultServiceId)) {
  throw new Error('CONSULT_SERVICE_ID must be set to a numeric service ID');
}
if (!/^\d+$/.test(stampProviderNo)) {
  throw new Error('CONSULT_STAMP_PROVIDER_NO must be numeric when provided');
}
if (unsignedRequestId && !/^\d+$/.test(unsignedRequestId)) {
  throw new Error('CONSULT_UNSIGNED_REQUEST_ID must be numeric when provided');
}

const findings = [];
const visited = [];

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
  return status === 404 && /\/favicon\.ico$/.test(responseUrl);
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
    if (message.type() === 'error' || /(ReferenceError|TypeError|SyntaxError|Cannot read|Cannot set)/i.test(text)) {
      findings.push({ label, type: `console:${message.type()}`, text, location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    findings.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
  page.on('dialog', async (dialog) => {
    findings.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
}

async function assertNoErrorPage(page, label) {
  const bodyText = await page.locator('body').innerText().catch(() => '');
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(bodyText)) {
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

async function checkStampAvailable(context) {
  const response = await context.request.get(
    appUrl(`/provider/providerSignatureImage?providerNo=${stampProviderNo}`),
  );
  const contentType = response.headers()['content-type'] || '';
  if (response.status() === 200 && contentType.startsWith('image/')) {
    return true;
  }
  console.error(
    `[skip] Stamp file not available for provider ${stampProviderNo} ` +
    `(HTTP ${response.status()}, Content-Type: ${contentType}). ` +
    `Configure consult_sig_${stampProviderNo}.png in the eForm image directory to enable stamp scenarios.`,
  );
  return false;
}

async function getCsrfToken(context) {
  const page = await context.newPage();
  try {
    await gotoApp(page, `/encounter/oscarConsultationRequest/ViewConsultationFormRequest?de=${consultDemoNo}`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const token = await page.evaluate(() => {
      const el = document.querySelector('input[name="CSRF-TOKEN"]');
      return el ? el.value || '' : '';
    });
    return token;
  } finally {
    await page.close();
  }
}

async function postConsultation(context, formParams, csrfToken, label) {
  const form = Object.assign({}, formParams, { 'CSRF-TOKEN': csrfToken });
  const response = await context.request.post(appUrl('/encounter/RequestConsultation'), {
    form,
    headers: {
      'CSRF-TOKEN': csrfToken,
      'X-Requested-With': 'XMLHttpRequest',
    },
  });
  visited.push({ label, url: response.url() });
  return response;
}

async function openConsultationRequest(page, requestId, label) {
  await gotoApp(page, `/encounter/ViewRequest?requestId=${requestId}`);
  await page.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label, url: page.url() });
  await assertNoErrorPage(page, label);
}

async function readSignatureState(page) {
  return page.evaluate(() => {
    const valueOf = (name) => {
      const el = document.querySelector(`[name="${name}"]`);
      return el ? el.value || '' : '';
    };
    const img = document.getElementById('signatureImgTag');
    const style = img ? window.getComputedStyle(img) : null;
    return {
      signatureImg: valueOf('signatureImg'),
      imageLoaded: img ? (img.complete && img.naturalWidth > 0) : false,
      imageVisible: style ? (style.display !== 'none' && style.visibility !== 'hidden') : false,
    };
  });
}

async function requestPrintPreviewWithStamp(page) {
  const result = await page.evaluate(async (provider) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection // provider is a validated numeric string (parseInt-checked before invocation)
    const form = document.getElementById('EctConsultationFormRequest2Form');
    if (!form) {
      throw new Error('Consultation form #EctConsultationFormRequest2Form not found');
    }

    const submissionEl = form.querySelector('[name="submission"]');
    const origSubmission = submissionEl ? submissionEl.value : '';
    if (submissionEl) submissionEl.value = 'And Print Preview';

    const newSignatureEl = form.querySelector('[name="newSignature"]');
    const origNewSignature = newSignatureEl ? newSignatureEl.value : '';
    if (newSignatureEl) newSignatureEl.value = 'false';

    const signatureImgEl = form.querySelector('[name="signatureImg"]');
    const origSignatureImg = signatureImgEl ? signatureImgEl.value : '';
    if (signatureImgEl) signatureImgEl.value = '';

    const sigProviderEl = form.querySelector('[name="signatureProviderNo"]');
    const origSigProvider = sigProviderEl ? sigProviderEl.value : '';
    if (sigProviderEl) sigProviderEl.value = provider;

    const body = new URLSearchParams(new FormData(form));
    const csrfEl = form.querySelector('input[name="CSRF-TOKEN"]') || document.querySelector('input[name="CSRF-TOKEN"]');
    const csrfToken = csrfEl ? csrfEl.value || '' : '';
    if (csrfToken && !body.has('CSRF-TOKEN')) {
      body.append('CSRF-TOKEN', csrfToken);
    }

    try {
      const response = await fetch(form.action, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
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
    } finally {
      if (submissionEl) submissionEl.value = origSubmission;
      if (newSignatureEl) newSignatureEl.value = origNewSignature;
      if (signatureImgEl) signatureImgEl.value = origSignatureImg;
      if (sigProviderEl) sigProviderEl.value = origSigProvider;
    }
  }, stampProviderNo);

  if (result.status !== 200) {
    throw new Error(`Print preview returned HTTP ${result.status}: ${result.text.slice(0, 300)}`);
  }

  let payload;
  try {
    payload = JSON.parse(result.text);
  } catch (err) {
    throw new Error(`Print preview did not return JSON (${result.contentType}): ${result.text.slice(0, 300)}`);
  }

  if (payload.errorMessage) {
    throw new Error(`Print preview returned errorMessage: ${payload.errorMessage}`);
  }
  if (!payload.consultPDF) {
    throw new Error('Print preview response did not include consultPDF');
  }

  const pdfBytes = Buffer.from(payload.consultPDF, 'base64');
  if (pdfBytes.length < 100 || pdfBytes.slice(0, 4).toString('ascii') !== '%PDF') {
    throw new Error(`Print preview response was not a valid PDF (length ${pdfBytes.length})`);
  }

  return {
    byteLength: pdfBytes.length,
    containsImage: /\/Subtype\s*\/Image/.test(pdfBytes.toString('latin1')),
  };
}

async function runStampCreateHappy(context, csrfToken) {
  const label = 'stamp-create-happy';
  const response = await postConsultation(context, {
    submission: 'Submit Consultation Request',
    demographicNo: consultDemoNo,
    service: consultServiceId,
    specialist: '0',
    sendTo: '',
    newSignature: 'false',
    signatureImg: '',
    signatureProviderNo: stampProviderNo,
    newSignatureImg: '',
  }, csrfToken, label);

  const finalUrl = response.url();
  if (finalUrl.includes('signatureNotApplied=1')) {
    throw new Error(
      `${label}: stamp was not applied — redirect URL contains signatureNotApplied=1: ${finalUrl}`,
    );
  }

  const html = await response.text();
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(html)) {
    findings.push({ label, type: 'error-page', url: finalUrl, body: html.replace(/\s+/g, ' ').slice(0, 500) });
    throw new Error(`${label}: server error page returned after consultation create`);
  }

  return { redirectUrl: finalUrl, signatureNotApplied: false };
}

async function runStampCreateWarning(context, csrfToken) {
  const label = 'stamp-create-warning';
  // Provider 99999 has no stamp file → triggers STAMP_FILE_MISSING → isGenuineFailure() → signatureNotApplied=1
  const response = await postConsultation(context, {
    submission: 'Submit Consultation Request',
    demographicNo: consultDemoNo,
    service: consultServiceId,
    specialist: '0',
    sendTo: '',
    newSignature: 'false',
    signatureImg: '',
    signatureProviderNo: '99999',
    newSignatureImg: '',
  }, csrfToken, label);

  const finalUrl = response.url();
  if (!finalUrl.includes('signatureNotApplied=1')) {
    throw new Error(
      `${label}: expected signatureNotApplied=1 in redirect URL but got: ${finalUrl}`,
    );
  }

  const html = await response.text();
  if (!/alert-warning/i.test(html)) {
    throw new Error(`${label}: warning alert (.alert-warning) not found in ConfirmConsultationRequest page`);
  }
  if (/HTTP Status 500|Exception Report/i.test(html)) {
    findings.push({ label, type: 'error-page', url: finalUrl, body: html.replace(/\s+/g, ' ').slice(0, 500) });
    throw new Error(`${label}: server error page returned after consultation create with no stamp`);
  }

  return { redirectUrl: finalUrl, signatureNotApplied: true, warningAlertPresent: true };
}

async function runStampUpdate(context, csrfToken) {
  const label = 'stamp-update';
  const response = await postConsultation(context, {
    submission: 'Update Consultation Request',
    requestId: unsignedRequestId,
    demographicNo: consultDemoNo,
    service: consultServiceId,
    specialist: '0',
    sendTo: '',
    newSignature: 'false',
    signatureImg: '',
    signatureProviderNo: stampProviderNo,
    newSignatureImg: '',
  }, csrfToken, label);

  const finalUrl = response.url();
  if (finalUrl.includes('signatureNotApplied=1')) {
    throw new Error(
      `${label}: stamp was not applied on update — redirect URL contains signatureNotApplied=1: ${finalUrl}`,
    );
  }

  const html = await response.text();
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(html)) {
    findings.push({ label, type: 'error-page', url: finalUrl, body: html.replace(/\s+/g, ' ').slice(0, 500) });
    throw new Error(`${label}: server error page returned after consultation update`);
  }

  // Verify the signature now appears on the consultation view page
  const page = await context.newPage();
  wirePage(page, `${label}:view`);
  try {
    await openConsultationRequest(page, unsignedRequestId, `${label}:view`);
    const sigState = await readSignatureState(page);
    if (!/^\d+$/.test(sigState.signatureImg)) {
      throw new Error(
        `${label}: signatureImg field is not a numeric DigitalSignature ID after update; got "${sigState.signatureImg}"`,
      );
    }
    if (!sigState.imageLoaded) {
      throw new Error(`${label}: #signatureImgTag did not load after stamp update (naturalWidth=0 or not complete)`);
    }
    return { redirectUrl: finalUrl, signatureNotApplied: false, signatureImg: sigState.signatureImg, imageLoaded: sigState.imageLoaded };
  } finally {
    await page.close();
  }
}

async function runStampPrintPreview(context) {
  const label = 'stamp-print-preview';
  const page = await context.newPage();
  wirePage(page, label);
  try {
    await openConsultationRequest(page, unsignedRequestId, label);
    const pdf = await requestPrintPreviewWithStamp(page);
    if (!pdf.containsImage) {
      throw new Error(`${label}: print preview PDF did not contain an embedded image (stamp expected)`);
    }
    return pdf;
  } finally {
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

    const stampAvailable = await checkStampAvailable(context);
    const csrfToken = await getCsrfToken(context);

    const scenarios = [];

    // Scenario 1: stamp-create-happy
    if (stampAvailable) {
      try {
        const result = await runStampCreateHappy(context, csrfToken);
        scenarios.push({ name: 'stamp-create-happy', skipped: false, ...result });
      } catch (err) {
        findings.push({ label: 'stamp-create-happy', type: 'scenario-error', text: err.message });
        scenarios.push({ name: 'stamp-create-happy', skipped: false, error: err.message });
      }
    } else {
      scenarios.push({ name: 'stamp-create-happy', skipped: true, reason: `No stamp file for provider ${stampProviderNo}` });
    }

    // Scenario 2: stamp-create-warning (no stamp needed — uses non-existent provider 99999)
    try {
      const result = await runStampCreateWarning(context, csrfToken);
      scenarios.push({ name: 'stamp-create-warning', skipped: false, ...result });
    } catch (err) {
      findings.push({ label: 'stamp-create-warning', type: 'scenario-error', text: err.message });
      scenarios.push({ name: 'stamp-create-warning', skipped: false, error: err.message });
    }

    // Scenario 3: stamp-update (requires CONSULT_UNSIGNED_REQUEST_ID and stamp)
    if (stampAvailable && unsignedRequestId) {
      try {
        const result = await runStampUpdate(context, csrfToken);
        scenarios.push({ name: 'stamp-update', skipped: false, ...result });
      } catch (err) {
        findings.push({ label: 'stamp-update', type: 'scenario-error', text: err.message });
        scenarios.push({ name: 'stamp-update', skipped: false, error: err.message });
      }
    } else {
      const reason = !stampAvailable
        ? `No stamp file for provider ${stampProviderNo}`
        : 'CONSULT_UNSIGNED_REQUEST_ID not set';
      scenarios.push({ name: 'stamp-update', skipped: true, reason });
    }

    // Scenario 4: stamp-print-preview (requires CONSULT_UNSIGNED_REQUEST_ID and stamp)
    if (stampAvailable && unsignedRequestId) {
      try {
        const result = await runStampPrintPreview(context);
        scenarios.push({ name: 'stamp-print-preview', skipped: false, ...result });
      } catch (err) {
        findings.push({ label: 'stamp-print-preview', type: 'scenario-error', text: err.message });
        scenarios.push({ name: 'stamp-print-preview', skipped: false, error: err.message });
      }
    } else {
      const reason = !stampAvailable
        ? `No stamp file for provider ${stampProviderNo}`
        : 'CONSULT_UNSIGNED_REQUEST_ID not set';
      scenarios.push({ name: 'stamp-print-preview', skipped: true, reason });
    }

    console.log(JSON.stringify({ visited, scenarios, findings }, null, 2));

    const blockingFindings = findings.filter((f) => f.type !== 'dialog');
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
