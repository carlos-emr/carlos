#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// eform-template-document-provider: the provider documents an eForm's action template files when the eForm is
// submitted (a <document> with no belong="patient" in the <!-- <template> ... </template> --> block that
// AddEForm2Action ▸ EFormUtil.writeEformTemplate runs). eform-template-outputs covers a patient's document.
// User path: E-Chart ▸ eForms ▸ Add ▸ the fixture eForm ▸ Submit, then Schedule ▸ eDoc (the login's documents).
// The template asks for two documents: one with no owner, which belongs to the login, and one whose <docowner>
// names another provider. Since the 2025 package rename (a08586ca65) the template files them under the module name
// "providers"; documents filed before it carry "provider", and the document pages read both (EDocUtil).
//   1. the eForm is saved once and the template files both documents as provider documents, each owned as asked
//      (either module spelling: the readers treat "provider" and "providers" alike, so a later rename stays green);
//   2. the login's eDoc list shows its template document and its older "provider" document, and not the
//      document the template filed for the other provider;
//   3. the template document opens with the template's content.
// No other check submits an eForm with a provider document template.
// Fixtures: the owned FAKE- patient, a marker-named eForm in the clinic's eForm library (status 1), and one
// marker-named HTML document filed for the login under the older module name "provider". The other provider is
// the lowest-numbered active provider that is not the login. Cleanup deletes the eForm and its saved copy, the
// three documents (with their chart-note and queue rows, lib/document-residue.js), and asserts none remain. The
// eForm library and the providers' document lists are clinic-wide (EXCLUSIVE).
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { markDocumentResidue, removeDocumentResidue } = require('./lib/document-residue');

const q = h.sqlString;
const POSITIVE = /^[1-9]\d*$/;

