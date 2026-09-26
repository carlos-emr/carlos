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
 * WHY THE CHECK PROBES THE FORM DATA, NOT ONLY THE SAVED ROW. The eChart saves through
 * Form.serialize(), which reads textarea.value, and Chromium only applies hard wrapping to
 * the submitted form-data value. So in the shipped Chromium a re-broken editor would still
 * save a clean note, and a check that only read casemgmt_note back would stay green. Each
 * editor is therefore probed where the wrap transformation lives: the FormData entry of the
 * rendered, laid-out textarea must equal what was typed. A hard-wrapped twin of the same
 * editor, with the same value and width, is the control -- it must show inserted breaks, or
 * the probe could not have detected the bug in this browser and the check fails rather than
 * passing vacuously.
 *
 * The saved row is still read back: a note typed as one long paragraph, a typed line break
 * and a second paragraph must be stored byte-for-byte, with no CR and exactly the one LF.
 *
 * Three editors are covered, reached the way a clinician reaches them:
 *   1. the new-note editor the chart opens with (ChartNotesAjax.jsp), saved with Save;
 *   2. the editor the new-note icon builds (newNote() in newCaseManagementView.js.jsp);
 *   3. the editor the saved note's Edit link builds (editNote()), appended to and re-saved.
 *
 * The check owns every row it touches: a synthetic FAKE- patient (runWorkflow), the notes
 * saved on it and their issue/ext/link rows, all removed afterwards. Nothing is signed.
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

async function saveNote(chart) {
  const [response] = await Promise.all([
    chart.waitForResponse((r) => r.request().method() === 'POST' && /\/CaseManagementEntry/.test(r.url())
      && new URLSearchParams(r.request().postData() || '').get('method') === 'save', { timeout: 30000 }),
    chart.locator('#saveImg').first().click(),
  ]);
  h.assert(response.status() < 400, `Save returned HTTP ${response.status()}`);
}

/**
 * Waits for the newest revision of the note carrying `needle` and reports its line-break
 * counts. Counted in SQL so no CR can be lost or invented by the client's output escaping.
 */
async function savedNote(sql, patient, needle, expected) {
  const query = `SELECT note_id, uuid, note = ${h.sqlString(expected)},
      LENGTH(note) - LENGTH(REPLACE(note, CHAR(13), '')),
      LENGTH(note) - LENGTH(REPLACE(note, CHAR(10), ''))
    FROM casemgmt_note WHERE demographic_no=${patient} AND note LIKE ${h.sqlString(`%${needle}%`)}
    ORDER BY note_id DESC LIMIT 1`;
  const deadline = Date.now() + 20000;
  let row;
  do {
    [row] = sql.rows(query);
    if (row && row[2] === '1') break;
    await new Promise((resolve) => setTimeout(resolve, 250));
  } while (Date.now() < deadline);
  h.assert(row, 'Save did not create a casemgmt_note row for the fixture note');
  const [noteId, uuid, matches, carriageReturns, lineFeeds] = row;
  return { noteId, uuid, matches: matches === '1', carriageReturns: Number(carriageReturns), lineFeeds: Number(lineFeeds) };
}

function assertStoredAsTyped(note, expected, what) {
  const typedBreaks = (expected.match(/\n/g) || []).length;
  h.assert(note.carriageReturns === 0, `${what} was stored with ${note.carriageReturns} carriage return(s)`);
  h.assert(note.lineFeeds === typedBreaks,
    `${what} was stored with ${note.lineFeeds} line break(s); ${typedBreaks} were typed`);
  h.assert(note.matches, `${what} was not stored exactly as typed`);
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

  const chart = await session.chart();
  const firstText = noteText(tag, 'opening editor');
  let first;

  await session.step('Opening note editor soft-wraps and saves only the typed line break', async () => {
    const editor = activeEditor(chart);
    await editor.first().waitFor({ state: 'visible', timeout: 30000 });
    h.assert(await editor.count() === 1, `expected one active note editor, found ${await editor.count()}`);
    await typeInto(editor, firstText);
    assertSoftWrap(await probeEditor(editor), 'the opening note editor');
    await saveNote(chart);
    first = await savedNote(sql, patient, `${tag} opening editor`, firstText);
    assertStoredAsTyped(first, firstText, 'the note saved from the opening editor');
    // Save binds the saved note to the form; the next steps must act on a different editor.
    await chart.waitForFunction((noteId) => {
      const field = document.querySelector('form[name="caseManagementEntryForm"] input[name="noteId"]');
      return field && field.value === noteId;
    }, first.noteId, { timeout: 30000 });
  });

  await session.step('New-note editor soft-wraps and saves only the typed line break', async () => {
    await chart.locator('#newNoteImg').first().click();
    const editor = chart.locator('#encMainDiv .newNote textarea[name="caseNote_note"]');
    await editor.first().waitFor({ state: 'visible', timeout: 30000 });
    h.assert(await activeEditor(chart).count() === 1, 'the new-note icon left more than one active editor');
    const text = noteText(tag, 'new-note icon');
    await typeInto(editor, text);
    assertSoftWrap(await probeEditor(editor), 'the new-note icon editor');
    await saveNote(chart);
    const saved = await savedNote(sql, patient, `${tag} new-note icon`, text);
    h.assert(saved.uuid !== first.uuid, 'the new-note icon editor overwrote the first note');
    assertStoredAsTyped(saved, text, 'the note saved from the new-note icon editor');
  });

  await session.step('Edit-link editor soft-wraps and re-saves only the typed line breaks', async () => {
    // The Edit link of the first note, which is now in view mode (its id carries the DOM note index).
    const noteDiv = chart.locator('#encMainDiv div[id^="n"]').filter({ hasText: `${tag} opening editor` }).first();
    const editLink = noteDiv.locator('a[id^="edit"]').first();
    await editLink.waitFor({ state: 'visible', timeout: 30000 });
    await editLink.click();
    const editor = noteDiv.locator('textarea[name="caseNote_note"]');
    await editor.first().waitFor({ state: 'visible', timeout: 30000 });
    const loaded = await editor.inputValue();
    h.assert(loaded.replace(/\s+$/, '') === firstText,
      'the Edit link did not load the saved note text unchanged into the editor');
    const text = `${firstText}\n${tag} edited paragraph`;
    await typeInto(editor, text);
    assertSoftWrap(await probeEditor(editor), 'the Edit-link editor');
    await saveNote(chart);
    const saved = await savedNote(sql, patient, `${tag} edited paragraph`, text);
    h.assert(saved.uuid === first.uuid, 'editing the note saved a different note instead of a new revision');
    assertStoredAsTyped(saved, text, 'the note re-saved from the Edit-link editor');
  });
}

if (require.main === module) runWorkflow('echart-note-soft-wrap', workflow);
module.exports = { workflow, noteText, assertSoftWrap, assertStoredAsTyped };
