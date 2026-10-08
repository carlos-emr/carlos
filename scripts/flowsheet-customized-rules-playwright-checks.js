#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #4433: a customized flowsheet keeps its flowsheet-level decision support and its header.
//
// User path: Master Record ▸ E-Chart ▸ Measurements ▸ CDM Indicators ▸ Recommendations; the flowsheet
// editor in Patient scope (the editor's "Patient" link opens
// adminFlowsheet/ViewEditFlowsheet?flowsheet=…&demographic=…) ▸ hide one item, and ▸ pencil (Update
// Flowsheet) ▸ warning rule ▸ Update; reopen the flowsheet after each. The same hide for the INR
// Flowsheet, opened by its ViewTemplateFlowSheet URL because it is diagnosis-triggered and the owned
// patient carries no diagnosis.
// Any patient or provider customization makes the server evaluate an exported-and-reparsed copy of
// the flowsheet. Before #4433 that copy dropped the root ds_rules attribute, so diab.drl and
// inrFlowsheet.drl never ran and the Recommendations box disappeared; a warning rule added to a
// flowsheet whose only rules are its DRL file replaced that file's rules; and the copy re-read the
// rendered header HTML as a file name, so the INR dosing table disappeared. Each step records what the
// uncustomized flowsheet shows this patient and asserts the customized one shows the same rule output
// (plus the added rule) and header. The hidden items are ones the DRL files never fire on for this
// patient, so the comparison does not depend on whether hidden items feed the rules.
// Fixtures: the owned FAKE-PW patient (runWorkflow) with one waist reading (the added rule's target;
// diab.drl ignores waist) and one INR reading five months old (inrFlowsheet.drl only warns about a
// stale INR; diab.drl warns about never-recorded items). Cleanup deletes this patient's customization
// rows and readings and asserts both.
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

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const warning = `${marker} waist above 100`;
  const owned = `demographic_no=${h.sqlString(String(patient))}`;
  const customization = (flowsheet, action, measurement) => `SELECT COUNT(*) FROM flowsheet_customization
    WHERE flowsheet=${h.sqlString(flowsheet)} AND ${owned} AND provider_no=${h.sqlString(provider)}
    AND action=${h.sqlString(action)} AND measurement=${h.sqlString(measurement)} AND archived='0'`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM flowsheet_customization WHERE ${owned};
      DELETE FROM measurements WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE ${owned}`) === '0',
      'Owned flowsheet customization rows were not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`) === '0',
      'Owned readings were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE ${owned}`) === '0',
    'The owned patient already has flowsheet customizations');
  const seed = (type, value, observed) => {
    const id = sql.value(`INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,
      comments,dateObserved,dateEntered) VALUES(${h.sqlString(type)},${patient},${h.sqlString(provider)},
      ${h.sqlString(value)},'',${h.sqlString(marker)},${observed},NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `The seeded ${type} reading was not created`);
  };
  seed('WAIS', '110', 'DATE_SUB(NOW(), INTERVAL 1 DAY)');
  seed('INR', '2.4', 'DATE_SUB(NOW(), INTERVAL 5 MONTH)');

  const item = (page, measurement) => page.locator(`div.preventionSection[id="${measurement}"]`);
  // The editor's Patient-scope view of one flowsheet, as editFlowsheetByDemographic opens it.
  async function openEditor(page, flowsheet) {
    await h.gotoApp(page, s.config.baseUrl, `/encounter/oscarMeasurements/adminFlowsheet/ViewEditFlowsheet`
      + `?flowsheet=${encodeURIComponent(flowsheet)}&demographic=${encodeURIComponent(patient)}`);
    await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await h.assertNotErrorPage(page, `${flowsheet} patient flowsheet editor`);
  }
  async function editorRow(page, flowsheet, measurement) {
    const row = page.locator('#measurementTbl tbody tr')
      .filter({ has: page.locator('td:nth-child(3)', { hasText: new RegExp(`^\\s*${measurement}\\s*$`) }) });
    h.assert(await row.count() === 1, `The ${flowsheet} editor does not list exactly one ${measurement} row`);
    return row;
  }
  async function hideForPatient(page, flowsheet, measurement) {
    await openEditor(page, flowsheet);
    await navigate(page, (await editorRow(page, flowsheet, measurement)).locator('a[title="Hide this measurement"]'));
    await expectValue(sql, customization(flowsheet, 'delete', measurement), '1',
      `Hiding ${measurement} did not store one patient-scoped delete customization for ${flowsheet}`);
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

    await hideForPatient(page, 'diab3', 'DTYP');
    await page.close();
    page = await openCdm('cdm-indicators-hidden');
    h.assert(!await item(page, 'DTYP').isVisible(), 'The patient-scoped hide did not remove DTYP from the flowsheet');
    const customized = await ruleOutput(page);
    h.assert(customized.length > 0,
      'The customized CDM Indicators flowsheet lost every diab.drl recommendation (its ds_rules were not copied)');
    h.assert(JSON.stringify(customized) === JSON.stringify(expected),
      `The customized CDM Indicators flowsheet shows different diab.drl output: ${JSON.stringify(customized)}`);
    await page.close();
  });

  await s.step('a warning rule added through Update Flowsheet runs alongside the diab.drl recommendations', async () => {
    const page = await s.context.newPage();
    await openEditor(page, 'diab3');
    await navigate(page, (await editorRow(page, 'diab3', 'WAIS')).locator('a[title="Edit"]'));
    h.assert(/\/ViewUpdateFlowsheet$/.test(new URL(page.url()).pathname), 'The pencil did not open Update Flowsheet');
    await page.locator('select[name="strength1"]').selectOption('warning');
    await page.locator('input[name="text1"]').fill(warning);
    await page.locator('select[name="type1c1"]').selectOption('lastValueAsInt');
    await page.locator('input[name="value1c1"]').fill('>100');
    await navigate(page, page.locator('input[type="submit"][value="Update"]'));
    await expectValue(sql, customization('diab3', 'update', 'WAIS'), '1',
      'Update Flowsheet did not store one patient-scoped update customization for WAIS');
    await page.close();

    const flowsheet = await openCdm('cdm-indicators-rule');
    const customized = await ruleOutput(flowsheet);
    h.assert(customized.includes(warning), 'The warning rule added through Update Flowsheet did not fire');
    h.assert(JSON.stringify(customized) === JSON.stringify([...expected, warning].sort()),
      `Adding a warning rule replaced the diab.drl recommendations: ${JSON.stringify(customized)}`);
    await flowsheet.close();
  });

  await s.step('a patient-scoped hide keeps the INR header table and inrFlowsheet.drl warning', async () => {
    const flowsheetPath = `/encounter/oscarMeasurements/ViewTemplateFlowSheet?demographic_no=${encodeURIComponent(patient)}&template=inrFlow`;
    const page = await s.context.newPage();
    await h.gotoApp(page, s.config.baseUrl, flowsheetPath);
    await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await h.assertNotErrorPage(page, 'uncustomized INR Flowsheet');
    const header = page.getByText('Tablet Strengths:', { exact: true });
    h.assert(await header.count() === 1, 'The uncustomized INR Flowsheet does not render its inr.html header');
    const inrExpected = await ruleOutput(page);
    h.assert(inrExpected.some(line => /^INR hasn't been reviewed in \d+ months$/.test(line)),
      'The uncustomized INR Flowsheet does not warn about the five-month-old INR reading');
    h.assert(await item(page, 'COUM').isVisible(), 'The uncustomized INR Flowsheet does not show COUM');

    // inrFlowsheet.drl only fires on a COUM reading, and this patient has none.
    await hideForPatient(page, 'inrFlow', 'COUM');
    await h.gotoApp(page, s.config.baseUrl, flowsheetPath);
    await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await h.assertNotErrorPage(page, 'customized INR Flowsheet');
    h.assert(!await item(page, 'COUM').isVisible(), 'The patient-scoped hide did not remove COUM from the flowsheet');
    h.assert(await header.count() === 1,
      'The customized INR Flowsheet lost its inr.html header (top_HTML was not copied as a file name)');
    const customized = await ruleOutput(page);
    h.assert(JSON.stringify(customized) === JSON.stringify(inrExpected),
      `The customized INR Flowsheet shows different inrFlowsheet.drl output: ${JSON.stringify(customized)}`);
    await page.close();
  });
}

if (require.main === module) runWorkflow('flowsheet-customized-rules', workflow);
module.exports = { workflow };
