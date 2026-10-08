/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/*
 * scripts/lib/fax-config-state.js: the snapshot, byte-exact restore and polling guard behind
 * fax-configure-playwright-checks.js.
 *
 * Finding 180 came from that check: it saved a fake SRFax account with the gateway enabled and
 * inbound polling on, and never put the row back, so FaxImporter logged an ERROR every minute for
 * as long as the install stayed up. The Configure Fax save is a clinic-wide write, so the check
 * snapshots the table first, restores it however it ends, and proves polling is unchanged (off on the shipped row) afterwards.
 * The database is a stub here; the live proof is the runner's --residue-audit and the journal.
 */

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { EventEmitter } = require('node:events');

const {
  createFaxConfigGuard, decodeFaxRows, pollingRowCount, restoreFaxConfig, snapshotFaxConfig,
} = require('./lib/fax-config-state');

const hex = (text) => Buffer.from(text, 'utf8').toString('hex').toUpperCase();

/** fax_config as the install ships it: one disabled MIDDLEWARE row, polling flag on but gateway off. */
const COLUMNS = [['id', 'int'], ['url', 'varchar'], ['download', 'tinyint'], ['providerType', 'varchar'],
  ['faxUser', 'varchar'], ['faxPasswd', 'varchar'], ['active', 'tinyint'], ['accountName', 'varchar']];
const SEED_ROW = ['D1', 'X', 'D1', `X${hex('MIDDLEWARE')}`, 'X', 'X', 'D0', 'X'];

/**
 * A stub of the harness client holding fax_config as encoded cells. It answers the snapshot,
 * polling and column queries, and applies the restore statements the way MariaDB would, so a
 * restore can be proved by reading the table back.
 */
function fakeSql(initialRows = [SEED_ROW]) {
  const state = { rows: initialRows.map((row) => [...row]), statements: [], failRestore: false, pollingOverride: null };
  const sql = {
    state,
    rows(query) {
      if (/information_schema\.COLUMNS/.test(query)) return COLUMNS.map((column) => [...column]);
      if (/FROM `fax_config`/.test(query)) return state.rows.map((row) => [...row]);
      throw new Error(`unexpected rows() query: ${query}`);
    },
    value(query) {
      assert.match(query, /COUNT\(\*\) FROM `fax_config` WHERE `active`=1 AND `download`=1/);
      const active = COLUMNS.findIndex(([name]) => name === 'active');
      const download = COLUMNS.findIndex(([name]) => name === 'download');
      if (state.pollingOverride !== null) return String(state.pollingOverride);
      return String(state.rows.filter((row) => row[active] === 'D1' && row[download] === 'D1').length);
    },
    execute(query) {
      state.statements.push(query);
      if (state.failRestore) return;
      state.rows = [];
      for (const insert of query.matchAll(/INSERT INTO `fax_config` \([^)]*\) VALUES \((.*)\)/g)) {
        state.rows.push(insert[1].split(',').map((literal) => {
          if (literal === 'NULL') return 'N';
          const unhex = /^UNHEX\('([0-9A-F]*)'\)$/.exec(literal);
          return unhex ? `X${unhex[1]}` : `D${literal}`;
        }));
      }
    },
  };
  return sql;
}

const polling = (extra = {}) => {
  const row = [...SEED_ROW];
  row[0] = extra.id || 'D2';
  row[4] = `X${hex(extra.faxUser || '000000')}`;
  row[6] = 'D1';
  row[7] = `X${hex(extra.accountName || 'Playwright SRFax check')}`;
  return row;
};

test('shouldSnapshotEveryColumnAsAnEncodedCell_soNullAndTheWordNullStayApart', () => {
  const sql = fakeSql([[...SEED_ROW]]);
  const queries = [];
  const spy = { ...sql, rows(query) { queries.push(query); return sql.rows(query); } };
  const snapshot = snapshotFaxConfig(spy);
  assert.deepEqual(snapshot.rows, [SEED_ROW]);
  assert.deepEqual(snapshot.columns, COLUMNS.map(([name, type]) => ({ name, numeric: type === 'int' || type === 'tinyint' })));
  const select = queries.find((query) => query.includes('FROM `fax_config`'));
  assert.match(select, /IF\(`url` IS NULL,'N',CONCAT\('X',HEX\(`url`\)\)\)/, 'text is read as hex, with a NULL flag');
  assert.match(select, /IF\(`active` IS NULL,'N',CONCAT\('D',CAST\(`active` AS CHAR\)\)\)/, 'numbers are read as numbers');
});

