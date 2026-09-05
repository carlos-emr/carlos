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
 * Browser regression check for the Rx signature-stamp fax path — driven through
 * the ACTUAL UI a clinician uses, not hand-built requests.
 *
 * A tester on 2026.08.0-alpha10 reported: with Rx signatures enabled the Fax
 * button on the Rx preview is greyed out even though the prescriber's canned
 * signature stamp is on the preview; the only way to fax was to draw a
 * signature (which overwrote the stamp). Two defects combined — nothing
 * persisted the stamp as a stored digital signature (so the Fax gate, which
 * keys off a stored signature, stayed disabled), and the print/fax servlet
 * only ever drew the signature-pad temp file, never the stamp or the stored
 * signature. A follow-up defect: the "Save And Print" flow opens ViewScript2 as
 * /rx/viewScript?scriptId=null, and the page built the fax request from that
 * empty parameter, so a stamp-signed script still faxed as "unsigned".
 *
 * Because those bugs live in the page's own JavaScript (sendFax -> onPrint2 ->
 * the scriptId it puts on the createcustomedpdf request), this check drives the
 * real controls and asserts on the real DOM and the real network request:
 *
 *   1. Log in through the login form. The login page must NOT disclose the
 *      build identity (defence in depth); the build stamp is checked on the
 *      authenticated About page instead.
 *   2. Open the patient's Rx module, click "Custom Drug", then "Save And Print"
 *      — the exact buttons a prescriber clicks. This writes a new script and
 *      opens ViewScript2 in the preview modal.
 *   3. On that ViewScript2, assert the real #faxButton is ENABLED (not greyed)
 *      with no drawn signature, the signature pad is still offered, the script
 *      persisted a PRESCRIPTION stamp signature, and the preview renders it.
 *   4. Click the real Fax button and capture the createcustomedpdf request: its
 *      scriptId must be the real script number (never "" or "null"), and the
 *      response must not be the unsigned refusal or the old "Signature not
 *      found" alert. A server-side direct POST additionally confirms an
 *      unsigned script IS refused.
 *
 * Every database row it creates (prescription and drugs, stored signature, fax
 * job and its FaxClientLog audit row, a fax_config account, a throwaway unsigned
 * row) is removed in a finally, so the check is idempotent at the database.
 * Files are NOT removed: the fax servlet writes prescription_<pdfId>.pdf under
 * DOCUMENT_DIR and prescription_<pdfId>.pdf/.txt under fax_file_location on the
 * install, and this check runs through HTTP and MySQL only. The pdfId is
 * <providerNo><millis>, so each run leaves one small PDF (plus the pair in the
 * fax spool, which the fax scheduler consumes) — see
 * docs/ui-tests/deb-install-validation.md §6.
 *
 * Prerequisites the packaged install must satisfy (see
 * docs/ui-tests/deb-install-validation.md §6):
 *   - rx_fax_enabled=true and rx_signature_enabled=true in carlos.properties,
 *   - the session facility has digital signatures enabled (demo default),
 *   - a provider stamp PNG consult_sig_<provider>.png in the eForm image dir.
 *
 * Env contract:
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (verify the stored signature, stage the
 *   fax_config and unsigned fixtures, and clean up).
 * Optional:
 *   RX_FAX_DEMOGRAPHIC_NO (default 1), RX_FAX_PROVIDER_NO (default 999998),
 *   CHROME_PATH, ALLOW_NON_LOCAL_BASE_URL.
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

const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || 'localhost');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || '';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
// Per-run identifiers so a concurrent (or crashed-then-rerun) invocation of this check is not
// correlated with — or has its rows deleted by — another run.
// fax_config.faxNumber/faxReply are varchar(10) and the servlet matches the staged account by exact
// string equality, so the "from" number MUST be exactly 10 chars: '416' + a 7-digit random keeps it
// there while giving a 10-million-value space, making a same-number collision between two concurrent
// runs negligible. The drug name (customName is varchar(60)) carries the full timestamp + suffix.
const runFaxSuffix = String(randomInt(1000000, 10000000)); // 7 digits (crypto RNG; CodeQL-clean)
// Destination number staged on the patient's pharmacy. Clicking Fax QUEUES A JOB against this
// number, so it must be unroutable: NPA 555 is not assignable in the NANP, so 555-xxx-xxxx can
// never reach a real fax machine. Ten digits after the servlet strips non-digits (it requires at
// least seven), and unique per run so cleanup restores only this run's fixture and never one a
// concurrent run is still using.
const pharmacyFaxNumber = `555${runFaxSuffix}`;
const faxNumber = `416${runFaxSuffix}`; // 10 chars — fits fax_config.faxNumber varchar(10)
// Per-run-unique custom-drug name for this fixture. It lands in drugs.customName, which lets the
// checks identify exactly the prescription(s) THIS run created — immune to a concurrent prescription
// for the same provider/patient, and to leftovers from an earlier crashed run — instead of inferring
// from MAX(script_no).
const customDrugName = `PW FAX STAMP ${Date.now()}${runFaxSuffix}`;

