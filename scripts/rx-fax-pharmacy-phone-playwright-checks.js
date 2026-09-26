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
 * Fixtures this run seeds and REMOVES: one custom-drug prescription per case (its
 * drugs row and stamp signature), a fax_config "from" account when no active
 * SRFAX row exists on that number, the fax job rows on that line with their
 * FaxClientLog audit rows, and the fax/phone1/phone2 of EVERY active pharmacy of
 * the patient -- replaced for the run so no fixture can leave for a real fax
 * machine, and each original restored exactly (NULL vs '' preserved). Left in
 * place on purpose: the three "[Rx faxed to ...]" lines the check appends to the
 * patient's chart (chart notes are an audit trail and are never deleted), one
 * prescription_<pdfId>.pdf per case under DOCUMENT_DIR, and the fax-spool pairs
 * the fax scheduler consumes. Run it against a disposable database only.
 *
 * Prerequisites on the install (docs/ui-tests/deb-install-validation.md section 6,
 * the same as rx-fax-record-binding-playwright-checks.js):
 *   - rx_fax_enabled=true and rx_signature_enabled=true in carlos.properties,
 *   - the session facility has digital signatures enabled (demo default),
 *   - a provider stamp PNG consult_sig_<provider>.png in the eForm image dir,
 *   - the patient has at least one active pharmacy (the demo dataset does).
 *
 * Env contract: BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE.
 * Optional: RX_FAX_DEMOGRAPHIC_NO (default 1), RX_FAX_PROVIDER_NO (default 999998),
 *   RX_FAX_ROUND_TRIP_TIMEOUT_MS (default 45000), CHROME_PATH.
 *
 * Nothing from the chart or the PDF is printed: the note is compared, and a
 * mismatch is reported by case and by what differed, never by its text.
 *
 * Run: npm run test:rx-fax-pharmacy-phone-playwright
 */

'use strict';

const { randomInt } = require('node:crypto');
const {
  assert,
  assertNoPageErrors,
  createRecorder,
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
  wirePage,
} = require('./lib/playwright-harness');
const { settleOperations } = require('./graceful-signal-cancellation');

const demographicNo = String(process.env.RX_FAX_DEMOGRAPHIC_NO || '1').trim();
const providerNo = String(process.env.RX_FAX_PROVIDER_NO || '999998').trim();
const faxRoundTripTimeoutMs = Number(process.env.RX_FAX_ROUND_TRIP_TIMEOUT_MS || '45000');
assert(/^\d+$/.test(demographicNo), 'RX_FAX_DEMOGRAPHIC_NO must be numeric');
assert(/^\d+$/.test(providerNo), 'RX_FAX_PROVIDER_NO must be numeric');
// Playwright reads NaN and 0 as "no timeout", so a malformed value would make the waits unbounded.
assert(Number.isInteger(faxRoundTripTimeoutMs) && faxRoundTripTimeoutMs >= 1000 && faxRoundTripTimeoutMs <= 600000,
  'RX_FAX_ROUND_TRIP_TIMEOUT_MS must be an integer between 1000 and 600000');

// Per-run values. The "from" number must be exactly 10 chars (fax_config.faxNumber varchar(10));
// every destination is NPA 555 so no fixture fax is routable; pharmacyInfo phone columns are
// varchar(20), which bounds the hostile value below.
const runSuffix = String(randomInt(100000, 1000000)); // 6 digits, crypto RNG
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
  pharmacyFax: `555${runSuffix}${index + 1}`,
  drugName: `${drugNamePrefix} ${c.key}`,
}));

const recorder = createRecorder();
const config = readConfig();
let db = null;
let faxConfig = null;
const seededPharmacies = [];

// --- fixtures -----------------------------------------------------------------------

function stageFaxConfig() {
  const existing = db.value(`SELECT id FROM fax_config WHERE faxNumber=${sqlString(fromFaxNumber)} AND active=1 AND providerType='SRFAX' LIMIT 1;`);
  if (/^\d+$/.test(existing)) return { id: existing, created: false };
  const id = db.value(
    'INSERT INTO fax_config (providerType, active, faxNumber, faxReply, accountName, senderEmail, faxUser, siteUser, passwd, faxPasswd, gatewayName, queue, url, download) '
    + `VALUES ('SRFAX', 1, ${sqlString(fromFaxNumber)}, ${sqlString(fromFaxNumber)}, 'Playwright Fax', 'fax@example.ca', 'faxuser', 'siteuser', 'x', 'x', 'srfax', '0', '', 1); SELECT LAST_INSERT_ID();`,
  );
  assert(/^\d+$/.test(id), 'the fixture fax_config row was not created');
  return { id, created: true };
}

/** Remember every active pharmacy's fax/phone1/phone2 exactly, so cleanup can restore them. */
function capturePharmacies() {
  const rows = db.rows(`SELECT p.recordId,
      p.fax IS NULL, IFNULL(p.fax, ''), p.phone1 IS NULL, IFNULL(p.phone1, ''), p.phone2 IS NULL, IFNULL(p.phone2, '')
    FROM pharmacyInfo p JOIN demographicPharmacy dp ON dp.pharmacyID = p.recordId
    WHERE dp.demographic_no = ${demographicNo} AND dp.status = '1' AND (p.status IS NULL OR p.status <> '0');`);
  for (const [recordId, faxNull, fax, phone1Null, phone1, phone2Null, phone2] of rows) {
    assert(/^\d+$/.test(String(recordId)), 'unexpected pharmacy recordId shape');
    seededPharmacies.push({
      recordId: String(recordId),
      fax: String(faxNull) === '1' ? null : String(fax ?? ''),
      phone1: String(phone1Null) === '1' ? null : String(phone1 ?? ''),
      phone2: String(phone2Null) === '1' ? null : String(phone2 ?? ''),
    });
  }
  assert(seededPharmacies.length > 0,
    'the RX_FAX_DEMOGRAPHIC_NO patient has no active pharmacy, so a prescription for them can never be faxed');
}

