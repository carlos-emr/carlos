/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const { test, after } = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync, spawnSync } = require('node:child_process');

/*
 * The demo data a Playwright check runs against must agree with the data the product ships.
 *
 * CARLOS has three ways to get a database that a check can log in to, and they are built from
 * different files:
 *   - the packaged install: the Flyway migrations (database/mysql/migration/{common,on|bc}), plus,
 *     for `carlos-ctl demo-data`, the ADDITIVE artifact scripts/build-demo-additive.sh derives from
 *     the dev snapshot at package build time (INSERT IGNORE only, security tables excluded);
 *   - the devcontainer: .devcontainer/db/scripts/populate_db.sh loads the same Flyway migrations,
 *     then TRUNCATE-RELOADS the whole database from development.sql (a 2023 snapshot), then
 *     patches parts of it back.
 * The suite's checks name security objects (`_eform`, `_admin.fieldnote`, ...) and assert what a
 * role may do with them. When the two builds disagree about a privilege, a check passes on the
 * packaged install and fails in the devcontainer (or the reverse) with nothing to say why.
 *
 * TWO CONTRACTS
 *  1. secObjPrivilege. For every security object named in a scripts/**\/*-playwright-checks.js
 *     source or in scripts/lib/authz-read-routes.js, the (role -> privilege, priority) rows of
 *     every build are identical. The builds are replayed by a small interpreter of exactly the
 *     statement shapes the sources use; any other shape THROWS, so a new migration cannot slip
 *     past unread. Flyway migrations are applied in Flyway's version order, and every later one
 *     that changes secObjPrivilege is applied (today: on V1.0.6, common V1.0.9 and V1.0.46, and
 *     bc V1.0.6). The devcontainer replay follows populate_db.sh: development.sql, then the
 *     re-applied reference migrations, then development_privileges.sql, which exists because the
 *     snapshot "replaces secObjPrivilege with its older demo snapshot".
 *  2. casemgmt_note_link. No link row of the additive artifact points at a tickler or a document
 *     the artifact does not contain (finding 143).
 *
 * KNOWN DIFFERENCES ARE PINNED EXACTLY, NOT BY OBJECT. A known-different object runs its
 * comparison as a `todo` (so CI stays green while the difference stands), and a SECOND, always-on
 * check fails if that object shows any difference beyond the pinned ones. New drift on an object
 * that is already known to differ is therefore still a failure, and when a difference is fixed the
 * todo simply starts passing.
 *
 * THE ADDITIVE ARTIFACT is built here, offline, with the shipped scripts/build-demo-additive.sh
 * (about half a second per province) into a temporary directory.
 */

const ROOT = path.resolve(__dirname, '..');
const MIGRATION_DIR = path.join(ROOT, 'database', 'mysql', 'migration');
const DEV_SQL = path.join(ROOT, '.devcontainer', 'db', 'scripts', 'development.sql');
const POPULATE_DB = path.join(ROOT, '.devcontainer', 'db', 'scripts', 'populate_db.sh');
const BUILD_ADDITIVE = path.join(ROOT, 'scripts', 'build-demo-additive.sh');
const EXCLUDE_FILE = path.join(ROOT, 'scripts', 'demo-additive-exclude.txt');
const AUTHZ_READ_ROUTES = path.join(ROOT, 'scripts', 'lib', 'authz-read-routes.js');
const BASELINE_SCHEMA = path.join(MIGRATION_DIR, 'common', 'V1__baseline_schema.sql');

const PRIVILEGE_COLUMNS = ['roleUserGroup', 'objectName', 'privilege', 'priority', 'provider_no'];

// ---------------------------------------------------------------------------------------------
// SQL reading
// ---------------------------------------------------------------------------------------------

/** Index of the `;` that ends the statement starting at `from`, ignoring semicolons inside strings. */
function statementEnd(sql, from) {
  let quoted = false;
  for (let i = from; i < sql.length; i += 1) {
    const c = sql[i];
    if (quoted) {
      if (c === '\\') i += 1;
      else if (c === "'") { if (sql[i + 1] === "'") i += 1; else quoted = false; }
    } else if (c === "'") quoted = true;
    else if (c === ';') return i;
  }
  return sql.length;
}

/**
 * Reads the parenthesised value tuples starting at `index` (a list such as `(1,'a'),(2,'b')`).
 * Strings are unescaped (`''` and backslash escapes); numbers stay text; NULL becomes null.
 */
