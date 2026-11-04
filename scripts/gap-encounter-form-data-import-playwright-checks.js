#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration ▸ Forms/eForms ▸ Import Form Data: load an encounter-form data archive.
 *
 * User path: Schedule ▸ Administration ▸ Forms/eForms ▸ "Import Form Data" (the upload page loads in
 * the administration panel) ▸ choose a .zip of <formTable>_<demographicNo>_<timestamp>.xml entries ▸
 * Import (multipart POST form/xmlUpload) ▸ the success page, which returns to Administration.
 * Asserts against MariaDB: a one-entry archive for the 2 Minute Walk form table stores exactly one row
 * for the owned patient with every XML field (study id, distance, comment, checkbox) and the patient
 * and edit timestamp taken from the entry name; importing the same archive again adds no second row;
 * an eForm export archive (made from Manage eForms and then removed from the library) is restored as an
 * eForm by the same page; and an archive whose only entry names a form table that does not exist is NOT
 * reported as a success.
 * That last step fails today: the legacy importer swallows its own XmlImportException (JDBCUtil
 * .toDataBase) and FrmXmlUpload2Action.importLegacyArchive then returns success, so the administrator
 * is told the import worked while nothing was stored.
 * Fixtures: the owned synthetic patient and its form2MinWalk rows only (the import targets the
 * patient named in the entry); cleanup deletes the rows and asserts them gone. Implements gap-encounter
 * "import form data from an archive" (form/xmlUpload had no check).
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates, clickInjectsPanel } = require('./lib/playwright-ui');
const { zipStored } = require('./lib/gap-encounter-zip');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, patient } = s;
  // The entry's patient is this run's own synthetic one, so a fixed edit timestamp cannot collide.
  const stamp = '20010203040506';
  h.assert(/^\d{14}$/.test(stamp), 'The import timestamp is not 14 digits');
  const study = marker.slice(-12);
  const comment = `Walked 2 min, "steady" & unaided ${marker}`;
  const rows = () => sql.value(`SELECT COUNT(*) FROM form2MinWalk WHERE demographic_no=${patient} AND studyID=${q(study)}`);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gap-form-import-'));
  s.cleanup(() => {
    sql.execute(`DELETE FROM form2MinWalk WHERE demographic_no=${patient} AND studyID=${q(study)}`);
    fs.rmSync(dir, { recursive: true, force: true });
    h.assert(rows() === '0' && !fs.existsSync(dir), 'Owned imported form rows or temporary files were not removed');
  });
  const xml = `<?xml version="1.0" encoding="UTF-8"?><Results><Row><ID>999999</ID><provider_no>${s.provider}</provider_no>`
    + `<formCreated>2001-02-03</formCreated><studyID>${study}</studyID><distance>12 metres</distance>`
    + `<Q1tried>1</Q1tried><Q1Cmt>${comment.replace(/&/g, '&amp;').replace(/"/g, '&quot;')}</Q1Cmt></Row></Results>`;
  const archive = (name, entries) => {
    const file = path.join(dir, name);
    fs.writeFileSync(file, zipStored(entries));
    return file;
  };
  const valid = archive('valid.zip', [{ name: `form2MinWalk_${patient}_${stamp}.xml`, data: xml }]);
  const unknown = archive('unknown.zip', [{ name: `formNoSuchTable${marker.slice(-8)}_${patient}_${stamp}.xml`, data: xml }]);

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'form-import-admin', timeout: 20000 });
  const openImport = async () => {
    await admin.locator('button[data-bs-target="#collapseForms"]').first().click().catch(() => {});
    await clickInjectsPanel(admin, admin.locator('a.contentLink[href$="/form/formXmlUpload"]').first(),
      { marker: '#dynamic-content form[action$="/form/xmlUpload"]' });
  };
  const importArchive = async file => {
    await admin.locator('#dynamic-content input[name="file1"]').setInputFiles(file);
    await Promise.all([admin.waitForURL(/\/form\/xmlUpload/, { timeout: 30000 }), admin.locator('#dynamic-content input[type="submit"][value="Import"]').click()]);
    await admin.waitForLoadState('domcontentloaded');
    return (await admin.locator('body').innerText()).replace(/\s+/g, ' ');
  };

  await s.step('Administration ▸ Forms/eForms ▸ Import Form Data loads the upload form in the panel', async () => {
    await openImport();
    h.assert(await admin.locator('#dynamic-content input[name="file1"]').count() === 1, 'The import page has no file chooser');
    h.assert(rows() === '0', 'The owned patient already has imported rows');
  });

  await s.step('importing a one-entry archive stores the row for the patient named in the entry', async () => {
    const text = await importArchive(valid);
    h.assert(/success|imported|complete/i.test(text), 'The import did not answer with a success page');
    await expectValue(sql, `SELECT COUNT(*) FROM form2MinWalk WHERE demographic_no=${patient} AND studyID=${q(study)}`, '1',
      'The archive entry was not imported for the owned patient');
    const [row] = sql.rows(`SELECT distance, Q1tried, formEdited, provider_no FROM form2MinWalk WHERE demographic_no=${patient} AND studyID=${q(study)}`);
    h.assert(row[0] === '12 metres' && row[1] === '1', 'The imported row lost the XML field values');
    h.assert(sql.value(`SELECT COUNT(*) FROM form2MinWalk WHERE demographic_no=${patient} AND studyID=${q(study)} AND Q1Cmt=${q(comment)}`) === '1',
      'The imported comment lost its punctuation, quotes or ampersand');
    h.assert(row[2].replace(/\D/g, '') === stamp, 'The imported row did not take its edit timestamp from the entry name');
    h.assert(row[3] === s.provider, 'The imported row lost the provider from the XML');
    // The success page returns to Administration on its own after three seconds.
    await admin.waitForURL(/\/administration/, { timeout: 15000 });
  });

  await s.step('importing the same archive again adds no second row', async () => {
    await openImport();
    const text = await importArchive(valid);
    h.assert(/success|imported|complete/i.test(text), 'The repeat import did not answer with a success page');
    h.assert(rows() === '1', 'Importing the same archive twice stored a duplicate row');
    await admin.waitForURL(/\/administration/, { timeout: 15000 });
  });

  await s.step('an eForm export archive is imported as an eForm in the library', async () => {
    // Manage eForms ▸ the owned eForm's export link makes the archive; the template is then removed and
    // Import Form Data restores it (FrmXmlUpload2Action recognises an eForm archive by its eform.properties).
    const templateName = `${marker} Export`;
    s.cleanup(() => {
      sql.execute(`DELETE FROM eform WHERE form_name=${q(templateName)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${q(templateName)}`) === '0', 'The owned exported eForm was not removed');
    });
    sql.execute(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
      showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
      VALUES(${q(templateName)},'','export fixture',CURDATE(),CURTIME(),${q(s.provider)},1,
      '<html><head><title>export</title></head><body><form name="FormName" id="FormName" method="post" action=""><input type="text" name="subject" id="subject"><input type="text" name="field_${marker.slice(-6)}"></form></body></html>',0,0,'',0,1)`);
    await admin.locator('button[data-bs-target="#collapseForms"]').first().click();
    await admin.locator('a.defaultForms').first().click();
    await admin.locator('#dynamic-content #eformTbl').waitFor({ state: 'visible', timeout: 30000 });
    const row = admin.locator('#dynamic-content #eformTbl tbody tr', { hasText: templateName }).first();
    const [download] = await Promise.all([admin.waitForEvent('download', { timeout: 30000 }), row.locator('a[href*="method=exportEForm"]').first().click()]);
    const exported = path.join(dir, 'export.zip');
    await download.saveAs(exported);
    h.assert(fs.statSync(exported).size > 0, 'The exported archive is empty');
    sql.execute(`DELETE FROM eform WHERE form_name=${q(templateName)}`);
    await openImport();
    const text = await importArchive(exported);
    h.assert(/success|imported|complete/i.test(text), 'The eForm archive import did not answer with a success page');
    await expectValue(sql, `SELECT COUNT(*) FROM eform WHERE form_name=${q(templateName)} AND status=1`, '1',
      'Import Form Data did not restore the exported eForm into the library');
    h.assert(sql.value(`SELECT form_html LIKE ${q(`%field_${marker.slice(-6)}%`)} FROM eform WHERE form_name=${q(templateName)}`) === '1',
      'The restored eForm lost its markup');
    await admin.waitForURL(/\/administration/, { timeout: 15000 });
  });

  await s.step('an archive that names a form table that does not exist is not reported as imported', async () => {
    await openImport();
    const before = sql.value(`SELECT COUNT(*) FROM form2MinWalk WHERE demographic_no=${patient}`);
    const text = await importArchive(unknown);
    h.assert(sql.value(`SELECT COUNT(*) FROM form2MinWalk WHERE demographic_no=${patient}`) === before, 'The unknown-table archive changed an existing form table');
    h.assert(!(await admin.locator('.alert-success').count()) && /not|unable|invalid|error|fail/i.test(text),
      'An archive with nothing importable was reported as a successful import');
  });
}

if (require.main === module) runWorkflow('gap-encounter-form-data-import', workflow, { openPatient: true });
module.exports = { workflow };
