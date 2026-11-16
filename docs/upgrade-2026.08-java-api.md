# CARLOS 2026.08 Java API migration for local customizations

Before upgrading a site with custom JSPs, eForm scriptlets, reports or plugins,
compile them against the candidate WAR and rehearse their workflows with
synthetic data. In-tree caller migration does not migrate site-local code.
Removed classes/members can cause JSP compilation errors, `NoClassDefFoundError`
or `NoSuchMethodError`; changed generic types can require source changes even
when bytecode linkage survives. Rebuild extensions rather than copying old
classes or jars into the new WAR.

This guide addresses #5747 and the C1-2 follow-up in #5830. The
[original API inventory](testing/release-2026.08-current-head-findings.json)
compares beta6 with release `4b302882`: it flags 29 types with removed/changed
members and 38 with additions. That is a historical inventory, not a stable
extension API promise or a complete count for later release heads. It includes
inherited signatures, checked exceptions and modifiers as well as actual
removals. The tables below distinguish those cases and include subsequent
changes present at `9004536dc83`.

## Original 29-type inventory

Paths below omit the `io.github.carlos_emr.carlos.` prefix, except where a full
name is shown. Consult the linked source for overloads and transaction ownership.

| Type | Change and migration |
| --- | --- |
| `db.DBHandler` | Removed, including `GetPreSQL`/`getPreSql` helpers. Use the domain's typed DAO/service. For a remaining approved JDBC boundary, use [`LegacyJdbcQuery`](../src/main/java/io/github/carlos_emr/carlos/db/LegacyJdbcQuery.java) with bound parameters and scoped resource closure; do not recreate the static connection API. |
| `db.DBPreparedHandler` | Removed, including `queryResults*`, `procExecute` and provider-number allocation. Use typed DAOs/services; migrate result handling to typed rows. `DBPreparedHandlerParam` surviving does not restore the handler. |
| `utility.VelocityUtils` | Removed along with its shared engine/tools/context helpers. The old notification feature is unsupported; there is no drop-in replacement. If an extension still renders Velocity templates, own its engine and tools explicitly and preserve output-context encoding. |
| `billings.MSP.dbExtract` | Removed JDBC extractor. Use [`BcBillingService`](../src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/service/BcBillingService.java) and the typed BC DAO/row APIs instead of passing live `ResultSet`s. |
| `billings.MSP.ExtractBean` | `getClaimDetailRecord` takes `BcBillingRows.ExtractClaimRow`, not `ResultSet`. Obtain the typed claim projection from the billing service. |
| `billings.MSP.CheckBillingData` | `checkC02` takes the same typed `ExtractClaimRow`, not `ResultSet`; update validators together with extraction. |
| `util.StringUtils` | Sixteen helpers removed. See the replacement guidance below; preserve each call site's null, padding, delimiter and CSV policy. |
| `io.github.carlos_emr.OscarDocumentCreator` | `CSV` and `EXCEL` constants/modes removed. Use `report.SpreadsheetExport` for OOXML or the dedicated CSV exporters; retain PDF-specific use separately. See [spreadsheet contracts](spreadsheet-exports.md). |
| `utility.DbConnectionFilter` | Static `getThreadLocalDbConnection`, `releaseThreadLocalDbConnection` and `releaseAllKnownDbResources` removed. Services/DAOs own transactions; scoped JDBC code uses `LegacyJdbcQuery`. The filter is a request-end safety net, not a resource-management API for extensions. |
| `utility.OscarTrackingBasicDataSource` | Driver setup now overrides `setDriverClassName(String)` to normalize legacy names through the MariaDB compatibility layer. The inventory flags the inherited DBCP declaration as changed, not a removed callable setter. Acquire/close tracked `Connection`s normally; do not bypass tracking or the compatibility adapter. |
| `daos.LookupDao` | Arbitrary `runProcedure(String, String[])` removed. There is no general replacement accepting caller-supplied procedure names; add a narrowly scoped, parameterized DAO operation for the required domain. |
| `daos.LookupDaoImpl` | Same `runProcedure` removal; extensions implementing the interface must remove/update that operation and its callers. |
| `managers.WaitListManager` | Protected `getAdmissionVelocityContext` and `getVacancyVelocityContext` removed. The old email-notification methods remain unsupported; there is no supported notification/context replacement. |
| `lab.ca.on.CML.ABCDParser` | `save(Connection)` becomes `save()`; `patientRouteReport` drops its trailing `Connection`. The typed persistence path owns resources. Update callers rather than injecting a raw connection. |
| `lab.ca.on.CommonLabTestValues` | `findValuesByLoinc2(demographic, loinc, Connection)` becomes the two-argument method returning `List<Map<String, Serializable>>`; remove the connection argument and the concrete `ArrayList` assumption. |
| `report.ClinicalReports.ReportEvaluator` | Both affected `evaluate` overloads now use `List<Map.Entry<String, String>>` instead of `List<KeyValue>`. Migrate collection construction and key/value access. |
| `fax.core.FaxImporter` | `poll()` is now `synchronized`; its name and parameters survive. The inventory records a modifier change, not a removal. Retain the importer's serialization/lifecycle ownership rather than running parallel polling on the same instance. |
| `integration.ebs.client.ng.AttachmentCleanupInterceptor` | `getAdditionalInterceptors()` now explicitly overrides the inherited method to arm fault cleanup. The method remains callable. Subclasses/chains must preserve its cleanup guard. |
| `integration.ebs.client.ng.DynamicWSS4JInInterceptor` | `handleFault(Message)` now overrides the generic inherited method to close replay resources. Source/binary consumers should recompile against the concrete signature; subclass overrides must preserve cleanup. |
| `utility.WebappShutdownResources.ShutdownStep` | `MYSQL_CLEANUP_THREAD` removed with MySQL Connector/J. Do not retain MySQL cleanup hooks; use the current shutdown lifecycle, which closes the webapp-owned MariaDB timeout scheduler after connections close. |
| `web.Cds4ReportUIBean.SingleMultiAdmissions` | `singleAdmissionCohortBuckets` and `multipleAdmissionCohortBuckets` change from Commons `MultiValueMap` to `Map<Integer, List<CdsClientForm>>`. Use typed lists and `computeIfAbsent` for additions; handle absent buckets. |
| `casemgmt.web.CaseManagementView2Action` | Protected `addGroupIssues(LoggedInInfo, ArrayList<CheckBoxBean>, int, boolean)` removed. Subclasses must migrate against the current request/patient-scoped chart assembly; there is no drop-in protected hook. Do not restore session-wide patient state. |
| `commn.web.DxresearchReport2Action` | Protected `getServletOstream` removed and `patientExcelReport()` now declares `IOException`. Use the action's completed-file download path; callers/overrides must handle the checked exception. |
| `demographic.pageUtil.DemographicMergeRecord2Action` | No-argument constructor replaced by injection of `SecurityInfoManager`, `DemographicDao` and `PlatformTransactionManager`. Obtain the managed action or supply those dependencies in tests/extensions; do not bypass transaction or authorization checks. |
| `eform.actions.HtmlEdit2Action` | `execute()` now declares `IOException`. Update source callers and overriding signatures as needed; the action still owns its guarded download path. |
| `billings.ca.bc.MSP.CreateBillingReport2Action` | `execute()` now declares `IOException`; handle it in custom callers/overrides rather than suppressing export failures. |
| `billings.ca.bc.MSP.MSPReconcile` | `getMSPRemittanceQuery` and `getAdjustmentsMSPRemittanceQuery` return typed lists of `BcBillingRows.RemittanceRow` / `AdjustmentRemittanceRow`, not `ResultSet`. Replace cursor iteration with row accessors. |
| `encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil` | Public `consultProvider` field removed. Use the consultation list's typed projection/provider data instead of reading a mutable cached field. |
| `utility.OntarioMD` | `getDiseaseList()` now returns `Map<String, String>` instead of Commons `OrderedMap`. Use the `Map` contract; remove Commons-specific casts and methods. |

