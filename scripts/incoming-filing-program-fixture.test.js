/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const test = require('node:test');
const {prepareIncomingFilingProgram} = require('./lib/incoming-filing-program-fixture');

function fixture({assigned = true, failSnapshot = false, ignoreDelete = false} = {}) {
  const marker = 'FAKE-PW1234567890abcdef', patient = '123', provider = '999998';
  const tables = {program: assigned ? [{id: '10', name: 'Existing program', programStatus: 'active'}] : [],
    program_provider: assigned ? [{id: '20', program_id: '10', provider_no: provider}] : [], admission: []};
  const keys = {program: 'id', program_provider: 'id', admission: 'am_id'};
  const callbacks = [], writes = [];
  let owned = true, next = 30, external = false, lostSnapshot = false, changedAtDelete = false;
  const hash = row => createHash('sha256').update(JSON.stringify(row)).digest('hex');
  function target(query) {
    const table = /FROM `([^`]+)`/.exec(query)?.[1];
    const id = /WHERE `(?:id|am_id)`=(\d+)/.exec(query)?.[1];
    return {table, id, row: tables[table]?.find(row => row[keys[table]] === id)};
  }
  const sql = {
    rows(query) {
      if (query.includes('ORDER BY ORDINAL_POSITION')) return [[keys[/TABLE_NAME='([^']+)'/.exec(query)[1]]]];
      if (query.includes('information_schema.COLUMNS')) {
        const refs = [['admission', 'program_id', 'legacy'], ['program_provider', 'program_id', 'legacy'],
          ['admission_dependents', 'admissionId', 'legacy'], ['program_notes', 'programName', 'legacy']];
        if (query.includes("REFERENCED_TABLE_NAME='program_provider'")) refs.push(['membership_audit', 'parent_id', 'declared']);
        return refs;
      }
      throw new Error('Unexpected read: ' + query);
    },
    value(query) {
      if (query.startsWith('INSERT')) {
        assert.ok(callbacks.length, 'cleanup must be registered before the first database mutation');
        writes.push(query);
        const table = /INSERT INTO (\w+)/.exec(query)[1], id = String(next++);
        const program = tables.program[0]?.id || id;
        const row = table === 'program' ? {id, name: `${marker}-incoming`, programStatus: 'active'}
          : table === 'program_provider' ? {id, program_id: program, provider_no: provider}
            : {am_id: id, client_id: patient, program_id: program, provider_no: provider, admission_status: 'current'};
        tables[table].push(row);
        if (failSnapshot && table === 'admission') lostSnapshot = true;
        return id;
      }
      if (query.includes('FROM demographic')) return owned ? '1' : '0';
      if (query.includes('FROM admission a JOIN')) return '1';
      if (query.includes('FROM admission WHERE client_id=')) return String(tables.admission.length);
      if (query.includes('FROM program p JOIN')) return assigned ? '10' : '';
      if (query.includes('FROM program WHERE name=')) return '0';
      if (query.includes('FROM Facility')) return '1';
      const {table, row} = target(query);
      if (query.startsWith('SELECT SHA2')) {
        if (lostSnapshot && table === 'admission') return '';
        return row ? hash(row) : '';
      }
      if (query.startsWith('SELECT COUNT(*) FROM `')) {
        if (query.includes('WHERE `id`=') || query.includes('WHERE `am_id`=')) return row ? '1' : '0';
        return external ? '1' : '0';
      }
      throw new Error('Unexpected scalar: ' + query);
    },
    execute(query) {
      writes.push(query);
      const {table, id, row} = target(query);
      assert.ok(query.startsWith('DELETE FROM '));
      const digest = /=\s*'([0-9a-f]{64})'/.exec(query)?.[1];
      assert.ok(digest, 'deletion must use the exact captured full-row digest');
      if (changedAtDelete) row.changed = true;
      if (!ignoreDelete && row && hash(row) === digest) tables[table] = tables[table].filter(row => row[keys[table]] !== id);
    },
  };
  const session = {marker, patient, provider, sql, cleanup(fn) {callbacks.push(fn);}};
  return {session, callbacks, writes, tables, changedOwner() {owned = false;}, addReference() {external = true;},
    concurrentChange() {changedAtDelete = true;}};
}

