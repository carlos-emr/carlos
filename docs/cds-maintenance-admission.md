# CDS maintenance admission

`cds.import.enabled=true` permits import work only after explicit
`cds.import.maintenanceMode=true` activation and when `cds.import.maintenanceGuardEnabled=true`
installed the physical datasource guard after the Flyway boot gate (`carlos.flyway.onBoot=migrate`
or `validate`). Defaults preserve ordinary datasource behavior before activation. The initializer
runs after Flyway and before Hibernate, requires the migrated InnoDB admission row, and refuses
an off-guard new WAR startup when durable maintenance, a chart hold, or an entering advisory owner
already exists.

The worker acquires `CdsImportMaintenanceService.Owner` before ledger ownership/clinical work.
Its dedicated physical, unpooled connection holds a database-namespaced advisory lock for the
owner's lifetime. Ordinary guarded SQL takes a shared lock on `clinical-maintenance` in its actual
transaction; autocommit clinical statements receive a bounded statement transaction. Autocommit
query admission ends when its result closes/exhausts. Entering maintenance takes the exclusive
row lock and therefore waits for admitted JDBC, Hibernate and background transactions to commit
or roll back. New ordinary clinical work refuses once the advisory owner is entering. Neither
pool abandoned-connection reclamation nor a heartbeat controls ownership.

Clinical writes require both the private active import-unit marker and the current database owner
(token, generation, physical advisory connection). `Owner.read(Supplier)` gives bounded read-only
coordinator access; it cannot admit clinical writes or escape into another scope. Administrative
ledger and source-derived login tables have an exact SQL allowlist. Joins/subqueries with clinical
tables still require admission. Vendor unwrap, multiple SQL statements, callable routines,
unreviewed functions and implicit-commit SQL are refused while the guard is installed.

`Owner.close()` stops new unit admission, drains old units and releases the advisory connection.
It retains durable maintenance by default, including after failure or process/connection loss.
Only an explicit `allowReopen()` after successful clinical reconciliation and backlog/care-owner
policy can reopen, and a remaining chart hold vetoes reopening. A successor first drains old units
and advances the owner generation. The HTTP filter keeps login, static assets and authenticated
import/recovery routes available; clinical chart/API/print/download routes receive a safe 503.
It does not grant import privileges.

## Deployment boundary

The database barrier covers application work using **configured guarded datasources**. It cannot
retrofit an already running old/off-guard WAR, an external direct database client, or network/file
side effects that outlive their preceding database transaction. Before activation, stop and drain
all such nodes, schedulers, inbound feeds and outbound delivery workers, preserve accepted work
or arrange retry, and record operational/clinical ownership and reopening/backlog arrangements.
A configuration flag, maintenance banner, SQL history check or heartbeat is not this proof.

Inventory installed reports, views, routines, plugins, eForms, background integrations and cached
clinical consumers. The SQL guard deliberately refuses unsupported execution forms; catalogue
and exercise actual enabled consumers before activation, with explicit maintenance/refusal for
unsupported paths. Arbitrary external SQL and cached/indexed copies require the deployment
barrier and operational drain. This implementation does not claim universal old-node or arbitrary
SQL compatibility.

## Real database verification

`scripts/ci/CdsImportMariaDbBootstrap.java` is a Java source-file runner accepting a protected
`jdbcUrl`/`jdbcUser`/`jdbcPassword` properties file, the migration root, and `on` or `bc`.
It only accepts explicitly named disposable CDS test databases, verifies MariaDB 32K pages,
snapshot isolation and the repository collation, applies the actual V1 baseline plus the complete
province/common Flyway chain, validates checksums/no pending migrations, and checks native and
admission InnoDB tables. Use the WAR Flyway/MySQL jars on its classpath. A `jdbc:mariadb:` selector
is translated to `jdbc:mysql:` to exercise the WAR's actual Connector/J driver.

Mount a service-readable copy of `.devcontainer/development/config/shared/my.cnf` as
`/etc/mysql/my.cnf` before initializing the empty database volume. A workspace copy with mode
0600 and a different UID is not readable by the container's `mysql` account. Assert the actual
server variables; do not infer them from mount metadata. Credentials stay in protected files,
never process arguments or test output. If an unpublished migration changes after application,
recreate only the disposable test database and run the full chain again; never repair checksums.

Explicitly execute `CdsImportMaintenanceMariaDbIntegrationTest` with
`-Dcds.test.properties=/path/to/protected-jdbc.properties`. The tier uses the complete real schema,
two independent datasource/node instances, ordinary JDBC and actual Hibernate transactions,
autocommit result lifetime, advisory-connection loss and successor drain, bounded coordinator
reads, fail-closed release, held-chart reopening veto and disabled-guard behavior. A skipped tier
is not evidence of exclusion. The test modifies only its synthetic probe and ledger fixture rows.
