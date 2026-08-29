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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for document upload, driven the way an operator does
 * it: log in, open the Inbox hub, click "Doc Upload" (which opens the legacy
 * 600x500 popup), choose a file, click "Upload File".
 *
 * Nothing covered this surface, which is how a tester reached 2026.08.0-alpha9
 * still reporting "upload documents gives a 500" after one fix attempt.
 *
 * WHICH uploader this drives matters, and it is not the one the property
 * suggests. The Inbox hub's "Doc Upload" link is chosen by
 *   <c:when test="${CarlosProperties.getInstance().getBooleanProperty(
 *                   'legacy_document_upload_enabled', 'true')}">
 * in InboxhubTopbar.jsp. `<%@ page import %>` exposes that class to SCRIPTLETS,
 * not to EL, so EL resolves the bare identifier as a scoped attribute, finds
 * nothing, and the test is always false -- the hub always offers the MODERN
 * uploader (documentUploader.jsp -> DocumentUpload2Action), whatever the
 * property says. Meanwhile oscarMDS/Index.jsp guards the same choice with a
 * scriptlet, which does honour it, so the two entry points can serve different
 * uploaders on one install.
 *
 * That is exactly why this check clicks the link instead of naming a route: it
 * follows whichever uploader the deployment actually serves, and it keeps
 * working if the guard is repaired.
 *
 * Reading the outcome needs care, because a 500 is this action's DESIGNED error
 * channel: sendHtml5UploadError answers a status plus an `oscar_error` header
 * carrying the message, and noswfupload.js reads that header. So the assertion
 * is not "no 500 anywhere" but "the upload completed and the row landed".
 *
 * Two scenarios:
 *   1. A single upload must succeed end to end -- HTTP < 400, no oscar_error,
 *      and a new row in the `document` table.
 *   2. Two uploads of the same file inside one second must NOT report success.
 *      The stored name is the file's own name prefixed with yyyyMMddHHmmss, so
 *      its resolution is one second and the second write collides. That is a
 *      user-recoverable condition: it must answer 409 with actionable text, not
 *      the opaque 500 that every handled upload failure used to return -- and
 *      the client must surface it, which it could not while it only treated
 *      status 500 as an error.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (to confirm the row landed)
 * Optional: CHROME_PATH, DOCUMENT_UPLOAD_SCREENSHOT_DIR (default /tmp).
 *
 * FIXTURE SAFETY: uploads a PDF this script generates under a unique,
 * timestamped name and only ever asserts on rows matching that name. It deletes
 * nothing; a failing run leaves its documents behind for diagnosis. Clear
 * strays with:
 *   DELETE FROM document WHERE docfilename LIKE '%carlos-upload-probe-%';
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  assert,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.DOCUMENT_UPLOAD_SCREENSHOT_DIR || '/tmp',
};
const mysqlHost = process.env.MYSQL_HOST || '127.0.0.1';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

const stamp = Date.now();
const probeName = `carlos-upload-probe-${stamp}.pdf`;

let mysqlDefaults = null;
function initMysqlDefaults() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'document-upload-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  mysqlDefaults = { dir, file };
}
function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}
function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
}

// A real, minimal PDF. The action sets contentType and counts pages for .pdf
// uploads, so a text file with a .pdf name would skip that branch entirely.
function writeProbePdf(dir) {
  const pdf = Buffer.from(
    '%PDF-1.4\n'
    + '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n'
    + '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
    + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n'
    + 'trailer<</Root 1 0 R>>\n'
    + '%%EOF\n',
    'latin1',
  );
  const file = path.join(dir, probeName);
  fs.writeFileSync(file, pdf);
  return file;
}

// Click "Doc Upload" in the Inbox hub and return the popup it opens.
async function openUploadPopup(context, recorder) {
  const inbox = await context.newPage();
  wirePage(inbox, 'inbox-hub', recorder);
  await gotoApp(inbox, config.baseUrl, '/web/inboxhub/Inboxhub');
  await inbox.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  // Match case-insensitively and accept BOTH uploaders. Which one the Inbox hub
  // offers is decided at render time, and on a packaged install it is currently
  // always the modern ViewDocumentUploader -- see the note in the header. The
  // point of clicking the real link is that this check follows whichever one the
  // deployment actually serves instead of pinning a route by hand.
  const docUpload = inbox
    .locator('a[href*="ViewHtml5AddDocuments" i], a[href*="ViewDocumentUploader" i]')
    .first();
  await docUpload.waitFor({ state: 'visible', timeout: 20000 });
  const [popup] = await Promise.all([
    inbox.waitForEvent('popup', { timeout: 30000 }),
    docUpload.click(),
  ]);
  wirePage(popup, 'doc-upload-popup', recorder);
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return { inbox, popup };
}

