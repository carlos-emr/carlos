#!/usr/bin/env node
/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 *
 * Rx "Fax & Paste": the encounter note names the pharmacy's phone number (issue #3974).
 *
 * The feature is a port of Open-O PR #2494 (openo-beta/Open-O, Liam Stanziani):
 * RxPharmacyData.composePharmacyPhone joins a pharmacy's phone1/phone2 and
 * rx/ViewScript2.jsp appends " Tel: <phones>" straight after the "Fax#:" part of
 * the "[Rx faxed to ...]" line it writes into the chart. This check drives the
 * real journey -- the patient's Rx module, a custom drug, "Save And Print", then
 * "Fax & Paste" in the ViewScript2 modal -- and asserts on the text the page sends
 * to /rx/WriteToEncounter and on the note that reaches casemgmt_note:
 *
 *   A. both numbers, padded, one carrying quote, double-quote and backslash
 *      characters: the line reads "Fax#: <fax> Tel: <phone1> <phone2> prescribed
 *      by", the hostile number arrives verbatim (it was JavaScript-encoded, so it
 *      neither broke the page's script nor was mangled), and a paste RETRY after the server's
 *      explicit "not-written" answer sends the identical text: the segment is
 *      built once, before the fax, and never appended a second time.
 *   B. phone2 only: "Tel: <phone2>" with no stray separator and no "null".
 *   C. no phone: the line is exactly as before the feature -- "Fax#: <fax>
 *      prescribed by", no "Tel:" label, no "null", no double space.
 *
 * Fixtures are owned by this run: a synthetic patient, pharmacy, sender account,
 * prescriptions, signatures, encounter notes and fax jobs. Existing patient and
 * pharmacy records are never modified. Database children are removed before the
 * owned patient, and cleanup failures fail the check.
 *
 * Requires rx_fax_enabled=true, rx_signature_enabled=true, an enabled facility
 * and a provider stamp PNG consult_sig_<provider>.png in the eForm image dir.
 * Env: BASE_URL, TEST_USER/PASSWORD/PIN, MYSQL_HOST/USER/PASSWORD/DATABASE.
 * Optional RX_FAX_PROVIDER_NO verifies the provider attached to TEST_USER;
 * RX_FAX_ROUND_TRIP_TIMEOUT_MS defaults to 45000 (maximum 600000).
 * RX_FAX_DOCUMENT_DIR and RX_FAX_SPOOL_DIR must name the install's local artifact
 * directories; only files associated with this run's owned fax jobs are removed.
 *
 * Nothing from the chart or the PDF is printed: the note is compared, and a
 * mismatch is reported by case and by what differed, never by its text.
 *
 * Run: npm run test:rx-fax-pharmacy-phone-playwright
 */

'use strict';

const { randomInt } = require('node:crypto');
const { readFaxSuffix, assertFaxDestination, installFaxRequestGuard } = require('./rx-fax-request-guard');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const { createFaxPhoneFixtures } = require('./rx-fax-pharmacy-fixtures');
const path = require('node:path');
const { cleanupOwnedWorkflow } = require('./lib/workflow-session');
const {
  assert,
  createRecorder,
  buildFailureDetails,
  createSqlRunner,
  gotoApp,
  launchBrowser,
  login,
  newContext,
  pathOnly,
  readConfig,
  runCheck,
  sqlString,
  withExpectedDialogs,
  wireStrictPage,
} = require('./lib/playwright-harness');
const { assertFaxCaseBrowser } = require('./rx-fax-pharmacy-browser');
const { assertStackedRxPdf } = require('./lib/rx-pdf-layout');
const { settleOperations } = require('./graceful-signal-cancellation');

let demographicNo;
let fixtures;
const faxRoundTripTimeoutMs = Number(process.env.RX_FAX_ROUND_TRIP_TIMEOUT_MS || '45000');
// Playwright reads NaN and 0 as "no timeout", so a malformed value would make the waits unbounded.
assert(Number.isInteger(faxRoundTripTimeoutMs) && faxRoundTripTimeoutMs >= 1000 && faxRoundTripTimeoutMs <= 600000,
  'RX_FAX_ROUND_TRIP_TIMEOUT_MS must be an integer between 1000 and 600000');

