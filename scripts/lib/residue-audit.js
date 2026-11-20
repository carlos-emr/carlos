/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * Post-run residue audit: did a browser-check run leave anything behind?
 *
 * WHY. A check that mutates the shared install and does not put it back poisons every later
 * check, and the symptom turns up somewhere else. Finding 180: fax-configure saved a fake SRFax
 * account with the gateway enabled and polling on, never restored it, and FaxImporter logged an
 * ERROR every minute for three hours (189 of them) in a log nobody had a reason to read.
 * `run-playwright-suite.js --residue-audit` makes the runner look: it takes a baseline before
 * the first check and audits after the last.
 *
 * THREE KINDS OF RESIDUE.
 *   1. Marker rows. A fixture row carries the run's marker (`FAKE-PW<16 hex>`, see runWorkflow
 *      in workflow-session.js) in a name-like column. Any such row that survives the run is
 *      residue, so these are counted absolutely, not against a baseline: a row an earlier,
 *      crashed run left behind is residue too, and is better said than hidden. MARKER_TABLES
 *      names the table and the columns that carry the marker.
 *   2. Clinic-wide state. fax_config, the encounterForm registrations and the `property` rows a
 *      check's manifest `mutates` names are shared by every check; there is no marker to find.
 *      Residue is a DIFFERENCE from the baseline taken at the start of the run.
 *
 *   3. Rows added or removed anywhere. A table with no marker column (form_boolean_value,
 *      formRourke2020 and the other form tables, providerLabRoutingLock, patientLabRouting,
 *      measurementsExt) is invisible to the marker audit, and so was a lock row 34 earlier runs
 *      leaked. The baseline therefore also holds an exact row count of EVERY base table of the
 *      database, and the audit reports each table that has more rows than it had ("rows added"),
 *      minus ROW_GROWTH_ALLOWED: the few tables that legitimately grow on every run (the audit
 *      trail itself), each with its reason; and each table that has FEWER ("rows removed"), with no
 *      allow-list, because nothing in the install shrinks a table behind a run's back (it has no
 *      purge job) and a check that deletes rows it does not own does more harm than one that leaves
 *      its own: acknowledging a demo lab deleted the demo's provider-0 routing row for it (the
 *      application removes those on acknowledge) and lab-acknowledge never put it back. Counting
 *      434 tables takes 0.4 to 0.7 s on the packaged install.
 *      A count diff is NET: a delete and an insert in the same table cancel out, so a check that
 *      removes one demo row and leaks one of its own in that table reads clean.
 *      The audit also needs the install to itself: another session driving the application while a
 *      run is audited adds or removes rows that the audit reads as that run's residue.
 *
 * WHAT IS REPORTED. Table and count, never a row. fax_config and property hold credentials and
 * may hold PHI, so the state tables are compared by SHA-256 digest computed inside MariaDB: the
 * row contents never reach this process, let alone a log. The row-count diff reads counts only.
 *
 * `sql` is the harness client (createSqlRunner): value(), rows(), execute(). The audit only
 * reads, so a test can stub it.
 */

const { sqlString } = require('./playwright-harness');

/**
 * The prefixes a fixture marker starts with. `FAKE-PW` is the run marker every runWorkflow check
 * gets; `FAKE-XP` is the xss-poison payload prefix. The demo dataset's names all start `FAKE-`
 * (and general_ci would match FAKE-Pwalski against FAKE-PW%), so a bare `FAKE-` would flag the
 * whole dataset and the match below is case-sensitive.
 */
const MARKER_PREFIXES = Object.freeze(['FAKE-PW', 'FAKE-XP']);

/**
 * A throwaway login's user name is the marker without its hyphen: Login2Action only accepts
 * [a-zA-Z0-9]{1,30} (see throwaway-login-fixture.js).
 */
const USER_NAME_PREFIXES = Object.freeze(['FAKEPW']);

const FAX_CONFIG_TABLE = 'fax_config';

/**
 * The tables checked for marker rows, in report order, and the columns that carry the marker in
 * the fixtures the suite writes (found by reading the INSERTs in scripts/*-playwright-checks.js
 * and the schema in database/mysql/migration/common/V1__baseline_schema.sql).
 *
 *   columns[].contains   free text (a note body, a tickler message) holds the marker somewhere,
 *                        not only at the start
 *   columns[].prefixes   overrides MARKER_PREFIXES for that column
 *   linked[]             a row whose `column` points at a parent row that carries the marker
 *                        (billingmaster has no name column of its own; its bill's does)
 */
