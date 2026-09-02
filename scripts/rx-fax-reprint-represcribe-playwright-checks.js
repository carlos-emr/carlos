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
 * Browser regression check for the Rx REPRINT and RE-PRESCRIBE routes, and for
 * the packaged build stamp — driven through the controls a clinician clicks.
 *
 * Companion to rx-fax-signature-stamp-playwright-checks.js, which covers the
 * original "Fax greyed out despite a stamp" defect. This one covers the
 * behaviours that came out of review afterwards, none of which had real-UI
 * coverage:
 *
 *   1. Reprinting a historical script must not write a second prescription and
 *      must not re-sign the one being reprinted. RxViewScript2Action used to
 *      infer "already persisted" from a uniform script_no on the session stash
 *      and would call saveScript again (a duplicate prescription and duplicate
 *      drugs rows), or stamp whatever script number the stash happened to
 *      carry.
 *   2. RE-PRESCRIBING then reprinting must leave the historical script alone.
 *      A re-prescribed stash item is built in memory carrying the ORIGINAL
 *      script_no with no drugs row, so the old "uniform script_no == persisted"
 *      rule mistook it for a saved script: the save was skipped and the
 *      prescriber's stamp was written onto a historical prescription they were
 *      not printing. Reaching this needs the real two-step journey — re-prescribe
 *      from the drug profile, then reprint — which is why it is a browser check.
 *   3. A stamp-signed script must stay faxable across signature-pad activity.
 *      The stamp made the pad and an enabled Fax button coexist for the first
 *      time, and the pad's event handler recomputed the Fax state from the pad
 *      alone, so a stray stroke or Clear greyed out a script the server would
 *      still have faxed.
 *   4. The build stamp must survive packaging: the authenticated About page
 *      shows it, and the unauthenticated login page must not (CWE-200 defence
 *      in depth). Asserted against the exact string the packaged WAR should
 *      carry when RX_EXPECTED_BUILD_TAG is supplied.
 *
 * FIXTURE SAFETY: the check creates one prescription (plus its drugs row and
 * the stored stamp signature) through the UI, identified by a per-run-unique
 * custom drug name, and removes exactly those rows in a finally and on
 * SIGINT/SIGTERM. It reprints and re-prescribes only rows it created itself, so
 * no pre-existing patient record is mutated. It writes no files; the fax servlet
 * is not exercised here (that is the sibling check's job).
 *
 * Env contract:
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE.
 * Optional:
 *   RX_FAX_DEMOGRAPHIC_NO (default 1), RX_FAX_PROVIDER_NO (default 999998),
 *   RX_EXPECTED_BUILD_TAG (exact About-page build tag to require, e.g.
 *     "2026.08.0-alpha11-SNAPSHOT (carlos-emr-deb 2026.09.0~snapshot18)"),
 *   CHROME_PATH, ALLOW_NON_LOCAL_BASE_URL, ALLOW_NON_LOCAL_MYSQL_HOST.
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const { randomInt } = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = String(process.env.RX_FAX_DEMOGRAPHIC_NO || '1').trim();
const providerNo = String(process.env.RX_FAX_PROVIDER_NO || '999998').trim();
const expectedBuildTag = (process.env.RX_EXPECTED_BUILD_TAG || '').trim();
// Obviously-synthetic, and restored to its original value in cleanupFixtures().
const FIXTURE_FAX_NUMBER = '555-0100';

const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || 'localhost');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || '';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

// Per-run identifier so a concurrent or crashed-then-rerun invocation is never correlated with,
// nor cleaned up by, another run. drugs.customName is varchar(60).
const runSuffix = String(randomInt(1000000, 10000000));
const customDrugName = `PW RX REPRINT ${Date.now()}${runSuffix}`;

if (!/^\d+$/.test(demographicNo)) throw new Error(`RX_FAX_DEMOGRAPHIC_NO must be numeric, got ${demographicNo}`);
if (!/^\d+$/.test(providerNo)) throw new Error(`RX_FAX_PROVIDER_NO must be numeric, got ${providerNo}`);

const findings = [];
const visited = [];
let mysqlDefaults = null;
let expectingCustomDrugConfirm = false;

// --- config validation -------------------------------------------------------

