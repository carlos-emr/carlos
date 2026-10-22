/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * An owned FAKE patient for the checks that are NOT built on runWorkflow() (which creates and removes
 * its own), so that they stop writing to a demo patient's chart.
 *
 * WHY. Writing to demo patient 1 on a shared install leaves damage no later run can see: `echart` archived (never removed) two
 * Social History notes, appended to the demo's casemgmt_cpp row, moved the update date of demo notes 27 and 28 and deleted the demo's
 * encounter-note draft; `allergy-add-penicillin` left the demo's "No Known Drug Allergies" row archived (the application archives it
 * when an allergy is added); `clinical-freetext` saved the demo record twice a run, which rewrote its province and newsletter codes;
 * `eform-render` saved an eForm instance for it. A patient the run creates and removes by key cannot do that.
 *
 * The patient is the one runWorkflow() would make (last name = the run marker, so the residue audit's marker pass can find a
 * survivor), and removeOwnedPatient() deletes by the PATIENT'S key everything the chart pages write for a patient: the notes and
 * their link/extension/issue rows, the CPP row, the draft, the note lock, the eChart row, the demographic archive and extension
 * rows. A check that writes more (allergies, eForm instances, consultation requests) names the extra tables: `extra` for a table
 * keyed by the patient, `via` for one keyed by a parent row of the patient's (a consultation request's extension rows). The
 * bundles ALLERGY_ROWS, CONSULTATION_ROWS and eformRows(sql) are the ones several checks need (combine two with
 * combineRows()). A `via` entry may carry `above: { column, value }` to delete only rows written after a mark.
 *
 * `sql` is anything with value(query) and execute(query): the harness client, or a thin adapter over an older check's own
 * mysql helper.
 */

const { randomBytes } = require('node:crypto');

const POSITIVE = /^[1-9]\d*$/;
const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_]*$/;
const MARKER = /^FAKE-PW[0-9a-f]{16}$/;

/** [table, patient column] of every table the chart pages write rows into, keyed by the patient directly. */
const PATIENT_KEYED_TABLES = Object.freeze([
  ['casemgmt_issue', 'demographic_no'],
  ['casemgmt_cpp', 'demographic_no'],
  ['casemgmt_note_lock', 'demographic_no'],
  ['casemgmt_tmpsave', 'demographic_no'],
  ['eChart', 'demographicNo'],
  ['measurementsDeleted', 'demographicNo'],
  ['demographicExt', 'demographic_no'],
  ['demographicExtArchive', 'demographic_no'],
  ['demographicArchive', 'demographic_no'],
  ['demographiccust', 'demographic_no'],
]);

/** What recording allergies for the patient writes beyond the chart's own rows. */
const ALLERGY_ROWS = Object.freeze({ extra: [['allergies', 'demographic_no']], via: [] });

/**
 * What saving an eForm for the patient writes: the instance (eform_data), its values and the attachments chosen for it (EFormDocs).
 * The admin library's and the patient's form list's Delete only mark a row removed (status 0), so nothing but a delete by key takes
 * them out. The eForm TEMPLATE a check imports is not the patient's; it is deleted by its own name.
 *
 * Call it BEFORE the check writes anything: the demo dataset ships EFormDocs rows (attached documents, labs, forms and HRM reports)
 * for instance numbers 247 to 1063 that no eform_data row owns any more, and an instance saved at one of those numbers inherits
 * them. The attachment rows are therefore deleted only above the highest id taken here, so a row of the demo's is never the run's
 * to remove.
 */
function eformRows(sql) {
  const attachmentFloor = Number(sql.value('SELECT IFNULL(MAX(id), 0) FROM EFormDocs'));
  if (!Number.isSafeInteger(attachmentFloor)) throw new Error('the EFormDocs mark was not readable');
  return {
    extra: [['eform_data', 'demographic_no']],
    via: [
      { table: 'eform_values', column: 'fdid', parent: 'eform_data', key: 'fdid', patient: 'demographic_no' },
      { table: 'EFormDocs', column: 'fdid', parent: 'eform_data', key: 'fdid', patient: 'demographic_no', above: { column: 'id', value: attachmentFloor } },
    ],
  };
}

/**
 * What saving a consultation request writes beyond the chart's own rows: the request, its archive, the extension, archive-extension
 * and document rows hanging off the request number, and the provider's signature image stored against the patient (a foreign key
 * to demographic, so the patient cannot be deleted while one is left).
 */
const CONSULTATION_ROWS = Object.freeze({
  extra: [['DigitalSignature', 'demographicId'], ['consultationRequestsArchive', 'demographicNo'], ['consultationRequests', 'demographicNo']],
  via: [
    { table: 'consultationRequestExt', column: 'requestId', parent: 'consultationRequests', key: 'requestId', patient: 'demographicNo' },
    { table: 'consultationRequestExtArchive', column: 'requestId', parent: 'consultationRequests', key: 'requestId', patient: 'demographicNo' },
    { table: 'consultdocs', column: 'requestId', parent: 'consultationRequests', key: 'requestId', patient: 'demographicNo' },
  ],
});

/** The union of several bundles, for a check that writes more than one kind of row. */
function combineRows(...bundles) {
  return { extra: bundles.flatMap((bundle) => bundle.extra || []), via: bundles.flatMap((bundle) => bundle.via || []) };
}

