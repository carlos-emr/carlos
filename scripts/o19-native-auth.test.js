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
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');
const { readNativeClinicianCredentials, validateNativeImportedCredentials, proveNativeClinicianLogin, redactNativeAuthError } = require('./lib/o19-native-auth');
const clinician = { userName: 'SyntheticClinician', pin: '1234' };
const credentials = { user: clinician.userName, password: 'Public-Synthetic-Legacy!', pin: clinician.pin };
// Builds the OSCAR 19 signed-decimal SHA-1 representation the helper verifies, for a public synthetic fixture.
// codeql[js/insufficient-password-hash] -- reproduces the imported legacy format under test; nothing is stored.
function legacy(password) { return Array.from(crypto.createHash('sha1').update(password).digest(), x => String(x < 128 ? x : x - 256)).join(''); } // nosemgrep: javascript.node-stdlib.cryptography.crypto-weak-algorithm.crypto-weak-algorithm -- legacy format fixture
function initial(change = {}) { return { userName: clinician.userName, password: legacy(credentials.password), forcePasswordReset: '1', ...change }; }
const adminRow = { forcePasswordReset: '1' };
function fixture(t, contents = JSON.stringify(credentials)) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-o19-native-auth-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const file = path.join(dir, 'private.json'); fs.writeFileSync(file, contents, { mode: 0o600 }); return file;
}

test('current-owner0600regular file binds exact discovered clinician and PIN', t => {
  assert.deepEqual(readNativeClinicianCredentials(fixture(t), clinician), credentials);
});
test('world/group-readable files, symlinks and hardlinks are refused', t => {
  const file = fixture(t); fs.chmodSync(file, 0o640);
  assert.throws(() => readNativeClinicianCredentials(file, clinician), /0600/);
  fs.chmodSync(file, 0o600); const link = file + '.link'; fs.symlinkSync(file, link);
  assert.throws(() => readNativeClinicianCredentials(link, clinician));
  fs.unlinkSync(link); fs.linkSync(file, link);
  assert.throws(() => readNativeClinicianCredentials(file, clinician), /0600/);
});
test('foreign identity/PIN, missing clinician, malformed fields and oversized file fail without secrets in errors', t => {
  const secret = 'PRIVATE-PASSWORD-MUST-NOT-APPEAR';
  for (const value of [{ ...credentials, user: 'ForeignUser' }, { ...credentials, pin: '9999' },
    { ...credentials, extra: secret }, { ...credentials, password: '' }, { ...credentials, password: 123 }]) {
    const file = fixture(t, JSON.stringify(value));
    assert.throws(() => readNativeClinicianCredentials(file, clinician), e => !e.message.includes(secret));
  }
  assert.throws(() => readNativeClinicianCredentials(fixture(t), null));
  assert.throws(() => readNativeClinicianCredentials(fixture(t, '{' + secret), clinician), e => !e.message.includes(secret));
  assert.throws(() => readNativeClinicianCredentials(fixture(t, 'x'.repeat(16385)), clinician), /size/);
  assert.throws(() => readNativeClinicianCredentials('relative.json', clinician), /absolute/);
});
test('actual legacy secret and genuine imported reset flags are required before login', () => {
  validateNativeImportedCredentials(credentials, initial(), adminRow);
  for (const change of [{ password: 'wrong-carried-hash' }, { forcePasswordReset: '0' }, { userName: 'Other' }]) {
    assert.throws(() => validateNativeImportedCredentials(credentials, initial(change), adminRow));
  }
  assert.throws(() => validateNativeImportedCredentials(credentials, initial(), { forcePasswordReset: '0' }));
});
test('native proof drives real credential then reset/new-session/old-refusal in order without any write callback', async () => {
  const steps = [];
  await proveNativeClinicianLogin({ credentials, initialRow: initial(), adminRow, newPassword: 'New-Synthetic-Only!',
    reset: async (actual, replacement) => { assert.equal(actual, credentials); assert.equal(replacement, 'New-Synthetic-Only!'); steps.push('reset'); },
    readRow: () => { steps.push('read'); return initial({ password: '{bcrypt}$2b$12$newSyntheticHash', forcePasswordReset: '0' }); },
    signIn: async () => { steps.push('fresh-new-login'); }, rejectOld: async () => { steps.push('old-refusal'); } });
  assert.deepEqual(steps, ['reset', 'read', 'fresh-new-login', 'old-refusal']);
});
test('failed reset, fresh login or old-password refusal cannot yield native proof success', async () => {
  const good = { credentials, initialRow: initial(), adminRow, newPassword: 'New-Synthetic!', reset: async () => {},
    readRow: () => initial({ password: '{bcrypt}$2b$12$newSyntheticHash', forcePasswordReset: '0' }), signIn: async () => {}, rejectOld: async () => {} };
  for (const step of ['reset', 'signIn', 'rejectOld']) {
    await assert.rejects(proveNativeClinicianLogin({ ...good, [step]: async () => { throw new Error('Expected genuine UI failure'); } }));
  }
  await assert.rejects(proveNativeClinicianLogin({ ...good, readRow: () => initial() }));
  await assert.rejects(proveNativeClinicianLogin({ ...good, initialRow: initial({ forcePasswordReset: '0' }) }));
});

