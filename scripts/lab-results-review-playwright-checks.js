#!/usr/bin/env node
/*
 * Browser checks for the CARLOS lab-results review workflow.
 *
 * WHY THIS EXISTS. Acknowledging a lab result is the step that records a
 * clinician has SEEN it, and it was the largest uncovered clinical surface in the
 * suite: the only lab route any browser check touched was the manual-upload page.
 * `web/inboxhub/Inboxhub`, `lab/CA/ALL/ViewLabDisplay`, `oscarMDS/UpdateStatus`,
 * `lab/ViewCumulativeLabValues` and `lab/CA/ALL/PrintPDF` were all unexercised —
 * so an acknowledge that stopped persisting would leave results looking reviewed
 * on screen and unreviewed in the record, with nothing failing anywhere.
 *
 * labDisplay.jsp is also the reference implementation CLAUDE.md names for the
 * CSRF-token bootstrap rule: its acknowledge is a fetch() POST that reads the
 * token out of a hidden input, so the token's presence is asserted explicitly. A
 * page that renders perfectly but bootstraps no token answers every acknowledge
 * with a rejected request and a console error the clinician never sees.
 *
 * THREE DIALOGS GUARD ONE ACKNOWLEDGE: a comment prompt, an "are you sure"
 * confirm, and (when the lab is unmatched) a second confirm. They are accepted
 * one at a time and by name, because dismissing any of them cancels the POST —
 * which is indistinguishable from a broken acknowledge unless the check asserts
 * the database moved.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:lab-results-review-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   LAB_PROVIDER_NO=999998        provider whose inbox is driven
 *   LAB_SEGMENT_ID=<hl7 lab_no>   lab to review; default: the lowest demo HL7 lab
 *                                 that is linked to a patient
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * FIXTURE AND CLEANUP. The demo dataset routes its labs to provider 0, so no
 * provider has a reviewable inbox item. The check routes ONE existing demo lab to
 * the test provider, reviews it, and in a finally removes the routing row it
 * added and the acknowledgement it wrote — including after a failure. It never
 * alters the lab itself, and a routing row that already existed is restored to
 * the status it had rather than deleted.
 */

const { chromium } = require('playwright');
const {
  acceptNextDialog,
  appUrl,
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  clearPendingDialogAccepts,
  createRecorder,
  createSqlClient,
  escapeSql,
  getLaunchOptions,
  gotoApp,
  login,
  readConfig,
  readCsrfToken,
  requireId,
  waitFor,
  wirePage,
} = require('./carlos-playwright-harness');

const config = readConfig();
const providerNo = requireId(process.env.LAB_PROVIDER_NO || '999998', 'LAB_PROVIDER_NO');

const recorder = createRecorder();
const sql = createSqlClient({ namespace: 'carlos-lab-review' });
const passed = [];
const ackComment = `PW_LAB_REVIEW_${Date.now()}`;

// State captured so cleanup can put the deployment back exactly as it was.
let segmentId = null;
let demographicNo = null;
let routingCreatedByCheck = false;
let originalRoutingStatus = null;

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

/**
 * Picks the lab to review.
 *
 * Requires a lab that is LINKED to a patient: labDisplay's acknowledge asks the
 * server `isLabLinkedToDemographic` first and refuses an unmatched lab, so an
 * unlinked fixture would make the check fail on the fixture rather than on the
 * code.
 */
function resolveSegment() {
  if (process.env.LAB_SEGMENT_ID) {
    const requested = requireId(process.env.LAB_SEGMENT_ID, 'LAB_SEGMENT_ID');
    const linked = sql.scalar(
      `SELECT demographic_no FROM patientLabRouting WHERE lab_no=${requested} AND lab_type='HL7' LIMIT 1`
    );
    assert(linked, `LAB_SEGMENT_ID=${requested} is not an HL7 lab linked to a patient`);
    return { segmentId: requested, demographicNo: linked };
  }

  const row = sql.rows(
    `SELECT h.lab_no, pl.demographic_no FROM hl7TextInfo h`
    + ` JOIN patientLabRouting pl ON pl.lab_no=h.lab_no AND pl.lab_type='HL7'`
    + ` ORDER BY h.lab_no LIMIT 1`
  )[0];
  assert(row, 'the deployment has no HL7 lab linked to a patient; set LAB_SEGMENT_ID');
  return { segmentId: row[0], demographicNo: row[1] };
}

