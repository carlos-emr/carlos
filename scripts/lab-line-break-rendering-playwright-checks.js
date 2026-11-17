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
 * Browser check for issues #3953 and #4272: HL7 lab text showed a literal "<br />".
 *
 * The lab handlers turn the HL7 line-break escape \.br\ into a "<br />" marker inside the
 * text they return, and the lab views HTML-encoded that marker, so a clinician read
 * "Final<br />11Sep2013" instead of two lines (OBX comments were flattened to a space
 * instead). The fix renders handler text with the break-aware encoder; this check proves it
 * against a real deployment rather than a unit test's idea of one.
 *
 * FIXTURE. Synthetic PATHL7 and ExcellerisON ORU^R01 messages are seeded into
 * hl7TextMessage/hl7TextInfo with provider and patient routing, using the same tables
 * as the uploader. Each message carries:
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
 * The lab PDF is also read back with required pdftotext, because LabPDFCreator consumes
 * the same marker: the lines must be separate and no "<br" may appear.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:lab-line-break-rendering-playwright
 *
 * Optional environment:
 *   LAB_BREAK_SCREENSHOT_DIR  write a screenshot of each view here
 */
const { execFileSync } = require('child_process');
const {
  SkipCheck, appUrl, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner,
  gotoApp, launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage, screenshot,
} = require('./lib/playwright-harness');

const RUN = `${Date.now()}`;
const ACCESSION = `CARLOS3953-${RUN}`;
const LINES = {
  resultA: 'Final', resultB: `11Sep2026 run ${RUN}`,
  rangeA: 'Adult 3.5-5.0', rangeB: 'Child 3.4-4.7',
  commentA: 'Specimen received warm', commentB: 'Repeat collection advised',
};
const HOSTILE = `<script>window.__carlos3953=1</script>\\.br\\<img src=carlos3953 onerror="window.__carlos3953=2">`;

function fixtureMessage(type = 'PATHL7') {
  assert(['PATHL7', 'ExcellerisON', 'TRUENORTH'].includes(type), 'Unknown fixture lab type');
  const version = type === 'ExcellerisON' ? '2.3.1' : '2.3';
  const subId = type === 'ExcellerisON' ? 'A' : '';
  const accession = `${ACCESSION}-${type}`;
  const segments = [
    `MSH|^~\\&|${type}|CW|CARLOS|TEST|20260926120000||ORU^R01|CARLOS3953${RUN}|P|${version}|||ER|AL`,
    'PID||9999999999|9999999999||FAKE-LINEBREAK^CHECK||19800101|F',
    `ORC|RE||${accession}|||||||||99999^FAKE-ORDERING^DOC`,
    `OBR|1||${accession}|CHEM^Chemistry||20260926100000|20260926100000|||||||20260926100000||99999^FAKE-ORDERING^DOC||||||20260926115500||CHEM4|F|||99999^FAKE-ORDERING^DOC`,
    `OBX|1|FT|XXX-0006^Report Status|${subId}|${LINES.resultA}\\.br\\${LINES.resultB}||||||F|||20260926115500`,
    `OBX|2|NM|2823-3^Potassium||4.1|mmol/L|${LINES.rangeA}\\.br\\${LINES.rangeB}|||||F|||20260926115500`,
    `NTE|1|L|${LINES.commentA}\\.br\\${LINES.commentB}`,
    // PATHL7Handler exposes one NTE per OBX, so the hostile text rides in its own result.
    `OBX|3|FT|XXX-0009^Note|${subId}|${HOSTILE}||||||F|||20260926115500`,
  ];
  if (type === 'TRUENORTH') {
    // This handler exposes FT observations as OBR comments. Its break marker is eight spaces.
    return segments.filter(segment => !segment.startsWith('NTE|')
      && (!segment.startsWith('OBX|') || segment.startsWith('OBX|1|')))
      .join('\r').replaceAll('\\.br\\', '        ');
  }
  return segments.join('\r');
}

/** Rows this run wrote; cleanup deletes exactly these and nothing else. */
const owned = { labNos: [], patientNo: null };
const PATIENT_MARKER = `FAKE-LINEBREAK-${RUN}`;

