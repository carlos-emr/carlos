/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const {validateOwnedPost} = require('./stored-document-mutations-playwright-checks');
const {fileProof} = require('./lib/stored-document-mutation-fixture');
const owned = new Set(['42']);
const expected = {method: ['split'], document: ['42'], queueID: ['1'], page: ['2,90', '1,0'], sourceRevision: ['a'.repeat(64)]};
const body = 'method=split&document=42&queueID=1&page=2%2C90&page=1%2C0&CSRF-TOKEN=owned-token&sourceRevision=' + 'a'.repeat(64);

test('installed mutation allowlist preserves repeated-page order and explicit owned identity', () => {
  assert.deepEqual(validateOwnedPost(body, expected, owned).getAll('page'), ['2,90', '1,0']);
});
for (const [name, candidate] of [
  ['different patient document', body.replace('document=42', 'document=43')],
  ['duplicate document', body + '&document=42'],
  ['different operation', body.replace('method=split', 'method=removeFirstPage')],
  ['different queue', body.replace('queueID=1', 'queueID=2')],
  ['changed page order', body.replace('page=2%2C90&page=1%2C0', 'page=1%2C0&page=2%2C90')],
  ['unexpected mutation field', body + '&demographicNo=9'],
  ['duplicate csrf', body + '&CSRF-TOKEN=other'],
  ['changed source revision', body.replace('a'.repeat(64), 'b'.repeat(64))],
]) test(`installed mutation guard refuses ${name}`, () => {
  assert.throws(() => validateOwnedPost(candidate, expected, owned));
});

test('file proof detects changed bytes, permissions, and a replaced inode even at the same name', () => {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'stored-document-proof-test-'));
  try {
    const file = path.join(store, 'owned.pdf'); fs.writeFileSync(file, 'first', {mode: 0o600});
    const baseline = fileProof(file, store);
    fs.writeFileSync(file, 'changed'); assert.notDeepEqual(fileProof(file, store), baseline);
    fs.writeFileSync(file, 'first'); fs.chmodSync(file, 0o640); assert.notDeepEqual(fileProof(file, store), baseline);
    fs.chmodSync(file, 0o600);
    const replacement = path.join(store, 'new.pdf'); fs.writeFileSync(replacement, 'first', {mode: 0o600});
    fs.renameSync(replacement, file); assert.notEqual(fileProof(file, store).ino, baseline.ino);
  } finally {fs.rmSync(store, {recursive: true, force: true});}
});

test('file proof rejects symlinks and paths outside the owned store', () => {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'stored-document-proof-test-'));
  try {
    const file = path.join(store, 'owned.pdf'), link = path.join(store, 'link.pdf');
    fs.writeFileSync(file, 'owned'); fs.symlinkSync(file, link);
    assert.throws(() => fileProof(link, store), /unsafe/);
    assert.throws(() => fileProof(file, path.dirname(store)), /escaped/);
  } finally {fs.rmSync(store, {recursive: true, force: true});}
});

