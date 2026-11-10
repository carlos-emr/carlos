# Qualified and qualitative laboratory results

Target: `release/2026.08`; coverage request [#5803](https://github.com/carlos-emr/carlos/issues/5803).
Run `npm run test:lab-qualified-results-playwright` on an installed disposable
Ubuntu deployment with `CARLOS_DISPOSABLE_HOST=true`, `EXCLUSIVE=1` and the real
`LAB_UPLOAD_DOCUMENT_STORE`. Poppler and English Tesseract are required. This
check must not run concurrently with other checks (live wrapper: EXCLUSIVE=1).

Six actual Inbox Create Lab submissions provide five independently dated source
reports and a separate patient's numeric control. The source observations include
`<0.1`, `>100`, `Negative`, `Pending` and two ordinary numeric series, with exact
test codes/names, units, reference ranges, flags and 09:00 observation times.
All observations have Final status; the literal result `Pending` is distinct
from an interim observation status. The glucose grid alias comes from the
shipped measurements mapping. Numeric reference ranges avoid asking the graph
to interpret a qualitative reference interval as a number.

Before any submission, read-only allocator checks refuse IDs that could acquire
legacy dangling lab/checksum references. No schema, allocator or preexisting row
is changed to make a fixture pass. The actual stored base64 HL7 is decoded by an
independent parser that requires every literal and identity exactly once.

The patient lab list must contain exactly its five source reports. Actual report
links, value histories, cumulative Grid Display and Row Display are opened
natively. The check compares historical/current literals and their dates,
units, ranges, flags and patient/test attribution. Row tooltips are verified
while visibly displayed, after the application's tooltip initializer has run.
Every offered Plot is exercised; an absent Plot is recorded explicitly and
never substitutes for the value-history assertion.

The native graph PNG is captured unchanged from the real request. Independent
OCR of printed annual or month/year ticks calibrates both axes. Numeric series
require both independently expected endpoints and all red series pixels within
six pixels of the expected line. Wholly qualitative/qualified series require no
numeric data pixels; the legend is excluded. This detects converting a bound
such as `<0.1` or `>100` into an exact clinical observation. A separate two-point
cholesterol series proves the positive geometry oracle. No expected coordinates
are read from application data or JavaScript.

Each report's actual Print action must serve a real PDF. Independent Poppler
word coordinates reconstruct every clinical row and column, preserving literal
case and comparators, test identity, flags, units, range, full observation time
and Final status. Layout wrapping alone is normalized. The known small report
must be complete on one page, with the correct patient and no control result.

Independent assertions accumulate in a fatal final verdict so one product
finding cannot conceal the other reports, graphs, PDFs and clinical-state
checks. [#5827](https://github.com/carlos-emr/carlos/issues/5827) records invented
numeric plot points from qualified bounds. [#5828](https://github.com/carlos-emr/carlos/issues/5828)
records lowercased `Negative` and the missing `Pending` measurement/history.
The supported contract preserves original spelling for accepted measurement
values without changing the shipped `HL7_LAB_MEASUREMENT_FILTER` selection or
extraction. The final-status `Pending` literal remains unfiled. Its native value
history and Row Display must visibly explain that reported results may be
available only in the original report; the grid must explain its measurement
scope and must not invent a `Pending` measurement row. These are explicit
unsupported-view assertions, not skipped literal assertions or failure waivers.
All raw source associations and actual reports/PDFs still require the exact
`Pending` literal with Final status, independently of interim OBX status. Other
supported numeric, qualified and qualitative histories retain exact assertions.
The notices are available in all five shipped languages. Neither finding has a
failure pin, and no clinical measurement filter is broadened.

Exact owned and unrelated clinical/message/metadata/routing snapshots must stay
unchanged through all reads. Native entry/plot/print requests use durable
supervision; uncertain submitted writes retain fixtures and a private journal.
Cleanup recovers IDs only from preclaimed unique accessions, checks patient and
checksum ownership, then removes owned messages, measurements, routing, checksums
and archive bytes before owned patients. Optional `LAB_QUALIFIED_ARTIFACT_DIR`
retains synthetic graph/PDF evidence in an allowed artifact directory; working
OCR files and owned application archives are removed. Original numeric, markup,
date and graph regressions remain registered unchanged.
