#!/usr/bin/env node
/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 *
 * Rx stamp-signed fax: the document is the RECORD, not the request.
 *
 * Browser regression check for the three record-binding guarantees of the
 * signed prescription fax (FrmCustomedPDFServlet.bindFaxContentToRecord and
 * rx/ViewScript2.jsp, PR #3606). It drives the real UI to write and stamp-sign
 * a prescription, faxes it the way a clinician does, and then reads the PDF the
 * servlet wrote to DOCUMENT_DIR and asserts on the text actually rendered:
 *
 *   A. identity — an oscarRxFax POST carrying a FORGED patientName/HIN/DOB/
 *      address/phone/chartNo for a signed script must fax the prescription's
 *      own demographic, never the forged values. Without the fix the verified
 *      drugs and the prescriber's stored signature went out under whatever
 *      identity the request named.
 *   B. one-character line — a standalone one-character line in a drug's
 *      directions (a dose such as "1") must survive into the faxed PDF.
 *      Without the fix splitRenderedRxBlocks treated any length-1 line as a
 *      block separator and silently dropped it.
 *   C. notes race — a note typed immediately before clicking Fax must appear
 *      in the faxed PDF. The save is deliberately DELAYED here (route
 *      interception) so the race is deterministic: without the fix the fax POST
 *      won and rendered the stored (old) note; with it, the fax waits.
 *   D. clinic header — the clinic name must render on its own line. Preview2.jsp
 *      joined the header's lines with <br> and converted them for the PDF with a
 *      replaceAll whose replacement, after JSP attribute unescaping, was an
 *      escaped literal 'n': every line break became the letter n and the header
 *      rendered glued ("ClinicnAddressnCity") on every faxed prescription.
 *
 * B, C and D are observed on one UI-driven fax; A is a second, direct POST for the
 * same signed script, made from inside the page so it carries the session and a
 * real CSRFGuard token exactly as the browser's own submit does.
 *
 * The check reads the generated PDF from disk (the servlet's only durable
 * output) with a small text-run extractor over the content streams; OpenPDF
 * writes each rendered line as its own `(...) Tj`, so a rendered line is a run.
 * Nothing from the PDF is printed: identity is reported as present/absent.
 *
 * Fixtures this run seeds and REMOVES (keyed on per-run-unique values, never a
 * range, so a concurrent run's rows are never touched): one custom-drug
 * prescription with its drug row and stamp signature, a fax_config account for
 * the "from" line when no active SRFAX row exists on that number, the fax job
 * rows on that line with their FaxClientLog audit rows, and the destination fax
 * number seeded on the patient's active pharmacies (restored exactly, NULL vs
 * '' preserved). Files are NOT removed: two small PDFs are left under
 * DOCUMENT_DIR (prescription_<pdfId>.pdf) plus the pair each leaves in the fax
 * spool, which the fax scheduler consumes.
 *
 * Prerequisites on the install (docs/ui-tests/deb-install-validation.md §6):
 *   - rx_fax_enabled=true and rx_signature_enabled=true in carlos.properties,
 *   - the session facility has digital signatures enabled (demo default),
 *   - a provider stamp PNG consult_sig_<provider>.png in the eForm image dir,
 *   - this process can READ the install's DOCUMENT_DIR.
 *
 * Env contract:
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE,
 *   RX_FAX_DOCUMENT_DIR — the install's DOCUMENT_DIR as seen by this process
 *                         (absolute; on a packaged install
 *                         /var/lib/carlos-emr/CarlosDocument/carlos/document).
 * Optional:
 *   RX_FAX_DEMOGRAPHIC_NO (default 1), RX_FAX_PROVIDER_NO (default 999998),
 *   RX_FAX_NOTES_SAVE_DELAY_MS (default 2500), CHROME_PATH, ARTIFACT_DIR,
 *   ALLOW_NON_LOCAL_BASE_URL.
 *
 * Run: npm run test:rx-fax-record-binding-playwright
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const { randomInt } = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const zlib = require('zlib');
const {
  appUrl,
  buildArtifactPath,
  getLaunchOptions,
  gotoApp,
  validateBaseUrl,
} = require('./eform-local-playwright-utils');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = String(process.env.RX_FAX_DEMOGRAPHIC_NO || '1').trim();
const providerNo = String(process.env.RX_FAX_PROVIDER_NO || '999998').trim();
const notesSaveDelayMs = Number(process.env.RX_FAX_NOTES_SAVE_DELAY_MS || '2500');
const artifactDir = process.env.ARTIFACT_DIR || '/tmp/carlos-playwright-artifacts';
const documentDir = validateDocumentDir(process.env.RX_FAX_DOCUMENT_DIR);

const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || 'localhost');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || '';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