test('shouldRestoreTheRowByteExact_whenTheSaveRewroteIt', () => {
  const sql = fakeSql();
  const snapshot = snapshotFaxConfig(sql);
  sql.state.rows = [polling({ id: 'D1' })];
  restoreFaxConfig(sql, snapshot);
  assert.deepEqual(sql.state.rows, [SEED_ROW]);
  const statement = sql.state.statements.join('\n');
  assert.match(statement, /^START TRANSACTION;/, 'a failed insert must not leave the table emptied');
  assert.match(statement, /DELETE FROM `fax_config`;/);
  assert.match(statement, /COMMIT;?\s*$/);
});

test('shouldRestoreANullCellAsNull_andTheTextNullAsText', () => {
  const sql = fakeSql([['D1', 'N', 'D1', `X${hex('NULL')}`, 'X', 'X', 'D0', 'X']]);
  const snapshot = snapshotFaxConfig(sql);
  sql.state.rows = [];
  restoreFaxConfig(sql, snapshot);
  assert.deepEqual(sql.state.rows, [['D1', 'N', 'D1', `X${hex('NULL')}`, 'X', 'X', 'D0', 'X']]);
});

test('shouldDeleteTheRowTheCheckCreated_whenNoRowExistedBefore', () => {
  const sql = fakeSql([]);
  const snapshot = snapshotFaxConfig(sql);
  assert.deepEqual(snapshot.rows, []);
  sql.state.rows = [polling()];
  restoreFaxConfig(sql, snapshot);
  assert.deepEqual(sql.state.rows, [], 'a row the save created is deleted, not left behind');
});

test('shouldFail_whenTheTableDoesNotReadBackAsItWas', () => {
  const sql = fakeSql();
  const snapshot = snapshotFaxConfig(sql);
  sql.state.rows = [polling({ id: 'D1' })];
  sql.state.failRestore = true;
  assert.throws(() => restoreFaxConfig(sql, snapshot), /fax_config was not restored/);
});

test('shouldRefuseToRunSql_whenASnapshotCellIsMalformed', () => {
  const sql = fakeSql();
  const snapshot = snapshotFaxConfig(sql);
  for (const cell of ["X'); DROP TABLE fax_config; --", 'Dabc', 'XZZ', 'X1', 'Y00', '']) {
    const forged = { ...snapshot, rows: [[cell, ...snapshot.rows[0].slice(1)]] };
    assert.throws(() => restoreFaxConfig(sql, forged), /malformed snapshot cell/, JSON.stringify(cell));
  }
  assert.equal(sql.state.statements.length, 0, 'nothing is executed for a forged snapshot');
});

test('shouldCountPollingRows_asActiveAndDownload', () => {
  assert.equal(pollingRowCount(fakeSql([SEED_ROW])), 0, 'the shipped row has the gateway off');
  assert.equal(pollingRowCount(fakeSql([polling()])), 1);
});

test('shouldDecodeRows_forTheGuardButNeverForReports', () => {
  const [row] = decodeFaxRows(snapshotFaxConfig(fakeSql([polling({ faxUser: '123456' })])));
  assert.equal(row.faxUser, '123456');
  assert.equal(row.accountName, 'Playwright SRFax check');
  assert.equal(row.active, '1');
  assert.equal(row.download, '1');
});

/*
 * THE GUARD. begin() snapshots and refuses a table already polling this check's fake account;
 * finish() restores, then asserts polling is unchanged; a signal runs the same restore before the
 * process exits.
 */
const FAKE = { faxUser: '000000', accountName: 'Playwright SRFax check' };

