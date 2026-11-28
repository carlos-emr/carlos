#!/usr/bin/env node
/**
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
 * End-to-end browser and MariaDB verification for atomic, serialized, idempotent
 * lab measurement filing (issue #6391 / PR #6422).
 *
 * Steps:
 *   1. Upload a matched multi-OBX CML ORU^R01 report through Inbox > HL7 Lab Upload.
 *      Assert that after commit, exactly 2 active measurements are filed with complete
 *      measurementsExt sets (lab_no, abnormal, identifier, name, other_id, unit, accession)
 *      and zero measurementsDeleted rows exist.
 *   2. Upload a corrected version (status C, newer OBR-7/OBR-22, updated values) for the
 *      same accession. Assert that the original 2 measurements are archived to
 *      measurementsDeleted, zero active measurements remain for the original lab_no,
 *      and only the corrected version's 2 measurements remain active (and render cleanly
 *      in the lab display UI).
 *   3. Upload a late older version (status F, earlier OBR-7/OBR-22) for the same accession.
 *      Assert that the corrected version's 2 measurements remain the sole active set and
 *      the late delivery writes no measurements.
 *   4. Upload an unmatched multi-OBX CML ORU^R01 report (unknown patient last name).
 *      Assert 0 active measurements exist initially. Then issue 3 concurrent POSTs to
 *      /oscarMDS/PatientMatch linking the lab to the owned patient, followed by a repeat
 *      match POST. Assert every request succeeds with HTTP 200 {"success":true} and that
 *      exactly 2 active measurements (zero duplicates) with complete measurementsExt sets
 *      exist for the matched lab.
 *
 * FIXTURE SAFETY: uses runWorkflow's owned FAKE- patient and per-run stamped accessions.
 * Cleanup deletes every owned lab row (hl7TextInfo, hl7TextMessage, patientLabRouting,
 * providerLabRouting, unshared providerLabRoutingLock, fileUploadCheck), active and
 * archived measurements plus their measurementsExt rows, and archived upload files under
 * LAB_UPLOAD_DOCUMENT_STORE, asserting zero residue.
 *
 * Environment (beyond the common contract in lib/playwright-harness.js):
 *   LAB_UPLOAD_DOCUMENT_STORE  the server's DOCUMENT_DIR, mounted or local;
 *                              enables removal of this run's archived upload files.
 */

'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { uploadThroughPopup } = require('./lab-upload-playwright-checks');
const { removeOwnedHl7Labs } = require('./lab-forwarding-rules-playwright-checks');
const { openInbox, openSidebar, setMode, search } = require('./inbox-file-forward-playwright-checks');

const TIMEOUT = 30000;
const q = h.sqlString;
const contextPathOf = (baseUrl) => new URL(baseUrl).pathname.replace(/\/+$/, '');

const VERSIONS = {
  original: {
    key: 'ORIG',
    time: '20260312090000',
    date: '2026-03-12 09:00:00',
    status: 'F',
    glucose: '5.4',
    glucoseFlag: 'N',
    creatinine: '78',
    creatinineFlag: 'N',
  },
  corrected: {
    key: 'CORR',
    time: '20260312130000',
    date: '2026-03-12 13:00:00',
    status: 'C',
    glucose: '9.8',
    glucoseFlag: 'H',
    creatinine: '112',
    creatinineFlag: 'H',
  },
  older: {
    key: 'OLD',
    time: '20260312070000',
    date: '2026-03-12 07:00:00',
    status: 'F',
    glucose: '4.1',
    glucoseFlag: 'N',
    creatinine: '61',
    creatinineFlag: 'N',
  },
  unmatched: {
    key: 'UNM',
    time: '20260312100000',
    date: '2026-03-12 10:00:00',
    status: 'F',
    glucose: '6.2',
    glucoseFlag: 'N',
    creatinine: '84',
    creatinineFlag: 'N',
  },
};