test('reuses an assigned program, owns only the new admission and preserves every existing program/link byte', () => {
  const env = fixture(), baseline = JSON.stringify([env.tables.program, env.tables.program_provider]);
  const result = prepareIncomingFilingProgram(env.session);
  assert.equal(result.program, '10');
  assert.equal(env.writes.filter(sql => sql.startsWith('INSERT')).length, 1);
  result.cleanup(); result.cleanup();
  assert.deepEqual(env.tables.admission, []);
  assert.equal(JSON.stringify([env.tables.program, env.tables.program_provider]), baseline);
});

test('fallback owns the private program and link, then deletes admission, link and program in reverse order', () => {
  const env = fixture({assigned: false});
  const result = prepareIncomingFilingProgram(env.session);
  assert.equal(env.writes.filter(sql => sql.startsWith('INSERT')).length, 3);
  result.cleanup();
  assert.deepEqual(env.writes.filter(sql => sql.startsWith('DELETE')).map(sql => /DELETE FROM `([^`]+)`/.exec(sql)[1]),
    ['admission', 'program_provider', 'program']);
  assert.ok(Object.values(env.tables).every(rows => rows.length === 0));
});

for (const change of ['patient', 'admission', 'program', 'link', 'external reference']) {
  test(`cleanup refuses ${change} changes before deleting any row`, () => {
    const env = fixture({assigned: false});
    const result = prepareIncomingFilingProgram(env.session);
    if (change === 'patient') env.changedOwner();
    else if (change === 'external reference') env.addReference();
    else env.tables[change === 'link' ? 'program_provider' : change][0].changed = true;
    const baseline = JSON.stringify(env.tables);
    assert.throws(() => result.cleanup());
    assert.equal(JSON.stringify(env.tables), baseline);
    assert.equal(env.writes.filter(sql => sql.startsWith('DELETE')).length, 0);
  });
}

test('uncertain snapshot after successful insert retains rows and fails the registered cleanup', () => {
  const env = fixture({failSnapshot: true});
  assert.throws(() => prepareIncomingFilingProgram(env.session), /snapshot/);
  assert.equal(env.callbacks.length, 1); assert.equal(env.tables.admission.length, 1);
  assert.throws(() => env.callbacks[0](), /Incomplete/);
  assert.equal(env.writes.filter(sql => sql.startsWith('DELETE')).length, 0);
});

for (const ignored of [true, false]) {
  test(`cleanup detects ${ignored ? 'ignored deletion' : 'a concurrent row change'} without deleting unrelated rows`, () => {
    const env = fixture({ignoreDelete: ignored});
    const result = prepareIncomingFilingProgram(env.session);
    if (!ignored) env.concurrentChange();
    assert.throws(() => result.cleanup(), /refused or incomplete/);
    assert.equal(env.tables.admission.length, 1);
    assert.equal(env.tables.program.length, 1); assert.equal(env.tables.program_provider.length, 1);
  });
}

test('reverse callback order removes filing before its admission and keeps an unrelated admission unchanged', () => {
  const env = fixture(); prepareIncomingFilingProgram(env.session);
  let filingRemoved = false;
  env.session.cleanup(() => {filingRemoved = true; assert.equal(env.tables.admission.length, 1);});
  for (const callback of [...env.callbacks].reverse()) callback();
  assert.equal(filingRemoved, true); assert.equal(env.tables.admission.length, 0);
  const preexisting = fixture(); preexisting.tables.admission.push({am_id: '9', client_id: '123', program_id: '10'});
  assert.throws(() => prepareIncomingFilingProgram(preexisting.session), /already has admissions/);
  preexisting.callbacks[0]();
  assert.equal(preexisting.tables.admission.length, 1); assert.deepEqual(preexisting.writes, []);
});