// Per-run values. The "from" number must be exactly 10 chars (fax_config.faxNumber varchar(10));
// every destination is NPA 555 so no fixture fax is routable; pharmacyInfo phone columns are
// varchar(20), which bounds the hostile value below.
const runSuffix = readFaxSuffix(process.env.PR4055_RX_FAX_SUFFIX, 6, randomInt);
const fromFaxNumber = `416${runSuffix}0`;
const drugNamePrefix = `PW FAX TEL ${runSuffix}`;
const CASES = [
  {
    key: 'A',
    label: 'both numbers, padded, one hostile',
    phone1: `  416 555 ${runSuffix.slice(0, 4)}  `,
    // Quote, double quote and backslash: each ends or escapes a JavaScript string literal, so the
    // value only arrives intact if the JSP encoded it. Markup such as </script> is deliberately not
    // used: through the packaged front door ModSecurity CRS 941160 rejects it in the note body before
    // it reaches the application, which is the WAF doing its job, not a page defect.
    phone2: 'x\'"\\1',
    expectedPhones: `416 555 ${runSuffix.slice(0, 4)} x'"\\1`,
    retry: true,
  },
  {
    key: 'B', label: 'phone2 only', phone1: null, phone2: `416-555-${runSuffix.slice(2)}`, expectedPhones: `416-555-${runSuffix.slice(2)}`,
  },
  {
    key: 'C', label: 'no phone on file', phone1: '', phone2: null, expectedPhones: '',
  },
].map((c, index) => ({
  ...c,
  paper: ['PageSize.A6', 'PageSize.A4', 'PageSize.Letter'][index],
  pharmacyFax: `555${runSuffix}${index + 1}`,
  drugName: `${drugNamePrefix} ${c.key}`,
}));

const recorder = createRecorder();
const config = readConfig();
let db = null;
const marker = `FAKE-PW-RX-TEL-${runSuffix}`;
let browser;
const artifactDirectories = ['RX_FAX_DOCUMENT_DIR', 'RX_FAX_SPOOL_DIR'].map(key => {
  assert(process.env[key], `${key} is required for owned fax artifact cleanup`);
  const directory = fs.realpathSync(process.env[key]);
  assert(directory !== path.parse(directory).root, 'Fax artifact directory must not be the filesystem root');
  return directory;
});

// --- fixtures -----------------------------------------------------------------------

// --- the journey --------------------------------------------------------------------

