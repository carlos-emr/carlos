/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {prepareIsolatedMrp} = require('./lib/incoming-isolated-mrp-fixture');

function fixture(options = {}) {
  const callbacks = [], writes = [];
  const state = {mrp: '999998', exists: false, fingerprint: 'a'.repeat(64), owned: true, ownRule: false, foreignRef: false, selections: 0};
  const session = {patient: '42', provider: '999998', marker: 'FAKE-PW1234567890abcdef', cleanup: fn => callbacks.push(fn)};
  session.sql = {
    rows: () => [['provider_no'], ['last_name'], ['first_name'], ['lastUpdateUser']],
    value(query) {
      if (query.includes('SELECT provider_no FROM demographic')) return state.mrp;
      if (query.includes('SELECT SHA2(JSON_ARRAY')) return state.exists && state.owned ? state.fingerprint : '';
      if (query.startsWith('SELECT COUNT(*) FROM incomingLabRules')) return state.ownRule ? '1' : '0';
      if (query.startsWith('SELECT COUNT(*) FROM provider')) return state.exists ? '1' : '0';
      if (query.startsWith('SELECT (SELECT COUNT(*) FROM provider')) return state.selections++ < (options.collisions || 0) ? '1' : '0';
      if (query.startsWith('SELECT (SELECT COUNT(*) FROM demographic')) return state.foreignRef ? '1' : '0';
      throw new Error('Unexpected test read');
    },
    execute(query) {
      writes.push(query);
      if (query.startsWith('INSERT INTO provider')) {
        assert.equal(callbacks.length, 1, 'Ownership cleanup must precede INSERT');
        state.exists = true;state.number = /VALUES \('([0-9]+)'/.exec(query)[1];
        if (options.lostInsertReply) throw new Error('Lost INSERT reply');
      } else if (query.startsWith('UPDATE demographic')) {
        assert(query.includes("demographic_no=42 AND last_name='FAKE-PW1234567890abcdef'"));
        state.mrp = /SET provider_no='([0-9]+)'/.exec(query)[1];
      } else if (query.startsWith('DELETE FROM provider')) {
        assert(query.includes("last_name='FAKE-PW1234567890abcdef'"));
        assert(query.includes("first_name='Isolated MRP'"));
        assert(query.includes('SHA2(JSON_ARRAY('));state.exists = false;
      } else throw new Error('Unexpected fixture write');
    },
  };
  return {session, callbacks, writes, state};
}

test('owns an unused provider, changes only its patient and never edits existing forwarding rules', () => {
  const f = fixture({collisions: 2});const owned = prepareIsolatedMrp(f.session);
  assert.equal(f.state.selections, 3);assert.equal(f.state.mrp, owned.number);
  owned.verify();f.callbacks[0]();f.callbacks[0]();
  assert.equal(f.state.exists, false);assert.equal(f.state.mrp, f.session.provider);
  assert.equal(f.writes.filter(q => q.startsWith('DELETE FROM provider')).length, 1);
  assert.equal(f.writes.some(q => /^(UPDATE|DELETE|INSERT).*incomingLabRules/.test(q)), false);
});

test('a committed INSERT with a lost reply is recovered by its exact registered owner scope', () => {
  const f = fixture({lostInsertReply: true});assert.throws(() => prepareIsolatedMrp(f.session), /Lost INSERT/);
  assert.equal(f.state.exists, true);f.callbacks[0]();
  assert.equal(f.state.exists, false);assert.equal(f.state.mrp, f.session.provider);
  assert.equal(f.writes.some(q => q.startsWith('UPDATE demographic')), false);
});

test('a replaced or modified provider is retained before any cleanup writes', () => {
  for (const change of [{owned: false}, {fingerprint: 'b'.repeat(64)}]) {
    const f = fixture();prepareIsolatedMrp(f.session);Object.assign(f.state, change);
    const before = f.writes.length;assert.throws(() => f.callbacks[0](), /ownership/);
    assert.equal(f.writes.length, before);assert.equal(f.state.exists, true);
  }
});

test('new forwarding rules or foreign references are preserved and prevent provider deletion', () => {
  for (const change of [{ownRule: true}, {foreignRef: true}]) {
    const f = fixture();const owned = prepareIsolatedMrp(f.session);Object.assign(f.state, change);
    const before = f.writes.length;
    if (change.ownRule) assert.throws(() => owned.verify(), /forwarding/);
    assert.throws(() => f.callbacks[0](), /forwarding|references/);
    assert.equal(f.writes.length, before);assert.equal(f.state.exists, true);
  }
});

test('a changed patient MRP cannot trigger deletion of the owned provider', () => {
  const f = fixture();prepareIsolatedMrp(f.session);f.state.mrp = 'ForeignProvider';
  const before = f.writes.length;assert.throws(() => f.callbacks[0](), /patient provider changed/);
  assert.equal(f.writes.length, before);assert.equal(f.state.exists, true);
});
