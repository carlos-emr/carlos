#!/usr/bin/env node
/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Issue #4999. The Consultations list offers an "Add ticklers for consultations with Nothing Done
 * status for more than one week" link. It used to send `demo` and `message`, the parameters of the
 * retired bulk action, to the standard Add Tickler form, which reads `demographic_no` and started
 * its textarea empty. Staff who confirmed the follow-up got a blank task form with no patient.
 *
 * WHAT IT DRIVES, THE WAY A REFERRAL CLERK DOES. Login, the schedule banner's Consultations link,
 * the Consultant picker (type-ahead, then Search) to isolate this run's own referrals from the
 * demo dataset's, then:
 *
 *   1. one specialist holds two overdue Nothing Done referrals for patient A (so the patient is
 *      listed twice) and a Nothing Done referral made today for patient B (not overdue): the list
 *      offers exactly ONE control and it names patient A only;
 *   2. dismissing the confirmation opens no window and writes no task;
 *   3. accepting it opens the standard Add Tickler form with patient A as its patient (the hidden
 *      demographic_no, the search box and the chart line) and the suggested follow-up message in the
 *      Reminder Message box, with no "choose a patient" warning;
 *   4. closing that form with Back writes no task;
 *   5. Save writes exactly ONE task, for patient A, carrying that message; patient B has none and
 *      every referral keeps its status;
 *   6. a second specialist holds overdue referrals for patients A and C: the list offers one control
 *      per patient, each opening the form for its own patient only (the form is single-patient, so
 *      the link must not fold two patients into one).
 *
 * Fixtures, all owned and removed afterwards (and asserted gone): three FAKE patients (A by
 * runWorkflow, B and C here), one consultation service, two specialists and their referrals, and the
 * task step 5 saves with its update/comment/link rows.
 *
 * Environment (common contract in lib/playwright-harness.js readConfig()):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH, SCREENSHOT_DIR, MYSQL_*
 *
 *   npm run test:consultation-overdue-tickler-playwright
 */
'use strict';
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const POSITIVE = /^[1-9]\d*$/;
/** The text the list suggests for the task (EctViewConsultationRequestsUtil.OVERDUE_TICKLER_MESSAGE). */
const SUGGESTED_MESSAGE = "Patient has Consultation Letter with a status of 'Nothing Done' for over one week";
const ADD_TICKLER_LINK = 'a[href*="/tickler/ViewAddTickler"]';

/** The Consultations list, entered through the banner link a user clicks. */
async function openConsultationList(s) {
  const { page } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator("a[onclick*='/encounter/IncomingConsultation']").first(),
    { label: 'consultation-list', recorder: s.recorder, timeout: TIMEOUT });
  await h.assertNotErrorPage(page, 'consultation list');
  return page;
}

/** Type the specialist's name, pick it from the type-ahead and press Search, as the picker is used. */
async function isolateWithConsultantPicker(list, lastName, label) {
  const search = list.locator('#consultantSearch');
  await search.fill('');
  const [response] = await Promise.all([
    list.waitForResponse(r => r.url().includes('/encounter/consultation/searchConsultants'), { timeout: TIMEOUT }),
    search.pressSequentially(lastName, { delay: 40 }),
  ]);
  h.assert(response.status() === 200, `consultant search answered HTTP ${response.status()}`);
  await list.locator('#consultantSuggestions').waitFor({ state: 'visible', timeout: TIMEOUT });
  const options = list.locator('#consultantSuggestions li[role="option"]');
  const index = await options.evaluateAll((items, wanted) => items
    .findIndex(item => item.textContent.replace(/\s+/g, ' ').trim() === wanted), label);
  h.assert(index >= 0, `the Consultant picker did not offer "${label}"`);
  await options.nth(index).click();
  h.assert(POSITIVE.test(await list.locator('#consultantId').inputValue()), 'picking a consultant left no consultantId');
  await Promise.all([
    list.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }),
    list.locator('#consultationFilterForm input[type="submit"]').click(),
  ]);
  await list.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(list, 'consultation list after the Consultant search');
}

async function listedPatients(list) {
  return list.locator('table.consult-table tbody tr').evaluateAll(
    rows => rows.map(row => row.querySelectorAll('td')[3].textContent.replace(/\s+/g, ' ').trim()));
}