const sqlValue = (value) => (value === null ? 'NULL' : sqlString(value));

function stagePharmacyPhones(testCase) {
  for (const { recordId } of seededPharmacies) {
    db.execute(`UPDATE pharmacyInfo SET fax = ${sqlString(testCase.pharmacyFax)}, phone1 = ${sqlValue(testCase.phone1)}, `
      + `phone2 = ${sqlValue(testCase.phone2)} WHERE recordId = ${recordId};`);
  }
}

function cleanupFixtures() {
  const failures = [];
  const attempt = (label, fn) => {
    try { fn(); } catch (error) { failures.push(label); }
  };
  if (!db) return;
  let scriptNos = [];
  attempt('list fixture scripts', () => {
    scriptNos = db.rows(`SELECT DISTINCT script_no FROM drugs WHERE customName LIKE ${sqlString(`${drugNamePrefix} %`)} AND demographic_no=${demographicNo};`)
      .map(([n]) => String(n)).filter((n) => /^\d+$/.test(n));
  });
  for (const scriptNo of scriptNos) {
    attempt(`prescription ${scriptNo}`, () => {
      const sigId = db.value(`SELECT COALESCE(digital_signature_id,'') FROM prescription WHERE script_no=${scriptNo};`);
      db.execute(`DELETE FROM drugs WHERE script_no=${scriptNo};`);
      db.execute(`DELETE FROM prescription WHERE script_no=${scriptNo};`);
      if (/^\d+$/.test(String(sigId))) db.execute(`DELETE FROM DigitalSignature WHERE id=${sigId};`);
    });
  }
  attempt('faxes', () => {
    const faxIds = db.rows(`SELECT id FROM faxes WHERE faxline=${sqlString(fromFaxNumber)};`)
      .map(([id]) => String(id)).filter((id) => /^\d+$/.test(id));
    if (faxIds.length) {
      db.execute(`DELETE FROM FaxClientLog WHERE transactionType='RX' AND faxId IN (${faxIds.map((id) => `'${id}'`).join(',')});`);
    }
    db.execute(`DELETE FROM faxes WHERE faxline=${sqlString(fromFaxNumber)};`);
  });
  attempt('fax_config', () => {
    if (faxConfig && faxConfig.created) db.execute(`DELETE FROM fax_config WHERE id=${faxConfig.id};`);
  });
  while (seededPharmacies.length) {
    const original = seededPharmacies.pop();
    attempt(`pharmacy ${original.recordId}`, () => db.execute(
      `UPDATE pharmacyInfo SET fax = ${sqlValue(original.fax)}, phone1 = ${sqlValue(original.phone1)}, `
      + `phone2 = ${sqlValue(original.phone2)} WHERE recordId = ${original.recordId};`,
    ));
  }
  if (failures.length) {
    throw new Error(`fixture cleanup failed for: ${failures.join(', ')}`);
  }
}

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

async function faxAndPaste(page, testCase) {
  const bodies = [];
  let retryClicked = false;
  await page.route(/\/rx\/WriteToEncounter/, async (route) => {
    const request = route.request();
    bodies.push(new URLSearchParams(request.postData() || '').get('body'));
    if (testCase.retry && bodies.length === 1) {
      // The server's explicit pre-write rejection: the only answer ViewScript2 treats as safe to
      // retry. Nothing reaches the chart on this attempt.
      await route.fulfill({ status: 409, headers: { 'X-Carlos-Encounter-Write': 'not-written' }, body: '' });
      return;
    }
    await route.continue();
  });
  try {
    const firstWrite = page.waitForRequest(isEncounterWrite, { timeout: faxRoundTripTimeoutMs });
    const faxPost = page.waitForResponse((res) => /form\/createcustomedpdf/.test(res.url()) && /__method=oscarRxFax/.test(res.url()), { timeout: faxRoundTripTimeoutMs });
    const modalFrame = page.frameLocator('#carlosModalBody iframe');
    const roundTrip = settleOperations([faxPost, firstWrite]);
    roundTrip.catch(() => {});
    await modalFrame.locator('#faxPasteButton').click();
    const [faxResponse] = await roundTrip;
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
      const response = await (await firstWrite).response();
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
  stagePharmacyPhones(testCase);
  const page = await context.newPage();
  const label = `case-${testCase.key}`;
  wirePage(page, label, recorder);
  try {
    await writeCustomRxThroughUi(page, testCase);
    let outcome;
    // The page alerts "could not paste to EMR" once when the server refuses the first write; that
    // is the expected path for the retry case and must not happen for the others.
    const dialogs = await withExpectedDialogs(page, async () => { outcome = await faxAndPaste(page, testCase); });
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
    assertNoPageErrors(recorder, [label]);
    assert(problems.length === 0, `case ${testCase.key} (${testCase.label}): ${problems.join('; ')}`);
    return `${testCase.key}${testCase.retry ? '+retry' : ''}`;
  } finally {
    await page.close().catch(() => {});
  }
}

async function main({ cancellation }) {
  db = createSqlRunner(config.mysql);
  faxConfig = stageFaxConfig();
  capturePharmacies();
  const browser = await launchBrowser(config);
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
  cleanup: async () => {
    try {
      cleanupFixtures();
    } finally {
      if (db) db.dispose();
    }
  },
});
