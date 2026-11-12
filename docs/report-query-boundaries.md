# Report persistence boundaries

Report configuration and fixed business reads belong to typed DAOs. The legacy
`RptReportConfigData`, `RptReportFilter`, `RptReportItem`, and
`RptTableFieldNameCaption` beans retain the arrays, vectors, and properties expected
by existing JSPs; their persistence reads return entities or typed projections,
never open JDBC resources. Configuration ordering includes the identifier as a
stable tie breaker. Nullable order numbers retain SQL ordering and expose the legacy zero value;
nullable filter report IDs and report status remain readable without rewriting stored NULLs.
SQL NULL strings keep the legacy trimmed empty-string behavior.
CSV export dispatches to the registered servlet by name, preserving the existing
report permission gate after the result JSP's relocation below `WEB-INF`.
Attachment names stay local to each request, and buffered response write/close failures propagate
instead of being swallowed.

`ReportConfigurationService` owns append/order allocation, duplicate deletion,
reordering/removal, and caption updates. Its transaction is REQUIRED; the report
DAOs join it through `AbstractDaoImpl`. No REQUIRES_NEW boundary is introduced.
The service removes the original reordered entry once, even when no sibling
occupies the new range, and rejects entries belonging to a different report or
saved variant. Existing generated identifiers and table layouts are unchanged.

The billed-provider reports use `BilledProviderReportDao` projections. Native
bound SQL preserves creator restrictions, deleted header/detail exclusions,
distinct patient/visit counts, service-date code selection, billing-date counts,
and MariaDB's current-date age expressions. `DemographicExtDao.findReportValues`
preserves the antenatal export's single latest extension across the selected
cohort; it intentionally does not reinterpret that query as latest per patient.

## Configurable SQL exceptions

These are explicit compatibility exceptions, not permission to route arbitrary
SQL through the clinical EntityManager:

