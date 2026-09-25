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
  providerNo: '',
  sessionId: '',
  /** What the patient's chart summary looked like before this run wrote anything. */
  chartBefore: null,
};

/**
 * The chart summary columns copyNote2cpp() can append a saved note into.
 *
 * Which one it picks depends on the item's issue code (SocHistory here), and saveCPPIntoEchart()
 * mirrors the same five into the legacy eChart row. Cleanup covers all of them rather than only
 * the one this check currently drives, so changing the issue does not silently leave text behind.
 */
const CHART_TEXT_COLUMNS = ['socialHistory', 'familyHistory', 'medicalHistory', 'ongoingConcerns', 'reminders'];

/** The same, plus the column saveNote() writes on the legacy row. */
const ECHART_TEXT_COLUMNS = [...CHART_TEXT_COLUMNS, 'encounter'];

/**
 * A SQL predicate matching rows whose column contains `needle` literally.
 *
 * NOT `LIKE '%needle%'`. The stamp is PW_CPP_EXT_<epoch> and `_` is a single-character wildcard in
 * LIKE, so a LIKE built from it also matches a clinician's text that merely differs in those
 * positions -- and what cleanup does with a match here is DELETE.
 */
function holds(column, needle) {
  return `LOCATE(${sqlString(needle)}, COALESCE(${column}, '')) > 0`;
}

/**
 * What the patient's chart summary held before this run touched it.
 *
 * Read BEFORE the first save, because cleanup has to tell a row this run created from one it only
 * appended to. saveCPP() creates the casemgmt_cpp row when the patient has none, and
 * saveCPPIntoEchart() (EChartDaoImpl:155-182) UPDATES the patient's NEWEST existing eChart row
 * rather than always adding one -- so deleting every eChart row carrying the stamp would take a
 * clinician's encounter history with it.
 */
function readChartSnapshot(sql, demographicNo) {
  // THE `IS NULL` FLAG COMES WITH THE VALUE. `mysql -B` prints SQL NULL and the four-character
  // string 'NULL' identically, so the harness reads both as null (see unescapeMysqlBatchValue) and
  // says plainly that a caller who will WRITE the value back must select a companion flag rather
  // than guess. This value is written back.
  //
  // Which half is live: `provider_no` is `varchar(6) NOT NULL` today, so the flag is the
  // belt-and-braces half and would only start mattering if the column became nullable. The half
  // that is live now is the other reading -- a provider recorded as the literal string 'NULL'
  // comes back as JS null, and without the mapping below the restore would write 'null'.
  const cpp = sql.rows(
    `SELECT provider_no, provider_no IS NULL FROM casemgmt_cpp WHERE demographic_no = ${demographicNo}`,
  );
  const echartLast = sql.rows(
    `SELECT eChartId FROM eChart WHERE demographicNo = ${demographicNo} ORDER BY eChartId DESC LIMIT 1`,
  );
  return {
    cppExisted: cpp.length > 0,
    /** SQL literal that restores the provider exactly, NULL and the string 'NULL' included. */
    cppProviderLiteral: cpp.length
      ? (cpp[0][1] === '1' ? 'NULL' : sqlString(cpp[0][0] === null ? 'NULL' : cpp[0][0]))
      : 'NULL',
    /** The same value for comparison, or null when the column was SQL NULL. */
    cppProviderNo: cpp.length && cpp[0][1] !== '1' ? (cpp[0][0] === null ? 'NULL' : cpp[0][0]) : null,
    echartMaxId: echartLast.length ? String(echartLast[0][0]) : '0',
    echartLastId: echartLast.length ? String(echartLast[0][0]) : '',
  };
}

/**
 * Remove this run's stamped blocks from one column, without overwriting anything written since.
 *
 * Read-modify-write across two database calls is a lost-update window: an entry appended between
 * the SELECT and the UPDATE would be erased by an UPDATE built from the stale value, and this is
 * a clinical column. So the write is a compare-and-swap -- it only lands while the column still
 * holds exactly what was read -- and a losing attempt re-reads and tries again rather than
 * forcing its value through.
 */
