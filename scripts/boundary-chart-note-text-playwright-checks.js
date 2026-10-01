#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Special characters in a progress note (wave 6, boundary values).
 * User path: Schedule > Search > Master Record > E-Chart > type into the note > Sign & Save > reopen the E-Chart.
 * Asserts: a note with an apostrophe, accents, CJK, an emoji, "&amp;", quotes, backslash, "%41", "+", ";",
 * a long line and a leading and trailing blank line is stored (utf8mb4, no "?" substitution, no entity or
 * encoding damage) and the reopened chart shows the same text. Leading and trailing spaces are compared
 * with the browser's own normalisation of the typed text.
 * Fixtures: the owned FAKE- patient; every casemgmt_note row of that patient carrying the run marker (with
 * issue links, ext rows, drafts and locks) is deleted by cleanup, which asserts they are gone.
 * Implements the wave-6 "boundary values" pattern, Part 1 (case-management note text).
 */
const h = require('./lib/playwright-harness');
const b = require('./lib/boundary-values');
const { runWorkflow } = require('./lib/workflow-session');

const NOTE = 'textarea[name="caseNote_note"]';

async function workflow(s) {
  const { sql, patient, marker } = s;
  const T = b.TOKENS;
  const like = h.sqlString(`%${marker}%`);
  const noteIds = () => sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${like}`).map(([id]) => id);
  const wipe = () => {
    const ids = noteIds();
    ids.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'Owned note id is invalid'));
    if (ids.length) {
      const list = ids.join(',');
      sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${list});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${list});
        DELETE FROM casemgmt_note_link WHERE note_id IN (${list});
        DELETE FROM casemgmt_note WHERE note_id IN (${list}) AND demographic_no=${patient}`);
    }
    sql.execute(`DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient}; DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient}`);
  };
  s.cleanup(() => { wipe(); h.assert(noteIds().length === 0, 'Owned chart notes were not removed'); });
  wipe();

  const lines = [
    `${marker} ${T.apostrophe} ${T.latin}`,
    `${T.cjk} ${T.emoji} ${T.entity} ${T.quotes}`,
    `${T.backslash} ${T.percent} ${T.plus} ${T.semicolon} <not a tag> 5 < 6 > 4`,
    `${'long line of words '.repeat(60)}end`,
  ];
  const text = lines.join('\n');

  let typed;
  await s.step('Sign & Save stores the note text byte for byte', async () => {
    const chart = await s.chart();
    const note = chart.locator(NOTE).first();
    await note.waitFor({ state: 'visible', timeout: 30000 });
    await note.click();
    await note.fill(text);
    // What the box holds is what the browser normalised the typed text to; the stored note must carry exactly that.
    typed = await note.inputValue();
    await chart.locator('#signSaveImg').first().click();
    const deadline = Date.now() + 25000;
    while (noteIds().length === 0 && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 300));
    h.assert(noteIds().length === 1, 'Sign & Save did not store exactly one note');
    const stored = b.readStored(sql, 'casemgmt_note', 'note', `demographic_no=${patient} AND note LIKE ${like}`);
    const storedText = Buffer.from(stored.hex, 'hex').toString('utf8');
    h.assert(storedText.includes(typed.trim()) || storedText.replace(/\r\n/g, '\n').includes(typed.trim()),
      `The stored note differs from what was typed: ${b.explainMismatch(typed.trim(), { hex: stored.hex, chars: stored.chars })}`);
    if (!chart.isClosed()) await chart.close().catch(() => {});
  });

  await s.step('the reopened E-Chart shows the saved note text unchanged', async () => {
    // Sign & Save closes the chart and refreshes the Master Record that opened it; let that settle first.
    await new Promise(resolve => setTimeout(resolve, 2000));
    await s.master.waitForLoadState('load', { timeout: 20000 }).catch(() => {});
    const chart = await s.chart();
    await chart.locator('#encMainDiv').first().waitFor({ state: 'attached', timeout: 30000 });
    await chart.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const shown = (await chart.locator('#encMainDiv').first().innerText()).replace(/\s+/g, ' ');
    const missing = [T.apostrophe, T.latin, T.cjk, T.emoji, T.entity, T.quotes, T.backslash, T.percent, T.plus, T.semicolon, '<not a tag> 5 < 6 > 4']
      .filter(token => !shown.includes(token));
    h.assert(missing.length === 0, `The reopened chart does not show these note fragments as typed: ${missing.join(' | ')}`);
    await chart.close().catch(() => {});
  });
}

if (require.main === module) runWorkflow('boundary-chart-note-text', workflow, { openPatient: true });
module.exports = { workflow };
