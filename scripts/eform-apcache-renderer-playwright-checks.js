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
 * Browser regression checks for the renderer's APCache bridge
 * (EFormApCacheForPdfGenerationServlet), the capability-scoped servlet the
 * headless PDF browser calls to populate APCache-driven eForm fields.
 *
 * The script logs into a running CARLOS app, imports two temporary eForms built
 * from scripts/fixtures/eform/apcache-renderer-bridge.html (fields filled by
 * APCache.js lookups, not server-side oscarDB attributes), and verifies:
 *
 *   1. positive: a saved form whose lookups name configured APs renders to a
 *      PDF whose text carries the values the bridge served (the signed-in
 *      provider's name, today's date) -- no placeholder, no pending marker --
 *      and the save result view's own re-run lookups (which carry the
 *      efmfid/efmdemographic_no form-action query string) still resolve
 *      through the interactive route with no 400 and no alert;
 *   2. negative: a form that also looks up an AP key this server does not
 *      configure is NOT silently rendered with a blank field: the download is
 *      withheld and the clinician gets the missing-content approval page whose
 *      "Failed content resources" count is non-zero, while the page never
 *      names the key (only the operator log does);
 *   3. optional (APCACHE_JOURNAL_UNIT=carlos-emr, run as root on the packaged
 *      install): the application journal carries the servlet's WARN line
 *      naming the rejected key and reason only, with no fdid, throwable, or AP
 *      value, and no 500-class "lookup failed" line for that render;
 *   4. optional (APCACHE_PROBE_URL=http://127.0.0.1:18080/carlos, a loopback
 *      Tomcat base): direct requests without a render grant answer 401, and a
 *      non-GET answers 405 (or 403 from the CSRF guard); the front door (BASE_URL) also refuses.
 *
 * Requires pdftotext from poppler-utils.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:eform-apcache-renderer-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   RESET_PASSWORD=...  (only for the one-time forced reset on a fresh deb install)
 *   APCACHE_DEMOGRAPHIC_NO=1
 *   APCACHE_SCREENSHOT_DIR=/tmp
 *   APCACHE_JOURNAL_UNIT=carlos-emr   (enables the journalctl assertions)
 *   APCACHE_PROBE_URL=http://127.0.0.1:18080/carlos   (enables direct servlet probes)
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('node:child_process');
const { chromium, request: playwrightRequest } = require('playwright');
const {
  assert,
  assertNotErrorPage,
  buildArtifactPath,
  buildFailureDetails,
  createRecorder,
  findLibraryEform,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

const SERVLET_PATH = '/EFormApCacheForPdfGenerationServlet';
const PROVIDER_KEY = 'current_user_fname_lname';
const TODAY_KEY = 'today';
const WARN_LINE = 'Renderer APCache key cannot be executed for this render';

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  resetPassword: process.env.RESET_PASSWORD || '',
  demographicNo: process.env.APCACHE_DEMOGRAPHIC_NO || '1',
  screenshotDir: process.env.APCACHE_SCREENSHOT_DIR || '/tmp',
  journalUnit: process.env.APCACHE_JOURNAL_UNIT || '',
  probeUrl: process.env.APCACHE_PROBE_URL || '',
  fixtureHtmlPath: path.join(__dirname, 'fixtures', 'eform', 'apcache-renderer-bridge.html'),
};

const recorder = createRecorder();