function stripStampedBlocks(sql, table, where, column, stamp, failures) {
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const current = sql.value(`SELECT ${column} FROM ${table} WHERE ${where}`);
    // NOTHING TO STRIP UNLESS THE STAMP IS THERE. This is also what keeps the compare-and-swap
    // honest: `mysql -B` prints SQL NULL and the four-character string 'NULL' identically, so the
    // harness reads both as null and warns against writing such a value back (see
    // unescapeMysqlBatchValue). A column carrying this run's stamp is neither, so the ambiguity
    // cannot reach the UPDATE below -- and a column the run never wrote is left exactly as it is,
    // rather than being normalised to '' by a cleanup that had no business touching it.
    if (typeof current !== 'string' || !current.includes(stamp)) {
      return;
    }
    const trimmed = withoutStampedBlocks(current, stamp);
    if (trimmed === current) {
      return;
    }
    sql.execute(`UPDATE ${table} SET ${column} = ${sqlString(trimmed)} `
      + `WHERE ${where} AND ${column} = ${sqlString(current)}`);
    if (sql.value(`SELECT ${column} FROM ${table} WHERE ${where}`) === trimmed) {
      return;
    }
  }
  failures.push(`${table}.${column} still carries this run's text: three compare-and-swap attempts `
    + 'each lost to a concurrent write');
}

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
  const { sql, stamp, demographicNo, providerNo, sessionId, chartBefore } = fixture;
  if (!sql) {
    return;
  }
  const statements = [];
  const failuresBeforeStatements = [];
  /** [table, where, columns] sets whose stamped blocks are stripped, not deleted. */
  const stripFrom = [];
  if (stamp) {
    // THE SIGNATURE ROW TOO. issueNoteSave marks the note signed, and
    // CaseManagementManagerImpl hashes a signed note into hash_audit (type "enc", the note id in
    // the `id` column, which the entity calls id2). Deleting the note without it leaves an audit
    // row pointing at a note that no longer exists, once per save this check makes.
    statements.push(['the stamped item\'s hash audit rows',
      `DELETE h FROM hash_audit h JOIN casemgmt_note n ON n.note_id = h.id `
      + `WHERE h.type = 'enc' AND LOCATE(${sqlString(stamp)}, n.note) = 1`]);
    // CHILDREN BEFORE THE PARENT, and the note row is what identifies them all,
    // so every child delete joins back to it and the note goes last.
    // casemgmt_note_ext and casemgmt_issue_notes carry real foreign keys to
    // casemgmt_note; casemgmt_note_link does not, but issueNoteSave() writes one
    // through addNewNoteLink() and an orphan there outlives the note.
    statements.push(['the stamped item\'s extension rows',
      `DELETE e FROM casemgmt_note_ext e JOIN casemgmt_note n ON n.note_id = e.note_id `
      + `WHERE LOCATE(${sqlString(stamp)}, n.note) = 1`]);
    statements.push(['the stamped item\'s issue links',
      `DELETE i FROM casemgmt_issue_notes i JOIN casemgmt_note n ON n.note_id = i.note_id `
      + `WHERE LOCATE(${sqlString(stamp)}, n.note) = 1`]);
    statements.push(['the stamped item\'s note link',
      `DELETE l FROM casemgmt_note_link l JOIN casemgmt_note n ON n.note_id = l.note_id `
      + `WHERE LOCATE(${sqlString(stamp)}, n.note) = 1`]);
    statements.push(['the stamped CPP item', `DELETE FROM casemgmt_note WHERE LOCATE(${sqlString(stamp)}, note) = 1`]);
  }
  // THE SUMMARY THE SAVE APPENDED TO, WHICH IS NOT THE NOTE. copyNote2cpp()/saveCPP() append the
  // note's text to the patient's casemgmt_cpp summary, and saveCPPIntoEchart() mirrors it into the
  // legacy eChart row when AbandonOldChart is off (it is off by default; this deployment has it
  // on). Deleting the note alone would leave every run's text in the chart for good -- but so
  // would a blunter cleanup take a clinician's row with it, because the eChart write UPDATES the
  // newest row the patient already has rather than adding one.
  if (stamp && demographicNo && chartBefore) {
    // Rows this run CREATED: newer than anything the patient had, and carrying the stamp.
    const echartStamped = ECHART_TEXT_COLUMNS.map((column) => holds(column, stamp)).join(' OR ');
    statements.push(['the legacy eChart rows this run created',
      `DELETE FROM eChart WHERE demographicNo = ${demographicNo} `
      + `AND eChartId > ${chartBefore.echartMaxId} AND (${echartStamped})`]);

    // Rows that ALREADY EXISTED: the run only appended to them, so only the appended blocks go.
    stripFrom.push(['casemgmt_cpp', `demographic_no = ${demographicNo}`, CHART_TEXT_COLUMNS]);
    if (chartBefore.echartLastId) {
      stripFrom.push(['eChart', `eChartId = ${chartBefore.echartLastId}`, ECHART_TEXT_COLUMNS]);
    }

    if (!chartBefore.cppExisted) {
      // The patient had no CPP row at all, so saveCPP() made one. Removed only once it is empty
      // again -- if a concurrent save has put real text in it, the row stays.
      const emptied = CHART_TEXT_COLUMNS.map((column) => `COALESCE(${column}, '') = ''`).join(' AND ');
      statements.push(['the CPP summary row this run created',
        `DELETE FROM casemgmt_cpp WHERE demographic_no = ${demographicNo} AND ${emptied}`]);
    } else if (providerNo && chartBefore.cppProviderNo !== providerNo) {
      // saveCPP() also stamps the row with whoever saved. Put the previous provider back, but only
      // while the row still records this run's -- a save since then is not ours to rewind.
      statements.push(["the CPP summary's provider",
        `UPDATE casemgmt_cpp SET provider_no = ${chartBefore.cppProviderLiteral} `
        + `WHERE demographic_no = ${demographicNo} AND provider_no = ${sqlString(providerNo)}`]);
    }
    // NOT REWOUND, DELIBERATELY: casemgmt_cpp.update_date and eChart.timeStamp. Both record when
    // the row last changed, which this run genuinely did; and a concurrent save cannot be told
    // from this one by its value, so restoring the old instant could misdate somebody else's edit.
    // Leaving a truthful timestamp is the lesser error.
  }
  if (demographicNo && sessionId) {
    statements.push(['this session\'s note lock',
      `DELETE FROM casemgmt_note_lock WHERE demographic_no = ${demographicNo} `
      + `AND session_id = '${sessionId}'`]);
  }
  const failures = [...failuresBeforeStatements];
  try {
    // STRIPS BEFORE DELETES. "the CPP summary row this run created" only removes the row once its
    // summary columns are empty again, which is what the strip does; running it the other way
    // round would leave the row behind on every run.
    for (const [table, where, columns] of stripFrom) {
      for (const column of columns) {
        try {
          stripStampedBlocks(sql, table, where, column, stamp, failures);
        } catch (error) {
          failures.push(`${table}.${column}: ${(error && error.message) || 'could not be rewritten'}`);
        }
      }
    }
    for (const [what, statement] of statements) {
      try {
        sql.execute(statement);
      } catch (error) {
        failures.push(`${what}: ${(error && error.message) || 'delete failed'}`);
      }
    }
  } finally {
    // ALWAYS, EVEN ON THE WAY OUT OF A THROW: createSqlRunner writes MYSQL_PASSWORD into a
    // temporary client.cnf, and dispose() is what removes it.
    sql.dispose();
  }
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
    + `JOIN casemgmt_note n ON n.note_id = e.note_id WHERE LOCATE(${sqlString(stamp)}, n.note) = 1 ORDER BY e.key_val`,
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
    const chartUrl = new URL(chartPage.url());
    fixture.demographicNo = sqlNumber(chartUrl.searchParams.get('demographicNo') || '',
      'the demographicNo in the chart URL');
    // Read from the chart rather than assumed: saveCPP() stamps the summary row with whoever
    // saved, and cleanup puts the previous provider back only while the row still records this one.
    fixture.providerNo = sqlNumber(chartUrl.searchParams.get('providerNo') || '',
      'the providerNo in the chart URL');

    // BEFORE ANYTHING IS WRITTEN. Cleanup has to tell a chart row this run created from one it
    // only appended to, and after the first save that distinction is gone.
    fixture.chartBefore = readChartSnapshot(sql, fixture.demographicNo);

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