const {createStoredDocumentFixture} = require('./lib/stored-document-mutation-fixture');
function ownedFixture() {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'stored-document-cleanup-test-'));
  const saved = {DOCUMENT_DIR: process.env.DOCUMENT_DIR, RX_FAX_DOCUMENT_DIR: process.env.RX_FAX_DOCUMENT_DIR,
    DOCUMENT_CACHE_DIR: process.env.DOCUMENT_CACHE_DIR};
  process.env.DOCUMENT_DIR = store; delete process.env.RX_FAX_DOCUMENT_DIR;
  process.env.DOCUMENT_CACHE_DIR = path.join(store, 'unused-cache');
  const hash = 'a'.repeat(64), other = ['5', 'b'.repeat(64)], callbacks = [], transactions = [];
  const data = {document: [['42', hash]], ctl_document: [['123', 'demographic', '42', hash]],
    patientLabRouting: [['11', hash]], providerLabRouting: [['12', hash]], queue_document_link: [['13', hash]]};
  let foreign = false, rollback = false;
  const sql = {
    rows(query) {
      if (query.includes('ORDER BY ORDINAL_POSITION')) return [['fixture_column']];
      if (query.includes('information_schema.COLUMNS c')) return [['document_storage', 'documentNo']];
      if (query.includes('FROM document ORDER BY')) return [other, ...data.document].map(row => [...row]);
      const table = /FROM `([^`]+)`/.exec(query)?.[1];
      if (table) return structuredClone(data[table]);
      throw new Error('Unexpected rows query');
    },
    value(query) {
      if (query.startsWith('INSERT INTO document')) {assert.equal(callbacks.length, 1); return '42';}
      if (query.includes('START TRANSACTION')) {
        transactions.push(query);
        if (rollback) return '0';
        for (const table of Object.keys(data)) data[table] = [];
        return '1';
      }
      if (query.includes('FROM demographic')) return '1';
      if (query.includes('information_schema.TABLES')) return '0';
      if (query.includes('document_storage') || query.includes('casemgmt_note_link')) return foreign ? '1' : '0';
      throw new Error('Unexpected value query');
    },
    execute(query) {assert.ok(query.startsWith('INSERT INTO ctl_document'));},
  };
  const fixture = createStoredDocumentFixture({sql, marker: 'FAKE-PW1234567890abcdef', patient: '123', provider: '999998',
    cleanup(callback) {callbacks.push(callback);}}, '10');
  return {fixture, data, transactions, cleanup: callbacks[0], setForeign() {foreign = true;}, rollback() {rollback = true;},
    dispose() {
      for (const [key, value] of Object.entries(saved)) {if (value === undefined) delete process.env[key]; else process.env[key] = value;}
      fs.rmSync(store, {recursive: true, force: true}); fs.rmSync(path.dirname(fixture.journal), {recursive: true, force: true});
    }};
}

test('successful cleanup retains its evidence journal and uses one guarded rollback-capable transaction', async () => {
  const env = ownedFixture();
  try {
    env.fixture.closeAfterDrain(); await env.cleanup();
    assert.equal(env.transactions.length, 1);
    assert.match(env.transactions[0], /FOR UPDATE/); assert.match(env.transactions[0], /ROW_COUNT\(\)=1/);
    assert.match(env.transactions[0], /IF\(@stored_fixture_ok,'COMMIT','ROLLBACK'\)/);
    assert.ok(Object.values(env.data).every(rows => rows.length === 0));
    assert.equal(fs.existsSync(env.fixture.sourceFile), false);
    assert.equal(JSON.parse(fs.readFileSync(env.fixture.journal)).phase, 'cleaned');
    assert.equal(env.fixture.isCleaned(), true);
  } finally {env.dispose();}
});

for (const problem of ['changed routing', 'changed file', 'external reference', 'unknown mutation', 'failed transport', 'undrained browser']) {
  test(`cleanup preserves the fixture with ${problem}`, async () => {
    const env = ownedFixture();
    try {
      if (problem === 'changed routing') env.data.patientLabRouting[0][1] = 'c'.repeat(64);
      if (problem === 'changed file') fs.appendFileSync(env.fixture.sourceFile, 'changed');
      if (problem === 'external reference') env.setForeign();
      if (problem === 'unknown mutation') env.fixture.begin();
      if (problem === 'failed transport') env.fixture.transportFailed();
      if (problem !== 'undrained browser') env.fixture.closeAfterDrain();
      await assert.rejects(env.cleanup());
      assert.equal(env.transactions.length, 0); assert.equal(env.data.document.length, 1);
      assert.equal(fs.existsSync(env.fixture.sourceFile), true); assert.equal(env.fixture.isCleaned(), false);
    } finally {env.dispose();}
  });
}

test('transaction refusal preserves document bytes and all clinical routes for recovery', async () => {
  const env = ownedFixture();
  try {
    const baseline = structuredClone(env.data), bytes = fs.readFileSync(env.fixture.sourceFile);
    env.rollback(); env.fixture.closeAfterDrain();
    await assert.rejects(env.cleanup(), /Atomic stored fixture cleanup refused/);
    assert.deepEqual(env.data, baseline); assert.deepEqual(fs.readFileSync(env.fixture.sourceFile), bytes);
    assert.equal(env.fixture.isCleaned(), false);
  } finally {env.dispose();}
});
