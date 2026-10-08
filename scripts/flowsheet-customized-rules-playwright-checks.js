#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #4433: a customized flowsheet keeps its flowsheet-level decision support and its header.
//
// User path: Master Record ▸ E-Chart ▸ Measurements ▸ CDM Indicators ▸ Recommendations; the flowsheet
// editor in Patient scope (the editor's "Patient" link opens
// adminFlowsheet/ViewEditFlowsheet?flowsheet=…&demographic=…) ▸ hide one item, ▸ pencil (Update
// Flowsheet) ▸ warning rule ▸ Update, and ▸ Add ▸ measurement with a month-range rule ▸ Save; reopen the
// flowsheet after each, then its Custom Print. The editor in "Your Patients" scope (no demographic)
// ▸ hide one CKD item. Patient-scope hides on the Diabetes (diab2) and INR flowsheets. Diagnosis-triggered
// flowsheets are opened by their ViewTemplateFlowSheet URL because the owned patient carries no diagnosis.
// Any patient or provider customization makes the server evaluate an exported-and-reparsed copy of
// the flowsheet. Before #4433 that copy dropped the root ds_rules attribute, so diab.drl, ckd.drl and
// inrFlowsheet.drl never ran and the Recommendations box disappeared; a rule added to a flowsheet
// whose only rules are its DRL file replaced that file's rules; and the copy re-read the rendered
// header HTML as a file name, so the INR dosing table disappeared. Each step records what the
// uncustomized flowsheet shows this patient and asserts the customized one shows the same rule output
// (plus the added rules) and header. Diabetes (diab2) runs its own item recommendations instead of
// diab.drl; its step guards that a customization does not start running diab.drl there, and that its
// prevention items keep their reminders. Hiding an item hides its column, not its decision support:
// one step hides ACR, which ckd.drl reads, after recording an ACR, and asserts the flowsheet and its
// Custom Print do not report the hidden ACR as never recorded (the pages once loaded readings only
// for visible items).
// Fixtures: the owned FAKE-PW patient (runWorkflow) with one waist reading (the Update rule's target;
// diab.drl ignores waist), one INR reading five months old (inrFlowsheet.drl only warns about a
// stale INR; diab.drl and ckd.drl warn about never-recorded items) and, from the ACR step on, one
// ACR reading a day old. The "Your Patients" hide is a provider-wide customization of the test
// login's CKD flowsheet; the check refuses to run if that login already has one, and removes its own,
// so the check must not run concurrently with another that customizes that login's CKD flowsheet.
// Cleanup deletes this patient's customization rows and readings and the provider-scope row, and
// asserts all three.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function navigate(page, locator) {
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 20000 }),
    locator.click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  await h.assertNotErrorPage(page, new URL(page.url()).pathname);
}

/** The Recommendations box: the flowsheet's rule output (warnings, then recommendations), sorted. */
async function ruleOutput(page) {
  return (await page.locator('#recomList li').allTextContents()).map(text => text.trim()).filter(Boolean).sort();
}

