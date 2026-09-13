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
 * Browser check for creating a consultation request the way a clinician does.
 *
 * Alpha-11 testers reported "consultation requests work". The existing
 * consultation checks either open pre-existing requests or POST a JS-built form
 * with a hard-coded specialist, so none of them proves the interactive form's
 * own Submit button still creates a row. This one does:
 *
 *   1. opens the patient's consultation list
 *      (ViewDisplayDemographicConsultationRequests) and clicks its
 *      "New Consultation" button, capturing the popup;
 *   2. picks a service and then a consultant through the jQuery UI autocomplete
 *      inputs (#serviceInput / #specialistInput) -- the hidden #service and
 *      #specialist fields that are actually posted must be populated by the
 *      pickers, not by the script;
 *   3. fills reason, urgency and notes, and clicks "Submit Consultation
 *      Request" (checkForm -> form submit -> RequestConsultation);
 *   4. asserts the confirmation page says the request was Created and that a
 *      consultationRequests row exists for the patient with the chosen
 *      specialist, service, urgency and stamped reason;
 *   5. reopens the saved request (ViewRequest?requestId=N) and asserts the
 *      chosen consultant and the stamped reason render back;
 *   6. asserts the request now shows in the patient's consultation list.
 *
 * The row is deleted in a finally, so repeat runs stay clean.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: CONSULT_DEMO_NO (default 1).
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
const demographicNo = process.env.CONSULT_DEMO_NO || '1';
assert(/^\d+$/.test(demographicNo), 'CONSULT_DEMO_NO must be numeric');

const stamp = `PW_CONSULT_CREATE_${Date.now()}`;
const reasonText = `${stamp} reason for consultation`;
const notesText = `${stamp} appointment notes`;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-create-'));
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
function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function findRequestRows() {
  const out = sql(
    'SELECT requestId, demographicNo, specId, serviceId, urgency, status, reason, statusText'
    + ` FROM consultationRequests WHERE reason LIKE '${escapeSql(`${stamp}%`)}' ORDER BY requestId`,
  );
  return out ? out.split('\n').map((line) => {
    const [requestId, demoNo, specId, serviceId, urgency, status, reason, appointmentNotes] = line.split('\t');
    return { requestId, demoNo, specId, serviceId, urgency, status, reason, appointmentNotes };
  }) : [];
}

function cleanupRows() {
  sql(`DELETE FROM consultationRequests WHERE reason LIKE '${escapeSql(`${stamp}%`)}'`);
}

async function pickFirstAutocomplete(page, inputSelector, label) {
  const input = page.locator(inputSelector);
  await input.click();
  // minLength:0 pickers open on click and list every candidate.
  const menuItem = page.locator('ul.ui-autocomplete:visible li.ui-menu-item').first();
  await menuItem.waitFor({ state: 'visible', timeout: 15000 });
  const text = (await menuItem.innerText()).trim();
  assert(text, `${label} autocomplete offered an empty first entry`);
  await menuItem.click();
  await page.waitForTimeout(250);
  return text;
}

async function openConsultationList(context, recorder) {
  const page = await context.newPage();
  wirePage(page, 'consult-list', recorder);
  await gotoApp(page, config.baseUrl, `/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=${encodeURIComponent(demographicNo)}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'consultation list');
  return page;
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

    // 1. Operator path: patient consultation list -> "New Consultation" popup.
    const listPage = await openConsultationList(context, recorder);
    const popupPromise = context.waitForEvent('page', { timeout: 30000 });
    await listPage.locator('a.btn', { hasText: /New Consultation/i }).first().click();
    const formPage = await popupPromise;
    wirePage(formPage, 'consult-form', recorder);
    await formPage.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await formPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(formPage, 'consultation form');
    await formPage.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: 30000 });
    assert((await formPage.locator('#demographicNo').inputValue()) === demographicNo,
      'consultation form did not carry the patient it was opened for');

    // 2. Service, then consultant, through the pickers.
    const serviceLabel = await pickFirstAutocomplete(formPage, '#serviceInput', 'service');
    const serviceId = await formPage.locator('#service').inputValue();
    assert(/^\d+$/.test(serviceId) && serviceId !== '0', `service picker did not populate #service (got "${serviceId}")`);
    const specialistLabel = await pickFirstAutocomplete(formPage, '#specialistInput', 'specialist');
    const specialistId = await formPage.locator('#specialist').inputValue();
    assert(/^\d+$/.test(specialistId), `specialist picker did not populate #specialist (got "${specialistId}")`);
    // The picker copies the consultant's own name into the visible input; the
    // list entry can carry extra service badges, so match on the name prefix.
    const specialistName = (await formPage.locator('#specialistInput').inputValue()).trim();
    assert(specialistName && specialistLabel.startsWith(specialistName),
      `specialist picker text "${specialistName}" did not come from the chosen entry "${specialistLabel}"`);

    // 3. Clinical content and submit.
    await formPage.locator('#urgency').selectOption('1');
    await formPage.locator('textarea[name="reasonForConsultation"]').fill(reasonText);
    await formPage.locator('textarea[name="appointmentNotes"]').fill(notesText);
    await Promise.all([
      formPage.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/encounter/RequestConsultation'), { timeout: 30000 }),
      formPage.locator('input[name="submitSaveOnly"]').click(),
    ]);
    await formPage.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await assertNotErrorPage(formPage, 'consultation confirmation');
    // The redirect must retain the successful create confirmation.
    assert(/ViewConfirmConsultationRequest/.test(formPage.url()),
      `submit did not land on the confirmation page (url ${formPage.url()})`);
    const confirmText = await formPage.locator('body').innerText();
    assert(/Consultation Request Form has been\s+Created/i.test(confirmText),
      'confirmation did not show the successful create message');
    assert(/This window will close in 5 seconds/i.test(confirmText),
      `confirmation page did not render its close countdown: ${confirmText.slice(0, 300)}`);
    assert(recorder.dialogs.length === 0, `unexpected dialogs during submit: ${JSON.stringify(recorder.dialogs)}`);

    // 4. Persistence.
    const rows = findRequestRows();
    assert(rows.length === 1, `expected one consultationRequests row, found ${rows.length}`);
    const row = rows[0];
    assert(row.demoNo === demographicNo, `request saved for demographic ${row.demoNo}, expected ${demographicNo}`);
    assert(row.specId === specialistId, `request saved specId ${row.specId}, expected ${specialistId}`);
    assert(row.serviceId === serviceId, `request saved serviceId ${row.serviceId}, expected ${serviceId}`);
    assert(row.urgency === '1', `request saved urgency ${row.urgency}, expected 1 (Urgent)`);
    assert(row.appointmentNotes === notesText, 'request did not persist the appointment notes');

    // 5. Reopen the saved request.
    const viewPage = await context.newPage();
    wirePage(viewPage, 'consult-view', recorder);
    await gotoApp(viewPage, config.baseUrl, `/encounter/ViewRequest?requestId=${encodeURIComponent(row.requestId)}`);
    await viewPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(viewPage, 'saved consultation');
    assert((await viewPage.locator('#specialist').inputValue()) === specialistId,
      'reopened request did not restore the chosen specialist');
    assert((await viewPage.locator('#specialistInput').inputValue()).trim() === specialistName,
      'reopened request did not show the consultant name');
    assert((await viewPage.locator('textarea[name="reasonForConsultation"]').inputValue()) === reasonText,
      'reopened request did not restore the reason text');
    await viewPage.close();

    // 6. It shows in the patient's list. The confirmation popup refreshes its
    // opener when it auto-closes, so navigate the list page fresh rather than
    // racing that reload.
    await formPage.close().catch(() => {});
    await gotoApp(listPage, config.baseUrl, `/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=${encodeURIComponent(demographicNo)}`);
    await listPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const listText = await listPage.locator('body').innerText();
    assert(listText.includes(specialistName) && /Urgent/i.test(listText),
      'patient consultation list did not show the new request');

    const { badResponses, consoleIssues } = recorder;
    assertNoPageErrors(recorder);
    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
    assert(consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(consoleIssues, null, 2)}`);
    console.log(`PASS consultation request created through the UI (requestId ${row.requestId}, service "${serviceLabel}", consultant "${specialistName}")`);
  } catch (error) {
    console.error(`FAIL consultation request create check: ${error.stack || error.message}`);
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