function seed(sql, providerNo, demographicNo, type) {
  const accession = `${ACCESSION}-${type}`;
  const base64 = Buffer.from(fixtureMessage(type), 'utf8').toString('base64');
  // One mysql session, so LAST_INSERT_ID() is this INSERT's id.
  const labNo = sql.value(
    'INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)'
    + ` VALUES (0, ${sqlString(base64)}, ${sqlString(type)}, 'CARLOS3953', NOW()); SELECT LAST_INSERT_ID();`);
  assert(/^\d+$/.test(labNo) && labNo !== '0', 'fixture lab did not insert');
  owned.labNos.push(labNo);
  sql.execute(
    'INSERT INTO hl7TextInfo (lab_no, sex, health_no, result_status, final_result_count, obr_date, priority,'
    + ' requesting_client, discipline, last_name, first_name, report_status, accessionNum, filler_order_num,'
    + ' sending_facility)'
    + ` VALUES (${labNo}, 'F', '9999999999', 'A', 2, '2026-09-26 10:00:00', 'R', 'FAKE-ORDERING, DOC',`
    + ` 'CHEM', 'FAKE-LINEBREAK', 'CHECK', 'F', ${sqlString(accession)}, ${sqlString(accession)}, 'CW')`);
  sql.execute(
    'INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type)'
    + ` VALUES (${sqlString(providerNo)}, ${labNo}, 'N', 'HL7')`);
  sql.execute(
    'INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created)'
    + ` VALUES (${demographicNo}, ${labNo}, 'HL7', NOW())`);
  return labNo;
}