function guardWith(sql, { exit } = {}) {
  const proc = new EventEmitter();
  const exits = [];
  const released = [];
  const guard = createFaxConfigGuard({
    sql, proc, exit: exit || ((code) => exits.push(code)), own: FAKE, release: () => released.push('released'),
  });
  return { guard, proc, exits, released };
}

test('shouldRestoreAndAssertPollingUnchanged_whenTheFlowEnds', () => {
  const sql = fakeSql();
  const { guard } = guardWith(sql);
  guard.begin();
  sql.state.rows = [polling({ id: 'D1' })];
  assert.equal(pollingRowCount(sql), 1, 'the save left the gateway polling');
  guard.finish();
  assert.equal(pollingRowCount(sql), 0);
  assert.deepEqual(sql.state.rows, [SEED_ROW]);
});

test('shouldFail_whenPollingIsStillOnAfterTheRestore', () => {
  // The cells read back identical, but the database reports a polling row: the guard trusts the
  // database's own count, not only its encoding of the row.
  const sql = fakeSql();
  const { guard } = guardWith(sql);
  guard.begin();
  sql.state.pollingOverride = 1;
  assert.throws(() => guard.finish(), /polling is on after the restore/);
});

test('shouldAllowAPollingRow_whenItWasThereBeforeAndIsUnchanged', () => {
  // A deliberately configured real account (FAX_CONFIG_ALLOW_OVERWRITE=true) polls before and
  // after; the check puts it back and does not call that a failure of its own.
  const sql = fakeSql([polling({ faxUser: '777777', accountName: 'Real clinic account', id: 'D1' })]);
  const { guard } = guardWith(sql);
  guard.begin();
  sql.state.rows = [polling({ id: 'D1' })];
  guard.finish();
  assert.deepEqual(sql.state.rows.map((row) => row[4]), [`X${hex('777777')}`]);
  assert.equal(pollingRowCount(sql), 1, 'the real account polls again, as it did before');
});

test('shouldRefuseToStart_whenTheFakeAccountIsAlreadyPolling', () => {
  const sql = fakeSql([polling()]);
  const { guard } = guardWith(sql);
  assert.throws(() => guard.begin(), /already polls/);
  assert.equal(sql.state.statements.length, 0, 'the refusal changes nothing');
});

test('shouldRestoreOnce_whenFinishRunsTwice', () => {
  const sql = fakeSql();
  const { guard } = guardWith(sql);
  guard.begin();
  sql.state.rows = [polling({ id: 'D1' })];
  guard.finish();
  const statements = sql.state.statements.length;
  guard.finish();
  assert.equal(sql.state.statements.length, statements, 'the signal handler and the finally block must not both restore');
});

test('shouldRestoreAndExit_whenTerminatedOrInterrupted', () => {
  for (const [signal, code] of [['SIGTERM', 143], ['SIGINT', 130]]) {
    const sql = fakeSql();
    const { guard, proc, exits } = guardWith(sql);
    guard.begin();
    guard.armSignals();
    sql.state.rows = [polling({ id: 'D1' })];
    proc.emit(signal);
    assert.deepEqual(sql.state.rows, [SEED_ROW], `${signal} must put the row back`);
    assert.deepEqual(exits, [code], `${signal} exits ${code} like the harness`);
  }
});

test('shouldFreeTheDatabaseClientOnce_afterASignalOrAnOrdinaryFinish', () => {
  const sql = fakeSql();
  const signalled = guardWith(sql);
  signalled.guard.begin();
  signalled.guard.armSignals();
  signalled.proc.emit('SIGTERM');
  signalled.guard.release();
  assert.deepEqual(signalled.released, ['released'], 'the handler released it; the finally block must not do it twice');

  const ordinary = guardWith(fakeSql());
  ordinary.guard.begin();
  ordinary.guard.finish();
  ordinary.guard.release();
  ordinary.guard.release();
  assert.deepEqual(ordinary.released, ['released']);
});

test('shouldStopListening_whenDisarmed', () => {
  const sql = fakeSql();
  const { guard, proc } = guardWith(sql);
  guard.begin();
  guard.armSignals();
  assert.equal(proc.listenerCount('SIGTERM'), 1);
  guard.disarmSignals();
  assert.equal(proc.listenerCount('SIGTERM'), 0);
  assert.equal(proc.listenerCount('SIGINT'), 0);
});

