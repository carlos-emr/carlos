/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {spawn} = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./playwright-harness');

// A dedicated connection holds this advisory lock across separate fixture queries and browser requests.
async function acquireMysqlWorkflowLock(config, name) {
  h.assert(/^[a-z0-9-]+$/.test(name), 'Invalid workflow lock name');
  const host = h.validateMysqlHost(config.host);
  h.assert(config.password != null && !/[\r\n]/.test(config.password), 'Invalid workflow database credentials');
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-workflow-lock-'));
  const optionFile = path.join(directory, 'client.cnf');
  const password = String(config.password).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  fs.writeFileSync(optionFile, `[client]\npassword="${password}"\n`, {mode: 0o600});
  const child = spawn('mysql', [`--defaults-extra-file=${optionFile}`, '-h', host,
    '-u', config.user || 'root', config.database || 'carlos', '-N', '-B', '--unbuffered'],
  {stdio: ['pipe', 'pipe', 'pipe']});
  const lock = `CONCAT('carlos-pw-', MD5(CONCAT(DATABASE(), ':', '${name}')))`;
  let ended = false;
  let failed = false;
  const closed = new Promise(resolve => {
    child.once('error', () => { failed = true; ended = true; resolve(); });
    child.once('close', code => { failed ||= code !== 0; ended = true; resolve(); });
  });
  child.stderr.resume(); // Never expose database/client output containing credentials or statements.
  child.stdin.on('error', () => { failed = true; });
  async function stop() {
    if (!ended) child.stdin.end(`DO RELEASE_LOCK(${lock});\n`);
    const timer = setTimeout(() => { failed = true; child.kill('SIGKILL'); }, 5000);
    try { await closed; } finally {
      clearTimeout(timer);
      fs.rmSync(directory, {recursive: true, force: true});
    }
  }
  try {
    await new Promise((resolve, reject) => {
      let output = '';
      const timer = setTimeout(() => reject(new Error('Workflow lock connection timed out')), 5000);
      function finish(error) {
        clearTimeout(timer);
        child.stdout.off('data', receive);
        if (error) reject(error); else resolve();
      }
      function receive(chunk) {
        output += chunk.toString();
        if (output.includes('\n')) finish(output.trim() === '1' ? null
          : new Error('Another report workflow owns this database; run it after that workflow finishes'));
        else if (output.length > 128) finish(new Error('Unexpected workflow lock response'));
      }
      child.stdout.on('data', receive);
      closed.then(() => finish(new Error('Workflow lock connection closed')));
      child.stdin.write(`SELECT GET_LOCK(${lock}, 0);\n`);
    });
  } catch (error) {
    await stop();
    throw error;
  }
  // The client read its option file at startup and is connected, so the cleartext password file is
  // no longer needed. Remove it now: a caller that exits from a signal handler never reaches stop().
  fs.rmSync(directory, {recursive: true, force: true});
  let released = false;
  return async () => {
    if (released) return;
    released = true;
    const lost = ended;
    await stop();
    h.assert(!lost && !failed, 'The workflow database lock was lost before cleanup completed');
  };
}
module.exports = {acquireMysqlWorkflowLock};
