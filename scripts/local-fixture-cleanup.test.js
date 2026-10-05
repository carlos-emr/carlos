/** Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const { localFixtureSql, removeOwnedDocumentFile, deleteOwnedPrescriptionSignature } = require('./local-fixture-cleanup');

for (const databaseFails of [false, true]) {
  for (const removalFails of [false, true]) {
    test(`local SQL credentials are recursively removed with bounded errors (database=${databaseFails}, removal=${removalFails})`, () => {
      const vm = require('node:vm');
      const ownedDirectory = '/tmp/carlos-fixture-sql-owned-test';
      const removals = [];
      const loadedModule = { exports: {} };
      const source = fs.readFileSync(path.join(__dirname, 'local-fixture-cleanup.js'), 'utf8');
      vm.runInNewContext(source, {
        module: loadedModule,
        require(name) {
          if (name === 'node:fs') return {
            mkdtempSync: () => ownedDirectory,
            writeFileSync(filename, contents, options) { assert.equal(options.mode, 0o600); },
            rmSync(directory, options) {
              removals.push({ directory, options });
              if (removalFails) throw new Error('PRIVATE_CLEANUP_PATH_AND_SECRET');
            },
          };
          if (name === 'node:child_process') return { execFileSync() {
            if (databaseFails) throw new Error('PRIVATE_SQL_AND_SECRET');
            return '1\n';
          } };
          return require(name);
        },
      });
      const invoke = () => loadedModule.exports.localFixtureSql('SELECT 1', { MYSQL_PASSWORD: 'fixture-secret' });
      if (databaseFails || removalFails) {
        assert.throws(invoke, error => {
          assert.doesNotMatch(error.message, /PRIVATE|fixture-secret|\/tmp\//);
          if (databaseFails) assert.match(error.message, /database cleanup failed/);
          if (removalFails) assert.match(error.message, /credential/);
          return true;
        });
      } else {
        assert.equal(invoke(), '1');
      }
      assert.equal(removals.length, 1);
      assert.equal(removals[0].directory, ownedDirectory);
      assert.equal(removals[0].options.recursive, true);
      assert.equal(removals[0].options.force, true);
      assert.equal(removals[0].options.maxRetries, 2);
    });
  }
}

test('removes only the uniquely owned regular document fixture', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'fixture-cleanup-test-'));
  const marker = `carlos-nav-probe-${randomUUID()}`;
  const filename = `20260912000000${marker}.pdf`;
  try {
    fs.writeFileSync(path.join(root, filename), 'test PDF');
    fs.writeFileSync(path.join(root, 'unrelated.pdf'), 'keep');
    removeOwnedDocumentFile(root, filename, marker);
    assert.equal(fs.existsSync(path.join(root, filename)), false);
    assert.equal(fs.readFileSync(path.join(root, 'unrelated.pdf'), 'utf8'), 'keep');
    // Idempotent after removal.
    removeOwnedDocumentFile(root, filename, marker);
  } finally { fs.rmSync(root, { recursive: true, force: true }); }
});

for (const variant of ['traversal', 'unrelated', 'symlink', 'directory']) {
  test(`rejects ${variant} instead of removing another artifact`, () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'fixture-cleanup-test-'));
    const marker = `carlos-nav-probe-${randomUUID()}`;
    let filename = `${marker}.pdf`;
    try {
      fs.writeFileSync(path.join(root, 'keep.pdf'), 'keep');
      if (variant === 'traversal') filename = `../${marker}.pdf`;
      if (variant === 'unrelated') filename = 'keep.pdf';
      if (variant === 'symlink') fs.symlinkSync(path.join(root, 'keep.pdf'), path.join(root, filename));
      if (variant === 'directory') fs.mkdirSync(path.join(root, filename));
      assert.throws(() => removeOwnedDocumentFile(root, filename, marker));
      assert.equal(fs.readFileSync(path.join(root, 'keep.pdf'), 'utf8'), 'keep');
    } finally { fs.rmSync(root, { recursive: true, force: true }); }
  });
}

test('signature cleanup is limited to the created id, patient, module and unreferenced state', () => {
  const queries = [];
  deleteOwnedPrescriptionSignature('42', '123', (sql) => { queries.push(sql); return '0'; });
  assert.match(queries[0], /id=42 AND demographicId=123/);
  assert.match(queries[0], /moduleType='PRESCRIPTION'/);
  assert.match(queries[0], /NOT EXISTS \(SELECT 1 FROM prescription WHERE digital_signature_id=42\)/);
  assert.equal(queries[1], 'SELECT COUNT(*) FROM DigitalSignature WHERE id=42');
});

test('signature cleanup reports an ownership mismatch or remaining reference', () => {
  assert.throws(() => deleteOwnedPrescriptionSignature('42', '123', () => '1'), /still referenced/);
});

test('signature cleanup rejects invalid identifiers before querying', () => {
  for (const id of ['0', '-1', '42 OR 1=1', '']) {
    assert.throws(() => deleteOwnedPrescriptionSignature(id, '123', () => assert.fail('must not query')));
  }
});

test('local database cleanup refuses remote hosts and invalid credential configuration', () => {
  assert.throws(() => localFixtureSql('SELECT 1', { MYSQL_HOST: 'example.com' }), /loopback/);
  assert.throws(() => localFixtureSql('SELECT 1', { MYSQL_HOST: 'localhost' }), /MYSQL_PASSWORD/);
  assert.throws(() => localFixtureSql('SELECT 1', { MYSQL_PASSWORD: 'secret\nvalue' }), /MYSQL_PASSWORD/);
});
