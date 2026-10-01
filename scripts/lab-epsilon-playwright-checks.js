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
 * Browser check for issue #4124: Epsilon (EPSILON) HL7 labs upload, and render
 * their results.
 *
 * EpsilonHandler never built the OBR/OBX groups and terser its superclass reads,
 * so getFirstName() threw (every Epsilon upload failed), getOBRCount() was 0 and
 * both lab views rendered no result rows.
 *
 * A SYNTHETIC Epsilon ORU^R01 v2.3 file is built here with two patients (the
 * uploader stores one message per PID): the run's owned FAKE- patient, and a
 * second fictitious patient. The owned patient's lab carries a discrete result, a
 * free-text (FT) line, an embedded PDF and an ED payload that is not a PDF.
 *
 *   1. The HL7 Lab Upload popup, with the type EPSILON, reports "Uploaded
 *      successfully"; both messages reach hl7TextInfo under the run's accession
 *      prefix with a parsed OBR date, and the owned patient's lab is routed to
 *      that patient by name, DOB and sex.
 *   2. The lab display (labDisplay.jsp) shows the discrete result, the FT line,
 *      a Download PDF link with one inline PDF frame, and the not-a-PDF note
 *      for the other ED payload, never its encoded bytes.
 *   3. The AJAX lab view (labDisplayAjax.jsp) renders the same rows.
 *   4. The PDF row's Download and inline routes answer with the PDF bytes.
 *
 * Every hl7TextMessage, hl7TextInfo, patientLabRouting, providerLabRouting,
 * measurement and fileUploadCheck row the upload creates is removed in cleanup
 * by the run's accession and stamped file name, pass or fail; the uploader's
 * archived copy is handled as in lab-upload-playwright-checks.js
 * (LAB_UPLOAD_DOCUMENT_STORE). All names and identifiers are fictitious
 * (FAKE-/PW4124 prefixes). Use a disposable database.
 *
 * Environment: the common contract in lib/playwright-harness.js (BASE_URL,
 * CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*), plus
 * LAB_UPLOAD_DOCUMENT_STORE as described above.
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 60000;

/** A small but complete one-page PDF. */
const PDF = Buffer.from([
  '%PDF-1.4',
  '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj',
  '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj',
  '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 300 200]/Contents 4 0 R'
    + '/Resources<</Font<</F1 5 0 R>>>>>>endobj',
  '4 0 obj<</Length 48>>stream',
  'BT /F1 18 Tf 30 100 Td (PW4124 lab report) Tj ET',
  'endstream endobj',
  '5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj',
  'trailer<</Root 1 0 R>>',
  '%%EOF',
  '',
].join('\n'), 'ascii');

/** An ED payload that is not a PDF: it must never be shown or served as one. */
const HTML_PAYLOAD = Buffer.from('<html><body><script>alert(document.cookie)</script></body></html>', 'ascii');

/**
 * The header both patients' rows are grouped under: Epsilon groups rows by OBX-3.1 and names
 * them by OBX-3.2 (an FT row's name is blank, so it renders as a free-text line).
 */
const HEADER = 'GENERAL CHEMISTRY';
const FT_LINE = 'PW4124 synthetic interpretive comment';
/** The owned patient's rows, in OBX order: (segment 0, group k). */
const PDF_ROW = { segment: 0, group: 2 };
const HTML_ROW = { segment: 0, group: 3 };

/**
 * A synthetic Epsilon feed: one MSH, two PIDs. Segments are joined with CR LF, as the
 * Epsilon sample files are, so the uploader's line reader splits it per PID.
 */
function buildFeed(accession, marker) {
  const obr = (acc) => `OBR|1||${acc}|||20260930100000||||||||20260930100000||99999^FAKE-ORDERING^DR. PW4124||||||||F`;
  return [
    'MSH|^~\\&|Epsilon-System||||20261001101500||ORU^R01|PW4124MSG|P|2.3|||||||',
    `PID|1|||^^ON|${marker}^Workflow||19800102|F`,
    obr(`${accession}A`),
    `OBX|1|NM|${HEADER}^Glucose Random^GLU|1|5.2|mmol/L|3.3-7.7||||F`,
    `OBX|2|FT|${HEADER}^Glucose Random^GLU|2|${FT_LINE}||||||F`,
    `OBX|3|ED|${HEADER}^Epsilon Report^RPT|3|^TEXT^PDF^Base64^${PDF.toString('base64')}||||||F`,
    `OBX|4|ED|${HEADER}^Epsilon Attachment^ATT|4|^TEXT^HTML^Base64^${HTML_PAYLOAD.toString('base64')}||||||F`,
    'PID|2|||^^ON|FAKE-PW4124^Second||19700305|M',
    obr(`${accession}B`),
    `OBX|1|NM|${HEADER}^Hemoglobin^HGB|1|140|g/L|120-160||||F`,
    '',
  ].join('\r\n');
}

