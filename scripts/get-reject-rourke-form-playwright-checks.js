#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, encounter forms. User path: Schedule ▸ Search ▸ Master Record ▸
 * E-Chart ▸ Forms menu ▸ Rourke2017 ▸ (a) the 1-week "Wt" link ▸ measurement dialog ▸
 * Save, and (b) the form's own Save (confirm).
 *
 * (a) POSTs encounter/MeasurementData?action=saveMeasurement, which persists a clinical
 * measurement for the posted demographicNo. The action dispatches on `action=`, not
 * `method=`, so HttpMethodGuardFilter never inspects it, and it has no POST check.
 * (b) POSTs form/formname?submit=save (Frm2Action), which writes a new form record
 * from whatever parameters arrive, with only _form w; "formname" has no mutator prefix
 * and the action has no POST check. Each positive save is driven by the page and
 * asserted in the database; the captured request is then replayed as GET/HEAD with a
 * value no row of this patient carries, and the replay must answer 405 and write
 * nothing. Both probes are asserted together in the LAST step.
 *
 * Fixtures: the owned synthetic patient; cleanup deletes its measurements and
 * formRourke2017 rows (the form also autosaves) and asserts they are gone.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger } = require('./lib/get-reject-probe');

const NAME = 'get-reject-rourke-form';

async function workflow(s) {
  const { sql, patient } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  s.cleanup(() => {
    // A save also writes the form's ticked boxes to form_boolean_value, keyed by the record's id (434 rows per save), so
    // those go first, while the records that name them still exist.
    sql.execute(`DELETE FROM form_boolean_value WHERE form_name='formRourke2017'
        AND form_id IN (SELECT ID FROM formRourke2017 WHERE demographic_no=${patient});
      DELETE FROM measurements WHERE demographicNo=${patient}; DELETE FROM formRourke2017 WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM formRourke2017 WHERE demographic_no=${patient})`) === '0', 'Owned measurement/form rows were not removed');
  });
  const unique = () => `${3 + Math.floor(Math.random() * 6)}.${String(Date.now()).slice(-3)}`;
  const uiWeight = unique();
  const measured = v => `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient} AND type='WT' AND dataField=${q(v)}`;
  const formRows = v => `SELECT COUNT(*) FROM formRourke2017 WHERE demographic_no=${patient} AND p1_wt1w=${q(v)}`;
  let form;
  let measurement;
  let saved;

  await s.step('E-Chart ▸ Forms ▸ Rourke2017 opens the form for the owned patient', async () => {
    const chart = await s.chart();
    await chart.locator('#menuTitle1 a').hover();
    const link = chart.getByRole('link', { name: 'Rourke2017', exact: true });
    h.assert(await link.count() === 1, 'The Forms menu does not list Rourke2017 exactly once');
    form = await s.popup(chart, link, 'rourke2017');
    await form.locator('#frmP1').waitFor({ state: 'attached', timeout: 30000 });
    h.assert(new URL(form.url()).searchParams.get('demographic_no') === patient, 'The form opened for another patient');
  });

  // The dialog runs on the freshly opened form (the redisplay after a Save has no CSRF token,
  // finding L160). A dialog failure is recorded, the browser signals it raised are moved out
  // of the strict recorder INTO the final assertion (nothing is dropped), so it cannot hide
  // the form-save probe, and the final step reports both.
  let dialogDefect = null;
  await s.step('the 1-week Wt measurement dialog is exercised (outcome asserted in the final step)', async () => {
    const since = { page: s.recorder.pageErrors.length, responses: s.recorder.badResponses.length,
      console: s.recorder.consoleIssues.length };
    try {
      await form.locator(`a[onclick*="displayDemographicMeasurements('p1_wt1w'"]`).first().click();
      await form.locator('#currentMeasurementValue').fill(uiWeight);
      measurement = await captureRequest(form, url => url.pathname.endsWith('/encounter/MeasurementData')
        && url.searchParams.get('action') === 'saveMeasurement', () => form.locator('button.meas-btn-save').click(),
      { timeout: 10000 });
      h.assert(measurement.status === 200, `saveMeasurement answered ${measurement.status}`);
      await expectValue(sql, measured(uiWeight), '1', 'The dialog Save did not store the WT measurement');
    } catch (error) {
      const thrown = [...s.recorder.pageErrors.splice(since.page).map(e => e.text.split('\n')[0]),
        ...s.recorder.badResponses.splice(since.responses).map(e => `HTTP ${e.status} ${e.method} ${new URL(e.url).pathname}`)];
      s.recorder.consoleIssues.splice(since.console);
      dialogDefect = `measurement dialog Save sent no encounter/MeasurementData request${thrown.length
        ? ` (page error: ${thrown.join(' | ')})` : ''} -- ${error.message.split('\n')[0]}`;
      console.log(`  observed ${NAME}: ${dialogDefect}`);
      return;
    }
    const probeValue = unique();
    await ledger.probe(s, { label: 'encounter/MeasurementData?action=saveMeasurement', path: measurement.path,
      params: replayParams(measurement.params, { value: probeValue }), snapshot: () => sql.value(measured(probeValue)) });
  });

  await s.step('the form Save stores a formRourke2017 row with the typed 1-week weight (POST form/formname?submit=save)', async () => {
    // The visit date is read-only; a double-click stamps today (resetDate), as a clinician does.
    await form.locator('#p1_date1w').dblclick();
    h.assert(await form.locator('#p1_date1w').inputValue(), 'Double-clicking the 1-week date did not stamp a date');
    await form.locator('input[name="p1_wt1w"]').fill(uiWeight);
    await h.withExpectedDialogs(form, async () => {
      saved = await captureRequest(form, url => url.pathname.endsWith('/form/formname') && url.searchParams.get('submit') === 'save',
        () => form.locator('#frmP1 input[type="submit"][value="Save"], #frmP1 input[type="button"][value="Save"]').first().click());
    });
    await form.waitForLoadState('domcontentloaded');
    await expectValue(sql, `SELECT (${formRows(uiWeight)})>0`, '1', 'The form Save did not store the 1-week weight');
    h.assert(saved.params.get('form_class') && saved.params.get('demographic_no') === patient,
      'The form Save did not post form_class and the owned demographic_no');
  });

  await s.step('the form save replayed as GET/HEAD with a new weight is recorded', async () => {
    const probeValue = unique();
    // Only the record identity and one field: the page posts hundreds of empty inputs.
    const keep = ['form_class', 'demographic_no', 'formId', 'submit', 'provNo'];
    const minimal = new URLSearchParams([...saved.params].filter(([k]) => keep.includes(k)));
    await ledger.probe(s, { label: 'form/formname?submit=save (Frm2Action)', path: saved.path,
      params: replayParams(minimal, { p1_wt1w: probeValue }), snapshot: () => sql.value(formRows(probeValue)) });
  });

  await s.step('every encounter-form write refused GET/HEAD, stored nothing replayed, and the measurement dialog saved', async () => {
    const problems = [];
    try { ledger.assertAllRefused(); } catch (error) { problems.push(error.message); }
    if (dialogDefect) problems.push(dialogDefect);
    h.assert(!problems.length, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
