/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const {createInboxAcknowledgementFixture} = require('./lib/inbox-acknowledgement-fixture');

function environment({unassigned = false, ignoredRestore = false} = {}) {
  let rows = [['7', '100', '4E', 'NULL', 'NULL']];
  let marker = '';
  let disposed = false;
  const writes = [];
  const sql = {
    value: query => query.includes('FROM security') ? '101' : (unassigned ? '1' : '0'),
    rows: query => query.includes('HEX(status)') ? structuredClone(rows)
      : rows.filter(row => row[3] === marker).map(row => [row[0]]),
    execute(query) {
      writes.push(query);
      if (query.startsWith('INSERT')) {
        marker = query.match(/'N','([^']+)'/)[1];
        rows.push(['8', '101', '4E', marker, 'NULL']);
      } else if (query.startsWith('DELETE')) rows = rows.filter(row => row[0] !== '8');
      else if (query.startsWith('UPDATE') && !ignoredRestore) {
        const row = rows.find(row => row[0] === '7');
        if (row) Object.assign(row, ['7', '100', '4E', 'NULL', 'NULL']);
      }
    },
    dispose() { disposed = true; },
  };
  return {sql, writes, rows: () => rows, disposed: () => disposed,
    removeOriginal() { rows = rows.filter(row => row[0] !== '7'); }};
}
const config = {testUser: 'review-user', mysql: {}};
const view = chain => ({evaluate: async () => chain});

test('restores acknowledged older versions and deletes only pretracked missing routes', async () => {
  const env = environment();
  const fixture = createInboxAcknowledgementFixture(config, () => env.sql);
  await fixture.prepare(view('100,101,102'), 'HL7:101');
  env.rows().forEach(row => { row[2] = '41'; row[3] = ''; row[4] = '32303236'; });
  fixture.cleanup();
  assert.deepEqual(env.rows(), [['7', '100', '4E', 'NULL', 'NULL']]);
  assert.ok(env.writes.every(query => !query.includes('102')));
  assert.equal(env.disposed(), true);
});

for (const failure of ['missing original row', 'ignored restoration']) {
  test(`fails cleanup instead of silently passing with ${failure}`, async () => {
    const env = environment({ignoredRestore: failure === 'ignored restoration'});
    const fixture = createInboxAcknowledgementFixture(config, () => env.sql);
    await fixture.prepare(view('100'), 'HL7:100');
    if (failure === 'missing original row') env.removeOriginal();
    else env.rows()[0][2] = '41';
    assert.throws(() => fixture.cleanup(), /exact routing snapshot/);
    assert.equal(env.disposed(), true);
  });
}

test('refuses provider-zero routes before any mutation and disposes credentials', async () => {
  const env = environment({unassigned: true});
  const fixture = createInboxAcknowledgementFixture(config, () => env.sql);
  await assert.rejects(fixture.prepare(view('100'), 'HL7:100'), {name: 'SkipCheck'});
  fixture.cleanup();
  assert.deepEqual(env.writes, []);
  assert.equal(env.disposed(), true);
});

test('rejects malformed source chains before querying the database', async () => {
  const fixture = createInboxAcknowledgementFixture(config, () => { throw new Error('must not query'); });
  await assert.rejects(fixture.prepare(view('100,1 OR 1=1'), 'HL7:100'), /Invalid acknowledgement/);
});
