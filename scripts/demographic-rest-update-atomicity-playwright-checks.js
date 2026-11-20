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
 * Step 3: an update that renames the stored extension to a key the patient already holds violates
 * the same index, but as an UPDATE of an existing row, which the application executes later than
 * the INSERT of a new one. It is answered and rolled back exactly as step 2 is; it used to be
 * refused only when the transaction committed and reported as HTTP 500.
 * Step 4: an update in which the stored extension gives up its key and a new extension takes that
 * key is valid in the order given, and both rows are stored. With the stored row's UPDATE left
 * until after the INSERT, the new row met the key the stored one still held and was refused.
 * Step 5: an update that sends the id of another patient's extension is refused (409,
 * DEMOGRAPHIC_EXTENSION_NOT_FOUND) before either chart changes. The id alone used to re-point that
 * row to this patient and overwrite it.
 * Step 6: an update that records a waiting-list note keeps the patient's start date and place on
 * the list. The replacement entry used to be dated today, and the list is renumbered by that
 * date, so the patient dropped behind the second patient, who joined a year later.
 * Step 7: an update that names a waiting list the patient was taken off after the form was loaded
 * (the application keeps the removed entry as history) is refused (409,
 * WAITING_LIST_ENTRY_NOT_FOUND) and rolled back. It used to put the patient back on the list.
 * Steps 8 and 9: the same refusal for a list on which the patient has a NULL history flag or no
 * entry at all. Those rows do not qualify as current.
 *
 * The patient is the runWorkflow fixture (last name = this run's FAKE-PW<hex> marker, which the
 * updates keep). The waiting list is a list of its own, named by the marker, so renumbering it
 * touches no other patient's entry. Step 5's second patient has this run's marker plus "-V" as its
 * last name; step 6 puts it on the list as well. Cleanup removes the patient's rows from each table
 * the update writes, the second patient with its extension and list entry, then the list, and
 * verifies them gone. Environment: the common contract (BASE_URL,
 * TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*) and EXCLUSIVE=1.
 */
'use strict';
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createRecoverablePatientFixture } = require('./lib/owned-patient');

const UPDATE_AUDIT = 'DemographicManager.updateDemographic';
const ACCESS_SUCCESS = 'REST WS: DemographicService.updateDemographicData';
const ACCESS_FAILURE = 'REST WS: FAILURE: DemographicService.updateDemographicData';
/** Every table the update writes for the patient, child rows first. */
const PATIENT_TABLES = [
  ['waitingList', 'demographic_no'], ['demographicExtArchive', 'demographic_no'],
  ['demographicExt', 'demographic_no'], ['demographiccustArchive', 'demographic_no'],
  ['demographiccust', 'demographic_no'], ['demographicArchive', 'demographic_no'],
];

function preflightConfig() {
  if (process.env.EXCLUSIVE !== '1') {
    throw new h.SkipCheck('Requires an exclusive run because its waiting list is visible clinic-wide');
  }
}

