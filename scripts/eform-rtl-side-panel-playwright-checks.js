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

/*
 * Browser regression check for the Rich Text Letter's side panel, addressee search, signature
 * stamp and signature pad, driven the way a clinician uses them, against a running CARLOS.
 *
 * WHY. An alpha tester reported "Signature box on rich text editor 404 error with multiple broken
 * links when clicking options to pull in data from the side". Every defect behind that shipped
 * green past the other Rich Text Letter checks, because none of them clicks a side-panel button,
 * searches an addressee or signs a letter:
 *   - the SQL safety gate refused every AP query with a "\n" literal, so Allergies, Prescriptions
 *     and the history buttons answered "The requested value has no content" for a chart with data;
 *   - the signature pad stored its drawing against patient -1 (HTTP 500) and named no module, so a
 *     saved signature could not be shown either;
 *   - the addressee search called an unmapped ".json" route (404) behind an administrator object;
 *   - a signer with no signature on file got a broken stamp image, and the check for one answered
 *     404 on every letter opened.
 *
 * STEPS (each also holds the strict browser contract: no uncaught error, no severe console
 * message, no failed request, no unexpected dialog; the one expected 404 is named below):
 *   1. the letter opens for an owned patient, with the signature pad bound to that patient and the
 *      stamp check answered without a 404;
 *   2. every side-panel button puts the owned patient's seeded data in the letter;
 *   3. the addressee search finds an owned specialist, and Paste Selected inserts its address;
 *   4. with no signature on file, Stamp and Closing Salutation leave no broken image and say why
 *      (the shared stamp.png fallback answers 404 on an install without one: that request is the
 *      only one taken out of the strict record, and only on this letter);
 *   5. every shipped template loads without a prompt and without an unfilled placeholder;
 *   6. once the signer uploads a signature stamp, a template's closing salutation, Stamp and the
 *      signature pad all put images in the letter that load, the pad's drawing stored for the
 *      letter's patient under the eForm module;
 *   7. the saved letter reopens with both images, and its PDF download carries both.
 *
 * FIXTURES, all owned and all removed by key (asserted gone):
 *   - a FAKE-PW patient (lib/owned-patient.js) with one allergy, prescription, prevention, weight
 *     and chart note per history section, and a referring doctor;
 *   - a throwaway login (lib/throwaway-login-fixture.js) who is the patient's MRP, so the letter
 *     signs as them and the shared test account's own stamp is never touched;
 *   - that login's signature stamp, uploaded and deleted through the Preferences stamp endpoint;
 *   - a FAKE specialist whose referral number the patient's family doctor field names.
 * Every pad save also leaves the application's own preview image in the server's temp directory;
 * that file is not the check's to remove.
 *
 * Needs the eForm PDF renderer for step 7 (docs/eform-browser-pdf-renderer.md) and pdfimages
 * (poppler-utils) to read the downloaded PDF.
 *
 * Environment: BASE_URL, TEST_USER/TEST_PASSWORD/TEST_PIN, MYSQL_*, CHROME_PATH (optional),
 * RTL_FORM_NAME (default "Rich Text Letter"), SCREENSHOT_DIR (optional).
 */
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { request } = require('playwright');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const {
  ALLERGY_ROWS, combineRows, createOwnedPatient, eformRows, removeOwnedPatient,
} = require('./lib/owned-patient');
const { stampPng } = require('./provider-signature-contact-playwright-checks');

const NAME = 'eform-rtl-side-panel';
const TIMEOUT = 20000;
const FORM_NAME = process.env.RTL_FORM_NAME || 'Rich Text Letter';
// The stamp this check uploads, and the drawing surface of the signature pad (TabletSignature.js).
const STAMP_WIDTH = 173;
const STAMP_HEIGHT = 53;
const PAD_WIDTH = 500;
const PAD_HEIGHT = 100;
// The templates CARLOS ships (EFormAssetDeployer). A clinic's own templates are not this check's to
// judge: one may legitimately ask for a value.
const SHIPPED_TEMPLATES = ['blank.rtl', 'clinic_letter.rtl', 'consultation_letter.rtl', 'patient_letter.rtl'];
// The start of MISSING_STAMP_NOTICE in editControl2.js.
const MISSING_STAMP_NOTICE = 'No signature stamp could be loaded for the signing provider';

/** The chart a clinician would pull from the side panel: one entry per button, each naming the run. */
function chartText(marker) {
  return {
    allergy: `${marker} allergy`,
    drug: `${marker} tablet`,
    social: `${marker} social history`,
    medical: `${marker} medical history`,
    concerns: `${marker} ongoing concern`,
    reminders: `${marker} reminder`,
    otherMeds: `${marker} other medication`,
    riskFactors: `${marker} risk factor`,
    family: `${marker} family history`,
  };
}