// Failure output must not carry patient-correlating identifiers: response URLs the recorder keeps
// include fdid and demographic numbers. Same contract as eform-test-pattern-playwright-checks.js.
const sensitiveQueryParamPattern = /([?&](?:fdid|efmfid|fid|demographic_no|efmdemographic_no|demographicNo|demo_no|demoNo|patient_id|patientId)=)[^&#\s'"]*/gi;
const sensitiveDiagnosticFieldPattern = /^(fdid|efmfid|demographic_no|efmdemographic_no|demographicNo|demo_no|demoNo|patient_id|patientId)$/i;

function redactSensitiveFailureText(value) {
  return String(value)
    .replace(sensitiveQueryParamPattern, '$1<redacted>')
    .replace(/(["']?)(fdid|efmdemographic_no|demographic_no|demographicNo|demo_no|demoNo|patient_id|patientId)(\1)(\s*[:=]\s*)(["']?)\d+\5/gi, '$1$2$3$4$5<redacted>$5')
    .replace(/\b(fdid|efmdemographic_no|demographic_no|demographicNo|demo_no|demoNo|patient_id|patientId)\b\s+\d+/gi, '$1 <redacted>');
}

function redactSensitiveFailureDetails(value) {
  if (typeof value === 'string') {
    return redactSensitiveFailureText(value);
  }
  if (Array.isArray(value)) {
    return value.map(redactSensitiveFailureDetails);
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, nestedValue]) => [
        key,
        sensitiveDiagnosticFieldPattern.test(key) ? '<redacted>' : redactSensitiveFailureDetails(nestedValue),
      ]),
    );
  }
  return value;
}

function describe(value) {
  return JSON.stringify(redactSensitiveFailureDetails(value), null, 2);
}

function validateConfig() {
  assert(/^\d+$/.test(config.demographicNo), `APCACHE_DEMOGRAPHIC_NO must be numeric, got ${config.demographicNo}`);
  assert(fs.existsSync(config.fixtureHtmlPath), `Fixture not found: ${config.fixtureHtmlPath}`);
  assert(/^[A-Za-z0-9_-]+$/.test(config.journalUnit || 'x'), `APCACHE_JOURNAL_UNIT must be a plain unit name, got ${config.journalUnit}`);
  if (config.probeUrl) {
    const probe = new URL(config.probeUrl);
    assert(['localhost', '127.0.0.1', '::1'].includes(probe.hostname), `APCACHE_PROBE_URL must be loopback, got ${probe.hostname}`);
  }
  try {
    execFileSync('pdftotext', ['-v'], { stdio: 'ignore' });
  } catch (error) {
    throw new Error('pdftotext (poppler-utils) is required to read the rendered PDF text');
  }
}

/** Builds one fixture variant; the negative one adds a lookup for a key no server configures. */
function writeFixture(tempDir, name, missingKey) {
  let html = fs.readFileSync(config.fixtureHtmlPath, 'utf8')
    .split('APCACHE_KEY_PROVIDER').join(PROVIDER_KEY)
    .split('APCACHE_KEY_TODAY').join(TODAY_KEY);
  if (missingKey) {
    assert(/^[A-Za-z0-9_]+$/.test(missingKey), `unsafe missing key ${missingKey}`);
    html = html
      .replace('APCACHE_EXTRA_LOOKUP', `apCache.lookup('${missingKey}');`)
      .replace('APCACHE_EXTRA_FIELD', [
        '<div class="field">',
        `  <label>Unconfigured AP (${missingKey})`,
        `    <input type="text" class="value" name="apcache_missing" id="apcache_missing" value="TemplateMissing" data-apcache-key="${missingKey}">`,
        '  </label>',
        `  <div id="apcache_missing_echo">APCACHE-PENDING[${missingKey}]</div>`,
        '</div>',
      ].join('\n'));
  } else {
    html = html.replace('APCACHE_EXTRA_LOOKUP', '').replace('APCACHE_EXTRA_FIELD', '');
  }
  const htmlPath = path.join(tempDir, `${name}.html`); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- name is a fixed literal chosen by this script under a private temp directory
  fs.writeFileSync(htmlPath, html);
  return htmlPath;
}

