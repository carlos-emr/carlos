#!/usr/bin/env node
/*
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
 * Browser check for eForm subjects on templates WITHOUT their own subject control (issue #4027).
 *
 * The floating toolbar's Subject box is the only place such a template's subject lives. The
 * server supplies it as a hidden `subject` input; the toolbar copies it in once its fragment has
 * loaded and copies it back at save time. Two ways that silently goes wrong:
 *
 *   1. Reopening a saved form showed an empty Subject, so the next save (including the implicit
 *      save behind Download PDF, Fax and Email) stored an empty subject over the clinician's.
 *   2. A NEW form, or the admin preview, loads the template from the catalog, whose `subject`
 *      column is the template's description. Supplying that pre-filled every new letter's
 *      Subject with the catalog text, which the clinician never chose and would then save.
 *
 * WHAT IT DRIVES. A throwaway catalog template with no subject control and a distinctive catalog
 * description, seeded here so the check never depends on which demo eForms happen to lack one:
 *
 *   a. the admin preview (efmshowform_data?fid=) shows an empty Subject, not the description;
 *   b. a new form (efmformadd_data) shows an empty Subject and carries exactly one subject field;
 *   c. saving with a subject containing quotes, ampersands and angle brackets stores it verbatim;
 *   d. reopening the saved form shows that exact subject (escaped once, not twice) in the toolbar;
 *   e. saving the reopened form again WITHOUT touching the Subject keeps it on the new revision.
 *
 * The Rich Text Letter (a template WITH its own subject control) is covered, through the saved
 * revision behind Download PDF, by eform-rtl-attachment-behavior-playwright-checks.js.
 *
 * Fixtures: the template row and every eform_data revision of it (with its eform_values and
 * EFormDocs rows). Ownership is the fixture's own `fid`, never the subject, so cleanup still
 * finds every revision when subject preservation regresses. All are removed in cleanup,
 * including after a failure.
 *
 * Environment (common contract in lib/playwright-harness.js readConfig()):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (required: the check seeds and asserts rows),
 *   EFORM_SUBJECT_DEMOGRAPHIC_NO (default 1): the synthetic patient the forms are saved against.
 */