if (!/^\d+$/.test(demographicNo)) throw new Error(`RX_FAX_DEMOGRAPHIC_NO must be numeric, got ${demographicNo}`);
if (!/^\d+$/.test(providerNo)) throw new Error(`RX_FAX_PROVIDER_NO must be numeric, got ${providerNo}`);
if (!Number.isInteger(notesSaveDelayMs) || notesSaveDelayMs < 0 || notesSaveDelayMs > 20000) {
  throw new Error('RX_FAX_NOTES_SAVE_DELAY_MS must be an integer between 0 and 20000');
}

// Per-run identifiers, same rationale as rx-fax-signature-stamp-playwright-checks.js: the "from"
// fax number must be exactly 10 chars (fax_config.faxNumber varchar(10), matched by equality), the
// destination must be unroutable (NPA 555), and the drug name identifies THIS run's prescription.
const runSuffix = String(randomInt(1000000, 10000000)); // 7 digits, crypto RNG
const pharmacyFaxNumber = `555${runSuffix}`;
const faxNumber = `416${runSuffix}`;
const customDrugName = `PW FAX BIND ${Date.now()}${runSuffix}`;
// Probes. Each is a string that cannot occur in a legitimate render of the demo data, so presence
// or absence in the PDF is unambiguous. The one-character probe is a letter rather than a digit
// because page and count fields legitimately render single digits.
const probeLine = 'Z';
const probeLineContext = `PW PROBE ${runSuffix}`;
const noteText = `PW NOTE ${runSuffix}`;
const forged = {
  patientName: `ZQFORGEDNAME${runSuffix}`,
  patientHIN: '9999999999',
  patientDOB: 'Jan 1, 1900',
  patientAddress: `999 FORGED AVE ${runSuffix}`,
  patientCityPostal: 'FORGEDVILLE ZZ 0Z0 0Z0',
  patientPhone: 'Tel: 4165559999',
  patientChartNo: `FORGEDCHART${runSuffix}`,
};

const findings = [];
const visited = [];
let expectingCustomDrugConfirm = false;
const mysqlBin = resolveMysqlBinary();
const mysqlDefaultsFile = createMysqlDefaultsFile();

// --- validation helpers -------------------------------------------------------

function validateMysqlHost(host) {
  if (!/^[A-Za-z0-9.\-:\[\]]+$/.test(host)) {
    throw new Error('MYSQL_HOST contains unsupported characters');
  }
  return host;
}

function validateDocumentDir(rawDir) {
  if (!rawDir || typeof rawDir !== 'string') {
    throw new Error('RX_FAX_DOCUMENT_DIR is required: the install\'s DOCUMENT_DIR as seen by this process');
  }
  if (!path.isAbsolute(rawDir)) throw new Error('RX_FAX_DOCUMENT_DIR must be an absolute path');
  const resolved = path.resolve(rawDir); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- operator-supplied read-only directory, checked to exist and be a directory; file names under it are built from a digits-validated pdfId
  let stat;
  try { stat = fs.statSync(resolved); } catch (error) { throw new Error('RX_FAX_DOCUMENT_DIR does not exist or is not readable'); }
  if (!stat.isDirectory()) throw new Error('RX_FAX_DOCUMENT_DIR is not a directory');
  return resolved;
}

function resolveMysqlBinary() {
  for (const candidate of ['/usr/bin/mysql', '/usr/local/bin/mysql', '/usr/bin/mariadb', '/opt/homebrew/bin/mysql']) {
    if (fs.existsSync(candidate)) return candidate;
  }
  throw new Error('mysql client not found (looked in /usr/bin, /usr/local/bin, /opt/homebrew/bin)');
}

