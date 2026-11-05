# Encounter, eForm and demographic query migration (#5547)

The historical caller inventory in [legacy-query-helper-migration.md](legacy-query-helper-migration.md)
remains an audit snapshot. This migration replaces its encounter, eForm,
demographic, scheduling-wrapper and form-import business queries with named DAO
operations. It also migrates every remaining `SqlUtils.getRow` and
`getQueryResultsList` consumer, including the BC billing callers.

| Caller | Persistence boundary and compatibility |
| --- | --- |
| `EctPatientData` | `DemographicDao` and `EChartDao`; strings retain trim-to-empty behavior, patient DOB construction and synchronous audit calls remain in the facade. Chart selection orders by ID, independently of timestamp. |
| `EctFormData`, `FrmData` | `EncounterFormDao.HistoryRow` and named latest-ID queries retain the registry/internal-table allowlist, date-only creation values, per-edit-day grouping, XML form history and descending ID order. The edited-time shortcut breaks ties by ID. `StudyDao` owns study/link reads; `PropertyDao` owns resource lookup. |
| `FrmSetupForm2Action` | The registered form table and both record/patient IDs are checked before returning detached column values. SQL NULL and raw whitespace remain available for VT's uneditable history. |
| eForm AP values/JSON | `EFormValueDao` validates the existing SQL policy, binds all template parameters and returns `ClinicalDataRow`. Last-row scalar selection, all-row JSON, case-insensitive labels, trim-to-empty values, missing-column placeholders and the strict failure-versus-empty distinction remain in the facade. |
| eForm groups/listings | `EFormGroupDao`, `EFormDao` and `EFormDataDao` own counts, membership and listing projections. Sentinel subtraction, status filters, approved sort keys and existing role checks remain. The existing paginated permission-aware patient API is retained. |
| report index/day sheet | `ProviderDataDao`, `MyGroupDao` and `OscarAppointmentDao` expose named report operations. Site/team restrictions, case-sensitive appointment status selection, ordering and nullable left joins remain. New appointments for all providers use the selected date rather than the session provider number previously bound in its place. Unused cursor/paged/CAISI methods are retired from `AppointmentMainBean`. |
| MDS search, BC address picker, note-role repair, antenatal planner | Existing owning DAOs now return typed values or mutation counts. Search allowlists, merged-patient exclusion, paging budgets, page access checks and output encoding remain. The planner additionally binds the requested patient with the form ID. |
| BC `SqlUtils` consumers | Named billing-service, billing-master and diagnosis projections replace arbitrary SQL. Billing-master reads explicitly use REQUIRED rather than inheriting its legacy REQUIRES_NEW, so they see caller writes. `LegacyReadResult` preserves null on missing/failed reads without logging clinical values. |

Ordinary fixed queries use JPA/native scalar projections. Two schema-dependent
adapters deliberately use Hibernate's managed `doReturningWork` inside the DAO:
AP results must retain the physical driver's string formatting (including
timestamp scale), and SetupForm must retain physical column names and SQL NULL.
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
CARLOS_TEST_MARIADB_URL=jdbc:mysql://127.0.0.1:3306/ \
CARLOS_TEST_MARIADB_SCHEMA=flyway CARLOS_TEST_MARIADB_PROVINCE=bc \
mvn test -Dtest=FormXmlImportServiceIntegrationTest
```

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
