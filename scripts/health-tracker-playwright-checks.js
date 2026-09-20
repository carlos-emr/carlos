#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
//
// Browser check for the restored Health Tracker
// (/encounter/oscarMeasurements/ViewHealthTracker).
//
// WHY THIS CHECK EXISTS. The tracker is the one measurement surface that writes
// from the page itself rather than from a popup, and three of its behaviours are
// invisible in the DOM when they break:
//
//   * the form field for a measurement is NAMED after the flowsheet item's
//     display name with non-word characters stripped. The JSP and
//     HealthTrackerSubmissionParser derive that name independently; if they ever
//     disagree the page still renders, still submits, and silently saves nothing.
//   * the delete control posts with fetch(), which CSRFGuard's client script does
//     NOT hijack, so the page has to send the CSRF-TOKEN header itself. A
//     regression there fails as an HTML error page parsed as JSON inside a catch
//     block nobody sees.
//   * duplicate suppression is what stops a browser refresh or a double-clicked
//     "Save All" from doubling an observation. Nothing else in the suite covers it.
//
// The unit tests pin all three against mocks. Only a browser proves the rendered
// page, the Struts route and the database agree.
//
// The tracker flowsheet ships deliberately EMPTY -- clinicians choose its
// measurements through Edit Flowsheet, stored as flowsheet_customization rows --
// so the check adds one itself for its own patient and removes it afterwards.
// That is also the first-run shape an operator sees, which is why the empty state
// is asserted before the customization is added.
//
// Defaults are for the local devcontainer:
//   npm run test:health-tracker-playwright
//
// Optional environment: the common contract in lib/playwright-harness.js
// readConfig() (BASE_URL, TEST_USER/PASSWORD/PIN, MYSQL_*, CHROME_PATH).
//
// Fixtures: creates and removes its own FAKE-PW patient plus that patient's
// flowsheet_customization and measurements rows. Local disposable database only.

const { appUrl, assert, sqlString, gotoApp, assertNotErrorPage } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

// The display name drives the form field name: "Weight kg" -> "Weightkg".
// Deliberately one that contains a space, so a regression that forgot to strip
// non-word characters produces a field the action cannot read back.
const DISPLAY_NAME = 'Weight kg';
const MEASUREMENT_TYPE = 'WT';
// The tracker names its inputs after the measurement type, which is unique within
// a flowsheet -- display names are not, and two of them can sanitize to one name.
const FIELD = MEASUREMENT_TYPE;
const GOOD_VALUE = '82.5';
// Letters where the type expects a number. A range bound is configurable per
// deployment; no numeric measurement type accepts alphabetic input anywhere.
const BAD_VALUE = 'not-a-number';

function trackerUrl(patient) {
  return `/encounter/oscarMeasurements/ViewHealthTracker?demographic_no=${encodeURIComponent(patient)}&template=tracker`;
}

async function openTracker(s, label) {
  const page = await s.context.newPage();
  await gotoApp(page, s.config.baseUrl, trackerUrl(s.patient));
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNotErrorPage(page, label);
  return page;
}

function measurementRows(s) {
  return Number(s.sql.value(
    `SELECT COUNT(*) FROM measurements WHERE demographicNo=${s.patient} AND type=${sqlString(MEASUREMENT_TYPE)}`));
}

