#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Verifies eForm <select> prefill from database tags (oscarDB=), dependent
 * database-tag binding (oscarDB="e$count#..."), and saved select restoration
 * through a live CARLOS installation (PR #6284).
 *
 * Covers:
 *   - prefix sibling ("Yes" selects "Yes", not "Yes, with help", and clears a
 *     later template default "No");
 *   - unquoted attribute value before selected and "/" preserves the untouched
 *     option's value ("No", not "No/");
 *   - entity-encoded option value ("Smith &amp; Sons") matches stored
 *     "Smith & Sons", saves untouched, reopens selected, and binds decoded
 *     "Smith & Sons" to a dependent e$count lookup;
 *   - text-only option with a no-break space ("Not&nbsp;done") saves and
 *     reopens selecting the no-break space option rather than "Not done".
 *
 * Owns its synthetic patient, eForm template, revisions, and values; cleanup
 * removes all of them and asserts zero residue. Must not run concurrently with
 * other checks (EXCLUSIVE=1).
 * Uses the common BASE_URL, TEST_*, CHROME_PATH, and MYSQL_* harness contract.
 */
'use strict';

const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const TEMPLATE = '<html><head><title>Select prefill fixture</title></head><body>'
  + '<form method="post">'
  + '<input type="hidden" name="subject" id="subject" value="Synthetic select prefill">'
  + '<select name="Choice" id="Choice" oscarDB=patient_alias>'
  + '<option value="Yes, with help">Yes, with help</option>'
  + '<option value="Yes">Yes</option>'
  + '<option value="Smith &amp; Sons">Smith &amp; Sons</option>'
  + '<option value="No" selected>No</option>'
  + '</select>'
  + '<template><select name="Choice"><option selected>Archived</option></select></template>'
  + '<select name="Choice" disabled><option selected>Archived</option></select>'
  + '<select name="SlashChoice" id="SlashChoice" oscarDB=patient_alias>'
  + '<option value=No selected/>No</option>'
  + '<option value=Yes>Yes</option>'
  + '</select>'
  + '<select name="NbspPick" id="NbspPick">'
  + '<option>Not done</option>'
  + '<option>Not&nbsp;done</option>'
  + '</select>'
  + '<input type="text" name="MatchCount" id="MatchCount" oscarDB="e$count#Choice" var$value="{Choice}">'
  + '</form></body></html>';

async function saveViaToolbar(page, label) {
  const [, answered] = await Promise.all([
    page.waitForRequest((request) => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/eform/addEForm')),
    page.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/eform/addEForm')),
    clickAndAwaitReload(page, page.locator('#remoteSubmitButton'), { timeout: 30000, label }),
  ]);
  h.assert(answered.status() < 400, `${label}: save was answered ${answered.status()}`);
  await h.assertNotErrorPage(page, label);
}

async function workflow(s) {
  const name = `${s.marker} select prefill`;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM EFormDocs WHERE fdid IN
      (SELECT fdid FROM eform_data WHERE fid IN (SELECT fid FROM eform WHERE form_name=?))`, [name]);
    s.sql.execute(`DELETE FROM eform_values WHERE fdid IN
      (SELECT fdid FROM eform_data WHERE fid IN (SELECT fid FROM eform WHERE form_name=?))`, [name]);
    s.sql.execute('DELETE FROM eform_data WHERE fid IN (SELECT fid FROM eform WHERE form_name=?)', [name]);
    s.sql.execute('DELETE FROM eform WHERE form_name=?', [name]);
    h.assert(s.sql.value('SELECT COUNT(*) FROM eform WHERE form_name=?', [name]) === '0',
      'Owned select prefill template remains');
  });

  s.sql.execute(`INSERT INTO eform (form_name,file_name,subject,form_date,form_time,form_creator,status,
      form_html,showLatestFormOnly,patient_independent)
    VALUES (?,'','',CURDATE(),CURTIME(),?,1,?,0,0)`, [name, s.provider, TEMPLATE]);
  const fid = s.sql.value('SELECT fid FROM eform WHERE form_name=?', [name]);
  h.assert(/^[1-9]\d*$/.test(fid), 'Owned select prefill template was not created');

  const page = await s.context.newPage();
  await page.addInitScript(() => { window.close = () => {}; });

  const openNew = async () => {
    await h.gotoApp(page, s.config.baseUrl, `/eform/efmformadd_data?fid=${fid}&demographic_no=${s.patient}`);
    await page.locator('#remoteSubmitButton').waitFor();
    await h.assertNotErrorPage(page, 'open new eForm');
  };

  let fdidYes;
  await s.step('prefix sibling selects exact Yes, clears later default, preserves unquoted slash value, and saves NBSP', async () => {
    s.sql.execute('UPDATE demographic SET alias=? WHERE demographic_no=?', ['Yes', s.patient]);
    await openNew();

    const choiceVal = await page.locator('#Choice').inputValue();
    h.assert(choiceVal === 'Yes', `Expected #Choice to be "Yes", got ${JSON.stringify(choiceVal)}`);
    const selectedChoiceAttrs = await page.evaluate(() => Array.from(
      document.querySelectorAll('#Choice option[selected]')).map((o) => o.value));
    h.assert(selectedChoiceAttrs.length === 1 && selectedChoiceAttrs[0] === 'Yes',
      `Expected only "Yes" marked selected in #Choice, got ${JSON.stringify(selectedChoiceAttrs)}`);

    const slashState = await page.evaluate(() => ({
      selected: document.getElementById('SlashChoice').value,
      options: Array.from(document.querySelectorAll('#SlashChoice option')).map((o) => o.value),
    }));
    h.assert(slashState.selected === 'Yes', `Expected #SlashChoice to be "Yes", got ${JSON.stringify(slashState.selected)}`);
    h.assert(slashState.options[0] === 'No' && slashState.options[1] === 'Yes',
      `Expected #SlashChoice option values ["No","Yes"], got ${JSON.stringify(slashState.options)}`);

    const matchCount = await page.locator('#MatchCount').inputValue();
    h.assert(matchCount === '0', `Expected initial #MatchCount to be "0", got ${JSON.stringify(matchCount)}`);

    await page.locator('#NbspPick').selectOption({ index: 1 });
    const nbspSelected = await page.locator('#NbspPick').inputValue();
    h.assert(nbspSelected === 'Not\u00a0done', `Expected #NbspPick to be "Not\\u00a0done", got ${JSON.stringify(nbspSelected)}`);

    await saveViaToolbar(page, 'save Yes and NBSP');
    const rows = s.sql.rows('SELECT fdid FROM eform_data WHERE fid=? AND demographic_no=? ORDER BY fdid', [fid, s.patient]);
    h.assert(rows.length === 1, `Expected 1 saved revision, found ${rows.length}`);
    fdidYes = rows[0][0];

    const savedChoice = s.sql.value('SELECT var_value FROM eform_values WHERE fdid=? AND var_name=?', [fdidYes, 'Choice']);
    const savedNbsp = s.sql.value('SELECT var_value FROM eform_values WHERE fdid=? AND var_name=?', [fdidYes, 'NbspPick']);
    h.assert(savedChoice === 'Yes', `Expected saved Choice="Yes", got ${JSON.stringify(savedChoice)}`);
    h.assert(savedNbsp === 'Not\u00a0done', `Expected saved NbspPick="Not\\u00a0done", got ${JSON.stringify(savedNbsp)}`);
  });

  await s.step('reopened Yes form preserves exact Yes and NBSP selection and dependent count sees 1', async () => {
    await h.gotoApp(page, s.config.baseUrl, `/eform/efmshowform_data?fdid=${fdidYes}`);
    await page.locator('#remoteSubmitButton').waitFor();
    await h.assertNotErrorPage(page, 'reopen Yes eForm');

    const reopenedChoice = await page.locator('#Choice').inputValue();
    const reopenedNbspIndex = await page.evaluate(() => document.getElementById('NbspPick').selectedIndex);
    const reopenedNbspVal = await page.locator('#NbspPick').inputValue();
    h.assert(reopenedChoice === 'Yes', `Reopened #Choice expected "Yes", got ${JSON.stringify(reopenedChoice)}`);
    h.assert(reopenedNbspIndex === 1 && reopenedNbspVal === 'Not\u00a0done',
      `Reopened #NbspPick expected index 1 ("Not\\u00a0done"), got index=${reopenedNbspIndex} value=${JSON.stringify(reopenedNbspVal)}`);

    // Opening a new instance for the same patient now sees the 1 saved "Yes" row via dependent e$count.
    await openNew();
    const matchCount = await page.locator('#MatchCount').inputValue();
    h.assert(matchCount === '1', `Expected dependent #MatchCount for "Yes" to be "1", got ${JSON.stringify(matchCount)}`);
  });

  let fdidEntity;
  await s.step('entity-encoded option matches Smith & Sons, saves untouched, reopens, and binds decoded dependent count', async () => {
    s.sql.execute('UPDATE demographic SET alias=? WHERE demographic_no=?', ['Smith & Sons', s.patient]);
    await openNew();

    const choiceVal = await page.locator('#Choice').inputValue();
    h.assert(choiceVal === 'Smith & Sons', `Expected #Choice to be "Smith & Sons", got ${JSON.stringify(choiceVal)}`);
    const matchCountBefore = await page.locator('#MatchCount').inputValue();
    h.assert(matchCountBefore === '0', `Expected #MatchCount for "Smith & Sons" before save to be "0", got ${JSON.stringify(matchCountBefore)}`);

    await saveViaToolbar(page, 'save Smith & Sons');
    const rows = s.sql.rows('SELECT fdid FROM eform_data WHERE fid=? AND demographic_no=? ORDER BY fdid', [fid, s.patient]);
    h.assert(rows.length === 2, `Expected 2 saved revisions, found ${rows.length}`);
    fdidEntity = rows[1][0];

    const savedChoice = s.sql.value('SELECT var_value FROM eform_values WHERE fdid=? AND var_name=?', [fdidEntity, 'Choice']);
    h.assert(savedChoice === 'Smith & Sons', `Expected saved Choice="Smith & Sons", got ${JSON.stringify(savedChoice)}`);

    await h.gotoApp(page, s.config.baseUrl, `/eform/efmshowform_data?fdid=${fdidEntity}`);
    await page.locator('#remoteSubmitButton').waitFor();
    await h.assertNotErrorPage(page, 'reopen Smith & Sons eForm');
    const reopenedChoice = await page.locator('#Choice').inputValue();
    h.assert(reopenedChoice === 'Smith & Sons', `Reopened #Choice expected "Smith & Sons", got ${JSON.stringify(reopenedChoice)}`);

    // Opening a new instance binds decoded "Smith & Sons" to e$count and finds the 1 saved row.
    await openNew();
    const matchCountAfter = await page.locator('#MatchCount').inputValue();
    h.assert(matchCountAfter === '1', `Expected dependent #MatchCount for "Smith & Sons" after save to be "1", got ${JSON.stringify(matchCountAfter)}`);
  });
}

if (require.main === module) runWorkflow('eform-select-prefill', workflow);
module.exports = { workflow };
