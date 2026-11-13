#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// consultation-appointment-time: the consultation request's appointment time around noon and midnight, and in
// the printed referral.
// The form shows one 24-hour time box (#appointmentTimeDisplay); its script (syncTimeToHiddenFields) posts the
// time as a 12-hour hour, a minute and AM/PM, EctConsultationFormRequest2Action turns that back into a 24-hour
// time, EctConsultationFormRequestUtil splits the saved time into 12-hour parts again, and the reopened form
// (initAppointmentTimeDisplay) and the PDF (ConsultationPDFCreator) are built from those parts.
// User path: Schedule ▸ Consultations ▸ the patient's referral ▸ Appointment Date and Time ▸ Update, then
//   1-3. 00:30, 12:00 and 12:30 are saved as those times and shown again when the referral is reopened;
//   4. a saved 15:05 is shown again as 15:05, and Print (the preview of the open form) prints it as 3:05 PM;
//   5. Print of a time typed but not yet saved (16:05) prints it as 4:05 PM and leaves the saved time alone.
// Both prints run first and print their outcome, then the steps report them.
// consultation-edit-status saves one ordinary time (14:30); no check covers 12 AM / 12 PM or the printed time.
// Fixtures: the owned FAKE- patient; a marker-named consultation service and specialist and one referral seeded by
// SQL. Cleanup removes the referral with its history, extension and stamped-signature rows, the service, the
// specialist and their link, and asserts none remain.
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { requirePoppler, pdfTextBuffer } = require('./lib/export-content-helpers');