function cleanup(sql, state = owned) {
  const failures = [];
  for (const value of [...state.labNos].reverse()) {
    try {
      assert(/^[1-9]\d*$/.test(value), 'Invalid owned lab identity');
      const labNo = Number(value);
      assert(sql.value(`SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${labNo}
        AND serviceName='CARLOS3953'`) === '1', 'Owned lab identity changed');
      sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${labNo} AND lab_type='HL7';
        DELETE FROM providerLabRouting WHERE lab_no=${labNo} AND lab_type='HL7';
        DELETE FROM hl7TextInfo WHERE lab_no=${labNo};
        DELETE FROM hl7TextMessage WHERE lab_id=${labNo} AND serviceName='CARLOS3953'`);
      assert(sql.value(`SELECT (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${labNo} AND lab_type='HL7')
        +(SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${labNo} AND lab_type='HL7')
        +(SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${labNo})
        +(SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${labNo})`) === '0', 'Owned lab rows remain');
      state.labNos.splice(state.labNos.indexOf(value), 1);
    } catch (error) { failures.push(error); }
  }
  if (state.patientNo && state.labNos.length === 0) {
    try {
      const patient = state.patientNo;
      assert(/^[1-9]\d*$/.test(patient), 'Invalid owned patient identity');
      assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
        AND last_name=${sqlString(PATIENT_MARKER)}`) === '1', 'Owned patient identity changed');
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${sqlString(PATIENT_MARKER)}`);
      assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) === '0', 'Owned patient remains');
      state.patientNo = null;
    } catch (error) { failures.push(error); }
  }
  if (failures.length) throw new AggregateError(failures, 'Lab fixture cleanup failed');
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
    await screenshot(page, screenshotDir, label);
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
  for (const [a, b] of [[LINES.commentA, LINES.commentB], [LINES.resultA, LINES.resultB],
    [LINES.rangeA, LINES.rangeB]]) {
    const first = lineOf(a);
    const second = lineOf(b);
    assert(first >= 0 && second > first, `lab PDF did not put "${a}" and "${b}" on separate lines`);
  }
  assert(text.includes('<script>window.__carlos3953=1</script>'), // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- checks extracted synthetic PDF text; no HTML is rendered
    'lab PDF lost or interpreted the literal script-like result text');
}

async function main() {
  const config = readConfig();
  const screenshotDir = process.env.LAB_BREAK_SCREENSHOT_DIR || '';
  const sql = createSqlRunner(config.mysql);
  let browser;
  const failures = [];
  try {
    if (!pdftotextAvailable()) throw new SkipCheck('pdftotext is required for lab PDF validation');
    const tables = Number(sql.value(
      "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"
      + " AND table_name IN ('hl7TextMessage','hl7TextInfo','providerLabRouting','patientLabRouting')"));
    if (tables !== 4) throw new SkipCheck('the HL7 lab tables are absent');
    const providerNo = sql.value(`SELECT provider_no FROM security WHERE user_name=${sqlString(config.testUser)} LIMIT 1`);
    if (!providerNo) throw new SkipCheck(`no security row for ${config.testUser}`);
    const demographicNo = sql.value(`INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,
       hc_type,province,roster_status,lastUpdateDate)
      VALUES (${sqlString(PATIENT_MARKER)},'CHECK','1980','01','01','F','AC',${sqlString(providerNo)},
        'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(demographicNo), 'Owned synthetic patient did not insert');
    owned.patientNo = demographicNo;
    const recorder = createRecorder();
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    await context.addInitScript(() => { window.close = () => {}; });
    const schedule = await login(context, config, recorder);
    await schedule.close();

    for (const type of ['PATHL7', 'ExcellerisON']) {
      const labNo = seed(sql, providerNo, demographicNo, type);
      const query = `segmentID=${labNo}&providerNo=${encodeURIComponent(providerNo)}`
        + `&searchProviderNo=${encodeURIComponent(providerNo)}&status=N&demographicId=${demographicNo}`;

      const full = await openView(context, config, recorder, `lab-display-${type}`,
        `/lab/CA/ALL/ViewLabDisplay?${query}`, screenshotDir);
      assertRendered(`${type} labDisplay`, await inspect(full, LINES));
      if (type === 'ExcellerisON') {
        assert((await full.locator('em').allTextContents()).some(text => text.includes('A) Final')),
          'ExcellerisON full view did not exercise the composite sub-ID branch');
      }
      console.log('PASS labDisplay renders HL7 \\.br\\ as line breaks and keeps the hostile result inert');

      const preview = await openView(context, config, recorder, `lab-display-ajax-${type}`,
        `/lab/CA/ALL/ViewLabDisplayAjax?${query}`, screenshotDir);
      assertRendered(`${type} labDisplayAjax`, await inspect(preview, LINES));
      if (type === 'ExcellerisON') {
        assert((await preview.locator('em').allTextContents()).some(text => text.includes('A) Final')),
          'ExcellerisON preview did not exercise the composite sub-ID branch');
      }
      console.log('PASS labDisplayAjax renders HL7 \\.br\\ as line breaks and keeps the hostile result inert');
      await preview.close();

      await checkPdf(full, config, labNo);
      console.log('PASS lab PDF puts HL7 line breaks on separate lines with no visible marker');
      await full.close();
      assertStrictPage(recorder);
      console.log(`PASS ${type} full/preview/PDF rendering`);
    }
    const labNo = seed(sql, providerNo, demographicNo, 'TRUENORTH');
    const query = `segmentID=${labNo}&providerNo=${encodeURIComponent(providerNo)}`
      + `&searchProviderNo=${encodeURIComponent(providerNo)}&status=N&demographicId=${demographicNo}`;
    for (const route of ['ViewLabDisplay', 'ViewLabDisplayAjax']) {
      const page = await context.newPage();
      wireStrictPage(page, `TRUENORTH-${route}`, recorder);
      const response = await gotoApp(page, config.baseUrl, `/lab/CA/ALL/${route}?${query}`);
      assert(response && response.status() === 200, `${route}: TRUENORTH returned an error`);
      await assertNotErrorPage(page, route);
      const text = await page.locator('body').innerText();
      assert(text.split(LINES.resultB).length - 1 === 1,
        `${route}: TRUENORTH FT result must appear exactly once as an OBR comment`);
      assert((await inspect(page, LINES)).result, `${route}: TRUENORTH comment lost its line break`);
      await page.close();
      assertStrictPage(recorder);
      console.log(`PASS TRUENORTH ${route} displays the comment once with its line break`);
    }
  } catch (error) { failures.push(error); }
  finally {
    try { if (browser) await browser.close(); } catch (error) { failures.push(error); }
    try { cleanup(sql); } catch (error) { failures.push(error); }
    try { sql.dispose(); } catch (error) { failures.push(error); }
  }
  if (failures.length === 1) throw failures[0];
  if (failures.length > 1) throw new AggregateError(failures, 'Lab validation and cleanup failed');
}

if (require.main === module) runCheck({ name: 'lab-line-break-rendering', run: main });
module.exports = { main, fixtureMessage, cleanup };
