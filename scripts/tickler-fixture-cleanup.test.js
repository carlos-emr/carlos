/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const { cleanupTicklerFixture } = require('./lib/tickler-fixture-cleanup');
const fixture = { patient: '123', stamp: 'PW_TICKLER_NOTE_12345', noteTexts: ['first note', "second note's text"] };
function run(outputs, overrides = {}) {
  const statements = [];
  const sql = query => {
    statements.push(query);
    if (query.startsWith('SELECT')) return outputs.shift() || '';
    return '';
  };
  cleanupTicklerFixture({ ...fixture, sql, ...overrides });
  return statements;
}
test('cleanup limits reads to the patient and marker and deletes only returned IDs in dependency order', () => {
  const queries = run(['456\n457', '789']);
  assert.match(queries[0], /demographic_no=123 AND LOCATE\('PW_TICKLER_NOTE_12345', message\)>0/);
  assert.match(queries[1], /n.demographic_no=123 AND n.note IN/);
  const writes = queries.slice(2);
  assert.deepEqual(writes.map(query => query.match(/^DELETE FROM (\w+)/)[1]),
    ['casemgmt_note_link', 'casemgmt_issue_notes', 'casemgmt_note', 'tickler_comments', 'tickler_update', 'tickler']);
  assert.equal(writes[0], 'DELETE FROM casemgmt_note_link WHERE table_name=10 AND table_id IN (456,457)');
  assert.ok(writes.slice(1, 3).every(query => query.includes('note_id IN (789)')));
  assert.ok(writes.slice(3).every(query => query.includes('tickler_no IN (456,457)')));
  assert.ok(!queries.some(query => query.includes('NOT IN')));
});
test('cleanup preserves unrelated notes when an owned tickler has no matching marked notes', () => {
  const queries = run(['456', '']);
  assert.ok(queries.includes('DELETE FROM casemgmt_note_link WHERE table_name=10 AND table_id IN (456)'));
  assert.ok(!queries.some(query => /^DELETE FROM casemgmt_(note|issue_notes) WHERE/.test(query)));
  assert.ok(queries.some(query => /^DELETE FROM tickler WHERE/.test(query)));
});
test('cleanup does not write when its owned ticklers are absent', () => {
  assert.equal(run(['']).length, 1);
});
test('cleanup rejects malformed identities before any query', () => {
  for (const overrides of [{ patient: '1 OR 1=1' }, { stamp: '%' }]) {
    assert.throws(() => cleanupTicklerFixture({ ...fixture, ...overrides,
      sql: () => assert.fail('must not query') }), /Invalid tickler fixture identity/);
  }
});
test('cleanup rejects malformed selected IDs before any delete', () => {
  for (const outputs of [['456;DELETE'], ['456', '789;DELETE']]) {
    assert.throws(() => cleanupTicklerFixture({ ...fixture, sql: query => {
      assert.ok(!query.startsWith('DELETE'));
      return outputs.shift();
    } }), /Invalid owned fixture ID/);
  }
});
test('cleanup propagates database failures instead of reporting successful cleanup', () => {
  assert.throws(() => cleanupTicklerFixture({ ...fixture, sql: () => { throw new Error('database unavailable'); } }),
    /database unavailable/);
});

test('cleanup removes owned tickler links while retaining notes when no note text is selected', () => {
  const queries = run(['456'], { noteTexts: [] });
  assert.equal(queries.filter(query => query.startsWith('SELECT')).length, 1);
  assert.ok(queries.includes('DELETE FROM casemgmt_note_link WHERE table_name=10 AND table_id IN (456)'));
  assert.ok(!queries.some(query => /^DELETE FROM casemgmt_(note|issue_notes) WHERE/.test(query)));
});


test('cleanup preserves shared notes, issue links and unrelated record links in a real SQL database', () => {
  const { execFileSync } = require('node:child_process');
  // SQLite executes the same SELECT/DELETE subset; LOCATE is the only MySQL function used.
  const statements = run(['456\n457', '789']);
  execFileSync('python3', ['-c', `
import json, sqlite3, sys
queries = json.load(sys.stdin)
db = sqlite3.connect(':memory:')
db.create_function('LOCATE', 2, lambda needle, text: text.find(needle) + 1)
db.executescript("""
CREATE TABLE tickler(tickler_no INTEGER, demographic_no INTEGER, message TEXT);
CREATE TABLE casemgmt_note(note_id INTEGER, demographic_no INTEGER, note TEXT);
CREATE TABLE casemgmt_note_link(table_name INTEGER, table_id INTEGER, note_id INTEGER);
CREATE TABLE casemgmt_issue_notes(note_id INTEGER);
CREATE TABLE tickler_comments(tickler_no INTEGER);
CREATE TABLE tickler_update(tickler_no INTEGER);
INSERT INTO tickler VALUES(456,123,'PW_TICKLER_NOTE_12345 A'),(457,123,'PW_TICKLER_NOTE_12345 B'),(999,123,'unrelated');
INSERT INTO casemgmt_note VALUES(789,123,'first note'),(790,123,'first note'),(791,123,'first note'),(792,999,'first note');
INSERT INTO casemgmt_note_link VALUES(10,456,789),(10,457,789),(10,456,790),(10,999,790),(10,456,791),(11,456,791),(10,456,792);
INSERT INTO casemgmt_issue_notes VALUES(789),(790),(791),(792);
INSERT INTO tickler_comments VALUES(456),(999);
INSERT INTO tickler_update VALUES(457),(999);
""")
assert db.execute(queries[0]).fetchall() == [(456,), (457,)]
assert db.execute(queries[1]).fetchall() == [(789,)], 'Shared or foreign notes selected for deletion'
for query in queries[2:]:
    db.execute(query)
assert db.execute('SELECT note_id FROM casemgmt_note ORDER BY note_id').fetchall() == [(790,), (791,), (792,)]
assert db.execute('SELECT note_id FROM casemgmt_issue_notes ORDER BY note_id').fetchall() == [(790,), (791,), (792,)]
assert db.execute('SELECT * FROM casemgmt_note_link ORDER BY note_id').fetchall() == [(10,999,790),(11,456,791)]
for table in ('tickler', 'tickler_comments', 'tickler_update'):
    assert db.execute('SELECT tickler_no FROM ' + table).fetchall() == [(999,)]
`], { input: JSON.stringify(statements), stdio: ['pipe', 'pipe', 'pipe'] });
});
