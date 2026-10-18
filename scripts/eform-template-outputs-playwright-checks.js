#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// eform-template-outputs: what an eForm's action template sends out when the eForm is submitted (the
// <!-- <template> ... </template> --> block that AddEForm2Action ▸ EFormUtil.writeEformTemplate runs): a message,
// a tickler and a patient document. eform-template-provider covers the template's preventions and notes.
// User path: E-Chart ▸ eForms ▸ Add ▸ the fixture eForm ▸ Submit. The template asks for
//   1. a <message> to the login: it is in the login's Messenger inbox (schedule ▸ Msg);
//   2. a <document belong="patient">: it is filed under the patient and listed in the E-Chart's Documents;
//   3. a <tickler> assigned to the login: it is on the patient's Tickler list (master record ▸ Tickler).
// No other check submits an eForm with these templates.
// Fixtures: the owned FAKE- patient and a marker-named eForm in the clinic's eForm library (status 1). Cleanup deletes
// the eForm and its saved copy, and the message, tickler and document the submission wrote (with the document's
// chart-note and queue rows, lib/document-residue.js), and asserts none remain. The eForm library is clinic-wide
// (EXCLUSIVE).
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { markDocumentResidue, removeDocumentResidue } = require('./lib/document-residue');
const { masterRecordLink } = require('./lib/master-record-link');

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
  const formName = `${marker} outputs`;
  const subject = `${marker} message`;
  const ticklerText = `${marker} tickler`;
  const docDesc = `${marker} document`;
  const residue = markDocumentResidue(sql);
  let fid;

  const messageIds = () => sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject=${q(subject)}`).map(([id]) => id);
  const ownedTicklers = `demographic_no=${patient} AND message=${q(ticklerText)}`;
  // By the marker-named description alone: the template's document is the run's own wherever it was filed, so the
  // cleanup still finds it (and the final count stays honest) if the filing module or patient is not what was expected.
  const documentIds = () => sql.rows(`SELECT document_no FROM document WHERE docdesc=${q(docDesc)}`).map(([id]) => id);
  s.cleanup(() => {
    const messages = messageIds();
    h.assert(messages.every(id => POSITIVE.test(id)), 'Owned message id is invalid');
    if (messages.length) {
      sql.execute(`DELETE FROM messagelisttbl WHERE message IN (${messages.join(',')});
        DELETE FROM msgDemoMap WHERE messageID IN (${messages.join(',')});
        DELETE FROM messagetbl WHERE messageid IN (${messages.join(',')}) AND thesubject=${q(subject)}`);
    }
    const ticklers = sql.rows(`SELECT tickler_no FROM tickler WHERE ${ownedTicklers}`).map(([id]) => id);
    h.assert(ticklers.every(id => POSITIVE.test(id)), 'Owned tickler id is invalid');
    if (ticklers.length) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ticklers.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ticklers.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ticklers.join(',')});
        DELETE FROM tickler WHERE ${ownedTicklers} AND tickler_no IN (${ticklers.join(',')})`);
    }
    const documents = documentIds();
    h.assert(documents.every(id => POSITIVE.test(id)), 'Owned document id is invalid');
    if (documents.length) {
      // An eForm's document keeps its HTML in the row (docxml). Its docfilename is a generated label
      // (a timestamp and "html"); no file is written to the document store, so there is none to remove.
      sql.execute(`DELETE FROM ctl_document WHERE document_no IN (${documents.join(',')});
        DELETE FROM document WHERE document_no IN (${documents.join(',')}) AND docdesc=${q(docDesc)}`);
      removeDocumentResidue(sql, residue, documents);
    }
    sql.execute(`DELETE FROM eform_values WHERE fdid IN (SELECT fdid FROM eform_data WHERE form_name=${q(formName)} AND demographic_no=${patient});
      DELETE FROM eform_data WHERE form_name=${q(formName)} AND demographic_no=${patient}`);
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)})
      + (SELECT COUNT(*) FROM messagetbl WHERE thesubject=${q(subject)})
      + (SELECT COUNT(*) FROM tickler WHERE ${ownedTicklers})
      + (SELECT COUNT(*) FROM document WHERE docdesc=${q(docDesc)})`) === '0', 'Owned eForm, message, tickler or document rows remain');
  });

  const template = '<!-- <template>'
    + `<message><subject>${subject}</subject><sendto>${provider}</sendto><content>${marker} message body</content></message>`
    + `<document belong="patient"><docdesc>${docDesc}</docdesc><content>${marker} document body</content></document>`
    + `<tickler><taskAssignedTo>${provider}</taskAssignedTo><tickMsg>${ticklerText}</tickMsg></tickler>`
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

  const chart = await s.chart();
  const written = {};
  await s.step('submitting the eForm saves it and runs its message, document and tickler templates', async () => {
    // The Add eForm list is never closed from here: unloading it throws on a null window.opener (known, eform-groups).
    const list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-template');
    await form.locator('#remoteSubmitButton').waitFor({ state: 'visible' });
    await form.locator('#note').fill(`${marker} submitted`);
    await form.locator('#remoteSubmitButton').click();
    const saved = await waitForValue(sql, `SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)} AND demographic_no=${patient}`, v => v === '1');
    h.assert(saved === '1', `The eForm was saved ${saved} time(s)`);
    written.message = await waitForValue(sql, `SELECT GROUP_CONCAT(messageid) FROM messagetbl WHERE thesubject=${q(subject)}`, v => !!v);
    written.document = await waitForValue(sql, `SELECT GROUP_CONCAT(d.document_no) FROM document d JOIN ctl_document c ON c.document_no=d.document_no
      WHERE d.docdesc=${q(docDesc)}`, v => !!v);
    written.tickler = await waitForValue(sql, `SELECT GROUP_CONCAT(tickler_no) FROM tickler WHERE ${ownedTicklers}`, v => !!v);
    console.log(`    written: ${JSON.stringify(written)}`);
    h.assert(POSITIVE.test(written.message || '') && POSITIVE.test(written.document || '') && POSITIVE.test(written.tickler || ''),
      `The template did not write one message, one document and one tickler: ${JSON.stringify(written)}`);
    if (!form.isClosed()) await form.close();
  });

  await s.step('the message is delivered to the login and listed in its Messenger inbox', async () => {
    const delivered = sql.value(`SELECT CONCAT(m.sentbyNo,'|',l.provider_no,'|',l.status) FROM messagetbl m
      JOIN messagelisttbl l ON l.message=m.messageid WHERE m.messageid=${written.message}`);
    h.assert(delivered === `${provider}|${provider}|new`, `The message was delivered as ${delivered} (sender|recipient|status)`);
    const { page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: 20000 });
    await inbox.locator(`input[name="messageNo"][value="${written.message}"]`).waitFor({ timeout: 20000 });
    const row = (await inbox.locator('tr').filter({ has: inbox.locator(`input[name="messageNo"][value="${written.message}"]`) })
      .first().innerText()).replace(/\s+/g, ' ');
    h.assert(row.includes(subject), `The inbox row for the message reads ${JSON.stringify(row)}`);
    if (inbox !== s.schedule && !inbox.isClosed()) await inbox.close();
  });

  await s.step('the document is filed under the patient and listed in the E-Chart\'s Documents', async () => {
    // EFormUtil files an eForm's document with status H, an HTML document (the Documents report edits it as HTML).
    const filed = sql.value(`SELECT CONCAT(c.module,'|',c.module_id,'|',d.contenttype,'|',d.status) FROM document d
      JOIN ctl_document c ON c.document_no=d.document_no WHERE d.document_no=${written.document}`);
    h.assert(filed === `demographic|${patient}|text/html|H`, `The document was filed as ${filed} (module|owner|type|status)`);
    await chart.locator('#menuTitledocs').waitFor();
    const report = await s.popup(chart, chart.locator('#menuTitledocs a').first(), 'document-report');
    await report.getByText(docDesc, { exact: false }).first().waitFor({ timeout: 20000 });
    await report.close();
  });

  await s.step('the tickler is assigned to the login and listed on the patient\'s Tickler list', async () => {
    const stored = sql.value(`SELECT CONCAT(task_assigned_to,'|',creator,'|',status,'|',IF(service_date IS NULL,'no date','dated'))
      FROM tickler WHERE tickler_no=${written.tickler}`);
    h.assert(stored === `${provider}|${provider}|A|dated`, `The tickler was stored as ${stored} (assignee|creator|status|service date)`);
    const list = await s.popup(s.master, masterRecordLink(s.master, '/tickler/ViewTicklerMain').first(), 'patient-tickler-list');
    await list.getByText(ticklerText, { exact: false }).first().waitFor({ timeout: 20000 });
    await list.close();
  });
}

if (require.main === module) runWorkflow('eform-template-outputs', workflow, { openPatient: true });
module.exports = { workflow };
