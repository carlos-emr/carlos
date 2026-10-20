#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The patient eForm lists opened from the Master Record, where no parentAjaxId is passed.
 * User path: Schedule > Search > Master Record > eForms (efmpatientformlist, same window) >
 * Deleted eForms tab (efmpatientformlistdeleted) > Add eForm tab (efmformslistadd) > Current
 * eForms tab.
 * Why: each list's onunload updateAjax() skips the E-Chart refresh only when
 * `parentAjaxId != "null"`. A missing parentAjaxId used to render as the literal "null"
 * (Encode/e:forJavaScriptBlock); the null-safe <carlos:encode> now renders "", so leaving the
 * Deleted or Add eForm list from the Master Record path writes into window.opener's encForm
 * and throws. efmpatientformlist.jsp was given a guard; its two siblings were not. (The
 * E-Chart path, which DOES pass parentAjaxId, is finding 77 / eform-groups.)
 * Asserts each list renders the owned rows (current instance, deleted instance, template),
 * and finally that leaving the lists raised no script error.
 * Fixtures: owned patient (runWorkflow), one owned eForm template named with the marker, one
 * current and one deleted instance of it for the owned patient; cleanup deletes only those
 * eform_data/eform_values rows and the template and asserts them gone.
 * Risk sweep: encoder-null (null-sentinel regressions of the null-safe encoder migration).
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const TEMPLATE_HTML = '<html><head><title>eForm list fixture</title></head><body>'
  + '<form method="post" action="" name="FormName" id="FormName"><input type="text" name="note" id="note">'
  + '<input type="submit" value="Submit" id="SubmitButton"></form></body></html>';

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const formName = `${marker} list eForm`;
  const currentSubject = `${marker} current`;
  const deletedSubject = `${marker} deleted`;
  let fid = null;

  s.cleanup(() => {
    if (fid) {
      for (const [fdid] of sql.rows(`SELECT fdid FROM eform_data WHERE fid=${fid} AND demographic_no=${patient}`)) {
        sql.execute(`DELETE FROM eform_values WHERE fdid=${fdid}; DELETE FROM eform_data WHERE fdid=${fdid}`);
      }
      sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${h.sqlString(formName)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE fid=${fid})
        + (SELECT COUNT(*) FROM eform_data WHERE fid=${fid})`) === '0', 'Owned eForm template or instances were not removed');
    }
  });
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
      showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${h.sqlString(formName)},'','eForm list fixture',CURDATE(),CURTIME(),${h.sqlString(provider)},1,
      ${h.sqlString(TEMPLATE_HTML)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'The eForm template fixture was not created');
  for (const [subject, status] of [[currentSubject, 1], [deletedSubject, 0]]) {
    const fdid = sql.value(`INSERT INTO eform_data(fid,form_name,subject,demographic_no,status,form_date,form_time,
        form_provider,form_data,showLatestFormOnly,patient_independent,roleType)
      VALUES(${fid},${h.sqlString(formName)},${h.sqlString(subject)},${patient},${status},CURDATE(),CURTIME(),
        ${h.sqlString(provider)},${h.sqlString(TEMPLATE_HTML)},0,0,''); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fdid), 'An eForm instance fixture was not created');
  }

  // Script errors thrown by a list's onunload handler are taken out of the recorder here and
  // asserted in the last step, so every list that CAN be proven is proven first. Anything
  // else the pages report stays in the recorder and fails its own step.
  const unloadErrors = [];
  const takeUnloadErrors = (leaving) => {
    for (let i = s.recorder.pageErrors.length - 1; i >= 0; i--) {
      if (/updateAjax/.test(s.recorder.pageErrors[i].text)) {
        const [entry] = s.recorder.pageErrors.splice(i, 1);
        unloadErrors.push(`${leaving}: ${entry.text.split('\n')[0]}`);
      }
    }
  };
  const list = s.master;
  const rows = text => list.locator('#efmTable tbody tr').filter({ hasText: text });
  const tab = route => list.locator(`a[href^="${route}?"]`).first();

  await s.step('Master Record > eForms opens the patient list without a parentAjaxId and lists the current eForm', async () => {
    await clickAndAwaitReload(list, list.locator('a[href*="/eform/efmpatientformlist?"]').first(), { label: 'Master Record eForms' });
    const url = new URL(list.url());
    h.assert(url.pathname.endsWith('/eform/efmpatientformlist'), 'The Master Record eForms link did not open the patient eForm list');
    h.assert(!url.searchParams.has('parentAjaxId'), 'The Master Record eForms link unexpectedly names an E-Chart section');
    await list.locator('#efmTable').waitFor();
    h.assert(await rows(currentSubject).count() === 1, 'The patient eForm list does not show the owned current eForm');
    h.assert(await rows(deletedSubject).count() === 0, 'The patient eForm list shows the owned deleted eForm');
  });

  await s.step('Deleted eForms tab lists the owned deleted eForm (the guarded patient list unloads cleanly)', async () => {
    await clickAndAwaitReload(list, tab('efmpatientformlistdeleted'), { label: 'Deleted eForms' });
    h.assert(new URL(list.url()).pathname.endsWith('/eform/efmpatientformlistdeleted'), 'The Deleted tab did not open the deleted list');
    await list.locator('#efmTable').waitFor();
    h.assert(await rows(deletedSubject).count() === 1, 'The deleted list does not show the owned deleted eForm');
    h.assert(await rows(currentSubject).count() === 0, 'The deleted list shows the owned current eForm');
  });

  await s.step('Add eForm tab from the Deleted list offers the owned template', async () => {
    await clickAndAwaitReload(list, tab('efmformslistadd'), { label: 'Add eForm' });
    takeUnloadErrors('leaving efmpatientformlistdeleted');
    h.assert(new URL(list.url()).pathname.endsWith('/eform/efmformslistadd'), 'The Add eForm tab did not open the add list');
    await list.locator('#efmTable').waitFor();
    h.assert(await list.locator('#efmTable a').filter({ hasText: formName }).count() === 1,
      'The Add eForm list does not offer the owned template');
  });

  await s.step('Current eForms tab from the Add eForm list shows the owned current eForm again', async () => {
    await clickAndAwaitReload(list, tab('efmpatientformlist'), { label: 'Current eForms' });
    takeUnloadErrors('leaving efmformslistadd');
    h.assert(new URL(list.url()).pathname.endsWith('/eform/efmpatientformlist'), 'The Current tab did not open the patient list');
    await list.locator('#efmTable').waitFor();
    h.assert(await rows(currentSubject).count() === 1, 'The patient eForm list does not show the owned current eForm');
  });

  // Report, don't encode: with no E-Chart behind the list, leaving it must not touch
  // window.opener at all. Both lists currently throw because "" no longer equals "null".
  await s.step('leaving the Deleted and Add eForm lists raised no script error (no parentAjaxId)', async () => {
    h.assert(unloadErrors.length === 0,
      `${unloadErrors.length} list unload handler(s) threw: ${unloadErrors.join(' | ')}`);
  });
}

if (require.main === module) runWorkflow('encoder-null-eform-lists', workflow, { openPatient: true });
module.exports = { workflow };
