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
