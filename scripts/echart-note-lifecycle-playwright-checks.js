#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * echart-note-lifecycle — coverage plan §2.5: a chart note written, signed, reopened, edited and
 * read back through its revision history, the way a clinician does it.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ type in the open note ▸ Sign & Save
 * (method=saveAndExit, the chart closes) ▸ E-Chart again ▸ the signed note's Edit link ▸ type ▸
 * Sign & Save ▸ E-Chart again ▸ the note's revision link (method=notehistory popup).
 *
 * Asserted:
 *   - Sign & Save stores the typed text signed by the test provider.
 *   - Reopened, the signed note is shown read-only with an Edit link. Edit loads its stored text
 *     and offers Sign & Save but not Save: a signed note can only be stored signed again.
 *   - Signing the edit writes a NEW casemgmt_note row with the same uuid and the next revision.
 *     The first row keeps its text, uuid, signer, signed flag and observation and update dates.
 *   - The chart then shows the edited text once and the revision link reads 2. The history popup
 *     lists both revisions, newest first, for this note and patient only.
 * Finding 279 (#4711): on develop, Sign & Save of the edit answers 500, because the edit form posts
 * observation_date as a raw timestamp that noteSave cannot parse. It is fixed on release/2026.08 by
 * #4769, so no step is expected to fail there.
 * Not covered here: Save of an unsigned note, autosave drafts, timers and billing
 * (echart-note-sign-bill, echart-note-editor) or soft wrap (echart-note-soft-wrap).
 *
 * Fixtures: the owned synthetic patient from lib/workflow-session.js; every note, note link/ext/
 * issue row and eChart row it gets is removed by patient id in cleanup, which asserts none remain
 * (the runner also clears the patient's note locks and drafts).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
// CaseManagementEntry2Action appends "\n[Signed on <date> by <provider>]" when it signs.
const SIGNATURE_STAMP = /\[Signed on [^\]\n]+\]/;

const isEntry = method => r => r.request().method() === 'POST' && /\/CaseManagementEntry(\?|$)/.test(r.url())
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

// Edit replaces rendered text with a textarea whose value is not DOM text.
// Keep the stored row's identity instead of re-resolving a hasText filter.
const noteContents = (chart, id) => chart.locator(`#encMainDiv #n${id}`);

async function waitVisible(locator, label, timeout = TIMEOUT) {
  try {
    await locator.waitFor({ state: 'visible', timeout });
  } catch (error) {
    throw new Error(`${label}: ${error.message}`, { cause: error });
  }
}

/** Sign & Save posts the entry form natively (saveAndExit) and the chart window closes itself. */
async function signAndSave(chart) {
  const closed = chart.waitForEvent('close', { timeout: TIMEOUT }).then(() => true).catch(() => false);
  const [response] = await Promise.all([
    chart.waitForResponse(isEntry('saveAndExit'), { timeout: TIMEOUT }),
    chart.locator('#signSaveImg').first().click(),
  ]);
  const posted = new URLSearchParams(response.request().postData() || '');
  h.assert(response.status() < 400, `Sign & Save answered HTTP ${response.status()} `
    + `(observation_date posted as "${posted.get('observation_date')}", obsDate "${posted.get('obsDate')}")`);
  h.assert(await closed, 'Sign & Save did not close the chart window');
}

/** The Master Record reloads when the chart closes; wait for its E-Chart link before opening the chart again. */
async function reopenChart(s) {
  await s.master.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
  await s.master.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await waitVisible(s.master.locator('a').filter({ hasText: /^\s*E-?Chart\s*$/i }).first(),
    'Reopen chart: Master Record E-Chart link');
  return s.chart();
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const tag = marker.slice(-10);
  const firstText = `${tag} lifecycle note: FAKE review, plan unchanged.`;
  const addedText = `${tag} lifecycle addendum: FAKE follow-up booked.`;
  const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0',
      'The owned notes were not removed');
  });
  /** Every stored row of the owned note, oldest first. */
  // The revision is not stored: it is the row's place among the rows that share its uuid.
  const revisions = () => sql.rows(`SELECT n.note_id, n.uuid,
      (SELECT COUNT(*) FROM casemgmt_note r WHERE r.uuid=n.uuid AND r.note_id<=n.note_id),
      n.signed, COALESCE(n.signing_provider_no,''),
      MD5(CONCAT_WS('|', n.note, n.uuid, n.signed, COALESCE(n.signing_provider_no,''), n.observation_date, n.update_date)),
      LOCATE(${q(firstText)}, n.note) > 0, LOCATE(${q(addedText)}, n.note) > 0
    FROM casemgmt_note n WHERE n.demographic_no=${patient} AND LOCATE(${q(tag)}, n.note) > 0 ORDER BY n.note_id`)
    .map(([id, uuid, revision, signed, signer, digest, hasFirst, hasAdded]) => ({
      id, uuid, revision, signed: signed === '1', signer, digest, hasFirst: hasFirst === '1', hasAdded: hasAdded === '1',
    }));

  let first;
  await s.step('E-Chart ▸ type a note ▸ Sign & Save stores it signed by the test provider', async () => {
    const chart = await s.chart();
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    await waitVisible(editor.first(), 'Create note: open note editor');
    h.assert(await editor.count() === 1, `Expected one open note editor, found ${await editor.count()}`);
    await editor.click();
    await editor.fill(firstText);
    await signAndSave(chart);
    const stored = revisions();
    h.assert(stored.length === 1, `Sign & Save stored ${stored.length} revision(s) of the note, not 1`);
    [first] = stored;
    h.assert(first.signed && first.signer === provider, 'The note was not stored signed by the test provider');
    h.assert(first.hasFirst && /^[1-9]\d*$/.test(first.revision), 'The stored note does not hold the typed text');
    h.assert(SIGNATURE_STAMP.test(sql.value(`SELECT note FROM casemgmt_note WHERE note_id=${first.id}`)),
      'The signed note carries no signature stamp');
  });

  let chart;
  let noteDiv;
  await s.step('reopened, the signed note is read-only; Edit loads it and offers Sign & Save but not Save', async () => {
    chart = await reopenChart(s);
    noteDiv = noteContents(chart, first.id);
    await waitVisible(noteDiv, 'Reopen signed note: stored note contents');
    h.assert((await noteDiv.innerText()).includes(firstText), 'The reopened note does not show the stored text');
    h.assert(await noteDiv.locator('textarea[name="caseNote_note"]').count() === 0,
      'The signed note opened in an editor instead of read-only');
    const editLink = noteDiv.locator('a[id^="edit"]').first();
    await waitVisible(editLink, 'Reopen signed note: Edit link');
    // editNote() posts the note's issue list (method=edit) and then reloads the issues panel.
    await Promise.all([
      chart.waitForResponse(isEntry('edit'), { timeout: TIMEOUT }),
      chart.waitForResponse(r => /\/encounter\/displayIssues/.test(r.url()), { timeout: TIMEOUT }),
      editLink.click(),
    ]);
    const editor = noteDiv.locator('textarea[name="caseNote_note"]');
    await waitVisible(editor, 'Edit signed note: caseNote_note textarea');
    const loaded = await editor.inputValue();
    h.assert(loaded.startsWith(firstText) && SIGNATURE_STAMP.test(loaded), 'Edit did not load the signed note text');
    h.assert(await chart.locator('#signSaveImg').first().isVisible(), 'Edit of a signed note offers no Sign & Save');
    h.assert(!(await chart.locator('#saveImg').first().isVisible()), 'Edit of a signed note still offers Save');
    await editor.click();
    await editor.fill(`${loaded.trimEnd()}\n${addedText}`);
  });

  let second;
  await s.step('Sign & Save of the edit stores a new revision with the same uuid and keeps the first one unchanged', async () => {
    await signAndSave(chart);
    const stored = revisions();
    h.assert(stored.length === 2, `The edit left ${stored.length} stored revision(s) of the note, not 2`);
    const [kept] = stored;
    second = stored[1];
    h.assert(kept.id === first.id && kept.digest === first.digest && kept.revision === first.revision && kept.signed,
      'Signing the edit changed the first stored revision');
    h.assert(Number(second.id) > Number(first.id) && second.uuid === first.uuid,
      'The edit was stored as a different note instead of a new revision of the same one');
    h.assert(second.revision === String(Number(first.revision) + 1), `The edit was stored as revision ${second.revision}`);
    h.assert(second.signed && second.signer === provider && second.hasFirst && second.hasAdded,
      'The new revision is not signed by the test provider with the earlier text and the addendum');
  });

  await s.step('the chart shows the edited note once, and its revision link opens both revisions newest first', async () => {
    chart = await reopenChart(s);
    const shown = chart.locator('#encMainDiv .note-contents').filter({ hasText: addedText });
    await waitVisible(noteContents(chart, second.id), 'Reopen edited note: new revision contents');
    h.assert(await shown.count() === 1, `The chart shows the edited note ${await shown.count()} times`);
    const rev = shown.first().locator('a[onclick^="return showHistory("]').first();
    h.assert((await rev.innerText()).trim() === second.revision, `The revision link reads "${(await rev.innerText()).trim()}"`);
    const history = await s.popup(chart, rev, 'note-history');
    const url = new URL(history.url());
    h.assert(url.searchParams.get('method') === 'notehistory' && url.searchParams.get('noteId') === second.id
      && url.searchParams.get('demographicNo') === patient, 'The revision link opened the history of another note or patient');
    await waitVisible(history.locator('h3', { hasText: 'Note Revision History' }), 'Note history: revision heading');
    const body = (await history.locator('body').innerText()).replace(/\s+/g, ' ');
    const newest = body.indexOf(addedText);
    const oldest = body.lastIndexOf(firstText);
    h.assert(newest >= 0, 'The history does not show the edited revision');
    h.assert(oldest > newest, 'The history does not list the first revision after the edited one');
    await history.close();
  });
}

if (require.main === module) runWorkflow('echart-note-lifecycle', workflow, { openPatient: true });
module.exports = { workflow, noteContents, waitVisible };
