# Raw JDBC accessor retirement (#5507)

## Inventory and scope

The `release/2026.08` audit at `112feaecb7b82b4bfe381bae3d93560e9b97f9d6`
found **zero production business callers** of
`DbConnectionFilter.getThreadLocalDbConnection()`, directly or through a wrapper.
Search both `src/main/java` and `src/main/webapp`, including static imports and
multiline calls, by searching the symbol rather than a fully qualified invocation:

```sh
rg -n 'getThreadLocalDbConnection|releaseThreadLocalDbConnection' src/main
rg -n 'LegacyJdbcQuery|DataSourceUtils|getConnection' src/main/java/io/github/carlos_emr/carlos/db
```

The only accessor invocation sites were tests in `DbConnectionFilterUnitTest`
and `RequestBoundaryFilterOrderIntegrationTest`. The production definition,
its comments and its private acquisition/holder tracking code had survived after
its callers were replaced. The production-source guard in `LegacyJdbcQueryUnitTest`
now rejects the symbol in any production Java/JSP/JSP fragment, including a
reintroduced definition or statically imported call.

This change removes that unused accessor and its dedicated thread-local/global
holder tracking, plus its unused release APIs. It introduces **no business-query
migration and no new transaction boundary**. Query results, generated IDs,
permissions and audit entry points therefore keep their existing implementation.

## What already changed and what remains

PR [#2233](https://github.com/carlos-emr/carlos/pull/2233), commit
`7f28e615af136192f24127de7a58e97a8630d811`, replaced the old raw acquisition paths
with `LegacyJdbcQuery`. That is an interim Spring-aware JDBC boundary using
`DataSourceUtils`; it is **not** a completed migration of every query to JPA.
The remaining typed DAO/service migration belongs to
[#5508](https://github.com/carlos-emr/carlos/issues/5508), with the form-record
entity/DAO work tracked under [#2249](https://github.com/carlos-emr/carlos/issues/2249).

| Area | Current path and behavior | Follow-up / reason retained |
| --- | --- | --- |
| `DBHandler`, `DBPreparedHandler` | Removed after the #5508 inventory confirmed zero production consumers; source guard forbids their return | See the [current helper migration inventory](legacy-query-helper-migration.md) |
| `DBPreparedHandlerParam` | Implements `LegacyJdbcQuery.LegacyJdbcParameter`; supplied by scheduling code, lookup DAO operations and report JSPs | #5547 / #5549; retain until these callers migrate |
| `DBHelp`, `JDBCUtil`, `FrmRecordHelp` | Delegate acquisition/query execution to `LegacyJdbcQuery`; some still expose JDBC cursors or connections and perform writes | #5508 / #2249; preserve existing transaction participation while migrating module by module |
| Billing, labels, lab uploads, curated reports and admin role repair | Remaining connection-passing callers use `LegacyJdbcQuery.getConnection()`; Jasper/report renderers need a connection, while other callers retain legacy query ownership. The recent-diagnosis picker and measurement graph connection were migrated in #5508 | #5546–#5549; no raw-accessor callers remain; Spring synchronization does not itself create a service transaction |
| Request filter and background worker finalizers | `releaseAllThreadDbResources()` releases `LegacyJdbcQuery` resources and tracked datasource connections after transactions finish | Required while legacy callers remain; filter ordering and worker finalizers are preserved |
| Web application shutdown | Releases current-thread legacy resources, then `OscarTrackingBasicDataSource.clearTrackingState()` and driver/executor resources | Required infrastructure lifecycle, preserved independently of the removed raw-holder registry |
| Flyway, datasource policy, CDS maintenance connections | Dedicated infrastructure/maintenance connection ownership | Outside #5507; not moved onto clinical JPA |
| Query by Example | Separate restricted reporting-account connection | Security boundary; must never use the clinical EntityManager |

`dbExtract` was retired by [#5552](https://github.com/carlos-emr/carlos/pull/5552)
and is no longer a retained helper.

There are **no remaining exceptions to the retired accessor**. The retained
legacy-helper work is explicitly linked above rather than described as finished
JPA work. Historical generated Javadoc under `docs/static/javadoc` is an upstream
snapshot, not executable source or the current caller inventory.

## Transaction and cleanup verification

`LegacyJdbcJpaDaoIntegrationTest` exercises the retained compatibility
boundary with the real `PropertyDao`, JPA transaction manager and datasource.
The test disables the inherited test transaction and uses explicit
`TransactionTemplate` transactions for its JPA and bound JDBC writes. It closes
the JDBC wrapper before another DAO operation and verifies the commit through an
independent connection. A second case observes that neither
write is visible before commit, injects failure after both writes and checks
that neither survives rollback. Fixtures use unique synthetic property keys and
are removed in committed cleanup transactions.

It runs in the normal H2 suite and against physical MariaDB using the existing
isolated DAO harness (`CARLOS_TEST_MARIADB_DAO=true`); it does not infer MariaDB
behavior from H2. Run the physical test with the same environment as the
`mariadb-dao-tests` CI job, selecting
`-Dtest=LegacyJdbcJpaDaoIntegrationTest`.

`RequestBoundaryFilterOrderIntegrationTest` still runs real Struts/JSP/servlet
paths in embedded Tomcat, including failure paths, the old-order leak control
and a transaction that must survive intermediate legacy-resource release. It
now leaks only the two connection kinds that still exist. Shutdown and filter
unit regressions verify the retained cleanup ordering and failure isolation.