const MARKER_TABLES = Object.freeze([
  { table: 'demographic', columns: [{ name: 'last_name' }, { name: 'first_name' }] },
  { table: 'provider', columns: [{ name: 'last_name' }, { name: 'first_name' }] },
  {
    table: 'security',
    columns: [{ name: 'user_name', prefixes: USER_NAME_PREFIXES }],
    linked: [{ column: 'provider_no', parent: 'provider', parentKey: 'provider_no', parentColumn: 'last_name' }],
  },
  { table: 'tickler', columns: [{ name: 'message', contains: true }] },
  { table: 'casemgmt_note', columns: [{ name: 'note', contains: true }] },
  { table: 'billing_on_cheader1', columns: [{ name: 'demographic_name' }, { name: 'comment1' }] },
  {
    table: 'billingmaster',
    columns: [{ name: 'claim_comment' }],
    linked: [
      { column: 'billing_no', parent: 'billing', parentKey: 'billing_no', parentColumn: 'demographic_name' },
      { column: 'demographic_no', parent: 'demographic', parentKey: 'demographic_no', parentColumn: 'last_name' },
    ],
  },
  { table: 'eform_data', columns: [{ name: 'subject' }] },
  { table: 'document', columns: [{ name: 'docdesc' }, { name: 'docfilename' }] },
  // A role a check makes for itself (authzReadFixture.addRole) is named like a throwaway login and
  // described by the run marker; its privilege rows are keyed on that name.
  { table: 'secRole', columns: [{ name: 'role_name', prefixes: USER_NAME_PREFIXES }, { name: 'description' }] },
  { table: 'secObjPrivilege', columns: [{ name: 'roleUserGroup', prefixes: USER_NAME_PREFIXES }] },
]);

/**
 * The tables compared against the baseline. `key` identifies a row, so a changed row is one
 * difference, not two. UserProperty is the Java entity of the `property` table, so it has no
 * entry of its own: a `UserProperty:<name>` mutates entry is a property row.
 */
const STATE_TABLES = Object.freeze([
  Object.freeze({ table: FAX_CONFIG_TABLE, key: 'id' }),
  Object.freeze({ table: 'encounterForm', key: 'form_value' }),
]);
const PROPERTY_TABLE = Object.freeze({ table: 'property', key: 'id' });

/**
 * Tables that gain rows on every run by design, and so are left out of the row-count diff's "rows added". Each entry says why;
 * add one only when the growth is the application's own record of the run and no check can be expected to remove it. Growth
 * anywhere else is a check that did not clean up (or an application write the check did not know about), and is reported.
 * There is deliberately no such list for "rows removed": no table shrinks on its own on this install.
 */
const ROW_GROWTH_ALLOWED = Object.freeze([
  Object.freeze({ table: 'log', reason: 'the audit trail: every page a check opens appends a row, and the application never removes one' }),
  Object.freeze({ table: 'hash_audit', reason: 'the signed-note integrity log: signing an encounter note appends a hash, and deleting the note leaves it' }),
]);

/** Marks a residue entry that comes from the row-count diff (rows added to a table) rather than a marker or a state difference. */
const ROWS_ADDED = 'rows added';
/** Marks a residue entry of the row-count diff for a table that has fewer rows than at the baseline. */
const ROWS_REMOVED = 'rows removed';

/** Tables per counting statement, so the statement stays far below the client's argument and 30 s limits whatever the schema grows to. */
const ROW_COUNT_BATCH = 100;

const IDENTIFIER = /^[A-Za-z_][A-Za-z0-9_]*$/;
const PROPERTY_ENTRY = /^(?:property|UserProperty):([A-Za-z0-9_.-]+)$/;
const FILE_ENTRY = /^file:[A-Za-z0-9_.-]+$/;
const DIGEST = /^[0-9a-f]{64}$/;

function quote(identifier) {
  if (!IDENTIFIER.test(identifier)) throw new Error(`not a plain SQL identifier: ${identifier}`);
  return `\`${identifier}\``;
}

/** A LIKE pattern matching values that START with `prefix`; its wildcards and escape are literal. */
function likePrefix(prefix) {
  return `${String(prefix).replace(/[\\%_]/g, '\\$&')}%`;
}

function likeContains(prefix) {
  return `%${likePrefix(prefix)}`;
}

