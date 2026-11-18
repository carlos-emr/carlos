/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./lib/playwright-harness');
const pdf = require('./lib/stored-pdf-documents');
const {createOwnedAnnotationFixture} = require('./lib/owned-annotation-fixture');
test('annotation fixture refuses non-disposable targets before creating SQL resources', t => {
  const old = process.env.CARLOS_DISPOSABLE_HOST;
  t.after(() => { if (old === undefined) delete process.env.CARLOS_DISPOSABLE_HOST; else process.env.CARLOS_DISPOSABLE_HOST = old; });
  delete process.env.CARLOS_DISPOSABLE_HOST;
  assert.throws(() => createOwnedAnnotationFixture({}), /requires a disposable/);
});
test('annotation setup disposes SQL when document-store discovery fails', t => {
  const old = process.env.CARLOS_DISPOSABLE_HOST, originalSql = h.createSqlRunner, originalDirectory = pdf.directory;
  t.after(() => { h.createSqlRunner = originalSql; pdf.directory = originalDirectory;
    if (old === undefined) delete process.env.CARLOS_DISPOSABLE_HOST; else process.env.CARLOS_DISPOSABLE_HOST = old; });
  process.env.CARLOS_DISPOSABLE_HOST = 'true';
  let disposed = 0;
  h.createSqlRunner = () => ({dispose: () => { disposed++; }});
  pdf.directory = () => { throw new Error('FAKE storage discovery failure'); };
  assert.throws(() => createOwnedAnnotationFixture({mysql: {}}), /storage discovery/);
  assert.equal(disposed, 1);
});
