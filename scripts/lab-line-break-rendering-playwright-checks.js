#!/usr/bin/env node
/*
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
 * Browser check for issue #3953: HL7 lab text showed a literal "<br />".
 *
 * The lab handlers turn the HL7 line-break escape \.br\ into a "<br />" marker inside the
 * text they return, and the lab views HTML-encoded that marker, so a clinician read
 * "Final<br />11Sep2013" instead of two lines (OBX comments were flattened to a space
 * instead). The fix renders handler text with the break-aware encoder; this check proves it
 * against a real deployment rather than a unit test's idea of one.
 *
 * FIXTURE. A synthetic Excelleris (PATHL7) ORU^R01 -- the format the real Excelleris test
 * messages use \.br\ in -- is seeded straight into hl7TextMessage/hl7TextInfo with provider
 * and patient routing, the same rows the uploader writes. It carries:
 *   - an FT OBX-5 with \.br\ (the case from the issue: "Final\.br\11Sep2013"),
 *   - an NM OBX whose reference range carries \.br\,
 *   - an NTE with \.br\ after that OBX,
 *   - a hostile FT result (script tag and an <img onerror>) that must stay inert text.
 * Every row it writes is removed afterwards, keyed on this run's unique accession.
 *
 * WHAT IS ASSERTED, on both lab views (labDisplay via ViewLabDisplay, and the inbox
 * preview labDisplayAjax via ViewLabDisplayAjax):
 *   - no "<br" is visible as text anywhere on the page,
 *   - each marker renders as a real <br> between the two expected lines,
 *   - the hostile result is shown as text, created no <img>/<script>, and ran nothing.
 * When pdftotext is installed the lab PDF is also read back, because LabPDFCreator consumes
 * the same marker: the lines must be separate and no "<br" may appear.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:lab-line-break-rendering-playwright
 *
 * Optional environment:
 *   LAB_BREAK_DEMOGRAPHIC_NO  demographic to link the fixture to (default: lowest demo number)
 *   LAB_BREAK_SCREENSHOT_DIR  write a screenshot of each view here
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const {
  SkipCheck, appUrl, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner,
  gotoApp, launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');

const RUN = `${Date.now()}`;
const ACCESSION = `CARLOS3953-${RUN}`;
const LINES = {
  resultA: 'Final', resultB: `11Sep2026 run ${RUN}`,
  rangeA: 'Adult 3.5-5.0', rangeB: 'Child 3.4-4.7',
  commentA: 'Specimen received warm', commentB: 'Repeat collection advised',
};
const HOSTILE = `<script>window.__carlos3953=1</script>\\.br\\<img src=carlos3953 onerror="window.__carlos3953=2">`;

function fixtureMessage() {
  return [
    `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|20260926120000||ORU^R01|CARLOS3953${RUN}|P|2.3|||ER|AL`,
    'PID||9999999999|9999999999||FAKE-LINEBREAK^CHECK||19800101|F',
    `ORC|RE||${ACCESSION}|||||||||99999^FAKE-ORDERING^DOC`,
    `OBR|1||${ACCESSION}|CHEM^Chemistry||20260926100000|20260926100000|||||||20260926100000||99999^FAKE-ORDERING^DOC||||||20260926115500||CHEM4|F|||99999^FAKE-ORDERING^DOC`,
    `OBX|1|FT|XXX-0006^Report Status||${LINES.resultA}\\.br\\${LINES.resultB}||||||F|||20260926115500`,
    `OBX|2|NM|2823-3^Potassium||4.1|mmol/L|${LINES.rangeA}\\.br\\${LINES.rangeB}|||||F|||20260926115500`,
    `NTE|1|L|${LINES.commentA}\\.br\\${LINES.commentB}`,
    // PATHL7Handler exposes one NTE per OBX, so the hostile text rides in its own result.
    `OBX|3|FT|XXX-0009^Note||${HOSTILE}||||||F|||20260926115500`,
  ].join('\r');
}

/** Rows this run wrote; cleanup deletes exactly these and nothing else. */
const owned = { labNo: null };

