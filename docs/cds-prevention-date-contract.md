# Native prevention dates and reader compatibility

The native `preventions.prevention_date` value is a storage anchor whenever the latest
`partial_date` row for table 4 / field 7 has format `YYYY` or `YYYY-MM`. This applies to
existing clinical records as well as imported records. Neither January 1 nor the first
of a month establishes partial precision without metadata. A null native date means
unknown timing; no `UNKNOWN` tag is written into the shared partial-date table.

`PreventionDateService` reads this metadata and returns `PreventionDateDTO`:

| Precision | `originalValue` | Inclusive interval |
| --- | --- | --- |
| `FULL_DATE` | `2019-01-01` | `2019-01-01` through `2019-01-01` |
| `YEAR_MONTH` | `2020-02` | `2020-02-01` through `2020-02-29` |
| `YEAR` | `2019` | `2019-01-01` through `2019-12-31` |
| `UNKNOWN` | null | null bounds |

`originalValue` is the native value rendered at its recorded precision. Source lexical
spelling, offset and provenance remain the importer's separate responsibility. Bounds
are ISO calendar strings, never exact administration timestamps. An unsupported stored
format refuses representation and requires review rather than reverting to a full date.

## Implemented read boundaries

- REST `/preventions/active?demographicNo=…` and `/preventions/immunizations/{demographicNo}`
  retain their Date field and existing exact-date behavior. If any returned record has
  partial or unknown timing, the entire operation returns HTTP 409 with an upgrade path.
- Additive REST `/preventions/active/precision?demographicNo=…` and
  `/preventions/immunizations/{demographicNo}/precision` retain every record. Their
  `preventionDateInfo` contains the precision, original native value and inclusive
  interval. `preventionDate` is populated only for a genuine exact date; partial and
  unknown records have a null Date plus explicit date information.
- SOAP `PreventionWs` operations retain their Date-only public type. A partial or unknown
  date produces an explicit SOAP fault requiring a precision-aware representation. No
  affected record is silently omitted and no anchor is serialized. SOAP-only clients
  need a reviewed upgrade before using affected operations; the precision-aware REST
  protocol is available for supported readers.
- The Date-only REST prevention summary refuses affected history with HTTP 409 and the
  precision endpoint location.
- `PreventionListItemDTO` projection includes the latest native partial-date format,
  without loading the eager extensions collection. `getPreventionDateInfo()` represents
  every record. Date-only access to partial/unknown data refuses explicitly.
- `PreventionData` list and record maps retain partial text and explicit `Unknown`, add
  `prevention_date_info`, and expose `prevention_date_asDate` only for full dates. Age at
  an uncertain event is `Unknown`. The prevention print loader consumes these display
  fields. Exact-date map behavior is preserved.
- `PreventionItem` requires native date precision when constructed from an entity. If
  any history date is uncertain, the existing Date-only Drools rule entry point adds a
  review warning and does not evaluate due/dose rules. All source history items remain
  in the fact. Last-dose date and age/elapsed calculations cannot select an anchor as
  the latest event, including overlapping year/month intervals. No vaccine threshold
  or interval-based validity policy is introduced by this change.
- Date-only latest-record and type/date-range DAO operations refuse uncertain history
  before selecting by anchors. The range operation is deliberately conservative across
  its active type scope: an unresolved date can affect membership in the requested range.
  It requires review rather than quietly losing undated records in the SQL filter.

## Remaining reader inventory and activation blockers

This is a finite source inventory, not proof that every deployment-specific integration
client has been found. These readers need their own precision-safe adaptation or a tested
explicit refusal before new import mutation admission can be enabled. A feature flag
alone does not protect existing partial records.

| Reader | Remaining issue |
| --- | --- |
| `commn.model.Prevention.getPreventionDate/getImmunizationDate/isHistorical` | Public entity Date methods have no external metadata; direct callers can still consume anchors. The persistence Date is retained for compatibility, not certified for clinical timing. |
| `integration/fhir/model/Immunization.setAdministrationDate` | Serializes the Date-only immunization interface and computes historical/estimated state; needs precision-aware FHIR mapping or refusal. |
| `prev/reports/ReportBuilder` | Direct Date filtering, latest-event selection and cutoff calculations remain unsafe. |
| `prevention/reports/ChildImmunizationReport`, `PapReport`, `MammogramReport`, `FluReport`, `FOBTReport` | Parse/compare native display strings as exact dates for reporting. A partial label must not be parsed leniently or counted as a proven dose/date. |
| `casemgmt/service/CaseManagementPrintPdf`, `casemgmt/print/OscarChartPrinter` | Direct entity Date formatting can print storage anchors. |
| `eform/actions/RtlPreventions2Action` | Direct entity Date formatting can expose storage anchors in eForm values. |
| `encounter/pageUtil/EctDisplayPrevention2Action` | Reads map Date for filtering/latest selection; uncertain records now have no Date and require an explicit displayed review state, not exclusion. |
| `TemplateFlowSheetPrint.jsp`, `TemplateFlowSheetPage.jspf` | Date parsing / Date-only filtering of prevention maps needs review; `Unknown` or partial text must not disappear or acquire precision. |
| `formbchppg1.jsp`, `formbchppg2.jsp`, `RhInjectionDisplay.jsp` | Date parsing / Date-only selection from maps needs explicit uncertain-state handling. |
| `demographic/pageUtil/DemographicExportAction42Action` | Existing export parses prevention strings; no export feature work is claimed, but safe egress or explicit refusal is required before activation. |
| `dao/ProviderDao` intake prevention query | Raw table projection exposes anchors without the partial-date metadata. |
| `resources/indicatorXMLTemplates/` prevention indicators | `smoking.xml`, `pneumococcal_immunization.xml`, `flu_immuzation.xml`, `flu_immunization_bonus.xml`, `colorectal_cancer.xml`, `colorectal_cancer_bonus.xml`, `childhood_immunization_bonus.xml`, `cervical_cancer.xml`, `cervical_cancer_bonus.xml`, `breast cancer.xml` and `breast_cancer_bonus.xml` use date cutoffs, age calculations, latest-date aggregates or direct date formatting without precision. |
| `resources/oscar/oscarReport/ClinicalReports/ClinicalReports.xml` | Embedded prevention cutoff SQL bypasses the guarded native DAO operations. |
| `resources/oscar/eform/apconfig.xml` | Embedded `DATE_FORMAT(prevention_date)` and latest-row eForm autofill SQL bypasses precision readers. |
| Installed report templates, eForms and query definitions in the database | Database-supplied SQL/templates are another reader surface; bundled source inventory cannot prove every installed definition safe. |
| External REST/SOAP/integrator/portal/cache/index clients | Actual installed client inventory and protocol/rollout proof remain required. Source searches cannot establish that inventory. |

The edit/save web flow must recognize the added map metadata and use a blank editable
value for `UNKNOWN`, preserving the unknown date on save. It must not submit literal
`Unknown` as a date. Existing partial lexemes remain `YYYY` / `YYYY-MM`. Complete browser,
print, form and integration evidence, native projection queries on ON/BC, and the
coordinated deployment barrier remain release gates.

## Verification scope

Focused tests cover genuine exact January 1, existing pre-import partial metadata,
leap-month intervals, null timing, unsupported tag refusal, preserved display records,
unknown ages, overlapping last-dose intervals, Date-only rule refusal, REST precision
wire output / legacy 409, SOAP faults, and summary refusal. `PreventionDaoIntegrationTest`
also covers precision projection, latest native metadata and explicit clearing, and
latest/range refusal before filtering. Run the focused tests and real-database tier on
the integrated candidate; test source alone is not execution evidence.
