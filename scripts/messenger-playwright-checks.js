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
 * Browser check for CARLOS Messenger: sending from the messenger itself,
 * sending from the patient's chart, and the message-to-patient link.
 *
 * Alpha-11 testers reported "messaging works from messenger and from the
 * patient's chart and it links appropriately"; nothing in the suite touched
 * /messenger before this check. It drives:
 *
 *   1. Messenger Group Admin (/messenger?method=fetch): enrols the test user
 *      as a messenger contact by ticking the contact checkbox (the demo seed
 *      ships no contacts, so a fresh install shows an empty recipient list
 *      until an administrator does this), verified in groupMembers_tbl;
 *   2. Messenger inbox -> "Compose Message": picks the recipient checkbox,
 *      types subject + body (the body is a Toast UI editor whose markdown is
 *      copied into the posted textarea on Send), clicks "Send Message", and
 *      asserts messagetbl + messagelisttbl rows (status 'new') and NO patient
 *      link for a plain message;
 *   3. Patient chart -> Messenger "+" (SendDemoMessage popup): asserts the
 *      compose form arrives with the patient pre-attached, sends, and asserts
 *      the msgDemoMap row links the message to that patient;
 *   4. Inbox -> opens the chart-sent message: asserts the linked patient's
 *      name renders with the demographic number and that the M / E / Rx
 *      shortcuts carry that demographic, and that the inbox list shows the
 *      patient column for it.
 *
 * Everything it writes (messages, list rows, demo map rows, and any contact
 * enrolment it had to add) is deleted in a finally.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: MESSENGER_DEMOGRAPHIC_NO (1), MESSENGER_PROVIDER_NO (999998, the
 *   logged-in provider, which is also the recipient so the inbox can be read).
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
const demographicNo = process.env.MESSENGER_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.MESSENGER_PROVIDER_NO || '999998';
assert(/^\d+$/.test(demographicNo), 'MESSENGER_DEMOGRAPHIC_NO must be numeric');
assert(/^\d+$/.test(providerNo), 'MESSENGER_PROVIDER_NO must be numeric');

const stamp = `PW_MSG_${Date.now()}`;
const plainSubject = `${stamp} from messenger`;
const chartSubject = `${stamp} from chart`;
const bodyText = `${stamp} body text`;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'messenger-'));
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

let enrolledContactByCheck = false;

function contactRows() {
  return sqlRows(`SELECT id, groupID FROM groupMembers_tbl WHERE provider_No='${escapeSql(providerNo)}' AND facilityId=0 ORDER BY id`);
}

// Delivery rows are read relative to a high-water mark taken before each
// send: the demo seed ships orphan messagelisttbl rows (no messagetbl parent)
// whose message ids a fresh install's first messages reuse, and a plain join
// would count those orphans as extra deliveries.
function highWaterMarks() {
  return {
    list: Number(sql('SELECT IFNULL(MAX(id), 0) FROM messagelisttbl')),
    demoMap: Number(sql('SELECT IFNULL(MAX(id), 0) FROM msgDemoMap')),
  };
}
function messageRows(subject, marks) {
  return sqlRows('SELECT m.messageid, m.thesubject, m.themessage, m.sentbyNo, l.provider_no, l.status, IFNULL(d.demographic_no, \'\')'
    + ' FROM messagetbl m JOIN messagelisttbl l ON l.message = m.messageid'
    + ` LEFT JOIN msgDemoMap d ON d.messageID = m.messageid AND d.id > ${Number(marks.demoMap)}`
    + ` WHERE m.thesubject='${escapeSql(subject)}' AND l.id > ${Number(marks.list)} ORDER BY m.messageid`)
    .map(([messageId, thesubject, themessage, sentByNo, recipient, status, linkedDemo]) => ({ messageId, thesubject, themessage, sentByNo, recipient, status, linkedDemo: linkedDemo || '' }));
}