function seed(sql, providerNo, demographicNo) {
  const base64 = Buffer.from(fixtureMessage(), 'utf8').toString('base64');
  // One mysql session, so LAST_INSERT_ID() is this INSERT's id.
  const labNo = sql.value(
    'INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)'
    + ` VALUES (0, ${sqlString(base64)}, 'PATHL7', 'CARLOS3953', NOW()); SELECT LAST_INSERT_ID();`);
  assert(/^\d+$/.test(labNo) && labNo !== '0', 'fixture lab did not insert');
  owned.labNo = labNo;
  sql.execute(
    'INSERT INTO hl7TextInfo (lab_no, sex, health_no, result_status, final_result_count, obr_date, priority,'
    + ' requesting_client, discipline, last_name, first_name, report_status, accessionNum, filler_order_num,'
    + ' sending_facility)'
    + ` VALUES (${labNo}, 'F', '9999999999', 'A', 2, '2026-09-26 10:00:00', 'R', 'FAKE-ORDERING, DOC',`
    + ` 'CHEM', 'FAKE-LINEBREAK', 'CHECK', 'F', ${sqlString(ACCESSION)}, ${sqlString(ACCESSION)}, 'CW')`);
  sql.execute(
    'INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type)'
    + ` VALUES (${sqlString(providerNo)}, ${labNo}, 'N', 'HL7')`);
  sql.execute(
    'INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created)'
    + ` VALUES (${demographicNo}, ${labNo}, 'HL7', NOW())`);
  return labNo;
}

function cleanup(sql) {
  if (!owned.labNo) return;
  const labNo = Number(owned.labNo);
  sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${labNo} AND lab_type='HL7'`);
  sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=${labNo} AND lab_type='HL7'`);
  sql.execute(`DELETE FROM hl7TextInfo WHERE lab_no=${labNo} AND accessionNum=${sqlString(ACCESSION)}`);
  sql.execute(`DELETE FROM hl7TextMessage WHERE lab_id=${labNo} AND serviceName='CARLOS3953'`);
  owned.labNo = null;
}

/**
 * Reads what the page actually rendered: visible text, whether each expected pair of lines is
 * split by a real <br>, and whether the hostile result produced any live markup.
 */
async function inspect(page, lines) {
  return page.evaluate((expected) => {
    const text = document.body.innerText;
    const brSplits = (a, b) => Array.from(document.querySelectorAll('br')).some((br) => {
      const before = br.previousSibling ? br.previousSibling.textContent : '';
      const after = br.nextSibling ? br.nextSibling.textContent : '';
      return before.trim().endsWith(a) && after.trim().startsWith(b);
    });
    return {
      visibleBreakText: /<br\s*\/?>/i.test(text),
      result: brSplits(expected.resultA, expected.resultB),
      range: brSplits(expected.rangeA, expected.rangeB),
      comment: brSplits(expected.commentA, expected.commentB),
      hostileText: text.includes('<script>window.__carlos3953=1</script>'),
      hostileImgText: text.includes('<img src=carlos3953'),
      hostileNodes: document.querySelectorAll('img[src*="carlos3953"]').length
        + Array.from(document.querySelectorAll('script:not([src])'))
          .filter((node) => node.textContent.includes('__carlos3953')).length,
      executed: window.__carlos3953 !== undefined,
    };
  }, lines);
}

function assertRendered(view, seen) {
  assert(!seen.visibleBreakText, `${view}: a "<br />" marker is visible as text`);
  assert(seen.result, `${view}: the OBX-5 \\.br\\ did not render as a line break`);
  assert(seen.range, `${view}: the reference-range \\.br\\ did not render as a line break`);
  assert(seen.comment, `${view}: the NTE \\.br\\ did not render as a line break`);
  assert(seen.hostileText && seen.hostileImgText, `${view}: the hostile result is not shown as literal text`);
  assert(seen.hostileNodes === 0, `${view}: the hostile result produced live markup`);
  assert(!seen.executed, `${view}: the hostile result executed script`);
}

async function openView(context, config, recorder, label, appPath, screenshotDir) {
  const page = await context.newPage();
  wireStrictPage(page, label, recorder);
  const response = await gotoApp(page, config.baseUrl, appPath);
  assert(response && response.status() === 200, `${label} returned HTTP ${response && response.status()}`);
  await assertNotErrorPage(page, label);
  await page.getByText(LINES.commentA, { exact: false }).first().waitFor({ state: 'attached', timeout: 30000 });
  if (screenshotDir) {
    fs.mkdirSync(screenshotDir, { recursive: true });
    await page.screenshot({ path: path.join(screenshotDir, `${label}.png`), fullPage: true });
  }
  return page;
}

function pdftotextAvailable() {
  try {
    execFileSync('pdftotext', ['-v'], { stdio: 'ignore', timeout: 10000 });
    return true;
  } catch (error) {
    return false;
  }
}

