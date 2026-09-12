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
 * Browser check for Administration > Billing > Manage Billing Service Code
 * (/billing/CA/ON/AddEditServiceCode): the "billing code updates" alpha-11
 * testers reported working.
 *
 *   1. Search an existing Ontario code, assert the form loads its description
 *      and fee and offers Save;
 *   2. change the fee and description, Save (the page asks "Are you sure you
 *      want to save?" -- the check accepts it), assert the success banner and
 *      the billingservice row, then restore the original values the same way
 *      and assert the row is back;
 *   3. search a code that does not exist, assert the "NEW service code" path,
 *      fill description / fee / issued date, Save, assert "<CODE> is added."
 *      and the new billingservice row; the row is deleted in a finally.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: BILLING_CODE_EXISTING (A007A), BILLING_CODE_NEW (X987Z).
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
function code(name, raw, fallback) {
  const value = (raw === undefined || raw === '' ? fallback : raw).toUpperCase();
  assert(/^[A-Z]\d{3}[A-Z]$/.test(value), `${name} must be an Ontario service code like A007A, got ${value}`);
  return value;
}
const existingCode = code('BILLING_CODE_EXISTING', process.env.BILLING_CODE_EXISTING, 'A007A');
const newCode = code('BILLING_CODE_NEW', process.env.BILLING_CODE_NEW, 'X987Z');
const stamp = `PW_CODE_${Date.now()}`;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'billing-code-admin-'));
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

function latestRow(serviceCode) {
  const out = sql(`SELECT billingservice_no, description, value, billingservice_date, termination_date FROM billingservice WHERE service_code='${escapeSql(serviceCode)}' ORDER BY billingservice_date DESC, billingservice_no DESC LIMIT 1`);
  if (!out) {
    return null;
  }
  const [id, description, value, issued, termination] = out.split('\t');
  return { id, description, value, issued, termination };
}

let original = null;
function restoreOriginalBySql() {
  if (original) {
    sql(`UPDATE billingservice SET description='${escapeSql(original.description)}', value='${escapeSql(original.value)}' WHERE billingservice_no=${Number(original.id)}`);
  }
}
function cleanupRows() {
  sql(`DELETE FROM billingservice WHERE service_code='${escapeSql(newCode)}'`);
}

async function openAdminPage(context, recorder) {
  const page = await context.newPage();
  // onSave() asks "Are you sure you want to save?" through window.confirm.
  // Answer it in-page (recording the prompt) so the shared recorder, which
  // dismisses stray dialogs, cannot race the answer and cancel the save.
  await page.addInitScript(() => {
    window.confirm = (message) => {
      try { sessionStorage.setItem('pwLastConfirm', String(message)); } catch (ignored) { /* storage unavailable */ }
      return true;
    };
  });
  wirePage(page, 'service-code-admin', recorder);
  await gotoApp(page, config.baseUrl, '/billing/CA/ON/AddEditServiceCode');
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'service code admin');
  return page;
}

async function search(page, serviceCode) {
  await page.locator('input[name="service_code"]').fill(serviceCode);
  await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/billing/CA/ON/AddEditServiceCode'), { timeout: 30000 }),
    page.locator('button[name="submitFrm"][value="Search"]').first().click(),
  ]);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await assertNotErrorPage(page, `search ${serviceCode}`);
  return page.locator('.alert').first().innerText().catch(() => '');
}