// Submit the chosen file and return the upload response.
async function submitUpload(popup, filePath) {
  await popup.locator('input[type="file"]').first().setInputFiles(filePath);
  const [response] = await Promise.all([
    popup.waitForResponse(
      (r) => /\/documentManager\/(addEditDocument|documentUpload)/.test(r.url())
        && r.request().method() === 'POST',
      { timeout: 60000 },
    ),
    // The legacy uploader submits a real form (input[type=submit] "Upload File");
    // the modern one posts by fetch from a plain button (#btnUpload). Accept
    // either, so this check keeps working whichever the hub offers.
    popup.locator('#btnUpload, input[type="submit"], button[type="submit"]').first().click(),
  ]);
  return response;
}

function documentRowCount() {
  return Number(sql(`SELECT COUNT(*) FROM document WHERE docfilename LIKE '%${probeName}'`) || '0');
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-upload-'));
  initMysqlDefaults();
  try {
    const probePdf = writeProbePdf(workDir);

    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
    const landing = await login(context, config, recorder);
    await landing.close();

    // ---- 1. a single upload succeeds ---------------------------------------
    const first = await openUploadPopup(context, recorder);
    const firstResponse = await submitUpload(first.popup, probePdf);
    const firstStatus = firstResponse.status();
    const firstError = firstResponse.headers()['oscar_error'];

    assert(
      firstStatus < 400,
      `Uploading a document returned HTTP ${firstStatus}`
        + (firstError ? ` with oscar_error "${firstError}"` : ' with no oscar_error header, so the '
          + 'failure escaped the action and was rewritten by errorpage.jsp — check catalina.out '
          + 'for the stack trace')
        + '.',
    );
    assert(
      !firstError,
      `Uploading a document reported an error header: "${firstError}"`,
    );
    await screenshot(first.popup, config.screenshotDir, 'document-upload-first');

    // The response is not the proof; the record is. A handled failure can still
    // answer 200 on some paths, and the file landing on disk without a row is
    // the exact shape of the post-write failures this check must not miss.
    let rows = 0;
    for (let attempt = 0; attempt < 20 && rows === 0; attempt += 1) {
      rows = documentRowCount();
      if (rows === 0) {
        await first.popup.waitForTimeout(500);
      }
    }
    assert(rows > 0, `Upload reported success but no document row was created for ${probeName}`);
    await first.popup.close();
    await first.inbox.close();

    // ---- 2. the same name inside one second must not report success --------
    // Back-to-back, deliberately: the stored name is prefixed to one-second
    // resolution, so this is the collision path.
    const second = await openUploadPopup(context, recorder);
    const secondResponse = await submitUpload(second.popup, probePdf);
    const secondStatus = secondResponse.status();
    const secondError = secondResponse.headers()['oscar_error'];
    const secondRows = documentRowCount();

    if (secondStatus < 400) {
      // No collision happened -- the second upload crossed a second boundary
      // and got its own name. That is a legitimate outcome, not a pass or a
      // failure of the behaviour under test, so say so rather than asserting
      // on a race that did not occur.
      assert(
        secondRows > rows,
        'The second upload reported success but created no new document row.',
      );
      console.log(
        '[note] the two uploads landed in different seconds, so the name-collision path was not '
        + 'exercised on this run; both uploads succeeded, which is correct.',
      );
    } else {
      assert(
        secondStatus === 409,
        `A duplicate-name upload answered HTTP ${secondStatus}, expected 409. A name collision is `
          + 'user-recoverable and must not be reported as a server error.',
      );
      assert(
        secondError && secondError.length > 0,
        'The 409 carried no oscar_error header, so the uploader has nothing to show the user.',
      );
      assert(
        secondRows === rows,
        'A rejected duplicate upload still created a document row.',
      );
    }
    await screenshot(second.popup, config.screenshotDir, 'document-upload-second');
    await second.popup.close();
    await second.inbox.close();

    // ---- 3. eDocs from the chart: the document must ATTACH to the patient ----
    // This is the path the tester meant by "upload not working from edocs": the
    // chart's documents "+" opens ViewDocumentReport?...&mode=add (the exact URL
    // EctDisplayDocs2Action puts on that button) and the form posts to
    // addEditDocument. Two defects hid here, and both were invisible to a check
    // that only watched the HTTP status:
    //   - Struts 7 collapses the form's duplicate functionId/functionid fields
    //     case-insensitively and the surviving lowercase key bound to nothing,
    //     so the document saved with module_id=0 — ATTACHED TO NO PATIENT — and
    //     the post-save redirect 400ed. Hence the DB assertion below: the row
    //     landing is not enough, it must land on THIS demographic.
    //   - an EMPTY file was rejected by the multipart layer before the action
    //     and fell through to errorpage.jsp as a raw 500.
    const edocsDemo = process.env.PRESCRIPTION_DEMOGRAPHIC_NO || '1';
    const edocsPage = await context.newPage();
    wirePage(edocsPage, 'edocs-add', recorder);
    await gotoApp(edocsPage, config.baseUrl,
      `/documentManager/ViewDocumentReport?function=demographic&doctype=lab&functionid=${edocsDemo}&mode=add&parentAjaxId=docs`);
    await edocsPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const edocsForm = edocsPage.locator('form[action*="addEditDocument"]').first();
    const edocsDesc = `edocs check ${stamp}`;
    const typeSelect = edocsForm.locator('select[name="docType"]');
    const typeValues = await typeSelect.locator('option').evaluateAll((os2) => os2.map((o) => o.value).filter((v) => v));
    assert(typeValues.length > 0, 'eDocs add form offers no document types');
    await typeSelect.selectOption(typeValues[0]);
    await edocsForm.locator('input[name="docDesc"]').fill(edocsDesc);
    await edocsForm.locator('input[type="file"]').setInputFiles(probePdf);

    const [edocsResponse] = await Promise.all([
      edocsPage.waitForResponse(
        (r) => r.url().includes('/documentManager/addEditDocument') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      edocsForm.locator('input[name="Submit"], input[type="submit"]').first().click(),
    ]);
    assert(edocsResponse.status() < 400, `eDocs upload POST returned HTTP ${edocsResponse.status()}`);
    await edocsPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const edocsBody = (await edocsPage.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');
    assert(
      !/CARLOS Error|unexpected error/i.test(edocsBody),
      `eDocs upload landed on an error page after saving: ${edocsBody.slice(0, 160)}`,
    );

    const attached = sql(
      `SELECT cd.module_id FROM ctl_document cd JOIN document d ON d.document_no = cd.document_no `
      + `WHERE d.docdesc = '${edocsDesc}' AND cd.module = 'demographic'`,
    );
    assert(
      attached === edocsDemo,
      `eDocs upload did not attach to the patient: expected module_id ${edocsDemo}, got `
        + `${JSON.stringify(attached)} — an orphaned document is saved but appears in no chart.`,
    );

    // Empty file: user-recoverable, must NOT be a raw 500 error page.
    const emptyPdf = path.join(workDir, `edocs-empty-${stamp}.pdf`);
    fs.writeFileSync(emptyPdf, Buffer.alloc(0));
    const emptyPage = await context.newPage();
    wirePage(emptyPage, 'edocs-empty', recorder);
    await gotoApp(emptyPage, config.baseUrl,
      `/documentManager/ViewDocumentReport?function=demographic&doctype=lab&functionid=${edocsDemo}&mode=add&parentAjaxId=docs`);
    await emptyPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const emptyForm = emptyPage.locator('form[action*="addEditDocument"]').first();
    const emptyTypes = await emptyForm.locator('select[name="docType"] option').evaluateAll((os2) => os2.map((o) => o.value).filter((v) => v));
    if (emptyTypes.length) await emptyForm.locator('select[name="docType"]').selectOption(emptyTypes[0]);
    await emptyForm.locator('input[name="docDesc"]').fill(`edocs empty ${stamp}`);
    await emptyForm.locator('input[type="file"]').setInputFiles(emptyPdf);
    const [emptyResponse] = await Promise.all([
      emptyPage.waitForResponse(
        (r) => r.url().includes('/documentManager/addEditDocument') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      emptyForm.locator('input[name="Submit"], input[type="submit"]').first().click(),
    ]);
    assert(
      emptyResponse.status() !== 500,
      'Uploading an EMPTY file from eDocs returned a raw 500 — the multipart rejection must land '
        + 'back on the documents page (the "input" result), not on errorpage.jsp.',
    );
    await emptyPage.close();
    await edocsPage.close();
    await context.close();

    console.log(
      `PASS document upload: ${probeName} uploaded through the Inbox hub popup, `
      + `document row created, duplicate handling returned HTTP ${secondStatus}`,
    );
  } catch (error) {
    console.error('FAIL document upload Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    cleanupMysqlDefaults();
    fs.rmSync(workDir, { recursive: true, force: true });
    await browser.close();
  }
})();