const listRowsCreated = [];
const demoMapRowsCreated = [];
function cleanupRows() {
  const ids = sqlRows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE '${escapeSql(`${stamp}%`)}'`).map((row) => row[0]);
  for (const id of ids) {
    if (demoMapRowsCreated.length) {
      sql(`DELETE FROM msgDemoMap WHERE messageID=${Number(id)} AND id IN (${demoMapRowsCreated.map(Number).join(',')})`);
    }
    if (listRowsCreated.length) {
      sql(`DELETE FROM messagelisttbl WHERE message=${Number(id)} AND id IN (${listRowsCreated.map(Number).join(',')})`);
    }
    sql(`DELETE FROM messagetbl WHERE messageid=${Number(id)}`);
  }
  if (enrolledContactByCheck) {
    sql(`DELETE FROM groupMembers_tbl WHERE provider_No='${escapeSql(providerNo)}' AND facilityId=0 AND groupID=0`);
  }
}

const patientName = () => sql(`SELECT CONCAT(last_name, ', ', first_name) FROM demographic WHERE demographic_no=${Number(demographicNo)}`);

async function ensureMessengerContact(context, recorder) {
  const page = await context.newPage();
  wirePage(page, 'messenger-admin', recorder);
  await gotoApp(page, config.baseUrl, '/messenger?method=fetch');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'messenger group admin');
  const box = page.locator(`#local-contacts input[type="checkbox"][value^="${providerNo}-"]`).first();
  assert(await box.count(), `messenger admin did not list provider ${providerNo} as a contact candidate`);
  if (await box.isChecked()) {
    assert(contactRows().some((row) => row[1] === '0'), 'admin page shows the contact enrolled but groupMembers_tbl has no registry row');
  } else {
    // Ticking the box POSTs method=add&member=<composite>&group=0.
    const [addResponse] = await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST'
        && /\/messenger\?method=add&member=/.test(response.url()), { timeout: 30000 }),
      box.check(),
    ]);
    assert(addResponse.status() < 400, `messenger contact add returned HTTP ${addResponse.status()}`);
    enrolledContactByCheck = true;
    assert(contactRows().some((row) => row[1] === '0'), 'ticking the contact checkbox did not create the groupMembers_tbl registry row');
  }
  await page.close();
}

async function fillEditorBody(page, text) {
  // Toast UI WYSIWYG editor; the posted textarea[name=message] is refreshed
  // from it by writeToMessage() when Send is clicked.
  const editor = page.locator('.toastui-editor-ww-container .ProseMirror').first();
  await editor.waitFor({ state: 'visible', timeout: 15000 });
  await editor.click();
  await page.keyboard.type(text);
}

async function pickRecipient(page) {
  const box = page.locator(`input[name="provider"][id^="0-"][value^="${providerNo}-"]`).first();
  assert(await box.count(), `compose form did not list provider ${providerNo} under Local Members`);
  await box.check();
}

async function sendMessage(page, subject) {
  const marks = highWaterMarks();
  await page.locator('#subject').fill(subject);
  await fillEditorBody(page, bodyText);
  const [sendResponse] = await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/messenger/CreateMessage'), { timeout: 30000 }),
    page.locator('button[type="submit"]', { hasText: /Send Message/i }).click(),
  ]);
  assert(sendResponse.status() < 400, `send returned HTTP ${sendResponse.status()}`);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  const text = await page.locator('body').innerText().catch(() => '');
  assert(!/No valid recipients|Failed to send message/i.test(text), `send page reported an error: ${text.slice(0, 300)}`);
  const rows = messageRows(subject, marks);
  for (const row of sqlRows(`SELECT id FROM messagelisttbl WHERE id > ${marks.list}`)) {
    listRowsCreated.push(row[0]);
  }
  for (const row of sqlRows(`SELECT id FROM msgDemoMap WHERE id > ${marks.demoMap}`)) {
    demoMapRowsCreated.push(row[0]);
  }
  return rows;
}

let browser = null;
let cleanupDone = false;
// Runs once from the finally block or the signal handler: every step is attempted
// and a step that fails marks the run as failed, because a fixture left behind is
// a failure of this check even when every assertion passed.
function runCleanup() {
  if (cleanupDone || !mysqlDefaults) {
    return;
  }
  cleanupDone = true;
  for (const step of [cleanupRows]) {
    try {
      step();
    } catch (cleanupError) {
      console.error(`FAIL cleanup step ${step.name} failed: ${cleanupError.message}`);
      process.exitCode = 1;
    }
  }
}
// Node does not run finally blocks on SIGINT/SIGTERM (the suite loop's `timeout`
// sends TERM), so restore the fixtures here too before exiting.
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    console.error(`${signal} received; restoring fixtures before exiting.`);
    runCleanup();
    cleanupMysqlDefaults();
    process.exit(130);
  });
}

