#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Browser regression checks for the CARLOS eChart first render and CPP saves.
 *
 * The script follows the same links a user follows: login, schedule Search,
 * patient result, patient page E-Chart link, then the Social History plus icon
 * and save action. It is intentionally narrow because it guards the filter/JSP
 * interaction that can leave the eChart without its clinical-notes DOM or
 * JavaScript handlers, plus the shared save callback used by all CPP sections.
 *
 * The chart carries clinical prose the OWASP CRS reads as an attack while it does
 * this (see CLINICAL_TEXT_THE_WAF_SCORES), so the run is only meaningful against
 * the packaged front door on :443 -- through bare Tomcat there is no WAF to
 * false-positive and that half of the check proves nothing.
 *
 * FIXTURE. The check writes: a Social History CPP item (a casemgmt_note, its
 * casemgmt_issue_notes link, the patient's casemgmt_issue and casemgmt_cpp rows and
 * an eChart row), an encounter-note draft (casemgmt_tmpsave) and, by opening the
 * chart, a note lock. It used to do all of that on DEMO patient 1, archived the CPP
 * item instead of removing it and posted `cancel` over the demo's own draft, which
 * left two archived notes, a rewritten casemgmt_cpp row, moved update dates on demo
 * notes 27 and 28 and a deleted demo draft behind. It now runs on a FAKE patient it
 * creates (the workflow's owned patient, last name = the run marker) and removes
 * every row it wrote by that patient's key, then asserts each table is empty for it.
 *
 * Note pagination needs a chart with more than one page of notes (20 a page), so the
 * patient is seeded with 45 signed notes dated 10 days apart: the first render shows
 * 20, and two older batches (20 and 5) page in above the reader's note. A chart that
 * pages in nothing would read "settled" without ever arming the poll, so this check
 * now REQUIRES both batches to have been checked for the issue #3961 scroll restore
 * (the demo chart's note count made that count unknowable, and it was only printed).
 *
 * Asserted, in order, on the owned patient:
 *   1. the notes wrapper and container render, the new-note icon and the Social
 *      History plus icon are visible;
 *   2. the older-notes poll arms, pages both older batches in with the reader's note
 *      held in place, stops, and clears the loading throbber;
 *   3. a Social History item whose text the CRS scores saves (ARGS:value), the encounter
 *      note carrying the same prose autosaves (ARGS:note) and the Unresolved Issues
 *      refresh that re-serializes the whole note form (ARGS:caseNote_note) answers 200,
 *      and the saved item shows in the Social History list and in the database;
 *   4. the encounter-note draft the autosave stored is discarded by the page's own
 *      cancel path and leaves no casemgmt_tmpsave row;
 *   5. the saved item archives from its editor (the cut icon), leaves the list, and is
 *      stored archived;
 *   6. (EXPECT_FRONT_DOOR=true) at least one response carried the nginx Server header.
 *
 * Defaults are for the local devcontainer; the database is reached with MYSQL_*:
 *   node scripts/echart-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (the owned patient and its cleanup)
 *   ECHART_SCREENSHOT_DIR=/tmp
 *   ECHART_NOTES_POLL_TIMEOUT_MS=90000
 *   EXPECT_FRONT_DOOR=true fails a run that never saw an nginx-served response
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 *
 * Expected: PASS (no known defect).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

const q = h.sqlString;
const { assert } = h;

// Set when a response arrives through the packaged nginx front door, which is the only
// configuration where the WAF can see (and so false-positive on) the seeded clinical text.
let frontDoorObserved = false;
// Set EXPECT_FRONT_DOOR=true to make a run that never saw an nginx-served response FAIL rather
// than merely report it: the Server header is the only cheap signal, and a hardened proxy that
// strips it would otherwise turn a WAF run into a silent no-op.
const expectFrontDoor = /^(1|true|yes)$/i.test(process.env.EXPECT_FRONT_DOOR || '');
const notesLoadRequests = [];

// The patient's notes: three pages of 20, so two older batches page in.
const TOTAL_NOTES = 45;
const PAGE_SIZE = 20;
const OLDER_BATCHES = Math.ceil(TOTAL_NOTES / PAGE_SIZE) - 1;

// The notes list pages in older notes from a 1s poll, so "settled" means no new fetch
// for several poll ticks. The overall cap keeps a legitimately long chart from hanging
// the check while still failing the runaway-pagination regression.
const NOTES_POLL_QUIET_MS = 4000;
const NOTES_POLL_TIMEOUT_MS = Number(process.env.ECHART_NOTES_POLL_TIMEOUT_MS || 90000);
if (!Number.isSafeInteger(NOTES_POLL_TIMEOUT_MS) || NOTES_POLL_TIMEOUT_MS < 5000
    || NOTES_POLL_TIMEOUT_MS > 240000) {
  throw new Error('ECHART_NOTES_POLL_TIMEOUT_MS must be an integer from 5000 to 240000');
}

// Ordinary clinical prose that the OWASP CRS scores as an attack, twice over. The text
// BEGINS with a pasted internal PACS link on an IP address: rule 931100 (RFI, URL parameter
// using an IP address) is anchored on the whole argument, so it fires only when the value
// starts with the link -- which is exactly how a link gets pasted into a CPP box or a note.
// The link's own query string then carries the literal "&cmd", which is rule 932110 (Windows
// command injection). Either CRITICAL match alone is the whole request at the packaged
// anomaly threshold. Every argument that carries this on POST /carlos/CaseManagementEntry --
// ARGS:value (the CPP body), ARGS:caseNote_note (the encounter note in the serialized
// form), ARGS:note (the draft autosave) and ARGS:noteTxt (the save-on-switch) -- is exempted
// per-argument by exclusion 1010 in debian/assets/modsecurity/REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf.
// This check drives the first three; it does not drive the save-on-switch, because that
// path persists a real encounter note for the patient. That path is pinned by
// CaseManagementCppSaveRegressionTest instead. This string is
// the check's whole point through the front door, so keep it signature-shaped AND keep the
// link first: replacing it with clean prose, or moving the link off the start, makes the
// check green on a re-broken WAF policy.
const CLINICAL_TEXT_THE_WAF_SCORES =
  "http://10.0.0.5/pacs/study?id=1&cmd=view reviewed prior imaging; pt's father had COPD, BP > 140/90";

// backup() re-arms every 5s and autosaves whenever the note textarea differs from the
// value the chart loaded, so one tick plus generous slack is enough to observe a draft save.
const AUTOSAVE_WAIT_MS = 20000;

async function elementState(page, selector) {
  return page.locator(selector).first().evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return {
      visible: !!(rect.width || rect.height || element.getClientRects().length),
      display: getComputedStyle(element).display,
      visibility: getComputedStyle(element).visibility,
      text: (element.innerText || element.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 500),
      htmlLength: element.innerHTML.length,
    };
  });
}

