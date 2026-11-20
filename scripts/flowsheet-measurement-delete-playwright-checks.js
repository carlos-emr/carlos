#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Deleting a saved reading from the Diabetes Flowsheet (issue #4715).
 *
 * A saved reading opens in the "Add Measurement Data" window read-only, with Delete as its only
 * action. The page used to disable every control of its form, the hidden CSRF-TOKEN field
 * CSRFGuard had added included, and a disabled control is not submitted, so Delete was refused
 * with HTTP 403 ("Required Token is missing from the Request"): nothing was deleted, nothing was
 * archived, and the clinician saw the application's error page in the popup.
 *
 *   1. The flowsheet lists the saved reading; its link opens the window read-only (no Save, the
 *      data controls disabled), and the form would submit its CSRF token, the reading to delete
 *      and the chart's patient.
 *   2. Delete removes the reading, keeps a copy in measurementsDeleted, closes the window, and
 *      the flowsheet refreshed behind it no longer lists the reading.
 *   3. Control, so the fix is not mistaken for a weaker CSRF defence: the same Delete posted
 *      with the session but WITHOUT the token is still refused by the application and deletes
 *      nothing.
 *
 * The check owns a synthetic patient (runWorkflow) and removes its diagnosis and readings. The
 * diagnosis that makes the chart offer the flowsheet and the readings are set-up, not the
 * behaviour under test; saving a reading from the flowsheet is covered by diagnosis-flowsheet.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TEMPLATE = 'diab2';
const MEASUREMENT = 'NRTF';
const DELETE_ROUTE = '/encounter/oscarMeasurements/DeleteData2';

async function openFlowsheet(session, chart) {
  const link = chart.locator(`a[onclick*="ViewTemplateFlowSheet"][onclick*="template=${TEMPLATE}'"]`).first();
  await link.waitFor({ state: 'visible' });
  return session.popup(chart, link, 'diabetes-flowsheet');
}

/** The flowsheet's cell for one saved reading; its onclick carries &id=<measurement id>. */
function readingCell(flowsheet, id) {
  return flowsheet.locator(`.preventionProcedure[onclick*="measurement=${MEASUREMENT}"][onclick*="&id=${id}&"]`);
}

// close.jsp refreshes the chart behind the flowsheet (the flowsheet's refreshEncounter) as well as the
// flowsheet itself. The chart releases its note lock with a sendBeacon on pagehide, and Chromium reports
// that beacon, cut off by the reload, as an aborted "ping". Consume only that entry, only from this
// reload (as patient-photo-upload does); every other signal stays strict.
function consumeUnloadBeacon(recorder, since) {
  const added = recorder.requestFailures.splice(since);
  recorder.requestFailures.push(...added.filter((entry) => !(entry.resourceType === 'ping'
    && entry.errorText === 'net::ERR_ABORTED' && new URL(entry.url).pathname.endsWith('/CaseManagementEntry'))));
}