async function uploadEform(context, formName, formSubject, htmlPath) {
  const page = await context.newPage();
  wirePage(page, `apcache-upload:${formName}`, recorder);
  try {
    await gotoApp(page, config.baseUrl, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const frame = page.frameLocator('#uploadFrame');
    await frame.locator('input[name="formName"]').fill(formName);
    await frame.locator('input[name="formSubject"]').fill(formSubject);
    await frame.locator('#formHtml').setInputFiles(htmlPath);
    await frame.locator('input.upload[type="submit"]').click();
    await page.waitForURL(/administration\?show=Forms/, { timeout: 10000 }).catch(() => {});
    await gotoApp(page, config.baseUrl, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const { fid } = await findLibraryEform(page, formName);
    return fid;
  } finally {
    await page.close().catch(() => {});
  }
}

async function openAddEform(context, fid, label) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.close = () => {
      window.__playwrightCloseIntercepted = true;
    };
  });
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(config.demographicNo)}`);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, label);
  return page;
}

/** Reads the value the interactive viewer's own lookup produced, so the PDF can be compared to it. */
async function readViewerValue(page, key, echoId) {
  const echo = page.locator(`#${echoId}`);
  await echo.filter({ hasText: `APCACHE-VALUE[${key}]=` }).waitFor({ state: 'attached', timeout: 20000 });
  const text = (await echo.textContent()) || '';
  const value = text.slice(`APCACHE-VALUE[${key}]=`.length).trim();
  assert(value.length > 0, `viewer lookup for ${key} produced an empty value`);
  return value;
}

async function downloadPdf(page, artifactBaseName) {
  const pdfPath = buildArtifactPath(config.screenshotDir, artifactBaseName, '.pdf');
  const downloadPromise = page.waitForEvent('download', { timeout: 90000 });
  await page.locator('#remoteDownloadButton').click();
  const download = await downloadPromise;
  await download.saveAs(pdfPath);
  const pdfBytes = fs.readFileSync(pdfPath);
  assert(pdfBytes.subarray(0, 5).toString('utf8') === '%PDF-', 'Downloaded payload was not a PDF');
  return pdfPath;
}

function pdfText(pdfPath) {
  return execFileSync('pdftotext', ['-layout', pdfPath, '-'], { encoding: 'utf8' });
}

async function savedFdidOnPage(page) {
  const fdid = await page.locator('#fdid, input[name="fdid"]').first().inputValue({ timeout: 15000 }).catch(() => '');
  return /^\d+$/.test(fdid) ? fdid : null;
}

async function listSavedFdids(context, subject) {
  const page = await context.newPage();
  wirePage(page, 'apcache-patient-list', recorder);
  try {
    await gotoApp(page, config.baseUrl, `/eform/efmpatientformlist?demographic_no=${encodeURIComponent(config.demographicNo)}`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const rows = page.locator('#efmTable tr', { hasText: subject });
    const fdids = new Set();
    const rowCount = await rows.count();
    for (let index = 0; index < rowCount; index += 1) {
      const row = rows.nth(index);
      const link = row.locator('a[onclick*="fdid="], a[href*="fdid="]').first();
      const onclick = await link.getAttribute('onclick').catch(() => '') || '';
      const href = await link.getAttribute('href').catch(() => '') || '';
      const match = onclick.match(/fdid=([^&'"]+)/) || href.match(/fdid=([^&'"]+)/);
      if (match && /^\d+$/.test(decodeURIComponent(match[1]))) {
        fdids.add(decodeURIComponent(match[1]));
      }
    }
    return Array.from(fdids);
  } finally {
    await page.close().catch(() => {});
  }
}

async function cleanupSavedEform(context, fdid) {
  const page = await context.newPage();
  wirePage(page, 'apcache-saved-cleanup', recorder);
  try {
    await gotoApp(page, config.baseUrl, `/eform/efmpatientformlist?demographic_no=${encodeURIComponent(config.demographicNo)}`);
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    const form = page.locator('form', { has: page.locator(`input[name="fdid"][value="${fdid}"]`) }).first();
    if (!(await form.count())) {
      return;
    }
    await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST' && response.url().includes('/eform/removeEForm'), { timeout: 15000 }),
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => null),
      form.evaluate((formElement) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed cleanup helper submits an already-selected local form without interpolated code
        formElement.submit();
      }),
    ]);
  } finally {
    await page.close().catch(() => {});
  }
}

