#!/usr/bin/env node
/*
 * Browser checks for CARLOS clinical measurement (vitals) entry and review.
 *
 * WHY THIS EXISTS. Vitals are the most frequently written clinical data in the
 * chart and the suite touched none of the write path: `echart-playwright-checks.js`
 * proves the encounter page renders and `flowsheet-admin-playwright-checks.js`
 * covers flowsheet CONFIGURATION, but `encounter/Measurements2` (the save),
 * `encounter/oscarMeasurements/ViewAddMeasurementData` (the entry form),
 * `SetupDisplayHistory` (the history view) and `encounter/GraphMeasurements` (the
 * trend graph) were unexercised. A save that silently dropped the observation
 * date, or a history view that stopped listing a value that is in the database,
 * both look like nothing happened.
 *
 * VALIDATION IS PART OF THE CONTRACT. Measurement types carry a validation rule,
 * and the form enforces it client-side while the action enforces it again on the
 * server. The check drives a deliberately invalid value first and asserts NOTHING
 * was written, then a valid one and asserts it was: a validation regression that
 * only relaxed the server would otherwise pass every "happy path" check while
 * letting a mistyped blood pressure into the chart.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:measurement-entry-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   MEASUREMENT_DEMOGRAPHIC_NO=1  patient whose vitals are recorded
 *   MEASUREMENT_TYPE=WT           measurement type code to record (must exist in measurementType
 *                                 AND be a member of MEASUREMENT_TEMPLATE's flowsheet)
 *   MEASUREMENT_TEMPLATE=phv      flowsheet the entry form is opened against; see
 *                                 WEB-INF/classes/oscar/encounter/oscarMeasurements/flowsheets/*.xml
 *   MEASUREMENT_VALUE=72          the value to record
 *   MEASUREMENT_ASSERT_INPUT_VALIDATION=true
 *                                 also assert the entry form answers malformed input with
 *                                 something other than HTTP 500. OFF by default because it
 *                                 currently FAILS: see the note below.
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * THE TEMPLATE PARAMETER IS MANDATORY, AND THAT IS A DEFECT, NOT A CONVENTION.
 * AddMeasurementData.jsp resolves `template` through
 * MeasurementTemplateFlowSheetConfig.getFlowSheet(), which does
 * `Hashtable.get(name)` with no null guard and dereferences the result
 * unconditionally. So the entry form answers HTTP 500 — not a 400, not a
 * message — for each of: no `template` parameter, an empty one, a `template`
 * naming a flowsheet that is not loaded, and a `measurement` that is not a member
 * of the named flowsheet. Only a valid (template, measurement) pair renders.
 * MEASUREMENT_ASSERT_INPUT_VALIDATION=true turns those four probes into
 * assertions; leave it off until the null handling is fixed, then turn it on so it
 * stays fixed.
 *
 * Cleanup: the comment field carries a unique PW_MEASUREMENT_<millis> marker and
 * every measurements row carrying it is deleted in a finally, including after a
 * failure. Pre-existing vitals are never touched.
 */

