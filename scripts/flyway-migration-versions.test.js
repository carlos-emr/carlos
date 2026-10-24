/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const root = path.join(__dirname, '..', 'database/mysql/migration');

for (const province of ['bc', 'on']) {
  test(`Flyway common + ${province} migrations have unique normalized versions`, () => {
    const versions = new Map();
    for (const directory of ['common', province]) {
      for (const name of fs.readdirSync(path.join(root, directory))) {
        const match = /^V([0-9._]+)__.*\.sql$/.exec(name);
        if (!match) continue;
        const parts = match[1].split(/[._]/).map(Number);
        while (parts.length > 1 && parts.at(-1) === 0) parts.pop();
        const version = parts.join('.');
        assert.equal(versions.has(version), false,
          `duplicate Flyway version ${version}: ${versions.get(version)} and ${directory}/${name}`);
        versions.set(version, `${directory}/${name}`);
      }
    }
    assert.ok(versions.size > 10, 'must inspect the full combined migration set');
  });
}

// The demo reload reapplies data migrations after schema creation. A renamed file
// must fail this contract before a fresh database is left partially initialized.
test('devcontainer demo reapply paths name existing migrations', () => {
  const script = fs.readFileSync(path.join(__dirname, '../.devcontainer/db/scripts/populate_db.sh'), 'utf8');
  const paths = [...script.matchAll(/"\$\{MIG\}\/([^"\n]+\.sql)"/g)].map(match => match[1]);
  assert.ok(paths.length >= 4, 'must inspect the demo reapply migrations');
  for (const migration of paths) assert.ok(fs.existsSync(path.join(root, migration)), migration);
});
