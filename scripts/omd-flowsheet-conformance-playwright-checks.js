#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * OntarioMD flowsheet conformance items from #3900 (issues #3892 and #3893; adapted from
 * MagentaHealth/Open-O), driven from the chart the way a clinician reaches them:
 *
 *   1. Diabetes flowsheet (DE16.066): the 10g monofilament exam (FTLS) and the 128Hz
 *      tuning-fork exam at D1 (NRTF) are separate rows, and an NRTF reading entered from the
 *      flowsheet saves. NRTF is seeded by migration V1.0.32, so this also proves the migration
 *      ran on the packaged install.
 *   2. Asthma flowsheet (DE16.098): the Action Plan entry offers Provided / Revised /
 *      Reviewed (migration V1.0.33) and saves one of them.
 *   3. An Action Plan reading stored before that change ("Yes") still displays when opened
 *      for editing: selected, and disabled so it cannot be chosen for a new reading.
 *
 * The check owns a synthetic patient (runWorkflow) and removes its diagnoses and readings.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const NRTF_LABEL = 'Neurological exam: 128Hz tuning fork D1';
const FTLS_LABEL = 'Neurological exam: 10g monofilament';

async function openFlowsheet(session, chart, template, label) {
  const link = chart.locator(`a[onclick*="ViewTemplateFlowSheet"][onclick*="template=${template}'"]`).first();
  await link.waitFor({ state: 'visible' });
  return session.popup(chart, link, label);
}

/**
 * The row's own add link. Its URL starts with ?measurement=<type>; the "Add all overdue"
 * link lists every overdue type after ?demographic_no=, so a plain measurement= match can
 * pick it instead.
 */
function rowAddLink(flowsheet, type) {
  return flowsheet.locator(`a.noborder[onclick*="ViewAddMeasurementData?measurement=${type}"]`).first();
}

async function selectOptions(page, selector) {
  return page.locator(`${selector} option`).evaluateAll((options) => options
    .map((option) => ({ value: option.value, disabled: option.disabled, selected: option.selected })));
}

async function workflow(session) {
  const { sql, patient, provider } = session;
  // The chart offers a flowsheet only for a patient with a triggering diagnosis. Seeding them
  // is set-up, not the behaviour under test (diagnosis-flowsheet covers the registry itself).
  sql.execute(`INSERT INTO dxresearch (demographic_no, start_date, update_date, status, dxresearch_code, coding_system, providerNo)
    VALUES (${patient}, CURDATE(), NOW(), 'A', '250', 'icd9', ${h.sqlString(provider)}),
           (${patient}, CURDATE(), NOW(), 'A', '493', 'icd9', ${h.sqlString(provider)})`);
  session.cleanup(() => sql.execute(`DELETE FROM dxresearch WHERE demographic_no=${patient};
    DELETE FROM measurements WHERE demographicNo=${patient}`));

  let chart = await session.chart();

  await session.step('diabetes flowsheet lists monofilament and tuning fork separately; NRTF saves', async () => {
    const flowsheet = await openFlowsheet(session, chart, 'diab2', 'diabetes-flowsheet');
    const text = await flowsheet.locator('body').innerText();
    h.assert(text.includes(FTLS_LABEL), 'the diabetes flowsheet does not label FTLS as the 10g monofilament exam');
    h.assert(text.includes(NRTF_LABEL), 'the diabetes flowsheet has no 128Hz tuning fork (NRTF) row');
    const add = rowAddLink(flowsheet, 'NRTF');
    const entry = await session.popup(flowsheet, add, 'nrtf-entry');
    const opened = await entry.locator('[name="inputType-0"]').inputValue();
    h.assert(opened === 'NRTF', `the flowsheet's NRTF link opened the entry form for ${opened || 'no measurement'}`);
    const options = await selectOptions(entry, '[name="inputValue-0"]');
    const yes = options.find((option) => /^yes$/i.test(option.value));
    h.assert(yes, 'the NRTF entry does not offer the Yes/No/NA choices FTLS uses');
    await entry.locator('[name="inputValue-0"]').selectOption(yes.value);
    await entry.getByRole('button', { name: 'Save', exact: true }).click();
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient} AND type='NRTF'
      AND dataField=${h.sqlString(yes.value)}`, '1', 'the NRTF reading entered from the flowsheet was not saved');
    await flowsheet.close();
  });

  await session.step('asthma Action Plan offers Provided / Revised / Reviewed and saves one', async () => {
    chart = await session.chart();
    const flowsheet = await openFlowsheet(session, chart, 'ASTH', 'asthma-flowsheet');
    const add = rowAddLink(flowsheet, 'AACP');
    const entry = await session.popup(flowsheet, add, 'aacp-entry');
    const values = (await selectOptions(entry, '[name="inputValue-0"]')).map((option) => option.value).filter(Boolean);
    h.assert(JSON.stringify(values) === JSON.stringify(['Provided', 'Revised', 'Reviewed']),
      `the Action Plan offers ${JSON.stringify(values)}, expected Provided, Revised, Reviewed`);
    await entry.locator('[name="inputValue-0"]').selectOption('Revised');
    await entry.getByRole('button', { name: 'Save', exact: true }).click();
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient} AND type='AACP'
      AND dataField='Revised'`, '1', 'the Action Plan reading was not saved');
    await flowsheet.close();
  });

  await session.step('a pre-change Action Plan reading still displays, selected but not choosable', async () => {
    const id = sql.value(`INSERT INTO measurements (type, demographicNo, providerNo, dataField, measuringInstruction,
      dateObserved, dateEntered) VALUES ('AACP', ${patient}, ${h.sqlString(provider)}, 'Yes', 'Yes/No',
      NOW() - INTERVAL 1 YEAR, NOW() - INTERVAL 1 YEAR); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'the legacy Action Plan reading fixture was not created');
    const page = await session.context.newPage();
    const query = new URLSearchParams({ demographic_no: patient, measurement: 'AACP', template: 'ASTH', id });
    await h.gotoApp(page, session.config.baseUrl, `/encounter/oscarMeasurements/ViewAddMeasurementData?${query}`);
    await h.assertNotErrorPage(page, 'Action Plan edit page');
    const options = await selectOptions(page, '[name="inputValue-0"]');
    const legacy = options.find((option) => option.value === 'Yes');
    h.assert(legacy, 'the stored "Yes" reading is not shown on the edit page');
    h.assert(legacy.selected && legacy.disabled, 'the stored "Yes" reading must be selected and disabled');
    h.assert(options.filter((option) => option.selected).length === 1, 'more than one Action Plan option is selected');
    await page.close();
  });
}

if (require.main === module) runWorkflow('omd-flowsheet-conformance', workflow);
module.exports = { workflow };
