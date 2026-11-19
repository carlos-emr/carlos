#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
/*
 * Updates a patient through the session REST endpoint (PUT /ws/rs/demographics), as an integrating
 * client does, and reads every record that update touches back from the database (issue #5298).
 *
 * One request writes the patient's custom fields (alert, notes) and their archive copy, the
 * waiting-list entry, the archive copy of the stored patient, the patient row, each extension with
 * its archive copy, and the audit rows. They are one transaction: all of them are stored, or none.
 *
 * Step 1: a successful update stores every one of those records, attributes the patient row and
 * the audit rows to the signed-in provider, and archives what it replaced. The custom-field
 * archive is the row this step exists for: the update used to overwrite the previous alert and
 * notes without archiving them, with no error.
 * Step 2: an update whose last extension the database refuses (two extras with the same key
 * violate uk_demo_ext_single_value, after the custom fields, the waiting-list entry, the patient
 * and an earlier extension have been written) fails, and the database is exactly as step 1 left it.
 * The failed call is still recorded in the web-service access log, which is written outside the
 * transaction; no audit row claims the update happened.
 *
 * The patient is the runWorkflow fixture (last name = this run's FAKE-PW<hex> marker, which the
 * updates keep). The waiting list is a list of its own, named by the marker, so renumbering it
 * touches no other patient's entry. Cleanup removes the patient's rows from each table the update
 * writes, then the list, and verifies them gone. Environment: the common contract (BASE_URL,
 * TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*).
 */
'use strict';
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const UPDATE_AUDIT = 'DemographicManager.updateDemographic';
const ACCESS_SUCCESS = 'REST WS: DemographicService.updateDemographicData';
const ACCESS_FAILURE = 'REST WS: FAILURE: DemographicService.updateDemographicData';
/** Every table the update writes for the patient, child rows first. */
const PATIENT_TABLES = [
  ['waitingList', 'demographic_no'], ['demographicExtArchive', 'demographic_no'],
  ['demographicExt', 'demographic_no'], ['demographiccustArchive', 'demographic_no'],
  ['demographiccust', 'demographic_no'], ['demographicArchive', 'demographic_no'],
];

