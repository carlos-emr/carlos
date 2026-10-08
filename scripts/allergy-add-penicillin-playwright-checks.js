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
 *      reaction, severity, onset, life stage, start date and not archived (the
 *      DrugRef identifiers are asserted by step 6);
 *   4. asserts the eChart's Allergies module shows the new allergy;
 *   5. amends it from the list's own "Modify" link and asserts the correction
 *      path's contract: RxAddAllergy2Action ARCHIVES the original
 *      (allergyToArchive -> patient.deleteAllergy) and writes a REPLACEMENT row
 *      rather than updating in place, so the record keeps what was believed when.
 *      Both halves are asserted -- an amend that only adds leaves the allergy
 *      recorded twice, one that only archives loses it -- along with
 *      allergyToArchive surviving onto the reloaded form, which is what makes the
 *      difference between an amend and a second add.
 *
 *   6. (Pinned to app-findings-log.md finding 178.) Both rows, the added allergy and its
 *      amendment, must carry BOTH drug identifiers (regional_identifier and atc), and
 *      RxAddAllergy2Action must have logged no ERROR in this run's window of the server journal.
 *      RxAddAllergy2Action catches a failed DrugRef lookup (RxDrugData.getDrug throws
 *      NoSuchElementException) and saves the allergy anyway, without its ATC code, which
 *      silently turns drug-allergy checking off for it. (This step replaces step 3's old
 *      "either identifier" assertion, which read an empty pair as set: the SQL helper trimmed the
 *      trailing empty columns off the row, leaving both fields undefined.) It reads the journal through
 *      CARLOS_LOG_JOURNAL_UNIT; without it the check ends SKIP (never PASS) after its other
 *      assertions, because the no-ERROR half cannot be judged.
 *
 * Both allergy rows (the original and the amendment) are deleted in a finally.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional: ALLERGY_DEMOGRAPHIC_NO (1), CARLOS_LOG_JOURNAL_UNIT (the systemd unit whose journal holds the
 * server log, for step 6).
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const h = require('./lib/playwright-harness');
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
  // Only the final newline is dropped. A trim() here also removed the trailing tab-separated empty
  // columns of allergyRow(), so a row whose regional_identifier and atc were both empty came back with
  // both fields undefined, and `undefined !== ''` read as "set".
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).replace(/\r?\n$/, '');
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
// Runs once, from runCheck's cleanup hook (which also runs it after an interruption): every
// step is attempted, and a step that fails throws afterwards, because a fixture left behind is
// a failure of this check even when every assertion passed.
function runCleanup() {
  if (cleanupDone || !mysqlDefaults) {
    return;
  }
  cleanupDone = true;
  const failures = [];
  for (const step of [cleanupRows]) {
    try {
      step();
    } catch (cleanupError) {
      console.error(`FAIL cleanup step ${step.name} failed: ${cleanupError.message}`);
      failures.push(cleanupError.message);
    }
  }
  assert(failures.length === 0, `cleanup failed: ${failures.join('; ')}`);
}

/**
 * The label of the pinned step. It is the one labelled step of this script: the stages before it
 * are unlabelled, so a failure in them is reported as a failure elsewhere, never as the known one.
 */
const IDENTIFIER_STEP = 'the added allergy and its amendment both carry a regional identifier and an ATC code, and the action logged no error';

/** Runs one labelled step, tagging a failure with its label for the suite runner (markFailedStep). */
async function runStep(cancellation, label, body) {
  try {
    await cancellation.run(body);
  } catch (error) {
    throw h.markFailedStep(error, label);
  }
  console.log(`  PASS allergy-add-penicillin: ${label}`);
}

/**
 * ERROR lines RxAddAllergy2Action wrote to the server journal since `since`, as a count (never the
 * lines: the action's message can name a drug and a patient-linked id). `unit` is the systemd unit
 * (CARLOS_LOG_JOURNAL_UNIT). The first line of a logged error names the logger, so a stack trace's
 * continuation lines are not counted twice.
 */
function journalActionErrors(unit, since) {
  assert(/^[A-Za-z0-9][A-Za-z0-9_.@-]*\.service$/.test(unit), 'CARLOS_LOG_JOURNAL_UNIT must name one systemd .service unit');
  const stamp = since.toISOString().replace('T', ' ').replace(/\.\d+Z$/, ' UTC');
  const journal = execFileSync('journalctl', ['-u', unit, '--since', stamp, '--no-pager', '-o', 'cat'],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, timeout: 30000 });
  assert(journal.trim() !== '' && !/^-- No entries --/.test(journal.trim()),
    'the server journal holds nothing in this run\'s window, so it cannot show the action logged no error');
  return journal.split('\n').filter((line) => /\bERROR\b/.test(line) && line.includes('RxAddAllergy2Action')).length;
}

