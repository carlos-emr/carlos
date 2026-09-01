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
 * Everything it creates (prescription, stored signature, fax job, a fax_config
 * account, a throwaway unsigned row) is removed in a finally, so the check is
 * idempotent.
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
const faxNumber = '4165550000';

if (!/^\d+$/.test(demographicNo)) throw new Error(`RX_FAX_DEMOGRAPHIC_NO must be numeric, got ${demographicNo}`);
if (!/^\d+$/.test(providerNo)) throw new Error(`RX_FAX_PROVIDER_NO must be numeric, got ${providerNo}`);

const findings = [];
const visited = [];
const mysqlBin = resolveMysqlBinary();
const mysqlDefaultsFile = createMysqlDefaultsFile();

function validateMysqlHost(host) {
  // This check creates and deletes prescription/signature rows, so it must target a local dev
  // database. Refuse a non-loopback host unless the operator explicitly opts in.
  const loopback = new Set(['localhost', '127.0.0.1', '::1', 'carlos', 'db']);
  if (!loopback.has(host.toLowerCase()) && process.env.ALLOW_NON_LOCAL_MYSQL !== 'true') {
    throw new Error(`Refusing non-local MYSQL_HOST "${host}"; set ALLOW_NON_LOCAL_MYSQL=true for an intentional test database`);
  }
  return host;
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

function createMysqlDefaultsFile() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rx-fax-stamp-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  fs.writeFileSync(file, `[client]\nuser=${mysqlUser}\npassword=${mysqlPassword}\nhost=${mysqlHost}\n`, { mode: 0o600 });
  return file;
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
    const reason = (error && error.code === 'ETIMEDOUT') ? 'timed out' : 'failed';
    throw new Error(`database query ${reason} (first 40 chars: ${String(query).slice(0, 40)})`);
  }
}

function wirePage(page, label) {
  page.on('pageerror', (error) => {
    const text = error.stack || error.message || '';
    // Known-benign legacy noise on the Rx preview: expandPreview runs before its
    // target node exists on some render orders. Matches the shared util's whitelist.
    if (/Cannot set properties of null \(setting 'innerHTML'\)/.test(text) && /expandPreview/.test(text)) {
      return;
    }
    findings.push({ label, type: 'pageerror', text });
  });
  page.on('dialog', async (dialog) => { await dialog.accept().catch(() => {}); });
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
  visited.push({ label: 'login', url: page.url() });
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
  const existing = sql(`SELECT id FROM fax_config WHERE faxNumber='${faxNumber}' LIMIT 1;`).trim();
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
  visited.push({ label: 'rx-search', url: page.url() });

  const rangeStart = Number(sql(`SELECT COALESCE(MAX(script_no),0) FROM prescription WHERE provider_no='${providerNo}' AND demographic_no=${demographicNo};`) || '0');

  // Real control: name the custom medication, then click the "Custom Drug" button.
  await page.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('#searchString').fill('PLAYWRIGHT FAX STAMP TEST');
  await page.locator('#customDrug').click(); // confirm() auto-accepted by the dialog handler
  // The custom drug injects the prescribe fragment and stages the drug.
  await page.locator("[id^='drugName_'], [id^='quantity_']").first().waitFor({ state: 'attached', timeout: 30000 });

  // Real control: "Save And Print" — writes the script and opens ViewScript2 in the modal.
  await page.locator('#saveButton').click();

  // The Bootstrap preview modal loads ViewScript2 in an iframe.
  const modalFrame = page.frameLocator('#carlosModalBody iframe');
  await modalFrame.locator('#faxButton').waitFor({ state: 'attached', timeout: 30000 });

  const maxAfter = Number(sql(`SELECT COALESCE(MAX(script_no),0) FROM prescription WHERE provider_no='${providerNo}' AND demographic_no=${demographicNo};`) || '0');
  if (maxAfter <= rangeStart) {
    throw new Error(`No new prescription row was created (max script_no stayed at ${maxAfter})`);
  }
  // NOTE: "Save And Print" persists the script twice (updateSaveAllDrugs, then RxViewScript2Action
  // opening /rx/viewScript?scriptId=null) — a pre-existing duplicate-prescription behavior. The
  // fax uses the SECOND (shown) row, which is why rangeStart..maxAfter is cleaned up as a range.
  return { modalFrame, scriptId: String(maxAfter), rangeStart };
}