const same = (actual, expected) => JSON.stringify(actual) === JSON.stringify([...expected].sort());

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const updateWarning = `${marker} waist above 100`;
  const addWarning = `${marker} heart rate never recorded`;
  const owned = `demographic_no=${h.sqlString(String(patient))}`;
  const providerWide = `flowsheet='ckd' AND provider_no=${h.sqlString(provider)} AND demographic_no='0'`;
  const customization = (flowsheet, action, measurement) => `SELECT COUNT(*) FROM flowsheet_customization
    WHERE flowsheet=${h.sqlString(flowsheet)} AND ${owned} AND provider_no=${h.sqlString(provider)}
    AND action=${h.sqlString(action)} AND measurement=${h.sqlString(measurement)} AND archived='0'`;
  h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE ${owned}`) === '0',
    'The owned patient already has flowsheet customizations');
  h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE ${providerWide}`) === '0',
    'The test login already customizes its CKD flowsheet for all its patients; refusing to change it');
  s.cleanup(() => {
    sql.execute(`DELETE FROM flowsheet_customization WHERE ${owned};
      DELETE FROM flowsheet_customization WHERE ${providerWide} AND action='delete' AND measurement='AORA';
      DELETE FROM measurements WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE ${owned}`) === '0',
      'Owned flowsheet customization rows were not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE ${providerWide}`) === '0',
      'The provider-scope CKD customization was not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`) === '0',
      'Owned readings were not removed');
  });
  const seed = (type, value, observed) => {
    const id = sql.value(`INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,
      comments,dateObserved,dateEntered) VALUES(${h.sqlString(type)},${patient},${h.sqlString(provider)},
      ${h.sqlString(value)},'',${h.sqlString(marker)},${observed},NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `The seeded ${type} reading was not created`);
  };
  seed('WAIS', '110', 'DATE_SUB(NOW(), INTERVAL 1 DAY)');
  seed('INR', '2.4', 'DATE_SUB(NOW(), INTERVAL 5 MONTH)');

  const item = (page, measurement) => page.locator(`div.preventionSection[id="${measurement}"]`);
  async function openPage(page, path, label) {
    await h.gotoApp(page, s.config.baseUrl, path);
    await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await h.assertNotErrorPage(page, label);
  }
  const flowsheetPath = (template) => `/encounter/oscarMeasurements/ViewTemplateFlowSheet`
    + `?demographic_no=${encodeURIComponent(patient)}&template=${encodeURIComponent(template)}`;
  // The editor's Patient-scope view (as editFlowsheetByDemographic opens it), or with no patient the
  // "Your Patients" view, which customizes the flowsheet for every patient of the signed-in provider.
  async function openEditor(page, flowsheet, { forPatient = true } = {}) {
    const demographic = forPatient ? `&demographic=${encodeURIComponent(patient)}` : '';
    await openPage(page, `/encounter/oscarMeasurements/adminFlowsheet/ViewEditFlowsheet`
      + `?flowsheet=${encodeURIComponent(flowsheet)}${demographic}`, `${flowsheet} flowsheet editor`);
  }
  async function editorRow(page, flowsheet, measurement) {
    const row = page.locator('#measurementTbl tbody tr')
      .filter({ has: page.locator('td:nth-child(3)').getByText(measurement, { exact: true }) });
    h.assert(await row.count() === 1, `The ${flowsheet} editor does not list exactly one ${measurement} row`);
    return row;
  }
  async function hide(page, flowsheet, measurement, options) {
    await openEditor(page, flowsheet, options);
    await navigate(page, (await editorRow(page, flowsheet, measurement)).locator('a[title="Hide this measurement"]'));
  }

  const chart = await s.chart();
  const cdmLink = chart.locator('a[onclick*="ViewTemplateFlowSheet"][onclick*="template=diab3\'"]').first();
  const openCdm = async (label) => {
    await cdmLink.waitFor({ state: 'visible' });
    const page = await s.popup(chart, cdmLink, label);
    await page.waitForLoadState('networkidle').catch(() => {});
    await h.assertNotErrorPage(page, label);
    return page;
  };
  let expected;

  await s.step('a patient-scoped hide keeps the CDM Indicators diab.drl recommendations', async () => {
    let page = await openCdm('cdm-indicators');
    expected = await ruleOutput(page);
    h.assert(expected.includes('no A1C values has been recorded') && expected.includes('no BP has been recorded'),
      'The uncustomized CDM Indicators flowsheet does not show the diab.drl warnings for a patient with no readings');
    h.assert(await item(page, 'DTYP').isVisible(), 'The uncustomized CDM Indicators flowsheet does not show DTYP');

    await hide(page, 'diab3', 'DTYP');
    await expectValue(sql, customization('diab3', 'delete', 'DTYP'), '1',
      'Hiding DTYP did not store one patient-scoped delete customization for diab3');
    await page.close();
    page = await openCdm('cdm-indicators-hidden');
    h.assert(!await item(page, 'DTYP').isVisible(), 'The patient-scoped hide did not remove DTYP from the flowsheet');
    const customized = await ruleOutput(page);
    h.assert(customized.length > 0,
      'The customized CDM Indicators flowsheet lost every diab.drl recommendation (its ds_rules were not copied)');
    h.assert(same(customized, expected),
      `The customized CDM Indicators flowsheet shows different diab.drl output: ${JSON.stringify(customized)}`);
    await page.close();
  });

  await s.step('a warning rule added through Update Flowsheet runs alongside the diab.drl recommendations', async () => {
    const page = await s.context.newPage();
    await openEditor(page, 'diab3');
    await navigate(page, (await editorRow(page, 'diab3', 'WAIS')).locator('a[title="Edit"]'));
    h.assert(/\/ViewUpdateFlowsheet$/.test(new URL(page.url()).pathname), 'The pencil did not open Update Flowsheet');
    await page.locator('select[name="strength1"]').selectOption('warning');
    await page.locator('input[name="text1"]').fill(updateWarning);
    await page.locator('select[name="type1c1"]').selectOption('lastValueAsInt');
    await page.locator('input[name="value1c1"]').fill('>100');
    await navigate(page, page.locator('input[type="submit"][value="Update"]'));
    await expectValue(sql, customization('diab3', 'update', 'WAIS'), '1',
      'Update Flowsheet did not store one patient-scoped update customization for WAIS');
    await page.close();

    const flowsheet = await openCdm('cdm-indicators-update-rule');
    const customized = await ruleOutput(flowsheet);
    h.assert(customized.includes(updateWarning), 'The warning rule added through Update Flowsheet did not fire');
    h.assert(same(customized, [...expected, updateWarning]),
      `Adding a warning rule replaced the diab.drl recommendations: ${JSON.stringify(customized)}`);
    await flowsheet.close();
  });

  await s.step('a measurement added with a rule through the editor runs alongside the diab.drl recommendations', async () => {
    const page = await s.context.newPage();
    await openEditor(page, 'diab3');
    await page.locator('#myTab a[href="#add"]').click();
    await page.locator('#add select[name="measurement"]').selectOption('HR');
    await page.locator('#display_name').fill(`${marker} Heart rate`);
    await page.locator('#add input[name="guideline"]').fill(marker);
    await page.locator('#add input[name="monthrange1"]').fill('-1');
    await page.locator('#add select[name="strength1"]').selectOption('warning');
    await page.locator('#add input[name="text1"]').fill(addWarning);
    await navigate(page, page.locator('#FlowSheetCustomActionForm input[type="submit"][value="Save"]'));
    await expectValue(sql, `SELECT COUNT(*) FROM flowsheet_customization WHERE flowsheet='diab3' AND ${owned}
      AND provider_no=${h.sqlString(provider)} AND action='add' AND archived='0'
      AND payload LIKE ${h.sqlString('%measurement_type="HR"%')}`, '1',
    'The editor did not store one patient-scoped add customization for HR');
    await page.close();

    const flowsheet = await openCdm('cdm-indicators-add-rule');
    await item(flowsheet, 'HR').waitFor({ state: 'visible' });
    const customized = await ruleOutput(flowsheet);
    h.assert(customized.includes(addWarning), 'The rule saved with the added measurement did not fire');
    h.assert(same(customized, [...expected, updateWarning, addWarning]),
      `Adding a measurement with a rule replaced the diab.drl recommendations: ${JSON.stringify(customized)}`);
    await flowsheet.close();
  });

  await s.step('Custom Print of the customized CDM Indicators lists the same recommendations', async () => {
    const page = await s.context.newPage();
    await openPage(page, `/encounter/oscarMeasurements/ViewTemplateFlowSheetPrint`
      + `?demographic_no=${encodeURIComponent(patient)}&template=diab3`, 'customized CDM Indicators print');
    const printed = await ruleOutput(page);
    h.assert(same(printed, [...expected, updateWarning, addWarning]),
      `Custom Print of the customized CDM Indicators lists different recommendations: ${JSON.stringify(printed)}`);
    await page.close();
  });

  await s.step('a Your Patients hide keeps the CKD ckd.drl warnings for this patient', async () => {
    const page = await s.context.newPage();
    await openPage(page, flowsheetPath('ckd'), 'uncustomized CKD Flowsheet');
    const ckdExpected = await ruleOutput(page);
    h.assert(ckdExpected.includes('An Alb: creat ratio value has not been recorded')
      && ckdExpected.includes('An eGFR value has not been recorded'),
    'The uncustomized CKD Flowsheet does not show the ckd.drl warnings for a patient with no readings');
    h.assert(await item(page, 'AORA').isVisible(), 'The uncustomized CKD Flowsheet does not show AORA');

    // ckd.drl only fires on ACR and EGFR.
    await hide(page, 'ckd', 'AORA', { forPatient: false });
    await expectValue(sql, `SELECT COUNT(*) FROM flowsheet_customization WHERE ${providerWide}
      AND action='delete' AND measurement='AORA' AND archived='0'`, '1',
    'The Your Patients hide did not store one provider-scope delete customization for CKD');
    await openPage(page, flowsheetPath('ckd'), 'provider-customized CKD Flowsheet');
    h.assert(!await item(page, 'AORA').isVisible(), 'The Your Patients hide did not remove AORA from this patient\'s flowsheet');
    const customized = await ruleOutput(page);
    h.assert(same(customized, ckdExpected),
      `The provider-customized CKD Flowsheet shows different ckd.drl output: ${JSON.stringify(customized)}`);
    await page.close();
  });

  await s.step('a hidden item the rules read keeps its readings in the CKD ckd.drl output and Custom Print', async () => {
    seed('ACR', '2.0', 'DATE_SUB(NOW(), INTERVAL 1 DAY)');
    const page = await s.context.newPage();
    await openPage(page, flowsheetPath('ckd'), 'CKD Flowsheet with an ACR reading');
    const recorded = await ruleOutput(page);
    h.assert(recorded.includes('An eGFR value has not been recorded'),
      'The CKD Flowsheet does not show the ckd.drl eGFR warning for a patient with no eGFR');
    h.assert(!recorded.some(line => line.startsWith('An Alb: creat ratio value')),
      `The CKD Flowsheet warns about the ACR recorded yesterday: ${JSON.stringify(recorded)}`);
    h.assert(await item(page, 'ACR').isVisible(), 'The CKD Flowsheet does not show ACR');

    await hide(page, 'ckd', 'ACR');
    await expectValue(sql, customization('ckd', 'delete', 'ACR'), '1',
      'Hiding ACR did not store one patient-scoped delete customization for ckd');
    await openPage(page, flowsheetPath('ckd'), 'CKD Flowsheet with ACR hidden');
    h.assert(!await item(page, 'ACR').isVisible(), 'The patient-scoped hide did not remove ACR from the flowsheet');
    const customized = await ruleOutput(page);
    h.assert(!customized.includes('An Alb: creat ratio value has not been recorded'),
      'Hiding ACR made ckd.drl report the ACR recorded yesterday as never recorded');
    h.assert(same(customized, recorded),
      `Hiding ACR changed the CKD Flowsheet's ckd.drl output: ${JSON.stringify(customized)}`);

    await openPage(page, `/encounter/oscarMeasurements/ViewTemplateFlowSheetPrint`
      + `?demographic_no=${encodeURIComponent(patient)}&template=ckd`, 'CKD Custom Print with ACR hidden');
    const printed = await ruleOutput(page);
    h.assert(same(printed, recorded),
      `Custom Print of the CKD Flowsheet with ACR hidden lists different ckd.drl output: ${JSON.stringify(printed)}`);
    await page.close();
  });

  await s.step('a patient-scoped hide keeps Diabetes on its own item recommendations, without diab.drl', async () => {
    const page = await s.context.newPage();
    await openPage(page, flowsheetPath('diab2'), 'uncustomized Diabetes Flowsheet');
    const diabExpected = await ruleOutput(page);
    h.assert(diabExpected.length > 0, 'The uncustomized Diabetes Flowsheet shows no item recommendations');
    h.assert(!diabExpected.includes('no A1C values has been recorded'),
      'The uncustomized Diabetes Flowsheet runs diab.drl, which its item recommendations replace');
    const fluReminder = line => /^Flu\s+has never been reviewed$/.test(line);
    h.assert(diabExpected.some(fluReminder),
      'The uncustomized Diabetes Flowsheet does not show the reminder its Flu prevention item carries');
    h.assert(await item(page, 'UMS').isVisible(), 'The uncustomized Diabetes Flowsheet does not show UMS');

    await hide(page, 'diab2', 'UMS');
    await expectValue(sql, customization('diab2', 'delete', 'UMS'), '1',
      'Hiding UMS did not store one patient-scoped delete customization for diab2');
    await openPage(page, flowsheetPath('diab2'), 'customized Diabetes Flowsheet');
    h.assert(!await item(page, 'UMS').isVisible(), 'The patient-scoped hide did not remove UMS from the flowsheet');
    const customized = await ruleOutput(page);
    h.assert(customized.some(fluReminder),
      'The customized Diabetes Flowsheet lost its Flu prevention reminder (prevention item rules were not copied)');
    h.assert(same(customized, diabExpected),
      `The customized Diabetes Flowsheet shows different recommendations: ${JSON.stringify(customized)}`);
    await page.close();
  });

  await s.step('a patient-scoped hide keeps the INR header table and inrFlowsheet.drl warning', async () => {
    const page = await s.context.newPage();
    await openPage(page, flowsheetPath('inrFlow'), 'uncustomized INR Flowsheet');
    const header = page.getByText('Tablet Strengths:', { exact: true });
    h.assert(await header.count() === 1, 'The uncustomized INR Flowsheet does not render its inr.html header');
    const inrExpected = await ruleOutput(page);
    h.assert(inrExpected.some(line => /^INR hasn't been reviewed in \d+ months$/.test(line)),
      'The uncustomized INR Flowsheet does not warn about the five-month-old INR reading');
    h.assert(await item(page, 'COUM').isVisible(), 'The uncustomized INR Flowsheet does not show COUM');

    // inrFlowsheet.drl only fires on a COUM reading, and this patient has none.
    await hide(page, 'inrFlow', 'COUM');
    await expectValue(sql, customization('inrFlow', 'delete', 'COUM'), '1',
      'Hiding COUM did not store one patient-scoped delete customization for inrFlow');
    await openPage(page, flowsheetPath('inrFlow'), 'customized INR Flowsheet');
    h.assert(!await item(page, 'COUM').isVisible(), 'The patient-scoped hide did not remove COUM from the flowsheet');
    h.assert(await header.count() === 1,
      'The customized INR Flowsheet lost its inr.html header (top_HTML was not copied as a file name)');
    const customized = await ruleOutput(page);
    h.assert(same(customized, inrExpected),
      `The customized INR Flowsheet shows different inrFlowsheet.drl output: ${JSON.stringify(customized)}`);
    await page.close();
  });
}

if (require.main === module) runWorkflow('flowsheet-customized-rules', workflow);
module.exports = { workflow };
