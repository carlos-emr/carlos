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
 * Default: create an owned patient, login and consultation; check the unsigned
 * pad, a provider stamp, and a saved signature after the stamp is removed.
 * Requires the usual BASE_URL, TEST_*, MYSQL_* and CHROME_PATH settings.
 * CONSULT_APPLICATION_TEMP_DIR optionally enables owned preview-file cleanup
 * when running on the application host (the server's carlos-temp directory).
 *
 * Existing requests remain supported without database access:
 *   CONSULT_REQUEST_ID=123 [CONSULT_DEMOGRAPHIC_NO=1]
 *   CONSULT_SIGNATURE_SCENARIOS='[{"name":"unsigned","requestId":"123","expectSignature":false}]'
 * Explicit scenarios expect a signature unless expectSignature is boolean false.
 */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {throwawayLoginFixture} = require('./lib/throwaway-login-fixture');
const {createConsultationSubmitFixture} = require('./lib/consultation-submit-fixture');
const {stampPng} = require('./provider-signature-contact-playwright-checks');

const STAMP_WIDTH = 173;
const STAMP_HEIGHT = 53;

function parseConsultSignatureScenarios(env = process.env) {
  const raw = (env.CONSULT_SIGNATURE_SCENARIOS || '').trim();
  let scenarios;
  if (raw) {
    try { scenarios = JSON.parse(raw); }
    catch (error) { throw new Error(`CONSULT_SIGNATURE_SCENARIOS must be valid JSON: ${error.message}`); }
    h.assert(Array.isArray(scenarios) && scenarios.length > 0,
      'CONSULT_SIGNATURE_SCENARIOS must be a non-empty JSON array');
  } else {
    if (!env.CONSULT_REQUEST_ID && !env.CONSULT_DEMOGRAPHIC_NO) return [];
    scenarios = [{name: 'default', requestId: env.CONSULT_REQUEST_ID,
      demographicNo: env.CONSULT_DEMOGRAPHIC_NO}];
  }
  return scenarios.map((scenario, index) => {
    const prefix = `CONSULT_SIGNATURE_SCENARIOS[${index}]`;
    h.assert(scenario && typeof scenario === 'object' && !Array.isArray(scenario), `${prefix} must be an object`);
    const requestId = String(scenario.requestId ?? scenario.consultRequestId ?? '').trim();
    const demographicNo = String(scenario.demographicNo ?? '').trim();
    const name = String(scenario.name ?? `scenario-${index + 1}`).trim();
    h.assert(/^[1-9]\d*$/.test(requestId), `${prefix}.requestId must be a positive numeric request id`);
    h.assert(!demographicNo || /^[1-9]\d*$/.test(demographicNo), `${prefix}.demographicNo must be a positive numeric id`);
    h.assert(name, `${prefix}.name must not be blank`);
    h.assert(scenario.expectSignature === undefined || typeof scenario.expectSignature === 'boolean',
      `${prefix}.expectSignature must be boolean true or false`);
    return {name, requestId, demographicNo, expectSignature: scenario.expectSignature !== false};
  });
}

// Runs in the browser, including while polling. Visibility includes the parent
// signatureShow container; checking only the image's own style misses a hidden pad.
function signatureStateInPage() {
  const value = id => document.getElementById(id)?.value || '';
  const img = document.getElementById('signatureImgTag');
  const visible = element => Boolean(element && element.getClientRects().length
    && window.getComputedStyle(element).visibility !== 'hidden');
  return {
    signatureImg: value('signatureImg'),
    newSignature: value('newSignature'),
    signatureProviderNo: value('signatureProviderNo'),
    imageSrc: img?.getAttribute('src') || '',
    imageWidth: img?.naturalWidth || 0,
    imageHeight: img?.naturalHeight || 0,
    imageLoaded: Boolean(img?.complete && img.naturalWidth > 0 && img.naturalHeight > 0 && visible(img)),
    padVisible: visible(document.getElementById('signatureFrame')),
  };
}

async function readSignatureState(page, scenario) {
  // The second waitForFunction parameter is the browser argument, not options.
  if (scenario.expectSignature) {
    try {
      await page.waitForFunction(() => {
        const img = document.getElementById('signatureImgTag');
        return img && img.complete && img.naturalWidth > 0 && img.naturalHeight > 0
          && img.getClientRects().length > 0 && window.getComputedStyle(img).visibility !== 'hidden';
      }, null, {timeout: 15000});
    } catch (error) {
      const state = await page.evaluate(signatureStateInPage);
      throw new Error(`${scenario.name}: expected a loaded signature image; request ${scenario.requestId} `
        + `has stored signature=${state.signatureImg || 'none'}, provider=${state.signatureProviderNo || 'none'}, `
        + `manual pad=${state.padVisible}. Seed a provider stamp/stored signature, or explicitly use `
        + 'expectSignature:false for an unsigned request.', {cause: error});
    }
  }
  const state = await page.evaluate(signatureStateInPage);
  if (scenario.expectSignature) {
    h.assert(state.imageLoaded && !state.padVisible && state.newSignature === 'false',
      `${scenario.name}: signed request did not display its signature instead of the manual pad`);
  } else {
    h.assert(!state.imageLoaded && state.padVisible && state.newSignature === 'true' && !state.signatureImg,
      `${scenario.name}: unsigned request did not show an empty manual signature pad`);
  }
  return state;
}

function describePdf(bytes) {
  // OpenPDF emits image objects outside compressed content streams. Inspect each
  // object's header (including nested DecodeParms), never the binary image data.
  const images = [...bytes.toString('latin1').matchAll(/\b\d+\s+\d+\s+obj\b([\s\S]*?)\bendobj\b/g)]
    .map(([, object]) => object.split(/\bstream\r?\n/, 1)[0])
    .filter(header => /\/Subtype\s*\/Image\b/.test(header))
    .map(header => ({width: Number(/\/Width\s+(\d+)/.exec(header)?.[1]),
      height: Number(/\/Height\s+(\d+)/.exec(header)?.[1])}));
  return {byteLength: bytes.length, images};
}

function assertSignaturePdf(pdf, signatureState, name) {
  h.assert(signatureState.imageWidth > 0 && signatureState.imageHeight > 0
    && pdf.images.some(image => image.width === signatureState.imageWidth && image.height === signatureState.imageHeight),
  `${name}: print preview PDF does not embed an image with the displayed signature's dimensions`);
}

async function requestPrintPreview(page, fixture) {
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
  if (fixture) fixture.capturePdf(pdfBytes);
  return describePdf(pdfBytes);
}

async function runScenario(context, config, scenario, fixture) {
  const page = await context.newPage();
  try {
    const params = new URLSearchParams({requestId: scenario.requestId});
    if (scenario.demographicNo) params.set('de', scenario.demographicNo);
    await h.gotoApp(page, config.baseUrl, `/encounter/ViewRequest?${params}`);
    await page.locator('#EctConsultationFormRequest2Form').waitFor({state: 'attached'});
    await page.locator('#signatureImgTag').waitFor({state: 'attached'});
    await page.waitForLoadState('networkidle');
    const signatureState = await readSignatureState(page, scenario);
    if (scenario.source) {
      h.assert(signatureState.imageSrc.includes(scenario.source), `${scenario.name}: wrong signature image source`);
      h.assert(signatureState.imageWidth === STAMP_WIDTH && signatureState.imageHeight === STAMP_HEIGHT,
        `${scenario.name}: the displayed image is not the owned signature`);
    }
    const pdf = await requestPrintPreview(page, fixture);
    if (scenario.expectSignature) {
      assertSignaturePdf(pdf, signatureState, scenario.name);
    } else if (fixture) {
      h.assert(!pdf.images.some(image => image.width === STAMP_WIDTH && image.height === STAMP_HEIGHT),
        `${scenario.name}: unsigned preview unexpectedly contains the owned stamp`);
    }
    console.log(JSON.stringify({scenario: scenario.name, signatureState, pdf}));
    return signatureState;
  } finally { await page.close(); }
}

async function workflow(s) {
  const {config, sql, patient, recorder} = s;
  const account = throwawayLoginFixture({sql, marker: s.marker, provider: s.provider, testUser: config.testUser});
  let stampClean = true;
  let consultationsClean = true;
  s.cleanup(() => {
    h.assert(stampClean && consultationsClean, 'Retaining owned login for failed signature fixture cleanup');
    account.cleanup();
  });
  account.create();
  const context = await h.newContext(s.context.browser(), config);
  context.on('page', page => h.wireStrictPage(page, 'consultation-signature', recorder));
  await h.login(context, {...config, testUser: account.username}, recorder);
  const page = await context.newPage();
  await h.gotoApp(page, config.baseUrl, `/encounter/oscarConsultationRequest/ViewConsultationFormRequest?de=${patient}`);
  const csrf = await page.locator('input[name="CSRF-TOKEN"]').inputValue();
  const {request} = require('playwright');
  // Workflow cleanup closes the browser first. A standalone API context keeps
  // the same owned session available to delete its stamp afterwards, even on failure.
  const api = await request.newContext({storageState: await context.storageState(), ignoreHTTPSErrors: config.ignoreHTTPSErrors});
  const stampUrl = h.appUrl(config.baseUrl, `/provider/providerSignatureImage?providerNo=${account.providerNo}`);
  async function stampStatus() {
    const response = await api.get(stampUrl);
    try { return response.status(); } finally { await response.dispose(); }
  }
  async function stampAction(method, extra = {}) {
    const response = await api.post(h.appUrl(config.baseUrl, '/provider/providerSignatureStamp'), {
      headers: {'CSRF-TOKEN': csrf}, form: {method, 'CSRF-TOKEN': csrf, ...extra},
    });
    try {
      h.assert(response.status() === 200 && (await response.json()).success === true, `Stamp ${method} failed`);
    } finally { await response.dispose(); }
  }
  async function deleteStamp() {
    await stampAction('delete');
    h.assert(await stampStatus() === 204, 'The owned provider stamp was not removed');
    stampClean = true;
  }
  s.cleanup(async () => {
    try { if (!stampClean) await deleteStamp(); }
    finally { await api.dispose(); }
  });
  const fixture = createConsultationSubmitFixture(sql, patient, process.env.CONSULT_APPLICATION_TEMP_DIR);
  s.cleanup(() => { fixture.cleanup(); consultationsClean = true; });
  const service = sql.value('SELECT MIN(serviceId) FROM consultationServices');
  h.assert(/^[1-9]\d*$/.test(service), 'At least one consultation service is required');
  const reason = fixture.reason('signature');
  const form = {demographicNo: patient, service, specialist: '0', sendTo: '', providerNo: account.providerNo,
    newSignature: 'true', signatureImg: '', signatureProviderNo: account.providerNo, newSignatureImg: '',
    reasonForConsultation: reason, 'CSRF-TOKEN': csrf};
  async function submit(extra) {
    consultationsClean = false; // Register write intent before dispatch, including a lost HTTP response.
    const response = await context.request.post(h.appUrl(config.baseUrl, '/encounter/RequestConsultation'), {
      headers: {'CSRF-TOKEN': csrf}, form: {...form, ...extra},
    });
    try {
      h.assert(response.status() === 200 && !response.url().includes('signatureNotApplied=1'), 'Consultation setup failed');
    } finally { await response.dispose(); }
  }
  let requestId;
  await s.step('unsigned request shows the manual pad and generates a preview', async () => {
    h.assert(await stampStatus() === 204, 'Owned provider unexpectedly has an existing stamp; refusing to overwrite it');
    await submit({submission: 'Submit Consultation Request'});
    requestId = fixture.requestId('signature');
    await runScenario(context, config, {name: 'unsigned', requestId, demographicNo: patient, expectSignature: false}, fixture);
  });
  await s.step('provider stamp loads on the request and is embedded in the preview', async () => {
    stampClean = false;
    await stampAction('saveDrawn', {signatureData: `data:image/png;base64,${stampPng(STAMP_WIDTH, STAMP_HEIGHT).toString('base64')}`});
    h.assert(await stampStatus() === 200, 'The uploaded provider stamp is unavailable');
    const state = await runScenario(context, config, {name: 'provider-stamp', requestId, demographicNo: patient,
      expectSignature: true, source: 'providerSignatureImage'}, fixture);
    h.assert(!state.signatureImg, 'Provider-stamp scenario unexpectedly used a stored signature');
  });
  await s.step('saved signature survives removal of the provider stamp in the request and PDF', async () => {
    await submit({submission: 'Update Consultation Request', requestId, newSignature: 'false'});
    const signatureId = sql.value(`SELECT signature_img FROM consultationRequests WHERE requestId=${requestId} AND demographicNo=${patient}`);
    h.assert(/^[1-9]\d*$/.test(signatureId), 'The application did not persist a DigitalSignature');
    h.assert(sql.value(`SELECT COUNT(*) FROM DigitalSignature WHERE id=${signatureId} AND demographicId=${patient}
      AND providerNo=${h.sqlString(account.providerNo)}`) === '1', 'The stored signature has the wrong owner');
    await deleteStamp();
    const state = await runScenario(context, config, {name: 'stored-signature', requestId, demographicNo: patient,
      expectSignature: true, source: 'source=signature_stored'}, fixture);
    h.assert(state.signatureImg === signatureId, 'The request did not use its saved DigitalSignature');
  });
}

async function main() {
  const scenarios = parseConsultSignatureScenarios();
  if (!scenarios.length) return runWorkflow('consultation-signature', workflow, {openMaster: false});
  const config = h.readConfig();
  const recorder = h.createRecorder();
  let browser;
  return h.runCheck({name: 'consultation-signature', async run() {
    browser = await h.launchBrowser(config);
    const context = await h.newContext(browser, config);
    context.on('page', page => h.wireStrictPage(page, 'consultation-signature', recorder));
    await h.login(context, config, recorder);
    for (const scenario of scenarios) await runScenario(context, config, scenario);
    h.assertStrictPage(recorder);
  }, async cleanup() { if (browser) await browser.close(); }});
}

if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1; });
module.exports = {parseConsultSignatureScenarios, readSignatureState, describePdf, assertSignaturePdf, workflow};