/** Each overdue control's label and the demographic_no values its link carries, in page order. */
async function controlPatients(list) {
  return list.locator(ADD_TICKLER_LINK).evaluateAll(links => links.map(link => ({
    href: link.getAttribute('href'),
    label: link.textContent.replace(/\s+/g, ' ').trim(),
    patients: new URL(link.href).searchParams.getAll('demographic_no'),
  })));
}

/** Click a control through its confirmation. Accepting must open the form; dismissing must not. */
async function clickControl(s, list, link, { accept, label }) {
  let popup = null;
  let opened = false;
  const watch = page => { opened = true; if (!popup) popup = page; };
  s.context.on('page', watch);
  try {
    const dialogs = await h.withExpectedDialogs(list, async () => {
      if (accept) popup = await s.popup(list, link, label);
      else {
        await link.click();
        // A dismissed confirmation returns false from the click handler, so no window opens.
        // Wait out the time a popup would take rather than asserting the instant after the click.
        await new Promise(resolve => setTimeout(resolve, 1500));
      }
    }, { accept });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && /Nothing Done/.test(dialogs[0].text),
      `the control did not ask for confirmation first (${JSON.stringify(dialogs)})`);
    if (!accept) h.assert(!opened, 'dismissing the confirmation still opened the Add Tickler form');
    return popup;
  } finally {
    s.context.off('page', watch);
  }
}

async function readForm(popup) {
  await popup.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(popup, 'Add Tickler form');
  const form = popup.locator('form[name="serviceform"]');
  await form.waitFor({ state: 'attached', timeout: TIMEOUT });
  return {
    path: new URL(popup.url()).pathname,
    patient: await form.locator('input[name="demographic_no"]').inputValue(),
    searchBox: await popup.locator('form[name="ADDAPPT"] input[name="keyword"]').inputValue(),
    message: await popup.locator('#ticklerMessage').inputValue(),
    chooseWarning: await popup.locator('#choosePatientForDocument').count(),
  };
}