function routingRow() {
  const row = sql.rows(
    `SELECT id, status FROM providerLabRouting WHERE provider_no='${escapeSql(providerNo)}'`
    + ` AND lab_no=${segmentId} AND lab_type='HL7' LIMIT 1`
  )[0];
  return row ? { id: row[0], status: row[1] } : null;
}

/** Routes the chosen lab to the test provider as unreviewed, remembering what was there. */
function seedRouting() {
  const existing = routingRow();
  if (existing) {
    originalRoutingStatus = existing.status;
    sql.exec(`UPDATE providerLabRouting SET status='N' WHERE id=${requireId(existing.id, 'routing id')}`);
    return;
  }
  sql.exec(
    `INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type)`
    + ` VALUES ('${escapeSql(providerNo)}', ${segmentId}, 'N', 'HL7')`
  );
  routingCreatedByCheck = true;
}

function cleanupFixture() {
  if (segmentId === null) {
    return;
  }
  sql.exec(
    `DELETE FROM providerLabRouting WHERE provider_no='${escapeSql(providerNo)}'`
    + ` AND lab_no=${segmentId} AND lab_type='HL7'`
    + (routingCreatedByCheck ? '' : ' AND 1=0')
  );
  if (!routingCreatedByCheck && originalRoutingStatus !== null) {
    // A routing row that pre-existed is restored rather than removed, and the
    // reviewer comment this run wrote is cleared off it: the comment lives on the
    // routing row itself, so leaving it behind would put a test marker into a
    // clinician's review history.
    sql.exec(
      `UPDATE providerLabRouting SET status='${escapeSql(originalRoutingStatus)}', comment=''`
      + ` WHERE provider_no='${escapeSql(providerNo)}' AND lab_no=${segmentId} AND lab_type='HL7'`
      + ` AND comment LIKE '${escapeSql(`%${ackComment}%`)}'`
    );
    sql.exec(
      `UPDATE providerLabRouting SET status='${escapeSql(originalRoutingStatus)}'`
      + ` WHERE provider_no='${escapeSql(providerNo)}' AND lab_no=${segmentId} AND lab_type='HL7'`
    );
  }
}

async function openInboxhub(context) {
  const page = await context.newPage();
  wirePage(page, 'inboxhub', recorder);
  const response = await gotoApp(page, config.baseUrl, '/web/inboxhub/Inboxhub', 'domcontentloaded');
  assert(response.status() < 400, `Inboxhub returned HTTP ${response.status()}`);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'Inboxhub');
  return page;
}

async function openLabDisplay(context) {
  const page = await context.newPage();
  wirePage(page, 'lab-display', recorder);
  const response = await gotoApp(page, config.baseUrl, '/lab/CA/ALL/ViewLabDisplay', 'domcontentloaded', {
    segmentID: segmentId,
    providerNo,
  });
  assert(response.status() < 400, `ViewLabDisplay returned HTTP ${response.status()}`);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  const text = await assertNotErrorPage(page, 'lab display');

  // CLAUDE.md's CSRF bootstrap rule, asserted where it matters: this page's
  // acknowledge is a fetch() POST that reads the token from this input, and a
  // missing token fails the POST with an HTML body the clinician never sees.
  const token = await readCsrfToken(page);
  assert(token, 'lab display page bootstrapped no CSRF-TOKEN input; its acknowledge fetch would be rejected');

  const ackForm = page.locator(`form#acknowledgeForm_${segmentId}`);
  assert(await ackForm.count() > 0, `lab display rendered no acknowledge form for segment ${segmentId}`);
  return { page, text };
}

/**
 * Acknowledges the lab through the page's own control.
 *
 * The accepts are queued in the order the page raises them — the comment prompt
 * first, then the confirm — and the POST is awaited without a timeout race,
 * because a dismissed dialog means no request is ever made.
 */
