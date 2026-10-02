#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Exercise shared autocomplete on the patient flowsheet and lab provider-selection pages.
// No flowsheet is saved; the session owns and removes its synthetic patient.
const h = require('./lib/playwright-harness');
const { prepareIncomingFilingProgram } = require('./lib/incoming-filing-program-fixture');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  prepareIncomingFilingProgram(s);
  const page = await s.context.newPage();
  await h.gotoApp(page, s.config.baseUrl, '/encounter/oscarMeasurements/adminFlowsheet/ViewFlowsheetAdd');
  await h.assertNotErrorPage(page, 'flowsheet creation');
  const providerName = s.sql.value(`SELECT last_name FROM provider WHERE provider_no=${h.sqlString(s.provider)}`);
  for (const [scope, input, query, selectedId, expectedId] of [
    ['patient', '#demographicAC', s.marker, '#demographicNo', s.patient],
    ['provider', '#autocompleteprov', providerName, '#fwdProviders', s.provider],
  ]) {
    await s.step(`${scope} suggestions render highlighted labels and select the correct record`, async () => {
      if (scope === 'patient') {
        await page.locator('#scope').selectOption('patient');
      } else {
        await h.gotoApp(page, s.config.baseUrl, '/oscarMDS/ViewSelectProvider');
        await h.assertNotErrorPage(page, 'lab provider selection');
      }
      await page.locator(input).fill(query);
      const menuId = await page.locator(input).evaluate(el => window.jQuery(el).autocomplete('widget').attr('id'));
      const row = page.locator(`#${menuId} .ui-menu-item`).filter({ hasText: query }).first();
      await row.waitFor({ state: 'visible' });
      h.assert(await row.locator('span.match').count() > 0, 'The match highlight was rendered as literal HTML');
      h.assert(!(await row.innerText()).includes('<span'), 'Suggestion contains literal formatting markup');
      await row.click();
      const selected = scope === 'patient' ? await page.locator(selectedId).inputValue()
        : await page.locator(`${selectedId} option`).last().getAttribute('value');
      h.assert(selected === expectedId, 'Autocomplete selected the wrong record');
    });
  }
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-autocomplete-labels', workflow, { openPatient: true, openMaster: false });
