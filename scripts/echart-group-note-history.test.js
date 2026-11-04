/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {assertSharedHistoryText, assertDeniedHistory, assertNoGroupIssueQueryFailure} = require('./echart-group-note-history-playwright-checks');
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
  const event = `2026-11-04 00:35:00,000 ERROR web.CaseManagementView2Action (CaseManagementView2Action.java:1035) - private clinical text
java.lang.IllegalArgumentException: org.hibernate.query.PathException: Plural path 'io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue(cmi).notes' refers to a collection
    at io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAOImpl.getIssuesByNote(CaseManagementIssueDAOImpl.java:80)`;
  assert.throws(() => assertNoGroupIssueQueryFailure(event), error =>
    /group-note issue PathException/.test(error.message) && !error.message.includes('private clinical text'));
  assert.doesNotThrow(() => assertNoGroupIssueQueryFailure(''));
  assert.doesNotThrow(() => assertNoGroupIssueQueryFailure(event.replaceAll('CaseManagementIssue', 'OtherEntity')));
  assert.doesNotThrow(() => assertNoGroupIssueQueryFailure(event.replaceAll('org.hibernate.query.PathException', 'java.lang.IllegalStateException')));
});
