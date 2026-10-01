#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient sets, through the only controls a user can reach.
 * User path: Schedule ▸ Report ▸ Demographic Report Tool (report/DemographicReport)
 * ▸ select Demographic No and names, filter by last name and Demographic ID ▸ Run
 * Query ▸ Run Query And Save to Patient Set; then Schedule ▸ Administration ▸ Data
 * Management ▸ Demographic Export, where saved sets are offered by name.
 * Asserts: the query isolates the owned patient; the save writes exactly one
 * demographicSets row (demographic_no, set_name, archive='0'); the export page
 * lists the new set. Fixtures: the owned FAKE- patient from runWorkflow and a
 * marker-named set ('PW' + 16 hex: set_name is varchar(20)); cleanup deletes every
 * row of that set name and asserts none remain. Not covered because no page links
 * them (reported, not pinned): the Master Record addToPatientSet() control
 * (demographic/ViewAddDemoToPatientSet), demographic/ViewDemographicCohort,
 * report/DemographicSetEdit (remove / ineligible) and report/CreateDemographicSet.
 * Implements coverage plan §2.4 patient-set-cohort.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopup, clickOpensPopupOrNavigates, clickAndAwaitReload} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

async function workflow(s) {
  const {sql, patient, marker, context, recorder} = s;
  // demographicSets.set_name is varchar(20): the full FAKE-PW marker does not fit.
  const setName = `PW${marker.slice(-16)}`;
  const setRows = `demographicSets WHERE set_name=${h.sqlString(setName)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM ${setRows}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM ${setRows}`) === '0', 'Owned patient set rows were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM ${setRows}`) === '0', 'The owned set name is already in use');

  // Schedule ▸ Report opens the report index (popup, or in place under the schedule shell).
  const {page: reportIndex} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a[title="Generate a report"]').first(),
    {context, recorder, label: 'report-index', timeout: 20000});
  // Issue #3275: the Demographic Report Tool link navigates the index window itself.
  const {page: tool} = await clickOpensPopupOrNavigates(reportIndex, reportIndex.locator('a[href*="ViewReportDemographicReport"]').first(),
    {context, recorder, label: 'demographic-report-tool', timeout: 20000});
  await tool.locator('#select_demographic_no').waitFor();
  const resultsHeader = () => tool.locator('.section-header').filter({hasText: 'Search Returned:'});
  async function runQuery(buttonName) {
    await clickAndAwaitReload(tool, tool.getByRole('button', {name: buttonName, exact: true}), {timeout: 30000, label: buttonName});
    await h.assertNotErrorPage(tool, buttonName);
    await resultsHeader().waitFor();
    h.assert((await resultsHeader().innerText()).replace(/\s+/g, ' ').includes('Search Returned: 1 Results'),
      `${buttonName} did not return exactly the owned patient`);
    const row = tool.locator('table tr').filter({hasText: marker});
    h.assert(await row.count() === 1 && (await row.innerText()).includes(patient), `${buttonName} result row is not the owned patient`);
  }

  await s.step('Report ▸ Demographic Report Tool runs a query that isolates the owned patient', async () => {
    // The save branch only reads a result set whose FIRST selected column is demographic_no.
    for (const id of ['#select_demographic_no', '#select_last_name', '#select_first_name']) await tool.locator(id).check();
    await tool.locator('#lastName').fill(marker);
    await tool.locator('textarea[name="demoIds"]').fill(patient);
    await runQuery('Run Query');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${setRows}`) === '0', 'Run Query created a patient set');
  });

  await s.step('Run Query And Save to Patient Set creates the set with exactly the owned patient', async () => {
    // The tool repopulates its own form after a run, so the filters are still in place.
    h.assert(await tool.locator('#lastName').inputValue() === marker && await tool.locator('#select_demographic_no').isChecked(),
      'The report tool did not keep the query after Run Query');
    await tool.locator('input[name="setName"]').fill(setName);
    await runQuery('Run Query And Save to Patient Set');
    await expectValue(sql, `SELECT COUNT(*) FROM ${setRows} AND demographic_no=${patient} AND archive='0'`, '1',
      'The save did not write the owned patient into the named set');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${setRows}`) === '1', 'The saved set holds rows other than the owned patient');
    h.assert(sql.value(`SELECT COUNT(*) FROM ${setRows} AND eligibility IS NOT NULL`) === '0', 'A new set member was created with an eligibility flag');
  });

  await s.step('the new set is offered by name where sets are consumed (Administration ▸ Demographic Export)', async () => {
    // The Report link may have replaced the day sheet with the schedule shell; the
    // Administration control then lives in a fresh session of the same user.
    let schedule = s.schedule;
    let second;
    if (await schedule.locator('#admin-panel, #admin2').count() === 0) {
      second = await h.newContext(context.browser(), s.config);
      second.on('page', page => h.wireStrictPage(page, 'patient-set-second-session', recorder));
      schedule = await h.login(second, s.config, recorder, {label: 'patient-set-second-login'});
    }
    const {page: admin} = await clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel, #admin2').first(),
      {context: schedule.context(), recorder, label: 'patient-set-administration', timeout: 20000});
    const link = admin.getByRole('link', {name: 'Demographic Export', exact: true, includeHidden: true});
    await revealAuditLink(admin, link, 20000);
    // Not s.popup: a second-session popup fires on that session's context, not the workflow's.
    const exporter = await clickOpensPopup(admin, link, {context: admin.context(), recorder, label: 'demographic-export', timeout: 20000});
    const options = exporter.locator('select#patientSet option');
    await options.first().waitFor();
    const named = options.filter({hasText: setName});
    h.assert(await named.count() === 1 && await named.getAttribute('value') === setName
      && (await named.innerText()).trim() === setName, 'The export page does not offer the new patient set by name');
    await exporter.close();
    if (second) await second.close();
  });
}

if (require.main === module) runWorkflow('patient-set-cohort', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
