# CDS maintenance admission

`cds.import.enabled=true` permits import work only after explicit
`cds.import.maintenanceMode=true` activation and when `cds.import.maintenanceGuardEnabled=true`
installed the physical datasource guard after the Flyway boot gate (`carlos.flyway.onBoot=migrate`
or `validate`). After Flyway, a metadata-only wrapper also protects new `cds_import_*`
mutations when clinical maintenance admission is disabled. It validates the actual JDBC encoder
and session charset before a prepared statement can bind source values, applies strict mode
through the metadata transaction, then restores the physical session. This scope requires no
maintenance lease, so upload, planning, dry-run and review metadata stay lossless before clinical
activation. Ordinary legacy SQL retains its existing behavior in that mode. The initializer
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

The active unit's first SQL also captures its actual physical session mode and requires an
`utf8mb4` client/connection charset and unconverted or `utf8mb4` results. The shipped
Connector/J parameter encoder must actually be UTF-8; changing only server variables cannot
prove this. Alternate drivers require an explicit verified adapter before activation. It adds `STRICT_ALL_TABLES` for that unit, including when
legacy configuration uses an empty `sql_mode` and `jdbcCompliantTruncation=false`. The captured
mode survives removal of the thread marker until actual commit/rollback, then restores before
the connection can return to DBCP. A restoration failure aborts the physical connection; an error
after commit can require acknowledgement reconciliation. Ordinary legacy transactions retain
their session settings. This checks the actual session, rather than assuming character-count
validation or a JDBC truncation option prevents database loss.

Clinical writes require both the private active import-unit marker and the current database owner
(token, generation, physical advisory connection). `Owner.read(Supplier)` gives bounded read-only
coordinator access; it cannot admit clinical writes or escape into another scope. Administrative
ledger and source-derived login tables have an exact SQL allowlist. Joins/subqueries with clinical
tables still require admission. Vendor unwrap, multiple SQL statements, callable routines,
unreviewed functions and implicit-commit SQL are refused while the guard is installed.

SQL keyword and allowlist-token classification folds ASCII letters only. The classifier must not turn a Unicode lookalike into `VERSION`, a known built-in function, or an allowlisted ledger table. Function names pass an explicit ASCII boundary before the shared reporting helper, whose own case conversion would otherwise reintroduce the ambiguity. This restriction applies to recognized security tokens: Unicode values and ordinary Unicode identifiers still go through the existing complete-statement parser and admission rules. The original SQL is executed unchanged after admission; it is never rewritten into the classification spelling.

`Owner.close()` stops new unit admission, drains old units and releases the advisory connection.
It retains durable maintenance by default, including after failure or process/connection loss.
Only an explicit `allowReopen()` after successful clinical reconciliation and backlog/care-owner
policy can reopen, and a remaining chart hold vetoes reopening. A successor first drains old units
and advances the owner generation. The HTTP filter keeps login, static assets and authenticated
import/recovery routes available; clinical chart/API/print/download routes receive a safe 503.
It does not grant import privileges.

## Native target catalogue

Before units execute, `CdsImportTargetSafetyService.verify(owner)` resolves the canonical
`CdsTargetReceiptService` writer tables, document/note links, audit log, native archives and all
CDS metadata/typed laboratory tables. Missing base tables or a non-InnoDB engine refuse import.
Zero-row reads on the dedicated owner connection pin metadata for the window; engines are
re-read after pinning. A current locking hold query is used on close, so the long metadata
transaction cannot reopen from an old patient snapshot. The physical SQL guard also requires
this owner-specific verified catalogue before an active unit can execute SQL.

Any trigger on those write targets requires an explicit server-owned approval manifest;
unknown definitions refuse import. The account must first prove direct global/schema or
per-target TRIGGER visibility. Without that visibility, an empty information-schema result can
hide existing definitions and is refused with `IMPORT_TARGET_TRIGGER_VISIBILITY_REQUIRED`. Set `cds.import.approvedTriggerManifest` to a normalized
absolute JSON file with this shape:

```json
{"format":1,"approvedTriggers":{"database.trigger_name":"64-lowercase-hex-sha256"}}
```

The file and every ancestor must be root-owned, have no symlinks, and deny group/other writes.
Keep it outside staging/upload roots and mount it read-only for the application. The SHA-256
covers twelve fields, in order: trigger schema, name, event object table, event manipulation,
action timing, action statement, SQL mode, definer, client character set, connection collation,
database collation and action order. Encode each nullable field as UTF-8 preceded by its signed
four-byte big-endian byte length (`-1` for null). Identity, body, stored SQL mode, security context
and ordering changes therefore require renewed approval. Errors expose only fixed safe codes.

Approval is an operational review of the exact definition, including all transitive writes,
transactional engines, stored SQL mode, archive capacities and disclosure/restriction effects.
The digest does not infer that semantic review. Do not approve a definition merely to clear a
refusal. External privileged clients and schema changes after loss of the dedicated connection
still require the operational deployment barrier described below.

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
`-Dcds.test.properties=/path/to/protected-jdbc.properties`. For an isolated run using already
prepared application/test dependencies (including JUnit ConsoleLauncher), use:

```sh
python3 scripts/ci/run-cds-maintenance-mariadb.py \
  --classpath-file /protected/test-classpath.txt \
  --jdbc-properties /protected/jdbc.properties \
  --output /tmp/cds-maintenance-check
```

The runner compiles the current guard sources into a separate directory and requires all twenty
physical tests to execute without skips, recording logs and XML reports. The tier uses the complete real schema,
two independent datasource/node instances, ordinary JDBC and actual Hibernate transactions,
autocommit result lifetime, advisory-connection loss and successor drain, bounded coordinator
reads, fail-closed release, held-chart reopening veto, disabled-guard behavior, lossy JDBC encoder refusal, strict mode
restoration on commit/rollback, nontransactional/unknown-trigger refusal and metadata DDL drain. A skipped tier
is not evidence of exclusion. The test modifies only its synthetic probe and ledger fixture rows.
