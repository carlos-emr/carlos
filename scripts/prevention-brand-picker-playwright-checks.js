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
 * Browser check for recording a prevention through the brand picker on the
 * patient's Preventions page, which alpha-11 testers reported working.
 *
 *   1. opens the Preventions page for the patient and types into the
 *      "Pick vaccine brand/generic" box (#immunization), which is fed by
 *      /prevention/vaccine-brands.json;
 *   2. picks the first matching brand: the Add Prevention popup must open for
 *      that brand's prevention type with brand name, DIN, dose, unit, route
 *      and manufacturer pre-filled from the picker entry;
 *   3. enters a lot number and submits ("given"); the popup closes and the
 *      preventions row plus its preventionsExt name/din/lot/dose/route/
 *      manufacture values exist for the patient;
 *   4. the Preventions page lists the new entry with today's date.
 *
 * Rows the check created are deleted in a finally.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: PREVENTION_DEMOGRAPHIC_NO (1), PREVENTION_BRAND_QUERY (Tdap).
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
const demographicNo = process.env.PREVENTION_DEMOGRAPHIC_NO || '1';
const brandQuery = process.env.PREVENTION_BRAND_QUERY || 'Tdap';
assert(/^\d+$/.test(demographicNo), 'PREVENTION_DEMOGRAPHIC_NO must be numeric');
const lotNumber = `PWLOT${String(Date.now()).slice(-8)}`;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'prevention-brand-'));
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

function preventionIds() {
  return sqlRows(`SELECT p.id FROM preventions p JOIN preventionsExt e ON e.prevention_id=p.id WHERE p.demographic_no=${Number(demographicNo)} AND e.keyval='lot' AND e.val='${escapeSql(lotNumber)}'`).map((row) => row[0]);
}
function cleanupRows() {
  for (const id of preventionIds()) {
    sql(`DELETE FROM preventionsExt WHERE prevention_id=${Number(id)}`);
    sql(`DELETE FROM preventions WHERE id=${Number(id)}`);
  }
}

