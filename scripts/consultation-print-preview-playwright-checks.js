#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the consultation request's Print button: does the
 * preview show what the clinician has typed?
 *
 * WHY THIS CHECK EXISTS (issue #3721). Print on the consultation screen exists to
 * preview work in progress. getConsultFormPrintPreview() serializes the whole form
 * and POSTs it by AJAX rather than navigating, precisely so the clinician keeps
 * their edits and stays on the page -- the JSP says so in as many words. The server
 * then ignored every posted field: ConsultationPDFCreator rebuilds the form from
 * the database with estRequestFromId, so the preview rendered the SAVED
 * consultation. On a referral whose clinical fields had never been filled in, that
 * is a generic form with nothing in it, which is exactly what the issue reports:
 * "It's a generic consultation with correct information but no added details that
 * user filled out."
 *
 * WHAT IT ASSERTS, AND WHY IT READS THE PDF. The preview never reaches a window
 * this check can inspect -- the page turns the base64 in the JSON reply into a
 * blob and hands it to the browser as a download. So the check reads the POST's
 * JSON, decodes the PDF, and looks inside its text for the stamp it typed. Three
 * things have to hold:
 *
 *   1. the typed text is in the PDF;
 *   2. the value it replaced is NOT (otherwise the preview is the stored record
 *      and the typed text merely appeared somewhere else on the page);
 *   3. the consultation row is unchanged afterwards -- Print previews, it does not
 *      save, and a "fix" that saved on Print would file an unfinished referral.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, Search, Master Record, E-Chart,
 * the Consultations list, the consultation's own edit screen, then its Print
 * button. The edit screen is reached through /encounter/ViewRequest, which is what
 * the chart's list links to and what makes the screen show Update/Print rather
 * than Submit -- opening the same JSP by any other route renders the new-request
 * buttons and there is no Print to press.
 *
 * WHAT IT WRITES, AND REMOVES. One consultation request for the selected patient,
 * created through the UI so the check has a referral of its own to edit, and the
 * note lock the chart takes. Both are removed afterwards through MYSQL_*. It never
 * edits a consultation it did not create: a clinician's referral is not something
 * a test types into.
 *
 * NO PATIENT KEY IN THE OUTPUT. demographic_no joins straight back to a patient
 * record and runCheck() writes thrown messages into CI artifacts, so the
 * diagnostics say "the selected patient" and never print the chart URL.
 *
 * Defaults are for the local devcontainer:
 *   MYSQL_PASSWORD=... npm run test:consultation-print-preview-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   CONSULT_PREVIEW_SEARCH=FAKE-          surname prefix used to reach a patient
 *   CONSULT_PREVIEW_DEMOGRAPHIC_NO=2      which patient's chart to open. Defaults to 2, not 1:
 *                                         demographic 1's chart answers 500 on the demo dataset,
 *                                         because its HRM rows point at report files that never
 *                                         shipped
 *   CONSULT_PREVIEW_TIMEOUT_MS=45000      per-step allowance
 */

const zlib = require('zlib');
const {
  assert, createRecorder, createSqlRunner, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/** The action every consultation button posts to. */
const CONSULT_POST = /\/encounter\/RequestConsultation(\?|$)/;
/** A service has to be picked before the form will submit at all. */
const SERVICE_SEARCH = 'Cardio';

const fixture = {
  sql: null,
  requestId: '',
  demographicNo: '',
  sessionId: '',
};

/** A plain integer, or the value never reaches a query. */
function sqlNumber(value, what) {
  assert(/^\d+$/.test(String(value)), `${what} is not a plain number, so it cannot be used in a database query`);
  return String(value);
}

/**
 * Remove what the run wrote, every statement attempted, any failure thrown.
 *
 * Thrown rather than logged: runCheck() promotes a cleanup error to FAIL, while a
 * `process.exitCode = 1` set here is overwritten when runCheck() records the PASS
 * the assertions earned.
 */
async function cleanup() {
  const { sql, requestId, demographicNo, sessionId } = fixture;
  if (!sql) {
    return;
  }
  const statements = [];
  if (requestId) {
    statements.push(['the consultation this run created',
      `DELETE FROM consultationRequests WHERE requestId = ${requestId}`]);
  }
  if (demographicNo && sessionId) {
    statements.push(['this session\'s note lock',
      `DELETE FROM casemgmt_note_lock WHERE demographic_no = ${demographicNo} AND session_id = '${sessionId}'`]);
  }
  const failures = [];
  for (const [what, statement] of statements) {
    try {
      sql.execute(statement);
    } catch (error) {
      failures.push(`${what}: ${(error && error.message) || 'delete failed'}`);
    }
  }
  sql.dispose();
  if (failures.length) {
    throw new Error(`the check could not remove what it wrote (${failures.join('; ')})`);
  }
}

/**
 * The text drawn in a PDF, near enough for an assertion.
 *
 * The consultation PDF is generated text, not a scan: every string is a literal in
 * a Flate-compressed content stream, so inflating the streams and reading the
 * literals is enough to tell one referral's wording from another's. No PDF library
 * is pulled in for this.
 */
function pdfText(pdfBuffer) {
  const parts = [];
  const streams = /stream\r?\n/g;
  let match = streams.exec(pdfBuffer.toString('latin1'));
  const raw = pdfBuffer.toString('latin1');
  while (match) {
    const start = match.index + match[0].length;
    const end = raw.indexOf('endstream', start);
    if (end > start) {
      try {
        parts.push(zlib.inflateSync(Buffer.from(raw.slice(start, end), 'latin1')).toString('latin1'));
      } catch {
        // Not a Flate stream (fonts, images): nothing this check needs is in one.
      }
    }
    match = streams.exec(raw);
  }
  return parts.join('\n');
}

/** Pick a service through the autocomplete, the only thing that fills the posted field. */
async function chooseService(page, timeout) {
  const input = page.locator('#serviceInput');
  await input.click({ timeout });
  await input.pressSequentially(SERVICE_SEARCH, { delay: 50 });
  const suggestion = page.locator('ul.ui-autocomplete li:visible, ul.ui-menu li:visible').first();
  await suggestion.waitFor({ state: 'visible', timeout });
  await suggestion.click();
  const chosen = await page.evaluate(() => (document.getElementById('service') || {}).value);
  assert(chosen && chosen !== '0',
    'no consultation service could be picked, and the form refuses to submit without one');
}

async function main() {
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const searchTerm = process.env.CONSULT_PREVIEW_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.CONSULT_PREVIEW_DEMOGRAPHIC_NO || '2';
  const timeout = Number(process.env.CONSULT_PREVIEW_TIMEOUT_MS || '45000');
  const saved = `PW_CONSULT_SAVED_${Date.now()}`;
  const typed = `PW_CONSULT_TYPED_${Date.now()}`;

  const sql = createSqlRunner(config.mysql);
  fixture.sql = sql;

  const recorder = createRecorder();
  let browser;
  try {
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const sessionCookie = (await context.cookies()).find((cookie) => cookie.name === 'JSESSIONID');
    assert(sessionCookie && /^[A-Za-z0-9._-]+$/.test(sessionCookie.value),
      'the login left no JSESSIONID cookie, so the note lock this session takes could not be told from another session\'s');
    fixture.sessionId = sessionCookie.value;

    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });
    const { page: chartPage } = await clickOpensPopupOrNavigates(masterPage,
      masterPage.locator('a').filter({ hasText: /^\s*E-?Chart\s*$/i }).first(),
      { context, label: 'echart', recorder, timeout, baseline: [] });
    await chartPage.waitForLoadState('networkidle', { timeout: 60000 }).catch(() => {});
    fixture.demographicNo = sqlNumber(new URL(chartPage.url()).searchParams.get('demographicNo') || '',
      'the demographicNo in the chart URL');

    // The chart's "+" for a new consultation, read from the menu rather than assembled here.
    const newConsultUrl = await chartPage.evaluate(() => {
      for (const anchor of document.querySelectorAll('a')) {
        const onclick = anchor.getAttribute('onclick') || '';
        if (/ViewConsultationFormRequest/.test(onclick)) {
          const match = /popupPage\([^,]+,[^,]+,\s*'[^']*'\s*,\s*'([^']+)'/.exec(onclick);
          // One pass over both escapings the JSP applies; see form-print-pdf's note.
          if (match) return match[1].replace(/\\x26|&amp;/g, '&');
        }
      }
      return '';
    });
    assert(newConsultUrl, 'the chart offers no way to start a consultation, so this check has nothing to edit');

    // --- create a referral of this check's own, with known stored text ---
    const newPage = await context.newPage();
    await newPage.goto(new URL(newConsultUrl, config.baseUrl).toString(), { waitUntil: 'domcontentloaded', timeout }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- the URL comes from the application's own rendered chart menu, resolved against the validated base URL
    await newPage.waitForLoadState('networkidle', { timeout: 60000 }).catch(() => {});
    await newPage.locator('textarea[name="reasonForConsultation"]').fill(`REASON ${saved}`);
    await newPage.locator('textarea[name="clinicalInformation"]').fill(`CLINICAL ${saved}`);
    await chooseService(newPage, timeout);
    await newPage.locator('input[name="submitSaveOnly"]').click({ timeout });
    await newPage.waitForTimeout(4000);

    fixture.requestId = sqlNumber(sql.value(
      `SELECT requestId FROM consultationRequests WHERE demographicNo = ${fixture.demographicNo} `
      + `AND reason LIKE 'REASON ${saved}%' ORDER BY requestId DESC LIMIT 1`,
    ), 'the consultation this run created');
    await newPage.close().catch(() => {});

    // --- reopen it the way the chart does, type over it, and press Print ---
    const editPage = await context.newPage();
    const previews = [];
    editPage.on('response', async (response) => {
      if (CONSULT_POST.test(response.url()) && response.request().method() === 'POST'
        && (response.headers()['content-type'] || '').includes('json')) {
        previews.push(await response.json().catch(() => null));
      }
    });
    await editPage.goto(
      `${config.baseUrl}/encounter/ViewRequest?de=${fixture.demographicNo}&requestId=${fixture.requestId}&appNo=0&teamVar=`,
      { waitUntil: 'domcontentloaded', timeout }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- every interpolated value is a validated integer and the base URL is validated
    await editPage.waitForLoadState('networkidle', { timeout: 60000 }).catch(() => {});

    const printButton = editPage.locator('input[name="printPreview"]');
    assert(await printButton.count() > 0,
      'the consultation edit screen offers no Print button; it renders the new-request buttons '
      + 'unless it was reached through /encounter/ViewRequest');

    await editPage.locator('textarea[name="reasonForConsultation"]').fill(`REASON ${typed}`);
    await editPage.locator('textarea[name="clinicalInformation"]').fill(`CLINICAL ${typed}`);
    await printButton.click({ timeout });
    await editPage.waitForTimeout(8000);

    assert(previews.length > 0 && previews[0],
      'pressing Print returned no preview, so the print path was never exercised');
    const preview = previews[previews.length - 1];
    assert(!preview.errorMessage, `the preview reported an error: ${preview.errorMessage}`);
    assert(preview.consultPDF, 'the preview reply carried no PDF');
    const text = pdfText(Buffer.from(preview.consultPDF, 'base64'));

    assert(text.includes(typed),
      'the print preview does not contain the text typed into the consultation; it is rendering the '
      + 'stored record and discarding the form it was posted (issue #3721)');
    assert(!text.includes(saved),
      'the print preview still contains the text that was typed over, so it is the stored record');

    // Print previews. It must not file the referral.
    const stored = sql.value(
      `SELECT reason FROM consultationRequests WHERE requestId = ${fixture.requestId}`,
    );
    assert(stored === `REASON ${saved}`,
      'pressing Print changed the stored consultation; Print previews unsaved work, it does not save it');

    console.log('  Print preview showed the unsaved consultation text and left the stored referral alone');
    await editPage.close().catch(() => {});
    await chartPage.close().catch(() => {});
    return { previews: previews.length };
  } finally {
    if (browser) await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'consultation-print-preview', run: main, cleanup });
}

module.exports = { CONSULT_POST, cleanup, fixture, main, pdfText };
