/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {documentReferenceColumns, planDocumentFixtureId, assertSplitFixtureId} = require('./lib/document-fixture-allocation');
const {createStoredDocumentFixture} = require('./lib/stored-document-mutation-fixture');

function database({inventory = [['document', 'document_no', '0']], values = {}, next = '22', overrideMaximum} = {}) {
  const queries = [];
  const scopeValues = (table, column, where) => values[`${table}.${column}:${where}`] || values[`${table}.${column}`] || [];
  const sql = {
    rows(query) {queries.push(query); assert(query.includes('information_schema.COLUMNS c')); return structuredClone(inventory);},
    value(query) {
      queries.push(query);
      if (query.startsWith('SELECT AUTO_INCREMENT')) return next;
      if (query.includes('NOT REGEXP')) {
        const [, table, where, column] = /FROM `([^`]+)` WHERE \(([^)]+)\) AND `([^`]+)` IS NOT NULL/.exec(query);
        return String(scopeValues(table, column, where).filter(value => value !== null && !/^\d+$/.test(value)).length);
      }
      // The model distinguishes numeric MAX(CAST(...)) from incorrect lexical MAX(...).
      const numeric = /MAX\(CAST\(`([^`]+)` AS UNSIGNED\)\)/.exec(query);
      const lexical = /MAX\(`([^`]+)`\)/.exec(query);
      const column = (numeric || lexical)?.[1], [, table, where] = /FROM `([^`]+)` WHERE (.+)$/.exec(query);
      assert(column);
      if (overrideMaximum !== undefined) return overrideMaximum;
      const values = scopeValues(table, column, where).filter(value => value !== null);
      if (!values.length) return '0';
      return numeric ? values.map(BigInt).reduce((maximum, n) => n > maximum ? n : maximum, 0n).toString()
        : [...values].sort().at(-1);
    },
    execute() {assert.fail('The identity planner must never write rows or allocation metadata');},
  };
  return {sql, queries};
}

test('dangling seed references reserve their identities even when document AUTO_INCREMENT is only22', () => {
  const values = {'document.document_no': ['1', '21'], 'consultdocs.document_no': ['22', '23', '92'],
    'ctl_document.document_no': ['4955'], 'emailAttachment.documentId': ['1059']};
  const before = structuredClone(values), {sql} = database({inventory: [
    ['document', 'document_no', '0'], ['consultdocs', 'document_no', '0'],
    ['ctl_document', 'document_no', '0'], ['emailAttachment', 'documentId', '0']], values});
  const result = planDocumentFixtureId(sql);
  assert.equal(result.sourceId, '4956'); assert.equal(result.maximumReference, 4955); assert.equal(result.nextAutoIncrement, 22);
  assert.deepEqual(values, before, 'Planning preserves every existing and dangling reference');
  assertSplitFixtureId(result.sourceId, '4957');
});

test('an allocator already ahead of all references keeps its next identity without ALTER or backward reuse', () => {
  const {sql} = database({next: '9000', values: {'document.document_no': ['55']}});
  assert.equal(planDocumentFixtureId(sql).sourceId, '9000');
});

test('declared unusual-column foreign keys and duplicate metadata rows remain in the ceiling', () => {
  const {sql} = database({inventory: [['attachments', 'target', '1'], ['attachments', 'target', '1']],
    values: {'attachments.target': ['1000']}});
  const result = planDocumentFixtureId(sql);
  assert.equal(result.sourceId, '1001'); assert.equal(result.references.filter(row => row.table === 'attachments').length, 1);
});

for (const [table, column, where] of [
  ['providerLabRouting', 'lab_no', "lab_type='DOC'"], ['patientLabRouting', 'lab_no', "lab_type='DOC'"],
  ['providerLabRoutingLock', 'lab_no', '1'], ['casemgmt_note_link', 'table_id', 'table_name=5'],
]) test(`semantic ${table} references reserve their real document scope`, () => {
  const {sql} = database({values: {[`${table}.${column}:${where}`]: ['6000']}});
  assert.equal(planDocumentFixtureId(sql).sourceId, '6001');
});

test('textual numeric identities use numeric maximum rather than lexical9 before100', () => {
  const {sql} = database({inventory: [['attachments', 'documentId', '0']], next: '1',
    values: {'attachments.documentId': ['9', '100', '00022']}});
  assert.equal(planDocumentFixtureId(sql).sourceId, '101');
});

test('empty or NULL references leave a valid empty database allocator available', () => {
  const {sql} = database({next: '1', values: {'document.document_no': [null]}});
  assert.equal(planDocumentFixtureId(sql).sourceId, '1');
});

for (const inventory of [[], null, [['document', 'document_no']], [['document', 'document_no', 'unknown']],
  [['unsafe`table', 'document_no', '0']], [['document', 'unsafe-column', '0']]]) {
  test(`unavailable, ambiguous or unsafe inventory fails before an allocation (${JSON.stringify(inventory)})`, () => {
    const {sql, queries} = database({inventory});
    assert.throws(() => planDocumentFixtureId(sql));
    assert.equal(queries.length, 1);
  });
}

for (const invalid of ['22-other', '-1', '2.5', '', '1e4']) test(`unfamiliar reference ${JSON.stringify(invalid)} is retained and blocks allocation`, () => {
  const {sql} = database({values: {'document.document_no': [invalid]}});
  assert.throws(() => planDocumentFixtureId(sql), /Unfamiliar document reference value/);
});

for (const next of [null, 22, '', '0', '001', '22-other', '2147483648']) test(`invalid AUTO_INCREMENT ${JSON.stringify(next)} cannot supply a fixture ID`, () => {
  const {sql} = database({next}); assert.throws(() => planDocumentFixtureId(sql));
});
for (const maximum of [null, '1.5', '-1', '2147483648', '9007199254740993']) test(`invalid maximum ${JSON.stringify(maximum)} cannot be rounded into a valid range`, () => {
  const {sql} = database({overrideMaximum: maximum}); assert.throws(() => planDocumentFixtureId(sql));
});

test('source and one accepted split must both fit the real signedINT document identity', () => {
  assert.equal(planDocumentFixtureId(database({next: '2147483646'}).sql).sourceId, '2147483646');
  assertSplitFixtureId('2147483646', '2147483647');
  assert.throws(() => planDocumentFixtureId(database({next: '2147483647'}).sql), /No safe document identity range/);
  assert.throws(() => planDocumentFixtureId(database({values: {'document.document_no': ['2147483646']}}).sql), /No safe document identity range/);
});

test('actual split IDs may advance but cannot reuse a seeded/source identity or exceed the model range', () => {
  assertSplitFixtureId('4956', '5000');
  for (const number of ['22', '4955', '4956', '2147483648', '04957', '4957-other']) {
    assert.throws(() => assertSplitFixtureId('4956', number));
  }
});

test('schema read errors propagate without a fallback allocator', () => {
  const error = new Error('metadata unavailable');
  assert.throws(() => documentReferenceColumns({rows() {throw error;}}), error);
});

test('exhausted allocation refuses the real fixture before creating a document file or INSERT', () => {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'document-allocation-refusal-'));
  const saved = Object.fromEntries(['DOCUMENT_DIR', 'RX_FAX_DOCUMENT_DIR'].map(key => [key, process.env[key]]));
  process.env.DOCUMENT_DIR = store; delete process.env.RX_FAX_DOCUMENT_DIR;
  const modeled = database({next: '2147483647'}), original = modeled.sql.value, callbacks = [];
  modeled.sql.value = query => {
    if (query.includes('MAX(id)')) return '0';
    if (query.includes('DATE_FORMAT(NOW()')) return '2026-10-19 20:00:00';
    if (query.includes('FROM demographic')) return '1';
    assert(!/^(?:INSERT|UPDATE|DELETE|ALTER)\b/.test(query), 'Failed planning must not reach a database write');
    return original(query);
  };
  try {
    assert.throws(() => createStoredDocumentFixture({sql: modeled.sql, patient: '123', provider: '999998',
      marker: 'FAKE-PW1234567890abcdef', cleanup(callback) {callbacks.push(callback);}}, '10'), /No safe document identity range/);
    assert.equal(callbacks.length, 1, 'Durable cleanup registration still precedes setup');
    assert.deepEqual(fs.readdirSync(store), []);
  } finally {
    for (const [key, value] of Object.entries(saved)) {if (value === undefined) delete process.env[key]; else process.env[key] = value;}
    fs.rmSync(store, {recursive: true, force: true});
  }
});
