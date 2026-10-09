/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/*
 * The post-run residue audit (scripts/lib/residue-audit.js) and the runner flag that drives it.
 *
 * The audit asks a database "did the run leave anything behind?" and reports table and count
 * only. These tests give it a stub `sql` (the same value/rows/execute shape the harness client
 * has), so they pin which tables and columns it reads and how it compares a baseline, without a
 * MariaDB.
 */

const assert = require('node:assert/strict');
const test = require('node:test');

const {
  FAX_CONFIG_TABLE, MARKER_PREFIXES, MARKER_TABLES, ROWS_ADDED, ROW_GROWTH_ALLOWED, STATE_TABLES, auditResidue, auditResidueDetailed,
  captureBaseline, countRows, describeResidue, formatResidue, likePrefix, parseMutates, requiredSchema, rowGrowth,
} = require('./lib/residue-audit');
const { parseArguments, main } = require('./run-playwright-suite');
const { EXIT_FAIL, EXIT_PASS } = require('./lib/playwright-harness');

const BRIEF_TABLES = ['demographic', 'provider', 'security', 'tickler', 'casemgmt_note',
  'billing_on_cheader1', 'billingmaster', 'eform_data', 'document', 'secRole', 'secObjPrivilege'];

/** The schema the stub reports: every column the marker queries read, plus the state tables. */
function installedSchema(without = []) {
  const schema = requiredSchema();
  for (const name of without) delete schema[name];
  schema.fax_config = ['id', 'url', 'faxUser', 'faxPasswd', 'active', 'download'];
  schema.encounterForm = ['form_name', 'form_value', 'form_table', 'hidden'];
  schema.property = ['name', 'value', 'id', 'provider_no'];
  return schema;
}

/**
 * A stub of the harness mysql client. `counts` answers the marker COUNT(*) queries; `digests`
 * answers the row-digest queries of the state tables; `rowCounts` (table -> rows) answers the
 * whole-database row-count diff: the list of base tables and their COUNT(*). The last two are read
 * on every call, so a test can change them between the baseline and the audit.
 */
