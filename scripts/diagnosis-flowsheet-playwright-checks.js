#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5: diagnosis search/add/resolve/delete and the clinical
// flowsheet the diagnosis enables. Never seed the diagnosis being tested.
const { assert, withExpectedDialogs } = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient } = s;
  s.cleanup(() => sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${patient};
    DELETE FROM measurements WHERE demographicNo=${patient}`));
  let chart = await s.chart();
  const registry = await s.popup(chart, chart.locator('a[onclick*="setupDxResearch"]').first(), 'diagnosis-registry');
  let id;
  const status = () => `SELECT status FROM dxresearch WHERE dxresearch_no=${id} AND demographic_no=${patient}`;
  await s.step('search selects a real diagnosis and add persists it', async () => {
    await registry.locator('select[name="selectedCodingSystem"]').selectOption('icd9');
    await registry.locator('[name="xml_research1"]').fill('250');
    const results = await s.popup(registry, registry.locator('[name="codeSearch"]'), 'diagnosis-search');
    await results.locator('input[name="searchCodes"][value="250"]').check();
    await results.locator('[name="confirm"]').click();
    assert(await registry.locator('[name="xml_research1"]').inputValue() === '250',
      'Diagnosis search did not return the selected code to the entry form');
    await clickAndAwaitReload(registry, registry.locator('[name="codeAdd"]'));
    id = sql.value(`SELECT dxresearch_no FROM dxresearch WHERE demographic_no=${patient} AND dxresearch_code='250' AND status='A'`);
    assert(/^[1-9]\d*$/.test(id), 'The selected diagnosis was not saved');
    await registry.locator(`#startdate1st${id}`).waitFor({ state: 'visible' });
  });
  await s.step('diagnosis enables a clinical flowsheet and measurement entry', async () => {
    await chart.close();
    chart = await s.chart();
    const link = chart.locator('a[onclick*="ViewTemplateFlowSheet"][onclick*="template=diab2\'"]').first();
    await link.waitFor({ state: 'visible' });
    const flowsheet = await s.popup(chart, link, 'clinical-flowsheet');
    const add = flowsheet.locator('a[onclick*="ViewAddMeasurementData"][onclick*="measurement=A1C"]').first();
    const entry = await s.popup(flowsheet, add, 'flowsheet-measurement');
    assert(await entry.locator('[name="inputType-0"]').inputValue() === 'A1C', 'Flowsheet opened the wrong measurement');
    await entry.locator('[name="inputValue-0"]').fill('6.4');
    await entry.getByRole('button', { name: 'Save', exact: true }).click();
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient} AND type='A1C' AND dataField='6.4'`, '1',
      'Clinical flowsheet save did not persist the entered measurement');
    await flowsheet.locator('.preventionProcedure[onclick*="measurement=A1C"] p')
      .filter({ hasText: /A1C:\s*6\.4(?:\s|$)/ }).first().waitFor({ state: 'visible' });
    await flowsheet.close();
  });
  await s.step('resolve persists, cancelled delete preserves, accepted delete archives', async () => {
    const row = registry.locator(`#startdate1st${id}`).locator('xpath=ancestor::tr[1]');
    await clickAndAwaitReload(registry, row.getByRole('link', { name: 'Resolve', exact: true }));
    await expectValue(sql, status(), 'C', 'Resolve did not persist');
    const remove = registry.locator(`a[onclick*="'D','','${id}'"]`);
    const dismissed = await withExpectedDialogs(registry, () => remove.click(), { accept: false });
    assert(dismissed.length === 1 && dismissed[0].type === 'confirm', 'Delete did not ask for confirmation');
    assert(sql.value(status()) === 'C', 'Cancelled delete changed the diagnosis');
    const accepted = await withExpectedDialogs(registry, () => clickAndAwaitReload(registry, remove));
    assert(accepted.length === 1 && accepted[0].type === 'confirm', 'Accepted delete did not use confirmation');
    await expectValue(sql, status(), 'D', 'Delete did not retain the archived diagnosis');
    assert(await registry.locator(`a[onclick*="'D','','${id}'"]`).count() === 0, 'Deleted diagnosis is still listed');
  });
}
if (require.main === module) runWorkflow('diagnosis-flowsheet', workflow);
module.exports = { workflow };
