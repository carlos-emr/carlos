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

const { ownedEchartDeletes, withoutStampedBlocks, rollbackTriggerSql, cleanup, fixture, assertSameSnapshot, isNoteSave,
  isCppTerminalResponse, assertCppSaveRedirect, saveCppDialog, assertExtensionValues } =
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

const extensionFixture = treatment => [
  ['Resolution Date', '2022-04-04', null], ['Start Date', '2022-03-03', null],
  ['Treatment', null, treatment], ['Procedure', null, ''],
];

test('CPP extension assertions accept populated and explicitly cleared Treatment alongside other blank fields', () => {
  for (const treatment of ['PW original treatment', '']) {
    assert.doesNotThrow(() => assertExtensionValues(extensionFixture(treatment), '2022-03-03', '2022-04-04', treatment));
  }
});

test('CPP clearing assertions reject missing, null or retained old Treatment', () => {
  for (const rows of [extensionFixture('').filter(row => row[0] !== 'Treatment'),
    extensionFixture(null), extensionFixture('PW original treatment')]) {
    assert.throws(() => assertExtensionValues(rows, '2022-03-03', '2022-04-04', ''));
  }
});

test('CPP extension assertions reject duplicate keys even among extra blank fields', () => {
  for (const duplicate of [['Treatment', null, ''], ['Procedure', null, '']]) {
    assert.throws(() => assertExtensionValues([...extensionFixture(''), duplicate], '2022-03-03', '2022-04-04', ''),
      /duplicate extension keys/);
  }
});

test('CPP extension assertions still require both distinct expected dates', () => {
  for (const key of ['Start Date', 'Resolution Date']) {
    const rows = extensionFixture('');
    rows.find(row => row[0] === key)[1] = '2021-01-01';
    assert.throws(() => assertExtensionValues(rows, '2022-03-03', '2022-04-04', ''), /both expected dates/);
  }
});

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

function redirectFixture({postStatus = 302, finalStatus = 200, finalUrl, contentType} = {}) {
  const action = 'http://fixture.test/carlos/CaseManagementEntry?method=issueNoteSave&noteId=31&demographicNo=123&providerNo=200';
  const reload = 'http://fixture.test/carlos/CaseManagementView?method=listCPPNotes&demographicNo=123';
  const postRequest = {method: () => 'POST', redirectedFrom: () => null};
  const finalRequest = {method: () => 'GET', redirectedFrom: () => postRequest};
  const posted = {url: () => action, request: () => postRequest, status: () => postStatus, finished: async () => null};
  const terminal = {url: () => finalUrl || reload, request: () => finalRequest, status: () => finalStatus,
    finished: async () => null, headers: () => contentType === undefined ? {} : {'content-type': contentType}};
  return {action, reload, postRequest, posted, terminal};
}

test('CPP success follows the captured POST redirect to its own listing response', () => {
  const s = redirectFixture();
  assert.equal(isCppTerminalResponse(s.posted, s.postRequest), false);
  assert.equal(isCppTerminalResponse(s.terminal, s.postRequest), true);
  assert.doesNotThrow(() => assertCppSaveRedirect(s.posted, s.terminal, s.action, s.reload));
  const unrelated = redirectFixture();
  assert.equal(isCppTerminalResponse(unrelated.terminal, s.postRequest), false, 'Identical URLs are not request identity');
  assert.throws(() => assertCppSaveRedirect(s.posted, unrelated.terminal, s.action, s.reload), /own successful GET/);
  const intermediate = {method: () => 'GET', redirectedFrom: () => s.postRequest};
  const chained = {...s.terminal, request: () => ({method: () => 'GET', redirectedFrom: () => intermediate})};
  assert.doesNotThrow(() => assertCppSaveRedirect(s.posted, chained, s.action, s.reload));
});

test('CPP redirect validation rejects failure, login redirects and an unrelated patient listing', () => {
  for (const options of [{postStatus: 200}, {postStatus: 500}, {finalStatus: 500},
    {finalUrl: 'http://fixture.test/carlos/login'},
    {finalUrl: 'http://fixture.test/carlos/CaseManagementView?method=listCPPNotes&demographicNo=456'}]) {
    const s = redirectFixture(options);
    assert.throws(() => assertCppSaveRedirect(s.posted, s.terminal, s.action, s.reload));
  }
});

test('CPP AJAX listing permits the observed absent Content-Type and explicit HTML', () => {
  for (const contentType of [undefined, 'text/html;charset=UTF-8']) {
    const s = redirectFixture({contentType});
    assert.doesNotThrow(() => assertCppSaveRedirect(s.posted, s.terminal, s.action, s.reload));
  }
});

test('CPP listing rejects an explicitly declared non-HTML response', () => {
  for (const contentType of ['application/json', 'text/javascript', 'text/plain']) {
    const s = redirectFixture({contentType});
    assert.throws(() => assertCppSaveRedirect(s.posted, s.terminal, s.action, s.reload), /non-HTML listing/);
  }
});

for (const options of [{}, {postStatus: 500}, {finalStatus: 500}, {finalUrl: 'http://fixture.test/carlos/login'}]) {
  test(`CPP click binds redirect listeners first and records owned rows before validating ${JSON.stringify(options)}`, async () => {
    const saved = {...fixture};
    const s = redirectFixture(options);
    const waiters = [];
    let finished = 0;
    s.posted.finished = async () => { finished++; };
    s.terminal.finished = async () => { finished++; };
    const sql = {rows() { return [['98', 'owned-fingerprint']]; }};
    Object.assign(fixture, {demographicNo: '123', providerNo: '200', chartBefore: {echartMaxId: '97'}, echartOwned: {}});
    const button = {getAttribute: async () => 'Sign & Save', async click() {
      assert.equal(waiters.length, 2, 'Both waiters must exist before the synchronous redirect');
      for (const response of options.postStatus === 500 ? [s.posted] : [s.posted, s.terminal]) {
        for (const waiter of waiters) if (waiter.predicate(response)) waiter.resolve(response);
      }
    }};
    const page = {
      locator: selector => selector === '#reloadUrl'
        ? {inputValue: async () => new URL(s.reload).search.slice(1)} : {all: async () => [button]},
      waitForResponse: predicate => new Promise(resolve => waiters.push({predicate, resolve})),
    };
    try {
      const result = saveCppDialog(page, sql, 100, s.action, /sign/i, 'test save');
      if (Object.keys(options).length) await assert.rejects(result);
      else assert.equal(await result, s.posted);
      assert.equal(finished, options.postStatus === 500 ? 1 : 2, 'Every received response body must finish');
      assert.equal(fixture.echartOwned['98'], 'owned-fingerprint', 'A failed assertion must retain guarded cleanup ownership');
    } finally { Object.assign(fixture, saved); }
  });
}

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