function fakeSql({ schema = installedSchema(), counts = {}, digests = {}, rowCounts = {} } = {}) {
  const queries = [];
  const table = (query) => /FROM `(\w+)`/.exec(query)[1];
  return {
    queries,
    value(query) {
      queries.push(query);
      assert.match(query, /^SELECT COUNT\(\*\) FROM `/, 'value() is only for the marker counts');
      return String(counts[table(query)] ?? 0);
    },
    rows(query) {
      queries.push(query);
      if (/information_schema\.COLUMNS/.test(query)) {
        const asked = [...query.matchAll(/'(\w+)'/g)].map((match) => match[1]);
        return Object.entries(schema).filter(([name]) => asked.includes(name))
          .flatMap(([name, columns]) => columns.map((column) => [name, column]));
      }
      if (/information_schema\.TABLES/.test(query)) return Object.keys(rowCounts).sort().map((name) => [name]);
      if (/^SELECT '/.test(query)) {
        // SELECT 'name', COUNT(*) FROM `name` UNION ALL ...: one [name, count] per part.
        return query.split(' UNION ALL ').map((part) => {
          const match = /^SELECT '((?:[^']|'')*)', COUNT\(\*\) FROM `((?:[^`]|``)*)`$/.exec(part);
          assert.ok(match, `not a row-count statement: ${part}`);
          assert.equal(match[1].replace(/''/g, "'"), match[2].replace(/``/g, '`'), 'the name and the table it counts differ');
          return [match[1].replace(/''/g, "'"), String(rowCounts[match[2].replace(/``/g, '`')])];
        });
      }
      return (digests[table(query)] || []).map((row) => [...row]);
    },
    execute() { throw new Error('the residue audit must never write'); },
  };
}

test('shouldReportResidue_whenMarkerRowSurvives', () => {
  const sql = fakeSql({ counts: { demographic: 2 } });
  assert.deepEqual(auditResidue({ sql }), [{ table: 'demographic', count: 2 }]);
});

test('shouldReportNothing_whenNoMarkerRowSurvives', () => {
  assert.deepEqual(auditResidue({ sql: fakeSql() }), []);
});

test('shouldReportEveryTable_whenSeveralKeepMarkerRows', () => {
  const sql = fakeSql({ counts: { security: 1, document: 3, tickler: 1 } });
  assert.deepEqual(auditResidue({ sql }), [
    { table: 'security', count: 1 }, { table: 'tickler', count: 1 }, { table: 'document', count: 3 },
  ], 'results follow the audit order, one entry per table');
});

test('shouldAuditTheTablesTheBriefNames_withMarkerRows', () => {
  assert.deepEqual(MARKER_TABLES.map((entry) => entry.table), BRIEF_TABLES);
  const sql = fakeSql();
  auditResidue({ sql });
  for (const name of BRIEF_TABLES) {
    assert.ok(sql.queries.some((query) => query.startsWith(`SELECT COUNT(*) FROM \`${name}\` WHERE`)),
      `${name} was never counted`);
  }
  assert.ok(MARKER_PREFIXES.includes('FAKE-PW'), 'the per-run marker prefix is FAKE-PW');
});

test('shouldMatchMarkersCaseSensitively_whenTheDemoDataIsAllFakePrefixed', () => {
  // Every demo name starts FAKE-, and general_ci would match FAKE-Pwalski against FAKE-PW%.
  const sql = fakeSql();
  auditResidue({ sql });
  const demographic = sql.queries.find((query) => query.includes('FROM `demographic`'));
  assert.match(demographic, /BINARY `last_name` LIKE 'FAKE-PW%'/);
  assert.match(demographic, /BINARY `first_name` LIKE 'FAKE-PW%'/);
  assert.doesNotMatch(demographic, /LIKE 'FAKE-%'/, 'FAKE- alone would flag the whole demo dataset');
  const security = sql.queries.find((query) => query.includes('FROM `security`'));
  assert.match(security, /BINARY `user_name` LIKE 'FAKEPW%'/, 'a throwaway login drops the hyphen of the marker');
});

test('shouldEscapeLikeWildcards_whenBuildingAPrefixPattern', () => {
  assert.equal(likePrefix('FAKE-PW'), 'FAKE-PW%');
  assert.equal(likePrefix('PW_X%'), 'PW\\_X\\%%');
  assert.equal(likePrefix('a\\b'), 'a\\\\b%');
});

test('shouldNotCountOrQuery_whenATableIsNotInstalled', () => {
  // billingmaster is a British Columbia table: the Ontario schema has none.
  const sql = fakeSql({ schema: installedSchema(['billingmaster']), counts: { billingmaster: 9 } });
  const { residue, absent } = auditResidueDetailed({ sql });
  assert.deepEqual(residue, []);
  assert.deepEqual(absent, ['billingmaster']);
  assert.ok(!sql.queries.some((query) => query.includes('FROM `billingmaster`')), 'an absent table is not queried');
});

test('shouldFailLoudly_whenAnAuditedColumnHasGone', () => {
  // A renamed column would otherwise shrink the audit's coverage without a sound.
  const schema = installedSchema();
  schema.billing_on_cheader1 = schema.billing_on_cheader1.filter((column) => column !== 'comment1');
  assert.throws(() => auditResidue({ sql: fakeSql({ schema }) }), /billing_on_cheader1.*comment1/);
});

test('shouldNeverWrite_andNeverSelectRowContents', () => {
  const sql = fakeSql({ digests: { fax_config: [['1', 'a'.repeat(64)]] } });
  const baseline = captureBaseline({ sql, mutates: ['property:consultRequestEnabled'] });
  auditResidue({ sql, since: baseline });
  for (const query of sql.queries.filter((entry) => !/COUNT\(\*\)|information_schema/.test(entry))) {
    assert.match(query, /SHA2\(/, 'a state table is compared by digest, never by value');
    assert.doesNotMatch(query, /SELECT \*/);
  }
});

const DIGEST_A = 'a'.repeat(64);
const DIGEST_B = 'b'.repeat(64);
const DIGEST_C = 'c'.repeat(64);

test('shouldReportFaxConfig_whenTheRowDiffersFromTheBaseline', () => {
  const digests = { fax_config: [['1', DIGEST_A]] };
  const sql = fakeSql({ digests });
  const baseline = captureBaseline({ sql });
  assert.deepEqual(auditResidue({ sql, since: baseline }), [], 'an untouched table is clean');

  digests.fax_config = [['1', DIGEST_B]];
  assert.deepEqual(auditResidue({ sql, since: baseline }), [{ table: 'fax_config', count: 1 }], 'a changed row');

  digests.fax_config = [['1', DIGEST_A], ['2', DIGEST_C]];
  assert.deepEqual(auditResidue({ sql, since: baseline }), [{ table: 'fax_config', count: 1 }], 'a created row');

  digests.fax_config = [];
  assert.deepEqual(auditResidue({ sql, since: baseline }), [{ table: 'fax_config', count: 1 }], 'a deleted row');

  digests.fax_config = [['2', DIGEST_C], ['1', DIGEST_B]];
  assert.deepEqual(auditResidue({ sql, since: baseline }), [{ table: 'fax_config', count: 2 }], 'one changed, one created');
});

test('shouldReportEncounterForm_whenARegistrationIsAddedRemovedOrHidden', () => {
  const digests = { encounterForm: [['../form/a.jsp', DIGEST_A], ['../form/b.jsp', DIGEST_B]] };
  const sql = fakeSql({ digests });
  const baseline = captureBaseline({ sql });
  digests.encounterForm = [['../form/a.jsp', DIGEST_A], ['../form/b.jsp', DIGEST_C], ['../form/c.jsp', DIGEST_C]];
  assert.deepEqual(auditResidue({ sql, since: baseline }), [{ table: 'encounterForm', count: 2 }]);
});

test('shouldAuditOnlyThePropertyNames_whenMutatesListsThem', () => {
  const digests = { property: [['7', DIGEST_A]] };
  const sql = fakeSql({ digests });
  const without = captureBaseline({ sql, mutates: ['fax_config'] });
  assert.ok(!sql.queries.some((query) => query.includes('FROM `property`')), 'no property names listed, no property read');
  digests.property = [['7', DIGEST_B]];
  assert.deepEqual(auditResidue({ sql, since: without }), [], 'property is not audited unless mutates names it');

  digests.property = [['7', DIGEST_A]];
  const baseline = captureBaseline({ sql, mutates: ['property:consultRequestEnabled', 'UserProperty:schedule_start'] });
  const read = sql.queries.filter((query) => query.includes('FROM `property`')).pop();
  assert.match(read, /`name` IN \('consultRequestEnabled','schedule_start'\)/, 'the audit is limited to the listed names');
  digests.property = [['7', DIGEST_B], ['8', DIGEST_C]];
  assert.deepEqual(auditResidue({ sql, since: baseline }), [{ table: 'property', count: 2 }],
    'a UserProperty row is a row of the property table');
});

test('shouldCompareNoStateTable_whenNoBaselineIsGiven', () => {
  const sql = fakeSql({ digests: { fax_config: [['1', DIGEST_A]] }, counts: { provider: 1 } });
  assert.deepEqual(auditResidue({ sql }), [{ table: 'provider', count: 1 }]);
  assert.ok(!sql.queries.some((query) => query.includes('SHA2(')), 'with nothing to compare against it reads no digests');
});

test('shouldSkipAStateTable_whenItIsNotInstalledAtEitherEnd', () => {
  const schema = installedSchema();
  delete schema.encounterForm;
  const sql = fakeSql({ schema });
  const baseline = captureBaseline({ sql });
  const { residue, absent } = auditResidueDetailed({ sql, since: baseline });
  assert.deepEqual(residue, []);
  assert.ok(absent.includes('encounterForm'));
});

test('shouldReturnOnlyTableAndCount_neverRowContents', () => {
  const sql = fakeSql({ counts: { demographic: 1 }, digests: { fax_config: [['1', DIGEST_A]] } });
  const baseline = captureBaseline({ sql });
  const result = auditResidue({ sql, since: { ...baseline } });
  for (const entry of result) assert.deepEqual(Object.keys(entry).sort(), ['count', 'table']);
  assert.ok(!JSON.stringify(result).includes(DIGEST_A));
});

test('shouldNameTheStateTables_theBriefListsBesideTheMarkerTables', () => {
  assert.deepEqual(STATE_TABLES.map((entry) => entry.table), [FAX_CONFIG_TABLE, 'encounterForm']);
});

test('shouldSplitMutates_intoPropertyNamesAndTheRest', () => {
  assert.deepEqual(parseMutates(['fax_config', 'property:a_b', 'UserProperty:c.d', 'scheduledate', 'file:eform-images']), {
    propertyNames: ['a_b', 'c.d'],
    audited: ['fax_config'],
    notDiffed: ['scheduledate', 'file:eform-images'],
  });
  assert.deepEqual(parseMutates(undefined), { propertyNames: [], audited: [], notDiffed: [] });
  assert.throws(() => parseMutates(['fax config']), /not a valid mutates entry/);
  assert.throws(() => parseMutates(['property:']), /not a valid mutates entry/);
  assert.throws(() => parseMutates(['property:a b']), /not a valid mutates entry/);
  assert.throws(() => parseMutates('fax_config'), /must be an array/);
});

test('shouldFormatResidueLines_asTableThenCount', () => {
  assert.deepEqual(formatResidue([{ table: 'demographic', count: 2 }, { table: 'fax_config', count: 1 }]),
    ['residue: demographic 2', 'residue: fax_config 1']);
  assert.deepEqual(formatResidue([]), ['residue audit: no residue']);
});

test('shouldSayRowsAdded_forAnEntryOfTheRowCountDiff', () => {
  assert.equal(describeResidue({ table: 'providerLabRoutingLock', count: 3, kind: ROWS_ADDED }), 'providerLabRoutingLock 3 (rows added)');
  assert.equal(describeResidue({ table: 'tickler', count: 1 }), 'tickler 1');
  assert.deepEqual(formatResidue([{ table: 'formRourke2020', count: 2, kind: ROWS_ADDED }]), ['residue: formRourke2020 2 (rows added)']);
});

/*
 * THE ROW-COUNT DIFF. A table with no marker column cannot be found by the marker audit, so the baseline counts the rows of
 * EVERY base table and the audit reports each table that has more. Task 32 found by hand that five checks leaked a
 * providerLabRoutingLock row per lab (34 rows) and that no audit could see it.
 */
const COUNTS = { demographic: 40, form_boolean_value: 10, formRourke2020: 0, providerLabRoutingLock: 9, patientLabRouting: 25,
  measurementsExt: 300, log: 1000, hash_audit: 261 };

test('shouldReportEveryTableThatGrew_whateverItsColumns', () => {
  const rowCounts = { ...COUNTS };
  const sql = fakeSql({ rowCounts });
  const baseline = captureBaseline({ sql });
  assert.deepEqual(auditResidue({ sql, since: baseline }), [], 'a run that adds nothing is clean');

  rowCounts.form_boolean_value += 4;
  rowCounts.formRourke2020 += 1;
  rowCounts.providerLabRoutingLock += 34;
  rowCounts.patientLabRouting += 1;
  rowCounts.measurementsExt += 2;
  assert.deepEqual(auditResidue({ sql, since: baseline }), [
    { table: 'formRourke2020', count: 1, kind: ROWS_ADDED },
    { table: 'form_boolean_value', count: 4, kind: ROWS_ADDED },
    { table: 'measurementsExt', count: 2, kind: ROWS_ADDED },
    { table: 'patientLabRouting', count: 1, kind: ROWS_ADDED },
    { table: 'providerLabRoutingLock', count: 34, kind: ROWS_ADDED },
  ]);
});

test('shouldNotReportTheTablesThatGrowOnEveryRun_butSayHowMuchTheyGrew', () => {
  const rowCounts = { ...COUNTS };
  const sql = fakeSql({ rowCounts });
  const baseline = captureBaseline({ sql });
  rowCounts.log += 120;
  rowCounts.hash_audit += 2;
  const report = auditResidueDetailed({ sql, since: baseline });
  assert.deepEqual(report.residue, []);
  assert.deepEqual(report.allowedGrowth, [{ table: 'hash_audit', count: 2 }, { table: 'log', count: 120 }],
    'what the allow-list absorbed is said, not hidden');
  rowCounts.demographic += 1;
  assert.deepEqual(auditResidue({ sql, since: baseline }), [{ table: 'demographic', count: 1, kind: ROWS_ADDED }],
    'a table outside the allow-list is reported beside them');
});

test('shouldGiveEveryAllowedTableAReason_andAllowOnlyTheAuditTrails', () => {
  assert.deepEqual(ROW_GROWTH_ALLOWED.map((entry) => entry.table), ['log', 'hash_audit']);
  for (const entry of ROW_GROWTH_ALLOWED) {
    assert.match(entry.table, /^[A-Za-z_][A-Za-z0-9_]*$/);
    assert.ok(typeof entry.reason === 'string' && entry.reason.length > 30, `${entry.table} has no reason`);
  }
  assert.ok(Object.isFrozen(ROW_GROWTH_ALLOWED), 'the allow-list is not edited at run time');
});

test('shouldNotReportATableThatShrankOrVanished_butCountOneCreatedDuringTheRunFromZero', () => {
  const growth = rowGrowth({ a: 5, b: 5, c: 5 }, { a: 4, b: 5, d: 2 });
  assert.deepEqual(growth.grown, [{ table: 'd', count: 2, kind: ROWS_ADDED }], 'a (shrank), b (same), c (gone) are not residue; d is new');
  assert.deepEqual(growth.allowedGrowth, []);
});

test('shouldHonourAnAllowListGiven_whenDiffingCounts', () => {
  const growth = rowGrowth({ x: 1, y: 1 }, { x: 4, y: 3 }, [{ table: 'x' }]);
  assert.deepEqual(growth.grown, [{ table: 'y', count: 2, kind: ROWS_ADDED }]);
  assert.deepEqual(growth.allowedGrowth, [{ table: 'x', count: 3 }]);
});

test('shouldCountEveryBaseTable_inStatementsOfAHundred_quotingAnyName', () => {
  const rowCounts = {};
  for (let index = 0; index < 250; index++) rowCounts[`t${String(index).padStart(3, '0')}`] = index;
  rowCounts['odd`name'] = 7;
  const sql = fakeSql({ rowCounts });
  assert.deepEqual(countRows(sql), rowCounts);
  const statements = sql.queries.filter((query) => /^SELECT '/.test(query));
  assert.equal(statements.length, 3, '251 tables are counted in three statements');
  for (const statement of statements) assert.ok(statement.split(' UNION ALL ').length <= 100, 'a statement counts at most 100 tables');
  assert.ok(statements.some((statement) => statement.includes('FROM `odd``name`')), 'a backtick in a table name is doubled');
  assert.ok(sql.queries.some((query) => /information_schema\.TABLES.*TABLE_TYPE='BASE TABLE'/.test(query)), 'views are not tables');
});

test('shouldReadOnlyCounts_fromEveryTable', () => {
  const sql = fakeSql({ rowCounts: COUNTS });
  const baseline = captureBaseline({ sql });
  auditResidue({ sql, since: baseline });
  const reads = sql.queries.filter((query) => /^SELECT '/.test(query));
  assert.ok(reads.length >= 2);
  for (const query of reads) assert.doesNotMatch(query, /SELECT \*|WHERE|LIMIT/, 'a table is only counted');
  assert.ok(!JSON.stringify(baseline).includes('SELECT'), 'the baseline holds counts, nothing else');
});

test('shouldSkipTheRowCountDiff_whenTheBaselineHoldsNoCounts', () => {
  const rowCounts = { ...COUNTS };
  const sql = fakeSql({ rowCounts });
  const { rowCounts: _dropped, ...older } = captureBaseline({ sql });
  rowCounts.demographic += 5;
  const before = sql.queries.length;
  assert.deepEqual(auditResidue({ sql, since: older }), []);
  assert.ok(!sql.queries.slice(before).some((query) => /^SELECT '/.test(query)), 'with nothing to compare against it counts nothing');
  assert.deepEqual(auditResidueDetailed({ sql, since: older }).allowedGrowth, []);
});

test('shouldNotCountRows_whenNoBaselineIsGiven', () => {
  const sql = fakeSql({ rowCounts: COUNTS });
  assert.deepEqual(auditResidue({ sql }), []);
  assert.ok(!sql.queries.some((query) => /^SELECT '/.test(query)), 'without a baseline there is nothing to subtract');
});

test('shouldFailLoudly_whenARowCountIsNotANumber', () => {
  const sql = fakeSql({ rowCounts: { demographic: 1 } });
  const rows = sql.rows;
  sql.rows = (query) => (/^SELECT '/.test(query) ? [['demographic', 'x']] : rows(query));
  assert.throws(() => countRows(sql), /row count of demographic was not a number/);
  sql.rows = (query) => (/^SELECT '/.test(query) ? [] : rows(query));
  assert.throws(() => countRows(sql), /row count of 1 table\(s\) was not returned/);
});

test('shouldListEveryTableTheBriefNames_asCoveredByTheRowCounts', () => {
  // The tables with no marker column: any run that leaves a row in one of them is reported.
  const covered = ['form_boolean_value', 'formRourke2020', 'providerLabRoutingLock', 'patientLabRouting', 'measurementsExt'];
  const rowCounts = Object.fromEntries(covered.map((name) => [name, 0]));
  const sql = fakeSql({ rowCounts });
  const baseline = captureBaseline({ sql });
  for (const name of covered) rowCounts[name] = 1;
  assert.deepEqual(auditResidue({ sql, since: baseline }).map((entry) => entry.table).sort(), [...covered].sort());
  for (const name of covered) assert.ok(!MARKER_TABLES.some((entry) => entry.table === name), `${name} has a marker column after all; the marker audit would cover it`);
});

/*
 * THE RUNNER FLAG. The runner takes the baseline before the first check and audits after the
 * last, prints one line per table, and exits non-zero on residue. It is driven here with spawn and
 * audit doubles; the audit itself is proved above.
 */
const check = (name, extra = {}) => ({
  name, script: `scripts/${name}-playwright-checks.js`, tiers: ['core'], assertsDatabase: true,
  provinces: ['all'], timeoutSec: 5, ...extra,
});

function runnerWith(checks, { argv = ['--residue-audit'], residueAudit, statuses = {} } = {}) {
  const lines = [];
  const events = [];
  const run = (command, args) => {
    events.push(`check ${args[0].split('/').pop().replace('-playwright-checks.js', '')}`);
    return { status: statuses[args[0].split('/').pop().replace('-playwright-checks.js', '')] ?? 0 };
  };
  const out = { log: (line = '') => lines.push(line), error: (line) => lines.push(line) };
  const code = main(argv, {}, out, {
    checks, run, readBuildIdentity: () => null, residueAudit: residueAudit && residueAudit(events),
  });
  return { code, text: lines.join('\n'), lines, events };
}

/** A residue-audit double: records when it began and finished, and reports what it is given. */
const auditDouble = (report, { beginFails, finishFails } = {}) => (events) => ({
  begin({ mutates }) {
    events.push(`begin ${JSON.stringify(mutates)}`);
    if (beginFails) throw new Error(beginFails);
    return {
      finish() {
        events.push('finish');
        if (finishFails) throw new Error(finishFails);
        return report;
      },
      dispose() { events.push('dispose'); },
    };
  },
});

test('shouldParseTheFlag_whenResidueAuditIsGiven', () => {
  assert.equal(parseArguments([]).residueAudit, false);
  assert.equal(parseArguments(['--residue-audit']).residueAudit, true);
  assert.equal(parseArguments(['--residue-audit', '--only', 'a']).residueAudit, true);
  assert.throws(() => parseArguments(['--residue-audits']), /Unknown argument/);
});

test('shouldTakeTheBaselineBeforeTheFirstCheck_andAuditAfterTheLast', () => {
  const { events, code } = runnerWith([check('a'), check('b')], {
    residueAudit: auditDouble({ residue: [], absent: [], notDiffed: [] }),
  });
  assert.equal(code, EXIT_PASS);
  assert.deepEqual(events.map((event) => event.split(' ')[0]), ['begin', 'check', 'check', 'finish', 'dispose']);
});

test('shouldPassTheSelectedChecksMutates_toTheBaseline', () => {
  const { events } = runnerWith([
    check('a', { mutates: ['fax_config'] }), check('b', { mutates: ['property:x', 'fax_config'] }), check('c'),
  ], { argv: ['--residue-audit', '--skip', 'c'], residueAudit: auditDouble({ residue: [], absent: [], notDiffed: [] }) });
  assert.equal(events[0], 'begin ["fax_config","property:x"]', 'the union of the selected checks, once each');
});

test('shouldExitNonZeroAndNameEachTable_whenResidueIsFound', () => {
  const { code, text } = runnerWith([check('a')], {
    residueAudit: auditDouble({ residue: [{ table: 'demographic', count: 2 }, { table: 'fax_config', count: 1 }], absent: [], notDiffed: [] }),
  });
  assert.equal(code, EXIT_FAIL);
  assert.match(text, /^residue: demographic 2$/m);
  assert.match(text, /^residue: fax_config 1$/m);
  assert.match(text, /FAIL\s+residue-audit/, 'the summary names the failure like the identity guard does');
  assert.doesNotMatch(text, /residue audit: no residue/);
});

test('shouldSayWhatTheAllowListAbsorbed_andNameRowsAdded', () => {
  const clean = runnerWith([check('a')], {
    residueAudit: auditDouble({ residue: [], absent: [], notDiffed: [], allowedGrowth: [{ table: 'log', count: 41 }, { table: 'hash_audit', count: 2 }] }),
  });
  assert.equal(clean.code, EXIT_PASS, 'growth the allow-list absorbs is not residue');
  assert.match(clean.text, /^residue audit: no residue$/m);
  assert.match(clean.text, /^residue audit: rows added to tables that grow on every run \(not residue\): log 41, hash_audit 2$/m);

  const leaked = runnerWith([check('a')], {
    residueAudit: auditDouble({ residue: [{ table: 'providerLabRoutingLock', count: 3, kind: ROWS_ADDED }], absent: [], notDiffed: [], allowedGrowth: [] }),
  });
  assert.equal(leaked.code, EXIT_FAIL);
  assert.match(leaked.text, /^residue: providerLabRoutingLock 3 \(rows added\)$/m);
  assert.match(leaked.text, /FAIL\s+residue-audit/);
  assert.doesNotMatch(leaked.text, /rows added to tables that grow/);
});

test('shouldPrintNoResidue_andPass_whenTheAuditIsClean', () => {
  const { code, text } = runnerWith([check('a')], {
    residueAudit: auditDouble({ residue: [], absent: ['billingmaster'], notDiffed: ['scheduledate'] }),
  });
  assert.equal(code, EXIT_PASS);
  assert.match(text, /^residue audit: no residue$/m);
  assert.match(text, /not installed here: billingmaster/, 'a table skipped as absent is said, not hidden');
  assert.match(text, /not diffed: scheduledate/, 'a mutates entry the audit cannot diff is said, not hidden');
  assert.doesNotMatch(text, /^residue: /m);
});

test('shouldAuditResidue_evenWhenACheckFailed', () => {
  const { code, text, events } = runnerWith([check('a')], {
    statuses: { a: 1 },
    residueAudit: auditDouble({ residue: [{ table: 'tickler', count: 1 }], absent: [], notDiffed: [] }),
  });
  assert.equal(code, EXIT_FAIL);
  assert.ok(events.includes('finish'), 'a failed check is exactly when residue is likely');
  assert.match(text, /residue: tickler 1/);
});

test('shouldRefuseToRun_whenTheBaselineCannotBeTaken', () => {
  const { code, text, events } = runnerWith([check('a')], {
    residueAudit: auditDouble(null, { beginFails: 'the database query failed (mysql exit 1)' }),
  });
  assert.equal(code, EXIT_FAIL);
  assert.match(text, /baseline/);
  assert.match(text, /mysql exit 1/);
  assert.ok(!events.some((event) => event.startsWith('check')), 'no check runs without a baseline to audit against');
});

test('shouldFailTheRun_whenTheAuditItselfFails', () => {
  const { code, text, events } = runnerWith([check('a')], {
    residueAudit: auditDouble(null, { finishFails: 'the database query failed (mysql exit 1)' }),
  });
  assert.equal(code, EXIT_FAIL, 'an audit that could not run must never read as clean');
  assert.match(text, /FAIL\s+residue-audit/);
  assert.doesNotMatch(text, /residue audit: no residue/);
  assert.ok(events.includes('dispose'));
});

test('shouldNotTouchTheDatabase_whenTheFlagIsAbsent', () => {
  const { events, code, text } = runnerWith([check('a')], {
    argv: [], residueAudit: auditDouble({ residue: [{ table: 'demographic', count: 9 }], absent: [], notDiffed: [] }),
  });
  assert.equal(code, EXIT_PASS);
  assert.deepEqual(events, ['check a']);
  assert.doesNotMatch(text, /residue/);
});

test('shouldNotAudit_whenOnlyListingOrDryRunning', () => {
  for (const argv of [['--residue-audit', '--list'], ['--residue-audit', '--dry-run']]) {
    const { events, code } = runnerWith([check('a')], {
      argv, residueAudit: auditDouble({ residue: [], absent: [], notDiffed: [] }),
    });
    assert.equal(code, EXIT_PASS);
    assert.deepEqual(events, [], argv.join(' '));
  }
});