async function workflow(s) {
  const { sql, patient, provider } = s;

  s.cleanup(() => sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient}`));
  s.cleanup(() => sql.execute(`DELETE FROM flowsheet_customization WHERE demographic_no=${sqlString(String(patient))}`));

  let page;

  await s.step('an un-customized tracker offers the add-measurements first run', async () => {
    page = await openTracker(s, 'health-tracker-empty');
    const empty = page.getByText('It looks like you are not tracking any measurements!');
    assert(await empty.count() === 1,
      'The Health Tracker did not render its empty first-run state for a patient with no customizations');
    assert(await page.locator(`a[href*="ViewEditFlowsheet"][href*="htracker"]`).count() > 0,
      'The empty tracker offers no route into Edit Flowsheet, so a clinician cannot populate it');
    await page.close();
  });

  // Add one measurement to the tracker for this patient, the way Edit Flowsheet
  // does: an ADD customization whose payload is the flowsheet <item> element.
  //
  // measurement is the item to insert AFTER, and it is NULL here on purpose.
  // MeasurementFlowSheet.addAfter appends at the end only for null; a non-null
  // name that is not already on the flowsheet resolves to index -1 and throws,
  // which MeasurementTemplateFlowSheetConfig swallows by falling back to the
  // un-customized flowsheet. FlowSheetCustom2Action writes null the same way
  // when the flowsheet is empty (count == 0), which is every first measurement
  // added to a fresh tracker.
  const payload = `<item measurement_type="${MEASUREMENT_TYPE}" display_name="${DISPLAY_NAME}" `
    + 'guideline="" graphable="yes" value_name="Weight" />';
  sql.execute(`INSERT INTO flowsheet_customization
    (flowsheet, action, measurement, payload, provider_no, demographic_no, create_date, archived)
    VALUES ('tracker','add',NULL,${sqlString(payload)},
      ${sqlString(provider)},${sqlString(String(patient))},NOW(),0)`);

  await s.step('a customized measurement renders as a tracker card with an entry field', async () => {
    page = await openTracker(s, 'health-tracker');
    assert(await page.locator(`#wrap-${MEASUREMENT_TYPE}`).count() === 1,
      `The Health Tracker did not render a card for the customized ${MEASUREMENT_TYPE} measurement`);
    assert((await page.locator(`#wrap-${MEASUREMENT_TYPE} .measurement-label`).first().innerText()).trim()
      === DISPLAY_NAME, 'The tracker card is not labelled with the flowsheet item display name');
    // The field name is the contract between the JSP and the save action.
    assert(await page.locator(`#trackerForm input[name="${FIELD}"]`).count() === 1,
      `The entry field is not named ${FIELD}; the save action derives that name from the measurement type`
      + ' and will read back nothing if the two disagree');
    assert(await page.locator(`#trackerForm input[name="${FIELD}_date"]`).count() === 1,
      'The tracker card has no per-row observation date field');
  });

  await s.step('CSRFGuard populates the tracker form token the delete path reads', async () => {
    const token = await page.locator('#trackerForm input[name="CSRF-TOKEN"]').inputValue();
    assert(token && token.length > 0,
      'The tracker form carries no CSRF-TOKEN value; the delete fetch() reads this input for its header'
      + ' and every delete would be rejected as an HTML error page parsed as JSON');
  });

  await s.step('a value entered on the tracker saves and returns to the tracker', async () => {
    await page.locator(`#trackerForm input[name="${FIELD}"]`).fill(GOOD_VALUE);
    await Promise.all([
      page.waitForURL(/ViewHealthTracker/, { timeout: 30000 }),
      page.locator('button[form="trackerForm"][value="Save All"]').click(),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'health-tracker after save');

    await expectValue(sql,
      `SELECT dataField FROM measurements WHERE demographicNo=${patient} AND type=${sqlString(MEASUREMENT_TYPE)}`,
      GOOD_VALUE, 'The value entered on the Health Tracker did not reach the chart');
    assert(await page.getByText(GOOD_VALUE, { exact: false }).count() > 0,
      'The saved value is not shown in the tracker history after the save round-trip');
  });

  await s.step('re-submitting the same observation does not double it', async () => {
    assert(measurementRows(s) === 1, 'Expected exactly one observation before the duplicate submit');
    await page.locator(`#trackerForm input[name="${FIELD}"]`).fill(GOOD_VALUE);
    await Promise.all([
      page.waitForURL(/ViewHealthTracker/, { timeout: 30000 }),
      page.locator('button[form="trackerForm"][value="Save All"]').click(),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    assert(measurementRows(s) === 1,
      'Submitting the same observation twice wrote a second row; a refresh or a double-clicked'
      + ' Save All would double every value in the chart');
  });

  await s.step('a refused value is reported to the clinician and writes nothing', async () => {
    // Either refusal is acceptable and both are reported: the page's own
    // pre-flight may stop the submit before a request leaves the browser, or the
    // save may post and come back with the server's rejection. What is never
    // acceptable is a row appearing in the chart.
    await page.locator(`#trackerForm input[name="${FIELD}"]`).fill(BAD_VALUE);
    await page.locator(`#trackerForm input[name="${FIELD}"]`).dispatchEvent('blur');
    await page.locator('button[form="trackerForm"][value="Save All"]').click();
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'health-tracker after rejected save');

    const alert = page.locator('#validation-alert');
    assert(await alert.isVisible(),
      'A refused value produced no validation alert, so the clinician is told nothing was wrong');
    assert(measurementRows(s) === 1,
      'An invalid value reached the chart, where it is indistinguishable from a real observation');
  });

  await s.step('the server refuses the value too, not just the browser', async () => {
    // The step above may have been satisfied by the page's client-side check,
    // which is user feedback and not a control: anything that can POST can skip
    // it. Post the same value from inside the page -- same session, same CSRF
    // token, the form's own validation bypassed -- and require the server to
    // refuse it on its own.
    const before = measurementRows(s);
    const outcome = await page.evaluate(async ({ field, value, patientId, endpoint }) => {
      const token = document.querySelector('#trackerForm input[name="CSRF-TOKEN"]').value;
      const body = new URLSearchParams();
      body.append('demographic_no', patientId);
      body.append('template', 'tracker');
      body.append('date', new Date().toISOString().slice(0, 10));
      body.append(field, value);
      body.append('submit', 'Save All');
      const response = await fetch(endpoint, {
        method: 'POST',
        credentials: 'same-origin',
        redirect: 'manual',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'X-Requested-With': 'XMLHttpRequest',
          'CSRF-TOKEN': token,
        },
        body: body.toString(),
      });
      return { status: response.status, type: response.type, text: await response.text() };
    }, {
      field: FIELD,
      value: BAD_VALUE,
      patientId: String(patient),
      // Absolute: a relative URL would resolve against the tracker's own path.
      endpoint: appUrl(s.config.baseUrl, '/encounter/oscarMeasurements/HealthTrackerUpdate'),
    });

    assert(outcome.status < 500,
      `The save answered an invalid value with HTTP ${outcome.status};`
      + ' a refused value must not be a server error');
    assert(outcome.status !== 403,
      'The save was rejected as a CSRF failure rather than on its merits, so this step'
      + ' proved nothing about validation');
    // The clean path redirects and the rejection path forwards, so an opaque
    // redirect here would mean the value was taken.
    assert(outcome.type !== 'opaqueredirect',
      'The save redirected as if it had accepted an invalid value');
    assert(outcome.text.includes(DISPLAY_NAME) && outcome.text.includes(BAD_VALUE),
      'The rejection response does not report which value was refused');
    assert(measurementRows(s) === before,
      'An invalid value posted straight at the endpoint reached the chart; the browser-side'
      + ' check is feedback only and the server has to refuse it independently');
  });

  await s.step('deleting an observation from the tracker removes it', async () => {
    page = await openTracker(s, 'health-tracker-delete');
    const id = sql.value(
      `SELECT id FROM measurements WHERE demographicNo=${patient} AND type=${sqlString(MEASUREMENT_TYPE)} LIMIT 1`);
    await page.locator(`.history-value[data-measurement-id="${id}"]`).click();
    const modal = page.locator('#deleteModal');
    await modal.waitFor({ state: 'visible', timeout: 10000 });
    assert((await modal.innerText()).includes(GOOD_VALUE),
      'The delete confirmation does not show the value being deleted');
    await Promise.all([
      page.waitForURL(/ViewHealthTracker/, { timeout: 30000 }),
      page.locator('#deleteButton').click(),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE id=${Number(id)}`, '0',
      'The tracker delete did not remove the observation; the fetch() CSRF header is the usual cause');
  });

  await s.step('the save endpoint refuses GET', async () => {
    // From inside the page so the request carries the logged-in session: an
    // unauthenticated GET would be bounced to login and prove nothing about the
    // endpoint's own method handling. Re-open first -- the delete step above
    // navigates, which destroys the previous execution context.
    page = await openTracker(s, 'health-tracker-get');
    const before = measurementRows(s);
    const status = await page.evaluate(async ({ field, value, patientId, endpoint }) => {
      const query = new URLSearchParams({
        demographic_no: patientId,
        template: 'tracker',
        [field]: value,
        submit: 'Save All',
      });
      const response = await fetch(`${endpoint}?${query.toString()}`,
        { method: 'GET', credentials: 'same-origin', redirect: 'manual' });
      return response.status;
    }, {
      field: FIELD,
      value: GOOD_VALUE,
      patientId: String(patient),
      endpoint: appUrl(s.config.baseUrl, '/encounter/oscarMeasurements/HealthTrackerUpdate'),
    });

    assert(status === 405,
      `A GET to the Health Tracker save endpoint answered ${status}, not 405;`
      + ' the endpoint must not be reachable from a link or an image tag');
    assert(measurementRows(s) === before,
      'A GET to the save endpoint wrote a measurement');

    // The 405 just asserted is also, to the page recorder, a failed request and a
    // console error. Drop those two entries -- and only those, matched on the
    // status and the URL this step drove -- so the strict-page assertion still
    // covers everything else the run touched.
    const probed = (entry) => entry && typeof entry.url === 'string'
      && entry.url.includes('/encounter/oscarMeasurements/HealthTrackerUpdate');
    s.recorder.badResponses = s.recorder.badResponses.filter(
      (entry) => !(probed(entry) && entry.status === 405));
    s.recorder.consoleIssues = s.recorder.consoleIssues.filter(
      (entry) => !(entry && typeof entry.text === 'string' && entry.text.includes('405')
        && probed(entry.location)));
  });
}

if (require.main === module) runWorkflow('health-tracker', workflow);
module.exports = { workflow };
