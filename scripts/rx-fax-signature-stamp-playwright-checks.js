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
 * Browser regression check for the Rx signature-stamp fax path.
 *
 * A tester on 2026.08.0-alpha10 reported: with Rx signatures enabled the Fax
 * button on the Rx preview is greyed out even though the prescriber's canned
 * signature stamp is on the preview; the only way to fax was to draw a
 * signature (which overwrote the stamp). Two defects combined — nothing
 * persisted the stamp as a stored digital signature (so the Fax gate, which
 * keys off a stored signature, stayed disabled), and the fax PDF servlet only
 * ever drew the signature-pad temp file, never the stamp or a stored signature.
 *
 * This check drives the packaged install and pins the fix end to end:
 *
 *   1. Write a NEW prescription for a provider who has a stamp on file, through
 *      the real rx write endpoints (choosePatient -> newCustomDrug -> writeScript
 *      updateAndPrint). On the resulting ViewScript2 page it asserts:
 *        - the Fax buttons render ENABLED (not disabled) without any drawn
 *          signature — the exact thing the tester could not get,
 *        - the signature pad is still offered, so the stamp can be overridden,
 *        - the preview signature image loads (the stamp is rendered),
 *        - the persisted prescription row carries a PRESCRIPTION digital
 *          signature id (the stamp was stored, not just painted).
 *   2. Server-side fax gate on FrmCustomedPDFServlet:
 *        - an UNSIGNED script POSTed to oscarRxFax is refused with an explicit
 *          "not signed" message (and never the old "Signature not found"
 *          alert, and never a 500),
 *        - the freshly stamped script is NOT refused (it is faxable).
 *   3. The login page build stamp is the WAR's own build version, with no raw
 *      ${...} placeholder — a lightweight guard for the companion build-stamp
 *      fix (build identity read from carlos-build.properties, not carlos.properties).
 *
 * The prescription (and its stored stamp signature) created in step 1 is
 * deleted in a finally so the check is idempotent.
 *
 * Prerequisites the packaged install must satisfy before this runs (see
 * docs/ui-tests/deb-install-validation.md §6 and the Rx-fax note added there):
 *   - rx_fax_enabled=true and rx_signature_enabled=true in carlos.properties,
 *   - the session facility has digital signatures enabled (demo default),
 *   - a provider stamp PNG consult_sig_<provider>.png in the eForm image dir.
 *
 * Requires the deb-install env contract:
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (to read the persisted signature id and
 *   to clean up the created prescription).
 * Optional:
 *   RX_FAX_DEMOGRAPHIC_NO   (default 1)   patient to prescribe for
 *   RX_FAX_PROVIDER_NO      (default 999998) the logged-in prescriber
 *   RX_FAX_UNSIGNED_SCRIPT_ID (default 45) a script with no stored signature for
 *                                          the unsigned-fax gate check; when it turns
 *                                          out to be signed (the demo signs every
 *                                          prescription) the check stages and then
 *                                          deletes its own throwaway unsigned row
 *   CHROME_PATH, ALLOW_NON_LOCAL_BASE_URL
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const appPath = baseUrl.pathname.replace(/\/$/, '') || '';
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = String(process.env.RX_FAX_DEMOGRAPHIC_NO || '1').trim();
const providerNo = String(process.env.RX_FAX_PROVIDER_NO || '999998').trim();
const unsignedScriptId = String(process.env.RX_FAX_UNSIGNED_SCRIPT_ID || '45').trim();

const mysqlHost = process.env.MYSQL_HOST || 'localhost';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || '';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

if (!/^\d+$/.test(demographicNo)) throw new Error(`RX_FAX_DEMOGRAPHIC_NO must be numeric, got ${demographicNo}`);
if (!/^\d+$/.test(providerNo)) throw new Error(`RX_FAX_PROVIDER_NO must be numeric, got ${providerNo}`);
if (!/^\d+$/.test(unsignedScriptId)) throw new Error(`RX_FAX_UNSIGNED_SCRIPT_ID must be numeric, got ${unsignedScriptId}`);

const findings = [];
const visited = [];
const mysqlBin = resolveMysqlBinary();
const mysqlDefaultsFile = createMysqlDefaultsFile();

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
      // try the next candidate
    }
  }
  throw new Error('Neither mariadb nor mysql client is on PATH; this check needs one to read/clean the prescription rows');
}

function createMysqlDefaultsFile() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rx-fax-stamp-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  // password may be empty under unix_socket auth; a [client] section still works.
  fs.writeFileSync(file, `[client]\nuser=${mysqlUser}\npassword=${mysqlPassword}\nhost=${mysqlHost}\n`, { mode: 0o600 });
  return file;
}

