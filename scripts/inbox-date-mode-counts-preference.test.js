/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * inbox-date-mode-counts changes the clinic-wide inboxDateSearchType preference. Its cleanup must put the
 * SystemPreferences rows back exactly, including the original absence of the row when the INSERT committed
 * but the SQL client lost the reply carrying the new id (PR #6317 review). Real SQL on an in-memory
 * database; the fake client mirrors lib/sql-client's rows/value/execute contract.
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const { guardDatePreference } = require('./inbox-date-mode-counts-playwright-checks');

const PREFERENCE = 'inboxDateSearchType';

function fixture({ rows = [], loseInsertReply = false, dropUpdates = false } = {}) {
  const db = new DatabaseSync(':memory:');
  db.function('NOW', () => '2026-11-19 00:00:00');
  db.exec('CREATE TABLE SystemPreferences (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, value TEXT, updateDate TEXT)');
  const insert = db.prepare('INSERT INTO SystemPreferences (name, value, updateDate) VALUES (?, ?, ?)');
  insert.run('lab_pdf_max_size', '1048576', null);
  for (const [name, value] of rows) insert.run(name, value, null);
  const read = query => {
    const statement = db.prepare(query);
    statement.setReturnArrays(true);
    return statement.all().map(row => row.map(value => (value === null ? 'NULL' : String(value))));
  };
  const sql = {
    rows: read,
    value(query) {
      const statements = query.split(';').map(part => part.trim()).filter(Boolean);
      for (const statement of statements.slice(0, -1)) db.exec(statement);
      // The INSERT above has committed; a lost reply never returns LAST_INSERT_ID() to the caller.
      if (loseInsertReply) throw new Error('synthetic lost mysql reply');
      return read(statements.at(-1).replace('LAST_INSERT_ID()', 'last_insert_rowid()'))[0]?.[0] || '';
    },
    execute(query) {
      if (dropUpdates && query.startsWith('UPDATE')) return;
      db.exec(query);
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
