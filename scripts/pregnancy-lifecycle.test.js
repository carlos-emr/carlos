/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {refreshPregnancy} = require('../src/main/webapp/js/pregnancy');
const {grantPregnancyModule, isPregnancyPost, withPatientPrivilege} = require('./pregnancy-lifecycle-playwright-checks');

function opener({url = 'http://localhost/CaseManagementEntry?demographicNo=101', global = '101', hidden = ['101'], closed = false} = {}) {
  const calls = [];
  return {calls, closed, demographicNo: global, location: {href: url},
    document: {querySelectorAll: () => hidden.map(value => ({value}))}, reloadNav: panel => calls.push(panel)};
}

test('pregnancy completion refreshes only the matching patient navbar', () => {
  const chart = opener();
  refreshPregnancy({opener: chart}, '101');
  assert.deepEqual(chart.calls, ['pregnancy']);
});
for (const [label, options] of [
  ['foreign URL with stale global', {url: 'http://localhost/CaseManagementEntry?demographicNo=202'}],
  ['conflicting URL aliases', {url: 'http://localhost/CaseManagementEntry?demographicNo=101&demographic_no=202'}],
  ['foreign hidden input', {hidden: ['202']}],
  ['foreign global', {global: '202'}],
  ['missing identity', {url: 'http://localhost/', global: undefined, hidden: []}],
  ['closed opener', {closed: true}],
]) test(`pregnancy completion does not refresh a ${label}`, () => {
  const chart = opener(options);
  if (label === 'missing identity') delete chart.demographicNo;
  refreshPregnancy({opener: chart}, '101');
  assert.deepEqual(chart.calls, []);
});
test('missing and cross-origin openers do not throw or close the history page', () => {
  assert.doesNotThrow(() => refreshPregnancy({}, '101'));
  assert.doesNotThrow(() => refreshPregnancy({get opener() {throw new Error('cross-origin');}}, '101'));
});

function fixture(initial = {}) {
  const rows = new Map(Object.entries(initial).map(([key, value]) => [key, [...value]]));
  const cleanups = [];
  const statements = [];
  const object = sql => /objectName='([^']+)'/.exec(sql)?.[1];
  const sql = {
    rows(query) { const row = rows.get(object(query)); return row ? [[...row]] : []; },
    execute(query) {
      assert.ok(cleanups.length, 'cleanup must be installed before privilege mutation');
      statements.push(query);
      if (query.startsWith('INSERT')) {
        const match = /VALUES \('999001','([^']+)','([^']+)',([01]),'999001'\)/.exec(query);
        assert.ok(match);
        assert.ok(!rows.has(match[1]));
        rows.set(match[1], [match[2], match[3], '999001']);
      } else if (query.startsWith('UPDATE')) {
        const row = rows.get(object(query));
        const wanted = /SET privilege='([^']*)'/.exec(query)[1];
        const expected = /AND privilege='([^']*)'/.exec(query)[1];
        if (row?.[0] === expected) row[0] = wanted;
      } else if (query.startsWith('DELETE')) {
        const row = rows.get(object(query));
        const expected = /AND privilege='([^']*)'/.exec(query)[1];
        if (row?.[0] === expected && row[2] === '999001') rows.delete(object(query));
      } else assert.fail(`Unexpected fixture mutation: ${query}`);
    },
  };
  return {sql, rows, statements, provider: '999001', cleanup: callback => cleanups.push(callback),
    restore: () => {for (const callback of [...cleanups].reverse()) callback();}};
}
test('temporary provider grants restore complete preexisting rows and remove owned inserts', () => {
  const s = fixture({_pregnancy: ['r', '7', 'other-provider']});
  grantPregnancyModule(s);
  assert.deepEqual(s.rows.get('_pregnancy'), ['x', '7', 'other-provider']);
  assert.deepEqual(s.rows.get('_newCasemgmt.pregnancy'), ['x', '0', '999001']);
  s.restore();
  assert.deepEqual([...s.rows], [['_pregnancy', ['r', '7', 'other-provider']]]);
});
test('already authorized provider grants remain untouched', () => {
  const s = fixture({_pregnancy: ['x', '2', '999001'], '_newCasemgmt.pregnancy': ['x', '3', '999001']});
  grantPregnancyModule(s);
  s.restore();
  assert.deepEqual(s.statements, []);
});
test('privilege cleanup refuses ownership drift instead of overwriting another change', () => {
  const s = fixture();
  grantPregnancyModule(s);
  s.rows.set('_newCasemgmt.pregnancy', ['x', '0', 'another-provider']);
  assert.throws(s.restore, /ownership changed/);
  assert.deepEqual(s.rows.get('_newCasemgmt.pregnancy'), ['x', '0', 'another-provider']);
});
test('failed activation still has cleanup registered before its first write', () => {
  const s = fixture();
  s.sql.execute = () => {throw new Error('synthetic failure');};
  assert.throws(() => grantPregnancyModule(s), /synthetic failure/);
  assert.doesNotThrow(s.restore);
  assert.equal(s.rows.size, 0);
});
test('native response matcher binds the action, method, operation and patient', () => {
  const response = (url, verb, body) => ({url: () => url, request: () => ({method: () => verb, postData: () => body})});
  const url = 'http://localhost/carlos/Pregnancy';
  assert.equal(isPregnancyPost(response(url, 'POST', 'demographicNo=101&method=create'), url, '101', 'create'), true);
  for (const [route, verb, body] of [[url, 'GET', 'demographicNo=101&method=create'],
    [url, 'POST', 'demographicNo=202&method=create'], [url, 'POST', 'demographicNo=101&method=complete'],
    ['http://localhost/carlos/Episode', 'POST', 'demographicNo=101&method=create']]) {
    assert.equal(isPregnancyPost(response(route, verb, body), url, '101', 'create'), false);
  }
});

test('patient-specific read-only probe restores its grant after success and deliberate rejection', async () => {
  for (const fail of [false, true]) {
    const s = fixture();
    const run = () => withPatientPrivilege(s, '_pregnancy$101', 'r', async () => {
      assert.deepEqual(s.rows.get('_pregnancy$101'), ['r', '1', '999001']);
      if (fail) throw new Error('probe failed');
    });
    if (fail) await assert.rejects(run, /probe failed/); else await run();
    assert.equal(s.rows.size, 0);
    assert.doesNotThrow(s.restore);
  }
});
test('patient restriction probe refuses a preexisting row or unrelated object', async () => {
  const s = fixture({'_demographic$101': ['o', '1', '999001']});
  await assert.rejects(() => withPatientPrivilege(s, '_demographic$101', 'o', async () => {}), /already has/);
  await assert.rejects(() => withPatientPrivilege(s, '_admin', 'o', async () => {}), /outside/);
  assert.deepEqual(s.statements, []);
});
test('patient restriction cleanup detects ownership drift without overwriting it', async () => {
  const s = fixture();
  await assert.rejects(() => withPatientPrivilege(s, '_demographic$101', 'o', async () => {
    s.rows.set('_demographic$101', ['o', '2', 'another-provider']);
  }), /ownership changed/);
  assert.deepEqual(s.rows.get('_demographic$101'), ['o', '2', 'another-provider']);
});
