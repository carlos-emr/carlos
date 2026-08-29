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
 * Browser regression checks for the tickler note dialog fix (#3252).
 *
 * ticklerGetNote() returns {} (not null) when a tickler has no note yet.
 * Both ticklerMain.jsp (Tickler Manager) and ticklerDemoMain.jsp (schedule-view
 * tickler popup) used to assign every response field into the dialog's DOM
 * fields unconditionally, so a missing property stringified into the literal
 * text "undefined". The stale "undefined" left in the hidden noteId field
 * then got posted back on save, throwing an uncaught NumberFormatException
 * that silently aborted the save. ticklerDemoMain.jsp also never reset the
 * dialog fields before opening it, so a previously-viewed tickler's note
 * could bleed into a freshly opened one on the same page.
 *
 * This script drives ticklerMain.jsp (route /tickler/ViewTicklerMain) for two
 * independently created ticklers on one page load:
 *   1. opening tickler A's note dialog for the first time shows no
 *      "undefined" text and every field is blank,
 *   2. saving a first note on tickler A round-trips correctly (revision "1"),
 *   3. editing and re-saving increments the revision ("2"),
 *   4. opening tickler B's dialog immediately afterward - without a page
 *      reload - does not leak tickler A's stale note/revision/noteId into
 *      tickler B's (still noteless) dialog.
 *
 * ticklerDemoMain.jsp (the schedule-view popup the fix's PR description
 * calls out as previously missing the pre-open reset) is NOT used as the
 * driver page here: as of this writing it throws an unrelated, pre-existing
 * org.hibernate.LazyInitializationException on the lazy Tickler.comments
 * collection (ticklerDemoMain.jsp line ~978, untouched by the note-dialog
 * fix) whenever a demographic has any tickler, producing a generic
 * "CARLOS Error: 0" page instead of the tickler list - the same reason
 * tickler-crud-playwright-checks.js's openDemoTicklerList() targets
 * ViewTicklerMain instead of ViewTicklerDemoMain despite its name. Both
 * pages share the same resetTicklerNoteFields()/applyTicklerNoteFields()
 * functions from js/ticklerNoteDialog.js, so exercising them through
 * ticklerMain.jsp still covers the shared logic this fix introduced; it
 * just cannot exercise ticklerDemoMain.jsp's own reset-call wiring
 * specifically until that unrelated Hibernate session issue is fixed.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:tickler-note-dialog-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   TICKLER_DEMOGRAPHIC_NO=1
 *   TICKLER_PROVIDER_NO=999998
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const mysqlHost = process.env.MYSQL_HOST || 'db';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
const demographicNo = process.env.TICKLER_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.TICKLER_PROVIDER_NO || '999998';
const stamp = `PW_TICKLER_NOTE_${Date.now()}`;
const messageA = `${stamp}_A note round-trip check`;
const messageB = `${stamp}_B stale-data leak check`;
const firstNoteText = `${stamp} first note text`;
const secondNoteText = `${stamp} second note text (edited)`;

// casemgmt_note_link.table_name value identifying a tickler-linked note (see
// CaseManagementNoteLink.TICKLER in the Java model).
const NOTE_LINK_TABLE_TICKLER = 10;

const mysqlDefaults = createMysqlDefaultsFile();
const badResponses = [];
const consoleIssues = [];
let createdTicklerIds = [];

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }

  const host = parsed.hostname.toLowerCase();
  const localHosts = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);
  const privateIpv4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  if (!localHosts.has(host) && !privateIpv4 && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

function appUrl(appPath, query = null) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const relative = new URL(appPath, 'http://localhost');
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      url.searchParams.set(key, value);
    }
  }
  return url.toString();
}

// MySQL option-file values: a bare `#` starts a comment for the rest of the line, and
// backslash is an escape character. Quoting the value and escaping embedded backslashes/
// quotes keeps passwords containing those characters intact instead of silently truncating
// or corrupting the parsed value. See https://dev.mysql.com/doc/refman/8.0/en/option-files.html
function escapeMysqlOptionValue(value) {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-tickler-note-mysql-'));
  const file = path.join(dir, 'client.cnf');
  fs.writeFileSync(file, `[client]\npassword="${escapeMysqlOptionValue(mysqlPassword)}"\n`, { mode: 0o600 });
  return { dir, file };
}

