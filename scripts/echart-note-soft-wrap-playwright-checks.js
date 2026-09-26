#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * eChart note editors wrap softly, so a saved note holds only the line breaks the clinician
 * typed (#3955; ported from open-osp/Open-O PR #137 by Chitrank Davé).
 *
 * The editors were built with wrap="hard" and a fixed cols. A hard-wrapped textarea submits a
 * CRLF at every visual wrap point, Firefox 145+ does so for script-driven submissions as well,
 * and the server stores those breaks: the note came back chopped at the editor width and
 * reflowed badly in print, the note browser and exports.
 *
 * WHY THE CHECK PROBES THE FORM DATA, NOT ONLY THE SAVED ROW. The eChart's Save posts
 * Form.serialize(), which reads textarea.value, and Chromium only applies hard wrapping to
 * the submitted form-data value. So in the shipped Chromium a re-broken editor would still
 * Save a clean note, and a check that only read casemgmt_note back after Save would stay
 * green. (Sign & Save does submit the form natively; steps 2 and 3 use it, and also assert
 * the posted value.) Each editor is therefore probed where the wrap transformation lives: the FormData entry of the
 * rendered, laid-out textarea must equal what was typed. A hard-wrapped twin of the same
 * editor, with the same value and width, is the control -- it must show inserted breaks, or
 * the probe could not have detected the bug in this browser and the check fails rather than
 * passing vacuously.
 *
 * The saved row is still read back: a note typed as one long paragraph, a typed line break
 * and a second paragraph must be stored byte-for-byte, with no CR and no LF beyond the typed
 * ones -- followed only by the "[Signed on ...]" stamp the server appends when it signs.
 *
 * Four editors are covered, reached the way a clinician reaches them:
 *   1. the new-note editor the chart opens with (ChartNotesAjax.jsp), probed only;
 *   2. the editor the new-note icon builds (newNote() in newCaseManagementView.js.jsp),
 *      typed into and stored with Sign & Save -- a native form submission, the path where
 *      the browser itself applies the wrap mode;
 *   3. the editor the signed note's Edit link builds (editNote()) on the reopened chart,
 *      appended to and stored with Sign & Save (editNote() hides Save for a signed note)
 *      as the same note;
 *   4. the classic CaseManagementEntry.jsp editor, opened at the URL the case-management note
 *      search links to, probed only: soft wrap, and no whitespace padding from its markup.
 *
 * The check owns every row it touches: a synthetic FAKE- patient (runWorkflow), the notes
 * signed and saved on it and their issue/ext/link rows, all removed afterwards.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

// Long enough to wrap several times in an 84-column editor at any chart width.
const LONG_PARAGRAPH_WORDS = 90;

function noteText(tag, label) {
  const words = [];
  for (let i = 0; i < LONG_PARAGRAPH_WORDS; i += 1) words.push(`word${i}`);
  // One long paragraph, ONE typed line break, a short second paragraph.
  return `${tag} ${label} ${words.join(' ')}\n${tag} ${label} second paragraph`;
}

/** The active eChart note editor: the one textarea the entry form submits as caseNote_note. */
function activeEditor(chart) {
  return chart.locator('#encMainDiv textarea[name="caseNote_note"]');
}

/**
 * Reads the editor's wrap mode and what a form submission would carry for it, next to a
 * hard-wrapped twin of the same width and value (the control).
 */
async function probeEditor(editor) {
  return editor.evaluate((textarea) => {
    const form = textarea.form;
    if (!form) return { error: 'the note editor is not inside a form' };
    const submitted = (element) => {
      const original = element.name;
      element.name = '__carlosWrapProbe';
      try {
        return new FormData(form).get('__carlosWrapProbe');
      } finally {
        element.name = original;
      }
    };
    const twin = textarea.cloneNode(false);
    twin.removeAttribute('id');
    twin.setAttribute('wrap', 'hard');
    twin.value = textarea.value;
    twin.style.width = `${textarea.getBoundingClientRect().width}px`;
    textarea.insertAdjacentElement('afterend', twin);
    try {
      const breaks = (text) => (String(text).match(/\r\n|\r|\n/g) || []).length;
      return {
        wrapAttribute: textarea.getAttribute('wrap'),
        wrapProperty: textarea.wrap,
        value: textarea.value,
        submitted: submitted(textarea),
        valueBreaks: breaks(textarea.value),
        submittedBreaks: breaks(submitted(textarea)),
        controlBreaks: breaks(submitted(twin)),
      };
    } finally {
      twin.remove();
    }
  });
}

function assertSoftWrap(probe, what) {
  h.assert(!probe.error, `${what}: ${probe.error}`);
  h.assert(probe.wrapAttribute === 'soft' && probe.wrapProperty === 'soft',
    `${what} is not soft-wrapped (wrap="${probe.wrapAttribute}")`);
  // The control proves the probe is meaningful in this browser: a hard-wrapped twin of the
  // same editor gains breaks on submission. Without it a browser that ignored wrap entirely
  // would pass the assertion below for any editor.
  h.assert(probe.controlBreaks > probe.valueBreaks,
    `${what}: a hard-wrapped control gained no line breaks, so this browser cannot show the defect`);
  const normalized = String(probe.submitted).replace(/\r\n/g, '\n');
  h.assert(normalized === probe.value && probe.submittedBreaks === probe.valueBreaks,
    `${what} submits ${probe.submittedBreaks} line break(s) for ${probe.valueBreaks} typed`);
}

async function typeInto(editor, text) {
  await editor.click();
  await editor.fill(text);
}

async function clickAndExpectSave(chart, selector, method) {
  const [response] = await Promise.all([
    chart.waitForResponse((r) => r.request().method() === 'POST' && /\/CaseManagementEntry/.test(r.url())
      && new URLSearchParams(r.request().postData() || '').get('method') === method, { timeout: 30000 }),
    chart.locator(selector).first().click(),
  ]);
  h.assert(response.status() < 400, `${method} returned HTTP ${response.status()}`);
}

/**
 * Waits for the newest revision of the note carrying `needle` and reports how it compares
 * with the typed text. Compared and counted in SQL so no CR can be lost or invented by the
 * client's output escaping; only the part after the typed text comes back, and on a
 * synthetic FAKE- fixture that is at most the server's signature stamp.
 */
async function savedNote(sql, patient, needle, expected) {
  const length = expected.length;
  const query = `SELECT note_id, uuid, signed, LEFT(note, ${length}) = ${h.sqlString(expected)},
      SUBSTRING(note, ${length + 1}),
      LENGTH(note) - LENGTH(REPLACE(note, CHAR(13), '')),
      LENGTH(note) - LENGTH(REPLACE(note, CHAR(10), ''))
    FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${h.sqlString(`%${needle}%`)}
    ORDER BY note_id DESC LIMIT 1`;
  const deadline = Date.now() + 20000;
  let row;
  do {
    [row] = sql.rows(query);
    if (row && row[3] === '1') break;
    await new Promise((resolve) => setTimeout(resolve, 250));
  } while (Date.now() < deadline);
  h.assert(row, 'Save did not create a casemgmt_note row for the fixture note');
  const [noteId, uuid, signed, prefixMatches, rest, carriageReturns, lineFeeds] = row;
  return {
    noteId, uuid, signed: signed === '1', prefixMatches: prefixMatches === '1', rest,
    carriageReturns: Number(carriageReturns), lineFeeds: Number(lineFeeds),
  };
}

// CaseManagementEntry2Action appends "\n[Signed on <date> by <provider>]" when it signs, and
// the stored note ends with one more line break after it.
const SIGNATURE_STAMP = /^\n\[Signed on [^\]\n]+\]\n?$/;

