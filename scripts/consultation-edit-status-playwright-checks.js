#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §3.3 consultation-edit-status. User path: Schedule ▸ Consultations ▸ the owned
// request's row (ViewRequest popup) ▸ status / urgency / appointment date+time / notes ▸ Update;
// then Current Medications ▸ Import ▸ Active Medications (consultationClinicalData) ▸ "Update
// And Print Preview"; then "Update And Fax" ▸ fax cover page ▸ Send (ConsultationFormFax).
// Asserts: the consultationRequests row carries the new status, urgency, appointment and notes
// and each update archived the previous version in consultationRequestsArchive; the list shows
// the new status; the import fills the textarea from the owned active drug; the preview download
// is a real PDF whose text carries the owned reason and imported medication; with no active fax
// sender the Send is refused with an alert and no fax job is queued (nothing leaves the host).
// Fixtures: FAKE- patient (runWorkflow), FAKE- service, specialist, service link, referral and
// drug seeded by SQL. Cleanup deletes the referral with its archive/ext rows, the consultation
// stamp signature the update records for the owned patient (DigitalSignature), and every owned row.
// EXCLUSIVE: the fax boundary requires that no active fax sender account exists while it runs.
const fs = require('node:fs');
const { spawnSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

function insertId(sql, statement, what) {
  const id = sql.value(`${statement}; SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), `The owned ${what} fixture was not created`);
  return id;
}

function pdfText(bytes) {
  h.assert(bytes.subarray(0, 4).toString('latin1') === '%PDF', 'The print preview download is not a PDF');
  const text = spawnSync('pdftotext', ['-', '-'], { input: bytes });
  h.assert(text.status === 0, 'The print preview PDF is not readable');
  return text.stdout.toString().replace(/\s+/g, ' ');
}

/** ISO date a fixed number of days ahead, computed in the server's calendar (MySQL CURDATE). */
function futureDate(sql, days) {
  return sql.value(`SELECT DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL ${days} DAY),'%Y-%m-%d')`);
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  if (spawnSync('pdftotext', ['-v']).error) throw new h.SkipCheck('Install Poppler pdftotext to read the consultation PDF');
  const serviceName = `${marker}-Svc`;
  const lastName = `${marker}-Spec`;
  const reason = `${marker} referral reason`;
  const drugText = `${marker} one tablet daily`;
  let requestId;
  let serviceId;
  let specId;
  s.cleanup(() => {
    if (requestId) {
      h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE requestId=${requestId}
        AND demographicNo=${patient}`) === '1', 'Referral fixture ownership changed');
      sql.execute(`DELETE FROM consultationRequestExtArchive WHERE requestId=${requestId};
        DELETE FROM consultationRequestsArchive WHERE requestId=${requestId} AND demographicNo=${patient};
        DELETE FROM consultationRequestExt WHERE requestId=${requestId};
        DELETE FROM consultationRequests WHERE requestId=${requestId} AND demographicNo=${patient};
        DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='CONSULTATION'`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM consultationRequests WHERE requestId=${requestId})
        + (SELECT COUNT(*) FROM consultationRequestsArchive WHERE requestId=${requestId})
        + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})`) === '0',
      'The owned referral, its history or its stamped signature was not removed');
    }
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient} AND special=${h.sqlString(drugText)}`);
    if (serviceId) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${serviceId}`);
    sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)};
      DELETE FROM professionalSpecialists WHERE lName=${h.sqlString(lastName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${h.sqlString(lastName)})`) === '0',
    'Owned drug, service or specialist rows remain');
  });
  serviceId = insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${h.sqlString(serviceName)},'1')`, 'service');
  specId = insertId(sql, `INSERT INTO professionalSpecialists
    (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Ann',${h.sqlString(lastName)},'555-0160','555-0161','2 Synthetic Way',NOW(),0,0,0,0)`, 'specialist');
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${serviceId},${specId})`);
  requestId = insertId(sql, `INSERT INTO consultationRequests
    (referalDate,serviceId,specId,providerNo,demographicNo,status,urgency,reason,clinicalInfo,currentMeds,
     allergies,concurrentProblems,statusText,sendTo,patientWillBook,lastUpdateDate)
    VALUES(CURDATE(),${serviceId},${specId},${h.sqlString(provider)},${patient},'1','2',${h.sqlString(reason)},
     'Synthetic clinical information','','','','','',0,NOW())`, 'referral');
  insertId(sql, `INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,BN,customName,special,
    create_date,lastUpdateDate) VALUES(${h.sqlString(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),
    CURDATE(),${h.sqlString(marker)},${h.sqlString(marker)},${h.sqlString(drugText)},NOW(),NOW())`, 'active drug');
  const archived = () => sql.value(`SELECT COUNT(*) FROM consultationRequestsArchive WHERE requestId=${requestId}`);
  const field = column => sql.value(`SELECT IFNULL(${column},'') FROM consultationRequests WHERE requestId=${requestId}`);

  const { page: list } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
    { context: s.context, recorder: s.recorder, label: 'consultations' });
  const row = list.locator(`tr[onclick*="requestId=${requestId}'"]`);
  const openRequest = async label => {
    const form = await s.popup(list, row, label);
    await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    h.assert(await form.locator('[name="reasonForConsultation"]').inputValue() === reason,
      'The Consultations row opened a referral other than the owned one');
    return form;
  };

  const apptDate = futureDate(sql, 14);
  const notes = `${marker} booked by phone`;
  await s.step('status, urgency and appointment update persists and archives the previous version', async () => {
    await row.waitFor({ state: 'visible' });
    const form = await openRequest('consultation-edit');
    h.assert(await form.locator('#status1').isChecked(), 'The saved status did not render');
    await form.locator('#status3').check();
    await form.locator('#urgency').selectOption('1');
    await form.locator('#appointmentDate').fill(apptDate);
    await form.locator('#appointmentTimeDisplay').fill('14:30');
    await form.locator('textarea[name="appointmentNotes"]').fill(notes);
    await clickAndAwaitReload(form, form.locator('input[name="update"]'));
    h.assert(/has been\s+Updated/i.test(await form.locator('body').innerText()), 'The update was not confirmed');
    h.assert(field('status') === '3' && field('urgency') === '1', 'Status and urgency were not saved');
    h.assert(field('appointmentDate') === apptDate && field('appointmentTime') === '14:30:00',
      'The appointment date and time were not saved');
    h.assert(field('statusText') === notes && field('reason') === reason, 'Notes were not saved or the reason changed');
    h.assert(archived() === '1', 'The update did not archive exactly one previous version');
    h.assert(sql.value(`SELECT CONCAT(status,'/',urgency,'/',IFNULL(appointmentDate,'none')) FROM consultationRequestsArchive
      WHERE requestId=${requestId}`) === '1/2/none', 'The archived history does not hold the previous version');
    // The countdown's self-close is not asserted: the Struts COOP header severs window.opener,
    // so the window falls back to the patient's list instead of closing (reported finding).
    await form.close();
  });

  await s.step('the Consultations list shows the new status and urgency', async () => {
    await clickAndAwaitReload(list, list.locator('form[action$="/encounter/ViewConsultation"] input[type="submit"]'));
    const cell = row.locator('td').first();
    h.assert(await cell.getAttribute('class') === 'consult-status-3', 'The list does not show the patient-called status');
    h.assert(/Urgent/i.test(await row.locator('td').nth(1).innerText()), 'The list does not show the urgent flag');
  });

  let form;
  await s.step('Import Active Medications fills Current Medications from the owned drug', async () => {
    form = await openRequest('consultation-print');
    h.assert(await form.locator('#appointmentTimeDisplay').inputValue() === '14:30', 'The saved appointment time did not render');
    const meds = form.locator('#currentMedications');
    await form.locator('.consult-import-dropdown', { has: form.locator('ul[data-target="currentMedications"]') })
      .locator('.dropdown-toggle').click();
    const [response] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/oscarConsultationRequest/consultationClinicalData')),
      form.locator('#fetchMedications_currentMedications').click(),
    ]);
    h.assert(response.status() === 200, 'The clinical data import was refused');
    await form.waitForFunction(() => document.getElementById('currentMedications').value.trim() !== '');
    h.assert((await meds.inputValue()).toLowerCase().includes(drugText.toLowerCase()),
      'The imported medications do not carry the owned active drug');
  });

  await s.step('Update And Print Preview saves the import and downloads a PDF of the referral', async () => {
    const download = form.waitForEvent('download', { timeout: 30000 });
    await clickAndAwaitReload(form, form.locator('input[name="updateAndPrint"]'));
    const file = await (await download).path();
    const text = pdfText(fs.readFileSync(file)).toLowerCase();
    h.assert(text.includes(reason.toLowerCase()), 'The PDF does not carry the owned referral reason');
    h.assert(text.includes(drugText.toLowerCase()), 'The PDF does not carry the imported medication');
    h.assert(field('currentMeds').toLowerCase().includes(drugText.toLowerCase()), 'The imported medication was not saved');
    h.assert(archived() === '2', 'Update And Print Preview did not archive the previous version');
    await form.close();
  });

  await s.step('Update And Fax stops at the cover page and Send is refused without queueing', async () => {
    h.assert(sql.value('SELECT COUNT(*) FROM fax_config WHERE active=1') === '0',
      'An active fax sender exists; the fax boundary cannot be driven without risk of queueing (run with EXCLUSIVE=1)');
    form = await openRequest('consultation-fax');
    await clickAndAwaitReload(form, form.locator('#fax_button'));
    const cover = form.locator('#coverPageForm');
    h.assert((await cover.getAttribute('action')).endsWith('/encounter/oscarConsultationRequest/ConsultationFormFax'),
      'The fax cover page does not post to the consultation fax action');
    h.assert(await form.locator('#senderFaxAccount option').count() === 0, 'The cover page offers a fax sender account');
    h.assert(await form.locator('#searchProfessionalSpecialist_fax').inputValue() === '555-0161',
      'The cover page did not carry the consultant fax number');
    await form.locator('#searchProfessionalSpecialist_name').fill(`${lastName}, Ann`);
    const refusal = await h.withExpectedDialogs(form, () => clickAndAwaitReload(form, form.locator('#btnSend')));
    h.assert(refusal.length === 1 && /could not be sent/i.test(refusal[0].text) && /not configured/i.test(refusal[0].text),
      'The fax Send was not refused for the missing sender account');
    await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    h.assert(sql.value(`SELECT COUNT(*) FROM faxes WHERE demographicNo=${patient}`) === '0', 'A fax job was queued');
    await expectValue(sql, `SELECT COUNT(*) FROM consultationRequestsArchive WHERE requestId=${requestId}`, '3',
      'Update And Fax did not archive the previous version before the cover page');
    await form.close();
  });
}

if (require.main === module) runWorkflow('consultation-edit-status', workflow, { openPatient: true });
module.exports = { workflow };
