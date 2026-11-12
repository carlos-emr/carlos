#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Consultation request: clearing the booked appointment time (the time box's ×) and the "Patient will book" checkbox, saved
 * with Update.
 *
 * WHY THIS CHECK EXISTS. A referral's appointment date and time print on the letter the consultant receives. The form's ×
 * next to the time box (clearAppointmentTime) empties the time, and "Patient will book" records that the patient makes the
 * appointment; on installs with CONSULTATION_PATIENT_WILL_BOOK=true ticking it also disables the date fields
 * (disableDateFields); with it off the box isn't shown at all. consultation-appointment-time and consultation-edit-status
 * set times, and consultation-null-booking renders a NULL flag; nothing pressed × or ticked the checkbox and looked at what
 * was saved.
 *
 * User path: Schedule ▸ Consultations ▸ the owned referral (its ViewRequest popup) ▸ × beside the appointment time ▸ Update ▸
 * the date cleared ▸ Update;
 * the referral again ▸ Patient will book ▸ Update; again ▸ untick ▸ Update.
 * Asserted, against MariaDB after every Update:
 *   1. × empties the time box and its hidden hour and minute. With the date still set, Update is refused ("Please enter
 *      appointment time…") and nothing is posted or changed; clearing the date too, Update saves the referral with no
 *      appointment date or time, and the reopened referral shows neither.
 *   2. Where CONSULTATION_PATIENT_WILL_BOOK=true: ticking Patient will book disables the appointment date fields, Update
 *      stores the flag, and the reopened referral shows it ticked; 3. unticking it and Update clears the flag.
 *   Where the setting is off (the packaged default) the box isn't rendered: the Patient will book step asserts it is
 *   absent and the date stays editable instead, and the untick step does not run.
 * Fixtures: FAKE- patient (runWorkflow), FAKE- service, consultant and their link, and one referral with an appointment date
 * and time, seeded by SQL. The service and consultant are in the clinic-wide pickers while the run lasts, so the check must
 * not run concurrently with other checks (live wrapper: EXCLUSIVE=1). Cleanup deletes the referral with its archive / ext
 * rows, the consultation signature an update records for the owned patient, and every owned row, and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const { insertId } = h;
const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const serviceName = `${marker}-Svc`;
  const lastName = `${marker}-Spec`;
  const reason = `${marker} referral reason`;
  const apptDate = sql.value(`SELECT DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 21 DAY),'%Y-%m-%d')`);
  let requestId;
  let serviceId;
  s.cleanup(() => {
    const ids = requestId ? [requestId] : sql.rows(`SELECT requestId FROM consultationRequests WHERE demographicNo=${patient}
      AND reason=${q(reason)}`).map(([id]) => id).filter((id) => /^[1-9]\d*$/.test(id));
    for (const id of ids) {
      sql.execute(`DELETE FROM consultationRequestExtArchive WHERE requestId=${id};
        DELETE FROM consultationRequestsArchive WHERE requestId=${id} AND demographicNo=${patient};
        DELETE FROM consultationRequestExt WHERE requestId=${id};
        DELETE FROM consultationRequests WHERE requestId=${id} AND demographicNo=${patient}`);
    }
    sql.execute(`DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='CONSULTATION'`);
    if (serviceId) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${serviceId}`);
    sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${q(serviceName)};
      DELETE FROM professionalSpecialists WHERE lName=${q(lastName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM consultationRequestsArchive WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${q(serviceName)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${q(lastName)})`) === '0',
    'The owned referral, its history or signature, or the owned service or consultant remains');
  });
  serviceId = insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${q(serviceName)},'1')`, 'service');
  const specId = insertId(sql, `INSERT INTO professionalSpecialists
    (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Ann',${q(lastName)},'555-0180','555-0181','4 Synthetic Way',NOW(),0,0,0,0)`, 'consultant');
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${serviceId},${specId})`);
  requestId = insertId(sql, `INSERT INTO consultationRequests
    (referalDate,serviceId,specId,providerNo,demographicNo,status,urgency,reason,clinicalInfo,currentMeds,
     allergies,concurrentProblems,statusText,sendTo,patientWillBook,appointmentDate,appointmentTime,lastUpdateDate)
    VALUES(CURDATE(),${serviceId},${specId},${q(provider)},${patient},'1','2',${q(reason)},
     'Synthetic clinical information','','','','','',0,${q(apptDate)},'14:30:00',NOW())`, 'referral');
  const field = (column) => sql.value(`SELECT IFNULL(${column},'<null>') FROM consultationRequests WHERE requestId=${requestId}`);

  const { page: list } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
    { context: s.context, recorder: s.recorder, label: 'consultations' });
  const row = list.locator(`tr[onclick*="requestId=${requestId}'"]`);
  const openRequest = async (label) => {
    await row.waitFor({ state: 'visible' });
    const form = await s.popup(list, row, label);
    await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    h.assert(await form.locator('[name="reasonForConsultation"]').inputValue() === reason,
      'The Consultations row opened a referral other than the owned one');
    return form;
  };
  const update = async (form) => {
    await clickAndAwaitReload(form, form.locator('input[name="update"]'));
    h.assert(/has been\s+Updated/i.test(await form.locator('body').innerText()), 'The update was not confirmed');
    await form.close();
  };

  await s.step('× empties the time; with the date still set Update is refused and saves nothing, and with the date '
    + 'cleared too Update saves the referral with no appointment', async () => {
    const form = await openRequest('consultation-clear-time');
    h.assert(await form.locator('#appointmentTimeDisplay').inputValue() === '14:30', 'The saved appointment time did not render');
    await form.locator('#clearTimeBtn').click();
    h.assert(await form.locator('#appointmentTimeDisplay').inputValue() === ''
      && await form.locator('#appointmentHour').inputValue() === '' && await form.locator('#appointmentMinute').inputValue() === '',
    '× did not empty the time box and its hidden hour and minute');
    const posted = [];
    const watch = (request) => { if (request.method() === 'POST') posted.push(new URL(request.url()).pathname); };
    form.on('request', watch);
    const refused = await h.withExpectedDialogs(form, async () => {
      await Promise.all([form.waitForEvent('dialog'), form.locator('input[name="update"]').click()]);
      // The script that raised the alert has finished once the page answers again, so a submit it made has started.
      await form.evaluate(() => document.readyState);
      await form.waitForLoadState('networkidle');
    });
    form.off('request', watch);
    h.assert(refused.length === 1 && /enter appointment time/i.test(refused[0].text),
      `Update with a date and no time raised ${JSON.stringify(refused)}, not the "enter appointment time" alert`);
    h.assert(!posted.some((path) => path.endsWith('/encounter/RequestConsultation')), 'Update with a date and no time was still posted');
    h.assert(field('appointmentDate') === apptDate && field('appointmentTime') === '14:30:00',
      'The refused Update changed the stored appointment');
    // The same window goes on to the next step's change, as a clinician would.
    await form.locator('#appointmentDate').fill('');
    await update(form);
    h.assert(field('appointmentDate') === '<null>' && field('appointmentTime') === '<null>',
      `With the date and time cleared, Update stored date ${field('appointmentDate')} and time ${field('appointmentTime')}`);
    const reopened = await openRequest('consultation-clear-time-reopen');
    h.assert(await reopened.locator('#appointmentDate').inputValue() === '' && await reopened.locator('#appointmentTimeDisplay').inputValue() === '',
      'The reopened referral still shows an appointment date or time');
    await reopened.close();
  });

  // The checkbox renders only where CONSULTATION_PATIENT_WILL_BOOK=true (packaged default false), so the step reads
  // which install it is on from the referral it opens.
  let offered;
  await s.step('ticking Patient will book stores the flag and disables the appointment date fields (where '
    + 'CONSULTATION_PATIENT_WILL_BOOK is off, the referral offers no such box)', async () => {
    const form = await openRequest('consultation-will-book');
    const box = form.locator('#patientWillBook');
    offered = await box.count() === 1;
    if (!offered) {
      h.assert(await form.locator('#appointmentDate').isEnabled(),
        'The referral\'s appointment date is not editable on an install with Patient will book off');
      await form.close();
      return;
    }
    h.assert(!(await box.isChecked()), 'The referral already shows Patient will book');
    await box.check();
    h.assert(await form.locator('#appointmentDate').isDisabled(), 'Patient will book did not disable the appointment date');
    await update(form);
    h.assert(field('patientWillBook') === '1', 'Update did not store Patient will book');
    const reopened = await openRequest('consultation-will-book-reopen');
    h.assert(await reopened.locator('#patientWillBook').isChecked(), 'The reopened referral does not show Patient will book');
    await reopened.close();
  });
  if (!offered) return;

  await s.step('unticking Patient will book and Update clears the flag', async () => {
    const form = await openRequest('consultation-will-book-off');
    await form.locator('#patientWillBook').uncheck();
    await update(form);
    h.assert(field('patientWillBook') === '0', 'Update did not clear Patient will book');
  });
}

if (require.main === module) runWorkflow('consultation-appointment-clear-will-book', workflow, { openPatient: true });
module.exports = { workflow };
