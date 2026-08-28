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
 * Browser regression check for the add-demographic form (add.jsp), driven the
 * way a user reaches it: login, Search popup, a search with no matches, the
 * "Create Demographic" link, fill the form, save, land on the master record.
 *
 * Pins the add.jsp script-block regression where a missing brace in
 * parseHINforVC() made the whole inline <script> fail to parse, so EVERY
 * page function (aSubmit, the validators, the DOB sync) came up undefined and
 * the Add button silently did nothing ("aSubmit is not defined"). The check
 * asserts the functions exist before it ever submits, so a re-broken script
 * block fails loudly rather than as an opaque form that will not save.
 *
 * The created patient is removed again through the master-record UI's data
 * this script captures (SQL delete of the demographic row it created), so
 * repeat runs do not accumulate records. Requires the standard deb-install
 * env contract (see docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (for the cleanup delete)
 * Optional: CHROME_PATH, DEMOGRAPHIC_ADD_SCREENSHOT_DIR (default /tmp).
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
  assertNotErrorPage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.DEMOGRAPHIC_ADD_SCREENSHOT_DIR || '/tmp',
};
const mysqlHost = process.env.MYSQL_HOST || '127.0.0.1';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'oscar';

// Unique, clearly synthetic name so a failed cleanup is identifiable and a
// stray record can never be mistaken for a real patient.
const fixtureLastName = `PLAYWRIGHT-ADD-${Date.now()}`;
const fixtureFirstName = 'Check';

let mysqlDefaults = null;
function initMysqlDefaults() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'demographic-add-'));
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

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  initMysqlDefaults();
  let createdDemographicNo = null;
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const schedulePage = await login(context, config, recorder);

    // User path: Search popup from the schedule banner.
    const searchPopup = context.waitForEvent('page');
    await schedulePage.locator('a').filter({ hasText: /^Search$/ }).click();
    const searchPage = await searchPopup;
    wirePage(searchPage, 'search', recorder);
    await searchPage.waitForLoadState('domcontentloaded', { timeout: 30000 });

    // A no-match search renders the results page that carries "Create Demographic".
    await searchPage.locator('#keyword, input[name="keyword"]').first().fill(fixtureLastName);
    await Promise.all([
      searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
      searchPage.locator("input[type='submit']").first().click(),
    ]);
    await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await searchPage.locator("a[href*='ViewDemographicAddARecordHtm']").first().click();
    await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(searchPage, 'add-demographic form');

    // The brace-fix pin: with the broken script block, none of these existed.
    const scriptState = await searchPage.evaluate(() => ({
      aSubmit: typeof window.aSubmit,
      parseHINforVC: typeof window.parseHINforVC,
      syncInputDobParts: typeof window.syncInputDobParts,
    }));
    assert(scriptState.aSubmit === 'function',
      `add.jsp inline script did not define aSubmit (${JSON.stringify(scriptState)}) — script block is broken again`);
    assert(scriptState.parseHINforVC === 'function',
      `add.jsp inline script did not define parseHINforVC (${JSON.stringify(scriptState)})`);
    assert(recorder.pageErrors.length === 0,
      `add-demographic form raised page errors: ${JSON.stringify(recorder.pageErrors)}`);
    await screenshot(searchPage, config.screenshotDir, 'demographic-add-form');

    // Fill a minimal valid patient the way a user would. The HIN stays empty
    // on purpose: aSubmit() still runs parseHINforVC() on submit (the pin for
    // the brace regression), and an empty HIN cannot trip the ON checksum
    // validator into an alert that would silently cancel the save.
    const form = searchPage.locator('form[name="adddemographic"]');
    await form.locator('input[name="last_name"]').fill(fixtureLastName);
    await form.locator('input[name="first_name"]').fill(fixtureFirstName);
    await form.locator('select[name="sex"]').selectOption('F');
    await form.locator('input[name="inputDOB"]').fill('1990-01-15');
    // The save-path validator rejects an empty/invalid postal code with an
    // alert, so give it a syntactically valid (Canada Post reserved) one.
    await form.locator('input[name="postal"]').fill('M5W 1E6');

    // The "Add Record" submit is form-associated but rendered outside the
    // form element's subtree, so locate it at page level.
    await Promise.all([
      searchPage.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {}),
      searchPage.locator('input[type="submit"][value="Add Record"]').first().click(),
    ]);
    await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    assert(recorder.dialogs.length === 0,
      `add-form validation blocked the save with a dialog: ${JSON.stringify(recorder.dialogs)}`);
    await assertNotErrorPage(searchPage, 'post-save page');
    const savedBody = await searchPage.locator('body').innerText();
    assert(/Successful Addition of a Demographic Record/i.test(savedBody),
      `post-save page did not confirm the save: ${savedBody.replace(/\s+/g, ' ').slice(0, 300)}`);
    await screenshot(searchPage, config.screenshotDir, 'demographic-add-saved');

    // Continue the way a user does: "Go to record" opens the new master
    // record, which must show the patient it just created.
    await Promise.all([
      searchPage.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {}),
      searchPage.locator('a').filter({ hasText: /Go to record/i }).first().click(),
    ]);
    await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(searchPage, 'new master record');
    const masterBody = await searchPage.locator('body').innerText();
    assert(masterBody.includes(fixtureLastName),
      `master record does not show the new patient ${fixtureLastName}: ${masterBody.replace(/\s+/g, ' ').slice(0, 300)}`);
    await screenshot(searchPage, config.screenshotDir, 'demographic-add-master-record');

    const found = sql(`SELECT demographic_no FROM demographic WHERE last_name='${fixtureLastName}' AND first_name='${fixtureFirstName}'`);
    assert(/^\d+$/.test(found), `saved patient not found in the database (got '${found}')`);
    createdDemographicNo = found;

    const fatalConsole = recorder.consoleIssues.filter((issue) => /is not defined|SyntaxError|ReferenceError/i.test(issue.text || ''));
    assert(fatalConsole.length === 0, `fatal console errors during add flow: ${JSON.stringify(fatalConsole)}`);

    await context.close();
    console.log(`PASS add-demographic form scripts intact, patient saved and verified (demographic_no ${createdDemographicNo})`);
  } catch (error) {
    console.error('FAIL add-demographic Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    // Remove the synthetic patient (and the admission the add flow creates)
    // so repeat runs stay clean. Looked up by the unique per-run last name —
    // never a bare number — so a bug can never delete a pre-existing record,
    // and a run that failed BEFORE capturing the id still cleans up after
    // itself if the save had already gone through.
    try {
      const leftover = createdDemographicNo
        || sql(`SELECT demographic_no FROM demographic WHERE last_name='${fixtureLastName}' AND first_name='${fixtureFirstName}'`);
      if (/^\d+$/.test(leftover)) {
        sql(`DELETE FROM admission WHERE client_id=${leftover}`);
        sql(`DELETE FROM demographicArchive WHERE demographic_no=${leftover}`);
        sql(`DELETE FROM demographic WHERE demographic_no=${leftover} AND last_name='${fixtureLastName}'`);
      }
    } catch (cleanupError) {
      console.error(`WARN cleanup failed: ${cleanupError.message}`);
    }
    cleanupMysqlDefaults();
    await browser.close();
  }
})();
