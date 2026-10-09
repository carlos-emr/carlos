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
 * WHAT IT WRITES, AND REMOVES. One consultation request for a FAKE patient it creates
 * (lib/owned-patient.js: last name = a FAKE-PW run marker), created through the UI so the
 * check has a referral of its own to edit, and the note lock the chart takes. Saving a
 * consultation also stores the provider's signature image against the patient and writes
 * extension and archive rows; the request, those rows, the note lock and the patient are
 * removed afterwards by the patient's key through MYSQL_*. It used to run on DEMO patient 2,
 * delete only the request and leave a DigitalSignature and a consultationRequestExt row
 * behind on every run. It never edits a consultation it did not create: a clinician's
 * referral is not something a test types into.
 *
 * NO PATIENT KEY IN THE OUTPUT. demographic_no joins straight back to a patient
 * record and runCheck() writes thrown messages into CI artifacts, so the
 * diagnostics say "the selected patient" and never print the chart URL.
 *
 * Defaults are for the local devcontainer:
 *   MYSQL_PASSWORD=... npm run test:consultation-print-preview-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   CONSULT_PREVIEW_TIMEOUT_MS=45000      per-step allowance
 */

const pdf = require('./lib/export-content-helpers');
const {
  assert, createRecorder, createSqlRunner, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { CONSULTATION_ROWS, createOwnedPatient, newOwnedMarker, removeOwnedPatient } = require('./lib/owned-patient');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/** The action every consultation button posts to. */
const CONSULT_POST = /\/encounter\/RequestConsultation(\?|$)/;
/** A service has to be picked before the form will submit at all. */
const SERVICE_SEARCH = 'Cardio';

const fixture = {
  sql: null,
  /** The marker this run put in the referral's reason, recorded BEFORE the id is looked up. */
  stamp: '',
  requestId: '',
  demographicNo: '',
  sessionId: '',
  /** The owned patient the referral is written for, and the marker that identifies it (never a demo patient). */
  ownedPatient: '',
  ownedMarker: '',
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
  const { sql, stamp, requestId, demographicNo, sessionId, ownedPatient, ownedMarker } = fixture;
  if (!sql) {
    return;
  }
  const failures = [];
  if (ownedPatient) {
    // FIRST: the request's extension, archive and document rows are found through the request, which the statements below delete.
    try {
      // The request, its extension, archive and document rows, the signature stored against the patient, the chart rows, the patient.
      removeOwnedPatient(sql, ownedPatient, ownedMarker, CONSULTATION_ROWS);
    } catch (error) {
      failures.push(`the owned patient: ${(error && error.message) || 'delete failed'}`);
    }
  }
  const statements = [];
  if (requestId) {
    statements.push(['the consultation this run created',
      `DELETE FROM consultationRequests WHERE requestId = ${requestId}`]);
  } else if (stamp) {
    // OWNERSHIP BY MARKER, WHEN THE ID LOOKUP NEVER HAPPENED. The referral is created through the
    // UI before this run learns its id, so a failure in between would otherwise leave it in the
    // chart. The stamp is unique to this run and was written into the reason, so it identifies
    // exactly the row this check created and nothing else.
    statements.push(['the consultation this run created, found by its marker',
      `DELETE FROM consultationRequests WHERE reason LIKE 'REASON ${stamp}%'`]);
  }
  if (demographicNo && sessionId) {
    statements.push(['this session\'s note lock',
      `DELETE FROM casemgmt_note_lock WHERE demographic_no = ${demographicNo} AND session_id = '${sessionId}'`]);
  }
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

/** Extracts actual rendered Unicode text, reflowing whitespace for preview assertions. */
function pdfText(pdfBuffer) {
  return pdf.pdfTextBuffer(pdfBuffer).replace(/\s+/g, ' ').trim();
}

/**
 * Pick a service through the autocomplete, the only thing that fills the posted field.
 *
 * Answers false when the deployment renders no picker: with
 * ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS on, the form posts a hidden service fixed at
 * "0" and there is nothing to choose. This check runs in the core tier for every province, so it
 * must not fail at a locator that such a deployment legitimately does not have.
 */
async function chooseService(page, timeout) {
  const input = page.locator('#serviceInput');
  if (await input.count() === 0) {
    return false;
  }
  await input.click({ timeout });
  await input.pressSequentially(SERVICE_SEARCH, { delay: 50 });
  const suggestion = page.locator('ul.ui-autocomplete li:visible, ul.ui-menu li:visible').first();
  await suggestion.waitFor({ state: 'visible', timeout });
  await suggestion.click();
  const chosen = await page.evaluate(() => (document.getElementById('service') || {}).value);
  assert(chosen && chosen !== '0',
    'no consultation service could be picked, and the form refuses to submit without one');
  return true;
}

async function main() {
  pdf.requirePoppler('pdftotext');
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const timeout = Number(process.env.CONSULT_PREVIEW_TIMEOUT_MS || '45000');
  const saved = `PW_CONSULT_SAVED_${Date.now()}`;
  // Recorded before anything is created: cleanup can find the referral by this alone.
  fixture.stamp = saved;
  const typed = `PW_CONSULT_TYPED_${Date.now()} Nguyễn Łukasz İstanbul ≥ 5 ≤ 9`;

  const sql = createSqlRunner(config.mysql);
  fixture.sql = sql;
  // The owned patient, recorded before it exists so cleanup can still find it: its marker is the search term.
  fixture.ownedMarker = newOwnedMarker();
  const searchTerm = fixture.ownedMarker;
  const provider = sql.value(`SELECT provider_no FROM security WHERE user_name='${config.testUser.replace(/'/g, "''")}'`);
  assert(provider, 'the configured test login has no provider');
  const preferredDemographicNo = createOwnedPatient(sql, { marker: fixture.ownedMarker, provider });
  fixture.ownedPatient = preferredDemographicNo;

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
    const servicePicked = await chooseService(newPage, timeout);
    if (!servicePicked) {
      console.log('  no service picker on this deployment (health care team mode); '
        + 'the referral is created without one');
    }
    // Wait for the save POST itself, not a guessed interval: on a slow CI host a fixed wait can
    // expire before the row exists, and the SQL lookup below then fails for a reason that has
    // nothing to do with what this check is testing.
    await Promise.all([
      newPage.waitForResponse(
        (response) => CONSULT_POST.test(response.url()) && response.request().method() === 'POST',
        { timeout: 60000 },
      ),
      newPage.locator('input[name="submitSaveOnly"]').click({ timeout }),
    ]);

    fixture.requestId = sqlNumber(sql.value(
      `SELECT requestId FROM consultationRequests WHERE demographicNo = ${fixture.demographicNo} `
      + `AND reason LIKE 'REASON ${saved}%' ORDER BY requestId DESC LIMIT 1`,
    ), 'the consultation this run created');
    await newPage.close().catch(() => {});

    // --- reopen it the way the chart does, type over it, and press Print ---
    const editPage = await context.newPage();
    const previews = [];
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
    // Await the preview reply itself. Reading the body from the awaited response, rather than from
    // an async 'response' listener, is what keeps this race-free: a listener that still has to run
    // response.json() can lose to the assertion below, a race the old fixed wait papered over.
    const previewArrived = editPage.waitForResponse(
      (response) => CONSULT_POST.test(response.url()) && response.request().method() === 'POST'
        && (response.headers()['content-type'] || '').includes('json'),
      { timeout: 60000 },
    );
    await printButton.click({ timeout });
    previews.push(await (await previewArrived).json().catch(() => null));

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
