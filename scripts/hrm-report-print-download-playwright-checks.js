#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * HRM report list, print and attachment download, driven from the chart.
 * User path: Schedule > Search > Master Record > E-Chart > HRM Documents heading
 *   (ViewDocList popup) > report > Print (PrintHRMReport); binary report > attachment link
 *   (HRMDownloadFile).
 * Asserts: the list shows exactly the patient's owned reports; opening a report marks this
 * provider's HRMDocumentToProvider link viewed; Print downloads a %PDF whose text carries the
 * report body; the attachment link downloads the embedded PDF byte-for-byte; and the list,
 * print and download routes refuse a report filed to a patient this provider is locked out of
 * (a provider-specific _demographic$N "o" privilege, proven by the eDoc report refusing it).
 * Fixtures: two owned patients (the second locked), three owned schema-valid HRM XML files in
 * DOCUMENT_DIR (one text, two binary PDF) with their HRMDocument, HRMDocumentToDemographic
 * and HRMDocumentToProvider rows; all removed and verified gone. The demo report is not used,
 * because opening a report writes its provider link.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md HRM section
 * (hrm-report-print-download). Env: DOCUMENT_DIR (or RX_FAX_DOCUMENT_DIR) plus the harness
 * contract; needs pdftotext. Ontario only (HRM lists nothing outside Ontario billing).
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createHash } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { requirePoppler, pdfText } = require('./lib/stored-pdf-documents');

/** One page of PDF text, so the downloaded attachment can be told apart from any other. */
function onePagePdf(text) {
  const content = `BT /F1 12 Tf 40 700 Td (${text}) Tj ET\n`;
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [4 0 R] /Count 1 >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents 5 0 R >>',
    `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}endstream`];
  let pdf = '%PDF-1.4\n';
  const offsets = [];
  objects.forEach((object, index) => { offsets.push(Buffer.byteLength(pdf)); pdf += `${index + 1} 0 obj\n${object}\nendobj\n`; });
  const xref = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.map(o => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}`;
  return Buffer.from(`${pdf}trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
}