function cleanupMysqlDefaultsFile() {
  fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
}

function sql(query) {
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost,
    '-u', mysqlUser,
    mysqlDatabase,
    '-N',
    '-B',
    '-e',
    query,
  ], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
}

function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function cleanupTicklerRows() {
  const escapedStamp = escapeSql(`${stamp}%`);
  sql(`DELETE FROM tickler_comments WHERE tickler_no IN (SELECT tickler_no FROM tickler WHERE message LIKE '${escapedStamp}')`);
  sql(`DELETE FROM tickler WHERE message LIKE '${escapedStamp}'`);
}

function purgeDanglingTicklerNoteLinks() {
  // Filtered demo snapshots (and any hand-pruned dev database) can carry
  // casemgmt_note_link rows whose TICKLER table_id no longer exists in the
  // tickler table. Ticklers created by this test then REUSE those
  // auto-increment ids and "inherit" the orphaned notes, which reads exactly
  // like the stale-data leak this script exists to detect. Those links are
  // unreachable garbage (their tickler is gone; the app only soft-deletes
  // ticklers, so this state never arises from the UI) - purge them so the
  // fresh-tickler-has-a-blank-dialog premise holds. Links of existing
  // ticklers are untouched.
  sql(`DELETE FROM casemgmt_note_link WHERE table_name = ${NOTE_LINK_TABLE_TICKLER} AND table_id NOT IN (SELECT tickler_no FROM tickler)`);
}