async function workflow(s) {
  const { sql, marker, patient: patientA, provider } = s;
  const lastNameOne = `${marker}-One`;
  const lastNameTwo = `${marker}-Two`;
  const labelOne = `${lastNameOne}, Ann`;
  const labelTwo = `${lastNameTwo}, Ann`;
  const serviceName = `${marker}-Svc`;
  const reasonPrefix = `${marker} overdue`;
  const owned = { patients: [patientA] };
  const newPatient = suffix => {
    const id = h.insertId(sql, `INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(`${marker}-${suffix}`)},'Workflow','1980','01','02','F','AC',
        ${h.sqlString(provider)},'ON','ON','NR',NOW())`, `patient ${suffix}`);
    owned.patients.push(id);
    return id;
  };
  const patientsIn = () => owned.patients.join(',');
  const ticklerRows = patient => sql.rows(`SELECT tickler_no, status, message, creator FROM tickler
    WHERE demographic_no=${patient}`);

  // Registered before the first write. Every owned row is found by the run's marker or by the
  // owned patients, so a run that dies half way still removes what it wrote.
  s.cleanup(() => {
    const ticklers = sql.rows(`SELECT tickler_no FROM tickler WHERE demographic_no IN (${patientsIn()})`)
      .map(([id]) => id).filter(id => POSITIVE.test(id));
    if (ticklers.length) {
      const ids = ticklers.join(',');
      sql.execute(`DELETE FROM tickler_update WHERE tickler_no IN (${ids});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ids});
        DELETE FROM tickler_link WHERE tickler_no IN (${ids});
        DELETE FROM tickler WHERE tickler_no IN (${ids}) AND demographic_no IN (${patientsIn()})`);
    }
    sql.execute(`DELETE FROM consultationRequests WHERE demographicNo IN (${patientsIn()})
      AND reason LIKE ${h.sqlString(`${reasonPrefix}%`)}`);
    sql.execute(`DELETE FROM serviceSpecialists WHERE specId IN
      (SELECT specId FROM professionalSpecialists WHERE lName LIKE ${h.sqlString(`${marker}-%`)})`);
    sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)};
      DELETE FROM professionalSpecialists WHERE lName LIKE ${h.sqlString(`${marker}-%`)}`);
    // Patient A is removed by runWorkflow after this runs; B and C are this check's.
    const extra = owned.patients.filter(id => id !== patientA);
    if (extra.length) {
      sql.execute(`DELETE FROM demographic WHERE demographic_no IN (${extra.join(',')})
        AND last_name LIKE ${h.sqlString(`${marker}-%`)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM tickler WHERE demographic_no IN (${patientsIn()}))
      + (SELECT COUNT(*) FROM consultationRequests WHERE demographicNo IN (${patientsIn()}))
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName LIKE ${h.sqlString(`${marker}-%`)})
      + (SELECT COUNT(*) FROM demographic WHERE last_name LIKE ${h.sqlString(`${marker}-%`)})`) === '0',
    'An owned task, referral, service, specialist or patient row remains');
  });

  const patientB = newPatient('B');
  const patientC = newPatient('C');
  const serviceId = h.insertId(sql,
    `INSERT INTO consultationServices(serviceDesc,active) VALUES(${h.sqlString(serviceName)},'1')`, 'service');
  const specialist = lastName => {
    const specId = h.insertId(sql, `INSERT INTO professionalSpecialists
      (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
      VALUES('Ann',${h.sqlString(lastName)},'555-0160','555-0161','2 Synthetic Way',NOW(),0,0,0,0)`, 'specialist');
    sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${serviceId},${specId})`);
    return specId;
  };
  const referral = (specId, patient, ageDays, note) => h.insertId(sql, `INSERT INTO consultationRequests
    (referalDate,serviceId,specId,providerNo,demographicNo,status,urgency,reason,clinicalInfo,currentMeds,
     allergies,concurrentProblems,statusText,sendTo,patientWillBook,lastUpdateDate)
    VALUES(DATE_SUB(CURDATE(), INTERVAL ${Number(ageDays)} DAY),${serviceId},${specId},${h.sqlString(provider)},
     ${patient},'1','2',${h.sqlString(`${reasonPrefix} ${note}`)},'Synthetic clinical information','','','','','',0,NOW())`,
  'referral');

  const specOne = specialist(lastNameOne);
  const specTwo = specialist(lastNameTwo);
  // Specialist one: patient A is overdue twice, patient B was referred today (not overdue).
  referral(specOne, patientA, 14, 'A first');
  referral(specOne, patientA, 20, 'A second');
  referral(specOne, patientB, 0, 'B today');
  // Specialist two: two different overdue patients.
  referral(specTwo, patientA, 15, 'A other specialist');
  referral(specTwo, patientC, 16, 'C');
  const referralStatuses = () => sql.rows(`SELECT requestId, status FROM consultationRequests
    WHERE demographicNo IN (${patientsIn()}) AND reason LIKE ${h.sqlString(`${reasonPrefix}%`)} ORDER BY requestId`);
  const statusesBefore = JSON.stringify(referralStatuses());

  const list = await openConsultationList(s);
  let control;

  await s.step('the overdue control names only the overdue patient, once, even with two overdue referrals', async () => {
    await isolateWithConsultantPicker(list, lastNameOne, labelOne);
    const patients = await listedPatients(list);
    h.assert(patients.length === 3, `the picker left ${patients.length} row(s); expected the 3 owned referrals`);
    const controls = await controlPatients(list);
    h.assert(controls.length === 1, `expected one overdue control, found ${controls.length}`);
    control = controls[0];
    h.assert(JSON.stringify(control.patients) === JSON.stringify([patientA]),
      `the control targets patient(s) ${control.patients}, not only the overdue patient ${patientA}`);
    await h.screenshot(list, s.config.screenshotDir, 'consultation-overdue-tickler-list');
  });

  await s.step('dismissing the confirmation opens no form and writes no task', async () => {
    await clickControl(s, list, list.locator(ADD_TICKLER_LINK), { accept: false, label: 'overdue-dismissed' });
    for (const patient of owned.patients) {
      h.assert(ticklerRows(patient).length === 0, `a task exists for patient ${patient} after the confirmation was dismissed`);
    }
  });

  await s.step('accepting opens the Add Tickler form with the patient and the suggested message', async () => {
    const popup = await clickControl(s, list, list.locator(ADD_TICKLER_LINK), { accept: true, label: 'overdue-add-tickler' });
    const form = await readForm(popup);
    h.assert(form.path.endsWith('/tickler/ViewAddTickler'), `the control opened ${form.path}`);
    h.assert(form.patient === patientA, `overdue consultation Add Tickler preserves the patient: the form's patient is "${form.patient}"`);
    h.assert(form.searchBox.includes(marker), `the form's patient box does not name the patient ("${form.searchBox}")`);
    h.assert(form.message === SUGGESTED_MESSAGE, `overdue consultation Add Tickler preserves the suggested follow-up message: the box holds "${form.message}"`);
    h.assert(form.chooseWarning === 0, 'the form asks the clinician to choose a patient although it was given one');
    await h.screenshot(popup, s.config.screenshotDir, 'consultation-overdue-tickler-form');
    // Cancel without saving: the form's Back button closes it.
    const closed = popup.waitForEvent('close', { timeout: TIMEOUT });
    await popup.locator('.action-bar-bottom input[value="Back"]').click();
    await closed;
    for (const patient of owned.patients) {
      h.assert(ticklerRows(patient).length === 0, `Back created a task for patient ${patient}`);
    }
  });

  await s.step('Save writes exactly one task, for that patient, and leaves the referrals alone', async () => {
    const popup = await clickControl(s, list, list.locator(ADD_TICKLER_LINK), { accept: true, label: 'overdue-add-tickler-save' });
    const form = await readForm(popup);
    h.assert(form.patient === patientA && form.message === SUGGESTED_MESSAGE, 'the reopened form lost the patient or the message');
    const closed = popup.waitForEvent('close', { timeout: TIMEOUT });
    await popup.locator('.action-bar-bottom input.btn-primary').click();
    await closed;
    const rows = ticklerRows(patientA);
    h.assert(rows.length === 1, `expected exactly one task for the patient, found ${rows.length}`);
    h.assert(rows[0][1] === 'A', `the task is not active (status ${rows[0][1]})`);
    h.assert(rows[0][2] === SUGGESTED_MESSAGE, 'the saved task does not carry the suggested message');
    h.assert(rows[0][3] === provider, `the task was created by ${rows[0][3]}, not the logged-in provider`);
    h.assert(ticklerRows(patientB).length === 0 && ticklerRows(patientC).length === 0,
      'a task was created for a patient the control did not name');
    h.assert(JSON.stringify(referralStatuses()) === statusesBefore, 'saving the task changed a referral');
  });

  await s.step('two overdue patients get one control each, and each opens the form for its own patient', async () => {
    await isolateWithConsultantPicker(list, lastNameTwo, labelTwo);
    const controls = await controlPatients(list);
    h.assert(controls.length === 2, `expected one control per overdue patient, found ${controls.length}`);
    h.assert(controls.every(c => c.patients.length === 1),
      'a control folds more than one patient into a single-patient form');
    h.assert(JSON.stringify(controls.map(c => c.patients[0]).sort()) === JSON.stringify([patientA, patientC].sort()),
      `the controls target ${controls.map(c => c.patients)}, not patients ${patientA} and ${patientC}`);
    h.assert(controls.every(c => c.label.includes(marker)), 'a control does not say which patient it is for');
    for (const wanted of controls) {
      const popup = await clickControl(s, list, list.locator(ADD_TICKLER_LINK).nth(controls.indexOf(wanted)),
        { accept: true, label: `overdue-add-tickler-${wanted.patients[0]}` });
      const form = await readForm(popup);
      h.assert(form.patient === wanted.patients[0],
        `the control for patient ${wanted.patients[0]} opened the form for "${form.patient}"`);
      h.assert(form.message === SUGGESTED_MESSAGE, 'the form lost the suggested message');
      const closed = popup.waitForEvent('close', { timeout: TIMEOUT });
      await popup.locator('.action-bar-bottom input[value="Back"]').click();
      await closed;
    }
    h.assert(ticklerRows(patientC).length === 0, 'opening and closing the form created a task');
    h.assert(ticklerRows(patientA).length === 1, 'the earlier task for patient A changed');
  });
}

if (require.main === module) {
  runWorkflow('consultation-overdue-tickler', workflow, { openMaster: false });
}
module.exports = { workflow };