if (!/^\d+$/.test(demographicNo)) throw new Error(`RX_FAX_DEMOGRAPHIC_NO must be numeric, got ${demographicNo}`);
if (!/^\d+$/.test(providerNo)) throw new Error(`RX_FAX_PROVIDER_NO must be numeric, got ${providerNo}`);

const findings = [];
const visited = [];
// True only while clicking the custom-drug button, the one moment a confirm() is expected. The
// page dialog handler auto-accepts a confirm only in this window and records any other dialog.
let expectingCustomDrugConfirm = false;
const mysqlBin = resolveMysqlBinary();
const mysqlDefaultsFile = createMysqlDefaultsFile();

function validateMysqlHost(host) {
  // This check creates and deletes prescription/signature rows, so it must target a local dev
  // database. Refuse a non-loopback host unless the operator explicitly opts in.
  const loopback = new Set(['localhost', '127.0.0.1', '::1', 'carlos', 'db']);
  if (!loopback.has(host.toLowerCase()) && process.env.ALLOW_NON_LOCAL_MYSQL_HOST !== 'true') {
    throw new Error(`Refusing non-local MYSQL_HOST "${host}"; set ALLOW_NON_LOCAL_MYSQL_HOST=true for an intentional test database`);
  }
  return host;
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  // Reject userinfo (user:pass@host): this harness logs navigations and diagnostics with the URL,
  // so embedded credentials would leak into test output. Pass auth through the app's login form.
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not embed a username or password');
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

function appUrl(relativePath) {
  if (!relativePath.startsWith('/') || relativePath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${relativePath}`);
  }
  const parsedPath = new URL(relativePath, 'http://app.local');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${parsedPath.pathname}`.replace(/\/{2,}/g, '/');
  url.search = parsedPath.search;
  return url.toString();
}

function gotoApp(page, relativePath, options = { waitUntil: 'domcontentloaded', timeout: 30000 }) {
  return page.goto(appUrl(relativePath), options); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts
}

function resolveMysqlBinary() {
  for (const bin of ['mariadb', 'mysql']) {
    try {
      execFileSync(bin, ['--version'], { stdio: 'ignore' });
      return bin;
    } catch (error) {
      // next candidate
    }
  }
  throw new Error('Neither mariadb nor mysql client is on PATH; this check needs one to read/stage/clean rows');
}

/**
 * A value safe to place on the right-hand side of a my.cnf option.
 *
 * Unquoted, a '#' truncates the line and a backslash starts an escape sequence, so an operator
 * password containing either would silently produce a different credential than intended. Kept
 * local to this script, per the repository's convention for self-contained check scripts.
 */
function encodeOptionFileValue(value) {
  return `"${String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

/**
 * A page URL with its query string removed.
 *
 * Rx URLs carry demographicNo and script numbers, which CLAUDE.md classifies as PHI-correlating:
 * they join straight back to a patient record. The path alone is what makes a diagnostic useful.
 */
function safeUrl(rawUrl) {
  try {
    const u = new URL(rawUrl);
    return `${u.origin}${u.pathname}`;
  } catch (error) {
    return '<unparseable url>';
  }
}

function createMysqlDefaultsFile() {
  // A newline in ANY of these injects an extra option into the [client] section, which quoting
  // cannot neutralise — so reject rather than encode.
  for (const [name, value] of [['MYSQL_USER', mysqlUser], ['MYSQL_PASSWORD', mysqlPassword], ['MYSQL_HOST', mysqlHost]]) {
    if (/[\r\n]/.test(value)) {
      throw new Error(`${name} must not contain a newline`);
    }
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rx-fax-stamp-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  try {
    fs.writeFileSync(file, `[client]\nuser=${encodeOptionFileValue(mysqlUser)}\npassword=${encodeOptionFileValue(mysqlPassword)}\nhost=${encodeOptionFileValue(mysqlHost)}\n`, { mode: 0o600 });
  } catch (error) {
    // Never leave a half-written defaults file (it carries the cleartext DB password) behind.
    try { fs.rmSync(dir, { recursive: true, force: true }); } catch (cleanupError) { /* best effort */ }
    throw error;
  }
  return file;
}

/** Best-effort removal of the temp dir holding the cleartext MySQL password. Safe to call twice. */
function removeSecretsDir() {
  try { fs.rmSync(path.dirname(mysqlDefaultsFile), { recursive: true, force: true }); } catch (error) { /* best effort */ }
}

// Fixtures this run seeds into the database. Module-scoped so the same idempotent cleanup runs from
// the normal finally AND from a signal handler, and so an interrupted run cannot leave rows behind.
let throwawayUnsignedScriptId = null;
let faxConfig = null;
// Pharmacy fax numbers this run seeded, restored by cleanupFixtures(): [{ recordId, wasNull }].
const seededPharmacyFaxes = [];

/**
 * Remove every row this run seeded, keyed on its per-run-unique identifiers (customDrugName,
 * faxNumber) plus the explicit throwaway id — never a range — so a concurrent run's data is never
 * touched. Idempotent and synchronous (safe to call from a signal handler); records a finding on
 * failure rather than throwing.
 */
/**
 * Give the patient's active pharmacies a destination fax number.
 *
 * The fax servlet refuses a prescription whose pharmacy has no fax number ("Valid fax number not
 * found"), and ViewScript2.jsp folds the same fact into the Fax button via `hasFaxNumber`. The demo
 * dataset ships its pharmacies with a blank fax, so without this the signed-fax assertion would be
 * measuring the missing pharmacy number rather than the signature gate it exists to pin. Every
 * active pharmacy for the patient is seeded because which one the Rx page carries through is a
 * property of the patient's saved preference, not of this check.
 *
 * Fidelity rules this follows, because it mutates a shared record:
 *   - deleted pharmacy records are never touched. The predicate excludes PharmacyInfo.DELETED
 *     ('0') rather than requiring ACTIVE ('1'): the model defines only those two constants, but
 *     the shipped demo dataset stores '2' on every pharmacy, so requiring '1' would silently
 *     match nothing and disable this fixture instead of protecting anything;
 *   - a NULL fax and an empty-string fax are distinct states, so which one it was is remembered
 *     and restored exactly — writing '' back over a NULL would be a silent schema-level change;
 *   - cleanup restores only while the column still holds THIS run's synthetic number, so a
 *     concurrent run or an operator edit made during the check is never overwritten.
 */
function seedPharmacyFax() {
  const rows = sql(`SELECT p.recordId, IF(p.fax IS NULL, 1, 0), IFNULL(p.fax, '') FROM pharmacyInfo p
    JOIN demographicPharmacy dp ON dp.pharmacyID = p.recordId
    WHERE dp.demographic_no = ${demographicNo} AND dp.status = '1'
      AND (p.status IS NULL OR p.status <> '0');`)
    .split('\n').map((r) => r.split('\t')).filter((r) => /^\d+$/.test((r[0] || '').trim()));
  for (const [rawId, rawWasNull, rawFax] of rows) {
    const recordId = rawId.trim();
    const originalFax = (rawFax || '').trim();
    if (originalFax) continue;
    const wasNull = String(rawWasNull).trim() === '1';
    sql(`UPDATE pharmacyInfo SET fax = '${pharmacyFaxNumber}' WHERE recordId = ${recordId};`);
    seededPharmacyFaxes.push({ recordId, wasNull });
  }
  visited.push({ label: 'pharmacy-fax', seeded: seededPharmacyFaxes.map((r) => r.recordId), active: rows.length });
  if (!rows.length) {
    findings.push({
      label: 'pharmacy-fax', type: 'no-active-pharmacy',
      text: `patient ${demographicNo} has no active pharmacy, so a prescription for them can never be faxed`,
    });
  }
}

function cleanupFixtures() {
  // Each target is deleted in its own try so one failure cannot suppress cleanup of the others;
  // failures are aggregated as findings rather than aborting the sweep.
  const attempt = (label, fn) => {
    try {
      fn();
    } catch (error) {
      findings.push({ label: 'cleanup', type: 'cleanup-error', text: `${label}: ${(error && error.message) || 'failed'}` });
    }
  };
  let ourScriptNos = new Set();
  attempt('list fixture scripts', () => {
    ourScriptNos = new Set(
      sql(`SELECT DISTINCT script_no FROM drugs WHERE customName='${customDrugName}' AND demographic_no=${demographicNo};`)
        .split('\n').map((r) => r.trim()).filter((r) => /^\d+$/.test(r)),
    );
  });
  if (throwawayUnsignedScriptId && /^\d+$/.test(throwawayUnsignedScriptId)) {
    ourScriptNos.add(throwawayUnsignedScriptId);
  }
  for (const scriptNo of ourScriptNos) {
    attempt(`prescription ${scriptNo}`, () => {
      const sigId = sql(`SELECT COALESCE(digital_signature_id,'') FROM prescription WHERE script_no=${scriptNo};`).trim();
      sql(`DELETE FROM drugs WHERE script_no=${scriptNo};`);
      sql(`DELETE FROM prescription WHERE script_no=${scriptNo};`);
      if (/^\d+$/.test(sigId)) sql(`DELETE FROM DigitalSignature WHERE id=${sigId};`);
    });
  }
  // Fax rows on this run's unique staged line, and the fax_config we created.
  attempt('faxes', () => {
    // The Fax click also writes a FaxClientLog audit row keyed to the fax job id, so collect the ids
    // BEFORE deleting the jobs or the audit rows would be orphaned.
    const faxIds = sql(`SELECT id FROM faxes WHERE faxline='${faxNumber}';`)
      .split('\n').map((r) => r.trim()).filter((r) => /^\d+$/.test(r));
    if (faxIds.length) {
      sql(`DELETE FROM FaxClientLog WHERE transactionType='RX' AND faxId IN (${faxIds.map((id) => `'${id}'`).join(',')});`);
    }
    sql(`DELETE FROM faxes WHERE faxline='${faxNumber}';`);
  });
  attempt('fax_config', () => {
    if (faxConfig && faxConfig.created) sql(`DELETE FROM fax_config WHERE id=${faxConfig.id};`);
  });
  while (seededPharmacyFaxes.length) {
    const { recordId, wasNull } = seededPharmacyFaxes.pop();
    attempt(`pharmacy-fax ${recordId}`, () => sql(
      `UPDATE pharmacyInfo SET fax = ${wasNull ? 'NULL' : "''"} `
      + `WHERE recordId = ${recordId} AND fax = '${pharmacyFaxNumber}';`));
  }
}

// On interruption (Ctrl-C / CI termination) run the same idempotent DB cleanup, then remove the
// cleartext-password file, before exiting — so a killed run leaves neither test rows nor the secret.
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => { cleanupFixtures(); removeSecretsDir(); process.exit(130); });
}

function sql(query) {
  try {
    return execFileSync(
      mysqlBin,
      [`--defaults-extra-file=${mysqlDefaultsFile}`, '-N', '-B', mysqlDatabase, '-e', query],
      { encoding: 'utf8', timeout: 30000 },
    ).trim();
  } catch (error) {
    // Do not echo the full command/SQL (it can carry identifiers); surface a bounded reason.
    // Neither the query nor raw stderr may reach the log: this check's queries carry demographic
    // and script numbers, and the mysql client echoes the offending statement back before its
    // ERROR line. Report the reason only.
    const reason = (error && error.code === 'ETIMEDOUT') ? 'timed out' : 'failed';
    throw new Error(`database query ${reason}`);
  }
}

function wirePage(page, label) {
  page.on('pageerror', (error) => {
    const text = error.stack || error.message || '';
    // Known pre-existing defect, tracked by issue #3578: expandPreview writes into the preview
    // iframe from an async fetch callback before that iframe has parsed, so the target node does
    // not exist on some render orders. Named here so the suppression stays auditable — an entry
    // without an issue behind it would let a real regression pass unnoticed.
    if (/Cannot set properties of null \(setting 'innerHTML'\)/.test(text) && /expandPreview/.test(text)) {
      return;
    }
    findings.push({ label, type: 'pageerror', text });
  });
  page.on('dialog', async (dialog) => {
    // Accept ONLY the one confirm() the custom-drug button legitimately raises, and only while we are
    // clicking it (expectingCustomDrugConfirm). Every other dialog — an alert (e.g. the legacy
    // "Signature not found" the stamp fix removes) OR an unexpected confirm (a blocking warning) — is
    // recorded as a BLOCKING finding, so the check cannot pass while a dialog is being dismissed
    // unseen. Dismiss (reject) the unexpected ones so a stray confirm does not proceed.
    if (dialog.type() === 'confirm' && expectingCustomDrugConfirm) {
      await dialog.accept().catch(() => {});
      return;
    }
    findings.push({ label, type: 'unexpected-dialog', text: `${dialog.type()}: ${dialog.message()}`.slice(0, 200) });
    await dialog.dismiss().catch(() => dialog.accept().catch(() => {}));
  });
}

// --- login + build-stamp defence-in-depth guard -----------------------------

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await gotoApp(page, '/');

  // Defence in depth: the login page must not disclose the build identity to an
  // unauthenticated visitor. The #buildInfo container may exist but must be empty.
  const loginBuildInfo = (await page.locator('#buildInfo').innerText().catch(() => '')).trim();
  visited.push({ label: 'login-build-info', text: loginBuildInfo });
  if (/\d{4}\.\d{2}\.\d/.test(loginBuildInfo) || /\$\{/.test(loginBuildInfo)) {
    findings.push({ label: 'login-disclosure', type: 'build-on-login', text: `login page discloses the build: "${loginBuildInfo}"` });
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
  visited.push({ label: 'login', url: safeUrl(page.url()) });
  return page;
}

async function checkBuildStampOnAboutPage(context) {
  const page = await context.newPage();
  wirePage(page, 'about');
  await gotoApp(page, '/encounter/ViewAbout');
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  const text = (await page.locator('.build_info').innerText().catch(() => '')).trim();
  visited.push({ label: 'about-build-info', text });
  if (/\$\{/.test(text)) {
    findings.push({ label: 'about-build', type: 'placeholder', text });
  }
  if (!/\d{4}\.\d{2}\.\d/.test(text)) {
    findings.push({ label: 'about-build', type: 'missing-version', text: `About page did not show a build version: "${text}"` });
  }
  await page.close();
}

// --- DB fixtures -------------------------------------------------------------

function stageFaxConfig() {
  // A fax gateway account so the ViewScript2 "From fax number" select has an
  // option and sendFax() can run; the servlet matches it to create the fax job.
  // Reuse only an ACTIVE SRFAX row — an inactive or MIDDLEWARE row on this number
  // would not populate the select the UI needs, so in that case stage our own.
  const existing = sql(`SELECT id FROM fax_config WHERE faxNumber='${faxNumber}' AND active=1 AND providerType='SRFAX' LIMIT 1;`).trim();
  if (/^\d+$/.test(existing)) return { id: existing, created: false };
  const id = sql(
    `INSERT INTO fax_config (providerType, active, faxNumber, faxReply, accountName, senderEmail, faxUser, siteUser, passwd, faxPasswd, gatewayName, queue, url, download) `
    + `VALUES ('SRFAX', 1, '${faxNumber}', '${faxNumber}', 'Playwright Fax', 'fax@example.ca', 'faxuser', 'siteuser', 'x', 'x', 'srfax', '0', '', 1); SELECT LAST_INSERT_ID();`,
  ).trim();
  return { id, created: true };
}

// --- the real UI journey -----------------------------------------------------

async function writeCustomRxThroughUi(page) {
  await gotoApp(page, `/rx/choosePatient?demographicNo=${demographicNo}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'rx-search', url: safeUrl(page.url()) });

  const rangeStart = Number(sql(`SELECT COALESCE(MAX(script_no),0) FROM prescription WHERE provider_no='${providerNo}' AND demographic_no=${demographicNo};`) || '0');

  // Real control: name the custom medication, then click the "Custom Drug" button.
  await page.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('#searchString').fill(customDrugName);
  // Open the confirm-acceptance window only for this click; the handler records any other dialog.
  expectingCustomDrugConfirm = true;
  try {
    await page.locator('#customDrug').click(); // the custom-drug confirm() is accepted by the handler
  } finally {
    expectingCustomDrugConfirm = false;
  }
  // The custom drug injects the prescribe fragment and stages the drug.
  await page.locator("[id^='drugName_'], [id^='quantity_']").first().waitFor({ state: 'attached', timeout: 30000 });

  // Real control: "Save And Print" — writes the script and opens ViewScript2 in the modal.
  await page.locator('#saveButton').click();

  // The Bootstrap preview modal loads ViewScript2 in an iframe.
  const modalFrame = page.frameLocator('#carlosModalBody iframe');
  await modalFrame.locator('#faxButton').waitFor({ state: 'attached', timeout: 30000 });

  // Identify exactly the prescription(s) this run created by our fixture drug name — not by
  // MAX(script_no), which a concurrent prescription for the same provider/patient could perturb.
  // "Save And Print" now writes exactly ONE prescription (updateSaveAllDrugs persists it and
  // RxViewScript2Action reuses that row instead of re-saving), so this list should have one entry;
  // a stray duplicate from re-saving would show as a second entry and is caught by runChecks.
  const createdScriptNos = sql(`SELECT DISTINCT script_no FROM drugs WHERE customName='${customDrugName}' AND demographic_no=${demographicNo} AND script_no>${rangeStart};`)
    .split('\n').map((r) => r.trim()).filter((r) => /^\d+$/.test(r));
  if (createdScriptNos.length === 0) {
    throw new Error('No new prescription row was created for the fixture custom drug');
  }
  // The shown/faxed script is the newest of them.
  const scriptId = String(Math.max(...createdScriptNos.map(Number)));
  return { modalFrame, scriptId, createdCount: createdScriptNos.length };
}

