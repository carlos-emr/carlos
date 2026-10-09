/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * Snapshot, byte-exact restore and polling guard for the clinic-wide fax_config table.
 *
 * WHY. Administration > Faxes > Configure Fax > Save rewrites fax_config, and a saved active
 * account starts the fax scheduler (ConfigureFax2Action). fax-configure-playwright-checks.js
 * walks that save with a fake SRFax account, gateway enabled and inbound polling on, and used
 * to leave the row that way. FaxImporter then polled the unreachable account every minute and
 * logged an ERROR each time (findings-log row 180: 189 of them in three hours) until the next
 * restart, so a check that passed poisoned the log of every later one.
 *
 * WHAT THIS GIVES THE CHECK.
 *   snapshotFaxConfig  every row, every column, as encoded cells;
 *   restoreFaxConfig   put exactly those rows back (or delete the ones the save created) in one
 *                      transaction, then read the table back and fail if it is not identical;
 *   createFaxConfigGuard  the lifecycle: begin() before anything saves, finish() when the flow
 *                      ends however it ends, and the same restore when the process is told to stop.
 *
 * CELLS. `mysql -B` prints SQL NULL and the text 'NULL' alike, and a value with a tab or a
 * backslash is escaped, so a round trip through its text output is not exact. A cell is encoded
 * in SQL instead: `N` is NULL, `D<number>` a numeric column's value, `X<hex>` a text column's
 * bytes. Nothing here is ever printed: fax_config holds an account number, a sender address and
 * (encrypted) passwords.
 */

const POLLING_COUNT_SQL = 'SELECT COUNT(*) FROM `fax_config` WHERE `active`=1 AND `download`=1';
const NUMERIC_TYPES = new Set(['tinyint', 'smallint', 'mediumint', 'int', 'bigint', 'decimal']);
const CELL = /^(?:N|D-?\d+(?:\.\d+)?|X(?:[0-9A-F]{2})*)$/;
const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_]*$/;

function quote(identifier) {
  if (!IDENTIFIER.test(identifier)) throw new Error(`not a plain SQL identifier: ${identifier}`);
  return `\`${identifier}\``;
}

/**
 * Read fax_config as encoded cells.
 *
 * @param {object} sql the harness mysql client
 * @returns {{ columns: {name: string, numeric: boolean}[], rows: string[][] }}
 */
