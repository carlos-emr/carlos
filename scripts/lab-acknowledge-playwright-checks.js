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
 * Browser check for acknowledging a lab result — the step that records a
 * clinician has SEEN it.
 *
 * lab-macro-tickler covers raising a tickler from a lab macro; nothing drove the
 * acknowledge itself (`oscarMDS/UpdateStatus`), the lab's PDF, or the cumulative
 * values view. An acknowledge that stopped persisting leaves results looking
 * reviewed on screen and unreviewed in the record, with nothing failing anywhere.
 *
 * THE LAB IS OPENED THE WAY A PROVIDER OPENS IT: the Inbox link in the schedule's
 * top nav, then the result's own link in the Inboxhub list. That is the reason this
 * check is written this way rather than navigating to ViewLabDisplay directly — the
 * Inboxhub link carries providerNo, searchProviderNo, status and demoName that a
 * hand-built URL does not, and the acknowledge gate reads the provider context. A
 * direct URL exercises a shape no operator ever produces.
 *
 * labDisplay.jsp is also the reference implementation CLAUDE.md names for the CSRF
 * bootstrap rule: its acknowledge is a fetch() POST that reads the token from a
 * hidden input, so the token's presence is asserted explicitly. A page that renders
 * perfectly but bootstraps no token answers every acknowledge with a rejected
 * request and a console error the clinician never sees.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:lab-acknowledge-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=127.0.0.1 MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   LAB_PROVIDER_NO=999998        provider whose inbox is driven
 *   LAB_SEGMENT_ID=<hl7 lab_no>   lab to review; default: the lowest demo HL7 lab
 *                                 that is linked to a patient
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * TWO REGRESSIONS THIS CHECK PINS, both found by an earlier version of it on
 * 2026.08.0-alpha12:
 *
 * - A lab acknowledge must not touch the DOCUMENT inbox queue. oscarMDSIndex.js
 *   updateStatus() used to call updateDocStatusInQueue(doclabid) for labs as well as
 *   documents, posting the lab segment id as `docid`; the server matched it against
 *   queue_document_link.document_id, and since lab and document ids are separate
 *   sequences, acknowledging lab 22 inactivated the queue link of document 22. The
 *   check plants a queue link whose document_id equals the lab's segment id and
 *   asserts it is still active after the acknowledge. (The window closing mid-fetch
 *   used to make this a race, visible as a console "Failed to fetch"; the console is
 *   asserted clean, so a reappearance fails here too.)
 * - The READ audit must name the lab the page rendered. With showLatest=true the
 *   page renders the newest version of the row's accession, and labDisplay.jsp used
 *   to write the audit row (and resolve the patient) from the REQUESTED id before
 *   making that substitution. The check reads the audit rows the open wrote and
 *   asserts each names the rendered segment.
 *
 * FIXTURE AND CLEANUP. The demo dataset routes every lab to provider 0, so no
 * provider has a reviewable inbox item. This check routes ONE existing demo lab to
 * the test provider, reviews it, and in a finally restores the routing exactly as
 * it found it — a row it created is deleted, a row that already existed keeps its
 * original status and loses only the reviewer comment this run wrote. It never
 * alters the lab itself. LAB_SEGMENT_ID must be LINKED to a patient: labDisplay
 * refuses to acknowledge an unmatched lab, so an unlinked fixture would make the
 * check fail on the fixture rather than on the code.
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  appUrl,
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  validateBaseUrl,
  validateMysqlHost,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
};
const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || '127.0.0.1');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
const providerNo = process.env.LAB_PROVIDER_NO || '999998';
assert(/^\d+$/.test(providerNo), 'LAB_PROVIDER_NO must be numeric');

const ackComment = `PW_LABACK_${Date.now()}`;
const recorder = createRecorder();
const passed = [];

// Captured so cleanup can put the deployment back exactly as it was.
let segmentId = null;
let demographicNo = null;
let routingCreatedByCheck = false;
let originalRoutingStatus = null;
// The planted queue_document_link row (see the header): created when no document
// shares the lab's number, otherwise the existing link's status is remembered.
let queueLinkCreatedByCheck = false;
let originalQueueLinkStatus = null;

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'lab-ack-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  mysqlDefaults = { dir, file };
}
function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}
function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
}
function sqlRows(query) {
  const out = sql(query);
  return out ? out.split('\n').map((line) => line.split('\t')) : [];
}
function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

