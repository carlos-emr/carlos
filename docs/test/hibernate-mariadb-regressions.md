# Hibernate 7.4 and MariaDB regression checks

Issue [#5399](https://github.com/carlos-emr/carlos/issues/5399) made Patient Match
return HTTP 500 after the Hibernate 7.2.7 to 7.4.14 upgrade. With the production
`OscarMySQL5Dialect`, Hibernate wraps the correlated `EXISTS` in the measurement
ownership update in a derived table. MariaDB cannot resolve the outer update
alias inside that derived table.

The correction uses an uncorrelated membership subquery. The measurement ID,
previous patient and matching `lab_no` extension remain predicates of the same
atomic update. The affected-row check and managed-entity refresh remain in place;
the clinical-value immutability callback is unchanged.

## Physical database regression suite

`MeasurementPatientMatchMariaDbIntegrationTest` runs the real DAO, Connector/J
and production dialect against MariaDB. It creates a random `carlos_test_5399_*`
database and drops only that database after the suite. Supply a disposable
MariaDB server URL ending in `/`, without a database name or query string:

```sh
export CARLOS_TEST_MARIADB_URL=jdbc:mysql://127.0.0.1:3306/
export CARLOS_TEST_MARIADB_USER=carlos_test
# Supply CARLOS_TEST_MARIADB_PASSWORD through the environment when required.
mvn -B -ntp test -Dtest=MeasurementPatientMatchMariaDbIntegrationTest -Dtest.forkCount=1
```

The test account needs permission to create/drop `carlos_test_5399_*` schemas.
Fixtures are synthetic. The suite verifies the MariaDB server identity and uses
separate transactions to check committed results, stale ownership and rollback.
It also checks rejected sources, duplicate extensions, unchanged clinical fields
and extensions, invalid/detached inputs, pessimistic locks, numeric bounds and
timestamp bindings. Without the URL it is skipped in ordinary H2 test runs.
The `mariadb-dao-tests` CI job supplies a physical MariaDB service and includes
this suite. Empty or skipped required coverage fails the job.

With MariaDB's `REPEATABLE READ`, a stale ownership update can raise server error
1020 (`HY000`, record changed since last read) before returning an affected-row
count. The regression accepts that precise refusal or the DAO's zero-row refusal,
and verifies in a fresh transaction that the winning owner's row is unchanged.
Other JDBC failures, including the original SQL grammar error, fail the test.

## Parallel H2 and MariaDB CI lanes

The existing `tests` job continues to run the full Java suite with H2. The
separate `mariadb-dao-tests` job runs existing Spring DAO integration tests on
MariaDB, plus the ten Patient Match regressions. It selects `*Dao*IntegrationTest`,
`*DAO*IntegrationTest`, and `QueryReturnTypeIntegrationTest`, with the inherited
`spring-context` tag identifying the configurable Spring harness. Standalone
tests that explicitly create their own H2 factories remain covered by the full
H2 job; they are not presented as MariaDB coverage.

Set `CARLOS_TEST_MARIADB_DAO=true` alongside the JDBC environment above to opt
the existing `CarlosTestBase` into MariaDB. Both its JPA EntityManagerFactory
and legacy SessionFactory use `OscarMySQL5Dialect`. Each cached Spring context
owns a random database, which is dropped after its persistence factories and
pool close. `MariaDbDaoHarnessIntegrationTest` verifies the physical database,
owned schema, selected schema mode, and both dialects to prevent H2 fallback.

The broad lane uses `CARLOS_TEST_MARIADB_SCHEMA=generated` (the default), with
the same annotation-generated test schema and lookup fixtures as H2. Shared
fixture SQL uses syntax supported by both engines. The generated MariaDB
connections preserve explicitly inserted zero IDs so historical-sentinel
fixtures have the same meaning on both engines. Production DAO queries and
assertions are not rewritten. This lane checks database/dialect behavior; it
does not claim that generated column definitions equal installed definitions.

The same CI job also runs Measurement, MeasurementsExt, ProviderLabRouting,
BillingONCHeader1 and harness-verification suites with
`CARLOS_TEST_MARIADB_SCHEMA=flyway`. This creates the actual common + Ontario
migration schema and disables Hibernate DDL and test-only lookup initialization.
It retains the packaged SQL-mode, collation and Connector/J settings. This
separate check catches physical schema mismatches in those critical paths.
The report verifier requires executed cases from both schema modes and rejects
failures, errors, skipped cases, and missing Patient Match or critical-DAO coverage.

For a broad local run after compilation:

```sh
export CARLOS_TEST_MARIADB_DAO=true CARLOS_TEST_MARIADB_SCHEMA=generated
mvn -B -ntp test \
  '-Dtest=*Dao*IntegrationTest,*DAO*IntegrationTest,QueryReturnTypeIntegrationTest,MeasurementPatientMatchMariaDbIntegrationTest' \
  '-Dgroups=spring-context | mariadb-native' -Dtest.forkCount=3
```

To verify a critical path against the installed schema, set the schema mode to
`flyway` and pass `-Dcarlos.test.schema.expected=flyway`, select one of the listed
critical suites plus `MariaDbDaoHarnessIntegrationTest`, and use one fork.

## Upgrade audit

The broad MariaDB run exposed two further SQL compatibility failures:

- BC billing-history totals cast stored amounts with HQL `CAST(... AS double)`.
  Hibernate 7.4 rendered `FLOAT(53)`, which MariaDB rejects as a cast target.
  The production dialect now renders double casts as `CAST(... AS double)`,
  preserving floating point results using syntax accepted by MariaDB and MySQL
  8.0.17+. Existing billing-history integration assertions run on both engines.
- Active-client organization counts used a backslash SQL escape literal that
  MariaDB rejected. They now use `ESCAPE '!'` and escape the marker and LIKE
  wildcards in the bound value. Positive regression cases cover literal
  underscores, percent signs, exclamation marks, backslashes and apostrophes,
  including decoy organizations. This was an additional compatibility defect;
  it has not been established as introduced by the Hibernate upgrade.

Fixture portability changes retain existing assertions: MySQL-compatible
upserts replace H2-only `MERGE`, native MariaDB routines replace H2 Java aliases,
and MariaDB uses its built-in `TO_DAYS`. The generated schema also supplies the
real log index referenced by a production index hint and matches the installed
appointment-code column's case-sensitive collation.

Reviewed the upstream [7.3 migration guide](https://docs.hibernate.org/orm/7.3/migration-guide/)
and [7.4 migration guide](https://docs.hibernate.org/orm/7.4/migration-guide/),
including changed result deduplication, read-only collections, MySQL fetch-depth
defaults, pagination with collection fetch joins and schema generation.

- A static scan of concatenated Java mutation query literals found 94 mutation
  strings, with this method the only one containing a subquery. Annotation named
  queries and XML query definitions were also checked; no additional mutation
  subqueries were found. This is a source audit, not proof about dynamically
  assembled queries or database triggers.
- JPA explicitly sets `hibernate.max_fetch_depth=3`, so removal of the MySQL
  dialect's implicit default does not change that setting.
- Tickler collection fetching is confined to its detail read; paginated lists
  fetch single-valued associations. Billing header pagination selects IDs before
  fetching item collections. These paths avoid pagination over a collection
  fetch join and use list results/explicit `DISTINCT` where appropriate.
- The read-only appointment export projects DTOs rather than mutable entity
  collections. No production `@Any`, obsolete `@Columns`, Classmate SPI or Envers
  mappings were found. Production schema changes are managed by Flyway; the
  newly inferred Hibernate DDL constraints are not applied to installed schemas.

The full Java suite and installed browser checks complement this audit. Passing
these checks does not establish compatibility for every clinical workflow or
external integration.
