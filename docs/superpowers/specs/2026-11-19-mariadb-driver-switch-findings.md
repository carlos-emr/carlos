<!-- SPDX-License-Identifier: GPL-2.0-or-later -->

# MariaDB Connector/J switch: review findings and required corrections

Date: 2026-11-19. Scope: everything that changed for CARLOS when #5460
(`710fb1ac`) replaced MySQL Connector/J 9.6.0 with MariaDB Connector/J 3.5.11
behind `MariaDbCompatibilityDriver` / `MariaDbJdbcCompatibility`.

This is the specification for
`docs/superpowers/plans/2026-11-19-mariadb-driver-switch-remediation.md`.

## 1. How this was established

| Evidence | What it covers |
|---|---|
| Live differential probe (`scripts/jdbc-connector-probe/`) | The same 278,142 observations run through Connector/J 9.6.0 and through the adapter, on MariaDB **11.8.8** (the CI server version) with the packaged server settings (`sql_mode=""`, `utf8mb4_general_ci`, 32K pages), JVM and server in `America/Toronto`, packaged URL options. Repeated for the adapter as it is on `main` (`51520621`), on `release/2026.08` (`c0b8708a`) and in PR #6113 (`94d42b24`); streaming and server-prepared variants; and once on MariaDB 10.11.14. |
| Driver sources | `mariadb-java-client-3.5.11-sources.jar` and `mysql-connector-j-9.6.0-sources.jar` from Maven Central, read for every difference the probe showed. |
| Code sweeps | JSP scriptlets (1,066 files); raw JDBC in Java (63 `executeQuery` lines, 67 `prepareStatement`/`prepareCall` sites, 15 `JdbcTemplate` query sites); every JPA attribute against the physical schema contracts (5,907 mapped attributes); every place SQL that is not a compile-time constant reaches a query API (287 `createNativeQuery` sites, all 88 non-literal ones read; 1,745 `createQuery` sites triaged by script); JDBC configuration and documentation in `carlos`, `carlos-ctl`, `carlos-podman` and `drugref2026`. Swept on `main` (`51520621`); every site named below was re-read on `release/2026.08`. |
| Trial integration | `release/2026.08` at `8872cdd3` merged with the heads of PR #6113, #6238 and #6295 in a scratch clone. |
| Small live checks | The session statement in section 8 through the adapter on MariaDB 11.8.8 (both URL schemes, read-only account, operator overrides); Spring 7.0.10's default `JdbcTemplate` exception translator on both shapes of error 1205. |
| Upstream | MariaDB CONJ-1306 (fixed only in Connector/J 4.0.0), MDEV-25897, the Connector/J 4.0 announcement, MySQL Connector/J `netTimeoutForStreamingResults`. |

Not done: no run through a deployed WAR or browser; no load test; library
internals of Spring, Hibernate and JasperReports were not read (that
`getResultList()` and a Jasper fill end in `executeQuery`, and that Spring
makes the connection read-only before a read-only transaction begins, are
inferred); the DrugRef adapter copy was compared as source, not probed.

## 2. Where the fixes belong

