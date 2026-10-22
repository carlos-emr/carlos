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
