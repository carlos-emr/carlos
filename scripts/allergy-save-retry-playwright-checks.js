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
 * Browser check for issue #3488: a failed allergy save must not discard what the
 * clinician typed, and retrying must persist exactly one record.
 *
 *   A. The save request is aborted (network failure) before reaching the server:
 *      the dialogue stays on the page, shows the "NOT SAVED" banner, keeps the
 *      comment / start date / age of onset, and nothing is in the database. The
 *      retry then saves exactly one row.
 *   B. The save reaches the server and persists, but its response is replaced by
 *      a 502 (a lost response): the banner shows, the values are kept, and the
 *      retry sends the same saveToken so the server does not add a second row.
 *
 * Rows carrying the run marker are deleted in a finally.
 *
 * Environment: same as allergy-add-penicillin-playwright-checks.js.
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
const demographicNo = process.env.ALLERGY_DEMOGRAPHIC_NO || '1';
assert(/^\d+$/.test(demographicNo), 'ALLERGY_DEMOGRAPHIC_NO must be numeric');
const reactionMarker = `PW_ALLERGY_${Date.now()}`;
const reactionText = `${reactionMarker} rash`;
// The amend path writes a SECOND allergies row rather than updating the first, so
// both carry the run marker and cleanup deletes by prefix.
const amendedReactionText = `${reactionMarker} hives`;

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

function allergyRow(wantedReaction = reactionText) {
  const out = sql(`SELECT allergyid, DESCRIPTION, TYPECODE, drugref_id, reaction, severity_of_reaction, onset_of_reaction, start_date, life_stage, archived, IFNULL(regional_identifier,''), IFNULL(atc,'') FROM allergies WHERE demographic_no=${Number(demographicNo)} AND reaction='${escapeSql(wantedReaction)}' ORDER BY allergyid DESC LIMIT 1`);
  if (!out) {
    return null;
  }
  const [id, description, typeCode, drugrefId, reaction, severity, onset, startDate, lifeStage, archived, regionalId, atc] = out.split('\t');
  return { id, description, typeCode, drugrefId, reaction, severity, onset, startDate, lifeStage, archived, regionalId, atc };
}
function cleanupRows() {
  sql(`DELETE FROM allergies WHERE demographic_no=${Number(demographicNo)} AND reaction LIKE '${escapeSql(reactionMarker)}%'`);
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

async function fillStartDate(page, form, value) {
  const date = form.locator('#startDate');
  await date.fill(value);
  // Typing opens flatpickr over the submit button. Dismiss it through the keyboard
  // and leave the field so the picker commits the entered date before submission.
  await date.press('Escape');
  await date.press('Tab');
  await page.locator('.flatpickr-calendar.open').waitFor({ state: 'hidden' });
  assert(await date.inputValue() === value, 'allergy start date changed when leaving its picker');
}


async function openPenicillinDialogue(page) {
  await gotoApp(page, config.baseUrl, `/rx/showAllergy?demographicNo=${encodeURIComponent(demographicNo)}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'allergy page');
  await page.locator('input[value="Penicillin"]').click();
  const form = page.locator('#RxAddAllergyForm');
  await form.waitFor({ state: 'visible', timeout: 30000 });
  return form;
}

async function fillDialogue(page, form, reaction) {
  await form.locator('#reactionDescription').fill(reaction);
  await fillStartDate(page, form, '2024-01-15');
  await form.locator('input[name="ageOfOnset"]').fill('42');
  if (await form.locator('select[name="nonDrug"]').count()) {
    await form.locator('select[name="nonDrug"]').selectOption('off');
  }
}

async function assertValuesKept(page, form, reaction, label) {
  const banner = page.locator('#allergySaveError');
  await banner.waitFor({ state: 'visible', timeout: 15000 });
  assert((await banner.innerText()).includes('NOT SAVED'), `${label}: banner does not say the allergy was not saved`);
  assert(/\/rx\/showAllergy/.test(page.url()) && await form.isVisible(), `${label}: dialogue was navigated away from`);
  assert((await form.locator('#reactionDescription').inputValue()) === reaction, `${label}: comment was discarded`);
  assert((await form.locator('#startDate').inputValue()) === '2024-01-15', `${label}: start date was discarded`);
  assert((await form.locator('input[name="ageOfOnset"]').inputValue()) === '42', `${label}: age of onset was discarded`);
  assert(await form.locator('input[type="submit"]').isEnabled(), `${label}: Add Allergy stayed disabled, so no retry is possible`);
}

function countRows(reaction) {
  return Number(sql(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${Number(demographicNo)} AND reaction='${escapeSql(reaction)}'`));
}

const isSave = (url) => new URL(url).pathname.endsWith('/rx/addAllergy2');

(async () => {
  const recorder = createRecorder();
  initMysqlDefaults();
  try {
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1200, height: 1000 } });
    await login(context, config, recorder);
    const page = await context.newPage();
    await page.addInitScript(() => { window.confirm = () => true; });
    wirePage(page, 'allergies', recorder);

    // A. request never reaches the server.
    const reactionA = `${reactionMarker} A`;
    let form = await openPenicillinDialogue(page);
    await fillDialogue(page, form, reactionA);
    let failNext = true;
    await page.route(isSave, async (route) => {
      if (failNext) { failNext = false; await route.abort('connectionreset'); } else { await route.continue(); }
    });
    await form.locator('input[type="submit"][value="Add Allergy"]').click();
    await assertValuesKept(page, form, reactionA, 'A (aborted)');
    assert(countRows(reactionA) === 0, 'A: a row exists although the save never reached the server');
    // The page already sits on /rx/showAllergy, so waiting for the URL would return at once and
    // count rows before the retry's POST completes: wait for the save, then for the dialogue to go.
    await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST' && isSave(response.url()), { timeout: 30000 }),
      form.locator('input[type="submit"][value="Add Allergy"]').click(),
    ]);
    await form.waitFor({ state: 'detached', timeout: 30000 });
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    assert(countRows(reactionA) === 1, `A: retry persisted ${countRows(reactionA)} rows, expected exactly 1`);
    await page.unroute(isSave);

    // B. server persists, response is lost.
    const reactionB = `${reactionMarker} B`;
    form = await openPenicillinDialogue(page);
    await fillDialogue(page, form, reactionB);
    let loseNext = true;
    await page.route(isSave, async (route) => {
      if (loseNext) {
        loseNext = false;
        await route.fetch();
        await route.fulfill({ status: 502, contentType: 'text/plain', body: 'bad gateway' });
      } else {
        await route.continue();
      }
    });
    await form.locator('input[type="submit"][value="Add Allergy"]').click();
    await assertValuesKept(page, form, reactionB, 'B (lost response)');
    assert(countRows(reactionB) === 1, 'B: first attempt should have persisted one row server-side');
    // The page already sits on /rx/showAllergy, so waiting for the URL would return at once and
    // count rows before the retry's POST completes: wait for the save, then for the dialogue to go.
    await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST' && isSave(response.url()), { timeout: 30000 }),
      form.locator('input[type="submit"][value="Add Allergy"]').click(),
    ]);
    await form.waitFor({ state: 'detached', timeout: 30000 });
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    assert(countRows(reactionB) === 1, `B: retry produced ${countRows(reactionB)} rows, expected exactly 1 (duplicate)`);

    assertNoPageErrors(recorder);
    console.log(`PASS failed allergy saves kept the entries, and retry persisted exactly one row (demographic ${demographicNo})`);
  } catch (error) {
    console.error(`FAIL allergy save retry check: ${error.stack || error.message}`);
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
