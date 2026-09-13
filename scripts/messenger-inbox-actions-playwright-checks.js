#!/usr/bin/env node
/*
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
 * Browser check for the messenger inbox's BULK ACTIONS: mark unread, mark read,
 * search and clear, archive, unarchive.
 *
 * messenger-playwright-checks.js covers composing, sending from the messenger and
 * from the chart, the patient link, and that OPENING a message marks it read. It
 * stops there. The row-selection toolbar — the buttons a provider uses to work
 * through an inbox rather than read one message — was untouched: none of
 * btnRead, btnUnread, btnDelete (archive) or btnUnarchive was driven, and neither
 * was the archived box the archive moves messages into.
 *
 * WHY THAT IS WORTH ITS OWN CHECK. A bulk action is selection-driven, so its
 * failure mode is not "nothing happened" but "it happened to the wrong rows".
 * Every assertion here therefore pins the action to the ONE message the check
 * selected, and reads the per-recipient messagelisttbl row — the row the inbox
 * actually renders. A status change written to the content row instead would look
 * correct in the database and change nothing an operator sees.
 *
 * THE INBOX IS ENTERED THE WAY A PROVIDER ENTERS IT: the Msg link in the
 * schedule's top nav, and Compose from inside the inbox. That is not decoration.
 * CreateMessage.jsp redirects to /index when the session carries no
 * msgSessionBean, and /index renders the LOGIN page — so a check that navigated
 * straight to /messenger/ViewCreateMessage would quietly be asserting against a
 * login form.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:messenger-inbox-actions-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=127.0.0.1 MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   MESSENGER_PROVIDER_NO=999998  provider the test user logs in as
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * Fixture: a clean install has NO messenger contacts (groupMembers_tbl is empty
 * and the shipped "doc" group has no members), so the compose page offers no
 * recipients and nothing can be sent. Like messenger-playwright-checks.js, this
 * check enrols the test provider through Administration > Messenger — the
 * operator's own remedy — and removes that enrolment again only if it created it.
 *
 * Cleanup: the subject and body carry a unique PW_MSGBOX_<millis> marker and every
 * messagetbl / messagelisttbl / msgDemoMap row carrying it is deleted in a finally,
 * including after a failure.
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
  validateBaseUrl,
  validateMysqlHost,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};
const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || '127.0.0.1');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
const providerNo = process.env.MESSENGER_PROVIDER_NO || '999998';
assert(/^\d+$/.test(providerNo), 'MESSENGER_PROVIDER_NO must be numeric');

const stamp = `PW_MSGBOX_${Date.now()}`;
const subject = `${stamp} subject`;
const bodyText = `${stamp} body line one`;

// messagelisttbl.status values, from MessageList.STATUS_*. The inbox renders these
// directly, so they are the contract the bulk actions have to honour.
const STATUS_NEW = 'new';
const STATUS_READ = 'read';
const STATUS_DELETED = 'del';

// DisplayMessages boxType: 0 inbox, 1 sent, 2 archived ("deleted").
const BOX_INBOX = '0';
const BOX_ARCHIVED = '2';

const recorder = createRecorder();
const passed = [];
let enrolledContactByCheck = false;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'messenger-inbox-'));
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
function sqlRows(query) {
  const out = sql(query);
  return out ? out.split('\n').map((line) => line.split('\t')) : [];
}
function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

function contactRows() {
  return sqlRows(`SELECT id, groupID FROM groupMembers_tbl WHERE provider_No='${escapeSql(providerNo)}' AND facilityId=0 ORDER BY id`);
}

function stampedMessageId() {
  const rows = sqlRows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE '${escapeSql(`${stamp}%`)}' ORDER BY messageid`);
  return rows.length === 1 ? rows[0][0] : null;
}

function recipientStatus(messageId) {
  const rows = sqlRows(
    `SELECT status FROM messagelisttbl WHERE message=${Number(messageId)}`
    + ` AND provider_no='${escapeSql(providerNo)}' ORDER BY id DESC LIMIT 1`
  );
  return rows.length ? rows[0][0] : null;
}

function cleanupRows() {
  const ids = sqlRows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE '${escapeSql(`${stamp}%`)}'`)
    .map(([id]) => id).filter((id) => /^\d+$/.test(id));
  for (const id of ids) {
    sql(`DELETE FROM messagelisttbl WHERE message=${Number(id)}`);
    sql(`DELETE FROM msgDemoMap WHERE messageID=${Number(id)}`);
    sql(`DELETE FROM messagetbl WHERE messageid=${Number(id)}`);
  }
  if (enrolledContactByCheck) {
    sql(`DELETE FROM groupMembers_tbl WHERE provider_No='${escapeSql(providerNo)}' AND facilityId=0 AND groupID=0`);
  }
}

async function waitFor(probe, description, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await probe();
    if (last) {
      return last;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`timed out waiting for ${description}; last value=${JSON.stringify(last)}`);
}

/** Enrols the test provider as a local messenger contact, through the admin page. */
async function ensureMessengerContact(context) {
  const page = await context.newPage();
  wirePage(page, 'messenger-admin', recorder);
  await gotoApp(page, config.baseUrl, '/messenger?method=fetch');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'messenger group admin');
  const box = page.locator(`#local-contacts input[type="checkbox"][value^="${providerNo}-"]`).first();
  assert(await box.count(), `messenger admin did not list provider ${providerNo} as a contact candidate`);
  if (!(await box.isChecked())) {
    const [addResponse] = await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST'
        && /\/messenger\?method=add&member=/.test(response.url()), { timeout: 30000 }),
      box.check(),
    ]);
    assert(addResponse.status() < 400, `messenger contact add returned HTTP ${addResponse.status()}`);
    enrolledContactByCheck = true;
  }
  assert(contactRows().some((row) => row[1] === '0'),
    'provider is not enrolled as a local messenger contact after the admin step');
  await page.close();
}