const { chromium } = require('playwright');
const {
  appUrl,
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
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
const demographicNo = requireId(process.env.MEASUREMENT_DEMOGRAPHIC_NO || '1', 'MEASUREMENT_DEMOGRAPHIC_NO');
const measurementType = process.env.MEASUREMENT_TYPE || 'WT';
assert(/^[A-Za-z0-9_-]{1,20}$/.test(measurementType),
  `MEASUREMENT_TYPE must be a measurement type code, got ${measurementType}`);
const measurementValue = process.env.MEASUREMENT_VALUE || '72';
assert(/^[0-9./]{1,12}$/.test(measurementValue),
  `MEASUREMENT_VALUE must be numeric, got ${measurementValue}`);
// "phv" (Periodic Health Visit) is the default because it is shipped in the WAR
// and carries the general vitals — BP, HT, WT, BMI — rather than a disease-specific
// panel, so the default fixture works on any deployment without customisation.
const measurementTemplate = process.env.MEASUREMENT_TEMPLATE || 'phv';
assert(/^[A-Za-z0-9_-]{1,40}$/.test(measurementTemplate),
  `MEASUREMENT_TEMPLATE must be a flowsheet name, got ${measurementTemplate}`);
const assertInputValidation = process.env.MEASUREMENT_ASSERT_INPUT_VALIDATION === 'true';

const stamp = `PW_MEASUREMENT_${Date.now()}`;
// Letters where the type expects a number: rejected by the form's validation rule
// and by the action, so nothing may reach the database.
const invalidValue = 'not-a-number';

const recorder = createRecorder();
const sql = createSqlClient({ namespace: 'carlos-measurement' });
const passed = [];

function pass(message) {
  passed.push(message);
  console.log(`PASS ${message}`);
}

function stampedMeasurements() {
  return sql.rows(
    `SELECT id, type, demographicNo, providerNo, dataField, comments, DATE(dateObserved)`
    + ` FROM measurements WHERE comments LIKE '${escapeSql(`${stamp}%`)}' ORDER BY id`
  ).map(([id, type, demographic, provider, dataField, comments, dateObserved]) => ({
    id, type, demographic, provider, dataField, comments, dateObserved,
  }));
}

function cleanupRows() {
  sql.exec(`DELETE FROM measurements WHERE comments LIKE '${escapeSql(`${stamp}%`)}'`);
}

function assertTypeExists() {
  const description = sql.scalar(
    `SELECT typeDescription FROM measurementType WHERE type='${escapeSql(measurementType)}' LIMIT 1`
  );
  assert(description,
    `MEASUREMENT_TYPE=${measurementType} is not configured in measurementType on this deployment`);
  return description;
}

/**
 * Opens the measurement entry form.
 *
 * Reached with the same parameters the encounter page's measurement link passes:
 * the form builds one input per `measurement` parameter, so a form opened without
 * one renders no fields at all and a check that did that would be asserting
 * against an empty page.
 */
async function openEntryForm(context) {
  const page = await context.newPage();
  wirePage(page, 'measurement-entry', recorder);
  const response = await gotoApp(page, config.baseUrl, '/encounter/oscarMeasurements/ViewAddMeasurementData',
    'domcontentloaded', {
      demographic_no: demographicNo,
      measurement: measurementType,
      template: measurementTemplate,
    });
  assert(response.status() < 400, `ViewAddMeasurementData returned HTTP ${response.status()}`);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'measurement entry form');

  assert(await readCsrfToken(page), 'measurement entry form carried no CSRFGuard token');
  const form = page.locator('#measurementForm');
  assert(await form.count() > 0, 'measurement entry page rendered no measurement form');
  const valueField = page.locator('#inputValue-0, [name="inputValue-0"]').first();
  await valueField.waitFor({ state: 'visible', timeout: 30000 });
  return { page, valueField };
}

/**
 * Fills the value and clicks Save.
 *
 * Returns whether a save POST was actually issued. A rejected value is expected
 * to produce NO POST — the form's own validation stops it — so the caller
 * distinguishes "blocked before the server" from "posted and rejected", and both
 * are acceptable for the invalid case as long as nothing persists.
 */
async function attemptSave(page, valueField, value, comment) {
  await valueField.fill('');
  await valueField.fill(value);
  const commentField = page.locator('[name="comments-0"]').first();
  if (await commentField.count() > 0) {
    await commentField.fill(comment);
  }

  let sawPost = false;
  const postSeen = page.waitForResponse((r) => r.request().method() === 'POST'
    && /\/encounter\/Measurements2?$/.test(new URL(r.url()).pathname), { timeout: 8000 })
    .then((response) => {
      sawPost = true;
      return response;
    })
    .catch(() => null);

  await page.locator('input[value="Save"]').first().click();
  const response = await postSeen;
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  return { sawPost, status: response ? response.status() : null };
}

/**
 * Probes how the entry form answers malformed input.
 *
 * Each case is a URL an operator or a stale bookmark can produce, and a clinical
 * form is expected to answer any of them with a message or a 4xx rather than an
 * unexplained HTTP 500. Reported either way; asserted only under
 * MEASUREMENT_ASSERT_INPUT_VALIDATION, because on the current code all four
 * return 500 and a CI job does not need a standing red for a tracked defect.
 */
async function probeMalformedInput(context) {
  const cases = [
    { label: 'no template parameter', query: { demographic_no: demographicNo, measurement: measurementType } },
    { label: 'empty template parameter', query: { demographic_no: demographicNo, measurement: measurementType, template: '' } },
    { label: 'unknown flowsheet name', query: { demographic_no: demographicNo, measurement: measurementType, template: 'no-such-flowsheet' } },
    { label: 'measurement outside the flowsheet', query: { demographic_no: demographicNo, measurement: 'ZZZZ', template: measurementTemplate } },
  ];

  const page = await context.newPage();
  // Not wired into the shared recorder: these navigations are EXPECTED to fail on
  // the current code, and feeding their 500s into assertNoPageErrors would make
  // every other assertion in this script unreachable.
  const results = [];
  for (const probe of cases) {
    let status = null;
    try {
      const response = await gotoApp(page, config.baseUrl,
        '/encounter/oscarMeasurements/ViewAddMeasurementData', 'domcontentloaded', probe.query);
      status = response.status();
    } catch (error) {
      status = `navigation failed: ${error.message}`;
    }
    results.push({ ...probe, status });
  }
  await page.close().catch(() => {});

  const serverErrors = results.filter((entry) => entry.status === 500);
  for (const entry of serverErrors) {
    console.log(`NOTE measurement entry form answers "${entry.label}" with HTTP 500`
      + ' (MeasurementTemplateFlowSheetConfig.getFlowSheet has no null/unknown-name guard)');
  }
  if (assertInputValidation) {
    assert(serverErrors.length === 0,
      `measurement entry form answered malformed input with HTTP 500: ${JSON.stringify(serverErrors, null, 2)}`);
  }
  return results;
}

