#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of the main PHI READ paths of one patient chart (wave 7 sweep `audit-log`).
 *
 * User path: Schedule > Search > Master Record; Master Record > E-Chart; E-Chart > type a note > Save
 * > the note's "rev" link (Note Revision History popup); E-Chart > the Print icon (#imgPrintEncounter)
 * > "Print All Notes" > Print (the browser downloads the chart PDF).
 *
 * Asserts the `log` table after each action, scoped to the owned patient: opening the Master Record
 * writes exactly one read/demographic row and opening the E-Chart exactly one read/eChart row, each
 * carrying the provider, the client address and demographic_no = the patient; reading a note's
 * revision history writes a read/CME note row that names the patient (demographic_no) and the note
 * (contentId); printing the whole chart as a PDF is audited (an action naming print, naming the
 * patient in demographic_no); and no row of the read path carries the note text. The check runs
 * every step and reports every violated expectation in its LAST step, so the steps that pass prove
 * what works before the defect is named.
 *
 * Fixtures: the harness's owned synthetic patient and one note saved through the chart. Cleanup
 * deletes the note rows and the audit rows scoped to the patient or to the note id and asserts them gone.
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { auditProbe, phiLeaks, incomplete, label } = require('./lib/audit-log-helpers');

const isEntry = method => r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const probe = auditProbe({ sql, patient, provider });
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  const text = `${marker} audit read-path note`;
  let noteId;
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned note rows were not removed');
    probe.cleanup();
  });
  const mine = rows => rows.filter(r => r.provider === provider);

  await s.step('Master Record open wrote exactly one complete read/demographic row', async () => {
    // An audit mismatch is recorded, not thrown: the E-Chart, note-history and print steps are independent of it
    // and must still run so the final step reports every defect together.
    const rows = await probe.waitFor(all => all.some(r => r.action === 'read' && r.content === 'demographic'), 'Master Record open')
      .catch(error => { expect(false, error.message); return probe.rows(); });
    const reads = mine(rows).filter(r => r.action === 'read' && r.content === 'demographic');
    expect(reads.length === 1, `Opening the Master Record wrote ${reads.length} read/demographic rows, expected 1`);
    const problems = incomplete(reads, { provider, patient });
    expect(!problems.length, `The read/demographic row is incomplete: ${problems.join(', ')}`);
    if (reads.length) expect(reads[0].contentId === String(patient), 'The read/demographic row names another contentId');
  });

  await s.step('E-Chart open wrote exactly one complete read/eChart row', async () => {
    const before = probe.mark();
    await s.chart();
    const rows = await probe.waitFor(all => all.some(r => r.action === 'read' && r.content === 'eChart'), 'E-Chart open', { after: before })
      .catch(error => { expect(false, error.message); return probe.rows(`id>${before}`); });
    const reads = mine(rows).filter(r => r.action === 'read' && r.content === 'eChart');
    expect(reads.length === 1, `Opening the E-Chart wrote ${reads.length} read/eChart rows, expected 1`);
    const problems = incomplete(reads, { provider, patient });
    expect(!problems.length, `The read/eChart row is incomplete: ${problems.join(', ')}`);
    expect(!phiLeaks(rows, [marker]).length, `Opening the chart logged the patient name in: ${phiLeaks(rows, [marker]).join(', ')}`);
  });

  await s.step('a note is saved and its rev link opens the Note Revision History (audit rows observed)', async () => {
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
    const before = probe.mark();
    const rev = chart.locator('#encMainDiv a[onclick^="return showHistory("]').first();
    const history = await s.popup(chart, rev, 'note-history');
    await history.locator('h3', { hasText: 'Note Revision History' }).waitFor();
    h.assert((await history.locator('body').innerText()).includes(text), 'The history popup does not show the note');
    await history.close();
    await probe.settle(2500);
    const reads = probe.since(before, `action='read' AND content='CME note'`);
    expect(reads.length === 1, `Reading a note's revision history from the E-Chart rev link (CaseManagementEntry method=notehistory) wrote ${reads.length} read/CME note rows, expected 1`);
    if (reads.length) {
      expect(reads[0].demographic === String(patient), 'The read/CME note row (note history) carries no demographic_no, so the read cannot be found from the patient');
      expect(reads[0].contentId === String(noteId), 'The read/CME note row does not name the note read');
      expect(!phiLeaks(reads, [text]).length, 'The read/CME note row carries the note text');
    }
  });

  await s.step('the chart Print icon downloads a PDF of all notes (audit rows observed)', async () => {
    const chart = await s.chart();
    const before = probe.mark();
    const download = chart.waitForEvent('download', { timeout: 40000 });
    await chart.locator('#imgPrintEncounter').click();
    await chart.locator('#printOps').waitFor({ state: 'visible' });
    await chart.locator('#printopAll').check();
    await chart.locator('#printOp').click();
    const file = await download;
    const bytes = fs.readFileSync(await file.path());
    h.assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The chart print did not download a PDF');
    h.assert(bytes.subarray(-1024).toString('latin1').includes('%%EOF'), 'The chart print PDF is truncated (no %%EOF trailer)');
    await probe.settle(3000);
    const printed = probe.since(before).filter(r => /print|export|reprint/i.test(r.action));
    expect(printed.length >= 1, 'Printing the whole chart as a PDF (E-Chart Print icon, CaseManagementEntry method=print) wrote no audit row');
    for (const r of printed) {
      expect(r.demographic === String(patient), `The chart print row ${label(r)} carries no demographic_no`);
      expect(Boolean(r.ip), `The chart print row ${label(r)} carries no client address`);
    }
  });

  await s.step('observed: the demographic_no and provider columns of every audit row that names the patient', async () => {
    const rows = probe.rows();
    const unkeyed = [...new Set(rows.filter(r => r.demographic !== String(patient)).map(label))];
    expect(!unkeyed.length, `Rows about the patient with no demographic_no column: ${unkeyed.join('; ')}`);
    const anonymous = [...new Set(rows.filter(r => !r.provider).map(label))];
    expect(!anonymous.length, `Rows with no provider: ${anonymous.join('; ')}`);
  });

  await s.step('every expectation of the read path held', async () => {
    h.assert(!defects.length, `Audit-trail defects on the chart read path:\n  - ${defects.join('\n  - ')}`);
  });
}

if (require.main === module) runWorkflow('audit-log-chart-read', workflow);
module.exports = { workflow };
