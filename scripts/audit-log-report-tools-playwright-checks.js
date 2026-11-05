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
 * export or report action of this provider that names the demographic report and this run's patient id, or any row
 * naming the patient) with
 * the provider and a nonempty client address on every row accepted as that event. The sibling bulk list, Patient List by Appointment Time, writes an `export` row with the provider,
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
    const listed = tool.locator('table tr').filter({ hasText: marker });
    const rows = await listed.count();
    h.assert(rows === 1, `The report listed ${rows} rows for the owned patient, expected 1`);
    // The marker row must carry the patient's own Demographic ID in a cell of its own, not just the name.
    const cells = (await listed.first().locator('td,th').allInnerTexts()).map(text => text.trim());
    h.assert(cells.includes(String(patient)), 'The report row for the owned patient does not show its Demographic ID');
  });

  await s.step('the report run is in the audit log, with the provider and the client address, and no patient text', async () => {
    await probe.settle(3000);
    // A report row of this run: written by the test provider since the watermark, naming the demographic report
    // itself AND this run's selection (the owned patient's id, the only Demographic IDs criterion). The test login is
    // shared with checks running at the same time, and several of them run this same Demographic Report Tool, so a
    // provider-wide "demographic report row" could be another check's run and must not count as this one's.
    const reportFilter = `(action='export' OR action LIKE '%eport%' OR content LIKE '%eport%')
      AND (action LIKE '%emographic%' OR content LIKE '%emographic%' OR data LIKE '%emographic%')`;
    const reportRows = probe.byProvider(provider, before, reportFilter);
    const own = probe.byProvider(provider, before, `${reportFilter}
      AND (contentId=${h.sqlString(String(patient))} OR data REGEXP '(^|[^0-9])${patient}([^0-9]|$)')`);
    // Only rows that provably name the patient count as evidence; a colliding id from a concurrent check must not.
    const aboutPatient = probe.ownedSince(before, `action NOT LIKE 'read%' AND action NOT LIKE 'DemographicManager.%' AND action NOT LIKE 'PatientConsentManager.%'`);
    const accepted = [...new Map([...own, ...aboutPatient].map(r => [r.id, r])).values()];
    h.assert(accepted.length >= 1,
      'Running the Demographic Report Tool (a listing of patient names and ids) wrote no audit row');
    h.assert(accepted.every(r => Boolean(r.ip)), `The report audit row carries no client address (${accepted.filter(r => !r.ip).map(r => label(r, { maskContent: true })).join(', ')})`);
    // The unique marker is scanned over every broadly matched row. The fixture's first name ("Workflow") is shared by
    // every check's patient, so it is scanned only in rows that provably name this patient: a concurrent check's
    // report row carrying its own patient's first name is not this run's leak.
    const leaks = [...new Set([...phiLeaks([...probe.rows(`id>${before}`), ...reportRows], [marker]),
      ...phiLeaks(probe.ownedSince(before), ['Workflow'])])];
    h.assert(!leaks.length, `A report audit row carries patient text (${leaks.join(', ')})`);
    h.assert(aboutPatient.every(r => r.provider === provider), `A report row is attributed to another provider (${aboutPatient.map(r => label(r, { maskContent: true })).join(', ')})`);
  });
}

if (require.main === module) runWorkflow('audit-log-report-tools', workflow);
module.exports = { workflow };