async function readCsrfToken(page) {
  let csrfToken = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue({ timeout: 5000 }).catch(() => '');
  if (!csrfToken) {
    const configuredContextPath = config.baseUrl.pathname === '/' ? '' : config.baseUrl.pathname.replace(/\/$/, '');
    csrfToken = await page.evaluate(async (baseContextPath) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection,javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- baseContextPath is derived from validateBaseUrl and used only for a same-origin CSRFGuard fetch fallback
      const contextInput = document.getElementById('context');
      const contextPath = contextInput && contextInput.value ? contextInput.value : baseContextPath;
      const response = await fetch(`${contextPath}/csrfguard`, { credentials: 'same-origin' });
      if (!response.ok) {
        return '';
      }
      const match = (await response.text()).match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
      return match ? match[1] : '';
    }, configuredContextPath).catch(() => '');
  }
  assert(csrfToken, 'form manager page did not expose a CSRF token for cleanup');
  return csrfToken;
}

async function cleanupImportedEform(context, fid) {
  if (!fid) {
    return;
  }
  const page = await context.newPage();
  wirePage(page, 'apcache-template-cleanup', recorder);
  try {
    await gotoApp(page, config.baseUrl, '/eform/efmformmanager');
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    const token = await readCsrfToken(page);
    const formId = `apcache_delete_form_${fid}`;
    await page.evaluate(({ submittedFid, deleteFormId, csrf }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- values are passed as Playwright arguments, not interpolated into code
      const form = document.createElement('form');
      form.id = deleteFormId;
      form.method = 'post';
      form.action = `${window.location.origin}${window.location.pathname.replace(/\/efmformmanager.*$/, '')}/delEForm`;
      for (const [name, value] of [['CSRF-TOKEN', csrf], ['fid', submittedFid]]) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        form.appendChild(input);
      }
      document.body.appendChild(form);
    }, { submittedFid: fid, deleteFormId: formId, csrf: token });
    await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST' && response.url().includes('/eform/delEForm'), { timeout: 15000 }),
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => null),
      page.locator(`#${formId}`).evaluate((form) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed cleanup helper submits an already-selected local form without interpolated code
        form.submit();
      }),
    ]);
  } finally {
    await page.close().catch(() => {});
  }
}