function encodeOptionFileValue(value) {
  // MySQL option files take double-quoted values with backslash escapes.
  return `"${String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function createMysqlDefaultsFile() {
  for (const [name, value] of [['MYSQL_USER', mysqlUser], ['MYSQL_PASSWORD', mysqlPassword], ['MYSQL_HOST', mysqlHost]]) {
    if (/[\r\n]/.test(value)) throw new Error(`${name} must not contain a newline`);
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rx-fax-bind-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  try {
    fs.writeFileSync(file, `[client]\nuser=${encodeOptionFileValue(mysqlUser)}\npassword=${encodeOptionFileValue(mysqlPassword)}\nhost=${encodeOptionFileValue(mysqlHost)}\n`, { mode: 0o600 });
  } catch (error) {
    try { fs.rmSync(dir, { recursive: true, force: true }); } catch (cleanupError) { /* best effort */ }
    throw error;
  }
  return file;
}

function removeSecretsDir() {
  try { fs.rmSync(path.dirname(mysqlDefaultsFile), { recursive: true, force: true }); } catch (error) { /* best effort */ }
}

function sql(query) {
  try {
    return execFileSync(
      mysqlBin,
      [`--defaults-extra-file=${mysqlDefaultsFile}`, '-N', '-B', mysqlDatabase, '-e', query],
      { encoding: 'utf8', timeout: 30000 },
    ).trim();
  } catch (error) {
    // Neither the query nor raw stderr may reach the log: queries carry demographic and script
    // numbers and the client echoes the statement before its ERROR line.
    const reason = (error && error.code === 'ETIMEDOUT') ? 'timed out' : 'failed';
    throw new Error(`database query ${reason}`);
  }
}

function safeUrl(rawUrl) {
  try {
    const u = new URL(rawUrl);
    return `${u.pathname}${u.search ? ' (query redacted)' : ''}`;
  } catch (error) {
    return '(unparseable url)';
  }
}

// --- PDF text runs --------------------------------------------------------------

/**
 * Every string shown by a `Tj` / `TJ` operator in every content stream of the PDF, in stream order.
 * OpenPDF (the servlet's writer) positions each rendered line with `Tm` and shows it with one `Tj`,
 * so one rendered line is one run. Handles FlateDecode streams and the ()-string escapes; that is
 * all this writer emits. Not a general PDF text extractor and not meant to be one.
 */
function pdfTextRuns(buf) {
  const runs = [];
  const text = buf.toString('latin1');
  const streamRe = /(<<(?:(?!<<)[\s\S]){0,400}?>>)\s*stream\r?\n/g;
  let m;
  while ((m = streamRe.exec(text))) {
    const dict = m[1];
    const start = m.index + m[0].length;
    const end = text.indexOf('endstream', start);
    if (end < 0) break;
    let data = buf.subarray(start, end);
    if (/FlateDecode/.test(dict)) {
      let inflated = null;
      // The stream may carry a trailing EOL before `endstream`; try the exact length first.
      for (const cut of [0, 1, 2]) {
        try { inflated = zlib.inflateSync(buf.subarray(start, end - cut)); break; } catch (error) { /* try shorter */ }
      }
      if (!inflated) continue;
      data = inflated;
    }
    const content = data.toString('latin1');
    if (!/\bT[jJ]\b/.test(content)) continue;
    const opRe = /\((?:\\.|[^\\)])*\)\s*Tj|\[(?:\((?:\\.|[^\\)])*\)|[^\]])*\]\s*TJ/g;
    let op;
    while ((op = opRe.exec(content))) {
      const parts = [];
      const strRe = /\(((?:\\.|[^\\)])*)\)/g;
      let s;
      while ((s = strRe.exec(op[0]))) {
        // PDF literal-string escapes (ISO 32000 7.3.4.2): \n \r \t \b \f, the three delimiters,
        // and \ddd octal. Decoding them here keeps a run's text equal to what was rendered.
        parts.push(s[1].replace(/\\(\d{1,3}|[nrtbf()\\])/g, (_, esc) => {
          switch (esc) {
            case 'n': return '\n';
            case 'r': return '\r';
            case 't': return '\t';
            case 'b': return '\b';
            case 'f': return '\f';
            case '(': case ')': case '\\': return esc;
            default: return String.fromCharCode(parseInt(esc, 8));
          }
        }));
      }
      runs.push(parts.join(''));
    }
  }
  return runs;
}

/** The servlet's PDF for this pdfId, once it is fully written (exists, %PDF header, size stable). */
async function waitForPdf(pdfId, label) {
  if (!/^[A-Za-z0-9]{1,64}$/.test(pdfId)) {
    throw new Error(`${label}: pdfId is not a plain identifier`);
  }
  const file = path.join(documentDir, `prescription_${pdfId}.pdf`); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- documentDir is a validated operator-supplied directory and pdfId is restricted to [A-Za-z0-9]
  const deadline = Date.now() + 20000;
  let lastSize = -1;
  while (Date.now() < deadline) {
    try {
      const stat = fs.statSync(file);
      if (stat.size > 0 && stat.size === lastSize) {
        const buf = fs.readFileSync(file);
        if (buf.subarray(0, 4).toString() === '%PDF') return buf;
      }
      lastSize = stat.size;
    } catch (error) {
      lastSize = -1;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`${label}: prescription_<pdfId>.pdf did not appear under RX_FAX_DOCUMENT_DIR within 20s`);
}

// --- fixtures ---------------------------------------------------------------------

let faxConfig = null;
const seededPharmacyFaxes = [];

function stageFaxConfig() {
  const existing = sql(`SELECT id FROM fax_config WHERE faxNumber='${faxNumber}' AND active=1 AND providerType='SRFAX' LIMIT 1;`).trim();
  if (/^\d+$/.test(existing)) return { id: existing, created: false };
  const id = sql(
    `INSERT INTO fax_config (providerType, active, faxNumber, faxReply, accountName, senderEmail, faxUser, siteUser, passwd, faxPasswd, gatewayName, queue, url, download) `
    + `VALUES ('SRFAX', 1, '${faxNumber}', '${faxNumber}', 'Playwright Fax', 'fax@example.ca', 'faxuser', 'siteuser', 'x', 'x', 'srfax', '0', '', 1); SELECT LAST_INSERT_ID();`,
  ).trim();
  return { id, created: true };
}

function seedPharmacyFax() {
  // Same predicate and NULL/'' fidelity as the sibling stamp check; see its seedPharmacyFax().
  const rows = sql(`SELECT p.recordId, IF(p.fax IS NULL, 1, 0), IFNULL(p.fax, '') FROM pharmacyInfo p
    JOIN demographicPharmacy dp ON dp.pharmacyID = p.recordId
    WHERE dp.demographic_no = ${demographicNo} AND dp.status = '1'
      AND (p.status IS NULL OR p.status <> '0');`)
    .split('\n').map((r) => r.split('\t')).filter((r) => /^\d+$/.test((r[0] || '').trim()));
  for (const [rawId, rawWasNull, rawFax] of rows) {
    const recordId = rawId.trim();
    if ((rawFax || '').trim()) continue;
    sql(`UPDATE pharmacyInfo SET fax = '${pharmacyFaxNumber}' WHERE recordId = ${recordId};`);
    seededPharmacyFaxes.push({ recordId, wasNull: String(rawWasNull).trim() === '1' });
  }
  visited.push({ label: 'pharmacy-fax', seeded: seededPharmacyFaxes.length, active: rows.length });
  if (!rows.length) {
    findings.push({ label: 'pharmacy-fax', type: 'no-active-pharmacy', text: `patient ${demographicNo} has no active pharmacy, so a prescription for them can never be faxed` });
  }
}

function cleanupFixtures() {
  const attempt = (label, fn) => {
    try { fn(); } catch (error) {
      findings.push({ label: 'cleanup', type: 'cleanup-error', text: `${label}: ${(error && error.message) || 'failed'}` });
    }
  };
  let ourScriptNos = [];
  attempt('list fixture scripts', () => {
    ourScriptNos = sql(`SELECT DISTINCT script_no FROM drugs WHERE customName='${customDrugName}' AND demographic_no=${demographicNo};`)
      .split('\n').map((r) => r.trim()).filter((r) => /^\d+$/.test(r));
  });
  for (const scriptNo of ourScriptNos) {
    attempt(`prescription ${scriptNo}`, () => {
      const sigId = sql(`SELECT COALESCE(digital_signature_id,'') FROM prescription WHERE script_no=${scriptNo};`).trim();
      sql(`DELETE FROM drugs WHERE script_no=${scriptNo};`);
      sql(`DELETE FROM prescription WHERE script_no=${scriptNo};`);
      if (/^\d+$/.test(sigId)) sql(`DELETE FROM DigitalSignature WHERE id=${sigId};`);
    });
  }
  attempt('faxes', () => {
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
      `UPDATE pharmacyInfo SET fax = ${wasNull ? 'NULL' : "''"} WHERE recordId = ${recordId} AND fax = '${pharmacyFaxNumber}';`));
  }
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => { cleanupFixtures(); removeSecretsDir(); process.exit(130); });
}

// --- browser plumbing -------------------------------------------------------------

function wirePage(page, label) {
  page.on('pageerror', (error) => {
    const text = error.stack || error.message || '';
    // Pre-existing, tracked by issue #3578 (expandPreview writes into the preview iframe before it
    // has parsed on some render orders). Named so the suppression stays auditable.
    if (/Cannot set properties of null \(setting 'innerHTML'\)/.test(text) && /expandPreview/.test(text)) return;
    findings.push({ label, type: 'pageerror', text: text.slice(0, 300) });
  });
  page.on('dialog', async (dialog) => {
    // Accept only the custom-drug confirm(), and only while clicking that button. Anything else is a
    // blocking finding: the check must not pass while a dialog is dismissed unseen.
    if (dialog.type() === 'confirm' && expectingCustomDrugConfirm) {
      await dialog.accept().catch(() => {});
      return;
    }
    findings.push({ label, type: 'unexpected-dialog', text: `${dialog.type()}: ${dialog.message()}`.slice(0, 200) });
    await dialog.dismiss().catch(() => dialog.accept().catch(() => {}));
  });
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await gotoApp(page, baseUrl, '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  // login/index.jsp renders #pin only when the legacy PIN is enabled.
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

/** A CSRFGuard master token for this session, read the way the page's own script does. */
async function csrfToken(page) {
  return page.evaluate(async (tokenUrl) => {
    const resp = await fetch(tokenUrl, { credentials: 'same-origin' });
    const js = await resp.text();
    const m = js.match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
    return m ? m[1] : '';
  }, appUrl(baseUrl, '/csrfguard'));
}

// --- the journey ------------------------------------------------------------------

async function writeCustomRxThroughUi(page) {
  await gotoApp(page, baseUrl, `/rx/choosePatient?demographicNo=${demographicNo}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'rx-search', url: safeUrl(page.url()) });

  const rangeStart = Number(sql(`SELECT COALESCE(MAX(script_no),0) FROM prescription WHERE provider_no='${providerNo}' AND demographic_no=${demographicNo};`) || '0');

  await page.locator('#searchString').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('#searchString').fill(customDrugName);
  expectingCustomDrugConfirm = true;
  try {
    await page.locator('#customDrug').click();
  } finally {
    expectingCustomDrugConfirm = false;
  }
  await page.locator("[id^='drugName_'], [id^='quantity_']").first().waitFor({ state: 'attached', timeout: 30000 });

  // "Save And Print": writes and stamp-signs the script and opens ViewScript2 in the modal.
  await page.locator('#saveButton').click();
  const modalFrame = page.frameLocator('#carlosModalBody iframe');
  await modalFrame.locator('#faxButton').waitFor({ state: 'attached', timeout: 30000 });

  const created = sql(`SELECT DISTINCT script_no FROM drugs WHERE customName='${customDrugName}' AND demographic_no=${demographicNo} AND script_no>${rangeStart};`)
    .split('\n').map((r) => r.trim()).filter((r) => /^\d+$/.test(r));
  if (!created.length) throw new Error('No new prescription row was created for the fixture custom drug');
  const scriptId = String(Math.max(...created.map(Number)));
  return { modalFrame, scriptId };
}