async function waitFor(probe, description, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await probe();
    if (last) {
      return last;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`timed out waiting for ${description}; last value=${JSON.stringify(last)}`);
}

/**
 * Returns the newest lab sharing this lab's accession number.
 *
 * labDisplay is opened by the Inboxhub with showLatest=true, and on that flag it
 * REPLACES the requested segment with the last of Hl7textResultsData.getMatchingLabs
 * -- the newest version of the same accession (labDisplay.jsp:319). The fixture has
 * to be that newest version, or the acknowledge form on the page would belong to a
 * segment this check never routed to the provider and the review would be written
 * against the wrong row.
 */
function newestOfChain(labNo) {
  const newest = sql(
    'SELECT MAX(h2.lab_no) FROM hl7TextInfo h2'
    + ` WHERE h2.accessionNum = (SELECT h.accessionNum FROM hl7TextInfo h WHERE h.lab_no=${Number(labNo)})`
  );
  return newest || String(labNo);
}

function resolveSegment() {
  if (process.env.LAB_SEGMENT_ID) {
    const requested = process.env.LAB_SEGMENT_ID;
    assert(/^\d+$/.test(requested), 'LAB_SEGMENT_ID must be numeric');
    const linked = sql(
      `SELECT demographic_no FROM patientLabRouting WHERE lab_no=${Number(requested)} AND lab_type='HL7' LIMIT 1`
    );
    assert(linked, `LAB_SEGMENT_ID=${requested} is not an HL7 lab linked to a patient`);
    const newest = newestOfChain(requested);
    assert(newest === requested,
      `LAB_SEGMENT_ID=${requested} is not the newest version of its accession (that is ${newest});`
      + ' the Inboxhub opens labs with showLatest=true, so the page would render'
      + ` ${newest} and this check would review a segment it never routed`);
    return { segmentId: requested, demographicNo: linked };
  }
  // Only labs that are the newest of their accession qualify, for the reason in
  // newestOfChain: the demo dataset ships one accession with 30-odd versions, and
  // the lowest lab_no of that chain is exactly the segment showLatest moves off.
  const row = sqlRows(
    'SELECT h.lab_no, pl.demographic_no FROM hl7TextInfo h'
    + " JOIN patientLabRouting pl ON pl.lab_no=h.lab_no AND pl.lab_type='HL7'"
    + ' WHERE h.lab_no = (SELECT MAX(h2.lab_no) FROM hl7TextInfo h2'
    + '                   WHERE h2.accessionNum = h.accessionNum)'
    + ' ORDER BY h.lab_no LIMIT 1'
  )[0];
  assert(row, 'the deployment has no HL7 lab linked to a patient; set LAB_SEGMENT_ID');
  return { segmentId: row[0], demographicNo: row[1] };
}

function routingRow() {
  const row = sqlRows(
    `SELECT id, status FROM providerLabRouting WHERE provider_no='${escapeSql(providerNo)}'`
    + ` AND lab_no=${Number(segmentId)} AND lab_type='HL7' LIMIT 1`
  )[0];
  return row ? { id: row[0], status: row[1] } : null;
}

/** Routes the chosen lab to the test provider as unreviewed, remembering what was there. */
function seedRouting() {
  const existing = routingRow();
  if (existing) {
    originalRoutingStatus = existing.status;
    sql(`UPDATE providerLabRouting SET status='N' WHERE id=${Number(existing.id)}`);
    return;
  }
  sql(
    'INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type)'
    + ` VALUES ('${escapeSql(providerNo)}', ${Number(segmentId)}, 'N', 'HL7')`
  );
  routingCreatedByCheck = true;
}

function queueLinkRow() {
  const row = sqlRows(
    `SELECT id, status FROM queue_document_link WHERE document_id=${Number(segmentId)} ORDER BY id LIMIT 1`
  )[0];
  return row ? { id: row[0], status: row[1] } : null;
}

/**
 * Plants the document queue link a lab acknowledge must leave alone. queue_id 1 is
 * the seeded default queue; the link is only ever read back by id, so it does not
 * matter to the check whether a document with that number exists.
 */
