#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Two patients' charts in one login keep their own chart working state (issue #4673).
 *
 * The chart kept state between requests in login-wide session attributes, so a request from one
 * patient's chart window read or replaced what the other chart had left there. Each is now keyed
 * by patient (ChartSessionKeys), and the chart's patient comes from the request alone. Driven here,
 * each right after the other chart wrote its own state:
 *
 *   1. A chart note list, note search or Disease Registry add that names no patient, a malformed
 *      one or two different ones is refused (400) without reading or writing either chart; a list
 *      that names A lists A's notes. They fell back to the chart opened last in any window.
 *   2. A's notes filtered by an issue (Filter ▸ the issue ▸ Hide) while B's chart reloads its notes
 *      before A's own list has loaded: B lists its own notes and A's list is filtered. The editor
 *      hands the filters to the next note list through the session; that handoff was login-wide,
 *      so B's list consumed A's filter and showed none of B's notes. The overlap is held open by
 *      delaying A's own note-list request in the browser.
 *
 * Not driven, with the unit tests that cover them:
 *   - the text a history (CPP) item save remembers so that the patient's next such save replaces it
 *     in the legacy eChart record, also keyed by patient now. The chart reloads its editor after
 *     every save, which clears that text (on release/2026.08 too), so in the current chart it is
 *     never there for a later save to use and two charts cannot observe each other's
 *     (CaseManagementNoteRevisionConflictUnitTest);
 *   - the "draft is being restored" signal, now per patient. Only the old-style chart view
 *     (CaseManagementView without newCaseManagement) offers Restore Lost Note; nothing links to it,
 *     every chart entry switches the login to the current chart, and it fails for an admitted
 *     patient on a JSP that no longer exists (casemgmt/current_issues.jsp)
 *     (CaseManagementViewPatientIsolationUnitTest).
 *
 * Fixtures: the workflow's FAKE patient (A) and a second FAKE patient (B, PatientBravo); the notes
 * saved in their charts (and a copy of A's, as A's second note), their legacy eChart rows, one
 * casemgmt_issue linked to A's note, and any registry row a refused request might have written
 * (asserted absent). Cleanup
 * removes every owned row by key and asserts it gone. No clinic-wide state is changed.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

const TIMEOUT = 30000;
const NOTE = '#caseManagementEntryForm textarea[name="caseNote_note"]';
const B_FIRST_NAME = 'PatientBravo';
// A catalog issue the issues-filter checks also use; the filter step links it to A's note.
const ISSUE_CODE = 'CTCMM1000';
const DX_CODE = '250';
const q = h.sqlString;

function key(value, what) {
  h.assert(/^[1-9]\d*$/.test(String(value)), `The owned ${what} key is invalid`);
  return String(value);
}

