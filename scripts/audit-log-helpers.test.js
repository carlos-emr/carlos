/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { auditProbe, phiLeaks } = require('./lib/audit-log-helpers');

let DatabaseSync = null;
try { ({ DatabaseSync } = require('node:sqlite')); } catch (error) { /* older Node: the SQL-evaluating test skips */ }

test('cleanup predicate keeps an unrelated row whose contentId equals the patient id', { skip: !DatabaseSync }, () => {
  const db = new DatabaseSync(':memory:');
  // auditProbe supplies a fixed predicate and validated numeric fixture patient ID.
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
  db.function('regexp', (pattern, value) => (value !== null && new RegExp(pattern).test(String(value)) ? 1 : 0));
  db.exec('CREATE TABLE log (id INTEGER PRIMARY KEY, action TEXT, content TEXT, contentId TEXT, demographic_no INTEGER, data TEXT)');
  const insert = db.prepare('INSERT INTO log (id, action, content, contentId, demographic_no, data) VALUES (?,?,?,?,?,?)');
  insert.run(1, 'read', 'demographic', '777', 777, null);            // the patient's own read row
  insert.run(2, 'update', 'demographic', '777', 777, null);          // the patient's own edit row
  insert.run(3, 'edit', 'x', null, null, 'demographicNo=777');       // keyed only by its data
  insert.run(4, 'read', 'document', '777', null, 'doc_no=777');      // a document whose id equals the patient id
  insert.run(5, 'add', 'CME note', '777', null, null);               // a note whose id equals the patient id
  insert.run(6, 'read', 'demographic', '778', 778, null);            // another patient
  const sql = {
    execute(statement) { db.exec(statement); },
    value(query) { return String(Object.values(db.prepare(query).get())[0]); },
    rows(query) { return db.prepare(query).all().map(row => Object.values(row).map(String)); },
  };
  const probe = auditProbe({ sql, patient: '777' });
  probe.cleanup();
  const left = db.prepare('SELECT id FROM log ORDER BY id').all().map(row => row.id);
  assert.deepEqual(left, [4, 5, 6], 'only rows provably about the owned patient may be deleted');
});

test('cleanup predicate still removes rows of an owned entity registered with own()', { skip: !DatabaseSync }, () => {
  const db = new DatabaseSync(':memory:');
  db.function('regexp', (pattern, value) => 0);
  db.exec('CREATE TABLE log (id INTEGER PRIMARY KEY, action TEXT, content TEXT, contentId TEXT, demographic_no INTEGER, data TEXT)');
  db.exec(`INSERT INTO log VALUES (1,'add','CME note','55',NULL,NULL),(2,'add','CME note','56',NULL,NULL)`);
  const sql = {
    execute(statement) { db.exec(statement); },
    value(query) { return String(Object.values(db.prepare(query).get())[0]); },
    rows() { return []; },
  };
  const probe = auditProbe({ sql, patient: '777' });
  probe.own('CME note', '55');
  probe.cleanup();
  assert.deepEqual(db.prepare('SELECT id FROM log').all().map(row => row.id), [2]);
});

test('cleanup leaves ambiguous rows written during the run (another check may own them)', { skip: !DatabaseSync }, () => {
  const db = new DatabaseSync(':memory:');
  db.function('regexp', (pattern, value) => 0);
  db.exec('CREATE TABLE log (id INTEGER PRIMARY KEY, dateTime TEXT, provider_no TEXT, action TEXT, content TEXT, contentId TEXT, ip TEXT, demographic_no INTEGER, data TEXT)');
  const insert = db.prepare('INSERT INTO log (id, provider_no, action, content, contentId, demographic_no, data) VALUES (?,?,?,?,?,?,?)');
  const sql = {
    execute(statement) { db.exec(statement); },
    value(query) { return String(Object.values(db.prepare(query).get())[0]); },
    rows(query) { return db.prepare(query).all().map(row => Object.values(row).map(String)); },
  };
  const probe = auditProbe({ sql, patient: '777' });
  insert.run(1, '999', 'read', 'demographic', '777', 777, null);           // provably the patient's: deleted
  insert.run(2, '999', 'DemographicManager.get', null, null, null, '777'); // bare numeric data: ambiguous, kept
  insert.run(3, '999', 'read', 'tickler', '777', null, null);              // a colliding tickler id: kept
  // A same-provider row whose contentId merely collides is not evidence about the patient either.
  assert.deepEqual(probe.ownedSince(0).map(row => row.id), [1]);
  probe.cleanup();
  assert.deepEqual(db.prepare('SELECT id FROM log ORDER BY id').all().map(row => row.id), [2, 3]);
});

test('phiLeaks never echoes the content column that carried the patient text', () => {
  const secret = 'FAKE-PWsecret';
  const leaks = phiLeaks([{ action: 'add', content: `note ${secret}`, contentId: '1', data: null }], [secret]);
  assert.deepEqual(leaks, ['add/<content>:content']);
  assert.ok(!leaks.join().includes(secret));
});
