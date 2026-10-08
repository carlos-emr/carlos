#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit row of the OLDER note-history route, CaseManagementEntry?method=history (issue #4426).
 *
 * User path: Schedule > Search > Master Record > E-Chart > type a note > Save; the Note Search and Case
 * Management view pages then open the note's history popup through method=history (the E-Chart "rev"
 * link uses method=notehistory, which audit-log-chart-read already covers).
 *
 * Asserts, scoped to the owned patient and the saved note: opening the history writes exactly one
 * read/CME note row carrying the provider, the client address, demographic_no = the patient, contentId =
 * the note id and a non-empty data column (the audit string). Before the fix the audit string was passed
 * in the demographic-number slot: the row was written with a NULL demographic_no and NULL data, so the
 * read could not be found from the patient, and the note text reached the application log as a
 * NumberFormatException. The server-side half (no "For input string" ERROR in the journal) is covered by
 * the packaged-install server-log audit (scripts/deb-server-log-audit.sh) and by LogActionUnitTest.
 *
 * Fixtures: the harness's owned synthetic patient and its note. Cleanup deletes the note rows and the
 * audit rows scoped to the patient or the note id and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { auditProbe, incomplete, label } = require('./lib/audit-log-helpers');

const isEntry = method => r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

async function workflow(s) {
  const { sql, marker, patient, provider, config } = s;
  const probe = auditProbe({ sql, patient });
  const text = `${marker} audit history-route note`;
  let noteId;
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};
      DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned note rows were not removed');
    // Saving the note and reading its history write audit rows (log.data carries the audit string, which has the
    // note text); the probe removes the ones it can prove are owned, this removes any that carry this run's marker.
    sql.execute(`DELETE FROM log WHERE data LIKE ${h.sqlString(`%${marker}%`)}`);
    probe.cleanup();
  });

  await s.step('E-Chart: a note is saved', async () => {
    const chart = await s.chart();
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    await editor.click();
    await editor.fill(text);
    const [saved] = await Promise.all([chart.waitForResponse(isEntry('save')), chart.locator('#saveImg').first().click()]);
    h.assert(saved.status() === 200, `Saving the note answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id DESC LIMIT 1`, text,
      'The note text did not reach casemgmt_note');
    noteId = sql.value(`SELECT MAX(note_id) FROM casemgmt_note WHERE demographic_no=${patient}`);
    probe.own('CME note', noteId);
    await probe.settle(2000);
  });

  await s.step('method=history shows the note and writes one read/CME note row naming the patient, the note and the audit string', async () => {
    const before = probe.mark();
    const page = await s.context.newPage();
    try {
      const url = h.appUrl(config.baseUrl, `/CaseManagementEntry?method=history&from=casemgmt&noteId=${noteId}&demographicNo=${patient}&providerNo=${provider}`);
      const response = await page.goto(url);
      h.assert(response.status() === 200, `method=history answered HTTP ${response.status()}`);
      h.assert((await page.locator('body').innerText()).includes(text), 'The history view does not show the note');
    } finally {
      await page.close();
    }
    const rows = await probe.waitFor(all => all.some(r => r.action === 'read' && r.content === 'CME note'), 'note history read (method=history)', { after: before });
    const reads = rows.filter(r => r.provider === provider && r.action === 'read' && r.content === 'CME note');
    h.assert(reads.length === 1, `Reading the note history wrote ${reads.length} read/CME note rows, expected 1 (rows: ${rows.map(label).join(', ') || 'none'})`);
    const problems = incomplete(reads, { provider, patient });
    h.assert(!problems.length, `The read/CME note row of method=history is incomplete: ${problems.join(', ')}`);
    h.assert(reads[0].contentId === String(noteId), 'The read/CME note row does not name the note read');
    h.assert(Boolean(reads[0].data), 'The read/CME note row carries no audit data (the audit string was lost)');
  });
}

if (require.main === module) runWorkflow('audit-log-chart-note-history', workflow);
module.exports = { workflow };
