#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * eForm delete and restore, for a patient's eForm and for a patient-independent eForm.
 *
 * User path: E-Chart > eForms "+" > the owned eForm > fill > toolbar Submit; E-Chart > eForms
 * heading (patient eForm list) > Delete > Deleted > Restore > back to the list; then Schedule >
 * Administration > Forms > Patient Independent eForms > Deleted > Restore > Current > Delete.
 * Asserts eform_data.status 1 -> 0 -> 1 for the saved fdid after each control (eform/addEForm,
 * eform/removeEForm, eform/unRemoveEForm, with and without callpage=independent), the saved field
 * value in eform_values, and that each list (eform/efmpatientformlist, efmpatientformlistdeleted,
 * efmmanageindependent, efmmanageindependentdeleted) shows the owned row exactly where it belongs.
 * Fixtures: two owned eForm templates named with the run marker (one patient-bound, one patient
 * independent), the instance saved through the UI and two seeded independent instances (one
 * current, one deleted) for the owned patient. Cleanup removes only those fdids, their eform_values and the two templates.
 * Implements coverage plan section 4.1 eForms, eform-groups-independent (deleted lists + restore).
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates, clickInjectsPanel, clickAndAwaitReload} = require('./lib/playwright-ui');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TEMPLATE_HTML = '<html><head><title>eForm restore fixture</title><script>var needToConfirm = false;'
  + 'document.onkeyup = function () { needToConfirm = true; };'
  + 'function releaseDirtyFlag() { needToConfirm = false; }</script></head><body>'
  + '<form method="post" action="" name="FormName" id="FormName">'
  + '<input type="text" name="note" id="note" style="width:300px">'
  + '<div class="DoNotPrint" id="BottomButtons"><input value="Submit" name="SubmitButton" id="SubmitButton"'
  + ' type="submit" onclick="releaseDirtyFlag();"></div></form></body></html>';

