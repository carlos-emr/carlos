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
 * Browser regression checks for consultation request signatures.
 *
 * The script opens one or more existing consultation requests, verifies that
 * the signature image shown on the request page is a loaded image, then posts
 * the same form to the direct print-preview endpoint and verifies a PDF is
 * returned with an embedded image when the scenario expects a signature.
 *
 * Required single fixture:
 *   CONSULT_REQUEST_ID=123 node scripts/consultation-signature-playwright-checks.js
 *
 * Optional multi-scenario fixture:
 *   CONSULT_SIGNATURE_SCENARIOS='[{"name":"stored","requestId":"123","demographicNo":"1","expectSignature":true}]'
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CONSULT_DEMOGRAPHIC_NO=1
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
const consultRequestId = process.env.CONSULT_REQUEST_ID || '';
const consultDemographicNo = process.env.CONSULT_DEMOGRAPHIC_NO || '';
const scenarios = parseConsultSignatureScenarios();

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

function parseConsultSignatureScenarios() {
  const rawScenarios = process.env.CONSULT_SIGNATURE_SCENARIOS || '';
  if (!rawScenarios.trim()) {
    if (!/^\d+$/.test(consultRequestId)) {
      throw new Error('CONSULT_REQUEST_ID must be set to an existing numeric consultation request id');
    }
    if (consultDemographicNo && !/^\d+$/.test(consultDemographicNo)) {
      throw new Error('CONSULT_DEMOGRAPHIC_NO must be numeric when provided');
    }
    return [normalizeScenario({
      name: 'default',
      requestId: consultRequestId,
      demographicNo: consultDemographicNo,
      expectSignature: true,
    }, 0)];
  }

  let parsed;
  try {
    parsed = JSON.parse(rawScenarios);
  } catch (error) {
    throw new Error(`CONSULT_SIGNATURE_SCENARIOS must be valid JSON: ${error.message}`);
  }
  if (!Array.isArray(parsed) || parsed.length === 0) {
    throw new Error('CONSULT_SIGNATURE_SCENARIOS must be a non-empty JSON array');
  }
  return parsed.map((scenario, index) => normalizeScenario(scenario, index));
}

function normalizeScenario(scenario, index) {
  if (!scenario || typeof scenario !== 'object' || Array.isArray(scenario)) {
    throw new Error(`CONSULT_SIGNATURE_SCENARIOS[${index}] must be an object`);
  }

  const requestId = String(scenario.requestId || scenario.consultRequestId || '').trim();
  const demographicNo = scenario.demographicNo == null ? '' : String(scenario.demographicNo).trim();
  const name = String(scenario.name || `scenario-${index + 1}`).trim();
  const expectSignature = scenario.expectSignature !== false;

  if (!/^\d+$/.test(requestId)) {
    throw new Error(`CONSULT_SIGNATURE_SCENARIOS[${index}].requestId must be numeric`);
  }
  if (demographicNo && !/^\d+$/.test(demographicNo)) {
    throw new Error(`CONSULT_SIGNATURE_SCENARIOS[${index}].demographicNo must be numeric when provided`);
  }
  if (!name) {
    throw new Error(`CONSULT_SIGNATURE_SCENARIOS[${index}].name must not be blank`);
  }

  return { name, requestId, demographicNo, expectSignature };
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

async function openConsultationRequest(page, scenario) {
  const params = new URLSearchParams({ requestId: scenario.requestId });
  if (scenario.demographicNo) {
    params.set('de', scenario.demographicNo);
  }
  await gotoApp(page, `/encounter/ViewRequest?${params.toString()}`);
  await page.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: 30000 });
  await page.locator('#signatureImgTag').waitFor({ state: 'attached', timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: `${scenario.name}:consultation-request`, url: page.url() });
  await assertNoErrorPage(page, `${scenario.name}:consultation-request`);
}

async function readSignatureState(page, scenario) {
  if (scenario.expectSignature) {
    await page.waitForFunction(() => {
      const img = document.getElementById('signatureImgTag');
      if (!img) return false;
      const style = window.getComputedStyle(img);
      return img.complete && img.naturalWidth > 0 && img.naturalHeight > 0
        && style.display !== 'none' && style.visibility !== 'hidden';
    }, { timeout: 15000 });
  }

  return page.evaluate(() => {
    const valueOf = (id) => {
      const el = document.getElementById(id);
      return el ? el.value || '' : '';
    };
    const img = document.getElementById('signatureImgTag');
    const style = img ? window.getComputedStyle(img) : null;
    return {
      signatureImg: valueOf('signatureImg'),
      newSignature: valueOf('newSignature'),
      signatureProviderNo: valueOf('signatureProviderNo'),
      imageSrc: img ? img.src : '',
      imageWidth: img ? img.naturalWidth : 0,
      imageHeight: img ? img.naturalHeight : 0,
      imageDisplay: style ? style.display : '',
      imageVisibility: style ? style.visibility : '',
      imageComplete: img ? img.complete : false,
    };
  });
}

async function requestPrintPreview(page) {
  const preview = await page.evaluate(async () => {
    const form = document.getElementById('EctConsultationFormRequest2Form');
    if (!form) {
      throw new Error('Consultation form not found');
    }

    const submission = form.elements.submission;
    const previousSubmission = submission ? submission.value : '';
    if (submission) {
      submission.value = 'And Print Preview';
    }
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
          'Accept': 'application/json',
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
      if (submission) {
        submission.value = previousSubmission;
      }
    }
  });

  if (preview.status !== 200) {
    throw new Error(`Print preview returned HTTP ${preview.status}: ${preview.text.slice(0, 300)}`);
  }

  let payload;
  try {
    payload = JSON.parse(preview.text);
  } catch (error) {
    throw new Error(`Print preview did not return JSON (${preview.contentType}): ${preview.text.slice(0, 300)}`);
  }

  if (payload.errorMessage) {
    throw new Error(`Print preview returned errorMessage: ${payload.errorMessage}`);
  }
  if (!payload.consultPDF) {
    throw new Error('Print preview response did not include consultPDF');
  }

  const pdfBytes = Buffer.from(payload.consultPDF, 'base64');
  if (pdfBytes.length < 100 || pdfBytes.slice(0, 4).toString('ascii') !== '%PDF') {
    throw new Error('Print preview response was not a valid PDF');
  }
  return { byteLength: pdfBytes.length, containsImage: /\/Subtype\s*\/Image/.test(pdfBytes.toString('latin1')) };
}

async function runScenario(context, scenario) {
  const page = await context.newPage();
  wirePage(page, `consultation-signature:${scenario.name}`);
  try {
    await openConsultationRequest(page, scenario);

    const signatureState = await readSignatureState(page, scenario);
    const pdf = await requestPrintPreview(page);
    if (scenario.expectSignature && !pdf.containsImage) {
      throw new Error(`${scenario.name}: print preview PDF did not contain an embedded image`);
    }

    return { scenario, signatureState, pdf };
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

    const results = [];
    for (const scenario of scenarios) {
      results.push(await runScenario(context, scenario));
    }

    console.log(JSON.stringify({ visited, scenarios: results, findings }, null, 2));
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