async function assertVisible(page, selector, label) {
  await page.locator(selector).first().waitFor({ state: 'attached', timeout: 15000 });
  const state = await elementState(page, selector);
  assert(state.visible, `${label} was attached but not visible: ${JSON.stringify(state)}`);
  assert(state.display !== 'none' && state.visibility !== 'hidden', `${label} was hidden: ${JSON.stringify(state)}`);
  return state;
}

/**
 * Parks the notes list at the top of the chart and waits for note pagination to stop.
 *
 * Scrolling to the top arms the 1s poll that loads older notes. Once the server has no
 * more notes to give, the poll must stop and the throbber must clear. The regression this
 * guards let the poll run forever -- offset 20, 40, 60, ... on an endless loop, with the
 * loading throbber up for the life of the chart -- because an exhausted batch still comes
 * back as a non-empty response body.
 *
 * Every batch that does land is also checked for issue #3961: the older notes go in above
 * the note the reader was looking at, and that note must stay where it was on screen
 * rather than the pane jumping to the oldest note that just arrived. Keeping the reader's
 * place moves scrollTop off 0, which is also what stops the poll, so after each checked
 * batch the pane is parked at the top again to page in the next one.
 *
 * @return {Promise<number>} how many batches paged in and were checked for the restore
 */
async function assertNotesPaginationSettles(page) {
  const wrapper = page.locator('#encMainDivWrapper').first();
  // Constrain the pane rather than trusting the chart to overflow on its own: the poll
  // only fires when the notes wrapper overflows AND sits at the top, so on a short chart
  // (or a tall window) an unforced check would report "settled" without ever arming the
  // pagination it exists to test.
  const geometry = await wrapper.evaluate((element) => {
    const original = { flex: element.style.flex, height: element.style.height };
    element.style.flex = 'none';
    element.style.height = '80px';
    return { original, scrollHeight: element.scrollHeight, clientHeight: element.clientHeight };
  });
  assert(geometry.scrollHeight > geometry.clientHeight,
    `notes wrapper did not overflow, so the pagination poll was never armed: ${JSON.stringify(geometry)}`);

  // Park at the top and remember the note now showing there, and where: the first one
  // with a layout box, since notes hidden by the encounter.hide_* settings render as
  // display:none. Held on window because the element cannot cross into Node.
  const parkAtTop = () => wrapper.evaluate((element) => {
    element.scrollTop = 0;
    const notes = document.getElementById('encMainDiv');
    const top = notes
      ? Array.from(notes.children).find((note) => note.getClientRects().length > 0) || null
      : null;
    window.__carlosScrollRestoreCheck = top
      ? { note: top, top: top.getBoundingClientRect().top - element.getBoundingClientRect().top }
      : null;
  });
  // Where that note is now, once no notes fetch is in flight (null while one still is).
  const readAnchor = () => wrapper.evaluate((element) => {
    if (typeof notesLoadsInFlight !== 'undefined' && notesLoadsInFlight > 0) {
      return null;
    }
    const anchor = window.__carlosScrollRestoreCheck;
    const notes = document.getElementById('encMainDiv');
    if (!anchor || !notes || !notes.contains(anchor.note)) {
      return { tracked: false };
    }
    // Compare against the first RENDERED note, the same rule parkAtTop() used: a hidden
    // note ahead of the anchor is not a page-in, and neither is a batch that brought only
    // hidden notes. Either way nothing moved, so scrollTop rightly stays at 0.
    const firstRendered = Array.from(notes.children)
      .find((note) => note.getClientRects().length > 0) || null;
    return {
      tracked: true,
      pagedIn: firstRendered !== null && firstRendered !== anchor.note,
      expectedTop: anchor.top,
      top: anchor.note.getBoundingClientRect().top - element.getBoundingClientRect().top,
      scrollTop: element.scrollTop,
    };
  });

  let restoredBatches = 0;
  try {
    // Take the baseline before parking: parking at the top is what arms the poll, and a
    // request it fires while this await is pending must still count as a batch to check.
    let observed = notesLoadRequests.length;
    await parkAtTop();
    const deadline = Date.now() + NOTES_POLL_TIMEOUT_MS;
    let awaitingBatch = false;
    let stableSince = Date.now();
    while (Date.now() < deadline) {
      await page.waitForTimeout(500);
      if (notesLoadRequests.length !== observed) {
        // A chart with many notes legitimately pages in several batches; restart the
        // quiet window and keep waiting for the poll to run out of notes.
        observed = notesLoadRequests.length;
        awaitingBatch = true;
        stableSince = Date.now();
      }
      if (awaitingBatch) {
        const anchor = await readAnchor();
        if (anchor === null) {
          continue;
        }
        awaitingBatch = false;
        if (anchor.tracked && anchor.pagedIn) {
          // Sub-pixel layout rounding aside, the reader's note must not have moved.
          assert(Math.abs(anchor.top - anchor.expectedTop) <= 2 && anchor.scrollTop > 0,
            `older notes paged in above the note the reader was on and the pane jumped away from it `
            + `(issue #3961): ${JSON.stringify(anchor)}`);
          restoredBatches += 1;
          await parkAtTop();
          stableSince = Date.now();
        }
      } else if (Date.now() - stableSince >= NOTES_POLL_QUIET_MS) {
        const throbber = await elementState(page, '#notesLoading');
        assert(!throbber.visible && throbber.display === 'none',
          `notes loading throbber stayed visible after pagination stopped: ${JSON.stringify(throbber)}`);
        return restoredBatches;
      }
    }

    throw new Error(`notes pagination never stopped while parked at the top of the chart; `
      + `${notesLoadRequests.length} viewNotesOpt requests: ${JSON.stringify(notesLoadRequests)}`);
  } finally {
    // Hand the chart back at its real size -- the Social History steps and their
    // screenshots come next, and an 80px notes pane is not the layout they mean to test.
    await wrapper.evaluate((element, original) => {
      element.style.flex = original.flex;
      element.style.height = original.height;
      delete window.__carlosScrollRestoreCheck;
    }, geometry.original).catch(() => {});
  }
}