async function workflow(s) {
  const patientForm = `${s.marker} Patient eForm`;
  const independentForm = `${s.marker} Independent eForm`;
  const subject = `${s.marker} restore subject`;
  const independentSubject = `${s.marker} independent current`;
  const removedSubject = `${s.marker} independent removed`;
  const note = `${s.marker} restore note`;
  const fids = [];

  const savedFdids = () => s.sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${s.patient}
    AND fid IN (${[...fids, 0].join(',')})`).map(([fdid]) => fdid);
  s.cleanup(() => {
    for (const fdid of savedFdids()) {
      s.sql.execute(`DELETE FROM eform_values WHERE fdid=${fdid}; DELETE FROM eform_data WHERE fdid=${fdid}`);
    }
    for (const fid of fids) {
      s.sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name LIKE ${h.sqlString(s.marker + '%')}`);
    }
    // fid 0 rows are counted only for the owned patient: other charts may hold them.
    const ids = fids.length ? fids.join(',') : '-1';
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE fid IN (${ids}))
      + (SELECT COUNT(*) FROM eform_data WHERE fid IN (${ids}) OR (fid=0 AND demographic_no=${s.patient}))`) === '0',
    'Owned eForm templates or instances were not removed');
  });
  for (const [name, independent] of [[patientForm, 0], [independentForm, 1]]) {
    const fid = s.sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,
      status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
      VALUES(${h.sqlString(name)},'','eForm restore fixture',CURDATE(),CURTIME(),${h.sqlString(s.provider)},
      1,${h.sqlString(TEMPLATE_HTML)},0,${independent},'',0,1); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(fid), 'eForm template fixture was not created');
    fids.push(fid);
  }
  const [patientFid, independentFid] = fids;
  const seedIndependent = (text, current) => s.sql.value(`INSERT INTO eform_data(fid,form_name,subject,demographic_no,
    status,form_date,form_time,form_provider,form_data,showLatestFormOnly,patient_independent,roleType)
    VALUES(${independentFid},${h.sqlString(independentForm)},${h.sqlString(text)},${s.patient},${current},
    CURDATE(),CURTIME(),${h.sqlString(s.provider)},${h.sqlString(TEMPLATE_HTML)},0,1,''); SELECT LAST_INSERT_ID()`);
  const independentFdid = seedIndependent(independentSubject, 1);
  const removedFdid = seedIndependent(removedSubject, 0);
  h.assert(/^[1-9]\d*$/.test(independentFdid) && /^[1-9]\d*$/.test(removedFdid), 'Independent eForm instance fixtures were not created');
  const status = fdid => s.sql.value(`SELECT status FROM eform_data WHERE fdid=${fdid}`);
  let fdid;

  const chart = await s.chart();
  await s.step('E-Chart Add eForm saves the owned eForm with its field value and subject', async () => {
    const addList = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    // The Add eForm list stays open: closing it is covered (and currently fails) in eform-groups.
    const form = await s.popup(addList, addList.locator('#efmTable a').filter({hasText: patientForm}), 'eform-fill');
    await form.locator('#note').fill(note);
    await form.locator('#remote_eform_subject').fill(subject);
    const closed = form.waitForEvent('close', {timeout: 30000}).catch(() => null);
    await form.locator('#remoteSubmitButton').click();
    await expectValue(s.sql, `SELECT COUNT(*) FROM eform_data WHERE fid=${patientFid} AND demographic_no=${s.patient}
      AND status=1 AND subject=${h.sqlString(subject)}`, '1', 'Submitting the eForm did not save one current instance');
    fdid = s.sql.value(`SELECT fdid FROM eform_data WHERE fid=${patientFid} AND demographic_no=${s.patient}`);
    h.assert(s.sql.value(`SELECT var_value FROM eform_values WHERE fdid=${fdid} AND var_name='note'`) === note,
      'The saved eForm did not store the typed field value');
    await closed;
  });

  const list = await s.popup(chart, chart.locator('a[onclick*="/eform/efmpatientformlist?"]').first(), 'eform-patient-list');
  const rowFor = (page, text) => page.locator('#efmTable tbody tr').filter({hasText: text});
  await s.step('patient eForm list Delete marks the instance deleted and drops it from the list', async () => {
    const row = rowFor(list, subject);
    h.assert(await row.count() === 1, 'The patient eForm list does not show the saved instance');
    const dialogs = await h.withExpectedDialogs(list, () => clickAndAwaitReload(list,
      row.locator('form[action$="/eform/removeEForm"] a'), {label: 'patient eForm Delete'}));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete did not ask for confirmation');
    await expectValue(s.sql, `SELECT status FROM eform_data WHERE fdid=${fdid}`, '0', 'Delete did not mark the instance deleted');
    h.assert(new URL(list.url()).pathname.endsWith('/eform/efmpatientformlist'), 'Delete did not return to the patient eForm list');
    await list.locator('#efmTable').waitFor();
    h.assert(await rowFor(list, subject).count() === 0, 'The deleted instance is still listed as current');
  });

  await s.step('patient Deleted list shows the deleted instance and no independent eForm', async () => {
    await clickAndAwaitReload(list, list.locator('a[href^="efmpatientformlistdeleted"]').first(), {label: 'Deleted eForms'});
    await list.locator('#efmTable').waitFor();
    h.assert(await rowFor(list, subject).count() === 1, 'The deleted eForm list does not show the deleted instance');
    h.assert(await rowFor(list, independentSubject).count() === 0, 'The patient deleted list shows a patient-independent eForm');
  });

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'eform-independent-admin', timeout: 20000});
  const independentRows = text => admin.locator('table tbody tr').filter({hasText: text});
  await s.step('Administration Patient Independent eForms lists only the owned current independent instance', async () => {
    await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
    await clickInjectsPanel(admin, admin.locator('#collapseForms a[href$="/eform/efmmanageindependent"]').first(),
      {marker: '#dynamic-content table'});
    h.assert(await independentRows(independentSubject).count() === 1, 'The independent eForm list does not show the owned instance');
    h.assert(await independentRows(removedSubject).count() === 0, 'The independent eForm list shows a deleted instance');
    h.assert(await independentRows(subject).count() === 0, 'The independent eForm list shows a patient-bound eForm');
  });

  await s.step('independent Deleted view lists only the owned deleted independent instance', async () => {
    await clickInjectsPanel(admin, admin.locator('#dynamic-content a[href$="/eform/efmmanageindependentdeleted"]').first(),
      {marker: '#dynamic-content a[onclick*="unRemoveIndependent"]'});
    h.assert(await independentRows(removedSubject).count() === 1, 'The independent deleted list does not show the deleted instance');
    h.assert(await independentRows(independentSubject).count() === 0, 'The independent deleted list shows a current instance');
    h.assert(await independentRows(subject).count() === 0, 'The independent deleted list shows a patient-bound eForm');
    h.assert(status(removedFdid) === '0' && status(independentFdid) === '1', 'Viewing the independent lists changed an instance');
  });

  // The remaining controls are refused on 2026.08 (see the report); they run after everything provable.
  await s.step('independent Restore makes the deleted instance current again', async () => {
    const dialogs = await h.withExpectedDialogs(admin, () => clickAndAwaitReload(admin,
      independentRows(removedSubject).locator('a[onclick*="unRemoveIndependent"]'), {label: 'independent eForm Restore'}));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Restore did not ask for confirmation');
    await expectValue(s.sql, `SELECT status FROM eform_data WHERE fdid=${removedFdid}`, '1',
      'Restore did not make the independent instance current');
    h.assert(new URL(admin.url()).pathname.endsWith('/eform/efmmanageindependentdeleted'),
      'Restore did not return to the independent deleted list');
    h.assert(await independentRows(removedSubject).count() === 0, 'The restored independent instance is still listed as deleted');
    h.assert(status(fdid) === '0', 'Restoring the independent eForm changed the patient eForm');
  });

  await s.step('independent Delete marks the current instance deleted', async () => {
    await clickAndAwaitReload(admin, admin.locator('a[href$="/eform/efmmanageindependent"]').first(), {label: 'independent Current'});
    const dialogs = await h.withExpectedDialogs(admin, () => clickAndAwaitReload(admin,
      independentRows(independentSubject).locator('form[action$="/eform/removeEForm"] a'), {label: 'independent eForm Delete'}));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete did not ask for confirmation');
    await expectValue(s.sql, `SELECT status FROM eform_data WHERE fdid=${independentFdid}`, '0',
      'Delete did not mark the independent instance deleted');
    h.assert(new URL(admin.url()).pathname.endsWith('/eform/efmmanageindependent'), 'Delete did not return to the independent list');
    h.assert(await independentRows(independentSubject).count() === 0, 'The deleted independent instance is still listed as current');
  });

  await s.step('patient Deleted list Restore makes the instance current and the eForm list shows it', async () => {
    const row = rowFor(list, subject);
    const dialogs = await h.withExpectedDialogs(list, () => clickAndAwaitReload(list,
      row.locator('a[onclick*="unRemoveEForm"]'), {label: 'patient eForm Restore'}));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Restore did not ask for confirmation');
    await expectValue(s.sql, `SELECT status FROM eform_data WHERE fdid=${fdid}`, '1', 'Restore did not make the instance current');
    h.assert(new URL(list.url()).pathname.endsWith('/eform/efmpatientformlistdeleted'), 'Restore did not return to the deleted list');
    await list.locator('#efmTable').waitFor();
    h.assert(await rowFor(list, subject).count() === 0, 'The restored instance is still listed as deleted');
    await clickAndAwaitReload(list, list.locator('a[href^="efmpatientformlist?"]').first(), {label: 'eForm list'});
    await list.locator('#efmTable').waitFor();
    h.assert(await rowFor(list, subject).count() === 1, 'The restored instance is not listed as current');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM eform_data WHERE fid=${patientFid}`) === '1', 'Delete or restore duplicated the instance');
  });
}

if (require.main === module) runWorkflow('eform-deleted-restore', workflow, {openPatient: true});
module.exports = {workflow};
