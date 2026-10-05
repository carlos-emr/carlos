#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Rourke 2017: real measurement Save, import without duplicates, date shortcut, form Save
 * and redisplay, using an owned synthetic patient. GET/HEAD rejection remains independently
 * covered by get-reject-rourke-form. All owned measurements and form versions are cleaned up. */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest } = require('./lib/get-reject-probe');

async function workflow(s) {
  const { sql, patient } = s;
  const q = h.sqlString;
  const value = '3.75';
  const observed = '2026-01-08';
  const measurementRows = `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient}; DELETE FROM formRourke2017 WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (${measurementRows}) + (SELECT COUNT(*) FROM formRourke2017 WHERE demographic_no=${patient})`) === '0',
      'Owned measurements and form versions were not removed');
  });
  let form;
  let date;
  const weightLink = () => form.locator(`a[onclick*="displayDemographicMeasurements('p1_wt1w'"]`).first();
  await s.step('E-Chart Forms opens Rourke 2017 for the owned patient', async () => {
    const chart = await s.chart();
    await chart.locator('#menuTitle1 a').hover();
    form = await s.popup(chart, chart.getByRole('link', {name:'Rourke2017', exact:true}), 'rourke');
    await form.locator('#frmP1').waitFor();
    h.assert(new URL(form.url()).searchParams.get('demographic_no') === patient, 'Rourke opened another patient');
  });
  await s.step('measurement Save POSTs the exact value, instruction and observation date, then updates the form', async () => {
    await weightLink().click();
    await form.locator('#currentMeasurementValue').fill(value);
    await form.locator('#currentMeasurementObservationDate').fill(observed);
    const request = await captureRequest(form, url => url.pathname.endsWith('/encounter/MeasurementData')
      && url.searchParams.get('action') === 'saveMeasurement', () => form.locator('.meas-btn-save').click(), {timeout:10000});
    h.assert(request.status === 200 && request.params.get('demographicNo') === patient
      && request.params.get('type') === 'WT' && request.params.get('value') === value
      && request.params.get('dateObserved') === observed && request.params.get('instruction') === 'in kg',
    'Measurement Save did not send the expected patient, type, value, instructions and date');
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}
      AND type='WT' AND dataField=${q(value)} AND measuringInstruction='in kg' AND DATE(dateObserved)=${q(observed)}`,
    '1', 'Measurement Save did not persist the exact clinical values');
    h.assert(await form.locator('#p1_wt1w').inputValue() === value, 'Save did not import the measurement into Rourke');
    h.assert(await form.locator('.meas-dialog-overlay').count() === 0, 'Save left the dialog open');
  });
  await s.step('Okay and selecting an existing measurement import values without duplicate records', async () => {
    const count = sql.value(measurementRows);
    await weightLink().click();
    await form.locator('#currentMeasurementValue').fill('3.8');
    await form.locator('.meas-btn-cancel').click();
    h.assert(await form.locator('#p1_wt1w').inputValue() === '3.8', 'Okay did not import the entered value');
    h.assert(sql.value(measurementRows) === count, 'Okay created a measurement');
    await weightLink().click();
    await form.locator('.meas-dialog-body a').filter({hasText:`${value} in kg (${observed}`}).click();
    await form.locator('.meas-btn-save').click();
    h.assert(await form.locator('#p1_wt1w').inputValue() === value, 'The selected measurement was not imported');
    h.assert(sql.value(measurementRows) === count, 'Importing an existing measurement created a duplicate');
  });
  await s.step('double-click stamps and clears the visit date, survives blur, and retains the calendar button', async () => {
    const field = form.locator('#p1_date1w');
    h.assert(await field.inputValue() === '', 'The new visit already has a date');
    const today = await form.evaluate(() => {const d = new Date(); return `${String(d.getDate()).padStart(2,'0')}/${String(d.getMonth()+1).padStart(2,'0')}/${d.getFullYear()}`;});
    await field.dblclick();
    h.assert(await field.inputValue() === today, 'Double-click did not stamp today');
    await form.locator('#p1_wt1w').click();
    h.assert(await field.inputValue() === today, 'The stamped date was lost on blur');
    await field.dblclick();
    h.assert(await field.inputValue() === '', 'Double-click did not clear the stamped date');
    await form.locator('#p1_date1w_cal').click();
    const selected = form.locator('.flatpickr-calendar.open .flatpickr-day.today');
    await selected.click();
    h.assert(await field.inputValue() === today, 'The calendar button did not select today');
    date = today;
  });
  await s.step('form Save persists the date and imported weight and redisplays them', async () => {
    let request;
    await h.withExpectedDialogs(form, async () => {
      request = await captureRequest(form, url => url.pathname.endsWith('/form/formname') && url.searchParams.get('submit') === 'save',
        () => form.locator('#frmP1 input[value="Save"]').first().click());
    });
    h.assert(request.status === 200 && request.params.get('demographic_no') === patient, 'Form Save failed or posted another patient');
    const isoDate = date.split('/').reverse().join('-');
    await expectValue(sql, `SELECT COUNT(*)>0 FROM formRourke2017 WHERE demographic_no=${patient}
      AND p1_wt1w=${q(value)} AND p1_date1w=${q(isoDate)}`, '1', 'Form Save lost the visit date or weight');
    await form.waitForLoadState('domcontentloaded');
    h.assert(await form.locator('#p1_wt1w').inputValue() === value, 'Redisplay lost the weight');
    const displayed = await form.locator('#p1_date1w').inputValue();
    h.assert(displayed === date || displayed === isoDate, 'Redisplay lost the visit date');
  });
}
if (require.main === module) runWorkflow('rourke-measurement', workflow, {openPatient:true});
module.exports = {workflow};
