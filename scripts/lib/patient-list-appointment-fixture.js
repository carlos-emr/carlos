/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The three-appointment fixture behind
 * scripts/patient-list-by-appointment-export-playwright-checks.js (issue #4412).
 *
 * The export check asserts exact rows for 2026-08-07..2026-08-10, so it needs
 * exactly three appointments in that window, booked against three demo-dataset
 * patients and two demo providers, and none in its empty-range window. Nothing
 * in a package install creates them, so before this module the check refused to
 * run on a fresh install unless an operator had inserted them by hand.
 *
 * Three ways in, chosen by planPatientListFixture():
 *   provisioned  PATIENT_LIST_FIXTURE_PROFILE names the contract: the operator
 *                inserted the rows (deb-install-validation.md fixture d). The
 *                check stays read-only, exactly as before.
 *   seed         no profile, but MYSQL_* is configured: the check inserts the
 *                three rows itself, marked with a per-run notes value, and
 *                deletes exactly those rows afterwards.
 *   skip         neither: SKIP (exit 2) naming both options, not a failure.
 *
 * Seeding is refused (SKIP) when the database is not the demo dataset or when
 * either date window already holds appointments, because the expected rows
 * would then be wrong for reasons that have nothing to do with the export.
 * The demo names here are FAKE- synthetic data, not PHI.
 */
const { SkipCheck, sqlString } = require('./playwright-harness');

const FIXTURE_PROFILE = 'local-seed-obec-report-v1';
const SEED_DATE_FROM = '2026-08-07';
const SEED_DATE_TO = '2026-08-10';
const EMPTY_DATE_FROM = '2026-09-01';
const EMPTY_DATE_TO = '2026-09-02';

/** The rows fixture d inserts; NULL type and empty location on purpose. */
const SEED_APPOINTMENTS = Object.freeze([
  Object.freeze({ name: 'LOCAL_SEED_OBEC_REPORT_1', providerNo: '9', date: '2026-08-07', start: '09:00:00', end: '09:15:00', demographicNo: 714 }),
  Object.freeze({ name: 'LOCAL_SEED_OBEC_REPORT_2', providerNo: '999998', date: '2026-08-08', start: '10:00:00', end: '10:15:00', demographicNo: 71 }),
  Object.freeze({ name: 'LOCAL_SEED_OBEC_REPORT_3', providerNo: '999998', date: '2026-08-10', start: '11:00:00', end: '11:15:00', demographicNo: 81 }),
]);

/** Demo-dataset identities the expected export rows were written against. */
const DEMO_PATIENTS = Object.freeze({
  714: ['FAKE-Abbott', 'FAKE-Jerilyn'],
  71: ['FAKE-Altenwerth', 'FAKE-Izola'],
  81: ['FAKE-Altenwerth', 'FAKE-Josh'],
});
const DEMO_PROVIDERS = Object.freeze(['9', '999998']);

const MARKER_PATTERN = /^playwright-patient-list-[0-9]+-[0-9]+$/;

const SKIP_HINT = `set PATIENT_LIST_FIXTURE_PROFILE=${FIXTURE_PROFILE} after inserting the rows from `
  + 'docs/ui-tests/deb-install-validation.md fixture d, or set MYSQL_* so the check can seed and remove them itself';

/**
 * Which of the three modes applies. Throws on a profile value that is set but
 * wrong: that is a typo in the run's environment, not an absent fixture.
 */
function planPatientListFixture(env = process.env) {
  const profile = env.PATIENT_LIST_FIXTURE_PROFILE || '';
  if (profile) {
    if (profile !== FIXTURE_PROFILE) {
      throw new Error(`PATIENT_LIST_FIXTURE_PROFILE must be ${FIXTURE_PROFILE} (got an unknown profile)`);
    }
    return { mode: 'provisioned' };
  }
  // MYSQL_PASSWORD is the variable every database-asserting check requires
  // (the runbook sets a dummy one for unix-socket root), so it is the signal
  // that this run was given database access at all.
  if (env.MYSQL_PASSWORD === undefined) {
    return { mode: 'skip', reason: `no fixture: ${SKIP_HINT}` };
  }
  return { mode: 'seed' };
}

function appointmentsBetween(from, to) {
  return `SELECT COUNT(*) FROM appointment WHERE appointment_date BETWEEN ${sqlString(from)} AND ${sqlString(to)}`;
}

/**
 * Seed-mode fixture. `sql` is a createSqlRunner() instance. prepare() throws
 * SkipCheck when the database cannot host a deterministic fixture; cleanup() is
 * idempotent and deletes only rows carrying this run's marker, so it is safe to
 * call from both the finally and a signal handler.
 */
function createPatientListFixture(sql, { marker }) {
  if (!MARKER_PATTERN.test(String(marker))) {
    throw new Error('Invalid patient-list fixture marker');
  }
  let seeded = false;

  function prepare() {
    const placeholders = Object.keys(DEMO_PATIENTS).join(',');
    const found = new Map(sql.rows(`SELECT demographic_no, last_name, first_name FROM demographic WHERE demographic_no IN (${placeholders})`)
      .map(([id, last, first]) => [String(id), [last, first]]));
    for (const [id, [last, first]] of Object.entries(DEMO_PATIENTS)) {
      const row = found.get(id);
      if (!row || row[0] !== last || row[1] !== first) {
        throw new SkipCheck(`demographic ${id} is not the demo-dataset patient the expected rows name; this check needs the demo database (or ${SKIP_HINT})`);
      }
    }
    const providers = new Set(sql.rows(`SELECT provider_no FROM provider WHERE provider_no IN (${DEMO_PROVIDERS.map(sqlString).join(',')})`)
      .map(([id]) => String(id)));
    const missingProvider = DEMO_PROVIDERS.find((id) => !providers.has(id));
    if (missingProvider) {
      throw new SkipCheck(`provider ${missingProvider} is missing; this check needs the demo database`);
    }
    const inSeedRange = Number(sql.value(appointmentsBetween(SEED_DATE_FROM, SEED_DATE_TO)));
    const inEmptyRange = Number(sql.value(appointmentsBetween(EMPTY_DATE_FROM, EMPTY_DATE_TO)));
    if (inSeedRange !== 0 || inEmptyRange !== 0) {
      throw new SkipCheck(`the export windows already hold appointments (${SEED_DATE_FROM}..${SEED_DATE_TO}: ${inSeedRange}, `
        + `${EMPTY_DATE_FROM}..${EMPTY_DATE_TO}: ${inEmptyRange}), so seeded rows could not be asserted exactly; `
        + `if they are fixture d's rows, set PATIENT_LIST_FIXTURE_PROFILE=${FIXTURE_PROFILE}`);
    }
    // Mark the run as owning rows BEFORE the insert: if the statement lands but
    // its reply is lost, cleanup still runs and removes whatever it wrote.
    seeded = true;
    const values = SEED_APPOINTMENTS.map((a) => `(${[
      sqlString(a.providerNo), sqlString(a.date), sqlString(a.start), sqlString(a.end), sqlString(a.name),
      a.demographicNo, sqlString(marker), "''", "''", "''", 'NULL', "''", "''", "'t'", 'NOW()', "'carlosdoc'",
    ].join(',')})`).join(',');
    sql.execute('INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no, '
      + 'notes, reason, location, resources, type, style, billing, status, createdatetime, creator) '
      + `VALUES ${values}`);
    const owned = Number(sql.value(`SELECT COUNT(*) FROM appointment WHERE notes = ${sqlString(marker)}`));
    if (owned !== SEED_APPOINTMENTS.length) {
      throw new Error(`seeded ${owned} of ${SEED_APPOINTMENTS.length} fixture appointments`);
    }
    return { seeded: owned };
  }

  function cleanup() {
    if (!seeded) {
      return 0;
    }
    const where = `notes = ${sqlString(marker)} AND name LIKE 'LOCAL\\_SEED\\_OBEC\\_REPORT\\_%' `
      + `AND appointment_date BETWEEN ${sqlString(SEED_DATE_FROM)} AND ${sqlString(SEED_DATE_TO)}`;
    sql.execute(`DELETE FROM appointment WHERE ${where}`);
    const left = Number(sql.value(`SELECT COUNT(*) FROM appointment WHERE ${where}`));
    if (left !== 0) {
      throw new Error(`${left} fixture appointment(s) survived cleanup`);
    }
    seeded = false;
    return SEED_APPOINTMENTS.length;
  }

  return { prepare, cleanup };
}

module.exports = {
  EMPTY_DATE_FROM,
  EMPTY_DATE_TO,
  FIXTURE_PROFILE,
  SEED_APPOINTMENTS,
  SEED_DATE_FROM,
  SEED_DATE_TO,
  createPatientListFixture,
  planPatientListFixture,
};