/**
 * The stored note must be the typed text, byte for byte, followed by nothing -- or, for a
 * signed note, by the server's signature stamp alone. No CR anywhere, and no LF beyond the
 * typed ones and the stamp's own.
 */
function assertStoredAsTyped(note, expected, what) {
  const typedBreaks = (expected.match(/\n/g) || []).length;
  const stampBreaks = (note.rest.match(/\n/g) || []).length;
  h.assert(note.carriageReturns === 0, `${what} was stored with ${note.carriageReturns} carriage return(s)`);
  h.assert(note.prefixMatches, `${what} was not stored exactly as typed`);
  h.assert(note.rest === '' || (note.signed && SIGNATURE_STAMP.test(note.rest)),
    `${what} was stored with text after the typed note that is not the signature stamp`);
  h.assert(note.lineFeeds === typedBreaks + stampBreaks,
    `${what} was stored with ${note.lineFeeds - stampBreaks} line break(s); ${typedBreaks} were typed`);
}

/** Line breaks in a submitted form value, with the CRLF form encoding counted once. */
function submittedBreaks(value) {
  return (String(value).match(/\r\n|\r|\n/g) || []).length;
}

/**
 * Opens an editor and waits for the issue refresh it starts. newNote() and editNote() both
 * post the note's issue list (method=edit), and that response's onIssueUpdate() then reloads
 * the issues panel (/encounter/displayIssues). A save made before the chain lands races it:
 * saveNoteAjax() blanks #notCPP under the late onIssueUpdate(), and Sign & Save's navigation
 * aborts the panel reload. No clinician's typing wins that race; a script's does.
 */