/** The patient's Rx module, a custom drug, "Save And Print": ViewScript2 opens in the modal. */
async function writeCustomRxThroughUi(page, testCase) {
  await gotoApp(page, config.baseUrl, `/rx/choosePatient?demographicNo=${demographicNo}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await page.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('#searchString').fill(testCase.drugName);
  await withExpectedDialogs(page, () => page.locator('#customDrug').click());
  await page.locator("[id^='drugName_'], [id^='quantity_']").first().waitFor({ state: 'attached', timeout: 30000 });
  await page.locator('#saveButton').click();
  const modalFrame = page.frameLocator('#carlosModalBody iframe');
  await modalFrame.locator('#faxPasteButton').waitFor({ state: 'visible', timeout: 30000 });
  // Fax & Paste reads #preview2Form from the nested preview synchronously; wait for it.
  await modalFrame.frameLocator('#preview').locator('#preview2Form')
    .waitFor({ state: 'attached', timeout: faxRoundTripTimeoutMs });
  await modalFrame.locator('#printPageSize').selectOption(testCase.paper);
  await modalFrame.locator('#faxNumber').selectOption(fromFaxNumber);
  assert(await modalFrame.locator('#faxNumber').inputValue() === fromFaxNumber, 'Owned fax sender was not selected');
  const scriptNo = db.value(`SELECT MAX(script_no) FROM drugs WHERE customName=${sqlString(testCase.drugName)} AND demographic_no=${demographicNo};`);
  assert(/^\d+$/.test(String(scriptNo)), `case ${testCase.key}: no prescription row was created for the fixture drug`);
  return modalFrame;
}

const isEncounterWrite = (request) => request.method() === 'POST' && /\/rx\/WriteToEncounter(?:\?|$)/.test(pathOnly(request.url()));

function faxedLine(body) {
  const line = String(body || '').split('\n').find((l) => l.startsWith('[Rx faxed to '));
  return line === undefined ? null : line;
}

/** The problems with one "[Rx faxed to ...]" line for a case, as descriptions without its text. */
function lineProblems(testCase, line) {
  if (line === null) return ['no "[Rx faxed to" line was sent'];
  const problems = [];
  const expected = `Fax#: ${testCase.pharmacyFax}${testCase.expectedPhones ? ` Tel: ${testCase.expectedPhones}` : ''} prescribed by `;
  if (!line.includes(expected)) problems.push('the Fax#/Tel segment is not exactly the expected text');
  if ((line.match(/ Tel: /g) || []).length !== (testCase.expectedPhones ? 1 : 0)) problems.push('the Tel: label appears the wrong number of times');
  if (/\bnull\b/i.test(line)) problems.push('the line contains "null"');
  if (!testCase.expectedPhones && line.includes('Tel:')) problems.push('a Tel: label was written with no phone on file');
  return problems;
}

async function faxAndPaste(page, testCase, releasePharmacy) {
  const bodies = [];
  let retryClicked = false;
  await page.route(/\/rx\/WriteToEncounter/, async (route) => {
    const request = route.request();
    bodies.push(new URLSearchParams(request.postData() || '').get('body'));
    if (testCase.retry && bodies.length === 1) {
      // The server's explicit pre-write rejection: the only answer ViewScript2 treats as safe to
      // retry. Nothing reaches the chart on this attempt.
      await route.fulfill({ status: 409, headers: { 'X-Carlos-Encounter-Write': 'not-written' }, contentType: 'text/plain', body: 'Synthetic pre-write refusal' });
      return;
    }
    await route.continue();
  });
  try {
    const firstWrite = page.waitForResponse(res => isEncounterWrite(res.request()), { timeout: faxRoundTripTimeoutMs });
    const faxPost = page.waitForResponse((res) => /form\/createcustomedpdf/.test(res.url()) && /__method=oscarRxFax/.test(res.url()), { timeout: faxRoundTripTimeoutMs });
    const modalFrame = page.frameLocator('#carlosModalBody iframe');
    const roundTrip = settleOperations([faxPost, firstWrite, modalFrame.locator('#faxPasteButton').click()]);
    const [faxResponse] = await roundTrip;
    assertFaxDestination(faxResponse.request(), fromFaxNumber, testCase.pharmacyFax);
    releasePharmacy();
    assert(faxResponse.status() === 200, `case ${testCase.key}: the fax POST answered HTTP ${faxResponse.status()}`);

    if (testCase.retry) {
      const retryButton = modalFrame.locator('#faxPasteRetryButton');
      await retryButton.waitFor({ state: 'visible', timeout: 30000 });
      assert(await retryButton.isEnabled(), `case ${testCase.key}: an explicit not-written answer did not enable the paste retry`);
      const [retryWrite] = await settleOperations([
        page.waitForResponse((res) => isEncounterWrite(res.request()), { timeout: faxRoundTripTimeoutMs }),
        retryButton.click(),
      ]);
      retryClicked = true;
      assert(retryWrite.headers()['x-carlos-encounter-write'] === 'written',
        `case ${testCase.key}: the retried encounter write was not acknowledged as written `
        + `(HTTP ${retryWrite.status()}, outcome ${retryWrite.headers()['x-carlos-encounter-write'] || 'absent'})`);
    } else {
      const response = await firstWrite;
      assert(response && response.headers()['x-carlos-encounter-write'] === 'written',
        `case ${testCase.key}: the encounter write was not acknowledged as written `
        + `(HTTP ${response ? response.status() : 'none'}, outcome ${(response && response.headers()['x-carlos-encounter-write']) || 'absent'})`);
    }
  } finally {
    await page.unroute(/\/rx\/WriteToEncounter/).catch(() => {});
  }
  return { bodies, retryClicked };
}

/** The chart note this case wrote, read back from MariaDB (the latest revision holding its fax number). */
function storedNoteLine(testCase) {
  const note = db.value(`SELECT note FROM casemgmt_note WHERE demographic_no=${sqlString(demographicNo)} `
    + `AND note LIKE ${sqlString(`%Fax#: ${testCase.pharmacyFax}%`)} ORDER BY note_id DESC LIMIT 1;`);
  const lines = String(note || '').split('\n').filter((l) => l.startsWith('[Rx faxed to ') && l.includes(`Fax#: ${testCase.pharmacyFax}`));
  return { line: lines.length ? lines[0] : null, occurrences: lines.length };
}

async function runCase(context, testCase) {
  fixtures.stagePharmacyPhones(testCase);
  const page = await context.newPage();
  const label = `case-${testCase.key}`;
  wireStrictPage(page, label, recorder);
  await installFaxRequestGuard(page, config.baseUrl, fromFaxNumber, testCase.pharmacyFax, () => {
    recorder.pageErrors.push({ label, text: 'Blocked a fax POST with an unowned sender or destination' });
  });
  // A pharmacy header must already be available to a fast Fax click. Hold the
  // redundant contact lookup until the fax has queued to expose the old race.
  let releasePharmacy;
  const pharmacyGate = new Promise(resolve => { releasePharmacy = resolve; });
  await page.route('**/rx/managePharmacy2?method=getPharmacyInfo*', async route => {
    await pharmacyGate;
    try { await route.continue(); } catch (error) { if (!page.isClosed()) throw error; }
  });
  try {
    await writeCustomRxThroughUi(page, testCase);
    const pharmacyPreview = page.frameLocator('#carlosModalBody iframe').frameLocator('#preview').locator('#pharmInfo');
    await pharmacyPreview.waitFor({ state: 'visible' });
    const previewText = await pharmacyPreview.innerText();
    assert(!previewText.includes('Email:') && !previewText.includes('Note:'),
      `case ${testCase.key}: missing pharmacy contacts left empty preview labels`);
    assert(!previewText.includes('null') && !previewText.includes('undefined'),
      `case ${testCase.key}: nullable contacts leaked into the pharmacy preview`);
    if (testCase.expectedPhones) {
      assert(previewText.includes(`Tel:${testCase.expectedPhones}`),
        `case ${testCase.key}: preview did not preserve trimmed available telephone numbers`);
    } else {
      assert(!previewText.includes('Tel:'), `case ${testCase.key}: absent telephone left an empty preview label`);
    }
    let outcome;
    // The page alerts "could not paste to EMR" once when the server refuses the first write; that
    // is the expected path for the retry case and must not happen for the others.
    const dialogs = await withExpectedDialogs(page, async () => { outcome = await faxAndPaste(page, testCase, releasePharmacy); });
    const { bodies, retryClicked } = outcome;
    const problems = [];
    const expectedAlerts = testCase.retry ? 1 : 0;
    if (dialogs.length !== expectedAlerts || dialogs.some((d) => d.type !== 'alert')) {
      problems.push(`expected ${expectedAlerts} paste-failure alert(s), saw ${dialogs.map((d) => d.type).join(',') || 'none'}`);
    }
    const sent = faxedLine(bodies[0]);
    problems.push(...lineProblems(testCase, sent).map((p) => `sent: ${p}`));
    if (testCase.retry) {
      if (!retryClicked || bodies.length !== 2) problems.push(`expected one retry, saw ${bodies.length} encounter writes`);
      else if (bodies[1] !== bodies[0]) problems.push('the retry did not resend the identical captured text');
    } else if (bodies.length !== 1) {
      problems.push(`expected one encounter write, saw ${bodies.length}`);
    }
    const stored = storedNoteLine(testCase);
    problems.push(...lineProblems(testCase, stored.line).map((p) => `stored: ${p}`));
    if (stored.occurrences !== 1) problems.push(`stored: the chart note carries this fax line ${stored.occurrences} times`);
    if (stored.line !== null && sent !== null && stored.line !== sent) problems.push('stored: the chart line differs from the text the page sent');
    const filename = db.value(`SELECT filename FROM faxes WHERE faxline=${sqlString(fromFaxNumber)}
      AND demographicNo=${demographicNo} AND destination=${sqlString(`1${testCase.pharmacyFax}`)}`);
    assert(/^prescription_[a-zA-Z0-9_-]{1,128}\.pdf$/.test(filename), `case ${testCase.key}: expected exactly one owned fax PDF`);
    let pdfText;
    try {
      pdfText = execFileSync('pdftotext', ['-raw', path.join(artifactDirectories[0], filename), '-'],
        { encoding: 'utf8', timeout: 15000, stdio: ['ignore', 'pipe', 'pipe'] });
    } catch (_) { throw new Error(`case ${testCase.key}: owned fax PDF extraction failed`); }
    assert(pdfText.includes(marker), `case ${testCase.key}: fax PDF lost its pharmacy name`);
    assert(pdfText.includes(testCase.pharmacyFax), `case ${testCase.key}: fax PDF lost its pharmacy fax number`);
    assert(!pdfText.includes('RxPreview.msgTel') && !/\bnull\b/i.test(pdfText), `case ${testCase.key}: fax PDF has unresolved or missing values`);
    if (testCase.expectedPhones) {
      assert(pdfText.replace(/\s+/g, '').includes(`Tel:${testCase.expectedPhones}`.replace(/\s+/g, '')),
        `case ${testCase.key}: fax PDF lost or changed its phone values`);
    } else {
      const pharmacyBlock = pdfText.slice(pdfText.indexOf('ATTENTION:'), pdfText.indexOf(testCase.pharmacyFax));
      assert(!pharmacyBlock.includes('Tel:'), `case ${testCase.key}: no-phone pharmacy has a dangling telephone label`);
    }
    if (testCase.paper === 'PageSize.A6') {
      let bounds;
      try {
        bounds = execFileSync('pdftotext', ['-bbox', path.join(artifactDirectories[0], filename), '-'],
          { encoding: 'utf8', timeout: 15000, stdio: ['ignore', 'pipe', 'pipe'] });
      } catch (_) { throw new Error(`case ${testCase.key}: owned fax PDF geometry extraction failed`); }
      assertStackedRxPdf(bounds, { patientLastWord: '0000000000', pharmacyFax: testCase.pharmacyFax, drugWord: 'PW' });
      console.log(`  PASS ${testCase.paper}: patient, pharmacy and drug blocks do not overlap`);
    }
    assertFaxCaseBrowser(recorder, label, testCase.retry);
    assert(problems.length === 0, `case ${testCase.key} (${testCase.label}): ${problems.join('; ')}`);
    return `${testCase.key}${testCase.retry ? '+retry' : ''}`;
  } finally {
    releasePharmacy();
    await page.unrouteAll({ behavior: 'wait' });
    await page.close().catch(() => {});
  }
}

async function main({ cancellation }) {
  db = createSqlRunner(config.mysql);
  fixtures = createFaxPhoneFixtures({ db, config, marker, fromFaxNumber, drugNamePrefix, artifactDirectories,
    expectedProvider: process.env.RX_FAX_PROVIDER_NO });
  fixtures.stage();
  demographicNo = fixtures.patient;
  browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const home = await cancellation.run(() => login(context, config, recorder));
    await home.close().catch(() => {});
    const passed = [];
    for (const testCase of CASES) {
      // eslint-disable-next-line no-await-in-loop -- cases share pharmacy rows and must run in order
      passed.push(await cancellation.run(() => runCase(context, testCase)));
    }
    await context.close();
    return passed;
  } finally {
    await browser.close().catch(() => {});
  }
}

runCheck({
  name: 'rx-fax-pharmacy-phone: "[Rx faxed to ...]" carries " Tel: <phones>" (both, phone2 only, none; JS-encoded; retry not duplicated)',
  run: main,
  cleanup: async () => cleanupOwnedWorkflow({ browser, sql: db, patient: fixtures?.patient, marker,
    cleanups: fixtures ? [fixtures.cleanup] : [] }),
}).then(result => {
  if (result.outcome === 'FAIL') console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
});
