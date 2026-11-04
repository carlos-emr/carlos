/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const {createEmailArchiveCleanup, preflightEmailArchives} = require('./lib/email-archive-fixture');
const {SkipCheck} = require('./lib/playwright-harness');
// This test file runs in its own Node test process and creates only temporary fixture files.
process.env.CARLOS_DISPOSABLE_VM = 'true';

function fixture(t, overrides = {}) {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'owned-email-archive-'));
  t.after(() => fs.rmSync(store, {recursive: true, force: true}));
  const owned = path.join(store, 'owned.eml'), unrelated = path.join(store, 'unrelated.eml');
  fs.writeFileSync(owned, 'owned encrypted fixture'); fs.writeFileSync(unrelated, 'preserve me');
  for (const name of overrides.extraFiles || []) fs.writeFileSync(path.join(store, name), 'second owned fixture');
  const mutations = [], queries = [];
  let removed = false;
  const sql = {
    value(query) {
      queries.push(query.replace(/\s+/g, ' ').trim());
      if (query.startsWith('SELECT (SELECT')) return removed ? (overrides.remaining ?? '0') : '2';
      if (query.includes('MAX(id)')) return '90';
      if (query.includes('FROM demographic')) return overrides.owner ?? '1';
      if (query.includes('FROM emailLog')) return overrides.logs ?? '1';
      if (query.includes('FROM document WHERE docfilename')) {
        return query.includes(' IN (') ? (overrides.reusedFile ?? (removed ? '0' : '1')) : (overrides.sharedFile ?? '1');
      }
      if (query.includes('FROM outboundEmailArchiveAttachment')) return overrides.sharedAttachment ?? '0';
      if (query.includes('FROM outboundEmailArchiveDeletion')) return overrides.sharedDeletion ?? '0';
      if (query.includes('id NOT IN')) return overrides.sharedArchive ?? '0';
      if (query.includes('AND NOT (module=')) return overrides.sharedPatient ?? '0';
      if (query.includes('FROM outboundEmailArchive')) return overrides.archives ?? '1';
      throw new Error('Unexpected fixture query');
    },
    rows(query) {
      queries.push(query.replace(/\s+/g, ' ').trim());
      return removed ? [] : (overrides.rows ?? [['5', '11', '21', 'owned.eml']]);
    },
    execute(query) {
      if (overrides.sqlFailure) throw new Error('simulated SQL failure');
      mutations.push(query); removed = true;
    },
  };
  const create = () => createEmailArchiveCleanup(sql, store, {unlink: overrides.unlink});
  let cleanup = create();
  const run = () => cleanup({patient: '31', marker: 'FAKE-PW0123456789abcdef', logIds: ['11']});
  const restart = () => { cleanup = create(); };
  const receipt = path.join(store, '.playwright-email-cleanup-FAKE-PW0123456789abcdef-31', 'receipt.json');
  return {store, owned, unrelated, mutations, queries, run, restart, receipt};
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
  const select = f.queries.find(query => query.startsWith('SELECT a.id,'));
  for (const scope of [/a\.emailLogId IN \(11\)/, /a\.demographicNo=31/, /l\.demographicNo=31/,
    /c\.module='demographic' AND c\.module_id=31/, /d\.doctype='email'/, /d\.docClass='EMAIL'/,
    /d\.docSubClass='OUTBOUND'/, /d\.source='Outbound Email Archive'/,
    /d\.docdesc=CONCAT\('Outbound email archive ',l\.id\)/]) assert.match(select, scope);
  const scopes = {
    outboundEmailArchiveLegalHoldEvent: [/archiveId IN \(5\)/],
    outboundEmailArchiveDeletion: [/archiveId IN \(5\)/],
    outboundEmailArchiveAttachment: [/archiveId IN \(5\)/],
    outboundEmailArchive: [/id IN \(5\)/, /demographicNo=31/],
    patientLabRouting: [/lab_type='DOC'/, /lab_no IN \(21\)/, /demographic_no=31/, /id>90/],
    providerLabRouting: [/lab_type='DOC'/, /lab_no IN \(21\)/, /id>90/],
    ctl_document: [/document_no IN \(21\)/, /module='demographic'/, /module_id=31/],
    document: [/document_no IN \(21\)/, /docClass='EMAIL'/, /docSubClass='OUTBOUND'/],
  };
  const deletes = f.mutations[0].split(';').map(query => query.trim()).filter(query => query.startsWith('DELETE'));
  assert.equal(deletes.length, Object.keys(scopes).length);
  for (const query of deletes) {
    const table = query.match(/^DELETE FROM (\w+) WHERE /)?.[1];
    assert(scopes[table], 'Unscoped or unreviewed deletion');
    for (const scope of scopes[table]) assert.match(query, scope);
  }
  assert(!fs.existsSync(path.dirname(f.receipt)));
});