/**
 * Opens the inbox from the Msg link in the schedule's top nav.
 *
 * scheduleNav=1 on the schedule makes that link navigate in place instead of
 * opening a popup, which is the same destination either way and keeps the check
 * on one page.
 */
async function openInboxFromSchedule(context) {
  const page = await context.newPage();
  wirePage(page, 'schedule', recorder);
  await gotoApp(page, config.baseUrl,
    '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1&scheduleNav=1');
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'schedule day sheet');

  const msgLink = page.locator('a[onclick*="/messenger/DisplayMessages"]').first();
  assert(await msgLink.count() > 0, 'the schedule top nav rendered no Msg link');
  await Promise.all([
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    msgLink.click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'messenger inbox');
  assert(/\/messenger\/DisplayMessages/.test(page.url()),
    `the schedule Msg link landed on ${page.url()} instead of the inbox`);
  return page;
}

async function openBox(page, boxType) {
  await gotoApp(page, config.baseUrl, `/messenger/DisplayMessages?boxType=${encodeURIComponent(boxType)}`);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, `messenger box ${boxType}`);
}

/** The selection checkbox for one message; the inbox nests tables, so this is the anchor. */
function messageCheckbox(page, messageId) {
  return page.locator(`input[name="messageNo"][value="${messageId}"]`).first();
}

/** Seeds one message to the logged-in provider, composing from inside the inbox. */
async function sendSelfMessage(inbox) {
  const composeLink = inbox.locator('a[href*="/messenger/ViewCreateMessage"]').first();
  await composeLink.waitFor({ state: 'visible', timeout: 30000 });
  await Promise.all([
    inbox.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    composeLink.click(),
  ]);
  await inbox.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(inbox, 'messenger compose');
  // The compose FORM is the proof the session bean was established; a login form
  // here means the /index redirect above fired.
  await inbox.locator('#subject').waitFor({ state: 'visible', timeout: 30000 });

  const recipient = inbox.locator(`input[type="checkbox"][name="provider"][value^="${providerNo}"]`).first();
  assert(await recipient.count() > 0, `compose offered no recipient checkbox for provider ${providerNo}`);
  await recipient.check({ force: true });
  await inbox.locator('#subject').fill(subject);

  // Typed into the WYSIWYG surface, not the hidden textarea: the Send button's
  // writeToMessage() copies the editor's markdown into that textarea, so filling
  // the textarea directly would be overwritten at submit time.
  const editor = inbox.locator('.toastui-editor-ww-container .ProseMirror').first();
  await editor.waitFor({ state: 'visible', timeout: 15000 });
  await editor.click();
  await inbox.keyboard.type(bodyText);

  const [response] = await Promise.all([
    inbox.waitForResponse((r) => r.request().method() === 'POST'
      && /\/messenger\/CreateMessage$/.test(new URL(r.url()).pathname), { timeout: 45000 }),
    inbox.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    inbox.locator('button[type="submit"]').first().click(),
  ]);
  assert(response.status() < 400, `messenger/CreateMessage returned HTTP ${response.status()}`);
  await assertNotErrorPage(inbox, 'messenger sent confirmation');

  const messageId = await waitFor(() => stampedMessageId(), 'the sent message to reach messagetbl');
  await waitFor(() => (recipientStatus(messageId) === STATUS_NEW ? true : null),
    `message ${messageId} to be delivered to provider ${providerNo} as ${STATUS_NEW}`);
  return messageId;
}

