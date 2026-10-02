/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const source = fs.readFileSync(path.join(__dirname, 'patient-letters-envelopes-playwright-checks.js'), 'utf8');
const code = source.slice(source.indexOf('async function cleanup('), source.indexOf('\n(async () => {'));
function fixture({filename='letter-7-owned.pdf', failedLookup=false} = {}) {
  const removed = [];
  const writes = [];
  const queries = [];
  const context = vm.createContext({path, documentDirectory:'/fixture/documents', reportName:'PW_LETTER_OWNED',
    fs:{rmSync: name => removed.push(name)},
    sql: query => {
      queries.push(query);
      if (query.startsWith('SELECT ID')) {
        if (failedLookup) throw new Error('database unavailable');
        return '7';
      }
      if (query.startsWith('SELECT document_no')) return `81\t${filename}`;
      if (query.startsWith('SELECT COUNT')) return '0';
      writes.push(query);
      return '';
    },
  });
  vm.runInContext(code, context);
  return {context,removed,writes,queries};
}
test('letter cleanup removes only the exact owned file and stamped database rows', async () => {
  const f = fixture();
  await f.context.cleanup('123');
  assert.deepEqual(f.removed, ['/fixture/documents/letter-7-owned.pdf']);
  assert.equal(f.writes.length, 1);
  assert.match(f.writes[0], /WHERE d\.docdesc='7-PW_LETTER_OWNED'/);
  assert.match(f.writes[0], /WHERE ID=7 AND report_name='PW_LETTER_OWNED'/);
  assert.match(f.writes[0], /WHERE comments='PW_LETTER_OWNED'/);
});
test('letter cleanup fails without deleting anything when database ownership cannot be checked', async () => {
  const f = fixture({failedLookup:true});
  await assert.rejects(f.context.cleanup('123'), /database unavailable/);
  assert.deepEqual(f.removed, []);
  assert.deepEqual(f.writes, []);
});
test('letter cleanup refuses path traversal and files belonging to another template', async () => {
  for (const filename of ['../letter-7-owned.pdf','letter-8-other.pdf','patient-document.pdf']) {
    const f = fixture({filename});
    await assert.rejects(f.context.cleanup('123'), /not owned/);
    assert.deepEqual(f.removed, []);
    assert.deepEqual(f.writes, []);
  }
});

function guardedCleanup() {
  const messages = [];
  const context = vm.createContext({ AggregateError, console: { error: message => messages.push(message) } });
  const start = source.indexOf('async function withPreservedCleanup(');
  vm.runInContext(source.slice(start, source.indexOf('async function checkUnicodeEnvelope(', start)), context);
  return { run: context.withPreservedCleanup, messages };
}
test('cleanup attempts every task and preserves the original body failure', async () => {
  const f = guardedCleanup();
  const primary = new Error('PDF request failed');
  const attempted = [];
  await assert.rejects(f.run(async () => { throw primary; }, [
    () => { attempted.push('files'); throw new Error('file removal failed'); },
    () => { attempted.push('rows'); throw new Error('row removal failed'); },
    () => attempted.push('browser'),
  ]), error => error === primary);
  assert.deepEqual(attempted, ['files', 'rows', 'browser']);
  assert.equal(f.messages.length, 2);
});
test('cleanup failures fail an otherwise successful workflow after every cleanup is attempted', async () => {
  const f = guardedCleanup();
  const attempted = [];
  await assert.rejects(f.run(async () => 'result', [
    () => { attempted.push('files'); throw new Error('file removal failed'); },
    () => attempted.push('browser'),
  ]), error => error instanceof AggregateError && error.errors[0].message === 'file removal failed');
  assert.deepEqual(attempted, ['files', 'browser']);
});
test('successful cleanup returns the workflow result', async () => {
  assert.equal(await guardedCleanup().run(async () => 'result', [() => {}]), 'result');
});