async function openEditorAndSettle(chart, open) {
  const isPost = (r, method) => r.request().method() === 'POST' && /\/CaseManagementEntry/.test(r.url())
    && new URLSearchParams(r.request().postData() || '').get('method') === method;
  const issueList = chart.waitForResponse((r) => isPost(r, 'edit'), { timeout: 30000 });
  const issuesPanel = chart.waitForResponse((r) => /\/encounter\/displayIssues/.test(r.url()), { timeout: 30000 });
  await open();
  await (await issueList).finished();
  await (await issuesPanel).finished();
}

/**
 * Sign & Save (savePage) submits the entry form natively -- the save path where the browser
 * itself applies the textarea's wrap mode, so a hard-wrapped editor reaches the server broken
 * even in Chromium. The posted value must carry exactly the typed line breaks. The chart
 * closes itself when it is done.
 */
async function signAndSave(chart, typed) {
  const posted = chart.waitForRequest((r) => r.method() === 'POST' && /\/CaseManagementEntry/.test(r.url())
    && new URLSearchParams(r.postData() || '').get('method') === 'saveAndExit', { timeout: 30000 });
  const closed = chart.waitForEvent('close', { timeout: 30000 }).then(() => true).catch(() => false);
  await clickAndExpectSave(chart, '#signSaveImg', 'saveAndExit');
  const postedNote = new URLSearchParams((await posted).postData() || '').get('caseNote_note') || '';
  h.assert(submittedBreaks(postedNote) === submittedBreaks(typed) && postedNote.replace(/\r\n/g, '\n') === typed,
    `Sign & Save posted ${submittedBreaks(postedNote)} line break(s) for ${submittedBreaks(typed)} typed`);
  h.assert(await closed, 'Sign & Save did not close the chart window');
}

/** A closing chart reloads its opener, the Master Record; wait until it is usable again. */
async function settleMasterRecord(session) {
  await session.master.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  await session.master.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await session.master.locator('a').filter({ hasText: /^\s*E-?Chart\s*$/i }).first()
    .waitFor({ state: 'visible', timeout: 30000 });
}

async function reopenChart(session) {
  await settleMasterRecord(session);
  return session.chart();
}

