#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for CPP (orange box) items that carry MORE THAN ONE
 * extension key: can a clinician save a Social History item with both a start
 * date and a resolution date, read both back, and archive it -- without the box
 * turning into "Error: 500"?
 *
 * WHY THIS CHECK EXISTS (issue #3739). issueNoteSave() in
 * CaseManagementEntry2Action hoisted ONE CaseManagementNoteExt out of the write
 * loop and re-saved that instance for every populated field. saveNoteExt()
 * bottoms out in entityManager().persist(), and each call runs in its own
 * transaction, so after the first key is written the instance carries an id and
 * is DETACHED. The second persist() therefore threw
 * org.hibernate.PersistentObjectException, surfaced as
 * jakarta.persistence.EntityExistsException, and the POST answered 500: the
 * second key never reached the database and updateCPPNote()'s onFailure handler
 * repainted the whole box as "<box>Error: 500".
 *
 * That is why this check insists on TWO dated keys. A CPP item with a single
 * extension never trips it -- one persist() per request succeeds -- so a
 * one-field check would pass against the broken code and prove nothing.
 *
 * THE ARCHIVE IS THE SECOND HALF, and it is a different trap. Archiving re-runs
 * the same write path over a note that ALREADY has its extension rows, so a
 * plain persist() per save would pile a second row onto every key rather than
 * replace it. The readers disagree about which duplicate wins
 * (CaseManagementNoteExtDAOImpl orders id desc; NotesService.getNote() assigns
 * from every row it walks and ends on the OLDEST), so a duplicate is not
 * cosmetic: it makes an edited date read back as the value it replaced. The
 * check asserts the row COUNT is unchanged after the archive, not merely that
 * the request succeeded.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, Search, the patient's Master
 * Record, E-Chart, the Social History box's "+", then the dialog's own
 * Sign & Save and Archive buttons. Nothing is posted behind the page's back --
 * the point is partly that the page's own CarlosAjax POST carries its CSRF
 * token and its serialized form exactly as the browser builds them.
 *
 * WHAT IT WRITES, AND REMOVES. One casemgmt_note row and its casemgmt_note_ext
 * rows for the selected patient, plus the casemgmt_note_lock the chart takes.
 * All are deleted afterwards through MYSQL_*; the note is found by the stamp
 * this run generated, so no clinician's note is matched. The lock delete is
 * scoped to THIS browser session id (casemgmt_note_lock records the id that
 * took the lock), so a lock another session holds on the same patient is left
 * alone. Cleanup runs through runCheck()'s cleanup hook: a delete that fails is
 * a FAIL whatever the assertions said.
 *
 * NO PATIENT KEY IN THE OUTPUT. demographic_no joins straight back to a patient
 * record and runCheck() writes thrown messages into CI artifacts, so the
 * diagnostics say "the selected patient" and never print the chart URL.
 *
 * Defaults are for the local devcontainer:
 *   MYSQL_PASSWORD=... npm run test:cpp-note-extension-archive-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   CPP_EXT_SEARCH=FAKE-           surname prefix used to reach a patient
 *   CPP_EXT_DEMOGRAPHIC_NO=2       which patient's chart to open. Defaults to 2, not 1: the
 *                                  shared helper records that demographic 1's chart answers 500 on
 *                                  the demo dataset, because its HRM rows point at report files
 *                                  that never shipped
 *   CPP_EXT_TIMEOUT_MS=45000       per-step allowance
 */

const {
  assert, assertStrictPage, createRecorder, createSqlRunner, launchBrowser, login, newContext, readConfig, runCheck,
  sqlString,
} = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/** The Social History box in the encounter layout's CPP grid. */
const BOX_ID = 'divR1I1';
/** The hovering CPP item editor newEncounterLayout.jsp renders once per chart. */
const DIALOG = '#showEditNote';
/** Two dated keys, because one persist() per request always succeeds. */
const START_DATE = '2021-01-01';
const RESOLUTION_DATE = '2021-02-02';
/** casemgmt_note_ext.key_val values CaseManagementNoteExt writes for those fields. */
const EXPECTED_KEYS = ['Start Date', 'Resolution Date'];

/**
 * What the run leaves for cleanup() to remove, filled in as the run learns it.
 * Module state rather than a closure because runCheck() calls run and cleanup
 * separately, and cleanup must see whatever the run recorded before it failed.
 */
const fixture = {
  sql: null,
  stamp: '',
  demographicNo: '',
  sessionId: '',
};

/**
 * Strip the blocks a stamped note appended to a CPP summary column.
 *
 * copyNote2cpp() appends "\n-----[[<date>]]-----\n<note text>" to casemgmt_cpp.socialHistory on
 * every save, so the summary grows by this check's text each run and deleting the note alone leaves
 * it there. Only the blocks carrying the stamp are removed, and only from that marker to the start
 * of the next one, so a clinician's entries either side are untouched.
 *
 * Exported for the node test: this is string surgery on a clinical column and deserves assertions
 * of its own.
 */
function withoutStampedBlocks(summary, stamp) {
  if (!summary || !stamp) {
    return summary || '';
  }
  // Split on the separator, keeping it attached to the block it introduces.
  const blocks = summary.split(/(?=\n-----\[\[)/);
  return blocks.filter((block) => !block.includes(stamp)).join('');
}

/** A plain integer, or the value never reaches a query. */
function sqlNumber(value, what) {
  assert(/^\d+$/.test(String(value)), `${what} is not a plain number, so it cannot be used in a database query`);
  return String(value);
}

/**
 * Remove what the run wrote, every statement attempted, any failure thrown.
 *
 * Thrown rather than logged: runCheck() promotes a cleanup error to FAIL, while
 * a `process.exitCode = 1` set here is overwritten when runCheck() records the
 * PASS the assertions earned -- the check would report success with its fixture
 * rows still in the chart.
 */
async function cleanup() {
  const { sql, stamp, demographicNo, sessionId } = fixture;
  if (!sql) {
    return;
  }
  const statements = [];
  const failuresBeforeStatements = [];
  if (stamp) {
    // THE SIGNATURE ROW TOO. issueNoteSave marks the note signed, and
    // CaseManagementManagerImpl hashes a signed note into hash_audit (type "enc", the note id in
    // the `id` column, which the entity calls id2). Deleting the note without it leaves an audit
    // row pointing at a note that no longer exists, once per save this check makes.
    statements.push(['the stamped item\'s hash audit rows',
      `DELETE h FROM hash_audit h JOIN casemgmt_note n ON n.note_id = h.id `
      + `WHERE h.type = 'enc' AND n.note LIKE '${stamp}%'`]);
    // CHILDREN BEFORE THE PARENT, and the note row is what identifies them all,
    // so every child delete joins back to it and the note goes last.
    // casemgmt_note_ext and casemgmt_issue_notes carry real foreign keys to
    // casemgmt_note; casemgmt_note_link does not, but issueNoteSave() writes one
    // through addNewNoteLink() and an orphan there outlives the note.
    statements.push(['the stamped item\'s extension rows',
      `DELETE e FROM casemgmt_note_ext e JOIN casemgmt_note n ON n.note_id = e.note_id `
      + `WHERE n.note LIKE '${stamp}%'`]);
    statements.push(['the stamped item\'s issue links',
      `DELETE i FROM casemgmt_issue_notes i JOIN casemgmt_note n ON n.note_id = i.note_id `
      + `WHERE n.note LIKE '${stamp}%'`]);
    statements.push(['the stamped item\'s note link',
      `DELETE l FROM casemgmt_note_link l JOIN casemgmt_note n ON n.note_id = l.note_id `
      + `WHERE n.note LIKE '${stamp}%'`]);
    statements.push(['the stamped CPP item', `DELETE FROM casemgmt_note WHERE note LIKE '${stamp}%'`]);
  }
  if (stamp && demographicNo) {
    // THE SUMMARY THE SAVE APPENDED TO, WHICH IS NOT THE NOTE. copyNote2cpp()/saveCPP() copy the
    // note's text into casemgmt_cpp.socialHistory, and saveNote() writes a legacy eChart row too
    // when AbandonOldChart is off (it is off by default; this deployment has it on, so the eChart
    // delete is a no-op here rather than untested-by-omission). Deleting the note without these
    // would leave every run's text in the patient's chart summary for good.
    try {
      const summary = sql.value(
        `SELECT socialHistory FROM casemgmt_cpp WHERE demographic_no = ${demographicNo}`,
      );
      const trimmed = withoutStampedBlocks(summary, stamp);
      if (trimmed !== summary) {
        statements.push(['the stamped text appended to the CPP summary',
          `UPDATE casemgmt_cpp SET socialHistory = ${sqlString(trimmed)} `
          + `WHERE demographic_no = ${demographicNo}`]);
      }
    } catch (error) {
      failuresBeforeStatements.push(
        `the CPP summary could not be read (${(error && error.message) || 'query failed'})`);
    }
    statements.push(['the stamped legacy eChart row',
      `DELETE FROM eChart WHERE demographicNo = ${demographicNo} `
      + `AND (socialHistory LIKE '%${stamp}%' OR encounter LIKE '%${stamp}%')`]);
  }
  if (demographicNo && sessionId) {
    statements.push(['this session\'s note lock',
      `DELETE FROM casemgmt_note_lock WHERE demographic_no = ${demographicNo} `
      + `AND session_id = '${sessionId}'`]);
  }
  const failures = [...failuresBeforeStatements];
  for (const [what, statement] of statements) {
    try {
      sql.execute(statement);
    } catch (error) {
      failures.push(`${what}: ${(error && error.message) || 'delete failed'}`);
    }
  }
  sql.dispose();
  if (failures.length) {
    throw new Error(`the check could not remove what it wrote (${failures.join('; ')})`);
  }
}

/** Click the dialog button whose title matches, as a clinician would. */
async function clickDialogButton(page, titlePattern, what) {
  for (const button of await page.locator(`${DIALOG} input[type="image"]`).all()) {
    const title = await button.getAttribute('title');
    if (title && titlePattern.test(title)) {
      await button.click();
      return title;
    }
  }
  throw new Error(`the CPP item editor offers no ${what} button, so the flow this check exists for cannot be driven`);
}

/** The POST the CPP dialog makes, whichever button was pressed. */
function isNoteSave(response) {
  return /method=issueNoteSave/.test(response.url()) && response.request().method() === 'POST';
}

/** Did updateCPPNote()'s onFailure handler repaint the box with an HTTP status? */
async function boxErrorText(page) {
  const html = await page.locator(`#${BOX_ID}`).innerHTML().catch(() => '');
  const match = /Error:\s*(\d{3})/.exec(html);
  return match ? match[1] : '';
}

/** The extension rows the stamped item holds, as [key, date] pairs. */
function extensionRows(sql, stamp) {
  return sql.rows(
    `SELECT e.key_val, e.date_value FROM casemgmt_note_ext e `
    + `JOIN casemgmt_note n ON n.note_id = e.note_id WHERE n.note LIKE '${stamp}%' ORDER BY e.key_val`,
  );
}

async function main() {
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const searchTerm = process.env.CPP_EXT_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.CPP_EXT_DEMOGRAPHIC_NO || '2';
  const timeout = Number(process.env.CPP_EXT_TIMEOUT_MS || '45000');
  // The stamp leads the note text so the cleanup can anchor its LIKE.
  const stamp = `PW_CPP_EXT_${Date.now()}`;

  const sql = createSqlRunner(config.mysql);
  fixture.sql = sql;
  fixture.stamp = stamp;

  const recorder = createRecorder();
  let browser;
  try {
    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    // The chart is about to take a lock recording this session's id; capture it
    // now so cleanup can name exactly that row and no other session's.
    const sessionCookie = (await context.cookies()).find((cookie) => cookie.name === 'JSESSIONID');
    assert(sessionCookie && /^[A-Za-z0-9._-]+$/.test(sessionCookie.value),
      'the login left no JSESSIONID cookie, so the note lock this session takes could not be told from another session\'s');
    fixture.sessionId = sessionCookie.value;

    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });
    const { page: chartPage } = await clickOpensPopupOrNavigates(masterPage,
      masterPage.locator('a').filter({ hasText: /^\s*E-?Chart\s*$/i }).first(),
      { context, label: 'echart', recorder, timeout, baseline: [] });
    fixture.demographicNo = sqlNumber(new URL(chartPage.url()).searchParams.get('demographicNo') || '',
      'the demographicNo in the chart URL');

    // Every issueNoteSave POST the page makes, with the status it answered. The
    // 500 this check exists for is invisible in the DOM beyond the repaint, so
    // the status is read from the wire.
    const saves = [];
    chartPage.on('response', (response) => {
      if (/method=issueNoteSave/.test(response.url())) {
        saves.push({ method: response.request().method(), status: response.status() });
      }
    });

    const box = chartPage.locator(`#${BOX_ID}`);
    await box.waitFor({ state: 'visible', timeout });

    // --- add the item, with BOTH dates ---
    await box.locator('a[title="Add Item"]').first().click();
    await chartPage.waitForSelector(DIALOG, { state: 'visible', timeout });
    await chartPage.locator('#noteEditTxt').fill(`${stamp} CPP extension regression item`);
    await chartPage.locator('#startdate').fill(START_DATE);
    await chartPage.locator('#resolutiondate').fill(RESOLUTION_DATE);
    // WAIT FOR THE SAVE ITSELF. An earlier version waited on a DOM predicate that every element
    // satisfies -- the box is already visible and `dataset` is never undefined -- so it returned at
    // once and a fixed sleep decided whether the database had been written yet. The POST is the
    // event that matters, so wait for it, then let the box finish repainting from its reply.
    const saveArrived = chartPage.waitForResponse(isNoteSave, { timeout });
    await clickDialogButton(chartPage, /sign|save/i, 'save');
    await saveArrived;
    await chartPage.waitForTimeout(1500);

    const savedError = await boxErrorText(chartPage);
    const failedSaves = saves.filter((save) => save.status >= 500);
    assert(failedSaves.length === 0,
      `saving a CPP item that carries a start date and a resolution date answered HTTP ${(failedSaves[0] || {}).status}; `
      + 'issueNoteSave is re-persisting one detached CaseManagementNoteExt across the keys (issue #3739)');
    assert(savedError === '',
      `the Social History box repainted as "Error: ${savedError}" after the save, so the POST behind it failed`);

    const savedRows = extensionRows(sql, stamp);
    assert(savedRows.length === EXPECTED_KEYS.length,
      `the saved CPP item holds ${savedRows.length} extension rows instead of ${EXPECTED_KEYS.length}; `
      + 'a start date and a resolution date must each get their own row');
    const savedKeys = savedRows.map((row) => row[0]).sort();
    assert(JSON.stringify(savedKeys) === JSON.stringify([...EXPECTED_KEYS].sort()),
      `the saved CPP item holds extension keys ${JSON.stringify(savedKeys)} instead of ${JSON.stringify(EXPECTED_KEYS)}`);
    const byKey = Object.fromEntries(savedRows);
    assert(byKey['Start Date'] === START_DATE && byKey['Resolution Date'] === RESOLUTION_DATE,
      'the saved CPP item\'s extension rows do not hold the two dates that were entered');

    // --- reopen it: both dates must come BACK, not just be stored ---
    const noteId = sqlNumber(sql.value(`SELECT note_id FROM casemgmt_note WHERE note LIKE '${stamp}%' LIMIT 1`),
      'the saved CPP item\'s note id');
    const reopen = box.locator(`a[onclick*="noteId=${noteId}"]`).first();
    assert(await reopen.count() > 0, 'the saved CPP item does not appear in the Social History box, so it cannot be archived');
    await reopen.click();
    await chartPage.waitForSelector(DIALOG, { state: 'visible', timeout });
    const prefill = await chartPage.evaluate(() => ({
      start: document.getElementById('startdate').value,
      resolution: document.getElementById('resolutiondate').value,
    }));
    assert(prefill.start === START_DATE && prefill.resolution === RESOLUTION_DATE,
      'reopening the CPP item did not bring both dates back, so one extension key was lost between the write and the read');

    // --- archive it: the same write path, over rows that already exist ---
    const savesBeforeArchive = saves.length;
    const archiveArrived = chartPage.waitForResponse(isNoteSave, { timeout });
    await clickDialogButton(chartPage, /archive/i, 'archive');
    await archiveArrived;
    await chartPage.waitForTimeout(1500);
    const archiveSaves = saves.slice(savesBeforeArchive);
    assert(archiveSaves.length > 0, 'clicking Archive posted nothing, so the archive path was never exercised');
    const failedArchives = archiveSaves.filter((save) => save.status >= 500);
    assert(failedArchives.length === 0,
      `archiving the CPP item answered HTTP ${(failedArchives[0] || {}).status} (issue #3739)`);
    const archiveError = await boxErrorText(chartPage);
    assert(archiveError === '',
      `the Social History box repainted as "Error: ${archiveError}" after the archive`);

    const archivedRows = extensionRows(sql, stamp);
    assert(archivedRows.length === EXPECTED_KEYS.length,
      `archiving left ${archivedRows.length} extension rows instead of ${EXPECTED_KEYS.length}; `
      + 'the write path is adding a second row per key rather than updating the one that exists');
    const archived = sql.value(`SELECT archived FROM casemgmt_note WHERE note_id = ${noteId}`);
    assert(archived === '1', 'the archive request succeeded but the CPP item is not marked archived');

    assertStrictPage(recorder, ['echart']);
    console.log('  CPP item with a start date and a resolution date: saved, read back, and archived with one row per key');
    await chartPage.close().catch(() => {});
    return { extensionRows: archivedRows.length };
  } finally {
    if (browser) await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'cpp-note-extension-archive', run: main, cleanup });
}

module.exports = {
  BOX_ID, DIALOG, EXPECTED_KEYS, RESOLUTION_DATE, START_DATE, cleanup, fixture, main,
  withoutStampedBlocks,
};
