#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #4433: a customized flowsheet keeps its flowsheet-level decision support and its header.
//
// User path: Master Record ▸ E-Chart ▸ Measurements ▸ CDM Indicators ▸ Recommendations; the flowsheet
// editor in Patient scope (the editor's "Patient" link opens
// adminFlowsheet/ViewEditFlowsheet?flowsheet=…&demographic=…) ▸ hide one item; reopen the flowsheet.
// The same for the INR Flowsheet, opened by its ViewTemplateFlowSheet URL because it is
// diagnosis-triggered and the owned patient carries no diagnosis.
// Any patient or provider customization makes the server evaluate an exported-and-reparsed copy of
// the flowsheet. Before #4433 that copy dropped the root ds_rules attribute, so diab.drl and
// inrFlowsheet.drl never ran and the Recommendations box disappeared; the copy also re-read the
// rendered header HTML as a file name, so the INR dosing table disappeared too. Each step records what
// the uncustomized flowsheet shows this patient and asserts the customized one shows the same rule
// output and header, minus the hidden item.
// Fixtures: the owned FAKE-PW patient (runWorkflow), with no readings except one INR reading five
// months old (inrFlowsheet.drl only warns about a stale INR; diab.drl warns about never-recorded
// items). Cleanup deletes this patient's customization rows and readings and asserts both.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const DS_ERROR = 'Decision Support Had Errors Running.';

async function navigate(page, locator) {
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 20000 }),
    locator.click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  await h.assertNotErrorPage(page, new URL(page.url()).pathname);
}

/** The Recommendations box: diab.drl / inrFlowsheet.drl warnings and recommendations, sorted. */
async function ruleOutput(page) {
  const lines = (await page.locator('#recomList li').allTextContents()).map(text => text.trim()).filter(Boolean);
  h.assert(!lines.includes(DS_ERROR), 'The flowsheet reported that decision support failed to run');
  return lines.sort();
}

async function workflow(s) {
  const { sql, patient, provider } = s;
  const owned = `demographic_no=${h.sqlString(String(patient))}`;
  const hidden = (flowsheet, measurement) => `SELECT COUNT(*) FROM flowsheet_customization
    WHERE flowsheet=${h.sqlString(flowsheet)} AND ${owned} AND provider_no=${h.sqlString(provider)}
    AND action='delete' AND measurement=${h.sqlString(measurement)} AND archived='0'`;
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

  const item = (page, measurement) => page.locator(`div.preventionSection[id="${measurement}"]`);
  // The editor's Patient-scope view of one flowsheet, as editFlowsheetByDemographic opens it.
  async function hideForPatient(page, flowsheet, measurement) {
    await h.gotoApp(page, s.config.baseUrl, `/encounter/oscarMeasurements/adminFlowsheet/ViewEditFlowsheet`
      + `?flowsheet=${encodeURIComponent(flowsheet)}&demographic=${encodeURIComponent(patient)}`);
    await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await h.assertNotErrorPage(page, `${flowsheet} patient flowsheet editor`);
    const row = page.locator('#measurementTbl tbody tr')
      .filter({ has: page.locator('td:nth-child(3)', { hasText: new RegExp(`^\\s*${measurement}\\s*$`) }) });
    h.assert(await row.count() === 1, `The ${flowsheet} editor does not list exactly one ${measurement} row`);
    await navigate(page, row.locator('a[title="Hide this measurement"]'));
    await expectValue(sql, hidden(flowsheet, measurement), '1',
      `Hiding ${measurement} did not store one patient-scoped delete customization for ${flowsheet}`);
  }

  await s.step('a patient-scoped hide keeps the CDM Indicators diab.drl recommendations', async () => {
    const chart = await s.chart();
    const link = chart.locator('a[onclick*="ViewTemplateFlowSheet"][onclick*="template=diab3\'"]').first();
    await link.waitFor({ state: 'visible' });
    let page = await s.popup(chart, link, 'cdm-indicators');
    await page.waitForLoadState('networkidle').catch(() => {});
    await h.assertNotErrorPage(page, 'uncustomized CDM Indicators');
    const expected = await ruleOutput(page);
    h.assert(expected.includes('no A1C values has been recorded') && expected.includes('no BP has been recorded'),
      'The uncustomized CDM Indicators flowsheet does not show the diab.drl warnings for a patient with no readings');
    h.assert(await item(page, 'BP').isVisible(), 'The uncustomized CDM Indicators flowsheet does not show BP');

    await hideForPatient(page, 'diab3', 'BP');
    await page.close();
    page = await s.popup(chart, link, 'cdm-indicators-customized');
    await page.waitForLoadState('networkidle').catch(() => {});
    await h.assertNotErrorPage(page, 'customized CDM Indicators');
    h.assert(!await item(page, 'BP').isVisible(), 'The patient-scoped hide did not remove BP from the flowsheet');
    const customized = await ruleOutput(page);
    h.assert(customized.length > 0,
      'The customized CDM Indicators flowsheet lost every diab.drl recommendation (its ds_rules were not copied)');
    h.assert(JSON.stringify(customized) === JSON.stringify(expected),
      `The customized CDM Indicators flowsheet shows different diab.drl output: ${JSON.stringify(customized)}`);
    await page.close();
  });

  await s.step('a patient-scoped hide keeps the INR header table and inrFlowsheet.drl warning', async () => {
    const reading = sql.value(`INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,
      comments,dateObserved,dateEntered) VALUES('INR',${patient},${h.sqlString(provider)},'2.4','',
      ${h.sqlString(s.marker)},DATE_SUB(NOW(), INTERVAL 5 MONTH),NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(reading), 'The seeded INR reading was not created');
    const flowsheetPath = `/encounter/oscarMeasurements/ViewTemplateFlowSheet?demographic_no=${encodeURIComponent(patient)}&template=inrFlow`;
    const page = await s.context.newPage();
    await h.gotoApp(page, s.config.baseUrl, flowsheetPath);
    await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await h.assertNotErrorPage(page, 'uncustomized INR Flowsheet');
    const header = page.getByText('Tablet Strengths:', { exact: true });
    h.assert(await header.count() === 1, 'The uncustomized INR Flowsheet does not render its inr.html header');
    const expected = await ruleOutput(page);
    h.assert(expected.some(line => /^INR hasn't been reviewed in \d+ months$/.test(line)),
      'The uncustomized INR Flowsheet does not warn about the five-month-old INR reading');
    h.assert(await item(page, 'COUM').isVisible(), 'The uncustomized INR Flowsheet does not show COUM');

    await hideForPatient(page, 'inrFlow', 'COUM');
    await h.gotoApp(page, s.config.baseUrl, flowsheetPath);
    await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await h.assertNotErrorPage(page, 'customized INR Flowsheet');
    h.assert(!await item(page, 'COUM').isVisible(), 'The patient-scoped hide did not remove COUM from the flowsheet');
    h.assert(await header.count() === 1,
      'The customized INR Flowsheet lost its inr.html header (top_HTML was not copied as a file name)');
    const customized = await ruleOutput(page);
    h.assert(JSON.stringify(customized) === JSON.stringify(expected),
      `The customized INR Flowsheet shows different inrFlowsheet.drl output: ${JSON.stringify(customized)}`);
    await page.close();
  });
}

if (require.main === module) runWorkflow('flowsheet-customized-rules', workflow);
module.exports = { workflow };
