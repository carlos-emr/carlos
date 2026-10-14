#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * HRM report deletes (detached-delete risk sweep, issue #4129): the two delete controls on the
 * HRM report viewer that no other check drives.
 * User path: Schedule > Search > Master Record > E-Chart > HRM Documents heading (ViewDocList
 *   popup) > report (Display) > Add Comment, then "(Delete this comment)"; Assigned Providers >
 *   "(remove)" beside the second provider (hospitalReportManager/Modify method=deleteComment /
 *   removeProvider, both POST JSON).
 * Asserts: Add Comment stores exactly one HRMDocumentComment row and the reopened report lists
 * it; Delete answers "Success", soft-deletes exactly that row (deleted=1, row kept) and the
 * reopened report no longer lists it; (remove) answers "Success", deletes exactly the second
 * provider's HRMDocumentToProvider row (the viewer's own row stays) and the reopened report no
 * longer lists that provider. Both handlers find then remove/merge by id inside the action's own
 * transaction, the safe shape of the detached-delete pattern; a regression to a cross-call
 * find/remove pair would fail the matching step.
 * Fixtures: the owned FAKE- patient (runWorkflow), one owned schema-valid text HRM XML file in
 * DOCUMENT_DIR and its HRMDocument / HRMDocumentToDemographic / HRMDocumentToProvider rows (the
 * test provider and one other active provider); the comment is created through the UI. Cleanup
 * deletes the owned document's rows and file by id and asserts they are gone.
 * Ontario only (HRM). Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR) plus the harness contract.
 */
const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

/** A schema-valid (ontariomd_hrm 1.1.2) text report, modelled on the shipped demo fixture. */
function hrmXml(lastName, reportNo, text) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<!-- Fictitious HRM report owned by a CARLOS browser check. No real clinical data. -->
<OmdCds xmlns="cds" xmlns:cdsd="cds_dt">
  <PatientRecord>
    <Demographics>
      <Names>
        <cdsd:LegalName namePurpose="L">
          <cdsd:FirstName><cdsd:Part>FAKE-Workflow</cdsd:Part><cdsd:PartType>GIV</cdsd:PartType></cdsd:FirstName>
          <cdsd:LastName><cdsd:Part>${lastName}</cdsd:Part><cdsd:PartType>FAMC</cdsd:PartType></cdsd:LastName>
        </cdsd:LegalName>
      </Names>
      <DateOfBirth><cdsd:FullDate>1980-01-02</cdsd:FullDate></DateOfBirth>
      <HealthCard><cdsd:Number>0000000000</cdsd:Number><cdsd:Version>XX</cdsd:Version><cdsd:ProvinceCode>CA-ON</cdsd:ProvinceCode></HealthCard>
      <Gender>F</Gender>
      <UniqueVendorIdSequence>1</UniqueVendorIdSequence>
      <PersonStatusCode>A</PersonStatusCode>
    </Demographics>
    <ReportsReceived>
      <Media>Download</Media>
      <Format>Text</Format>
      <FileExtensionAndVersion>.txt</FileExtensionAndVersion>
      <Content><cdsd:TextContent>${text}</cdsd:TextContent></Content>
      <Class>Diagnostic Imaging Report</Class>
      <SubClass>X-Ray</SubClass>
      <EventDateTime><cdsd:DateTime>2026-01-18T10:15:00</cdsd:DateTime></EventDateTime>
      <ReceivedDateTime><cdsd:DateTime>2026-01-18T12:00:00</cdsd:DateTime></ReceivedDateTime>
      <AuthorPhysician><cdsd:FirstName>Demo</cdsd:FirstName><cdsd:LastName>Radiologist</cdsd:LastName></AuthorPhysician>
      <SendingFacility>DEMO</SendingFacility>
      <SendingFacilityReportNumber>${reportNo}</SendingFacilityReportNumber>
      <OBRContent>
        <AccompanyingSubClass>X-Ray</AccompanyingSubClass>
        <AccompanyingMnemonic>CXR</AccompanyingMnemonic>
        <AccompanyingDescription>Chest X-ray, PA and lateral</AccompanyingDescription>
        <ObservationDateTime><cdsd:DateTime>2026-01-18T10:15:00</cdsd:DateTime></ObservationDateTime>
      </OBRContent>
      <ResultStatus>S</ResultStatus>
    </ReportsReceived>
    <TransactionInformation>
      <MessageUniqueID>${reportNo}</MessageUniqueID>
      <DeliverToUserID>999998</DeliverToUserID>
      <Provider><cdsd:FirstName>Demo</cdsd:FirstName><cdsd:LastName>Provider</cdsd:LastName></Provider>
    </TransactionInformation>
  </PatientRecord>
