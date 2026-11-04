/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * The eForm INSTANCES a check saves for a patient it does not own, removed again by key.
 *
 * Most eForm checks save an instance (a filled-in form) and delete it through the application, which only marks it removed
 * (eform_data.status 0) and leaves the instance, its eform_values and the attachments chosen for it (EFormDocs) in the tables. The
 * residue audit (lib/residue-audit.js) counts the rows: a run of eform-rtl-attachment-pdf left 25 instances, 245 values and 20
 * attachment rows on demo patient 1. A check that can run on an owned patient does (lib/owned-patient.js, eformRows()); the Rich
 * Text Letter attachment checks cannot, because what they assert is the demo patient's documents, labs, forms and HRM reports in
 * the attach popup, so they keep the demo patient and remove what they saved:
 *
 *   mark = markEformInstances(sql)        before the check saves anything
 *   removeEformInstancesSince(sql, mark, patient)   after, by instance number
 *
 * Only instances of that patient with a number above the mark are removed, with their values, and only attachment rows above the id
 * mark: the demo dataset ships EFormDocs rows for instance numbers 247 to 1063 that no instance owns any more, an instance saved at
 * one of those numbers inherits them, and a row of the demo's is never the run's to delete.
 *
 * `sql` is anything with value(query), rows(query) and execute(query).
 */

const POSITIVE = /^[1-9]\d*$/;

/** Take the mark before the check saves its first instance: the highest instance number and the highest attachment-row id. */
function markEformInstances(sql) {
  const fdid = Number(sql.value('SELECT IFNULL(MAX(fdid), 0) FROM eform_data'));
  const docs = Number(sql.value('SELECT IFNULL(MAX(id), 0) FROM EFormDocs'));
  if (!Number.isSafeInteger(fdid) || !Number.isSafeInteger(docs)) throw new Error('the eForm instance mark was not readable');
  return { fdid, docs };
}

/** Remove, for the given patient, every instance saved after `mark`, with its values and attachment rows, then assert none is left. */
function removeEformInstancesSince(sql, mark, patient) {
  if (!POSITIVE.test(String(patient))) throw new Error('removeEformInstancesSince needs the patient\'s demographic_no');
  if (!Number.isSafeInteger(mark.fdid) || !Number.isSafeInteger(mark.docs)) throw new Error('the eForm instance mark is not a mark');
  const ids = sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND fdid > ${mark.fdid}`)
    .map(([id]) => id).filter((id) => POSITIVE.test(id));
  if (!ids.length) return;
  const list = ids.join(',');
  sql.execute(`DELETE FROM eform_values WHERE fdid IN (${list});
    DELETE FROM EFormDocs WHERE fdid IN (${list}) AND id > ${mark.docs};
    DELETE FROM eform_data WHERE fdid IN (${list}) AND demographic_no=${patient}`);
  const left = sql.value(`SELECT (SELECT COUNT(*) FROM eform_values WHERE fdid IN (${list}))
    + (SELECT COUNT(*) FROM EFormDocs WHERE fdid IN (${list}) AND id > ${mark.docs})
    + (SELECT COUNT(*) FROM eform_data WHERE fdid IN (${list}))`);
  if (left !== '0') throw new Error('The eForm instances this run saved, or their values or attachments, were not removed');
}

module.exports = { markEformInstances, removeEformInstancesSince };