function seedQueueLink() {
  const existing = queueLinkRow();
  if (existing) {
    originalQueueLinkStatus = existing.status;
    sql(`UPDATE queue_document_link SET status='A' WHERE id=${Number(existing.id)}`);
    return;
  }
  sql(`INSERT INTO queue_document_link (queue_id, document_id, status) VALUES (1, ${Number(segmentId)}, 'A')`);
  queueLinkCreatedByCheck = true;
}

function cleanupQueueLink() {
  if (segmentId === null) {
    return;
  }
  if (queueLinkCreatedByCheck) {
    sql(`DELETE FROM queue_document_link WHERE document_id=${Number(segmentId)} AND queue_id=1 AND status IN ('A','I')`);
  } else if (originalQueueLinkStatus !== null) {
    // mysql -N -B prints a NULL status as the text NULL; put a real NULL back.
    const restored = originalQueueLinkStatus === 'NULL' ? 'NULL' : `'${escapeSql(originalQueueLinkStatus)}'`;
    sql(`UPDATE queue_document_link SET status=${restored} WHERE document_id=${Number(segmentId)}`);
  }
}

function cleanupFixture() {
  if (segmentId === null) {
    return;
  }
  cleanupQueueLink();
  if (routingCreatedByCheck) {
    sql(
      `DELETE FROM providerLabRouting WHERE provider_no='${escapeSql(providerNo)}'`
      + ` AND lab_no=${Number(segmentId)} AND lab_type='HL7'`
    );
    return;
  }
  if (originalRoutingStatus !== null) {
    // A routing row that pre-existed is restored rather than removed, and the
    // reviewer comment this run wrote is cleared off it: the comment lives on the
    // routing row itself, so leaving it behind would put a test marker into a
    // clinician's review history.
    sql(
      `UPDATE providerLabRouting SET status='${escapeSql(originalRoutingStatus)}', comment=''`
      + ` WHERE provider_no='${escapeSql(providerNo)}' AND lab_no=${Number(segmentId)}`
      + ` AND lab_type='HL7' AND comment LIKE '${escapeSql(`%${ackComment}%`)}'`
    );
    sql(
      `UPDATE providerLabRouting SET status='${escapeSql(originalRoutingStatus)}'`
      + ` WHERE provider_no='${escapeSql(providerNo)}' AND lab_no=${Number(segmentId)} AND lab_type='HL7'`
    );
  }
}

/**
 * Opens the Inboxhub from the Inbox link in the schedule's top nav.
 *
 * scheduleNav=1 is what makes that link a real href rather than "#": without it
 * the schedule renders the Inbox tab inert and expects a popup helper instead.
 */
async function openInboxhubFromSchedule(context) {
  const page = await context.newPage();
  wirePage(page, 'schedule', recorder);
  await gotoApp(page, config.baseUrl,
    '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1&scheduleNav=1');
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'schedule day sheet');

  const inboxLink = page.locator('a#inboxLink').first();
  assert(await inboxLink.count() > 0, 'the schedule top nav rendered no Inbox link');
  await Promise.all([
    page.waitForLoadState('domcontentloaded', { timeout: 45000 }),
    inboxLink.click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'Inboxhub');
  assert(/\/web\/inboxhub\/Inboxhub/.test(page.url()),
    `the schedule Inbox link landed on ${page.url()} instead of the Inboxhub`);
  return page;
}

/**
 * Opens the lab by clicking its row in the Inboxhub list.
 *
 * The row's link carries providerNo/searchProviderNo/status/demoName that a
 * hand-built ViewLabDisplay URL would omit, which is exactly why the lab is
 * reached this way.
 */