/**
 * Split a manifest `mutates` list into what the audit can diff and what it cannot.
 *
 * Grammar: a table name; `property:<name>` or `UserProperty:<name>` (rows of the property table
 * with that name); or `file:<label>` for something on disk. fax_config and encounterForm are
 * audited on every run whether listed or not, so listing them only documents the check; so does
 * listing a ROW_GROWTH_ALLOWED table such as `log`. Any other table is `notDiffed`: the row-count
 * diff still sees rows added or removed, but not a row that was changed in place.
 *
 * @returns {{ propertyNames: string[], audited: string[], notDiffed: string[] }} `notDiffed` are
 *   entries the audit cannot compare (other tables, files): the runner says so rather than let a
 *   reader assume they were covered.
 */
function parseMutates(mutates) {
  if (mutates === undefined) return { propertyNames: [], audited: [], notDiffed: [] };
  if (!Array.isArray(mutates)) throw new Error('mutates must be an array of clinic-wide objects');
  const propertyNames = [];
  const audited = [];
  const notDiffed = [];
  for (const entry of new Set(mutates)) {
    const property = typeof entry === 'string' ? PROPERTY_ENTRY.exec(entry) : null;
    if (property) {
      if (!propertyNames.includes(property[1])) propertyNames.push(property[1]);
    } else if (typeof entry === 'string' && (STATE_TABLES.some((state) => state.table === entry)
        || ROW_GROWTH_ALLOWED.some((allowed) => allowed.table === entry))) {
      // A state table is diffed row by row; an allow-listed table (the audit trail) is accounted for by the row-count diff,
      // which says how much it grew. Listing either only documents the check.
      audited.push(entry);
    } else if (typeof entry === 'string' && (IDENTIFIER.test(entry) || FILE_ENTRY.test(entry))) {
      notDiffed.push(entry);
    } else {
      throw new Error(`${JSON.stringify(entry)} is not a valid mutates entry `
        + '(a table name, property:<name>, UserProperty:<name> or file:<label>)');
    }
  }
  return { propertyNames, audited, notDiffed };
}

/** WHERE predicate: the column starts with (or, for free text, contains) one of the prefixes. */
function matchAny(column, prefixes, contains) {
  return prefixes.map((prefix) => `BINARY ${quote(column)} LIKE ${sqlString(contains ? likeContains(prefix) : likePrefix(prefix))}`)
    .join(' OR ');
}

function markerPredicate(spec) {
  const parts = spec.columns.map((column) => matchAny(column.name, column.prefixes || MARKER_PREFIXES, column.contains));
  for (const link of spec.linked || []) {
    parts.push(`${quote(link.column)} IN (SELECT ${quote(link.parentKey)} FROM ${quote(link.parent)} `
      + `WHERE ${matchAny(link.parentColumn, MARKER_PREFIXES, false)})`);
  }
  return parts.map((part) => `(${part})`).join(' OR ');
}

/** The columns one MARKER_TABLES entry reads, by table: its own and its linked parents'. */
function requiredFor(spec) {
  const schema = {};
  const need = (table, column) => {
    schema[table] = schema[table] || [];
    if (!schema[table].includes(column)) schema[table].push(column);
  };
  for (const column of spec.columns) need(spec.table, column.name);
  for (const link of spec.linked || []) {
    need(spec.table, link.column);
    need(link.parent, link.parentKey);
    need(link.parent, link.parentColumn);
  }
  return schema;
}

/** Every column the marker queries read, by table: what must exist for the audit to mean anything. */
function requiredSchema() {
  const schema = {};
  for (const spec of MARKER_TABLES) {
    for (const [table, columns] of Object.entries(requiredFor(spec))) {
      schema[table] = [...new Set([...(schema[table] || []), ...columns])];
    }
  }
  return schema;
}

/** table -> its columns, for the tables that exist in this database. */
function columnsOf(sql, tables) {
  const found = {};
  const rows = sql.rows('SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS '
    + `WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (${[...new Set(tables)].map(sqlString).join(',')}) `
    + 'ORDER BY TABLE_NAME, ORDINAL_POSITION');
  for (const [table, column] of rows) {
    found[table] = found[table] || [];
    found[table].push(column);
  }
  return found;
}

/**
 * [key, digest] for each row of a state table. The digest is computed by MariaDB over every
 * column (QUOTE keeps NULL distinct from the text 'NULL'), so the row itself is never read here.
 */
