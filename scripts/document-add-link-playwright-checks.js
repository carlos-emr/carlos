#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Browser regression check for Document Manager "Add Link" URL handling
 * (GitHub issue #3949), driven through the patient's eDoc report exactly as an
 * operator uses it: open the Add Link panel, type a URL, click Add.
 *
 * The defect: AddEditHtml2Action prepended "http://" to any URL that did not
 * contain "http://" -- so every https:// link was stored as
 * "http://https://..." and never opened -- and concatenated the raw value into
 * an inline window.location='...' script with no scheme allowlist and no quote
 * escaping. The fix (DocumentLink) allows only http/https, prepends https://
 * only when there is no scheme, and stores a meta-refresh page with an
 * HTML-attribute-encoded URL instead of inline script.
 *
 * Scenarios:
 *   1. An https:// link is stored as https:// (no http://https://), with no
 *      <script>, and OPENING it from the report list really navigates to that
 *      https URL. The external host is intercepted in the browser
 *      (page.route), so nothing leaves the test machine.
 *   2. A schemeless link (host/path) is stored with https:// prepended.
 *   3. A javascript: link is refused: the report re-renders the Add Link panel
 *      with the "only http:// and https://" error, keeps what the user typed,
 *      stores no row, and raises no dialog.
 *   4. A link containing a double quote (attribute break-out attempt) is refused
 *      the same way.
 *
 * Environment (deb-install contract, docs/ui-tests/deb-install-validation.md):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (row assertions and teardown)
 *   DOCUMENT_ADD_LINK_DEMOGRAPHIC_NO  optional; defaults to the lowest active
 *                                     demographic. SKIP when there is none.
 *
 * FIXTURE SAFETY: every description carries a per-run carlos-link-probe-<uuid>
 * marker; only rows with that marker are asserted on, and they are hard-deleted
 * in cleanup whether the check passes or fails. Link documents own no file.
 */
const { randomUUID } = require('node:crypto');
const {
  SkipCheck, appUrl, assert, assertStrictPage, createRecorder, createSqlRunner,
  gotoApp, launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');

/** Reserved TLD (RFC 2606): never resolvable, and intercepted in-browser anyway. */
const PROBE_HOST = 'carlos-link-probe.example.invalid';
const marker = `carlos-link-probe-${randomUUID()}`;

let sql;

function docRows(description) {
  return sql.rows(`SELECT document_no, docxml FROM document WHERE docdesc=${sqlString(description)}`);
}

async function openReport(page, config, demographicNo) {
  await gotoApp(page, config.baseUrl,
    `/documentManager/ViewDocumentReport?function=demographic&functionid=${demographicNo}`);
  await page.locator('button[data-bs-target="#addLinkDiv"]').waitFor({ state: 'visible', timeout: 30000 });
}

/** Opens the Add Link panel, fills it, submits, and returns the POST response. */
async function submitLink(page, description, url) {
  const panel = page.locator('#addLinkDiv');
  if (!(await panel.isVisible())) {
    await page.locator('button[data-bs-target="#addLinkDiv"]').click();
  }
  const form = panel.locator('form');
  await form.locator('#docDesc2').waitFor({ state: 'visible', timeout: 15000 });
  const types = await form.locator('#docType1 option').evaluateAll(
    (options) => options.map((option) => option.value).filter(Boolean));
  if (!types.length) throw new SkipCheck('Add Link offers no document type on this install');
  await form.locator('#docType1').selectOption(types[0]);
  await form.locator('#docDesc2').fill(description);
  await form.locator('#html').fill(url);
  const [response] = await Promise.all([
    page.waitForResponse((r) => new URL(r.url()).pathname.endsWith('/documentManager/addLink')
      && r.request().method() === 'POST', { timeout: 30000 }),
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 30000 }),
    form.locator('input[type="submit"]').click(),
  ]);
  return response;
}

async function assertStored(page, config, description, expectedUrl) {
  const rows = docRows(`${description} (link)`);
  assert(rows.length === 1, `expected exactly one stored link row for ${description}, found ${rows.length}`);
  const [documentNo, html] = rows[0];
  assert(/^\d+$/.test(documentNo), 'stored link document id is not numeric');
  assert(!html.includes('http://https://'), 'stored link still carries the http://https:// prefix');
  assert(!/<script/i.test(html), 'stored link page still contains inline script');
  assert(html.includes(`content="0; url=${expectedUrl}"`), `stored link does not meta-refresh to ${expectedUrl}`);
  assert(html.includes(`href="${expectedUrl}"`), `stored link has no fallback anchor to ${expectedUrl}`);
  assert(await page.locator(`a[title="${description} (link)"]`).count() === 1,
    'the stored link is not listed exactly once in the document report');
  return documentNo;
}

