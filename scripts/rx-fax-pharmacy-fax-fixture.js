/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * The pharmacy fax destination shared by the Rx fax browser checks
 * (rx-fax-signature-stamp, rx-fax-reprint-represcribe, rx-fax-record-binding).
 *
 * WHY EVERY PHARMACY IS REWRITTEN. The Rx page renders, and the Fax button queues a job to,
 * whatever number the patient's pharmacy carries. The demo dataset's active pharmacies carry a
 * real-looking Toronto number, so a fixture prescription must never be able to leave for that
 * line: for the length of a run, every pharmacy linked to the check's patient holds this run's
 * NPA-555 number, which the NANP never assigns. A pharmacy that already has a fax is rewritten
 * too -- leaving it in place is the case issue #3607 exists to close.
 *
 * WHICH PHARMACIES. Exactly the set the Rx page lists: RxPharmacyData.getPharmacyFromDemographic
 * reads every pharmacy behind an ACTIVE demographicPharmacy link and does not filter on the
 * pharmacy's own status, so a pharmacy marked deleted but still linked is offered with its fax
 * and is covered here too. An inactive link is not listed and is left alone.
 *
 * Fidelity rules, because this mutates shared records:
 *   - each original value is snapshotted exactly: NULL and '' are distinct, and the value is read
 *     as hex so neither the client's batch escaping nor a caller's trim() can alter it. A value
 *     outside RESTORABLE_FAX is refused before ANY row is written, because it is restored later as
 *     a quoted literal;
 *   - the rewrite is compare-and-swap against that snapshot, and the restore is compare-and-swap
 *     against this run's number, so an operator edit made during the run is never overwritten.
 *     Every comparison is BINARY: the column's utf8mb4_general_ci collation ignores case and
 *     trailing spaces, so a plain '=' would treat an edit like '5551234567 ' as untouched;
 *   - pharmacyInfo.addDate is `ON UPDATE current_timestamp()`, so every statement here sets it to
 *     itself (KEEP_ADD_DATE); otherwise each run would stamp the pharmacy as added today.
 *
 * CRASH RECOVERY. SIGKILL or a container restart skips every finally and signal handler, which
 * is how runs left their 555 number behind (#3607). So before the first write the snapshot is
 * journalled to a private directory, and the next run on the same database restores any journal
 * an interrupted run left -- under a database advisory lock that all three checks share, which is
 * what makes that safe: while the lock is held no other run can be using its number. The journal
 * holds pharmacy record ids and fax numbers only, never a patient identifier. Journals are keyed by
 * the server's @@hostname, @@port and DATABASE(); a journal that restores nothing on replay -- one
 * written against another database with the same key, or one whose rows were already repaired by
 * hand -- is kept rather than deleted, because deleting it could discard the only copy of another
 * database's originals.
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

// NPA 555 is not assignable in the NANP, so 555-xxx-xxxx can never reach a real fax machine.
const STAGED_FAX = /^555[0-9]{7}$/;
// What an original value may look like for this fixture to rewrite and later restore as a quoted
// SQL literal: no quote, backslash or non-ASCII byte can appear, so no escaping is ever needed.
const RESTORABLE_FAX = /^[0-9A-Za-z .()+-]{0,32}$/;
const RECORD_ID = /^[1-9][0-9]{0,9}$/;
const LOCK_NAME = 'rx-fax-pharmacy-fixture';
const JOURNAL_FORMAT = 1;
// pharmacyInfo.addDate is `timestamp ... ON UPDATE current_timestamp()`; assigning it to itself
// suppresses that, so neither the rewrite nor the restore changes when the pharmacy was added.
const KEEP_ADD_DATE = 'addDate = addDate';

/*
 * The checks print only a standard error class (browser-error-class.js), never a message, so a
 * lock conflict would read as a bare "Error". These fixed codes are this module's own strings --
 * no page or database text -- so fixtureErrorTag() may print them, and the runbook says what each
 * one means.
 */
const FIXTURE_ERROR_CODE = /^RX_FAX_FIXTURE_[A-Z_]+$/;

function fixtureError(code, message) {
  const error = new Error(message);
  error.code = `RX_FAX_FIXTURE_${code}`;
  return error;
}

/** ' (RX_FAX_FIXTURE_...)' for an error this module raised, '' for anything else. */
function fixtureErrorTag(error) {
  const code = error && error.code;
  return typeof code === 'string' && FIXTURE_ERROR_CODE.test(code) ? ` (${code})` : '';
}

/** The directory crash journals live in; RX_FAX_JOURNAL_DIR overrides it. */
function defaultJournalDir(env = process.env) {
  return env.RX_FAX_JOURNAL_DIR || path.join(os.homedir(), '.cache', 'carlos-playwright', 'rx-fax-pharmacy');
}

/** Create (or accept) a journal directory only if no other account could write into it. */
function ensurePrivateDir(dir) {
  fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  const stat = fs.lstatSync(dir);
  const foreignOwner = typeof process.getuid === 'function' && stat.uid !== process.getuid();
  if (!stat.isDirectory() || (stat.mode & 0o022) || foreignOwner) {
    throw fixtureError('JOURNAL_DIR', 'the Rx fax journal directory must be a private directory owned by this user; set RX_FAX_JOURNAL_DIR to one');
  }
  return dir;
}

function sqlLiteral(row) {
  return row.wasNull ? 'NULL' : `'${row.originalFax}'`;
}

/** Validate one snapshot entry from the database or a journal; throws without echoing it. */
function checkedEntry(entry) {
  const recordId = String(entry && entry.recordId);
  const wasNull = entry && entry.wasNull === true;
  const originalFax = wasNull ? null : entry && entry.originalFax;
  if (!RECORD_ID.test(recordId) || (!wasNull && (typeof originalFax !== 'string' || !RESTORABLE_FAX.test(originalFax)))) {
    throw fixtureError('VALUE', 'a pharmacy fax value has an unexpected shape; refusing to rewrite it');
  }
  return { recordId, wasNull, originalFax };
}

function parseJournal(text) {
  let journal;
  try {
    journal = JSON.parse(text);
  } catch (error) {
    throw fixtureError('JOURNAL', 'an Rx fax pharmacy journal is not valid JSON; inspect and remove it by hand');
  }
  if (!journal || journal.format !== JOURNAL_FORMAT || !STAGED_FAX.test(String(journal.stagedFax))
      || !Array.isArray(journal.entries)) {
    throw fixtureError('JOURNAL', 'an Rx fax pharmacy journal has an unexpected shape; inspect and remove it by hand');
  }
  return { stagedFax: journal.stagedFax, entries: journal.entries.map(checkedEntry) };
}

/**
 * Own the pharmacy fax destination for one check run.
 *
 * @param {object} options
 * @param {(query: string) => string} options.sql runs one statement batch through the mysql client
 *   (-N -B) and returns its output; it must not echo the query or client output on failure.
 * @param {string} options.demographicNo the check's patient; must be numeric.
 * @param {string} options.stagedFax this run's unique 555 number.
 * @param {object} options.mysql {host, user, password, database} for the advisory-lock connection.
 * @param {string} [options.journalDir] overrides defaultJournalDir().
 * @param {Function} [options.acquireLock] (config, name) => Promise<release>; injectable for tests.
 */
function createPharmacyFaxFixture({ sql, demographicNo, stagedFax, mysql, journalDir, acquireLock }) {
  if (!/^[0-9]+$/.test(String(demographicNo))) throw new Error('demographicNo must be numeric');
  if (!STAGED_FAX.test(String(stagedFax))) throw new Error('the staged pharmacy fax must be an NPA-555 number');
  const dir = journalDir || defaultJournalDir();
  const lockFn = acquireLock || require('./lib/mysql-workflow-lock').acquireMysqlWorkflowLock;
  const seeded = [];
  let releaseLock = null;
  let journalFile = null;
  let identity = null;

  // The journal is keyed by the server's own identity rather than MYSQL_HOST, so 'localhost' and
  // '127.0.0.1' (or a socket) find the same journals.
  function databaseKey() {
    if (!identity) {
      const id = sql('SELECT @@hostname, @@port, DATABASE();').trim();
      if (!id) throw fixtureError('DATABASE', 'could not identify the fixture database');
      identity = crypto.createHash('sha256').update(id).digest('hex').slice(0, 16);
    }
    return identity;
  }

  function changedOne(statement) {
    return sql(`${statement}; SELECT ROW_COUNT();`).trim() === '1';
  }

  /**
   * Restore one journal left by an interrupted run. The file is kept if any statement failed (the
   * next run retries it), and also if it restored nothing: then it was written against another
   * database with the same key, or repaired by hand, and deleting it could lose originals.
   */
  function recoverJournal(file, summary) {
    const journal = parseJournal(fs.readFileSync(file, 'utf8'));
    let failed = 0;
    let restored = 0;
    for (const entry of journal.entries) {
      try {
        if (changedOne(`UPDATE pharmacyInfo SET fax = ${sqlLiteral(entry)}, ${KEEP_ADD_DATE} WHERE recordId = ${entry.recordId} AND fax = BINARY '${journal.stagedFax}'`)) {
          restored += 1;
        } else {
          summary.untouched += 1;
        }
      } catch (error) {
        failed += 1;
      }
    }
    summary.restored += restored;
    if (failed) throw fixtureError('RECOVERY', 'could not restore a pharmacy fax left by an interrupted Rx fax run; see docs/ui-tests/deb-install-validation.md');
    if (!restored && journal.entries.length) {
      summary.kept += 1;
      return;
    }
    fs.unlinkSync(file);
    summary.journals += 1;
  }

  /**
   * Take the advisory lock the three Rx fax checks share, then restore anything an interrupted
   * earlier run on this database left behind. Must precede seed().
   */
  async function lock() {
    if (releaseLock) return { journals: 0, restored: 0, untouched: 0, kept: 0 };
    try {
      releaseLock = await lockFn(mysql, LOCK_NAME);
    } catch (error) {
      // The helper's own message names its first caller ("report workflow"); say what it means here.
      throw fixtureError('LOCKED', 'could not take the Rx fax pharmacy fixture lock: another Rx fax check is running against this database, or the mysql client could not connect');
    }
    const summary = { journals: 0, restored: 0, untouched: 0, kept: 0 };
    ensurePrivateDir(dir);
    const prefix = `${databaseKey()}-`;
    for (const name of fs.readdirSync(dir).sort()) {
      if (name.startsWith(prefix) && name.endsWith('.json')) recoverJournal(path.join(dir, name), summary);
    }
    return summary;
  }

  // The pharmacies the Rx page lists for the patient: every pharmacy behind an active link, whatever
  // its own status (see WHICH PHARMACIES above).
  function readLinkedPharmacies() {
    const output = sql(`SELECT p.recordId, IF(p.fax IS NULL, 1, 0), IFNULL(HEX(p.fax), '') FROM pharmacyInfo p
      JOIN demographicPharmacy dp ON dp.pharmacyID = p.recordId
      WHERE dp.demographic_no = ${demographicNo} AND dp.status = '1'
      ORDER BY p.recordId;`);
    const rows = [];
    const ids = new Set();
    for (const line of output.split('\n')) {
      if (!line.trim()) continue;
      const [rawId, rawNull, rawHex = ''] = line.split('\t');
      const hex = rawHex.trim();
      if (!/^(?:[0-9A-Fa-f]{2})*$/.test(hex)) throw fixtureError('VALUE', 'a pharmacy fax value has an unexpected shape; refusing to rewrite it');
      const wasNull = String(rawNull).trim() === '1';
      const entry = checkedEntry({
        recordId: String(rawId).trim(),
        wasNull,
        originalFax: wasNull ? null : Buffer.from(hex, 'hex').toString('utf8'),
      });
      // A pharmacy linked twice to the patient is still one record to rewrite and restore.
      if (!ids.has(entry.recordId)) {
        ids.add(entry.recordId);
        rows.push(entry);
      }
    }
    return rows;
  }

  function writeJournal(rows) {
    journalFile = path.join(ensurePrivateDir(dir), `${databaseKey()}-${stagedFax}.json`);
    const temp = `${journalFile}.tmp`;
    fs.rmSync(temp, { force: true });
    fs.writeFileSync(temp, JSON.stringify({ format: JOURNAL_FORMAT, stagedFax, entries: rows }), { mode: 0o600, flag: 'wx' });
    fs.renameSync(temp, journalFile);
  }

  /**
   * Point every pharmacy the Rx page lists for the patient at this run's number. Every value is
   * validated before the first write, and the snapshot is journalled before it.
   *
   * @return {{active: number, seeded: number}} active counts the listed pharmacies.
   */
  function seed() {
    if (!releaseLock) throw fixtureError('NOT_LOCKED', 'take the Rx fax pharmacy fixture lock before seeding');
    // A second seed over rows still staged would journal those 555 values as the "originals",
    // replacing the snapshot a crash would need. restore() must settle the first one.
    if (seeded.length || journalFile) throw fixtureError('SEEDED', 'restore the Rx fax pharmacy fixture before seeding it again');
    const rows = readLinkedPharmacies();
    if (rows.length) writeJournal(rows);
    let written = 0;
    for (const row of rows) {
      // Recorded BEFORE the statement: an UPDATE whose outcome is unknown (the client timed out, or
      // ROW_COUNT() failed after a commit) must still be restored. Restoring a row that was never
      // written is a no-op, because the restore only matches this run's number.
      seeded.push(row);
      const unchanged = row.wasNull ? 'fax IS NULL' : `fax = BINARY '${row.originalFax}'`;
      if (!changedOne(`UPDATE pharmacyInfo SET fax = '${stagedFax}', ${KEEP_ADD_DATE} WHERE recordId = ${row.recordId} AND ${unchanged}`)) {
        throw fixtureError('CHANGED', 'a pharmacy fax changed while the fixture was being staged; refusing to continue');
      }
      written += 1;
    }
    return { active: rows.length, seeded: written };
  }

  /**
   * Put back every value this run replaced, newest first, only where the column still holds this
   * run's number. Synchronous and safe to call again (a finally and a signal handler can both run
   * it). Attempts every row; a row whose statement failed stays queued for the next call, and the
   * journal is kept until every row has been settled, so neither a retry nor the next run can lose
   * an original.
   *
   * @return {{restored: number, untouched: number}} untouched counts rows that no longer hold this
   *   run's number: changed by someone else, or never written.
   */
  function restore() {
    const summary = { restored: 0, untouched: 0 };
    const failed = [];
    while (seeded.length) {
      const row = seeded.pop();
      try {
        if (changedOne(`UPDATE pharmacyInfo SET fax = ${sqlLiteral(row)}, ${KEEP_ADD_DATE} WHERE recordId = ${row.recordId} AND fax = BINARY '${stagedFax}'`)) {
          summary.restored += 1;
        } else {
          summary.untouched += 1;
        }
      } catch (error) {
        failed.unshift(row);
      }
    }
    if (failed.length) {
      seeded.push(...failed);
      throw fixtureError('RESTORE', `could not restore ${failed.length} pharmacy fax value(s); the next Rx fax run restores them from its journal`);
    }
    if (journalFile) {
      fs.rmSync(journalFile, { force: true });
      journalFile = null;
    }
    return summary;
  }

  /** Release the advisory lock. Call after restore(). */
  async function unlock() {
    const release = releaseLock;
    releaseLock = null;
    if (release) await release();
  }

  return {
    lock,
    seed,
    restore,
    unlock,
    get seededCount() { return seeded.length; },
  };
}

module.exports = {
  createPharmacyFaxFixture, defaultJournalDir, fixtureErrorTag, parseJournal, RESTORABLE_FAX, STAGED_FAX, LOCK_NAME,
};
