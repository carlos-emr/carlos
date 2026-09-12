/** Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

// Explicitly opt-in, local-only database teardown; never put credentials in argv or errors.
function localFixtureSql(query, env = process.env) {
  const host = env.MYSQL_HOST || 'localhost';
  if (!['localhost', '127.0.0.1', '::1'].includes(host)) {
    throw new Error('Fixture artifact cleanup requires a loopback MYSQL_HOST');
  }
  if (env.MYSQL_PASSWORD === undefined || /[\r\n]/.test(env.MYSQL_PASSWORD)) {
    throw new Error('Fixture artifact cleanup requires a valid MYSQL_PASSWORD');
  }
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-fixture-sql-'));
  const options = path.join(directory, 'mysql.cnf');
  try {
    const password = env.MYSQL_PASSWORD.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
    fs.writeFileSync(options, `[client]\npassword="${password}"\n`, { mode: 0o600 });
    return execFileSync('mysql', [`--defaults-extra-file=${options}`, '-h', host,
      '-u', env.MYSQL_USER || 'root', env.MYSQL_DATABASE || 'carlos', '-NBse', query],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
  } catch (_) {
    throw new Error('Fixture artifact database cleanup failed');
  } finally {
    fs.rmSync(options, { force: true });
    fs.rmdirSync(directory);
  }
}

function removeOwnedDocumentFile(directory, filename, marker) {
  if (!/^carlos-nav-probe-[0-9a-f-]{36}$/.test(marker)
      || !/^[A-Za-z0-9._-]+$/.test(filename) || !filename.includes(marker)) {
    throw new Error('Refusing a document artifact not owned by this fixture');
  }
  const root = fs.realpathSync(directory);
  if ([path.parse(root).root, os.homedir(), process.cwd()].includes(root)) {
    throw new Error('Document store must be an explicit dedicated directory');
  }
  const target = path.join(root, filename);
  let stat;
  try { stat = fs.lstatSync(target); }
  catch (error) { if (error.code === 'ENOENT') return; throw error; }
  if (!stat.isFile() || stat.isSymbolicLink()) {
    throw new Error('Refusing a non-regular document fixture artifact');
  }
  fs.unlinkSync(target);
}

function deleteOwnedPrescriptionSignature(signatureId, demographicNo, query = localFixtureSql) {
  if (!/^[1-9]\d*$/.test(String(signatureId)) || !/^[1-9]\d*$/.test(String(demographicNo))) {
    throw new Error('Fixture signature identifiers must be positive integers');
  }
  // The caller supplies ONLY the id returned by its own upload, after clearing its association.
  query(`DELETE FROM DigitalSignature WHERE id=${signatureId} AND demographicId=${demographicNo}`
    + " AND moduleType='PRESCRIPTION' AND NOT EXISTS"
    + ` (SELECT 1 FROM prescription WHERE digital_signature_id=${signatureId})`);
  if (query(`SELECT COUNT(*) FROM DigitalSignature WHERE id=${signatureId}`) !== '0') {
    throw new Error('Created signature is still referenced or its ownership does not match');
  }
}

module.exports = { localFixtureSql, removeOwnedDocumentFile, deleteOwnedPrescriptionSignature };