function digestRows(sql, spec, columns, where) {
  const digest = `SHA2(CONCAT_WS(CHAR(31),${columns.map((column) => `QUOTE(${quote(column)})`).join(',')}),256)`;
  const rows = sql.rows(`SELECT CAST(${quote(spec.key)} AS CHAR), ${digest} FROM ${quote(spec.table)}`
    + `${where ? ` WHERE ${where}` : ''} ORDER BY 1`);
  return rows.map(([key, hash]) => {
    if (!DIGEST.test(String(hash))) throw new Error(`the digest of a ${spec.table} row was malformed`);
    return [String(key), hash];
  });
}

/** A table name read from information_schema, quoted for any character it could hold. */
function quoteAny(name) {
  return `\`${String(name).replace(/`/g, '``')}\``;
}

/**
 * The exact number of rows of every base table of the current database, by table name. COUNT(*) per table, a few statements of
 * up to ROW_COUNT_BATCH tables each. Nothing but a count leaves the database.
 */
function countRows(sql) {
  const tables = sql.rows("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
    + "AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME").map(([name]) => String(name));
  const counts = {};
  for (let from = 0; from < tables.length; from += ROW_COUNT_BATCH) {
    const batch = tables.slice(from, from + ROW_COUNT_BATCH);
    const rows = sql.rows(batch.map((name) => `SELECT ${sqlString(name)}, COUNT(*) FROM ${quoteAny(name)}`).join(' UNION ALL '));
    for (const [name, count] of rows) {
      const value = Number(count);
      if (!Number.isInteger(value) || value < 0) throw new Error(`the row count of ${name} was not a number`);
      counts[name] = value;
    }
  }
  const missing = tables.filter((name) => !(name in counts));
  if (missing.length) throw new Error(`the row count of ${missing.length} table(s) was not returned`);
  return counts;
}

/**
 * The tables whose row count differs between `before` and `after`: those with more rows ("rows added", minus the allow-listed
 * ones, which are returned apart) and those with fewer ("rows removed", never exempt), as residue entries
 * `{ table, count, kind }` in table order. A table that is not in `before` (created during the run) counts from zero; one that
 * is gone from `after` has lost every row it had.
 *
 * It is a NET count: a delete and an insert in the same table cancel out.
 *
 * @param {Object<string, number>} before  row counts at the baseline
 * @param {Object<string, number>} after  row counts now
 * @param {{table: string}[]} [allowed]  tables whose growth is expected (default ROW_GROWTH_ALLOWED)
 * @returns {{ changed: {table: string, count: number, kind: string}[], allowedGrowth: {table: string, count: number}[] }}
 */
function rowChanges(before, after, allowed = ROW_GROWTH_ALLOWED) {
  const expected = new Set(allowed.map((entry) => entry.table));
  const changed = [];
  const allowedGrowth = [];
  for (const table of [...new Set([...Object.keys(before), ...Object.keys(after)])].sort()) {
    const difference = (after[table] || 0) - (before[table] || 0);
    if (difference > 0) {
      if (expected.has(table)) allowedGrowth.push({ table, count: difference });
      else changed.push({ table, count: difference, kind: ROWS_ADDED });
    } else if (difference < 0) {
      changed.push({ table, count: -difference, kind: ROWS_REMOVED });
    }
  }
  return { changed, allowedGrowth };
}

function stateSpecs(propertyNames) {
  return propertyNames.length ? [...STATE_TABLES, PROPERTY_TABLE] : [...STATE_TABLES];
}

function propertyWhere(names) {
  return `${quote('name')} IN (${names.map(sqlString).join(',')})`;
}

/**
 * Take the baseline the audit compares against. Call it before the first check.
 *
 * @param {{ sql: object, mutates?: string[] }} options `mutates` is the union of the selected
 *   checks' manifest `mutates`; its property entries decide which property rows are watched.
 * @returns {object} an opaque, JSON-serialisable baseline to pass to auditResidue() as `since`.
 */
function captureBaseline({ sql, mutates = [] }) {
  const { propertyNames, notDiffed } = parseMutates(mutates);
  const specs = stateSpecs(propertyNames);
  const columns = columnsOf(sql, specs.map((spec) => spec.table));
  const tables = {};
  for (const spec of specs) {
    tables[spec.table] = columns[spec.table]
      ? digestRows(sql, spec, columns[spec.table], spec === PROPERTY_TABLE ? propertyWhere(propertyNames) : undefined)
      : null;
  }
  return { takenAt: new Date().toISOString(), propertyNames, notDiffed, tables, rowCounts: countRows(sql) };
}

/** How many rows differ: created, deleted, or changed (one, not two). */
function countDifferences(before, after) {
  const base = new Map(before);
  const now = new Map(after);
  let differences = 0;
  for (const [key, hash] of now) {
    if (base.get(key) !== hash) differences += 1;
  }
  for (const key of base.keys()) {
    if (!now.has(key)) differences += 1;
  }
  return differences;
}

