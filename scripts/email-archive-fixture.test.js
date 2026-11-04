/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const {createEmailArchiveCleanup} = require('./lib/email-archive-fixture');

function fixture(t, overrides = {}) {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'owned-email-archive-'));
  t.after(() => fs.rmSync(store, {recursive: true, force: true}));
  const owned = path.join(store, 'owned.eml'), unrelated = path.join(store, 'unrelated.eml');
  fs.writeFileSync(owned, 'owned encrypted fixture'); fs.writeFileSync(unrelated, 'preserve me');
  const mutations = [];
  let removed = false;
  const sql = {
    value(query) {
      if (query.includes('MAX(id)')) return '90';
      if (query.includes('FROM demographic')) return overrides.owner ?? '1';
      if (query.includes('FROM emailLog')) return overrides.logs ?? '1';
      if (query.includes('docfilename=')) return overrides.sharedFile ?? '1';
      if (query.includes('id NOT IN')) return overrides.sharedArchive ?? '0';
      if (query.includes('AND NOT (module=')) return overrides.sharedPatient ?? '0';
      if (query.startsWith('SELECT (SELECT')) return removed ? '0' : '2';
      if (query.includes('FROM outboundEmailArchive')) return overrides.archives ?? '1';
      throw new Error('Unexpected fixture query');
    },
    rows: () => overrides.rows ?? [['5', '11', '21', 'owned.eml']],
    execute(query) {
      if (overrides.sqlFailure) throw new Error('simulated SQL failure');
      mutations.push(query); removed = true;
    },
  };
  const cleanup = createEmailArchiveCleanup(sql, store);
  const run = () => cleanup({patient: '31', marker: 'FAKE-PW0123456789abcdef', logIds: ['11']});
  return {store, owned, unrelated, mutations, run};
}

test('owned archive cleanup removes only its file and bounds new DOC routes by their pre-send marks', t => {
  const f = fixture(t); f.run();
  assert(!fs.existsSync(f.owned));
  assert.equal(fs.readFileSync(f.unrelated, 'utf8'), 'preserve me');
  assert.equal(f.mutations.length, 1);
  assert.match(f.mutations[0], /START TRANSACTION;[\s\S]*COMMIT/);
  assert.match(f.mutations[0], /providerLabRouting WHERE lab_type='DOC' AND lab_no IN \(21\) AND id>90/);
  assert.match(f.mutations[0], /demographic_no=31 AND id>90/);
  assert.doesNotMatch(f.mutations[0], /FOREIGN_KEY_CHECKS/);
});

for (const [name, overrides] of [
  ['changed patient ownership', {owner: '0'}],
  ['a log belonging to another patient', {logs: '0'}],
  ['unfamiliar archive metadata', {archives: '2'}],
  ['a shared filename', {sharedFile: '2'}],
  ['a document used by another archive', {sharedArchive: '1'}],
  ['a document linked to another patient', {sharedPatient: '1'}],
  ['path traversal', {rows: [['5', '11', '21', '../unrelated.eml']]}],
]) {
  test(`archive cleanup retains rows and files on ${name}`, t => {
    const f = fixture(t, overrides);
    assert.throws(f.run);
    assert.equal(f.mutations.length, 0);
    assert(fs.existsSync(f.owned) && fs.existsSync(f.unrelated));
  });
}

test('archive cleanup refuses symbolic links before any database mutation', t => {
  const f = fixture(t); fs.unlinkSync(f.owned); fs.symlinkSync(f.unrelated, f.owned);
  assert.throws(f.run, /symbolic/);
  assert.equal(f.mutations.length, 0);
  assert.equal(fs.readFileSync(f.unrelated, 'utf8'), 'preserve me');
});

test('archive cleanup preserves files if the SQL transaction fails', t => {
  const f = fixture(t, {sqlFailure: true});
  assert.throws(f.run, /simulated SQL failure/);
  assert(fs.existsSync(f.owned) && fs.existsSync(f.unrelated));
});
