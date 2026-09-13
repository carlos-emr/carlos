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
 * Browser check for the eChart encounter note verbs alpha-11 testers reported
 * working: Save, Sign & Save, Sign Save & Bill, plus the encounter timer.
 *
 * The other eChart checks only save CPP items or assert the chart renders;
 * none of them writes a casemgmt_note through the main editor. This one opens
 * the chart from the appointment's encounter ("E") link on the day sheet and:
 *
 *   1. timer: waits for #aTimer to start counting, pauses it with #toggleTimer
 *      and asserts it stops, then clicks the timer to paste "Start Time /
 *      End Time / elapsed" into the note;
 *   2. Save (#saveImg -> saveNoteAjax, method=save): asserts the casemgmt_note row exists
 *      unsigned with the typed text and the pasted timer lines;
 *   3. Sign & Save (#signSaveImg -> savePage('saveAndExit')): asserts the
 *      note is now signed by the logged-in provider and the chart window
 *      closed itself;
 *   4. Sign Save & Bill (input[title="Sign Save & Bill"]) on a new note:
 *      asserts the note is signed and that the chart hands off to the
 *      Ontario bill form for the appointment (/billing?...bNewForm=1), which
 *      renders.
 *
 * Fixture: one appointment for NOTE_DEMOGRAPHIC_NO with NOTE_PROVIDER_NO on
 * today's day sheet; the appointment, the notes it created, and their locks
 * are deleted in a finally.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: NOTE_DEMOGRAPHIC_NO (1), NOTE_PROVIDER_NO (999998, must be the
 *   logged-in provider so the day sheet shows the appointment).
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
const demographicNo = process.env.NOTE_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.NOTE_PROVIDER_NO || '999998';
assert(/^\d+$/.test(demographicNo) && /^\d+$/.test(providerNo), 'NOTE_DEMOGRAPHIC_NO and NOTE_PROVIDER_NO must be numeric');

const stamp = `PW_NOTE_${Date.now()}`;
const savedText = `${stamp} saved note`;
const billedText = `${stamp} billed note`;
let browserSessionId = null;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'echart-note-'));
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

let appointmentNo = null;
let appointmentDate = null;
const startTime = '11:30:00';