async function workflow(s) {
  const endpoint = h.appUrl(s.config.baseUrl, '/ws/rs/demographics');
  const patient = Number(s.patient);
  const count = (sql, parameters) => Number(s.sql.value(sql, parameters));
  const audits = action => count('SELECT COUNT(*) FROM log WHERE action=? AND provider_no=?', [action, s.provider]);
  /** Step 5's second patient, whose extension the update must not be able to claim; on the list from step 6. */
  const victimName = `${s.marker}-V`;
  const victimRows = 'SELECT demographic_no FROM demographic WHERE last_name=?';

  h.assert(count('SELECT COUNT(*) FROM waitingListName WHERE name=?', [s.marker]) === 0,
    'A waiting list with this run\'s marker already exists');
  s.cleanup(() => {
    for (const [table, column] of PATIENT_TABLES) {
      s.sql.execute(`DELETE FROM ${table} WHERE ${column}=?`, [patient]);
      h.assert(count(`SELECT COUNT(*) FROM ${table} WHERE ${column}=?`, [patient]) === 0,
        `Owned ${table} rows were not removed`);
    }
    for (const table of ['waitingList', 'demographicExtArchive', 'demographicExt']) {
      s.sql.execute(`DELETE FROM ${table} WHERE demographic_no IN (${victimRows})`, [victimName]);
    }
    s.sql.execute('DELETE FROM demographic WHERE last_name=?', [victimName]);
    h.assert(count('SELECT COUNT(*) FROM demographic WHERE last_name=?', [victimName]) === 0,
      'The second patient was not removed');
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

  /**
   * Sends an update that must be refused. The client must be told why (HTTP 409 with the documented
   * error code; `meaning` says what the code identifies), and nothing of the update may have been
   * stored. `refusedValues` matches the extension values only this update carries.
   */
  const expectRefusal = async (what, data, code, meaning, refusedValues) => {
    const before = state();
    const failuresBefore = audits(ACCESS_FAILURE);
    const response = await s.context.request.put(endpoint, { data, maxRedirects: 0 });
    h.assert(response.status() === 409, `${what} expected HTTP 409, received ${response.status()}`);
    const failure = await response.json().catch(() => null);
    h.assert(failure?.code === code, `the response did not identify ${meaning}`);
    await response.dispose();

    const after = state();
    h.assert(after === before, `The refused update left changes behind.\nbefore: ${before}\nafter:  ${after}`);
    // Found by value: a row the rollback orphaned would not be found through the patient.
    h.assert(count('SELECT COUNT(*) FROM demographicExt WHERE value LIKE ?', [refusedValues]) === 0,
      'The refused update left an extension behind');
    h.assert(audits(ACCESS_FAILURE) === failuresBefore + 1, 'The refused call was not recorded as a failure in the access log');
  };

  const expectDuplicateKeyConflict = (what, data, refusedValues) => expectRefusal(what, data,
    'DUPLICATE_DEMOGRAPHIC_EXTENSION_KEY', 'the duplicate demographic extension key', refusedValues);

  await s.step('an update whose last extension is refused leaves every record as it was', async () => {
    await expectDuplicateKeyConflict('a duplicate-key update',
      update('Refused', 2, [extra('demo_cell', `${s.marker}-cell2`, cell),
        extra('cytolNum', `${s.marker}-dupA`), extra('cytolNum', `${s.marker}-dupB`)]),
      `${s.marker}-dup%`);
  });

  await s.step('a stored extension renamed onto a key the patient already holds is refused the same way', async () => {
    // Step 1 left the patient a stored hPhoneExt; this gives the stored demo_cell row that key.
    await expectDuplicateKeyConflict('renaming a stored extension onto a held key',
      update('Renamed', 3, [extra('hPhoneExt', `${s.marker}-cell3`, cell)]),
      `${s.marker}-cell3`);
  });

  await s.step('a stored extension can give up the key a new one takes in the same update', async () => {
    const auditsBefore = audits(UPDATE_AUDIT);
    const response = await s.context.request.put(endpoint, {
      data: update('Reordered', 4, [extra('cytolNum', `${s.marker}-cell4`, cell), extra('demo_cell', `${s.marker}-new4`)]),
      maxRedirects: 0,
    });
    h.assert(response.status() === 200, `an update valid in the order given expected HTTP 200, received ${response.status()}`);
    await response.dispose();

    const now = JSON.parse(state());
    h.assert(JSON.stringify(now.patient) === JSON.stringify([['Reordered', s.marker, s.provider]]),
      `The patient row is ${JSON.stringify(now.patient)}, expected the new first name`);
    // Ordered by id: the stored row under its new key, step 1's extension, then the new row.
    h.assert(JSON.stringify(now.extensions) === JSON.stringify([['cytolNum', `${s.marker}-cell4`],
      ['hPhoneExt', `${s.marker}-ext1`], ['demo_cell', `${s.marker}-new4`]]),
      `The extensions are ${JSON.stringify(now.extensions)}, expected the stored row renamed and a new demo_cell row`);
    h.assert(JSON.stringify(now.extensionArchive) === JSON.stringify([['demo_cell', `${s.marker}-cell0`],
      ['demo_cell', `${s.marker}-cell1`]]),
      `The extension archive is ${JSON.stringify(now.extensionArchive)}, expected a copy of the stored row before each change`);
    h.assert(now.updateAudits === auditsBefore + 1, 'The update wrote no audit row for the signed-in provider');
  });

  await s.step('an extension id of another patient is refused before either chart changes', async () => {
    const victimValue = `${s.marker}-victim`;
    s.sql.execute(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,
      sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (?,'Victim','1975','05','06','M','AC',?,'ON','ON','NR',NOW())`, [victimName, s.provider]);
    const victim = Number(s.sql.value(victimRows, [victimName]));
    h.assert(Number.isSafeInteger(victim) && victim > 0 && victim !== patient, 'The second patient fixture was not created');
    s.sql.execute(`INSERT INTO demographicExt (demographic_no,provider_no,key_val,value,date_time)
      VALUES (?,?,'demo_cell',?,NOW())`, [victim, s.provider, victimValue]);
    const victimExt = Number(s.sql.value('SELECT id FROM demographicExt WHERE demographic_no=?', [victim]));
    h.assert(Number.isSafeInteger(victimExt) && victimExt > 0, 'The second patient\'s extension fixture was not created');

    // Sent as this patient's own extension: only the id says whose row it is.
    await expectRefusal('claiming another patient\'s extension by id',
      update('Claimed', 5, [extra('wPhoneExt', `${s.marker}-claim5`, victimExt)]),
      'DEMOGRAPHIC_EXTENSION_NOT_FOUND', 'the extension that is not this patient\'s', `${s.marker}-claim5`);

    const victimRow = s.sql.rows('SELECT demographic_no,key_val,value FROM demographicExt WHERE id=?', [victimExt]);
    h.assert(JSON.stringify(victimRow) === JSON.stringify([[String(victim), 'demo_cell', victimValue]]),
      `The other patient's extension row is ${JSON.stringify(victimRow)}, expected it unchanged and still theirs`);
    h.assert(count('SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=?', [victim]) === 0,
      'The refused update archived the other patient\'s extension');
  });

  await s.step('an update that records a note keeps the patient\'s start date and place on the list', async () => {
    const victim = Number(s.sql.value(victimRows, [victimName]));
    h.assert(Number.isSafeInteger(victim) && victim > 0 && victim !== patient, 'The second patient is missing');
    // The owned patient joined the list first; the second patient joined it a year later.
    s.sql.execute(`UPDATE waitingList SET onListSince='2020-05-04 00:00:00'
      WHERE listID=? AND demographic_no=? AND is_history='N'`, [list, patient]);
    s.sql.execute(`INSERT INTO waitingList (listID,demographic_no,note,position,onListSince,is_history)
      VALUES (?,?,?,2,'2021-06-01 00:00:00','N')`, [list, victim, `${s.marker}-later`]);
    const currentEntries = () => JSON.stringify(s.sql.rows(`SELECT demographic_no,position,
      DATE_FORMAT(onListSince,'%Y-%m-%d') FROM waitingList WHERE listID=? AND is_history='N' ORDER BY position,id`, [list]));
    const expected = JSON.stringify([[String(patient), '1', '2020-05-04'], [String(victim), '2', '2021-06-01']]);
    h.assert(currentEntries() === expected, `The list fixture is ${currentEntries()}, expected ${expected}`);

    const response = await s.context.request.put(endpoint, { data: update('Noted', 6, []), maxRedirects: 0 });
    h.assert(response.status() === 200, `an update that records a note expected HTTP 200, received ${response.status()}`);
    await response.dispose();

    h.assert(count("SELECT COUNT(*) FROM waitingList WHERE listID=? AND demographic_no=? AND is_history='N' AND note=?",
      [list, patient, `${s.marker}-note6`]) === 1, 'The note was not recorded on a current entry');
    // The list is renumbered by start date: an entry dated today would be second.
    h.assert(currentEntries() === expected,
      `After the note was recorded the list is ${currentEntries()}, expected ${expected}`);
  });

  await s.step('an update naming a waiting list the patient was taken off does not put them back', async () => {
    // The application's removal (WLWaitingListUtil.removeFromWaitingList) keeps the entry as
    // history. It happens after the form that names the list was loaded.
    s.sql.execute("UPDATE waitingList SET is_history='Y' WHERE listID=? AND demographic_no=?", [list, patient]);
    h.assert(count('SELECT COUNT(*) FROM waitingList WHERE listID=? AND demographic_no=?', [list, patient]) > 0
      && count("SELECT COUNT(*) FROM waitingList WHERE listID=? AND demographic_no=? AND is_history<>'Y'", [list, patient]) === 0,
      'The waiting-list entries were not turned into history');

    // expectRefusal compares every waiting-list row before and after: no current entry may appear.
    await expectRefusal('an update naming a waiting list the patient was taken off',
      update('Stale', 7, [extra('wPhoneExt', `${s.marker}-stale7`)]),
      'WAITING_LIST_ENTRY_NOT_FOUND', 'the removed waiting-list entry', `${s.marker}-stale7`);
  });

  await s.step('an update naming a list with only a NULL history flag is refused', async () => {
    s.sql.execute('UPDATE waitingList SET is_history=NULL WHERE listID=? AND demographic_no=?', [list, patient]);
    h.assert(count('SELECT COUNT(*) FROM waitingList WHERE listID=? AND demographic_no=? AND is_history IS NULL',
      [list, patient]) > 0, 'No waiting-list rows with a NULL history flag were created');
    await expectRefusal('an update naming a list with only a NULL history flag',
      update('Stale', 8, [extra('wPhoneExt', `${s.marker}-stale8`)]),
      'WAITING_LIST_ENTRY_NOT_FOUND', 'the NULL history flag', `${s.marker}-stale8`);
  });

  await s.step('an update naming a waiting list the patient has no entry on is refused, not half saved', async () => {
    s.sql.execute('DELETE FROM waitingList WHERE listID=? AND demographic_no=?', [list, patient]);
    h.assert(count('SELECT COUNT(*) FROM waitingList WHERE listID=? AND demographic_no=?', [list, patient]) === 0,
      'The waiting-list entries were not removed');

    await expectRefusal('an update naming a waiting list the patient has no entry on',
      update('Stale', 9, [extra('wPhoneExt', `${s.marker}-stale9`)]),
      'WAITING_LIST_ENTRY_NOT_FOUND', 'the missing waiting-list entry', `${s.marker}-stale9`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('demographic-rest-update-atomicity', workflow,
  {openMaster: false, preflightConfig, patientFixtureFactory: createRecoverablePatientFixture});