function insertReading(sql, { patient, provider }) {
  const id = sql.value(`INSERT INTO measurements (type, demographicNo, providerNo, dataField, measuringInstruction,
    comments, dateObserved, dateEntered) VALUES (${h.sqlString(MEASUREMENT)}, ${patient}, ${h.sqlString(provider)},
    'Yes', 'Yes/No/NA', '', NOW(), NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), 'The saved-reading fixture was not created');
  return id;
}

async function workflow(session) {
  const { sql, patient, provider } = session;
  // The chart offers the Diabetes Flowsheet only for a patient with a triggering diagnosis.
  sql.execute(`INSERT INTO dxresearch (demographic_no, start_date, update_date, status, dxresearch_code, coding_system, providerNo)
    VALUES (${patient}, CURDATE(), NOW(), 'A', '250', 'icd9', ${h.sqlString(provider)})`);
  // The archived copy a Delete writes to measurementsDeleted needs no cleanup here: runWorkflow's
  // owned-patient cleanup deletes this patient's measurementsDeleted rows and asserts none remain.
  session.cleanup(() => sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${patient};
    DELETE FROM measurements WHERE demographicNo=${patient}`));
  const id = insertReading(sql, { patient, provider });

  const chart = await session.chart();
  const flowsheet = await openFlowsheet(session, chart);
  let entry;

  await session.step('a saved reading opens read-only and would submit its CSRF token, the reading and the patient', async () => {
    const cell = readingCell(flowsheet, id).first();
    await cell.waitFor({ state: 'visible' });
    entry = await session.popup(flowsheet, cell, 'saved-reading');
    await h.assertNotErrorPage(entry, 'saved reading window');
    h.assert(await entry.getByRole('button', { name: 'Save', exact: true }).count() === 0,
      'A saved reading offers Save; it should open read-only');
    const remove = entry.getByRole('button', { name: 'Delete', exact: true });
    await remove.waitFor({ state: 'visible' });
    h.assert(await remove.isEnabled(), 'Delete is disabled on a saved reading');
    for (const selector of ['#prevDate0', '[name="inputValue-0"]', '[name="comments-0"]']) {
      h.assert(await entry.locator(selector).isDisabled(), `${selector} is editable on a saved reading`);
    }
    // What the browser would send for Delete. A disabled field is left out, which is how the
    // CSRF-TOKEN went missing (issue #4715).
    const submitted = await entry.locator('#measurementForm').evaluate((form) => {
      const data = new FormData(form);
      return { tokens: data.getAll('CSRF-TOKEN'), deletes: data.getAll('deleteCheckbox'), patients: data.getAll('demographicNo') };
    });
    h.assert(submitted.tokens.length === 1 && submitted.tokens[0].trim() !== '',
      `The saved-reading page would submit ${submitted.tokens.length} CSRF-TOKEN values (issue #4715: its token field is disabled), so Delete is refused with 403`);
    h.assert(submitted.deletes.length === 1 && submitted.deletes[0] === id,
      `The saved-reading page would delete ${JSON.stringify(submitted.deletes)}, not reading ${id}`);
    h.assert(submitted.patients.length === 1 && submitted.patients[0] === patient,
      `The saved-reading page would name patient ${JSON.stringify(submitted.patients)}, not ${patient}`);
  });

  await session.step('Delete removes the reading, archives a copy, closes the window and refreshes the flowsheet', async () => {
    const since = session.recorder.requestFailures.length;
    const closed = entry.waitForEvent('close');
    // Armed before the click: the reloads start as soon as the window's close page loads.
    const flowsheetReloaded = flowsheet.waitForEvent('load', { timeout: 20000 });
    const chartReloaded = chart.waitForEvent('load', { timeout: 20000 }).then(() => true, () => false);
    await entry.getByRole('button', { name: 'Delete', exact: true }).click({ noWaitAfter: true });
    await closed;
    await flowsheetReloaded;
    if (await chartReloaded) consumeUnloadBeacon(session.recorder, since);
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE id=${id}`, '0',
      'Delete did not remove the reading from measurements');
    await expectValue(sql, `SELECT COUNT(*) FROM measurementsDeleted WHERE originalId=${id}
      AND demographicNo=${patient} AND type=${h.sqlString(MEASUREMENT)} AND dataField='Yes'`, '1',
    'Delete did not keep a copy of the reading in measurementsDeleted');
    // close.jsp reloads the opener, so the reading drops out of the flowsheet behind the window.
    await readingCell(flowsheet, id).first().waitFor({ state: 'detached' });
    await h.assertNotErrorPage(flowsheet, 'Diabetes Flowsheet after Delete');
  });

  await session.step('a Delete without the CSRF token is still refused and removes nothing', async () => {
    const guarded = insertReading(sql, { patient, provider });
    const before = sql.value(`SELECT COUNT(*) FROM measurements WHERE id=${guarded}`);
    h.assert(before === '1', 'The control reading was not created');
    // The session cookie only: the request carries no CSRF-TOKEN header or parameter.
    const response = await session.context.request.post(h.appUrl(session.config.baseUrl, DELETE_ROUTE), {
      form: { deleteCheckbox: guarded, demographicNo: patient },
      maxRedirects: 0, failOnStatusCode: false,
    });
    try {
      await h.assertRefused(session, {
        response, table: 'measurements', where: `id=${guarded}`, before,
        label: 'a Delete posted without the CSRF token',
      });
    } finally { await response.dispose(); }
    await expectValue(sql, `SELECT COUNT(*) FROM measurementsDeleted WHERE originalId=${guarded}`, '0',
      'A refused Delete still archived the reading');
  });

  await flowsheet.close();
}

if (require.main === module) runWorkflow('flowsheet-measurement-delete', workflow);
module.exports = { workflow };
