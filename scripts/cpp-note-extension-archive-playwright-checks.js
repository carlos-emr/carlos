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
 * extension key: can a clinician save a Medical History item with both a start
 * date and a resolution date, read both back, clear Treatment, and archive it -- without the box
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
 * Archiving creates a new note revision (#4507). The latest revision must carry
 * one extension row per populated key, and the prior revision and its dates must
 * remain unchanged. Duplicate keys can otherwise make readers display old values.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, Search, the patient's Master
 * Record, E-Chart, the Medical History box's "+", then the dialog's own
 * Sign & Save and Archive buttons. Nothing is posted behind the page's back --
 * the point is partly that the page's own CarlosAjax POST carries its CSRF
 * token and its serialized form exactly as the browser builds them.
 *
 * A patient-scoped trigger rejects the edited Resolution Date after the Start Date
 * update. The whole failed save must roll back, and retrying the original revision
 * through the still-open dialog must succeed before archive creates the third revision.
 * The edit also clears Treatment: the latest value must be empty while the original
 * revision retains its populated value, including after archive.
 *
 * WHAT IT WRITES, AND REMOVES. Three casemgmt_note revisions and their casemgmt_note_ext
 * rows for a FAKE patient this check creates (lib/owned-patient.js: last name = a
 * FAKE-PW run marker), plus the casemgmt_note_lock the chart takes, the patient's
 * casemgmt_issue assignment and CPP summary and eChart rows. The patient used to be
 * DEMO patient 2, where the first Social History item also created the casemgmt_issue
 * row the chart assigns the issue by, which stayed behind. The note's rows are deleted
 * afterwards through MYSQL_*, found by the stamp this run generated, and then everything
 * left for the patient and the patient itself by its key. The lock delete is
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
 *   CPP_EXT_TIMEOUT_MS=45000       per-step allowance
 */

const {
  assert, assertStrictPage, createRecorder, createSqlRunner, launchBrowser, login, newContext, readConfig, runCheck,
  sqlString, withExpectedDialogs,
} = require('./lib/playwright-harness');
const { randomBytes } = require('node:crypto');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { createOwnedPatient, newOwnedMarker, removeOwnedPatient } = require('./lib/owned-patient');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/** Medical History exposes Treatment as well as both dates in the actual dialog. */
const BOX_ID = 'divR1I2';
/** The hovering CPP item editor newEncounterLayout.jsp renders once per chart. */
const DIALOG = '#showEditNote';
/** Two dated keys, because one persist() per request always succeeds. */
const START_DATE = '2021-01-01';
const RESOLUTION_DATE = '2021-02-02';
const EDIT_START_DATE = '2022-03-03';
const EDIT_RESOLUTION_DATE = '2022-04-04';
const TREATMENT = 'PW original treatment';
/** casemgmt_note_ext.key_val values CaseManagementNoteExt writes for those fields. */
const EXPECTED_KEYS = ['Start Date', 'Resolution Date', 'Treatment'];

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
  /** The owned patient the item is written for, and its marker (never a demo patient). */
  ownedPatient: '',
  ownedMarker: '',
  /** What the patient's chart summary looked like before this run wrote anything. */
  chartBefore: null,
  /** eChart rows this run's own saves wrote: id -> fingerprint when last written by the run. */
  echartOwned: {},
  /** Registered before CREATE so both the immediate finally and cleanup can drop it. */
  rollbackTrigger: '',
};

/**
 * The chart summary columns copyNote2cpp() can append a saved note into.
 *
 * Which one it picks depends on the item's issue code (MedHistory here), and saveCPPIntoEchart()
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
 * A SQL expression fingerprinting everything an eChart row holds that a save can change.
 *
 * NULL and '' are kept distinct, so a later write that only normalises one into the other still
 * reads as a change.
 */
const ECHART_FINGERPRINT = `MD5(CONCAT_WS(CHAR(31), providerNo, ${
  ECHART_TEXT_COLUMNS.map((column) => `${column} IS NULL, COALESCE(${column}, '')`).join(', ')}))`;

/**
 * Remember the legacy eChart rows this run's own save just wrote, as they stand right now.
 *
 * Called straight after each issueNoteSave answers. saveEchart() adds a row per note save, copying
 * the previous row's encounter text forward, and saveCPPIntoEchart() then UPDATES the newest row
 * (EChartDaoImpl:70-182). So "newer than the snapshot and carrying the stamp" does not identify
 * this run's rows: a clinician saving on the same chart during the run gets a row that is newer,
 * inherits the stamp through that copy, and holds their own note as well. Cleanup deletes only rows
 * recorded here, written as this run's provider, and only while they are unchanged since.
 */