async function checkPdf(page, config, labNo) {
  const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue().catch(() => '');
  assert(token, 'lab page bootstrapped no CSRF token for the PDF request');
  const target = appUrl(config.baseUrl, '/lab/CA/ALL/PrintPDF');
  const result = await page.evaluate(async ({ url, csrfToken, segment }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- values are Playwright arguments, not code: url comes from appUrl over the loopback-restricted base URL, csrfToken from the app's own hidden input, segment is the digits-only lab id this check inserted
    const response = await fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': csrfToken },
      body: `segmentID=${encodeURIComponent(segment)}&CSRF-TOKEN=${encodeURIComponent(csrfToken)}`,
    });
    const bytes = new Uint8Array(await response.arrayBuffer());
    let binary = '';
    for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
    return { status: response.status, base64: btoa(binary) };
  }, { url: target, csrfToken: token, segment: labNo });
  assert(result.status === 200, `lab PrintPDF returned HTTP ${result.status}`);
  const bytes = Buffer.from(result.base64, 'base64');
  assert(bytes.subarray(0, 5).toString() === '%PDF-', 'lab PrintPDF did not return a PDF');
  const text = execFileSync('pdftotext', ['-layout', '-', '-'], {
    input: bytes, encoding: 'utf8', timeout: 30000, maxBuffer: 16 * 1024 * 1024,
  });
  assert(!/<br\s*\/?>/i.test(text), 'lab PDF shows a "<br />" marker as text');
  const lines = text.split('\n');
  const lineOf = (needle) => lines.findIndex((line) => line.includes(needle));
  for (const [a, b] of [[LINES.commentA, LINES.commentB], [LINES.resultA, LINES.resultB]]) {
    const first = lineOf(a);
    const second = lineOf(b);
    assert(first >= 0 && second > first, `lab PDF did not put "${a}" and "${b}" on separate lines`);
  }
}

async function main() {
  const config = readConfig();
  const screenshotDir = process.env.LAB_BREAK_SCREENSHOT_DIR || '';
  const sql = createSqlRunner(config.mysql);
  let browser;
  try {
    const tables = Number(sql.value(
      "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"
      + " AND table_name IN ('hl7TextMessage','hl7TextInfo','providerLabRouting','patientLabRouting')"));
    if (tables !== 4) throw new SkipCheck('the HL7 lab tables are absent');
    const providerNo = sql.value(`SELECT provider_no FROM security WHERE user_name=${sqlString(config.testUser)} LIMIT 1`);
    if (!providerNo) throw new SkipCheck(`no security row for ${config.testUser}`);
    const demographicNo = process.env.LAB_BREAK_DEMOGRAPHIC_NO
      || sql.value('SELECT MIN(demographic_no) FROM demographic');
    if (!demographicNo || demographicNo === 'NULL') throw new SkipCheck('no demographic to link the fixture lab to');
    assert(/^\d+$/.test(demographicNo), 'LAB_BREAK_DEMOGRAPHIC_NO must be numeric');

    const labNo = seed(sql, providerNo, demographicNo);
    const recorder = createRecorder();
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    await context.addInitScript(() => { window.close = () => {}; });
    const schedule = await login(context, config, recorder);
    await schedule.close();

    const query = `segmentID=${labNo}&providerNo=${encodeURIComponent(providerNo)}`
      + `&searchProviderNo=${encodeURIComponent(providerNo)}&status=N&demographicId=${demographicNo}`;

    const full = await openView(context, config, recorder, 'lab-display',
      `/lab/CA/ALL/ViewLabDisplay?${query}`, screenshotDir);
    assertRendered('labDisplay', await inspect(full, LINES));
    console.log('PASS labDisplay renders HL7 \\.br\\ as line breaks and keeps the hostile result inert');

    const preview = await openView(context, config, recorder, 'lab-display-ajax',
      `/lab/CA/ALL/ViewLabDisplayAjax?${query}`, screenshotDir);
    assertRendered('labDisplayAjax', await inspect(preview, LINES));
    console.log('PASS labDisplayAjax renders HL7 \\.br\\ as line breaks and keeps the hostile result inert');
    await preview.close();

    if (pdftotextAvailable()) {
      await checkPdf(full, config, labNo);
      console.log('PASS lab PDF puts HL7 \\.br\\ lines on separate lines with no visible marker');
    } else {
      console.log('INFO pdftotext not installed; lab PDF line-break assertion not run');
    }
    await full.close();
    assertStrictPage(recorder);
  } finally {
    try { if (browser) await browser.close(); }
    finally {
      try { cleanup(sql); }
      finally { sql.dispose(); }
    }
  }
}

if (require.main === module) runCheck({ name: 'lab-line-break-rendering', run: main });
module.exports = { main, fixtureMessage };