/**
 * Put the one-character probe into the RECORD's directions. The fax body is rebuilt from
 * drugs.special (recordBlock), so this is exactly the data path under test; the preview on screen
 * still shows the pre-probe text, which is also exactly the mismatch the servlet resolves in favour
 * of the record.
 */
function addProbeLineToRecord(scriptId) {
  sql(`UPDATE drugs SET special = CONCAT(IFNULL(special,''), '\\n${probeLine}\\n${probeLineContext}') WHERE script_no=${scriptId} AND customName='${customDrugName}';`);
  const check = sql(`SELECT COUNT(*) FROM drugs WHERE script_no=${scriptId} AND customName='${customDrugName}' AND special LIKE '%${probeLineContext}%';`).trim();
  if (check !== '1') throw new Error('probe line was not written to the fixture drug row');
}

async function faxThroughUi(page, modalFrame, scriptId) {
  // Deterministic race: hold the notes save so the fax POST can only carry the note if the page
  // waited for it. The route covers the modal iframe's requests too.
  let saveRequested = false;
  await page.route(/\/rx\/ViewAddRxComment/, async (route) => {
    saveRequested = true;
    await new Promise((resolve) => setTimeout(resolve, notesSaveDelayMs));
    await route.continue();
  });

  await modalFrame.locator('#additionalNotes').waitFor({ state: 'visible', timeout: 30000 });
  await modalFrame.locator('#additionalNotes').fill(noteText);

  const faxRequestPromise = page.waitForRequest((req) => /form\/createcustomedpdf/.test(req.url()) && /__method=oscarRxFax/.test(req.url()), { timeout: 45000 });
  const faxResponsePromise = page.waitForResponse((res) => /form\/createcustomedpdf/.test(res.url()) && /__method=oscarRxFax/.test(res.url()), { timeout: 45000 });
  // The click moves focus off the textarea, firing its onchange (addNotes -> delayed save) before
  // the click handler faxes: the same order a clinician's click produces.
  await modalFrame.locator('#faxButton').click();

  let pdfId = null;
  let status = 0;
  let body = '';
  try {
    const request = await faxRequestPromise;
    const response = await faxResponsePromise;
    status = response.status();
    body = await response.text().catch(() => '');
    const post = new URLSearchParams(request.postData() || '');
    pdfId = post.get('pdfId');
    if (post.get('scriptId') !== scriptId && new URL(request.url()).searchParams.get('scriptId') !== scriptId) {
      findings.push({ label: 'ui-fax', type: 'wrong-script', text: 'the Fax button posted a different scriptId than the prescription just written' });
    }
  } catch (error) {
    findings.push({ label: 'ui-fax', type: 'no-request', text: `Fax click produced no createcustomedpdf round trip: ${error.message}` });
  } finally {
    await page.unroute(/\/rx\/ViewAddRxComment/).catch(() => {});
  }
  visited.push({ label: 'ui-fax', status, saveRequested, hadPdfId: Boolean(pdfId) });

  if (!saveRequested) {
    // Without a save request the race cannot be observed; say so distinctly rather than let the
    // note assertion below read as the race failing.
    findings.push({ label: 'notes-race', type: 'save-not-triggered', text: 'clicking Fax did not fire the Additional Notes save (textarea onchange); the race could not be exercised' });
  }
  if (status < 200 || status >= 300) {
    findings.push({ label: 'ui-fax', type: 'http-error', status, text: `signed fax returned HTTP ${status}` });
  } else if (/not signed/i.test(body)) {
    findings.push({ label: 'ui-fax', type: 'signed-refused', text: 'the freshly stamp-signed prescription was refused as unsigned' });
  } else if (!/fax-success/i.test(body)) {
    findings.push({ label: 'ui-fax', type: 'not-successful', text: `fax did not report fax-success: ${body.replace(/\s+/g, ' ').slice(0, 160)}` });
  }
  return pdfId;
}

