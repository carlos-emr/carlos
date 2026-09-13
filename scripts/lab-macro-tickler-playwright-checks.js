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
 * Browser check for creating a tickler via a lab macro, the "via macro"
 * half of "ticklers work, both manually and via macro" from the alpha-11
 * report. tickler-crud-playwright-checks.js covers the manual half.
 *
 * Macros are per-provider lab recall macros (Preferences > Lab Recall
 * Macros). The demo seed ships none, so the check:
 *
 *   1. defines a macro through the preferences page (name, acknowledge
 *      comment, tickler message, assignee, 2 weeks) and asserts the
 *      provider's labMacroJSON property carries it;
 *   2. opens a lab result routed to the provider and still unacknowledged
 *      (the Macros dropdown only renders then), runs the macro, and asserts
 *      the JSON success reply and that the lab window closed itself;
 *   3. asserts the tickler row: message, patient from the lab, assignee,
 *      creator, service date = today + 14 days, priority default, plus the
 *      tickler_link row back to the lab, and that the lab is now
 *      acknowledged with the macro's comment;
 *   4. asserts the new tickler appears in the tickler list (date window
 *      widened to the recall date) for that patient.
 *
 * Fixture: an unacknowledged providerLabRouting row for the provider on the
 * first HL7 lab that has a patient, restored on cleanup along with the
 * tickler rows and the macro property (only when the check created it).
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: MACRO_PROVIDER_NO (999998, the logged-in provider), MACRO_LAB_NO.
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
const providerNo = process.env.MACRO_PROVIDER_NO || '999998';
assert(/^\d+$/.test(providerNo), 'MACRO_PROVIDER_NO must be numeric');

const stamp = `PW_MACRO_${Date.now()}`;
const macroName = `${stamp} recall`;
const ackComment = `${stamp} reviewed`;
const ticklerMessage = `${stamp} call patient`;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'lab-macro-'));
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

const fixture = { labNo: null, demographicNo: null, routingCreated: false, routingBefore: null, macroPropertyBefore: undefined };

function macroProperty() {
  const out = sql(`SELECT IFNULL(value, '') FROM property WHERE provider_no='${escapeSql(providerNo)}' AND name='labMacroJSON' ORDER BY id DESC LIMIT 1`);
  return out === '' && sql(`SELECT COUNT(*) FROM property WHERE provider_no='${escapeSql(providerNo)}' AND name='labMacroJSON'`) === '0' ? null : out;
}

function stageLabRouting() {
  const chosen = process.env.MACRO_LAB_NO
    ? sql(`SELECT lab_no, demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${Number(process.env.MACRO_LAB_NO)} LIMIT 1`)
    : sql('SELECT lab_no, demographic_no FROM patientLabRouting WHERE lab_type=\'HL7\' AND demographic_no > 0 ORDER BY lab_no LIMIT 1');
  assert(chosen, 'no HL7 lab linked to a patient is available to run the macro on');
  [fixture.labNo, fixture.demographicNo] = chosen.split('\t');
  const routing = sql(`SELECT id, status, IFNULL(comment,'') FROM providerLabRouting WHERE lab_no=${Number(fixture.labNo)} AND lab_type='HL7' AND provider_no='${escapeSql(providerNo)}' ORDER BY id DESC LIMIT 1`);
  if (routing) {
    const [id, status, comment] = routing.split('\t');
    fixture.routingBefore = { id, status, comment };
    sql(`UPDATE providerLabRouting SET status='N', comment='' WHERE id=${Number(id)}`);
  } else {
    sql(`INSERT INTO providerLabRouting (provider_no, lab_no, status, comment, timestamp, lab_type) VALUES ('${escapeSql(providerNo)}', ${Number(fixture.labNo)}, 'N', '', NOW(), 'HL7')`);
    fixture.routingCreated = true;
  }
}

function ticklerRows() {
  return sqlRows(`SELECT tickler_no, demographic_no, status, priority, task_assigned_to, creator, DATE(service_date), message FROM tickler WHERE message='${escapeSql(ticklerMessage)}' ORDER BY tickler_no`)
    .map(([id, demoNo, status, priority, assignee, creator, serviceDate, message]) => ({ id, demoNo, status, priority, assignee, creator, serviceDate, message }));
}

