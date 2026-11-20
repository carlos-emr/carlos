#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * HL7 lab upload for merged patients (issue #3880), driven the way an operator does it: Inbox hub
 * ▸ HL7 Lab Upload (popup) ▸ choose a file and the CML lab type ▸ Upload.
 * A merged source record keeps the HIN it shared with its head, so an incoming lab used to match
 * two demographic rows and stay unmatched. Asserts: a lab whose HIN belongs to a merged source and
 * its head files and routes to the head (one patientLabRouting row, none to the source); a lab
 * whose HIN also belongs to a second, unmerged patient stays unmatched, because two distinct heads
 * is a real ambiguity and a lab must never be delivered to the wrong patient.
 * Fixtures: the run's FAKE- patient is the head; a merged duplicate and an unrelated patient are
 * seeded by SQL and the merge row is written by SQL. Cleanup removes both labs' hl7TextMessage,
 * hl7TextInfo, routing, measurement and fileUploadCheck rows by this run's accessions, the merge
 * row and the seeded patients, and asserts none remain. LAB_UPLOAD_DOCUMENT_STORE (see
 * lab-upload-playwright-checks.js) lets cleanup delete the archived upload copies as well.
 * The server-side rejection of a crafted merge that would create a cycle is asserted by
 * demographic-merge-playwright-checks.js.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {syntheticCmlLab, uploadThroughPopup, removeArchivedUploads} = require('./lab-upload-playwright-checks');

async function workflow(s) {
  const {sql, patient: head, marker, provider} = s;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const hin = `998${String(Number.parseInt(stamp, 16) % 10000000).padStart(7, '0')}`;
  const mergedAccession = `MP${stamp}`;
  const ambiguousAccession = `MA${stamp}`;
  const accessions = [mergedAccession, ambiguousAccession];
  const accessionList = accessions.map(h.sqlString).join(',');
  const fileName = `lab-upload-probe-${stamp}.hl7`;
  const ownUpload = `filename LIKE ${h.sqlString(`%${fileName}%`)}`;
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-merged-lab-'));
  const ids = [head];

  const seed = firstName => {
    const id = sql.value(`INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,hin,lastUpdateDate)
      VALUES (${h.sqlString(marker)},${h.sqlString(firstName)},'1980','01','02','F','AC',
        ${h.sqlString(provider)},'ON','ON','NR',${h.sqlString(hin)},NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `The ${firstName} patient fixture was not created`);
    ids.push(id);
    return id;
  };
  const upload = async accession => {
    const filePath = path.join(workDir, fileName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
    fs.writeFileSync(filePath, Buffer.from(syntheticCmlLab(accession, marker, hin), 'latin1'));
    return uploadThroughPopup(s, filePath, fileName);
  };
  const routedTo = (accession, demographic) => sql.value(`SELECT COUNT(*) FROM patientLabRouting pl
    JOIN hl7TextInfo i ON i.lab_no=pl.lab_no AND pl.lab_type='HL7'
    WHERE i.accessionNum=${h.sqlString(accession)} AND pl.demographic_no=${demographic}`);
  const routes = accession => sql.value(`SELECT COUNT(*) FROM patientLabRouting pl
    JOIN hl7TextInfo i ON i.lab_no=pl.lab_no AND pl.lab_type='HL7'
    WHERE i.accessionNum=${h.sqlString(accession)}`);

  s.cleanup(() => {
    fs.rmSync(workDir, {recursive: true, force: true});
    const labs = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum IN (${accessionList})`)
      .map(([labNo]) => labNo).filter(labNo => /^\d+$/.test(labNo));
    if (labs.length) {
      const list = labs.join(',');
      const measurements = sql.rows(`SELECT measurement_id FROM measurementsExt
        WHERE keyval='lab_no' AND val IN (${labs.map(h.sqlString).join(',')})`)
        .map(([id]) => id).filter(id => /^\d+$/.test(id));
      if (measurements.length) {
        const measurementList = measurements.join(',');
        sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN (${measurementList});
          DELETE FROM measurements WHERE id IN (${measurementList})`);
      }
      sql.execute(`DELETE FROM providerLabRoutingLock WHERE lab_no IN (${list});
        DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list});
        DELETE FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list});
        DELETE FROM hl7TextInfo WHERE lab_no IN (${list});
        DELETE FROM hl7TextMessage WHERE lab_id IN (${list})`);
    }
    sql.execute(`DELETE FROM fileUploadCheck WHERE ${ownUpload}`);
    for (const [id] of sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${h.sqlString(marker)}`)) {
      if (/^[1-9]\d*$/.test(id) && !ids.includes(id)) ids.push(id);
    }
    const list = ids.join(',');
    // Children first: demographic_merged.merged_to is a foreign key to demographic. Only
    // owned (marker-named) patients are deleted; the harness removes the head itself.
    sql.execute(`DELETE FROM demographic_merged WHERE demographic_no IN (${list}) OR merged_to IN (${list})`);
    sql.execute(`DELETE FROM demographic WHERE demographic_no IN (${list}) AND demographic_no<>${head}
      AND last_name=${h.sqlString(marker)}`);
    removeArchivedUploads(stamp);
    h.assert(sql.value(`SELECT
        (SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum IN (${accessionList}))
      + (SELECT COUNT(*) FROM fileUploadCheck WHERE ${ownUpload})
      + (SELECT COUNT(*) FROM demographic_merged WHERE demographic_no IN (${list}) OR merged_to IN (${list}))
      + (SELECT COUNT(*) FROM demographic WHERE demographic_no IN (${list}) AND demographic_no<>${head})`) === '0',
    'The merged-patient lab fixtures were not all removed');
  });

  let duplicate;
  await s.step('a lab for a HIN shared by a merged source and its head files and routes to the head', async () => {
    sql.execute(`UPDATE demographic SET hin=${h.sqlString(hin)} WHERE demographic_no=${head}`);
    duplicate = seed('Duplicate');
    sql.execute(`INSERT INTO demographic_merged (demographic_no,merged_to,deleted,lastUpdateUser,lastUpdateDate)
      VALUES (${duplicate},${head},0,${h.sqlString(provider)},NOW())`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE hin=${h.sqlString(hin)}`) === '2',
      'The merged pair does not share the fixture HIN');
    const status = await upload(mergedAccession);
    h.assert(status === 'Uploaded successfully', `The merged-patient lab upload reported "${status}"`);
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(mergedAccession)}`, '1',
      'The uploaded lab did not reach hl7TextInfo');
    h.assert(routedTo(mergedAccession, head) === '1' && routes(mergedAccession) === '1',
      `The lab was not routed to the head record alone (head ${routedTo(mergedAccession, head)}, total ${routes(mergedAccession)})`);
    h.assert(routedTo(mergedAccession, duplicate) === '0', 'The lab was routed to the merged source record');
  });

  await s.step('a HIN also carried by an unmerged patient stays unmatched', async () => {
    const other = seed('Unrelated');
    const status = await upload(ambiguousAccession);
    h.assert(status === 'Uploaded successfully', `The ambiguous lab upload reported "${status}"`);
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(ambiguousAccession)}`, '1',
      'The ambiguous lab did not reach hl7TextInfo');
    h.assert(routes(ambiguousAccession) === '0',
      `A lab matching two different patients was routed to one of them (${routedTo(ambiguousAccession, head)} head, ${routedTo(ambiguousAccession, other)} other)`);
  });
}

if (require.main === module) runWorkflow('lab-merged-patient-routing', workflow);
module.exports = {workflow};