| Workflow | Execution boundary and limits |
| --- | --- |
| Query by Example | Unchanged `RptByExampleData` and `ReportingConnectionService`: separate least-privileged reporting account, `QueryByExampleSqlValidator` SQL-shape, schema and sensitive-table checks before execution (including favourites); column restrictions enforced by the reporting-account grant audit and database privileges, 15 second timeout, 1,000 rows, 1,000,000 output characters, cancellation and audit. It fails closed when that account is unavailable. |
| Configured form/antenatal exports and clinical denominator/numerator SQL | `ConfiguredReportQuery`: validated report SELECT, bound values, managed legacy JDBC datasource, 30 seconds, 10,000 rows, 5 Mi characters per query. Results detach before returning; excess rows/output fail instead of returning partial results. Internal identifier sets use a separate 100,000-ID limit and the same 30 second timeout; the rendered row budget is applied only to result rows. The CSV servlet also rejects downloads exceeding 5 MiB in UTF-8. These five values are defaults: administrators can change them in `carlos.properties` (`configured_report.*`, see [Configured report limits](#configured-report-limits)). Existing form-table/column validation and configuration permissions remain in their callers. |
| Report by Template | Template SQL is validated when saved and again before execution; `ReportObjectGeneric` parameterization and active-template checks remain. `SQLReporter` uses streaming `ReportResultBudget`: one shared deadline (30 seconds), row limit (10,000), 32 queries and HTML limit (10 MiB) per report, including all queries in a sequence. Each query result is limited to 256 columns and each cell to 64 Ki characters. Administrators can change the deadline, rows, HTML, columns and cell size in `carlos.properties` (`report_by_template.*`, up to 300 seconds, 100,000 rows, 50 MiB, 2,000 columns and 1 Mi characters; see [Report by Template budgets](test/report-template-budgets.md)). Each query's CSV download is limited to 5 MiB; a larger CSV is held back and the result is still shown with a note. Failed sequences discard earlier output. Resources are released on success, timeout, limit rejection and rendering failure. |
| Form column discovery | The existing encounterForm table allowlist and bare-identifier validation remain; a zero-row metadata query returns column names without reading field values, including for empty tables. Query failures propagate instead of presenting empty fields. |

Configured clinical and legacy form reports continue using their existing managed
legacy datasource. Moving those templates to the restricted reporting account
requires a separate compatibility and grant review; the Query by Example policy
must not be widened to accommodate them. Configured JDBC queries use a monotonic deadline established before connection acquisition.
The pool's configured timeout governs a blocking acquisition; that wait consumes the query budget,
and an expired budget prevents statement execution. Once acquired, connection cancellation covers
statement setup, execution and streaming reads; a rounded-up JDBC `setQueryTimeout` is a fallback.
Closing an unread streaming cursor aborts it before asynchronous resource release so cleanup does
not drain an unbounded result. The fixed antenatal birth-summary DAO also enforces the 100,000-ID
ceiling with an overflow row and a query timeout. No report SQL or patient values are added to audit logs.

Configured form/clinical query failures and budget breaches propagate instead of becoming successful
empty exports. Clinical denominator/numerator failures abort the report rather than reporting a zero
population or silently excluding patients; denominators use the 100,000-ID budget. Numerator
existence/output checks read only their first matching row. Report by Template preserves result-page
navigation for an ordinary single-query SQL failure and displays the error with no CSV/XLS download;
limit failures (with a message that names the limit and, while it can still be raised, its setting) and
failed sequences return to configuration.
A CSV over its limit is not a failure: the result page shows the report and holds back only that
query's downloads. Caption lookup retains its legacy
empty-caption fallback for database failures.

## Configured report limits

The limits of `ConfiguredReportQuery` (form reports and their CSV download, the
demographic/antenatal CSV export, Clinical Reports numerators and denominators, and
the antenatal birth-summary cohort) are `carlos.properties` settings. They are read
each time a query runs, so an operator's edit takes effect at the next restart.
The rules are those of the Report by Template settings (`ReportLimitSetting`): a
whole number from 1 to the maximum; a missing or blank setting keeps the default;
a word, zero, or a negative or decimal number keeps the default and a value above
the maximum is capped, each with one warning in the log that names the setting; no
value switches a limit off. Query by Example keeps its own fixed limits.

| Limit | Default | Setting (maximum) |
| --- | ---: | --- |
| Rows one report or export may return | 10,000 | `configured_report.max_rows` (100,000) |
| Patients or records one internal selection may hold (clinical denominators, form-report sub-queries, the birth-summary cohort) | 100,000 | `configured_report.max_identifier_rows` (1,000,000) |
| Deadline of each query, including the wait for a connection | 30 seconds | `configured_report.timeout_seconds` (300) |
| Characters of text one query may return | 5,242,880 | `configured_report.max_output_chars` (52,428,800) |
| Form-report CSV download, UTF-8 | 5 MiB | `configured_report.max_csv_mb` (50) |

Every returned row is detached and held in memory until the page or file has been
written, so `max_rows` and `max_output_chars` should be raised only with heap to
spare (allow a few kilobytes per row of a wide report, for each report running at
once). The packaged nginx proxy stops waiting after 300 seconds.

A breach is a `ConfiguredReportQuery.LimitExceededException` that carries its limit
and value; a JDBC deadline (`SQLTimeoutException`) is reported as the time limit.
The report still fails as a whole, with the same HTTP status as before (500, or 413
for a download over its size limit), and nothing partial is shown or downloaded.
`ReportLimitNotice` publishes the limit to the shared error page, which shows a
message in the user's language that names the limit and its value and, while the
limit is below its maximum, ends with "An administrator can raise this limit with
the setting `configured_report.…` in carlos.properties.". Only a bundle key, the
number and the setting's name are published: never the exception, SQL or a value.

## Jasper and remaining legacy APIs

The bundled lab/billing day sheets and patient end-year statement use
`FixedJasperReportDao` detached projections and explicit `FixedJasperDataSources`.
The fixed templates contain no SQL or `REPORT_CONNECTION`. The statement loads
headers and items in one repeatable-read transaction; HTML totals and PDF details
use the same result. Blank statement date endpoints use optional bound predicates.
Patient lookup retains its writable transaction for demographic-access audit writes.
PDF methods suspend ambient transactions and render without database access.

Both downloads render to owner-only temporary files, validate the PDF signature,
and set headers only after generation succeeds. Files are removed on every exit;
output/cleanup failures propagate. Both PDFs prohibit cache storage (`no-store`).
Invalid renderer output is a PDF error, distinct from disk or response I/O errors.
Day sheets now return HTTP 204 with no body when no rows match, replacing the old
HTTP 200 blank PDF. Bundled day sheets require strict `yyyy-MM-dd` dates; missing,
invalid and non-padded dates now return HTTP 400. The statement retains its
zero-total header/footer when no invoices match. An invoice with a NULL total or
paid amount fails the statement explicitly as a database/data error; no partial
statement, omitted invoice or fabricated zero amount is returned.
The day-sheet query retains date-only selection across all providers/statuses and
has no added ORDER BY; its historic row order is not guaranteed by SQL.

The administrator-installed `${user.home}/Addresslabel.xml` override retains its
original precedence and arbitrary embedded SQL through the package-private
`ConfiguredJasperTemplateRenderer` managed JDBC boundary. It is explicitly outside
the fixed query contract: changing its query to a fixed typed selection would
silently change the report. A present override that is unreadable or not a regular
file now fails explicitly, as do override compilation/query/export failures;
there is no automatic fallback after an override fails. Its SQL is never executed
through the clinical EntityManager. `printLabDaySheet2Action` owns no JDBC resources.
Other configured-report boundaries and Query by Example's restricted account are
unchanged.

Installed qualification separates the bundled and configured contracts. Set
`CARLOS_REPORT_SERVER_HOME` to the running server's `user.home` directory, mounted
locally when the deployment is remote. The `report-fixed-jasper` check refuses to
run while `Addresslabel.xml` exists and verifies both bundled styles, empty output,
invalid dates and cache headers. To qualify an intentionally installed synthetic
override separately, run `report-configured-jasper` with
`CARLOS_FIXED_JASPER_OVERRIDE_TEXT` set to its expected output marker. This checks its
precedence over both styles and null-date compatibility against that marker.
Override checks do not satisfy bundled-template qualification; remove the owned
override and run the bundled check separately. The check never replaces or removes
administrator configuration.

Environment prerequisites run before SQL setup. Missing server-home, override
fixture/marker or Poppler prerequisites produce SKIP in ordinary runs; strict
release qualification rejects skipped checks. Invalid configured paths and a
conflicting override in bundled mode fail with explicit guidance. The
`packaged-full` lane declares the server-home requirement, and installed lifecycle
clients supply the standard `/var/lib/carlos-emr` home automatically.

Test-only baseline templates in `src/test/resources/jasper-5692-baseline/` are copied
from release commit `e0601ced004c38040e6ef293e4b3f24fc43826d3` (trailing whitespace normalized) solely for synthetic
JDBC-versus-typed rendered-output comparisons; they are not packaged in the WAR.

`LookupDao.runProcedure` had no production callers and is removed rather than
retaining an untyped routine-name/string-array execution API. A future routine
must define its allowed name, IN/OUT types, result contract, and service transaction
before implementation. Active lookup queries now bind Java String/Integer/SQL
Date/Timestamp values with explicit Hibernate types; NULL strings remain typed.
`DBPreparedHandlerParam` and `LegacyJdbcQuery` remain for their other active callers.
`DBHelp` remains for non-report form consumers and must not be deleted yet.

## Regression coverage

`ReportConfigurationDaoIntegrationTest` verifies committed generated identifiers,
ordering, variant isolation, native date/decimal/metadata results, and a real
rollback after an earlier DELETE has been flushed. The test runs against H2 and
the physical MariaDB CI harness. `BilledProviderReportDaoIntegrationTest` qualifies
native age/date, distinct-count, provider, and deleted-status behavior on MariaDB.
Configured query unit tests verify null/label handling, immutable detached output,
budget rejection, and result cleanup. Existing CSV and report-template tests
continue to cover parameter ordering and spreadsheet formula protection.
`npm run test:report-typed-queries-playwright` qualifies generated report IDs,
configuration add/delete commits, empty-table field metadata, provider roles,
native billed counts, and the gated CSV download on an Ontario demo install.
The existing Report by Template and Query by Example least-privilege browser
checks qualify the metadata export and restricted-account compatibility paths.