function createAppointment() {
  appointmentDate = sql('SELECT CURDATE()');
  sql(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no, program_id, notes, reason, location, resources, type, style, billing, status, createdatetime, updatedatetime, creator, remarks, urgency)`
    + ` SELECT '${escapeSql(providerNo)}', CURDATE(), '${startTime}', ADDTIME('${startTime}', '00:14:00'), CONCAT(last_name, ',', first_name), ${Number(demographicNo)}, 0, '${escapeSql(stamp)}', '${escapeSql(stamp)}', '', '', '', '', '', 't', NOW(), NOW(), '${escapeSql(providerNo)}', '', ''`
    + ` FROM demographic WHERE demographic_no=${Number(demographicNo)}`);
  appointmentNo = sql(`SELECT appointment_no FROM appointment WHERE notes='${escapeSql(stamp)}' ORDER BY appointment_no DESC LIMIT 1`);
  assert(/^\d+$/.test(appointmentNo), 'could not create the fixture appointment');
}

function noteRows() {
  return sqlRows(`SELECT note_id, signed, signing_provider_no, provider_no, appointmentNo, note FROM casemgmt_note WHERE demographic_no='${escapeSql(demographicNo)}' AND note LIKE '%${escapeSql(stamp)}%' ORDER BY note_id`)
    .map(([noteId, signed, signingProviderNo, provider, appt, note]) => ({ noteId, signed, signingProviderNo, provider, appt, note }));
}
function latestNoteWith(text) {
  const rows = noteRows().filter((row) => row.note.includes(text));
  return rows.length ? rows[rows.length - 1] : null;
}
function cleanupRows() {
  const ids = noteRows().map((row) => row.noteId);
  for (const id of ids) {
    sql(`DELETE FROM casemgmt_issue_notes WHERE note_id=${Number(id)}`);
    sql(`DELETE FROM casemgmt_note_ext WHERE note_id=${Number(id)}`);
    sql(`DELETE FROM casemgmt_note WHERE note_id=${Number(id)}`);
  }
  if (browserSessionId) {
    sql(`DELETE FROM casemgmt_note_lock WHERE demographic_no=${Number(demographicNo)} AND provider_no='${escapeSql(providerNo)}' AND session_id='${escapeSql(browserSessionId)}'`);
  }
  sql(`DELETE FROM casemgmt_tmpsave WHERE demographic_no=${Number(demographicNo)} AND provider_no='${escapeSql(providerNo)}' AND note LIKE '%${escapeSql(stamp)}%'`);
  sql(`DELETE FROM appointment WHERE notes='${escapeSql(stamp)}'`);
}

async function openDaySheet(context, recorder) {
  const page = await context.newPage();
  wirePage(page, 'day-sheet', recorder);
  const [year, month, day] = appointmentDate.split('-').map(Number);
  // A provider filter must retain the day view and its encounter/billing links.
  await gotoApp(page, config.baseUrl, `/provider/providercontrol?year=${year}&month=${month}&day=${day}&view=0&displaymode=day&dboperation=searchappointmentday&viewall=0&provider_no=${encodeURIComponent(providerNo)}`);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, 'day sheet');
  return page;
}

async function openChartFromAppointment(context, daySheet, recorder, label) {
  const encounterLink = daySheet.locator(`a.encounterBtn[onclick*=",${appointmentNo});"]`).first();
  await encounterLink.waitFor({ state: 'visible', timeout: 30000 });
  const popupPromise = context.waitForEvent('page', { timeout: 30000 });
  await encounterLink.click();
  const chart = await popupPromise;
  wirePage(chart, label, recorder);
  await chart.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await chart.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(chart, label);
  await chart.locator('textarea[name="caseNote_note"]').first().waitFor({ state: 'visible', timeout: 30000 });
  assert((await chart.locator('input[name="appointmentNo"]').first().inputValue()) === appointmentNo,
    `${label} did not open against appointment ${appointmentNo}`);
  return chart;
}

async function typeIntoActiveNote(chart, text) {
  const note = chart.locator('textarea[name="caseNote_note"]').first();
  await note.click();
  await note.fill(text);
}

async function pasteTimer(chart) {
  // Exercise the active chart timer and the clinical prose it inserts.
  const timer = chart.locator('#aTimer').first();
  const toggle = chart.locator('#toggleTimer').first();
  // The timer text only starts changing after five seconds of elapsed time.
  await chart.waitForFunction(() => /^00:0[6-9]|^00:[1-5]\d/.test(document.getElementById('aTimer').textContent.trim()), null, { timeout: 30000 });
  assert(await timer.isVisible(), 'the timer the page updates is not the visible one');
  await toggle.click();
  const paused = (await timer.innerText()).trim();
  await chart.waitForTimeout(2500);
  assert((await timer.innerText()).trim() === paused, 'pausing the timer did not stop it counting');
  await toggle.click();
  await chart.waitForFunction((before) => document.getElementById('aTimer').textContent.trim() !== before, paused, { timeout: 15000 });
  await timer.click();
  const noteValue = await chart.locator('textarea[name="caseNote_note"]').first().inputValue();
  assert(/Start Time: \d{1,2}:\d{2}/.test(noteValue) && /End Time: \d{1,2}:\d{2}/.test(noteValue) && /\d{2}:\d{2}:\d{2}/.test(noteValue),
    `pasting the timer did not add the timing lines to the note: ${noteValue.slice(-120)}`);
}

// Exercise the pointer path: a dispatched event would hide unreachable controls.
async function pressChartButton(chart, locator) {
  await locator.first().click({ timeout: 15000 });
}

async function clickAndExpectSave(chart, selector, expectedMethod) {
  const [response] = await Promise.all([
    chart.waitForResponse((r) => r.request().method() === 'POST' && /\/CaseManagementEntry/.test(r.url())
      && new URLSearchParams(r.request().postData() || '').get('method') === expectedMethod, { timeout: 30000 }),
    pressChartButton(chart, chart.locator(selector)),
  ]);
  assert(response.status() < 400, `${expectedMethod} returned HTTP ${response.status()}`);
  return response;
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
    createAppointment();
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1600, height: 1100 } });
    await login(context, config, recorder);
    browserSessionId = (await context.cookies()).find((cookie) => cookie.name === 'JSESSIONID')?.value || null;
    assert(browserSessionId, 'authenticated browser session cookie is missing');
    const daySheet = await openDaySheet(context, recorder);

    // 1-3. Timer, Save, Sign & Save.
    const chart = await openChartFromAppointment(context, daySheet, recorder, 'echart-save');
    for (const viewport of [{width: 1366, height: 768}, {width: 1920, height: 1400}, {width: 1600, height: 1100}]) {
      await chart.setViewportSize(viewport);
      const saveButton = chart.locator('#saveImg').first();
      await saveButton.scrollIntoViewIfNeeded({timeout: 10000});
      const bounds = await saveButton.boundingBox();
      assert(bounds && bounds.y >= 0 && bounds.y + bounds.height <= viewport.height,
        `Save button cannot be reached at ${viewport.width}x${viewport.height}: ${JSON.stringify(bounds)}`);
    }
    await typeIntoActiveNote(chart, savedText);
    await pasteTimer(chart);
    await clickAndExpectSave(chart, '#saveImg', 'save');
    let saved = null;
    for (let attempt = 0; attempt < 20 && !saved; attempt += 1) {
      saved = latestNoteWith(savedText);
      if (!saved) {
        await chart.waitForTimeout(500);
      }
    }
    assert(saved, 'Save did not create the casemgmt_note row');
    // The ajax save re-renders the note list and only then binds the saved
    // note id into the form; Sign & Save must act on that note, not a new one.
    await chart.waitForFunction((noteId) => {
      const field = document.querySelector('input[name="noteId"]');
      return field && field.value === noteId;
    }, saved.noteId, { timeout: 30000 });
    assert(saved.signed === '0', `saved note was already signed (${saved.signed})`);
    assert(/Start Time:/.test(saved.note) && /End Time:/.test(saved.note), 'saved note did not keep the pasted timer lines');
    assert(saved.appt === appointmentNo,
      `saved note ${saved.noteId} carries appointmentNo ${saved.appt}, expected ${appointmentNo}`);

    const closed = chart.waitForEvent('close', { timeout: 30000 }).then(() => true).catch(() => false);
    await clickAndExpectSave(chart, '#signSaveImg', 'saveAndExit');
    const didClose = await closed;
    assert(didClose, 'Sign & Save did not close the chart window');
    const signed = latestNoteWith(savedText);
    assert(signed && signed.signed === '1', `Sign & Save did not sign the note (signed=${signed && signed.signed})`);
    assert(signed.signingProviderNo === providerNo, `signing provider was ${signed.signingProviderNo}, expected ${providerNo}`);

    // 4. Sign Save & Bill on a fresh note. The closing chart reloads its
    // opener, so let that settle before reloading the day sheet ourselves.
    await daySheet.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
    await daySheet.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await daySheet.reload({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await daySheet.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const chart2 = await openChartFromAppointment(context, daySheet, recorder, 'echart-bill');
    await typeIntoActiveNote(chart2, billedText);
    const billButton = chart2.locator('input[type="image"][title="Sign Save & Bill"]');
    assert(await billButton.count(), 'the chart did not render the Sign Save & Bill button');
    const [billResponse] = await Promise.all([
      chart2.waitForResponse((r) => r.request().method() === 'POST' && /\/CaseManagementEntry/.test(r.url())
        && new URLSearchParams(r.request().postData() || '').get('toBill') === 'true', { timeout: 30000 }),
      pressChartButton(chart2, billButton),
    ]);
    assert(billResponse.status() < 400, `bill save returned HTTP ${billResponse.status()}`);
    await chart2.waitForURL(/\/billing\?/, { timeout: 30000 });
    await chart2.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(chart2, 'bill form after Sign Save & Bill');
    const billUrl = new URL(chart2.url());
    assert(billUrl.searchParams.get('appointment_no') === appointmentNo, `bill hand-off carried appointment ${billUrl.searchParams.get('appointment_no')}`);
    assert(billUrl.searchParams.get('demographic_no') === demographicNo, 'bill hand-off did not carry the patient');
    assert(billUrl.searchParams.get('bNewForm') === '1', 'bill hand-off did not open a new bill form');
    assert(await chart2.locator('select[name="xml_billtype"]').count(), 'bill form did not render after the hand-off');
    const billed = latestNoteWith(billedText);
    assert(billed && billed.signed === '1' && billed.signingProviderNo === providerNo, `Sign Save & Bill did not sign the note (${JSON.stringify(billed)})`);
    await chart2.close().catch(() => {});

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`PASS eChart note saved (${saved.noteId}), signed, and a second note (${billed.noteId}) signed and handed to billing for appointment ${appointmentNo}; timer pasted`);
  } catch (error) {
    console.error(`FAIL eChart note save/sign/bill check: ${error.stack || error.message}`);
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
