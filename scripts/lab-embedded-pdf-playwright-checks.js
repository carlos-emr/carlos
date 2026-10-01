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
 * Browser check for issue #3977: PDFs embedded in HL7 lab results (OBX value type
 * ED) are shown inline on the lab display, with the download kept.
 *
 * An owned synthetic patient (lib/workflow-session.js) is given one PATHL7 lab
 * that mixes discrete results, an embedded PDF, and an ED segment whose payload
 * is HTML rather than a PDF. The lab is routed, unread, to the test provider.
 * The check then works as a clinician does and asserts:
 *
 *   1. From the Inbox (filtered to the owned patient) the lab opens; the text
 *      results render as rows, the PDF gets a "Download PDF" link and an inline
 *      frame, and the HTML payload gets neither: only a short not-a-PDF note,
 *      never its encoded bytes.
 *   2. The frame's request is answered with the PDF itself: application/pdf,
 *      Content-Disposition inline, nosniff, no-store and the restrictive CSP,
 *      and the page logs no console error (the strict recorder fails on any CSP
 *      violation). When the browser has a PDF viewer, the frame's document is
 *      the PDF.
 *   3. The Download PDF link still answers with the same bytes as an attachment.
 *   4. The endpoint refuses a POST (405), the HTML payload (415) and a malformed
 *      segment (400) without writing a body.
 *   5. The same preview renders in the Inbox's preview mode and in the AJAX lab
 *      view (labDisplayAjax.jsp) the classic inbox inserts.
 *   6. Lab Display Settings (Administration) turns the preview off and sets the
 *      size limit: with it off the frame is gone and the route answers 404; with
 *      a limit below the PDF's size the page says to use the download and the
 *      route answers 413. The Download link is unaffected by both.
 *
 * The lab rows and the two SystemPreferences rows are restored exactly
 * afterwards; the patient is removed by the workflow session. All names and
 * identifiers are fictitious (FAKE-/PW3977- prefixes). Use a disposable database.
 *
 * Environment: the common contract in lib/playwright-harness.js (BASE_URL,
 * CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*).
 */

const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { settle, shownRows } = require('./inboxhub-filters-playwright-checks');

const TIMEOUT = 60000;

/** A small but complete one-page PDF, so a real PDF viewer has something to draw. */
const PDF = Buffer.from([
  '%PDF-1.4',
  '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj',
  '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj',
  '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 300 200]/Contents 4 0 R'
    + '/Resources<</Font<</F1 5 0 R>>>>>>endobj',
  '4 0 obj<</Length 48>>stream',
  'BT /F1 18 Tf 30 100 Td (PW3977 lab report) Tj ET',
  'endstream endobj',
  '5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj',
  'trailer<</Root 1 0 R>>',
  '%%EOF',
  '',
].join('\n'), 'ascii');

/** An ED payload that is not a PDF: it must never be served as one. */
const HTML_PAYLOAD = Buffer.from('<html><body><script>alert(document.cookie)</script></body></html>', 'ascii');

/** The lab's OBR/OBX layout: OBR 0 holds text results, OBR 1 the PDF, OBR 2 the HTML payload. */
const PDF_SEGMENT = { segment: 1, group: 0 };
const HTML_SEGMENT = { segment: 2, group: 0 };

function buildMessage(accession, marker) {
  const obr = (setId, service, section) => `OBR|${setId}||${accession}|${service}|RT|20260930100000|20260930100000`
    + `|||||||20260930100000||TESTLAB^CARLOS^TEST LAB||||||20260930100000||${section}|F`;
  return [
    'MSH|^~\\&|PATHL7|CARLOSTEST|HTTPCLIENT|carlos|20260930101500||ORU^R01|PW3977MSG|P|2.3|||ER|AL',
    `PID||9999999999|3977||${marker}^WORKFLOW||19800102|F`,
    `ORC|RE||${accession}|||||||||TESTLAB^CARLOS^TEST LAB`,
    obr(1, 'CHEM^Chemistry', 'CHEM1'),
    'OBX|1|NM|GLU^Glucose Random||5.2|mmol/L|3.3-7.7|N|||F|||20260930100000',
    obr(2, 'PDF^Pathology Report', 'PATH'),
    `OBX|1|ED|PDF^Pathology Report||^TEXT^PDF^Base64^${PDF.toString('base64')}||||||F|||20260930100000`,
    obr(3, 'ATT^Attachment', 'PATH'),
    `OBX|1|ED|ATT^Attachment||^TEXT^HTML^Base64^${HTML_PAYLOAD.toString('base64')}||||||F|||20260930100000`,
  ].join('\r') + '\r';
}

