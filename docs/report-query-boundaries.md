# Report persistence boundaries

Report configuration and fixed business reads belong to typed DAOs. The legacy
`RptReportConfigData`, `RptReportFilter`, `RptReportItem`, and
`RptTableFieldNameCaption` beans retain the arrays, vectors, and properties expected
by existing JSPs; their persistence reads return entities or typed projections,
never open JDBC resources. Configuration ordering includes the identifier as a
stable tie breaker. SQL NULL strings keep the legacy trimmed empty-string behavior.

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
| Query by Example | Unchanged `RptByExampleData` and `ReportingConnectionService`: separate least-privileged reporting account, `QueryByExampleSqlValidator` table/column allowlists, 15 second timeout, 1,000 rows, 1,000,000 output characters, cancellation and audit. It fails closed when that account is unavailable. |
| Configured form/antenatal exports and clinical denominator/numerator SQL | `ConfiguredReportQuery`: validated report SELECT, bound values, managed legacy JDBC datasource, 30 seconds, 10,000 rows, 5 Mi characters per query. Results detach before returning; excess rows/output fail instead of returning partial results. The CSV servlet also rejects downloads exceeding 5 MiB in UTF-8. Existing form-table/column validation and configuration permissions remain in their callers. |
| Report by Template | `ReportObjectGeneric` parameterization/validation and active-template checks remain. `SQLReporter` retains its metadata-dependent HTML/CSV rendering on a managed JDBC result, with the same 30 second, 10,000 row, 5 Mi character read budget and existing 5 MiB CSV cap. All resources close on success, timeout, limit rejection, and rendering failure. |
| Form column discovery | The existing encounterForm table allowlist and bare-identifier validation remain; a bounded metadata read returns column names even when the table is empty. |

Configured clinical and legacy form reports continue using their existing managed
legacy datasource. Moving those templates to the restricted reporting account
requires a separate compatibility and grant review; the Query by Example policy
must not be widened to accommodate them. Timeout cancellation is performed by the
JDBC driver's `setQueryTimeout`; closing the wrapper releases the statement and
Spring-managed connection. No report SQL or patient values are added to audit logs.

## Jasper and remaining legacy APIs

`printLabDaySheet2Action` and `PatientEndYearStatementService` retain managed
connections with try-with-resources. Their Jasper templates execute embedded
queries, so removing the connection requires a separately reviewed template and
JRDataSource/projection conversion. This change does not replace that contract.

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
