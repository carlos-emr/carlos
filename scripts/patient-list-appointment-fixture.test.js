/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  FIXTURE_PROFILE, SEED_APPOINTMENTS, createPatientListFixture, planPatientListFixture,
} = require('./lib/patient-list-appointment-fixture');

const MARKER = 'playwright-patient-list-123-456';
const DEMO_ROWS = [['714', 'FAKE-Abbott', 'FAKE-Jerilyn'], ['71', 'FAKE-Altenwerth', 'FAKE-Izola'], ['81', 'FAKE-Altenwerth', 'FAKE-Josh']];

/** A stand-in for createSqlRunner() that answers the fixture's queries from a tiny table model. */
function fakeSql({ patients = DEMO_ROWS, providers = [['9'], ['999998']], seedRange = 0, emptyRange = 0, failInsert = false } = {}) {
  const statements = [];
  let owned = 0;
  return {
    statements,
    get owned() { return owned; },
    rows(query) {
      statements.push(query);
      if (/FROM demographic/.test(query)) return patients;
      if (/FROM provider/.test(query)) return providers;
      throw new Error(`unexpected rows() query: ${query}`);
    },
    value(query) {
      statements.push(query);
      if (/notes = /.test(query)) return String(owned);
      if (/'2026-08-07' AND '2026-08-10'/.test(query)) return String(seedRange + owned);
      if (/'2026-09-01' AND '2026-09-02'/.test(query)) return String(emptyRange);
      throw new Error(`unexpected value() query: ${query}`);
    },
    execute(query) {
      statements.push(query);
      if (/^INSERT/.test(query)) {
        if (failInsert) throw new Error('the database query failed');
        owned = SEED_APPOINTMENTS.length;
      } else if (/^DELETE/.test(query)) {
        owned = 0;
      }
    },
  };
}

test('planPatientListFixture picks provisioned, seed or skip from the environment', () => {
  assert.deepEqual(planPatientListFixture({ PATIENT_LIST_FIXTURE_PROFILE: FIXTURE_PROFILE }), { mode: 'provisioned' });
  assert.deepEqual(planPatientListFixture({ MYSQL_PASSWORD: 'dummy' }), { mode: 'seed' });
  // An empty password is still "database access configured" (unix-socket root).
  assert.deepEqual(planPatientListFixture({ MYSQL_PASSWORD: '' }), { mode: 'seed' });
  const skip = planPatientListFixture({});
  assert.equal(skip.mode, 'skip');
  assert.match(skip.reason, /PATIENT_LIST_FIXTURE_PROFILE=local-seed-obec-report-v1/);
  assert.match(skip.reason, /MYSQL_\*/);
  // A typo in the profile is a misconfigured run, not a missing fixture.
  assert.throws(() => planPatientListFixture({ PATIENT_LIST_FIXTURE_PROFILE: 'local-seed-v2', MYSQL_PASSWORD: 'x' }), /must be local-seed-obec-report-v1/);
});

test('the seeded rows are the ones fixture d documents in the runbook', () => {
  const runbook = fs.readFileSync(path.join(__dirname, '..', 'docs', 'ui-tests', 'deb-install-validation.md'), 'utf8');
  for (const row of SEED_APPOINTMENTS) {
    const documented = `('${row.providerNo}','${row.date}','${row.start}','${row.end}','${row.name}',${row.demographicNo},`;
    assert.ok(runbook.includes(documented), `deb-install-validation.md fixture d no longer matches ${row.name}`);
  }
});

test('prepare seeds three marked rows and cleanup deletes only those, once', () => {
  const sql = fakeSql();
  const fixture = createPatientListFixture(sql, { marker: MARKER });
  assert.deepEqual(fixture.prepare(), { seeded: 3 });
  const insert = sql.statements.find((statement) => /^INSERT/.test(statement));
  assert.equal((insert.match(/'playwright-patient-list-123-456'/g) || []).length, 3);
  assert.match(insert, /'LOCAL_SEED_OBEC_REPORT_1'/);
  // type is NULL: the export's null-type assertion depends on it.
  assert.match(insert, /'',NULL,'',''/);
  assert.equal(fixture.cleanup(), 3);
  const deletes = sql.statements.filter((statement) => /^DELETE/.test(statement));
  assert.equal(deletes.length, 1);
  assert.match(deletes[0], /notes = 'playwright-patient-list-123-456' AND name LIKE 'LOCAL\\_SEED\\_OBEC\\_REPORT\\_%'/);
  assert.equal(fixture.cleanup(), 0, 'a second cleanup (signal handler after finally) is a no-op');
  assert.equal(sql.statements.filter((statement) => /^DELETE/.test(statement)).length, 1);
});

test('prepare SKIPs without writing when the database cannot host a deterministic fixture', () => {
  const cases = [
    ['not the demo dataset', { patients: [['714', 'Smith', 'Jan'], ...DEMO_ROWS.slice(1)] }, /not the demo-dataset patient/],
    ['a demo patient is missing', { patients: DEMO_ROWS.slice(1) }, /demographic 714/],
    ['a demo provider is missing', { providers: [['999998']] }, /provider 9 is missing/],
    ['the seed window is occupied', { seedRange: 3 }, /already hold appointments .*2026-08-10: 3/],
    ['the empty window is occupied', { emptyRange: 1 }, /2026-09-02: 1/],
  ];
  for (const [name, options, reason] of cases) {
    const sql = fakeSql(options);
    const fixture = createPatientListFixture(sql, { marker: MARKER });
    assert.throws(() => fixture.prepare(), (error) => error.name === 'SkipCheck' && reason.test(error.message), name);
    assert.ok(!sql.statements.some((statement) => /^(INSERT|DELETE)/.test(statement)), `${name}: nothing written`);
    assert.equal(fixture.cleanup(), 0, `${name}: nothing to clean`);
  }
});

test('a failed insert still leaves cleanup armed, so a partial write is removed', () => {
  const sql = fakeSql({ failInsert: true });
  const fixture = createPatientListFixture(sql, { marker: MARKER });
  assert.throws(() => fixture.prepare(), /database query failed/);
  assert.equal(fixture.cleanup(), 3);
  assert.equal(sql.statements.filter((statement) => /^DELETE/.test(statement)).length, 1);
});

test('the marker must be the per-run shape, never free text in SQL', () => {
  assert.throws(() => createPatientListFixture(fakeSql(), { marker: "x' OR '1'='1" }), /Invalid patient-list fixture marker/);
  assert.throws(() => createPatientListFixture(fakeSql(), { marker: '' }), /Invalid patient-list fixture marker/);
});