function snapshotFaxConfig(sql) {
  const columns = sql.rows('SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS '
    + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='fax_config' ORDER BY ORDINAL_POSITION")
    .map(([name, type]) => ({ name, numeric: NUMERIC_TYPES.has(String(type).toLowerCase()) }));
  if (!columns.length) throw new Error('fax_config does not exist in this database');
  const cells = columns.map(({ name, numeric }) => (numeric
    ? `IF(${quote(name)} IS NULL,'N',CONCAT('D',CAST(${quote(name)} AS CHAR)))`
    : `IF(${quote(name)} IS NULL,'N',CONCAT('X',HEX(${quote(name)})))`));
  const rows = sql.rows(`SELECT ${cells.join(',')} FROM \`fax_config\` ORDER BY ${quote(columns[0].name)}`);
  return { columns, rows: rows.map((row) => row.map(String)) };
}

function literalFor(cell) {
  if (typeof cell !== 'string' || !CELL.test(cell)) throw new Error('malformed snapshot cell; refusing to build SQL from it');
  if (cell === 'N') return 'NULL';
  return cell[0] === 'D' ? cell.slice(1) : `UNHEX('${cell.slice(1)}')`;
}

/**
 * Put fax_config back as `snapshot` found it, then prove it: the table is read again and must
 * encode identically. A row the save created is deleted; a row it rewrote is rewritten back. The
 * delete and the inserts run in one transaction, so a failing insert cannot leave the table empty.
 *
 * @throws if a cell is not one of the encodings above, or the table does not read back identical.
 */
function restoreFaxConfig(sql, snapshot) {
  // Nothing to put back: leave an unchanged table alone, so a check that never saved does not
  // rewrite the clinic's fax account (and FaxImporter never sees the table empty mid-restore).
  if (JSON.stringify(snapshotFaxConfig(sql).rows) === JSON.stringify(snapshot.rows)) return;
  const names = snapshot.columns.map(({ name }) => quote(name)).join(',');
  const inserts = snapshot.rows.map((row) => {
    if (row.length !== snapshot.columns.length) throw new Error('malformed snapshot cell; refusing to build SQL from it');
    return `INSERT INTO \`fax_config\` (${names}) VALUES (${row.map(literalFor).join(',')});`;
  });
  sql.execute(['START TRANSACTION;', 'DELETE FROM `fax_config`;', ...inserts, 'COMMIT;'].join('\n'));
  const after = snapshotFaxConfig(sql);
  if (JSON.stringify(after.rows) !== JSON.stringify(snapshot.rows)) {
    throw new Error(`fax_config was not restored to its snapshot (${snapshot.rows.length} row(s) expected, ${after.rows.length} found)`);
  }
}

/** Rows whose gateway is enabled AND inbound polling is on: the ones FaxImporter polls each minute. */
function pollingRowCount(sql) {
  const count = Number(sql.value(POLLING_COUNT_SQL));
  if (!Number.isInteger(count) || count < 0) throw new Error('the fax_config polling count was not a number');
  return count;
}

function decodeCell(cell) {
  if (cell === 'N') return null;
  if (cell[0] === 'D') return cell.slice(1);
  return Buffer.from(cell.slice(1), 'hex').toString('utf8');
}

/**
 * A snapshot as objects of decoded values, for the guard's own decisions only (never to be
 * printed: the values are account details).
 */
function decodeFaxRows(snapshot) {
  return snapshot.rows.map((row) => Object.fromEntries(snapshot.columns.map(({ name }, index) => [name, decodeCell(row[index])])));
}

function pollsIn(rows) {
  return rows.filter((row) => row.active === '1' && row.download === '1');
}

/**
 * The lifecycle of one check's use of fax_config.
 *
 * @param {object} options
 * @param {object} options.sql the harness mysql client
 * @param {{faxUser: string, accountName: string}} options.own the fake account the check saves;
 *   a table already polling it is an earlier run's leftover (finding 180) and begin() refuses it
 * @param {NodeJS.EventEmitter} [options.proc] where signals arrive (default: the process)
 * @param {function(number): void} [options.exit] how the process ends after a signal
 * @param {function(): void} [options.release] frees what the caller holds for `sql` (the harness
 *   client's option file carries the database password); run once, by release() or after a signal
 */
function createFaxConfigGuard({ sql, own, proc = process, exit = process.exit, release = () => {} }) {
  let snapshot = null;
  let pollingBefore = 0;
  let finished = false;
  let released = false;
  const handlers = new Map();

  const guard = {
    /**
     * Snapshot the table. Call it before anything can save.
     *
     * @throws if the fake account is already polling: restoring that exactly would only keep
     *   FaxImporter erroring every minute, so the leftover must be disabled by hand first.
     */
    begin() {
      snapshot = snapshotFaxConfig(sql);
      const decoded = decodeFaxRows(snapshot);
      pollingBefore = pollsIn(decoded).length;
      const leftover = pollsIn(decoded).find((row) => row.faxUser === own.faxUser && row.accountName === own.accountName);
      if (leftover) {
        throw new Error('fax_config already polls the fake account an earlier fax-configure run left behind '
          + `(row id ${leftover.id}; findings-log row 180). Disable it first: UPDATE fax_config SET active=0 WHERE id=${leftover.id}`);
      }
      finished = false;
      return snapshot;
    },

    /**
     * Restore the snapshot and assert the database agrees polling is as it was: off, unless a
     * real account was already polling before the run. Safe to call more than once.
     */
    finish() {
      if (!snapshot || finished) return;
      restoreFaxConfig(sql, snapshot);
      const polling = pollingRowCount(sql);
      if (polling !== pollingBefore) {
        throw new Error(polling > pollingBefore
          ? `polling is on after the restore (${polling} fax_config row(s) polling, ${pollingBefore} before the run)`
          : `polling changed across the restore (${polling} fax_config row(s) polling, ${pollingBefore} before the run)`);
      }
      finished = true;
    },

    /** On SIGTERM (the runner's timeout) or SIGINT, restore the row, then exit as the harness does. */
    armSignals() {
      for (const [signal, code] of [['SIGINT', 130], ['SIGTERM', 143]]) {
        const handler = () => {
          try {
            guard.finish();
          } catch (error) {
            console.error(`fax_config could not be restored after ${signal}: ${error.message}`);
          } finally {
            guard.release();
            exit(code);
          }
        };
        handlers.set(signal, handler);
        proc.once(signal, handler);
      }
    },

    /** Free the database client. Safe to call more than once; call it after finish(). */
    release() {
      if (released) return;
      released = true;
      release();
    },

    disarmSignals() {
      for (const [signal, handler] of handlers) proc.removeListener(signal, handler);
      handlers.clear();
    },
  };
  return guard;
}

module.exports = {
  createFaxConfigGuard, decodeFaxRows, pollingRowCount, restoreFaxConfig, snapshotFaxConfig,
};