test('follow-up cleanup preserves matching comments for other patients and measurement types', async () => {
  const { execFileSync } = require('node:child_process');
  const f = fixture();
  await f.context.cleanup('123');
  const deletion = f.writes[0].match(/DELETE FROM measurements[^;]+/)[0];
  const verification = f.queries.find(query => query.startsWith('SELECT COUNT(*) FROM measurements'));
  assert.ok(verification, 'Owned measurement cleanup must be verified');
  execFileSync('python3', ['-c', `
import json, sqlite3, sys
delete, verify = json.load(sys.stdin)
db = sqlite3.connect(':memory:')
db.executescript("""
CREATE TABLE measurements(id INTEGER, demographicNo INTEGER, type TEXT, comments TEXT);
INSERT INTO measurements VALUES(1,123,'FLUF','PW_LETTER_OWNED'),(2,999,'FLUF','PW_LETTER_OWNED'),(3,123,'BP','PW_LETTER_OWNED'),(4,123,'FLUF','another report');
""")
db.execute(delete)
assert db.execute(verify).fetchone() == (0,), 'Owned follow-up remains'
assert db.execute('SELECT id FROM measurements ORDER BY id').fetchall() == [(2,), (3,), (4,)], 'Unrelated measurements were deleted'
`], { input: JSON.stringify([deletion, verification]), stdio: ['pipe', 'pipe', 'pipe'] });
});

test('follow-up cleanup refuses invalid patient identities before reading or deleting data', async () => {
  for (const patient of [undefined, '', '0', '-1', '123 OR 1=1', '9007199254740993']) {
    const f = fixture();
    await assert.rejects(f.context.cleanup(patient), /Invalid owned follow-up patient/);
    assert.deepEqual(f.queries, []);
    assert.deepEqual(f.removed, []);
  }
});

test('unicode envelope cleanup removes the owned patient even when its stored name was transformed', async () => {
  const { execFileSync } = require('node:child_process');
  const { sqlString } = require('./lib/playwright-harness');
  const queries = [];
  const findings = [];
  const removedDirs = [];
  let deleted = false;
  const start = source.indexOf('async function withPreservedCleanup(');
  const end = source.indexOf('async function checkEnvelopePdf(', start);
  const context = vm.createContext({
    AggregateError, Buffer, path, sqlString, testUser: 'carlosdoc', console: { error() {} },
    process: { pid: 4242 },
    os: { tmpdir: () => '/fixture/tmp' },
    fs: { mkdtempSync: prefix => `${prefix}x`, rmSync: dir => removedDirs.push(dir), writeFileSync() {} },
    execFileSync: () => '',
    isPdf: () => false,
    appUrl: route => route,
    expect: (condition, label) => { if (!condition) findings.push(label); },
    sql: query => {
      queries.push(query);
      if (query.startsWith('SELECT provider_no')) return '999998';
      if (query.startsWith('INSERT INTO demographic')) return '55';
      // The failure mode under test: the database stored a truncated last_name.
      if (query.startsWith('SELECT last_name')) return 'FAKE_LETTER_trunc';
      if (query.startsWith('DELETE FROM demographic')) { deleted = true; return ''; }
      if (query.startsWith('SELECT COUNT')) return deleted ? '0' : '1';
      return '';
    },
  });
  vm.runInContext(source.slice(start, end), context);
  const request = { get: async () => ({ status: () => 500, body: async () => Buffer.from('') }) };
  await context.checkUnicodeEnvelope({ request });

  assert.ok(findings.includes('unicode-envelope: synthetic patient name survived the database round trip'),
    'the round-trip assertion must report the transformed name');
  const deletion = queries.find(query => query.startsWith('DELETE FROM demographic'));
  assert.ok(deletion, 'the owned patient must be deleted');
  const verification = queries.find(query => query.startsWith('SELECT COUNT(*) FROM demographic'));
  assert.ok(verification, 'the owned patient deletion must be verified');
  // Run the real statements against a row whose name no longer matches the marker.
  const remaining = execFileSync('python3', ['-c', `
import json, sqlite3, sys
delete, verify = json.load(sys.stdin)
db = sqlite3.connect(':memory:')
db.executescript("""
CREATE TABLE demographic(demographic_no INTEGER, first_name TEXT, last_name TEXT);
INSERT INTO demographic VALUES(55,'Lukasz ?????','FAKE_LETTER_trunc'),(56,'Other','Patient');
""")
db.execute(delete)
print(json.dumps([db.execute(verify).fetchone()[0], [r[0] for r in db.execute('SELECT demographic_no FROM demographic')]]))
`], { input: JSON.stringify([deletion, verification]), encoding: 'utf8' });
  assert.deepEqual(JSON.parse(remaining), [0, [56]], 'only the owned synthetic patient is removed');
  assert.deepEqual(removedDirs, ['/fixture/tmp/letter-envelope-pdf-x']);
});
