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
Budget exhaustion cancels the statement before closing the cursor, statement and
Spring-managed connection. Scheduled cancellation is removed on close. Other
legacy JDBC callers retain their scrollable cursor contract.

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
probe short and assert cancellation within four seconds.

Observed in Ubuntu 26.04.1, OpenJDK 25.0.4.1 and MariaDB 11.8.6, with `-Xmx128m`:

| Dataset | Duration | Sum of heap-pool peaks | Outcome |
| --- | ---: | ---: | --- |
| 10 rows, 64 accented characters per value | 123 ms | 30.1 MiB | Complete |
| 10,000 rows, same values | 338 ms | 75.8 MiB | Complete |
| 100,000 available rows, same values | 335 ms | 70.9 MiB | Row limit; no partial output |
| One 16 MiB field | 511 ms | 66.6 MiB | Cell limit; bounded reader stops |
| `SLEEP(5)` | 1,037 ms | 28.1 MiB | Timed out |
| 1,000 rows with `SLEEP(0.02)` per row | 1,032 ms | 28.1 MiB | Timed out during streaming |

These are synthetic local measurements, not production latency or exhaustion
claims. Automated regressions cover the exact CSV byte boundary, UTF-8 and quote
expansion, encoded HTML expansion, row sentinel, shared sequence budgets, wide
metadata, oversized readers, deadline checks and JDBC cleanup.
