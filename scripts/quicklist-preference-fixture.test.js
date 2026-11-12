/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { trackQuickListPreferences } = require('./lib/quicklist-preference-fixture');

const oldStamp = '2024-04-02 22:33:34.000000';
const newStamp = '2026-10-25 12:56:02.000000';
const row = (id, name = 'List1', stamp = newStamp, provider = '999998') => [id, provider, name, stamp, '0'];

function fixture(before = []) {
  let current = structuredClone(before);
  const mutations = [];
  const cleanups = [];
  const sql = {
    rows() { return structuredClone(current); },
    value() { return '20'; },
    execute(statement) { mutations.push(statement); },
  };
  const tracker = trackQuickListPreferences({ sql, provider: '999998', cleanup(fn) { cleanups.push(fn); } },
    ['List1', 'List2']);
  assert.equal(cleanups.length, 1, 'Cleanup must be registered before the caller can start its read');
  return {
    mutations,
    observe(values) { current = structuredClone(values); tracker.observe(); },
    change(values) { current = structuredClone(values); },
    cleanup() { cleanups[0](); },
  };
}

function refusesWithoutMutation(before, observed, current = observed) {
  const f = fixture(before);
  f.observe(observed);
  f.change(current);
  assert.throws(() => f.cleanup());
  assert.deepEqual(f.mutations, [], 'A failed ownership/drift preflight must not execute any SQL mutation');
}

test('unchanged preferences cause no SQL mutations', () => {
  const f = fixture([row('10', 'List1', oldStamp)]);
  f.observe([row('10', 'List1', oldStamp)]);
  f.cleanup();
  assert.deepEqual(f.mutations, []);
});

test('a timestamp changed after observation is retained without SQL mutations', () => {
  refusesWithoutMutation([row('10', 'List1', oldStamp)], [row('10')], [row('10', 'List1', '2026-10-25 13:00:00.000000')]);
});

test('a foreign provider row is never adopted for deletion', () => {
  refusesWithoutMutation([], [row('21', 'List1', newStamp, '888888')]);
});

test('a foreign preference key is never adopted for deletion', () => {
  refusesWithoutMutation([], [row('21', 'Unrelated')]);
});

test('a preexisting foreign ID moved into the target key is never adopted', () => {
  refusesWithoutMutation([], [row('19')]);
});

test('a tracked ID moved to another tracked key is retained', () => {
  refusesWithoutMutation([row('10', 'List1', oldStamp)], [row('10', 'List2')]);
});

test('a missing original ID cannot be replaced by a newly observed ID', () => {
  refusesWithoutMutation([row('10', 'List1', oldStamp)], [row('21')]);
});

test('unexpected duplicate rows for a new key are retained', () => {
  refusesWithoutMutation([], [row('21'), row('22')]);
});

test('duplicate IDs across keys are retained', () => {
  refusesWithoutMutation([], [row('21'), row('21', 'List2')]);
});

test('all keys are preflighted before a valid earlier key can be modified', () => {
  const before = [row('10', 'List1', oldStamp), row('11', 'List2', oldStamp)];
  const observed = [row('10'), row('11', 'List2')];
  refusesWithoutMutation(before, observed, [row('10'), row('11', 'List2', newStamp, '888888')]);
});

test('unobserved writes are retained rather than guessed as fixture-owned', () => {
  const f = fixture();
  f.change([row('21')]);
  assert.throws(() => f.cleanup());
  assert.deepEqual(f.mutations, []);
});

function statefulFixture(before) {
  let current = structuredClone(before);
  const mutations = [];
  let cleanup;
  const sql = {
    rows() { return structuredClone(current); },
    value(statement) {
      if (statement.includes('MAX(id)')) return '20';
      const ids = statement.match(/id IN \(([0-9,]+)\)/)[1].split(',');
      return String(current.filter(value => ids.includes(value[0])).length);
    },
    execute(statement) {
      mutations.push(statement);
      const text = statement.replace(/\s+/g, ' ');
      const match = text.match(/WHERE id=([0-9]+) AND providerNo='([0-9]+)' AND quickListName='([^']+)' AND lastUsed <=> (NULL|'([^']*)')$/);
      assert.ok(match, 'Every restoration must retain the exact ID, owner, key and observed timestamp predicates');
      const index = current.findIndex(value => value[0] === match[1] && value[1] === match[2]
        && value[2] === match[3] && (match[4] === 'NULL' ? value[4] === '1' : value[4] === '0' && value[3] === match[5]));
      if (index < 0) return;
      if (text.startsWith('DELETE ')) current.splice(index, 1);
      else {
        const original = text.match(/^UPDATE quickListUser SET lastUsed=(NULL|'([^']*)') WHERE /);
        assert.ok(original);
        current[index][3] = original[1] === 'NULL' ? '' : original[2];
        current[index][4] = original[1] === 'NULL' ? '1' : '0';
      }
    },
  };
  const tracker = trackQuickListPreferences({ sql, provider: '999998', cleanup(fn) { cleanup = fn; } }, ['List1']);
  return { observe(values) { current = structuredClone(values); tracker.observe(); },
    cleanup() { cleanup(); }, current() { return current; }, mutations };
}

test('an existing preference restores its exact prior timestamp', () => {
  const before = [row('10', 'List1', oldStamp)];
  const f = statefulFixture(before);
  f.observe([row('10')]);
  f.cleanup();
  assert.deepEqual(f.current(), before);
  assert.equal(f.mutations.length, 1);
});

test('a nullable prior preference restores SQL NULL', () => {
  const before = [['10', '999998', 'List1', '', '1']];
  const f = statefulFixture(before);
  f.observe([row('10')]);
  f.cleanup();
  assert.deepEqual(f.current(), before);
  assert.equal(f.mutations.length, 1);
});

test('a proved new preference is deleted and its ID verified absent', () => {
  const f = statefulFixture([]);
  f.observe([row('21')]);
  f.cleanup();
  assert.deepEqual(f.current(), []);
  assert.equal(f.mutations.length, 1);
});
