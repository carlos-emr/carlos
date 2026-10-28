/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const {createLabRoutingFixture} = require('./lib/lab-routing-fixture');
const {parseMysqlBatchOutput} = require('./lib/playwright-harness');

function fixture(original = []) {
  const writes = [];
  const owned = [];
  let failInsert = false;
  const sql = statement => {
    writes.push(statement);
    if (statement.startsWith('INSERT')) {
      owned.push([String(900 + owned.length)]);
      if (failInsert) throw new Error('lost INSERT acknowledgement');
    }
  };
  const rows = statement => statement.includes('IFNULL(HEX(status)') ? original : owned;
  return {writes, owned, sql, rows, failInsert() { failInsert = true; }};
}

test('restores original comments, NULL status and review timestamp by exact row and source', () => {
  const env = fixture([['5', '100', 'NULL', '71756F7465270A096E6F7465', '323032362D30312D30322030333A30343A3035']]);
  const value = createLabRoutingFixture(env.sql, env.rows, '101', ['100']);
  value.prepare();
  value.cleanup();
  const restore = env.writes.find(s => s.includes('comment=UNHEX'));
  assert.match(restore, /status=NULL/);
  assert.match(restore, /comment=UNHEX\('71756F7465270A096E6F7465'\)/);
  assert.match(restore, /timestamp=UNHEX\('323032362D30312D30322030333A30343A3035'\)/);
  assert.match(restore, /WHERE id=5 AND lab_no=100 AND provider_no='101' AND lab_type='HL7'/);
  assert.equal(env.writes.filter(s => s.startsWith('DELETE')).length, 0);
});

test('restores real mysql-parser SQL NULL without confusing literal NULL or empty comments', () => {
  const timestamp = Buffer.from('2026-10-19 21:27:00').toString('hex').toUpperCase();
  const original = parseMysqlBatchOutput(`5\t100\t4E\tNULL\t${timestamp}\n6\t101\tNULL\t4E554C4C\tNULL\n7\t102\t4E\t\t${timestamp}\n`);
  assert.equal(original[0][3], null, 'Exercise the actual parser, not a string-NULL mock');
  const env = fixture(original);
  const value = createLabRoutingFixture(env.sql, env.rows, '101', ['100', '101', '102']);
  value.prepare(false);
  assert.deepEqual(env.writes, [], 'Snapshot and prepare must not mutate existing routes');
  value.cleanup();
  const restores = env.writes.filter(statement => statement.startsWith('UPDATE'));
  assert.equal(restores.length, 3);
  assert.match(restores[0], /status=UNHEX\('4E'\), comment=NULL/);
  assert.match(restores[0], new RegExp(`timestamp=UNHEX\\('${timestamp}'\\)`));
  assert.match(restores[1], /status=NULL, comment=UNHEX\('4E554C4C'\)/);
  assert.match(restores[1], /timestamp=NULL/);
  assert.match(restores[2], /comment=UNHEX\(''\)/);
  for (const statement of restores) assert.match(statement, /AND provider_no='101' AND lab_type='HL7' AND lab_no IN \(100,101,102\)$/);
});

test('actual parser non-null malformed hexadecimal snapshots still refuse before writes', () => {
  for (const value of ['4G', 'F', 'NULL;DROP TABLE providerLabRouting']) {
    const env = fixture(parseMysqlBatchOutput(`5\t100\t4E\t${value}\tNULL\n`));
    assert.throws(() => createLabRoutingFixture(env.sql, env.rows, '101', ['100']), /invalid hexadecimal snapshot/);
    assert.deepEqual(env.writes, []);
  }
});

test('tracks inserted routing for every version before acknowledgement and deletes only owned IDs', () => {
  const env = fixture();
  const value = createLabRoutingFixture(env.sql, env.rows, '101', ['100', '101']);
  value.prepare();
  env.owned.length = 0; // application acknowledgement overwrites the marker comments
  value.cleanup();
  const deletions = env.writes.filter(s => s.startsWith('DELETE'));
  assert.equal(deletions.length, 2);
  assert.match(deletions[0], /WHERE id=900 AND provider_no='101' AND lab_type='HL7' AND lab_no IN \(100,101\)/);
  assert.match(deletions[1], /WHERE id=901 /);
});

test('recovers a committed fixture INSERT even when its acknowledgement was lost', () => {
  const env = fixture();
  const value = createLabRoutingFixture(env.sql, env.rows, '101', ['100']);
  env.failInsert();
  assert.throws(() => value.prepare(), /lost INSERT acknowledgement/);
  env.owned.length = 0; // acknowledgement overwrites the marker before cleanup
  value.cleanup();
  assert.ok(env.writes.some(s => s.startsWith('DELETE') && s.includes('id=900')));
});

test('continues restoring other rows and reports a cleanup failure', () => {
  const env = fixture([['5', '100', '4E', '', 'NULL'], ['6', '101', '41', '4E554C4C', 'NULL']]);
  const value = createLabRoutingFixture(statement => {
    env.sql(statement);
    if (statement.includes('WHERE id=5 ')) throw new Error('first restore failed');
  }, env.rows, '101', ['100', '101']);
  assert.throws(() => value.cleanup(), AggregateError);
  assert.ok(env.writes.some(s => s.includes('WHERE id=6 ') && s.includes("comment=UNHEX('4E554C4C')")));
});

test('refuses foreign source snapshots and malformed identifiers before mutations', () => {
  const env = fixture([['5', '999', '4E', '', 'NULL']]);
  assert.throws(() => createLabRoutingFixture(env.sql, env.rows, '101', ['100']), /foreign source/);
  assert.throws(() => createLabRoutingFixture(env.sql, env.rows, '101 OR 1=1', ['100']), /identifiers/);
  assert.deepEqual(env.writes, []);
});

test('reports both failures with the INSERT failure as cause when immediate recovery also fails', () => {
  const insertFailure = new Error('lost insert reply');
  const recoveryFailure = new Error('lost ownership query');
  const value = createLabRoutingFixture(() => { throw insertFailure; },
    statement => {
      if (statement.includes('IFNULL(HEX(status)')) return [];
      throw recoveryFailure;
    }, '101', ['100']);
  assert.throws(() => value.prepare(), error => {
    assert.ok(error instanceof AggregateError);
    assert.equal(error.cause, insertFailure);
    assert.deepEqual(error.errors, [insertFailure, recoveryFailure]);
    return true;
  });
});
