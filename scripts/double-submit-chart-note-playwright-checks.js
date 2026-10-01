#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: E-Chart progress note Sign & Save and Save (HIGH severity if it duplicates).
 *
 * User path: Schedule > Search > Master Record > E-Chart > type into the note > Sign & Save
 * (#signSaveImg) or Save (#saveImg). For each rapid activation (dblclick(), two back-to-back clicks,
 * slow-response re-click) the check types ONE note with its own marker text and asserts the owned
 * patient has EXACTLY ONE logical note (distinct casemgmt_note.uuid) carrying that marker and, for Sign
 * & Save, that it is signed. (A textarea swallows Enter as a newline, so there is no double-Enter mode.)
 *
 * Fixtures: the owned FAKE- patient; every casemgmt_note row for that patient carrying the run marker
 * (plus issue links, ext rows, drafts, locks) is deleted and asserted gone. Wave-6 pattern sweep
 * "double-submit".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer, sleep } = require('./lib/double-submit-helpers');

const NOTE = 'textarea[name="caseNote_note"]';

async function workflow(s) {
  const { sql, patient, marker } = s;
  const likeAll = h.sqlString(`%${marker}-%`);
  const noteIds = () => sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${likeAll}`).map(([id]) => id);
  const wipe = () => {
    const ids = noteIds();
    for (const id of ids) h.assert(/^[1-9]\d*$/.test(id), 'Owned note id is invalid');
    if (ids.length) {
      const list = ids.join(',');
      sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${list})`);
      sql.execute(`DELETE FROM casemgmt_note_ext WHERE note_id IN (${list})`);
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${list})`);
      sql.execute(`DELETE FROM casemgmt_note WHERE note_id IN (${list}) AND demographic_no=${patient}`);
    }
    sql.execute(`DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient}`);
    sql.execute(`DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient}`);
  };
  s.cleanup(() => {
    wipe();
    h.assert(noteIds().length === 0, 'Owned chart notes were not removed');
  });

  const v = verdicts('chart-note');
  const modes = MODES.filter((mode) => mode.key !== 'doubleEnter');

  for (const [verb, button, route] of [
    ['Sign & Save', '#signSaveImg', /\/CaseManagementEntry$/],
    ['Save', '#saveImg', /\/CaseManagementEntry$/],
  ]) {
    for (const mode of modes) {
      await s.step(`${verb} via ${mode.label} writes one note`, async () => {
        wipe();
        const chart = await s.chart();
        const note = chart.locator(NOTE).first();
        await note.waitFor({ state: 'visible', timeout: 30000 });
        await note.click();
        const text = `${marker}-${verb === 'Save' ? 'S' : 'G'}${mode.tag} double submit`;
        await note.fill(text);
        const posts = watchPosts(chart.context(), route);
        const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, route) : null;
        await rapid(mode.key, chart.locator(button).first());
        const like = h.sqlString(`%${text}%`);
        const count = await settledCount(sql,
          `SELECT COUNT(DISTINCT uuid) FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${like}`, { min: 1, quietMs: 3500 });
        if (disarm) await disarm();
        posts.stop();
        const rows = sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${like}`);
        const signed = sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${like} AND signed=1`);
        console.log(`    (${posts.seen.length} CaseManagementEntry POST(s); ${rows} row(s); ${signed} signed)`);
        v.record(`${verb} ${mode.label}`, count, { exactly: 1 });
        if (!chart.isClosed()) await chart.close().catch(() => {});
        await sleep(500);
      });
    }
  }
  wipe();
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-chart-note', workflow, { openPatient: true });