/**
 * Runs one bulk action over a single selected message.
 *
 * The POST body is inspected as well as the resulting status: that is what proves
 * the action applied to the row the check selected rather than to the whole box,
 * which is the way a bulk action fails.
 */
async function runBulkAction(page, boxType, messageId, buttonName, expectedStatus) {
  await openBox(page, boxType);
  const checkbox = messageCheckbox(page, messageId);
  await checkbox.waitFor({ state: 'visible', timeout: 30000 });
  await checkbox.check();

  const button = page.locator(`button[name="${buttonName}"]`).first();
  assert(await button.count() > 0, `box ${boxType} rendered no ${buttonName} control`);

  const [response] = await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'POST'
      && /\/messenger\//.test(new URL(r.url()).pathname), { timeout: 45000 }),
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    button.click(),
  ]);
  assert(response.status() < 400, `${buttonName} returned HTTP ${response.status()}`);
  const posted = new URLSearchParams(response.request().postData() || '');
  assert(posted.getAll('messageNo').includes(String(messageId)),
    `${buttonName} POST did not carry the selected message ${messageId}`);
  await assertNotErrorPage(page, `box ${boxType} after ${buttonName}`);

  await waitFor(() => (recipientStatus(messageId) === expectedStatus ? true : null),
    `${buttonName} to move message ${messageId} to status ${expectedStatus}`);
}

/** Asserts the archived message left the inbox and is listed in the archived box. */
async function assertArchivedPlacement(page, messageId) {
  await openBox(page, BOX_INBOX);
  assert(await messageCheckbox(page, messageId).count() === 0,
    `archived message ${messageId} is still listed in the inbox`);
  await openBox(page, BOX_ARCHIVED);
  await messageCheckbox(page, messageId).waitFor({ state: 'visible', timeout: 30000 });
}

/**
 * Drives the inbox search form and then clears it.
 *
 * Clearing matters as much as searching: the filter lives in a session bean, so a
 * clear that no-ops leaves the next page load showing a stale, narrowed inbox.
 */
async function searchAndClear(page, messageId) {
  await openBox(page, BOX_INBOX);
  const searchInput = page.locator('input[name="searchString"]').first();
  assert(await searchInput.count() > 0, 'the inbox rendered no search field');
  await searchInput.fill(stamp);
  await Promise.all([
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    page.locator('button[name="btnSearch"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'inbox search results');
  await messageCheckbox(page, messageId).waitFor({ state: 'visible', timeout: 30000 });

  await Promise.all([
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    page.locator('button[name="btnClearSearch"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'inbox after clearing the search');
}

(async () => {
  initMysqlDefaults();
  cleanupRows();

  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  try {
    await login(context, config, recorder);
    await ensureMessengerContact(context);
    pass(`provider ${providerNo} is enrolled as a local messenger contact`
      + `${enrolledContactByCheck ? ' (enrolled by this check)' : ' (already enrolled)'}`);

    const inbox = await openInboxFromSchedule(context);
    pass('the schedule Msg link opens the messenger inbox');

    const messageId = await sendSelfMessage(inbox);
    pass(`message ${messageId} composed from the inbox and delivered as unread`);

    await runBulkAction(inbox, BOX_INBOX, messageId, 'btnRead', STATUS_READ);
    pass('mark-read applies to the selected message only');

    await runBulkAction(inbox, BOX_INBOX, messageId, 'btnUnread', STATUS_NEW);
    pass('mark-unread returns the selected message to unread');

    await searchAndClear(inbox, messageId);
    pass('inbox search finds the message by subject and the filter clears again');

    await runBulkAction(inbox, BOX_INBOX, messageId, 'btnDelete', STATUS_DELETED);
    pass('archive moves the selected message to the archived status');

    await assertArchivedPlacement(inbox, messageId);
    pass('the archived message leaves the inbox and appears in the archived box');

    await runBulkAction(inbox, BOX_ARCHIVED, messageId, 'btnUnarchive', STATUS_READ);
    pass('unarchive restores the message to a readable status');

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0,
      `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0,
      `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`\nPASS messenger inbox actions: ${passed.length} checks, 0 failures`);
  } catch (error) {
    console.error(`FAIL messenger inbox actions: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    try {
      cleanupRows();
    } finally {
      cleanupMysqlDefaults();
    }
  }
})();
