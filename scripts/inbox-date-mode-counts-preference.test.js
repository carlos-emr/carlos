/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * inbox-date-mode-counts changes the clinic-wide inboxDateSearchType preference. Its cleanup must put the
 * SystemPreferences rows back exactly, including the original absence of the row when the INSERT committed
 * but the SQL client lost the reply carrying the new id (PR #6317 review). Real SQL on an in-memory
 * database; the fake client mirrors the playwright-harness SQL runner's bound rows/value/execute/insertId
 * contract and refuses any call that is not a single statement with a parameter array, so the guard can
 * never fall back to interpolating values into SQL text.
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const { guardDatePreference, preflightConfig, WORKFLOW_OPTIONS } = require('./inbox-date-mode-counts-playwright-checks');

const PREFERENCE = 'inboxDateSearchType';

function fixture({ rows = [], loseInsertReply = false, dropUpdates = false } = {}) {
  const db = new DatabaseSync(':memory:');
  db.function('NOW', () => '2026-11-19 00:00:00');
  db.exec('CREATE TABLE SystemPreferences (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, value TEXT, updateDate TEXT)');
  const insert = db.prepare('INSERT INTO SystemPreferences (name, value, updateDate) VALUES (?, ?, ?)');
  insert.run('lab_pdf_max_size', '1048576', null);
  for (const [name, value] of rows) insert.run(name, value, null);
  const read = (query, parameters = []) => {
    const statement = db.prepare(query);
    statement.setReturnArrays(true);
    return statement.all(...parameters).map(row => row.map(value => (value === null ? 'NULL' : String(value))));
  };
  // The harness binds through server-side PREPARE: one statement, values only in the parameter array.
  const bound = (query, parameters) => {
    assert.ok(Array.isArray(parameters), `unbound SQL: ${query}`);
    assert.ok(!query.includes(';'), `bound SQL must be one statement: ${query}`);
    for (const value of [PREFERENCE, 'serviceObservation', 'receivedCreated']) {
      assert.ok(!query.includes(value), `${value} was written into the SQL text instead of bound: ${query}`);
    }
    return parameters;
  };
  const sql = {
    rows: (query, parameters) => read(query, bound(query, parameters)),
    value: (query, parameters) => read(query, bound(query, parameters))[0]?.[0] || '',
    execute(query, parameters) {
      bound(query, parameters);
      if (dropUpdates && query.startsWith('UPDATE')) return;
      db.prepare(query).run(...parameters);
    },
    insertId(query, parameters) {
      db.prepare(query).run(...bound(query, parameters));
      // The INSERT above has committed; a lost reply never returns LAST_INSERT_ID() to the caller.
      if (loseInsertReply) throw new Error('synthetic lost mysql reply');
      return read('SELECT last_insert_rowid()')[0][0];
    },
  };
  const callbacks = [];
  const s = { sql, cleanup: fn => callbacks.push(fn) };
  const preference = () => read(`SELECT id, IFNULL(HEX(value),''), value IS NULL FROM SystemPreferences
    WHERE name='${PREFERENCE}' ORDER BY id`);
  const others = () => read(`SELECT id, value FROM SystemPreferences WHERE name<>'${PREFERENCE}' ORDER BY id`);
  return { db, s, callbacks, preference, others, cleanup: () => callbacks.forEach(fn => fn()) };
}

test('shouldRestoreTheOriginalAbsence_whenTheInsertCommitsButItsReplyIsLost', () => {
  const f = fixture({ loseInsertReply: true });
  const before = f.others();

  assert.throws(() => guardDatePreference(f.s), /lost mysql reply/);
  assert.equal(f.preference().length, 1, 'the INSERT committed although no id came back');
  f.cleanup();

  assert.deepEqual(f.preference(), [], 'cleanup left the inserted preference behind');
  assert.deepEqual(f.others(), before, 'cleanup touched another preference');
});

test('shouldDeleteTheInsertedRow_whenThePreferenceWasAbsent', () => {
  const f = fixture();
  const setPreference = guardDatePreference(f.s);
  setPreference('receivedCreated');
  assert.deepEqual(f.preference().map(([, hex]) => Buffer.from(hex, 'hex').toString()), ['receivedCreated']);

  f.cleanup();

  assert.deepEqual(f.preference(), []);
});

test('shouldRestoreEveryOriginalValueIncludingNull_andRemoveRowsAddedDuringTheRun', () => {
  const f = fixture({ rows: [[PREFERENCE, 'receivedCreated'], [PREFERENCE, null], [PREFERENCE, '']] });
  const before = f.preference();
  const setPreference = guardDatePreference(f.s);
  setPreference('serviceObservation');
  f.db.exec(`INSERT INTO SystemPreferences (name, value) VALUES ('${PREFERENCE}', 'serviceObservation')`);

  f.cleanup();

  assert.deepEqual(f.preference(), before);
  assert.deepEqual(before.map(([, , isNull]) => isNull), ['0', '1', '0']);
});

test('shouldFailCleanup_whenThePreferenceCannotBeRestored', () => {
  const f = fixture({ rows: [[PREFERENCE, 'receivedCreated']], dropUpdates: true });
  guardDatePreference(f.s);
  f.db.exec(`UPDATE SystemPreferences SET value='serviceObservation' WHERE name='${PREFERENCE}'`);

  assert.throws(() => f.cleanup(), /inboxDateSearchType was not restored/);
});

test('shouldSkipBeforeAnyWrite_whenTheRunIsNotExclusiveOnADisposableHost', () => {
  // The guard deletes every preference row its snapshot did not hold, so a concurrent writer's row would go
  // with it (PR #6317 review). preflightConfig runs before runWorkflow opens a database connection.
  for (const env of [{}, { CARLOS_DISPOSABLE_HOST: 'true' }, { EXCLUSIVE: '1' },
    { CARLOS_DISPOSABLE_HOST: 'true', EXCLUSIVE: '0' }, { CARLOS_DISPOSABLE_HOST: 'yes', EXCLUSIVE: '1' }]) {
    assert.throws(() => preflightConfig({ config: {} }, env),
      error => error.name === 'SkipCheck' && /EXCLUSIVE=1/.test(error.message), JSON.stringify(env));
  }
  assert.doesNotThrow(() => preflightConfig({ config: {} }, { CARLOS_DISPOSABLE_HOST: 'true', EXCLUSIVE: '1' }));
});

test('shouldEnforceExclusivity_atTheDirectEntryPoint', () => {
  assert.equal(WORKFLOW_OPTIONS.preflightConfig, preflightConfig);
});
