/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createFixture, removeOwnedFiles, cleanupResources, checkedDiskDirectory } = require('./billing-on-group-disk-zero-total-playwright-checks');

function directory(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ohip-cleanup-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  return root;
}

test('removes exact owned files and backups while preserving unrelated and similarly named output', (t) => {
  const root = directory(t);
  const owned = ['H8123.html', 'H8123.html.12345', '.ohip-preview-H8123.html-2345.bak'];
  const other = ['H8123.html.unrelated', 'H8123.html2', '.ohip-preview-H8123.html-other.bak', 'H9999.html'];
  for (const name of [...owned, ...other]) fs.writeFileSync(path.join(root, name), name);
  removeOwnedFiles(root, ['H8123.html']);
  assert.deepEqual(fs.readdirSync(root).sort(), other.sort());
});

test('rejects path traversal and symlinks before deleting any owned file', (t) => {
  const root = directory(t);
  fs.writeFileSync(path.join(root, 'one.html'), 'preserved');
  fs.symlinkSync('one.html', path.join(root, 'two.html'));
  assert.throws(() => removeOwnedFiles(root, ['one.html', '../escape']), /invalid/);
  assert.throws(() => removeOwnedFiles(root, ['one.html', 'two.html']), /non-regular/);
  assert.equal(fs.readFileSync(path.join(root, 'one.html'), 'utf8'), 'preserved');
});

test('requires an explicit dedicated local output directory', (t) => {
  assert.throws(() => checkedDiskDirectory(''), /OHIP_DISK_DIR/);
  assert.throws(() => checkedDiskDirectory('/'), /dedicated/);
  const root = directory(t);
  assert.equal(checkedDiskDirectory(root), root);
});

function faultDatabase(failAt) {
  const providers = new Set();
  let claims = false;
  let disposed = false;
  const deleted = [];
  const db = {
    rows(query) {
      if (query.includes('SELECT provider_no, ohip_no')) return [['999998', '123456', '1']];
      if (query.includes('FROM demographic')) return failAt === 'prerequisite' ? [] : [['1', '1234567890', '', '1980', '01', '01', 'F']];
      if (query.includes('information_schema')) return ['provider_no', 'ohip_no', 'first_name', 'last_name', 'comments', 'status'].map((c) => [c]);
      if (query.startsWith('SELECT id FROM billing_on_cheader1')) return claims ? [['42']] : [];
      if (query.startsWith('SELECT d.id')) {
        assert.match(query, /d\.groupno=/);
        assert.match(query, /AND NOT EXISTS/);
        assert.doesNotMatch(query, /id\s*>/);
        return [];
      }
      throw new Error(`Unexpected rows query: ${query}`);
    },
    value(query) {
      if (query.startsWith('SELECT value FROM billingservice')) return '20.00';
      if (query.startsWith('SELECT MAX(id)')) throw new Error('lost insert acknowledgement');
      if (query.includes('AND first_name=')) return providers.has(query.match(/provider_no='(\d+)'/)[1]) ? '1' : '0';
      return '0';
    },
    execute(query) {
      if (query.startsWith('INSERT INTO provider ')) providers.add(query.match(/ SELECT '(\d+)'/)[1]);
      if (query.startsWith('INSERT IGNORE INTO providersite') && failAt === 'site') throw new Error('site write failed');
      if (query.startsWith('INSERT INTO billing_on_cheader1')) claims = true;
      if (query.includes('DELETE FROM provider WHERE')) deleted.push(query.match(/DELETE FROM provider WHERE provider_no='(\d+)'/)[1]);
      if (query.includes('DELETE FROM billing_on_cheader1')) { assert.match(query, /id=42/); claims = false; }
    },
    dispose() { disposed = true; },
  };
  return { db, providers, deleted, disposed: () => disposed, claims: () => claims };
}

for (const failAt of ['prerequisite', 'site', 'claim-result']) {
  test(`cleanup survives ${failAt} failure and recovers only owned records`, async (t) => {
    const root = directory(t);
    const fake = faultDatabase(failAt);
    const state = { providers: {}, headerIds: [], groupNo: '', marker: 'PW3942-test' };
    assert.throws(() => createFixture(fake.db, {
      templateProvider: '999998', window: { serviceDate: '2003-02-03' }, paidCode: 'A007A', testUser: 'test',
    }, state));
    if (failAt === 'prerequisite') assert.equal(fake.providers.size, 0);
    else assert.ok(fake.providers.size > 0);
    await cleanupResources(null, fake.db, state, root);
    assert.deepEqual(fake.deleted.sort(), [...fake.providers].sort());
    assert.equal(fake.claims(), false);
    assert.equal(fake.disposed(), true);
  });
}

test('browser and database cleanup failures are reported and credentials are still disposed', async (t) => {
  const root = directory(t);
  let disposed = false;
  const db = { value() { throw new Error('database unavailable'); }, dispose() { disposed = true; } };
  const state = { providers: { ZERO: { providerNo: '970001' } }, marker: 'PW3942-test' };
  await assert.rejects(cleanupResources({ close: async () => { throw new Error('browser close failed'); } }, db, state, root),
    (error) => error instanceof AggregateError && error.errors.length === 2);
  assert.equal(disposed, true);
});

test('legacy empty-group cleanup retains exclusive provider ownership checks', async (t) => {
  const root = directory(t);
  fs.writeFileSync(path.join(root, 'HJ.001'), 'owned fallback');
  fs.writeFileSync(path.join(root, 'H9999J.001'), 'unrelated');
  const deleted = [];
  const db = {
    value: query => query.includes('AND first_name=') ? '1' : '0',
    rows(query) {
      if (query.startsWith('SELECT d.id')) {
        assert.match(query, /d\.groupno IN \('8123',''\)/);
        assert.match(query, /AND EXISTS .*f\.providerno IN \('970001'\)/);
        assert.match(query, /AND NOT EXISTS .*f\.providerno IS NULL OR f\.providerno NOT IN \('970001'\)/);
        return [['12', 'HJ.001']];
      }
      return [];
    },
    execute: query => deleted.push(query),
    dispose() {},
  };
  await cleanupResources(null, db, {
    marker: 'PW4277-test', groupNo: '8123', cleanupGroupNumbers: ['8123', ''],
    providers: { ZERO: { providerNo: '970001' } },
  }, root);
  assert.deepEqual(fs.readdirSync(root), ['H9999J.001']);
  assert.ok(deleted.some(query => query.includes('DELETE FROM billing_on_diskname WHERE id=12')));
});
