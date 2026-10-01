#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * An HRM report matched to a patient who has no health number must not break the Inbox (gap-records, HRM).
 * User path: Schedule ▸ Inbox (the Inboxhub list, any provider, all statuses).
 * Asserts: with one owned HRM report routed to the test provider and matched to an owned patient whose HIN is
 * NULL (a newborn, an out-of-province or imported patient), the list request answers HTTP 200 and the list
 * shows the report. On 2026.08 the request answers 500 for EVERY provider whose inbox includes the report:
 * HRMResultsData.populateHRMdocumentsResultsData copies demographic.getHin() into lbData.healthNumber and then
 * calls healthNumber.contains(...) (HRMResultsData.java:180) -> NullPointerException.
 * Fixtures: the runWorkflow FAKE- patient with its HIN left NULL, one owned schema-valid text HRM XML file with
 * HRMDocument / HRMDocumentToDemographic / HRMDocumentToProvider rows, all removed in cleanup and checked gone.
 * EXCLUSIVE=1: while the fixture exists every other session's "any provider" Inbox list is broken, so no other
 * check may run beside it.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { createOwnedHrmReport } = require('./lib/gap-records-hrm-fixture');
const { settle } = require('./inboxhub-filters-playwright-checks');

async function workflow(s) {
  const { sql, marker, patient, provider, context, recorder } = s;
  h.assert(sql.value(`SELECT hin IS NULL FROM demographic WHERE demographic_no=${patient}`) === '1', 'The owned patient is expected to have a NULL HIN');
  const report = createOwnedHrmReport({ sql, marker, suffix: 'nullhin', description: `${marker} null hin`, reportText: `${marker} HRM report for a patient without a health number` });
  s.cleanup(() => report.remove());
  const reportId = report.create();
  const key = h.sqlString(reportId);
  sql.execute(`INSERT INTO HRMDocumentToDemographic (demographicNo,hrmDocumentId,timeAssigned) VALUES (${patient},${key},NOW());
    INSERT INTO HRMDocumentToProvider (providerNo,hrmDocumentId,signedOff,viewed) VALUES (${h.sqlString(provider)},${key},0,0)`);

  await s.step('the Inbox list answers 200 and lists an HRM report matched to a patient without a health number', async () => {
    const { page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink').first(),
      { context, recorder, label: 'null-hin-inbox', timeout: 60000 });
    await inbox.locator('#btnViewMode2').waitFor({ state: 'attached', timeout: 60000 });
    if (await inbox.locator('#btnViewMode2').isChecked()) await inbox.locator('#btnViewModeLabel').click();
    await settle(inbox, 60000).catch(() => {});
    const failed = recorder.badResponses.filter(entry => entry.label === 'null-hin-inbox' && /\/web\/inboxhub\/Inboxhub/.test(entry.url));
    h.assert(failed.length === 0, `The Inbox list answered HTTP ${failed[0] && failed[0].status} for an HRM report matched to a patient whose HIN is NULL `
      + '(HRMResultsData.java:180 calls healthNumber.contains on the NULL HIN); the inbox of every provider routed that report is unusable');
    const rows = await inbox.locator('#inboxhubListModeTableBody tr[data-segment-id]').evaluateAll(trs => trs.map(tr => tr.getAttribute('data-segment-id')));
    h.assert(rows.includes(reportId), 'The Inbox list does not show the HRM report routed to the test provider');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-hrm-inbox-null-hin', workflow, { openPatient: true, openMaster: false });