- The adapter exists on `main`, `develop` and `release/2026.08`. Driver fixes
  are landing on **`release/2026.08`**, the oldest affected line under
  `docs/release-process.md`. `main` has three commits the release branch lacks,
  so the work branch must start from the release branch, not from `main`.
  Already merged there: #6275 (DATE writes, #5769), #6247 (#6089), #6282
  (devcontainer DrugRef, #5862), #6308 (#5924), #6312 (#5971/#5973).
- **Five of the eight issues already have open, unmerged pull requests.** The
  work below must build on them, not repeat them. In a trial merge onto the
  release head, #6113 and #6238 merge cleanly; #6295 conflicts in one file,
  `scripts/ci/verify-mariadb-dao-reports.py` (both edit the `REQUIRED` table).

| Issue | State on 2026-11-19 |
|---|---|
| #5968 `getString` on DOUBLE/FLOAT/BIT | PR #6113 (open, 58 commits). Also restores BIT objects/metadata, unsigned `tinyint(1)`, floating `getBigDecimal`, float range. |
| #5969 `yes`/`no` option values | PR #6113. |
| #5922 binary BLOB in report readers | PR #6238 (open). Adds `ReportJdbcTextReader`; fixes `ConfiguredReportQuery` and `ReportResultBudget` only. |
| #6088 abandoned-connection timeout | PR #6295 (open). `db_remove_abandoned_timeout` 15 to 360. Also edits `.github/workflows/maven-project.yml` to add its test class to the MariaDB lane. |
| #5770 bare "Invalid database connection configuration" | Fixed only inside draft PR #6133, which is 298 commits behind the release branch. |
| #5863 `AuditLogManager` needs `?` in `db_name` | Same draft PR #6133 (rewrites the purge as `AuditLogPurgeService`). Still present at `AuditLogManager.java:67` on the release branch. |
| #5768 stale documentation | Closed on 2026-11-19 as consolidated into umbrella #3053; the work is still outstanding there. No pull request. Part of it is under `.github/`, which CLAUDE.md reserves for maintainers. |
| #5744 DrugRef pinned to an unmerged commit | No pull request. `debian/drugref.pin` is `93775a04` (drugref2026 PR #18, open); PR #6113 moves it to `703e3f08` (drugref2026 PR #19, open, stacked on #18). Neither commit is reachable from drugref2026 `master` (`57c8642`). |

## 3. New findings

Each was observed in the probe unless marked otherwise; all are still present
with PR #6113's adapter.

### N1. Write statements sent through a query API are now executed (security, defence in depth)

Connector/J refused, in the client and before sending anything, every statement
that cannot produce a result set when it was run through
`Statement.executeQuery(sql)` or `PreparedStatement.executeQuery()`. JPA
`getResultList()`/`getSingleResult()`/`scroll()` on a native query, Spring
`JdbcTemplate.query*` and a JasperReports fill with a connection all end in
`executeQuery`, so they had the same protection. MariaDB Connector/J 3.5.11
sends the statement; the server executes it and the caller receives an empty
result.

- Oracle (`src/test/resources/jdbc/connector-j-9.6.0-executequery-oracle.tsv`):
  of 176 recorded statements Connector/J refused 79; **25 of those change the
  database** when sent through the adapter today: `INSERT`, `UPDATE`, `DELETE`,
  `REPLACE`, `INSERT … SELECT`, `INSERT/DELETE … RETURNING`, `CREATE`, `DROP`,
  `TRUNCATE`, `ALTER`, `RENAME`, `CREATE OR REPLACE VIEW`,
  `SET STATEMENT … FOR UPDATE`, `BEGIN NOT ATOMIC … END`, including forms hidden
  behind comments (`/* c */ INSERT`, `-- c⏎UPDATE`, `/*! INSERT … */`,
  `/*M! INSERT … */`). `SET`, `USE`, `BEGIN`/`COMMIT`, `LOCK TABLES`,
  `SET PASSWORD`, `PREPARE` also run. `SELECT … INTO OUTFILE` and
  `SELECT … INTO @variable` were refused by Connector/J too and are now sent.
- Upstream: CONJ-1306 "PreparedStatement.executeQuery allows DML statements",
  fixed in 4.0.0 only. MariaDB does not pre-parse, so even 4.0 raises the
  exception after the statement has run.
- Connector/J's refusal was never a complete read-only guarantee. These went
  through under Connector/J too and changed data in the probe:
  `ANALYZE UPDATE|DELETE|INSERT …`, `EXECUTE IMMEDIATE '…'`, `CALL` of a writing
  procedure, `SELECT` of a writing stored function.
- Exposure (section 5 lists every sink). The features that run SQL they did not
  write fall into three groups:
  - Own statement check, independent of the driver: Query by Example, Report by
    Template, clinical and configured reports, eForm database fields, document
    transfer. Their validators require the text to start with `select`.
  - No statement check of their own; the driver's refusal was the only one:
    **dashboard indicator and drill-down queries**
    (`AbstractQueryHandler`, text from `indicatorTemplate` rows, which arrive by
    import or direct database writes) and **Jasper templates filled with a
    connection** (administrator-installed files). The dashboard handler runs in
    a read-only transaction, which the server enforces when the connection is
    made read-only before the transaction starts (probe: error 1792, row
    unchanged); the Jasper paths have no such protection.
  - A source-literal `select` prefix and no statement validator: the
    Demographic Report Tool, saved demographic queries, prevention reports and
    the demographic search status fragment. The lost layer here is the refusal
    of `SELECT … INTO`.
- No shipped code depends on the new behaviour: every write literal passed to a
  query API ends in `executeUpdate`/`execute` (17 sites checked), the only
  non-`SELECT` literal given to `executeQuery` is `SHOW GRANTS`, nothing uses
  `RETURNING`, and both configured Hibernate dialects obtain generated keys
  through `getGeneratedKeys`.

Correction: restore the refusal in the adapter for `executeQuery`, with the
oracle as its test. Rule in section 6.

### N2. Query by Example fails on binary BLOB columns (same defect as #5922, different reader)

`RptResultStruct.appendCell` (`report/data/RptResultStruct.java`, the renderer
behind `getStructureWithCount`, which Query by Example uses) calls
`getCharacterStream` for `BINARY`, `VARBINARY`, `LONGVARBINARY` and `BLOB`
columns. MariaDB Connector/J throws
`SQLDataException: Data type BLOB cannot be decoded as Reader` for a binary
BLOB; Connector/J returned the text. PR #6238 does not touch this class.
Reportable tables with such columns include `reportByExamples.query` and
`reportByExamplesFavorite.query` (Query by Example's own history), `formBPMH`,
`messagetbl`, `client_image`, `document_storage`, `report_letters`,
`ResourceStorage`, `RemoteDataLog`, `DigitalSignature`.

Correction: read the cell through `ReportJdbcTextReader.open` (from PR #6238).

### N3. Every session now runs with `sql_mode=IGNORE_SPACE`

MariaDB Connector/J always sends the `CLIENT_IGNORE_SPACE` capability
(`ConnectionHelper.java:197`); the server then adds `IGNORE_SPACE` to the
session mode (MDEV-25897 describes the same). Connector/J did not. Probe:
`@@session.sql_mode` is `''` under Connector/J and `IGNORE_SPACE` under the
adapter, on both URL schemes.

Effects: `COUNT (*)` with a space now parses; a table or index named like a
built-in function and followed by `(` no longer parses; routines created through
JDBC (seven Flyway migrations create helper procedures) record `IGNORE_SPACE` as
their mode. No shipped SQL depends on either behaviour (searched migrations,
`apconfig.xml`, clinical report XML, indicator templates, Java literals), and no
shipped table or index has such a name. Saved report and Query by Example text
written before the switch was written for the mode without it. CDS import reads
the session mode, adds `STRICT_ALL_TABLES` and restores what it read, so it is
unaffected either way.

Correction: remove `IGNORE_SPACE` from the session when the connection opens,
unless the server's own default has it or the operator sets `sql_mode`
themselves. Statement in section 8.

### N4. Streaming results no longer raise `net_write_timeout`

Connector/J set `net_write_timeout=600` while a streaming result was open
(`netTimeoutForStreamingResults`). Probe: `600` under Connector/J, `60` under
the adapter. A consumer that stops reading a streamed result for a minute (a
long garbage-collection pause or slow per-row work in a report that may now run
for 300 seconds) has its connection dropped by the server. Streaming sites:
`LegacyJdbcQuery.getBoundedReportResultSet`,
`OscarAppointmentDaoImpl.streamPatientAppointments`,
`CdsImportTargetSafetyService`.

Correction: set `net_write_timeout=600` for adapter sessions (section 8). It is
set for the whole session rather than around each streamed result; a buffered
result is read at once, so the value only matters while streaming.

### N5. `getObject` on a signed SMALLINT returns `Short`, not `Integer`

Probe: `java.lang.Integer:5` under Connector/J, `java.lang.Short:5` under the
adapter and under PR #6113; `getColumnClassName` likewise. Columns:
`caisi_form.status`, `survey.status`, and any `MAX()`/expression over them. No
main-code consumer casts such a value (the one untyped `getObject` cast,
`RxSubmissionReceiptService.java:39`, is on an INT column), so this is a
contract gap for report tools and future code.

Correction: return `Integer` and report `java.lang.Integer`, as PR #6113 already
does for the other integer cases.

### N6. A lock-wait timeout is no longer a transaction-rollback exception

Error 1205 arrived from Connector/J as `SQLTransactionRollbackException` with
SQLState `40001`; it now arrives as plain `SQLException`, SQLState `HY000`
(also for `FOR UPDATE NOWAIT`). Hibernate and the raw checks in
`DemographicWriteConflict`, `LabDisplaySettings2Action` and
`CdsExportStatementWaitReadSupport` test the vendor code and are unaffected.
Spring's `JdbcTemplate` translation is not. Run against Spring 7.0.10's default
translator (`SQLExceptionSubclassTranslator`; CARLOS installs no other): the
`HY000` shape is not translated and becomes `UncategorizedSQLException`; the
`40001` shape becomes `CannotAcquireLockException`. So
`RxFavoriteGroups2Action`, which answers `ConcurrencyFailureException` with
HTTP 409 "retry", answers a lock-wait timeout on its `SELECT … FOR UPDATE` with
an error page instead.

Correction: give error 1205 back its `40001` state and exception class in the
adapter. A `BatchUpdateException` is left as it is, so its update counts
survive.

### N7. `OscarAppointmentDaoImpl` still tests for the product name "MySQL"

`OscarAppointmentDaoImpl.streamPatientAppointments` (lines 596-599 on the
release branch) asks for the streaming sentinel only when the product name is
`"MySQL"`. The adapter reports `MariaDB`, so the method now takes the
"other driver" branch (fetch size 500, which MariaDB also streams). The result
is correct by accident and the comment is wrong. `LegacyJdbcQuery` and
`OscarLogDaoImpl` already accept both names.

Correction: accept both names.

### N8. DrugRef's copy of the adapter is behind the CARLOS adapter (source comparison)

DrugRef carries its own copy of the three adapter classes. The copy in
drugref2026 PR #19 (`703e3f08`, the commit PR #6113 pins) is identical to the
CARLOS adapter for `MariaDbJdbcCompatibility` and `MariaDbPreferredTlsPlugin`,
but its `MariaDbCompatibilityDriver` lacks the DATE-write correction that #6275
added to the release branch (`setDate`, `setObject`/`updateObject` with a
`java.sql.Date`, `updateDate`). DrugRef maps three `TemporalType.DATE`
attributes (`CdDrugProduct`, `CdDrugStatus`, `CdInactiveProducts`) and connects
with `connectionTimeZone=UTC` unless told otherwise, so a DrugRef host whose
JVM zone is east of UTC stores those dates one day early. Canadian zones are
west of UTC and are not affected. Nothing checks that the two copies match;
`debian/check-drugref-jdbc.py` checks only that the classes exist.

Correction: mirror the final CARLOS adapter into drugref2026 and add a build
check that the three sources are identical apart from the package name.

## 4. Differences confirmed and left as they are

No consumer was found for any of these. They are to be listed in
`docs/mariadb-connector-migration.md`. The deterministic ones are pinned by one
physical test, so that a later connector upgrade cannot change them unnoticed;
the rest (marked *doc only*) need a second session, a dropped connection or an
oversized packet and are documented without a test.

| Difference (Connector/J to adapter) | Why no change |
|---|---|
| Narrowing reads out of range (`getInt` on a BIGINT above 2^31, `getByte`/`getShort`, `getLong` on a large unsigned BIGINT) wrapped silently with `jdbcCompliantTruncation=false`; they now throw `SQLDataException`. | A refusal is safer than a wrapped clinical number. Entity integer attributes sit on columns of the same or a narrower width. |
| `getLong` on text such as `12.5`, `1.0`, `1e3` returned the truncated number; it now throws. `getInt` still truncates in both. | Integer attributes on character columns (`ProgramAccess.access_type_id`, `HRMDocumentToDemographic.*`, `FaxClientLog.*`, `FaxConfig.queue`, `LstOrgcd.activeyn`, `CustomFilter.programId`) hold integer text. |
| `getBytes`, `getBlob`, `getClob` on numeric and temporal columns, and `getClob`/numeric getters on binary BLOB, returned the text; they now throw. | No raw caller. No `@Lob String` attribute is mapped to a binary column (the six String-on-BLOB attributes have no `@Lob`, so Hibernate uses `getString`, which is unchanged). |
| `getDate` on a TIME column returned 1970-01-01; it now throws. TIME values outside 00:00-24:00 threw; they now convert. Negative TIME text prints `-01:00:00` instead of `-1:00:00`. | TIME columns are mapped `@Temporal(TIME)` or String and hold times of day. |
| Temporal getters on character columns: zero-date text followed `zeroDateTimeBehavior`; invalid text threw. MariaDB returns null for `0000-00-00` and rolls invalid dates over. | No entity or raw reader applies a temporal getter to a character column. |
| Result metadata: TEXT/MEDIUMTEXT report `VARCHAR` (was `LONGVARCHAR`), BLOB/MEDIUMBLOB `VARBINARY` (was `LONGVARBINARY`); `INT` is named `INTEGER`; integer precision follows the declared width; FLOAT/DOUBLE scale 31; `isSigned`/`isCaseSensitive` differ; LONGBLOB display size is -1. | Report readers stream both text codes; `FrmRecordHelp` tests only `TINYINT*`, `bit`, `date`, `timestamp`; the CDS receipt digests are compared within one build only. |
| `getGeneratedKeys` after a multi-row `INSERT` returns one key (was one per row); key column label is `insert_id` (was `GENERATED_KEY`). | Callers insert one row and read by index. |
| A failing row in a JDBC batch fails the whole batch (`[-3,-3,-3]`, nothing inserted); Connector/J continued (`[1,-3,1]`). | No batch in main code. |
| A second statement on a connection that has a streamed result open no longer fails ("Streaming result set … is still active"); MariaDB Connector/J first reads the rest of the streamed result into memory. | Code that ran under Connector/J cannot contain the pattern. The three streaming consumers run no SQL per row. New streaming code must not either. |
| `commit()`/`rollback()` in autocommit mode no longer throw. `createStatement()` on a closed connection succeeds and the failure comes at execution. An unknown column label is a plain `SQLException` (was `SQLSyntaxErrorException` `42S22`). | More lenient or equivalent; no caller tests these classes or states. |
| *Doc only.* `setReadOnly(true)` after a transaction has started does not protect that transaction (server-side from the next one). Connector/J also checked in the client. | Query by Example sets it on a fresh connection; Spring sets it before the transaction begins. |
| *Doc only.* `Statement.cancel()` and `KILL QUERY` surface as `SQLTimeoutException` (70100/1317), the class a timeout uses. | Report code treats both as "stopped"; CDS waits re-send. |
| Lost connection (*doc only*): `SQLNonTransientConnectionException` `08000` (was `SQLRecoverableException` `08S01`). Missing parameter: `SQLTransientConnectionException` `07004`. Strict-mode data errors 1366/1365: `SQLSyntaxErrorException`. | All state checks use the `08` prefix; the CDS and Rx code that inspects classes was written against this driver. |
| *Doc only.* A packet above `max_allowed_packet` kills the connection ("Socket error") instead of a clean refusal. | Packaged servers set 1G. |
| `setObject` with `Calendar`, or with an explicit target type the value does not match (`String` as `TIMESTAMP`, anything as `OTHER`), is refused. `Character`, `StringBuilder`, enums were Java-serialized by Connector/J and are now refused. `setTime` into a character column writes `23:30:00.000`. | Raw JDBC binds only String, Integer, Long, `java.sql.Date`, Timestamp; Hibernate binds through typed setters. |
| *Doc only.* YEAR columns: the adapter's temporal path throws. | The schema has no YEAR column. |
| `character_set_results` is `utf8mb4` (was NULL); `DatabaseMetaData` product name, version, user name, catalog handling, `getColumns` details. | Schema is utf8mb4; main code calls only `getDatabaseProductName`. |

## 5. SQL that is not written in the source: sinks and their own checks

"Validator A" is `LegacyJdbcQuery.validateReportSelectQuery` (must start with
the word `select`; refuses comments, a second statement, backslashes in
literals, `INTO OUTFILE|DUMPFILE`, `LOAD_FILE`, `LOAD DATA`). "Validator B" is
`validateSafeSelectQuery` (A, plus `UNION` and write keywords anywhere).
Paths are under `src/main/java/io/github/carlos_emr/carlos/`. Read on `main`;
the validators and execution points were re-read on the release branch.

| Feature | Execution point | SQL text comes from | Own check before the driver |
|---|---|---|---|
| Query by Example | `report/data/RptByExampleData` `executeQuery` | Request | `QueryByExampleSqlValidator` (one parsed `SELECT`, function allow-list, restricted tables) then validator B; read-only account `carlos_report`; `setReadOnly(true)`; row and time limits. |
| Report by Template query | `report/reportByTemplate/SQLReporter` via `LegacyJdbcQuery` | `reportTemplates` row (`_report` write) | Validator A at save and again at run. |
| Report by Template parameter query | `ReportManager` via `AbstractDaoImpl` `getResultList` | Same row | Validator A at save and at run. |
| Clinical reports | `report/ClinicalReports` via `ConfiguredReportQuery` | Shipped XML or configured file | Validator A; row, size and time limits. |
| Configured form reports, CSV download | `report/data/RptReportCreator` via `ConfiguredReportQuery` | Source-literal `select`, `reportConfig` rows, request filters | Validator A on the assembled text; identifier checks. |
| eForm database fields | `commn/dao/EFormValueDaoImpl` `executeQuery` | Shipped `apconfig.xml` or configured file | `EFormSqlSafety` (validator A, no unresolved `${`); read-only transaction. |
| Document transfer export | `commn/dao/DocumentTransferDao` `executeQuery` | Shipped `DocXferConfig.xml` | Validator A. |
| Legacy form records | `form/FrmRecordHelp`, `form/Frm*Record`, `login/DBHelp` | Source literals; form table name from the registry | Validator B in `saveFormRecord`, validator A in `DBHelp`; the remaining readers pass literals to an unvalidated helper. |
| **Dashboard indicators, drill-down, export** | `dashboard/handler/AbstractQueryHandler` `getResultList` | `indicatorTemplate` rows (import or direct database write; no upload screen) | **None on the statement.** Comment stripping only; class-level read-only transaction. |
| **Jasper templates filled with a connection** | `demographic/DemographicLabelPdf`, `commn/web/PrintReferralLabel2Action`, `report/ConfiguredJasperTemplateRenderer`, `OscarDocumentCreator` | Template files in the Tomcat user's home directory or at configured paths; shipped templates otherwise | **None.** Documented in code as administrator-installed arbitrary SQL. |
| Demographic Report Tool, saved demographic queries, prevention reports | `commn/dao/forms/FormsDao` `getResultList` | Source-literal `select` plus generated clauses | Source-literal prefix; no statement validator. |
| Demographic search | `commn/dao/DemographicDaoImpl` | Source literals plus the `inactive_statuses` property | Source-literal prefix. |
| Lookup tables, encounter-form tables, eForm report tool, CDS import/export, provider name lookup | Several DAOs | Identifiers only (registry rows, shipped catalogues, `information_schema`) | Identifier validators; no free SQL text. |

## 6. Rule for the `executeQuery` guard

The guard applies to `Statement.executeQuery(String)` and to
`executeQuery()` on a `PreparedStatement` or `CallableStatement`, classifying
the text given to `prepareStatement`/`prepareCall`. `execute`, `executeUpdate`
and `executeBatch` are not affected.

A statement is *sent* when, after skipping leading whitespace, non-executable
comments (`/* … */`, `-- ` to end of line, `#` to end of line), the opener of an
executable comment (`/*!` or `/*M!` and its version digits), a JDBC call escape
opener (`{`, optional `?=`) and opening parentheses, its first word is one of
`SELECT`, `SHOW`, `DESCRIBE`, `DESC`, `EXPLAIN`, `ANALYZE`, `CHECK`, `CHECKSUM`,
`OPTIMIZE`, `REPAIR`, `HELP`, `TABLE`, `VALUES`, `WITH`, `CALL`, `EXECUTE`, or is
`XA` followed by `RECOVER`. A word is a run of ASCII letters not followed by an
identifier character. Everything else, including a null, empty or comment-only
statement, is *refused*.

Two further refusals:

- `SELECT`, `TABLE`, `VALUES` and `WITH` statements that contain the bare word
  `INTO` outside quotes, backticks and comments (not preceded by `@` or `.`).
- `WITH` statements that contain, at parenthesis depth 0 and outside quotes and
  comments, `DELETE`; `INSERT` or `REPLACE` not followed by `(`; or `UPDATE` not
  preceded by `FOR`.

This matches Connector/J on 170 of the 176 recorded statements. The six
differences are deliberate and none lets a recorded write through:
`WITH … SELECT … INTO` is refused (Connector/J sent it); `;SELECT 1` and
`SELECTED` are refused (both are server syntax errors); `SELECT 1 AS into_x`,
`SELECT 1 AS x_into` and `/*M! SELECT 1 */` are sent (Connector/J refused valid
reads). The reference model is `scripts/jdbc-connector-probe/guard_reference.py`.

Like Connector/J, the rule looks at the first statement only. Multiple
statements in one string need `allowMultiQueries=true`, which no CARLOS
configuration sets.

The refusal is a `java.sql.SQLException` with SQLState `S1009` and the message
`executeQuery() cannot run a statement that does not produce a result set`. It
never contains the statement text.

## 7. Verified unchanged

- Legacy form save, load and update through updatable result sets
  (`FrmRecordHelp` semantics) over all 52 `form*` tables: stored rows are
  byte-identical under both drivers.
- Every entity attribute whose Java type family differs from its column's
  (1,166 of 5,907): String on integer, decimal, DATE, TIME and BLOB columns;
  boolean on integer and character columns; integer on `tinyint(1)` (1,089,
  mostly `FormRourke2009`); `Date` on DATE/DATETIME in either direction. All
  use getter/column pairs on which the two drivers agree.
- Typed parameter setters, `WHERE` comparisons through parameters, column label
  lookup, client-side placeholder parsing, update counts, `setMaxRows`,
  `setQueryTimeout` on 75 statement kinds, the session collation
  (`utf8mb4_general_ci`) and time zone (`SYSTEM`).
- With `jdbcCompliantTruncation` left at its default, both drivers add
  `STRICT_TRANS_TABLES` to the session mode.
- A write on a connection made read-only before its transaction starts is
  refused by the server (error 1792) through `executeQuery`, `execute` and
  `executeUpdate` alike.
- JSP scriptlets contain no live raw JDBC.

## 8. Session defaults the adapter must apply

When a connection opens, the adapter adds one statement to the driver's
`initSql` option, ahead of any `initSql` the operator configured:

```
SET SESSION net_write_timeout=600, sql_mode=IF(FIND_IN_SET('IGNORE_SPACE',@@GLOBAL.sql_mode)>0,@@SESSION.sql_mode,TRIM(BOTH ',' FROM REPLACE(CONCAT(',',@@SESSION.sql_mode,','),',IGNORE_SPACE,',',')))
```

- The `net_write_timeout=600` part is left out when the operator's
  `sessionVariables` or `initSql` mentions `net_write_timeout`.
- The `sql_mode=…` part is left out when the operator's `sessionVariables` or
  `initSql` mentions `sql_mode`.
- Both comparisons ignore ASCII case. With both parts left out nothing is added.
- `sessionVariables` and `initSql` are recognised in any letter case and stored
  under those exact spellings.
- The defaults apply to `jdbc:mysql:` and `jdbc:mariadb:` URLs alike.

Verified on MariaDB 11.8.8 through the adapter: the session then has
`sql_mode=''` and `net_write_timeout=600` for the application and the read-only
reporting account; an operator statement placed after it wins.

`sessionVariables` cannot carry the `sql_mode` expression, because the driver
splits that option at commas without regard to parentheses.

## 9. Not part of this work

- `carlos-ctl` still writes `db_driver=com.mysql.cj.jdbc.Driver` and
  `jdbc:mysql://` URLs, so the adapter remains the permanent path for packaged
  installs. `carlos-podman` builds DrugRef from `master`. Their comments and
  documentation need a follow-up in those repositories.
- MariaDB Connector/J 4.0 (needs its own probe run; it does not remove the need
  for the guard).
- The assistant and agent files under `.github/` named in #3053.
- Statement validators for the dashboard and Jasper sinks. The guard restores
  what they had before the switch; adding a validator could refuse templates
  that work today and needs its own review.
- Observations about report code that are not caused by the driver switch were
  reported to the maintainer separately.