async function runChecks(context) {
  const page = await context.newPage();
  wirePage(page, 'rx-fax-stamp');
  let createdScriptId = null;
  let throwawayUnsignedScriptId = null;
  let faxConfig = null;
  let rxRangeStart = null;
  const faxJobsBefore = Number(sql(`SELECT COALESCE(MAX(id),0) FROM faxes;`) || '0');
  try {
    faxConfig = stageFaxConfig();

    const { modalFrame, scriptId, rangeStart } = await writeCustomRxThroughUi(page);
    createdScriptId = scriptId;
    rxRangeStart = rangeStart;

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

    // Real control: click Fax. Capture the createcustomedpdf request (the JSP puts
    // scriptId on it) and its response.
    const faxRequestPromise = page.waitForRequest((req) => /form\/createcustomedpdf/.test(req.url()) && /__method=oscarRxFax/.test(req.url()), { timeout: 30000 });
    const faxResponsePromise = page.waitForResponse((res) => /form\/createcustomedpdf/.test(res.url()), { timeout: 30000 });
    await modalFrame.locator('#faxButton').click();

    let faxRequest = null;
    let faxBody = '';
    try {
      faxRequest = await faxRequestPromise;
      const faxResponse = await faxResponsePromise;
      faxBody = await faxResponse.text().catch(() => '');
      visited.push({ label: 'fax-request', url: faxRequest.url(), status: faxResponse.status() });
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
      if (!/fax-success/i.test(faxBody) && !/not signed/i.test(faxBody)) {
        // Not fatal on its own (depends on the gateway match), but record it.
        visited.push({ label: 'fax-result', note: 'no explicit fax-success banner', body: faxBody.replace(/\s+/g, ' ').slice(0, 160) });
      }
    }

    // Server-side confirmation that an UNSIGNED script is still refused. The demo
    // signs every prescription, so stage a throwaway unsigned row (the refusal
    // fires before PDF rendering, so it needs no drugs).
    throwawayUnsignedScriptId = sql(
      `INSERT INTO prescription (provider_no, demographic_no, date_prescribed) VALUES ('${providerNo}', ${demographicNo}, NOW()); SELECT LAST_INSERT_ID();`,
    ).trim();
    const unsigned = await page.context().request.get(
      appUrl(`/form/createcustomedpdf?__title=Rx&__method=oscarRxFax&scriptId=${throwawayUnsignedScriptId}`
        + `&pdfId=rxfaxstamp&pharmaFax=4165551212&clinicFax=${faxNumber}&pharmaName=P&demographic_no=${demographicNo}&rxPageSize=PageSize.Letter&rx=x&rxDate=2026-01-01`),
    );
    const unsignedBody = await unsigned.text().catch(() => '');
    visited.push({ label: 'fax-unsigned', status: unsigned.status() });
    if (unsigned.status() >= 500) findings.push({ label: 'fax-gate', type: 'http-500', status: unsigned.status() });
    if (/Signature not found/i.test(unsignedBody)) findings.push({ label: 'fax-gate', type: 'legacy-alert-unsigned', text: 'unsigned fax still shows the old "Signature not found" alert' });
    if (!/not signed/i.test(unsignedBody)) findings.push({ label: 'fax-gate', type: 'not-refused', text: `unsigned fax was not refused: ${unsignedBody.replace(/\s+/g, ' ').slice(0, 160)}` });

    return { createdScriptId, faxDisabled, padPresent, persistedSignatureId: sigId };
  } finally {
    try {
      if (rxRangeStart != null) {
        // Every prescription this run created for the test provider/patient: the shown script, the
        // duplicate from the save-twice flow, and the throwaway unsigned row are all > rangeStart.
        const rows = sql(`SELECT script_no, COALESCE(digital_signature_id,'') FROM prescription WHERE provider_no='${providerNo}' AND demographic_no=${demographicNo} AND script_no>${rxRangeStart};`)
          .split('\n').map((r) => r.trim()).filter(Boolean);
        for (const row of rows) {
          const [scriptNo, sigId] = row.split('\t');
          sql(`DELETE FROM drugs WHERE script_no=${scriptNo};`);
          sql(`DELETE FROM prescription WHERE script_no=${scriptNo};`);
          if (/^\d+$/.test(sigId)) sql(`DELETE FROM DigitalSignature WHERE id=${sigId};`);
        }
      }
      sql(`DELETE FROM faxes WHERE id>${faxJobsBefore};`);
      if (faxConfig && faxConfig.created) sql(`DELETE FROM fax_config WHERE id=${faxConfig.id};`);
    } catch (error) {
      findings.push({ label: 'cleanup', type: 'cleanup-error', text: error.stack || error.message });
    }
    await page.close();
  }
}

(async () => {
  const launchOptions = { headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] };
  if (chromePath) launchOptions.executablePath = chromePath;

  const browser = await chromium.launch(launchOptions);
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
    const loginPage = await login(context);
    await loginPage.close();

    await checkBuildStampOnAboutPage(context);
    const result = await runChecks(context);

    console.log(JSON.stringify({ visited, result, findings }, null, 2));
    const blocking = findings.filter((f) => f.type !== 'dialog');
    if (blocking.length) {
      console.error(`FAIL: ${blocking.length} finding(s)`);
      process.exitCode = 1;
    } else {
      console.log('PASS rx-fax-signature-stamp');
    }
  } finally {
    await browser.close();
    try { fs.rmSync(path.dirname(mysqlDefaultsFile), { recursive: true, force: true }); } catch (error) { /* best effort */ }
  }
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
