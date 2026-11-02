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

function fixture(t, foreignRoute, failLinkDelete = false) {
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
      const current = state.copy && query.endsWith('document_no=' + state.copy.id) ? state.copy : doc;
      if (query.startsWith('SELECT * FROM document')) return [[current.id, current.label, current.filename]];
      if (query.startsWith('SELECT docdesc')) return [[current.label, current.filename, '999998', current === doc ? '' : 'annotated-copy-of:' + doc.id]];
      if (query.startsWith('SELECT module,module_id')) return state.link ? [['demographic', '42']] : [];
      if (query.startsWith('SELECT document_no FROM document')) return state.document ? [doc, state.copy].filter(Boolean).map(row => [row.id]) : [];
      assert.fail('Unexpected SQL rows: ' + query);
    },
    execute(query) {
      if (query.includes('DELETE FROM ctl_document') && failLinkDelete) throw new Error('injected link deletion failure');
      if (query.includes('DELETE FROM ctl_document')) state.link = false;
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
  const owned = createOwnedAnnotationFixture({mysql: {}, testUser: 'synthetic', baseUrl: new URL('http://127.0.0.1/carlos')});
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

test('annotation cleanup retains its document and PDF when the link deletion transaction fails', t => {
  const {owned, state, doc, receipt} = fixture(t, false, true);
  const before = fs.readFileSync(doc.file);
  assert.throws(() => owned.cleanup(), /injected link deletion failure/);
  assert.equal(state.document, true);
  assert.deepEqual(fs.readFileSync(doc.file), before);
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

test('annotation save records an accepted copy before navigation can discard its browser response body', async t => {
  const {owned, state, doc, receipt} = fixture(t, false);
  const listeners = {};
  let handler, finishResponse, browserBodyReads = 0, disposed = false;
  const delivered = new Promise(resolve => {finishResponse = resolve;});
  const request = {method: () => 'POST', url: () => 'http://127.0.0.1/carlos/documentManager/SaveAnnotatedDocument?docId=7',
    async response() {await delivered; return {finished: async () => null, json() {browserBodyReads++; throw new Error('No resource with given identifier found');}};}};
  const context = {on(name, callback) {listeners[name] = callback;}, async route(match, callback) {
    assert.equal(match(new URL(request.url())), true);
    assert.equal(match(new URL('http://foreign.invalid/carlos/documentManager/SaveAnnotatedDocument')), false);
    handler = callback;
  }};
  await owned.bind(context);
  listeners.request(request);
  const response = {status: () => 200, json: async () => ({success: true, demographicNo: '42', documentNo: '8'}),
    async dispose() {disposed = true;}};
  await handler({request: () => request, async fetch(options) {
    assert.equal(options.maxRedirects, 0);
    state.copy = {id: '8', label: doc.label + ' (annotated)', filename: 'owned-annotated-copy.pdf', file: path.join(path.dirname(doc.file), 'owned-annotated-copy.pdf')};
    fs.writeFileSync(state.copy.file, pdf.textPdf(state.copy.label));
    return response;
  }, async fulfill(options) {
    assert.equal(options.response, response);
    const recovery = JSON.parse(fs.readFileSync(path.join(receipt, 'recovery.json'), 'utf8'));
    assert.equal(recovery.records.some(([id]) => id === '8'), true);
    finishResponse();
  }, abort() {assert.fail('confirmed response must not be aborted');}});
  await owned.drain();
  owned.confirmClosed();
  owned.cleanup();
  assert.equal(browserBodyReads, 0);
  assert.equal(disposed, true);
  assert.equal(fs.existsSync(doc.file), false);
  assert.equal(fs.existsSync(state.copy.file), false);
  assert.equal(state.document, false);
});

test('annotation capture failure retains its rows, PDF and receipt and disposes the fetched response', async t => {
  const {owned, state, doc, receipt} = fixture(t, false);
  let handler, disposed = false, aborted = false;
  const context = {on() {}, async route(match, callback) {handler = callback;}};
  await owned.bind(context);
  await handler({request: () => ({method: () => 'POST'}), async fetch() {
    return {status: () => 200, async json() {throw new Error('capture unavailable');}, async dispose() {disposed = true;}};
  }, fulfill() {assert.fail('unconfirmed response must not be released');}, async abort() {aborted = true;}});
  await assert.rejects(owned.drain(), /capture unavailable/);
  owned.confirmClosed();
  assert.throws(() => owned.cleanup(), /unconfirmed/);
  assert.equal(state.document, true);
  assert.equal(state.link, true);
  assert.equal(state.patient, true);
  assert.equal(fs.existsSync(doc.file), true);
  assert.equal(fs.existsSync(receipt), true);
  assert.equal(disposed, true);
  assert.equal(aborted, true);
});
