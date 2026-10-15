/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  patientStateQueries, readPatientState, assertPatientStateUnchanged, invalidPatientParameters, assertRequestPatient,
  isOwnedLockRelease, consumeDeliveredLockReleases, prepareVascularTrackerFixture,
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

// Stateful SQL double: evaluate the ownership predicates against current rows, including changes
// between the initial SELECT and UPDATE. Returning copies prevents fixture snapshots from aliasing DB state.
function vascularTrackerRegistry(displayOrder = '0', options = {}) {
  const registry = [
    ['Vascular Tracker', '../form/SetupForm.do?formName=VTForm&demographic_no=', 'formVTForm', displayOrder],
    ['Other form', '../form/OtherForm', 'formOther', '7'],
  ];
  const before = structuredClone(registry);
  const cleanups = [];
  const writes = [];
  const sqlLiteral = (query, column) => {
    const match = query.match(new RegExp(`BINARY ${column}='((?:''|[^'])*)'`));
    assert(match, `UPDATE must guard ${column}`);
    return match[1].replace(/''/g, "'");
  };
  const session = {
    cleanup(callback) { cleanups.push(callback); },
    sql: {
      rows(query) {
        if (query === 'SELECT * FROM encounterForm ORDER BY form_value') return structuredClone(registry);
        assert.match(query, /^SELECT form_name,form_value,form_table,hidden FROM encounterForm/);
        if (query.includes("WHERE form_name='Vascular Tracker' AND form_table='formVTForm'")) {
          return structuredClone(registry.filter(row => row[0] === 'Vascular Tracker' && row[2] === 'formVTForm'));
        }
        const key = sqlLiteral(query, 'form_value');
        return structuredClone(registry.filter(row => row[1] === key));
      },
      value(query) {
        assert.equal(cleanups.length, 1, 'Cleanup must be registered before any mutation');
        writes.push(query);
        const activation = writes.length === 1;
        if (activation && options.failBeforeUpdate) throw new Error('Activation transport failed');
        if (activation && options.beforeUpdate) options.beforeUpdate(registry);
        const next = query.match(/^UPDATE encounterForm SET hidden=(\d+) WHERE /);
        const previous = query.match(/ AND hidden=(\d+);\s*SELECT ROW_COUNT\(\)$/);
        assert(next && previous, 'Only a conditional display-order update is permitted');
        const identity = ['form_name', 'form_value', 'form_table'].map(column => sqlLiteral(query, column));
        const matching = registry.filter(row => identity.every((value, index) => row[index] === value)
          && row[3] === previous[1]);
        for (const row of matching) row[3] = next[1];
        if (activation && options.failAfterUpdate) throw new Error('Activation result lost');
        return String(matching.length);
      },
    },
  };
  return {session, registry, before, cleanups, writes};
}

test('hidden VT activation changes only its display order and restores the exact complete registry', () => {
  const fixture = vascularTrackerRegistry();
  prepareVascularTrackerFixture(fixture.session);
  assert(Number(fixture.registry[0][3]) > 0);
  assert.deepEqual(fixture.registry[0].slice(0, 3), fixture.before[0].slice(0, 3));
  assert.deepEqual(fixture.registry[1], fixture.before[1]);
  assert.equal(fixture.writes.length, 1);
  fixture.cleanups[0]();
  assert.deepEqual(fixture.registry, fixture.before);
  fixture.cleanups[0]();
  assert.equal(fixture.writes.length, 2, 'Repeated cleanup must not write again');
});

test('an already visible VT registration retains its original positive order without any writes', () => {
  const fixture = vascularTrackerRegistry('37');
  prepareVascularTrackerFixture(fixture.session);
  fixture.cleanups[0]();
  assert.deepEqual(fixture.registry, fixture.before);
  assert.deepEqual(fixture.writes, []);
});

test('VT cleanup refuses every ownership-field change without overwriting the changed row', () => {
  for (let column = 0; column < 4; column++) {
    const fixture = vascularTrackerRegistry();
    prepareVascularTrackerFixture(fixture.session);
    fixture.registry[0][column] = column === 3 ? '42' : 'Concurrent registry change';
    const drifted = structuredClone(fixture.registry);
    assert.throws(() => fixture.cleanups[0](), /ownership changed; refusing to overwrite/);
    assert.deepEqual(fixture.registry, drifted);
    assert.equal(fixture.writes.length, 1);
  }
});

test('VT cleanup is registered before activation fails without writing', () => {
  const fixture = vascularTrackerRegistry('0', {failBeforeUpdate: true});
  assert.throws(() => prepareVascularTrackerFixture(fixture.session), /Activation transport failed/);
  assert.equal(fixture.cleanups.length, 1);
  fixture.cleanups[0]();
  assert.deepEqual(fixture.registry, fixture.before);
  assert.equal(fixture.writes.length, 1);
});

test('VT cleanup restores an activation that committed before its SQL result was lost', () => {
  const fixture = vascularTrackerRegistry('0', {failAfterUpdate: true});
  assert.throws(() => prepareVascularTrackerFixture(fixture.session), /Activation result lost/);
  assert.notEqual(fixture.registry[0][3], '0');
  fixture.cleanups[0]();
  assert.deepEqual(fixture.registry, fixture.before);
  assert.equal(fixture.writes.length, 2);
});

test('VT activation detects a concurrent display-order change and leaves the new owner untouched', () => {
  const fixture = vascularTrackerRegistry('0', {beforeUpdate: rows => { rows[0][3] = '42'; }});
  assert.throws(() => prepareVascularTrackerFixture(fixture.session), /activation lost ownership/);
  assert.throws(() => fixture.cleanups[0](), /ownership changed; refusing to overwrite/);
  assert.equal(fixture.registry[0][3], '42');
  assert.equal(fixture.writes.length, 1);
});

test('VT cleanup flags changed values elsewhere in the full registry after restoring its own row', () => {
  const fixture = vascularTrackerRegistry();
  prepareVascularTrackerFixture(fixture.session);
  fixture.registry[1][0] = 'Changed other form';
  assert.throws(() => fixture.cleanups[0](), /complete encounterForm registry was not restored/);
  assert.deepEqual(fixture.registry[0], fixture.before[0]);
  assert.equal(fixture.registry[1][0], 'Changed other form');
  assert.equal(fixture.registry.length, fixture.before.length, 'Equal counts cannot prove restoration');
});