function loadDriver(native) {
  const queries = [], logs = [];
  const filename = path.join(__dirname, 'o19-migrated-smoke-playwright-checks.js');
  let code = fs.readFileSync(filename, 'utf8'); const marker = '(async () => {';
  code = code.slice(0, code.indexOf(marker));
  code += '\nmodule.exports = { restoreSecurityRows, restorePoints };';
  const sandbox = { module: { exports: {} }, URL, URLSearchParams,
    console: { log: (...v) => logs.push(v.join(' ')), error: () => {} },
    process: { env: native ? { O19_NATIVE_CLINICIAN_CREDENTIALS: '/private/native.json' } : {}, on: () => {}, getuid: () => process.getuid() },
    require: name => {
      if (name === 'playwright') return { chromium: {} };
      if (name === './lib/playwright-harness') return { ...require('./lib/playwright-harness'), shouldIgnoreHttpsErrors: () => true, EXIT_SKIP: 2 };
      if (name === './lib/o19-native-auth') return require('./lib/o19-native-auth');
      if (name === './lib/deb-login-navigation') return {};
      if (name === 'child_process') return { execFileSync: (cmd, args, options) => { queries.push(args.at(-1)); return ''; } };
      return require(name);
    } };
  vm.runInNewContext(code, sandbox, { filename }); return { ...sandbox.module.exports, queries, logs };
}
test('actual driver native cleanup never executes credential SQL even with a remembered row; legacy cleanup still restores', () => {
  const point = { userName: 'SyntheticClinician', password: 'prior-synthetic-only', forcePasswordReset: '1', passwordUpdateDate: 'NULL' };
  const native = loadDriver(true); native.restorePoints.push(point); native.restoreSecurityRows();
  assert.deepEqual(native.queries, []);
  const compat = loadDriver(false); compat.restorePoints.push(point); compat.restoreSecurityRows();
  assert.equal(compat.queries.length, 1); assert.match(compat.queries[0], /UPDATE security SET password/);
});


test('a credential FIFO is refused without waiting for a writer', t => {
  const file = fixture(t); fs.unlinkSync(file);
  const made = require('node:child_process').spawnSync('mkfifo', ['-m', '600', file]);
  assert.equal(made.status, 0);
  const started = Date.now();
  assert.throws(() => readNativeClinicianCredentials(file, clinician), /regular/);
  assert(Date.now() - started < 2000, 'Regular-file validation blocked on a FIFO');
});

test('native file or require-native mode refuses absent genuine P4 credentials before SQL or browser', async t => {
  for (const native of [{ O19_REQUIRE_NATIVE_AUTH: 'true' }, { O19_NATIVE_CLINICIAN_CREDENTIALS: fixture(t) }]) {
    const exits = [], queries = [], browserCalls = [];
    const filename = path.join(__dirname, 'o19-migrated-smoke-playwright-checks.js');
    const code = fs.readFileSync(filename, 'utf8').replace('(async () => {', 'module.exports.mainPromise = (async () => {');
    const sandbox = { module: { exports: {} }, URL, URLSearchParams,
      console: { log: () => {}, error: () => {} },
      process: { env: { ...native, O19_ADMIN_CREDENTIALS: fixture(t) + '.missing' }, on: () => {}, getuid: () => process.getuid(), exit: code => exits.push(code) },
      require: name => {
        if (name === 'playwright') return { chromium: { launch: () => { browserCalls.push(1); throw new Error('Browser must not launch'); } } };
        if (name === './lib/playwright-harness') return { ...require('./lib/playwright-harness'), shouldIgnoreHttpsErrors: () => true, EXIT_SKIP: 2 };
        if (name === './lib/o19-native-auth') return require('./lib/o19-native-auth');
        if (name === './lib/deb-login-navigation') return {};
        if (name === 'child_process') return { execFileSync: () => { queries.push(1); throw new Error('SQL must not execute'); } };
        return require(name);
      } };
    vm.runInNewContext(code, sandbox, { filename });
    await sandbox.module.exports.mainPromise;
    assert.deepEqual(exits, [1]); assert.deepEqual(queries, []); assert.deepEqual(browserCalls, []);
    assert.notEqual(sandbox.process.exitCode, 2);
  }
});


test('native UI/client error messages and stacks redact literal imported passwords and PINs', () => {
  const secrets = ['literal$.*password', '0987', '', null];
  const error = new Error('fill literal$.*password with PIN0987; ordinary diagnostic retained');
  for (const stack of [false, true]) {
    const text = redactNativeAuthError(error, secrets, stack);
    assert(!text.includes(secrets[0]) && !text.includes(secrets[1]));
    assert(text.includes('ordinary diagnostic retained'));
  }
  assert.equal(redactNativeAuthError('plain issue', secrets), 'plain issue');
});


test('JSON and Playwright quoted diagnostic variants redact backslash, quote and newline secrets', () => {
  const secret = 'literal\\backslash"single\'line\nnext\ttab';
  const realSecret = secret.replace('\\n', '\n').replace('\\t', '\t');
  const json = JSON.stringify(realSecret);
  const inspect = require('node:util').inspect(realSecret, { colors: false, maxStringLength: null, compact: true });
  for (const representation of [realSecret, json, json.slice(1, -1), inspect]) {
    const error = new Error('locator.fill(' + representation + ') failed; bounded diagnostic');
    for (const stack of [false, true]) {
      const text = redactNativeAuthError(error, [realSecret], stack);
      assert(!text.includes(representation), 'Credential survived encoded UI diagnostic redaction');
      assert(text.includes('bounded diagnostic'));
    }
  }
});