/**
 * Writes text into the encounter-note textarea that lives inside caseManagementEntryForm
 * and returns whatever was there before.
 *
 * It has to be the textarea inside THAT form, not merely a visible one: only what the form
 * serializes travels on the CPP save's issue-refresh POST, and the autosave reads the same
 * element. The form arrives with the AJAX render of ChartNotes.jsp into #notCPP rather than
 * with the first paint, so wait for it instead of assuming the chart is already whole. The
 * value is assigned directly rather than through fill() because the field sits behind the
 * CPP editor overlay by the time this runs.
 */
async function seedEncounterNoteText(page, text) {
  await page.locator('#caseManagementEntryForm textarea[name="caseNote_note"]')
    .first().waitFor({ state: 'attached', timeout: 15000 });
  // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the argument is this script's own constant or a value just read back out of the same textarea; it is assigned to .value, never used to build a URL or a request target
  return page.evaluate((value) => {
    const form = document.forms['caseManagementEntryForm'];
    const textarea = form && form.querySelector('textarea[name="caseNote_note"]');
    if (!textarea) {
      throw new Error('encounter note textarea was not found inside caseManagementEntryForm');
    }
    const previous = textarea.value;
    textarea.value = value;
    return previous;
  }, text);
}

/**
 * Drops the temporary draft the autosave stored for this provider/patient/program.
 *
 * Calls the page's own deleteAutoSave(), which posts method=cancel and makes the action
 * call deleteTmpSave(). The patient is this run's own, so nothing a clinician wrote can be
 * lost here; the call stays because it is the application's discard path and the step
 * asserts the draft is gone afterwards. clearAutoSaveTimer() first, so no tick can post a
 * new draft after the cancel.
 */