/** Part 1: configured keys are served by the bridge and land in the PDF text. */
async function checkPositiveRender(context, fid, runId, artifactPaths) {
  const page = await openAddEform(context, fid, 'apcache-positive');
  const providerValue = await readViewerValue(page, PROVIDER_KEY, 'apcache_provider_echo');
  const todayValue = await readViewerValue(page, TODAY_KEY, 'apcache_today_echo');
  assert(/^\d{4}-\d{2}-\d{2}$/.test(todayValue), `viewer 'today' lookup is not a date: ${todayValue}`);

  const pdfPath = await downloadPdf(page, `eform-apcache-positive-${runId}`);
  artifactPaths.add(pdfPath);
  const text = pdfText(pdfPath);
  assert(text.includes(`APCACHE-VALUE[${PROVIDER_KEY}]=${providerValue}`),
    `rendered PDF does not carry the provider value the bridge should have served (viewer saw a ${providerValue.length}-character value)`);
  const todayMatch = text.match(new RegExp(`APCACHE-VALUE\\[${TODAY_KEY}\\]=(\\d{4}-\\d{2}-\\d{2})`));
  assert(todayMatch, "rendered PDF does not carry the 'today' value the bridge should have served");
  // Same value the viewer's own lookup produced moments earlier, not just any date: a stale or
  // wrong renderer value must not pass on shape alone (a run that straddles midnight would fail
  // here; rerun it).
  assert(todayMatch[1] === todayValue, `rendered PDF 'today' value ${todayMatch[1]} does not match the viewer's ${todayValue}`);
  assert(!text.includes('APCACHE-PENDING'), 'rendered PDF still carries a pending marker: a lookup never completed in the renderer');
  assert(!text.includes('APCACHE-ERROR'), 'rendered PDF carries an APCache error marker: the bridge refused a configured key');
  assert(!text.includes('TemplateProvider') && !text.includes('TemplateToday'), 'rendered PDF carries template placeholders instead of bridge values');
  // The download lands on the save result view, whose form action query string carries
  // efmfid/efmdemographic_no rather than fid/demographic_no. The fixture's lookups re-run there
  // against the INTERACTIVE route and must still resolve: a 400 here left every APCache field
  // blank behind the library's "an error has occurred" alert after any save-and-download.
  const postSaveProvider = await readViewerValue(page, PROVIDER_KEY, 'apcache_provider_echo');
  assert(postSaveProvider === providerValue, 'post-save view resolved a different provider value than the add view');
  const fdid = await savedFdidOnPage(page);
  await page.close().catch(() => {});
  return { fdid, providerValue, todayValue };
}

/** Part 2: an unconfigured key withholds the PDF and offers the approval page; the key is not shown. */
async function checkNegativeRender(context, fid, missingKey, runId, artifactPaths) {
  const page = await openAddEform(context, fid, 'apcache-negative');
  await readViewerValue(page, PROVIDER_KEY, 'apcache_provider_echo');

  let downloadFired = false;
  page.waitForEvent('download', { timeout: 60000 }).then(() => { downloadFired = true; }).catch(() => {});
  await page.locator('#remoteDownloadButton').click();
  const heading = page.locator('h5', { hasText: 'Some eForm content could not be loaded' });
  await heading.waitFor({ state: 'visible', timeout: 90000 });
  artifactPaths.add(await screenshot(page, config.screenshotDir, `eform-apcache-missing-content-${runId}`));
  const bodyText = await page.locator('body').innerText();
  const failedMatch = bodyText.match(/Failed content resources:\s*(\d+)/);
  assert(failedMatch, `approval page does not report failed content resources: ${bodyText.slice(0, 400)}`);
  assert(Number(failedMatch[1]) >= 1, `approval page counts ${failedMatch[1]} failed content resources; the unconfigured key should count as one`);
  assert(!bodyText.includes(missingKey), 'approval page names the AP key; only the operator log should');
  assert(await page.locator('input[name="renderApproval"]').count() === 1, 'approval page carries no one-time approval token');
  assert(!downloadFired, 'a PDF was downloaded despite the unconfigured APCache key');
  const fdid = await savedFdidOnPage(page);
  assert(fdid, 'approval page does not carry the saved fdid');
  await page.close().catch(() => {});
  return { fdid };
}