async function waitForValue(sql, query, accept, timeout = 20000) {
  // The template is written after the eForm itself, in the same request; poll briefly.
  const deadline = Date.now() + timeout;
  let value;
  do {
    value = sql.value(query);
    if (accept(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 200));
  } while (Date.now() < deadline);
  return value;
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const formName = `${marker} provider documents`;
  const mine = `${marker} login document`;
  const theirs = `${marker} other provider document`;
  const older = `${marker} older provider document`;
  const body = `${marker} template document body`;
  const residue = markDocumentResidue(sql);
  let fid;

  const other = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no REGEXP '^[1-9][0-9]*$'
    AND provider_no<>${q(provider)} ORDER BY CAST(provider_no AS UNSIGNED) LIMIT 1`);
  h.assert(POSITIVE.test(other || ''), 'No other active provider to name in the template');

  const ownedDocuments = () => sql.rows(`SELECT document_no FROM document
    WHERE docdesc IN (${q(mine)},${q(theirs)},${q(older)})`).map(([id]) => id);
  s.cleanup(() => {
    const documents = ownedDocuments();
    h.assert(documents.every(id => POSITIVE.test(id)), 'Owned document id is invalid');
    if (documents.length) {
      // HTML documents keep their content in the row (docxml); no file is written to the document store.
      sql.execute(`DELETE FROM ctl_document WHERE document_no IN (${documents.join(',')});
        DELETE FROM document WHERE document_no IN (${documents.join(',')}) AND docdesc IN (${q(mine)},${q(theirs)},${q(older)})`);
      removeDocumentResidue(sql, residue, documents);
    }
    sql.execute(`DELETE FROM eform_values WHERE fdid IN (SELECT fdid FROM eform_data WHERE form_name=${q(formName)} AND demographic_no=${patient});
      DELETE FROM eform_data WHERE form_name=${q(formName)} AND demographic_no=${patient}`);
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM document WHERE docdesc IN (${q(mine)},${q(theirs)},${q(older)}))`) === '0',
    'Owned eForm or document rows remain');
  });

  // A document the login's eForm template filed before the rename, under the module name "provider".
  const olderDocument = h.insertId(sql, `INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,
    public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime,docxml)
    VALUES ('forms',${q(older)},'html',${q(provider)},${q(provider)},'H','text/html',0,0,0,CURDATE(),NOW(),NOW(),${q(`<p>${marker} older</p>`)})`,
  'older provider document');
  sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('provider',${q(provider)},${olderDocument},'A')`);

  const template = '<!-- <template>'
    + `<document><docdesc>${mine}</docdesc><content>${body}</content></document>`
    + `<document belong="provider"><docowner>${other}</docowner><docdesc>${theirs}</docdesc><content>${marker} other body</content></document>`
    + '</template> -->';
  const html = `<html><head><title>${formName}</title></head><body>`
    + '<form method="post" action="" name="FormName" id="FormName"><input type="text" name="note" id="note">'
    + `<input type="submit" value="Submit" id="SubmitButton"></form>${template}</body></html>`;
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'',${q(formName)},CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(POSITIVE.test(fid || ''), 'The template eForm was not created');
  // Same guard as double-submit-eform: the demo data ships EFormDocs rows for eForm instance ids that no
  // eform_data row owns any more. A new instance saved at one of those ids inherits their attachments and the
  // save is refused with 403 before the template runs. Move the id counter past them with an owned row.
  const staleAttachmentHigh = Number(sql.value('SELECT COALESCE(MAX(fdid),0) FROM EFormDocs'));
  const nextInstanceId = Number(sql.value(`SELECT AUTO_INCREMENT FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='eform_data'`));
  if (nextInstanceId <= staleAttachmentHigh) {
    const seed = staleAttachmentHigh + 1;
    h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE fdid=${seed}`) === '0', 'The eForm instance id used to move the id counter is occupied');
    sql.execute(`INSERT INTO eform_data(fdid,fid,form_name,subject,demographic_no,status,form_date,form_time,form_provider,form_data,
      showLatestFormOnly,patient_independent,roleType)
      VALUES(${seed},${fid},${q(formName)},${q(`${marker}-IDSEED`)},${patient},1,CURDATE(),CURTIME(),${q(provider)},'',0,0,'')`);
    sql.execute(`DELETE FROM eform_data WHERE fdid=${seed} AND form_name=${q(formName)} AND demographic_no=${patient}`);
  }

  const filedAs = docdesc => sql.value(`SELECT CONCAT(c.module,'|',c.module_id,'|',d.contenttype,'|',d.status) FROM document d
    JOIN ctl_document c ON c.document_no=d.document_no WHERE d.docdesc=${q(docdesc)}`);
  const chart = await s.chart();
  let mineId;
  let report;
  await s.step('submitting the eForm saves it and files both template documents as provider documents, owned as asked', async () => {
    // The Add eForm list is never closed from here: unloading it throws on a null window.opener (known, eform-groups).
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-template');
    await form.locator('#remoteSubmitButton').waitFor({ state: 'visible' });
    await form.locator('#note').fill(`${marker} submitted`);
    await form.locator('#remoteSubmitButton').click();
    const saved = await waitForValue(sql, `SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)} AND demographic_no=${patient}`, v => v === '1');
    h.assert(saved === '1', `The eForm was saved ${saved} time(s)`);
    mineId = await waitForValue(sql, `SELECT GROUP_CONCAT(document_no) FROM document WHERE docdesc=${q(mine)}`, v => !!v);
    await waitForValue(sql, `SELECT COUNT(*) FROM document WHERE docdesc=${q(theirs)}`, v => v !== '0');
    const filed = { mine: filedAs(mine), theirs: filedAs(theirs) };
    console.log(`    filed (module|owner|type|status): ${JSON.stringify({ login: provider, other, ...filed })}`);
    h.assert(POSITIVE.test(mineId || ''), `The template did not file one document for the login: ${mineId}`);
    // The module spelling is not pinned: step 2 proves the login's list reads the document, whichever it is.
    const asProvider = (row, owner) => [`provider|${owner}|text/html|H`, `providers|${owner}|text/html|H`].includes(row);
    h.assert(asProvider(filed.mine, provider), `The login's template document was filed as ${filed.mine}`);
    h.assert(asProvider(filed.theirs, other), `The other provider's template document was filed as ${filed.theirs}`);
    if (!form.isClosed()) await form.close();
  });

  await s.step('the login\'s eDoc list shows its template document and its older "provider" document, not the other provider\'s', async () => {
    ({ page: report } = await ui.clickOpensPopupOrNavigates(s.schedule,
      s.schedule.locator("a[onclick*='/documentManager/ViewDocumentReport']").first(),
      { context: s.context, recorder: s.recorder, label: 'provider-documents', timeout: 20000 }));
    await report.getByText(mine, { exact: false }).first().waitFor({ timeout: 20000 });
    const text = (await report.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(older), 'The login\'s eDoc list does not show its older "provider" document');
    h.assert(!text.includes(theirs), 'The login\'s eDoc list shows the document filed for the other provider');
  });

  await s.step('the template document opens with the template\'s content', async () => {
    // The title link opens ManageDocument?method=display for the row's document in a popup.
    const title = report.locator(`a[onclick*="doc_no=${mineId}"][title]`).filter({ hasText: mine }).first();
    const shown = await s.popup(report, title, 'provider-document');
    await shown.getByText(body, { exact: false }).first().waitFor({ timeout: 20000 });
    await shown.close();
  });
}

if (require.main === module) runWorkflow('eform-template-document-provider', workflow, { openPatient: true });
module.exports = { workflow };
