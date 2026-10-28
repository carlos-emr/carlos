/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {createHash} = require('node:crypto');
const source = fs.readFileSync(path.join(__dirname, 'lib/document-metadata-patient-fixture.js'), 'utf8');
const normalized = ['phone', 'phone2', 'chart_no', 'family_doctor', 'alias', 'pronoun', 'gender'];
function fixture() {
  const schema = Object.entries({demographic: ['demographic_no', 'last_name', 'first_name', 'provider_no', 'merged_to', ...normalized],
    formRourke2009: ['demographic_no'], ctl_document: ['module', 'module_id'], casemgmt_note_link: ['table_name', 'table_id'],
    clinical: ['patient_id'], log: ['demographic_no']}).flatMap(([table, columns]) => columns.map((column, index) =>
    [table, column, String(index + 1), table === 'formRourke2009' ? 'Aria' : 'InnoDB', null, '', 'varchar(255)', null, 'YES', 'BASE TABLE']));
  const foreign = [], events = [], originalHash = 'a'.repeat(64); let owner = true, uncertain = false, built, afterCreate;
  const mixed = {readSchema: () => structuredClone(schema), readForeign: () => structuredClone(foreign),
    identifier(value) {assert(/^[A-Za-z0-9_]+$/.test(value)); return '`' + value + '`';},
    signature: rows => createHash('sha256').update(JSON.stringify(rows)).digest('hex'),
    capability(sql, seenSchema, owned, tables, seenForeign) {
      assert.deepEqual(Array.from(owned), ['demographic']); assert(tables.includes('formRourke2009'));
      assert.deepEqual(seenSchema, schema); assert.deepEqual(seenForeign, foreign); events.push('capability');
    },
    build(plan) {built = plan; return 'ONE_PRIVATE_CONNECTION';},
  };
  const sql = {value(query) {
    if (query.startsWith('INSERT INTO demographic')) {events.push(['insert', query]); return '42';}
    if (query.startsWith('SELECT SHA2')) {events.push('snapshot'); return originalHash;}
    if (query.startsWith('SELECT COUNT(*) FROM demographic')) {events.push('owner'); return owner ? '1' : '0';}
    if (query === 'ONE_PRIVATE_CONNECTION') {events.push('cleanup'); if (uncertain) throw new Error('lost SQL acknowledgement'); afterCreate?.(); return '1';}
    throw new Error('Unexpected SQL');
  }};
  const context = {module: {exports: {}}, Buffer, process, require(name) {
    if (name === './mixed-engine-owned-cleanup') return mixed;
    if (name === './playwright-harness') return {sqlString: value => "'" + value + "'"};
    return require(name);
  }};
  vm.runInNewContext(source, context);
  const api = context.module.exports;
  const lifecycle = api.createMetadataPatientFixture({sql, marker: 'FAKE-PW1234567890abcdef', provider: '999998'});
  return {api, lifecycle, events, schema, foreign, originalHash, plan: () => built,
    changeOwner() {owner = false;}, loseReply() {uncertain = true;}, atCleanup(callback) {afterCreate = callback;},
    journal: () => JSON.parse(fs.readFileSync(lifecycle.journal, 'utf8')),
    dispose() {fs.rmSync(path.dirname(lifecycle.journal), {recursive: true, force: true});}};
}
test('seven empty defaults are initialized in the only INSERT before immutable full-row capture', () => {
  const env = fixture();
  try {
    assert.equal(env.lifecycle.create(), '42');
    const insert = env.events.find(event => Array.isArray(event) && event[0] === 'insert')[1];
    for (const column of normalized) assert(insert.includes('`' + column + '`'));
    assert.match(insert, /NOW\(\),''(?:,''){6}\)/);
    const original = JSON.parse(fs.readFileSync(path.join(path.dirname(env.lifecycle.journal), 'original.json')));
    assert.equal(original.hash, env.originalHash); assert.equal(original.patient, '42');
    assert.equal(env.journal().phase, 'ready'); assert.throws(() => env.lifecycle.create(), /cannot be replayed/);
  } finally {env.dispose();}
});
test('parent cleanup uses exact original hash and all references, preserving audit rows', () => {
  const env = fixture();
  try {
    env.lifecycle.create(); env.lifecycle.cleanup({browserClosed: true});
    const plan = env.plan();
    assert.deepEqual(Array.from(plan.owned), ['demographic']); assert.equal(plan.deletes.length, 1);
    assert(plan.deletes[0].where.includes(env.originalHash)); assert(plan.deletes[0].where.includes('demographic_no=42'));
    for (const table of ['formRourke2009', 'clinical', 'ctl_document', 'casemgmt_note_link']) {
      const guard = plan.checks.find(check => check.table === table); assert(guard); assert.equal(guard.count, 0);
    }
    assert(!plan.checks.some(check => check.table === 'log')); assert.equal(env.journal().phase, 'cleaned');
    assert.throws(() => env.lifecycle.cleanup({browserClosed: true}), /unconfirmed/);
  } finally {env.dispose();}
});
for (const issue of ['changed parent', 'browser not closed', 'schema drift', 'foreign-key drift']) test('parent cleanup refuses ' + issue + ' without sending a deletion', () => {
  const env = fixture();
  try {
    env.lifecycle.create();
    if (issue === 'changed parent') env.changeOwner();
    if (issue === 'schema drift') env.schema[0][6] = 'int';
    if (issue === 'foreign-key drift') env.foreign.push(['clinical', 'patient_id', 'demographic', 'demographic_no']);
    assert.throws(() => env.lifecycle.cleanup({browserClosed: issue !== 'browser not closed'}));
    assert(!env.events.includes('cleanup')); assert.equal(env.journal().cleaned, false);
  } finally {env.dispose();}
});
test('lost cleanup acknowledgement is durable uncertainty and cannot replay', () => {
  const env = fixture();
  try {
    env.lifecycle.create(); env.loseReply();
    assert.throws(() => env.lifecycle.cleanup({browserClosed: true}), /lost SQL/);
    assert.equal(env.journal().phase, 'cleanup-dispatch'); assert.equal(env.journal().cleaning, true);
    assert.match(env.journal().querySha256, /^[a-f0-9]{64}$/);
    assert.throws(() => env.lifecycle.cleanup({browserClosed: true}), /unconfirmed/);
    assert.equal(env.events.filter(event => event === 'cleanup').length, 1);
  } finally {env.dispose();}
});
test('cleanup intent is fsynced before its single SQL connection is dispatched', () => {
  const env = fixture();
  try {
    env.lifecycle.create(); env.atCleanup(() => {
      assert.equal(env.journal().phase, 'cleanup-dispatch'); assert.equal(env.journal().cleaning, true);
      assert.match(env.journal().querySha256, /^[a-f0-9]{64}$/);
    });
    env.lifecycle.cleanup({browserClosed: true});
  } finally {env.dispose();}
});
test('a declared patient FK with a different target key is never guessed as a patient number', () => {
  const env = fixture();
  try {assert.throws(() => env.api.parentReferences(env.schema, [['clinical', 'patient_id', 'demographic', 'hin']], '42'), /Unreviewed/);}
  finally {env.dispose();}
});
test('reference scopes include legacy aliases and self-merge references with exact patient identity', () => {
  const env = fixture();
  try {
    const scopes = env.api.parentReferences(env.schema, [], '42');
    const self = scopes.find(scope => scope.table === 'demographic'); assert(self.where.includes('`merged_to`=42'));
    assert(!self.where.includes('`demographic_no`'));
    assert(scopes.find(scope => scope.table === 'formRourke2009').where.includes('`demographic_no`=42'));
  } finally {env.dispose();}
});

test('declared audit foreign keys block parent cleanup without deleting audit history', () => {
  const env = fixture();
  try {
    const scopes = env.api.parentReferences(env.schema, [['log', 'demographic_no', 'demographic', 'demographic_no']], '42');
    const audit = scopes.find(scope => scope.table === 'log');
    assert(audit); assert.equal(audit.count, 0); assert(audit.where.includes('`demographic_no`=42'));
    assert(!env.api.parentReferences(env.schema, [], '42').some(scope => scope.table === 'log'));
  } finally {env.dispose();}
});
