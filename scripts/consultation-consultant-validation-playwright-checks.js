#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Issue #3336. Exercise the picker, bypass it with authenticated form POSTs, and
// verify rejected creates/updates preserve entered clinical data without writing history.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  let service;
  let lab;
  const specialists = [];
  s.cleanup(() => {
    sql.execute(`DELETE FROM consultdocs WHERE requestId IN
      (SELECT requestId FROM consultationRequests WHERE demographicNo=${patient});
      DELETE FROM consultationRequestExtArchive WHERE requestId IN
      (SELECT requestId FROM consultationRequests WHERE demographicNo=${patient});
      DELETE FROM consultationRequestExt WHERE requestId IN
      (SELECT requestId FROM consultationRequests WHERE demographicNo=${patient});
      DELETE FROM consultationRequestsArchive WHERE demographicNo=${patient};
      DELETE FROM consultationRequests WHERE demographicNo=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient}`);
    if (service) {
      sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${service};
        DELETE FROM consultationServices WHERE serviceId=${service}`);
    }
    if (lab) sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7';
      DELETE FROM providerLabRouting WHERE lab_no=${lab} AND lab_type='HL7';
      DELETE FROM hl7TextInfo WHERE lab_no=${lab}; DELETE FROM hl7TextMessage WHERE lab_id=${lab}`);
    for (const id of specialists) sql.execute(`DELETE FROM professionalSpecialists WHERE specId=${id}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient}`) === '0',
      'Owned consultation fixtures remain');
  });
  service = h.insertId(sql, `INSERT INTO consultationServices(serviceDesc,active)
    VALUES(${h.sqlString(marker)},'1')`, 'consultation service');
  for (const deleted of [0, 1]) {
    specialists.push(h.insertId(sql, `INSERT INTO professionalSpecialists(fName,lName,deleted)
      VALUES('Consultant',${h.sqlString(`${marker}-${deleted}`)},${deleted})`, 'specialist'));
  }
  const [active, inactive] = specialists;
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${service},${active}),(${service},${inactive})`);

  const stamp = '20261019100000';
  const message = [
    `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${stamp}||ORU^R01|${marker}|P|2.3`,
    `PID||9999999999|9999999999||${marker}^FAKE||19800101|F`,
    `OBR|1||${marker}|CHEM^Chemistry||${stamp}|${stamp}|||||||${stamp}||99999^FAKE^DOC||||||${stamp}||CHEM4|F`,
    `OBX|1|NM|2345-7^Glucose Random||5.2|mmol/L|3.3-7.7|N|||F|||${stamp}`,
  ].join('\r');
  lab = h.insertId(sql, `INSERT INTO hl7TextMessage(fileUploadCheck_id,message,type,serviceName,created)
    VALUES(0,${h.sqlString(Buffer.from(message).toString('base64'))},'PATHL7','CONSULTTEST',NOW())`, 'owned lab');
  sql.execute(`INSERT INTO hl7TextInfo(lab_no,sex,result_status,final_result_count,obr_date,priority,
      requesting_client,discipline,last_name,first_name,report_status,accessionNum)
    VALUES(${lab},'F','A',1,NOW(),'R','FAKE, DOC','CHEM',${h.sqlString(marker)},'FAKE','F',${h.sqlString(marker)});
    INSERT INTO patientLabRouting(demographic_no,lab_no,lab_type,created) VALUES(${patient},${lab},'HL7',NOW());
    INSERT INTO providerLabRouting(provider_no,lab_no,status,lab_type) VALUES(${h.sqlString(provider)},${lab},'N','HL7')`);

  const list = await s.context.newPage();
  h.wireStrictPage(list, 'patient-consultations', s.recorder);
  await list.goto(`${s.config.baseUrl}/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=${patient}`);
  let form = await s.popup(list, list.getByRole('link', { name: /New Consultation/i }), 'consultant-validation');
  const waitForm = async () => {
    await form.locator('#specialistInput').waitFor();
    await form.waitForFunction(() => typeof allSpecialistsData !== 'undefined' && allSpecialistsData.length > 0);
  };
  await waitForm();
  async function pick(selector, text) {
    await form.locator(selector).click();
    await form.locator(selector).fill(text);
    await form.locator('.ui-autocomplete:visible .ui-menu-item').filter({ hasText: text }).first().click();
  }
  const reason = `${marker} entered referral reason`;
  const clinical = `${marker} clinical information`;
  await pick('#serviceInput', marker);
  h.assert(await form.locator('#service').inputValue() === service, 'Picker selected a service outside the owned fixture');
  await form.locator('[name="reasonForConsultation"]').fill(reason);
  await form.locator('[name="clinicalInformation"]').fill(clinical);

  await s.step('browser requires a selected consultant and clears stale picker ids', async () => {
    const dialogs = await h.withExpectedDialogs(form, () => form.locator('[name="submitSaveOnly"]').click());
    h.assert(dialogs.some(d => /active consultant/.test(d.text)), 'Missing consultant was not explained');
    h.assert(await form.evaluate(id => !allSpecialistsData.some(s => String(s.specId) === id), inactive),
      'Inactive consultant remained in the picker');
    await pick('#specialistInput', `${marker}-0`);
    h.assert(await form.locator('#specialist').inputValue() === active, 'Picker did not select the active specialist');
    await form.locator('#specialistInput').fill('Changed unselected name');
    h.assert(await form.locator('#specialist').inputValue() === '', 'Edited name retained a stale consultant id');
    h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient}`) === '0',
      'Browser validation created a referral');
  });

  if (!(await form.locator('#collapseLetterhead').isVisible())) {
    await form.locator('a[href="#collapseLetterhead"]').click();
    await form.locator('#collapseLetterhead').waitFor({ state: 'visible' });
  }
  const letterhead = await form.locator('#letterheadName').evaluate(select => {
    const option = Array.from(select.options).find(option => option.value && option.value !== select.value);
    return option ? option.value : select.value;
  });
  await form.locator('#letterheadName').selectOption(letterhead);
  const letterheadFields = ['letterheadName', 'letterheadAddress', 'letterheadPhone', 'letterheadFax'];
  const savedLetterhead = {};
  for (const field of letterheadFields) savedLetterhead[field] = await form.locator(`#${field}`).inputValue();
  const willBook = await form.locator('#patientWillBook').count() > 0;
  if (willBook) await form.locator('#patientWillBook').check();
  async function openAttachments() {
    await form.locator('#attachDocumentPanelBtn').click();
    await form.locator('#attachDocumentsForm').waitFor({ state: 'visible' });
  }
  async function closeAttachments() {
    await form.locator('.ui-dialog .save-and-close-button').click();
    await form.locator('#attachDocumentsForm').waitFor({ state: 'hidden' });
  }
  await openAttachments();
  await form.locator(`#labNoHL7${lab}`).check();
  await closeAttachments();

  async function reject(id, submission, requestId) {
    const dialogs = await h.withExpectedDialogs(form, async () => {
      await Promise.all([
        form.waitForNavigation({ waitUntil: 'load' }),
        form.evaluate(({ id, submission }) => {
          const f = document.getElementById('EctConsultationFormRequest2Form');
          f.elements.specialist.value = id || '';
          f.elements.specialist.disabled = id === null;
          f.elements.submission.value = submission;
          f.requestSubmit(); // bypass the button's checkForm to exercise the server
        }, { id, submission }),
      ]);
    });
    h.assert(dialogs.some(d => /active consultant/.test(d.text)), 'Server did not explain the consultant rejection');
    await waitForm();
    h.assert(await form.locator('[name="reasonForConsultation"]').inputValue() === reason, 'Referral reason was lost');
    h.assert(await form.locator('[name="clinicalInformation"]').inputValue() === clinical, 'Clinical information was lost');
    h.assert(await form.locator('#service').inputValue() === service, 'Service selection was lost');
    if (!requestId) {
      for (const field of letterheadFields) h.assert(await form.locator(`#${field}`).inputValue() === savedLetterhead[field],
        `${field} was overwritten on the retry form`);
      if (willBook) h.assert(await form.locator('#patientWillBook').isChecked(), 'Patient will book choice was lost');
    }
    h.assert(await form.locator('#specialist').inputValue() === '', 'Retry did not require a fresh active selection');
    h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequestsArchive WHERE demographicNo=${patient}`) === '0',
      'Rejected update wrote an archive');
    h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient}`) === (requestId ? '1' : '0'),
      'Rejected save changed the referral count');
    h.assert(sql.value(`SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient}`) === '0',
      'Rejected save wrote a signature');
  }
  await s.step('server rejects missing, malformed, nonexistent and inactive consultant ids on create', async () => {
    const absent = String(Number(sql.value('SELECT MAX(specId) FROM professionalSpecialists')) + 1000);
    await reject(null, 'Submit Consultation Request');
    h.assert(await form.locator(`#delegate_labNoHL7${lab}`).inputValue() === `HL7:${lab}`, 'Lab selection was lost on retry');
    await openAttachments();
    h.assert(await form.locator(`#labNoHL7${lab}`).isChecked(), 'Retained lab was not checked in the dialog');
    await form.locator(`#labNoHL7${lab}`).uncheck();
    await closeAttachments();
    h.assert(await form.locator(`#entry_labNoHL7${lab}`).count() === 0, 'Retained lab could not be removed');
    // CRS rule 942220 blocks integer-overflow payloads before the action; the Java tests cover that boundary.
    for (const id of ['', '0', 'abc', '-1', absent, inactive]) await reject(id, 'Submit Consultation Request');
  });
  await s.step('active directory selection saves a consultation', async () => {
    await pick('#specialistInput', `${marker}-0`);
    await Promise.all([
      form.waitForNavigation({ waitUntil: 'load' }),
      form.locator('[name="submitSaveOnly"]').click(),
    ]);
    h.assert(/has been\s+Created/i.test(await form.locator('body').innerText()), 'Valid consultation was not confirmed');
    h.assert(sql.value(`SELECT specId FROM consultationRequests WHERE demographicNo=${patient}`) === active,
      'Active consultant was not persisted');
    await form.close();
  });
  await s.step('legacy unassigned consultation remains readable and invalid updates preserve it', async () => {
    const requestId = sql.value(`SELECT requestId FROM consultationRequests WHERE demographicNo=${patient}`);
    // Model an explicitly unassigned historical referral; normal saves must assign it.
    sql.execute(`UPDATE consultationRequests SET specId=NULL, signatureImg=NULL WHERE requestId=${requestId};
      DELETE FROM DigitalSignature WHERE demographicId=${patient}`);
    form = await s.context.newPage();
    h.wireStrictPage(form, 'legacy-unassigned', s.recorder);
    await form.goto(`${s.config.baseUrl}/encounter/ViewRequest?requestId=${requestId}`);
    await waitForm();
    h.assert(await form.locator('[name="reasonForConsultation"]').inputValue() === reason, 'Legacy referral is unreadable');
    for (const id of ['', 'abc', inactive]) await reject(id, 'Update Consultation Request', requestId);
    h.assert(sql.value(`SELECT IFNULL(specId,'unassigned') FROM consultationRequests WHERE requestId=${requestId}`) === 'unassigned',
      'Rejected update changed the legacy consultant');
    await pick('#specialistInput', `${marker}-0`);
    await Promise.all([form.waitForNavigation({ waitUntil: 'load' }), form.locator('[name="update"]').click()]);
    h.assert(sql.value(`SELECT specId FROM consultationRequests WHERE requestId=${requestId}`) === active,
      'Valid update did not assign the consultant');
    h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequestsArchive WHERE requestId=${requestId}`) === '1',
      'Valid update did not archive exactly once');
  });
}

if (require.main === module) runWorkflow('consultation-consultant-validation', workflow, { openPatient: true });
module.exports = { workflow };