function sql(query) {
  const out = execFileSync(
    mysqlBin,
    [`--defaults-extra-file=${mysqlDefaultsFile}`, '-N', '-B', mysqlDatabase, '-e', query],
    { encoding: 'utf8' },
  );
  return out.trim();
}

function assertNoErrorPage(html, label) {
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report|Security Error/i.test(html)) {
    findings.push({ label, type: 'error-page', body: html.replace(/\s+/g, ' ').slice(0, 400) });
    return false;
  }
  return true;
}

function wirePage(page, label) {
  page.on('response', (response) => {
    const status = response.status();
    const url = response.url();
    // A stored-signature image can legitimately 404 before one is associated.
    const expected404 = status === 404 && /imageRenderingServlet\?source=signature_stored/.test(url);
    if (status >= 400 && !expected404) {
      findings.push({ label, type: 'http', status, url });
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

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await gotoApp(page, '/');
  // Build-stamp guard: the login page must show a real build version, never a
  // raw ${...} placeholder frozen from an override file.
  const buildInfo = (await page.locator('#buildInfo').innerText().catch(() => '')).trim();
  if (/\$\{/.test(buildInfo)) {
    findings.push({ label: 'build-stamp', type: 'placeholder', text: buildInfo });
  }
  if (!/\d{4}\.\d{2}\.\d/.test(buildInfo)) {
    findings.push({ label: 'build-stamp', type: 'missing-version', text: buildInfo });
  }
  visited.push({ label: 'build-stamp', buildInfo });

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

async function readCsrfToken(page) {
  const input = page.locator('input[name="CSRF-TOKEN"]').first();
  if (await input.count() === 0) return '';
  return input.inputValue({ timeout: 5000 }).catch(() => '');
}

/**
 * POST a form to an rx endpoint from inside the page, reusing the session
 * cookie and the CSRF token on the current document. Returns { status, text }.
 */
async function postForm(page, relativePath, fields) {
  const csrfToken = await readCsrfToken(page);
  return page.evaluate(async ({ url, fields, csrfToken }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- inputs are validated/literal and passed as a serialized argument, not interpolated into the function body
    const body = new URLSearchParams();
    Object.entries(fields).forEach(([k, v]) => body.append(k, String(v)));
    if (csrfToken) body.append('CSRF-TOKEN', csrfToken);
    const headers = { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest' };
    if (csrfToken) headers['CSRF-TOKEN'] = csrfToken;
    const response = await fetch(url, { method: 'POST', credentials: 'same-origin', headers, body });
    return { status: response.status, text: await response.text() };
  }, { url: appUrl(relativePath), fields, csrfToken });
}

function today() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/**
 * Writes a fresh custom-drug prescription for the demo patient through the real
 * rx endpoints and returns the parsed ViewScript2 HTML plus the new script id.
 */
async function writeStampedPrescription(page) {
  await gotoApp(page, `/rx/choosePatient?demographicNo=${demographicNo}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'choose-patient', url: page.url() });
  assertNoErrorPage(await page.content(), 'choose-patient');

  const maxBefore = Number(sql(`SELECT COALESCE(MAX(script_no),0) FROM prescription WHERE provider_no='${providerNo}' AND demographic_no=${demographicNo};`) || '0');

  // Add a free-text (custom) drug to the stash via the newCustomDrug handler — it does not perform
  // a DrugRef monograph lookup, so a new prescription can be written independent of drug data.
  const randomId = String(Math.floor(Math.random() * 1_000_000) + 1);
  const choose = await postForm(page, '/rx/WriteScript', {
    parameterValue: 'newCustomDrug',
    name: 'PLAYWRIGHT FAX STAMP TEST',
    randomId,
  });
  if (choose.status !== 200) throw new Error(`newCustomDrug returned HTTP ${choose.status}: ${choose.text.slice(0, 300)}`);

  // Save + print the script: action=updateAndPrint persists it and applies the
  // stamp, returning the ViewScript2 render.
  const write = await postForm(page, '/rx/WriteScript', {
    action: 'updateAndPrint',
    GCN_SEQNO: '0',
    customName: 'PLAYWRIGHT FAX STAMP TEST',
    brandName: '',
    rxDate: today(),
    writtenDate: today(),
    takeMin: '1',
    takeMax: '1',
    frequencyCode: 'OID',
    duration: '30',
    durationUnit: 'D',
    quantity: '30',
    repeat: '0',
    special: 'Take 1 tablet daily for 30 days',
    method: 'Take',
    unit: '',
    unitName: 'tablet',
    atcCode: '',
    regionalIdentifier: '',
    route: '',
    customInstr: 'false',
    dosage: '',
  });
  if (write.status !== 200) throw new Error(`writeScript(updateAndPrint) returned HTTP ${write.status}: ${write.text.slice(0, 300)}`);
  assertNoErrorPage(write.text, 'write-script');

  const maxAfter = Number(sql(`SELECT COALESCE(MAX(script_no),0) FROM prescription WHERE provider_no='${providerNo}' AND demographic_no=${demographicNo};`) || '0');
  if (maxAfter <= maxBefore) {
    throw new Error(`No new prescription row was created (max script_no stayed at ${maxAfter})`);
  }
  return { html: write.text, scriptId: String(maxAfter) };
}

/**
 * Reads the fax-button state and the signature-pad presence out of a
 * ViewScript2 render. The Fax button is disabled server-side by writing the
 * literal ` disabled` attribute into the <input>, so the check inspects that
 * exact tag rather than a live DOM (the HTML came from a fetch, not a nav).
 */
function inspectViewScript(html) {
  const faxButtonTag = (html.match(/<input[^>]*id="faxButton"[^>]*>/i) || [])[0] || '';
  return {
    faxButtonPresent: faxButtonTag !== '',
    faxButtonDisabled: /\bdisabled\b/i.test(faxButtonTag),
    signaturePadPresent: /id="signatureFrame"/i.test(html) || /signature_pad\/tabletSignature/i.test(html),
    previewIframePresent: /id=['"]preview['"]/i.test(html),
  };
}

async function runChecks(context) {
  const page = await context.newPage();
  wirePage(page, 'rx-fax-stamp');
  let createdScriptId = null;
  let throwawayUnsignedScriptId = null;
  try {
    const written = await writeStampedPrescription(page);
    createdScriptId = written.scriptId;

    const view = inspectViewScript(written.html);
    if (!view.faxButtonPresent) {
      throw new Error('ViewScript2 did not render a Fax button (#faxButton); is rx_fax_enabled=true on this install?');
    }
    if (view.faxButtonDisabled) {
      findings.push({ label: 'fax-button', type: 'greyed', text: 'Fax button is disabled on a stamp-signed new script — the reported defect' });
    }
    if (!view.signaturePadPresent) {
      findings.push({ label: 'signature-pad', type: 'missing', text: 'Signature pad not offered; the auto-applied stamp cannot be overridden by hand' });
    }

    // The persisted prescription must carry a stored signature (the stamp), and
    // it must be a PRESCRIPTION signature for this patient.
    const sigId = sql(`SELECT COALESCE(digital_signature_id,'') FROM prescription WHERE script_no=${createdScriptId};`).trim();
    if (!/^\d+$/.test(sigId)) {
      findings.push({ label: 'stamp-persisted', type: 'missing', text: `prescription ${createdScriptId} has no digitalSignatureId; the stamp was not stored` });
    } else {
      const meta = sql(`SELECT ModuleType, demographicId FROM DigitalSignature WHERE id=${sigId};`).split('\t');
      if (meta[0] !== 'PRESCRIPTION' || meta[1] !== demographicNo) {
        findings.push({ label: 'stamp-persisted', type: 'wrong-scope', text: `signature ${sigId} moduleType=${meta[0]} demographicId=${meta[1]}` });
      }
    }

    // Preview signature image must render the stored stamp (drawn on the preview).
    await gotoApp(page, `/rx/ViewPreview2?scriptId=${createdScriptId}&rePrint=`);
    await page.locator('#signature').waitFor({ state: 'attached', timeout: 15000 }).catch(() => {});
    const loaded = await page.waitForFunction(() => {
      const img = document.getElementById('signature');
      return img && img.complete && img.naturalWidth > 0;
    }, { timeout: 15000 }).then(() => true).catch(() => false);
    const preview = await page.evaluate(() => {
      const img = document.getElementById('signature');
      return img ? { src: img.src, complete: img.complete, w: img.naturalWidth } : null;
    });
    visited.push({ label: 'preview', preview, loaded });
    if (!preview) {
      findings.push({ label: 'preview-signature', type: 'missing', text: 'Preview did not render a #signature image' });
    } else if (!/source=signature_stored/.test(preview.src)) {
      findings.push({ label: 'preview-signature', type: 'not-stored', text: `preview signature is not the stored stamp: ${preview.src}` });
    } else if (!loaded) {
      findings.push({ label: 'preview-signature', type: 'not-loaded', text: `stored stamp image did not load (naturalWidth 0): ${preview.src}` });
    }

    // --- server-side fax gate -------------------------------------------------
    const faxFields = (scriptId) => ({
      __method: 'oscarRxFax', __title: 'Rx', scriptId,
      pdfId: `rxfaxstamp${scriptId}`, pharmaFax: '4165551212', clinicFax: '4165553434',
      pharmaName: 'Playwright Pharmacy', demographic_no: demographicNo, rxPageSize: 'PageSize.Letter',
      patientName: 'Test Patient', rx: 'Test', rxDate: today(),
    });

    // Unsigned script: must be refused with an explicit "not signed" message,
    // never the old "Signature not found" alert, never a 500. Prefer the
    // configured fixture when it is genuinely unsigned; otherwise pick any
    // prescription that carries no stored signature (the refusal fires before
    // PDF rendering, so the script needs no drugs).
    let effectiveUnsignedScriptId = unsignedScriptId;
    const configuredSig = sql(`SELECT COALESCE(digital_signature_id,'') FROM prescription WHERE script_no=${unsignedScriptId};`).trim();
    if (/^\d+$/.test(configuredSig)) {
      effectiveUnsignedScriptId = sql(
        `SELECT script_no FROM prescription WHERE digital_signature_id IS NULL AND script_no<>${createdScriptId} ORDER BY script_no LIMIT 1;`,
      ).trim();
    }
    if (!/^\d+$/.test(effectiveUnsignedScriptId)) {
      // The demo dataset signs every prescription, so stage a throwaway unsigned row. The fax
      // refusal fires before PDF rendering, so it needs no drugs; deleted in the finally.
      throwawayUnsignedScriptId = sql(
        `INSERT INTO prescription (provider_no, demographic_no, date_prescribed) VALUES ('${providerNo}', ${demographicNo}, NOW()); SELECT LAST_INSERT_ID();`,
      ).trim();
      effectiveUnsignedScriptId = throwawayUnsignedScriptId;
    }
    if (!/^\d+$/.test(effectiveUnsignedScriptId)) {
      findings.push({ label: 'fax-gate', type: 'fixture', text: 'no unsigned prescription available to exercise the unsigned-fax refusal' });
    } else {
      const unsigned = await postForm(page, '/form/createcustomedpdf', faxFields(effectiveUnsignedScriptId));
      visited.push({ label: 'fax-unsigned', scriptId: effectiveUnsignedScriptId, status: unsigned.status });
      if (unsigned.status >= 500) {
        findings.push({ label: 'fax-gate', type: 'http-500', status: unsigned.status });
      }
      if (/Signature not found/i.test(unsigned.text)) {
        findings.push({ label: 'fax-gate', type: 'legacy-alert', text: 'unsigned fax still shows the old "Signature not found" alert' });
      }
      if (!/not signed/i.test(unsigned.text)) {
        findings.push({ label: 'fax-gate', type: 'not-refused', text: `unsigned fax was not refused: ${unsigned.text.replace(/\s+/g, ' ').slice(0, 200)}` });
      }
    }

    // Stamp-signed script: must NOT be refused as unsigned.
    const signed = await postForm(page, '/form/createcustomedpdf', faxFields(createdScriptId));
    visited.push({ label: 'fax-signed', status: signed.status });
    if (signed.status >= 500) {
      findings.push({ label: 'fax-gate', type: 'http-500-signed', status: signed.status });
    }
    if (/not signed/i.test(signed.text) || /Signature not found/i.test(signed.text)) {
      findings.push({ label: 'fax-gate', type: 'signed-refused', text: 'a stamp-signed script was refused as unsigned' });
    }

    return {
      createdScriptId,
      faxButton: view,
      persistedSignatureId: sigId,
      previewSignature: visited.find((v) => v.label === 'preview'),
    };
  } finally {
    try {
      if (createdScriptId) {
        const sigId = sql(`SELECT COALESCE(digital_signature_id,'') FROM prescription WHERE script_no=${createdScriptId};`).trim();
        sql(`DELETE FROM drugs WHERE script_no=${createdScriptId};`);
        sql(`DELETE FROM prescription WHERE script_no=${createdScriptId};`);
        if (/^\d+$/.test(sigId)) sql(`DELETE FROM DigitalSignature WHERE id=${sigId};`);
      }
      if (/^\d+$/.test(String(throwawayUnsignedScriptId || ''))) {
        sql(`DELETE FROM prescription WHERE script_no=${throwawayUnsignedScriptId};`);
      }
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
