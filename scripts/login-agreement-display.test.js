/* SPDX-License-Identifier: GPL-2.0-or-later */
// Unit tests for the fixture handling in login-agreement-display-playwright-checks.js: the check
// replaces a clinic-wide file, so putting it back exactly is what keeps a run from leaving residue.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { fixture, installFixture } = require('./login-agreement-display-playwright-checks');

function tempBase(t) {
  const base = fs.mkdtempSync(path.join(os.tmpdir(), 'aua-display-'));
  t.after(() => fs.rmSync(base, { recursive: true, force: true }));
  return base;
}

test('fixture carries the markup the login page must keep and the markup it must drop', () => {
  const text = fixture('FAKE-PW-AUA-1');
  assert.match(text, /<h2>FAKE-PW-AUA-1 agreement<\/h2>/);
  assert.match(text, /<b>only<\/b>/);
  assert.match(text, /<script>/);
  assert.match(text, /onerror=/);
  assert.match(text, /style=/);
});

test('a run on a deployment with no agreement removes the file and the directory it created', (t) => {
  const base = tempBase(t);
  const putBack = installFixture(base, 'FAKE-PW-AUA-2');
  const file = path.join(base, 'login', 'AcceptableUseAgreement.txt');
  assert.equal(fs.readFileSync(file, 'utf8'), fixture('FAKE-PW-AUA-2'));
  putBack();
  assert.equal(fs.existsSync(path.join(base, 'login')), false);
});

test('a run keeps a login directory that was already there', (t) => {
  const base = tempBase(t);
  fs.mkdirSync(path.join(base, 'login'));
  fs.writeFileSync(path.join(base, 'login', 'supportLogo.png'), 'logo');
  installFixture(base, 'FAKE-PW-AUA-3')();
  assert.deepEqual(fs.readdirSync(path.join(base, 'login')), ['supportLogo.png']);
});

test('an existing agreement is put back byte-exact with its mode', (t) => {
  const base = tempBase(t);
  const file = path.join(base, 'login', 'AcceptableUseAgreement.txt');
  fs.mkdirSync(path.dirname(file));
  const original = Buffer.from('<p>Clinic agreement café</p>\n', 'utf8');
  fs.writeFileSync(file, original);
  fs.chmodSync(file, 0o640);
  const putBack = installFixture(base, 'FAKE-PW-AUA-4');
  assert.notDeepEqual(fs.readFileSync(file), original);
  fs.chmodSync(file, 0o600); // as an upload would leave it
  putBack();
  assert.deepEqual(fs.readFileSync(file), original);
  assert.equal(fs.statSync(file).mode & 0o777, 0o640);
});

test('a file changed by someone else during the run is left in place and reported', (t) => {
  const base = tempBase(t);
  const putBack = installFixture(base, 'FAKE-PW-AUA-5');
  const file = path.join(base, 'login', 'AcceptableUseAgreement.txt');
  fs.writeFileSync(file, 'an administrator uploaded this meanwhile');
  assert.throws(putBack, /changed outside this check/);
  assert.equal(fs.readFileSync(file, 'utf8'), 'an administrator uploaded this meanwhile');
});

test('a non-regular agreement path is refused before anything is written', (t) => {
  const base = tempBase(t);
  fs.mkdirSync(path.join(base, 'login', 'AcceptableUseAgreement.txt'), { recursive: true });
  assert.throws(() => installFixture(base, 'FAKE-PW-AUA-6'), /not a regular file/);
});