/** Part 3 (optional): the WARN line names the key in the journal, and nothing else about the render. */
function checkJournal(sinceIso, missingKey, runFdids, positiveProviderValue) {
  if (!config.journalUnit) {
    return { skipped: true };
  }
  const journal = execFileSync('journalctl', ['-u', config.journalUnit, '--since', sinceIso, '--no-pager', '-o', 'cat'], { encoding: 'utf8' });
  const warnLines = journal.split('\n').filter((line) => line.includes(WARN_LINE));
  assert(warnLines.length >= 1, `journal has no "${WARN_LINE}" line since ${sinceIso}`);
  const keyed = warnLines.filter((line) => line.includes(`key=${missingKey}`));
  assert(keyed.length >= 1, `journal WARN lines never name key=${missingKey}`);
  assert(keyed.every((line) => line.includes('reason=APCache key is not configured')), 'journal WARN line carries an unexpected reason');
  // Key and reason only: the fix is in apconfig.xml, so no patient-correlating fdid rides along.
  assert(!keyed.some((line) => line.includes('fdid=')), 'journal WARN line carries an fdid');
  assert(!keyed.some((line) => /\bat [a-z]+\./.test(line) || line.includes('Exception')), 'journal WARN line carries a stack trace');
  assert(!journal.includes(positiveProviderValue), 'journal carries an AP value; the servlet must log identifiers only');
  // The lookup-failure ERROR lines carry the render's fdid; only this run's renders count, so a
  // concurrent render on the same install cannot fail the check.
  const failed = journal.split('\n')
    .filter((line) => line.includes('Renderer APCache lookup failed') || line.includes('Renderer APCache lookup returned an unusable result'))
    .filter((line) => runFdids.some((fdid) => line.includes(`fdid=${fdid}`)));
  assert(failed.length === 0, 'journal reports an unexpected lookup failure for one of this run\'s renders');
  return { warnLines: keyed.length };
}

/** Part 4 (optional): without a render grant the servlet refuses, on loopback and through the front door. */
async function checkDirectProbes() {
  const results = {};
  if (config.probeUrl) {
    const base = config.probeUrl.replace(/\/$/, '');
    const noCookie = await fetch(`${base}${SERVLET_PATH}?key=${TODAY_KEY}`, { redirect: 'manual' });
    results.loopbackNoGrant = noCookie.status;
    assert(noCookie.status === 401, `loopback request without a render grant answered ${noCookie.status}, expected 401`);
    const bogus = await fetch(`${base}${SERVLET_PATH}?key=${TODAY_KEY}`, { redirect: 'manual', headers: { cookie: 'CARLOS_EFORM_RENDER=bogus-grant' } });
    results.loopbackBogusGrant = bogus.status;
    assert(bogus.status === 401, `loopback request with a bogus grant answered ${bogus.status}, expected 401`);
    // The servlet itself only implements GET (405), but on a full deployment the CSRF guard
    // refuses a token-less POST first (403). Either is the refusal that matters.
    const post = await fetch(`${base}${SERVLET_PATH}?key=${TODAY_KEY}`, { method: 'POST', redirect: 'manual' });
    results.loopbackPost = post.status;
    assert([403, 405].includes(post.status), `POST to the read-only bridge answered ${post.status}, expected 403/405`);
  }
  const front = await playwrightRequest.newContext({ ignoreHTTPSErrors: true });
  try {
    const response = await front.get(`${config.baseUrl.origin}${config.baseUrl.pathname}${SERVLET_PATH}?key=${TODAY_KEY}`, { maxRedirects: 0 });
    results.frontDoorNoGrant = response.status();
    assert([401, 403].includes(response.status()), `front-door request without a render grant answered ${response.status()}, expected 401/403`);
  } finally {
    await front.dispose();
  }
  return results;
}