async function runChecks(context) {
  const page = await context.newPage();
  wirePage(page, 'rx-fax-stamp');
  let createdScriptId = null;
  // throwawayUnsignedScriptId and faxConfig are module-scoped (see cleanupFixtures); assign, not redeclare.
  try {
    faxConfig = stageFaxConfig();
    seedPharmacyFax();

    const { modalFrame, scriptId, createdCount } = await writeCustomRxThroughUi(page);
    createdScriptId = scriptId;

    // One "Save And Print" must create exactly ONE prescription — the duplicate this PR fixes was a
    // SECOND row (updateSaveAllDrugs, then RxViewScript2Action re-saving). createdCount is the number
    // of distinct scripts carrying THIS run's fixture drug name, so an unrelated concurrent
    // prescription is never miscounted as our duplicate and a real duplicate is never missed.
    visited.push({ label: 'prescriptions-created', count: createdCount });
    if (createdCount !== 1) {
      findings.push({ label: 'duplicate-prescription', type: 'count', text: `one Save And Print created ${createdCount} prescriptions, expected 1` });
    }

    // Real DOM: the Fax button must be enabled with no drawn signature.
    const faxDisabled = await modalFrame.locator('#faxButton').isDisabled();
    if (faxDisabled) {
      findings.push({ label: 'fax-button', type: 'greyed', text: 'Fax button is disabled on a stamp-signed new script — the reported defect' });
    }
    const padPresent = (await modalFrame.locator('#signatureFrame').count()) > 0;
    if (!padPresent) {
      findings.push({ label: 'signature-pad', type: 'missing', text: 'Signature pad not offered; the auto-applied stamp cannot be overridden by hand' });
    }

    // The stamp must be persisted as a PRESCRIPTION signature for this patient.
    const sigId = sql(`SELECT COALESCE(digital_signature_id,'') FROM prescription WHERE script_no=${createdScriptId};`).trim();
    if (!/^\d+$/.test(sigId)) {
      findings.push({ label: 'stamp-persisted', type: 'missing', text: `prescription ${createdScriptId} has no digital_signature_id; the stamp was not stored` });
    } else {
      const meta = sql(`SELECT ModuleType, demographicId FROM DigitalSignature WHERE id=${sigId};`).split('\t');
      if (meta[0] !== 'PRESCRIPTION' || meta[1] !== demographicNo) {
        findings.push({ label: 'stamp-persisted', type: 'wrong-scope', text: `signature ${sigId} ModuleType=${meta[0]} demographicId=${meta[1]}` });
      }
    }

    // The preview inside ViewScript2 must render the stored stamp image.
    const previewFrame = modalFrame.frameLocator('#preview');
    const stampImg = previewFrame.locator('#signature');
    await stampImg.waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
    const previewInfo = await stampImg.evaluate((img) => ({ src: img.src, complete: img.complete, w: img.naturalWidth })).catch(() => null);
    visited.push({ label: 'preview', previewInfo });
    if (!previewInfo || !/source=signature_stored/.test(previewInfo.src)) {
      findings.push({ label: 'preview-signature', type: 'not-stored', text: `preview signature is not the stored stamp: ${previewInfo ? previewInfo.src : 'none'}` });
    }

    // Real control: click Fax. Capture the createcustomedpdf request (the JSP puts scriptId on it)
    // and its response. The fax row it inserts lands on this run's unique faxline and is cleaned up
    // by cleanupFixtures.
    const faxRequestPromise = page.waitForRequest((req) => /form\/createcustomedpdf/.test(req.url()) && /__method=oscarRxFax/.test(req.url()), { timeout: 30000 });
    const faxResponsePromise = page.waitForResponse((res) => /form\/createcustomedpdf/.test(res.url()), { timeout: 30000 });
    await modalFrame.locator('#faxButton').click();

    let faxRequest = null;
    let faxBody = '';
    let faxStatus = 0;
    try {
      // Await both together: awaited one after the other, a click that produces no round trip
      // times both out at once and the second, still-unhandled rejection kills the process before
      // the fixture cleanup runs.
      const [request, faxResponse] = await Promise.all([faxRequestPromise, faxResponsePromise]);
      faxRequest = request;
      faxStatus = faxResponse.status();
      faxBody = await faxResponse.text().catch(() => '');
      // Path only: the query carries scriptId and the satellite-clinic block, and this goes to the artifact file.
      visited.push({ label: 'fax-request', url: new URL(faxRequest.url()).pathname + ' (query redacted)', status: faxStatus });
    } catch (error) {
      findings.push({ label: 'fax-click', type: 'no-request', text: `Fax click produced no createcustomedpdf request: ${error.message}` });
    }

    if (faxRequest) {
      const scriptIdParam = new URL(faxRequest.url()).searchParams.get('scriptId');
      // The core regression: the fax request must carry the real script number,
      // not "" (empty parameter) or the literal "null" from popForm2(null).
      if (!/^\d+$/.test(String(scriptIdParam || ''))) {
        findings.push({ label: 'fax-scriptid', type: 'bad-scriptid', text: `fax request scriptId is "${scriptIdParam}", not the saved script number` });
      }
      if (/Signature not found/i.test(faxBody)) {
        findings.push({ label: 'fax-gate', type: 'legacy-alert', text: 'clicking Fax on a stamp-signed script still shows the old "Signature not found" alert' });
      }
      if (/not signed/i.test(faxBody)) {
        findings.push({ label: 'fax-gate', type: 'signed-refused', text: 'clicking Fax on a stamp-signed script was refused as unsigned' });
      }
      // The signed fax must SUCCEED: the servlet writes a fax-success banner on success and a
      // fax-failure banner (or a non-2xx status) on any error. A generic error is a failure of the
      // check, not a note — otherwise a 500 or an unrelated error page would let it pass.
      if (faxStatus < 200 || faxStatus >= 300) {
        findings.push({ label: 'fax-gate', type: 'http-error', status: faxStatus, text: `signed fax returned HTTP ${faxStatus}` });
      } else if (!/fax-success/i.test(faxBody) && !/not signed/i.test(faxBody)) {
        findings.push({ label: 'fax-gate', type: 'not-successful', text: `signed fax did not report fax-success: ${faxBody.replace(/\s+/g, ' ').slice(0, 160)}` });
      }
    }

    // Server-side confirmation that an UNSIGNED script is still refused. The demo
    // signs every prescription, so stage a throwaway unsigned row (the refusal
    // fires before PDF rendering, so it needs no drugs).
    throwawayUnsignedScriptId = sql(
      // lastUpdateDate is NOT NULL with no default, so a strict-mode database rejects an insert that
      // omits it; supply it explicitly so the throwaway row can be staged anywhere.
      `INSERT INTO prescription (provider_no, demographic_no, date_prescribed, lastUpdateDate) VALUES ('${providerNo}', ${demographicNo}, NOW(), NOW()); SELECT LAST_INSERT_ID();`,
    ).trim();
    // Drive the SAME POST the Fax button submits (preview2Form.submit()), from inside the page so
    // it carries the session cookie and, via a real CSRFGuard master token, passes CSRF the way the
    // browser does. GET is CSRF-unprotected here (ProtectedMethods=POST,PUT,DELETE,PATCH), so only a
    // POST proves the signature gate refuses an unsigned script on the actual, CSRF-validated route.
    const unsigned = await page.evaluate(async ({ tokenUrl, postUrl, params }) => {
      const tokenResp = await fetch(tokenUrl, { credentials: 'same-origin' });
      const tokenJs = await tokenResp.text();
      const m = tokenJs.match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
      const token = m ? m[1] : '';
      const resp = await fetch(postUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'X-Requested-With': 'XMLHttpRequest',
          'CSRF-TOKEN': token,
        },
        credentials: 'same-origin',
        body: new URLSearchParams(params).toString(),
      });
      const body = await resp.text().catch(() => '');
      return { status: resp.status, hadToken: token.length > 0, body: body.slice(0, 400) };
    }, {
      tokenUrl: appUrl('/csrfguard'),
      postUrl: appUrl('/form/createcustomedpdf'),
      params: {
        __title: 'Rx', __method: 'oscarRxFax', scriptId: throwawayUnsignedScriptId,
        pdfId: 'rxfaxstamp', pharmaFax: '4165551212', clinicFax: faxNumber, pharmaName: 'P',
        demographic_no: demographicNo, rxPageSize: 'PageSize.Letter', rx: 'x', rxDate: '2026-01-01',
      },
    });
    const unsignedBody = unsigned.body || '';
    visited.push({ label: 'fax-unsigned', status: unsigned.status, hadToken: unsigned.hadToken });
    if (!unsigned.hadToken) findings.push({ label: 'fax-gate', type: 'no-csrf-token', text: 'could not obtain a CSRFGuard token for the unsigned-fax POST' });
    if (unsigned.status >= 500) findings.push({ label: 'fax-gate', type: 'http-500', status: unsigned.status });
    if (/csrf|token/i.test(unsignedBody) && /reject|forbidden|invalid/i.test(unsignedBody)) {
      findings.push({ label: 'fax-gate', type: 'csrf-rejected', text: `unsigned-fax POST was rejected by CSRF, not the signature gate: ${unsignedBody.replace(/\s+/g, ' ').slice(0, 160)}` });
    }
    if (/Signature not found/i.test(unsignedBody)) findings.push({ label: 'fax-gate', type: 'legacy-alert-unsigned', text: 'unsigned fax still shows the old "Signature not found" alert' });
    if (!/not signed/i.test(unsignedBody)) findings.push({ label: 'fax-gate', type: 'not-refused', text: `unsigned fax was not refused: ${unsignedBody.replace(/\s+/g, ' ').slice(0, 160)}` });

    return { createdScriptId, faxDisabled, padPresent, persistedSignatureId: sigId };
  } finally {
    cleanupFixtures();
    await page.close();
  }
}