async function acknowledgeLab(page) {
  const ackControl = page.locator('input[onclick*="ackLab"], button[onclick*="ackLab"]').first();
  assert(await ackControl.count() > 0, 'lab display rendered no acknowledge control');

  // The prompt is answered with this run's marker so cleanup can find exactly the
  // acknowledgement this check wrote and withdraw only that one.
  const promptAccepted = acceptNextDialog(page, recorder, 'ack-comment-prompt', ackComment);
  // The "are you sure" confirm is CONDITIONAL — a clinic preference suppresses it,
  // and on a default install confirmAck() returns true without prompting. The
  // accept is queued anyway so the confirm is answered where it does appear, but
  // it is never awaited: waiting for a dialog that will not come is what hung this
  // check before. The POST is the signal that the acknowledge went through.
  acceptNextDialog(page, recorder, 'ack-confirm');
  const responsePromise = page.waitForResponse((r) => r.request().method() === 'POST'
    && new URL(r.url()).pathname.endsWith('/oscarMDS/UpdateStatus'), { timeout: 45000 });

  await ackControl.click();
  const promptText = await promptAccepted;
  assert(/comment/i.test(promptText),
    `acknowledge raised an unexpected first dialog: ${promptText}`);
  const response = await responsePromise;
  clearPendingDialogAccepts(page);
  assert(response.status() < 400, `oscarMDS/UpdateStatus returned HTTP ${response.status()}`);

  const acknowledged = await waitFor(() => {
    const row = routingRow();
    return row && row.status === 'A' ? row : null;
  }, { description: `lab ${segmentId} routing status to become A (acknowledged)` });
  return acknowledged;
}

/** Confirms the acknowledged lab drops out of the provider's unreviewed inbox. */
function assertLeavesUnreviewedQueue() {
  const unreviewed = sql.scalar(
    `SELECT COUNT(*) FROM providerLabRouting WHERE provider_no='${escapeSql(providerNo)}'`
    + ` AND lab_no=${segmentId} AND lab_type='HL7' AND status='N'`
  );
  assert(unreviewed === '0',
    `lab ${segmentId} is still queued as unreviewed for provider ${providerNo} after acknowledgement`);
}

async function checkCumulativeValues(context) {
  const page = await context.newPage();
  wirePage(page, 'cumulative-labs', recorder);
  const response = await gotoApp(page, config.baseUrl, '/lab/ViewCumulativeLabValues', 'domcontentloaded', {
    demographicNo,
  });
  assert(response.status() < 400, `ViewCumulativeLabValues returned HTTP ${response.status()}`);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  const text = await assertNotErrorPage(page, 'cumulative lab values');
  await page.close().catch(() => {});
  return text;
}

/**
 * Fetches the lab's PDF.
 *
 * Driven through fetch() so the bytes can be inspected: PrintPDF is a
 * direct-response route, and CLAUDE.md's rule for those exists because the
 * failure mode is an HTML error page delivered with a PDF content type. A
 * navigation would hand that to the browser's viewer and the check could not tell.
 */
async function checkLabPdf(page) {
  const url = appUrl(config.baseUrl, '/lab/CA/ALL/PrintPDF', { segmentID: segmentId });
  const token = await readCsrfToken(page);
  const result = await page.evaluate(async ({ target, csrfToken, segment }) => {
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
  assert(result.length > 0, 'lab PrintPDF returned an empty body');
  assert(result.head.startsWith('%PDF'),
    `lab PrintPDF did not return PDF bytes: contentType=${result.contentType} head=${JSON.stringify(result.head)}`);
  return result;
}

(async () => {
  const resolved = resolveSegment();
  segmentId = resolved.segmentId;
  demographicNo = resolved.demographicNo;
  seedRouting();

  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  try {
    await login(context, config, recorder);

    const inbox = await openInboxhub(context);
    pass(`Inboxhub renders for provider ${providerNo}`);
    await inbox.close().catch(() => {});

    const { page: labPage } = await openLabDisplay(context);
    pass(`lab ${segmentId} renders with an acknowledge form and a bootstrapped CSRF token`);

    const pdf = await checkLabPdf(labPage);
    pass(`lab PDF print returns ${pdf.length} real PDF bytes`);

    const acknowledged = await acknowledgeLab(labPage);
    pass(`acknowledging lab ${segmentId} persisted routing status ${acknowledged.status}`);

    assertLeavesUnreviewedQueue();
    pass('the acknowledged lab no longer sits in the provider unreviewed queue');

    const cumulative = await checkCumulativeValues(context);
    assert(cumulative.length > 0, 'cumulative lab values page rendered nothing');
    pass(`cumulative lab values render for demographic ${demographicNo}`);

    assertNoPageErrors(recorder, 'lab results review');
    console.log(`\nCompleted ${passed.length} Playwright checks, 0 failures`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    cleanupFixture();
    sql.close();
  }
})().catch((error) => {
  console.error(`FAIL lab results review interface flow: ${error.stack || error.message}`);
  process.exit(1);
});