function documentQuery(labNo, { segment, group }) {
  return `labNo=${labNo}&segment=${segment}&group=${group}`;
}

/** Open the HL7 Lab Upload popup from the Inbox hub and upload one file as an Epsilon lab. */
async function uploadAsEpsilon(s, filePath, fileName) {
  const inbox = await s.context.newPage();
  await h.gotoApp(inbox, s.config.baseUrl, '/web/inboxhub/Inboxhub');
  await inbox.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  const link = inbox.locator('a[href*="ViewInsideLabUpload"]').first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  const popup = await s.popup(inbox, link, 'epsilon-lab-upload');
  try {
    await popup.locator('#uploadForm').waitFor({ state: 'visible', timeout: 20000 });
    await popup.locator('#importFiles').setInputFiles(filePath);
    await popup.locator('#type').selectOption('EPSILON');
    const [response] = await Promise.all([
      popup.waitForResponse((r) => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/lab/CA/ALL/insideLabUpload'), { timeout: TIMEOUT }),
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

/** Deletes this run's archived uploads when LAB_UPLOAD_DOCUMENT_STORE names the store. */
function removeArchivedUploads(stamp) {
  const store = process.env.LAB_UPLOAD_DOCUMENT_STORE;
  if (!store) {
    console.warn(`    archived uploads retained: set LAB_UPLOAD_DOCUMENT_STORE to remove LabUpload.lab-epsilon-probe-${stamp}.hl7.*`);
    return;
  }
  const root = fs.realpathSync(store);
  const prefix = `LabUpload.lab-epsilon-probe-${stamp}.hl7.`;
  const ownFiles = () => fs.readdirSync(root)
    .filter((name) => /^LabUpload\.lab-epsilon-probe-[0-9A-F]{8}\.hl7\.\d+$/.test(name) && name.startsWith(prefix));
  // name matched the fixed pattern above and is joined to the resolved store root.
  for (const name of ownFiles()) fs.unlinkSync(path.join(root, name)); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
  h.assert(ownFiles().length === 0, 'The archived synthetic Epsilon uploads were not all removed');
}

async function fetchRoute(s, routePath) {
  const response = await s.context.request.fetch(h.appUrl(s.config.baseUrl, routePath), {
    method: 'GET', maxRedirects: 0, failOnStatusCode: false,
  });
  return { status: response.status(), headers: response.headers(), body: await response.body() };
}

/** The row assertions shared by both lab views, made against the visible text and the markup. */
function assertRenderedRows(text, html, labNo, label) {
  h.assert(text.includes('Glucose Random'), `${label}: the named NM row was not rendered`);
  h.assert(/\b5\.2\b/.test(text), `${label}: the NM result value was not rendered`);
  h.assert(text.includes(FT_LINE), `${label}: the FT line was not rendered`);
  h.assert((html.match(/class="lab-embedded-pdf-download"/g) || []).length === 1,
    `${label}: expected one Download PDF link`);
  h.assert(html.includes(`/lab/DownloadEmbeddedDocumentFromLab?${documentQuery(labNo, PDF_ROW).replace(/&/g, '&amp;')}`),
    `${label}: the Download PDF link does not address the PDF row`);
  h.assert((html.match(/class="lab-embedded-pdf-frame"/g) || []).length === 1, `${label}: expected one inline PDF frame`);
  h.assert((html.match(/class="lab-embedded-document-unsupported"/g) || []).length === 1,
    `${label}: the non-PDF ED payload does not show the not-a-PDF note`);
  h.assert(!text.includes(HTML_PAYLOAD.toString('base64')) && !text.includes(PDF.toString('base64')),
    `${label}: an ED payload was printed as encoded bytes`);
}

async function workflow(s) {
  const { sql, patient, marker, cleanup } = s;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const accession = `PW4124${stamp}`;
  const fileName = `lab-epsilon-probe-${stamp}.hl7`;
  const ownUpload = `filename LIKE ${h.sqlString(`%${fileName}%`)}`;
  const ownLabs = `accessionNum LIKE ${h.sqlString(`%${accession}%`)}`;
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-epsilon-'));
  // fileName is built from a fixed prefix and random hex, never from input.
  const filePath = path.join(workDir, fileName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
  fs.writeFileSync(filePath, buildFeed(accession, marker), 'latin1');

  cleanup(async () => {
    fs.rmSync(workDir, { recursive: true, force: true });
    const labs = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE ${ownLabs}
      UNION SELECT lab_id FROM hl7TextMessage WHERE FROM_BASE64(message) LIKE ${h.sqlString(`%${accession}%`)}`)
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
        (SELECT COUNT(*) FROM hl7TextInfo WHERE ${ownLabs})
      + (SELECT COUNT(*) FROM fileUploadCheck WHERE ${ownUpload})
      + (SELECT COUNT(*) FROM hl7TextMessage WHERE FROM_BASE64(message) LIKE ${h.sqlString(`%${accession}%`)})`) === '0',
    'The synthetic Epsilon lab rows were not all removed');
  });

  let labNo;
  await s.step('the Epsilon file uploads, one lab per patient, routed to the owned patient', async () => {
    const status = await uploadAsEpsilon(s, filePath, fileName);
    h.assert(status === 'Uploaded successfully', `The Epsilon upload reported "${status}"`);
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE ${ownLabs}`, '2',
      'The two patients\' Epsilon messages did not both reach hl7TextInfo');
    labNo = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum LIKE ${h.sqlString(`%${accession}A%`)}`);
    h.assert(/^[1-9]\d*$/.test(labNo), 'The owned patient\'s Epsilon lab was not stored');
    h.assert(sql.value(`SELECT DATE_FORMAT(obr_date, '%Y-%m-%d %H:%i') FROM hl7TextInfo WHERE lab_no=${labNo}`)
      === '2026-09-30 10:00', 'The Epsilon OBR date was not parsed into hl7TextInfo.obr_date');
    h.assert(sql.value(`SELECT type FROM hl7TextMessage WHERE lab_id=${labNo}`) === 'EPSILON',
      'The stored lab is not typed EPSILON');
    h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting
      WHERE lab_type='HL7' AND lab_no=${labNo} AND demographic_no=${patient}`) === '1',
    'The Epsilon lab was not routed to the owned patient by name, DOB and sex');
  });

  await s.step('the lab display renders the result rows, the PDF link and frame, and the not-a-PDF note', async () => {
    const report = await s.context.newPage();
    await h.gotoApp(report, s.config.baseUrl,
      `/lab/CA/ALL/ViewLabDisplay?segmentID=${labNo}&providerNo=${encodeURIComponent(s.provider)}`);
    await report.getByText('Glucose Random').first().waitFor({ timeout: TIMEOUT });
    // Visible text only: the page also keeps the raw HL7 in a hidden <pre id="rawhl7...">.
    assertRenderedRows(await report.locator('body').innerText(), await report.content(), labNo, 'lab display');
    h.assert(await report.locator('details.lab-embedded-pdf[open]').count() === 1, 'the PDF preview is not expanded');
    await report.close();
  });

  await s.step('the AJAX lab view renders the same rows', async () => {
    const ajax = await fetchRoute(s, `/lab/CA/ALL/ViewLabDisplayAjax?segmentID=${labNo}`
      + `&providerNo=${encodeURIComponent(s.provider)}&searchProviderNo=${encodeURIComponent(s.provider)}&status=N`);
    h.assert(ajax.status === 200, `the AJAX lab view answered ${ajax.status}`);
    const html = ajax.body.toString('utf8');
    // Strip the hidden raw HL7 and tags for the text assertions; the markup assertions use the HTML.
    const text = html.replace(/<pre[^>]*id="rawhl7[\s\S]*?<\/pre>/g, '').replace(/<[^>]+>/g, ' ');
    assertRenderedRows(text, html, labNo, 'AJAX lab view');
  });

  await s.step('the PDF row is served inline and as a download', async () => {
    const inline = await fetchRoute(s, `/lab/ViewEmbeddedDocumentFromLab?${documentQuery(labNo, PDF_ROW)}`);
    h.assert(inline.status === 200 && (inline.headers['content-type'] || '').startsWith('application/pdf'),
      `the inline PDF route answered ${inline.status} ${inline.headers['content-type']}`);
    h.assert(inline.body.equals(PDF), 'the inline route did not serve the embedded PDF bytes');
    const download = await fetchRoute(s, `/lab/DownloadEmbeddedDocumentFromLab?${documentQuery(labNo, PDF_ROW)}`);
    h.assert(download.status === 200 && download.body.equals(PDF), 'the download did not carry the embedded PDF bytes');
    const html = await fetchRoute(s, `/lab/ViewEmbeddedDocumentFromLab?${documentQuery(labNo, HTML_ROW)}`);
    h.assert(html.status === 415, `the non-PDF ED payload answered ${html.status}, expected 415`);
  });
}

if (require.main === module) runWorkflow('lab-epsilon', workflow, { openMaster: false });
module.exports = { workflow, buildFeed, PDF, HTML_PAYLOAD, PDF_ROW, HTML_ROW, HEADER, FT_LINE };
