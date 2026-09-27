/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { withOhipTestLock } = require('./lib/ohip-test-lock');

test('real POSIX lock rejects another process and releases after a failed check', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'ohip-lock-'));
  fs.writeFileSync(path.join(directory, '.carlos-ohip-disk.lock'), '');
  const probe = () => spawnSync('python3', ['-c',
    'import fcntl,sys\nwith open(sys.argv[1], "a") as f: fcntl.lockf(f, fcntl.LOCK_EX | fcntl.LOCK_NB)\n',
    path.join(directory, '.carlos-ohip-disk.lock')]);
  const expected = new Error('browser failure');
  try {
    await assert.rejects(withOhipTestLock(directory, async () => {
      assert.equal(probe().status, 1);
      throw expected;
    }), error => error === expected);
    assert.equal(probe().status, 0);
    assert.equal(await withOhipTestLock(directory, async () => 'completed'), 'completed');
  } finally {
    fs.rmSync(directory, { recursive: true });
  }
});

test('missing application lock prevents the check without creating a root-owned lock', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'ohip-lock-'));
  let ran = false;
  try {
    await assert.rejects(withOhipTestLock(directory, async () => { ran = true; }));
    assert.equal(ran, false);
    assert.deepEqual(fs.readdirSync(directory), []);
  } finally {
    fs.rmSync(directory, { recursive: true });
  }
});
