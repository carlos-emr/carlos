/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * Put back the audit columns the application moves on a DEMO row a check had to touch.
 *
 * Some checks cannot run on an owned patient: the Rx checks reprint the demo dataset's own prescription (script 45 of patient 1 is the
 * documented unsigned fixture) and stage that patient's pharmacy links. The check puts back the column it changed (the signature
 * association, the link status), but the application also moves two audit columns on the way and nothing restores them:
 *
 *   prescription.dates_reprinted   every reprint appends "<time>;<provider>" to it, and the row's lastUpdateDate is stamped.
 *   demographicPharmacy.addDate    is ON UPDATE CURRENT_TIMESTAMP, so switching a link's status off and on stamps it with the
 *                                  time of the switch, which the pharmacy list then shows as the date the pharmacy was added.
 *
 * snapshot(sql, patient) reads both for the patient, and restore(sql, snapshot) writes them back explicitly (an explicit assignment
 * of an ON UPDATE column keeps the assigned value). Both are COLUMN-level restores of rows the check did not create; rows a check
 * creates are removed by key instead (lib/owned-patient.js).
 *
 * `sql` is anything with rows(query) and execute(query). NULL is read with an explicit IS NULL flag, because `mysql -B` prints SQL
 * NULL and the text 'NULL' alike (see harness unescapeMysqlBatchValue).
 */

const POSITIVE = /^[1-9]\d*$/;
const DATETIME = /^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d$/;

const quote = (value) => `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;

/** The audit columns of the patient's prescriptions and pharmacy links, as they are now. */
function snapshot(sql, patient) {
  if (!POSITIVE.test(String(patient))) throw new Error('the patient\'s demographic_no is not a number');
  return {
    patient: String(patient),
    scripts: sql.rows(`SELECT script_no, IFNULL(dates_reprinted, ''), dates_reprinted IS NULL, lastUpdateDate FROM prescription
      WHERE demographic_no=${patient} ORDER BY script_no`),
    links: sql.rows(`SELECT id, addDate FROM demographicPharmacy WHERE demographic_no=${patient} ORDER BY id`),
  };
}

/** Write the snapshot's audit columns back, one row at a time, keyed by the row's own id, for the snapshot's patient only. */
function restore(sql, state) {
  for (const [script, reprinted, reprintedIsNull, updated] of state.scripts) {
    if (!POSITIVE.test(script) || !DATETIME.test(updated)) throw new Error('a prescription snapshot row is not a script number and a time');
    sql.execute(`UPDATE prescription SET dates_reprinted=${reprintedIsNull === '1' ? 'NULL' : quote(reprinted)}, lastUpdateDate=${quote(updated)}
      WHERE script_no=${script} AND demographic_no=${state.patient}`);
  }
  for (const [id, added] of state.links) {
    if (!POSITIVE.test(id) || !DATETIME.test(added)) throw new Error('a pharmacy link snapshot row is not an id and a time');
    sql.execute(`UPDATE demographicPharmacy SET addDate=${quote(added)} WHERE id=${id} AND demographic_no=${state.patient}`);
  }
}

module.exports = { restore, snapshot };
