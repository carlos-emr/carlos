/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {createConsultationSubmitFixture} = require('./lib/consultation-submit-fixture');

function database() {
  const writes = [];
  return {writes, rows: () => [['85', '99']], value: () => '0', execute: query => writes.push(query)};
}

test('owns each request by a unique exact reason and removes only its dependencies and linked signatures', () => {
  const sql = database();
  const fixture = createConsultationSubmitFixture(sql, '42');
  const first = fixture.reason('stamp-create-warning');
  const second = fixture.reason('stamp-update');
  assert.notEqual(first, second);
  assert.match(first, /^PW_CONSULT_[a-f0-9]{32}_stamp-create-warning$/);
  assert.equal(fixture.requestId('stamp-create-warning'), '85');
  fixture.cleanup();
  const cleanup = sql.writes[0];
  assert.match(cleanup, /START TRANSACTION;/);
  assert.match(cleanup, /DELETE FROM consultationRequests WHERE requestId IN \(85\) AND demographicNo=42 AND reason IN/);
  assert.ok(cleanup.includes(first) && cleanup.includes(second));
  assert.match(cleanup, /DELETE FROM DigitalSignature WHERE id=99\s+AND demographicId=42/);
  assert.match(cleanup, /NOT EXISTS \(SELECT 1 FROM consultationRequestsArchive WHERE signature_img='99'\)/);
  assert.ok(cleanup.indexOf('DELETE FROM consultdocs') < cleanup.indexOf('DELETE FROM consultationRequests WHERE'));
});

test('reports a silently ignored request deletion as cleanup failure', () => {
  const sql = database();
  sql.value = () => '1';
  const fixture = createConsultationSubmitFixture(sql, '42');
  fixture.reason('stamp-create-happy');
  assert.throws(() => fixture.cleanup(), error => error instanceof AggregateError
    && error.errors.some(item => /did not remove every request/.test(item.message)));
});

test('recovers requests after a lost HTTP reply through their marker, without needing a response ID', () => {
  const sql = database();
  const fixture = createConsultationSubmitFixture(sql, '42');
  fixture.reason('stamp-create-happy');
  // Submission commits but the caller receives no redirect: cleanup still finds exact ownership.
  fixture.cleanup();
  assert.match(sql.writes[0], /requestId IN \(85\)/);
});

test('cleans only new byte-identical preview files and keeps preexisting and unrelated files', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-cleanup-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  const bytes = Buffer.from('%PDF-owned-preview');
  const baselineDir = path.join(root, 'tempPDF1');
  fs.mkdirSync(baselineDir);
  fs.writeFileSync(path.join(baselineDir, 'combinedPDF_42_1.pdf'), bytes);
  const fixture = createConsultationSubmitFixture(database(), '42', root);
  const ownedDir = path.join(root, 'tempPDF2');
  fs.mkdirSync(ownedDir);
  const owned = path.join(ownedDir, 'combinedPDF_42_2.pdf');
  fs.writeFileSync(owned, bytes);
  fs.writeFileSync(path.join(ownedDir, 'unrelated.pdf'), bytes);
  fs.writeFileSync(path.join(ownedDir, 'combinedPDF_43_2.pdf'), bytes);
  fixture.capturePdf(bytes);
  fixture.cleanup();
  assert.equal(fs.existsSync(owned), false);
  assert.equal(fs.existsSync(path.join(baselineDir, 'combinedPDF_42_1.pdf')), true);
  assert.deepEqual(fs.readdirSync(ownedDir).sort(), ['combinedPDF_43_2.pdf', 'unrelated.pdf']);
});

test('refuses to unlink an owned preview whose content changed after capture', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-cleanup-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  const fixture = createConsultationSubmitFixture(database(), '42', root);
  const directory = path.join(root, 'tempPDF2');
  fs.mkdirSync(directory);
  const owned = path.join(directory, 'combinedPDF_42_2.pdf');
  const bytes = Buffer.from('%PDF-owned-preview');
  fs.writeFileSync(owned, bytes);
  fixture.capturePdf(bytes);
  fs.writeFileSync(owned, 'replacement');
  assert.throws(() => fixture.cleanup(), AggregateError);
  assert.equal(fs.readFileSync(owned, 'utf8'), 'replacement');
});


