# Health Tracker

The Health Tracker is a write-oriented view over a measurement flowsheet. Where
`TemplateFlowSheet` shows a flowsheet and sends the clinician to a popup to add
anything, the Health Tracker puts a value/date/comment row on every measurement
card so a whole visit's worth of measurements can be entered and saved in one
POST.

It was removed from CARLOS in December 2025 (commit `2b69c3b357`, "Removed
health tracker related code and files") and restored in September 2026 on the
current frameworks. This document is the map of what the feature consists of,
because it is spread across the flowsheet config, the encounter nav, a Struts
route pair and several sibling pages.

## Reaching it

| Route | Class | Purpose |
| --- | --- | --- |
| `GET /encounter/oscarMeasurements/ViewHealthTracker` | `ViewClinical2Action` | Renders the page. Same read gate as the other flowsheet views: `_eChart r` at the action, `_flowsheet r` in the JSP, plus `_flowsheet.<template> r` for the flowsheet actually asked for — the same per-flowsheet object the encounter nav entry gates on, so a role denied one flowsheet cannot read it by naming it in `?template=`. Flowsheets the role cannot read are left out of the switcher. |
| `POST /encounter/oscarMeasurements/HealthTrackerUpdate` | `HealthTrackerUpdate2Action` | Saves the form. POST-only, `_measurement w`. |

Both routes are extensionless and both JSPs live under `/WEB-INF`, so the page
has no public JSP URL.

The entry point is a single item in the encounter **Measurements** left-nav
module, contributed by `EctDisplayMeasurements2Action`. It appears only when:

- the `health_tracker` property is on (`false` in the shipped
  `carlos.properties`, `true` in the devcontainer config), **and**
- the provider has read access to the `_flowsheet.tracker` security object.

Query parameters the page honours: `demographic_no` (required), `template`
(defaults to `tracker`), `numEle`, `sdate`/`edate`, `show=outOfRange`, `ycoord`.

## The "tracker" flowsheet

`src/main/resources/oscar/encounter/oscarMeasurements/flowsheets/healthTracker.xml`
defines a flowsheet named `tracker`, registered in `applicationContext.xml` and
`applicationContextCaisi.xml` alongside the other system flowsheets.

Two properties of that file are load-bearing and easy to break:

- **It has no items.** The Health Tracker is a scratch flowsheet: clinicians
  choose its measurements themselves through Edit Flowsheet, and those choices
  are stored as `FlowSheetCustomization` rows, not in the XML. The empty
  definition is what makes the page's "It looks like you are not tracking any
  measurements!" first-run state reachable.
- **It declares no `is_universal`, `dxcode_triggers` or `program_triggers`.**
  Any of those would make `MeasurementTemplateFlowSheetConfig` register
  `tracker` as an ordinary flowsheet, and it would show up a second time in the
  left nav next to its own Health Tracker entry.

`tracker.drl` backs the flowsheet's `ds_rules` attribute. It deliberately
contains no rules — a scratch flowsheet has no fixed clinical content to write
timeliness rules against — but it is kept rather than dropping the attribute,
because the `MEASUREMENT_DS_DIRECTORY` filesystem override only applies to
flowsheets that declare `ds_rules`. It was converted from the legacy Drools
XML `<rule-set>` stub to native DRL during the restore; the XML syntax does not
compile under Drools 10.

`HealthTrackerFlowsheetDefinitionUnitTest` pins all of the above, because every
one of these failure modes is silent at runtime.

Manage Flowsheets (`admin/manageFlowsheets.jsp`) deliberately skips the
`tracker` row: there is nothing clinic-wide to enable, disable or edit, and the
feature's visibility is governed by the property instead.

## The save path

`HealthTrackerUpdate2Action` is a thin web layer. The domain logic lives in
`io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker`, which
is servlet-free and Struts-free:

| Class | Responsibility |
| --- | --- |
| `HealthTrackerSubmissionParser` | Walks the flowsheet and pulls the parameters it derived itself into `HealthTrackerEntry` values. A request cannot introduce a measurement type that is not on the named flowsheet. |
| `HealthTrackerMeasurementPersister` | Validates one entry against its `Validations` row via `EctValidation`, then writes it. |
| `HealthTrackerNoteComposer` | Builds the encounter progress-note body for entries flagged "add to progress note". |
| `HealthTrackerSubmissionService` | Orchestrates the three and returns a `HealthTrackerSubmissionResult`. |

This replaces `FormUpdate2Action`, the GPL2-only Indivica action the Health
Tracker shared with the retired DiabFlowSheet page and which was deleted in
PR #579. The restored code is GPL2+ CARLOS code, split so each part is testable
without a container.

### Behaviours worth knowing

- **Field names are derived, not stored.** An input is named after its flowsheet
  item's *measurement type* — the weight row posts as `WT`. The JSP and the
  parser both call `HealthTrackerSubmissionParser.fieldNameFor`, because if the
  two ever drift the form silently stops saving. The type is the key
  `MeasurementFlowSheet` orders its items by, so it is unique within a flowsheet;
  display names are not (a clinician can rename items, and `A/B` and `AB`
  sanitize to the same string), and a shared field name would let one posted
  value be written under the wrong measurement type. The same types key the
  double-click unit conversions, the automatic BMI, and the two-series
  blood-pressure chart, none of which should stop working because an item was
  renamed.
- **Partial success is normal.** Valid rows are saved even when a sibling row is
  rejected; the rejected ones come back in the page's validation alert.
- **Duplicates are suppressed.** An entry matching an existing row (same
  demographic, type, value, date, instruction and comment) is accepted but not
  written again, so a refresh or a re-submitted Save All cannot double a value.
  This is a read-then-write, so it covers *sequential* re-submits; the
  `measurements` table has no unique index to serialize two saves genuinely in
  flight at once. The page disables Save All on submit, which removes the
  double-click that makes that race reachable. Closing it properly needs a
  constraint plus a migration that first has to reckon with duplicate rows
  already sitting in deployed charts.
- **Notes are opt-in, best effort, and follow the write.** A save with nothing
  flagged writes no note at all, and an entry suppressed as a duplicate gets no
  note either — it wrote nothing, so noting it again would put clinical content
  in the chart with no measurement behind it. If note filing fails, the
  measurements are already committed, so the failure is logged rather than
  turned into an error page.
- **Values and comments are length-checked against their columns.** Both
  `measurements.dataField` and `measurements.comments` are `varchar(255)`, and a
  `Validations` row is free to set a shorter maximum or none at all. Anything
  longer is refused with `errors.maxlength` rather than left to throw from the
  database, which under strict SQL modes would surface after earlier rows in the
  same partial-success submission had been committed.
- **A comment is stored exactly as entered, empty string included.** Every other
  writer on this table (`EctMeasurements2Action`, `WriteNewMeasurements`) stores
  the raw parameter and `MeasurementDao.findMatching` compares comments with
  exact equality, so normalizing an empty comment to `" "` would make the
  duplicate check miss rows the Add Measurement path had written.
- **Server-side validation is authoritative.** The `pattern`/`min`/`max`
  attributes the page renders are user feedback only.

### Results

A clean save redirects (POST/Redirect/GET) back to
`ViewHealthTracker`, carrying `ycoord` so the clinician lands where they left
off. Only the validation-error path resolves a Struts result, forwarding back to
the page so the `testOutput` request attribute survives.

## The page

`HealthTracker.jsp` is a thin wrapper that turns an unknown template into a 404;
`HealthTrackerPage.jspf` is the body. It is Bootstrap 5, uses
`<carlos:encode>`/`SafeEncode` throughout, native `<input type="date">` and
Chart.js for trend charts.

Three things differ from the pre-removal page, because their dependencies are no
longer vendored:

| Was | Now |
| --- | --- |
| Bootstrap 3 carousel paging through history values | horizontally scrolling history strip — every value stays reachable and there is no hidden paging state to get out of sync with the filters |
| jqPlot inline chart + jQuery Sparkline inline trend | Chart.js chart in the card footer, plus the existing `GraphMeasurements` popup for the full graph |
| `bootstrap-datepicker` | native `<input type="date">` |

CSRF: the page's single `<form method="post" action="...">` is what makes
CSRFGuard inject the hidden `CSRF-TOKEN` input. The delete call uses `fetch`,
which CSRFGuard's client script does not hijack, so it reads that input and
sends the `CSRF-TOKEN` header itself.

Colours from the flowsheet XML (indicator, warning, recommendation) are
whitelisted to a hex triplet or a bare colour keyword before they are inlined
into a `style` attribute. CSS string escaping cannot be used for a colour — an
escaped `#` stops being one — so anything else is dropped.

## Sibling pages

Restoring the tracker also restored the `htracker` round-trip that lets the
flowsheet admin pages find their way back to it:

- `adminFlowsheet/EditFlowsheet.jsp` — the back button targets
  `ViewHealthTracker` when opened with `&htracker`, and the customization form
  round-trips the flag so `FlowSheetCustom2Action`'s forward lands back on
  Edit Flowsheet with the origin still known.
- `adminFlowsheet/UpdateFlowsheet.jsp` — already carried `htQueryString`.
- `TemplateFlowSheetPrint.jsp` — "back" targets `ViewHealthTracker` when the
  print view was opened from the tracker.
- `MeasurementGroupDSadd.jsp` — decision-support files are again described as
  available to both the flowsheets and the Health Tracker.

## Tests

| Test | Covers |
| --- | --- |
| `HealthTrackerUpdate2ActionUnitTest` | POST-only, `_measurement w`, bad/unknown template handling, redirect vs. forward |
| `HealthTrackerSubmissionParserUnitTest` | field-name derivation, blank/prevention/unknown-type skipping, date fallback |
| `HealthTrackerMeasurementPersisterUnitTest` | validation failures, duplicate suppression, persisted field mapping |
| `HealthTrackerNoteComposerUnitTest` | opt-in note content and the empty-note case |
| `HealthTrackerSubmissionServiceUnitTest` | partial success, note filing, role fallback |
| `HealthTrackerFlowsheetDefinitionUnitTest` | flowsheet identity, absence of triggers/items, DRL compiles |
| `MutatorActionGetRejectionContractUnitTest` | `HealthTrackerUpdate2Action` registered as an unconditional mutator |

```bash
mvn test -Dtest='HealthTracker*'
mvn test -Dtest=MutatorActionGetRejectionContractUnitTest
```

The JSP itself is not covered by the default build: `jspc-maven-plugin` excludes
`**/*.jspf`, and `HealthTrackerPage.jspf` is pulled in by a runtime
`<jsp:include>` rather than compiled with its wrapper. To syntax-check it, copy
it to a `.jsp` next to the original, run `mvn -Pjspc -DskipTests process-classes`,
and delete the copy.
