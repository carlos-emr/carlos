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
The `tests` CI job supplies a physical MariaDB service, runs this suite separately,
and rejects empty or skipped results.

With MariaDB's `REPEATABLE READ`, a stale ownership update can raise server error
1020 (`HY000`, record changed since last read) before returning an affected-row
count. The regression accepts that precise refusal or the DAO's zero-row refusal,
and verifies in a fresh transaction that the winning owner's row is unchanged.
Other JDBC failures, including the original SQL grammar error, fail the test.

## Upgrade audit

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
