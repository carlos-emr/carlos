/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {assertCopiedProvenance, assertCopyLocks, assertStagedDefaults, forgeSignature} = require('./lib/pmm-attach-source-context-checks');
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
test('a forged chart context always carries another MAC, also when the genuine one ends in A', () => {
  const payload = Buffer.from('3076:10001').toString('base64url');
  // Every value the last character of a 32-byte MAC can take, "A" among them, and both middle cases.
  for (let last = 0; last < 16; last++) for (const middle of [0x00, 0x41, 0xff]) {
    const mac = Buffer.alloc(32, middle); mac[31] = last << 4 | last;
    const signature = mac.toString('base64url');
    assert.equal(signature.length, 43);
    const token = `${payload}.${signature}`;
    const forged = forgeSignature(token);
    const [forgedPayload, forgedSignature] = forged.split('.');
    assert.equal(forgedPayload, payload);
    assert.match(forgedSignature, /^[A-Za-z0-9_-]{43}$/);
    assert.equal([...forgedSignature].filter((character, index) => character !== signature[index]).length, 1);
    assert.equal(forgedSignature.at(-1), signature.at(-1), 'the last character, whose low bits the decoder discards, is left alone');
    assert.equal(Buffer.from(forgedSignature, 'base64url').equals(mac), false, 'the forged MAC is another MAC');
  }
  // What the check did before: a final "A" became "B", which decodes to the same MAC.
  const endsInA = Buffer.alloc(32, 7); endsInA[31] = 0;
  const genuine = endsInA.toString('base64url');
  assert.equal(genuine.at(-1), 'A');
  assert.equal(Buffer.from(`${genuine.slice(0, -1)}B`, 'base64url').equals(endsInA), true);
  assert.throws(() => forgeSignature('no-signature'), /no signature to forge/);
});
