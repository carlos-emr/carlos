#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, chart notes written through CaseManagementEntry. User paths:
 *   (a) Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Social History "+" (Add Item) ▸
 *       type ▸ Sign & Save  -> POST CaseManagementEntry?method=issueNoteSave
 *   (b) Master Record ▸ Tickler (tickler/ViewTicklerMain for the patient) ▸ row note icon ▸
 *       type ▸ Save         -> CaseManagementEntry?method=ticklerSaveNote
 *
 * CaseManagementEntry2Action has no POST check for any of its ~30 `method=` branches and
 * checks only _demographic READ before dispatching; HttpMethodGuardFilter recognises only
 * method=save/cancel there (exact match), so issueNoteSave, ticklerSaveNote, ajaxsave,
 * saveAndExit, autosave, issueAdd/issueDelete etc. all run on a GET. (b) is worse: the page's
 * own saveNoteDialog() calls jQuery.ajax without `type`, i.e. the UI itself writes the signed
 * note with a GET. Each positive save is driven by the page and asserted in casemgmt_note;
 * the captured request is replayed as GET/HEAD with a new stamped text and must answer 405
 * and write nothing. The UI's own verb for (b) and every probe are asserted in the LAST step.
 *
 * Fixtures: the owned synthetic patient (its chart lock is taken by opening the E-Chart) and
 * one marker tickler; cleanup deletes the patient's notes, note links/exts/issue rows, CPP and
 * legacy eChart rows and the tickler, and asserts they are gone.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger } = require('./lib/get-reject-probe');

const NAME = 'get-reject-chart-notes';

/** Replay `params` with every occurrence of `from` in a value replaced by `to`. */
function substitute(params, from, to) {
  const out = new URLSearchParams();
  for (const [k, v] of params) out.append(k, v.split(from).join(to));
  return out;
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
  const message = `${marker} get-reject tickler`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient};
      DELETE FROM tickler WHERE demographic_no=${patient} AND message=${q(message)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient})`) === '0', 'Owned notes/issues/tickler were not removed');
  });
  const stamped = text => `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND LOCATE(${q(text)}, note) > 0`;
  let cpp;
  let tickler;
  const cppText = `${marker} CPP social history item`;

  await s.step('E-Chart ▸ Social History ▸ Add Item ▸ Sign & Save stores the item (POST method=issueNoteSave)', async () => {
    const chart = await s.chart();
    const box = chart.locator('#divR1I1');
    await box.waitFor({ state: 'visible' });
    await box.locator('a[title="Add Item"]').first().click();
    await chart.locator('#showEditNote').waitFor({ state: 'visible' });
    await chart.locator('#noteEditTxt').fill(cppText);
    let button = null;
    for (const candidate of await chart.locator('#showEditNote input[type="image"]').all()) {
      if (/sign|save/i.test(await candidate.getAttribute('title') || '')) { button = candidate; break; }
    }
    h.assert(button, 'The CPP item editor offers no Sign/Save button');
    cpp = await captureRequest(chart, url => url.pathname.endsWith('/CaseManagementEntry')
      && url.searchParams.get('method') === 'issueNoteSave', () => button.click());
    h.assert(cpp.status > 0 && cpp.status < 400, `issueNoteSave answered ${cpp.status}`);
    await expectValue(sql, stamped(cppText), '1', 'Sign & Save did not store the Social History item');
  });

  await s.step('the issueNoteSave replayed as GET/HEAD with a new text is recorded', async () => {
    const probeText = `${marker} CPP replayed by GET`;
    await ledger.probe(s, { label: 'CaseManagementEntry?method=issueNoteSave', path: cpp.path,
      // reloadUrl carries the defining issue_code the action parses; its embedded "&cmd=<div>"
      // repaint hint trips the WAF's CRS 932110 on a query string, which would hide what the
      // APPLICATION does with the GET, so the replay keeps reloadUrl without that segment.
      params: replayParams(substitute(cpp.params, cppText, probeText), {
        reloadUrl: (cpp.params.get('reloadUrl') || '').split('&').filter(part => !part.startsWith('cmd=')).join('&') }),
      snapshot: () => sql.value(stamped(probeText)) });
  });

  const ticklerNo = sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
    VALUES(${patient},${q(message)},'A',NOW(),DATE_SUB(CURDATE(),INTERVAL 1 DAY),${q(provider)},'Normal',${q(provider)});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(ticklerNo), 'The owned tickler was not created');
  const ticklerText = `${marker} tickler note`;

  await s.step('Master Record ▸ Tickler ▸ note icon ▸ Save stores a signed note linked to the owned tickler', async () => {
    const list = await s.popup(s.master, s.master.locator('a[onclick*="/tickler/ViewTicklerMain"]').first(), 'tickler-list');
    const row = list.locator('#ticklerResults tbody tr').filter({ hasText: message }).first();
    await row.waitFor({ state: 'visible' });
    await row.locator('a.noteDialogLink').click();
    await list.locator('#tickler_note').waitFor({ state: 'visible' });
    await list.locator('#tickler_note').fill(ticklerText);
    tickler = await captureRequest(list, url => url.pathname.endsWith('/CaseManagementEntry')
      && url.searchParams.get('method') === 'ticklerSaveNote', () => list.locator('#note-form button.btn-primary').click(),
    { method: null });
    await expectValue(sql, stamped(ticklerText), '1', 'The note dialog did not store the tickler note');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_link l JOIN casemgmt_note n ON n.note_id=l.note_id
      WHERE l.table_id=${ticklerNo} AND LOCATE(${q(ticklerText)}, n.note) > 0`) === '1', 'The tickler note is not linked to the tickler');
    await list.close();
  });

  await s.step('the ticklerSaveNote replayed as GET/HEAD with a new text is recorded', async () => {
    const probeText = `${marker} tickler note replayed by GET`;
    await ledger.probe(s, { label: 'CaseManagementEntry?method=ticklerSaveNote', path: tickler.path,
      params: replayParams(substitute(tickler.params, ticklerText, probeText)), snapshot: () => sql.value(stamped(probeText)) });
  });

  await s.step('chart-note writes refuse GET/HEAD, and the tickler note dialog itself saves by POST', async () => {
    const problems = [];
    try { ledger.assertAllRefused(); } catch (error) { problems.push(error.message); }
    if (tickler.method !== 'POST') {
      problems.push(`the tickler note dialog itself saved the note with a ${tickler.method} (ticklerMain.jsp saveNoteDialog() `
        + 'jQuery.ajax has no type), so the write cannot be made POST-only without fixing the page');
    }
    h.assert(!problems.length, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
