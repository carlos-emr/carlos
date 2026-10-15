/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  patientStateQueries, readPatientState, assertPatientStateUnchanged, invalidPatientParameters, assertRequestPatient,
  isOwnedLockRelease, consumeDeliveredLockReleases,
} = require('./echart-two-patient-isolation-playwright-checks');

test('patient snapshots preserve all columns, SQL nulls, empty values and child rows', () => {
  const queries = [];
  const rows = [['11', 'existing synthetic value', null, '']];
  const state = readPatientState({rows(query) { queries.push(query); return structuredClone(rows); }}, '123');
  for (const name of ['measurements', 'deletedMeasurements', 'measurementExtensions', 'diagnoses', 'notes',
    'noteExtensions', 'noteLinks', 'noteIssues', 'issues', 'signatures', 'cpp', 'echart', 'preventions',
    'preventionExtensions', 'forms', 'drafts', 'demographicExtensions', 'demographic']) {
    assert.deepEqual(state[name], rows, `Missing complete rows for ${name}`);
  }
  assert(queries.every(query => /^SELECT \*/.test(query)), 'Counting rows cannot detect overwritten values');
  assert.match(queries.find(query => query.includes('FROM hash_audit')), /type='enc'.*id IN \(SELECT note_id/);
  assert.match(queries.find(query => query.includes('FROM measurementsExt')), /UNION SELECT originalId FROM measurementsDeleted/);
});

for (const category of Object.keys(patientStateQueries('123'))) {
  test(`isolation fails when ${category} values change with the same row count`, () => {
    const before = {[category]: [['11', 'original value', null, '']]};
    assert.doesNotThrow(() => assertPatientStateUnchanged(before, structuredClone(before), 'test write'));
    const after = {[category]: [['11', 'crossed patient value', null, '']]};
    assert.throws(() => assertPatientStateUnchanged(before, after, 'test write'), new RegExp(category));
  });
}

test('isolation fails for inserted, removed, reassigned or null-normalized rows', () => {
  const before = {notes: [['11', '123', null, '']]};
  for (const notes of [[], [['11', '123', null, ''], ['12', '123', null, '']],
    [['11', '456', null, '']], [['11', '123', '', '']]]) {
    assert.throws(() => assertPatientStateUnchanged(before, {notes}, 'save'), /notes rows changed/);
  }
  assert.throws(() => assertPatientStateUnchanged(before, {}, 'save'), /snapshot is incomplete/);
});

test('patient-state failure messages do not expose clinical row content', () => {
  assert.throws(() => assertPatientStateUnchanged({notes: [['sensitive before']]}, {notes: [['sensitive after']]}, 'save'),
    error => !error.message.includes('sensitive') && /notes rows changed/.test(error.message));
});

test('patient snapshot SQL rejects invalid keys before invoking the SQL runner', () => {
  for (const id of ['', '0', '-1', '123 OR 1=1', null, undefined, '1;DROP TABLE demographic']) {
    assert.throws(() => readPatientState({rows() { throw new Error('SQL must not run'); }}, id), /owned patient key is invalid/);
  }
});

test('invalid request fixtures preserve conflicting aliases and repeated parameters on the wire', () => {
  const variants = Object.fromEntries(invalidPatientParameters('123', '456'));
  assert.equal(new URLSearchParams(variants.missing).toString(), '');
  const aliases = new URLSearchParams(variants['conflicting aliases']);
  assert.equal(aliases.get('demographicNo'), '123');
  assert.equal(aliases.get('demographic_no'), '456');
  const repeated = new URLSearchParams(variants['conflicting repeated parameter']);
  assert.deepEqual(repeated.getAll('demographicNo'), ['123', '456']);
});

const request = (query = '', body = '') => ({url: () => `http://fixture.test/encounter/displayForms?${query}`, postData: () => body});

test('navbar patient assertion accepts the real POST body and explicit query parameters', () => {
  for (const req of [request('', 'demographicNo=123'), request('demographic_no=123'),
    request('demographic_no=123', 'demographicNo=123')]) {
    assert.doesNotThrow(() => assertRequestPatient(req, '123'));
  }
});

test('navbar patient assertion rejects missing identity and conflicting query or body patient values', () => {
  for (const req of [request(), request('demographicNo=456'), request('', 'demographicNo=123&demographicNo=456'),
    request('demographic_no=123', 'demographicNo=456')]) {
    assert.throws(() => assertRequestPatient(req, '123'), /explicitly identify only its own patient/);
  }
});

function lockRelease(overrides = {}) {
  return {url: () => 'https://fixture.invalid/carlos/CaseManagementEntry', method: () => 'POST', resourceType: () => 'ping',
    postData: () => 'method=releaseNoteLock&demographicNo=123&noteId=0&providerNo=999',
    failure: () => ({errorText: 'net::ERR_ABORTED'}), ...overrides};
}

test('the unload proof binds the exact patient, note, provider, method and ping endpoint', () => {
  assert.equal(isOwnedLockRelease(lockRelease(), '123', '0', '999'), true);
  for (const change of [
    {method: () => 'GET'}, {resourceType: () => 'xhr'}, {url: () => 'https://fixture.invalid/carlos/OtherAction'},
    ...['method=save', 'demographicNo=456', 'noteId=123', 'providerNo=other', 'demographicNo=123'].map(extra => ({
      postData: () => `${lockRelease().postData()}&${extra}`,
    })),
  ]) assert.equal(isOwnedLockRelease(lockRelease(change), '123', '0', '999'), false);
});

test('delivered unload consumption removes only the captured abandoned ping and preserves unrelated failures', () => {
  const request = lockRelease();
  const expected = {url: request.url(), resourceType: 'ping', errorText: 'net::ERR_ABORTED', navigatedAway: () => true};
  const unrelated = {...expected};
  const sameDocument = {...expected, navigatedAway: () => false};
  const unexpectedFailure = {...expected, errorText: 'net::ERR_CONNECTION_REFUSED'};
  const wrongUrl = {...expected, url: 'https://fixture.invalid/carlos/OtherAction'};
  const xhr = {...expected, resourceType: 'xhr'};
  const recorder = {requestFailures: [unrelated, expected, sameDocument, unexpectedFailure, wrongUrl, xhr]};
  consumeDeliveredLockReleases(recorder, [expected, sameDocument, unexpectedFailure, wrongUrl, xhr].map(entry => ({request, entry})));
  assert.deepEqual(recorder.requestFailures, [unrelated, sameDocument, unexpectedFailure, wrongUrl, xhr]);
});