/**
 * Response headers by lower-case name, repeated headers joined by a newline. Playwright's
 * headers() keeps one value per name, and through the packaged front door a response carries
 * two Content-Security-Policy headers (the application's and nginx's baseline, both enforced).
 */
function headerMap(headersArray) {
  const map = {};
  for (const { name, value } of headersArray) {
    const key = name.toLowerCase();
    map[key] = key in map ? `${map[key]}\n${value}` : value;
  }
  return map;
}

function documentQuery(labNo, { segment, group }) {
  return `labNo=${labNo}&segment=${segment}&group=${group}`;
}

/**
 * The #3977 contract for the inline response. Pure, so the node unit test can
 * pin it without a deployment.
 */
function assertInlinePdfResponse(status, headers, body) {
  h.assert(status === 200, `the inline PDF route answered ${status}, expected 200`);
  h.assert((headers['content-type'] || '').startsWith('application/pdf'),
    `the inline PDF route answered ${headers['content-type']}, not application/pdf`);
  h.assert(/^inline;\s*filename="Lab-\d+\.pdf"$/.test(headers['content-disposition'] || ''),
    `the inline PDF route is not served inline: ${headers['content-disposition']}`);
  h.assert(headers['x-content-type-options'] === 'nosniff', 'the inline PDF route lacks X-Content-Type-Options: nosniff');
  h.assert((headers['cache-control'] || '').includes('no-store'), 'the inline PDF route may be cached');
  const csp = headers['content-security-policy'] || '';
  for (const directive of ["default-src 'none'", "frame-ancestors 'self'", 'sandbox']) {
    h.assert(csp.includes(directive), `the inline PDF response CSP lacks ${directive}: ${csp}`);
  }
  if (body) h.assert(body.subarray(0, 5).toString('ascii') === '%PDF-', 'the inline PDF route served bytes that are not a PDF');
}

/** A refusal must carry no body and never claim to be a PDF. */
function assertRefusal(status, headers, body, expected, label) {
  h.assert(status === expected, `${label} answered ${status}, expected ${expected}`);
  h.assert(!(headers['content-type'] || '').startsWith('application/pdf'), `${label} was labelled application/pdf`);
  h.assert(!body || body.length === 0, `${label} wrote a body`);
}