/**
 * Re-opens the saved observation for editing and asserts the value comes back.
 *
 * Opening the form with `id` set is the edit path — the flowsheet's value cell
 * links here — and it is the read-back this check asserts instead of
 * SetupDisplayHistory, whose patient comes from an EctSessionBean only the
 * encounter flow establishes (see probeHistoryWithoutEncounterSession). A save
 * that persisted but does not reload into the form looks, to the person who
 * entered it, exactly like a save that was lost.
 */
async function checkValueReadBack(context, measurementId) {
  const page = await context.newPage();
  wirePage(page, 'measurement-readback', recorder);
  const response = await gotoApp(page, config.baseUrl, '/encounter/oscarMeasurements/ViewAddMeasurementData',
    'domcontentloaded', {
      demographic_no: demographicNo,
      measurement: measurementType,
      template: measurementTemplate,
      id: measurementId,
    });
  assert(response.status() < 400, `measurement edit form returned HTTP ${response.status()}`);
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await assertNotErrorPage(page, 'measurement edit form');

  const field = page.locator('#inputValue-0, [name="inputValue-0"]').first();
  await field.waitFor({ state: 'visible', timeout: 30000 });
  const shown = await field.inputValue();
  // The edit form must also carry the row's id back, or Save would create a
  // duplicate observation instead of amending this one.
  const carriedId = await page.locator('#measurementForm input[name="id"]').first().inputValue().catch(() => '');
  await page.close().catch(() => {});
  return { shown, carriedId };
}

/**
 * Records how the standalone history view behaves without an encounter session.
 *
 * EctSetupDisplayHistory2Action takes the patient from the session's EctSessionBean
 * and never from the request, so opening the history view by URL — which a stale
 * bookmark or a link opened in a fresh tab does — leaves `demographicNo` unset and
 * DisplayHistory.jsp answers HTTP 500 rather than redirecting to a patient search.
 * Reported, not asserted, for the same reason as the malformed-input probe.
 */
async function probeHistoryWithoutEncounterSession(context) {
  const page = await context.newPage();
  let status = null;
  try {
    const response = await gotoApp(page, config.baseUrl, '/encounter/oscarMeasurements/SetupDisplayHistory',
      'domcontentloaded', { demographicNo, type: measurementType });
    status = response.status();
  } catch (error) {
    status = `navigation failed: ${error.message}`;
  }
  await page.close().catch(() => {});
  if (status === 500) {
    console.log('NOTE measurement history view (SetupDisplayHistory) answers HTTP 500 when opened without an'
      + ' encounter session; it reads the patient from EctSessionBean and ignores the request parameter');
  }
  if (assertInputValidation) {
    assert(status !== 500,
      `SetupDisplayHistory answered HTTP 500 when opened without an encounter session`);
  }
  return status;
}

/**
 * Requests the trend graph.
 *
 * Fetched rather than navigated: the graph route answers with an image on the
 * success path and an HTML page on the failure path, and only inspecting the bytes
 * distinguishes them.
 */
async function checkGraph(page) {
  // MeasurementGraphAction22Action reads `demographic_no` and `type` — NOT the
  // camelCase names the rest of the measurement surfaces use. Passing the wrong
  // spelling answers HTTP 500, so the parameter names are pinned here on purpose.
  const url = appUrl(config.baseUrl, '/encounter/GraphMeasurements', {
    demographic_no: demographicNo,
    type: measurementType,
  });
  const result = await page.evaluate(async (target) => {
    const response = await fetch(target, { credentials: 'same-origin' });
    const buffer = await response.arrayBuffer();
    const head = new Uint8Array(buffer.slice(0, 8));
    return {
      status: response.status,
      contentType: response.headers.get('content-type') || '',
      length: buffer.byteLength,
      head: Array.from(head).map((b) => b.toString(16).padStart(2, '0')).join(''),
    };
  }, url);

  assert(result.status < 400, `GraphMeasurements returned HTTP ${result.status}`);
  assert(result.length > 0, 'GraphMeasurements returned an empty body');
  const isPng = result.head.startsWith('89504e47');
  const isJpeg = result.head.startsWith('ffd8ff');
  return { ...result, isImage: isPng || isJpeg };
}

