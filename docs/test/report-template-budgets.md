# Report by Template resource budgets

`SQLReporter` uses a forward-only Connector/J streaming cursor and builds HTML and
CSV together in one pass. It keeps the current cell and bounded output buffers;
it does not rewind the result set or create an array of all rows. Spreadsheet
formula guards, HTML encoding, column labels, null handling, CSV quoting and CRLF
record separators are preserved.

Every request, including all queries of a sequenced template, shares these limits:

| Resource | Limit |
| --- | ---: |
| Result rows | 10,000 |
| CSV output, including headers, guards, quoting and separators | 5 MiB in UTF-8 |
| Encoded HTML output | 10 MiB in UTF-8 |
| Columns per query | 256 |
| Characters per cell or column label | 65,536 UTF-16 code units |
| Queries per sequence | 32 |
| Query execution and consumption deadline | 30 seconds |

The CSV boundary remains inclusive: exactly 5 MiB is accepted; one byte more is
refused. Exceeding a limit discards the report and returns a narrow-your-search
message. A failed sequence removes its earlier output attributes and does not
execute later queries. A sequence shares output and row limits so that splitting
a large result into subqueries cannot multiply request memory use.

`LegacyJdbcQuery.getBoundedReportResultSet` validates the SELECT and binds its
parameters before execution. It sets a JDBC row ceiling with one overflow row,
a query timeout derived from the remaining request deadline, and Connector/J's
supported streaming fetch size. A cancellation timer remains armed through result
consumption, since the JDBC execution timeout alone may end before streaming does.
The Connector/J streaming sentinel is used only for MySQL/MariaDB; other JDBC
drivers receive a positive fetch hint bounded by the row ceiling.
Connection acquisition and JDBC setup retain the Spring datasource's existing
limits (`db_max_wait`, 20 seconds by default, controls pool waits). Their elapsed
time is charged to the same absolute monotonic deadline; an expired deadline
prevents query execution. A blocked acquisition/setup operation is governed by
the datasource/driver timeouts. The report cancellation timer is armed once the
statement is ready, with the original deadline and nanosecond precision. The
renderer also checks that deadline between rows and reader chunks.
Deadline expiry aborts the connection with JDBC `Connection.abort`, which closes
Connector/J's socket even while `ResultSet.next()` is blocked. `Statement.cancel`
is insufficient once Connector/J has returned from `executeQuery`. Closing an
incompletely consumed report cursor also aborts the connection, preventing a row,
cell or output refusal from draining unread rows. Abort and subsequent cursor,
statement and pool cleanup run on virtual threads; the request does not wait for
slow driver cancellation or cleanup. The connection stays checked out until abort
and cleanup finish. A fully consumed cursor closes normally and keeps its
connection reusable. Scheduled interruption is removed on close. Other legacy
JDBC callers retain their scrollable cursor contract.

The renderer bounds its own allocations. Connector/J can still retain the current
row or field internally; these limits do not change the database's packet limits
or promise a fixed total application heap size.

## Synthetic database and heap probe

The probe runs the real JDBC boundary and renderer against a disposable MariaDB
schema. Its read-only user needs SELECT access; MariaDB's sequence tables supply
synthetic rows without patient fixtures. Set `REPORT_PROBE_JDBC_URL`,
`REPORT_PROBE_USER` and `REPORT_PROBE_PASSWORD` for that schema, then run from the
repository root:

```sh
mvn -DskipTests package dependency:build-classpath -Dmdep.outputFile=/tmp/report-budget.classpath
probe_cp="target/classes:$(cat /tmp/report-budget.classpath)"
mkdir -p /tmp/report-budget-probe
javac -cp "$probe_cp" -d /tmp/report-budget-probe scripts/report-budget-probe/ReportTemplateBudgetProbe.java
for scenario in 10 10000 100000 large-cell timeout stream-timeout; do
  java -Xmx128m -cp "/tmp/report-budget-probe:$probe_cp" \
    io.github.carlos_emr.carlos.report.reportByTemplate.ReportTemplateBudgetProbe "$scenario"
done
```

Each scenario uses a fresh JVM. The probe warms the connection, requests a GC,
resets heap-pool peak counters, and reports elapsed time and the sum of heap-pool
peak usage. Pool peaks need not occur at the same instant, so this sum is a
conservative diagnostic, rather than a precise simultaneous live-set measurement.
The two timeout cases deliberately use a one-second JDBC deadline to keep the
probe short and assert interruption within four seconds. The streaming case sends
a large first row immediately, asserts that `executeQuery` returned within half a
second, then blocks on later rows until the deadline aborts the socket. All cases
use a one-slot DBCP pool and verify that a healthy connection can be borrowed after
report cleanup, with no active connection left behind.

Revalidated with OpenJDK 25.0.3, Connector/J 9.6.0, MariaDB 11.8.9 and a one-slot
DBCP pool, with `-Xmx128m`:

| Dataset | Duration | Sum of heap-pool peaks | Outcome |
| --- | ---: | ---: | --- |
| 10 rows, 64 accented characters per value | 192 ms | 29.9 MiB | Complete |
| 10,000 rows, same values | 405 ms | 72.6 MiB | Complete |
| 100,000 available rows, same values | 332 ms | 70.7 MiB | Row limit; no partial output |
| One 16 MiB field | 383 ms | 66.4 MiB | Cell limit; bounded reader stops |
| `SLEEP(5)` | 1,056 ms | 26.9 MiB | Timed out |
| Immediate 32 KiB first row, then `SLEEP(0.2)` per row | 1,044 ms | 32.8 MiB | Timed out after query returned |

These are synthetic local measurements, not production latency or exhaustion
claims. Automated regressions cover the exact CSV byte boundary, UTF-8 and quote
expansion, encoded HTML expansion, row sentinel, shared sequence budgets, wide
metadata, oversized readers, deadline checks and JDBC cleanup.