const {
  assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner, gotoApp,
  launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');

const TIMEOUT = 30000;
const stamp = `${Date.now()}_${process.pid}`;
const formName = `PW4027 subject fixture ${stamp}`;
const catalogDescription = `PW4027 catalog description ${stamp}`;
const subject = `PW4027 "follow-up" & <review> ${stamp}`;
// A plain template: one form, one clinical-looking field, no subject control of its own.
const templateHtml = '<html><head><title>PW4027 fixture</title></head><body>'
  + '<form method="post" action="" name="pw4027"><p>Note: <textarea name="pw_note" id="pw_note"></textarea></p></form>'
  + '</body></html>';

const state = { sql: null, fid: null, demographicNo: null };

async function openEformPage(context, config, recorder, label, appPath) {
  const page = await context.newPage();
  // A saved form reloads onto a success page that calls window.close(); keep the page to read it.
  await page.addInitScript(() => { window.close = () => {}; });
  wireStrictPage(page, label, recorder);
  await gotoApp(page, config.baseUrl, appPath);
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await assertNotErrorPage(page, label);
  // The toolbar fragment is fetched asynchronously after the page loads.
  await page.locator('#remote_eform_subject').waitFor({ state: 'attached', timeout: TIMEOUT });
  return page;
}

/** Every subject field the save will submit, plus what the toolbar shows. */
async function subjectState(page) {
  return page.evaluate(() => ({
    toolbar: document.getElementById('remote_eform_subject').value,
    fields: Array.from(document.forms[0].elements).filter((field) => field.name === 'subject')
      .map((field) => ({ type: field.type, value: field.value })),
  }));
}

async function save(page, label) {
  await clickAndAwaitReload(page, page.locator('#remoteSubmitButton'), { timeout: TIMEOUT, label });
  await assertNotErrorPage(page, `${label} result`);
}

function revisions() {
  return state.sql.rows(`SELECT fdid, subject FROM eform_data WHERE fid=${Number(state.fid)} ORDER BY fdid`)
    .map(([fdid, stored]) => ({ fdid: Number(fdid), subject: stored }));
}

async function run(config) {
  state.sql = createSqlRunner(config.mysql);
  state.demographicNo = process.env.EFORM_SUBJECT_DEMOGRAPHIC_NO || '1';
  assert(/^[1-9][0-9]*$/.test(state.demographicNo), 'EFORM_SUBJECT_DEMOGRAPHIC_NO must be a positive ID');
  assert(state.sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${Number(state.demographicNo)}`) === '1',
    `demographic ${state.demographicNo} does not exist; set EFORM_SUBJECT_DEMOGRAPHIC_NO`);

  state.sql.execute(`INSERT INTO eform (form_name, file_name, subject, form_date, form_time, form_creator, status,
      form_html, showLatestFormOnly, patient_independent)
    VALUES (${sqlString(formName)}, '', ${sqlString(catalogDescription)}, CURDATE(), CURTIME(), 'pw4027', 1,
      ${sqlString(templateHtml)}, 0, 0)`);
  state.fid = state.sql.value(`SELECT fid FROM eform WHERE form_name=${sqlString(formName)}`);
  assert(/^[1-9][0-9]*$/.test(state.fid), 'the fixture template was not created');

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const landing = await login(context, config, recorder);
    await landing.close();

    // a. Admin preview: catalog-loaded, so the description must not appear as a subject.
    const preview = await openEformPage(context, config, recorder, 'subject-preview',
      `/eform/efmshowform_data?fid=${encodeURIComponent(state.fid)}`);
    const previewState = await subjectState(preview);
    assert(previewState.toolbar === '',
      `the admin preview showed the catalog description as its subject: ${JSON.stringify(previewState.toolbar)}`);
    await preview.close();

    // b. New form: starts empty, with exactly one subject field for the save to fill.
    const addPage = await openEformPage(context, config, recorder, 'subject-add',
      `/eform/efmformadd_data?fid=${encodeURIComponent(state.fid)}&demographic_no=${encodeURIComponent(state.demographicNo)}`);
    const addState = await subjectState(addPage);
    assert(addState.toolbar === '',
      `a new form pre-filled its subject with the catalog description: ${JSON.stringify(addState.toolbar)}`);
    assert(addState.fields.length === 1 && addState.fields[0].value === '',
      `a new form should carry one empty subject field, found ${JSON.stringify(addState.fields)}`);

    // c. Save with punctuation that a double escape or a lost copy would change.
    await addPage.locator('#pw_note').fill('PW4027 synthetic note');
    await addPage.locator('#remote_eform_subject').fill(subject);
    await save(addPage, 'the new form save');
    let saved = revisions();
    assert(saved.length === 1, `expected one saved revision, found ${saved.length}`);
    assert(saved[0].subject === subject, `the saved subject was not stored verbatim: ${JSON.stringify(saved[0].subject)}`);
    await addPage.close();

    // d. Reopen: the toolbar shows the stored subject, escaped exactly once.
    const viewPage = await openEformPage(context, config, recorder, 'subject-reopen',
      `/eform/efmshowform_data?fdid=${encodeURIComponent(saved[0].fdid)}`);
    const viewState = await subjectState(viewPage);
    assert(viewState.toolbar === subject,
      `reopening the saved form changed its subject: ${JSON.stringify(viewState.toolbar)}`);
    assert(viewState.fields.length === 1,
      `a reopened form should carry one subject field, found ${viewState.fields.length}`);

    // e. Save again without touching the Subject: the new revision keeps it.
    await save(viewPage, 'the reopened form save');
    saved = revisions();
    assert(saved.length === 2, `expected a second saved revision, found ${saved.length}`);
    assert(saved[1].subject === subject,
      `re-saving the reopened form lost its subject: ${JSON.stringify(saved[1].subject)}`);
    await viewPage.close();

    assertStrictPage(recorder);
    await context.close();
  } finally {
    await browser.close();
  }
}

async function cleanup() {
  if (!state.sql) {
    return;
  }
  try {
    if (state.fid) {
      const fid = Number(state.fid);
      const owned = `SELECT fdid FROM eform_data WHERE fid=${fid}`;
      state.sql.execute(`START TRANSACTION;
        DELETE FROM EFormDocs WHERE fdid IN (${owned});
        DELETE FROM eform_values WHERE fdid IN (${owned});
        DELETE FROM eform_data WHERE fid=${fid};
        DELETE FROM eform WHERE fid=${fid} AND form_name=${sqlString(formName)};
        COMMIT`);
      assert(state.sql.value(`SELECT COUNT(*) FROM eform_data WHERE fid=${fid}`) === '0', 'owned eForm revisions remain');
    }
    assert(state.sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${sqlString(formName)}`) === '0',
      'the fixture template remains');
  } finally {
    state.sql.dispose();
  }
}

if (require.main === module) {
  runCheck({
    name: 'eform-subject-preservation',
    run: () => run(readConfig({ require: ['MYSQL_PASSWORD'] })),
    cleanup,
  });
}
