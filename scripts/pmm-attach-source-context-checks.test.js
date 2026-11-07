/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {assertCopiedProvenance, assertCopyLocks, assertStagedDefaults} = require('./lib/pmm-attach-source-context-checks');
test('PMM copy requires the exact authored source provenance and rejects ambiguous sources', () => {
  const columns = ['note_id', 'program_no', 'provider_no', 'reporter_caisi_role', 'reporter_program_team'];
  const source = ['10', '1', '2', '3', '4'], copy = ['11', '1', '2', '3', '4'];
  assertCopiedProvenance([source], copy, columns, '10');
  assert.throws(() => assertCopiedProvenance([source, source], copy, columns, '10'), /ambiguous/);
  for (let i = 1; i < columns.length; i++) {
    const changed = [...copy]; changed[i] = 'foreign';
    assert.throws(() => assertCopiedProvenance([source], changed, columns, '10'), /changed/);
  }
});
test('PMM lock and staged-default comparisons cannot accept lost or foreign state', () => {
  const before = [['1', 'provider', 'patient', 'note', 'other-session']];
  const context = {provider: 'provider', patient: 'patient', note: 'note', session: 'session.node'};
  assertCopyLocks(before, [...before, ['2', 'provider', 'patient', 'note', 'session']], context);
  assert.throws(() => assertCopyLocks(before, [], context), /preexisting/);
  assert.throws(() => assertCopyLocks(before, [...before, ['2', 'foreign', 'patient', 'note', 'session']], context), /unrelated/);
  assertStagedDefaults([['owned']], [['owned']]);
  assert.throws(() => assertStagedDefaults([], [['owned']]), /retaining/);
});
