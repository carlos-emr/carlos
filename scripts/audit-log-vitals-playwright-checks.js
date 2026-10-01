#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of vitals (measurement) entry and deletion from the E-Chart (wave 7 sweep `audit-log`).
 *
 * User path: Schedule > Search > Master Record > E-Chart > Measurements "+" > Input Groups > Vitals (popup) > BP
 * value, observation date, comment > Submit; Vitals again > the BP last-value clock (history popup) > tick the
 * reading > Delete.
 *
 * Asserts the `measurements` row after the entry (value, comment, provider) and its move to
 * `measurementsDeleted` after the delete, and, scoped to the owned patient: entering a reading and deleting
 * it are each audited with at least one row that is not a read, carrying the provider, the client address and
 * demographic_no = the patient; and no audit row carries the reading's value or comment. Every expectation is
 * evaluated and the violated ones are reported together in the last step.
 *
 * Fixtures: the harness's owned synthetic patient; one BP reading carrying the run marker as its comment.
 * Cleanup deletes this patient's readings, archived readings, encounter notes (with their issue, ext and link
 * rows) and the audit rows scoped to the patient, and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { auditProbe, phiLeaks, incomplete, label } = require('./lib/audit-log-helpers');

const VALUE = '128/82';

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const probe = auditProbe({ sql, patient, provider });
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  s.cleanup(() => {
    const ownedNotes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient};
      DELETE FROM measurementsDeleted WHERE demographicNo=${patient};
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${ownedNotes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${ownedNotes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${ownedNotes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`) === '0', 'Owned readings or notes were not removed');
    probe.cleanup();
  });
  const writesSince = since => probe.since(since, `action NOT LIKE 'read%' AND action NOT LIKE '%Manager.get%' AND action NOT LIKE '%Manager.find%'
    AND action NOT LIKE 'DemographicManager.%' AND action NOT LIKE 'PatientConsentManager.%' AND NOT (content='CME note')`);
  async function judge(what, since) {
    await probe.settle(2500);
    const rows = writesSince(since);
    expect(rows.length >= 1, `${what} wrote no audit row`);
    for (const r of rows) {
      const problems = incomplete([r], { provider, patient });
      expect(!problems.length, `${what}: the audit row ${label(r)} is incomplete (${problems.join(', ')})`);
    }
    expect(!phiLeaks(probe.since(since), [VALUE, marker]).length, `${what}: an audit row carries the reading or its comment`);
  }

  const chart = await s.chart();
  const openGroup = async name => {
    await chart.locator('#menuTitle3 a').hover();
    const item = chart.locator('#menu3 a.menuItemleft').filter({ hasText: 'Vitals' });
    await item.waitFor({ state: 'visible' });
    const page = await s.popup(chart, item, name);
    await page.locator('#row-BP').waitFor({ state: 'visible' });
    return page;
  };
  let readingId;

  await s.step('the Vitals group saves a BP reading (audit rows observed)', async () => {
    const before = probe.mark();
    const group = await openGroup('audit-vitals-entry');
    const row = group.locator('#row-BP');
    await row.locator('input[name^="inputValue-"]').fill(VALUE);
    await row.locator('input[name^="date-"]').fill('2026-03-04');
    await row.locator('input[name^="comments-"]').fill(marker);
    const closed = group.waitForEvent('close', { timeout: 20000 });
    await group.getByRole('button', { name: 'Submit', exact: true }).click();
    await closed;
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient} AND type='BP' AND dataField=${h.sqlString(VALUE)}
      AND comments=${h.sqlString(marker)} AND providerNo=${h.sqlString(provider)}`, '1', 'The BP reading was not stored as entered');
    readingId = sql.value(`SELECT id FROM measurements WHERE demographicNo=${patient} AND type='BP'`);
    await judge('Entering the vitals reading', before);
  });

  await s.step('the history popup deletes the reading (audit rows observed)', async () => {
    const before = probe.mark();
    const group = await openGroup('audit-vitals-last-value');
    const last = group.locator('tr.note').filter({ has: group.locator('i.fa-clock[onclick*="type=BP"]') });
    const history = await s.popup(group, last.locator('i.fa-clock'), 'audit-vitals-history');
    await group.close();
    await history.locator(`input[name="deleteCheckbox"][value="${readingId}"]`).check();
    await clickAndAwaitReload(history, history.locator('input[onclick="submit();"]'), { label: 'history Delete' });
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE id=${readingId}`, '0', 'The reading was not deleted');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE originalId=${readingId} AND demographicNo=${patient}`) === '1',
      'The deleted reading was not archived');
    await judge('Deleting the vitals reading', before);
    await history.close().catch(() => {});
  });

  await s.step('every expectation of the vitals audit trail held', async () => {
    h.assert(!defects.length, `Audit-trail defects on the vitals path:\n  - ${[...new Set(defects)].join('\n  - ')}`);
  });
}

if (require.main === module) runWorkflow('audit-log-vitals', workflow);
module.exports = { workflow };