async function faxWithForgedIdentity(page, scriptId) {
  const token = await csrfToken(page);
  if (!token) findings.push({ label: 'forged-fax', type: 'no-csrf-token', text: 'could not obtain a CSRFGuard token for the forged-identity POST' });
  const pdfId = `pwbind${runSuffix}`;
  const result = await page.evaluate(async ({ postUrl, token: t, params }) => {
    const resp = await fetch(postUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-Requested-With': 'XMLHttpRequest',
        'CSRF-TOKEN': t,
      },
      credentials: 'same-origin',
      body: new URLSearchParams(params).toString(),
    });
    const body = await resp.text().catch(() => '');
    return { status: resp.status, body: body.slice(0, 400) };
  }, {
    postUrl: appUrl(baseUrl, '/form/createcustomedpdf'),
    token,
    params: {
      __title: 'Rx',
      __method: 'oscarRxFax',
      scriptId,
      pdfId,
      pharmaFax: pharmacyFaxNumber,
      clinicFax: faxNumber,
      pharmaName: 'Playwright Pharmacy',
      demographic_no: demographicNo,
      rxPageSize: 'PageSize.Letter',
      rx: 'ignored: the servlet faxes the record',
      rxDate: 'January 1, 2026',
      sigDoctorName: 'Dr Forged',
      showPatientDOB: 'true',
      ...forged,
    },
  });
  visited.push({ label: 'forged-fax', status: result.status });
  if (result.status < 200 || result.status >= 300) {
    findings.push({ label: 'forged-fax', type: 'http-error', status: result.status, text: `forged-identity fax returned HTTP ${result.status}` });
    return null;
  }
  if (/csrf|token/i.test(result.body) && /reject|forbidden|invalid/i.test(result.body)) {
    findings.push({ label: 'forged-fax', type: 'csrf-rejected', text: 'forged-identity POST was rejected by CSRF, so the binding was not exercised' });
    return null;
  }
  if (!/fax-success/i.test(result.body)) {
    findings.push({ label: 'forged-fax', type: 'not-successful', text: `forged-identity fax did not report fax-success: ${result.body.replace(/\s+/g, ' ').slice(0, 160)}` });
    return null;
  }
  return pdfId;
}

