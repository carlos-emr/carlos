/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const pdf = require('./lib/stored-pdf-documents');
const {createOwnedAnnotationFixture} = require('./lib/owned-annotation-fixture');

function fixture(t, foreignRoute, ignoreLinkDelete = false) {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'annotation-cleanup-test-'));
  t.after(() => fs.rmSync(store, {recursive: true, force: true}));
  const state = {document: true, link: true, patient: true, disposed: false};
  let doc, receipt;
  const mkdtemp = fs.mkdtempSync;
  t.mock.method(fs, 'mkdtempSync', (...args) => {
    const directory = mkdtemp(...args);
    if (path.basename(directory).startsWith('carlos-annotation-fixture-')) {
      receipt = directory;
      t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
    }
    return directory;
  });
  const sql = {
    value(query) {
      if (query.includes('FROM security')) return '999998';
      if (query.startsWith('INSERT INTO demographic')) return '42';
      if (query.includes('FROM patientLabRouting')) return foreignRoute ? '1' : '0';
      if (query.includes('FROM ctl_document')) return query.includes('AND NOT') || !state.link ? '0' : '1';
      if (query.includes('FROM demographic')) return state.patient ? '1' : '0';
      assert.fail('Unexpected SQL read: ' + query);
    },
    rows(query) {
      if (query.startsWith('SELECT * FROM document')) return [[doc.id, doc.label, doc.filename]];
      if (query.startsWith('SELECT docdesc')) return [[doc.label, doc.filename, '999998', '']];
      if (query.startsWith('SELECT module,module_id')) return state.link ? [['demographic', '42']] : [];
      if (query.startsWith('SELECT document_no FROM document')) return state.document ? [[doc.id]] : [];
      assert.fail('Unexpected SQL rows: ' + query);
    },
    execute(query) {
      if (query.includes('DELETE FROM ctl_document') && !ignoreLinkDelete) state.link = false;
      if (query.includes('DELETE FROM document')) state.document = false;
      else if (query.startsWith('DELETE FROM demographic')) state.patient = false;
      else assert.fail('Unexpected SQL write: ' + query);
    },
    dispose() {state.disposed = true;},
  };
  t.mock.method(h, 'createSqlRunner', () => sql);
  t.mock.method(pdf, 'directory', () => store);
  t.mock.method(pdf, 'seedOwnedPdfDocuments', ({docs}) => {
    [doc] = docs;
    doc.id = '7';
    fs.writeFileSync(doc.file, pdf.textPdf(doc.label));
  });
  const previous = process.env.CARLOS_DISPOSABLE_HOST;
  process.env.CARLOS_DISPOSABLE_HOST = 'true';
  t.after(() => {
    if (previous === undefined) delete process.env.CARLOS_DISPOSABLE_HOST;
    else process.env.CARLOS_DISPOSABLE_HOST = previous;
  });
  const owned = createOwnedAnnotationFixture({mysql: {}, testUser: 'synthetic'});
  owned.create();
  return {owned, state, doc, receipt};
}

test('annotation cleanup retains a PDF, all owned rows and its receipt after a foreign patient route appears', t => {
  const {owned, state, doc, receipt} = fixture(t, true);
  const before = fs.readFileSync(doc.file);
  assert.throws(() => owned.cleanup(), /another patient link/);
  assert.equal(state.document, true);
  assert.equal(state.link, true);
  assert.equal(state.patient, true);
  assert.deepEqual(fs.readFileSync(doc.file), before);
  assert.equal(fs.existsSync(receipt), true);
  assert.equal(state.disposed, true);
});

test('annotation cleanup still removes its exact unreferenced fixture and receipt', t => {
  const {owned, state, doc, receipt} = fixture(t, false);
  owned.cleanup();
  assert.equal(state.document, false);
  assert.equal(state.link, false);
  assert.equal(state.patient, false);
  assert.equal(fs.existsSync(doc.file), false);
  assert.equal(fs.existsSync(receipt), false);
  assert.equal(state.disposed, true);
});

test('annotation cleanup cannot report success when its patient link survives deletion', t => {
  const {owned, state, receipt} = fixture(t, false, true);
  assert.throws(() => owned.cleanup(), /links remain/);
  assert.equal(state.link, true);
  assert.equal(state.patient, true);
  assert.equal(fs.existsSync(receipt), true);
});

test('annotation cleanup preserves a replacement swapped into its PDF path during retirement', t => {
  const {owned, state, doc, receipt} = fixture(t, false);
  const rename = fs.renameSync;
  t.mock.method(fs, 'renameSync', (from, to) => {
    if (from === doc.file) {
      fs.unlinkSync(from);
      fs.writeFileSync(from, 'foreign replacement');
    }
    return rename(from, to);
  });
  assert.throws(() => owned.cleanup(), /changed/);
  assert.equal(fs.readFileSync(doc.file, 'utf8'), 'foreign replacement');
  assert.equal(state.patient, true);
  assert.equal(fs.existsSync(receipt), true);
});
