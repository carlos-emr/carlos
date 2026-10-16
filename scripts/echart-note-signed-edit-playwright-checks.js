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
 * echart-note-signed-edit -- editing a signed chart note and pressing Sign & Save stores the edit
 * as a new signed revision, whatever language the browser asks for (issue #4711).
 *
 * User path: Master Record ▸ E-Chart ▸ type a note ▸ Sign & Save (method=saveAndExit; the chart
 * closes) ▸ E-Chart again ▸ the signed note's Edit link (method=edit renders noteIssueList.jsp) ▸
 * type an addendum ▸ Sign & Save.
 *
 * WHY THE BROWSER LANGUAGE. The Edit fragment rendered the note's encounter date with
 * <fmt:formatDate>, which formats in the language of the request's Accept-Language and, for a
 * language with no date format, prints java.sql.Timestamp.toString() ("2026-10-15 19:14:08.0").
 * The save reads only "dd-MMM-yyyy H:mm" with English months, so Sign & Save of the edit answered
 * HTTP 500 and the edit was lost -- for that language, and for German ("15-Okt.-2026") too. Each
 * edit round below therefore opens the chart with the browser asking for a different language:
 * the runner's own, a German one, and "zz", which has no date format (the #4711 report). A fourth
 * round opens a French chart and posts the form its localized calendar writes when a clinician
 * picks a date ("30-sept-2026 08:30"); the field shows English months there too, which the
 * French chart's own past-date check (validDate()) must also read.
 *
 * Asserted per round:
 *   - the Edit fragment's observation_date field holds the stored encounter date as
 *     dd-MMM-yyyy HH:mm with English months, in every language;
 *   - Sign & Save answers below 400 and closes the chart;
 *   - it stores ONE new row with the same uuid (the next revision), signed by the test provider,
 *     carrying the earlier text and the addendum, with the encounter date kept (to the minute)
 *     or, in the calendar round, the picked date;
 *   - every earlier revision is unchanged (text, uuid, signer, signed flag, dates).
 * Last, a date the server cannot read (the raw timestamp a pre-fix page posted) is refused
 * without a 500: the chart window shows why and the unsaved text, and nothing is stored.
 *
 * Fixtures: the owned synthetic patient from lib/workflow-session.js; every note, note
 * link/ext/issue row and eChart row it gets is removed by patient id in cleanup, which asserts
 * none remain (the runner also clears the patient's note locks and drafts).
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
// CaseManagementEntry2Action appends "\n[Signed on <date> by <provider>]" when it signs.
const SIGNATURE_STAMP = /\[Signed on [^\]\n]+\]/;
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
// The month names flatpickr's French locale writes into the field: no abbreviation period.
const FRENCH_CALENDAR_MONTHS = ['janv', 'févr', 'mars', 'avr', 'mai', 'juin', 'juil', 'août', 'sept', 'oct', 'nov', 'déc'];
// The value a page rendered before the fix posted (java.sql.Timestamp.toString()).
const RAW_TIMESTAMP = '2026-10-15 19:14:08.0';

const isEntry = method => r => r.request().method() === 'POST' && /\/CaseManagementEntry(\?|$)/.test(r.url())
  && new URLSearchParams(r.request().postData() || '').get('method') === method;

/** A MariaDB DATETIME ("2026-10-15 19:14:08") as the editor field shows it ("15-Oct-2026 19:14"). */
function canonical(datetime) {
  const m = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})/.exec(datetime || '');
  h.assert(m, `Unexpected stored observation date "${datetime}"`);
  return `${m[3]}-${MONTHS[Number(m[2]) - 1]}-${m[1]} ${m[4]}:${m[5]}`;
}

/**
 * A recent past date, 08:30, as the French calendar writes it and as the database should then
 * hold it. Its month is one whose French name differs from the English one, so the save has to
 * read French (October and November would pass as English); at most two months back, well inside
 * the chart's one-year full-display window.
 */
function frenchCalendarPick(today = new Date()) {
  const day = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1, 8, 30);
  while (FRENCH_CALENDAR_MONTHS[day.getMonth()] === MONTHS[day.getMonth()].toLowerCase()) day.setDate(day.getDate() - 1);
  const dd = String(day.getDate()).padStart(2, '0');
  const mm = String(day.getMonth() + 1).padStart(2, '0');
  return {
    text: `${dd}-${FRENCH_CALENDAR_MONTHS[day.getMonth()]}-${day.getFullYear()} 08:30`,
    stored: `${day.getFullYear()}-${mm}-${dd} 08:30`,
  };
}

