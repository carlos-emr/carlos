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
 * Browser regression check for Print Pdf on an encounter form: does the button
 * a clinician presses actually return a PDF?
 *
 * WHY THIS CHECK EXISTS (issue #3735). Printing a lab requisition from the
 * chart answered an error page instead of the form. Three separate breaks sat
 * on the one path, and each produced a different status, which is why the issue
 * reads as several bugs at once:
 *
 *   1. The lab requisition JSPs post submit="printall" while the rest of the
 *      forms post "printAll". FrmRecordHelp.findActionValue() looked the token
 *      up in an exact-case set, answered "failure", and "failure" is a mapped
 *      Struts result pointing at the error page -- HTTP 500, after the record
 *      had already been saved.
 *   2. Even spelled canonically, Frm2Action returned createActionURL()'s
 *      Struts 1 style forward ("printAll?demographic_no=1&formId=6") as the
 *      Struts 2 RESULT NAME. Nothing is called that, so the request ended as
 *      "No result defined for action ...".
 *   3. With the result resolving, it forwards to /form/createpdf -- a servlet
 *      declared in web.xml. The Struts filters are mapped for FORWARD as well
 *      as REQUEST and struts.action.extension is empty, so that path was
 *      resolved as an action and answered 404 before reaching the servlet.
 *
 * WHY IT READS THE RESPONSE RATHER THAN THE PAGE. A PDF delivered to a popup
 * leaves an empty DOM, and so does an error page that has been sanitized. Only
 * the response tells them apart, so the check asserts the status and the
 * Content-Type of the POST the button makes, not what the window looks like.
 *
 * WHY A LAB REQUISITION. It is the form the issue was reported against, and it
 * is one of the seven that send the lowercase token, so it exercises all three
 * breaks. A form that sends "printAll" would miss the first.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, Search, the patient's Master
 * Record, E-Chart, then the chart's own Forms menu entry for the requisition
 * and the form's own Print Pdf button. The menu entry's URL is read from the
 * chart rather than guessed, so a change to how the chart builds it fails here
 * rather than being silently worked around.
 *
 * WHAT IT WRITES, AND REMOVES. Print Pdf saves the form first (that is the
 * behaviour, not a side effect of the check), so a row lands in the form's own
 * table, and opening the chart takes a note lock. Both are removed afterwards
 * through MYSQL_*: the form row by the id the run observed, the lock by THIS
 * browser session's id, so another session's lock on the same patient is left
 * alone. Cleanup runs through runCheck()'s cleanup hook: a delete that fails is
 * a FAIL whatever the assertions said.
 *
 * NO PATIENT KEY IN THE OUTPUT. demographic_no joins straight back to a patient
 * record and runCheck() writes thrown messages into CI artifacts, so the
 * diagnostics say "the selected patient" and never print the chart URL.
 *
 * Defaults are for the local devcontainer:
 *   MYSQL_PASSWORD=... npm run test:form-print-pdf-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   FORM_PRINT_SEARCH=FAKE-           surname prefix used to reach a patient
 *   FORM_PRINT_DEMOGRAPHIC_NO=1       which patient's chart to open
 *   FORM_PRINT_NAME=Lab Req 2007      the Forms menu entry to open
 *   FORM_PRINT_TIMEOUT_MS=45000       per-step allowance
 */

const {
  assert, createRecorder, createSqlRunner, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/** The action every encounter form posts to, whatever the button. */
const FORM_POST = /\/form\/formname(\?|$)/;
/** The table the lab requisition saves into; the row this run creates is removed from it. */
const FORM_TABLE = 'formLabReq07';

const fixture = {
  sql: null,
  /** Largest form-table id that existed BEFORE this run; anything above it is this run's. */
  highWaterMark: '',
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
 * Thrown rather than logged: runCheck() promotes a cleanup error to FAIL, while
 * a `process.exitCode = 1` set here is overwritten when runCheck() records the
 * PASS the assertions earned.
 */
async function cleanup() {
  const { sql, highWaterMark, demographicNo, sessionId } = fixture;
  if (!sql) {
    return;
  }
  const statements = [];
  if (highWaterMark && demographicNo) {
    // Resolved HERE rather than at the end of the run: Print saves the record
    // before it renders, so an assertion that fails after the POST still leaves
    // a row behind. Bounded by the mark read before anything was written, so a
    // clinician's requisition is never in range.
    statements.push(['the form rows this run saved',
      `DELETE FROM ${FORM_TABLE} WHERE ID > ${highWaterMark} AND demographic_no = ${demographicNo}`]);
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
 * The URL the chart's Forms menu builds for one form.
 *
 * Read from the rendered menu rather than assembled here: the menu item is an
 * onclick calling popupPage(...) with the URL as its fourth argument, and the
 * JSP writes it with `&` escaped as \x26 for the JavaScript string literal.
 */
async function formMenuUrl(chartPage, formName) {
  return chartPage.evaluate((name) => {
    for (const anchor of document.querySelectorAll('a')) {
      if ((anchor.textContent || '').trim().startsWith(name)) {
        const match = /popupPage\([^,]+,[^,]+,\s*'[^']*'\s*,\s*'([^']+)'/.exec(anchor.getAttribute('onclick') || '');
        if (match) {
          // ONE PASS OVER THE SOURCE, both spellings in the same alternation. The JSP escapes the
          // ampersands twice over -- once for the JavaScript string literal (\x26) and once for
          // the HTML attribute (&amp;) -- and decoding them in two chained replaces would rescan
          // the output of the first, so a literal "\x26amp;" would come out as a bare "&".
          return match[1].replace(/\\x26|&amp;/g, '&');
        }
      }
    }
    return '';
  }, formName);
}

async function main() {
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const searchTerm = process.env.FORM_PRINT_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.FORM_PRINT_DEMOGRAPHIC_NO || '1';
  const formName = process.env.FORM_PRINT_NAME || 'Lab Req 2007';
  const timeout = Number(process.env.FORM_PRINT_TIMEOUT_MS || '45000');

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

    const menuUrl = await formMenuUrl(chartPage, formName);
    assert(menuUrl, `the chart's Forms menu offers no entry for ${formName}, so the print flow cannot be driven`);

    // THE HIGH-WATER MARK, READ BEFORE ANYTHING IS WRITTEN. Print saves the
    // record, and the cleanup has to remove that row without touching a
    // clinician's. Deleting "the newest rows" would do exactly that on a chart
    // that already holds requisitions, so the cleanup deletes only ids ABOVE
    // the largest one that existed before this run started.
    fixture.highWaterMark = sqlNumber(
      sql.value(`SELECT COALESCE(MAX(ID), 0) FROM ${FORM_TABLE}`) || '0',
      `the largest existing ${FORM_TABLE} id`,
    );

    // Every response the form window and its popups make for this action, with
    // the status and content type each answered.
    const posts = [];
    const record = (response) => {
      if (FORM_POST.test(response.url()) && response.request().method() === 'POST') {
        posts.push({
          status: response.status(),
          contentType: (response.headers()['content-type'] || '').toLowerCase(),
        });
      }
    };
    context.on('page', (page) => page.on('response', record));

    const formPage = await context.newPage();
    formPage.on('response', record);
    await formPage.goto(new URL(menuUrl, config.baseUrl).toString(), { waitUntil: 'domcontentloaded', timeout }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- the URL comes from the application's own rendered Forms menu, resolved against the validated base URL
    await formPage.waitForLoadState('networkidle', { timeout: 60000 }).catch(() => {});

    const printButton = formPage.locator('input[value*="Print" i], button:has-text("Print"), a:has-text("Print")').first();
    assert(await printButton.count() > 0,
      `${formName} renders no Print button, so there is nothing for this check to press`);
    await printButton.click({ timeout });
    // The print opens a window; give the POST behind it time to answer.
    await formPage.waitForTimeout(8000);

    assert(posts.length > 0, 'pressing Print posted nothing, so the print path was never exercised');
    const failed = posts.filter((post) => post.status >= 400);
    assert(failed.length === 0,
      `Print answered HTTP ${(failed[0] || {}).status} instead of a PDF. A 500 is the submit token `
      + 'resolving to "failure", a 404 is /form/createpdf being routed as a Struts action (issue #3735)');
    const pdfs = posts.filter((post) => post.contentType.includes('application/pdf'));
    assert(pdfs.length > 0,
      `Print answered ${JSON.stringify(posts.map((post) => post.contentType))} rather than application/pdf; `
      + 'the request succeeded but what came back is not the form');

    // Print saves the record before it renders, so a row must have appeared.
    const saved = Number(sql.value(
      `SELECT COUNT(*) FROM ${FORM_TABLE} WHERE ID > ${fixture.highWaterMark} `
      + `AND demographic_no = ${fixture.demographicNo}`,
    ) || '0');
    assert(saved > 0, 'Print returned a PDF but saved no form row, so the record it printed was never stored');

    console.log(`  ${formName}: Print answered 200 application/pdf for the selected patient`);
    await formPage.close().catch(() => {});
    await chartPage.close().catch(() => {});
    return { posts: posts.length };
  } finally {
    if (browser) await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'form-print-pdf', run: main, cleanup });
}

module.exports = { FORM_POST, FORM_TABLE, cleanup, fixture, formMenuUrl, main };
