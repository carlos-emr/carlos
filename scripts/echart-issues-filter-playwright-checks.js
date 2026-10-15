#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Chart issues: assigning an issue to a note, listing it in the Issues module, filtering the notes by it,
 * getting every note back, resolving it, and the notes filter panel.
 *
 * WHY THIS CHECK EXISTS. A clinician uses an issue to gather the notes of one problem: the note is linked to a
 * patient issue (casemgmt_issue through casemgmt_issue_notes), the Issues module in the chart's right column
 * lists that issue, clicking it narrows the notes area to the notes linked to it, and the module's heading is
 * the way back to every note. Resolving the issue moves it from the Issues module to Resolved Issues. Nothing
 * else in the suite drives any of this: cpp-note-extension-archive and gap-clinical-cpp-boxes save CPP items
 * and read their link to the box's own issue, echart-navbar-modules only looks at the module headings.
 *
 * What the application does, read from the source before asserting:
 *   - Assign: the CPP item editor (the "+" of a CPP box) has an "Assign Issues" text box (#issueAutocompleteCPP,
 *     jQuery UI autocomplete on CaseManagementEntry?method=issueList). The main note editor's own controls
 *     (#issueAutocomplete, #asgnIssues) are driven by newCaseManagementView.js.jsp but no template renders them.
 *   - Issues module (EctDisplayIssues2Action): the patient's unresolved casemgmt_issue rows, minus the CPP
 *     codes. An item runs setIssueCheckbox(<casemgmt_issue.id>); filter(false), an ajax POST of
 *     CaseManagementEntry (method=edit, ajaxview) that replaces the notes area with the linked notes.
 *   - Heading: EctDisplayIssues2Action / EctDisplayResolvedIssues2Action give the heading the statement
 *     $('check_issue').value='';document.caseManagementViewForm.submit(), which LeftNavBarDisplay.jsp writes
 *     into an onclick through the javaScriptAttribute encoder.
 *   - Resolve: the note editor's "Reference Unresolved Issues" checklist (noteIssueList.jsp) holds the
 *     issue's Resolved radio; issueChange saves casemgmt_issue.resolved at once.
 *   - Filter panel: ChartNotes.jsp renders <div id="filter" style="display:none"> with the provider, role,
 *     sort and issue filters; showFilter() is its only revealer.
 *
 * THE CATALOG ISSUE. The brief names "issue 250"; the packaged catalog has no such issue (72 rows: the CPP and
 * system codes and the counsellor and CSW lists). The check assigns catalog row CTCMM1000 "Safety" (role
 * counsellor), which the chart's issue search offers the test provider, read by code so no id is hard-coded.
 *
 * ONE ENTRY PER DEFECT. A script stops at its first failing step, so one run cannot pin two defects. The
 * ECHART_ISSUES_PIN variable picks the entry (the manifest runs the script once per value):
 *   unset    The real assignment path, CPP editor > Assign Issues > Sign & Save. Pinned to finding 225: the box
 *            suggests the issue and takes the pick, and the save links the note to its own CPP issue only.
 *            The steps after the pin (module, filter, heading) are the same functions the seeded entries run.
 *   editor   The main note editor offers an Assign Issues search box and button. Pinned to finding 186.
 *   heading  A SEEDED link (see below), the Issues module lists the issue, clicking it lists only the linked
 *            note, then the heading brings every note back. Pinned to finding 226.
 *   resolve  A SEEDED link, the module lists the issue, then resolving: the clinician's way to the control is
 *            recorded, a control step proves the server side by revealing the hidden checklist with a script
 *            (resolved=1, Resolved Issues lists it, its item filters to the note), and the pin asserts that a
 *            clinician could reach the control. Pinned to finding 227.
 *   panel    A SEEDED link, the filter panel is in the page and closed, a clinician can open it. Pinned to
 *            finding 185.
 *   radios   A SEEDED link; with a FULL-rights login (the test provider) and the checklist revealed by a script, the
 *            Certain, Uncertain, Major and Not Major radios and the role box of the issue are enabled. Pinned to
 *            finding 261: noteIssueList.jsp writes disabled="${disabled}" for them in the unresolved list, which
 *            prints disabled="false", a boolean attribute, so they are disabled whatever the user's rights.
 *
 * SEEDED LINK. Findings 186 and 225 mean no UI route assigns an issue, so the entries that exercise what comes
 * after the assignment write the casemgmt_issue row and its casemgmt_issue_notes link with SQL. That is a
 * fixture, not a UI path, and its step says so. Everything after it is driven through the chart.
 *
 * Fixtures: the workflow's owned FAKE patient; two notes written through the chart's note editor (Sign & Save);
 * in the unset entry one CPP Social History item instead of the first note. Cleanup deletes, by the patient key,
 * every note (with its link, extension and issue rows), the patient's casemgmt_issue rows, the CPP summary, draft,
 * note lock and eChart row, and asserts them all gone. The catalog row is only read. No clinic-wide state changes.
 *
 * Expected: every step passes except the pinned one of each entry (see the manifest); a step named in an
 * expectedFailure contains only the assertion its finding breaks.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');

const TIMEOUT = 30000;
const NOTE = 'textarea[name="caseNote_note"]';
const SIGN_SAVE = '#signSaveImg';
/** Catalog issue "Safety" (role counsellor): offered by the issue search to the test provider, one match. */
const ISSUE_CODE = 'CTCMM1000';
/** The Social History CPP box: its "+", the item editor and the editor's own Sign & Save. */
const CPP_PLUS = '#divR1I1 a[title="Add Item"]';
const CPP_DIALOG = '#showEditNote';

/** The step labels the manifest's expectedFailure entries name; one place, so the script and the manifest agree. */
const STEP = {
  assign: 'the issue picked in the CPP editor Assign Issues box is assigned to the note: a casemgmt_issue row linked through casemgmt_issue_notes',
  editor: 'the note editor offers an Assign Issues search box and an Assign button',
  heading: 'clicking the Issues heading brings every note back',
  resolve: 'a clinician can reach the control that resolves an issue from the note editor',
  panel: 'the notes filter panel can be opened from the chart',
  radios: 'the Certain, Uncertain, Major and Not Major radios and the role box of an issue are enabled for a login with full rights',
};

const PINS = ['editor', 'heading', 'resolve', 'panel', 'radios'];
// ECHART_ISSUES_PIN selects which finding the run pins (see the header); judged when the check runs, never when
// the module is required, so a stray value in someone's shell cannot break a test that only requires the file.
const PIN = (process.env.ECHART_ISSUES_PIN || '').trim();
/** ECHART_ISSUES_PIN must be unset or one of editor, heading, resolve, panel, radios. */
function validatePin(value = PIN) {
  if (!['', ...PINS].includes(value)) {
    throw new Error(`ECHART_ISSUES_PIN must be unset or ${PINS.join(', ')}, not ${value}`);
  }
}

async function workflow(s) {
  validatePin();
  const { sql, patient, marker, config } = s;
  const q = h.sqlString;
  const seeded = ['heading', 'resolve', 'panel', 'radios'].includes(PIN);

  const issueRow = sql.rows(`SELECT issue_id, description, role FROM issue WHERE code=${q(ISSUE_CODE)}`);
  if (issueRow.length !== 1) throw new h.SkipCheck(`The issue catalog has no single row with code ${ISSUE_CODE} to assign`);
  const [issueId, issueName, issueRole] = issueRow[0];
  const cppIssueId = sql.value("SELECT issue_id FROM issue WHERE code='SocHistory'");
  h.assert(/^\d+$/.test(issueId) && /^\d+$/.test(cppIssueId), 'The catalog issue ids are not numbers');

  // ---- Fixtures --------------------------------------------------------------------------------
  s.cleanup(() => {
    // The patient is owned by this run, so everything below is the run's, deleted by the patient key.
    const ids = rows => (rows.length ? rows.map(([id]) => { h.assert(/^\d+$/.test(id), 'An owned id is not a number'); return id; }).join(',') : '0');
    const notes = ids(sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`));
    const issues = ids(sql.rows(`SELECT id FROM casemgmt_issue WHERE demographic_no=${patient}`));
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes}) OR id IN (${issues});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
      DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
      DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id IN (${notes}) OR id IN (${issues}))
      + (SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id IN (${notes}))
      + (SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id IN (${notes}))
      + (SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient})`) === '0',
    'Owned chart notes, issues, links, summary, drafts or locks were not removed');
  });

  // ---- Database helpers ------------------------------------------------------------------------
  /** The owned note whose text holds `text` (the check gives every note its own text), or null. */
  function noteWith(text) {
    const found = sql.rows(`SELECT note_id, signed, program_no FROM casemgmt_note WHERE demographic_no=${patient}
      AND LOCATE(${q(text)}, note) > 0 ORDER BY note_id`);
    h.assert(found.length <= 1, `More than one note holds the text of this step (${found.length})`);
    return found.length ? { id: found[0][0], signed: found[0][1], program: found[0][2] } : null;
  }
  async function waitForNote(text, message) {
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND LOCATE(${q(text)}, note) > 0`, '1', message);
    return noteWith(text);
  }
  /** The patient's casemgmt_issue row for a catalog issue, or null; `linkedTo` also requires the note link. */
  function caseIssue(catalogId, linkedTo = null) {
    const found = sql.rows(`SELECT ci.id, ci.resolved FROM casemgmt_issue ci
      ${linkedTo ? `JOIN casemgmt_issue_notes cin ON cin.id=ci.id AND cin.note_id=${linkedTo}` : ''}
      WHERE ci.demographic_no=${patient} AND ci.issue_id=${catalogId}`);
    h.assert(found.length <= 1, `The patient has ${found.length} casemgmt_issue rows for issue ${catalogId}`);
    return found.length ? { id: found[0][0], resolved: found[0][1] } : null;
  }

  // ---- Page helpers ----------------------------------------------------------------------------
  const noteField = chart => chart.locator(NOTE).first();
  const chartSaveRequest = method => request => request.method() === 'POST' && /\/CaseManagementEntry(\?|$)/.test(request.url())
    && new URLSearchParams(request.postData() || '').get('method') === method;
  /** The listed note's whole block (text, Encounter Date, Assigned Issues), found by its note id. */
  const noteContainer = (chart, id) => chart.locator('div[id^="nc"]').filter({ has: chart.locator(`div#n${id}`) }).first();
  /** The ids of the saved notes the chart lists, in listing order (the new-note editor, n0, is not a saved note). */
  const listedIds = chart => chart.evaluate(() => [...document.querySelectorAll('div[id^="nc"]')]
    .map(container => (container.querySelector('div[id^="n"]:not([id^="nc"])') || {}).id || '')
    .filter(id => /^n[1-9]\d*$/.test(id)).map(id => id.slice(1)));
  /** Poll until the listed notes are exactly `wanted` (order aside), or fail saying what was listed. */
  async function waitForListed(chart, wanted, message, timeoutMs = 15000) {
    const sorted = ids => [...ids].sort().join(',');
    const deadline = Date.now() + timeoutMs;
    let listed = [];
    do {
      // A page that is navigating answers evaluate() with an error; the next poll finds the new page.
      listed = await listedIds(chart).catch(() => []);
      if (sorted(listed) === sorted(wanted)) return listed;
      await chart.waitForTimeout(200);
    } while (Date.now() < deadline);
    h.assert(false, `${message}: the chart lists notes [${listed.join(', ')}], expected [${wanted.join(', ')}]`);
    return listed;
  }
  /** Leave a chart that stays open: release its note lock, then close the window. */
  async function leaveChart(chart) {
    await releaseChartLocks(s.context, config.baseUrl, [chart]);
    await chart.close();
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient}`, '0',
      'Leaving the chart left a note lock behind');
  }
  /** Press a toolbar button that submits the page and closes the chart window (saveAndExit). */
  async function pressAndExit(chart, button) {
    const closed = chart.waitForEvent('close', { timeout: TIMEOUT });
    closed.catch(() => {});
    // close.jsp reloads the window that opened the chart; wait for that reload or the next navigation collides.
    const openerReloaded = s.master.waitForEvent('load', { timeout: TIMEOUT });
    openerReloaded.catch(() => {});
    const [response] = await Promise.all([
      chart.waitForResponse(candidate => chartSaveRequest('saveAndExit')(candidate.request()), { timeout: TIMEOUT }),
      chart.locator(button).first().click({ timeout: TIMEOUT }).catch((error) => {
        // The window closes under the click when the save succeeds; that is the outcome, not an error.
        if (!chart.isClosed()) throw error;
      }),
    ]);
    h.assert(response.status() < 400, `${button} answered HTTP ${response.status()}`);
    await closed;
    await openerReloaded;
    await s.master.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  }
  /** Write a signed note through the chart's note editor (Sign & Save closes the chart). */
  async function writeSignedNote(text) {
    const chart = await s.chart();
    await noteField(chart).waitFor({ state: 'visible', timeout: TIMEOUT });
    await noteField(chart).fill(text);
    await pressAndExit(chart, SIGN_SAVE);
    const note = await waitForNote(text, 'Sign & Save did not write the note');
    h.assert(note.signed === '1', 'The note was not signed');
    return note;
  }
  /** The Issues module's items (the description each one shows), read from the open chart. */
  async function moduleItems(chart, list) {
    const items = chart.locator(`${list} a.links`);
    return (await items.allInnerTexts()).map(text => text.trim());
  }

  // ---- Steps shared by the entries ---------------------------------------------------------------
  /** Write notes A (to be linked) and B (never linked) through the note editor, then seed A's link. */
  const notes = {};
  async function seedNotesAndLink() {
    await s.step('(fixture) two owned notes are written and signed through the note editor', async () => {
      notes.a = (await writeSignedNote(`${marker} note alpha`)).id;
      notes.b = (await writeSignedNote(`${marker} note beta`)).id;
    });
    // Findings 186 and 225: no UI route assigns the issue, so the link is written with SQL.
    await s.step('(fixture) the issue is linked to the first note with SQL, because no UI route assigns it (findings 186 and 225)', async () => {
      const program = sql.value(`SELECT program_no FROM casemgmt_note WHERE note_id=${notes.a}`);
      h.assert(/^\d+$/.test(program), 'The first note has no program');
      const id = sql.value(`INSERT INTO casemgmt_issue (demographic_no, issue_id, acute, certain, major, resolved, program_id, type, update_date)
        VALUES (${patient}, ${issueId}, 0, 0, 0, 0, ${program}, ${q(issueRole)}, NOW()); SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(id), 'The casemgmt_issue fixture was not created');
      sql.execute(`INSERT INTO casemgmt_issue_notes (id, note_id) VALUES (${id}, ${notes.a})`);
      h.assert(caseIssue(issueId, notes.a) !== null, 'The seeded link is not readable through casemgmt_issue_notes');
    });
  }

  /**
   * The module lists the issue; `noteShowsIssue` also asks the listing to say "Assigned Issues <issue>" on the
   * linked note, which the chart does for an ordinary note and not for a CPP item (it draws a CPP item without
   * the Assigned Issues line).
   */
  async function stepModuleLists(noteShowsIssue = true) {
    const label = noteShowsIssue
      ? 'the Issues module lists the assigned issue, the Resolved Issues module does not, and the linked note shows it'
      : 'the Issues module lists the assigned issue and the Resolved Issues module does not';
    await s.step(label, async () => {
      const chart = await s.chart();
      await waitForListed(chart, [notes.a, notes.b], 'The chart does not list both owned notes');
      const link = caseIssue(issueId, notes.a);
      h.assert(link !== null && link.resolved === '0', 'The patient has no unresolved casemgmt_issue linked to the note');
      const unresolved = await moduleItems(chart, '#unresolvedIssueslist');
      h.assert(unresolved.length === 1 && unresolved[0] === issueName,
        `The Issues module lists [${unresolved.join(' | ')}], expected only "${issueName}" (the CPP issue is not listed)`);
      const onclick = await chart.locator('#unresolvedIssueslist a.links').first().getAttribute('onclick');
      h.assert((onclick || '').includes(`setIssueCheckbox('${link.id}')`),
        `The module item does not filter on casemgmt_issue ${link.id}: ${onclick}`);
      const resolved = await moduleItems(chart, '#resolvedIssueslist');
      h.assert(resolved.length === 0, `The Resolved Issues module lists [${resolved.join(' | ')}] before anything is resolved`);
      const otherText = await noteContainer(chart, notes.b).innerText();
      h.assert(!otherText.includes(issueName), 'The note that was never linked shows the issue as assigned');
      if (!noteShowsIssue) return;
      const linkedText = await noteContainer(chart, notes.a).innerText();
      h.assert(linkedText.includes(issueName), `The linked note does not show its assigned issue "${issueName}": ${linkedText.replace(/\s+/g, ' ').slice(0, 200)}`);
    });
  }

  async function stepClickFilters() {
    await s.step('clicking the issue in the Issues module lists only the linked note', async () => {
      const chart = await s.chart();
      const before = Number(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`));
      const filterPost = candidate => chartSaveRequest('edit')(candidate.request())
        && new URLSearchParams(candidate.request().postData() || '').get('ajaxview') === 'ajaxView';
      const [response] = await Promise.all([
        chart.waitForResponse(filterPost, { timeout: TIMEOUT }),
        chart.locator('#unresolvedIssueslist a.links').first().click({ timeout: TIMEOUT }),
      ]);
      h.assert(response.status() === 200, `The filter request answered HTTP ${response.status()}`);
      await waitForListed(chart, [notes.a], 'Clicking the issue did not narrow the notes to the linked note');
      // The listing settles before it is trusted: the other note must stay out.
      await chart.waitForTimeout(500);
      h.assert((await listedIds(chart)).join(',') === notes.a, 'The note that was never linked came back into the filtered listing');
      h.assert(Number(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`)) === before, 'Filtering changed the notes');
    });
  }

  /** Click the Issues heading; the finding-226 pin when it is the last step of the heading entry. */
  async function stepHeading() {
    await s.step(STEP.heading, async () => {
      const chart = await s.chart();
      await chart.locator('#unresolvedIssues .nav-menu-title a').first().click({ timeout: TIMEOUT });
      await waitForListed(chart, [notes.a, notes.b], 'After the heading was clicked the notes area does not list every note', 8000);
    });
  }

  // ---- The unset entry: the real assignment path ----------------------------------------------------
  if (PIN === '') {
    await s.step('(control) a plain note is written and signed through the note editor', async () => {
      notes.b = (await writeSignedNote(`${marker} note beta`)).id;
    });

    const cppText = `${marker} social history item with an assigned issue`;
    await s.step('(control) the CPP editor Assign Issues box suggests the issue and takes the pick', async () => {
      const chart = await s.chart();
      await chart.locator(CPP_PLUS).first().click({ timeout: TIMEOUT });
      await chart.locator(CPP_DIALOG).waitFor({ state: 'visible', timeout: TIMEOUT });
      await chart.locator('#noteEditTxt').fill(cppText);
      h.assert(await chart.locator('#issueAutocompleteCPP').isVisible(), 'The CPP editor has no visible Assign Issues box');
      const picked = await ui.typeAutocomplete(chart, '#issueAutocompleteCPP', issueName, {
        option: issueName, hidden: '#newIssueId', timeout: TIMEOUT });
      h.assert(picked === issueId, `Picking "${issueName}" registered issue ${picked}, expected ${issueId}`);
      h.assert(await chart.locator('#issueAutocompleteCPP').inputValue() === issueName, 'The Assign Issues box does not show the picked issue');
    });

    await s.step('(control) Sign & Save in the CPP editor saves the item and links it to its own CPP issue', async () => {
      const chart = await s.chart();
      const arrived = chart.waitForResponse(candidate => /method=issueNoteSave/.test(candidate.url()) && candidate.request().method() === 'POST', { timeout: TIMEOUT });
      await chart.locator(`${CPP_DIALOG} input[type="image"][title="Sign & Save"]`).click({ timeout: TIMEOUT });
      const response = await arrived;
      h.assert(response.status() < 400, `The CPP Sign & Save answered HTTP ${response.status()}`);
      await chart.locator(CPP_DIALOG).waitFor({ state: 'hidden', timeout: TIMEOUT });
      const note = await waitForNote(cppText, 'The CPP Sign & Save did not write the item');
      h.assert(note.signed === '1', 'The CPP item was not signed');
      notes.a = note.id;
      await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_issue ci JOIN casemgmt_issue_notes cin ON cin.id=ci.id
        WHERE ci.demographic_no=${patient} AND ci.issue_id=${cppIssueId} AND cin.note_id=${notes.a}`, '1',
      'The CPP item is not linked to its own Social History issue');
      await leaveChart(chart);
    });

    // Finding 225. Pure assertion: the chart is closed and the save has long returned.
    await s.step(STEP.assign, async () => {
      h.assert(caseIssue(issueId, notes.a) !== null,
        `The note was saved with the issue "${issueName}" picked in Assign Issues, and no casemgmt_issue row for it is linked to the note`);
    });

    await stepModuleLists(false);
    await stepClickFilters();
    await stepHeading();
    return;
  }

  // ---- editor: finding 186 --------------------------------------------------------------------------
  if (PIN === 'editor') {
    await s.step('(control) the chart opens with the note editor and its issue area', async () => {
      const chart = await s.chart();
      await noteField(chart).waitFor({ state: 'visible', timeout: TIMEOUT });
      h.assert(await chart.locator('#noteIssues').count() === 1, 'The note editor has no issue area (#noteIssues)');
    });
    await s.step(STEP.editor, async () => {
      const chart = await s.chart();
      const box = await chart.locator('#issueAutocomplete').first().isVisible();
      const button = await chart.locator('#asgnIssues').first().isVisible();
      h.assert(box && button, `The note editor shows no way to search for an issue and assign it (search box ${box ? 'shown' : 'missing'}, Assign button ${button ? 'shown' : 'missing'})`);
    });
    return;
  }

  // ---- The seeded entries -----------------------------------------------------------------------------
  h.assert(seeded, 'An unknown ECHART_ISSUES_PIN reached the steps');
  await seedNotesAndLink();
  await stepModuleLists();

  if (PIN === 'heading') {
    await stepClickFilters();
    // Finding 226. The click is the behaviour under test: the heading's handler is a syntax error, so nothing changes.
    await stepHeading();
    return;
  }

  if (PIN === 'resolve') {
    const reachable = { checklistRows: 0, shown: false, togglers: [] };
    await s.step('(control) the note editor carries the issue checklist and what a clinician can see of it is recorded', async () => {
      const chart = await s.chart();
      await noteField(chart).waitFor({ state: 'visible', timeout: TIMEOUT });
      // The server delivered the checklist with the issue in it, so the pin below is about reaching it.
      reachable.checklistRows = await chart.locator('#noteIssues-unresolved a[onclick^="return displayIssue"]').count();
      h.assert(reachable.checklistRows === 1, `The note editor's unresolved checklist holds ${reachable.checklistRows} issues, expected the one assigned`);
      reachable.shown = await chart.locator('#noteIssues-unresolved').isVisible();
      // A visible control in the editor that names issues is the only way a clinician could open it: find them
      // (tagged so the click below can address one) and, when the checklist is closed, press the first.
      reachable.togglers = await chart.evaluate(() => {
        const editor = document.querySelector('#noteIssues').closest('div[id^="nc"]') || document.body;
        const found = [...editor.querySelectorAll('a, button, input[type="button"], input[type="image"], input[type="submit"], img[onclick]')]
          .filter(el => !el.closest('#noteIssues') && el.getClientRects().length > 0
            && /issue/i.test(`${el.textContent} ${el.title} ${el.alt} ${el.value}`));
        found.forEach((el, index) => el.setAttribute('data-pw-issue-toggler', String(index)));
        return found.map(el => el.outerHTML.slice(0, 120));
      });
      if (!reachable.shown && reachable.togglers.length > 0) {
        await chart.locator('[data-pw-issue-toggler="0"]').click({ timeout: TIMEOUT });
        reachable.shown = await chart.locator('#noteIssues-unresolved').isVisible();
      }
    });

    await s.step('(control, not a clinician path) with the checklist revealed by a script, choosing Resolved sets resolved=1 and the Resolved Issues module lists the issue', async () => {
      const chart = await s.chart();
      const link = caseIssue(issueId, notes.a);
      // A script reveals what no control reveals; this proves the server side, it does not reach the code a clinician uses.
      await chart.evaluate(() => { document.getElementById('noteIssues-unresolved').style.display = 'block'; });
      await chart.locator('#noteIssues-unresolved a[onclick^="return displayIssue"]').click({ timeout: TIMEOUT });
      const radio = chart.locator('#noteIssues-unresolved input[type="radio"][name$=".issue.resolved"][value="true"]');
      await radio.waitFor({ state: 'visible', timeout: TIMEOUT });
      const [response] = await Promise.all([
        chart.waitForResponse(candidate => chartSaveRequest('issueChange')(candidate.request()), { timeout: TIMEOUT }),
        radio.check({ timeout: TIMEOUT }),
      ]);
      h.assert(response.status() === 200, `Choosing Resolved answered HTTP ${response.status()}`);
      await expectValue(sql, `SELECT resolved FROM casemgmt_issue WHERE id=${link.id}`, '1', 'Choosing Resolved did not set casemgmt_issue.resolved');
      h.assert(caseIssue(issueId, notes.a) !== null, 'Resolving the issue removed its link to the note');
      await leaveChart(chart);

      const reopened = await s.chart();
      await waitForListed(reopened, [notes.a, notes.b], 'The reopened chart does not list both owned notes');
      const unresolved = await moduleItems(reopened, '#unresolvedIssueslist');
      const resolved = await moduleItems(reopened, '#resolvedIssueslist');
      h.assert(unresolved.length === 0, `The Issues module still lists [${unresolved.join(' | ')}] after the issue was resolved`);
      h.assert(resolved.length === 1 && resolved[0] === issueName, `The Resolved Issues module lists [${resolved.join(' | ')}], expected "${issueName}"`);
      // The resolved issue still filters to its note, through its own module.
      await Promise.all([
        reopened.waitForResponse(candidate => chartSaveRequest('edit')(candidate.request()), { timeout: TIMEOUT }),
        reopened.locator('#resolvedIssueslist a.links').first().click({ timeout: TIMEOUT }),
      ]);
      await waitForListed(reopened, [notes.a], 'Clicking the resolved issue did not narrow the notes to the linked note');
    });

    // Finding 227. Pure assertion on what the earlier step recorded.
    await s.step(STEP.resolve, async () => {
      h.assert(reachable.shown,
        'The note editor holds the Reference Unresolved Issues checklist but it is hidden (display none) and no visible control in the editor opens it '
        + `(visible controls naming issues: ${reachable.togglers.length})`);
    });
    return;
  }

  if (PIN === 'radios') {
    // What the unresolved checklist's controls look like for the signed-in provider, recorded by the control and judged by the pin.
    const shown = {};
    await s.step('(control, not a clinician path) with the checklist revealed by a script, the login can write the issue: its checkbox, Acute and Resolved controls are enabled', async () => {
      const chart = await s.chart();
      await noteField(chart).waitFor({ state: 'visible', timeout: TIMEOUT });
      const rows = await chart.locator('#noteIssues-unresolved a[onclick^="return displayIssue"]').count();
      h.assert(rows === 1, `The note editor's unresolved checklist holds ${rows} issues, expected the one assigned`);
      // A script reveals what no control reveals (finding 227); it is what lets the radios be looked at, not a clinician path.
      await chart.evaluate(() => { document.getElementById('noteIssues-unresolved').style.display = 'block'; });
      await chart.locator('#noteIssues-unresolved a[onclick^="return displayIssue"]').click({ timeout: TIMEOUT });
      await chart.locator('#noteIssues-unresolved input[type="radio"][name$=".issue.certain"]').first().waitFor({ state: 'visible', timeout: TIMEOUT });
      // Every control is read once, in one page evaluation: the property (what the browser enforces) and the attribute (what the page wrote).
      Object.assign(shown, await chart.evaluate(() => {
        const read = (selector) => [...document.querySelectorAll(`#noteIssues-unresolved ${selector}`)].map((el) => ({
          disabled: el.disabled, attribute: el.getAttribute('disabled'), value: el.value }));
        return {
          checkbox: read('input[type="checkbox"][name$=".checked"]'),
          acute: read('input[type="radio"][name$=".issue.acute"]'),
          resolved: read('input[type="radio"][name$=".issue.resolved"]'),
          certain: read('input[type="radio"][name$=".issue.certain"]'),
          major: read('input[type="radio"][name$=".issue.major"]'),
          role: read('input[type="text"][name$=".issueDisplay.role"]'),
        };
      }));
      h.assert(shown.checkbox.length === 1 && shown.acute.length === 2 && shown.resolved.length === 2
        && shown.certain.length === 2 && shown.major.length === 2 && shown.role.length === 1,
      `The revealed checklist holds checkbox/acute/resolved/certain/major/role controls ${JSON.stringify(Object.values(shown).map((list) => list.length))}, expected 1/2/2/2/2/1`);
      // The checkbox and the Acute and Resolved radios are the controls the same page enables for a login that may write the issue.
      h.assert(!shown.checkbox[0].disabled && shown.acute.every((el) => !el.disabled) && shown.resolved.every((el) => !el.disabled),
        'The checkbox, Acute and Resolved controls of the issue are disabled, so this login cannot write the issue and the radios cannot be judged');
    });

    // Finding 261. Pure assertion on what the control recorded.
    await s.step(STEP.radios, async () => {
      const disabled = ['certain', 'major'].flatMap((name) => shown[name].filter((el) => el.disabled)
        .map((el) => `${name}=${el.value} (disabled="${el.attribute}")`)).concat(shown.role.filter((el) => el.disabled).map((el) => `role box (disabled="${el.attribute}")`));
      h.assert(disabled.length === 0,
        `The Certain, Uncertain, Major and Not Major radios and the role box are disabled for a login with full rights: ${disabled.join(', ')}`);
    });
    return;
  }

  // ---- panel: finding 185 ---------------------------------------------------------------------------------
  await s.step('(control) the notes filter panel is in the page, closed, and carries the issue as a choice', async () => {
    const chart = await s.chart();
    const link = caseIssue(issueId, notes.a);
    h.assert(await chart.locator('#filter').count() === 1, 'The chart has no notes filter panel (#filter)');
    h.assert(!(await chart.locator('#filter').isVisible()), 'The notes filter panel is already open');
    h.assert(await chart.locator(`#filter input[name="issues"][value="${link.id}"]`).count() === 1, 'The filter panel does not offer the assigned issue');
  });
  await s.step(STEP.panel, async () => {
    const chart = await s.chart();
    // Any visible control outside the panel that calls itself Filter (its own text, title, alt, value or label);
    // the panel's own Hide and Reset buttons are inside it and hidden.
    const named = await chart.evaluate(() => {
      const found = [...document.querySelectorAll('a, button, input, img, h3, span, div')]
        .filter(el => !el.closest('#filter') && el.getClientRects().length > 0
          && /\bfilter\b/i.test(`${el.children.length === 0 ? el.textContent : ''} ${el.title} ${el.alt || ''} ${el.value || ''} ${el.getAttribute('aria-label') || ''}`));
      found.forEach((el, index) => el.setAttribute('data-pw-filter-opener', String(index)));
      return found.map(el => el.outerHTML.slice(0, 100));
    });
    h.assert(named.length > 0, 'No visible control in the chart is labelled Filter, so a clinician has no way to open the notes filter panel');
    await chart.locator('[data-pw-filter-opener="0"]').click({ timeout: TIMEOUT });
    h.assert(await chart.locator('#filter').isVisible(), `Clicking the Filter control (${named[0]}) did not open the notes filter panel`);
  });
}

module.exports = { workflow, STEP, validatePin };
if (require.main === module) runWorkflow('echart-issues-filter', workflow, { openPatient: true });