/** Multi-OBX synthetic CML ORU^R01 v2.3 message with two distinct chemistry results. */
function multiObxCmlLab(accession, lastName, version) {
  const orc = Array(16).fill('');
  const obr = Array(26).fill('');
  const obx1 = Array(15).fill('');
  const obx2 = Array(15).fill('');
  Object.assign(orc, { 0: 'ORC', 1: 'RE', 2: accession, 5: version.status, 12: '999998^DR. PROBE', 15: version.time });
  Object.assign(obr, {
    0: 'OBR', 1: '1', 2: accession, 4: 'ML70^SYNTHETIC CHEM PANEL',
    6: version.time, 7: version.time, 16: '999998^DR. PROBE', 22: version.time, 25: version.status,
  });
  Object.assign(obx1, {
    0: 'OBX', 1: '1', 2: 'NM', 3: '7010^SYNTHETIC GLUCOSE', 4: '^^CHEMISTRY',
    5: version.glucose, 6: 'mmol/L', 7: '3.6-6.0', 8: version.glucoseFlag, 11: version.status, 14: version.time,
  });
  Object.assign(obx2, {
    0: 'OBX', 1: '2', 2: 'NM', 3: '7020^SYNTHETIC CREATININE', 4: '^^CHEMISTRY',
    5: version.creatinine, 6: 'umol/L', 7: '50-98', 8: version.creatinineFlag, 11: version.status, 14: version.time,
  });
  return [
    `MSH|^~\\&|Reports|CML|||${version.time}||ORU^R01|${accession}${version.key}|P|2.3`,
    `PID|1||||${lastName}^Workflow||19800102|F`,
    orc.join('|'),
    obr.join('|'),
    obx1.join('|'),
    obx2.join('|'),
    `NTE|1|L|${lastName} ${version.key} SYNTHETIC MEASUREMENT FILING CHECK - NOT A PATIENT RESULT`,
    'FTS|1',
    '',
  ].join('\r');
}

async function settled(page) {
  await page.waitForLoadState('load', { timeout: TIMEOUT });
  try {
    await page.waitForLoadState('networkidle', { timeout: 5000 });
  } catch (error) {
    if (error.name !== 'TimeoutError') throw error;
  }
}

/** Remove any leftover archived upload files matching this run's unique stamp prefix. */
function removeStampedArchives(filePrefix) {
  const store = process.env.LAB_UPLOAD_DOCUMENT_STORE;
  if (!store) {
    console.warn(`    archived lab uploads may be retained: set LAB_UPLOAD_DOCUMENT_STORE to remove LabUpload.${filePrefix}*`);
    return;
  }
  const root = fs.realpathSync(store);
  const prefix = `LabUpload.${filePrefix}`;
  const mine = fs.readdirSync(root).filter((name) => name.startsWith(prefix));
  for (const name of mine) {
    h.assert(/^LabUpload\.lab-meas-filing-[0-9A-F]{8}-[a-z]+\.hl7\.\d+$/.test(name),
      `Unexpected archive name for this run: ${name}`);
  }
  for (const name of mine) {
    const file = path.join(root, name); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
    fs.unlinkSync(file);
    h.assert(!fs.existsSync(file), `Archived synthetic lab upload was not removed: ${name}`);
  }
}

/** Read active measurements and their extensions for the given lab_no list. */
function readMeasurementsForLabs(sql, labNos) {
  const valid = labNos.map(String).filter((id) => /^[1-9]\d*$/.test(id));
  if (!valid.length) return [];
  const inClause = valid.map(q).join(',');
  const rows = sql.rows(`SELECT m.id, m.demographicNo, m.dataField, e.keyval, e.val
    FROM measurements m
    JOIN measurementsExt e ON e.measurement_id = m.id
    WHERE m.id IN (SELECT measurement_id FROM measurementsExt WHERE keyval='lab_no' AND val IN (${inClause}))
    ORDER BY m.id, e.id`);
  const byId = new Map();
  for (const [id, demo, dataField, keyval, val] of rows) {
    if (!byId.has(id)) byId.set(id, { id, demographicNo: demo, dataField, ext: {} });
    byId.get(id).ext[keyval] = val;
  }
  return [...byId.values()];
}

