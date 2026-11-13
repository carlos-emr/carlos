# Report by Template resource budgets

`SQLReporter` uses a forward-only Connector/J streaming cursor and builds HTML and
CSV together in one pass. It keeps the current cell and bounded output buffers;
it does not rewind the result set or create an array of all rows. Spreadsheet
formula guards, HTML encoding, column labels, null handling, CSV quoting and CRLF
record separators are preserved.

Every request, including all queries of a sequenced template, shares these limits,
except the CSV limit, which applies to each query. All but the query count are
`carlos.properties` settings (#5849), read each time a report runs, so an edit
takes effect at the next restart. Each is a whole number from 1 to its maximum,
and no value switches a limit off: a missing or blank setting keeps the default,
a value above the maximum is capped, and any other value that is not a whole
number of at least 1 (a word, zero, a negative or decimal number) keeps the
default. Either correction logs one warning per setting and value, naming the
setting and never the value (`ReportLimitSetting`). The shipped
`src/main/resources/carlos.properties` lists every setting, commented out, with
its default and maximum.

| Resource | Default | Setting (maximum) |
| --- | ---: | --- |
| Result rows | 10,000 | `report_by_template.max_rows` (100,000) |
| Query execution and consumption deadline | 30 seconds | `report_by_template.timeout_seconds` (300) |
| Encoded HTML output | 10 MiB in UTF-8 | `report_by_template.max_html_mb` (50) |
| Columns per query | 256 | `report_by_template.max_columns` (2,000) |
| Characters per cell or column label | 65,536 UTF-16 code units | `report_by_template.max_cell_chars` (1,048,576) |
| CSV output of each query, including headers, guards, quoting and separators | 5 MiB in UTF-8 | `report_by_template.max_csv_mb` (50) |
| Queries per sequence | 32 | fixed |

Exceeding a limit discards the report and returns to the configuration page with a
message, in the user's language, that names the limit and its value (D262). While
the limit is below its maximum the message ends with "An administrator can raise
this limit with the setting `report_by_template.…` in carlos.properties."; at the
maximum, and for the fixed query count, that sentence is left out so the page never
promises a raise that cannot be made. The CSV
is the exception (D260): its boundary is inclusive (exactly 5 MiB is kept; one byte
more is held back), and a query whose CSV goes over it is still shown, with a note
above it, and without its CSV and XLSX downloads. In a sequence the note and each
remaining download name their query. The CSV limit is per query because each
query's CSV is kept and downloaded on its own (see [CSV and XLSX downloads](#csv-and-xlsx-downloads)).
While the CSV limit is below its maximum the note ends with the same "An administrator
can raise this limit with the setting `report_by_template.max_csv_mb` in
carlos.properties." sentence. A failed sequence
removes its earlier output attributes, including held-back notes, and does not
execute later queries. A sequence shares the row, HTML and time limits, so that
splitting a large result into subqueries cannot multiply request memory use; a
query's CSV is no larger than its HTML, except for characters the HTML encoder
replaces with a space (Unicode noncharacters; up to four times larger).

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
or promise a fixed total application heap size. Raising `max_html_mb` raises the
heap a running report may use: the HTML buffer grows to the next power of two and
is copied once more into a string, and a sequence keeps each query's CSV as well
(about as much again with ordinary text), so plan on three to four times the
setting per report running at once (about 150–200 MiB at the 50 MiB maximum).
The result page no longer carries the CSV (only a token), so the page itself stays
near the HTML limit; the CSV kept for the downloads is described below. The packaged nginx
proxy also stops waiting after 300 seconds (`proxy_read_timeout`), so at the
300-second maximum the browser may see a gateway timeout instead of the time
message; keep `timeout_seconds` a little lower, or raise the proxy timeout too.

## CSV and XLSX downloads

The downloads are built on the server, never from data the browser sends. When a report
runs, `GenerateReport2Action` hands each query's CSV to `ReportCsvDownloads`, which keeps
it in the user's HTTP session under a random 256-bit token and takes it off the request;
the result page carries only that token, the query's index and the template id. **Export
to CSV** and **Export to XLSX** post those (with the page's CSRF token) to
`oscarReport/reportByTemplate/generateOutFilesAction`. `GenerateOutFiles2Action` checks
the same `_admin`/`_report` read privilege as running the report, looks the result up for
this session and the signed-in provider, and streams the file: the CSV bytes and the XLSX
workbook are the same as the previous posted-CSV export produced for the same data
(`GenerateOutFiles2ActionTest` pins them by digest). Neither copies the kept CSV: the CSV
download writes its bytes as they are, and the XLSX export reads them one row at a time,
twice (once to check every row, so a cell a workbook cannot hold is a 500 before anything
is sent, once to write), so an export holds one row however large the result and however
many run at once. Both downloads, and the 410 page, are `Cache-Control: no-store`. The
route is a read: GET and POST are served, other methods get 405 (registered in
`MutatorActionGetRejectionContractUnitTest` as a read gate; `HttpMethodGuardFilter` passes
it). A `csv` parameter is ignored.

A token the session no longer holds (expired, dropped for a newer result, from another
session or provider, or a result page opened before this change, which posts its CSV) gets
HTTP 410 and a page, in the user's language, saying the result is no longer available,
with a **Run the report again** button that opens the template's configuration (or the
Template Library when the request named no template). No download, error page or
`CARLOS Error: 0` is ever written in its place.

Bounds and memory. Kept CSVs are heap, so a session keeps the results of its three most
recent report runs that offered a download, for 30 minutes after each ran, and at most
three times `report_by_template.max_csv_mb` in all. All sessions together keep at most an
eighth of the JVM's maximum heap (256 MiB with the packaged 2 GiB `CARLOS_JAVA_XMX`);
past that the oldest results of any user are dropped, and that user's download asks them
to run the report again. Older results go first, by when their report ran, and the newest
result is always kept whole, so one sequence whose queries' CSVs add up to more than a
bound is kept by itself (its size is bounded by the shared HTML limit, as above). A query
over the CSV limit is measured without being encoded and held back. A run with nothing to
download keeps nothing and drops nothing: older results stay downloadable from their own
pages. Every report run and every download drops the expired results of all sessions; a
session's results are released when the user signs out or the session times out, and are
never written to disk: the session value is not `Serializable` (session persistence is also
off in the packaged Tomcat). At the 5 MiB default a user who runs reports holds about
15 MiB at most (more only while their newest result is one larger sequence); at the
50 MiB maximum about 150 MiB, and all users together never more than the heap share
above, so raise `max_csv_mb` with heap (`CARLOS_JAVA_XMX`) to match, or users of a busy
server will be asked to run reports again sooner. Before this change the limit could only be lowered,
because the downloads posted the whole CSV back through the web firewall (form data
limited near 16 MiB, and URL encoding inflates CSV), so report data crossed the network
twice and the file was whatever the browser sent.

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
# Every other setting at its maximum and the CSV at its 5 MiB default (#5849): 100,000 rows,
# 50 MiB of HTML, CSV held back.
java -Xmx512m -cp "/tmp/report-budget-probe:$probe_cp" \
  io.github.carlos_emr.carlos.report.reportByTemplate.ReportTemplateBudgetProbe maximums
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
claims; the table predates the `maximums` scenario (#5849), which prints whether
the CSV was held back. Automated regressions cover the exact CSV byte boundary,
the held-back CSV of each query, UTF-8 and quote expansion, encoded HTML
expansion, row sentinel, shared sequence budgets, configured limits, wide
metadata, oversized readers, deadline checks and JDBC cleanup.
