#!/usr/bin/env node
/*
 * Browser regression check for consultation request signatures.
 *
 * The script opens an existing consultation request, verifies that the signature
 * image shown on the request page is a loaded image, then posts the same form to
 * the direct print-preview endpoint and verifies a PDF is returned.
 *
 * Required fixture:
 *   CONSULT_REQUEST_ID=123 node scripts/consultation-signature-playwright-checks.js
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

if (!/^\d+$/.test(consultRequestId)) {
  throw new Error('CONSULT_REQUEST_ID must be set to an existing numeric consultation request id');
}
if (consultDemographicNo && !/^\d+$/.test(consultDemographicNo)) {
  throw new Error('CONSULT_DEMOGRAPHIC_NO must be numeric when provided');
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

async function openConsultationRequest(page) {
  const params = new URLSearchParams({ requestId: consultRequestId });
  if (consultDemographicNo) {
    params.set('de', consultDemographicNo);
  }
  await gotoApp(page, `/encounter/ViewRequest?${params.toString()}`);
  await page.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: 30000 });
  await page.locator('#signatureImgTag').waitFor({ state: 'attached', timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'consultation-request', url: page.url() });
  await assertNoErrorPage(page, 'consultation-request');
}

async function readSignatureState(page) {
  await page.waitForFunction(() => {
    const img = document.getElementById('signatureImgTag');
    if (!img) return false;
    const style = window.getComputedStyle(img);
    return img.complete && img.naturalWidth > 0 && img.naturalHeight > 0
      && style.display !== 'none' && style.visibility !== 'hidden';
  }, { timeout: 15000 });

  return page.evaluate(() => {
    const valueOf = (id) => {
      const el = document.getElementById(id);
      return el ? el.value || '' : '';
    };
    const img = document.getElementById('signatureImgTag');
    return {
      signatureImg: valueOf('signatureImg'),
      newSignature: valueOf('newSignature'),
      signatureProviderNo: valueOf('signatureProviderNo'),
      imageSrc: img ? img.src : '',
      imageWidth: img ? img.naturalWidth : 0,
      imageHeight: img ? img.naturalHeight : 0,
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
    const page = await login(context);
    wirePage(page, 'consultation-signature');
    await openConsultationRequest(page);

    const signatureState = await readSignatureState(page);
    const pdf = await requestPrintPreview(page);
    const storedSignature = /^[0-9]{1,9}$/.test(signatureState.signatureImg);
    if (!storedSignature && !pdf.containsImage) {
      throw new Error('Stamp-mode print preview PDF did not contain an embedded image');
    }

    console.log(JSON.stringify({ visited, signatureState, pdf, findings }, null, 2));
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
