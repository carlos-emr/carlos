/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The CPP browser check edits a clinical summary column during cleanup, so the
 * string surgery that decides what it removes is worth asserting on its own.
 *
 * copyNote2cpp() appends "\n-----[[<date>]]-----\n<note text>" to
 * casemgmt_cpp.socialHistory on every save, so deleting the note alone leaves
 * the check's text in the patient's summary for good. Removing too much instead
 * would delete a clinician's entry, which is worse.
 */

const test = require('node:test');
const assert = require('node:assert');

const { ownedEchartDeletes, withoutStampedBlocks, rollbackTriggerSql, cleanup, fixture, assertSameSnapshot, isNoteSave } =
  require('./cpp-note-extension-archive-playwright-checks');

const STAMP = 'PW_CPP_EXT_1790000000000';
const BEFORE = '\n-----[[Thu Sep 24 2026]]-----\nclinician entry before';
const MINE = `\n-----[[Fri Sep 25 2026]]-----\n${STAMP} CPP extension regression item`;
const AFTER = '\n-----[[Sat Sep 26 2026]]-----\nclinician entry after';

test('removes only the block carrying the stamp', () => {
  assert.equal(withoutStampedBlocks(BEFORE + MINE + AFTER, STAMP), BEFORE + AFTER);
});

test('removes every block carrying the stamp, not just the first', () => {
  const twice = BEFORE + MINE + MINE + AFTER;
  assert.equal(withoutStampedBlocks(twice, STAMP), BEFORE + AFTER);
});

test('leaves a summary that never held the stamp exactly as it was', () => {
  assert.equal(withoutStampedBlocks(BEFORE + AFTER, STAMP), BEFORE + AFTER);
});

test('leaves another run\'s stamp alone', () => {
  const other = `\n-----[[Fri Sep 25 2026]]-----\nPW_CPP_EXT_9999999999999 someone else`;
  assert.equal(withoutStampedBlocks(BEFORE + other, STAMP), BEFORE + other);
});

test('handles an absent summary and an absent stamp without throwing', () => {
  assert.equal(withoutStampedBlocks('', STAMP), '');
  assert.equal(withoutStampedBlocks(null, STAMP), '');
  assert.equal(withoutStampedBlocks(BEFORE, ''), BEFORE);
});

test('eChart deletes are guarded by row id, provider and the fingerprint recorded after the run\'s save', () => {
  const statements = ownedEchartDeletes('2', '999998', { 41: 'abc123' });
  assert.equal(statements.length, 1);
  const [what, sql] = statements[0];
  assert.match(what, /eChart row 41/);
  assert.match(sql, /^DELETE FROM eChart WHERE eChartId = 41 AND demographicNo = 2 /);
  assert.match(sql, /providerNo = '999998'/);
  assert.match(sql, /MD5\(CONCAT_WS\(CHAR\(31\), providerNo, .*encounter IS NULL, COALESCE\(encounter, ''\)\)\) = 'abc123'$/);
});

test('no recorded eChart rows means no eChart delete at all', () => {
  assert.deepEqual(ownedEchartDeletes('2', '999998', {}), []);
  assert.deepEqual(ownedEchartDeletes('2', '999998', undefined), []);
});

test('a recorded eChart id that is not a plain number never reaches a statement', () => {
  assert.throws(() => ownedEchartDeletes('2', '999998', { '1 OR 1=1': 'abc' }));
});

const TRIGGER = 'pw_cpp_ext_0123456789abcdef01234567';