async function discardEncounterNoteDraft(page) {
  const cancelled = page.waitForResponse(
    (response) => isCaseManagementEntryPost(response, 'cancel'), { timeout: 15000 });
  await page.evaluate(() => {
    if (typeof clearAutoSaveTimer !== 'function' || typeof deleteAutoSave !== 'function') {
      throw new Error('clearAutoSaveTimer()/deleteAutoSave() are not defined on the chart page');
    }
    // deleteAutoSave() only posts the cancel; it neither stops the 5s timer nor aborts an
    // autosave already on the wire. clearAutoSaveTimer() does both, so nothing can land
    // after the cancel and recreate the draft this call is removing.
    clearAutoSaveTimer();
    deleteAutoSave();
  });
  const response = await cancelled;
  assert(response.ok(), `discarding the note draft failed with HTTP ${response.status()}`);
}

/**
 * Archives the saved Social History item from its editor, as a clinician removes it from the list.
 * @return {Promise<boolean>} false when the item was not in the list to archive
 */
async function archiveCppNote(page, noteText) {
  const noteLink = page.locator("#divR1I1 a[id^='listNote']").filter({ hasText: noteText }).first();
  if (!(await noteLink.isVisible().catch(() => false))) {
    return false;
  }

  await noteLink.click();
  await assertVisible(page, '#showEditNote', 'saved Social History editor during cleanup');
  await page.locator("#frmIssueNotes input[type='image'][src*='edit-cut.png']").click();
  await noteLink.waitFor({ state: 'detached', timeout: 15000 });
  return true;
}

