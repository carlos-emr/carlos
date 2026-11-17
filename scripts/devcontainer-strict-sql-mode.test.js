/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/*
 * The development database must run MariaDB's default STRICT sql_mode (issue #3151).
 *
 * An empty sql_mode in the devcontainer my.cnf is not a harmless preference. It silently coerces every
 * type mismatch (a name into an INT column becomes 0, an over-long string is truncated, an invalid
 * datetime becomes zero), so a column-misaligned demo snapshot loads "successfully" and the damage is
 * only visible as meaningless values. Under the default strict mode the same snapshot aborts
 * populate_db.sh at the first bad row, which is how the next regression gets caught: both
 * container-images.yml (builds and starts the dev DB image) and db-schema-verify.yml (mounts this
 * my.cnf) therefore run strict.
 *
 * demo-seed-layouts.test.js pins the snapshot's column layouts; this file pins the server setting that
 * makes a layout mistake loud. scripts/check-demo-data-strict-load.sh proves the pair end to end against
 * a real MariaDB.
 *
 * The Debian package's drop-in (debian/assets/mariadb/60-carlos-emr.cnf) deliberately keeps
 * sql_mode = "" for existing OSCAR-lineage deployments. That is a per-site decision and is NOT
 * asserted here.
 */

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

// Every file that configures or launches the development MariaDB server. development.sql and the
// drugref dump are deliberately absent: they are data, not configuration.
const DEVCONTAINER_DB_FILES = [
  '.devcontainer/development/config/shared/my.cnf',
  '.devcontainer/db/Dockerfile',
  '.devcontainer/db/scripts/populate_db.sh',
  '.devcontainer/docker-compose.yml',
  '.devcontainer/devcontainer.json',
];

/**
 * Lines that set sql_mode, with comments removed. MariaDB option files treat `-` and `_` in option names
 * as interchangeable, so `sql-mode` counts. A `SET [GLOBAL|SESSION] sql_mode` statement and a
 * `--sql-mode` server flag are caught as well, since any of them would blank the mode just as well.
 */
function sqlModeSettings(text) {
  return text
    .split('\n')
    // `#` starts a my.cnf/YAML/shell comment. `--` starts a SQL comment only when whitespace (or the end of the line)
    // follows it: a bare `--sql-mode=` at the start of a folded Compose `command:` line is a server flag, and must
    // survive this strip or the scan would call a config that blanks sql_mode clean.
    .map(line => line.replace(/^\s*(#|--(?=\s|$)).*$/, '').replace(/\s+#\s.*$/, ''))
    .filter(line => /(^|[\s"'`-])(--)?sql[-_]mode\b/i.test(line) && !/^\s*$/.test(line));
}

test('the helper finds every spelling that sets sql_mode', () => {
  assert.equal(sqlModeSettings('sql_mode\t\t= ""').length, 1);
  assert.equal(sqlModeSettings('sql-mode=""').length, 1);
  assert.equal(sqlModeSettings('sql_mode = STRICT_ALL_TABLES').length, 1);
  assert.equal(sqlModeSettings('  command: ["--sql-mode="]').length, 1);
  assert.equal(sqlModeSettings('mariadb -e "SET GLOBAL sql_mode=\'\';"').length, 1);
});

test('the helper keeps a bare --sql-mode flag that starts a folded Compose command line', () => {
  const compose = [
    'services:',
    '  db:',
    '    command: >',
    '      --character-set-server=utf8mb4',
    '      --sql-mode=',
  ].join('\n');
  assert.equal(sqlModeSettings(compose).length, 1);
  // ...while a real SQL comment that merely mentions the setting is still stripped.
  assert.equal(sqlModeSettings('-- SET sql_mode = ""').length, 0);
  assert.equal(sqlModeSettings('--').length, 0);
});

test('the helper ignores comments that merely mention sql_mode', () => {
  assert.equal(sqlModeSettings('# sql_mode is deliberately NOT set: ...').length, 0);
  assert.equal(sqlModeSettings('  # sql_mode = ""').length, 0);
  assert.equal(sqlModeSettings('-- SET sql_mode = ""').length, 0);
  assert.equal(sqlModeSettings('innodb_strict_mode = 1').length, 0);
});

test('the devcontainer my.cnf does not override sql_mode', () => {
  const cnf = fs.readFileSync(path.join(root, DEVCONTAINER_DB_FILES[0]), 'utf8');
  assert.deepEqual(sqlModeSettings(cnf), [],
    'Remove sql_mode from the devcontainer my.cnf. An empty sql_mode silently coerces bad demo data '
    + '(issue #3151); the server default is strict.');
});

test('nothing that launches the development database blanks sql_mode', () => {
  for (const relative of DEVCONTAINER_DB_FILES) {
    const file = path.join(root, relative);
    if (!fs.existsSync(file)) continue;
    assert.deepEqual(sqlModeSettings(fs.readFileSync(file, 'utf8')), [],
      `${relative} must not set sql_mode; the development database runs MariaDB's default strict mode`);
  }
});
