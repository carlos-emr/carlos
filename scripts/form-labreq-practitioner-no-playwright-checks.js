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
 * Browser check for the Lab Requisition 2007 practitioner number (issue #3724).
 *
 * The form rebuilds practitionerNo from the provider record on every render and
 * discards whatever the stored row held. When the provider has no ohip_no the
 * old code emitted "0000--00", and that double hyphen is a SQL line comment, so
 * the packaged front door's libinjection rule (OWASP CRS 942100) scored the
 * posted form body as an injection attempt and answered 403 before the request
 * reached Tomcat. The requisition could then never be saved again.
 *
 *   1. opens the patient's seeded requisition and asserts the rendered
 *      practitioner number carries no empty segment, even though the shipped
 *      demo row still stores "0000--00";
 *   2. asserts the rendered value is exactly what the provider record supports:
 *      empty when there is no ohip_no, "0000-<ohip_no>-<specialty>" when there
 *      is. This is what keeps the fix from degenerating into "blank it always";
 *   3. opens a new requisition and saves it, asserting the POST is not rejected.
 *      Against a deployment that fronts Tomcat with the CRS rules this is the
 *      step that used to return 403; against a bare Tomcat it still pins that
 *      the saved row carries no double hyphen.
 *
 * The seeded requisition is only read. The row created by step 3 is deleted in
 * a finally, including on SIGINT/SIGTERM.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: LABREQ_DEMOGRAPHIC_NO (1), LABREQ_PROVIDER_NO (999998).
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
const demographicNo = process.env.LABREQ_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.LABREQ_PROVIDER_NO || '999998';
assert(/^\d+$/.test(demographicNo) && /^\d+$/.test(providerNo),
  'LABREQ_DEMOGRAPHIC_NO and LABREQ_PROVIDER_NO must be numeric');

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'labreq-practitioner-'));
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
  // Only the trailing newline is stripped. A blanket trim() would eat the leading
  // tab of a row whose first column is empty, and an empty ohip_no -- the exact
  // condition this check exists for -- is such a row.
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).replace(/\r?\n$/, '');
}
function sqlRows(query) {
  const out = sql(query);
  return out ? out.split('\n').map((line) => line.split('\t')) : [];
}

/*
 * The server-side rule, restated here so the check fails when the two drift
 * apart rather than agreeing with whatever the page happens to render.
 * Mirrors PractitionerNumber.ohipRequisition and the "00" default that
 * FrmLabReq07Record.extractSpecialtyCode falls back to.
 */
function expectedPractitionerNo(ohipNo, comments) {
  const billing = (ohipNo || '').trim();
  if (!billing) {
    return '';
  }
  const match = /<xml_p_specialty_code>([\s\S]*?)<\/xml_p_specialty_code>/.exec(comments || '');
  const specialty = (match ? match[1] : '').trim() || '00';
  return `0000-${billing}-${specialty}`;
}

let formHighWater = 0;
function newFormIds() {
  return sqlRows(`SELECT ID FROM formLabReq07 WHERE demographic_no=${Number(demographicNo)} AND ID > ${formHighWater} ORDER BY ID`)
    .map(([id]) => id);
}
function cleanupRows() {
  for (const id of newFormIds()) {
    sql(`DELETE FROM formLabReq07 WHERE ID=${Number(id)}`);
  }
}

async function openForm(context, recorder, label, appPath) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    window.__confirms = [];
    window.confirm = (message) => { window.__confirms.push(String(message)); return true; };
    window.close = () => { window.__closed = true; };
  });
  wirePage(page, label, recorder);
  await gotoApp(page, config.baseUrl, appPath);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, label);
  await page.locator('input[name="practitionerNo"]').first().waitFor({ state: 'attached', timeout: 30000 });
  return page;
}

function practitionerNoOf(page) {
  return page.locator('input[name="practitionerNo"]').first().inputValue();
}

let browser = null;
let cleanupDone = false;
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
  try {
    const tables = Number(sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='formLabReq07'"));
    if (!tables) {
      console.log('SKIP formLabReq07 is an Ontario-only table and this database does not have it');
      return;
    }
    const provider = sqlRows(`SELECT IFNULL(ohip_no,''), IFNULL(comments,'') FROM provider WHERE provider_no='${Number(providerNo)}'`)[0];
    assert(provider, `provider ${providerNo} not found`);
    const expected = expectedPractitionerNo(provider[0], provider[1]);
    assert(!expected.includes('--'), `the expectation itself carries an empty segment: "${expected}"`);

    formHighWater = Number(sql(`SELECT IFNULL(MAX(ID), 0) FROM formLabReq07 WHERE demographic_no=${Number(demographicNo)}`));
    const seeded = sqlRows(`SELECT ID, IFNULL(practitionerNo,'') FROM formLabReq07 WHERE demographic_no=${Number(demographicNo)} ORDER BY ID LIMIT 1`)[0];

    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1500, height: 1100 } });
    await login(context, config, recorder);

    // 1 & 2. An existing requisition renders a usable number, not the stored skeleton.
    if (seeded) {
      const stored = await openForm(context, recorder, 'labreq-existing',
        `/form/formlabreq07?demographic_no=${encodeURIComponent(demographicNo)}&formId=${encodeURIComponent(seeded[0])}&provNo=${encodeURIComponent(providerNo)}`);
      const rendered = await practitionerNoOf(stored);
      assert(!rendered.includes('--'),
        `requisition ${seeded[0]} rendered practitionerNo "${rendered}"; a double hyphen is what CRS 942100 rejects`);
      assert(rendered === expected,
        `requisition ${seeded[0]} rendered practitionerNo "${rendered}", expected "${expected}" from the provider record`);
      await stored.close();
      console.log(`PASS requisition ${seeded[0]} renders "${rendered}" (row stores "${seeded[1]}")`);
    } else {
      console.log('SKIP no seeded requisition for this patient; only the new-form path is checked');
    }

    // 3. A new requisition renders the same value and saves without being rejected.
    const page = await openForm(context, recorder, 'labreq-new',
      `/form/formlabreq07?demographic_no=${encodeURIComponent(demographicNo)}&formId=0&provNo=${encodeURIComponent(providerNo)}`);
    const fresh = await practitionerNoOf(page);
    assert(fresh === expected, `a new requisition rendered practitionerNo "${fresh}", expected "${expected}"`);

    const [saveResponse] = await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST'
        && /\/form\/formname/.test(response.url()), { timeout: 30000 }),
      page.locator('input[type="submit"][value="Save"], input[type="submit"][value="Save & Exit"]').first().click(),
    ]);
    assert(saveResponse.status() < 400,
      `saving the requisition returned HTTP ${saveResponse.status()}; 403 here is the front door rejecting the posted body`);

    const created = newFormIds();
    assert(created.length >= 1, 'Save did not create a formLabReq07 row');
    const savedNumbers = sqlRows(`SELECT ID, IFNULL(practitionerNo,'') FROM formLabReq07 WHERE ID IN (${created.map(Number).join(',')})`);
    for (const [id, value] of savedNumbers) {
      assert(!value.includes('--'), `saved requisition ${id} stored practitionerNo "${value}"`);
      assert(value === expected, `saved requisition ${id} stored practitionerNo "${value}", expected "${expected}"`);
    }
    await page.close();

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`PASS lab requisition saved (ID ${created.join(', ')}) with practitionerNo "${expected || '(empty)'}"`);
  } catch (error) {
    console.error(`FAIL lab requisition practitioner number check: ${error.stack || error.message}`);
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