function validateBaseUrl(rawBaseUrl) {
  let parsed;
  try {
    parsed = new URL(rawBaseUrl);
  } catch (e) {
    throw new Error(`BASE_URL is not a valid URL: ${rawBaseUrl}`);
  }
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not embed credentials');
  }
  const allowed = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'carlos', 'host.docker.internal']);
  if (!allowed.has(parsed.hostname) && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`refusing non-local BASE_URL host ${parsed.hostname}; set ALLOW_NON_LOCAL_BASE_URL=true to override`);
  }
  return `${parsed.origin}${parsed.pathname.replace(/\/$/, '')}`;
}

function validateMysqlHost(host) {
  const allowed = new Set(['localhost', '127.0.0.1', '::1', 'db', 'mysql', 'mariadb']);
  if (!allowed.has(host) && process.env.ALLOW_NON_LOCAL_MYSQL_HOST !== 'true') {
    throw new Error(`refusing non-local MYSQL_HOST ${host}; set ALLOW_NON_LOCAL_MYSQL_HOST=true to override`);
  }
  return host;
}

function appUrl(relativePath) {
  if (!relativePath.startsWith('/') || relativePath.startsWith('//')) {
    throw new Error(`refusing non-root-relative app path: ${relativePath}`);
  }
  return `${baseUrl}${relativePath}`;
}

function gotoApp(page, relativePath, options = { waitUntil: 'domcontentloaded', timeout: 30000 }) {
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to loopback by default
  return page.goto(appUrl(relativePath), options);
}

// --- MySQL -------------------------------------------------------------------

function resolveMysqlBinary() {
  for (const candidate of ['mariadb', 'mysql']) {
    try {
      execFileSync('sh', ['-c', `command -v ${candidate}`], { stdio: ['ignore', 'ignore', 'ignore'] });
      return candidate;
    } catch (e) { /* try the next one */ }
  }
  throw new Error('neither the mariadb nor the mysql client is on PATH; this check needs one to verify rows');
}

const mysqlBinary = resolveMysqlBinary();

/**
 * A 0600 option file so the password never reaches a command line or the process table.
 * MariaDB option-file quoting: a backslash starts an escape and an unquoted '#' truncates the
 * line, so the value is double-quoted with backslashes and quotes escaped.
 */
function encodeOptionFileValue(value) {
  return `"${String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain a newline');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pw-rx-reprint-'));
  const file = path.join(dir, 'my.cnf');
  try {
    fs.writeFileSync(file, `[client]\nuser=${encodeOptionFileValue(mysqlUser)}\npassword=${encodeOptionFileValue(mysqlPassword)}\nhost=${encodeOptionFileValue(mysqlHost)}\n`, { mode: 0o600 });
  } catch (e) {
    fs.rmSync(dir, { recursive: true, force: true });
    throw e;
  }
  return { dir, file };
}

function removeSecretsDir() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}

function sql(query) {
  if (!mysqlDefaults) mysqlDefaults = createMysqlDefaultsFile();
  try {
    return execFileSync(mysqlBinary, [
      `--defaults-extra-file=${mysqlDefaults.file}`,
      '-N', '-B', mysqlDatabase, '-e', query,
    ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 30000 });
  } catch (e) {
    // Deliberately do not echo the whole query: it can carry identifiers.
    const detail = String((e && e.stderr) || (e && e.message) || e).slice(0, 200);
    throw new Error(`SQL failed (${query.slice(0, 40)}...): ${detail}`);
  }
}

function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

/** Script numbers this run created, newest last, identified by the fixture drug name. */
function createdScriptNos() {
  return sql(`SELECT DISTINCT script_no FROM drugs WHERE customName='${escapeSql(customDrugName)}' AND demographic_no=${demographicNo};`)
    .split('\n').map((r) => r.trim()).filter((r) => /^\d+$/.test(r));
}

function prescriptionSnapshot(scriptNo) {
  const row = sql(`SELECT COALESCE(digital_signature_id,''), COALESCE(date_prescribed,''), COALESCE(provider_no,'') FROM prescription WHERE script_no=${scriptNo};`).trim();
  const [signatureId, datePrescribed, prescriber] = row.split('\t');
  return { signatureId: signatureId || '', datePrescribed: datePrescribed || '', prescriber: prescriber || '' };
}

function prescriptionCount() {
  return Number(sql(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${demographicNo};`).trim() || '0');
}

// --- pharmacy fax fixture ----------------------------------------------------

// Restored by cleanupFixtures(): [{ recordId, originalFax }].
const seededPharmacyFaxes = [];

