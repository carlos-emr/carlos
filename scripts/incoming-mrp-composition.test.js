/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, either version 2 or
 * (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
// Execute the actual caller and both unchanged helpers against shared state.
// No application, MySQL or network calls; only owned temporary queue files.
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const vm = require('node:vm');
const {createRequire} = require('node:module');
const scripts = __dirname;
const nativeRequire = createRequire(path.join(scripts, 'incoming-docs-delete-pdf-mrp-playwright-checks.js'));
const original = path.join(scripts, 'incoming-docs-delete-pdf-mrp-playwright-checks.js');
const candidate = process.env.COMPOSITION_CALLER || original;

// Reuse the existing program test's keyed row/digest simulation, adding the
// provider predicate it intentionally did not need for its standalone tests.
function environment(options = {}) {
  const source = path.join(scripts, 'incoming-filing-program-fixture.test.js');
  const programRequire = createRequire(source);
  const module = {exports: {}};
  vm.runInNewContext(fs.readFileSync(source, 'utf8') + '\nmodule.exports={fixture};', {
    module, require: name => name === 'node:test' ? () => {} : programRequire(name),
  }, {filename: source});
  const f = module.exports.fixture();
  const state = {mrp: f.session.provider, exists: false, fingerprint: 'a'.repeat(64), owned: true};
  const events = [];
  const {sql} = f.session;
  const value = sql.value.bind(sql), rows = sql.rows.bind(sql), execute = sql.execute.bind(sql);
  const patientMatches = query => {
    const provider = /AND provider_no='([^']+)'/.exec(query)?.[1];
    return state.owned && (!provider || provider === state.mrp);
  };
  sql.rows = query => {
    if (query.includes("TABLE_NAME='provider'")) return [['provider_no'], ['last_name'], ['first_name'], ['lastUpdateUser']];
    if (query.startsWith('SELECT document_no,docfilename')) return [];
    return rows(query);
  };
  sql.value = query => {
    if (query.startsWith('SELECT provider_no FROM demographic')) return state.owned ? state.mrp : '';
    if (query.startsWith('SELECT COUNT(*) FROM demographic')) return patientMatches(query) ? '1' : '0';
    if (query.startsWith('SELECT (SELECT COUNT(*) FROM provider')) return '0';
    if (query.startsWith('SELECT (SELECT COUNT(*) FROM demographic')) return '0';
    if (query.startsWith('SELECT COUNT(*) FROM incomingLabRules')) return '0';
    if (query.startsWith('SELECT COUNT(*) FROM provider')) return state.exists ? '1' : '0';
    if (query.startsWith('SELECT SHA2(JSON_ARRAY') && query.includes('FROM provider WHERE')) return state.exists ? state.fingerprint : '';
    return value(query);
  };
  sql.execute = query => {
    if (query.startsWith('INSERT INTO provider')) {
      events.push('mrp-insert'); state.exists = true;
      state.number = /VALUES \('([0-9]+)'/.exec(query)[1];
      if (options.lostMrpInsertReply) throw new Error('lost MRP INSERT acknowledgement');
      return;
    }
    if (query.startsWith('UPDATE demographic')) {
      state.mrp = /SET provider_no='([0-9]+)'/.exec(query)[1];
      events.push(state.mrp === f.session.provider ? 'mrp-restored' : 'mrp-assigned');
      return;
    }
    if (query.startsWith('DELETE FROM provider')) {events.push('mrp-deleted');state.exists = false;return;}
    if (query.startsWith('DELETE FROM `admission`')) {
      assert.equal(state.mrp, f.session.provider, 'Program deletion must see restored login provider');
      events.push('admission-deleted');
    }
    execute(query);
  };
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'incoming-composition-'));
  fs.mkdirSync(path.join(temp, '1', 'File'), {recursive: true});
  f.session.schedule = {locator: () => ({first: () => ({})})};
  const sentinel = new Error('intentional offline stop before first UI call');
  const callerModule = {exports: {}};
  const scopedRequire = name => {
    if (name === './lib/playwright-ui') return {clickOpensPopupOrNavigates: async () => {events.push('first-ui');throw sentinel;}};
    if (name === './incoming-pdf-extraction-playwright-checks') return {fixturePdf: text => Buffer.from('%PDF-synthetic-' + text)};
    return nativeRequire(name);
  };
  vm.runInNewContext(fs.readFileSync(candidate, 'utf8'), {module: callerModule, require: scopedRequire,
    Buffer, console, process: {env: {INCOMINGDOCUMENT_DIR: temp, DOCUMENT_DIR: temp}}}, {filename: candidate});
  return {...f, state, events, sentinel, temp, workflow: callerModule.exports.workflow,
    dispose() {fs.rmSync(temp, {recursive: true, force: true});},
    cleanup() {const failures = [];for (const fn of [...f.callbacks].reverse()) try {fn();} catch (e) {failures.push(e);}return failures;}};
}

test('actual caller composes login-provider admission with isolated MRP then unwinds filing, MRP and program', async () => {
  const f = environment();
  try {
    await assert.rejects(f.workflow(f.session), error => error === f.sentinel);
    assert.equal(f.tables.admission.length, 1);
    assert.equal(f.tables.admission[0].provider_no, f.session.provider);
    assert.equal(f.state.mrp, f.state.number);assert.notEqual(f.state.mrp, f.session.provider);
    assert.equal(fs.readdirSync(path.join(f.temp, '1', 'File')).length, 3);
    assert.deepEqual(f.cleanup(), []);
    assert.equal(fs.readdirSync(path.join(f.temp, '1', 'File')).length, 0);
    assert.equal(f.state.mrp, f.session.provider);assert.equal(f.state.exists, false);
    assert.equal(f.tables.admission.length, 0);
    assert(f.events.indexOf('mrp-restored') < f.events.indexOf('admission-deleted'));
  } finally {f.dispose();}
});

test('lost isolated-MRP INSERT reply still removes only owned provider after program registration', async () => {
  const f = environment({lostMrpInsertReply: true});
  try {
    await assert.rejects(f.workflow(f.session), /lost MRP INSERT/);
    assert.equal(f.tables.admission.length, 1);assert.equal(f.state.mrp, f.session.provider);
    assert.equal(fs.readdirSync(path.join(f.temp, '1', 'File')).length, 0);
    assert.deepEqual(f.cleanup(), []);assert.equal(f.state.exists, false);
    assert.equal(f.tables.admission.length, 0);
  } finally {f.dispose();}
});

test('changed patient ownership refuses both cleanup domains and retains child identities', async () => {
  const f = environment();
  try {
    await assert.rejects(f.workflow(f.session), error => error === f.sentinel);
    f.state.owned = false;
    const writes = f.writes.length, failures = f.cleanup();
    assert.equal(failures.length, 3);assert.equal(f.writes.length, writes);
    assert.equal(f.state.exists, true);assert.equal(f.tables.admission.length, 1);
  } finally {f.dispose();}
});

test('changed owned provider fingerprint blocks MRP deletion and dependent program cleanup', async () => {
  const f = environment();
  try {
    await assert.rejects(f.workflow(f.session), error => error === f.sentinel);
    f.state.fingerprint = 'b'.repeat(64);
    const failures = f.cleanup();assert.equal(failures.length, 2);
    assert.equal(f.state.exists, true);assert.equal(f.state.mrp, f.state.number);
    assert.equal(f.tables.admission.length, 1);
    assert.equal(f.events.includes('mrp-deleted'), false);assert.equal(f.events.includes('admission-deleted'), false);
  } finally {f.dispose();}
});
