#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #3609: one failed pagination fetch used to stop loading older notes for the rest of the
 * chart session. ChartNotesAjax.jsp reports the rendered count only when its response scripts run;
 * a response that never ran them (a 500, a CSRF rejection, a login redirect, a dropped connection)
 * left notesLastBatchSize at -1, which notesLoader() read as "the chart is fully loaded": the scroll
 * poll was cleared for good, the throbber hid, and nothing told the clinician.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart (first page of notes) ▸ scroll the note pane
 * to the top (older notes page in) ▸ "Load All Notes". The failures are injected at the network layer
 * with Playwright routes on the CaseManagementView?method=viewNotesOpt POST the pane makes, so the
 * application code is the real one end to end.
 *
 * Asserts, on an owned patient with 45 signed notes dated 10 days apart:
 *   1. a pagination fetch answered 401 (what an expired session gets on an AJAX call) shows the
 *      "Older notes could not be loaded" indicator with its Retry link, inserts nothing into the
 *      pane, rolls the offset back and leaves the scroll poll armed;
 *   2. the next scroll to the top asks for the SAME batch again and, once it renders, the indicator
 *      hides and paging continues until all 45 notes are present exactly once, in date order;
 *   3. a persistent error (every pagination request answered 500) is retried only
 *      NOTES_MAX_FAILED_LOADS (3) times, after which the poll stops: no request a second, the error
 *      page is never inserted at the top of the chart, and the indicator stays up;
 *   4. the indicator's Retry link, once the server answers again, loads the batch, re-arms the poll,
 *      and paging continues to all 45;
 *   5. a failed Load All Notes can simply be clicked again.
 *
 * Fixtures: the owned FAKE-PW patient with 45 SQL-seeded notes (token FAKE-PW<hex> NOTEnn); every note,
 * eChart and note-support row of the patient is deleted in cleanup and asserted gone.
 * The 401 and 500s this check provokes are consumed from the strict recorder exactly as drawn (that
 * status on the CaseManagementView POST and the browser's console line for each); any other signal
 * stays strict.
 */
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

const TOTAL = 45;
const PAGE = 20;
const MAX_FAILED_LOADS = 3;
const pad = n => String(n).padStart(2, '0');
const NOTES_ROUTE = '**/CaseManagementView';

/** The pagination POST the notes pane makes, or null for any other request to the action. */
function paginationParams(request) {
  if (request.method() !== 'POST') return null;
  const params = new URLSearchParams(request.postData() || '');
  if (params.get('method') !== 'viewNotesOpt') return null;
  return {offset: Number(params.get('offset')), numToReturn: Number(params.get('numToReturn'))};
}

async function workflow(s) {
  const {sql, patient, provider, marker, recorder} = s;
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
  const values = [];
  for (let i = 1; i <= TOTAL; i += 1) {
    values.push(`(NOW(),DATE_SUB(NOW(), INTERVAL ${i * 10} DAY),${patient},${q(provider)},${q(`${marker} NOTE${pad(i)}`)},1,${q(provider)},'',${q(program)},${q(role)},'x',UUID(),'0',0)`);
  }
  sql.execute(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,
    encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived) VALUES ${values.join(',')}`);

  /** The owned notes the pane shows, in display order, as numbers (NOTE07 -> 7). */
  const shown = chart => chart.evaluate(m => {
    const text = document.getElementById('encMainDiv').innerText;
    // m is the workflow's fixed-prefix hexadecimal fixture marker, not application input.
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
    return (text.match(new RegExp(`${m} NOTE\\d\\d`, 'g')) || []).map(x => Number(x.slice(-2)));
  }, marker);
  const range = (from, to) => Array.from({length: to - from + 1}, (_, i) => from + i);
  const descending = (hi, lo) => range(lo, hi).reverse(); // oldest (highest number) first, as the pane lists them
  const paneState = chart => chart.evaluate(() => ({
    offset: window.notesOffset,
    failed: window.notesFailedLoads,
    retrieveOk: window.notesRetrieveOk,
    pollArmed: window.notesScrollCheckInterval !== null,
    indicator: (() => { const el = document.getElementById('notesLoadFailed'); return el && el.offsetParent !== null; })(),
    throbber: (() => { const el = document.getElementById('notesLoading'); return el && el.offsetParent !== null; })(),
    paneText: document.getElementById('encMainDiv').innerText,
  }));
  const firstPage = async chart => {
    // m is the workflow's fixed-prefix hexadecimal fixture marker, not application input.
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
    await chart.waitForFunction(m => (document.getElementById('encMainDiv').innerText.match(new RegExp(`${m} NOTE\\d\\d`, 'g')) || []).length >= 20,
      marker, {timeout: 20000});
    await chart.waitForTimeout(1500);
    const notes = await shown(chart);
    h.assert(JSON.stringify(notes) === JSON.stringify(descending(PAGE, 1)),
      `The first page should list NOTE20 down to NOTE01 once each, it lists ${JSON.stringify(notes)}`);
  };
  /** Keep the pane at the top (where the poll pages) until every owned note is present. */
  const allLoaded = chart => chart.waitForFunction(({m, total}) => {
    document.getElementById('encMainDivWrapper').scrollTop = 0;
    // m is the workflow's fixed-prefix hexadecimal fixture marker, not application input.
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
    return (document.getElementById('encMainDiv').innerText.match(new RegExp(`${m} NOTE\\d\\d`, 'g')) || []).length >= total;
  }, {m: marker, total: TOTAL}, {timeout: 40000, polling: 500})
    .catch(() => { throw new Error('Scrolling to the top did not page in all 45 notes within 40 s'); });
  /** Keep the pane at the top until the indicator is showing. */
  const indicatorShown = chart => chart.waitForFunction(() => {
    document.getElementById('encMainDivWrapper').scrollTop = 0;
    const el = document.getElementById('notesLoadFailed');
    return el && el.offsetParent !== null;
  }, null, {timeout: 15000, polling: 500})
    .catch(() => { throw new Error('The "notes could not be loaded" indicator did not appear after the failed pagination fetch'); });
  /** Keep the pane at the top until the failure cap has cleared the poll. */
  const pollStopped = chart => chart.waitForFunction(() => {
    document.getElementById('encMainDivWrapper').scrollTop = 0;
    return window.notesScrollCheckInterval === null;
  }, null, {timeout: 20000, polling: 500})
    .catch(() => { throw new Error('The scroll poll was not stopped after repeated failed fetches (it would retry once a second for ever)'); });
  /** Consume the error statuses this check drew, and only those; everything else stays strict. */
  const consumeProvokedErrors = (status, expected) => {
    let consumed = 0;
    for (let i = recorder.badResponses.length - 1; i >= 0; i -= 1) {
      const entry = recorder.badResponses[i];
      if (entry.status === status && entry.method === 'POST' && /\/CaseManagementView(?:\?|$)/.test(entry.url)) {
        recorder.badResponses.splice(i, 1);
        consumed += 1;
      }
    }
    for (let i = recorder.consoleIssues.length - 1; i >= 0; i -= 1) {
      const entry = recorder.consoleIssues[i];
      // Chrome's "Failed to load resource: the server responded with a status of NNN" line
      // carries the URL in its location, not in its text.
      if (new RegExp(`status of ${status} \\(`).test(entry.text) && /CaseManagementView/.test((entry.location && entry.location.url) || '')) {
        recorder.consoleIssues.splice(i, 1);
      }
    }
    h.assert(consumed === expected, `The browser recorded ${consumed} provoked ${status}(s) on the notes fetch, expected ${expected}`);
  };

  // What UnauthenticatedRejectionResolver answers an AJAX caller whose session has expired.
  const SESSION_EXPIRED = JSON.stringify({error: 'unauthenticated', message: 'Session expired, sign in again'});
  const ERROR_PAGE = '<!DOCTYPE html><html><body><h1>HTTP Status 500 - Internal Server Error</h1><p>CARLOS Error: injected by the check</p></body></html>';

  let chart = await s.chart();
  await s.step('the first render shows exactly the 20 newest notes', () => firstPage(chart));

  let seen = [];
  await s.step('a pagination fetch answered 401 (session expired) shows the indicator, inserts nothing and keeps the poll armed', async () => {
    seen = [];
    let faked = false;
    await chart.route(NOTES_ROUTE, async route => {
      const page = paginationParams(route.request());
      if (!page) return route.continue();
      seen.push(page);
      if (!faked && page.offset === PAGE) {
        faked = true;
        // A session that expired mid-chart: the AJAX caller is answered 401 with a JSON body,
        // which carries none of ChartNotesAjax.jsp's bootstrap scripts.
        return route.fulfill({status: 401, contentType: 'application/json', body: SESSION_EXPIRED});
      }
      return route.continue();
    });
    await indicatorShown(chart);
    const state = await paneState(chart);
    const indicatorText = await chart.locator('#notesLoadFailed').innerText();
    h.assert(/older notes could not be loaded/i.test(indicatorText), `The indicator reads "${indicatorText}", not the expected message`);
    h.assert(await chart.locator('#notesLoadFailed a', {hasText: /retry/i}).count() === 1, 'The indicator offers no Retry link');
    h.assert(!state.paneText.includes('unauthenticated') && !state.paneText.includes('Session expired'),
      'The 401 body was inserted into the notes pane');
    h.assert(JSON.stringify(await shown(chart)) === JSON.stringify(descending(PAGE, 1)), 'The first page of notes was disturbed by the failed fetch');
    h.assert(state.offset === 0, `notesOffset was not rolled back after the failed fetch (it is ${state.offset})`);
    h.assert(state.failed === 1, `notesFailedLoads should be 1 after one failure, it is ${state.failed}`);
    h.assert(state.retrieveOk === true, 'one failed fetch was treated as the end of the chart (notesRetrieveOk false)');
    h.assert(state.pollArmed, 'one failed fetch cleared the scroll poll for the rest of the session');
    h.assert(!state.throbber, 'The loading throbber is still showing after the failed fetch');
    consumeProvokedErrors(401, 1);
  });

  await s.step('the next scroll to the top retries the same batch, the indicator hides and paging continues to all 45', async () => {
    await allLoaded(chart);
    h.assert(seen.length >= 2 && seen[0].offset === PAGE && seen[1].offset === PAGE,
      `The retry should ask for offset ${PAGE} again; the pane requested ${JSON.stringify(seen.map(p => p.offset))}`);
    // The batch that comes back empty is the last one; wait for it so the final state is settled.
    await chart.waitForFunction(() => {
      document.getElementById('encMainDivWrapper').scrollTop = 0;
      return window.notesLastBatchSize === 0;
    }, null, {timeout: 15000, polling: 500}).catch(() => {});
    const notes = await shown(chart);
    h.assert(JSON.stringify(notes) === JSON.stringify(descending(TOTAL, 1)),
      `After paging the pane should list NOTE45 down to NOTE01 once each, it lists ${notes.length} notes: ${JSON.stringify(notes)}`);
    const state = await paneState(chart);
    h.assert(!state.indicator, 'The indicator is still showing after a successful retry');
    h.assert(state.failed === 0, 'The failure streak was not reset by the successful retry');
    h.assert(!state.pollArmed, 'The poll kept running after the server reported the chart fully loaded');
    await chart.unroute(NOTES_ROUTE);
  });

  await s.step('a persistent 500 is retried only three times, then the poll stops and nothing is inserted into the pane', async () => {
    await chart.close();
    chart = await s.chart();
    await firstPage(chart);
    seen = [];
    await chart.route(NOTES_ROUTE, async route => {
      const page = paginationParams(route.request());
      if (!page) return route.continue();
      seen.push(page);
      return route.fulfill({status: 500, contentType: 'text/html', body: ERROR_PAGE});
    });
    await pollStopped(chart);
    const requestsAtStop = seen.length;
    // Hold the pane at the top for a while longer: a stopped poll issues nothing more.
    await chart.waitForTimeout(3500);
    await chart.evaluate(() => { document.getElementById('encMainDivWrapper').scrollTop = 0; });
    await chart.waitForTimeout(1500);
    h.assert(seen.length === MAX_FAILED_LOADS && requestsAtStop === MAX_FAILED_LOADS,
      `Expected exactly ${MAX_FAILED_LOADS} retries of the failing batch, the pane made ${seen.length} requests: ${JSON.stringify(seen.map(p => p.offset))}`);
    h.assert(seen.every(p => p.offset === PAGE), `Every retry should ask for offset ${PAGE}; the pane asked for ${JSON.stringify(seen.map(p => p.offset))}`);
    const state = await paneState(chart);
    h.assert(state.indicator, 'The indicator is not showing after the failure cap stopped the poll');
    h.assert(state.failed === MAX_FAILED_LOADS, `notesFailedLoads is ${state.failed}, expected ${MAX_FAILED_LOADS}`);
    h.assert(!state.paneText.includes('Internal Server Error') && !state.paneText.includes('injected by the check'),
      'The 500 error page was inserted at the top of the notes pane');
    h.assert(JSON.stringify(await shown(chart)) === JSON.stringify(descending(PAGE, 1)), 'The first page of notes was disturbed by the failed fetches');
    consumeProvokedErrors(500, MAX_FAILED_LOADS);
  });

  await s.step('Retry loads the batch once the server answers again, re-arms the poll and paging continues to all 45', async () => {
    await chart.unroute(NOTES_ROUTE);
    seen = [];
    await chart.route(NOTES_ROUTE, async route => {
      const page = paginationParams(route.request());
      if (page) seen.push(page);
      return route.continue();
    });
    await chart.locator('#notesLoadFailed a', {hasText: /retry/i}).click();
    await chart.waitForFunction(({m, want}) => {
      // m is the workflow's fixed-prefix hexadecimal fixture marker, not application input.
      // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
      return (document.getElementById('encMainDiv').innerText.match(new RegExp(`${m} NOTE\\d\\d`, 'g')) || []).length >= want;
    }, {m: marker, want: 2 * PAGE}, {timeout: 15000}).catch(() => { throw new Error('Retry did not load the second page of notes within 15 s'); });
    h.assert(seen.length >= 1 && seen[0].offset === PAGE && seen[0].numToReturn === PAGE,
      `Retry should ask for offset ${PAGE}; the pane requested ${JSON.stringify(seen)}`);
    let state = await paneState(chart);
    h.assert(!state.indicator, 'The indicator is still showing after Retry succeeded');
    h.assert(state.failed === 0, 'The failure streak was not reset by Retry');
    h.assert(state.pollArmed, 'Retry did not re-arm the scroll poll');
    await allLoaded(chart);
    await chart.waitForFunction(() => {
      document.getElementById('encMainDivWrapper').scrollTop = 0;
      return window.notesLastBatchSize === 0;
    }, null, {timeout: 15000, polling: 500}).catch(() => {});
    const notes = await shown(chart);
    h.assert(JSON.stringify(notes) === JSON.stringify(descending(TOTAL, 1)),
      `After Retry and paging the pane should list NOTE45 down to NOTE01 once each, it lists ${notes.length} notes`);
    state = await paneState(chart);
    h.assert(!state.pollArmed, 'The poll kept running after the server reported the chart fully loaded');
    await chart.unroute(NOTES_ROUTE);
  });

  await s.step('a failed Load All Notes shows the indicator and can simply be clicked again', async () => {
    await chart.close();
    chart = await s.chart();
    await firstPage(chart);
    let failed = false;
    seen = [];
    await chart.route(NOTES_ROUTE, async route => {
      const page = paginationParams(route.request());
      if (!page) return route.continue();
      seen.push(page);
      if (!failed) {
        failed = true;
        return route.fulfill({status: 500, contentType: 'text/html', body: ERROR_PAGE});
      }
      return route.continue();
    });
    const loadAll = chart.locator('#note-control-panel button', {hasText: /load all/i});
    await loadAll.click();
    await chart.locator('#notesLoadFailed').waitFor({state: 'visible', timeout: 10000})
      .catch(() => { throw new Error('The indicator did not appear after Load All Notes failed'); });
    let state = await paneState(chart);
    h.assert(state.offset === 0, `A failed Load All left notesOffset at ${state.offset}; it must come back so the button works again`);
    h.assert(JSON.stringify(await shown(chart)) === JSON.stringify(descending(PAGE, 1)), 'The first page of notes was disturbed by the failed Load All');
    await loadAll.click();
    await chart.waitForFunction(({m, total}) => {
      // m is the workflow's fixed-prefix hexadecimal fixture marker, not application input.
      // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
      return (document.getElementById('encMainDiv').innerText.match(new RegExp(`${m} NOTE\\d\\d`, 'g')) || []).length >= total;
    }, {m: marker, total: TOTAL}, {timeout: 30000}).catch(() => { throw new Error('The second Load All Notes did not show all 45 notes within 30 s'); });
    await chart.waitForTimeout(1500);
    const notes = await shown(chart);
    h.assert(JSON.stringify(notes) === JSON.stringify(descending(TOTAL, 1)),
      `Load All should list NOTE45 down to NOTE01 once each, it lists ${notes.length} notes`);
    h.assert(seen.length === 2 && seen[0].offset === PAGE && seen[1].offset === PAGE && seen[1].numToReturn >= 1000000,
      `The second Load All should ask for the whole chart from offset ${PAGE} again; the pane requested ${JSON.stringify(seen)}`);
    state = await paneState(chart);
    h.assert(!state.indicator, 'The indicator is still showing after the second Load All succeeded');
    consumeProvokedErrors(500, 1);
    await chart.unroute(NOTES_ROUTE);
  });
}

if (require.main === module) runWorkflow('echart-notes-pagination-retry', workflow, {openPatient: true});
module.exports = {workflow};
