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
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for issue #3946: the OntarioMD CDS export of HL7 labs.
 *
 * An owned synthetic patient (lib/workflow-session.js) is given one Excelleris
 * (PATHL7) lab that mixes discrete results with an embedded PDF, acknowledged by
 * the test provider. The check then does what an operator does: from the master
 * record it clicks Export, ticks Laboratory Results and downloads the export. The
 * downloaded zip's patient XML must show that
 *
 *   1. the ED (embedded document) OBX is a Reports entry -- Class "Lab Report",
 *      Format Binary, the exact PDF bytes, ".pdf", the reviewer carried over --
 *      and not a LaboratoryResults value holding the base64 blob;
 *   2. a 150-character result made only of base64-alphabet characters is cut to
 *      the schema's 120 characters (the old isBase64 test let it through);
 *   3. a vertical tab in a result is removed rather than exported as '?', and the
 *      file is well-formed XML.
 *
 * It also proves the single-patient export page submits through its fetch()
 * path: its option check used to throw on the missing batch selectors, which
 * the strict page recorder reports as a page error.
 *
 * The lab rows are removed afterwards; the patient is removed by the workflow
 * session. All names and identifiers are fictitious (FAKE-/PW3946- prefixes).
 *
 * ENCRYPTION. The packaged default is demographic.export.encryptedOnly=true: the
 * export is refused unless PGP is configured (PGP_BIN/PGP_KEY/PGP_ENV), and a
 * fresh install has no PGP. Keep that default and give the check the recipient's
 * GnuPG home in CDS_EXPORT_GNUPGHOME: the .pgp download is decrypted with it
 * before the zip is read. docs/ui-tests/deb-install-validation.md ("CDS export
 * lab documents") shows a throwaway key and the PGP_BIN wrapper. An install that
 * refuses the export for want of PGP makes the check SKIP, naming that fixture,
 * rather than fail; an unencrypted zip (encryptedOnly=false) is read directly.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_* (fixture rows)
 *   CDS_EXPORT_GNUPGHOME  GnuPG home holding the export recipient's secret key
 *                         (needed when the install encrypts exports; read as root)
 * Needs python3 on the runner to prove the exported XML is well-formed, and gpg
 * when the export is encrypted.
 */

const fs = require('node:fs');
const zlib = require('node:zlib');
const { randomBytes } = require('node:crypto');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

// Same shape as src/test/java/.../lab/ca/all/parsers/PathL7EmbeddedDocumentMessage.java.
const PDF = Buffer.from('%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n', 'ascii');
const LONG_TEXT_RESULT = `ABCDEFGHIJ${'KLMNOPQRST'.repeat(14)}`;
const CONTROL_TEXT_RESULT = 'Hemolysed\u000Bsample';

function buildMessage(accession, marker) {
  return [
    'MSH|^~\\&|PATHL7|CARLOSTEST|HTTPCLIENT|carlos|20260901101500||ORU^R01|PW3946MSG|P|2.3|||ER|AL',
    `PID||9999999999|3946||${marker}^WORKFLOW||19800102|F`,
    `ORC|RE||${accession}|||||||||TESTLAB^CARLOS^TEST LAB`,
    `OBR|1||${accession}|CHEM^Chemistry|RT|20260901100000|20260901100000|||||||20260901100000||`
      + 'TESTLAB^CARLOS^TEST LAB||||||20260901100000||CHEM1|F',
    'OBX|1|NM|GLU^Glucose Random||5.2|mmol/L|3.3-7.7|N|||F|||20260901100000',
    `OBX|2|FT|NOTE^Interpretation||${LONG_TEXT_RESULT}||||||F|||20260901100000`,
    `OBX|3|ST|SPEC^Specimen Quality||${CONTROL_TEXT_RESULT}||||||F|||20260901100000`,
    `OBR|2||${accession}|PDF^Pathology Report|RT|20260901100000|20260901100000|||||||20260901100000||`
      + 'TESTLAB^CARLOS^TEST LAB||||||20260901100000||PATH|F',
    `OBX|1|ED|PDF^Pathology Report||^TEXT^PDF^Base64^${PDF.toString('base64')}||||||F|||20260901100000`,
  ].join('\r') + '\r';
}

/**
 * Minimal reader for the export's zip (stored or deflated entries, no zip64).
 * Walks the central directory rather than the local headers, which is where a
 * writer that streams sizes (data descriptors) records them.
 */
function readZip(buffer) {
  const eocd = buffer.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  h.assert(eocd >= 0, 'the export download is not a zip file');
  const count = buffer.readUInt16LE(eocd + 10);
  let offset = buffer.readUInt32LE(eocd + 16);
  const entries = new Map();
  for (let i = 0; i < count; i++) {
    h.assert(buffer.readUInt32LE(offset) === 0x02014b50, 'the export zip has a corrupt central directory');
    const method = buffer.readUInt16LE(offset + 10);
    const compressedSize = buffer.readUInt32LE(offset + 20);
    const nameLength = buffer.readUInt16LE(offset + 28);
    const extraLength = buffer.readUInt16LE(offset + 30);
    const commentLength = buffer.readUInt16LE(offset + 32);
    const localOffset = buffer.readUInt32LE(offset + 42);
    const name = buffer.toString('utf8', offset + 46, offset + 46 + nameLength);
    const dataStart = localOffset + 30 + buffer.readUInt16LE(localOffset + 26) + buffer.readUInt16LE(localOffset + 28);
    const data = buffer.subarray(dataStart, dataStart + compressedSize);
    h.assert(method === 0 || method === 8, `export zip entry uses unsupported compression method ${method}`);
    entries.set(name, method === 8 ? zlib.inflateRawSync(data) : Buffer.from(data));
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

/** Every element named `name` (any namespace prefix), as its inner text. */
function elements(xml, name) {
  const pattern = new RegExp(`<(?:[\\w.-]+:)?${name}(?:\\s[^>]*)?>([\\s\\S]*?)</(?:[\\w.-]+:)?${name}>`, 'g');
  return [...xml.matchAll(pattern)].map(match => match[1]);
}

function firstText(xml, name) {
  const [value] = elements(xml, name);
  return value === undefined ? undefined : value.trim();
}

/** XML 1.0 Char production; anything else makes the file unparseable. */
function invalidXmlCharacters(text) {
  return [...text].filter((ch) => {
    const cp = ch.codePointAt(0);
    return !(cp === 0x9 || cp === 0xA || cp === 0xD || (cp >= 0x20 && cp <= 0xD7FF)
      || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF));
  });
}

/**
 * The #3946 contract for one exported patient file. Pure, so the node unit test
 * can pin it against hand-built XML without a deployment.
 */
function assertLabExport(xml, { accession, pdf = PDF }) {
  h.assert(invalidXmlCharacters(xml).length === 0, 'the exported XML contains characters XML 1.0 forbids');

  const labs = elements(xml, 'LaboratoryResults').filter(block => block.includes(accession));
  h.assert(labs.length === 3, `expected the lab's 3 discrete results as LaboratoryResults, found ${labs.length}`);
  const pdfBase64 = pdf.toString('base64');
  h.assert(labs.every(block => !block.includes(pdfBase64)), 'the embedded PDF was exported as a lab result value');
  const valueOf = (code) => {
    const block = labs.find(candidate => firstText(candidate, 'LabTestCode') === code);
    h.assert(block, `lab result ${code} is missing from the export`);
    return firstText(block, 'Value');
  };
  const note = valueOf('NOTE');
  h.assert(note === LONG_TEXT_RESULT.slice(0, 120),
    `a long base64-alphabet result was exported with ${note ? note.length : 0} characters, expected 120`);
  const specimen = valueOf('SPEC');
  h.assert(specimen === 'Hemolysedsample',
    'a result containing a vertical tab was not exported with the character removed');

  const reports = elements(xml, 'Reports').filter(block => firstText(block, 'MessageUniqueID') === accession);
  h.assert(reports.length === 1, `expected the embedded PDF as exactly one Reports entry, found ${reports.length}`);
  const [report] = reports;
  h.assert(firstText(report, 'Class') === 'Lab Report', 'the embedded document is not classed as a Lab Report');
  h.assert(firstText(report, 'Format') === 'Binary', 'the embedded document is not exported as Binary');
  h.assert(firstText(report, 'FileExtensionAndVersion') === '.pdf', 'the embedded PDF has no .pdf extension');
  const media = firstText(report, 'Media');
  h.assert(media && Buffer.from(media.replace(/\s+/g, ''), 'base64').equals(pdf),
    'the exported report does not carry the embedded PDF bytes');
  h.assert(elements(report, 'ReportReviewed').length === 1, "the lab's acknowledgement was not carried to the report");
  return { labs: labs.length, reports: reports.length };
}

function assertWellFormed(xml) {
  try {
    execFileSync('python3', ['-c', 'import sys, xml.dom.minidom; xml.dom.minidom.parseString(sys.stdin.buffer.read())'],
      { input: Buffer.from(xml, 'utf8'), stdio: ['pipe', 'pipe', 'pipe'], timeout: 30000 });
  } catch (error) {
    if (error.code === 'ENOENT') throw new h.SkipCheck('python3 is needed to prove the export is well-formed XML');
    throw new Error('the exported patient XML is not well-formed');
  }
}

/**
 * Submits the export and waits for its outcome: the download, or the page's
 * error banner. The banner is only a SKIP when the page also says PGP is not
 * available (the encrypted-only default on an install without PGP); any other
 * refusal is a failure.
 */
async function exportDownload(page) {
  const downloaded = page.waitForEvent('download', { timeout: 180000 }).then(download => ({ download }));
  const refused = page.locator('#exportErrorMessage').waitFor({ state: 'visible', timeout: 180000 })
    .then(() => ({ refused: true }));
  await page.locator('#DemographicExportForm input[type="submit"]').click();
  const outcome = await Promise.race([downloaded, refused]);
  downloaded.catch(() => {});
  refused.catch(() => {});
  if (outcome.download) return outcome.download;
  const pgpMissing = await page.locator('#pgpReady').inputValue() !== 'Yes';
  if (pgpMissing) {
    throw new h.SkipCheck('the export was refused: this install exports encrypted files only and has no PGP '
      + 'configured (configure PGP_BIN/PGP_KEY/PGP_ENV and set CDS_EXPORT_GNUPGHOME)');
  }
  throw new Error('the export page reported that the export failed');
}

function decryptExport(encrypted) {
  const home = process.env.CDS_EXPORT_GNUPGHOME;
  if (!home) throw new h.SkipCheck('the export is PGP-encrypted; set CDS_EXPORT_GNUPGHOME to decrypt it');
  try {
    return execFileSync('gpg', ['--homedir', home, '--batch', '--quiet', '--decrypt'],
      { input: encrypted, stdio: ['pipe', 'pipe', 'pipe'], timeout: 60000, maxBuffer: 256 * 1024 * 1024 });
  } catch (error) {
    if (error.code === 'ENOENT') throw new h.SkipCheck('gpg is needed to decrypt the PGP-encrypted export');
    throw new Error('the PGP-encrypted export could not be decrypted with CDS_EXPORT_GNUPGHOME');
  }
}

function seedLab(s) {
  const accession = `PW3946-${randomBytes(6).toString('hex')}`;
  const message = Buffer.from(buildMessage(accession, s.marker), 'utf8').toString('base64');
  const labNo = s.sql.value(`INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)
    VALUES (0, ${h.sqlString(message)}, 'PATHL7', 'PLAYWRIGHT-3946', NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(labNo), 'the synthetic HL7 lab was not created');
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=${labNo} AND lab_type='HL7';
      DELETE FROM patientLabRouting WHERE lab_no=${labNo} AND lab_type='HL7';
      DELETE FROM hl7TextInfo WHERE lab_no=${labNo};
      DELETE FROM hl7TextMessage WHERE lab_id=${labNo} AND serviceName='PLAYWRIGHT-3946'`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${labNo})
      + (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${labNo})
      + (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${labNo} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${labNo} AND lab_type='HL7')`) === '0',
    'the synthetic lab rows were not removed');
  });
  s.sql.execute(`INSERT INTO hl7TextInfo (lab_no, sex, result_status, final_result_count, obr_date, priority,
      discipline, last_name, first_name, report_status, accessionNum)
    VALUES (${labNo}, 'F', '', 3, '2026-09-01 10:00:00', 'R', 'CHEM/PATH', ${h.sqlString(s.marker)}, 'Workflow', 'F',
      ${h.sqlString(accession)});
    INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created)
    VALUES (${s.patient}, ${labNo}, 'HL7', NOW());
    INSERT INTO providerLabRouting (provider_no, lab_no, status, comment, timestamp, lab_type)
    VALUES (${h.sqlString(s.provider)}, ${labNo}, 'A', '', NOW(), 'HL7')`);
  return { accession, labNo };
}

async function workflow(s) {
  const { accession } = seedLab(s);
  let exportPage;
  await s.step('the master record opens the export page for this patient', async () => {
    exportPage = await s.popup(s.master,
      s.master.locator('input[type="button"][onclick*="/demographic/DemographicExport?demographicNo="]').first(),
      'cds-export');
    await exportPage.locator('#DemographicExportForm').waitFor();
    h.assert(await exportPage.locator('#demographicNo').inputValue() === s.patient,
      'the export page is not scoped to the owned patient');
  });

  let xml;
  await s.step('exporting Laboratory Results downloads a zip holding the patient file', async () => {
    await exportPage.locator('input[name="exLaboratoryResults"]').check();
    const download = await exportDownload(exportPage);
    let zip = fs.readFileSync(await download.path());
    if (download.suggestedFilename().toLowerCase().endsWith('.pgp')) zip = decryptExport(zip);
    const entries = readZip(zip);
    const xmlNames = [...entries.keys()].filter(name => name.toLowerCase().endsWith('.xml'));
    h.assert(xmlNames.length === 1, `expected one patient XML in the export zip, found ${xmlNames.length}`);
    xml = entries.get(xmlNames[0]).toString('utf8');
    // The fetch() path shows this banner; a plain form POST (the old script error) never does.
    await exportPage.locator('#exportSuccessMessage').waitFor({ state: 'visible', timeout: 30000 });
  });

  await s.step('the embedded PDF is a Lab Report and the results are clean, well-formed XML', async () => {
    assertWellFormed(xml);
    assertLabExport(xml, { accession });
  });
  await exportPage.close();
}

if (require.main === module) runWorkflow('cds-export-lab-documents', workflow);
module.exports = {
  workflow, buildMessage, readZip, elements, assertLabExport, invalidXmlCharacters, PDF, LONG_TEXT_RESULT,
};