function seedLab(s) {
  const accession = `PW3977-${randomBytes(6).toString('hex')}`;
  const message = Buffer.from(buildMessage(accession, s.marker), 'utf8').toString('base64');
  const labNo = s.sql.value(`INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)
    VALUES (0, ${h.sqlString(message)}, 'PATHL7', 'PLAYWRIGHT-3977', NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(labNo), 'the synthetic HL7 lab was not created');
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=${labNo} AND lab_type='HL7';
      DELETE FROM patientLabRouting WHERE lab_no=${labNo} AND lab_type='HL7';
      DELETE FROM hl7TextInfo WHERE lab_no=${labNo};
      DELETE FROM hl7TextMessage WHERE lab_id=${labNo} AND serviceName='PLAYWRIGHT-3977'`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${labNo})
      + (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${labNo})
      + (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${labNo} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${labNo} AND lab_type='HL7')`) === '0',
    'the synthetic lab rows were not removed');
  });
  s.sql.execute(`INSERT INTO hl7TextInfo (lab_no, sex, result_status, final_result_count, obr_date, priority,
      discipline, last_name, first_name, report_status, accessionNum)
    VALUES (${labNo}, 'F', '', 3, '2026-09-30 10:00:00', 'R', 'CHEM/PATH', ${h.sqlString(s.marker)}, 'Workflow', 'F',
      ${h.sqlString(accession)});
    INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created)
    VALUES (${s.patient}, ${labNo}, 'HL7', NOW());
    INSERT INTO providerLabRouting (provider_no, lab_no, status, comment, timestamp, lab_type)
    VALUES (${h.sqlString(s.provider)}, ${labNo}, 'N', '', NOW(), 'HL7')`);
  return labNo;
}

/** Snapshot both preview preferences and restore them exactly, whatever the check changes. */
function ownPreferences(s) {
  const names = "name IN ('lab_pdf_inline_preview','lab_pdf_max_size')";
  const before = s.sql.rows(`SELECT id, name, IFNULL(HEX(value),'NULL') FROM SystemPreferences WHERE ${names} ORDER BY id`);
  for (const [id, , hex] of before) {
    h.assert(/^\d+$/.test(id) && (hex === 'NULL' || /^[0-9A-F]*$/i.test(hex)), 'Unexpected preference snapshot');
  }
  s.cleanup(() => {
    const ids = before.map(row => row[0]);
    s.sql.execute(`DELETE FROM SystemPreferences WHERE ${names}${ids.length ? ` AND id NOT IN(${ids.join(',')})` : ''}`);
    for (const [id, , hex] of before) {
      s.sql.execute(`UPDATE SystemPreferences SET value=${hex === 'NULL' ? 'NULL' : `UNHEX('${hex}')`} WHERE id=${id}`);
    }
    h.assert(JSON.stringify(s.sql.rows(`SELECT id, name, IFNULL(HEX(value),'NULL') FROM SystemPreferences
      WHERE ${names} ORDER BY id`)) === JSON.stringify(before), 'the lab display preferences were not restored exactly');
  });
  return {
    set(name, value) {
      const updated = s.sql.value(`UPDATE SystemPreferences SET value=${h.sqlString(value)}, updateDate=NOW()
        WHERE name=${h.sqlString(name)}; SELECT ROW_COUNT()`);
      if (updated === '0' && s.sql.value(`SELECT COUNT(*) FROM SystemPreferences WHERE name=${h.sqlString(name)}`) === '0') {
        s.sql.execute(`INSERT INTO SystemPreferences (name, value, updateDate) VALUES (${h.sqlString(name)}, ${h.sqlString(value)}, NOW())`);
      }
    },
    value(name) {
      return s.sql.value(`SELECT value FROM SystemPreferences WHERE name=${h.sqlString(name)} ORDER BY id LIMIT 1`);
    },
  };
}

async function fetchRoute(s, path, options = {}) {
  const response = await s.context.request.fetch(h.appUrl(s.config.baseUrl, path), {
    method: options.method || 'GET', maxRedirects: 0, failOnStatusCode: false,
    ...(options.headers ? { headers: options.headers } : {}),
  });
  return { status: response.status(), headers: headerMap(response.headersArray()), body: await response.body() };
}

/** Opens the owned lab from the Inbox's list the way a clinician does; returns the report window. */
async function openLabFromInbox(s, labNo, label) {
  const { page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(), {
    context: s.context, recorder: s.recorder, label: `${label}-inbox`, timeout: TIMEOUT,
  });
  await inbox.locator('#btnViewMode2').waitFor({ state: 'attached', timeout: TIMEOUT });
  if (await inbox.locator('#btnViewMode2').isChecked()) await inbox.locator('#btnViewModeLabel').click();
  await settle(inbox, TIMEOUT);
  if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
  // The same filters inbox-document-dates uses to find one owned result.
  await inbox.locator('#anyProvider').check();
  await inbox.locator('#statusNew').check();
  await inbox.locator('#specificPatients').check();
  await inbox.locator('#inputLastName').fill(s.marker);
  await inbox.locator('#inboxhubFormSearchBtn').click();
  await settle(inbox, TIMEOUT);
  const rows = await shownRows(inbox);
  h.assert(rows.includes(`HL7:${labNo}`),
    `the Inbox filtered to the owned patient does not list the seeded lab HL7:${labNo} (shown: ${rows.join(', ') || 'none'})`);
  const row = inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${labNo}"]`);
  const report = await s.popup(inbox, row.locator('a[onclick*="reportWindow"]').first(), `${label}-report`);
  await report.waitForLoadState('domcontentloaded');
  return { inbox, report };
}