// --- assertions on the rendered PDFs ---------------------------------------------

function recordIdentity() {
  const row = sql(`SELECT IFNULL(first_name,''), IFNULL(last_name,''), IFNULL(hin,'') FROM demographic WHERE demographic_no=${demographicNo};`).split('\t');
  const first = (row[0] || '').trim();
  const last = (row[1] || '').trim();
  return { name: `${first} ${last}`.trim(), hin: (row[2] || '').trim() };
}

function assertIdentityBound(runs, identity) {
  const joined = runs.join('\n');
  for (const [field, value] of Object.entries(forged)) {
    // The forged phone carries the "Tel: " prefix the page adds; match on the number.
    const needle = field === 'patientPhone' ? '4165559999' : value;
    if (joined.includes(needle)) {
      findings.push({ label: 'identity', type: 'forged-value-rendered', field, text: `the faxed PDF rendered the request's ${field}, not the record's` });
    }
  }
  const nameBound = identity.name.length > 0 && runs.includes(identity.name);
  const hinBound = identity.hin.length === 0 || runs.some((r) => r.endsWith(identity.hin));
  visited.push({ label: 'identity', nameBound, hinBound, recordHasHin: identity.hin.length > 0 });
  if (!nameBound) findings.push({ label: 'identity', type: 'record-name-absent', text: 'the faxed PDF does not carry the prescription demographic\'s name as its own line' });
  if (!hinBound) findings.push({ label: 'identity', type: 'record-hin-absent', text: 'the faxed PDF does not carry the prescription demographic\'s health number' });
}