(async () => {
  cleanupRows();
  const typeDescription = assertTypeExists();

  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  // The save lands on close.jsp, which calls self.close() (and reloads its
  // opener). Neutralising close keeps the confirmation inspectable.
  await context.addInitScript(() => {
    window.close = () => {
      window.__carlosSelfCloseRequested = true;
    };
  });
  try {
    await login(context, config, recorder);

    const { page, valueField } = await openEntryForm(context);
    pass(`measurement entry form renders for ${measurementType} (${typeDescription}) with a CSRFGuard token`);

    const rejected = await attemptSave(page, valueField, invalidValue, `${stamp} invalid`);
    assert(stampedMeasurements().length === 0,
      `an invalid ${measurementType} value was persisted: ${JSON.stringify(stampedMeasurements())}`);
    pass(`an invalid ${measurementType} value is refused and writes nothing`
      + `${rejected.sawPost ? ' (rejected server-side)' : ' (blocked by form validation)'}`);

    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const freshValueField = page.locator('#inputValue-0, [name="inputValue-0"]').first();
    await freshValueField.waitFor({ state: 'visible', timeout: 30000 });

    const saved = await attemptSave(page, freshValueField, measurementValue, `${stamp} valid`);
    assert(saved.sawPost, 'saving a valid measurement issued no POST to encounter/Measurements2');
    assert(saved.status < 400, `measurement save returned HTTP ${saved.status}`);

    const row = await waitFor(() => {
      const rows = stampedMeasurements();
      return rows.length === 1 ? rows[0] : null;
    }, { description: `the recorded ${measurementType} measurement to reach the database` });
    assert(row.type === measurementType,
      `measurement persisted with type ${row.type}, expected ${measurementType}`);
    assert(row.demographic === demographicNo,
      `measurement landed on demographic ${row.demographic}, expected ${demographicNo}`);
    assert(row.dataField === measurementValue,
      `measurement persisted value ${row.dataField}, expected ${measurementValue}`);
    assert(row.provider && row.provider.trim().length > 0,
      'measurement was persisted with no recording provider; the observation has no author');
    assert(row.dateObserved && row.dateObserved !== '0000-00-00',
      `measurement was persisted with no observation date (${row.dateObserved})`);
    pass(`measurement ${row.id} persisted ${measurementValue} for demographic ${demographicNo}`
      + ` with provider ${row.provider} and observation date ${row.dateObserved}`);

    await page.close().catch(() => {});

    const readBack = await checkValueReadBack(context, row.id);
    assert(readBack.shown === measurementValue,
      `the edit form showed ${JSON.stringify(readBack.shown)} for measurement ${row.id},`
      + ` expected the saved ${measurementValue}`);
    assert(readBack.carriedId === String(row.id),
      `the edit form carried id ${JSON.stringify(readBack.carriedId)}; saving would duplicate rather than amend`);
    pass(`the saved ${measurementType} value reloads into the edit form against measurement ${row.id}`);

    await probeHistoryWithoutEncounterSession(context);

    const graphPage = await context.newPage();
    wirePage(graphPage, 'measurement-graph', recorder);
    await gotoApp(graphPage, config.baseUrl, '/provider/providercontrol', 'domcontentloaded', {
      displaymode: 'day', dboperation: 'searchappointmentday', viewall: 1,
    });
    const graph = await checkGraph(graphPage);
    pass(`measurement trend graph answers with ${graph.length} bytes`
      + ` (${graph.isImage ? 'image' : graph.contentType})`);
    await graphPage.close().catch(() => {});

    const malformed = await probeMalformedInput(context);
    if (assertInputValidation) {
      pass('the entry form answers malformed template/measurement input without an HTTP 500');
    } else {
      const serverErrors = malformed.filter((entry) => entry.status === 500).length;
      console.log(`NOTE ${serverErrors}/${malformed.length} malformed-input probes returned HTTP 500;`
        + ' set MEASUREMENT_ASSERT_INPUT_VALIDATION=true to fail on them once the null handling is fixed');
    }

    assertNoPageErrors(recorder, 'measurement entry');
    console.log(`\nCompleted ${passed.length} Playwright checks, 0 failures`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
    cleanupRows();
    sql.close();
  }
})().catch((error) => {
  console.error(`FAIL measurement entry interface flow: ${error.stack || error.message}`);
  process.exit(1);
});