const quote = (value) => `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;

/** A run marker in the form the residue audit looks for: FAKE-PW and 16 hex digits. */
function newOwnedMarker() {
  return `FAKE-PW${randomBytes(8).toString('hex')}`;
}

/**
 * Insert the owned patient, the same row runWorkflow() inserts, and return its demographic_no.
 *
 * @param {{ marker: string, provider: string, firstName?: string }} fixture
 * @returns {string} the new demographic_no
 */
function createOwnedPatient(sql, { marker, provider, firstName = 'Workflow' }) {
  if (!MARKER.test(marker)) throw new Error('the owned patient needs a FAKE-PW<16 hex> marker (newOwnedMarker())');
  if (!provider) throw new Error('the owned patient needs the test provider\'s number');
  const patient = sql.value(`INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
     provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${quote(marker)},${quote(firstName)},'1980','01','02','F','AC',
      ${quote(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  if (!POSITIVE.test(String(patient))) throw new Error('The owned patient fixture was not created');
  return String(patient);
}

/**
 * Remove the owned patient and everything the chart pages wrote for it, by its key, then assert none is left.
 *
 * The parent row is deleted only while it still carries the marker as its last name, so a patient that is not this run's can
 * never be removed here. Call it BEFORE the check deletes any parent row of its own (a consultation request, an eForm instance):
 * `via` rows are found through their parent, and once the parent is gone nothing names them (a consultation check that deleted
 * its request first left a consultationRequestExt row per run, which the residue audit reported). `extra` lists the tables a check writes beyond the chart's own: [table, patient column] pairs.
 *
 * @param {string} patient the demographic_no createOwnedPatient() returned
 * @param {string} marker the marker the patient was created with
 * @param {{ extra?: Array<[string, string]>, via?: Array<{table: string, column: string, parent: string, key: string, patient: string}> }} [options]
 *   `via`: rows of `table` whose `column` names a `parent` row (its `key`) that belongs to the patient (its `patient` column)
 */
function removeOwnedPatient(sql, patient, marker, { extra = [], via = [] } = {}) {
  if (!POSITIVE.test(String(patient))) throw new Error('removeOwnedPatient needs the owned patient\'s demographic_no');
  if (!MARKER.test(marker)) throw new Error('removeOwnedPatient needs the FAKE-PW<16 hex> marker the patient was created with');
  for (const [table, column] of extra) {
    if (!IDENTIFIER.test(table) || !IDENTIFIER.test(column)) throw new Error(`not a table and column name: ${table}.${column}`);
  }
  for (const link of via) {
    for (const name of [link.table, link.column, link.parent, link.key, link.patient, ...(link.above ? [link.above.column] : [])]) {
      if (!IDENTIFIER.test(name)) throw new Error(`not a table or column name: ${name}`);
    }
    if (link.above && !Number.isSafeInteger(link.above.value)) throw new Error('the mark of a hanging row must be an integer');
  }
  // The rows that hang off a parent row of the patient's, selected through the parent while it still exists (and, when a mark
  // was taken, only those written after it).
  const hanging = (link) => `SELECT ${link.key} FROM ${link.parent} WHERE ${link.patient}=${patient}`;
  const hangingWhere = (link) => `${link.column} IN (${hanging(link)})${link.above ? ` AND ${link.above.column} > ${link.above.value}` : ''}`;
  if (sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND last_name=${quote(marker)}`) !== '1') {
    throw new Error('Patient fixture ownership changed');
  }
  const keyed = [...PATIENT_KEYED_TABLES, ...extra];
  const owned = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
  if (via.length) {
    // First, and counted while the parent rows that name them still exist: once those are deleted, no query can find a survivor.
    sql.execute(via.map((link) => `DELETE FROM ${link.table} WHERE ${hangingWhere(link)}`).join(';\n    '));
    if (sql.value(`SELECT ${via.map((link) => `(SELECT COUNT(*) FROM ${link.table} WHERE ${hangingWhere(link)})`).join(' + ')}`) !== '0') {
      throw new Error('The rows hanging off the owned patient\'s records were not removed');
    }
  }
  sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${owned});
    DELETE FROM casemgmt_note_ext WHERE note_id IN (${owned});
    DELETE FROM casemgmt_note_link WHERE note_id IN (${owned});
    DELETE FROM casemgmt_note WHERE demographic_no=${patient};
    ${keyed.map(([table, column]) => `DELETE FROM ${table} WHERE ${column}=${patient}`).join(';\n    ')}`);
  const left = sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
    ${keyed.map(([table, column]) => `+ (SELECT COUNT(*) FROM ${table} WHERE ${column}=${patient})`).join('\n    ')}`);
  if (left !== '0') throw new Error('The owned patient\'s chart rows were not removed');
  sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${quote(marker)}`);
  if (sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) !== '0') {
    throw new Error('The owned patient was not removed');
  }
}

module.exports = {
  ALLERGY_ROWS, CONSULTATION_ROWS, PATIENT_KEYED_TABLES, combineRows, createOwnedPatient, eformRows, newOwnedMarker, removeOwnedPatient,
};