function waitForListReload(page) {
  return page.waitForEvent('framenavigated', {
    predicate: (frame) => frame === page.mainFrame() && /\/rx\/showAllergy/.test(frame.url()),
    timeout: 30000,
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

async function main({ cancellation }) {
  const recorder = createRecorder();
  // The browser launch sits inside the protected scope so a failure in it still reaches the
  // fixture cleanup (runCheck's cleanup hook runs whatever this function does).
  try {
    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1200, height: 1000 } });
    await login(context, config, recorder);
    // The window of the server journal this run is judged on (step 6).
    const windowStart = new Date(Date.now() - 2000);

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

    cancellation.throwIfCancelled();
    // 2. Details + submit.
    await form.locator('#reactionDescription').fill(reactionText);
    await form.locator('select[name="severityOfReaction"]').selectOption('3');
    await form.locator('select[name="onSetOfReaction"]').selectOption('1');
    await form.locator('select[name="lifeStage"]').selectOption('A');
    await fillStartDate(page, form, '2024-01-15');
    if (await form.locator('select[name="nonDrug"]').count()) {
      await form.locator('select[name="nonDrug"]').selectOption('off');
    }
    // The save posts in the page and only then reloads the allergy list (rx-allergy-dialog.js,
    // #3488), and the page is already at /rx/showAllergy: wait for that reload, not for the URL.
    const [saveResponse] = await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/rx/addAllergy2'), { timeout: 30000 }),
      waitForListReload(page),
      form.locator('input[type="submit"][value="Add Allergy"]').click(),
    ]);
    assert(saveResponse.status() < 400, `addAllergy2 returned HTTP ${saveResponse.status()}`);
    await page.waitForURL(/\/rx\/showAllergy/, { timeout: 30000 });
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'allergy page after add');
    assert((await page.locator('body').innerText()).includes('PENICILLINS'), 'allergy list did not show PENICILLINS after adding it');

    cancellation.throwIfCancelled();
    // 3. Persistence.
    const row = allergyRow();
    assert(row, 'allergies row was not created');
    assert(row.description === 'PENICILLINS', `saved description was ${row.description}`);
    assert(row.typeCode === '10', `saved TYPECODE was ${row.typeCode}`);
    assert(row.drugrefId === '44452', `saved drugref_id was ${row.drugrefId}`);
    assert(row.severity === '3' && row.onset === '1' && row.lifeStage === 'A', `saved severity/onset/lifeStage were ${row.severity}/${row.onset}/${row.lifeStage}`);
    assert(row.startDate.startsWith('2024-01-15'), `saved start_date was ${row.startDate}`);
    assert(row.archived === '0', 'new allergy was saved archived');
    // The DrugRef identifiers (regional_identifier and atc) are asserted by the last step (6), for this row and
    // for the amendment, where both are required; this stage no longer asks for either one alone.

    cancellation.throwIfCancelled();
    // 4. eChart shows it.
    const chart = await context.newPage();
    wirePage(chart, 'echart', recorder);
    await gotoApp(chart, config.baseUrl, `/encounter/IncomingEncounter?demographicNo=${encodeURIComponent(demographicNo)}&providerNo=${encodeURIComponent(config.testUser === 'carlosdoc' ? '999998' : '')}&curProviderNo=`);
    await chart.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(chart, 'eChart');
    await chart.waitForFunction(() => document.body.innerText.includes('PENICILLINS'), null, { timeout: 30000 });
    await chart.close();

    cancellation.throwIfCancelled();
    // 5. Amend the allergy the way the list offers it: the "Modify" link beside the
    // row. This is the correction path a clinician uses when a reaction was recorded
    // wrongly, and it is not an UPDATE -- RxAddAllergy2Action archives the original
    // (allergyToArchive -> patient.deleteAllergy) and writes a replacement row, so the
    // history of what was believed when is preserved. Both halves are asserted: a
    // version that only added would double-report the allergy, and one that only
    // archived would lose it.
    // allergyToArchive is the last field of the anchor id, so match it as a suffix:
    // a contains() on "allergyToArchive=12" also matches the row for allergy 123.
    const archiveSuffix = `allergyToArchive=${row.id}`;
    const modifyLink = page.locator('a.modifyAllergyLink').filter({ hasText: 'Modify' })
      .locator(`xpath=self::a[substring(@id, string-length(@id) - ${archiveSuffix.length - 1}) = "${archiveSuffix}"]`)
      .first();
    assert(await modifyLink.count() > 0,
      `the allergy list offered no Modify link carrying allergyToArchive=${row.id}`);

    const [amendFormResponse] = await Promise.all([
      page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/rx/addReaction2'), { timeout: 30000 }),
      modifyLink.click(),
    ]);
    assert(amendFormResponse.status() < 400, `addReaction2 (amend) returned HTTP ${amendFormResponse.status()}`);
    const amendForm = page.locator('#RxAddAllergyForm');
    await amendForm.waitFor({ state: 'visible', timeout: 30000 });
    // The archive target has to survive onto the form, or the amend silently becomes a
    // plain second add and the patient ends up with the allergy recorded twice.
    assert((await amendForm.locator('#allergyToArchive').inputValue()) === row.id,
      `the amend form carried allergyToArchive=${await amendForm.locator('#allergyToArchive').inputValue()}, expected ${row.id}`);
    assert((await amendForm.locator('input[name="name"]').inputValue()) === 'PENICILLINS',
      'the amend form did not reload the allergy being corrected');

    await amendForm.locator('#reactionDescription').fill(amendedReactionText);
    await amendForm.locator('select[name="severityOfReaction"]').selectOption('1');
    await fillStartDate(page, amendForm, '2024-01-15');
    const [amendResponse] = await Promise.all([
      page.waitForResponse((response) => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/rx/addAllergy2'), { timeout: 30000 }),
      waitForListReload(page),
      amendForm.locator('input[type="submit"][value="Add Allergy"]').click(),
    ]);
    assert(amendResponse.status() < 400, `addAllergy2 (amend) returned HTTP ${amendResponse.status()}`);
    await page.waitForURL(/\/rx\/showAllergy/, { timeout: 30000 });
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'allergy page after amend');

    const replacement = allergyRow(amendedReactionText);
    assert(replacement, 'the amend wrote no replacement allergies row');
    assert(replacement.id !== row.id,
      'the amend overwrote the original row instead of archiving it and adding a replacement');
    assert(replacement.description === 'PENICILLINS' && replacement.drugrefId === '44452',
      `the replacement row lost the drug identity: ${replacement.description}/${replacement.drugrefId}`);
    assert(replacement.severity === '1', `the replacement kept severity ${replacement.severity} instead of the corrected 1`);
    assert(replacement.archived === '0', 'the replacement row was written already archived');
    const archivedOriginal = sql(`SELECT archived FROM allergies WHERE allergyid=${Number(row.id)}`);
    assert(archivedOriginal === '1',
      `the original allergy ${row.id} was left with archived=${archivedOriginal} after the amend;`
      + ' the patient now has the same allergy recorded twice');
    // The list must show the correction and not the superseded reaction, which is the
    // part an operator actually sees.
    const listText = await page.locator('body').innerText();
    assert(listText.includes('PENICILLINS'), 'the allergy list lost PENICILLINS after the amend');
    assert(listText.includes(amendedReactionText),
      'the allergy list does not show the corrected reaction text after the amend,'
      + ' so the correction reached the database without reaching the operator');
    assert(!listText.includes(reactionText),
      'the allergy list still shows the superseded reaction text after the amend');

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0, `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);

    // 6. Both identifiers, on the added allergy and on its amendment, and no logged error (finding 178).
    // Pinned: holds only what the finding breaks. The rows are read again here, after the amend archived
    // the original, so the step judges what is stored rather than what the earlier stages saw.
    const journalUnit = (process.env.CARLOS_LOG_JOURNAL_UNIT || '').trim();
    let loggedErrors = null;
    await runStep(cancellation, IDENTIFIER_STEP, async () => {
      const problems = [];
      for (const [what, wanted] of [['the added allergy', reactionText], ['its amendment', amendedReactionText]]) {
        const stored = allergyRow(wanted);
        if (!stored) {
          problems.push(`${what} is not stored`);
          continue;
        }
        if (stored.regionalId === '') problems.push(`${what} has no regional_identifier`);
        if (stored.atc === '') problems.push(`${what} has no atc`);
      }
      if (journalUnit) {
        loggedErrors = journalActionErrors(journalUnit, windowStart);
        if (loggedErrors > 0) problems.push(`RxAddAllergy2Action logged ${loggedErrors} ERROR line(s) in this run's window`);
      }
      assert(problems.length === 0,
        `The saved allergy lacks the drug identifiers that drug-allergy checking needs: ${problems.join('; ')}`);
    });
    if (!journalUnit) {
      throw new h.SkipCheck('the allergy rows carry both identifiers, but CARLOS_LOG_JOURNAL_UNIT is not set, so there is no server journal '
        + 'to show RxAddAllergy2Action logged no error');
    }

    console.log(`Penicillins allergy ${row.id} added for demographic ${demographicNo}, shown in the eChart, and amended to ${replacement.id} with the original archived`);
  } catch (error) {
    // The failure details name pages and requests, never patient data: the same dump the check always wrote.
    if (!(error instanceof h.SkipCheck)) console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    throw error;
  }
}

if (require.main === module) {
  initMysqlDefaults();
  h.runCheck({
    name: 'allergy-add-penicillin',
    run: ({ cancellation }) => main({ cancellation }),
    cleanup: async () => {
      try {
        if (browser) await browser.close().catch(() => {});
      } finally {
        try { runCleanup(); } finally { cleanupMysqlDefaults(); }
      }
    },
  });
}
module.exports = { main, journalActionErrors };