/** [issue code, chartText key] of each history section the side panel reads. */
const CHART_NOTES = [
  ['SocHistory', 'social'], ['MedHistory', 'medical'], ['Concerns', 'concerns'], ['Reminders', 'reminders'],
  ['OMeds', 'otherMeds'], ['RiskFactors', 'riskFactors'], ['FamHistory', 'family'],
];

/** What seeding and the letter write for the patient beyond the chart's own rows. */
const LETTER_ROWS = Object.freeze({
  extra: [['drugs', 'demographic_no'], ['preventions', 'demographic_no'], ['measurements', 'demographicNo'],
    ['DigitalSignature', 'demographicId']],
  via: [],
});

/**
 * The owned patient, with runWorkflow()'s lifecycle: the rows its chart and letters gain are
 * removed by the patient's key. The eForm attachment mark is taken before anything is written.
 */
function letterPatientFixture({ sql, marker, provider }) {
  let patient = null;
  let rows = null;
  return {
    async create() {
      rows = combineRows(eformRows(sql), ALLERGY_ROWS, LETTER_ROWS);
      patient = createOwnedPatient(sql, { marker, provider, firstName: 'Letter' });
      return patient;
    },
    async verifyOwner() {
      h.assert(patient && sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
        AND last_name=${h.sqlString(marker)}`) === '1', 'Patient fixture ownership changed');
    },
    async cleanup() {
      if (patient) removeOwnedPatient(sql, patient, marker, rows);
    },
  };
}

/** A FAKE specialist with an unused six-digit referral number; removed by its id and name. */
function createOwnedSpecialist(sql, marker) {
  let referralNo = '';
  for (let attempt = 0; attempt < 20 && !referralNo; attempt++) {
    const candidate = String(900000 + Math.floor(Math.random() * 99999));
    if (sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE referralNo=${h.sqlString(candidate)}`) === '0') {
      referralNo = candidate;
    }
  }
  h.assert(referralNo, 'No unused referral number was found for the specialist fixture');
  const specialist = {
    lName: marker, fName: 'Addressee', address: `${marker} Referral Road`, phone: '416-555-0142', fax: '416-555-0143', referralNo,
  };
  specialist.id = sql.value(`INSERT INTO professionalSpecialists (fName, lName, address, phone, fax, referralNo,
      lastUpdated, institutionId, departmentId, hideFromView, deleted)
    VALUES (${h.sqlString(specialist.fName)}, ${h.sqlString(marker)}, ${h.sqlString(specialist.address)},
      ${h.sqlString(specialist.phone)}, ${h.sqlString(specialist.fax)}, ${h.sqlString(referralNo)}, NOW(), 0, 0, 0, 0);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(specialist.id), 'The specialist fixture was not created');
  return specialist;
}

function removeOwnedSpecialist(sql, specialist, marker) {
  sql.execute(`DELETE FROM professionalSpecialists WHERE specId=${specialist.id} AND lName=${h.sqlString(marker)}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE specId=${specialist.id}`) === '0',
    'The specialist fixture was not removed');
}

/** The data the side panel reads, for the owned patient, with the throwaway login as the MRP. */
function seedChart(sql, { patient, marker, mrp, specialist, text }) {
  const owner = h.sqlString(mrp);
  const familyDoctor = `<rdohip>${specialist.referralNo}</rdohip><rd>${specialist.lName},${specialist.fName}</rd>`;
  sql.execute(`UPDATE demographic SET provider_no=${owner}, family_doctor=${h.sqlString(familyDoctor)}
    WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  sql.execute(`INSERT INTO allergies (demographic_no, entry_date, DESCRIPTION, TYPECODE, archived, position, lastUpdateDate, providerNo)
    VALUES (${patient}, CURDATE(), ${h.sqlString(text.allergy)}, 0, 0, 0, NOW(), ${owner})`);
  sql.execute(`INSERT INTO drugs (provider_no, demographic_no, rx_date, end_date, written_date, BN, GCN_SEQNO, archived,
      position, lastUpdateDate, dispenseInternal, create_date)
    VALUES (${owner}, ${patient}, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), CURDATE(), ${h.sqlString(text.drug)}, 0, 0,
      0, NOW(), 0, NOW())`);
  sql.execute(`INSERT INTO preventions (demographic_no, creation_date, prevention_date, provider_no, prevention_type,
      deleted, refused, never, lastUpdateDate)
    VALUES (${patient}, NOW(), '2026-01-15 09:00:00', ${owner}, 'Td', '0', '0', '0', NOW())`);
  sql.execute(`INSERT INTO measurements (type, demographicNo, providerNo, dataField, measuringInstruction, dateObserved, dateEntered)
    VALUES ('WT', ${patient}, ${owner}, '71.3', 'in kg', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW())`);
  for (const [code, key] of CHART_NOTES) {
    const issue = sql.value(`SELECT issue_id FROM issue WHERE code=${h.sqlString(code)}`);
    h.assert(/^[1-9]\d*$/.test(issue), `The chart has no ${code} issue`);
    const caseIssue = sql.value(`INSERT INTO casemgmt_issue (demographic_no, issue_id, type, update_date)
      VALUES (${patient}, ${issue}, 'system', NOW()); SELECT LAST_INSERT_ID()`);
    const note = sql.value(`INSERT INTO casemgmt_note (update_date, observation_date, demographic_no, provider_no, note,
        signed, signing_provider_no, history, archived, position, uuid)
      VALUES (NOW(), NOW(), ${patient}, ${owner}, ${h.sqlString(text[key])}, 1, ${owner}, ${h.sqlString(text[key])}, 0, 0, UUID());
      SELECT LAST_INSERT_ID()`);
    sql.execute(`INSERT INTO casemgmt_issue_notes (id, note_id) VALUES (${caseIssue}, ${note})`);
  }
}

// ---------------------------------------------------------------------------------------------
// The letter
// ---------------------------------------------------------------------------------------------

async function editorFrame(page) {
  const handle = await page.locator('iframe#edit').elementHandle({ timeout: TIMEOUT });
  const frame = handle && await handle.contentFrame();
  h.assert(frame, 'The letter has no editor frame');
  return frame;
}

/** The letter as the clinician reads it. Contenteditable may render typed spaces as non-breaking. */
async function editorText(page) {
  const frame = await editorFrame(page);
  return frame.evaluate(() => (document.body ? document.body.innerText : '').replace(/ /g, ' '));
}

/** Every image in the letter, as loaded. */
async function letterImages(page) {
  const frame = await editorFrame(page);
  return frame.evaluate(() => Array.from(document.images).map((image) => ({
    src: image.getAttribute('src') || '',
    complete: image.complete,
    width: image.naturalWidth,
    height: image.naturalHeight,
  })));
}

/** Waits until the editor, its template catalog and any measurement or template lookup are idle. */
async function waitForLetterIdle(page) {
  await page.waitForFunction(() => {
    const select = document.getElementById('template');
    const frame = document.getElementById('edit');
    let ready = false;
    try {
      ready = !!frame && frame.contentDocument.readyState === 'complete' && !!frame.contentDocument.body;
    } catch (error) {
      ready = false;
    }
    return ready && !!select && !Array.from(select.options).some((option) => option.textContent.trim() === 'loading...')
      && typeof measurementHistoryStillLoading === 'function' && !measurementHistoryStillLoading();
  }, null, { timeout: 30000 });
}

/**
 * Opens a new letter for the patient and waits for it to settle, including the signer's stamp
 * check (which Start() fires once the server has filled in the identity inputs).
 */
async function openLetter(context, config, recorder, { fid, patient, label }) {
  const page = await context.newPage();
  h.wireStrictPage(page, label, recorder);
  // A save reloads onto a page that closes itself; keep it to read the result.
  await page.addInitScript(() => { window.close = () => { window.__closeIntercepted = true; }; });
  const stampCheck = page.waitForResponse((response) => response.url().includes('/provider/providerSignatureImage'),
    { timeout: 30000 });
  await h.gotoApp(page, config.baseUrl, `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(patient)}`);
  await h.assertNotErrorPage(page, label);
  await waitForLetterIdle(page);
  const answer = await stampCheck;
  return { page, stampCheckStatus: answer.status() };
}

/** Puts the caret at the end of the letter, so every insertion appends. */
async function caretToEnd(page) {
  const frame = await editorFrame(page);
  await frame.evaluate(() => {
    const range = document.createRange();
    range.selectNodeContents(document.body);
    range.collapse(false);
    const selection = document.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
  });
}

function lowerIncludes(text, fragment) {
  return text.toLowerCase().includes(String(fragment).toLowerCase());
}

/**
 * Waits for every fragment to be in the letter. A dialog raised meanwhile (the "has no content"
 * alert, a template prompt) fails at once with its text, rather than after the timeout.
 */
async function waitForLetterText(page, recorder, label, fragments, { dialogMark, timeout = TIMEOUT } = {}) {
  const mark = dialogMark === undefined ? recorder.dialogs.length : dialogMark;
  const deadline = Date.now() + timeout;
  let missing = fragments;
  while (Date.now() < deadline) {
    const raised = recorder.dialogs.slice(mark);
    h.assert(raised.length === 0,
      `${label}: the letter raised ${raised.map((dialog) => `${dialog.type} "${dialog.text}"`).join(', ')} instead of filling it in`);
    const text = await editorText(page);
    missing = fragments.filter((fragment) => (fragment instanceof RegExp ? !fragment.test(text) : !lowerIncludes(text, fragment)));
    if (!missing.length) return text;
    await page.waitForTimeout(150);
  }
  // Names only the run's own synthetic fragments, never the letter's text.
  throw new Error(`${label}: the letter never showed ${missing.map(String).join(' | ')}`);
}

/** The "Some fields could not be filled in automatically" banner, if APCache raised one. */
async function lookupFailureNotice(page) {
  return page.evaluate(() => {
    const notice = document.getElementById('carlos-apcache-lookup-failure');
    return notice && notice.offsetParent !== null ? notice.textContent.trim() : '';
  });
}

/** The status line editControl2.js shows above the editor for a stamp that could not be loaded. */
async function stampNotice(page) {
  return page.evaluate(() => {
    const notice = document.getElementById('rtl-stamp-status');
    return notice && !notice.hidden ? notice.textContent.trim() : '';
  });
}

/**
 * Takes the 404s of one missing eForm image, requested by one page, out of the strict record and
 * returns how many there were. Only the named file on the named page: any other 404 still fails.
 */
function takeMissingImage(recorder, label, file) {
  const named = (url) => typeof url === 'string' && url.includes(`imagefile=${file}`);
  let taken = 0;
  for (let index = recorder.badResponses.length - 1; index >= 0; index--) {
    const entry = recorder.badResponses[index];
    if (entry.label === label && entry.status === 404 && named(entry.url)) {
      recorder.badResponses.splice(index, 1);
      taken++;
    }
  }
  for (let index = recorder.consoleIssues.length - 1; index >= 0; index--) {
    const entry = recorder.consoleIssues[index];
    if (entry.label === label && named(entry.location && entry.location.url) && /status of 404/.test(entry.text)) {
      recorder.consoleIssues.splice(index, 1);
    }
  }
  return taken;
}

/** Selects a template the way the dropdown does and waits for it to be filled in. */
async function loadTemplate(page, recorder, template) {
  const mark = recorder.dialogs.length;
  const frame = await editorFrame(page);
  const navigated = frame.waitForURL((url) => url.toString().endsWith(`imagefile=${template}`), { timeout: TIMEOUT });
  await page.locator('#template').selectOption(template);
  await navigated;
  await waitForLetterIdle(page);
  const raised = recorder.dialogs.slice(mark);
  h.assert(raised.length === 0,
    `${template}: loading the template raised ${raised.map((dialog) => `${dialog.type} "${dialog.text}"`).join(', ')}`);
  const text = await editorText(page);
  h.assert(!text.includes('##'), `${template}: the template still shows an unfilled ##placeholder##`);
  return text;
}

/** Draws a stroke on the signature pad and saves it; returns the stored signature's id. */
async function signOnPad(page) {
  const padElement = page.locator('#signatureFrame');
  await padElement.waitFor({ state: 'attached', timeout: TIMEOUT });
  await padElement.scrollIntoViewIfNeeded();
  const pad = await (await padElement.elementHandle()).contentFrame();
  h.assert(pad, 'The signature pad frame did not load');
  const canvas = pad.locator('#canvas');
  await canvas.waitFor({ state: 'visible', timeout: TIMEOUT });
  const box = await canvas.boundingBox();
  h.assert(box && box.width > 100 && box.height > 40, 'The signature pad canvas has no drawable area');
  await page.mouse.move(box.x + 40, box.y + box.height * 0.6);
  await page.mouse.down();
  for (let step = 1; step <= 14; step++) {
    await page.mouse.move(box.x + 40 + step * 22, box.y + box.height * (step % 2 ? 0.25 : 0.75), { steps: 3 });
  }
  await page.mouse.up();
  const save = pad.locator('#save');
  await save.waitFor({ state: 'visible', timeout: TIMEOUT });
  const upload = page.waitForResponse((response) => response.url().includes('/signature_pad/SaveSignatureUpload')
    && response.request().method() === 'POST', { timeout: TIMEOUT });
  await save.click();
  const response = await upload;
  h.assert(response.status() === 200, `Saving the drawing answered HTTP ${response.status()}`);
  const signatureId = (/name="signatureId" value="(\d+)"/.exec(await response.text()) || [])[1];
  h.assert(signatureId, 'Saving the drawing returned no signature id');
  return signatureId;
}

/**
 * What a signer with no signature on file gets once the stamp settles: the clinic's shared
 * stamp.png when there is one, otherwise no image at all and the missing-stamp notice (the 404 of
 * the absent stamp.png is then taken out of the strict record).
 */
async function expectFallbackStamp(page, recorder, label, sharedStamp) {
  if (sharedStamp) {
    await page.waitForFunction(() => Array.from(document.getElementById('edit').contentDocument.images)
      .some((image) => /imagefile=stamp\.png$/.test(image.getAttribute('src') || '') && image.complete && image.naturalWidth > 0),
    null, { timeout: TIMEOUT });
    return;
  }
  await page.waitForFunction(() => !Array.from(document.getElementById('edit').contentDocument.images)
    .some((image) => /imagefile=(stamp\.png|consult_sig_)/.test(image.getAttribute('src') || '')), null, { timeout: TIMEOUT });
  const notice = await stampNotice(page);
  h.assert(notice.startsWith(MISSING_STAMP_NOTICE), `${label}: the letter did not say why no stamp was added`);
  h.assert(takeMissingImage(recorder, 'rtl-letter', 'stamp.png') > 0, `${label}: the stamp fallback was never requested`);
}

/** The console reports a failed image a moment after the request; take that report too. */
async function settleMissingStamp(page, recorder) {
  await page.waitForTimeout(500);
  takeMissingImage(recorder, 'rtl-letter', 'stamp.png');
}

/**
 * The browser's session for a request client. The session cookie is Secure (web.xml), and Chromium
 * sends a Secure cookie to a plain-http loopback address, which it counts as a trustworthy origin;
 * Playwright's request client does not, so over http://127.0.0.1 its copy of the session would be
 * logged out. The server reads only the cookie, never its Secure attribute.
 */
function requestClientState(state, baseUrl) {
  if (new URL(String(baseUrl)).protocol !== 'http:') return state;
  return { ...state, cookies: state.cookies.map((cookie) => ({ ...cookie, secure: false })) };
}

/** Image sizes in a PDF, from pdfimages (poppler-utils); soft masks are left out. */
function pdfImageSizes(file) {
  const listing = execFileSync('pdfimages', ['-list', file], { encoding: 'utf8', timeout: 30000 });
  return listing.split('\n').slice(2)
    .map((line) => line.trim().split(/\s+/))
    .filter((columns) => columns.length > 4 && columns[2] === 'image')
    .map((columns) => ({ width: Number(columns[3]), height: Number(columns[4]) }));
}

// ---------------------------------------------------------------------------------------------
// The workflow
// ---------------------------------------------------------------------------------------------

async function workflow(s) {
  const { config, sql, patient, marker, recorder } = s;
  const text = chartText(marker);
  const fid = sql.value(`SELECT MIN(fid) FROM eform WHERE form_name=${h.sqlString(FORM_NAME)} AND status=1`);
  h.assert(/^[1-9]\d*$/.test(fid), `The "${FORM_NAME}" eForm is not installed`);
  const clinicName = (sql.value('SELECT IFNULL(MAX(clinic_name), \'\') FROM clinic') || '').trim();

  // Cleanups run in reverse: the stamp and the signature rows go before the login that owns them
  // (DigitalSignature.providerNo is a foreign key to provider).
  const account = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  s.cleanup(() => account.cleanup());
  account.create();
  s.cleanup(() => {
    sql.execute(`DELETE FROM DigitalSignature WHERE demographicId=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient}`) === '0',
      'The letter\'s stored signatures were not removed');
  });
  const specialist = createOwnedSpecialist(sql, marker);
  s.cleanup(() => removeOwnedSpecialist(sql, specialist, marker));
  seedChart(sql, { patient, marker, mrp: account.providerNo, specialist, text });

  const context = await h.newContext(s.context.browser(), config, { acceptDownloads: true });
  context.setDefaultTimeout(TIMEOUT);
  context.on('page', (page) => h.wireStrictPage(page, NAME, recorder));
  const landing = await h.login(context, { ...config, testUser: account.username }, recorder, { label: 'rtl-login' });
  await landing.close();

  // The stamp endpoint is reached outside the browser, so it can still be cleaned up once the
  // workflow has closed the browser.
  const api = await request.newContext({
    storageState: requestClientState(await context.storageState(), config.baseUrl),
    ignoreHTTPSErrors: config.ignoreHTTPSErrors === true,
  });
  let stampOnFile = false;
  let csrf = '';
  const stampUrl = h.appUrl(config.baseUrl, `/provider/providerSignatureImage?providerNo=${account.providerNo}`);
  async function stampStatus() {
    const response = await api.get(stampUrl);
    try { return response.status(); } finally { await response.dispose(); }
  }
  async function assetStatus(file) {
    const response = await api.get(h.appUrl(config.baseUrl, `/eform/displayImage?imagefile=${encodeURIComponent(file)}`));
    try { return response.status(); } finally { await response.dispose(); }
  }
  async function stampAction(method, extra = {}) {
    h.assert(csrf, 'No CSRF token was read from the letter');
    const response = await api.post(h.appUrl(config.baseUrl, '/provider/providerSignatureStamp'), {
      headers: { 'CSRF-TOKEN': csrf }, form: { method, 'CSRF-TOKEN': csrf, ...extra },
    });
    try {
      h.assert(response.status() === 200 && (await response.json()).success === true, `The stamp ${method} failed`);
    } finally { await response.dispose(); }
  }
  s.cleanup(async () => {
    try {
      if (stampOnFile) {
        await stampAction('delete');
        h.assert(await stampStatus() === 204, 'The owned signature stamp was not removed');
        stampOnFile = false;
      }
    } finally { await api.dispose(); }
  });

  let letter;
  // A clinic may ship the legacy shared stamp.png; then it, not the missing-stamp notice, is what a
  // signer without a signature gets.
  let sharedStamp = false;
  await s.step('the letter opens for the patient, with its signature pad bound to the patient', async () => {
    const before = await stampStatus();
    h.assert(before === 204, `The throwaway signer's stamp check answered HTTP ${before} before any stamp was uploaded`);
    const opened = await openLetter(context, config, recorder, { fid, patient, label: 'rtl-letter' });
    letter = opened.page;
    h.assert(opened.stampCheckStatus === 204,
      `The stamp check answered HTTP ${opened.stampCheckStatus}; with no signature on file it must answer 204, never a 404`);
    const identity = await letter.evaluate(() => ({
      user: (document.getElementById('user_id') || {}).value,
      mrp: (document.getElementById('doctor_provider_no') || {}).value,
      pad: (document.getElementById('signatureFrame') || {}).src || '',
    }));
    h.assert(identity.user === account.providerNo && identity.mrp === account.providerNo,
      'The letter did not fill in the signer\'s identity inputs');
    h.assert(identity.pad, 'The letter has no signature pad (eForm signatures disabled?)');
    const pad = new URL(identity.pad);
    h.assert(pad.pathname.endsWith('/signature_pad/tabletSignature'), `The signature pad loads ${pad.pathname}`);
    h.assert(pad.searchParams.get('demographicNo') === patient && pad.searchParams.get('ModuleType') === 'E_FORM',
      'The signature pad is not bound to the letter\'s patient under the eForm module');
    csrf = await letter.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    sharedStamp = await assetStatus('stamp.png') === 200;
    await h.screenshot(letter, config.screenshotDir, 'rtl-side-panel-open');
  });

  await s.step('every side-panel button puts the patient\'s chart data in the letter', async () => {
    const buttons = [
      ['AddLetterhead', 'Letterhead', clinicName ? [clinicName] : []],
      ['AddReferral', 'Referring Block', [`${specialist.lName}, ${specialist.fName}`, specialist.address, `Fax: ${specialist.fax}`]],
      ['label', 'Patient Label', [`${marker}, Letter`]],
      ['Patient', 'Patient Name', [`Letter ${marker}`]],
      // Each button inserts at the caret with no separator, so the age follows the name directly.
      ['PatientSex', 'Age & Gender', [/\d{1,3} year old/]],
      ['AddSocialFamilyHistory', 'Social History', [text.social]],
      ['AddMedicalHistory', 'Medical History', [text.medical]],
      ['AddOngoingConcerns', 'Ongoing Concerns', [text.concerns]],
      ['AddReminders', 'Reminders', [text.reminders]],
      ['Allergies', 'Allergies', [text.allergy]],
      ['Medlist', 'Prescriptions', [text.drug]],
      ['OtherMedicationsHistory', 'Other Medications', [text.otherMeds]],
      ['RiskFactors', 'Risk Factors', [text.riskFactors]],
      ['FamilyHistory', 'Family History', [text.family]],
      ['Preventions', 'Preventions', ['Td', '2026-01-15']],
      ['User', 'Current User', [`${marker}, Throwaway`]],
      ['Doctor', 'Doctor (MRP)', [`${marker}, Throwaway`]],
      ['Vitals', 'Vitals', ['Vitals summary', 'WT: ', '71.3']],
      ['LabGrid', 'Lab Grid', ['Lab testing summary']],
    ];
    for (const [name, label, fragments] of buttons) {
      await caretToEnd(letter);
      const before = (await editorText(letter)).length;
      const mark = recorder.dialogs.length;
      await letter.locator(`#control4 input[name="${name}"]`).click();
      if (fragments.length) {
        await waitForLetterText(letter, recorder, label, fragments, { dialogMark: mark });
      } else {
        await letter.waitForFunction((length) => {
          const body = document.getElementById('edit').contentDocument.body;
          return body && body.innerText.length > length;
        }, before, { timeout: TIMEOUT });
      }
      await waitForLetterIdle(letter);
      const notice = await lookupFailureNotice(letter);
      h.assert(!notice, `${label}: APCache reported fields it could not fill in: ${notice}`);
      console.log(`    ${label}: filled in`);
    }
    await h.screenshot(letter, config.screenshotDir, 'rtl-side-panel-buttons');
  });

  await s.step('the addressee search finds a specialist and Paste Selected inserts the address', async () => {
    await caretToEnd(letter);
    const input = letter.locator('#referral_name');
    const search = letter.waitForResponse((response) => response.url().includes('searchProfessionalSpecialist')
      && new URL(response.url()).searchParams.get('keyword') === marker, { timeout: TIMEOUT });
    await input.click();
    await input.fill(marker);
    await input.press('End');
    const response = await search;
    const searched = new URL(response.url());
    h.assert(searched.pathname.endsWith('/searchProfessionalSpecialist'), `The addressee search called ${searched.pathname}`);
    h.assert(response.status() === 200 && /json/.test(response.headers()['content-type'] || ''),
      `The addressee search answered HTTP ${response.status()}`);
    const match = letter.locator('#tempBin li', { hasText: marker });
    await match.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    await match.first().click();
    h.assert(await letter.locator('#consultantFilter').inputValue() === specialist.id,
      'Choosing the specialist did not record its id');
    await letter.locator('#AddSelected').click();
    await waitForLetterText(letter, recorder, 'Paste Selected',
      [`${specialist.fName} ${specialist.lName}`, specialist.address, `Fax: ${specialist.fax}`]);
  });

  await s.step('with no signature on file, Stamp and Closing Salutation leave no broken image and say why', async () => {
    for (const [name, label, fragments] of [['stamp', 'Stamp', []],
      ['Closing', 'Closing Salutation', ['Yours Sincerely', `T. ${marker}, MD`]]]) {
      await caretToEnd(letter);
      const mark = recorder.dialogs.length;
      const fallback = letter.waitForResponse((response) => response.url().includes('imagefile=stamp.png'), { timeout: TIMEOUT });
      await letter.locator(`#control4 input[name="${name}"]`).click();
      await fallback;
      if (fragments.length) await waitForLetterText(letter, recorder, label, fragments, { dialogMark: mark });
      await expectFallbackStamp(letter, recorder, label, sharedStamp);
    }
    h.assert((await letterImages(letter)).every((image) => image.width > 0), 'The letter holds a broken image');
    await settleMissingStamp(letter, recorder);
    await h.screenshot(letter, config.screenshotDir, 'rtl-side-panel-no-stamp');
  });

  await s.step('every shipped template loads without a prompt or an unfilled placeholder', async () => {
    const catalog = await letter.locator('#template option').evaluateAll((options) => options.map((option) => option.value));
    const templates = SHIPPED_TEMPLATES.filter((template) => catalog.includes(template));
    h.assert(templates.some((template) => template !== 'blank.rtl'),
      `The template catalog lists no shipped letter template: ${catalog.join(', ')}`);
    for (const template of templates) {
      const content = await loadTemplate(letter, recorder, template);
      if (template !== 'blank.rtl') {
        h.assert(lowerIncludes(content, marker), `${template}: the patient's details were not filled in`);
        h.assert(content.includes('Yours Sincerely'), `${template}: the closing salutation was not filled in`);
        await expectFallbackStamp(letter, recorder, template, sharedStamp);
      }
      console.log(`    ${template}: no prompt`);
    }
    await settleMissingStamp(letter, recorder);
    // Leave the editor clean so the unsaved letter closes without its unload prompt.
    await letter.evaluate(() => { window.needToConfirm = false; window.onbeforeunload = null; });
    await letter.close();
  });

  let signatureId;
  let saved;
  await s.step('with a stamp uploaded, the template, Stamp and signature pad all put loading images in the letter', async () => {
    await stampAction('saveDrawn', { signatureData: `data:image/png;base64,${stampPng(STAMP_WIDTH, STAMP_HEIGHT).toString('base64')}` });
    stampOnFile = true;
    h.assert(await stampStatus() === 200, 'The uploaded signature stamp is not served');
    const opened = await openLetter(context, config, recorder, { fid, patient, label: 'rtl-letter-signed' });
    saved = opened.page;
    h.assert(opened.stampCheckStatus === 200, `The stamp check answered HTTP ${opened.stampCheckStatus} for a signer with a stamp`);
    const stampSrc = `imagefile=consult_sig_${account.providerNo}.png`;
    const loadedStamps = async () => (await letterImages(saved))
      .filter((image) => image.src.endsWith(stampSrc) && image.complete && image.width === STAMP_WIDTH && image.height === STAMP_HEIGHT);

    const catalog = await saved.locator('#template option').evaluateAll((options) => options.map((option) => option.value));
    const template = SHIPPED_TEMPLATES.slice(1).find((name) => catalog.includes(name));
    h.assert(template, 'No shipped template with a closing salutation is installed');
    await loadTemplate(saved, recorder, template);
    await saved.waitForFunction((file) => Array.from(document.getElementById('edit').contentDocument.images)
      .some((image) => (image.getAttribute('src') || '').endsWith(file) && image.complete && image.naturalWidth > 0),
    stampSrc, { timeout: TIMEOUT });
    h.assert((await loadedStamps()).length === 1, `${template}: the closing salutation's stamp did not load`);

    await caretToEnd(saved);
    await saved.locator('#control4 input[name="stamp"]').click();
    await saved.waitForFunction((file) => Array.from(document.getElementById('edit').contentDocument.images)
      .filter((image) => (image.getAttribute('src') || '').endsWith(file) && image.complete && image.naturalWidth > 0)
      .length === 2, stampSrc, { timeout: TIMEOUT });
    h.assert(await stampNotice(saved) === '', 'A stamp that loaded left the missing-stamp notice up');

    await caretToEnd(saved);
    signatureId = await signOnPad(saved);
    h.assert(sql.value(`SELECT COUNT(*) FROM DigitalSignature WHERE id=${signatureId} AND demographicId=${patient}
      AND providerNo=${h.sqlString(account.providerNo)} AND ModuleType='E_FORM'`) === '1',
    'The drawing was not stored for the letter\'s patient under the eForm module');
    await saved.waitForFunction((id) => Array.from(document.getElementById('edit').contentDocument.images)
      .some((image) => (image.getAttribute('src') || '').includes(`digitalSignatureId=${id}`)
        && image.complete && image.naturalWidth > 0), signatureId, { timeout: TIMEOUT });
    const drawn = (await letterImages(saved)).find((image) => image.src.includes(`digitalSignatureId=${signatureId}`));
    h.assert(drawn, 'The drawn signature image is not in the letter');
    h.assert(drawn.width === PAD_WIDTH && drawn.height === PAD_HEIGHT,
      `The signature in the letter is ${drawn.width}x${drawn.height}, not the pad's ${PAD_WIDTH}x${PAD_HEIGHT} drawing`);
    await h.screenshot(saved, config.screenshotDir, 'rtl-side-panel-signed');
  });

  let fdid;
  await s.step('the saved letter reopens with its stamp and signature, and its PDF carries both', async () => {
    await saved.locator('#remote_eform_subject').fill(`${marker} signed letter`);
    await ui.clickAndAwaitReload(saved, saved.locator('#remoteSubmitButton'), { timeout: 30000, label: 'the letter save' });
    await h.assertNotErrorPage(saved, 'the saved letter');
    await saved.locator('#fdid').waitFor({ state: 'attached', timeout: TIMEOUT });
    fdid = await saved.locator('#fdid').inputValue();
    h.assert(/^[1-9]\d*$/.test(fdid) && sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid=${fdid}
      AND demographic_no=${patient}`) === '1', 'The letter was not saved for the patient');
    await saved.close();

    const view = await context.newPage();
    h.wireStrictPage(view, 'rtl-letter-saved', recorder);
    await view.addInitScript(() => { window.close = () => { window.__closeIntercepted = true; }; });
    await h.gotoApp(view, config.baseUrl, `/eform/efmshowform_data?fdid=${encodeURIComponent(fdid)}`);
    await h.assertNotErrorPage(view, 'the reopened letter');
    await waitForLetterIdle(view);
    await view.waitForFunction(() => {
      const images = Array.from(document.getElementById('edit').contentDocument.images);
      return images.length >= 3 && images.every((image) => image.complete);
    }, null, { timeout: TIMEOUT });
    const images = await letterImages(view);
    h.assert(images.every((image) => image.width > 0), 'The reopened letter holds a broken image');
    h.assert(images.filter((image) => image.width === STAMP_WIDTH && image.height === STAMP_HEIGHT).length === 2,
      'The reopened letter lost a signature stamp');
    h.assert(images.some((image) => image.src.includes(`digitalSignatureId=${signatureId}`) && image.width === PAD_WIDTH),
      'The reopened letter lost the drawn signature');

    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'rtl-side-panel-'));
    try {
      const download = view.waitForEvent('download', { timeout: 120000 });
      await view.locator('#remoteDownloadButton').click();
      const file = path.join(directory, 'letter.pdf');
      await (await download).saveAs(file);
      const bytes = fs.readFileSync(file);
      h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The letter download is not a PDF');
      const sizes = pdfImageSizes(file);
      h.assert(sizes.some((size) => size.width === PAD_WIDTH && size.height === PAD_HEIGHT),
        `The letter PDF does not carry the drawn signature (images: ${JSON.stringify(sizes)})`);
      h.assert(sizes.some((size) => size.width === STAMP_WIDTH && size.height === STAMP_HEIGHT),
        `The letter PDF does not carry the signature stamp (images: ${JSON.stringify(sizes)})`);
    } finally {
      fs.rmSync(directory, { recursive: true, force: true });
    }
    await view.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await view.close();
  });

  await s.step('the uploaded stamp is removed', async () => {
    await stampAction('delete');
    h.assert(await stampStatus() === 204, 'The owned signature stamp was not removed');
    stampOnFile = false;
  });
}

if (require.main === module) {
  runWorkflow(NAME, workflow, { openMaster: false, patientFixtureFactory: letterPatientFixture });
}
module.exports = { chartText, pdfImageSizes, takeMissingImage, workflow };
