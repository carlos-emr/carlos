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

const { assert, sqlString, gotoApp, assertNotErrorPage } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

// The display name drives the form field name: "Weight kg" -> "Weightkg".
// Deliberately one that contains a space, so a regression that forgot to strip
// non-word characters produces a field the action cannot read back.
const DISPLAY_NAME = 'Weight kg';
const FIELD = 'Weightkg';
const MEASUREMENT_TYPE = 'WT';
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
  const payload = `<item measurement_type="${MEASUREMENT_TYPE}" display_name="${DISPLAY_NAME}" `
    + 'guideline="" graphable="yes" value_name="Weight" />';
  sql.execute(`INSERT INTO flowsheet_customization
    (flowsheet, action, measurement, payload, provider_no, demographic_no, create_date, archived)
    VALUES ('tracker','add',${sqlString(MEASUREMENT_TYPE)},${sqlString(payload)},
      ${sqlString(provider)},${sqlString(String(patient))},NOW(),0)`);

  await s.step('a customized measurement renders as a tracker card with an entry field', async () => {
    page = await openTracker(s, 'health-tracker');
    assert(await page.locator(`#wrap-${MEASUREMENT_TYPE}`).count() === 1,
      `The Health Tracker did not render a card for the customized ${MEASUREMENT_TYPE} measurement`);
    assert((await page.locator(`#wrap-${MEASUREMENT_TYPE} .measurement-label`).first().innerText()).trim()
      === DISPLAY_NAME, 'The tracker card is not labelled with the flowsheet item display name');
    // The field name is the contract between the JSP and the save action.
    assert(await page.locator(`#trackerForm input[name="${FIELD}"]`).count() === 1,
      `The entry field is not named ${FIELD}; the save action derives that name from the display name`
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

  await s.step('a value the validation rule refuses is reported and writes nothing', async () => {
    await page.locator(`#trackerForm input[name="${FIELD}"]`).fill(BAD_VALUE);
    await Promise.all([
      page.waitForURL(/HealthTrackerUpdate|ViewHealthTracker/, { timeout: 30000 }),
      page.locator('button[form="trackerForm"][value="Save All"]').click(),
    ]);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(page, 'health-tracker after rejected save');

    const alert = page.locator('#validation-alert');
    assert(await alert.isVisible(),
      'A refused value produced no validation alert, so the clinician is told nothing was wrong');
    assert((await alert.innerText()).includes(DISPLAY_NAME),
      'The validation alert does not name the measurement that was refused');
    assert(measurementRows(s) === 1,
      'An invalid value reached the chart, where it is indistinguishable from a real observation');
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
    const response = await s.context.request.get(
      `${s.config.baseUrl}/encounter/oscarMeasurements/HealthTrackerUpdate`
      + `?demographic_no=${encodeURIComponent(patient)}&template=tracker&${FIELD}=${GOOD_VALUE}`,
      { maxRedirects: 0, failOnStatusCode: false });
    assert(response.status() === 405,
      `A GET to the Health Tracker save endpoint answered ${response.status()}, not 405;`
      + ' the endpoint must not be reachable from a link or an image tag');
    assert(measurementRows(s) === 0,
      'A GET to the save endpoint wrote a measurement');
  });
}

if (require.main === module) runWorkflow('health-tracker', workflow);
module.exports = { workflow };
