/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * The HL7 lab `lab-acknowledge` owns: a COPY of one demo lab, so that the check can acknowledge a lab without touching the demo's.
 *
 * Acknowledging a demo lab deleted the demo's provider-0 routing row for it (the application removes those on acknowledge; see
 * CommonLabResultData:619-629) and nothing put it back, so the demo lost a row on every run (the "Harness damage found by the
 * shrink-aware residue audit" record in docs/ui-tests/deb-install-validation.md). The copy has a lab number and an accession of
 * its own, so the review changes only rows this module made, and removeOwnedLab() removes them by those keys.
 *
 * Both functions are SQL shapes more than logic, and the shape is the contract: which keys a delete may use (the owned lab's
 * number, read from its accession as well as from the copy's return value; the table_modification rows the application files for
 * the provider-0 routing row it deletes, found by the `<lab_no>N</lab_no>` in their XML and by `id` above the floor taken before
 * the first write). scripts/owned-lab.test.js pins them with a recording stub; the live check proves MariaDB accepts them.
 *
 * `sql(query)` returns the query's output as one trimmed string and `sqlRows(query)` its rows as arrays of columns (the older
 * checks' own mysql helpers). `lockNotShared` is the SQL predicate of lab-forwarding-rules that leaves a routing-lock row alone
 * while another lab type also routes that number.
 */

const crypto = require('node:crypto');

const POSITIVE = /^[1-9]\d*$/;

const escapeSql = (value) => String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");

function createOwnedLab({ sql, sqlRows, lockNotShared, accessionFor = () => `ACK${crypto.randomBytes(6).toString('hex').toUpperCase()}` }) {
  // What removal needs to find the copy: its unique accession, the lab number once it exists, and the highest table_modification id
  // before the run (the review files one row there when it deletes the provider-0 routing row).
  let ownedLab = null;

  function assert(condition, message) {
    if (!condition) throw new Error(message);
  }

  /**
   * Copies a demo lab into a lab this run owns and returns the copy's number. One transaction in one session, so a failure leaves
   * nothing behind; the copy has a unique accession, so it is its own latest version and the demo labs sharing the template's
   * accession are untouched. The message row keeps the template's fileUploadCheck_id (the column is NOT NULL); removal deletes the
   * copy's message and never the checksum row, which belongs to the demo.
   */
  function cloneLab(template) {
    const accession = accessionFor();
    // Registered before the first write: removal keys on the accession.
    ownedLab = { accession, labNo: null, modificationFloor: Number(sql('SELECT IFNULL(MAX(id), 0) FROM table_modification')) };
    const copy = sql(`START TRANSACTION;
    INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)
      SELECT fileUploadCheck_id, message, type, serviceName, created FROM hl7TextMessage WHERE lab_id=${Number(template)} LIMIT 1;
    SET @lab = LAST_INSERT_ID();
    INSERT INTO hl7TextInfo (lab_no, sex, health_no, result_status, final_result_count, obr_date, priority, requesting_client,
        discipline, last_name, first_name, report_status, accessionNum, filler_order_num, sending_facility, label)
      SELECT @lab, sex, health_no, result_status, final_result_count, obr_date, priority, requesting_client,
        discipline, last_name, first_name, report_status, '${accession}', filler_order_num, sending_facility, label
      FROM hl7TextInfo WHERE lab_no=${Number(template)} LIMIT 1;
    INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created)
      SELECT demographic_no, @lab, 'HL7', created FROM patientLabRouting WHERE lab_no=${Number(template)} AND lab_type='HL7' LIMIT 1;
    INSERT INTO providerLabRouting (provider_no, lab_no, status, comment, timestamp, lab_type)
      SELECT provider_no, @lab, status, comment, timestamp, lab_type FROM providerLabRouting
      WHERE lab_no=${Number(template)} AND lab_type='HL7' AND provider_no='0';
    COMMIT;
    SELECT @lab`);
    assert(POSITIVE.test(copy) && Number(copy) > Number(template), `the copy of demo lab ${template} has no lab number of its own (${copy})`);
    assert(sql(`SELECT (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${Number(copy)} AND accessionNum='${accession}')
    + (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${Number(copy)})
    + (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${Number(copy)} AND lab_type='HL7')`) === '3',
    `the copy of demo lab ${template} is missing its info, message or patient link`);
    ownedLab.labNo = copy;
    return copy;
  }

  /**
   * Removes the owned copy and everything the review wrote for it, by its own lab number, and asserts it gone. The lab number is
   * read from the accession too, so a copy whose number was never returned is still found. The routing lock is the row the
   * application files when it routes or acknowledges the lab; it is deleted unless another lab type also routes that number.
   */
  function removeOwnedLab() {
    if (ownedLab === null) return;
    const accession = escapeSql(ownedLab.accession);
    const numbers = new Set(sqlRows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum='${accession}'`).map(([no]) => no));
    if (ownedLab.labNo) numbers.add(String(ownedLab.labNo));
    const labs = [...numbers].filter((no) => POSITIVE.test(no));
    if (!labs.length) return;
    const list = labs.join(',');
    // The review files a table_modification row for each provider-0 routing row it deletes; it names the lab number in its XML.
    const modifications = labs.map((no) => `resultSet LIKE '%<lab_no>${no}</lab_no>%'`).join(' OR ');
    sql(`DELETE FROM table_modification WHERE id > ${Number(ownedLab.modificationFloor)} AND table_name='providerLabRouting'
      AND modification_type='delete' AND (${modifications});
    DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list});
    DELETE FROM providerLabRoutingLock WHERE lab_no IN (${list}) AND ${lockNotShared};
    DELETE FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list});
    DELETE FROM hl7TextInfo WHERE lab_no IN (${list}) AND accessionNum='${accession}';
    DELETE FROM hl7TextMessage WHERE lab_id IN (${list})`);
    assert(sql(`SELECT (SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}))
    + (SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no IN (${list}) AND ${lockNotShared})
    + (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}))
    + (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no IN (${list}))
    + (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id IN (${list}))
    + (SELECT COUNT(*) FROM table_modification WHERE id > ${Number(ownedLab.modificationFloor)} AND table_name='providerLabRouting'
        AND modification_type='delete' AND (${modifications}))`) === '0',
    'the owned copy of the demo lab, or a row the review wrote for it, was not removed');
  }

  return { cloneLab, removeOwnedLab };
}

module.exports = { createOwnedLab };
