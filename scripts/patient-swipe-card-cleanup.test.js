/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {registerSwipeSupportCleanup, workflow} = require('./patient-swipe-card-search-playwright-checks');
const {cleanupOwnedWorkflow} = require('./lib/workflow-session');

const MARKER = 'FAKE-PW0123456789abcdef';

function fixture() {
  const patients = new Map([['42', MARKER], ['43', MARKER], ['44', 'OtherPatient']]);
  const archives = new Map();
  const custom = new Map();
  const callbacks = [], queries = [], events = [];
  const browser = {connected: true, isConnected() {return this.connected;}, async close() {this.connected = false;}};
  let beforeExecute, afterExecute, rowOverride;
  const owned = () => patients.get('42')?.toLowerCase() === MARKER.toLowerCase();
  const sql = {
    value(query) {
      queries.push(query);
      if (/^SELECT COUNT\(\*\) FROM demographic WHERE hin=/.test(query)) return '0';
      if (/^SELECT GROUP_CONCAT\(demographic_no\)/.test(query)) return '42';
      if (/^SELECT COUNT\(\*\) FROM demographic WHERE demographic_no=42/.test(query)) {
        return String(Number(query.includes('last_name=') ? owned() : patients.has('42')));
      }
      if (/^SELECT \(SELECT COUNT\(\*\)/.test(query)) return '0'; // Generic workflow support tables are empty.
      throw new Error('Unexpected value query: ' + query);
    },
    rows(query) {
      queries.push(query);
      if (rowOverride) {
        const override = rowOverride(query);
        if (override !== undefined) return override;
      }
      const patient = /WHERE demographic_no=(\d+)\b/.exec(query)?.[1];
      assert(patient, 'Every child read must use an exact patient key');
      if (/^SELECT id,demographic_no FROM demographicExtArchive/.test(query)) {
        return [...archives.values()].filter(row => row.patient === patient).map(row => [row.id, row.patient]);
      }
      if (/^SELECT demographic_no FROM demographiccust/.test(query)) {
        return custom.has(patient) ? [[patient]] : [];
      }
      throw new Error('Unexpected rows query: ' + query);
    },
    execute(query) {
      queries.push(query);
      beforeExecute?.(query);
      if (/^UPDATE demographic SET hin=/.test(query)) {
        events.push(['first demographic write', callbacks.length]);
        return;
      }
      for (const statement of query.split(';').map(s => s.trim()).filter(Boolean)) {
        const table = /^DELETE FROM (\w+)/.exec(statement)?.[1];
        assert(table, 'Only scoped child/parent deletes are expected');
        const clauses = statement.split(/\s+AND EXISTS (?=\()/);
        const outer = clauses[0];
        const patient = /(?:demographic_no|demographicNo)=(\d+)\b/.exec(outer)?.[1];
        assert.equal(patient, '42', 'Cleanup must never select another patient');
        // Model the SQL predicates rather than treating the mere DELETE as success.
        if (table === 'demographicExtArchive' || table === 'demographiccust') {
          assert.equal(clauses.length, 2, 'Every swipe support delete needs a separate parent ownership guard');
          assert.equal(clauses[1], `(SELECT 1 FROM demographic WHERE demographic_no=42 AND last_name='${MARKER}')`,
            'The parent guard must retain both the patient key and original marker');
          if (!owned()) continue;
        }
        if (table === 'demographicExtArchive') {
          const match = /^DELETE FROM demographicExtArchive\s+WHERE id IN \(([\d,]+)\) AND demographic_no=42$/.exec(outer);
          assert(match, 'Archive deletes need captured primary keys and an independent outer patient predicate');
          const ids = match[1].split(',');
          for (const id of ids) if (archives.get(id)?.patient === patient) archives.delete(id);
        } else if (table === 'demographiccust') {
          assert.match(outer, /^DELETE FROM demographiccust\s+WHERE demographic_no=42$/);
          custom.delete(patient);
        }
        else if (table === 'demographic') {if (owned()) patients.delete(patient);}
      }
      afterExecute?.(query);
    },
    dispose() {},
  };
  const session = {sql, patient: '42', marker: MARKER, context: {browser: () => browser},
    cleanup(fn) {callbacks.push(fn);}};
  return {session, sql, patients, archives, custom, browser, callbacks, queries, events,
    beforeExecute(fn) {beforeExecute = fn;}, afterExecute(fn) {afterExecute = fn;}, rows(fn) {rowOverride = fn;},
    register() {registerSwipeSupportCleanup(session);},
    async cleanup() {await cleanupOwnedWorkflow({browser, sql, patient: session.patient,
      marker: session.marker, cleanups: callbacks});},
    deletes() {return queries.filter(query => /^DELETE FROM/.test(query));},
  };
}

test('swipe cleanup removes update support rows before the owned parent', async () => {
  const f = fixture(); f.register();
  f.archives.set('101', {id: '101', patient: '42', archiveId: '700'}); f.custom.set('42', 'owned');
  await f.cleanup();
  assert.equal(f.archives.size, 0); assert.equal(f.custom.size, 0); assert.equal(f.patients.has('42'), false);
  assert.equal(f.patients.get('43'), MARKER);
});

test('the maximum signed integer archive identity is removed without numeric rounding', async () => {
  const f = fixture(); f.register();
  f.archives.set('2147483647', {id: '2147483647', patient: '42'});
  await f.cleanup();
  assert.equal(f.archives.size, 0); assert.equal(f.patients.has('42'), false);
});

for (const patient of ['0', '-1', '2147483648', '42 OR 1=1']) test(`invalid patient identity ${patient} refuses before any SQL`, () => {
  const f = fixture(); f.session.patient = patient;
  assert.throws(() => f.register(), /Invalid swipe fixture ownership/);
  assert.equal(f.queries.length, 0); assert.equal(f.callbacks.length, 0);
});

test('foreign and concurrently added patients survive even with the same marker or archiveId', async () => {
  const f = fixture();
  f.archives.set('100', {id: '100', patient: '43', archiveId: '700'}); f.custom.set('43', 'pre-existing');
  f.register();
  f.archives.set('101', {id: '101', patient: '42', archiveId: '700'}); f.custom.set('42', 'owned');
  f.archives.set('102', {id: '102', patient: '44', archiveId: '700'}); f.custom.set('44', 'concurrent');
  await f.cleanup();
  assert.deepEqual([...f.archives.keys()], ['100', '102']);
  assert.deepEqual([...f.custom], [['43', 'pre-existing'], ['44', 'concurrent']]);
  assert.deepEqual([...f.patients.keys()], ['43', '44']);
});

for (const table of ['archive', 'custom']) test(`pre-existing owned-key ${table} rows are preserved and retain the parent`, async () => {
  const f = fixture();
  if (table === 'archive') f.archives.set('100', {id: '100', patient: '42'}); else f.custom.set('42', 'pre-existing');
  assert.throws(() => f.register(), /pre-existing support/);
  assert.equal(f.callbacks.length, 1);
  await assert.rejects(f.cleanup(), /baseline is unconfirmed/);
  assert.equal(f.deletes().length, 0); assert(f.patients.has('42'));
  assert(table === 'archive' ? f.archives.has('100') : f.custom.has('42'));
});

test('unreadable baseline registers a retaining cleanup before any workflow write', async () => {
  const f = fixture(); f.rows(() => {throw new Error('baseline read failed');});
  assert.throws(() => f.register(), /baseline read failed/);
  f.rows(undefined);
  await assert.rejects(f.cleanup(), /baseline is unconfirmed/);
  assert.equal(f.deletes().length, 0); assert(f.patients.has('42'));
});

test('lost fixture ownership refuses all child and parent deletion', async () => {
  const f = fixture(); f.register();
  f.archives.set('101', {id: '101', patient: '42'}); f.custom.set('42', 'owned');
  f.patients.set('42', 'DifferentPatient');
  await assert.rejects(f.cleanup(), /ownership changed/);
  assert.equal(f.deletes().length, 0); assert(f.archives.has('101')); assert(f.custom.has('42'));
});

test('an unconfirmed browser close retains support rows and the parent', async () => {
  const f = fixture(); f.register(); f.custom.set('42', 'owned');
  f.browser.close = async () => {throw new Error('browser teardown failed');};
  await assert.rejects(f.cleanup(), /browser close is unconfirmed/);
  assert.equal(f.deletes().length, 0); assert(f.custom.has('42')); assert(f.patients.has('42'));
});

for (const id of ['0', '-1', '01', '2147483648', '7 OR 1=1']) test(`invalid archive identity ${id} refuses every delete`, async () => {
  const f = fixture(); f.register(); f.archives.set(id, {id, patient: '42'}); f.custom.set('42', 'owned');
  await assert.rejects(f.cleanup(), /Invalid owned swipe extension archive/);
  assert.equal(f.deletes().length, 0); assert(f.archives.has(id)); assert(f.custom.has('42')); assert(f.patients.has('42'));
});

for (const [label, rows] of [
  ['duplicate', [['101', '42'], ['101', '42']]],
  ['malformed', [['101']]],
  ['foreign patient', [['101', '43']]],
]) test(`${label} archive evidence refuses every delete`, async () => {
  const f = fixture(); f.register(); f.archives.set('101', {id: '101', patient: '42'});
  f.rows(query => query.startsWith('SELECT id,demographic_no FROM demographicExtArchive') ? rows : undefined);
  await assert.rejects(f.cleanup(), /Invalid owned swipe extension archive/);
  assert.equal(f.deletes().length, 0); assert(f.archives.has('101')); assert(f.patients.has('42'));
});

test('a custom-row identity from another patient cannot authorize deletion', async () => {
  const f = fixture(); f.register(); f.custom.set('42', 'owned');
  f.rows(query => query.startsWith('SELECT demographic_no FROM demographiccust') ? [['43']] : undefined);
  await assert.rejects(f.cleanup(), /Invalid owned swipe custom-row/);
  assert.equal(f.deletes().length, 0); assert(f.custom.has('42')); assert(f.patients.has('42'));
});

test('ownership changed between the child read and DELETE preserves all support rows', async () => {
  const f = fixture(); f.register();
  f.archives.set('101', {id: '101', patient: '42'}); f.custom.set('42', 'owned');
  f.beforeExecute(() => {f.patients.set('42', 'ReplacementPatient');});
  await assert.rejects(f.cleanup(), /ownership changed/);
  assert(f.archives.has('101')); assert(f.custom.has('42')); assert(f.patients.has('42'));
});

test('a child reassigned after capture is preserved by its patient predicate', async () => {
  const f = fixture(); f.register(); f.archives.set('101', {id: '101', patient: '42'});
  f.beforeExecute(() => {f.archives.get('101').patient = '43';});
  await f.cleanup();
  assert.equal(f.archives.get('101').patient, '43'); assert(f.patients.has('43'));
});

test('a new owned row after capture is retained and fails cleanup instead of being swept away', async () => {
  const f = fixture(); f.register(); f.archives.set('101', {id: '101', patient: '42'});
  f.afterExecute(query => {if (query.startsWith('DELETE FROM demographicExtArchive')) f.archives.set('102', {id: '102', patient: '42'});});
  await assert.rejects(f.cleanup(), /were not removed/);
  assert(f.archives.has('102')); assert(f.patients.has('42'));
});

test('a silently ineffective child delete cannot remove the parent or report success', async () => {
  const f = fixture(); f.register(); f.custom.set('42', 'owned');
  const execute = f.sql.execute;
  f.sql.execute = query => {if (!query.startsWith('DELETE FROM demographiccust')) execute(query);};
  await assert.rejects(f.cleanup(), /were not removed/);
  assert(f.custom.has('42')); assert(f.patients.has('42'));
});

test('workflow registers support cleanup before its first demographic write', async () => {
  const f = fixture();
  f.session.schedule = {locator: () => ({first() {return {};}})};
  f.session.popup = async () => {throw new Error('stop before browser UI');};
  await assert.rejects(workflow(f.session), /stop before browser UI/);
  assert.deepEqual(f.events, [['first demographic write', 1]]);
  assert.equal(f.callbacks.length, 1);
});