function recordOwnEchartRows(sql) {
  const { demographicNo, providerNo, chartBefore } = fixture;
  if (!demographicNo || !providerNo || !chartBefore) {
    return;
  }
  const rows = sql.rows(
    `SELECT eChartId, ${ECHART_FINGERPRINT} FROM eChart WHERE demographicNo = ${demographicNo} `
    + `AND eChartId > ${chartBefore.echartMaxId} AND providerNo = ${sqlString(providerNo)}`,
  );
  for (const [id, fingerprint] of rows) {
    fixture.echartOwned[sqlNumber(id, 'an eChart row id')] = String(fingerprint);
  }
}

/**
 * The deletes for the eChart rows recordOwnEchartRows() saw, each guarded so that it only lands
 * while the row still belongs to this run's provider and is byte-for-byte what the run left.
 * Exported for the node test: what these statements refuse to delete is the point of them.
 */
function ownedEchartDeletes(demographicNo, providerNo, owned) {
  return Object.entries(owned || {}).map(([id, fingerprint]) => [
    `the legacy eChart row ${id} this run created`,
    `DELETE FROM eChart WHERE eChartId = ${sqlNumber(id, 'an eChart row id')} `
    + `AND demographicNo = ${demographicNo} AND providerNo = ${sqlString(providerNo)} `
    + `AND ${ECHART_FINGERPRINT} = ${sqlString(fingerprint)}`,
  ]);
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
  // THE ROW THE SAVE WILL PICK, BY THE SAME RULE IT PICKS IT. casemgmt_cpp is not unique by
  // demographic -- idx_casemgmt_cpp_demographic_no is a non-unique index -- and
  // CaseManagementCPPDAO.getCPP() takes `order by update_date desc` and keeps the first. An
  // unordered snapshot could therefore capture a different row from the one issueNoteSave()
  // updates, and cleanup keyed on demographic_no alone would strip the wrong row while reporting
  // success. Every CPP predicate below is scoped to this id instead.
  const cpp = sql.rows(
    `SELECT id, provider_no, provider_no IS NULL FROM casemgmt_cpp `
    + `WHERE demographic_no = ${demographicNo} ORDER BY update_date DESC LIMIT 1`,
  );
  // The run's own row, when the patient has none, is the one newer than every id here.
  const cppMaxId = sql.value(
    `SELECT COALESCE(MAX(id), 0) FROM casemgmt_cpp WHERE demographic_no = ${demographicNo}`,
  );
  const echartLast = sql.rows(
    `SELECT eChartId FROM eChart WHERE demographicNo = ${demographicNo} ORDER BY eChartId DESC LIMIT 1`,
  );
  return {
    cppExisted: cpp.length > 0,
    /** The row getCPP() would return, which is the row the save updates. */
    cppId: cpp.length ? String(cpp[0][0]) : '',
    cppMaxId: String(cppMaxId || '0'),
    /** SQL literal that restores the provider exactly, NULL and the string 'NULL' included. */
    cppProviderLiteral: cpp.length
      ? (cpp[0][2] === '1' ? 'NULL' : sqlString(cpp[0][1] === null ? 'NULL' : cpp[0][1]))
      : 'NULL',
    /** The same value for comparison, or null when the column was SQL NULL. */
    cppProviderNo: cpp.length && cpp[0][2] !== '1' ? (cpp[0][1] === null ? 'NULL' : cpp[0][1]) : null,
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

function rollbackTriggerName(name) {
  assert(/^pw_cpp_ext_[a-f0-9]{24}$/.test(name), 'the rollback trigger needs a unique owned SQL identifier');
  return name;
}

/** Reject only the submitted second date, after the first date reached this transaction. */
function rollbackTriggerSql(name, patient, stamp) {
  rollbackTriggerName(name);
  assert(/^[1-9]\d*$/.test(String(patient)), 'the rollback trigger needs an owned patient key');
  assert(/^PW_CPP_EXT_\d+$/.test(stamp), 'the rollback trigger needs this check\'s note stamp');
  return `DELIMITER //
CREATE TRIGGER ${name} BEFORE UPDATE ON casemgmt_note_ext FOR EACH ROW
BEGIN
  IF NEW.key_val='Resolution Date' AND NEW.date_value=${sqlString(EDIT_RESOLUTION_DATE)}
    AND EXISTS (SELECT 1 FROM casemgmt_note n WHERE n.note_id=NEW.note_id
      AND n.demographic_no=${patient} AND LOCATE(${sqlString(stamp)}, n.note)=1)
    AND EXISTS (SELECT 1 FROM casemgmt_note_ext e WHERE e.note_id=NEW.note_id
      AND e.key_val='Start Date' AND e.date_value=${sqlString(EDIT_START_DATE)}) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Owned CPP late extension rollback probe';
  END IF;
END//
DELIMITER ;`;
}

function dropRollbackTrigger() {
  if (!fixture.rollbackTrigger) return;
  const name = rollbackTriggerName(fixture.rollbackTrigger);
  fixture.sql.execute(`DROP TRIGGER IF EXISTS ${name}`);
  assert(fixture.sql.value(`SELECT COUNT(*) FROM information_schema.TRIGGERS
    WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=${sqlString(name)}`) === '0',
  'the owned rollback trigger survived cleanup');
  fixture.rollbackTrigger = '';
}

/** Full owned rows include revision metadata, positions, signatures, and both CPP projections. */
function readCppWriteSnapshot(sql, patient) {
  const demographic = sqlNumber(patient, 'the owned snapshot patient');
  const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${demographic}`;
  const queries = {
    notes: `SELECT * FROM casemgmt_note WHERE demographic_no=${demographic} ORDER BY note_id`,
    extensions: `SELECT * FROM casemgmt_note_ext WHERE note_id IN (${notes}) ORDER BY id`,
    issueLinks: `SELECT * FROM casemgmt_issue_notes WHERE note_id IN (${notes}) ORDER BY note_id,id`,
    noteLinks: `SELECT * FROM casemgmt_note_link WHERE note_id IN (${notes}) ORDER BY id`,
    issues: `SELECT * FROM casemgmt_issue WHERE demographic_no=${demographic} ORDER BY id`,
    cpp: `SELECT * FROM casemgmt_cpp WHERE demographic_no=${demographic} ORDER BY id`,
    echart: `SELECT * FROM eChart WHERE demographicNo=${demographic} ORDER BY eChartId`,
    signatures: `SELECT * FROM hash_audit WHERE type='enc' AND id IN (${notes}) ORDER BY pkid`,
  };
  return Object.fromEntries(Object.entries(queries).map(([name, query]) => [name, sql.rows(query)]));
}

function readNoteHistory(sql, noteIds) {
  const ids = noteIds.map(id => sqlNumber(id, 'a historical CPP revision')).join(',');
  return {
    notes: sql.rows(`SELECT * FROM casemgmt_note WHERE note_id IN (${ids}) ORDER BY note_id`),
    extensions: sql.rows(`SELECT * FROM casemgmt_note_ext WHERE note_id IN (${ids}) ORDER BY id`),
    issueLinks: sql.rows(`SELECT * FROM casemgmt_issue_notes WHERE note_id IN (${ids}) ORDER BY note_id,id`),
    noteLinks: sql.rows(`SELECT * FROM casemgmt_note_link WHERE note_id IN (${ids}) ORDER BY id`),
  };
}

function assertSameSnapshot(before, after, context) {
  for (const name of Object.keys(before)) {
    assert(JSON.stringify(before[name]) === JSON.stringify(after[name]), `${context} changed ${name}`);
  }
}

async function step(name, body) {
  try {
    await body();
    console.log(`  PASS ${name}`);
  } catch (error) {
    error.failedStep = name;
    throw error;
  }
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
  try {
    dropRollbackTrigger();
  } catch (error) {
    failuresBeforeStatements.push(`the owned rollback trigger: ${error.message}`);
  }
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
    // Rows this run CREATED, and only those it recorded right after its own saves, still unchanged.
    // A stamped row outside that set -- a concurrent save's, or one of ours written to since -- is
    // left in place and reported below: it can hold a clinician's note alongside the copied stamp.
    if (providerNo) {
      statements.push(...ownedEchartDeletes(demographicNo, providerNo, fixture.echartOwned));
    }

    // THE ROW THE RUN WROTE INTO, WHICHEVER IT IS. When the patient already had a summary row the
    // save appended to that one, and the snapshot named it. When the patient had none, saveCPP()
    // created one during the run, so it has no snapshot id and has to be found here -- newer than
    // anything the patient had, and carrying the stamp. Missing this second case leaves the row
    // AND its stamped clinical text in the chart: the delete below only fires once the summary
    // columns are empty, which is precisely what stripping them achieves.
    const cppRowId = chartBefore.cppId || (() => {
      try {
        return sql.value(
          `SELECT id FROM casemgmt_cpp WHERE demographic_no = ${demographicNo} `
          + `AND id > ${chartBefore.cppMaxId} `
          + `AND (${CHART_TEXT_COLUMNS.map((column) => holds(column, stamp)).join(' OR ')}) `
          + 'ORDER BY id DESC LIMIT 1',
        );
      } catch (error) {
        failuresBeforeStatements.push(
          `the CPP summary row this run created could not be found (${(error && error.message) || 'query failed'})`);
        return '';
      }
    })();
    if (cppRowId) {
      stripFrom.push(['casemgmt_cpp', `id = ${cppRowId}`, CHART_TEXT_COLUMNS]);
    }
    if (chartBefore.echartLastId) {
      stripFrom.push(['eChart', `eChartId = ${chartBefore.echartLastId}`, ECHART_TEXT_COLUMNS]);
    }

    if (!chartBefore.cppExisted) {
      // The patient had no CPP row at all, so saveCPP() made one. Removed only once it is empty
      // again -- if a concurrent save has put real text in it, the row stays.
      // Bounded by id as well as by emptiness: a pre-existing row that merely happens to be blank
      // is not this run's to remove.
      const emptied = CHART_TEXT_COLUMNS.map((column) => `COALESCE(${column}, '') = ''`).join(' AND ');
      statements.push(['the CPP summary row this run created',
        `DELETE FROM casemgmt_cpp WHERE demographic_no = ${demographicNo} `
        + `AND id > ${chartBefore.cppMaxId} AND ${emptied}`]);
    } else if (providerNo && chartBefore.cppProviderNo !== providerNo) {
      // saveCPP() also stamps the row with whoever saved. Put the previous provider back, but only
      // while the row still records this run's -- a save since then is not ours to rewind.
      statements.push(["the CPP summary's provider",
        `UPDATE casemgmt_cpp SET provider_no = ${chartBefore.cppProviderLiteral} `
        + `WHERE id = ${chartBefore.cppId} AND provider_no = ${sqlString(providerNo)}`]);
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
    // LOUD, NOT DESTRUCTIVE. Whatever stamped eChart row survived the guarded deletes was written
    // by something other than this run's recorded saves; removing it could take clinical text.
    if (stamp && demographicNo && chartBefore) {
      try {
        const echartStamped = ECHART_TEXT_COLUMNS.map((column) => holds(column, stamp)).join(' OR ');
        const left = sql.rows(
          `SELECT eChartId FROM eChart WHERE demographicNo = ${demographicNo} `
          + `AND eChartId > ${chartBefore.echartMaxId} AND (${echartStamped})`,
        ).map((row) => row[0]);
        if (left.length) {
          failures.push(`legacy eChart row(s) ${left.join(', ')} still carry this run's text but were changed or `
            + 'created by a save outside this run, so they were left in place; remove the stamped text by hand');
        }
      } catch (error) {
        failures.push(`the leftover eChart check: ${(error && error.message) || 'query failed'}`);
      }
    }
    // LAST: whatever else the chart wrote for the owned patient (its casemgmt_issue assignment, its CPP summary and eChart
    // rows), then the patient, all by its key, asserted gone.
    if (fixture.ownedPatient) {
      try {
        removeOwnedPatient(sql, fixture.ownedPatient, fixture.ownedMarker);
      } catch (error) {
        failures.push(`the owned patient: ${(error && error.message) || 'delete failed'}`);
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
function isNoteSave(response, expectedAction) {
  const url = new URL(response.url());
  if (!url.pathname.endsWith('/CaseManagementEntry') || url.searchParams.get('method') !== 'issueNoteSave'
    || response.request().method() !== 'POST') return false;
  if (!expectedAction) return true;
  const expected = new URL(expectedAction);
  return url.origin === expected.origin && url.pathname === expected.pathname
    && ['noteId', 'demographicNo', 'providerNo'].every(key => url.searchParams.get(key) === expected.searchParams.get(key));
}

function isCppTerminalResponse(response, postedRequest) {
  if (!postedRequest || response.request() === postedRequest
    || (response.status() >= 300 && response.status() < 400)) return false;
  let root = response.request();
  while (root.redirectedFrom()) root = root.redirectedFrom();
  return root === postedRequest;
}

function assertCppSaveRedirect(posted, terminal, action, reloadUrl) {
  assert(isNoteSave(posted, action) && posted.status() === 302,
    'the CPP save must return its configured HTTP 302 redirect');
  assert(isCppTerminalResponse(terminal, posted.request()) && terminal.request().method() === 'GET'
    && terminal.status() === 200, 'the CPP save redirect must finish with its own successful GET');
  const expected = new URL(reloadUrl);
  const actual = new URL(terminal.url());
  expected.searchParams.sort(); actual.searchParams.sort();
  assert(actual.href === expected.href, 'the CPP save redirected outside its expected CaseManagementView listing');
  // The AJAX listNotes include renders viewNotes.jsp without setting Content-Type.
  // The caller verifies the rendered note/reopen state, or the empty list after archive.
  const contentType = terminal.headers()['content-type'];
  assert(contentType === undefined || /^text\/html\b/i.test(contentType),
    'the CPP redirect declared a non-HTML listing response');
}

/** Bind both response listeners before clicking; a fast redirect must not escape the check. */
async function saveCppDialog(page, sql, timeout, action, titlePattern, what) {
  const reload = new URL('CaseManagementView', action);
  reload.search = await page.locator('#reloadUrl').inputValue();
  let postedRequest;
  const posted = page.waitForResponse(response => {
    if (!isNoteSave(response, action)) return false;
    postedRequest = response.request();
    return true;
  }, {timeout}).then(async response => {
    try { await response.finished(); }
    finally { recordOwnEchartRows(sql); }
    assert(response.status() === 302, `the CPP ${what} must return HTTP 302, received ${response.status()}`);
    return response;
  });
  const terminal = page.waitForResponse(response => isCppTerminalResponse(response, postedRequest), {timeout})
    .then(async response => { await response.finished(); return response; });
  const [postedResponse, terminalResponse] = await Promise.all([
    posted, terminal, clickDialogButton(page, titlePattern, what),
  ]);
  assertCppSaveRedirect(postedResponse, terminalResponse, action, reload.href);
  return postedResponse;
}

/** Did updateCPPNote()'s onFailure handler repaint the box with an HTTP status? */
async function boxErrorText(page) {
  const html = await page.locator(`#${BOX_ID}`).innerHTML().catch(() => '');
  const match = /Error:\s*(\d{3})/.exec(html);
  return match ? match[1] : '';
}

/** The extension rows the stamped item holds, including empty non-date values. */
function extensionRows(sql, stamp) {
  return sql.rows(
    `SELECT e.key_val, e.date_value, e.value FROM casemgmt_note_ext e `
    + `JOIN casemgmt_note n ON n.note_id = e.note_id WHERE n.note_id = (SELECT MAX(note_id) FROM casemgmt_note WHERE LOCATE(${sqlString(stamp)}, note) = 1) ORDER BY e.key_val`,
  );
}

function assertExtensionValues(rows, startDate, resolutionDate, treatment) {
  const byKey = new Map(rows.map(row => [row[0], row]));
  assert(byKey.size === rows.length, 'the CPP revision contains duplicate extension keys');
  assert(EXPECTED_KEYS.every(key => byKey.has(key)), 'the CPP revision lost a required extension row');
  assert(byKey.get('Start Date')[1] === startDate && byKey.get('Resolution Date')[1] === resolutionDate,
    'the CPP revision did not retain both expected dates');
  assert(byKey.get('Treatment')[2] === treatment,
    'the CPP revision did not retain the exact Treatment value, including an explicit empty value');
}

async function main() {
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const timeout = Number(process.env.CPP_EXT_TIMEOUT_MS || '45000');
  // The stamp leads the note text so the cleanup can anchor its LIKE.
  const stamp = `PW_CPP_EXT_${Date.now()}`;

  const sql = createSqlRunner(config.mysql);
  fixture.sql = sql;
  fixture.stamp = stamp;
  // The owned patient, recorded before it exists so cleanup can still find it: its marker is the search term.
  fixture.ownedMarker = newOwnedMarker();
  const searchTerm = fixture.ownedMarker;
  const provider = sql.value(`SELECT provider_no FROM security WHERE user_name=${sqlString(config.testUser)}`);
  assert(provider, 'the configured test login has no provider');
  const preferredDemographicNo = createOwnedPatient(sql, { marker: fixture.ownedMarker, provider });
  fixture.ownedPatient = preferredDemographicNo;

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
    assert(fixture.demographicNo === String(fixture.ownedPatient), 'the chart must belong to this run\'s owned patient');
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
      if (isNoteSave(response)) {
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
    await chartPage.locator('#treatment').fill(TREATMENT);
    // WAIT FOR THE SAVE ITSELF. An earlier version waited on a DOM predicate that every element
    // satisfies -- the box is already visible and `dataset` is never undefined -- so it returned at
    // once and a fixed sleep decided whether the database had been written yet. The POST is the
    // event that matters, so wait for it, then let the box finish repainting from its reply.
    const initialAction = await chartPage.locator('#frmIssueNotes').evaluate(form => form.action);
    await saveCppDialog(chartPage, sql, timeout, initialAction, /sign|save/i, 'create');
    await chartPage.waitForTimeout(1500);

    const savedError = await boxErrorText(chartPage);
    const failedSaves = saves.filter((save) => save.status >= 500);
    assert(failedSaves.length === 0,
      `saving a CPP item that carries a start date and a resolution date answered HTTP ${(failedSaves[0] || {}).status}; `
      + 'issueNoteSave is re-persisting one detached CaseManagementNoteExt across the keys (issue #3739)');
    assert(savedError === '',
      `the Medical History box repainted as "Error: ${savedError}" after the save, so the POST behind it failed`);

    const savedRows = extensionRows(sql, stamp);
    // The form also serializes blank non-date fields; they may now have explicit empty rows.
    assertExtensionValues(savedRows, START_DATE, RESOLUTION_DATE, TREATMENT);

    // --- reopen it: both dates must come BACK, not just be stored ---
    const noteId = sqlNumber(sql.value(
      `SELECT note_id FROM casemgmt_note WHERE LOCATE(${sqlString(stamp)}, note) = 1 ORDER BY note_id DESC LIMIT 1`),
      'the saved CPP item\'s note id');
    const reopen = box.locator(`a[onclick*="noteId=${noteId}"]`).first();
    assert(await reopen.count() > 0, 'the saved CPP item does not appear in the Medical History box, so it cannot be archived');
    await reopen.click();
    await chartPage.waitForSelector(DIALOG, { state: 'visible', timeout });
    const prefill = await chartPage.evaluate(() => ({
      start: document.getElementById('startdate').value,
      resolution: document.getElementById('resolutiondate').value,
      treatment: document.getElementById('treatment').value,
    }));
    assert(prefill.start === START_DATE && prefill.resolution === RESOLUTION_DATE && prefill.treatment === TREATMENT,
      'reopening the CPP item did not bring both dates and Treatment back');

    const originalHistory = readNoteHistory(sql, [noteId]);
    const editedText = `${stamp} CPP revision after rollback`;
    await chartPage.locator('#noteEditTxt').fill(editedText);
    await chartPage.locator('#startdate').fill(EDIT_START_DATE);
    await chartPage.locator('#resolutiondate').fill(EDIT_RESOLUTION_DATE);
    await chartPage.locator('#treatment').fill('');
    await step('late extension failure rolls back the complete CPP revision', async () => {
      assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${fixture.demographicNo}
        AND last_name=${sqlString(fixture.ownedMarker)}`) === '1', 'the rollback probe requires the owned fixture patient');
      const trigger = rollbackTriggerName(`pw_cpp_ext_${randomBytes(12).toString('hex')}`);
      assert(sql.value(`SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()
        AND TRIGGER_NAME=${sqlString(trigger)}`) === '0', 'the owned trigger name already exists');
      fixture.rollbackTrigger = trigger;
      try {
        sql.execute(rollbackTriggerSql(trigger, fixture.demographicNo, stamp));
        const before = readCppWriteSnapshot(sql, fixture.demographicNo);
        const badBefore = recorder.badResponses.length;
        const consoleBefore = recorder.consoleIssues.length;
        const expectedAlert = await chartPage.evaluate(() => savingNoteError + ' 500');
        const failedAction = await chartPage.locator('#frmIssueNotes').evaluate(form => form.action);
        let failed;
        const dialogs = await withExpectedDialogs(chartPage, async () => {
          [failed] = await Promise.all([
            chartPage.waitForResponse(response => isNoteSave(response, failedAction), {timeout}).then(async response => {
              try { await response.finished(); }
              finally { recordOwnEchartRows(sql); }
              assert(response.status() === 500, 'the late extension trigger must reject the actual CPP save with HTTP 500');
              return response;
            }),
            chartPage.waitForEvent('dialog', {timeout}),
            clickDialogButton(chartPage, /sign|save/i, 'save'),
          ]);
          await failed.finished();
          await chartPage.locator(DIALOG).waitFor({state: 'visible', timeout});
        });
        assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text === expectedAlert,
          'the failed CPP save must show exactly its save-error alert');
        const submitted = new URLSearchParams(failed.request().postData() || '');
        assert(new URL(failed.url()).searchParams.get('noteId') === noteId
          && submitted.get('value') === editedText && submitted.get('startdate') === EDIT_START_DATE
          && submitted.get('resolutiondate') === EDIT_RESOLUTION_DATE && submitted.get('treatment') === '',
        'the failed save must submit the original revision with the edited note, both new dates and empty Treatment');
        assertSameSnapshot(before, readCppWriteSnapshot(sql, fixture.demographicNo), 'the rolled-back CPP save');
        assert(await chartPage.locator('#noteEditTxt').inputValue() === editedText
          && await chartPage.locator('#startdate').inputValue() === EDIT_START_DATE
          && await chartPage.locator('#resolutiondate').inputValue() === EDIT_RESOLUTION_DATE
          && await chartPage.locator('#treatment').inputValue() === '',
        'the failed CPP save lost the dialog draft');
        // Exempt exactly this deliberately rejected request, retaining all unrelated errors.
        const bad = recorder.badResponses.slice(badBefore);
        assert(bad.length === 1 && bad[0].url === failed.url() && bad[0].method === 'POST' && bad[0].status === 500,
          'the rollback probe produced an unexpected HTTP failure');
        recorder.badResponses.splice(badBefore, 1);
        for (let index = recorder.consoleIssues.length - 1; index >= consoleBefore; index--) {
          const entry = recorder.consoleIssues[index];
          if (entry.label === 'echart' && entry.location.url === failed.url()
            && /^Failed to load resource:.*\b500\b/.test(entry.text)) recorder.consoleIssues.splice(index, 1);
        }
      } finally {
        dropRollbackTrigger();
      }
    });

    let editedId;
    let editedRows;
    await step('retry the original CPP revision through Sign & Save after rollback', async () => {
      const action = await chartPage.locator('#frmIssueNotes').evaluate(form => form.action);
      assert(new URL(action).searchParams.get('noteId') === noteId,
        'the failed save advanced the dialog revision before retry');
      const retried = await saveCppDialog(chartPage, sql, timeout, action, /sign|save/i, 'retry');
      assert(new URL(retried.url()).searchParams.get('noteId') === noteId,
        'retrying must submit the original revision ID on the wire');
      assert(new URLSearchParams(retried.request().postData() || '').get('treatment') === '',
        'retrying must submit Treatment as empty rather than omit the cleared field');
      editedId = sqlNumber(sql.value(`SELECT MAX(note_id) FROM casemgmt_note
        WHERE demographic_no=${fixture.demographicNo} AND LOCATE(${sqlString(stamp)}, note)=1`), 'the retried CPP revision');
      assert(BigInt(editedId) > BigInt(noteId), 'the retry must create a fresh CPP revision');
      assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${fixture.demographicNo}
        AND LOCATE(${sqlString(stamp)}, note)=1`) === '2', 'the failed attempt left an extra CPP revision');
      assert(sql.value(`SELECT note FROM casemgmt_note WHERE note_id=${editedId}`) === editedText,
        'the retry did not persist the edited note');
      editedRows = extensionRows(sql, stamp);
      assertExtensionValues(editedRows, EDIT_START_DATE, EDIT_RESOLUTION_DATE, '');
      assertSameSnapshot(originalHistory, readNoteHistory(sql, [noteId]), 'the successful retry');
      const editedLink = box.locator(`a[onclick*="noteId=${editedId}"]`).first();
      await editedLink.waitFor({state: 'visible', timeout});
      await editedLink.click();
      await chartPage.locator(DIALOG).waitFor({state: 'visible', timeout});
      assert(await chartPage.locator('#startdate').inputValue() === EDIT_START_DATE
        && await chartPage.locator('#resolutiondate').inputValue() === EDIT_RESOLUTION_DATE
        && await chartPage.locator('#treatment').inputValue() === '',
      'reopening the retried revision lost a new date or restored the cleared Treatment');
    });
    const historyBeforeArchive = readNoteHistory(sql, [noteId, editedId]);

    // --- archive it: the same write path, over rows that already exist ---
    const savesBeforeArchive = saves.length;
    const archiveAction = await chartPage.locator('#frmIssueNotes').evaluate(form => form.action);
    assert(new URL(archiveAction).searchParams.get('noteId') === editedId, 'archive must submit the retried revision');
    await saveCppDialog(chartPage, sql, timeout, archiveAction, /archive/i, 'archive');
    await chartPage.waitForTimeout(1500);
    const archiveSaves = saves.slice(savesBeforeArchive);
    assert(archiveSaves.length > 0, 'clicking Archive posted nothing, so the archive path was never exercised');
    const failedArchives = archiveSaves.filter((save) => save.status >= 500);
    assert(failedArchives.length === 0,
      `archiving the CPP item answered HTTP ${(failedArchives[0] || {}).status} (issue #3739)`);
    const archiveError = await boxErrorText(chartPage);
    assert(archiveError === '',
      `the Medical History box repainted as "Error: ${archiveError}" after the archive`);
    await box.locator('.topBox-notes .cpp-empty-state').waitFor({state: 'visible', timeout});
    assert(!(await box.textContent()).includes(stamp), 'the archived CPP item remains in the active listing');

    const archivedRows = extensionRows(sql, stamp);
    assertExtensionValues(archivedRows, EDIT_START_DATE, EDIT_RESOLUTION_DATE, '');
    const latestId = sqlNumber(sql.value(
      `SELECT MAX(note_id) FROM casemgmt_note WHERE LOCATE(${sqlString(stamp)}, note) = 1`), 'archived revision id');
    assert(BigInt(latestId) > BigInt(editedId), 'archiving overwrote the prior CPP revision');
    assert(sql.value(`SELECT archived FROM casemgmt_note WHERE note_id = ${noteId}`) === '0',
      'archiving changed the historical CPP revision');
    const originalRows = sql.rows(`SELECT key_val,date_value,value FROM casemgmt_note_ext WHERE note_id=${noteId} ORDER BY key_val`);
    assertExtensionValues(originalRows, START_DATE, RESOLUTION_DATE, TREATMENT);
    assert(JSON.stringify(originalRows) === JSON.stringify(savedRows), 'archiving changed historical CPP extensions');
    assert(JSON.stringify(archivedRows) === JSON.stringify(editedRows), 'archiving lost the edited CPP extension values');
    assertSameSnapshot(historyBeforeArchive, readNoteHistory(sql, [noteId, editedId]), 'archiving the CPP revision');
    assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${fixture.demographicNo}
      AND LOCATE(${sqlString(stamp)}, note)=1`) === '3', 'the CPP history must contain create, retry, and archive revisions only');
    const archived = sql.value(`SELECT archived FROM casemgmt_note WHERE note_id = ${latestId}`);
    assert(archived === '1', 'the archive request succeeded but the CPP item is not marked archived');

    assertStrictPage(recorder, ['echart']);
    console.log('  PASS CPP create/reopen, late-write rollback/retry, and archive preserve three revisions with both dates');
    console.log('  PASS cleared Treatment stays empty in the latest revisions while its original value remains immutable');
    await chartPage.close().catch(() => {});
    return { extensionRows: archivedRows.length, revisions: 3, rollbackStatus: 500 };
  } finally {
    try { dropRollbackTrigger(); }
    finally { if (browser) await browser.close().catch(() => {}); }
  }
}

if (require.main === module) {
  runCheck({ name: 'cpp-note-extension-archive', run: main, cleanup });
}

module.exports = {
  BOX_ID, DIALOG, EXPECTED_KEYS, RESOLUTION_DATE, START_DATE, cleanup, fixture, main,
  ownedEchartDeletes, withoutStampedBlocks, rollbackTriggerSql, dropRollbackTrigger, assertSameSnapshot, isNoteSave,
  isCppTerminalResponse, assertCppSaveRedirect, saveCppDialog, assertExtensionValues,
};
