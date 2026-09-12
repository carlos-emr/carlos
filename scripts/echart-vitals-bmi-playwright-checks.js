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
 * Browser check for eChart measurements: add height/weight, double-click to
 * convert imperial to metric, and the automatic BMI, as alpha-11 testers
 * reported. The behaviour lives on the Anthropometrics group of the
 * measurement popup (SetupMeasurements), which is the popup the eChart's
 * Measurements module opens for that group.
 *
 *   1. types 5'10" into HT and double-clicks it: the page asks to convert and
 *      the cell becomes 177.8 (cm);
 *   2. types 150 into WT and double-clicks it: converted to 68 (kg);
 *   3. leaving the WT field computes BMI = 21.5 into the BMI cell;
 *   4. Submit posts the group (ajax) and the popup closes; measurements rows
 *      HT=177.8, WT=68, BMI=21.5 exist for the patient with today's date.
 *
 * Rows the check created are deleted in a finally.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: VITALS_DEMOGRAPHIC_NO (1), VITALS_GROUP (Anthropometrics).
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
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};
const mysqlHost = process.env.MYSQL_HOST || '127.0.0.1';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
const demographicNo = process.env.VITALS_DEMOGRAPHIC_NO || '1';
const groupName = process.env.VITALS_GROUP || 'Anthropometrics';
assert(/^\d+$/.test(demographicNo), 'VITALS_DEMOGRAPHIC_NO must be numeric');

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'echart-vitals-'));
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

let measurementHighWater = 0;
function newMeasurementRows() {
  return sqlRows(`SELECT id, type, dataField, measuringInstruction, DATE(dateObserved) FROM measurements WHERE demographicNo=${Number(demographicNo)} AND id > ${measurementHighWater} ORDER BY id`)
    .map(([id, type, dataField, instruction, dateObserved]) => ({ id, type, dataField, instruction, dateObserved }));
}
function cleanupRows() {
  for (const row of newMeasurementRows()) {
    sql(`DELETE FROM measurements WHERE id=${Number(row.id)}`);
  }
}

(async () => {
  const recorder = createRecorder();
  initMysqlDefaults();
  measurementHighWater = Number(sql('SELECT IFNULL(MAX(id), 0) FROM measurements'));
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1200, height: 1000 } });
    await login(context, config, recorder);

    const page = await context.newPage();
    // Both conversions ask through window.confirm; answer in-page, recording
    // the prompts, so the shared recorder cannot dismiss them.
    await page.addInitScript(() => {
      window.__confirms = [];
      window.confirm = (message) => { window.__confirms.push(String(message)); return true; };
      window.close = () => { window.__closed = true; };
    });
    wirePage(page, 'measurements', recorder);
    await gotoApp(page, config.baseUrl, `/encounter/oscarMeasurements/SetupMeasurements?demographicNo=${encodeURIComponent(demographicNo)}&groupName=${encodeURIComponent(groupName)}`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'measurements popup');
    const ht = page.locator('#row-HT td:nth-child(3) input');
    const wt = page.locator('#row-WT td:nth-child(3) input');
    const bmi = page.locator('#row-BMI td:nth-child(3) input');
    assert((await ht.count()) && (await wt.count()) && (await bmi.count()), `group ${groupName} did not render HT, WT and BMI rows`);
    assert(/Double click to convert/i.test(await ht.getAttribute('title') || ''), 'HT cell is not wired for double-click conversion');
    assert(/Double click to convert/i.test(await wt.getAttribute('title') || ''), 'WT cell is not wired for double-click conversion');

    // 1. Height: feet/inches -> cm.
    await ht.fill('5\'10"');
    await ht.dblclick();
    assert((await ht.inputValue()) === '177.8', `height did not convert to cm (got ${await ht.inputValue()})`);

    // 2. Weight: lb -> kg.
    await wt.fill('150');
    await wt.dblclick();
    assert((await wt.inputValue()) === '68', `weight did not convert to kg (got ${await wt.inputValue()})`);
    const confirms = await page.evaluate(() => window.__confirms);
    assert(confirms.some((m) => /pounds/.test(m)) && confirms.some((m) => /feet/.test(m)), `conversion prompts were ${JSON.stringify(confirms)}`);

    // 3. BMI on blur.
    await wt.dispatchEvent('blur');
    await page.waitForFunction(() => document.querySelector('#row-BMI td:nth-child(3) input').value !== '', null, { timeout: 10000 });
    assert((await bmi.inputValue()) === '21.5', `BMI was ${await bmi.inputValue()}, expected 21.5`);

    // 4. Submit.
    const [saveResponse] = await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST' && /\/encounter\/Measurements\?ajax=true/.test(response.url()), { timeout: 30000 }),
      page.locator('input[name="Button"][value="Submit"]').click(),
    ]);
    assert(saveResponse.status() < 400, `measurement save returned HTTP ${saveResponse.status()}`);
    const saveJson = await saveResponse.json().catch(() => null);
    assert(saveJson && !(saveJson.errors && saveJson.errors.length), `measurement save reported errors: ${JSON.stringify(saveJson)}`);
    await page.waitForFunction(() => window.__closed === true, null, { timeout: 15000 });

    const rows = newMeasurementRows();
    const byType = Object.fromEntries(rows.map((row) => [row.type, row]));
    const today = sql('SELECT CURDATE()');
    assert(byType.HT && byType.HT.dataField === '177.8' && byType.HT.dateObserved === today, `HT row was ${JSON.stringify(byType.HT)}`);
    assert(byType.WT && byType.WT.dataField === '68' && byType.WT.dateObserved === today, `WT row was ${JSON.stringify(byType.WT)}`);
    assert(byType.BMI && byType.BMI.dataField === '21.5', `BMI row was ${JSON.stringify(byType.BMI)}`);
    assert(rows.length === 3, `expected exactly HT, WT and BMI rows, got ${JSON.stringify(rows)}`);

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`PASS measurements: 5'10"/150lb converted to 177.8cm/68kg, BMI 21.5 computed and saved for demographic ${demographicNo}`);
  } catch (error) {
    console.error(`FAIL eChart vitals/BMI check: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close().catch(() => {});
    try { cleanupRows(); } catch (cleanupError) { console.error(`cleanup failed: ${cleanupError.message}`); }
    cleanupMysqlDefaults();
  }
})();
