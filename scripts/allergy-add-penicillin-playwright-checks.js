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
 * Browser check for adding a Penicillins allergy through the patient's
 * allergy page (the popup the eChart's Allergies module opens), which
 * alpha-11 testers reported working.
 *
 *   1. opens /rx/showAllergy for the patient and clicks the "Penicillin"
 *      shortcut button; the reaction form must load into the page for
 *      PENICILLINS (drug class, TYPECODE 10, drugref id 44452);
 *   2. fills reaction, severity, onset, life stage and start date and
 *      submits "Add Allergy"; the page must return to the allergy list with
 *      PENICILLINS listed;
 *   3. asserts the allergies row: description, type code, drugref id,
 *      reaction, severity, onset, life stage, start date, not archived, and
 *      that the ATC/regional identifier lookup against DrugRef ran;
 *   4. asserts the eChart's Allergies module shows the new allergy.
 *
 * The allergy row is deleted in a finally.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: ALLERGY_DEMOGRAPHIC_NO (2; not 1, whose demo-seed chart notes
 *   panel answers 500 because of unshipped HRM report files -- tracked for
 *   review).
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
const demographicNo = process.env.ALLERGY_DEMOGRAPHIC_NO || '2';
assert(/^\d+$/.test(demographicNo), 'ALLERGY_DEMOGRAPHIC_NO must be numeric');
const reactionText = `PW_ALLERGY_${Date.now()} rash`;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'allergy-add-'));
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

function allergyRow() {
  const out = sql(`SELECT allergyid, DESCRIPTION, TYPECODE, drugref_id, reaction, severity_of_reaction, onset_of_reaction, start_date, life_stage, archived, IFNULL(regional_identifier,''), IFNULL(atc,'') FROM allergies WHERE demographic_no=${Number(demographicNo)} AND reaction='${escapeSql(reactionText)}' ORDER BY allergyid DESC LIMIT 1`);
  if (!out) {
    return null;
  }
  const [id, description, typeCode, drugrefId, reaction, severity, onset, startDate, lifeStage, archived, regionalId, atc] = out.split('\t');
  return { id, description, typeCode, drugrefId, reaction, severity, onset, startDate, lifeStage, archived, regionalId, atc };
}
function cleanupRows() {
  sql(`DELETE FROM allergies WHERE demographic_no=${Number(demographicNo)} AND reaction='${escapeSql(reactionText)}'`);
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
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1200, height: 1000 } });
    await login(context, config, recorder);

    // 1. Allergy page -> Penicillin shortcut.
    const page = await context.newPage();
    await page.addInitScript(() => {
      window.__confirms = [];
      window.confirm = (message) => { window.__confirms.push(String(message)); return true; };
    });
    wirePage(page, 'allergies', recorder);
    await gotoApp(page, config.baseUrl, `/rx/showAllergy?demographicNo=${encodeURIComponent(demographicNo)}`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'allergy page');
    const [reactionResponse] = await Promise.all([
      page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/rx/addReaction2'), { timeout: 30000 }),
      page.locator('input[value="Penicillin"]').click(),
    ]);
    assert(reactionResponse.status() < 400, `addReaction2 returned HTTP ${reactionResponse.status()}`);
    const form = page.locator('#RxAddAllergyForm');
    await form.waitFor({ state: 'visible', timeout: 30000 });
    assert((await form.locator('input[name="name"]').inputValue()) === 'PENICILLINS', 'reaction form is not for PENICILLINS');
    assert((await form.locator('input[name="type"]').inputValue()) === '10', 'PENICILLINS shortcut did not carry the drug-class type code');
    assert((await form.locator('input[name="ID"]').inputValue()) === '44452', 'PENICILLINS shortcut did not carry its drugref id');
    assert((await form.locator('input[name="formDemographicNo"]').inputValue()) === demographicNo, 'reaction form is not bound to the patient');

    // 2. Details + submit.
    await form.locator('#reactionDescription').fill(reactionText);
    await form.locator('select[name="severityOfReaction"]').selectOption('3');
    await form.locator('select[name="onSetOfReaction"]').selectOption('1');
    await form.locator('select[name="lifeStage"]').selectOption('A');
    await form.locator('#startDate').fill('2024-01-15');
    if (await form.locator('select[name="nonDrug"]').count()) {
      await form.locator('select[name="nonDrug"]').selectOption('off');
    }
    const [saveResponse] = await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/rx/addAllergy2'), { timeout: 30000 }),
      form.locator('input[type="submit"][value="Add Allergy"]').click(),
    ]);
    assert(saveResponse.status() < 400, `addAllergy2 returned HTTP ${saveResponse.status()}`);
    await page.waitForURL(/\/rx\/showAllergy/, { timeout: 30000 });
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'allergy page after add');
    assert((await page.locator('body').innerText()).includes('PENICILLINS'), 'allergy list did not show PENICILLINS after adding it');

    // 3. Persistence.
    const row = allergyRow();
    assert(row, 'allergies row was not created');
    assert(row.description === 'PENICILLINS', `saved description was ${row.description}`);
    assert(row.typeCode === '10', `saved TYPECODE was ${row.typeCode}`);
    assert(row.drugrefId === '44452', `saved drugref_id was ${row.drugrefId}`);
    assert(row.severity === '3' && row.onset === '1' && row.lifeStage === 'A', `saved severity/onset/lifeStage were ${row.severity}/${row.onset}/${row.lifeStage}`);
    assert(row.startDate.startsWith('2024-01-15'), `saved start_date was ${row.startDate}`);
    assert(row.archived === '0', 'new allergy was saved archived');
    assert(row.regionalId !== '' || row.atc !== '', 'DrugRef lookup did not populate regional_identifier/atc for the class');

    // 4. eChart shows it.
    const chart = await context.newPage();
    wirePage(chart, 'echart', recorder);
    await gotoApp(chart, config.baseUrl, `/encounter/IncomingEncounter?demographicNo=${encodeURIComponent(demographicNo)}&providerNo=${encodeURIComponent(config.testUser === 'carlosdoc' ? '999998' : '')}&curProviderNo=`);
    await chart.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(chart, 'eChart');
    await chart.waitForFunction(() => document.body.innerText.includes('PENICILLINS'), null, { timeout: 30000 });
    await chart.close();

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`PASS Penicillins allergy ${row.id} added for demographic ${demographicNo} and shown in the eChart`);
  } catch (error) {
    console.error(`FAIL Penicillins allergy check: ${error.stack || error.message}`);
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