/** The second patient, owned by this run; removed last, after every child row. */
function createSecondPatient(s) {
  const { sql, marker, provider } = s;
  const patient = key(h.insertId(sql, `INSERT INTO demographic (last_name, first_name, year_of_birth,
      month_of_birth, date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status,
      lastUpdateDate)
    VALUES (${q(marker)}, ${q(B_FIRST_NAME)}, '1975', '03', '04', 'M', 'AC', ${q(provider)}, 'ON', 'ON', 'NR',
      NOW())`, 'second patient'), 'second patient');
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${q(marker)}`) === '1', 'The second patient fixture changed owner');
    const support = [
      ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no'],
      ['demographicExt', 'demographic_no'], ['demographicArchive', 'demographic_no'],
    ];
    sql.execute(support.map(([table, column]) => `DELETE FROM ${table} WHERE ${column}=${patient}`).join(';'));
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) === '0',
      'The second patient was not removed');
  });
  return patient;
}

/** Notes, issues, drafts, registry rows and legacy eChart rows of the two owned patients. */
function ownChartRows(s, patients) {
  const { sql } = s;
  const list = patients.join(',');
  s.cleanup(() => {
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no IN (${list})`)
      .map(([id]) => key(id, 'note'));
    const issues = sql.rows(`SELECT id FROM casemgmt_issue WHERE demographic_no IN (${list})`)
      .map(([id]) => key(id, 'chart issue'));
    if (issues.length) {
      sql.execute(`DELETE FROM casemgmt_issue_notes WHERE id IN (${issues.join(',')})`);
    }
    if (notes.length) {
      const ids = notes.join(',');
      sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${ids});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${ids});
        DELETE FROM casemgmt_note_link WHERE note_id IN (${ids});
        DELETE FROM casemgmt_note WHERE note_id IN (${ids}) AND demographic_no IN (${list})`);
    }
    sql.execute(`DELETE FROM casemgmt_issue WHERE demographic_no IN (${list});
      DELETE FROM eChart WHERE demographicNo IN (${list});
      DELETE FROM casemgmt_tmpsave WHERE demographic_no IN (${list});
      DELETE FROM casemgmt_note_lock WHERE demographic_no IN (${list});
      DELETE FROM dxresearch WHERE demographic_no IN (${list})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo IN (${list}))
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${list}))`) === '0',
    'Owned chart rows were not removed');
  });
}

/**
 * One step whose chart windows this check reloads as a user would: a request a window abandoned
 * because its own document went away (navigatedAway, the harness's proof) is the browser's doing,
 * not a finding. Every other failure still fails the step.
 */
async function step(s, label, body) {
  await s.step(label, async () => {
    const since = s.recorder.requestFailures.length;
    await body();
    for (let i = s.recorder.requestFailures.length - 1; i >= since; i--) {
      const entry = s.recorder.requestFailures[i];
      if (/ERR_ABORTED/.test(entry.errorText || '') && typeof entry.navigatedAway === 'function'
        && entry.navigatedAway()) {
        s.recorder.requestFailures.splice(i, 1);
      }
    }
  });
}

async function settle(page, label) {
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(page, label);
}

/**
 * Reload a chart window as a user does (F5). The replaced document sends its note-lock release as a
 * beacon from its pagehide handler, and Chromium reports that beacon as an aborted "ping" because
 * the document is gone; the reloaded chart takes the lock again. Exactly those entries (a ping to
 * CaseManagementEntry, ERR_ABORTED, recorded during this reload) are the browser's doing and are
 * excused; every other failure stays a finding.
 */
async function reloadChart(s, chart, label) {
  const mark = s.recorder.requestFailures.length;
  await chart.reload();
  await settle(chart, label);
  await waitForNavbars(chart, TIMEOUT);
  for (let i = s.recorder.requestFailures.length - 1; i >= mark; i--) {
    const entry = s.recorder.requestFailures[i];
    if (entry.resourceType === 'ping' && /ERR_ABORTED/.test(entry.errorText || '')
        && new URL(entry.url).pathname.endsWith('/CaseManagementEntry')) {
      s.recorder.requestFailures.splice(i, 1);
    }
  }
}

/** A's chart, and B's opened from a second schedule tab, as a clinician opens a second chart. */
async function openCharts(s, B) {
  const { context, recorder, marker } = s;
  const chartA = await s.chart();
  const second = await context.newPage();
  const schedule = new URL(s.schedule.url());
  await h.gotoApp(second, s.config.baseUrl, schedule.pathname.replace(/^\/[^/]+/, '') + schedule.search);
  await settle(second, 'second schedule tab');
  const { masterPage } = await openMasterRecord(context, second, recorder, {
    searchTerm: marker, preferredDemographicNo: B, timeout: TIMEOUT });
  const chartB = await openChart(context, masterPage, recorder, TIMEOUT);
  await waitForNavbars(chartB, TIMEOUT);
  h.assert(new URL(chartB.url()).searchParams.get('demographicNo') === B, 'The second chart is not B\'s');
  return { A: chartA, B: chartB };
}

/**
 * Write the note text (typed after what the editor holds, or replacing it) and Save; returns the
 * saved revision's id once the editor names it.
 */
async function saveNote(chart, text, { replace = false } = {}) {
  const editor = chart.locator(NOTE);
  await editor.waitFor({ state: 'visible', timeout: TIMEOUT });
  const previousId = await chart.locator('#caseManagementEntryForm [name="noteId"]').inputValue();
  if (replace) {
    await editor.fill(text);
  } else {
    await editor.click();
    await editor.press('Control+End');
    await editor.pressSequentially(` ${text}`, { delay: 5 });
  }
  await chart.waitForFunction(() => !window.autoSaveXhr, undefined, { timeout: TIMEOUT });
  const [saved] = await Promise.all([
    chart.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
      && new URLSearchParams(response.request().postData() || '').get('method') === 'save', { timeout: TIMEOUT }),
    chart.locator('#saveImg').first().click(),
  ]);
  await saved.finished();
  h.assert(saved.status() === 200, `The note save answered HTTP ${saved.status()}`);
  const handle = await chart.waitForFunction(previous => {
    const id = document.forms.caseManagementEntryForm?.elements.noteId?.value;
    return /^[1-9]\d*$/.test(id || '') && id !== previous && !window.autoSaveXhr && id;
  }, previousId, { timeout: TIMEOUT });
  const noteId = key(await handle.jsonValue(), 'saved note');
  await handle.dispose();
  return noteId;
}

/** The note ids the chart's notes area lists. */
const listedIds = chart => chart.evaluate(() => [...document.querySelectorAll('div[id^="nc"]')]
  .map(container => (container.querySelector('div[id^="n"]:not([id^="nc"])') || {}).id || '')
  .filter(id => /^n[1-9]\d*$/.test(id)).map(id => id.slice(1)));

/** Poll until the chart lists exactly `wanted` (order aside), or fail saying what it listed. */
async function waitForListed(chart, wanted, message, timeoutMs = 15000) {
  const sorted = ids => [...ids].sort().join(',');
  const deadline = Date.now() + timeoutMs;
  let listed = [];
  do {
    listed = await listedIds(chart).catch(() => []);
    if (sorted(listed) === sorted(wanted)) return;
    await chart.waitForTimeout(200);
  } while (Date.now() < deadline);
  h.assert(false, `${message}: the chart lists notes [${listed.join(', ')}], expected [${wanted.join(', ')}]`);
}

/** The notes the steps below list, filter and search: one saved in each chart's editor. */
async function saveChartNotes(s, charts, notes) {
  notes.A = [await saveNote(charts.A, `${s.marker}-A-note`)];
  notes.B = [await saveNote(charts.B, `${s.marker}-B-note`)];
}

async function patientlessRequests(s, A, B) {
  const { sql, marker, context, config } = s;
  await step(s, 'a chart note request that names no patient, a malformed one or two different ones is refused', async () => {
    const dxBefore = sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${A},${B})`);
    const notesBefore = sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${A},${B})`);
    const cases = [['no', ''], ['a malformed', 'demographicNo=12x'], ['two different', `demographicNo=${A}&demographic_no=${B}`]];
    const methods = [
      ['viewNotesOpt', 'offset=0&numToReturn=20'],
      ['listNotes', ''],
      ['search', `searchText=${encodeURIComponent(marker)}`],
      ['addToDx', `issue_code=${DX_CODE}`],
    ];
    for (const [method, extra] of methods) {
      for (const [label, patient] of cases) {
        const query = [`method=${method}`, extra, patient].filter(Boolean).join('&');
        const response = await context.request.get(h.appUrl(config.baseUrl, `/CaseManagementView?${query}`),
          { maxRedirects: 0, failOnStatusCode: false });
        try {
          const body = await response.text();
          h.assert(response.status() === 400,
            `CaseManagementView ${method} with ${label} patient answered HTTP ${response.status()}, not 400`);
          h.assert(!body.includes(`${marker}-A-`) && !body.includes(`${marker}-B-`),
            `CaseManagementView ${method} with ${label} patient exposed a chart note`);
        } finally { await response.dispose(); }
      }
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${A},${B})`) === dxBefore,
      'A refused Disease Registry add wrote a registry row');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${A},${B})`) === notesBefore,
      'A refused chart request wrote a note');
    // The positive control: a list that names A lists A's notes, after B's chart was used last.
    const own = await context.request.get(h.appUrl(config.baseUrl,
      `/CaseManagementView?method=viewNotesOpt&offset=0&numToReturn=20&demographicNo=${A}`), { failOnStatusCode: false });
    try {
      const body = await own.text();
      h.assert(own.status() === 200 && body.includes(`${marker}-A-`) && !body.includes(`${marker}-B-`),
        'A note list that names A does not list A\'s own notes alone');
    } finally { await own.dispose(); }
  });
}

async function filterHandoff(s, charts, A, B, notes) {
  const { sql } = s;
  await step(s, 'a filter applied in A\'s chart is not applied to B\'s note list', async () => {
    const issue = sql.rows(`SELECT issue_id, role FROM issue WHERE code=${q(ISSUE_CODE)}`);
    h.assert(issue.length === 1, `The issue catalog has no single row with code ${ISSUE_CODE}`);
    const [issueId, issueRole] = issue[0];
    const noteA = notes.A[notes.A.length - 1];
    const program = sql.value(`SELECT program_no FROM casemgmt_note WHERE note_id=${noteA}`);
    h.assert(/^\d+$/.test(program), 'A\'s note has no program');
    // A fixture: the issue linked to A's note with SQL, as the issues-filter checks link theirs.
    const link = key(sql.value(`INSERT INTO casemgmt_issue (demographic_no, issue_id, acute, certain, major, resolved,
        program_id, type, update_date)
      VALUES (${A}, ${issueId}, 0, 0, 0, 0, ${program}, ${q(issueRole)}, NOW()); SELECT LAST_INSERT_ID()`), 'chart issue');
    sql.execute(`INSERT INTO casemgmt_issue_notes (id, note_id) VALUES (${link}, ${noteA})`);
    // A second note in A's chart, which the filter must hide: a copy of A's saved note under a new
    // identity and text (writing it in the editor would mean signing and closing the chart).
    const columns = sql.rows('SHOW COLUMNS FROM casemgmt_note').map(([name]) => name).filter(name => name !== 'note_id');
    h.assert(columns.every(name => /^[A-Za-z_][A-Za-z0-9_]*$/.test(name)), 'casemgmt_note has an unexpected column name');
    const copied = columns.map(name => (name === 'uuid' ? 'UUID()'
      : name === 'note' || name === 'history' ? q(`${s.marker}-A-unfiltered`)
        : name === 'update_date' || name === 'create_date' ? 'NOW()' : `\`${name}\``));
    notes.A.push(key(sql.value(`INSERT INTO casemgmt_note (${columns.map(name => `\`${name}\``).join(',')})
      SELECT ${copied.join(',')} FROM casemgmt_note WHERE note_id=${noteA}; SELECT LAST_INSERT_ID()`), 'copied note'));

    const chartA = charts.A;
    // Reloaded so its filter offers the issue just linked.
    await reloadChart(s, chartA, 'A\'s reloaded chart');
    await chartA.locator('#notesFilterToggle').click();
    await chartA.locator(`#filter input[name="issues"][value="${link}"]`).check();
    // Hold A's own note-list request, which follows the filter, until B's list has loaded.
    let release;
    const released = new Promise(resolve => { release = resolve; });
    let held = false;
    const isNoteList = request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/CaseManagementView')
      && new URLSearchParams(request.postData() || '').get('method') === 'viewNotesOpt';
    await chartA.route('**/CaseManagementView', async route => {
      if (!held && isNoteList(route.request())) {
        held = true;
        await released;
      }
      await route.continue();
    });
    const [applied] = await Promise.all([
      chartA.waitForResponse(candidate => candidate.request().method() === 'POST'
        && new URL(candidate.url()).pathname.endsWith('/CaseManagementEntry')
        && new URLSearchParams(candidate.request().postData() || '').get('ajaxview') === 'ajaxView', { timeout: TIMEOUT }),
      chartA.locator('#filter input[type="button"][value="Hide"]').click(),
    ]);
    h.assert(applied.status() === 200, 'Applying the issue filter in A\'s chart failed');
    const deadline = Date.now() + TIMEOUT;
    while (!held && Date.now() < deadline) await chartA.waitForTimeout(100);
    h.assert(held, 'A\'s chart sent no note list after its filter was applied');
    try {
      // B's chart reloads, and lists its notes, while A's filtered list is still on its way.
      await reloadChart(s, charts.B, 'B\'s reloaded chart');
      await waitForListed(charts.B, notes.B.slice(-1),
        'B\'s note list, loaded after A applied an issue filter, is not B\'s own unfiltered list');
    } finally {
      release();
    }
    await waitForListed(chartA, [noteA], 'A\'s note list does not show only the note its filter selects');
    await chartA.unroute('**/CaseManagementView');
  });
}

async function workflow(s) {
  const A = key(s.patient, 'patient');
  const B = createSecondPatient(s);
  ownChartRows(s, [A, B]);
  const charts = await openCharts(s, B);
  const notes = {};
  await saveChartNotes(s, charts, notes);
  await patientlessRequests(s, A, B);
  await filterHandoff(s, charts, A, B, notes);
  await releaseChartLocks(s.context, s.config.baseUrl);
}

if (require.main === module) runWorkflow('chart-session-state-two-window-isolation', workflow, { openPatient: true });
module.exports = { workflow };