/** A schema-valid (ontariomd_hrm 1.1.2) report, modelled on the shipped demo fixture. */
function hrmXml({ lastName, reportNo, text, pdf }) {
  const body = pdf ? `<Format>Binary</Format>
      <FileExtensionAndVersion>.pdf</FileExtensionAndVersion>
      <Content><cdsd:Media>${pdf.toString('base64')}</cdsd:Media></Content>`
    : `<Format>Text</Format>
      <FileExtensionAndVersion>.txt</FileExtensionAndVersion>
      <Content><cdsd:TextContent>${text}</cdsd:TextContent></Content>`;
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
      ${body}
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

async function download(page, locator, label, s, file) {
  const outcome = await ui.clickDownloadsOrOpens(page, locator, { context: s.context, recorder: s.recorder, label });
  h.assert(outcome.kind === 'download', `${label} opened a page instead of downloading`);
  h.assert(!(await outcome.download.failure()), `${label} download failed`);
  await outcome.download.saveAs(file);
  return fs.readFileSync(file);
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const configured = process.env.DOCUMENT_DIR || process.env.RX_FAX_DOCUMENT_DIR;
  if (!configured) throw new h.SkipCheck('Set DOCUMENT_DIR to the installed document store');
  const store = fs.realpathSync(configured);
  const owner = fs.statSync(store);
  const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-hrm-print-'));
  const lockedName = `${marker}-LOCKED`;
  const reports = [
    { key: 'text', text: `${marker} HRM text Nguyễn Łukasz İstanbul ≥ 5 ≤ 9` },
    { key: 'pdf', pdf: onePagePdf(`${marker} HRM attachment`) },
    { key: 'locked', pdf: onePagePdf(`${marker} HRM locked attachment`) },
  ];
  for (const report of reports) {
    report.file = path.join(store, `${marker}-hrm-${report.key}.xml`);
    report.hash = createHash('sha256').update(`${marker}-${report.key}`).digest('hex');
  }
  const owned = `reportFile LIKE ${h.sqlString(`${marker}-hrm-%`)}`;
  let lockedPatient;
  const lockObject = () => h.sqlString(`_demographic$${lockedPatient}`);

  s.cleanup(() => {
    const ids = sql.rows(`SELECT id FROM HRMDocument WHERE ${owned}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned HRM identity');
    if (ids.length) {
      const list = ids.join(',');
      sql.execute(`DELETE FROM HRMDocumentToProvider WHERE hrmDocumentId IN (${list});
        DELETE FROM HRMDocumentToDemographic WHERE hrmDocumentId IN (${list});
        DELETE FROM HRMDocument WHERE id IN (${list}) AND ${owned}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM HRMDocument WHERE ${owned})
        + (SELECT COUNT(*) FROM HRMDocumentToDemographic WHERE hrmDocumentId IN (${list}))
        + (SELECT COUNT(*) FROM HRMDocumentToProvider WHERE hrmDocumentId IN (${list}))`) === '0', 'Owned HRM rows were not removed');
    }
    for (const report of reports) if (fs.existsSync(report.file)) fs.unlinkSync(report.file);
    h.assert(!reports.some(report => fs.existsSync(report.file)), 'Owned HRM files were not removed');
    fs.rmSync(scratch, { recursive: true, force: true });
    if (lockedPatient) {
      sql.execute(`DELETE FROM secObjPrivilege WHERE objectName=${lockObject()} AND roleUserGroup=${h.sqlString(provider)};
        DELETE FROM demographic WHERE demographic_no=${lockedPatient} AND last_name=${h.sqlString(lockedName)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secObjPrivilege WHERE objectName=${lockObject()})
        + (SELECT COUNT(*) FROM demographic WHERE demographic_no=${lockedPatient})`) === '0', 'The locked patient fixture was not removed');
    }
  });

  lockedPatient = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
      patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(lockedName)},'Locked','1980','01','02','F','AC',${h.sqlString(provider)},'ON','ON','NR',NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(lockedPatient), 'The locked patient fixture was not created');
  for (const report of reports) {
    const lastName = report.key === 'locked' ? lockedName : marker;
    fs.writeFileSync(report.file, hrmXml({ lastName, reportNo: `${marker}-${report.key}`, text: report.text, pdf: report.pdf }),
      { flag: 'wx', mode: 0o640 });
    fs.chownSync(report.file, owner.uid, owner.gid);
    report.id = sql.value(`INSERT INTO HRMDocument (timeReceived,reportType,reportHash,reportStatus,reportFile,numDuplicatesReceived,
        reportDate,sourceFacility,description,className,sourceFacilityReportNo)
      VALUES (NOW(),'Diagnostic Imaging Report',${h.sqlString(report.hash)},'S',${h.sqlString(path.basename(report.file))},1,
        '2026-01-18 10:15:00','DEMO',${h.sqlString(`${marker} ${report.key}`)},'Diagnostic Imaging Report',${h.sqlString(`${marker}-${report.key}`)});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(report.id), 'The HRM report fixture was not created');
    sql.execute(`INSERT INTO HRMDocumentToDemographic (demographicNo,hrmDocumentId,timeAssigned)
        VALUES (${report.key === 'locked' ? lockedPatient : patient},${report.id},NOW());
      INSERT INTO HRMDocumentToProvider (providerNo,hrmDocumentId,signedOff,viewed) VALUES (${h.sqlString(provider)},${report.id},0,0)`);
  }
  sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
    VALUES (${h.sqlString(provider)},${lockObject()},'|o|',0,${h.sqlString(provider)})`);
  const [textReport, pdfReport, lockedReport] = reports;

  let list;
  await s.step('E-Chart HRM Documents opens the list (ViewDocList) with exactly the owned reports', async () => {
    const chart = await s.chart();
    list = await s.popup(chart, chart.locator('#leftNavBar a[onclick*="/hospitalReportManager/ViewDocList"]').first(), 'hrm-list');
    const ids = await list.locator('#tblHRM a[onclick*="/hospitalReportManager/Display?id="]').evaluateAll(links =>
      links.map(link => new URL(link.getAttribute('onclick').match(/'([^']*Display\?id=[^']*)'/)[1], location.href).searchParams.get('id')));
    h.assert(JSON.stringify(ids.sort()) === JSON.stringify([textReport.id, pdfReport.id].sort()),
      'The HRM list does not show exactly the patient\'s owned reports');
  });

  const openReport = async (report, label) => {
    const viewer = await s.popup(list, list.locator(`#tblHRM a[onclick*="Display?id=${report.id}'"]`), label);
    await viewer.locator(`#hrmdoc_${report.id}`).waitFor({ state: 'attached' });
    await expectValue(sql, `SELECT viewed FROM HRMDocumentToProvider WHERE hrmDocumentId=${report.id} AND providerNo=${h.sqlString(provider)}`,
      '1', 'Opening the report did not mark this provider\'s link viewed');
    return viewer;
  };

  await s.step('Print (PrintHRMReport) downloads a PDF carrying the text report body', async () => {
    const viewer = await openReport(textReport, 'hrm-text-report');
    h.assert((await viewer.locator('#hrmReportContent').innerText()).includes(textReport.text), 'The report body is not displayed');
    const pdf = await download(viewer, viewer.locator('form[action$="/hospitalReportManager/PrintHRMReport"] input[type="submit"]'),
      'hrm-print', s, path.join(scratch, 'print.pdf'));
    h.assert(pdf.subarray(0, 5).toString('latin1') === '%PDF-', 'Print did not answer a PDF');
    const text = pdfText(path.join(scratch, 'print.pdf'));
    h.assert(text.replace(/\s+/g, ' ').includes(textReport.text), 'The printed PDF does not carry the report body');
    await viewer.close();
  });

  await s.step('The attachment link (HRMDownloadFile) downloads the embedded PDF byte-for-byte', async () => {
    const viewer = await openReport(pdfReport, 'hrm-pdf-report');
    const link = viewer.locator(`a[href$="/hospitalReportManager/HRMDownloadFile?hash=${pdfReport.hash}"]`);
    const bytes = await download(viewer, link, 'hrm-download', s, path.join(scratch, 'attachment.pdf'));
    h.assert(bytes.equals(pdfReport.pdf), 'The downloaded attachment differs from the embedded report PDF');
    await viewer.close();
  });

  await s.step('List, print and download refuse a report filed to a patient this provider is locked out of', async () => {
    const base = s.config.baseUrl;
    const get = url => s.context.request.get(url, { maxRedirects: 0, failOnStatusCode: false });
    // Fixture proof: a route that enforces patient access refuses the locked patient.
    const gate = await get(`${base}/documentManager/ViewDocumentReport?function=demographic&functionid=${lockedPatient}`);
    h.assert(gate.status() >= 300, 'The patient lock fixture is not enforced by the eDoc report');
    const leaks = [];
    const listed = await get(`${base}/hospitalReportManager/ViewDocList?demographic_no=${lockedPatient}`);
    if (listed.status() < 300 && (await listed.text()).includes(`Display?id=${lockedReport.id}`)) leaks.push('ViewDocList');
    const printed = await get(`${base}/hospitalReportManager/PrintHRMReport?hrmReportId=${lockedReport.id}`);
    if (printed.status() < 300 && (await printed.body()).subarray(0, 5).toString('latin1') === '%PDF-') leaks.push('PrintHRMReport');
    const fetched = await get(`${base}/hospitalReportManager/HRMDownloadFile?hash=${lockedReport.hash}`);
    if (fetched.status() < 300 && (await fetched.body()).equals(lockedReport.pdf)) leaks.push('HRMDownloadFile');
    h.assert(!leaks.length, `HRM served a locked patient's report through: ${leaks.join(', ')}`);
  });
}

if (require.main === module) runWorkflow('hrm-report-print-download', workflow, {
  openPatient: true, preflight: () => requirePoppler('pdftotext'),
});
module.exports = { workflow };
