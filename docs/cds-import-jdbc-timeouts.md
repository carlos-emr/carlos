# CDS JDBC connection deadlines

Controlled CDS connections use the shipped MariaDB Connector/J transport settings before opening a socket.
Missing `connectTimeout` receives **5000 ms** and missing `socketTimeout` receives **60000 ms**.
Explicit positive operator values are retained up to **30000 ms** for connect and **300000 ms**
for socket reads. An explicit zero, negative, nonnumeric or over-ceiling value is refused with
`CDS_JDBC_TIMEOUT_CONFIGURATION_INVALID` / SQLState `08001`; it is never silently accepted as
an unlimited operation. The policy uses the driver's actual query/property/per-host precedence: explicit connection
properties override URL query options, while single-host descriptors retain the driver's more
specific host settings. Driver, URL, credentials, SSL, charset and other properties
are copied without modifying the original factory or shared configuration.

The scope is deliberate:

- Ordinary WAR **pooled** connections retain their previous settings when
  `cds.import.maintenanceGuardEnabled=false` (the existing default).
- Opting into `cds.import.maintenanceGuardEnabled=true` also opts the **whole primary WAR pool**
  into finite network settings before its first Flyway/JPA connection. This includes ordinary
  requests using that pool; operators with long database queries must choose suitable finite
  values within the limits. This is not a per-request timeout or a late pool reconfiguration.
- The dedicated one-shot CLI datasource always uses the finite policy, including startup,
  schema validation and authorization before run execution.
- Dedicated nonpooled coordinator, maintenance and ordinary filesystem-admission connections
  always use a separately copied bounded factory. Ordinary pool flags are not changed by these calls.

Controlled connections require one primary-writer endpoint: a MariaDB Connector/J URL with no
high-availability mode (`HaMode.NONE`).
Multi-host failover, load-balance, replication and DNS-SRV URLs are refused before connecting or
resolving SRV targets with `CDS_JDBC_SINGLE_HOST_REQUIRED`. A secondary host cannot retain an
unbounded per-host override. The protocol does not claim cross-server advisory-lock or HA failover
support. Default-off ordinary pools are not subjected to this controlled-connection topology rule.

