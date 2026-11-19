#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
// Issue #4693. A consultation PDF can draw only HL7 lab reports. A referral that also has a CML,
// MDS or PathNet (BCP) lab attached used to print and fax with that lab simply missing, and nothing
// said so. This check drives a referral with one HL7 lab and one MDS lab attached and asserts that
// the MDS lab is named everywhere the packet goes:
//   1. Print (the AJAX preview): the PDF is the consultation letter, then a page naming the MDS lab,
//      then the HL7 lab; the reply's warning, shown as an alert, names it too.
//   2. The legacy printPdf2 route streams the same three parts.
//   3. Update And Print Preview: the confirmation page names it and does not close itself.
//   4. Update And Fax: the fax cover page names it apart from the attachments the fax contains.
//      Nothing is sent: the check cancels on the cover page.
// The MDS lab is named with its discipline and date when the install lists MDS labs (MDS_LABS=yes)
// and as "details unavailable" otherwise. The check reads which from the referral's own lab row
// (an unlisted attachment is drawn with a Remove button) and expects the matching name.
// Fixtures, all owned and removed afterwards: the FAKE patient (runWorkflow); a FAKE service,
// specialist and referral; a copy of one demo HL7 lab (message, info and patient routing, with no
// inbox routing) for the patient; an MDS lab (mdsMSH, mdsZRG and patient routing) for the patient;
// the consultdocs rows attaching both; and what Update writes (archive and ext rows, the stamp
// DigitalSignature). Needs Poppler pdftotext. Skips when the install has no demo HL7 lab to copy.
const crypto = require('node:crypto');
const fs = require('node:fs');
const { spawnSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const { insertId } = h;

/** The action every consultation button posts to. */
const CONSULT_POST = /\/encounter\/RequestConsultation(\?|$)/;
/** The MDS lab's report time, as the consultation listing prints it. */
const MDS_DATE_TIME = '2024-03-05 10:22:00';
const NOTICE_TITLE = 'Attachments not included';
const POSITIVE = /^[1-9]\d*$/;

/** Text of each page of a PDF; pdftotext ends every page, the last one too, with a form feed. */
function pdfPages(bytes, what) {
  h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', `${what} is not a PDF`);
  const run = spawnSync('pdftotext', ['-enc', 'UTF-8', '-', '-'], { input: bytes, maxBuffer: 32 * 1024 * 1024 });
  h.assert(run.status === 0, `${what} is not a readable PDF`);
  const pages = run.stdout.toString('utf8').split('\f').map(page => page.replace(/\s+/g, ' ').trim());
  if (pages.length && pages[pages.length - 1] === '') pages.pop();
  return pages;
}

/**
 * The packet's order: the consultation letter, then one page naming the lab it cannot include, then
 * the HL7 lab (LabPDFCreator heads every lab with "Patient Name:"; the letter does not).
 */
function assertPacket(pages, expectedName, reason, what) {
  h.assert(pages.length >= 3, `${what} has ${pages.length} page(s); expected the letter, the notice and the HL7 lab`);
  h.assert(pages[0].includes(reason), `${what} does not start with the owned referral's letter`);
  h.assert(pages[1].includes(NOTICE_TITLE), `${what} has no page naming the lab it could not include after the letter`);
  h.assert(pages[1].includes(expectedName), `${what}'s notice page does not name the MDS lab as "${expectedName}"`);
  h.assert(!pages[1].includes('Patient Name:'), `${what}'s notice page carries lab content; the MDS lab was rendered`);
  h.assert(pages.slice(2).some(page => page.includes('Patient Name:')), `${what} does not carry the HL7 lab after the notice`);
  h.assert(pages.filter(page => page.includes(NOTICE_TITLE)).length === 1, `${what} names the lab on more than one page`);
}

async function workflow(s) {
  const { sql, marker, patient, provider, config } = s;
  if (spawnSync('pdftotext', ['-v']).error) throw new h.SkipCheck('Install Poppler pdftotext to read the consultation PDF');
  const template = sql.value(`SELECT i.lab_no FROM hl7TextInfo i JOIN hl7TextMessage m ON m.lab_id=i.lab_no
    JOIN patientLabRouting r ON r.lab_no=i.lab_no AND r.lab_type='HL7' ORDER BY i.lab_no LIMIT 1`);
  if (!POSITIVE.test(template || '')) throw new h.SkipCheck('There is no demo HL7 lab to copy; load the demo dataset');

  const serviceName = `${marker}-Svc`;
  const lastName = `${marker}-Spec`;
  const reason = `${marker} referral with an MDS lab`;
  const accession = `PW4693${crypto.randomBytes(6).toString('hex').toUpperCase()}`;
  const owned = { requestId: null, serviceId: null, hl7Lab: null, mdsLab: null };

  // Registered before the first write. The owned rows are found by the run's own keys (the patient,
  // the unique accession and reason), so a run that fails half way still removes what it wrote.
  s.cleanup(() => {
    const requests = owned.requestId ? [owned.requestId] : sql.rows(`SELECT requestId FROM consultationRequests
      WHERE demographicNo=${patient} AND reason=${h.sqlString(reason)}`).map(([id]) => id).filter(id => POSITIVE.test(id));
    for (const id of requests) {
      h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE requestId=${id} AND demographicNo=${patient}`) === '1',
        'Referral fixture ownership changed');
      sql.execute(`DELETE FROM consultdocs WHERE requestId=${id};
        DELETE FROM consultationRequestExtArchive WHERE requestId=${id};
        DELETE FROM consultationRequestsArchive WHERE requestId=${id} AND demographicNo=${patient};
        DELETE FROM consultationRequestExt WHERE requestId=${id};
        DELETE FROM consultationRequests WHERE requestId=${id} AND demographicNo=${patient}`);
    }
    sql.execute(`DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='CONSULTATION'`);
    const hl7Labs = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`)
      .map(([no]) => no).filter(no => POSITIVE.test(no));
    if (owned.hl7Lab) hl7Labs.push(String(owned.hl7Lab));
    for (const lab of new Set(hl7Labs)) {
      sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7' AND demographic_no=${patient};
        DELETE FROM hl7TextInfo WHERE lab_no=${lab} AND accessionNum=${h.sqlString(accession)};
        DELETE FROM hl7TextMessage WHERE lab_id=${lab}`);
    }
    const mdsLabs = sql.rows(`SELECT segmentID FROM mdsMSH WHERE demographic_no=${patient} AND messageConID=${h.sqlString(accession)}`)
      .map(([no]) => no).filter(no => POSITIVE.test(no));
    for (const lab of mdsLabs) {
      sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='MDS' AND demographic_no=${patient};
        DELETE FROM mdsZRG WHERE segmentID=${lab};
        DELETE FROM mdsMSH WHERE segmentID=${lab} AND messageConID=${h.sqlString(accession)}`);
    }
    if (owned.serviceId) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${owned.serviceId}`);
    sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)};
      DELETE FROM professionalSpecialists WHERE lName=${h.sqlString(lastName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM patientLabRouting WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)})
      + (SELECT COUNT(*) FROM mdsMSH WHERE messageConID=${h.sqlString(accession)})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='CONSULTATION')
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${h.sqlString(lastName)})`) === '0',
    'An owned referral, lab, signature, service or specialist row remains');
  });

  owned.serviceId = insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${h.sqlString(serviceName)},'1')`, 'service');
  const specId = insertId(sql, `INSERT INTO professionalSpecialists
    (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Ann',${h.sqlString(lastName)},'555-0160','555-0161','2 Synthetic Way',NOW(),0,0,0,0)`, 'specialist');
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${owned.serviceId},${specId})`);
  owned.requestId = insertId(sql, `INSERT INTO consultationRequests
    (referalDate,serviceId,specId,providerNo,demographicNo,status,urgency,reason,clinicalInfo,currentMeds,
     allergies,concurrentProblems,statusText,sendTo,patientWillBook,lastUpdateDate)
    VALUES(CURDATE(),${owned.serviceId},${specId},${h.sqlString(provider)},${patient},'1','2',${h.sqlString(reason)},
     'Synthetic clinical information','','','','','',0,NOW())`, 'referral');

  // A copy of a demo HL7 lab for the owned patient: same message, a unique accession (so it is its
  // own latest version), routed to this patient only and to no provider's inbox.
  owned.hl7Lab = sql.value(`START TRANSACTION;
    INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)
      SELECT fileUploadCheck_id, message, type, serviceName, created FROM hl7TextMessage WHERE lab_id=${Number(template)} LIMIT 1;
    SET @lab = LAST_INSERT_ID();
    INSERT INTO hl7TextInfo (lab_no, sex, health_no, result_status, final_result_count, obr_date, priority, requesting_client,
        discipline, last_name, first_name, report_status, accessionNum, filler_order_num, sending_facility, label)
      SELECT @lab, sex, health_no, result_status, final_result_count, obr_date, priority, requesting_client,
        discipline, last_name, first_name, report_status, ${h.sqlString(accession)}, filler_order_num, sending_facility, label
      FROM hl7TextInfo WHERE lab_no=${Number(template)} LIMIT 1;
    INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created) VALUES (${patient}, @lab, 'HL7', NOW());
    COMMIT;
    SELECT @lab`);
  h.assert(POSITIVE.test(owned.hl7Lab || ''), 'The copy of the demo HL7 lab has no lab number');

  // An MDS lab for the owned patient. mdsMSH, mdsZRG and the routing row are what the consultation
  // listing joins when MDS_LABS=yes; a report group outside MICRO and DIAGNOSTIC IMAGING lists as
  // Hem/Chem/Other. The accession goes in messageConID so cleanup can find it.
  owned.mdsLab = insertId(sql, `INSERT INTO mdsMSH (sendingApp, dateTime, type, messageConID, processingID, versionID,
      acceptAckType, appAckType, demographic_no)
    VALUES ('FAKE-LAB', ${h.sqlString(MDS_DATE_TIME)}, 'ORU^R01', ${h.sqlString(accession)}, 'P', '2.3', 'AL', 'NE', ${patient})`, 'MDS lab');
  sql.execute(`INSERT INTO mdsZRG (segmentID, reportSequence, reportGroupID, reportGroupDesc, reportGroupHeading)
      VALUES (${owned.mdsLab}, '1', 'CHEM', 'FAKE CHEMISTRY', '');
    INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created) VALUES (${patient}, ${owned.mdsLab}, 'MDS', NOW())`);
  h.assert(owned.mdsLab !== owned.hl7Lab, 'The fixture labs share a number; the check needs them distinct');

  sql.execute(`INSERT INTO consultdocs (requestId, document_no, doctype, lab_type, deleted, attach_date, provider_no)
      VALUES (${owned.requestId}, ${owned.hl7Lab}, 'L', 'HL7', NULL, CURDATE(), ${h.sqlString(provider)}),
             (${owned.requestId}, ${owned.mdsLab}, 'L', 'MDS', NULL, CURDATE(), ${h.sqlString(provider)})`);

  const openReferral = async label => {
    const page = await s.context.newPage();
    h.wireStrictPage(page, label, s.recorder);
    await page.goto(`${config.baseUrl}/encounter/ViewRequest?de=${patient}&requestId=${owned.requestId}&appNo=0&teamVar=`, // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- every interpolated value is a validated integer and the base URL is validated
      { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    h.assert(await page.locator('[name="reasonForConsultation"]').inputValue() === reason,
      'The edit screen opened a referral other than the owned one');
    return page;
  };

  let expectedName;
  await s.step('the referral lists both labs as attached', async () => {
    const page = await openReferral('consult-labs-rows');
    h.assert(await page.locator(`#entry_labNoHL7${owned.hl7Lab}`).count() === 1, 'The HL7 lab is not listed as attached');
    const mdsRow = page.locator(`#entry_labNoMDS${owned.mdsLab}`);
    h.assert(await mdsRow.count() === 1, 'The MDS lab is not listed as attached');
    const listed = await mdsRow.locator('.removeUnavailableLab').count() === 0;
    expectedName = listed ? `MDS lab result: Hem/Chem/Other, ${MDS_DATE_TIME}` : 'MDS lab result (details unavailable)';
    console.log(`  this install ${listed ? 'lists MDS labs (MDS_LABS=yes)' : 'does not list MDS labs'}; expecting "${expectedName}"`);
    await page.close();
  });

  await s.step('Print puts a page naming the MDS lab between the letter and the HL7 lab, and warns', async () => {
    const page = await openReferral('consult-labs-print');
    let reply;
    const dialogs = await h.withExpectedDialogs(page, async () => {
      // Armed before the click. The page downloads the PDF and then raises the reply's warning as an
      // alert; a missing alert is reported by the assertion below rather than as a timeout.
      const previewArrived = page.waitForResponse(response => CONSULT_POST.test(response.url())
        && response.request().method() === 'POST' && (response.headers()['content-type'] || '').includes('json'),
      { timeout: 60000 });
      const download = page.waitForEvent('download', { timeout: 60000 });
      const warned = page.waitForEvent('dialog', { predicate: dialog => dialog.type() === 'alert', timeout: 20000 })
        .then(() => true, () => false);
      await page.locator('input[name="printPreview"]').click();
      reply = await (await previewArrived).json();
      await Promise.all([download, warned]);
    });
    h.assert(reply && !reply.errorMessage, `The preview reported an error: ${reply && reply.errorMessage}`);
    h.assert(reply.consultPDF, 'The preview reply carried no PDF');
    assertPacket(pdfPages(Buffer.from(reply.consultPDF, 'base64'), 'the preview PDF'), expectedName, reason, 'The preview PDF');
    h.assert(typeof reply.warningMessage === 'string' && reply.warningMessage.includes(expectedName),
      'The preview reply does not warn about the MDS lab');
    h.assert(dialogs.some(dialog => dialog.type === 'alert' && dialog.text.includes(expectedName)),
      'The Print button did not alert the clinician that the MDS lab is not in the PDF');
    await page.close();
  });

  await s.step('the legacy printPdf2 route streams the same three parts', async () => {
    const response = await s.context.request.get(
      `${config.baseUrl}/encounter/oscarConsultationRequest/printPdf2?reqId=${owned.requestId}`, { timeout: 60000 });
    h.assert(response.status() === 200, `printPdf2 answered ${response.status()}`);
    h.assert((response.headers()['content-type'] || '').includes('application/pdf'), 'printPdf2 did not stream a PDF');
    assertPacket(pdfPages(Buffer.from(await response.body()), 'the printPdf2 PDF'), expectedName, reason, 'The printPdf2 PDF');
  });

  await s.step('Update And Print Preview names the MDS lab on the confirmation page, which stays open', async () => {
    const page = await openReferral('consult-labs-update-print');
    const download = page.waitForEvent('download', { timeout: 60000 });
    await clickAndAwaitReload(page, page.locator('input[name="updateAndPrint"]'), { label: 'Update And Print Preview' });
    const file = await (await download).path();
    assertPacket(pdfPages(fs.readFileSync(file), 'the confirmation PDF'), expectedName, reason, 'The confirmation PDF');
    const notice = page.locator('#consultLabsNotIncluded');
    await notice.waitFor({ state: 'visible' });
    h.assert((await notice.innerText()).includes(expectedName), 'The confirmation page does not name the MDS lab');
    h.assert(await page.locator('#countdown').count() === 0,
      'The confirmation page counts down to closing itself while it names a lab the PDF does not contain');
    await page.close();
  });

  await s.step('Update And Fax names the MDS lab on the cover page, apart from what the fax contains', async () => {
    const page = await openReferral('consult-labs-fax');
    const faxButton = page.locator('#fax_button');
    h.assert(await faxButton.count() === 1, 'The referral offers no fax button');
    // The page enables the button once its script has seen the specialist's fax number.
    await page.waitForFunction(() => !document.getElementById('fax_button').disabled, null, { timeout: 20000 })
      .catch(() => h.assert(false, 'The fax button stayed disabled for a specialist with a fax number'));
    await clickAndAwaitReload(page, faxButton, { label: 'Update And Fax' });
    const cover = page.locator('#coverPageForm');
    await cover.waitFor({ state: 'attached' });
    const notice = cover.locator('#consultLabsNotIncluded');
    h.assert(await notice.count() === 1, 'The fax cover page does not say which attached lab the fax cannot include');
    h.assert((await notice.innerText()).includes(expectedName), 'The fax cover page does not name the MDS lab');
    const included = (await cover.locator('ol.list-group li').allInnerTexts()).join('\n');
    h.assert(!included.includes(expectedName), 'The fax cover page lists the MDS lab among what the fax contains');
    // Cancel: nothing is rendered, stored or queued.
    await clickAndAwaitReload(page, page.locator('#btnCancel'), { label: 'Cancel' });
    h.assert(sql.value(`SELECT COUNT(*) FROM faxes WHERE demographicNo=${patient}`) === '0', 'A fax job was queued');
    await page.close();
  });
}

if (require.main === module) runWorkflow('consultation-labs-not-included', workflow, { openPatient: true, openMaster: false });
module.exports = { assertPacket, pdfPages, workflow };