const { insertId } = h;
const q = h.sqlString;
const CONSULT_POST = /\/encounter\/RequestConsultation(?:\?|$)/;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  requirePoppler('pdftotext');
  const serviceName = `${marker}-Svc`;
  const lastName = `${marker}-Spec`;
  const reason = `${marker} appointment time referral`;
  let requestId;
  let serviceId;
  s.cleanup(() => {
    const requests = requestId ? [requestId] : sql.rows(`SELECT requestId FROM consultationRequests
      WHERE demographicNo=${patient} AND reason=${q(reason)}`).map(([id]) => id).filter(id => /^[1-9]\d*$/.test(id));
    for (const id of requests) {
      h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE requestId=${id}
        AND demographicNo=${patient}`) === '1', 'Referral fixture ownership changed');
      sql.execute(`DELETE FROM consultationRequestExtArchive WHERE requestId=${id};
        DELETE FROM consultationRequestsArchive WHERE requestId=${id} AND demographicNo=${patient};
        DELETE FROM consultationRequestExt WHERE requestId=${id};
        DELETE FROM consultationRequests WHERE requestId=${id} AND demographicNo=${patient};
        DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='CONSULTATION'`);
    }
    if (serviceId) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${serviceId}`);
    sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${q(serviceName)};
      DELETE FROM professionalSpecialists WHERE lName=${q(lastName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM consultationRequestsArchive WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${q(serviceName)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${q(lastName)})`) === '0',
    'Owned referral, history, signature, service or specialist rows remain');
  });
  serviceId = insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${q(serviceName)},'1')`, 'service');
  const specId = insertId(sql, `INSERT INTO professionalSpecialists
    (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Ann',${q(lastName)},'555-0160','555-0161','2 Synthetic Way',NOW(),0,0,0,0)`, 'specialist');
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${serviceId},${specId})`);
  requestId = insertId(sql, `INSERT INTO consultationRequests
    (referalDate,serviceId,specId,providerNo,demographicNo,status,urgency,reason,clinicalInfo,currentMeds,
     allergies,concurrentProblems,statusText,sendTo,patientWillBook,lastUpdateDate)
    VALUES(CURDATE(),${serviceId},${specId},${q(provider)},${patient},'1','2',${q(reason)},
     'Synthetic clinical information','','','','','',0,NOW())`, 'referral');
  const apptDate = sql.value("SELECT DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 14 DAY),'%Y-%m-%d')");
  const saved = () => sql.value(`SELECT CONCAT(IFNULL(appointmentDate,''),' ',IFNULL(appointmentTime,''))
    FROM consultationRequests WHERE requestId=${requestId}`);

  const { page: list } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
    { context: s.context, recorder: s.recorder, label: 'consultations' });
  const row = list.locator(`tr[onclick*="requestId=${requestId}'"]`);
  await row.waitFor({ state: 'visible' });
  const openRequest = async label => {
    const form = await s.popup(list, row, label);
    await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    h.assert(await form.locator('[name="reasonForConsultation"]').inputValue() === reason,
      'The Consultations row opened a referral other than the owned one');
    return form;
  };
  const shown = form => form.locator('#appointmentTimeDisplay').inputValue();
  const saveTime = async (time, label) => {
    const form = await openRequest(`consultation-${label}`);
    await form.locator('#appointmentDate').fill(apptDate);
    await form.locator('#appointmentTimeDisplay').fill(time);
    await clickAndAwaitReload(form, form.locator('input[name="update"]'));
    h.assert(/has been\s+Updated/i.test(await form.locator('body').innerText()), `The update with ${time} was not confirmed`);
    await form.close();
  };
  // Every 12-hour time the PDF prints ("3:05 PM"), as written.
  const printedTimes = async form => {
    const preview = form.waitForResponse(r => CONSULT_POST.test(r.url()) && r.request().method() === 'POST'
      && (r.headers()['content-type'] || '').includes('json'), { timeout: 60000 });
    await form.locator('input[name="printPreview"]').click();
    const reply = await (await preview).json();
    h.assert(!reply.errorMessage && reply.consultPDF, `Print returned no PDF: ${JSON.stringify(reply.errorMessage || null)}`);
    const text = pdfTextBuffer(Buffer.from(reply.consultPDF, 'base64'));
    h.assert(text.includes(apptDate), 'The printed referral does not show the appointment date');
    return [...text.matchAll(/\b(\d{1,2}):(\d{0,2})\s*([AP]M)\b/g)].map(([, hour, minute, half]) => `${hour}:${minute} ${half}`);
  };

  for (const time of ['00:30', '12:00', '12:30']) {
    await s.step(`${time} is saved as ${time} and shown again as ${time} when the referral is reopened`, async () => {
      await saveTime(time, time.replace(':', ''));
      h.assert(saved() === `${apptDate} ${time}:00`, `Update with ${time} saved ${saved()}`);
      const form = await openRequest(`consultation-${time.replace(':', '')}-reopen`);
      const now = await shown(form);
      h.assert(now === time, `The reopened referral shows ${JSON.stringify(now)}; expected ${time}`);
      await form.close();
    });
  }

  // Both prints run before either is reported, so one wrong print does not hide the other.
  const outcomes = [];
  const attempt = async (label, body) => {
    let error = null;
    try { await body(); } catch (caught) { error = caught; }
    outcomes.push({ label, error });
    console.log(`  ${error ? 'BAD' : 'OK '} consultation-appointment-time: ${label}${error ? ` -> ${error.message}` : ''}`);
  };
  await s.step('15:05 is saved as 15:05', async () => {
    await saveTime('15:05', '1505');
    h.assert(saved() === `${apptDate} 15:05:00`, `Update with 15:05 saved ${saved()}`);
  });
  const form = await openRequest('consultation-1505-reopen');
  await attempt('the reopened 15:05 is shown as 15:05 and Print prints it as 3:05 PM', async () => {
    const now = await shown(form);
    h.assert(now === '15:05', `The reopened referral shows ${JSON.stringify(now)}`);
    const printed = await printedTimes(form);
    h.assert(printed.includes('3:05 PM'), `Print shows the saved 15:05 as ${JSON.stringify(printed)}`);
  });
  await attempt('Print of a time typed but not saved prints 4:05 PM and leaves the saved time alone', async () => {
    await form.locator('#appointmentTimeDisplay').fill('16:05');
    const printed = await printedTimes(form);
    h.assert(saved() === `${apptDate} 15:05:00`, `Print changed the saved time to ${saved()}`);
    h.assert(printed.includes('4:05 PM'), `Print shows the typed 16:05 as ${JSON.stringify(printed)}`);
  });
  for (const { label, error } of outcomes) await s.step(label, async () => { if (error) throw error; });
}

if (require.main === module) runWorkflow('consultation-appointment-time', workflow, { openPatient: true });
module.exports = { workflow };