function assertProbeLine(runs) {
  const contextPresent = runs.some((r) => r.includes(probeLineContext));
  const probePresent = runs.includes(probeLine);
  visited.push({ label: 'one-char-line', contextPresent, probePresent });
  if (!contextPresent) {
    findings.push({ label: 'one-char-line', type: 'context-absent', text: 'the record directions written for this run did not reach the faxed PDF at all (the fax body did not come from the record)' });
  } else if (!probePresent) {
    findings.push({ label: 'one-char-line', type: 'dropped', text: 'the standalone one-character direction line was dropped from the faxed PDF' });
  }
}

/**
 * The clinic name exactly as rx/Preview2.jsp puts it at the top of the prescriber header: the
 * single clinic row's name with a trailing "(nnnnnn)" site code removed. Empty when the install has
 * no clinic row, in which case the header assertion is skipped and says so.
 */
function expectedClinicName() {
  const raw = sql('SELECT IFNULL(clinic_name, \'\') FROM clinic ORDER BY clinic_no LIMIT 1;').trim();
  return raw.replace(/\(\d{6}\)/g, '').trim();
}

function assertClinicHeader(runs) {
  const clinicName = expectedClinicName();
  if (!clinicName) {
    visited.push({ label: 'clinic-header', skipped: 'no clinic row' });
    return;
  }
  const trimmed = runs.map((r) => r.trim());
  const ownLine = trimmed.includes(clinicName);
  // The defect's signature: the name is there but the address is glued onto the same rendered line.
  const glued = trimmed.some((r) => r.startsWith(clinicName) && r.length > clinicName.length);
  visited.push({ label: 'clinic-header', ownLine, glued });
  if (ownLine) return;
  if (glued) {
    findings.push({ label: 'clinic-header', type: 'lines-glued', text: 'the faxed clinic header renders the clinic name and its address on one glued line (the <br> to line-break conversion lost the break)' });
  } else {
    // Not this defect. A specialist-address (useSC) fax composes the header server-side; on the
    // demo install the header is the clinic's, so its absence is still worth a look.
    findings.push({ label: 'clinic-header', type: 'absent', text: 'the faxed PDF has no line carrying the clinic name at all' });
  }
}

