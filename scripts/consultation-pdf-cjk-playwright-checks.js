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
// Issue #5046. The consultation PDF built every cell in DejaVu, which has no Chinese, Japanese or
// Korean characters, so a referral's reason, its clinical information and the patient's name printed
// with those characters silently missing: the PDF opened and printed normally, with gaps where the
// text was. This check gives the owned FAKE patient a first name in all three scripts and writes a
// referral whose reason and clinical information use them too, then reads the PDF that the edit
// screen's Print button and the legacy printPdf2 route return. Every value must be in the text, and
// Poppler's pdffonts must report every font embedded (a non-embedded font prints in whatever face the
// printer substitutes, and the host decides which characters survive).
//
// User path: Master Record > Consultations > the referral > Print, and the printPdf2 link.
// Fixtures, all owned and removed afterwards: the FAKE patient (runWorkflow, its first name set
// here); a FAKE service, specialist and referral; what Print writes (none) and nothing else.
// Needs Poppler pdftotext and pdffonts.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const { insertId } = h;

/** The action every consultation button posts to. */
const CONSULT_POST = /\/encounter\/RequestConsultation(\?|$)/;
const POSITIVE = /^[1-9]\d*$/;
// The same person's name in Chinese, Japanese and Korean, and record text in all three scripts.
const FIRST_NAME = '小明 さくら 민준';
const CLINICAL_INFO = 'FAKE 复诊 血压正常; 経過観察; 추적 관찰 필요';

/** A utf8mb4 SQL literal that does not depend on the mysql client's character set. */
function utf8Literal(value) {
  return `CONVERT(X'${Buffer.from(value, 'utf8').toString('hex')}' USING utf8mb4)`;
}

/** Text without whitespace: line breaks and the spacing between runs set in different faces vary. */
const compact = value => value.replace(/\s+/g, '');

/** The PDF's text without whitespace, after checking the bytes are a PDF. */
function pdfText(bytes, what) {
  h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', `${what} is not a PDF`);
  const run = spawnSync('pdftotext', ['-enc', 'UTF-8', '-', '-'], { input: bytes, maxBuffer: 32 * 1024 * 1024 });
  h.assert(run.status === 0, `${what} is not a readable PDF`);
  return compact(run.stdout.toString('utf8'));
}

/** Names of the fonts pdffonts lists as not embedded. pdffonts reads a file, not stdin. */
function unembeddedFonts(file) {
  const run = spawnSync('pdffonts', [file], { encoding: 'utf8' });
  h.assert(run.status === 0, `pdffonts could not read ${file}`);
  const rows = run.stdout.split('\n').slice(2).filter(line => line.trim());
  h.assert(rows.length > 0, 'pdffonts listed no fonts; the PDF has no text');
  // Columns: name, type (may contain spaces), encoding, emb, sub, uni, object number, generation.
  return rows.map(line => line.trim().split(/\s+/)).filter(cells => cells[cells.length - 5] !== 'yes')
    .map(cells => cells[0]);
}

function assertPrinted(bytes, reason, what, workDir) {
  const text = pdfText(bytes, what);
  for (const [label, value] of [['reason', reason], ['clinical information', CLINICAL_INFO], ['patient first name', FIRST_NAME]]) {
    h.assert(text.includes(compact(value)), `${what} dropped characters from the ${label}: expected "${value}"`);
  }
  const file = path.join(workDir, `${what.replace(/\W+/g, '-')}.pdf`);
  fs.writeFileSync(file, bytes);
  const missing = unembeddedFonts(file);
  h.assert(missing.length === 0, `${what} uses fonts that are not embedded: ${missing.join(', ')}`);
}