test('save response matching requires the actual dialog endpoint, POST, patient and revision', () => {
  const action = 'http://fixture.test/carlos/CaseManagementEntry?method=issueNoteSave&noteId=31&demographicNo=123&providerNo=200';
  const response = (url = action, method = 'POST') => ({url: () => url, request: () => ({method: () => method})});
  assert.equal(isNoteSave(response(), action), true);
  assert.equal(isNoteSave(response(action + '&extra=value'), action), true);
  assert.equal(isNoteSave(response(action, 'GET'), action), false);
  for (const url of [action.replace('issueNoteSave', 'issueNoteSaveJson'), action.replace('noteId=31', 'noteId=32'),
    action.replace('demographicNo=123', 'demographicNo=456'), action.replace('providerNo=200', 'providerNo=201'),
    action.replace('/CaseManagementEntry', '/OtherEntry'), action.replace('/carlos/', '/another/'),
    action.replace('fixture.test', 'other.test')]) {
    assert.equal(isNoteSave(response(url), action), false);
  }
});

test('rollback trigger targets only the owned note and second new date after the first date update', () => {
  const sql = rollbackTriggerSql(TRIGGER, '123', STAMP);
  assert.match(sql, /^DELIMITER \/\/\nCREATE TRIGGER pw_cpp_ext_0123456789abcdef01234567 BEFORE UPDATE ON casemgmt_note_ext/);
  assert.match(sql, /NEW.key_val='Resolution Date' AND NEW.date_value='2022-04-04'/);
  assert.match(sql, /n.note_id=NEW.note_id\s+AND n.demographic_no=123 AND LOCATE\('PW_CPP_EXT_1790000000000', n.note\)=1/);
  assert.match(sql, /e.note_id=NEW.note_id\s+AND e.key_val='Start Date' AND e.date_value='2022-03-03'/);
  assert.match(sql, /SIGNAL SQLSTATE '45000'/);
});

test('rollback trigger rejects unowned or malformed identifiers and predicates before SQL', () => {
  for (const name of ['other_trigger', `${TRIGGER}; DROP TABLE demographic`, `${TRIGGER}_extra`]) {
    assert.throws(() => rollbackTriggerSql(name, '123', STAMP), /owned SQL identifier/);
  }
  for (const patient of ['', '0', '1 OR 1=1', '-1']) {
    assert.throws(() => rollbackTriggerSql(TRIGGER, patient, STAMP), /owned patient key/);
  }
  assert.throws(() => rollbackTriggerSql(TRIGGER, '123', "x' OR 1=1"), /note stamp/);
});

for (const fails of [false, true]) {
  test(`cleanup always attempts the owned trigger drop and disposes SQL when DROP ${fails ? 'fails' : 'succeeds'}`, async () => {
    const saved = {...fixture};
    const calls = [];
    Object.assign(fixture, {stamp: '', demographicNo: '', ownedPatient: '', chartBefore: null,
      rollbackTrigger: TRIGGER, sql: {
        execute(sql) { calls.push(sql); if (fails) throw new Error('DROP rejected'); },
        value() { return '0'; }, dispose() { calls.push('dispose'); },
      }});
    try {
      if (fails) await assert.rejects(cleanup(), /owned rollback trigger.*DROP rejected/);
      else await cleanup();
      assert.equal(calls[0], `DROP TRIGGER IF EXISTS ${TRIGGER}`);
      assert.equal(calls.at(-1), 'dispose');
      assert.equal(fixture.rollbackTrigger, fails ? TRIGGER : '');
    } finally { Object.assign(fixture, saved); }
  });
}

test('rollback comparison detects any owned row change while allowing unchanged snapshots', () => {
  const before = {notes: [['1', 'original', '-1']], extensions: [['1', 'Start Date', '2021-01-01']],
    cpp: [['1', 'summary']], echart: [], issueLinks: [['3', '1']], noteLinks: [], issues: [['3', '0']], signatures: []};
  assert.doesNotThrow(() => assertSameSnapshot(before, structuredClone(before), 'rollback'));
  for (const key of Object.keys(before)) {
    const after = structuredClone(before);
    after[key].push(['unexpected write']);
    assert.throws(() => assertSameSnapshot(before, after, 'rollback'), /rollback changed/);
  }
  const moved = structuredClone(before);
  moved.notes[0][2] = '0';
  assert.throws(() => assertSameSnapshot(before, moved, 'rollback'), /rollback changed notes/);
});
