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
 *   FORM_PRINT_DEMOGRAPHIC_NO=2       which patient's chart to open. Defaults to 2, not 1:
 *                                     demographic 1's chart answers 500 on the demo
 *                                     dataset, because its HRM rows point at report files
 *                                     that never shipped
 *   FORM_PRINT_NAME=Lab Req 2007      the Forms menu entry to open (one of the lab requisitions
 *                                     in FORM_TABLES; printing saves, so the table has to be known)
 *   FORM_PRINT_TIMEOUT_MS=45000       per-step allowance
 */

const {
  SkipCheck, assert, createRecorder, createSqlRunner, launchBrowser, login, newContext, readConfig,
  runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/** The action every encounter form posts to, whatever the button. */
const FORM_POST = /\/form\/formname(\?|$)/;

/**
 * Forms this check knows how to clean up after, and the table each saves into.
 *
 * Printing SAVES the record first, so a form whose table is not named here would leave its row
 * behind; FORM_PRINT_NAME is therefore restricted to this map rather than accepting any menu entry.
 * All three are lab requisitions because those are the forms that send the lowercase token the
 * issue is about.
 */
const FORM_TABLES = {
  'Lab Req 2007': 'formLabReq07',
  'Lab Req 2010': 'formLabReq10',
  'Lab Req': 'formLabReq',
};

/**
 * The field this check stamps so the row it creates can be told from every other row.
 *
 * "Additional Clinical Information" is a free-text area on all three requisitions, and it lands in
 * the `aci` column of each of their tables. Stamping it is what makes cleanup an identity rather
 * than a guess: a snapshot difference or an id range would also capture a row a clinician or a
 * concurrent check saved for the same patient while this one was running, and that is real chart
 * data.
 */
const STAMP_FIELD = 'aci';

/**
 * The client reference as FrmLabReq07Record renders it, capturing the record id it carries.
 *
 * `encounter.form.labreq.clientreference` in the English bundle, then the id. The label is part of
 * the pattern because a bare number would also match a postal code or a phone number elsewhere on
 * the form; the id is a capture rather than interpolated into the pattern, so this stays one fixed
 * literal and no regular expression is ever built from a value (Semgrep
 * `detect-non-literal-regexp`, and the ReDoS class it guards against).
 */
const CLIENT_REFERENCE = /Client Reference No\.\s*:\s*(\d+)/;

/**
 * The literal text drawn inside a PDF, as far as a regression check needs to read it.
 *
 * Not a PDF parser and not trying to be one. iText writes page content into Flate-compressed
 * streams and shows text with `(...)Tj` / `[...]TJ`, so inflating every stream and pulling the
 * string literals out is enough to answer the only question asked here: did this text reach the
 * page? Streams that do not inflate (images, fonts, anything not Flate) are skipped rather than
 * failed -- they hold no drawn text to miss.
 */
function pdfText(buffer) {
  const zlib = require('zlib');
  const out = [];
  const haystack = buffer.toString('latin1');
  // NOT PRECEDED BY "end". The closing keyword is "endstream", which contains "stream" --
  // without the lookbehind the walk matches inside it, treats the gap to the NEXT endstream as
  // a stream, fails to inflate that, and so reads only the first content stream of the file.
  const stream = /(?<!end)stream\r?\n/g;
  const CLOSE = 'endstream';
  let match;
  while ((match = stream.exec(haystack)) !== null) {
    const start = match.index + match[0].length;
    const end = haystack.indexOf(CLOSE, start);
    if (end < 0) break;
    // Resume past the closing keyword whatever happens below, so a stream that does not inflate
    // costs only itself.
    stream.lastIndex = end + CLOSE.length;
    let text;
    try {
      text = zlib.inflateSync(Buffer.from(haystack.slice(start, end), 'latin1')).toString('latin1');
    } catch { continue; }
    // \( and \) are escaped parentheses inside a PDF string, not its delimiters.
    for (const literal of text.matchAll(/\((?:\\.|[^\\()])*\)/g)) {
      out.push(literal[0].slice(1, -1).replace(/\\([()\\])/g, '$1'));
    }
  }
  return out.join('\n');
}

const fixture = {
  sql: null,
  table: '',
  /** The marker this run types into the form; the only thing that identifies its row. */
  stamp: '',
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
  const { sql, table, stamp, demographicNo, sessionId } = fixture;
  if (!sql) {
    return;
  }
  const statements = [];
  if (table && stamp) {
    // IDENTIFIED BY THE STAMP THIS RUN TYPED, not by an id range or a snapshot difference: both of
    // those also match a row a clinician or a concurrent check saved for the same patient while
    // this one was running. Resolved in the cleanup hook rather than at the end of the run because
    // Print saves the record before it renders, so an assertion that fails after the POST would
    // otherwise leave the row behind.
    // EXACT MATCH, NOT LIKE. The stamp contains underscores and `_` is a single-character
    // wildcard in LIKE, so `LIKE 'PW_FORM_PRINT_17...%'` also matches a clinician's note that
    // happens to differ in exactly those positions. Scoped to the patient this run opened as
    // well, so a stamp collision cannot reach another chart's rows.
    statements.push(['the form rows this run saved',
      `DELETE FROM ${table} WHERE ${STAMP_FIELD} = '${stamp}'`
      + (demographicNo ? ` AND demographic_no = ${demographicNo}` : '')]);
  }
  if (demographicNo && sessionId) {
    statements.push(['this session\'s note lock',
      `DELETE FROM casemgmt_note_lock WHERE demographic_no = ${demographicNo} AND session_id = '${sessionId}'`]);
  }
  const failures = [];
  try {
    for (const [what, statement] of statements) {
      try {
        sql.execute(statement);
      } catch (error) {
        failures.push(`${what}: ${(error && error.message) || 'delete failed'}`);
      }
    }
  } finally {
    // ALWAYS, EVEN ON THE WAY OUT OF A THROW. createSqlRunner() writes MYSQL_PASSWORD into a
    // temporary client.cnf, and dispose() is what removes it; a failure that skipped this would
    // leave the credential on disk.
    sql.dispose();
  }
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
async function formMenuUrl(chartPage, formName, which = 'blank') {
  return chartPage.evaluate(([name, wanted]) => {
    for (const anchor of document.querySelectorAll('a')) {
      // THE LABEL CAN BE DECORATED. EctDisplayForm2Action wraps a started Lab Req 2007 in
      // asterisks when no lab report is linked to it, so the raw text of that entry reads
      // "*Lab Req 2007*". Strip the decoration before comparing rather than matching the
      // raw text, so the menu's own presentation cannot decide whether this check runs.
      const label = (anchor.textContent || '').trim().replace(/^\*+|\*+$/g, '');
      if (label.startsWith(name)) {
        const match = /popupPage\([^,]+,[^,]+,\s*'[^']*'\s*,\s*'([^']+)'/.exec(anchor.getAttribute('onclick') || '');
        // WHICH OF THE TWO ENTRIES. The same menu lists both: a started form links to
        // /form/forwardshortcutname?...&formId=latest, a blank one to the form route with
        // formId=0. They are not interchangeable -- pressing Print on a started form UPDATES
        // whatever record is newest, so the default ('blank') is what keeps this check to a row
        // it created itself, and it is also why undecorating the label above is safe. The
        // 'started' entry is asked for only after this run has saved its own row, so "newest"
        // is that row.
        // ONE PASS OVER THE SOURCE, both spellings in the same alternation. The JSP escapes the
        // ampersands twice over -- once for the JavaScript string literal (\x26) and once for
        // the HTML attribute (&amp;) -- and decoding them in two chained replaces would rescan
        // the output of the first, so a literal "\x26amp;" would come out as a bare "&".
        // Decoded BEFORE the formId test below, or that test reads "\x26formId=0" and never matches.
        const url = match ? match[1].replace(/\\x26|&amp;/g, '&') : '';
        const matches = wanted === 'started'
          ? /[?&]formId=latest(&|$)/.test(url)
          : /[?&]formId=0(&|$)/.test(url);
        if (matches) {
          return url;
        }
      }
    }
    return '';
  }, [formName, which]);
}

/**
 * Open a form at `url`, press Print, and return the text of the PDF that came back.
 *
 * Used for the control print only: the main flow stamps the form before printing and asserts a
 * good deal more about the answer, and is written out inline for that reason.
 */
async function printAndRead(context, config, url, { timeout, posts, pdfBodies }) {
  const before = pdfBodies.length;
  const page = await context.newPage();
  try {
    await page.goto(new URL(url, config.baseUrl).toString(), { waitUntil: 'domcontentloaded', timeout }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- the URL comes from the application's own rendered Forms menu, resolved against the validated base URL
    await page.waitForLoadState('networkidle', { timeout: 60000 }).catch(() => {});
    const printButton = page.locator('input[value*="Print" i], button:has-text("Print"), a:has-text("Print")').first();
    if (await printButton.count() === 0) return '';
    const postsBefore = posts.length;
    await printButton.click({ timeout }).catch(() => {});
    // Wait on the answer rather than a clock, for the same reason the main flow does.
    const deadline = Date.now() + timeout;
    while (pdfBodies.length === before && posts.length === postsBefore && Date.now() < deadline) {
      await page.waitForTimeout(250);
    }
    while (pdfBodies.length === before && Date.now() < deadline) {
      await page.waitForTimeout(250);
    }
    return pdfBodies.slice(before).map((body) => pdfText(body)).join('\n');
  } finally {
    await page.close().catch(() => {});
  }
}

async function main() {
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const searchTerm = process.env.FORM_PRINT_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.FORM_PRINT_DEMOGRAPHIC_NO || '2';
  const formName = process.env.FORM_PRINT_NAME || 'Lab Req 2007';
  const timeout = Number(process.env.FORM_PRINT_TIMEOUT_MS || '45000');

  const table = FORM_TABLES[formName];
  assert(table, `FORM_PRINT_NAME must name a form this check can clean up after `
    + `(${Object.keys(FORM_TABLES).join(', ')}); printing saves the record, so a form whose table `
    + 'is unknown would leave its row in the chart');

  const sql = createSqlRunner(config.mysql);
  fixture.sql = sql;
  fixture.table = table;

  // ONTARIO ONLY. The lab requisition tables come from the Ontario migration
  // (database/mysql/migration/on/V1.0.1__on_schema.sql), so on a BC deployment the first query
  // would fail with a missing table long before anything was proved. Skip, saying which.
  const tableExists = sql.value(
    'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() '
    + `AND table_name = '${table}'`,
  );
  if (tableExists !== '1') {
    throw new SkipCheck(`this deployment has no ${table}; the lab requisitions are Ontario forms, `
      + 'so there is nothing here to print');
  }

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

    // THE STAMP, RECORDED BEFORE ANYTHING IS WRITTEN. Print saves the record, and cleanup has to
    // remove that row without touching a clinician's. Typed into the form below, so the row carries
    // it and can be named exactly -- unlike an id range or a snapshot difference, both of which also
    // match whatever somebody else saved for this patient while the check was running.
    fixture.stamp = `PW_FORM_PRINT_${Date.now()}`;

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

    // The bytes the server sent, which the response listener above cannot give.
    //
    // WHY A ROUTE AND NOT response.body(). A PDF navigation is handed to Chromium's built-in
    // viewer, and by the time the response object is readable its body is the viewer's own
    // wrapper markup ("<embed ... type='application/pdf' src='about:blank'>", ~345 bytes) --
    // never the document. Routing the request instead lets the check read the real response
    // and then hand that same response to the renderer, so the flow is still the one a
    // clinician drives and exactly one form row is saved.
    const pdfBodies = [];
    await context.route(FORM_POST, async (route) => {
      let answer;
      try {
        answer = await route.fetch();
      } catch { await route.continue().catch(() => {}); return; }
      const buffer = await answer.body().catch(() => null);
      if (buffer) pdfBodies.push(buffer);
      await route.fulfill({ response: answer, body: buffer || undefined }).catch(() => {});
    });
    context.on('page', (page) => page.on('response', record));

    const formPage = await context.newPage();
    formPage.on('response', record);
    await formPage.goto(new URL(menuUrl, config.baseUrl).toString(), { waitUntil: 'domcontentloaded', timeout }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- the URL comes from the application's own rendered Forms menu, resolved against the validated base URL
    await formPage.waitForLoadState('networkidle', { timeout: 60000 }).catch(() => {});

    // Stamp the record before printing: Print saves it, so the stamp is what cleanup will find.
    const stampField = formPage.locator(`textarea[name="${STAMP_FIELD}"], input[name="${STAMP_FIELD}"]`).first();
    assert(await stampField.count() > 0,
      `${formName} renders no ${STAMP_FIELD} field, so the row this check is about to create could `
      + 'not be told apart from a clinician\'s afterwards');
    await stampField.fill(fixture.stamp);

    const printButton = formPage.locator('input[value*="Print" i], button:has-text("Print"), a:has-text("Print")').first();
    assert(await printButton.count() > 0,
      `${formName} renders no Print button, so there is nothing for this check to press`);
    // WAIT FOR THE ANSWER, NOT A CLOCK. The print opens a window and the PDF is generated
    // server-side, so a fixed sleep reports "posted nothing" on a slow render while the request is
    // still in flight -- and leaves the check's own step timeout unused.
    const answered = new Promise((resolve) => {
      const done = () => resolve();
      if (posts.length) { done(); return; }
      const poll = setInterval(() => {
        if (posts.length) { clearInterval(poll); done(); }
      }, 250);
      setTimeout(() => { clearInterval(poll); done(); }, timeout);
    });
    await printButton.click({ timeout });
    await answered;

    assert(posts.length > 0,
      `pressing Print posted no answer within ${timeout}ms, so the print path was never exercised`);
    const failed = posts.filter((post) => post.status >= 400);
    assert(failed.length === 0,
      `Print answered HTTP ${(failed[0] || {}).status} instead of a PDF. A 500 is the submit token `
      + 'resolving to "failure", a 404 is /form/createpdf being routed as a Struts action (issue #3735)');
    const pdfs = posts.filter((post) => post.contentType.includes('application/pdf'));
    assert(pdfs.length > 0,
      `Print answered ${JSON.stringify(posts.map((post) => post.contentType))} rather than application/pdf; `
      + 'the request succeeded but what came back is not the form');

    // THE PAGE, NOT JUST THE ENVELOPE. The print forwards to /form/createpdf with its own
    // query string, and a forward's query string is AGGREGATED with the request's parameters
    // (Servlet spec, "Query String") rather than replacing them -- but nothing in a status
    // line says so. If __cfgfile or __template were lost on the way, FrmPDFServlet would
    // still answer 200 application/pdf and simply draw nothing. Asserting the text this run
    // typed is what tells a real requisition from an empty one.
    const drawn = pdfBodies.map((body) => pdfText(body)).join('\n');
    assert(drawn.includes(fixture.stamp),
      'the PDF came back but carries none of the text this run typed into the form, so the print '
      + 'lost the parameters that place the fields (__cfgfile/__template) on the way to the servlet');

    // Print saves the record before it renders, so the stamped row must exist.
    const savedIds = sql.rows(
      `SELECT ID FROM ${table} WHERE ${STAMP_FIELD} = '${fixture.stamp}' `
      + `AND demographic_no = ${sqlNumber(fixture.demographicNo, 'the patient key')}`,
    ).map((row) => row[0]);
    assert(savedIds.length > 0,
      'Print returned a PDF but saved no form row, so the record it printed was never stored');

    // THE ID THE SAVE PRODUCED, PROVEN ON THE PAGE. The page posts formId=0 for a brand-new
    // record; only the forwarded ?formId=${savedFormId} tells FrmPDFServlet which row was just
    // written. FrmLabReq07Record fills clientRefNo as "<label>:<id>" and only on the branch that
    // loads an existing id, and labReqPrint07.txt places it, so that number appearing on the page
    // is end-to-end evidence the saved id reached the servlet. With the stale 0 the field is
    // simply absent. Restricted to Lab Req 2007 because it is the form that carries the field.
    // THE SAVED ID, PROVEN ON THE PAGE.
    //
    // The page posts formId=0 for a brand-new record; only the forwarded ?formId=${savedFormId}
    // tells FrmPDFServlet which row was just written. Nothing else on the document can show
    // that: every other value also arrives as a POST parameter and FrmPDFServlet overlays those
    // on top of whichever record it loaded, so a print of the wrong record still looks right.
    // clientRefNo is the exception -- FrmLabReq07Record sets it only on the branch that loads an
    // existing id, and it holds that id verbatim.
    //
    // WHY THE SECOND PRINT. clientRefNo is also gated on use_lab_clientreference, which defaults
    // to true in code but ships false on the deb, so "no client reference on the page" is
    // ambiguous: either the setting is off, or the id never arrived. Printing the STARTED form
    // entry settles it. That path carries the id in its own menu URL (formId=latest) and so does
    // not depend on this PR's forward at all: if the reference appears there, the setting is on
    // and its absence from the first print is the regression; if it appears in neither, the
    // setting is off and this half genuinely cannot be exercised here. The second print reprints
    // the row the first one saved -- this run's own -- so it writes nothing new to the chart.
    const reference = CLIENT_REFERENCE.exec(drawn);
    if (reference) {
      assert(reference[1] === String(savedIds[0]),
        `the PDF shows client reference ${reference[1]}, not row ${savedIds[0]} that Print had just `
        + 'saved, so FrmPDFServlet rendered some other record');
    } else {
      const startedUrl = await formMenuUrl(chartPage, formName, 'started');
      const control = startedUrl
        ? await printAndRead(context, config, startedUrl, { timeout, posts, pdfBodies })
        : '';
      assert(!CLIENT_REFERENCE.test(control),
        'reprinting the saved record shows a client reference while printing it the first time '
        + 'did not, so the id the save produced never reached FrmPDFServlet and the first print '
        + 'rendered the new-form defaults (issue #3935)');
      console.log('  (no client reference on either print -- use_lab_clientreference is off on '
        + 'this deployment, so the saved-id half cannot be exercised here)');
    }

    console.log(`  ${formName}: Print answered 200 application/pdf carrying the saved record`);
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

module.exports = {
  CLIENT_REFERENCE, FORM_POST, FORM_TABLES, cleanup, fixture, formMenuUrl, main, pdfText,
};