/** Verify an exact complete 2-OBX measurement set for one lab version. */
function assertCompleteTwoObxSet(measurements, expectedLabNo, expectedPatient, expectedVersion, label) {
  h.assert(measurements.length === 2,
    `${label}: expected exactly 2 active measurements, found ${measurements.length}`);
  const byIdentifier = new Map(measurements.map((m) => [m.ext.identifier, m]));
  h.assert(byIdentifier.size === 2, `${label}: duplicate or missing OBX identifiers`);
  for (const [identifier, expectedValue, expectedUnit, expectedOtherId] of [
    ['7010', expectedVersion.glucose, 'mmol/L', '0-0'],
    ['7020', expectedVersion.creatinine, 'umol/L', '0-1'],
  ]) {
    const m = byIdentifier.get(identifier);
    h.assert(m, `${label}: missing measurement for identifier ${identifier}`);
    h.assert(m.demographicNo === expectedPatient,
      `${label}: measurement ${identifier} belongs to demographic ${m.demographicNo}, expected ${expectedPatient}`);
    h.assert(m.dataField === expectedValue,
      `${label}: measurement ${identifier} value was ${m.dataField}, expected ${expectedValue}`);
    h.assert(m.ext.lab_no === expectedLabNo,
      `${label}: measurement ${identifier} lab_no was ${m.ext.lab_no}, expected ${expectedLabNo}`);
    h.assert(m.ext.unit === expectedUnit,
      `${label}: measurement ${identifier} unit was ${m.ext.unit}, expected ${expectedUnit}`);
    h.assert(m.ext.other_id === expectedOtherId,
      `${label}: measurement ${identifier} other_id was ${m.ext.other_id}, expected ${expectedOtherId}`);
    for (const requiredKey of ['abnormal', 'name', 'accession', 'datetime']) {
      h.assert(Object.prototype.hasOwnProperty.call(m.ext, requiredKey),
        `${label}: measurement ${identifier} is missing measurementsExt key ${requiredKey}`);
    }
  }
}