These are driver TCP-connect and individual socket-read limits, including the initial server
handshake: they detect a server or connection that has gone silent, not a slow statement (see
[statement deadlines are waits](#statement-deadlines-are-waits)). They complement the 3-second
lock probes and the maintenance drain bound described there, and the stall window; they do not
claim a single wall-clock deadline for the entire import. Endpoint name resolution remains a
responsibility of the deployment's resolver. No global `DriverManager` login timeout, generic
future executor or pool abandonment setting is introduced. Transport failure must retain the
existing unknown-commit/hold/reconciliation rules.

`CdsJdbcConnectionTimeoutsUnitTest` checks the actual Connector/J property merge, opt-out and
opt-in factories, explicit invalid values, both orders of the former secondary-host bypass, and
four real loopback silent-handshake paths (maintenance, ordinary lease, opted-in web pool and
focused CLI). Each test peer retires within three seconds even if a timeout regresses. The HRM
MariaDB tier additionally reads back the actual 60000 ms network timeout on pooled/nonpooled
connections while proving abandoned-pool reclamation cannot release a filesystem admission lease.

## Statement deadlines are waits

A slow but working database never fails an import. No import transaction carries a clock: the
ledger's transactions (every unit, checkpoint and status read), the browser upload's, the
synthetic course reads, the program-context reads and the source-field pages all run as long as
the database takes. They used to end at 120, 30, 30, 30 and 15 seconds respectively.

A read that can legitimately wait (a metadata or row lock held by another session, a busy
server) is sent with a server-side deadline instead, the JDBC query timeout that Connector/J sends
as `max_statement_time`: first 30 seconds. When the server ends the statement at that deadline,
the same statement is sent again on the same connection, inside the same transaction, with twice
the wait, and again until it is answered (`CdsImportStatementWaits`). This applies to:

- the native receipt reads of a unit (its target table's columns and each row digest), formerly
  failed at 30 seconds;
- the run's binding and report-entry reads, formerly failed at 120 seconds;
- the target-safety catalogue reads on the maintenance owner's connection (tables, metadata pins,
  triggers and trigger privileges), formerly failed at 10 seconds;
- the maintenance release's two locking reads, formerly failed at 30 seconds.

The applied-copy read the exporter makes through the import's run table waits the exporter's way
(`CdsExportStatementWaitReadSupport`), inside the exporter's snapshot.

Sending a statement again is sound because these are reads, including locking reads: a statement
the server ends at its own deadline is rolled back by itself, changed nothing and acquired no lock,
and the transaction with every lock it already holds is as it was. A statement ended for any other
reason is passed on and never sent again: a lock-wait timeout (the server's own
`innodb_lock_wait_timeout`) or a deadlock can roll back the whole transaction, and a lost connection
loses it. Such a failure fails its unit loudly under the unit's existing code, and the run is
resumed. Writes carry no deadline of ours.

A wait ends only with what ends its scope: a cancel (`IMPORT_CANCELLED`), the scope's stall window
(`IMPORT_WORKER_STALLED`, or `IMPORT_REPORT_GENERATION_STALLED` for report generation), since a
wait is never a checkpoint, and the loss of the connection. Because a live server answers every
statement within its deadline, with rows or with the timeout, the connection's socket timeout is
raised to the deadline plus 30 seconds while a statement waits: for a transaction's pooled
connection until the transaction completes, when it is restored before the connection returns to
its pool, and for the maintenance owner's dedicated connection until the statement is answered. A
connection held outside a transaction is never changed, since nothing would put it back.

Two kinds of short bound remain, deliberately:

- **3-second lock probes.** Each asks the server a question it answers at once, from its lock
  table or session state, without waiting on a lock or reading a row: `GET_LOCK(name, 0)` (which
  returns immediately whether or not it got the lock), `IS_USED_LOCK`, `RELEASE_LOCK`,
  `CONNECTION_ID()`, `DATABASE()`, `@@autocommit` and `SET SESSION sql_mode`, on the coordinator,
  maintenance and admission-guard connections. The coordinator's control read (the run's cancel
  flag and owner epoch by primary key, with the advisory-lock check) and the admission guard's
  gate are read the same way: the control read is a non-locking autocommit read, and the gate's
  shared lock on the admission row can wait only behind a maintenance owner's exclusive lock,
  which no other session holds while an import owns maintenance. The maintenance owner's own
  recovery and release readbacks are the same kind of read. The clinical-access status read used
  by the login and status filters is not part of a transfer at all; it fails closed quickly so
  that every clinical request is not held behind a stalled status query. Three seconds of silence
  on any of these means the server or the session is unreachable, which is a genuine failure.
- **The maintenance drain** (`cds.import.drainTimeoutSeconds`, 30 seconds by default). Entering
  maintenance takes the admission row exclusively, which waits for every clinical transaction
  already admitted to finish while new clinical work is refused. The bound is how long the clinic
  may be held out waiting for that, not how slow the database may be: when it passes, the attempt
  ends with `MAINTENANCE_ACQUISITION_FAILED` before any unit, the clinic is let back in, and the
  run is resumed later.

`CdsImportStatementWaitsUnitTest` sends a read again with doubling waits, passes every other
failure on unsent, ends a wait at the scope's stall window or a cancel, and raises and restores
the socket timeout of a transaction's and of a dedicated connection.

## Server-prepared binary reads

Receipt capture and verification of rows with FLOAT/DOUBLE columns, and the exporter's exact
numeric reads, use `CdsBinaryReads`: a server-prepared statement whose binary result keeps exact
IEEE bits. Connector/J applies `setQueryTimeout` to such a statement only as a
`SET STATEMENT max_statement_time=N FOR` prefix on its first prepare, and the statement is
already prepared (its parameter metadata is read) before a caller can set a timeout, so the
timeout used to be a silent no-op (IMP-2): a read blocked on a metadata lock waited up to the
server's `lock_wait_timeout`. The reviewed statement now enforces the caller's timeout on the
client: a daemon timer cancels the execution at the deadline (Connector/J sends `KILL QUERY`
on a separate connection) and the read fails with `SQLTimeoutException`
(`CDS_BINARY_READ_TIMEOUT`), which a receipt read then sends again with twice the wait, like any
other statement ended at its deadline. A read cancelled at its deadline is never treated as
complete, even when the server returns normally (an interrupted `SLEEP()` returns 1). No session variable
changes. `CdsBinaryReadsMariaDbIntegrationTest` times out a `SLEEP(30)` through it after one
second and reuses the connection afterwards.