/**
 * Give the patient's active pharmacies a fax number.
 *
 * ViewScript2.jsp derives `hasFaxNumber` from the pharmacy popForm2 passes through (the preferred
 * pharmacy held in SearchDrug3's #Calcs field), and signatureHandler folds that into the Fax
 * button's state. The demo dataset ships its pharmacies with a blank fax, so without this the pad
 * check would only be re-proving "you cannot fax a pharmacy that has no fax number" and would
 * never exercise the stamp/pad interaction it exists to pin.
 *
 * Every active pharmacy for the patient is seeded rather than just one, because which of them
 * #Calcs holds is a property of the patient's saved preference, not of this check.
 */
function seedPharmacyFax() {
  const rows = sql(`SELECT p.recordId, IFNULL(p.fax,'') FROM pharmacyInfo p
    JOIN demographicPharmacy dp ON dp.pharmacyID = p.recordId
    WHERE dp.demographic_no = ${demographicNo} AND dp.status = '1';`)
    .split('\n').map((r) => r.split('\t')).filter((r) => /^\d+$/.test((r[0] || '').trim()));
  for (const [rawId, rawFax] of rows) {
    const recordId = rawId.trim();
    const originalFax = (rawFax || '').trim();
    if (originalFax) continue;
    sql(`UPDATE pharmacyInfo SET fax = '${FIXTURE_FAX_NUMBER}' WHERE recordId = ${recordId};`);
    seededPharmacyFaxes.push({ recordId, originalFax });
  }
  visited.push({ label: 'pharmacy-fax', seeded: seededPharmacyFaxes.map((r) => r.recordId), total: rows.length });
  return rows.length > 0;
}

// --- cleanup -----------------------------------------------------------------

/**
 * Synchronous and idempotent so it is safe from a signal handler. Each target is attempted
 * independently: one failure must not suppress the rest.
 */
function cleanupFixtures() {
  const attempt = (label, fn) => {
    try { fn(); } catch (e) {
      findings.push({ label: 'cleanup', type: 'cleanup-error', text: `${label}: ${String(e.message || e).slice(0, 200)}` });
    }
  };
  let scripts = [];
  attempt('collect', () => { scripts = createdScriptNos(); });
  if (scripts.length) {
    const list = scripts.join(',');
    attempt('signatures', () => {
      const sigIds = sql(`SELECT DISTINCT digital_signature_id FROM prescription WHERE script_no IN (${list}) AND digital_signature_id IS NOT NULL;`)
        .split('\n').map((r) => r.trim()).filter((r) => /^\d+$/.test(r));
      if (sigIds.length) sql(`DELETE FROM DigitalSignature WHERE id IN (${sigIds.join(',')});`);
    });
    attempt('drugs', () => sql(`DELETE FROM drugs WHERE script_no IN (${list});`));
    attempt('prescription', () => sql(`DELETE FROM prescription WHERE script_no IN (${list});`));
  }
  while (seededPharmacyFaxes.length) {
    const { recordId, originalFax } = seededPharmacyFaxes.pop();
    attempt(`pharmacy-fax ${recordId}`, () => sql(`UPDATE pharmacyInfo SET fax = '${escapeSql(originalFax)}' WHERE recordId = ${recordId};`));
  }
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => { cleanupFixtures(); removeSecretsDir(); process.exit(130); });
}

// --- page wiring -------------------------------------------------------------

// Page errors that are known, pre-existing, and outside this PR's diff. Each entry must name the
// issue tracking it: an unexplained entry here would let a real regression pass unnoticed. They are
// recorded in `visited` so a run still shows them, but they do not fail the check.
const KNOWN_PAGE_ERRORS = [
  {
    // ViewScript2.jsp's printPharmacy() writes into the preview iframe from an async fetch callback
    // without waiting for that iframe to parse, so #pharmInfo is null when the fetch wins the race.
    // Untouched by this branch; surfaced here only because this is the first check to exercise a
    // patient whose preferred pharmacy is populated.
    issue: 3578,
    match: (message, stack) => /setting 'innerHTML'/.test(message) && /expandPreview|reducePreview/.test(stack),
  },
];