async function assertRejected(page, description, url, label) {
  const response = await submitLink(page, description, url);
  assert(response.status() < 400, `${label}: rejected Add Link returned HTTP ${response.status()}`);
  const alert = page.locator('#addLinkDiv .alert-danger');
  await alert.first().waitFor({ state: 'visible', timeout: 15000 });
  assert(/http:\/\/ and https:\/\//.test(await alert.first().innerText()),
    `${label}: the Add Link panel did not explain that only http/https links are allowed`);
  assert(await page.locator('#addLinkDiv #html').inputValue() === url,
    `${label}: the rejected URL was not kept in the field for correction`);
  assert(await page.locator('#addLinkDiv #html.is-invalid').count() === 1,
    `${label}: the URL field is not marked invalid`);
  assert(docRows(`${description} (link)`).length === 0 && docRows(description).length === 0,
    `${label}: a rejected link was stored`);
}

async function main() {
  const config = readConfig();
  sql = createSqlRunner(config.mysql);
  let browser;
  try {
    const demographicNo = process.env.DOCUMENT_ADD_LINK_DEMOGRAPHIC_NO
      || sql.value("SELECT MIN(demographic_no) FROM demographic WHERE patient_status='AC'");
    if (!/^\d+$/.test(demographicNo || '')) throw new SkipCheck('no active demographic to attach the probe link to');

    const recorder = createRecorder();
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    // Keep every request to the probe host inside the browser.
    const landed = [];
    await context.route(`https://${PROBE_HOST}/**`, (route) => {
      landed.push(route.request().url());
      return route.fulfill({ status: 200, contentType: 'text/html', body: '<!DOCTYPE html><title>probe</title><p>landed</p>' });
    });
    await context.route(`http://${PROBE_HOST}/**`, (route) => route.abort());

    await login(context, config, recorder);
    const page = await context.newPage();
    wireStrictPage(page, 'edoc-report', recorder);
    await openReport(page, config, demographicNo);

    // 1. https is kept, stored safely, and actually opens.
    const httpsDesc = `${marker}-https`;
    const httpsUrl = `https://${PROBE_HOST}/report?id=1&amp;src=carlos`;
    const httpsTyped = `https://${PROBE_HOST}/report?id=1&src=carlos`;
    let response = await submitLink(page, httpsDesc, httpsTyped);
    assert(response.status() < 400, `Add Link (https) POST returned HTTP ${response.status()}`);
    const documentNo = await assertStored(page, config, httpsDesc, httpsUrl);

    const viewer = await context.newPage();
    wireStrictPage(viewer, 'link-viewer', recorder);
    await Promise.all([
      viewer.waitForURL((u) => u.hostname === PROBE_HOST, { timeout: 30000 }),
      viewer.goto(appUrl(config.baseUrl, `/documentManager/ManageDocument?method=display&doc_no=${documentNo}`)), // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl validates the local base URL
    ]);
    assert(new URL(viewer.url()).protocol === 'https:', `opening the link landed on ${new URL(viewer.url()).protocol} instead of https:`);
    assert(landed.some((u) => u === httpsTyped), 'opening the link did not request the stored https URL');
    await viewer.close();

    // 2. schemeless gets https://.
    const bareDesc = `${marker}-schemeless`;
    await openReport(page, config, demographicNo);
    response = await submitLink(page, bareDesc, `${PROBE_HOST}/schemeless`);
    assert(response.status() < 400, `Add Link (schemeless) POST returned HTTP ${response.status()}`);
    await assertStored(page, config, bareDesc, `https://${PROBE_HOST}/schemeless`);

    // 3. and 4. unsafe input is refused without storing anything or running script.
    await openReport(page, config, demographicNo);
    await assertRejected(page, `${marker}-javascript`, 'javascript:alert(document.domain)', 'javascript: scheme');
    await openReport(page, config, demographicNo);
    await assertRejected(page, `${marker}-quote`, `https://${PROBE_HOST}/x"onmouseover="alert(1)`, 'double quote');

    assert(recorder.dialogs.length === 0, 'a dialog was raised; the link input reached a script context');
    assertStrictPage(recorder);
    return { demographicNo, stored: 2, rejected: 2 };
  } finally {
    if (browser) await browser.close();
  }
}

function cleanup() {
  if (!sql) return;
  try {
    const ids = sql.rows(`SELECT document_no FROM document WHERE docdesc LIKE ${sqlString(`${marker}%`)}`)
      .map((row) => row[0]);
    assert(ids.every((id) => /^\d+$/.test(id)), 'probe cleanup returned a non-numeric document id');
    if (ids.length) {
      const list = ids.join(',');
      sql.execute(`DELETE FROM document_storage WHERE documentNo IN (${list})`);
      sql.execute(`DELETE FROM ctl_document WHERE document_no IN (${list})`);
      sql.execute(`DELETE FROM document WHERE document_no IN (${list})`);
      console.log(`cleanup: removed probe link document(s) ${list}`);
    }
  } finally {
    sql.dispose();
  }
}

if (require.main === module) runCheck({ name: 'document-add-link', run: main, cleanup });
module.exports = { main };