for (const [name, overrides] of [
  ['changed patient ownership', {owner: '0'}],
  ['a log belonging to another patient', {logs: '0'}],
  ['unfamiliar archive metadata', {archives: '2'}],
  ['a shared filename', {sharedFile: '2'}],
  ['a document used by another archive', {sharedArchive: '1'}],
  ['a document attached to another archive', {sharedAttachment: '1'}],
  ['a document retained in another archive tombstone', {sharedDeletion: '1'}],
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
  assert(fs.existsSync(f.receipt));
});

test('archive cleanup refuses to remove archives without an explicit disposable-install flag', t => {
  const f = fixture(t);
  delete process.env.CARLOS_DISPOSABLE_VM;
  try {
    assert.throws(f.run, /CARLOS_DISPOSABLE_VM/);
    assert.equal(f.mutations.length, 0);
    assert(fs.existsSync(f.owned) && fs.existsSync(f.unrelated));
  } finally { process.env.CARLOS_DISPOSABLE_VM = 'true'; }
});

test('missing cleanup prerequisites skip before reading SQL or creating fixtures', t => {
  assert.throws(() => preflightEmailArchives({env: {CARLOS_DISPOSABLE_VM: 'true'}}), SkipCheck);
  assert.throws(() => preflightEmailArchives({env: {CARLOS_DISPOSABLE_VM: 'false', DOCUMENT_DIR: os.tmpdir()}}), SkipCheck);
  let reads = 0;
  assert.throws(() => createEmailArchiveCleanup({value() { reads++; }}, ''), SkipCheck);
  assert.equal(reads, 0);
});

test('a private receipt recovers a partial unlink from a new helper without deleting metadata again', t => {
  let fail = true;
  const f = fixture(t, {archives: '2', extraFiles: ['second.eml'],
    rows: [['5', '11', '21', 'owned.eml'], ['6', '11', '22', 'second.eml']],
    unlink(file) {
      if (file.endsWith('second.eml') && fail) { fail = false; throw new Error('simulated unlink failure'); }
      fs.unlinkSync(file);
    }});
  assert.throws(f.run, /preserve .*receipt\.json/);
  assert(!fs.existsSync(f.owned) && fs.existsSync(path.join(f.store, 'second.eml')));
  assert.equal(fs.statSync(f.receipt).mode & 0o777, 0o600);
  assert.equal(fs.statSync(path.dirname(f.receipt)).mode & 0o777, 0o700);
  f.restart(); f.run();
  assert.equal(f.mutations.length, 1);
  assert(!fs.existsSync(path.join(f.store, 'second.eml')) && !fs.existsSync(f.receipt));
  assert.equal(fs.readFileSync(f.unrelated, 'utf8'), 'preserve me');
});

test('a failed SQL transaction can retry from its receipt while preserving the original routing marks', t => {
  const faults = {sqlFailure: true};
  const f = fixture(t, faults);
  assert.throws(f.run, /simulated SQL failure/);
  faults.sqlFailure = false; f.restart(); f.run();
  assert.equal(f.mutations.length, 1);
  assert(!fs.existsSync(f.owned) && !fs.existsSync(f.receipt));
});

test('a pending unlink refuses a replaced file and retains its recovery receipt', t => {
  let fail = true;
  const f = fixture(t, {unlink(file) { if (fail) { fail = false; throw new Error('simulated unlink failure'); } fs.unlinkSync(file); }});
  assert.throws(f.run, /pending/);
  fs.unlinkSync(f.owned); fs.writeFileSync(f.owned, 'different file');
  f.restart(); assert.throws(f.run, /pending/);
  assert.equal(fs.readFileSync(f.owned, 'utf8'), 'different file');
  assert(fs.existsSync(f.receipt));
});

test('unremoved child rows keep files and the private receipt for diagnosis', t => {
  const f = fixture(t, {remaining: '1'});
  assert.throws(f.run, /child rows remain/);
  assert(fs.existsSync(f.owned) && fs.existsSync(f.receipt));
});