/**
 * Audit, with the detail the runner prints.
 *
 * @param {{ sql: object, since?: object }} options `since` is captureBaseline()'s result. Without
 *   it the state tables are not compared (there is nothing to compare against) and only the
 *   marker rows are counted; the runner always supplies it.
 * @returns {{ residue: {table: string, count: number, kind?: string}[], absent: string[], notDiffed: string[],
 *   allowedGrowth: {table: string, count: number}[] }}
 *   `absent` are audited tables this install does not have (billingmaster is British Columbia
 *   only); `notDiffed` are `mutates` entries no comparison covers; `allowedGrowth` are the
 *   ROW_GROWTH_ALLOWED tables that grew, said so a reader can see what the allow-list absorbed.
 *   Entries of `residue` with a `kind` come from the row-count diff (the count is rows added or removed).
 * @throws when a table is present but lacks a column the audit reads, because a silently shrunk
 *   audit is worse than none.
 */
function auditResidueDetailed({ sql, since }) {
  const required = requiredSchema();
  const specs = since ? stateSpecs(since.propertyNames || []) : [];
  const columns = columnsOf(sql, [...Object.keys(required), ...specs.map((spec) => spec.table)]);
  const residue = [];
  const absent = [];

  for (const spec of MARKER_TABLES) {
    if (!columns[spec.table]) {
      absent.push(spec.table);
      continue;
    }
    for (const [table, needed] of Object.entries(requiredFor(spec))) {
      const missing = needed.filter((column) => !(columns[table] || []).includes(column));
      if (missing.length) {
        throw new Error(`residue audit: ${table}${table === spec.table ? '' : ` (read by the ${spec.table} audit)`} `
          + `has no ${missing.join(', ')} column; update scripts/lib/residue-audit.js`);
      }
    }
    const count = Number(sql.value(`SELECT COUNT(*) FROM ${quote(spec.table)} WHERE ${markerPredicate(spec)}`));
    if (!Number.isInteger(count) || count < 0) throw new Error(`the ${spec.table} marker count was not a number`);
    if (count > 0) residue.push({ table: spec.table, count });
  }

  for (const spec of specs) {
    const before = since.tables ? since.tables[spec.table] : null;
    if (!columns[spec.table]) {
      absent.push(spec.table);
      continue;
    }
    const where = spec === PROPERTY_TABLE ? propertyWhere(since.propertyNames) : undefined;
    // A table that appeared during the run has no baseline: every row of it is new.
    const count = countDifferences(before || [], digestRows(sql, spec, columns[spec.table], where));
    if (count > 0) residue.push({ table: spec.table, count });
  }

  // Rows added to or removed from ANY table, marker column or not. A baseline taken before this audit existed carries no counts:
  // nothing to compare.
  let allowedGrowth = [];
  if (since && since.rowCounts) {
    const differences = rowChanges(since.rowCounts, countRows(sql));
    residue.push(...differences.changed);
    allowedGrowth = differences.allowedGrowth;
  }

  return { residue, absent, notDiffed: since && since.notDiffed ? [...since.notDiffed] : [], allowedGrowth };
}

/**
 * Residue as `[{ table, count }]`: marker rows in the suite's fixture tables, plus the
 * differences in fax_config, encounterForm and the watched property rows against `since`.
 * Empty means nothing was left behind.
 */
function auditResidue(options) {
  return auditResidueDetailed(options).residue;
}

/** One residue entry as the runner words it: `<table> <count>`, with `(rows added)` or `(rows removed)` for the row-count diff. */
function describeResidue({ table, count, kind }) {
  return `${table} ${count}${kind ? ` (${kind})` : ''}`;
}

/** The lines the runner prints: one `residue: <table> <count>` per entry, or the clean verdict. */
function formatResidue(residue) {
  if (!residue.length) return ['residue audit: no residue'];
  return residue.map((entry) => `residue: ${describeResidue(entry)}`);
}

module.exports = {
  FAX_CONFIG_TABLE, MARKER_PREFIXES, MARKER_TABLES, ROWS_ADDED, ROWS_REMOVED, ROW_GROWTH_ALLOWED, STATE_TABLES,
  auditResidue, auditResidueDetailed, captureBaseline, countRows, describeResidue, formatResidue, likePrefix, parseMutates,
  requiredSchema, rowChanges,
};
