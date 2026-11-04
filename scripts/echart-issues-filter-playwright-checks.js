#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Real chart issue assignments and note filters (#4586, #4560, #4587, #4559).
 * Unset ECHART_ISSUES_PIN drives CPP assignment and form isolation. `editor` assigns to a new
 * note and an edited saved note, reading the returned latest revision rather than assuming
 * stable IDs. `heading` clicks both module headings after filtering. `panel` drives opening,
 * hiding, filtering, and resetting. Existing `resolve` and `radios` pins cover separate defects.
 * Seeded heading/panel fixtures isolate those controls from assignment failures. Every note,
 * patient issue, link, extension, draft and lock belongs to the workflow's disposable patient.
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
    // Keep heading/filter checks independent of the assignment workflow.
    await s.step('(fixture) the issue is linked to the first note with SQL to isolate filtering', async () => {
      const program = sql.value(`SELECT program_no FROM casemgmt_note WHERE note_id=${notes.a}`);
      h.assert(/^\d+$/.test(program), 'The first note has no program');
      const id = sql.value(`INSERT INTO casemgmt_issue (demographic_no, issue_id, acute, certain, major, resolved, program_id, type, update_date)
        VALUES (${patient}, ${issueId}, 0, 0, 0, 0, ${program}, ${q(issueRole)}, NOW()); SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(id), 'The casemgmt_issue fixture was not created');
      sql.execute(`INSERT INTO casemgmt_issue_notes (id, note_id) VALUES (${id}, ${notes.a})`);
      h.assert(caseIssue(issueId, notes.a) !== null, 'The seeded link is not readable through casemgmt_issue_notes');
      if (PIN === 'heading') {
        // Distinct owned dates make retained ascending sort observable, independent of save timing.
        sql.execute(`UPDATE casemgmt_note SET observation_date=CASE note_id WHEN ${notes.a} THEN '2026-08-01 10:00:00' ELSE '2026-08-02 10:00:00' END WHERE note_id IN (${notes.a},${notes.b}) AND demographic_no=${patient}`);
      }
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

  async function stepClickFilters(label = 'clicking the issue in the Issues module lists only the linked note') {
    await s.step(label, async () => {
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
  async function stepHeading(module = 'unresolvedIssues') {
    await s.step(module === 'unresolvedIssues' ? STEP.heading : 'clicking the Resolved Issues heading brings every note back', async () => {
      const chart = await s.chart();
      if (PIN !== 'heading') {
        await chart.locator(`#${module} .nav-menu-title button`).first().click({ timeout: TIMEOUT });
        await waitForListed(chart, [notes.a, notes.b], 'After the heading was clicked the notes area does not list every note', 8000);
        return;
      }
      const link = caseIssue(issueId, notes.a);
      h.assert(link !== null && /^[1-9]\d*$/.test(link.id), 'The seeded issue link is missing before clearing its heading filter');
      const [provider, role] = sql.rows(`SELECT provider_no, reporter_caisi_role FROM casemgmt_note WHERE note_id=${notes.a}`)[0];
      h.assert(/^\d+$/.test(provider) && /^\d+$/.test(role), 'The note fixture has no provider/role filter values');
      await chart.locator('#notesFilterToggle').click();
      const retained = { filter_providers: provider, filter_roles: role, note_sort: 'observation_date_asc' };
      for (const [name, value] of Object.entries(retained)) {
        await chart.locator(`#filter input[name="${name}"][value="${value}"]`).check();
      }
      await chart.locator(`#filter input[name="issues"][value="${link.id}"]`).check();
      // Exercise the legacy hidden constraint as well as the visible issue checkbox.
      await chart.locator('#check_issue').evaluate((input, value) => { input.value = value; }, `${link.id}.${issueId}`);
      const previousFilter = await chart.locator('#filter').elementHandle();
      const [applied] = await Promise.all([
        chart.waitForResponse(candidate => chartSaveRequest('edit')(candidate.request())
          && new URLSearchParams(candidate.request().postData() || '').get('ajaxview') === 'ajaxView', { timeout: TIMEOUT }),
        chart.locator('#filter input[type="button"][value="Hide"]').click(),
      ]);
      h.assert(applied.status() === 200, 'Applying provider/role/sort and issue filters failed');
      await chart.waitForFunction(element => !element.isConnected, previousFilter, { timeout: TIMEOUT });
      await previousFilter.dispose();
      await waitForListed(chart, [notes.a], 'Applying the selected filters did not retain only the issue-linked note');
      for (const [name, value] of Object.entries({...retained, issues: link.id})) {
        h.assert(await chart.locator(`#filter input[name="${name}"][value="${value}"]`).isChecked(),
          `Apply lost the selected ${name} before the heading was clicked`);
      }
      h.assert(await chart.locator('#check_issue').inputValue() === `${link.id}.${issueId}`,
        'Apply lost the legacy issue constraint before the heading was clicked');
      const [response] = await Promise.all([
        chart.waitForResponse(candidate => chartSaveRequest('edit')(candidate.request())
          && new URLSearchParams(candidate.request().postData() || '').get('ajaxview') === 'ajaxView', { timeout: TIMEOUT }),
        chart.locator(`#${module} .nav-menu-title button`).first().click({ timeout: TIMEOUT }),
      ]);
      h.assert(response.status() === 200, 'Clearing the issue filter failed');
      const data = new URLSearchParams(response.request().postData() || '');
      h.assert(data.get('resetFilter') === 'false' && data.get('check_issue') === '' && !data.has('issues'),
        'The heading did not clear both issue controls without resetting every filter');
      for (const [name, value] of Object.entries(retained)) {
        h.assert(data.get(name) === value, `The heading discarded the selected ${name}`);
      }
      await waitForListed(chart, [notes.a, notes.b], 'After the heading was clicked the notes area does not list both matching notes', 8000);
      h.assert((await listedIds(chart)).join(',') === [notes.a, notes.b].join(','), 'The heading lost ascending note sort order');
      const selected = await chart.locator('#filter input:checked').evaluateAll(inputs => inputs.map(input => ({name: input.name, value: input.value})));
      const missing = Object.entries(retained).filter(([name, value]) => !selected.some(input => input.name === name && input.value === value)).map(([name]) => name);
      h.assert(missing.length === 0, `The refreshed filter controls lost selections: ${missing.join(', ')}`);
      h.assert(await chart.locator('#check_issue').inputValue() === '' && selected.every(input => input.name !== 'issues'),
        'The refreshed filter controls retained an issue constraint');
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
      const noteSelection = await chart.locator('#newIssueId').inputValue();
      const noteName = await chart.locator('#newIssueName').inputValue();
      await ui.typeAutocomplete(chart, '#issueAutocompleteCPP', issueName, { option: issueName, timeout: TIMEOUT });
      const choice = chart.locator(`#frmIssueNotes input[name="issue_id"][value="${issueId}"]`);
      h.assert(await choice.count() === 1 && await choice.isChecked(), 'The CPP pick did not create a checked issue_id control');
      h.assert((await choice.locator('..').innerText()).includes(issueName), 'The CPP pick is not shown in the issue list');
      await ui.typeAutocomplete(chart, '#issueAutocompleteCPP', issueName, { option: issueName, timeout: TIMEOUT });
      h.assert(await choice.count() === 1, 'Picking the same issue twice duplicated the CPP assignment');
      h.assert(await chart.locator('#newIssueId').inputValue() === noteSelection
        && await chart.locator('#newIssueName').inputValue() === noteName, 'CPP selection contaminated the note form');
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
    async function assignFromNote(chart) {
      await chart.waitForFunction(() => issueUpdatesPending === 0, null, { timeout: TIMEOUT });
      const cppBefore = await chart.locator('#frmIssueNotes input[name="issue_id"]').evaluateAll(inputs =>
        inputs.map(input => [input.value, input.checked]));
      const picked = await ui.typeAutocomplete(chart, '#issueAutocomplete', issueName, {
        option: issueName, hidden: '#newIssueId', timeout: TIMEOUT });
      h.assert(picked === issueId, 'The note search selected the wrong catalog issue');
      const [response] = await Promise.all([
        chart.waitForResponse(candidate => chartSaveRequest('makeIssue')(candidate.request()), { timeout: TIMEOUT }),
        chart.locator('#asgnIssues').click(),
      ]);
      h.assert(response.status() === 200, `Assign answered HTTP ${response.status()}`);
      await chart.waitForFunction(() => issueUpdatesPending === 0, null, { timeout: TIMEOUT });
      h.assert(await chart.locator('#noteIssues input[name$=".checked"]:checked').count() === 1,
        'Assign did not preserve the selected issue in the replacement note fragment');
      h.assert(await chart.locator('#issueAutocomplete').isEnabled() && await chart.locator('#asgnIssues').isEnabled(),
        'The replacement note fragment did not restore working issue controls');
      const cppAfter = await chart.locator('#frmIssueNotes input[name="issue_id"]').evaluateAll(inputs =>
        inputs.map(input => [input.value, input.checked]));
      h.assert(JSON.stringify(cppBefore) === JSON.stringify(cppAfter), 'Note assignment contaminated the CPP form');
    }
    await s.step('a new note saves the issue selected through its own Assign control', async () => {
      const chart = await s.chart();
      const text = `${marker} assigned new note`;
      await noteField(chart).fill(text);
      await assignFromNote(chart);
      await pressAndExit(chart, SIGN_SAVE);
      notes.a = (await waitForNote(text, 'Assigned new note was not saved')).id;
      h.assert(caseIssue(issueId, notes.a) !== null, 'The new note lost the assigned issue on save');
    });
    await s.step('a separate new note starts without the preceding note assignment', async () => {
      notes.b = (await writeSignedNote(`${marker} plain note before edit`)).id;
      h.assert(caseIssue(issueId, notes.b) === null, 'The new note inherited another note assignment');
    });
    await s.step('editing a saved note can assign an issue and saves it on the latest revision', async () => {
      const chart = await s.chart();
      const oldId = notes.b;
      await chart.locator(`#edit${oldId}`).click({ timeout: TIMEOUT });
      const editor = chart.locator(`#caseNote_note${oldId}`);
      await editor.waitFor({ state: 'visible', timeout: TIMEOUT });
      await chart.waitForFunction(() => issueUpdatesPending === 0, null, { timeout: TIMEOUT });
      await editor.fill(`${await editor.inputValue()}\n${marker} assigned edited revision`);
      await assignFromNote(chart);
      await pressAndExit(chart, SIGN_SAVE);
      notes.b = (await waitForNote(`${marker} assigned edited revision`, 'The edited note was not saved')).id;
      h.assert(caseIssue(issueId, notes.b) !== null, 'The latest saved revision lost its assigned issue');
      h.assert(caseIssue(issueId, oldId) === null, 'Assigning the edited note changed the prior revision');
      const reopened = await s.chart();
      await waitForListed(reopened, [notes.a, notes.b], 'The chart did not display the latest note revisions');
      h.assert(await reopened.locator(`#edit${notes.b}`).count() === 1, 'The edit control does not address the latest saved note id');
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
    await stepClickFilters('after filter restoration, the sidebar issue still narrows the list to its linked note');
    await s.step('(fixture) resolving the owned issue exposes its Resolved Issues module link', async () => {
      const chart = await s.chart();
      await leaveChart(chart);
      const link = caseIssue(issueId, notes.a);
      h.assert(link !== null && /^[1-9]\d*$/.test(link.id), 'The seeded issue link is missing before resolving it');
      sql.execute(`UPDATE casemgmt_issue SET resolved=1 WHERE id=${link.id} AND demographic_no=${patient}`);
      const reopened = await s.chart();
      await reopened.locator('#resolvedIssueslist a.links').first().click({ timeout: TIMEOUT });
      await waitForListed(reopened, [notes.a], 'The resolved issue did not filter to its linked note');
    });
    await stepHeading('resolvedIssues');
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
    h.assert(await chart.locator('#notesFilterToggle').getAttribute('aria-expanded') === 'true', 'Filter expansion is not announced');
  });
  await s.step('the filter can hide and reopen, apply an issue selection, and reset to every note', async () => {
    const chart = await s.chart();
    const link = caseIssue(issueId, notes.a);
    await chart.locator('#notesFilterToggle').click();
    h.assert(!(await chart.locator('#filter').isVisible()), 'Filter toggle did not hide the panel');
    await chart.locator('#notesFilterToggle').click();
    await chart.locator(`#filter input[name="issues"][value="${link.id}"]`).check();
    const [response] = await Promise.all([
      chart.waitForResponse(candidate => chartSaveRequest('edit')(candidate.request()), { timeout: TIMEOUT }),
      chart.locator('#filter input[type="button"][value="Hide"]').click(),
    ]);
    h.assert(response.status() === 200, 'Applying the filter failed');
    await waitForListed(chart, [notes.a], 'The filter did not restrict the list to the linked note');
    h.assert(!(await chart.locator('#filter').isVisible()), 'The applied filter panel did not close');
    await chart.locator('#notesFilterToggle').click();
    await chart.locator(`#filter input[name="issues"][value="${link.id}"]`).check();
    await chart.locator('#filter input[name="note_sort"]').first().check();
    const [reset] = await Promise.all([
      chart.waitForRequest(chartSaveRequest('edit'), { timeout: TIMEOUT }),
      chart.locator('#filter input[type="button"][onclick="return filter(true);"]').click(),
    ]);
    const data = new URLSearchParams(reset.postData() || '');
    h.assert(data.get('resetFilter') === 'true' && !data.has('issues') && !data.has('note_sort'),
      'Reset submitted selected issue or sort filters');
    await waitForListed(chart, [notes.a, notes.b], 'Reset did not restore every note');
    await chart.locator('#notesFilterToggle').click();
    h.assert(await chart.locator('#filter').isVisible(), 'The replacement filter panel cannot reopen');
    h.assert(await chart.locator('#filter input:checked').count() === 0, 'Reset left selected filters behind');
  });
}

module.exports = { workflow, STEP, validatePin };
if (require.main === module) runWorkflow('echart-issues-filter', workflow, { openPatient: true });
