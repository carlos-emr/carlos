/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');

const source = fs.readFileSync(path.join(__dirname, 'document-add-new-type-playwright-checks.js'), 'utf8');

test('shouldCleanUncapturedUploadResidue_whenTheBrowserFailsAfterSaving', async () => {
  const marker = 'owned-marker';
  const documents = new Map([['99', 'unrelated document']]);
  const residueIds = new Set(['99']);
  let cleanup;
  const sql = {
    value: () => '0',
    execute() {},
    rows(query) {
      assert.equal(query, `SELECT document_no FROM document WHERE docdesc LIKE '${marker}%'`);
      return [...documents].filter(([, label]) => label.startsWith(marker)).map(([id]) => [id]);
    },
  };
  const module = { exports: {} };
  vm.runInNewContext(source, {
    module,
    require(name) {
    if (name === './lib/authenticated-probe') return require('./lib/authenticated-probe');
      if (name === 'node:fs') return { mkdtempSync: () => '/tmp/synthetic-doc-test', readdirSync: () => [], writeFileSync() {}, rmSync() {} };
      if (name.startsWith('node:')) return require(name);
      if (name === './lib/playwright-harness') return h;
      if (name === './lib/workflow-session' || name === './lib/master-record-link') return {};
      if (name === './lib/document-residue') return {
        markDocumentResidue: () => ({}),
        removeDocumentResidue(_sql, _mark, ids) { ids.forEach(id => residueIds.delete(id)); },
      };
      if (name === './lib/stored-pdf-documents') return {
        directory: () => '/tmp/synthetic-doc-store',
        textPdf: () => Buffer.from('synthetic PDF'),
        ownedPdfDocuments: () => [{ label: `${marker} A`, file: '/tmp/synthetic-doc-store/owned.pdf' }],
        seedOwnedPdfDocuments({ docs }) { docs[0].id = '41'; documents.set('41', docs[0].label); },
        removeOwnedPdfDocuments({ docs }) {
          assert.equal(docs[1].id, null, 'the failed redirect has not returned the new upload ID');
          for (const [id, label] of documents) if (label.startsWith(marker)) documents.delete(id);
        },
        assertOwnedPdfDocumentsRemoved() { assert.deepEqual([...documents.keys()], ['99']); },
      };
      throw new Error(`Unexpected module ${name}`);
    },
  });
  await assert.rejects(module.exports.workflow({
    sql, marker, patient: '7', provider: '8',
    cleanup(callback) { cleanup = callback; },
    async step() {
      // Simulate the server saving the upload before the browser reports its ID.
      documents.set('42', `${marker} N`);
      residueIds.add('42');
      throw new Error('redirect timed out');
    },
  }), /redirect timed out/);
  cleanup();
  assert.deepEqual([...residueIds], ['99'], 'clean the saved upload residue and preserve unrelated rows');
});
