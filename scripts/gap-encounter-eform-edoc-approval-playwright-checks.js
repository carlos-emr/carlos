#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * eForm "Add to Documents" when the PDF cannot be rendered completely: the missing-content page and
 * the one-time approval that files it anyway.
 *
 * User path: Schedule ▸ Master Record ▸ E-Chart ▸ eForms "+" ▸ an eForm whose page script throws ▸ Subject ▸ toolbar "Add to Documents" (AddEForm2Action saveAsEdoc) ▸ the missing-content page
 * (EFormRenderMissingContent) ▸ "Approve and add to documents" (POST eform/saveEFormAsEDoc, which
 * carries the one-time renderApproval token) ▸ the patient's Documents. eform-apcache-renderer covers
 * the same page for the Download button; the eDocument branch had no check.
 * Asserts against MariaDB and the document store: the refused render saves the eForm instance but files
 * NO document; the page reports the severe script error and carries a renderApproval token and the
 * saved fdid; approving files exactly one active eForm PDF under the patient with the subject as its
 * description and a stored real PDF; the saved eForm is not duplicated by the approval; and replaying
 * the same approval (the token is one-time) files nothing more.
 * Fixtures: an owned eForm template (an inline script that throws a marker error), the owned patient;
 * cleanup deletes the instance(s), the filed document, its stored PDF and the template, asserting each.
 * Needs DOCUMENT_DIR. Implements gap-encounter "file an incomplete eForm as an eDocument by approval".
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, patient, provider, config } = s;
  const store = process.env.DOCUMENT_DIR;
  if (!store || !fs.existsSync(store)) throw new h.SkipCheck('DOCUMENT_DIR (the document store) is not readable here');
  const formName = `${marker} edoc approval`;
  const subject = `${marker} approval subject`;
  // An uncaught page-script error is what makes the renderer's completeness gate withhold the PDF (a missing
  // image does not: the packaged renderer tolerates it). The error is deliberate; the check consumes exactly it.
  const scriptError = `FIXTURE-${marker.slice(-8)}`;
  const html = '<html><head><title>approval fixture</title></head><body>'
    + '<form method="post" action="" name="FormName" id="FormName">'
    + `<h2>${marker.replace(/-/g, '')}APPROVALTEXT</h2><script>throw new Error('${scriptError}');</script>`
    + '<input type="text" name="subject" id="subject"><input type="submit" value="Submit" id="SubmitButton"></form></body></html>';
  const files = [];
  let fid;
  const documents = () => sql.rows(`SELECT d.document_no, d.docfilename FROM document d JOIN ctl_document c ON c.document_no=d.document_no
    WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc=${q(subject)}`);
  const instances = () => sql.value(`SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)}`);
  s.cleanup(() => {
    const fdids = sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)}`).map(r => r[0]);
    for (const [no, file] of documents()) {
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
      + (SELECT COUNT(*) FROM document WHERE docdesc=${q(subject)})`) === '0', 'Owned eForm and document rows were not removed');
  });
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'','approval fixture',CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(fid), 'The eForm template fixture was not created');
  // The fixture's deliberate script error (and nothing else) is consumed from the strict recorder; the
  // page raises it again whenever the eForm is redisplayed.
  const consumeScriptErrors = () => {
    const expected = s.recorder.pageErrors.filter(entry => entry.text.includes(scriptError));
    for (const entry of expected) s.recorder.pageErrors.splice(s.recorder.pageErrors.indexOf(entry), 1);
    return expected.length;
  };
  const chart = await s.chart();
  let form;
  let fdid;
  let approvalToken;
  let csrfToken;

  await s.step('Add to Documents on an eForm whose script throws saves it but files nothing and shows the missing-content page', async () => {
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
    form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-fill');
    h.assert(consumeScriptErrors() >= 1, 'The fixture script error was not raised by the eForm page');
    await form.locator('#remote_eform_subject').fill(subject);
    await form.locator('#remoteSaveEdocumentButton').click();
    await expectValue(sql, `SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)}`, '1',
      'The eForm instance was not saved');
    const outcome = await Promise.race([
      form.locator('input[name="renderApproval"]').waitFor({ state: 'attached', timeout: 90000 }).then(() => 'approval'),
      new Promise(resolve => form.once('close', () => resolve('closed'))),
    ]);
    h.assert(outcome === 'approval', `The save did not stop at the missing-content page (${outcome}; ${documents().length} document(s) filed)`);
    h.assert(documents().length === 0, 'A document was filed although the render was incomplete and not yet approved');
    fdid = sql.value(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND form_name=${q(formName)}`);
    h.assert(await form.locator('input[name="fdid"]').inputValue() === fdid, 'The approval page does not carry the saved fdid');
    approvalToken = await form.locator('input[name="renderApproval"]').inputValue();
    h.assert(approvalToken.length > 20, 'The approval page carries no one-time approval token');
    // CSRFGuard injects the session token into the approval form; the replay probe below reuses it so the
    // POST reaches the action instead of being turned away by the CSRF filter.
    csrfToken = await form.locator('form:has(input[name="renderApproval"]) input[name="CSRF-TOKEN"]').first().inputValue();
    h.assert(csrfToken.length > 8, 'The approval form carries no CSRF token for the replay probe');
    h.assert(/eform\/saveEFormAsEDoc$/.test(await form.locator('form:has(input[name="renderApproval"])').getAttribute('action')),
      'The approval form does not post to the save-as-eDocument route');
    const text = (await form.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(/Page script errors:\s*[1-9]/.test(text), 'The approval page does not report the page script error');
    consumeScriptErrors();
  });

  await s.step('Approve and add to documents files exactly one eForm PDF without duplicating the saved eForm', async () => {
    const approved = form.waitForResponse(r => /\/eform\/saveEFormAsEDoc$/.test(new URL(r.url()).pathname) && r.request().method() === 'POST',
      { timeout: 90000 });
    approved.catch(() => {});
    await form.locator('form:has(input[name="renderApproval"]) button[type="submit"]').click();
    const response = await approved;
    h.assert(response.status() < 400, `Approving answered HTTP ${response.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM document d JOIN ctl_document c ON c.document_no=d.document_no
      WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc=${q(subject)} AND d.status='A'`, '1',
      'Approving did not file exactly one active document for the patient');
    const [row] = sql.rows(`SELECT d.document_no, d.docfilename, d.doctype, d.contenttype FROM document d JOIN ctl_document c ON c.document_no=d.document_no
      WHERE c.module='demographic' AND c.module_id=${patient} AND d.docdesc=${q(subject)}`);
    files.push(row[1]);
    h.assert(row[2] === 'eForm' && row[3] === 'application/pdf', 'The approved document is not an eForm PDF');
    const target = path.join(store, path.basename(row[1]));
    h.assert(path.basename(row[1]) === row[1] && fs.existsSync(target), 'The approved eDocument has no stored file');
    h.assert(fs.readFileSync(target).subarray(0, 5).toString('latin1') === '%PDF-', 'The approved eDocument is not a PDF');
    h.assert(instances() === '1', 'Approving duplicated the saved eForm instance');
    consumeScriptErrors();
  });

  await s.step('replaying the same approval files nothing more', async () => {
    const before = documents().length;
    // Negative probe after the real path: the same one-time token, posted again with the session's valid CSRF
    // token, so the rejection under test is the action's own and not the CSRF filter's.
    const response = await s.context.request.post(`${String(config.baseUrl).replace(/\/$/, '')}/eform/saveEFormAsEDoc`,
      { form: { fdid, demographicNo: patient, parentAjaxId: 'eforms', renderApproval: approvalToken, 'CSRF-TOKEN': csrfToken }, headers: { 'CSRF-TOKEN': csrfToken } });
    const status = response.status();
    const body = await response.text();
    await response.dispose();
    h.assert(status === 200, `The replayed approval was answered HTTP ${status} (a CSRF or gateway refusal, not the action's own answer)`);
    h.assert(/approvalExpired|no longer valid/i.test(body.replace(/&[a-z#0-9]+;/gi, ' ')),
      'The replayed approval was not answered with the approval-expired response');
    h.assert(documents().length === before && before === 1, 'A replayed approval token filed another document');
    consumeScriptErrors();
  });
}

if (require.main === module) runWorkflow('gap-encounter-eform-edoc-approval', workflow, { openPatient: true });
module.exports = { workflow };
