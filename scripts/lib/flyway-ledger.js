/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * Shared helpers for the two Flyway order checks (scripts/flyway-upgrade-check.test.js and
 * scripts/flyway-migration-order.test.js). Both are proposals; see the ledger file headers.
 *
 * CARLOS runs Flyway without outOfOrder and with ignoreMigrationPatterns empty (the boot gate in
 * FlywaySchemaValidator and the .deb FlywayRunner). A database therefore refuses to start when a
 * migration it has not run carries a version below the highest one it has run, and when a
 * migration it has run is renamed, removed or edited. These helpers find both situations from
 * plain files, without a database and without git history.
 *
 * A migration belongs to the Ontario set (common + on) or the BC set (common + bc), or to both;
 * ordering is judged per set, the way an install sees it.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const MIGRATION_ROOT = path.join(__dirname, '..', '..', 'database', 'mysql', 'migration');
const LOCATIONS = ['common', 'on', 'bc'];
const SETS = { ON: ['common', 'on'], BC: ['common', 'bc'] };
const NAME = /^V(\d+(?:[._]\d+)*)__.+\.sql$/;

/** Version parts of "common/V1.0.60__x.sql", trailing zeros dropped as Flyway compares them. */
function versionOf(relPath) {
  const match = NAME.exec(path.basename(relPath));
  if (!match) return null;
  const parts = match[1].split(/[._]/).map(Number);
  while (parts.length > 1 && parts.at(-1) === 0) parts.pop();
  return parts;
}

function compareVersions(a, b) {
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const d = (a[i] || 0) - (b[i] || 0);
    if (d !== 0) return d;
  }
  return 0;
}

const versionText = (parts) => parts.join('.');
const locationOf = (relPath) => relPath.split('/')[0];
const setsOf = (relPath) => Object.keys(SETS).filter((s) => SETS[s].includes(locationOf(relPath)));

/** Content hash that ignores line endings (a CRLF checkout must not look like an edit). */
function contentHash(buffer) {
  return crypto.createHash('sha256').update(buffer.toString('utf8').replace(/\r\n/g, '\n')).digest('hex');
}

/** Migrations in a working tree: { "common/V1.0.60__x.sql": hash }. */
function readMigrations(root = MIGRATION_ROOT) {
  const files = {};
  for (const location of LOCATIONS) {
    const dir = path.join(root, location);
    if (!fs.existsSync(dir)) continue;
    for (const name of fs.readdirSync(dir)) {
      if (NAME.test(name)) files[`${location}/${name}`] = contentHash(fs.readFileSync(path.join(dir, name)));
    }
  }
  return files;
}

/** Non-comment, non-blank lines split on whitespace. */
function readLedger(file) {
  return fs.readFileSync(file, 'utf8').split('\n')
    .map((line) => line.replace(/#.*/, '').trim())
    .filter(Boolean)
    .map((line) => line.split(/\s+/));
}

/**
 * Released migrations must stay as shipped, and anything not yet released must sort above every
 * released version of its set, or an install of that release could never upgrade.
 * released: [{ tag, path, hash }]; files: readMigrations() shape. Returns error strings.
 */
function upgradeErrors(released, files) {
  const errors = [];
  const top = {};
  for (const entry of released) {
    if (!versionOf(entry.path)) { errors.push(`${entry.path} is not a versioned migration name`); continue; }
    if (!(entry.path in files)) {
      errors.push(`${entry.path} shipped in ${entry.tag} but is missing or renamed; installs of ${entry.tag} would refuse to start`);
    } else if (files[entry.path] !== entry.hash) {
      errors.push(`${entry.path} shipped in ${entry.tag} but its content changed; Flyway would report a checksum mismatch`);
    }
    const v = versionOf(entry.path);
    for (const set of setsOf(entry.path)) {
      if (!top[set] || compareVersions(v, top[set].version) > 0) top[set] = { version: v, entry };
    }
  }
  const releasedPaths = new Set(released.map((e) => e.path));
  for (const file of Object.keys(files).sort()) {
    if (releasedPaths.has(file)) continue;
    const highest = setsOf(file).map((set) => top[set]).filter(Boolean)
      .sort((a, b) => compareVersions(b.version, a.version))[0];
    if (highest && compareVersions(versionOf(file), highest.version) <= 0) {
      errors.push(`${file} is not in any release, but ${highest.entry.tag} already shipped ${versionText(highest.version)} `
        + `(${highest.entry.path}); renumber it above ${versionText(highest.version)} so installs of ${highest.entry.tag} can upgrade`);
    }
  }
  return errors;
}

/**
 * On a release line with a newer line after it, new migrations must stay below the newer line's
 * first version, or installs of this line could never upgrade to it. floor: version text such as
 * "1.0.60"; a floor at the line's own released top freezes the line. Returns error strings.
 */
function floorErrors(floor, released, files) {
  const releasedPaths = new Set(released.map((e) => e.path));
  const limit = versionOf(`V${floor}__floor.sql`);
  return Object.keys(files).sort()
    .filter((file) => !releasedPaths.has(file) && compareVersions(versionOf(file), limit) >= 0)
    .map((file) => `${file} is at or above this line's floor ${floor}; a newer line already uses those numbers, `
      + `so use a version below ${floor} (for example a .1 sub-version) or none at all if the line is frozen`);
}

/** Release line of this checkout ("2026.08"), from the project version in pom.xml. */
function currentLine(pom = path.join(MIGRATION_ROOT, '..', '..', '..', 'pom.xml')) {
  // The first <version> in pom.xml is the project's own (CalVer, kept per branch across forward merges).
  if (!fs.existsSync(pom)) return null;
  const match = /<version>(\d{4}\.\d{2})\./.exec(fs.readFileSync(pom, 'utf8'));
  return match ? match[1] : null;
}

/**
 * Migrations must be listed in the order they merged, each above everything listed before it in
 * its set, unless marked "forward-merge" (a released migration arriving from a release branch).
 * order: [{ path, forwardMerge }]; files: readMigrations() shape. Returns error strings.
 */
function orderErrors(order, files) {
  const errors = [];
  const seen = new Set();
  const top = {};
  for (const { path: file, forwardMerge } of order) {
    if (seen.has(file)) errors.push(`${file} is listed twice`);
    seen.add(file);
    if (!(file in files)) errors.push(`${file} is listed but no longer exists; a merged migration must not be renamed or removed`);
    const v = versionOf(file);
    if (!v) { errors.push(`${file} is not a versioned migration name`); continue; }
    const highest = setsOf(file).map((set) => top[set]).filter(Boolean)
      .sort((a, b) => compareVersions(b.version, a.version))[0];
    if (highest && compareVersions(v, highest.version) <= 0 && !forwardMerge) {
      errors.push(`${file} was added after ${highest.path}; existing databases at ${versionText(highest.version)} `
        + `would refuse it. Use a version above the highest one listed, or mark a release forward-merge`);
    }
    for (const set of setsOf(file)) {
      if (!top[set] || compareVersions(v, top[set].version) > 0) top[set] = { version: v, path: file };
    }
  }
  for (const file of Object.keys(files).sort()) {
    if (!seen.has(file)) errors.push(`${file} is not listed; append it to the end of scripts/flyway-merge-order.txt`);
  }
  return errors;
}

module.exports = {
  MIGRATION_ROOT, contentHash, currentLine, floorErrors, orderErrors, readLedger, readMigrations, upgradeErrors, versionOf,
};