(async () => {
  const recorder = createRecorder();
  initMysqlDefaults();
  // Staging and the browser launch sit inside the protected scope so a failure in
  // either still reaches the fixture cleanup below.
  try {
    cleanupRows();
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    await login(context, config, recorder);
    const expectedPatientName = patientName();
    assert(expectedPatientName, `demographic ${demographicNo} not found`);

    // 1. Contact enrolment through the admin page.
    await ensureMessengerContact(context, recorder);

    // 2. Messenger -> Compose Message.
    const inbox = await context.newPage();
    wirePage(inbox, 'messenger-inbox', recorder);
    await gotoApp(inbox, config.baseUrl, '/messenger/DisplayMessages');
    await inbox.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(inbox, 'messenger inbox');
    await inbox.locator('a[href$="/messenger/ViewCreateMessage"]').first().click();
    await inbox.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(inbox, 'compose message');
    assert((await inbox.locator('input[name="demographic_no"]').inputValue()) === '', 'plain compose form unexpectedly carried a patient');
    await pickRecipient(inbox);
    const plainRows = await sendMessage(inbox, plainSubject);
    assert(plainRows.length === 1, `expected one delivery row for the messenger-sent message, found ${plainRows.length}: ${JSON.stringify(plainRows)}`);
    assert(plainRows[0].recipient === providerNo && plainRows[0].status === 'new', `messenger-sent message delivery row was ${JSON.stringify(plainRows[0])}`);
    assert(plainRows[0].sentByNo === providerNo, `messenger-sent message sentbyNo ${plainRows[0].sentByNo}, expected ${providerNo}`);
    // The editor posts markdown, so underscores in the body arrive escaped.
    assert(plainRows[0].themessage.replace(/\\/g, '').includes(bodyText), `messenger-sent message body was not saved (got "${plainRows[0].themessage}")`);
    assert(plainRows[0].linkedDemo === '', `plain message was unexpectedly linked to a patient: ${JSON.stringify(plainRows)}`);

    // 3. Patient chart -> Messenger "+".
    const chart = await context.newPage();
    wirePage(chart, 'echart', recorder);
    await gotoApp(chart, config.baseUrl, `/encounter/IncomingEncounter?demographicNo=${encodeURIComponent(demographicNo)}&providerNo=${encodeURIComponent(providerNo)}&curProviderNo=${encodeURIComponent(providerNo)}`);
    await chart.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(chart, 'eChart');
    const composePopupPromise = context.waitForEvent('page', { timeout: 30000 });
    await chart.locator('a[onclick*="/messenger/SendDemoMessage?demographic_no="]').first().click();
    const compose = await composePopupPromise;
    wirePage(compose, 'chart-compose', recorder);
    await compose.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await compose.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(compose, 'chart compose');
    assert((await compose.locator('input[name="demographic_no"]').inputValue()) === demographicNo, 'chart compose did not pre-attach the patient');
    assert((await compose.locator('input[name="selectedDemo"]').inputValue()).trim() === expectedPatientName,
      `chart compose showed selected patient "${await compose.locator('input[name="selectedDemo"]').inputValue()}", expected "${expectedPatientName}"`);
    await pickRecipient(compose);
    const chartRows = await sendMessage(compose, chartSubject);
    await compose.close().catch(() => {});
    assert(chartRows.length === 1, `expected one delivery row for the chart-sent message, found ${chartRows.length}`);
    assert(chartRows[0].linkedDemo === demographicNo, `chart-sent message linked demographic was "${chartRows[0].linkedDemo}", expected ${demographicNo}`);
    assert(chartRows[0].recipient === providerNo && chartRows[0].status === 'new', `chart-sent message delivery row was ${JSON.stringify(chartRows[0])}`);

    // 4. Inbox shows the link and the message view resolves it.
    await gotoApp(inbox, config.baseUrl, '/messenger/DisplayMessages');
    await inbox.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const chartRowLink = inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${chartRows[0].messageId}&"]`).first();
    assert(await chartRowLink.count(), 'inbox did not list the chart-sent message');
    const inboxRowText = await chartRowLink.locator('xpath=ancestor::tr[1]').innerText();
    assert(inboxRowText.includes(expectedPatientName), `inbox row for the chart-sent message did not show the patient (${inboxRowText.replace(/\s+/g, ' ').trim()})`);
    await chartRowLink.click();
    await inbox.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(inbox, 'message view');
    await inbox.locator('#viewer .toastui-editor-contents').waitFor({ state: 'visible' });
    assert((await inbox.locator('#viewer').innerText()).includes(bodyText),
      'message viewer did not render the stored markdown without escaped underscores');
    const linkedInput = inbox.locator(`input[title="${demographicNo}"]`).first();
    assert(await linkedInput.count(), 'message view did not render the linked patient');
    assert((await linkedInput.inputValue()).trim() === expectedPatientName, 'message view linked patient name did not match');
    const bodyHtml = await inbox.locator('body').innerHTML();
    assert(bodyHtml.includes(`DemographicEdit?demographic_no=${demographicNo}`), 'message view M shortcut did not carry the demographic');
    assert(bodyHtml.includes(`IncomingEncounter?demographicNo=${demographicNo}`), 'message view E shortcut did not carry the demographic');
    assert(bodyHtml.includes(`demographicNo=${demographicNo}`) && /choosePatient\?providerNo=/.test(bodyHtml), 'message view Rx shortcut did not carry the demographic');
    const viewedRows = sqlRows(`SELECT status FROM messagelisttbl WHERE message=${Number(chartRows[0].messageId)} AND provider_no='${escapeSql(providerNo)}' ORDER BY id DESC LIMIT 1`).map(([status]) => ({ status }));
    assert(viewedRows[0].status !== 'new', `opening the message did not mark it read (status ${viewedRows[0].status})`);

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`PASS messenger send from messenger (message ${plainRows[0].messageId}) and from the chart (message ${chartRows[0].messageId}) linked to demographic ${demographicNo}`);
  } catch (error) {
    console.error(`FAIL messenger check: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
    runCleanup();
    cleanupMysqlDefaults();
  }
})();
