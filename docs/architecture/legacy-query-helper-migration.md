# Legacy query-helper migration (#5508)

This inventory starts from `release/2026.08` at
`be931c3fcb0ff9161c6a9a0b317fa5ac52fc81bb` and records the first migration on
that base. The operation index and caller counts below are a historical snapshot
of that migration, not a live inventory of the current release tree. During
release integration, [#5552](https://github.com/carlos-emr/carlos/pull/5552)
already migrated the indexed `MSPReconcile` reads and retired `dbExtract` for
#5546; those entries remain as the audit trail of the original inventory. The work is deliberately split into module changes, as requested by
[#5508](https://github.com/carlos-emr/carlos/issues/5508). It does not claim that
all application SQL has been converted to JPA.

## Completed in this change

| Operation | Previous SQL/resource boundary | Current typed boundary and preserved behavior |
| --- | --- | --- |
| `DxReference.getLatestDxCodes` | Connection, prepared statement and result set over `billingmaster`: `SELECT dx_code1, dx_code2, dx_code3, service_date ... WHERE demographic_no=? AND billingstatus != 'D' ORDER BY service_date DESC LIMIT 10` | `BillingmasterDAO.findRecentDiagnosisRows` returns `DiagnosisRow`; `BillingDiagnosisService` returns `DiagnosisDto`. Native SQL and string binding preserve MariaDB comparison semantics. The ten-bill limit precedes code expansion and deduplication; blank/null codes are skipped, original code whitespace is retained, first occurrence supplies the date, and descriptions use the existing `DiagnosticCodeDao`. The facade retains legacy date parsing, stable reverse ordering (including null dates first), and its empty-result database-failure behavior. |
| Measurement graph LOINC read | `MeasurementGraphAction22Action` acquired a connection solely to pass it to `CommonLabTestValues.findValuesByLoinc2`, which never used it | Remove the parameter and acquisition. The existing `MeasurementDao.findMeasurementsWithIdentifiersByDemographicIdAndLocationCode` query, mapping, order and graph permissions are unchanged. This is connection retirement, not a change to the lab query. |
| `DBHandler`, `DBPreparedHandler` | Deprecated forwarding classes with zero production consumers on the audited base | Remove both classes. The production-source guard rejects their symbols, including in Java/JSP comments and imports. Keep `DBPreparedHandlerParam`: scheduling, lookup DAO operations and report JSPs still use it. |
| Waiting-list names | Already use `WaitingListDao` / `WaitingListNameDao` | Remove an obsolete JDBC/rollback comment; no business query migration is needed here. |

`BillingDiagnosisService` is a Spring-managed read-only service. Its query method
on `BillingmasterDAO` explicitly uses `SUPPORTS`, overriding that legacy DAO's
class-level `REQUIRES_NEW`. The service starts a transaction when needed and joins
an existing write transaction. Inheriting `REQUIRES_NEW` would hide uncommitted
bills from the picker. Existing write methods on that DAO are unchanged.
The new boundary changes an unscoped read from separate auto-commit reads to one
service transaction; this intentionally keeps the bill and description reads in
a single transaction. It does not add a write, permission bypass or audit call.

## Inventory method and transaction ownership

The [operation index](legacy-query-helper-operations.tsv) records every direct
Java executor invocation and JSP scriptlet invocation of `LegacyJdbcQuery`, plus
`DBHelp.searchDBRecord` and the `dbExtract` construction site, in the original migration
snapshot. Java methods were parsed with the JDK compiler tree API, so multiline
calls, overloads, package-private methods and nested methods are included. The
snapshot has 235 Java call expressions grouped into 151 method entries, plus
32 JSP call expressions, across 87 files. Of those Java expressions, 128 in 47
files belong to the separately tracked `Frm*Record` / `FrmRecordHelp` project.

Each index entry records the source, entry point and line, exact boundary call,
SQL declaration context, result getter/mapping context, ambient transaction
participation and replacement candidate/follow-up. SQL and mapper context cover
the containing method, rather than pretending a dynamic query has a single fixed
SQL string. Follow the source when the query comes from a caller, constant,
allowlisted form table, template or configuration. This is a source inventory,
not a claim that every historical entry point is reachable in every deployment.
Indirect consumers of generic wrappers migrate with their owning module; the
linked tickets explicitly include their Java/JSP call chains.

`LegacyJdbcQuery.getConnection()` already uses Spring `DataSourceUtils`.
Remaining operations can participate in an ambient Spring transaction; the
helper does not by itself start a service transaction. Before replacing any
operation, inspect its entry-point service/proxy, connection-taking downstream
adapters, and write ordering. Do not describe these as universally unmanaged
or auto-commit connections, and do not apply a DAO's propagation policy without
reviewing it. The index's common transaction note records that shared boundary;
module tickets require review of the actual entry-point transaction and real
commit/rollback coverage before each conversion.

The following searches complement the parsed call inventory with type-only
references, trust validators, compatibility parameters and indirect wrappers:

```sh
rg -n 'LegacyJdbcQuery|DBHelp|dbExtract|DBPreparedHandlerParam' src/main
rg -n '\bDBHandler\b|\bDBPreparedHandler\b' src/main
rg -n 'AppointmentMainBean|SqlUtils|JDBCUtil|FrmRecordHelp' src/main
```

The second search should be empty. `ParameterizedSql`, `EFormSqlSafety`, report
validators, `StatementClosingResultSet` and `DbConnectionFilter` are policy or
lifecycle infrastructure, not additional business-query migrations.

## Module follow-ups and replacements

| Follow-up | SQL ownership and mapping to preserve | Candidate persistence replacement |
| --- | --- | --- |
| [#5546](https://github.com/carlos-emr/carlos/issues/5546): BC MSP/WCB | Bill extraction, provider/payee joins, remittance/private payment/reconciliation rows, amounts, statuses and dates; `dbExtract` exported cursors in the audited snapshot and was retired by #5552; fixed Jasper reports use connections | Existing `BillingmasterDAO`, Billing/WCB/provider/Teleplan models and DAOs, plus typed native payment/remittance projections. Preserve pagination/order, amounts, provider restrictions and reviewed write propagation. |
| [#5547](https://github.com/carlos-emr/carlos/issues/5547): encounter/eForm/demographic | Demographic and eChart values; grouped/latest form IDs and dates; study/shortcut/property values; eForm groups and dynamic values/JSON; form XML import and scheduling wrapper operations; label templates and role-repair JSP | Existing demographic, eChart, encounter-form, study/property, eForm and scheduling/case-management DAOs. Retain dynamic-table allowlists, row budgets, null/date/string conventions, current JSP encoding/access checks and synchronous audit calls. XML import needs typed insert results and generated-ID/rollback checks. |
| [#5548](https://github.com/carlos-emr/carlos/issues/5548): lab/MDS/Messenger/workflow | Lab routing/import writes; bounded `hl7TextInfo` status reads and `UPDATE LOW_PRIORITY`; MDS `HIGH_PRIORITY` patient counts; dynamic document-transfer fields; workflow first-ID lookups | Existing lab/routing/HL7, document/HRM and workflow DAOs, with typed count/schema projections. Preserve sign-off, matched/unmatched/abnormal counts, routing/duplicates, priorities, metadata and workflow state semantics. |
| [#5549](https://github.com/carlos-emr/carlos/issues/5549): reports/lookup procedures | Report configuration/order/caption/filter/item rows; fixed CSV/day-sheet/clinical report results; lookup procedure parameters; dynamic SQL and metadata-based exports | Existing report configuration/item/filter/caption DAOs and clinical DAOs for fixed business queries; explicit procedure argument/result DTOs. Keep configurable reporting SQL and Jasper exceptions behind their existing validation, credentials, budgets and cleanup until separately reviewed. |
| [#2249](https://github.com/carlos-emr/carlos/issues/2249) / #2167: form records | `Frm*Record` / `FrmRecordHelp` per-form reads, writes, history, generated IDs and XML serialization | Separate form entity/DAO replacement, not expanded into this PR. All 47 current files and their operations remain listed in the index. |

## Retained JDBC exceptions and lifecycle

Query by Example (`RptByExampleData`) continues to acquire its own restricted
reporting-account connection. Its `TrustedSql` references are validation, not
calls to the clinical helper's executor. Never route arbitrary report SQL through
the clinical EntityManager. Coordinate its SQL validation, timeout, row/output
budgets and cancellation work with [#5311](https://github.com/carlos-emr/carlos/issues/5311).

`DemographicLabelPdf`, `PrintReferralLabel2Action`,
`PatientEndYearStatementService` and `CreateBillingReport2Action` pass managed
connections to Jasper templates. Converting them requires checking query fields,
subreports, clinic overrides, no-row behavior and PDF output before introducing
bean data sources. SQLReporter, clinical configurable reports and
MsgGenerate's schema-driven exports likewise need typed metadata contracts;
their current JDBC behavior remains explicitly tracked, not silently moved to
JPA or declared complete.

Retain `DBHelp` for report consumers, `DBPreparedHandlerParam` for scheduling/lookup DAO operations/report JSPs, `JDBCUtil` for
form import, and `LegacyJdbcQuery` / `StatementClosingResultSet` for their actual
remaining callers. Request/worker finalizers and datasource shutdown cleanup
remain required. Raw-accessor retirement is already complete under
[#5507](raw-jdbc-retirement.md); this work neither restores that accessor nor
changes Flyway, datasource policy, advisory locks, JDBC drivers or shutdown.

## Verification

`BillingDiagnosisServiceIntegrationTest` disables the inherited test transaction
and uses real `TransactionTemplate` boundaries. It verifies the ten-bill limit,
deleted/null statuses, other-patient isolation, nullable/quoted values, generated
IDs, read-your-writes before a second write, independent post-commit visibility,
and rollback after that second write. Unit tests preserve duplicate/blank-code
and description mapping, legacy invalid/null-date sorting and database failure
handling. Existing measurement and legacy resource-lifecycle regressions remain
part of validation.

Run against physical MariaDB using the existing isolated test harness, never an
operator database. The optional province selector defaults to `on`; `bc` loads
the actual published BC schema, which owns `billingmaster`:

Start MariaDB with the repository's compatibility configuration (32 KiB InnoDB
pages for a fresh test datadir, `innodb_strict_mode=0` and the configured empty
SQL mode). The BC baseline contains legacy wide tables; stock strict defaults
can reject that schema before any test runs.

```sh
CARLOS_TEST_MARIADB_DAO=true \
CARLOS_TEST_MARIADB_URL=jdbc:mariadb://127.0.0.1:3306/ \
CARLOS_TEST_MARIADB_SCHEMA=flyway \
CARLOS_TEST_MARIADB_PROVINCE=bc \
mvn test -Dtest=BillingDiagnosisServiceIntegrationTest
```

Use the environment's configured Maven proxy, CA trust and credentials. The
harness requires physical MariaDB, creates a unique synthetic schema and drops
only that schema after the pools close. The regular MariaDB CI lanes select an
explicit `-Dtest` list that does not include this test; its inherited
`spring-context` tag only filters tests already selected. Run this test explicitly
for BC Flyway qualification using the command above. H2 results alone do not
establish MariaDB-native comparison, metadata or transaction behavior.

## Historical caller index

| Source | Entry points | Follow-up |
| --- | --- | --- |
| [AppointmentMainBean.java](../../src/main/java/io/github/carlos_emr/AppointmentMainBean.java) | `queryResults`, `queryResults_paged`, `queryResultsCaisi` | #5547 |
| [ExtractBean.java](../../src/main/java/io/github/carlos_emr/carlos/billings/MSP/ExtractBean.java) | `dbQuery` | #5546 |
| [dbExtract.java (historical)](https://github.com/carlos-emr/carlos/blob/be931c3fcb0ff9161c6a9a0b317fa5ac52fc81bb/src/main/java/io/github/carlos_emr/carlos/billings/MSP/dbExtract.java) | `openConnection` | #5546 |
| [CreateBillingReport2Action.java](../../src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/MSP/CreateBillingReport2Action.java) | `execute` | #5546 |
| [MSPReconcile.java](../../src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/MSP/MSPReconcile.java) | `getBillsByType`, `getPayments`, `getPrivatePayments`, `getMSPRemittanceQuery`, `getAdjustmentsMSPRemittanceQuery`, `hasMatchingBillingRecord` | #5546 |
| [ViewWCB2Action.java](../../src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/pageUtil/ViewWCB2Action.java) | `execute` | #5546 |
| [PatientEndYearStatementService.java](../../src/main/java/io/github/carlos_emr/carlos/billings/ca/on/service/PatientEndYearStatementService.java) | `writePdfTo` | #5549 |
| [PrintReferralLabel2Action.java](../../src/main/java/io/github/carlos_emr/carlos/commn/web/PrintReferralLabel2Action.java) | `renderLabel` | #5547 |
| [LookupDaoImpl.java](../../src/main/java/io/github/carlos_emr/carlos/daos/LookupDaoImpl.java) | `runProcedure` | #5549 |
| [DemographicLabelPdf.java](../../src/main/java/io/github/carlos_emr/carlos/demographic/DemographicLabelPdf.java) | `write` | #5547 |
| [EFormUtil.java](../../src/main/java/io/github/carlos_emr/carlos/eform/EFormUtil.java) | `getValuesOrNull`, `getJsonValuesOrNull`, `getEFormGroups`, `addEFormToGroup`, `listEForms`, `listPatientEForms`, `getSQL` | #5547 |
| [EctFormData.java](../../src/main/java/io/github/carlos_emr/carlos/encounter/data/EctFormData.java) | `getGroupedPatientFormsAsArrayList`, `getPatientFormsAsArrayList` | #5547 |
| [EctPatientData.java](../../src/main/java/io/github/carlos_emr/carlos/encounter/data/EctPatientData.java) | `getProviderNo`, `getPatient`, `init` | #5547 |
| [FrmData.java](../../src/main/java/io/github/carlos_emr/carlos/form/data/FrmData.java) | `getPatientForms`, `getCurrentPatientForm`, `getStudyNameLink`, `getShortcutFormValue`, `getResource` | #5547 |
| [FrmSetupForm2Action.java](../../src/main/java/io/github/carlos_emr/carlos/form/pageUtil/FrmSetupForm2Action.java) | `loadFormRecord` | #5547 |
| [MessageUploader.java](../../src/main/java/io/github/carlos_emr/carlos/lab/ca/all/upload/MessageUploader.java) | `routeReport` | #5548 |
| [HL7Handler.java](../../src/main/java/io/github/carlos_emr/carlos/lab/ca/all/upload/handlers/HL7Handler.java) | `updateLabStatus` | #5548 |
| [LabUpload2Action.java](../../src/main/java/io/github/carlos_emr/carlos/lab/ca/on/CML/Upload/LabUpload2Action.java) | `saveParsedLab` | #5548 |
| [DBHelp.java](../../src/main/java/io/github/carlos_emr/carlos/login/DBHelp.java) | `searchDBRecord` | #5549 |
| [CategoryData.java](../../src/main/java/io/github/carlos_emr/carlos/mds/data/CategoryData.java) | `getLabCountForPatientSearch`, `getDocumentCountForPatientSearch`, `queryTrustedCount` | #5548 |
| [MsgGenerate.java](../../src/main/java/io/github/carlos_emr/carlos/messenger/docxfer/send/MsgGenerate.java) | `constructTable` | #5548 |
| [SQLDenominator.java](../../src/main/java/io/github/carlos_emr/carlos/report/ClinicalReports/SQLDenominator.java) | `getDenominatorList` | #5549 |
| [SQLNumerator.java](../../src/main/java/io/github/carlos_emr/carlos/report/ClinicalReports/SQLNumerator.java) | `evaluateOLD`, `evaluate` | #5549 |
| [RptReportConfigData.java](../../src/main/java/io/github/carlos_emr/carlos/report/data/RptReportConfigData.java) | `insertRecordWithOrder`, `getConfigFieldName`, `getConfigNameList`, `getConfigObj`, `getReportTableNameList` | #5549 |
| [RptReportCreator.java](../../src/main/java/io/github/carlos_emr/carlos/report/data/RptReportCreator.java) | `getSelectField`, `getFromTableFirst`, `getFromTable`, `getRltSubQuery`, `query` | #5549 |
| [RptReportFilter.java](../../src/main/java/io/github/carlos_emr/carlos/report/data/RptReportFilter.java) | `getNameList` | #5549 |
| [RptReportItem.java](../../src/main/java/io/github/carlos_emr/carlos/report/data/RptReportItem.java) | `getReportName`, `getNameList` | #5549 |
| [RptTableFieldNameCaption.java](../../src/main/java/io/github/carlos_emr/carlos/report/data/RptTableFieldNameCaption.java) | `recordExists`, `getNameCaptionProp`, `getMetaNameList` | #5549 |
| [RptDownloadCSVServlet.java](../../src/main/java/io/github/carlos_emr/carlos/report/pageUtil/RptDownloadCSVServlet.java) | `demoReport` | #5549 |
| [printLabDaySheet2Action.java](../../src/main/java/io/github/carlos_emr/carlos/report/pageUtil/printLabDaySheet2Action.java) | `execute` | #5549 |
| [SQLReporter.java](../../src/main/java/io/github/carlos_emr/carlos/report/reportByTemplate/SQLReporter.java) | `executeQuery` | #5549 |
| [JDBCUtil.java](../../src/main/java/io/github/carlos_emr/carlos/util/JDBCUtil.java) | `importRowExists`, `insertImportRow` | #5547 |
| [SqlUtils.java](../../src/main/java/io/github/carlos_emr/carlos/util/SqlUtils.java) | `getQueryResultsList` | #5547 |
| [RHWorkFlow.java](../../src/main/java/io/github/carlos_emr/carlos/workflow/RHWorkFlow.java) | `firstId` | #5548 |
| [fixRolesOnNotes.jsp](../../src/main/webapp/WEB-INF/jsp/admin/fixRolesOnNotes.jsp) | `JSP scriptlet` | #5547 |
| [onSearch3rdBillAddr.jsp](../../src/main/webapp/WEB-INF/jsp/billing/CA/BC/onSearch3rdBillAddr.jsp) | `JSP scriptlet` | #5547 |
| [antenatalplannerprint.jsp](../../src/main/webapp/WEB-INF/jsp/decision/antenatal/antenatalplannerprint.jsp) | `JSP scriptlet` | #5547 |
| [PatientSearch.jsp](../../src/main/webapp/WEB-INF/jsp/oscarMDS/PatientSearch.jsp) | `JSP scriptlet` | #5547 |
| [reportonbilledphcp.jsp](../../src/main/webapp/WEB-INF/jsp/report/reportonbilledphcp.jsp) | `JSP scriptlet` | #5549 |
| [reportonbilledvisitprovider.jsp](../../src/main/webapp/WEB-INF/jsp/report/reportonbilledvisitprovider.jsp) | `JSP scriptlet` | #5549 |

## Lab, inbox, Messenger and RH workflow follow-up (#5548)

The connection parameters in `MessageUploader` and CML `ABCDParser` were read
adapters; report, patient, result and routing writes already used managed DAOs.
`LabRoutingLookupDao` now returns patient/provider projections from bound native
queries. It preserves Ontario OHIP padding, non-Ontario LIKE matching, provider
identifier allowlisting, optional length ordering/limits and separate CML gender
and HIN rules. The callers retain distinct merged-head matching, ambiguity/cycle
refusal, provider forwarding and MRP audit behavior. CML's nested saves no longer
accept an unused connection. `FileUploadCheck.storeIfNew` still owns the upload's
READ_COMMITTED / REQUIRES_NEW transaction and checksum lock.

`Hl7TextInfoDao` exposes bounded descending lab-status projections and retains
MariaDB's `UPDATE LOW_PRIORITY`. `InboxCategoryDao` owns count SQL, including
HIGH_PRIORITY, distinct accession/document counts, status/date/HIN filters and
HRM sign-off semantics. Its per-call query state returns immutable count/patient
projections; `CategoryData` retains UI aggregation and does not create an
EntityManager. `WorkFlowDao.findLatestRhFormId` binds patient and workflow and
orders by edit time then ID, without changing state/workflow creation methods.

`DocumentTransferDao` is the metadata exception: the packaged transfer definition
requires physical table/column names and JDBC string conversions. It returns
immutable row/column DTOs, trims SQL nulls to empty strings as before, validates
the curated SELECT shape, binds the demographic, and uses DataSourceUtils to
join/release the caller's connection. Result/statement resources close before
return, including mapping failures. Shared request, statement and worker cleanup
remains for other modules.

The historical TSV above remains an audit snapshot; these migrated executors are
not outstanding callers. Validation includes the affected upload/count/export
regressions and physical MariaDB tests, including a real CML service rollback
after lab/routing writes with independent-connection verification.
