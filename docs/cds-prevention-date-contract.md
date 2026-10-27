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

## Extended legacy reader coverage

The followthrough adapts both chart PDF printers and the RTL eForm to print the original
native precision, including explicit unknown timing. Encounter prevention summaries show
review state without choosing a synthetic latest event. Flowsheet screen/print ranges and
latest-only limits retain uncertain records for review. BC forms preserve the partial label;
Rh injection gestational age stays explicitly unknown when timing is not known.

`PreventionReadSafetyService` and the legacy `PreventionData` boundary refuse Date-only
SQL decisions before a filter or aggregate can lose uncertain records. The scalar query
checks null dates and the latest partial metadata, including merged charts. Structural SQL
table inspection handles quoted/qualified references and nested queries. A supported
population query is refused when any active prevention timing is uncertain; known patient
AP lookups use patient scope. A legacy dialect that might read prevention history but cannot
be structurally interpreted requires review instead of running unguarded.

Guarded readers include the native prevention report families; the newer report builder;
ClinicalReports SQL numerator and denominator; dashboard indicator/drilldown/export SQL;
SQL report templates; Query by Example; eForm AP rendering, interactive lookups and generated
PDF lookups; FHIR immunization API assembly; the intake prevention projection; and legacy
CDS export when prevention categories are selected. Report/HTTP paths expose review state or
an explicit refusal, and do not publish fabricated counts, omitted history or a partial
export package. Existing native full-date formatting and Date API shapes remain in place.

The SQL-engine guards cover the bundled prevention indicators, ClinicalReports.xml and
apconfig.xml without editing their clinical thresholds. They also guard installed SQL that
names the native prevention table. Full installed runtime inventory remains an activation
gate: views, stored functions, custom plugins, direct database access and arbitrary new
readers can hide dependencies from SQL table inspection. They require a verified supported
consumer catalogue and deployment barrier before new import admission is enabled; this
source change does not certify arbitrary database definitions. Raw entity Date getters
remain persistence interfaces and require a precision reader at any new clinical boundary.

The edit/save web flow must recognize `prevention_date_info` and use a blank editable
value for `UNKNOWN`, preserving the unknown date on save. It must not submit literal
`Unknown` as a date. Existing partial lexemes remain `YYYY` / `YYYY-MM`. Complete browser,
print, form and integration execution evidence, native projection queries on ON/BC, and
the coordinated deployment barrier remain release gates.

## Verification scope

Focused tests cover genuine exact January 1, existing pre-import partial metadata,
leap-month intervals, null timing, unsupported tag refusal, preserved display records,
unknown ages, overlapping last-dose intervals, Date-only rule refusal, REST precision
wire output / legacy 409, SOAP faults, and summary refusal. `PreventionDaoIntegrationTest`
also covers precision projection, latest native metadata and explicit clearing, and
latest/range refusal before filtering. Run the focused tests and real-database tier on
the integrated candidate; test source alone is not execution evidence.
