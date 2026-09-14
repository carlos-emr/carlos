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
 *   2. A second upload is accepted and filed. (The same-second name-collision
 *      path and its 409 + actionable-message contract are pinned by
 *      AddEditDocument2ActionUnitTest, not here -- driving the popup twice takes
 *      seconds, so the timestamp-prefixed names never actually collide.)
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (to confirm the row landed)
 * Optional: CHROME_PATH, DOCUMENT_UPLOAD_SCREENSHOT_DIR (default /tmp).
 *
 * FIXTURE SAFETY: uploads a PDF this script generates under a unique,
 * timestamped name, only ever asserts on rows matching that name, and removes
 * exactly those rows on the way out -- pass OR fail. The cleanup is
 * unconditional on purpose: eform-rtl-attachment-behavior attaches whichever
 * document is FIRST in the patient's list, so a leftover probe displaces the
 * large report it needs and fails it. The removed ids are printed, so a failing
 * run is still diagnosable. Clear strays from older runs with:
 *   DELETE FROM document WHERE docfilename LIKE '%carlos-upload-probe-%';
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
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

// Remove the document rows this run created, by its unique stamp only.
function cleanupProbeDocuments() {
  try {
    const ids = sql(
      `SELECT document_no FROM document WHERE docfilename LIKE '%${stamp}%' OR docdesc LIKE '%${stamp}%'`,
    ).split(/\s+/).filter(Boolean);
    if (!ids.length) return;
    const list = ids.join(',');
    console.log(`cleanup: removing probe document row(s) ${list} for stamp ${stamp}`);
    sql(`DELETE FROM ctl_document WHERE document_no IN (${list})`);
    sql(`DELETE FROM document WHERE document_no IN (${list})`);
  } catch (e) {
    // Cleanup is housekeeping, not an assertion: never turn a passing run red.
    console.warn(`WARN: could not clean up probe documents for stamp ${stamp}: ${e.message}`);
  }
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
  // Both segments are trusted constants: dir comes from fs.mkdtempSync above and
  // probeName is a fixed prefix plus Date.now() — no user or network input reaches
  // this join, which the taint rule cannot see across the module.
  const file = path.join(dir, probeName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal

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

    // ---- 2. a second upload also succeeds and creates its own row ----------
    // Not a duplicate-collision test: driving the popup twice takes several
    // wall-clock seconds, so the two stored names (yyyyMMddHHmmss prefix) never
    // collide, and the two uploaders answer a collision differently anyway
    // (legacy html5MultiUpload -> 409 + oscar_error; modern documentUpload ->
    // 200 + JSON {error}). The same-second collision and its 409 contract are
    // pinned deterministically by AddEditDocument2ActionUnitTest
    // (shouldReturnConflict_whenHtml5UploadNameAlreadyTaken). Here we only
    // confirm a second real upload is accepted and filed.
    const second = await openUploadPopup(context, recorder);
    const secondResponse = await submitUpload(second.popup, probePdf);
    assert(secondResponse.status() < 400, `Second upload returned HTTP ${secondResponse.status()}`);
    assert(documentRowCount() > rows, 'The second upload created no new document row.');
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
    // No .catch(() => '') here: this feeds a NEGATIVE assertion, so swallowing a failed
    // read into '' would satisfy it vacuously -- the exact hollowness pattern removed
    // from the drug-search banner checks.
    const edocsBody = (await edocsPage.locator('body').innerText()).replace(/\s+/g, ' ');
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

    // Add Link: the SAME case-collapse defect, on a route nothing else drives.
    //
    // The Add Link form posts both functionId and functionid, exactly like the
    // upload form above, so AddEditHtml2Action needed the same lowercase alias.
    // That fix had a unit test proving the setter delegates and carries
    // @StrutsParameter, but nothing anywhere asserted its EFFECT -- deleting the
    // alias would have re-orphaned every link and HTML document, attached to no
    // chart, with the whole suite still green. Assert the persisted row, not the
    // HTTP status: the defect saved successfully and mis-filed.
    const linkDesc = `edocs link ${stamp}`;
    const linkPanelToggle = edocsPage.locator('[data-bs-target="#addLinkDiv"]').first();
    if (await linkPanelToggle.count() > 0) {
      await linkPanelToggle.click();
      const linkForm = edocsPage.locator('form[action*="addLink"]').first();
      await linkForm.locator('input[name="docDesc"]').waitFor({ state: 'visible', timeout: 20000 });
      await linkForm.locator('input[name="docDesc"]').fill(linkDesc);
      // docType and html are BOTH required by the action. Leaving either blank
      // takes the validation-failure branch, which is a different path -- and
      // one that used to answer 500, because it put the empty String under the
      // "completedForm" attribute the JSP casts to AddEditDocument2Form.
      const linkType = linkForm.locator('select[name="docType"], input[name="docType"]').first();
      if (await linkType.count() > 0) {
        const tag = await linkType.evaluate((el) => el.tagName.toLowerCase());
        if (tag === 'select') {
          const linkTypeValues = await linkType.locator('option')
            .evaluateAll((os2) => os2.map((o) => o.value).filter((v) => v));
          assert(linkTypeValues.length > 0, 'Add Link form offers no document types');
          await linkType.selectOption(linkTypeValues[0]);
        } else {
          await linkType.fill('lab');
        }
      }
      await linkForm.locator('input[name="html"]').fill(`https://example.invalid/${stamp}`);
      const linkClass = linkForm.locator('select[name="docClass"]');
      if (await linkClass.count() > 0) {
        const linkClassValues = await linkClass.locator('option')
          .evaluateAll((os2) => os2.map((o) => o.value).filter((v) => v));
        if (linkClassValues.length) await linkClass.selectOption(linkClassValues[0]);
      }
      const [linkResponse] = await Promise.all([
        edocsPage.waitForResponse(
          (r) => r.url().includes('/documentManager/addLink') && r.request().method() === 'POST',
          { timeout: 60000 },
        ),
        linkForm.locator('input[name="Submit"], input[type="submit"]').first().click(),
      ]);
      assert(linkResponse.status() < 400, `Add Link POST returned HTTP ${linkResponse.status()}`);
      await edocsPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

      // AddEditHtml2Action APPENDS " (link)" to the description before saving, so match on
      // the prefix rather than for equality -- an exact match finds nothing and looks
      // identical to "the link was filed against no patient", which is the defect this
      // assertion exists to catch.
      const linkAttached = sql(
        `SELECT cd.module_id FROM ctl_document cd JOIN document d ON d.document_no = cd.document_no `
        + `WHERE d.docdesc LIKE '${linkDesc}%' AND cd.module = 'demographic'`,
      );
      assert(
        linkAttached.split(/\s+/).filter(Boolean).includes(String(edocsDemo)),
        `Add Link attached to module_id ${JSON.stringify(linkAttached)}, expected ${edocsDemo}. `
          + 'A link filed against no patient is the functionId/functionid case-collapse defect on '
          + 'AddEditHtml2Action.',
      );
    }

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

    // Not-a-500 is only half of it. The first version of that "input" result forwarded to the
    // documents page WITHOUT the docerrors attribute that page keys its alert off, so the
    // rejection rendered a clean document list and was indistinguishable from a page refresh --
    // a silent drop, which for a clinician filing a scan is worse than the 500 it replaced.
    // Assert the user is actually told, and that no document row was created for the empty file.
    await emptyPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    // Landing on errorpage.jsp is a failure, not a pass. That page renders the
    // literal text "CARLOS Error: ..." which would satisfy any regex looking for
    // the word "error", so the scenario has to rule it out explicitly -- the
    // whole point is that the rejection lands back on the documents page.
    await assertNotErrorPage(emptyPage, 'the empty-file eDocs upload');
    // Scope the assertion to the alert container the page actually uses for
    // upload errors. Matching anywhere in the body is satisfied by any document
    // in the list whose description happens to contain "error" -- including the
    // "edocs empty <stamp>" fixtures a failing run leaves behind -- at which
    // point this stops seeing the silent drop it exists to catch.
    const emptyAlert = emptyPage.locator('#addDocDiv .alert-danger, #docAlertContainer, .alert-danger');
    const emptyAlertText = (await emptyAlert.first().innerText().catch(() => '')).trim();
    assert(
      (await emptyAlert.count()) > 0 && emptyAlertText.length > 0,
      'The empty-file rejection produced no visible error alert. The upload did not happen and '
        + 'the user was not told: the "input" result must carry the error (docerrors), not just '
        + 'avoid the 500. A clean documents list here is the silent-drop defect.',
    );
    const emptyRows = sql(
      `SELECT COUNT(*) FROM document WHERE docdesc = 'edocs empty ${stamp}'`,
    ).trim();
    assert(
      emptyRows === '0',
      `A rejected empty upload still created ${emptyRows} document row(s).`,
    );

    await emptyPage.close();
    await edocsPage.close();

    assertNoPageErrors(recorder);

    await context.close();

    console.log(
      `PASS document upload: ${probeName} uploaded through the Inbox hub popup and the eDocs `
      + 'chart form, both document rows created, the eDocs row attached to the patient, and an '
      + 'empty file was rejected without a 500',
    );
  } catch (error) {
    console.error('FAIL document upload Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    // ALWAYS clean up, pass or fail. Leaving these rows behind is not neutral:
    // eform-rtl-attachment-behavior attaches whichever document is FIRST in this
    // patient's list, so a leftover ~200-byte probe PDF -- or worse, the Add Link
    // row, which is a <script> redirect rather than a PDF -- displaces the large
    // report it needs and fails it. A failing run here was silently failing a
    // sibling. The rows are listed as they are removed, so a failure is still
    // diagnosable from this output without leaving the fixture poisoned.
    cleanupProbeDocuments();
    cleanupMysqlDefaults();
    fs.rmSync(workDir, { recursive: true, force: true });
    await browser.close();
  }
})();
