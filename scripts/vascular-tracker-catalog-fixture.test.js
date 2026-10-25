/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { vascularTrackerCatalogFixture, readDefinitions } = require('./lib/vascular-tracker-catalog-fixture');
const { sqlString } = require('./lib/playwright-harness');
const definition = {metadata: ['WTVT', 'Weight', 'VT weight', 'VT in kg'],
  rule: ['Numeric 0 to 300', null, 300, 0, 0, null, 1, null, null]};
const oldType = [1, 'WT', 'Existing weight', 'NULL', 'in kg', '5', '2020-01-01 00:00:00'];
const oldRule = [5, 'Existing numeric', null, 300, 0, 0, null, 1, null, null];
const newType = [2, ...definition.metadata, '7', '2026-10-25 00:52:43'];
const newRule = [7, ...definition.rule];

function database() {
  const state = {measurementType: [structuredClone(oldType)], validations: [structuredClone(oldRule)]};
  const before = structuredClone(state);
  const writes = [];
  let reads = 0;
  const sql = {
    state, before, writes, clinicalReference: false, validationReference: false, beforeDelete: null,
    rows(query) {
      reads++;
      const table = / FROM (measurementType|validations) ORDER BY id$/.exec(query)?.[1];
      assert.ok(table, `Unexpected snapshot query: ${query}`);
      return state[table].map(row => [String(row[0]), JSON.stringify(row)]);
    },
    value(query) {
      if (/^SELECT COUNT\(\*\) FROM measurements(?:Deleted)? WHERE/.test(query)) return sql.clinicalReference ? '1' : '0';
      if (/^SELECT \(SELECT COUNT\(\*\) FROM measurementType WHERE/.test(query)) return sql.validationReference ? '1' : '0';
      const deletion = /^DELETE FROM (measurementType|validations) WHERE id=(\d+)/.exec(query);
      assert.ok(deletion, `Unexpected mutation: ${query}`);
      writes.push(query);
      const [, table, id] = deletion;
      if (sql.beforeDelete) { const mutation = sql.beforeDelete; sql.beforeDelete = null; mutation(); }
      const index = state[table].findIndex(row => String(row[0]) === id);
      if (index < 0 || !query.includes(`BINARY ${sqlString(JSON.stringify(state[table][index]))}`)) return '0';
      state[table].splice(index, 1);
      return '1';
    },
    readCount() { return reads; },
    add() { state.measurementType.push(structuredClone(newType)); state.validations.push(structuredClone(newRule)); },
  };
  return sql;
}

// The real XML contains self-closing empty descriptions, entity escapes and repeated rules.
test('bundled VT catalog parses all declarations and preserves decoded instruction text', () => {
  const definitions = readDefinitions();
  assert.deepEqual(require('./fixtures/vascular-tracker-catalog.json'), definitions,
    'The script-only runtime catalog must exactly match the production VT form definition');
  assert.equal(definitions.length, 55);
  assert.ok(definitions.some(def => def.metadata[0] === 'BP' && def.metadata[3].includes('Target:<130/80')));
  assert.ok(definitions.some(def => def.metadata[0] === 'MI' && def.metadata[2] === ''));
});

test('an unopened VT form does not read or modify shared catalog data', () => {
  const sql = database();
  vascularTrackerCatalogFixture(sql, [definition]).cleanup();
  assert.equal(sql.readCount(), 0);
  assert.deepEqual(sql.state, sql.before);
});

test('first-use VT definitions are removed in dependency order while every preexisting value survives', () => {
  const sql = database();
  const fixture = vascularTrackerCatalogFixture(sql, [definition]);
  fixture.begin(); sql.add(); fixture.capture(); fixture.cleanup();
  assert.deepEqual(sql.state, sql.before);
  assert.equal(sql.writes.length, 2);
  assert.match(sql.writes[0], /^DELETE FROM measurementType WHERE id=2 AND BINARY JSON_ARRAY/);
  assert.match(sql.writes[1], /^DELETE FROM validations WHERE id=7 AND BINARY JSON_ARRAY/);
  fixture.cleanup();
  assert.equal(sql.writes.length, 2, 'Repeated cleanup must not delete baseline rows');
});

test('a reused preexisting validation is retained when its new VT type is removed', () => {
  const sql = database();
  const fixture = vascularTrackerCatalogFixture(sql, [definition]);
  fixture.begin(); sql.state.measurementType.push([...newType.slice(0, 5), '5', newType[6]]);
  fixture.capture(); fixture.cleanup();
  assert.deepEqual(sql.state, sql.before);
  assert.equal(sql.writes.length, 1);
});

test('a failed form setup can clean its partial known initialization without a completed capture', () => {
  const sql = database();
  const fixture = vascularTrackerCatalogFixture(sql, [definition]);
  fixture.begin(); sql.state.validations.push(structuredClone(newRule));
  fixture.cleanup();
  assert.deepEqual(sql.state, sql.before);
  assert.equal(sql.writes.length, 1);
});

for (const [name, change, error] of [
  ['a changed preexisting rule', sql => { sql.state.validations[0][1] = 'Other owner'; }, /preexisting VT catalog row changed/],
  ['an unrelated new type', sql => { sql.state.measurementType[1][1] = 'UNRELATED'; }, /unexpected measurement/],
  ['an unrelated new rule', sql => { sql.state.validations[1][1] = 'Unrelated validation'; }, /unexpected validation/],
]) test(`catalog ownership rejects ${name} before deleting any row`, () => {
  const sql = database(); const fixture = vascularTrackerCatalogFixture(sql, [definition]);
  fixture.begin(); sql.add(); change(sql);
  const changed = structuredClone(sql.state);
  assert.throws(() => fixture.cleanup(), error);
  assert.deepEqual(sql.state, changed); assert.equal(sql.writes.length, 0);
});

test('a captured new row changed by another owner is retained without deleting its rule', () => {
  const sql = database(); const fixture = vascularTrackerCatalogFixture(sql, [definition]);
  fixture.begin(); sql.add(); fixture.capture(); sql.state.measurementType[1][6] = '2030-01-01 00:00:00';
  const changed = structuredClone(sql.state);
  assert.throws(() => fixture.cleanup(), /Owned VT catalog definitions changed/);
  assert.deepEqual(sql.state, changed); assert.equal(sql.writes.length, 0);
});

for (const reference of ['clinicalReference', 'validationReference']) test(`cleanup retains definitions with ${reference}`, () => {
  const sql = database(); const fixture = vascularTrackerCatalogFixture(sql, [definition]);
  fixture.begin(); sql.add(); fixture.capture(); sql[reference] = true;
  const before = structuredClone(sql.state);
  assert.throws(() => fixture.cleanup(), /references|referenced outside/);
  assert.deepEqual(sql.state, before); assert.equal(sql.writes.length, 0);
});

test('the exact-row DELETE guard retains a row replaced after the ownership preflight', () => {
  const sql = database(); const fixture = vascularTrackerCatalogFixture(sql, [definition]);
  fixture.begin(); sql.add(); fixture.capture();
  sql.beforeDelete = () => { sql.state.measurementType[1][3] = 'Changed concurrently'; };
  assert.throws(() => fixture.cleanup(), /lost exact row ownership/);
  assert.equal(sql.state.measurementType[1][3], 'Changed concurrently');
  assert.equal(sql.state.validations.length, 2);
});

test('matching VT metadata cannot claim an existing but incompatible linked clinic rule', () => {
  const sql = database(); sql.state.validations[0][3] = 999;
  const fixture = vascularTrackerCatalogFixture(sql, [definition]);
  fixture.begin(); sql.state.measurementType.push([...newType.slice(0, 5), '5', newType[6]]);
  const before = structuredClone(sql.state);
  assert.throws(() => fixture.cleanup(), /unexpected measurement definition or linked rule/);
  assert.deepEqual(sql.state, before); assert.equal(sql.writes.length, 0);
});
