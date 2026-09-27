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
 *   Behind the packaged nginx + ModSecurity front door the CRS usually refuses
 *   3 and 4 with a 403 before CARLOS sees them. That still counts as refused
 *   (nothing stored, nginx-served 403), and the run logs which layer refused;
 *   point BASE_URL at bare Tomcat to exercise the application-layer message.
 *   EXPECT_FRONT_DOOR=true requires at least one front-door refusal.
 *
 * Environment (deb-install contract, docs/ui-tests/deb-install-validation.md):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (row assertions and teardown)
 *   The check creates and removes its own synthetic patient.
 *
 * FIXTURE SAFETY: every description carries a per-run carlos-link-probe-<uuid>
 * marker; only rows with that marker are asserted on, and they are hard-deleted
 * in cleanup whether the check passes or fails. Link documents own no file.
 */
const { randomUUID } = require('node:crypto');
const { cleanupOwnedWorkflow } = require('./lib/workflow-session');
const {
  SkipCheck, appUrl, assert, assertStrictPage, createRecorder, createSqlRunner,
  gotoApp, launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');

/** Reserved TLD (RFC 2606): never resolvable, and intercepted in-browser anyway. */
const PROBE_HOST = 'carlos-link-probe.example.invalid';
const marker = `carlos-link-probe-${randomUUID()}`;

let sql;
let browser;
let patient;
let appointment;
const patientMarker = `FAKE-LINK-${randomUUID().slice(0, 8)}`;

function docRows(description) {
  return sql.rows(`SELECT document_no, docxml FROM document WHERE docdesc=${sqlString(description)}`);
}

async function openReport(page, config, demographicNo) {
  await gotoApp(page, config.baseUrl,
    `/documentManager/ViewDocumentReport?function=demographic&functionid=${demographicNo}&appointmentNo=${appointment}`);
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
  assert(sql.value(`SELECT appointment_no FROM document WHERE document_no=${documentNo}`) === appointment,
    'The stored link lost its appointment association');
  assert(!html.includes('http://https://'), 'stored link still carries the http://https:// prefix');
  assert(!/<script/i.test(html), 'stored link page still contains inline script');
  assert(html.includes(`content="0; url=${expectedUrl}"`), `stored link does not meta-refresh to ${expectedUrl}`);
  assert(html.includes(`href="${expectedUrl}"`), `stored link has no fallback anchor to ${expectedUrl}`);
  assert(await page.locator(`a[title="${description} (link)"]`).count() === 1,
    'the stored link is not listed exactly once in the document report');
  return documentNo;
}

/**
 * Submits an unsafe URL on its own page and asserts it was refused with nothing stored.
 *
 * Behind the packaged nginx + ModSecurity front door the CRS may refuse the POST with a
 * bare 403 before CARLOS sees it; that is a valid (earlier) refusal, attributed to the
 * WAF only when nginx served it. Against bare Tomcat the application must refuse it
 * itself: the Add Link panel shows the http/https error, keeps the typed value for
 * correction and marks the field invalid. Returns which layer refused.
 */
async function assertRejected(context, config, demographicNo, recorder, description, url, label) {
  const page = await context.newPage();
  const pageLabel = `reject-${label}`;
  wireStrictPage(page, pageLabel, recorder);
  try {
    await openReport(page, config, demographicNo);
    const response = await submitLink(page, description, url);
    assert(docRows(`${description} (link)`).length === 0 && docRows(description).length === 0,
      `${label}: a rejected link was stored`);
    if (response.status() === 403 && /nginx/i.test(response.headers().server || '')) {
      return { label, layer: 'front-door', pageLabel };
    }
    assert(response.status() < 400, `${label}: rejected Add Link returned HTTP ${response.status()}`);
    const alert = page.locator('#addLinkDiv .alert-danger');
    await alert.first().waitFor({ state: 'visible', timeout: 15000 });
    assert(/http:\/\/ and https:\/\//.test(await alert.first().innerText()),
      `${label}: the Add Link panel did not explain that only http/https links are allowed`);
    assert(await page.locator('#addLinkDiv #html').inputValue() === url,
      `${label}: the rejected URL was not kept in the field for correction`);
    assert(await page.locator('#addLinkDiv input[name="appointmentNo"]').inputValue() === appointment,
      `${label}: the validation retry lost the appointment association`);
    assert(await page.locator('#addLinkDiv #html.is-invalid').count() === 1,
      `${label}: the URL field is not marked invalid`);
    return { label, layer: 'application', pageLabel };
  } finally {
    await page.close();
  }
}

async function main() {
  const config = readConfig();
  sql = createSqlRunner(config.mysql);
  const provider = sql.value(`SELECT provider_no FROM security WHERE user_name=${sqlString(config.testUser)}`);
  if (!provider) throw new SkipCheck('The configured login has no provider');
  patient = sql.value(`INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
     provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${sqlString(patientMarker)},'Link','1980','01','01','F','AC',
      ${sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(patient), 'Synthetic patient was not created');
  const demographicNo = patient;
  appointment = sql.value(`INSERT INTO appointment
    (provider_no,appointment_date,start_time,end_time,name,demographic_no,program_id,notes,reason,
     location,resources,type,style,billing,status,createdatetime,updatedatetime,creator,remarks,urgency)
    VALUES (${sqlString(provider)},CURDATE(),'11:30:00','11:45:00',${sqlString(patientMarker)},${patient},0,
      ${sqlString(marker)},${sqlString(marker)},'','','','','','t',NOW(),NOW(),${sqlString(provider)},'','');
    SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(appointment), 'Synthetic appointment was not created');

  const recorder = createRecorder();
  browser = await launchBrowser(config);
  const context = await newContext(browser, config);
  // Keep every request to the probe host inside the browser.
  const landed = [];
  const referrers = [];
  await context.route(`https://${PROBE_HOST}/**`, (route) => {
    landed.push(route.request().url());
    if (route.request().isNavigationRequest()) referrers.push(route.request().headers().referer || '');
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

  // Preserve exact Unicode path spelling and escapes when the stored link opens.
  const unicodeTyped = `https://${PROBE_HOST}/cafe\u0301?q=e\u0301&ready=1`;
  const unicodeUrl = `https://${PROBE_HOST}/cafe%CC%81?q=e%CC%81&ready=1`;
  await openReport(page, config, demographicNo);
  response = await submitLink(page, `${marker}-unicode`, unicodeTyped);
  assert(response.status() < 400, 'Unicode Add Link failed');
  const unicodeDocument = await assertStored(page, config, `${marker}-unicode`, unicodeUrl.replace('&', '&amp;'));
  const unicodeViewer = await context.newPage();
  wireStrictPage(unicodeViewer, 'unicode-link-viewer', recorder);
  await Promise.all([
    unicodeViewer.waitForURL(unicodeUrl, { timeout: 30000 }),
    gotoApp(unicodeViewer, config.baseUrl, `/documentManager/ManageDocument?method=display&doc_no=${unicodeDocument}`),
  ]);
  assert(landed.includes(unicodeUrl), 'Unicode link changed the resource path or query');
  assert(referrers.every(value => value === ''), 'An external link received a referrer');
  await unicodeViewer.close();

  // Unsafe input is refused without storing anything or running script.
  const refusals = [
    await assertRejected(context, config, demographicNo, recorder,
      `${marker}-javascript`, 'javascript:alert(document.domain)', 'javascript-scheme'),
    await assertRejected(context, config, demographicNo, recorder,
      `${marker}-quote`, `https://${PROBE_HOST}/x"onmouseover="alert(1)`, 'double-quote'),
  ];
  for (const [label, url] of [
    ['javascript-digit', 'javascript:1'], ['data-digit', 'data:1'], ['ftp-digit', 'ftp:1'],
    ['opaque-digit', 'custom:123'], ['missing-host', 'https://:443/path'],
    ['empty-user-host', 'https://user@:80/path'], ['bad-port', `https://${PROBE_HOST}:bad/path`],
    ['large-port', `https://${PROBE_HOST}:65536/path`],
  ]) {
    refusals.push(await assertRejected(context, config, demographicNo, recorder,
      `${marker}-${label}`, url, label));
  }
  for (const refusal of refusals) console.log(`${refusal.label}: refused by the ${refusal.layer}`);
  if (config.expectFrontDoor) {
    assert(refusals.some((r) => r.layer === 'front-door'), 'EXPECT_FRONT_DOOR is set but no refusal came from nginx');
  }

  assert(recorder.dialogs.length === 0, 'a dialog was raised; the link input reached a script context');
  // Read hostile stored HTML as editor text, then exercise an ordinary edit/save/reload.
  const hostileEditorText = '<p>Owned editor fixture</p></textarea><script>window.__carlos3992=1</script><textarea>';
  const originalFacility = 'FAKE referral hospital';
  sql.execute(`UPDATE document SET docxml=${sqlString(hostileEditorText)},docfilename='html',
    sourcefacility=${sqlString(originalFacility)} WHERE document_no=${documentNo}`);
  const editor = await context.newPage();
  wireStrictPage(editor, 'html-document-editor', recorder);
  const editPath = `/documentManager/ViewAddEditHtml?editDocumentNo=${documentNo}`
    + `&function=demographic&functionid=${demographicNo}`;
  await gotoApp(editor, config.baseUrl, editPath);
  assert(await editor.locator('textarea[name="html"]').inputValue() === hostileEditorText,
    'Stored HTML broke out of the editor or changed its text');
  assert(await editor.evaluate(() => window.__carlos3992 === undefined), 'Stored HTML executed inside the editor');
  assert(await editor.locator('input[name="sourceFacility"]').inputValue() === originalFacility,
    'The HTML editor did not load the existing source facility');
  await editor.locator('input[name="sourceFacility"]').fill('FAKE updated referral hospital');
  const editedText = 'Clinical reference & follow-up\nSecond line';
  await editor.locator('textarea[name="html"]').fill(editedText);
  const [editResponse] = await Promise.all([
    editor.waitForResponse(r => new URL(r.url()).pathname.endsWith('/documentManager/addEditHtml')
      && r.request().method() === 'POST'),
    editor.waitForNavigation({ waitUntil: 'domcontentloaded' }),
    editor.locator('input[type="submit"]').click(),
  ]);
  assert(editResponse.status() < 400, `HTML metadata edit returned HTTP ${editResponse.status()}`);
  assert(sql.value(`SELECT sourcefacility FROM document WHERE document_no=${documentNo}`)
    === 'FAKE updated referral hospital', 'The HTML edit discarded the submitted facility');
  assert(sql.value(`SELECT appointment_no FROM document WHERE document_no=${documentNo}`) === appointment,
    'The HTML edit changed the existing appointment association');
  await gotoApp(editor, config.baseUrl, editPath);
  assert(await editor.locator('textarea[name="html"]').inputValue() === editedText.replaceAll('\n', '\r\n')
    || await editor.locator('textarea[name="html"]').inputValue() === editedText,
  'The HTML editor did not reload the exact saved text');
  assert(await editor.locator('input[name="sourceFacility"]').inputValue() === 'FAKE updated referral hospital',
    'The HTML editor did not reload the saved facility');
  await editor.close();
  console.log('PASS source facility, appointment association and inert HTML editor text survive save/reload');

  assertExpectedRefusals(recorder, refusals);
  return { demographicNo, stored: 3, refusals: refusals.map((r) => `${r.label}:${r.layer}`).join(',') };
}

/** Exempts only the deliberate nginx 403 POST and its matching browser console entry. */
function assertExpectedRefusals(recorder, refusals) {
  const wafPages = new Set(refusals.filter(r => r.layer === 'front-door').map(r => r.pageLabel));
  const isSubmit = url => {
    try { return new URL(url).pathname.endsWith('/documentManager/addLink'); }
    catch { return false; }
  };
  assertStrictPage({ ...recorder,
    badResponses: recorder.badResponses.filter(e => !(wafPages.has(e.label)
      && e.status === 403 && e.method === 'POST' && isSubmit(e.url))),
    consoleIssues: recorder.consoleIssues.filter(e => !(wafPages.has(e.label)
      && isSubmit(e.location && e.location.url)
      && /^Failed to load resource: the server responded with a status of 403\b/.test(e.text))),
  });
}

async function cleanup() {
  if (!sql) return;
  await cleanupOwnedWorkflow({ browser, sql, patient, marker: patientMarker, cleanups: [() => {
    const ids = sql.rows(`SELECT document_no FROM document WHERE docdesc LIKE ${sqlString(`${marker}%`)}`)
      .map(row => row[0]);
    assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Probe cleanup returned a non-numeric document id');
    if (ids.length) {
      const list = ids.join(',');
      sql.execute(`DELETE FROM document_storage WHERE documentNo IN (${list});
        DELETE FROM ctl_document WHERE document_no IN (${list});
        DELETE FROM document WHERE document_no IN (${list}) AND docdesc LIKE ${sqlString(`${marker}%`)}`);
      assert(sql.value(`SELECT (SELECT COUNT(*) FROM document_storage WHERE documentNo IN (${list}))
        +(SELECT COUNT(*) FROM ctl_document WHERE document_no IN (${list}))
        +(SELECT COUNT(*) FROM document WHERE document_no IN (${list}))`) === '0',
      'Owned link document rows remain');
      console.log(`cleanup: removed ${ids.length} owned link documents`);
    }
    if (appointment) {
      assert(/^[1-9]\d*$/.test(appointment), 'Invalid owned appointment identity');
      sql.execute(`DELETE FROM appointment WHERE appointment_no=${appointment}
        AND demographic_no=${patient} AND notes=${sqlString(marker)}`);
      assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${appointment}`) === '0',
        'Owned appointment was not removed');
    }
  }] });
}

if (require.main === module) runCheck({ name: 'document-add-link', run: main, cleanup });
module.exports = { main, assertExpectedRefusals };