function cleanupNoteRows() {
  if (createdTicklerIds.length === 0) {
    return;
  }
  const ids = createdTicklerIds.map((id) => Number(id)).join(',');
  const noteIdSubquery = `SELECT note_id FROM casemgmt_note_link WHERE table_name = ${NOTE_LINK_TABLE_TICKLER} AND table_id IN (${ids})`;
  // ticklerSaveNote() also links every saved note to the system "TicklerNote"
  // issue via casemgmt_issue_notes, which FKs to casemgmt_note.note_id and
  // must be cleared first.
  sql(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${noteIdSubquery})`);
  sql(`DELETE FROM casemgmt_note WHERE note_id IN (${noteIdSubquery})`);
  sql(`DELETE FROM casemgmt_note_link WHERE table_name = ${NOTE_LINK_TABLE_TICKLER} AND table_id IN (${ids})`);
}

function getTicklerRows() {
  const escapedStamp = escapeSql(`${stamp}%`);
  const out = sql(`SELECT tickler_no, message FROM tickler WHERE message LIKE '${escapedStamp}' ORDER BY tickler_no`);
  if (!out) {
    return [];
  }
  return out.split('\n').map((line) => {
    const [id, message] = line.split('\t');
    return { id, message };
  });
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    consoleIssues.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
  page.on('response', (response) => {
    const status = response.status();
    const responseUrl = response.url();
    if (status >= 400 && !(status === 404 && /\/imageRenderingServlet\?/.test(responseUrl))) {
      badResponses.push({ label, status, url: responseUrl });
    }
  });
  page.on('console', (message) => {
    const text = message.text();
    if (/(ReferenceError|SyntaxError|TypeError|Uncaught)/i.test(text)) {
      consoleIssues.push({ label, type: message.type(), text, location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    consoleIssues.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
}

async function gotoApp(page, appPath, waitUntil = 'domcontentloaded', query = null) {
  const url = appUrl(appPath, query);
  // BASE_URL is restricted by validateBaseUrl(), and appUrl() only accepts root-relative app paths.
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection
  return page.goto(url, { waitUntil, timeout: 30000 });
}

async function login(page) {
  await gotoApp(page, '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
}

async function createTickler(context, message) {
  const page = await context.newPage();
  wirePage(page, 'tickler-add');
  await gotoApp(page, '/tickler/ViewAddTickler', 'domcontentloaded', {
    updateParent: 'true',
    bFirstDisp: 'false',
    demographic_no: demographicNo,
  });
  await page.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 30000 });
  await page.locator('textarea[name="ticklerMessage"]').fill(message);
  await page.locator('input[name="xml_appointment_date"]').fill('2026-02-18');
  await page.locator('select[name="priority"]').selectOption('High');
  await page.locator('select[name="task_assigned_to"]').selectOption(providerNo);
  await page.locator('input.btn-primary[name="Button"]').first().click();
  await page.waitForFunction(() => {
    const frame = document.getElementById('ticklerSubmitFrame');
    return frame && frame.contentDocument && frame.contentDocument.getElementById('tickler-save-ok');
  }, null, { timeout: 30000 });

  await page.close().catch(() => {});

  let row = null;
  const deadline = Date.now() + 15000;
  while (!row && Date.now() < deadline) {
    row = getTicklerRows().find((item) => item.message === message) || null;
    if (!row) {
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
  }
  assert(row, `expected a created tickler row for message ${message}`);
  return row.id;
}

async function openTicklerList(page) {
  await gotoApp(page, '/tickler/ViewTicklerMain', 'domcontentloaded', { demoview: demographicNo, ticklerview: 'A' });
  await waitForTicklerListReady(page);
}

async function waitForTicklerListReady(page) {
  await page.locator('#ticklerResults').waitFor({ state: 'visible', timeout: 30000 });
  await page.waitForFunction(() => (
    window.jQuery
      && window.jQuery.fn
      && window.jQuery.fn.DataTable
      && window.jQuery.fn.DataTable.isDataTable('#ticklerResults')
      && window.jQuery('#ticklerResults').DataTable().settings()[0].bInitialised
  ), null, { timeout: 30000 });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
}

async function findRowInList(page, message) {
  await page.locator('#ticklerResults_filter input[type="search"]').fill(message);
  await page.locator('#ticklerResults tbody tr').filter({ hasText: message }).first().waitFor({
    state: 'visible',
    timeout: 30000,
  });
  return page.locator('#ticklerResults tbody tr').filter({ hasText: message }).first();
}

async function openNoteDialogForRow(page, message) {
  const row = await findRowInList(page, message);
  // openNoteDialog() runs its AJAX fetch with async:false, so the click handler
  // completes synchronously - no navigation/response wait needed beyond the
  // dialog becoming visible.
  await row.locator('a.noteDialogLink').click();
  await page.locator('#note-form').waitFor({ state: 'visible', timeout: 15000 });
}

async function readDialogFieldSnapshot(page) {
  return {
    noteId: await page.locator('#tickler_note_noteId').inputValue(),
    note: await page.locator('#tickler_note').inputValue(),
    revision: ((await page.locator('#tickler_note_revision').textContent()) || '').trim(),
    editor: ((await page.locator('#tickler_note_editor').textContent()) || '').trim(),
    obsDate: ((await page.locator('#tickler_note_obsDate').textContent()) || '').trim(),
  };
}

function assertNoLiteralUndefined(snapshot, label) {
  for (const [field, value] of Object.entries(snapshot)) {
    assert(value !== 'undefined', `${label}: field "${field}" shows the literal text "undefined" - snapshot=${JSON.stringify(snapshot)}`);
  }
}

function assertBlankSnapshot(snapshot, label) {
  assert(snapshot.noteId === '', `${label}: expected a blank noteId, got ${JSON.stringify(snapshot)}`);
  assert(snapshot.note === '', `${label}: expected a blank note, got ${JSON.stringify(snapshot)}`);
  assert(snapshot.revision === '', `${label}: expected a blank revision, got ${JSON.stringify(snapshot)}`);
  assert(snapshot.editor === '', `${label}: expected a blank editor, got ${JSON.stringify(snapshot)}`);
  assert(snapshot.obsDate === '', `${label}: expected a blank obsDate, got ${JSON.stringify(snapshot)}`);
}

async function saveDialogNote(page, text) {
  await page.locator('#tickler_note').fill(text);
  // saveNoteDialog() also runs with async:false and only closes the dialog on
  // a successful response, so waiting for #note-form to hide both signals the
  // synchronous save handler finished and asserts the save did not silently
  // fail (the exact "NumberFormatException aborts the save" bug this fix
  // addresses would leave the dialog open here).
  await page.locator('#note-form button[onclick*="saveNoteDialog"]').click();
  await page.locator('#note-form').waitFor({ state: 'hidden', timeout: 15000 });
}

async function closeDialogIfOpen(page) {
  const dialog = page.locator('#note-form');
  if (await dialog.isVisible().catch(() => false)) {
    await page.locator('#note-form button[onclick*="closeNoteDialog"]').click();
    await dialog.waitFor({ state: 'hidden', timeout: 15000 }).catch(() => {});
  }
}

(async () => {
  cleanupTicklerRows();
  purgeDanglingTicklerNoteLinks();

  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const browser = await chromium.launch(launchOptions);
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  try {
    const loginPage = await context.newPage();
    wirePage(loginPage, 'login');
    await login(loginPage);
    await loginPage.close().catch(() => {});

    const ticklerAId = await createTickler(context, messageA);
    const ticklerBId = await createTickler(context, messageB);
    createdTicklerIds = [ticklerAId, ticklerBId];

    const page = await context.newPage();
    wirePage(page, 'tickler-main');
    await openTicklerList(page);

    // 1. Opening a fresh, noteless tickler's dialog for the first time must
    //    not show the literal text "undefined" anywhere, and every field
    //    must be blank (ticklerGetNote() returns {} for "no note yet").
    await openNoteDialogForRow(page, messageA);
    let snapshot = await readDialogFieldSnapshot(page);
    assertNoLiteralUndefined(snapshot, 'tickler A first open (no note yet)');
    assertBlankSnapshot(snapshot, 'tickler A first open (no note yet)');

    // 2. Saving a first note must round-trip correctly: reopening shows the
    //    saved text, a numeric noteId, and revision "1" - not "undefined"
    //    and not a silently aborted save.
    await saveDialogNote(page, firstNoteText);
    await openNoteDialogForRow(page, messageA);
    snapshot = await readDialogFieldSnapshot(page);
    assertNoLiteralUndefined(snapshot, 'tickler A reopened after first save');
    assert(snapshot.note === firstNoteText, `tickler A note did not round-trip: ${JSON.stringify(snapshot)}`);
    assert(snapshot.revision === '1', `tickler A revision should be "1" after first save, got ${JSON.stringify(snapshot)}`);
    assert(/^\d+$/.test(snapshot.noteId), `tickler A noteId should be numeric after first save, got ${JSON.stringify(snapshot)}`);

    // 3. Editing and re-saving must increment the revision to "2".
    await saveDialogNote(page, secondNoteText);
    await openNoteDialogForRow(page, messageA);
    snapshot = await readDialogFieldSnapshot(page);
    assertNoLiteralUndefined(snapshot, 'tickler A reopened after second save');
    assert(snapshot.note === secondNoteText, `tickler A edited note did not round-trip: ${JSON.stringify(snapshot)}`);
    assert(snapshot.revision === '2', `tickler A revision should be "2" after second save, got ${JSON.stringify(snapshot)}`);

    // Close tickler A's dialog the way a user actually would (Cancel) before
    // moving to a different row - the jQuery UI modal overlay blocks clicks
    // on the rest of the page while a dialog is open, and the bug this test
    // guards against is specifically about DOM state left behind after a
    // close, not about interacting through an open modal.
    await closeDialogIfOpen(page);

    // 4. THE KEY REGRESSION CHECK: opening tickler B's (still noteless)
    //    dialog immediately afterward - same page, no reload - must not
    //    leak tickler A's stale note/revision/noteId. This exercises the
    //    shared resetTicklerNoteFields()/applyTicklerNoteFields() functions
    //    both ticklerMain.jsp and ticklerDemoMain.jsp now depend on.
    await openNoteDialogForRow(page, messageB);
    snapshot = await readDialogFieldSnapshot(page);
    assertNoLiteralUndefined(snapshot, 'tickler B open after tickler A had a note');
    assertBlankSnapshot(snapshot, 'tickler B open after tickler A had a note (must not inherit tickler A data)');

    await closeDialogIfOpen(page);

    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
    assert(consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(consoleIssues, null, 2)}`);

    console.log(`PASS tickler note dialog round-trip and stale-data checks for ticklers ${ticklerAId} and ${ticklerBId}`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    cleanupNoteRows();
    cleanupTicklerRows();
    cleanupMysqlDefaultsFile();
  }
})().catch((error) => {
  cleanupMysqlDefaultsFile();
  console.error(`FAIL tickler note dialog checks: ${error.stack || error.message}`);
  process.exit(1);
});