function wirePage(page, label) {
  page.on('pageerror', (error) => {
    const message = String(error.message || error);
    const stack = String(error.stack || '');
    const known = KNOWN_PAGE_ERRORS.find((k) => k.match(message, stack));
    if (known) {
      visited.push({ label, type: 'known-pageerror', issue: known.issue, text: message.slice(0, 200) });
      return;
    }
    const where = stack.split('\n').slice(1, 4).join(' | ');
    findings.push({ label, type: 'pageerror', text: `${message}${where ? ` @ ${where}` : ''}`.slice(0, 500) });
  });
  page.on('dialog', (dialog) => {
    // Only the custom-drug confirm() is expected, and only while the flag is set. Any other
    // dialog is a blocking finding: a check must never pass while silently dismissing an alert.
    if (expectingCustomDrugConfirm && dialog.type() === 'confirm') {
      dialog.accept().catch(() => {});
      return;
    }
    findings.push({ label, type: 'unexpected-dialog', text: `${dialog.type()}: ${dialog.message()}`.slice(0, 300) });
    dialog.dismiss().catch(() => {});
  });
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await gotoApp(page, '/');
  // Defence in depth: the unauthenticated login page must not disclose the build identity.
  const loginHtml = await page.content();
  if (/carlos-emr-deb|\d{4}\.\d{2}\.\d+[-~]?\w*\s*\(/.test(loginHtml)) {
    findings.push({ label: 'login-build', type: 'build-disclosed', text: 'login page appears to disclose a build stamp' });
  }
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
  return page;
}

// --- checks ------------------------------------------------------------------

/**
 * The About page is the only surface that shows the build stamp. With
 * RX_EXPECTED_BUILD_TAG set this asserts the exact packaged string, which proves the whole
 * carlos-build.properties -> BuildInfo -> CarlosProperties.getBuildTag() chain survived packaging.
 */
async function checkBuildStamp(context) {
  const page = await context.newPage();
  wirePage(page, 'about');
  await gotoApp(page, '/encounter/ViewAbout');
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  const text = (await page.locator('.build_info').innerText().catch(() => '')).trim();
  visited.push({ label: 'about-build-info', text });
  if (/\$\{/.test(text)) {
    findings.push({ label: 'about-build', type: 'placeholder', text });
  }
  if (/unknown/i.test(text)) {
    findings.push({ label: 'about-build', type: 'unknown-stamp', text: `About page reports an unknown build: "${text}"` });
  }
  if (!/\d{4}\.\d{2}\.\d/.test(text)) {
    findings.push({ label: 'about-build', type: 'missing-version', text: `About page did not show a build version: "${text}"` });
  }
  if (expectedBuildTag && !text.includes(expectedBuildTag)) {
    findings.push({
      label: 'about-build',
      type: 'wrong-stamp',
      text: `About page build tag "${text}" does not contain the expected packaged tag "${expectedBuildTag}"`,
    });
  }
  await page.close();
}

/** Write one script through the real controls; returns its script number and the modal frame. */
async function writeScriptThroughUi(page) {
  await gotoApp(page, `/rx/choosePatient?demographicNo=${demographicNo}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'rx-module', url: page.url() });

  await page.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('#searchString').fill(customDrugName);
  expectingCustomDrugConfirm = true;
  try {
    await page.locator('#customDrug').click();
  } finally {
    expectingCustomDrugConfirm = false;
  }
  await page.locator("[id^='drugName_'], [id^='quantity_']").first().waitFor({ state: 'attached', timeout: 30000 });
  await page.locator('#saveButton').click();

  const modalFrame = page.frameLocator('#carlosModalBody iframe');
  await modalFrame.locator('#faxButton').waitFor({ state: 'attached', timeout: 30000 });

  const scripts = createdScriptNos();
  if (!scripts.length) throw new Error('no prescription row was created for the fixture custom drug');
  const scriptId = String(Math.max(...scripts.map(Number)));
  visited.push({ label: 'script-created', scriptId, count: scripts.length });
  return { modalFrame, scriptId, createdCount: scripts.length };
}

/**
 * Reveal the prescription-history list the way an operator does.
 *
 * SearchDrug3.jsp keeps the reprint list in a cell that starts `display: none` and is toggled by
 * the "Reprint" link in the drug-profile section head. That link only renders for a provider with
 * `_rx` write access, so its absence is itself meaningful: a read-only provider is never offered
 * the reprint route at all.
 *
 * @returns true when the list is visible, false when the toggle is not on the page.
 */
async function openReprintPanel(page) {
  const panel = page.locator('#reprint');
  if (await panel.isVisible().catch(() => false)) return true;
  const toggle = page.locator('a[onclick*="getElementById(\'reprint\')"]').first();
  if (!(await toggle.count())) return false;
  await toggle.click();
  await panel.waitFor({ state: 'visible', timeout: 15000 }).catch(() => {});
  return await panel.isVisible().catch(() => false);
}

/**
 * Reprint the script through the drug-profile link an operator clicks
 * (SearchDrug3.jsp renders <a onclick="reprint2('<script_no>')">), then assert the reprint wrote
 * nothing: no extra prescription row, and the reprinted script's own signature and date untouched.
 */
async function checkReprintIsReadOnly(page, scriptId) {
  const before = prescriptionSnapshot(scriptId);
  const countBefore = prescriptionCount();

  await gotoApp(page, `/rx/choosePatient?demographicNo=${demographicNo}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  if (!(await openReprintPanel(page))) {
    findings.push({ label: 'reprint', type: 'no-reprint-toggle', text: 'the drug profile offered no Reprint toggle, so the reprint route could not be exercised' });
    return;
  }
  const reprintLink = page.locator(`#reprint a[onclick*="reprint2('${scriptId}')"]`).first();
  const haveLink = await reprintLink.count();
  if (!haveLink) {
    findings.push({ label: 'reprint', type: 'no-link', text: `no reprint link for script ${scriptId} in the prescription history` });
    return;
  }
  await reprintLink.click();
  // reprint2 posts, then popForm2 opens ViewScript2 in the modal.
  const modalFrame = page.frameLocator('#carlosModalBody iframe');
  await modalFrame.locator('#faxButton').waitFor({ state: 'attached', timeout: 30000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});

  const after = prescriptionSnapshot(scriptId);
  const countAfter = prescriptionCount();
  visited.push({ label: 'reprint', scriptId, countBefore, countAfter });

  if (countAfter !== countBefore) {
    findings.push({
      label: 'reprint', type: 'duplicate-prescription',
      text: `reprinting script ${scriptId} changed the prescription count for the patient from ${countBefore} to ${countAfter}`,
    });
  }
  if (after.signatureId !== before.signatureId) {
    findings.push({
      label: 'reprint', type: 're-signed',
      text: `reprinting script ${scriptId} changed its digital_signature_id from "${before.signatureId}" to "${after.signatureId}"`,
    });
  }
  if (after.datePrescribed !== before.datePrescribed) {
    findings.push({
      label: 'reprint', type: 'date-changed',
      text: `reprinting script ${scriptId} changed date_prescribed from "${before.datePrescribed}" to "${after.datePrescribed}"`,
    });
  }
}

/**
 * The confirmed review defect: re-prescribing leaves an UNSAVED stash item carrying the ORIGINAL
 * script_no, and a reprint immediately afterwards used to treat that stash as "already persisted"
 * — skipping the save and stamping the historical prescription instead. Drive both steps through
 * the page and assert the historical script is byte-identical afterwards.
 */
async function checkRePrescribeThenReprint(page, scriptId) {
  const before = prescriptionSnapshot(scriptId);
  const countBefore = prescriptionCount();

  await gotoApp(page, `/rx/choosePatient?demographicNo=${demographicNo}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  // drugs' primary key column is `drugid` (not drug_id), and ListDrugs.jsp renders the
  // re-prescribe checkbox as #reRxCheckBox_<drugid>.
  const drugId = sql(`SELECT drugid FROM drugs WHERE script_no=${scriptId} ORDER BY drugid DESC LIMIT 1;`).trim();
  if (!/^\d+$/.test(drugId)) {
    findings.push({ label: 'represcribe', type: 'no-drug-row', text: `no drugs row found for script ${scriptId}` });
    return;
  }

  // The operator journey: tick ReRx in the drug profile, then confirm with "Stage medication"
  // in the #reRxConfirmBox that appears. That POSTs represcribeMultiple, which builds in-memory
  // stash items carrying the ORIGINAL script_no with no drugs row — the exact state the defect fed on.
  const checkbox = page.locator(`#reRxCheckBox_${drugId}`);
  if (!(await checkbox.count())) {
    findings.push({ label: 'represcribe', type: 'no-checkbox', text: `no re-prescribe checkbox for drug ${drugId}; the drug profile may not list it` });
    return;
  }
  await checkbox.check().catch(() => {});
  const stageButton = page.locator('#reRxConfirmBox input[name="stage"]');
  if (!(await stageButton.count())) {
    findings.push({ label: 'represcribe', type: 'no-stage-button', text: 'the ReRx confirm box did not offer a stage control' });
    return;
  }
  await stageButton.click();
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  // The staging must actually have happened, or the reprint below would prove nothing.
  const stagedCount = await page.locator("[id^='drugName_'], [id^='quantity_']").count();
  visited.push({ label: 'represcribe-staged', drugId, stagedCount });
  if (!stagedCount) {
    findings.push({
      label: 'represcribe', type: 'not-staged',
      text: `re-prescribing drug ${drugId} staged nothing, so the reprint that follows cannot exercise the unsaved-stash path`,
    });
    return;
  }

  // Now reprint the SAME historical script while that unsaved stash is live.
  const panelOpen = await openReprintPanel(page);
  const reprintLink = page.locator(`#reprint a[onclick*="reprint2('${scriptId}')"]`).first();
  if (!panelOpen || !(await reprintLink.count())) {
    // Without the reprint the assertions below would compare an untouched row against itself and
    // report success, so treat an unreachable reprint as a failure rather than a skip.
    findings.push({
      label: 'represcribe-then-reprint', type: 'reprint-unreachable',
      text: `could not reach the reprint link for script ${scriptId} while a re-prescribed stash was live`,
    });
    return;
  }
  await reprintLink.click();
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});

  const after = prescriptionSnapshot(scriptId);
  const countAfter = prescriptionCount();
  visited.push({ label: 'represcribe-then-reprint', scriptId, countBefore, countAfter });

  if (after.signatureId !== before.signatureId) {
    findings.push({
      label: 'represcribe-then-reprint', type: 'historical-script-signed',
      text: `after re-prescribe + reprint, script ${scriptId} digital_signature_id changed from "${before.signatureId}" to "${after.signatureId}" — a historical prescription was re-signed`,
    });
  }
  if (after.datePrescribed !== before.datePrescribed) {
    findings.push({
      label: 'represcribe-then-reprint', type: 'historical-script-touched',
      text: `after re-prescribe + reprint, script ${scriptId} date_prescribed changed from "${before.datePrescribed}" to "${after.datePrescribed}"`,
    });
  }
  if (countAfter > countBefore + 1) {
    findings.push({
      label: 'represcribe-then-reprint', type: 'unexpected-rows',
      text: `re-prescribe + reprint added ${countAfter - countBefore} prescription rows (expected at most 1)`,
    });
  }
}

/**
 * With the stamp applied the pad and an enabled Fax button coexist. A stroke or Clear on the pad
 * must not grey out a script the server would still fax from its stored signature.
 */
async function checkStampSurvivesPadActivity(modalFrame, scriptId) {
  // Recorded because both assertions below are only meaningful when the page believes it has
  // somewhere to fax to; a false value here explains a disabled button without implicating the stamp.
  const pageFaxState = await modalFrame.locator('body').evaluate(() => ({
    hasFaxNumber: typeof window.hasFaxNumber === 'undefined' ? null : !!window.hasFaxNumber,
    hasStoredSignature: typeof window.hasStoredSignature === 'undefined' ? null : !!window.hasStoredSignature,
    canFaxScript: typeof window.canFaxScript === 'undefined' ? null : !!window.canFaxScript,
  })).catch(() => ({ hasFaxNumber: null, hasStoredSignature: null, canFaxScript: null }));
  visited.push({ label: 'pad', scriptId, pageFaxState });
  if (pageFaxState.hasFaxNumber === false) {
    findings.push({
      label: 'pad', type: 'no-fax-number',
      text: 'ViewScript2 rendered hasFaxNumber=false, so the pad assertions would not isolate the stamp; the pharmacy fixture did not take effect',
    });
    return;
  }
  if (pageFaxState.hasStoredSignature === false) {
    findings.push({
      label: 'pad', type: 'stamp-not-seen-by-page',
      text: `ViewScript2 rendered hasStoredSignature=false for stamp-signed script ${scriptId}`,
    });
    return;
  }
  const faxButton = modalFrame.locator('#faxButton');
  const enabledBefore = !(await faxButton.isDisabled().catch(() => true));
  visited.push({ label: 'pad', scriptId, faxEnabledBefore: enabledBefore });
  if (!enabledBefore) {
    findings.push({ label: 'pad', type: 'fax-disabled', text: `Fax button was already disabled on stamp-signed script ${scriptId}` });
    return;
  }
  if (!(await modalFrame.locator('#signatureFrame').count())) {
    findings.push({ label: 'pad', type: 'no-pad', text: 'signature pad iframe not offered on a stamp-signed script' });
    return;
  }
  // ViewScript2.jsp reacts to the pad through signatureHandler(e), and TabletSignature.js raises
  // exactly this with isSave=false on a stroke (OnSignEvent(false,true)) and on Clear
  // (OnSignEvent(false,false)). Calling it directly sends the same signal the pad sends while
  // staying independent of the pad's internal canvas geometry.
  //
  // The call MUST be observed to happen: if the handler were missing, doing nothing and then
  // asserting the button is still enabled would pass vacuously on a real regression.
  const invoked = await modalFrame.locator('body').evaluate(() => {
    if (typeof window.signatureHandler !== 'function') return false;
    window.signatureHandler({ target: { onbeforeunload: null }, isSave: false, isDirty: true });
    return true;
  }).catch((e) => String(e && e.message ? e.message : e));
  if (invoked !== true) {
    findings.push({
      label: 'pad', type: 'handler-not-invoked',
      text: `could not raise the pad's signatureHandler on script ${scriptId}, so the Fax-stays-enabled assertion would prove nothing: ${invoked}`,
    });
    return;
  }
  await modalFrame.locator('#faxButton').waitFor({ state: 'attached', timeout: 10000 }).catch(() => {});
  const enabledAfter = !(await faxButton.isDisabled().catch(() => true));
  visited.push({ label: 'pad', faxEnabledAfter: enabledAfter });
  if (!enabledAfter) {
    findings.push({
      label: 'pad', type: 'fax-disabled-after-pad',
      text: `Fax became disabled after pad activity on stamp-signed script ${scriptId}, although the stored stamp still signs the fax`,
    });
  }
}

// --- driver ------------------------------------------------------------------

async function runChecks(context) {
  const page = await context.newPage();
  wirePage(page, 'rx-reprint');
  try {
    await checkBuildStamp(context);

    if (!seedPharmacyFax()) {
      findings.push({
        label: 'pharmacy-fax', type: 'no-active-pharmacy',
        text: `patient ${demographicNo} has no active pharmacy, so ViewScript2 renders hasFaxNumber=false and the Fax-button assertions cannot be trusted`,
      });
    }

    const { modalFrame, scriptId, createdCount } = await writeScriptThroughUi(page);
    if (createdCount !== 1) {
      findings.push({ label: 'save-and-print', type: 'duplicate', text: `one Save And Print created ${createdCount} prescriptions, expected 1` });
    }
    const stamped = prescriptionSnapshot(scriptId);
    if (!/^\d+$/.test(stamped.signatureId)) {
      findings.push({ label: 'stamp', type: 'not-signed', text: `script ${scriptId} carries no stored signature; the stamp was not applied` });
    }

    await checkStampSurvivesPadActivity(modalFrame, scriptId);
    await checkReprintIsReadOnly(page, scriptId);
    await checkRePrescribeThenReprint(page, scriptId);

    return { scriptId, createdCount, storedSignatureId: stamped.signatureId };
  } finally {
    await page.close().catch(() => {});
  }
}

(async () => {
  const args = ['--disable-dev-shm-usage'];
  if (process.env.EFORM_RENDER_ENABLE_CHROMIUM_SANDBOX !== 'true') args.unshift('--no-sandbox');
  const launchOptions = { headless: true, args };
  if (chromePath) launchOptions.executablePath = chromePath;
  const browser = await chromium.launch(launchOptions);
  // The packaged install serves a self-signed certificate on the loopback front door.
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
  let result = null;
  try {
    const loginPage = await login(context);
    await loginPage.close().catch(() => {});
    result = await runChecks(context);
  } finally {
    try { cleanupFixtures(); } finally { removeSecretsDir(); }
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }

  console.log(JSON.stringify({ visited, result, findings }, null, 2));
  if (findings.length) {
    console.error(`FAIL rx-fax-reprint-represcribe: ${findings.length} finding(s)`);
    process.exitCode = 1;
  } else {
    console.log('PASS rx-fax-reprint-represcribe');
  }
})().catch((error) => {
  try { cleanupFixtures(); } finally { removeSecretsDir(); }
  console.error(error);
  process.exit(1);
});