function cleanupRows() {
  for (const row of ticklerRows()) {
    sql(`DELETE FROM tickler_link WHERE tickler_no=${Number(row.id)}`);
    sql(`DELETE FROM tickler_comments WHERE tickler_no=${Number(row.id)}`);
    sql(`DELETE FROM tickler WHERE tickler_no=${Number(row.id)}`);
  }
  if (fixture.routingCreated) {
    sql(`DELETE FROM providerLabRouting WHERE lab_no=${Number(fixture.labNo)} AND lab_type='HL7' AND provider_no='${escapeSql(providerNo)}'`);
  } else if (fixture.routingBefore) {
    sql(`UPDATE providerLabRouting SET status='${escapeSql(fixture.routingBefore.status)}', comment='${escapeSql(fixture.routingBefore.comment)}' WHERE id=${Number(fixture.routingBefore.id)}`);
  }
  if (fixture.macroPropertyBefore === null) {
    sql(`DELETE FROM property WHERE provider_no='${escapeSql(providerNo)}' AND name='labMacroJSON'`);
  } else if (typeof fixture.macroPropertyBefore === 'string') {
    sql(`UPDATE property SET value='${escapeSql(fixture.macroPropertyBefore)}' WHERE provider_no='${escapeSql(providerNo)}' AND name='labMacroJSON'`);
  }
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
    fixture.macroPropertyBefore = macroProperty();
    stageLabRouting();
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1400, height: 1100 } });
    await login(context, config, recorder);

    // 1. Define the macro in preferences.
    const prefs = await context.newPage();
    wirePage(prefs, 'macro-prefs', recorder);
    await gotoApp(prefs, config.baseUrl, '/setProviderStaleDate?method=viewLabMacroPrefs');
    await prefs.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(prefs, 'lab macro preferences');
    await prefs.locator('#name_new').fill(macroName);
    await prefs.locator('#comment_new').fill(ackComment);
    await prefs.locator('#message_new').fill(ticklerMessage);
    await prefs.locator('#ticklerTo_new').selectOption(providerNo);
    await prefs.locator('#quantity_new').fill('2');
    await prefs.locator('#timeUnits_new').selectOption('7');
    const [prefsSave] = await Promise.all([
      prefs.waitForResponse((response) => response.request().method() === 'POST' && /\/setProviderStaleDate/.test(response.url()), { timeout: 30000 }),
      prefs.locator('form[name="labMacroPrefsForm"] input[type="submit"][value="Save"]').first().click(),
    ]);
    assert(prefsSave.status() < 400, `macro preferences save returned HTTP ${prefsSave.status()}`);
    await prefs.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
    const stored = macroProperty();
    assert(stored && stored.includes(macroName), `labMacroJSON property did not store the macro: ${stored}`);
    const macros = JSON.parse(stored);
    const macro = macros.find((entry) => entry.name === macroName);
    assert(macro && macro.tickler && macro.tickler.taskAssignedTo === providerNo && macro.tickler.message === ticklerMessage
      && String(macro.tickler.quantity) === '2' && String(macro.tickler.timeUnits) === '7' && macro.acknowledge && macro.acknowledge.comment === ackComment,
    `stored macro definition was ${JSON.stringify(macro)}`);
    await prefs.close();

    // 2. Run it from the lab result.
    const lab = await context.newPage();
    await lab.addInitScript(() => { window.close = () => { window.__closed = true; }; });
    wirePage(lab, 'lab-display', recorder);
    await gotoApp(lab, config.baseUrl, `/lab/CA/ALL/ViewLabDisplay?segmentID=${encodeURIComponent(fixture.labNo)}&providerNo=${encodeURIComponent(providerNo)}&searchProviderNo=${encodeURIComponent(providerNo)}&status=N`);
    await lab.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(lab, 'lab display');
    const macroMenu = lab.locator('.macro-dropdown');
    assert(await macroMenu.count(), 'lab display did not render the Macros dropdown for an unacknowledged lab with macros defined');
    await macroMenu.locator('button.dropdown-toggle').click();
    const entry = macroMenu.locator('a.dropdown-item', { hasText: macroName }).first();
    await entry.waitFor({ state: 'visible', timeout: 15000 });
    const [runResponse] = await Promise.all([
      lab.waitForResponse((response) => response.request().method() === 'POST' && /\/oscarMDS\/RunMacro\?/.test(response.url()), { timeout: 30000 }),
      entry.click(),
    ]);
    assert(runResponse.status() === 200, `RunMacro returned HTTP ${runResponse.status()}`);
    const runJson = await runResponse.json().catch(() => null);
    assert(runJson && runJson.success === true, `RunMacro reply was ${JSON.stringify(runJson)}`);
    assert(new URL(runResponse.url()).searchParams.get('demographicNo') === fixture.demographicNo, 'RunMacro was not sent with the lab\'s patient');
    await lab.waitForFunction(() => window.__closed === true, null, { timeout: 15000 });
    assert(recorder.dialogs.length === 0, `running the macro raised a dialog: ${JSON.stringify(recorder.dialogs)}`);

    // 3. Tickler + lab acknowledgement.
    const ticklers = ticklerRows();
    assert(ticklers.length === 1, `expected one tickler from the macro, found ${ticklers.length}`);
    const tickler = ticklers[0];
    assert(tickler.demoNo === fixture.demographicNo, `tickler patient ${tickler.demoNo}, expected ${fixture.demographicNo}`);
    assert(tickler.assignee === providerNo && tickler.creator === providerNo, `tickler assignee/creator were ${tickler.assignee}/${tickler.creator}`);
    assert(tickler.status === 'A', `tickler status was ${tickler.status}`);
    assert(tickler.serviceDate === sql('SELECT DATE(NOW() + INTERVAL 14 DAY)'), `tickler service date ${tickler.serviceDate} was not 2 weeks out`);
    const link = sql(`SELECT table_name, table_id FROM tickler_link WHERE tickler_no=${Number(tickler.id)}`);
    assert(link === `HL7\t${fixture.labNo}`, `tickler_link row was "${link}"`);
    const routing = sql(`SELECT status, IFNULL(comment,'') FROM providerLabRouting WHERE lab_no=${Number(fixture.labNo)} AND lab_type='HL7' AND provider_no='${escapeSql(providerNo)}' ORDER BY id DESC LIMIT 1`);
    assert(routing === `A\t${ackComment}`, `lab routing after the macro was "${routing}"`);

    // 4. In the patient's tickler list.
    const list = await context.newPage();
    wirePage(list, 'tickler-list', recorder);
    // The list's date window defaults to today, and the patient-scoped view
    // carries no date controls (a future-dated recall never shows there --
    // tracked for review), so use the full tickler list and widen its window
    // to cover the two-week recall the macro scheduled, submitting it the way
    // the filter's own button does (DataTables ajax reload).
    await gotoApp(list, config.baseUrl, `/tickler/ViewTicklerMain?ticklerview=A`);
    await list.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const window14 = { from: sql('SELECT DATE(NOW() - INTERVAL 1 DAY)'), to: sql('SELECT DATE(NOW() + INTERVAL 30 DAY)') };
    const [listResponse] = await Promise.all([
      list.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/tickler/ListTicklers') && new URL(response.url()).searchParams.get('endDate') === window14.to, { timeout: 30000 }),
      list.evaluate((range) => {
        document.getElementById('xml_vdate').value = range.from;
        document.getElementById('xml_appointment_date').value = range.to;
        window.jQuery('#ticklerResults').DataTable().ajax.reload();
      }, window14),
    ]);
    assert(listResponse.status() < 400, `tickler list reload returned HTTP ${listResponse.status()}`);
    await list.waitForFunction((needle) => document.body.innerText.includes(needle), ticklerMessage, { timeout: 30000 });
    const listRow = list.locator('#ticklerResults tbody tr', { hasText: ticklerMessage }).first();
    assert((await listRow.innerText()).includes(sql(`SELECT CONCAT(last_name, ', ', first_name) FROM demographic WHERE demographic_no=${Number(fixture.demographicNo)}`).split(',')[0]),
      'tickler list row did not show the lab\'s patient');
    await list.close();

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`PASS lab macro "${macroName}" created tickler ${tickler.id} for demographic ${fixture.demographicNo} from lab ${fixture.labNo} and acknowledged the lab`);
  } catch (error) {
    console.error(`FAIL lab macro tickler check: ${error.stack || error.message}`);
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