(async () => {
  const recorder = createRecorder();
  initMysqlDefaults();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1400, height: 1100 } });
    await login(context, config, recorder);

    // The picker's data source, fetched the way the page fetches it.
    const brandsResponse = await context.request.get(`${config.baseUrl.href}/prevention/vaccine-brands.json`);
    assert(brandsResponse.ok(), `vaccine-brands.json returned HTTP ${brandsResponse.status()}`);
    const brands = await brandsResponse.json();
    const expected = brands.find((entry) => (entry.value || '').toLowerCase().includes(brandQuery.toLowerCase())
      || (entry.name || '').toLowerCase().includes(brandQuery.toLowerCase()));
    assert(expected, `no vaccine brand matches "${brandQuery}"`);

    // 1-2. Preventions page -> brand picker -> Add Prevention popup.
    const index = await context.newPage();
    wirePage(index, 'preventions', recorder);
    await gotoApp(index, config.baseUrl, `/prevention/ViewPreventionIndex?demographic_no=${encodeURIComponent(demographicNo)}`);
    await index.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(index, 'preventions page');
    await index.locator('#immunization').click();
    await index.keyboard.type(brandQuery, { delay: 30 });
    const choice = index.locator('#immunization_choices [class*="item"], #immunization_choices div, #immunization_choices li').first();
    await choice.waitFor({ state: 'visible', timeout: 15000 });
    const choiceText = (await choice.innerText()).trim();
    assert(choiceText.includes(expected.name) && choiceText.includes(expected.value.slice(0, 20)),
      `first picker entry "${choiceText}" did not match the expected brand ${expected.name} / ${expected.value}`);
    const popupPromise = context.waitForEvent('page', { timeout: 30000 });
    await choice.click();
    const popup = await popupPromise;
    wirePage(popup, 'add-prevention', recorder);
    await popup.waitForLoadState('domcontentloaded', { timeout: 30000 });
    await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(popup, 'add prevention popup');
    const popupUrl = new URL(popup.url());
    assert(popupUrl.searchParams.get('prevention') === expected.name, `popup opened for prevention ${popupUrl.searchParams.get('prevention')}, expected ${expected.name}`);
    assert((await popup.locator('input[name="prevention"]').inputValue()) === expected.name, 'popup form is not for the picked prevention type');
    assert((await popup.locator('#name').inputValue()) === expected.value, 'brand name was not pre-filled');
    assert((await popup.locator('#din').inputValue()) === (expected.din || ''), 'DIN was not pre-filled');
    assert((await popup.locator('#dose').inputValue()) === (expected.dose || ''), 'dose was not pre-filled');
    assert((await popup.locator('select[name="doseUnit"]').inputValue()) === (expected.units || ''), 'dose unit was not pre-filled');
    assert((await popup.locator('#route').inputValue()) === (expected.route || ''), 'route was not pre-filled');
    assert((await popup.locator('#manufacture').inputValue()) === (expected.manufacture || ''), 'manufacturer was not pre-filled');

    // 3. Lot + submit.
    await popup.locator('input[name="given"][value="given"]').check();
    await popup.locator('#lot').fill(lotNumber);
    const prevDate = await popup.locator('#prevDate').inputValue();
    assert(/^\d{4}-\d{2}-\d{2}$/.test(prevDate), `prevention date defaulted to "${prevDate}"`);
    const [saveResponse] = await Promise.all([
      popup.waitForResponse((response) => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/prevention/AddPrevention'), { timeout: 30000 }),
      popup.locator('form[action$="/prevention/AddPrevention"] input[type="submit"], form[action$="/prevention/AddPrevention"] button[type="submit"]').first().click(),
    ]);
    assert(saveResponse.status() < 400, `prevention save returned HTTP ${saveResponse.status()}`);
    await popup.waitForEvent('close', { timeout: 30000 }).catch(() => {});

    const ids = preventionIds();
    assert(ids.length === 1, `expected one prevention row with lot ${lotNumber}, found ${ids.length}`);
    const [type, date, providerNo, refused] = sql(`SELECT prevention_type, prevention_date, provider_no, refused FROM preventions WHERE id=${Number(ids[0])}`).split('\t');
    assert(type === expected.name, `saved prevention type ${type}, expected ${expected.name}`);
    assert(date.startsWith(prevDate), `saved prevention date ${date}, expected ${prevDate}`);
    assert(refused === '0', `saved prevention refused flag was ${refused}`);
    assert(providerNo && providerNo !== '', 'saved prevention has no provider');
    const ext = Object.fromEntries(sqlRows(`SELECT keyval, val FROM preventionsExt WHERE prevention_id=${Number(ids[0])}`));
    assert(ext.name === expected.value, `ext name was "${ext.name}"`);
    assert(ext.din === (expected.din || ''), `ext din was "${ext.din}"`);
    assert(ext.lot === lotNumber, `ext lot was "${ext.lot}"`);
    assert(ext.route === (expected.route || ''), `ext route was "${ext.route}"`);
    assert(ext.manufacture === (expected.manufacture || ''), `ext manufacture was "${ext.manufacture}"`);
    assert((ext.dose || '').startsWith(expected.dose || ''), `ext dose was "${ext.dose}"`);

    // 4. Listed on the Preventions page. The popup reloads its opener on the
    // way out, so let that settle and then navigate the page fresh.
    await index.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
    await index.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await gotoApp(index, config.baseUrl, `/prevention/ViewPreventionIndex?demographic_no=${encodeURIComponent(demographicNo)}`);
    await index.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const indexText = await index.locator('body').innerText();
    assert(indexText.includes(prevDate), 'Preventions page did not list the new prevention date');

    // The page first tries an admin override of the brand list through
    // displayImage and falls back to the shipped JSON on 404; that probe is
    // the designed path, not a broken asset.
    const isBrandOverrideProbe = (entry) => /displayImage\?imagefile=vaccine-brands\.json/.test(entry.url || (entry.location && entry.location.url) || '');
    const badResponses = recorder.badResponses.filter((entry) => !(entry.status === 404 && isBrandOverrideProbe(entry)));
    const consoleIssues = recorder.consoleIssues.filter((entry) => !isBrandOverrideProbe(entry));
    assertNoPageErrors(recorder);
    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
    assert(consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(consoleIssues, null, 2)}`);
    console.log(`PASS prevention ${expected.name} (${expected.value}) recorded through the brand picker with lot ${lotNumber} for demographic ${demographicNo}`);
  } catch (error) {
    console.error(`FAIL prevention brand picker check: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close().catch(() => {});
    try { cleanupRows(); } catch (cleanupError) { console.error(`cleanup failed: ${cleanupError.message}`); }
    cleanupMysqlDefaults();
  }
})();
