/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { ownedPdfDocuments, seedOwnedPdfDocuments } = require('./lib/stored-pdf-documents');

function fixture(body) {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-pdf-claim-'));
  const [doc] = ownedPdfDocuments(store, 'FAKE-PW0123456789abcdef', [{ key: 'delete' }]);
  try { body({ store, doc }); }
  finally { fs.rmSync(store, { recursive: true, force: true }); }
}

function database(write) {
  return {
    rows(query) {
      if (query.includes('information_schema.COLUMNS c')) return [['document', 'document_no', '0']];
      if (query.includes('FROM providerLabRouting') || query.includes('FROM incomingLabRules')) return [];
      assert.fail(`Unexpected row query: ${query}`);
    },
    value(query) {
      if (query.startsWith('SET TRANSACTION')) return write(query);
      if (query.startsWith('SELECT AUTO_INCREMENT')) return '22';
      if (query.includes('NOT REGEXP')) return '0';
      if (query.includes('MAX(CAST(')) return query.includes('FROM `providerLabRouting`') ? '6000' : '21';
      assert.fail(`Unexpected value query: ${query}`);
    },
  };
}

test('the seeder claims the new inode before SQL and retains safe routing ownership', () => fixture(({ store, doc }) => {
  let claimed;
  const sql = database(() => {
    assert(claimed, 'SQL must not run before the file ownership callback');
    assert.equal(fs.readFileSync(doc.file).subarray(0, 5).toString(), '%PDF-');
    return '6001';
  });
  seedOwnedPdfDocuments({ sql, store, patient: '123', provider: '999998', docs: [doc],
    onFileCreated(file, identity) {
      assert.equal(file, doc.file);
      assert(identity.isFile());
      assert.equal(identity.size, 0, 'Ownership must be captured before writing PDF bytes');
      claimed = identity;
    } });
  const actual = fs.lstatSync(doc.file);
  assert.equal(actual.dev, claimed.dev);
  assert.equal(actual.ino, claimed.ino);
  assert.equal(doc.id, '6001');
  assert.equal(doc.routingFloor, '6001');
  assert.deepEqual(doc.routingProviders, ['999998']);
}));

test('a failed row insert still leaves a provably claimed file for cleanup', () => fixture(({ store, doc }) => {
  let claimed;
  const failure = new Error('owned row insert failed');
  const sql = database(() => { throw failure; });
  assert.throws(() => seedOwnedPdfDocuments({ sql, store, patient: '123', provider: '999998', docs: [doc],
    onFileCreated(file, identity) { claimed = { file, dev: identity.dev, ino: identity.ino }; } }), failure);
  assert(claimed);
  const actual = fs.lstatSync(claimed.file);
  assert.equal(actual.dev, claimed.dev);
  assert.equal(actual.ino, claimed.ino);
  assert.equal(doc.id, undefined);
  fs.unlinkSync(claimed.file);
  assert.deepEqual(fs.readdirSync(store), []);
}));

for (const kind of ['regular file', 'dangling symlink']) {
  test(`a collided ${kind} is never claimed or written`, () => fixture(({ store, doc }) => {
    if (kind === 'regular file') fs.writeFileSync(doc.file, 'retain existing evidence');
    else fs.symlinkSync('missing-original-target', doc.file);
    const before = fs.lstatSync(doc.file);
    let claims = 0;
    const sql = database(() => assert.fail('A file collision must precede every SQL write'));
    assert.throws(() => seedOwnedPdfDocuments({ sql, store, patient: '123', provider: '999998', docs: [doc],
      onFileCreated() { claims++; } }), { code: 'EEXIST' });
    assert.equal(claims, 0);
    assert.equal(fs.lstatSync(doc.file).ino, before.ino);
    if (kind === 'regular file') assert.equal(fs.readFileSync(doc.file, 'utf8'), 'retain existing evidence');
    else assert.equal(fs.readlinkSync(doc.file), 'missing-original-target');
  }));
}
