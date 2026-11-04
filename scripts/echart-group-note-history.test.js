/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {assertSharedHistoryText, assertDeniedHistory, assertNoGroupIssueQueryFailure,
  groupIssueLogWindow, verifyGroupIssueQueryLog} = require('./echart-group-note-history-playwright-checks');
const queryFailure = `2026-11-04 00:35:00,000 ERROR web.CaseManagementView2Action (CaseManagementView2Action.java:1035) - private clinical text
java.lang.IllegalArgumentException: org.hibernate.query.PathException: Plural path 'io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue(cmi).notes' refers to a collection
    at io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAOImpl.getIssuesByNote(CaseManagementIssueDAOImpl.java:80)`;
const texts = {shared: 'SHARED assessment', privateText: 'PRIVATE assessment', future: 'FUTURE assessment', cumulative: 'CUMULATIVE assessment'};
test('shared history contains the granted row without other clinical text', () => {
  assert.doesNotThrow(() => assertSharedHistoryText(`Note History ${texts.shared}`, texts));
});
for (const name of ['privateText', 'future', 'cumulative']) test(`shared history rejects leaked ${name}`, () => {
  assert.throws(() => assertSharedHistoryText(`${texts.shared} ${texts[name]}`, texts), /disclosed/);
});
test('shared history cannot pass with a blank or unrelated response', () => {
  assert.throws(() => assertSharedHistoryText('Login required', texts), /omitted/);
});
test('denied history must be an explicit refusal without clinical text', () => {
  assert.doesNotThrow(() => assertDeniedHistory(403, '', Object.values(texts)));
  for (const status of [200, 302, 404, 500]) assert.throws(() => assertDeniedHistory(status, '', Object.values(texts)), /refused/);
  for (const text of Object.values(texts)) assert.throws(() => assertDeniedHistory(403, text, Object.values(texts)), /disclosed/);
});
test('Current Issues rejects a caught group-issue PathException without disclosing log messages', () => {
  const event = queryFailure;
  assert.throws(() => assertNoGroupIssueQueryFailure(event), error =>
    /group-note issue PathException/.test(error.message) && !error.message.includes('private clinical text'));
  assert.doesNotThrow(() => assertNoGroupIssueQueryFailure(''));
  assert.doesNotThrow(() => assertNoGroupIssueQueryFailure(event.replaceAll('CaseManagementIssue', 'OtherEntity')));
  assert.doesNotThrow(() => assertNoGroupIssueQueryFailure(event.replaceAll('org.hibernate.query.PathException', 'java.lang.IllegalStateException')));
});
test('missing log configuration skips only the log assertion without throwing a workflow SkipCheck', async () => {
  const notices = [];
  const window = groupIssueLogWindow({env: {}, notify: message => notices.push(message)});
  assert.equal(window, null);
  await verifyGroupIssueQueryLog(window);
  assert.match(notices.join('\n'), /SKIP.*browser assertions continue/);
});
test('log prerequisites probe both the mark and the read before returning a usable window', () => {
  for (const failingMethod of ['mark', 'since']) {
    const notices = [];
    const reader = {configured: true, mark: () => ({}), since: () => ''};
    reader[failingMethod] = () => { throw new Error('private clinical text'); };
    assert.equal(groupIssueLogWindow({reader, notify: message => notices.push(message)}), null);
    assert.match(notices.join('\n'), /unreadable.*browser assertions continue/);
    assert.ok(!notices.join('\n').includes('private clinical text'));
  }
  const mark = {}, reads = [];
  const reader = {configured: true, mark: () => mark, since: value => { reads.push(value); return ''; }};
  assert.deepEqual(groupIssueLogWindow({reader}), {reader, mark});
  assert.deepEqual(reads, [mark]);
});
test('the log settle window catches a query failure appearing after the first second', async () => {
  let time = 0;
  const reader = {since: () => time >= 1500 ? queryFailure : ''};
  await assert.rejects(verifyGroupIssueQueryLog({reader, mark: {}}, {
    now: () => time, sleep: async ms => { time += ms; },
  }), /group-note issue PathException/);
  assert.ok(time >= 1500 && time < 5000);
});
test('a clean log is polled through the full bounded settle window using the same cursor', async () => {
  let time = 0;
  const mark = {}, reads = [];
  await verifyGroupIssueQueryLog({reader: {since: value => { reads.push(value); return ''; }}, mark}, {
    now: () => time, sleep: async ms => { time += ms; },
  });
  assert.equal(time, 5000);
  assert.ok(reads.length > 1 && reads.every(value => value === mark));
});
test('a log read failure during settling skips only log coverage without exposing its message', async () => {
  const notices = [];
  await verifyGroupIssueQueryLog({reader: {since: () => { throw new Error('private clinical text'); }}, mark: {}}, {
    notify: message => notices.push(message),
  });
  assert.match(notices.join('\n'), /SKIP.*source became unreadable.*browser assertions continue/);
  assert.ok(!notices.join('\n').includes('private clinical text'));
});