async function workflow(s) {
  const { sql, marker, patient, provider, config } = s;
  for (const tool of ['pdftotext', 'pdffonts']) {
    if (spawnSync(tool, ['-v']).error) throw new h.SkipCheck(`Install Poppler ${tool} to read the consultation PDF`);
  }
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-cjk-'));
  const serviceName = `${marker}-Svc`;
  const lastName = `${marker}-Spec`;
  const reason = `${marker} 转诊 さくら 김민준 review`;
  const owned = { requestId: null, serviceId: null };

  // Registered before the first write; the rows are found by the run's own keys, so a run that fails
  // half way still removes what it wrote.
  s.cleanup(() => {
    const requests = owned.requestId ? [owned.requestId] : sql.rows(`SELECT requestId FROM consultationRequests
      WHERE demographicNo=${patient} AND reason=${utf8Literal(reason)}`).map(([id]) => id).filter(id => POSITIVE.test(id));
    for (const id of requests) {
      h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE requestId=${id} AND demographicNo=${patient}`) === '1',
        'Referral fixture ownership changed');
      sql.execute(`DELETE FROM consultdocs WHERE requestId=${id};
        DELETE FROM consultationRequestExtArchive WHERE requestId=${id};
        DELETE FROM consultationRequestsArchive WHERE requestId=${id} AND demographicNo=${patient};
        DELETE FROM consultationRequestExt WHERE requestId=${id};
        DELETE FROM consultationRequests WHERE requestId=${id} AND demographicNo=${patient}`);
    }
    if (owned.serviceId) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${owned.serviceId}`);
    sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)};
      DELETE FROM professionalSpecialists WHERE lName=${h.sqlString(lastName)}`);
    fs.rmSync(workDir, { recursive: true, force: true });
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${h.sqlString(serviceName)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${h.sqlString(lastName)})`) === '0',
    'An owned referral, service or specialist row remains');
  });

  sql.execute(`UPDATE demographic SET first_name=${utf8Literal(FIRST_NAME)}
    WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  // The database must hold the characters before the PDF can be blamed for losing them.
  h.assert(sql.value(`SELECT HEX(first_name) FROM demographic WHERE demographic_no=${patient}`)
    === Buffer.from(FIRST_NAME, 'utf8').toString('hex').toUpperCase(), 'The database did not store the patient name as UTF-8');

  owned.serviceId = insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${h.sqlString(serviceName)},'1')`, 'service');
  const specId = insertId(sql, `INSERT INTO professionalSpecialists
    (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Ann',${h.sqlString(lastName)},'555-0160','555-0161','2 Synthetic Way',NOW(),0,0,0,0)`, 'specialist');
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${owned.serviceId},${specId})`);
  owned.requestId = insertId(sql, `INSERT INTO consultationRequests
    (referalDate,serviceId,specId,providerNo,demographicNo,status,urgency,reason,clinicalInfo,currentMeds,
     allergies,concurrentProblems,statusText,sendTo,patientWillBook,lastUpdateDate)
    VALUES(CURDATE(),${owned.serviceId},${specId},${h.sqlString(provider)},${patient},'1','2',${utf8Literal(reason)},
     ${utf8Literal(CLINICAL_INFO)},'','','','','',0,NOW())`, 'referral');

  await s.step('the printPdf2 route prints the CJK record text in embedded fonts', async () => {
    const response = await s.context.request.get(
      `${config.baseUrl}/encounter/oscarConsultationRequest/printPdf2?reqId=${owned.requestId}`, { timeout: 60000 });
    h.assert(response.status() === 200, `printPdf2 answered ${response.status()}`);
    h.assert((response.headers()['content-type'] || '').includes('application/pdf'), 'printPdf2 did not stream a PDF');
    assertPrinted(Buffer.from(await response.body()), reason, 'the printPdf2 PDF', workDir);
  });

  await s.step('the Print button prints the CJK record text in embedded fonts', async () => {
    const page = await s.context.newPage();
    h.wireStrictPage(page, 'consult-cjk-print', s.recorder);
    await page.goto(`${config.baseUrl}/encounter/ViewRequest?de=${patient}&requestId=${owned.requestId}&appNo=0&teamVar=`, // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- every interpolated value is a validated integer and the base URL is validated
      { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
    h.assert(await page.locator('[name="reasonForConsultation"]').inputValue() === reason,
      'The edit screen opened a referral other than the owned one, or lost the CJK reason');
    const previewArrived = page.waitForResponse(response => CONSULT_POST.test(response.url())
      && response.request().method() === 'POST' && (response.headers()['content-type'] || '').includes('json'),
    { timeout: 60000 });
    const download = page.waitForEvent('download', { timeout: 60000 });
    // Handled now so a failed preview below cannot leave it rejecting unobserved; the await
    // after the preview still reports a missing download.
    download.catch(() => {});
    await page.locator('input[name="printPreview"]').click();
    const reply = await (await previewArrived).json();
    await download;
    h.assert(reply && !reply.errorMessage, `The preview reported an error: ${reply && reply.errorMessage}`);
    h.assert(reply.consultPDF, 'The preview reply carried no PDF');
    assertPrinted(Buffer.from(reply.consultPDF, 'base64'), reason, 'the Print PDF', workDir);
    await page.close();
  });
}

if (require.main === module) runWorkflow('consultation-pdf-cjk', workflow, { openPatient: true, openMaster: false });
module.exports = { unembeddedFonts, workflow };
