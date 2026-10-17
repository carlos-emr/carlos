#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, chart notes written through CaseManagementEntry. User paths:
 *   (a) Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Social History "+" (Add Item) ▸
 *       type ▸ Sign & Save  -> POST CaseManagementEntry?method=issueNoteSave
 *   (b) Master Record ▸ Tickler (tickler/ViewTicklerMain for the patient) ▸ row note icon ▸
 *       type ▸ Save         -> CaseManagementEntry?method=ticklerSaveNote
 *   (c) the patient's tickler list (tickler/ViewTicklerDemoMain, the popup the document
 *       viewer's and the lab display's "open ticklers" alert opens) ▸ row note icon ▸ type ▸
 *       Save                -> CaseManagementEntry?method=ticklerSaveNote
 *
 * The CaseManagementEntry methods that change a note, an issue, a draft or a note lock accept
 * only POST (CaseManagementEntry2Action.POST_ONLY_METHODS); opening, reading, printing and
 * restoring the chart stay GET. Each positive save is driven by the page and asserted in
 * casemgmt_note; the captured request is replayed as GET/HEAD with a new stamped text and must
 * answer 405 and write nothing. The other save method names are probed by GET/HEAD too. The
 * dialogs' own verb for (b) and (c) and every probe are asserted in the LAST step.
 *
 * Fixtures: the owned synthetic patient (its chart lock is taken by opening the E-Chart) and
 * two marker ticklers; cleanup deletes the patient's notes, note links/exts/issue rows, drafts,
 * CPP and legacy eChart rows and the ticklers, and asserts they are gone.
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
  const demoMessage = `${marker} get-reject patient list tickler`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
      DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient};
      DELETE FROM tickler WHERE demographic_no=${patient} AND message IN (${q(message)}, ${q(demoMessage)})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient})`) === '0', 'Owned notes/issues/drafts/ticklers were not removed');
  });
  const stamped = text => `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND LOCATE(${q(text)}, note) > 0`;
  let cpp;
  let tickler;
  let demoTickler;
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
    // The dialog posts, so `method` is in the body: match the route, then check the captured params.
    tickler = await captureRequest(list, url => url.pathname.endsWith('/CaseManagementEntry'),
      () => list.locator('#note-form button.btn-primary').click(), { method: null });
    h.assert(tickler.params.get('method') === 'ticklerSaveNote', `The dialog sent method=${tickler.params.get('method')}`);
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

  const demoTicklerNo = sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
    VALUES(${patient},${q(demoMessage)},'A',NOW(),DATE_SUB(CURDATE(),INTERVAL 1 DAY),${q(provider)},'Normal',${q(provider)});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(demoTicklerNo), 'The second owned tickler was not created');
  const demoText = `${marker} patient list tickler note`;

  await s.step('patient tickler list (tickler/ViewTicklerDemoMain) ▸ note icon ▸ Save stores a note linked to its tickler', async () => {
    // The document viewer's and the lab display's "open ticklers" alert opens this URL in a popup.
    const list = await s.context.newPage();
    await list.goto(h.appUrl(s.config.baseUrl, `/tickler/ViewTicklerDemoMain?demoview=${patient}`));
    // Both dialog requests are synchronous XHRs; start only once the page's scripts (CSRFGuard
    // included) have finished loading, or a click can stall behind them.
    await list.waitForLoadState('networkidle');
    await list.locator(`a[onclick*="openNoteDialog('${patient}','${demoTicklerNo}')"]`).click();
    await list.locator('#note-form').waitFor({ state: 'visible' });
    await list.locator('#tickler_note').waitFor({ state: 'visible' });
    await list.locator('#tickler_note').fill(demoText);
    demoTickler = await captureRequest(list, url => url.pathname.endsWith('/CaseManagementEntry'),
      () => list.locator('#note-form a[onclick^="saveNoteDialog"]').click(), { method: null });
    h.assert(demoTickler.params.get('method') === 'ticklerSaveNote', `The dialog sent method=${demoTickler.params.get('method')}`);
    await expectValue(sql, stamped(demoText), '1', 'The patient tickler list note dialog did not store the note');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_link l JOIN casemgmt_note n ON n.note_id=l.note_id
      WHERE l.table_id=${demoTicklerNo} AND LOCATE(${q(demoText)}, n.note) > 0`) === '1', 'The note is not linked to its tickler');
    await list.close();
  });

  await s.step('the other note, issue, draft and lock saves are probed by GET/HEAD', async () => {
    // Owned rows only: the patient's notes, issues, drafts and note locks.
    const owned = () => sql.value(`SELECT CONCAT_WS('/',
      (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}),
      (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient}),
      (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient}),
      (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient}))`);
    for (const method of ['save', 'saveAndExit', 'autosave', 'cancel', 'ajaxsave', 'issueNoteSaveJson',
      'isNoteEdited', 'updateNoteLock', 'releaseNoteLock', 'makeIssue', 'issueAdd',
      'submitChangeDiagnosis', 'ajaxChangeDiagnosis', 'issueChange', 'issueDelete']) {
      await ledger.probe(s, { label: `CaseManagementEntry?method=${method}`, path: cpp.path,
        params: new URLSearchParams({ method, demographicNo: patient, noteId: '0', ticklerNo: ticklerNo,
          value: `${marker} ${method} by GET`, caseNote_note: `${marker} ${method} by GET` }),
        snapshot: owned });
    }
  });

  await s.step('chart-note writes refuse GET/HEAD, and both tickler note dialogs save by POST', async () => {
    const problems = [];
    try { ledger.assertAllRefused(); } catch (error) { problems.push(error.message); }
    for (const [page, sent] of [['ticklerMain.jsp', tickler], ['ticklerDemoMain.jsp', demoTickler]]) {
      if (sent.method !== 'POST') problems.push(`the ${page} note dialog saved the note with a ${sent.method}`);
    }
    h.assert(!problems.length, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
