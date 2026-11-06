/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./playwright-harness');
const ui = require('./playwright-ui');
const { markDocumentResidue, removeDocumentResidue } = require('./document-residue');
const { openPrintMenu } = require('../demographic-labels-playwright-checks');

async function preparePatientLetters(s) {
  const directory = process.env.LETTER_DOCUMENT_DIR || process.env.DOCUMENT_DIR || process.env.RX_FAX_DOCUMENT_DIR;
  if (!directory) throw new h.SkipCheck('Set LETTER_DOCUMENT_DIR or DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(directory);
  h.assert(fs.statSync(store).isDirectory(), 'Letter document store is not a directory');
  const mark = markDocumentResidue(s.sql);
  const templates = [];
  const owned = `provider_no=${h.sqlString(s.provider)} AND report_name LIKE ${h.sqlString(`${s.marker}%`)}`;
  s.cleanup(() => {
    const reports = s.sql.rows(`SELECT ID,report_name FROM report_letters WHERE ${owned}`);
    for (const [id, name] of reports) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned report ID is invalid');
      const docs = s.sql.rows(`SELECT document_no,docfilename FROM document WHERE docdesc=${h.sqlString(`${id}-${name}`)}`);
      for (const [number, filename] of docs) {
        h.assert(/^[1-9]\d*$/.test(number) && path.basename(filename) === filename
          && filename.startsWith(`letter-${id}-`) && filename.endsWith('.pdf'), 'Generated letter ownership changed');
        h.assert(s.sql.value(`SELECT COUNT(*) FROM ctl_document WHERE document_no=${number}
          AND (module<>'demographic' OR module_id<>${s.patient})`) === '0', 'Another patient acquired an owned letter');
        const file = path.join(store, filename);
        h.assert(fs.lstatSync(file).isFile(), 'Generated letter file is not a regular owned file');
        fs.unlinkSync(file);
        s.sql.execute(`DELETE FROM ctl_document WHERE document_no=${number} AND module='demographic' AND module_id=${s.patient};
          DELETE FROM document WHERE document_no=${number} AND docdesc=${h.sqlString(`${id}-${name}`)}`);
        h.assert(!fs.existsSync(file), 'Generated letter file remains');
      }
      removeDocumentResidue(s.sql, mark, docs.map(row => row[0]));
      s.sql.execute(`DELETE FROM log_letters WHERE report_id=${id}; DELETE FROM report_letters WHERE ID=${id} AND ${owned}`);
      h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM log_letters WHERE report_id=${id})
        +(SELECT COUNT(*) FROM document WHERE docdesc=${h.sqlString(`${id}-${name}`)})`) === '0', 'Letter children remain');
    }
    h.assert(s.sql.value(`SELECT COUNT(*) FROM report_letters WHERE ${owned}`) === '0', 'Owned templates remain');
  });
  async function letters() {
    const menu = await openPrintMenu(s.master, 20000);
    const page = await s.popup(s.master, menu.locator('a[href*="/report/ViewGenerateLetters?"]'), 'patient-letters');
    await page.locator('#listDemographic').waitFor();
    h.assert(await page.locator('input[name="demos"]').count() === 1
      && await page.locator('input[name="demos"]').inputValue() === s.patient, 'Letters did not select only the owned patient');
    return page;
  }
  const manager = await letters();
  await ui.clickAndAwaitReload(manager, manager.locator('a[href$="/report/ViewManageLetters"]'), { label: 'Manage Letters' });
  for (const label of ['target', 'control']) {
    const name = `${s.marker}-${label}`;
    const filename = `${name}.jrxml`;
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<jasperReport name="PwLetter${s.marker.slice(-16)}${label}" pageWidth="595" pageHeight="842" columnWidth="555"
  leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20" whenNoDataType="AllSectionsNoDetail">
  <title height="40"><element kind="staticText" x="0" y="0" width="555" height="20">
    <text><![CDATA[${name} patient letter]]></text>
  </element></title>
</jasperReport>`;
    await manager.locator('input[name="reportFile"]').setInputFiles({ name: filename, mimeType: 'text/xml', buffer: Buffer.from(xml) });
    await manager.locator('input[name="reportName"]').fill(name);
    await ui.clickAndAwaitReload(manager, manager.locator('form[action$="/report/ManageLetters"] input[type="submit"]'),
      { label: 'Upload letter template' });
    await h.assertNotErrorPage(manager, 'letter template upload');
    const id = s.sql.value(`SELECT ID FROM report_letters WHERE ${owned} AND report_name=${h.sqlString(name)}`);
    h.assert(/^[1-9]\d*$/.test(id), 'Upload did not store one owned template');
    templates.push({ id, name, filename, xml });
    // Upload returns to Generate Letters. Follow its real Manage Letters link
    // before uploading the control or operating the library row actions.
    if (await manager.locator('input[name="reportFile"]').count() === 0) {
      await ui.clickAndAwaitReload(manager, manager.locator('a[href$="/report/ViewManageLetters"]'),
        { label: 'Return to Manage Letters after Upload' });
    }
  }
  const row = template => manager.locator('tr').filter({ has: manager.locator(`input[name="reportID"][value="${template.id}"]`) }).last();
  async function archive(template) {
    await ui.clickAndAwaitReload(manager, row(template).locator('form[action$="/report/DeleteLetter"] button[type="submit"]'),
      { label: 'Delete letter template' });
    await h.assertNotErrorPage(manager, 'Delete letter template');
  }
  return { store, manager, templates, row, archive, letters };
}
module.exports = { preparePatientLetters };
