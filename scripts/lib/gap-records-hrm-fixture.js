/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const h = require('./playwright-harness');

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

/**
 * One owned text HRM report: the XML file in DOCUMENT_DIR plus its HRMDocument row. Returns the ids and a
 * remove() that deletes the owned rows and the file and asserts they are gone.
 */
function createOwnedHrmReport({ sql, marker, suffix, description, reportText }) {
  const configured = process.env.DOCUMENT_DIR || process.env.RX_FAX_DOCUMENT_DIR;
  if (!configured) throw new h.SkipCheck('Set DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(configured);
  const owner = fs.statSync(store);
  const file = path.join(store, `${marker}-hrm-${suffix}.xml`);
  const remove = () => {
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
        + (SELECT COUNT(*) FROM HRMDocumentToProvider WHERE hrmDocumentId IN (${keys}))`) === '0', 'Owned HRM rows were not removed');
    }
    if (fs.existsSync(file)) fs.unlinkSync(file);
    h.assert(!fs.existsSync(file), 'The owned HRM file was not removed');
  };
  const create = () => {
    fs.writeFileSync(file, hrmXml(marker, `${marker}-${suffix}`, reportText), { flag: 'wx', mode: 0o640 });
    fs.chownSync(file, owner.uid, owner.gid);
    const id = sql.value(`INSERT INTO HRMDocument (timeReceived,reportType,reportHash,reportStatus,reportFile,numDuplicatesReceived,
        reportDate,sourceFacility,description,className,sourceFacilityReportNo)
      VALUES (NOW(),'Diagnostic Imaging Report',${h.sqlString(createHash('sha256').update(marker + suffix).digest('hex'))},'S',
        ${h.sqlString(path.basename(file))},1,'2026-01-18 10:15:00','DEMO',${h.sqlString(description)},
        'Diagnostic Imaging Report',${h.sqlString(`${marker}-${suffix}`)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'The HRM report fixture was not created');
    return id;
  };
  return { create, remove, file };
}

module.exports = { hrmXml, createOwnedHrmReport };