/**
 * The Master Record reloads when the chart closes; wait for its E-Chart link, then open the chart
 * with the browser asking for `language` (null: the runner's own). Only the chart window asks for
 * it, so the Master Record stays in the language its E-Chart link is found by.
 */
async function reopenChart(s, language) {
  await s.master.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
  await s.master.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await s.master.locator('a').filter({ hasText: /^\s*E-?Chart\s*$/i }).first().waitFor({ state: 'visible', timeout: TIMEOUT });
  if (!language) return s.chart();
  let chart;
  await s.context.setExtraHTTPHeaders({ 'Accept-Language': language });
  try {
    chart = await s.chart();
  } finally {
    await s.context.setExtraHTTPHeaders({});
  }
  await chart.setExtraHTTPHeaders({ 'Accept-Language': language });
  return chart;
}

async function workflow(s) {
  const { sql, patient, provider, marker, recorder } = s;
  const q = h.sqlString;
  // Sign & Save submits the form natively. Clicked while the chart is still paging notes or
  // refreshing issues, it aborts those requests -- a clinician's chart is settled first, so wait.
  const inFlight = new Map();
  s.context.on('request', request => {
    if (['xhr', 'fetch'].includes(request.resourceType())) inFlight.set(request, request.frame().page());
  });
  for (const event of ['requestfinished', 'requestfailed']) s.context.on(event, request => inFlight.delete(request));
  async function settled(page) {
    const deadline = Date.now() + TIMEOUT;
    let quietSince = Date.now();
    while (Date.now() < deadline) {
      if ([...inFlight.values()].includes(page)) quietSince = Date.now();
      else if (Date.now() - quietSince >= 500) return;
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    h.assert(false, 'The chart still had requests in flight after 30 s');
  }
  /**
   * Typing into the editor resizes it, and the chart can start an older-notes page fetch
   * (CaseManagementView) a moment later -- sometimes just as Sign & Save submits, whose navigation
   * then abandons it. That is the browser dropping a load, not an application failure: excuse
   * exactly those entries recorded since `mark` (an aborted CaseManagementView XHR whose document
   * went away); every other failure stays a finding.
   */
  function consumeAbandonedPaging(mark) {
    for (let i = recorder.requestFailures.length - 1; i >= mark; i--) {
      const entry = recorder.requestFailures[i];
      if (entry.resourceType === 'xhr' && /ERR_ABORTED/.test(entry.errorText || '')
          && new URL(entry.url).pathname.endsWith('/CaseManagementView')
          && typeof entry.navigatedAway === 'function' && entry.navigatedAway()) {
        recorder.requestFailures.splice(i, 1);
      }
    }
  }
  const tag = marker.slice(-10);
  const firstText = `${tag} signed note: FAKE review, plan unchanged.`;
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
      n.signed, COALESCE(n.signing_provider_no,''), n.observation_date,
      MD5(CONCAT_WS('|', n.note, n.uuid, n.signed, COALESCE(n.signing_provider_no,''), n.observation_date, n.update_date)),
      n.note
    FROM casemgmt_note n WHERE n.demographic_no=${patient} AND LOCATE(${q(tag)}, n.note) > 0 ORDER BY n.note_id`)
    .map(([id, uuid, revision, signed, signer, observed, digest, text]) => ({
      id, uuid, revision, signed: signed === '1', signer, observed, digest, text,
    }));

  /** Sign & Save posts the entry form natively (saveAndExit); a stored note closes the chart. */
  async function signAndSave(chart) {
    await settled(chart);
    const mark = recorder.requestFailures.length;
    const closed = chart.waitForEvent('close', { timeout: TIMEOUT }).then(() => true).catch(() => false);
    const [response] = await Promise.all([
      chart.waitForResponse(isEntry('saveAndExit'), { timeout: TIMEOUT }),
      chart.locator('#signSaveImg').first().click(),
    ]);
    const posted = new URLSearchParams(response.request().postData() || '');
    h.assert(response.status() < 400, `Sign & Save answered HTTP ${response.status()} `
      + `(observation_date posted as "${posted.get('observation_date')}")`);
    h.assert(await closed, 'Sign & Save did not close the chart window');
    consumeAbandonedPaging(mark);
    return posted;
  }

  /**
   * Reopen the chart in `language` (null: the runner's), open the signed note's editor with the
   * Edit link (method=edit), and type an addendum. Returns the chart, the field value the Edit
   * fragment rendered and the text now in the editor.
   */
  async function editLatest(language, addendum) {
    const chart = await reopenChart(s, language);
    const noteDiv = chart.locator('#encMainDiv div[id^="n"]').filter({ hasText: tag }).last();
    await noteDiv.waitFor({ state: 'visible', timeout: TIMEOUT });
    const containerId = await noteDiv.getAttribute('id');
    h.assert(/^n\d+$/.test(containerId), 'The signed note has no stable editor container ID');
    h.assert(await noteDiv.locator('textarea[name="caseNote_note"]').count() === 0,
      'The signed note opened in an editor instead of read-only');
    const editLink = noteDiv.locator('a[id^="edit"]').first();
    await editLink.waitFor({ state: 'visible', timeout: TIMEOUT });
    // editNote() posts the note's issue list (method=edit) and then reloads the issues panel.
    const [edit] = await Promise.all([
      chart.waitForResponse(isEntry('edit'), { timeout: TIMEOUT }),
      chart.waitForResponse(r => /\/encounter\/displayIssues/.test(r.url()), { timeout: TIMEOUT }),
      editLink.click(),
    ]);
    h.assert(edit.status() < 400, `Edit answered HTTP ${edit.status()}`);
    if (language) {
      h.assert(edit.request().headers()['accept-language'] === language,
        `The Edit request was not sent in "${language}"`);
    }
    // A textarea keeps its text in .value, so the view's hasText filter no longer matches it.
    const editor = chart.locator(`#${containerId} textarea[name="caseNote_note"]`);
    await editor.waitFor({ state: 'visible', timeout: TIMEOUT });
    const field = chart.locator('#observationDate');
    await field.waitFor({ state: 'attached', timeout: TIMEOUT });
    const rendered = await field.inputValue();
    h.assert(await chart.locator('#signSaveImg').first().isVisible(), 'Edit of a signed note offers no Sign & Save');
    const loaded = await editor.inputValue();
    h.assert(SIGNATURE_STAMP.test(loaded), 'Edit did not load the signed note text');
    const typed = `${loaded.trimEnd()}\n${addendum}`;
    await editor.click();
    await editor.fill(typed);
    return { chart, rendered, typed };
  }

  let stored;
  await s.step('E-Chart ▸ type a note ▸ Sign & Save stores it signed by the test provider', async () => {
    const chart = await s.chart();
    const editor = chart.locator('#encMainDiv textarea[name="caseNote_note"]');
    await editor.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await editor.count() === 1, `Expected one open note editor, found ${await editor.count()}`);
    await editor.click();
    await editor.fill(firstText);
    await signAndSave(chart);
    stored = revisions();
    h.assert(stored.length === 1, `Sign & Save stored ${stored.length} revision(s) of the note, not 1`);
    h.assert(stored[0].signed && stored[0].signer === provider, 'The note was not stored signed by the test provider');
    h.assert(stored[0].text.includes(firstText) && SIGNATURE_STAMP.test(stored[0].text),
      'The stored note does not hold the typed text and its signature stamp');
    h.assert(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(stored[0].observed), 'The signed note has no encounter date');
  });

  const rounds = [
    { label: "in the runner's browser language", language: null },
    { label: 'in German, whose month names the save could not read', language: 'de-DE,de;q=0.9' },
    { label: 'in a language with no date format (the #4711 report)', language: 'zz' },
    {
      label: 'in French, with a date picked from the French calendar',
      language: 'fr-CA,fr;q=0.9',
      // flatpickr's French months carry no period; the JDK's do ("févr."). Both must be read.
      pick: frenchCalendarPick(),
      // The chart's own month labels (validDate()) and calendar are French in this round.
      labels: ['Janv', 'Févr', 'Mars', 'Avr', 'Mai', 'Juin', 'Juil', 'Août', 'Sept', 'Oct', 'Nov', 'Déc'],
    },
  ];
  for (const [index, round] of rounds.entries()) {
    await s.step(`Edit ▸ Sign & Save ${round.label} stores a new signed revision and keeps the earlier ones`, async () => {
      const before = stored;
      const latest = before[before.length - 1];
      const addendum = `${tag} addendum ${index + 1}: FAKE follow-up booked.`;
      const { chart, rendered, typed } = await editLatest(round.language, addendum);
      h.assert(rendered === canonical(latest.observed),
        `The Edit field rendered the encounter date as "${rendered}", not "${canonical(latest.observed)}"`);
      if (round.labels) {
        const labels = await chart.evaluate(() => Array.from(month));
        h.assert(JSON.stringify(labels) === JSON.stringify(round.labels),
          `The chart was not rendered in the round's language (month labels ${labels.join(', ')})`);
      }
      if (round.pick) {
        // The calendar writes the readonly field itself; this is the text it leaves there.
        await chart.locator('#observationDate').evaluate((field, text) => { field.value = text; }, round.pick.text);
      }
      const posted = await signAndSave(chart);
      h.assert(posted.get('caseNote_note').replace(/\r\n/g, '\n') === typed, 'Sign & Save did not post the edited text');
      stored = revisions();
      h.assert(stored.length === before.length + 1,
        `The edit left ${stored.length} stored revision(s) of the note, not ${before.length + 1}`);
      for (let i = 0; i < before.length; i++) {
        h.assert(stored[i].id === before[i].id && stored[i].digest === before[i].digest && stored[i].signed,
          `Signing the edit changed stored revision ${before[i].revision}`);
      }
      const added = stored[stored.length - 1];
      h.assert(Number(added.id) > Number(latest.id) && added.uuid === stored[0].uuid,
        'The edit was stored as a different note instead of a new revision of the same one');
      h.assert(added.revision === String(Number(latest.revision) + 1), `The edit was stored as revision ${added.revision}`);
      h.assert(added.signed && added.signer === provider, 'The new revision is not signed by the test provider');
      h.assert(added.text.includes(firstText) && added.text.includes(addendum),
        'The new revision does not carry the earlier text and the addendum');
      const expected = round.pick ? round.pick.stored : latest.observed.slice(0, 16);
      h.assert(added.observed.slice(0, 16) === expected,
        `The new revision's encounter date is ${added.observed}, not ${expected}`);
    });
  }

  await s.step('a date the server cannot read is refused with its reason and the unsaved text, and nothing is stored', async () => {
    const before = stored;
    const addendum = `${tag} refused addendum: FAKE.`;
    const { chart, typed } = await editLatest(null, addendum);
    await chart.locator('#observationDate').evaluate((field, text) => { field.value = text; }, RAW_TIMESTAMP);
    await settled(chart);
    const since = {
      responses: recorder.badResponses.length, console: recorder.consoleIssues.length, failures: recorder.requestFailures.length,
    };
    const [response] = await Promise.all([
      chart.waitForResponse(isEntry('saveAndExit'), { timeout: TIMEOUT }),
      chart.locator('#signSaveImg').first().click(),
    ]);
    h.assert(response.status() === 400, `An unreadable encounter date answered HTTP ${response.status()}, not 400`);
    await chart.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
    const heading = (await chart.locator('h3').first().innerText()).trim();
    h.assert(/encounter date could not be read/i.test(heading), `The refusal page says "${heading}"`);
    const draft = await chart.locator('#unsaved-note-text').inputValue();
    h.assert(draft.replace(/\r\n/g, '\n').trim() === typed.trim(), 'The refusal page does not offer the unsaved text to copy');
    h.assert(!/HTTP Status 500|unexpected error/i.test(await chart.locator('body').innerText()), 'The refusal rendered an error page');
    stored = revisions();
    h.assert(stored.length === before.length, `The refused save stored ${stored.length - before.length} revision(s)`);
    for (let i = 0; i < before.length; i++) {
      h.assert(stored[i].digest === before[i].digest, `The refused save changed stored revision ${before[i].revision}`);
    }
    // Consume exactly the refusal this step drew; any other signal stays strict.
    const refusal = recorder.badResponses.findIndex((entry, i) => i >= since.responses && entry.status === 400
      && entry.method === 'POST' && /\/CaseManagementEntry(\?|$)/.test(entry.url));
    h.assert(refusal >= 0, 'The refusal was not recorded as the 400 document it is');
    recorder.badResponses.splice(refusal, 1);
    for (let i = recorder.consoleIssues.length - 1; i >= since.console; i--) {
      if (/status of 400 \(/.test(recorder.consoleIssues[i].text)) recorder.consoleIssues.splice(i, 1);
    }
    consumeAbandonedPaging(since.failures);
    await chart.close();
  });
}

if (require.main === module) runWorkflow('echart-note-signed-edit', workflow, { openPatient: true });
module.exports = { workflow, canonical, frenchCalendarPick };