test('a cold installation permits only an absent exact tail and never creates it itself', t => {
  const parent = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-cold-parent-'));
  t.after(() => fs.rmSync(parent, {recursive: true, force: true}));
  const root = path.join(parent, 'carlos-temp');
  const fixture = createConsultationSubmitFixture(database(), '42', root);
  assert.equal(fs.existsSync(root), false, 'Fixture must leave native root creation to the application');
  assert.throws(() => fixture.capturePdf(Buffer.from('%PDF-not-yet-created')), /ENOENT/);
  assert.equal(fs.existsSync(root), false);
  assert.throws(() => createConsultationSubmitFixture(database(), '42', path.join(parent, 'wrong-tail')), /exact/);
  assert.throws(() => createConsultationSubmitFixture(database(), '42', path.join(parent, 'missing-parent', 'carlos-temp')), /ENOENT/);
});

test('the later native-created0700 root must own the byte-matched preview and foreign files survive', t => {
  const parent = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-cold-parent-'));
  t.after(() => fs.rmSync(parent, {recursive: true, force: true}));
  const root = path.join(parent, 'carlos-temp');
  const fixture = createConsultationSubmitFixture(database(), '42', root);
  // Model the application writing the preview after the fixture's empty snapshot.
  fs.mkdirSync(root, {mode: 0o700});
  const directory = path.join(root, 'tempPDF123');fs.mkdirSync(directory, {mode: 0o700});
  const bytes = Buffer.from('%PDF-native-synthetic-preview');
  const owned = path.join(directory, 'combinedPDF_42_1.pdf');fs.writeFileSync(owned, bytes);
  const foreign = path.join(directory, 'combinedPDF_43_1.pdf');fs.writeFileSync(foreign, bytes);
  fixture.capturePdf(bytes);fixture.cleanup();
  assert.equal(fs.existsSync(owned), false);assert.equal(fs.existsSync(foreign), true);
  assert.equal(fs.statSync(root).mode & 0o777, 0o700);
});

test('native root mode, owner, symbolic links and foreign-owned preview files fail closed', t => {
  const parent = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-cold-parent-'));
  t.after(() => fs.rmSync(parent, {recursive: true, force: true}));
  const root = path.join(parent, 'carlos-temp');
  const fixture = createConsultationSubmitFixture(database(), '42', root);
  fs.mkdirSync(root, {mode: 0o755});fs.chmodSync(root, 0o755);
  assert.throws(() => fixture.capturePdf(Buffer.from('unused')), /0700/);
  fs.chmodSync(root, 0o700);
  const directory = path.join(root, 'tempPDF123');fs.mkdirSync(directory);
  const bytes = Buffer.from('%PDF-native-synthetic-preview');
  const owned = path.join(directory, 'combinedPDF_42_1.pdf');fs.writeFileSync(owned, bytes);
  const lstat = fs.lstatSync;
  for (const foreignOwnerPath of [root, owned]) {
    try {
      fs.lstatSync = (filename, ...args) => {
        const stat = lstat(filename, ...args);
        if (filename === foreignOwnerPath) return new Proxy(stat, {get: (value, key) => key === 'uid' ? value.uid + 1 : typeof value[key] === 'function' ? value[key].bind(value) : value[key]});
        return stat;
      };
      assert.throws(() => fixture.capturePdf(bytes), /owner/);
    } finally {fs.lstatSync = lstat;}
  }
  const moved = root + '-moved';fs.renameSync(root, moved);fs.symlinkSync(moved, root);
  assert.throws(() => fixture.capturePdf(bytes), /symbolic/);
  assert.throws(() => createConsultationSubmitFixture(database(), '42', root), /real directory/);
});


test('a previously captured preview cannot stand in for a later missing byte match', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'consult-cleanup-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  const fixture = createConsultationSubmitFixture(database(), '42', root);
  const directory = path.join(root, 'tempPDF1');fs.mkdirSync(directory);
  const bytes = Buffer.from('%PDF-first-owned-preview');
  const file = path.join(directory, 'combinedPDF_42_1.pdf');fs.writeFileSync(file, bytes);
  fixture.capturePdf(bytes);
  assert.throws(() => fixture.capturePdf(Buffer.from('%PDF-later-preview-not-on-disk')), /not found/);
  fixture.cleanup();assert.equal(fs.existsSync(file), false);
});
