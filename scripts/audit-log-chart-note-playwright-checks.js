#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of the chart-note WRITE path, and whether the trail itself leaks the note (wave 7 sweep `audit-log`).
 *
 * User path: Schedule > Search > Master Record > E-Chart > type a note > Save; type more > Save again;
 * Sign & Save (the chart window closes); reopen the E-Chart.
 *
 * Asserts, scoped to the owned patient and the saved note: the first Save writes exactly one add/CME note
 * row and the second Save exactly one update/CME note row, each with the provider, the client address,
 * demographic_no = the patient and contentId = the note id; Sign & Save is recorded as a further
 * update/CME note row for the same note; reopening the chart does not write a note row of its own for a
 * note nobody changed. The last step reports every violated expectation, including the privacy one: a log
 * row must say THAT a note was written, not carry the note. The note text (which a clinician types as
 * clinical content) is read back from `log.data` and must appear in none of content, contentId and data.
 *
 * Fixtures: the harness's owned synthetic patient and its note. Cleanup deletes the note rows and the
 * audit rows scoped to the patient or the note id and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { auditProbe, phiLeaks, incomplete, label } = require('./lib/audit-log-helpers');

const isEntry = method => r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/CaseManagementEntry')
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const probe = auditProbe({ sql, patient });
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  const first = `${marker} audit write-path note`;
  const second = `${first} revised`;
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
    probe.cleanup();
  });
  const noteRows = (rows, action) => rows.filter(r => r.provider === provider && r.content === 'CME note' && (!action || r.action === action));
  let chart;
  let afterAdd = 0;

  await s.step('Save writes exactly one complete add/CME note row', async () => {
    chart = await s.chart();
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    await editor.click();
    await editor.fill(first);
    const [saved] = await Promise.all([chart.waitForResponse(isEntry('save')), chart.locator('#saveImg').first().click()]);
    h.assert(saved.status() === 200, `Saving the note answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id DESC LIMIT 1`, first,
      'The note text did not reach casemgmt_note');
    noteId = sql.value(`SELECT MAX(note_id) FROM casemgmt_note WHERE demographic_no=${patient}`);
    probe.own('CME note', noteId);
    await chart.waitForFunction(id => { const f = document.querySelector('input[name="noteId"]'); return f && f.value === id; }, noteId, { timeout: 30000 });
    const rows = await probe.waitFor(all => all.some(r => r.action === 'add' && r.content === 'CME note'), 'first note Save');
    const adds = noteRows(rows, 'add');
    // Audit-row mismatches are recorded, not thrown, so signing, reopening and the privacy check still run.
    expect(adds.length === 1, `The first Save wrote ${adds.length} add/CME note rows, expected 1`);
    const problems = incomplete(adds, { provider, patient });
    expect(!problems.length, `The add/CME note row is incomplete: ${problems.join(', ')}`);
    expect(adds.some(r => r.contentId === noteId), 'The add/CME note row does not name the saved note');
    afterAdd = probe.mark();
  });

  await s.step('a second Save of the revised text writes exactly one update/CME note row', async () => {
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    await editor.click();
    await editor.fill(second);
    const [saved] = await Promise.all([chart.waitForResponse(isEntry('save')), chart.locator('#saveImg').first().click()]);
    h.assert(saved.status() === 200, `Saving the revised note answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT note FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id DESC LIMIT 1`, second,
      'The revised note text did not reach casemgmt_note');
    const rows = await probe.waitFor(all => all.some(r => r.action === 'update' && r.content === 'CME note'), 'second note Save', { after: afterAdd });
    const updates = noteRows(rows, 'update');
    noteId = sql.value(`SELECT MAX(note_id) FROM casemgmt_note WHERE demographic_no=${patient}`);
    probe.own('CME note', noteId);
    expect(updates.length === 1, `The second Save wrote ${updates.length} update/CME note rows, expected 1`);
    const problems = incomplete(updates, { provider, patient });
    expect(!problems.length, `The update/CME note row is incomplete: ${problems.join(', ')}`);
    expect(updates.some(r => r.contentId === String(noteId)), 'The update/CME note row does not name the saved note');
  });

  await s.step('Sign & Save signs the note (audit rows observed)', async () => {
    const before = probe.mark();
    const closed = chart.waitForEvent('close', { timeout: 30000 }).then(() => true).catch(() => false);
    const [signed] = await Promise.all([chart.waitForResponse(isEntry('saveAndExit')), chart.locator('#signSaveImg').first().click()]);
    h.assert(signed.status() < 400, `Sign & Save answered HTTP ${signed.status()}`);
    h.assert(await closed, 'Sign & Save did not close the chart window');
    await expectValue(sql, `SELECT signed FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id DESC LIMIT 1`, '1',
      'The note was not signed');
    const rows = await probe.waitFor(all => all.some(r => r.content === 'CME note'), 'Sign & Save', { after: before });
    const written = noteRows(probe.since(before));
    const signedId = sql.value(`SELECT MAX(note_id) FROM casemgmt_note WHERE demographic_no=${patient}`);
    probe.own('CME note', signedId);
    expect(written.some(r => r.action === 'update' && r.contentId === signedId),
      `Sign & Save of a chart note wrote no update/CME note row naming the signed note (rows: ${written.map(label).join(', ') || 'none'})`);
    expect(rows.length > 0, 'Sign & Save wrote no audit row');
  });

  await s.step('observed: the E-Chart reopened after signing and the CME note rows it wrote', async () => {
    const before = probe.mark();
    chart = await s.chart();
    await probe.settle(3000);
    const rows = noteRows(probe.since(before));
    expect(!rows.length, `Opening the E-Chart wrote ${rows.length} CME note audit row(s) (${[...new Set(rows.map(label))].join(', ')}) though no note was changed`
      + (rows.some(r => r.contentId === 'null') ? '; the row names the note id as the text "null" (a new, unsaved note) and carries its generated text' : ''));
  });

  await s.step('observed: the note text in the audit rows of the note path', async () => {
    const leaks = phiLeaks(probe.rows(), [marker]);
    expect(!leaks.length, `The note text (clinical content) is stored in the audit log: ${leaks.join(', ')}`);
  });

  await s.step('every expectation of the note write path held', async () => {
    h.assert(!defects.length, `Audit-trail defects on the chart-note path:\n  - ${defects.join('\n  - ')}`);
  });
}

if (require.main === module) runWorkflow('audit-log-chart-note', workflow);
module.exports = { workflow };
