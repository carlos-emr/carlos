/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
const {withPropertyProfile, propertiesFor} = require('./lib/property-profiles');
for (const failAt of ['none', 'check', 'restart']) test(`profile restores byte-exact properties after ${failAt}`, async t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-profile-'));
  t.after(() => fs.rmSync(dir, {recursive: true, force: true}));
  const file = path.join(dir, 'properties'), original = Buffer.from('# keep\r\nlogin_lock=true\r\nlast=no-final-newline');
  fs.writeFileSync(file, original, {mode: 0o640}); fs.chmodSync(file, 0o640); let restarts = 0, ran = false;
  const call = withPropertyProfile('address-lock', () => {
    ran = true; assert.match(fs.readFileSync(file, 'utf8'), /login_lock=false/);
    if (failAt === 'check') throw new Error('check failure'); return 0;
  }, {env: {CARLOS_DISPOSABLE_HOST: 'true'}, file, readRestarts: () => '0', restart: () => {
    restarts++; if (failAt === 'restart' && restarts === 1) throw new Error('restart failure');
  }});
  if (failAt === 'none') assert.equal(await call, 0); else await assert.rejects(call, /failure/);
  assert.equal(restarts, 2); assert.equal(ran, failAt !== 'restart');
  assert.deepEqual(fs.readFileSync(file), original); assert.equal(fs.statSync(file).mode & 0o777, 0o640);
});
test('unknown profiles and missing consent programs fail before mutation', () => {
  assert.throws(() => propertiesFor('typo'), /Unknown/);
  assert.throws(() => propertiesFor('private-consent', {}), /CONSENT_PROGRAM_ID/);
});
