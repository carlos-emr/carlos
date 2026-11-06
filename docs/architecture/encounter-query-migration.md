# Encounter, eForm and demographic query migration (#5547)

The historical caller inventory in [legacy-query-helper-migration.md](legacy-query-helper-migration.md)
remains an audit snapshot. This migration replaces its encounter, eForm,
demographic, scheduling-wrapper and form-import business queries with named DAO
operations. It also migrates every remaining `SqlUtils.getRow` and
`getQueryResultsList` consumer, including the BC billing callers.

| Caller | Persistence boundary and compatibility |
| --- | --- |
| `EctPatientData` | `DemographicDao` and `EChartDao`; strings retain trim-to-empty behavior, patient DOB construction and synchronous audit calls remain in the facade. Chart selection orders by ID, independently of timestamp. |
| `EctFormData`, `FrmData` | `EncounterFormDao.HistoryRow` and named latest-ID queries retain the registry/internal-table allowlist, date-only creation values, per-edit-day grouping, XML form history (including its `form` table identity) and descending ID order. The edited-time shortcut breaks ties by ID. `StudyDao` owns study/link reads; `PropertyDao` owns resource lookup. |
| `FrmSetupForm2Action` | The registered form table and both record/patient IDs are checked before returning detached column values. SQL NULL and raw whitespace remain available for VT's uneditable history. |
| eForm AP values/JSON | `EFormValueDao` validates the existing SQL policy, binds all template parameters and returns `ClinicalDataRow`. Requested column names are resolved by the physical driver before detaching rows, preserving qualified names such as `d.last_name` even when joined columns share a label. Last-row scalar selection, all-row JSON, case-insensitive labels, trim-to-empty values, missing-column placeholders and the strict failure-versus-empty distinction remain in the facade. |
| eForm groups/listings | `EFormGroupDao`, `EFormDao` and `EFormDataDao` own counts, membership and listing projections. Sentinel subtraction, status filters, approved sort keys and existing role checks remain. Group additions reject malformed form IDs before writing; the explicit `0` group marker remains supported. The existing paginated permission-aware patient API is retained. |
| report index/day sheet | `ProviderDataDao`, `MyGroupDao` and `OscarAppointmentDao` expose named report operations. Site/team restrictions, case-sensitive appointment status selection, ordering and nullable left joins remain. New appointments for all providers use the selected date rather than the session provider number previously bound in its place. `AppointmentDaySheetService` applies visibility filters before acknowledging new appointments, locks their IDs in stable order and archives each prior state in the same transaction as its update. Filtered or concurrently moved appointments remain untouched. Unused cursor/paged/CAISI methods are retired from `AppointmentMainBean`. |
| MDS search, BC address picker, note-role repair, antenatal planner | Existing owning DAOs now return typed values or mutation counts. Search allowlists, merged-patient exclusion, paging budgets, page access checks and output encoding remain. The planner additionally binds the requested patient with the form ID. |
| BC `SqlUtils` consumers | Named billing-service, billing-master and diagnosis projections replace arbitrary SQL. Time-rule flags are projected numerically so physical `TINYINT(1)` values retain the `0`/`1` strings required by billing validation. Billing-master reads explicitly use REQUIRED rather than inheriting its legacy REQUIRES_NEW, so they see caller writes. `LegacyReadResult` preserves null on missing/failed reads without logging clinical values. |

Ordinary fixed queries use JPA/native scalar projections. Schema-dependent
adapters deliberately use Hibernate's managed `doReturningWork` inside the DAO:
AP results must retain the physical driver's string formatting (including
timestamp scale), the antenatal planner retains date/decimal text and NULL across legacy column types,
SetupForm must retain physical column names and SQL NULL, and XML import uses
physical column metadata and generated keys for unmapped form tables.
Their statements/results close before returning detached scalar values; neither
opens nor releases Hibernate's connection. Application callers receive no JDBC
resource. These adapters do not create a new unrestricted SQL executor: AP keeps
the existing SELECT policy, and SetupForm accepts only registry-approved tables
and bound record/patient IDs.

## XML import transactions

`FormXmlImportService` owns the archive transaction, with rollback for checked
and unchecked failures. The action calls its Spring proxy once for the complete
archive. Per-entry compatibility calls join any caller transaction. The native
insert validates column names against the physical table metadata, binds every
value as a string and returns the generated ID on the same connection. Omitted
fields retain database defaults. The archive entry name alone chooses the
patient and edit timestamp; body IDs and conflicting identity fields are ignored.
The existence check preserves the patient/timestamp deduplication rule.

