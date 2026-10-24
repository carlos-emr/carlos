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
 *   1. A chart note list or note search that names no patient, a malformed one or two different
 *      ones is refused (400) without reading either chart; a list that names A lists A's notes.
 *      They fell back to the chart opened last in any window. A Disease Registry add by GET is
 *      refused (405), naming a patient or not, and writes nothing: it fell back the same way, and
 *      wrote with no privilege check.
 *   2. A's notes filtered by an issue (Filter ▸ the issue ▸ Hide) while B's chart reloads its notes
 *      before A's own list has loaded: B lists its own notes and A's list is filtered. The editor
 *      hands the filters to the next note list through the session; that handoff was login-wide,
 *      so B's list consumed A's filter and showed none of B's notes. The overlap is held open by
 *      delaying A's own note-list request in the browser.
 *   3. A new note from the notes service (POST ws/rs/notes/{patient}/getCurrentNote, a REST
 *      client of this login) for A's chart, entered as a face-to-face visit the way the day
 *      sheet's E link enters it, and for B's, entered after it as its Master Record's E-Chart link
 *      enters it, a telephone visit: each carries its own chart's visit type. It took the visit type of
 *      the chart entered last in any window. A new note also answered HTTP 500 until #4784 was
 *      fixed (an unsaved note's link lookup).
 *      A save posted with no note body saves nothing; it dereferenced the missing body.
 *   4. A Disease Registry add by POST, as a chart's scripts post, after B's chart was entered last:
 *      with no patient, a malformed one or two different ones it is refused (400) and writes
 *      nothing; naming A, it files the code for A and leaves B's registry as it was.
 *   5. B's message, opened from B's message list ▸ Write to Encounter, with B's new chart holding
 *      its note list back: A's note list, loaded meanwhile as A's chart loads it, does not carry the
 *      message, and B's new note does. The login-wide bean held the pending message for whichever
 *      chart's note list rendered next: A's list wrote B's message into its page's script, and
 *      cleared it.
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
 * casemgmt_issue linked to A's note, the registry code A's add files (a refused request's is
 * asserted absent), and one message linked to B, delivered to the test provider. Cleanup removes
 * every owned row by key and asserts it gone. No clinic-wide state is changed.
 */
const h = require('./lib/playwright-harness');
const { csrfTokenPresent } = require('./lib/playwright-ui');
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
// The visit types the day sheet's E link and the Master Record's E-Chart link enter a chart with.
const FACE_TO_FACE = 'face to face encounter with client';
const TELEPHONE = 'telephone encounter with client';
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
    const children = notes.length ? ['casemgmt_issue_notes', 'casemgmt_note_ext', 'casemgmt_note_link']
      .map(table => ` + (SELECT COUNT(*) FROM ${table} WHERE note_id IN (${notes.join(',')}))`).join('') : '';
    const issueLinks = issues.length
      ? ` + (SELECT COUNT(*) FROM casemgmt_issue_notes WHERE id IN (${issues.join(',')}))` : '';
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo IN (${list}))
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${list}))${children}${issueLinks}`) === '0',
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

/**
 * Enter a patient's chart again in its own window with the given visit type, the address the day
 * sheet's E link opens (without an appointment). As for a reload, the replaced document's
 * note-lock release beacon is excused.
 */
async function enterChart(s, chart, patient, encType, label) {
  const mark = s.recorder.requestFailures.length;
  const entry = new URLSearchParams({ providerNo: s.provider, appointmentNo: '', demographicNo: patient,
    curProviderNo: s.provider, reason: '', encType, curDate: s.sql.value('SELECT CURDATE()'),
    appointmentDate: '', startTime: '', status: '' });
  await h.gotoApp(chart, s.config.baseUrl, `/encounter/IncomingEncounter?${entry}`);
  await settle(chart, label);
  await waitForNavbars(chart, TIMEOUT);
  h.assert(new URL(chart.url()).searchParams.get('demographicNo') === patient, `${label} is another patient's chart`);
  for (let i = s.recorder.requestFailures.length - 1; i >= mark; i--) {
    const failure = s.recorder.requestFailures[i];
    if (failure.resourceType === 'ping' && /ERR_ABORTED/.test(failure.errorText || '')
        && new URL(failure.url).pathname.endsWith('/CaseManagementEntry')) {
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
    // The Disease Registry add writes, so a GET is refused (405) before its patient is even read,
    // also when it names a patient; the reads refuse a request without one valid patient (400).
    const methods = [
      ['viewNotesOpt', 'offset=0&numToReturn=20', 400],
      ['listNotes', '', 400],
      ['search', `searchText=${encodeURIComponent(marker)}`, 400],
      ['addToDx', `issue_code=${DX_CODE}`, 405],
    ];
    for (const [method, extra, refusal] of methods) {
      const named = method === 'addToDx' ? [...cases, ['A\'s', `demographicNo=${A}`]] : cases;
      for (const [label, patient] of named) {
        const query = [`method=${method}`, extra, patient].filter(Boolean).join('&');
        const response = await context.request.get(h.appUrl(config.baseUrl, `/CaseManagementView?${query}`),
          { maxRedirects: 0, failOnStatusCode: false });
        try {
          const body = await response.text();
          h.assert(response.status() === refusal,
            `CaseManagementView ${method} with ${label} patient answered HTTP ${response.status()}, not ${refusal}`);
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

/** POST CaseManagementView with the chart's token, as the chart's own scripts post. */
async function postChartView(s, chart, fields) {
  const token = await csrfTokenPresent(chart);
  const response = await s.context.request.post(h.appUrl(s.config.baseUrl, '/CaseManagementView'), {
    data: new URLSearchParams([...fields, ['CSRF-TOKEN', token]]).toString(),
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': token, 'X-Requested-With': 'XMLHttpRequest' },
    maxRedirects: 0, failOnStatusCode: false,
  });
  try {
    return { status: response.status(), body: await response.text() };
  } finally { await response.dispose(); }
}

async function registryAddByPost(s, charts, A, B) {
  const { sql } = s;
  await step(s, 'a Disease Registry add by POST is refused without one valid patient and files the code for the patient it names', async () => {
    const registryOf = patient => JSON.stringify(sql.rows(`SELECT dxresearch_code, status, coding_system FROM dxresearch
      WHERE demographic_no=${patient} ORDER BY dxresearch_no`));
    const before = { A: registryOf(A), B: registryOf(B) };
    for (const [label, patient] of [['no', []], ['a malformed', [['demographicNo', '12x']]],
      ['two different', [['demographicNo', A], ['demographic_no', B]]]]) {
      const refused = await postChartView(s, charts.A, [['method', 'addToDx'], ['issue_code', DX_CODE], ...patient]);
      h.assert(refused.status === 400, `A Disease Registry add with ${label} patient answered HTTP ${refused.status}, not 400`);
    }
    h.assert(registryOf(A) === before.A && registryOf(B) === before.B, 'A refused Disease Registry add wrote a registry row');
    // B's chart was entered last; the add names A and files the code for A alone.
    const added = await postChartView(s, charts.A, [['method', 'addToDx'], ['issue_code', DX_CODE], ['demographicNo', A]]);
    h.assert(added.status === 200, `A's Disease Registry add answered HTTP ${added.status}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${A} AND dxresearch_code=${q(DX_CODE)}
      AND status='A'`) === '1', 'A\'s Disease Registry add did not file the code for A');
    h.assert(registryOf(B) === before.B, 'A\'s Disease Registry add changed B\'s registry');
  });
}

/** A message linked to `patient`, delivered to the test provider, owned by this run. */
function linkedMessage(s, patient, subject, body) {
  const { sql, provider, marker } = s;
  const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'No current messenger location is configured');
  const id = key(h.insertId(sql, `INSERT INTO messagetbl(thedate, theime, themessage, thesubject, sentby, sentto,
      sentbyNo, sentByLocation, type)
    VALUES(CURDATE(), CURTIME(), ${q(body)}, ${q(subject)}, 'FAKE-PW sender', 'FAKE-PW recipient', ${q(provider)},
      ${location}, 0)`, 'message'), 'message');
  s.cleanup(() => {
    sql.execute(`DELETE FROM msgDemoMap WHERE messageID=${id} AND demographic_no=${patient};
      DELETE FROM messagelisttbl WHERE message=${id} AND provider_no=${q(provider)};
      DELETE FROM messagetbl WHERE messageid=${id} AND thesubject LIKE ${q(`${marker}%`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE messageid=${id})
      + (SELECT COUNT(*) FROM messagelisttbl WHERE message=${id})
      + (SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${id})`) === '0', 'The owned message was not removed');
  });
  sql.execute(`INSERT INTO messagelisttbl(message, provider_no, status, remoteLocation, destinationFacilityId,
      sourceFacilityId) VALUES(${id}, ${q(provider)}, 'new', ${location}, 0, 0);
    INSERT INTO msgDemoMap(messageID, demographic_no) VALUES(${id}, ${patient})`);
  return id;
}

async function writeToEncounter(s, charts, A, B) {
  const { marker, context, config } = s;
  // The pages write the message into their scripts JavaScript-encoded, which escapes the marker's
  // hyphen, so the pages are searched for a part of the text that the encoding leaves as written.
  const token = `pendingEncounter${marker.replace(/[^A-Za-z0-9]/g, '')}`;
  const text = `${marker} ${token}`;
  const id = linkedMessage(s, B, `${marker} write to encounter`, text);
  await step(s, 'Write to Encounter from B\'s message fills B\'s new note, and A\'s note list, loaded meanwhile, does not carry it', async () => {
    // B's message, opened from B's message list.
    const view = await context.newPage();
    await h.gotoApp(view, config.baseUrl,
      `/messenger/ViewMessage?from=encounter&demographic_no=${B}&messageID=${id}&msgCount=1&orderBy=date&messagePosition=0`);
    await settle(view, 'B\'s message');
    // Hold the note list of the chart the message opens, so A's list loads while B's entry is pending.
    let release;
    const released = new Promise(resolve => { release = resolve; });
    let heldBy = null;
    const hold = async route => {
      const form = new URLSearchParams(route.request().postData() || '');
      if (!heldBy && route.request().method() === 'POST' && form.get('method') === 'viewNotesOpt'
          && form.get('demographicNo') === B && route.request().frame().page() !== charts.B) {
        heldBy = route.request().frame().page();
        await released;
      }
      await route.continue();
    };
    await context.route('**/CaseManagementView', hold);
    try {
      await view.locator('input[name="writeToEncounter"]').click();
      const deadline = Date.now() + TIMEOUT;
      while (!heldBy && Date.now() < deadline) await view.waitForTimeout(100);
      h.assert(heldBy, 'Write to Encounter opened no chart that lists B\'s notes');
      // A's note list, as A's chart loads it, while B's chart was entered from the message.
      const listed = await postChartView(s, charts.A, [['method', 'viewNotesOpt'], ['offset', '0'], ['numToReturn', '20'],
        ['demographicNo', A]]);
      h.assert(listed.status === 200, `A's note list answered HTTP ${listed.status}`);
      h.assert(!listed.body.includes(token), 'A\'s note list carries the message written to B\'s chart');
    } finally {
      release();
    }
    const chartB = heldBy;
    await chartB.waitForFunction(wanted => {
      const editor = window.caseNote && document.getElementById(window.caseNote);
      return Boolean(editor && editor.value.includes(wanted));
    }, text, { timeout: TIMEOUT });
    // B's own page carries the token as written, so A's list could not have hidden it by encoding.
    h.assert((await chartB.content()).includes(token), 'B\'s chart page does not carry the message token as written');
    await context.unroute('**/CaseManagementView', hold);
    // Closing B's second chart window releases its note lock by a beacon, which the browser
    // reports as an aborted ping because the window is gone.
    const mark = s.recorder.requestFailures.length;
    await chartB.close({ runBeforeUnload: false });
    await view.close();
    for (let i = s.recorder.requestFailures.length - 1; i >= mark; i--) {
      const entry = s.recorder.requestFailures[i];
      if (entry.resourceType === 'ping' && /ERR_ABORTED/.test(entry.errorText || '')
          && new URL(entry.url).pathname.endsWith('/CaseManagementEntry')) {
        s.recorder.requestFailures.splice(i, 1);
      }
    }
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

async function newNoteVisitTypes(s, charts, A, B) {
  await step(s, 'a new note from the notes service carries the visit type of its own patient\'s chart', async () => {
    await enterChart(s, charts.A, A, FACE_TO_FACE, 'A\'s chart, entered as a face-to-face visit');
    // Then B's, as its Master Record's E-Chart link enters it: the chart entered last.
    await enterChart(s, charts.B, B, TELEPHONE, 'B\'s chart, entered as a telephone visit');
    for (const [label, patient, chart, expected] of [['A', A, charts.A, FACE_TO_FACE], ['B', B, charts.B, TELEPHONE]]) {
      const answer = await chart.request.post(`${s.config.baseUrl}/ws/rs/notes/${patient}/getCurrentNote`,
        { data: { note_edit: 'new' } });
      h.assert(answer.status() === 200, `A new note for ${label} answered HTTP ${answer.status()}`);
      const note = await answer.json();
      h.assert(note.noteId == null, `The new note for ${label} names a saved note`);
      h.assert(note.encounterType === expected, `${label}'s new note has the visit type "${note.encounterType}", `
        + `not that of ${label}'s chart${label === 'A' && note.encounterType === TELEPHONE ? ' (B\'s, the chart entered last)' : ''}`);
    }
  });
}

async function emptyNoteSave(s, charts, A) {
  await step(s, 'a notes service save without a note saves nothing', async () => {
    const notes = `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${A}`;
    const before = s.sql.value(notes);
    // A REST client of this login posting no note body (POST ws/rs/notes/{patient}/save).
    const answer = await charts.A.request.post(`${s.config.baseUrl}/ws/rs/notes/${A}/save`,
      { headers: { 'Content-Type': 'application/json' }, data: Buffer.alloc(0) });
    try {
      h.assert(answer.status() < 300, `A notes service save without a note answered HTTP ${answer.status()}`);
    } finally { await answer.dispose(); }
    h.assert(s.sql.value(notes) === before, 'A notes service save without a note wrote a note');
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
  await registryAddByPost(s, charts, A, B);
  await filterHandoff(s, charts, A, B, notes);
  await newNoteVisitTypes(s, charts, A, B);
  await emptyNoteSave(s, charts, A);
  await writeToEncounter(s, charts, A, B);
  await releaseChartLocks(s.context, s.config.baseUrl);
}

if (require.main === module) runWorkflow('chart-session-state-two-window-isolation', workflow, { openPatient: true });
module.exports = { workflow };
