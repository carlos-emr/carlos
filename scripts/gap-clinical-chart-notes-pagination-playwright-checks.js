#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Gap check (clinical): a long chart -- does the E-Chart show every note exactly once, newest page
 * first, whichever way the clinician pages through it?
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart (first page of notes) ▸ scroll the note pane
 * to the top (older notes load) ▸ "Load All Notes" ▸ "Collapse loaded notes" ▸ "Expand loaded notes"
 * (the note-control-panel under the editor; CaseManagementView?method=view paging via
 * ChartNotesAjax.jsp, 20 notes a page).
 * No check opens a chart with more than one page of notes, so the paging path (the scroll poll, the
 * offset arithmetic, the zero-note final batch, Load All) is unexercised, and a missing or doubled note
 * there is invisible: the page renders and the clinician simply does not see an old note.
 *
 * Asserts, on a patient with 45 signed notes dated 10 days apart: the first render shows exactly the 20
 * newest, oldest-to-newest, none of the older ones; scrolling the pane to the top pages the older ones in
 * until all 45 are present exactly once and still in date order; on a fresh chart Load All Notes shows
 * all 45 once each in order; Collapse shortens the multi-line owned note and Expand restores it; and (last,
 * because it fails) the paging bookkeeping the print dialog relies on stays valid: maxNcId must still
 * cover the loaded notes after the final, empty batch (ChartNotesAjax.jsp:856 resets it to 0, which
 * makes the print dialog's Clear a no-op -- see gap-clinical-chart-print-scope).
 *
 * Fixtures: the owned FAKE-PW patient with 45 SQL-seeded notes (token FAKE-PW<hex> NOTEnn); every note,
 * eChart and note-support row of the patient is deleted in cleanup and asserted gone.
 * Coverage plan §2.5 echart-notes (gap-clinical).
 */
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

const TOTAL = 45;
const PAGE = 20;
const pad = n => String(n).padStart(2, '0');

async function workflow(s) {
  const {sql, patient, provider, marker} = s;
  const q = h.sqlString;
  s.cleanup(() => {
    const owned = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned notes were not removed');
  });
  const program = sql.value(`SELECT id FROM program WHERE name='OSCAR' ORDER BY id LIMIT 1`);
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)} AND program_id=${program || 0} LIMIT 1`);
  h.assert(program && role, 'The default OSCAR program or the test provider\'s role in it is missing');
  const longBody = Array.from({length: 14}, (_, i) => `line ${i + 1} of the newest note`).join('\n');
  const values = [];
  for (let i = 1; i <= TOTAL; i += 1) {
    const text = i === 1 ? `${marker} NOTE${pad(i)}\n${longBody}` : `${marker} NOTE${pad(i)}`;
    values.push(`(NOW(),DATE_SUB(NOW(), INTERVAL ${i * 10} DAY),${patient},${q(provider)},${q(text)},1,${q(provider)},'',${q(program)},${q(role)},'x',UUID(),'0',0)`);
  }
  sql.execute(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,
    encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived) VALUES ${values.join(',')}`);

  /** The owned notes the pane shows, in display order, as numbers (NOTE07 -> 7). */
  const shown = chart => chart.evaluate(m => {
    const text = document.getElementById('encMainDiv').innerText;
    return (text.match(new RegExp(`${m} NOTE\\d\\d`, 'g')) || []).map(x => Number(x.slice(-2)));
  }, marker);
  const range = (from, to) => Array.from({length: to - from + 1}, (_, i) => from + i);
  const descending = (hi, lo) => range(lo, hi).reverse(); // oldest (highest number) first, as the pane lists them

  let chart = await s.chart();
  await s.step('the first render shows exactly the 20 newest notes, oldest-to-newest', async () => {
    await chart.waitForFunction(m => (document.getElementById('encMainDiv').innerText.match(new RegExp(`${m} NOTE\\d\\d`, 'g')) || []).length >= 20,
      marker, {timeout: 20000});
    await chart.waitForTimeout(2500);
    const notes = await shown(chart);
    h.assert(JSON.stringify(notes) === JSON.stringify(descending(PAGE, 1)),
      `The first page should list NOTE20 down to NOTE01 once each, it lists ${JSON.stringify(notes)}`);
  });

  await s.step('scrolling to the top pages the older notes in until all 45 are present once, in date order', async () => {
    await chart.waitForFunction(({m, total}) => {
      document.getElementById('encMainDivWrapper').scrollTop = 0;
      return (document.getElementById('encMainDiv').innerText.match(new RegExp(`${m} NOTE\\d\\d`, 'g')) || []).length >= total;
    }, {m: marker, total: TOTAL}, {timeout: 40000, polling: 1000})
      .catch(() => { throw new Error('Scrolling to the top did not page in all 45 notes within 40 s'); });
    // The pane keeps asking for older notes while the scroll is at the top; the batch that comes back
    // empty (notesLastBatchSize === 0) is the last one, and only after it has rendered is maxNcId
    // final. Wait for it rather than for a fixed pause, then read the bookkeeping. If no empty batch is
    // ever requested the wait times out quietly and maxNcId is read as it stands.
    await chart.waitForFunction(() => {
      document.getElementById('encMainDivWrapper').scrollTop = 0;
      return window.notesLastBatchSize === 0;
    }, null, {timeout: 15000, polling: 500}).catch(() => {});
    const notes = await shown(chart);
    h.assert(JSON.stringify(notes) === JSON.stringify(descending(TOTAL, 1)),
      `After paging the pane should list NOTE45 down to NOTE01 once each, it lists ${notes.length} notes: ${JSON.stringify(notes)}`);
  });

  const maxNcId = () => chart.evaluate(() => maxNcId);
  const afterPaging = await maxNcId();

  await s.step('on a fresh chart Load All Notes shows all 45 once each, in order', async () => {
    await chart.close();
    chart = await s.chart();
    await chart.waitForFunction(m => (document.getElementById('encMainDiv').innerText.match(new RegExp(`${m} NOTE\\d\\d`, 'g')) || []).length >= 20,
      marker, {timeout: 20000});
    await chart.locator('#note-control-panel button', {hasText: /load all/i}).click();
    await chart.waitForFunction(({m, total}) => (document.getElementById('encMainDiv').innerText.match(new RegExp(`${m} NOTE\\d\\d`, 'g')) || []).length >= total,
      {m: marker, total: TOTAL}, {timeout: 30000})
      .catch(() => { throw new Error('Load All Notes did not show all 45 notes within 30 s'); });
    await chart.waitForTimeout(1500);
    const notes = await shown(chart);
    h.assert(JSON.stringify(notes) === JSON.stringify(descending(TOTAL, 1)),
      `Load All should list NOTE45 down to NOTE01 once each, it lists ${notes.length} notes`);
  });

  await s.step('Collapse loaded notes shortens the long note and Expand loaded notes restores it', async () => {
    const heightOfLong = () => chart.evaluate(m => {
      const el = Array.from(document.querySelectorAll('#encMainDiv .note-contents')).find(e => e.innerText.includes(`${m} NOTE01`));
      return el ? el.offsetHeight : -1;
    }, marker);
    const open = await heightOfLong();
    h.assert(open > 100, `The long note renders ${open}px tall; the fixture needs it to be multi-line`);
    await chart.locator('#note-control-panel button', {hasText: /collapse/i}).click();
    await chart.waitForFunction(({m, was}) => {
      const el = Array.from(document.querySelectorAll('#encMainDiv .note-contents')).find(e => e.innerText.includes(`${m} NOTE01`));
      return el && el.offsetHeight < was;
    }, {m: marker, was: open}, {timeout: 8000}).catch(() => { throw new Error('Collapse loaded notes did not shorten the long note'); });
    await chart.locator('#note-control-panel button', {hasText: /expand/i}).click();
    await chart.waitForFunction(({m, was}) => {
      const el = Array.from(document.querySelectorAll('#encMainDiv .note-contents')).find(e => e.innerText.includes(`${m} NOTE01`));
      return el && el.offsetHeight >= was - 5;
    }, {m: marker, was: open}, {timeout: 8000}).catch(() => { throw new Error('Expand loaded notes did not restore the long note'); });
  });

  await s.step('the paging bookkeeping still covers the loaded notes after the final empty batch (maxNcId)', async () => {
    h.assert(afterPaging > 0 && await maxNcId() > 0,
      `maxNcId is ${afterPaging} with ${TOTAL} notes on the page: the last empty paging batch reset it (ChartNotesAjax.jsp sets `
      + 'maxNcId from the batch it rendered), so every nc1..maxNcId loop in the chart -- Clear in the print dialog -- finds no notes');
  });
}

if (require.main === module) runWorkflow('gap-clinical-chart-notes-pagination', workflow, {openPatient: true});
module.exports = {workflow};