async function workflow(s) {
  const endpoint = h.appUrl(s.config.baseUrl, '/ws/rs/demographics');
  const patient = Number(s.patient);
  const count = (sql, parameters) => Number(s.sql.value(sql, parameters));
  const audits = action => count('SELECT COUNT(*) FROM log WHERE action=? AND provider_no=?', [action, s.provider]);

  h.assert(count('SELECT COUNT(*) FROM waitingListName WHERE name=?', [s.marker]) === 0,
    'A waiting list with this run\'s marker already exists');
  s.cleanup(() => {
    for (const [table, column] of PATIENT_TABLES) {
      s.sql.execute(`DELETE FROM ${table} WHERE ${column}=?`, [patient]);
      h.assert(count(`SELECT COUNT(*) FROM ${table} WHERE ${column}=?`, [patient]) === 0,
        `Owned ${table} rows were not removed`);
    }
    s.sql.execute('DELETE FROM waitingListName WHERE name=?', [s.marker]);
    h.assert(count('SELECT COUNT(*) FROM waitingListName WHERE name=?', [s.marker]) === 0,
      'The owned waiting list was not removed');
  });

  // The stored records the update replaces: custom fields, one waiting-list entry, one extension.
  s.sql.execute(`INSERT INTO waitingListName (name,group_no,provider_no,create_date,is_history)
    VALUES (?,'',?,NOW(),'N')`, [s.marker, s.provider]);
  const list = Number(s.sql.value('SELECT ID FROM waitingListName WHERE name=?', [s.marker]));
  h.assert(Number.isSafeInteger(list) && list > 0, 'The owned waiting list was not created');
  s.sql.execute(`INSERT INTO waitingList (listID,demographic_no,note,position,onListSince,is_history)
    VALUES (?,?,?,1,NOW(),'N')`, [list, patient, `${s.marker}-note0`]);
  s.sql.execute('INSERT INTO demographiccust (demographic_no,cust3,content) VALUES (?,?,?)',
    [patient, `${s.marker}-alert0`, `${s.marker}-notes0`]);
  s.sql.execute(`INSERT INTO demographicExt (demographic_no,provider_no,key_val,value,date_time)
    VALUES (?,?,'demo_cell',?,NOW())`, [patient, s.provider, `${s.marker}-cell0`]);
  const cell = Number(s.sql.value("SELECT id FROM demographicExt WHERE demographic_no=? AND key_val='demo_cell'", [patient]));
  h.assert(Number.isSafeInteger(cell) && cell > 0, 'The stored extension fixture was not created');

  const read = await s.context.request.get(`${endpoint}/${patient}`, { maxRedirects: 0 });
  h.assert(read.status() === 200, `reading the owned patient expected HTTP 200, received ${read.status()}`);
  const stored = await read.json();
  await read.dispose();
  h.assert(Number(stored.demographicNo) === patient && stored.lastName === s.marker,
    'REST returned a patient other than the owned fixture');

  /** Everything the update writes for this patient, as one comparable value. */
  const state = () => JSON.stringify({
    patient: s.sql.rows('SELECT first_name,last_name,lastUpdateUser FROM demographic WHERE demographic_no=?', [patient]),
    patientArchive: s.sql.rows('SELECT first_name,lastUpdateUser FROM demographicArchive WHERE demographic_no=? ORDER BY id', [patient]),
    custom: s.sql.rows('SELECT cust1,cust2,cust3,cust4,content FROM demographiccust WHERE demographic_no=?', [patient]),
    customArchive: s.sql.rows('SELECT cust3,content FROM demographiccustArchive WHERE demographic_no=? ORDER BY id', [patient]),
    waitingList: s.sql.rows('SELECT note,position,is_history FROM waitingList WHERE demographic_no=? ORDER BY id', [patient]),
    extensions: s.sql.rows('SELECT key_val,value FROM demographicExt WHERE demographic_no=? ORDER BY id', [patient]),
    extensionArchive: s.sql.rows('SELECT key_val,value FROM demographicExtArchive WHERE demographic_no=? ORDER BY id', [patient]),
    updateAudits: audits(UPDATE_AUDIT),
  });
  const update = (firstName, suffix, extras) => ({
    ...stored, firstName, extras,
    alert: `${s.marker}-alert${suffix}`, notes: `${s.marker}-notes${suffix}`,
    waitingListID: list, waitingListNote: `${s.marker}-note${suffix}`,
  });
  const extra = (key, value, id) => ({ ...(id ? { id } : {}), key, value, demographicNo: patient, providerNo: s.provider });

  await s.step('a REST update stores every companion record and archives what it replaced', async () => {
    const auditsBefore = audits(UPDATE_AUDIT);
    const accessBefore = audits(ACCESS_SUCCESS);
    const response = await s.context.request.put(endpoint, {
      data: update('Updated', 1, [extra('demo_cell', `${s.marker}-cell1`, cell), extra('hPhoneExt', `${s.marker}-ext1`)]),
      maxRedirects: 0,
    });
    h.assert(response.status() === 200, `update expected HTTP 200, received ${response.status()}`);
    await response.dispose();

    const now = JSON.parse(state());
    h.assert(JSON.stringify(now.patient) === JSON.stringify([['Updated', s.marker, s.provider]]),
      `The patient row is ${JSON.stringify(now.patient)}, expected the new first name attributed to the signed-in provider`);
    h.assert(now.patientArchive.length === 1 && now.patientArchive[0][0] === 'Workflow',
      `The patient archive is ${JSON.stringify(now.patientArchive)}, expected one copy of the row as it was`);
    h.assert(now.custom.length === 1 && now.custom[0][2] === `${s.marker}-alert1` && now.custom[0][4] === `${s.marker}-notes1`,
      'The custom fields were not saved');
    h.assert(JSON.stringify(now.customArchive) === JSON.stringify([[`${s.marker}-alert0`, `${s.marker}-notes0`]]),
      `The custom-field archive is ${JSON.stringify(now.customArchive)}, expected one copy of the previous alert and notes`);
    h.assert(JSON.stringify(now.waitingList) === JSON.stringify([[`${s.marker}-note0`, '1', 'Y'], [`${s.marker}-note1`, '1', 'N']]),
      `The waiting-list entries are ${JSON.stringify(now.waitingList)}, expected the old entry kept as history and one current entry`);
    h.assert(JSON.stringify(now.extensions) === JSON.stringify([['demo_cell', `${s.marker}-cell1`], ['hPhoneExt', `${s.marker}-ext1`]]),
      `The extensions are ${JSON.stringify(now.extensions)}, expected the changed cell phone and the new extension`);
    h.assert(JSON.stringify(now.extensionArchive) === JSON.stringify([['demo_cell', `${s.marker}-cell0`]]),
      `The extension archive is ${JSON.stringify(now.extensionArchive)}, expected one copy of the previous cell phone`);
    h.assert(now.updateAudits === auditsBefore + 1, 'The update wrote no audit row for the signed-in provider');
    h.assert(count('SELECT COUNT(*) FROM log WHERE action=? AND provider_no=? AND data=?',
      [UPDATE_AUDIT, s.provider, `demographicNo=${patient}`]) === 1, 'The update audit row does not name the patient');
    h.assert(audits(ACCESS_SUCCESS) === accessBefore + 1, 'The successful call was not recorded in the access log');
  });

  await s.step('an update whose last extension is refused leaves every record as it was', async () => {
    const before = state();
    const failuresBefore = audits(ACCESS_FAILURE);
    const response = await s.context.request.put(endpoint, {
      data: update('Refused', 2, [extra('demo_cell', `${s.marker}-cell2`, cell),
        extra('cytolNum', `${s.marker}-dupA`), extra('cytolNum', `${s.marker}-dupB`)]),
      maxRedirects: 0,
    });
    h.assert(response.status() >= 400, `a refused update expected an error status, received ${response.status()}`);
    await response.dispose();

    const after = state();
    h.assert(after === before, `The refused update left changes behind.\nbefore: ${before}\nafter:  ${after}`);
    // Found by value: a row the rollback orphaned would not be found through the patient.
    h.assert(count('SELECT COUNT(*) FROM demographicExt WHERE value LIKE ?', [`${s.marker}-dup%`]) === 0,
      'The refused update left an extension behind');
    h.assert(audits(ACCESS_FAILURE) === failuresBefore + 1, 'The refused call was not recorded as a failure in the access log');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('demographic-rest-update-atomicity', workflow, { openMaster: false });