The original counts include inherited `Object` members on deleted types and
constant fingerprints; they are not counts of distinct removed application
methods. Likewise, an API-addition flag alone does not establish a binary break.
Review custom subclasses and overload resolution for the 38 addition entries.

## Removed StringUtils helpers

| Removed helper(s) | Replacement direction |
| --- | --- |
| `nullSafeEquals` | `Objects.equals(a, b)`. |
| `nullSafeEqualsIgnoreCase` | Explicit null handling followed by `String.equalsIgnoreCase`; define what two nulls mean. |
| `transformNullInEmptyString`, `transformNullInOtherString` | `Objects.requireNonNullElse(value, fallback)` with a nonnull fallback, or an explicit null conditional. |
| `join(String[], String)`, `join(List, String)` | `String.join` for nonnull string elements; explicitly preserve any legacy null-element behavior. |
| `replaceChar` | `String.replace(char, char)`, preserving the old argument order at the call site. |
| `preencheBranco`, `preenchimento`, `preenchimentoEsquerda` | `String.repeat` and explicit length/padding/truncation logic; preserve the fixed-width field contract. |
| `returnStringToFirst` | `indexOf` and `substring`, with explicit handling for an absent delimiter. |
| `split`, `splitString` | Choose literal versus regex delimiters and empty-token behavior explicitly. `String.split` uses regex and drops trailing empties by default; use `Pattern.quote` and a limit when appropriate. Convert to `List`/`Vector` only if the caller needs it. |
| `getCSV` | Use the report's Commons CSV writer and spreadsheet-formula policy; string joining is not CSV escaping. |
| `getStrIn` | Use bound placeholders in a typed DAO/query, not string-built SQL `IN` contents. Handle empty lists explicitly. |
| `isValidDate` | Parse with an explicit formatter and strict date resolution, catching parse failures. Match the required date pattern and legacy acceptance before replacing it. |

