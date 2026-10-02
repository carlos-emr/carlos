#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * eForm floating toolbar "Add to Documents": save the filled eForm and file its PDF as an eDocument.
 *
 * User path: Schedule ▸ Master Record ▸ E-Chart ▸ eForms "+" (Add eForm list) ▸ the owned eForm ▸
 * Subject and a field ▸ toolbar "Add to Documents" (#remoteSaveEdocumentButton, AddEForm2Action
 * saveAsEdoc) ▸ E-Chart ▸ Documents (the patient's eDocument report) ▸ the new document.
 * Asserts against MariaDB and the document store: the eForm instance is saved (eform_data subject
 * and form_data, eform_values field), one document row (doctype eForm, the subject as description,
 * application/pdf, active, filed under the patient in ctl_document, responsible provider), a stored
 * file in the document directory that is a real PDF carrying the eForm's text, and that the E-Chart
 * Documents report lists the filed document and opens it. The save's result page replaces the form and
 * closes its window, so the same page cannot be submitted a second time from the UI (AddEForm2Action's
 * "same form, not saved" guard is dead code); a second fill is a new instance and is not asserted here.
 * Fixtures: one marker-named eForm template, the owned synthetic patient. Cleanup deletes the saved
 * instance, the document row, its ctl_document link, its stored file and the template, and asserts it.
 * Needs DOCUMENT_DIR (the server's document store, readable by the check). Implements gap-encounter
 * "file an eForm as an eDocument" (the toolbar button had no check; saveAsEdoc is not driven anywhere).
 */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { requirePoppler } = require('./lib/stored-pdf-documents');

const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const store = process.env.DOCUMENT_DIR;
  if (!store || !fs.existsSync(store)) throw new h.SkipCheck('DOCUMENT_DIR (the document store) is not readable here');
  const formName = `${marker} eDoc form`;
  const bodyText = `${marker.replace(/-/g, '')}BODYTEXT`;
  // Short: a text input prints only as many characters as its width shows.
  const typed = `TN${marker.slice(-10)}`;
  const subject = `${marker} filed subject`;
  const html = '<html><head><title>eDoc fixture</title></head><body>'
    + `<form method="post" action="" name="FormName" id="FormName"><h2>${bodyText}</h2>`
    + '<input type="text" name="subject" id="subject"><input type="text" name="note" id="note">'
    + '<input type="submit" value="Submit" id="SubmitButton"></form></body></html>';
  const files = [];
  let fid;
  s.cleanup(() => {
    const fdids = sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)}`).map(r => r[0]);
    const docs = sql.rows(`SELECT d.document_no, d.docfilename FROM document d JOIN ctl_document c ON c.document_no=d.document_no
      WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc LIKE ${q(`${marker}%`)}`);
    for (const [no, file] of docs) {
      sql.execute(`DELETE FROM ctl_document WHERE document_no=${no} AND module_id=${patient}; DELETE FROM document WHERE document_no=${no}`);
      files.push(file);
    }
    for (const file of new Set(files)) {
      const target = path.join(store, path.basename(file));
      if (path.basename(file) === file && file.startsWith('eform-') && fs.existsSync(target)) fs.unlinkSync(target);
      h.assert(!fs.existsSync(target), 'The filed eForm PDF was not removed from the document store');
    }
    for (const fdid of fdids) sql.execute(`DELETE FROM eform_values WHERE fdid=${fdid}; DELETE FROM eform_data WHERE fdid=${fdid} AND demographic_no=${patient}`);
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM document WHERE docdesc LIKE ${q(`${marker}%`)})`) === '0', 'Owned eForm and document rows were not removed');
  });
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'','eDoc fixture',CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'The eForm template fixture was not created');
  const chart = await s.chart();
  let form;
  let documentNo;
  let docFile;

  await s.step('E-Chart ▸ eForms "+" ▸ the owned eForm opens with the floating toolbar and an Add to Documents button', async () => {
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
    form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-fill');
    await form.locator('#remoteSaveEdocumentButton').waitFor({ state: 'visible' });
    h.assert(await form.locator('#remoteSaveEdocumentButton').isEnabled(), 'Add to Documents is disabled');
    // The Add eForm list stays open: unloading it throws (null window.opener, known defect covered by eform-groups).
  });

  await s.step('Add to Documents saves the instance and answers success', async () => {
    await form.locator('#remote_eform_subject').fill(subject);
    await form.locator('#note').fill(typed);
    await form.locator('#remoteSaveEdocumentButton').click();
    await expectValue(sql, `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)}`, '1',
      'Add to Documents did not save the eForm instance');
    await form.getByText('saved successfully').waitFor({ timeout: 90000 });
    h.assert(sql.value(`SELECT subject FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)}`) === subject,
      'The saved instance lost the subject typed in the toolbar');
    const fdid = sql.value(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)}`);
    h.assert(sql.value(`SELECT var_value FROM eform_values WHERE fdid=${fdid} AND var_name='note'`) === typed,
      'The typed field was not stored in eform_values');
    h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid=${fdid} AND form_data LIKE ${q(`%${bodyText}%`)}`) === '1',
      'The stored form_data does not carry the eForm markup');
  });

  await s.step('exactly one eDocument row is filed for the patient with the subject, PDF type and responsible provider', async () => {
    await expectValue(sql, `SELECT COUNT(*) FROM document d JOIN ctl_document c ON c.document_no=d.document_no
      WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc=${q(subject)}`, '1',
      'Add to Documents did not file exactly one eDocument for the patient');
    const [row] = sql.rows(`SELECT d.document_no, d.doctype, d.contenttype, d.status, d.responsible, d.docfilename, c.status, d.doccreator
      FROM document d JOIN ctl_document c ON c.document_no=d.document_no
      WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc=${q(subject)}`);
    [documentNo, , , , , docFile] = row;
    h.assert(row[1] === 'eForm' && row[2] === 'application/pdf' && row[3] === 'A' && row[6] === 'A',
      'The eDocument row is not an active eForm PDF linked to the patient');
    h.assert(row[4] === provider, 'The eDocument is not attributed to the saving provider');
    h.assert(row[7] === provider, 'The eDocument creator is not the authenticated saving provider');
    files.push(docFile);
  });

  await s.step('the stored file is a real PDF that carries the eForm text', async () => {
    h.assert(path.basename(docFile) === docFile, 'The document file name escapes the store');
    const target = path.join(store, docFile);
    h.assert(fs.existsSync(target), 'The filed eDocument has no file in the document store');
    const bytes = fs.readFileSync(target);
    h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The filed eDocument is not a PDF');
    const text = execFileSync('pdftotext', [target, '-'], { encoding: 'utf8' });
    h.assert(text.includes(bodyText), 'The filed PDF does not carry the eForm text');
    h.assert(text.includes(typed), 'The filed PDF does not carry the value typed into the eForm');
  });

  await s.step('E-Chart ▸ Documents lists the filed document and opens it', async () => {
    // The report popup reads the document list from the database, so the open chart needs no reload.
    const fresh = await s.chart();
    await fresh.locator('#menuTitledocs').waitFor();
    const report = await s.popup(fresh, fresh.locator('#menuTitledocs a').first(), 'edoc-report');
    const row = report.getByText(subject, { exact: false }).first();
    await row.waitFor({ timeout: 20000 });
    const link = report.locator(`a[href*="doc_no=${documentNo}"], a[onclick*="doc_no=${documentNo}"], a[onclick*="${documentNo}"]`).first();
    h.assert(await link.count() === 1, 'The report row has no link that opens the filed document');
    // A PDF opens in a window with no HTML body, which the popup helper reads as a blank page.
    const [viewer] = await Promise.all([s.context.waitForEvent('page', { timeout: 20000 }), link.click()]);
    await viewer.waitForURL(url => String(url) !== 'about:blank', { waitUntil: 'commit' });
    h.assert(viewer.url().includes(`doc_no=${documentNo}`), 'The report row opened another document');
    // Headless Chromium has no PDF viewer, so read the viewer's own destination and compare its bytes
    // with the stored file: the document the clinician opens is the PDF that was filed.
    const response = await s.context.request.get(viewer.url(), { timeout: 20000 });
    h.assert(response.status() === 200 && /^application\/pdf/i.test(response.headers()['content-type'] || ''),
      'The Documents viewer did not answer with the filed PDF');
    const served = await response.body();
    h.assert(served.equals(fs.readFileSync(path.join(store, docFile))), 'The viewer served bytes other than the filed PDF');
    h.assert(sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${documentNo} AND status='A'`) === '1',
      'Opening the filed document changed it');
  });
}

if (require.main === module) {
  runWorkflow('gap-encounter-eform-save-as-edoc', workflow,
    { openPatient: true, preflight: () => requirePoppler('pdftotext') });
}
module.exports = { workflow };