async function workflow(session) {
  const { sql, patient, marker } = session;
  const tag = marker.slice(-10);

  session.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0',
      'the fixture notes were not removed');
  });

  let chart = await session.chart();
  const firstText = noteText(tag, 'new-note icon');
  let first;

  await session.step('Opening note editor soft-wraps', async () => {
    const editor = activeEditor(chart);
    await editor.first().waitFor({ state: 'visible', timeout: 30000 });
    h.assert(await editor.count() === 1, `expected one active note editor, found ${await editor.count()}`);
    // Probed, then put back exactly as the chart rendered it: leaving it edited would make
    // changeToView() ask to save it before the new-note icon opens the next editor.
    const rendered = await editor.inputValue();
    await editor.fill(noteText(tag, 'opening editor'));
    const probe = await probeEditor(editor);
    await editor.fill(rendered);
    assertSoftWrap(probe, 'the opening note editor');
  });

  await session.step('New-note editor soft-wraps and Sign & Save stores only the typed line break', async () => {
    // Opened from the untouched opening editor, so no "not saved" prompt is expected: the
    // strict page wiring fails the step on any dialog.
    await openEditorAndSettle(chart, () => chart.locator('#newNoteImg').first().click());
    const editor = chart.locator('#encMainDiv .newNote textarea[name="caseNote_note"]');
    await editor.first().waitFor({ state: 'visible', timeout: 30000 });
    h.assert(await activeEditor(chart).count() === 1, 'the new-note icon left more than one active editor');
    await typeInto(editor, firstText);
    assertSoftWrap(await probeEditor(editor), 'the new-note icon editor');
    await signAndSave(chart, firstText);
    first = await savedNote(sql, patient, `${tag} new-note icon`, firstText);
    h.assert(first.signed, 'Sign & Save did not sign the note');
    assertStoredAsTyped(first, firstText, 'the note signed and saved from the new-note icon editor');
  });

  await session.step('Edit-link editor soft-wraps and Sign & Save stores only the typed line breaks', async () => {
    // The reopened chart shows the signed note in view mode with its Edit link, and a fresh,
    // untouched editor, so opening the note for editing raises no "not saved" prompt.
    chart = await reopenChart(session);
    const noteDiv = chart.locator('#encMainDiv div[id^="n"]').filter({ hasText: `${tag} new-note icon` }).last();
    const editLink = noteDiv.locator('a[id^="edit"]').first();
    await editLink.waitFor({ state: 'visible', timeout: 30000 });
    await openEditorAndSettle(chart, () => editLink.click());
    const editor = noteDiv.locator('textarea[name="caseNote_note"]');
    await editor.first().waitFor({ state: 'visible', timeout: 30000 });
    // editNote() loads the stored text (typed text plus the signature stamp), trimmed, with
    // one trailing line break for the clinician to continue on.
    const loaded = await editor.inputValue();
    h.assert(loaded.startsWith(firstText) && SIGNATURE_STAMP.test(loaded.slice(firstText.length)),
      'the Edit link did not load the signed note text unchanged into the editor');
    const text = `${loaded}${tag} edited paragraph`;
    await typeInto(editor, text);
    assertSoftWrap(await probeEditor(editor), 'the Edit-link editor');
    // An edited signed note can only be stored signed: editNote() hides Save for it.
    await signAndSave(chart, text);
    const saved = await savedNote(sql, patient, `${tag} edited paragraph`, text);
    // Same note (uuid); whether the server stores the edit as a new row is not this check's subject.
    h.assert(saved.uuid === first.uuid, 'editing the note saved a different note instead of the edited one');
    h.assert(saved.signed, 'Sign & Save did not sign the edited note');
    assertStoredAsTyped(saved, text, 'the note re-saved from the Edit-link editor');
  });

  await session.step('Classic case-management entry editor soft-wraps and pads no whitespace', async () => {
    // CaseManagementEntry.jsp, the legacy (non-eChart) editor. The case-management note search
    // opens exactly this URL as a popup (popupNotePage), and the page reloads its opener on
    // load, so it is opened the same way here: from the Master Record. Probed only; nothing is
    // submitted from it.
    const url = new URL(`${session.config.baseUrl}/CaseManagementEntry`);
    url.search = new URLSearchParams({
      method: 'edit', from: 'casemgmt', noteId: first.noteId, demographicNo: patient, providerNo: session.provider,
    }).toString();
    await settleMasterRecord(session);
    const [page] = await Promise.all([
      session.context.waitForEvent('page', { timeout: 30000 }),
      // The popup's init() reloads this opener, which can destroy the evaluate's context
      // before it returns; the popup event above is what the step waits on.
      session.master.evaluate((target) => { window.open(target, 'carlosClassicNote'); }, url.toString())
        .catch(() => {}),
    ]);
    try {
      await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
      await h.assertNotErrorPage(page, 'classic case-management entry form');
      const editor = page.locator('form textarea[name="caseNote_note"]');
      await editor.first().waitFor({ state: 'visible', timeout: 30000 });
      // Whitespace between the value and </textarea> used to be textarea content, appended to
      // the note on every save from this form.
      h.assert(!/\s$/.test(await editor.inputValue()),
        'the classic entry form pads the note with trailing whitespace from its markup');
      await editor.fill(noteText(tag, 'classic editor'));
      assertSoftWrap(await probeEditor(editor), 'the classic case-management entry editor');
    } finally {
      await page.close();
    }
  });
}

if (require.main === module) runWorkflow('echart-note-soft-wrap', workflow);
module.exports = { workflow, noteText, assertSoftWrap, assertStoredAsTyped, submittedBreaks };