async function openLabFromInboxhub(context, inboxhub) {
  const row = inboxhub.locator(`tr[data-segment-id="${segmentId}"][data-lab-type="HL7"]`).first();
  await row.waitFor({ state: 'attached', timeout: 30000 });
  const labLink = row.locator('a[onclick*="reportWindow"]').first();
  assert(await labLink.count() > 0, `the Inboxhub row for lab ${segmentId} rendered no result link`);

  const popupPromise = context.waitForEvent('page', { timeout: 45000 });
  await labLink.click();
  const popup = await popupPromise;
  wirePage(popup, 'lab-display', recorder, async (dialog, entry) => {
    // The acknowledge raises a comment prompt and, on some deployments, a
    // confirm. Both are accepted here; the prompt is answered with this run's
    // marker so cleanup can withdraw exactly the review this check wrote.
    recorder.dialogs.push({ ...entry, accepted: true });
    await dialog.accept(dialog.type() === 'prompt' ? ackComment : undefined).catch(() => {});
  });
  await popup.waitForLoadState('domcontentloaded', { timeout: 45000 });
  await popup.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(popup, 'lab display');

  const token = await popup.evaluate(() => {
    const input = document.querySelector('input[name="CSRF-TOKEN"]');
    return input ? input.value : null;
  });
  assert(token,
    'the lab display bootstrapped no CSRF-TOKEN input; its acknowledge fetch would be rejected');
  // The form id carries the segment the page actually resolved to. Asserting it
  // matches the routed one pins the showLatest contract from the operator's side:
  // the Inboxhub row promises a lab, and the page it opens must be that lab.
  const renderedSegments = await popup.locator('form[id^="acknowledgeForm_"]')
    .evaluateAll((forms) => forms.map((form) => form.id.replace('acknowledgeForm_', '')));
  assert(renderedSegments.length > 0,
    `the lab display rendered no acknowledge form at all (segment ${segmentId})`);
  assert(renderedSegments.includes(segmentId),
    `the Inboxhub row for lab ${segmentId} opened a page whose acknowledge form(s) are for`
    + ` ${JSON.stringify(renderedSegments)}; showLatest resolved off the routed segment, so an`
    + ' acknowledge here would be recorded against a lab the provider never had in their inbox');
  return { popup, token };
}

/**
 * Fetches the lab's PDF and inspects the bytes.
 *
 * PrintPDF is a direct-response route, and CLAUDE.md's rule for those exists
 * because the failure mode is an HTML error page delivered with a PDF content
 * type. A navigation would hand that to the browser's viewer and the check could
 * not tell the difference.
 */
async function checkLabPdf(popup, token) {
  const url = appUrl(config.baseUrl, `/lab/CA/ALL/PrintPDF?segmentID=${encodeURIComponent(segmentId)}`);
  const result = await popup.evaluate(async ({ target, csrfToken, segment }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- all values are passed as Playwright arguments, not interpolated into code: target is built by appUrl from the loopback-restricted validateBaseUrl result, csrfToken is read from the app's own hidden CSRF-TOKEN input on this page, and segment is a digits-validated lab id (LAB_SEGMENT_ID regex or the database's own lab_no)
    const response = await fetch(target, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': csrfToken || '' },
      body: `segmentID=${encodeURIComponent(segment)}&CSRF-TOKEN=${encodeURIComponent(csrfToken || '')}`,
    });
    const buffer = await response.arrayBuffer();
    const head = new Uint8Array(buffer.slice(0, 5));
    return {
      status: response.status,
      contentType: response.headers.get('content-type') || '',
      length: buffer.byteLength,
      head: Array.from(head).map((b) => String.fromCharCode(b)).join(''),
    };
  }, { target: url, csrfToken: token, segment: segmentId });

  assert(result.status === 200, `lab PrintPDF returned HTTP ${result.status}`);
  assert(result.head.startsWith('%PDF'),
    `lab PrintPDF did not return PDF bytes: contentType=${result.contentType} head=${JSON.stringify(result.head)}`);
  return result;
}

/**
 * Acknowledges the lab through the page's own Acknowledge control.
 *
 * The POST is the signal, not a dialog: the "are you sure" confirm is conditional
 * (a clinic preference suppresses it, and on a default install confirmAck()
 * returns true without prompting), so waiting for a dialog that will not come is
 * how this check used to hang.
 */
async function acknowledgeLab(popup) {
  const ackControl = popup.locator('input[onclick*="ackLab"], button[onclick*="ackLab"]').first();
  assert(await ackControl.count() > 0, 'the lab display rendered no Acknowledge control');

  const [response] = await Promise.all([
    popup.waitForResponse((r) => r.request().method() === 'POST'
      && /\/oscarMDS\/UpdateStatus$/.test(new URL(r.url()).pathname), { timeout: 45000 }),
    ackControl.click(),
  ]);
  assert(response.status() < 400, `oscarMDS/UpdateStatus returned HTTP ${response.status()}`);

  return waitFor(() => {
    const row = routingRow();
    return row && row.status === 'A' ? row : null;
  }, `lab ${segmentId} routing status to become A (acknowledged)`);
}