/**
 * True when the response answers a POST to the note route whose body carries `method=<name>`.
 * The method travels in the body, not the query string, on every call this check watches.
 *
 * The body is form-encoded, so parse it as such rather than pattern-matching the raw string:
 * a substring match would also have to reason about parameter boundaries and percent-encoding,
 * and building the pattern from the caller's argument put a dynamically constructed RegExp on
 * a hot path for no benefit.
 */
function isCaseManagementEntryPost(response, method) {
  const request = response.request();
  if (request.method() !== 'POST') {
    return false;
  }
  if (!new URL(response.url()).pathname.endsWith('/CaseManagementEntry')) {
    return false;
  }
  return new URLSearchParams(request.postData() || '').getAll('method').includes(method);
}

function isAutosaveResponse(response) {
  return isCaseManagementEntryPost(response, 'autosave');
}

/** An autosave whose posted note body carries this run's scored text. */
function isScoredTextAutosaveResponse(response) {
  if (!isAutosaveResponse(response)) {
    return false;
  }
  const note = new URLSearchParams(response.request().postData() || '').get('note') || '';
  return note.includes(CLINICAL_TEXT_THE_WAF_SCORES);
}

async function workflow(s) {
  const { sql, patient, provider, marker, config } = s;
  const screenshotDir = process.env.ECHART_SCREENSHOT_DIR || config.screenshotDir || '/tmp';
  const isUnresolvedIssuesResponse = (response) => {
    const url = new URL(response.url());
    return url.pathname.endsWith('/encounter/displayIssues')
      && url.searchParams.get('cmd') === 'unresolvedIssues'
      && url.searchParams.get('demographicNo') === patient;
  };

  // ---- Fixture cleanup, registered before the first write ----------------------------------------
  // Everything the run writes for the patient, by the patient's key, then asserted gone. The
  // harness removes the patient itself afterwards. The CPP item is archived by a step below, not
  // here, so this removes the archived row too.
  const patientRows = [
    ['casemgmt_note', 'demographic_no'], ['casemgmt_issue', 'demographic_no'], ['casemgmt_cpp', 'demographic_no'],
    ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no'], ['eChart', 'demographicNo'],
  ];
  s.cleanup(() => {
    const owned = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${owned});
      ${patientRows.map(([table, column]) => `DELETE FROM ${table} WHERE ${column}=${patient}`).join(';\n      ')}`);
    assert(sql.value(`SELECT ${patientRows.map(([table, column]) =>
      `(SELECT COUNT(*) FROM ${table} WHERE ${column}=${patient})`).join(' + ')}`) === '0',
    'The owned patient\'s chart rows were not removed');
  });

  // ---- The patient's notes: three pages of them ---------------------------------------------------
  const program = sql.value(`SELECT id FROM program WHERE name='OSCAR' ORDER BY id LIMIT 1`);
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)} AND program_id=${program || 0} LIMIT 1`);
  assert(program && role, 'The default OSCAR program or the test provider\'s role in it is missing');
  const values = [];
  for (let i = 1; i <= TOTAL_NOTES; i += 1) {
    values.push(`(NOW(),DATE_SUB(NOW(), INTERVAL ${i * 10} DAY),${patient},${q(provider)},${q(`${marker} NOTE${String(i).padStart(2, '0')}`)},1,${q(provider)},'',${q(program)},${q(role)},'x',UUID(),'0',0)`);
  }
  sql.execute(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,
    encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived) VALUES ${values.join(',')}`);
  assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === String(TOTAL_NOTES),
    'The owned patient\'s notes were not seeded');
  assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})
    + (SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no=${patient})`) === '0',
  'The owned patient already has a draft or a CPP row, so the rows this run writes cannot be told apart');

  // The nginx Server header is the only cheap signal that the WAF was in the path.
  s.context.on('response', (response) => {
    if (/nginx/i.test(response.headers()['server'] || '')) frontDoorObserved = true;
  });
  // The pagination poll posts method=viewNotesOpt; counted from here so the first render's batch is too.
  s.context.on('request', (request) => {
    const postData = request.postData() || '';
    if (/(?:^|&)method=viewNotesOpt(?:&|$)/.test(postData)) {
      const offsetMatch = /(?:^|&)offset=(\d+)/.exec(postData);
      notesLoadRequests.push({ offset: offsetMatch ? Number(offsetMatch[1]) : null });
    }
  });

  const echart = await s.chart();
  let restoredBatches = 0;
  const cppNoteToken = `Playwright Social History ${Date.now()}`;
  // The scored text goes FIRST so ARGS:value also begins with the pasted link (931100 is
  // anchored on the start of the argument); the token is what the archive step looks for.
  const cppNote = `${CLINICAL_TEXT_THE_WAF_SCORES} — ${cppNoteToken}`;

  await s.step('the chart renders its notes, the new-note icon and the Social History plus icon', async () => {
    await assertVisible(echart, '#encMainDivWrapper', 'clinical notes wrapper');
    const notes = await assertVisible(echart, '#encMainDiv', 'clinical notes container');
    assert(notes.htmlLength > 1000, `clinical notes container was unexpectedly small: ${notes.htmlLength}`);
    await assertVisible(echart, '#newNoteImg', 'new-note icon');
    await assertVisible(echart, "#divR1I1 a[title='Add Item']", 'Social History plus icon');
    await h.screenshot(echart, screenshotDir, 'echart-initial');
  });

  await s.step('note pagination pages both older batches in, holds the reader\'s place and stops', async () => {
    restoredBatches = await assertNotesPaginationSettles(echart);
    assert(restoredBatches >= OLDER_BATCHES,
      `the ${TOTAL_NOTES} seeded notes should page in ${OLDER_BATCHES} older batches above the reader's note, `
      + `${restoredBatches} were checked for the scroll restore (${notesLoadRequests.length} viewNotesOpt requests: `
      + `${JSON.stringify(notesLoadRequests)})`);
  });

  let originalEncounterNote = null;
  await s.step('a Social History item and an encounter-note draft carrying WAF-scored text save and refresh', async () => {
    await echart.locator("#divR1I1 a[title='Add Item']").first().click();
    const editor = await assertVisible(echart, '#showEditNote', 'Social History editor');
    assert(/Social History/i.test(editor.text), `Social History editor did not contain its expected label: ${editor.text}`);
    await h.screenshot(echart, screenshotDir, 'echart-after-social-history-plus');

    // Put clinical text the CRS scores into the ENCOUNTER note before touching the CPP
    // box. The CPP save is not self-contained: its issue-refresh callback re-serializes
    // the whole caseManagementEntryForm (ARGS:caseNote_note), and the 5s draft autosave
    // posts the same text again as ARGS:note. Behind the packaged front door both were
    // 403ed while the CPP item itself saved, which is how a saved Social History entry
    // still produced "403 ... your session has expired". Leave this seeding in place --
    // without it the check drives only the one shape that already worked.
    originalEncounterNote = await seedEncounterNoteText(echart, CLINICAL_TEXT_THE_WAF_SCORES);

    // Arm the autosave wait BEFORE the save click. Waiting a fixed interval and moving on
    // would pass silently in the one case worth catching: if the note timer never re-arms,
    // no autosave is sent, ARGS:note is never exercised, and a re-broken exclusion goes
    // unnoticed. Requiring the request also settles whether assigning textarea.value is
    // enough to trigger it -- backup() polls the value against origCaseNote rather than
    // listening for input events, so no synthetic event is needed, and this proves it.
    // The wait is for THIS run's autosave, the one carrying the scored text as ARGS:note.
    const autosaveResponse = echart.waitForResponse(isScoredTextAutosaveResponse, { timeout: AUTOSAVE_WAIT_MS });
    // Mark the armed wait as handled. If the CPP save fails before it is awaited below, the
    // wait would otherwise reject (timeout, or the browser closing) with no handler and
    // Node would report an unhandled rejection over the failure that actually mattered.
    // `await autosaveResponse` below still rejects normally.
    autosaveResponse.catch(() => {});

    await echart.locator('#noteEditTxt').fill(cppNote);
    const unresolvedIssuesResponse = echart.waitForResponse(isUnresolvedIssuesResponse, { timeout: 15000 });
    await echart.locator("#frmIssueNotes input[type='image'][src*='note-save.png']").click();

    const refreshResponse = await unresolvedIssuesResponse;
    assert(refreshResponse.ok(),
      `Unresolved Issues refresh failed with HTTP ${refreshResponse.status()}: ${h.pathOnly(refreshResponse.url())}`);
    await echart.locator('#divR1I1').filter({ hasText: cppNoteToken }).waitFor({ state: 'visible', timeout: 15000 });

    const draftSave = await autosaveResponse;
    assert(draftSave.ok(),
      `note draft autosave failed with HTTP ${draftSave.status()}; the encounter note text is `
      + `blocked before it reaches the application, and nothing in the UI reports it`);
    await h.screenshot(echart, screenshotDir, 'echart-after-social-history-save');

    // The UI showed the item; the database must hold it, unarchived, on this patient and no other.
    await expectValue(sql, `SELECT CONCAT(COUNT(*), '/', COALESCE(SUM(archived = 0), 0)) FROM casemgmt_note
      WHERE demographic_no=${patient} AND LOCATE(${q(cppNoteToken)}, note) > 0`, '1/1',
    'The saved Social History item is not stored exactly once, unarchived, for the owned patient');
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient}
      AND LOCATE(${q(CLINICAL_TEXT_THE_WAF_SCORES)}, note) > 0`, '1',
    'The autosave answered 200 but stored no draft of the scored text for the owned patient');
  });

  await s.step('the autosaved draft is discarded by the page\'s own cancel path', async () => {
    // Back to the text the editor opened with, so the discard is of a draft that matches what the chart
    // loaded; clearAutoSaveTimer() inside the discard stops any further tick.
    await seedEncounterNoteText(echart, originalEncounterNote === null ? '' : originalEncounterNote);
    await discardEncounterNoteDraft(echart);
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient}`, '0',
      'Discarding the note draft left a casemgmt_tmpsave row for the owned patient');
  });

  await s.step('the saved Social History item archives from its editor and leaves the list', async () => {
    const archived = await archiveCppNote(echart, cppNoteToken);
    assert(archived, 'saved Social History item was not available to archive');
    await expectValue(sql, `SELECT CONCAT(COUNT(*), '/', COALESCE(SUM(archived = 1), 0)) FROM casemgmt_note
      WHERE demographic_no=${patient} AND LOCATE(${q(cppNoteToken)}, note) > 0`, '1/1',
    'Archiving the Social History item did not leave exactly one archived row for the owned patient');
  });

  await s.step('the run went through the packaged front door when one is expected', async () => {
    console.log(`Observed ${notesLoadRequests.length} note pagination requests; `
      + `${restoredBatches} older-note batches paged in with the reader's note held in place`);
    // Say plainly whether the WAF was in the path. This script's default BASE_URL is the
    // devcontainer's bare Tomcat, where CLINICAL_TEXT_THE_WAF_SCORES passes for the boring
    // reason that nothing inspected it -- a green run there is NOT evidence that exclusion
    // 1010 is intact, and only the packaged front door on :443 can give that.
    if (expectFrontDoor && !frontDoorObserved) {
      throw new Error('EXPECT_FRONT_DOOR is set but no response carried an nginx Server header; the run did not go through the packaged front door');
    }
    console.log(frontDoorObserved
      ? 'WAF coverage: requests went through the packaged front door, so the '
        + 'attack-shaped clinical text exercised exclusion 1010'
      : 'WAF coverage: NONE -- no front-door responses seen, so this run says nothing about '
        + 'the WAF exclusions; re-run with BASE_URL pointing at the packaged install on :443');
  });

  // The chart window is still open: let go of its note lock the way the page would, then the harness
  // closes the browser and removes the rows.
  await releaseChartLocks(echart.context(), config.baseUrl);
}

module.exports = { workflow };
if (require.main === module) runWorkflow('echart', workflow, { openPatient: true });