async function workflow(s) {
  const { sql, patient, marker, context, recorder } = s;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const matchedAccession = `MF${stamp}A`;
  const unmatchedAccession = `MF${stamp}B`;
  const filePrefix = `lab-meas-filing-${stamp}-`;
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-meas-filing-'));

  s.cleanup(() => {
    fs.rmSync(workDir, { recursive: true, force: true });
    const allLabNos = sql.rows(`SELECT lab_no FROM hl7TextInfo
      WHERE accessionNum IN (${q(matchedAccession)}, ${q(unmatchedAccession)})`).map((row) => row[0]);
    const deletedOriginalIds = sql.rows(`SELECT originalId FROM measurementsDeleted WHERE demographicNo=${patient}`)
      .map(([id]) => id).filter((id) => /^[1-9]\d*$/.test(id));
    removeOwnedHl7Labs(sql, allLabNos);
    if (deletedOriginalIds.length) {
      sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN (${deletedOriginalIds.join(',')})`);
    }
    sql.execute(`DELETE FROM measurementsDeleted WHERE demographicNo=${patient};
      DELETE FROM measurements WHERE demographicNo=${patient};
      DELETE FROM fileUploadCheck WHERE filename LIKE ${q(`LabUpload.${filePrefix}%`)}`);
    removeStampedArchives(filePrefix);
    h.assert(sql.value(`SELECT
        (SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum IN (${q(matchedAccession)}, ${q(unmatchedAccession)}))
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM fileUploadCheck WHERE filename LIKE ${q(`LabUpload.${filePrefix}%`)})`) === '0',
      'Synthetic lab measurement filing fixtures were not completely removed');
  });

  // Ensure the owned patient has empty HIN so Inbox search and PID name/DOB matching behave deterministically.
  sql.execute(`UPDATE demographic SET hin='' WHERE demographic_no=${patient} AND last_name=${q(marker)}`);

  async function uploadVersion(tag, accession, lastName, version) {
    const fileName = `${filePrefix}${tag}.hl7`;
    const filePath = path.join(workDir, fileName);
    fs.writeFileSync(filePath, multiObxCmlLab(accession, lastName, version));
    const outcome = await uploadThroughPopup(s, filePath, fileName);
    h.assert(outcome.includes('Uploaded successfully'),
      `Upload ${tag} was not accepted (got "${outcome}")`);
    const rows = sql.rows(`SELECT lab_no FROM hl7TextInfo
      WHERE accessionNum=${q(accession)} AND obr_date=${q(version.date)} ORDER BY lab_no`);
    h.assert(rows.length === 1 && /^[1-9]\d*$/.test(rows[0][0]),
      `Upload ${tag} did not store one hl7TextInfo row at ${version.date}`);
    return rows[0][0];
  }

  let originalLabNo;
  let correctedLabNo;
  let olderLabNo;
  let unmatchedLabNo;

  await s.step('matched multi-OBX upload atomically files one complete set of measurements after commit', async () => {
    originalLabNo = await uploadVersion('orig', matchedAccession, marker, VERSIONS.original);
    h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting
      WHERE lab_type='HL7' AND lab_no=${originalLabNo} AND demographic_no=${patient}`) === '1',
      'Original lab was not matched to the owned patient');
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`, '2',
      'Original upload did not file exactly 2 measurements');
    const active = readMeasurementsForLabs(sql, [originalLabNo]);
    assertCompleteTwoObxSet(active, originalLabNo, patient, VERSIONS.original, 'original upload');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo=${patient}`) === '0',
      'Original upload unexpectedly archived measurements');
  });

  await s.step('correction archives original measurements and leaves only the corrected values active', async () => {
    correctedLabNo = await uploadVersion('corr', matchedAccession, marker, VERSIONS.corrected);
    h.assert(correctedLabNo !== originalLabNo, 'Correction reused original lab_no');
    await expectValue(sql, `SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo=${patient}`, '2',
      'Correction did not archive the original 2 measurements');
    const active = readMeasurementsForLabs(sql, [originalLabNo, correctedLabNo]);
    assertCompleteTwoObxSet(active, correctedLabNo, patient, VERSIONS.corrected, 'corrected upload');
    h.assert(readMeasurementsForLabs(sql, [originalLabNo]).length === 0,
      'Original lab still has active measurements after correction');
    const archivedValues = sql.rows(`SELECT dataField FROM measurementsDeleted
      WHERE demographicNo=${patient} ORDER BY dataField`).flat();
    h.assert(JSON.stringify(archivedValues) === JSON.stringify([VERSIONS.original.glucose, VERSIONS.original.creatinine]),
      `Archived measurement values differ: ${JSON.stringify(archivedValues)}`);

    // Open the corrected lab in the real UI via Inbox and confirm the corrected values render cleanly.
    const inbox = await openInbox(s.schedule, context, recorder, 'meas-filing-inbox');
    let report;
    try {
      await setMode(inbox, 'list');
      await openSidebar(inbox);
      await inbox.locator('#statusAll').check();
      await inbox.locator('#specificPatients').check();
      await inbox.locator('#inputLastName').fill(marker);
      await search(inbox);
      const row = inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${correctedLabNo}"]`);
      await row.waitFor({ state: 'visible', timeout: TIMEOUT });
      report = await ui.clickOpensPopup(inbox, row.locator('a[onclick*="reportWindow"]').first(),
        { context, recorder, label: 'meas-filing-report', timeout: TIMEOUT });
      await settled(report);
      await h.assertNotErrorPage(report, 'corrected lab display');
      const bodyText = await report.locator('body').innerText();
      h.assert(bodyText.includes(VERSIONS.corrected.glucose) && bodyText.includes(VERSIONS.corrected.creatinine),
        'Corrected lab display did not show both updated result values');
    } finally {
      if (report && !report.isClosed()) await report.close().catch(() => {});
      if (inbox && inbox !== s.schedule && !inbox.isClosed()) await inbox.close().catch(() => {});
    }
  });

  await s.step('late older version does not overwrite or duplicate the corrected measurements', async () => {
    olderLabNo = await uploadVersion('old', matchedAccession, marker, VERSIONS.older);
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${q(matchedAccession)}`) === '3',
      'Late older version was not stored in hl7TextInfo');
    const active = readMeasurementsForLabs(sql, [originalLabNo, correctedLabNo, olderLabNo]);
    assertCompleteTwoObxSet(active, correctedLabNo, patient, VERSIONS.corrected, 'after late older delivery');
    h.assert(readMeasurementsForLabs(sql, [olderLabNo]).length === 0,
      'Late older delivery filed active measurements');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo=${patient}`) === '2',
      'Late older delivery unexpectedly archived the corrected measurements');
  });

  await s.step('concurrent and repeated PatientMatch POSTs serialize and file one duplicate-free measurement set', async () => {
    const unknownLastName = `${marker}-U`;
    unmatchedLabNo = await uploadVersion('unm', unmatchedAccession, unknownLastName, VERSIONS.unmatched);
    h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting
      WHERE lab_type='HL7' AND lab_no=${unmatchedLabNo} AND demographic_no > 0`) === '0',
      'Unmatched lab was unexpectedly linked at upload');
    h.assert(readMeasurementsForLabs(sql, [unmatchedLabNo]).length === 0,
      'Unmatched lab filed measurements before patient matching');

    // Open the real Patient Matching popup (which provides the session's CSRF-TOKEN input)
    // and fire 3 concurrent PatientMatch POSTs, then a 4th repeat POST.
    const matchPage = await context.newPage();
    h.wirePage(matchPage, 'meas-filing-match', recorder);
    try {
      await h.gotoApp(matchPage, s.config.baseUrl,
        `/oscarMDS/SearchPatient?labType=HL7&segmentID=${unmatchedLabNo}&name=${encodeURIComponent(unknownLastName)}`);
      await settled(matchPage);
      await h.assertNotErrorPage(matchPage, 'patient search popup');
      const contextPath = contextPathOf(s.config.baseUrl);
      const concurrent = await matchPage.evaluate(async ({ ctx, labId, demoId }) => {
        const tokenInput = document.querySelector('input[name="CSRF-TOKEN"]');
        const token = tokenInput ? tokenInput.value : '';
        if (!token) throw new Error('PatientSearch page carried no CSRF-TOKEN input');
        const postOnce = async () => {
          const body = 'labNo=' + encodeURIComponent(labId)
            + '&labType=HL7'
            + '&demographicNo=' + encodeURIComponent(demoId)
            + '&CSRF-TOKEN=' + encodeURIComponent(token);
          const response = await fetch(`${ctx}/oscarMDS/PatientMatch`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded',
              Accept: 'application/json',
            },
            body,
            credentials: 'same-origin',
          });
          const text = await response.text();
          return { status: response.status, text };
        };
        const burst = await Promise.all([postOnce(), postOnce(), postOnce()]);
        const repeat = await postOnce();
        return { burst, repeat };
      }, { ctx: contextPath, labId: unmatchedLabNo, demoId: patient });

      for (const [idx, outcome] of [...concurrent.burst, concurrent.repeat].entries()) {
        h.assert(outcome.status === 200,
          `PatientMatch call #${idx + 1} answered HTTP ${outcome.status}`);
        let parsed;
        try {
          parsed = JSON.parse(outcome.text);
        } catch {
          throw new Error(`PatientMatch call #${idx + 1} did not return JSON`);
        }
        h.assert(parsed && parsed.success === true,
          `PatientMatch call #${idx + 1} did not confirm success`);
      }
    } finally {
      await matchPage.close().catch(() => {});
    }

    await expectValue(sql, `SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${unmatchedLabNo}`,
      patient, 'Concurrent PatientMatch did not link the lab to the owned patient');
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`, '4',
      'Expected exactly 4 total active measurements (2 corrected + 2 newly matched)');
    const unmatchedMeasurements = readMeasurementsForLabs(sql, [unmatchedLabNo]);
    assertCompleteTwoObxSet(unmatchedMeasurements, unmatchedLabNo, patient, VERSIONS.unmatched,
      'concurrent and repeated PatientMatch');
    // Confirm the earlier corrected accession's 2 measurements were untouched.
    const matchedMeasurements = readMeasurementsForLabs(sql, [originalLabNo, correctedLabNo, olderLabNo]);
    assertCompleteTwoObxSet(matchedMeasurements, correctedLabNo, patient, VERSIONS.corrected,
      'corrected accession after matching another accession');
  });
}

module.exports = { workflow, multiObxCmlLab };
if (require.main === module) runWorkflow('lab-measurement-filing', workflow);