async function checkCumulativeValues(context) {
  const page = await context.newPage();
  wirePage(page, 'cumulative-labs', recorder);
  const response = await gotoApp(page, config.baseUrl,
    `/lab/ViewCumulativeLabValues?demographicNo=${encodeURIComponent(demographicNo)}`);
  assert(response.status() < 400, `ViewCumulativeLabValues returned HTTP ${response.status()}`);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'cumulative lab values');
  const text = await page.locator('body').innerText().catch(() => '');
  await page.close().catch(() => {});
  return text;
}

(async () => {
  initMysqlDefaults();
  const resolved = resolveSegment();
  segmentId = resolved.segmentId;
  demographicNo = resolved.demographicNo;
  seedRouting();
  seedQueueLink();

  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  try {
    await login(context, config, recorder);

    const inboxhub = await openInboxhubFromSchedule(context);
    pass(`the schedule Inbox link opens the Inboxhub for provider ${providerNo}`);

    const auditHighWater = sql('SELECT IFNULL(MAX(id), 0) FROM log');
    const { popup, token } = await openLabFromInboxhub(context, inboxhub);
    pass(`the Inboxhub row opens lab ${segmentId} with an acknowledge form and a bootstrapped CSRF token`);

    // Every READ audit row the open wrote must name the segment the page rendered,
    // not the one the row's URL asked for (see the header).
    const auditedSegments = sqlRows(
      `SELECT contentId FROM log WHERE id > ${Number(auditHighWater)} AND provider_no='${escapeSql(providerNo)}'`
      + " AND content='lab' AND action='read' ORDER BY id"
    ).map((row) => row[0]);
    assert(auditedSegments.length > 0, 'opening the lab wrote no READ audit row');
    assert(auditedSegments.every((id) => id === segmentId),
      `the READ audit named lab(s) ${JSON.stringify(auditedSegments)} but the page rendered lab ${segmentId}`);
    pass(`the READ audit names the rendered lab ${segmentId}`);

    const pdf = await checkLabPdf(popup, token);
    pass(`the lab PDF print returns ${pdf.length} real PDF bytes`);

    const acknowledged = await acknowledgeLab(popup);
    pass(`acknowledging lab ${segmentId} persisted routing status ${acknowledged.status}`);

    const unreviewed = sql(
      `SELECT COUNT(*) FROM providerLabRouting WHERE provider_no='${escapeSql(providerNo)}'`
      + ` AND lab_no=${Number(segmentId)} AND lab_type='HL7' AND status='N'`
    );
    assert(unreviewed === '0',
      `lab ${segmentId} is still queued as unreviewed for provider ${providerNo} after acknowledgement`);
    pass('the acknowledged lab no longer sits in the provider unreviewed queue');

    const queueLink = queueLinkRow();
    assert(queueLink && queueLink.status === 'A',
      `acknowledging lab ${segmentId} changed the DOCUMENT queue link for document_id ${segmentId}`
      + ` to ${JSON.stringify(queueLink)}; a lab acknowledge must not reach the document queue`);
    pass(`the document queue link sharing number ${segmentId} is untouched by the lab acknowledge`);

    const cumulative = await checkCumulativeValues(context);
    assert(cumulative.length > 0, 'the cumulative lab values page rendered nothing');
    pass(`cumulative lab values render for demographic ${demographicNo}`);

    assertNoPageErrors(recorder);
    assert(recorder.badResponses.length === 0,
      `unexpected HTTP errors: ${JSON.stringify(recorder.badResponses, null, 2)}`);
    assert(recorder.consoleIssues.length === 0,
      `unexpected console issues: ${JSON.stringify(recorder.consoleIssues, null, 2)}`);
    console.log(`\nPASS lab acknowledge: ${passed.length} checks, 0 failures`);
  } catch (error) {
    console.error(`FAIL lab acknowledge: ${error.stack || error.message}`);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    try {
      cleanupFixture();
    } finally {
      cleanupMysqlDefaults();
    }
  }
})();
