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
 * Browser check for HL7 lab upload, driven the way an operator does it: log in,
 * open the Inbox hub, click "HL7 Lab Upload" (a popup), choose a file and a lab
 * type, click Upload. This is the `lab-upload-hl7` surface the 2026.08 coverage
 * plan listed with no script behind it (issue #3774).
 *
 * The lab is a SYNTHETIC CML HL7 v2.3 message built here, with a per-run
 * accession number and the run's FAKE- patient in PID, so its content is unique and
 * every row it creates can be found and removed. It is never a real result.
 *
 * Steps:
 *   1. First upload: the popup reports "Uploaded successfully", the lab
 *      reaches hl7TextInfo under this run's accession, and patientLabRouting
 *      links it to the run's patient.
 *   2. Same file again: the popup reports "Already uploaded" (FileUploadCheck's
 *      MD5 gate) and no second hl7TextInfo row appears.
 *   3. The ON CML uploader (lab/CMLlabUpload) answers its XML outcome for the
 *      same bytes. A wrong key must answer accessDenied. With CML_UPLOAD_KEY set
 *      to the server's key, the already-recorded file must answer
 *      uploadedPreviously -- before #3915 a duplicate answered an empty
 *      <outcome/>. That half is skipped (and says so) when CML_UPLOAD_KEY is
 *      unset, because the package ships no key and the action then refuses
 *      every request.
 *
 * Both CML posts carry the session's CSRF token as the CSRF-TOKEN header, the
 * way an AJAX client does, so the CSRF filter and (through the packaged front
 * door) ModSecurity see a legitimate multipart lab upload.
 *
 * FIXTURE SAFETY: the synthetic patient, and every hl7TextMessage,
 * hl7TextInfo, patientLabRouting, providerLabRouting, measurement and
 * fileUploadCheck row the upload creates, are removed in cleanup by this run's
 * accession and stamped file name, pass or fail. The uploaders also ARCHIVE the file under
 * DOCUMENT_DIR as LabUpload.lab-upload-probe-<stamp>.hl7.<millis>. With
 * LAB_UPLOAD_DOCUMENT_STORE pointing at that directory as this process sees it,
 * cleanup deletes exactly the files carrying this run's stamp and asserts none
 * remain. Without it a browser cannot reach them, so they are kept and the
 * check says so; remove them later by that prefix.
 *
 * Environment (beyond the common contract in lib/playwright-harness.js):
 *   CML_UPLOAD_KEY             the server's CML_UPLOAD_KEY; enables the
 *                              uploadedPreviously half of step 3. Unset: that
 *                              half is skipped.
 *   LAB_UPLOAD_DOCUMENT_STORE  the server's DOCUMENT_DIR, mounted or local;
 *                              enables removal of this run's archived files.
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const contextPathOf = (baseUrl) => new URL(baseUrl).pathname.replace(/\/+$/, '');

/** A minimal CML ORU^R01 v2.3 message for the run's synthetic patient. */
function syntheticCmlLab(accession, patientLast) {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const ts = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
    + `${pad(now.getHours())}${pad(now.getMinutes())}`;
  return [
    // The backslash is doubled so the template literal emits MSH-2 as the four HL7 encoding
    // characters ^~\&; HAPI rejects anything shorter before the CML handler sees the message.
    `MSH|^~\\&|Reports|CML|||${ts}-500||ORU^R01||1|2.3`,
    `PID|1|||^^ON|${patientLast}^Workflow||19800102|F`,
    `ORC|NW|${accession}|||F|||||||999998^DR. PROBE|||${ts.slice(0, 8)}`,
    `OBR|1|${accession}||ML70^SYNTHETIC PANEL||${ts.slice(0, 8)}|${ts.slice(0, 8)}|||||||||999998^DR. PROBE|||||||||F`,
    'OBX|1|ST|7010^SYNTHETIC RESULT|^^CHEMISTRY|NORMAL|||N|||F||765^1007010||70',
    'NTE|1|L|SYNTHETIC LAB UPLOAD BROWSER CHECK - NOT A PATIENT RESULT',
    'FTS|1',
    '',
  ].join('\r');
}

/** Open the HL7 Lab Upload popup from the Inbox hub, the way an operator does. */
async function openUploader(session) {
  const inbox = await session.context.newPage();
  await h.gotoApp(inbox, session.config.baseUrl, '/web/inboxhub/Inboxhub');
  await inbox.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const link = inbox.locator('a[href*="ViewInsideLabUpload"]').first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  const popup = await session.popup(inbox, link, 'hl7-lab-upload');
  await popup.locator('#uploadForm').waitFor({ state: 'visible', timeout: 20000 });
  return { inbox, popup };
}

/** Upload one file as a CML lab and return the status text the page reports for it. */
async function uploadThroughPopup(session, filePath, fileName) {
  const { inbox, popup } = await openUploader(session);
  try {
    await popup.locator('#importFiles').setInputFiles(filePath);
    await popup.locator('#type').selectOption('CML');
    const [response] = await Promise.all([
      popup.waitForResponse((r) => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/lab/CA/ALL/insideLabUpload'), { timeout: 60000 }),
      popup.locator('#uploadForm button[type="submit"]').click(),
    ]);
    h.assert(response.status() < 400, `HL7 lab upload answered HTTP ${response.status()}`);
    await popup.waitForLoadState('domcontentloaded');
    const item = popup.locator('#file-list .file-item').filter({ hasText: fileName });
    await item.first().waitFor({ state: 'visible', timeout: 20000 });
    return (await item.first().locator('.upload-text').innerText()).trim();
  } finally {
    await popup.close().catch(() => {});
    await inbox.close().catch(() => {});
  }
}

/** POST the file to lab/CMLlabUpload from the logged-in page and return the XML outcome. */
async function postCml(page, contextPath, bytes, fileName, key) {
  const result = await page.evaluate(async ({ context, content, name, uploadKey }) => {
    const tokenInput = document.querySelector('input[name="CSRF-TOKEN"]');
    const form = new FormData();
    form.append('importFile', new Blob([content], { type: 'application/octet-stream' }), name);
    form.append('key', uploadKey);
    const headers = { 'X-Requested-With': 'XMLHttpRequest' };
    if (tokenInput && tokenInput.value) headers['CSRF-TOKEN'] = tokenInput.value;
    const response = await fetch(`${context}/lab/CMLlabUpload`, {
      method: 'POST', body: form, headers, credentials: 'same-origin',
    });
    return { status: response.status, text: await response.text(), hadToken: !!(tokenInput && tokenInput.value) };
  }, {
    context: contextPath, content: bytes.toString('latin1'), name: fileName, uploadKey: key,
  });
  h.assert(result.hadToken, 'The uploader page carried no CSRF token to send with the CML post');
  h.assert(result.status === 200, `lab/CMLlabUpload answered HTTP ${result.status}`);
  const match = /<outcome>([^<]*)<\/outcome>|<outcome\/>/.exec(result.text);
  h.assert(match, 'lab/CMLlabUpload did not answer its labUploadResult XML');
  return match[1] || '';
}

/**
 * Deletes this run's archived uploads from LAB_UPLOAD_DOCUMENT_STORE. Only names built entirely
 * from the fixed probe prefix, this run's random stamp and the uploader's millisecond suffix match,
 * so no other document in the store can be touched.
 */
function removeArchivedUploads(stamp) {
  const store = process.env.LAB_UPLOAD_DOCUMENT_STORE;
  if (!store) {
    console.warn(`    archived uploads retained: set LAB_UPLOAD_DOCUMENT_STORE to remove LabUpload.lab-upload-probe-${stamp}.hl7.*`);
    return;
  }
  const root = fs.realpathSync(store);
  const prefix = `LabUpload.lab-upload-probe-${stamp}.hl7.`;
  const ownFiles = () => fs.readdirSync(root)
    .filter((name) => /^LabUpload\.lab-upload-probe-[0-9A-F]{8}\.hl7\.\d+$/.test(name) && name.startsWith(prefix));
  const found = ownFiles();
  // name matched the fixed pattern above and is joined to the resolved store root.
  for (const name of found) fs.unlinkSync(path.join(root, name)); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
  // Every run archives at least the first upload; finding none means the store or the archive
  // name no longer matches, and cleanup would otherwise pass while leaving the files behind.
  h.assert(found.length > 0, `No archived LabUpload.lab-upload-probe-${stamp}.hl7.* file was found in LAB_UPLOAD_DOCUMENT_STORE`);
  h.assert(ownFiles().length === 0, 'The archived synthetic lab uploads were not all removed');
}

async function workflow(session) {
  const { sql, patient, marker, cleanup, config } = session;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const accession = `LU${stamp}`;
  const fileName = `lab-upload-probe-${stamp}.hl7`;
  const content = Buffer.from(syntheticCmlLab(accession, marker), 'latin1');
  // The uploader records the saved file's name (LabUpload.<name>.<millis>), which carries the
  // run's random stamp, so the check finds its own fileUploadCheck row without hashing.
  const ownUpload = `filename LIKE ${h.sqlString(`%${fileName}%`)}`;
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-upload-'));
  // fileName is built from a fixed prefix and random hex, never from input.
  const filePath = path.join(workDir, fileName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
  fs.writeFileSync(filePath, content);
  h.assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE ${ownUpload}`) === '0',
    'A fileUploadCheck row already carries this run\'s stamp');

  cleanup(async () => {
    fs.rmSync(workDir, { recursive: true, force: true });
    const labs = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`)
      .map(([labNo]) => labNo).filter((labNo) => /^\d+$/.test(labNo));
    if (labs.length) {
      const list = labs.join(',');
      const measurements = sql.rows(`SELECT measurement_id FROM measurementsExt
        WHERE keyval='lab_no' AND val IN (${labs.map((labNo) => h.sqlString(labNo)).join(',')})`)
        .map(([id]) => id).filter((id) => /^\d+$/.test(id));
      if (measurements.length) {
        const ids = measurements.join(',');
        sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN (${ids});
          DELETE FROM measurements WHERE id IN (${ids})`);
      }
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list});
        DELETE FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list});
        DELETE FROM hl7TextInfo WHERE lab_no IN (${list});
        DELETE FROM hl7TextMessage WHERE lab_id IN (${list})`);
    }
    sql.execute(`DELETE FROM fileUploadCheck WHERE ${ownUpload}`);
    removeArchivedUploads(stamp);
    h.assert(sql.value(`SELECT
        (SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)})
      + (SELECT COUNT(*) FROM fileUploadCheck WHERE ${ownUpload})`) === '0',
    'The synthetic lab rows were not all removed');
  });

  await session.step('first HL7 upload is filed', async () => {
    const status = await uploadThroughPopup(session, filePath, fileName);
    h.assert(status === 'Uploaded successfully', `First upload reported "${status}"`);
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`, '1',
      'The uploaded lab did not reach hl7TextInfo under its accession');
    await expectValue(sql, `SELECT COUNT(*) FROM fileUploadCheck WHERE ${ownUpload}`, '1',
      'The upload recorded no fileUploadCheck row for its content');
    const matched = sql.value(`SELECT COUNT(*) FROM patientLabRouting pl JOIN hl7TextInfo h
      ON h.lab_no=pl.lab_no AND pl.lab_type='HL7'
      WHERE h.accessionNum=${h.sqlString(accession)} AND pl.demographic_no=${patient}`);
    // PID carries the run patient's unique surname, DOB and sex, so matching is deterministic;
    // a lab that files but stays unmatched is a routing regression, not a pass.
    h.assert(matched === '1', `The uploaded lab was not routed to the run's patient (matched ${matched})`);
  });

  await session.step('the same file again is refused as already uploaded', async () => {
    const status = await uploadThroughPopup(session, filePath, fileName);
    h.assert(status === 'Already uploaded', `Duplicate upload reported "${status}"`);
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`) === '1',
      'The duplicate upload filed a second copy of the lab');
  });

  await session.step('lab/CMLlabUpload answers a distinct XML outcome', async () => {
    const { inbox, popup } = await openUploader(session);
    try {
      const contextPath = contextPathOf(config.baseUrl);
      const denied = await postCml(popup, contextPath, content, fileName, `wrong-${stamp}`);
      h.assert(denied === 'accessDenied', `A wrong CML key answered "${denied}"`);
      const key = process.env.CML_UPLOAD_KEY || '';
      if (!key) {
        console.log('    SKIP uploadedPreviously half: CML_UPLOAD_KEY is not set');
        return;
      }
      const duplicate = await postCml(popup, contextPath, content, fileName, key);
      h.assert(duplicate === 'uploadedPreviously',
        `An already-recorded file answered "${duplicate}" instead of uploadedPreviously`);
      h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`) === '1',
        'The CML duplicate filed another copy of the lab');
    } finally {
      await popup.close().catch(() => {});
      await inbox.close().catch(() => {});
    }
  });
}

if (require.main === module) runWorkflow('lab-upload', workflow);
module.exports = { workflow, syntheticCmlLab };
