#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of a bulk patient-data report: the Demographic Report Tool (wave 7 sweep `audit-log`).
 *
 * User path: Schedule > Report ("Generate a report") > Demographic Report Tool (ViewReportDemographicReport) > tick
 * the Demographic ID, last name and first name columns > the owned patient's id in "Demographic IDs" > Run
 * Query > the result table (name and id for every patient the criteria select).
 *
 * Asserts the result table lists the owned patient with the marker name (the report ran and returned
 * identifying data) and, as the test provider and since the run: the report left an audit row of its own (an
 * export or report action for this provider, or any row naming the patient) with the provider and the client
 * address. The sibling bulk list, Patient List by Appointment Time, writes an `export` row with the provider,
 * the client address, the date range and the row count (PatientListByAppt.createExportAuditLog), so a
 * demographic listing that writes none is the inconsistency asserted here. The audit rows scoped to the
 * patient are checked for patient text too.
 *
 * Fixtures: the harness's owned synthetic patient; nothing is saved (Run Query only). Cleanup removes the audit
 * rows scoped to the patient and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates, clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { auditProbe, phiLeaks, label } = require('./lib/audit-log-helpers');

const TIMEOUT = 30000;

async function workflow(s) {
  const { sql, marker, patient, provider, context, recorder } = s;
  const probe = auditProbe({ sql, patient });
  s.cleanup(() => probe.cleanup());
  let before;

  const { page: reportIndex } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a[title="Generate a report"]').first(),
    { context, recorder, label: 'audit-report-index', timeout: TIMEOUT });
  const { page: tool } = await clickOpensPopupOrNavigates(reportIndex, reportIndex.locator('a[href*="ViewReportDemographicReport"]').first(),
    { context, recorder, label: 'audit-report-tool', timeout: TIMEOUT });
  await tool.locator('#select_demographic_no').waitFor({ timeout: TIMEOUT });

  await s.step('Run Query lists the owned patient by name and id', async () => {
    await probe.settle(2000);
    before = probe.mark();
    for (const id of ['#select_demographic_no', '#select_last_name', '#select_first_name']) await tool.locator(id).check();
    await tool.locator('#lastName').fill('');
    await tool.locator('#firstName').fill('');
    await tool.locator('textarea[name="demoIds"]').fill(patient);
    await clickAndAwaitReload(tool, tool.getByRole('button', { name: 'Run Query', exact: true }), { timeout: TIMEOUT, label: 'Run Query' });
    await h.assertNotErrorPage(tool, 'Run Query');
    await tool.locator('.section-header').filter({ hasText: 'Search Returned:' }).waitFor({ timeout: TIMEOUT });
    const rows = await tool.locator('table tr').filter({ hasText: marker }).count();
    h.assert(rows === 1, `The report listed ${rows} rows for the owned patient, expected 1`);
  });

  await s.step('the report run is in the audit log, with the provider and the client address, and no patient text', async () => {
    await probe.settle(3000);
    const own = sql.rows(`SELECT id,COALESCE(action,''),COALESCE(content,''),COALESCE(ip,'~NULL~') FROM log WHERE id>${before}
      AND provider_no=${h.sqlString(provider)} AND (action='export' OR action LIKE '%eport%' OR content LIKE '%eport%' OR content LIKE '%emographic report%')`);
    const aboutPatient = probe.since(before, `action NOT LIKE 'read%' AND action NOT LIKE 'DemographicManager.%' AND action NOT LIKE 'PatientConsentManager.%'`);
    h.assert(own.length + aboutPatient.length >= 1,
      'Running the Demographic Report Tool (a listing of patient names and ids) wrote no audit row');
    h.assert(own.every(([, , , ip]) => ip !== '~NULL~'), 'The report audit row carries no client address');
    const leaks = phiLeaks(probe.rows(`id>${before}`), [marker]);
    h.assert(!leaks.length, `A report audit row carries patient text (${leaks.join(', ')})`);
    h.assert(aboutPatient.every(r => r.provider === provider), `A report row is attributed to another provider (${aboutPatient.map(label).join(', ')})`);
  });
}

if (require.main === module) runWorkflow('audit-log-report-tools', workflow);
module.exports = { workflow };