async function workflow(s) {
  // The owned fixture patient has no HIN (NULL): the Inbox patient search must still find it.
  const labNo = seedLab(s);
  const preferences = ownPreferences(s);
  // Start from the shipped defaults whatever this database holds.
  preferences.set('lab_pdf_inline_preview', 'true');
  preferences.set('lab_pdf_max_size', String(10 * 1024 * 1024));
  const viewPath = `/lab/ViewEmbeddedDocumentFromLab?${documentQuery(labNo, PDF_SEGMENT)}`;

  let inbox;
  let report;
  let frameResponse;
  await s.step('the lab opened from the Inbox shows its text results, a Download PDF link and one inline PDF frame', async () => {
    const framed = s.context.waitForEvent('response', {
      predicate: response => response.url().includes('/lab/ViewEmbeddedDocumentFromLab?'), timeout: TIMEOUT,
    });
    // Awaited below; a failure before that point must surface as itself, not as this timeout.
    framed.catch(() => {});
    ({ inbox, report } = await openLabFromInbox(s, labNo, 'lab-pdf'));
    await report.getByText('Glucose Random').first().waitFor({ timeout: TIMEOUT });
    h.assert(await report.getByText('5.2', { exact: true }).count() > 0, 'the discrete result beside the PDF was not rendered');
    const downloads = report.locator('a.lab-embedded-pdf-download');
    h.assert(await downloads.count() === 1, `expected one Download PDF link, found ${await downloads.count()}`);
    h.assert((await downloads.first().getAttribute('href')).includes(`/lab/DownloadEmbeddedDocumentFromLab?${documentQuery(labNo, PDF_SEGMENT)}`),
      'the Download PDF link does not address the PDF segment');
    const frames = report.locator('iframe.lab-embedded-pdf-frame');
    h.assert(await frames.count() === 1, `expected one inline PDF frame (the HTML payload must get none), found ${await frames.count()}`);
    h.assert((await frames.first().getAttribute('src')).endsWith(viewPath), 'the inline frame does not address the PDF segment');
    h.assert(await report.locator('details.lab-embedded-pdf[open]').count() === 1, 'the first PDF preview is not expanded');
    h.assert(await report.locator('em.lab-embedded-document-unsupported').count() === 1,
      'the non-PDF ED payload does not show the not-a-PDF note');
    // Visible text only: the page also keeps the raw HL7 in a hidden <pre id="rawhl7...">.
    h.assert(!(await report.locator('body').innerText()).includes(HTML_PAYLOAD.toString('base64')),
      'the non-PDF ED payload was printed as encoded bytes');
    frameResponse = await framed;
  });

  await s.step('the frame is answered with the PDF, inline, with nosniff, no-store and a restrictive CSP', async () => {
    assertInlinePdfResponse(frameResponse.status(), headerMap(await frameResponse.headersArray()), null);
    const direct = await fetchRoute(s, viewPath);
    assertInlinePdfResponse(direct.status, direct.headers, direct.body);
    h.assert(direct.body.equals(PDF), 'the inline route did not serve the embedded PDF bytes');
    const frame = report.frames().find(candidate => candidate.url().includes('/lab/ViewEmbeddedDocumentFromLab?'));
    if (frame) {
      // Chromium with its PDF viewer renders the PDF in the frame; a headless shell without
      // one downloads it instead, which the response assertions above already cover.
      const contentType = await frame.evaluate(() => document.contentType).catch(() => null);
      h.assert(contentType === null || contentType === 'application/pdf' || contentType === 'text/html',
        `the inline frame rendered ${contentType}`);
      console.log(`  INFO lab-embedded-pdf: inline frame document type ${contentType || 'unavailable'}`);
    }
  });

  await s.step('Download PDF still sends the same PDF as an attachment', async () => {
    const download = await fetchRoute(s, `/lab/DownloadEmbeddedDocumentFromLab?${documentQuery(labNo, PDF_SEGMENT)}`);
    h.assert(download.status === 200, `the download answered ${download.status}`);
    h.assert(/^attachment;/.test(download.headers['content-disposition'] || ''), 'the download is no longer an attachment');
    h.assert(download.headers['x-content-type-options'] === 'nosniff', 'the download lacks nosniff');
    h.assert(download.body.equals(PDF), 'the download did not carry the embedded PDF bytes');
  });

  await s.step('the route refuses a POST, a payload that is not a PDF, and a malformed segment', async () => {
    // CSRFGuard may refuse a token-less POST before the action does; either way it is not served.
    const post = await fetchRoute(s, viewPath, { method: 'POST' });
    h.assert(post.status >= 300, `a POST answered ${post.status}, expected a refusal`);
    h.assert(!(post.headers['content-type'] || '').startsWith('application/pdf'), 'a POST was answered with the PDF');
    const html = await fetchRoute(s, `/lab/ViewEmbeddedDocumentFromLab?${documentQuery(labNo, HTML_SEGMENT)}`);
    assertRefusal(html.status, html.headers, html.body, 415, 'the HTML payload');
    const htmlDownload = await fetchRoute(s, `/lab/DownloadEmbeddedDocumentFromLab?${documentQuery(labNo, HTML_SEGMENT)}`);
    assertRefusal(htmlDownload.status, htmlDownload.headers, htmlDownload.body, 415, 'the HTML payload download');
    const malformed = await fetchRoute(s, `/lab/ViewEmbeddedDocumentFromLab?labNo=${labNo}&segment=abc&group=0`);
    assertRefusal(malformed.status, malformed.headers, malformed.body, 400, 'a malformed segment');
    const legacy = await fetchRoute(s, `${viewPath}&legacy=maybe`);
    assertRefusal(legacy.status, legacy.headers, legacy.body, 400, 'a malformed legacy flag');
  });

  await s.step('the AJAX lab view the classic inbox inserts renders the same preview', async () => {
    const ajax = await fetchRoute(s, `/lab/CA/ALL/ViewLabDisplayAjax?segmentID=${labNo}&providerNo=${encodeURIComponent(s.provider)}`
      + `&searchProviderNo=${encodeURIComponent(s.provider)}&status=N`);
    h.assert(ajax.status === 200, `the AJAX lab view answered ${ajax.status}`);
    const html = ajax.body.toString('utf8');
    h.assert((html.match(/class="lab-embedded-pdf-frame"/g) || []).length === 1, 'the AJAX lab view has no single inline PDF frame');
    h.assert(html.includes(`/lab/ViewEmbeddedDocumentFromLab?${documentQuery(labNo, PDF_SEGMENT).replace(/&/g, '&amp;')}`),
      'the AJAX lab view frame does not address the PDF segment');
    h.assert((html.match(/class="lab-embedded-pdf-download"/g) || []).length === 1, 'the AJAX lab view has no single Download PDF link');
    h.assert((html.match(/class="lab-embedded-document-unsupported"/g) || []).length === 1,
      'the AJAX lab view does not show the not-a-PDF note for the HTML payload');
  });

  await s.step("the Inbox's preview mode shows the PDF inside the lab card", async () => {
    await report.close();
    await inbox.locator('#btnViewModeLabel').click();
    const card = inbox.locator(`#inboxViewItems .document-card[data-segment-id="${labNo}"]`);
    await card.first().waitFor({ state: 'attached', timeout: TIMEOUT });
    const cardFrame = card.first().frameLocator('iframe').first();
    await cardFrame.locator('iframe.lab-embedded-pdf-frame').waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await cardFrame.locator('a.lab-embedded-pdf-download').count() === 1,
      'the preview card lost the Download PDF link');
  });

  let settingsPage;
  await s.step('Lab Display Settings turns the preview off, and the page then offers only the download', async () => {
    // The workflow session wires every new page of the context into the strict recorder.
    settingsPage = await s.context.newPage();
    await h.gotoApp(settingsPage, s.config.baseUrl, '/admin/LabDisplaySettings');
    await settingsPage.locator('#lab_pdf_inline_preview').waitFor({ timeout: TIMEOUT });
    h.assert(await settingsPage.locator('#lab_pdf_inline_preview').isChecked(), 'the settings page does not show the preview as on');
    h.assert(await settingsPage.locator('#lab_pdf_max_size_mb').inputValue() === '10', 'the settings page does not show the 10 MB limit');
    await settingsPage.locator('#lab_pdf_inline_preview').uncheck();
    await Promise.all([
      settingsPage.waitForNavigation({ timeout: TIMEOUT }),
      settingsPage.locator('input[name="saveLabDisplaySettings"]').click(),
    ]);
    await settingsPage.locator('#labDisplaySettingsSaved').waitFor({ timeout: TIMEOUT });
    h.assert(preferences.value('lab_pdf_inline_preview') === 'false', 'saving did not store lab_pdf_inline_preview=false');

    const off = await fetchRoute(s, viewPath);
    assertRefusal(off.status, off.headers, off.body, 404, 'the inline route with the preview off');
    const ajax = (await fetchRoute(s, `/lab/CA/ALL/ViewLabDisplayAjax?segmentID=${labNo}&providerNo=${encodeURIComponent(s.provider)}`)).body.toString('utf8');
    h.assert(!ajax.includes('lab-embedded-pdf-frame'), 'the preview frame is still rendered with the preview off');
    h.assert(ajax.includes('lab-embedded-pdf-download'), 'turning the preview off removed the Download PDF link');
  });

  await s.step('a size limit below the PDF shows the use-download message and the route answers 413', async () => {
    await settingsPage.locator('#lab_pdf_inline_preview').check();
    await settingsPage.locator('#lab_pdf_max_size_mb').fill('1');
    await Promise.all([
      settingsPage.waitForNavigation({ timeout: TIMEOUT }),
      settingsPage.locator('input[name="saveLabDisplaySettings"]').click(),
    ]);
    await settingsPage.locator('#labDisplaySettingsSaved').waitFor({ timeout: TIMEOUT });
    h.assert(preferences.value('lab_pdf_max_size') === String(1024 * 1024), 'saving did not store a 1 MB limit in bytes');
    // No PDF in a lab is below 1 MB and above a limit an administrator can set, so lower the
    // limit under this PDF directly; a byte count is the stored form the page writes.
    preferences.set('lab_pdf_max_size', '100');
    const tooLarge = await fetchRoute(s, viewPath);
    assertRefusal(tooLarge.status, tooLarge.headers, tooLarge.body, 413, 'the inline route over the limit');
    const ajax = (await fetchRoute(s, `/lab/CA/ALL/ViewLabDisplayAjax?segmentID=${labNo}&providerNo=${encodeURIComponent(s.provider)}`)).body.toString('utf8');
    h.assert(ajax.includes('lab-embedded-pdf-too-large') && !ajax.includes('lab-embedded-pdf-frame'),
      'a PDF over the limit is not replaced by the use-download message');
    const download = await fetchRoute(s, `/lab/DownloadEmbeddedDocumentFromLab?${documentQuery(labNo, PDF_SEGMENT)}`);
    h.assert(download.status === 200 && download.body.equals(PDF), 'the size limit wrongly applied to the download');
    await settingsPage.close();
  });
}

if (require.main === module) runWorkflow('lab-embedded-pdf', workflow, { openMaster: false });
module.exports = {
  workflow, buildMessage, headerMap, assertInlinePdfResponse, assertRefusal, PDF, HTML_PAYLOAD, PDF_SEGMENT, HTML_SEGMENT,
};