</OmdCds>
`;
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const configured = process.env.DOCUMENT_DIR || process.env.RX_FAX_DOCUMENT_DIR;
  if (!configured) throw new h.SkipCheck('Set DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(configured);
  const owner = fs.statSync(store);
  const other = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no REGEXP '^[1-9][0-9]*$'
      AND provider_no<>${h.sqlString(provider)} AND COALESCE(last_name,'')<>'' ORDER BY provider_no LIMIT 1`);
  if (!other) throw new h.SkipCheck('The database has no second active provider to route the report to');
  const file = path.join(store, `${marker}-hrm-delete.xml`);
  const reportText = `${marker} HRM report for the delete controls`;
  const commentText = `${marker} comment that Delete must hide`;
  let reportId = null;

  s.cleanup(() => {
    const ids = sql.rows(`SELECT id FROM HRMDocument WHERE reportFile=${h.sqlString(path.basename(file))}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned HRM identity');
    if (ids.length) {
      const list = ids.join(',');
      const keys = ids.map(id => h.sqlString(id)).join(',');
      sql.execute(`DELETE FROM HRMDocumentComment WHERE hrmDocumentId IN (${list});
        DELETE FROM HRMDocumentToProvider WHERE hrmDocumentId IN (${keys});
        DELETE FROM HRMDocumentToDemographic WHERE hrmDocumentId IN (${keys});
        DELETE FROM HRMDocument WHERE id IN (${list})`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM HRMDocument WHERE id IN (${list}))
        + (SELECT COUNT(*) FROM HRMDocumentComment WHERE hrmDocumentId IN (${list}))
        + (SELECT COUNT(*) FROM HRMDocumentToDemographic WHERE hrmDocumentId IN (${keys}))
        + (SELECT COUNT(*) FROM HRMDocumentToProvider WHERE hrmDocumentId IN (${keys}))`) === '0',
      'Owned HRM rows were not removed');
    }
    if (fs.existsSync(file)) fs.unlinkSync(file);
    h.assert(!fs.existsSync(file), 'The owned HRM file was not removed');
  });

  fs.writeFileSync(file, hrmXml(marker, `${marker}-del`, reportText), { flag: 'wx', mode: 0o640 });
  fs.chownSync(file, owner.uid, owner.gid);
  reportId = sql.value(`INSERT INTO HRMDocument (timeReceived,reportType,reportHash,reportStatus,reportFile,numDuplicatesReceived,
      reportDate,sourceFacility,description,className,sourceFacilityReportNo)
    VALUES (NOW(),'Diagnostic Imaging Report',${h.sqlString(createHash('sha256').update(marker).digest('hex'))},'S',
      ${h.sqlString(path.basename(file))},1,'2026-01-18 10:15:00','DEMO',${h.sqlString(`${marker} delete`)},
      'Diagnostic Imaging Report',${h.sqlString(`${marker}-del`)});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(reportId), 'The HRM report fixture was not created');
  const key = h.sqlString(reportId);
  sql.execute(`INSERT INTO HRMDocumentToDemographic (demographicNo,hrmDocumentId,timeAssigned) VALUES (${patient},${key},NOW());
    INSERT INTO HRMDocumentToProvider (providerNo,hrmDocumentId,signedOff,viewed) VALUES (${h.sqlString(provider)},${key},0,0);
    INSERT INTO HRMDocumentToProvider (providerNo,hrmDocumentId,signedOff,viewed) VALUES (${h.sqlString(other)},${key},0,0)`);
  const mapping = who => sql.value(`SELECT id FROM HRMDocumentToProvider WHERE hrmDocumentId=${key} AND providerNo=${h.sqlString(who)}`);
  const ownMapping = mapping(provider);
  const otherMapping = mapping(other);
  h.assert(/^[1-9]\d*$/.test(ownMapping) && /^[1-9]\d*$/.test(otherMapping), 'The provider routing fixture was not created');
  const comments = () => sql.rows(`SELECT id, providerNo, IFNULL(deleted,'NULL') FROM HRMDocumentComment
    WHERE hrmDocumentId=${reportId} ORDER BY id`);

  let list;
  let viewer;
  const openReport = async label => {
    if (viewer && !viewer.isClosed()) await viewer.close();
    viewer = await s.popup(list, list.locator(`#tblHRM a[onclick*="Display?id=${reportId}'"]`), label);
    await viewer.locator(`#hrmdoc_${reportId}`).waitFor({ state: 'attached' });
    return viewer;
  };
  const status = id => viewer.locator(`#${id}${reportId}`);
  const removeLink = mappingId => viewer.locator(`#assignedProviders${reportId} a[onclick*="removeProvFromHrm('${mappingId}'"]`);

  await s.step('E-Chart HRM Documents opens the owned report with both providers and no comments', async () => {
    const chart = await s.chart();
    list = await s.popup(chart, chart.locator('#leftNavBar a[onclick*="/hospitalReportManager/ViewDocList"]').first(), 'hrm-delete-list');
    await openReport('hrm-delete-report');
    h.assert((await viewer.locator('#hrmReportContent').innerText()).includes(reportText), 'The owned report body is not displayed');
    h.assert(await removeLink(ownMapping).count() === 1 && await removeLink(otherMapping).count() === 1,
      'Assigned Providers does not offer (remove) for both owned routing rows');
    h.assert(/Displaying 0 comments/.test(await viewer.locator('#commentBox').innerText()), 'The new report already lists comments');
  });

  let commentId;
  await s.step('Add Comment stores exactly one comment and the reopened report lists it', async () => {
    await viewer.locator(`[id="commentField_${reportId}_hrm"]`).fill(commentText);
    await viewer.locator('#commentBox input[type="button"][value="Add Comment"]').click();
    await status('commentstatus').filter({ hasText: /^Success$/ }).waitFor();
    await expectValue(sql, `SELECT COUNT(*) FROM HRMDocumentComment WHERE hrmDocumentId=${reportId}`, '1', 'Add Comment did not store one row');
    const rows = comments();
    h.assert(rows.length === 1 && rows[0][1] === provider && rows[0][2] === '0', 'The stored comment is not this provider\'s live comment');
    commentId = rows[0][0];
    h.assert(sql.value(`SELECT comment FROM HRMDocumentComment WHERE id=${commentId}`) === commentText, 'The stored comment text differs');
    await openReport('hrm-delete-report-commented');
    const box = viewer.locator('#commentBox');
    h.assert(/Displaying 1 comment\b/.test(await box.innerText()) && (await box.innerText()).includes(commentText),
      'The reopened report does not list the stored comment');
  });

  await s.step('(Delete this comment) says Success and soft-deletes exactly the owned comment', async () => {
    const link = viewer.locator('#commentBox .documentComment').filter({ hasText: commentText })
      .getByRole('link', { name: /Delete\s+this comment/ });
    h.assert(await link.count() === 1, 'The comment has no Delete link');
    await link.click();
    await status('commentstatus').filter({ hasText: /^Success$/ }).waitFor();
    await expectValue(sql, `SELECT IFNULL(deleted,'NULL') FROM HRMDocumentComment WHERE id=${commentId}`, '1',
      'Delete did not mark the comment deleted');
    h.assert(JSON.stringify(comments()) === JSON.stringify([[commentId, provider, '1']]),
      'Delete touched a row other than the owned comment (or removed it instead of hiding it)');
    await openReport('hrm-delete-report-after-comment-delete');
    const text = await viewer.locator('#commentBox').innerText();
    h.assert(/Displaying 0 comments/.test(text) && !text.includes(commentText), 'The reopened report still lists the deleted comment');
  });

  await s.step('Assigned Providers (remove) says Success and deletes only the second provider\'s routing row', async () => {
    h.assert(await removeLink(otherMapping).count() === 1, 'The reopened report does not offer (remove) for the second provider');
    await removeLink(otherMapping).click();
    await status('provstatus').filter({ hasText: /^Success$/ }).waitFor();
    await expectValue(sql, `SELECT COUNT(*) FROM HRMDocumentToProvider WHERE id=${otherMapping}`, '0',
      '(remove) did not delete the second provider\'s routing row');
    h.assert(sql.value(`SELECT GROUP_CONCAT(id ORDER BY id) FROM HRMDocumentToProvider WHERE hrmDocumentId=${key}`) === ownMapping,
      '(remove) changed the viewer\'s own routing row as well');
    await openReport('hrm-delete-report-after-remove');
    h.assert(await removeLink(otherMapping).count() === 0 && await removeLink(ownMapping).count() === 1,
      'The reopened report still lists the removed provider (or lost the viewer)');
    await viewer.close();
  });
}

if (require.main === module) runWorkflow('detached-delete-hrm', workflow, { openPatient: true });
module.exports = { workflow };