These are migration directions, not an assertion that a one-line JDK replacement
preserves every historical edge case. Test those cases in the custom caller.

## Subsequent removals and dependency changes

| Type or dependency | Migration |
| --- | --- |
| `io.github.carlos_emr.AppointmentMainBean` | Fifteen public `queryResults`, `queryResults_paged`, `queryResultsCaisi` and `getString(ResultSet, String/int)` overloads removed in #5694. Migrate JSP query code to typed appointment DAOs/services such as `OscarAppointmentDao`/`AppointmentSearchDao`; remaining `doConfigure(String[][])` does not execute those old query arrays. |
| `util.SqlUtils` | Both `getQueryResultsList` and `getRow` overload pairs (plain SQL and varargs) removed in #5694. Use the domain DAO/service or a deliberately scoped `LegacyJdbcQuery` boundary with bound parameters. |
| `billings.ca.bc.pageUtil.BillCodesTableWrapper` | Deleted with displaytag. Use the current JSTL-rendered table and client-side sorting/paging contract; migrate custom `display:table`/`display-el` tags. |
| `form.FrmGrowth0_36Record` | Deleted in #5705. Use the typed `Growth0To36FormService`, `Growth0To36Form`/`Growth0To36FormValues` and `commn.dao.forms.FormGrowth0To36Dao` path. Custom form bridges must preserve patient authorization and version/persistence rules. |
| displaytag, JFreeChart, production POI, Selenium | Removed from the WAR. Migrate to the current JSTL/client-side tables, browser-backed chart rendering and JDK spreadsheet writer. Do not rely on test-scope dependencies at runtime. See [dependency migration](dependency-footprint-5472.md). |
| Xerces packages | No longer bundled. Replace `org.apache.xerces.*`/`org.apache.xml.serialize.*`/`org.apache.html.dom.*`/`org.apache.wml.*` uses with supported JAXP/DOM/Transformer APIs and explicit secure limits. Default discovery uses JDK 25 `java.xml`; see [XML provider policy](cds-xml-providers.md). |
| OpenPDF `com.lowagie.*` | Removed legacy API. Migrate to `org.openpdf.*`; rehearse PDF 2.0 consumers. See [Jasper/OpenPDF](jasperreports-openpdf3.md). |

## Behavior contracts that retain method names

- `AbstractModel.toString()` now reports the type plus an allowed Integer/Long
  key (`Type[id=N]`), or `Type[id=protected]` for other keys. `BaseObject` and
  many explicit DTO/model overrides use `Type[protected]`. Do not parse these
  strings for patient fields or functional data; use authorized getters and
  explicit serializers. See [safe object diagnostics](security-object-diagnostics.md).
- `Issue`, `ClientImage`, `CaseManagementCPP` and `FieldDefValue` use persistent-key
  equality, with reference identity for unsaved instances. Unsaved
  `CaseManagementNote` and `CaseManagementIssue` hashes use a per-type constant.
  Review custom sets/maps, detached entities and persist transitions; do not
  assume old all-field equality or distinct unsaved hash codes.
- Audit `log.data` for prescription retrieval/discontinuation, appointment creation
  and FHIR provider retrieval uses fixed/type-safe text instead of object dumps.
  Custom audit consumers must use event/identity fields rather than parsing
  the former object representation.
- OWASP Encoder 1.6.0 changes `Encode`/`SafeEncode.forJavaScript*` output bytes
  (including `\xNN` escapes) while preserving interpreted JavaScript string
  values. Use JavaScript encoding only inside the intended JS string context;
  use JSON serialization/`SafeEncode.forJson` for JSON and HTML encoding for HTML.
  Do not expect encoded output to round-trip as plain text. See the
  [encoder migration](security/owasp-encoder-upgrade-2026-10.md).

See also the [2026.08 upgrade notes](release-notes-2026.08.md#upgrade-notes)
for HTTP, wire-format, installation and rollback changes. This source-based
inventory does not replace testing a site's custom binaries and workflows.