function assertNoteRendered(runs) {
  const present = runs.some((r) => r.includes(noteText));
  visited.push({ label: 'notes-race', notePresent: present });
  if (!present) {
    findings.push({ label: 'notes-race', type: 'note-omitted', text: `a note typed immediately before Fax (save delayed ${notesSaveDelayMs} ms) is absent from the faxed PDF` });
  }
}

async function runChecks(context) {
  const page = await login(context);
  try {
    faxConfig = stageFaxConfig();
    seedPharmacyFax();

    const { modalFrame, scriptId } = await writeCustomRxThroughUi(page);
    visited.push({ label: 'prescription', created: true });
    addProbeLineToRecord(scriptId);

    // B + C on one real click.
    const uiPdfId = await faxThroughUi(page, modalFrame, scriptId);
    if (uiPdfId) {
      const runs = pdfTextRuns(await waitForPdf(uiPdfId, 'ui-fax'));
      visited.push({ label: 'ui-fax-pdf', runs: runs.length });
      if (!runs.length) findings.push({ label: 'ui-fax', type: 'no-text', text: 'the faxed PDF has no text runs' });
      assertProbeLine(runs);
      assertNoteRendered(runs);
      assertClinicHeader(runs);
    }

    // A: the same signed script, posted with a forged identity.
    const forgedPdfId = await faxWithForgedIdentity(page, scriptId);
    if (forgedPdfId) {
      const runs = pdfTextRuns(await waitForPdf(forgedPdfId, 'forged-fax'));
      visited.push({ label: 'forged-fax-pdf', runs: runs.length });
      if (!runs.length) findings.push({ label: 'forged-fax', type: 'no-text', text: 'the forged-identity fax PDF has no text runs' });
      assertIdentityBound(runs, recordIdentity());
    }
  } finally {
    cleanupFixtures();
    await page.close().catch(() => {});
  }
}

(async () => {
  const browser = await chromium.launch(getLaunchOptions(process.env.CHROME_PATH || ''));
  let exitCode = 0;
  try {
    const host = baseUrl.hostname.replace(/^\[|\]$/g, '').toLowerCase();
    const isLoopback = ['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1'].includes(host);
    const context = await browser.newContext({ ignoreHTTPSErrors: isLoopback && baseUrl.protocol === 'https:' });
    await runChecks(context);
    await context.close();
  } catch (error) {
    findings.push({ label: 'run', type: 'exception', text: (error && error.message) || String(error) });
  } finally {
    await browser.close().catch(() => {});
    removeSecretsDir();
  }

  const summary = { baseUrl: `${baseUrl.origin}${baseUrl.pathname}`, visited, findings };
  try {
    const out = buildArtifactPath(artifactDir, 'rx-fax-record-binding', '.json');
    fs.writeFileSync(out, JSON.stringify(summary, null, 2));
    console.log(`artifact: ${out}`);
  } catch (error) {
    console.log(`artifact not written: ${(error && error.message) || 'unknown error'}`);
  }
  console.log(JSON.stringify({ visited }, null, 2));
  if (findings.length) {
    exitCode = 1;
    console.error(`FAIL: ${findings.length} finding(s)`);
    for (const f of findings) console.error(` - [${f.label}] ${f.type}: ${f.text || ''}`);
  } else {
    console.log('PASS: identity, one-character line and notes are bound to the prescription record; the clinic header renders on its own lines');
  }
  process.exit(exitCode);
})();