async function main() {
  const runId = `${Date.now()}-${process.pid}`;
  const missingKey = `renderer_missing_ap_${Date.now()}`;
  const positiveName = `Playwright APCache bridge ${runId}`;
  const negativeName = `Playwright APCache missing ${runId}`;
  const positiveSubject = `APCache bridge ${runId}`;
  const negativeSubject = `APCache missing ${runId}`;
  const sinceIso = new Date(Date.now() - 5000).toISOString();
  const artifactPaths = new Set();
  const savedFdids = new Set();
  let tempDir = null;
  let browser = null;
  let context = null;
  let positiveFid = null;
  let negativeFid = null;

  try {
    validateConfig();
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-eform-apcache-'));
    const positiveHtml = writeFixture(tempDir, 'apcache-positive', null);
    const negativeHtml = writeFixture(tempDir, 'apcache-negative', missingKey);
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    context = await browser.newContext({
      acceptDownloads: true,
      ignoreHTTPSErrors: config.baseUrl.protocol === 'https:',
      viewport: { width: 1280, height: 1200 },
    });
    const landing = await login(context, config, recorder);
    await landing.close();

    positiveFid = await uploadEform(context, positiveName, positiveSubject, positiveHtml);
    negativeFid = await uploadEform(context, negativeName, negativeSubject, negativeHtml);

    const positive = await checkPositiveRender(context, positiveFid, runId, artifactPaths);
    if (positive.fdid) {
      savedFdids.add(positive.fdid);
    }
    const negative = await checkNegativeRender(context, negativeFid, missingKey, runId, artifactPaths);
    savedFdids.add(negative.fdid);

    const journal = checkJournal(sinceIso, missingKey, [positive.fdid, negative.fdid].filter(Boolean), positive.providerValue);
    const probes = await checkDirectProbes();

    // A 422 from the bridge is expected during the negative render (it is what withholds the
    // PDF); it happens inside the headless renderer, not in this browser, so this browser must
    // never have seen the bridge answer an error. The interactive route must not have answered
    // one either: the save result view's efmfid/efmdemographic_no query string once produced
    // "Invalid fid" 400s on every post-save lookup, and APCache.js turns any lookup failure
    // into a "contact an administrator" alert the recorder captures as a dialog.
    const lookupFailures = recorder.badResponses.filter((response) => response.url.includes('/eform/efmformapconfig_lookup'));
    assert(lookupFailures.length === 0, `interactive APCache lookups answered errors: ${describe(lookupFailures)}`);
    assert(!recorder.badResponses.some((response) => response.url.includes(SERVLET_PATH)), `this browser saw the renderer bridge answer an error: ${describe(recorder.badResponses)}`);
    assert(recorder.badResponses.length === 0, `Unexpected HTTP errors: ${describe(recorder.badResponses)}`);
    const lookupAlerts = recorder.dialogs.filter((dialog) => /an error has occurred/i.test(dialog.text));
    assert(lookupAlerts.length === 0, `APCache raised its lookup-failure alert: ${describe(lookupAlerts)}`);

    console.log(JSON.stringify({
      positiveTemplateFid: positiveFid,
      negativeTemplateFid: negativeFid,
      savedEformsCreated: [positive.fdid, negative.fdid].filter(Boolean).length,
      providerValueLength: positive.providerValue.length,
      todayValue: positive.todayValue,
      journal,
      probes,
    }, null, 2));
    for (const artifactPath of artifactPaths) {
      fs.rmSync(artifactPath, { force: true });
    }
    console.log('PASS eForm renderer APCache bridge check');
  } catch (error) {
    console.error('FAIL eForm renderer APCache bridge check');
    console.error(redactSensitiveFailureText(error.stack || error.message));
    console.error(describe(buildFailureDetails(recorder)));
    process.exitCode = 1;
  } finally {
    const cleanupErrors = [];
    if (context) {
      for (const subject of [positiveSubject, negativeSubject]) {
        await listSavedFdids(context, subject).then((fdids) => fdids.forEach((fdid) => savedFdids.add(fdid))).catch((error) => cleanupErrors.push(error));
      }
      for (const fdid of savedFdids) {
        await cleanupSavedEform(context, fdid).catch((error) => cleanupErrors.push(error));
      }
      await cleanupImportedEform(context, positiveFid).catch((error) => cleanupErrors.push(error));
      await cleanupImportedEform(context, negativeFid).catch((error) => cleanupErrors.push(error));
    }
    if (browser) {
      await browser.close().catch(() => {});
    }
    if (tempDir) {
      fs.rmSync(tempDir, { recursive: true, force: true });
    }
    if (cleanupErrors.length) {
      console.error(`cleanup problems: ${cleanupErrors.map((error) => redactSensitiveFailureText(error.message)).join('; ')}`);
      process.exitCode = 1;
    }
  }
}

main();
