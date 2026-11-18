#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// eform-template-consult: the consultation request an eForm's action template writes when the eForm is submitted
// (the <consultRequest> part of the <!-- <template> ... </template> --> block that AddEForm2Action ▸
// EFormUtil.writeEformTemplate runs). eform-template-outputs covers the template's message, document and tickler;
// eform-template-provider its preventions and notes.
// User path: E-Chart ▸ eForms ▸ Add ▸ the fixture eForm ▸ Submit, then Schedule ▸ Consultations ▸ the request.
// The template asks for a Return request (urgency 3) with status 2, Pending specialist callback, to the fixture
// service and specialist, referred by the login:
//   1. the eForm is saved once and the template writes one request for the patient, tied to the saved eForm;
//   2. the request has the service, specialist, referring provider, reason and urgency the template names, and the
//      Consultations list and the request screen show them;
//   3. the request has the status the template names;
//   4. LAST: a second eForm, whose template has an encounter note and a consultation request with no specialist,
//      is saved with its note and Submit does not fail; no request is made for it.
// No other check submits an eForm with a consultation request template.
// Fixtures: the owned FAKE- patient, a marker-named consultation service and specialist (linked), and two marker-named
// eForms in the clinic's eForm library (status 1). Cleanup deletes the eForms and their saved copies, the request the
// submission wrote, the owned patient's notes with their links, and the service and specialist, and asserts none
// remain. The eForm library is clinic-wide
// (EXCLUSIVE), and so are the consultation service and specialist lists.
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;
const POSITIVE = /^[1-9]\d*$/;
// consultationRequests codes, as ConsultationFormRequest.jsp offers them.
const RETURN = '3';
const PENDING_SPECIALIST_CALLBACK = '2';

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
  const tag = marker.slice(-6);
  const formName = `${marker} consult`;
  const serviceDesc = `FAKE-Svc ${tag}`;
  const lastName = `FAKE-Spec ${tag}`;
  const reason = `${marker} reason for referral`;
  const formNameNoSpecialist = `${marker} consult without specialist`;
  const reasonNoSpecialist = `${marker} referral without specialist`;
  const noteText = `${marker} template note`;
  const formNames = `${q(formName)},${q(formNameNoSpecialist)}`;
  let fid;
  let fidNoSpecialist;
  let serviceId;
  let specId;

  const ownedRequests = () => `demographicNo=${patient} AND reason=${q(reason)}`;
  const anyOwnedRequest = `demographicNo=${patient} AND reason IN (${q(reason)},${q(reasonNoSpecialist)})`;
  s.cleanup(() => {
    const requests = sql.rows(`SELECT requestId FROM consultationRequests WHERE ${anyOwnedRequest}`).map(([id]) => id);
    h.assert(requests.every(id => POSITIVE.test(id)), 'Owned consultation request id is invalid');
    if (requests.length) {
      sql.execute(`DELETE FROM consultationRequests WHERE requestId IN (${requests.join(',')}) AND ${anyOwnedRequest}`);
    }
    // The second eForm's template writes an encounter note for the owned patient.
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(([id]) => id);
    h.assert(notes.every(id => POSITIVE.test(id)), 'Owned note id is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM eform_values WHERE fdid IN (SELECT fdid FROM eform_data WHERE form_name IN (${formNames}) AND demographic_no=${patient});
      DELETE FROM eform_data WHERE form_name IN (${formNames}) AND demographic_no=${patient}`);
    if (fid) sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
    if (fidNoSpecialist) sql.execute(`DELETE FROM eform WHERE fid=${fidNoSpecialist} AND form_name=${q(formNameNoSpecialist)}`);
    if (serviceId && specId) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${serviceId} AND specId=${specId}`);
    if (specId) sql.execute(`DELETE FROM professionalSpecialists WHERE specId=${specId} AND lName=${q(lastName)}`);
    if (serviceId) sql.execute(`DELETE FROM consultationServices WHERE serviceId=${serviceId} AND serviceDesc=${q(serviceDesc)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name IN (${formNames}))
      + (SELECT COUNT(*) FROM eform_data WHERE form_name IN (${formNames}))
      + (SELECT COUNT(*) FROM consultationRequests WHERE reason IN (${q(reason)},${q(reasonNoSpecialist)}))
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${q(lastName)})
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${q(serviceDesc)})`) === '0',
    'Owned eForm, consultation request, note, specialist or service rows remain');
  });

  // Own service and specialist: never borrowed from the install (echart-consult-specialist explains why).
  serviceId = h.insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${q(serviceDesc)},'1')`, 'service');
  specId = h.insertId(sql, `INSERT INTO professionalSpecialists
    (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Ann',${q(lastName)},'555-0170','555-0171','3 Synthetic Way',NOW(),0,0,0,0)`, 'specialist');
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${serviceId},${specId})`);

  const template = '<!-- <template><consultRequest>'
    + `<referredToService>${serviceId}</referredToService><referredToSpecialist>${specId}</referredToSpecialist>`
    + `<referredBy>${provider}</referredBy><urgency>${RETURN}</urgency><status>${PENDING_SPECIALIST_CALLBACK}</status>`
    + `<reason>${reason}</reason><clinicalInfo>${marker} clinical information</clinicalInfo>`
    + '</consultRequest></template> -->';
  const html = `<html><head><title>${formName}</title></head><body>`
    + '<form method="post" action="" name="FormName" id="FormName"><input type="text" name="note" id="note">'
    + `<input type="submit" value="Submit" id="SubmitButton"></form>${template}</body></html>`;
  fid = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formName)},'',${q(formName)},CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
  h.assert(POSITIVE.test(fid || ''), 'The template eForm was not created');
  const templateNoSpecialist = '<!-- <template>'
    + `<EncounterNote>${noteText}</EncounterNote>`
    + `<consultRequest><referredToService>${serviceId}</referredToService><referredBy>${provider}</referredBy>`
    + `<reason>${reasonNoSpecialist}</reason></consultRequest></template> -->`;
  const htmlNoSpecialist = `<html><head><title>${formNameNoSpecialist}</title></head><body>`
    + '<form method="post" action="" name="FormName" id="FormName"><input type="text" name="note" id="note">'
    + `<input type="submit" value="Submit" id="SubmitButton"></form>${templateNoSpecialist}</body></html>`;
  fidNoSpecialist = sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
    showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(formNameNoSpecialist)},'',${q(formNameNoSpecialist)},CURDATE(),CURTIME(),${q(provider)},1,${q(htmlNoSpecialist)},0,0,'',0,1);
    SELECT LAST_INSERT_ID()`);
  h.assert(POSITIVE.test(fidNoSpecialist || ''), 'The template eForm without a specialist was not created');
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
  let requestId;
  let request;
  let listRow;
  let list;
  await s.step('submitting the eForm saves it and runs its consultation request template', async () => {
    // The Add eForm list is never closed from here: unloading it throws on a null window.opener (known, eform-groups).
    list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
    await list.locator('#efmTable').waitFor();
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-template');
    await form.locator('#remoteSubmitButton').waitFor({ state: 'visible' });
    await form.locator('#note').fill(`${marker} submitted`);
    await form.locator('#remoteSubmitButton').click();
    const saved = await waitForValue(sql, `SELECT GROUP_CONCAT(fdid) FROM eform_data WHERE form_name=${q(formName)} AND demographic_no=${patient}`,
      v => !!v);
    h.assert(POSITIVE.test(saved || ''), `The eForm was not saved once: ${saved}`);
    requestId = await waitForValue(sql, `SELECT GROUP_CONCAT(requestId) FROM consultationRequests WHERE ${ownedRequests()}`, v => !!v);
    const stored = sql.value(`SELECT CONCAT_WS('|',serviceId,specId,providerNo,urgency,status,IFNULL(fdid,'none'),sendTo)
      FROM consultationRequests WHERE ${ownedRequests()} LIMIT 1`);
    console.log(`    written: request ${requestId} (service|specialist|provider|urgency|status|eForm|send to) ${stored}`);
    h.assert(POSITIVE.test(requestId || ''), `The template did not write one consultation request: ${requestId}`);
    h.assert(sql.value(`SELECT fdid FROM consultationRequests WHERE requestId=${requestId}`) === saved,
      'The consultation request is not tied to the saved eForm');
    if (!form.isClosed()) await form.close();
  });

  await s.step('the request names the template\'s service, specialist, referring provider, reason and urgency', async () => {
    const stored = sql.value(`SELECT CONCAT_WS('|',serviceId,specId,providerNo,urgency) FROM consultationRequests WHERE requestId=${requestId}`);
    h.assert(stored === `${serviceId}|${specId}|${provider}|${RETURN}`,
      `The request was stored as ${stored} (service|specialist|provider|urgency)`);
    const { page: list } = await clickOpensPopupOrNavigates(s.schedule,
      s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
      { context: s.context, recorder: s.recorder, label: 'consultations', timeout: 20000 });
    listRow = list.locator(`tr[onclick*="requestId=${requestId}'"]`);
    await listRow.waitFor({ timeout: 20000 });
    const rowText = (await listRow.innerText()).replace(/\s+/g, ' ');
    h.assert(rowText.includes(serviceDesc), `The Consultations row does not name the service: ${JSON.stringify(rowText)}`);
    h.assert(/\bReturn\b/.test(await listRow.locator('td').nth(1).innerText()), 'The Consultations row does not show the urgency Return');
    request = await s.popup(list, listRow, 'consultation-request');
    await request.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    h.assert(await request.locator('[name="reasonForConsultation"]').inputValue() === reason,
      'The request screen does not show the template\'s reason');
    h.assert(await request.locator('#urgency').inputValue() === RETURN, 'The request screen does not show the urgency Return');
  });

  await s.step('the request has the status the template names (Pending specialist callback)', async () => {
    const status = sql.value(`SELECT status FROM consultationRequests WHERE requestId=${requestId}`);
    const shown = await request.locator('input[name="status"]:checked').getAttribute('value').catch(() => 'none');
    const listed = await listRow.locator('td').first().getAttribute('class');
    h.assert(status === PENDING_SPECIALIST_CALLBACK && shown === PENDING_SPECIALIST_CALLBACK
      && listed === `consult-status-${PENDING_SPECIALIST_CALLBACK}`,
      `The request has status ${status} saved, ${shown} on the request screen and ${listed} in the Consultations list, `
      + `not ${PENDING_SPECIALIST_CALLBACK} (Pending specialist callback)`);
  });

  await s.step('an eForm whose consultation template names no specialist is saved with its note, and Submit does not fail', async () => {
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formNameNoSpecialist }).first(),
      'eform-template-no-specialist');
    await form.locator('#remoteSubmitButton').waitFor({ state: 'visible' });
    await form.locator('#note').fill(`${marker} submitted without a specialist`);
    const answered = form.waitForResponse(r => r.url().includes('/eform/addEForm') && r.request().method() === 'POST',
      { timeout: 30000 });
    await form.locator('#remoteSubmitButton').click();
    const status = (await answered).status();
    const saved = await waitForValue(sql, `SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formNameNoSpecialist)}
      AND demographic_no=${patient}`, v => v === '1');
    const note = await waitForValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND note LIKE ${q(`%${noteText}%`)}`, v => v !== '0');
    const requests = sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient} AND reason=${q(reasonNoSpecialist)}`);
    h.assert(status === 200 && saved === '1' && note === '1' && requests === '0',
      `Submit answered ${status}; the eForm was saved ${saved} time(s), its template note ${note} time(s), and ${requests} `
      + 'consultation request(s) were made (expected 200, 1, 1, 0)');
    if (!form.isClosed()) await form.close();
  });
}

if (require.main === module) runWorkflow('eform-template-consult', workflow, { openPatient: true });
module.exports = { workflow };
