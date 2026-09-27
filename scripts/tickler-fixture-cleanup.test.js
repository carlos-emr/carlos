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
    ['casemgmt_note_link', 'casemgmt_issue_notes', 'casemgmt_note', 'ticklerdocs', 'tickler_comments', 'tickler_update', 'tickler']);
  assert.equal(writes[0], 'DELETE FROM casemgmt_note_link WHERE table_name=10 AND table_id IN (456,457)');
  assert.ok(writes.slice(1, 3).every(query => query.includes('note_id IN (789)')));
  assert.match(writes[3], /tickler_id IN \(456,457\)/);
  assert.ok(writes.slice(4).every(query => query.includes('tickler_no IN (456,457)')));
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
