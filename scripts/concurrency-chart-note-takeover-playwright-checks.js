#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check: two windows of the same login write the same patient's chart note.
 *
 * User path (both sessions of the shared test login): Schedule > Search > Master Record > E-Chart >
 * type in the note editor > Save. Session A opens the chart (it holds the note lock). Session B opens
 * the same chart: the chart asks "You have started to edit this note in another window ... Do you wish
 * to continue?" and B accepts, taking the lock over. B types its note and saves. Session A, which has
 * lost the lock, then types its own text and clicks Save.
 *
 * Asserted: the explicit takeover is offered exactly once (control); B's note is stored; A's text is NOT written to the chart or over B's note;
 * and A is told, with its typed text still in the editor.
 * A's save writes nothing over B's note (control). The check then fails at its last step: A's Save posts
 * the whole form through saveNoteAjax('save'), which ignores lostNoteLock; the server answers 200 with the
 * "did not have the exclusive note lock" page and the chart replaces its notes pane with it, so the editor
 * and the typed clinical text vanish with no alert (the ajaxsave path alerts and keeps the editor).
 *
 * Fixtures: the owned FAKE- patient only; the notes, drafts and locks the two windows leave are removed
 * by the session cleanup (the harness deletes casemgmt_note_lock / casemgmt_tmpsave for the patient)
 * and the note rows by id, asserted gone. Wave-7 sweep "concurrency".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openSecondSession, failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const TAKEOVER = /^You have started to edit this note in another window at [^\n]+\.\nDo you wish to continue\?$/;
const isEntry = method => response => response.request().method() === 'POST'
  && h.pathOnly(response.url()).endsWith('/CaseManagementEntry')
  && new URLSearchParams(response.request().postData() || '').get('method') === method;

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => {
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(notes.every(id => /^[1-9]\d*$/.test(id)), 'Owned note id is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned notes were not removed');
  });
  const textA = `${marker} note typed in the stale window`;
  const textB = `${marker} note typed in the window that took the lock`;
  const notesWith = text => `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND note=${h.sqlString(text)}`;
  const editorOf = chart => chart.locator('#encMainDiv textarea[name="caseNote_note"]');
  let takeovers = 0;
  let bChart;

  const aChart = await s.chart();
  await editorOf(aChart).waitFor({ state: 'visible', timeout: 20000 });

  await s.step('session B opens the same chart and takes the note lock over with the explicit confirmation', async () => {
    const b = await openSecondSession(s, {
      label: 'second-session',
      async dialogHandler(dialog, entry) {
        if (dialog.type() === 'confirm' && TAKEOVER.test(dialog.message())) { takeovers++; await dialog.accept(); return; }
        s.recorder.unexpectedDialogs.push(entry);
        await dialog.dismiss();
      },
    });
    bChart = await openChart(b.context, b.master, s.recorder, 20000);
    await waitForNavbars(bChart, 20000);
    h.assert(takeovers === 1, `Expected exactly one takeover confirmation, saw ${takeovers}`);
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient}`, '1', 'The takeover left more than one lock row');
  });
  await s.step('session B types and saves its note', async () => {
    const editor = editorOf(bChart);
    await editor.click();
    await editor.fill(textB);
    const [saved] = await Promise.all([bChart.waitForResponse(isEntry('save'), { timeout: 30000 }), bChart.locator('#saveImg').first().click()]);
    h.assert(saved.status() === 200, `Session B's save answered HTTP ${saved.status()}`);
    await expectValue(sql, notesWith(textB), '1', 'Session B\'s note did not reach casemgmt_note');
  });
  let alerts;
  await s.step('session A, which lost the lock, clicks Save: nothing is written over session B\'s note', async () => {
    const editor = editorOf(aChart);
    await editor.click();
    await editor.fill(textA);
    const mark = failureMark(s.recorder);
    alerts = await h.withExpectedDialogs(aChart, async () => {
      const [response] = await Promise.all([aChart.waitForResponse(isEntry('save'), { timeout: 30000 }), aChart.locator('#saveImg').first().click()]);
      h.assert(response.status() === 409, `The stale window's save answered HTTP ${response.status()}`);
      await aChart.waitForTimeout(1500); // the page applies the response (and any alert) after it arrives
    });
    consumeExpectedFailure(s.recorder, mark, { status: 409, path: /\/CaseManagementEntry$/ });
    h.assert(alerts.length === 1 && alerts[0].type === 'alert' && /note lock.*lost/i.test(alerts[0].text),
      'The stale save must warn exactly once that the note lock was lost');
    h.assert(sql.value(notesWith(textA)) === '0', 'The stale window\'s text was written to the chart');
    h.assert(sql.value(notesWith(textB)) === '1', 'Session B\'s note is no longer the stored note');
  });
  await s.step('session A is told its note lock was lost and its typed text is still in the editor', async () => {
    const editor = editorOf(aChart);
    const kept = await editor.count() > 0 && await editor.inputValue() === textA;
    const body = await aChart.locator('body').innerText();
    h.assert(kept,
      `The stale window's Save (saveNoteAjax('save') -> CaseManagementEntry method=save, HTTP 200 "windowCloseError" page) replaced the chart's notes area with `
      + `${/exclusive note lock/.test(body) ? 'the text "Your note was not saved as you did not have the exclusive note lock"' : 'an error page'}; the note editor and the `
      + `clinical text typed into it are gone (${alerts.length} alert(s) raised). The ajaxsave path (409 + noteLockLostError alert, editor kept) and the autosave `
      + 'indicator handle a lost lock; this Save path ignores lostNoteLock and treats the refusal page as the new notes pane.');
    // Keeping the editor is not enough: the user must also be told the lock was lost, by the noteLockLostError alert or a visible message.
    const told = alerts.some(alert => /lock/i.test(alert.text)) || /note lock/i.test(body);
    h.assert(told, 'The stale window kept its editor but gave the user no alert or visible message that the note lock was lost');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-chart-note-takeover', workflow, { openPatient: true });
