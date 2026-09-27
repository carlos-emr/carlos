/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const { spawn } = require('node:child_process');
const path = require('node:path');

// The application creates this persistent lock first; the test must not change its ownership.
// Python's POSIX record lock interoperates with Java FileChannel.lock; flock does not.
async function withOhipTestLock(directory, operation) {
  const child = spawn('python3', ['-c',
    'import fcntl,sys\nwith open(sys.argv[1], "r+") as lock:\n fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)\n print("LOCKED", flush=True)\n sys.stdin.read()\n',
    path.join(directory, '.carlos-ohip-disk.lock')], { stdio: ['pipe', 'pipe', 'pipe'], timeout: 60000 });
  const closed = new Promise(resolve => {
    child.once('error', error => resolve({ error }));
    child.once('close', (code, signal) => resolve({ code, signal }));
  });
  let failure;
  let result;
  try {
    await new Promise((resolve, reject) => {
      let text = '';
      const timer = setTimeout(() => reject(new Error('OHIP test lock did not become ready')), 10000);
      const finish = callback => { clearTimeout(timer); callback(); };
      child.stdout.on('data', data => {
        text += data.toString();
        if (text.includes('LOCKED\n')) finish(resolve);
      });
      closed.then(() => finish(() => reject(new Error('OHIP test lock process exited before readiness'))));
    });
    result = await operation();
  } catch (error) {
    failure = error;
  } finally {
    child.stdin.destroy();
    const exit = await closed;
    if (exit.error || exit.code !== 0) {
      const cleanup = new Error('OHIP test lock process failed', { cause: exit.error });
      if (failure) failure = new AggregateError([failure, cleanup], 'OHIP check and lock cleanup failed', { cause: failure });
      else failure = cleanup;
    }
  }
  if (failure) throw failure;
  return result;
}
module.exports = { withOhipTestLock };