/*
 * The check itself is a browser script and cannot run here, so its use of the guard is pinned as
 * text: a regression that drops the restore from its finally block would pass every test above.
 */
const SCRIPT = fs.readFileSync(path.join(__dirname, 'fax-configure-playwright-checks.js'), 'utf8');

test('shouldSnapshotBeforeTheBrowserStarts_andRestoreInFinally', () => {
  assert.match(SCRIPT, /createFaxConfigGuard\(/);
  const begin = SCRIPT.indexOf('.begin(');
  const launch = SCRIPT.indexOf('chromium.launch(');
  assert.ok(begin > 0 && launch > 0 && begin < launch, 'the snapshot is taken before anything can save');
  // The finally block that closes the browser: the restore must live in the same one.
  const finallyBlock = SCRIPT.slice(SCRIPT.indexOf('} finally {\n    if (context)'));
  assert.ok(finallyBlock.startsWith('} finally {'), 'the browser-closing finally block moved; update this pin');
  assert.match(finallyBlock, /\.finish\(\)/, 'the restore runs in the finally block');
  assert.match(finallyBlock, /\.disarmSignals\(\)/);
  assert.match(SCRIPT, /\.armSignals\(\)/);
});

test('shouldSkipTheSave_whenTheRowCannotBeRestored', () => {
  // saveIsSafe must require the guard: without MYSQL_PASSWORD there is no snapshot to restore from.
  assert.match(SCRIPT, /const saveIsSafe = faxGuard !== null\s*&&/);
});

test('shouldRestoreTheRowBeforeTheSaveAssertions_soTheSchedulerBarelyCanReadIt', () => {
  // The saved account is enabled with polling on until the restore, and the scheduler the save
  // started reads it 3 s later at the earliest. The restore therefore sits right after the reload
  // is captured, ahead of every assertion, screenshot and the browser teardown.
  const step = SCRIPT.slice(SCRIPT.indexOf('await (saveIsSafe ? step'), SCRIPT.indexOf("await step('no page errors"));
  const restore = step.indexOf('faxGuard.finish()');
  assert.ok(restore > 0, 'the save step restores the row itself');
  for (const later of ['Account number did not persist', "shot(direct, existing, 'fax-config-reloaded')", "shot(page, existing, 'fax-config-saved')", 'direct.close()']) {
    assert.ok(step.indexOf(later) > restore, `${later} must come after the restore`);
  }
  assert.ok(step.indexOf('direct.content()') < restore, 'the reload is captured before the restore');
});

test('shouldNameThePollingStepUnchanged_notOff', () => {
  // The guard asserts the polling count equals the pre-run count (off on the shipped row, but
  // unchanged when a real account was already polling), so the label must not say "off".
  assert.match(SCRIPT, /polling state is unchanged/);
  assert.doesNotMatch(SCRIPT, /restoreStep = [^;]*polling is off/);
});

test('shouldNotBaselineTheFaxImporterError_nowThatTheCheckRestoresTheRow', () => {
  // scripts/lib/server-log-baseline.tsv used to explain FaxImporter.java:406 as "the fake SRFax
  // account fax-configure saves". The check restores the row, so that reason is false, and an entry
  // with no count bound would report a failed restore (a per-minute ERROR) as KNOWN. Report, don't
  // encode: the ERROR is an unexplained signature (finding 180 stays open).
  const baseline = fs.readFileSync(path.join(__dirname, 'lib', 'server-log-baseline.tsv'), 'utf8');
  const entries = baseline.split('\n').filter((line) => line.trim() && !line.startsWith('#'));
  const signature = 'ERROR core.FaxImporter (FaxImporter.java:406)';
  assert.ok(!entries.some((entry) => new RegExp(entry.split('\t')[0]).test(signature)),
    'the baseline explains the FaxImporter ERROR again');
  assert.doesNotMatch(baseline, /fax-configure-playwright-checks\.js saves/);
});