function readTuples(sql, index) {
  const rows = [];
  let i = index;
  for (;;) {
    while (i < sql.length && /[\s,]/.test(sql[i])) i += 1;
    if (sql[i] !== '(') break;
    i += 1;
    const row = [];
    let current = '';
    let quoted = false;
    let isString = false;
    const push = () => {
      const value = isString ? current : current.trim();
      row.push(!isString && value.toUpperCase() === 'NULL' ? null : value);
      current = '';
      isString = false;
    };
    for (; i < sql.length; i += 1) {
      const c = sql[i];
      if (quoted) {
        if (c === '\\') { i += 1; current += sql[i]; } else if (c === "'") {
          if (sql[i + 1] === "'") { current += "'"; i += 1; } else quoted = false;
        } else current += c;
      } else if (c === "'") { quoted = true; isString = true; current = ''; } else if (c === ',') push();
      else if (c === ')') { push(); i += 1; break; } else current += c;
    }
    rows.push(row);
  }
  return { rows, end: i };
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Rows of every plain `INSERT [IGNORE] INTO <table> [(columns)] VALUES ...` in `sql`, as arrays
 * ordered by `defaultColumns` (the table's declared order, used when the statement lists none).
 */
function insertedRows(sql, table, defaultColumns) {
  const start = new RegExp(`(?:^|[;\\n])[ \\t]*INSERT(?:[ \\t]+IGNORE)?[ \\t]+INTO[ \\t]+\`?${escapeRegExp(table)}(?![A-Za-z0-9_$])\`?[ \\t]*`, 'g');
  const out = [];
  let match;
  while ((match = start.exec(sql))) {
    let i = match.index + match[0].length;
    let columns = defaultColumns;
    if (sql[i] === '(') {
      const close = sql.indexOf(')', i);
      columns = sql.slice(i + 1, close).split(',').map((name) => name.trim().replace(/`/g, ''));
      i = close + 1;
    }
    const values = /^\s*VALUES\s*/i.exec(sql.slice(i, i + 40));
    assert.ok(values, `cannot read INSERT INTO ${table}: expected VALUES near "${sql.slice(i, i + 60).replace(/\s+/g, ' ')}"`);
    for (const row of readTuples(sql, i + values[0].length).rows) {
      out.push(Object.fromEntries(defaultColumns.map((name) => [name, columns.includes(name) ? row[columns.indexOf(name)] : null])));
    }
  }
  return out;
}

// ---------------------------------------------------------------------------------------------
// A replay of the statements that change secObjPrivilege
// ---------------------------------------------------------------------------------------------

/** The primary key (roleUserGroup, objectName); the column collation is case-insensitive. */
function privilegeKey(row) {
  return `${row.roleUserGroup}\u0000${row.objectName}`.toLowerCase();
}

function sameText(a, b) {
  return String(a).toLowerCase() === String(b).toLowerCase();
}

/** `col = 'lit' AND col = 'lit'` -> [[col, lit]]; anything else throws (the replay models no other predicate). */
function equalityConditions(text, label) {
  return text.trim().split(/\s+AND\s+/i).map((condition) => {
    const parts = /^`?(\w+)`?\s*=\s*'((?:[^']|'')*)'$/.exec(condition.trim());
    assert.ok(parts, `${label}: the replay of secObjPrivilege does not understand the predicate "${condition.trim()}"`);
    return [parts[1], parts[2].replace(/''/g, "'")];
  });
}

/**
 * Applies to `table` (a Map keyed by privilegeKey) every statement in `sql` that writes
 * secObjPrivilege. Returns the number of statements applied. Shapes understood:
 *   INSERT [IGNORE] INTO secObjPrivilege [(cols)] VALUES (...),... [ON DUPLICATE KEY UPDATE c = VALUES(c), ...]
 *   INSERT [IGNORE] INTO secObjPrivilege (cols) SELECT <literals> WHERE NOT EXISTS (SELECT 1 FROM secObjPrivilege WHERE ...)
 *   DELETE FROM secObjPrivilege [WHERE col = 'x' AND ...]      TRUNCATE [TABLE] secObjPrivilege
 * UPDATE, REPLACE and any other form throw, so a migration that uses one is never silently ignored.
 */
function applyPrivilegeStatements(table, sql, label) {
  if (!/secObjPrivilege/i.test(sql)) return 0;
  const start = /(?:^|[;\n])[ \t]*(INSERT(?:[ \t]+IGNORE)?[ \t]+INTO|REPLACE[ \t]+INTO|DELETE[ \t]+FROM|UPDATE|TRUNCATE(?:[ \t]+TABLE)?)[ \t]+`?secObjPrivilege`?(?![A-Za-z0-9_$])`?/gi;
  let applied = 0;
  let match;
  while ((match = start.exec(sql))) {
    const verb = match[1].replace(/\s+/g, ' ').toUpperCase();
    const from = match.index + match[0].length;
    const body = sql.slice(from, statementEnd(sql, from));
    const where = `${label}: ${verb} secObjPrivilege`;
    applied += 1;
    if (verb.startsWith('TRUNCATE')) { table.clear(); continue; }
    if (verb.startsWith('DELETE')) {
      const predicate = /^\s*WHERE\s+([\s\S]+)$/i.exec(body);
      const conditions = predicate ? equalityConditions(predicate[1], where) : [];
      for (const [key, row] of [...table]) {
        if (conditions.every(([column, value]) => sameText(row[column], value))) table.delete(key);
      }
      continue;
    }
    assert.ok(verb.startsWith('INSERT'), `${where} is not a statement shape the replay understands; extend applyPrivilegeStatements`);
    let rest = body;
    let columns = PRIVILEGE_COLUMNS;
    const listed = /^\s*\(([^)]*)\)/.exec(rest);
    if (listed) {
      columns = listed[1].split(',').map((name) => name.trim().replace(/`/g, ''));
      rest = rest.slice(listed[0].length);
    }
    let rows;
    let guard = null;
    const values = /^\s*VALUES\s*/i.exec(rest);
    if (values) {
      const read = readTuples(rest, values[0].length);
      rows = read.rows;
      rest = rest.slice(read.end);
    } else {
      const select = /^\s*SELECT\s+([\s\S]*?)\s+WHERE\s+NOT\s+EXISTS\s*\(\s*SELECT\s+1\s+FROM\s+`?secObjPrivilege`?\s+WHERE\s+([\s\S]*?)\s*\)\s*$/i.exec(rest);
      assert.ok(select, `${where} is not a statement shape the replay understands (near "${rest.trim().slice(0, 80).replace(/\s+/g, ' ')}")`);
      rows = readTuples(`(${select[1]})`, 0).rows;
      guard = equalityConditions(select[2], where);
      rest = '';
    }
    let duplicateUpdate = null;
    if (rest.trim() !== '') {
      const clause = /^\s*ON\s+DUPLICATE\s+KEY\s+UPDATE\s+([\s\S]+)$/i.exec(rest);
      assert.ok(clause, `${where}: trailing clause "${rest.trim().slice(0, 60)}" is not understood`);
      duplicateUpdate = clause[1].split(',').map((assignment) => {
        const parts = /^\s*`?(\w+)`?\s*=\s*VALUES\(\s*`?(\w+)`?\s*\)\s*$/.exec(assignment);
        assert.ok(parts && parts[1] === parts[2], `${where}: ON DUPLICATE KEY UPDATE "${assignment.trim()}" is not col = VALUES(col)`);
        return parts[1];
      });
    }
    for (const values of rows) {
      const row = Object.fromEntries(PRIVILEGE_COLUMNS.map((name) => [name, columns.includes(name) ? values[columns.indexOf(name)] : null]));
      const key = privilegeKey(row);
      if (guard && [...table.values()].some((existing) => guard.every(([column, value]) => sameText(existing[column], value)))) continue;
      if (table.has(key)) {
        if (/IGNORE/.test(verb)) continue;
        if (duplicateUpdate) { for (const column of duplicateUpdate) table.get(key)[column] = row[column]; continue; }
        assert.fail(`${where} inserts the duplicate key ${row.roleUserGroup}/${row.objectName}; the load would stop on it`);
      }
      table.set(key, row);
    }
  }
  return applied;
}

/** [role -> "privilege/priority"] for one object in one replayed table. */
function objectPrivileges(table, objectName) {
  const out = new Map();
  for (const row of table.values()) {
    if (sameText(row.objectName, objectName)) out.set(row.roleUserGroup, `${row.privilege}/${row.priority}`);
  }
  return out;
}

// ---------------------------------------------------------------------------------------------
// The builds
// ---------------------------------------------------------------------------------------------

/** Flyway's order: numeric, component by component (V1 < V1.0.1 < ... < V1.0.23 < V1.0.23.1 < V1.0.24). */
function versionOf(fileName) {
  return /^V(\d+(?:\.\d+)*)__/.exec(fileName)[1].split('.').map(Number);
}

function compareVersions(a, b) {
  for (let i = 0; i < Math.max(a.length, b.length); i += 1) {
    const difference = (a[i] || 0) - (b[i] || 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

/** The migrations a province installs, in the order Flyway applies them. */
function flywayMigrations(province) {
  const files = [];
  for (const location of ['common', province]) {
    for (const name of fs.readdirSync(path.join(MIGRATION_DIR, location))) {
      if (/^V\d+(?:\.\d+)*__.*\.sql$/.test(name)) files.push({ name, file: path.join(MIGRATION_DIR, location, name) });
    }
  }
  return files.sort((a, b) => compareVersions(versionOf(a.name), versionOf(b.name)));
}

function flywayState(province) {
  const table = new Map();
  const applied = [];
  for (const { name, file } of flywayMigrations(province)) {
    if (applyPrivilegeStatements(table, fs.readFileSync(file, 'utf8'), `${province}/${name}`) > 0) applied.push(name);
  }
  return { table, applied };
}

/**
 * The files populate_db.sh loads into the `carlos` database from development.sql onward, in
 * order: the snapshot, the re-applied reference migrations, and every later seed script.
 */
function devcontainerLoads() {
  const script = fs.readFileSync(POPULATE_DB, 'utf8');
  const found = [];
  for (const m of script.matchAll(/^\$SQL carlos < \/scripts\/(\S+\.sql)\s*$/gm)) {
    found.push({ at: m.index, file: path.join(ROOT, '.devcontainer', 'db', 'scripts', m[1]) });
  }
  for (const m of script.matchAll(/^\$SQL carlos < \/database\/mysql\/updates\/(\S+\.sql)\s*$/gm)) {
    found.push({ at: m.index, file: path.join(ROOT, 'database', 'mysql', 'updates', m[1]) });
  }
  const refs = /for REF_MIGRATION in([\s\S]*?);\s*do\b/.exec(script);
  assert.ok(refs, 'populate_db.sh no longer has the REF_MIGRATION loop; update devcontainerLoads()');
  for (const m of refs[1].matchAll(/"\$\{MIG\}\/([^"]+\.sql)"/g)) {
    found.push({ at: refs.index + m.index, file: path.join(MIGRATION_DIR, m[1]) });
  }
  found.sort((a, b) => a.at - b.at);
  const first = found.findIndex((entry) => entry.file === DEV_SQL);
  assert.ok(first >= 0, 'populate_db.sh no longer loads development.sql; update devcontainerLoads()');
  return found.slice(first).map((entry) => entry.file);
}

function devcontainerState(flywayOn) {
  const table = new Map([...flywayOn].map(([key, row]) => [key, { ...row }]));
  const applied = [];
  for (const file of devcontainerLoads()) {
    if (applyPrivilegeStatements(table, fs.readFileSync(file, 'utf8'), path.basename(file)) > 0) applied.push(path.relative(ROOT, file));
  }
  return { table, applied };
}

// ---- the additive artifact ------------------------------------------------------------------

const BASH_AVAILABLE = spawnSync('bash', ['--version'], { stdio: 'ignore' }).status === 0;
let scratch = null;
const artifacts = {};

function additiveArtifact(province) {
  if (!artifacts[province]) {
    scratch = scratch || fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-demo-contract-'));
    const out = path.join(scratch, `demo-${province}.sql`);
    execFileSync('bash', [BUILD_ADDITIVE, DEV_SQL, province, out], { stdio: 'pipe', timeout: 120000 });
    artifacts[province] = fs.readFileSync(out, 'utf8');
  }
  return artifacts[province];
}

after(() => {
  if (scratch) fs.rmSync(scratch, { recursive: true, force: true });
});

function debDemoState(flyway, province) {
  const table = new Map([...flyway].map(([key, row]) => [key, { ...row }]));
  const statements = applyPrivilegeStatements(table, additiveArtifact(province), `additive artifact (${province})`);
  return { table, statements };
}

// ---- which security objects the suite names -------------------------------------------------

function* walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) { if (entry.name !== 'node_modules') yield* walk(full); } else yield full;
  }
}

function suiteSources() {
  const checks = [...walk(path.join(ROOT, 'scripts'))].filter((file) => file.endsWith('-playwright-checks.js'));
  return [...checks, AUTHZ_READ_ROUTES].sort();
}

/**
 * Security objects named anywhere in `sources`: every `_name` / `_name.part` token that is the
 * objectName of some secObjPrivilege row (so `_blank`, `_month` and the like are not objects).
 * Returns Map objectName -> number of source files naming it.
 */
function namedObjects(sources, knownObjects) {
  const known = new Set([...knownObjects]);
  const counts = new Map();
  for (const file of sources) {
    const seen = new Set();
    for (const token of fs.readFileSync(file, 'utf8').matchAll(/(?<![A-Za-z0-9_$.])_[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*/g)) {
      if (known.has(token[0])) seen.add(token[0]);
    }
    for (const name of seen) counts.set(name, (counts.get(name) || 0) + 1);
  }
  return counts;
}

// ---------------------------------------------------------------------------------------------
// Differences between the builds, and the ones already known
// ---------------------------------------------------------------------------------------------

const LEGS = ['flyway-bc', 'devcontainer', 'deb-demo-on', 'deb-demo-bc'];

/** Differences of one object against the Ontario Flyway baseline, as stable text such as `devcontainer|doctor|w/0->x/0`. */
function differences(states, objectName) {
  const reference = objectPrivileges(states['flyway-on'], objectName);
  const out = [];
  for (const leg of LEGS) {
    const actual = objectPrivileges(states[leg], objectName);
    for (const role of new Set([...reference.keys(), ...actual.keys()])) {
      const expected = reference.get(role) || 'absent';
      const observed = actual.get(role) || 'absent';
      if (expected !== observed) out.push(`${leg}|${role}|${expected}->${observed}`);
    }
  }
  return out.sort();
}

/**
 * Differences that exist today and are waiting on a decision (all logged as finding 193). Keyed by object; `diffs` is the
 * exact set pinned (anything beyond it fails), `reason` is the todo text.
 */
const OPEN_DIFFERENCES = {
  '_newCasemgmt.episode': {
    reason: 'finding 193: maintainers to decide doctor Episodes privilege',
    diffs: ['devcontainer|doctor|o/0->x/0'],
  },
  _episode: {
    reason: 'finding 193: maintainers to decide doctor Episodes privilege (same decision as _newCasemgmt.episode)',
    diffs: ['devcontainer|doctor|o/0->x/0'],
  },
  _eform: {
    reason: 'finding 193: development.sql gives doctor _eform x where Flyway gives w (the eForm delete split); '
      + 'authz-read-admin-objects asserts doctor holds _eform w; maintainers to decide which side moves',
    diffs: ['devcontainer|doctor|w/0->x/0'],
  },
  '_admin.flowsheet': {
    reason: 'finding 193: the devcontainer gives doctor no _admin.flowsheet where Flyway gives r; admin-role-management relies on the '
      + 'doctor holding it (the day-sheet Administration link) and development_privileges.sql restores only the admin row; '
      + 'maintainers to decide how the devcontainer gets it',
    diffs: ['devcontainer|doctor|r/0->absent'],
  },
  '_admin.fieldnote': {
    reason: 'finding 193: the devcontainer has no admin _admin.fieldnote row (Flyway V1.0.46 adds it); populate_db.sh does not re-apply '
      + 'V1.0.46 after development.sql truncates secObjPrivilege and development_privileges.sql omits it; '
      + 'maintainers to decide how the devcontainer gets it',
    diffs: ['devcontainer|admin|x/0->absent'],
  },
};

/** Objects compared although no check names them, because a known difference concerns them. */
const PINNED_EXTRAS = ['_newCasemgmt.episode', '_episode'];

// ---------------------------------------------------------------------------------------------
// Tests: replay machinery
// ---------------------------------------------------------------------------------------------

test('shouldReplayEachStatementShape_whenBuildingPrivilegeState', () => {
  const replay = (sql, seed = []) => {
    const table = new Map(seed.map((row) => [privilegeKey(row), row]));
    applyPrivilegeStatements(table, sql, 'fixture');
    return [...table.values()].map((row) => `${row.roleUserGroup}/${row.objectName}=${row.privilege}`).sort();
  };
  const row = (roleUserGroup, objectName, privilege) => ({ roleUserGroup, objectName, privilege, priority: '0', provider_no: null });
  assert.deepEqual(replay("INSERT INTO secObjPrivilege VALUES ('a','_x','r',0,'1'),('b','_y','it''s;ok',0,NULL);"), ['a/_x=r', "b/_y=it's;ok"]);
  assert.deepEqual(replay("INSERT IGNORE INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) VALUES ('a','_x','o',0,'1');",
    [row('a', '_x', 'x')]), ['a/_x=x']);
  assert.deepEqual(replay("INSERT INTO `secObjPrivilege` (`roleUserGroup`,`objectName`,`privilege`) VALUES ('a','_x','w') ON DUPLICATE KEY UPDATE `privilege` = VALUES(`privilege`);",
    [row('a', '_x', 'x')]), ['a/_x=w']);
  assert.deepEqual(replay("INSERT INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no) SELECT 'a', '_x', 'x', 0, NULL WHERE NOT EXISTS (SELECT 1 FROM secObjPrivilege WHERE roleUserGroup = 'a' AND objectName = '_x');",
    [row('a', '_x', 'r')]), ['a/_x=r']);
  assert.deepEqual(replay("DELETE FROM `secObjPrivilege` WHERE `roleUserGroup` = 'a' AND `objectName` = '_x';", [row('a', '_x', 'r'), row('a', '_y', 'r')]), ['a/_y=r']);
  assert.deepEqual(replay('TRUNCATE TABLE `secObjPrivilege`;', [row('a', '_x', 'r')]), []);
  // The case-insensitive primary key: a differently cased role is the same row.
  assert.deepEqual(replay("INSERT IGNORE INTO secObjPrivilege VALUES ('Doctor','_x','o',0,NULL);", [row('doctor', '_x', 'r')]), ['doctor/_x=r']);
  // Text that merely mentions the table is not a statement.
  assert.deepEqual(replay("-- INSERT INTO secObjPrivilege VALUES ('a','_x','r',0,NULL);\nINSERT INTO recyclebin VALUES (1,'secObjPrivilege');"), []);
});

test('shouldRefuseToReplay_whenAStatementShapeIsNotUnderstood', () => {
  const replay = (sql) => applyPrivilegeStatements(new Map(), sql, 'fixture');
  assert.throws(() => replay("UPDATE secObjPrivilege SET privilege='r' WHERE objectName='_x';"), /not a statement shape the replay understands/);
  assert.throws(() => replay("REPLACE INTO secObjPrivilege VALUES ('a','_x','r',0,NULL);"), /not a statement shape the replay understands/);
  assert.throws(() => replay("INSERT INTO secObjPrivilege SELECT roleUserGroup, objectName, 'r', 0, NULL FROM secObjPrivilege;"), /not a statement shape the replay understands/);
  assert.throws(() => replay("DELETE FROM secObjPrivilege WHERE objectName IN ('_x','_y');"), /does not understand the predicate/);
  assert.throws(() => replay("INSERT INTO secObjPrivilege VALUES ('a','_x','r',0,NULL),('a','_x','w',0,NULL);"), /duplicate key/);
});

test('shouldApplyEveryPrivilegeMigration_inFlywayVersionOrder', () => {
  assert.ok(compareVersions(versionOf('V1.0.23.1__x.sql'), versionOf('V1.0.23__x.sql')) > 0);
  assert.ok(compareVersions(versionOf('V1.0.23.1__x.sql'), versionOf('V1.0.24__x.sql')) < 0);
  assert.ok(compareVersions(versionOf('V1.0.9__x.sql'), versionOf('V1.0.10__x.sql')) < 0);
  assert.ok(compareVersions(versionOf('V1__x.sql'), versionOf('V1.0.1__x.sql')) < 0);
  const on = flywayState('on');
  const bc = flywayState('bc');
  // The migrations that change secObjPrivilege today; a new one appearing here is applied automatically.
  for (const name of ['V1.0.2__on_data.sql', 'V1.0.6__restore_reporting_privilege.sql', 'V1.0.9__remove_carlosdoc_schedule_group_denial.sql', 'V1.0.46__field_note_report_privilege.sql']) {
    assert.ok(on.applied.includes(name), `${name} should be replayed for the Ontario install; replayed: ${on.applied.join(', ')}`);
  }
  for (const name of ['V1.0.2__bc_data.sql', 'V1.0.6__restore_live_legacy_bc_tables_and_reference_data.sql', 'V1.0.9__remove_carlosdoc_schedule_group_denial.sql']) {
    assert.ok(bc.applied.includes(name), `${name} should be replayed for the BC install; replayed: ${bc.applied.join(', ')}`);
  }
  assert.ok(on.table.size > 400 && bc.table.size > 400, `Flyway seeds should hold 400+ privilege rows (on ${on.table.size}, bc ${bc.table.size})`);
  assert.equal(objectPrivileges(on.table, '_admin.fieldnote').get('admin'), 'x/0', 'V1.0.46 grants admin _admin.fieldnote');
  assert.equal(objectPrivileges(on.table, '_admin.schedule.groupCreate').get('999998'), undefined, 'V1.0.9 removes the carlosdoc denial');
});

test('shouldReplayDevcontainerLoad_inPopulateDbOrder', () => {
  const loads = devcontainerLoads().map((file) => path.relative(ROOT, file));
  assert.equal(loads[0], path.join('.devcontainer', 'db', 'scripts', 'development.sql'));
  const restore = loads.indexOf(path.join('.devcontainer', 'db', 'scripts', 'development_privileges.sql'));
  assert.ok(restore > 0, `populate_db.sh must load development_privileges.sql after the snapshot; loads: ${loads.join(', ')}`);
  const flyway = flywayState('on');
  const dev = devcontainerState(flyway.table);
  assert.ok(dev.applied[0].endsWith('development.sql') && dev.applied.some((file) => file.endsWith('development_privileges.sql')), `replayed: ${dev.applied.join(', ')}`);
  assert.equal(objectPrivileges(dev.table, '_admin.schedule').get('admin'), 'x/0', 'development_privileges.sql restores the admin schedule privilege');
  assert.equal(objectPrivileges(dev.table, '_admin.traceability').size, 0, 'development_privileges.sql removes _admin.traceability');
});

test('shouldNameSecurityObjects_fromPlaywrightSourcesAndRouteTable', () => {
  const flyway = flywayState('on');
  const known = new Set([...flyway.table.values()].map((row) => row.objectName));
  const sources = suiteSources();
  assert.ok(sources.length > 100, `expected 100+ sources, found ${sources.length}`);
  assert.ok(sources.includes(AUTHZ_READ_ROUTES));
  const named = namedObjects(sources, known);
  for (const name of ['_eChart', '_demographic', '_eform', '_admin.fieldnote', '_admin.flowsheet', '_edoc', '_rx']) {
    assert.ok(named.has(name), `${name} is named in the suite but was not found`);
  }
  for (const notAnObject of ['_blank', '_self', '_all', '_status']) {
    assert.ok(!named.has(notAnObject), `${notAnObject} is not a security object`);
  }
  assert.ok(named.size >= 25, `expected 25+ named security objects, found ${named.size}`);
});

// ---------------------------------------------------------------------------------------------
// Tests: contract 1, secObjPrivilege
// ---------------------------------------------------------------------------------------------

test('shouldExcludeSecurityTables_fromTheAdditiveArtifact', { skip: !BASH_AVAILABLE && 'bash is not available' }, () => {
  const excluded = new Set(fs.readFileSync(EXCLUDE_FILE, 'utf8').split('\n').map((line) => line.trim()).filter((line) => line && !line.startsWith('#')));
  for (const table of ['secObjPrivilege', 'secObjectName', 'secPrivilege', 'secRole', 'secUserRole', 'security']) {
    assert.ok(excluded.has(table), `${table} must stay on demo-additive-exclude.txt: the demo load must not add privileges to a Flyway install`);
  }
  for (const province of ['on', 'bc']) {
    const sql = additiveArtifact(province);
    assert.ok(sql.length > 1000000, `the ${province} additive artifact should be substantial, got ${sql.length} bytes`);
    const state = debDemoState(new Map(), province);
    assert.equal(state.statements, 0, `the ${province} additive artifact carries ${state.statements} secObjPrivilege statement(s); `
      + 'they would add privileges the Flyway seed does not grant');
    assert.ok(!/INSERT\s+(IGNORE\s+)?INTO\s+`?(secObjectName|secRole|secUserRole|secPrivilege)`?[\s(]/i.test(sql), 'the artifact must not touch the other security tables');
  }
});

test('shouldMatchSecObjPrivilege_acrossSeedsAndDemoData', { skip: !BASH_AVAILABLE && 'bash is not available (the additive artifact cannot be built)' }, async (t) => {
  const flywayOn = flywayState('on');
  const flywayBc = flywayState('bc');
  const states = {
    'flyway-on': flywayOn.table,
    'flyway-bc': flywayBc.table,
    devcontainer: devcontainerState(flywayOn.table).table,
    'deb-demo-on': debDemoState(flywayOn.table, 'on').table,
    'deb-demo-bc': debDemoState(flywayBc.table, 'bc').table,
  };
  const known = new Set(Object.values(states).flatMap((table) => [...table.values()].map((row) => row.objectName)));
  const named = namedObjects(suiteSources(), known);
  const objects = [...new Set([...named.keys(), ...PINNED_EXTRAS])].sort();
  assert.ok(objects.length >= 25, `expected 25+ objects to compare, found ${objects.length}`);

  for (const objectName of objects) {
    const open = OPEN_DIFFERENCES[objectName];
    // The comparison itself: identical in every build. A known difference reports as todo.
    await t.test(`${objectName} is identical in every build`, open ? { todo: open.reason } : {}, () => {
      const found = differences(states, objectName);
      assert.ok(found.length === 0,
        `${objectName} (named in ${named.get(objectName) || 0} suite source(s)) differs from the Ontario Flyway seed: ${found.join('; ')}`);
    });
    // The pin: nothing beyond the known difference may appear, and it is not a todo.
    if (open) {
      await t.test(`${objectName} shows no difference beyond the pinned one`, () => {
        const unexpected = differences(states, objectName).filter((entry) => !open.diffs.includes(entry));
        assert.ok(unexpected.length === 0, `new drift on ${objectName}: ${unexpected.join('; ')}`);
      });
    }
  }
});

// ---------------------------------------------------------------------------------------------
// Tests: contract 2, casemgmt_note_link (finding 143)
// ---------------------------------------------------------------------------------------------

/** First column of `CREATE TABLE <table>` in the baseline schema (the key the link rows refer to). */
function keyColumnOf(table) {
  const schema = fs.readFileSync(BASELINE_SCHEMA, 'utf8');
  const create = new RegExp(`CREATE TABLE \`${escapeRegExp(table)}\` \\(\\s*\`(\\w+)\``).exec(schema);
  assert.ok(create, `the baseline schema has no CREATE TABLE ${table}`);
  return create[1];
}

const LINK_TYPE = { DOCUMENT: '5', TICKLER: '10' }; // CaseManagementNoteLink.DOCUMENT / .TICKLER

/** Link rows of `type` whose target row is absent from `sql`, grouped by target id. */
function orphanedLinks(sql, type, table) {
  const keyColumn = keyColumnOf(table);
  const present = new Set(insertedRows(sql, table, [keyColumn]).map((row) => row[keyColumn]));
  const links = insertedRows(sql, 'casemgmt_note_link', ['id', 'table_name', 'table_id', 'note_id', 'other_id']);
  const orphans = new Map();
  for (const link of links.filter((entry) => entry.table_name === type && !present.has(entry.table_id))) {
    orphans.set(link.table_id, [...(orphans.get(link.table_id) || []), link.note_id]);
  }
  return { orphans, links: links.filter((entry) => entry.table_name === type).length, present: present.size };
}

function describeOrphans({ orphans, links, present }, noun) {
  const list = [...orphans].map(([id, notes]) => `${noun} ${id} -> note ${notes.join(', ')}`).join('; ');
  return `${orphans.size} ${noun} id(s) have link rows but no ${noun} row (${links} ${noun} link rows, ${present} ${noun} rows in the dataset): ${list}. `
    + `On a fresh install the first new ${noun} takes one of those ids and opens an unrelated demo note.`;
}

test('shouldSeedNoTicklersOrDocuments_inFlywayMigrations', () => {
  // Contract 2 looks for the link targets in the additive artifact alone; that is only sound while Flyway seeds none.
  for (const province of ['on', 'bc']) {
    for (const { name, file } of flywayMigrations(province)) {
      const sql = fs.readFileSync(file, 'utf8');
      for (const table of ['tickler', 'document']) {
        assert.ok(!new RegExp(`INSERT\\s+(IGNORE\\s+)?INTO\\s+\`?${table}(?![A-Za-z0-9_$])`, 'i').test(sql),
          `${province}/${name} seeds ${table} rows; the orphan check must then count them as present`);
      }
    }
  }
  assert.equal(keyColumnOf('tickler'), 'tickler_no');
  assert.equal(keyColumnOf('document'), 'document_no');
});

test('shouldFindOrphanedLinks_whenTargetRowsAreMissing', () => {
  // The negative control for the finding test below: the detector must report an orphan when there is one.
  const sql = "INSERT IGNORE INTO `tickler` VALUES (1,7,'x');\n"
    + "INSERT IGNORE INTO `casemgmt_note_link` VALUES (1,10,1,68,NULL),(2,10,4,69,NULL),(3,10,4,70,NULL),(4,5,9,71,NULL);\n"
    + "INSERT IGNORE INTO `document` VALUES (9,'lab');\n";
  const tickler = orphanedLinks(sql, LINK_TYPE.TICKLER, 'tickler');
  assert.deepEqual([...tickler.orphans], [['4', ['69', '70']]]);
  assert.equal(orphanedLinks(sql, LINK_TYPE.DOCUMENT, 'document').orphans.size, 0);
  assert.match(describeOrphans(tickler, 'tickler'), /tickler 4 -> note 69, 70/);
});

for (const [province, label] of [['on', 'Ontario'], ['bc', 'Bc']]) {
  // NOT a todo, and the reason the two todos below can be trusted. A todo that passes looks exactly
  // like a fix, so if a change to build-demo-additive.sh, demo-additive-exclude.txt or development.sql
  // left the artifact with no link rows, or no tickler / document rows, "no orphans" would hold
  // vacuously and the pin would quietly read as resolved. This test fails loudly in that case instead.
  test(`shouldParseLinkRowsAndTargets_from${label}AdditiveArtifact`, { skip: !BASH_AVAILABLE && 'bash is not available' }, () => {
    const sql = additiveArtifact(province);
    for (const [type, table] of [[LINK_TYPE.TICKLER, 'tickler'], [LINK_TYPE.DOCUMENT, 'document']]) {
      const { links, present } = orphanedLinks(sql, type, table);
      assert.ok(links > 0, `the ${label} additive artifact yielded no casemgmt_note_link rows of type ${type} (${table}); `
        + 'the orphan check below would pass vacuously');
      assert.ok(present > 0, `the ${label} additive artifact yielded no ${table} rows; the orphan check below would pass vacuously`);
    }
  });

  test(`shouldLinkNoNoteToMissingTickler_in${label}AdditiveArtifact`, { todo: 'finding 143', skip: !BASH_AVAILABLE && 'bash is not available' }, () => {
    const result = orphanedLinks(additiveArtifact(province), LINK_TYPE.TICKLER, 'tickler');
    assert.ok(result.orphans.size === 0, describeOrphans(result, 'tickler'));
  });

  test(`shouldLinkNoNoteToMissingDocument_in${label}AdditiveArtifact`, { todo: 'finding 143', skip: !BASH_AVAILABLE && 'bash is not available' }, () => {
    const result = orphanedLinks(additiveArtifact(province), LINK_TYPE.DOCUMENT, 'document');
    assert.ok(result.orphans.size === 0, describeOrphans(result, 'document'));
  });
}
