# Query-by-Example budgets and recovery

`report-query-by-example-budgets` implements #5811 through Administration →
Query By Example and its native form. It creates 1,202 marker-owned extension
rows for one synthetic patient: 1,001 distinct ordered narrow rows, 200
encoding-heavy rows, and one 600,000-character value spanning JDBC reader chunks.
SQL creates fixtures; every tested report runs through the deployed application.

The 999/1,000/1,001-source-row cases compare all returned source IDs, keys and
values, distinguishing exactly 1,000 complete results from omitted extra rows.
The character cases inspect the **original result-table markup** from the real
HTTP response, independently of page-wrapper size or browser serialization.
Both aggregate encoded output and one large streamed cell approach the separate
1,000,000-character cap while remaining within it. Row/cell closing markup and
entities must remain complete, original identities/order and preceding values
must survive, and any final partial cell must be a faithful prefix with an explicit
ellipsis and the distinct size warning. Partial display is not claimed as a full
clinical value. No fixture markup may execute or become an HTML element.

The timeout fixture is one allowed SELECT over three filtered aliases of the
1,001-row set, with SHA-512 work that depends on all three values. This is a
finite streaming cross-join, bounded by the application's 15-second query timeout;
it uses no SLEEP, forbidden function, fabricated error or HTTP timeout. Independent
MariaDB process-list observations prove the actual reporting account runs the
query for at least 12 seconds. The native timeout guidance, absent old results,
absent successful history, cancelled database execution and bounded timeout audit
must all agree. Narrowing the same composer then returns the expected rows with
no stale timeout/truncation guidance.

Each successful/truncated execution enters history exactly once; the timeout does
not. Each execution requires exactly one fresh row in the `log` audit table
(finding 357; the INFO line it replaces as evidence is discarded at the packaged
WARN level): found by the run's id window and its independently computed SHA-256,
then required to be `read`/`query_by_example`, by the test provider with a client
address, with no content id and no patient, and with data naming the exact query
length, row count and outcome. Another action or content type, another provider,
substituted/duplicate metadata, extra fields or raw SQL cannot stand in for audit
evidence, and the application log span of each run must not contain the submitted
SQL. Twenty-one independent controls reject bad table fragments and false audit
evidence; two positive controls accept valid inputs and one pins the audit lookup.

Requires `CARLOS_DISPOSABLE_VM=1`, a configured read-only reporting account
(`QBE_REPORTING_USER` defaults to `carlos_report`), a SQL fixture account with
PROCESS visibility, and a readable application log via `LAB_UPLOAD_JOURNAL_UNIT`
or `LAB_UPLOAD_SERVER_LOG` (to prove the SQL never reaches it; INFO need not be
enabled). Missing execution, audit or account proof cannot count as a passing
resource-limit test.

Cleanup claims empty namespaces before writes, refuses active queries or changed
ownership, and removes only the owned extension/history rows. Unexpected
favourites or foreign history are retained for reconciliation. It verifies zero
owned residue and an unchanged clinical parent. Existing validation, escaping,
history/favourite, least-privilege and front-door checks remain registered.