`FormXmlImportServiceIntegrationTest` verifies generated IDs, defaults, nullable
columns, physical date strings, caller transaction visibility, commit and
rollback after an earlier successful entry, including checked validation errors.
It supports the existing opt-in physical MariaDB/Flyway harness:

```sh
CARLOS_TEST_MARIADB_DAO=true \
CARLOS_TEST_MARIADB_URL=jdbc:mariadb://127.0.0.1:3306/ \
CARLOS_TEST_MARIADB_SCHEMA=flyway CARLOS_TEST_MARIADB_PROVINCE=bc \
mvn test -Dtest=FormXmlImportServiceIntegrationTest
```

## Reviewed SQL structure

The method-scoped `java:S2077` exceptions cover only these reviewed query builders:

| Query boundary | Structural input and value binding |
| --- | --- |
| Encounter latest/history/import existence and the three private JDBC statement factories | `requireTable` checks an ASCII identifier and requires a form-registry entry, except for the two built-in tables. Record, patient and timestamp values are bound. Import fields come only from escaped physical column metadata; placeholders are generated and every value is bound. |
| eForm library and patient group listings | `EFormListingRow.requireSort` accepts five exact, fixed order clauses. The optional status predicate is literal; group, patient and status values are bound. |
| eForm group counts | The table and optional predicate are selected from fixed literals; the patient value is bound. |
| Lab patient choices | Search field/predicate variants are fixed, the sort must match the exact column/direction allowlist, search values are bound, and pagination is bounded. |
| Report provider and group choices | An enum or boolean selects fixed predicate fragments. The provider identifier is bound. |
| Appointment day sheet | The sort must equal one of eight fixed columns. Predicate variants are literal; date, time and provider values are bound. |

These exceptions do not exclude a class or disable a rule globally. The native
form/import and eForm integration tests exercise these boundaries, including
patient isolation, raw NULL/whitespace retention, metadata validation and archive
rollback. Re-review a method if its structural input or binding contract changes.

The antenatal planner's method-scoped `java:S6905` exception retains the complete
physical row because legacy checkbox sets are discovered from tinyint metadata.
Only four text fields and the checked flag set leave the DAO; record and patient
IDs remain bound parameters. An explicit fixed column list would omit checkboxes
from a different installed form schema.

## Jasper label compatibility assessment

The shared `DemographicLabelPdf` serves `label.xml`, `Chartlabel.xml`,
`SexualHealthClinicLabel.xml`, `Addresslabel.xml` and `ClientLabLabel.xml`.
The bundled queries own demographic/provider joins, calculated aliases such as
`doc_name` and `age`, and, for the sexual-health template, the `program` parameter.
Their field classes include both String and Integer. Referral `reflabel.xml`
owns its `professionalSpecialists` lookup, `billingreferral_no` binding and seven
String fields. The bundled templates do not remove the compatibility obligation
for operator-supplied templates: demographic labels accept configured paths or
home-directory overrides, and referral labels accept `reflabel.xml` overrides.
An override may change SQL, fields, parameters, no-row behavior or subreports.

Substituting one bean collection for these JDBC-backed Jasper contracts would
ignore override SQL and potentially break subreport connections and no-page
failure handling. These two rendering boundaries therefore retain their scoped,
managed connections. A separate migration must inventory deployed overrides,
define field/parameter/subreport contracts and compare generated PDFs and
missing-patient responses before changing their data source. Existing label
failure/response and referral batch tests remain applicable.

`Frm*Record` / `FrmRecordHelp` remain the separate #2249 / #2167 project.
`JDBCUtil.toDocument`, shared resource finalizers, `SqlUtils.closeResources`,
configurable report infrastructure and Jasper connections remain for their
actual dependents. This change does not retire their lifecycle cleanup or alter
published Flyway migrations, dependency locks or release metadata.

The CDS crosswalk binds complete reader-file hashes. Its review was refreshed
for `CaseManagementNoteDAOImpl` (only the named role-repair mutation was added;
CDS note reads are unchanged) and `FrmSetupForm2Action` (medication prefill is
unchanged; form-row extraction preserves the physical names, nulls and trimming
through its DAO). Schema pins, field dispositions and normalization rules are
unchanged. The regenerated inventory retains those reviewed contracts.
