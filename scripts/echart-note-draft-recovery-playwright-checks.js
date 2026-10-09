#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * An unsaved E-Chart note survives leaving the chart: the Exit confirm, the autosaved draft, its
 * recovery on reopen (also after the window is killed), and nobody else ever sees it.
 *
 * WHY THIS CHECK EXISTS. Typing into a chart note is the most-used action in the product, and the only
 * thing between a clinician and a lost note is a five-second timer. echart-note-editor proves the
 * autosave POST is accepted (CSRF header, HTTP 200, a casemgmt_tmpsave row). It never leaves the chart,
 * so nothing proves what the clinician is promised: that closing the chart does not lose the text, that
 * the draft comes back when the chart is opened again, that it still comes back when the window was
 * killed rather than closed (browser crash, laptop lid, a closed tab with no unload handlers run), and
 * that a draft written under one provider's name is never shown to another provider who opens the same
 * patient's chart. A draft is clinical text; leaking it across providers is a privacy defect.
 *
 * User path: Schedule > Search > Master Record > E-Chart. Click into the note and type. Exit (the chart
 * toolbar's Exit button, a window.confirm from closeEnc). Open the chart again. Kill the window. Open the
 * chart again. Save.
 *
 * Asserted, in order, on an owned FAKE patient:
 *   1. Typing autosaves, and the autosave POST (CaseManagementEntry method=autosave, the draft text in
 *      its body) answers 200 and leaves exactly one casemgmt_tmpsave row for this patient and provider
 *      holding the typed text (the harness waits for the POST; there is no fixed sleep).
 *   2. Dismissing Exit's confirm keeps the window, the text in the editor and the draft row.
 *   3. Accepting it closes the window, releases the note lock and leaves exactly one draft row holding
 *      the typed text.
 *   4. A second provider (a throwaway login of this run, with the test provider's program membership so
 *      the same patient's chart opens for it) gets a working editor that does not hold the draft, a page
 *      that does not contain the draft text anywhere, its own separate draft when it types, and leaves
 *      the first provider's draft row untouched.
 *   5. Opening the chart again restores the draft into the editor.
 *   6. A window of the same provider (its own login) restores the draft, extends it, autosaves, and is
 *      killed: its note lock is not released (the page's release flag is cleared, as echart-lock-
 *      lifecycle does, because Playwright's context.close() otherwise still runs the unload beacon),
 *      and the whole browser context is closed with no Exit, no beforeunload and no confirm. A new login
 *      of the same provider opens the chart, is asked whether to continue editing a note "in another
 *      window" (the stale lock; the check accepts, as a clinician would), and gets the extended draft
 *      back with the lock moved to its own session.
 *   7. Save writes the casemgmt_note row, unsigned, holding the recovered text, and removes the draft.
 *
 * Fixtures: the workflow's owned FAKE patient; a throwaway provider login with a copy of the test
 * provider's program_provider and provider_default_program rows (lib/throwaway-login-fixture.js). The
 * saved note and its link/extension rows, both providers' drafts, every note lock of the patient, and
 * the throwaway are removed by key and asserted gone. No clinic-wide state is changed.
 *
 * Expected: PASS (no known defect).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { openSecondSession, failureMark } = require('./lib/concurrency-support');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const TIMEOUT = 20000;
const NOTE = '#caseManagementEntryForm textarea[name="caseNote_note"]';
/** backup() fires 5 s after the last timer tick when the note changed; allow for a slow page. */
const AUTOSAVE_WAIT_MS = 30000;
const TAKEOVER_PROMPT = /^You have started to edit this note in another window at [^\n]+\.\nDo you wish to continue\?$/;

/** The draft autosave the note editor POSTs after typing. */
function isAutosave(request) {
  return request.method() === 'POST' && /\/CaseManagementEntry(\?|$)/.test(request.url())
    && new URLSearchParams(request.postData() || '').get('method') === 'autosave';
}
const autosavedNote = request => new URLSearchParams(request.postData() || '').get('note') || '';
const normalise = text => text.replace(/\r\n/g, '\n').trim();

/**
 * A chart window this check closes on purpose sends the note-lock release as a beacon from its pagehide
 * handler, and Chromium reports that request as an aborted "ping" once its document is gone. That is the
 * browser abandoning a load, not an application failure: the beacon is proven delivered by the lock row
 * going away, which the steps assert. Excuse exactly those entries recorded since `mark` (a ping to
 * CaseManagementEntry, ERR_ABORTED, whose issuing document went away); every other failure stays a finding.
 */
function consumeUnloadBeacons(recorder, mark) {
  for (let i = recorder.requestFailures.length - 1; i >= mark.failures; i--) {
    const entry = recorder.requestFailures[i];
    if (entry.resourceType === 'ping' && /ERR_ABORTED/.test(entry.errorText || '')
        && new URL(entry.url).pathname.endsWith('/CaseManagementEntry')
        && typeof entry.navigatedAway === 'function' && entry.navigatedAway()) {
      recorder.requestFailures.splice(i, 1);
    }
  }
}

async function workflow(s) {
  const { sql, patient, marker, provider, config } = s;
  const q = h.sqlString;
  const textOne = `${marker} first draft line`;
  const textTwo = `${marker} second draft line`;
  const secondProviderText = `${marker} second provider own line`;

  // ---- Fixtures --------------------------------------------------------------------------------
  const second = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => second.cleanup());
  second.create();
  // The throwaway holds the test provider's program memberships, so the same patient's chart is open to it.
  s.cleanup(() => {
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(second.providerNo)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(second.providerNo)}`) === '0',
      'The throwaway default program was not removed');
  });
  sql.execute(`INSERT INTO program_provider (program_id,provider_no,role_id,team_id)
    SELECT program_id,${q(second.providerNo)},role_id,team_id FROM program_provider WHERE provider_no=${q(provider)}`);
  sql.execute(`INSERT INTO provider_default_program (provider_no,program_id,signnote)
    SELECT ${q(second.providerNo)},program_id,signnote FROM provider_default_program WHERE provider_no=${q(provider)}`);
  h.assert(Number(sql.value(`SELECT COUNT(*) FROM program_provider WHERE provider_no=${q(second.providerNo)}`)) > 0,
    'The throwaway received no program membership, so it could not open the patient\'s chart');
  // The patient is owned by this run, so every row below is the run's; deleted by the patient key.
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
      DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient})`) === '0', 'Owned chart rows were not removed');
  });

  // ---- Database helpers (the patient and the provider are always part of the key) ---------------
  const draftsOf = who => `FROM casemgmt_tmpsave WHERE demographic_no=${patient} AND provider_no=${q(who)}`;
  /** "<rows>/<rows holding the text>" so one poll asserts both "exactly one" and "holding it". */
  const draftShape = (who, text) => sql.value(
    `SELECT CONCAT(COUNT(*), '/', COALESCE(SUM(LOCATE(${q(text)}, note) > 0), 0)) ${draftsOf(who)}`);
  const draftIds = who => sql.value(`SELECT COALESCE(GROUP_CONCAT(id ORDER BY id), '') ${draftsOf(who)}`);
  const locksOf = who => sql.value(
    `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no=${q(who)}`);
  const expectDraft = (who, text, message) => expectValue(sql, `SELECT CONCAT(COUNT(*), '/',
    COALESCE(SUM(LOCATE(${q(text)}, note) > 0), 0)) ${draftsOf(who)}`, '1/1', message);

  // ---- Page helpers ----------------------------------------------------------------------------
  const noteOf = async chart => {
    const note = chart.locator(NOTE).first();
    await note.waitFor({ state: 'visible', timeout: TIMEOUT });
    return note;
  };
  const exitButton = async chart => {
    // The CPP edit panel has its own Exit image input; the note toolbar's is the one wired to closeEnc().
    const exit = chart.locator('input[type="image"][title="Exit"][onclick*="closeEnc("]');
    h.assert(await exit.count() === 1, 'The chart toolbar does not offer exactly one note Exit button');
    return exit;
  };

  /**
   * Type into the chart's note the way a clinician does (click, caret to the end, keystrokes), and wait for
   * the autosave POST whose body carries the whole of `text`, rather than sleeping for the timer: a tick can
   * land mid-typing and post a prefix.
   * @return {Promise<string>} the editor's value once the draft was acknowledged
   */
  async function typeAndAwaitAutosave(chart, text) {
    const note = await noteOf(chart);
    const posted = chart.waitForResponse(
      response => isAutosave(response.request()) && autosavedNote(response.request()).includes(text),
      { timeout: AUTOSAVE_WAIT_MS });
    posted.catch(() => {});
    await note.click();
    await note.press('Control+End');
    await note.pressSequentially(` ${text}`, { delay: 10 });
    const response = await posted;
    h.assert(response.status() === 200, `The draft autosave answered HTTP ${response.status()} instead of 200`);
    return note.inputValue();
  }

  /** Click Exit with the confirm answered as given; returns the confirm dialogs the click raised. */
  async function clickExit(chart, accept) {
    const exit = await exitButton(chart);
    const closed = accept ? chart.waitForEvent('close', { timeout: TIMEOUT }) : null;
    closed?.catch(() => {});
    const dialogs = await h.withExpectedDialogs(chart, async () => {
      // Accepting closes the window under the click; that is the outcome, not an error.
      await exit.click().catch((error) => { if (!(accept && chart.isClosed())) throw error; });
    }, { accept });
    if (closed) await closed;
    return dialogs;
  }
  const assertExitConfirm = (dialogs, what) => h.assert(
    dialogs.length === 1 && dialogs[0].type === 'confirm' && /unsaved data/i.test(dialogs[0].text),
    `${what}: expected exactly one "unsaved data will be lost" confirm, got ${dialogs.length} dialog(s)`
    + `${dialogs.length ? ` (${dialogs.map(d => d.type).join(', ')})` : ''}`);

  // The test provider is the one whose drafts this check asserts on; the owned patient has none yet.
  h.assert(draftShape(provider, textOne) === '0/0' && draftShape(second.providerNo, textOne) === '0/0',
    'The owned patient already has a draft row');

  let fullText;
  let chart = await s.chart();
  await s.step('typing a note autosaves exactly one draft for this patient and provider', async () => {
    fullText = await typeAndAwaitAutosave(chart, textOne);
    h.assert(fullText.includes(textOne), 'The editor does not hold the text just typed');
    await expectDraft(provider, textOne, 'The autosave answered 200 but did not leave exactly one draft row holding the typed text');
    h.assert(locksOf(provider) === '1', 'The open chart does not hold its note lock (the autosave would have been refused)');
  });

  await s.step('Exit\'s confirm, dismissed, keeps the window, the text and the draft', async () => {
    const dialogs = await clickExit(chart, false);
    assertExitConfirm(dialogs, 'Exit with an unsaved note');
    // window.close() is synchronous in closeEnc; give a close that should not happen time to show up.
    await chart.waitForTimeout(1000);
    h.assert(!chart.isClosed(), 'Dismissing Exit\'s confirm closed the chart window anyway');
    h.assert((await (await noteOf(chart)).inputValue()) === fullText, 'Dismissing Exit\'s confirm changed the text in the editor');
    await expectDraft(provider, textOne, 'Dismissing Exit\'s confirm lost or duplicated the draft row');
  });

  await s.step('Exit\'s confirm, accepted, closes the window and leaves exactly one draft holding the text', async () => {
    const mark = failureMark(s.recorder);
    const dialogs = await clickExit(chart, true);
    assertExitConfirm(dialogs, 'Exit with an unsaved note');
    h.assert(chart.isClosed(), 'Accepting Exit\'s confirm did not close the chart window');
    await expectDraft(provider, textOne, 'Leaving through Exit did not leave exactly one draft row holding the typed text');
    // Released by the unload beacon; reopening below would otherwise ask whether to take the lock over.
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient}
      AND provider_no=${q(provider)}`, '0', 'Leaving through Exit left the note lock behind');
    consumeUnloadBeacons(s.recorder, mark);
  });

  await s.step('a second provider who opens the same chart never gets the draft', async () => {
    const mark = failureMark(s.recorder);
    const before = draftIds(provider);
    h.assert(/^\d+$/.test(before), 'The first provider\'s draft id is not a single id');
    const b = await openSecondSession(s, {
      label: 'second-provider', config: { ...config, testUser: second.username },
    });
    const bChart = await openChart(b.context, b.master, s.recorder, TIMEOUT);
    await waitForNavbars(bChart, TIMEOUT);
    h.assert(new URL(bChart.url()).searchParams.get('demographicNo') === patient,
      'The second provider\'s chart is not the owned patient\'s');
    // The editor must be there before its content means anything: an unopened editor holds no draft either.
    const bNote = await noteOf(bChart);
    h.assert(!(await bNote.inputValue()).includes(textOne), 'The second provider\'s editor holds the first provider\'s draft');
    const html = await bChart.evaluate(() => document.documentElement.outerHTML);
    h.assert(!html.includes(textOne), 'The second provider\'s chart page contains the first provider\'s draft text');
    h.assert(draftIds(provider) === before && draftShape(provider, textOne) === '1/1',
      'The second provider opening the chart changed the first provider\'s draft');
    h.assert(draftShape(second.providerNo, textOne) === '0/0' && locksOf(second.providerNo) === '1',
      'The second provider\'s chart did not open as its own editor (no lock) or already holds a draft');
    // Its own typing is its own draft; the first provider's is neither read, replaced nor removed.
    await typeAndAwaitAutosave(bChart, secondProviderText);
    await expectDraft(second.providerNo, secondProviderText, 'The second provider\'s autosave did not leave its own single draft');
    h.assert(draftShape(second.providerNo, textOne) === '1/0', 'The second provider\'s draft carries the first provider\'s text');
    h.assert(draftIds(provider) === before && draftShape(provider, textOne) === '1/1' && draftShape(provider, secondProviderText) === '1/0',
      'The second provider\'s autosave replaced or altered the first provider\'s draft');
    // Leave cleanly: release its lock, then close the chart (its draft row is removed with the patient at cleanup).
    await releaseChartLocks(b.context, config.baseUrl);
    await bChart.close();
    consumeUnloadBeacons(s.recorder, mark);
  });

  await s.step('opening the chart again restores the draft into the editor', async () => {
    chart = await s.chart();
    const restored = await (await noteOf(chart)).inputValue();
    h.assert(restored.includes(textOne), 'The reopened chart did not restore the draft text');
    h.assert(normalise(restored) === normalise(fullText),
      'The reopened chart restored something other than exactly what was typed');
    await expectDraft(provider, textOne, 'Reopening the chart lost or duplicated the draft row');
  });

  let recoveredChart;
  await s.step('a window killed with no beforeunload still leaves the draft to recover in a new session', async () => {
    const mark = failureMark(s.recorder);
    const takeovers = [];
    const answerPrompt = label => async (dialog) => {
      if (dialog.type() === 'confirm' && TAKEOVER_PROMPT.test(dialog.message())) {
        takeovers.push(label);
        await dialog.accept();
      } else {
        s.recorder.unexpectedDialogs.push({ label, type: dialog.type(), text: dialog.message() });
        await dialog.dismiss();
      }
    };
    // The reopened chart of the main login is left cleanly, so the doomed window is the only one holding
    // the lock and the recovery window's prompt (or its absence) is about the doomed window alone.
    await releaseChartLocks(s.context, config.baseUrl);
    await chart.close();
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient}
      AND provider_no=${q(provider)}`, '0', 'Closing the reopened chart left the note lock behind');

    // A window of the same provider in its own session: restores the draft, extends it, autosaves, then dies.
    const doomed = await openSecondSession(s, { label: 'killed-window', dialogHandler: answerPrompt('killed-window') });
    const doomedChart = await openChart(doomed.context, doomed.master, s.recorder, TIMEOUT);
    await waitForNavbars(doomedChart, TIMEOUT);
    h.assert((await (await noteOf(doomedChart)).inputValue()).includes(textOne),
      'The draft was not restored into the window that is about to be killed');
    const extended = await typeAndAwaitAutosave(doomedChart, textTwo);
    await expectDraft(provider, textTwo, 'The extended note\'s autosave did not leave exactly one draft holding the new text');
    h.assert(draftShape(provider, textOne) === '1/1', 'The extended draft lost the text restored from the earlier draft');
    // The kill. A crash or a kill -9 runs no pagehide handler, so nothing releases the note lock; Playwright's
    // context.close() does run the unload sequence (the lock is gone within milliseconds), so the page's own
    // release flag is cleared first, as echart-lock-lifecycle does, to stand in for an unload that never ran.
    // Then the whole browser context goes away: no Exit, no confirm, no beforeunload answer.
    await doomedChart.evaluate(() => { window.needToReleaseLock = false; });
    await doomed.context.close();
    // A negative wait: a release that was going to arrive does so within milliseconds (observed), so the lock
    // still being there after this is the stale lock a crash leaves.
    await new Promise(resolve => setTimeout(resolve, 1500));
    h.assert(locksOf(provider) === '1',
      'The killed window\'s note lock was released, so the stale-lock recovery path was not exercised');
    await expectDraft(provider, textTwo, 'Killing the window changed the draft row');

    // A new login of the same provider: the draft comes back, after the clinician agrees to take the stale lock over.
    const reopened = await openSecondSession(s, { label: 'recovery-window', dialogHandler: answerPrompt('recovery-window') });
    recoveredChart = await openChart(reopened.context, reopened.master, s.recorder, TIMEOUT);
    await waitForNavbars(recoveredChart, TIMEOUT);
    const recovered = await (await noteOf(recoveredChart)).inputValue();
    h.assert(recovered.includes(textOne) && recovered.includes(textTwo),
      'A new session did not recover the draft the killed window had autosaved');
    h.assert(normalise(recovered) === normalise(extended), 'The recovered text is not exactly what the killed window had typed');
    await expectDraft(provider, textTwo, 'Opening the chart in a new session lost or duplicated the draft row');
    h.assert(takeovers.length === 1 && takeovers[0] === 'recovery-window',
      `Expected exactly one take-over prompt, in the recovery window, got ${takeovers.length}`);
    const sessionId = (await reopened.context.cookies()).find(cookie => cookie.name === 'JSESSIONID')?.value;
    h.assert(sessionId && /^[A-Za-z0-9._-]+$/.test(sessionId), 'The recovery login has no session cookie');
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient}
      AND provider_no=${q(provider)} AND session_id=${q(sessionId)}`, '1',
    'Agreeing to continue did not move the stale note lock to the recovery window');
    h.assert(locksOf(provider) === '1', 'The patient holds more than one note lock for the provider after the take-over');
    consumeUnloadBeacons(s.recorder, mark);
    console.log('  the kill left its note lock behind; the recovery window took it over and recovered the draft');
  });

  await s.step('Save writes the recovered note and removes the draft', async () => {
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0',
      'A note existed before Save, so the row Save wrote cannot be told');
    const [response] = await Promise.all([
      recoveredChart.waitForResponse(r => r.request().method() === 'POST' && /\/CaseManagementEntry/.test(r.url())
        && new URLSearchParams(r.request().postData() || '').get('method') === 'save', { timeout: AUTOSAVE_WAIT_MS }),
      recoveredChart.locator('#saveImg').first().click({ timeout: TIMEOUT }),
    ]);
    h.assert(response.status() < 400, `Save answered HTTP ${response.status()}`);
    await expectValue(sql, `SELECT CONCAT(COUNT(*), '/', COALESCE(SUM(signed = 0), 0), '/',
      COALESCE(SUM(LOCATE(${q(textOne)}, note) > 0 AND LOCATE(${q(textTwo)}, note) > 0), 0))
      FROM casemgmt_note WHERE demographic_no=${patient} AND provider_no=${q(provider)}`, '1/1/1',
    'Save did not write exactly one unsigned note holding the recovered text');
    await expectValue(sql, `SELECT COUNT(*) ${draftsOf(provider)}`, '0',
      'Save wrote the note but left the autosaved draft behind, so the chart would offer the saved text as a draft again');
    await releaseChartLocks(recoveredChart.context(), config.baseUrl);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('echart-note-draft-recovery', workflow, { openPatient: true });