async function save(page) {
  await Promise.all([
    page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/billing/CA/ON/AddEditServiceCode'), { timeout: 30000 }),
    page.locator('input[name="submitFrm"][value="Save"]').click(),
  ]);
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await assertNotErrorPage(page, 'save');
  return page.locator('.alert').first().innerText().catch(() => '');
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
  for (const step of [restoreOriginalBySql, cleanupRows]) {
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
    original = latestRow(existingCode);
    assert(original, `service code ${existingCode} is not in billingservice`);
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    await login(context, config, recorder);
    const page = await openAdminPage(context, recorder);

    // 1. Search an existing code.
    let banner = await search(page, existingCode);
    assert(/edit the service code by clicking 'Save'/i.test(banner), `search ${existingCode} banner was "${banner}"`);
    assert((await page.locator('textarea[name="description"]').inputValue()).trim() === original.description.trim(),
      `search did not load the description of ${existingCode}`);
    assert(Number(await page.locator('input[name="value"]').inputValue()) === Number(original.value),
      `search did not load the fee of ${existingCode}`);
    assert((await page.locator('input[name="submitFrm"][value="Save"]').count()) === 1, 'search result did not offer Save');

    // 2. Update fee + description, then restore.
    const newFee = (Number(original.value) + 0.05).toFixed(2);
    const newDescription = `${original.description.trim()} ${stamp}`;
    await page.locator('input[name="value"]').fill(newFee);
    await page.locator('textarea[name="description"]').fill(newDescription);
    banner = await save(page);
    assert(banner.includes(`${existingCode} is updated`), `save banner was "${banner}"`);
    let row = latestRow(existingCode);
    assert(row.id === original.id, 'save created a new row instead of updating the searched entry');
    assert(Number(row.value) === Number(newFee), `fee after save was ${row.value}, expected ${newFee}`);
    assert(row.description.trim() === newDescription, `description after save was "${row.description}"`);
    assert(recorder.dialogs.length === 0, `save raised an unexpected dialog: ${JSON.stringify(recorder.dialogs)}`);
    const lastConfirm = await page.evaluate(() => { try { return sessionStorage.getItem('pwLastConfirm') || ''; } catch (ignored) { return ''; } });
    assert(/sure you want to save/i.test(lastConfirm), `save did not ask for confirmation (last prompt: "${lastConfirm}")`);

    banner = await search(page, existingCode);
    assert(Number(await page.locator('input[name="value"]').inputValue()) === Number(newFee), 'reload did not show the updated fee');
    await page.locator('input[name="value"]').fill(Number(original.value).toFixed(2));
    await page.locator('textarea[name="description"]').fill(original.description.trim());
    banner = await save(page);
    assert(banner.includes(`${existingCode} is updated`), `restore banner was "${banner}"`);
    row = latestRow(existingCode);
    assert(Number(row.value) === Number(original.value) && row.description.trim() === original.description.trim(),
      'restore did not put the original fee and description back');

    // 3. Add a brand-new code.
    banner = await search(page, newCode);
    assert(/NEW service code/i.test(banner), `search ${newCode} banner was "${banner}"`);
    await page.locator('textarea[name="description"]').fill(`${stamp} new code`);
    await page.locator('input[name="value"]').fill('12.34');
    await page.locator('#billingservice_date').fill('2026-01-01');
    // The issued-date field is a flatpickr input; close its calendar overlay so
    // it cannot sit over the Save button.
    await page.keyboard.press('Escape');
    await page.locator('textarea[name="description"]').click();
    assert((await page.locator('#billingservice_date').inputValue()) === '2026-01-01', 'issued date did not keep the typed value');
    banner = await save(page);
    assert(banner.includes(`${newCode} is added`), `add banner was "${banner}"`);
    row = latestRow(newCode);
    assert(row, `${newCode} row was not added`);
    assert(Number(row.value) === 12.34 && row.description.trim() === `${stamp} new code`, `added row was ${JSON.stringify(row)}`);
    assert(row.issued === '2026-01-01', `added row issued date was ${row.issued}`);
    banner = await search(page, newCode);
    assert(/edit the service code by clicking 'Save'/i.test(banner), `the added code was not found on re-search: "${banner}"`);

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`PASS billing service code admin: ${existingCode} updated and restored, ${newCode} added`);
  } catch (error) {
    console.error(`FAIL billing service code admin check: ${error.stack || error.message}`);
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