(async () => {
  const launchOptions = { headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] };
  if (chromePath) launchOptions.executablePath = chromePath;

  const browser = await chromium.launch(launchOptions);
  try {
    // Bypass TLS verification ONLY for an exact loopback target (self-signed local certs). Any other
    // host — including host.docker.internal, a container alias, or a private-range IP — receives the
    // login credentials, so its certificate must be verified. IPv6 hostnames are normalized (Node's
    // URL parser already strips the [...] brackets, but be defensive) before the check.
    const bypassHost = baseUrl.hostname.toLowerCase().replace(/^\[|\]$/g, '');
    const loopbackTarget = new Set(['localhost', '127.0.0.1', '::1']).has(bypassHost);
    const context = await browser.newContext({ ignoreHTTPSErrors: loopbackTarget, viewport: { width: 1440, height: 1000 } });
    const loginPage = await login(context);
    await loginPage.close();

    await checkBuildStampOnAboutPage(context);
    const result = await runChecks(context);

    console.log(JSON.stringify({ visited, result, findings }, null, 2));
    // Every recorded finding is blocking. Expected dialogs (the custom-drug confirm) are accepted
    // silently and never recorded; an unexpected alert is recorded as 'unexpected-dialog' and fails.
    if (findings.length) {
      console.error(`FAIL: ${findings.length} finding(s)`);
      process.exitCode = 1;
    } else {
      console.log('PASS rx-fax-signature-stamp');
    }
  } finally {
    await browser.close();
    removeSecretsDir();
  }
})().catch((error) => {
  console.error(error.stack || error.message);
  removeSecretsDir();
  process.exit(1);
});
